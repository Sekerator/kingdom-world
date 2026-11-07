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
    /** An accident in a mine, far from players. */
    MINE,
    /** An accident while felling trees, far from players. */
    FOREST,
    /** Drowned while fishing, far from players. */
    FISHING,
    /** An accident with the herd, far from players. */
    HERD,
    /** A fall from a building going up, far from players. */
    BUILDING,
    /** Any other accident at work far from players. */
    WORK,
    /** Fell on an expedition of the guild. */
    EXPEDITION,
    /** Fell in a battle of the army far from players. */
    BATTLE,
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
