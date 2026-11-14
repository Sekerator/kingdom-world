package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
