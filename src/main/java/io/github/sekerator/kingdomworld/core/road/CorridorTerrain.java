package io.github.sekerator.kingdomworld.core.road;

import java.util.Objects;

/**
 * The land along a rough line: the columns of another terrain within a width of the line, every column farther away blocked,
 * so that a search for a road between settlements looks only along the way the plan drew and not over the whole country.
 */
public final class CorridorTerrain implements Terrain {
    /** Blocks on either side of the line a road may stray. */
    public static final int WIDTH = 64;
    private static final Column BLOCKED = new Column(0, Surface.BLOCKED, null);

    private final Terrain terrain;
    private final int[] xs;
    private final int[] zs;
    private final long width2;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    /**
     * @param xs    the blocks of the points of the line along x
     * @param zs    the blocks of the points along z
     * @param width blocks on either side of the line a road may stray
     */
    public CorridorTerrain(Terrain terrain, int[] xs, int[] zs, int width) {
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        if (xs.length != zs.length || xs.length < 1) {
            throw new IllegalArgumentException("A corridor needs a point at least");
        }
        this.xs = xs.clone();
        this.zs = zs.clone();
        this.width2 = (long) width * width;
        int x0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z0 = Integer.MAX_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (int i = 0; i < xs.length; i++) {
            x0 = Math.min(x0, xs[i]);
            x1 = Math.max(x1, xs[i]);
            z0 = Math.min(z0, zs[i]);
            z1 = Math.max(z1, zs[i]);
        }
        minX = x0 - width;
        maxX = x1 + width;
        minZ = z0 - width;
        maxZ = z1 + width;
    }

    @Override
    public Column column(int x, int z) {
        if (!inside(x, z)) {
            return BLOCKED;
        }
        return terrain.column(x, z);
    }

    /**
     * Whether a block lies within the width of the line.
     */
    public boolean inside(int x, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }
        if (xs.length == 1) {
            return distance2(x, z, xs[0], zs[0], xs[0], zs[0]) <= width2;
        }
        for (int i = 0; i + 1 < xs.length; i++) {
            if (distance2(x, z, xs[i], zs[i], xs[i + 1], zs[i + 1]) <= width2) {
                return true;
            }
        }
        return false;
    }

    /**
     * The square of the distance of a point from a stretch of the line.
     */
    static double distance2(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double length2 = dx * dx + dz * dz;
        double t = length2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / length2));
        double cx = ax + t * dx - px;
        double cz = az + t * dz - pz;
        return cx * cx + cz * cz;
    }
}
