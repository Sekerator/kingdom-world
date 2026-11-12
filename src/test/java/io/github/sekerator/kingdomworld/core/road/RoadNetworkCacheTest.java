package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The way of a caravan through roads whose graph is kept between trips is the way the graph made anew for every trip gives,
 * as the roads are paid for, wear, wait for consent, come and go.
 */
class RoadNetworkCacheTest {
    private static final UUID KINGDOM = new UUID(5, 5);
    private static final String WORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final double[] PACES = {60.0, 45.5, 120.0};

    /**
     * A road that wanders one way from a random point, a random part of it paid for and worn.
     */
    private static Road road(SplittableRandom random, long id, String dimension) {
        int x = random.nextInt(-300, 301);
        int z = random.nextInt(-300, 301);
        int dirX = random.nextBoolean() ? 1 : -1;
        int dirZ = random.nextBoolean() ? 1 : -1;
        int count = random.nextInt(2, 160);
        List<RouteCell> cells = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cells.add(new RouteCell(x, 64, z, RouteCell.Kind.GROUND, 0, null));
            int step = random.nextInt(3);
            if (step != 1) {
                x += dirX;
            }
            if (step != 0) {
                z += dirZ;
            }
        }
        RoadClass roadClass = RoadClass.values()[random.nextInt(RoadClass.values().length)];
        Road.Status status = random.nextInt(6) == 0 ? Road.Status.WAITING_CONSENT : Road.Status.BUILDING;
        RouteCell first = cells.get(0);
        RouteCell last = cells.get(cells.size() - 1);
        Road road = new Road(id, KINGDOM, roadClass, dimension, Endpoint.point(first.x(), first.z()), Endpoint.point(last.x(), last.z()),
                cells, Road.Mode.OWN, 0, status, List.of());
        if (random.nextInt(5) != 0) {
            for (Road.Section section : road.sections()) {
                int kind = random.nextInt(3);
                int blocks = kind == 0 ? 0 : kind == 1 ? random.nextInt(section.units() + 1) : section.units();
                pay(section, blocks);
                section.setCondition(random.nextDouble());
            }
        }
        return road;
    }

    private static void pay(Road.Section section, int blocks) {
        for (int i = 0; i < blocks && !section.done(); i++) {
            section.markPaid(Map.of());
        }
    }

    /**
     * Changes the roads as the game does, without telling anyone: a block paid for, a section worn or repaired, a road let
     * through or built, a road planned or given up, two roads swapped.
     */
    private static void change(SplittableRandom random, List<Road> roads, long[] nextId) {
        int what = random.nextInt(7);
        if (roads.isEmpty() || what == 4) {
            roads.add(road(random, nextId[0]++, random.nextInt(4) == 0 ? NETHER : WORLD));
            return;
        }
        Road road = roads.get(random.nextInt(roads.size()));
        Road.Section section = road.section(random.nextInt(road.sections().size()));
        switch (what) {
            case 0 -> pay(section, 1 + random.nextInt(4));
            case 1 -> section.setCondition(random.nextDouble());
            case 2 -> road.setStatus(Road.Status.values()[random.nextInt(Road.Status.values().length)]);
            case 3 -> road.sections().forEach(each -> pay(each, each.units()));
            case 5 -> roads.remove(road);
            default -> {
                int other = random.nextInt(roads.size());
                int at = roads.indexOf(road);
                roads.set(at, roads.get(other));
                roads.set(other, road);
            }
        }
    }

    @Test
    void theKeptGraphGivesTheWayOfAGraphMadeAnew() {
        RoadRules.Builder fast = new RoadRules.Builder();
        fast.caravanSpeed = new double[]{2.0, 4.0, 7.5};
        RoadRules[] rules = {RoadRules.DEFAULT, fast.build()};
        int onRoads = 0;
        for (long seed = 1; seed <= 6; seed++) {
            SplittableRandom random = new SplittableRandom(seed * 7919L);
            RoadNetwork.forget();
            List<Road> roads = new ArrayList<>();
            long[] nextId = {1};
            int start = random.nextInt(4, 30);
            for (int i = 0; i < start; i++) {
                roads.add(road(random, nextId[0]++, random.nextInt(4) == 0 ? NETHER : WORLD));
            }
            for (int round = 0; round < 120; round++) {
                if (round > 0 && random.nextInt(3) != 0) {
                    change(random, roads, nextId);
                }
                String dimension = random.nextInt(5) == 0 ? NETHER : WORLD;
                int fromX = random.nextInt(-400, 401);
                int fromZ = random.nextInt(-400, 401);
                int toX = random.nextInt(-400, 401);
                int toZ = random.nextInt(-400, 401);
                if (!roads.isEmpty() && random.nextInt(3) == 0) {
                    // From one end of a road to the other, so that roads are taken often.
                    Road along = roads.get(random.nextInt(roads.size()));
                    dimension = along.dimension();
                    fromX = along.cells().get(0).x();
                    fromZ = along.cells().get(0).z();
                    toX = along.cells().get(along.cells().size() - 1).x();
                    toZ = along.cells().get(along.cells().size() - 1).z();
                }
                double pace = PACES[random.nextInt(PACES.length)];
                RoadRules rule = rules[random.nextInt(rules.length)];
                long minTicks = random.nextBoolean() ? 1L : 1_200L;
                long maxTicks = random.nextBoolean() ? Long.MAX_VALUE : 24_000L * 30;
                String at = "seed " + seed + " round " + round;
                TravelPlan anew = fresh(roads, dimension, fromX, fromZ, toX, toZ, pace, rule, minTicks, maxTicks);
                TravelPlan kept = RoadNetwork.plan(roads, dimension, fromX, fromZ, toX, toZ, pace, rule, minTicks, maxTicks);
                assertEquals(anew, kept, at);
                // Asked again at once, the kept graph answers the same.
                assertEquals(anew, RoadNetwork.plan(List.copyOf(roads), dimension, fromX, fromZ, toX, toZ, pace, rule, minTicks, maxTicks), at);
                if (anew.onRoads() > 0) {
                    onRoads++;
                }
                // The other dimension keeps its own graph meanwhile.
                String elsewhere = dimension.equals(WORLD) ? NETHER : WORLD;
                assertEquals(fresh(roads, elsewhere, toX, toZ, fromX, fromZ, pace, rule, minTicks, maxTicks),
                        RoadNetwork.plan(roads, elsewhere, toX, toZ, fromX, fromZ, pace, rule, minTicks, maxTicks), at);
            }
        }
        assertTrue(onRoads > 0, "no caravan took a road, the test proves little");
    }

    @Test
    void aRoadPaidForOrWornWithoutAChangeOfTheRoadsCounts() {
        SplittableRandom random = new SplittableRandom(42);
        List<Road> roads = new ArrayList<>();
        Road road = null;
        while (road == null || road.sections().size() < 4) {
            road = road(random, 1, WORLD);
        }
        road.setStatus(Road.Status.BUILDING);
        road.sections().forEach(section -> section.restore(0, 0, 1.0, false, 0, Map.of()));
        roads.add(road);
        RouteCell first = road.cells().get(0);
        RouteCell last = road.cells().get(road.cells().size() - 1);
        RoadNetwork.forget();
        TravelPlan before = RoadNetwork.plan(roads, WORLD, first.x(), first.z(), last.x(), last.z(), 60.0, RoadRules.DEFAULT, 1, Long.MAX_VALUE);
        assertEquals(0.0, before.onRoads(), 1e-9);
        road.sections().forEach(section -> pay(section, section.units()));
        TravelPlan paid = RoadNetwork.plan(roads, WORLD, first.x(), first.z(), last.x(), last.z(), 60.0, RoadRules.DEFAULT, 1, Long.MAX_VALUE);
        assertEquals(fresh(roads, WORLD, first.x(), first.z(), last.x(), last.z(), 60.0, RoadRules.DEFAULT, 1, Long.MAX_VALUE), paid);
        assertTrue(paid.onRoads() > 0, "a road paid for is not taken");
        road.sections().forEach(section -> section.setCondition(0.0));
        TravelPlan worn = RoadNetwork.plan(roads, WORLD, first.x(), first.z(), last.x(), last.z(), 60.0, RoadRules.DEFAULT, 1, Long.MAX_VALUE);
        assertEquals(fresh(roads, WORLD, first.x(), first.z(), last.x(), last.z(), 60.0, RoadRules.DEFAULT, 1, Long.MAX_VALUE), worn);
        assertTrue(worn.ticks() > paid.ticks(), "a worn road is as fast as a new one");
        road.setStatus(Road.Status.WAITING_CONSENT);
        TravelPlan waiting = RoadNetwork.plan(roads, WORLD, first.x(), first.z(), last.x(), last.z(), 60.0, RoadRules.DEFAULT, 1, Long.MAX_VALUE);
        assertEquals(0.0, waiting.onRoads(), 1e-9);
    }

    // ---------------------------------------------------------------- the graph made anew for every trip

    private record Node(int x, int z, Road road, int boundary) {
    }

    private record Edge(int to, double ticks, double length, Road road, int section, RoadClass roadClass, boolean forward) {
    }

    /**
     * The way of a caravan as it was planned before the graph was kept: every trip makes the graph of all roads anew.
     */
    private static TravelPlan fresh(Collection<Road> roads, String dimension, int fromX, int fromZ, int toX, int toZ, double blocksPerMinute,
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
                double pace = built ? blocksPerMinute * RoadNetwork.speedFactor(road.roadClass(), section.condition(), rules) : blocksPerMinute;
                double ticks = RoadNetwork.ticks(length, pace);
                RoadClass roadClass = built ? road.roadClass() : null;
                edges.get(first + s).add(new Edge(first + s + 1, ticks, length, road, s, roadClass, true));
                edges.get(first + s + 1).add(new Edge(first + s, ticks, length, road, s, roadClass, false));
            }
            ends.add(new int[]{first, first + count});
        }
        int total = nodes.size();
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
                    if (dx * dx + dz * dz <= (double) RoadNetwork.JUNCTION * RoadNetwork.JUNCTION) {
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
        edges.get(from).add(new Edge(to, RoadNetwork.ticks(length, blocksPerMinute), length, null, 0, null, true));
    }

    private static double straight(List<TravelPlan.Point> points, List<TravelPlan.Piece> pieces, Node a, Node b, double clock, double ticks) {
        double length = Math.sqrt(Math.pow((double) b.x() - a.x(), 2) + Math.pow((double) b.z() - a.z(), 2));
        if (length < 0.5) {
            return clock;
        }
        int parts = Math.max(1, (int) Math.ceil(length / RoadNetwork.PIECE));
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
            if (c % RoadNetwork.POINT_EVERY == 0 || c == way.size() - 1) {
                double piece = Math.sqrt(Math.pow(cell.x() - lastPoint.x(), 2) + Math.pow(cell.z() - lastPoint.z(), 2));
                points.add(new TravelPlan.Point(cell.x(), cell.z(), Math.round(clock + walked * perBlock)));
                pieces.add(new TravelPlan.Piece(road.id(), edge.section(), edge.roadClass(), Math.max(0.5, piece)));
                lastPoint = cell;
            }
        }
        return clock + edge.ticks();
    }
}
