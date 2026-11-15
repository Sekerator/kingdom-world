package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.road.RoadPlanning;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.SpawnStage;
import io.github.sekerator.kingdomworld.core.road.Road;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The roads from the capital of a kingdom of the world to its towns that were not laid at its founding (no way was found then,
 * or the search could not start): the founding goes on without them, the members of the kingdom who see to its roads are told,
 * and the way is looked for again a day later, until the road stands. What a road still lacks is seen from the roads of the
 * kingdom themselves, so nothing of it is saved: after a restart the roads left out are looked for again a minute later.
 */
public final class TownRoads {
    /** Ticks between two looks over the kingdoms of the world. */
    private static final int LOOK_TICKS = 200;
    /** Ticks after a restart before a road left out is looked for again. */
    private static final long FIRST_TRY_TICKS = 1_200L;
    /** Ticks between two tries of the road to the same town. */
    public static final long RETRY_TICKS = CitizenNeeds.TICKS_PER_DAY;
    private static final Logger LOGGER = LogUtils.getLogger();

    /** When each kingdom of the world, by its place, looks again for the roads to its towns that have none. */
    private static final Map<Integer, Long> NEXT = new HashMap<>();
    /** The town, by its index, whose road a kingdom of the world, by its place, is searching for again. */
    private static final Map<Integer, Integer> SEARCHING = new HashMap<>();

    private TownRoads() {
    }

    static void reset() {
        NEXT.clear();
        SEARCHING.clear();
    }

    /**
     * The road to a town was not laid: the members who see to the roads are told, and it is tried again a day later.
     */
    static void missed(MinecraftServer server, NpcSite site, int index, String why) {
        NEXT.put(site.id(), KingdomWorldApi.gameTime(server) + RETRY_TICKS);
        LOGGER.warn("The road from {} to its town {} is not laid: {}; it is looked for again in a day", site, index + 1, why);
        Optional<UUID> id = site.kingdomId();
        if (id.isPresent() && index < site.towns().size()) {
            NpcSite.Town town = site.towns().get(index);
            RoadWork.notify(server, id.get(), Component.translatable("kingdomworld.road.town_road_missed", town.x(), town.z()));
        }
    }

    /**
     * When the next look for the roads left out of a kingdom of the world comes, for tests; -1 if none is due.
     */
    public static long next(int siteId) {
        return NEXT.getOrDefault(siteId, -1L);
    }

    /**
     * Brings the next look for the roads left out of a kingdom of the world forward to now, for tests.
     */
    public static void lookNow(MinecraftServer server, int siteId) {
        NEXT.put(siteId, KingdomWorldApi.gameTime(server));
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % LOOK_TICKS != 0 || RoadWork.isReadOnly(server)) {
            return;
        }
        look(server);
    }

    /**
     * Pays for the roads found again and starts the search for the next road left out whose time has come, one at a time.
     */
    public static void look(MinecraftServer server) {
        long now = KingdomWorldApi.gameTime(server);
        ServerLevel level = server.overworld();
        for (Iterator<Map.Entry<Integer, Integer>> it = SEARCHING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Integer> entry = it.next();
            NpcSite site = NpcWork.state(server).site(entry.getKey()).orElse(null);
            if (site == null || site.kingdomId().isEmpty() || entry.getValue() >= site.towns().size()) {
                it.remove();
                continue;
            }
            UUID id = site.kingdomId().get();
            if (RoadPlanning.searching(id)) {
                continue;
            }
            it.remove();
            Optional<Road> road = NpcSpawner.roadTo(server, id, site.towns().get(entry.getValue()));
            if (road.isPresent()) {
                // Paid as part of the founding, as the roads laid then.
                NpcSpawner.payRoad(server, level, road.get());
                NEXT.remove(site.id());
                LOGGER.info("The road from {} to its town {} is laid after all", site, entry.getValue() + 1);
            } else {
                missed(server, site, entry.getValue(), "no way was found again");
            }
        }
        boolean started = false;
        for (NpcSite site : NpcWork.state(server).sites()) {
            if (site.status() != NpcSite.Status.SPAWNED || site.stage().ordinal() <= SpawnStage.ROADS.ordinal() || site.kingdomId().isEmpty()
                    || SEARCHING.containsKey(site.id())) {
                continue;
            }
            UUID id = site.kingdomId().get();
            if (KingdomWorldApi.kingdom(server, id).isEmpty()) {
                continue;
            }
            int missing = -1;
            for (int i = 0; i < site.towns().size(); i++) {
                if (NpcSpawner.roadTo(server, id, site.towns().get(i)).isEmpty()) {
                    missing = i;
                    break;
                }
            }
            if (missing < 0) {
                NEXT.remove(site.id());
                continue;
            }
            Long next = NEXT.get(site.id());
            if (next == null) {
                NEXT.put(site.id(), now + FIRST_TRY_TICKS);
                continue;
            }
            if (started || now < next || RoadPlanning.searching(id)) {
                continue;
            }
            Outcome outcome = NpcSpawner.startRoad(level, site, NpcSpawner.composition(site), id, site.towns().get(missing));
            if (outcome.ok()) {
                SEARCHING.put(site.id(), missing);
                NEXT.put(site.id(), now + RETRY_TICKS);
                started = true;
            } else {
                missed(server, site, missing, "it was not started: " + outcome.error());
            }
        }
    }
}
