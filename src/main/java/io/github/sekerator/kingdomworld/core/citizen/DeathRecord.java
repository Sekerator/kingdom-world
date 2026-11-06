package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Objects;
import java.util.UUID;

/**
 * An entry of a kingdom's chronicle: who died, when, why and where.
 *
 * @param killer   name of the player who killed the citizen, or an empty string
 * @param gameTime game time of the death in ticks
 * @param place    where the citizen died
 */
public record DeathRecord(UUID citizenId, int nameSeed, Gender gender, String professionId, CauseOfDeath cause,
                          String killer, long gameTime, WorldPos place) {
    public DeathRecord {
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(place, "place");
        killer = killer == null ? "" : killer;
    }

    /**
     * Game day of the death, counted from 1.
     */
    public long day() {
        return gameTime / CitizenNeeds.TICKS_PER_DAY + 1;
    }
}
