package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * The plan of a levelled road through its points: the points are held to columns, a leg is planned between every two of them
 * (each on its own, kept by its ends, class and way, so that moving a point plans only the two legs at it again), the legs are
 * joined and the profile runs over the whole road, so that the heights meet where they join; then the survey of its earthworks
 * and its estimate, by leg and in all.
 */
public final class RoutePlan {

    /**
     * A point of the plan.
     *
     * @param targets for the end at a city or a capital, the columns the road may begin or end at, x, z and the height of the deck;
     *                empty for a point of its own
     */
    public record Point(int x, int z, List<int[]> targets) {
        public Point {
            targets = List.copyOf(targets);
        }

        public static Point at(int x, int z) {
            return new Point(x, z, List.of());
        }

        public boolean city() {
            return !targets.isEmpty();
        }
    }

    /**
     * A plan found, or why not.
     *
     * @param cells     the way of the road with its heights and kinds, empty when it failed
     * @param snapped   every point as it was held, x and z
     * @param legs      the legs planned, in order (those after a failed one are missing)
     * @param estimates the estimate of every leg found
     * @param failedLeg the leg that failed, -1 for none
     */
    public record Result(List<RouteCell> cells, List<int[]> snapped, List<LegPlanner.Leg> legs, List<RoadEstimate> estimates,
                         RoadEstimate estimate, List<RoadLayout.ColumnSurvey> surveys, double earthTotal, LegPlanner.Reason failure,
                         int failedLeg, int[] stuck, int guessedColumns) {
        public boolean found() {
            return failure == LegPlanner.Reason.NONE;
        }
    }

    private RoutePlan() {
    }

    /**
     * The key a leg is kept by.
     */
    static String key(int[] from, int[] to, Point a, Point b, RoadClass roadClass, RouteStyle style) {
        return from[0] + "," + from[1] + (a.city() ? "c" + a.targets().size() : "") + ">" + to[0] + "," + to[1] + (b.city() ? "c" + b.targets().size()
                : "") + ":" + roadClass.id() + ":" + style.id();
    }

    /**
     * Plans the road.
     *
     * @param cache    legs planned before, by their key; legs found are put in
     * @param progress told the share done, in hundredths
     * @param pace     the pace of a caravan across country, blocks a minute
     */
    public static Result plan(LegPlanner.Land land, List<Point> points, RoadClass roadClass, RouteStyle style, RoadRules rules, EarthRules earth,
                              UUID kingdomId, Map<String, LegPlanner.Leg> cache, BooleanSupplier cancelled, IntConsumer progress, double pace) {
        // The land round every point, to hold it to a column.
        Set<Long> around = new HashSet<>();
        for (Point point : points) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    around.add(LandSnapshot.chunk((point.x() >> 4) + dx, (point.z() >> 4) + dz));
                }
            }
        }
        if (!land.read(around)) {
            return failed(points, List.of(), cancelled.getAsBoolean() ? LegPlanner.Reason.CANCELLED : LegPlanner.Reason.NOT_LOADED, 0,
                    new int[]{points.get(0).x(), points.get(0).z()});
        }
        List<int[]> snapped = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            if (point.city()) {
                snapped.add(new int[]{point.x(), point.z()});
                continue;
            }
            PlanPoints.Snap snap;
            if (i == 0 || i == points.size() - 1) {
                snap = PlanPoints.end(land, point.x(), point.z());
            } else {
                Point before = points.get(i - 1);
                Point after = points.get(i + 1);
                snap = PlanPoints.waypoint(land, point.x(), point.z(), new int[]{before.x(), before.z()}, new int[]{after.x(), after.z()});
            }
            if (!snap.ok()) {
                snapped.add(new int[]{point.x(), point.z()});
                LegPlanner.Reason reason = snap.failure() == PlanPoints.Failure.IN_WATER ? LegPlanner.Reason.END_IN_WATER
                        : LegPlanner.Reason.POINT_ON_BUILDING;
                return failed(points, snapped, reason, Math.max(0, i - 1), new int[]{point.x(), point.z()});
            }
            snapped.add(new int[]{snap.x(), snap.z()});
        }
        List<LegPlanner.Leg> legs = new ArrayList<>();
        List<RoadEstimate> estimates = new ArrayList<>();
        List<RouteCell> way = new ArrayList<>();
        int guessed = 0;
        LegPlanner planner = new LegPlanner(land, roadClass, style, rules, earth, kingdomId, cancelled);
        for (int i = 0; i + 1 < points.size(); i++) {
            Point a = points.get(i);
            Point b = points.get(i + 1);
            int[] from = snapped.get(i);
            int[] to = snapped.get(i + 1);
            String key = key(from, to, a, b, roadClass, style);
            LegPlanner.Leg leg = cache.get(key);
            if (leg == null) {
                List<int[]> starts = new ArrayList<>();
                if (a.city()) {
                    starts.addAll(a.targets());
                } else {
                    LandColumn column = land.at(from[0], from[1]);
                    starts.add(new int[]{from[0], from[1], column == null ? 64 : column.top()});
                }
                List<EngineeredSearch.Goal> goals = new ArrayList<>();
                if (b.city()) {
                    for (int[] target : b.targets()) {
                        goals.add(new EngineeredSearch.Goal(target[0], target[1], target[2]));
                    }
                } else if (i + 1 == points.size() - 1) {
                    LandColumn column = land.at(to[0], to[1]);
                    goals.add(new EngineeredSearch.Goal(to[0], to[1], column == null || column.water() ? EngineeredSearch.ANY : column.top()));
                } else {
                    // A point between is passed at any height of the deck: the profile joins the heights of its two legs.
                    goals.add(new EngineeredSearch.Goal(to[0], to[1], EngineeredSearch.ANY));
                }
                if (starts.isEmpty() || goals.isEmpty()) {
                    return failed(points, snapped, LegPlanner.Reason.END_WALLED, i, a.city() && starts.isEmpty() ? from : to);
                }
                leg = planner.plan(new LegPlanner.Spec(starts, goals, from[0], from[1], to[0], to[1]));
                if (leg.failure() == LegPlanner.Reason.CANCELLED) {
                    return failed(points, snapped, LegPlanner.Reason.CANCELLED, i, from);
                }
                if (leg.found()) {
                    cache.put(key, leg);
                }
            }
            legs.add(leg);
            if (!leg.found()) {
                return new Result(List.of(), snapped, legs, estimates, RoadEstimate.EMPTY, List.of(), 0, leg.failure(), i, leg.stuck(), guessed);
            }
            guessed += leg.stats().guessedColumns();
            List<RouteCell> cells = leg.cells();
            estimates.add(RoadEstimate.of(cells, roadClass, land, rules, earth, Math.hypot(to[0] - from[0], to[1] - from[1]), pace, kingdomId));
            way.addAll(way.isEmpty() ? cells : cells.subList(1, cells.size()));
            progress.accept((i + 1) * 90 / (points.size() - 1));
        }
        if (way.size() > Road.MAX_CELLS) {
            RouteCell at = way.get(Road.MAX_CELLS);
            return new Result(List.of(), snapped, legs, estimates, RoadEstimate.EMPTY, List.of(), 0, LegPlanner.Reason.TOO_LONG,
                    legs.size() - 1, new int[]{at.x(), at.z()}, guessed);
        }
        // The heights where the legs meet, joined by the profile of the whole road.
        List<RouteCell> cells = legs.size() > 1 ? planner.reprofile(way) : way;
        List<RoadLayout.ColumnSurvey> surveys = RoadSurvey.survey(cells, roadClass, land, earth);
        List<RoadLayout.Block> blocks = RoadLayout.blocks(cells, roadClass, RoadLayout.LEVELLED, surveys);
        double earthTotal = RoadSurvey.earthTotal(blocks, roadClass, rules, earth);
        int[] first = snapped.get(0);
        int[] last = snapped.get(snapped.size() - 1);
        RoadEstimate estimate = RoadEstimate.of(cells, roadClass, land, rules, earth, Math.hypot(last[0] - first[0], last[1] - first[1]), pace,
                kingdomId);
        progress.accept(100);
        return new Result(cells, snapped, legs, estimates, estimate, surveys, earthTotal, LegPlanner.Reason.NONE, -1, null, guessed);
    }

    private static Result failed(List<Point> points, List<int[]> snapped, LegPlanner.Reason reason, int leg, int[] at) {
        return new Result(List.of(), snapped, List.of(), List.of(), RoadEstimate.EMPTY, List.of(), 0, reason, leg, at, 0);
    }
}
