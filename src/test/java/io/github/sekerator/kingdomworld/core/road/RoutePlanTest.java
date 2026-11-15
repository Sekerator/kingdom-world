package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plan asked for again with the same points: its legs come from those planned before, and the plan, its works and its estimate
 * are the same as the first time, the land of those legs being read for the new request.
 */
class RoutePlanTest {
    /**
     * Land that knows only the chunks read into it, as the land of a new request does.
     */
    private static final class Fresh implements LegPlanner.Land {
        private final SyntheticLand land;
        private final Set<Long> read = new HashSet<>();

        Fresh(SyntheticLand land) {
            this.land = land;
        }

        @Override
        public LandColumn at(int x, int z) {
            return read.contains(LandSnapshot.chunk(x >> 4, z >> 4)) ? land.at(x, z) : null;
        }

        @Override
        public boolean read(Set<Long> chunks) {
            read.addAll(chunks);
            return true;
        }

        @Override
        public UUID owner(int x, int z) {
            return land.owner(x, z);
        }

        @Override
        public CoarseRoute.Field field(int cx0, int cz0, int cx1, int cz1) {
            return land.field(cx0, cz0, cx1, cz1);
        }

        @Override
        public boolean guessed(int chunkX, int chunkZ) {
            return false;
        }

        @Override
        public long roadAt(int x, int z) {
            return -1;
        }

        @Override
        public int minY() {
            return land.minY();
        }

        @Override
        public int maxY() {
            return land.maxY();
        }
    }

    private static RoutePlan.Result plan(LegPlanner.Land land, Map<String, LegPlanner.Leg> cache) {
        return RoutePlan.plan(land, List.of(RoutePlan.Point.at(0, 0), RoutePlan.Point.at(60, 24), RoutePlan.Point.at(200, 0)),
                RoadClass.GRAVEL, RouteStyle.DIRECT, RoadRules.DEFAULT, EarthRules.DEFAULT, SyntheticLand.US, cache, () -> false, done -> {
                }, 1.0);
    }

    @Test
    void legsPlannedBeforeGiveTheSamePlan() {
        // A river 16 wide with banks of the height of the land, a hill before it.
        SyntheticLand land = new SyntheticLand((x, z) -> x >= 120 && x < 136 ? LandColumn.water(58, 64)
                : SyntheticLand.land(64 + Math.max(0, 6 - (int) Math.hypot(x - 60, z - 24) / 3)));
        Map<String, LegPlanner.Leg> cache = new HashMap<>();
        RoutePlan.Result first = plan(new Fresh(land), cache);
        assertTrue(first.found(), "no plan: " + first.failure());
        assertEquals(2, cache.size(), "the legs were not kept");
        RoutePlan.Result again = plan(new Fresh(land), cache);
        assertTrue(again.found(), "no plan the second time: " + again.failure());
        assertEquals(first.cells(), again.cells());
        RoadEstimate a = first.estimate();
        RoadEstimate b = again.estimate();
        assertTrue(a.bridgeCells() > 0, "the river has no bridge");
        assertEquals(a.bridgeCells(), b.bridgeCells());
        assertEquals(a.fillCells(), b.fillCells());
        assertEquals(a.cutCells(), b.cutCells());
        assertEquals(a.soil(), b.soil());
        assertEquals(a.fill(), b.fill());
        assertEquals(a.pierBlocks(), b.pierBlocks());
        assertArrayEquals(a.materials(), b.materials());
        assertEquals(a.builders(), b.builders(), 1e-9);
        assertEquals(a.miners(), b.miners(), 1e-9);
        assertEquals(first.earthTotal(), again.earthTotal(), 1e-9);
        assertEquals(first.surveys(), again.surveys());
    }
}
