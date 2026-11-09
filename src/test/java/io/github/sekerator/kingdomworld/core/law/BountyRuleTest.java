package io.github.sekerator.kingdomworld.core.law;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BountyRuleTest {
    @Test
    void theFirstTwoHeadsPayTheFullReward() {
        assertEquals(1_000, BountyRule.reward(1_000, 0, 0, 0));
        assertEquals(1_000, BountyRule.reward(1_000, 1, 10, 10));
        assertFalse(BountyRule.reduced(1));
    }

    @Test
    void theThirdHeadPaysTheLeastOfRewardRansomAndCoins() {
        // The example of the rules: coins 2000, goods worth 3000 with a ransom of 1500, so the head pays 1500.
        assertTrue(BountyRule.reduced(2));
        assertEquals(1_500, BountyRule.reward(5_000, 2, 1_500, 2_000));
        assertEquals(800, BountyRule.reward(800, 5, 1_500, 2_000));
        assertEquals(300, BountyRule.reward(5_000, 2, 1_500, 300));
        assertEquals(0, BountyRule.reward(5_000, 3, 0, 2_000));
    }

    @Test
    void nothingIsNegative() {
        assertEquals(0, BountyRule.reward(-5, 0, 0, 0));
        assertEquals(0, BountyRule.reward(100, 2, -1, 50));
    }
}
