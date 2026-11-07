package io.github.sekerator.kingdomworld.core.companion;

import java.util.Locale;
import java.util.Optional;

/**
 * Where a companion comes from, one line in the offers of the market: how a slave came to be sold, or who a free companion
 * was before looking for work.
 */
public enum Origin {
    BANDIT_CAPTIVE(true),
    DEBTOR(true),
    WAR_CAPTIVE(true),
    CONVICT(true),
    ORPHAN(true),
    SHIPWRECKED(true),
    SOLD_BY_FAMILY(true),
    SELLSWORD(false),
    VETERAN(false),
    HUNTER(false),
    WANDERER(false),
    EXILE(false),
    FARMHAND(false),
    /** A slave set free who stays in service for pay. */
    FREEDMAN(false);

    private final boolean slave;

    Origin(boolean slave) {
        this.slave = slave;
    }

    /**
     * Whether a slave on the market comes from here; the others are free people looking for work.
     */
    public boolean slave() {
        return slave;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Origin> byId(String id) {
        for (Origin origin : values()) {
            if (origin.id().equals(id)) {
                return Optional.of(origin);
            }
        }
        return Optional.empty();
    }

    /**
     * The origins offered on the market for slaves or for free companions; a freedman only comes from setting a slave free.
     */
    public static Origin[] offered(boolean slaves) {
        return java.util.Arrays.stream(values()).filter(origin -> origin.slave == slaves && origin != FREEDMAN).toArray(Origin[]::new);
    }
}
