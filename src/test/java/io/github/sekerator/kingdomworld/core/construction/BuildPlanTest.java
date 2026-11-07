package io.github.sekerator.kingdomworld.core.construction;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order of the work: clearing from the top down before anything is built, then standing blocks from the bottom up,
 * then leaning ones; what is left to pay for, what the site lacks, and how far the building is.
 */
class BuildPlanTest {
    private static final UUID KINGDOM = UUID.randomUUID();

    static Construction construction(BuildPlan plan) {
        return new Construction(1, KINGDOM, "test:house", 7L, new WorldPos("minecraft:overworld", 0, 0, 0), Placement.NONE,
                new Box(0, 0, 0, 9, 9, 9), 0, List.of(), plan.size());
    }

    /**
     * Two clearings and four blocks: a torch on a wall, the wall, a floor and a door.
     */
    static BuildPlan sample() {
        List<int[]> clear = List.of(new int[]{1, 0, 1}, new int[]{1, 3, 1});
        List<PlacementOrder.Block> blocks = List.of(
                new PlacementOrder.Block(0, 1, 2, 0, PlacementOrder.Phase.LEANING),
                new PlacementOrder.Block(1, 1, 1, 0, PlacementOrder.Phase.STANDING),
                new PlacementOrder.Block(2, 0, 0, 0, PlacementOrder.Phase.STANDING),
                new PlacementOrder.Block(3, 2, 1, 0, PlacementOrder.Phase.LEANING));
        List<Map<String, Integer>> costs = List.of(Map.of("minecraft:torch", 1), Map.of("minecraft:oak_planks", 1),
                Map.of("minecraft:cobblestone", 1), Map.of("minecraft:oak_door", 1));
        return BuildPlan.of(clear, blocks, costs);
    }

    @Test
    void clearingComesFirstFromTheTopDown() {
        BuildPlan plan = sample();
        assertEquals(2, plan.clears());
        assertEquals(4, plan.places());
        assertEquals(BuildPlan.Kind.CLEAR, plan.step(0).kind());
        assertEquals(3, plan.step(0).y(), "the top is cleared first");
        assertEquals(0, plan.step(1).y());
    }

    @Test
    void standingBlocksGoUpBeforeLeaningOnes() {
        BuildPlan plan = sample();
        List<Integer> blocks = new ArrayList<>();
        for (int i = plan.clears(); i < plan.size(); i++) {
            blocks.add(plan.step(i).block());
        }
        // The floor, the wall, then the door (lower) and the torch (higher).
        assertEquals(List.of(2, 1, 3, 0), blocks);
    }

    @Test
    void buildingWaitsForTheClearing() {
        BuildPlan plan = sample();
        Construction construction = construction(plan);
        assertEquals(OptionalInt.of(0), plan.next(construction, step -> false));
        // With the first clearing taken by another builder, the second is next; nothing is built while one is left.
        assertEquals(OptionalInt.of(1), plan.next(construction, step -> step == 0));
        assertTrue(plan.next(construction, step -> step <= 1).isEmpty());
        construction.markDone(0);
        construction.markPending(1);
        assertTrue(plan.cleared(construction), "a clearing waiting for the world counts as settled");
        assertEquals(OptionalInt.of(2), plan.next(construction, step -> false));
    }

    @Test
    void remainingAndNeededCountWhatIsPaidAndBrought() {
        BuildPlan plan = sample();
        Construction construction = construction(plan);
        assertEquals(4, plan.remaining(construction).size());
        construction.markDone(0);
        construction.markDone(1);
        construction.markDone(2);
        // The planks are paid for far from players and wait to be placed: they cost nothing more.
        construction.markPending(3);
        assertEquals(Map.of("minecraft:oak_door", 1, "minecraft:torch", 1), plan.remaining(construction));
        construction.deliver("minecraft:torch", 5);
        assertEquals(Map.of("minecraft:oak_door", 1), plan.needed(construction));
        assertEquals(2, plan.built(construction));
        assertEquals(1, plan.placed(construction));
        assertEquals(25, plan.percent(construction));
        construction.markDone(3);
        assertEquals(50, plan.percent(construction));
        assertFalse(plan.finished(construction));
        for (int i = 0; i < plan.size(); i++) {
            construction.markDone(i);
        }
        assertTrue(plan.finished(construction));
        assertEquals(100, plan.percent(construction));
    }
}
