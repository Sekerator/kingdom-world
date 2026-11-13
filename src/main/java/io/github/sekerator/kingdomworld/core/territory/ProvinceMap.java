package io.github.sekerator.kingdomworld.core.territory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The provinces of one dimension: which province every chunk lies in, kept in sparse tiles of {@value #TILE} by {@value #TILE}
 * chunks (the chunks of a region file) of province numbers, 0 for none; a tile exists only where a province is. For every
 * province its description, its holder (null for nobody's, a wild province), the day it was acquired and whether it is held
 * for a buyer.
 *
 * <p>Looking up the province of a chunk is a probe of the map of tiles (the last tile is remembered) and a read of an array:
 * nothing is allocated. Holders change through {@link TerritoryIndex#transferProvince}, which tells the listeners.
 *
 * <p>Server thread only, reads included: the remembered tile is changed by every lookup, so a lookup from another thread may
 * read the tile of another chunk.
 */
public final class ProvinceMap {
    /** Chunks across a tile. */
    public static final int TILE = 32;
    /** Chunks of a tile. */
    public static final int TILE_CHUNKS = TILE * TILE;
    /** The highest number of a province. */
    public static final int MAX_ID = 0xFFFF;

    /**
     * A province held for a buyer until a day.
     */
    public record SaleHold(UUID buyer, long untilDay) {
        public SaleHold {
            Objects.requireNonNull(buyer, "buyer");
        }
    }

    private final String dimension;
    private final Long2ObjectOpenHashMap<char[]> tiles = new Long2ObjectOpenHashMap<>();
    private Province[] byId = new Province[16];
    private UUID[] ownerById = new UUID[16];
    private long[] acquired = new long[16];
    private SaleHold[] holds = new SaleHold[16];
    private int[] chunkCount = new int[16];
    private int owned;
    private long version;
    private long chunksVersion;
    private long cachedKey = Long.MIN_VALUE;
    private char[] cachedTile;
    private Runnable changeListener = () -> {
    };
    /** Tiles whose chunks changed, and provinces described or forgotten, since they were last drained: for those who send them. */
    private final LongOpenHashSet touchedTiles = new LongOpenHashSet();
    private final IntOpenHashSet touchedProvinces = new IntOpenHashSet();

    public ProvinceMap(String dimension) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * The dimension the provinces lie in.
     */
    public String dimension() {
        return dimension;
    }

    /**
     * Grows with every change of a chunk, a description or a holder.
     */
    public long version() {
        return version;
    }

    /**
     * Grows with every change of the chunks of the provinces alone: their outlines are drawn anew only then.
     */
    public long chunksVersion() {
        return chunksVersion;
    }

    // ---------------------------------------------------------------- reading

    public static long tileKey(int tileX, int tileZ) {
        return ((long) tileX << 32) | (tileZ & 0xFFFFFFFFL);
    }

    public static int tileX(long key) {
        return (int) (key >> 32);
    }

    public static int tileZ(long key) {
        return (int) key;
    }

    /**
     * The place of a chunk in its tile, as in the header of a region file.
     */
    public static int indexInTile(int chunkX, int chunkZ) {
        return ((chunkZ & (TILE - 1)) << 5) | (chunkX & (TILE - 1));
    }

    /**
     * The province of a chunk, or 0.
     */
    public int provinceAt(long chunk) {
        int x = ChunkKey.x(chunk);
        int z = ChunkKey.z(chunk);
        long key = tileKey(x >> 5, z >> 5);
        char[] tile;
        if (key == cachedKey) {
            tile = cachedTile;
        } else {
            tile = tiles.get(key);
            cachedKey = key;
            cachedTile = tile;
        }
        return tile == null ? 0 : tile[indexInTile(x, z)];
    }

    /**
     * The holder of the province of a chunk, or null for none or a wild province.
     */
    public UUID ownerAt(long chunk) {
        int id = provinceAt(chunk);
        return id == 0 ? null : ownerById[id];
    }

    public boolean exists(int id) {
        return id > 0 && id < byId.length && byId[id] != null;
    }

    public Optional<Province> province(int id) {
        return exists(id) ? Optional.of(byId[id]) : Optional.empty();
    }

    /**
     * All provinces, in the order of their numbers.
     */
    public List<Province> provinces() {
        List<Province> list = new ArrayList<>();
        for (Province province : byId) {
            if (province != null) {
                list.add(province);
            }
        }
        return list;
    }

    public int size() {
        int count = 0;
        for (Province province : byId) {
            if (province != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * The holder of a province, or null.
     */
    public UUID owner(int id) {
        return exists(id) ? ownerById[id] : null;
    }

    /**
     * The day the holder acquired a province.
     */
    public long acquiredDay(int id) {
        return exists(id) ? acquired[id] : 0;
    }

    public Optional<SaleHold> hold(int id) {
        return exists(id) ? Optional.ofNullable(holds[id]) : Optional.empty();
    }

    /**
     * The chunks of a province.
     */
    public int chunks(int id) {
        return id > 0 && id < chunkCount.length ? chunkCount[id] : 0;
    }

    /**
     * The provinces a kingdom holds, in the order of their numbers.
     */
    public List<Integer> provincesOf(UUID owner) {
        List<Integer> list = new ArrayList<>();
        if (owner == null) {
            return list;
        }
        for (int id = 1; id < byId.length; id++) {
            if (byId[id] != null && owner.equals(ownerById[id])) {
                list.add(id);
            }
        }
        return list;
    }

    /**
     * The provinces whose holder is not among the known kingdoms, in the order of their numbers. Nothing is changed.
     */
    public List<Integer> heldByUnknown(java.util.function.Predicate<UUID> known) {
        List<Integer> list = new ArrayList<>();
        for (int id = 1; id < byId.length; id++) {
            if (byId[id] != null && ownerById[id] != null && !known.test(ownerById[id])) {
                list.add(id);
            }
        }
        return list;
    }

    /**
     * Whether any province has a holder.
     */
    public boolean anyOwned() {
        return owned > 0;
    }

    public int tileCount() {
        return tiles.size();
    }

    /**
     * About how much memory the tiles take, in bytes: the arrays and the entries of the map.
     */
    public long memoryBytes() {
        return tiles.size() * (16L + 2L * TILE_CHUNKS + 8 + 8) + (long) byId.length * 64;
    }

    /**
     * The keys of all tiles, in order.
     */
    public long[] tileKeys() {
        long[] keys = tiles.keySet().toLongArray();
        Arrays.sort(keys);
        return keys;
    }

    /**
     * The keys of the tiles a province lies in, in order.
     */
    public long[] tileKeysOf(int id) {
        long[] all = tileKeys();
        int count = 0;
        for (long key : all) {
            char[] tile = tiles.get(key);
            for (char value : tile) {
                if (value == id) {
                    all[count++] = key;
                    break;
                }
            }
        }
        return Arrays.copyOf(all, count);
    }

    /**
     * A copy of a tile, or null where there is none.
     */
    public char[] tile(long key) {
        char[] tile = tiles.get(key);
        return tile == null ? null : tile.clone();
    }

    /**
     * The tile itself, for reading within the package.
     */
    char[] tileUnsafe(long key) {
        return tiles.get(key);
    }

    // ---------------------------------------------------------------- changes

    /**
     * Describes a province, or describes it anew; its holder and chunks stay.
     */
    public void define(Province province) {
        int id = province.id();
        grow(id);
        byId[id] = province;
        touchedProvinces.add(id);
        changed();
    }

    /**
     * Restores the holder of a province as it was saved, without telling anybody. For loading only: changes of a holder go
     * through {@link TerritoryIndex#transferProvince}.
     */
    public void restore(int id, UUID owner, long acquiredDay, SaleHold hold) {
        if (!exists(id)) {
            throw new IllegalArgumentException("No province " + id);
        }
        setOwner(id, owner, acquiredDay);
        holds[id] = hold;
        changed();
    }

    /**
     * Holds a province for a buyer, or lets it go with null.
     */
    public void setHold(int id, SaleHold hold) {
        if (!exists(id)) {
            throw new IllegalArgumentException("No province " + id);
        }
        holds[id] = hold;
        changed();
    }

    void setOwner(int id, UUID owner, long day) {
        if (ownerById[id] != null) {
            owned--;
        }
        ownerById[id] = owner;
        if (owner != null) {
            owned++;
        }
        acquired[id] = day;
        changed();
    }

    /**
     * Puts a chunk into a province, or out of every province with 0.
     */
    public void setChunk(long chunk, int id) {
        checkId(id);
        if (set(ChunkKey.x(chunk), ChunkKey.z(chunk), id)) {
            changed();
        }
    }

    /**
     * Puts a rectangle of chunks (corners included, in any order) into a province, or out of every province with 0.
     *
     * @return the chunks that changed
     */
    public int fill(int id, int x1, int z1, int x2, int z2) {
        checkId(id);
        int changedChunks = 0;
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                if (set(x, z, id)) {
                    changedChunks++;
                }
            }
        }
        if (changedChunks > 0) {
            changed();
        }
        return changedChunks;
    }

    /**
     * Takes every chunk out of a province and forgets it.
     *
     * @return the chunks it had
     */
    public int clear(int id) {
        if (!exists(id)) {
            return 0;
        }
        int removed = 0;
        chunksVersion++;
        for (long key : tileKeysOf(id)) {
            touchedTiles.add(key);
            char[] tile = tiles.get(key);
            for (int i = 0; i < TILE_CHUNKS; i++) {
                if (tile[i] == id) {
                    tile[i] = 0;
                    removed++;
                }
            }
            dropIfEmpty(key, tile);
        }
        chunkCount[id] = 0;
        if (ownerById[id] != null) {
            owned--;
        }
        byId[id] = null;
        ownerById[id] = null;
        acquired[id] = 0;
        holds[id] = null;
        touchedProvinces.add(id);
        cachedKey = Long.MIN_VALUE;
        cachedTile = null;
        changed();
        return removed;
    }

    /**
     * Puts a whole tile, as it was saved or planned. Numbers of provinces not described are taken as they are.
     */
    public void putTile(int tileX, int tileZ, char[] ids) {
        if (ids.length != TILE_CHUNKS) {
            throw new IllegalArgumentException("A tile has " + TILE_CHUNKS + " chunks");
        }
        long key = tileKey(tileX, tileZ);
        touchedTiles.add(key);
        chunksVersion++;
        char[] old = tiles.remove(key);
        if (old != null) {
            for (char id : old) {
                if (id != 0) {
                    chunkCount[id]--;
                }
            }
        }
        char[] copy = ids.clone();
        boolean any = false;
        for (char id : copy) {
            if (id != 0) {
                grow(id);
                chunkCount[id]++;
                any = true;
            }
        }
        if (any) {
            tiles.put(key, copy);
        }
        cachedKey = Long.MIN_VALUE;
        cachedTile = null;
        changed();
    }

    private boolean set(int x, int z, int id) {
        long key = tileKey(x >> 5, z >> 5);
        char[] tile = tiles.get(key);
        int index = indexInTile(x, z);
        int old = tile == null ? 0 : tile[index];
        if (old == id) {
            return false;
        }
        if (tile == null) {
            tile = new char[TILE_CHUNKS];
            tiles.put(key, tile);
            cachedKey = Long.MIN_VALUE;
            cachedTile = null;
        }
        tile[index] = (char) id;
        touchedTiles.add(key);
        chunksVersion++;
        if (old != 0) {
            chunkCount[old]--;
        }
        if (id != 0) {
            grow(id);
            chunkCount[id]++;
        } else {
            dropIfEmpty(key, tile);
        }
        return true;
    }

    private void dropIfEmpty(long key, char[] tile) {
        for (char value : tile) {
            if (value != 0) {
                return;
            }
        }
        tiles.remove(key);
        cachedKey = Long.MIN_VALUE;
        cachedTile = null;
    }

    private static void checkId(int id) {
        if (id < 0 || id > MAX_ID) {
            throw new IllegalArgumentException("Province number out of range: " + id);
        }
    }

    private void grow(int id) {
        if (id < byId.length) {
            return;
        }
        int size = Math.min(MAX_ID + 1, Math.max(id + 1, byId.length * 2));
        byId = Arrays.copyOf(byId, size);
        ownerById = Arrays.copyOf(ownerById, size);
        acquired = Arrays.copyOf(acquired, size);
        holds = Arrays.copyOf(holds, size);
        chunkCount = Arrays.copyOf(chunkCount, size);
    }

    private void changed() {
        version++;
        changeListener.run();
    }

    /**
     * The tiles whose chunks changed since the last call, forgotten then: for the one who sends them to players.
     */
    public long[] drainTouchedTiles() {
        long[] keys = touchedTiles.toLongArray();
        touchedTiles.clear();
        return keys;
    }

    /**
     * The provinces described anew or forgotten since the last call, forgotten then: for the one who sends them to players.
     */
    public int[] drainTouchedProvinces() {
        int[] ids = touchedProvinces.toIntArray();
        touchedProvinces.clear();
        return ids;
    }

    // ---------------------------------------------------------------- runs

    /**
     * A tile as runs of equal numbers, each {@code (id << 16) | length}, from the first chunk of the tile on.
     */
    public static int[] encode(char[] tile) {
        if (tile.length != TILE_CHUNKS) {
            throw new IllegalArgumentException("A tile has " + TILE_CHUNKS + " chunks");
        }
        int[] runs = new int[TILE_CHUNKS];
        int count = 0;
        int i = 0;
        while (i < TILE_CHUNKS) {
            char id = tile[i];
            int length = 1;
            while (i + length < TILE_CHUNKS && tile[i + length] == id) {
                length++;
            }
            runs[count++] = (id << 16) | length;
            i += length;
        }
        return Arrays.copyOf(runs, count);
    }

    /**
     * A tile from its runs.
     *
     * @throws IllegalArgumentException if the runs do not make up exactly one tile
     */
    public static char[] decode(int[] runs) {
        char[] tile = new char[TILE_CHUNKS];
        int at = 0;
        for (int run : runs) {
            int id = run >>> 16;
            int length = run & 0xFFFF;
            if (length == 0 || at + length > TILE_CHUNKS) {
                throw new IllegalArgumentException("Runs of a tile overflow it");
            }
            Arrays.fill(tile, at, at + length, (char) id);
            at += length;
        }
        if (at != TILE_CHUNKS) {
            throw new IllegalArgumentException("Runs of a tile cover " + at + " of " + TILE_CHUNKS + " chunks");
        }
        return tile;
    }
}
