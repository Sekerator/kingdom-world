package io.github.sekerator.kingdomworld.core.diplomacy;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.npc.ProvinceSale;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A province ceded by treaty outside war: the offer, its checks, what accepting it hands over, how a ruler of the world
 * answers it at the prices of sales, which provinces a kingdom may ask for; and the goal of a warrior against a bordering
 * state.
 */
class CessionTest {
    private static final long DAY = CitizenNeeds.TICKS_PER_DAY;
    private static final DiplomacyRules RULES = DiplomacyRules.DEFAULT;

    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);
    private final UUID c = new UUID(0, 3);

    /** Province 7 of b, touching the land of the asker. */
    private CedeLand ofB() {
        return new CedeLand(b, false, true);
    }

    @Test
    void aCessionNamesItsProvinceAndOtherOffersDoNot() {
        Proposal cede = new Proposal(1, a, b, Proposal.Kind.CEDE, null, 0, PeaceTerms.WHITE, 7, 500, 0, 10, Proposal.Status.OPEN);
        assertEquals(7, cede.province());
        assertEquals(500, cede.price());
        assertEquals(7, cede.withStatus(Proposal.Status.ACCEPTED).withExpires(5).province(), "the province survives a change of status");
        Proposal pact = new Proposal(2, a, b, Proposal.Kind.PACT, null, 0, PeaceTerms.WHITE, 7, 500, 0, 10, Proposal.Status.OPEN);
        assertEquals(0, pact.province());
        assertEquals(0, pact.price());
        assertEquals(0, new Proposal(3, a, b, Proposal.Kind.ALLIANCE, null, 0, PeaceTerms.WHITE, 0, 10, Proposal.Status.OPEN).province());
        assertThrows(IllegalArgumentException.class, () -> new Proposal(4, a, b, Proposal.Kind.CEDE, null, 0, PeaceTerms.WHITE, 0, 0, 0, 10,
                Proposal.Status.OPEN), "a cession of no province");
        assertThrows(IllegalArgumentException.class, () -> new Proposal(5, a, b, Proposal.Kind.CEDE, null, 0, PeaceTerms.WHITE, 7, -1, 0, 10,
                Proposal.Status.OPEN), "a negative price");
    }

    @Test
    void onlyABorderingProvinceOfTheOtherIsAskedForAndOnlyInPeace() {
        DiplomacyState state = new DiplomacyState();
        assertEquals(KingdomError.VALUE_OUT_OF_RANGE, state.checkCede(a, b, 7, 0, null, 0, 0).error(), "no such province");
        assertEquals(KingdomError.VALUE_OUT_OF_RANGE, state.checkCede(a, b, 0, 0, ofB(), 0, 0).error());
        assertEquals(KingdomError.VALUE_OUT_OF_RANGE, state.checkCede(a, b, 7, -5, ofB(), 0, 0).error());
        assertEquals(KingdomError.NOT_OWNED, state.checkCede(a, b, 7, 0, new CedeLand(c, false, true), 0, 0).error());
        assertEquals(KingdomError.NOT_OWNED, state.checkCede(a, b, 7, 0, new CedeLand(null, false, true), 0, 0).error(), "nobody's land");
        assertEquals(KingdomError.CAPITAL_CHUNK, state.checkCede(a, b, 7, 0, new CedeLand(b, true, true), 0, 0).error(), "never the heart");
        assertEquals(KingdomError.NOT_ADJACENT, state.checkCede(a, b, 7, 0, new CedeLand(b, false, false), 0, 0).error());
        assertEquals(KingdomError.NOT_ENOUGH_MONEY, state.checkCede(a, b, 7, 800, ofB(), 0, 799).error(), "the asker pays what it offers");
        assertEquals(KingdomError.TARGET_IS_SELF, state.checkCede(a, a, 7, 0, new CedeLand(a, false, true), 0, 0).error());
        assertTrue(state.checkCede(a, b, 7, 800, ofB(), 0, 800).ok());
        assertEquals(KingdomError.VALUE_OUT_OF_RANGE, state.propose(a, b, Proposal.Kind.CEDE, null, 0, PeaceTerms.WHITE, 0, 1_000, RULES)
                .outcome().error(), "a cession goes with its land");

        Proposal asked = state.proposeCede(a, b, 7, 800, ofB(), 0, 1_000, RULES).proposal().orElseThrow();
        assertEquals(Proposal.Kind.CEDE, asked.kind());
        assertEquals(7, asked.province());
        assertEquals(800, asked.price());
        assertEquals(KingdomError.PROPOSAL_EXISTS, state.checkCede(a, b, 8, 0, ofB(), 0, 0).error(), "one cession at a time between two");
        assertEquals(KingdomError.PROPOSAL_EXISTS, state.checkCede(b, a, 9, 0, new CedeLand(a, false, true), 0, 0).error(), "either way");
        assertTrue(state.checkCede(c, b, 7, 0, ofB(), 0, 0).ok(), "another kingdom may ask for it too");

        War war = state.declare(b, a, WarGoal.RAID, 10, 0, RULES, false, true).war().orElseThrow();
        assertEquals(Proposal.Status.CANCELLED, state.proposal(asked.id()).orElseThrow().status(), "a war calls the cession off");
        assertEquals(KingdomError.AT_WAR, state.checkCede(a, b, 7, 0, ofB(), 20, 0).error());
        assertTrue(state.endWar(war.id(), War.Result.TIME, PeaceTerms.WHITE, 30, 0, RULES).ok());
        assertEquals(KingdomError.IN_TRUCE, state.checkCede(a, b, 7, 0, ofB(), 40, 0).error());
        assertTrue(state.checkCede(a, b, 7, 0, ofB(), 40 + (long) RULES.truceDays() * DAY, 0).ok(), "after the truce");
    }

    @Test
    void acceptingACessionTellsWhatTheWorldHandsOver() {
        DiplomacyState state = new DiplomacyState();
        Proposal asked = state.proposeCede(a, b, 7, 800, ofB(), 0, 1_000, RULES).proposal().orElseThrow();
        Proposal rival = state.proposeCede(c, b, 7, 900, ofB(), 0, 1_000, RULES).proposal().orElseThrow();
        assertEquals(KingdomError.VALUE_OUT_OF_RANGE, state.accept(asked.id(), b, 5, 0, RULES).error(), "a cession is accepted with its land");
        assertTrue(state.proposal(asked.id()).orElseThrow().open());
        assertEquals(KingdomError.NOT_OWNED, state.checkAccept(asked.id(), b, 5, 1_000, RULES, new CedeLand(c, false, true)).error(),
                "the province changed hands meanwhile");
        assertEquals(KingdomError.NOT_ENOUGH_MONEY, state.checkAccept(asked.id(), b, 5, 700, RULES, ofB()).error(), "the asker cannot pay now");
        assertEquals(KingdomError.NOT_YOUR_PROPOSAL, state.acceptOffer(asked.id(), a, 5, 0, RULES, ofB()).outcome().error());

        int before = state.relation(a, b);
        DiplomacyState.Accepted accepted = state.acceptOffer(asked.id(), b, 5, 0, RULES, ofB());
        assertTrue(accepted.outcome().ok());
        DiplomacyState.Cession cession = accepted.cession().orElseThrow();
        assertEquals(a, cession.winner(), "the asker gets the province");
        assertEquals(b, cession.loser());
        assertEquals(7, cession.province());
        assertEquals(800, cession.price());
        assertEquals(asked.id(), cession.proposal());
        assertEquals(before + 3, state.relation(a, b), "a cession makes the two a little closer");
        assertEquals(Proposal.Status.ACCEPTED, state.proposal(asked.id()).orElseThrow().status());
        assertEquals(Proposal.Status.CANCELLED, state.proposal(rival.id()).orElseThrow().status(), "a province is ceded once");

        Proposal pact = state.propose(a, c, Proposal.Kind.PACT, null, 0, PeaceTerms.WHITE, 10, 0, RULES).proposal().orElseThrow();
        DiplomacyState.Accepted treaty = state.acceptOffer(pact.id(), c, 11, 0, RULES, null);
        assertTrue(treaty.outcome().ok());
        assertTrue(treaty.cession().isEmpty(), "other offers hand nothing over");
    }

    private DiplomacyMind.Self ruler(RulerCharacter character, RulerCharacter.Trait... traits) {
        return new DiplomacyMind.Self(b, character, List.of(traits), 100.0, false, null);
    }

    private DiplomacyMind.Neighbor asker(int relation) {
        return new DiplomacyMind.Neighbor(a, true, 100.0, 300.0, relation, Bond.PEACE, true, false, null, true);
    }

    private static DiplomacyMind.CedeAsk free() {
        return new DiplomacyMind.CedeAsk(false, false, 1_000, 0, false);
    }

    @Test
    void aRulerSellsAProvinceByItsCharacterAndRelations() {
        DiplomacyMind.CedeAsk ask = new DiplomacyMind.CedeAsk(false, false, 1_000, 0, false);
        assertEquals(2_700, DiplomacyMind.cedeValue(ruler(RulerCharacter.BUILDER), asker(50), ask), "3 a chunk, less between friends");
        assertEquals(2_700, DiplomacyMind.cedeValue(ruler(RulerCharacter.MAGE), asker(90), ask));
        assertEquals(3_600, DiplomacyMind.cedeValue(ruler(RulerCharacter.BUILDER), asker(0), ask), "more from one who dislikes the asker");
        assertEquals(3_600, DiplomacyMind.cedeValue(ruler(RulerCharacter.BUILDER), asker(-15), ask));
        assertEquals(3_150, DiplomacyMind.cedeValue(ruler(RulerCharacter.BUILDER), asker(25), ask));
        assertEquals(2_295, DiplomacyMind.cedeValue(ruler(RulerCharacter.MERCHANT), asker(50), ask), "a merchant sells cheaper");
        assertEquals(3_780, DiplomacyMind.cedeValue(ruler(RulerCharacter.WARRIOR), asker(50), ask), "a warrior dearer");
        assertEquals(2_430, DiplomacyMind.cedeValue(ruler(RulerCharacter.BUILDER, RulerCharacter.Trait.GENEROUS), asker(50), ask));
        assertEquals(3_375, DiplomacyMind.cedeValue(ruler(RulerCharacter.BUILDER, RulerCharacter.Trait.PROUD), asker(50), ask));
        DiplomacyMind.CedeAsk small = new DiplomacyMind.CedeAsk(false, false, 10, 0, false);
        assertEquals(DiplomacyMind.MIN_CEDE_VALUE, DiplomacyMind.cedeValue(ruler(RulerCharacter.MERCHANT), asker(50), small), "a small one too");
        DiplomacyMind.CedeAsk town = new DiplomacyMind.CedeAsk(true, false, 1_000, 0, false);
        assertEquals(5_400, DiplomacyMind.cedeValue(ruler(RulerCharacter.BUILDER), asker(50), town), "twice where people live");

        DiplomacyMind.Self builder = ruler(RulerCharacter.BUILDER);
        assertEquals(new DiplomacyMind.Verdict(true, DiplomacyMind.AGREED),
                DiplomacyMind.cede(builder, asker(50), new DiplomacyMind.CedeAsk(false, false, 1_000, 2_700, false)));
        assertEquals(new DiplomacyMind.Verdict(false, DiplomacyMind.LOW_PRICE),
                DiplomacyMind.cede(builder, asker(50), new DiplomacyMind.CedeAsk(false, false, 1_000, 2_699, false)));
        assertEquals(new DiplomacyMind.Verdict(false, DiplomacyMind.HEART),
                DiplomacyMind.cede(builder, asker(100), new DiplomacyMind.CedeAsk(false, true, 1_000, 1_000_000, true)), "never the heart");
        assertEquals(new DiplomacyMind.Verdict(false, DiplomacyMind.DISTRUST),
                DiplomacyMind.cede(builder, asker(-21), new DiplomacyMind.CedeAsk(false, false, 1_000, 1_000_000, false)), "not to a foe");
        assertTrue(DiplomacyMind.cede(builder, asker(-20), new DiplomacyMind.CedeAsk(false, false, 1_000, 1_000_000, false)).accept());
        DiplomacyMind.Self warrior = ruler(RulerCharacter.WARRIOR, RulerCharacter.Trait.GENEROUS);
        assertEquals(new DiplomacyMind.Verdict(false, DiplomacyMind.PROUD),
                DiplomacyMind.cede(warrior, asker(100), new DiplomacyMind.CedeAsk(false, false, 1_000, 0, true)), "a warrior gives nothing away");
        assertEquals(3_240, DiplomacyMind.cedeValue(warrior, asker(0), free()), "a generous trait weighs over the leaning, as in a sale");
        assertEquals(5_040, DiplomacyMind.cedeValue(ruler(RulerCharacter.WARRIOR), asker(0), free()));
        assertTrue(DiplomacyMind.cede(warrior, asker(0), new DiplomacyMind.CedeAsk(false, false, 1_000, 3_240, false)).accept(),
                "a warrior sells for its price");
        assertFalse(DiplomacyMind.cede(warrior, asker(0), new DiplomacyMind.CedeAsk(false, false, 1_000, 3_239, false)).accept());

        DiplomacyMind.CedeAsk free = free();
        DiplomacyMind.Self generous = ruler(RulerCharacter.MERCHANT, RulerCharacter.Trait.GENEROUS);
        assertTrue(DiplomacyMind.cede(generous, asker(80), free).accept(), "a generous ruler gives empty land to a dear friend");
        assertFalse(DiplomacyMind.cede(generous, asker(79), free).accept());
        assertFalse(DiplomacyMind.cede(ruler(RulerCharacter.MERCHANT), asker(100), free).accept(), "only when generous");
        assertFalse(DiplomacyMind.cede(generous, asker(100), new DiplomacyMind.CedeAsk(true, false, 1_000, 0, false)).accept(),
                "not where people live");
        assertTrue(DiplomacyMind.cede(builder, asker(0), new DiplomacyMind.CedeAsk(false, false, 1_000, 0, true)).accept(),
                "a vassal gives empty land to its overlord");
        assertFalse(DiplomacyMind.cede(builder, asker(0), new DiplomacyMind.CedeAsk(true, false, 1_000, 0, true)).accept());
        assertTrue(DiplomacyMind.REASONS.containsAll(List.of(DiplomacyMind.HEART, DiplomacyMind.LOW_PRICE)));

        Proposal cede = new Proposal(1, a, b, Proposal.Kind.CEDE, null, 0, PeaceTerms.WHITE, 7, 1_000_000, 0, 10, Proposal.Status.OPEN);
        assertFalse(DiplomacyMind.answer(cede, builder, asker(100)).accept(), "an offer without its land is not taken");
    }

    @Test
    void aRulerAsksForAProvinceWhatItAsksForOneOnSale() {
        ProvinceSale.Rules dear = new ProvinceSale.Rules(5.0, 2_000, 10, 0.75, 0.75, 0.25, -20, 3);
        DiplomacyMind.Self proud = ruler(RulerCharacter.MERCHANT, RulerCharacter.Trait.PROUD);
        DiplomacyMind.CedeAsk empty = new DiplomacyMind.CedeAsk(false, false, 700, 0, false);
        long sale = ProvinceSale.price(700, RulerCharacter.MERCHANT, List.of(RulerCharacter.Trait.PROUD), 30, dear);
        assertEquals(sale, DiplomacyMind.cedeValue(proud, asker(30), empty, dear), "the price of a sale, with the rules of the server");
        assertEquals(sale * 2, DiplomacyMind.cedeValue(proud, asker(30), new DiplomacyMind.CedeAsk(true, false, 700, 0, false), dear),
                "twice where people live");
        assertEquals(2_000, DiplomacyMind.cedeValue(proud, asker(30), new DiplomacyMind.CedeAsk(false, false, 3, 0, false), dear), "the least price");
        assertTrue(DiplomacyMind.cede(proud, asker(30), new DiplomacyMind.CedeAsk(false, false, 700, sale, false), dear).accept());
        assertFalse(DiplomacyMind.cede(proud, asker(30), new DiplomacyMind.CedeAsk(false, false, 700, sale - 1, false), dear).accept());
    }

    @Test
    void theProvincesAskedForAreThoseOfOthersThatTouchTheLandOfTheAsker() {
        // a holds 1; 2, 3 (the heart of b), 6 (promised by a charter) and 9 of b and 7 of nobody border 1; 4 of c is beside a claim of a.
        Map<Integer, List<Integer>> near = Map.of(1, List.of(1, 2, 3, 6, 7, 9), 2, List.of(1, 5));
        Map<Integer, UUID> holders = new HashMap<>(Map.of(1, a, 2, b, 3, b, 4, c, 5, b, 6, b, 9, b));
        long claim = ChunkKey.of(10, 0);
        Map<Long, Integer> provinceAt = Map.of(ChunkKey.of(11, 0), 4, ChunkKey.of(10, 1), 1);
        Set<Integer> touching = CedeLand.bordering(List.of(1), id -> near.getOrDefault(id, List.of()), List.of(claim),
                chunk -> provinceAt.getOrDefault(chunk, 0));
        assertEquals(Set.of(2, 3, 4, 6, 7, 9), touching, "not its own, not one far away, not the sea of no province");

        Map<UUID, List<Integer>> asked = CedeLand.candidates(touching, holders::get, a, k -> k.equals(b) ? 3 : 0, id -> id == 6, 8);
        assertEquals(Map.of(b, List.of(2, 9), c, List.of(4)), asked, "never the heart, nor land promised by a charter, nor land of nobody");
        assertEquals(Map.of(b, List.of(2), c, List.of(4)), CedeLand.candidates(touching, holders::get, a, k -> k.equals(b) ? 3 : 0, id -> id == 6, 1),
                "the lowest numbers first, a few of each");
        assertTrue(CedeLand.candidates(Set.of(3, 6), holders::get, a, k -> 3, id -> id == 6, 8).isEmpty(), "none left: nobody to ask");
        assertEquals(Map.of(b, List.of(2)), CedeLand.candidates(Set.of(1, 2), holders::get, a, k -> 0, id -> false, 8), "never its own");
    }

    @Test
    void aWarriorGoesForTheProvincesOfABorderingStateTwiceWeaker() {
        DiplomacyMind.Self warrior = new DiplomacyMind.Self(a, RulerCharacter.WARRIOR, List.of(), 100.0, false, null);
        DiplomacyMind.Neighbor state = new DiplomacyMind.Neighbor(b, true, 50, 800, 0, Bond.PEACE, true, false, null, true);
        assertEquals(WarGoal.LAND, DiplomacyMind.goal(warrior, state));
        assertEquals(WarGoal.LAND, DiplomacyMind.goal(warrior, new DiplomacyMind.Neighbor(b, true, 20, 800, 0, Bond.PEACE, true, false, null, true)),
                "land before vassalage when the state borders");
        assertEquals(WarGoal.RAID, DiplomacyMind.goal(warrior, new DiplomacyMind.Neighbor(b, true, 51, 800, 0, Bond.PEACE, true, false, null, true)),
                "not twice weaker: loot");
        assertEquals(WarGoal.TRIBUTE, DiplomacyMind.goal(warrior, new DiplomacyMind.Neighbor(b, true, 20, 800, 0, Bond.PEACE, true, false, null, false)),
                "not bordering: as before");
        assertEquals(WarGoal.TRIBUTE, DiplomacyMind.goal(warrior, new DiplomacyMind.Neighbor(b, false, 20, 800, 0, Bond.PEACE, true, false, null, true)),
                "never land of players");
        assertFalse(new DiplomacyMind.Neighbor(b, true, 20, 800, 0, Bond.PEACE, true, false, null).bordering());

        List<DiplomacyMind.Move> moves = DiplomacyMind.decide(warrior, List.of(state), false, 50, RULES);
        DiplomacyMind.Move war = moves.stream().filter(m -> m.kind() == DiplomacyMind.Move.Kind.DECLARE).findFirst().orElseThrow();
        assertEquals(b, war.target());
        assertEquals(WarGoal.LAND, war.goal());
    }
}
