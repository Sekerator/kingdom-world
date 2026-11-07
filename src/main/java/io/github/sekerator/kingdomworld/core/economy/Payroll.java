package io.github.sekerator.kingdomworld.core.economy;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;

import java.math.BigInteger;

/**
 * The daily wages of the citizens: what is due, what the treasury can pay, and how the pay sets the mood.
 */
public final class Payroll {
    /** Pay percent of a citizen who expects no wage: it neither pleases nor angers. */
    public static final int NOTHING_EXPECTED = -1;
    /** Pay above this percent of the expected wage pleases no more. */
    public static final int MAX_PERCENT = 200;

    /**
     * The last payday of a kingdom.
     *
     * @param day      game day it was held on, or -1 before the first one
     * @param due      wages the citizens should have got
     * @param paid     wages they got
     * @param citizens citizens on the payroll
     * @param underpaid   citizens who got less than their wage
     */
    public record Summary(long day, long due, long paid, int citizens, int underpaid) {
        public static final Summary NONE = new Summary(-1, 0, 0, 0, 0);

        public Summary {
            if (!Money.isAmount(due) || !Money.isAmount(paid) || paid > due || citizens < 0 || underpaid < 0) {
                throw new IllegalArgumentException("Payroll summary out of range");
            }
        }

        /**
         * Percent of the wages that was paid, 100 when nothing was due.
         */
        public int percent() {
            return due == 0 ? 100 : (int) Money.share(100, paid, due);
        }
    }

    private Payroll() {
    }

    /**
     * Shares out the money a treasury has for wages: everybody gets the full wage if there is enough, otherwise the
     * same share of it, rounded down, so that the sum never exceeds the funds.
     *
     * @param wages wage of each citizen
     * @param funds money the treasury has
     * @return what each citizen gets
     */
    public static long[] distribute(long[] wages, long funds) {
        // The sum of many wages may exceed a long, so it is counted exactly.
        BigInteger due = BigInteger.ZERO;
        for (long wage : wages) {
            due = due.add(BigInteger.valueOf(Math.max(0, wage)));
        }
        BigInteger available = BigInteger.valueOf(Math.max(0, funds));
        boolean enough = available.compareTo(due) >= 0;
        long[] paid = new long[wages.length];
        for (int i = 0; i < wages.length; i++) {
            long wage = Math.max(0, wages[i]);
            paid[i] = enough ? wage : BigInteger.valueOf(wage).multiply(available).divide(due).longValue();
        }
        return paid;
    }

    /**
     * How much of the wage a citizen of its profession expects it got, in percent.
     *
     * @param expected the usual wage of the profession; nothing is expected below 1
     */
    public static int percent(long paid, long expected) {
        if (expected <= 0) {
            return NOTHING_EXPECTED;
        }
        return (int) Math.min(MAX_PERCENT, Money.share(100, Math.max(0, paid), expected));
    }

    /**
     * How the last pay changes the mood a citizen tends to: nothing at the usual wage, up to
     * {@value #UNPAID_MOOD} without pay, up to {@value #GENEROUS_MOOD} at twice the usual wage.
     */
    public static int mood(int percent) {
        if (percent < 0 || percent == 100) {
            return 0;
        }
        if (percent < 100) {
            return Math.round(UNPAID_MOOD * (100 - percent) / 100.0f);
        }
        return Math.min(GENEROUS_MOOD, (percent - 100) * GENEROUS_MOOD / (MAX_PERCENT - 100));
    }

    /** Change of the mood a citizen tends to when it got no wage at all. */
    public static final int UNPAID_MOOD = -20;
    /** Change of the mood a citizen tends to when it got twice the usual wage. */
    public static final int GENEROUS_MOOD = 10;

    /**
     * How fast a citizen works at a mood: a neutral citizen at the usual pace, a happy one up to {@code effect}
     * faster, a miserable one up to {@code effect} slower.
     *
     * @param effect the strongest change of pace, from 0 (none) to 0.5
     */
    public static double workSpeed(int mood, double effect) {
        double clamped = Math.max(0.0, Math.min(0.5, effect));
        int bounded = Math.max(0, Math.min(CitizenNeeds.MAX_MOOD, mood));
        return 1.0 + clamped * (bounded - CitizenNeeds.NEUTRAL_MOOD) / (double) CitizenNeeds.NEUTRAL_MOOD;
    }
}
