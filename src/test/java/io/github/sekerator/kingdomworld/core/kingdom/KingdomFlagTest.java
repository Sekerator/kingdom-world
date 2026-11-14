package io.github.sekerator.kingdomworld.core.kingdom;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flag of a kingdom: the bounds of a flag and the changes the editor makes.
 */
class KingdomFlagTest {
    private static final KingdomFlag.Layer CROSS = new KingdomFlag.Layer("minecraft:cross", 0);
    private static final KingdomFlag.Layer BORDER = new KingdomFlag.Layer("minecraft:border", 15);
    private static final KingdomFlag.Layer CIRCLE = new KingdomFlag.Layer("minecraft:circle", 4);

    @Test
    void holdsAtMostSixLayersOfSixteenDyes() {
        List<KingdomFlag.Layer> six = new ArrayList<>();
        for (int i = 0; i < KingdomFlag.MAX_LAYERS; i++) {
            six.add(CROSS);
        }
        KingdomFlag full = new KingdomFlag(11, six);
        assertTrue(full.full());
        assertThrows(IllegalArgumentException.class, () -> full.added(CROSS));
        List<KingdomFlag.Layer> seven = new ArrayList<>(six);
        seven.add(BORDER);
        assertThrows(IllegalArgumentException.class, () -> new KingdomFlag(11, seven));
        assertThrows(IllegalArgumentException.class, () -> KingdomFlag.plain(-1));
        assertThrows(IllegalArgumentException.class, () -> KingdomFlag.plain(16));
        assertThrows(IllegalArgumentException.class, () -> new KingdomFlag.Layer("minecraft:cross", 16));
        assertThrows(IllegalArgumentException.class, () -> new KingdomFlag.Layer("minecraft:cross", -1));
        assertFalse(KingdomFlag.plain(0).full());
    }

    @Test
    void patternsAreIdsOfARightForm() {
        assertTrue(KingdomFlag.validPattern("minecraft:cross"));
        assertTrue(KingdomFlag.validPattern("somemod:flags/eagle.big-1"));
        assertFalse(KingdomFlag.validPattern("minecraft:base"), "the field is no layer");
        assertFalse(KingdomFlag.validPattern("cross"));
        assertFalse(KingdomFlag.validPattern("Minecraft:Cross"));
        assertFalse(KingdomFlag.validPattern("minecraft:"));
        assertFalse(KingdomFlag.validPattern(":cross"));
        assertFalse(KingdomFlag.validPattern("minecraft:cross pattern"));
        assertFalse(KingdomFlag.validPattern(null));
        assertTrue(KingdomFlag.validPattern("m:" + "a".repeat(KingdomFlag.MAX_PATTERN_LENGTH - 2)));
        assertFalse(KingdomFlag.validPattern("m:" + "a".repeat(KingdomFlag.MAX_PATTERN_LENGTH - 1)));
        assertThrows(IllegalArgumentException.class, () -> new KingdomFlag.Layer("minecraft:base", 0));
        assertThrows(NullPointerException.class, () -> new KingdomFlag.Layer(null, 0));
    }

    @Test
    void theLayersCannotBeChangedFromOutside() {
        List<KingdomFlag.Layer> layers = new ArrayList<>(List.of(CROSS));
        KingdomFlag flag = new KingdomFlag(3, layers);
        layers.add(BORDER);
        assertEquals(1, flag.layers().size());
        assertThrows(UnsupportedOperationException.class, () -> flag.layers().add(BORDER));
    }

    @Test
    void theEditorChangesACopy() {
        KingdomFlag plain = KingdomFlag.plain(11);
        KingdomFlag flag = plain.added(CROSS).added(BORDER).added(CIRCLE);
        assertEquals(List.of(), plain.layers());
        assertEquals(List.of(CROSS, BORDER, CIRCLE), flag.layers());
        assertEquals(14, flag.withBase(14).base());
        assertEquals(flag.layers(), flag.withBase(14).layers());
        assertEquals(List.of(CROSS, CIRCLE, CIRCLE), flag.withLayer(1, CIRCLE).layers());
        assertEquals(List.of(CROSS, CIRCLE), flag.removed(1).layers());
        assertEquals(List.of(BORDER, CROSS, CIRCLE), flag.moved(1, -1).layers());
        assertEquals(List.of(CROSS, CIRCLE, BORDER), flag.moved(1, 1).layers());
        assertEquals(List.of(BORDER, CIRCLE, CROSS), flag.moved(0, 5).layers(), "a move goes as far as the layers go");
        assertEquals(List.of(CIRCLE, CROSS, BORDER), flag.moved(2, -9).layers());
        assertSame(flag, flag.moved(0, -1));
        assertSame(flag, flag.moved(7, 1));
        assertSame(flag, flag.moved(-1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> flag.removed(3));
        assertThrows(IndexOutOfBoundsException.class, () -> flag.withLayer(3, CROSS));
    }

    @Test
    void flagsAreEqualByTheirFieldAndLayers() {
        assertEquals(KingdomFlag.plain(4).added(CROSS), new KingdomFlag(4, List.of(new KingdomFlag.Layer("minecraft:cross", 0))));
        assertFalse(KingdomFlag.plain(4).added(CROSS).equals(KingdomFlag.plain(5).added(CROSS)));
        assertFalse(KingdomFlag.plain(4).added(CROSS).added(BORDER).equals(KingdomFlag.plain(4).added(BORDER).added(CROSS)));
    }
}
