package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.FolkSettings;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.npc.SettlementResidents;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRegistry;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRules;
import io.github.sekerator.kingdomworld.core.citizen.Site;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.CityCharter;
import io.github.sekerator.kingdomworld.core.road.CityFolk;
import io.github.sekerator.kingdomworld.core.road.FolkRules;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The people of the cities of a kingdom of players: which city every bed and every named citizen belongs to, the newcomers
 * of each city and the days of their townsfolk (see {@link CityFolk}).
 *
 * <p>Every city, the capital among them, grows by itself, by its own free beds and the mood of its own citizens, so that ten
 * or a hundred cities fill at the same time; its newcomer comes to its town hall (to the throne in the capital) and moves into a
 * free bed there. A city with as many named citizens as it takes grows by its townsfolk instead. A bed or a citizen belongs to
 * the city whose square holds it, the nearest one where squares meet, and outside every square to the nearest town hall or
 * throne in its world, so that no bed of the kingdom is left out. The kingdoms of the world grow as before.
 *
 * <p>The work is one pass over the beds and the citizens of a kingdom in its step, and one day of the townsfolk a day for each
 * city: no chunk is loaded, no body is made for the townsfolk.
 */
public final class CityPeople {
    private static final String KEY = "kingdomworld.folk.";

    private CityPeople() {
    }

    /**
     * The people and beds of a city of a kingdom.
     *
     * @param place         the city (0 for the capital) and where its newcomers come: its town hall, the throne of the capital
     * @param named         the named citizens whose home is in it
     * @param mood          their average mood; that of the kingdom while it has none
     * @param beds          the beds of the kingdom in it
     * @param freeBeds      the free beds in it a citizen may live in, those a settlement of a state keeps for its own left out
     * @param freeHouseBeds of those, the beds in houses
     * @param bed           the free bed a newcomer moves into: in a house first, then the nearest to the place; {@code null} for none
     */
    public record Census(CityWalls.Place place, int named, int mood, int beds, int freeBeds, int freeHouseBeds, @Nullable WorldPos bed) {
        public long cityId() {
            return place.cityId();
        }

        WorldPos hall() {
            return new WorldPos(place.dimension(), place.x(), place.y(), place.z());
        }
    }

    /**
     * The capital and the cities of a kingdom, the capital first.
     */
    static List<CityWalls.Place> places(MinecraftServer server, UUID kingdomId) {
        List<CityWalls.Place> result = new ArrayList<>();
        CityWalls.place(server, kingdomId, 0).ifPresent(result::add);
        for (City city : RoadWork.state(server).citiesOf(kingdomId)) {
            result.add(new CityWalls.Place(kingdomId, city.id(), city.dimension(), city.x(), city.y(), city.z(), city.name()));
        }
        return result;
    }

    /**
     * The people and beds of each city of a kingdom, the capital first: one pass over its beds and citizens.
     */
    public static List<Census> census(MinecraftServer server, UUID kingdomId) {
        List<CityWalls.Place> places = places(server, kingdomId);
        int count = places.size();
        if (count == 0) {
            return List.of();
        }
        Map<Long, Integer> index = new HashMap<>();
        for (int i = 0; i < count; i++) {
            index.put(places.get(i).cityId(), i);
        }
        int[] named = new int[count];
        long[] moods = new long[count];
        int[] beds = new int[count];
        int[] free = new int[count];
        int[] houses = new int[count];
        WorldPos[] best = new WorldPos[count];
        int[] bestRank = new int[count];
        long[] bestDistance = new long[count];
        KingdomWorldState state = KingdomWorldApi.state(server);
        boolean keeps = BuildingEffects.keepsBunks(server, kingdomId);
        for (Site bed : state.citizens().sites().sitesOf(kingdomId, SiteKinds.BED)) {
            int i = cityOf(server, kingdomId, places, index, bed.pos());
            if (i < 0) {
                continue;
            }
            beds[i]++;
            if (!bed.isFree() || !BuildingEffects.bedUsable(server, kingdomId, bed.pos())
                    || (keeps && BuildingEffects.inBarracks(server, kingdomId, bed.pos()))) {
                continue;
            }
            free[i]++;
            boolean house = BuildingEffects.inHouse(server, kingdomId, bed.pos());
            if (house) {
                houses[i]++;
            }
            int rank = house ? 1 : 0;
            long distance = distance(places.get(i), bed.pos());
            if (best[i] == null || rank > bestRank[i] || (rank == bestRank[i] && distance < bestDistance[i])) {
                best[i] = bed.pos();
                bestRank[i] = rank;
                bestDistance[i] = distance;
            }
        }
        long allMood = 0;
        int all = 0;
        for (Citizen citizen : state.citizens().registry().citizensOf(kingdomId)) {
            all++;
            allMood += citizen.mood();
            if (citizen.home() == null) {
                continue;
            }
            int i = cityOf(server, kingdomId, places, index, citizen.home());
            if (i >= 0) {
                named[i]++;
                moods[i] += citizen.mood();
            }
        }
        // The free beds of a settlement of a state wait for its citizens to come back.
        String overworld = io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(server.overworld());
        for (int[] kept : SettlementResidents.bedsKeptAt(server, kingdomId)) {
            int i = cityOf(server, kingdomId, places, index, new WorldPos(overworld, kept[0], 64, kept[1]));
            if (i >= 0) {
                free[i] = Math.max(0, free[i] - kept[2]);
                houses[i] = Math.min(houses[i], free[i]);
            }
        }
        int kingdomMood = all == 0 ? CitizenNeeds.NEUTRAL_MOOD : (int) Math.round(allMood / (double) all);
        List<Census> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int mood = named[i] == 0 ? kingdomMood : (int) Math.round(moods[i] / (double) named[i]);
            result.add(new Census(places.get(i), named[i], mood, beds[i], free[i], houses[i], free[i] > 0 ? best[i] : null));
        }
        return result;
    }

    /**
     * The city of a kingdom a bed or a home belongs to, as an index into its places: the nearest of those whose square holds it,
     * else the nearest town hall or throne in its world, else the capital; -1 for none.
     */
    private static int cityOf(MinecraftServer server, UUID kingdomId, List<CityWalls.Place> places, Map<Long, Integer> index, WorldPos pos) {
        Optional<CityAreas.Area> area = CityAreas.nearestAt(server, kingdomId, pos.dimension(), pos.x(), pos.z());
        if (area.isPresent()) {
            Integer found = index.get(area.get().cityId());
            if (found != null) {
                return found;
            }
        }
        int nearest = -1;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < places.size(); i++) {
            CityWalls.Place place = places.get(i);
            if (!place.dimension().equals(pos.dimension())) {
                continue;
            }
            long distance = distance(place, pos);
            if (distance < best) {
                best = distance;
                nearest = i;
            }
        }
        return nearest >= 0 ? nearest : index.getOrDefault(0L, -1);
    }

    private static long distance(CityWalls.Place place, WorldPos pos) {
        long dx = (long) place.x() - pos.x();
        long dz = (long) place.z() - pos.z();
        return dx * dx + dz * dz;
    }

    // ---------------------------------------------------------------- the step

    /**
     * Whether the cities of a kingdom grow by themselves: those of a kingdom of players, while what the cities keep can be saved.
     */
    public static boolean growsByCity(MinecraftServer server, UUID kingdomId) {
        return !io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, kingdomId) && !RoadWork.isReadOnly(server);
    }

    /**
     * The end of a step of the citizens of a kingdom of players: each city whose day came lives a day of its townsfolk, then
     * each city whose newcomer is due gets it.
     */
    public static void step(MinecraftServer server, UUID kingdomId, CitizenRules rules, long now) {
        if (KingdomWorldApi.isReadOnly(server) || RoadWork.isReadOnly(server)) {
            return;
        }
        List<Census> census = census(server, kingdomId);
        if (census.isEmpty()) {
            return;
        }
        FolkRules folkRules = FolkSettings.rules();
        RoadState roads = RoadWork.state(server);
        KingdomWorldState state = KingdomWorldApi.state(server);
        CitizenRegistry registry = state.citizens().registry();
        long kingdomArrival = registry.lastArrival(kingdomId);
        int named = registry.citizensOf(kingdomId).size();
        long folk = roads.folkOf(kingdomId);
        long nutrition = KingdomStorage.totalNutrition(server, kingdomId);
        long day = Math.floorDiv(now, CitizenNeeds.TICKS_PER_DAY);
        int hungry = 0;
        int hungryCities = 0;
        for (Census city : census) {
            CityCharter charter = roads.charterOf(kingdomId, city.cityId(), arrival(kingdomArrival));
            if (charter.lastDay() == CityCharter.NEVER || charter.lastDay() > day) {
                // The first day of a city, or a clock turned back: the count starts now.
                charter.setLastDay(day);
                roads.charterChanged();
                continue;
            }
            if (day <= charter.lastDay()) {
                continue;
            }
            charter.setLastDay(day);
            roads.charterChanged();
            if (charter.folk() == 0 && !CityFolk.quotaReached(city.named(), folkRules)) {
                continue;
            }
            int housed = CityFolk.housed(charter.folk(), city.freeBeds(), folkRules);
            long ration = CityFolk.ration(housed, rules.dailyNutrition());
            long eaten = ration <= 0 ? 0 : KingdomStorage.eat(server, kingdomId, (int) Math.min(Integer.MAX_VALUE, ration), city.hall());
            nutrition = Math.max(0, nutrition - eaten);
            CityFolk.Result result = CityFolk.day(new CityFolk.Day(charter.folk(), charter.folkCarry(), city.freeBeds(), rules.dailyNutrition(),
                    eaten, nutrition, named + folk - charter.folk(), rules.growthFoodDays(), city.named(), city.mood(),
                    state.economy().treasuryRoom(kingdomId)), folkRules);
            folk += result.folk() - charter.folk();
            charter.setFolk(result.folk());
            charter.setFolkCarry(result.carry());
            if (result.dues() > 0) {
                state.economy().folkDues(kingdomId, result.dues(), day);
            }
            if (result.leftHungry() > 0) {
                hungry += result.leftHungry();
                hungryCities++;
            }
        }
        if (hungry > 0) {
            int left = hungry;
            int cities = hungryCities;
            KingdomWorldApi.kingdom(server, kingdomId).ifPresent(kingdom -> KingdomActions.notifyMembers(server, kingdom, Permission.MANAGE_CITIZENS,
                    Component.translatable(KEY + "hungry", KingdomActions.kingdomName(kingdom), left, cities).withStyle(ChatFormatting.GOLD)));
        }
        for (Census city : census) {
            CityCharter charter = roads.charterOf(kingdomId, city.cityId(), arrival(kingdomArrival));
            CityFolk.Newcomer newcomer = CityFolk.newcomer(city.named(), charter.folk(), city.freeBeds(), folkRules);
            if (newcomer == CityFolk.Newcomer.NONE) {
                continue;
            }
            int houseBeds = newcomer == CityFolk.Newcomer.FOLK ? CityFolk.bedsLeft(city.freeHouseBeds(), charter.folk(), folkRules)
                    : city.freeHouseBeds();
            long since = charter.lastArrival() == CityCharter.NEVER ? Long.MAX_VALUE : now - charter.lastArrival();
            if (CityFolk.check(newcomer, named, named + folk, houseBeds, nutrition, since, city.mood(), rules) != CitizenNeeds.GrowthCheck.READY) {
                continue;
            }
            if (newcomer == CityFolk.Newcomer.FOLK) {
                charter.setFolk(charter.folk() + 1);
                folk++;
            } else {
                if (CitizenLife.arriveInto(server, kingdomId, city.hall(), city.bed(), true).isEmpty()) {
                    continue;
                }
                named++;
                if (newcomer == CityFolk.Newcomer.FROM_FOLK) {
                    charter.setFolk(charter.folk() - 1);
                    folk--;
                }
            }
            charter.setLastArrival(now);
            roads.charterChanged();
        }
    }

    private static long arrival(long kingdomArrival) {
        return kingdomArrival == CitizenRegistry.NEVER ? CityCharter.NEVER : kingdomArrival;
    }

    /**
     * Why newcomers do or do not come to a kingdom of players right now, for the throne screen: ready when one of its cities is,
     * else the reason of the city nearest to it (too soon, then too little food, then no free bed).
     */
    public static CitizenNeeds.GrowthCheck growthCheck(MinecraftServer server, UUID kingdomId, CitizenRules rules) {
        FolkRules folkRules = FolkSettings.rules();
        RoadState roads = RoadWork.state(server);
        KingdomWorldState state = KingdomWorldApi.state(server);
        CitizenRegistry registry = state.citizens().registry();
        long kingdomArrival = registry.lastArrival(kingdomId);
        int named = registry.citizensOf(kingdomId).size();
        long folk = roads.folkOf(kingdomId);
        long nutrition = KingdomStorage.totalNutrition(server, kingdomId);
        long now = KingdomWorldApi.gameTime(server);
        CitizenNeeds.GrowthCheck best = null;
        for (Census city : census(server, kingdomId)) {
            Optional<CityCharter> charter = roads.charter(kingdomId, city.cityId());
            int cityFolk = charter.map(CityCharter::folk).orElse(0);
            long last = charter.map(CityCharter::lastArrival).orElse(arrival(kingdomArrival));
            CityFolk.Newcomer newcomer = CityFolk.newcomer(city.named(), cityFolk, city.freeBeds(), folkRules);
            int houseBeds = newcomer == CityFolk.Newcomer.FOLK ? CityFolk.bedsLeft(city.freeHouseBeds(), cityFolk, folkRules) : city.freeHouseBeds();
            CitizenNeeds.GrowthCheck check = CityFolk.check(newcomer, named, named + folk, houseBeds, nutrition,
                    last == CityCharter.NEVER ? Long.MAX_VALUE : now - last, city.mood(), rules);
            if (check == CitizenNeeds.GrowthCheck.READY) {
                return check;
            }
            if (best == null || rank(check) > rank(best)) {
                best = check;
            }
        }
        return best == null ? CitizenNeeds.GrowthCheck.NO_FREE_BED : best;
    }

    private static int rank(CitizenNeeds.GrowthCheck check) {
        return switch (check) {
            case READY -> 4;
            case TOO_SOON -> 3;
            case NOT_ENOUGH_FOOD -> 2;
            case NO_FREE_BED -> 1;
            case FULL -> 0;
        };
    }

    /**
     * The townsfolk of every city of a kingdom, the capital under id 0; for the shares of fiefs and the views.
     */
    public static Map<Long, Integer> folkByCity(MinecraftServer server, UUID kingdomId) {
        Map<Long, Integer> result = new HashMap<>();
        for (CityCharter charter : RoadWork.state(server).charters()) {
            if (charter.kingdomId().equals(kingdomId) && charter.folk() > 0) {
                result.put(charter.cityId(), charter.folk());
            }
        }
        return result;
    }
}
