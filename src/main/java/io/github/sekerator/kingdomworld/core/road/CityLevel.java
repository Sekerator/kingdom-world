package io.github.sekerator.kingdomworld.core.road;

import java.util.Locale;

/**
 * How big a city is: a village, a town or a large city, by the people who live around it and the buildings standing there.
 * A city reaches a level when both its population and its buildings reach the thresholds of that level.
 */
public enum CityLevel {
    VILLAGE,
    TOWN,
    CITY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The level of a city.
     *
     * @param people    citizens living around it
     * @param buildings buildings standing around it
     * @param rules     the thresholds
     */
    public static CityLevel of(int people, int buildings, RoadRules rules) {
        if (people >= rules.cityPeople() && buildings >= rules.cityBuildings()) {
            return CITY;
        }
        if (people >= rules.townPeople() && buildings >= rules.townBuildings()) {
            return TOWN;
        }
        return VILLAGE;
    }

    /**
     * The radius of the walls of a city of this level, in blocks.
     */
    public int wallRadius(RoadRules rules) {
        return switch (this) {
            case VILLAGE -> rules.wallRadius();
            case TOWN -> rules.wallRadius() * 3 / 2;
            case CITY -> rules.wallRadius() * 2;
        };
    }
}
