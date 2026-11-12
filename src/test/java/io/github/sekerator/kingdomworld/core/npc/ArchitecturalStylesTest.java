package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The architectural styles: what each climate prefers, how the kingdoms of a world share them out, their indices and stones.
 */
class ArchitecturalStylesTest {
    private static Set<String> blocks() throws IOException {
        Set<String> ids = new HashSet<>();
        try (InputStream in = ArchitecturalStylesTest.class.getResourceAsStream("/minecraft-1.20.1-blocks.txt")) {
            assertNotNull(in, "the list of the blocks of the game is missing");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    ids.add(line.trim());
                }
            }
        }
        return ids;
    }

    @Test
    void everyClimatePrefersEveryStyleOnce() {
        for (NpcStyle.Climate climate : NpcStyle.Climate.values()) {
            for (boolean mountains : new boolean[]{false, true}) {
                List<String> list = ArchitecturalStyles.preferences(climate, mountains);
                assertEquals(ArchitecturalStyles.IDS.size(), list.size(), climate + " " + mountains);
                assertEquals(new HashSet<>(ArchitecturalStyles.IDS), new HashSet<>(list), climate + " " + mountains);
                if (mountains) {
                    assertEquals("mountain", list.get(0), climate.id());
                    assertEquals("mountain", ArchitecturalStyles.byClimate(climate, true));
                }
                assertEquals(list.get(0), ArchitecturalStyles.byClimate(climate, mountains));
            }
            List<String> plain = ArchitecturalStyles.preferences(climate, false);
            List<String> moved = ArchitecturalStyles.preferences(climate, true);
            assertEquals(plain.stream().filter(id -> !id.equals("mountain")).toList(), moved.subList(1, moved.size()),
                    climate + ": the others keep their order");
        }
        assertEquals("nordic", ArchitecturalStyles.byClimate(NpcStyle.Climate.COLD, false));
        assertEquals("desert", ArchitecturalStyles.byClimate(NpcStyle.Climate.DRY, false));
        assertEquals("eastern", ArchitecturalStyles.byClimate(NpcStyle.Climate.LUSH, false));
        assertEquals("classic", ArchitecturalStyles.byClimate(NpcStyle.Climate.TEMPERATE, false));
        assertEquals("classic", ArchitecturalStyles.byClimate(null, false), "an unknown climate is temperate");
    }

    @Test
    void aNewKingdomTakesAStyleNobodyHolds() {
        for (NpcStyle.Climate climate : NpcStyle.Climate.values()) {
            Set<String> taken = new LinkedHashSet<>();
            for (int i = 0; i < ArchitecturalStyles.IDS.size(); i++) {
                String style = ArchitecturalStyles.pick(climate, false, i * 31L, taken);
                assertFalse(taken.contains(style), climate + ": " + style + " is held already");
                assertEquals(ArchitecturalStyles.preferences(climate, false).get(i), style, climate + " in the order of the climate");
                assertEquals(style, ArchitecturalStyles.pick(climate, false, i * 31L, Set.copyOf(taken)));
                taken.add(style);
            }
            assertEquals(new HashSet<>(ArchitecturalStyles.IDS), taken, "eight kingdoms, eight styles");
            Set<String> repeated = new HashSet<>();
            for (long seed = 0; seed < 64; seed++) {
                String style = ArchitecturalStyles.pick(climate, false, seed, taken);
                assertTrue(ArchitecturalStyles.known(style), style);
                assertEquals(style, ArchitecturalStyles.pick(climate, false, seed, taken), "the same seed, the same style");
                repeated.add(style);
            }
            assertTrue(repeated.size() > 1, climate + ": beyond eight the seed decides " + repeated);
        }
        assertEquals("terem", ArchitecturalStyles.pick(NpcStyle.Climate.COLD, false, 1L, Set.of("nordic")));
        assertEquals("mountain", ArchitecturalStyles.pick(NpcStyle.Climate.DRY, true, 1L, Set.of("nordic")));
        assertEquals("desert", ArchitecturalStyles.pick(NpcStyle.Climate.DRY, true, 1L, Set.of("mountain")));
        assertEquals("nordic", ArchitecturalStyles.pick(NpcStyle.Climate.COLD, false, 1L, null));
    }

    @Test
    void indicesAndIdsGoBothWays() {
        for (int i = 0; i < ArchitecturalStyles.IDS.size(); i++) {
            String id = ArchitecturalStyles.id(i);
            assertTrue(ArchitecturalStyles.known(id));
            assertEquals(i, ArchitecturalStyles.index(id));
        }
        assertEquals(TemplateCatalog.STYLES, ArchitecturalStyles.IDS);
        assertEquals("", ArchitecturalStyles.id(-1));
        assertEquals("", ArchitecturalStyles.id(ArchitecturalStyles.IDS.size()));
        assertEquals(-1, ArchitecturalStyles.index("brick"));
        assertEquals(-1, ArchitecturalStyles.index(""));
        assertEquals(-1, ArchitecturalStyles.index(null));
        assertFalse(ArchitecturalStyles.known("stone"), "the looks of the second layout are no architectural styles");
        assertFalse(ArchitecturalStyles.known(""));
        assertFalse(ArchitecturalStyles.known(null));
    }

    @Test
    void theStonesOfAStyleAreBlocksOfTheGame() throws IOException {
        Set<String> blocks = blocks();
        for (String style : ArchitecturalStyles.IDS) {
            for (String block : List.of(ArchitecturalStyles.wallBlock(style), ArchitecturalStyles.paving(style), ArchitecturalStyles.cobbles(style))) {
                assertTrue(blocks.contains(block), style + ": " + block);
            }
        }
        assertEquals("minecraft:polished_diorite", ArchitecturalStyles.wallBlock("terem"));
        assertEquals("minecraft:smooth_sandstone", ArchitecturalStyles.wallBlock("southern"));
        assertEquals("", ArchitecturalStyles.wallBlock("brick"));
        assertEquals("", ArchitecturalStyles.paving(""));
        assertEquals("", ArchitecturalStyles.cobbles(null));
    }
}
