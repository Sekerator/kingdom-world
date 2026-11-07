package io.github.sekerator.kingdomworld.core.life;

/**
 * Festivals and fairs. The king calls a festival for the next day: it costs the treasury money and the storehouses treat for
 * every citizen; on the day the citizens work only in the morning and gather at the throne in the afternoon, and their mood is
 * better for some days. Once a year the day the kingdom was founded is a festival by itself, for free. A year of the calendar
 * is {@code daysPerYear} game days (by default 96: twelve turns of the moon of eight days).
 *
 * <p>A large city holds a fair every {@code fairEveryDays} days, each city on its own day: its market has more seats, sells a
 * little cheaper, and merchants of other kingdoms linked by road bring goods.
 */
public final class Festivals {
    /** Afternoon of the day: from noon on, the citizens of a festival leave work. */
    public static final int AFTERNOON = 6_000;

    private Festivals() {
    }

    /**
     * Why a festival cannot be called, or {@code null} if it can.
     *
     * @param lastDay the day of the last festival the king called, or {@link Long#MIN_VALUE}
     */
    public static String refusal(long lastDay, long today, LifeRules rules) {
        if (lastDay != Long.MIN_VALUE && today - lastDay < rules.festivalEveryDays()) {
            return "too_soon";
        }
        return null;
    }

    /**
     * Days until the king may call the next festival, 0 if now.
     */
    public static long daysUntilNext(long lastDay, long today, LifeRules rules) {
        if (lastDay == Long.MIN_VALUE) {
            return 0;
        }
        return Math.max(0, lastDay + rules.festivalEveryDays() - today);
    }

    /**
     * What a festival costs the treasury.
     */
    public static long cost(int citizens, LifeRules rules) {
        return Math.max(0, citizens) * rules.festivalPerCitizen();
    }

    /**
     * Food points of treat a festival takes from the storehouses.
     */
    public static int treat(int citizens, LifeRules rules) {
        return Math.max(0, citizens) * rules.festivalFood();
    }

    /**
     * Mood a festival on {@code festivalDay} gives today: from the festival on for {@code festivalMoodDays} days; a festival
     * without the treat (the storehouses were empty) gives half.
     */
    public static int mood(long festivalDay, boolean treated, long today, LifeRules rules) {
        if (festivalDay == Long.MIN_VALUE || today < festivalDay || today - festivalDay >= rules.festivalMoodDays()) {
            return 0;
        }
        return treated ? rules.festivalMood() : rules.festivalMood() / 2;
    }

    /**
     * Whether the citizens rest now: on the afternoon and evening of a festival.
     */
    public static boolean resting(long festivalDay, long gameTime) {
        return festivalDay != Long.MIN_VALUE && DaySchedule.day(gameTime) == festivalDay
                && Math.floorMod(gameTime, (long) LifeRules.DAY) >= AFTERNOON;
    }

    /**
     * The year of a kingdom at a time: 0 in its first year.
     */
    public static long year(long foundedTime, long gameTime, LifeRules rules) {
        return Math.max(0, Math.floorDiv(DaySchedule.day(gameTime) - DaySchedule.day(foundedTime), rules.daysPerYear()));
    }

    /**
     * Whether today is the day the kingdom was founded, a whole number of years later (not the founding itself).
     */
    public static boolean foundingDay(long foundedTime, long today, LifeRules rules) {
        long days = today - DaySchedule.day(foundedTime);
        return days > 0 && days % rules.daysPerYear() == 0;
    }

    // ---------------------------------------------------------------- fairs

    /**
     * Whether a city holds its fair today: every {@code fairEveryDays} days, each city on a day of its own.
     */
    public static boolean fairDay(long cityKey, long today, LifeRules rules) {
        if (rules.fairEveryDays() <= 0) {
            return false;
        }
        long offset = Math.floorMod(DaySchedule.mix(cityKey), (long) rules.fairEveryDays());
        return Math.floorMod(today - offset, (long) rules.fairEveryDays()) == 0;
    }

    /**
     * Days until a city's next fair, 0 if it is today; -1 if there are no fairs.
     */
    public static long daysToFair(long cityKey, long today, LifeRules rules) {
        if (rules.fairEveryDays() <= 0) {
            return -1;
        }
        for (int i = 0; i < rules.fairEveryDays(); i++) {
            if (fairDay(cityKey, today + i, rules)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A price during a fair: lower by {@code fairDiscount} permille, never below one coin.
     */
    public static long fairPrice(long price, LifeRules rules) {
        if (price <= 0) {
            return price;
        }
        return Math.max(1, price - price * rules.fairDiscount() / 1000);
    }
}
