package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Citizens kept by the squares of 64 by 64 blocks their home, workplace and last position lie in, so that the citizens near
 * a place are found without looking at all of them. A citizen lies in one to three squares, once in each. Every citizen also
 * keeps the number of its arrival here: citizens put earlier have lower numbers, and a citizen taken out and put again comes
 * after all others. Not thread safe.
 */
public final class CitizenCells {
    /** Blocks along the side of a square, as a shift: 64 blocks. */
    public static final int CELL_SHIFT = 6;
    /** Chunks along the side of a square, as a shift: 4 chunks. */
    private static final int CHUNK_SHIFT = CELL_SHIFT - 4;

    private record Cell(String dimension, long key) {
    }

    private static final class Entry {
        private final long order;
        private Cell[] cells;

        private Entry(long order, Cell[] cells) {
            this.order = order;
            this.cells = cells;
        }
    }

    private final Map<String, Map<Long, Set<UUID>>> byCell = new HashMap<>();
    private final Map<UUID, Entry> entries = new HashMap<>();
    private long nextOrder;

    // ---------------------------------------------------------------- changes

    /**
     * Puts a citizen into the squares of its places, or moves it there if it is here already. Missing places
     * ({@code null}) are left out.
     */
    void place(UUID id, WorldPos home, WorldPos workplace, WorldPos lastPos) {
        Cell[] cells = cellsOf(home, workplace, lastPos);
        Entry entry = entries.computeIfAbsent(id, key -> new Entry(nextOrder++, new Cell[0]));
        if (Arrays.equals(entry.cells, cells)) {
            // The usual case: somebody walked about within its square.
            return;
        }
        for (Cell cell : entry.cells) {
            if (!contains(cells, cell)) {
                takeOut(cell, id);
            }
        }
        for (Cell cell : cells) {
            if (!contains(entry.cells, cell)) {
                byCell.computeIfAbsent(cell.dimension(), d -> new HashMap<>()).computeIfAbsent(cell.key(), k -> new HashSet<>()).add(id);
            }
        }
        entry.cells = cells;
    }

    /**
     * Takes a citizen out of all its squares.
     *
     * @return whether it was here
     */
    boolean remove(UUID id) {
        Entry entry = entries.remove(id);
        if (entry == null) {
            return false;
        }
        for (Cell cell : entry.cells) {
            takeOut(cell, id);
        }
        return true;
    }

    private void takeOut(Cell cell, UUID id) {
        Map<Long, Set<UUID>> inDimension = byCell.get(cell.dimension());
        if (inDimension == null) {
            return;
        }
        Set<UUID> ids = inDimension.get(cell.key());
        if (ids == null || !ids.remove(id)) {
            return;
        }
        if (ids.isEmpty()) {
            inDimension.remove(cell.key());
            if (inDimension.isEmpty()) {
                byCell.remove(cell.dimension());
            }
        }
    }

    // ---------------------------------------------------------------- queries

    public int size() {
        return entries.size();
    }

    public boolean contains(UUID id) {
        return entries.containsKey(id);
    }

    /**
     * The number of the arrival of a citizen here, or {@link Long#MAX_VALUE} for one that is not here.
     */
    public long order(UUID id) {
        Entry entry = entries.get(id);
        return entry == null ? Long.MAX_VALUE : entry.order;
    }

    /**
     * Adds to {@code into} every citizen with a place within {@code radius} chunks of a chunk, the distance counted as in
     * {@link ActiveSelection#distance} (the larger of the distances along x and z), and maybe some a little farther: whole
     * squares are taken.
     */
    public void near(String dimension, int chunkX, int chunkZ, int radius, Collection<UUID> into) {
        Map<Long, Set<UUID>> inDimension = byCell.get(dimension);
        if (inDimension == null || radius < 0) {
            return;
        }
        int fromX = squareOf((long) chunkX - radius);
        int toX = squareOf((long) chunkX + radius);
        int fromZ = squareOf((long) chunkZ - radius);
        int toZ = squareOf((long) chunkZ + radius);
        long squares = ((long) toX - fromX + 1) * ((long) toZ - fromZ + 1);
        if (squares > inDimension.size()) {
            // Fewer squares hold citizens than the area covers: look at those.
            for (Map.Entry<Long, Set<UUID>> entry : inDimension.entrySet()) {
                int x = (int) (entry.getKey() >> 32);
                int z = entry.getKey().intValue();
                if (x >= fromX && x <= toX && z >= fromZ && z <= toZ) {
                    into.addAll(entry.getValue());
                }
            }
            return;
        }
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                Set<UUID> ids = inDimension.get(key(x, z));
                if (ids != null) {
                    into.addAll(ids);
                }
            }
        }
    }

    // ---------------------------------------------------------------- squares

    /**
     * The square along one axis of a chunk coordinate; coordinates beyond the range of the world are clamped.
     */
    private static int squareOf(long chunk) {
        long clamped = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, chunk));
        return (int) (clamped >> CHUNK_SHIFT);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static Cell cellOf(WorldPos pos) {
        return new Cell(pos.dimension(), key(pos.x() >> CELL_SHIFT, pos.z() >> CELL_SHIFT));
    }

    /**
     * The distinct squares of the places, in the order of the places.
     */
    private static Cell[] cellsOf(WorldPos home, WorldPos workplace, WorldPos lastPos) {
        Cell[] cells = new Cell[3];
        int count = 0;
        for (WorldPos pos : new WorldPos[]{home, workplace, lastPos}) {
            if (pos == null) {
                continue;
            }
            Cell cell = cellOf(pos);
            if (!contains(cells, cell)) {
                cells[count++] = cell;
            }
        }
        return count == cells.length ? cells : Arrays.copyOf(cells, count);
    }

    private static boolean contains(Cell[] cells, Cell cell) {
        for (Cell other : cells) {
            if (cell.equals(other)) {
                return true;
            }
        }
        return false;
    }
}
