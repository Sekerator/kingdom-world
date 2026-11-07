package io.github.sekerator.kingdomworld.common.citizen.goal;

/**
 * Limits how many blocks the bodies of workers look at per server tick when they search their zones (trees, ore,
 * water, crops), so that a crowd of workers never makes one tick expensive: a worker that gets no budget this
 * tick goes on searching in the next one.
 */
public final class ScanBudget {
    /** Blocks all bodies together may look at in one tick. */
    public static final int BLOCKS_PER_TICK = 6_144;
    /** Blocks one worker looks at per tick at most. */
    public static final int PER_WORKER = 384;
    private static int used;

    private ScanBudget() {
    }

    /**
     * @return how many blocks the caller may look at now, at most {@code wanted}
     */
    public static int take(int wanted) {
        int granted = Math.max(0, Math.min(Math.min(wanted, PER_WORKER), BLOCKS_PER_TICK - used));
        used += granted;
        return granted;
    }

    /**
     * Called at the start of every server tick.
     */
    public static void reset() {
        used = 0;
    }
}
