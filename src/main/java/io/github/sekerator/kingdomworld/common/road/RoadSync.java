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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends the cities, roads and the camps a player's kingdom knows of to the maps of the player, when they change: the cities and
 * roads as a part a kingdom, each with its version, so that a player gets again only the parts that changed.
 */
public final class RoadSync {
    /** Every how many blocks of road the maps get a point. */
    /** How far a block of a road may stray from the line the maps draw, in blocks. */
    private static final double THIN_BLOCKS = 3.0;
    /** About how many bytes of the parts of kingdoms a packet carries at most; a part larger than this goes alone. */
    static final int PACKET_BYTES = 192 * 1024;
    /** The first id of the settlements of the plan on the maps, negated; the roads of the plan start at twice it. */
    static final long SETTLEMENT_IDS = 1L << 40;
    private static final Map<UUID, Sent> SENT = new HashMap<>();
    private static boolean dirty = true;
    private static Map<UUID, RoadViews.MapPart> parts = new LinkedHashMap<>();
    private static List<RoadViews.MapCity> cities = List.of();
    private static List<RoadViews.MapRoad> roads = List.of();
    private static List<RoadViews.MapWall> walls = List.of();
    /** Grows whenever a part of a kingdom or the walls change. */
    private static long version;
    private static long partVersions;
    private static long settlementsSeen = -1;
    /** The names of the settlements of the plan on the maps in both alphabets, by their ids on the maps. */
    private static Map<Long, io.github.sekerator.kingdomworld.core.npc.NpcNames.Name> settlementNames = Map.of();
    /** The parts as a language and a view of them shows them, by kingdom, language and view. */
    private static final Map<String, Local> LOCAL = new HashMap<>();
    private static int seconds;

    private RoadSync() {
    }

    static void reset() {
        SENT.clear();
        LOCAL.clear();
        parts = new LinkedHashMap<>();
        settlementsSeen = -1;
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

    /**
     * The id on the maps of a settlement of the plan.
     */
    public static long settlementId(int id) {
        return -(SETTLEMENT_IDS | id);
    }

    /**
     * The id on the maps of a road of the plan.
     */
    public static long plannedRoadId(int id) {
        return -((2 * SETTLEMENT_IDS) | id);
    }

    /**
     * The name of a city of the maps in a language: a settlement of the plan or a town of a kingdom of the world in its Cyrillic
     * form for Russian, any other as it is.
     */
    public static String localName(RoadViews.MapCity city, String language) {
        io.github.sekerator.kingdomworld.core.npc.NpcNames.Name both = settlementNames.get(city.id());
        if (both == null) {
            return io.github.sekerator.kingdomworld.common.npc.NpcWork.localCity(city.name(), language);
        }
        return io.github.sekerator.kingdomworld.core.citizen.CitizenNames.alphabetFor(language)
                == io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.CYRILLIC ? both.cyrillic() : both.latin();
    }

    /**
     * Whether an id on the maps is that of a settlement or a road of the plan.
     */
    static boolean ofPlan(long id) {
        return id < 0 && -id >= SETTLEMENT_IDS;
    }

    /**
     * What a player has: the version of the maps, its language, the names of the kingdoms of the world, the camps and walls it
     * was sent, and for every kingdom the version of its part with whether its settlements are hidden from the player.
     */
    private record Sent(long version, String language, long names, List<RoadViews.MapCamp> camps, List<RoadViews.MapWall> walls,
                        Map<UUID, Long> parts, java.util.Set<UUID> hidden) {
    }

    private record Local(long version, long names, RoadViews.MapPart part) {
    }

    /**
     * The camps a player is shown: those its kingdom knows of and those it paid to have marked.
     */
    private static List<RoadViews.MapCamp> knownCamps(MinecraftServer server, RoadState state, UUID player) {
        Optional<UUID> kingdom = KingdomWorldApi.state(server).membership().kingdomOf(player);
        List<RoadViews.MapCamp> camps = new ArrayList<>();
        java.util.Set<Long> marked = io.github.sekerator.kingdomworld.common.life.LifeWork.state(server).marks(player);
        if (kingdom.isPresent() || !marked.isEmpty()) {
            for (BanditCamp camp : state.camps()) {
                if ((kingdom.isPresent() && camp.knownTo(kingdom.get()) || marked.contains(camp.id())) && !camp.cleared()) {
                    camps.add(new RoadViews.MapCamp(camp.id(), camp.dimension(), camp.x(), camp.z(), camp.size()));
                }
            }
        }
        return camps;
    }

    static void tick(MinecraftServer server) {
        // The levels of cities follow their people and buildings: they are looked at again every minute.
        if (++seconds % 60 == 0) {
            dirty = true;
        }
        long settlements = io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server).settlementsVersion();
        if (settlements != settlementsSeen) {
            settlementsSeen = settlements;
            dirty = true;
        }
        if (dirty) {
            dirty = false;
            rebuild(server);
        }
        RoadState state = RoadWork.state(server);
        // Read before any part is named, so that names changing meanwhile are looked at again the next time.
        long names = io.github.sekerator.kingdomworld.common.npc.NpcWork.namesVersion();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof FakePlayer) {
                continue;
            }
            send(player, knownCamps(server, state, player.getUUID()), names);
        }
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    /**
     * Sends a player what changed of its maps since it was last sent anything, in packets of about {@link #PACKET_BYTES}.
     */
    private static void send(ServerPlayer player, List<RoadViews.MapCamp> camps, long names) {
        UUID id = player.getUUID();
        String language = player.getLanguage();
        java.util.Set<UUID> hidden = new java.util.HashSet<>();
        for (UUID kingdom : parts.keySet()) {
            if (!io.github.sekerator.kingdomworld.common.kingdom.TerritorySnapshots.canSee(id, kingdom)) {
                hidden.add(kingdom);
            }
        }
        Sent last = SENT.get(id);
        if (last != null && last.version() == version && last.language().equals(language) && last.names() == names && last.camps().equals(camps)
                && last.hidden().equals(hidden)) {
            return;
        }
        boolean reset = last == null || !last.language().equals(language) || last.names() != names;
        Map<UUID, Long> had = reset ? Map.of() : last.parts();
        Map<UUID, Long> now = new HashMap<>();
        List<RoadViews.MapPart> changed = new ArrayList<>();
        for (RoadViews.MapPart part : parts.values()) {
            boolean hide = hidden.contains(part.kingdomId());
            long key = part.version() * 2 + (hide ? 1 : 0);
            now.put(part.kingdomId(), key);
            if (!java.util.Objects.equals(had.get(part.kingdomId()), key)) {
                changed.add(local(part, language, hide, names));
            }
        }
        List<UUID> dropped = new ArrayList<>();
        for (UUID kingdom : had.keySet()) {
            if (!now.containsKey(kingdom)) {
                dropped.add(kingdom);
            }
        }
        SENT.put(id, new Sent(version, language, names, List.copyOf(camps), walls, now, hidden));
        if (!reset && changed.isEmpty() && dropped.isEmpty() && last.camps().equals(camps) && last.walls().equals(walls)) {
            return;
        }
        List<RoadViews.MapPart> batch = new ArrayList<>();
        int weight = 0;
        boolean first = true;
        for (RoadViews.MapPart part : changed) {
            int partWeight = part.weight();
            if (!batch.isEmpty() && weight + partWeight > PACKET_BYTES) {
                KingdomWorldNetwork.sendToPlayer(player, new RoadPackets.MapSync(new RoadViews.MapChange(first && reset, batch, first ? dropped : List.of(),
                        camps, walls)));
                first = false;
                batch = new ArrayList<>();
                weight = 0;
            }
            batch.add(part);
            weight += partWeight;
        }
        KingdomWorldNetwork.sendToPlayer(player, new RoadPackets.MapSync(new RoadViews.MapChange(first && reset, batch, first ? dropped : List.of(),
                camps, walls)));
    }

    /**
     * A part as a player sees it: the towns of kingdoms of the world named in the alphabet of the player, and without the
     * settlements and roads of the plan of a kingdom the map visibility hides from the player.
     */
    private static RoadViews.MapPart local(RoadViews.MapPart part, String language, boolean hide, long names) {
        String key = part.kingdomId() + "|" + language + "|" + hide;
        Local cached = LOCAL.get(key);
        if (cached != null && cached.version() == part.version() && cached.names() == names) {
            return cached.part();
        }
        boolean cyrillic = io.github.sekerator.kingdomworld.core.citizen.CitizenNames.alphabetFor(language)
                == io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.CYRILLIC;
        List<RoadViews.MapCity> named = new ArrayList<>(part.cities().size());
        for (RoadViews.MapCity city : part.cities()) {
            if (hide && ofPlan(city.id())) {
                continue;
            }
            io.github.sekerator.kingdomworld.core.npc.NpcNames.Name both = settlementNames.get(city.id());
            String name = both != null ? (cyrillic ? both.cyrillic() : both.latin())
                    : city.id() == 0 ? io.github.sekerator.kingdomworld.common.npc.NpcWork.localKingdom(city.kingdomId(), city.name(), language)
                    : io.github.sekerator.kingdomworld.common.npc.NpcWork.localCity(city.name(), language);
            named.add(name.equals(city.name()) ? city : city.named(name));
        }
        List<RoadViews.MapRoad> shown = part.roads();
        if (hide) {
            shown = new ArrayList<>();
            for (RoadViews.MapRoad road : part.roads()) {
                if (!ofPlan(road.id())) {
                    shown.add(road);
                }
            }
        }
        RoadViews.MapPart local = new RoadViews.MapPart(part.kingdomId(), part.version(), named, shown);
        LOCAL.put(key, new Local(part.version(), names, local));
        return local;
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
        settlements(server, cityList, roadList);
        // The cities and roads by kingdom, in the order they come; a part that came out the same keeps its version.
        Map<UUID, List<RoadViews.MapCity>> citiesBy = new LinkedHashMap<>();
        Map<UUID, List<RoadViews.MapRoad>> roadsBy = new LinkedHashMap<>();
        java.util.Set<UUID> kingdoms = new java.util.LinkedHashSet<>();
        for (RoadViews.MapCity city : cityList) {
            citiesBy.computeIfAbsent(city.kingdomId(), k -> new ArrayList<>()).add(city);
            kingdoms.add(city.kingdomId());
        }
        for (RoadViews.MapRoad road : roadList) {
            roadsBy.computeIfAbsent(road.kingdomId(), k -> new ArrayList<>()).add(road);
            kingdoms.add(road.kingdomId());
        }
        Map<UUID, RoadViews.MapPart> next = new LinkedHashMap<>();
        boolean changed = !kingdoms.equals(parts.keySet());
        for (UUID kingdom : kingdoms) {
            List<RoadViews.MapCity> own = citiesBy.getOrDefault(kingdom, List.of());
            List<RoadViews.MapRoad> ownRoads = roadsBy.getOrDefault(kingdom, List.of());
            RoadViews.MapPart old = parts.get(kingdom);
            if (old != null && old.cities().equals(own) && old.roads().equals(ownRoads)) {
                next.put(kingdom, old);
            } else {
                next.put(kingdom, new RoadViews.MapPart(kingdom, ++partVersions, own, ownRoads));
                changed = true;
            }
        }
        if (!wallList.equals(walls)) {
            changed = true;
        }
        parts = next;
        walls = List.copyOf(wallList);
        if (changed) {
            version++;
            LOCAL.values().removeIf(local -> {
                RoadViews.MapPart part = parts.get(local.part().kingdomId());
                return part == null || part.version() != local.version();
            });
        }
        cities = List.copyOf(cityList);
        roads = List.copyOf(roadList);
    }

    /**
     * The settlements of the states of the world and the roads planned between them that are not raised or laid yet: a
     * settlement by its rank and numbers, dashed while it lives as data, and a road of the plan as a dashed line along its way.
     */
    private static void settlements(MinecraftServer server, List<RoadViews.MapCity> cityList, List<RoadViews.MapRoad> roadList) {
        io.github.sekerator.kingdomworld.core.npc.NpcState npc = io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server);
        Map<Long, io.github.sekerator.kingdomworld.core.npc.NpcNames.Name> names = new HashMap<>();
        if (npc.settlements().isEmpty()) {
            settlementNames = Map.of();
            return;
        }
        String dimension = io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(server.overworld());
        Map<UUID, Integer> colors = new HashMap<>();
        for (io.github.sekerator.kingdomworld.core.npc.Settlement settlement : npc.settlements()) {
            if (!shown(server, settlement)) {
                continue;
            }
            int color = colors.computeIfAbsent(settlement.kingdomId(), kingdom -> color(server, kingdom));
            CityLevel level = switch (settlement.tier()) {
                case CAPITAL, CITY -> CityLevel.CITY;
                case TOWN -> CityLevel.TOWN;
                case VILLAGE, HAMLET -> CityLevel.VILLAGE;
            };
            boolean data = settlement.status() != io.github.sekerator.kingdomworld.core.npc.Settlement.Status.STANDING;
            int most = io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo.MAX_COUNT;
            long id = settlementId(settlement.id());
            cityList.add(new RoadViews.MapCity(id, settlement.kingdomId(), settlement.name().latin(), dimension, settlement.x(), settlement.z(), color,
                    level, null, new RoadViews.MapSettlement(settlement.tier(), Math.min(most, settlement.population()), Math.min(most, settlement.guards()),
                    Math.min(most, settlement.militia()), Math.max(0, settlement.province()), data)));
            names.put(id, settlement.name());
        }
        settlementNames = Map.copyOf(names);
        for (io.github.sekerator.kingdomworld.core.npc.PlannedRoad road : npc.plannedRoads()) {
            if (road.roadId() != 0) {
                continue;
            }
            io.github.sekerator.kingdomworld.core.npc.Settlement from = npc.settlement(road.from()).orElse(null);
            io.github.sekerator.kingdomworld.core.npc.Settlement to = npc.settlement(road.to()).orElse(null);
            if (from == null || to == null || !shown(server, from) || !shown(server, to)) {
                continue;
            }
            int[] xs = road.xs();
            int[] zs = road.zs();
            if (xs.length > RoadViews.MAX_POINTS) {
                int step = (xs.length + RoadViews.MAX_POINTS - 2) / (RoadViews.MAX_POINTS - 1);
                List<Integer> keptX = new ArrayList<>();
                List<Integer> keptZ = new ArrayList<>();
                for (int i = 0; i < xs.length; i += step) {
                    keptX.add(xs[i]);
                    keptZ.add(zs[i]);
                }
                keptX.add(xs[xs.length - 1]);
                keptZ.add(zs[zs.length - 1]);
                xs = keptX.stream().mapToInt(Integer::intValue).toArray();
                zs = keptZ.stream().mapToInt(Integer::intValue).toArray();
            }
            roadList.add(new RoadViews.MapRoad(plannedRoadId(road.id()), from.kingdomId(), dimension, road.roadClass(),
                    colors.computeIfAbsent(from.kingdomId(), kingdom -> color(server, kingdom)), 0, xs, zs));
        }
    }

    /**
     * Whether the maps show a settlement of the plan: one of a kingdom that is there, not a ruin and not raised as a city of its own.
     */
    private static boolean shown(MinecraftServer server, io.github.sekerator.kingdomworld.core.npc.Settlement settlement) {
        return settlement.status() != io.github.sekerator.kingdomworld.core.npc.Settlement.Status.RUINED && settlement.cityId() == 0
                && KingdomWorldApi.kingdom(server, settlement.kingdomId()).isPresent();
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
        int[] allX = new int[cells.size()];
        int[] allZ = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            allX[i] = cells.get(i).x();
            allZ[i] = cells.get(i).z();
        }
        // Only the bends: no block of the road strays further than a few blocks from the line drawn.
        int[] kept = io.github.sekerator.kingdomworld.core.road.Polylines.keep(allX, allZ, THIN_BLOCKS);
        int step = Math.max(1, (kept.length + RoadViews.MAX_POINTS - 2) / (RoadViews.MAX_POINTS - 1));
        List<Integer> xs = new ArrayList<>();
        List<Integer> zs = new ArrayList<>();
        for (int k = 0; k < kept.length; k += step) {
            xs.add(allX[kept[k]]);
            zs.add(allZ[kept[k]]);
        }
        if (kept.length > 0 && (kept.length - 1) % step != 0) {
            xs.add(allX[kept[kept.length - 1]]);
            zs.add(allZ[kept[kept.length - 1]]);
        }
        int built = road.units() == 0 ? 1000 : (int) Math.round(1000.0 * road.paid() / road.units());
        return new RoadViews.MapRoad(road.id(), road.kingdomId(), road.dimension(), road.roadClass(), color(server, road.kingdomId()), built,
                xs.stream().mapToInt(Integer::intValue).toArray(), zs.stream().mapToInt(Integer::intValue).toArray());
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
