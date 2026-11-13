package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.npc.NpcBuildRules;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;

import java.util.List;

/**
 * The rules of the kingdoms of the world of the running server, built from the server config and refreshed once a second like
 * the other rules; game tests may replace them. Before the config is loaded the defaults count.
 */
public final class NpcSettings {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Whether the log was told that the radius of the config is too large for the plan of states. */
    private static boolean radiusWarned;
    private static NpcRules override;
    private static NpcRules rules;
    private static io.github.sekerator.kingdomworld.core.npc.RulerRules rulerOverride;
    private static io.github.sekerator.kingdomworld.core.npc.RulerRules rulers;
    private static NpcBuildRules buildOverride;
    private static NpcBuildRules build;

    private NpcSettings() {
    }

    /** The plan of a new world given by a game test, or null for the config. */
    private static String planOverride;

    /**
     * Gives the way a new world plans its kingdoms for game tests, or null for the config.
     */
    public static void overridePlan(String plan) {
        planOverride = plan;
    }

    /**
     * Whether a new world lays out large states with provinces ("states", the default) or places capitals as before ("legacy").
     */
    public static boolean states() {
        if (planOverride != null) {
            return "states".equals(planOverride);
        }
        try {
            return "states".equals(KingdomWorldConfig.SERVER.npcKingdoms.plan.get());
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /**
     * The rules of the plan of states around a spawn, from the config.
     */
    public static io.github.sekerator.kingdomworld.core.land.StateRules stateRules(int spawnX, int spawnZ) {
        NpcRules npc = rules();
        io.github.sekerator.kingdomworld.core.land.StateRules d = io.github.sekerator.kingdomworld.core.land.StateRules.defaults(spawnX, spawnZ);
        try {
            KingdomWorldConfig.NpcKingdoms c = KingdomWorldConfig.SERVER.npcKingdoms;
            List<? extends Integer> chunks = c.stateChunks.get();
            int[] least = d.minChunks();
            int[] most = d.maxChunks();
            if (chunks.size() == 6) {
                for (int i = 0; i < 3; i++) {
                    least[i] = chunks.get(2 * i);
                    most[i] = Math.max(chunks.get(2 * i), chunks.get(2 * i + 1));
                }
            }
            int radius = io.github.sekerator.kingdomworld.core.land.StateRules.planRadius(npc.radius());
            if (radius != npc.radius() && !radiusWarned) {
                radiusWarned = true;
                LOGGER.warn("The radius {} of the kingdoms of the world is too large for the plan of states: it takes {} blocks", npc.radius(),
                        radius);
            }
            return new io.github.sekerator.kingdomworld.core.land.StateRules(spawnX, spawnZ, c.spawnZone.get(), radius, c.gapBlocks.get(),
                    c.neutralShare.get(), d.ringShare(), quota(npc.sizeQuota()), least, most);
        } catch (IllegalStateException e) {
            return d;
        }
    }

    private static int[] quota(int[] quota) {
        int left = io.github.sekerator.kingdomworld.core.land.StateRules.MAX_STATES;
        for (int i = 2; i >= 0; i--) {
            quota[i] = Math.min(quota[i], left);
            left -= quota[i];
        }
        return quota;
    }

    /**
     * The sizes of the provinces of the plan of states, from the config.
     */
    public static io.github.sekerator.kingdomworld.core.land.ProvinceRules provinceRules() {
        int chunks = 1600;
        try {
            chunks = KingdomWorldConfig.SERVER.npcKingdoms.provinceChunks.get();
        } catch (IllegalStateException e) {
            // The defaults while the config is not loaded.
        }
        int cells = Math.max(4, Math.round(chunks / 16f));
        return new io.github.sekerator.kingdomworld.core.land.ProvinceRules(cells, Math.max(1, cells / 2), Math.max(cells, cells * 8 / 5));
    }

    public static NpcRules rules() {
        if (override != null) {
            return override;
        }
        NpcRules value = rules;
        if (value == null) {
            value = fromConfig();
            rules = value;
        }
        return value;
    }

    /**
     * The rules of the rulers of the kingdoms of the world.
     */
    public static io.github.sekerator.kingdomworld.core.npc.RulerRules rulers() {
        if (rulerOverride != null) {
            return rulerOverride;
        }
        io.github.sekerator.kingdomworld.core.npc.RulerRules value = rulers;
        if (value == null) {
            value = rulersFromConfig();
            rulers = value;
        }
        return value;
    }

    /**
     * How the buildings of the kingdoms of the world are raised.
     */
    public static NpcBuildRules build() {
        if (buildOverride != null) {
            return buildOverride;
        }
        NpcBuildRules value = build;
        if (value == null) {
            value = buildFromConfig();
            build = value;
        }
        return value;
    }

    /**
     * Replaces the configured rules of raising, for game tests; {@code null} restores the config.
     */
    public static void overrideBuild(NpcBuildRules value) {
        buildOverride = value;
    }

    public static void refresh() {
        rules = fromConfig();
        rulers = rulersFromConfig();
        build = buildFromConfig();
    }

    public static void reset() {
        rules = null;
        override = null;
        rulers = null;
        rulerOverride = null;
        build = null;
        buildOverride = null;
        planOverride = null;
    }

    private static NpcBuildRules buildFromConfig() {
        NpcBuildRules d = NpcBuildRules.DEFAULT;
        try {
            KingdomWorldConfig.NpcKingdoms c = KingdomWorldConfig.SERVER.npcKingdoms;
            return new NpcBuildRules(NpcBuildRules.Mode.byId(c.buildMode.get()).orElse(d.mode()), c.backgroundBlocksPerTick.get(),
                    c.backgroundChunksPerTick.get(), c.backgroundPauseTickMillis.get(), c.foundingClaimsPerTick.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return d;
        }
    }

    /**
     * Replaces the configured rules of the rulers, for game tests; {@code null} restores the config.
     */
    public static void overrideRulers(io.github.sekerator.kingdomworld.core.npc.RulerRules value) {
        rulerOverride = value;
    }

    private static io.github.sekerator.kingdomworld.core.npc.RulerRules rulersFromConfig() {
        io.github.sekerator.kingdomworld.core.npc.RulerRules d = io.github.sekerator.kingdomworld.core.npc.RulerRules.DEFAULT;
        try {
            KingdomWorldConfig.NpcKingdoms c = KingdomWorldConfig.SERVER.npcKingdoms;
            List<io.github.sekerator.kingdomworld.core.npc.RulerRules.RoleWant> roles =
                    io.github.sekerator.kingdomworld.core.npc.RulerRules.RoleWant.parse(c.rulerRoles.get());
            return new io.github.sekerator.kingdomworld.core.npc.RulerRules(c.rulerTurnEveryDays.get(), c.rulerDecisionsPerTurn.get(),
                    c.rulerDecreesPerDay.get(), ints(c.rulerMaxChunks.get(), d.maxChunks()), c.rulerChunksPerClaim.get(), c.rulerFreeBeds.get(),
                    c.rulerStorageFullPercent.get(), roles, c.rulerTownPopulation.get(), c.rulerMaxTowns.get(), c.rulerReserveDays.get(),
                    c.rulerSoldiersPermille.get(), c.rulerSlackPermille.get(), c.rulerRoadDistance.get(), c.rulerFestivalMood.get(),
                    c.rulerGrowthChunks.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return d;
        }
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(NpcRules value) {
        override = value;
    }

    private static NpcRules fromConfig() {
        NpcRules d = NpcRules.DEFAULT;
        try {
            KingdomWorldConfig.NpcKingdoms c = KingdomWorldConfig.SERVER.npcKingdoms;
            int[] money = ints(c.startingMoney.get(), new int[]{(int) d.money()[0], (int) d.money()[1], (int) d.money()[2]});
            return new NpcRules(c.generate.get(), c.count.get(), c.radius.get(), c.minDistance.get(), c.minSpawnDistance.get(),
                    c.triggerChunks.get(), ints(c.sizeQuota.get(), d.sizeQuota()), ints(c.sizeWeights.get(), d.sizeWeights()),
                    ints(c.landRadius.get(), d.landRadius()),
                    ints(c.towns.get(), d.towns()), c.townDistance.get(), new long[]{money[0], money[1], money[2]}, c.startingFoodDays.get(),
                    ints(c.startingMaterials.get(), d.materials()), ints(c.soldiers.get(), d.soldiers()), c.blocksPerTick.get(),
                    c.chunksPerTick.get(), c.maxInhabitedMinutes.get(), d.attemptsPerSite(), ints(c.guards.get(), d.guards()),
                    ints(c.army.get(), d.army()), ints(c.townGuards.get(), d.townGuards()), ints(c.armyLeast.get(), d.armyLeast()),
                    ints(c.armyMost.get(), d.armyMost()), c.armyPerDay.get(), c.campSoldiers.get(), c.campBodies.get(), c.armyBodies.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return d;
        }
    }

    private static int[] ints(List<? extends Integer> values, int[] fallback) {
        if (values == null || values.size() != 3) {
            return fallback;
        }
        return new int[]{values.get(0), values.get(1), values.get(2)};
    }
}
