package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Where a squad is and where it marches far from players: its place, the points still ahead of it (the way found for the
 * current stretch, then the points it was told to march through, then the place of its order) and the way it faces, which
 * is where its front is in a battle.
 *
 * @param at    the place of the squad
 * @param path  the points still ahead, in order; empty when the squad stands
 * @param faceX the way the squad faces, along x
 * @param faceZ the way the squad faces, along z
 */
public record March(WorldPos at, List<WorldPos> path, double faceX, double faceZ) {
    /** Most points ahead of a squad. */
    public static final int MAX_PATH = 512;

    public March {
        Objects.requireNonNull(at, "at");
        path = List.copyOf(path.size() > MAX_PATH ? path.subList(0, MAX_PATH) : path);
        if (!Double.isFinite(faceX) || !Double.isFinite(faceZ)) {
            faceX = 0.0;
            faceZ = 0.0;
        }
    }

    /**
     * A squad standing at a place.
     */
    public static March standing(WorldPos at) {
        return new March(at, List.of(), 0.0, 0.0);
    }

    public boolean arrived() {
        return path.isEmpty();
    }

    /**
     * The last point ahead, or the place for a squad that stands.
     */
    public WorldPos destination() {
        return path.isEmpty() ? at : path.get(path.size() - 1);
    }

    /**
     * The same squad with other points ahead.
     */
    public March towards(List<WorldPos> points) {
        return new March(at, points, faceX, faceZ);
    }

    /**
     * The same squad facing another way, for a squad that turns to its enemy.
     */
    public March facing(double x, double z) {
        return new March(at, path, x, z);
    }

    /**
     * The length of the way still ahead, in blocks, along the points; a point in another dimension ends the count.
     */
    public double remaining() {
        double sum = 0.0;
        WorldPos from = at;
        for (WorldPos point : path) {
            if (!point.dimension().equals(from.dimension())) {
                break;
            }
            sum += Math.hypot(point.x() - from.x(), point.z() - from.z());
            from = point;
        }
        return sum;
    }

    /**
     * The squad after marching some blocks along its way: points reached are dropped, the squad faces the way it went.
     * A point in another dimension cannot be marched to; the squad stops before it.
     */
    public March step(double blocks) {
        if (path.isEmpty() || !(blocks > 0.0)) {
            return this;
        }
        List<WorldPos> left = new ArrayList<>(path);
        double x = at.x();
        double y = at.y();
        double z = at.z();
        double fx = faceX;
        double fz = faceZ;
        double budget = blocks;
        while (!left.isEmpty() && budget > 0.0) {
            WorldPos next = left.get(0);
            if (!next.dimension().equals(at.dimension())) {
                left.clear();
                break;
            }
            double dx = next.x() - x;
            double dz = next.z() - z;
            double length = Math.hypot(dx, dz);
            if (length > 1e-6) {
                fx = dx / length;
                fz = dz / length;
            }
            if (length <= budget) {
                budget -= length;
                x = next.x();
                y = next.y();
                z = next.z();
                left.remove(0);
            } else {
                x += dx / length * budget;
                z += dz / length * budget;
                y += (next.y() - y) * (budget / length);
                budget = 0.0;
            }
        }
        WorldPos now = new WorldPos(at.dimension(), (int) Math.round(x), (int) Math.round(y), (int) Math.round(z));
        return new March(now, left, fx, fz);
    }

    /**
     * Seconds the way ahead takes at a speed in blocks per second.
     */
    public long secondsLeft(double speed) {
        if (path.isEmpty() || !(speed > 0.0)) {
            return 0;
        }
        return (long) Math.ceil(remaining() / speed);
    }
}
