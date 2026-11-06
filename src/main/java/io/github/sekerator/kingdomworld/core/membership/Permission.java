package io.github.sekerator.kingdomworld.core.membership;

import java.util.Locale;
import java.util.Optional;

/**
 * Rights a role may hold inside its kingdom.
 */
public enum Permission {
    /** Break and place blocks, use buckets and tools on blocks in the kingdom's land. */
    BUILD,
    /** Open blocks with an inventory (chests, furnaces, machines) in the kingdom's land. */
    CONTAINERS,
    /** Use doors, buttons, levers, item frames and armor stands in the kingdom's land. */
    INTERACT,
    /** Accept and reject applications, invite and expel subjects. */
    RECRUIT,
    /** Claim and abandon chunks. */
    TERRITORY,
    /** Appoint members to positions and change the rights of positions. Only the king holds it. */
    APPOINT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Permission> byId(String id) {
        for (Permission permission : values()) {
            if (permission.id().equals(id)) {
                return Optional.of(permission);
            }
        }
        return Optional.empty();
    }

    /**
     * Rights that belong to the king alone and can never be granted to other positions.
     */
    public boolean isKingOnly() {
        return this == APPOINT;
    }
}
