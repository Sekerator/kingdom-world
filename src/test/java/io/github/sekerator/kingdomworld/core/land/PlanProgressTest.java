package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.land.PlanProgress.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanProgressTest {
    private static final long MS = 1_000_000L;
    private static final long BUDGET = 8 * MS;

    /** How many pieces of work each stage has; a stage on a worker waits for it a few ticks instead. */
    private static final Map<Stage, Integer> UNITS = new EnumMap<>(Stage.class);

    static {
        UNITS.put(Stage.SURVEY, 50);
        UNITS.put(Stage.GUARD, 3);
        UNITS.put(Stage.STATES, 0);
        UNITS.put(Stage.THRONES, 9);
        UNITS.put(Stage.PROVINCES, 0);
        UNITS.put(Stage.REFINE, 20);
        UNITS.put(Stage.RASTER, 30);
        UNITS.put(Stage.SETTLE, 0);
        UNITS.put(Stage.ROADS, 0);
        UNITS.put(Stage.FOUND, 4);
    }

    /**
     * A plan of pieces of work, each costing a millisecond of a clock; the stages on a worker are ready three ticks after they
     * are asked for, and what they make lives only in memory, as the plans of the states and provinces do.
     */
    private static final class FakeWork implements PlanProgress.Work {
        final long[] clock;
        final List<String> done;
        int statesAsked = -1;
        boolean statesReady;
        int provincesAsked = -1;
        boolean provincesReady;
        int computed;
        int tick;

        FakeWork(long[] clock, List<String> done) {
            this.clock = clock;
            this.done = done;
        }

        @Override
        public boolean run(Stage stage, PlanProgress progress, long deadlineNanos) {
            if (stage == Stage.STATES || (stage.ordinal() > Stage.STATES.ordinal() && !statesReady)) {
                if (!worker(true)) {
                    return false;
                }
                if (stage == Stage.STATES) {
                    done.add("states");
                    return true;
                }
            }
            if (stage == Stage.PROVINCES || (stage.ordinal() > Stage.PROVINCES.ordinal() && !provincesReady)) {
                if (!worker(false)) {
                    return false;
                }
                if (stage == Stage.PROVINCES) {
                    done.add("provinces");
                    return true;
                }
            }
            int units = UNITS.get(stage);
            while (progress.cursor() < units) {
                if (clock[0] - deadlineNanos >= 0) {
                    return false;
                }
                done.add(stage.key() + " " + progress.cursor());
                clock[0] += MS;
                progress.moveTo(progress.cursor() + 1);
            }
            if (stage == Stage.THRONES) {
                progress.setThrones(new int[]{7, -1, 3});
            }
            return true;
        }

        private boolean worker(boolean states) {
            if (states) {
                if (statesAsked < 0) {
                    statesAsked = tick;
                    computed++;
                }
                statesReady = tick - statesAsked >= 3;
                return statesReady;
            }
            if (provincesAsked < 0) {
                provincesAsked = tick;
                computed++;
            }
            provincesReady = tick - provincesAsked >= 3;
            return provincesReady;
        }
    }

    /**
     * Runs a plan to its end, restarting it (keeping only what a save keeps) after the ticks a test asks for.
     */
    private static List<String> run(java.util.function.BiPredicate<Integer, PlanProgress> restartAfter) {
        long[] clock = {0};
        List<String> done = new ArrayList<>();
        PlanProgress progress = new PlanProgress();
        FakeWork work = new FakeWork(clock, done);
        for (int tick = 0; tick < 10_000 && !progress.done(); tick++) {
            work.tick = tick;
            progress.tick(work, () -> clock[0], BUDGET, tick * 50L);
            clock[0] += 50 * MS;
            if (restartAfter.test(tick, progress)) {
                progress = PlanProgress.restore(progress.numbers(), progress.thrones());
                work = new FakeWork(clock, done);
            }
        }
        assertTrue(progress.done(), "the plan ends");
        return done;
    }

    private static List<String> straight() {
        return run((tick, progress) -> false);
    }

    @Test
    void thePlanGoesThroughEveryStageOnceInOrder() {
        List<String> done = straight();
        List<String> expected = new ArrayList<>();
        for (Stage stage : Stage.values()) {
            if (stage == Stage.DONE) {
                continue;
            }
            if (stage == Stage.STATES) {
                expected.add("states");
            } else if (stage == Stage.PROVINCES) {
                expected.add("provinces");
            } else {
                for (int i = 0; i < UNITS.get(stage); i++) {
                    expected.add(stage.key() + " " + i);
                }
            }
        }
        assertEquals(expected, done);
    }

    @Test
    void aRestartInEveryStageGoesOnWithoutRepeatingWork() {
        List<String> expected = straight();
        for (Stage stage : Stage.values()) {
            if (stage == Stage.DONE) {
                continue;
            }
            for (int at = 0; at < 3; at++) {
                int wanted = at;
                int[] seen = {0};
                boolean[] restarted = {false};
                List<String> done = run((tick, progress) -> {
                    if (restarted[0] || progress.stage() != stage) {
                        return false;
                    }
                    if (seen[0]++ < wanted) {
                        return false;
                    }
                    restarted[0] = true;
                    return true;
                });
                assertEquals(expected, done, "a restart in " + stage + " after " + at + " ticks");
            }
        }
        // A restart every few ticks (each worker needs three ticks without one to be done, and both are made again after it).
        assertEquals(expected, run((tick, progress) -> tick % 13 == 12));
    }

    @Test
    void noTickGoesFarPastItsBudget() {
        long[] clock = {0};
        PlanProgress progress = new PlanProgress();
        FakeWork work = new FakeWork(clock, new ArrayList<>());
        for (int tick = 0; tick < 1000 && !progress.done(); tick++) {
            work.tick = tick;
            long before = clock[0];
            progress.tick(work, () -> clock[0], BUDGET, tick * 50L);
            assertTrue(clock[0] - before <= BUDGET + MS, "a tick took " + (clock[0] - before));
            clock[0] += 50 * MS;
        }
        assertTrue(progress.done());
        assertTrue(progress.worstTickNanos() <= BUDGET + MS);
        assertTrue(progress.ticks(Stage.SURVEY) >= 50 / 8, "the survey took several ticks");
        assertTrue(progress.wallMillis(Stage.SURVEY) > 0);
        assertTrue(progress.totalWallMillis() > 0);
    }

    @Test
    void theProgressIsKeptAndRead() {
        PlanProgress progress = new PlanProgress();
        progress.finish(Stage.SURVEY);
        progress.finish(Stage.GUARD);
        progress.finish(Stage.STATES);
        progress.moveTo(5);
        progress.setThrones(new int[]{4, -1});
        progress.count(Stage.THRONES, 3 * MS, 100);
        progress.count(Stage.THRONES, 2 * MS, 150);
        progress.count(Stage.THRONES, MS, 5_000);
        PlanProgress read = PlanProgress.restore(progress.numbers(), progress.thrones());
        assertEquals(Stage.THRONES, read.stage());
        assertEquals(5, read.cursor());
        assertArrayEquals(new int[]{4, -1}, read.thrones());
        assertEquals(6 * MS, read.workNanos(Stage.THRONES));
        assertEquals(3, read.ticks(Stage.THRONES));
        assertEquals(50 + PlanProgress.MAX_PAUSE_MILLIS, read.wallMillis(Stage.THRONES), "a long pause counts as a second");
        assertEquals(3 * MS, read.worstTickNanos());
        assertEquals(Stage.THRONES, read.worstStage());
        // Numbers kept before a stage was added are read as far as they go.
        long[] shorter = java.util.Arrays.copyOf(progress.numbers(), 10);
        assertEquals(Stage.THRONES, PlanProgress.restore(shorter, null).stage());
    }

    @Test
    void theStagesGoOnlyForward() {
        PlanProgress progress = new PlanProgress();
        progress.moveTo(3);
        assertThrows(IllegalArgumentException.class, () -> progress.moveTo(2));
        assertThrows(IllegalStateException.class, () -> progress.finish(Stage.GUARD));
        progress.finish(Stage.SURVEY);
        assertEquals(0, progress.cursor());
        assertEquals(Stage.GUARD, progress.stage());
        assertFalse(progress.done());
        assertTrue(Stage.STATES.onWorker());
        assertTrue(Stage.PROVINCES.onWorker());
        assertFalse(Stage.RASTER.onWorker());
        assertEquals(Stage.DONE, Stage.DONE.next());
    }
}
