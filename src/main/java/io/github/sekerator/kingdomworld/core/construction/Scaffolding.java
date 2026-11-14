package io.github.sekerator.kingdomworld.core.construction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.LongPredicate;

/**
 * Scaffolding of the builders: where a builder that cannot reach a block from the ground puts up a column of scaffolds and
 * stands on it, as a player jumps and puts a block under its feet. A column stands on firm ground on the site of the
 * construction or in a narrow band around it; the builder climbs it block by block to the height from which the block is
 * within its reach, does the steps it reaches from there and takes the column down from the top as it goes down.
 *
 * <p>The world is seen through {@link World}, so that the choice of the place can be tried without a game. Positions of
 * scaffolds are kept packed into a {@code long} as {@link #pack} packs them.
 */
public final class Scaffolding {
    /** Blocks around the site in which scaffolds may stand. */
    public static final int BAND = 3;
    /** Most scaffolds under one builder. */
    public static final int MAX_HEIGHT = 16;
    /** How much closer than its reach a builder stands, so that a body not quite in the middle of the column still reaches. */
    public static final double MARGIN = 0.3;
    /** How far below the block a column is looked for, in blocks. */
    private static final int DEPTH = MAX_HEIGHT + 4;

    private Scaffolding() {
    }

    /**
     * What the place of a column looks like.
     */
    public interface World {
        /**
         * Whether a body may be in the cell: nothing to bump into and no liquid.
         */
        boolean open(int x, int y, int z);

        /**
         * Whether one may stand on top of the block.
         */
        boolean firm(int x, int y, int z);

        /**
         * Whether a scaffold may go into the cell: it is free (air or what gives way, as grass and a layer of snow), on the
         * site or in the band around it, on the land of the kingdom, and neither a road nor a building of others.
         */
        boolean placeable(int x, int y, int z);

        /**
         * Whether the block may carry a column: firm and holding nothing (a chest or a post carries no scaffolds).
         */
        boolean carries(int x, int y, int z);

        /**
         * Whether a block of the plan still goes into the cell: a column there is in the way of the work later.
         */
        default boolean planned(int x, int y, int z) {
            return false;
        }
    }

    /**
     * A place to work from: the column at {@code x, z} on the ground at {@code ground} (the height of the feet of a body standing
     * on the ground), the builder standing with its feet at {@code feet}. A spot of height 0 needs no scaffolds.
     */
    public record Spot(int x, int z, int ground, int feet) {
        public int height() {
            return feet - ground;
        }

        /**
         * The scaffolds of the column from the bottom up, as {x, y, z}.
         */
        public List<int[]> cells() {
            List<int[]> cells = new ArrayList<>(height());
            for (int y = ground; y < feet; y++) {
                cells.add(new int[]{x, y, z});
            }
            return cells;
        }
    }

    // ---------------------------------------------------------------- positions

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    public static int x(long packed) {
        return (int) (packed >> 38);
    }

    public static int y(long packed) {
        return (int) (packed << 52 >> 52);
    }

    public static int z(long packed) {
        return (int) (packed << 26 >> 38);
    }

    /**
     * Whether a cell is on the site of a construction or in the band around it: {@link #BAND} blocks to the sides, as deep
     * below its bottom and two blocks over its top.
     */
    public static boolean inBand(Box box, int x, int y, int z) {
        return x >= box.minX() - BAND && x <= box.maxX() + BAND && z >= box.minZ() - BAND && z <= box.maxZ() + BAND
                && y >= box.minY() - BAND && y <= box.maxY() + 2;
    }

    /**
     * The order in which scaffolds are taken down: from the top down, so that none is left hanging and nobody stands on one
     * taken away under it; inside a layer by z, then x.
     */
    public static List<Long> takeDownOrder(Collection<Long> cells) {
        List<Long> order = new ArrayList<>(cells);
        order.sort(Comparator.comparingInt((Long cell) -> -y(cell)).thenComparingInt(Scaffolding::z).thenComparingInt(Scaffolding::x));
        return order;
    }

    // ---------------------------------------------------------------- reach

    /**
     * Whether a block is within reach of the eyes: the distance to the middle of the block, with the margin.
     */
    public static boolean reaches(double eyeX, double eyeY, double eyeZ, int x, int y, int z, double reach) {
        double dx = x + 0.5 - eyeX;
        double dy = y + 0.5 - eyeY;
        double dz = z + 0.5 - eyeZ;
        double within = Math.max(0.5, reach - MARGIN);
        return dx * dx + dy * dy + dz * dz <= within * within;
    }

    private static boolean reachesFrom(int x, int feet, int z, double eye, int tx, int ty, int tz, double reach) {
        return reaches(x + 0.5, feet + eye, z + 0.5, tx, ty, tz, reach);
    }

    // ---------------------------------------------------------------- the place of a column

    /**
     * The best place to work on a block from: the column needing the fewest scaffolds, then the one nearest to the builder,
     * then one in nobody's way; a place on the ground (height 0) when there is one within reach. A place high above the feet
     * of the builder (on a wall, on a roof) counts as far, since it is seldom one the builder can walk to.
     *
     * @param tx       the block
     * @param reach    how far the builder reaches
     * @param eye      the height of its eyes over its feet
     * @param fromX    where the builder stands, to prefer columns near it
     * @param excluded columns the builder could not get to, packed with y 0
     * @return the place, or empty when no column of at most {@link #MAX_HEIGHT} scaffolds reaches the block
     */
    public static Optional<Spot> find(int tx, int ty, int tz, double reach, double eye, World world, int fromX, int fromY, int fromZ,
                                      LongPredicate excluded) {
        List<Spot> spots = candidates(tx, ty, tz, reach, eye, world, fromX, fromY, fromZ, excluded);
        return spots.isEmpty() ? Optional.empty() : Optional.of(spots.get(0));
    }

    /**
     * Every column a block can be worked on from, best first, as {@link #find} chooses.
     */
    public static List<Spot> candidates(int tx, int ty, int tz, double reach, double eye, World world, int fromX, int fromY, int fromZ,
                                        LongPredicate excluded) {
        int radius = (int) Math.ceil(reach) + 1;
        List<Spot> spots = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (int x = tx - radius; x <= tx + radius; x++) {
            for (int z = tz - radius; z <= tz + radius; z++) {
                if (excluded.test(pack(x, 0, z))) {
                    continue;
                }
                Spot spot = column(x, z, tx, ty, tz, reach, eye, world);
                if (spot == null) {
                    continue;
                }
                double dx = x - fromX;
                double dz = z - fromZ;
                int inTheWay = 0;
                for (int y = spot.ground(); y <= spot.feet() + 1; y++) {
                    if (world.planned(x, y, z)) {
                        inTheWay++;
                    }
                }
                double score = spot.height() * 4.0 + Math.sqrt(dx * dx + dz * dz) * 0.5 + inTheWay * 3.0
                        + Math.max(0, spot.ground() - fromY - 1) * 3.0;
                int at = 0;
                while (at < scores.size() && scores.get(at) <= score) {
                    at++;
                }
                spots.add(at, spot);
                scores.add(at, score);
            }
        }
        return spots;
    }

    /**
     * The lowest place on one column from which a block is reached, or {@code null}: the highest room for a body (two open
     * cells) at most a block over the target, or under it when the column is the target's own; the ground under that room;
     * scaffolds from the ground up to the feet, every one of them placeable and the ground able to carry them.
     */
    static Spot column(int x, int z, int tx, int ty, int tz, double reach, double eye, World world) {
        boolean under = x == tx && z == tz;
        int top = under ? ty - 2 : ty + 1;
        int floor = ty - DEPTH;
        int high = top;
        while (high >= floor && !(world.open(x, high, z) && world.open(x, high + 1, z))) {
            high--;
        }
        if (high < floor) {
            return null;
        }
        int ground = high;
        while (ground > floor && world.open(x, ground - 1, z)) {
            ground--;
        }
        if (!world.firm(x, ground - 1, z)) {
            return null;
        }
        for (int feet = ground; feet <= high && feet - ground <= MAX_HEIGHT; feet++) {
            if (feet > ground && (!world.placeable(x, feet - 1, z) || feet - 1 == ground && !world.carries(x, ground - 1, z))) {
                // A cell that takes no scaffold, or a ground that carries none: nothing higher on this column.
                return null;
            }
            if (reachesFrom(x, feet, z, eye, tx, ty, tz, reach)) {
                return new Spot(x, z, ground, feet);
            }
        }
        return null;
    }

    /**
     * The height on a column the builder already stands on at which a block is reached: the nearest to where it stands, going
     * down over its own scaffolds or up over placeable cells. Empty when no height of the column reaches the block or the block
     * is the column itself.
     *
     * @param feet where the feet of the builder are now
     */
    public static OptionalInt feetOn(int x, int z, int ground, int feet, int tx, int ty, int tz, double reach, double eye, World world) {
        if (x == tx && z == tz && ty < Math.max(feet, ground) + 2) {
            // The block is the column itself or where the builder's head is.
            return OptionalInt.empty();
        }
        for (int at = feet; at >= ground; at--) {
            if (reachesFrom(x, at, z, eye, tx, ty, tz, reach)) {
                return OptionalInt.of(at);
            }
        }
        for (int at = feet + 1; at - ground <= MAX_HEIGHT; at++) {
            if (!world.placeable(x, at - 1, z) || at - 1 == ground && !world.carries(x, ground - 1, z) || !world.open(x, at + 1, z)
                    || x == tx && z == tz && ty <= at + 1) {
                return OptionalInt.empty();
            }
            if (reachesFrom(x, at, z, eye, tx, ty, tz, reach)) {
                return OptionalInt.of(at);
            }
        }
        return OptionalInt.empty();
    }
}
