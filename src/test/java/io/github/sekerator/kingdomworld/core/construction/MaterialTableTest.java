package io.github.sekerator.kingdomworld.core.construction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table of woods and stones made from the names of blocks: the game's own woods, woods of mods named the usual way,
 * blocks a species lacks staying as they are, stones and the wood of a biome.
 */
class MaterialTableTest {
    private static final List<String> KINDS = List.of("planks", "log", "wood", "stripped_%s_log", "stripped_%s_wood", "stairs", "slab", "fence",
            "fence_gate", "door", "trapdoor", "button", "pressure_plate", "sign", "wall_sign", "hanging_sign", "wall_hanging_sign");

    private static List<String> wood(String namespace, String species, String... without) {
        List<String> ids = new ArrayList<>();
        for (String kind : KINDS) {
            String name = kind.contains("%s") ? kind.replace("%s", species) : species + "_" + kind;
            if (!List.of(without).contains(kind)) {
                ids.add(namespace + ":" + name);
            }
        }
        return ids;
    }

    private static MaterialTable table() {
        List<String> ids = new ArrayList<>();
        ids.addAll(wood("minecraft", "oak"));
        ids.addAll(wood("minecraft", "spruce"));
        ids.addAll(wood("minecraft", "dark_oak"));
        ids.addAll(wood("minecraft", "birch"));
        // Crimson has stems and hyphae instead of logs and wood.
        ids.addAll(List.of("minecraft:crimson_planks", "minecraft:crimson_stem", "minecraft:crimson_hyphae", "minecraft:stripped_crimson_stem",
                "minecraft:crimson_stairs", "minecraft:crimson_slab", "minecraft:crimson_door"));
        // A wood of a mod without doors and signs.
        ids.addAll(wood("examplemod", "redwood", "door", "sign", "wall_sign", "hanging_sign", "wall_hanging_sign"));
        ids.addAll(List.of("minecraft:cobblestone", "minecraft:cobblestone_stairs", "minecraft:cobblestone_slab", "minecraft:cobblestone_wall",
                "minecraft:stone_bricks", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab", "minecraft:stone_brick_wall",
                "minecraft:deepslate_bricks", "minecraft:deepslate_brick_stairs", "minecraft:deepslate_brick_slab", "minecraft:deepslate_brick_wall",
                "minecraft:stone", "minecraft:stone_stairs", "minecraft:stone_slab", "minecraft:smooth_stone", "minecraft:smooth_stone_slab",
                "examplemod:marble_bricks", "examplemod:marble_brick_stairs", "examplemod:marble_brick_slab",
                "minecraft:glass", "minecraft:crafting_table"));
        return MaterialTable.of(ids, id -> !id.contains("planks"));
    }

    @Test
    void theWoodsOfTheGameAndOfModsAreKnown() {
        MaterialTable table = table();
        assertTrue(table.woods().containsAll(List.of("minecraft:oak", "minecraft:spruce", "minecraft:dark_oak", "minecraft:crimson",
                "examplemod:redwood")));
        assertTrue(table.woods().indexOf("minecraft:spruce") < table.woods().indexOf("examplemod:redwood"), "the game's woods come first");
        assertEquals("minecraft:dark_oak", table.woodOf("minecraft:dark_oak_door"));
        assertEquals("minecraft:oak", table.woodOf("minecraft:stripped_oak_log"));
        assertEquals("", table.woodOf("minecraft:crafting_table"));
    }

    @Test
    void oakBecomesSpruce() {
        MaterialTable table = table();
        Materials spruce = new Materials("minecraft:spruce", "");
        for (String kind : List.of("planks", "log", "stairs", "slab", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate",
                "sign", "wall_sign")) {
            assertEquals("minecraft:spruce_" + kind, table.substitute("minecraft:oak_" + kind, spruce), kind);
        }
        assertEquals("minecraft:stripped_spruce_log", table.substitute("minecraft:stripped_oak_log", spruce));
        // Dark oak is its own species, not oak.
        assertEquals("minecraft:spruce_door", table.substitute("minecraft:dark_oak_door", spruce));
        // What is not wood stays.
        assertEquals("minecraft:crafting_table", table.substitute("minecraft:crafting_table", spruce));
        assertEquals("minecraft:cobblestone", table.substitute("minecraft:cobblestone", spruce));
        // Nothing chosen, or the wood of the place not decided yet: nothing changes.
        assertEquals("minecraft:oak_planks", table.substitute("minecraft:oak_planks", Materials.NONE));
        assertEquals("minecraft:oak_planks", table.substitute("minecraft:oak_planks", new Materials(Materials.AUTO, "")));
    }

    @Test
    void aWoodOfAModReplacesWhatItHasAndLeavesTheRest() {
        MaterialTable table = table();
        Materials redwood = new Materials("examplemod:redwood", "");
        assertEquals("examplemod:redwood_planks", table.substitute("minecraft:oak_planks", redwood));
        assertEquals("examplemod:redwood_log", table.substitute("minecraft:spruce_log", redwood));
        assertEquals("examplemod:redwood_stairs", table.substitute("minecraft:oak_stairs", redwood));
        // The mod has no doors: the oak door stays.
        assertEquals("minecraft:oak_door", table.substitute("minecraft:oak_door", redwood));
        assertEquals("minecraft:crimson_stem", table.substitute("minecraft:oak_log", new Materials("minecraft:crimson", "")));
        assertEquals("minecraft:crimson_hyphae", table.substitute("minecraft:oak_wood", new Materials("minecraft:crimson", "")));
        // An unknown species changes nothing.
        assertEquals("minecraft:oak_planks", table.substitute("minecraft:oak_planks", new Materials("othermod:ghost", "")));
    }

    @Test
    void stonesAreReplacedTheSameWay() {
        MaterialTable table = table();
        assertTrue(table.stones().containsAll(List.of("minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:deepslate_bricks",
                "examplemod:marble_bricks", "minecraft:stone")));
        assertFalse(table.hasStone("minecraft:smooth_stone"), "a block without stairs is no stone of the table");
        assertFalse(table.hasStone("minecraft:oak_planks"));
        Materials deepslate = new Materials("", "minecraft:deepslate_bricks");
        assertEquals("minecraft:deepslate_bricks", table.substitute("minecraft:cobblestone", deepslate));
        assertEquals("minecraft:deepslate_brick_stairs", table.substitute("minecraft:stone_brick_stairs", deepslate));
        assertEquals("minecraft:deepslate_brick_wall", table.substitute("minecraft:cobblestone_wall", deepslate));
        Materials marble = new Materials("", "examplemod:marble_bricks");
        assertEquals("examplemod:marble_brick_slab", table.substitute("minecraft:stone_brick_slab", marble));
        // The marble of the mod has no wall: the wall stays.
        assertEquals("minecraft:stone_brick_wall", table.substitute("minecraft:stone_brick_wall", marble));
        assertEquals("minecraft:oak_planks", table.substitute("minecraft:oak_planks", marble));
    }

    @Test
    void theWoodOfThePlaceComesFromTheBiome() {
        MaterialTable table = table();
        assertEquals("minecraft:birch", table.woodForBiome("minecraft:birch_forest", Set.of("minecraft:is_forest")));
        assertEquals("minecraft:dark_oak", table.woodForBiome("minecraft:dark_forest", Set.of("minecraft:is_forest")));
        assertEquals("minecraft:spruce", table.woodForBiome("minecraft:taiga", Set.of("minecraft:is_taiga")));
        assertEquals("minecraft:spruce", table.woodForBiome("minecraft:snowy_plains", Set.of()));
        assertEquals("examplemod:redwood", table.woodForBiome("examplemod:redwood_grove", Set.of()));
        assertEquals("minecraft:crimson", table.woodForBiome("minecraft:crimson_forest", Set.of()));
        assertEquals("", table.woodForBiome("minecraft:plains", Set.of()));
        // "oak_forest" names oak, not dark oak.
        assertEquals("minecraft:oak", table.woodForBiome("othermod:oak_forest", Set.of()));
    }
}
