package io.github.sekerator.kingdomworld.core.companion;

import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * Somebody on the market of a branch of the guild: a slave for sale, or a free companion looking for work.
 *
 * @param slave whether it is a slave for sale rather than a free companion to hire
 * @param price what the slave costs, or the daily wage of the free companion
 */
public record Offer(long id, UUID branch, boolean slave, int nameSeed, Gender gender, int skin, Origin origin, int level, Skills skills, long price) {
    public Offer {
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(skills, "skills");
        if (id < 1 || level < 0 || level > Companion.MAX_LEVEL || !Money.isPayment(price)) {
            throw new IllegalArgumentException("Offer out of range: #" + id);
        }
    }

    public float maxHealth() {
        return Companion.maxHealth(level);
    }
}
