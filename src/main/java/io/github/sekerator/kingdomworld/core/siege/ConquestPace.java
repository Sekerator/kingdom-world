package io.github.sekerator.kingdomworld.core.siege;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;

/**
 * How much of the journal of conquests one second carries out, so that handing over a whole state never makes a long tick: a
 * share of time common to every conquest of the second, and for each step no more than so many things at once. What is left
 * waits for the next second. Nothing about it is saved: the next second finds what is left in the world again (a province
 * still of the loser, a building of the loser still in the area), so a restart in the middle neither loses nor doubles anything.
 */
public final class ConquestPace {
    /** Nanoseconds one second of the journal may take, all its conquests together. */
    public static final long BUDGET_NANOS = 8_000_000L;
    /** Claimed chunks of the loser an annexation hands over in a second. */
    public static final int CLAIMS = 512;
    /** Buildings a conquest hands over in a second. */
    public static final int BUILDINGS = 64;
    /** Citizens a conquest hands over in a second. */
    public static final int CITIZENS = 16;

    private final LongSupplier clock;
    private final long deadline;

    /**
     * @param clock  the time in nanoseconds, {@link System#nanoTime()} in the world
     * @param budget nanoseconds from now the second may take
     */
    public ConquestPace(LongSupplier clock, long budget) {
        this.clock = clock;
        this.deadline = clock.getAsLong() + Math.max(0, budget);
    }

    /**
     * The pace of a second of the journal starting now, with the share of time of {@link #BUDGET_NANOS}.
     */
    public static ConquestPace start(LongSupplier clock) {
        return new ConquestPace(clock, BUDGET_NANOS);
    }

    /**
     * Whether the share of time of the second is used up.
     */
    public boolean spent() {
        return clock.getAsLong() - deadline >= 0;
    }

    /**
     * A batch of a step: at most {@code most} things within the time left.
     */
    public Batch batch(int most) {
        return new Batch(this, most);
    }

    /**
     * Things done one at a time by a step until it runs out of room: the most it may do in a second, or the time of the second.
     * Ask {@link #room()} before each thing and tell {@link #did()} after it.
     */
    public static final class Batch {
        private final ConquestPace pace;
        private final int most;
        private int done;
        private boolean cut;

        private Batch(ConquestPace pace, int most) {
            this.pace = pace;
            this.most = Math.max(0, most);
        }

        /**
         * Whether one more thing may be done now. Once it says no the batch is cut: the step has things left for the next second.
         */
        public boolean room() {
            if (cut || done >= most || pace.spent()) {
                cut = true;
                return false;
            }
            return true;
        }

        public void did() {
            done++;
        }

        /**
         * How many things were done.
         */
        public int done() {
            return done;
        }

        /**
         * Whether the batch stopped before everything that was there was done.
         */
        public boolean cut() {
            return cut;
        }
    }

    /**
     * The next claimed chunks of a loser to hand over, in the order they were claimed: at most {@code most} of those that
     * match, never the capital chunk, which goes only with the kingdom.
     *
     * @param claims  the claimed chunks of the loser
     * @param capital its capital chunk
     * @param which   which chunks are handed over at all
     */
    public static List<Long> nextClaims(Collection<Long> claims, long capital, int most, LongPredicate which) {
        List<Long> next = new ArrayList<>(Math.min(Math.max(0, most), claims.size()));
        if (most <= 0) {
            return next;
        }
        for (long chunk : claims) {
            if (chunk == capital || !which.test(chunk)) {
                continue;
            }
            next.add(chunk);
            if (next.size() >= most) {
                break;
            }
        }
        return next;
    }
}
