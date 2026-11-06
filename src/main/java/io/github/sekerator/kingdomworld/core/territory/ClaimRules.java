package io.github.sekerator.kingdomworld.core.territory;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Game rules for claiming and abandoning chunks.
 *
 * <ul>
 *   <li>A kingdom's land lies in the dimension of its capital and stays connected: every chunk can be reached
 *       from the capital chunk through chunks that share a side.</li>
 *   <li>A new chunk must touch the kingdom's land and keep a gap of {@code bufferChunks} (square distance) to
 *       land of other kingdoms.</li>
 *   <li>A kingdom cannot own more chunks than its limit, and cannot give up its capital chunk.</li>
 * </ul>
 */
public final class ClaimRules {

    private ClaimRules() {
    }

    /**
     * @param maxChunks    the kingdom's chunk limit
     * @param bufferChunks minimum distance to land of other kingdoms; 0 lets borders touch
     * @return the reason the chunk cannot be claimed, or empty if it can
     */
    public static Optional<KingdomError> checkClaim(TerritoryIndex index, UUID kingdomId, String dimension, long chunk,
                                                    int maxChunks, int bufferChunks) {
        Optional<Territory> territory = index.territory(kingdomId);
        if (territory.isEmpty()) {
            return Optional.of(KingdomError.NO_CAPITAL);
        }
        if (!territory.get().dimension().equals(dimension)) {
            return Optional.of(KingdomError.WRONG_DIMENSION);
        }
        UUID owner = index.ownerOf(dimension, chunk);
        if (kingdomId.equals(owner)) {
            return Optional.of(KingdomError.ALREADY_OWNED);
        }
        if (owner != null) {
            return Optional.of(KingdomError.OWNED_BY_OTHER);
        }
        if (!index.touchesTerritory(kingdomId, chunk)) {
            return Optional.of(KingdomError.NOT_ADJACENT);
        }
        if (bufferChunks > 0 && index.hasForeignLandWithin(dimension, chunk, bufferChunks, kingdomId)) {
            return Optional.of(KingdomError.TOO_CLOSE_TO_OTHER_KINGDOM);
        }
        if (territory.get().size() >= maxChunks) {
            return Optional.of(KingdomError.LIMIT_REACHED);
        }
        return Optional.empty();
    }

    /**
     * @return the reason the chunk cannot be abandoned, or empty if it can
     */
    public static Optional<KingdomError> checkUnclaim(TerritoryIndex index, UUID kingdomId, String dimension, long chunk) {
        Optional<Territory> territory = index.territory(kingdomId);
        if (territory.isEmpty()) {
            return Optional.of(KingdomError.NO_CAPITAL);
        }
        if (!territory.get().dimension().equals(dimension)) {
            return Optional.of(KingdomError.WRONG_DIMENSION);
        }
        if (!territory.get().contains(chunk)) {
            return Optional.of(KingdomError.NOT_OWNED);
        }
        if (territory.get().capital().chunk() == chunk) {
            return Optional.of(KingdomError.CAPITAL_CHUNK);
        }
        if (!index.staysConnectedWithout(kingdomId, chunk)) {
            return Optional.of(KingdomError.WOULD_DISCONNECT);
        }
        return Optional.empty();
    }

    /**
     * The first chunks of a new kingdom: the square of {@code radius} around the capital chunk, without chunks
     * that are owned or too close to other kingdoms, and without chunks this cuts off from the capital.
     */
    public static Set<Long> initialClaim(TerritoryIndex index, String dimension, long capitalChunk, int radius, int bufferChunks) {
        Set<Long> candidates = new LinkedHashSet<>();
        int cx = ChunkKey.x(capitalChunk);
        int cz = ChunkKey.z(capitalChunk);
        int r = Math.max(0, radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long chunk = ChunkKey.of(cx + dx, cz + dz);
                if (index.ownerOf(dimension, chunk) != null) {
                    continue;
                }
                if (chunk != capitalChunk && bufferChunks > 0 && index.hasForeignLandWithin(dimension, chunk, bufferChunks, null)) {
                    continue;
                }
                candidates.add(chunk);
            }
        }
        Set<Long> connected = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        connected.add(capitalChunk);
        queue.add(capitalChunk);
        while (!queue.isEmpty()) {
            long current = queue.poll();
            for (long side : ChunkKey.sides(current)) {
                if (candidates.contains(side) && connected.add(side)) {
                    queue.add(side);
                }
            }
        }
        return connected;
    }
}
