package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulerMemoryTest {
    @Test
    void decreesKeepTheNewestFirstAndNoMoreThanTheirRoom() {
        RulerMemory memory = new RulerMemory();
        for (int day = 1; day <= RulerMemory.MAX_DECREES + 3; day++) {
            memory.decree(day, RulerDecision.of(RulerDecision.Kind.BUILD, "house", 1));
        }
        assertEquals(RulerMemory.MAX_DECREES, memory.decrees().size());
        assertEquals(RulerMemory.MAX_DECREES + 3, memory.decrees().get(0).day());
    }

    @Test
    void theChronicleTakesAFewDecreesADay() {
        RulerMemory memory = new RulerMemory();
        assertTrue(memory.chronicle(4, 2));
        assertTrue(memory.chronicle(4, 2));
        assertFalse(memory.chronicle(4, 2), "a third decree of the day is not written");
        assertTrue(memory.chronicle(5, 2), "a new day, a new count");
        assertFalse(new RulerMemory().chronicle(1, 0));
    }

    @Test
    void relationsGrowWithTradeSinkWithRefusalsAndFade() {
        RulerMemory memory = new RulerMemory();
        UUID other = UUID.randomUUID();
        memory.relate(other, RulerMemory.Reason.TRADE);
        memory.relate(other, RulerMemory.Reason.TRADE);
        assertEquals(2 * RulerMemory.Reason.TRADE.weight(), memory.relation(other));
        memory.relate(other, RulerMemory.Reason.LAND);
        assertEquals(2 * RulerMemory.Reason.TRADE.weight() + RulerMemory.Reason.LAND.weight(), memory.relation(other));
        assertEquals(RulerMemory.Reason.TRADE, memory.relations().get(other).main().orElseThrow());
        int before = memory.relation(other);
        memory.fade();
        assertEquals(before - Integer.signum(before), memory.relation(other));
        for (int i = 0; i < 100; i++) {
            memory.relate(other, RulerMemory.Reason.BLOCKED);
        }
        assertEquals(-RulerMemory.MAX_RELATION, memory.relation(other));
    }

    @Test
    void pendingDecisionsComeOutInTurnAndOverflowDropsTheOldest() {
        RulerMemory memory = new RulerMemory();
        for (int i = 0; i < RulerMemory.MAX_PENDING + 2; i++) {
            memory.addPending(List.of(RulerDecision.of(RulerDecision.Kind.CLAIM, "", i)));
        }
        assertEquals(RulerMemory.MAX_PENDING, memory.pending().size());
        assertEquals(2, memory.nextPending().orElseThrow().amount());
    }

    @Test
    void dealingsWithPlayersAreKeptWithinBounds() {
        RulerMemory memory = new RulerMemory();
        UUID player = UUID.randomUUID();
        memory.dealt(player, 150);
        assertEquals(RulerMemory.MAX_RELATION, memory.dealings(player));
        memory.dealt(player, -RulerMemory.MAX_RELATION);
        memory.fade();
        assertEquals(0, memory.dealings(player));
        assertFalse(memory.players().containsKey(player), "a forgotten player leaves no entry");
    }
}
