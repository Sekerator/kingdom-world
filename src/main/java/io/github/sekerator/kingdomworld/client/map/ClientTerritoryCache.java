package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaPacket;
import io.github.sekerator.kingdomworld.common.network.packet.TerritoryDeltaPacket;
import io.github.sekerator.kingdomworld.common.network.packet.TerritorySnapshotPacket;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.TerritoryListener;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the client knows about land in its current dimension: owners of the chunks it has asked for and the
 * map facts of the kingdoms seen. Display only; the server stays the source of truth. Map integrations can
 * read it and follow changes with {@link #addListener(TerritoryListener)}. Used on the client main thread.
 */
public final class ClientTerritoryCache {
    private static final Map<Long, UUID> OWNERS = new HashMap<>();
    private static final Map<UUID, KingdomMapInfo> KINGDOMS = new HashMap<>();
    private static final List<TerritoryListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static String dimension;
    private static boolean hasArea;
    private static int areaMinX;
    private static int areaMinZ;
    private static int areaMaxX;
    private static int areaMaxZ;
    private static int generation;
    /** Owners of squares of chunks for a map zoomed far out: the last such area asked for. */
    private static int coarseStep;
    private static int coarseMinX;
    private static int coarseMinZ;
    private static int coarseWidth;
    private static int coarseHeight;
    private static UUID[] coarseOwners = new UUID[0];

    private ClientTerritoryCache() {
    }

    public static void addListener(TerritoryListener listener) {
        LISTENERS.add(Objects.requireNonNull(listener, "listener"));
    }

    public static void removeListener(TerritoryListener listener) {
        LISTENERS.remove(listener);
    }

    public static void clear() {
        OWNERS.clear();
        KINGDOMS.clear();
        coarseStep = 0;
        coarseOwners = new UUID[0];
        dimension = null;
        hasArea = false;
        generation++;
    }

    /**
     * Changes whenever the cache starts over (a new world or dimension) without reporting single chunks.
     */
    public static int generation() {
        return generation;
    }

    public static String dimension() {
        return dimension;
    }

    public static UUID ownerOf(int chunkX, int chunkZ) {
        return OWNERS.get(ChunkKey.of(chunkX, chunkZ));
    }

    public static KingdomMapInfo kingdom(UUID id) {
        return id == null ? null : KINGDOMS.get(id);
    }

    public static Map<UUID, KingdomMapInfo> kingdoms() {
        return Collections.unmodifiableMap(KINGDOMS);
    }

    /**
     * Whether the last answered request covers the whole rectangle.
     */
    public static boolean covers(String currentDimension, int minX, int minZ, int maxX, int maxZ) {
        return hasArea && currentDimension.equals(dimension)
                && minX >= areaMinX && minZ >= areaMinZ && maxX <= areaMaxX && maxZ <= areaMaxZ;
    }

    public static void applyArea(MapAreaPacket packet) {
        switchDimension(packet.dimension());
        for (KingdomMapInfo info : packet.palette()) {
            KINGDOMS.put(info.id(), info);
        }
        if (packet.step() > 1) {
            // Squares of chunks for a map zoomed far out are kept apart: the chunks known one by one stay as they are.
            coarseStep = packet.step();
            coarseMinX = packet.minChunkX();
            coarseMinZ = packet.minChunkZ();
            coarseWidth = packet.width();
            coarseHeight = packet.height();
            coarseOwners = new UUID[packet.width() * packet.height()];
            for (int i = 0; i < coarseOwners.length; i++) {
                int index = packet.cells()[i] - 1;
                coarseOwners[i] = index < 0 ? null : packet.palette().get(index).id();
            }
            return;
        }
        for (int z = 0; z < packet.height(); z++) {
            for (int x = 0; x < packet.width(); x++) {
                int chunkX = packet.minChunkX() + x;
                int chunkZ = packet.minChunkZ() + z;
                int index = packet.ownerIndex(chunkX, chunkZ);
                UUID owner = index < 0 ? null : packet.palette().get(index).id();
                set(chunkX, chunkZ, owner);
            }
        }
        hasArea = true;
        areaMinX = packet.minChunkX();
        areaMinZ = packet.minChunkZ();
        areaMaxX = packet.minChunkX() + packet.width() - 1;
        areaMaxZ = packet.minChunkZ() + packet.height() - 1;
    }

    /**
     * The side of the squares of chunks known for a map zoomed far out, or 0 when none are.
     */
    public static int coarseStep() {
        return coarseStep;
    }

    /**
     * Whether the squares known for a map zoomed far out have this side and cover a rectangle of chunks.
     */
    public static boolean coarseCovers(int step, int minX, int minZ, int maxX, int maxZ) {
        return coarseStep == step && minX >= coarseMinX && minZ >= coarseMinZ && maxX < coarseMinX + coarseWidth * step
                && maxZ < coarseMinZ + coarseHeight * step;
    }

    /**
     * The owner of the square of the map zoomed far out that holds a chunk, or {@code null}.
     */
    public static UUID coarseOwnerOf(int chunkX, int chunkZ) {
        if (coarseStep == 0) {
            return null;
        }
        int x = Math.floorDiv(chunkX - coarseMinX, coarseStep);
        int z = Math.floorDiv(chunkZ - coarseMinZ, coarseStep);
        return x < 0 || z < 0 || x >= coarseWidth || z >= coarseHeight ? null : coarseOwners[z * coarseWidth + x];
    }

    /**
     * Land of the dimension for map integrations: all of it, or the square the packet covers. Chunks the snapshot
     * covers but does not list become unclaimed.
     */
    public static void applySnapshot(TerritorySnapshotPacket packet) {
        switchDimension(packet.dimension());
        Map<Long, UUID> next = new HashMap<>();
        for (int i = 0; i < packet.palette().size(); i++) {
            KingdomMapInfo info = packet.palette().get(i);
            KINGDOMS.put(info.id(), info);
            for (long chunk : packet.chunks().get(i)) {
                next.put(chunk, info.id());
            }
        }
        for (Long chunk : List.copyOf(OWNERS.keySet())) {
            if (!next.containsKey(chunk) && packet.covers(ChunkKey.x(chunk), ChunkKey.z(chunk))) {
                set(ChunkKey.x(chunk), ChunkKey.z(chunk), null);
            }
        }
        next.forEach((chunk, owner) -> set(ChunkKey.x(chunk), ChunkKey.z(chunk), owner));
    }

    /**
     * Every chunk with a known owner in the current dimension, read-only.
     */
    public static Map<Long, UUID> owners() {
        return Collections.unmodifiableMap(OWNERS);
    }

    public static void applyDelta(TerritoryDeltaPacket packet) {
        for (KingdomMapInfo info : packet.palette()) {
            KINGDOMS.put(info.id(), info);
        }
        if (!packet.dimension().equals(dimension)) {
            return;
        }
        long[] chunks = packet.chunks();
        for (int i = 0; i < chunks.length; i++) {
            int index = packet.owners()[i] - 1;
            UUID owner = index < 0 ? null : packet.palette().get(index).id();
            set(ChunkKey.x(chunks[i]), ChunkKey.z(chunks[i]), owner);
        }
    }

    private static void switchDimension(String newDimension) {
        if (!newDimension.equals(dimension)) {
            OWNERS.clear();
            hasArea = false;
            coarseStep = 0;
            dimension = newDimension;
            generation++;
        }
    }

    private static void set(int chunkX, int chunkZ, UUID owner) {
        long key = ChunkKey.of(chunkX, chunkZ);
        UUID old = owner == null ? OWNERS.remove(key) : OWNERS.put(key, owner);
        if (!Objects.equals(old, owner)) {
            for (TerritoryListener listener : LISTENERS) {
                listener.onChunkChanged(dimension, key, old, owner);
            }
        }
    }
}
