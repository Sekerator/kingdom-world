package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;

/**
 * The gathering rules of the running server, built from the server config and refreshed once per second like
 * the citizen rules. Readable only while a server runs; game tests may replace them.
 */
public final class GatheringSettings {
    private static GatheringRules override;
    private static GatheringRules cached;

    private GatheringSettings() {
    }

    public static GatheringRules rules() {
        if (override != null) {
            return override;
        }
        GatheringRules rules = cached;
        if (rules == null) {
            rules = fromConfig();
            cached = rules;
        }
        return rules;
    }

    /**
     * Rereads the config.
     */
    public static void refresh() {
        cached = fromConfig();
    }

    public static void reset() {
        cached = null;
        override = null;
    }

    private static GatheringRules fromConfig() {
        KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
        int max = config.workPostMaxRadius.get();
        return new GatheringRules(Math.min(config.workPostDefaultRadius.get(), max), max, config.workPostMaxSeats.get(),
                config.maxTreeBlocks.get(), config.mineLevels.get(), config.maxHerdSize.get(), config.carrierRange.get(),
                config.carrierItemsPerMinute.get(), config.taskRiskMultiplier.get(), TaskRisks.parseAll(config.professionRisks.get()));
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(GatheringRules rules) {
        override = rules;
    }
}
