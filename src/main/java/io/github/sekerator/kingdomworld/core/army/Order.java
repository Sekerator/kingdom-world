package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a squad is told to do.
 *
 * @param points the point held, the route of a patrol, or the center of a guarded area
 * @param radius the radius of a guarded area, in chunks around its center
 * @param player the player followed
 * @param deal   the deal whose caravans are escorted
 */
public record Order(Kind kind, List<WorldPos> points, int radius, UUID player, long deal) {
    /** Most points of a patrol route. */
    public static final int MAX_POINTS = 8;
    /** Largest radius of a guarded area, in chunks. */
    public static final int MAX_RADIUS = 4;

    public static final Order NONE = new Order(Kind.NONE, List.of(), 0, null, 0);

    /**
     * Kinds of orders.
     */
    public enum Kind {
        /** No order: the soldiers train at the barracks or stay at home. */
        NONE,
        /** Hold a point: a garrison. */
        HOLD,
        /** Walk a route of points, round and round. */
        PATROL,
        /** Guard an area of chunks. */
        GUARD,
        /** Follow a player: the king, the marshal or a subject they chose. */
        FOLLOW,
        /** Go with the caravans of a deal. */
        ESCORT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Kind> byId(String id) {
            for (Kind kind : values()) {
                if (kind.id().equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    public Order {
        Objects.requireNonNull(kind, "kind");
        points = List.copyOf(points);
        boolean valid = switch (kind) {
            case NONE -> points.isEmpty() && player == null && deal == 0;
            case HOLD -> points.size() == 1;
            case PATROL -> points.size() >= 1 && points.size() <= MAX_POINTS;
            case GUARD -> points.size() == 1 && radius >= 0 && radius <= MAX_RADIUS;
            case FOLLOW -> player != null && points.isEmpty();
            case ESCORT -> deal > 0 && points.isEmpty();
        };
        if (!valid) {
            throw new IllegalArgumentException("Order out of range: " + kind);
        }
        if (kind != Kind.GUARD) {
            radius = 0;
        }
        if (kind != Kind.FOLLOW) {
            player = null;
        }
        if (kind != Kind.ESCORT) {
            deal = 0;
        }
    }

    public static Order hold(WorldPos point) {
        return new Order(Kind.HOLD, List.of(point), 0, null, 0);
    }

    public static Order patrol(List<WorldPos> route) {
        return new Order(Kind.PATROL, route, 0, null, 0);
    }

    public static Order guard(WorldPos center, int radius) {
        return new Order(Kind.GUARD, List.of(center), radius, null, 0);
    }

    public static Order follow(UUID player) {
        return new Order(Kind.FOLLOW, List.of(), 0, player, 0);
    }

    public static Order escort(long deal) {
        return new Order(Kind.ESCORT, List.of(), 0, null, deal);
    }

    /**
     * A patrol with one more point at the end, or a new patrol from a point.
     */
    public Order withPatrolPoint(WorldPos point) {
        List<WorldPos> route = new ArrayList<>(kind == Kind.PATROL ? points : List.of());
        if (route.size() >= MAX_POINTS) {
            route.remove(0);
        }
        route.add(point);
        return patrol(route);
    }

    /**
     * Where the squad stands when nothing else calls it: the point held, the first point of the route, the center of the
     * area; empty for the other orders.
     */
    public Optional<WorldPos> anchor() {
        return points.isEmpty() ? Optional.empty() : Optional.of(points.get(0));
    }

    /**
     * Whether a place lies in the guarded area.
     */
    public boolean covers(WorldPos pos) {
        if (kind != Kind.GUARD || points.isEmpty() || !points.get(0).dimension().equals(pos.dimension())) {
            return false;
        }
        WorldPos center = points.get(0);
        return Math.abs(pos.chunkX() - center.chunkX()) <= radius && Math.abs(pos.chunkZ() - center.chunkZ()) <= radius;
    }
}
