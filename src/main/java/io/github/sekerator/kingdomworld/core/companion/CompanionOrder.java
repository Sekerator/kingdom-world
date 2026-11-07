package io.github.sekerator.kingdomworld.core.companion;

import java.util.Locale;
import java.util.Optional;

/**
 * What a companion does when nothing threatens it.
 */
public enum CompanionOrder {
    /** Walk with the owner and fight what attacks either of them. */
    FOLLOW,
    /** Stand where told and defend itself only. */
    STAND,
    /** Guard the place where told: fight every monster that comes near. */
    GUARD,
    /** Go with the owner on a contract: hunt the monsters of the owner's contracts nearby as well. */
    CONTRACT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether the companion keeps to a place rather than to the owner.
     */
    public boolean atPlace() {
        return this == STAND || this == GUARD;
    }

    public static Optional<CompanionOrder> byId(String id) {
        for (CompanionOrder order : values()) {
            if (order.id().equals(id)) {
                return Optional.of(order);
            }
        }
        return Optional.empty();
    }
}
