package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The fine search for a levelled road in a corridor (A* over states x, z, the height of the deck and the blocks since the last
 * change of height). A step goes to one of eight neighbours and up, down or level; the height changes at most once in
 * {@code k} blocks, a diagonal step adding its corner block and counting two. What a block becomes at a height (on the ground,
 * a cutting, a tunnel, an embankment or a bridge) and what it costs is {@link CellCosts}; a step costs its blocks and
 * {@code lambda} times its length. The estimate is the larger of the plain blocks side by side to the nearest goal with
 * {@code lambda} times the octile distance to it, weighted by {@value #WEIGHT}, and the cost of the coarse field: not
 * admissible, on purpose, for speed; the way found costs at most so much more than the best one.
 *
 * <p>States are kept in open addressing by a long key with the cost as a float and the parent as an int, about 17 bytes each; the
 * open list is a heap of longs. The search stops after its budget of expanded states or of time and checks a flag to stop every
 * thousand states.
 */
public final class EngineeredSearch {
    /** A goal at any height. */
    public static final int ANY = Integer.MIN_VALUE;
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final double DIAGONAL = Math.sqrt(2.0);

    /**
     * How a search ended.
     */
    public enum Status {
        FOUND,
        /** Every way through the corridor is closed. */
        CLOSED,
        /** The budget of states or time ran out. */
        BUDGET,
        CANCELLED
    }

    /**
     * A goal column, at a height or at {@link #ANY}.
     */
    public record Goal(int x, int z, int y) {
    }

    /**
     * What the search found.
     *
     * @param path    the blocks of the way, x, y, z and the ordinal of their kind, side by side, the corners of diagonal steps in
     *                between; empty unless found
     * @param stuck   where the search came nearest the goal, x and z
     * @param bytes   the memory its states took at most, about
     */
    public record Result(Status status, List<int[]> path, int expanded, long nanos, long bytes, int[] stuck) {
        public boolean found() {
            return status == Status.FOUND;
        }
    }

    private final CorridorLand land;
    private final CellCosts costs;
    private final List<int[]> starts;
    private final Map<Long, Integer> goals = new HashMap<>();
    private final CoarseRoute.Result coarse;
    private final double coarseAtGoal;
    private final int budget;
    private final long nanosBudget;
    private final BooleanSupplier cancelled;
    private final int[] goalBox;
    /** Blocks side by side and the octile distance from every column of the corridor to the nearest goal, ignoring what stands. */
    private final float[] steps;
    private final float[] octile;
    /** How much the estimate of plain blocks is trusted over the true cost: above one the search goes for the goal sooner. */
    static final double WEIGHT = 1.2;
    /** How much the cost of the coarse field to the goal is trusted. */
    static final double COARSE = 1.0;
    /** The best cost a state of each column was expanded at: a state much dearer at another height is not followed. */
    private final float[] bestAt;
    /** How much dearer than the best state of its column a state may be and still be followed. */
    private final double margin;

    // The states: open addressing.
    private long[] keys;
    private float[] score;
    private int[] parent;
    private byte[] info;
    private int size;
    private int mask;
    // The open list.
    private long[] heap = new long[1 << 12];
    private int heapSize;

    /**
     * @param starts   columns to start from: x, z and the height of the deck
     * @param goals    the goal columns
     * @param coarse   the coarse field of the leg, or {@code null}
     * @param budget   states expanded at most
     * @param nanos    time at most
     */
    public EngineeredSearch(CorridorLand land, CellCosts costs, List<int[]> starts, Collection<Goal> goals, CoarseRoute.Result coarse,
                            int budget, long nanos, BooleanSupplier cancelled) {
        this.land = land;
        this.costs = costs;
        this.starts = starts;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        double atGoal = Double.POSITIVE_INFINITY;
        for (Goal goal : goals) {
            this.goals.merge(LandSnapshot.column(goal.x(), goal.z()), goal.y(), (a, b) -> a == ANY || b == ANY ? ANY : a);
            minX = Math.min(minX, goal.x());
            minZ = Math.min(minZ, goal.z());
            maxX = Math.max(maxX, goal.x());
            maxZ = Math.max(maxZ, goal.z());
            if (coarse != null) {
                atGoal = Math.min(atGoal, coarse.costAt(goal.x() + 0.5, goal.z() + 0.5));
            }
        }
        this.goalBox = new int[]{minX, minZ, maxX, maxZ};
        int n = land.width() * land.depth();
        this.steps = new float[n];
        this.octile = new float[n];
        this.bestAt = new float[n];
        Arrays.fill(bestAt, Float.POSITIVE_INFINITY);
        this.margin = 6 * (costs.base + costs.lambda) + 24;
        distances(goals);
        this.coarse = coarse;
        this.coarseAtGoal = Double.isFinite(atGoal) ? atGoal : 0;
        this.budget = budget;
        this.nanosBudget = nanos;
        this.cancelled = cancelled == null ? () -> false : cancelled;
        allocate(1 << 16);
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        Arrays.fill(keys, -1L);
        score = new float[capacity];
        parent = new int[capacity];
        info = new byte[capacity];
        mask = capacity - 1;
        size = 0;
    }

    private long key(int x, int z, int y, int g) {
        return (long) (x - land.x0()) << 32 | (long) (z - land.z0() & 0xFFFF) << 16 | (long) (y + 2048 & 0xFFF) << 4 | g & 0xF;
    }

    private int keyX(long key) {
        return (int) (key >>> 32) + land.x0();
    }

    private int keyZ(long key) {
        return (int) (key >>> 16 & 0xFFFF) + land.z0();
    }

    private static int keyY(long key) {
        return (int) (key >>> 4 & 0xFFF) - 2048;
    }

    private static int keyG(long key) {
        return (int) (key & 0xF);
    }

    private int slot(long key) {
        long h = key * 0x9E37_79B9_7F4A_7C15L;
        int i = (int) (h ^ h >>> 32) & mask;
        while (keys[i] != -1L && keys[i] != key) {
            i = i + 1 & mask;
        }
        return i;
    }

    private void grow() {
        long[] oldKeys = keys;
        float[] oldScore = score;
        int[] oldParent = parent;
        byte[] oldInfo = info;
        int[] moved = new int[oldKeys.length];
        allocate(oldKeys.length * 2);
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != -1L) {
                int j = slot(oldKeys[i]);
                keys[j] = oldKeys[i];
                score[j] = oldScore[i];
                info[j] = oldInfo[i];
                moved[i] = j;
                size++;
            }
        }
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != -1L) {
                parent[moved[i]] = oldParent[i] < 0 ? -1 : moved[oldParent[i]];
            }
        }
        for (int i = 0; i < heapSize; i++) {
            int s = (int) heap[i];
            heap[i] = heap[i] & 0xFFFF_FFFF_0000_0000L | moved[s] & 0xFFFF_FFFFL;
        }
    }

    private void push(double f, int slot) {
        if (heapSize == heap.length) {
            heap = Arrays.copyOf(heap, heap.length * 2);
        }
        long item = (long) Float.floatToIntBits((float) Math.max(0, f)) << 32 | slot & 0xFFFF_FFFFL;
        int i = heapSize++;
        while (i > 0) {
            int up = (i - 1) >>> 1;
            if (heap[up] <= item) {
                break;
            }
            heap[i] = heap[up];
            i = up;
        }
        heap[i] = item;
    }

    private long pop() {
        long top = heap[0];
        long last = heap[--heapSize];
        int i = 0;
        while (true) {
            int child = 2 * i + 1;
            if (child >= heapSize) {
                break;
            }
            if (child + 1 < heapSize && heap[child + 1] < heap[child]) {
                child++;
            }
            if (heap[child] >= last) {
                break;
            }
            heap[i] = heap[child];
            i = child;
        }
        if (heapSize > 0) {
            heap[i] = last;
        }
        return top;
    }

    /**
     * The distances of every column of the corridor to the nearest goal, by two passes over it each way: blocks side by side
     * (a diagonal step lays its corner) and the octile distance.
     */
    private void distances(Collection<Goal> goals) {
        int w = land.width();
        int d = land.depth();
        float far = Float.MAX_VALUE / 4;
        Arrays.fill(steps, far);
        Arrays.fill(octile, far);
        for (Goal goal : goals) {
            if (land.inside(goal.x(), goal.z())) {
                int i = land.index(goal.x(), goal.z());
                steps[i] = 0;
                octile[i] = 0;
            }
        }
        float diagonal = (float) DIAGONAL;
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < w; a++) {
                int x = pass == 0 ? a : w - 1 - a;
                for (int b = 0; b < d; b++) {
                    int z = pass == 0 ? b : d - 1 - b;
                    int i = x * d + z;
                    int sx = pass == 0 ? -1 : 1;
                    int sz = pass == 0 ? -1 : 1;
                    int px = x + sx;
                    int pz = z + sz;
                    float s = steps[i];
                    float o = octile[i];
                    if (px >= 0 && px < w) {
                        int j = px * d + z;
                        s = Math.min(s, steps[j] + 1);
                        o = Math.min(o, octile[j] + 1);
                    }
                    if (pz >= 0 && pz < d) {
                        int j = x * d + pz;
                        s = Math.min(s, steps[j] + 1);
                        o = Math.min(o, octile[j] + 1);
                    }
                    if (px >= 0 && px < w && pz >= 0 && pz < d) {
                        o = Math.min(o, octile[px * d + pz] + diagonal);
                    }
                    int qz = z - sz;
                    if (px >= 0 && px < w && qz >= 0 && qz < d) {
                        o = Math.min(o, octile[px * d + qz] + diagonal);
                    }
                    steps[i] = s;
                    octile[i] = o;
                }
            }
        }
        // The two passes miss what lies across them: two more, the other way round in z, settle it.
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < w; a++) {
                int x = pass == 0 ? a : w - 1 - a;
                for (int b = 0; b < d; b++) {
                    int z = pass == 0 ? d - 1 - b : b;
                    int i = x * d + z;
                    int sx = pass == 0 ? -1 : 1;
                    int sz = pass == 0 ? 1 : -1;
                    int px = x + sx;
                    int pz = z + sz;
                    float s = steps[i];
                    float o = octile[i];
                    if (px >= 0 && px < w) {
                        int j = px * d + z;
                        s = Math.min(s, steps[j] + 1);
                        o = Math.min(o, octile[j] + 1);
                    }
                    if (pz >= 0 && pz < d) {
                        int j = x * d + pz;
                        s = Math.min(s, steps[j] + 1);
                        o = Math.min(o, octile[j] + 1);
                    }
                    if (px >= 0 && px < w && pz >= 0 && pz < d) {
                        o = Math.min(o, octile[px * d + pz] + diagonal);
                    }
                    steps[i] = s;
                    octile[i] = o;
                }
            }
        }
    }

    private double estimate(int x, int z) {
        double plain;
        if (land.inside(x, z)) {
            int i = land.index(x, z);
            plain = (steps[i] * costs.base + octile[i] * costs.lambda) * WEIGHT;
        } else {
            int dx = Math.max(0, Math.max(goalBox[0] - x, x - goalBox[2]));
            int dz = Math.max(0, Math.max(goalBox[1] - z, z - goalBox[3]));
            plain = ((dx + dz) * costs.base + (Math.max(dx, dz) + (DIAGONAL - 1.0) * Math.min(dx, dz)) * costs.lambda) * WEIGHT;
        }
        if (coarse == null) {
            return plain;
        }
        double field = coarse.costAt(x + 0.5, z + 0.5);
        return Double.isFinite(field) ? Math.max(plain, COARSE * Math.max(0, field - coarseAtGoal)) : plain;
    }

    private boolean atGoal(int x, int z, int y) {
        Integer at = goals.get(LandSnapshot.column(x, z));
        return at != null && (at == ANY || at == y);
    }

    /**
     * Searches until the goal is reached, every way is closed or the budget runs out.
     */
    public Result run() {
        long started = System.nanoTime();
        int k = costs.grade;
        for (int[] start : starts) {
            double c = costs.cost(start[0], start[1], start[2]);
            if (!Double.isFinite(c)) {
                // A start on the ground its point is on, whatever it is: the road begins there.
                c = costs.base;
            }
            long key = key(start[0], start[1], start[2], k - 1);
            int s = slot(key);
            if (keys[s] == key) {
                continue;
            }
            keys[s] = key;
            score[s] = 0;
            parent[s] = -1;
            info[s] = (byte) (costs.kind.ordinal() << 2);
            size++;
            push(estimate(start[0], start[1]), s);
        }
        int expanded = 0;
        int[] stuck = starts.isEmpty() ? new int[2] : new int[]{starts.get(0)[0], starts.get(0)[1]};
        double nearest = Double.POSITIVE_INFINITY;
        while (heapSize > 0) {
            long item = pop();
            int s = (int) item;
            if ((info[s] & 1) != 0) {
                continue;
            }
            float f = Float.intBitsToFloat((int) (item >>> 32));
            long key = keys[s];
            int x = keyX(key);
            int z = keyZ(key);
            int y = keyY(key);
            int g = keyG(key);
            if (f > (score[s] + estimate(x, z)) * 1.000_01 + 0.01) {
                // A stale entry of a state found cheaper since; the costs are floats, so only a clear difference counts.
                continue;
            }
            int column = land.index(x, z);
            if (score[s] > bestAt[column] + margin) {
                // Another height of the same column was reached much cheaper: this one is not followed.
                info[s] |= 1;
                continue;
            }
            bestAt[column] = Math.min(bestAt[column], score[s]);
            info[s] |= 1;
            if (atGoal(x, z, y)) {
                return new Result(Status.FOUND, path(s), expanded, System.nanoTime() - started, bytes(), new int[]{x, z});
            }
            double h = estimate(x, z);
            if (h < nearest) {
                nearest = h;
                stuck = new int[]{x, z};
            }
            expanded++;
            if (expanded >= budget || (expanded & 1023) == 0 && (System.nanoTime() - started > nanosBudget || cancelled.getAsBoolean())) {
                Status status = cancelled.getAsBoolean() ? Status.CANCELLED : Status.BUDGET;
                return new Result(status, List.of(), expanded, System.nanoTime() - started, bytes(), stuck);
            }
            expand(s, x, z, y, g, k);
        }
        return new Result(Status.CLOSED, List.of(), expanded, System.nanoTime() - started, bytes(), stuck);
    }

    private long bytes() {
        return (long) keys.length * 17 + (long) heap.length * 8;
    }

    private void expand(int s, int x, int z, int y, int g, int k) {
        double base = score[s];
        long self = keys[s];
        for (int i = 0; i < STEPS.length; i++) {
            int nx = x + STEPS[i][0];
            int nz = z + STEPS[i][1];
            boolean diagonal = i >= 4;
            if (!land.inside(nx, nz)) {
                continue;
            }
            int corner = -1;
            double cornerCost = 0;
            if (diagonal) {
                // The corner keeps the height of the block left.
                double a = costs.cost(nx, z, y);
                double b = costs.cost(x, nz, y);
                if (!Double.isFinite(a) && !Double.isFinite(b)) {
                    continue;
                }
                corner = a <= b ? 0 : 1;
                cornerCost = Math.min(a, b);
            }
            int counted = diagonal ? 2 : 1;
            for (int dy = -1; dy <= 1; dy++) {
                if (dy != 0 && g < k - 1) {
                    continue;
                }
                int ny = y + dy;
                double c = costs.cost(nx, nz, ny);
                if (!Double.isFinite(c)) {
                    continue;
                }
                RouteCell.Kind kind = costs.kind;
                int ng = dy != 0 ? 0 : Math.min(k - 1, g + counted);
                double step = c + cornerCost + costs.lambda * (diagonal ? DIAGONAL : 1.0);
                offer(self, nx, nz, ny, ng, base + step, kind, corner);
            }
        }
    }

    private void offer(long fromKey, int x, int z, int y, int g, double cost, RouteCell.Kind kind, int corner) {
        if (size * 2 >= keys.length) {
            grow();
        }
        // The slot of the state left, which growing may have moved.
        int from = slot(fromKey);
        if (cost > bestAt[land.index(x, z)] + margin) {
            return;
        }
        long key = key(x, z, y, g);
        int s = slot(key);
        if (keys[s] == key) {
            if ((info[s] & 1) != 0 || score[s] <= cost) {
                return;
            }
        } else {
            keys[s] = key;
            size++;
        }
        score[s] = (float) cost;
        parent[s] = from;
        info[s] = (byte) (kind.ordinal() << 2 | (corner + 1) << 5);
        push(cost + estimate(x, z), s);
    }

    /**
     * The way to a state, the corners of its diagonal steps in between.
     */
    private List<int[]> path(int s) {
        List<int[]> reversed = new ArrayList<>();
        int at = s;
        while (at >= 0) {
            long key = keys[at];
            int x = keyX(key);
            int z = keyZ(key);
            int y = keyY(key);
            int kind = info[at] >> 2 & 7;
            reversed.add(new int[]{x, y, z, kind});
            int corner = (info[at] >> 5 & 3) - 1;
            int up = parent[at];
            if (corner >= 0 && up >= 0) {
                long before = keys[up];
                int px = keyX(before);
                int pz = keyZ(before);
                int py = keyY(before);
                int cx = corner == 0 ? x : px;
                int cz = corner == 0 ? pz : z;
                costs.cost(cx, cz, py);
                reversed.add(new int[]{cx, py, cz, costs.kind.ordinal()});
            }
            at = up;
        }
        Collections.reverse(reversed);
        return reversed;
    }
}
