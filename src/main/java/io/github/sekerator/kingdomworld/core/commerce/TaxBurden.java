package io.github.sekerator.kingdomworld.core.commerce;

import java.util.List;

/**
 * How heavy the taxes of a kingdom weigh on it, for its stability and its nobles: the heavier of the poll tax of its subjects
 * against the usual one and of the taxes of its cities (sales tax and tithe, weighed by the people of each city, the named and
 * the townsfolk) against {@value #USUAL_CITY_BURDEN} percent, in percent. With the defaults both are 100.
 */
public final class TaxBurden {
    /** The burden of the taxes of a city the people take as usual, in percent. */
    public static final int USUAL_CITY_BURDEN = 20;
    /** The most the burden is counted as, in percent. */
    public static final int MAX_PERCENT = 1_000;

    private TaxBurden() {
    }

    /**
     * The taxes of one city and the people who pay them.
     *
     * @param burdenPermille sales tax and tithe together, in thousandths
     */
    public record City(int burdenPermille, long people) {
    }

    /**
     * The poll tax against the usual one, in percent; 100 without a usual tax.
     */
    public static int pollPercent(long pollTax, long usual) {
        return usual <= 0 ? 100 : (int) Math.min(MAX_PERCENT, Math.max(0, pollTax) * 100 / usual);
    }

    /**
     * The taxes of the cities weighed by their people, against the usual burden, in percent; 0 without people.
     */
    public static int cityPercent(List<City> cities) {
        long people = 0;
        long weighed = 0;
        for (City city : cities) {
            long count = Math.max(0, city.people());
            people += count;
            weighed += count * Math.max(0, city.burdenPermille());
        }
        if (people <= 0) {
            return 0;
        }
        // permille / 10 = percent; against 20 percent: x 100 / 20.
        long percent = weighed / people * 100 / (USUAL_CITY_BURDEN * 10L);
        return (int) Math.min(MAX_PERCENT, percent);
    }

    /**
     * The heavier of the poll tax and the taxes of the cities, in percent.
     */
    public static int percent(long pollTax, long usual, List<City> cities) {
        return Math.max(pollPercent(pollTax, usual), cityPercent(cities));
    }

    /**
     * Whether the taxes of the cities weigh more than the poll tax: a demand to lower the taxes lowers them, not the poll tax.
     */
    public static boolean citiesHeavier(long pollTax, long usual, List<City> cities) {
        return cityPercent(cities) > pollPercent(pollTax, usual);
    }
}
