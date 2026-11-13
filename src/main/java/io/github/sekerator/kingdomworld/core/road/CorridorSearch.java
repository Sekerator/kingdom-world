package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The search for a road along a rough line of the plan: a stretch at a time between points of the line no more than
 * {@value #STRETCH} blocks apart, each searched within {@link CorridorTerrain#WIDTH} blocks of the line and at most
 * {@value #STRETCH_NODES} steps, the stretches joined into one way. A point the search cannot reach is moved aside a few
 * times before the road gives up. {@link RouteSearch} itself is the same as for the roads of players.
 */
public final class CorridorSearch {
    /** The longest stretch searched at once, in blocks. */
    public static final int STRETCH = 256;
    /** Steps a stretch takes at most. */
    public static final int STRETCH_NODES = 30_000;
    /** How far a point the search cannot reach is moved aside, in turn, in blocks across the line. */
    private static final int[] SHIFTS = {24, -24, 48, -48};

    public enum Status {
        RUNNING,
        FOUND,
        FAILED
    }

    private final Terrain terrain;
    private final int[] xs;
    private final int[] zs;
    private final UUID builder;
    private final RoadRules rules;
    private final List<RouteCell> cells = new ArrayList<>();
    private int leg;
    private int shift;
    private RouteSearch search;
    private Status status = Status.RUNNING;
    private RouteSearch.Failure failure = RouteSearch.Failure.NONE;
    private int expanded;
    private int mostInStretch;
    private int shifted;

    /**
     * @param terrain the land, read from the world; the search looks only along the line
     * @param xs      the blocks of the points of the line along x, from the start to the end
     * @param zs      the blocks along z
     * @param builder the kingdom that lays the road
     */
    public CorridorSearch(Terrain terrain, int[] xs, int[] zs, UUID builder, RoadRules rules) {
        Objects.requireNonNull(terrain, "terrain");
        if (xs.length != zs.length || xs.length < 2) {
            throw new IllegalArgumentException("A road needs two points at least");
        }
        this.terrain = new CorridorTerrain(terrain, xs, zs, CorridorTerrain.WIDTH);
        int[][] points = stretches(xs, zs, STRETCH);
        this.xs = points[0];
        this.zs = points[1];
        this.builder = builder;
        this.rules = rules.forSearch(STRETCH_NODES, 2 * STRETCH + 2 * CorridorTerrain.WIDTH);
    }

    /**
     * The points of a line with more put in, so that no two following ones lie further apart than a stretch.
     */
    public static int[][] stretches(int[] xs, int[] zs, int stretch) {
        List<int[]> points = new ArrayList<>();
        points.add(new int[]{xs[0], zs[0]});
        for (int i = 1; i < xs.length; i++) {
            double dx = xs[i] - xs[i - 1];
            double dz = zs[i] - zs[i - 1];
            int parts = (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / stretch);
            for (int k = 1; k <= parts; k++) {
                points.add(new int[]{(int) Math.round(xs[i - 1] + dx * k / parts), (int) Math.round(zs[i - 1] + dz * k / parts)});
            }
            if (parts == 0) {
                points.add(new int[]{xs[i], zs[i]});
            }
        }
        int[] outX = new int[points.size()];
        int[] outZ = new int[points.size()];
        for (int i = 0; i < points.size(); i++) {
            outX[i] = points.get(i)[0];
            outZ[i] = points.get(i)[1];
        }
        return new int[][]{outX, outZ};
    }

    public Status status() {
        return status;
    }

    public RouteSearch.Failure failure() {
        return failure;
    }

    /** Steps made in all. */
    public int expanded() {
        return expanded;
    }

    /** The most steps one stretch took. */
    public int mostInStretch() {
        return mostInStretch;
    }

    /** Stretches and of them all. */
    public int stretch() {
        return leg;
    }

    public int stretchCount() {
        return xs.length - 1;
    }

    /** Points moved aside so far. */
    public int shifted() {
        return shifted;
    }

    /**
     * The way found, from the start to the end; empty until it is found.
     */
    public List<RouteCell> route() {
        return status == Status.FOUND ? List.copyOf(cells) : List.of();
    }

    /**
     * Searches on for up to a number of steps.
     */
    public Status step(int budget) {
        int left = budget;
        while (left > 0 && status == Status.RUNNING) {
            if (search == null) {
                int fromX = cells.isEmpty() ? xs[0] : cells.get(cells.size() - 1).x();
                int fromZ = cells.isEmpty() ? zs[0] : cells.get(cells.size() - 1).z();
                int[] target = target();
                search = new RouteSearch(terrain, fromX, fromZ, target[0], target[1], builder, rules);
            }
            int before = search.expanded();
            RouteSearch.Status result = search.step(left);
            int done = search.expanded() - before;
            left -= done;
            expanded += done;
            if (result == RouteSearch.Status.FOUND) {
                mostInStretch = Math.max(mostInStretch, search.expanded());
                join(search.route());
                search = null;
                leg++;
                shift = 0;
                if (leg >= xs.length - 1) {
                    status = Status.FOUND;
                }
            } else if (result == RouteSearch.Status.FAILED) {
                mostInStretch = Math.max(mostInStretch, search.expanded());
                boolean last = leg + 1 == xs.length - 1;
                if (!last && shift < SHIFTS.length) {
                    shift++;
                    shifted++;
                    search = null;
                } else {
                    failure = search.failure();
                    status = Status.FAILED;
                    search = null;
                }
            } else if (done == 0) {
                // The land is not loaded yet: the search waits for the next tick.
                return status;
            }
        }
        return status;
    }

    /**
     * The point the stretch at hand goes to: the next point of the line, moved across it when it could not be reached.
     */
    private int[] target() {
        int x = xs[leg + 1];
        int z = zs[leg + 1];
        if (shift == 0) {
            return new int[]{x, z};
        }
        double dx = xs[leg + 1] - xs[leg];
        double dz = zs[leg + 1] - zs[leg];
        double length = Math.max(1e-9, Math.sqrt(dx * dx + dz * dz));
        int by = SHIFTS[shift - 1];
        return new int[]{(int) Math.round(x - dz / length * by), (int) Math.round(z + dx / length * by)};
    }

    /**
     * Joins the way of a stretch to the way so far: the blocks between where the last one ended and where this one begins
     * are filled in a straight line.
     */
    private void join(List<RouteCell> next) {
        if (next.isEmpty()) {
            return;
        }
        if (cells.isEmpty()) {
            cells.addAll(next);
            return;
        }
        RouteCell last = cells.get(cells.size() - 1);
        RouteCell first = next.get(0);
        int start = 0;
        if (first.x() == last.x() && first.z() == last.z()) {
            start = 1;
        } else {
            int steps = Math.max(Math.abs(first.x() - last.x()), Math.abs(first.z() - last.z()));
            for (int k = 1; k < steps; k++) {
                int x = last.x() + Math.round((first.x() - last.x()) * (float) k / steps);
                int z = last.z() + Math.round((first.z() - last.z()) * (float) k / steps);
                int y = last.y() + Math.round((first.y() - last.y()) * (float) k / steps);
                RouteCell.Kind kind = first.kind() == RouteCell.Kind.BRIDGE ? RouteCell.Kind.BRIDGE : RouteCell.Kind.GROUND;
                cells.add(new RouteCell(x, kind == RouteCell.Kind.BRIDGE ? first.y() : y, z, kind, 0, last.owner()));
            }
        }
        for (int i = start; i < next.size(); i++) {
            cells.add(next.get(i));
        }
    }
}
