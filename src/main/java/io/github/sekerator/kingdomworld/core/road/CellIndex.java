package io.github.sekerator.kingdomworld.core.road;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Things of the world sorted into square cells of their dimension, for questions about one place that should not look at
 * every thing: the things whose square meets the cell of the place, in the order they were first added. The answer holds
 * every thing that may be at the place and some that are not; the caller asks each of them what it would ask without the
 * index, and the first to answer is the one a look at all of them in order would find.
 *
 * <p>A thing wider than {@value #MOST_CELLS} cells a side, or reaching further than {@value #FAR} blocks from the middle of
 * the world, is not sorted: every question then gets all things in order, as a look at all of them would. So does a
 * question about a place that far out.
 *
 * <p>Not thread safe.
 *
 * @param <T> the things
 */
public final class CellIndex<T> {
    /** Most cells a thing may take a side and still be sorted. */
    public static final int MOST_CELLS = 64;
    /** Blocks from the middle of the world beyond which nothing is sorted. */
    public static final int FAR = 1 << 29;

    private final int shift;
    private final Map<String, Long2ObjectOpenHashMap<List<T>>> cells = new HashMap<>();
    private final List<T> all = new ArrayList<>();
    private final List<T> allView = Collections.unmodifiableList(all);
    private boolean unsorted;

    /**
     * @param shift the side of a cell as a power of two: 4 for chunks, 9 for cells of 512 blocks
     */
    public CellIndex(int shift) {
        if (shift < 0 || shift > 24) {
            throw new IllegalArgumentException("A cell of 2^" + shift + " blocks");
        }
        this.shift = shift;
    }

    /**
     * Adds a thing that covers the blocks from {@code minX, minZ} to {@code maxX, maxZ}, both corners inside.
     */
    public void add(String dimension, long minX, long minZ, long maxX, long maxZ, T value) {
        if (all.isEmpty() || all.get(all.size() - 1) != value) {
            all.add(value);
        }
        if (unsorted) {
            return;
        }
        if (minX > maxX || minZ > maxZ || minX < -FAR || maxX > FAR || minZ < -FAR || maxZ > FAR
                || (maxX >> shift) - (minX >> shift) >= MOST_CELLS || (maxZ >> shift) - (minZ >> shift) >= MOST_CELLS) {
            unsorted = true;
            cells.clear();
            return;
        }
        Long2ObjectOpenHashMap<List<T>> map = cells.computeIfAbsent(dimension, d -> new Long2ObjectOpenHashMap<>());
        for (long cx = minX >> shift; cx <= maxX >> shift; cx++) {
            for (long cz = minZ >> shift; cz <= maxZ >> shift; cz++) {
                long key = key((int) cx, (int) cz);
                List<T> list = map.get(key);
                if (list == null) {
                    list = new ArrayList<>(2);
                    map.put(key, list);
                }
                // A thing added again right after itself is there once.
                if (list.isEmpty() || list.get(list.size() - 1) != value) {
                    list.add(value);
                }
            }
        }
    }

    /**
     * Adds a thing to one cell given by the coordinates of the cell itself (those of a chunk for cells of 16 blocks).
     */
    public void addCell(String dimension, int cellX, int cellZ, T value) {
        long x = (long) cellX << shift;
        long z = (long) cellZ << shift;
        add(dimension, x, z, x, z, value);
    }

    /**
     * The things that may be at a place, in the order they were first added; empty when nothing is near.
     */
    public List<T> at(String dimension, int x, int z) {
        if (unsorted || x < -FAR || x > FAR || z < -FAR || z > FAR) {
            return allView;
        }
        Long2ObjectOpenHashMap<List<T>> map = cells.get(dimension);
        if (map == null) {
            return List.of();
        }
        List<T> found = map.get(key(x >> shift, z >> shift));
        return found == null ? List.of() : Collections.unmodifiableList(found);
    }

    /**
     * Every thing added, in the order they were added; a thing added again right after itself is there once.
     */
    public List<T> all() {
        return allView;
    }

    /**
     * Whether some thing could not be sorted, so that every question gets all things.
     */
    public boolean unsorted() {
        return unsorted;
    }

    private static long key(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFF_FFFFL);
    }
}
