package io.github.sekerator.kingdomworld.common.citizen;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bodies of citizens a player looks for, outlined through walls for that player alone: the client keeps them by the id of the
 * entity and its UUID until the time is up, so that another entity given the same id later is never outlined. Filled only on the
 * client, from what the server answers to "Find".
 */
public final class CitizenHighlights {
    /** How long a body found stays outlined, in milliseconds. */
    public static final long MILLIS = 30_000L;

    private record Outlined(UUID entity, long until) {
    }

    private static final Map<Integer, Outlined> OUTLINED = new HashMap<>();

    private CitizenHighlights() {
    }

    /**
     * Outlines a body until {@link #MILLIS} from now; the body outlined before is not any more.
     *
     * @param entityId the id of the entity of the body
     * @param entity   the UUID of that entity
     */
    public static synchronized void show(int entityId, UUID entity, long now) {
        OUTLINED.clear();
        OUTLINED.put(entityId, new Outlined(entity, now + MILLIS));
    }

    public static synchronized void clear() {
        OUTLINED.clear();
    }

    /**
     * Whether a body is outlined now: the entity of that id must still be the same one, by its UUID; another one ends the outline.
     */
    public static synchronized boolean glows(int entityId, UUID entity, long now) {
        Outlined outlined = OUTLINED.get(entityId);
        if (outlined == null) {
            return false;
        }
        if (now > outlined.until() || !outlined.entity().equals(entity)) {
            OUTLINED.remove(entityId);
            return false;
        }
        return true;
    }
}
