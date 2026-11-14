package io.github.sekerator.kingdomworld.core.construction;

import java.util.ArrayList;
import java.util.List;

/**
 * The points a building ordered for a city is tried at: rings around the town hall (or the throne), a step apart, the nearest
 * ring first and along each ring from its north-west corner, so that the town grows around its middle.
 */
public final class PlotRings {
    /** Blocks between two rings, and between two points of a ring. */
    public static final int STEP = 4;

    private PlotRings() {
    }

    /**
     * The points of the rings around a middle up to a radius, along x and z, the nearest ring first.
     */
    public static List<int[]> around(int centerX, int centerZ, int radius) {
        List<int[]> points = new ArrayList<>();
        for (int r = STEP; r <= radius; r += STEP) {
            points.addAll(ring(centerX, centerZ, r));
        }
        return points;
    }

    /**
     * The points of one ring: the square at a distance from a middle, a point every {@value #STEP} blocks.
     */
    public static List<int[]> ring(int centerX, int centerZ, int r) {
        List<int[]> points = new ArrayList<>();
        for (int d = -r; d < r; d += STEP) {
            points.add(new int[]{centerX + d, centerZ - r});
        }
        for (int d = -r; d < r; d += STEP) {
            points.add(new int[]{centerX + r, centerZ + d});
        }
        for (int d = r; d > -r; d -= STEP) {
            points.add(new int[]{centerX + d, centerZ + r});
        }
        for (int d = r; d > -r; d -= STEP) {
            points.add(new int[]{centerX - r, centerZ + d});
        }
        return points;
    }

    /**
     * Whether a box lies inside a square of a city: a city post reaches every construction whose middle lies in its square, and a
     * building ordered for a city stands wholly inside it.
     */
    public static boolean inside(Box box, int centerX, int centerZ, int radius) {
        return box.minX() >= centerX - radius && box.maxX() <= centerX + radius && box.minZ() >= centerZ - radius && box.maxZ() <= centerZ + radius;
    }
}
