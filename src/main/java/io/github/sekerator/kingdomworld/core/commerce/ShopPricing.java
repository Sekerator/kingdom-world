package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.economy.Money;

/**
 * The prices of the shops of citizens. Prices of one item are kept in thousandths of the smallest coin, so that the margin of
 * a cheap item is not lost to rounding; what a buyer pays is rounded up to whole coins for all the items of a purchase.
 *
 * <p>A shop sells at what it paid plus its margin, the sales tax on top. The margin moves each day towards what gives its
 * keepers a fair income from yesterday's sales, a few points at a time, within bounds. No shop sells above a ceiling, the worth
 * of the item and a half or what it would cost to bring it from abroad; an item that cannot be sold at least five in a hundred
 * above what the shop pays under that ceiling is not bought. Players get the price of the reputation they have, but never
 * below what the shop paid.
 */
public final class ShopPricing {
    /** Thousandths of a coin in a coin. */
    public static final long MILLI = 1_000;
    /** The least margin an item must be able to bear under the ceiling, in thousandths. */
    public static final int LEAST_MARGIN = 50;

    private ShopPricing() {
    }

    /**
     * The price of one item without the tax, in thousandths of a coin.
     */
    public static long priceMilli(long costMilli, int marginPermille) {
        long margin = Money.share(Math.max(0, costMilli), Math.max(0, marginPermille), 1000);
        return Money.addCapped(Math.max(0, costMilli), margin);
    }

    /**
     * The price of one item with the tax, in thousandths of a coin.
     */
    public static long withTaxMilli(long priceMilli, int taxPermille) {
        return Money.addCapped(priceMilli, Money.share(priceMilli, Math.max(0, taxPermille), 1000));
    }

    /**
     * What a number of items costs without the tax, in whole coins, rounded up.
     */
    public static long total(long priceMilli, long count) {
        if (count <= 0 || priceMilli <= 0) {
            return 0;
        }
        java.math.BigInteger sum = java.math.BigInteger.valueOf(priceMilli).multiply(java.math.BigInteger.valueOf(count))
                .add(java.math.BigInteger.valueOf(MILLI - 1)).divide(java.math.BigInteger.valueOf(MILLI));
        return sum.min(java.math.BigInteger.valueOf(Money.MAX)).longValue();
    }

    /**
     * The tax on a price in whole coins, rounded to the nearest coin.
     */
    public static long tax(long total, int taxPermille) {
        if (total <= 0 || taxPermille <= 0) {
            return 0;
        }
        return Money.share(Money.addCapped(Money.multiplyCapped(total, taxPermille), 500), 1, 1000);
    }

    /**
     * The most a shop may ask for an item without the tax, in thousandths of a coin: the worth and a half, and what bringing it
     * from abroad costs.
     *
     * @param worth      the worth of the item
     * @param importCost what one of it costs from abroad, in whole coins; 0 or less for none
     */
    public static long ceilingMilli(double worth, long importCost) {
        long ceiling = (long) Math.floor(Math.max(0, worth) * 1.5 * MILLI);
        if (importCost > 0) {
            ceiling = Math.min(ceiling, Money.multiplyCapped(importCost, MILLI));
        }
        return Math.max(0, ceiling);
    }

    /**
     * Whether a shop should buy an item at a cost: it can sell it at least a little above that under the ceiling.
     */
    public static boolean worthBuying(long costMilli, long ceilingMilli) {
        return costMilli > 0 && priceMilli(costMilli, LEAST_MARGIN) <= ceilingMilli;
    }

    /**
     * The price a shop asks, never above the ceiling.
     */
    public static long askedMilli(long costMilli, int marginPermille, long ceilingMilli) {
        return Math.max(costMilli, Math.min(priceMilli(costMilli, marginPermille), ceilingMilli));
    }

    /**
     * The margin of the next day: towards what gives the keepers a fair income from yesterday's sales after the upkeep of the
     * shop, at most a step a day, within bounds. Without sales yesterday it stays.
     *
     * @param fairIncome    what a keeper should earn a day
     * @param keepers       the keepers of the shop
     * @param soldCost      what the goods sold yesterday cost the shop
     * @param upkeepPercent the share of the takings its upkeep takes
     */
    public static int nextMargin(int marginPermille, long fairIncome, int keepers, long soldCost, int minPercent, int maxPercent, int stepPercent,
                                 int upkeepPercent) {
        int min = minPercent * 10;
        int max = Math.max(min, maxPercent * 10);
        int current = Math.max(min, Math.min(max, marginPermille));
        if (soldCost <= 0) {
            return current;
        }
        // m x cost - upkeep x cost x (1 + m) = fair income  =>  m = (fair / cost + upkeep) / (1 - upkeep)
        long upkeep = Math.max(0, Math.min(900, upkeepPercent * 10L));
        long fair = Money.share(Money.multiplyCapped(Math.max(0, fairIncome), Math.max(1, keepers)), 1000, soldCost);
        long target = Money.share(Money.addCapped(fair, upkeep), 1000, 1000 - upkeep);
        int clampedTarget = (int) Math.max(min, Math.min(max, target));
        int step = stepPercent * 10;
        if (clampedTarget > current) {
            return Math.min(clampedTarget, current + step);
        }
        return Math.max(clampedTarget, current - step);
    }

    public static int nextMargin(int marginPermille, long fairIncome, int keepers, long soldCost, int minPercent, int maxPercent, int stepPercent) {
        return nextMargin(marginPermille, fairIncome, keepers, soldCost, minPercent, maxPercent, stepPercent, 0);
    }

    /**
     * What the crown pays for one item of a closing shop: the lower of its price of buying and what the shop paid, so that a
     * shop that bought cheap never sells dearer to the crown.
     */
    public static long buyback(long crownBuyPrice, long costMilli) {
        return Math.max(0, Math.min(crownBuyPrice, total(costMilli, 1)));
    }

    /**
     * What a shop pays a player for one item, before the reputation of the player: a share of the lower of the price of the crown
     * and the worth for an item the crown sells, else a share of the price of the shop itself without the tax (its worth where it
     * has none), never above the worth. So a king who sets a high price of selling does not let his players drain the tills of the
     * citizens, and the shop never pays more than an item is worth.
     *
     * @param crownSells whether the crown sells the item: it has it in stock or the king set a price
     * @param crownPrice what the crown asks for one, in whole coins
     * @param ownMilli   what the shop asks for one without the tax, in thousandths, 0 when it has none
     */
    public static long playerSellUnit(boolean crownSells, long crownPrice, long ownMilli, double worth, int sellPercent, int ownPercent) {
        double value = Math.max(0.0, worth);
        double base;
        int percent;
        if (crownSells) {
            base = Math.min(Math.max(0, crownPrice), value);
            percent = sellPercent;
        } else {
            base = ownMilli > 0 ? Math.min(ownMilli / (double) MILLI, value) : value;
            percent = ownPercent;
        }
        return capToWorth((long) Math.floor(base * Math.max(0, Math.min(100, percent)) / 100.0), worth);
    }

    /**
     * What a shop pays a player, never above the worth of the item: the reputation of the player raises it at most to that.
     */
    public static long capToWorth(long unit, double worth) {
        return Math.max(0, Math.min(unit, (long) Math.floor(Math.max(0.0, worth))));
    }

    /**
     * Whether townspeople buy the lot of a player: one of it costs no more than the reference price of their city with the tax and
     * no more than the worth and a half.
     *
     * @param unitMilli      what one of the lot costs, in thousandths
     * @param referenceMilli the reference price of the city with the tax, in thousandths, below 0 for none
     */
    public static boolean lotWanted(long unitMilli, long referenceMilli, double worth) {
        return referenceMilli >= 0 && unitMilli <= referenceMilli && unitMilli <= (long) Math.floor(Math.max(0.0, worth) * 1.5 * MILLI);
    }

    /**
     * The price a player pays for one item without the tax, after the reputation of the player changed the price: the discount
     * comes out of the margin and never takes the price below what the shop paid.
     *
     * @param askedMilli    the price the shop asks
     * @param reputationMilli the price as the reputation of the player makes it
     */
    public static long playerMilli(long askedMilli, long reputationMilli, long costMilli) {
        return Math.max(costMilli, reputationMilli);
    }
}
