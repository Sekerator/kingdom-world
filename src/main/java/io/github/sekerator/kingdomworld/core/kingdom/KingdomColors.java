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

    /** The sixteen dyes of the game with the colours it gives them, as {@code 0xRRGGBB}. */
    private static final List<String> DYE_NAMES = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private static final int[] DYE_COLORS = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};

    /**
     * The name of the vanilla dye nearest to a color, for example {@code red}: the banners and carpets of the great buildings of
     * a kingdom wear it.
     */
    public static String dye(int color) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < DYE_COLORS.length; i++) {
            int dr = r - (DYE_COLORS[i] >> 16 & 0xFF);
            int dg = g - (DYE_COLORS[i] >> 8 & 0xFF);
            int db = b - (DYE_COLORS[i] & 0xFF);
            // The eye weighs green most and red and blue by how red the colours are.
            double mean = (r + (DYE_COLORS[i] >> 16 & 0xFF)) / 2.0;
            double distance = (2 + mean / 256) * dr * dr + 4.0 * dg * dg + (2 + (255 - mean) / 256) * db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return DYE_NAMES.get(best);
    }
}
