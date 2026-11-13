package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoldedCitizenTest {
    @Test
    void aCitizenPacksIntoEightBytesAndBack() {
        for (int slot : new int[]{0, 1, 77, 65535}) {
            FoldedCitizen citizen = new FoldedCitizen(slot, 13, 4321, 200);
            assertEquals(citizen, FoldedCitizen.unpack(citizen.pack()));
        }
        assertEquals(new FoldedCitizen(5, 255, 65535, 255), FoldedCitizen.unpack(new FoldedCitizen(5, 999, 99999, 999).pack()), "kept in range");
        assertThrows(IllegalArgumentException.class, () -> new FoldedCitizen(70000, 0, 0, 0));
    }

    @Test
    void theFoldedOfASettlementKeepOnePerSlot() {
        long[] folded = new long[0];
        folded = FoldedCitizen.with(folded, new FoldedCitizen(3, 1, 30, 50));
        folded = FoldedCitizen.with(folded, new FoldedCitizen(7, 2, 40, 60));
        folded = FoldedCitizen.with(folded, new FoldedCitizen(3, 4, 31, 51));
        assertEquals(2, folded.length, "folding a slot twice keeps one record");
        assertEquals(new FoldedCitizen(3, 4, 31, 51), FoldedCitizen.of(folded, 3));
        folded = FoldedCitizen.without(folded, 3);
        assertArrayEquals(new long[]{new FoldedCitizen(7, 2, 40, 60).pack()}, folded);
        assertNull(FoldedCitizen.of(folded, 3));
        assertArrayEquals(folded, FoldedCitizen.without(folded, 99));
    }
}
