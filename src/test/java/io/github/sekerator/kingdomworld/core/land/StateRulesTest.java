package io.github.sekerator.kingdomworld.core.land;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateRulesTest {
    @Test
    void theRadiusOfThePlanOfStatesHasABound() {
        assertEquals(StateRules.RADIUS, StateRules.planRadius(StateRules.RADIUS));
        assertEquals(StateRules.MAX_RADIUS, StateRules.planRadius(StateRules.MAX_RADIUS));
        assertEquals(StateRules.MAX_RADIUS, StateRules.planRadius(1_000_000));
        assertEquals(256, StateRules.planRadius(10));
    }

    @Test
    void theSurveyOfTheLargestRadiusStaysSmall() {
        // The survey reaches 512 blocks and a cell beyond the radius on every side, in cells of 64 blocks.
        int reach = StateRules.MAX_RADIUS + 512 + SurveyRaster.CELL;
        long size = (long) Math.ceil(2.0 * reach / SurveyRaster.CELL) + 1;
        assertTrue(size * size < 500_000, "The survey of the largest radius has " + size * size + " cells");
    }
}
