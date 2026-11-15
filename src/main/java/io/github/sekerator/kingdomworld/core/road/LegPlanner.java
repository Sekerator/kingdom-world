package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Plans one leg of a levelled road, from a point to the next: the coarse stage over chunks, then the fine search in a corridor
 * round the coarse way, a stretch of up to {@value #SEGMENT} blocks at a time; a corridor found closed is widened once; when it
 * is closed still or a search runs out of its budget the leg falls back to an engineered line along the coarse way, going round in
 * the plane only what no road may cross. The way is straightened, and the profile gives its heights; only buildings of players,
 * foreign walls, the void and the border stop a leg, and then it tells why and where.
 */
public final class LegPlanner {
    /** A leg shorter than this skips the coarse stage. */
    public static final int SHORT = 160;
    /** The longest stretch of one fine search, along the coarse way. */
    public static final int SEGMENT = 512;
    /** Blocks across the coarse way at the end of a stretch where the next one begins. */
    public static final int JOINT = 33;
    /** Time one stretch may take. */
    public static final long SEGMENT_NANOS = 20_000_000_000L;

    /**
     * Why a leg found no way.
     */
    public enum Reason {
        NONE,
        END_WALLED,
        POINT_ON_BUILDING,
        END_IN_WATER,
        BLOCKED,
        FOREIGN_WALL,
        NO_LAND,
        BORDER,
        NOT_LOADED,
        TOO_LONG,
        CANCELLED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The land a planner reads.
     */
    public interface Land extends RoadSurvey.Land {
        /**
         * Makes sure the chunks are read, waiting if it must.
         *
         * @return whether every one of them could be read
         */
        boolean read(Set<Long> chunks);

        UUID owner(int x, int z);

        /**
         * The coarse field of a frame of chunks.
         */
        CoarseRoute.Field field(int cx0, int cz0, int cx1, int cz1);

        boolean guessed(int chunkX, int chunkZ);

        /**
         * The road on a column, -1 for none.
         */
        long roadAt(int x, int z);

        int minY();

        int maxY();
    }

    /**
     * What a leg is to join.
     *
     * @param starts columns it may start from: x, z and the height of the deck
     * @param goals  columns it ends at
     * @param fromX  the point of the start, for the frame and the coarse stage
     * @param toX    the point of the goal
     */
    public record Spec(List<int[]> starts, List<EngineeredSearch.Goal> goals, int fromX, int fromZ, int toX, int toZ) {
    }

    /**
     * How long the stages of a leg took and how much the search did.
     */
    public record Stats(long coarseNanos, long fineNanos, long profileNanos, List<Integer> expanded, long bytes, boolean fallback,
                        int segments, int guessedColumns) {
    }

    /**
     * A leg planned.
     *
     * @param cells   the blocks of the way with their heights and kinds, empty when it failed
     * @param stuck   where it got stuck, x and z, when it failed
     */
    public record Leg(List<RouteCell> cells, Reason failure, int[] stuck, Stats stats) {
        public boolean found() {
            return failure == Reason.NONE;
        }
    }

    private final Land land;
    private final RoadClass roadClass;
    private final RouteStyle style;
    private final RoadRules rules;
    private final EarthRules earth;
    private final UUID kingdomId;
    private final BooleanSupplier cancelled;
    public LegPlanner(Land land, RoadClass roadClass, RouteStyle style, RoadRules rules, EarthRules earth, UUID kingdomId,
                      BooleanSupplier cancelled) {
        this.land = land;
        this.roadClass = roadClass;
        this.style = style;
        this.rules = rules;
        this.earth = earth;
        this.kingdomId = kingdomId;
        this.cancelled = cancelled == null ? () -> false : cancelled;
    }

    private static long chunk(int cx, int cz) {
        return LandSnapshot.chunk(cx, cz);
    }

    private static int cx(long key) {
        return (int) (key >> 32);
    }

    private static int cz(long key) {
        return (int) key;
    }

    /**
     * Plans the leg.
     */
    public Leg plan(Spec spec) {
        long started = System.nanoTime();
        engineeredFrom = Integer.MAX_VALUE;
        coarseChunks = Set.of();
        double straight = Math.hypot(spec.toX() - spec.fromX(), spec.toZ() - spec.fromZ());
        int halfWidth = EarthRules.fullWidth(roadClass) / 2;
        CoarseRoute.Costs coarseCosts = CoarseRoute.Costs.of(roadClass, style, rules, earth);
        List<int[]> ends = List.of(new int[]{spec.fromX() >> 4, spec.fromZ() >> 4}, new int[]{spec.toX() >> 4, spec.toZ() >> 4});
        int margin = straight < SHORT ? 48 : (int) Math.max(128, 0.35 * straight);
        int fx0 = Math.min(spec.fromX(), spec.toX()) - margin;
        int fz0 = Math.min(spec.fromZ(), spec.toZ()) - margin;
        int fx1 = Math.max(spec.fromX(), spec.toX()) + margin;
        int fz1 = Math.max(spec.fromZ(), spec.toZ()) + margin;
        for (EngineeredSearch.Goal goal : spec.goals()) {
            fx0 = Math.min(fx0, goal.x() - 48);
            fz0 = Math.min(fz0, goal.z() - 48);
            fx1 = Math.max(fx1, goal.x() + 48);
            fz1 = Math.max(fz1, goal.z() + 48);
        }
        int[] frame = {fx0 >> 4, fz0 >> 4, fx1 >> 4, fz1 >> 4};
        CoarseRoute.Result coarse = null;
        List<int[]> coarsePath;
        Set<Long> corridor = new LinkedHashSet<>();
        if (straight < SHORT) {
            for (int cx = frame[0]; cx <= frame[2]; cx++) {
                for (int cz = frame[1]; cz <= frame[3]; cz++) {
                    corridor.add(chunk(cx, cz));
                }
            }
            coarsePath = chunkLine(ends.get(0), ends.get(1));
        } else {
            CoarseRoute.Field field = land.field(frame[0], frame[1], frame[2], frame[3]);
            if (cancelled.getAsBoolean()) {
                return failed(Reason.CANCELLED, spec.fromX(), spec.fromZ(), started, 0, 0, List.of(), 0, false, 0, 0);
            }
            List<int[]> goalChunks = new ArrayList<>();
            for (EngineeredSearch.Goal goal : spec.goals()) {
                goalChunks.add(new int[]{goal.x() >> 4, goal.z() >> 4});
            }
            coarse = CoarseRoute.solve(field, goalChunks, ends.get(0), coarseCosts);
            if (!coarse.found()) {
                return failed(Reason.NO_LAND, spec.fromX(), spec.fromZ(), started, System.nanoTime() - started, 0, List.of(), 0, false, 0, 0);
            }
            coarsePath = coarse.path();
            corridor.addAll(CoarseRoute.corridor(coarse, coarseCosts, 0, ends));
        }
        long coarseNanos = System.nanoTime() - started;
        // The stretches: the coarse way cut every SEGMENT blocks.
        List<List<int[]>> stretches = new ArrayList<>();
        int per = Math.max(2, SEGMENT / 16);
        if (coarsePath.size() <= per + 1) {
            stretches.add(coarsePath);
        } else {
            for (int i = 0; i < coarsePath.size() - 1; i += per) {
                stretches.add(coarsePath.subList(i, Math.min(coarsePath.size(), i + per + 1)));
            }
        }
        long fineStarted = System.nanoTime();
        List<Integer> expanded = new ArrayList<>();
        long bytes = 0;
        int guessed = 0;
        List<int[]> way = new ArrayList<>();
        List<int[]> starts = spec.starts();
        boolean fallback = false;
        int[] stuck = {spec.fromX(), spec.fromZ()};
        for (int s = 0; s < stretches.size(); s++) {
            if (cancelled.getAsBoolean()) {
                return failed(Reason.CANCELLED, stuck[0], stuck[1], started, coarseNanos, 0, expanded, bytes, false, stretches.size(), guessed);
            }
            boolean last = s == stretches.size() - 1;
            List<int[]> stretch = stretches.get(s);
            Goals goals = last ? new Goals(spec.goals()) : new Goals(joint(stretches.get(s + 1).get(0), stretch));
            EngineeredSearch.Result result = null;
            for (int widen = 0; widen <= 4; widen += 4) {
                Set<Long> chunks = stretchChunks(stretch, corridor, widen, s == 0 ? ends.get(0) : null, last ? ends.get(1) : null, goals.goals);
                if (!land.read(chunks)) {
                    return failed(cancelled.getAsBoolean() ? Reason.CANCELLED : Reason.NOT_LOADED, stuck[0], stuck[1], started, coarseNanos,
                            System.nanoTime() - fineStarted, expanded, bytes, false, stretches.size(), guessed);
                }
                CorridorLand corridorLand = new CorridorLand(chunks, land, land::owner, kingdomId, halfWidth, land.minY(), land.maxY(),
                        land::guessed);
                corridorLand.roads(land::roadAt);
                CellCosts costs = new CellCosts(corridorLand, roadClass, style, rules, earth);
                int length = stretch.size() * 16;
                EngineeredSearch search = new EngineeredSearch(corridorLand, costs, starts, goals.goals, coarse, Math.max(50_000, 600 * length),
                        SEGMENT_NANOS, cancelled);
                result = search.run();
                expanded.add(result.expanded());
                bytes = Math.max(bytes, result.bytes());
                stuck = result.stuck();
                if (result.found()) {
                    guessed += corridorLand.guessedColumns();
                    List<int[]> piece = RouteSmoother.smooth(result.path(), costs, anchors(result.path(), costs, corridorLand));
                    if (!way.isEmpty()) {
                        piece = piece.subList(1, piece.size());
                    }
                    way.addAll(piece);
                    int[] end = way.get(way.size() - 1);
                    starts = List.of(new int[]{end[0], end[2], end[1]});
                    break;
                }
                if (result.status() != EngineeredSearch.Status.CLOSED) {
                    break;
                }
            }
            if (result == null || !result.found()) {
                if (result != null && result.status() == EngineeredSearch.Status.CANCELLED) {
                    return failed(Reason.CANCELLED, stuck[0], stuck[1], started, coarseNanos, System.nanoTime() - fineStarted, expanded, bytes,
                            false, stretches.size(), guessed);
                }
                fallback = true;
                break;
            }
        }
        if (fallback) {
            Set<Long> along = new HashSet<>();
            for (int[] at : coarsePath) {
                along.add(chunk(at[0], at[1]));
            }
            coarseChunks = along;
            int[] from = way.isEmpty() ? null : way.get(way.size() - 1);
            List<int[]> line = engineered(spec, from, corridor, frame);
            if (line == null) {
                return failed(lineFailure, lineStuck[0], lineStuck[1], started, coarseNanos, System.nanoTime() - fineStarted, expanded, bytes,
                        true, stretches.size(), guessed);
            }
            if (!way.isEmpty()) {
                line = line.subList(1, line.size());
            }
            engineeredFrom = way.size();
            way.addAll(line);
        }
        long fineNanos = System.nanoTime() - fineStarted;
        if (way.size() > Road.MAX_CELLS) {
            return failed(Reason.TOO_LONG, way.get(Road.MAX_CELLS)[0], way.get(Road.MAX_CELLS)[2], started, coarseNanos, fineNanos, expanded,
                    bytes, fallback, stretches.size(), guessed);
        }
        long profileStarted = System.nanoTime();
        List<RouteCell> cells = profile(way);
        long profileNanos = System.nanoTime() - profileStarted;
        return new Leg(cells, Reason.NONE, null, new Stats(coarseNanos, fineNanos, profileNanos, expanded, bytes, fallback, stretches.size(),
                guessed));
    }

    /**
     * The chunks on the straight line between two chunks, side by side or across.
     */
    static List<int[]> chunkLine(int[] from, int[] to) {
        List<int[]> line = new ArrayList<>();
        int x = from[0];
        int z = from[1];
        line.add(new int[]{x, z});
        while (x != to[0] || z != to[1]) {
            int dx = to[0] - x;
            int dz = to[1] - z;
            if (Math.abs(dx) >= 2 * Math.abs(dz)) {
                x += Integer.signum(dx);
            } else if (Math.abs(dz) >= 2 * Math.abs(dx)) {
                z += Integer.signum(dz);
            } else {
                x += Integer.signum(dx);
                z += Integer.signum(dz);
            }
            line.add(new int[]{x, z});
        }
        return line;
    }

    /** The goals of a stretch. */
    private record Goals(List<EngineeredSearch.Goal> goals) {
    }

    private Leg failed(Reason reason, int x, int z, long started, long coarse, long fine, List<Integer> expanded, long bytes, boolean fallback,
                       int segments, int guessed) {
        return new Leg(List.of(), reason, new int[]{x, z}, new Stats(coarse, fine, 0, expanded, bytes, fallback, segments, guessed));
    }

    /**
     * The chunks of the corridor near a stretch of the coarse way, widened as asked, with the chunks round its ends and goals.
     */
    private Set<Long> stretchChunks(List<int[]> stretch, Set<Long> corridor, int widen, int[] start, int[] end,
                                    List<EngineeredSearch.Goal> goals) {
        Set<Long> near = new HashSet<>();
        int reach = 3 + widen;
        for (int[] at : stretch) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    near.add(chunk(at[0] + dx, at[1] + dz));
                }
            }
        }
        Set<Long> chunks = new LinkedHashSet<>();
        for (long key : corridor) {
            if (near.contains(key)) {
                chunks.add(key);
            }
        }
        if (widen > 0) {
            for (int[] at : stretch) {
                for (int dx = -widen; dx <= widen; dx++) {
                    for (int dz = -widen; dz <= widen; dz++) {
                        chunks.add(chunk(at[0] + dx, at[1] + dz));
                    }
                }
            }
        }
        for (int[] at : stretch) {
            chunks.add(chunk(at[0], at[1]));
        }
        for (int[] end2 : new int[][]{start, end}) {
            if (end2 != null) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        chunks.add(chunk(end2[0] + dx, end2[1] + dz));
                    }
                }
            }
        }
        for (EngineeredSearch.Goal goal : goals) {
            chunks.add(chunk(goal.x() >> 4, goal.z() >> 4));
        }
        return chunks;
    }

    /**
     * The line across the coarse way where one stretch ends and the next begins: {@value #JOINT} columns at any height.
     */
    private static List<EngineeredSearch.Goal> joint(int[] at, List<int[]> stretch) {
        int[] before = stretch.get(Math.max(0, stretch.size() - 2));
        int dx = Integer.signum(at[0] - before[0]);
        int dz = Integer.signum(at[1] - before[1]);
        int x = at[0] * 16 + 8;
        int z = at[1] * 16 + 8;
        List<EngineeredSearch.Goal> goals = new ArrayList<>();
        int half = JOINT / 2;
        for (int s = -half; s <= half; s++) {
            if (dx != 0 && dz != 0) {
                // Across a diagonal: both directions across, so that the line has no gaps.
                goals.add(new EngineeredSearch.Goal(x + s, z - s, EngineeredSearch.ANY));
                goals.add(new EngineeredSearch.Goal(x + s + 1, z - s, EngineeredSearch.ANY));
            } else {
                goals.add(new EngineeredSearch.Goal(x - dz * s, z + dx * s, EngineeredSearch.ANY));
            }
        }
        return goals;
    }

    /**
     * Blocks of a way that stay where they are when it is straightened: its ends and the crossings of roads and walls.
     */
    private static Set<Integer> anchors(List<int[]> path, CellCosts costs, CorridorLand land) {
        Set<Integer> anchors = new HashSet<>();
        anchors.add(0);
        anchors.add(path.size() - 1);
        for (int i = 0; i < path.size(); i++) {
            int[] cell = path.get(i);
            if (land.inside(cell[0], cell[2]) && (land.flags(cell[0], cell[2]) & (LandColumn.ROAD | LandColumn.WALL | LandColumn.GATE)) != 0) {
                anchors.add(i);
            }
        }
        return anchors;
    }

    private Reason lineFailure = Reason.BLOCKED;
    private int[] lineStuck = new int[2];

    /**
     * The engineered line: blocks side by side over every column a road may cross, along the coarse way where it can, round in the
     * plane what it may not cross, the corridor round the place it got stuck doubled until it gets round or reaches the frame of
     * the leg. The profile then digs, bores, fills and bridges it.
     *
     * @param from where the line starts, x, y, z, or {@code null} for the starts of the leg
     */
    /** The first block of the way laid by the engineered line, which has no heights of a search. */
    private int engineeredFrom = Integer.MAX_VALUE;
    /** Blocks before and after a block of the engineered line whose ground bounds its heights at first. */
    static final int ENVELOPE = 64;

    /** The chunks of the coarse way, which the engineered line keeps to. */
    private Set<Long> coarseChunks = Set.of();

    private List<int[]> engineered(Spec spec, int[] from, Set<Long> corridor, int[] frame) {
        Set<Long> chunks = new LinkedHashSet<>(corridor);
        int[] start = from != null ? new int[]{from[0], from[2]} : new int[]{spec.starts().get(0)[0], spec.starts().get(0)[1]};
        Set<Long> goals = new HashSet<>();
        for (EngineeredSearch.Goal goal : spec.goals()) {
            goals.add(LandSnapshot.column(goal.x(), goal.z()));
        }
        int radius = 2;
        while (true) {
            if (cancelled.getAsBoolean()) {
                lineFailure = Reason.CANCELLED;
                return null;
            }
            if (!land.read(chunks)) {
                lineFailure = cancelled.getAsBoolean() ? Reason.CANCELLED : Reason.NOT_LOADED;
                lineStuck = start;
                return null;
            }
            int[] reached = new int[]{start[0], start[1]};
            List<long[]> plane = plane(start, goals, chunks, reached, coarseChunks);
            if (plane != null) {
                List<int[]> line = new ArrayList<>();
                for (long[] at : plane) {
                    int x = (int) at[0];
                    int z = (int) at[1];
                    LandColumn column = land.at(x, z);
                    line.add(new int[]{x, column == null ? 64 : column.top(), z, RouteCell.Kind.GROUND.ordinal()});
                }
                return line;
            }
            lineStuck = reached;
            boolean grew = false;
            int cx = reached[0] >> 4;
            int cz = reached[1] >> 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = cx + dx;
                    int z = cz + dz;
                    if (x >= frame[0] && x <= frame[2] && z >= frame[1] && z <= frame[3]) {
                        grew |= chunks.add(chunk(x, z));
                    }
                }
            }
            if (!grew) {
                lineFailure = why(reached);
                return null;
            }
            radius *= 2;
        }
    }

    /**
     * Why the line got stuck by a column: a foreign wall, the void or the border near it, otherwise buildings.
     */
    private Reason why(int[] at) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                LandColumn column = land.at(at[0] + dx, at[1] + dz);
                if (column == null) {
                    continue;
                }
                if (column.has(LandColumn.FOREIGN_WALL)) {
                    return Reason.FOREIGN_WALL;
                }
                if (column.has(LandColumn.VOID)) {
                    return Reason.NO_LAND;
                }
                if (column.has(LandColumn.BORDER)) {
                    return Reason.BORDER;
                }
            }
        }
        return Reason.BLOCKED;
    }

    /**
     * A* in the plane over the columns of the chunks a road may cross, side by side: a step costs a block, half a block for every
     * block of height between the columns, a block more off the chunks of the coarse way and half a block more over water.
     *
     * @param reached receives the column nearest the goal when there is no way
     */
    private List<long[]> plane(int[] start, Set<Long> goals, Set<Long> chunks, int[] reached, Set<Long> along) {
        int gx = 0;
        int gz = 0;
        for (long goal : goals) {
            gx = (int) (goal >> 32);
            gz = (int) goal;
        }
        final int tx = gx;
        final int tz = gz;
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, Double> cost = new HashMap<>();
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        long first = LandSnapshot.column(start[0], start[1]);
        cost.put(first, 0.0);
        open.add(new double[]{Math.abs(start[0] - tx) + Math.abs(start[1] - tz), first});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        long best = first;
        int bestH = Integer.MAX_VALUE;
        while (!open.isEmpty()) {
            double[] top = open.poll();
            long key = (long) top[1];
            int x = (int) (key >> 32);
            int z = (int) key;
            double c = cost.get(key);
            if (top[0] > c + Math.abs(x - tx) + Math.abs(z - tz) + 1e-6) {
                continue;
            }
            if (goals.contains(key)) {
                List<long[]> path = new ArrayList<>();
                for (Long at = key; at != null; at = parent.get(at)) {
                    path.add(0, new long[]{(int) (at >> 32), (int) (long) at});
                }
                return path;
            }
            int h = Math.abs(x - tx) + Math.abs(z - tz);
            if (h < bestH) {
                bestH = h;
                best = key;
            }
            for (int[] step : steps) {
                int nx = x + step[0];
                int nz = z + step[1];
                if (!chunks.contains(chunk(nx >> 4, nz >> 4))) {
                    continue;
                }
                long next = LandSnapshot.column(nx, nz);
                LandColumn column = land.at(nx, nz);
                if (!goals.contains(next) && (column == null || column.closed() || column.has(LandColumn.LAVA))) {
                    continue;
                }
                LandColumn here = land.at(x, z);
                double cost1 = 1;
                if (column != null && here != null) {
                    cost1 += 0.5 * Math.abs(column.top() - here.top());
                    cost1 += column.water() ? 0.5 : 0;
                }
                cost1 += along.isEmpty() || along.contains(chunk(nx >> 4, nz >> 4)) ? 0 : 1;
                double nc = c + cost1;
                Double known = cost.get(next);
                if (known == null || nc < known) {
                    cost.put(next, nc);
                    parent.put(next, key);
                    open.add(new double[]{nc + Math.abs(nx - tx) + Math.abs(nz - tz), next});
                }
            }
        }
        reached[0] = (int) (best >> 32);
        reached[1] = (int) best;
        return null;
    }

    /**
     * The heights and kinds of a way by the profile, crossings of roads and gates pinned.
     */
    /**
     * The heights of a road of several legs joined, by the profile over all of it: the heights of the legs as its reference.
     */
    List<RouteCell> reprofile(List<RouteCell> cells) {
        engineeredFrom = Integer.MAX_VALUE;
        List<int[]> way = new ArrayList<>(cells.size());
        for (RouteCell cell : cells) {
            way.add(new int[]{cell.x(), cell.y(), cell.z(), cell.kind().ordinal()});
        }
        return profile(way);
    }

    private List<RouteCell> profile(List<int[]> way) {
        List<int[]> cells = new ArrayList<>(way.size());
        int[] heights = new int[way.size()];
        Map<Integer, Integer> pins = new HashMap<>();
        for (int i = 0; i < way.size(); i++) {
            int[] at = way.get(i);
            cells.add(new int[]{at[0], at[2]});
            heights[i] = at[1];
            LandColumn column = land.at(at[0], at[2]);
            if (column != null && (column.has(LandColumn.ROAD) || column.has(LandColumn.WALL) || column.has(LandColumn.GATE))
                    && column.roadY() != LandColumn.NONE) {
                pins.put(i, column.roadY());
            }
        }
        int[] lows = null;
        int[] highs = null;
        if (engineeredFrom < way.size()) {
            // The engineered line has only the ground for its heights: its blocks may lie anywhere between the lowest and the highest
            // ground round them, so that the profile may bore through a ridge as well as climb it.
            lows = new int[way.size()];
            highs = new int[way.size()];
            for (int i = 0; i < way.size(); i++) {
                if (i < engineeredFrom) {
                    lows[i] = heights[i] - RoadProfile.WINDOW;
                    highs[i] = heights[i] + RoadProfile.WINDOW;
                    continue;
                }
                int low = heights[i];
                int high = heights[i];
                for (int j = Math.max(engineeredFrom, i - ENVELOPE); j <= Math.min(way.size() - 1, i + ENVELOPE); j++) {
                    low = Math.min(low, heights[j]);
                    high = Math.max(high, heights[j]);
                }
                lows[i] = low - RoadProfile.WINDOW / 2;
                highs[i] = high + RoadProfile.WINDOW / 2;
            }
        }
        RoadProfile.Result profile = RoadProfile.solve(new RoadProfile.Input(cells, heights, pins, lows, highs), roadClass, land, rules, earth,
                land.minY(), land.maxY());
        List<RouteCell> result = new ArrayList<>(way.size());
        for (int i = 0; i < way.size(); i++) {
            int x = cells.get(i)[0];
            int z = cells.get(i)[1];
            int y = profile.heights()[i];
            RouteCell.Kind kind = profile.kinds()[i];
            LandColumn column = land.at(x, z);
            int ground = column == null ? y : column.ground();
            int depth = switch (kind) {
                case CUT -> ground - y;
                case TUNNEL -> ground - (y + earth.tunnelHeight());
                case FILL -> y - ground - 1;
                case BRIDGE -> i % EarthRules.PIER_EVERY == 0 ? Math.max(0, y - 1 - ground) : 0;
                default -> 0;
            };
            result.add(new RouteCell(x, y, z, kind, depth, land.owner(x, z)));
        }
        return result;
    }
}
