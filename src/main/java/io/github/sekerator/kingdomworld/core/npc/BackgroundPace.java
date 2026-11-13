package io.github.sekerator.kingdomworld.core.npc;

/**
 * Whether the background appearance of a kingdom of the world works in a tick, by how long the tick took before the work came
 * to it. A tick that was long already is left to the rest of the server, but never for good: on a server whose game alone
 * takes longer than that every tick, the appearance still goes on with a small step every {@link #MAX_SKIPPED} ticks.
 */
public final class BackgroundPace {
    /** A tick that took this long before the background came to it is left to the rest of the server. */
    public static final long LATE_NANOS = 20_000_000L;
    /** Ticks in a row the background leaves at most; the next one gets a small step whatever it took. */
    public static final int MAX_SKIPPED = 20;
    /** Ticks of a minute. */
    private static final int MINUTE = 1200;

    /**
     * What the background does in a tick.
     */
    public enum Step {
        /** The step as the budget of the tick allows. */
        FULL,
        /** A small step: the ticks before it were all long. */
        SMALL,
        /** Nothing: the tick took long already. */
        SKIP
    }

    private int skipped;
    private int ticks;
    private int lateInMinute;
    private int smallInMinute;

    /**
     * Decides about this tick.
     *
     * @param sinceTickStart how long the tick took before the background came to it, in nanoseconds
     */
    public Step decide(long sinceTickStart) {
        ticks++;
        if (sinceTickStart <= LATE_NANOS) {
            skipped = 0;
            return Step.FULL;
        }
        lateInMinute++;
        if (++skipped >= MAX_SKIPPED) {
            skipped = 0;
            smallInMinute++;
            return Step.SMALL;
        }
        return Step.SKIP;
    }

    /**
     * Once a minute of ticks has passed: how many of them were long and how many small steps were made, or {@code null} when
     * fewer than a minute passed or no tick of it was long. The counts start again.
     */
    public int[] minuteOver() {
        if (ticks < MINUTE) {
            return null;
        }
        int[] counts = lateInMinute > 0 ? new int[]{lateInMinute, smallInMinute, ticks} : null;
        ticks = 0;
        lateInMinute = 0;
        smallInMinute = 0;
        return counts;
    }
}
