package io.github.sekerator.kingdomworld.core.companion;

import java.util.Locale;
import java.util.Optional;

/**
 * Why the loyalty of a companion changed; the window of a companion shows the last few.
 */
public enum LoyaltyReason {
    /** Went hungry for a long time. */
    HUNGER,
    /** Badly wounded. */
    WOUNDS,
    /** Struck by the owner. */
    BEATEN,
    /** Fed, healthy and treated well for a while. */
    WELL_KEPT,
    GIFT,
    /** The wage of a free companion was paid. */
    PAID,
    /** The wage of a free companion was not paid. */
    UNPAID,
    /** Set free. */
    FREED,
    /** Changed by an operator. */
    COMMAND;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<LoyaltyReason> byId(String id) {
        for (LoyaltyReason reason : values()) {
            if (reason.id().equals(id)) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }
}
