package io.github.sekerator.kingdomworld.core.road;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The townsfolk of a city: who the next newcomer is, room, food, growth only once the city is full of named citizens, dues
 * into a treasury that may be full, and the square a city of many people needs.
 */
class CityFolkTest {
    private static final FolkRules RULES = FolkRules.DEFAULT;
    private static final int DAILY = 6;
    /** Plenty of food, no other eaters, the quota of named citizens reached, a good mood, a treasury with room. */
    private static final long PLENTY = 1_000_000_000L;

    private static CityFolk.Day day(int folk, int freeBeds, long eaten) {
        return new CityFolk.Day(folk, 0.0, freeBeds, DAILY, eaten, PLENTY, 64, 3, 64, CitizenNeeds.NEUTRAL_MOOD, PLENTY);
    }

    @Test
    void aCityTakesNamedCitizensUpToTheQuotaThenTownsfolk() {
        assertEquals(CityFolk.Newcomer.NAMED, CityFolk.newcomer(10, 0, 3, RULES));
        assertEquals(CityFolk.Newcomer.NONE, CityFolk.newcomer(10, 0, 0, RULES), "a named newcomer needs a free bed");
        assertEquals(CityFolk.Newcomer.FROM_FOLK, CityFolk.newcomer(63, 40, 12, RULES),
                "below the quota the next newcomer comes out of the townsfolk");
        assertEquals(CityFolk.Newcomer.FOLK, CityFolk.newcomer(64, 0, 1, RULES));
        assertEquals(CityFolk.Newcomer.FOLK, CityFolk.newcomer(64, 3, 1, RULES), "a bed holds a family of four");
        assertEquals(CityFolk.Newcomer.NONE, CityFolk.newcomer(64, 4, 1, RULES), "no room for more townsfolk");
        FolkRules never = new FolkRules(0, 4, 8, 1);
        assertEquals(CityFolk.Newcomer.NAMED, CityFolk.newcomer(5_000, 0, 1, never), "a quota of 0 never makes townsfolk");
        assertFalse(CityFolk.quotaReached(5_000, never));
    }

    @Test
    void namedCitizensComeBeforeTownsfolk() {
        // The free beds are full of townsfolk, yet a city below its quota takes a named newcomer, and the townsfolk make way.
        assertEquals(CityFolk.Newcomer.FROM_FOLK, CityFolk.newcomer(60, 400, 100, RULES));
        assertEquals(25, CityFolk.beds(100, RULES));
        assertEquals(26, CityFolk.beds(101, RULES));
        assertEquals(0, CityFolk.bedsLeft(20, 100, RULES));
        assertEquals(5, CityFolk.bedsLeft(30, 100, RULES));
        assertEquals(400, CityFolk.room(100, RULES));
        assertEquals(80, CityFolk.housed(100, 20, RULES));
    }

    @Test
    void townsfolkTheBedsNoLongerHoldLeave() {
        CityFolk.Result result = CityFolk.day(day(100, 20, 80L * DAILY), RULES);
        assertEquals(20, result.leftForRoom());
        assertEquals(0, result.leftHungry());
        assertEquals(80, result.folk(), "no room left, so nobody comes");
        assertEquals(0, result.grown());
    }

    @Test
    void townsfolkWithoutFoodLeave() {
        CityFolk.Result result = CityFolk.day(day(100, 100, 30L * DAILY), RULES);
        assertEquals(70, result.leftHungry());
        assertEquals(30, result.folk(), "those who ate stay, and nobody comes while some went hungry");
        assertEquals(30, result.dues(), "only those who stay pay");
        assertEquals(600, CityFolk.ration(100, DAILY));
        CityFolk.Result free = CityFolk.day(new CityFolk.Day(100, 0.0, 100, 0, 0, 0, 64, 3, 64, 50, PLENTY), RULES);
        assertEquals(0, free.leftHungry(), "townsfolk who need no food never go hungry");
    }

    @Test
    void townsfolkGrowOnlyOnceTheCityIsFullOfNamedCitizens() {
        CityFolk.Result full = CityFolk.day(day(100, 100, 100L * DAILY), RULES);
        assertEquals(3, full.grown(), "three in a hundred a day");
        assertEquals(103, full.folk());
        CityFolk.Day short1 = new CityFolk.Day(100, 0.0, 100, DAILY, 100L * DAILY, PLENTY, 63, 3, 63, 60, PLENTY);
        assertEquals(0, CityFolk.day(short1, RULES).grown(), "below the quota the city grows by named newcomers");
        CityFolk.Day sad = new CityFolk.Day(100, 0.0, 100, DAILY, 100L * DAILY, PLENTY, 64, 3, 64, CitizenNeeds.NEUTRAL_MOOD - 1, PLENTY);
        assertEquals(0, CityFolk.day(sad, RULES).grown(), "unhappy named citizens keep newcomers away");
        // Food for 2 days only: 164 eaters of 6 points for 2 days.
        CityFolk.Day hungry = new CityFolk.Day(100, 0.0, 100, DAILY, 100L * DAILY, 164L * DAILY * 2, 64, 3, 64, 60, PLENTY);
        assertEquals(0, CityFolk.day(hungry, RULES).grown(), "the storehouses must feed everybody for three days");
        CityFolk.Day fed = new CityFolk.Day(100, 0.0, 100, DAILY, 100L * DAILY, 164L * DAILY * 3, 64, 3, 64, 60, PLENTY);
        assertEquals(3, CityFolk.day(fed, RULES).grown());
    }

    @Test
    void growthIsAtLeastOneAndKeepsItsParts() {
        CityFolk.Result first = CityFolk.day(day(0, 10, 0), RULES);
        assertEquals(1, first.grown(), "an empty city full of named citizens takes one a day");
        CityFolk.Result some = CityFolk.day(new CityFolk.Day(50, 0.0, 100, DAILY, 50L * DAILY, PLENTY, 64, 3, 64, 60, PLENTY), RULES);
        assertEquals(1, some.grown(), "1.5 makes one");
        assertEquals(0.5, some.carry(), 1e-9, "and half of one is kept for the next day");
        CityFolk.Result next = CityFolk.day(new CityFolk.Day(51, some.carry(), 100, DAILY, 51L * DAILY, PLENTY, 64, 3, 64, 60, PLENTY), RULES);
        assertEquals(2, next.grown(), "1.53 and the half kept make two");
        CityFolk.Result room = CityFolk.day(new CityFolk.Day(395, 0.0, 100, DAILY, 395L * DAILY, PLENTY, 64, 3, 64, 60, PLENTY), RULES);
        assertEquals(5, room.grown(), "never beyond the room of the beds");
        assertEquals(400, room.folk());
        assertEquals(0.0, room.carry());
    }

    @Test
    void aFullTreasuryTakesNoMoreThanItHasRoomFor() {
        CityFolk.Result paid = CityFolk.day(day(200, 100, 200L * DAILY), RULES);
        assertEquals(200, paid.dues(), "a coin a head a day");
        CityFolk.Day full = new CityFolk.Day(200, 0.0, 100, DAILY, 200L * DAILY, PLENTY, 64, 3, 64, 60, 37);
        assertEquals(37, CityFolk.day(full, RULES).dues());
        CityFolk.Day none = new CityFolk.Day(200, 0.0, 100, DAILY, 200L * DAILY, PLENTY, 64, 3, 64, 60, 0);
        assertEquals(0, CityFolk.day(none, RULES).dues());
        assertEquals(400, CityFolk.dues(200, new FolkRules(64, 4, 8, 2)));
    }

    @Test
    void theSquareOfACityGrowsWithItsPeopleWithoutALimit() {
        assertEquals(0, CityFolk.radius(0, RULES));
        assertEquals(1, CityFolk.radius(1, RULES));
        // A large city of the default rules is 48 blocks from the middle: some 74 thousand people fit in it.
        assertTrue(CityFolk.radius(73_728, RULES) <= 48);
        assertTrue(CityFolk.radius(74_000, RULES) > 48);
        assertEquals(177, CityFolk.radius(1_000_000, RULES), "a city of a million needs a square of 354 blocks");
        assertEquals(1_768, CityFolk.radius(100_000_000, RULES));
    }

    @Test
    void everyCityWaitsForItsOwnNewcomer() {
        CitizenRules rules = CitizenRules.DEFAULT;
        long interval = rules.growthIntervalTicks();
        assertEquals(CitizenNeeds.GrowthCheck.READY,
                CityFolk.check(CityFolk.Newcomer.NAMED, 10, 10, 0, PLENTY, Long.MAX_VALUE, 50, rules));
        assertEquals(CitizenNeeds.GrowthCheck.TOO_SOON,
                CityFolk.check(CityFolk.Newcomer.FOLK, 64, 500, 0, PLENTY, interval - 1, 50, rules));
        assertEquals(CitizenNeeds.GrowthCheck.READY,
                CityFolk.check(CityFolk.Newcomer.FOLK, 64, 500, 1, PLENTY, Math.round(interval * CitizenNeeds.HOUSE_GROWTH_FACTOR), 50, rules),
                "free beds in houses bring newcomers sooner");
        assertEquals(CitizenNeeds.GrowthCheck.NO_FREE_BED,
                CityFolk.check(CityFolk.Newcomer.NONE, 64, 500, 0, PLENTY, Long.MAX_VALUE, 50, rules));
        // The townsfolk eat too: 64 named citizens and 1000 townsfolk need 6 points each for 3 days.
        assertEquals(CitizenNeeds.GrowthCheck.NOT_ENOUGH_FOOD,
                CityFolk.check(CityFolk.Newcomer.FOLK, 64, 1_064, 0, 1_064L * 6 * 3, Long.MAX_VALUE, 50, rules));
        assertEquals(CitizenNeeds.GrowthCheck.READY,
                CityFolk.check(CityFolk.Newcomer.FOLK, 64, 1_064, 0, 1_065L * 6 * 3, Long.MAX_VALUE, 50, rules));
    }
}
