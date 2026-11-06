package io.github.sekerator.kingdomworld.core.membership;

import java.util.Objects;
import java.util.UUID;

/**
 * A player who belongs to a kingdom.
 *
 * @param playerId       the player's id
 * @param role           the position the player holds
 * @param joinedGameTime game time in ticks when the player joined
 */
public record Member(UUID playerId, Role role, long joinedGameTime) {
    public Member {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(role, "role");
    }

    public Member withRole(Role newRole) {
        return new Member(playerId, newRole, joinedGameTime);
    }
}
