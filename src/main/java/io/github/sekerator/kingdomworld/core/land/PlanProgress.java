package io.github.sekerator.kingdomworld.core.land;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Where the plan of the states of a world stands: its stage, the place reached in the stage and the time each stage took. The
 * plan goes through its stages in order, each one resumable from its place after a restart; nothing done before the place is
 * done again.
 *
 * <p>The time of a stage is counted twice: the time of the server thread spent on it, and the time it lasted (the time
 * between the ticks it ran in, a pause of the server not counted).
 */
public final class PlanProgress {
    /**
     * The stages of the plan, in order.
     */
    public enum Stage {
        /** The land is looked at cell by cell. */
        SURVEY,
        /** The land generated and the land of players are marked. */
        GUARD,
        /** The states are laid out, on a worker thread. */
        STATES,
        /** The thrones are judged in the world, candidate by candidate. */
        THRONES,
        /** The provinces are laid out, on a worker thread. */
        PROVINCES,
        /** The borders through water are drawn by the chunks probed there. */
        REFINE,
        /** The provinces are written into the map of the land, tile by tile. */
        RASTER,
        /** The towns and villages of the states (later stages). */
        SETTLE,
        /** The roads between them (later stages). */
        ROADS,
        /** The states are founded and given their provinces, one a tick. */
        FOUND,
        /** The plan is made. */
        DONE;

        private static final Stage[] VALUES = values();

        /**
         * Whether the stage runs on a worker thread, the server only waiting for it.
         */
        public boolean onWorker() {
            return this == STATES || this == PROVINCES;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Stage next() {
            return this == DONE ? DONE : VALUES[ordinal() + 1];
        }

        public static Stage byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : SURVEY;
        }
    }

    /** The longest pause between two ticks of the plan counted in the time a stage lasted, in milliseconds. */
    public static final long MAX_PAUSE_MILLIS = 1_000;

    private static final int STAGES = Stage.VALUES.length;

    private Stage stage = Stage.SURVEY;
    private int cursor;
    private final long[] workNanos = new long[STAGES];
    private final long[] wallMillis = new long[STAGES];
    private final int[] ticks = new int[STAGES];
    private long worstTickNanos;
    private Stage worstStage = Stage.SURVEY;
    private long worstWorkNanos;
    private Stage worstWorkStage = Stage.SURVEY;
    private int[] thrones = new int[0];
    private long lastTickMillis = -1;

    public Stage stage() {
        return stage;
    }

    public boolean done() {
        return stage == Stage.DONE;
    }

    /**
     * The place reached in the stage: what comes before it is done.
     */
    public int cursor() {
        return cursor;
    }

    /**
     * Moves the place reached in the stage on; it never goes back.
     */
    public void moveTo(int cursor) {
        if (cursor < this.cursor) {
            throw new IllegalArgumentException("The place of a stage goes back: " + this.cursor + " -> " + cursor);
        }
        this.cursor = cursor;
    }

    /**
     * Ends a stage: the plan goes to the next one, from its start.
     */
    public void finish(Stage stage) {
        if (stage != this.stage || stage == Stage.DONE) {
            throw new IllegalStateException("The stage " + stage + " ends while the plan is at " + this.stage);
        }
        this.stage = stage.next();
        this.cursor = 0;
    }

    /**
     * The cells of the thrones of the states as the server chose them, -1 for a state it found none for; empty before.
     */
    public int[] thrones() {
        return thrones.clone();
    }

    public void setThrones(int[] thrones) {
        this.thrones = thrones.clone();
    }

    /**
     * Counts a tick of the plan spent on one stage.
     *
     * @param stage      the stage the time went to
     * @param nanos      the time of the server thread spent on the plan in the tick
     * @param nowMillis  the time of the tick
     */
    public void count(Stage stage, long nanos, long nowMillis) {
        work(stage, nanos);
        lasted(stage, nowMillis);
        worst(nanos, stage);
    }

    private void work(Stage stage, long nanos) {
        workNanos[stage.ordinal()] += nanos;
        ticks[stage.ordinal()]++;
    }

    /**
     * The time since the tick before goes to the stage the plan is at after the tick.
     */
    private void lasted(Stage stage, long nowMillis) {
        if (lastTickMillis >= 0 && nowMillis >= lastTickMillis) {
            wallMillis[stage.ordinal()] += Math.min(MAX_PAUSE_MILLIS, nowMillis - lastTickMillis);
        }
        lastTickMillis = nowMillis;
    }

    private void worst(long nanos, Stage stage) {
        if (nanos > worstTickNanos) {
            worstTickNanos = nanos;
            worstStage = stage;
        }
        if (nanos > worstWorkNanos) {
            worstWorkNanos = nanos;
            worstWorkStage = stage;
        }
    }

    /**
     * The worst tick of the plan without the pauses of the whole process within it (the collector of garbage stops every
     * thread), and its stage: what the plan itself took.
     */
    public long worstWorkNanos() {
        return worstWorkNanos;
    }

    public Stage worstWorkStage() {
        return worstWorkStage;
    }

    public long workNanos(Stage stage) {
        return workNanos[stage.ordinal()];
    }

    public long wallMillis(Stage stage) {
        return wallMillis[stage.ordinal()];
    }

    public int ticks(Stage stage) {
        return ticks[stage.ordinal()];
    }

    public long worstTickNanos() {
        return worstTickNanos;
    }

    public Stage worstStage() {
        return worstStage;
    }

    /**
     * The time the whole plan lasted so far, in milliseconds.
     */
    public long totalWallMillis() {
        long total = 0;
        for (long value : wallMillis) {
            total += value;
        }
        return total;
    }

    /**
     * The numbers of the progress to keep: the stage, the place, the worst tick and its stage, then for every stage its server
     * time, the time it lasted and its ticks, then the worst tick without the pauses of the process and its stage.
     */
    public long[] numbers() {
        long[] numbers = new long[6 + 3 * STAGES];
        numbers[0] = stage.ordinal();
        numbers[1] = cursor;
        numbers[2] = worstTickNanos;
        numbers[3] = worstStage.ordinal();
        for (int i = 0; i < STAGES; i++) {
            numbers[4 + 3 * i] = workNanos[i];
            numbers[5 + 3 * i] = wallMillis[i];
            numbers[6 + 3 * i] = ticks[i];
        }
        numbers[4 + 3 * STAGES] = worstWorkNanos;
        numbers[5 + 3 * STAGES] = worstWorkStage.ordinal();
        return numbers;
    }

    /**
     * The progress kept as {@link #numbers()} and {@link #thrones()}; numbers of another length (a stage added later) are read as
     * far as they go.
     */
    public static PlanProgress restore(long[] numbers, int[] thrones) {
        PlanProgress progress = new PlanProgress();
        if (numbers.length >= 4) {
            progress.stage = Stage.byId((int) numbers[0]);
            progress.cursor = (int) Math.max(0, numbers[1]);
            progress.worstTickNanos = numbers[2];
            progress.worstStage = Stage.byId((int) numbers[3]);
        }
        for (int i = 0; i < STAGES && 6 + 3 * i < numbers.length; i++) {
            progress.workNanos[i] = numbers[4 + 3 * i];
            progress.wallMillis[i] = numbers[5 + 3 * i];
            progress.ticks[i] = (int) numbers[6 + 3 * i];
        }
        if (numbers.length >= 6 + 3 * STAGES) {
            progress.worstWorkNanos = numbers[4 + 3 * STAGES];
            progress.worstWorkStage = Stage.byId((int) numbers[5 + 3 * STAGES]);
        } else {
            progress.worstWorkNanos = progress.worstTickNanos;
            progress.worstWorkStage = progress.worstStage;
        }
        progress.thrones = thrones == null ? new int[0] : thrones.clone();
        return progress;
    }

    /**
     * The work of the stages of a plan.
     */
    public interface Work {
        /**
         * Does the work of a stage from the place reached, moving the place on, until the stage is done or the time is up.
         *
         * @return whether the stage is done
         */
        boolean run(Stage stage, PlanProgress progress, long deadlineNanos);
    }

    /**
     * Runs the plan for one tick: stage after stage while there is time, stopping at a stage that is not done (its time is up, or
     * it waits for a worker thread). Every stage that ran counts the tick and its time; the worst tick is put down to the stage
     * that took most of it.
     *
     * @return whether the plan is made
     */
    public boolean tick(Work work, LongSupplier nanoClock, long budgetNanos, long nowMillis) {
        return tick(work, nanoClock, () -> 0L, budgetNanos, nowMillis);
    }

    /**
     * Runs the plan for one tick as {@link #tick(Work, LongSupplier, long, long)}, also counting the worst tick without the pauses
     * of the whole process within it.
     *
     * @param pauseNanos the time the whole process was paused so far (the collector of garbage), in nanoseconds
     */
    public boolean tick(Work work, LongSupplier nanoClock, LongSupplier pauseNanos, long budgetNanos, long nowMillis) {
        if (done()) {
            return true;
        }
        long paused = pauseNanos.getAsLong();
        long start = nanoClock.getAsLong();
        long deadline = start + budgetNanos;
        Stage longest = stage;
        long longestNanos = -1;
        while (!done()) {
            Stage running = stage;
            long before = nanoClock.getAsLong();
            boolean finished = work.run(running, this, deadline);
            long after = nanoClock.getAsLong();
            work(running, after - before);
            if (after - before > longestNanos) {
                longestNanos = after - before;
                longest = running;
            }
            if (!finished) {
                break;
            }
            finish(running);
            if (after - deadline >= 0) {
                break;
            }
        }
        lasted(stage, nowMillis);
        long took = nanoClock.getAsLong() - start;
        if (took > worstTickNanos) {
            worstTickNanos = took;
            worstStage = longest;
        }
        long own = Math.max(0, took - Math.max(0, pauseNanos.getAsLong() - paused));
        if (own > worstWorkNanos) {
            worstWorkNanos = own;
            worstWorkStage = longest;
        }
        return done();
    }

    @Override
    public String toString() {
        return "PlanProgress[" + stage.key() + " at " + cursor + ", thrones " + Arrays.toString(thrones) + "]";
    }
}
