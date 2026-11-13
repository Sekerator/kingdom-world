package io.github.sekerator.kingdomworld.core.siege;

import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pace of the journal of conquests: a share of time common to a second, a cap for each step, and the claims of a loser
 * handed over a batch at a time with the capital chunk left for last.
 */
class ConquestPaceTest {
    /** A clock that moves only when told. */
    private static final class Clock {
        long now = 1_000;

        long read() {
            return now;
        }
    }

    @Test
    void aBatchStopsAtItsCapAndSaysSomethingIsLeft() {
        Clock clock = new Clock();
        ConquestPace pace = new ConquestPace(clock::read, 100);
        ConquestPace.Batch batch = pace.batch(3);
        int done = 0;
        for (int i = 0; i < 10; i++) {
            if (!batch.room()) {
                break;
            }
            batch.did();
            done++;
        }
        assertEquals(3, done);
        assertEquals(3, batch.done());
        assertTrue(batch.cut(), "seven things wait for the next second");
        assertFalse(batch.room(), "a cut batch stays cut");
    }

    @Test
    void aBatchThatRunsOutOfThingsIsNotCut() {
        Clock clock = new Clock();
        ConquestPace pace = new ConquestPace(clock::read, 100);
        ConquestPace.Batch batch = pace.batch(ConquestPace.BUILDINGS);
        for (int i = 0; i < 5; i++) {
            assertTrue(batch.room());
            batch.did();
        }
        assertEquals(5, batch.done());
        assertFalse(batch.cut(), "all five went: the step is done");
    }

    @Test
    void theTimeOfTheSecondIsSharedByEveryBatch() {
        Clock clock = new Clock();
        ConquestPace pace = new ConquestPace(clock::read, 100);
        ConquestPace.Batch first = pace.batch(1_000);
        int done = 0;
        while (first.room()) {
            first.did();
            done++;
            clock.now += 30;
        }
        assertEquals(4, done, "things at 0, 30, 60 and 90 nanoseconds; at 120 the time is up");
        assertTrue(first.cut());
        assertTrue(pace.spent());
        ConquestPace.Batch second = pace.batch(1_000);
        assertFalse(second.room(), "the next conquest of the second waits for the next second");
        assertTrue(second.cut());
        assertEquals(0, second.done());
    }

    @Test
    void theClockMayWrapAround() {
        Clock clock = new Clock();
        clock.now = Long.MAX_VALUE - 10;
        ConquestPace pace = new ConquestPace(clock::read, 100);
        assertFalse(pace.spent());
        clock.now += 50;
        assertFalse(pace.spent(), "half the time is gone though the clock went past its largest value");
        clock.now += 60;
        assertTrue(pace.spent());
    }

    @Test
    void theRealPaceHasEightMilliseconds() {
        assertEquals(8_000_000L, ConquestPace.BUDGET_NANOS);
        Clock clock = new Clock();
        ConquestPace pace = ConquestPace.start(clock::read);
        clock.now += ConquestPace.BUDGET_NANOS - 1;
        assertFalse(pace.spent());
        clock.now += 1;
        assertTrue(pace.spent());
        assertEquals(512, ConquestPace.CLAIMS);
        assertEquals(64, ConquestPace.BUILDINGS);
        assertEquals(16, ConquestPace.CITIZENS);
    }

    @Test
    void claimsGoABatchAtATimeTheCapitalLast() {
        long capital = ChunkKey.of(0, 0);
        Set<Long> claims = new LinkedHashSet<>();
        claims.add(capital);
        for (int x = 1; x <= 1_200; x++) {
            claims.add(ChunkKey.of(x, 0));
        }
        List<Long> handed = new ArrayList<>();
        int seconds = 0;
        while (true) {
            List<Long> next = ConquestPace.nextClaims(claims, capital, ConquestPace.CLAIMS, chunk -> true);
            assertTrue(next.size() <= ConquestPace.CLAIMS);
            assertFalse(next.contains(capital), "the capital chunk goes only with the kingdom");
            if (next.isEmpty()) {
                break;
            }
            // The world hands them over: they are no claims of the loser any more, so the next second finds the rest.
            claims.removeAll(next);
            handed.addAll(next);
            seconds++;
        }
        assertEquals(3, seconds, "1200 claims at 512 a second");
        assertEquals(1_200, handed.size());
        assertEquals(Set.of(capital), claims);
        assertEquals(ChunkKey.of(1, 0), handed.get(0), "in the order they were claimed");
    }

    @Test
    void onlyTheClaimsThatMatchAreHandedOver() {
        long capital = ChunkKey.of(0, 0);
        Set<Long> claims = new LinkedHashSet<>(List.of(ChunkKey.of(1, 0), capital, ChunkKey.of(2, 0), ChunkKey.of(3, 0), ChunkKey.of(4, 0)));
        List<Long> next = ConquestPace.nextClaims(claims, capital, 2, chunk -> ChunkKey.x(chunk) % 2 == 0);
        assertEquals(List.of(ChunkKey.of(2, 0), ChunkKey.of(4, 0)), next, "the capital matches too, yet stays");
        assertTrue(ConquestPace.nextClaims(claims, capital, 0, chunk -> true).isEmpty());
        assertTrue(ConquestPace.nextClaims(Set.of(capital), capital, 10, chunk -> true).isEmpty());
    }
}
