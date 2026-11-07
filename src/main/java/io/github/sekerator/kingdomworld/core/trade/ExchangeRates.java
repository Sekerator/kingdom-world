package io.github.sekerator.kingdomworld.core.trade;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.math.BigInteger;

/**
 * Exchange rates of currencies. A rate says what one copper coin of a currency is worth in the common unit of worth that
 * item values use, in millionths: {@link #ONE} is a copper coin worth exactly one unit.
 *
 * <p>The target of a rate is the wealth of the kingdom divided by the money of its currency: goods in the storehouses,
 * coins and reserves of other currencies in its treasury and some days of its production, against what every account,
 * the treasury and the coins in hands hold. Rates stay within bounds and move towards their target a share of the way
 * once a game day, so that one rich day does not shake them.
 *
 * <p>Exchanging never makes money: what one currency gives is worth at most what the other takes, and the fee stays with
 * the bank.
 */
public final class ExchangeRates {
    /** The rate of a copper coin worth one unit of worth. */
    public static final long ONE = 1_000_000L;

    private ExchangeRates() {
    }

    /**
     * What a kingdom owns, in units of worth.
     *
     * @param goods      the worth of what its storehouses hold
     * @param coins      the worth of the coins in its vault
     * @param reserves   the worth of the money of other currencies in its treasury
     * @param production the worth of what its citizens made in a day
     */
    public record Wealth(long goods, long coins, long reserves, long production) {
        public Wealth {
            if (goods < 0 || coins < 0 || reserves < 0 || production < 0) {
                throw new IllegalArgumentException("Negative wealth");
            }
        }

        /**
         * Everything together, with {@code days} days of production.
         */
        public long total(int days) {
            long sum = Money.addCapped(Money.addCapped(goods, coins), reserves);
            return Money.addCapped(sum, Money.multiplyCapped(production, Math.max(0, days)));
        }
    }

    /**
     * The rate a currency tends to: wealth over money, within the bounds.
     *
     * @param wealth units of worth the kingdom owns
     * @param supply money of the currency, in copper coins
     */
    public static long target(long wealth, long supply, TradeRules rules) {
        long money = Math.max(rules.minSupply(), supply);
        BigInteger rate = BigInteger.valueOf(Math.max(0, wealth)).multiply(BigInteger.valueOf(ONE)).divide(BigInteger.valueOf(money));
        return clamp(rate, rules);
    }

    private static long clamp(BigInteger rate, TradeRules rules) {
        if (rate.compareTo(BigInteger.valueOf(rules.maxRate())) > 0) {
            return rules.maxRate();
        }
        return Math.max(rules.minRate(), rate.longValue());
    }

    /**
     * The rate after one game day: a share of the way from the current rate to the target, at least one millionth.
     */
    public static long smooth(long current, long target, TradeRules rules) {
        if (current <= 0) {
            return clamp(BigInteger.valueOf(target), rules);
        }
        long step = (long) ((target - (double) current) * rules.rateSmoothingPercent() / 100.0);
        if (step == 0 && target != current) {
            step = target > current ? 1 : -1;
        }
        return clamp(BigInteger.valueOf(current).add(BigInteger.valueOf(step)), rules);
    }

    /**
     * What an amount of one currency gives in another, less the fee, rounded down: never more than it is worth.
     *
     * @param fromRate    the rate of the currency given
     * @param toRate      the rate of the currency got
     * @param feePermille the fee in thousandths
     */
    public static long convert(long amount, long fromRate, long toRate, int feePermille) {
        if (amount <= 0 || fromRate <= 0 || toRate <= 0) {
            return 0;
        }
        int keep = 1000 - Math.max(0, Math.min(1000, feePermille));
        BigInteger value = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(fromRate)).multiply(BigInteger.valueOf(keep));
        BigInteger result = value.divide(BigInteger.valueOf(toRate).multiply(BigInteger.valueOf(1000)));
        return result.min(BigInteger.valueOf(Money.MAX)).longValue();
    }

    /**
     * What a price in one currency costs in another, with the fee, rounded up: never less than it is worth.
     *
     * @param priceRate   the rate of the currency of the price
     * @param payRate     the rate of the currency paid with
     * @param feePermille the fee in thousandths
     * @return the amount, or -1 if more than any amount of money
     */
    public static long priceIn(long price, long priceRate, long payRate, int feePermille) {
        if (price <= 0) {
            return 0;
        }
        if (priceRate <= 0 || payRate <= 0) {
            return -1;
        }
        int keep = 1000 - Math.max(0, Math.min(999, feePermille));
        BigInteger numerator = BigInteger.valueOf(price).multiply(BigInteger.valueOf(priceRate)).multiply(BigInteger.valueOf(1000));
        BigInteger denominator = BigInteger.valueOf(payRate).multiply(BigInteger.valueOf(keep));
        BigInteger[] parts = numerator.divideAndRemainder(denominator);
        BigInteger result = parts[1].signum() == 0 ? parts[0] : parts[0].add(BigInteger.ONE);
        return result.compareTo(BigInteger.valueOf(Money.MAX)) > 0 ? -1 : result.longValue();
    }

    /**
     * The worth of an amount of money in units of worth, rounded down.
     */
    public static long worth(long amount, long rate) {
        if (amount <= 0 || rate <= 0) {
            return 0;
        }
        return BigInteger.valueOf(amount).multiply(BigInteger.valueOf(rate)).divide(BigInteger.valueOf(ONE))
                .min(BigInteger.valueOf(Money.MAX)).longValue();
    }
}
