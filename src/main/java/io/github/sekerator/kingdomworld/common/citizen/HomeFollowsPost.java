package io.github.sekerator.kingdomworld.common.citizen;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.Citizens;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.City;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A citizen of a kingdom of players whose post stands in another city than its home (hired there, put on the post from afar or
 * at a post set up from the capital) moves to the city of its post: once a game day it leaves its bed for the free bed of that
 * city nearest the post. While there is none it keeps its home, the post tells so, and its body is kept by its post alone, not
 * by a home in another city. A post outside every city changes nothing.
 */
public final class HomeFollowsPost {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.screen.post.";
    /** The game day each citizen last looked for a bed in the city of its post, so that it looks once a day. */
    private static final Map<UUID, Long> TRIED = new HashMap<>();

    private HomeFollowsPost() {
    }

    /**
     * The city (0 for the capital) of the kingdom whose square holds a place, the nearer one where squares meet.
     */
    static Optional<Long> cityOf(MinecraftServer server, UUID kingdomId, WorldPos pos) {
        return CityAreas.nearestAt(server, kingdomId, pos.dimension(), pos.x(), pos.z()).map(CityAreas.Area::cityId);
    }

    /**
     * Whether a citizen works at a post in a city and lives outside that city: in another city or outside every city.
     */
    public static boolean apart(MinecraftServer server, Citizen citizen) {
        WorldPos home = citizen.home();
        WorldPos post = citizen.workplace();
        if (home == null || post == null || io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, citizen.kingdomId())) {
            return false;
        }
        Optional<Long> postCity = cityOf(server, citizen.kingdomId(), post);
        if (postCity.isEmpty()) {
            return false;
        }
        Optional<Long> homeCity = cityOf(server, citizen.kingdomId(), home);
        return homeCity.isEmpty() || !homeCity.get().equals(postCity.get());
    }

    /**
     * Moves a citizen into the free bed of the city of its post nearest the post, once a game day while it lives elsewhere.
     *
     * @return whether it moved
     */
    static boolean follow(MinecraftServer server, Citizens citizens, Citizen citizen) {
        if (!apart(server, citizen)) {
            TRIED.remove(citizen.id());
            return false;
        }
        long day = Math.floorDiv(KingdomWorldApi.gameTime(server), CitizenNeeds.TICKS_PER_DAY);
        Long tried = TRIED.get(citizen.id());
        if (tried != null && tried == day) {
            return false;
        }
        TRIED.put(citizen.id(), day);
        WorldPos post = citizen.workplace();
        long city = cityOf(server, citizen.kingdomId(), post).orElseThrow();
        Optional<io.github.sekerator.kingdomworld.core.citizen.Site> bed = BuildingEffects.freeBed(server, citizen.kingdomId(), post, false,
                pos -> cityOf(server, citizen.kingdomId(), pos).filter(found -> found == city).isPresent());
        if (bed.isEmpty()) {
            return false;
        }
        WorldPos before = citizen.home();
        if (!citizens.assignHome(citizen, bed.get().pos())) {
            return false;
        }
        TRIED.remove(citizen.id());
        LOGGER.info("Citizen {} of {} moved from {} to {} near its post", citizen.id(), citizen.kingdomId(), before, bed.get().pos());
        return true;
    }

    /**
     * The name of a city of a kingdom: the kingdom for its capital.
     */
    static String cityName(MinecraftServer server, UUID kingdomId, long cityId) {
        if (cityId == 0) {
            return KingdomWorldApi.kingdom(server, kingdomId).map(Kingdom::name).orElse("");
        }
        return RoadWork.state(server).city(cityId).map(City::name).orElse("");
    }

    /**
     * What a post says of its workers who live in another city: there is no bed in the city of the post, they live in theirs;
     * empty when all live in its city or the post stands outside every city.
     */
    public static Component note(MinecraftServer server, WorkPost post) {
        Citizens citizens = KingdomWorldApi.state(server).citizens();
        int apart = 0;
        Citizen first = null;
        for (UUID worker : post.workers()) {
            Optional<Citizen> citizen = citizens.get(worker);
            if (citizen.isPresent() && apart(server, citizen.get())) {
                apart++;
                if (first == null) {
                    first = citizen.get();
                }
            }
        }
        if (first == null) {
            return Component.empty();
        }
        UUID kingdom = post.kingdomId();
        String here = cityOf(server, kingdom, post.pos()).map(id -> cityName(server, kingdom, id)).orElse("");
        Component there = cityOf(server, kingdom, first.home()).map(id -> (Component) Component.literal(cityName(server, kingdom, id)))
                .orElse(Component.translatable(KEY + "no_bed.outside"));
        return apart == 1 ? Component.translatable(KEY + "no_bed", here, there) : Component.translatable(KEY + "no_bed_many", here, there, apart);
    }

    /**
     * Forgets the days looked at, when the server stops.
     */
    public static void reset() {
        TRIED.clear();
    }
}
