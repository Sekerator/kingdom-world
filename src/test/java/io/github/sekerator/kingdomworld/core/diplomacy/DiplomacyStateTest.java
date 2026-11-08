package io.github.sekerator.kingdomworld.core.diplomacy;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiplomacyStateTest {
    private static final long DAY = CitizenNeeds.TICKS_PER_DAY;
    private static final DiplomacyRules RULES = DiplomacyRules.DEFAULT;

    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);
    private final UUID c = new UUID(0, 3);
    private final UUID d = new UUID(0, 4);

    private static long accept(DiplomacyState state, Proposal proposal, long now) {
        Outcome outcome = state.accept(proposal.id(), proposal.to(), now, now / DAY, RULES);
        assertTrue(outcome.ok(), () -> "accepted: " + outcome);
        return proposal.id();
    }

    private Proposal propose(DiplomacyState state, UUID from, UUID to, Proposal.Kind kind, UUID vassal, long now) {
        DiplomacyState.Proposed proposed = state.propose(from, to, kind, vassal, 0, PeaceTerms.WHITE, now, 0, RULES);
        assertTrue(proposed.outcome().ok(), () -> "proposed: " + proposed.outcome());
        return proposed.proposal().orElseThrow();
    }

    private War declare(DiplomacyState state, UUID attacker, UUID defender, WarGoal goal, long now) {
        DiplomacyState.Declared declared = state.declare(attacker, defender, goal, now, now / DAY, RULES, false, true);
        assertTrue(declared.outcome().ok(), () -> "declared: " + declared.outcome());
        return declared.war().orElseThrow();
    }

    @Test
    void peaceIsTheDefaultAndTreatiesFollowTheirOffers() {
        DiplomacyState state = new DiplomacyState();
        assertEquals(Bond.PEACE, state.bond(a, b, 0));
        accept(state, propose(state, a, b, Proposal.Kind.PACT, null, 0), 10);
        assertEquals(Bond.PACT, state.bond(a, b, 20));
        assertEquals(Bond.PACT, state.bond(b, a, 20), "a pair is one pair in either order");
        assertEquals(Bond.PEACE, state.bond(a, b, 10 + RULES.pactDays() * DAY), "a pact runs out");
        accept(state, propose(state, b, a, Proposal.Kind.ALLIANCE, null, 30), 40);
        assertEquals(Bond.ALLIANCE, state.bond(a, b, 50 + 100 * DAY), "an alliance holds until broken");
        assertTrue(state.relation(a, b) > 0, "treaties warm relations");

        accept(state, propose(state, c, d, Proposal.Kind.VASSALAGE, c, 0), 5);
        assertEquals(Bond.VASSAL, state.bond(c, d, 10));
        assertEquals(Optional.of(d), state.overlordOf(c));
        assertEquals(List.of(c), state.vassalsOf(d));
        accept(state, propose(state, c, d, Proposal.Kind.RELEASE, null, 20), 30);
        assertEquals(Bond.PEACE, state.bond(c, d, 40), "a vassal let go in peace is free");
    }

    @Test
    void offersAreAnsweredOnceAndExpire() {
        DiplomacyState state = new DiplomacyState();
        Proposal pact = propose(state, a, b, Proposal.Kind.PACT, null, 0);
        assertEquals(KingdomError.PROPOSAL_EXISTS, state.propose(b, a, Proposal.Kind.PACT, null, 0, PeaceTerms.WHITE, 1, 0, RULES).outcome().error());
        assertEquals(KingdomError.NOT_YOUR_PROPOSAL, state.accept(pact.id(), a, 1, 0, RULES).error(), "the proposer does not answer itself");
        assertTrue(state.decline(pact.id(), b, 2).ok());
        assertEquals(KingdomError.PROPOSAL_CLOSED, state.accept(pact.id(), b, 3, 0, RULES).error());
        assertTrue(state.relation(a, b) < 0, "a declined offer cools relations");

        Proposal alliance = propose(state, a, c, Proposal.Kind.ALLIANCE, null, 0);
        assertTrue(state.expire(RULES.proposalTicks() - 1).isEmpty());
        assertEquals(List.of(alliance.id()), state.expire(RULES.proposalTicks()).stream().map(Proposal::id).toList());
        assertEquals(Proposal.Status.EXPIRED, state.proposal(alliance.id()).orElseThrow().status());
        state.expire(RULES.proposalTicks() + DiplomacyState.CLOSED_PROPOSAL_TICKS + 1);
        assertTrue(state.proposal(alliance.id()).isEmpty(), "old closed offers are forgotten");
    }

    @Test
    void vassalageNeedsFreeKingdoms() {
        DiplomacyState state = new DiplomacyState();
        accept(state, propose(state, a, b, Proposal.Kind.VASSALAGE, a, 0), 1);
        assertEquals(KingdomError.TREATY_IMPOSSIBLE, state.propose(a, c, Proposal.Kind.VASSALAGE, a, 0, PeaceTerms.WHITE, 2, 0, RULES)
                .outcome().error(), "a vassal serves one overlord");
        assertEquals(KingdomError.TREATY_IMPOSSIBLE, state.propose(c, a, Proposal.Kind.VASSALAGE, c, 0, PeaceTerms.WHITE, 2, 0, RULES)
                .outcome().error(), "a vassal has no vassals");
        assertEquals(KingdomError.NOT_A_VASSAL, state.propose(c, b, Proposal.Kind.RELEASE, null, 0, PeaceTerms.WHITE, 2, 0, RULES)
                .outcome().error());
        assertEquals(KingdomError.OWN_VASSAL, state.breakTreaty(a, b, 3).outcome().error(), "a vassal cannot walk away");
        assertTrue(state.breakTreaty(b, a, 3).outcome().ok(), "an overlord may let a vassal go");
        assertEquals(Bond.PEACE, state.bond(a, b, 4));
    }

    @Test
    void declarationsAreRefusedInTruceInPactToVassalsAndToTheAbsent() {
        DiplomacyState state = new DiplomacyState();
        assertEquals(KingdomError.TARGET_IS_SELF, state.checkDeclare(a, a, WarGoal.RAID, 0, false, true).error());
        assertEquals(KingdomError.NOBODY_ONLINE, state.checkDeclare(a, b, WarGoal.RAID, 0, false, false).error(),
                "a kingdom of players with nobody online cannot be attacked");
        accept(state, propose(state, a, b, Proposal.Kind.PACT, null, 0), 1);
        assertEquals(KingdomError.IN_PACT, state.checkDeclare(a, b, WarGoal.RAID, 2, false, true).error());
        accept(state, propose(state, c, d, Proposal.Kind.VASSALAGE, c, 0), 1);
        assertEquals(KingdomError.OWN_VASSAL, state.checkDeclare(d, c, WarGoal.TRIBUTE, 2, false, true).error(), "no war on one's own vassal");
        assertEquals(KingdomError.VASSAL_NO_WAR, state.checkDeclare(c, a, WarGoal.RAID, 2, false, true).error(), "a vassal declares no wars");
        assertTrue(state.checkDeclare(c, d, WarGoal.INDEPENDENCE, 2, false, true).ok(), "but it may fight for its freedom");
        assertEquals(KingdomError.NOT_A_VASSAL, state.checkDeclare(a, d, WarGoal.INDEPENDENCE, 2, false, true).error());

        War war = declare(state, a, c == null ? b : d, WarGoal.RAID, 3);
        assertEquals(KingdomError.AT_WAR, state.checkDeclare(d, a, WarGoal.RAID, 4, false, true).error());
        assertTrue(state.endWar(war.id(), War.Result.PEACE, PeaceTerms.WHITE, 10, 0, RULES).ok());
        assertEquals(Bond.TRUCE, state.bond(a, d, 11));
        assertEquals(KingdomError.IN_TRUCE, state.checkDeclare(a, d, WarGoal.RAID, 12, true, true).error(), "a truce cannot be broken");
        assertEquals(KingdomError.IN_TRUCE, state.breakTreaty(a, d, 12).outcome().error());
        assertTrue(state.checkDeclare(a, d, WarGoal.RAID, 10 + RULES.truceDays() * DAY, false, true).ok(), "the truce runs out");
    }

    @Test
    void breakingAPactForWarCostsScoreAndTheWordOfEverybodyWhoKnows() {
        DiplomacyState state = new DiplomacyState();
        accept(state, propose(state, a, b, Proposal.Kind.PACT, null, 0), 1);
        accept(state, propose(state, b, c, Proposal.Kind.PACT, null, 0), 1);
        int before = state.relation(a, c);
        DiplomacyState.Declared declared = state.declare(a, b, WarGoal.RAID, 5, 0, RULES, true, true);
        assertTrue(declared.outcome().ok());
        assertTrue(declared.broken().isPresent());
        War war = declared.war().orElseThrow();
        assertTrue(war.brokePact());
        assertEquals(RULES.pactPenalty(), war.score(War.Side.DEFENDER), 1e-9, "the betrayed starts ahead");
        assertTrue(state.relation(a, c) < before, "the neighbour of the victim heard of it");
        assertTrue(state.relation(a, b) <= Relations.Reason.BROKEN_TREATY.weight() + Relations.Reason.WAR.weight() + Relations.Reason.TREATY.weight());
    }

    @Test
    void aWarIsPreparedCallsAlliesAndTakesVassalsAlong() {
        DiplomacyState state = new DiplomacyState();
        accept(state, propose(state, b, c, Proposal.Kind.ALLIANCE, null, 0), 1);
        accept(state, propose(state, d, b, Proposal.Kind.VASSALAGE, d, 0), 1);
        War war = declare(state, a, b, WarGoal.TRIBUTE, 100);
        assertEquals(War.Phase.PREPARING, war.phase());
        assertEquals(Bond.WAR, state.bond(a, b, 101), "declared is at war already for the atlas");
        assertFalse(state.fighting(a, b), "but nobody fights before the start");
        assertEquals(Optional.of(War.Side.DEFENDER), war.sideOf(d), "a vassal goes with its overlord");
        assertEquals(War.Side.DEFENDER, war.calls().get(c), "the ally is called");
        assertEquals(KingdomError.NOT_CALLED, state.answerCall(war.id(), a, true).error());
        assertTrue(state.answerCall(war.id(), c, false).ok());
        assertTrue(state.relation(c, b) < Relations.Reason.ALLIANCE.weight(), "refusing a call cools the alliance");
        assertTrue(war.sideOf(c).isEmpty());

        assertTrue(state.startDue(100 + RULES.preparationTicks() - 1).isEmpty());
        assertEquals(List.of(war), state.startDue(100 + RULES.preparationTicks()));
        assertTrue(state.fighting(a, b));
        assertTrue(state.fighting(a, d));
        assertEquals(java.util.Set.of(b, d), state.enemiesOf(a));
    }

    @Test
    void scoreAndExhaustionStayInTheirRangeAndCountOnlyWhileFighting() {
        DiplomacyState state = new DiplomacyState();
        War war = declare(state, a, b, WarGoal.RAID, 0);
        assertEquals(0.0, state.score(a, b, DiplomacyState.Feat.BATTLE, 10), 1e-9, "no score before the start");
        state.startDue(RULES.preparationTicks());
        assertEquals(10.0, state.score(a, b, DiplomacyState.Feat.BATTLE, 10), 1e-9);
        for (int i = 0; i < 20; i++) {
            state.score(a, b, DiplomacyState.Feat.KILL, 10);
        }
        assertEquals(War.MAX, war.score(War.Side.ATTACKER), 1e-9, "the score stops at 100");
        assertEquals(1, war.tally(War.Side.ATTACKER).battles());
        assertEquals(20, war.tally(War.Side.ATTACKER).kills());
        assertEquals(0.0, state.score(a, c, DiplomacyState.Feat.KILL, 5), 1e-9, "no score against a kingdom at peace");
        state.exhaust(b, a, 30, 3);
        assertEquals(30.0, war.exhaustion(War.Side.DEFENDER), 1e-9);
        assertEquals(3, war.tally(War.Side.DEFENDER).losses());
    }

    @Test
    void daysOfWarTireTheSidesExceptTheShieldedAndTheWornOutGiveIn() {
        DiplomacyState state = new DiplomacyState();
        War war = declare(state, a, b, WarGoal.INDEMNITY, 0);
        long start = RULES.preparationTicks();
        state.startDue(start);
        long day = start / DAY;
        List<DiplomacyState.Ending> endings = state.daily(start, day + 1, RULES, id -> id.equals(b), id -> id.equals(a), id -> 1_000);
        assertTrue(endings.isEmpty());
        assertEquals(RULES.exhaustionDay() + RULES.exhaustionPoor(), war.exhaustion(War.Side.ATTACKER), 1e-9, "a poor side tires faster");
        assertEquals(0.0, war.exhaustion(War.Side.DEFENDER), 1e-9, "a shielded kingdom does not tire");
        state.daily(start, day + 1, RULES, id -> false, id -> false, id -> 1_000);
        assertEquals(RULES.exhaustionDay() + RULES.exhaustionPoor(), war.exhaustion(War.Side.ATTACKER), 1e-9, "once a day");

        state.exhaust(b, a, War.MAX, 0);
        endings = state.daily(start + 10, day + 2, RULES, id -> false, id -> false, id -> 1_000);
        assertEquals(1, endings.size());
        DiplomacyState.Ending ending = endings.get(0);
        assertEquals(War.Result.SURRENDER, ending.how());
        assertEquals(a, ending.terms().winner(), "the worn out side gives in on the terms of the enemy");
        assertEquals(1_000L * RULES.indemnityShare() / 1_000, ending.terms().indemnity());
    }

    @Test
    void aWarPastItsLongestTimeEndsInAWhitePeace() {
        DiplomacyState state = new DiplomacyState();
        War war = declare(state, a, b, WarGoal.RAID, 0);
        long start = RULES.preparationTicks();
        state.startDue(start);
        state.score(a, b, DiplomacyState.Feat.BATTLE, 30);
        long end = start + RULES.maxWarDays() * DAY;
        assertTrue(state.ending(war, end - 1, RULES, id -> 0).isEmpty());
        DiplomacyState.Ending ending = state.ending(war, end, RULES, id -> 0).orElseThrow();
        assertEquals(War.Result.TIME, ending.how());
        assertTrue(ending.terms().isWhite());
        assertTrue(state.endWar(war.id(), ending.how(), ending.terms(), end, end / DAY, RULES).ok());
        assertTrue(war.over());
        assertEquals(Bond.TRUCE, state.bond(a, b, end + 1));
        assertEquals(Optional.of(end), state.lastWarEnded(b));
    }

    @Test
    void peaceTermsDependOnTheLeadAndApplyTogether() {
        DiplomacyState state = new DiplomacyState();
        War war = declare(state, a, b, WarGoal.TRIBUTE, 0);
        state.startDue(RULES.preparationTicks());
        long now = RULES.preparationTicks() + 1;
        PeaceTerms full = PeaceTerms.forGoal(WarGoal.TRIBUTE, a, true, 1_000, RULES);
        assertTrue(full.vassalage());
        assertEquals(KingdomError.TERMS_TOO_HIGH, state.propose(a, b, Proposal.Kind.PEACE, null, war.id(), full, now, 1_000, RULES).outcome().error(),
                "no lead, no terms");
        assertTrue(DiplomacyState.terms(war, War.Side.ATTACKER, false, 1_000, RULES).isWhite());
        state.score(a, b, DiplomacyState.Feat.BATTLE, 20);
        PeaceTerms half = DiplomacyState.terms(war, War.Side.ATTACKER, false, 1_000, RULES);
        assertFalse(half.vassalage(), "a small lead asks a tribute instead of vassalage");
        assertEquals(RULES.tributeShare() / 2, half.tributeShare());
        state.score(a, b, DiplomacyState.Feat.BATTLE, RULES.imposeLead());
        Proposal peace = state.propose(a, b, Proposal.Kind.PEACE, null, war.id(), full, now, 1_000, RULES).proposal().orElseThrow();
        assertTrue(state.propose(b, a, Proposal.Kind.PEACE, null, war.id(), PeaceTerms.WHITE, now, 0, RULES).outcome().ok(),
                "the other side may still offer a white peace");
        assertTrue(state.accept(peace.id(), b, now + 1, now / DAY, RULES).ok());
        assertTrue(war.over());
        assertEquals(Bond.VASSAL, state.bond(a, b, now + 2));
        assertEquals(Optional.of(a), state.overlordOf(b));
        assertTrue(state.openProposals(a).isEmpty(), "other offers of peace for the war are closed");
        List<DiplomacyState.Tribute> due = state.dueTributes(now / DAY + 1);
        assertEquals(1, due.size());
        assertEquals(b, due.get(0).payer());
        assertEquals(RULES.vassalShare(), due.get(0).share());
        assertTrue(state.dueTributes(now / DAY + 1).isEmpty(), "a day pays once");
    }

    @Test
    void anIndemnityIsASettlementAndATributeRunsForItsDays() {
        DiplomacyState state = new DiplomacyState();
        War war = declare(state, a, b, WarGoal.INDEMNITY, 0);
        state.startDue(RULES.preparationTicks());
        PeaceTerms terms = new PeaceTerms(a, 300, 100, 2, false, false, 0, "", false);
        assertEquals(Optional.of(new DiplomacyState.Settlement(b, a, 300)), DiplomacyState.settlement(war, terms));
        assertTrue(DiplomacyState.settlement(war, PeaceTerms.WHITE).isEmpty());
        assertTrue(state.endWar(war.id(), War.Result.PEACE, terms, 100, 10, RULES).ok());
        assertEquals(1, state.dueTributes(11).size());
        assertEquals(1, state.dueTributes(12).size());
        assertTrue(state.dueTributes(13).isEmpty(), "the tribute ran out");
    }

    @Test
    void claimsOfLandCitiesAndThronesWaitForTheWorld() {
        DiplomacyState state = new DiplomacyState();
        War war = declare(state, a, b, WarGoal.LAND, 0);
        state.startDue(RULES.preparationTicks());
        PeaceTerms terms = PeaceTerms.forGoal(WarGoal.LAND, a, true, 0, RULES);
        assertTrue(terms.claims());
        assertTrue(state.endWar(war.id(), War.Result.SURRENDER, terms, 50, 1, RULES).ok());
        assertEquals(1, state.claims().size());
        assertEquals(RULES.landChunks(), state.claims().get(0).chunks());
        assertTrue(state.takeClaim(0).isPresent());
        assertTrue(state.claims().isEmpty());
    }

    @Test
    void independenceFreesTheVassalAndVassalageFallsBackToTribute() {
        DiplomacyState state = new DiplomacyState();
        accept(state, propose(state, a, b, Proposal.Kind.VASSALAGE, a, 0), 1);
        War war = declare(state, a, b, WarGoal.INDEPENDENCE, 10);
        state.startDue(10 + RULES.preparationTicks());
        PeaceTerms free = PeaceTerms.forGoal(WarGoal.INDEPENDENCE, a, true, 0, RULES);
        assertTrue(state.endWar(war.id(), War.Result.PEACE, free, 20 + RULES.preparationTicks(), 1, RULES).ok());
        assertTrue(state.overlordOf(a).isEmpty(), "the vassal is free");
        assertEquals(Bond.TRUCE, state.bond(a, b, 30 + RULES.preparationTicks()));

        DiplomacyState other = new DiplomacyState();
        accept(other, propose(other, d, b, Proposal.Kind.VASSALAGE, d, 0), 1);
        War second = declare(other, c, b, WarGoal.TRIBUTE, 10);
        other.startDue(10 + RULES.preparationTicks());
        PeaceTerms vassalage = PeaceTerms.forGoal(WarGoal.TRIBUTE, c, true, 0, RULES);
        assertEquals(KingdomError.TREATY_IMPOSSIBLE, other.checkTerms(second, vassalage).error(), "a kingdom with vassals bows to nobody");
        PeaceTerms feasible = other.feasible(second, vassalage, RULES);
        assertFalse(feasible.vassalage());
        assertEquals(RULES.tributeShare(), feasible.tributeShare());
    }

    @Test
    void theAbsentAreShieldedAfterADelayAndNotRightAfterAFight() {
        Presence presence = new Presence();
        UUID kingdom = UUID.randomUUID();
        assertTrue(presence.shielded(kingdom, 0, false, RULES), "never seen: shielded");
        assertFalse(presence.shielded(kingdom, 0, true, RULES), "players at the keyboard are no shield");
        presence.present(kingdom, 1_000);
        assertFalse(presence.shielded(kingdom, 1_000 + RULES.absenceTicks() - 1, false, RULES), "the shield comes with a delay");
        assertTrue(presence.shielded(kingdom, 1_000 + RULES.absenceTicks(), false, RULES));
        presence.fought(kingdom, 1_000 + RULES.absenceTicks());
        assertFalse(presence.shielded(kingdom, 1_000 + RULES.absenceTicks() + RULES.fightGraceTicks() - 1, false, RULES),
                "no shield right after a fight");
        assertTrue(presence.shielded(kingdom, 1_000 + RULES.absenceTicks() + RULES.fightGraceTicks(), false, RULES));
        assertEquals(1_000 + RULES.absenceTicks() + RULES.fightGraceTicks(), presence.shieldFrom(kingdom, 0, RULES));
    }

    @Test
    void aKingdomThatIsGoneLeavesNoWarsOrTreatiesBehind() {
        DiplomacyState state = new DiplomacyState();
        accept(state, propose(state, a, c, Proposal.Kind.ALLIANCE, null, 0), 1);
        War war = declare(state, a, b, WarGoal.RAID, 2);
        state.forget(a, 10);
        assertTrue(war.over());
        assertEquals(War.Result.GONE, war.result());
        assertTrue(state.treaties().isEmpty());
        assertEquals(0, state.relation(a, b));
    }
}
