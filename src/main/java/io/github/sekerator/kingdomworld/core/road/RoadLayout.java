package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The blocks of a road: its middle widened to the width of its class, each block with what it needs. Side blocks take the
 * height of their middle block; in the world a side block of a road on the ground that would float or sink more than a
 * block is left out.
 */
public final class RoadLayout {

    /**
     * What a block of road is made of.
     */
    public enum Material {
        /** Gravel or stone of the road itself. */
        SURFACE,
        /** Planks of a bridge. */
        BRIDGE,
        /** Earth or stone of an embankment. */
        FILL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One block of road.
     *
     * @param cell  the index of the middle block it belongs to
     * @param y     the height of the road there
     * @param depth blocks of embankment under it
     * @param side  whether it is beside the middle of the road
     */
    public record Block(int cell, int x, int y, int z, RouteCell.Kind kind, int depth, boolean side) {

        /**
         * Items the block takes, in the order they are used: the embankment from the bottom up, then the top.
         */
        public List<Material> needs(RoadClass roadClass) {
            List<Material> list = new ArrayList<>(depth + 1);
            if (kind == RouteCell.Kind.BRIDGE) {
                list.add(Material.BRIDGE);
                return list;
            }
            for (int i = 0; i < depth; i++) {
                list.add(Material.FILL);
            }
            if (kind == RouteCell.Kind.FILL) {
                // An embankment of a path is topped with earth that becomes the path.
                list.add(roadClass == RoadClass.PATH ? Material.FILL : Material.SURFACE);
            } else if (roadClass != RoadClass.PATH) {
                list.add(Material.SURFACE);
            }
            return list;
        }
    }

    private RoadLayout() {
    }

    /**
     * The blocks of a road along its middle, in the order of the middle; a block shared by two middle blocks belongs to
     * the first.
     */
    public static List<Block> blocks(List<RouteCell> cells, RoadClass roadClass) {
        List<Block> result = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        int half = roadClass.width() / 2;
        for (int i = 0; i < cells.size(); i++) {
            RouteCell cell = cells.get(i);
            if (taken.add(column(cell.x(), cell.z()))) {
                result.add(new Block(i, cell.x(), cell.y(), cell.z(), cell.kind(), cell.depth(), false));
            }
            if (half == 0) {
                continue;
            }
            RouteCell before = cells.get(Math.max(0, i - 1));
            RouteCell after = cells.get(Math.min(cells.size() - 1, i + 1));
            int dx = Integer.signum(after.x() - before.x());
            int dz = Integer.signum(after.z() - before.z());
            if (dx == 0 && dz == 0) {
                dx = 1;
            }
            // Across the way; at a bend both directions, so that the corner is full.
            int[][] across = dx != 0 && dz != 0 ? new int[][]{{1, 0}, {0, 1}} : new int[][]{{-dz, dx}};
            for (int[] dir : across) {
                for (int s = -half; s <= half; s++) {
                    if (s == 0) {
                        continue;
                    }
                    int x = cell.x() + dir[0] * s;
                    int z = cell.z() + dir[1] * s;
                    if (taken.add(column(x, z))) {
                        result.add(new Block(i, x, cell.y(), z, cell.kind(), cell.depth(), true));
                    }
                }
            }
        }
        return result;
    }

    static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    /**
     * Items of each material all the blocks take.
     */
    public static int[] materials(List<Block> blocks, RoadClass roadClass) {
        int[] counts = new int[Material.values().length];
        for (Block block : blocks) {
            for (Material material : block.needs(roadClass)) {
                counts[material.ordinal()]++;
            }
        }
        return counts;
    }

    /**
     * Seconds of a builder's work the blocks take: every item placed, and at least a moment for a block of path.
     */
    public static double seconds(List<Block> blocks, RoadClass roadClass, RoadRules rules) {
        double seconds = 0;
        for (Block block : blocks) {
            seconds += Math.max(1, block.needs(roadClass).size()) * rules.secondsPerBlock(roadClass);
        }
        return seconds;
    }
}
