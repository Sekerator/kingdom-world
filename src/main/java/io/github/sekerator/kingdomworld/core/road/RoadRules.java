package io.github.sekerator.kingdomworld.core.road;

/**
 * Settings of cities and roads on a server.
 *
 * @param maxCities          cities one kingdom founds at most, besides its capital
 * @param minCityDistance    blocks between two cities (and a city and a capital) at least
 * @param maxRoads           roads one kingdom keeps at most
 * @param maxRouteLength     the longest road, in blocks along the way
 * @param nodesPerTick       steps of the search for a route the server makes in one tick
 * @param maxNodes           steps one search makes at most before it gives up
 * @param chunksPerTick      chunks the search may load or look at for the first time in one tick
 * @param maxBridge          the longest bridge or embankment, in blocks
 * @param maxFill            the highest embankment, in blocks; a deeper gap needs a bridge
 * @param slopeCost          extra cost of a block up or down, against 1 for a flat block
 * @param bridgeCost         cost of a block of bridge, against 1 for a block of road on the ground
 * @param fillCost           cost of a block of embankment
 * @param foreignCost        extra cost of a block on the land of another kingdom, so that roads go round it if they can
 * @param secondsPerBlock    seconds a builder takes for a block of road, by class
 * @param wearPerDay         share of the condition a section loses a day, by class
 * @param repairShare        share of the materials of a section a repair takes
 * @param caravanSpeed       how many times faster than across country a caravan goes on a road, by class
 * @param playerSpeed        extra speed of players on a road, by class (0.2 is a fifth faster)
 * @param horseSpeed         extra speed of horses on a road, by class
 * @param speedOnRoads       whether players and horses are faster on roads
 * @param crossingDays       days a request to build across the land of another kingdom waits for an answer
 * @param autoRepair         whether builders repair worn sections by themselves
 * @param repairBelow        condition below which a section is repaired by itself
 * @param rememberConsent    whether a kingdom that once let the roads of another cross its land lets the next ones too
 * @param townPeople         citizens around a city that make it a town, with {@code townBuildings} buildings
 * @param cityPeople         citizens that make it a large city, with {@code cityBuildings} buildings
 * @param wallRadius         the radius of the walls of a village; a town gets half as much again, a large city twice
 */
public record RoadRules(int maxCities, int minCityDistance, int maxRoads, int maxRouteLength, int nodesPerTick, int maxNodes,
                        int chunksPerTick, int maxBridge, int maxFill, double slopeCost, double bridgeCost, double fillCost,
                        double foreignCost, double[] secondsPerBlock, double[] wearPerDay, double repairShare, double[] caravanSpeed,
                        double[] playerSpeed, double[] horseSpeed, boolean speedOnRoads, int crossingDays, boolean autoRepair,
                        double repairBelow, boolean rememberConsent, int townPeople, int townBuildings, int cityPeople, int cityBuildings,
                        int wallRadius) {
    public static final RoadRules DEFAULT = new Builder().build();

    public RoadRules {
        secondsPerBlock = byClass(secondsPerBlock, 0.05, 600.0);
        wearPerDay = byClass(wearPerDay, 0.0, 1.0);
        caravanSpeed = byClass(caravanSpeed, 1.0, 20.0);
        playerSpeed = byClass(playerSpeed, 0.0, 2.0);
        horseSpeed = byClass(horseSpeed, 0.0, 2.0);
        if (maxCities < 0 || minCityDistance < 0 || maxRoads < 0 || maxRouteLength < 16 || nodesPerTick < 1 || maxNodes < nodesPerTick
                || chunksPerTick < 1 || maxBridge < 0 || maxFill < 0 || !finite(slopeCost, 0) || !finite(bridgeCost, 1) || !finite(fillCost, 1)
                || !finite(foreignCost, 0) || !(repairShare >= 0 && repairShare <= 1) || crossingDays < 1
                || !(repairBelow >= 0 && repairBelow <= 1) || townPeople < 0 || townBuildings < 0 || cityPeople < townPeople
                || cityBuildings < townBuildings || wallRadius < 4 || wallRadius > 128) {
            throw new IllegalArgumentException("Road rules out of range");
        }
    }

    private static boolean finite(double value, double min) {
        return Double.isFinite(value) && value >= min;
    }

    private static double[] byClass(double[] values, double min, double max) {
        if (values == null || values.length != RoadClass.values().length) {
            throw new IllegalArgumentException("Road rules need a value for every class");
        }
        double[] copy = values.clone();
        for (double value : copy) {
            if (!Double.isFinite(value) || value < min || value > max) {
                throw new IllegalArgumentException("Road rules out of range");
            }
        }
        return copy;
    }

    public double secondsPerBlock(RoadClass value) {
        return secondsPerBlock[value.ordinal()];
    }

    public double wearPerDay(RoadClass value) {
        return wearPerDay[value.ordinal()];
    }

    public double caravanSpeed(RoadClass value) {
        return caravanSpeed[value.ordinal()];
    }

    public double playerSpeed(RoadClass value) {
        return speedOnRoads ? playerSpeed[value.ordinal()] : 0.0;
    }

    public double horseSpeed(RoadClass value) {
        return speedOnRoads ? horseSpeed[value.ordinal()] : 0.0;
    }

    /**
     * Rules built step by step, from the defaults.
     */
    public static final class Builder {
        public int maxCities = 6;
        public int minCityDistance = 160;
        public int maxRoads = 32;
        public int maxRouteLength = 1_536;
        public int nodesPerTick = 3_000;
        public int maxNodes = 400_000;
        public int chunksPerTick = 6;
        public int maxBridge = 32;
        public int maxFill = 3;
        public double slopeCost = 1.5;
        public double bridgeCost = 4.0;
        public double fillCost = 2.5;
        public double foreignCost = 2.0;
        public double[] secondsPerBlock = {1.0, 3.0, 5.0};
        public double[] wearPerDay = {0.004, 0.002, 0.001};
        public double repairShare = 0.25;
        public double[] caravanSpeed = {1.6, 2.5, 3.5};
        public double[] playerSpeed = {0.10, 0.20, 0.30};
        public double[] horseSpeed = {0.10, 0.20, 0.30};
        public boolean speedOnRoads = true;
        public int crossingDays = 3;
        public boolean autoRepair = true;
        public double repairBelow = 0.5;
        public boolean rememberConsent = true;
        public int townPeople = 12;
        public int townBuildings = 6;
        public int cityPeople = 40;
        public int cityBuildings = 20;
        public int wallRadius = 24;

        public RoadRules build() {
            return new RoadRules(maxCities, minCityDistance, maxRoads, maxRouteLength, nodesPerTick, maxNodes, chunksPerTick, maxBridge, maxFill,
                    slopeCost, bridgeCost, fillCost, foreignCost, secondsPerBlock, wearPerDay, repairShare, caravanSpeed, playerSpeed, horseSpeed,
                    speedOnRoads, crossingDays, autoRepair, repairBelow, rememberConsent, townPeople, townBuildings, cityPeople, cityBuildings,
                    wallRadius);
        }
    }
}
