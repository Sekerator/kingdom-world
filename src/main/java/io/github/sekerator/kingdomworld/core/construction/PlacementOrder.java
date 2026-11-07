package io.github.sekerator.kingdomworld.core.construction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The order in which a builder works: clearing from the top down, then building from the bottom up, all blocks that stand
 * on their own first (full blocks, stairs, slabs, fences) and the blocks that lean on others afterwards (torches, doors,
 * ladders, carpets, rails, redstone, plants), so that nothing falls off while the house goes up.
 */
public final class PlacementOrder {

    /**
     * Whether a block stands on its own or leans on others.
     */
    public enum Phase {
        STANDING,
        LEANING
    }

    /**
     * A block to place, at a position in the world.
     *
     * @param index where it is in the template's list of blocks
     */
    public record Block(int index, int x, int y, int z, Phase phase) {
    }

    private PlacementOrder() {
    }

    /**
     * The blocks in building order: standing ones from the bottom up, then leaning ones from the bottom up; inside a layer
     * by z, then x, then the template's own order.
     */
    public static List<Block> building(List<Block> blocks) {
        List<Block> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparing(Block::phase).thenComparingInt(Block::y).thenComparingInt(Block::z).thenComparingInt(Block::x)
                .thenComparingInt(Block::index));
        return sorted;
    }

    /**
     * Positions to clear, from the top down; packed positions keep their order inside a layer.
     *
     * @param positions positions as {x, y, z}
     */
    public static List<int[]> clearing(List<int[]> positions) {
        List<int[]> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.comparingInt((int[] p) -> -p[1]).thenComparingInt(p -> p[2]).thenComparingInt(p -> p[0]));
        return sorted;
    }
}
