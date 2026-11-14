package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.road.FolkRules;

/**
 * The rules of the townsfolk of the running server, built from the server config and refreshed with the citizen rules.
 */
public final class FolkSettings {
    private static FolkRules override;
    private static FolkRules cached;

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
    }

    static void reset() {
        cached = null;
        override = null;
    }

    private static FolkRules fromConfig() {
        try {
            KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
            return new FolkRules(config.namedPerCity.get(), config.folkPerBed.get(), config.folkDensity.get(), config.folkDues.get());
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
