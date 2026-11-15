package io.github.sekerator.kingdomworld.core.production;

import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/**
 * Batches of the workshops: a worker takes the ingredients of several makings of one recipe at once, as many as the order still
 * asks for, the storehouses hold, its bag carries and the free furnaces of its post take; it spreads them over those furnaces and
 * burns as much fuel as the cooking takes. Pure functions, shared by the game and the tests.
 */
public final class Batches {

    private Batches() {
    }

    /**
     * Makings an order still asks for: {@code need} items of a recipe that gives {@code outputCount} at a time.
     */
    public static int makings(int need, int outputCount) {
        if (need <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, ((long) need + Math.max(1, outputCount) - 1) / Math.max(1, outputCount));
    }

    /**
     * Whole stacks the items take in a bag: each kind of item in stacks of its own size.
     */
    public static int stacks(Map<String, Integer> items, ToIntFunction<String> maxStack) {
        long total = 0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count > 0) {
                int size = Math.max(1, maxStack.applyAsInt(entry.getKey()));
                total += (count + size - 1) / size;
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * The largest number of makings, from 0 to {@code most}, a batch may hold: {@code fits} tells whether so many makings fit
     * (into the bag, into the fuel there is), and whatever fits for some makings fits for fewer.
     */
    public static int largest(int most, IntPredicate fits) {
        int low = 0;
        int high = Math.max(0, most);
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (fits.test(middle)) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    /**
     * Spreads makings over furnaces as evenly as their room lets, one at a time to each in turn, the first ones first: 64
     * smeltings over three empty furnaces are 22, 21 and 21. What does not fit is left out.
     *
     * @param room the makings each furnace takes at most
     * @return the makings for each furnace
     */
    public static int[] spread(int total, int[] room) {
        int[] counts = new int[room.length];
        int left = Math.max(0, total);
        while (left > 0) {
            // Rounds over the furnaces that still have room, each taking an equal share of what is left.
            int open = 0;
            for (int i = 0; i < room.length; i++) {
                if (counts[i] < room[i]) {
                    open++;
                }
            }
            if (open == 0) {
                break;
            }
            int share = Math.max(1, left / open);
            for (int i = 0; i < room.length && left > 0; i++) {
                int give = Math.min(Math.min(share, room[i] - counts[i]), left);
                if (give > 0) {
                    counts[i] += give;
                    left -= give;
                }
            }
        }
        return counts;
    }

    /**
     * Fuel items a furnace needs to cook {@code items} things of {@code cookTicks} each, when what lies in its fuel slot already
     * burns {@code banked} ticks and one item of the fuel burns {@code burnTicks}.
     */
    public static int fuel(int items, int cookTicks, double banked, int burnTicks) {
        return ProductionRules.fuelItems((double) Math.max(0, items) * Math.max(0, cookTicks) - Math.max(0.0, banked), burnTicks);
    }

    /**
     * Things a furnace can cook on what lies in its fuel slot only: fuel that is not of the batch cannot be added to.
     */
    public static int cookable(double banked, int cookTicks) {
        if (cookTicks <= 0) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(Math.max(0.0, banked) / cookTicks + 1e-9));
    }
}
