package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.land.Tier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the settlements stand: cells of 256 blocks with the settlements whose square of chunks reaches into each, so that the
 * settlement of a chunk and the settlements near a point are found without looking at all of them. Built anew whenever the
 * settlements change; a ruin stands nowhere.
 */
public final class SettlementGrid {
    /** The side of a cell, in blocks. */
    public static final int CELL_BLOCKS = 256;
    private static final int CELL_SHIFT = 4;

    private final Map<Long, int[]> cells;
    private final Map<Integer, Settlement> settlements;

    private SettlementGrid(Map<Long, int[]> cells, Map<Integer, Settlement> settlements) {
        this.cells = cells;
        this.settlements = settlements;
    }

    /**
     * Half the side of the square of chunks a settlement of a rank stands on, without its middle chunk.
     */
    public static int reach(Tier tier) {
        return switch (tier) {
            case CAPITAL -> 6;
            case CITY -> 4;
            case TOWN -> 3;
            case VILLAGE -> 2;
            case HAMLET -> 1;
        };
    }

    public static SettlementGrid of(Collection<Settlement> all) {
        Map<Long, List<Integer>> lists = new HashMap<>();
        Map<Integer, Settlement> kept = new HashMap<>();
        for (Settlement settlement : all) {
            if (settlement.status() == Settlement.Status.RUINED) {
                continue;
            }
            kept.put(settlement.id(), settlement);
            int reach = reach(settlement.tier());
            int chunkX = settlement.x() >> 4;
            int chunkZ = settlement.z() >> 4;
            for (int cellX = (chunkX - reach) >> CELL_SHIFT; cellX <= (chunkX + reach) >> CELL_SHIFT; cellX++) {
                for (int cellZ = (chunkZ - reach) >> CELL_SHIFT; cellZ <= (chunkZ + reach) >> CELL_SHIFT; cellZ++) {
                    lists.computeIfAbsent(key(cellX, cellZ), k -> new ArrayList<>()).add(settlement.id());
                }
            }
        }
        Map<Long, int[]> cells = new HashMap<>(lists.size() * 2);
        lists.forEach((key, ids) -> cells.put(key, ids.stream().mapToInt(Integer::intValue).toArray()));
        return new SettlementGrid(cells, kept);
    }

    private static long key(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    /**
     * The settlement whose square holds a chunk, the one of the highest rank when squares meet; 0 for none.
     */
    public int at(int chunkX, int chunkZ) {
        int[] ids = cells.get(key(chunkX >> CELL_SHIFT, chunkZ >> CELL_SHIFT));
        if (ids == null) {
            return 0;
        }
        Settlement best = null;
        for (int id : ids) {
            Settlement settlement = settlements.get(id);
            int reach = reach(settlement.tier());
            if (Math.abs((settlement.x() >> 4) - chunkX) <= reach && Math.abs((settlement.z() >> 4) - chunkZ) <= reach
                    && (best == null || settlement.tier().ordinal() < best.tier().ordinal())) {
                best = settlement;
            }
        }
        return best == null ? 0 : best.id();
    }

    /**
     * The settlements whose middle is within a distance of a point, in blocks, nearest first.
     */
    public List<Settlement> near(int x, int z, int radius) {
        List<Settlement> found = new ArrayList<>();
        long most = (long) radius * radius;
        int fromX = Math.floorDiv(x - radius, CELL_BLOCKS);
        int toX = Math.floorDiv(x + radius, CELL_BLOCKS);
        int fromZ = Math.floorDiv(z - radius, CELL_BLOCKS);
        int toZ = Math.floorDiv(z + radius, CELL_BLOCKS);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int cellX = fromX; cellX <= toX; cellX++) {
            for (int cellZ = fromZ; cellZ <= toZ; cellZ++) {
                int[] ids = cells.get(key(cellX, cellZ));
                if (ids == null) {
                    continue;
                }
                for (int id : ids) {
                    Settlement settlement = settlements.get(id);
                    long dx = settlement.x() - x;
                    long dz = settlement.z() - z;
                    if (dx * dx + dz * dz <= most && seen.add(id)) {
                        found.add(settlement);
                    }
                }
            }
        }
        found.sort(java.util.Comparator.comparingLong(settlement -> {
            long dx = settlement.x() - x;
            long dz = settlement.z() - z;
            return dx * dx + dz * dz;
        }));
        return found;
    }

    public int size() {
        return settlements.size();
    }
}
