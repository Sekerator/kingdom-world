package io.github.sekerator.kingdomworld.core.road;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The land a plan of a road has read: tiles of chunks, the last {@value #KEPT} used kept, with the snapshot of what stands on it
 * laid over each column. Safe to use from several threads.
 */
public final class LandGrid implements RoadSurvey.Land {
    /** Tiles kept at most: about five megabytes. */
    public static final int KEPT = 1_024;

    private final LandSnapshot snapshot;
    private final Map<Long, LandTile> tiles = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, LandTile> eldest) {
            return size() > KEPT;
        }
    };

    public LandGrid(LandSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public LandSnapshot snapshot() {
        return snapshot;
    }

    public synchronized void put(LandTile tile) {
        tiles.put(LandSnapshot.chunk(tile.chunkX(), tile.chunkZ()), tile);
    }

    public synchronized LandTile tile(int chunkX, int chunkZ) {
        return tiles.get(LandSnapshot.chunk(chunkX, chunkZ));
    }

    public synchronized boolean has(int chunkX, int chunkZ) {
        return tiles.containsKey(LandSnapshot.chunk(chunkX, chunkZ));
    }

    public synchronized int size() {
        return tiles.size();
    }

    /**
     * The column at a block with what stands on it, or {@code null} when its chunk is not read.
     */
    @Override
    public LandColumn at(int x, int z) {
        if (!snapshot.withinBorder(x, z)) {
            return new LandColumn(snapshot.minY(), LandColumn.NONE, 0, 0, LandColumn.BORDER, LandColumn.NONE, snapshot.minY(), LandColumn.NONE,
                    LandColumn.NONE, false);
        }
        LandTile tile = tile(x >> 4, z >> 4);
        return tile == null ? null : snapshot.overlay(tile.column(x, z), x, z);
    }

    /**
     * The kingdom whose land a block is, {@code null} for nobody's.
     */
    public UUID owner(int x, int z) {
        return snapshot.owner(x >> 4, z >> 4);
    }
}
