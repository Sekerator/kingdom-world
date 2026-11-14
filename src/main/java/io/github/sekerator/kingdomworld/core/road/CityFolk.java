package io.github.sekerator.kingdomworld.core.road;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRules;

/**
 * The townsfolk of a city of a kingdom of players: once a city has {@link FolkRules#namedPerCity()} named citizens, its
 * newcomers stay a number of the city instead of becoming citizens with a body and a record, so that a city of thousands
 * costs the server no more than its named citizens and one day of the townsfolk. Nobody named is ever folded into the
 * number: a city with fewer named citizens than the quota takes its next newcomer out of the townsfolk.
 *
 * <p>The townsfolk have no beds of their own: they live in the free beds of their city that no named citizen took, a family
 * ({@link FolkRules#folkPerBed()}) to a bed, and named citizens always come first. Once a day they eat from the storehouses
 * as citizens far from players do, pay their dues and, while the city is full of named citizens, has room, food and a good
 * mood, grow by {@value #GROWTH_PERMILLE} thousandths. What they pay is reckoned in {@link #dues} alone, so that a later
 * economy of citizens replaces one rule. Pure functions, so that the server and the tests share them.
 */
public final class CityFolk {
    /** How much the townsfolk of a city grow in a day, in thousandths: three in a hundred. */
    public static final int GROWTH_PERMILLE = 30;

    /**
     * Who the next newcomer of a city is.
     */
    public enum Newcomer {
        /** Nobody: no free bed for a named citizen, or no room for more townsfolk. */
        NONE,
        /** A new named citizen. */
        NAMED,
        /** One of the townsfolk becomes a named citizen: the city has fewer named citizens than the quota. */
        FROM_FOLK,
        /** One more of the townsfolk: the city has as many named citizens as the quota. */
        FOLK
    }

    private CityFolk() {
    }

    /**
     * Whether a city has as many named citizens as it takes, so that its newcomers stay townsfolk.
     */
    public static boolean quotaReached(int named, FolkRules rules) {
        return rules.namedPerCity() > 0 && named >= rules.namedPerCity();
    }

    /**
     * How many townsfolk the free beds of a city hold, a family to a bed.
     *
     * @param freeBeds the beds of the city no named citizen took
     */
    public static long room(int freeBeds, FolkRules rules) {
        return (long) Math.max(0, freeBeds) * rules.folkPerBed();
    }

    /**
     * The beds the townsfolk of a city take, a family to a bed.
     */
    public static int beds(int folk, FolkRules rules) {
        return folk <= 0 ? 0 : (int) ((folk + (long) rules.folkPerBed() - 1) / rules.folkPerBed());
    }

    /**
     * The free beds of a city left once its townsfolk took theirs.
     */
    public static int bedsLeft(int freeBeds, int folk, FolkRules rules) {
        return Math.max(0, freeBeds - beds(folk, rules));
    }

    /**
     * The townsfolk the free beds of a city keep; the rest leave.
     */
    public static int housed(int folk, int freeBeds, FolkRules rules) {
        return (int) Math.min(Math.max(0, folk), room(freeBeds, rules));
    }

    /**
     * The radius a city of so many people needs at least, built densely, in blocks: half the side of a square of
     * {@code people / folkDensity} blocks. 0 for nobody; without an upper limit.
     *
     * @param people its named citizens and townsfolk
     */
    public static int radius(long people, FolkRules rules) {
        if (people <= 0) {
            return 0;
        }
        return (int) Math.ceil(Math.sqrt(people / (double) rules.folkDensity()) / 2.0);
    }

    /**
     * The food points the townsfolk eat in a day.
     */
    public static long ration(int folk, int dailyNutrition) {
        return (long) Math.max(0, folk) * Math.max(0, dailyNutrition);
    }

    /**
     * What the townsfolk of a city pay into the treasury in a day. The one rule of what they pay: an economy of citizens
     * replaces it here.
     */
    public static long dues(int folk, FolkRules rules) {
        return (long) Math.max(0, folk) * rules.folkDues();
    }

    /**
     * Who the next newcomer of a city is: a named citizen while the city has fewer than the quota and a free bed, out of the
     * townsfolk if it has any; once it has as many, one more of the townsfolk while they have room.
     *
     * @param named    the named citizens whose home is in the city
     * @param folk     its townsfolk
     * @param freeBeds the beds of the city no named citizen took; townsfolk in them make way for a named newcomer
     */
    public static Newcomer newcomer(int named, int folk, int freeBeds, FolkRules rules) {
        if (!quotaReached(named, rules)) {
            if (freeBeds <= 0) {
                return Newcomer.NONE;
            }
            return folk > 0 ? Newcomer.FROM_FOLK : Newcomer.NAMED;
        }
        return room(freeBeds, rules) > folk && folk < CityCharter.MAX_FOLK ? Newcomer.FOLK : Newcomer.NONE;
    }

    /**
     * Whether the newcomer of a city comes now: the city has one ({@link #newcomer}), the kingdom is below the most citizens the
     * server allows, its storehouses feed everybody, the townsfolk too, for the days the rules ask, and the time since the last
     * newcomer of this city passed. Every city of a kingdom grows by itself.
     *
     * @param population    the named citizens of the kingdom
     * @param eaters        everybody who eats from the storehouses of the kingdom: the named citizens and the townsfolk
     * @param freeHouseBeds free beds in houses of the city: they bring newcomers sooner
     * @param sinceArrival  time since the last newcomer of the city, {@link Long#MAX_VALUE} before the first
     * @param mood          the average mood of the named citizens of the city
     */
    public static CitizenNeeds.GrowthCheck check(Newcomer newcomer, int population, long eaters, int freeHouseBeds, long nutrition,
                                                 long sinceArrival, int mood, CitizenRules rules) {
        int all = (int) Math.min(Integer.MAX_VALUE - 1L, Math.max(0L, eaters));
        return CitizenNeeds.checkGrowth(population, all, newcomer == Newcomer.NONE ? 0 : 1, Math.max(0, freeHouseBeds), nutrition,
                sinceArrival, mood, rules);
    }

    /**
     * What a day of the townsfolk of a city starts from.
     *
     * @param folk           the townsfolk
     * @param carry          the part of a newcomer they grew by and that did not make a whole one yet
     * @param freeBeds       the beds of the city no named citizen took
     * @param dailyNutrition the food points one eats in a day
     * @param eaten          the food points they got from the storehouses: their {@link #ration} of those {@link #housed}, or less
     * @param food           the food points left in the storehouses of the kingdom after they ate
     * @param others         everybody else who eats from those storehouses: the named citizens and the townsfolk of other cities
     * @param foodDays       the days of food the storehouses must hold for everybody before the townsfolk grow
     * @param named          the named citizens whose home is in the city
     * @param mood           their average mood
     * @param treasuryRoom   how much the treasury can still take
     */
    public record Day(int folk, double carry, int freeBeds, int dailyNutrition, long eaten, long food, long others, int foodDays, int named,
                      int mood, long treasuryRoom) {
    }

    /**
     * How a day of the townsfolk of a city went.
     *
     * @param folk        the townsfolk at the end of the day
     * @param carry       the part of a newcomer kept for the next day
     * @param leftForRoom those who left because the free beds no longer held them
     * @param leftHungry  those who left because they got nothing to eat
     * @param grown       those who came
     * @param dues        what they paid into the treasury
     */
    public record Result(int folk, double carry, int leftForRoom, int leftHungry, int grown, long dues) {
    }

    /**
     * A day of the townsfolk of a city: those the free beds no longer hold leave, then those who got nothing to eat, the rest
     * pay their dues (as much as the treasury can still take) and, while the city has as many named citizens as it takes,
     * room for more, food for everybody for the days the rules ask and named citizens at least of neutral mood, they grow by
     * {@value #GROWTH_PERMILLE} thousandths, at least one a day, never beyond the room.
     */
    public static Result day(Day day, FolkRules rules) {
        int start = Math.max(0, day.folk());
        int housed = housed(start, day.freeBeds(), rules);
        int leftForRoom = start - housed;
        int fed = day.dailyNutrition() <= 0 ? housed : (int) Math.min(housed, Math.max(0L, day.eaten()) / day.dailyNutrition());
        int leftHungry = housed - fed;
        int folk = fed;
        long dues = Math.min(dues(folk, rules), Math.max(0L, day.treasuryRoom()));
        double carry = Double.isFinite(day.carry()) ? Math.max(0.0, day.carry()) : 0.0;
        long space = Math.min(room(day.freeBeds(), rules), CityCharter.MAX_FOLK) - folk;
        int eaters = (int) Math.min(Integer.MAX_VALUE - 1L, Math.max(0L, day.others()) + folk);
        boolean grows = quotaReached(day.named(), rules) && leftHungry == 0 && space > 0 && day.mood() >= CitizenNeeds.NEUTRAL_MOOD
                && CitizenNeeds.foodDays(day.food(), eaters, day.dailyNutrition()) >= day.foodDays();
        int grown = 0;
        if (grows) {
            double exact = folk * (GROWTH_PERMILLE / 1000.0) + carry;
            long whole = (long) Math.floor(exact);
            if (whole < 1) {
                whole = 1;
                carry = 0.0;
            } else {
                carry = exact - whole;
            }
            if (whole >= space) {
                whole = space;
                carry = 0.0;
            }
            grown = (int) whole;
            folk += grown;
        }
        return new Result(folk, Math.min(carry, 0.999_999), leftForRoom, leftHungry, grown, dues);
    }
}
