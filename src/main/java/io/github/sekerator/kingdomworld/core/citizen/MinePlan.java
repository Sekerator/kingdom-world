package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The layout of a mine at a work post. Beside the post a spiral staircase goes down inside a square of three by
 * three blocks, one block per step and three blocks high. At the working depth, and on every further level
 * {@link GatheringRules#MINE_LEVEL_STEP} blocks below it (or above it once the bottom of the world is reached), a
 * short passage leads from the staircase to the main corridor, one block wide and two high, which runs in both
 * directions past the staircase; branches leave it every third block to both sides, so that two blocks of rock lie
 * between neighbouring branches and every block of the layer is seen from a tunnel.
 *
 * <p>Corridors, branches and passages stop at the edge of the kingdom's land. The cells come in digging order, and
 * each cell names the cell it is entered from, so that a miner only digs a cell it can reach: one whose entrance is
 * open.
 */
public final class MinePlan {
    /** Height of a corridor or branch cell. */
    public static final int TUNNEL_HEIGHT = 2;
    /** Height of a staircase cell; walking down a step needs a block more. */
    public static final int STAIR_HEIGHT = 3;
    /** Branches leave the corridor every this many blocks. */
    public static final int BRANCH_SPACING = 3;
    /** The corridor runs this many blocks from the post, just past the square of the staircase. */
    public static final int CORRIDOR_OFFSET = 4;
    /** The lowest level of a mine lies at least this many blocks above the bottom of the world. */
    public static final int BOTTOM_MARGIN = 6;
    /** A plan never grows beyond this many cells; levels that would not fit are left out. */
    public static final int MAX_CELLS = 60_000;

    /** The square of the staircase around its middle, as steps away from the post and to the side, in walking order. */
    private static final int[][] RING = {{1, 0}, {1, 1}, {2, 1}, {3, 1}, {3, 0}, {3, -1}, {2, -1}, {1, -1}};

    /** What a cell is part of. */
    public enum Kind {
        STAIR,
        PASSAGE,
        CORRIDOR,
        BRANCH
    }

    /** Tells whether a column of blocks lies on the kingdom's own land. */
    @FunctionalInterface
    public interface Land {
        boolean owns(int x, int z);
    }

    /**
     * One cell of the mine: the block a miner stands on top of is {@code y - 1}; the cell is the column of
     * {@code height} blocks from {@code y} up.
     *
     * @param level index of the level the cell belongs to, in digging order; stairs belong to the level they were
     *              first dug for
     * @param from  index of the cell the miner enters this one from, or -1 for the first cell, entered from the post
     */
    public record Cell(int x, int y, int z, int height, int level, int from, Kind kind) {
    }

    /**
     * Where the mine lies around its post.
     *
     * @param dx     the way from the post to the staircase, along x
     * @param dz     the way from the post to the staircase, along z
     * @param alongX whether the corridor runs along x (otherwise along z)
     */
    public record Layout(int dx, int dz, boolean alongX) {
        /** The step to the side of the way to the staircase: the direction of the corridor. */
        public int sideX() {
            return dz;
        }

        public int sideZ() {
            return dx;
        }
    }

    private MinePlan() {
    }

    /**
     * The depth a mine works at: the wanted one, but never above the post and never closer to the bottom of the world
     * than {@link #BOTTOM_MARGIN} blocks (a post that stands lower than that digs at its own level).
     */
    public static int clampDepth(int wanted, int postY, int worldBottom) {
        return Math.min(postY, Math.max(worldBottom + BOTTOM_MARGIN, wanted));
    }

    /**
     * The floors of the levels of a mine in digging order: the working depth first, then one level every
     * {@link GatheringRules#MINE_LEVEL_STEP} blocks below it down to the bottom of the world, then above it up to
     * the post.
     */
    public static List<Integer> floors(int depth, int postY, int worldBottom, int maxLevels) {
        int step = GatheringRules.MINE_LEVEL_STEP;
        int lowest = worldBottom + BOTTOM_MARGIN;
        int levels = Math.max(1, maxLevels);
        List<Integer> floors = new ArrayList<>();
        floors.add(depth);
        for (int y = depth - step; floors.size() < levels && y >= lowest; y -= step) {
            floors.add(y);
        }
        for (int y = depth + step; floors.size() < levels && y <= postY - step; y += step) {
            floors.add(y);
        }
        return floors;
    }

    /**
     * Chooses where the mine lies: the corridor runs along the axis on which more of it lies on the kingdom's own
     * land (x if both are the same), on the side of the post where the square of the staircase is on the land.
     */
    public static Layout layout(int x0, int z0, int corridorLength, Land land) {
        Layout[] choices = {new Layout(0, 1, true), new Layout(0, -1, true), new Layout(1, 0, false), new Layout(-1, 0, false)};
        Layout best = choices[0];
        int bestScore = -1;
        for (Layout choice : choices) {
            int score = corridorOnLand(x0, z0, corridorLength, choice, land);
            if (stairsOnLand(x0, z0, choice, land)) {
                // A staircase that fits counts more than any length of corridor.
                score += 2 * corridorLength + 2;
            }
            if (score > bestScore) {
                best = choice;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean stairsOnLand(int x0, int z0, Layout layout, Land land) {
        for (int away = 1; away <= 3; away++) {
            for (int side = -1; side <= 1; side++) {
                if (!land.owns(x0 + away * layout.dx() + side * layout.sideX(), z0 + away * layout.dz() + side * layout.sideZ())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Blocks of the corridor of a layout that lie on the land without a gap from its middle. */
    private static int corridorOnLand(int x0, int z0, int corridorLength, Layout layout, Land land) {
        int cx = x0 + CORRIDOR_OFFSET * layout.dx();
        int cz = z0 + CORRIDOR_OFFSET * layout.dz();
        if (!land.owns(cx, cz)) {
            return 0;
        }
        int count = 1;
        for (int direction : new int[]{1, -1}) {
            for (int t = 1; t <= corridorLength; t++) {
                if (!land.owns(cx + direction * t * layout.sideX(), cz + direction * t * layout.sideZ())) {
                    break;
                }
                count++;
            }
        }
        return count;
    }

    /**
     * The cells of the mine of a post at {@code (x0, y0, z0)}, which stands on the floor it is entered from.
     *
     * @param depth          floor of the first level, at most {@code y0}
     * @param corridorLength blocks of corridor to each side of its middle
     * @param branchLength   blocks of a branch to each side of the corridor
     * @param maxLevels      most levels of the mine
     * @param worldBottom    the lowest block of the world
     */
    public static List<Cell> cells(int x0, int y0, int z0, int depth, int corridorLength, int branchLength, int maxLevels,
                                   int worldBottom, Land land) {
        return new Builder(x0, y0, z0, Math.max(0, corridorLength), Math.max(0, branchLength), land)
                .build(floors(Math.min(depth, y0), y0, worldBottom, maxLevels));
    }

    private static final class Builder {
        private final int x0;
        private final int y0;
        private final int z0;
        private final int corridorLength;
        private final int branchLength;
        private final Land land;
        private final Layout layout;
        private final List<Cell> cells = new ArrayList<>();
        /** Index of the cell of each step of the staircase built so far. */
        private final List<Integer> steps = new ArrayList<>();
        /** Steps that stay on the level of the post before the staircase goes down: one more if a level lies there. */
        private int flat;
        private boolean stairsCut;

        Builder(int x0, int y0, int z0, int corridorLength, int branchLength, Land land) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.corridorLength = corridorLength;
            this.branchLength = branchLength;
            this.land = land;
            this.layout = layout(x0, z0, corridorLength, land);
        }

        List<Cell> build(List<Integer> floors) {
            flat = floors.contains(y0) ? 1 : 0;
            for (int level = 0; level < floors.size() && cells.size() < MAX_CELLS; level++) {
                level(level, floors.get(level));
            }
            return Collections.unmodifiableList(cells);
        }

        private int x(int away, int side) {
            return x0 + away * layout.dx() + side * layout.sideX();
        }

        private int z(int away, int side) {
            return z0 + away * layout.dz() + side * layout.sideZ();
        }

        /**
         * Builds the staircase down to a step, if the land lets it.
         *
         * @return whether the step is there
         */
        private boolean stairsTo(int step, int level) {
            while (steps.size() <= step && !stairsCut) {
                int next = steps.size();
                int[] place = RING[next % RING.length];
                int x = x(place[0], place[1]);
                int z = z(place[0], place[1]);
                if (!land.owns(x, z)) {
                    stairsCut = true;
                    break;
                }
                int floor = y0 - Math.max(0, next - flat);
                // Cells on the level of the post are walked on the flat; a step down needs a block more of room.
                int height = floor == y0 ? TUNNEL_HEIGHT : STAIR_HEIGHT;
                steps.add(add(x, floor, z, height, level, next == 0 ? -1 : steps.get(next - 1), Kind.STAIR));
            }
            return steps.size() > step;
        }

        private void level(int level, int floor) {
            int step = flat + y0 - floor;
            boolean down = false;
            if (step % RING.length == 0) {
                // The step in front of the post has no way out of the square: the passage leaves one step higher.
                step--;
                down = true;
            }
            if (step < 0 || !stairsTo(step, level)) {
                return;
            }
            int previous = steps.get(step);
            int[] place = RING[step % RING.length];
            int away = place[0];
            int side = place[1];
            if (away == 3) {
                // The far row of the square opens on the corridor.
                away = CORRIDOR_OFFSET;
            } else {
                // Out of the square to the side, then along it to the line of the corridor.
                side *= 2;
            }
            boolean first = true;
            while (true) {
                if (!land.owns(x(away, side), z(away, side))) {
                    return;
                }
                boolean arrived = away == CORRIDOR_OFFSET;
                previous = add(x(away, side), floor, z(away, side), first && down ? STAIR_HEIGHT : TUNNEL_HEIGHT, level, previous,
                        arrived ? Kind.CORRIDOR : Kind.PASSAGE);
                first = false;
                if (arrived) {
                    break;
                }
                away++;
            }
            int arrival = side;
            if (arrival == 0) {
                // Behind the middle of the corridor lies the staircase: the branch there goes away from it only.
                branch(0, floor, level, previous, 1);
            }
            for (int direction : new int[]{1, -1}) {
                int from = previous;
                for (int t = arrival + direction; Math.abs(t) <= corridorLength; t += direction) {
                    if (!land.owns(x(CORRIDOR_OFFSET, t), z(CORRIDOR_OFFSET, t))) {
                        break;
                    }
                    from = add(x(CORRIDOR_OFFSET, t), floor, z(CORRIDOR_OFFSET, t), TUNNEL_HEIGHT, level, from, Kind.CORRIDOR);
                    if (t == 0) {
                        branch(t, floor, level, from, 1);
                    } else if (t % BRANCH_SPACING == 0) {
                        branch(t, floor, level, from, 1);
                        branch(t, floor, level, from, -1);
                    }
                }
            }
        }

        private void branch(int t, int floor, int level, int corridor, int direction) {
            int previous = corridor;
            for (int s = 1; s <= branchLength; s++) {
                int away = CORRIDOR_OFFSET + direction * s;
                if (!land.owns(x(away, t), z(away, t))) {
                    return;
                }
                previous = add(x(away, t), floor, z(away, t), TUNNEL_HEIGHT, level, previous, Kind.BRANCH);
            }
        }

        private int add(int x, int y, int z, int height, int level, int from, Kind kind) {
            cells.add(new Cell(x, y, z, height, level, from, kind));
            return cells.size() - 1;
        }
    }
}
