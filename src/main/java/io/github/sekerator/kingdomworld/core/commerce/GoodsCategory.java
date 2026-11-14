package io.github.sekerator.kingdomworld.core.commerce;

import java.util.Locale;
import java.util.Optional;

/**
 * The kinds of goods the townspeople buy, each a share of their basket. The items of a kind are given by the item tag
 * {@code kingdomworld:goods/<id>}, which data packs extend. Saved by id; new kinds come only at the end.
 */
public enum GoodsCategory {
    FOOD,
    CLOTHING,
    TOOLS,
    HOUSEHOLD,
    LUXURY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
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
