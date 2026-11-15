package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coarse stage goes round a lake, takes a pass through a ridge and keeps off chunks built over.
 */
class CoarseRouteTest {
    private static final CoarseRoute.Costs GRAVEL = CoarseRoute.Costs.of(RoadClass.GRAVEL, RouteStyle.TERRAIN, RoadRules.DEFAULT,
            EarthRules.DEFAULT);

    private static CoarseRoute.Field flat(int width, int depth) {
        CoarseRoute.Field field = new CoarseRoute.Field(0, 0, width, depth);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                field.set(x, z, 64, 63, 65, 0, 0, 0, false, false);
            }
        }
        return field;
    }

    private static boolean passes(List<int[]> path, int x, int z) {
        return path.stream().anyMatch(at -> at[0] == x && at[1] == z);
    }

    @Test
    void theCoarseWayGoesRoundALake() {
        CoarseRoute.Field field = flat(20, 11);
        for (int x = 8; x <= 12; x++) {
            for (int z = 2; z <= 8; z++) {
                field.set(x, z, 63, 50, 63, 1.0, 12, 0, false, false);
            }
        }
        CoarseRoute.Result result = CoarseRoute.solve(field, List.of(new int[]{19, 5}), new int[]{0, 5}, GRAVEL);
        assertTrue(result.found());
        for (int[] at : result.path()) {
            assertFalse(at[0] >= 8 && at[0] <= 12 && at[1] >= 2 && at[1] <= 8, "the way crosses the lake at " + at[0] + " " + at[1]);
        }
        Set<Long> corridor = CoarseRoute.corridor(result, GRAVEL, 0, List.of(new int[]{0, 5}, new int[]{19, 5}));
        assertTrue(corridor.contains(LandSnapshot.chunk(0, 5)) && corridor.contains(LandSnapshot.chunk(19, 5)));
        assertTrue(Double.isFinite(result.costAt(8.0, 88.0)), "the field has a cost at the start");
        assertTrue(result.costAt(8.0, 88.0) > result.costAt(300.0, 88.0), "the cost falls towards the goal");
    }

    @Test
    void theCoarseWayTakesAPassThroughARidge() {
        CoarseRoute.Field field = flat(21, 9);
        for (int z = 0; z < 9; z++) {
            if (z != 4) {
                field.set(10, z, 140, 120, 160, 0, 0, 0, false, false);
                field.set(9, z, 100, 64, 140, 0, 0, 0, false, false);
                field.set(11, z, 100, 64, 140, 0, 0, 0, false, false);
            }
        }
        CoarseRoute.Result result = CoarseRoute.solve(field, List.of(new int[]{20, 1}), new int[]{0, 7}, GRAVEL);
        assertTrue(result.found());
        assertTrue(passes(result.path(), 10, 4), "the way does not take the pass");
    }

    @Test
    void theCoarseWayKeepsOffBuiltChunksAndStopsAtTheVoid() {
        CoarseRoute.Field field = flat(15, 9);
        for (int z = 0; z < 9; z++) {
            if (z != 5) {
                field.set(7, z, 64, 63, 65, 0, 0, 1.0, false, false);
            }
        }
        CoarseRoute.Result result = CoarseRoute.solve(field, List.of(new int[]{14, 2}), new int[]{0, 2}, GRAVEL);
        assertTrue(result.found());
        assertTrue(passes(result.path(), 7, 5), "the way crosses the built chunks rather than the gap in them");
        CoarseRoute.Field islands = flat(10, 3);
        for (int z = 0; z < 3; z++) {
            islands.set(5, z, 0, 0, 0, 0, 0, 0, false, true);
        }
        assertFalse(CoarseRoute.solve(islands, List.of(new int[]{9, 1}), new int[]{0, 1}, GRAVEL).found(), "a road across the void");
    }

    @Test
    void aTileGuessesOrKnowsItsColumnsAndTheSnapshotLaysWhatStandsOverThem() {
        LandColumn[] columns = new LandColumn[LandTile.SIZE];
        for (int i = 0; i < LandTile.SIZE; i++) {
            columns[i] = LandColumn.land(70 + i / 16, 4).withCave(60, 55, i == 5).withLogs(i == 7 ? 12 : 0);
        }
        LandTile tile = new LandTile(2, -1, null, false, columns);
        LandColumn back = tile.column(32 + 0, -16 + 5);
        assertTrue(back.caveLiquid() && back.caveTop() == 60 && back.soil() == 4);
        assertTrue(tile.column(32, -16 + 7).logs() == 12);
        assertTrue(tile.ground(32 + 3, -16) == 73);
        LandTile guessed = LandTile.guessed(0, 0, null, new int[LandTile.SIZE], 63);
        assertTrue(guessed.guessed() && guessed.water(1, 1), "land guessed under the level of the sea is water");
        java.util.UUID us = new java.util.UUID(1, 1);
        LandSnapshot snapshot = new LandSnapshot(us, java.util.Map.of(), List.of(new int[]{40, -10, 42, -8}),
                java.util.Map.of(LandSnapshot.column(33, -15), LandSnapshot.road(9, 71, true)),
                java.util.Map.of(LandSnapshot.column(34, -15), LandSnapshot.wall(72, LandColumn.WALL)), new int[]{-100, -100, 100, 100},
                new int[]{-64, 320, 63});
        LandGrid grid = new LandGrid(snapshot);
        grid.put(tile);
        assertTrue(grid.at(33, -15).has(LandColumn.ROAD) && grid.at(33, -15).has(LandColumn.OWN_ROAD) && grid.at(33, -15).roadY() == 71);
        assertTrue(grid.at(34, -15).has(LandColumn.WALL) && grid.at(34, -15).roadY() == 72, "the floor of the wall");
        assertTrue(grid.at(39, -11).has(LandColumn.BUILT), "a frame widened by a block");
        assertFalse(grid.at(38, -11).has(LandColumn.BUILT));
        assertTrue(grid.at(100, 0).has(LandColumn.BORDER));
        assertTrue(grid.at(48, -15) == null, "a chunk not read");
    }
}
