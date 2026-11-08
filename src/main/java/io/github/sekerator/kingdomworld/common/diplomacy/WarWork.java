package io.github.sekerator.kingdomworld.common.diplomacy;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyCaravans;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.RoadCaravans;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.trade.MarketStock;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.combat.Battle;
import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.diplomacy.Bond;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyMind;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.diplomacy.Treaty;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.KingdomMembers;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.RulerMemory;
import io.github.sekerator.kingdomworld.core.road.Ambushes;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * Diplomacy and war at work on the server: who is there to defend each kingdom of players, the start of wars, the steps of
 * the wars far from players (battles of squads that hold places on the land of the enemy, raids on its caravans), the days
 * of war (exhaustion, tributes, the end of wars), the decisions of rulers of the world and peace with its money. Everything
 * that changes diplomacy goes through {@link #change}, which refuses while diplomacy or the kingdoms are read-only.
 */
public final class WarWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String KEY = "kingdomworld.war.";
    private static final Random RANDOM = new Random();
    /** Kingdoms of the world a ruler looks at for treaties and wars, in blocks between capitals. */
    public static final int NEIGHBOUR_DISTANCE = 1_500;

    private static DoubleSupplier rolls;
    /** Kingdoms game tests mark as having a player at the keyboard, or as having none, whatever the players do. */
    private static final Map<UUID, Boolean> MARKED = new java.util.HashMap<>();
    private static long lastStep = Long.MIN_VALUE;
    private static long lastDay = Long.MIN_VALUE;
    private static int forgetCalls;

    private WarWork() {
    }

    // ---------------------------------------------------------------- access

    public static DiplomacyState state(MinecraftServer server) {
        return DiplomacySavedData.get(server).state();
    }

    public static boolean isReadOnly(MinecraftServer server) {
        return DiplomacySavedData.get(server).isReadOnly() || KingdomWorldApi.isReadOnly(server);
    }

    /**
     * Runs a change of diplomacy, refusing it while diplomacy or the kingdoms are read-only.
     */
    public static Outcome change(MinecraftServer server, String what, Function<DiplomacyState, Outcome> change) {
        if (isReadOnly(server)) {
            LOGGER.debug("Refused to {}: diplomacy or kingdom data is read-only", what);
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        return change.apply(state(server));
    }

    public static void reset() {
        rolls = null;
        MARKED.clear();
        lastStep = Long.MIN_VALUE;
        lastDay = Long.MIN_VALUE;
        NpcDiplomacy.reset();
    }

    /**
     * Replaces the random numbers of the wars, for game tests; {@code null} gives chance back its say.
     */
    public static void overrideRolls(DoubleSupplier value) {
        rolls = value;
    }

    static DoubleSupplier rolls() {
        return rolls != null ? rolls : RANDOM::nextDouble;
    }

    /**
     * Marks a kingdom as having a player at the keyboard ({@code true}), as having none ({@code false}), or gives the
     * question back to the players ({@code null}); for game tests, which have no real players.
     */
    public static void markPresent(UUID kingdomId, @Nullable Boolean present) {
        if (present == null) {
            MARKED.remove(kingdomId);
        } else {
            MARKED.put(kingdomId, present);
        }
    }

    public static long day(MinecraftServer server) {
        return TreasuryActions.day(server);
    }

    // ---------------------------------------------------------------- who is there

    public static boolean isNpc(MinecraftServer server, UUID kingdomId) {
        return NpcWork.isNpc(server, kingdomId);
    }

    /**
     * Whether a player of a kingdom is online and at the keyboard now.
     */
    public static boolean present(MinecraftServer server, UUID kingdomId) {
        Boolean marked = MARKED.get(kingdomId);
        if (marked != null) {
            return marked;
        }
        Optional<KingdomMembers> members = KingdomWorldApi.state(server).membership().kingdom(kingdomId);
        if (members.isEmpty()) {
            return false;
        }
        for (Member member : members.get().members().values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
            if (player != null && !player.isSpectator() && !ArmyWork.isAway(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a kingdom can defend itself now: a kingdom of the world always, a kingdom of players while one of them is
     * online at the keyboard.
     */
    public static boolean playable(MinecraftServer server, UUID kingdomId) {
        return isNpc(server, kingdomId) || present(server, kingdomId);
    }

    /**
     * Whether a kingdom of players is shielded now: nobody of it is online at the keyboard, the last left some minutes ago and
     * no fight near its players happened a moment ago. Kingdoms of the world have their armies instead.
     */
    public static boolean shielded(MinecraftServer server, UUID kingdomId) {
        if (isNpc(server, kingdomId)) {
            return false;
        }
        return state(server).presence().shielded(kingdomId, KingdomWorldApi.gameTime(server), present(server, kingdomId),
                DiplomacySettings.rules());
    }

    /**
     * Players of a kingdom fought an enemy near them: no shield for a while.
     */
    public static void fought(MinecraftServer server, UUID kingdomId) {
        if (!isReadOnly(server)) {
            state(server).presence().fought(kingdomId, KingdomWorldApi.gameTime(server));
        }
    }

    // ---------------------------------------------------------------- questions of other systems

    /**
     * Whether two kingdoms fight each other now (the war has started).
     */
    public static boolean fighting(MinecraftServer server, @Nullable UUID a, @Nullable UUID b) {
        return a != null && b != null && !a.equals(b) && state(server).fighting(a, b);
    }

    /**
     * Whether a deal between two kingdoms is frozen by a war between them (declared or fought).
     */
    public static boolean frozen(MinecraftServer server, UUID seller, UUID buyer) {
        return state(server).warBetween(seller, buyer).isPresent();
    }

    /**
     * The kingdom that blocks a caravan of a deal: one at war with the seller or the buyer whose land lies on the way between
     * their capitals, and that is not shielded itself; empty if the way is free.
     */
    public static Optional<UUID> blocker(MinecraftServer server, Deal deal) {
        DiplomacyState state = state(server);
        Set<UUID> enemies = new LinkedHashSet<>();
        enemies.addAll(state.enemiesOf(deal.seller()));
        enemies.addAll(state.enemiesOf(deal.buyer()));
        if (enemies.isEmpty()) {
            return Optional.empty();
        }
        KingdomWorldState world = KingdomWorldApi.state(server);
        Optional<Capital> from = world.territory().territory(deal.seller()).map(Territory::capital);
        Optional<Capital> to = world.territory().territory(deal.buyer()).map(Territory::capital);
        if (from.isEmpty() || to.isEmpty() || !from.get().dimension().equals(to.get().dimension())) {
            return Optional.empty();
        }
        int dx = to.get().x() - from.get().x();
        int dz = to.get().z() - from.get().z();
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt((double) dx * dx + (double) dz * dz) / 16.0));
        long lastChunk = Long.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            int x = from.get().x() + (int) Math.round(dx * (i / (double) steps));
            int z = from.get().z() + (int) Math.round(dz * (i / (double) steps));
            long chunk = ChunkKey.of(x >> 4, z >> 4);
            if (chunk == lastChunk) {
                continue;
            }
            lastChunk = chunk;
            UUID owner = world.territory().ownerOf(from.get().dimension(), chunk);
            if (owner != null && enemies.contains(owner) && !shielded(server, owner)) {
                return Optional.of(owner);
            }
        }
        return Optional.empty();
    }

    /**
     * A caravan of a deal could not leave: the blocker gains score against the party it is at war with.
     */
    public static void blocked(MinecraftServer server, Deal deal, UUID blocker) {
        if (isReadOnly(server)) {
            return;
        }
        DiplomacyRules rules = DiplomacySettings.rules();
        DiplomacyState state = state(server);
        UUID victim = state.fighting(blocker, deal.seller()) ? deal.seller() : deal.buyer();
        if (shielded(server, victim)) {
            return;
        }
        if (state.score(blocker, victim, DiplomacyState.Feat.BLOCKADE, rules.scoreBlockade()) > 0) {
            long now = KingdomWorldApi.gameTime(server);
            state.warBetween(blocker, victim).ifPresent(war -> state.record(now, "blockade", blocker, victim, war.id(), ""));
            tell(server, victim, Component.translatable(KEY + "blockade_victim", name(server, blocker), deal.id()).withStyle(ChatFormatting.GOLD));
            tell(server, blocker, Component.translatable(KEY + "blockade", name(server, victim), deal.id()).withStyle(ChatFormatting.GREEN));
        }
    }

    // ---------------------------------------------------------------- texts

    static Component name(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.kingdom(server, kingdomId).<Component>map(KingdomActions::kingdomName).orElseGet(() -> Component.literal("?"));
    }

    /**
     * Tells the members of a kingdom who run its diplomacy (or everybody, for war) something.
     */
    static void tell(MinecraftServer server, UUID kingdomId, Component message) {
        KingdomWorldApi.kingdom(server, kingdomId).ifPresent(kingdom -> KingdomActions.notifyMembers(server, kingdom, null, message));
    }

    static void tellDiplomats(MinecraftServer server, UUID kingdomId, Component message) {
        KingdomWorldApi.kingdom(server, kingdomId).ifPresent(kingdom ->
                KingdomActions.notifyMembers(server, kingdom, Permission.MANAGE_DIPLOMACY, message));
    }

    // ---------------------------------------------------------------- the wealth of kingdoms

    public static long funds(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.state(server).economy().treasury(kingdomId).map(Treasury::funds).orElse(0L);
    }

    /**
     * Whether the treasury of a kingdom is empty or could not pay one more day like the last.
     */
    public static boolean poor(MinecraftServer server, UUID kingdomId) {
        Optional<Treasury> treasury = KingdomWorldApi.state(server).economy().treasury(kingdomId);
        if (treasury.isEmpty()) {
            return true;
        }
        long day = day(server);
        Map<io.github.sekerator.kingdomworld.core.economy.LedgerCategory, TreasuryJournal.Totals> yesterday =
                treasury.get().journal().days().getOrDefault(day - 1, Map.of());
        long spent = 0;
        for (TreasuryJournal.Totals totals : yesterday.values()) {
            spent += totals.expense();
        }
        return treasury.get().funds() <= 0 || treasury.get().funds() < spent;
    }

    /**
     * What a kingdom earned on a day: taxes, trade, the law and the taverns, not transfers, wars or the money of its founding.
     */
    public static long income(MinecraftServer server, UUID kingdomId, long day) {
        Optional<Treasury> treasury = KingdomWorldApi.state(server).economy().treasury(kingdomId);
        if (treasury.isEmpty()) {
            return 0;
        }
        Map<LedgerCategory, TreasuryJournal.Totals> totals = treasury.get().journal().days().getOrDefault(day, Map.of());
        long sum = 0;
        for (LedgerCategory category : List.of(LedgerCategory.TAXES, LedgerCategory.TRADE, LedgerCategory.LAW, LedgerCategory.LIFE)) {
            sum += totals.getOrDefault(category, TreasuryJournal.Totals.ZERO).income();
        }
        return sum;
    }

    /**
     * Money from the treasury of one kingdom to another, in the currency of the payer, in the books of both or not at all.
     */
    public static Outcome pay(MinecraftServer server, UUID payer, UUID payee, long amount, long day) {
        if (amount <= 0) {
            return Outcome.DONE;
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        Outcome check = economy.checkHoldFromTreasury(payer, amount);
        if (!check.ok()) {
            return check;
        }
        if (!economy.canReleaseToTreasury(payee, payer, amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        Outcome held = economy.holdFromTreasury(payer, amount, LedgerCategory.WAR, day);
        if (!held.ok()) {
            return held;
        }
        Outcome released = economy.releaseToTreasury(payee, payer, amount, LedgerCategory.WAR, day);
        if (!released.ok()) {
            // Cannot happen after the checks; the money goes back where it came from rather than vanish.
            economy.releaseToTreasury(payer, payer, amount, LedgerCategory.WAR, day);
            return released;
        }
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- once a second

    /**
     * Moves diplomacy on: who is there, wars that start, offers and treaties that run out, the answers of rulers of the world,
     * the steps of the wars far from players and, once a game day, exhaustion, tributes and the decisions of rulers.
     */
    public static void tick(MinecraftServer server) {
        if (isReadOnly(server)) {
            return;
        }
        DiplomacyState state = state(server);
        DiplomacyRules rules = DiplomacySettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        long day = day(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        long mark = System.nanoTime();
        phases.setLength(0);
        forgetGone(server, state, world, now);
        for (Kingdom kingdom : world.kingdoms().all()) {
            if (!isNpc(server, kingdom.id()) && present(server, kingdom.id())) {
                state.presence().present(kingdom.id(), now);
            }
        }
        for (War war : state.startDue(now)) {
            started(server, war, now);
        }
        for (Proposal proposal : state.expire(now)) {
            tellDiplomats(server, proposal.from(), Component.translatable(KEY + "offer_expired", WarTexts.offer(proposal.kind()),
                    name(server, proposal.to())));
        }
        for (Treaty treaty : state.expireTreaties(now)) {
            Component text = Component.translatable(KEY + "treaty_ended." + treaty.kind().id(), name(server, treaty.second()));
            tellDiplomats(server, treaty.first(), text);
            tellDiplomats(server, treaty.second(), Component.translatable(KEY + "treaty_ended." + treaty.kind().id(), name(server, treaty.first())));
        }
        mark = phase("presence and treaties", mark);
        NpcDiplomacy.answer(server, state, rules, now, day);
        mark = phase("answers of rulers", mark);
        NpcDiplomacy.work(server, state, rules, now);
        mark = phase("a turn of a ruler", mark);
        if (lastStep == Long.MIN_VALUE || now < lastStep) {
            lastStep = now;
        } else if (now - lastStep >= rules.stepTicks()) {
            lastStep = now;
            step(server, state, rules, now);
            mark = phase("a step of the wars", mark);
        }
        if (day != lastDay) {
            boolean first = lastDay == Long.MIN_VALUE;
            lastDay = day;
            daily(server, state, rules, now, day, first);
            phase("a day of the wars", mark);
        }
    }

    /** The parts of the last tick that took a while, for the line of a slow tick. */
    private static final StringBuilder phases = new StringBuilder();

    private static long phase(String name, long since) {
        long now = System.nanoTime();
        if (now - since > 2_000_000L) {
            phases.append(phases.length() == 0 ? "" : ", ").append(name).append(' ')
                    .append(String.format(java.util.Locale.ROOT, "%.1f", (now - since) / 1_000_000.0)).append(" ms");
        }
        return now;
    }

    /**
     * The parts of the last tick that took more than 2 ms.
     */
    static String phases() {
        return phases.toString();
    }

    private static void forgetGone(MinecraftServer server, DiplomacyState state, KingdomWorldState world, long now) {
        if (++forgetCalls % 5 != 0) {
            return;
        }
        Set<UUID> known = new HashSet<>();
        for (War war : state.wars()) {
            known.add(war.attacker());
            known.add(war.defender());
            for (War.Side side : War.Side.values()) {
                known.addAll(war.allies(side));
            }
        }
        for (Treaty treaty : state.treaties()) {
            known.add(treaty.first());
            known.add(treaty.second());
        }
        for (Proposal proposal : state.proposals()) {
            known.add(proposal.from());
            known.add(proposal.to());
        }
        for (UUID id : known) {
            if (world.kingdoms().get(id).isEmpty()) {
                LOGGER.info("Diplomacy forgets the kingdom {}, which is gone", id);
                state.forget(id, now);
            }
        }
    }

    private static void started(MinecraftServer server, War war, long now) {
        DiplomacyState state = state(server);
        state.record(now, "started", war.attacker(), war.defender(), war.id(), war.goal().id());
        for (War.Side side : War.Side.values()) {
            for (UUID member : war.members(side)) {
                tell(server, member, Component.translatable(KEY + "started", name(server, war.leader(side.enemy())))
                        .withStyle(ChatFormatting.RED));
                if (isNpc(server, member)) {
                    NpcDiplomacy.muster(server, member, war.leader(side.enemy()), side == War.Side.ATTACKER);
                }
            }
        }
        LOGGER.info("War #{} between {} and {} started", war.id(), name(server, war.attacker()).getString(), name(server, war.defender()).getString());
    }

    // ---------------------------------------------------------------- steps of the wars far from players

    /**
     * Squads that hold places on the land of an enemy meet its defenders, and fall upon its caravans.
     */
    static void step(MinecraftServer server, DiplomacyState state, DiplomacyRules rules, long now) {
        for (War war : List.copyOf(state.wars())) {
            if (!war.active()) {
                continue;
            }
            for (War.Side side : War.Side.values()) {
                Set<UUID> enemies = war.members(side.enemy());
                List<ArmySquad> field = new ArrayList<>();
                for (UUID member : war.members(side)) {
                    for (ArmySquad squad : ArmyWork.state(server).squadsOf(member)) {
                        Optional<UUID> on = onLandOf(server, squad);
                        if (on.isPresent() && enemies.contains(on.get()) && !ArmyWork.hasBodies(squad)) {
                            field.add(squad);
                        }
                    }
                }
                for (ArmySquad squad : field) {
                    UUID enemy = onLandOf(server, squad).orElseThrow();
                    if (shielded(server, enemy) || rolls().getAsDouble() >= rules.battleChance()) {
                        continue;
                    }
                    battle(server, war, squad, enemy, rules, now);
                }
                if (!field.isEmpty() && rolls().getAsDouble() < rules.raidChance()) {
                    ArmySquad raiders = field.get((int) Math.floor(rolls().getAsDouble() * field.size()) % field.size());
                    raid(server, raiders, enemies, rules, now);
                }
            }
        }
    }

    /**
     * The kingdom whose land the place of a squad lies on, if any.
     */
    public static Optional<UUID> onLandOf(MinecraftServer server, ArmySquad squad) {
        Order order = squad.order();
        if (order.kind() != Order.Kind.HOLD && order.kind() != Order.Kind.GUARD && order.kind() != Order.Kind.PATROL) {
            return Optional.empty();
        }
        Optional<WorldPos> anchor = order.anchor();
        if (anchor.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(KingdomWorldApi.state(server).territory().ownerOf(anchor.get().dimension(),
                ChunkKey.of(anchor.get().chunkX(), anchor.get().chunkZ())));
    }

    /**
     * The squad of a kingdom that defends its land against an attack: one that holds, guards or patrols its own land, else
     * one at home, the strongest first; never one with soldiers near players.
     */
    static Optional<ArmySquad> defender(MinecraftServer server, UUID kingdomId) {
        ArmySquad best = null;
        double bestPower = 0.0;
        boolean bestOnLand = false;
        for (ArmySquad squad : ArmyWork.state(server).squadsOf(kingdomId)) {
            if (ArmyWork.hasBodies(squad)) {
                continue;
            }
            boolean onLand = onLandOf(server, squad).map(kingdomId::equals).orElse(false);
            boolean home = squad.order().kind() == Order.Kind.NONE;
            if (!onLand && !home) {
                continue;
            }
            double power = ArmyWork.power(server, squad);
            if (power <= 0) {
                continue;
            }
            if (best == null || (onLand && !bestOnLand) || (onLand == bestOnLand && power > bestPower)) {
                best = squad;
                bestPower = power;
                bestOnLand = onLand;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * A squad on the land of the enemy meets the defenders there. Without defenders it ravages the land unopposed.
     */
    static void battle(MinecraftServer server, War war, ArmySquad attacking, UUID enemy, DiplomacyRules rules, long now) {
        DiplomacyState state = state(server);
        ArmyRules armyRules = ArmySettings.rules();
        WorldPos where = attacking.order().anchor().orElse(null);
        Optional<ArmySquad> defending = defender(server, enemy);
        UUID attacker = attacking.kingdomId();
        if (defending.isEmpty()) {
            state.score(attacker, enemy, DiplomacyState.Feat.HOLD, rules.scoreBattle() / 2.0);
            state.exhaust(enemy, attacker, rules.exhaustionLoss(), 0);
            return;
        }
        Side ours = ArmyWork.side(server, attacking);
        Side theirs = ArmyWork.side(server, defending.get());
        Battle.Result result = Battle.resolve(ours, theirs, armyRules.battle(), rolls());
        if (result.winner() == Battle.Winner.NONE) {
            return;
        }
        ArmyWork.Fought first = ArmyWork.apply(server, attacking, ours, result, where, armyRules);
        ArmyWork.Fought second = ArmyWork.apply(server, defending.get(), theirs, mirror(result), where, armyRules);
        Component against = name(server, enemy);
        ArmyWork.announce(server, attacking, first, Component.translatable(KEY + "battle_at", against));
        ArmyWork.announce(server, defending.get(), second, Component.translatable(KEY + "battle_at", name(server, attacker)));
        boolean won = result.winner() == Battle.Winner.FIRST;
        UUID winner = won ? attacker : enemy;
        UUID loser = won ? enemy : attacker;
        state.score(winner, loser, DiplomacyState.Feat.BATTLE, rules.scoreBattle());
        int attackerKills = first.kills();
        int defenderKills = second.kills();
        for (int i = 0; i < attackerKills; i++) {
            state.score(attacker, enemy, DiplomacyState.Feat.KILL, rules.scoreKill());
        }
        for (int i = 0; i < defenderKills; i++) {
            state.score(enemy, attacker, DiplomacyState.Feat.KILL, rules.scoreKill());
        }
        state.exhaust(enemy, attacker, rules.exhaustionLoss() * attackerKills, attackerKills);
        state.exhaust(attacker, enemy, rules.exhaustionLoss() * defenderKills, defenderKills);
        state.record(now, "battle", winner, loser, war.id(), "");
        LOGGER.info("War #{}: a squad of {} fought one of {} far from players and {}", war.id(), name(server, attacker).getString(),
                against.getString(), won ? "won" : "lost");
    }

    /**
     * The result of a battle as the second side sees it.
     */
    public static Battle.Result mirror(Battle.Result result) {
        Battle.Winner winner = switch (result.winner()) {
            case FIRST -> Battle.Winner.SECOND;
            case SECOND -> Battle.Winner.FIRST;
            case NONE -> Battle.Winner.NONE;
        };
        return new Battle.Result(winner, 1.0 - result.chance(), result.secondPower(), result.firstPower(), result.fates(), result.secondLost(),
                result.firstLost(), result.secondFled(), result.firstFled(), result.lootShare());
    }

    /**
     * A squad in the field falls upon a caravan of the enemy far from players, on the mechanics of the ambushes of robbers:
     * its guards fight, and if they lose a share of the goods goes to the storehouses of the raiders and the payment for it
     * back to the buyer. Caravans of a shielded kingdom are left alone.
     *
     * @return whether a caravan was robbed
     */
    static boolean raid(MinecraftServer server, ArmySquad raiders, Set<UUID> enemies, DiplomacyRules rules, long now) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        UUID raider = raiders.kingdomId();
        DiplomacyState state = state(server);
        List<Caravan> targets = new ArrayList<>();
        for (Caravan caravan : world.trade().caravans()) {
            if (caravan.state() != Caravan.State.TRAVELING) {
                continue;
            }
            UUID victim = enemies.contains(caravan.seller()) ? caravan.seller() : enemies.contains(caravan.buyer()) ? caravan.buyer() : null;
            if (victim == null || caravan.seller().equals(raider) || caravan.buyer().equals(raider) || shielded(server, victim)
                    || RoadCaravans.merchantOfDeal(server, caravan.dealId()).isPresent()) {
                continue;
            }
            targets.add(caravan);
        }
        if (targets.isEmpty()) {
            return false;
        }
        Caravan caravan = targets.get((int) Math.floor(rolls().getAsDouble() * targets.size()) % targets.size());
        return raid(server, raiders, caravan, rules, now).isPresent();
    }

    /**
     * Raiders fall upon one caravan. Public for operators and checks.
     *
     * @return the goods taken, empty if the guards beat them off or nothing was taken
     */
    public static Optional<List<Goods>> raid(MinecraftServer server, ArmySquad raiders, Caravan caravan, DiplomacyRules rules, long now) {
        DiplomacyState state = state(server);
        UUID raider = raiders.kingdomId();
        UUID victim = state.fighting(raider, caravan.seller()) ? caravan.seller() : caravan.buyer();
        ArmyRules armyRules = ArmySettings.rules();
        List<ArmySquad> guards = ArmyCaravans.guards(server, caravan);
        List<Side> guardSides = new ArrayList<>();
        Side all = Side.of(Side.Kind.SOLDIERS, List.of());
        for (ArmySquad squad : guards) {
            Side side = ArmyWork.side(server, squad);
            guardSides.add(side);
            all = all.with(side);
        }
        Side ours = ArmyWork.side(server, raiders);
        Ambushes.Ambush ambush = Ambushes.resolve(1.0, all, ours, RoadSettings.bandits(), armyRules.battle(), rolls());
        if (!ambush.happened()) {
            return Optional.empty();
        }
        WorldPos where = raiders.order().anchor().orElse(null);
        if (ambush.battle().winner() != Battle.Winner.NONE) {
            Component what = Component.translatable(KEY + "raid_fight", caravan.dealId());
            for (int i = 0; i < guards.size(); i++) {
                ArmyWork.Fought fought = ArmyWork.apply(server, guards.get(i), guardSides.get(i), ambush.battle(), where, armyRules);
                ArmyWork.announce(server, guards.get(i), fought, what);
            }
            ArmyWork.Fought fought = ArmyWork.apply(server, raiders, ours, mirror(ambush.battle()), where, armyRules);
            ArmyWork.announce(server, raiders, fought, what);
        }
        if (!ambush.robbed()) {
            tell(server, victim, Component.translatable(KEY + "raid_beaten", name(server, raider), caravan.dealId()).withStyle(ChatFormatting.GREEN));
            return Optional.empty();
        }
        RoadCaravans.Seized seized = RoadCaravans.seize(server, caravan, ambush.share());
        WorldPos near = capital(server, raider);
        List<ItemStack> stacks = new ArrayList<>();
        for (Goods goods : seized.taken()) {
            stacks.addAll(io.github.sekerator.kingdomworld.common.trade.GoodsStacks.stacks(goods));
        }
        List<ItemStack> rest = MarketStock.putBack(server, raider, stacks, near);
        if (!rest.isEmpty()) {
            LOGGER.info("The storehouses of {} are full: {} stack(s) of the loot of a raid are left behind", name(server, raider).getString(),
                    rest.size());
        }
        long items = Ambushes.items(seized.taken());
        state.score(raider, victim, DiplomacyState.Feat.CARAVAN, rules.scoreCaravan());
        state.exhaust(victim, raider, rules.exhaustionCaravan(), 0);
        state.warBetween(raider, victim).ifPresent(war -> state.record(now, "raid", raider, victim, war.id(), Long.toString(items)));
        Component goods = Component.translatable(KEY + "items", WarTexts.items(items));
        tell(server, victim, Component.translatable(KEY + "raided", name(server, raider), caravan.dealId(), goods).withStyle(ChatFormatting.RED));
        tell(server, raider, Component.translatable(KEY + "raid_loot", name(server, victim), goods).withStyle(ChatFormatting.GREEN));
        return Optional.of(seized.taken());
    }

    /**
     * Battle zones for the war layers of the maps: the chunks around every place a squad holds on the land of an enemy.
     */
    public static List<io.github.sekerator.kingdomworld.core.territory.MapZone> zones(MinecraftServer server) {
        List<io.github.sekerator.kingdomworld.core.territory.MapZone> zones = new ArrayList<>();
        if (DiplomacySavedData.get(server).isReadOnly()) {
            return zones;
        }
        for (War war : state(server).wars()) {
            if (!war.active()) {
                continue;
            }
            for (War.Side side : War.Side.values()) {
                Set<UUID> enemies = war.members(side.enemy());
                for (UUID member : war.members(side)) {
                    for (ArmySquad squad : ArmyWork.state(server).squadsOf(member)) {
                        Optional<UUID> on = onLandOf(server, squad);
                        Optional<WorldPos> anchor = squad.order().anchor();
                        if (on.isEmpty() || !enemies.contains(on.get()) || anchor.isEmpty()) {
                            continue;
                        }
                        Set<Long> chunks = new java.util.HashSet<>();
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                chunks.add(ChunkKey.of(anchor.get().chunkX() + dx, anchor.get().chunkZ() + dz));
                            }
                        }
                        zones.add(new io.github.sekerator.kingdomworld.core.territory.MapZone(
                                io.github.sekerator.kingdomworld.core.territory.MapZone.Layer.BATTLE, anchor.get().dimension(),
                                "war-" + war.id() + "-" + squad.id(), List.of(member, on.get()), chunks));
                    }
                }
            }
        }
        return zones;
    }

    @Nullable
    static WorldPos capital(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.state(server).territory().territory(kingdomId).map(Territory::capital)
                .map(c -> new WorldPos(c.dimension(), c.x(), c.y(), c.z())).orElse(null);
    }

    /**
     * A war being prepared starts at once, for operators.
     */
    public static void startNow(MinecraftServer server, War war) {
        if (war.phase() != War.Phase.PREPARING || !change(server, "start a war", state -> state.startNow(war.id())).ok()) {
            return;
        }
        started(server, war, KingdomWorldApi.gameTime(server));
    }

    /**
     * A step of the wars far from players at once, for operators.
     */
    public static void forceStep(MinecraftServer server) {
        if (!isReadOnly(server)) {
            step(server, state(server), DiplomacySettings.rules(), KingdomWorldApi.gameTime(server));
        }
    }

    /**
     * A day of the wars at once, for operators: exhaustion, tributes, the end of wars and the decisions of rulers.
     *
     * @return the wars still going on afterwards
     */
    public static int forceDay(MinecraftServer server) {
        if (isReadOnly(server)) {
            return 0;
        }
        DiplomacyState state = state(server);
        long next = day(server);
        for (War war : state.wars()) {
            if (war.active()) {
                next = Math.max(next, war.lastDay() + 1);
            }
        }
        next = Math.max(next, state.tributeDay() + 1);
        daily(server, state, DiplomacySettings.rules(), KingdomWorldApi.gameTime(server), next, false);
        return (int) state.wars().stream().filter(war -> !war.over()).count();
    }

    // ---------------------------------------------------------------- days of war

    private static void daily(MinecraftServer server, DiplomacyState state, DiplomacyRules rules, long now, long day, boolean first) {
        List<DiplomacyState.Ending> endings = state.daily(now, day, rules, id -> shielded(server, id), id -> poor(server, id),
                id -> funds(server, id));
        for (DiplomacyState.Ending ending : endings) {
            end(server, ending.war(), ending.how(), ending.terms());
        }
        payTributes(server, state, day);
        if (!first) {
            state.fade();
        }
        NpcDiplomacy.daily(server, state, rules, now, day);
    }

    private static void payTributes(MinecraftServer server, DiplomacyState state, long day) {
        for (DiplomacyState.Tribute tribute : state.dueTributes(day)) {
            if (state.warBetween(tribute.payer(), tribute.payee()).isPresent()) {
                continue;
            }
            long income = income(server, tribute.payer(), day - 1);
            long amount = income * tribute.share() / 1_000L;
            if (amount <= 0) {
                continue;
            }
            Outcome paid = pay(server, tribute.payer(), tribute.payee(), Math.min(amount, funds(server, tribute.payer())), day);
            if (paid.ok()) {
                Component money = io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(amount,
                        io.github.sekerator.kingdomworld.common.trade.Purses.units(server, tribute.payer()));
                tellDiplomats(server, tribute.payee(), Component.translatable(KEY + "tribute_got", name(server, tribute.payer()), money));
                tellDiplomats(server, tribute.payer(), Component.translatable(KEY + "tribute_paid", name(server, tribute.payee()), money));
            }
        }
    }

    // ---------------------------------------------------------------- the end of wars

    /**
     * Ends a war on terms: the indemnity moves between the treasuries first, then the truce, vassalage, tribute and claims are
     * recorded. Peace is refused if the loser cannot pay; a side that gave in or a war ended by an operator pays what it has.
     */
    public static Outcome end(MinecraftServer server, War war, War.Result how, PeaceTerms asked) {
        if (isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        DiplomacyState state = state(server);
        DiplomacyRules rules = DiplomacySettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        long day = day(server);
        PeaceTerms terms = state.feasible(war, asked, rules);
        Optional<DiplomacyState.Settlement> settlement = DiplomacyState.settlement(war, terms);
        if (settlement.isPresent() && how != War.Result.PEACE) {
            long can = Math.min(settlement.get().amount(), funds(server, settlement.get().payer()));
            terms = terms.withIndemnity(can);
            settlement = DiplomacyState.settlement(war, terms);
        }
        Outcome check = state.checkTerms(war, terms);
        if (!check.ok()) {
            return check;
        }
        if (settlement.isPresent()) {
            Outcome paid = pay(server, settlement.get().payer(), settlement.get().payee(), settlement.get().amount(), day);
            if (!paid.ok()) {
                return paid;
            }
        }
        Outcome ended = state.endWar(war.id(), how, terms, now, day, rules);
        if (!ended.ok()) {
            LOGGER.error("War #{} could not end after its indemnity was paid: {}", war.id(), ended.error());
            return ended;
        }
        announcePeace(server, war, how, terms, now);
        recall(server, war);
        return Outcome.DONE;
    }

    /**
     * After the peace the squads of rulers of the world that held places on the land of the former enemy come home.
     */
    private static void recall(MinecraftServer server, War war) {
        if (ArmyWork.isReadOnly(server)) {
            return;
        }
        for (War.Side side : War.Side.values()) {
            Set<UUID> enemies = war.members(side.enemy());
            for (UUID member : war.members(side)) {
                if (!isNpc(server, member)) {
                    continue;
                }
                for (ArmySquad squad : List.copyOf(ArmyWork.state(server).squadsOf(member))) {
                    if (onLandOf(server, squad).map(enemies::contains).orElse(false)) {
                        ArmyWork.order(server, null, squad.id(), Order.NONE);
                    }
                }
            }
        }
    }

    /**
     * Ends a war by peace offered and accepted.
     */
    static Outcome acceptPeace(MinecraftServer server, Proposal proposal, UUID kingdomId) {
        DiplomacyState state = state(server);
        DiplomacyRules rules = DiplomacySettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        Outcome check = state.checkAccept(proposal.id(), kingdomId, now, Long.MAX_VALUE, rules);
        if (!check.ok()) {
            return check;
        }
        War war = state.war(proposal.war()).orElseThrow();
        Outcome ended = end(server, war, War.Result.PEACE, proposal.terms());
        if (ended.ok()) {
            state.markAccepted(proposal.id(), now);
        }
        return ended;
    }

    private static void announcePeace(MinecraftServer server, War war, War.Result how, PeaceTerms terms, long now) {
        DiplomacyState state = state(server);
        String detail = terms.isWhite() ? "white" : how.id();
        state.record(now, "peace", war.attacker(), war.defender(), war.id(), detail);
        Component text = WarTexts.terms(server, war, terms);
        for (War.Side side : War.Side.values()) {
            for (UUID member : war.members(side)) {
                tell(server, member, Component.translatable(KEY + "peace." + how.id(), name(server, war.leader(side.enemy())), text)
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        LOGGER.info("War #{} between {} and {} ended: {}, {}", war.id(), name(server, war.attacker()).getString(),
                name(server, war.defender()).getString(), how.id(), text.getString());
    }

    // ---------------------------------------------------------------- relations as shown

    /**
     * How one kingdom stands with another as shown: the relation of the pair and, for a ruler of the world, its own memory of
     * the other.
     */
    public static int relation(MinecraftServer server, UUID from, UUID to) {
        int value = state(server).relation(from, to);
        if (isNpc(server, from)) {
            Optional<RulerMemory> memory = NpcWork.state(server).existingRuler(from);
            if (memory.isPresent()) {
                value += memory.get().relation(to);
            }
        }
        return Math.max(-100, Math.min(100, value));
    }

    /**
     * The ruler of a kingdom of the world as diplomacy sees it.
     */
    static Optional<DiplomacyMind.Self> self(MinecraftServer server, UUID kingdomId) {
        Optional<NpcKingdom> npc = NpcWork.kingdom(server, kingdomId);
        if (npc.isEmpty()) {
            return Optional.empty();
        }
        Long last = state(server).lastDeclared().get(kingdomId);
        return Optional.of(new DiplomacyMind.Self(kingdomId, npc.get().character(), npc.get().traits(), NpcDiplomacy.power(server, kingdomId),
                poor(server, kingdomId), last));
    }

    /**
     * Another kingdom as a ruler sees it.
     */
    static DiplomacyMind.Neighbor neighbor(MinecraftServer server, UUID self, UUID other, double distance, long now, DiplomacyRules rules) {
        DiplomacyState state = state(server);
        boolean recent = state.lastWarEnded(other).map(end -> now - end < (long) rules.npcRecentWarDays() * CitizenNeeds.TICKS_PER_DAY)
                .orElse(false) || state.fightsAnyWar(other);
        return new DiplomacyMind.Neighbor(other, isNpc(server, other), NpcDiplomacy.power(server, other), distance, relation(server, self, other),
                state.bond(self, other, now), playable(server, other) && !shielded(server, other), recent, state.lastNpcWar().get(other));
    }

    /**
     * The squads a kingdom keeps by the soldiers it has.
     */
    static int soldiers(MinecraftServer server, UUID kingdomId) {
        List<Soldier> soldiers = ArmyWork.state(server).soldiersOf(kingdomId);
        return soldiers.size();
    }
}
