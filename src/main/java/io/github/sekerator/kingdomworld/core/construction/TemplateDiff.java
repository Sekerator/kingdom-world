package io.github.sekerator.kingdomworld.core.construction;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The difference between two templates laid at the same place, for an upgrade: only the blocks that differ are touched.
 * Blocks of the new template that stand the same in the old one are left (skipped), blocks of the old template where the
 * new one wants nothing are cleared, and the rest is placed over what stands there.
 */
public final class TemplateDiff {

    /**
     * A block of a template where it ends up in the world.
     *
     * @param state the block state as text, the same for the same state
     * @param kind  how it counts: built blocks are placed by the builders, parts come with their main block, nothing and
     *              liquids are not built
     */
    public record Cell(int x, int y, int z, String state, BlockCost.Kind kind) {
        public Cell {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(kind, "kind");
        }

        boolean stands() {
            return kind != BlockCost.Kind.NOTHING && kind != BlockCost.Kind.LIQUID;
        }

        long key() {
            return pack(x, y, z);
        }
    }

    /**
     * @param skip  indices of the new template's blocks that stand the same already
     * @param clear positions {x, y, z} where the old template has a block and the new one nothing
     * @param cost  what the blocks of the new template that are placed cost
     */
    public record Result(BitSet skip, List<int[]> clear, Map<String, Integer> cost) {
    }

    private TemplateDiff() {
    }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /**
     * @param costs what each block of the new template costs, by its index
     */
    public static Result of(List<Cell> oldCells, List<Cell> newCells, List<Map<String, Integer>> costs) {
        Map<Long, Cell> before = new HashMap<>();
        for (Cell cell : oldCells) {
            before.put(cell.key(), cell);
        }
        Map<Long, Cell> after = new HashMap<>();
        BitSet skip = new BitSet();
        Map<String, Integer> cost = new LinkedHashMap<>();
        for (int i = 0; i < newCells.size(); i++) {
            Cell cell = newCells.get(i);
            after.put(cell.key(), cell);
            Cell old = before.get(cell.key());
            boolean same = old != null ? old.state().equals(cell.state()) : !cell.stands();
            if (same) {
                skip.set(i);
            } else if (i < costs.size() && cell.kind() != BlockCost.Kind.PART) {
                costs.get(i).forEach((item, count) -> cost.merge(item, count, Integer::sum));
            }
        }
        List<int[]> clear = new ArrayList<>();
        for (Cell cell : oldCells) {
            if (!cell.stands() || cell.kind() == BlockCost.Kind.PART) {
                continue;
            }
            Cell next = after.get(cell.key());
            if (next == null || !next.stands()) {
                clear.add(new int[]{cell.x(), cell.y(), cell.z()});
            }
        }
        return new Result(skip, clear, cost);
    }
}
