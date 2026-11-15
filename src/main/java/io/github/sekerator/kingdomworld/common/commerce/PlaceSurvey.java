package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.production.Workstations;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The workstations of the places of the shops of citizens: inside the box of a house, inside a market for its stalls, within a
 * few blocks of a counter. A place is looked at when a chunk of it is scanned and all of it is loaded, at most once a game day;
 * no chunk is loaded for it. A place nobody looked at yet resells.
 */
public final class PlaceSurvey {
    /** How far from a counter its workstations stand. */
    static final int COUNTER_REACH = 4;

    private PlaceSurvey() {
    }

    /**
     * The blocks where the workstations of a place stand.
     */
    record Area(String dimension, Box box) {
        boolean touches(long chunk) {
            int x = ChunkKey.x(chunk);
            int z = ChunkKey.z(chunk);
            return box.maxX() >> 4 >= x && box.minX() >> 4 <= x && box.maxZ() >> 4 >= z && box.minZ() >> 4 <= z;
        }
    }

    static Optional<Area> areaOf(MinecraftServer server, Business.Place place) {
        if (place.kind() == Business.PlaceKind.COUNTER) {
            return Optional.of(new Area(place.dimension(), new Box(place.x() - COUNTER_REACH, place.y() - COUNTER_REACH, place.z() - COUNTER_REACH,
                    place.x() + COUNTER_REACH, place.y() + COUNTER_REACH, place.z() + COUNTER_REACH)));
        }
        Optional<Building> building = KingdomWorldApi.state(server).construction().building(place.building());
        return building.map(found -> new Area(found.dimension(), found.box()));
    }

    /**
     * A chunk of the land of a kingdom was scanned: the places of its shops that touch it, are all loaded and were not looked at
     * today are looked at.
     */
    public static void chunkScanned(ServerLevel level, long chunk, UUID owner) {
        MinecraftServer server = level.getServer();
        if (owner == null || KingdomWorldApi.isReadOnly(server) || !CityOf.active(server, owner)) {
            return;
        }
        String dimension = Dimensions.id(level);
        long day = TreasuryActions.day(server);
        CommerceSavedData data = CommerceSavedData.get(server);
        // The stalls of one market share its box: it is looked at once.
        Map<String, Set<String>> seen = new HashMap<>();
        for (List<BusinessPlaces.Spot> spots : BusinessPlaces.all(server, owner).values()) {
            for (BusinessPlaces.Spot spot : spots) {
                Business.Place place = spot.place();
                if (!place.dimension().equals(dimension)) {
                    continue;
                }
                Optional<CommerceSavedData.Survey> known = data.survey(place.key());
                if (known.isPresent() && known.get().day() >= day) {
                    continue;
                }
                Optional<Area> area = areaOf(server, place);
                if (area.isEmpty() || !area.get().touches(chunk) || !loaded(level, area.get().box())) {
                    continue;
                }
                String areaKey = place.kind() == Business.PlaceKind.COUNTER ? place.key() : "building/" + place.building();
                Set<String> stations = seen.computeIfAbsent(areaKey, key -> scan(level, area.get().box()));
                data.putSurvey(place.key(), new CommerceSavedData.Survey(stations, day));
            }
        }
    }

    private static boolean loaded(ServerLevel level, Box box) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The kinds of workstations in a box of loaded chunks.
     */
    static Set<String> scan(ServerLevel level, Box box) {
        Set<String> found = new LinkedHashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight(), box.minY());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, box.maxY());
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir() || !Workstations.mayBePlaceStation(state)) {
                        continue;
                    }
                    String kind = Workstations.kindOf(level, pos, state);
                    if (kind != null) {
                        found.add(kind);
                    }
                }
            }
        }
        return found;
    }

    /**
     * The workstations of a place as last seen, empty when nobody looked at it yet.
     */
    public static Optional<Set<String>> stations(MinecraftServer server, String placeKey) {
        return CommerceSavedData.get(server).survey(placeKey).map(CommerceSavedData.Survey::stations);
    }
}
