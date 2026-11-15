package io.github.sekerator.kingdomworld.core.commerce;

import java.util.Locale;
import java.util.Optional;

/**
 * The kinds of goods the townspeople buy, each a share of their basket, and building materials, which they never buy: the crown,
 * the heads of cities and players do. The items of a kind are given by the item tag {@code kingdomworld:goods/<id>}, which data
 * packs extend. Saved by id; new kinds come only at the end.
 */
public enum GoodsCategory {
    FOOD,
    CLOTHING,
    TOOLS,
    HOUSEHOLD,
    LUXURY,
    /** Planks, slabs, stairs, doors, fences, ladders, bricks and glass: for the crown, the heads of cities and players. */
    MATERIALS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether the townspeople buy goods of the kind for their basket.
     */
    public boolean basket() {
        return this != MATERIALS;
    }

    public static Optional<GoodsCategory> byId(String id) {
        for (GoodsCategory category : values()) {
            if (category.id().equals(id)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
