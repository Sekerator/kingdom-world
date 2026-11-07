package io.github.sekerator.kingdomworld.core.economy;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollTest {

    @Test
    void sharesNeverExceedTheFunds() {
        assertArrayEquals(new long[]{10, 6, 0}, Payroll.distribute(new long[]{10, 6, 0}, 100));
        assertArrayEquals(new long[]{5, 3, 0}, Payroll.distribute(new long[]{10, 6, 0}, 8));
        assertArrayEquals(new long[]{0, 0}, Payroll.distribute(new long[]{10, 6}, 0));
        long[] wages = {7, 7, 7};
        long[] paid = Payroll.distribute(wages, 20);
        assertTrue(paid[0] + paid[1] + paid[2] <= 20);
        long[] huge = Payroll.distribute(new long[]{Money.MAX, Money.MAX}, Money.MAX - 1);
        assertTrue(huge[0] + huge[1] <= Money.MAX, "no overflow with the largest amounts");
    }

    @Test
    void payMovesTheMood() {
        assertEquals(Payroll.NOTHING_EXPECTED, Payroll.percent(5, 0));
        assertEquals(0, Payroll.mood(Payroll.NOTHING_EXPECTED));
        assertEquals(0, Payroll.mood(100));
        assertEquals(Payroll.UNPAID_MOOD, Payroll.mood(0));
        assertEquals(-10, Payroll.mood(50));
        assertEquals(Payroll.GENEROUS_MOOD, Payroll.mood(200));
        assertEquals(200, Payroll.percent(1_000, 10));

        int fed = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20.0f, true, false, 0));
        int unpaid = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20.0f, true, false, 0, 0));
        int paid = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20.0f, true, false, 0, 100));
        assertEquals(fed, paid, "the usual wage changes nothing");
        assertEquals(fed + Payroll.UNPAID_MOOD, unpaid);
    }

    @Test
    void moodChangesThePaceOfWorkGently() {
        assertEquals(1.0, Payroll.workSpeed(CitizenNeeds.NEUTRAL_MOOD, 0.2), 1e-9);
        assertEquals(1.2, Payroll.workSpeed(100, 0.2), 1e-9);
        assertEquals(0.8, Payroll.workSpeed(0, 0.2), 1e-9);
        assertEquals(1.0, Payroll.workSpeed(0, 0.0), 1e-9);
        assertEquals(1.5, Payroll.workSpeed(100, 3.0), 1e-9, "the effect is limited");
    }

    @Test
    void taxesTakeWhatThereIs() {
        assertEquals(new Taxes.Collection(10, 0), Taxes.collect(50, 0, 10));
        assertEquals(new Taxes.Collection(4, 6), Taxes.collect(4, 0, 10));
        assertEquals(new Taxes.Collection(15, 0), Taxes.collect(20, 5, 10));
        assertEquals(new Taxes.Collection(0, Money.MAX), Taxes.collect(0, Money.MAX, 10), "a debt stops growing");
    }
}
