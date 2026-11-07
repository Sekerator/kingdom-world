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
    TRESPASS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean petty() {
        return this != MURDER;
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
