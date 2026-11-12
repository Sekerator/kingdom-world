package io.github.sekerator.kingdomworld.core.territory;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Who owns the land, indexed by dimension and chunk so that "who owns this chunk" is a constant time lookup; block protection
 * asks it on every block change in the world.
 *
 * <p>Land is held two ways. Claims are single chunks of a kingdom ({@link Territory}): the land of players, the capitals of
 * the kingdoms of the world, old land. Provinces ({@link ProvinceMap}, in one dimension) are large areas a kingdom holds as a
 * whole. A claim always wins over a province: the owner of a chunk is the kingdom that claimed it, otherwise the holder of its
 * province.
 *
 * <p>The mutating methods only keep the index consistent; the game rules for claiming live in {@link ClaimRules}. Listeners
 * are told after every change of the claim of a chunk; province listeners once for a province that changes hands, and chunk
 * listeners of other mods learn of the chunks of such a province a few thousand a tick through
 * {@link #drainProvinceChunks(int)}. Not thread safe.
 */
public final class TerritoryIndex {
    private final Map<String, Long2ObjectOpenHashMap<UUID>> owners = new HashMap<>();
    private final Map<UUID, Territory> territories = new LinkedHashMap<>();
    private final List<TerritoryListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ProvinceListener> provinceListeners = new CopyOnWriteArrayList<>();
    private final List<TerritoryListener> chunkListeners = new CopyOnWriteArrayList<>();
    private final Deque<Sweep> sweeps = new ArrayDeque<>();
    private ProvinceMap provinces;
    private long version;
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Listens to changes of claims.
     */
    public void addListener(TerritoryListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(TerritoryListener listener) {
        listeners.remove(listener);
        chunkListeners.remove(listener);
    }

    /**
     * Listens to provinces changing hands, one event a province.
     */
    public void addProvinceListener(ProvinceListener listener) {
        provinceListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeProvinceListener(ProvinceListener listener) {
        provinceListeners.remove(listener);
    }

    /**
     * Listens to the chunks of provinces changing hands, chunk by chunk, as {@link #drainProvinceChunks(int)} hands them out.
     */
    public void addProvinceChunkListener(TerritoryListener listener) {
        chunkListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Puts provinces under the index, or takes them away with null. Nobody is told: the land was there already.
     */
    public void attachProvinces(ProvinceMap map) {
        provinces = map;
        sweeps.clear();
        version++;
    }

    /**
     * Grows whenever a claim is made, given up or moved, a kingdom loses its land, or a province changes hands, is filled or
     * cleared through the index: what is sorted by the owners of chunks compares it. Changes made on a {@link ProvinceMap}
     * directly show in its own {@link ProvinceMap#version()}.
     */
    public long version() {
        return version;
    }

    public Optional<ProvinceMap> provinceMap() {
        return Optional.ofNullable(provinces);
    }

    // ---------------------------------------------------------------- queries

    /**
     * @return the kingdom owning the chunk by a claim or by its province, or {@code null} for land of nobody
     */
    public UUID ownerOf(String dimension, long chunk) {
        Long2ObjectOpenHashMap<UUID> map = owners.get(dimension);
        if (map != null) {
            UUID owner = map.get(chunk);
            if (owner != null) {
                return owner;
            }
        }
        ProvinceMap map2 = provinces;
        return map2 != null && map2.dimension().equals(dimension) ? map2.ownerAt(chunk) : null;
    }

    /**
     * @return the kingdom that claimed the chunk, provinces aside, or {@code null}
     */
    public UUID claimOwner(String dimension, long chunk) {
        Long2ObjectOpenHashMap<UUID> map = owners.get(dimension);
        return map == null ? null : map.get(chunk);
    }

    /**
     * The province of a chunk, or 0.
     */
    public int provinceAt(String dimension, long chunk) {
        ProvinceMap map = provinces;
        return map != null && map.dimension().equals(dimension) ? map.provinceAt(chunk) : 0;
    }

    /**
     * The provinces a kingdom holds, by number.
     */
    public List<Integer> provincesOf(UUID kingdomId) {
        return provinces == null ? List.of() : provinces.provincesOf(kingdomId);
    }

    /**
     * The land of a kingdom in chunks: its claims and the chunks of its provinces, a claim inside its own province counted
     * once.
     */
    public long landSize(UUID kingdomId) {
        Territory territory = territories.get(kingdomId);
        long size = territory == null ? 0 : territory.size();
        if (provinces == null) {
            return size;
        }
        for (int id : provinces.provincesOf(kingdomId)) {
            size += provinces.chunks(id);
        }
        if (territory != null && provinces.dimension().equals(territory.dimension())) {
            for (long chunk : territory.chunks) {
                if (kingdomId.equals(provinces.ownerAt(chunk))) {
                    size--;
                }
            }
        }
        return size;
    }

    /**
     * Whether any land of a kingdom, claimed or held as a province, lies in a dimension.
     */
    public boolean hasLand(String dimension) {
        Long2ObjectOpenHashMap<UUID> map = owners.get(dimension);
        if (map != null && !map.isEmpty()) {
            return true;
        }
        ProvinceMap map2 = provinces;
        return map2 != null && map2.anyOwned() && map2.dimension().equals(dimension);
    }

    public Optional<Territory> territory(UUID kingdomId) {
        return Optional.ofNullable(territories.get(kingdomId));
    }

    public Collection<Territory> territories() {
        return Collections.unmodifiableCollection(territories.values());
    }

    /**
     * Every claimed chunk of a dimension with its owner; provinces are not in it.
     */
    public Map<Long, UUID> claimsIn(String dimension) {
        Map<Long, UUID> map = owners.get(dimension);
        return map == null ? Map.of() : Collections.unmodifiableMap(map);
    }

    public Set<String> dimensions() {
        return Collections.unmodifiableSet(owners.keySet());
    }

    /**
     * Whether a chunk owned by a kingdom other than {@code self}, by a claim or a province, lies within {@code radius} chunks
     * (square distance) of {@code center}.
     *
     * @param self the kingdom whose own land does not count, or {@code null} to count every kingdom
     */
    public boolean hasForeignLandWithin(String dimension, long center, int radius, UUID self) {
        if (radius < 0 || !hasLand(dimension)) {
            return false;
        }
        int cx = ChunkKey.x(center);
        int cz = ChunkKey.z(center);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                UUID owner = ownerOf(dimension, ChunkKey.of(cx + dx, cz + dz));
                if (owner != null && !owner.equals(self)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the chunk shares a side with land of the kingdom: a claim of it or a chunk of its province.
     */
    public boolean touchesTerritory(UUID kingdomId, long chunk) {
        Territory territory = territories.get(kingdomId);
        if (territory == null) {
            return false;
        }
        for (long side : ChunkKey.sides(chunk)) {
            if (territory.chunks.contains(side) || kingdomId.equals(ownerOf(territory.dimension(), side))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every remaining claim of the kingdom would still be joined to its land, moving between chunks that share a side,
     * if {@code removed} were given up: to the capital chunk through other claims, or to a province of its own. A chunk given
     * up inside its own province stays its land and joins what touches it.
     */
    public boolean staysConnectedWithout(UUID kingdomId, long removed) {
        Territory territory = territories.get(kingdomId);
        if (territory == null) {
            return true;
        }
        long start = territory.capital().chunk();
        if (start == removed || !territory.chunks.contains(start)) {
            return false;
        }
        String dimension = territory.dimension();
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        if (provinces != null && provinces.dimension().equals(dimension) && !provinces.provincesOf(kingdomId).isEmpty()) {
            for (long chunk : territory.chunks) {
                if (chunk == removed || visited.contains(chunk)) {
                    continue;
                }
                for (long side : ChunkKey.sides(chunk)) {
                    if (ownProvinceLand(kingdomId, dimension, side, removed)) {
                        visited.add(chunk);
                        queue.add(chunk);
                        break;
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            long current = queue.poll();
            for (long side : ChunkKey.sides(current)) {
                if (side != removed && territory.chunks.contains(side) && visited.add(side)) {
                    queue.add(side);
                }
            }
        }
        int remaining = territory.chunks.size() - (territory.chunks.contains(removed) ? 1 : 0);
        return visited.size() == remaining;
    }

    private boolean ownProvinceLand(UUID kingdomId, String dimension, long chunk, long removed) {
        if (chunk != removed && claimOwner(dimension, chunk) != null) {
            return false;
        }
        return kingdomId.equals(provinces.ownerAt(chunk));
    }

    // ---------------------------------------------------------------- changes

    /**
     * Gives a kingdom its capital and first chunks. The capital chunk is always included.
     *
     * @throws IllegalArgumentException if the kingdom has land already or a chunk is claimed
     */
    public Territory establish(UUID kingdomId, Capital capital, Collection<Long> chunks) {
        Objects.requireNonNull(kingdomId, "kingdomId");
        if (territories.containsKey(kingdomId)) {
            throw new IllegalArgumentException("Kingdom " + kingdomId + " already has territory");
        }
        Set<Long> all = new LinkedHashSet<>();
        all.add(capital.chunk());
        all.addAll(chunks);
        Long2ObjectOpenHashMap<UUID> map = owners.computeIfAbsent(capital.dimension(), d -> new Long2ObjectOpenHashMap<>());
        for (long chunk : all) {
            if (map.containsKey(chunk)) {
                throw new IllegalArgumentException("Chunk " + ChunkKey.format(chunk) + " in " + capital.dimension()
                        + " is already owned by " + map.get(chunk));
            }
        }
        Territory territory = new Territory(kingdomId, capital);
        territories.put(kingdomId, territory);
        version++;
        for (long chunk : all) {
            territory.chunks.add(chunk);
            map.put(chunk, kingdomId);
        }
        changeListener.run();
        for (long chunk : all) {
            notifyListeners(capital.dimension(), chunk, null, kingdomId);
        }
        return territory;
    }

    /**
     * Adds an unclaimed chunk in the capital's dimension to a kingdom's claims.
     *
     * @throws IllegalArgumentException if the kingdom has no land or the chunk is claimed
     */
    public void assign(UUID kingdomId, long chunk) {
        Territory territory = territories.get(kingdomId);
        if (territory == null) {
            throw new IllegalArgumentException("Kingdom " + kingdomId + " has no territory");
        }
        Long2ObjectOpenHashMap<UUID> map = owners.computeIfAbsent(territory.dimension(), d -> new Long2ObjectOpenHashMap<>());
        if (map.containsKey(chunk)) {
            throw new IllegalArgumentException("Chunk " + ChunkKey.format(chunk) + " is already owned");
        }
        map.put(chunk, kingdomId);
        territory.chunks.add(chunk);
        version++;
        changeListener.run();
        notifyListeners(territory.dimension(), chunk, null, kingdomId);
    }

    /**
     * Gives up a claimed chunk of a kingdom other than its capital chunk.
     *
     * @throws IllegalArgumentException if the kingdom does not own the chunk or it is the capital chunk
     */
    public void release(UUID kingdomId, long chunk) {
        Territory territory = territories.get(kingdomId);
        if (territory == null || !territory.chunks.contains(chunk)) {
            throw new IllegalArgumentException("Kingdom " + kingdomId + " does not own chunk " + ChunkKey.format(chunk));
        }
        if (territory.capital().chunk() == chunk) {
            throw new IllegalArgumentException("The capital chunk cannot be released");
        }
        territory.chunks.remove(chunk);
        removeOwner(territory.dimension(), chunk);
        version++;
        changeListener.run();
        notifyListeners(territory.dimension(), chunk, kingdomId, null);
    }

    /**
     * Removes all land of a kingdom: its claims, and its provinces become nobody's.
     *
     * @return the chunks it claimed
     */
    public Set<Long> remove(UUID kingdomId) {
        Territory territory = territories.remove(kingdomId);
        Set<Long> claimed = Set.of();
        if (territory != null) {
            for (long chunk : territory.chunks) {
                removeOwner(territory.dimension(), chunk);
            }
            version++;
            changeListener.run();
            for (long chunk : territory.chunks) {
                notifyListeners(territory.dimension(), chunk, kingdomId, null);
            }
            claimed = Collections.unmodifiableSet(territory.chunks);
        }
        for (int id : provincesOf(kingdomId)) {
            transferProvince(id, null, provinces.acquiredDay(id));
        }
        return claimed;
    }

    /**
     * Hands a province to a kingdom, or to nobody with null: one event for the province, and its chunks to the chunk
     * listeners as {@link #drainProvinceChunks(int)} goes on.
     *
     * @param day the day it changed hands
     * @return whether the holder changed
     * @throws IllegalArgumentException if there is no such province
     */
    public boolean transferProvince(int id, UUID owner, long day) {
        ProvinceMap map = provinces;
        if (map == null || !map.exists(id)) {
            throw new IllegalArgumentException("No province " + id);
        }
        UUID old = map.owner(id);
        if (Objects.equals(old, owner)) {
            return false;
        }
        map.setOwner(id, owner, day);
        version++;
        for (ProvinceListener listener : provinceListeners) {
            listener.onProvinceChanged(map.dimension(), id, old, owner);
        }
        if (!chunkListeners.isEmpty()) {
            sweeps.add(new Sweep(map, id, old, owner, map.tileKeysOf(id)));
        }
        return true;
    }

    /**
     * Puts a rectangle of chunks into a province (or out of every province with 0) and tells the chunk listeners of every chunk
     * whose owner changed, at once: for operators and tests, whose rectangles are small.
     *
     * @return the chunks that changed province
     * @throws IllegalArgumentException if there are no provinces or no such province
     */
    public int fillProvince(int id, int x1, int z1, int x2, int z2) {
        ProvinceMap map = provinces;
        if (map == null || (id != 0 && !map.exists(id))) {
            throw new IllegalArgumentException("No province " + id);
        }
        int minX = Math.min(x1, x2);
        int minZ = Math.min(z1, z2);
        int width = Math.abs(x2 - x1) + 1;
        int height = Math.abs(z2 - z1) + 1;
        version++;
        if (chunkListeners.isEmpty()) {
            return map.fill(id, x1, z1, x2, z2);
        }
        String dimension = map.dimension();
        UUID[] before = new UUID[width * height];
        for (int dz = 0; dz < height; dz++) {
            for (int dx = 0; dx < width; dx++) {
                before[dz * width + dx] = ownerOf(dimension, ChunkKey.of(minX + dx, minZ + dz));
            }
        }
        int changed = map.fill(id, x1, z1, x2, z2);
        for (int dz = 0; dz < height; dz++) {
            for (int dx = 0; dx < width; dx++) {
                long chunk = ChunkKey.of(minX + dx, minZ + dz);
                UUID after = ownerOf(dimension, chunk);
                UUID old = before[dz * width + dx];
                if (!Objects.equals(old, after)) {
                    for (TerritoryListener listener : chunkListeners) {
                        listener.onChunkChanged(dimension, chunk, old, after);
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Gives up a province and forgets it: its holder lets it go (one event), the chunk listeners hear of its chunks at once, and
     * its chunks lie in no province.
     *
     * @return the chunks it had
     */
    public int clearProvince(int id) {
        ProvinceMap map = provinces;
        if (map == null || !map.exists(id)) {
            throw new IllegalArgumentException("No province " + id);
        }
        UUID old = map.owner(id);
        version++;
        if (old != null) {
            map.setOwner(id, null, map.acquiredDay(id));
            for (ProvinceListener listener : provinceListeners) {
                listener.onProvinceChanged(map.dimension(), id, old, null);
            }
            if (!chunkListeners.isEmpty()) {
                for (long key : map.tileKeysOf(id)) {
                    char[] tile = map.tileUnsafe(key);
                    for (int cell = 0; cell < ProvinceMap.TILE_CHUNKS; cell++) {
                        if (tile[cell] != id) {
                            continue;
                        }
                        long chunk = ChunkKey.of(ProvinceMap.tileX(key) * ProvinceMap.TILE + (cell & (ProvinceMap.TILE - 1)),
                                ProvinceMap.tileZ(key) * ProvinceMap.TILE + (cell >> 5));
                        if (claimOwner(map.dimension(), chunk) == null) {
                            for (TerritoryListener listener : chunkListeners) {
                                listener.onChunkChanged(map.dimension(), chunk, old, null);
                            }
                        }
                    }
                }
            }
        }
        return map.clear(id);
    }

    /**
     * Chunks of provinces that changed hands still to be told to the chunk listeners.
     */
    public boolean provinceChunksPending() {
        return !sweeps.isEmpty();
    }

    /**
     * Provinces that changed hands whose chunks are still being told.
     */
    public int provincesPending() {
        return sweeps.size();
    }

    /**
     * Tells the chunk listeners of up to {@code budget} chunks of provinces that changed hands, in the order the provinces
     * changed hands. A chunk under a claim is skipped: its owner did not change.
     *
     * @return the chunks told
     */
    public int drainProvinceChunks(int budget) {
        int told = 0;
        while (told < budget && !sweeps.isEmpty()) {
            Sweep sweep = sweeps.peek();
            if (sweep.map != provinces) {
                sweeps.poll();
                continue;
            }
            while (told < budget && sweep.tile < sweep.tiles.length) {
                long key = sweep.tiles[sweep.tile];
                char[] tile = sweep.map.tileUnsafe(key);
                if (tile == null) {
                    sweep.tile++;
                    sweep.cell = 0;
                    continue;
                }
                int baseX = ProvinceMap.tileX(key) * ProvinceMap.TILE;
                int baseZ = ProvinceMap.tileZ(key) * ProvinceMap.TILE;
                while (told < budget && sweep.cell < ProvinceMap.TILE_CHUNKS) {
                    int cell = sweep.cell++;
                    if (tile[cell] != sweep.id) {
                        continue;
                    }
                    long chunk = ChunkKey.of(baseX + (cell & (ProvinceMap.TILE - 1)), baseZ + (cell >> 5));
                    if (claimOwner(sweep.map.dimension(), chunk) != null) {
                        continue;
                    }
                    for (TerritoryListener listener : chunkListeners) {
                        listener.onChunkChanged(sweep.map.dimension(), chunk, sweep.oldOwner, sweep.newOwner);
                    }
                    told++;
                }
                if (sweep.cell >= ProvinceMap.TILE_CHUNKS) {
                    sweep.tile++;
                    sweep.cell = 0;
                }
            }
            if (sweep.tile >= sweep.tiles.length) {
                sweeps.poll();
            }
        }
        return told;
    }

    private void removeOwner(String dimension, long chunk) {
        Long2ObjectOpenHashMap<UUID> map = owners.get(dimension);
        if (map != null) {
            map.remove(chunk);
            if (map.isEmpty()) {
                owners.remove(dimension);
            }
        }
    }

    private void notifyListeners(String dimension, long chunk, UUID oldOwner, UUID newOwner) {
        for (TerritoryListener listener : listeners) {
            listener.onChunkChanged(dimension, chunk, oldOwner, newOwner);
        }
    }

    /**
     * Where the telling of the chunks of a province that changed hands has got to.
     */
    private static final class Sweep {
        private final ProvinceMap map;
        private final int id;
        private final UUID oldOwner;
        private final UUID newOwner;
        private final long[] tiles;
        private int tile;
        private int cell;

        Sweep(ProvinceMap map, int id, UUID oldOwner, UUID newOwner, long[] tiles) {
            this.map = map;
            this.id = id;
            this.oldOwner = oldOwner;
            this.newOwner = newOwner;
            this.tiles = tiles;
        }
    }
}
