package io.github.sekerator.kingdomworld.core.law;

import java.util.Locale;
import java.util.Optional;

/**
 * What the law of a kingdom punishes. Petty crimes cost a fine and are forgotten with time; a murder is punished with death
 * unless the court replaces it with a fine, and is never forgotten.
 */
public enum CrimeKind {
    /** Took what belongs to somebody else: from a container, a field, the herds of the kingdom. */
    THEFT,
    /** Struck a citizen of the kingdom. */
    ASSAULT,
    /** Killed a citizen of the kingdom or a player on its land. */
    MURDER,
    /** Got into a city past its gates or without paying for the entry. */
    TRESPASS,
    /**
     * Damaged property: broke blocks of a city, a building, a wall or a road of the kingdom. The blocks come back; the fine
     * follows their worth.
     */
    VANDALISM;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean petty() {
        return this != MURDER;
    }

    /**
     * Whether acts of this kind close together make one crime whose worth adds up: thefts, and blocks broken one after
     * another, so that a hundred blocks are not a hundred crimes.
     */
    public boolean adds() {
        return this == THEFT || this == VANDALISM;
    }

    public static Optional<CrimeKind> byId(String id) {
        for (CrimeKind kind : values()) {
            if (kind.id().equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
