package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Straightens the way the search found, as a string pulled tight: from a block it takes the farthest block up to 256 on to which a
 * line of blocks side by side goes over columns the road may cross, with heights spread evenly within the grade, and costs at most
 * {@value EarthRules#SMOOTH_TOLERANCE} more than the way it replaces. Points, crossings and gates stay where they are. The way stays
 * one of blocks side by side.
 */
public final class RouteSmoother {
    /** The farthest a line goes at once. */
    public static final int REACH = 256;
    private static final int[] SPANS = {256, 128, 64, 32, 16, 8, 4, 2};

    private RouteSmoother() {
    }

    /**
     * The way straightened.
     *
     * @param path    blocks of the way, x, y, z, kind, side by side
     * @param anchors indices of blocks that stay
     */
    public static List<int[]> smooth(List<int[]> path, CellCosts costs, Set<Integer> anchors) {
        int n = path.size();
        if (n < 3) {
            return new ArrayList<>(path);
        }
        double[] prefix = new double[n];
        for (int i = 1; i < n; i++) {
            int[] cell = path.get(i);
            double c = costs.cost(cell[0], cell[2], cell[1]);
            prefix[i] = prefix[i - 1] + (Double.isFinite(c) ? c : 1e6) + costs.lambda();
        }
        List<int[]> result = new ArrayList<>();
        result.add(path.get(0));
        int i = 0;
        while (i < n - 1) {
            int next = i + 1;
            while (next < n - 1 && !anchors.contains(next)) {
                next++;
            }
            boolean moved = false;
            for (int span : SPANS) {
                int j = Math.min(i + span, next);
                if (j <= i + 1) {
                    continue;
                }
                List<int[]> line = line(path.get(i), path.get(j), costs);
                if (line == null) {
                    continue;
                }
                double cost = 0;
                for (int t = 1; t < line.size(); t++) {
                    int[] cell = line.get(t);
                    cost += costs.cost(cell[0], cell[2], cell[1]) + costs.lambda();
                }
                if (cost <= (prefix[j] - prefix[i]) * (1 + EarthRules.SMOOTH_TOLERANCE)) {
                    for (int t = 1; t < line.size(); t++) {
                        result.add(line.get(t));
                    }
                    i = j;
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                result.add(path.get(i + 1));
                i++;
            }
        }
        return result;
    }

    /**
     * A line of blocks side by side from one block to another, with heights spread evenly; {@code null} when it crosses a column
     * the road may not cross at its height or climbs faster than the grade.
     */
    static List<int[]> line(int[] from, int[] to, CellCosts costs) {
        int dx = to[0] - from[0];
        int dz = to[2] - from[2];
        int nx = Math.abs(dx);
        int nz = Math.abs(dz);
        int steps = nx + nz;
        int rise = to[1] - from[1];
        if (Math.abs(rise) * costs.grade() > steps) {
            return null;
        }
        List<int[]> line = new ArrayList<>(steps + 1);
        line.add(from);
        int x = from[0];
        int z = from[2];
        int ix = 0;
        int iz = 0;
        for (int t = 1; t <= steps; t++) {
            if (iz >= nz || ix < nx && (0.5 + ix) * nz < (0.5 + iz) * nx) {
                x += Integer.signum(dx);
                ix++;
            } else {
                z += Integer.signum(dz);
                iz++;
            }
            int y = from[1] + (int) Math.round(rise * (t / (double) steps));
            double c = costs.cost(x, z, y);
            if (!Double.isFinite(c)) {
                return null;
            }
            line.add(new int[]{x, y, z, costs.kind.ordinal()});
        }
        return line;
    }
}
