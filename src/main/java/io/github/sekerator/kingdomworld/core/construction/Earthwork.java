package io.github.sekerator.kingdomworld.core.construction;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Earthworks: a rectangle of land its builders level to a height, cut down to it or fill up to it, as it lay when they were
 * laid out. The record keeps what the plan of the work is made from, so that the plan comes out the same after a restart
 * however much of the land is dug already: the height of the ground of every column of the rectangle and of the ring of
 * columns around it, the highest block of every column (the trees over the ground), and what the work would dig and the
 * storehouses held then, by item, which choose the earth and stone each block of the fill is made of.
 *
 * @param minX   the west edge of the rectangle in the world
 * @param minZ   the north edge of the rectangle in the world
 * @param sizeX  its width, west to east
 * @param sizeZ  its length, north to south
 * @param target the height of the finished ground, the top block of the fill
 * @param smooth whether its edges slope one to one to the land around, so that no cliff is left
 * @param ground the height of the solid ground of every column of the rectangle and of the ring around it, row by row from the
 *               north west corner of the ring
 * @param top    the highest block of every column of the rectangle (trees and plants over the ground), row by row
 * @param dug    what the blocks the work digs make, by item: earth, stone, gravel and sand for the fill
 * @param stock  what of those items the storehouses held when the work was laid out
 */
public record Earthwork(int minX, int minZ, int sizeX, int sizeZ, Mode mode, int target, boolean smooth, int[] ground, int[] top,
                        Map<String, Integer> dug, Map<String, Integer> stock) {
    /** What the work does. */
    public enum Mode {
        /** Down to the height where the land is higher, up to it where it is lower. */
        LEVEL,
        /** Only down to the height. */
        CUT,
        /** Only up to the height. */
        FILL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Mode byId(String id) {
            for (Mode mode : values()) {
                if (mode.id().equals(id)) {
                    return mode;
                }
            }
            return LEVEL;
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public Earthwork {
        Objects.requireNonNull(mode, "mode");
        if (sizeX < 1 || sizeZ < 1 || ground.length != (sizeX + 2) * (sizeZ + 2) || top.length != sizeX * sizeZ) {
            throw new IllegalArgumentException("The heights do not fit the rectangle of the earthworks");
        }
        ground = ground.clone();
        top = top.clone();
        dug = Map.copyOf(new LinkedHashMap<>(dug));
        stock = Map.copyOf(new LinkedHashMap<>(stock));
    }

    /**
     * The height of the ground of a column, from the north west corner of the rectangle; -1 and the size reach the ring of
     * columns around it.
     */
    public int groundAt(int dx, int dz) {
        return ground[(dz + 1) * (sizeX + 2) + dx + 1];
    }

    /**
     * The highest block of a column of the rectangle, never below its ground.
     */
    public int topAt(int dx, int dz) {
        return Math.max(groundAt(dx, dz), top[dz * sizeX + dx]);
    }

    /**
     * The height of the ground of a column once the work is done. With smooth edges the ground near an edge keeps within one
     * block of the land outside for every block nearer the edge, so that it slopes one to one to it.
     */
    public int surface(int dx, int dz) {
        int wanted = target;
        if (smooth) {
            int west = dx;
            int east = sizeX - 1 - dx;
            int north = dz;
            int south = sizeZ - 1 - dz;
            int nearest = Math.min(Math.min(west, east), Math.min(north, south));
            int outside;
            if (nearest == west) {
                outside = groundAt(-1, dz);
            } else if (nearest == east) {
                outside = groundAt(sizeX, dz);
            } else if (nearest == north) {
                outside = groundAt(dx, -1);
            } else {
                outside = groundAt(dx, sizeZ);
            }
            int reach = nearest + 1;
            wanted = Math.max(outside - reach, Math.min(outside + reach, target));
        }
        int ground = groundAt(dx, dz);
        return switch (mode) {
            case LEVEL -> wanted;
            case CUT -> Math.min(ground, wanted);
            case FILL -> Math.max(ground, wanted);
        };
    }

    /**
     * The lowest height a column is dug down to, the block over its finished ground; nothing is dug where it is higher than
     * the column's highest block.
     */
    public int digFrom(int dx, int dz) {
        return surface(dx, dz) + 1;
    }

    /**
     * The highest block dug in a column: everything from {@link #digFrom} up to here goes, trees and plants too.
     */
    public int digTo(int dx, int dz) {
        return topAt(dx, dz);
    }

    /**
     * The lowest block of the fill of a column, the one over its ground.
     */
    public int fillFrom(int dx, int dz) {
        return groundAt(dx, dz) + 1;
    }

    /**
     * The highest block of the fill of a column, its finished ground; nothing is filled where it is below {@link #fillFrom}.
     */
    public int fillTo(int dx, int dz) {
        return surface(dx, dz);
    }

    /**
     * Blocks the work fills, all columns together.
     */
    public int fills() {
        int count = 0;
        for (int dz = 0; dz < sizeZ; dz++) {
            for (int dx = 0; dx < sizeX; dx++) {
                count += Math.max(0, fillTo(dx, dz) - fillFrom(dx, dz) + 1);
            }
        }
        return count;
    }

    /**
     * The most a column's ground lies from the height of the work: how far the work goes down or up.
     */
    public int span() {
        int most = 0;
        for (int dz = 0; dz < sizeZ; dz++) {
            for (int dx = 0; dx < sizeX; dx++) {
                most = Math.max(most, Math.abs(groundAt(dx, dz) - surface(dx, dz)));
            }
        }
        return most;
    }

    /**
     * The lowest height the work touches: the first block of a fill, or the finished ground of a cut.
     */
    public int bottom() {
        int low = Integer.MAX_VALUE;
        for (int dz = 0; dz < sizeZ; dz++) {
            for (int dx = 0; dx < sizeX; dx++) {
                low = Math.min(low, Math.min(fillFrom(dx, dz), digFrom(dx, dz)));
            }
        }
        return low;
    }

    /**
     * The highest height the work touches: the top of a tree dug, or the finished ground of a fill.
     */
    public int ceiling() {
        int high = Integer.MIN_VALUE;
        for (int dz = 0; dz < sizeZ; dz++) {
            for (int dx = 0; dx < sizeX; dx++) {
                high = Math.max(high, Math.max(digTo(dx, dz), fillTo(dx, dz)));
            }
        }
        return high;
    }

    /**
     * A number that tells two plans apart: the plan of a construction is checked against it after a restart.
     */
    public long fingerprint() {
        long hash = 1125899906842597L;
        for (long value : new long[]{minX, minZ, sizeX, sizeZ, mode.ordinal(), target, smooth ? 1 : 0, Arrays.hashCode(ground),
                Arrays.hashCode(top), new java.util.TreeMap<>(dug).hashCode(), new java.util.TreeMap<>(stock).hashCode()}) {
            hash = 31 * hash + value;
        }
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Earthwork that && minX == that.minX && minZ == that.minZ && sizeX == that.sizeX && sizeZ == that.sizeZ
                && mode == that.mode && target == that.target && smooth == that.smooth && Arrays.equals(ground, that.ground)
                && Arrays.equals(top, that.top) && dug.equals(that.dug) && stock.equals(that.stock);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(fingerprint());
    }
}
