package io.github.sekerator.kingdomworld.core.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluralsTest {

    @Test
    void russianFormsFollowTheLastDigits() {
        long[] one = {1, 21, 31, 101, 121, 1_001};
        long[] few = {2, 3, 4, 22, 24, 102, 1_004};
        long[] many = {0, 5, 9, 10, 11, 12, 13, 14, 15, 20, 25, 100, 111, 112, 114, 1_000_000};
        for (long n : one) {
            assertEquals(Plurals.Russian.ONE, Plurals.russian(n), "for " + n);
        }
        for (long n : few) {
            assertEquals(Plurals.Russian.FEW, Plurals.russian(n), "for " + n);
        }
        for (long n : many) {
            assertEquals(Plurals.Russian.MANY, Plurals.russian(n), "for " + n);
        }
        assertEquals("золотой", Plurals.russian(1, "золотой", "золотых", "золотых"));
        assertEquals("медяка", Plurals.russian(2, "медяк", "медяка", "медяков"));
        assertEquals("медяков", Plurals.russian(5, "медяк", "медяка", "медяков"));
        assertEquals("медяк", Plurals.russian(21, "медяк", "медяка", "медяков"));
        assertEquals("серебряников", Plurals.russian(11, "серебряник", "серебряника", "серебряников"));
    }

    @Test
    void englishHasOneAndOther() {
        assertEquals(Plurals.English.ONE, Plurals.english(1));
        for (long n : new long[]{0, 2, 11, 21, 101}) {
            assertEquals(Plurals.English.OTHER, Plurals.english(n), "for " + n);
        }
        assertEquals("coin", Plurals.english(1, "coin", "coins"));
        assertEquals("coins", Plurals.english(21, "coin", "coins"));
    }

    @Test
    void keyFormsServeBothLanguages() {
        assertEquals(Plurals.Form.N1, Plurals.form(1));
        assertEquals(Plurals.Form.N21, Plurals.form(21));
        assertEquals(Plurals.Form.N21, Plurals.form(101));
        assertEquals(Plurals.Form.N2, Plurals.form(2));
        assertEquals(Plurals.Form.N2, Plurals.form(34));
        assertEquals(Plurals.Form.N5, Plurals.form(0));
        assertEquals(Plurals.Form.N5, Plurals.form(5));
        assertEquals(Plurals.Form.N5, Plurals.form(11));
        assertEquals(Plurals.Form.N5, Plurals.form(112));
        assertEquals("n21", Plurals.Form.N21.suffix());
        // Exactly one is singular in English, every other form plural, so English never reads "21 coin".
        for (long n = 0; n < 200; n++) {
            boolean englishSingular = Plurals.form(n) == Plurals.Form.N1;
            assertEquals(Plurals.english(n) == Plurals.English.ONE, englishSingular, "for " + n);
        }
    }
}
