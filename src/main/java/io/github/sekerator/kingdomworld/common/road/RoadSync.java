package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
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
            RoadViews.MapData data = data(server, state, player.getUUID(), player.getLanguage());
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
            // The capital of a kingdom of the world tells how large it is, so that the list of kingdoms shows it before its land is seen.
            io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo npc = place.cityId() == 0
                    ? io.github.sekerator.kingdomworld.common.kingdom.KingdomViews.npcInfo(server, place.kingdomId()) : null;
            cityList.add(new RoadViews.MapCity(place.cityId(), place.kingdomId(), place.name(), place.dimension(), place.x(), place.z(),
                    color(server, place.kingdomId()), CityLevel.of(counts[0], counts[1], RoadSettings.rules()),
                    npc == null ? null : RoadViews.MapKingdom.of(npc)));
        }
        List<RoadViews.MapRoad> roadList = new ArrayList<>();
        for (Road road : state.roads()) {
            roadList.add(mapRoad(server, road));
        }
        List<RoadViews.MapWall> wallList = new ArrayList<>();
        for (Wall wall : state.walls()) {
            wallList.add(mapWall(wall));
        }
        planned(server, state, cityList, roadList);
        cities = List.copyOf(cityList);
        roads = List.copyOf(roadList);
        walls = List.copyOf(wallList);
    }

    /** Blocks from the place of a planned town within which a city of its kingdom counts as that town. */
    private static final int TOWN_MATCH = 48;

    /**
     * The towns and roads of the kingdoms of the world whose buildings are not raised yet: a town as a village where it will
     * stand and a road as a straight dashed line from the capital, until the real ones are there. Their ids are negative, so
     * that they never meet the ids of real cities and roads.
     */
    private static void planned(MinecraftServer server, RoadState state, List<RoadViews.MapCity> cityList, List<RoadViews.MapRoad> roadList) {
        io.github.sekerator.kingdomworld.core.npc.NpcState npc = io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server);
        String dimension = io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(server.overworld());
        for (io.github.sekerator.kingdomworld.core.npc.NpcSite site : npc.sites()) {
            boolean waiting = site.status() == io.github.sekerator.kingdomworld.core.npc.NpcSite.Status.FOUNDED
                    || site.status() == io.github.sekerator.kingdomworld.core.npc.NpcSite.Status.SPAWNING;
            if (!waiting || site.kingdomId().isEmpty()) {
                continue;
            }
            UUID id = site.kingdomId().get();
            Optional<io.github.sekerator.kingdomworld.core.npc.NpcKingdom> record = npc.kingdom(id);
            if (record.isEmpty() || KingdomWorldApi.kingdom(server, id).isEmpty()) {
                continue;
            }
            int color = color(server, id);
            RoadClass roadClass = switch (site.level()) {
                case SMALL -> RoadClass.PATH;
                case MEDIUM -> RoadClass.GRAVEL;
                case LARGE -> RoadClass.PAVED;
            };
            List<io.github.sekerator.kingdomworld.core.npc.NpcSite.Town> towns = site.towns();
            for (int i = 0; i < towns.size(); i++) {
                io.github.sekerator.kingdomworld.core.npc.NpcSite.Town town = towns.get(i);
                long plannedId = -((long) site.id() * 16 + i + 1);
                boolean standing = false;
                for (City city : state.citiesOf(id)) {
                    standing |= Math.abs(city.x() - town.x()) <= TOWN_MATCH && Math.abs(city.z() - town.z()) <= TOWN_MATCH;
                }
                if (!standing) {
                    String name = i < record.get().towns().size() ? record.get().towns().get(i).latin() : record.get().name().latin();
                    cityList.add(new RoadViews.MapCity(plannedId, id, name, dimension, town.x(), town.z(), color, CityLevel.VILLAGE));
                }
                boolean road = false;
                for (Road built : state.roads()) {
                    if (built.kingdomId().equals(id) && !built.cells().isEmpty()) {
                        RouteCell end = built.cells().get(built.cells().size() - 1);
                        road |= Math.abs(end.x() - town.x()) <= TOWN_MATCH && Math.abs(end.z() - town.z()) <= TOWN_MATCH;
                    }
                }
                if (!road) {
                    roadList.add(new RoadViews.MapRoad(plannedId, id, dimension, roadClass, color, 0, new int[]{site.x(), town.x()},
                            new int[]{site.z(), town.z()}));
                }
            }
        }
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

    private static RoadViews.MapData data(MinecraftServer server, RoadState state, UUID player, String language) {
        Optional<UUID> kingdom = KingdomWorldApi.state(server).membership().kingdomOf(player);
        List<RoadViews.MapCamp> camps = new ArrayList<>();
        // Camps an innkeeper told the player of and the player paid to have marked count as known too.
        java.util.Set<Long> marked = io.github.sekerator.kingdomworld.common.life.LifeWork.state(server).marks(player);
        if (kingdom.isPresent() || !marked.isEmpty()) {
            for (BanditCamp camp : state.camps()) {
                if ((kingdom.isPresent() && camp.knownTo(kingdom.get()) || marked.contains(camp.id())) && !camp.cleared()) {
                    camps.add(new RoadViews.MapCamp(camp.id(), camp.dimension(), camp.x(), camp.z(), camp.size()));
                }
            }
        }
        // Towns of kingdoms of the world are named in the alphabet of the player.
        List<RoadViews.MapCity> named = new ArrayList<>(cities.size());
        for (RoadViews.MapCity city : cities) {
            String name = city.id() == 0 ? io.github.sekerator.kingdomworld.common.npc.NpcWork.localKingdom(city.kingdomId(), city.name(), language)
                    : io.github.sekerator.kingdomworld.common.npc.NpcWork.localCity(city.name(), language);
            named.add(name.equals(city.name()) ? city : new RoadViews.MapCity(city.id(), city.kingdomId(), name, city.dimension(), city.x(),
                    city.z(), city.color(), city.level(), city.kingdom()));
        }
        return new RoadViews.MapData(named, roads, camps, walls);
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
