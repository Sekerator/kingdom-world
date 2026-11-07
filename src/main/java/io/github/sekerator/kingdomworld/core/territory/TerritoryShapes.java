package io.github.sekerator.kingdomworld.core.territory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The outlines of every kingdom in one dimension, kept up to date by chunk changes. A change only marks its kingdoms;
 * {@link #rebuild()} then outlines just the kingdoms that changed, so a map integration redraws only what moved.
 * Not thread safe: each side uses it from one thread.
 */
public final class TerritoryShapes {
    private final Map<UUID, Set<Long>> chunks = new HashMap<>();
    private final Map<UUID, Long> anchors = new HashMap<>();
    private final Map<UUID, List<ChunkOutlines.Area>> shapes = new HashMap<>();
    private final Set<UUID> dirty = new LinkedHashSet<>();

    /**
     * Starts over from all claims of the dimension; every kingdom that had or has land counts as changed.
     */
    public void reset(Map<Long, UUID> claims) {
        dirty.addAll(chunks.keySet());
        chunks.clear();
        claims.forEach((chunk, owner) -> {
            if (owner != null) {
                chunks.computeIfAbsent(owner, key -> new HashSet<>()).add(chunk);
            }
        });
        dirty.addAll(chunks.keySet());
    }

    /**
     * A chunk changed hands; either owner may be {@code null}.
     */
    public void change(long chunk, UUID oldOwner, UUID newOwner) {
        if (Objects.equals(oldOwner, newOwner)) {
            return;
        }
        if (oldOwner != null) {
            Set<Long> land = chunks.get(oldOwner);
            if (land != null && land.remove(chunk)) {
                dirty.add(oldOwner);
                if (land.isEmpty()) {
                    chunks.remove(oldOwner);
                }
            }
        }
        if (newOwner != null && chunks.computeIfAbsent(newOwner, key -> new HashSet<>()).add(chunk)) {
            dirty.add(newOwner);
        }
    }

    /**
     * The chunk the label of a kingdom should stand on, usually its capital; {@code null} lets the outline choose.
     */
    public void setAnchor(UUID kingdom, Long chunk) {
        Long old = chunk == null ? anchors.remove(kingdom) : anchors.put(kingdom, chunk);
        if (!Objects.equals(old, chunk) && chunks.containsKey(kingdom)) {
            dirty.add(kingdom);
        }
    }

    public boolean hasChanges() {
        return !dirty.isEmpty();
    }

    /**
     * Outlines the kingdoms changed since the last call.
     *
     * @return the new areas of each changed kingdom, an empty list for a kingdom that has no land left
     */
    public Map<UUID, List<ChunkOutlines.Area>> rebuild() {
        Map<UUID, List<ChunkOutlines.Area>> changed = new LinkedHashMap<>();
        for (UUID kingdom : dirty) {
            Set<Long> land = chunks.get(kingdom);
            if (land == null || land.isEmpty()) {
                shapes.remove(kingdom);
                changed.put(kingdom, List.of());
            } else {
                List<ChunkOutlines.Area> areas = ChunkOutlines.of(land, anchors.get(kingdom));
                shapes.put(kingdom, areas);
                changed.put(kingdom, areas);
            }
        }
        dirty.clear();
        return changed;
    }

    /**
     * The areas of a kingdom as of the last {@link #rebuild()}.
     */
    public List<ChunkOutlines.Area> shape(UUID kingdom) {
        return shapes.getOrDefault(kingdom, List.of());
    }

    /**
     * All outlined kingdoms as of the last {@link #rebuild()}.
     */
    public Map<UUID, List<ChunkOutlines.Area>> shapes() {
        return Collections.unmodifiableMap(shapes);
    }

    public Set<Long> chunksOf(UUID kingdom) {
        return Collections.unmodifiableSet(chunks.getOrDefault(kingdom, Set.of()));
    }

    public void clear() {
        dirty.addAll(chunks.keySet());
        chunks.clear();
    }
}
