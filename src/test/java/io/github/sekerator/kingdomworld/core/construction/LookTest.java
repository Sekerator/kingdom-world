package io.github.sekerator.kingdomworld.core.construction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The look of a building: its text, and the blocks of a template that stand for its parts replaced by what it names, the
 * stone of the materials then standing for the walls only.
 */
class LookTest {
    private static List<String> wood(String species) {
        List<String> ids = new ArrayList<>();
        for (String kind : List.of("planks", "log", "stairs", "slab", "fence", "fence_gate", "door", "trapdoor")) {
            ids.add("minecraft:" + species + "_" + kind);
        }
        ids.add("minecraft:stripped_" + species + "_log");
        return ids;
    }

    private static List<String> stone(String block, String stem, boolean wall) {
        List<String> ids = new ArrayList<>(List.of("minecraft:" + block, "minecraft:" + stem + "_stairs", "minecraft:" + stem + "_slab"));
        if (wall) {
            ids.add("minecraft:" + stem + "_wall");
        }
        return ids;
    }

    private static MaterialTable table() {
        List<String> ids = new ArrayList<>();
        for (String species : List.of("oak", "spruce", "dark_oak", "mangrove", "warped", "birch")) {
            ids.addAll(wood(species));
        }
        ids.addAll(stone("cobblestone", "cobblestone", true));
        ids.addAll(stone("mossy_cobblestone", "mossy_cobblestone", true));
        ids.addAll(stone("stone_bricks", "stone_brick", true));
        ids.addAll(stone("bricks", "brick", true));
        ids.addAll(stone("deepslate_tiles", "deepslate_tile", true));
        ids.addAll(stone("polished_andesite", "polished_andesite", false));
        ids.addAll(stone("stone", "stone", false));
        ids.addAll(stone("sandstone", "sandstone", true));
        ids.addAll(List.of("minecraft:dark_prismarine", "minecraft:dark_prismarine_stairs", "minecraft:dark_prismarine_slab",
                "minecraft:chiseled_stone_bricks", "minecraft:chiseled_sandstone", "minecraft:white_terracotta", "minecraft:yellow_terracotta",
                "minecraft:red_wool", "minecraft:blue_wool", "minecraft:red_carpet", "minecraft:blue_carpet", "minecraft:red_wall_banner",
                "minecraft:blue_wall_banner", "minecraft:red_bed", "minecraft:blue_bed", "minecraft:glass_pane", "minecraft:air"));
        return MaterialTable.of(ids, id -> !id.contains("planks") && !id.contains("prismarine") && !id.contains("terracotta")
                && !id.contains("wool") && !id.contains("carpet") && !id.contains("banner") && !id.contains("bed") && !id.contains("glass"));
    }

    private static Look look() {
        Map<Look.Part, String> parts = new EnumMap<>(Look.Part.class);
        parts.put(Look.Part.ROOF, "minecraft:deepslate_tiles");
        parts.put(Look.Part.SPIRE, "minecraft:spruce");
        parts.put(Look.Part.ROUGH, "minecraft:mossy_cobblestone");
        parts.put(Look.Part.TRIM, "minecraft:stone");
        parts.put(Look.Part.CARVED, "minecraft:chiseled_sandstone");
        parts.put(Look.Part.PLASTER, "minecraft:yellow_terracotta");
        parts.put(Look.Part.CLOTH, "blue");
        parts.put(Look.Part.SHUTTER, Look.REMOVED);
        parts.put(Look.Part.CHIMNEY, Look.REMOVED);
        return Look.of(parts);
    }

    @Test
    void aLookIsWrittenAndReadAsText() {
        Look look = look();
        assertEquals(look, Look.parse(look.text()));
        assertTrue(look.text().startsWith("roof=minecraft:deepslate_tiles,spire=minecraft:spruce,"), look.text());
        assertTrue(Look.parse("").isEmpty());
        assertTrue(Look.parse(null).isEmpty());
        // Unknown parts and broken pairs are left out, so that a look of a newer version still reads.
        assertEquals("roof=minecraft:bricks", Look.parse("roof=minecraft:bricks,glow=minecraft:sea_lantern,=x,cloth=").text());
        assertEquals("", Look.NONE.with(Look.Part.CLOTH, "blue").with(Look.Part.CLOTH, "").text());
        Materials materials = new Materials("minecraft:spruce", "minecraft:bricks", look);
        assertEquals(look, materials.appearance());
        assertFalse(materials.none());
        assertEquals("minecraft:spruce|minecraft:bricks", new Materials("minecraft:spruce", "minecraft:bricks").key());
        assertTrue(materials.key().endsWith(look.text()));
        assertTrue(new Materials("", "", "").none());
    }

    @Test
    void thePartsOfALookTakeThePlaceOfTheirStandIns() {
        MaterialTable table = table();
        Materials materials = new Materials("minecraft:birch", "minecraft:sandstone", look());
        // Roofs of all three roof woods become the roof of the look, by kind.
        assertEquals("minecraft:deepslate_tile_stairs", table.substitute("minecraft:dark_oak_stairs", materials));
        assertEquals("minecraft:deepslate_tile_slab", table.substitute("minecraft:mangrove_slab", materials));
        assertEquals("minecraft:deepslate_tiles", table.substitute("minecraft:warped_planks", materials));
        // Other blocks of a roof wood are timber and become the wood of the materials.
        assertEquals("minecraft:birch_door", table.substitute("minecraft:dark_oak_door", materials));
        assertEquals("minecraft:birch_log", table.substitute("minecraft:dark_oak_log", materials));
        // Spires of a wood.
        assertEquals("minecraft:spruce_stairs", table.substitute("minecraft:dark_prismarine_stairs", materials));
        assertEquals("minecraft:spruce_planks", table.substitute("minecraft:dark_prismarine", materials));
        // The stone of the materials is that of the walls; footing and trim follow the look.
        assertEquals("minecraft:sandstone_stairs", table.substitute("minecraft:stone_brick_stairs", materials));
        assertEquals("minecraft:mossy_cobblestone_wall", table.substitute("minecraft:cobblestone_wall", materials));
        assertEquals("minecraft:stone_slab", table.substitute("minecraft:polished_andesite_slab", materials));
        assertEquals("minecraft:chiseled_sandstone", table.substitute("minecraft:chiseled_stone_bricks", materials));
        assertEquals("minecraft:yellow_terracotta", table.substitute("minecraft:white_terracotta", materials));
        // Cloth of the colour of the look.
        assertEquals("minecraft:blue_wool", table.substitute("minecraft:red_wool", materials));
        assertEquals("minecraft:blue_carpet", table.substitute("minecraft:red_carpet", materials));
        assertEquals("minecraft:blue_wall_banner", table.substitute("minecraft:red_wall_banner", materials));
        assertEquals("minecraft:blue_bed", table.substitute("minecraft:red_bed", materials));
        // Shutters and chimney left out.
        assertEquals(MaterialTable.AIR, table.substitute("minecraft:spruce_trapdoor", materials));
        assertEquals(MaterialTable.AIR, table.substitute("minecraft:brick_wall", materials));
        assertEquals(MaterialTable.AIR, table.substitute("minecraft:bricks", materials));
        // Timber becomes the wood of the materials as before; the other trapdoors are no shutters.
        assertEquals("minecraft:birch_planks", table.substitute("minecraft:oak_planks", materials));
        assertEquals("minecraft:birch_trapdoor", table.substitute("minecraft:oak_trapdoor", materials));
        assertEquals("minecraft:stripped_birch_log", table.substitute("minecraft:stripped_spruce_log", materials));
        // Accents stay.
        assertEquals("minecraft:glass_pane", table.substitute("minecraft:glass_pane", materials));
        assertEquals("minecraft:deepslate_tiles", table.substitute("minecraft:deepslate_tiles", materials));
    }

    @Test
    void withoutALookEveryStoneFollowsTheStoneAsBefore() {
        MaterialTable table = table();
        Materials old = new Materials("minecraft:spruce", "minecraft:sandstone");
        assertEquals("minecraft:sandstone", table.substitute("minecraft:cobblestone", old));
        assertEquals("minecraft:sandstone_slab", table.substitute("minecraft:polished_andesite_slab", old));
        assertEquals("minecraft:spruce_stairs", table.substitute("minecraft:dark_oak_stairs", old));
        assertEquals("minecraft:spruce_trapdoor", table.substitute("minecraft:spruce_trapdoor", old));
        assertEquals("minecraft:white_terracotta", table.substitute("minecraft:white_terracotta", old));
        // A look of a few parts: the rest of the template is replaced as without one, the stones but the walls stay.
        Materials roofOnly = new Materials("minecraft:spruce", "minecraft:sandstone", Look.NONE.with(Look.Part.ROOF, "minecraft:bricks"));
        assertEquals("minecraft:brick_stairs", table.substitute("minecraft:dark_oak_stairs", roofOnly));
        assertEquals("minecraft:cobblestone", table.substitute("minecraft:cobblestone", roofOnly));
        assertEquals("minecraft:sandstone", table.substitute("minecraft:stone_bricks", roofOnly));
        assertEquals("minecraft:spruce_trapdoor", table.substitute("minecraft:spruce_trapdoor", roofOnly));
    }

    @Test
    void aLookNamesOnlyWhatTheGameHas() {
        MaterialTable table = table();
        assertTrue(table.knows(look()));
        assertTrue(table.knows(Look.NONE));
        assertTrue(table.knows(Look.NONE.with(Look.Part.SPIRE, "minecraft:dark_prismarine")), "a block with stairs and a slab");
        assertFalse(table.knows(Look.NONE.with(Look.Part.ROOF, "minecraft:glass_pane")));
        assertFalse(table.knows(Look.NONE.with(Look.Part.CLOTH, "violet")));
        assertFalse(table.knows(Look.NONE.with(Look.Part.PLASTER, "minecraft:no_such_block")));
        assertFalse(table.knows(Look.NONE.with(Look.Part.ROOF, Look.REMOVED)), "a roof is never left out");
        assertFalse(table.knows(Look.NONE.with(Look.Part.ROUGH, Look.REMOVED)), "a footing is never left out");
        assertTrue(table.knows(Look.NONE.with(Look.Part.CHIMNEY, Look.REMOVED)));
        assertFalse(table.knows(Look.NONE.with(Look.Part.SHUTTER, "minecraft:stone")));
        // A part the look names but the game lacks keeps the template's block.
        Materials ghost = new Materials("", "", Look.NONE.with(Look.Part.ROOF, "othermod:ghost_tiles"));
        assertEquals("minecraft:dark_oak_stairs", table.substitute("minecraft:dark_oak_stairs", ghost));
    }
}
