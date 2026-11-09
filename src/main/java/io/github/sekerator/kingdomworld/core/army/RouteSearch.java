package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.LongPredicate;

/**
 * The way of a squad between two points far from players, found over chunks: a chunk with a road costs less, so the way
 * bends to the roads when they save time. The search goes a few chunks a tick ({@link #advance}), so a long way never
 * holds the server up; a way it cannot find in its bounds is the straight line.
 */
public final class RouteSearch {
    /** Chunks beyond the box of the two points the search may go. */
    public static final int MARGIN = 8;
    /** Longest side of the box searched, in chunks; a longer way is the straight line. */
    public static final int MAX_SIDE = 192;
    /** Most chunks one search looks at. */
    public static final int MAX_NODES = 20_000;

    /**
     * How a search stands.
     */
    public enum State {
        SEARCHING,
        FOUND,
        /** No way inside the bounds: the straight line. */
        STRAIGHT
    }

    private record Node(int x, int z, double cost, double estimate) {
    }

    private final WorldPos from;
    private final WorldPos to;
    private final LongPredicate road;
    private final double roadCost;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final int goalX;
    private final int goalZ;
    private final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.estimate, b.estimate));
    private final Map<Long, Double> best = new HashMap<>();
    private final Map<Long, Long> came = new HashMap<>();
    private int looked;
    private State state = State.SEARCHING;
    private List<WorldPos> path = List.of();

    /**
     * @param road     whether a chunk (its key) has a road
     * @param roadCost the cost of a chunk with a road by one without, below 1
     */
    public RouteSearch(WorldPos from, WorldPos to, LongPredicate road, double roadCost) {
        this.from = from;
        this.to = to;
        this.road = road;
        this.roadCost = Math.max(0.1, Math.min(1.0, roadCost));
        int sx = from.chunkX();
        int sz = from.chunkZ();
        goalX = to.chunkX();
        goalZ = to.chunkZ();
        minX = Math.min(sx, goalX) - MARGIN;
        maxX = Math.max(sx, goalX) + MARGIN;
        minZ = Math.min(sz, goalZ) - MARGIN;
        maxZ = Math.max(sz, goalZ) + MARGIN;
        if (!from.dimension().equals(to.dimension()) || maxX - minX > MAX_SIDE || maxZ - minZ > MAX_SIDE || (sx == goalX && sz == goalZ)) {
            finishStraight();
            return;
        }
        best.put(key(sx, sz), 0.0);
        open.add(new Node(sx, sz, 0.0, heuristic(sx, sz)));
    }

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private double heuristic(int x, int z) {
        return Math.hypot(goalX - x, goalZ - z) * roadCost;
    }

    public State state() {
        return state;
    }

    /**
     * The way found: points at the middle of the chunks where it turns, ending at the target point; the straight line is
     * the target point alone.
     */
    public List<WorldPos> path() {
        return path;
    }

    /**
     * Looks at up to {@code budget} more chunks.
     *
     * @return the chunks looked at
     */
    public int advance(int budget) {
        int used = 0;
        while (state == State.SEARCHING && used < budget) {
            Node node = open.poll();
            if (node == null || looked >= MAX_NODES) {
                finishStraight();
                break;
            }
            used++;
            looked++;
            long nodeKey = key(node.x, node.z);
            if (node.cost > best.getOrDefault(nodeKey, Double.MAX_VALUE)) {
                continue;
            }
            if (node.x == goalX && node.z == goalZ) {
                finishFound();
                break;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int nx = node.x + dx;
                    int nz = node.z + dz;
                    if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
                        continue;
                    }
                    long next = key(nx, nz);
                    double step = (dx != 0 && dz != 0 ? Math.sqrt(2.0) : 1.0) * (road.test(next) ? roadCost : 1.0);
                    double cost = node.cost + step;
                    if (cost < best.getOrDefault(next, Double.MAX_VALUE)) {
                        best.put(next, cost);
                        came.put(next, nodeKey);
                        open.add(new Node(nx, nz, cost, cost + heuristic(nx, nz)));
                    }
                }
            }
        }
        return used;
    }

    /**
     * Finds the whole way at once, for checks.
     */
    public List<WorldPos> finish() {
        while (state == State.SEARCHING) {
            advance(Integer.MAX_VALUE);
        }
        return path;
    }

    private void finishStraight() {
        state = State.STRAIGHT;
        path = List.of(to);
        open.clear();
    }

    private void finishFound() {
        List<long[]> cells = new ArrayList<>();
        long at = key(goalX, goalZ);
        long start = key(from.chunkX(), from.chunkZ());
        while (at != start) {
            cells.add(new long[]{at >> 32, (int) at});
            Long previous = came.get(at);
            if (previous == null) {
                break;
            }
            at = previous;
        }
        Collections.reverse(cells);
        List<WorldPos> points = new ArrayList<>();
        int lastDx = Integer.MIN_VALUE;
        int lastDz = Integer.MIN_VALUE;
        long px = from.chunkX();
        long pz = from.chunkZ();
        for (int i = 0; i < cells.size(); i++) {
            long[] cell = cells.get(i);
            int dx = (int) (cell[0] - px);
            int dz = (int) (cell[1] - pz);
            if (i > 0 && (dx != lastDx || dz != lastDz)) {
                // The way turned in the chunk before: its middle is a point of the way.
                long[] turn = cells.get(i - 1);
                points.add(new WorldPos(from.dimension(), (int) turn[0] * 16 + 8, from.y(), (int) turn[1] * 16 + 8));
            }
            lastDx = dx;
            lastDz = dz;
            px = cell[0];
            pz = cell[1];
        }
        points.add(to);
        path = List.copyOf(points);
        state = State.FOUND;
        open.clear();
        best.clear();
        came.clear();
    }
}
