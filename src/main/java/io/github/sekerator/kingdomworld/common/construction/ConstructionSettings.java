package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.construction.BuildingRules;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;

/**
 * The construction rules of the running server, built from the server config and refreshed once a second like the other
 * rules; game tests may replace them. Before the config is loaded (data packs load first) the defaults count.
 */
public final class ConstructionSettings {
    private static ConstructionRules override;
    private static ConstructionRules cached;
    private static BuildingRules buildingOverride;
    private static BuildingRules buildingCached;

    private ConstructionSettings() {
    }

    public static ConstructionRules rules() {
        if (override != null) {
            return override;
        }
        ConstructionRules rules = cached;
        if (rules == null) {
            rules = fromConfig();
            cached = rules;
        }
        return rules;
    }

    public static void refresh() {
        cached = fromConfig();
        buildingCached = buildingsFromConfig();
    }

    public static void reset() {
        cached = null;
        override = null;
        buildingCached = null;
        buildingOverride = null;
    }

    /**
     * The settings of the buildings of the register: roles, repairs, demolitions, saved blueprints.
     */
    public static BuildingRules buildings() {
        if (buildingOverride != null) {
            return buildingOverride;
        }
        BuildingRules rules = buildingCached;
        if (rules == null) {
            rules = buildingsFromConfig();
            buildingCached = rules;
        }
        return rules;
    }

    /**
     * Replaces the configured settings of buildings, for game tests; {@code null} restores the config.
     */
    public static void overrideBuildings(BuildingRules rules) {
        buildingOverride = rules;
    }

    private static BuildingRules buildingsFromConfig() {
        KingdomWorldConfig.Server c = KingdomWorldConfig.SERVER;
        try {
            return new BuildingRules(c.roleIntegrityPercent.get(), c.autoRepairBelowPercent.get(), c.demolitionLossPercent.get(),
                    c.maxBlueprintsPerKingdom.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return BuildingRules.DEFAULT;
        }
    }

    /**
     * The widest and longest earthworks may be, in blocks.
     */
    public static int earthworkSize() {
        try {
            return KingdomWorldConfig.SERVER.earthworkMaxSize.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return 64;
        }
    }

    /**
     * Whether places of constructions far from players are loaded for a while to be built.
     */
    public static boolean siteLoading() {
        try {
            return KingdomWorldConfig.SERVER.siteLoading.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * Chunks the loading of places asks for in one tick at most.
     */
    public static int siteLoadChunksPerTick() {
        try {
            return KingdomWorldConfig.SERVER.siteLoadChunksPerTick.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return 2;
        }
    }

    /**
     * The average tick above which no place is loaded, in milliseconds.
     */
    public static int siteLoadPauseMillis() {
        try {
            return KingdomWorldConfig.SERVER.siteLoadPauseMillis.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return 35;
        }
    }

    /**
     * Whether builders without a construction help constructions no post reaches.
     */
    public static boolean helpOtherCities() {
        try {
            return KingdomWorldConfig.SERVER.helpOtherCities.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * How fast builders help a city no road of the kingdom leads to, against 1 by road.
     */
    public static double remoteSpeed() {
        try {
            return KingdomWorldConfig.SERVER.remoteSpeed.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return 0.75;
        }
    }

    /**
     * How far earthworks may dig down or fill up from the ground of a column, in blocks.
     */
    public static int earthworkDepth() {
        try {
            return KingdomWorldConfig.SERVER.earthworkMaxHeight.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return 32;
        }
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(ConstructionRules rules) {
        override = rules;
    }

    private static ConstructionRules fromConfig() {
        KingdomWorldConfig.Server c = KingdomWorldConfig.SERVER;
        try {
            double reach = c.builderReach.get();
            // The old default of a configuration written before (4) is read as no limit.
            int most = io.github.sekerator.kingdomworld.core.Limits.effective(io.github.sekerator.kingdomworld.core.Limits.CONSTRUCTIONS,
                    c.maxConstructionsPerKingdom.get());
            return new ConstructionRules(most, c.builderRadius.get(), reach, Math.max(reach, c.builderFarReach.get()),
                    c.builderGiveUpSeconds.get() * 20, (int) Math.max(1, Math.round(c.builderSecondsPerBlock.get() * 20.0)),
                    c.builderFallChance.get(), c.templateMaxWidth.get(), c.templateMaxHeight.get(), c.templateMaxLength.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ConstructionRules.DEFAULT;
        }
    }
}
