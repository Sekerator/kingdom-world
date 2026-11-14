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
            return new ConstructionRules(c.maxConstructionsPerKingdom.get(), c.builderRadius.get(), reach, Math.max(reach, c.builderFarReach.get()),
                    c.builderGiveUpSeconds.get() * 20, (int) Math.max(1, Math.round(c.builderSecondsPerBlock.get() * 20.0)),
                    c.builderFallChance.get(), c.templateMaxWidth.get(), c.templateMaxHeight.get(), c.templateMaxLength.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ConstructionRules.DEFAULT;
        }
    }
}
