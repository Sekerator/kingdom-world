package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grove of a lumberjack: its capacity, the grid of new saplings, the choice of their spots, what it gives a day, and the
 * radius of a lumberjack's zone.
 */
class GrovesTest {

    /**
     * The spots of the grid around a post in a square of the given radius.
     */
    private static List<Groves.Spot> grid(int postX, int postZ, int radius) {
        List<Groves.Spot> spots = new ArrayList<>();
        for (int x = postX - radius; x <= postX + radius; x++) {
            for (int z = postZ - radius; z <= postZ + radius; z++) {
                if (Groves.onGrid(postX, postZ, x, z)) {
                    spots.add(new Groves.Spot(x, z));
                }
            }
        }
        return spots;
    }

    @Test
    void theZoneHoldsOneTreeForEveryTwentyFiveBlocksOfItsGround() {
        assertEquals(43, Groves.capacity(33 * 33), "a zone of radius 16 wholly on the land");
        assertEquals(169, Groves.capacity(65 * 65), "a zone of radius 32");
        assertEquals(0, Groves.capacity(24));
        assertEquals(0, Groves.capacity(-5));
        assertEquals(3, Groves.room(30, 10, 43));
        assertEquals(0, Groves.room(40, 10, 43), "a forest denser than the limit gets no new saplings");
    }

    @Test
    void newSaplingsStandOnASparseGridAroundThePost() {
        assertTrue(Groves.onGrid(100, 200, 100, 200));
        assertTrue(Groves.onGrid(100, 200, 105, 195));
        assertTrue(Groves.onGrid(100, 200, 90, 215));
        assertFalse(Groves.onGrid(100, 200, 103, 200));
        assertFalse(Groves.onGrid(100, 200, 100, 204));
        // Below zero the grid goes on the same way.
        assertTrue(Groves.onGrid(-3, -7, -8, -2));
        assertFalse(Groves.onGrid(-3, -7, -9, -2));
        assertEquals(49, grid(0, 0, 16).size(), "seven spots along each side of a zone of radius 16");
        assertTrue(Groves.SPACING >= Groves.MIN_GAP, "two spots of the grid are never too close to each other");
    }

    @Test
    void newSaplingsKeepTheirDistanceFromTreesAndSaplings() {
        List<Groves.Spot> candidates = grid(0, 0, 10);
        // A tree right beside the spot of the post and a sapling three blocks from the spot (5, 5).
        List<Groves.Spot> stands = List.of(new Groves.Spot(1, 1), new Groves.Spot(8, 8));
        List<Groves.Spot> chosen = Groves.choose(0, 0, candidates, stands, 100, 100);
        assertFalse(chosen.contains(new Groves.Spot(0, 0)), "too close to the tree");
        assertFalse(chosen.contains(new Groves.Spot(5, 5)), "too close to the sapling");
        assertFalse(chosen.contains(new Groves.Spot(10, 10)), "too close to the sapling");
        assertTrue(chosen.contains(new Groves.Spot(5, 0)), "four blocks from the tree is far enough");
        assertTrue(chosen.contains(new Groves.Spot(-5, -5)));
        assertFalse(chosen.contains(new Groves.Spot(10, 5)), "too close to the sapling");
        assertEquals(candidates.size() - 5, chosen.size(), "only (0, 0), (5, 5), (10, 10), (5, 10) and (10, 5) are too close");
        for (Groves.Spot spot : chosen) {
            for (Groves.Spot stand : stands) {
                assertTrue(Math.abs(spot.x() - stand.x()) >= Groves.MIN_GAP || Math.abs(spot.z() - stand.z()) >= Groves.MIN_GAP,
                        spot + " is too close to " + stand);
            }
        }
    }

    @Test
    void newSaplingsGoNearestToThePostFirstAndNoMoreThanTheRoom() {
        List<Groves.Spot> candidates = grid(50, 50, 15);
        List<Groves.Spot> chosen = Groves.choose(50, 50, candidates, List.of(), 3, Groves.NEW_PER_ROUND);
        assertEquals(List.of(new Groves.Spot(50, 50), new Groves.Spot(45, 50), new Groves.Spot(50, 45)), chosen);
        assertEquals(Groves.NEW_PER_ROUND, Groves.choose(50, 50, candidates, List.of(), 100, Groves.NEW_PER_ROUND).size());
        assertTrue(Groves.choose(50, 50, candidates, List.of(), 0, Groves.NEW_PER_ROUND).isEmpty(), "a full zone gets none");
        // Candidates that are not on one grid still keep their distance from each other.
        List<Groves.Spot> close = List.of(new Groves.Spot(0, 0), new Groves.Spot(2, 3), new Groves.Spot(4, 0));
        assertEquals(List.of(new Groves.Spot(0, 0), new Groves.Spot(4, 0)), Groves.choose(0, 0, close, List.of(), 10, 10));
    }

    @Test
    void aGrovePlantedAgainNeverRunsOut() {
        // Six trees, three saplings and room for three of ten free spots: twelve trees every day and a half.
        assertEquals(8.0, Groves.treesPerDay(6, 3, 10, 12), 1e-9);
        // A grove cut bare still gives what its saplings and free spots will grow into.
        assertEquals(2.0, Groves.treesPerDay(0, 3, 0, 12), 1e-9);
        assertEquals(10.0 / 3.0, Groves.treesPerDay(0, 0, 5, 12), 1e-9);
        // No more than one lumberjack fells.
        assertEquals(Groves.MAX_TREES_PER_DAY, Groves.treesPerDay(60, 0, 0, 40), 1e-9);
        assertEquals(0.0, Groves.treesPerDay(0, 0, 0, 12), 1e-9);
        // A forest denser than the limit is planted again tree for tree, without new saplings.
        assertEquals(20.0 / 1.5, Groves.treesPerDay(20, 0, 9, 10), 1e-9);
    }

    @Test
    void theYieldOfAGroveIsAFloorForItsRates() {
        PostYield measured = new PostYield(Map.of("minecraft:oak_log", 20.0, "minecraft:stick", 6.0), Map.of(), 25.0, true);
        PostYield grove = new PostYield(Map.of("minecraft:oak_log", 40.0), Map.of(), 40.0, false);
        PostYield raised = measured.atLeast(grove);
        assertEquals(40.0, raised.outputs().get("minecraft:oak_log"), 1e-9);
        assertEquals(6.0, raised.outputs().get("minecraft:stick"), 1e-9);
        assertEquals(40.0, raised.actions(), 1e-9);
        assertTrue(raised.measured(), "the rates stay measured");
        PostYield kept = new PostYield(Map.of("minecraft:oak_log", 60.0), Map.of(), 60.0, true).atLeast(grove);
        assertEquals(60.0, kept.outputs().get("minecraft:oak_log"), 1e-9, "a floor never lowers the rates");
        PostYield estimated = PostYield.NONE.atLeast(grove);
        assertEquals(grove.outputs(), estimated.outputs());
        assertFalse(estimated.measured());
    }

    @Test
    void lumberjacksHaveARadiusOfTheirOwn() {
        GatheringRules rules = GatheringRules.DEFAULT;
        assertEquals(16, rules.defaultRadius("lumberjack"));
        assertEquals(32, rules.maxRadius("lumberjack"));
        assertEquals(8, rules.defaultRadius("miner"));
        assertEquals(16, rules.maxRadius("fisher"));
        assertFalse(GatheringRules.boundToLand("lumberjack"));
        assertTrue(GatheringRules.boundToLand("miner"));
        assertTrue(GatheringRules.boundToLand("unemployed"));
        // A lumberjack's zone is not cut to the land; the zones of the others are.
        assertEquals(16, rules.radiusForNewPost("lumberjack", 3));
        assertEquals(3, rules.radiusForNewPost("miner", 3));
        assertEquals(1, rules.radiusForNewPost("miner", 0));
        // Taking up a profession: a lumberjack's post grows to its default, the others stay within their largest radius.
        assertEquals(16, rules.radiusOnProfession("lumberjack", 8));
        assertEquals(24, rules.radiusOnProfession("lumberjack", 24));
        assertEquals(32, rules.radiusOnProfession("lumberjack", 40));
        assertEquals(16, rules.radiusOnProfession("fisher", 32));
        assertEquals(5, rules.radiusOnProfession("fisher", 5));
        assertThrows(IllegalArgumentException.class,
                () -> new GatheringRules(8, 16, 32, 16, 4, 160, 4, 12, 48, 64, 1.0, TaskRisks.DEFAULTS));
        assertThrows(IllegalArgumentException.class,
                () -> new GatheringRules(8, 16, 0, 16, 4, 160, 4, 12, 48, 64, 1.0, TaskRisks.DEFAULTS));
    }
}
