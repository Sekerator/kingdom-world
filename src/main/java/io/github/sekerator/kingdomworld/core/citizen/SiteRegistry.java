package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Places in kingdom land that citizens use (beds, workplaces, infirmaries), found by scanning the world. The
 * registry is kept with the saved data, so that kingdoms far from players still know their beds.
 *
 * <p>Occupants are kept consistent with the citizens' records by
 * {@link io.github.sekerator.kingdomworld.core.state.KingdomWorldState}. Not thread safe.
 */
public final class SiteRegistry {
    private final Map<WorldPos, Site> byPos = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Set<WorldPos>>> byKingdom = new LinkedHashMap<>();
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    // ---------------------------------------------------------------- queries

    public Optional<Site> get(WorldPos pos) {
        return Optional.ofNullable(byPos.get(pos));
    }

    /**
     * Places of one kind of a kingdom, in the order they were found.
     */
    public List<Site> sitesOf(UUID kingdomId, String kind) {
        Set<WorldPos> positions = positions(kingdomId, kind);
        List<Site> result = new ArrayList<>(positions.size());
        for (WorldPos pos : positions) {
            result.add(byPos.get(pos));
        }
        return result;
    }

    /**
     * Every place of a kingdom.
     */
    public List<Site> sitesOf(UUID kingdomId) {
        List<Site> result = new ArrayList<>();
        Map<String, Set<WorldPos>> kinds = byKingdom.get(kingdomId);
        if (kinds != null) {
            for (Set<WorldPos> positions : kinds.values()) {
                for (WorldPos pos : positions) {
                    result.add(byPos.get(pos));
                }
            }
        }
        return result;
    }

    public int count(UUID kingdomId, String kind) {
        return positions(kingdomId, kind).size();
    }

    public int occupied(UUID kingdomId, String kind) {
        int count = 0;
        for (WorldPos pos : positions(kingdomId, kind)) {
            if (!byPos.get(pos).isFree()) {
                count++;
            }
        }
        return count;
    }

    /**
     * The free place of a kind nearest to {@code near}; any free place if {@code near} is {@code null}.
     */
    public Optional<Site> nearestFree(UUID kingdomId, String kind, WorldPos near) {
        Site best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (WorldPos pos : positions(kingdomId, kind)) {
            Site site = byPos.get(pos);
            if (!site.isFree()) {
                continue;
            }
            double distance = near == null ? 0.0 : near.distanceSqr(pos);
            if (best == null || distance < bestDistance) {
                best = site;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Places in one chunk of a dimension, of any kingdom.
     */
    public List<Site> sitesInChunk(String dimension, long chunk) {
        List<Site> result = new ArrayList<>();
        for (Site site : byPos.values()) {
            if (site.pos().chunk() == chunk && site.pos().dimension().equals(dimension)) {
                result.add(site);
            }
        }
        return result;
    }

    public int size() {
        return byPos.size();
    }

    private Set<WorldPos> positions(UUID kingdomId, String kind) {
        Map<String, Set<WorldPos>> kinds = byKingdom.get(kingdomId);
        if (kinds == null) {
            return Set.of();
        }
        return kinds.getOrDefault(kind, Set.of());
    }

    // ---------------------------------------------------------------- changes (occupants are handled by the state)

    /**
     * Records a place. A place already known at the position with the same kind and kingdom stays unchanged.
     *
     * @return the place that was replaced (another kind or kingdom), whose occupant must be released
     */
    Optional<Site> put(WorldPos pos, String kind, UUID kingdomId) {
        Site existing = byPos.get(pos);
        if (existing != null && existing.kind().equals(kind) && existing.kingdomId().equals(kingdomId)) {
            return Optional.empty();
        }
        if (existing != null) {
            unindex(existing);
        }
        Site site = new Site(pos, kind, kingdomId, null);
        byPos.put(pos, site);
        byKingdom.computeIfAbsent(kingdomId, k -> new LinkedHashMap<>())
                .computeIfAbsent(kind, k -> new LinkedHashSet<>()).add(pos);
        changeListener.run();
        return Optional.ofNullable(existing);
    }

    Optional<Site> remove(WorldPos pos) {
        Site site = byPos.remove(pos);
        if (site == null) {
            return Optional.empty();
        }
        unindex(site);
        changeListener.run();
        return Optional.of(site);
    }

    void setOccupant(WorldPos pos, UUID citizen) {
        Site site = byPos.get(pos);
        if (site != null && !Objects.equals(site.occupant(), citizen)) {
            byPos.put(pos, site.withOccupant(citizen));
            changeListener.run();
        }
    }

    List<Site> removeKingdom(UUID kingdomId) {
        List<Site> removed = sitesOf(kingdomId);
        for (Site site : removed) {
            byPos.remove(site.pos());
        }
        if (byKingdom.remove(kingdomId) != null) {
            changeListener.run();
        }
        return removed;
    }

    /**
     * Restores a place from saved data, occupant included.
     *
     * @throws IllegalArgumentException if another place is known at the position
     */
    void restore(Site site) {
        if (byPos.containsKey(site.pos())) {
            throw new IllegalArgumentException("Two places at " + site.pos());
        }
        byPos.put(site.pos(), site);
        byKingdom.computeIfAbsent(site.kingdomId(), k -> new LinkedHashMap<>())
                .computeIfAbsent(site.kind(), k -> new LinkedHashSet<>()).add(site.pos());
    }

    private void unindex(Site site) {
        Map<String, Set<WorldPos>> kinds = byKingdom.get(site.kingdomId());
        if (kinds == null) {
            return;
        }
        Set<WorldPos> positions = kinds.get(site.kind());
        if (positions != null) {
            positions.remove(site.pos());
            if (positions.isEmpty()) {
                kinds.remove(site.kind());
            }
        }
        if (kinds.isEmpty()) {
            byKingdom.remove(site.kingdomId());
        }
    }
}
