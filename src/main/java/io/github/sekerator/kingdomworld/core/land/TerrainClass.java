package io.github.sekerator.kingdomworld.core.land;

import java.util.Locale;

/**
 * What a cell of the survey (or a chunk of a probe) mostly is. The ordinals are stored in the survey file: new classes are
 * added at the end only.
 *
 * <p>The cost is what it takes a state, a province or a road to step onto the land: open land 1, hills 3, steep mountains 8,
 * crossing a river or a lake 12 (a ford or a bridge); the sea and land the survey never looked at cannot be crossed.
 */
public enum TerrainClass {
    /** Not surveyed: beyond the circle of the survey. */
    UNKNOWN(-1),
    /** The sea. */
    OCEAN(-1),
    /** A lake. */
    LAKE(12),
    /** A river runs through. */
    RIVER(12),
    /** A beach or a stony shore at the sea. */
    BEACH(2),
    /** A swamp or a marsh. */
    SWAMP(3),
    /** Open land: plains, forests, savannas, snowy plains. */
    LAND(1),
    /** Hills, or ground that rises a little against its neighbours. */
    HILLS(3),
    /** Mountains, peaks, or ground far higher or lower than a neighbour. */
    STEEP(8),
    /** A desert or a badland. */
    DESERT(1);

    private static final TerrainClass[] VALUES = values();

    private final int cost;

    TerrainClass(int cost) {
        this.cost = cost;
    }

    /**
     * What it takes to step onto a cell of this class; -1 where no state, province or road may go.
     */
    public int cost() {
        return cost;
    }

    public boolean passable() {
        return cost > 0;
    }

    /**
     * Land in the sense of the shares of the plan: not the sea, a lake or land never surveyed. A river is mostly land around it.
     */
    public boolean dry() {
        return this != UNKNOWN && this != OCEAN && this != LAKE;
    }

    /**
     * Whether a capital or a settlement may stand on a cell of this class: open land or a desert.
     */
    public boolean settles() {
        return this == LAND || this == DESERT;
    }

    /**
     * Whether a border through a cell of this class is drawn by the chunks probed in it: the border follows the water.
     */
    public boolean probedOnBorder() {
        return this == RIVER || this == LAKE || this == BEACH || this == OCEAN;
    }

    public byte id() {
        return (byte) ordinal();
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The class stored as a byte; anything unknown is {@link #UNKNOWN}.
     */
    public static TerrainClass byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : UNKNOWN;
    }
}
