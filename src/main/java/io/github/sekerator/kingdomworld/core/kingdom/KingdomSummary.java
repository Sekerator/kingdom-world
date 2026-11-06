package io.github.sekerator.kingdomworld.core.kingdom;

import java.util.Objects;
import java.util.UUID;

/**
 * Public, lightweight view of a kingdom that may be shown to any player.
 */
public record KingdomSummary(UUID id, String name, int color, boolean hasRuler) {
    public KingdomSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }

    public static KingdomSummary of(Kingdom kingdom) {
        return new KingdomSummary(kingdom.id(), kingdom.name(), kingdom.color(), kingdom.rulerId() != null);
    }
}
