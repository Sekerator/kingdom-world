package io.github.sekerator.kingdomworld.core.construction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reach of a post of builders is its city, and its radius as before; helpers share their time between far constructions and
 * roads and are faster by road.
 */
class BuilderReachTest {
    private static final long CAPITAL = 0;
    private static final long VILLAGE = 3;
    private static final long TOWN = 7;
    private static final int RADIUS = 64;

    @Test
    void aPostInACityTakesEveryConstructionOfItsCity() {
        double far = 300.0 * 300.0;
        assertTrue(BuilderReach.reaches(VILLAGE, VILLAGE, far, RADIUS), "anywhere in its city");
        assertFalse(BuilderReach.reaches(VILLAGE, TOWN, far, RADIUS), "not in another city");
        assertFalse(BuilderReach.reaches(VILLAGE, BuilderReach.NO_CITY, far, RADIUS), "not outside the cities");
        assertFalse(BuilderReach.reaches(BuilderReach.NO_CITY, BuilderReach.NO_CITY, far, RADIUS), "two places outside the cities are no city");
    }

    @Test
    void theRadiusStillHolds() {
        double near = 60.0 * 60.0;
        assertTrue(BuilderReach.reaches(CAPITAL, BuilderReach.NO_CITY, near, RADIUS), "just beyond the square of the capital, near its post");
        assertTrue(BuilderReach.reaches(BuilderReach.NO_CITY, TOWN, near, RADIUS), "a post outside the cities near a town");
        assertTrue(BuilderReach.reaches(CAPITAL, TOWN, 64.0 * 64.0, RADIUS));
        assertFalse(BuilderReach.reaches(CAPITAL, TOWN, 65.0 * 65.0, RADIUS));
    }

    @Test
    void helpersShareTheirTimeWithRoads() {
        assertEquals(0.5, HelperWork.remoteShare(true), "half while roads and walls wait too");
        assertEquals(1.0, HelperWork.remoteShare(false), "all of it otherwise");
    }

    @Test
    void helpersAreFasterByRoad() {
        // The capital and the village are linked by a road, the town by a road to the village; the hamlet has none.
        Map<Long, Long> linked = HelperWork.linked(List.of(new long[]{CAPITAL, VILLAGE}, new long[]{VILLAGE, TOWN}));
        assertTrue(HelperWork.connected(linked, CAPITAL, TOWN), "through the village");
        assertTrue(HelperWork.connected(linked, TOWN, CAPITAL));
        assertTrue(HelperWork.connected(linked, 11, 11), "the same city");
        assertFalse(HelperWork.connected(linked, CAPITAL, 11));
        assertFalse(HelperWork.connected(linked, 11, 12));
        assertFalse(HelperWork.connected(linked, CAPITAL, BuilderReach.NO_CITY), "a place outside the cities has no road");
        assertEquals(1.0, HelperWork.speed(true, 0.75));
        assertEquals(0.75, HelperWork.speed(false, 0.75));
        assertTrue(HelperWork.linked(List.of()).isEmpty());
    }
}
