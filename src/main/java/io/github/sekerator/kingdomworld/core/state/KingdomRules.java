package io.github.sekerator.kingdomworld.core.state;

import io.github.sekerator.kingdomworld.core.territory.ChunkLimitPolicy;

import java.util.Objects;
import java.util.Set;

/**
 * Server settings that founding and claiming follow.
 *
 * @param allowedDimensions         dimension ids where kingdoms may be founded
 * @param minChunksFromOtherKingdom founding is refused if land of another kingdom lies within this many chunks
 *                                  (square distance) of the throne chunk
 * @param initialClaimRadius        a new kingdom gets the square of this radius around the throne chunk
 * @param claimBufferChunks         a claimed chunk must keep this distance to land of other kingdoms
 * @param chunkLimit                how many chunks a kingdom may own
 */
public record KingdomRules(Set<String> allowedDimensions, int minChunksFromOtherKingdom, int initialClaimRadius,
                           int claimBufferChunks, ChunkLimitPolicy chunkLimit) {
    public static final KingdomRules DEFAULT = new KingdomRules(Set.of("minecraft:overworld"), 4, 1, 1,
            ChunkLimitPolicy.perSubjectAndCitizen(25, 4, 2));

    public KingdomRules {
        allowedDimensions = Set.copyOf(allowedDimensions);
        Objects.requireNonNull(chunkLimit, "chunkLimit");
        if (minChunksFromOtherKingdom < 0 || initialClaimRadius < 0 || claimBufferChunks < 0) {
            throw new IllegalArgumentException("Distances must not be negative");
        }
    }

    public KingdomRules withChunkLimit(ChunkLimitPolicy policy) {
        return new KingdomRules(allowedDimensions, minChunksFromOtherKingdom, initialClaimRadius, claimBufferChunks, policy);
    }
}
