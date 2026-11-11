package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
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
 * its wall would take at its level (a village, a town, a large city). The squares are worked out once in
 * {@value #REFRESH_TICKS} ticks, so that asking costs a look into a short list.
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

    private static Map<UUID, List<Area>> areas = Map.of();
    private static long builtAt = Long.MIN_VALUE;
    private static long builtSignature;
    private static MinecraftServer builtFor;

    private CityAreas() {
    }

    static void reset() {
        invalidate();
        builtFor = null;
    }

    /**
     * The squares are worked out anew at the next question: a wall was built or pulled down, a city founded or lost.
     */
    public static void invalidate() {
        areas = Map.of();
        builtAt = Long.MIN_VALUE;
    }

    /**
     * A number that changes when a kingdom, a city or a wall appears or goes: the squares are then worked out at once rather
     * than at the next turn.
     */
    private static long signature(MinecraftServer server) {
        long sum = 17;
        for (var kingdom : KingdomWorldApi.state(server).kingdoms().all()) {
            sum = sum * 31 + kingdom.id().hashCode();
        }
        for (var city : RoadWork.state(server).cities()) {
            sum = sum * 31 + city.id();
        }
        for (Wall wall : RoadWork.state(server).walls()) {
            sum = sum * 31 + wall.id() * 7 + wall.cells().size();
        }
        return sum;
    }

    private static Map<UUID, List<Area>> all(MinecraftServer server) {
        long now = server.getTickCount();
        long signature = signature(server);
        if (builtFor == server && builtSignature == signature && builtAt != Long.MIN_VALUE && now - builtAt < REFRESH_TICKS && now >= builtAt) {
            return areas;
        }
        builtSignature = signature;
        RoadRules rules = RoadSettings.rules();
        Map<UUID, List<Area>> built = new HashMap<>();
        for (CityWalls.Place place : CityWalls.places(server)) {
            Optional<Wall> wall = RoadWork.state(server).wallOf(place.cityId(), place.kingdomId());
            Area area = wall.map(found -> new Area(place.kingdomId(), place.cityId(), found.dimension(), found.centerX(), found.centerZ(),
                            found.radius(), true))
                    .orElseGet(() -> new Area(place.kingdomId(), place.cityId(), place.dimension(), place.x(), place.z(),
                            CityWalls.level(server, place.kingdomId(), place.cityId()).wallRadius(rules), false));
            built.computeIfAbsent(place.kingdomId(), k -> new ArrayList<>()).add(area);
        }
        areas = built;
        builtAt = now;
        builtFor = server;
        return built;
    }

    /**
     * The cities of a kingdom, its capital among them.
     */
    public static List<Area> of(MinecraftServer server, UUID kingdomId) {
        return all(server).getOrDefault(kingdomId, List.of());
    }

    /**
     * The city of a kingdom a place lies in, if it lies in one.
     */
    public static Optional<Area> at(MinecraftServer server, UUID kingdomId, String dimension, int x, int z) {
        for (Area area : of(server, kingdomId)) {
            if (area.contains(dimension, x, z)) {
                return Optional.of(area);
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
