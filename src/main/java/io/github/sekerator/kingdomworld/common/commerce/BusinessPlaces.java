package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.common.road.CityPeople;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The places where the shops of citizens may stand in the cities of a kingdom: working houses of merchants and craftsmen (one
 * shop for two keepers each), the stalls of working markets (two at the first level and one more a level, one keeper each) and
 * the counters players put up in the squares of the cities of their kingdom (one shop for two keepers each). Buildings come
 * from the index of the working buildings of the kingdom, never from walking every building of the world; the places of a
 * kingdom are worked out once a day.
 */
public final class BusinessPlaces {
    /** A place of a shop and how it trades. */
    public record Spot(Business.Place place, int marketLevel) {
        public WorldPos pos() {
            return new WorldPos(place.dimension(), place.x(), place.y(), place.z());
        }
    }

    /** The kind of the template of each building, known once. */
    private static final Map<String, Optional<TemplateCatalog.Kind>> KINDS = new HashMap<>();
    /** The places of each kingdom by city, and the day and the version of the buildings they were worked out for. */
    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    private record Cached(long day, long version, int counters, Map<Long, List<Spot>> byCity) {
    }

    private BusinessPlaces() {
    }

    public static void reset() {
        KINDS.clear();
        CACHE.clear();
    }

    private static Optional<TemplateCatalog.Kind> kindOf(Building building) {
        return KINDS.computeIfAbsent(building.templateId(), id -> TemplateCatalog.get(id).map(TemplateCatalog.Entry::kind));
    }

    /**
     * The places of the shops of a city of a kingdom.
     */
    public static List<Spot> of(MinecraftServer server, UUID kingdom, long city) {
        return all(server, kingdom).getOrDefault(city, List.of());
    }

    /**
     * The places of the shops of every city of a kingdom, by city.
     */
    public static Map<Long, List<Spot>> all(MinecraftServer server, UUID kingdom) {
        long day = io.github.sekerator.kingdomworld.common.economy.TreasuryActions.day(server);
        long version = KingdomWorldApi.state(server).construction().version();
        int counters = CommerceSavedData.get(server).counters().size();
        Cached cached = CACHE.get(kingdom);
        if (cached != null && cached.day() == day && cached.version() == version && cached.counters() == counters) {
            return cached.byCity();
        }
        List<CityWalls.Place> places = CityPeople.places(server, kingdom);
        Map<Long, Integer> index = CityPeople.index(places);
        Map<Long, List<Spot>> byCity = new HashMap<>();
        for (BuildingEffects.Effect effect : BuildingEffects.working(server, kingdom, BuildingRoles.HOUSE)) {
            Building building = effect.building();
            Optional<TemplateCatalog.Kind> kind = kindOf(building);
            Business.PlaceKind placeKind = kind.isEmpty() ? null : kind.get() == TemplateCatalog.Kind.MERCHANT_HOUSE ? Business.PlaceKind.MERCHANT_HOUSE
                    : kind.get() == TemplateCatalog.Kind.CRAFT_HOUSE ? Business.PlaceKind.CRAFT_HOUSE : null;
            if (placeKind == null) {
                continue;
            }
            long city = CityPeople.cityIdOf(server, kingdom, places, index, building.anchor());
            if (city < 0) {
                continue;
            }
            WorldPos at = building.anchor();
            byCity.computeIfAbsent(city, k -> new ArrayList<>()).add(new Spot(new Business.Place(placeKind, building.id(), 0, at.dimension(),
                    at.x(), at.y(), at.z()), 0));
        }
        for (BuildingEffects.Effect effect : BuildingEffects.working(server, kingdom, BuildingRoles.MARKET)) {
            Building building = effect.building();
            long city = CityPeople.cityIdOf(server, kingdom, places, index, building.anchor());
            if (city < 0) {
                continue;
            }
            int stalls = (int) Math.round(effect.param(BuildingRoles.STALLS));
            if (stalls <= 0) {
                stalls = BuildingRoles.stalls(building.level());
            }
            WorldPos at = building.anchor();
            for (int i = 0; i < stalls; i++) {
                byCity.computeIfAbsent(city, k -> new ArrayList<>()).add(new Spot(new Business.Place(Business.PlaceKind.MARKET_STALL, building.id(), i,
                        at.dimension(), at.x(), at.y(), at.z()), building.level()));
            }
        }
        for (CommerceSavedData.Counter counter : CommerceSavedData.get(server).counters().values()) {
            if (!counter.kingdom().equals(kingdom)) {
                continue;
            }
            Optional<Long> city = counterCity(server, kingdom, counter.dimension(), counter.x(), counter.z());
            if (city.isEmpty()) {
                continue;
            }
            byCity.computeIfAbsent(city.get(), k -> new ArrayList<>()).add(new Spot(new Business.Place(Business.PlaceKind.COUNTER, 0, 0,
                    counter.dimension(), counter.x(), counter.y(), counter.z()), 0));
        }
        CACHE.put(kingdom, new Cached(day, version, counters, byCity));
        return byCity;
    }

    /**
     * The city whose square holds a counter of a kingdom, on the land of that kingdom; empty where a counter may not trade.
     */
    public static Optional<Long> counterCity(MinecraftServer server, UUID kingdom, String dimension, int x, int z) {
        UUID owner = KingdomWorldApi.state(server).territory().ownerOf(dimension, ChunkKey.of(x >> 4, z >> 4));
        if (!kingdom.equals(owner)) {
            return Optional.empty();
        }
        return CityAreas.at(server, kingdom, dimension, x, z).map(CityAreas.Area::cityId).filter(city -> city >= 0);
    }

    /**
     * Forgets the places worked out for a kingdom, after a counter came or went.
     */
    public static void changed(UUID kingdom) {
        CACHE.remove(kingdom);
    }
}
