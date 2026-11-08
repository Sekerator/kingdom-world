package io.github.sekerator.kingdomworld.common.law;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.law.CityGates;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.LawPolicy;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.law.LawState;
import io.github.sekerator.kingdomworld.core.law.Reputation;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The law at work on the server: whether a deed is a crime, who saw it and when they tell the guards, the records of the
 * wanted, the contracts for the heads of murderers, the confiscated goods nobody claims, and the small weight reputation
 * has on prices. Everything that changes the law goes through {@link #change}, which refuses while the law or the kingdoms
 * are read-only.
 */
public final class LawWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String KEY = "kingdomworld.law.";
    private static final Random RANDOM = new Random();
    private static final LawPolicy POLICY = new LawPolicy();
    /** Ticks between two tries to post a contract for a head the treasury could not pay. */
    private static final long BOUNTY_RETRY_TICKS = 1_200;

    /**
     * A crime somebody saw and runs to tell the guards. Thefts reported by the same witnesses before they arrive add up.
     *
     * @param citizens witnesses among the citizens, by citizen id
     * @param players  witnesses among the subjects
     */
    private static final class Report {
        final UUID kingdom;
        final UUID offender;
        final String name;
        final CrimeKind kind;
        long worth;
        final String victim;
        final long due;
        final Set<UUID> citizens = new LinkedHashSet<>();
        final Set<UUID> players = new LinkedHashSet<>();

        Report(UUID kingdom, UUID offender, String name, CrimeKind kind, long worth, String victim, long due) {
            this.kingdom = kingdom;
            this.offender = offender;
            this.name = name;
            this.kind = kind;
            this.worth = worth;
            this.victim = victim;
            this.due = due;
        }
    }

    private static final List<Report> REPORTS = new ArrayList<>();
    /** The last deed of a kind of a player at a victim, to make one assault of many blows. */
    private static final Map<String, Long> LAST_DEED = new HashMap<>();
    private static final Map<String, Long> BOUNTY_TRIES = new HashMap<>();
    private static CityGates gates = CityGates.NONE;

    private LawWork() {
    }

    // ---------------------------------------------------------------- access

    public static LawState state(MinecraftServer server) {
        return LawSavedData.get(server).state();
    }

    public static boolean isReadOnly(MinecraftServer server) {
        return LawSavedData.get(server).isReadOnly() || KingdomWorldApi.isReadOnly(server);
    }

    /**
     * Runs a change of the law, refusing it while the law or the kingdoms are read-only: fines and ransoms go to the
     * treasuries, so both must be writable.
     */
    public static Outcome change(MinecraftServer server, String what, Function<LawState, Outcome> change) {
        if (isReadOnly(server)) {
            LOGGER.debug("Refused to {}: law or kingdom data is read-only", what);
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        return change.apply(state(server));
    }

    /**
     * Who is spared by the law; wars and the nobility of later stages add to it.
     */
    public static LawPolicy policy() {
        return POLICY;
    }

    /**
     * The cities with gates; the roads install them.
     */
    public static CityGates gates() {
        return gates;
    }

    public static void setGates(CityGates value) {
        gates = value == null ? CityGates.NONE : value;
    }

    public static void reset() {
        REPORTS.clear();
        LAST_DEED.clear();
        BOUNTY_TRIES.clear();
        LawGuards.reset();
        LawContainers.reset();
        LawSlavery.reset();
        LawPunish.resetSentences();
    }

    public static long now(MinecraftServer server) {
        return KingdomWorldApi.gameTime(server);
    }

    static Optional<Kingdom> kingdom(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.kingdom(server, kingdomId);
    }

    static Component kingdomName(MinecraftServer server, UUID kingdomId, String fallback) {
        return kingdom(server, kingdomId).<Component>map(KingdomActions::kingdomName).orElseGet(() -> Component.literal(fallback));
    }

    // ---------------------------------------------------------------- deeds

    /**
     * Whether a deed of a player in a kingdom is a crime there: not for the king and officials, the owner, whoever holds the
     * right, an operator with bypass, a machine or an enemy in war.
     *
     * @param right the right that allows the deed (containers, building), or {@code null} if none does
     * @param owner whether the thing belongs to the player
     */
    public static boolean isCrime(MinecraftServer server, ServerPlayer player, UUID kingdomId, CrimeKind kind, @Nullable Permission right,
                                  boolean owner) {
        return isCrime(server, player, kingdomId, kind, right, owner, null);
    }

    /**
     * Whether a deed of a player in a kingdom is a crime there, knowing whom it was done to: the blows and the killing of an
     * enemy in war are none.
     *
     * @param victimKingdom the kingdom of the victim, {@code null} if none or unknown
     */
    public static boolean isCrime(MinecraftServer server, ServerPlayer player, UUID kingdomId, CrimeKind kind, @Nullable Permission right,
                                  boolean owner, @Nullable UUID victimKingdom) {
        if (player instanceof FakePlayer || player.isSpectator()
                || ProtectionService.hasBypass(player.getUUID()) && player.hasPermissions(ProtectionService.BYPASS_PERMISSION_LEVEL)) {
            return false;
        }
        KingdomWorldState world = KingdomWorldApi.state(server);
        Optional<Member> member = world.membership().memberOf(player.getUUID()).filter(m -> world.membership().isMember(player.getUUID(), kingdomId));
        boolean hasRight = right != null && world.membership().hasPermission(player.getUUID(), kingdomId, right);
        return POLICY.isCrime(kingdomId, new LawPolicy.Offender(player.getUUID(), member.map(Member::role), hasRight, owner), kind, victimKingdom);
    }

    /**
     * A player did something on the land of a kingdom that may be a crime. If it is one and somebody saw it, it is reported:
     * by a soldier at once, by citizens and subjects when one of them gets to the guards alive.
     *
     * @param kingdomId the kingdom whose law applies; {@code null} takes the owner of the land at {@code where}
     * @param worth     what was stolen, for a theft
     * @param victim    who suffered, for the record
     * @param victimEntity the victim, who is no witness
     * @return whether the crime was seen
     */
    public static boolean deed(ServerPlayer player, BlockPos where, @Nullable UUID kingdomId, CrimeKind kind, long worth, String victim,
                               @Nullable Permission right, boolean owner, @Nullable Entity victimEntity) {
        MinecraftServer server = player.server;
        if (isReadOnly(server)) {
            return false;
        }
        UUID kingdom = kingdomId != null ? kingdomId : KingdomWorldApi.ownerAt(player.level(), where);
        UUID victimKingdom = null;
        if (victimEntity instanceof CitizenEntity citizen) {
            victimKingdom = citizen.kingdomId();
        } else if (victimEntity instanceof ServerPlayer target) {
            victimKingdom = KingdomWorldApi.state(server).membership().kingdomOf(target.getUUID()).orElse(null);
        }
        if (kingdom == null || kingdom(server, kingdom).isEmpty() || !isCrime(server, player, kingdom, kind, right, owner, victimKingdom)) {
            return false;
        }
        LawRules rules = LawSettings.rules();
        long now = now(server);
        LawWitnesses.Seen seen = LawWitnesses.find(player.serverLevel(), player, kingdom, victimEntity, rules);
        String name = player.getGameProfile().getName();
        if (seen.none()) {
            LOGGER.debug("Nobody saw {} commit {} in {}", name, kind.id(), kingdom);
            return false;
        }
        if (kind == CrimeKind.ASSAULT) {
            // Many blows at one victim are one assault.
            String key = player.getUUID() + "|" + kind.id() + "|" + (victimEntity != null ? victimEntity.getUUID() : victim);
            Long last = LAST_DEED.get(key);
            if (last != null && now - last <= rules.mergeTicks) {
                return true;
            }
            LAST_DEED.put(key, now);
        }
        if (seen.soldier()) {
            commit(server, kingdom, player.getUUID(), name, kind, worth, victim);
            return true;
        }
        for (Report report : REPORTS) {
            if (report.kingdom.equals(kingdom) && report.offender.equals(player.getUUID()) && report.kind == kind && kind == CrimeKind.THEFT) {
                report.worth = io.github.sekerator.kingdomworld.core.economy.Money.addCapped(report.worth, worth);
                report.citizens.addAll(seen.citizens());
                report.players.addAll(seen.players());
                return true;
            }
        }
        long delay = rules.reportMinTicks + (rules.reportMaxTicks > rules.reportMinTicks
                ? RANDOM.nextInt(rules.reportMaxTicks - rules.reportMinTicks + 1) : 0);
        Report report = new Report(kingdom, player.getUUID(), name, kind, worth, victim, now + delay);
        report.citizens.addAll(seen.citizens());
        report.players.addAll(seen.players());
        REPORTS.add(report);
        for (CitizenEntity body : seen.bodies()) {
            body.level().playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 1.2f);
            LawWitnesses.runToGuards(body);
        }
        player.displayClientMessage(Component.translatable(KEY + "witness_ran").withStyle(ChatFormatting.GOLD), true);
        LOGGER.debug("{} saw {} commit {} in {}; report in {} ticks", seen.citizens().size() + seen.players().size(), name, kind.id(),
                kingdom, delay);
        return true;
    }

    /**
     * Reports that are due: a witness still alive tells the guards; if every witness died, nobody does.
     */
    private static void deliverReports(MinecraftServer server, long now) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        for (Iterator<Report> it = REPORTS.iterator(); it.hasNext(); ) {
            Report report = it.next();
            if (report.due > now) {
                continue;
            }
            it.remove();
            boolean alive = false;
            for (UUID citizen : report.citizens) {
                if (world.citizens().get(citizen).isPresent()) {
                    alive = true;
                    break;
                }
            }
            if (!alive) {
                for (UUID witness : report.players) {
                    ServerPlayer player = server.getPlayerList().getPlayer(witness);
                    if (player != null && player.isAlive()) {
                        alive = true;
                        break;
                    }
                }
            }
            if (!alive) {
                LOGGER.info("Every witness of {} by {} died before telling the guards of {}", report.kind.id(), report.name, report.kingdom);
                continue;
            }
            commit(server, report.kingdom, report.offender, report.name, report.kind, report.worth, report.victim);
        }
    }

    /**
     * Witness reports still on the way, for checks.
     */
    public static int pendingReports() {
        return REPORTS.size();
    }

    /**
     * Makes every report due now, for checks and commands.
     */
    public static void deliverNow(MinecraftServer server) {
        List<Report> copy = new ArrayList<>(REPORTS);
        REPORTS.clear();
        long now = now(server);
        for (Report report : copy) {
            Report due = new Report(report.kingdom, report.offender, report.name, report.kind, report.worth, report.victim, now);
            due.citizens.addAll(report.citizens);
            due.players.addAll(report.players);
            REPORTS.add(due);
        }
        deliverReports(server, now);
    }

    /**
     * Writes a crime into the record of a kingdom, tells the player when the kingdom starts to want them and posts a contract
     * for the head of a murderer.
     */
    public static void commit(MinecraftServer server, UUID kingdomId, UUID player, String name, CrimeKind kind, long worth, String victim) {
        if (isReadOnly(server)) {
            return;
        }
        LawRules rules = LawSettings.rules();
        LawState.Committed committed = state(server).commit(kingdomId, player, name, kind, worth, victim, now(server), rules);
        Wanted wanted = committed.wanted();
        LOGGER.info("{} is wanted in {} for {} (worth {}), fine {}{}", name, kingdomId, kind.id(), worth,
                wanted.fine(rules, state(server).law(kingdomId).fineMultiplierPermille()), wanted.executable() ? ", to be executed" : "");
        if (committed.appeared() || kind == CrimeKind.MURDER) {
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            if (online != null) {
                online.sendSystemMessage(Component.translatable(KEY + "wanted_new", kingdomName(server, kingdomId, ""),
                        LawTexts.crime(kind), LawTexts.punishment(server, wanted)).withStyle(ChatFormatting.RED));
            }
        }
        if (wanted.executable()) {
            ensureBounty(server, wanted);
        }
        LawSync.dirty(player);
    }

    /**
     * A record is gone (paid, pardoned, executed, forgotten): the contract for the head is withdrawn and the player hears it.
     *
     * @param tell whether to tell the player in the chat
     */
    public static void cleared(MinecraftServer server, Wanted wanted, boolean tell, String reasonKey) {
        withdrawBounty(server, wanted);
        LawGuards.forget(wanted.player());
        LawSync.dirty(wanted.player());
        if (tell) {
            ServerPlayer online = server.getPlayerList().getPlayer(wanted.player());
            if (online != null) {
                online.sendSystemMessage(Component.translatable(KEY + reasonKey, kingdomName(server, wanted.kingdomId(), ""))
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    // ---------------------------------------------------------------- heads of murderers

    /**
     * Posts the contract for the head of a wanted murderer at the branch of the kingdom, if none is on the board and the
     * treasury pays the reward.
     */
    static void ensureBounty(MinecraftServer server, Wanted wanted) {
        if (GuildWork.isReadOnly(server) || !wanted.executable()) {
            return;
        }
        if (wanted.bounty() != 0 && GuildWork.state(server).contract(wanted.bounty()).isPresent()) {
            return;
        }
        LawRules rules = LawSettings.rules();
        long reward = state(server).law(wanted.kingdomId()).bounty(rules);
        if (reward <= 0) {
            return;
        }
        long now = now(server);
        String key = wanted.kingdomId() + "|" + wanted.player();
        Long last = BOUNTY_TRIES.get(key);
        if (last != null && now - last < BOUNTY_RETRY_TICKS) {
            return;
        }
        BOUNTY_TRIES.put(key, now);
        var made = GuildWork.state(server).postOutlaw(wanted.kingdomId(), wanted.player() + "|" + wanted.name(), reward, rules.bountyDays, now,
                TreasuryActions.day(server), io.github.sekerator.kingdomworld.common.guild.GuildSettings.rules(), KingdomWorldApi.state(server));
        if (made.outcome().ok() && made.contract().isPresent()) {
            state(server).setBounty(wanted.kingdomId(), wanted.player(), made.contract().get().id());
            BOUNTY_TRIES.remove(key);
            LOGGER.info("Contract #{} for the head of {} posted in {} for {}", made.contract().get().id(), wanted.name(), wanted.kingdomId(), reward);
        } else {
            LOGGER.debug("No contract for the head of {} in {}: {}", wanted.name(), wanted.kingdomId(), made.outcome().error());
        }
    }

    static void withdrawBounty(MinecraftServer server, Wanted wanted) {
        if (wanted.bounty() == 0 || GuildWork.isReadOnly(server)) {
            return;
        }
        GuildWork.state(server).withdrawOutlaw(wanted.bounty(), TreasuryActions.day(server), KingdomWorldApi.state(server));
        state(server).setBounty(wanted.kingdomId(), wanted.player(), 0);
    }

    /**
     * The contract for the head of a player in a kingdom, if one is on the board.
     */
    public static Optional<Contract> bountyFor(MinecraftServer server, UUID kingdomId, UUID player) {
        Optional<Wanted> wanted = state(server).wanted(kingdomId, player);
        if (wanted.isEmpty() || wanted.get().bounty() == 0) {
            return Optional.empty();
        }
        return GuildWork.state(server).contract(wanted.get().bounty()).filter(contract -> contract.kind() == Contract.Kind.OUTLAW);
    }

    // ---------------------------------------------------------------- once a second

    static void tick(MinecraftServer server) {
        if (isReadOnly(server)) {
            return;
        }
        long now = now(server);
        long day = TreasuryActions.day(server);
        KingdomWorldApi.state(server).economy().setToday(day);
        LawRules rules = LawSettings.rules();
        LawState state = state(server);
        deliverReports(server, now);
        for (Wanted forgotten : state.decay(now, rules)) {
            cleared(server, forgotten, true, "wanted_forgotten");
        }
        for (Wanted wanted : List.copyOf(state.allWanted())) {
            if (kingdom(server, wanted.kingdomId()).isEmpty()) {
                continue;
            }
            if (wanted.executable()) {
                ensureBounty(server, wanted);
            } else if (wanted.bounty() != 0) {
                withdrawBounty(server, wanted);
            }
        }
        for (UUID gone : kingdomsGone(server, state)) {
            state.forgetKingdom(gone);
        }
        state.expirePasses(now);
        LawPunish.expireConfiscations(server, day, rules);
        LawSlavery.tick(server, rules);
        LawGuards.tick(server, now);
        LAST_DEED.values().removeIf(at -> now - at > rules.mergeTicks);
    }

    private static Set<UUID> kingdomsGone(MinecraftServer server, LawState state) {
        Set<UUID> known = new LinkedHashSet<>();
        state.allWanted().forEach(wanted -> known.add(wanted.kingdomId()));
        known.addAll(state.laws().keySet());
        known.addAll(state.reputations().keySet());
        known.addAll(state.passes().keySet());
        known.removeIf(id -> kingdom(server, id).isPresent());
        return known;
    }

    // ---------------------------------------------------------------- what the law weighs elsewhere

    /**
     * A price of the market of a kingdom as this player pays it: a little less with a good name there, a little more with a
     * bad one.
     */
    public static long priceFor(MinecraftServer server, UUID kingdomId, UUID player, long unit) {
        int reputation = reputation(server, kingdomId, player);
        return reputation == 0 || unit <= 0 ? unit : Reputation.price(unit, reputation, LawSettings.rules().priceEffectPercent);
    }

    /**
     * What the market of a kingdom pays this player for an item.
     */
    public static long payoutFor(MinecraftServer server, UUID kingdomId, UUID player, long unit) {
        int reputation = reputation(server, kingdomId, player);
        return reputation == 0 || unit <= 0 ? unit : Reputation.payout(unit, reputation, LawSettings.rules().priceEffectPercent);
    }

    public static int reputation(MinecraftServer server, UUID kingdomId, UUID player) {
        LawSavedData data = LawSavedData.get(server);
        return data.state().reputation(kingdomId, player);
    }

    /**
     * Money a player traded with the market of a kingdom.
     */
    public static void traded(MinecraftServer server, UUID kingdomId, UUID player, long money) {
        if (!isReadOnly(server) && money > 0) {
            state(server).traded(kingdomId, player, money, LawSettings.rules());
        }
    }

    /**
     * A subject paid the tax of the day in full.
     */
    public static void taxPaid(MinecraftServer server, UUID kingdomId, UUID player) {
        if (!isReadOnly(server)) {
            state(server).changeReputation(kingdomId, player, LawSettings.rules().taxReputation);
        }
    }

    /**
     * A player did a contract of the guild at the branch of a kingdom.
     */
    public static void contractDone(MinecraftServer server, UUID kingdomId, UUID player) {
        if (!isReadOnly(server)) {
            state(server).changeReputation(kingdomId, player, LawSettings.rules().contractReputation);
        }
    }

    /**
     * Whether a player owes the treasury of a kingdom more than the market lets go: it then sells only for coins and takes no
     * new lots from them. The guards never attack for a debt.
     */
    public static boolean overDebtLimit(MinecraftServer server, UUID kingdomId, UUID player) {
        long debt = KingdomWorldApi.state(server).economy().treasury(kingdomId).map(treasury -> treasury.debt(player)).orElse(0L);
        return debt > LawSettings.rules().debtLimit;
    }

    /**
     * Whether the player is wanted by the kingdom on whose land they stand; for messages of other parts.
     */
    public static Optional<Wanted> wantedHere(ServerPlayer player) {
        UUID owner = KingdomWorldApi.ownerAt(player.level(), player.blockPosition());
        return owner == null ? Optional.empty() : state(player.server).wanted(owner, player.getUUID());
    }

    /**
     * Whether the soldiers of a kingdom may fight a player for the law now: a guard called the player out, the time to give
     * up is over, and the player is still wanted on the land of the kingdom.
     */
    public static boolean pursued(MinecraftServer server, UUID kingdomId, net.minecraft.world.entity.player.Player player) {
        return LawGuards.pursues(server, kingdomId, player);
    }

    /**
     * What a guard does about a wanted player near it.
     *
     * @param attack whether the time to give up is over
     */
    public record Pursuit(ServerPlayer player, boolean attack) {
    }

    /**
     * The wanted player a guard deals with now: the first time it calls out, later it attacks. {@code null} for none.
     */
    @Nullable
    public static Pursuit pursuit(MinecraftServer server, CitizenEntity guard) {
        return LawGuards.pursue(server, guard);
    }

    /**
     * Whether a guard of a kingdom may strike a player for the law now: the player can be stopped and was called out long
     * enough ago.
     */
    public static boolean mayFight(MinecraftServer server, UUID kingdomId, ServerPlayer player) {
        return LawGuards.catchable(server, player) && LawGuards.pursues(server, kingdomId, player);
    }

    /**
     * Calls a player out as a guard of the kingdom would have done long enough ago to attack now, for game tests.
     */
    public static void hailForCheck(MinecraftServer server, ServerPlayer player, UUID kingdomId) {
        LawGuards.hailNow(server, player, kingdomId, now(server) - LawSettings.rules().hailTicks);
    }

    /**
     * Whether guards may stop a player at all: online, at the keyboard, alive, not in creative or spectator mode.
     */
    public static boolean catchable(MinecraftServer server, ServerPlayer player) {
        return LawGuards.catchable(server, player);
    }

    /**
     * Sets free the slaves of foreign owners on the land of kingdoms that forbid slaves now, for checks and commands.
     *
     * @return how many went free
     */
    public static int enforceSlaveryNow(MinecraftServer server) {
        return isReadOnly(server) ? 0 : LawSlavery.freeForeignSlaves(server, state(server), LawSettings.rules());
    }

    /**
     * Whether a citizen body is a soldier, who reports what it sees at once.
     */
    static boolean isSoldier(CitizenEntity body) {
        return body.kingdomId() != null && Professions.isMilitary(body.professionId());
    }

    /**
     * Bodies of the kingdom's soldiers near a place, for the citizens who run to them.
     */
    static List<CitizenEntity> soldiersNear(ServerLevel level, BlockPos pos, UUID kingdomId, double radius) {
        return level.getEntitiesOfClass(CitizenEntity.class, new AABB(pos).inflate(radius),
                body -> body.isAlive() && kingdomId.equals(body.kingdomId()) && Professions.isMilitary(body.professionId()));
    }

    /**
     * A player is away from the keyboard: the guards leave them alone, as the army does.
     */
    static boolean isAway(ServerPlayer player) {
        return ArmyWork.isAway(player);
    }
}
