package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorridorSearchTest {
    private static final UUID BUILDER = UUID.randomUUID();

    /** Rolling land: hills and hollows a few blocks high, a lake here and there. */
    private static final Terrain ROLLING = (x, z) -> {
        int y = 64 + (int) Math.round(4 * Math.sin(x / 23.0) + 3 * Math.cos(z / 17.0));
        if (Math.floorMod(x * 7 + z * 13, 311) < 9 && Math.floorMod(x, 97) < 20) {
            return Terrain.Column.water(62);
        }
        return Terrain.Column.land(y);
    };

    private static List<RouteCell> search(Terrain terrain, int[] xs, int[] zs, CorridorSearch[] out) {
        CorridorSearch search = new CorridorSearch(terrain, xs, zs, BUILDER, RoadRules.DEFAULT);
        out[0] = search;
        for (int tick = 0; tick < 100_000 && search.status() == CorridorSearch.Status.RUNNING; tick++) {
            search.step(RoadRules.DEFAULT.nodesPerTick());
        }
        return search.route();
    }

    @Test
    void aLongRoadIsFoundAStretchAtATimeWithFewStepsEach() {
        int[] xs = {0, 400, 900, 1400, 2000};
        int[] zs = {0, 150, -100, 200, 50};
        CorridorSearch[] out = new CorridorSearch[1];
        List<RouteCell> route = search(ROLLING, xs, zs, out);
        CorridorSearch search = out[0];
        assertEquals(CorridorSearch.Status.FOUND, search.status(), "failure " + search.failure());
        assertTrue(search.stretchCount() >= 8, "stretches of 256 blocks at most: " + search.stretchCount());
        assertTrue(search.mostInStretch() <= CorridorSearch.STRETCH_NODES, "steps of a stretch: " + search.mostInStretch());
        // The whole way, two thousand blocks long, in far fewer steps than the search of players allows.
        assertTrue(search.expanded() < RoadRules.DEFAULT.maxNodes() / 2, "steps in all: " + search.expanded());
        RouteCell first = route.get(0);
        RouteCell last = route.get(route.size() - 1);
        assertTrue(Math.abs(first.x()) <= RouteSearch.START_RADIUS && Math.abs(first.z()) <= RouteSearch.START_RADIUS, "start " + first);
        assertTrue(Math.abs(last.x() - 2000) <= RouteSearch.GOAL_RADIUS && Math.abs(last.z() - 50) <= RouteSearch.GOAL_RADIUS, "end " + last);
    }

    @Test
    void theStretchesJoinIntoOneWayWithinTheCorridor() {
        int[] xs = {0, 300, 600, 900};
        int[] zs = {0, 250, 0, 250};
        CorridorSearch[] out = new CorridorSearch[1];
        List<RouteCell> route = search(ROLLING, xs, zs, out);
        assertEquals(CorridorSearch.Status.FOUND, out[0].status());
        CorridorTerrain corridor = new CorridorTerrain(ROLLING, xs, zs, CorridorTerrain.WIDTH);
        for (int i = 0; i < route.size(); i++) {
            RouteCell cell = route.get(i);
            assertTrue(corridor.inside(cell.x(), cell.z()), "a block outside the corridor: " + cell);
            if (i > 0) {
                RouteCell before = route.get(i - 1);
                assertTrue(Math.max(Math.abs(cell.x() - before.x()), Math.abs(cell.z() - before.z())) == 1, "a gap at " + before + " -> " + cell);
            }
        }
    }

    @Test
    void theCorridorBlocksTheLandAwayFromTheLine() {
        CorridorTerrain corridor = new CorridorTerrain(ROLLING, new int[]{0, 1000}, new int[]{0, 0}, 64);
        assertEquals(Terrain.Surface.LAND, corridor.column(500, 60).surface());
        assertEquals(Terrain.Surface.BLOCKED, corridor.column(500, 70).surface());
        assertEquals(Terrain.Surface.BLOCKED, corridor.column(-70, 0).surface());
        assertFalse(corridor.inside(1065, 0));
    }

    @Test
    void aPointThatCannotBeReachedIsMovedAside() {
        // A wall of lava across the line where a point of it lies, with a gap a little to one side.
        Terrain wall = (x, z) -> x >= 290 && x <= 310 && !(z >= 15 && z <= 32) ? new Terrain.Column(64, Terrain.Surface.DANGER, null)
                : Terrain.Column.land(64);
        CorridorSearch[] out = new CorridorSearch[1];
        List<RouteCell> route = search(wall, new int[]{0, 300, 600}, new int[]{0, 0, 0}, out);
        assertEquals(CorridorSearch.Status.FOUND, out[0].status(), "failure " + out[0].failure());
        assertTrue(out[0].shifted() > 0, "the point in the lava was moved aside");
        assertFalse(route.isEmpty());
    }

    @Test
    void aLongLegIsCutIntoStretches() {
        int[][] points = CorridorSearch.stretches(new int[]{0, 1000}, new int[]{0, 0}, 256);
        assertEquals(5, points[0].length);
        assertEquals(1000, points[0][4]);
        for (int i = 1; i < points[0].length; i++) {
            assertTrue(points[0][i] - points[0][i - 1] <= 256);
        }
    }
}
