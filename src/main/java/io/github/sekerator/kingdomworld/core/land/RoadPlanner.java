package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.road.RoadClass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plans the roads between the settlements of a world: every settlement to the nearest higher one of its province (or, without
 * one, to the settlement it belongs to or the capital), the large cities of a state in a ring around the capital, and every
 * capital to the capitals of the states around it (the pairs no third capital stands between). The way of a road is a rough
 * search over the cells of the survey with the costs of the plan (a river is crossed by a bridge, the sea and the land of
 * players are gone round), straightened into points at most {@value #MAX_STEP_CELLS} cells apart. The class of a road follows
 * its lower end: a path to a village or a hamlet, gravel to a town, paved between cities and capitals.
 */
public final class RoadPlanner {
    /** Cells between two points of a road at most: 256 blocks. */
    public static final int MAX_STEP_CELLS = 4;
    /** How far the way may stray from a straight piece between two points, in cells. */
    static final double TOLERANCE = 0.5;
    /** Cells one search looks at at most. */
    static final int MAX_NODES = 400_000;
    private static final int STRAIGHT = 10;
    private static final int DIAGONAL = 14;

    private RoadPlanner() {
    }

    public static RoadPlan plan(SettlementPlan settlements) {
        SurveyRaster raster = settlements.raster();
        List<SettlementSlot> slots = settlements.slots();
        List<int[]> pairs = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        // Every settlement to a higher one.
        for (SettlementSlot slot : slots) {
            if (slot.tier() == Tier.CAPITAL) {
                continue;
            }
            int best = -1;
            double bestDistance = Double.MAX_VALUE;
            for (SettlementSlot other : slots) {
                if (other.state() == slot.state() && other.province() == slot.province() && other.tier().above(slot.tier())) {
                    double distance = Math.hypot(other.x() - slot.x(), other.z() - slot.z());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = other.index();
                    }
                }
            }
            if (best < 0 && slot.parent() >= 0 && slots.get(slot.parent()).tier().above(slot.tier())) {
                best = slot.parent();
            }
            if (best < 0) {
                best = capitalOf(slots, slot.state());
            }
            if (best >= 0) {
                pair(pairs, seen, best, slot.index());
            }
        }

        // The large cities of a state in a ring around its capital.
        int states = settlements.provinces().states().states().size();
        for (int s = 0; s < states; s++) {
            int capital = capitalOf(slots, s);
            if (capital < 0) {
                continue;
            }
            SettlementSlot middle = slots.get(capital);
            List<SettlementSlot> cities = new ArrayList<>();
            for (SettlementSlot slot : slots) {
                if (slot.state() == s && slot.tier() == Tier.CITY) {
                    cities.add(slot);
                }
            }
            cities.sort((a, b) -> {
                double angleA = Math.atan2(a.z() - middle.z(), a.x() - middle.x());
                double angleB = Math.atan2(b.z() - middle.z(), b.x() - middle.x());
                return angleA != angleB ? Double.compare(angleA, angleB) : Integer.compare(a.index(), b.index());
            });
            if (cities.size() == 2) {
                pair(pairs, seen, cities.get(0).index(), cities.get(1).index());
            } else if (cities.size() > 2) {
                for (int i = 0; i < cities.size(); i++) {
                    pair(pairs, seen, cities.get(i).index(), cities.get((i + 1) % cities.size()).index());
                }
            }
        }

        // Capitals to the capitals around them: no third capital nearer to both.
        List<SettlementSlot> capitals = new ArrayList<>();
        for (SettlementSlot slot : slots) {
            if (slot.tier() == Tier.CAPITAL) {
                capitals.add(slot);
            }
        }
        for (int a = 0; a < capitals.size(); a++) {
            for (int b = a + 1; b < capitals.size(); b++) {
                double ab = distance(capitals.get(a), capitals.get(b));
                boolean between = false;
                for (int c = 0; c < capitals.size() && !between; c++) {
                    if (c != a && c != b) {
                        between = Math.max(distance(capitals.get(a), capitals.get(c)), distance(capitals.get(b), capitals.get(c))) < ab;
                    }
                }
                if (!between) {
                    pair(pairs, seen, capitals.get(a).index(), capitals.get(b).index());
                }
            }
        }

        Search search = new Search(raster);
        List<PlannedEdge> edges = new ArrayList<>();
        int failed = 0;
        for (int[] pair : pairs) {
            SettlementSlot from = slots.get(pair[0]);
            SettlementSlot to = slots.get(pair[1]);
            int[] path = search.route(from.cell(), to.cell());
            if (path == null) {
                failed++;
                continue;
            }
            int[] points = points(raster, path);
            int[] xs = new int[points.length];
            int[] zs = new int[points.length];
            for (int i = 0; i < points.length; i++) {
                xs[i] = raster.centerX(points[i]);
                zs[i] = raster.centerZ(points[i]);
            }
            xs[0] = from.x();
            zs[0] = from.z();
            xs[xs.length - 1] = to.x();
            zs[zs.length - 1] = to.z();
            edges.add(new PlannedEdge(edges.size(), from.index(), to.index(), classOf(from.tier(), to.tier()), xs, zs));
        }
        return new RoadPlan(settlements, edges, failed);
    }

    /**
     * The class of a road by its lower end.
     */
    public static RoadClass classOf(Tier a, Tier b) {
        Tier lower = a.above(b) ? b : a;
        return switch (lower) {
            case CAPITAL, CITY -> RoadClass.PAVED;
            case TOWN -> RoadClass.GRAVEL;
            case VILLAGE, HAMLET -> RoadClass.PATH;
        };
    }

    private static void pair(List<int[]> pairs, Set<Long> seen, int a, int b) {
        if (a == b) {
            return;
        }
        long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
        if (seen.add(key)) {
            pairs.add(new int[]{a, b});
        }
    }

    private static int capitalOf(List<SettlementSlot> slots, int state) {
        for (SettlementSlot slot : slots) {
            if (slot.state() == state && slot.tier() == Tier.CAPITAL) {
                return slot.index();
            }
        }
        return -1;
    }

    private static double distance(SettlementSlot a, SettlementSlot b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }

    /**
     * Whether a road may go over a cell: not the sea, land never surveyed or land of a player.
     */
    static boolean passable(SurveyRaster raster, int cell) {
        return raster.terrain(cell).passable() && !raster.player(cell);
    }

    /**
     * The cells of a way straightened into points: a piece between two points strays at most {@link #TOLERANCE} cells from
     * the way and crosses no cell a road may not go over, and no piece is longer than {@value #MAX_STEP_CELLS} cells.
     */
    static int[] points(SurveyRaster raster, int[] path) {
        List<Integer> kept = new ArrayList<>();
        kept.add(0);
        simplify(raster, path, 0, path.length - 1, kept);
        List<Integer> points = new ArrayList<>();
        points.add(path[0]);
        for (int k = 1; k < kept.size(); k++) {
            int i = kept.get(k - 1);
            int j = kept.get(k);
            // Long pieces are cut at cells of the way.
            while (cells(raster, path[i], path[j]) > MAX_STEP_CELLS) {
                int m = i + 1;
                for (int next = i + 2; next < j; next++) {
                    if (cells(raster, path[i], path[next]) <= MAX_STEP_CELLS && clear(raster, path[i], path[next])) {
                        m = next;
                    }
                }
                points.add(path[m]);
                i = m;
            }
            points.add(path[j]);
        }
        int[] result = new int[points.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = points.get(i);
        }
        return result;
    }

    private static void simplify(SurveyRaster raster, int[] path, int i, int j, List<Integer> kept) {
        if (j - i <= 1) {
            kept.add(j);
            return;
        }
        double ax = raster.cellX(path[i]);
        double az = raster.cellZ(path[i]);
        double bx = raster.cellX(path[j]);
        double bz = raster.cellZ(path[j]);
        double length = Math.hypot(bx - ax, bz - az);
        int far = i + 1;
        double farDistance = -1;
        for (int k = i + 1; k < j; k++) {
            double px = raster.cellX(path[k]);
            double pz = raster.cellZ(path[k]);
            double distance = length == 0 ? Math.hypot(px - ax, pz - az) : Math.abs((bx - ax) * (az - pz) - (ax - px) * (bz - az)) / length;
            if (distance > farDistance) {
                farDistance = distance;
                far = k;
            }
        }
        if (farDistance > TOLERANCE || !clear(raster, path[i], path[j])) {
            simplify(raster, path, i, far, kept);
            simplify(raster, path, far, j, kept);
        } else {
            kept.add(j);
        }
    }

    private static double cells(SurveyRaster raster, int a, int b) {
        return Math.hypot(raster.cellX(a) - raster.cellX(b), raster.cellZ(a) - raster.cellZ(b));
    }

    /**
     * Whether the straight piece between the middles of two cells crosses only cells a road may go over.
     */
    static boolean clear(SurveyRaster raster, int a, int b) {
        double ax = raster.cellX(a) + 0.5;
        double az = raster.cellZ(a) + 0.5;
        double bx = raster.cellX(b) + 0.5;
        double bz = raster.cellZ(b) + 0.5;
        int steps = (int) Math.ceil(Math.hypot(bx - ax, bz - az) * 4) + 1;
        for (int k = 0; k <= steps; k++) {
            double t = (double) k / steps;
            int x = (int) Math.floor(ax + (bx - ax) * t);
            int z = (int) Math.floor(az + (bz - az) * t);
            if (!raster.contains(x, z) || !passable(raster, raster.index(x, z))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The rough search over the cells, reused from road to road.
     */
    static final class Search {
        private final SurveyRaster raster;
        private final int[] cost;
        private final int[] from;
        private final int[] stamp;
        private final LongHeap heap = new LongHeap(4096);
        private int run;

        Search(SurveyRaster raster) {
            this.raster = raster;
            this.cost = new int[raster.cells()];
            this.from = new int[raster.cells()];
            this.stamp = new int[raster.cells()];
        }

        /**
         * The cheapest way from one cell to another over eight neighbours, or null if there is none within the limit.
         */
        int[] route(int start, int goal) {
            run++;
            heap.clear();
            int width = raster.width();
            int gx = raster.cellX(goal);
            int gz = raster.cellZ(goal);
            stamp[start] = run;
            cost[start] = 0;
            from[start] = -1;
            heap.add(LandMath.pack(heuristic(start, gx, gz), 0, start));
            int looked = 0;
            while (!heap.isEmpty() && looked < MAX_NODES) {
                long entry = heap.poll();
                int cell = LandMath.cell(entry);
                int known = cost[cell];
                if (LandMath.cost(entry) != known + heuristic(cell, gx, gz)) {
                    continue;
                }
                looked++;
                if (cell == goal) {
                    return path(goal);
                }
                int cx = cell % width;
                int cz = cell / width;
                for (int d = 0; d < 8; d++) {
                    int x = cx + LandMath.DX8[d];
                    int z = cz + LandMath.DZ8[d];
                    if (!raster.contains(x, z)) {
                        continue;
                    }
                    int next = z * width + x;
                    if (next != goal && !passable(raster, next)) {
                        continue;
                    }
                    boolean diagonal = d >= 4;
                    if (diagonal && (!passable(raster, raster.index(cx + LandMath.DX8[d], cz)) || !passable(raster, raster.index(cx, cz + LandMath.DZ8[d])))) {
                        continue;
                    }
                    int step = Math.max(1, raster.terrain(next).cost()) * (diagonal ? DIAGONAL : STRAIGHT);
                    int through = known + step;
                    if (stamp[next] != run || through < cost[next]) {
                        stamp[next] = run;
                        cost[next] = through;
                        from[next] = cell;
                        heap.add(LandMath.pack(through + heuristic(next, gx, gz), 0, next));
                    }
                }
            }
            return null;
        }

        private int heuristic(int cell, int gx, int gz) {
            int dx = Math.abs(raster.cellX(cell) - gx);
            int dz = Math.abs(raster.cellZ(cell) - gz);
            return STRAIGHT * Math.abs(dx - dz) + DIAGONAL * Math.min(dx, dz);
        }

        private int[] path(int goal) {
            List<Integer> cells = new ArrayList<>();
            for (int cell = goal; cell >= 0; cell = from[cell]) {
                cells.add(cell);
            }
            int[] path = new int[cells.size()];
            for (int i = 0; i < path.length; i++) {
                path[i] = cells.get(path.length - 1 - i);
            }
            return path;
        }
    }
}
