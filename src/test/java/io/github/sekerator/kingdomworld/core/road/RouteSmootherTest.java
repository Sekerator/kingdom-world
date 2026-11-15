package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Straightening a way keeps it side by side, never takes it over a building and keeps the points where they are.
 */
class RouteSmootherTest {

    @Test
    void aStraightenedWayNeverCrossesABuilding() {
        SyntheticLand land = new SyntheticLand((x, z) -> x >= 20 && x < 30 && z >= -5 && z < 5 ? LandColumn.built(70) : SyntheticLand.land(64));
        Set<Long> chunks = new HashSet<>();
        for (int cx = -1; cx <= 4; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                chunks.add(LandSnapshot.chunk(cx, cz));
            }
        }
        CorridorLand corridor = new CorridorLand(chunks, land, land::owner, SyntheticLand.US, 2, -64, 320, null);
        CellCosts costs = new CellCosts(corridor, RoadClass.GRAVEL, RouteStyle.TERRAIN, RoadRules.DEFAULT, EarthRules.DEFAULT);
        // A way that steps round the house a block at a time, zig-zagging.
        List<int[]> path = new ArrayList<>();
        int x = 0;
        int z = 0;
        path.add(new int[]{x, 64, z, 0});
        while (z > -8) {
            z--;
            path.add(new int[]{x, 64, z, 0});
            x++;
            path.add(new int[]{x, 64, z, 0});
        }
        while (x < 40) {
            x++;
            path.add(new int[]{x, 64, z, 0});
        }
        while (z < 0) {
            z++;
            path.add(new int[]{x, 64, z, 0});
        }
        List<int[]> smooth = RouteSmoother.smooth(path, costs, Set.of(0, path.size() - 1));
        assertTrue(smooth.size() <= path.size(), "the way did not get shorter");
        assertEquals(path.get(0)[0], smooth.get(0)[0]);
        assertEquals(path.get(path.size() - 1)[2], smooth.get(smooth.size() - 1)[2]);
        for (int i = 0; i < smooth.size(); i++) {
            int[] cell = smooth.get(i);
            assertFalse(cell[0] >= 20 && cell[0] < 30 && cell[2] >= -5 && cell[2] < 5, "over the house at " + cell[0] + " " + cell[2]);
            if (i > 0) {
                int[] before = smooth.get(i - 1);
                assertEquals(1, Math.abs(cell[0] - before[0]) + Math.abs(cell[2] - before[2]), "not side by side at " + i);
            }
        }
        // A point in the middle stays.
        int middle = path.size() / 2;
        List<int[]> held = RouteSmoother.smooth(path, costs, Set.of(0, middle, path.size() - 1));
        assertTrue(held.stream().anyMatch(cell -> cell[0] == path.get(middle)[0] && cell[2] == path.get(middle)[2]), "the point moved");
    }
}
