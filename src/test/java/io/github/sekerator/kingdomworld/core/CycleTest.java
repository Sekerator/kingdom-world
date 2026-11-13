package io.github.sekerator.kingdomworld.core;

import io.github.sekerator.kingdomworld.core.membership.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CycleTest {

    @Test
    void goesForwardAndWrapsToTheFirst() {
        assertEquals(1, Cycle.step(0, 1, 5));
        assertEquals(4, Cycle.step(3, 1, 5));
        assertEquals(0, Cycle.step(4, 1, 5));
    }

    @Test
    void goesBackAndWrapsToTheLast() {
        assertEquals(2, Cycle.step(3, -1, 5));
        assertEquals(0, Cycle.step(1, -1, 5));
        assertEquals(4, Cycle.step(0, -1, 5));
    }

    @Test
    void aStepBackUndoesAStepForward() {
        for (int size = 1; size <= 7; size++) {
            for (int index = 0; index < size; index++) {
                assertEquals(index, Cycle.step(Cycle.step(index, 1, size), -1, size));
                assertEquals(index, Cycle.step(Cycle.step(index, -1, size), 1, size));
            }
        }
    }

    @Test
    void aWholeRoundBackComesHome() {
        int index = 2;
        for (int i = 0; i < 5; i++) {
            index = Cycle.step(index, -1, 5);
        }
        assertEquals(2, index);
    }

    @Test
    void anIndexOutsideTheRingIsTakenAroundIt() {
        assertEquals(0, Cycle.step(7, 1, 4));
        assertEquals(2, Cycle.step(-1, -1, 4));
        assertEquals(3, Cycle.step(0, -1, 4));
    }

    @Test
    void oneChoiceOrNoneStaysInPlace() {
        assertEquals(0, Cycle.step(0, 1, 1));
        assertEquals(0, Cycle.step(0, -1, 1));
        assertEquals(0, Cycle.step(3, -1, 0));
    }

    @Test
    void theEdgesOfIntDoNotBreakTheRing() {
        assertEquals(2, Cycle.step(Integer.MAX_VALUE, 1, 7));
        assertEquals(4, Cycle.step(Integer.MIN_VALUE, -1, 7));
        assertEquals(0, Cycle.step(2, Integer.MIN_VALUE, 7));
    }

    @Test
    void enumsGoBothWays() {
        Role[] roles = Role.values();
        assertEquals(roles[1], Cycle.step(roles[0], 1));
        assertEquals(roles[roles.length - 1], Cycle.step(roles[0], -1));
        assertEquals(roles[0], Cycle.step(roles[roles.length - 1], 1));
    }
}
