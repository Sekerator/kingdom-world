package io.github.sekerator.kingdomworld.common.nobility;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacyActions;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.nobility.Demand;
import io.github.sekerator.kingdomworld.core.nobility.Fief;
import io.github.sekerator.kingdomworld.core.nobility.Grant;
import io.github.sekerator.kingdomworld.core.nobility.NobilityRules;
import io.github.sekerator.kingdomworld.core.nobility.NobilityState;
import io.github.sekerator.kingdomworld.core.nobility.NobleHouse;
import io.github.sekerator.kingdomworld.core.nobility.Plot;
import io.github.sekerator.kingdomworld.core.nobility.Spy;
import io.github.sekerator.kingdomworld.core.nobility.Title;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * What kings, chancellors, spymasters, rulers of the world and operators do with the nobility. The rights are checked here,
 * on the server: {@code actor} is the player acting for the kingdom, {@code null} for a ruler of the world or an operator.
 * Money moves only between treasuries and accounts, by the books.
 */
public final class NobilityActions {
    private static final String KEY = NobilityTexts.KEY;

    private NobilityActions() {
    }

    static Outcome allowed(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, Permission right) {
        if (KingdomWorldApi.kingdom(server, kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (NobilityWork.isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        if (actor != null && !KingdomWorldApi.state(server).membership().hasPermission(actor, kingdomId, right)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- titles

    /**
     * Whether the player acting is the king of the kingdom (operators and rulers of the world act as kings).
     */
    static boolean king(MinecraftServer server, @Nullable UUID actor, UUID kingdomId) {
        return actor == null || KingdomWorldApi.kingdom(server, kingdomId).map(kingdom -> actor.equals(kingdom.rulerId())).orElse(false);
    }

    /**
     * Grants a title with a free fief of the kingdom to one of its houses, to a new house ({@code house} = -1) or to a player
     * who is its subject. A title of a player brings a share of the income from the treasury, so only the king grants it, and
     * only the king raises new houses, as many as the rules allow; a chancellor grants titles to houses that are there. A player
     * of a kingdom of the world is made noble by its ruler or an operator only.
     */
    public static Outcome grant(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, Title title, Fief fief, long house, @Nullable UUID player) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_NOBILITY);
        if (!right.ok()) {
            return right;
        }
        if ((player != null || house < 0) && !king(server, actor, kingdomId)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        if (house < 0 && NobilityWork.state(server).houses(kingdomId).size() >= NobilitySettings.rules().maxHouses()) {
            return Outcome.fail(KingdomError.LIMIT_REACHED);
        }
        if (!NobilityWork.fiefs(server, kingdomId).contains(fief)) {
            return Outcome.fail(KingdomError.FIEF_TAKEN);
        }
        NobilityState state = NobilityWork.state(server);
        if (state.holderOf(kingdomId, fief).isPresent()) {
            return Outcome.fail(KingdomError.FIEF_TAKEN);
        }
        long day = NobilityWork.day(server);
        if (player != null) {
            boolean npc = WarWork.isNpc(server, kingdomId);
            if (npc ? actor != null : !KingdomWorldApi.state(server).membership().isMember(player, kingdomId)) {
                return Outcome.fail(KingdomError.NOT_A_MEMBER);
            }
            Outcome outcome = state.grant(kingdomId, title, fief, 0, player, day);
            if (outcome.ok()) {
                ServerPlayer online = server.getPlayerList().getPlayer(player);
                if (online != null) {
                    online.sendSystemMessage(Component.translatable(KEY + "msg.granted_you", NobilityTexts.title(title), NobilityTexts.fief(server, fief),
                            WarWork.name(server, kingdomId)).withStyle(ChatFormatting.GOLD));
                }
            }
            return outcome;
        }
        long holder = house;
        if (house < 0) {
            NobleHouse raised = state.found(kingdomId, (long) (NobilityWork.rolls().getAsDouble() * Long.MAX_VALUE) ^ day ^ fief.code().hashCode(), day);
            raised.setRaised(true);
            holder = raised.id();
        }
        Outcome outcome = state.grant(kingdomId, title, fief, holder, null, day);
        if (!outcome.ok() && house < 0) {
            state.removeHouse(holder);
        }
        return outcome;
    }

    public static Outcome revoke(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long grantId) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_NOBILITY);
        if (!right.ok()) {
            return right;
        }
        NobilityState state = NobilityWork.state(server);
        Optional<Grant> grant = state.grant(grantId).filter(g -> g.kingdomId().equals(kingdomId));
        if (grant.isEmpty()) {
            return Outcome.fail(KingdomError.NO_SUCH_NOBLE);
        }
        state.revoke(grantId, NobilityWork.day(server));
        if (grant.get().heldByPlayer()) {
            ServerPlayer online = server.getPlayerList().getPlayer(grant.get().player());
            if (online != null) {
                online.sendSystemMessage(Component.translatable(KEY + "msg.revoked_you", NobilityTexts.title(grant.get().title()),
                        WarWork.name(server, kingdomId)).withStyle(ChatFormatting.RED));
            }
        }
        return Outcome.DONE;
    }

    /**
     * Hands a title to another house ({@code house} &gt; 0) or a subject ({@code player}).
     */
    public static Outcome transfer(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long grantId, long house, @Nullable UUID player) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_NOBILITY);
        if (!right.ok()) {
            return right;
        }
        NobilityState state = NobilityWork.state(server);
        if (state.grant(grantId).filter(g -> g.kingdomId().equals(kingdomId)).isEmpty()) {
            return Outcome.fail(KingdomError.NO_SUCH_NOBLE);
        }
        if (player != null && !king(server, actor, kingdomId)) {
            // A title of a player brings money from the treasury: only the king hands one to a player.
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        if (player != null && !WarWork.isNpc(server, kingdomId) && !KingdomWorldApi.state(server).membership().isMember(player, kingdomId)) {
            return Outcome.fail(KingdomError.NOT_A_MEMBER);
        }
        return state.transfer(grantId, house, player, NobilityWork.day(server));
    }

    // ---------------------------------------------------------------- demands and verdicts

    /**
     * The crown answers the demand of a party. Agreeing does what was asked (a lower tax, works or loot paid to the house, a
     * title, a declaration of war, an offer of peace); a bargain gives the house half of what it was worth.
     */
    public static Outcome answer(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long demandId, Demand.Answer answer) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_NOBILITY);
        if (!right.ok()) {
            return right;
        }
        NobilityState state = NobilityWork.state(server);
        Optional<Demand> found = state.demand(demandId).filter(d -> d.kingdomId().equals(kingdomId));
        if (found.isEmpty() || answer == Demand.Answer.LAPSED) {
            return Outcome.fail(KingdomError.NOT_PENDING);
        }
        Demand demand = found.get();
        NobilityRules rules = NobilitySettings.rules();
        long day = NobilityWork.day(server);
        var economy = KingdomWorldApi.state(server).economy();
        Optional<NobleHouse> house = state.house(demand.house());
        if (answer == Demand.Answer.BARGAIN) {
            long gift = demand.bargain(rules.demandValue());
            Outcome paid = economy.nobilityPayment(kingdomId, gift, day);
            if (!paid.ok()) {
                return paid;
            }
            house.ifPresent(h -> state.addWealth(h, gift));
        } else if (answer == Demand.Answer.ACCEPT) {
            Outcome done = fulfil(server, actor, demand, house, day);
            if (!done.ok()) {
                return done;
            }
        }
        return state.answer(demandId, answer, day);
    }

    private static Outcome fulfil(MinecraftServer server, @Nullable UUID actor, Demand demand, Optional<NobleHouse> house, long day) {
        UUID kingdomId = demand.kingdomId();
        NobilityState state = NobilityWork.state(server);
        var economy = KingdomWorldApi.state(server).economy();
        return switch (demand.kind()) {
            case LOWER_TAX -> {
                long tax = economy.treasury(kingdomId).map(Treasury::pollTax).orElse(0L);
                long lower = Math.max(0, tax * (100 - Math.min(100, demand.value())) / 100);
                yield lower == tax ? Outcome.DONE : economy.setPollTax(kingdomId, lower, EconomySettings.rules().maxPollTax());
            }
            case BUILD, SHARE_LOOT -> {
                Outcome paid = economy.nobilityPayment(kingdomId, demand.value(), day);
                if (paid.ok()) {
                    house.ifPresent(h -> state.addWealth(h, demand.value()));
                }
                yield paid;
            }
            case GRANT_TITLE -> {
                Optional<Fief> free = NobilityWork.freeFiefs(server, kingdomId).stream().findFirst();
                if (free.isEmpty() || house.isEmpty()) {
                    yield Outcome.fail(KingdomError.FIEF_TAKEN);
                }
                yield state.grant(kingdomId, NobilityWork.titleFor(free.get()), free.get(), house.get().id(), null, day);
            }
            case DECLARE_WAR -> demand.target() == null ? Outcome.fail(KingdomError.UNKNOWN_KINGDOM)
                    : DiplomacyActions.declare(server, actor, kingdomId, demand.target(), WarGoal.RAID, false);
            case MAKE_PEACE -> {
                Optional<War> war = WarWork.state(server).warBetween(kingdomId, demand.target() == null ? kingdomId : demand.target());
                Optional<War.Side> side = war.flatMap(w -> w.sideOf(kingdomId)).filter(s -> war.get().leader(s).equals(kingdomId));
                if (war.isEmpty() || side.isEmpty()) {
                    // The war is over already: the party has what it wanted.
                    yield Outcome.DONE;
                }
                yield DiplomacyActions.propose(server, actor, kingdomId, war.get().leader(side.get().enemy()), Proposal.Kind.PEACE, null,
                        war.get().id(), PeaceTerms.WHITE);
            }
        };
    }

    /**
     * The crown decides the fate of a house whose plot was found out.
     */
    public static Outcome verdict(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long plotId, Plot.Verdict verdict) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_NOBILITY);
        if (!right.ok()) {
            return right;
        }
        NobilityState state = NobilityWork.state(server);
        if (state.plot(plotId).filter(p -> p.kingdomId().equals(kingdomId)).isEmpty()) {
            return Outcome.fail(KingdomError.NOT_PENDING);
        }
        return state.verdict(plotId, verdict, NobilityWork.day(server), (long) (Math.random() * Long.MAX_VALUE));
    }

    // ---------------------------------------------------------------- spies

    /**
     * Hires a spy among the agents of the guild for the treasury.
     */
    public static Outcome hire(MinecraftServer server, @Nullable UUID actor, UUID kingdomId) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_SPIES);
        if (!right.ok()) {
            return right;
        }
        NobilityState state = NobilityWork.state(server);
        NobilityRules rules = NobilitySettings.rules();
        int limit = WarWork.isNpc(server, kingdomId) ? rules.npcMaxSpies() : rules.maxSpies();
        if (state.spiesOf(kingdomId).size() >= limit) {
            return Outcome.fail(KingdomError.TOO_MANY_SPIES);
        }
        long day = NobilityWork.day(server);
        if (rules.spyHire() > 0) {
            Outcome paid = KingdomWorldApi.state(server).economy().nobilityPayment(kingdomId, rules.spyHire(), day);
            if (!paid.ok()) {
                return paid;
            }
        }
        double roll = NobilityWork.rolls().getAsDouble();
        state.hire(kingdomId, (int) (roll * Integer.MAX_VALUE) ^ (int) day, NobilityWork.gender(roll), 30 + (int) (roll * 40), day);
        return Outcome.DONE;
    }

    public static Outcome send(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long spyId, Spy.Task task, UUID target, long house) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_SPIES);
        if (!right.ok()) {
            return right;
        }
        if (target == null || KingdomWorldApi.kingdom(server, target).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        NobilityState state = NobilityWork.state(server);
        if (state.spy(spyId).filter(s -> s.owner().equals(kingdomId)).isEmpty()) {
            return Outcome.fail(KingdomError.NO_SUCH_NOBLE);
        }
        long aim = house;
        if (task == Spy.Task.ASSASSINATE) {
            if (!WarWork.isNpc(server, target)) {
                return Outcome.fail(KingdomError.SPY_BUSY);
            }
            if (aim <= 0) {
                aim = state.houses(target).stream().mapToLong(NobleHouse::id).findFirst().orElse(0);
            }
        }
        return state.send(spyId, task, target, aim, NobilityWork.day(server));
    }

    public static Outcome station(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long spyId, boolean counter) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_SPIES);
        if (!right.ok()) {
            return right;
        }
        NobilityState state = NobilityWork.state(server);
        if (state.spy(spyId).filter(s -> s.owner().equals(kingdomId)).isEmpty()) {
            return Outcome.fail(KingdomError.NO_SUCH_NOBLE);
        }
        return state.station(spyId, counter);
    }

    /**
     * Pays the ransom of a caught spy from our treasury into the treasury that holds the spy.
     */
    public static Outcome ransom(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long spyId) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_SPIES);
        if (!right.ok()) {
            return right;
        }
        NobilityState state = NobilityWork.state(server);
        Optional<Spy> spy = state.spy(spyId).filter(s -> s.owner().equals(kingdomId) && s.status() == Spy.Status.CAPTURED);
        if (spy.isEmpty()) {
            return Outcome.fail(KingdomError.NOT_PENDING);
        }
        UUID captor = spy.get().captor().orElseThrow();
        long day = NobilityWork.day(server);
        if (spy.get().ransom() > 0) {
            Outcome paid = KingdomWorldApi.state(server).economy().nobilityTransfer(kingdomId, captor, spy.get().ransom(), day);
            if (!paid.ok()) {
                return paid;
            }
        }
        return state.ransomed(spyId, day);
    }

    public static Outcome dismiss(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long spyId) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_SPIES);
        if (!right.ok()) {
            return right;
        }
        NobilityState state = NobilityWork.state(server);
        if (state.spy(spyId).filter(s -> s.owner().equals(kingdomId)).isEmpty()) {
            return Outcome.fail(KingdomError.NO_SUCH_NOBLE);
        }
        state.dismiss(spyId);
        return Outcome.DONE;
    }
}
