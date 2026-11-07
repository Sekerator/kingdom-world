package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.combat.CombatPower;
import io.github.sekerator.kingdomworld.core.combat.Fighter;
import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A band of mercenaries: on offer to a kingdom for a day, or hired. A hired band serves as a squad of its own and is paid
 * every day from the treasury; a band that is not paid leaves.
 *
 * @param kingdomId  the kingdom it is offered to or serves
 * @param dailyPrice what the band costs a day, in the kingdom's currency
 * @param day        the game day of the offer, or of hiring
 * @param paidUntil  the last game day the band is paid for; {@link #OFFER} while it is only offered
 */
public record MercenaryBand(long id, UUID kingdomId, List<Mercenary> fighters, long dailyPrice, long day, long paidUntil) {
    /** {@link #paidUntil} of a band on offer. */
    public static final long OFFER = Long.MIN_VALUE;
    /** Most mercenaries in one band. */
    public static final int MAX_FIGHTERS = 16;

    public MercenaryBand {
        Objects.requireNonNull(kingdomId, "kingdomId");
        fighters = List.copyOf(fighters);
        if (id < 1 || fighters.isEmpty() || fighters.size() > MAX_FIGHTERS || !Money.isPayment(dailyPrice)) {
            throw new IllegalArgumentException("Band of mercenaries out of range: #" + id);
        }
    }

    public boolean hired() {
        return paidUntil != OFFER;
    }

    public Mercenary captain() {
        Mercenary best = fighters.get(0);
        for (Mercenary fighter : fighters) {
            if (fighter.level() > best.level()) {
                best = fighter;
            }
        }
        return best;
    }

    public List<Fighter> asFighters() {
        List<Fighter> result = new ArrayList<>();
        for (Mercenary fighter : fighters) {
            result.add(fighter.fighter());
        }
        return result;
    }

    public double power() {
        return CombatPower.of(asFighters());
    }

    /**
     * Days the band is still paid for, counting today.
     */
    public long daysLeft(long today) {
        return hired() ? Math.max(0, paidUntil - today + 1) : 0;
    }

    public MercenaryBand hiredOn(long today, long paidDays) {
        return new MercenaryBand(id, kingdomId, fighters, dailyPrice, today, today + Math.max(1, paidDays) - 1);
    }

    public MercenaryBand paidThrough(long through) {
        return new MercenaryBand(id, kingdomId, fighters, dailyPrice, day, Math.max(paidUntil, through));
    }

    public MercenaryBand withFighters(List<Mercenary> value) {
        return new MercenaryBand(id, kingdomId, value, dailyPrice, day, paidUntil);
    }
}
