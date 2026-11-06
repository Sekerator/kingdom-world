package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Locale;

/**
 * Why a citizen died, as written to the chronicle.
 */
public enum CauseOfDeath {
    MONSTER,
    PLAYER,
    FALL,
    FIRE,
    LAVA,
    DROWNING,
    STARVATION,
    /** Removed by an operator command. */
    COMMAND,
    OTHER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The cause with this id, or {@link #OTHER} for an unknown one (for example from a newer version).
     */
    public static CauseOfDeath byId(String id) {
        for (CauseOfDeath cause : values()) {
            if (cause.id().equals(id)) {
                return cause;
            }
        }
        return OTHER;
    }
}
