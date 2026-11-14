package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomRegistry;
import io.github.sekerator.kingdomworld.core.road.CellIndex;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.Wall;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Where the cities of each kingdom lie, for questions asked on every block event and every blow: whether a place is inside
 * a city of a kingdom. A city with a wall is the square of its wall with the wall itself; a city without one is the square
 * its wall would take at its level (a village, a town, a large city), or by the plan of a capital of a kingdom of the world
 * where that is wider. A city of more people than that square holds, built densely, is wider by them, without an upper
 * limit, but where it grew it lies on the land of its kingdom only. The squares are worked out once in
 * {@value #REFRESH_TICKS} ticks and sorted into cells of 512 blocks, so that asking costs a look at the few cities near the
 * place.
 */
public final class CityAreas {
    /** Ticks the worked out squares are kept. */
    static final int REFRESH_TICKS = 100;

    /**
     * The square of a city.
     *
     * @param cityId 0 for the capital
     * @param radius half the side of the square, in blocks
     * @param walled whether a wall stands around it
     * @param base   half the side of the square by its level, plan and wall alone; beyond it, up to {@code radius}, the city
     *               grew by its people and holds the land of its kingdom only
     * @param landOnly whether the whole square holds the land of its kingdom only: a city of a kingdom of players lives within its
     *                 state, and the part of its square beyond the border is no city for the law, the work, the beds or the guard
     */
    public record Area(UUID kingdomId, long cityId, String dimension, int x, int z, int radius, boolean walled, int base, boolean landOnly) {
        public Area(UUID kingdomId, long cityId, String dimension, int x, int z, int radius, boolean walled) {
            this(kingdomId, cityId, dimension, x, z, radius, walled, radius, false);
        }

        public Area(UUID kingdomId, long cityId, String dimension, int x, int z, int radius, boolean walled, int base) {
            this(kingdomId, cityId, dimension, x, z, radius, walled, base, false);
        }

        public boolean contains(String dimension, int px, int pz) {
            return this.dimension.equals(dimension) && Math.abs(px - x) <= radius && Math.abs(pz - z) <= radius;
        }

        /**
         * Whether a place it contains lies where the city grew by its people, beyond the square of its level, plan and wall.
         */
        public boolean grown(int px, int pz) {
            return Math.abs(px - x) > base || Math.abs(pz - z) > base;
        }
    }

    /** The side of a cell the squares are sorted into, as a power of two: 512 blocks. */
    private static final int CELL_SHIFT = 9;

    private static Map<UUID, List<Area>> areas = Map.of();
    /** The squares of each kingdom sorted into cells of 512 blocks, so that a question looks at the few near the place. */
    private static Map<UUID, CellIndex<Area>> cells = Map.of();
    private static long builtAt = Long.MIN_VALUE;
    private static long builtSignature;
    private static MinecraftServer builtFor;
    /** The cities and walls and the list of kingdoms the last signature was worked out from, with their versions then. */
    private static RoadState signedRoads;
    private static long signedRoadVersion;
    private static KingdomRegistry signedKingdoms;
    private static long signedKingdomVersion;
    private static long signedSettlements = Long.MIN_VALUE;
    /** The settlements and their version the squares of the villages were last summed from, and the sum. */
    private static io.github.sekerator.kingdomworld.core.npc.NpcState villagesOf;
    private static long villagesVersion = Long.MIN_VALUE;
    private static long villages;
    private static long signed;
    /** Grows whenever the squares are worked out anew. */
    private static long generation;

    private CityAreas() {
    }

    static void reset() {
        invalidate();
        builtFor = null;
        signedRoads = null;
        signedKingdoms = null;
        villagesOf = null;
    }

    /**
     * The squares are worked out anew at the next question: a wall was built or pulled down, a city founded or lost.
     */
    public static void invalidate() {
        areas = Map.of();
        cells = Map.of();
        builtAt = Long.MIN_VALUE;
    }

    /**
     * A number that changes when a kingdom, a city or a wall appears or goes: the squares are then worked out at once rather
     * than at the next turn. It is worked out again only when the cities and walls or the list of kingdoms changed since the
     * last time, which their versions tell, so that a question does not go through all of them.
     */
    private static long signature(MinecraftServer server) {
        RoadState roads = RoadWork.state(server);
        KingdomRegistry kingdoms = KingdomWorldApi.state(server).kingdoms();
        long settlements = villages(server);
        if (roads == signedRoads && roads.version() == signedRoadVersion && kingdoms == signedKingdoms
                && kingdoms.version() == signedKingdomVersion && settlements == signedSettlements) {
            return signed;
        }
        signedSettlements = settlements;
        long sum = 17;
        for (var kingdom : kingdoms.all()) {
            sum = sum * 31 + kingdom.id().hashCode();
        }
        for (var city : roads.cities()) {
            sum = sum * 31 + city.id();
        }
        for (Wall wall : roads.walls()) {
            sum = sum * 31 + wall.id() * 7 + wall.cells().size();
        }
        sum = sum * 31 + settlements;
        signedRoads = roads;
        signedRoadVersion = roads.version();
        signedKingdoms = kingdoms;
        signedKingdomVersion = kingdoms.version();
        signed = sum;
        return sum;
    }

    private static Map<UUID, List<Area>> all(MinecraftServer server) {
        build(server);
        return areas;
    }

    private static void build(MinecraftServer server) {
        long now = server.getTickCount();
        long signature = signature(server);
        if (builtFor == server && builtSignature == signature && builtAt != Long.MIN_VALUE && now - builtAt < REFRESH_TICKS && now >= builtAt) {
            return;
        }
        builtSignature = signature;
        generation++;
        RoadState roads = RoadWork.state(server);
        List<CityWalls.Place> places = CityWalls.places(server);
        // The people and buildings of a kingdom are counted once for all of its cities; the people of a city with a wall widen it
        // beyond its wall when they need more room.
        int[][] counts = CityWalls.counts(server, places);
        Map<UUID, List<Area>> built = new HashMap<>();
        Map<UUID, CellIndex<Area>> sorted = new HashMap<>();
        Map<UUID, Boolean> ofPlayers = new HashMap<>();
        for (int i = 0; i < places.size(); i++) {
            CityWalls.Place place = places.get(i);
            Optional<Wall> wall = roads.wallOf(place.cityId(), place.kingdomId());
            int people = CityWalls.peopleRadius(counts[i][0]);
            boolean landOnly = ofPlayers.computeIfAbsent(place.kingdomId(),
                    id -> !io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, id));
            Area area;
            if (wall.isPresent()) {
                Wall found = wall.get();
                area = new Area(place.kingdomId(), place.cityId(), found.dimension(), found.centerX(), found.centerZ(),
                        Math.max(found.radius(), people), true, found.radius(), landOnly);
            } else {
                int base = CityWalls.baseRadius(server, place.kingdomId(), place.cityId(), CityWalls.level(counts[i]));
                area = new Area(place.kingdomId(), place.cityId(), place.dimension(), place.x(), place.z(), Math.max(base, people), false, base,
                        landOnly);
            }
            built.computeIfAbsent(place.kingdomId(), k -> new ArrayList<>()).add(area);
            sorted.computeIfAbsent(place.kingdomId(), k -> new CellIndex<>(CELL_SHIFT)).add(area.dimension(),
                    (long) area.x() - area.radius(), (long) area.z() - area.radius(), (long) area.x() + area.radius(),
                    (long) area.z() + area.radius(), area);
        }
        // A village or a hamlet of a state that stands has no town hall, but its law and its watch hold on its land as in a town.
        String overworld = io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(server.overworld());
        for (io.github.sekerator.kingdomworld.core.npc.Settlement settlement
                : io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server).settlements()) {
            if (!village(settlement) || KingdomWorldApi.state(server).kingdoms().get(settlement.kingdomId()).isEmpty()) {
                continue;
            }
            int radius = io.github.sekerator.kingdomworld.core.npc.SettlementLayouts.land(settlement.tier()) * 8 + 8;
            Area area = new Area(settlement.kingdomId(), -settlement.id(), overworld, settlement.x(), settlement.z(), radius, false);
            built.computeIfAbsent(settlement.kingdomId(), k -> new ArrayList<>()).add(area);
            sorted.computeIfAbsent(settlement.kingdomId(), k -> new CellIndex<>(CELL_SHIFT)).add(area.dimension(),
                    (long) area.x() - area.radius(), (long) area.z() - area.radius(), (long) area.x() + area.radius(),
                    (long) area.z() + area.radius(), area);
        }
        areas = built;
        cells = sorted;
        builtAt = now;
        builtFor = server;
    }

    /**
     * A sum of what makes the squares of the villages and hamlets of the states: which stand without a town hall, of which
     * kingdom, rank and place. The settlements change every tick one of them rises; the sum is worked out again only when
     * their version moved, and changes only when a square does, so that no question of the law rebuilds the squares for that.
     */
    private static long villages(MinecraftServer server) {
        io.github.sekerator.kingdomworld.core.npc.NpcState npc = io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server);
        if (npc == villagesOf && npc.settlementsVersion() == villagesVersion) {
            return villages;
        }
        long sum = 29;
        for (io.github.sekerator.kingdomworld.core.npc.Settlement settlement : npc.settlements()) {
            if (village(settlement)) {
                sum = sum * 31 + settlement.id();
                sum = sum * 31 + settlement.kingdomId().hashCode();
                sum = sum * 31 + settlement.tier().ordinal();
                sum = sum * 31 + settlement.x();
                sum = sum * 31 + settlement.z();
            }
        }
        villagesOf = npc;
        villagesVersion = npc.settlementsVersion();
        villages = sum;
        return sum;
    }

    /**
     * Whether a settlement of a state has a square of the law of its own: it stands and has no town hall.
     */
    private static boolean village(io.github.sekerator.kingdomworld.core.npc.Settlement settlement) {
        return settlement.status() == io.github.sekerator.kingdomworld.core.npc.Settlement.Status.STANDING && settlement.cityId() == 0
                && settlement.tier() != io.github.sekerator.kingdomworld.core.land.Tier.CAPITAL;
    }

    /**
     * A number that grows whenever the squares are worked out anew: what is worked out from them is worked out again then.
     */
    public static long generation(MinecraftServer server) {
        build(server);
        return generation;
    }

    /**
     * The cities of a kingdom, its capital among them.
     */
    public static List<Area> of(MinecraftServer server, UUID kingdomId) {
        return all(server).getOrDefault(kingdomId, List.of());
    }

    /**
     * The city of a kingdom a place lies in, if it lies in one: the first of {@link #of} that holds it. Only the cities whose
     * square meets the cell of 512 blocks of the place are asked. Where a city grew by its people, only the land of its kingdom
     * is in it.
     */
    public static Optional<Area> at(MinecraftServer server, UUID kingdomId, String dimension, int x, int z) {
        build(server);
        CellIndex<Area> index = cells.get(kingdomId);
        if (index == null) {
            return Optional.empty();
        }
        for (Area area : index.at(dimension, x, z)) {
            if (holds(server, area, dimension, x, z)) {
                return Optional.of(area);
            }
        }
        return Optional.empty();
    }

    /**
     * The city (0 for the capital, never a village of a state) of a kingdom whose square holds a place and whose middle is
     * nearest to it, if any holds it: where the squares of two cities meet, the place belongs to the nearer one.
     */
    public static Optional<Area> nearestAt(MinecraftServer server, UUID kingdomId, String dimension, int x, int z) {
        build(server);
        CellIndex<Area> index = cells.get(kingdomId);
        if (index == null) {
            return Optional.empty();
        }
        Area best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Area area : index.at(dimension, x, z)) {
            if (area.cityId() < 0 || !holds(server, area, dimension, x, z)) {
                continue;
            }
            long dx = (long) x - area.x();
            long dz = (long) z - area.z();
            long distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                best = area;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Whether a city holds a place: its square contains it, and where the city grew by its people, and anywhere in a city of a
     * kingdom of players, the place lies on the land of its kingdom.
     */
    private static boolean holds(MinecraftServer server, Area area, String dimension, int x, int z) {
        if (!area.contains(dimension, x, z)) {
            return false;
        }
        return !area.landOnly() && !area.grown(x, z) || area.kingdomId().equals(KingdomWorldApi.state(server).territory().ownerOf(dimension,
                io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(x >> 4, z >> 4)));
    }

    /**
     * The city of a kingdom whose square, widened by a margin on every side, holds a place, if one does.
     */
    public static Optional<Area> near(MinecraftServer server, UUID kingdomId, String dimension, int x, int z, int margin) {
        build(server);
        CellIndex<Area> index = cells.get(kingdomId);
        if (index == null) {
            return Optional.empty();
        }
        // A square within the margin of the place meets the cell of the place or of a corner of the margin around it.
        for (int dx = -margin; dx <= margin; dx += Math.max(1, margin)) {
            for (int dz = -margin; dz <= margin; dz += Math.max(1, margin)) {
                for (Area area : index.at(dimension, x + dx, z + dz)) {
                    if (area.dimension().equals(dimension) && Math.abs(x - area.x()) <= area.radius() + margin
                            && Math.abs(z - area.z()) <= area.radius() + margin) {
                        return Optional.of(area);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a place lies inside a city of the kingdom.
     */
    public static boolean inCity(MinecraftServer server, UUID kingdomId, String dimension, int x, int z) {
        return at(server, kingdomId, dimension, x, z).isPresent();
    }

    /**
     * The city of any kingdom a place lies in, for checks.
     */
    public static Optional<Area> at(MinecraftServer server, String dimension, int x, int z) {
        UUID owner = KingdomWorldApi.state(server).territory().ownerOf(dimension,
                io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(x >> 4, z >> 4));
        return owner == null ? Optional.empty() : at(server, owner, dimension, x, z);
    }
}
