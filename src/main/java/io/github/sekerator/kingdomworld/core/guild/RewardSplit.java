package io.github.sekerator.kingdomworld.core.guild;

import java.util.Objects;
import java.util.UUID;

/**
 * Splits a reward among several people by their shares so that the parts add up to the whole: nothing is lost to rounding
 * and nothing is made. Everybody gets the whole part the share gives; what is left over goes one coin at a time to those
 * whose part lost most to rounding, the first named first among equals (the taker of a contract is named first).
 */
public final class RewardSplit {
    /** Largest share one person may have. */
    public static final int MAX_WEIGHT = 1_000;

    /**
     * One person's share.
     */
    public record Share(UUID player, int weight) {
        public Share {
            Objects.requireNonNull(player, "player");
            weight = Math.max(0, Math.min(MAX_WEIGHT, weight));
        }
    }

    private RewardSplit() {
    }

    /**
     * Splits an amount by weights. When every weight is 0, the amount is split evenly.
     *
     * @return the parts, in the order of the weights, adding up to {@code amount}
     */
    public static long[] split(long amount, int[] weights) {
        int n = weights.length;
        long[] parts = new long[n];
        if (n == 0 || amount <= 0) {
            return parts;
        }
        long total = 0;
        int[] used = new int[n];
        for (int i = 0; i < n; i++) {
            used[i] = Math.max(0, Math.min(MAX_WEIGHT, weights[i]));
            total += used[i];
        }
        if (total == 0) {
            java.util.Arrays.fill(used, 1);
            total = n;
        }
        long given = 0;
        long[] remainders = new long[n];
        long whole = amount / total;
        long rest = amount % total;
        for (int i = 0; i < n; i++) {
            // amount * weight / total without overflow: (whole * total + rest) * weight / total.
            parts[i] = whole * used[i] + rest * used[i] / total;
            remainders[i] = rest * used[i] % total;
            given += parts[i];
        }
        long left = amount - given;
        while (left > 0) {
            int best = -1;
            for (int i = 0; i < n; i++) {
                if (used[i] > 0 && (best < 0 || remainders[i] > remainders[best])) {
                    best = i;
                }
            }
            parts[best]++;
            remainders[best] = -1;
            left--;
        }
        return parts;
    }
}
