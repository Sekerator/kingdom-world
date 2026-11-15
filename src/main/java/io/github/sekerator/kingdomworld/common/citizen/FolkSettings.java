package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.road.FolkRules;
import io.github.sekerator.kingdomworld.core.road.FolkWorkRules;

/**
 * The rules of the townsfolk of the running server, built from the server config and refreshed with the citizen rules.
 */
public final class FolkSettings {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static FolkRules override;
    private static FolkRules cached;
    private static FolkWorkRules workOverride;
    private static FolkWorkRules workCached;
    private static boolean duesTold;

    private FolkSettings() {
    }

    public static FolkRules rules() {
        if (override != null) {
            return override;
        }
        FolkRules rules = cached;
        if (rules == null) {
            rules = fromConfig();
            cached = rules;
        }
        return rules;
    }

    /**
     * Rereads the config; called regularly by the server tick so that config changes take effect.
     */
    static void refresh() {
        cached = fromConfig();
        workCached = workFromConfig();
    }

    static void reset() {
        cached = null;
        override = null;
        workCached = null;
        workOverride = null;
    }

    /**
     * How the townsfolk work and earn, and whether the kingdoms of the world keep shops of citizens.
     */
    public static FolkWorkRules work() {
        if (workOverride != null) {
            return workOverride;
        }
        FolkWorkRules rules = workCached;
        if (rules == null) {
            rules = workFromConfig();
            workCached = rules;
        }
        return rules;
    }

    /**
     * Replaces the configured rules of the work of the townsfolk, for game tests; {@code null} restores the config.
     */
    public static void overrideWork(FolkWorkRules rules) {
        workOverride = rules;
    }

    private static FolkWorkRules workFromConfig() {
        try {
            KingdomWorldConfig.CityEconomy config = KingdomWorldConfig.SERVER.cityEconomy;
            return new FolkWorkRules(config.folkAtPosts.get(), config.folkPostRatePercent.get(), config.folkHandPercent.get(),
                    config.folkUsualIncome.get(), config.folkIdleMaxPercent.get(), config.folkLeaveIdlePercent.get(), config.folkLeavePercent.get(),
                    config.folkWageReserveDays.get(), config.folkReleaseMasterPercent.get(), config.worldShops.get());
        } catch (RuntimeException e) {
            return FolkWorkRules.DEFAULT;
        }
    }

    private static FolkRules fromConfig() {
        try {
            KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
            if (config.folkDues.get() > 0 && !duesTold) {
                // The townsfolk pay the tithe of what they earn and nothing out of nothing: the old key is read and does nothing.
                duesTold = true;
                LOGGER.info("KingdomWorld: citizens.folkDues is no more, the townsfolk pay the tithe of what they earn instead");
            }
            return new FolkRules(config.namedPerCity.get(), config.folkPerBed.get(), config.folkDensity.get());
        } catch (RuntimeException e) {
            // The config is not loaded (a unit check or a client without a server): the defaults hold.
            return FolkRules.DEFAULT;
        }
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(FolkRules rules) {
        override = rules;
    }
}
