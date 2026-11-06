package io.github.sekerator.kingdomworld.core.territory;

/**
 * Packs chunk coordinates into one {@code long}: the low 32 bits hold x, the high 32 bits hold z. This is
 * the same layout the game uses for chunk positions, so keys can be exchanged with it directly.
 */
public final class ChunkKey {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private ChunkKey() {
    }

    public static long of(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((z & 0xFFFFFFFFL) << 32);
    }

    public static int x(long key) {
        return (int) key;
    }

    public static int z(long key) {
        return (int) (key >>> 32);
    }

    public static long offset(long key, int dx, int dz) {
        return of(x(key) + dx, z(key) + dz);
    }

    /**
     * Square (Chebyshev) distance in chunks: diagonal neighbours are 1 apart.
     */
    public static int distance(long a, long b) {
        return Math.max(Math.abs(x(a) - x(b)), Math.abs(z(a) - z(b)));
    }

    /**
     * The four chunks sharing a side with the given one.
     */
    public static long[] sides(long key) {
        long[] result = new long[SIDES.length];
        for (int i = 0; i < SIDES.length; i++) {
            result[i] = offset(key, SIDES[i][0], SIDES[i][1]);
        }
        return result;
    }

    public static String format(long key) {
        return "[" + x(key) + ", " + z(key) + "]";
    }
}
