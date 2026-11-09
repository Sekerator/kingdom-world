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
    private static NpcRules override;
    private static NpcRules rules;
    private static io.github.sekerator.kingdomworld.core.npc.RulerRules rulerOverride;
    private static io.github.sekerator.kingdomworld.core.npc.RulerRules rulers;
    private static NpcBuildRules buildOverride;
    private static NpcBuildRules build;

    private NpcSettings() {
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
                    c.triggerChunks.get(), ints(c.sizeWeights.get(), d.sizeWeights()), ints(c.landRadius.get(), d.landRadius()),
                    ints(c.towns.get(), d.towns()), c.townDistance.get(), new long[]{money[0], money[1], money[2]}, c.startingFoodDays.get(),
                    ints(c.startingMaterials.get(), d.materials()), ints(c.soldiers.get(), d.soldiers()), c.blocksPerTick.get(),
                    c.chunksPerTick.get(), c.maxInhabitedMinutes.get(), d.attemptsPerSite());
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
