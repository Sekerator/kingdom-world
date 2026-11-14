package io.github.sekerator.kingdomworld.core;

import java.util.Map;

/**
 * The limits of a kingdom of players a server may set: citizens, cities, roads and constructions at the same time. A limit of
 * 0 means none, and none is the default: what a kingdom has is decided by its beds, its food, its builders and what it pays.
 *
 * <p>The configurations of servers written before kept the old defaults (200 citizens, 6 cities, 32 roads, 4 constructions,
 * 160 blocks between cities). Such a value is read as the new default, so that a server gets the new rules without its file
 * being edited; any other value written there is kept as it is.
 */
public final class Limits {
    /** Paths of the limits in the configuration of the server. */
    public static final String CITIZENS = "citizens.maxCitizensPerKingdom";
    public static final String CITIES = "roads.maxCities";
    public static final String ROADS = "roads.maxRoads";
    public static final String CONSTRUCTIONS = "construction.maxConstructionsPerKingdom";
    /** The path of the distance between cities, not a limit of a number but read the same way. */
    public static final String CITY_DISTANCE = "roads.minCityDistance";

    /** The blocks between cities the configurations of servers held by default before they counted along x or z. */
    public static final int OLD_CITY_DISTANCE = 160;
    /** The village wall radius of a server by default. */
    public static final int WALL_RADIUS_DEFAULT = 24;
    /** Blocks of land between the walls of two large cities at least. */
    public static final int CITY_GAP = 16;
    /**
     * Blocks between two cities by default, along x or z, with the default walls: two large cities (a wall 48 blocks from the
     * middle) keep 16 blocks of land between their walls and squares.
     */
    public static final int CITY_DISTANCE_DEFAULT = cityDistance(WALL_RADIUS_DEFAULT);

    /** The old defaults of the limits, read as none; the old distance between cities read as the new one. */
    private static final Map<String, Integer> OLD_DEFAULTS = Map.of(CITIZENS, 200, CITIES, 6, ROADS, 32, CONSTRUCTIONS, 4);

    private Limits() {
    }

    /**
     * Whether a count has reached its limit; a limit of 0 or less is none.
     */
    public static boolean reached(long count, int limit) {
        return limit > 0 && count >= limit;
    }

    /**
     * Whether the path is that of a limit of a number, where 0 means none.
     */
    public static boolean isLimit(String path) {
        return OLD_DEFAULTS.containsKey(path);
    }

    /**
     * The least distance between two cities, along x or z, that keeps the walls of two large cities apart: a large city has a
     * wall twice the village radius from its middle, and {@link #CITY_GAP} blocks of land stay between two of them.
     */
    public static int cityDistance(int wallRadius) {
        return 4 * Math.max(0, wallRadius) + CITY_GAP;
    }

    /**
     * The distance between cities a server runs with: never less than its walls need ({@link #cityDistance}); 0, the default,
     * and the defaults of older configurations (160, then 112) give just that, a larger number written is used as it is.
     */
    public static int cityDistance(int configured, int wallRadius) {
        int byWalls = cityDistance(wallRadius);
        int written = configured == OLD_CITY_DISTANCE || configured == CITY_DISTANCE_DEFAULT ? 0 : configured;
        return Math.max(written, byWalls);
    }

    /**
     * The value a server runs with for the value written in its configuration: the old default of a limit counts as none,
     * the distance between cities as {@link #cityDistance(int, int)} with the default walls, anything else as it is written.
     */
    public static int effective(String path, int configured) {
        if (CITY_DISTANCE.equals(path)) {
            return cityDistance(configured, WALL_RADIUS_DEFAULT);
        }
        Integer old = OLD_DEFAULTS.get(path);
        return old != null && configured == old ? 0 : configured;
    }

    /**
     * {@link #effective(String, int)} for a value written as text, as the guide gets the values of the server; text that is
     * no whole number, or the value of another path, stays as it is.
     */
    public static String effectiveText(String path, String raw) {
        if (raw == null || !isLimit(path) && !CITY_DISTANCE.equals(path)) {
            return raw;
        }
        try {
            return Integer.toString(effective(path, Integer.parseInt(raw.strip())));
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}
