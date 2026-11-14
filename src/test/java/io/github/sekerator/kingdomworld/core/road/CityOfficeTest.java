package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who may govern a city: a living citizen of the kingdom, no soldier, governing no other city and heading no house, whose post,
 * if any, stands in the city; a home elsewhere takes nothing away.
 */
class CityOfficeTest {
    private static final long CITY = 3;
    private static final long OTHER = 5;

    /** A living citizen of the kingdom without a post, an office or a house. */
    private static CityOffice.Candidate free() {
        return new CityOffice.Candidate(true, true, false, false, -1, -1, -1);
    }

    @Test
    void aFreeCitizenMayGovernAnyCity() {
        assertEquals(CityOffice.Unfit.FIT, CityOffice.mayor(free(), CITY));
        assertEquals(CityOffice.Unfit.FIT, CityOffice.mayor(free(), 0), "the capital has a mayor too");
        assertEquals(CityOffice.Unfit.FIT, CityOffice.baron(free(), CITY));
        assertEquals(CityOffice.Unfit.FIT, CityOffice.keep(free(), CITY));
    }

    @Test
    void aPostInAnotherCityKeepsACitizenFromGoverning() {
        CityOffice.Candidate here = new CityOffice.Candidate(true, true, false, true, CITY, -1, -1);
        CityOffice.Candidate there = new CityOffice.Candidate(true, true, false, true, OTHER, -1, -1);
        CityOffice.Candidate outside = new CityOffice.Candidate(true, true, false, true, -1, -1, -1);
        assertEquals(CityOffice.Unfit.FIT, CityOffice.mayor(here, CITY));
        assertEquals(CityOffice.Unfit.POST_ELSEWHERE, CityOffice.mayor(there, CITY));
        assertEquals(CityOffice.Unfit.POST_ELSEWHERE, CityOffice.mayor(outside, CITY), "a post outside every city is elsewhere too");
        assertEquals(CityOffice.Unfit.POST_ELSEWHERE, CityOffice.keep(there, CITY), "moved to a post elsewhere, a mayor loses the office");
    }

    @Test
    void soldiersTheDeadAndCitizensOfOthersDoNotGovern() {
        assertEquals(CityOffice.Unfit.SOLDIER, CityOffice.mayor(new CityOffice.Candidate(true, true, true, false, -1, -1, -1), CITY));
        assertEquals(CityOffice.Unfit.GONE, CityOffice.mayor(new CityOffice.Candidate(false, true, false, false, -1, -1, -1), CITY));
        assertEquals(CityOffice.Unfit.OTHER_KINGDOM, CityOffice.mayor(new CityOffice.Candidate(true, false, false, false, -1, -1, -1), CITY));
        assertEquals(CityOffice.Unfit.SOLDIER, CityOffice.keep(new CityOffice.Candidate(true, true, true, false, -1, CITY, -1), CITY));
        assertEquals(CityOffice.Unfit.GONE, CityOffice.keep(new CityOffice.Candidate(false, true, false, false, -1, CITY, -1), CITY));
        assertEquals(CityOffice.Unfit.NO_CITY, CityOffice.mayor(free(), -1));
    }

    @Test
    void oneOfficeAtATime() {
        CityOffice.Candidate mayorElsewhere = new CityOffice.Candidate(true, true, false, false, -1, OTHER, -1);
        CityOffice.Candidate mayorHere = new CityOffice.Candidate(true, true, false, false, -1, CITY, -1);
        CityOffice.Candidate baron = new CityOffice.Candidate(true, true, false, false, -1, -1, OTHER);
        CityOffice.Candidate headWithoutCity = new CityOffice.Candidate(true, true, false, false, -1, -1, -2);
        assertEquals(CityOffice.Unfit.MAYOR_ELSEWHERE, CityOffice.mayor(mayorElsewhere, CITY));
        assertEquals(CityOffice.Unfit.FIT, CityOffice.baron(mayorHere, CITY), "the mayor of a city may become its baron");
        assertEquals(CityOffice.Unfit.MAYOR_ELSEWHERE, CityOffice.baron(mayorElsewhere, CITY));
        assertEquals(CityOffice.Unfit.HEAD_OF_HOUSE, CityOffice.mayor(baron, CITY));
        assertEquals(CityOffice.Unfit.HEAD_OF_HOUSE, CityOffice.baron(baron, CITY), "a citizen heads one house at most");
        assertEquals(CityOffice.Unfit.HEAD_OF_HOUSE, CityOffice.baron(headWithoutCity, CITY));
    }

    @Test
    void theCityOfACitizenIsThatOfItsPostElseOfItsHome() {
        assertEquals(CITY, CityOffice.cityFor(true, CITY, OTHER));
        assertEquals(-1, CityOffice.cityFor(true, -1, OTHER), "a post outside every city gives no city");
        assertEquals(OTHER, CityOffice.cityFor(false, -1, OTHER));
        assertEquals(-1, CityOffice.cityFor(false, -1, -1));
    }
}
