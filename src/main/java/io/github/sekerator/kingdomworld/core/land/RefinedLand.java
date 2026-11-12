package io.github.sekerator.kingdomworld.core.land;

import java.util.Arrays;

/**
 * The provinces chunk by chunk, in tiles of {@value #TILE} by {@value #TILE} chunks (the chunks of a region file). A tile is
 * computed when asked, the same every time; the work done once (the borders through the probed water) is done already.
 * Immutable: tiles may be asked from any thread.
 *
 * <p>A tile is a {@code char[1024]} of the numbers of the provinces (0 is none), chunk {@code (x, z)} at
 * {@code (z & 31) * 32 + (x & 31)}.
 */
public final class RefinedLand {
    /** Chunks across a tile. */
    public static final int TILE = 32;

    private final BorderRefiner.Work work;
    private final long[] tiles;

    RefinedLand(BorderRefiner.Work work, long[] tiles) {
        this.work = work;
        this.tiles = tiles;
    }

    /**
     * The key of a tile.
     */
    public static long tileKey(int tileX, int tileZ) {
        return ((long) tileX << 32) | (tileZ & 0xFFFFFFFFL);
    }

    public static int tileX(long key) {
        return (int) (key >> 32);
    }

    public static int tileZ(long key) {
        return (int) key;
    }

    /**
     * The place of a chunk in its tile.
     */
    public static int indexInTile(int chunkX, int chunkZ) {
        return ((chunkZ & (TILE - 1)) << 5) | (chunkX & (TILE - 1));
    }

    /**
     * The tiles that may hold a province, in order of their keys.
     */
    public long[] tileKeys() {
        return tiles.clone();
    }

    public int tileCount() {
        return tiles.length;
    }

    /**
     * The province of a chunk, or 0.
     */
    public int provinceAt(int chunkX, int chunkZ) {
        return work.provinceAt(chunkX, chunkZ);
    }

    /**
     * The provinces of the chunks of a tile.
     */
    public char[] tile(int tileX, int tileZ) {
        char[] ids = new char[TILE * TILE];
        int baseX = tileX * TILE;
        int baseZ = tileZ * TILE;
        for (int z = 0; z < TILE; z++) {
            for (int x = 0; x < TILE; x++) {
                ids[(z << 5) | x] = (char) work.provinceAt(baseX + x, baseZ + z);
            }
        }
        return ids;
    }

    /**
     * Whether a tile holds no province at all.
     */
    public static boolean empty(char[] tile) {
        for (char id : tile) {
            if (id != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "RefinedLand" + Arrays.toString(new int[]{tiles.length});
    }
}
