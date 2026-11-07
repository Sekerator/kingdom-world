package io.github.sekerator.kingdomworld.core.citizen;

/**
 * The formulas of the citizen simulation: hunger, healing, mood, growth of the population and the output of
 * work done far from players. Pure functions, so the abstract simulation and the tests share them.
 */
public final class CitizenNeeds {
    public static final long TICKS_PER_DAY = 24_000L;
    /** One step of reference length; healing and starvation are given per reference step. */
    public static final int REFERENCE_STEP_TICKS = 1_200;
    public static final float MAX_FOOD = 20.0f;
    /** A citizen eats when its satiety drops below this. */
    public static final float EAT_BELOW = 14.0f;
    /** Below this satiety a citizen is hungry: no healing, worse mood. */
    public static final float HUNGRY_BELOW = 6.0f;
    public static final int MAX_MOOD = 100;
    public static final int NEUTRAL_MOOD = 50;
    /** Largest change of mood in one simulation step. */
    public static final int MOOD_STEP = 5;
    /** Deaths of neighbours spoil the mood for this many days. */
    public static final int RECENT_DEATH_DAYS = 3;
    /** Time a planted crop needs to ripen, on average. */
    public static final long FARM_CYCLE_TICKS = 2 * TICKS_PER_DAY;

    /**
     * Why a newcomer did not arrive.
     */
    public enum GrowthCheck {
        READY,
        /** The population reached the configured maximum. */
        FULL,
        NO_FREE_BED,
        NOT_ENOUGH_FOOD,
        TOO_SOON
    }

    /**
     * What the mood of a citizen depends on. More factors (festivals, unrest) are added here later.
     *
     * @param recentDeaths citizens of the same kingdom who died in the last {@link #RECENT_DEATH_DAYS} days
     * @param payPercent   how much of the usual wage the citizen got on the last payday, see
     *                     {@link io.github.sekerator.kingdomworld.core.economy.Payroll#percent}
     */
    public record MoodInputs(float food, boolean hasHome, boolean wounded, int recentDeaths, int payPercent) {
        public MoodInputs(float food, boolean hasHome, boolean wounded, int recentDeaths) {
            this(food, hasHome, wounded, recentDeaths, io.github.sekerator.kingdomworld.core.economy.Payroll.NOTHING_EXPECTED);
        }
    }

    private CitizenNeeds() {
    }

    // ---------------------------------------------------------------- food

    /**
     * Satiety lost during one simulation step.
     */
    public static float hunger(int dailyNutrition, int intervalTicks) {
        return (float) (dailyNutrition * (double) intervalTicks / TICKS_PER_DAY);
    }

    /**
     * Food points a citizen wants to eat now: enough to be full, but only once satiety dropped below
     * {@link #EAT_BELOW}.
     */
    public static int nutritionWanted(float food) {
        return food < EAT_BELOW ? (int) Math.ceil(MAX_FOOD - food) : 0;
    }

    /**
     * For how many days the food points in the storehouses feed the population; a kingdom without citizens is
     * measured for one citizen, so that the first newcomer also needs food.
     */
    public static double foodDays(long nutrition, int population, int dailyNutrition) {
        if (dailyNutrition <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0L, nutrition) / ((double) Math.max(1, population) * dailyNutrition);
    }

    // ---------------------------------------------------------------- health

    /**
     * Change of health during one simulation step of a citizen without a body: starving citizens lose health,
     * fed citizens heal, faster in an infirmary.
     */
    public static float healthChange(float food, boolean inInfirmary, int intervalTicks) {
        float steps = intervalTicks / (float) REFERENCE_STEP_TICKS;
        if (food <= 0.0f) {
            return -steps;
        }
        if (food < HUNGRY_BELOW) {
            return 0.0f;
        }
        return steps * (inInfirmary ? 4.0f : 1.0f);
    }

    // ---------------------------------------------------------------- mood

    /**
     * The mood a citizen tends to under the given circumstances.
     */
    public static int targetMood(MoodInputs inputs) {
        int mood = NEUTRAL_MOOD;
        if (inputs.food() <= 0.0f) {
            mood -= 35;
        } else if (inputs.food() < HUNGRY_BELOW) {
            mood -= 20;
        } else if (inputs.food() >= EAT_BELOW) {
            mood += 15;
        }
        mood += inputs.hasHome() ? 10 : -15;
        if (inputs.wounded()) {
            mood -= 5;
        }
        mood -= Math.min(20, 5 * Math.max(0, inputs.recentDeaths()));
        mood += io.github.sekerator.kingdomworld.core.economy.Payroll.mood(inputs.payPercent());
        return Math.max(0, Math.min(MAX_MOOD, mood));
    }

    /**
     * The mood after one step towards the target; it changes by at most {@link #MOOD_STEP}.
     */
    public static int nextMood(int current, int target) {
        if (target > current) {
            return Math.min(target, current + MOOD_STEP);
        }
        return Math.max(target, current - MOOD_STEP);
    }

    // ---------------------------------------------------------------- growth

    /**
     * Least time between arrivals at the given average mood: a happy kingdom (100) attracts people in three
     * quarters of the base time, a neutral one (50) in the base time, a miserable one (0) in twice the time.
     */
    public static long growthInterval(long baseTicks, int averageMood) {
        int mood = Math.max(0, Math.min(MAX_MOOD, averageMood));
        double factor = mood >= NEUTRAL_MOOD
                ? 1.0 - (mood - NEUTRAL_MOOD) / 200.0
                : 1.0 + (NEUTRAL_MOOD - mood) / (double) NEUTRAL_MOOD;
        return Math.round(baseTicks * factor);
    }

    /**
     * Whether a newcomer arrives now.
     *
     * @param ticksSinceArrival time since the last arrival, or {@link Long#MAX_VALUE} if nobody arrived yet
     */
    public static GrowthCheck checkGrowth(int population, int freeBeds, long nutrition, long ticksSinceArrival,
                                          int averageMood, CitizenRules rules) {
        if (population >= rules.maxPerKingdom()) {
            return GrowthCheck.FULL;
        }
        if (freeBeds <= 0) {
            return GrowthCheck.NO_FREE_BED;
        }
        // The newcomer eats too, so the stock must last for the population after the arrival.
        if (foodDays(nutrition, population + 1, rules.dailyNutrition()) < rules.growthFoodDays()) {
            return GrowthCheck.NOT_ENOUGH_FOOD;
        }
        if (ticksSinceArrival < growthInterval(rules.growthIntervalTicks(), averageMood)) {
            return GrowthCheck.TOO_SOON;
        }
        return GrowthCheck.READY;
    }

    // ---------------------------------------------------------------- work far from players

    /**
     * Harvests a farmer brings in during one simulation step without a body: every plot ripens once per
     * {@link #FARM_CYCLE_TICKS}.
     */
    public static double farmOutput(int plots, int intervalTicks) {
        return Math.max(0, plots) * (double) intervalTicks / FARM_CYCLE_TICKS;
    }
}
