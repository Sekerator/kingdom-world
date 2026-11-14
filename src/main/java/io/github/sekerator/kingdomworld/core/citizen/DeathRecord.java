package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Objects;
import java.util.UUID;

/**
 * An entry of a kingdom's chronicle: who died, when, why and where.
 *
 * @param killer     name of who killed the citizen (a player, a creature with a name of its own, the kingdom of a citizen or
 *                   soldier), or an empty string
 * @param gameTime   game time of the death in ticks
 * @param place      where the citizen died
 * @param killerType id of the kind of creature that killed, or an empty string
 * @param damage     id of the kind of damage that killed, or an empty string; entries of older versions have neither
 */
public record DeathRecord(UUID citizenId, int nameSeed, Gender gender, String professionId, CauseOfDeath cause,
                          String killer, long gameTime, WorldPos place, String killerType, String damage) {
    public DeathRecord {
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(place, "place");
        DeathCause checked = new DeathCause(cause, killer, killerType, damage);
        killer = checked.killer();
        killerType = checked.killerType();
        damage = checked.damage();
    }

    public DeathRecord(UUID citizenId, int nameSeed, Gender gender, String professionId, DeathCause cause, long gameTime, WorldPos place) {
        this(citizenId, nameSeed, gender, professionId, cause.cause(), cause.killer(), gameTime, place, cause.killerType(), cause.damage());
    }

    public DeathRecord(UUID citizenId, int nameSeed, Gender gender, String professionId, CauseOfDeath cause,
                       String killer, long gameTime, WorldPos place) {
        this(citizenId, nameSeed, gender, professionId, cause, killer, gameTime, place, "", "");
    }

    /**
     * The cause with who killed and the kind of damage.
     */
    public DeathCause deathCause() {
        return new DeathCause(cause, killer, killerType, damage);
    }

    /**
     * Game day of the death, counted from 1.
     */
    public long day() {
        return gameTime / CitizenNeeds.TICKS_PER_DAY + 1;
    }
}
