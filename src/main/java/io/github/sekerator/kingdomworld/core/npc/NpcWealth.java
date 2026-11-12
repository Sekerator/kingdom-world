package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The wealth a kingdom of the world has when it appears, like the chests of villages: the money of its treasury (journaled as
 * its founding), food for its citizens, building materials and the gear of its soldiers in the storehouses. This is the only
 * place where wealth comes from nothing; afterwards the kingdom lives by the usual rules.
 *
 * @param money the money of the treasury, in the smallest unit of its currency
 * @param items items for the storehouses by id, in the order they are put in
 */
public record NpcWealth(long money, Map<String, Integer> items) {
    /** Nutrition of a loaf of bread, as the game counts it. */
    public static final int BREAD_NUTRITION = 5;

    public NpcWealth {
        items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    /**
     * The wealth of a kingdom.
     *
     * @param composition    what the kingdom is made of
     * @param wood           the id of the logs of the wood of its place, for example {@code minecraft:spruce_log}
     * @param planks         the id of the planks of that wood
     * @param dailyNutrition the food a citizen eats a day
     */
    public static NpcWealth of(NpcComposition composition, NpcRules rules, String wood, String planks, int dailyNutrition) {
        NpcLevel level = composition.level();
        Map<String, Integer> items = new LinkedHashMap<>();
        int bread = (int) Math.min(64L * 64, ((long) rules.foodDays() * composition.citizens() * dailyNutrition + BREAD_NUTRITION - 1)
                / BREAD_NUTRITION);
        add(items, "minecraft:bread", bread);
        int stacks = rules.materials(level);
        add(items, wood, stacks * 64);
        add(items, planks, stacks * 64);
        add(items, "minecraft:cobblestone", stacks * 64);
        add(items, "minecraft:iron_ingot", stacks * 4);
        add(items, "minecraft:wheat_seeds", composition.fields() * 16);
        if (composition.districts()) {
            // Those who fell trees and break stone start with their tools, and the fields of carrots with seed carrots.
            add(items, "minecraft:iron_axe", composition.jobs().getOrDefault("lumberjack", 0));
            add(items, "minecraft:iron_pickaxe", composition.jobs().getOrDefault("miner", 0));
            add(items, "minecraft:carrot", composition.fields() * 16);
        }
        if (composition.districts()) {
            // Its garrison gets the kits of its branches and posts at the founding: those are its gear, and the storehouses
            // keep their room for goods.
            return new NpcWealth(rules.money(level), items);
        }
        int soldiers = composition.soldiers();
        int warriors = (soldiers + 1) / 2;
        int archers = soldiers - warriors;
        add(items, level == NpcLevel.SMALL ? "minecraft:stone_sword" : "minecraft:iron_sword", warriors);
        add(items, "minecraft:bow", archers);
        add(items, "minecraft:arrow", archers * 32);
        boolean iron = level == NpcLevel.LARGE;
        add(items, iron ? "minecraft:iron_helmet" : "minecraft:leather_helmet", soldiers);
        add(items, iron ? "minecraft:iron_chestplate" : "minecraft:leather_chestplate", soldiers);
        add(items, iron ? "minecraft:iron_leggings" : "minecraft:leather_leggings", soldiers);
        add(items, iron ? "minecraft:iron_boots" : "minecraft:leather_boots", soldiers);
        if (level != NpcLevel.SMALL) {
            add(items, "minecraft:shield", warriors);
        }
        return new NpcWealth(rules.money(level), items);
    }

    private static void add(Map<String, Integer> items, String item, int count) {
        if (count > 0) {
            items.merge(item, count, Integer::sum);
        }
    }

    /**
     * The items as stacks of at most {@code maxStack} each, for putting them into storehouses.
     */
    public List<Map.Entry<String, Integer>> stacks(int maxStack) {
        List<Map.Entry<String, Integer>> stacks = new ArrayList<>();
        items.forEach((item, count) -> {
            for (int left = count; left > 0; left -= maxStack) {
                stacks.add(Map.entry(item, Math.min(maxStack, left)));
            }
        });
        return stacks;
    }

    public int count(String item) {
        return items.getOrDefault(item, 0);
    }
}
