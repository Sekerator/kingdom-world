package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
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
    private static List<RoadViews.MapWall> walls = List.of();
    private static int seconds;

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
        // The levels of cities follow their people and buildings: they are looked at again every minute.
        if (++seconds % 60 == 0) {
            dirty = true;
        }
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
        for (CityWalls.Place place : CityWalls.places(server)) {
            int[] counts = CityWalls.counts(server, place);
            cityList.add(new RoadViews.MapCity(place.cityId(), place.kingdomId(), place.name(), place.dimension(), place.x(), place.z(),
                    color(server, place.kingdomId()), CityLevel.of(counts[0], counts[1], RoadSettings.rules())));
        }
        List<RoadViews.MapRoad> roadList = new ArrayList<>();
        for (Road road : state.roads()) {
            roadList.add(mapRoad(server, road));
        }
        List<RoadViews.MapWall> wallList = new ArrayList<>();
        for (Wall wall : state.walls()) {
            wallList.add(mapWall(wall));
        }
        cities = List.copyOf(cityList);
        roads = List.copyOf(roadList);
        walls = List.copyOf(wallList);
    }

    /**
     * A wall as the maps draw it.
     */
    public static RoadViews.MapWall mapWall(Wall wall) {
        int units = 0;
        int placed = 0;
        for (Road.Section section : wall.sections()) {
            units += section.units();
            placed += section.placed();
        }
        List<RouteCell> gates = wall.gateCells();
        int count = Math.min(64, gates.size());
        int[] xs = new int[count];
        int[] zs = new int[count];
        for (int i = 0; i < count; i++) {
            xs[i] = gates.get(i).x();
            zs[i] = gates.get(i).z();
        }
        return new RoadViews.MapWall(wall.id(), wall.dimension(), wall.centerX(), wall.centerZ(), wall.radius(), wall.wallClass(),
                units == 0 ? 1000 : (int) Math.round(1000.0 * placed / units), xs, zs);
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
        return new RoadViews.MapData(cities, roads, camps, walls);
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
        return new RoadViews.MapData(cities, roads, camps, walls);
    }
}
