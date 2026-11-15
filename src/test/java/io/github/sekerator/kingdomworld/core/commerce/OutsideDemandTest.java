package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.road.CityLevel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The demand from abroad of a city: its level and the root of its people, raised by roads up to twice and by a working market,
 * nothing in war; over a kingdom the first five cities count in full and the city numbered i after them with the weight 5 / i,
 * so that many cities do not print money by their number.
 */
class OutsideDemandTest {
    private static final CraftRules RULES = CraftRules.DEFAULT;

    @Test
    void levelsAndTheRootOfThePeople() {
        assertEquals(2 * Math.sqrt(10), OutsideDemand.of(CityLevel.VILLAGE, 10, 0, false, false, RULES), 1e-9);
        assertEquals(60 + 2 * Math.sqrt(30), OutsideDemand.of(CityLevel.TOWN, 30, 0, false, false, RULES), 1e-9);
        assertEquals(150 + 2 * 8, OutsideDemand.of(CityLevel.CITY, 64, 0, false, false, RULES), 1e-9);
    }

    @Test
    void roadsRaiseItUpToTwiceAndAMarketByItsFactor() {
        double base = OutsideDemand.of(CityLevel.TOWN, 36, 0, false, false, RULES);
        assertEquals(base * 1.5, OutsideDemand.of(CityLevel.TOWN, 36, 2, false, false, RULES), 1e-9);
        assertEquals(base * 2.0, OutsideDemand.of(CityLevel.TOWN, 36, 4, false, false, RULES), 1e-9);
        assertEquals(base * 2.0, OutsideDemand.of(CityLevel.TOWN, 36, 12, false, false, RULES), 1e-9, "never more than twice");
        assertEquals(base * 2.0 * 1.5, OutsideDemand.of(CityLevel.TOWN, 36, 4, true, false, RULES), 1e-9);
    }

    @Test
    void nothingInWarOrBlockade() {
        assertEquals(0, OutsideDemand.of(CityLevel.CITY, 900, 4, true, true, RULES));
    }

    @Test
    void theKingdomCountsFiveInFullAndThenLessAndLess() {
        double[] five = OutsideDemand.weighted(new double[]{10, 50, 30, 20, 40}, 5);
        assertEquals(150, Arrays.stream(five).sum(), 1e-9);
        assertEquals(1.0, OutsideDemand.weight(4, 5));
        assertEquals(1.0, OutsideDemand.weight(5, 5), "the city numbered 5 counts 5 / 5");
        assertEquals(0.5, OutsideDemand.weight(10, 5));
        double[] twenty = new double[20];
        Arrays.fill(twenty, 1.0);
        double sum = Arrays.stream(OutsideDemand.weighted(twenty, 5)).sum();
        assertTrue(sum > 11.5 && sum < 12.5, "twenty cities count about twelve: " + sum);
        double[] hundred = new double[100];
        Arrays.fill(hundred, 1.0);
        double many = Arrays.stream(OutsideDemand.weighted(hundred, 5)).sum();
        assertTrue(many > 19.5 && many < 21.0, "a hundred cities count about twenty: " + many);
        // The largest count in full whatever the order they come in.
        double[] mixed = OutsideDemand.weighted(new double[]{1, 1, 1, 1, 1, 1, 1, 100, 1, 1}, 5);
        assertEquals(100, mixed[7], 1e-9);
    }

    @Test
    void aPlayerNeverGetsMoreThanTheWorthWhateverTheKingAsks() {
        // The crown sells: a share of the lower of its price and the worth.
        assertEquals(9, ShopPricing.playerSellUnit(true, 1_000, 0, 10.0, 90, 70));
        assertEquals(4, ShopPricing.playerSellUnit(true, 5, 0, 10.0, 90, 70));
        // The crown does not sell it: a share of the price of the shop, never above the worth.
        assertEquals(7, ShopPricing.playerSellUnit(false, 0, 50_000, 10.0, 90, 70));
        assertEquals(5, ShopPricing.playerSellUnit(false, 0, 8_000, 10.0, 90, 70));
        assertEquals(7, ShopPricing.playerSellUnit(false, 0, 0, 10.0, 90, 70), "without a price of its own the worth");
        // A good reputation raises it, never above the worth.
        assertEquals(10, ShopPricing.capToWorth(14, 10.0));
        for (long king = 1; king <= 10_000; king *= 3) {
            assertTrue(ShopPricing.playerSellUnit(true, king, 0, 10.0, 90, 70) <= 9);
        }
    }

    @Test
    void aLotSellsToTheTownspeopleNoDearerThanTheReferencePrice() {
        assertTrue(ShopPricing.lotWanted(9_000, 9_900, 8.0));
        assertTrue(ShopPricing.lotWanted(9_900, 9_900, 8.0));
        assertTrue(!ShopPricing.lotWanted(9_901, 9_900, 8.0), "a lot dearer than the shops of the city does not sell");
        assertTrue(!ShopPricing.lotWanted(12_100, 20_000, 8.0), "nor one dearer than the worth and a half");
        assertTrue(!ShopPricing.lotWanted(1_000, -1, 8.0), "nor one the city has no price for");
    }
}
