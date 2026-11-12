package io.github.sekerator.kingdomworld.core.kingdom;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The colour of a kingdom as the dye its banners and carpets are made of.
 */
class KingdomColorsTest {
    private static final Set<String> DYES = Set.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    @Test
    void everyColourOfThePaletteIsADyeOfTheGame() {
        Set<String> used = new HashSet<>();
        for (int color : KingdomColors.PALETTE) {
            String dye = KingdomColors.dye(color);
            assertTrue(DYES.contains(dye), Integer.toHexString(color) + " gives " + dye);
            used.add(dye);
        }
        assertTrue(used.size() >= 6, "the palette looks different in dyes too: " + used);
    }

    @Test
    void theColoursOfTheDyesGiveTheirDyes() {
        assertEquals("red", KingdomColors.dye(0xB02E26));
        assertEquals("blue", KingdomColors.dye(0x3C44AA));
        assertEquals("white", KingdomColors.dye(0xFFFFFF));
        assertEquals("black", KingdomColors.dye(0x000000));
        assertEquals("red", KingdomColors.dye(0xB03A2E), "the first colour of the palette");
        for (int color : List.of(0x123456, 0xFEDCBA, 0x00FF00)) {
            assertTrue(DYES.contains(KingdomColors.dye(color)));
        }
    }
}
