package io.github.sekerator.kingdomworld.core.law;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.Objects;

/**
 * One crime on the record of a wanted player. Several thefts in a short time, or several blows at one victim, make one
 * crime: the worth adds up and {@code count} grows.
 *
 * @param worth  what was stolen, in copper coins of the kingdom; 0 for other crimes
 * @param time   game time of the last act
 * @param victim who suffered, for the record (a name, may be empty)
 * @param count  acts in the crime
 */
public record Crime(CrimeKind kind, long worth, long time, String victim, int count) {
    /** Longest name of a victim kept. */
    public static final int MAX_VICTIM = 64;

    public Crime {
        Objects.requireNonNull(kind, "kind");
        victim = victim == null ? "" : victim.length() > MAX_VICTIM ? victim.substring(0, MAX_VICTIM) : victim;
        if (!Money.isAmount(worth) || count < 1) {
            throw new IllegalArgumentException("Crime out of range: " + kind + " " + worth + " x" + count);
        }
    }

    public static Crime of(CrimeKind kind, long worth, long time, String victim) {
        return new Crime(kind, Math.max(0, Math.min(Money.MAX, worth)), time, victim, 1);
    }

    /**
     * The crime with one more act of the same kind.
     */
    public Crime and(long moreWorth, long now) {
        return new Crime(kind, Money.addCapped(worth, Math.max(0, moreWorth)), Math.max(time, now), victim,
                count == Integer.MAX_VALUE ? count : count + 1);
    }
}
