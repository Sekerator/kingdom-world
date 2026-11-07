package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;

/**
 * The construction rules of the running server, built from the server config and refreshed once a second like the other
 * rules; game tests may replace them. Before the config is loaded (data packs load first) the defaults count.
 */
public final class ConstructionSettings {
    private static ConstructionRules override;
    private static ConstructionRules cached;

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
    }

    public static void reset() {
        cached = null;
        override = null;
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
