package io.github.sekerator.kingdomworld.core.life;

/**
 * Light illnesses: now and then a citizen falls ill, more often when hungry, without a home or after wounds. An ill citizen
 * works slower and is in a worse mood; it gets well by itself, fast in an infirmary and slower at home. Nobody dies of an
 * illness and nobody catches it from another.
 */
public final class Sickness {
    /** Most chance to fall ill in a day, whatever the circumstances. */
    public static final double MAX_DAILY = 0.5;

    private Sickness() {
    }

    /**
     * The chance to fall ill during a day.
     */
    public static double dailyChance(LifeRules rules, boolean hungry, boolean homeless, boolean wounded) {
        double chance = rules.sickChance() / 1000.0;
        if (hungry) {
            chance *= rules.sickFactor();
        }
        if (homeless) {
            chance *= rules.sickFactor();
        }
        if (wounded) {
            chance *= rules.sickFactor();
        }
        return Math.min(MAX_DAILY, chance);
    }

    /**
     * The chance to fall ill during one step of the simulation: the same chance per day, whatever the length of a step.
     */
    public static double stepChance(double daily, int intervalTicks) {
        if (daily <= 0 || intervalTicks <= 0) {
            return 0.0;
        }
        return 1.0 - Math.pow(1.0 - Math.min(MAX_DAILY, daily), intervalTicks / (double) LifeRules.DAY);
    }

    /**
     * How much of the way to health an ill citizen makes in one step: all of it after {@code infirmaryDays} in an infirmary,
     * after {@code homeDays} without one.
     */
    public static double recovery(LifeRules rules, boolean infirmary, int intervalTicks) {
        double days = infirmary ? rules.infirmaryDays() : rules.homeDays();
        return Math.max(0, intervalTicks) / (days * LifeRules.DAY);
    }

    /**
     * How fast an ill citizen works, as a factor.
     */
    public static double workFactor(LifeRules rules, boolean sick) {
        return sick ? rules.sickWorkPercent() / 100.0 : 1.0;
    }
}
