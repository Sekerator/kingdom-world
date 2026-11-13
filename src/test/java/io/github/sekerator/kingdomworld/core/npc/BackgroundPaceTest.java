package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BackgroundPaceTest {
    private static final long SHORT = 5_000_000L;
    private static final long LONG = 30_000_000L;

    @Test
    void aShortTickGetsAFullStep() {
        BackgroundPace pace = new BackgroundPace();
        for (int i = 0; i < 100; i++) {
            assertEquals(BackgroundPace.Step.FULL, pace.decide(SHORT));
        }
    }

    @Test
    void aServerWhoseTicksAreAllLongStillGetsAStepEveryFewTicks() {
        BackgroundPace pace = new BackgroundPace();
        int small = 0;
        int run = 0;
        int longestRun = 0;
        for (int i = 0; i < 1200; i++) {
            BackgroundPace.Step step = pace.decide(LONG);
            if (step == BackgroundPace.Step.SMALL) {
                small++;
                run = 0;
            } else {
                assertEquals(BackgroundPace.Step.SKIP, step);
                longestRun = Math.max(longestRun, ++run);
            }
        }
        assertEquals(1200 / BackgroundPace.MAX_SKIPPED, small);
        assertEquals(BackgroundPace.MAX_SKIPPED - 1, longestRun);
    }

    @Test
    void aShortTickStartsTheCountAgain() {
        BackgroundPace pace = new BackgroundPace();
        for (int i = 0; i < BackgroundPace.MAX_SKIPPED - 1; i++) {
            assertEquals(BackgroundPace.Step.SKIP, pace.decide(LONG));
        }
        assertEquals(BackgroundPace.Step.FULL, pace.decide(SHORT));
        for (int i = 0; i < BackgroundPace.MAX_SKIPPED - 1; i++) {
            assertEquals(BackgroundPace.Step.SKIP, pace.decide(LONG));
        }
        assertEquals(BackgroundPace.Step.SMALL, pace.decide(LONG));
    }

    @Test
    void aMinuteWithLongTicksIsToldOnceAndAQuietOneIsNot() {
        BackgroundPace pace = new BackgroundPace();
        for (int i = 0; i < 1199; i++) {
            pace.decide(i < 40 ? LONG : SHORT);
        }
        assertNull(pace.minuteOver());
        pace.decide(SHORT);
        assertArrayEquals(new int[]{40, 2, 1200}, pace.minuteOver());
        assertNull(pace.minuteOver());
        for (int i = 0; i < 1200; i++) {
            pace.decide(SHORT);
        }
        assertNull(pace.minuteOver());
    }
}
