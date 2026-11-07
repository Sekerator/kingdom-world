package io.github.sekerator.kingdomworld.core.road;

import java.util.Locale;
import java.util.Optional;

/**
 * The class of a road: a path trodden into the grass, a gravel road or a paved one. Better roads cost more and take longer
 * to lay, wear more slowly and are faster to travel.
 */
public enum RoadClass {
    /** A path: the grass turned into a path, one block wide. */
    PATH(1),
    /** A gravel road, three blocks wide. */
    GRAVEL(3),
    /** A paved road of stone, three blocks wide. */
    PAVED(3);

    private final int width;

    RoadClass(int width) {
        this.width = width;
    }

    /**
     * Blocks across the road.
     */
    public int width() {
        return width;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public RoadClass next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Optional<RoadClass> byId(String id) {
        for (RoadClass value : values()) {
            if (value.id().equals(id)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
