package io.github.sekerator.kingdomworld.core;

import io.github.sekerator.kingdomworld.core.citizen.CitizenRules;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The limits of a kingdom of players: 0 is none and none is the default, a configuration written before with the old defaults
 * gets the new rules, and any other number written there stays.
 */
class LimitsTest {

    @Test
    void zeroIsNoLimit() {
        assertFalse(Limits.reached(0, 0));
        assertFalse(Limits.reached(1_000_000, 0));
        assertFalse(Limits.reached(5, -1));
        assertFalse(Limits.reached(5, 6));
        assertTrue(Limits.reached(6, 6));
        assertTrue(Limits.reached(7, 6));
    }

    @Test
    void theDefaultsHaveNoLimit() {
        assertEquals(0, CitizenRules.DEFAULT.maxPerKingdom());
        assertFalse(CitizenRules.DEFAULT.full(100_000));
        assertEquals(0, ConstructionRules.DEFAULT.maxConstructions());
        assertFalse(ConstructionRules.DEFAULT.full(1_000));
        assertEquals(0, RoadRules.DEFAULT.maxCities());
        assertEquals(0, RoadRules.DEFAULT.maxRoads());
        assertFalse(RoadRules.DEFAULT.citiesFull(100));
        assertFalse(RoadRules.DEFAULT.roadsFull(10_000));
        assertEquals(Limits.CITY_DISTANCE_DEFAULT, RoadRules.DEFAULT.minCityDistance());
    }

    @Test
    void theOldDefaultsOfAConfigurationAreReadAsTheNewOnes() {
        assertEquals(0, Limits.effective(Limits.CITIZENS, 200));
        assertEquals(0, Limits.effective(Limits.CITIES, 6));
        assertEquals(0, Limits.effective(Limits.ROADS, 32));
        assertEquals(0, Limits.effective(Limits.CONSTRUCTIONS, 4));
        assertEquals(112, Limits.effective(Limits.CITY_DISTANCE, 160));
    }

    @Test
    void anyOtherNumberWrittenStays() {
        assertEquals(201, Limits.effective(Limits.CITIZENS, 201));
        assertEquals(150, Limits.effective(Limits.CITIZENS, 150));
        assertEquals(0, Limits.effective(Limits.CITIZENS, 0));
        assertEquals(5, Limits.effective(Limits.CITIES, 5));
        assertEquals(7, Limits.effective(Limits.CITIES, 7));
        assertEquals(33, Limits.effective(Limits.ROADS, 33));
        assertEquals(3, Limits.effective(Limits.CONSTRUCTIONS, 3));
        assertEquals(200, Limits.effective(Limits.CITY_DISTANCE, 200));
        assertEquals(6, Limits.effective("roads.maxBridge", 6), "only the limits are read anew");
        assertEquals(160, Limits.effective("guild.something", 160));
    }

    @Test
    void theDistanceBetweenCitiesFollowsTheWalls() {
        assertEquals(112, Limits.cityDistance(24), "two large cities with walls 48 blocks from their middle, 16 blocks apart");
        assertEquals(176, Limits.cityDistance(40));
        assertEquals(112, Limits.cityDistance(0, 24));
        assertEquals(176, Limits.cityDistance(0, 40), "an operator who raised the walls gets the distance they need");
        assertEquals(176, Limits.cityDistance(160, 40), "the old default follows the walls too");
        assertEquals(176, Limits.cityDistance(112, 40));
        assertEquals(112, Limits.cityDistance(50, 24), "never closer than the walls need");
        assertEquals(300, Limits.cityDistance(300, 24), "a larger number written is kept");
        assertEquals(300, Limits.cityDistance(300, 40));
    }

    @Test
    void theGuideGetsTheLimitsTheServerRunsWith() {
        assertTrue(Limits.isLimit(Limits.CITIES) && Limits.isLimit(Limits.CITIZENS) && Limits.isLimit(Limits.ROADS)
                && Limits.isLimit(Limits.CONSTRUCTIONS));
        assertFalse(Limits.isLimit(Limits.CITY_DISTANCE), "a distance of 0 is no distance, not no limit");
        assertEquals("0", Limits.effectiveText(Limits.CITIES, "6"));
        assertEquals("12", Limits.effectiveText(Limits.CITIES, "12"));
        assertEquals("112", Limits.effectiveText(Limits.CITY_DISTANCE, "160"));
        assertEquals("6", Limits.effectiveText("roads.maxBridge", "6"));
        assertEquals("1.5", Limits.effectiveText(Limits.CITIES, "1.5"), "what is no whole number stays as it is");
        assertEquals(null, Limits.effectiveText(Limits.CITIES, null));
    }
}
