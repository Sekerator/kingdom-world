package io.github.sekerator.kingdomworld.core.magic;

/**
 * Turning the energy of a kingdom into Forge Energy for machines. It only goes one way: Forge Energy never becomes energy
 * of a kingdom. A generator takes whole units of energy from the store and keeps the Forge Energy they give in a small
 * buffer, so nothing is made from rounding.
 */
public final class ForgeEnergyRate {
    private ForgeEnergyRate() {
    }

    /**
     * Units of energy needed for at least {@code fe} Forge Energy at a rate of {@code rate} Forge Energy per unit.
     */
    public static long unitsFor(long fe, int rate) {
        if (fe <= 0 || rate <= 0) {
            return 0;
        }
        return (fe + rate - 1) / rate;
    }

    /**
     * Forge Energy that {@code units} of energy give.
     */
    public static long feFor(long units, int rate) {
        if (units <= 0 || rate <= 0) {
            return 0;
        }
        return units > Long.MAX_VALUE / rate ? Long.MAX_VALUE : units * rate;
    }

    /**
     * How many units a generator should take from the store to give {@code wanted} Forge Energy this tick, with
     * {@code buffered} Forge Energy already in its buffer, at most {@code maxPerTick} given per tick and at most
     * {@code stored} units in the store.
     */
    public static long unitsToTake(long wanted, long buffered, int maxPerTick, int rate, long stored) {
        long give = Math.min(Math.max(0, wanted), Math.max(0, maxPerTick));
        long missing = give - Math.max(0, buffered);
        if (missing <= 0) {
            return 0;
        }
        return Math.min(Math.max(0, stored), unitsFor(missing, rate));
    }
}
