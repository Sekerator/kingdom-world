package io.github.sekerator.kingdomworld.core.road;

/**
 * One column of the land as a levelled road sees it: where its natural ground is, the water over it, how deep its soil goes
 * before rock, the trees on it, how far down it is natural, the first cave under it, and what stands there that a road must not
 * break.
 *
 * @param ground     the top natural solid block, under trees, plants, snow and water; the top block of whatever stands there
 *                   when it is built over
 * @param waterTop   the top of the water over the ground (ice over water counts as water), or {@link #NONE}
 * @param soil       blocks of soil from the ground down before the first rock, 0 to {@value #DEEP_SOIL}; deeper soil counts as rock
 * @param logs       logs of trees standing over the ground
 * @param flags      {@link #BUILT}, {@link #LAVA}, {@link #BORDER}, {@link #VOID}, {@link #ROAD}, {@link #OWN_ROAD}, {@link #GATE},
 *                   {@link #WALL}
 * @param roadY      the height of the road on the column, when it is on one
 * @param naturalTo  the lowest height down to which the column is natural without a break, the ground itself at most
 * @param caveTop    the top of the first hollow under the ground, or {@link #NONE}
 * @param caveBottom its bottom
 * @param caveLiquid whether water or lava is in it
 */
public record LandColumn(int ground, int waterTop, int soil, int logs, int flags, int roadY, int naturalTo, int caveTop, int caveBottom,
                         boolean caveLiquid) {
    /** No such height. */
    public static final int NONE = Integer.MIN_VALUE;
    /** The deepest soil a column tells apart. */
    public static final int DEEP_SOIL = 15;

    /** Something made stands there: a block that is not natural, a protected one or one with a block entity, or a frame. */
    public static final int BUILT = 1;
    /** Lava at the top. */
    public static final int LAVA = 1 << 1;
    /** Beyond the border of the world. */
    public static final int BORDER = 1 << 2;
    /** No ground at all: the void between the islands of the End. */
    public static final int VOID = 1 << 3;
    /** On a road. */
    public static final int ROAD = 1 << 4;
    /** On a road of the kingdom that plans. */
    public static final int OWN_ROAD = 1 << 5;
    /** In a gate of a wall. */
    public static final int GATE = 1 << 6;
    /** In a wall of a city of the kingdom that plans: crossed at the cost of a gate. */
    public static final int WALL = 1 << 7;
    /** In a wall of another kingdom outside its gates. */
    public static final int FOREIGN_WALL = 1 << 8;

    /**
     * Plain land: solid ground at a height, its soil so deep, natural all the way down.
     */
    public static LandColumn land(int ground, int soil) {
        return new LandColumn(ground, NONE, Math.min(DEEP_SOIL, Math.max(0, soil)), 0, 0, NONE, ground - 96, NONE, NONE, false);
    }

    /**
     * Water over a bed.
     */
    public static LandColumn water(int bed, int top) {
        return new LandColumn(bed, top, 2, 0, 0, NONE, bed - 96, NONE, NONE, false);
    }

    /**
     * A column built over, that no road crosses.
     */
    public static LandColumn built(int top) {
        return new LandColumn(top, NONE, 0, 0, BUILT, NONE, top, NONE, NONE, false);
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public boolean water() {
        return waterTop != NONE;
    }

    /**
     * The top of what a road meets there: the water or the ground.
     */
    public int top() {
        return water() ? waterTop : ground;
    }

    public boolean cave() {
        return caveTop != NONE;
    }

    /**
     * Whether nothing can be done on the column: built over, beyond the border or without ground.
     */
    public boolean closed() {
        return has(BUILT) || has(BORDER) || has(VOID) || has(FOREIGN_WALL);
    }

    public LandColumn withFlags(int add) {
        return new LandColumn(ground, waterTop, soil, logs, flags | add, roadY, naturalTo, caveTop, caveBottom, caveLiquid);
    }

    public LandColumn withRoad(int y, boolean own) {
        return new LandColumn(ground, waterTop, soil, logs, flags | ROAD | (own ? OWN_ROAD : 0), y, naturalTo, caveTop, caveBottom, caveLiquid);
    }

    public LandColumn withCave(int top, int bottom, boolean liquid) {
        return new LandColumn(ground, waterTop, soil, logs, flags, roadY, naturalTo, top, bottom, liquid);
    }

    public LandColumn withLogs(int count) {
        return new LandColumn(ground, waterTop, soil, Math.max(0, count), flags, roadY, naturalTo, caveTop, caveBottom, caveLiquid);
    }

    public LandColumn withNaturalTo(int y) {
        return new LandColumn(ground, waterTop, soil, logs, flags, roadY, y, caveTop, caveBottom, caveLiquid);
    }

    /**
     * Blocks of a cave between two heights, both included.
     */
    public int caveWithin(int low, int high) {
        if (!cave() || high < low) {
            return 0;
        }
        return Math.max(0, Math.min(high, caveTop) - Math.max(low, caveBottom) + 1);
    }
}
