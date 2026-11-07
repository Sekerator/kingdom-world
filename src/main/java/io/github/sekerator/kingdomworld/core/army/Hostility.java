package io.github.sekerator.kingdomworld.core.army;

import java.util.UUID;

/**
 * Whether soldiers of a kingdom may attack a player. In peace nobody is an enemy; wars (a later stage) replace this.
 * Players who are away from the keyboard or offline are never attacked, whatever this says: the server checks that apart.
 */
@FunctionalInterface
public interface Hostility {
    /** Peace: no player is an enemy of any kingdom. */
    Hostility PEACE = (kingdomId, playerId, playerKingdom) -> false;

    /**
     * @param kingdomId     the kingdom of the soldiers
     * @param playerId      the player
     * @param playerKingdom the player's kingdom, {@code null} for none
     */
    boolean isEnemy(UUID kingdomId, UUID playerId, UUID playerKingdom);
}
