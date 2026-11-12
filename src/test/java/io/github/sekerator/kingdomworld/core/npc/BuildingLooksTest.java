package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.construction.Look;
import io.github.sekerator.kingdomworld.core.construction.MaterialTable;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The looks of the buildings of kingdoms of the world: only blocks the game has, the same look for the same plot, and houses
 * of one style and quarter that differ from each other.
 */
class BuildingLooksTest {
    private static final List<String> NOT_STONE = List.of("planks", "copper", "iron", "gold", "glass", "ice", "purpur", "quartz",
            "prismarine", "wool", "carpet", "terracotta", "concrete", "banner", "bed", "log", "wood", "leaves", "stem", "hyphae");

    /**
     * The table of materials of the game itself, from the list of its blocks; what the game mines with a pickaxe is guessed
     * from the names.
     */
    static MaterialTable vanilla() throws IOException {
        List<String> ids = new ArrayList<>();
        try (InputStream in = BuildingLooksTest.class.getResourceAsStream("/minecraft-1.20.1-blocks.txt")) {
            assertNotNull(in, "the list of the blocks of the game is missing");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    ids.add(line.trim());
                }
            }
        }
        return MaterialTable.of(ids, id -> NOT_STONE.stream().noneMatch(id::contains));
    }

    @Test
    void everyLookNamesOnlyWhatTheGameHas() throws IOException {
        MaterialTable table = vanilla();
        for (NpcStyle style : NpcStyle.values()) {
            for (Materials materials : BuildingLooks.samples(style, "minecraft:oak", 60)) {
                Look look = materials.appearance();
                assertTrue(table.knows(look), style + ": " + look);
                for (Look.Part part : Look.Part.values()) {
                    String value = look.get(part);
                    assertTrue(value.isEmpty() || table.knows(Look.NONE.with(part, value)), style + " " + part + " " + value);
                }
                assertTrue(materials.wood().isEmpty() || table.hasWood(materials.wood()), style + " wood " + materials.wood());
                assertTrue(materials.stone().isEmpty() || table.hasStone(materials.stone()), style + " stone " + materials.stone());
            }
        }
        for (String style : BuildingLooks.styles()) {
            for (BuildingLooks.Quarter quarter : BuildingLooks.Quarter.values()) {
                for (long seed = 0; seed < 60; seed++) {
                    Materials materials = BuildingLooks.of(style, "minecraft:oak", "red", quarter, seed * 7919);
                    assertTrue(table.knows(materials.appearance()), style + ": " + materials.look());
                    assertTrue(materials.wood().isEmpty() || table.hasWood(materials.wood()), style + " wood " + materials.wood());
                    assertTrue(materials.stone().isEmpty() || table.hasStone(materials.stone()), style + " stone " + materials.stone());
                }
            }
        }
        assertTrue(BuildingLooks.styles().containsAll(TemplateCatalog.STYLES), "every style of the templates has a palette");
    }

    @Test
    void thePublicLookIsThatOfTheStyleAndAHouseKeepsItsLook() {
        for (NpcStyle style : NpcStyle.values()) {
            Materials castle = BuildingLooks.of(style, "minecraft:birch", "blue", BuildingLooks.Quarter.PUBLIC, 1);
            assertEquals(castle, BuildingLooks.of(style, "minecraft:birch", "blue", BuildingLooks.Quarter.PUBLIC, 99_999));
            assertEquals("blue", castle.appearance().get(Look.Part.CLOTH));
            assertEquals(style.stone(), castle.stone());
            assertEquals(style.wood().isEmpty() ? "minecraft:birch" : style.wood(), castle.wood());
            for (BuildingLooks.Quarter quarter : BuildingLooks.Quarter.values()) {
                for (long seed = -50; seed < 50; seed++) {
                    assertEquals(BuildingLooks.of(style, "minecraft:birch", "blue", quarter, seed * 104_729L),
                            BuildingLooks.of(style, "minecraft:birch", "blue", quarter, seed * 104_729L));
                }
            }
        }
    }

    @Test
    void housesOfOneQuarterDiffer() {
        for (NpcStyle style : NpcStyle.values()) {
            for (BuildingLooks.Quarter quarter : BuildingLooks.Quarter.values()) {
                if (quarter == BuildingLooks.Quarter.PUBLIC) {
                    continue;
                }
                Set<Materials> looks = new HashSet<>();
                Set<String> roofs = new HashSet<>();
                Set<String> plasters = new HashSet<>();
                Set<String> cloths = new HashSet<>();
                for (long seed = 0; seed < 40; seed++) {
                    Materials materials = BuildingLooks.of(style, "minecraft:oak", "red", quarter, seed * 31 + 7);
                    looks.add(materials);
                    roofs.add(materials.appearance().get(Look.Part.ROOF));
                    plasters.add(materials.appearance().get(Look.Part.PLASTER));
                    cloths.add(materials.appearance().get(Look.Part.CLOTH));
                }
                assertTrue(looks.size() >= 35, style + " " + quarter + ": only " + looks.size() + " looks of 40 houses");
                assertTrue(roofs.size() >= 2, style + " " + quarter + " roofs " + roofs);
                assertTrue(plasters.size() >= 3, style + " " + quarter + " plaster " + plasters);
                assertTrue(cloths.size() >= 6, style + " " + quarter + " cloth " + cloths);
            }
        }
    }

    @Test
    void nobleHousesKeepTheirShuttersAndChimneysAndPoorOnesOftenDoNot() {
        int poorWithout = 0;
        for (NpcStyle style : NpcStyle.values()) {
            for (long seed = 0; seed < 40; seed++) {
                Look noble = BuildingLooks.of(style, "minecraft:oak", "red", BuildingLooks.Quarter.NOBLE, seed).appearance();
                assertTrue(!Look.REMOVED.equals(noble.get(Look.Part.SHUTTER)) && !Look.REMOVED.equals(noble.get(Look.Part.CHIMNEY)),
                        style + " " + noble);
                Look poor = BuildingLooks.of(style, "minecraft:oak", "red", BuildingLooks.Quarter.POOR, seed).appearance();
                if (Look.REMOVED.equals(poor.get(Look.Part.SHUTTER)) || Look.REMOVED.equals(poor.get(Look.Part.CHIMNEY))) {
                    poorWithout++;
                }
            }
        }
        assertTrue(poorWithout > 60, "poor houses without shutters or chimney: " + poorWithout + " of 200");
    }
}
