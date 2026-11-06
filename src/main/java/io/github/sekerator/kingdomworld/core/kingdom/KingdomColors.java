package io.github.sekerator.kingdomworld.core.kingdom;

import java.util.List;
import java.util.UUID;

/**
 * Default map colors for new kingdoms.
 */
public final class KingdomColors {
    /** Distinct, map-friendly colors as {@code 0xRRGGBB}. */
    public static final List<Integer> PALETTE = List.of(
            0xB03A2E, 0x1F618D, 0x1E8449, 0xB7950B,
            0x6C3483, 0xCA6F1E, 0x117A65, 0x839192,
            0x922B21, 0x2874A6, 0x239B56, 0xD4AC0D,
            0x7D3C98, 0xDC7633, 0x148F77, 0x5D6D7E);

    private KingdomColors() {
    }

    /**
     * Deterministic default color for a kingdom id.
     */
    public static int defaultFor(UUID id) {
        return PALETTE.get(Math.floorMod(id.hashCode(), PALETTE.size()));
    }
}
