package io.github.sekerator.kingdomworld.core.construction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a template costs: doors, beds and tall plants once for their two blocks, a double slab two slabs, candles by their
 * number; liquids, air and fire nothing, and a block no item makes keeps the template from being built.
 */
class TemplateCostTest {

    @Test
    void twoBlockThingsCountOnce() {
        TemplateCost cost = TemplateCost.of(List.of(BlockCost.one("minecraft:oak_door"), BlockCost.part("minecraft:oak_door"),
                BlockCost.one("minecraft:red_bed"), BlockCost.part("minecraft:red_bed"), BlockCost.one("minecraft:tall_grass"),
                BlockCost.part("minecraft:tall_grass")));
        assertEquals(Map.of("minecraft:oak_door", 1, "minecraft:red_bed", 1, "minecraft:tall_grass", 1), cost.items());
        assertEquals(3, cost.blocks(), "the parts are placed with their main block");
    }

    @Test
    void doubleSlabsAndCandlesCountTheirItems() {
        TemplateCost cost = TemplateCost.of(List.of(BlockCost.twice("minecraft:oak_slab"), BlockCost.one("minecraft:oak_slab"),
                BlockCost.several("minecraft:candle", 3), BlockCost.several("minecraft:sea_pickle", 4)));
        assertEquals(3, cost.items().get("minecraft:oak_slab"));
        assertEquals(3, cost.items().get("minecraft:candle"));
        assertEquals(4, cost.items().get("minecraft:sea_pickle"));
        assertEquals(10, cost.total());
        assertEquals(4, cost.blocks());
    }

    @Test
    void liquidsAndNothingAreLeftOut() {
        TemplateCost cost = TemplateCost.of(List.of(BlockCost.liquid("minecraft:water"), BlockCost.liquid("minecraft:water"), BlockCost.NOTHING,
                BlockCost.one("minecraft:cobblestone")));
        assertEquals(Map.of("minecraft:cobblestone", 1), cost.items());
        assertEquals(2, cost.liquids());
        assertEquals(1, cost.blocks());
        assertTrue(cost.buildable());
    }

    @Test
    void blocksWithoutItemsMakeTheTemplateUnbuildable() {
        TemplateCost cost = TemplateCost.of(List.of(BlockCost.one("minecraft:stone"), BlockCost.unobtainable("minecraft:bedrock"),
                BlockCost.unobtainable("minecraft:spawner"), BlockCost.unobtainable("minecraft:bedrock")));
        assertFalse(cost.buildable());
        assertEquals(Set.of("minecraft:bedrock", "minecraft:spawner"), cost.unobtainable());
        assertEquals(Map.of("minecraft:stone", 1), cost.items());
    }

    @Test
    void aBlockKnowsWhetherItIsPlaced() {
        assertTrue(BlockCost.one("a:b").placed());
        assertTrue(BlockCost.twice("a:b").placed());
        assertFalse(BlockCost.part("a:b").placed());
        assertFalse(BlockCost.liquid("a:b").placed());
        assertEquals(Map.of(), BlockCost.part("a:b").asMap());
        assertEquals(Map.of("a:b", 2), BlockCost.twice("a:b").asMap());
    }
}
