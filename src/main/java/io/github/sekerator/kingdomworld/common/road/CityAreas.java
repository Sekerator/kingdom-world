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
 * where that is wider. The squares are worked out once in
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
     */
    public record Area(UUID kingdomId, long cityId, String dimension, int x, int z, int radius, boolean walled) {
        public boolean contains(String dimension, int px, int pz) {
            return this.dimension.equals(dimension) && Math.abs(px - x) <= radius && Math.abs(pz - z) <= radius;
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
    private static long signed;

    private CityAreas() {
    }

    static void reset() {
        invalidate();
        builtFor = null;
        signedRoads = null;
        signedKingdoms = null;
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
        if (roads == signedRoads && roads.version() == signedRoadVersion && kingdoms == signedKingdoms
                && kingdoms.version() == signedKingdomVersion) {
            return signed;
        }
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
        RoadState roads = RoadWork.state(server);
        List<CityWalls.Place> places = CityWalls.places(server);
        List<Optional<Wall>> walls = new ArrayList<>(places.size());
        List<CityWalls.Place> open = new ArrayList<>();
        for (CityWalls.Place place : places) {
            Optional<Wall> wall = roads.wallOf(place.cityId(), place.kingdomId());
            walls.add(wall);
            if (wall.isEmpty()) {
                open.add(place);
            }
        }
        // The people and buildings of a kingdom are counted once for all of its cities without a wall.
        int[][] counts = CityWalls.counts(server, open);
        int next = 0;
        Map<UUID, List<Area>> built = new HashMap<>();
        Map<UUID, CellIndex<Area>> sorted = new HashMap<>();
        for (int i = 0; i < places.size(); i++) {
            CityWalls.Place place = places.get(i);
            Optional<Wall> wall = walls.get(i);
            Area area;
            if (wall.isPresent()) {
                Wall found = wall.get();
                area = new Area(place.kingdomId(), place.cityId(), found.dimension(), found.centerX(), found.centerZ(), found.radius(), true);
            } else {
                int radius = CityWalls.radiusOf(server, place.kingdomId(), place.cityId(), CityWalls.level(counts[next++]));
                area = new Area(place.kingdomId(), place.cityId(), place.dimension(), place.x(), place.z(), radius, false);
            }
            built.computeIfAbsent(place.kingdomId(), k -> new ArrayList<>()).add(area);
            sorted.computeIfAbsent(place.kingdomId(), k -> new CellIndex<>(CELL_SHIFT)).add(area.dimension(),
                    (long) area.x() - area.radius(), (long) area.z() - area.radius(), (long) area.x() + area.radius(),
                    (long) area.z() + area.radius(), area);
        }
        areas = built;
        cells = sorted;
        builtAt = now;
        builtFor = server;
    }

    /**
     * The cities of a kingdom, its capital among them.
     */
    public static List<Area> of(MinecraftServer server, UUID kingdomId) {
        return all(server).getOrDefault(kingdomId, List.of());
    }

    /**
     * The city of a kingdom a place lies in, if it lies in one: the first of {@link #of} that holds it. Only the cities whose
     * square meets the cell of 512 blocks of the place are asked.
     */
    public static Optional<Area> at(MinecraftServer server, UUID kingdomId, String dimension, int x, int z) {
        build(server);
        CellIndex<Area> index = cells.get(kingdomId);
        if (index == null) {
            return Optional.empty();
        }
        for (Area area : index.at(dimension, x, z)) {
            if (area.contains(dimension, x, z)) {
                return Optional.of(area);
            }
        }
        return Optional.empty();
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
