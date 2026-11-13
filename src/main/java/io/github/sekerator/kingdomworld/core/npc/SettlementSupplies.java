package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.land.Tier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a settlement of a state is given when it rises, once, beyond the food and the wood and stone of its founding: a tool in
 * the hands of every worker who needs one, the materials its workshops work with, seed for its fields and a few spare tools in
 * its storehouse. Like the founding wealth of a capital, this is the only time these things come from nothing; afterwards the
 * settlement lives by its own work, its storehouses and the treasury of its kingdom.
 */
public final class SettlementSupplies {
    private SettlementSupplies() {
    }

    /**
     * The tool a worker of a profession works with, of stone (of iron in a large city), or {@code null} for a profession that
     * works with its hands or at its stations.
     */
    public static String tool(String profession, Tier tier) {
        boolean iron = tier == Tier.CITY;
        return switch (profession) {
            case "lumberjack" -> iron ? "minecraft:iron_axe" : "minecraft:stone_axe";
            case "miner", "builder" -> iron ? "minecraft:iron_pickaxe" : "minecraft:stone_pickaxe";
            case "farmer" -> iron ? "minecraft:iron_hoe" : "minecraft:stone_hoe";
            case "fisher" -> "minecraft:fishing_rod";
            case "herder" -> "minecraft:shears";
            default -> null;
        };
    }

    /**
     * Food for the people of a settlement for some days, in loaves of bread, at most a stack of 64 for every 4 people and never
     * more than 16 stacks.
     *
     * @param nutrition the food a citizen eats a day
     * @param loaf      the food of a loaf
     */
    public static int bread(int people, int days, int nutrition, int loaf) {
        if (people <= 0 || days <= 0 || nutrition <= 0 || loaf <= 0) {
            return 0;
        }
        long needed = ((long) days * people * nutrition + loaf - 1) / loaf;
        return (int) Math.min(64L * 16, needed);
    }

    /**
     * What goes into the storehouse of a settlement for its work: the materials of its workshops by their workers, seed for its
     * fields and spare tools by its rank.
     *
     * @param workers the workers of the settlement by profession
     * @param fields  its fields
     * @param planks  the planks of the wood of its kingdom
     */
    public static Map<String, Integer> stores(Tier tier, Map<String, Integer> workers, int fields, String planks) {
        Map<String, Integer> items = new LinkedHashMap<>();
        int smiths = workers.getOrDefault("smith", 0);
        add(items, "minecraft:iron_ingot", 16 * smiths);
        add(items, "minecraft:coal", 32 * smiths);
        int cooks = workers.getOrDefault("cook", 0) + workers.getOrDefault("innkeeper", 0);
        add(items, "minecraft:wheat", 32 * cooks);
        add(items, "minecraft:potato", 16 * cooks);
        int crafters = workers.getOrDefault("crafter", 0);
        add(items, planks, 32 * crafters);
        add(items, "minecraft:white_wool", 8 * crafters);
        add(items, "minecraft:wheat_seeds", 16 * fields);
        add(items, "minecraft:carrot", 8 * fields);
        int spare = switch (tier) {
            case CITY -> 2;
            case TOWN -> 1;
            default -> 0;
        };
        for (String profession : new String[]{"lumberjack", "miner", "farmer"}) {
            add(items, tool(profession, tier), spare);
        }
        if (tier == Tier.VILLAGE || tier == Tier.HAMLET) {
            add(items, tool("farmer", tier), 1);
        }
        return items;
    }

    private static void add(Map<String, Integer> items, String item, int count) {
        if (item != null && count > 0) {
            items.merge(item, count, Integer::sum);
        }
    }
}
