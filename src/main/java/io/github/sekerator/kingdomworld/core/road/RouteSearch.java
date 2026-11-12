package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/**
 * The search for the way of a road over the land, a few thousand steps a tick (A* over columns). A road goes up or down
 * one block at a time, so it winds round cliffs and climbs a mountain in serpentines; it keeps away from lava, fire and
 * what it must not break; it crosses water on a bridge and a gap on an embankment or a bridge, as long as they are not
 * too long; it goes round the land of other kingdoms when that costs little, since crossing it needs their consent.
 *
 * <p>The way it finds is connected side by side: a diagonal step gets the block at its corner, so that a path one block
 * wide can be walked.
 */
public final class RouteSearch {
    /** How close to the goal the road must come, in blocks along each axis. */
    public static final int GOAL_RADIUS = 2;
    /** How far from the start point the road may begin, in blocks along each axis. */
    public static final int START_RADIUS = 3;
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final double DIAGONAL = Math.sqrt(2.0);

    public enum Status {
        RUNNING,
        FOUND,
        FAILED
    }

    /**
     * Why no way was found.
     */
    public enum Failure {
        NONE,
        /** The goal is farther than the longest road. */
        TOO_FAR,
        /** Every way is blocked: cliffs, lava, protected blocks, too long a bridge. */
        NO_WAY,
        /** The search looked too long without finding a way. */
        TOO_LONG_SEARCH,
        /** The start is in the air, in lava or blocked. */
        BAD_START
    }

    private static final class Node {
        final int x;
        final int z;
        final int y;
        final int run;
        final RouteCell.Kind kind;
        final int depth;
        final double g;
        final double f;
        final int steps;
        final Node parent;
        /** For a diagonal step, the corner block between the parent and this node: 0 along x first, 1 along z first. */
        final int corner;
        boolean closed;

        Node(int x, int z, int y, int run, RouteCell.Kind kind, int depth, double g, double f, int steps, Node parent, int corner) {
            this.x = x;
            this.z = z;
            this.y = y;
            this.run = run;
            this.kind = kind;
            this.depth = depth;
            this.g = g;
            this.f = f;
            this.steps = steps;
            this.parent = parent;
            this.corner = corner;
        }

        boolean raised() {
            return kind != RouteCell.Kind.GROUND;
        }
    }

    private final Terrain terrain;
    private final int fromX;
    private final int fromZ;
    private final int toX;
    private final int toZ;
    private final UUID builder;
    private final RoadRules rules;
    private final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
    private final Map<Long, Node> best = new HashMap<>();
    private Status status = Status.RUNNING;
    private Failure failure = Failure.NONE;
    private int expanded;
    private boolean started;
    private List<RouteCell> route = List.of();

    /**
     * @param builder the kingdom that lays the road; land of others costs more
     */
    public RouteSearch(Terrain terrain, int fromX, int fromZ, int toX, int toZ, UUID builder, RoadRules rules) {
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.fromX = fromX;
        this.fromZ = fromZ;
        this.toX = toX;
        this.toZ = toZ;
        this.builder = builder;
        this.rules = Objects.requireNonNull(rules, "rules");
        if (octile(fromX, fromZ, toX, toZ) > rules.maxRouteLength()) {
            finish(Failure.TOO_FAR);
        }
    }

    public Status status() {
        return status;
    }

    public Failure failure() {
        return failure;
    }

    /**
     * Steps made so far.
     */
    public int expanded() {
        return expanded;
    }

    /**
     * The way found, from the start to the goal; empty until it is found.
     */
    public List<RouteCell> route() {
        return route;
    }

    private void finish(Failure reason) {
        status = Status.FAILED;
        failure = reason;
        open.clear();
        best.clear();
    }

    static double octile(int x0, int z0, int x1, int z1) {
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        return Math.max(dx, dz) + (DIAGONAL - 1.0) * Math.min(dx, dz);
    }

    private double heuristic(int x, int z) {
        int dx = Math.max(0, Math.abs(toX - x) - GOAL_RADIUS);
        int dz = Math.max(0, Math.abs(toZ - z) - GOAL_RADIUS);
        return (Math.max(dx, dz) + (DIAGONAL - 1.0) * Math.min(dx, dz)) * 1.0001;
    }

    private long key(int x, int z, int y, int run) {
        long rx = (x - fromX) & 0x1F_FFFFL;
        long rz = (z - fromZ) & 0x1F_FFFFL;
        long ry = (y + 2048) & 0xFFFL;
        long rr = Math.min(run, 63) & 0x3FL;
        // Neighbouring cells differ in a few bits of the fields, and the hash of a Long folds its halves onto each other: the
        // cells of a search fell into few buckets of the map. The key is scrambled one to one, so that it spreads them.
        long mixed = (rx << 39 | rz << 18 | ry << 6 | rr) * 0x9E37_79B9_7F4A_7C15L;
        return mixed ^ (mixed >>> 29);
    }

    private boolean atGoal(Node node) {
        return node.kind == RouteCell.Kind.GROUND && Math.abs(node.x - toX) <= GOAL_RADIUS && Math.abs(node.z - toZ) <= GOAL_RADIUS;
    }

    /**
     * Searches on for up to a number of steps.
     */
    public Status step(int budget) {
        if (status != Status.RUNNING) {
            return status;
        }
        if (!started) {
            // The ends of a road are often a throne or a town hall: the road starts on the free land around the point.
            List<Node> starts = new ArrayList<>();
            for (int dx = -START_RADIUS; dx <= START_RADIUS; dx++) {
                for (int dz = -START_RADIUS; dz <= START_RADIUS; dz++) {
                    Terrain.Column column = terrain.column(fromX + dx, fromZ + dz);
                    if (column == null) {
                        return status;
                    }
                    if (column.surface() != Terrain.Surface.LAND && column.surface() != Terrain.Surface.WATER) {
                        continue;
                    }
                    boolean water = column.surface() == Terrain.Surface.WATER;
                    int y = water ? column.y() + 1 : column.y();
                    double g = octile(fromX, fromZ, fromX + dx, fromZ + dz);
                    starts.add(new Node(fromX + dx, fromZ + dz, y, water ? 1 : 0, water ? RouteCell.Kind.BRIDGE : RouteCell.Kind.GROUND, 0, g,
                            g + heuristic(fromX + dx, fromZ + dz), 1, null, -1));
                }
            }
            started = true;
            if (starts.isEmpty()) {
                finish(Failure.BAD_START);
                return status;
            }
            for (Node first : starts) {
                best.put(key(first.x, first.z, first.y, first.run), first);
                open.add(first);
            }
        }
        int done = 0;
        while (done < budget) {
            Node node = open.poll();
            if (node == null) {
                finish(Failure.NO_WAY);
                return status;
            }
            if (node.closed) {
                continue;
            }
            if (atGoal(node)) {
                route = build(node);
                status = Status.FOUND;
                open.clear();
                best.clear();
                return status;
            }
            if (!ready(node)) {
                // The land around is not loaded yet: look again next tick.
                open.add(node);
                return status;
            }
            node.closed = true;
            done++;
            expanded++;
            if (expanded > rules.maxNodes()) {
                finish(Failure.TOO_LONG_SEARCH);
                return status;
            }
            expand(node);
        }
        return status;
    }

    private boolean ready(Node node) {
        for (int[] step : STEPS) {
            if (terrain.column(node.x + step[0], node.z + step[1]) == null) {
                return false;
            }
        }
        return true;
    }

    private void expand(Node node) {
        for (int i = 0; i < STEPS.length; i++) {
            int nx = node.x + STEPS[i][0];
            int nz = node.z + STEPS[i][1];
            boolean diagonal = i >= 4;
            int corner = -1;
            Terrain.Column column = terrain.column(nx, nz);
            if (column == null || column.surface() == Terrain.Surface.DANGER || column.surface() == Terrain.Surface.BLOCKED) {
                continue;
            }
            if (node.steps + 1 + heuristic(nx, nz) > rules.maxRouteLength() + GOAL_RADIUS) {
                continue;
            }
            double length = diagonal ? DIAGONAL : 1.0;
            double extra = column.owner() != null && !column.owner().equals(builder) ? rules.foreignCost() : 0.0;
            if (column.surface() == Terrain.Surface.LAND) {
                int ground = column.y();
                if (Math.abs(ground - node.y) <= 1) {
                    if (diagonal) {
                        if (node.raised()) {
                            continue;
                        }
                        corner = corner(node.x, node.z, nx, nz, node.y, ground);
                        if (corner < 0) {
                            continue;
                        }
                        Terrain.Column at = cornerColumn(node.x, node.z, nx, nz, corner);
                        if (at != null && foreign(at)) {
                            extra += rules.foreignCost();
                        }
                    }
                    double cost = length * (1.0 + rules.slopeCost() * Math.abs(ground - node.y)) + extra;
                    offer(node, nx, nz, ground, 0, RouteCell.Kind.GROUND, 0, cost, corner);
                } else if (ground < node.y - 1 && !diagonal) {
                    // A gap: the deck stays level or goes one block down, on an embankment or a bridge.
                    for (int deck = node.y; deck >= node.y - 1; deck--) {
                        if (deck <= ground || node.run + 1 > rules.maxBridge()) {
                            continue;
                        }
                        int depth = deck - ground - 1;
                        boolean fill = depth <= rules.maxFill();
                        double cost = length * (fill ? rules.fillCost() * (1.0 + 0.25 * depth) : rules.bridgeCost()) + extra;
                        offer(node, nx, nz, deck, node.run + 1, fill ? RouteCell.Kind.FILL : RouteCell.Kind.BRIDGE, depth, cost, -1);
                    }
                }
            } else if (column.surface() == Terrain.Surface.WATER && !diagonal) {
                int deck = column.y() + 1;
                if (Math.abs(deck - node.y) <= 1 && node.run + 1 <= rules.maxBridge()) {
                    offer(node, nx, nz, deck, node.run + 1, RouteCell.Kind.BRIDGE, 0, length * rules.bridgeCost() + extra, -1);
                }
            }
        }
    }

    /**
     * The corner block a diagonal step walks over: one next to both blocks that is solid ground at a fitting height.
     */
    private int corner(int x0, int z0, int x1, int z1, int y0, int y1) {
        int found = -1;
        for (int option = 0; option < 2; option++) {
            int cx = option == 0 ? x1 : x0;
            int cz = option == 0 ? z0 : z1;
            Terrain.Column column = terrain.column(cx, cz);
            if (column != null && column.surface() == Terrain.Surface.LAND && Math.abs(column.y() - y0) <= 1
                    && Math.abs(column.y() - y1) <= 1) {
                if (!foreign(column)) {
                    return option;
                }
                found = found < 0 ? option : found;
            }
        }
        return found;
    }

    private boolean foreign(Terrain.Column column) {
        return column.owner() != null && !column.owner().equals(builder);
    }

    private Terrain.Column cornerColumn(int x0, int z0, int x1, int z1, int corner) {
        return terrain.column(corner == 0 ? x1 : x0, corner == 0 ? z0 : z1);
    }

    private void offer(Node from, int x, int z, int y, int run, RouteCell.Kind kind, int depth, double cost, int corner) {
        double g = from.g + cost;
        long key = key(x, z, y, run);
        Node known = best.get(key);
        if (known != null && (known.closed || known.g <= g)) {
            return;
        }
        if (known != null) {
            known.closed = true;
        }
        Node next = new Node(x, z, y, run, kind, depth, g, g + heuristic(x, z), from.steps + (corner >= 0 ? 2 : 1), from, corner);
        best.put(key, next);
        open.add(next);
    }

    private List<RouteCell> build(Node goal) {
        List<Node> nodes = new ArrayList<>();
        for (Node node = goal; node != null; node = node.parent) {
            nodes.add(node);
        }
        Collections.reverse(nodes);
        List<RouteCell> cells = new ArrayList<>();
        for (Node node : nodes) {
            if (node.corner >= 0 && node.parent != null) {
                int cx = node.corner == 0 ? node.x : node.parent.x;
                int cz = node.corner == 0 ? node.parent.z : node.z;
                Terrain.Column column = terrain.column(cx, cz);
                int cy = column == null ? node.y : column.y();
                cells.add(new RouteCell(cx, cy, cz, RouteCell.Kind.GROUND, 0, column == null ? null : column.owner()));
            }
            Terrain.Column column = terrain.column(node.x, node.z);
            cells.add(new RouteCell(node.x, node.y, node.z, node.kind, node.depth, column == null ? null : column.owner()));
        }
        return List.copyOf(cells);
    }

    /**
     * The kingdoms other than the builder whose land a way crosses, in the order it meets them.
     */
    public static Set<UUID> crossed(List<RouteCell> cells, UUID builder) {
        Set<UUID> result = new LinkedHashSet<>();
        for (RouteCell cell : cells) {
            if (cell.owner() != null && !cell.owner().equals(builder)) {
                result.add(cell.owner());
            }
        }
        return result;
    }

    /**
     * The length of a way in blocks.
     */
    public static int length(List<RouteCell> cells) {
        return cells.size();
    }
}
