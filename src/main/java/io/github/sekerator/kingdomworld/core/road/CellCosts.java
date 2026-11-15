package io.github.sekerator.kingdomworld.core.road;

/**
 * What a block of a levelled road costs the fine search at a height of its deck, from the land of its middle column: the surface
 * (or the deck of a bridge) on the whole width, the earthworks of the middle column times the width with its verges, a share for
 * the roughness of the land round it, and the kind of block it becomes. Seconds of work of one worker, see {@link EarthRules}.
 *
 * <p>Not thread safe: the kind of the last block valued is kept in the instance.
 */
public final class CellCosts {
    private final CorridorLand land;
    private final RoadClass roadClass;
    private final EarthRules earth;
    /** b: a plain block on level ground. */
    final double base;
    /** The weight of a block of length. */
    final double lambda;
    final int width;
    final int grade;
    final int tunnel;
    final int maxFill;
    final double foreignCost;
    private final double deck;
    private final double digSoil;
    private final double digRock;
    private final double fill;
    private final double pier;
    private final double lining;
    /** The kind of the block last valued. */
    RouteCell.Kind kind = RouteCell.Kind.GROUND;
    /** Whether the block last valued lies on a road. */
    boolean onRoad;

    public CellCosts(CorridorLand land, RoadClass roadClass, RouteStyle style, RoadRules rules, EarthRules earth) {
        this.land = land;
        this.roadClass = roadClass;
        this.earth = earth;
        this.base = EarthRules.base(roadClass, rules);
        this.lambda = earth.lambda(roadClass, style, rules);
        this.width = EarthRules.fullWidth(roadClass);
        this.grade = earth.grade(roadClass);
        this.tunnel = earth.tunnelHeight();
        this.maxFill = rules.maxFill();
        this.foreignCost = rules.foreignCost();
        double rails = roadClass == RoadClass.PATH ? 0 : 2 * (EarthRules.RAIL_SECONDS + EarthRules.itemSeconds(RoadLayout.Material.RAIL));
        this.deck = width * (earth.bridgeSeconds() + EarthRules.itemSeconds(RoadLayout.Material.BRIDGE)) + rails;
        this.digSoil = earth.digSoilSeconds();
        this.digRock = earth.digRockSeconds();
        this.fill = earth.fillSeconds() + EarthRules.itemSeconds(RoadLayout.Material.FILL);
        this.pier = earth.bridgeSeconds() + EarthRules.itemSeconds(RoadLayout.Material.PIER);
        this.lining = earth.fillSeconds() + EarthRules.itemSeconds(RoadLayout.Material.LINING);
    }

    public RoadClass roadClass() {
        return roadClass;
    }

    public double base() {
        return base;
    }

    public double lambda() {
        return lambda;
    }

    public int grade() {
        return grade;
    }

    /**
     * What a block costs with its deck at a height, its length aside; infinite where the road may not be at that height. The kind
     * of block it becomes is then in {@link #kind}.
     */
    public double cost(int x, int z, int y) {
        onRoad = false;
        if (land.closed(x, z) || y <= land.minY() || y + tunnel + 2 >= land.maxY()) {
            return Double.POSITIVE_INFINITY;
        }
        int flags = land.flags(x, z);
        if ((flags & LandColumn.ROAD) != 0) {
            if (y != land.roadY(x, z)) {
                return Double.POSITIVE_INFINITY;
            }
            kind = RouteCell.Kind.GROUND;
            onRoad = true;
            // The surface is there already; but a road of its own free of all cost would draw a new one along it for hundreds of
            // blocks.
            return (flags & LandColumn.OWN_ROAD) != 0 ? EarthRules.OWN_ROAD * base : base;
        }
        if ((flags & LandColumn.GATE) != 0 || (flags & LandColumn.WALL) != 0) {
            if (y != land.roadY(x, z)) {
                return Double.POSITIVE_INFINITY;
            }
            kind = RouteCell.Kind.GROUND;
            return (flags & LandColumn.GATE) != 0 ? base : EarthRules.GATE_SECONDS;
        }
        double extra = land.foreign(x, z) ? foreignCost * base : 0.0;
        int ground = land.ground(x, z);
        if (land.water(x, z)) {
            if (y < land.top(x, z) + 1) {
                return Double.POSITIVE_INFINITY;
            }
            kind = RouteCell.Kind.BRIDGE;
            return deck + Math.max(0, y - 1 - ground) * pier / EarthRules.PIER_EVERY + extra;
        }
        if ((flags & LandColumn.LAVA) != 0) {
            // Under lava only a tunnel with a cover, lava itself taken as up to four blocks deep.
            if (y + tunnel + 1 + EarthRules.LAVA_COVER + 4 > ground || land.naturalTo(x, z) > y - 1
                    || land.liquidWithin(x, z, y - 1, y + tunnel + 1) || belowWater(x, z, y)) {
                return Double.POSITIVE_INFINITY;
            }
            kind = RouteCell.Kind.TUNNEL;
            return base + width * tunnelWork(x, z, y, ground) + extra;
        }
        double logs = land.logs(x, z) * EarthRules.LOG_SECONDS;
        double rough = land.rough(x, z) * width * digSoil * 0.5;
        int d = ground - y;
        if (d == 0) {
            kind = RouteCell.Kind.GROUND;
            return base + logs + rough + extra;
        }
        if (d < 0) {
            int under = -d - 1;
            if (under <= maxFill) {
                kind = RouteCell.Kind.FILL;
                return base + width * under * fill + 2 * fill + rough + extra;
            }
            kind = RouteCell.Kind.BRIDGE;
            return deck + under * pier / EarthRules.PIER_EVERY + extra;
        }
        if (belowWater(x, z, y)) {
            // A cutting or a tunnel never goes below water beside it: nothing is flooded.
            return Double.POSITIVE_INFINITY;
        }
        double cut = Double.POSITIVE_INFINITY;
        if (land.naturalTo(x, z) <= y) {
            cut = base + width * cutWork(x, z, y, ground) + logs + rough + extra;
        }
        double bore = Double.POSITIVE_INFINITY;
        if (ground - (y + tunnel) >= EarthRules.MIN_COVER && land.naturalTo(x, z) <= y - 1 && !land.liquidWithin(x, z, y - 1, y + tunnel + 1)) {
            bore = base + width * tunnelWork(x, z, y, ground) + extra;
        }
        if (cut <= bore) {
            kind = RouteCell.Kind.CUT;
            return cut;
        }
        kind = RouteCell.Kind.TUNNEL;
        return bore;
    }

    private boolean belowWater(int x, int z, int y) {
        int water = land.waterNear(x, z);
        return water != LandColumn.NONE && y < water;
    }

    /**
     * Seconds of digging a cutting of a column down to a deck.
     */
    private double cutWork(int x, int z, int y, int ground) {
        int dug = ground - y - land.caveWithin(x, z, y + 1, ground);
        int soil = Math.min(land.soil(x, z), ground - y);
        int rock = Math.max(0, dug - soil);
        return soil * digSoil + rock * digRock;
    }

    /**
     * Seconds of boring a tunnel of a column at a deck, its share of a torch and its roof propped where it is soil.
     */
    private double tunnelWork(int x, int z, int y, int ground) {
        int soilBottom = ground - land.soil(x, z) + 1;
        int high = y + tunnel;
        int soil = land.soil(x, z) <= 0 ? 0 : Math.max(0, Math.min(high, ground) - Math.max(y + 1, soilBottom) + 1);
        int rock = Math.max(0, tunnel - soil - land.caveWithin(x, z, y + 1, high));
        double work = (soil * digSoil + rock * digRock) * EarthRules.TUNNEL_FACTOR;
        if (land.soil(x, z) > 0 && high + 1 >= soilBottom) {
            work += lining;
        }
        return work + (EarthRules.TORCH_SECONDS + EarthRules.itemSeconds(RoadLayout.Material.TORCH)) / (EarthRules.TORCH_EVERY * (double) width);
    }

    /**
     * Whether a block may be a corner of a diagonal step at a height.
     */
    public boolean fits(int x, int z, int y) {
        return Double.isFinite(cost(x, z, y));
    }

    public EarthRules earth() {
        return earth;
    }
}
