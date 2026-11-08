package io.github.sekerator.kingdomworld.core.diplomacy;

import java.util.Locale;
import java.util.Optional;

/**
 * What a kingdom goes to war for. Land, a city and the throne are taken in the world (a later stage); their terms of peace
 * are known already and are handed to whoever carries them out.
 */
public enum WarGoal {
    /** Vassalage of the enemy, or a tribute for some days. */
    TRIBUTE,
    /** A single sum from the enemy's treasury. */
    INDEMNITY,
    /** Loot and weakening: caravans and battles, no terms beyond a small sum. */
    RAID,
    /** Chunks of the enemy's land. */
    LAND,
    /** A city of the enemy. */
    CITY,
    /** The enemy's throne. */
    THRONE,
    /** A vassal's war against its overlord for its freedom. */
    INDEPENDENCE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<WarGoal> byId(String id) {
        for (WarGoal goal : values()) {
            if (goal.id().equals(id)) {
                return Optional.of(goal);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the goal is carried out in the world by a later stage (land, a city, the throne).
     */
    public boolean inTheWorld() {
        return this == LAND || this == CITY || this == THRONE;
    }
}
