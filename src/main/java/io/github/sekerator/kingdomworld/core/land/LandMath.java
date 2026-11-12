package io.github.sekerator.kingdomworld.core.land;

/**
 * Small pieces the planners share: mixing of seeds, smooth noise, and the packing of the entries of a search.
 */
final class LandMath {
    /** Bits of the cell in a packed entry of a search. */
    static final int CELL_BITS = 24;
    /** Bits of the source in a packed entry of a search. */
    static final int SOURCE_BITS = 14;
    static final int MAX_SOURCES = 1 << SOURCE_BITS;
    /** The largest cost a packed entry holds. */
    static final int MAX_COST = (1 << (63 - CELL_BITS - SOURCE_BITS)) - 1;
    private static final long CELL_MASK = (1L << CELL_BITS) - 1;
    private static final long SOURCE_MASK = (1L << SOURCE_BITS) - 1;

    /** Steps to the four neighbours of a cell: x, then z. */
    static final int[] DX4 = {1, -1, 0, 0};
    static final int[] DZ4 = {0, 0, 1, -1};
    /** Steps to the eight neighbours of a cell, straight ones first. */
    static final int[] DX8 = {1, -1, 0, 0, 1, 1, -1, -1};
    static final int[] DZ8 = {0, 0, 1, -1, 1, -1, 1, -1};

    private LandMath() {
    }

    /**
     * An entry of a search, the least cost first, then the least source, then the least cell.
     */
    static long pack(int cost, int source, int cell) {
        return ((long) Math.min(cost, MAX_COST) << (CELL_BITS + SOURCE_BITS)) | ((long) source << CELL_BITS) | cell;
    }

    static int cost(long entry) {
        return (int) (entry >>> (CELL_BITS + SOURCE_BITS));
    }

    static int source(long entry) {
        return (int) ((entry >>> CELL_BITS) & SOURCE_MASK);
    }

    static int cell(long entry) {
        return (int) (entry & CELL_MASK);
    }

    /**
     * Mixes a seed with a value: the finalizer of SplitMix64 over both.
     */
    static long mix(long seed, long value) {
        long z = seed + 0x9E3779B97F4A7C15L * (value + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static long mix(long seed, long a, long b) {
        return mix(mix(seed, a), b);
    }

    /**
     * A number from 0 (inclusive) to 1 (exclusive) out of a mixed value.
     */
    static double unit(long mixed) {
        return (mixed >>> 11) * 0x1.0p-53;
    }

    /**
     * Smooth noise from -1 to 1 over the plane: values at the corners of a lattice of {@code step} blocks, blended with the
     * smoothstep between them. Its slope is at most 3 / step.
     */
    static double noise(long seed, double x, double z, int step) {
        double gx = x / step;
        double gz = z / step;
        long ix = (long) Math.floor(gx);
        long iz = (long) Math.floor(gz);
        double fx = smooth(gx - ix);
        double fz = smooth(gz - iz);
        double a = corner(seed, ix, iz);
        double b = corner(seed, ix + 1, iz);
        double c = corner(seed, ix, iz + 1);
        double d = corner(seed, ix + 1, iz + 1);
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    private static double corner(long seed, long ix, long iz) {
        return unit(mix(seed, ix, iz)) * 2 - 1;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}
