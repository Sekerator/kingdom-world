package io.github.sekerator.kingdomworld.core.companion;

import java.util.Objects;

/**
 * One change of loyalty: why, by how much (summed while the same reason repeats) and when it last happened.
 */
public record LoyaltyNote(LoyaltyReason reason, int change, long time) {
    public LoyaltyNote {
        Objects.requireNonNull(reason, "reason");
    }
}
