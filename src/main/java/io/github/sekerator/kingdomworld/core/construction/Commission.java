package io.github.sekerator.kingdomworld.core.construction;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A building, a post of builders or a town hall the king orders from afar: the server finds the place, levels the ground if it
 * must and lays the construction out, or sets up the post or the city, without anybody going there.
 *
 * @param kind      what is ordered
 * @param templateId the template of a building ({@link Kind#BUILD}, {@link Kind#AT}), else empty
 * @param cityId    the city ({@link #CAPITAL} for the capital); {@link #OUTSIDE} for a point outside every city
 * @param x         the point on the map ({@link Kind#AT}, {@link Kind#HALL}), else the place found or 0
 * @param rotation  the quarter turns of a building laid at a point, 0 to 3
 * @param actor     who ordered it, or {@code null} for an operator or the server
 * @param state     where the work stands
 * @param after     the earthworks that level the place first, or 0
 * @param reason    why it waits: the first block in the way, the item it lacks
 * @param name      the name of a city founded ({@link Kind#HALL})
 * @param withPost  whether a city founded gets a post of builders too ({@link Kind#HALL})
 * @param retryAt   the game time it is looked at again
 * @param order     the order of the item it lacks, or 0
 */
public record Commission(long id, UUID kingdomId, Kind kind, String templateId, Materials materials, long cityId, String dimension, int x,
                         int z, int rotation, Construction.Priority priority, UUID actor, Origin origin, long created, State state,
                         long after, String reason, String name, boolean withPost, long retryAt, long order) {
    public static final long CAPITAL = 0;
    public static final long OUTSIDE = -1;
    public static final int MAX_REASON = 256;

    public Commission {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(kind, "kind");
        templateId = templateId == null ? "" : templateId;
        materials = materials == null ? Materials.NONE : materials;
        Objects.requireNonNull(dimension, "dimension");
        rotation = Math.floorMod(rotation, 4);
        priority = priority == null ? Construction.Priority.NORMAL : priority;
        origin = origin == null ? Origin.KING : origin;
        state = state == null ? State.WAITING : state;
        reason = reason == null ? "" : reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason;
        name = name == null ? "" : name;
    }

    /**
     * What is ordered.
     */
    public enum Kind {
        /** A building in a city: the server chooses the place near its town hall. */
        BUILD,
        /** A building at a point of the map, with its entrance as asked. */
        AT,
        /** A post of builders for a city. */
        POST,
        /** A city founded at a point of the map: its town hall. */
        HALL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * The kind of an id, or {@code null} for an id this version does not know.
         */
        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id().equals(id)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /**
     * Who ordered it: the king (or a member with the right), or the governor of the city (a later stage).
     */
    public enum Origin {
        KING,
        GOVERNOR;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Origin byId(String id) {
            for (Origin origin : values()) {
                if (origin.id().equals(id)) {
                    return origin;
                }
            }
            return null;
        }
    }

    /**
     * Where the work of an order stands.
     */
    public enum State {
        /** Waits for its turn. */
        WAITING,
        /** The server looks at places in the world. */
        LOOKING,
        /** Earthworks level the place first. */
        LEVELLING,
        /** An item is missing in the storehouses: the work post or the town hall. */
        LACKS_ITEM,
        /** No level free place was found; it is tried again a game day later. */
        NO_PLACE,
        /** The kingdom has as many constructions as the server allows: it waits. */
        LIMIT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static State byId(String id) {
            for (State state : values()) {
                if (state.id().equals(id)) {
                    return state;
                }
            }
            return null;
        }
    }

    public Commission withState(State value, String why, long retry) {
        return new Commission(id, kingdomId, kind, templateId, materials, cityId, dimension, x, z, rotation, priority, actor, origin, created,
                value, after, why, name, withPost, retry, order);
    }

    public Commission withAfter(long earthworks) {
        return new Commission(id, kingdomId, kind, templateId, materials, cityId, dimension, x, z, rotation, priority, actor, origin, created,
                State.LEVELLING, earthworks, "", name, withPost, retryAt, order);
    }

    public Commission withOrder(long value) {
        return new Commission(id, kingdomId, kind, templateId, materials, cityId, dimension, x, z, rotation, priority, actor, origin, created,
                state, after, reason, name, withPost, retryAt, value);
    }

    public Commission withPlace(int placeX, int placeZ) {
        return new Commission(id, kingdomId, kind, templateId, materials, cityId, dimension, placeX, placeZ, rotation, priority, actor, origin,
                created, state, after, reason, name, withPost, retryAt, order);
    }

    public Commission withId(long value) {
        return new Commission(value, kingdomId, kind, templateId, materials, cityId, dimension, x, z, rotation, priority, actor, origin, created,
                state, after, reason, name, withPost, retryAt, order);
    }
}
