package io.github.sekerator.kingdomworld.core.magic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForgeEnergyRateTest {

    @Test
    void unitsRoundUpAndForgeEnergyIsTheirExactWorth() {
        assertEquals(1, ForgeEnergyRate.unitsFor(1, 40));
        assertEquals(1, ForgeEnergyRate.unitsFor(40, 40));
        assertEquals(2, ForgeEnergyRate.unitsFor(41, 40));
        assertEquals(0, ForgeEnergyRate.unitsFor(0, 40));
        assertEquals(400, ForgeEnergyRate.feFor(10, 40));
        assertEquals(0, ForgeEnergyRate.feFor(-3, 40));
        assertEquals(Long.MAX_VALUE, ForgeEnergyRate.feFor(Long.MAX_VALUE / 2, 40));
    }

    @Test
    void aGeneratorTakesOnlyWhatTheTickNeedsAndTheStoreHas() {
        // Wants 1000, may give 400 per tick: 10 units at 40 each.
        assertEquals(10, ForgeEnergyRate.unitsToTake(1_000, 0, 400, 40, 1_000));
        // The buffer already covers part of it.
        assertEquals(5, ForgeEnergyRate.unitsToTake(1_000, 200, 400, 40, 1_000));
        assertEquals(0, ForgeEnergyRate.unitsToTake(100, 150, 400, 40, 1_000));
        // Never more than the store holds.
        assertEquals(3, ForgeEnergyRate.unitsToTake(1_000, 0, 400, 40, 3));
        assertEquals(0, ForgeEnergyRate.unitsToTake(1_000, 0, 400, 40, 0));
        assertEquals(0, ForgeEnergyRate.unitsToTake(1_000, 0, 0, 40, 100));
    }
}
