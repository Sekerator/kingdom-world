package io.github.sekerator.kingdomworld.core.army;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The soldiers of a world by citizen, in the order they were first put, with the soldiers of every kingdom at hand in the
 * same order.
 */
final class SoldierBook {
    private final Map<UUID, Soldier> byCitizen = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> byKingdom = new HashMap<>();

    Soldier get(UUID citizenId) {
        return byCitizen.get(citizenId);
    }

    boolean containsKey(UUID citizenId) {
        return byCitizen.containsKey(citizenId);
    }

    Collection<Soldier> values() {
        return Collections.unmodifiableCollection(byCitizen.values());
    }

    Soldier put(UUID citizenId, Soldier soldier) {
        Soldier old = byCitizen.put(citizenId, soldier);
        if (old != null && !old.kingdomId().equals(soldier.kingdomId())) {
            unindex(old);
        }
        byKingdom.computeIfAbsent(soldier.kingdomId(), key -> new LinkedHashSet<>()).add(citizenId);
        return old;
    }

    Soldier putIfAbsent(UUID citizenId, Soldier soldier) {
        Soldier old = byCitizen.get(citizenId);
        if (old != null) {
            return old;
        }
        put(citizenId, soldier);
        return null;
    }

    Soldier remove(UUID citizenId) {
        Soldier old = byCitizen.remove(citizenId);
        if (old != null) {
            unindex(old);
        }
        return old;
    }

    void removeIf(Predicate<Soldier> filter) {
        for (Soldier soldier : List.copyOf(byCitizen.values())) {
            if (filter.test(soldier)) {
                remove(soldier.citizenId());
            }
        }
    }

    void clear() {
        byCitizen.clear();
        byKingdom.clear();
    }

    /**
     * The soldiers of a kingdom in the order of the whole book.
     */
    List<Soldier> ofKingdom(UUID kingdomId) {
        Set<UUID> ids = byKingdom.get(kingdomId);
        if (ids == null) {
            return new ArrayList<>();
        }
        List<Soldier> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            result.add(byCitizen.get(id));
        }
        return result;
    }

    private void unindex(Soldier soldier) {
        Set<UUID> ids = byKingdom.get(soldier.kingdomId());
        if (ids != null) {
            ids.remove(soldier.citizenId());
            if (ids.isEmpty()) {
                byKingdom.remove(soldier.kingdomId());
            }
        }
    }
}
