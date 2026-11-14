package io.github.sekerator.kingdomworld.core.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideRecipeTest {

    @Test
    void readsAShapedRecipe() {
        JsonObject json = JsonParser.parseString("""
                {"type": "minecraft:crafting_shaped", "pattern": ["I  ", "IWI", "P P"],
                 "key": {"I": {"item": "minecraft:gold_ingot"}, "W": {"item": "minecraft:red_wool"}, "P": {"tag": "minecraft:planks"}},
                 "result": {"item": "kingdomworld:throne", "count": 1}}
                """).getAsJsonObject();
        GuideRecipe recipe = GuideRecipe.fromJson(json, tag -> tag.equals("minecraft:planks")
                ? List.of("minecraft:oak_planks", "minecraft:spruce_planks") : List.of()).orElseThrow();
        assertEquals("kingdomworld:throne", recipe.result());
        assertEquals(1, recipe.count());
        assertFalse(recipe.shapeless());
        assertEquals(List.of("minecraft:gold_ingot"), recipe.slots().get(0));
        assertEquals(List.of(), recipe.slots().get(1));
        assertEquals(List.of("minecraft:red_wool"), recipe.slots().get(4));
        assertEquals(List.of("minecraft:oak_planks", "minecraft:spruce_planks"), recipe.slots().get(6));
        assertEquals(List.of(), recipe.slots().get(7));
    }

    @Test
    void readsAShapelessRecipeWithAListOfIngredients() {
        JsonObject json = JsonParser.parseString("""
                {"type": "minecraft:crafting_shapeless", "ingredients": [{"item": "minecraft:book"},
                 [{"item": "minecraft:compass"}, {"item": "minecraft:map"}]], "result": {"item": "kingdomworld:kingdom_atlas", "count": 2}}
                """).getAsJsonObject();
        GuideRecipe recipe = GuideRecipe.fromJson(json, tag -> List.of()).orElseThrow();
        assertTrue(recipe.shapeless());
        assertEquals(2, recipe.count());
        assertEquals(List.of("minecraft:book"), recipe.slots().get(0));
        assertEquals(List.of("minecraft:compass", "minecraft:map"), recipe.slots().get(1));
    }

    @Test
    void leavesOutOtherKinds() {
        assertTrue(GuideRecipe.fromJson(JsonParser.parseString("{\"type\": \"minecraft:smelting\"}").getAsJsonObject(), tag -> List.of())
                .isEmpty());
        assertTrue(GuideRecipe.fromJson(JsonParser.parseString("{\"type\": \"minecraft:crafting_shaped\"}").getAsJsonObject(),
                tag -> List.of()).isEmpty());
    }

    @Test
    void followsTagsWithinTags() {
        Map<String, String> files = Map.of(
                "minecraft:planks", "{\"values\": [\"minecraft:oak_planks\", \"#minecraft:nether_planks\"]}",
                "minecraft:nether_planks", "{\"values\": [\"minecraft:crimson_planks\", {\"id\": \"#minecraft:planks\", \"required\": false}]}");
        List<String> items = GuideRecipe.tagItems("minecraft:planks", id -> Optional.ofNullable(files.get(id))
                .map(text -> JsonParser.parseString(text).getAsJsonObject()));
        assertEquals(List.of("minecraft:oak_planks", "minecraft:crimson_planks"), items);
        assertEquals("data/minecraft/tags/items/planks.json", GuideRecipe.dataPath("tags/items", "minecraft:planks"));
        assertEquals("data/kingdomworld/recipes/throne.json", GuideRecipe.dataPath("recipes", "kingdomworld:throne"));
    }
}
