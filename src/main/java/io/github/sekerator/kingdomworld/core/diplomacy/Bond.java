package io.github.sekerator.kingdomworld.core.diplomacy;

import java.util.Locale;
import java.util.Optional;

/**
 * How two kingdoms stand with each other.
 */
public enum Bond {
    /** Nothing binds them: the default. */
    PEACE,
    /** A pact of non-aggression for some days: no war without breaking it. */
    PACT,
    /** A defensive alliance: each is called when the other is attacked. */
    ALLIANCE,
    /** One is the vassal of the other: it pays tribute, declares no wars of its own and is defended. */
    VASSAL,
    /** At war, or preparing for it. */
    WAR,
    /** A truce after a war, for some days: no war is possible. */
    TRUCE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Bond> byId(String id) {
        for (Bond bond : values()) {
            if (bond.id().equals(id)) {
                return Optional.of(bond);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the bond is a treaty kept apart from wars (a pact, an alliance, vassalage or a truce).
     */
    public boolean isTreaty() {
        return this == PACT || this == ALLIANCE || this == VASSAL || this == TRUCE;
    }
}
