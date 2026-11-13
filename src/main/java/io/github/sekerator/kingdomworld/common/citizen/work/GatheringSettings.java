package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import io.github.sekerator.kingdomworld.core.citizen.MineRules;
import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;

/**
 * The gathering rules of the running server, built from the server config and refreshed once per second like
 * the citizen rules. Readable only while a server runs; game tests may replace them.
 */
public final class GatheringSettings {
    private static GatheringRules override;
    private static GatheringRules cached;
    private static MineRules mineOverride;
    private static MineRules mineCached;

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
     * The rules of mines of the running server.
     */
    public static MineRules mine() {
        if (mineOverride != null) {
            return mineOverride;
        }
        MineRules rules = mineCached;
        if (rules == null) {
            rules = mineFromConfig();
            mineCached = rules;
        }
        return rules;
    }

    /**
     * Rereads the config.
     */
    public static void refresh() {
        cached = fromConfig();
        MineRules mine = mineFromConfig();
        // The plans of mines are kept by their rules: the same settings stay the same object.
        if (!mine.equals(mineCached)) {
            mineCached = mine;
        }
    }

    public static void reset() {
        cached = null;
        override = null;
        mineCached = null;
        mineOverride = null;
    }

    private static MineRules mineFromConfig() {
        KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
        return new MineRules(config.minerCorridorLength.get(), config.minerBranchLength.get(), config.minerVeinBlocks.get(),
                MineRules.parseDepths(config.minerOreDepths.get()));
    }

    /**
     * Replaces the configured rules of mines, for game tests; {@code null} restores the config.
     */
    public static void overrideMine(MineRules rules) {
        mineOverride = rules;
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
