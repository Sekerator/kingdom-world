package io.github.sekerator.kingdomworld.core.guild;

import java.util.Locale;
import java.util.Optional;

/**
 * The ranks of the adventurers' guild, from the lowest to the highest. Monsters have the same ranks: a monster of rank C
 * is a fair fight for an adventurer of rank C. The order of the constants is the order of the ranks.
 */
public enum Rank {
    F,
    E,
    D,
    C,
    B,
    A,
    S,
    SS,
    SSS;

    /** Number of ranks. */
    public static final int COUNT = values().length;

    /**
     * Lower case id, used in saved data, commands and translation keys.
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * How the rank is written: {@code SSS}.
     */
    public String label() {
        return name();
    }

    /**
     * The next rank, or empty at the top.
     */
    public Optional<Rank> next() {
        return ordinal() + 1 < COUNT ? Optional.of(values()[ordinal() + 1]) : Optional.empty();
    }

    /**
     * The rank {@code steps} above (or below for negative steps), kept within F and SSS.
     */
    public Rank plus(int steps) {
        return values()[Math.max(0, Math.min(COUNT - 1, ordinal() + steps))];
    }

    public boolean atLeast(Rank other) {
        return ordinal() >= other.ordinal();
    }

    public static Rank max(Rank a, Rank b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public static Optional<Rank> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (Rank rank : values()) {
            if (rank.id().equalsIgnoreCase(id.strip())) {
                return Optional.of(rank);
            }
        }
        return Optional.empty();
    }

    /**
     * The rank at an index, kept within the ranks; for data read from the network.
     */
    public static Rank ofIndex(int index) {
        return values()[Math.max(0, Math.min(COUNT - 1, index))];
    }
}
