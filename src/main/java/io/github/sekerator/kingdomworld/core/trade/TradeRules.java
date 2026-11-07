package io.github.sekerator.kingdomworld.core.trade;

/**
 * Server settings of trade: prices of the market, the external market, exchange rates and caravans.
 *
 * @param exportFactor          share of an item's worth the external market pays for it, below {@code importFactor}
 * @param importFactor          share of an item's worth the external market asks for it
 * @param exportPerMerchant     items one merchant sells abroad in a game day as a beginner
 * @param exportExperienceBonus how much more an experienced merchant sells, 1.0 doubles the amount
 * @param exportMaxIncomePerDay most money one kingdom earns abroad in a game day
 * @param importMaxPerDay       most items one kingdom orders from abroad in a game day
 * @param importDelayTicks      game ticks an order from abroad takes to arrive
 * @param demandElasticity      how strongly the auto price follows the stock: 0 keeps the worth, 1 follows the ratio
 * @param minPricePercent       the auto price never falls below this share of the worth, in percent
 * @param maxPricePercent       the auto price never rises above this share of the worth, in percent
 * @param stockTarget           the stock of an item at which the auto price equals its worth
 * @param spreadPercent         how much less the kingdom pays for an item than it asks for it, in percent
 * @param defaultSalesTaxPercent the tax on the sales of players in a new market, in percent
 * @param maxSalesTaxPercent    the highest tax on sales a king may set, in percent
 * @param maxLotsPerPlayer      lots one player may have on one market
 * @param maxEntries            items in the list of what a kingdom sells, buys or exports, each
 * @param defaultExchangeFeePercent the fee of a new bank for exchanging currencies, in percent
 * @param maxExchangeFeePercent the highest fee a king may set, in percent
 * @param minRate               lowest exchange rate, in millionths of a unit of worth per copper coin
 * @param maxRate               highest exchange rate, in millionths
 * @param rateSmoothingPercent  how far a rate moves towards its target in a game day, in percent
 * @param productionDays        days of production a kingdom's wealth counts
 * @param minSupply             money of a currency the rate counts at least, so that a new currency is not worth a fortune
 * @param caravanBlocksPerMinute how fast caravans travel
 * @param caravanLossChance     chance that a caravan is lost on the way, from 0 to 1
 * @param proposalDays          game days a proposal of a deal waits for an answer
 * @param maxDealsPerKingdom    deals and proposals one kingdom may have open
 * @param maxPeriodDays         longest period of a regular deal, in game days
 */
public record TradeRules(double exportFactor, double importFactor, int exportPerMerchant, double exportExperienceBonus,
                         long exportMaxIncomePerDay, int importMaxPerDay, long importDelayTicks, double demandElasticity,
                         int minPricePercent, int maxPricePercent, int stockTarget, int spreadPercent, int defaultSalesTaxPercent,
                         int maxSalesTaxPercent, int maxLotsPerPlayer, int maxEntries, int defaultExchangeFeePercent,
                         int maxExchangeFeePercent, long minRate, long maxRate, int rateSmoothingPercent, int productionDays,
                         long minSupply, double caravanBlocksPerMinute, double caravanLossChance, int proposalDays,
                         int maxDealsPerKingdom, int maxPeriodDays) {
    /** Experience at which a merchant gets the whole bonus. */
    public static final int FULL_EXPERIENCE = 1_000;

    /**
     * The same rules with another number of lots a player may keep at a market, for a kingdom whose markets give more.
     */
    public TradeRules withMaxLots(int lots) {
        return new TradeRules(exportFactor, importFactor, exportPerMerchant, exportExperienceBonus, exportMaxIncomePerDay, importMaxPerDay,
                importDelayTicks, demandElasticity, minPricePercent, maxPricePercent, stockTarget, spreadPercent, defaultSalesTaxPercent,
                maxSalesTaxPercent, Math.max(0, lots), maxEntries, defaultExchangeFeePercent, maxExchangeFeePercent, minRate, maxRate,
                rateSmoothingPercent, productionDays, minSupply, caravanBlocksPerMinute, caravanLossChance, proposalDays, maxDealsPerKingdom,
                maxPeriodDays);
    }

    public static final TradeRules DEFAULT = new TradeRules(0.6, 1.5, 64, 1.0, 2_000, 256, 6_000, 0.5, 50, 300, 64, 30, 5, 25,
            8, 32, 2, 10, ExchangeRates.ONE / 10, ExchangeRates.ONE * 10, 25, 7, 1_000, 200.0, 0.02, 3, 16, 30);

    public TradeRules {
        if (!(exportFactor >= 0.0 && exportFactor < importFactor && importFactor <= 100.0)) {
            throw new IllegalArgumentException("The export factor must be below the import factor");
        }
        if (exportPerMerchant < 0 || !(exportExperienceBonus >= 0.0 && exportExperienceBonus <= 10.0) || exportMaxIncomePerDay < 0
                || importMaxPerDay < 0 || importDelayTicks < 0 || !(demandElasticity >= 0.0 && demandElasticity <= 4.0)
                || minPricePercent < 1 || maxPricePercent < minPricePercent || maxPricePercent > 10_000 || stockTarget < 1
                || spreadPercent < 0 || spreadPercent > 95 || maxSalesTaxPercent < 0 || maxSalesTaxPercent > 100
                || defaultSalesTaxPercent < 0 || defaultSalesTaxPercent > maxSalesTaxPercent || maxLotsPerPlayer < 0 || maxEntries < 1
                || maxExchangeFeePercent < 0 || maxExchangeFeePercent > 90 || defaultExchangeFeePercent < 0
                || defaultExchangeFeePercent > maxExchangeFeePercent || minRate < 1 || maxRate < minRate
                || rateSmoothingPercent < 1 || rateSmoothingPercent > 100 || productionDays < 0 || minSupply < 1
                || !(caravanBlocksPerMinute > 0.0) || !(caravanLossChance >= 0.0 && caravanLossChance <= 1.0) || proposalDays < 1
                || maxDealsPerKingdom < 1 || maxPeriodDays < 1) {
            throw new IllegalArgumentException("Trade rules out of range");
        }
    }

    /**
     * Items a merchant with this experience sells abroad in a game day.
     */
    public int exportVolume(int experience) {
        double bonus = Math.min(1.0, Math.max(0, experience) / (double) FULL_EXPERIENCE) * exportExperienceBonus;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(exportPerMerchant * (1.0 + bonus)));
    }

    public int salesTaxPermille(int percent) {
        return Math.max(0, Math.min(maxSalesTaxPercent, percent)) * 10;
    }
}
