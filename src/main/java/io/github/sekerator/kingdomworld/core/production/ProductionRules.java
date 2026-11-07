package io.github.sekerator.kingdomworld.core.production;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;

/**
 * Server settings of production and the formulas of work far from players. Pure functions, shared by the
 * simulation and the tests.
 *
 * @param craftTicks    ticks a beginner needs to put one thing together by hand; experience makes it up to twice as fast
 * @param cookFoodDays  days of food for everybody a cook keeps in the storehouses on its own
 * @param chainDepth    how many steps of sub-orders a chain of orders may have
 * @param maxOrders     orders of the king one kingdom may have at most
 */
public record ProductionRules(int craftTicks, int cookFoodDays, int chainDepth, int maxOrders) {
    /** Experience at which a worker works twice as fast as a beginner. */
    public static final int MASTER_EXPERIENCE = 1_000;
    /** A worker that measured slower than the estimate works at most this many times slower far from players. */
    public static final double MAX_SLOWDOWN = 8.0;
    /** Shortest time one making takes, in ticks. */
    public static final int MIN_TICKS = 20;

    public static final ProductionRules DEFAULT = new ProductionRules(200, 5, 3, 32);

    public ProductionRules {
        if (craftTicks < 1 || cookFoodDays < 0 || chainDepth < 0 || maxOrders < 1) {
            throw new IllegalArgumentException("Production rules out of range");
        }
    }

    /**
     * How much faster than a beginner a worker with this experience works, from 1 to 2.
     */
    public static double speed(int experience) {
        return 1.0 + Math.max(0.0, Math.min(1.0, experience / (double) MASTER_EXPERIENCE));
    }

    /**
     * Ticks one making of the recipe takes a worker: a recipe that cooks takes its cooking time, the furnace sets the
     * pace; anything put together by hand takes {@link #craftTicks}, less with experience.
     */
    public int ticks(RecipeOption recipe, int experience) {
        if (recipe.kind().cooks() && recipe.ticks() > 0) {
            return Math.max(MIN_TICKS, recipe.ticks());
        }
        return Math.max(MIN_TICKS, (int) Math.round(craftTicks / speed(experience)));
    }

    /**
     * Ticks of work one worker does during a simulation step: workers work by day only.
     *
     * @param factor share of the full pace, lower for a worker who fetches its ingredients itself
     */
    public static double workTicks(int intervalTicks, double factor) {
        return Math.max(0, intervalTicks) * (GatheringRules.WORK_TICKS_PER_DAY / (double) CitizenNeeds.TICKS_PER_DAY)
                * Math.max(0.0, factor);
    }

    /**
     * How much slower than the estimate a post worked while it was watched: makings of a measured day against
     * those {@link #craftTicks} would give; 1 if not measured.
     *
     * @param measuredPerDay makings one worker did in a day of watched work, 0 if not measured
     */
    public double slowdown(double measuredPerDay) {
        if (!(measuredPerDay > 0.0)) {
            return 1.0;
        }
        double estimated = GatheringRules.WORK_TICKS_PER_DAY / (double) craftTicks;
        return Math.max(1.0, Math.min(MAX_SLOWDOWN, estimated / measuredPerDay));
    }

    /**
     * Fuel items to burn to owe no more burn time: {@code owed} ticks of burning are needed and one item burns
     * {@code burnTicks}.
     */
    public static int fuelItems(double owed, int burnTicks) {
        if (owed <= 0.0 || burnTicks <= 0) {
            return 0;
        }
        return (int) Math.ceil(owed / burnTicks - 1e-9);
    }

    /**
     * Days the food of the storehouses lasts everybody.
     *
     * @param nutrition     food points in the storehouses
     * @param dailyNutrition food points a citizen eats a day
     */
    public static double foodDays(long nutrition, int citizens, int dailyNutrition) {
        if (citizens <= 0 || dailyNutrition <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return nutrition / (double) ((long) citizens * dailyNutrition);
    }
}
