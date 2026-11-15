package io.github.sekerator.kingdomworld.core.road;

import java.util.Locale;
import java.util.Optional;

/**
 * How straight the king wants a road: along the land, where it winds round what is steep and costs little earthwork; in
 * moderation; or straight on, through hills in cuttings and tunnels. The search weighs a block of length against the work of
 * the earthworks by it, see {@link EarthRules#lambda}.
 */
public enum RouteStyle {
    /** Along the land: the cheapest road within the grade of its class. */
    TERRAIN,
    /** A shorter road for some more earthwork. */
    MODERATE,
    /** As straight as the land lets: cuttings, tunnels and bridges for a short road. */
    DIRECT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public RouteStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Optional<RouteStyle> byId(String id) {
        for (RouteStyle value : values()) {
            if (value.id().equals(id)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
