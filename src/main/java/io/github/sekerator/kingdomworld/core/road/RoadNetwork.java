package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    /** How many times faster than across country a caravan goes along a track of the plan not laid as a road yet. */
    public static final double TRACK_SPEED = 1.2;

    /**
     * A road of the plan not laid yet: a track along its rough line, a little faster than across country.
     *
     * @param xs the blocks of its points along x
     * @param zs the blocks of its points along z
     */
    public record Track(int[] xs, int[] zs) {
        public Track {
            if (xs.length != zs.length || xs.length < 2) {
                throw new IllegalArgumentException("A track needs two points at least");
            }
        }
    }

    private record Node(int x, int z, Road road, int boundary) {
    }

    private record Edge(int to, double ticks, double length, Road road, int section, RoadClass roadClass, boolean forward) {
    }

    /**
     * A way across country between the end of a road and a nearby road, before the pace of a trip makes it ticks.
     */
    private record Link(int to, double length) {
    }

    /** Dimensions whose skeleton is kept at most. */
    private static final int KEPT_DIMENSIONS = 8;
    /** The skeleton of the roads of a dimension made last, by dimension. */
    private static final Map<String, Skeleton> SKELETONS = new LinkedHashMap<>(KEPT_DIMENSIONS * 2, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Skeleton> eldest) {
            return size() > KEPT_DIMENSIONS;
        }
    };

    /**
     * What the roads taken make of the graph by themselves: the points where their sections meet, the lengths of the sections
     * and the ways across country between the ends of roads and nearby roads, each point with its ways in the order the graph
     * adds them. A road keeps its cells and sections for good, so the same roads in the same order make the same skeleton;
     * whether a section is built, how worn it is, the rules and the pace are read for every trip. Nothing in it is changed once
     * it is made.
     */
    private static final class Skeleton {
        /** The roads it is made of, in order. */
        final Road[] roads;
        /** The points of the roads; the point {@code i} here is the node {@code i + 2} of the graph, after the start and the goal. */
        final List<Node> nodes;
        /** The node of the first point of every road. */
        final int[] firsts;
        /** The lengths of the sections of every road. */
        final double[][] lengths;
        /** The ways across country from every point, as {@link #nodes}. */
        final List<List<Link>> links;

        Skeleton(List<Road> taken) {
            roads = taken.toArray(new Road[0]);
            firsts = new int[roads.length];
            lengths = new double[roads.length][];
            List<Node> all = new ArrayList<>();
            all.add(null);
            all.add(null);
            List<int[]> ends = new ArrayList<>();
            for (int r = 0; r < roads.length; r++) {
                Road road = roads[r];
                int first = all.size();
                int count = road.sections().size();
                for (int b = 0; b <= count; b++) {
                    RouteCell cell = road.boundary(b);
                    all.add(new Node(cell.x(), cell.z(), road, b));
                }
                double[] of = new double[count];
                for (int s = 0; s < count; s++) {
                    of[s] = road.sectionLength(road.section(s));
                }
                firsts[r] = first;
                lengths[r] = of;
                ends.add(new int[]{first, first + count});
            }
            int total = all.size();
            List<List<Link>> found = new ArrayList<>(total - 2);
            for (int i = 2; i < total; i++) {
                found.add(new ArrayList<>());
            }
            for (int[] end : ends) {
                for (int e : end) {
                    Node node = all.get(e);
                    for (int j = 2; j < total; j++) {
                        Node other = all.get(j);
                        if (other.road() == node.road()) {
                            continue;
                        }
                        double dx = other.x() - node.x();
                        double dz = other.z() - node.z();
                        if (dx * dx + dz * dz <= (double) JUNCTION * JUNCTION) {
                            found.get(e - 2).add(new Link(j, distance(node, other)));
                            found.get(j - 2).add(new Link(e, distance(other, node)));
                        }
                    }
                }
            }
            List<List<Link>> kept = new ArrayList<>(found.size());
            for (List<Link> list : found) {
                kept.add(list.isEmpty() ? List.of() : List.copyOf(list));
            }
            nodes = List.copyOf(all.subList(2, total));
            links = List.copyOf(kept);
        }

        /**
         * Whether it is made of these very roads in this order.
         */
        boolean madeOf(List<Road> taken) {
            if (taken.size() != roads.length) {
                return false;
            }
            for (int r = 0; r < roads.length; r++) {
                if (taken.get(r) != roads[r]) {
                    return false;
                }
            }
            return true;
        }
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
        return plan(roads, List.of(), dimension, fromX, fromZ, toX, toZ, blocksPerMinute, rules, minTicks, maxTicks);
    }

    /**
     * The fastest way of a caravan between two points of a dimension, by the roads and the tracks of the plan: trade goes along
     * the network of the states before it is paved.
     *
     * @param tracks the roads of the plan of the dimension not laid yet
     */
    public static TravelPlan plan(Collection<Road> roads, Collection<Track> tracks, String dimension, int fromX, int fromZ, int toX, int toZ,
                                  double blocksPerMinute, RoadRules rules, long minTicks, long maxTicks) {
        // Whether a road is taken is read anew for every trip: a road is paid for and let through without a change of the roads.
        List<Road> taken = new ArrayList<>();
        for (Road road : roads) {
            if (!road.dimension().equals(dimension) || road.status() == Road.Status.WAITING_CONSENT || road.paid() == 0) {
                continue;
            }
            taken.add(road);
        }
        Skeleton skeleton = skeleton(dimension, taken);
        int trackPoints = 0;
        for (Track track : tracks) {
            trackPoints += track.xs().length;
        }
        int total = skeleton.nodes.size() + 2 + trackPoints;
        List<Node> nodes = new ArrayList<>(total);
        nodes.add(new Node(fromX, fromZ, null, -1));
        nodes.add(new Node(toX, toZ, null, -1));
        nodes.addAll(skeleton.nodes);
        List<List<Edge>> edges = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            edges.add(new ArrayList<>());
        }
        // The tracks of the plan: their points after those of the roads, each to the next a little faster than across country,
        // and the ends of tracks joined where they meet at a settlement.
        List<Integer> ends = new ArrayList<>();
        int trackAt = skeleton.nodes.size() + 2;
        for (Track track : tracks) {
            for (int p = 0; p < track.xs().length; p++) {
                nodes.add(new Node(track.xs()[p], track.zs()[p], null, -1));
                if (p > 0) {
                    double length = distance(nodes.get(trackAt + p - 1), nodes.get(trackAt + p));
                    double ticks = ticks(length, blocksPerMinute * TRACK_SPEED);
                    edges.get(trackAt + p - 1).add(new Edge(trackAt + p, ticks, length, null, 0, null, true));
                    edges.get(trackAt + p).add(new Edge(trackAt + p - 1, ticks, length, null, 0, null, true));
                }
            }
            ends.add(trackAt);
            ends.add(trackAt + track.xs().length - 1);
            trackAt += track.xs().length;
        }
        for (int i = 0; i < ends.size(); i++) {
            for (int j = i + 1; j < ends.size(); j++) {
                double length = distance(nodes.get(ends.get(i)), nodes.get(ends.get(j)));
                if (length <= JUNCTION) {
                    double ticks = ticks(length, blocksPerMinute);
                    edges.get(ends.get(i)).add(new Edge(ends.get(j), ticks, length, null, 0, null, true));
                    edges.get(ends.get(j)).add(new Edge(ends.get(i), ticks, length, null, 0, null, true));
                }
            }
        }
        // How fast a section is depends on whether it is built and how worn it is, and on the rules and the pace of the trip.
        for (int r = 0; r < skeleton.roads.length; r++) {
            Road road = skeleton.roads[r];
            int first = skeleton.firsts[r];
            double[] lengths = skeleton.lengths[r];
            for (int s = 0; s < lengths.length; s++) {
                Road.Section section = road.section(s);
                double length = lengths[s];
                boolean built = section.done();
                double pace = built ? blocksPerMinute * speedFactor(road.roadClass(), section.condition(), rules) : blocksPerMinute;
                double ticks = ticks(length, pace);
                RoadClass roadClass = built ? road.roadClass() : null;
                edges.get(first + s).add(new Edge(first + s + 1, ticks, length, road, s, roadClass, true));
                edges.get(first + s + 1).add(new Edge(first + s, ticks, length, road, s, roadClass, false));
            }
        }
        // Across country: from the start and to the goal everywhere, and from the ends of roads to other roads nearby.
        for (int i = 1; i < total; i++) {
            addStraight(nodes, edges, 0, i, blocksPerMinute);
        }
        for (int i = 2; i < total; i++) {
            addStraight(nodes, edges, i, 1, blocksPerMinute);
        }
        for (int i = 2; i < skeleton.nodes.size() + 2; i++) {
            List<Edge> from = edges.get(i);
            for (Link link : skeleton.links.get(i - 2)) {
                from.add(new Edge(link.to(), ticks(link.length(), blocksPerMinute), link.length(), null, 0, null, true));
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
        double length = distance(nodes.get(from), nodes.get(to));
        edges.get(from).add(new Edge(to, ticks(length, blocksPerMinute), length, null, 0, null, true));
    }

    private static double distance(Node a, Node b) {
        return Math.sqrt(Math.pow((double) b.x() - a.x(), 2) + Math.pow((double) b.z() - a.z(), 2));
    }

    /**
     * The skeleton of the roads of a dimension that are taken, made again when they are other roads or in another order.
     */
    private static Skeleton skeleton(String dimension, List<Road> taken) {
        synchronized (SKELETONS) {
            Skeleton kept = SKELETONS.get(dimension);
            if (kept != null && kept.madeOf(taken)) {
                return kept;
            }
        }
        Skeleton made = new Skeleton(taken);
        synchronized (SKELETONS) {
            SKELETONS.put(dimension, made);
        }
        return made;
    }

    /**
     * Forgets the skeletons kept, so that the next trips make them anew; for tests.
     */
    static void forget() {
        synchronized (SKELETONS) {
            SKELETONS.clear();
        }
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
