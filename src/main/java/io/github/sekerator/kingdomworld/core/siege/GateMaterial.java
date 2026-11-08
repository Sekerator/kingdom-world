package io.github.sekerator.kingdomworld.core.siege;

import java.util.Locale;
import java.util.Optional;

/**
 * What the leaves of a gate are made of: planks for a palisade, an iron portcullis for a stone wall.
 */
public enum GateMaterial {
    WOOD,
    IRON;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<GateMaterial> byId(String id) {
        for (GateMaterial value : values()) {
            if (value.id().equals(id)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
