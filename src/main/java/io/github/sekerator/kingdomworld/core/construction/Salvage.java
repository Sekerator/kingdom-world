package io.github.sekerator.kingdomworld.core.construction;

/**
 * What a demolition gives back: the items of the blocks taken down, less a share lost on the way. The loss is counted over
 * all blocks of a kind together, so that a building taken down block by block gives back exactly what it would give in one
 * go, and nothing is ever given back twice.
 */
public final class Salvage {

    private Salvage() {
    }

    /**
     * Items given back in all for so many items taken down, rounded down.
     *
     * @param lossPercent share lost, from 0 to 100
     */
    public static int total(long removed, int lossPercent) {
        int loss = Math.max(0, Math.min(100, lossPercent));
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, removed) * (100 - loss) / 100);
    }

    /**
     * Items given back when the items taken down grow from {@code before} to {@code after}.
     */
    public static int returned(long before, long after, int lossPercent) {
        return Math.max(0, total(after, lossPercent) - total(before, lossPercent));
    }
}
