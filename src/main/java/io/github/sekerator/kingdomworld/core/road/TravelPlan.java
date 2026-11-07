package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The way of a caravan: points it passes and when, relative to its departure, and the pieces of way between them, each on
 * a section of a road or across country. A caravan far from players is where the time says it is along these points.
 *
 * @param points the points, the first at tick 0 and the last at {@link #ticks()}
 * @param pieces the pieces between them, as many as the points less one
 */
public record TravelPlan(List<Point> points, List<Piece> pieces) {

    /**
     * A point of the way.
     *
     * @param tick ticks after the departure the caravan passes it
     */
    public record Point(int x, int z, long tick) {
    }

    /**
     * A piece of the way from a point to the next.
     *
     * @param roadId    the road, or 0 across country
     * @param section   the section of the road
     * @param roadClass the class of the road, or {@code null} across country or on a section not built yet
     */
    public record Piece(long roadId, int section, RoadClass roadClass, double length) {
    }

    public TravelPlan {
        points = List.copyOf(points);
        pieces = List.copyOf(pieces);
        if (points.size() < 2 || pieces.size() != points.size() - 1) {
            throw new IllegalArgumentException("A travel plan needs points and the pieces between them");
        }
    }

    public long ticks() {
        return points.get(points.size() - 1).tick();
    }

    /**
     * Blocks of way on roads.
     */
    public double onRoads() {
        double sum = 0;
        for (Piece piece : pieces) {
            if (piece.roadClass() != null) {
                sum += piece.length();
            }
        }
        return sum;
    }

    public double length() {
        double sum = 0;
        for (Piece piece : pieces) {
            sum += piece.length();
        }
        return sum;
    }

    /**
     * Where the caravan is a number of ticks after its departure.
     */
    public double[] position(long tick) {
        return position(points, tick);
    }

    public static double[] position(List<Point> points, long tick) {
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) {
            return new double[]{0, 0};
        }
        if (tick <= points.get(0).tick()) {
            return new double[]{points.get(0).x(), points.get(0).z()};
        }
        for (int i = 1; i < points.size(); i++) {
            Point to = points.get(i);
            if (tick <= to.tick()) {
                Point from = points.get(i - 1);
                double span = Math.max(1, to.tick() - from.tick());
                double t = (tick - from.tick()) / span;
                return new double[]{from.x() + (to.x() - from.x()) * t, from.z() + (to.z() - from.z()) * t};
            }
        }
        Point last = points.get(points.size() - 1);
        return new double[]{last.x(), last.z()};
    }

    /**
     * The plan of a caravan across country in a straight line.
     */
    public static TravelPlan straight(int fromX, int fromZ, int toX, int toZ, long ticks) {
        double length = Math.sqrt(Math.pow((double) toX - fromX, 2) + Math.pow((double) toZ - fromZ, 2));
        List<Point> points = new ArrayList<>();
        List<Piece> pieces = new ArrayList<>();
        // Pieces of at most 64 blocks, so that robbers lie in wait on each of them alone.
        int parts = Math.max(1, (int) Math.ceil(length / 64.0));
        for (int i = 0; i <= parts; i++) {
            double t = i / (double) parts;
            points.add(new Point((int) Math.round(fromX + (toX - fromX) * t), (int) Math.round(fromZ + (toZ - fromZ) * t),
                    Math.round(ticks * t)));
            if (i > 0) {
                pieces.add(new Piece(0, 0, null, length / parts));
            }
        }
        return new TravelPlan(points, pieces);
    }
}
