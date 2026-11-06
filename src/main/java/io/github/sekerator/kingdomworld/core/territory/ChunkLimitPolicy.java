package io.github.sekerator.kingdomworld.core.territory;

/**
 * How many chunks a kingdom may own. The default grows with the number of subjects; a later stage replaces
 * it with influence, which only needs a new implementation of this interface.
 */
@FunctionalInterface
public interface ChunkLimitPolicy {

    int maxChunks(KingdomStats stats);

    /**
     * {@code base} chunks plus {@code perSubject} for every player subject.
     */
    static ChunkLimitPolicy perSubject(int base, int perSubject) {
        if (base < 0 || perSubject < 0) {
            throw new IllegalArgumentException("Chunk limits must not be negative: " + base + ", " + perSubject);
        }
        return stats -> (int) Math.min(Integer.MAX_VALUE, base + (long) perSubject * stats.playerSubjects());
    }
}
