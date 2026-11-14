package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.road.CityPeople;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The city every citizen of a kingdom of players lives, counts and pays in, by the rule of the beds and homes of
 * {@link CityPeople}: the city of its home; a citizen without one, the city of its post by the same rule; else the capital.
 *
 * <p>Worked out for the whole kingdom on its payday, one pass over its citizens, and kept until the next one; a citizen who came
 * in between is looked up alone. The rule of the cities of a kingdom is never walked for every call.
 */
public final class CityOf {
    private static final Map<UUID, Map<UUID, Long>> CACHE = new HashMap<>();

    private CityOf() {
    }

    public static void reset() {
        CACHE.clear();
    }

    /**
     * Whether the money of a kingdom stays in its cities: a kingdom of players while the key is on.
     */
    public static boolean active(MinecraftServer server, UUID kingdomId) {
        return CityEconomySettings.enabled() && KingdomWorldApi.kingdom(server, kingdomId).isPresent()
                && !io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, kingdomId);
    }

    /**
     * Works out the city of every citizen of a kingdom again.
     *
     * @return the city of each citizen
     */
    public static Map<UUID, Long> refresh(MinecraftServer server, UUID kingdomId) {
        List<CityWalls.Place> places = CityPeople.places(server, kingdomId);
        Map<Long, Integer> index = CityPeople.index(places);
        Map<UUID, Long> cities = new HashMap<>();
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId)) {
            cities.put(citizen.id(), compute(server, kingdomId, places, index, citizen));
        }
        CACHE.put(kingdomId, cities);
        return Collections.unmodifiableMap(cities);
    }

    /**
     * The city of a citizen, 0 for the capital.
     */
    public static long of(MinecraftServer server, Citizen citizen) {
        Map<UUID, Long> known = CACHE.get(citizen.kingdomId());
        Long city = known == null ? null : known.get(citizen.id());
        if (city != null) {
            return city;
        }
        List<CityWalls.Place> places = CityPeople.places(server, citizen.kingdomId());
        long found = compute(server, citizen.kingdomId(), places, CityPeople.index(places), citizen);
        CACHE.computeIfAbsent(citizen.kingdomId(), k -> new HashMap<>()).put(citizen.id(), found);
        return found;
    }

    /**
     * The city of every citizen of a kingdom as last worked out; empty before its first payday.
     */
    public static Map<UUID, Long> known(UUID kingdomId) {
        Map<UUID, Long> known = CACHE.get(kingdomId);
        return known == null ? Map.of() : Collections.unmodifiableMap(known);
    }

    /**
     * The named citizens of each city of a kingdom, as last worked out.
     */
    public static Map<Long, Integer> counts(UUID kingdomId) {
        Map<Long, Integer> counts = new HashMap<>();
        for (Long city : known(kingdomId).values()) {
            counts.merge(city, 1, Integer::sum);
        }
        return counts;
    }

    private static long compute(MinecraftServer server, UUID kingdomId, List<CityWalls.Place> places, Map<Long, Integer> index, Citizen citizen) {
        WorldPos home = citizen.home();
        if (home != null) {
            long city = CityPeople.cityIdOf(server, kingdomId, places, index, home);
            if (city >= 0) {
                return city;
            }
        }
        var post = PostWork.postOf(server, citizen);
        if (post.isPresent()) {
            long city = CityPeople.cityIdOf(server, kingdomId, places, index, post.get().pos());
            if (city >= 0) {
                return city;
            }
        }
        return 0;
    }

    /**
     * Forgets a kingdom, dissolved or annexed.
     */
    public static void forget(UUID kingdomId) {
        CACHE.remove(kingdomId);
    }
}
