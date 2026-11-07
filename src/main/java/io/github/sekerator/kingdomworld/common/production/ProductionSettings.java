package io.github.sekerator.kingdomworld.common.production;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.production.ProductionRules;

/**
 * The production rules of the running server, built from the server config and refreshed once per second like
 * the other rules. Readable only while a server runs; game tests may replace them.
 */
public final class ProductionSettings {
    private static ProductionRules override;
    private static ProductionRules cached;

    private ProductionSettings() {
    }

    public static ProductionRules rules() {
        if (override != null) {
            return override;
        }
        ProductionRules rules = cached;
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

    private static ProductionRules fromConfig() {
        KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
        return new ProductionRules(config.craftSeconds.get() * 20, config.cookFoodDaysTarget.get(), config.orderChainDepth.get(),
                config.maxOrdersPerKingdom.get());
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(ProductionRules rules) {
        override = rules;
    }
}
