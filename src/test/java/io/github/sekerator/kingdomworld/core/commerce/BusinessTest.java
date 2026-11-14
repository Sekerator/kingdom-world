package io.github.sekerator.kingdomworld.core.commerce;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shop just starting out gets its first goods on credit even after the empty days it stood since it opened, and no longer
 * once it bought, sold, holds goods or owes the crown; a kind of goods it sold none of lately is stocked by one basket of its
 * strength.
 */
class BusinessTest {
    private static Business shop() {
        Business.Place place = new Business.Place(Business.PlaceKind.COUNTER, 0, 0, "minecraft:overworld", 1, 64, 1);
        return new Business(1, UUID.randomUUID(), 0, place, 10, 200);
    }

    private static long[] noKinds() {
        return new long[GoodsCategory.values().length];
    }

    @Test
    void aShopThatStoodEmptySinceItOpenedIsStillStartingOut() {
        Business shop = shop();
        assertTrue(shop.startingOut());
        // The day it opened is written though nothing happened: the goods of the crown are bought before the shops open.
        shop.record(10, new Business.Day(0, 0, 0, -5, noKinds()));
        shop.record(11, new Business.Day(0, 0, 0, -5, noKinds()));
        assertTrue(shop.startingOut(), "An empty day took the credit of a new shop away");
    }

    @Test
    void aShopThatTradedOwedOrHoldsGoodsIsNoLongerStartingOut() {
        Business bought = shop();
        bought.record(10, new Business.Day(0, 0, 80, 0, noKinds()));
        assertFalse(bought.startingOut());

        Business sold = shop();
        sold.record(10, new Business.Day(30, 20, 0, 5, noKinds()));
        assertFalse(sold.startingOut());

        Business owing = shop();
        owing.setDebt(40);
        assertFalse(owing.startingOut());

        Business stocked = shop();
        stocked.addGoods("minecraft:bread", GoodsCategory.FOOD.ordinal(), 4, 3_000);
        assertFalse(stocked.startingOut());
    }

    @Test
    void aKindSoldNoneOfLatelyIsStockedByOneBasket() {
        Business shop = shop();
        shop.record(10, new Business.Day(0, 0, 0, -5, noKinds()));
        long average = shop.averageCategory(11, GoodsCategory.FOOD.ordinal());
        assertEquals(0, average, "An empty day counts as a day without sales");
        // Without sales of the kind the shop keeps a basket of its strength, so that the shop that stood empty buys again.
        assertEquals(60, CityDay.stockTarget(average, 150.0, 40, 3));
        assertEquals(60, CityDay.stockTarget(-1, 150.0, 40, 3));
        assertEquals(0, CityDay.stockTarget(-1, -5.0, 40, 3));
        // With sales it keeps the days of stock of them.
        assertEquals(120, CityDay.stockTarget(40, 150.0, 40, 3));
    }
}
