package io.github.sekerator.kingdomworld.core.road;

import java.util.Objects;
import java.util.UUID;

/**
 * The lay of the land a route is searched over, column by column. The server reads it from the world; tests make it up.
 */
@FunctionalInterface
public interface Terrain {

    /**
     * What the top of a column is.
     */
    enum Surface {
        /** Solid ground a road can be laid on. */
        LAND,
        /** Water: a road crosses it on a bridge. */
        WATER,
        /** Lava, fire, cactus, magma and the like: a road keeps away. */
        DANGER,
        /** Something a road must not break: a protected block, a container, the edge of the world. */
        BLOCKED
    }

    /**
     * The top of a column.
     *
     * @param y     the height of the top block: the ground, or the surface of the water
     * @param owner the kingdom whose land it is, or {@code null} for wild land
     */
    record Column(int y, Surface surface, UUID owner) {
        public Column {
            Objects.requireNonNull(surface, "surface");
        }

        public static Column land(int y) {
            return new Column(y, Surface.LAND, null);
        }

        public static Column water(int y) {
            return new Column(y, Surface.WATER, null);
        }
    }

    /**
     * The column at a block, or {@code null} if it cannot be looked at yet (its chunk is not loaded and this tick may load
     * no more); the search waits and asks again.
     */
    Column column(int x, int z);
}
