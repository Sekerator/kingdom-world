package io.github.sekerator.kingdomworld.core.road;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The estimate of a levelled road before it is built: its length along the way (a block for a straight step, the square root of
 * two for a diagonal one) and straight across country, its earthworks (logs, soil and rock dug, embankment, the blocks of tunnels
 * and bridges, piers, lining and torches), its items, the seconds of builders and of miners, those on the land of each other
 * kingdom, and the time of a caravan along it and across country. Coins are reckoned outside the core.
 */
public record RoadEstimate(double length, double straight, int cells, int logs, int soil, int rock, int fill, int cutCells, int tunnelCells,
                           int fillCells, int bridgeCells, int pierBlocks, int lining, int torches, int[] materials, double builders,
                           double miners, double caravanTicks, double straightTicks, Map<UUID, double[]> foreign) {
    public static final RoadEstimate EMPTY = new RoadEstimate(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            new int[RoadLayout.Material.values().length], 0, 0, 0, 0, Map.of());

    public RoadEstimate {
        materials = materials.clone();
        foreign = Map.copyOf(foreign);
    }

    @Override
    public int[] materials() {
        return materials.clone();
    }

    /**
     * The length of a way of blocks side by side, a diagonal step through its corner counted as the square root of two.
     */
    public static double length(List<RouteCell> cells) {
        double length = 0;
        int i = 1;
        while (i < cells.size()) {
            RouteCell before = cells.get(i - 1);
            if (i + 1 < cells.size()) {
                RouteCell after = cells.get(i + 1);
                if (Math.abs(after.x() - before.x()) == 1 && Math.abs(after.z() - before.z()) == 1) {
                    length += Math.sqrt(2.0);
                    i += 2;
                    continue;
                }
            }
            RouteCell cell = cells.get(i);
            length += Math.hypot(cell.x() - before.x(), cell.z() - before.z());
            i++;
        }
        return length;
    }

    /**
     * The estimate of a way.
     *
     * @param straight        blocks between its ends straight across country
     * @param blocksPerMinute the pace of a caravan across country
     */
    public static RoadEstimate of(List<RouteCell> cells, RoadClass roadClass, RoadSurvey.Land land, RoadRules rules, EarthRules earth,
                                  double straight, double blocksPerMinute, UUID kingdomId) {
        if (cells.size() < 2) {
            return EMPTY;
        }
        List<RoadLayout.ColumnSurvey> surveys = RoadSurvey.survey(cells, roadClass, land, earth);
        List<RoadLayout.Block> blocks = RoadLayout.blocks(cells, roadClass, RoadLayout.LEVELLED, surveys);
        int[] materials = new int[RoadLayout.Material.values().length];
        double builders = 0;
        double miners = 0;
        int logs = 0;
        int soil = 0;
        int rock = 0;
        int fill = 0;
        int pier = 0;
        int lining = 0;
        int torches = 0;
        Map<UUID, double[]> foreign = new LinkedHashMap<>();
        for (RoadLayout.Block block : blocks) {
            ColumnWork work = ColumnWork.of(block, roadClass, rules, earth);
            int[] items = work.items();
            for (int m = 0; m < items.length; m++) {
                materials[m] += items[m];
            }
            builders += work.builders();
            miners += work.miners();
            RoadLayout.ColumnSurvey survey = block.survey();
            logs += survey.logs();
            soil += survey.soil();
            rock += survey.rock();
            fill += survey.fill();
            pier += survey.pier();
            lining += survey.lining();
            torches += survey.light() ? 1 : 0;
            UUID owner = cells.get(block.cell()).owner();
            if (owner != null && !owner.equals(kingdomId)) {
                double[] sum = foreign.computeIfAbsent(owner, key -> new double[2]);
                sum[0] += work.builders();
                sum[1] += work.miners();
            }
        }
        int cut = 0;
        int tunnel = 0;
        int filled = 0;
        int bridge = 0;
        for (RouteCell cell : cells) {
            switch (cell.kind()) {
                case CUT -> cut++;
                case TUNNEL -> tunnel++;
                case FILL -> filled++;
                case BRIDGE -> bridge++;
                default -> {
                }
            }
        }
        double length = length(cells);
        double caravan = RoadNetwork.ticks(length, blocksPerMinute * rules.caravanSpeed(roadClass));
        double across = RoadNetwork.ticks(straight, blocksPerMinute);
        return new RoadEstimate(length, straight, cells.size(), logs, soil, rock, fill, cut, tunnel, filled, bridge, pier, lining, torches,
                materials, builders, miners, caravan, across, foreign);
    }

    /**
     * Two estimates of legs together.
     */
    public RoadEstimate plus(RoadEstimate other) {
        int[] sum = materials.clone();
        for (int m = 0; m < sum.length; m++) {
            sum[m] += other.materials[m];
        }
        Map<UUID, double[]> both = new LinkedHashMap<>();
        foreign.forEach((key, value) -> both.put(key, value.clone()));
        other.foreign.forEach((key, value) -> both.merge(key, value.clone(), (a, b) -> new double[]{a[0] + b[0], a[1] + b[1]}));
        return new RoadEstimate(length + other.length, straight + other.straight, cells + other.cells, logs + other.logs, soil + other.soil,
                rock + other.rock, fill + other.fill, cutCells + other.cutCells, tunnelCells + other.tunnelCells, fillCells + other.fillCells,
                bridgeCells + other.bridgeCells, pierBlocks + other.pierBlocks, lining + other.lining, torches + other.torches, sum,
                builders + other.builders, miners + other.miners, caravanTicks + other.caravanTicks, straightTicks + other.straightTicks, both);
    }

    /**
     * What the search counts the road at: the seconds of its work and its items at their worth.
     */
    public double effort() {
        double sum = builders + miners;
        for (RoadLayout.Material material : RoadLayout.Material.values()) {
            sum += materials[material.ordinal()] * EarthRules.itemSeconds(material);
        }
        return sum;
    }

    /**
     * Seconds the road takes with builders of so many seconds a second and miners of so many, the rock the miners do not dig done by
     * builders: the larger of the builders' own work alone and of all of it shared.
     */
    public double time(double builderRate, double minerRate, EarthRules earth) {
        if (builderRate <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        double r = earth.rockByBuilders();
        return Math.max(builders / builderRate, (builders + r * miners) / (builderRate + r * minerRate));
    }
}
