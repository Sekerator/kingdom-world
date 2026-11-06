package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenNeedsTest {
    private static final CitizenRules RULES = CitizenRules.DEFAULT;

    @Test
    void hungerFollowsTheDailyNeed() {
        assertEquals(6.0f, CitizenNeeds.hunger(6, 24_000), 1e-4);
        assertEquals(0.3f, CitizenNeeds.hunger(6, 1_200), 1e-4);
        assertEquals(0, CitizenNeeds.nutritionWanted(15.0f));
        assertEquals(7, CitizenNeeds.nutritionWanted(13.5f));
        assertEquals(20, CitizenNeeds.nutritionWanted(0.0f));
    }

    @Test
    void foodDaysCountTheWholePopulation() {
        assertEquals(3.0, CitizenNeeds.foodDays(180, 10, 6), 1e-9);
        // An empty kingdom is measured for one citizen.
        assertEquals(5.0, CitizenNeeds.foodDays(30, 0, 6), 1e-9);
        assertEquals(0.0, CitizenNeeds.foodDays(-5, 3, 6), 1e-9);
        assertTrue(Double.isInfinite(CitizenNeeds.foodDays(0, 3, 0)));
    }

    @Test
    void growthNeedsABedFoodAndTime() {
        long interval = RULES.growthIntervalTicks();
        long enough = 4L * RULES.dailyNutrition() * RULES.growthFoodDays();
        assertEquals(CitizenNeeds.GrowthCheck.READY, CitizenNeeds.checkGrowth(3, 1, enough, interval, 50, RULES));
        assertEquals(CitizenNeeds.GrowthCheck.NO_FREE_BED, CitizenNeeds.checkGrowth(3, 0, enough, interval, 50, RULES));
        assertEquals(CitizenNeeds.GrowthCheck.NOT_ENOUGH_FOOD, CitizenNeeds.checkGrowth(3, 1, enough - 1, interval, 50, RULES));
        assertEquals(CitizenNeeds.GrowthCheck.TOO_SOON, CitizenNeeds.checkGrowth(3, 1, enough, interval - 1, 50, RULES));
        assertEquals(CitizenNeeds.GrowthCheck.READY, CitizenNeeds.checkGrowth(0, 1, enough, Long.MAX_VALUE, 50, RULES));
        assertEquals(CitizenNeeds.GrowthCheck.FULL, CitizenNeeds.checkGrowth(RULES.maxPerKingdom(), 5, enough * 100, interval, 50, RULES));
    }

    @Test
    void moodChangesHowFastPeopleCome() {
        long base = 12_000L;
        assertEquals(base, CitizenNeeds.growthInterval(base, 50));
        assertEquals(9_000L, CitizenNeeds.growthInterval(base, 100));
        assertEquals(24_000L, CitizenNeeds.growthInterval(base, 0));
        assertTrue(CitizenNeeds.growthInterval(base, 70) < base);
        long happyWait = CitizenNeeds.growthInterval(RULES.growthIntervalTicks(), 100);
        long enough = 100L * RULES.dailyNutrition() * RULES.growthFoodDays();
        assertEquals(CitizenNeeds.GrowthCheck.READY, CitizenNeeds.checkGrowth(1, 1, enough, happyWait, 100, RULES));
        assertEquals(CitizenNeeds.GrowthCheck.TOO_SOON, CitizenNeeds.checkGrowth(1, 1, enough, happyWait, 20, RULES));
    }

    @Test
    void moodFollowsFoodHomeAndLosses() {
        int content = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20.0f, true, false, 0));
        int homeless = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20.0f, false, false, 0));
        int starving = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(0.0f, true, false, 0));
        int grieving = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20.0f, true, false, 2));
        assertEquals(75, content);
        assertTrue(homeless < content && starving < homeless && grieving < content);
        assertEquals(content - 20, CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20.0f, true, false, 9)));
        assertEquals(55, CitizenNeeds.nextMood(50, 75));
        assertEquals(45, CitizenNeeds.nextMood(50, 0));
        assertEquals(52, CitizenNeeds.nextMood(50, 52));
    }

    @Test
    void healthDependsOnFoodAndTheInfirmary() {
        assertEquals(-1.0f, CitizenNeeds.healthChange(0.0f, false, 1_200), 1e-6);
        assertEquals(0.0f, CitizenNeeds.healthChange(3.0f, true, 1_200), 1e-6);
        assertEquals(1.0f, CitizenNeeds.healthChange(10.0f, false, 1_200), 1e-6);
        assertEquals(8.0f, CitizenNeeds.healthChange(10.0f, true, 2_400), 1e-6);
    }

    @Test
    void farmOutputGrowsWithPlots() {
        assertEquals(1.0, CitizenNeeds.farmOutput(1, (int) CitizenNeeds.FARM_CYCLE_TICKS), 1e-9);
        assertEquals(0.5, CitizenNeeds.farmOutput(10, 2_400), 1e-9);
        assertEquals(0.0, CitizenNeeds.farmOutput(-3, 2_400), 1e-9);
    }
}
