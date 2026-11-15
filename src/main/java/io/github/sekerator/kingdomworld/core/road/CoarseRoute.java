package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The coarse stage of the search for a levelled road: chunks of a frame around a leg, each with its mean, lowest and highest
 * level, its water, owner and buildings, and from the chunks of the goal outwards (Dijkstra) the cost of reaching the goal from
 * every chunk and the coarse way along that field. The fine search keeps to a corridor around the coarse way and takes the field
 * for its estimate.
 *
 * <p>An edge from chunk A to its neighbour B of length L (16, or 22.6 across) costs {@code b * 16} ({@code b * 32} across, as the
 * fine way lays the corner of a diagonal step) and {@code lambda * L}; plus the earthworks
 * of the change of level {@code min(F * L * e / 2 * 3.5, F * H * 7.5 * L)} with {@code e = max(0, |mean B - mean A| - L / k)}; plus
 * the roughness of B {@code max(0, max B - min B - 16 / k) * F * 8}; plus its water {@code share * L * (F * 4 + depth)}; plus
 * {@code foreignCost * b * L} on foreign land; all of it times 1 + 7 times the share of B built over.
 */
public final class CoarseRoute {
    private static final double DIAGONAL = Math.sqrt(2.0);
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /**
     * The chunks of a frame: index {@code (x - x0) * depth + (z - z0)}.
     */
    public static final class Field {
        public final int x0;
        public final int z0;
        public final int width;
        public final int depth;
        public final float[] mean;
        public final float[] low;
        public final float[] high;
        public final float[] water;
        public final float[] waterDepth;
        public final float[] built;
        public final boolean[] foreign;
        public final boolean[] closed;

        public Field(int x0, int z0, int width, int depth) {
            this.x0 = x0;
            this.z0 = z0;
            this.width = width;
            this.depth = depth;
            int n = width * depth;
            mean = new float[n];
            low = new float[n];
            high = new float[n];
            water = new float[n];
            waterDepth = new float[n];
            built = new float[n];
            foreign = new boolean[n];
            closed = new boolean[n];
        }

        public boolean inside(int x, int z) {
            return x >= x0 && z >= z0 && x < x0 + width && z < z0 + depth;
        }

        public int index(int x, int z) {
            return (x - x0) * depth + (z - z0);
        }

        /**
         * Sets what is known of a chunk.
         */
        public void set(int x, int z, double meanLevel, double lowLevel, double highLevel, double waterShare, double depthOfWater, double builtShare,
                        boolean foreignLand, boolean noLand) {
            int i = index(x, z);
            mean[i] = (float) meanLevel;
            low[i] = (float) lowLevel;
            high[i] = (float) highLevel;
            water[i] = (float) waterShare;
            waterDepth[i] = (float) depthOfWater;
            built[i] = (float) builtShare;
            foreign[i] = foreignLand;
            closed[i] = noLand;
        }
    }

    /**
     * What a block of road costs the coarse stage.
     *
     * @param base        b, a plain block of road on level ground
     * @param lambda      the weight of a block of length
     * @param width       F, the width of the road with its verges
     * @param grade       k, the road climbs a block in so many
     * @param tunnel      H, the height of a tunnel
     * @param foreignCost the extra share of a block on foreign land
     */
    public record Costs(double base, double lambda, int width, int grade, int tunnel, double foreignCost) {
        public static Costs of(RoadClass roadClass, RouteStyle style, RoadRules rules, EarthRules earth) {
            return new Costs(EarthRules.base(roadClass, rules), earth.lambda(roadClass, style, rules), EarthRules.fullWidth(roadClass),
                    earth.grade(roadClass), earth.tunnelHeight(), rules.foreignCost());
        }
    }

    /**
     * The field of costs to the goal and the coarse way from the start.
     *
     * @param cost the cost of reaching the goal from every chunk of the frame, infinite where it cannot be reached
     * @param path chunks from the start to the goal, x and z; empty when the goal cannot be reached
     */
    public record Result(Field field, double[] cost, List<int[]> path) {
        public boolean found() {
            return !path.isEmpty();
        }

        /**
         * The cost to the goal at a block, between the middles of the chunks around it; infinite out of reach.
         */
        public double costAt(double blockX, double blockZ) {
            double fx = blockX / 16.0 - 0.5;
            double fz = blockZ / 16.0 - 0.5;
            int cx = (int) Math.floor(fx);
            int cz = (int) Math.floor(fz);
            double tx = fx - cx;
            double tz = fz - cz;
            double sum = 0;
            double weight = 0;
            double nearest = Double.POSITIVE_INFINITY;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    int x = cx + dx;
                    int z = cz + dz;
                    if (!field.inside(x, z)) {
                        continue;
                    }
                    double c = cost[field.index(x, z)];
                    if (!Double.isFinite(c)) {
                        continue;
                    }
                    double w = (dx == 0 ? 1 - tx : tx) * (dz == 0 ? 1 - tz : tz);
                    sum += c * w;
                    weight += w;
                    nearest = Math.min(nearest, c);
                }
            }
            return weight <= 1e-9 ? nearest : sum / weight;
        }
    }

    private CoarseRoute() {
    }

    /**
     * What going from chunk A to its neighbour B costs.
     */
    public static double edge(Field field, int a, int b, boolean diagonal, Costs costs) {
        if (field.closed[b]) {
            return Double.POSITIVE_INFINITY;
        }
        double length = diagonal ? 16 * DIAGONAL : 16;
        // The fine way lays the corner of every diagonal step: the work across is that of two chunks, the length the diagonal.
        double cost = costs.base() * (diagonal ? 32 : 16) + costs.lambda() * length;
        double rise = Math.max(0, Math.abs(field.mean[b] - field.mean[a]) - length / costs.grade());
        cost += Math.min(costs.width() * length * rise / 2 * 3.5, costs.width() * costs.tunnel() * 7.5 * length);
        cost += Math.max(0, field.high[b] - field.low[b] - 16.0 / costs.grade()) * costs.width() * 8;
        cost += field.water[b] * length * (costs.width() * 4 + field.waterDepth[b] / 4 * 4);
        if (field.foreign[b]) {
            cost += costs.foreignCost() * costs.base() * length;
        }
        return cost * (1 + 7 * Math.min(1, field.built[b]));
    }

    /**
     * Searches the frame from the goal chunks outwards and follows the field from the start chunk.
     *
     * @param goals chunks of the goal, x and z
     * @param start the chunk of the start, x and z
     */
    public static Result solve(Field field, Collection<int[]> goals, int[] start, Costs costs) {
        int n = field.width * field.depth;
        double[] cost = new double[n];
        Arrays.fill(cost, Double.POSITIVE_INFINITY);
        PriorityQueue<double[]> open = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
        for (int[] goal : goals) {
            if (field.inside(goal[0], goal[1])) {
                int i = field.index(goal[0], goal[1]);
                cost[i] = 0;
                open.add(new double[]{0, i});
            }
        }
        while (!open.isEmpty()) {
            double[] top = open.poll();
            int i = (int) top[1];
            if (top[0] > cost[i]) {
                continue;
            }
            int x = field.x0 + i / field.depth;
            int z = field.z0 + i % field.depth;
            for (int s = 0; s < STEPS.length; s++) {
                int nx = x + STEPS[s][0];
                int nz = z + STEPS[s][1];
                if (!field.inside(nx, nz)) {
                    continue;
                }
                int j = field.index(nx, nz);
                // Reaching the goal from j goes from j to i: the edge is paid for the chunk entered.
                double c = cost[i] + edge(field, j, i, s >= 4, costs);
                if (c < cost[j]) {
                    cost[j] = c;
                    open.add(new double[]{c, j});
                }
            }
        }
        List<int[]> path = new ArrayList<>();
        if (field.inside(start[0], start[1]) && Double.isFinite(cost[field.index(start[0], start[1])])) {
            Set<Integer> seen = new HashSet<>();
            int x = start[0];
            int z = start[1];
            path.add(new int[]{x, z});
            seen.add(field.index(x, z));
            while (cost[field.index(x, z)] > 0) {
                int i = field.index(x, z);
                int bestX = x;
                int bestZ = z;
                double best = Double.POSITIVE_INFINITY;
                for (int s = 0; s < STEPS.length; s++) {
                    int nx = x + STEPS[s][0];
                    int nz = z + STEPS[s][1];
                    if (!field.inside(nx, nz)) {
                        continue;
                    }
                    int j = field.index(nx, nz);
                    double c = cost[j] + edge(field, i, j, s >= 4, costs);
                    if (c < best && !seen.contains(j)) {
                        best = c;
                        bestX = nx;
                        bestZ = nz;
                    }
                }
                if (bestX == x && bestZ == z) {
                    break;
                }
                x = bestX;
                z = bestZ;
                seen.add(field.index(x, z));
                path.add(new int[]{x, z});
            }
        }
        return new Result(field, cost, path);
    }

    /**
     * The corridor around a coarse way: the chunks within one of it, two where the land is rough or wet, and the chunks round the
     * ends.
     *
     * @param widen chunks more on every side, after a corridor was found closed
     */
    public static Set<Long> corridor(Result result, Costs costs, int widen, Collection<int[]> ends) {
        Field field = result.field();
        Set<Long> chunks = new HashSet<>();
        for (int[] at : result.path()) {
            int i = field.index(at[0], at[1]);
            boolean rough = field.high[i] - field.low[i] > 16 || field.water[i] > 0;
            int reach = (rough ? 2 : 1) + widen;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    chunks.add(LandSnapshot.chunk(at[0] + dx, at[1] + dz));
                }
            }
        }
        for (int[] end : ends) {
            for (int dx = -1 - widen; dx <= 1 + widen; dx++) {
                for (int dz = -1 - widen; dz <= 1 + widen; dz++) {
                    chunks.add(LandSnapshot.chunk(end[0] + dx, end[1] + dz));
                }
            }
        }
        return chunks;
    }
}
