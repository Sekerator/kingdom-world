package io.github.sekerator.kingdomworld.core.construction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the buildings of a kingdom stand, so that "which buildings hold this block" looks at one chunk instead of every
 * building: for every chunk the buildings whose box touches it, and for every cell of {@value #WELL_CELL} blocks the wells
 * whose reach touches it. Buildings are numbered by their place in the list the index was made from, and every answer keeps
 * that order. Immutable once made.
 */
public final class BuildingIndex {
    /** Blocks across a cell of the reach of wells. */
    public static final int WELL_CELL = 64;
    private static final int[] NONE = new int[0];

    /**
     * A building to index.
     *
     * @param dimension  the dimension it stands in
     * @param box        its box
     * @param wellRadius how far it reaches as a well, in blocks, or a negative number for a building that is no well
     */
    public record Entry(String dimension, Box box, double wellRadius) {
    }

    private final List<Entry> entries;
    private final Map<String, Map<Long, int[]>> byChunk = new HashMap<>();
    private final Map<String, Map<Long, int[]>> wellsByCell = new HashMap<>();

    public BuildingIndex(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        Map<String, Map<Long, List<Integer>>> chunks = new HashMap<>();
        Map<String, Map<Long, List<Integer>>> cells = new HashMap<>();
        for (int i = 0; i < this.entries.size(); i++) {
            Entry entry = this.entries.get(i);
            Box box = entry.box();
            Map<Long, List<Integer>> inDimension = chunks.computeIfAbsent(entry.dimension(), d -> new HashMap<>());
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    inDimension.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(i);
                }
            }
            if (entry.wellRadius() >= 0) {
                int reach = (int) Math.ceil(entry.wellRadius());
                Map<Long, List<Integer>> wells = cells.computeIfAbsent(entry.dimension(), d -> new HashMap<>());
                for (int gx = Math.floorDiv(box.minX() - reach, WELL_CELL); gx <= Math.floorDiv(box.maxX() + reach, WELL_CELL); gx++) {
                    for (int gz = Math.floorDiv(box.minZ() - reach, WELL_CELL); gz <= Math.floorDiv(box.maxZ() + reach, WELL_CELL); gz++) {
                        wells.computeIfAbsent(key(gx, gz), k -> new ArrayList<>()).add(i);
                    }
                }
            }
        }
        freeze(chunks, byChunk);
        freeze(cells, wellsByCell);
    }

    private static void freeze(Map<String, Map<Long, List<Integer>>> from, Map<String, Map<Long, int[]>> to) {
        for (Map.Entry<String, Map<Long, List<Integer>>> dimension : from.entrySet()) {
            Map<Long, int[]> frozen = new HashMap<>();
            for (Map.Entry<Long, List<Integer>> bucket : dimension.getValue().entrySet()) {
                frozen.put(bucket.getKey(), bucket.getValue().stream().mapToInt(Integer::intValue).toArray());
            }
            to.put(dimension.getKey(), frozen);
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public int size() {
        return entries.size();
    }

    /**
     * The buildings whose box holds a block, by their numbers in order.
     */
    public int[] containing(String dimension, int x, int y, int z) {
        Map<Long, int[]> inDimension = byChunk.get(dimension);
        if (inDimension == null) {
            return NONE;
        }
        int[] candidates = inDimension.get(key(x >> 4, z >> 4));
        if (candidates == null) {
            return NONE;
        }
        int[] found = new int[candidates.length];
        int count = 0;
        for (int i : candidates) {
            if (entries.get(i).box().contains(x, y, z)) {
                found[count++] = i;
            }
        }
        return count == candidates.length ? found : Arrays.copyOf(found, count);
    }

    /**
     * The buildings whose box touches a chunk, by their numbers in order.
     */
    public int[] inChunk(String dimension, int chunkX, int chunkZ) {
        Map<Long, int[]> inDimension = byChunk.get(dimension);
        int[] found = inDimension == null ? null : inDimension.get(key(chunkX, chunkZ));
        return found == null ? NONE : found.clone();
    }

    /**
     * The wells whose reach may cover a block: every well within its radius of the block is among them, in order. The caller
     * measures the distance exactly.
     */
    public int[] wellsNear(String dimension, int x, int z) {
        Map<Long, int[]> inDimension = wellsByCell.get(dimension);
        if (inDimension == null) {
            return NONE;
        }
        int[] found = inDimension.get(key(Math.floorDiv(x, WELL_CELL), Math.floorDiv(z, WELL_CELL)));
        return found == null ? NONE : found.clone();
    }
}
