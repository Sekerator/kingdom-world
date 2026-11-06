package io.github.sekerator.kingdomworld.common.citizen.goal;

/**
 * Limits how many paths citizen bodies compute per server tick, so that a crowd of workers does not make one tick
 * expensive: a body that gets no path this tick simply tries again later.
 */
public final class PathBudget {
    /** Paths all bodies together may compute in one tick. */
    public static final int PATHS_PER_TICK = 12;
    private static int used;

    private PathBudget() {
    }

    /**
     * @return whether the caller may compute a path now
     */
    public static boolean tryAcquire() {
        if (used >= PATHS_PER_TICK) {
            return false;
        }
        used++;
        return true;
    }

    /**
     * Called at the start of every server tick.
     */
    public static void reset() {
        used = 0;
    }
}
