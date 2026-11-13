package io.github.sekerator.kingdomworld.core.siege;

import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * War by provinces in the core: how long a province is held, which provinces a peace hands over and the steps of the
 * journal that hands them over.
 */
class ProvinceWarTest {
    private static final DiplomacyRules RULES = DiplomacyRules.DEFAULT;
    private static final SiegeRules SIEGE = SiegeRules.DEFAULT;
    private static final String WORLD = "minecraft:overworld";

    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);
    private final UUID c = new UUID(0, 3);

    @Test
    void aSettledProvinceIsHeldFiveMinutesAnEmptyOneThree() {
        long settled = SIEGE.provinceTicks(true);
        long empty = SIEGE.provinceTicks(false);
        assertEquals(5 * 1_200, settled, "the square of its main settlement: five minutes");
        assertEquals(3 * 1_200, empty, "its boundary stone: three minutes");
        assertEquals(SIEGE.plazaTicks(), SIEGE.ticks(Hold.Kind.PROVINCE));

        Hold town = new Hold(Hold.Kind.PROVINCE, 7, a, b, WORLD, 12, 0, false, 0);
        Hold stone = new Hold(Hold.Kind.PROVINCE, 7, a, b, WORLD, 13, 0, false, 0);
        assertEquals(Hold.Change.PROGRESS, town.step(true, false, false, 3_600, 3_600, settled));
        assertEquals(60, town.percent(settled));
        assertEquals(Hold.Change.TAKEN, stone.step(true, false, false, 3_600, 3_600, empty), "three minutes take an empty province");
        assertEquals(100, stone.percent(empty));
        assertEquals(3_600, stone.since());

        assertEquals(Hold.Change.NONE, town.step(true, false, true, 1_200, 4_800, settled), "a shielded defender is not taken from");
        assertEquals(Hold.Change.INTERRUPTED, town.step(true, true, false, 20, 4_820, settled), "defenders at the square start it over");
        assertEquals(0, town.progress());
        assertEquals(Hold.Change.PROGRESS, town.step(true, false, false, 4_800, 9_620, settled));
        assertEquals(Hold.Change.TAKEN, town.step(true, false, false, 1_200, 10_820, settled));
        assertTrue(town.taken());
        assertEquals(Hold.Change.NONE, town.step(false, true, false, 10_000, 20_820, settled), "a taken province stays taken until the peace");
        assertTrue(town.taken());
        assertFalse(town.idle());

        Hold operator = new Hold(Hold.Kind.PROVINCE, 7, a, b, WORLD, 14, 0, false, 0);
        operator.take(50, empty);
        assertEquals(empty, operator.progress());
        assertEquals(100, operator.percent(settled));
        assertEquals(Optional.of(Hold.Kind.PROVINCE), Hold.Kind.byId("province"));
        assertEquals("province/7/" + b + "/" + WORLD + "/12", town.key());
    }

    @Test
    void thePeaceCedesTheProvincesTakenFirstButNeverTheHeart() {
        SiegeRules rules = SIEGE.withTimes(100, 100, 100);
        SiegeState state = new SiegeState();
        // Provinces 11 to 15 of b, taken in the order 15, 14, 13, 12 (11 never), and 15 holds the capital of b.
        for (int i = 0; i < 5; i++) {
            Hold hold = state.holdAt(Hold.Kind.PROVINCE, 9, a, b, WORLD, 11 + i, 0);
            if (i != 0) {
                hold.step(true, false, false, 100, 10 * (6 - i), rules);
            }
        }
        state.holdAt(Hold.Kind.PROVINCE, 8, a, b, WORLD, 20, 0).take(0, rules);
        state.holdAt(Hold.Kind.PROVINCE, 9, c, b, WORLD, 21, 0).take(0, rules);
        state.holdAt(Hold.Kind.CHUNK, 9, a, b, WORLD, 500, 0).take(0, rules);
        List<Hold> holds = List.copyOf(state.holds());

        PeaceTerms full = PeaceTerms.forGoal(WarGoal.LAND, a, true, 0, RULES);
        PeaceTerms halved = full.halved(RULES);
        assertEquals(2, full.provinces(), "the land of a goal in full is two provinces");
        assertEquals(1, halved.provinces(), "halved, one");
        assertEquals(0, PeaceTerms.WHITE.provinces());
        assertEquals(0, PeaceTerms.forGoal(WarGoal.CITY, a, true, 0, RULES).provinces());

        assertEquals(List.of(14, 13), Conquest.provincesToCede(holds, a, b, 9, full.provinces(), 15),
                "the first taken first, the heart skipped, only of this war and this winner");
        assertEquals(List.of(14), Conquest.provincesToCede(holds, a, b, 9, halved.provinces(), 15));
        assertEquals(List.of(15, 14, 13, 12), Conquest.provincesToCede(holds, a, b, 9, 10, 0), "never more than were taken");
        assertTrue(Conquest.provincesToCede(holds, b, a, 9, 2, 0).isEmpty(), "the loser takes nothing");
        assertTrue(Conquest.provincesToCede(holds, a, b, 9, 0, 0).isEmpty());
        assertEquals(List.of(500L), Conquest.landToCede(holds, a, b, 9, 4), "chunks are counted apart from provinces");

        Map<Long, String> names = Map.of(12L, "Ostbrook", 13L, "Westholm", 14L, "Nordwick", 15L, "Kronstadt");
        Set<Long> settled = Set.of(12L, 13L, 15L);
        assertEquals(Optional.of(12), Conquest.provinceOfCity(holds, a, b, 9, "ostbrook", names::get, settled::contains, 15),
                "the named city with its province");
        assertEquals(Optional.of(13), Conquest.provinceOfCity(holds, a, b, 9, "?", names::get, settled::contains, 15),
                "else the first taken province where people live, not the heart");
        assertEquals(Optional.of(13), Conquest.provinceOfCity(holds, a, b, 9, "Nordwick", names::get, settled::contains, 15),
                "an empty province is no city");
        assertEquals(Optional.of(15), Conquest.provinceOfCity(holds, a, b, 9, "", names::get, settled::contains, 0));
        assertEquals(Optional.empty(), Conquest.provinceOfCity(holds, a, b, 9, "?", names::get, id -> false, 0));
    }

    @Test
    void aPeaceOnAStateClaimsTheProvincesAndOldClaimsHaveNone() {
        DiplomacyState diplomacy = new DiplomacyState();
        War war = diplomacy.declare(a, b, WarGoal.LAND, 0, 0, RULES, false, true).war().orElseThrow();
        assertTrue(diplomacy.startNow(war.id()).ok());
        PeaceTerms terms = PeaceTerms.forGoal(WarGoal.LAND, a, true, 0, RULES);
        assertTrue(diplomacy.endWar(war.id(), War.Result.SURRENDER, terms, 10, 0, RULES, List.of(14, 13)).ok());
        DiplomacyState.Claim claim = diplomacy.claims().get(0);
        assertEquals(List.of(14, 13), claim.provinces());
        assertEquals(terms.landChunks(), claim.chunks());

        War other = diplomacy.declare(a, c, WarGoal.LAND, 20, 0, RULES, false, true).war().orElseThrow();
        assertTrue(diplomacy.startNow(other.id()).ok());
        assertTrue(diplomacy.endWar(other.id(), War.Result.SURRENDER, terms, 30, 0, RULES).ok());
        assertTrue(diplomacy.claims().get(1).provinces().isEmpty(), "a peace on a kingdom without provinces claims chunks");
        assertTrue(new DiplomacyState.Claim(1, a, b, 4, null, false, 0).provinces().isEmpty());

        War white = diplomacy.declare(b, c, WarGoal.LAND, 40, 0, RULES, false, true).war().orElseThrow();
        assertTrue(diplomacy.endWar(white.id(), War.Result.TIME, PeaceTerms.WHITE, 50, 0, RULES, List.of(3)).ok());
        assertEquals(2, diplomacy.claims().size(), "a white peace claims nothing");
    }

    @Test
    void provincesGoStepByStepWithTheirSettlements() {
        SiegeState state = new SiegeState();
        Conquest provinces = state.addConquest(Conquest.Kind.PROVINCE, 3, a, b, WORLD, List.of(14L, 13L), 0, 0);
        assertEquals(List.of(14, 13), provinces.provinces());
        List<Conquest.Step> steps = new ArrayList<>();
        Conquest at = provinces;
        while (!at.done()) {
            steps.add(at.step());
            at = state.advance(at.id()).orElseThrow();
        }
        assertEquals(List.of(Conquest.Step.LAND, Conquest.Step.BUILDINGS, Conquest.Step.CITIZENS, Conquest.Step.CITIES, Conquest.Step.SETTLEMENTS,
                Conquest.Step.DAMAGE), steps);
        assertTrue(state.conquests().isEmpty());

        List<Conquest.Step> annex = new ArrayList<>();
        for (Conquest.Step step = Conquest.first(Conquest.Kind.ANNEX); step != Conquest.Step.DONE; step = Conquest.next(Conquest.Kind.ANNEX, step)) {
            annex.add(step);
        }
        assertEquals(List.of(Conquest.Step.TREASURY, Conquest.Step.LAND, Conquest.Step.BUILDINGS, Conquest.Step.CITIZENS, Conquest.Step.CITIES,
                Conquest.Step.SETTLEMENTS, Conquest.Step.DAMAGE, Conquest.Step.MEMBERS, Conquest.Step.DISSOLVE), annex,
                "an annexation moves the settlements before the kingdom is dissolved");
        assertEquals(Conquest.Step.DAMAGE, Conquest.next(Conquest.Kind.CITY, Conquest.Step.CITIES), "a city has no step of settlements");
        assertFalse(Conquest.has(Conquest.Kind.LAND, Conquest.Step.SETTLEMENTS));
        assertFalse(Conquest.has(Conquest.Kind.PROVINCE, Conquest.Step.TREASURY));
        assertFalse(Conquest.has(Conquest.Kind.PROVINCE, Conquest.Step.DISSOLVE));
        assertTrue(state.addConquest(Conquest.Kind.LAND, 3, a, b, WORLD, List.of(5L), 0, 0).provinces().isEmpty(),
                "chunks of land are no provinces");
        assertEquals(Optional.of(Conquest.Kind.PROVINCE), Conquest.Kind.byId("province"));
        assertEquals(Optional.of(Conquest.Step.SETTLEMENTS), Conquest.Step.byId("settlements"));
    }

    @Test
    void aSettlementIsBeingHandedOverFromTheStepOfItsLandUntilItGoes() {
        SiegeState state = new SiegeState();
        Conquest at = state.addConquest(Conquest.Kind.PROVINCE, 3, a, b, WORLD, List.of(14L, 13L), 0, 0);
        assertTrue(at.handsOver(b, 14), "its land changes hands at the first step");
        assertTrue(at.handsOver(b, 13));
        assertFalse(at.handsOver(b, 12), "a province the conquest does not take");
        assertFalse(at.handsOver(b, 0), "a settlement of no province");
        assertFalse(at.handsOver(a, 14), "a settlement of the winner");
        while (at.step() != Conquest.Step.SETTLEMENTS) {
            at = state.advance(at.id()).orElseThrow();
            assertTrue(at.handsOver(b, 14), at.step().id());
        }
        at = state.advance(at.id()).orElseThrow();
        assertEquals(Conquest.Step.DAMAGE, at.step());
        assertFalse(at.handsOver(b, 14), "once the settlements went, what the loser still has there stays its own");

        Conquest annex = state.addConquest(Conquest.Kind.ANNEX, 4, a, c, WORLD, List.of(), 0, 0);
        assertEquals(Conquest.Step.TREASURY, annex.step());
        assertFalse(annex.handsOver(c, 5), "while its money goes the land of the loser is still its own");
        annex = state.advance(annex.id()).orElseThrow();
        assertTrue(annex.handsOver(c, 5), "an annexation takes every settlement");
        assertTrue(annex.handsOver(c, 0));
        assertFalse(annex.handsOver(b, 5));
        assertFalse(state.addConquest(Conquest.Kind.LAND, 5, a, b, WORLD, List.of(14L), 0, 0).handsOver(b, 14),
                "chunks of land hand over no settlement");
    }
}
