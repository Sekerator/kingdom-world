package io.github.sekerator.kingdomworld.core.commerce;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the crown buys of its citizens: the cheapest first, never above the price it allows, never beyond its sum of the day and
 * never so that its treasury falls below the days of wages it keeps, partly when it cannot buy all.
 */
class CrownPurchaseTest {
    private static CrownPurchase.Offer offer(long shop, long count, long priceMilli) {
        return new CrownPurchase.Offer(shop, "minecraft:oak_planks#made", count, priceMilli, 2.0);
    }

    @Test
    void theCheapestFirst() {
        List<CrownPurchase.Take> takes = CrownPurchase.choose(List.of(offer(1, 10, 3_000), offer(2, 10, 2_000), offer(3, 10, 2_500)), 15,
                3_000, 1_000);
        assertEquals(2, takes.size());
        assertEquals(2, takes.get(0).shop());
        assertEquals(10, takes.get(0).count());
        assertEquals(20, takes.get(0).total());
        assertEquals(3, takes.get(1).shop());
        assertEquals(5, takes.get(1).count());
        assertEquals(13, takes.get(1).total(), "five at 2.5 rounded up");
    }

    @Test
    void neverAboveThePriceAllowed() {
        long cap = CrownPurchase.capMilli(2.0, 150);
        assertEquals(3_000, cap);
        List<CrownPurchase.Take> takes = CrownPurchase.choose(List.of(offer(1, 10, 3_001), offer(2, 4, 3_000)), 10, cap, 1_000);
        assertEquals(1, takes.size());
        assertEquals(2, takes.get(0).shop());
        assertEquals(4, takes.get(0).count(), "only what is cheap enough, a partial purchase");
        assertEquals(1_000, CrownPurchase.capMilli(2.0, 50));
    }

    @Test
    void neverBeyondTheMoney() {
        List<CrownPurchase.Take> takes = CrownPurchase.choose(List.of(offer(1, 100, 2_000)), 100, 3_000, 51);
        assertEquals(25, takes.get(0).count());
        assertEquals(50, takes.get(0).total());
        assertTrue(CrownPurchase.choose(List.of(offer(1, 100, 2_000)), 100, 3_000, 1).isEmpty());
    }

    @Test
    void theSumOfTheDayAndTheDaysOfWagesBoundTheMoney() {
        // A treasury of 5 000 keeping 7 days of wages of 300 a day may spend 2 900, but no more than the sum of the day.
        assertEquals(500, CrownPurchase.budget(5_000, 2_100, 500, 0));
        assertEquals(200, CrownPurchase.budget(5_000, 2_100, 500, 300));
        assertEquals(0, CrownPurchase.budget(5_000, 2_100, 500, 600));
        // A poor treasury buys nothing below what it keeps.
        assertEquals(100, CrownPurchase.budget(2_200, 2_100, 500, 0));
        assertEquals(0, CrownPurchase.budget(2_000, 2_100, 500, 0));
    }
}
