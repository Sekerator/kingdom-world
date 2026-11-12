package io.github.sekerator.kingdomworld.core.road;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The cities, roads, requests to cross the land of others, camps of robbers and ways of caravans of a world. Rights are
 * checked by the server before it changes anything here; this keeps the rules of the things themselves: limits of cities
 * and their distance, the consent of every kingdom a road crosses.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class RoadState {
    private final Map<Long, City> cities = new LinkedHashMap<>();
    private final Map<Long, Road> roads = new LinkedHashMap<>();
    private final Map<Long, Crossing> crossings = new LinkedHashMap<>();
    private final Map<Long, BanditCamp> camps = new LinkedHashMap<>();
    private final Map<Long, CaravanRoute> routes = new LinkedHashMap<>();
    private final Map<Long, Wall> walls = new LinkedHashMap<>();
    /** Kingdoms that let the roads of another cross their land, as owner and requester. */
    private final java.util.Set<String> consents = new java.util.LinkedHashSet<>();
    private long nextWall = 1;
    private long nextCity = 1;
    private long nextRoad = 1;
    private long nextCrossing = 1;
    private long nextCamp = 1;
    private long version;
    private Runnable listener = () -> {
    };

    /**
     * What answering a request did to its road.
     *
     * @param outcome  whether the answer was taken
     * @param started  the road, if every kingdom has now agreed and it can be built
     * @param dropped  the road, if it was given up because a kingdom declined
     */
    public record Answer(Outcome outcome, Optional<Road> started, Optional<Road> dropped) {
        static Answer fail(KingdomError error) {
            return new Answer(Outcome.fail(error), Optional.empty(), Optional.empty());
        }
    }

    private boolean rememberConsent = true;

    /**
     * Whether consent to cross land is remembered for the next roads of the same kingdom.
     */
    public void setRememberConsent(boolean value) {
        rememberConsent = value;
    }

    /**
     * Whether an owner of land let a road of a kingdom of this mode cross it before, and that is remembered. Only roads the
     * requester builds and pays for alone ({@link Road.Mode#PAYER}) pass without a new request; a joint road makes the owner
     * build and pay on its land, so the owner is always asked.
     */
    public boolean consented(UUID owner, UUID requester, Road.Mode mode) {
        return rememberConsent && mode == Road.Mode.PAYER && consents.contains(consentKey(owner, requester, mode));
    }

    private static String consentKey(UUID owner, UUID requester, Road.Mode mode) {
        return owner + ">" + requester + ">" + mode.id();
    }

    public java.util.Set<String> consents() {
        return java.util.Collections.unmodifiableSet(consents);
    }

    /**
     * Restores a remembered consent as {@link #consents()} lists it, {@code owner>requester>mode}. Anything else, a consent
     * that does not name its mode among them, is not taken: the owner is asked again.
     */
    public void restoreConsent(String entry) {
        String[] parts = entry.split(">");
        if (parts.length == 3 && parts[2].equals(Road.Mode.PAYER.id())) {
            try {
                consents.add(consentKey(UUID.fromString(parts[0]), UUID.fromString(parts[1]), Road.Mode.PAYER));
            } catch (IllegalArgumentException e) {
                // Not two kingdoms: nothing is remembered.
            }
        }
    }

    // ---------------------------------------------------------------- walls

    public Collection<Wall> walls() {
        return Collections.unmodifiableCollection(walls.values());
    }

    public Optional<Wall> wall(long id) {
        return Optional.ofNullable(walls.get(id));
    }

    /**
     * The wall of a city; for a capital the city id is 0 and the kingdom tells it.
     */
    public Optional<Wall> wallOf(long cityId, UUID kingdomId) {
        for (Wall wall : walls.values()) {
            if (wall.cityId() == cityId && (cityId != 0 || wall.kingdomId().equals(kingdomId))) {
                return Optional.of(wall);
            }
        }
        return Optional.empty();
    }

    public long nextWallId() {
        return nextWall++;
    }

    public void putWall(Wall wall) {
        walls.put(wall.id(), wall);
        nextWall = Math.max(nextWall, wall.id() + 1);
        changed();
    }

    public Optional<Wall> removeWall(long id) {
        Wall removed = walls.remove(id);
        if (removed != null) {
            changed();
        }
        return Optional.ofNullable(removed);
    }

    public void setChangeListener(Runnable value) {
        listener = Objects.requireNonNull(value, "listener");
    }

    /**
     * Marks a change of the cities, roads, walls, crossings, camps or routes: the version grows and the listener hears of it.
     */
    public void changed() {
        version++;
        listener.run();
    }

    /**
     * Grows with every {@link #changed()}: what is built from the roads and cities (the graph of the caravans, the areas of the
     * cities, the maps of the players) compares it to know when to build again.
     */
    public long version() {
        return version;
    }

    // ---------------------------------------------------------------- cities

    public Collection<City> cities() {
        return Collections.unmodifiableCollection(cities.values());
    }

    public Optional<City> city(long id) {
        return Optional.ofNullable(cities.get(id));
    }

    public List<City> citiesOf(UUID kingdomId) {
        List<City> result = new ArrayList<>();
        for (City city : cities.values()) {
            if (city.kingdomId().equals(kingdomId)) {
                result.add(city);
            }
        }
        return result;
    }

    public Optional<City> cityAt(String dimension, int x, int y, int z) {
        for (City city : cities.values()) {
            if (city.dimension().equals(dimension) && city.x() == x && city.y() == y && city.z() == z) {
                return Optional.of(city);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a kingdom may found a city at a place: it has fewer than the most, and no other city or capital is near.
     *
     * @param capitals the capitals of all kingdoms in the dimension, as x and z
     */
    public Outcome checkCity(UUID kingdomId, String dimension, int x, int z, Collection<int[]> capitals, RoadRules rules) {
        if (citiesOf(kingdomId).size() >= rules.maxCities()) {
            return Outcome.fail(KingdomError.TOO_MANY_CITIES);
        }
        double min = (double) rules.minCityDistance() * rules.minCityDistance();
        for (City city : cities.values()) {
            if (city.dimension().equals(dimension) && city.distanceSqr(x, z) < min) {
                return Outcome.fail(KingdomError.CITY_TOO_CLOSE);
            }
        }
        for (int[] capital : capitals) {
            double dx = capital[0] - x;
            double dz = capital[1] - z;
            if (dx * dx + dz * dz < min) {
                return Outcome.fail(KingdomError.CITY_TOO_CLOSE);
            }
        }
        return Outcome.DONE;
    }

    public City addCity(UUID kingdomId, String name, String dimension, int x, int y, int z, long founded) {
        City city = new City(nextCity++, kingdomId, name, dimension, x, y, z, founded);
        cities.put(city.id(), city);
        changed();
        return city;
    }

    public void putCity(City city) {
        cities.put(city.id(), city);
        nextCity = Math.max(nextCity, city.id() + 1);
        changed();
    }

    public Optional<City> removeCity(long id) {
        City removed = cities.remove(id);
        if (removed != null) {
            changed();
        }
        return Optional.ofNullable(removed);
    }

    // ---------------------------------------------------------------- roads

    public Collection<Road> roads() {
        return Collections.unmodifiableCollection(roads.values());
    }

    public Optional<Road> road(long id) {
        return Optional.ofNullable(roads.get(id));
    }

    /**
     * Roads a kingdom planned or builds a part of.
     */
    public List<Road> roadsOf(UUID kingdomId) {
        List<Road> result = new ArrayList<>();
        for (Road road : roads.values()) {
            if (road.kingdomId().equals(kingdomId) || road.builders().contains(kingdomId)) {
                result.add(road);
            }
        }
        return result;
    }

    public long ownedRoads(UUID kingdomId) {
        return roads.values().stream().filter(road -> road.kingdomId().equals(kingdomId)).count();
    }

    public long nextRoadId() {
        return nextRoad;
    }

    /**
     * Adds a planned road: building starts at once when it crosses no other kingdom; otherwise it waits for an answer to
     * a request sent to each of them.
     *
     * @param owners  the kingdom whose land most of each section is on, or {@code null} for wild land
     * @param crossed every other kingdom whose land the road crosses, with the sections that touch it
     * @param mode    who builds on the land of others
     * @param sent    receives the requests sent
     */
    public Road addRoad(UUID kingdomId, RoadClass roadClass, String dimension, Endpoint from, Endpoint to, List<RouteCell> cells,
                        List<UUID> owners, Map<UUID, Integer> crossed, Road.Mode mode, long now, long expires, List<Crossing> sent) {
        Map<UUID, Integer> foreign = new LinkedHashMap<>(crossed);
        foreign.remove(kingdomId);
        Road.Mode actual = foreign.isEmpty() ? Road.Mode.OWN : mode == Road.Mode.OWN ? Road.Mode.PAYER : mode;
        List<UUID> builders = new ArrayList<>();
        for (UUID owner : owners) {
            boolean other = owner != null && !owner.equals(kingdomId) && foreign.containsKey(owner);
            builders.add(other && actual == Road.Mode.JOINT ? owner : kingdomId);
        }
        boolean waiting = false;
        for (UUID owner : foreign.keySet()) {
            waiting |= !consented(owner, kingdomId, actual);
        }
        Road road = new Road(nextRoad++, kingdomId, roadClass, dimension, from, to, cells, actual, now,
                waiting ? Road.Status.WAITING_CONSENT : Road.Status.BUILDING, builders);
        roads.put(road.id(), road);
        for (Map.Entry<UUID, Integer> entry : foreign.entrySet()) {
            // A kingdom that let roads of this one, built at its cost, cross its land before is not asked again for such a road.
            boolean known = consented(entry.getKey(), kingdomId, actual);
            Crossing crossing = new Crossing(nextCrossing++, road.id(), kingdomId, entry.getKey(), actual,
                    known ? Crossing.Status.ACCEPTED : Crossing.Status.PROPOSED, entry.getValue(), now, Math.max(now, expires));
            crossings.put(crossing.id(), crossing);
            if (!known) {
                sent.add(crossing);
            }
        }
        changed();
        return road;
    }

    /**
     * The kingdom whose land most of each section of a way is on, {@code null} for wild land.
     */
    public static List<UUID> sectionOwners(List<RouteCell> cells) {
        List<UUID> owners = new ArrayList<>();
        for (int first = 0; first < cells.size(); first += Road.SECTION_CELLS) {
            Map<UUID, Integer> counts = new LinkedHashMap<>();
            int wild = 0;
            for (int i = first; i < Math.min(cells.size(), first + Road.SECTION_CELLS); i++) {
                UUID owner = cells.get(i).owner();
                if (owner == null) {
                    wild++;
                } else {
                    counts.merge(owner, 1, Integer::sum);
                }
            }
            UUID best = null;
            int bestCount = wild;
            for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > bestCount) {
                    best = entry.getKey();
                    bestCount = entry.getValue();
                }
            }
            owners.add(best);
        }
        return owners;
    }

    /**
     * Every kingdom other than the builder whose land a way touches, with the sections that touch it.
     */
    public static Map<UUID, Integer> crossedSections(List<RouteCell> cells, UUID builder) {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (int first = 0; first < cells.size(); first += Road.SECTION_CELLS) {
            java.util.Set<UUID> seen = new java.util.HashSet<>();
            for (int i = first; i < Math.min(cells.size(), first + Road.SECTION_CELLS); i++) {
                UUID owner = cells.get(i).owner();
                if (owner != null && !owner.equals(builder) && seen.add(owner)) {
                    result.merge(owner, 1, Integer::sum);
                }
            }
        }
        return result;
    }

    public void putRoad(Road road) {
        roads.put(road.id(), road);
        nextRoad = Math.max(nextRoad, road.id() + 1);
        changed();
    }

    /**
     * Removes a road and its requests.
     */
    public Optional<Road> removeRoad(long id) {
        Road removed = roads.remove(id);
        if (removed != null) {
            crossings.values().removeIf(crossing -> crossing.roadId() == id && crossing.status().open());
            changed();
        }
        return Optional.ofNullable(removed);
    }

    // ---------------------------------------------------------------- crossings

    public Collection<Crossing> crossings() {
        return Collections.unmodifiableCollection(crossings.values());
    }

    public Optional<Crossing> crossing(long id) {
        return Optional.ofNullable(crossings.get(id));
    }

    public List<Crossing> crossingsOf(UUID kingdomId) {
        List<Crossing> result = new ArrayList<>();
        for (Crossing crossing : crossings.values()) {
            if (crossing.requester().equals(kingdomId) || crossing.owner().equals(kingdomId)) {
                result.add(crossing);
            }
        }
        return result;
    }

    public List<Crossing> crossingsOfRoad(long roadId) {
        List<Crossing> result = new ArrayList<>();
        for (Crossing crossing : crossings.values()) {
            if (crossing.roadId() == roadId) {
                result.add(crossing);
            }
        }
        return result;
    }

    public void putCrossing(Crossing crossing) {
        crossings.put(crossing.id(), crossing);
        nextCrossing = Math.max(nextCrossing, crossing.id() + 1);
        changed();
    }

    /**
     * The owner of the land answers a request. When every kingdom the road crosses has agreed, building starts; when one
     * declines, the road is given up.
     *
     * @param kingdomId the kingdom answering; only the owner of the land may
     */
    public Answer answer(long crossingId, UUID kingdomId, boolean accept) {
        Crossing crossing = crossings.get(crossingId);
        if (crossing == null) {
            return Answer.fail(KingdomError.UNKNOWN_CROSSING);
        }
        if (!crossing.owner().equals(kingdomId)) {
            return Answer.fail(KingdomError.NO_PERMISSION);
        }
        if (!crossing.status().open()) {
            return Answer.fail(KingdomError.CROSSING_CLOSED);
        }
        crossings.put(crossing.id(), crossing.withStatus(accept ? Crossing.Status.ACCEPTED : Crossing.Status.DECLINED));
        if (accept && rememberConsent && crossing.mode() == Road.Mode.PAYER) {
            consents.add(consentKey(crossing.owner(), crossing.requester(), crossing.mode()));
        }
        return settle(crossing.roadId());
    }

    /**
     * Requests nobody answered in time run out, and their roads are given up.
     *
     * @return the roads given up
     */
    public List<Road> expire(long now) {
        List<Long> roadIds = new ArrayList<>();
        for (Crossing crossing : new ArrayList<>(crossings.values())) {
            if (crossing.status().open() && now >= crossing.expires()) {
                crossings.put(crossing.id(), crossing.withStatus(Crossing.Status.EXPIRED));
                roadIds.add(crossing.roadId());
            }
        }
        List<Road> dropped = new ArrayList<>();
        for (long roadId : roadIds) {
            settle(roadId).dropped().ifPresent(dropped::add);
        }
        return dropped;
    }

    private Answer settle(long roadId) {
        Road road = roads.get(roadId);
        if (road == null) {
            changed();
            return new Answer(Outcome.DONE, Optional.empty(), Optional.empty());
        }
        boolean all = true;
        for (Crossing other : crossingsOfRoad(roadId)) {
            if (other.status() == Crossing.Status.DECLINED || other.status() == Crossing.Status.EXPIRED) {
                for (Crossing open : crossingsOfRoad(roadId)) {
                    if (open.status().open()) {
                        crossings.put(open.id(), open.withStatus(Crossing.Status.EXPIRED));
                    }
                }
                roads.remove(roadId);
                changed();
                return new Answer(Outcome.DONE, Optional.empty(), Optional.of(road));
            }
            all &= other.status() == Crossing.Status.ACCEPTED;
        }
        if (all && road.status() == Road.Status.WAITING_CONSENT) {
            road.setStatus(Road.Status.BUILDING);
            changed();
            return new Answer(Outcome.DONE, Optional.of(road), Optional.empty());
        }
        changed();
        return new Answer(Outcome.DONE, Optional.empty(), Optional.empty());
    }

    /**
     * Forgets answered requests older than a time, keeping open ones.
     */
    public void prune(long before) {
        if (crossings.values().removeIf(crossing -> !crossing.status().open() && crossing.expires() < before)) {
            changed();
        }
    }

    // ---------------------------------------------------------------- camps

    public Collection<BanditCamp> camps() {
        return Collections.unmodifiableCollection(camps.values());
    }

    public Optional<BanditCamp> camp(long id) {
        return Optional.ofNullable(camps.get(id));
    }

    public BanditCamp addCamp(String dimension, int x, int y, int z, int bandits, boolean leader, long day) {
        BanditCamp camp = new BanditCamp(nextCamp++, dimension, x, y, z, bandits, leader, day);
        camps.put(camp.id(), camp);
        changed();
        return camp;
    }

    public void putCamp(BanditCamp camp) {
        camps.put(camp.id(), camp);
        nextCamp = Math.max(nextCamp, camp.id() + 1);
        changed();
    }

    public Optional<BanditCamp> removeCamp(long id) {
        BanditCamp removed = camps.remove(id);
        if (removed != null) {
            changed();
        }
        return Optional.ofNullable(removed);
    }

    /**
     * The camp with robbers nearest to a place, within a distance.
     */
    public Optional<BanditCamp> nearestCamp(String dimension, int x, int z, double within) {
        BanditCamp best = null;
        double bestDistance = within * within;
        for (BanditCamp camp : camps.values()) {
            if (!camp.dimension().equals(dimension) || camp.cleared()) {
                continue;
            }
            double distance = camp.distanceSqr(x, z);
            if (distance <= bestDistance) {
                best = camp;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    // ---------------------------------------------------------------- caravans

    public Collection<CaravanRoute> routes() {
        return Collections.unmodifiableCollection(routes.values());
    }

    public Optional<CaravanRoute> route(long caravanId) {
        return Optional.ofNullable(routes.get(caravanId));
    }

    public void putRoute(CaravanRoute route) {
        routes.put(route.caravanId(), route);
        changed();
    }

    public void removeRoute(long caravanId) {
        if (routes.remove(caravanId) != null) {
            changed();
        }
    }

    // ---------------------------------------------------------------- kingdoms

    /**
     * A kingdom is gone: its cities, the roads it planned and its open requests go with it; sections it built for the
     * roads of others fall to their planners. The roads in the world stay.
     *
     * @return the roads removed
     */
    public List<Road> forgetKingdom(UUID kingdomId) {
        cities.values().removeIf(city -> city.kingdomId().equals(kingdomId));
        walls.values().removeIf(wall -> wall.kingdomId().equals(kingdomId));
        consents.removeIf(pair -> pair.contains(kingdomId.toString()));
        List<Road> removed = new ArrayList<>();
        for (Road road : new ArrayList<>(roads.values())) {
            if (road.kingdomId().equals(kingdomId)) {
                removeRoad(road.id());
                removed.add(road);
            } else {
                for (Road.Section section : road.sections()) {
                    if (section.builder().equals(kingdomId)) {
                        section.setBuilder(road.kingdomId());
                    }
                }
            }
        }
        for (Crossing crossing : new ArrayList<>(crossings.values())) {
            if (crossing.status().open() && (crossing.requester().equals(kingdomId) || crossing.owner().equals(kingdomId))) {
                crossings.put(crossing.id(), crossing.withStatus(Crossing.Status.EXPIRED));
                settle(crossing.roadId()).dropped().ifPresent(removed::add);
            }
        }
        for (BanditCamp camp : camps.values()) {
            camp.forget(kingdomId);
        }
        changed();
        return removed;
    }

    /**
     * Problems with what was read: requests of roads that are gone are dropped.
     */
    public List<String> verify() {
        List<String> problems = new ArrayList<>();
        for (Crossing crossing : new ArrayList<>(crossings.values())) {
            if (crossing.status().open() && !roads.containsKey(crossing.roadId())) {
                crossings.remove(crossing.id());
                problems.add("request " + crossing.id() + " of a missing road " + crossing.roadId());
            }
        }
        return problems;
    }

    public long[] counters() {
        return new long[]{nextCity, nextRoad, nextCrossing, nextCamp, nextWall};
    }

    public void restoreWallCounter(long wall) {
        nextWall = Math.max(nextWall, wall);
    }

    public void restoreCounters(long city, long road, long crossing, long camp) {
        nextCity = Math.max(nextCity, city);
        nextRoad = Math.max(nextRoad, road);
        nextCrossing = Math.max(nextCrossing, crossing);
        nextCamp = Math.max(nextCamp, camp);
    }
}
