package io.github.sekerator.kingdomworld.common.diplomacy;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.diplomacy.CedeLand;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.diplomacy.Treaty;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * What kings, chancellors, rulers of the world and operators do in diplomacy: declare wars, make, answer and call off offers,
 * break treaties, answer the calls of allies. The rights are checked here, on the server: {@code actor} is the player acting for
 * the kingdom, {@code null} for a ruler of the world or an operator.
 */
public final class DiplomacyActions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = WarWork.KEY;

    private DiplomacyActions() {
    }

    static Outcome allowed(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, Permission right) {
        if (KingdomWorldApi.kingdom(server, kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !KingdomWorldApi.state(server).membership().hasPermission(actor, kingdomId, right)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- war

    /**
     * Declares war. The defender must be able to defend itself: a kingdom of the world, or a kingdom of players with a player
     * online at the keyboard.
     *
     * @param breakTreaty whether a pact or an alliance with the defender is broken for it
     */
    public static Outcome declare(MinecraftServer server, @Nullable UUID actor, UUID attacker, UUID defender, WarGoal goal, boolean breakTreaty) {
        return declare(server, actor, attacker, defender, goal, breakTreaty, false);
    }

    /**
     * Declares war for an operator: the defender need not have a player online.
     */
    public static Outcome declareByOperator(MinecraftServer server, UUID attacker, UUID defender, WarGoal goal, boolean breakTreaty) {
        return declare(server, null, attacker, defender, goal, breakTreaty, true);
    }

    private static Outcome declare(MinecraftServer server, @Nullable UUID actor, UUID attacker, UUID defender, WarGoal goal, boolean breakTreaty,
                                   boolean force) {
        Outcome right = allowed(server, actor, attacker, Permission.DECLARE_WAR);
        if (!right.ok()) {
            return right;
        }
        if (KingdomWorldApi.kingdom(server, defender).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (goal.inTheWorld() && WarWork.isNpc(server, attacker) && !WarWork.isNpc(server, defender) && !force) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        DiplomacyRules rules = DiplomacySettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        long day = WarWork.day(server);
        boolean playable = force || WarWork.playable(server, defender);
        DiplomacyState.Declared[] result = new DiplomacyState.Declared[1];
        Outcome outcome = WarWork.change(server, "declare war", state -> {
            result[0] = state.declare(attacker, defender, goal, now, day, rules, breakTreaty, playable);
            return result[0].outcome();
        });
        if (!outcome.ok()) {
            return outcome;
        }
        War war = result[0].war().orElseThrow();
        DiplomacyState state = WarWork.state(server);
        state.record(now, "declared", attacker, defender, war.id(), goal.id());
        if (WarWork.isNpc(server, attacker) && !WarWork.isNpc(server, defender)) {
            state.noteNpcWar(defender, day);
        }
        Component goalText = WarTexts.goal(goal);
        Component when = WarTexts.ticks(war.starts() - now);
        WarWork.tell(server, attacker, Component.translatable(KEY + "declared_by_us", WarWork.name(server, defender), goalText, when)
                .withStyle(ChatFormatting.GOLD));
        WarWork.tell(server, defender, Component.translatable(KEY + "declared_on_us", WarWork.name(server, attacker), goalText, when)
                .withStyle(ChatFormatting.RED));
        for (War.Side side : War.Side.values()) {
            for (UUID ally : war.allies(side)) {
                WarWork.tell(server, ally, Component.translatable(KEY + "joined_as_vassal", WarWork.name(server, war.leader(side)),
                        WarWork.name(server, war.leader(side.enemy()))).withStyle(ChatFormatting.GOLD));
            }
        }
        war.calls().forEach((ally, side) -> WarWork.tellDiplomats(server, ally, Component.translatable(KEY + "called",
                WarWork.name(server, war.leader(side)), WarWork.name(server, war.leader(side.enemy()))).withStyle(ChatFormatting.GOLD)));
        result[0].broken().ifPresent(treaty -> brokenWord(server, attacker, defender, treaty, now));
        LOGGER.info("{} declared war #{} on {} for {}", WarWork.name(server, attacker).getString(), war.id(),
                WarWork.name(server, defender).getString(), goal.id());
        return Outcome.DONE;
    }

    private static void brokenWord(MinecraftServer server, UUID breaker, UUID victim, Treaty treaty, long now) {
        WarWork.state(server).record(now, "broken", breaker, victim, 0, treaty.kind().id());
        WarWork.tell(server, victim, Component.translatable(KEY + "broken." + treaty.kind().id(), WarWork.name(server, breaker))
                .withStyle(ChatFormatting.RED));
    }

    /**
     * An ally answers the call to a war.
     */
    public static Outcome answerCall(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long warId, boolean join) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_DIPLOMACY);
        if (!right.ok()) {
            return right;
        }
        Optional<War> war = WarWork.state(server).war(warId);
        Optional<War.Side> side = war.map(w -> w.calls().get(kingdomId));
        Outcome outcome = WarWork.change(server, "answer a call to war", state -> state.answerCall(warId, kingdomId, join));
        if (!outcome.ok() || side.isEmpty()) {
            return outcome;
        }
        UUID leader = war.get().leader(side.get());
        long now = KingdomWorldApi.gameTime(server);
        WarWork.state(server).record(now, join ? "joined" : "refused", kingdomId, leader, warId, "");
        WarWork.tell(server, leader, Component.translatable(KEY + (join ? "ally_joined" : "ally_refused"), WarWork.name(server, kingdomId))
                .withStyle(join ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        if (join) {
            WarWork.tell(server, kingdomId, Component.translatable(KEY + "we_joined", WarWork.name(server, leader),
                    WarWork.name(server, war.get().leader(side.get().enemy()))).withStyle(ChatFormatting.GOLD));
        }
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- offers

    /**
     * Makes an offer to another kingdom.
     *
     * @param vassal for vassalage, which of the two would become the vassal
     * @param warId  for peace, the war
     * @param terms  for peace, the terms
     */
    public static Outcome propose(MinecraftServer server, @Nullable UUID actor, UUID from, UUID to, Proposal.Kind kind, @Nullable UUID vassal,
                                  long warId, PeaceTerms terms) {
        Outcome right = allowed(server, actor, from, Permission.MANAGE_DIPLOMACY);
        if (!right.ok()) {
            return right;
        }
        if (KingdomWorldApi.kingdom(server, to).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        DiplomacyRules rules = DiplomacySettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        long funds = 0;
        if (kind == Proposal.Kind.PEACE) {
            Optional<War> war = WarWork.state(server).war(warId);
            if (war.isPresent() && !terms.isWhite()) {
                UUID loser = DiplomacyState.loser(war.get(), terms);
                funds = loser == null ? 0 : WarWork.funds(server, loser);
            }
        }
        long loserFunds = funds;
        Proposal[] made = new Proposal[1];
        Outcome outcome = WarWork.change(server, "make an offer", state -> {
            DiplomacyState.Proposed proposed = state.propose(from, to, kind, vassal, warId, terms, now, loserFunds, rules);
            made[0] = proposed.proposal().orElse(null);
            return proposed.outcome();
        });
        if (!outcome.ok()) {
            return outcome;
        }
        Component what = WarTexts.offerText(server, made[0]);
        WarWork.tellDiplomats(server, to, Component.translatable(KEY + "offer_got", WarWork.name(server, from), what).withStyle(ChatFormatting.AQUA));
        WarWork.tellDiplomats(server, from, Component.translatable(KEY + "offer_sent", WarWork.name(server, to), what));
        return Outcome.DONE;
    }

    /**
     * Asks another kingdom to cede a province that touches the land of the asker: for the price its ruler asks, when a ruler
     * of the world holds it (nothing when it would give it away), or for nothing from a kingdom of players, which answers the
     * offer itself. The money moves only when the offer is accepted.
     */
    public static Outcome askCede(MinecraftServer server, @Nullable UUID actor, UUID from, UUID to, int province) {
        Outcome right = allowed(server, actor, from, Permission.MANAGE_DIPLOMACY);
        if (!right.ok()) {
            return right;
        }
        if (KingdomWorldApi.kingdom(server, to).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (io.github.sekerator.kingdomworld.common.land.ProvinceSavedData.get(server).isReadOnly()) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        DiplomacyRules rules = DiplomacySettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        CedeLand land = Cessions.land(server, from, province);
        long price = land == null || !to.equals(land.holder()) ? 0 : Cessions.price(server, to, from, province);
        Outcome check = WarWork.state(server).checkCede(from, to, province, price, land, now, Long.MAX_VALUE);
        if (!check.ok()) {
            return check;
        }
        if (Cessions.chartered(server, province)) {
            return Outcome.fail(KingdomError.NOT_FOR_SALE);
        }
        if (price > 0) {
            // What the price costs the asker in its currency by the rates of today; the money moves only on acceptance.
            Outcome money = KingdomWorldApi.state(server).economy().checkLandPayment(from, to, price);
            if (!money.ok()) {
                return money;
            }
        }
        Proposal[] made = new Proposal[1];
        Outcome outcome = WarWork.change(server, "ask for a province", state -> {
            DiplomacyState.Proposed proposed = state.proposeCede(from, to, province, price, land, now, Long.MAX_VALUE, rules);
            made[0] = proposed.proposal().orElse(null);
            return proposed.outcome();
        });
        if (!outcome.ok()) {
            return outcome;
        }
        Component what = WarTexts.offerText(server, made[0]);
        WarWork.tellDiplomats(server, to, Component.translatable(KEY + "offer_got", WarWork.name(server, from), what).withStyle(ChatFormatting.AQUA));
        WarWork.tellDiplomats(server, from, Component.translatable(KEY + "offer_sent", WarWork.name(server, to), what));
        LOGGER.info("{} asked {} to cede the province {} for {}", WarWork.name(server, from).getString(), WarWork.name(server, to).getString(),
                province, price);
        return Outcome.DONE;
    }

    /**
     * Accepts or declines an offer made to a kingdom.
     */
    public static Outcome answer(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long proposalId, boolean accept) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_DIPLOMACY);
        if (!right.ok()) {
            return right;
        }
        Optional<Proposal> found = WarWork.state(server).proposal(proposalId);
        if (found.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_PROPOSAL);
        }
        Proposal proposal = found.get();
        long now = KingdomWorldApi.gameTime(server);
        DiplomacyRules rules = DiplomacySettings.rules();
        if (!accept) {
            Outcome outcome = WarWork.change(server, "decline an offer", state -> state.decline(proposalId, kingdomId, now));
            if (outcome.ok()) {
                WarWork.tellDiplomats(server, proposal.from(), Component.translatable(KEY + "offer_declined", WarWork.name(server, kingdomId),
                        WarTexts.offer(proposal.kind())).withStyle(ChatFormatting.GOLD));
            }
            return outcome;
        }
        if (WarWork.isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        Outcome outcome;
        if (proposal.kind() == Proposal.Kind.PEACE) {
            outcome = WarWork.acceptPeace(server, proposal, kingdomId);
        } else if (proposal.kind() == Proposal.Kind.CEDE) {
            outcome = Cessions.accept(server, proposal, kingdomId);
        } else {
            outcome = WarWork.state(server).accept(proposalId, kingdomId, now, WarWork.day(server), rules);
            if (outcome.ok()) {
                String kind = switch (proposal.kind()) {
                    case PACT -> "pact";
                    case ALLIANCE -> "alliance";
                    case VASSALAGE -> "vassal";
                    case RELEASE -> "released";
                    case PEACE -> "peace";
                    case CEDE -> "ceded";
                };
                UUID first = proposal.kind() == Proposal.Kind.VASSALAGE ? proposal.vassal() : proposal.from();
                UUID second = proposal.kind() == Proposal.Kind.VASSALAGE ? proposal.overlord() : proposal.to();
                WarWork.state(server).record(now, kind, first, second, 0, "");
            }
        }
        if (outcome.ok()) {
            Component what = WarTexts.offerText(server, proposal);
            WarWork.tellDiplomats(server, proposal.from(), Component.translatable(KEY + "offer_accepted", WarWork.name(server, kingdomId), what)
                    .withStyle(ChatFormatting.GREEN));
            WarWork.tellDiplomats(server, kingdomId, Component.translatable(KEY + "offer_we_accepted", WarWork.name(server, proposal.from()), what)
                    .withStyle(ChatFormatting.GREEN));
        }
        return outcome;
    }

    public static Outcome cancel(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long proposalId) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_DIPLOMACY);
        if (!right.ok()) {
            return right;
        }
        long now = KingdomWorldApi.gameTime(server);
        return WarWork.change(server, "call off an offer", state -> state.cancel(proposalId, kingdomId, now));
    }

    /**
     * Breaks a pact or an alliance, or lets a vassal go.
     */
    public static Outcome breakTreaty(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, UUID other) {
        Outcome right = allowed(server, actor, kingdomId, Permission.MANAGE_DIPLOMACY);
        if (!right.ok()) {
            return right;
        }
        long now = KingdomWorldApi.gameTime(server);
        DiplomacyState.Broken[] result = new DiplomacyState.Broken[1];
        Outcome outcome = WarWork.change(server, "break a treaty", state -> {
            result[0] = state.breakTreaty(kingdomId, other, now);
            return result[0].outcome();
        });
        if (!outcome.ok()) {
            return outcome;
        }
        Treaty treaty = result[0].treaty().orElseThrow();
        if (treaty.kind() == io.github.sekerator.kingdomworld.core.diplomacy.Bond.VASSAL) {
            WarWork.state(server).record(now, "released", other, kingdomId, 0, "");
            WarWork.tell(server, other, Component.translatable(KEY + "let_go", WarWork.name(server, kingdomId)).withStyle(ChatFormatting.GREEN));
        } else {
            brokenWord(server, kingdomId, other, treaty, now);
        }
        return Outcome.DONE;
    }
}
