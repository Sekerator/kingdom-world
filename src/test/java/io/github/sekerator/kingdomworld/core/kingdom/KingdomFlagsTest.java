package io.github.sekerator.kingdomworld.core.kingdom;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flags kingdoms fly without a king drawing one: the flag of a colour, and the flags drawn for the kingdoms of the world.
 */
class KingdomFlagsTest {
    private static final List<String> STYLES = List.of("classic", "nordic", "terem", "gothic", "southern", "desert", "eastern", "mountain");
    private static final List<String> CHARACTERS = new ArrayList<>(List.of("WARRIOR", "MAGE", "MERCHANT", "BUILDER"));

    static {
        CHARACTERS.add(null);
    }

    @Test
    void theFlagOfAColourIsABannerOfTheNearestDye() {
        for (int color : KingdomColors.PALETTE) {
            KingdomFlag flag = KingdomFlags.standard(color);
            assertEquals(KingdomColors.nearestDye(color), flag.base());
            assertEquals(KingdomColors.dye(color), KingdomColors.dyeName(flag.base()));
            assertTrue(flag.layers().isEmpty());
        }
        assertEquals(KingdomFlag.plain(14), KingdomFlags.standard(0xB03A2E));
    }

    @Test
    void theFlagOfTheColourIsKeptAsNone() {
        Kingdom kingdom = new Kingdom(UUID.randomUUID(), "Valoria", 0x1F618D, null, 0L);
        assertNull(KingdomFlags.normalized(kingdom, KingdomFlags.standard(0x1F618D)));
        assertNull(KingdomFlags.normalized(kingdom, null));
        KingdomFlag own = KingdomFlag.plain(11).added(new KingdomFlag.Layer("minecraft:cross", 0));
        assertSame(own, KingdomFlags.normalized(kingdom, own));
        KingdomFlag otherColour = KingdomFlag.plain(14);
        assertSame(otherColour, KingdomFlags.normalized(kingdom, otherColour), "a plain banner of another dye is a flag of its own");
        assertEquals(KingdomFlags.standard(0x1F618D), KingdomFlags.of(kingdom));
        assertEquals(own, KingdomFlags.of(kingdom.withFlag(own)));
    }

    @Test
    void contrastIsMeasuredAsForText() {
        assertEquals(1.0, KingdomFlags.contrast(14, 14), 1e-9);
        assertTrue(KingdomFlags.contrast(0, 15) > 15);
        assertEquals(KingdomFlags.contrast(0, 11), KingdomFlags.contrast(11, 0), 1e-9);
        for (int field = 0; field < KingdomColors.DYES; field++) {
            int brush = KingdomFlags.mostContrasting(field);
            for (int dye = 0; dye < KingdomColors.DYES; dye++) {
                assertTrue(KingdomFlags.contrast(field, brush) >= KingdomFlags.contrast(field, dye));
            }
            assertTrue(KingdomFlags.contrast(field, brush) >= KingdomFlags.MIN_CONTRAST, "white or black stands out from " + field);
        }
    }

    @Test
    void generatedFlagsAreDeterministicSmallFittingAndClear() {
        Set<String> styles = new HashSet<>();
        for (String style : STYLES) {
            for (int dye = 0; dye < KingdomColors.DYES; dye++) {
                int color = KingdomColors.dyeRgb(dye);
                for (int seed = 0; seed < 200; seed++) {
                    String character = CHARACTERS.get(seed % CHARACTERS.size());
                    KingdomFlag flag = KingdomFlags.generated(seed * 7919L, color, style, character, Set.of());
                    assertEquals(flag, KingdomFlags.generated(seed * 7919L, color, style, character, Set.of()));
                    assertEquals(KingdomColors.nearestDye(color), flag.base(), "the field is the colour of the kingdom");
                    assertTrue(flag.layers().size() >= 1 && flag.layers().size() <= 3, style + " " + flag);
                    List<Integer> under = new ArrayList<>(List.of(flag.base()));
                    for (KingdomFlag.Layer layer : flag.layers()) {
                        assertTrue(KingdomFlags.GENERATED_PATTERNS.contains(layer.pattern()), layer.pattern());
                        assertTrue(KingdomFlags.contrast(layer.dye(), flag.base()) >= KingdomFlags.MIN_CONTRAST,
                                style + " " + flag + ": " + layer + " on the field");
                        for (int below : under) {
                            // A pattern of the colour of one under it joins it, as two charges of the same metal.
                            assertTrue(below == layer.dye() || KingdomFlags.contrast(layer.dye(), below) >= KingdomFlags.MIN_CONTRAST,
                                    style + " " + flag + ": " + layer + " on " + below);
                        }
                        under.add(layer.dye());
                    }
                    styles.add(style + flag.layers().get(0).pattern());
                }
            }
        }
        assertTrue(styles.size() >= STYLES.size() * 3, "every style draws several motifs: " + styles.size());
    }

    @Test
    void anUnknownStyleDrawsAsTheClassic() {
        for (int seed = 0; seed < 50; seed++) {
            assertEquals(KingdomFlags.generated(seed, 0x1F618D, "classic", null, Set.of()),
                    KingdomFlags.generated(seed, 0x1F618D, "unknown", null, Set.of()));
            assertEquals(KingdomFlags.generated(seed, 0x1F618D, "classic", null, Set.of()),
                    KingdomFlags.generated(seed, 0x1F618D, "", null, Set.of()));
        }
    }

    @Test
    void aFlagTakenIsNotGivenAgain() {
        KingdomFlag first = KingdomFlags.generated(42L, 0x1F618D, "gothic", "WARRIOR", Set.of());
        KingdomFlag second = KingdomFlags.generated(42L, 0x1F618D, "gothic", "WARRIOR", Set.of(first));
        assertNotEquals(first, second);
        Set<KingdomFlag> taken = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            KingdomFlag flag = KingdomFlags.generated(7L, 0xB03A2E, "classic", null, taken);
            if (!taken.add(flag)) {
                // A flag already flown is given only when the sixteen seeds tried give no other.
                for (int next = 0; next < 16; next++) {
                    assertTrue(taken.contains(KingdomFlags.generated(7L + next, 0xB03A2E, "classic", null, Set.of())), flag + " given");
                }
            }
        }
        assertTrue(taken.size() >= 6, "a red kingdom of the classic style has many flags to choose from: " + taken.size());
    }

    @Test
    void rulersLeanToTheirMotifs() {
        int warriorCrosses = 0;
        int builderCrosses = 0;
        for (int seed = 0; seed < 400; seed++) {
            if (motif(KingdomFlags.generated(seed, 0xB03A2E, "classic", "WARRIOR", Set.of())).equals("minecraft:cross")) {
                warriorCrosses++;
            }
            if (motif(KingdomFlags.generated(seed, 0xB03A2E, "classic", "BUILDER", Set.of())).equals("minecraft:cross")) {
                builderCrosses++;
            }
        }
        assertTrue(warriorCrosses > 30, "a warrior likes crosses: " + warriorCrosses);
        assertEquals(0, builderCrosses, "the classic style has no saltire of its own");
    }

    /**
     * Flags drawn for a few kingdoms: a change of the generator changes the flags of the kingdoms of the world not yet given one.
     */
    @Test
    void goldenFlags() {
        assertEquals(new KingdomFlag(11, List.of(layer("straight_cross", 4))),
                KingdomFlags.generated(1L, 0x1F618D, "nordic", null, Set.of()));
        assertEquals(new KingdomFlag(14, List.of(layer("cross", 0), layer("border", 0))),
                KingdomFlags.generated(42L, 0xB03A2E, "gothic", "WARRIOR", Set.of()));
        assertEquals(new KingdomFlag(1, List.of(layer("rhombus", 11))),
                KingdomFlags.generated(-7L, 0xD4AC0D, "eastern", "MAGE", Set.of()));
        assertEquals(new KingdomFlag(0, List.of(layer("small_stripes", 15), layer("curly_border", 13))),
                KingdomFlags.generated(123456789L, 0xF9FFFE, "southern", "MERCHANT", Set.of()));
        assertEquals(new KingdomFlag(7, List.of(layer("triangles_bottom", 0))),
                KingdomFlags.generated(2024L, 0x1E8449, "mountain", "BUILDER", Set.of()));
    }

    private static KingdomFlag.Layer layer(String path, int dye) {
        return new KingdomFlag.Layer("minecraft:" + path, dye);
    }

    private static String motif(KingdomFlag flag) {
        return flag.layers().get(0).pattern();
    }
}
