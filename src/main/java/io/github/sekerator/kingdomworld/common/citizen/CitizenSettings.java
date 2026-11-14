package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRules;

/**
 * The citizen rules of the running server, built from the server config and refreshed once per second, so that
 * the many checks of the bodies do not rebuild them. Readable only while a server runs.
 */
public final class CitizenSettings {
    private static CitizenRules override;
    private static CitizenRules cached;

    private CitizenSettings() {
    }

    public static CitizenRules rules() {
        if (override != null) {
            return override;
        }
        CitizenRules rules = cached;
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

    private static CitizenRules fromConfig() {
        KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
        return new CitizenRules(config.citizenActivationChunks.get(), config.maxActiveNpcs.get(),
                config.simulationIntervalTicks.get(), config.growthFoodDays.get(),
                config.growthIntervalMinutes.get() * 1_200L, config.starvationCanKill.get(), config.dailyNutrition.get(),
                config.infirmaryCapacity.get(), config.woundedHealthPercent.get() / 100.0f,
                io.github.sekerator.kingdomworld.core.Limits.effective(io.github.sekerator.kingdomworld.core.Limits.CITIZENS,
                        config.maxCitizensPerKingdom.get()), config.farmRadius.get());
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(CitizenRules rules) {
        override = rules;
    }
}
