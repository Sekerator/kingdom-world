package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a city keeps of its own: its townsfolk stay with it when it changes hands and go with it when it is given up or its
 * kingdom is gone.
 */
class CityCharterTest {
    private static final UUID US = new UUID(1, 1);
    private static final UUID THEM = new UUID(2, 2);
    private static final String WORLD = "minecraft:overworld";

    @Test
    void aCityStartsWithNothingAndCountsItsTownsfolk() {
        RoadState state = new RoadState();
        int[] saves = new int[1];
        state.setChangeListener(() -> saves[0]++);
        long version = state.version();
        CityCharter capital = state.charterOf(US, 0, 1_000);
        assertEquals(1_000, capital.lastArrival(), "a city that starts now waits for the next newcomer from the last one of the kingdom");
        assertEquals(CityCharter.NEVER, capital.lastDay());
        assertTrue(saves[0] > 0, "a new record is saved");
        assertEquals(version, state.version(), "what is built from the cities stays as it is");
        capital.setFolk(900);
        City city = state.addCity(US, "Olkhovka", WORLD, 500, 64, 0, 0);
        state.charterOf(US, city.id(), CityCharter.NEVER).setFolk(36);
        state.charterOf(THEM, 0, CityCharter.NEVER).setFolk(5);
        assertEquals(900, state.folk(US, 0));
        assertEquals(36, state.folk(US, city.id()));
        assertEquals(0, state.folk(US, 99));
        assertEquals(936, state.folkOf(US));
        assertTrue(state.charterOf(US, 0, 0) == capital, "a city keeps one record");
        capital.setFolk(-5);
        assertEquals(0, capital.folk());
        capital.setFolk(Integer.MAX_VALUE);
        assertEquals(CityCharter.MAX_FOLK, capital.folk());
    }

    @Test
    void aCityGivenUpTakesItsTownsfolkAlong() {
        RoadState state = new RoadState();
        City city = state.addCity(US, "Kamenka", WORLD, 500, 64, 0, 0);
        state.charterOf(US, city.id(), CityCharter.NEVER).setFolk(120);
        state.charterOf(US, 0, CityCharter.NEVER).setFolk(10);
        state.removeCity(city.id());
        assertTrue(state.charter(US, city.id()).isEmpty());
        assertEquals(10, state.folk(US, 0), "the capital keeps its own");
    }

    @Test
    void aKingdomGoneTakesTheRecordsOfItsCities() {
        RoadState state = new RoadState();
        state.charterOf(US, 0, CityCharter.NEVER).setFolk(10);
        state.charterOf(THEM, 0, CityCharter.NEVER).setFolk(20);
        state.forgetKingdom(US);
        assertTrue(state.charter(US, 0).isEmpty());
        assertEquals(20, state.folk(THEM, 0));
    }

    @Test
    void aCityTakenInAWarKeepsItsTownsfolk() {
        RoadState state = new RoadState();
        City city = state.addCity(THEM, "Kamenka", WORLD, 500, 64, 0, 0);
        CityCharter theirs = state.charterOf(THEM, city.id(), 77);
        theirs.setFolk(250);
        theirs.setFolkCarry(0.4);
        theirs.setLastDay(12);
        state.handOverCharter(city.id(), THEM, US, city.id());
        assertTrue(state.charter(THEM, city.id()).isEmpty());
        CityCharter ours = state.charter(US, city.id()).orElseThrow();
        assertEquals(250, ours.folk());
        assertEquals(0.4, ours.folkCarry(), 1e-9);
        assertEquals(12, ours.lastDay());
        assertEquals(77, ours.lastArrival());
        // An annexed capital becomes a city of the winner, its townsfolk with it.
        state.charterOf(THEM, 0, CityCharter.NEVER).setFolk(3_000);
        City old = state.addCity(US, "Old capital", WORLD, 900, 64, 0, 0);
        state.handOverCharter(0, THEM, US, old.id());
        assertEquals(3_000, state.folk(US, old.id()));
        assertEquals(0, state.folk(THEM, 0));
        state.handOverCharter(5, THEM, US, 6);
        assertFalse(state.charter(US, 6).isPresent(), "a city that kept nothing hands nothing over");
    }

    @Test
    void anEmptyRecordHoldsNothingWorthKeeping() {
        CityCharter charter = new CityCharter(US, 3);
        assertTrue(charter.empty());
        charter.setLastArrival(5);
        assertFalse(charter.empty());
        CityCharter moved = charter.movedTo(THEM, 4);
        assertEquals(THEM, moved.kingdomId());
        assertEquals(4, moved.cityId());
        assertEquals(5, moved.lastArrival());
    }

    @Test
    void aCityTakenInAWarLosesItsMayorAndItsPause() {
        RoadState state = new RoadState();
        City city = state.addCity(THEM, "Kamenka", WORLD, 500, 64, 0, 0);
        CityCharter theirs = state.charterOf(THEM, city.id(), CityCharter.NEVER);
        UUID mayor = new UUID(9, 9);
        theirs.setFolk(40);
        theirs.setMayor(mayor, 100);
        theirs.setPaused(true);
        theirs.setNote(new GovernorNote(GovernorNote.Kind.BUILDS, "kingdomworld:well", 0).encode());
        theirs.spend(3, 50);
        theirs.refuse("kingdomworld:well", 5);
        assertTrue(state.mayoralty(mayor).isPresent());
        state.handOverCharter(city.id(), THEM, US, city.id());
        CityCharter ours = state.charter(US, city.id()).orElseThrow();
        assertEquals(40, ours.folk(), "the townsfolk stay");
        assertNull(ours.mayor(), "the mayor does not come along");
        assertFalse(ours.paused());
        assertTrue(ours.note().isEmpty());
        assertEquals(0, ours.spent(3));
        assertTrue(ours.refused().isEmpty());
        assertTrue(state.mayoralty(mayor).isEmpty());
    }

    @Test
    void theRecordOfAGovernorGoesWithItsCityAndItsKingdom() {
        RoadState state = new RoadState();
        City city = state.addCity(US, "Olkhovka", WORLD, 500, 64, 0, 0);
        UUID mayor = new UUID(7, 7);
        state.charterOf(US, city.id(), CityCharter.NEVER).setMayor(mayor, 10);
        state.charterOf(US, 0, CityCharter.NEVER).setMayor(new UUID(8, 8), 10);
        state.removeCity(city.id());
        assertTrue(state.mayoralty(mayor).isEmpty(), "a city given up takes its mayor along");
        assertTrue(state.mayoralty(new UUID(8, 8)).isPresent(), "a capital keeps its mayor");
        state.forgetKingdom(US);
        assertTrue(state.charters().isEmpty(), "a kingdom gone takes the mayor of its capital along");
    }

    @Test
    void aGovernorRemembersWhatItSpentAndWhatTheKingGaveUp() {
        CityCharter charter = new CityCharter(US, 3);
        charter.spend(4, 30);
        charter.spend(4, 20);
        assertEquals(50, charter.spent(4));
        assertEquals(0, charter.spent(5), "another day starts from nothing");
        charter.spend(5, 7);
        assertEquals(7, charter.spent(5));
        assertEquals(0, charter.spent(4));
        charter.refuse("kingdomworld:well", 8);
        charter.refuse("kingdomworld:well", 6);
        assertTrue(charter.refuses("kingdomworld:well", 8), "the later day holds");
        assertFalse(charter.refuses("kingdomworld:well", 9));
        assertFalse(charter.refuses("kingdomworld:market", 1));
        assertFalse(charter.forgetRefused(8));
        assertTrue(charter.forgetRefused(9));
        assertTrue(charter.refused().isEmpty());
    }

    @Test
    void aRecordWithAGovernorIsWorthKeeping() {
        CityCharter charter = new CityCharter(US, 3);
        charter.setPaused(true);
        assertFalse(charter.empty(), "a city stopped by the king remembers it");
        charter.setPaused(false);
        charter.setMayor(new UUID(1, 2), 5);
        assertFalse(charter.empty());
        assertEquals(5, charter.mayorSince());
        charter.clearGovernor();
        assertNull(charter.mayor());
        assertEquals(CityCharter.NEVER, charter.mayorSince());
        assertTrue(charter.empty());
    }

    @Test
    void aCityWidenedByItsGovernorKeepsItsSquare() {
        CityCharter charter = new CityCharter(US, 3);
        charter.setExtent(-4);
        assertEquals(0, charter.extent());
        charter.setExtent(64);
        assertFalse(charter.empty(), "a widened city remembers it");
        charter.clearGovernor();
        assertEquals(64, charter.extent(), "the city stays as wide when its governor goes");
        assertEquals(64, charter.movedTo(THEM, 4).extent(), "and when it changes hands");
    }
}
