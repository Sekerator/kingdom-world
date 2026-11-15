package io.github.sekerator.kingdomworld.core.production;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batches of the workshops: their size, how they are spread over furnaces, the fuel they burn and the pace of furnaces far from
 * players.
 */
class BatchesTest {
    /** Coal in a furnace: eight smeltings of 200 ticks. */
    private static final int COAL = 1_600;
    private static final int SMELT = 200;

    @Test
    void anOrderAsksForWholeMakings() {
        assertEquals(64, Batches.makings(64, 1));
        assertEquals(3, Batches.makings(9, 4), "nine planks are three makings of four");
        assertEquals(0, Batches.makings(0, 4));
        assertEquals(0, Batches.makings(-3, 1));
    }

    @Test
    void itemsTakeWholeStacksOfTheirOwnSize() {
        Map<String, Integer> load = Map.of("raw_iron", 64, "coal", 9, "iron_ingot", 64, "bucket", 17);
        assertEquals(1 + 1 + 1 + 2, Batches.stacks(load, item -> item.equals("bucket") ? 16 : 64));
        assertEquals(0, Batches.stacks(Map.of("raw_iron", 0), item -> 64));
    }

    @Test
    void aBatchIsAsLargeAsTheBagHolds() {
        // A bag of nine slots: n makings of a recipe taking three wheat and giving a bread need ceil(3n/64) + ceil(n/64) slots.
        int most = Batches.largest(1_000, n -> (3 * n + 63) / 64 + (n + 63) / 64 <= 9);
        assertEquals(128, most);
        assertEquals(64, Batches.largest(64, n -> true), "the batch never goes beyond the most it may hold");
        assertEquals(0, Batches.largest(64, n -> n == 0));
        assertEquals(0, Batches.largest(0, n -> true));
    }

    @Test
    void makingsSpreadEvenlyOverTheFurnaces() {
        assertArrayEquals(new int[]{22, 21, 21}, Batches.spread(64, new int[]{64, 64, 64}));
        assertArrayEquals(new int[]{1, 1, 0}, Batches.spread(2, new int[]{64, 64, 64}));
        // A furnace with little room takes what fits, the others share the rest.
        int[] uneven = Batches.spread(64, new int[]{5, 64, 64});
        assertEquals(5, uneven[0]);
        assertEquals(64, Arrays.stream(uneven).sum());
        assertTrue(Math.abs(uneven[1] - uneven[2]) <= 1);
        // What no furnace has room for is left out.
        assertArrayEquals(new int[]{3, 4}, Batches.spread(100, new int[]{3, 4}));
        assertArrayEquals(new int[]{0, 0}, Batches.spread(0, new int[]{3, 4}));
    }

    @Test
    void fuelIsCountedByTheTimeItBurns() {
        // A stack of raw iron over three furnaces: 22, 21 and 21 smeltings burn three coal each, nine in all, not 64.
        int total = 0;
        for (int count : Batches.spread(64, new int[]{64, 64, 64})) {
            total += Batches.fuel(count, SMELT, 0.0, COAL);
        }
        assertEquals(9, total);
        assertTrue(total <= 64 / 8 + 3);
        assertEquals(1, Batches.fuel(8, SMELT, 0.0, COAL));
        assertEquals(2, Batches.fuel(9, SMELT, 0.0, COAL));
        // Coal lying in the furnace already burns too.
        assertEquals(0, Batches.fuel(8, SMELT, COAL, COAL));
        assertEquals(1, Batches.fuel(16, SMELT, COAL, COAL));
        assertEquals(0, Batches.fuel(0, SMELT, 0.0, COAL));
        // A blast furnace smelts twice as fast and burns its fuel twice as fast: coal is still eight things.
        assertEquals(1, Batches.fuel(8, SMELT / 2, 0.0, COAL / 2));
        // Fuel of another kind: the furnace cooks only what it burns for.
        assertEquals(4, Batches.cookable(800.0, SMELT));
        assertEquals(0, Batches.cookable(0.0, SMELT));
    }

    @Test
    void farFromPlayersAWorkerWithNFurnacesSmeltsNThingsInTheTimeOfOne() {
        ProductionRules rules = ProductionRules.DEFAULT;
        RecipeOption smelting = new RecipeOption("smelting", RecipeKind.SMELTING, "smelter", "iron_ingot", 1,
                List.of(new RecipeOption.Part(List.of("raw_iron"), 1)), SMELT);
        RecipeOption planks = new RecipeOption("planks", RecipeKind.CRAFTING, "crafter", "oak_planks", 4,
                List.of(new RecipeOption.Part(List.of("oak_log"), 1)), 0);
        for (int furnaces = 1; furnaces <= 5; furnaces++) {
            double each = ProductionRules.furnacesEach(furnaces, 1);
            assertEquals(furnaces, makingsIn(SMELT, rules.cost(smelting, 0, 1.0, each)), furnaces + " furnaces");
            // Hand work goes one thing at a time whatever furnaces stand there.
            assertEquals(1, makingsIn(200, rules.cost(planks, 0, 1.0, each)));
        }
        // Two workers share three furnaces: together they smelt three things in the time of one, never fewer than one each.
        assertEquals(1.5, ProductionRules.furnacesEach(3, 2), 1e-9);
        assertEquals(1.0, ProductionRules.furnacesEach(1, 2), 1e-9);
        assertEquals(1.0, ProductionRules.furnacesEach(0, 1), 1e-9);
        // A slow post is slower at furnaces too.
        assertEquals(1, makingsIn(SMELT, rules.cost(smelting, 0, 3.0, 3.0)));
        // A step of work far from players goes into as many makings as its time pays for, as WorkshopBehavior.makeAt does.
        double budget = ProductionRules.workTicks((int) io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY, 1.0);
        int one = makingsIn(budget, rules.cost(smelting, 0, 1.0, 1.0));
        int three = makingsIn(budget, rules.cost(smelting, 0, 1.0, 3.0));
        assertEquals(one * 3, three);
    }

    /**
     * Makings that fit into a time, one after another, as far from players.
     */
    private static int makingsIn(double budget, double cost) {
        int made = 0;
        while (budget + ProductionRules.TICK_SLACK >= cost) {
            budget -= cost;
            made++;
        }
        return made;
    }
}
