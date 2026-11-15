package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.road.CityLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The demand from abroad for what the citizens of a city make, worth a day: what the trading houses of the city can sell abroad.
 * It is the only way the work of citizens brings new money, so it has its own bound in every city.
 *
 * <p>A city of a level wants a base worth and some more for every square root of its people; each road to another city raises it
 * by a share, up to twice, and a working market by a factor; a kingdom at war or under blockade sells nothing abroad. The market
 * abroad is one for the whole kingdom: its cities by their demand, the first few count in full and the city numbered {@code i}
 * (from 0) after them with the weight {@code full / i}, so that a kingdom does not print money by the number of its cities.
 */
public final class OutsideDemand {
    private OutsideDemand() {
    }

    /**
     * The demand from abroad of one city, worth a day.
     *
     * @param people the people of the city, named and townsfolk
     * @param roads  roads from the city to other cities
     * @param market whether the city has a working market
     * @param cutOff whether its kingdom is at war or under blockade
     */
    public static double of(CityLevel level, long people, int roads, boolean market, boolean cutOff, CraftRules rules) {
        if (cutOff) {
            return 0;
        }
        double base = rules.outsideDemand(level) + rules.outsideDemandPerRootPerson() * Math.sqrt(Math.max(0, people));
        return base * roadFactor(roads, rules) * (market ? rules.marketDemandFactor() : 1.0);
    }

    /**
     * How roads to other cities raise the demand: a share for each road, up to twice.
     */
    public static double roadFactor(int roads, CraftRules rules) {
        return Math.min(CraftRules.MAX_ROAD_FACTOR, 1.0 + Math.max(0, roads) * rules.roadLinkPercent() / 100.0);
    }

    /**
     * The weight of the city numbered {@code index} (from 0) among the cities of a kingdom sorted by their demand.
     */
    public static double weight(int index, int fullCities) {
        int full = Math.max(1, fullCities);
        return index < full ? 1.0 : full / (double) index;
    }

    /**
     * The demand of every city of a kingdom as the one market abroad sees it: by their own demand, the first in full, the rest
     * with less and less weight. The result is in the order the demands came in.
     */
    public static double[] weighted(double[] demands, int fullCities) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < demands.length; i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer i) -> -demands[i]).thenComparingInt(i -> i));
        double[] result = new double[demands.length];
        for (int rank = 0; rank < order.size(); rank++) {
            int city = order.get(rank);
            result[city] = Math.max(0, demands[city]) * weight(rank, fullCities);
        }
        return result;
    }
}
