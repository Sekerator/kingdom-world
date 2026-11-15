package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The profile keeps the grade of every class, holds pinned blocks, never digs below water beside the road, turns a short tunnel
 * into a cutting, and finds heights over a cliff of sixty blocks on a way that had none.
 */
class RoadProfileTest {
    private static final RoadRules ROADS = RoadRules.DEFAULT;
    private static final EarthRules EARTH = EarthRules.DEFAULT;

    private static RoadProfile.Result solve(BiFunction<Integer, Integer, LandColumn> land, int length, RoadClass roadClass,
                                            Map<Integer, Integer> pins) {
        List<int[]> cells = new ArrayList<>();
        int[] heights = new int[length];
        for (int x = 0; x < length; x++) {
            cells.add(new int[]{x, 0});
            heights[x] = land.apply(x, 0).top();
        }
        return RoadProfile.solve(new RoadProfile.Input(cells, heights, pins), roadClass, land::apply, ROADS, EARTH, -64, 320);
    }

    static void graded(int[] heights, int grade) {
        int since = grade;
        for (int i = 1; i < heights.length; i++) {
            int dy = Math.abs(heights[i] - heights[i - 1]);
            assertTrue(dy <= 1, "a step of " + dy + " at " + i);
            if (dy == 1) {
                assertTrue(since >= grade, "a change " + since + " blocks after the last at " + i);
                since = 1;
            } else {
                since++;
            }
        }
    }

    @Test
    void everyClassKeepsItsGrade() {
        BiFunction<Integer, Integer, LandColumn> step = (x, z) -> LandColumn.land(x < 60 ? 64 : 74, 3);
        for (RoadClass roadClass : RoadClass.values()) {
            RoadProfile.Result result = solve(step, 120, roadClass, Map.of());
            assertTrue(Double.isFinite(result.cost()));
            graded(result.heights(), EARTH.grade(roadClass));
        }
    }

    @Test
    void pinnedBlocksHoldTheirHeight() {
        Map<Integer, Integer> pins = new HashMap<>();
        pins.put(40, 70);
        RoadProfile.Result result = solve((x, z) -> LandColumn.land(64, 3), 100, RoadClass.GRAVEL, pins);
        assertEquals(70, result.heights()[40]);
        graded(result.heights(), 2);
        assertEquals(64, result.heights()[0], "an end on its ground");
    }

    @Test
    void noCuttingGoesBelowTheWaterBesideIt() {
        // A lake beside the way, two blocks off its middle: within the verge and a block.
        BiFunction<Integer, Integer, LandColumn> land = (x, z) -> z == -3 && x > 20 && x < 80 ? LandColumn.water(50, 62)
                : LandColumn.land(x > 30 && x < 70 ? 70 : 64, 3);
        RoadProfile.Result result = solve(land, 100, RoadClass.GRAVEL, Map.of());
        for (int i = 21; i < 80; i++) {
            if (result.kinds()[i] == RouteCell.Kind.CUT || result.kinds()[i] == RouteCell.Kind.TUNNEL) {
                assertTrue(result.heights()[i] >= 62, "dug below the water at " + i);
            }
        }
    }

    @Test
    void aShortTunnelBecomesACutting() {
        // A sharp crest three blocks thick and twelve high.
        BiFunction<Integer, Integer, LandColumn> crest = (x, z) -> LandColumn.land(x >= 50 && x < 53 ? 76 : 64, 2);
        RoadProfile.Result result = solve(crest, 100, RoadClass.GRAVEL, Map.of());
        int run = 0;
        for (int i = 0; i <= result.kinds().length; i++) {
            if (i < result.kinds().length && result.kinds()[i] == RouteCell.Kind.TUNNEL) {
                run++;
            } else {
                assertFalse(run > 0 && run < 4, "a tunnel of " + run + " blocks before " + i);
                run = 0;
            }
        }
    }

    @Test
    void aCliffOfSixtyOnAWayWithoutHeightsGetsAProfile() {
        BiFunction<Integer, Integer, LandColumn> cliff = (x, z) -> LandColumn.land(x < 100 ? 64 : 124, 3);
        RoadProfile.Result result = solve(cliff, 300, RoadClass.PAVED, Map.of());
        assertTrue(Double.isFinite(result.cost()), "no profile");
        assertTrue(result.windows() > 1, "the window was not widened");
        graded(result.heights(), 3);
    }
}
