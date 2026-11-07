package io.github.sekerator.kingdomworld.core.magic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalChargesTest {

    @Test
    void aStartedCrystalIsUsedFirstAndRemembersWhatIsLeft() {
        List<CrystalCharges.Charge> charges = List.of(
                new CrystalCharges.Charge(0, 3, 30, false),
                new CrystalCharges.Charge(4, 1, 12, true));
        Optional<List<CrystalCharges.Use>> plan = CrystalCharges.plan(charges, 5);
        assertEquals(Optional.of(List.of(new CrystalCharges.Use(4, 1, 7))), plan);
    }

    @Test
    void theWeakestFreshCrystalsGoBeforeStrongOnes() {
        List<CrystalCharges.Charge> charges = List.of(
                new CrystalCharges.Charge(1, 1, 1_000, false),
                new CrystalCharges.Charge(2, 5, 10, false));
        assertEquals(Optional.of(List.of(new CrystalCharges.Use(2, 2, 5))), CrystalCharges.plan(charges, 15));
        assertEquals(Optional.of(List.of(new CrystalCharges.Use(2, 5, 0), new CrystalCharges.Use(1, 1, 950))),
                CrystalCharges.plan(charges, 100));
    }

    @Test
    void crystalsUsedUpExactlyLeaveNothing() {
        List<CrystalCharges.Charge> charges = List.of(new CrystalCharges.Charge(3, 4, 10, false));
        assertEquals(Optional.of(List.of(new CrystalCharges.Use(3, 2, 0))), CrystalCharges.plan(charges, 20));
    }

    @Test
    void tooLittleEnergyTakesNothing() {
        List<CrystalCharges.Charge> charges = List.of(new CrystalCharges.Charge(0, 2, 10, false), new CrystalCharges.Charge(1, 1, 3, true));
        assertEquals(23, CrystalCharges.total(charges));
        assertTrue(CrystalCharges.plan(charges, 24).isEmpty());
        assertEquals(Optional.of(List.of()), CrystalCharges.plan(charges, 0));
        assertTrue(CrystalCharges.plan(charges, -1).isEmpty());
        assertTrue(CrystalCharges.plan(List.of(), 1).isEmpty());
    }

    @Test
    void thePlanTakesExactlyTheCost() {
        List<CrystalCharges.Charge> charges = List.of(
                new CrystalCharges.Charge(0, 7, 30, false),
                new CrystalCharges.Charge(1, 2, 9, true),
                new CrystalCharges.Charge(2, 1, 100, false));
        for (long cost = 1; cost <= CrystalCharges.total(charges); cost++) {
            List<CrystalCharges.Use> uses = CrystalCharges.plan(charges, cost).orElseThrow();
            long taken = 0;
            for (CrystalCharges.Use use : uses) {
                long energy = charges.stream().filter(c -> c.slot() == use.slot()).findFirst().orElseThrow().energy();
                taken += use.used() * energy - use.left();
                assertTrue(use.left() >= 0 && use.left() < energy, "A crystal kept more than it held: " + use);
            }
            assertEquals(cost, taken, "Cost " + cost + " took " + taken);
        }
    }
}
