package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.Limits;
import io.github.sekerator.kingdomworld.core.road.BanditRules;
import io.github.sekerator.kingdomworld.core.road.RoadRules;

import java.util.List;

/**
 * The road and robber rules of the running server, built from the server config and refreshed once a second like the
 * other rules; game tests may replace them. Before the config is loaded the defaults count.
 */
public final class RoadSettings {
    private static RoadRules roadOverride;
    private static BanditRules banditOverride;
    private static RoadRules roads;
    private static BanditRules bandits;
    private static Boolean physicalOverride;

    private RoadSettings() {
    }

    public static RoadRules rules() {
        if (roadOverride != null) {
            return roadOverride;
        }
        RoadRules value = roads;
        if (value == null) {
            value = roadsFromConfig();
            roads = value;
        }
        return value;
    }

    /**
     * The rules of cities and roads for a kingdom: those of the config for a kingdom of players, for a kingdom of the world its
     * own number of roads and no limit of cities but its plan.
     */
    public static RoadRules rulesFor(net.minecraft.server.MinecraftServer server, java.util.UUID kingdomId) {
        RoadRules rules = rules();
        if (!io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, kingdomId)) {
            return rules;
        }
        int most;
        try {
            most = io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig.SERVER.roads.npcMaxRoads.get();
        } catch (IllegalStateException e) {
            most = 200;
        }
        return rules.forWorldKingdom(Math.max(rules.maxRoads(), most));
    }

    public static BanditRules bandits() {
        if (banditOverride != null) {
            return banditOverride;
        }
        BanditRules value = bandits;
        if (value == null) {
            value = banditsFromConfig();
            bandits = value;
        }
        return value;
    }

    /**
     * Whether caravans near players walk the roads.
     */
    public static boolean physicalCaravans() {
        if (physicalOverride != null) {
            return physicalOverride;
        }
        try {
            return KingdomWorldConfig.SERVER.roads.physicalCaravans.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static List<? extends String> weapons() {
        try {
            return KingdomWorldConfig.SERVER.roads.banditWeapons.get();
        } catch (IllegalStateException e) {
            return List.of("#minecraft:swords=1");
        }
    }

    public static List<? extends String> armor() {
        try {
            return KingdomWorldConfig.SERVER.roads.banditArmor.get();
        } catch (IllegalStateException e) {
            return List.of();
        }
    }

    public static void refresh() {
        roads = roadsFromConfig();
        bandits = banditsFromConfig();
    }

    public static void reset() {
        roads = null;
        bandits = null;
        roadOverride = null;
        banditOverride = null;
        physicalOverride = null;
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(RoadRules roadRules, BanditRules banditRules) {
        roadOverride = roadRules;
        banditOverride = banditRules;
    }

    public static void overridePhysical(Boolean value) {
        physicalOverride = value;
    }

    private static double[] three(List<? extends Double> values, double[] fallback) {
        if (values == null || values.size() != 3) {
            return fallback;
        }
        return new double[]{values.get(0), values.get(1), values.get(2)};
    }

    /**
     * The road rules of the config, ready to be changed by a game test.
     */
    public static RoadRules.Builder configuredRoads() {
        RoadRules.Builder b = new RoadRules.Builder();
        try {
            KingdomWorldConfig.Roads c = KingdomWorldConfig.SERVER.roads;
            // The old defaults of a configuration written before (6 cities, 32 roads, 160 blocks) are read as the new ones.
            b.maxCities = Limits.effective(Limits.CITIES, c.maxCities.get());
            b.wallRadius = c.wallRadius.get();
            // Never closer than the walls of two large cities need: from the wall radius, which an operator may have raised.
            b.minCityDistance = Limits.cityDistance(c.minCityDistance.get(), b.wallRadius);
            b.maxRoads = Limits.effective(Limits.ROADS, c.maxRoads.get());
            b.maxRouteLength = c.maxRouteLength.get();
            b.nodesPerTick = c.searchStepsPerTick.get();
            b.maxNodes = Math.max(c.maxSearchSteps.get(), b.nodesPerTick);
            b.chunksPerTick = c.searchChunksPerTick.get();
            b.maxBridge = c.maxBridge.get();
            b.maxFill = c.maxFill.get();
            b.secondsPerBlock = three(c.secondsPerBlock.get(), b.secondsPerBlock);
            b.wearPerDay = three(c.wearPerDay.get(), b.wearPerDay);
            b.repairShare = c.repairShare.get();
            b.caravanSpeed = three(c.caravanSpeed.get(), b.caravanSpeed);
            b.speedOnRoads = c.speedOnRoads.get();
            b.playerSpeed = three(c.playerSpeed.get(), b.playerSpeed);
            b.horseSpeed = three(c.horseSpeed.get(), b.horseSpeed);
            b.crossingDays = c.crossingDays.get();
            b.autoRepair = c.autoRepair.get();
            b.repairBelow = c.repairBelow.get();
            b.rememberConsent = c.rememberConsent.get();
            b.townPeople = c.townPeople.get();
            b.townBuildings = c.townBuildings.get();
            b.cityPeople = Math.max(c.cityPeople.get(), b.townPeople);
            b.cityBuildings = Math.max(c.cityBuildings.get(), b.townBuildings);
        } catch (IllegalStateException e) {
            // The config is not loaded yet: the defaults count.
        }
        return b;
    }

    /**
     * The robber rules of the config, ready to be changed by a game test.
     */
    public static BanditRules.Builder configuredBandits() {
        BanditRules.Builder b = new BanditRules.Builder();
        try {
            KingdomWorldConfig.Roads c = KingdomWorldConfig.SERVER.roads;
            b.enabled = c.bandits.get();
            b.maxCamps = c.maxCamps.get();
            b.minDistance = c.campMinDistance.get();
            b.spawnChance = c.campSpawnChance.get();
            b.startBandits = c.campStartBandits.get();
            b.maxBandits = Math.max(c.campMaxBandits.get(), b.startBandits);
            b.growthChance = c.campGrowthChance.get();
            b.banditStrength = c.banditStrength.get();
            b.leaderStrength = c.leaderStrength.get();
            b.ambushChance = c.ambushChance.get();
            b.campReach = c.campReach.get();
            b.classFactor = three(c.ambushByClass.get(), b.classFactor);
            b.garrisonFactor = c.garrisonFactor.get();
            b.patrolFactor = c.patrolFactor.get();
            b.ownLandFactor = c.ownLandFactor.get();
            b.protectRadius = c.protectRadius.get();
            b.raidParty = c.raidParty.get();
            b.unguardedLoot = c.unguardedLoot.get();
            b.merchantDeath = c.merchantDeath.get();
            b.maxPhysical = c.maxPhysicalBandits.get();
        } catch (IllegalStateException e) {
            // The config is not loaded yet: the defaults count.
        }
        return b;
    }

    private static RoadRules roadsFromConfig() {
        try {
            return configuredRoads().build();
        } catch (IllegalArgumentException e) {
            return RoadRules.DEFAULT;
        }
    }

    private static BanditRules banditsFromConfig() {
        try {
            return configuredBandits().build();
        } catch (IllegalArgumentException e) {
            return BanditRules.DEFAULT;
        }
    }
}
