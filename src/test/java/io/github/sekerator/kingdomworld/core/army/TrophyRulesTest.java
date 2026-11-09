package io.github.sekerator.kingdomworld.core.army;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrophyRulesTest {
    @Test
    void theBuybackCostsHalfTheWorth() {
        TrophyRules rules = TrophyRules.DEFAULT;
        assertEquals(1_500, rules.price(3_000));
        assertEquals(0, rules.price(0));
        assertEquals(0, rules.price(-10));
        assertEquals(3, new TrophyRules(50, 7).price(7));
    }

    @Test
    void theBuybackOpensWithPeaceAndClosesAfterSomeDays() {
        TrophyRules rules = new TrophyRules(50, 7);
        assertFalse(rules.open(0, 10), "Trophies were bought back at war");
        assertFalse(rules.expired(0, 1_000), "Trophies of a war going on went to the holder");
        assertTrue(rules.open(10, 10));
        assertTrue(rules.open(10, 17));
        assertFalse(rules.open(10, 18));
        assertTrue(rules.expired(10, 18));
        assertFalse(rules.expired(10, 17));
        assertThrows(IllegalArgumentException.class, () -> new TrophyRules(-1, 7));
    }
}
