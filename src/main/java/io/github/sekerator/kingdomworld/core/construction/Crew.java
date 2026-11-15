package io.github.sekerator.kingdomworld.core.construction;

import java.util.List;

/**
 * How builders working on one construction at the same time share out the steps of the tier at hand (see
 * {@link BuildPlan#tier}). A builder alone takes them in the order of the plan. With others at work it takes a step at least
 * {@value #SPREAD} blocks from every place where another builder works, the nearest such step to itself, so that each keeps to
 * its own stretch of the layer: they do not walk to the same place, stand on each other's blocks or climb the same column.
 * When no step is that far, the one farthest from the others.
 */
public final class Crew {
    /** Blocks between the steps of two builders, along the ground. */
    public static final int SPREAD = 4;

    private Crew() {
    }

    /**
     * The step a builder takes.
     *
     * @param options the steps it may take now, in the order of the plan; not empty
     * @param others  the places {x, z} where the other builders work
     * @param x       where the builder stands
     * @param z       where the builder stands
     * @return the index of the step in {@code options}
     */
    public static int pick(List<BuildPlan.Step> options, List<int[]> others, double x, double z) {
        if (others.isEmpty() || options.size() == 1) {
            return 0;
        }
        int nearest = -1;
        double nearestDistance = Double.MAX_VALUE;
        int farthest = 0;
        long farthestGap = -1;
        for (int i = 0; i < options.size(); i++) {
            BuildPlan.Step step = options.get(i);
            long gap = Long.MAX_VALUE;
            for (int[] other : others) {
                long dx = step.x() - other[0];
                long dz = step.z() - other[1];
                gap = Math.min(gap, dx * dx + dz * dz);
            }
            if (gap > farthestGap) {
                farthest = i;
                farthestGap = gap;
            }
            if (gap >= (long) SPREAD * SPREAD) {
                double mx = step.x() + 0.5 - x;
                double mz = step.z() + 0.5 - z;
                double distance = mx * mx + mz * mz;
                if (distance < nearestDistance) {
                    nearest = i;
                    nearestDistance = distance;
                }
            }
        }
        return nearest >= 0 ? nearest : farthest;
    }
}
