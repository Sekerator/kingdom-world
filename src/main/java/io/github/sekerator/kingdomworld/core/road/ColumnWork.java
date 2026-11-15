package io.github.sekerator.kingdomworld.core.road;

/**
 * The work and the items of one column of a levelled road, in seconds of work of one worker: soil is dug by builders and rock
 * by miners, digging in a tunnel takes half as long again; an embankment, a pier, a deck, a rail, lining and a torch are put in
 * by builders, each with its item; the surface of the way takes the seconds of its class as the first layout does.
 *
 * @param builders seconds of builders
 * @param miners   seconds of miners; when no miner helps, builders dig the rock in {@link EarthRules#rockByBuilders} times as
 *                 long
 * @param items    items of each {@link RoadLayout.Material}
 */
public record ColumnWork(double builders, double miners, int[] items) {
    public static final ColumnWork NONE = new ColumnWork(0, 0, new int[RoadLayout.Material.values().length]);

    public ColumnWork {
        items = items.clone();
    }

    @Override
    public int[] items() {
        return items.clone();
    }

    public int items(RoadLayout.Material material) {
        return items[material.ordinal()];
    }

    /**
     * The work of a block of a levelled road; nothing for a block of the first layout or one on another road.
     */
    public static ColumnWork of(RoadLayout.Block block, RoadClass roadClass, RoadRules roads, EarthRules earth) {
        RoadLayout.ColumnSurvey survey = block.survey();
        if (survey == null || survey.onRoad()) {
            return NONE;
        }
        int[] items = new int[RoadLayout.Material.values().length];
        for (RoadLayout.Material material : block.needs(roadClass)) {
            items[material.ordinal()]++;
        }
        double factor = block.kind() == RouteCell.Kind.TUNNEL ? EarthRules.TUNNEL_FACTOR : 1.0;
        double builders = survey.logs() * EarthRules.LOG_SECONDS
                + survey.soil() * earth.digSoilSeconds() * factor
                + survey.fill() * earth.fillSeconds()
                + survey.pier() * earth.bridgeSeconds()
                + survey.lining() * earth.fillSeconds()
                + (survey.light() ? EarthRules.TORCH_SECONDS : 0.0);
        RoadLayout.Material top = block.top(roadClass);
        if (top == RoadLayout.Material.BRIDGE) {
            builders += earth.bridgeSeconds();
        } else if (block.role() != RoadLayout.Role.VERGE) {
            builders += roads.secondsPerBlock(roadClass);
        }
        builders += items[RoadLayout.Material.RAIL.ordinal()] * EarthRules.RAIL_SECONDS;
        double miners = survey.rock() * earth.digRockSeconds() * factor;
        return new ColumnWork(builders, miners, items);
    }

    /**
     * The seconds of builders of the earthworks alone: everything but the surface or the deck, for the share of a road levelled.
     */
    public static double earthwork(RoadLayout.Block block, RoadClass roadClass, RoadRules roads, EarthRules earth) {
        ColumnWork work = of(block, roadClass, roads, earth);
        if (work == NONE) {
            return 0;
        }
        double deck = block.top(roadClass) == RoadLayout.Material.BRIDGE ? earth.bridgeSeconds()
                : block.role() != RoadLayout.Role.VERGE ? roads.secondsPerBlock(roadClass) : 0.0;
        return Math.max(0, work.builders() - deck) + work.miners();
    }

    /**
     * What the column is worth to the search: the seconds of builders and miners and its items at their worth.
     */
    public double effort() {
        double sum = builders + miners;
        for (RoadLayout.Material material : RoadLayout.Material.values()) {
            sum += items[material.ordinal()] * EarthRules.itemSeconds(material);
        }
        return sum;
    }

    /**
     * Seconds of builders when the miners do not help: the rock dug by builders.
     */
    public double buildersAlone(EarthRules earth) {
        return builders + miners * earth.rockByBuilders();
    }

    /**
     * The work of two columns together.
     */
    public ColumnWork plus(ColumnWork other) {
        int[] sum = items.clone();
        for (int i = 0; i < sum.length; i++) {
            sum[i] += other.items[i];
        }
        return new ColumnWork(builders + other.builders, miners + other.miners, sum);
    }
}
