package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The layout of a mine below a work post: on each level a corridor along the x axis in front of the post with
 * branches every third block, all one block wide and two high, and a staircase three blocks high down to the
 * next level. Levels lie {@link GatheringRules#MINE_LEVEL_STEP} blocks apart; the corridor of each level is
 * {@code MINE_LEVEL_STEP} blocks further along z than the one above, where the staircase arrives.
 *
 * <p>The cells come in digging order, and each cell names the cell it is entered from, so that a miner only digs
 * a cell it can reach: one whose entrance is open.
 */
public final class MinePlan {
    /** Height of a corridor or branch cell. */
    public static final int TUNNEL_HEIGHT = 2;
    /** Height of a staircase cell; walking down a step needs a block more. */
    public static final int STAIR_HEIGHT = 3;
    /** Branches leave the corridor every this many blocks. */
    public static final int BRANCH_SPACING = 3;

    /**
     * One cell of the mine: the block a miner stands on top of is {@code y - 1}; the cell is the column of
     * {@code height} blocks from {@code y} up.
     *
     * @param from index of the cell the miner enters this one from, or -1 for the first cell, entered from the post
     */
    public record Cell(int x, int y, int z, int height, int level, int from) {
    }

    private MinePlan() {
    }

    /**
     * Levels a mine of the given radius has: as many as fit, at most {@code maxLevels}.
     */
    public static int levels(int radius, int maxLevels) {
        int fit = Math.max(0, radius - 1) / GatheringRules.MINE_LEVEL_STEP + 1;
        return Math.max(1, Math.min(Math.max(1, maxLevels), fit));
    }

    /**
     * The cells of the mine of a post at {@code (x0, y0, z0)}: the post stands on the floor of the first level.
     */
    public static List<Cell> cells(int x0, int y0, int z0, int radius, int maxLevels) {
        int r = Math.max(1, radius);
        int levels = levels(r, maxLevels);
        List<Cell> cells = new ArrayList<>();
        int entrance = -1;
        for (int level = 0; level < levels; level++) {
            int y = y0 - level * GatheringRules.MINE_LEVEL_STEP;
            int zc = z0 + 1 + level * GatheringRules.MINE_LEVEL_STEP;
            boolean last = level == levels - 1;
            // A hub reached down a staircase needs the room of a stair cell to step into.
            int hub = add(cells, x0, y, zc, level == 0 ? TUNNEL_HEIGHT : STAIR_HEIGHT, level, entrance);
            for (int side : new int[]{1, -1}) {
                int previous = hub;
                for (int dx = 1; dx <= r; dx++) {
                    previous = add(cells, x0 + side * dx, y, zc, TUNNEL_HEIGHT, level, previous);
                    if (dx % BRANCH_SPACING == 0) {
                        branch(cells, x0 + side * dx, y, zc, z0, r, level, previous);
                    }
                }
            }
            if (!last) {
                // The staircase goes down one block per step along z; its last step is the hub of the next level.
                int previous = hub;
                for (int step = 1; step < GatheringRules.MINE_LEVEL_STEP; step++) {
                    previous = add(cells, x0, y - step, zc + step, STAIR_HEIGHT, level, previous);
                }
                entrance = previous;
            }
        }
        return Collections.unmodifiableList(cells);
    }

    private static void branch(List<Cell> cells, int x, int y, int zc, int z0, int r, int level, int corridor) {
        int previous = corridor;
        for (int z = zc + 1; z <= z0 + r; z++) {
            previous = add(cells, x, y, z, TUNNEL_HEIGHT, level, previous);
        }
        previous = corridor;
        for (int z = zc - 1; z >= z0 - r; z--) {
            previous = add(cells, x, y, z, TUNNEL_HEIGHT, level, previous);
        }
    }

    private static int add(List<Cell> cells, int x, int y, int z, int height, int level, int from) {
        cells.add(new Cell(x, y, z, height, level, from));
        return cells.size() - 1;
    }
}
