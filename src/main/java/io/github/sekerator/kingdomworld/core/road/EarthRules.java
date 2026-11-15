package io.github.sekerator.kingdomworld.core.road;

/**
 * The rules of the earthworks of a levelled road: its grade, its tunnels, and what digging, filling and bridging cost. Every
 * price is in seconds of work of one worker in an ordinary mood; a game day is {@value #DAY} of them. The search, the profile
 * and the card of a plan all count in them; coins are only shown.
 *
 * @param maxGrade             a road of the class climbs one block in at most so many blocks (path, gravel, paved)
 * @param tunnelHeight         blocks of room in a tunnel above its floor
 * @param digSoilSeconds       seconds a builder takes to dig a block of soil
 * @param digRockSeconds       seconds a miner takes to dig a block of rock
 * @param rockByBuilders       seconds of a builder for a second of a miner, for rock the miners do not dig
 * @param fillSeconds          seconds a builder takes to put a block of embankment or lining in
 * @param bridgeSeconds        seconds a builder takes to lay a column of a bridge deck or a block of a pier
 * @param routeModeWeights     the weight of a block of length against the work of a block of road, by style
 * @param roadMinerShare       the share of their time miners whose post is short of its goal give to the roads
 * @param plannerThreads       threads that search the ways of roads
 * @param plannerMillisPerTick milliseconds of the main thread a tick for copying the land the planner reads
 */
public record EarthRules(int[] maxGrade, int tunnelHeight, double digSoilSeconds, double digRockSeconds, double rockByBuilders,
                         double fillSeconds, double bridgeSeconds, double[] routeModeWeights, double roadMinerShare, int plannerThreads,
                         int plannerMillisPerTick) {
    public static final EarthRules DEFAULT = new Builder().build();

    /** Seconds of work in a game day. */
    public static final double DAY = 1_200.0;
    /** Blocks of verge on each side of the way. */
    public static final int VERGE = 1;
    /** Every so many blocks of a bridge stand on a pier. */
    public static final int PIER_EVERY = 4;
    /** Every so many blocks of a tunnel a torch stands, at the block of this remainder. */
    public static final int TORCH_EVERY = 8;
    public static final int TORCH_AT = 4;
    /** How much longer digging in a tunnel takes. */
    public static final double TUNNEL_FACTOR = 1.5;
    /** Seconds of a builder for a log of a tree felled. */
    public static final double LOG_SECONDS = 1.0;
    /** Seconds of a builder for a rail of a bridge. */
    public static final double RAIL_SECONDS = 1.0;
    /** Seconds of a builder for a torch. */
    public static final double TORCH_SECONDS = 0.5;
    /** Seconds a road through the wall of its own city costs at the place of the gate. */
    public static final double GATE_SECONDS = 24.0;
    /** Blocks of rock or soil over a tunnel at least; over lava more. */
    public static final int MIN_COVER = 2;
    public static final int LAVA_COVER = 3;
    /** What a block of the kingdom's own road costs the search, against a block of new road. */
    public static final double OWN_ROAD = 0.25;
    /** What a change of grade costs the profile, against a block of road. */
    public static final double GRADE_CHANGE = 0.25;
    /** How much dearer than the way found a straightened stretch may be. */
    public static final double SMOOTH_TOLERANCE = 0.02;

    /**
     * Seconds an item of each material is worth to the search, besides the work of putting it in: what fetching it from the
     * storehouses and what it is worth come to.
     */
    private static final double[] ITEM_SECONDS = {1.0, 1.0, 0.5, 1.0, 1.0, 0.5, 1.0};

    public EarthRules {
        maxGrade = maxGrade == null ? null : maxGrade.clone();
        routeModeWeights = routeModeWeights == null ? null : routeModeWeights.clone();
        if (maxGrade == null || maxGrade.length != RoadClass.values().length || routeModeWeights == null
                || routeModeWeights.length != RouteStyle.values().length) {
            throw new IllegalArgumentException("Earthwork rules need a value for every class and style");
        }
        for (int grade : maxGrade) {
            if (grade < 1 || grade > 8) {
                throw new IllegalArgumentException("Earthwork rules out of range");
            }
        }
        for (double weight : routeModeWeights) {
            if (!(weight >= 0 && weight <= 1_000)) {
                throw new IllegalArgumentException("Earthwork rules out of range");
            }
        }
        if (tunnelHeight < 3 || tunnelHeight > 5 || !seconds(digSoilSeconds) || !seconds(digRockSeconds) || !(rockByBuilders >= 1
                && rockByBuilders <= 10) || !seconds(fillSeconds) || !seconds(bridgeSeconds) || !(roadMinerShare >= 0 && roadMinerShare <= 1)
                || plannerThreads < 1 || plannerThreads > 4 || plannerMillisPerTick < 1 || plannerMillisPerTick > 20) {
            throw new IllegalArgumentException("Earthwork rules out of range");
        }
    }

    private static boolean seconds(double value) {
        return value >= 0.1 && value <= 60;
    }

    /**
     * Blocks a road of the class goes at least before it climbs or falls a block.
     */
    public int grade(RoadClass roadClass) {
        return maxGrade[roadClass.ordinal()];
    }

    @Override
    public int[] maxGrade() {
        return maxGrade.clone();
    }

    @Override
    public double[] routeModeWeights() {
        return routeModeWeights.clone();
    }

    /**
     * Seconds an item of a material is worth to the search.
     */
    public static double itemSeconds(RoadLayout.Material material) {
        return ITEM_SECONDS[material.ordinal()];
    }

    /**
     * Blocks across the way with its verges.
     */
    public static int fullWidth(RoadClass roadClass) {
        return roadClass.width() + 2 * VERGE;
    }

    /**
     * What a block of road on level ground costs, its surface on the whole width: the work and the items, a path without one.
     */
    public static double base(RoadClass roadClass, RoadRules rules) {
        double item = roadClass == RoadClass.PATH ? 0 : itemSeconds(RoadLayout.Material.SURFACE);
        return roadClass.width() * (rules.secondsPerBlock(roadClass) + item);
    }

    /**
     * What a block of length costs the search besides its work, for a road of the class in a style.
     */
    public double lambda(RoadClass roadClass, RouteStyle style, RoadRules rules) {
        return routeModeWeights[style.ordinal()] * base(roadClass, rules);
    }

    /**
     * What digging a block of rock costs the builders when the miners do not help.
     */
    public double rockByBuildersSeconds() {
        return digRockSeconds * rockByBuilders;
    }

    /**
     * Rules built step by step, from the defaults.
     */
    public static final class Builder {
        public int[] maxGrade = {1, 2, 3};
        public int tunnelHeight = 3;
        public double digSoilSeconds = 2.0;
        public double digRockSeconds = 5.0;
        public double rockByBuilders = 2.0;
        public double fillSeconds = 2.0;
        public double bridgeSeconds = 3.0;
        public double[] routeModeWeights = {0.0, 2.0, 20.0};
        public double roadMinerShare = 0.5;
        public int plannerThreads = 1;
        public int plannerMillisPerTick = 2;

        public EarthRules build() {
            return new EarthRules(maxGrade, tunnelHeight, digSoilSeconds, digRockSeconds, rockByBuilders, fillSeconds, bridgeSeconds,
                    routeModeWeights, roadMinerShare, plannerThreads, plannerMillisPerTick);
        }
    }
}
