package io.github.sekerator.kingdomworld.core.tactics;

/**
 * A place on the field of a fight, in blocks.
 */
public record Spot(double x, double y, double z) {
    public double distanceTo(Spot other) {
        return Math.sqrt(distanceSq(other));
    }

    public double distanceSq(Spot other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * The distance on the ground, without the height.
     */
    public double flatDistanceTo(Spot other) {
        double dx = x - other.x;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * The angle of the way from here to another place on the ground, in radians (0 along +x, growing towards +z).
     */
    public double angleTo(Spot other) {
        return Math.atan2(other.z - z, other.x - x);
    }

    /**
     * The place at a distance from here on the ground in a direction, at the same height.
     */
    public Spot toward(double angle, double distance) {
        return new Spot(x + Math.cos(angle) * distance, y, z + Math.sin(angle) * distance);
    }

    public Spot withY(double newY) {
        return new Spot(x, newY, z);
    }

    /**
     * The middle of some places, or {@code fallback} if there are none.
     */
    public static Spot middle(Iterable<Spot> spots, Spot fallback) {
        double x = 0;
        double y = 0;
        double z = 0;
        int count = 0;
        for (Spot spot : spots) {
            x += spot.x;
            y += spot.y;
            z += spot.z;
            count++;
        }
        return count == 0 ? fallback : new Spot(x / count, y / count, z / count);
    }
}
