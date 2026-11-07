package io.github.sekerator.kingdomworld.core.guild;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardSplitTest {
    @Test
    void anEvenSplitLosesNoCoin() {
        assertArrayEquals(new long[]{34, 33, 33}, RewardSplit.split(100, new int[]{1, 1, 1}), "the taker, named first, gets the odd coin");
        assertArrayEquals(new long[]{1, 0, 0}, RewardSplit.split(1, new int[]{1, 1, 1}));
        assertArrayEquals(new long[]{0, 0}, RewardSplit.split(0, new int[]{5, 5}));
    }

    @Test
    void sharesSplitByWeightAndTheRestGoesToTheBiggestRemainders() {
        // 10 * 3/6 = 5, 10 * 2/6 = 3.33, 10 * 1/6 = 1.67: the coin left goes to the third.
        assertArrayEquals(new long[]{5, 3, 2}, RewardSplit.split(10, new int[]{3, 2, 1}));
        assertArrayEquals(new long[]{0, 7}, RewardSplit.split(7, new int[]{0, 4}), "a share of 0 gets nothing");
        assertArrayEquals(new long[]{4, 3}, RewardSplit.split(7, new int[]{0, 0}), "all shares 0 split evenly");
    }

    @Test
    void largeAmountsDoNotOverflow() {
        long amount = 999_999_999_999_999L;
        long[] parts = RewardSplit.split(amount, new int[]{1_000, 999, 1, 7});
        assertEquals(amount, Arrays.stream(parts).sum());
    }

    @Test
    void randomSplitsAlwaysAddUp() {
        Random random = new Random(42);
        for (int i = 0; i < 5_000; i++) {
            int n = 1 + random.nextInt(16);
            int[] weights = new int[n];
            for (int j = 0; j < n; j++) {
                weights[j] = random.nextInt(4) == 0 ? 0 : random.nextInt(RewardSplit.MAX_WEIGHT + 1);
            }
            long amount = random.nextInt(3) == 0 ? random.nextInt(50) : Math.abs(random.nextLong() % 1_000_000_000_000L);
            long[] parts = RewardSplit.split(amount, weights);
            assertEquals(amount, Arrays.stream(parts).sum(), "the parts add up to the whole");
            long total = Arrays.stream(weights).sum();
            for (int j = 0; j < n; j++) {
                assertTrue(parts[j] >= 0);
                if (total > 0) {
                    double exact = (double) amount * weights[j] / total;
                    assertTrue(Math.abs(parts[j] - exact) < 1.0 + 1e-6 * exact, "a part is within one coin of its exact share");
                }
            }
        }
    }
}
