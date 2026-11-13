package io.github.sekerator.kingdomworld.core.territory;

/**
 * How many chunks a kingdom may own. The default grows with the number of subjects and citizens; a later stage
 * replaces it with influence, which only needs a new implementation of this interface.
 */
@FunctionalInterface
public interface ChunkLimitPolicy {

    int maxChunks(KingdomStats stats);

    /**
     * {@code base} chunks plus {@code perSubject} for every player subject.
     */
    static ChunkLimitPolicy perSubject(int base, int perSubject) {
        return perSubjectAndCitizen(base, perSubject, 0);
    }

    /**
     * {@code base} chunks plus {@code perSubject} for every player subject and {@code perCitizen} for every
     * living citizen. Land a kingdom holds already stays its own when the limit falls below it.
     */
    static ChunkLimitPolicy perSubjectAndCitizen(int base, int perSubject, int perCitizen) {
        if (base < 0 || perSubject < 0 || perCitizen < 0) {
            throw new IllegalArgumentException("Chunk limits must not be negative: " + base + ", " + perSubject
                    + ", " + perCitizen);
        }
        return stats -> (int) Math.min(Integer.MAX_VALUE, base
                + (long) perSubject * Math.max(0, stats.playerSubjects())
                + (long) perCitizen * Math.max(0, stats.citizens()));
    }
}
