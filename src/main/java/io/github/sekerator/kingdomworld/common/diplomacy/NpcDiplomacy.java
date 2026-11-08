package io.github.sekerator.kingdomworld.common.diplomacy;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyMind;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Diplomacy of the rulers of the world: they answer offers and calls of allies at once, and once a game day make offers,
 * declare wars by character and offer peace in the wars they lead. Their squads go to the field when a war starts.
 */
final class NpcDiplomacy {
    private static final Logger LOGGER = LogUtils.getLogger();

    private NpcDiplomacy() {
    }

    /**
     * Rulers of the world answer the offers made to them and the calls of their allies.
     */
    static void answer(MinecraftServer server, DiplomacyState state, DiplomacyRules rules, long now, long day) {
        for (Proposal proposal : List.copyOf(state.proposals())) {
            if (!proposal.open() || !WarWork.isNpc(server, proposal.to())) {
                continue;
            }
            Optional<DiplomacyMind.Self> self = WarWork.self(server, proposal.to());
            if (self.isEmpty()) {
                continue;
            }
            DiplomacyMind.Verdict verdict;
            if (proposal.kind() == Proposal.Kind.PEACE) {
                Optional<War> war = state.war(proposal.war());
                if (war.isEmpty() || war.get().over()) {
                    continue;
                }
                War.Side side = war.get().sideOf(proposal.to()).orElse(War.Side.DEFENDER);
                PeaceTerms enemyMay = DiplomacyState.terms(war.get(), side.enemy(), false, WarWork.funds(server, proposal.to()), rules);
                verdict = DiplomacyMind.peace(war.get(), side, proposal.terms(), enemyMay, rules);
            } else {
                DiplomacyMind.Neighbor from = WarWork.neighbor(server, proposal.to(), proposal.from(), 0.0, now, rules);
                verdict = DiplomacyMind.answer(proposal, self.get(), from);
            }
            Outcome outcome = DiplomacyActions.answer(server, null, proposal.to(), proposal.id(), verdict.accept());
            if (verdict.accept() && !outcome.ok()) {
                // The ruler wanted to accept but could not (an indemnity it cannot pay): it declines instead.
                DiplomacyActions.answer(server, null, proposal.to(), proposal.id(), false);
                verdict = new DiplomacyMind.Verdict(false, DiplomacyMind.TOO_MUCH);
            }
            if (!verdict.accept()) {
                WarWork.tellDiplomats(server, proposal.from(), Component.translatable(WarWork.KEY + "npc_reason." + verdict.reason(),
                        WarWork.name(server, proposal.to())).withStyle(ChatFormatting.GRAY));
            }
        }
        for (War war : List.copyOf(state.wars())) {
            if (war.over()) {
                continue;
            }
            for (Map.Entry<UUID, War.Side> call : Map.copyOf(war.calls()).entrySet()) {
                UUID ally = call.getKey();
                if (!WarWork.isNpc(server, ally)) {
                    continue;
                }
                Optional<DiplomacyMind.Self> self = WarWork.self(server, ally);
                if (self.isEmpty()) {
                    continue;
                }
                boolean join = DiplomacyMind.joins(self.get(), WarWork.relation(server, ally, war.leader(call.getValue())));
                DiplomacyActions.answerCall(server, null, ally, war.id(), join);
                if (join && war.active()) {
                    muster(server, ally, war.leader(call.getValue().enemy()), true);
                }
            }
        }
    }

    /**
     * Once a game day: offers, declarations and offers of peace of every ruler of the world.
     */
    static void daily(MinecraftServer server, DiplomacyState state, DiplomacyRules rules, long now, long day) {
        // The rulers take their turns of diplomacy one a second, so that no tick carries them all.
        QUEUE.clear();
        POWER.clear();
        for (Kingdom kingdom : KingdomWorldApi.kingdoms(server)) {
            if (NpcWork.isNpc(server, kingdom.id())) {
                QUEUE.add(kingdom.id());
            }
        }
        queueDay = day;
    }

    /**
     * The turn of diplomacy of the next ruler of the world waiting for it today, if any. Called once a second.
     */
    static void work(MinecraftServer server, DiplomacyState state, DiplomacyRules rules, long now) {
        if (QUEUE.isEmpty()) {
            return;
        }
        // First the strength of every army, one a second, then one ruler a second.
        for (Kingdom kingdom : KingdomWorldApi.kingdoms(server)) {
            if (!POWER.containsKey(kingdom.id())) {
                power(server, kingdom.id());
                return;
            }
        }
        UUID next = QUEUE.poll();
        if (next != null && KingdomWorldApi.kingdom(server, next).isPresent()) {
            turn(server, state, rules, now, queueDay, next);
        }
    }

    static void reset() {
        QUEUE.clear();
        POWER.clear();
    }

    /**
     * The strength of the army of a kingdom, counted once a game day for the decisions of rulers.
     */
    static double power(MinecraftServer server, UUID kingdomId) {
        return POWER.computeIfAbsent(kingdomId, id -> ArmyWork.power(server, id));
    }

    private static final java.util.ArrayDeque<UUID> QUEUE = new java.util.ArrayDeque<>();
    private static final Map<UUID, Double> POWER = new java.util.HashMap<>();
    private static long queueDay;

    private static void turn(MinecraftServer server, DiplomacyState state, DiplomacyRules rules, long now, long day, UUID id) {
        List<Kingdom> kingdoms = KingdomWorldApi.kingdoms(server);
        for (Kingdom kingdom : kingdoms) {
            if (!kingdom.id().equals(id)) {
                continue;
            }
            Optional<NpcKingdom> npc = NpcWork.kingdom(server, kingdom.id());
            Optional<DiplomacyMind.Self> self = WarWork.self(server, kingdom.id());
            Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(kingdom.id()).map(Territory::capital);
            if (npc.isEmpty() || self.isEmpty() || capital.isEmpty()) {
                continue;
            }
            offerPeace(server, state, rules, kingdom.id());
            List<DiplomacyMind.Neighbor> neighbors = new ArrayList<>();
            for (Kingdom other : kingdoms) {
                if (other.id().equals(kingdom.id())) {
                    continue;
                }
                Optional<Capital> theirs = KingdomWorldApi.state(server).territory().territory(other.id()).map(Territory::capital);
                if (theirs.isEmpty() || !theirs.get().dimension().equals(capital.get().dimension())) {
                    continue;
                }
                double distance = Math.hypot(theirs.get().x() - capital.get().x(), theirs.get().z() - capital.get().z());
                if (distance <= WarWork.NEIGHBOUR_DISTANCE) {
                    neighbors.add(WarWork.neighbor(server, kingdom.id(), other.id(), distance, now, rules));
                }
            }
            boolean atWar = !state.warsOf(kingdom.id()).isEmpty();
            for (DiplomacyMind.Move move : DiplomacyMind.decide(self.get(), neighbors, atWar, day, rules)) {
                Outcome outcome = switch (move.kind()) {
                    case DECLARE -> DiplomacyActions.declare(server, null, kingdom.id(), move.target(), move.goal(), false);
                    case PROPOSE_PACT -> DiplomacyActions.propose(server, null, kingdom.id(), move.target(), Proposal.Kind.PACT, null, 0,
                            PeaceTerms.WHITE);
                    case PROPOSE_ALLIANCE -> DiplomacyActions.propose(server, null, kingdom.id(), move.target(), Proposal.Kind.ALLIANCE, null, 0,
                            PeaceTerms.WHITE);
                };
                LOGGER.debug("Ruler of {} decided {} on {}: {}", kingdom.name(), move.kind(), move.target(), outcome.ok() ? "done" : outcome.error());
            }
        }
    }

    /**
     * A ruler leading a side of a war offers peace when tired or beaten, or presses its terms when far ahead; at most one
     * offer of peace waits at a time.
     */
    private static void offerPeace(MinecraftServer server, DiplomacyState state, DiplomacyRules rules, UUID kingdomId) {
        for (War war : state.warsOf(kingdomId)) {
            if (!war.active() || !(war.attacker().equals(kingdomId) || war.defender().equals(kingdomId))) {
                continue;
            }
            War.Side side = war.sideOf(kingdomId).orElseThrow();
            UUID enemy = war.leader(side.enemy());
            boolean waiting = state.openProposals(kingdomId).stream()
                    .anyMatch(p -> p.kind() == Proposal.Kind.PEACE && p.war() == war.id() && p.from().equals(kingdomId));
            if (waiting) {
                continue;
            }
            PeaceTerms mine = state.feasible(war, DiplomacyState.terms(war, side, false, WarWork.funds(server, enemy), rules), rules);
            PeaceTerms theirs = state.feasible(war, DiplomacyState.terms(war, side.enemy(), false, WarWork.funds(server, kingdomId), rules), rules);
            Optional<PeaceTerms> offer = DiplomacyMind.offerPeace(war, side, mine, theirs, rules);
            offer.ifPresent(terms -> DiplomacyActions.propose(server, null, kingdomId, enemy, Proposal.Kind.PEACE, null, war.id(), terms));
        }
    }

    /**
     * A ruler of the world sends its squads to the field when a war starts: free soldiers form a squad, and for an attack
     * half of the squads at home go to hold the capital of the enemy; the others stay to defend.
     */
    static void muster(MinecraftServer server, UUID kingdomId, UUID enemy, boolean attack) {
        if (ArmyWork.isReadOnly(server)) {
            return;
        }
        ArmyState army = ArmyWork.state(server);
        List<UUID> free = new ArrayList<>();
        for (Soldier soldier : army.soldiersOf(kingdomId)) {
            if (army.squadOf(soldier.citizenId()).isEmpty() && free.size() < ArmySquad.MAX_MEMBERS) {
                free.add(soldier.citizenId());
            }
        }
        if (!free.isEmpty()) {
            ArmyWork.change(server, "form a squad for a war", state -> state.formSquad(null, kingdomId, free, ArmySettings.rules(),
                    KingdomWorldApi.state(server)).outcome());
        }
        if (!attack) {
            return;
        }
        WorldPos target = WarWork.capital(server, enemy);
        if (target == null) {
            return;
        }
        // Squads at home go first, then those on patrol or guard; escorts stay with their caravans.
        List<ArmySquad> home = new ArrayList<>();
        List<ArmySquad> watching = new ArrayList<>();
        for (ArmySquad squad : army.squadsOf(kingdomId)) {
            Order.Kind kind = squad.order().kind();
            if (kind == Order.Kind.NONE) {
                home.add(squad);
            } else if (kind == Order.Kind.PATROL || kind == Order.Kind.GUARD) {
                watching.add(squad);
            }
        }
        int all = home.size() + watching.size();
        home.addAll(watching);
        int send = (all + 1) / 2;
        if (send == 0) {
            LOGGER.info("Ruler of {} has no squad to send against {}", WarWork.name(server, kingdomId).getString(),
                    WarWork.name(server, enemy).getString());
        }
        for (int i = 0; i < send; i++) {
            Outcome sent = ArmyWork.order(server, null, home.get(i).id(), Order.hold(target));
            LOGGER.info("Ruler of {} sends squad #{} against {}: {}", WarWork.name(server, kingdomId).getString(), home.get(i).id(),
                    WarWork.name(server, enemy).getString(), sent.ok() ? "on its way" : sent.error());
        }
    }
}
