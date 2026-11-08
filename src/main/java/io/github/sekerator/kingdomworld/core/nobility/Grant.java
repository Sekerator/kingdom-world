package io.github.sekerator.kingdomworld.core.nobility;

import java.util.Objects;
import java.util.UUID;

/**
 * A title with its fief, held by a noble house or by a player.
 *
 * @param house  the house that holds it, 0 when a player does
 * @param player the player who holds it, {@code null} when a house does
 * @param since  the game day it was granted
 */
public record Grant(long id, UUID kingdomId, Title title, Fief fief, long house, UUID player, long since) {
    public Grant {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(fief, "fief");
        if ((house > 0) == (player != null)) {
            throw new IllegalArgumentException("A title is held by a house or by a player");
        }
    }

    public boolean heldByPlayer() {
        return player != null;
    }

    Grant to(long newHouse, UUID newPlayer, long day) {
        return new Grant(id, kingdomId, title, fief, newHouse, newPlayer, day);
    }
}
