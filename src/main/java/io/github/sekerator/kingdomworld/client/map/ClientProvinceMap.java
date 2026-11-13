package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.packet.ProvinceDeltaPacket;
import io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the client knows of the provinces of its dimension: their tiles, their table and their holders, as the server sends
 * them ({@link ProvinceSnapshotPacket}, {@link ProvinceDeltaPacket}). Display only. The atlas and the map integrations read it
 * on the client main thread; threads of their own read the {@link #view() view}, a copy that never changes. A tile is always
 * replaced whole, never changed in place, so the views share the tiles.
 */
public final class ClientProvinceMap {
    /** Chunks around the player whose changes of holder the map integrations hear of chunk by chunk. */
    public static final int NEAR_CHUNKS = 32;

    /**
     * The provinces as they were at a moment, for other threads: never changed once made.
     *
     * @param tiles     the numbers of the provinces, tile by tile
     * @param provinces the provinces by number, {@code null} for none
     * @param owners    the holders by number, {@code null} for nobody
     */
    public record View(String dimension, Long2ObjectMap<char[]> tiles, ProvinceSnapshotPacket.Entry[] provinces, UUID[] owners, long version) {
        public static final View EMPTY = new View(null, new Long2ObjectOpenHashMap<>(), new ProvinceSnapshotPacket.Entry[1], new UUID[1], 0);

        public int provinceAt(int chunkX, int chunkZ) {
            char[] tile = tiles.get(ProvinceMap.tileKey(chunkX >> 5, chunkZ >> 5));
            return tile == null ? 0 : tile[ProvinceMap.indexInTile(chunkX, chunkZ)];
        }

        public UUID ownerAt(int chunkX, int chunkZ) {
            int id = provinceAt(chunkX, chunkZ);
            return id == 0 || id >= owners.length ? null : owners[id];
        }

        public UUID owner(int id) {
            return id <= 0 || id >= owners.length ? null : owners[id];
        }

        public ProvinceSnapshotPacket.Entry province(int id) {
            return id <= 0 || id >= provinces.length ? null : provinces[id];
        }

        /**
         * The chunks of a province, read from the tiles.
         */
        public List<Long> chunksOf(int id) {
            List<Long> chunks = new ArrayList<>();
            for (Long2ObjectMap.Entry<char[]> entry : tiles.long2ObjectEntrySet()) {
                char[] tile = entry.getValue();
                int baseX = ProvinceMap.tileX(entry.getLongKey()) * ProvinceMap.TILE;
                int baseZ = ProvinceMap.tileZ(entry.getLongKey()) * ProvinceMap.TILE;
                for (int i = 0; i < tile.length; i++) {
                    if (tile[i] == id) {
                        chunks.add(ChunkKey.of(baseX + (i & 31), baseZ + (i >> 5)));
                    }
                }
            }
            return chunks;
        }
    }

    private static final Long2ObjectOpenHashMap<char[]> TILES = new Long2ObjectOpenHashMap<>();
    private static ProvinceSnapshotPacket.Entry[] provinces = new ProvinceSnapshotPacket.Entry[1];
    private static UUID[] owners = new UUID[1];
    /** For every province, a number that grows whenever its chunks change: outlines are made again only then. */
    private static int[] shapeVersions = new int[1];
    private static String dimension;
    private static long version;
    private static long ownersVersion;
    private static volatile View view = View.EMPTY;

    private ClientProvinceMap() {
    }

    public static String dimension() {
        return dimension;
    }

    /**
     * Grows with every change of the provinces known.
     */
    public static long version() {
        return version;
    }

    /**
     * Grows whenever a province changes hands.
     */
    public static long ownersVersion() {
        return ownersVersion;
    }

    public static View view() {
        return view;
    }

    public static boolean isEmpty() {
        return TILES.isEmpty();
    }

    public static int provinceAt(int chunkX, int chunkZ) {
        char[] tile = TILES.get(ProvinceMap.tileKey(chunkX >> 5, chunkZ >> 5));
        return tile == null ? 0 : tile[ProvinceMap.indexInTile(chunkX, chunkZ)];
    }

    public static UUID ownerAt(int chunkX, int chunkZ) {
        return owner(provinceAt(chunkX, chunkZ));
    }

    public static UUID owner(int id) {
        return id <= 0 || id >= owners.length ? null : owners[id];
    }

    public static ProvinceSnapshotPacket.Entry province(int id) {
        return id <= 0 || id >= provinces.length ? null : provinces[id];
    }

    /**
     * Every province known, in the order of their numbers.
     */
    public static List<ProvinceSnapshotPacket.Entry> provinces() {
        List<ProvinceSnapshotPacket.Entry> list = new ArrayList<>();
        for (ProvinceSnapshotPacket.Entry entry : provinces) {
            if (entry != null) {
                list.add(entry);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public static int shapeVersion(int id) {
        return id <= 0 || id >= shapeVersions.length ? 0 : shapeVersions[id];
    }

    public static int tileCount() {
        return TILES.size();
    }

    private static UUID heldCountOf;
    private static long heldCountVersion = -1;
    private static long heldCount;

    /**
     * The chunks of the provinces a kingdom holds, as the client knows them; counted again only when the provinces changed.
     */
    public static long chunksHeldBy(UUID kingdom) {
        if (kingdom == null) {
            return 0;
        }
        if (kingdom.equals(heldCountOf) && heldCountVersion == version) {
            return heldCount;
        }
        boolean[] held = new boolean[owners.length];
        boolean any = false;
        for (int id = 1; id < owners.length; id++) {
            held[id] = kingdom.equals(owners[id]);
            any |= held[id];
        }
        long count = 0;
        if (any) {
            for (char[] tile : TILES.values()) {
                for (char id : tile) {
                    if (id != 0 && id < held.length && held[id]) {
                        count++;
                    }
                }
            }
        }
        heldCountOf = kingdom;
        heldCountVersion = version;
        heldCount = count;
        return count;
    }

    /**
     * About what the provinces known take in memory.
     */
    public static long memoryBytes() {
        return (long) TILES.size() * (ProvinceMap.TILE_CHUNKS * 2L + 48) + provinces.length * 72L;
    }

    public static void clear() {
        boolean had = !TILES.isEmpty() || dimension != null;
        TILES.clear();
        provinces = new ProvinceSnapshotPacket.Entry[1];
        owners = new UUID[1];
        shapeVersions = new int[1];
        dimension = null;
        if (had) {
            version++;
            ownersVersion++;
        }
        view = View.EMPTY;
    }

    // ---------------------------------------------------------------- from the server

    public static void apply(ProvinceSnapshotPacket packet) {
        for (KingdomMapInfo info : packet.palette()) {
            ClientTerritoryCache.remember(info);
        }
        if (packet.reset()) {
            clear();
            dimension = packet.dimension();
        } else if (!packet.dimension().equals(dimension)) {
            return;
        }
        // The table first: the chunks of a tile are told with their holders.
        for (int id : packet.removed()) {
            if (id < provinces.length) {
                provinces[id] = null;
                setOwner(id, null);
                bump(id);
            }
        }
        for (int i = 0; i < packet.provinces().size(); i++) {
            ProvinceSnapshotPacket.Entry entry = packet.provinces().get(i);
            grow(entry.id());
            provinces[entry.id()] = entry;
            int index = packet.owners()[i] - 1;
            setOwner(entry.id(), index < 0 ? null : packet.palette().get(index).id());
        }
        long[] keys = packet.tileKeys();
        for (int i = 0; i < keys.length; i++) {
            char[] tile = packet.tiles().get(i);
            char[] old = tile.length == 0 ? TILES.remove(keys[i]) : TILES.put(keys[i], tile);
            touchShapes(old);
            touchShapes(tile);
            tellNear(keys[i], old, tile);
        }
        version++;
        publish();
    }

    public static void apply(ProvinceDeltaPacket packet) {
        for (KingdomMapInfo info : packet.palette()) {
            ClientTerritoryCache.remember(info);
        }
        if (!packet.dimension().equals(dimension)) {
            return;
        }
        for (int i = 0; i < packet.provinces().length; i++) {
            int index = packet.owners()[i] - 1;
            setOwner(packet.provinces()[i], index < 0 ? null : packet.palette().get(index).id());
        }
        version++;
        publish();
    }

    private static void setOwner(int id, UUID owner) {
        grow(id);
        UUID old = owners[id];
        if (Objects.equals(old, owner)) {
            return;
        }
        owners[id] = owner;
        ownersVersion++;
        tellOwnerNear(id, old, owner);
    }

    private static void grow(int id) {
        if (id < provinces.length) {
            return;
        }
        int size = Math.max(id + 1, provinces.length * 2);
        provinces = Arrays.copyOf(provinces, size);
        owners = Arrays.copyOf(owners, size);
        shapeVersions = Arrays.copyOf(shapeVersions, size);
    }

    private static void touchShapes(char[] tile) {
        if (tile == null) {
            return;
        }
        char last = 0;
        for (char id : tile) {
            if (id != 0 && id != last) {
                bump(id);
                last = id;
            }
        }
    }

    private static void bump(int id) {
        grow(id);
        shapeVersions[id]++;
    }

    private static void publish() {
        view = new View(dimension, new Long2ObjectOpenHashMap<>(TILES), provinces.clone(), owners.clone(), version);
    }

    // ---------------------------------------------------------------- chunk by chunk, near the player

    /**
     * Tells the listeners of the chunks of a changed tile near the player whose holder changed with it.
     */
    private static void tellNear(long key, char[] old, char[] tile) {
        int[] near = near();
        if (near == null) {
            return;
        }
        int baseX = ProvinceMap.tileX(key) * ProvinceMap.TILE;
        int baseZ = ProvinceMap.tileZ(key) * ProvinceMap.TILE;
        if (baseX > near[0] + NEAR_CHUNKS || baseX + ProvinceMap.TILE <= near[0] - NEAR_CHUNKS || baseZ > near[1] + NEAR_CHUNKS
                || baseZ + ProvinceMap.TILE <= near[1] - NEAR_CHUNKS) {
            return;
        }
        for (int i = 0; i < ProvinceMap.TILE_CHUNKS; i++) {
            int x = baseX + (i & 31);
            int z = baseZ + (i >> 5);
            if (Math.abs(x - near[0]) > NEAR_CHUNKS || Math.abs(z - near[1]) > NEAR_CHUNKS) {
                continue;
            }
            UUID before = old == null || old.length == 0 ? null : owner(old[i]);
            UUID after = tile == null || tile.length == 0 ? null : owner(tile[i]);
            if (!Objects.equals(before, after)) {
                ClientTerritoryCache.provinceChunkChanged(x, z, before, after);
            }
        }
    }

    /**
     * Tells the listeners of the chunks of a province near the player that it changed hands.
     */
    private static void tellOwnerNear(int id, UUID old, UUID owner) {
        int[] near = near();
        if (near == null) {
            return;
        }
        for (int x = near[0] - NEAR_CHUNKS; x <= near[0] + NEAR_CHUNKS; x++) {
            for (int z = near[1] - NEAR_CHUNKS; z <= near[1] + NEAR_CHUNKS; z++) {
                if (provinceAt(x, z) == id) {
                    ClientTerritoryCache.provinceChunkChanged(x, z, old, owner);
                }
            }
        }
    }

    private static int[] near() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || !ClientTerritoryCache.hasListeners()) {
            return null;
        }
        return new int[]{minecraft.player.chunkPosition().x, minecraft.player.chunkPosition().z};
    }
}
