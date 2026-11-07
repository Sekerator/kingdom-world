package io.github.sekerator.kingdomworld.core.construction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Work far from players: as many steps as the time allows, every block paid for in one go or not at all, the rest of the
 * time kept for the next step, and experience making a builder faster.
 */
class AbstractProgressTest {
    private static final ConstructionRules RULES = new ConstructionRules(4, 64, 4.5, 12.0, 300, 40, 0.0, 48, 32, 48);

    /**
     * A store of items that pays a block only if it holds all of its items.
     */
    private static final class Store {
        final Map<String, Integer> items = new HashMap<>();
        final List<Map<String, Integer>> paid = new ArrayList<>();

        boolean pay(Map<String, Integer> cost) {
            for (Map.Entry<String, Integer> entry : cost.entrySet()) {
                if (items.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                    return false;
                }
            }
            cost.forEach((item, count) -> items.merge(item, -count, Integer::sum));
            paid.add(cost);
            return true;
        }
    }

    @Test
    void timeBuysStepsAndTheRestIsKept() {
        BuildPlan plan = BuildPlanTest.sample();
        Construction construction = BuildPlanTest.construction(plan);
        Store store = new Store();
        store.items.put("minecraft:cobblestone", 1);
        store.items.put("minecraft:oak_planks", 1);
        ConstructionRules.Progress progress = ConstructionRules.work(plan, construction, 3.5, store::pay);
        // Two clearings cost nothing, then the floor is paid.
        assertEquals(3, progress.steps());
        assertFalse(progress.starved());
        assertEquals(0.5, progress.carry(), 1e-9);
        assertEquals(List.of(0, 1, 2), construction.pending());
        assertEquals(0, store.items.get("minecraft:cobblestone"));
    }

    @Test
    void aMissingItemStopsTheWorkWithoutPayingHalf() {
        BuildPlan plan = BuildPlanTest.sample();
        Construction construction = BuildPlanTest.construction(plan);
        Store store = new Store();
        store.items.put("minecraft:cobblestone", 1);
        store.items.put("minecraft:oak_planks", 1);
        ConstructionRules.Progress progress = ConstructionRules.work(plan, construction, 100.0, store::pay);
        assertEquals(4, progress.steps(), "two clearings, the floor and the wall");
        assertTrue(progress.starved(), "the door is missing");
        assertEquals(0.0, progress.carry());
        assertEquals(2, store.paid.size());
        assertFalse(construction.isPending(4), "the door is not paid");
        // Once the door is there, the work goes on where it stopped.
        store.items.put("minecraft:oak_door", 1);
        store.items.put("minecraft:torch", 1);
        ConstructionRules.Progress more = ConstructionRules.work(plan, construction, 100.0, store::pay);
        assertEquals(2, more.steps());
        assertFalse(more.starved());
        assertEquals(6, construction.pending().size());
        assertTrue(ConstructionRules.work(plan, construction, 100.0, store::pay).steps() == 0, "nothing is paid twice");
    }

    @Test
    void lessThanOneBlockOfTimeDoesNothing() {
        BuildPlan plan = BuildPlanTest.sample();
        Construction construction = BuildPlanTest.construction(plan);
        ConstructionRules.Progress progress = ConstructionRules.work(plan, construction, 0.9, cost -> true);
        assertEquals(0, progress.steps());
        assertEquals(0.9, progress.carry(), 1e-9);
    }

    @Test
    void experienceMakesABuilderUpToTwiceAsFast() {
        assertEquals(40.0, RULES.ticksPerBlock(0), 1e-9);
        assertEquals(20.0, RULES.ticksPerBlock(ConstructionRules.VETERAN_EXPERIENCE), 1e-9);
        assertEquals(20.0, RULES.ticksPerBlock(1_000_000), 1e-9);
        assertEquals(30.0, RULES.blocks(1_200, 0, 1.0), 1e-9);
        assertEquals(60.0, RULES.blocks(1_200, 1_000, 1.0), 1e-9);
        assertEquals(36.0, RULES.blocks(1_200, 0, 1.2), 1e-9);
    }

    @Test
    void templatesLargerThanTheServerAllowsDoNotFit() {
        assertTrue(RULES.fits(48, 32, 48));
        assertFalse(RULES.fits(49, 10, 10));
        assertFalse(RULES.fits(10, 33, 10));
    }
}
