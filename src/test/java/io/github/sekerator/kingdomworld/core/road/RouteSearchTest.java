package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteSearchTest {
    private static final UUID US = new UUID(1, 1);
    private static final UUID THEM = new UUID(2, 2);

    private static RouteSearch run(BiFunction<Integer, Integer, Terrain.Column> land, int fromX, int fromZ, int toX, int toZ, RoadRules rules) {
        RouteSearch search = new RouteSearch(land::apply, fromX, fromZ, toX, toZ, US, rules);
        for (int i = 0; i < 1_000 && search.status() == RouteSearch.Status.RUNNING; i++) {
            search.step(rules.nodesPerTick());
        }
        return search;
    }

    private static RouteSearch run(BiFunction<Integer, Integer, Terrain.Column> land, int fromX, int fromZ, int toX, int toZ) {
        return run(land, fromX, fromZ, toX, toZ, RoadRules.DEFAULT);
    }

    private static void assertConnected(List<RouteCell> cells) {
        for (int i = 1; i < cells.size(); i++) {
            RouteCell a = cells.get(i - 1);
            RouteCell b = cells.get(i);
            int steps = Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
            assertEquals(1, steps, "blocks " + a + " and " + b + " are not side by side");
            assertTrue(Math.abs(a.y() - b.y()) <= 1, "the road jumps from " + a.y() + " to " + b.y());
        }
    }

    @Test
    void aFlatWayIsAlmostStraight() {
        RouteSearch search = run((x, z) -> Terrain.Column.land(64), 0, 0, 60, 30);
        assertEquals(RouteSearch.Status.FOUND, search.status());
        List<RouteCell> cells = search.route();
        assertConnected(cells);
        assertTrue(cells.size() <= 60 + 30 + 2, "the way is too long: " + cells.size());
        RouteCell last = cells.get(cells.size() - 1);
        assertTrue(Math.abs(last.x() - 60) <= RouteSearch.GOAL_RADIUS && Math.abs(last.z() - 30) <= RouteSearch.GOAL_RADIUS);
    }

    @Test
    void theRoadGoesRoundACliff() {
        // A wall ten blocks high from z = -40 to z = 10 at x = 20, open to the south.
        RouteSearch search = run((x, z) -> x == 20 && z >= -40 && z <= 10 ? Terrain.Column.land(74) : Terrain.Column.land(64), 0, 0, 40, 0);
        assertEquals(RouteSearch.Status.FOUND, search.status());
        assertConnected(search.route());
        for (RouteCell cell : search.route()) {
            assertFalse(cell.x() == 20 && cell.z() >= -40 && cell.z() <= 10, "the road climbs the cliff at " + cell);
        }
        assertTrue(search.route().stream().anyMatch(cell -> cell.z() > 10), "the road does not go round the cliff");
    }

    @Test
    void waterIsCrossedOnABridge() {
        // A river eight blocks wide across the whole way.
        RouteSearch search = run((x, z) -> x >= 10 && x < 18 ? Terrain.Column.water(62) : Terrain.Column.land(63), 0, 0, 30, 0);
        assertEquals(RouteSearch.Status.FOUND, search.status());
        assertConnected(search.route());
        long bridges = search.route().stream().filter(cell -> cell.kind() == RouteCell.Kind.BRIDGE).count();
        assertTrue(bridges >= 8, "no bridge over the river: " + bridges);
        assertTrue(search.route().stream().filter(cell -> cell.kind() == RouteCell.Kind.BRIDGE).allMatch(cell -> cell.y() == 63),
                "the bridge is not on the water");
    }

    @Test
    void aRiverWiderThanTheLongestBridgeStopsTheRoad() {
        RoadRules.Builder rules = new RoadRules.Builder();
        rules.maxBridge = 6;
        rules.maxNodes = 20_000;
        RouteSearch search = run((x, z) -> x >= 10 && x < 20 ? Terrain.Column.water(62) : Terrain.Column.land(63), 0, 0, 30, 0, rules.build());
        assertEquals(RouteSearch.Status.FAILED, search.status());
        assertTrue(search.route().isEmpty());
    }

    @Test
    void aShallowGapGetsAnEmbankmentAndADeepOneABridge() {
        RouteSearch shallow = run((x, z) -> x >= 10 && x < 13 ? Terrain.Column.land(61) : Terrain.Column.land(64), 0, 0, 25, 0);
        assertEquals(RouteSearch.Status.FOUND, shallow.status());
        assertTrue(shallow.route().stream().anyMatch(cell -> cell.kind() == RouteCell.Kind.FILL), "no embankment over the gap");
        RouteSearch deep = run((x, z) -> x >= 10 && x < 13 ? Terrain.Column.land(40) : Terrain.Column.land(64), 0, 0, 25, 0);
        assertEquals(RouteSearch.Status.FOUND, deep.status());
        assertTrue(deep.route().stream().anyMatch(cell -> cell.kind() == RouteCell.Kind.BRIDGE), "no bridge over the ravine");
    }

    @Test
    void aSteepSlopeIsClimbedOneBlockAtATime() {
        // A slope that rises two blocks for every block on the diagonal: the road climbs it in zigzags.
        RouteSearch search = run((x, z) -> Terrain.Column.land(64 + Math.max(0, Math.min(40, x + z))), 0, 0, 20, 20);
        assertEquals(RouteSearch.Status.FOUND, search.status());
        assertConnected(search.route());
        int top = search.route().stream().mapToInt(RouteCell::y).max().orElse(0);
        assertTrue(top >= 100, "the road did not climb: " + top);
    }

    @Test
    void aRoadFromAThroneStartsOnTheLandAroundIt() {
        // The throne stands six blocks above the ground and is not to be broken.
        RouteSearch search = run((x, z) -> x == 0 && z == 0 ? new Terrain.Column(70, Terrain.Surface.BLOCKED, null) : Terrain.Column.land(64),
                0, 0, 30, 0);
        assertEquals(RouteSearch.Status.FOUND, search.status());
        RouteCell first = search.route().get(0);
        assertTrue(Math.abs(first.x()) <= RouteSearch.START_RADIUS && first.y() == 64, "the road does not start beside the throne: " + first);
        RouteSearch nowhere = run((x, z) -> new Terrain.Column(64, Terrain.Surface.DANGER, null), 0, 0, 30, 0);
        assertEquals(RouteSearch.Failure.BAD_START, nowhere.failure());
    }

    @Test
    void dangerIsAvoided() {
        RouteSearch search = run((x, z) -> x >= 10 && x < 14 && z > -20 && z < 20 ? new Terrain.Column(64, Terrain.Surface.DANGER, null)
                : Terrain.Column.land(64), 0, 0, 30, 0);
        assertEquals(RouteSearch.Status.FOUND, search.status());
        assertTrue(search.route().stream().noneMatch(cell -> cell.x() >= 10 && cell.x() < 14 && cell.z() > -20 && cell.z() < 20));
    }

    @Test
    void theLandOfOthersIsAvoidedWhenItCostsLittleAndCrossedOtherwise() {
        RouteSearch around = run((x, z) -> x >= 10 && x < 20 && z > -4 && z < 4 ? new Terrain.Column(64, Terrain.Surface.LAND, THEM)
                : Terrain.Column.land(64), 0, 0, 30, 0);
        assertEquals(RouteSearch.Status.FOUND, around.status());
        assertTrue(RouteSearch.crossed(around.route(), US).isEmpty(), "the road crosses foreign land it could go round");
        RouteSearch through = run((x, z) -> x >= 10 && x < 20 ? new Terrain.Column(64, Terrain.Surface.LAND, THEM) : Terrain.Column.land(64),
                0, 0, 30, 0);
        assertEquals(RouteSearch.Status.FOUND, through.status());
        assertEquals(java.util.Set.of(THEM), RouteSearch.crossed(through.route(), US));
    }

    @Test
    void theLongestRoadIsKept() {
        RoadRules.Builder rules = new RoadRules.Builder();
        rules.maxRouteLength = 50;
        RouteSearch far = run((x, z) -> Terrain.Column.land(64), 0, 0, 200, 0, rules.build());
        assertEquals(RouteSearch.Failure.TOO_FAR, far.failure());
        // Close enough in a straight line, but the only way round a wall is too long.
        RouteSearch round = run((x, z) -> x == 20 && z > -200 && z < 200 ? Terrain.Column.land(90) : Terrain.Column.land(64), 0, 0, 40, 0,
                rules.build());
        assertEquals(RouteSearch.Status.FAILED, round.status());
    }

    @Test
    void theSearchWaitsForLandNotLoadedYet() {
        java.util.Set<Long> loaded = new java.util.HashSet<>();
        int[] waits = new int[1];
        Terrain slow = (x, z) -> {
            long chunk = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFF_FFFFL);
            if (loaded.add(chunk)) {
                waits[0]++;
                return null;
            }
            return Terrain.Column.land(64);
        };
        RouteSearch search = new RouteSearch(slow, 0, 0, 40, 0, US, RoadRules.DEFAULT);
        for (int i = 0; i < 10_000 && search.status() == RouteSearch.Status.RUNNING; i++) {
            search.step(5);
        }
        assertEquals(RouteSearch.Status.FOUND, search.status());
        assertTrue(waits[0] > 1, "the search never waited");
    }

    @Test
    void aPathIsOneBlockWideAndOthersThree() {
        RouteSearch search = run((x, z) -> Terrain.Column.land(64), 0, 0, 20, 0);
        List<RouteCell> cells = search.route();
        assertEquals(cells.size(), RoadLayout.blocks(cells, RoadClass.PATH).size());
        assertEquals(cells.size() * 3, RoadLayout.blocks(cells, RoadClass.GRAVEL).size());
        int[] path = RoadLayout.materials(RoadLayout.blocks(cells, RoadClass.PATH), RoadClass.PATH);
        assertEquals(0, path[0] + path[1] + path[2], "a path on the ground needs no material");
        int[] paved = RoadLayout.materials(RoadLayout.blocks(cells, RoadClass.PAVED), RoadClass.PAVED);
        assertEquals(cells.size() * 3, paved[RoadLayout.Material.SURFACE.ordinal()]);
    }
}
