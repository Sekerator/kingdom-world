package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.road.CityPeople;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.CraftRules;
import io.github.sekerator.kingdomworld.core.commerce.OutsideDemand;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.Road;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What ties the cities of a kingdom of players together and to the world abroad, worked out once a game day: the roads from each
 * city to other cities, the nearest larger city of the kingdom each city reaches by road (its regional market), and the demand
 * from abroad for what the citizens of each city make, weighted over the kingdom.
 */
public final class CityTies {
    /**
     * The ties of the cities of a kingdom on a day.
     *
     * @param roads    roads from each city to other cities
     * @param regional the nearest larger city each city reaches by road, absent for none
     * @param demand   the demand from abroad of each city as the one market abroad sees it, worth a day
     * @param level    the level of each city
     */
    public record Ties(long day, Map<Long, Integer> roads, Map<Long, Long> regional, Map<Long, Double> demand, Map<Long, CityLevel> level) {
        public double demand(long city) {
            return demand.getOrDefault(city, 0.0);
        }

        public CityLevel level(long city) {
            return level.getOrDefault(city, CityLevel.VILLAGE);
        }
    }

    private static final Map<UUID, Ties> CACHE = new HashMap<>();

    private CityTies() {
    }

    public static void reset() {
        CACHE.clear();
    }

    /**
     * The ties of the cities of a kingdom today.
     */
    public static Ties of(MinecraftServer server, UUID kingdom) {
        long day = TreasuryActions.day(server);
        Ties known = CACHE.get(kingdom);
        if (known != null && known.day() == day) {
            return known;
        }
        Ties ties = build(server, kingdom, day);
        CACHE.put(kingdom, ties);
        return ties;
    }

    private static Ties build(MinecraftServer server, UUID kingdom, long day) {
        List<CityWalls.Place> places = CityPeople.places(server, kingdom);
        Set<Long> ours = new HashSet<>();
        for (CityWalls.Place place : places) {
            ours.add(place.cityId());
        }
        // The roads that are done, as edges between the cities of the kingdom; roads to cities of others count for the demand.
        Map<Long, Integer> roads = new HashMap<>();
        Map<Long, Set<Long>> edges = new HashMap<>();
        for (Road road : RoadWork.state(server).roads()) {
            if (road.status() != Road.Status.DONE) {
                continue;
            }
            Long a = cityOf(road.from(), kingdom, ours);
            Long b = cityOf(road.to(), kingdom, ours);
            boolean townA = road.from().kind() != Endpoint.Kind.POINT;
            boolean townB = road.to().kind() != Endpoint.Kind.POINT;
            if (a != null && townB && !same(road.from(), road.to())) {
                roads.merge(a, 1, Integer::sum);
            }
            if (b != null && townA && !same(road.from(), road.to())) {
                roads.merge(b, 1, Integer::sum);
            }
            if (a != null && b != null && !a.equals(b)) {
                edges.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                edges.computeIfAbsent(b, k -> new HashSet<>()).add(a);
            }
        }
        Map<Long, Integer> named = CityOf.counts(kingdom);
        Map<Long, Integer> folk = CityPeople.folkByCity(server, kingdom);
        Map<Long, Long> people = new HashMap<>();
        Map<Long, CityLevel> levels = new HashMap<>();
        Map<Long, Boolean> markets = new HashMap<>();
        Map<Long, List<BusinessPlaces.Spot>> spots = BusinessPlaces.all(server, kingdom);
        for (CityWalls.Place place : places) {
            long city = place.cityId();
            people.put(city, (long) named.getOrDefault(city, 0) + folk.getOrDefault(city, 0));
            levels.put(city, CityWalls.level(server, kingdom, city));
            boolean market = false;
            for (BusinessPlaces.Spot spot : spots.getOrDefault(city, List.of())) {
                market |= spot.place().kind() == Business.PlaceKind.MARKET_STALL;
            }
            markets.put(city, market);
        }
        // The nearest larger city reached by road.
        Map<Long, Long> regional = new HashMap<>();
        for (CityWalls.Place place : places) {
            long city = place.cityId();
            Set<Long> reached = reach(city, edges);
            long best = -1;
            double bestDistance = Double.MAX_VALUE;
            for (CityWalls.Place other : places) {
                if (other.cityId() == city || !reached.contains(other.cityId()) || !larger(other.cityId(), city, levels, people)
                        || !other.dimension().equals(place.dimension())) {
                    continue;
                }
                double distance = Math.hypot(other.x() - place.x(), other.z() - place.z());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = other.cityId();
                }
            }
            if (best >= 0) {
                regional.put(city, best);
            }
        }
        // The demand from abroad, one market for the whole kingdom.
        CraftRules rules = CraftSettings.rules();
        boolean cutOff = io.github.sekerator.kingdomworld.common.kingdom.ThroneMoving.atWar(server, kingdom);
        double[] own = new double[places.size()];
        for (int i = 0; i < places.size(); i++) {
            long city = places.get(i).cityId();
            own[i] = OutsideDemand.of(levels.get(city), people.get(city), roads.getOrDefault(city, 0), markets.get(city), cutOff, rules);
        }
        double[] weighted = OutsideDemand.weighted(own, rules.outsideDemandFullCities());
        Map<Long, Double> demand = new HashMap<>();
        for (int i = 0; i < places.size(); i++) {
            demand.put(places.get(i).cityId(), weighted[i]);
        }
        return new Ties(day, Map.copyOf(roads), Map.copyOf(regional), Map.copyOf(demand), Map.copyOf(levels));
    }

    private static boolean same(Endpoint a, Endpoint b) {
        return a.kind() == b.kind() && a.city() == b.city() && java.util.Objects.equals(a.kingdom(), b.kingdom());
    }

    /**
     * The city of the kingdom an end of a road is, or {@code null}.
     */
    private static Long cityOf(Endpoint end, UUID kingdom, Set<Long> ours) {
        if (end.kind() == Endpoint.Kind.CAPITAL) {
            return kingdom.equals(end.kingdom()) ? 0L : null;
        }
        if (end.kind() == Endpoint.Kind.CITY && end.city() != 0 && ours.contains(end.city())) {
            return end.city();
        }
        return null;
    }

    private static Set<Long> reach(long from, Map<Long, Set<Long>> edges) {
        Set<Long> seen = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty()) {
            for (Long next : edges.getOrDefault(queue.poll(), Set.of())) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    /**
     * Whether a city is larger than another: a higher level, or as high with more people.
     */
    private static boolean larger(long city, long than, Map<Long, CityLevel> levels, Map<Long, Long> people) {
        int a = levels.get(city).ordinal();
        int b = levels.get(than).ordinal();
        return a > b || a == b && people.getOrDefault(city, 0L) > people.getOrDefault(than, 0L);
    }

    /**
     * The cities of a kingdom whose townspeople come to the shops of a city as to their regional market.
     */
    public static List<Long> customersOf(MinecraftServer server, UUID kingdom, long city) {
        List<Long> customers = new ArrayList<>();
        Ties ties = of(server, kingdom);
        ties.regional().forEach((from, to) -> {
            if (to == city) {
                customers.add(from);
            }
        });
        return customers;
    }
}
