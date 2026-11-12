package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcWealthTest {
    private static final NpcRules RULES = NpcCompositionTest.GRID_RULES;

    private static NpcComposition of(NpcLevel level) {
        return NpcComposition.of(level, 11L, RULES, 12, 6, 40, 20, 24);
    }

    @Test
    void aCapitalOfDistrictsStartsWithToolsBreadAndItsMoney() {
        for (NpcLevel level : NpcLevel.values()) {
            NpcRules rules = NpcRules.DEFAULT;
            NpcComposition composition = NpcComposition.districts(level, 21L, rules, rules.towns(level));
            NpcWealth wealth = NpcWealth.of(composition, rules, "minecraft:birch_log", "minecraft:birch_planks", 6);
            assertEquals(rules.money(level), wealth.money(), level.id());
            int lumberjacks = composition.jobs().getOrDefault("lumberjack", 0);
            int miners = composition.jobs().getOrDefault("miner", 0);
            assertTrue(lumberjacks > 0 && miners > 0, level + ": " + composition.jobs());
            assertEquals(lumberjacks, wealth.count("minecraft:iron_axe"));
            assertEquals(miners, wealth.count("minecraft:iron_pickaxe"));
            assertTrue(wealth.count("minecraft:bread") > 0);
            assertEquals(composition.fields() * 16, wealth.count("minecraft:carrot"));
            assertEquals(rules.materials(level) * 64, wealth.count("minecraft:birch_log"));
        }
        // The grid has no tools.
        assertEquals(0, NpcWealth.of(of(NpcLevel.LARGE), RULES, "minecraft:oak_log", "minecraft:oak_planks", 6).count("minecraft:iron_axe"));
    }

    @Test
    void aStyledCapitalStartsWithToolsAndSeedCarrots() {
        for (NpcLevel level : NpcLevel.values()) {
            NpcRules rules = NpcRules.DEFAULT;
            NpcComposition composition = NpcComposition.styled(level, 21L, "eastern", GuildHouses.defaultOf(level), rules, rules.towns(level));
            NpcWealth wealth = NpcWealth.of(composition, rules, "minecraft:spruce_log", "minecraft:spruce_planks", 6);
            assertEquals(rules.money(level), wealth.money(), level.id());
            int lumberjacks = composition.jobs().getOrDefault("lumberjack", 0);
            int miners = composition.jobs().getOrDefault("miner", 0);
            assertTrue(lumberjacks > 0 && miners > 0, level + ": " + composition.jobs());
            assertEquals(lumberjacks, wealth.count("minecraft:iron_axe"));
            assertEquals(miners, wealth.count("minecraft:iron_pickaxe"));
            assertEquals(composition.fields() * 16, wealth.count("minecraft:carrot"));
            assertEquals(0, wealth.count("minecraft:iron_sword") + wealth.count("minecraft:stone_sword") + wealth.count("minecraft:bow"),
                    "the garrison gets its kits as that of a capital of districts");
        }
    }

    @Test
    void theTreasuryAndTheStoresGrowWithTheKingdom() {
        NpcWealth small = NpcWealth.of(of(NpcLevel.SMALL), RULES, "minecraft:spruce_log", "minecraft:spruce_planks", 6);
        NpcWealth large = NpcWealth.of(of(NpcLevel.LARGE), RULES, "minecraft:spruce_log", "minecraft:spruce_planks", 6);
        assertEquals(RULES.money(NpcLevel.SMALL), small.money());
        assertEquals(RULES.money(NpcLevel.LARGE), large.money());
        assertTrue(large.count("minecraft:bread") > small.count("minecraft:bread"));
        assertEquals(RULES.materials(NpcLevel.SMALL) * 64, small.count("minecraft:spruce_log"));
        assertEquals(RULES.materials(NpcLevel.LARGE) * 64, large.count("minecraft:cobblestone"));
    }

    @Test
    void foodLastsTheDaysOfTheRules() {
        NpcComposition composition = of(NpcLevel.MEDIUM);
        NpcWealth wealth = NpcWealth.of(composition, RULES, "minecraft:oak_log", "minecraft:oak_planks", 6);
        long needed = (long) RULES.foodDays() * composition.citizens() * 6;
        long bread = wealth.count("minecraft:bread");
        assertTrue(bread * NpcWealth.BREAD_NUTRITION >= needed);
        assertTrue((bread - 1) * NpcWealth.BREAD_NUTRITION < needed, "no more than the days need");
    }

    @Test
    void everySoldierHasAWeaponAndArmor() {
        for (NpcLevel level : NpcLevel.values()) {
            NpcComposition composition = of(level);
            NpcWealth wealth = NpcWealth.of(composition, RULES, "minecraft:oak_log", "minecraft:oak_planks", 6);
            // The garrison of a capital of districts gets the kits of its branches and posts at the founding (the army issues
            // them): its wealth holds no other gear of soldiers. A kingdom of the first layout arms its handful from its stores.
            int soldiers = composition.districts() ? 0 : composition.soldiers();
            int weapons = wealth.count("minecraft:stone_sword") + wealth.count("minecraft:iron_sword") + wealth.count("minecraft:bow");
            assertEquals(soldiers, weapons, level.id());
            int archers = wealth.count("minecraft:bow");
            assertEquals(archers * 32, wealth.count("minecraft:arrow"));
            String chest = level == NpcLevel.LARGE ? "minecraft:iron_chestplate" : "minecraft:leather_chestplate";
            assertEquals(soldiers, wealth.count(chest));
        }
    }

    @Test
    void stacksSplitWithoutLosingItems() {
        NpcWealth wealth = new NpcWealth(10, Map.of("minecraft:bread", 130));
        List<Map.Entry<String, Integer>> stacks = wealth.stacks(64);
        assertEquals(List.of(64, 64, 2), stacks.stream().map(Map.Entry::getValue).toList());
    }

    @Test
    void aStateKeepsItsPlacesKingdomsAndPlan() {
        NpcState state = new NpcState();
        int[] changes = new int[1];
        state.setChangeListener(() -> changes[0]++);
        NpcSite site = state.addSite(100, 200, NpcLevel.SMALL, 7L, List.of(new NpcSite.Town(300, 200)));
        assertEquals(1, site.id());
        site.setStatus(NpcSite.Status.SPAWNING);
        assertEquals(site, state.spawning().orElseThrow());
        UUID id = UUID.randomUUID();
        state.putKingdom(NpcKingdom.create(id, site, new NpcNames.Name("Zlatoslavia", "Златославия"), List.of(), 0, 0));
        assertTrue(state.isNpc(id));
        site.setKingdomId(id);
        site.setStatus(NpcSite.Status.SPAWNED);
        state.addSite(5000, 0, NpcLevel.LARGE, 8L, List.of());
        assertEquals(1, state.resetPlan(), "only places still waiting are forgotten");
        assertEquals(1, state.sites().size());
        assertTrue(state.verify().isEmpty());
        assertTrue(changes[0] > 5);
        state.removeKingdom(id);
        assertEquals(1, state.verify().size(), "an appeared place without its kingdom is reported");
    }
}
