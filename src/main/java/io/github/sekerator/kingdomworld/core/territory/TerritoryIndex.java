package io.github.sekerator.kingdomworld.core.territory;

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
 * Chunk ownership of all kingdoms, indexed by dimension and chunk so that "who owns this chunk" is a constant
 * time lookup; block protection asks it on every block change in the world.
 *
 * <p>The mutating methods only keep the index consistent; the game rules for claiming live in
 * {@link ClaimRules}. Listeners are notified after every change of a chunk's owner. Not thread safe.
 */
public final class TerritoryIndex {
    private final Map<String, Map<Long, UUID>> owners = new HashMap<>();
    private final Map<UUID, Territory> territories = new LinkedHashMap<>();
    private final List<TerritoryListener> listeners = new CopyOnWriteArrayList<>();
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    public void addListener(TerritoryListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(TerritoryListener listener) {
        listeners.remove(listener);
    }

    // ---------------------------------------------------------------- queries

    /**
     * @return the kingdom owning the chunk, or {@code null} for unclaimed land
     */
    public UUID ownerOf(String dimension, long chunk) {
        Map<Long, UUID> map = owners.get(dimension);
        return map == null ? null : map.get(chunk);
    }

    public Optional<Territory> territory(UUID kingdomId) {
        return Optional.ofNullable(territories.get(kingdomId));
    }

    public Collection<Territory> territories() {
        return Collections.unmodifiableCollection(territories.values());
    }

    /**
     * Every claimed chunk of a dimension with its owner.
     */
    public Map<Long, UUID> claimsIn(String dimension) {
        return Collections.unmodifiableMap(owners.getOrDefault(dimension, Map.of()));
    }

    public Set<String> dimensions() {
        return Collections.unmodifiableSet(owners.keySet());
    }

    /**
     * Whether a chunk owned by a kingdom other than {@code self} lies within {@code radius} chunks (square
     * distance) of {@code center}.
     *
     * @param self the kingdom whose own land does not count, or {@code null} to count every kingdom
     */
    public boolean hasForeignLandWithin(String dimension, long center, int radius, UUID self) {
        if (radius < 0) {
            return false;
        }
        Map<Long, UUID> map = owners.get(dimension);
        if (map == null || map.isEmpty()) {
            return false;
        }
        long area = (2L * radius + 1) * (2L * radius + 1);
        if (map.size() < area) {
            for (Map.Entry<Long, UUID> entry : map.entrySet()) {
                if (!entry.getValue().equals(self) && ChunkKey.distance(entry.getKey(), center) <= radius) {
                    return true;
                }
            }
            return false;
        }
        int cx = ChunkKey.x(center);
        int cz = ChunkKey.z(center);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                UUID owner = map.get(ChunkKey.of(cx + dx, cz + dz));
                if (owner != null && !owner.equals(self)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the chunk shares a side with land of the kingdom.
     */
    public boolean touchesTerritory(UUID kingdomId, long chunk) {
        Territory territory = territories.get(kingdomId);
        if (territory == null) {
            return false;
        }
        for (long side : ChunkKey.sides(chunk)) {
            if (territory.chunks.contains(side)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every remaining chunk of the kingdom would still be reachable from the capital chunk, moving
     * between chunks that share a side, if {@code removed} were given up.
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
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
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

    // ---------------------------------------------------------------- changes

    /**
     * Gives a kingdom its capital and first chunks. The capital chunk is always included.
     *
     * @throws IllegalArgumentException if the kingdom has land already or a chunk is owned
     */
    public Territory establish(UUID kingdomId, Capital capital, Collection<Long> chunks) {
        Objects.requireNonNull(kingdomId, "kingdomId");
        if (territories.containsKey(kingdomId)) {
            throw new IllegalArgumentException("Kingdom " + kingdomId + " already has territory");
        }
        Set<Long> all = new LinkedHashSet<>();
        all.add(capital.chunk());
        all.addAll(chunks);
        Map<Long, UUID> map = owners.computeIfAbsent(capital.dimension(), d -> new HashMap<>());
        for (long chunk : all) {
            if (map.containsKey(chunk)) {
                throw new IllegalArgumentException("Chunk " + ChunkKey.format(chunk) + " in " + capital.dimension()
                        + " is already owned by " + map.get(chunk));
            }
        }
        Territory territory = new Territory(kingdomId, capital);
        territories.put(kingdomId, territory);
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
     * Adds an unowned chunk in the capital's dimension to a kingdom's land.
     *
     * @throws IllegalArgumentException if the kingdom has no land or the chunk is owned
     */
    public void assign(UUID kingdomId, long chunk) {
        Territory territory = territories.get(kingdomId);
        if (territory == null) {
            throw new IllegalArgumentException("Kingdom " + kingdomId + " has no territory");
        }
        Map<Long, UUID> map = owners.computeIfAbsent(territory.dimension(), d -> new HashMap<>());
        if (map.containsKey(chunk)) {
            throw new IllegalArgumentException("Chunk " + ChunkKey.format(chunk) + " is already owned");
        }
        map.put(chunk, kingdomId);
        territory.chunks.add(chunk);
        changeListener.run();
        notifyListeners(territory.dimension(), chunk, null, kingdomId);
    }

    /**
     * Gives up a chunk of a kingdom other than its capital chunk.
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
        changeListener.run();
        notifyListeners(territory.dimension(), chunk, kingdomId, null);
    }

    /**
     * Removes all land of a kingdom.
     *
     * @return the chunks it owned
     */
    public Set<Long> remove(UUID kingdomId) {
        Territory territory = territories.remove(kingdomId);
        if (territory == null) {
            return Set.of();
        }
        for (long chunk : territory.chunks) {
            removeOwner(territory.dimension(), chunk);
        }
        changeListener.run();
        for (long chunk : territory.chunks) {
            notifyListeners(territory.dimension(), chunk, kingdomId, null);
        }
        return Collections.unmodifiableSet(territory.chunks);
    }

    private void removeOwner(String dimension, long chunk) {
        Map<Long, UUID> map = owners.get(dimension);
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
}
