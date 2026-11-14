package io.github.sekerator.kingdomworld.core.commerce;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The prices of the shops of citizens: the ceiling, nothing bought at a loss, the steps of the margin, the tax and the price
 * without it making the price, and a discount of reputation never below what the shop paid.
 */
class ShopPricingTest {
    @Test
    void noShopAsksAboveTheCeiling() {
        long ceiling = ShopPricing.ceilingMilli(4.0, 5);
        assertEquals(5_000, ceiling, "the cost of bringing it from abroad is lower than the worth and a half");
        assertEquals(6_000, ShopPricing.ceilingMilli(4.0, 0), "without an import cost the worth and a half");
        assertEquals(5_000, ShopPricing.askedMilli(4_000, 500, ceiling), "a margin of 50%% is cut at the ceiling");
        assertEquals(4_400, ShopPricing.askedMilli(4_000, 100, ceiling));
        assertEquals(4_000, ShopPricing.askedMilli(4_000, 300, 3_000), "never below what the shop paid");
    }

    @Test
    void nothingIsBoughtThatCannotBeSoldAboveWhatItCosts() {
        assertTrue(ShopPricing.worthBuying(4_000, 4_200));
        assertFalse(ShopPricing.worthBuying(4_000, 4_199), "not even five in a hundred above the cost");
        assertFalse(ShopPricing.worthBuying(0, 10_000));
    }

    @Test
    void theMarginMovesAStepADayTowardsAFairIncome() {
        // A fair income of 6 for 2 keepers from goods that cost 40: a margin of 300 thousandths, reached two points a day.
        assertEquals(220, ShopPricing.nextMargin(200, 6, 2, 40, 5, 50, 2));
        assertEquals(180, ShopPricing.nextMargin(200, 6, 2, 1_000, 5, 50, 2), "a big turnover lowers it");
        assertEquals(200, ShopPricing.nextMargin(200, 6, 2, 0, 5, 50, 2), "nothing sold, nothing learned");
        assertEquals(500, ShopPricing.nextMargin(495, 600, 2, 10, 5, 50, 2), "at most the largest margin");
        assertEquals(50, ShopPricing.nextMargin(55, 0, 2, 10, 5, 50, 2), "at least the smallest margin");
        // With an upkeep of 5%% a shop selling goods of 200 aims at (6 / 200 + 0.05) / 0.95 = 8.4%%, not below its upkeep.
        assertEquals(84, ShopPricing.nextMargin(84, 6, 1, 200, 5, 50, 2, 5));
        assertEquals(104, ShopPricing.nextMargin(124, 6, 1, 200, 5, 50, 2, 5));
    }

    @Test
    void theTaxAndThePriceWithoutItMakeThePrice() {
        for (long priceMilli = 1; priceMilli < 40_000; priceMilli += 997) {
            for (int count = 1; count <= 64; count *= 2) {
                long total = ShopPricing.total(priceMilli, count);
                long tax = ShopPricing.tax(total, 100);
                assertEquals(total + tax, total + ShopPricing.tax(total, 100));
                assertTrue(total * 1_000 >= priceMilli * count, "whole coins are rounded up");
                assertTrue(tax <= total, "the tax is never more than the price");
            }
        }
        assertEquals(5, ShopPricing.tax(50, 100));
        assertEquals(1, ShopPricing.tax(5, 100), "half a coin is rounded up");
        assertEquals(0, ShopPricing.tax(4, 100));
        assertEquals(0, ShopPricing.total(1_500, 0));
        assertEquals(2, ShopPricing.total(1_500, 1));
        assertEquals(3, ShopPricing.total(1_500, 2));
    }

    @Test
    void aDiscountOfReputationNeverGoesBelowWhatTheShopPaid() {
        assertEquals(4_000, ShopPricing.playerMilli(5_000, 3_000, 4_000));
        assertEquals(4_500, ShopPricing.playerMilli(5_000, 4_500, 4_000));
        assertEquals(6_000, ShopPricing.playerMilli(5_000, 6_000, 4_000), "an ill reputation pays more");
    }

    @Test
    void theCrownBuysBackNoDearerThanTheShopPaid() {
        assertEquals(3, ShopPricing.buyback(3, 5_000), "the crown pays its own lower price");
        assertEquals(5, ShopPricing.buyback(9, 5_000), "a shop that bought cheap does not sell dearer");
        assertEquals(0, ShopPricing.buyback(-1, 5_000));
    }
}
