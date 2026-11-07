package io.github.sekerator.kingdomworld.common.life;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.life.LifeRules;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import org.slf4j.Logger;

/**
 * The rules of the life of cities of the running server, built from the server config and refreshed once per second like the
 * other rules; game tests may replace them. Times out of order in the config fall back to the defaults with a warning.
 */
public final class LifeSettings {
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static LifeRules override;
    private static LifeRules cached;
    private static boolean warned;

    private LifeSettings() {
    }

    public static LifeRules rules() {
        if (override != null) {
            return override;
        }
        LifeRules rules = cached;
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
        warned = false;
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(LifeRules rules) {
        override = rules;
    }

    public static LifeRules.Builder configured() {
        LifeRules.Builder b = new LifeRules.Builder();
        KingdomWorldConfig.Life c = KingdomWorldConfig.SERVER.life;
        b.workStart = c.workStart.get();
        b.eveningStart = c.eveningStart.get();
        b.sleepStart = c.sleepStart.get();
        b.wakeUp = c.wakeUp.get();
        b.spreadTicks = c.spreadTicks.get();
        b.tavernShare = c.tavernShare.get();
        b.tavernMood = c.tavernMood.get();
        b.tavernMoodDays = c.tavernMoodDays.get();
        b.sickChance = c.sickChance.get();
        b.sickFactor = c.sickFactor.get();
        b.sickMood = c.sickMood.get();
        b.sickWorkPercent = c.sickWorkPercent.get();
        b.infirmaryDays = c.infirmaryDays.get();
        b.homeDays = Math.max(c.homeDays.get(), b.infirmaryDays);
        b.festivalPerCitizen = c.festivalPerCitizen.get();
        b.festivalFood = c.festivalFood.get();
        b.festivalMood = c.festivalMood.get();
        b.festivalMoodDays = c.festivalMoodDays.get();
        b.festivalEveryDays = c.festivalEveryDays.get();
        b.daysPerYear = c.daysPerYear.get();
        b.fairEveryDays = c.fairEveryDays.get();
        for (CityLevel level : CityLevel.values()) {
            if (level.id().equals(c.fairLevel.get())) {
                b.fairLevel = level;
            }
        }
        b.fairSeats = c.fairSeats.get();
        b.fairDiscount = c.fairDiscount.get();
        b.fairVisitorItems = c.fairVisitorItems.get();
        b.roomPrice = c.roomPrice.get();
        b.rumorPrice = c.rumorPrice.get();
        b.rumorsMin = c.rumorsMin.get();
        b.rumorsMax = Math.max(c.rumorsMax.get(), b.rumorsMin);
        return b;
    }

    private static LifeRules fromConfig() {
        if (!KingdomWorldConfig.SERVER_SPEC.isLoaded()) {
            return LifeRules.defaults();
        }
        LifeRules.Builder b = configured();
        try {
            return b.build();
        } catch (IllegalArgumentException e) {
            if (!warned) {
                warned = true;
                LOGGER.warn("The times of the day in the [life] config are out of order ({}); the default day is used", e.getMessage());
            }
            LifeRules.Builder d = new LifeRules.Builder();
            b.workStart = d.workStart;
            b.eveningStart = d.eveningStart;
            b.sleepStart = d.sleepStart;
            b.wakeUp = d.wakeUp;
            return b.build();
        }
    }
}
