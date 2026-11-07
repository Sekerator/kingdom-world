package io.github.sekerator.kingdomworld.core.road;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * One block along the middle of a road.
 *
 * @param y     the height of the road there: the ground block that becomes road, or the deck of a bridge or embankment
 * @param depth blocks of embankment under the deck, for {@link Kind#FILL}
 * @param owner the kingdom whose land it was when the route was found, or {@code null}; not saved with a road
 */
public record RouteCell(int x, int y, int z, Kind kind, int depth, UUID owner) {

    public enum Kind {
        /** The road on the ground. */
        GROUND,
        /** A bridge over water or a deep gap. */
        BRIDGE,
        /** An embankment over a shallow gap. */
        FILL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public RouteCell {
        Objects.requireNonNull(kind, "kind");
        depth = kind == Kind.FILL ? Math.max(0, depth) : 0;
    }

    public RouteCell withoutOwner() {
        return owner == null ? this : new RouteCell(x, y, z, kind, depth, null);
    }

    public boolean raised() {
        return kind != Kind.GROUND;
    }
}
