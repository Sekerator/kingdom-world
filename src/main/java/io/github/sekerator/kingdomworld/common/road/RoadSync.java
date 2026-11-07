package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends the cities, roads and the camps a player's kingdom knows of to the maps of the player, when they change.
 */
public final class RoadSync {
    /** Every how many blocks of road the maps get a point. */
    private static final int EVERY = 4;
    private static final Map<UUID, RoadViews.MapData> SENT = new HashMap<>();
    private static boolean dirty = true;
    private static List<RoadViews.MapCity> cities = List.of();
    private static List<RoadViews.MapRoad> roads = List.of();

    private RoadSync() {
    }

    static void reset() {
        SENT.clear();
        dirty = true;
    }

    /**
     * Something the maps show changed.
     */
    public static void changed() {
        dirty = true;
    }

    /**
     * A player logged in: it gets everything again.
     */
    public static void forget(UUID player) {
        SENT.remove(player);
    }

    static void tick(MinecraftServer server) {
        if (dirty) {
            dirty = false;
            rebuild(server);
        }
        RoadState state = RoadWork.state(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof FakePlayer) {
                continue;
            }
            RoadViews.MapData data = data(server, state, player.getUUID());
            if (!data.equals(SENT.get(player.getUUID()))) {
                SENT.put(player.getUUID(), data);
                KingdomWorldNetwork.sendToPlayer(player, new RoadPackets.MapSync(data));
            }
        }
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static void rebuild(MinecraftServer server) {
        RoadState state = RoadWork.state(server);
        List<RoadViews.MapCity> cityList = new ArrayList<>();
        for (City city : state.cities()) {
            cityList.add(new RoadViews.MapCity(city.id(), city.kingdomId(), city.name(), city.dimension(), city.x(), city.z(), color(server, city.kingdomId())));
        }
        List<RoadViews.MapRoad> roadList = new ArrayList<>();
        for (Road road : state.roads()) {
            roadList.add(mapRoad(server, road));
        }
        cities = List.copyOf(cityList);
        roads = List.copyOf(roadList);
    }

    static int color(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.kingdom(server, kingdomId).map(Kingdom::color).orElse(0x808080);
    }

    /**
     * A road as the maps draw it: every few blocks of its middle.
     */
    public static RoadViews.MapRoad mapRoad(MinecraftServer server, Road road) {
        List<RouteCell> cells = road.cells();
        int step = Math.max(EVERY, (cells.size() + RoadViews.MAX_POINTS - 2) / (RoadViews.MAX_POINTS - 1));
        List<Integer> xs = new ArrayList<>();
        List<Integer> zs = new ArrayList<>();
        for (int i = 0; i < cells.size(); i += step) {
            xs.add(cells.get(i).x());
            zs.add(cells.get(i).z());
        }
        xs.add(cells.get(cells.size() - 1).x());
        zs.add(cells.get(cells.size() - 1).z());
        int built = road.units() == 0 ? 1000 : (int) Math.round(1000.0 * road.paid() / road.units());
        return new RoadViews.MapRoad(road.id(), road.kingdomId(), road.dimension(), road.roadClass(), color(server, road.kingdomId()), built,
                xs.stream().mapToInt(Integer::intValue).toArray(), zs.stream().mapToInt(Integer::intValue).toArray());
    }

    private static RoadViews.MapData data(MinecraftServer server, RoadState state, UUID player) {
        Optional<UUID> kingdom = KingdomWorldApi.state(server).membership().kingdomOf(player);
        List<RoadViews.MapCamp> camps = new ArrayList<>();
        if (kingdom.isPresent()) {
            for (BanditCamp camp : state.camps()) {
                if (camp.knownTo(kingdom.get()) && !camp.cleared()) {
                    camps.add(new RoadViews.MapCamp(camp.id(), camp.dimension(), camp.x(), camp.z(), camp.size()));
                }
            }
        }
        return new RoadViews.MapData(cities, roads, camps);
    }

    /**
     * Everything the maps of the server draw (for the web maps), with the camps any kingdom knows of.
     */
    public static RoadViews.MapData all(MinecraftServer server) {
        if (dirty) {
            dirty = false;
            rebuild(server);
        }
        List<RoadViews.MapCamp> camps = new ArrayList<>();
        for (BanditCamp camp : RoadWork.state(server).camps()) {
            if (!camp.known().isEmpty() && !camp.cleared()) {
                camps.add(new RoadViews.MapCamp(camp.id(), camp.dimension(), camp.x(), camp.z(), camp.size()));
            }
        }
        return new RoadViews.MapData(cities, roads, camps);
    }
}
