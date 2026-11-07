package io.github.sekerator.kingdomworld.core.life;

import io.github.sekerator.kingdomworld.core.road.CityLevel;

/**
 * The rules of the life of cities: the day of a citizen, the tavern, light illnesses, festivals and fairs. Times of day are
 * in ticks of the day as Minecraft counts them (0 is six in the morning, 6000 noon, 12000 sunset, 18000 midnight).
 *
 * @param workStart          when the day of work begins; the morning before it is the way to work
 * @param eveningStart       when work ends and the evening begins
 * @param sleepStart         when citizens go to bed
 * @param wakeUp             when they get up
 * @param spreadTicks        each citizen keeps the day up to this much earlier or later, so that a city does not move in step
 * @param tavernShare        percent of the citizens who go to the tavern on an evening when there is one
 * @param tavernMood         mood an evening in a tavern with food and drink gives
 * @param tavernMoodDays     for how many days that mood lasts
 * @param sickChance         chance per day that a healthy citizen falls ill, in permille
 * @param sickFactor         how many times likelier a hungry, homeless or wounded citizen falls ill, for each of these
 * @param sickMood           mood a citizen loses while ill
 * @param sickWorkPercent    how fast an ill citizen works, in percent
 * @param infirmaryDays      days an ill citizen needs to get well in an infirmary
 * @param homeDays           days it needs without one
 * @param festivalPerCitizen money a festival costs the treasury for every citizen
 * @param festivalFood       food points of treat a festival takes from the storehouses for every citizen
 * @param festivalMood       mood a festival gives
 * @param festivalMoodDays   for how many days
 * @param festivalEveryDays  least days between festivals the king calls
 * @param daysPerYear        game days of a year of the calendar; the day the kingdom was founded comes back once a year
 * @param fairEveryDays      days between fairs of a large city, 0 for none
 * @param fairLevel          least level of a city that holds fairs
 * @param fairSeats          seats more each market post has during a fair
 * @param fairDiscount       how much cheaper the market sells during a fair, in permille
 * @param fairVisitorItems   most items the merchants of one other kingdom bring to a fair
 * @param roomPrice          what a room in a tavern costs for a day
 * @param rumorPrice         what the innkeeper asks to mark a camp of robbers on the atlas
 * @param rumorsMin          least rumors an innkeeper knows a day
 * @param rumorsMax          most rumors an innkeeper knows a day
 */
public record LifeRules(int workStart, int eveningStart, int sleepStart, int wakeUp, int spreadTicks, int tavernShare, int tavernMood,
                        int tavernMoodDays, int sickChance, double sickFactor, int sickMood, int sickWorkPercent, double infirmaryDays,
                        double homeDays, long festivalPerCitizen, int festivalFood, int festivalMood, int festivalMoodDays, int festivalEveryDays,
                        int daysPerYear, int fairEveryDays, CityLevel fairLevel, int fairSeats, int fairDiscount, int fairVisitorItems,
                        long roomPrice, long rumorPrice, int rumorsMin, int rumorsMax) {
    public static final int DAY = 24_000;
    /** Longest number of days a mood of the tavern or a festival lasts. */
    public static final int MAX_MOOD_DAYS = 30;

    public LifeRules {
        if (!inDay(workStart) || !inDay(eveningStart) || !inDay(sleepStart) || !inDay(wakeUp)
                || !(DaySchedule.span(wakeUp, workStart) + DaySchedule.span(workStart, eveningStart) + DaySchedule.span(eveningStart, sleepStart)
                + DaySchedule.span(sleepStart, wakeUp) == DAY) || DaySchedule.span(workStart, eveningStart) == 0
                || DaySchedule.span(sleepStart, wakeUp) == 0 || spreadTicks < 0 || spreadTicks > 3_000) {
            throw new IllegalArgumentException("The times of the day of a citizen are out of order");
        }
        if (tavernShare < 0 || tavernShare > 100 || tavernMood < 0 || tavernMood > 50 || tavernMoodDays < 1 || tavernMoodDays > MAX_MOOD_DAYS
                || sickChance < 0 || sickChance > 1000 || !(sickFactor >= 1 && sickFactor <= 10) || sickMood < 0 || sickMood > 50
                || sickWorkPercent < 0 || sickWorkPercent > 100 || !(infirmaryDays > 0 && infirmaryDays <= 30) || !(homeDays >= infirmaryDays
                && homeDays <= 60) || festivalPerCitizen < 0 || festivalFood < 0 || festivalMood < 0 || festivalMood > 50 || festivalMoodDays < 1
                || festivalMoodDays > MAX_MOOD_DAYS || festivalEveryDays < 1 || daysPerYear < 8 || fairEveryDays < 0 || fairLevel == null
                || fairSeats < 0 || fairSeats > 8 || fairDiscount < 0 || fairDiscount > 500 || fairVisitorItems < 0 || roomPrice < 0
                || rumorPrice < 0 || rumorsMin < 1 || rumorsMax < rumorsMin || rumorsMax > 8) {
            throw new IllegalArgumentException("A rule of the life of cities is out of range");
        }
    }

    private static boolean inDay(int tick) {
        return tick >= 0 && tick < DAY;
    }

    public static LifeRules defaults() {
        return new Builder().build();
    }

    /**
     * Changeable values of the rules, for the config and game tests.
     */
    public static final class Builder {
        public int workStart = 1_000;
        public int eveningStart = 11_000;
        public int sleepStart = 14_500;
        public int wakeUp = 23_200;
        public int spreadTicks = 700;
        public int tavernShare = 50;
        public int tavernMood = 8;
        public int tavernMoodDays = 2;
        public int sickChance = 15;
        public double sickFactor = 2.0;
        public int sickMood = 10;
        public int sickWorkPercent = 50;
        public double infirmaryDays = 1.0;
        public double homeDays = 3.0;
        public long festivalPerCitizen = 20;
        public int festivalFood = 6;
        public int festivalMood = 15;
        public int festivalMoodDays = 3;
        public int festivalEveryDays = 7;
        public int daysPerYear = 96;
        public int fairEveryDays = 7;
        public CityLevel fairLevel = CityLevel.CITY;
        public int fairSeats = 2;
        public int fairDiscount = 100;
        public int fairVisitorItems = 64;
        public long roomPrice = 30;
        public long rumorPrice = 15;
        public int rumorsMin = 2;
        public int rumorsMax = 4;

        public Builder() {
        }

        public Builder(LifeRules rules) {
            workStart = rules.workStart();
            eveningStart = rules.eveningStart();
            sleepStart = rules.sleepStart();
            wakeUp = rules.wakeUp();
            spreadTicks = rules.spreadTicks();
            tavernShare = rules.tavernShare();
            tavernMood = rules.tavernMood();
            tavernMoodDays = rules.tavernMoodDays();
            sickChance = rules.sickChance();
            sickFactor = rules.sickFactor();
            sickMood = rules.sickMood();
            sickWorkPercent = rules.sickWorkPercent();
            infirmaryDays = rules.infirmaryDays();
            homeDays = rules.homeDays();
            festivalPerCitizen = rules.festivalPerCitizen();
            festivalFood = rules.festivalFood();
            festivalMood = rules.festivalMood();
            festivalMoodDays = rules.festivalMoodDays();
            festivalEveryDays = rules.festivalEveryDays();
            daysPerYear = rules.daysPerYear();
            fairEveryDays = rules.fairEveryDays();
            fairLevel = rules.fairLevel();
            fairSeats = rules.fairSeats();
            fairDiscount = rules.fairDiscount();
            fairVisitorItems = rules.fairVisitorItems();
            roomPrice = rules.roomPrice();
            rumorPrice = rules.rumorPrice();
            rumorsMin = rules.rumorsMin();
            rumorsMax = rules.rumorsMax();
        }

        public LifeRules build() {
            return new LifeRules(workStart, eveningStart, sleepStart, wakeUp, spreadTicks, tavernShare, tavernMood, tavernMoodDays, sickChance,
                    sickFactor, sickMood, sickWorkPercent, infirmaryDays, homeDays, festivalPerCitizen, festivalFood, festivalMood, festivalMoodDays,
                    festivalEveryDays, daysPerYear, fairEveryDays, fairLevel, fairSeats, fairDiscount, fairVisitorItems, roomPrice, rumorPrice,
                    rumorsMin, rumorsMax);
        }
    }
}
