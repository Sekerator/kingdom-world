package io.github.sekerator.kingdomworld.core.production;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pace of work, fuel and food of the workshops.
 */
class ProductionRulesTest {
    private static final RecipeOption.Part LOG = new RecipeOption.Part(List.of("oak_log"), 1);

    @Test
    void experienceMakesHandWorkUpToTwiceAsFast() {
        ProductionRules rules = ProductionRules.DEFAULT;
        RecipeOption planks = new RecipeOption("planks", RecipeKind.CRAFTING, "crafter", "oak_planks", 4, List.of(LOG), 0);
        assertEquals(200, rules.ticks(planks, 0));
        assertEquals(100, rules.ticks(planks, ProductionRules.MASTER_EXPERIENCE * 5));
        assertTrue(rules.ticks(planks, 500) < 200 && rules.ticks(planks, 500) > 100);
        assertEquals(1.0, ProductionRules.speed(-10), 1e-9);
    }

    @Test
    void cookingTakesTheTimeOfTheRecipe() {
        ProductionRules rules = ProductionRules.DEFAULT;
        RecipeOption charcoal = new RecipeOption("charcoal", RecipeKind.SMELTING, "smelter", "charcoal", 1, List.of(LOG), 200);
        RecipeOption blast = new RecipeOption("blast", RecipeKind.BLASTING, "smelter", "charcoal", 1, List.of(LOG), 100);
        assertEquals(200, rules.ticks(charcoal, ProductionRules.MASTER_EXPERIENCE));
        assertEquals(100, rules.ticks(blast, 0));
        RecipeOption instant = new RecipeOption("instant", RecipeKind.SMELTING, "smelter", "charcoal", 1, List.of(LOG), 1);
        assertEquals(ProductionRules.MIN_TICKS, rules.ticks(instant, 0));
    }

    @Test
    void workersWorkHalfOfEveryStep() {
        double ticks = ProductionRules.workTicks((int) CitizenNeeds.TICKS_PER_DAY, 1.0);
        assertEquals(GatheringRules.WORK_TICKS_PER_DAY, ticks, 1e-6);
        assertEquals(ticks * 0.75, ProductionRules.workTicks((int) CitizenNeeds.TICKS_PER_DAY, 0.75), 1e-6);
        assertEquals(0.0, ProductionRules.workTicks(-5, 1.0), 1e-9);
    }

    @Test
    void aSlowMeasurementSlowsWorkFarFromPlayers() {
        ProductionRules rules = ProductionRules.DEFAULT;
        double estimated = GatheringRules.WORK_TICKS_PER_DAY / (double) rules.craftTicks();
        assertEquals(1.0, rules.slowdown(0.0), 1e-9);
        assertEquals(1.0, rules.slowdown(estimated * 2), 1e-9, "faster than the estimate counts as the estimate");
        assertEquals(2.0, rules.slowdown(estimated / 2), 1e-9);
        assertEquals(ProductionRules.MAX_SLOWDOWN, rules.slowdown(0.001), 1e-9);
    }

    @Test
    void fuelIsBurnedInWholeItems() {
        assertEquals(0, ProductionRules.fuelItems(0.0, 1600));
        assertEquals(1, ProductionRules.fuelItems(200.0, 1600));
        assertEquals(1, ProductionRules.fuelItems(1600.0, 1600));
        assertEquals(2, ProductionRules.fuelItems(1601.0, 1600));
        assertEquals(0, ProductionRules.fuelItems(200.0, 0));
    }

    @Test
    void foodLastsForDays() {
        assertEquals(5.0, ProductionRules.foodDays(300, 10, 6), 1e-9);
        assertEquals(Double.POSITIVE_INFINITY, ProductionRules.foodDays(0, 0, 6));
        assertThrows(IllegalArgumentException.class, () -> new ProductionRules(0, 5, 3, 32));
    }
}
