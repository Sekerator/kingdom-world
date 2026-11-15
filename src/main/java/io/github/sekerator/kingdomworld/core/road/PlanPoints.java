package io.github.sekerator.kingdomworld.core.road;

/**
 * Where the points of a plan of a road lie: a free end on a building moves to the nearest column a road may cross within
 * {@value #REACH} blocks, one in water to the nearest shore; a point between is held to one column, the nearest a road may cross
 * within two blocks (on a tie the one nearer the line between its neighbours), else within {@value #REACH}.
 */
public final class PlanPoints {
    /** Blocks a point is moved at most. */
    public static final int REACH = 8;
    /** Blocks a point between is first looked round. */
    public static final int NEAR = 2;

    /**
     * Why a point cannot be held.
     */
    public enum Failure {
        NONE,
        ON_BUILDING,
        IN_WATER
    }

    /**
     * A point held to a column, or why not.
     */
    public record Snap(int x, int z, Failure failure) {
        public boolean ok() {
            return failure == Failure.NONE;
        }
    }

    private PlanPoints() {
    }

    /**
     * Whether a road may cross a column: not built over, beyond the border, without ground, in a foreign wall, or lava.
     */
    static boolean open(LandColumn column) {
        return column != null && !column.closed() && !column.has(LandColumn.LAVA);
    }

    /**
     * Holds a point between to a column.
     *
     * @param before the point before it, x and z
     * @param after  the point after it
     */
    public static Snap waypoint(RoadSurvey.Land land, int x, int z, int[] before, int[] after) {
        Snap near = nearest(land, x, z, NEAR, before, after, false);
        if (near != null) {
            return near;
        }
        Snap far = nearest(land, x, z, REACH, before, after, false);
        return far != null ? far : new Snap(x, z, Failure.ON_BUILDING);
    }

    /**
     * Holds a free end: on land a road may cross it stays, on a building it moves to the nearest such column, in water to the
     * nearest shore.
     */
    public static Snap end(RoadSurvey.Land land, int x, int z) {
        LandColumn here = land.at(x, z);
        if (open(here) && !here.water()) {
            return new Snap(x, z, Failure.NONE);
        }
        boolean wet = here != null && here.water();
        Snap moved = nearest(land, x, z, REACH, null, null, true);
        if (moved != null) {
            return moved;
        }
        return new Snap(x, z, wet ? Failure.IN_WATER : Failure.ON_BUILDING);
    }

    private static Snap nearest(RoadSurvey.Land land, int x, int z, int reach, int[] before, int[] after, boolean dry) {
        Snap best = null;
        int bestDistance = Integer.MAX_VALUE;
        double bestLine = Double.POSITIVE_INFINITY;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                LandColumn column = land.at(x + dx, z + dz);
                if (!open(column) || dry && column.water()) {
                    continue;
                }
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                double line = before != null && after != null ? toLine(x + dx, z + dz, before, after) : dx * dx + dz * dz;
                if (distance < bestDistance || distance == bestDistance && line < bestLine) {
                    best = new Snap(x + dx, z + dz, Failure.NONE);
                    bestDistance = distance;
                    bestLine = line;
                }
            }
        }
        return best;
    }

    /**
     * The distance of a column from the line between two points.
     */
    static double toLine(int x, int z, int[] a, int[] b) {
        double dx = b[0] - a[0];
        double dz = b[1] - a[1];
        double length = Math.hypot(dx, dz);
        if (length < 1e-9) {
            return Math.hypot(x - a[0], z - a[1]);
        }
        return Math.abs(dz * (x - a[0]) - dx * (z - a[1])) / length;
    }
}
