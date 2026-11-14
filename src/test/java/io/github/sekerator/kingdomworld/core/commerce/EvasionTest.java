package io.github.sekerator.kingdomworld.core.commerce;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evasion: the taxes collected have one top, near 37.5 percent of burden, and the heaviest taxes collect less than light ones;
 * the means of the people follow the burden.
 */
class EvasionTest {
    private static final int START = 25;
    private static final int MAX = 90;

    private static long collected(int burdenPercent) {
        int evasion = Evasion.permille(burdenPercent * 10, START, MAX);
        return Evasion.collected(burdenPercent * 1_000L, evasion);
    }

    @Test
    void theCollectedTaxHasOneTopNearThirtySevenAndAHalfPercent() {
        int top = 0;
        long best = -1;
        for (int burden = 0; burden <= 75; burden++) {
            if (collected(burden) > best) {
                best = collected(burden);
                top = burden;
            }
        }
        assertTrue(top >= 37 && top <= 38, "the top is at " + top);
        for (int burden = 1; burden <= top; burden++) {
            assertTrue(collected(burden) >= collected(burden - 1), "the collected tax fell before the top at " + burden);
        }
        for (int burden = top + 1; burden <= 70; burden++) {
            assertTrue(collected(burden) <= collected(burden - 1), "the collected tax rose again at " + burden);
        }
        for (int burden = 55; burden <= 75; burden++) {
            assertTrue(collected(burden) < collected(25), "heavy taxes collect less than light ones at " + burden);
        }
        assertTrue(collected(75) < collected(25), "the heaviest taxes collect less than 25%%: " + collected(75) + " against " + collected(25));
    }

    @Test
    void evasionStartsAboveTheStartAndStopsAtTheMost() {
        assertEquals(0, Evasion.permille(250, START, MAX));
        assertEquals(100, Evasion.permille(300, START, MAX), "two points for every point above");
        assertEquals(900, Evasion.permille(1_000, START, MAX));
        assertEquals(10, Evasion.percent(300, START, MAX));
        assertEquals(1_000, Evasion.collected(1_000, 0));
        assertEquals(500, Evasion.collected(1_000, 500));
        assertEquals(0, Evasion.collected(0, 500));
    }

    @Test
    void theMeansOfThePeopleFollowTheBurdenTheShopsAndTheCrown() {
        assertEquals(3, CityMeans.fromBurden(150));
        assertEquals(0, CityMeans.fromBurden(200), "the default taxes change nothing");
        assertEquals(-5, CityMeans.fromBurden(300));
        assertEquals(-10, CityMeans.fromBurden(400));
        assertEquals(-15, CityMeans.fromBurden(500));
        assertEquals(5, CityMeans.fromShops(90));
        assertEquals(2, CityMeans.fromShops(60));
        assertEquals(0, CityMeans.fromShops(59));
        assertEquals(0, CityMeans.of(200, 0, false, false, 500, 75).value(), "an old world loses no mood");
        assertEquals(8, CityMeans.of(100, 95, false, false, 500, 75).value(), "at most +8");
        assertEquals(-15, CityMeans.of(600, 0, true, true, 1000, 75).value(), "at least -15");
        assertEquals(-5, CityMeans.of(200, 0, true, false, 500, 75).recall());
        assertEquals(-3, CityMeans.of(200, 0, false, true, 800, 75).share(), "a city with a head that keeps nothing");
        assertEquals(0, CityMeans.of(200, 0, false, false, 1000, 75).share(), "a city without a head is a passage anyway");
    }

    @Test
    void theBurdenOfTheTaxesIsTheHeavierOfThePollTaxAndTheCities() {
        assertEquals(100, TaxBurden.percent(5, 5, List.of(new TaxBurden.City(200, 10))), "the defaults weigh as before");
        assertEquals(200, TaxBurden.percent(10, 5, List.of(new TaxBurden.City(200, 10))));
        assertEquals(200, TaxBurden.percent(5, 5, List.of(new TaxBurden.City(400, 10))));
        // A heavy city of 10 people and a light one of 30: (400 x 10 + 100 x 30) / 40 = 175 thousandths, against 200.
        assertEquals(87, TaxBurden.cityPercent(List.of(new TaxBurden.City(400, 10), new TaxBurden.City(100, 30))));
        assertEquals(0, TaxBurden.cityPercent(List.of()));
        assertTrue(TaxBurden.citiesHeavier(5, 5, List.of(new TaxBurden.City(500, 1))));
        assertTrue(!TaxBurden.citiesHeavier(10, 5, List.of(new TaxBurden.City(200, 1))));
        assertEquals(100, TaxBurden.pollPercent(3, 0), "no usual tax counts as usual");
    }
}
