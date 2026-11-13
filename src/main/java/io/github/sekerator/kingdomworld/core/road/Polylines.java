package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Lines of points made lighter for the maps: the points a line can do without, by Douglas and Peucker, so that no point of
 * the line strays further than a tolerance from what is kept.
 */
public final class Polylines {
    private Polylines() {
    }

    /**
     * Which points of a line to keep: the first and the last, and between them those without which the line would stray
     * further than the tolerance, in blocks.
     *
     * @return the numbers of the points kept, in order
     */
    public static int[] keep(int[] xs, int[] zs, double tolerance) {
        int n = xs.length;
        if (n != zs.length) {
            throw new IllegalArgumentException("A coordinate for every point");
        }
        if (n <= 2) {
            int[] all = new int[n];
            for (int i = 0; i < n; i++) {
                all[i] = i;
            }
            return all;
        }
        boolean[] kept = new boolean[n];
        kept[0] = true;
        kept[n - 1] = true;
        double tolerance2 = tolerance * tolerance;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, n - 1});
        while (!stack.isEmpty()) {
            int[] range = stack.pop();
            int first = range[0];
            int last = range[1];
            double worst = -1;
            int at = -1;
            for (int i = first + 1; i < last; i++) {
                double d = CorridorTerrain.distance2(xs[i], zs[i], xs[first], zs[first], xs[last], zs[last]);
                if (d > worst) {
                    worst = d;
                    at = i;
                }
            }
            if (at >= 0 && worst > tolerance2) {
                kept[at] = true;
                stack.push(new int[]{first, at});
                stack.push(new int[]{at, last});
            }
        }
        int count = 0;
        for (boolean k : kept) {
            if (k) {
                count++;
            }
        }
        int[] result = new int[count];
        int j = 0;
        for (int i = 0; i < n; i++) {
            if (kept[i]) {
                result[j++] = i;
            }
        }
        return result;
    }
}
