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
 * @param via    the points the squad marches through on its way, in order: around an enemy, to its flank or rear
 * @param target the squad attacked, 0 for a place (a camp of robbers, a city)
 */
public record Order(Kind kind, List<WorldPos> points, int radius, UUID player, long deal, List<WorldPos> via, long target) {
    /** Most points of a patrol route. */
    public static final int MAX_POINTS = 8;
    /** Largest radius of a guarded area, in chunks. */
    public static final int MAX_RADIUS = 4;

    public static final Order NONE = new Order(Kind.NONE, List.of(), 0, null, 0);

    public Order(Kind kind, List<WorldPos> points, int radius, UUID player, long deal) {
        this(kind, points, radius, player, deal, List.of(), 0);
    }

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
        ESCORT,
        /** Besiege a city of the enemy: break its gates and hold its square. */
        SIEGE,
        /** Defend a city of one's own: its gates, walls and square. */
        DEFEND,
        /** March to a point and stand there. */
        MOVE,
        /** Attack a squad of an enemy, a camp of robbers or a city. */
        ATTACK,
        /** March back to a barracks; the order ends there. */
        RETREAT,
        /** Scout an area: the squad sees farther there and shows the enemies in it. */
        SCOUT;

        /**
         * Whether a squad with such an order marches to a place.
         */
        public boolean marches() {
            return this != NONE && this != FOLLOW && this != ESCORT;
        }

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
        via = via == null ? List.of() : List.copyOf(via);
        boolean valid = switch (kind) {
            case NONE -> points.isEmpty() && player == null && deal == 0;
            case HOLD -> points.size() == 1;
            case PATROL -> points.size() >= 1 && points.size() <= MAX_POINTS;
            case GUARD -> points.size() == 1 && radius >= 0 && radius <= MAX_RADIUS;
            case FOLLOW -> player != null && points.isEmpty();
            case ESCORT -> deal > 0 && points.isEmpty();
            case SIEGE, DEFEND, MOVE, RETREAT -> points.size() == 1;
            case ATTACK -> points.size() == 1 && target >= 0;
            case SCOUT -> points.size() == 1 && radius >= 0 && radius <= MAX_RADIUS;
        };
        if (via.size() > MAX_POINTS || (!kind.marches() && !via.isEmpty())) {
            valid = false;
        }
        if (!valid) {
            throw new IllegalArgumentException("Order out of range: " + kind);
        }
        if (kind != Kind.GUARD && kind != Kind.SCOUT) {
            radius = 0;
        }
        if (kind != Kind.ATTACK) {
            target = 0;
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
     * Besieges the city standing at a point.
     */
    public static Order siege(WorldPos city) {
        return new Order(Kind.SIEGE, List.of(city), 0, null, 0);
    }

    /**
     * Defends the city standing at a point.
     */
    public static Order defend(WorldPos city) {
        return new Order(Kind.DEFEND, List.of(city), 0, null, 0);
    }

    /**
     * Marches to a point and stands there.
     */
    public static Order move(WorldPos point) {
        return new Order(Kind.MOVE, List.of(point), 0, null, 0);
    }

    /**
     * Attacks a squad that was last seen at a point, or a place (a camp of robbers, a city) with a squad of 0.
     */
    public static Order attack(WorldPos point, long squadId) {
        return new Order(Kind.ATTACK, List.of(point), 0, null, 0, List.of(), squadId);
    }

    /**
     * Marches back to a barracks.
     */
    public static Order retreat(WorldPos barracks) {
        return new Order(Kind.RETREAT, List.of(barracks), 0, null, 0);
    }

    /**
     * Scouts the chunks around a point.
     */
    public static Order scout(WorldPos center, int radius) {
        return new Order(Kind.SCOUT, List.of(center), radius, null, 0);
    }

    /**
     * The same order, marching through other points on the way.
     */
    public Order withVia(List<WorldPos> value) {
        return new Order(kind, points, radius, player, deal, value, target);
    }

    /**
     * The same order with another point attacked, for a squad that chases an enemy who moved.
     */
    public Order withPoint(WorldPos point) {
        return new Order(kind, List.of(point), radius, player, deal, via, target);
    }

    /**
     * Whether a place lies in the area a squad scouts.
     */
    public boolean scouts(WorldPos pos) {
        if (kind != Kind.SCOUT || points.isEmpty() || !points.get(0).dimension().equals(pos.dimension())) {
            return false;
        }
        WorldPos center = points.get(0);
        return Math.abs(pos.chunkX() - center.chunkX()) <= radius && Math.abs(pos.chunkZ() - center.chunkZ()) <= radius;
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
