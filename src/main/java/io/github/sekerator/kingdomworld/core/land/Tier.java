package io.github.sekerator.kingdomworld.core.land;

import java.util.Locale;
import java.util.Optional;

/**
 * The rank of a settlement of a state, the highest first.
 */
public enum Tier {
    /** The capital of the state. */
    CAPITAL,
    /** A large city. */
    CITY,
    /** A small town. */
    TOWN,
    /** A village. */
    VILLAGE,
    /** A hamlet of a few houses. */
    HAMLET;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether this rank stands above another.
     */
    public boolean above(Tier other) {
        return ordinal() < other.ordinal();
    }

    public static Optional<Tier> byId(String id) {
        for (Tier tier : values()) {
            if (tier.id().equals(id)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }
}
