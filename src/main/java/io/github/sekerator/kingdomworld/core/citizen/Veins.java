package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Veins of ore beside a tunnel: from a block of ore a miner has uncovered, the blocks of ore that touch it with a
 * side, then those touching them, and so on. Only ore belongs to a vein, never the rock around it, and every block
 * of the list touches an earlier one, so that a miner taking them in order always reaches the next from the hole
 * of the one before.
 */
public final class Veins {
    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** A block of the world. */
    public record Block(int x, int y, int z) {
    }

    /** Tells whether a block is ore the miner may take. */
    @FunctionalInterface
    public interface Ore {
        boolean at(int x, int y, int z);
    }

    private Veins() {
    }

    /**
     * The blocks of the vein that starts at an uncovered block, nearest first.
     *
     * @param limit most blocks taken from one vein; 0 or less takes none
     */
    public static List<Block> blocks(int x, int y, int z, Ore ore, int limit) {
        List<Block> vein = new ArrayList<>();
        if (limit <= 0 || !ore.at(x, y, z)) {
            return vein;
        }
        Set<Block> seen = new HashSet<>();
        Deque<Block> open = new ArrayDeque<>();
        Block start = new Block(x, y, z);
        seen.add(start);
        open.add(start);
        while (!open.isEmpty() && vein.size() < limit) {
            Block block = open.poll();
            vein.add(block);
            for (int[] side : SIDES) {
                Block next = new Block(block.x() + side[0], block.y() + side[1], block.z() + side[2]);
                if (seen.add(next) && ore.at(next.x(), next.y(), next.z())) {
                    open.add(next);
                }
            }
        }
        return vein;
    }
}
