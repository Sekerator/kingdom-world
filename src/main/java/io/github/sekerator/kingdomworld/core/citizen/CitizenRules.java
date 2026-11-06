package io.github.sekerator.kingdomworld.core.citizen;

/**
 * Server settings the citizen simulation follows.
 *
 * @param activationChunks      a citizen has a body while a player is within this many chunks of its home or
 *                              workplace
 * @param maxActive             most citizen bodies in the world at the same time, across all kingdoms
 * @param simulationInterval    ticks between two steps of the needs simulation
 * @param growthFoodDays        a newcomer arrives only if the storehouses hold food for this many days
 * @param growthIntervalTicks   least time between two arrivals in one kingdom, at neutral mood
 * @param starvationCanKill     whether hunger may kill; otherwise it stops at one point of health
 * @param dailyNutrition        food points a citizen eats per game day
 * @param infirmaryCapacity     wounded citizens one infirmary treats at the same time
 * @param woundedFraction       fraction of the maximum health below which a citizen is wounded
 * @param maxPerKingdom         newcomers stop arriving at this population
 * @param farmRadius            horizontal reach of a farmer around the composter, in blocks
 */
public record CitizenRules(int activationChunks, int maxActive, int simulationInterval, int growthFoodDays,
                           long growthIntervalTicks, boolean starvationCanKill, int dailyNutrition,
                           int infirmaryCapacity, float woundedFraction, int maxPerKingdom, int farmRadius) {
    public static final CitizenRules DEFAULT = new CitizenRules(6, 300, 1200, 3, 10L * 1200L, false, 6, 4, 0.5f, 200, 8);

    public CitizenRules {
        if (activationChunks < 0 || maxActive < 0 || simulationInterval <= 0 || growthFoodDays < 0
                || growthIntervalTicks < 0 || dailyNutrition < 0 || infirmaryCapacity < 0 || maxPerKingdom < 0
                || farmRadius < 1) {
            throw new IllegalArgumentException("Citizen rules out of range");
        }
        if (!(woundedFraction > 0.0f && woundedFraction < 1.0f)) {
            throw new IllegalArgumentException("Wounded fraction must lie between 0 and 1: " + woundedFraction);
        }
    }
}
