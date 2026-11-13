package io.github.sekerator.kingdomworld.core.road;

/**
 * Settings of robbers on a server: where their camps appear and how they grow, how often they fall upon caravans and how
 * strong they are.
 *
 * @param enabled          whether camps of robbers appear at all
 * @param maxCamps         camps in a world at most
 * @param minDistance      blocks between a camp and the land of any kingdom at least
 * @param spawnChance      chance a day that a new camp appears, while there are fewer than the most
 * @param startBandits     robbers of a new camp
 * @param maxBandits       robbers a camp grows to at most
 * @param growthChance     chance a day that a camp nobody disturbs gains a robber
 * @param banditStrength   strength of a robber in a fight far from players (a recruit with bare hands has 10)
 * @param leaderStrength   strength of the leader of a camp
 * @param ambushChance     chance of an ambush on a hundred blocks of way next to a full camp, across country
 * @param campReach        blocks around a camp in which its robbers lie in wait
 * @param classFactor      how much a road lowers the chance of an ambush, by class (across country it is 1)
 * @param garrisonFactor   how much a squad that holds or guards nearby lowers it
 * @param patrolFactor     how much a squad patrolling nearby lowers it
 * @param ownLandFactor    how much the land of a kingdom lowers it
 * @param protectRadius    blocks around a squad in which it protects the way
 * @param raidParty        robbers of an ambush at most
 * @param unguardedLoot    share of the goods robbers take from a caravan without guards
 * @param merchantDeath    chance that a merchant of the seller dies in an ambush the caravan loses
 * @param maxPhysical      robbers of a camp that appear in the world near players at most
 */
public record BanditRules(boolean enabled, int maxCamps, int minDistance, double spawnChance, int startBandits, int maxBandits,
                          double growthChance, double banditStrength, double leaderStrength, double ambushChance, int campReach,
                          double[] classFactor, double garrisonFactor, double patrolFactor, double ownLandFactor, int protectRadius,
                          int raidParty, double unguardedLoot, double merchantDeath, int maxPhysical) {
    public static final BanditRules DEFAULT = new Builder().build();

    public BanditRules {
        if (classFactor == null || classFactor.length != RoadClass.values().length) {
            throw new IllegalArgumentException("Bandit rules need a factor for every class of road");
        }
        classFactor = classFactor.clone();
        for (double factor : classFactor) {
            if (!share(factor)) {
                throw new IllegalArgumentException("Bandit rules out of range");
            }
        }
        if (maxCamps < 0 || minDistance < 0 || !share(spawnChance) || startBandits < 1 || maxBandits < startBandits || !share(growthChance)
                || !(banditStrength > 0) || !Double.isFinite(banditStrength) || !(leaderStrength > 0) || !Double.isFinite(leaderStrength)
                || !share(ambushChance) || campReach < 1 || !share(garrisonFactor) || !share(patrolFactor) || !share(ownLandFactor)
                || protectRadius < 0 || raidParty < 1 || !share(unguardedLoot) || !share(merchantDeath) || maxPhysical < 0) {
            throw new IllegalArgumentException("Bandit rules out of range");
        }
    }

    private static boolean share(double value) {
        return value >= 0.0 && value <= 1.0;
    }

    public double classFactor(RoadClass value) {
        return classFactor[value.ordinal()];
    }

    /**
     * Rules built step by step, from the defaults.
     */
    public static final class Builder {
        public boolean enabled = true;
        /** The states of the world have wide wilds: twice the camps of before, in new configs. */
        public int maxCamps = 16;
        public int minDistance = 192;
        public double spawnChance = 0.2;
        public int startBandits = 3;
        public int maxBandits = 12;
        public double growthChance = 0.35;
        public double banditStrength = 18.0;
        public double leaderStrength = 60.0;
        public double ambushChance = 0.03;
        public int campReach = 320;
        public double[] classFactor = {0.8, 0.6, 0.45};
        public double garrisonFactor = 0.35;
        public double patrolFactor = 0.5;
        public double ownLandFactor = 0.6;
        public int protectRadius = 96;
        public int raidParty = 6;
        public double unguardedLoot = 0.75;
        public double merchantDeath = 0.25;
        public int maxPhysical = 8;

        public BanditRules build() {
            return new BanditRules(enabled, maxCamps, minDistance, spawnChance, startBandits, maxBandits, growthChance, banditStrength,
                    leaderStrength, ambushChance, campReach, classFactor, garrisonFactor, patrolFactor, ownLandFactor, protectRadius, raidParty,
                    unguardedLoot, merchantDeath, maxPhysical);
        }
    }
}
