package io.github.sekerator.kingdomworld.core.construction;

import java.util.Locale;
import java.util.Optional;

/**
 * A horizontal direction, the way the game counts them: north is towards negative z, east towards positive x.
 */
public enum Facing {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    private final int dx;
    private final int dz;

    Facing(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    /**
     * The direction a quarter turn clockwise (seen from above).
     */
    public Facing clockwise() {
        return values()[(ordinal() + 1) % 4];
    }

    public Facing opposite() {
        return values()[(ordinal() + 2) % 4];
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Facing> byId(String id) {
        for (Facing facing : values()) {
            if (facing.id().equals(id)) {
                return Optional.of(facing);
            }
        }
        return Optional.empty();
    }

    /**
     * The direction of a step, or empty if the step is not one block along an axis.
     */
    public static Optional<Facing> of(int dx, int dz) {
        for (Facing facing : values()) {
            if (facing.dx == dx && facing.dz == dz) {
                return Optional.of(facing);
            }
        }
        return Optional.empty();
    }
}
