package io.github.sekerator.kingdomworld.core.citizen;

/**
 * Where a frightened citizen runs: to a refuge (its home, a guard) only when the way there does not lead past whoever
 * frightened it, otherwise straight away. Positions are in blocks on the ground plane.
 */
public final class Flight {
    /** A refuge this near the threat is none. */
    public static final double UNSAFE_REFUGE = 4.0;
    /** The way counts as leading past the threat when it lies within this angle of it (its cosine). */
    private static final double PAST_COSINE = 0.5;

    private Flight() {
    }

    /**
     * Whether a body at {@code (x, z)} may run to a refuge: the refuge is not next to the threat, and the threat does not
     * stand in the way to it.
     */
    public static boolean leadsAway(double x, double z, double threatX, double threatZ, double refugeX, double refugeZ) {
        double toRefugeX = refugeX - x;
        double toRefugeZ = refugeZ - z;
        double toThreatX = threatX - x;
        double toThreatZ = threatZ - z;
        double refuge = Math.sqrt(toRefugeX * toRefugeX + toRefugeZ * toRefugeZ);
        double threat = Math.sqrt(toThreatX * toThreatX + toThreatZ * toThreatZ);
        double between = Math.sqrt((refugeX - threatX) * (refugeX - threatX) + (refugeZ - threatZ) * (refugeZ - threatZ));
        if (between < UNSAFE_REFUGE) {
            return false;
        }
        if (refuge < 1.0 || threat < 1.0E-3) {
            return true;
        }
        if (threat >= refuge) {
            // The refuge is nearer than the threat: the body is there before it reaches the threat.
            return true;
        }
        double cosine = (toRefugeX * toThreatX + toRefugeZ * toThreatZ) / (refuge * threat);
        return cosine < PAST_COSINE;
    }

    /**
     * A point {@code distance} blocks from the body straight away from the threat; when both stand in one place, to the east.
     *
     * @return {x, z}
     */
    public static double[] away(double x, double z, double threatX, double threatZ, double distance) {
        double dx = x - threatX;
        double dz = z - threatZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3) {
            return new double[]{x + distance, z};
        }
        return new double[]{x + dx / length * distance, z + dz / length * distance};
    }
}
