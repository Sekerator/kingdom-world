package io.github.sekerator.kingdomworld.core.trade;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Prices of the market and of the external market, in the smallest unit of money.
 *
 * <p>The auto price of an item is its worth times a factor of demand: as many items in the storehouses as the target
 * leaves the worth, fewer raise the price and more lower it, within the bounds of the config. The kingdom pays for an
 * item less than it asks for it by the spread, so buying and selling back loses money. Abroad an item sells for less
 * than it costs to bring in, so exporting what was imported loses money too.
 */
public final class Prices {

    private Prices() {
    }

    /**
     * The factor of demand for a stock: {@code ((target + 1) / (stock + 1)) ^ elasticity}, within the bounds.
     */
    public static double demandFactor(long stock, long target, TradeRules rules) {
        double ratio = (Math.max(0, target) + 1.0) / (Math.max(0, stock) + 1.0);
        double factor = Math.pow(ratio, rules.demandElasticity());
        double min = rules.minPricePercent() / 100.0;
        double max = rules.maxPricePercent() / 100.0;
        if (!Double.isFinite(factor)) {
            return max;
        }
        return Math.max(min, Math.min(max, factor));
    }

    /**
     * What the kingdom asks for one item when nobody set a price: the worth times the factor of demand, at least 1 for
     * anything worth something.
     */
    public static long autoSellPrice(double worth, long stock, TradeRules rules) {
        if (!(worth > 0.0) || !Double.isFinite(worth)) {
            return 0;
        }
        double price = worth * demandFactor(stock, rules.stockTarget(), rules);
        return Math.max(1, Math.min(Money.MAX, Math.round(price)));
    }

    /**
     * What the kingdom pays for one item when nobody set a price: the auto price less the spread, rounded down. Items so
     * cheap that this is nothing are not bought.
     */
    public static long autoBuyPrice(double worth, long stock, TradeRules rules) {
        long sell = autoSellPrice(worth, stock, rules);
        return Money.share(sell, 100 - rules.spreadPercent(), 100);
    }

    /**
     * The price of an entry: the king's when one is set, else the auto price.
     */
    public static long price(long manual, long auto) {
        return manual > 0 ? manual : auto;
    }

    /**
     * What a number of items costs at a price per item, or -1 if more than any amount of money.
     */
    public static long total(long unitPrice, long count) {
        if (unitPrice < 0 || count < 0) {
            return -1;
        }
        if (unitPrice != 0 && count > Money.MAX / unitPrice) {
            return -1;
        }
        return unitPrice * count;
    }

    /**
     * What the external market pays for items: their worth times the export factor, for all of them together, rounded
     * down.
     */
    public static long exportIncome(double worth, long count, TradeRules rules) {
        return times(worth, count, rules.exportFactor(), RoundingMode.FLOOR);
    }

    /**
     * What the external market asks for items: their worth times the import factor, rounded up.
     */
    public static long importCost(double worth, long count, TradeRules rules) {
        return times(worth, count, rules.importFactor(), RoundingMode.CEILING);
    }

    private static long times(double worth, long count, double factor, RoundingMode rounding) {
        if (!(worth > 0.0) || !Double.isFinite(worth) || count <= 0) {
            return 0;
        }
        BigDecimal value = BigDecimal.valueOf(worth).multiply(BigDecimal.valueOf(count)).multiply(BigDecimal.valueOf(factor))
                .setScale(0, rounding);
        return value.min(BigDecimal.valueOf(Money.MAX)).longValue();
    }

    /**
     * How a sale of a player's lot splits: the tax goes to the treasury, the rest to the seller.
     */
    public record Sale(long seller, long tax) {
    }

    /**
     * Splits a price by the tax on sales, in thousandths; the tax is rounded down.
     */
    public static Sale split(long price, int taxPermille) {
        long tax = Money.share(Math.max(0, price), Math.max(0, Math.min(1000, taxPermille)), 1000);
        return new Sale(Math.max(0, price) - tax, tax);
    }
}
