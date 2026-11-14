package io.github.sekerator.kingdomworld.core.construction;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * How builders without a construction of their own help a city no post reaches: they share their time between those
 * constructions and the roads and walls of the kingdom, and they help faster where a road of the kingdom leads from their city.
 * They do not walk there, nor wait for a road: the city they help is only slower to reach without one.
 */
public final class HelperWork {
    /** The share of the time of helpers given to far constructions while roads and walls wait for them too. */
    public static final double REMOTE_SHARE = 0.5;

    private HelperWork() {
    }

    /**
     * The share of their time helpers give to far constructions: half while roads and walls of the kingdom wait for them too, all of
     * it otherwise. What the far constructions do not use goes to the roads.
     */
    public static double remoteShare(boolean roadsWait) {
        return roadsWait ? REMOTE_SHARE : 1.0;
    }

    /**
     * The cities linked by built roads, as a root city for every city that has a road: two cities with the same root are linked.
     *
     * @param roads the built roads between two cities of the kingdom, as their two cities (0 for the capital)
     */
    public static Map<Long, Long> linked(Collection<long[]> roads) {
        Map<Long, Long> parent = new HashMap<>();
        for (long[] road : roads) {
            long a = root(parent, road[0]);
            long b = root(parent, road[1]);
            if (a != b) {
                parent.put(Math.max(a, b), Math.min(a, b));
            }
        }
        Map<Long, Long> roots = new HashMap<>();
        for (long city : parent.keySet()) {
            roots.put(city, root(parent, city));
        }
        return roots;
    }

    private static long root(Map<Long, Long> parent, long city) {
        long at = city;
        parent.putIfAbsent(at, at);
        while (parent.get(at) != at) {
            long up = parent.get(at);
            parent.put(at, parent.getOrDefault(up, up));
            at = up;
        }
        return at;
    }

    /**
     * Whether a road of the kingdom leads from one city to the other.
     */
    public static boolean connected(Map<Long, Long> roots, long from, long to) {
        if (from == BuilderReach.NO_CITY || to == BuilderReach.NO_CITY) {
            return false;
        }
        return from == to || roots.getOrDefault(from, from).longValue() == roots.getOrDefault(to, to).longValue() && roots.containsKey(from);
    }

    /**
     * How fast a helper helps: as fast as at home by road, slower without one.
     */
    public static double speed(boolean connected, double remoteSpeed) {
        return connected ? 1.0 : remoteSpeed;
    }
}
