package io.github.sekerator.kingdomworld.core.construction;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The edges of earthworks next to walls, gates, buildings and roads: a smoothed edge meets them flat at the height of the
 * work, and natural land around still slopes one to one.
 */
class EarthworkEdgeTest {
    private static final int SIZE = 9;
    private static final int WIDTH = SIZE + 2;

    private static int index(int dx, int dz) {
        return (dz + 1) * WIDTH + dx + 1;
    }

    /**
     * Flat ground at a height, with the north row of the ring at another.
     */
    private static int[] ground(int flat, int north) {
        int[] ground = new int[WIDTH * WIDTH];
        Arrays.fill(ground, flat);
        for (int dx = -1; dx <= SIZE; dx++) {
            ground[index(dx, -1)] = north;
        }
        return ground;
    }

    private static Earthwork work(int[] ground, int target, boolean smooth) {
        int[] top = new int[SIZE * SIZE];
        Arrays.fill(top, 64);
        return new Earthwork(0, 0, SIZE, SIZE, Earthwork.Mode.LEVEL, target, smooth, ground, top, Map.of(), Map.of());
    }

    @Test
    void aWallOnTheRingMeetsTheFloorFlat() {
        // A stone wall on the north row of the ring, as the old survey read it: its top at 69.
        int[] ground = ground(64, 69);
        Earthwork before = work(ground, 64, true);
        assertEquals(68, before.surface(4, 0), "the old survey ramps up against the wall");
        assertEquals(65, before.surface(4, 3));
        boolean[] built = new boolean[ground.length];
        for (int dx = -1; dx <= SIZE; dx++) {
            built[index(dx, -1)] = true;
        }
        Earthwork after = work(Earthwork.meetStructures(SIZE, SIZE, 64, ground, built), 64, true);
        for (int dz = 0; dz < SIZE; dz++) {
            for (int dx = 0; dx < SIZE; dx++) {
                assertEquals(64, after.surface(dx, dz), "a step at " + dx + " " + dz);
            }
        }
        assertEquals(0, after.fills());
    }

    @Test
    void aWallOnTheEdgeRowStopsTheSlopeFromBeyondIt() {
        // The wall stands on the north row of the plot; the land outside it lies at 70.
        int[] ground = ground(64, 70);
        boolean[] built = new boolean[ground.length];
        for (int dx = 0; dx < SIZE; dx++) {
            built[index(dx, 0)] = true;
        }
        int[] met = Earthwork.meetStructures(SIZE, SIZE, 64, ground, built);
        for (int dx = 0; dx < SIZE; dx++) {
            assertEquals(64, met[index(dx, -1)], "the ring beyond the wall at " + dx);
        }
        Earthwork after = work(met, 64, true);
        for (int dx = 0; dx < SIZE; dx++) {
            assertEquals(64, after.surface(dx, 1));
        }
    }

    @Test
    void naturalLandAroundStillSlopes() {
        int[] ground = ground(60, 67);
        boolean[] built = new boolean[ground.length];
        int[] met = Earthwork.meetStructures(SIZE, SIZE, 60, ground, built);
        assertArrayEquals(ground, met, "no structure: nothing changes");
        Earthwork slope = work(met, 60, true);
        assertEquals(66, slope.surface(4, 0), "the edge keeps within a block of the land outside");
        assertEquals(65, slope.surface(4, 1));
        assertEquals(64, slope.surface(4, 2));
    }

    @Test
    void meetStructuresTouchesOnlyTheRing() {
        int[] ground = ground(64, 70);
        for (int i = 0; i < ground.length; i++) {
            ground[i] += i % 3;
        }
        boolean[] built = new boolean[ground.length];
        Arrays.fill(built, true);
        int[] met = Earthwork.meetStructures(SIZE, SIZE, 50, ground, built);
        for (int dz = -1; dz <= SIZE; dz++) {
            for (int dx = -1; dx <= SIZE; dx++) {
                boolean ring = dx == -1 || dz == -1 || dx == SIZE || dz == SIZE;
                assertEquals(ring ? 50 : ground[index(dx, dz)], met[index(dx, dz)], "at " + dx + " " + dz);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Earthwork.meetStructures(SIZE, SIZE, 50, ground, new boolean[3]));
        assertEquals(work(met, 50, true).fingerprint(), work(Earthwork.meetStructures(SIZE, SIZE, 50, ground, built), 50, true).fingerprint());
    }

    @Test
    void withoutSmoothingTheFloorIsFlat() {
        Earthwork flat = work(ground(64, 75), 64, false);
        for (int dz = 0; dz < SIZE; dz++) {
            for (int dx = 0; dx < SIZE; dx++) {
                assertEquals(64, flat.surface(dx, dz));
            }
        }
    }
}
