package io.github.sekerator.kingdomworld.common.citizen;

import java.util.HashMap;
import java.util.Map;

/**
 * Bodies of citizens a player looks for, outlined through walls for that player alone: the client keeps them by the id of the
 * entity until the time is up. Filled only on the client, from what the server answers to "Find".
 */
public final class CitizenHighlights {
    /** How long a body found stays outlined, in milliseconds. */
    public static final long MILLIS = 30_000L;

    private static final Map<Integer, Long> UNTIL = new HashMap<>();

    private CitizenHighlights() {
    }

    /**
     * Outlines a body until {@link #MILLIS} from now; the body outlined before is not any more.
     */
    public static synchronized void show(int entityId, long now) {
        UNTIL.clear();
        UNTIL.put(entityId, now + MILLIS);
    }

    public static synchronized void clear() {
        UNTIL.clear();
    }

    /**
     * Whether a body is outlined now.
     */
    public static synchronized boolean glows(int entityId, long now) {
        Long until = UNTIL.get(entityId);
        if (until == null) {
            return false;
        }
        if (now > until) {
            UNTIL.remove(entityId);
            return false;
        }
        return true;
    }
}
