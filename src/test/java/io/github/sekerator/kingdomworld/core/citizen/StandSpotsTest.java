package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a body stands to work at a block: on the ground beside it within reach, never on leaves or on the block itself, the
 * places nearest to the body first.
 */
class StandSpotsTest {
    private static final StandSpots.Footing GROUND = new StandSpots.Footing(true, true, false, true, false);
    private static final StandSpots.Footing LEAVES = new StandSpots.Footing(true, true, false, true, true);
    private static final StandSpots.Footing AIR = new StandSpots.Footing(true, true, false, false, false);

    /** Flat ground with its surface at y = 0: feet stand at y = 0. */
    private static StandSpots.Footing flat(int x, int y, int z) {
        return y == 0 ? GROUND : AIR;
    }

    @Test
    void aPlaceNeedsRoomADryFloorAndNoLeaves() {
        assertTrue(GROUND.standable());
        assertFalse(LEAVES.standable(), "leaves bear a body but are no ground to work from");
        assertFalse(AIR.standable(), "nothing under the feet");
        assertFalse(new StandSpots.Footing(false, true, false, true, false).standable(), "no room for the feet");
        assertFalse(new StandSpots.Footing(true, false, false, true, false).standable(), "no room for the head");
        assertFalse(new StandSpots.Footing(true, true, true, true, false).standable(), "in water");
    }

    @Test
    void placesAreWithinReachOnTheGroundAndOutOfTheColumnOfTheBlock() {
        double reachSqr = 4.0 * 4.0;
        List<StandSpots.Spot> spots = StandSpots.around(10, 0, 10, reachSqr, 30.5, 0.0, 10.5, StandSpotsTest::flat);
        assertFalse(spots.isEmpty());
        for (StandSpots.Spot spot : spots) {
            assertEquals(0, spot.y(), "feet on the ground");
            assertFalse(spot.x() == 10 && spot.z() == 10, "not on the block itself");
            assertTrue(StandSpots.distanceSqr(spot.x(), spot.y(), spot.z(), 10, 0, 10) <= StandSpots.chosenReachSqr(reachSqr),
                    "within reach, short of its edge: " + spot);
        }
        // Three blocks from the trunk, under a low crown, is within the reach of a lumberjack.
        assertTrue(spots.contains(new StandSpots.Spot(13, 0, 10)));
        assertFalse(spots.contains(new StandSpots.Spot(14, 0, 10)), "four blocks off is beyond the margin");
    }

    @Test
    void theNearestPlacesToTheBodyComeFirst() {
        List<StandSpots.Spot> spots = StandSpots.around(10, 0, 10, 4.0 * 4.0, 30.5, 0.0, 10.5, StandSpotsTest::flat);
        assertEquals(new StandSpots.Spot(13, 0, 10), spots.get(0), "on the side of the body, nearest to it");
        for (int i = 1; i < spots.size(); i++) {
            assertTrue(fromBody(spots.get(i - 1)) <= fromBody(spots.get(i)), "ordered by distance from the body");
        }
    }

    private static double fromBody(StandSpots.Spot spot) {
        double dx = spot.x() + 0.5 - 30.5;
        double dz = spot.z() + 0.5 - 10.5;
        return dx * dx + spot.y() * spot.y() + dz * dz;
    }

    @Test
    void aLowCrownAroundTheTrunkLeavesOnlyTheGroundBeyondIt() {
        // A trunk at (0, 0, 0) with a row of leaves two blocks around it at y = 1: a body could stand on them at y = 2, and
        // under them it has no room for its head.
        StandSpots.Ground ground = (x, y, z) -> {
            boolean underCrown = Math.abs(x) <= 2 && Math.abs(z) <= 2;
            if (y == 2 && underCrown) {
                return LEAVES;
            }
            if (y == 0) {
                return underCrown ? new StandSpots.Footing(true, false, false, true, false) : GROUND;
            }
            return AIR;
        };
        List<StandSpots.Spot> spots = StandSpots.around(0, 0, 0, 4.0 * 4.0, 7.5, 0.0, 0.5, ground);
        assertFalse(spots.isEmpty());
        for (StandSpots.Spot spot : spots) {
            assertEquals(0, spot.y(), "never on the crown");
            assertTrue(Math.abs(spot.x()) > 2 || Math.abs(spot.z()) > 2, "beyond the crown: " + spot);
        }
    }

    @Test
    void noPlaceWhenTheBlockStandsAlone() {
        assertTrue(StandSpots.around(0, 0, 0, 3.0 * 3.0, 5.0, 0.0, 5.0, (x, y, z) -> AIR).isEmpty());
    }
}
