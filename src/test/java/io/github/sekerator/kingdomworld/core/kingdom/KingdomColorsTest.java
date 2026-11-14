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

    @Test
    void theDyesAreNumberedAsTheGameNumbersThem() {
        List<String> order = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
                "purple", "blue", "brown", "green", "red", "black");
        assertEquals(order.size(), KingdomColors.DYES);
        for (int i = 0; i < KingdomColors.DYES; i++) {
            assertEquals(order.get(i), KingdomColors.dyeName(i));
            assertEquals(i, KingdomColors.dyeIndex(order.get(i)));
            assertEquals(i, KingdomColors.nearestDye(KingdomColors.dyeRgb(i)), "the colour of a dye is nearest to itself");
        }
        assertEquals(-1, KingdomColors.dyeIndex("crimson"));
        assertEquals(-1, KingdomColors.dyeIndex(""));
        assertEquals(0x3C44AA, KingdomColors.dyeRgb(11));
        org.junit.jupiter.api.Assertions.assertThrows(IndexOutOfBoundsException.class, () -> KingdomColors.dyeRgb(16));
        org.junit.jupiter.api.Assertions.assertThrows(IndexOutOfBoundsException.class, () -> KingdomColors.dyeName(-1));
    }

    @Test
    void theNearestDyeGivesTheSameDyeAsBefore() {
        for (int color : List.of(0xB03A2E, 0x1F618D, 0x1E8449, 0xB7950B, 0x123456, 0xFEDCBA, 0x00FF00, 0, 0xFFFFFF)) {
            assertEquals(KingdomColors.dye(color), KingdomColors.dyeName(KingdomColors.nearestDye(color)));
        }
        assertEquals(14, KingdomColors.nearestDye(0xB03A2E));
        assertEquals(15, KingdomColors.nearestDye(0x000000));
        assertEquals(0, KingdomColors.nearestDye(0xFFFFFF));
    }
}
