package io.github.sekerator.kingdomworld.core.construction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builders working together: the layers of a plan they share out, the support a block waits for, the step each takes, and the
 * work far from players that pays for every step once.
 */
class CrewTest {

    /**
     * A floor of 8 blocks along x, a wall of 8 blocks on it and a torch on the side of the wall's first block; a block to clear
     * over another one.
     */
    private static BuildPlan wall() {
        List<PlacementOrder.Block> blocks = new ArrayList<>();
        List<Map<String, Integer>> costs = new ArrayList<>();
        for (int x = 0; x < 8; x++) {
            blocks.add(new PlacementOrder.Block(blocks.size(), x, 0, 0, PlacementOrder.Phase.STANDING));
            costs.add(Map.of("minecraft:cobblestone", 1));
            blocks.add(new PlacementOrder.Block(blocks.size(), x, 1, 0, PlacementOrder.Phase.STANDING));
            costs.add(Map.of("minecraft:oak_planks", 1));
        }
        blocks.add(new PlacementOrder.Block(blocks.size(), 0, 1, 1, PlacementOrder.Phase.LEANING));
        costs.add(Map.of("minecraft:torch", 1));
        return BuildPlan.of(List.of(new int[]{3, 5, 3}, new int[]{3, 4, 3}), blocks, costs);
    }

    private static int stepAt(BuildPlan plan, BuildPlan.Kind kind, int x, int y, int z) {
        for (BuildPlan.Step step : plan.steps()) {
            if (step.kind() == kind && step.x() == x && step.y() == y && step.z() == z) {
                return step.index();
            }
        }
        throw new IllegalArgumentException("No step at " + x + "," + y + "," + z);
    }

    @Test
    void aLayerIsOneTierAndWhatComesAfterItAnother() {
        BuildPlan plan = wall();
        int floor = stepAt(plan, BuildPlan.Kind.PLACE, 0, 0, 0);
        int wall = stepAt(plan, BuildPlan.Kind.PLACE, 0, 1, 0);
        int torch = stepAt(plan, BuildPlan.Kind.PLACE, 0, 1, 1);
        for (int x = 1; x < 8; x++) {
            assertEquals(plan.tier(floor), plan.tier(stepAt(plan, BuildPlan.Kind.PLACE, x, 0, 0)), "the floor is one tier");
            assertEquals(plan.tier(wall), plan.tier(stepAt(plan, BuildPlan.Kind.PLACE, x, 1, 0)), "a course of the wall is one tier");
        }
        assertTrue(plan.tier(floor) < plan.tier(wall));
        assertTrue(plan.tier(wall) < plan.tier(torch), "a leaning block of the same height comes after the standing ones");
        assertNotEquals(plan.tier(0), plan.tier(1), "each height of clearing is its own tier");
        assertTrue(plan.tier(1) < plan.tier(floor));
    }

    @Test
    void aBlockWaitsForItsSupportAndClearingForWhatIsOverIt() {
        BuildPlan plan = wall();
        Construction construction = BuildPlanTest.construction(plan);
        int floor = stepAt(plan, BuildPlan.Kind.PLACE, 0, 0, 0);
        int wall = stepAt(plan, BuildPlan.Kind.PLACE, 0, 1, 0);
        int torch = stepAt(plan, BuildPlan.Kind.PLACE, 0, 1, 1);
        assertFalse(plan.waits(construction, floor));
        assertTrue(plan.waits(construction, wall), "the wall waits for the floor under it");
        assertTrue(plan.waits(construction, torch), "the torch waits for the wall beside it");
        construction.markPending(floor);
        assertFalse(plan.waits(construction, wall), "a block paid for and waiting for the world is as good as there");
        construction.markDone(wall);
        assertFalse(plan.waits(construction, torch));
        assertTrue(plan.waits(construction, 1), "the lower block to clear waits for the one over it");
        construction.markDone(0);
        assertFalse(plan.waits(construction, 1));
    }

    @Test
    void oneLookOverThePlanGivesTheStepsNextGivesOneAfterAnother() {
        BuildPlan plan = wall();
        Construction construction = BuildPlanTest.construction(plan);
        java.util.function.IntPredicate busy = index -> index % 3 == 1;
        List<Integer> expected = new ArrayList<>();
        java.util.Set<Integer> taken = new java.util.HashSet<>();
        for (java.util.OptionalInt next = plan.next(construction, busy); next.isPresent();
             next = plan.next(construction, index -> busy.test(index) || taken.contains(index))) {
            expected.add(next.getAsInt());
            taken.add(next.getAsInt());
        }
        List<Integer> seen = new ArrayList<>();
        assertFalse(plan.forEachNext(construction, busy, step -> seen.add(step.index())));
        assertEquals(expected, seen);
        assertEquals(List.of(0), seen.subList(0, 1), "the first clearing comes first");
        assertFalse(seen.contains(plan.clears()), "nothing is built while a clearing is left");
        // Clearing done, building begins; the visitor may stop early.
        construction.markDone(0);
        construction.markDone(1);
        List<Integer> two = new ArrayList<>();
        assertTrue(plan.forEachNext(construction, index -> false, step -> two.add(step.index()) && two.size() < 2));
        assertEquals(List.of(plan.clears(), plan.clears() + 1), two);
    }

    @Test
    void aBuilderAloneTakesTheStepsInTheirOrder() {
        BuildPlan plan = wall();
        List<BuildPlan.Step> floor = plan.steps().subList(plan.clears(), plan.clears() + 8);
        assertEquals(0, Crew.pick(floor, List.of(), 7.5, 0.5));
    }

    @Test
    void aSecondBuilderTakesTheNearestStepAwayFromTheFirst() {
        BuildPlan plan = wall();
        List<BuildPlan.Step> floor = new ArrayList<>(plan.steps().subList(plan.clears(), plan.clears() + 8));
        // The first builder lays the block at x 0; the second, standing at x 2, takes the nearest block at least 4 away from it.
        assertEquals(4, floor.get(Crew.pick(floor, List.of(new int[]{0, 0}), 2.5, -1.5)).x());
        // Standing at the far end it takes the block there.
        assertEquals(7, floor.get(Crew.pick(floor, List.of(new int[]{0, 0}), 9.5, -1.5)).x());
        // With builders at both ends the middle is the farthest place left, even if not 4 away.
        List<BuildPlan.Step> middle = floor.subList(2, 6);
        int pick = Crew.pick(middle, List.of(new int[]{0, 0}, new int[]{7, 0}), 0.5, -1.5);
        assertTrue(middle.get(pick).x() == 3 || middle.get(pick).x() == 4, "took " + middle.get(pick).x());
    }

    @Test
    void twoBuildersFarFromPlayersPayEveryStepOnce() {
        BuildPlan plan = wall();
        Construction construction = BuildPlanTest.construction(plan);
        Map<String, Integer> paid = new HashMap<>();
        int[] payments = {0};
        java.util.function.Predicate<Map<String, Integer>> pay = cost -> {
            payments[0]++;
            cost.forEach((item, count) -> paid.merge(item, count, Integer::sum));
            return true;
        };
        // Two builders by turns, each with a budget of a few blocks, until the plan is paid for.
        for (int round = 0; round < 10 && plan.next(construction, step -> false).isPresent(); round++) {
            ConstructionRules.work(plan, construction, 3.0, pay);
            ConstructionRules.work(plan, construction, 3.0, pay);
        }
        assertTrue(plan.next(construction, step -> false).isEmpty());
        assertEquals(plan.size(), construction.pending().size(), "every step is paid for and waits for the world");
        assertEquals(plan.places(), payments[0], "every block is paid for once");
        assertEquals(Map.of("minecraft:cobblestone", 8, "minecraft:oak_planks", 8, "minecraft:torch", 1), paid);
    }
}
