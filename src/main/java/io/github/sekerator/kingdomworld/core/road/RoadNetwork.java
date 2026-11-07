package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;

/**
 * The roads of a dimension as a graph for caravans: the points where sections meet are its nodes; a built section is an
 * edge as fast as its class and condition allow, a section not built yet is walked at the pace across country; from the
 * start, to the goal and between the ends of roads and nearby roads a caravan goes across country in a straight line. The
 * fastest way wins, so a caravan takes a road when the detour pays.
 */
public final class RoadNetwork {
    /** Blocks between the end of a road and another road within which a caravan changes from one to the other. */
    public static final int JUNCTION = 64;
    /** Every how many blocks of road the plan of a caravan has a point. */
    public static final int POINT_EVERY = 4;
    /** The longest piece of a way across country, in blocks. */
    public static final double PIECE = 64.0;

    private record Node(int x, int z, Road road, int boundary) {
    }

    private record Edge(int to, double ticks, double length, Road road, int section, RoadClass roadClass, boolean forward) {
    }

    private RoadNetwork() {
    }

    /**
     * Ticks a caravan takes for a distance at a pace in blocks per minute.
     */
    public static double ticks(double length, double blocksPerMinute) {
        return length / Math.max(0.001, blocksPerMinute) * 1200.0;
    }

    /**
     * How many times faster than across country a caravan goes on a built section.
     */
    public static double speedFactor(RoadClass roadClass, double condition, RoadRules rules) {
        double full = rules.caravanSpeed(roadClass);
        return 1.0 + (full - 1.0) * (0.5 + 0.5 * Math.max(0.0, Math.min(1.0, condition)));
    }

    /**
     * The fastest way of a caravan between two points of a dimension.
     *
     * @param blocksPerMinute the pace of a caravan across country
     * @param minTicks        the shortest trip
     * @param maxTicks        the longest trip; slower ways are cut to it
     */
    public static TravelPlan plan(Collection<Road> roads, String dimension, int fromX, int fromZ, int toX, int toZ, double blocksPerMinute,
                                  RoadRules rules, long minTicks, long maxTicks) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(fromX, fromZ, null, -1));
        nodes.add(new Node(toX, toZ, null, -1));
        List<List<Edge>> edges = new ArrayList<>();
        edges.add(new ArrayList<>());
        edges.add(new ArrayList<>());
        List<int[]> ends = new ArrayList<>();
        for (Road road : roads) {
            if (!road.dimension().equals(dimension) || road.status() == Road.Status.WAITING_CONSENT || road.paid() == 0) {
                continue;
            }
            int first = nodes.size();
            int count = road.sections().size();
            for (int b = 0; b <= count; b++) {
                RouteCell cell = road.boundary(b);
                nodes.add(new Node(cell.x(), cell.z(), road, b));
                edges.add(new ArrayList<>());
            }
            for (int s = 0; s < count; s++) {
                Road.Section section = road.section(s);
                double length = road.sectionLength(section);
                boolean built = section.done();
                double pace = built ? blocksPerMinute * speedFactor(road.roadClass(), section.condition(), rules) : blocksPerMinute;
                double ticks = ticks(length, pace);
                RoadClass roadClass = built ? road.roadClass() : null;
                edges.get(first + s).add(new Edge(first + s + 1, ticks, length, road, s, roadClass, true));
                edges.get(first + s + 1).add(new Edge(first + s, ticks, length, road, s, roadClass, false));
            }
            ends.add(new int[]{first, first + count});
        }
        int total = nodes.size();
        // Across country: from the start and to the goal everywhere, and from the ends of roads to other roads nearby.
        for (int i = 1; i < total; i++) {
            addStraight(nodes, edges, 0, i, blocksPerMinute);
        }
        for (int i = 2; i < total; i++) {
            addStraight(nodes, edges, i, 1, blocksPerMinute);
        }
        for (int[] end : ends) {
            for (int e : end) {
                Node node = nodes.get(e);
                for (int j = 2; j < total; j++) {
                    Node other = nodes.get(j);
                    if (other.road() == node.road()) {
                        continue;
                    }
                    double dx = other.x() - node.x();
                    double dz = other.z() - node.z();
                    if (dx * dx + dz * dz <= (double) JUNCTION * JUNCTION) {
                        addStraight(nodes, edges, e, j, blocksPerMinute);
                        addStraight(nodes, edges, j, e, blocksPerMinute);
                    }
                }
            }
        }
        double[] best = new double[total];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        Edge[] via = new Edge[total];
        int[] previous = new int[total];
        Arrays.fill(previous, -1);
        best[0] = 0;
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        queue.add(new double[]{0, 0});
        while (!queue.isEmpty()) {
            double[] top = queue.poll();
            int at = (int) top[1];
            if (top[0] > best[at]) {
                continue;
            }
            if (at == 1) {
                break;
            }
            for (Edge edge : edges.get(at)) {
                double next = best[at] + edge.ticks();
                if (next < best[edge.to()]) {
                    best[edge.to()] = next;
                    via[edge.to()] = edge;
                    previous[edge.to()] = at;
                    queue.add(new double[]{next, edge.to()});
                }
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int at = 1; at >= 0; at = previous[at]) {
            order.add(0, at);
            if (at == 0) {
                break;
            }
        }
        List<TravelPlan.Point> points = new ArrayList<>();
        List<TravelPlan.Piece> pieces = new ArrayList<>();
        double clock = 0;
        points.add(new TravelPlan.Point(fromX, fromZ, 0));
        for (int i = 1; i < order.size(); i++) {
            Node a = nodes.get(order.get(i - 1));
            Node b = nodes.get(order.get(i));
            Edge edge = via[order.get(i)];
            if (edge.road() == null) {
                clock = straight(points, pieces, a, b, clock, edge.ticks());
            } else {
                clock = along(points, pieces, edge, clock);
            }
        }
        double scale = 1.0;
        if (clock > maxTicks) {
            scale = maxTicks / clock;
        } else if (clock < minTicks) {
            scale = minTicks / Math.max(1.0, clock);
        }
        List<TravelPlan.Point> scaled = new ArrayList<>(points.size());
        long last = -1;
        for (TravelPlan.Point point : points) {
            long tick = Math.max(last, Math.round(point.tick() * scale));
            scaled.add(new TravelPlan.Point(point.x(), point.z(), tick));
            last = tick;
        }
        return new TravelPlan(scaled, pieces);
    }

    private static void addStraight(List<Node> nodes, List<List<Edge>> edges, int from, int to, double blocksPerMinute) {
        Node a = nodes.get(from);
        Node b = nodes.get(to);
        double length = Math.sqrt(Math.pow((double) b.x() - a.x(), 2) + Math.pow((double) b.z() - a.z(), 2));
        edges.get(from).add(new Edge(to, ticks(length, blocksPerMinute), length, null, 0, null, true));
    }

    private static double straight(List<TravelPlan.Point> points, List<TravelPlan.Piece> pieces, Node a, Node b, double clock, double ticks) {
        double length = Math.sqrt(Math.pow((double) b.x() - a.x(), 2) + Math.pow((double) b.z() - a.z(), 2));
        if (length < 0.5) {
            return clock;
        }
        int parts = Math.max(1, (int) Math.ceil(length / PIECE));
        for (int p = 1; p <= parts; p++) {
            double t = p / (double) parts;
            points.add(new TravelPlan.Point((int) Math.round(a.x() + (b.x() - a.x()) * t), (int) Math.round(a.z() + (b.z() - a.z()) * t),
                    Math.round(clock + ticks * t)));
            pieces.add(new TravelPlan.Piece(0, 0, null, length / parts));
        }
        return clock + ticks;
    }

    private static double along(List<TravelPlan.Point> points, List<TravelPlan.Piece> pieces, Edge edge, double clock) {
        Road road = edge.road();
        Road.Section section = road.section(edge.section());
        List<RouteCell> cells = road.cells();
        int first = section.firstCell();
        int end = Math.min(cells.size() - 1, section.endCell());
        List<RouteCell> way = new ArrayList<>();
        for (int c = first; c <= end; c++) {
            way.add(cells.get(c));
        }
        if (!edge.forward()) {
            java.util.Collections.reverse(way);
        }
        double perBlock = edge.ticks() / Math.max(1.0, edge.length());
        double walked = 0;
        RouteCell lastPoint = way.get(0);
        for (int c = 1; c < way.size(); c++) {
            RouteCell before = way.get(c - 1);
            RouteCell cell = way.get(c);
            walked += Math.sqrt(Math.pow(cell.x() - before.x(), 2) + Math.pow(cell.z() - before.z(), 2));
            if (c % POINT_EVERY == 0 || c == way.size() - 1) {
                double piece = Math.sqrt(Math.pow(cell.x() - lastPoint.x(), 2) + Math.pow(cell.z() - lastPoint.z(), 2));
                points.add(new TravelPlan.Point(cell.x(), cell.z(), Math.round(clock + walked * perBlock)));
                pieces.add(new TravelPlan.Piece(road.id(), edge.section(), edge.roadClass(), Math.max(0.5, piece)));
                lastPoint = cell;
            }
        }
        return clock + edge.ticks();
    }
}
