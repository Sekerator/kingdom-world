package io.github.sekerator.kingdomworld.core.road;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * One block along the middle of a road.
 *
 * @param y     the height of the road there: the ground block that becomes road, or the deck of a bridge or embankment
 * @param depth blocks of embankment under the deck, for {@link Kind#FILL}; for the other raised and dug kinds only shown: the height
 *              of the pier of a bridge, the depth of a cutting at its middle, the cover over a tunnel
 * @param owner the kingdom whose land it was when the route was found, or {@code null}; not saved with a road
 */
public record RouteCell(int x, int y, int z, Kind kind, int depth, UUID owner) {

    public enum Kind {
        /** The road on the ground. */
        GROUND,
        /** A bridge over water or a deep gap. */
        BRIDGE,
        /** An embankment over a shallow gap. */
        FILL,
        /** A cutting: the land above the road dug away, open to the sky. */
        CUT,
        /** A tunnel through the land, its roof left standing. */
        TUNNEL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public RouteCell {
        Objects.requireNonNull(kind, "kind");
        depth = kind == Kind.GROUND ? 0 : Math.max(0, depth);
    }

    public RouteCell withoutOwner() {
        return owner == null ? this : new RouteCell(x, y, z, kind, depth, null);
    }

    /**
     * Whether the road is a bridge or an embankment there.
     */
    public boolean raised() {
        return kind == Kind.FILL || kind == Kind.BRIDGE;
    }

    /**
     * Whether the land was dug away for the road there: a cutting or a tunnel.
     */
    public boolean dug() {
        return kind == Kind.CUT || kind == Kind.TUNNEL;
    }
}
