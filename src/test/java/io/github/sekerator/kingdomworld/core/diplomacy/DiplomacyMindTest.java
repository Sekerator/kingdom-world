package io.github.sekerator.kingdomworld.core.diplomacy;

import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiplomacyMindTest {
    private static final DiplomacyRules RULES = DiplomacyRules.DEFAULT;
    private final UUID self = new UUID(1, 1);
    private final UUID weak = new UUID(1, 2);
    private final UUID strong = new UUID(1, 3);

    private DiplomacyMind.Self ruler(RulerCharacter character, List<RulerCharacter.Trait> traits, Long lastDeclared) {
        return new DiplomacyMind.Self(self, character, traits, 100.0, false, lastDeclared);
    }

    private DiplomacyMind.Neighbor neighbor(UUID id, boolean npc, double power, int relation, Bond bond, boolean recentWar, Long lastNpcWar) {
        return new DiplomacyMind.Neighbor(id, npc, power, 100.0, relation, bond, true, recentWar, lastNpcWar);
    }

    @Test
    void aWarriorFallsUponAWeakNeighbourButNotTooOften() {
        DiplomacyMind.Self warrior = ruler(RulerCharacter.WARRIOR, List.of(), null);
        List<DiplomacyMind.Neighbor> around = List.of(neighbor(weak, true, 20, 0, Bond.PEACE, false, null),
                neighbor(strong, true, 200, 0, Bond.PEACE, false, null));
        List<DiplomacyMind.Move> moves = DiplomacyMind.decide(warrior, around, false, 50, RULES);
        DiplomacyMind.Move war = moves.stream().filter(m -> m.kind() == DiplomacyMind.Move.Kind.DECLARE).findFirst().orElseThrow();
        assertEquals(weak, war.target());
        assertEquals(WarGoal.TRIBUTE, war.goal(), "five times weaker: vassalage");
        assertEquals(WarGoal.RAID, DiplomacyMind.goal(warrior, neighbor(weak, true, 60, 0, Bond.PEACE, false, null)), "else loot");

        DiplomacyMind.Self recent = ruler(RulerCharacter.WARRIOR, List.of(), 50L - RULES.npcWarDays() + 1);
        assertTrue(DiplomacyMind.victim(recent, around, false, 50, RULES).isEmpty(), "not again so soon");
        assertTrue(DiplomacyMind.victim(warrior, around, true, 50, RULES).isEmpty(), "one war at a time");
        assertTrue(DiplomacyMind.victim(warrior, List.of(neighbor(weak, true, 20, 0, Bond.PEACE, true, null)), false, 50, RULES).isEmpty(),
                "not upon a kingdom just out of a war");
        assertTrue(DiplomacyMind.victim(warrior, List.of(neighbor(weak, true, 20, 0, Bond.PACT, false, null)), false, 50, RULES).isEmpty(),
                "a pact holds");
        assertTrue(DiplomacyMind.victim(warrior, List.of(neighbor(weak, true, 20, 60, Bond.PEACE, false, null)), false, 50, RULES).isEmpty(),
                "not upon a friend");
    }

    @Test
    void warsOnPlayersHaveTheirOwnLimitsAndSwitch() {
        DiplomacyMind.Self warrior = ruler(RulerCharacter.WARRIOR, List.of(), null);
        DiplomacyMind.Neighbor players = neighbor(weak, false, 20, 0, Bond.PEACE, false, 45L);
        assertTrue(DiplomacyMind.victim(warrior, List.of(players), false, 50, RULES).isEmpty(), "a war on these players was declared lately");
        DiplomacyMind.Neighbor rested = neighbor(weak, false, 20, 0, Bond.PEACE, false, 50L - RULES.npcPlayerWarDays());
        assertTrue(DiplomacyMind.victim(warrior, List.of(rested), false, 50, RULES).isPresent());
        assertTrue(DiplomacyMind.victim(warrior, List.of(rested), false, 50, RULES.withNpcWars(false, RULES.npcWarDays(), RULES.npcPlayerWarDays()))
                .isEmpty(), "switched off for players");
        DiplomacyMind.Neighbor away = new DiplomacyMind.Neighbor(weak, false, 20, 100, 0, Bond.PEACE, false, false, null);
        assertTrue(DiplomacyMind.victim(warrior, List.of(away), false, 50, RULES).isEmpty(), "nobody online, no war");
    }

    @Test
    void aJustRulerAndAMageNeverStartAWar() {
        List<DiplomacyMind.Neighbor> around = List.of(neighbor(weak, true, 10, -50, Bond.PEACE, false, null));
        assertTrue(DiplomacyMind.victim(ruler(RulerCharacter.WARRIOR, List.of(RulerCharacter.Trait.JUST), null), around, false, 50, RULES).isEmpty());
        assertTrue(DiplomacyMind.decide(ruler(RulerCharacter.MAGE, List.of(), null), around, false, 50, RULES).isEmpty(), "a mage keeps out");
        assertTrue(DiplomacyMind.joins(ruler(RulerCharacter.WARRIOR, List.of(RulerCharacter.Trait.JUST), null), -30), "but a just one stands by allies");
        assertFalse(DiplomacyMind.joins(ruler(RulerCharacter.MAGE, List.of(), null), 90));
        assertFalse(DiplomacyMind.joins(ruler(RulerCharacter.MERCHANT, List.of(), null), 20));
        assertTrue(DiplomacyMind.joins(ruler(RulerCharacter.MERCHANT, List.of(), null), 50));
    }

    @Test
    void aMerchantSeeksPactsThenAlliances() {
        DiplomacyMind.Self merchant = ruler(RulerCharacter.MERCHANT, List.of(), null);
        List<DiplomacyMind.Move> moves = DiplomacyMind.decide(merchant, List.of(neighbor(weak, true, 50, 5, Bond.PEACE, false, null)), false, 3, RULES);
        assertEquals(List.of(new DiplomacyMind.Move(DiplomacyMind.Move.Kind.PROPOSE_PACT, weak, null)), moves);
        moves = DiplomacyMind.decide(merchant, List.of(neighbor(weak, true, 50, 30, Bond.PACT, false, null)), false, 3, RULES);
        assertEquals(DiplomacyMind.Move.Kind.PROPOSE_ALLIANCE, moves.get(0).kind());
    }

    @Test
    void offersAreAnsweredByCharacter() {
        Proposal alliance = new Proposal(1, strong, self, Proposal.Kind.ALLIANCE, null, 0, PeaceTerms.WHITE, 0, 10, Proposal.Status.OPEN);
        DiplomacyMind.Neighbor friend = neighbor(strong, false, 150, 30, Bond.PEACE, false, null);
        assertTrue(DiplomacyMind.answer(alliance, ruler(RulerCharacter.MERCHANT, List.of(), null), friend).accept());
        assertEquals(DiplomacyMind.ASIDE, DiplomacyMind.answer(alliance, ruler(RulerCharacter.MAGE, List.of(), null), friend).reason());
        Proposal bow = new Proposal(2, strong, self, Proposal.Kind.VASSALAGE, self, 0, PeaceTerms.WHITE, 0, 10, Proposal.Status.OPEN);
        assertFalse(DiplomacyMind.answer(bow, ruler(RulerCharacter.BUILDER, List.of(), null), friend).accept(), "not three times stronger");
        DiplomacyMind.Neighbor giant = neighbor(strong, false, 400, 30, Bond.PEACE, false, null);
        assertTrue(DiplomacyMind.answer(bow, ruler(RulerCharacter.BUILDER, List.of(), null), giant).accept());
        assertEquals(DiplomacyMind.PROUD, DiplomacyMind.answer(bow, ruler(RulerCharacter.BUILDER, List.of(RulerCharacter.Trait.PROUD), null), giant)
                .reason());
        Proposal pact = new Proposal(3, weak, self, Proposal.Kind.PACT, null, 0, PeaceTerms.WHITE, 0, 10, Proposal.Status.OPEN);
        assertFalse(DiplomacyMind.answer(pact, ruler(RulerCharacter.WARRIOR, List.of(), null), neighbor(weak, false, 10, -5, Bond.PEACE, false, null))
                .accept(), "a warrior wants no pact with a weak stranger it dislikes");
    }

    @Test
    void rulersMakePeaceWhenWornOutOrBeatenAndPressWhenAhead() {
        War war = new War(1, self, weak, WarGoal.INDEMNITY, 0, 0);
        PeaceTerms theirs = new PeaceTerms(weak, 100, 0, 0, false, false, 0, "", false);
        PeaceTerms mine = new PeaceTerms(self, 100, 0, 0, false, false, 0, "", false);
        assertTrue(DiplomacyMind.peace(war, War.Side.ATTACKER, PeaceTerms.WHITE, PeaceTerms.WHITE, RULES).accept(), "no lead: a white peace is fine");
        assertFalse(DiplomacyMind.peace(war, War.Side.ATTACKER, theirs, theirs, RULES).accept(), "not beaten, no tribute");
        war.exhaust(War.Side.ATTACKER, RULES.npcPeaceExhaustion());
        assertTrue(DiplomacyMind.peace(war, War.Side.ATTACKER, theirs, theirs, RULES).accept(), "worn out: gives in");
        assertEquals(DiplomacyMind.TOO_MUCH, DiplomacyMind.peace(war, War.Side.ATTACKER, theirs.withIndemnity(500), theirs, RULES).reason(),
                "but not to more than the score allows");
        assertTrue(DiplomacyMind.offerPeace(war, War.Side.ATTACKER, mine, theirs, RULES).isPresent());

        War winning = new War(2, self, weak, WarGoal.INDEMNITY, 0, 0);
        winning.gain(War.Side.ATTACKER, RULES.imposeLead());
        assertEquals(mine, DiplomacyMind.offerPeace(winning, War.Side.ATTACKER, mine, PeaceTerms.WHITE, RULES).orElseThrow(), "ahead: press");
        assertEquals(DiplomacyMind.WINNING, DiplomacyMind.peace(winning, War.Side.ATTACKER, PeaceTerms.WHITE, PeaceTerms.WHITE, RULES).reason());
    }
}
