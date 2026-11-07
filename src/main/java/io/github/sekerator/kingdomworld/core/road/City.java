package io.github.sekerator.kingdomworld.core.road;

import java.util.Objects;
import java.util.UUID;

/**
 * A city of a kingdom: a town hall on its land with a name. The capital is a city too, but it lives with the territory
 * of the kingdom and is not kept here.
 *
 * @param founded game time the town hall was placed
 */
public record City(long id, UUID kingdomId, String name, String dimension, int x, int y, int z, long founded) {
    /** The longest name of a city. */
    public static final int MAX_NAME = 32;

    public City {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dimension, "dimension");
        name = name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME || dimension.isEmpty()) {
            throw new IllegalArgumentException("City out of range");
        }
    }

    public City withName(String value) {
        return new City(id, kingdomId, value, dimension, x, y, z, founded);
    }

    public double distanceSqr(int otherX, int otherZ) {
        double dx = x - otherX;
        double dz = z - otherZ;
        return dx * dx + dz * dz;
    }
}
