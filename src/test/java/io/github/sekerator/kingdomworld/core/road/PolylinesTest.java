package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolylinesTest {
    @Test
    void aStraightRoadKeepsItsEnds() {
        int[] xs = new int[500];
        int[] zs = new int[500];
        for (int i = 0; i < 500; i++) {
            xs[i] = i;
            zs[i] = i / 2;
        }
        assertArrayEquals(new int[]{0, 499}, Polylines.keep(xs, zs, 3));
    }

    @Test
    void noBlockOfAWindingRoadStraysFurtherThanTheTolerance() {
        int n = 2_000;
        int[] xs = new int[n];
        int[] zs = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = i;
            zs[i] = (int) Math.round(40 * Math.sin(i / 60.0));
        }
        int[] kept = Polylines.keep(xs, zs, 3);
        assertTrue(kept.length < n / 10, "points kept: " + kept.length);
        for (int k = 0; k + 1 < kept.length; k++) {
            int a = kept[k];
            int b = kept[k + 1];
            for (int i = a; i <= b; i++) {
                assertTrue(CorridorTerrain.distance2(xs[i], zs[i], xs[a], zs[a], xs[b], zs[b]) <= 9.0 + 1e-9, "block " + i);
            }
        }
    }

    @Test
    void shortLinesStayWhole() {
        assertArrayEquals(new int[]{0, 1}, Polylines.keep(new int[]{0, 5}, new int[]{0, 5}, 3));
        assertArrayEquals(new int[]{0}, Polylines.keep(new int[]{7}, new int[]{7}, 3));
    }
}
