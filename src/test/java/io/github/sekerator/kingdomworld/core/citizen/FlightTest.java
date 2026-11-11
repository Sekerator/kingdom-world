package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightTest {

    @Test
    void aRefugeBehindTheBodyIsRunTo() {
        // The threat stands to the east, the home lies to the west.
        assertTrue(Flight.leadsAway(0, 0, 5, 0, -20, 0));
        // To the side is away too.
        assertTrue(Flight.leadsAway(0, 0, 5, 0, 0, 20));
    }

    @Test
    void nobodyRunsPastTheThreatToARefugeBeyondIt() {
        assertFalse(Flight.leadsAway(0, 0, 5, 0, 20, 0));
        assertFalse(Flight.leadsAway(0, 0, 5, 0, 20, 4), "A little to the side of the threat is still past it");
    }

    @Test
    void aRefugeNearerThanTheThreatIsReachedFirst() {
        assertTrue(Flight.leadsAway(0, 0, 12, 0, 6, 0));
    }

    @Test
    void aRefugeNextToTheThreatIsNone() {
        assertFalse(Flight.leadsAway(0, 0, 10, 0, 8, 1));
        assertFalse(Flight.leadsAway(0, 0, 3, 0, 0.5, 0), "At home with the threat in the room is no shelter");
        assertTrue(Flight.leadsAway(0, 0, 10, 0, 0.5, 0), "At home with the threat far off is");
    }

    @Test
    void withNoRefugeTheBodyRunsStraightAway() {
        double[] west = Flight.away(0, 0, 5, 0, 14);
        assertEquals(-14, west[0], 1.0E-9);
        assertEquals(0, west[1], 1.0E-9);
        double[] diagonal = Flight.away(0, 0, 3, 4, 10);
        assertEquals(-6, diagonal[0], 1.0E-9);
        assertEquals(-8, diagonal[1], 1.0E-9);
        double[] same = Flight.away(2, 2, 2, 2, 7);
        assertEquals(9, same[0], 1.0E-9, "Standing in one place with the threat, any way is away");
    }
}
