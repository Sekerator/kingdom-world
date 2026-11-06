package io.github.sekerator.kingdomworld.core.territory;

import java.util.UUID;

/**
 * Notified after a chunk changes hands. Used to send map updates to players and by map integrations.
 */
@FunctionalInterface
public interface TerritoryListener {

    /**
     * @param dimension dimension id
     * @param chunk     the chunk as a {@link ChunkKey}
     * @param oldOwner  previous owner kingdom, or {@code null}
     * @param newOwner  new owner kingdom, or {@code null}
     */
    void onChunkChanged(String dimension, long chunk, UUID oldOwner, UUID newOwner);
}
