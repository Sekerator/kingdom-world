package io.github.sekerator.kingdomworld.core.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricesTest {
    private final TradeRules rules = TradeRules.DEFAULT;

    @Test
    void theAutoPriceIsTheWorthAtTheTargetStock() {
        assertEquals(1.0, Prices.demandFactor(rules.stockTarget(), rules.stockTarget(), rules), 1e-9);
        assertEquals(20, Prices.autoSellPrice(20.0, rules.stockTarget(), rules));
    }

    @Test
    void scarceGoodsCostMoreAndPlentifulOnesLessWithinTheBounds() {
        long scarce = Prices.autoSellPrice(100.0, 4, rules);
        long usual = Prices.autoSellPrice(100.0, rules.stockTarget(), rules);
        long plenty = Prices.autoSellPrice(100.0, 1_000, rules);
        assertTrue(scarce > usual && usual > plenty, scarce + " / " + usual + " / " + plenty);
        assertEquals(300, Prices.autoSellPrice(100.0, 0, new TradeRules(0.6, 1.5, 64, 1.0, 2_000, 256, 6_000, 4.0, 50, 300, 64, 30, 5, 25, 8, 32,
                2, 10, 100_000, 10_000_000, 25, 7, 1_000, 200.0, 0.02, 3, 16, 30)), "Capped at the top");
        assertEquals(50, Prices.autoSellPrice(100.0, 1_000_000_000L, rules), "Capped at the bottom");
        // Prices fall as the stock grows.
        long previous = Long.MAX_VALUE;
        for (long stock = 0; stock < 10_000; stock += 37) {
            long price = Prices.autoSellPrice(100.0, stock, rules);
            assertTrue(price <= previous, "The price rose with the stock at " + stock);
            previous = price;
        }
    }

    @Test
    void theKingdomPaysLessThanItAsks() {
        for (double worth : new double[]{2.0, 5.0, 20.0, 400.0, 5_000.0}) {
            for (long stock : new long[]{0, 10, 64, 500}) {
                long sell = Prices.autoSellPrice(worth, stock, rules);
                long buy = Prices.autoBuyPrice(worth, stock, rules);
                assertTrue(buy < sell, "Worth " + worth + ", stock " + stock + ": buys at " + buy + ", sells at " + sell);
            }
        }
        assertEquals(0, Prices.autoBuyPrice(1.0, 1_000, rules), "Nearly worthless goods are not bought by the auto price");
        assertEquals(7, Prices.price(7, 20), "The king's price wins");
        assertEquals(20, Prices.price(0, 20));
    }

    @Test
    void exportingIsAlwaysCheaperThanImporting() {
        for (double worth : new double[]{0.4, 1.0, 3.3, 20.0, 400.0, 1e9}) {
            for (long count : new long[]{1, 7, 64, 10_000}) {
                long income = Prices.exportIncome(worth, count, rules);
                long cost = Prices.importCost(worth, count, rules);
                assertTrue(income < cost || (income == 0 && cost == 0), worth + " x" + count + ": " + income + " >= " + cost);
            }
        }
        assertEquals(12, Prices.exportIncome(20.0, 1, rules));
        assertEquals(30, Prices.importCost(20.0, 1, rules));
        assertEquals(0, Prices.exportIncome(1.0, 1, rules), "One cobblestone brings nothing");
        assertEquals(6, Prices.exportIncome(1.0, 10, rules), "Ten bring six");
        assertEquals(0, Prices.exportIncome(Double.NaN, 5, rules));
    }

    @Test
    void theSalesTaxGoesToTheTreasury() {
        Prices.Sale sale = Prices.split(1_000, 50);
        assertEquals(950, sale.seller());
        assertEquals(50, sale.tax());
        Prices.Sale small = Prices.split(19, 50);
        assertEquals(19, small.seller() + small.tax(), "Nothing is made or lost");
        assertEquals(0, small.tax(), "The tax is rounded down");
        assertEquals(-1, Prices.total(1_000_000_000_000L, 1_000_000_000L), "Overflow is refused");
        assertEquals(640, Prices.total(10, 64));
    }

    @Test
    void merchantsSellMoreWithExperience() {
        assertEquals(64, rules.exportVolume(0));
        assertEquals(96, rules.exportVolume(TradeRules.FULL_EXPERIENCE / 2));
        assertEquals(128, rules.exportVolume(TradeRules.FULL_EXPERIENCE));
        assertEquals(128, rules.exportVolume(1_000_000), "The bonus stops growing");
        assertEquals(250, rules.salesTaxPermille(25));
        assertEquals(250, rules.salesTaxPermille(99), "Within the config");
    }
}
