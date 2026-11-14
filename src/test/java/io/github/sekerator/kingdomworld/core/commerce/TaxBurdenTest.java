package io.github.sekerator.kingdomworld.core.commerce;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The burden of the taxes for stability and the nobles: the default taxes weigh 100 as before, the heavier of the poll tax and the
 * cities counts, the cities weighed by their people.
 */
class TaxBurdenTest {
    @Test
    void theDefaultsWeighAsBefore() {
        assertEquals(100, TaxBurden.percent(5, 5, List.of(new TaxBurden.City(200, 64), new TaxBurden.City(200, 1_000))));
        assertEquals(100, TaxBurden.percent(5, 5, List.of()), "a kingdom without cities: the poll tax alone");
    }

    @Test
    void theHeavierCounts() {
        assertEquals(300, TaxBurden.percent(15, 5, List.of(new TaxBurden.City(200, 10))));
        assertEquals(250, TaxBurden.percent(5, 5, List.of(new TaxBurden.City(500, 10))));
        assertTrue(TaxBurden.citiesHeavier(5, 5, List.of(new TaxBurden.City(500, 10))));
        assertFalse(TaxBurden.citiesHeavier(15, 5, List.of(new TaxBurden.City(500, 10))));
    }

    @Test
    void citiesAreWeighedByTheirPeople() {
        // A heavy village of 10 and a light city of 990: nearly the light one.
        int percent = TaxBurden.cityPercent(List.of(new TaxBurden.City(700, 10), new TaxBurden.City(150, 990)));
        assertEquals(77, percent);
        assertEquals(0, TaxBurden.cityPercent(List.of(new TaxBurden.City(700, 0))), "a city of nobody weighs nothing");
        assertEquals(TaxBurden.MAX_PERCENT, TaxBurden.pollPercent(Long.MAX_VALUE / 2, 1));
    }
}
