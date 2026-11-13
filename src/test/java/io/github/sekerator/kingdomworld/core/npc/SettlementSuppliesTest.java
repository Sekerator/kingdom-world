package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.land.Tier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementSuppliesTest {
    @Test
    void everyWorkerWhoNeedsAToolGetsTheToolOfHisWork() {
        assertEquals("minecraft:stone_axe", SettlementSupplies.tool("lumberjack", Tier.VILLAGE));
        assertEquals("minecraft:iron_axe", SettlementSupplies.tool("lumberjack", Tier.CITY), "a large city gives iron");
        assertEquals("minecraft:stone_pickaxe", SettlementSupplies.tool("miner", Tier.TOWN));
        assertEquals("minecraft:fishing_rod", SettlementSupplies.tool("fisher", Tier.HAMLET));
        assertEquals("minecraft:stone_hoe", SettlementSupplies.tool("farmer", Tier.VILLAGE));
        assertNull(SettlementSupplies.tool("merchant", Tier.TOWN), "a merchant works without a tool");
        assertNull(SettlementSupplies.tool("smith", Tier.CITY), "a smith works at his stations");
    }

    @Test
    void foodForTheDaysOfItsPeople() {
        // 20 people eating 10 a day for 5 days, 5 a loaf: 200 loaves.
        assertEquals(200, SettlementSupplies.bread(20, 5, 10, 5));
        assertEquals(0, SettlementSupplies.bread(0, 5, 10, 5));
        assertEquals(64 * 16, SettlementSupplies.bread(10_000, 30, 20, 5), "never more than 16 stacks");
    }

    @Test
    void theStoresFollowTheWorkOfTheSettlement() {
        Map<String, Integer> town = SettlementSupplies.stores(Tier.TOWN, Map.of("smith", 1, "cook", 1, "crafter", 1, "farmer", 4), 2,
                "minecraft:spruce_planks");
        assertEquals(16, town.get("minecraft:iron_ingot"), "iron for its smith");
        assertTrue(town.get("minecraft:coal") > 0 && town.get("minecraft:wheat") > 0);
        assertEquals(32, town.get("minecraft:spruce_planks"), "planks of the wood of its kingdom for its crafter");
        assertEquals(32, town.get("minecraft:wheat_seeds"), "seed for its two fields");
        assertEquals(1, town.get("minecraft:stone_axe"), "a spare axe");
        Map<String, Integer> village = SettlementSupplies.stores(Tier.VILLAGE, Map.of("farmer", 4), 2, "minecraft:oak_planks");
        assertFalse(village.containsKey("minecraft:iron_ingot"), "no smith, no iron");
        assertEquals(1, village.get("minecraft:stone_hoe"), "a spare hoe");
        Map<String, Integer> city = SettlementSupplies.stores(Tier.CITY, Map.of(), 4, "minecraft:oak_planks");
        assertEquals(2, city.get("minecraft:iron_pickaxe"), "spare tools of iron");
    }
}
