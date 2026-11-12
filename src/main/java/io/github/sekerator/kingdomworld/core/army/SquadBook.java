package io.github.sekerator.kingdomworld.core.army;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The squads of a world by id, in the order they were first put, with the squad of every soldier and the squads of every
 * kingdom at hand: a world with a thousand soldiers asks for the squad of each of them every second.
 */
final class SquadBook {
    private final Map<Long, ArmySquad> byId = new LinkedHashMap<>();
    private final Map<UUID, Long> byMember = new HashMap<>();
    private final Map<UUID, Set<Long>> byKingdom = new HashMap<>();

    ArmySquad get(long id) {
        return byId.get(id);
    }

    boolean containsKey(long id) {
        return byId.containsKey(id);
    }

    Collection<ArmySquad> values() {
        return Collections.unmodifiableCollection(byId.values());
    }

    int size() {
        return byId.size();
    }

    ArmySquad put(long id, ArmySquad squad) {
        ArmySquad old = byId.put(id, squad);
        if (old != null) {
            unindexMembers(old);
            if (!old.kingdomId().equals(squad.kingdomId())) {
                unindexKingdom(old);
            }
        }
        indexMembers(squad);
        byKingdom.computeIfAbsent(squad.kingdomId(), key -> new LinkedHashSet<>()).add(id);
        return old;
    }

    ArmySquad putIfAbsent(long id, ArmySquad squad) {
        ArmySquad old = byId.get(id);
        if (old != null) {
            return old;
        }
        put(id, squad);
        return null;
    }

    ArmySquad remove(long id) {
        ArmySquad old = byId.remove(id);
        if (old != null) {
            unindexMembers(old);
            unindexKingdom(old);
        }
        return old;
    }

    void removeIf(Predicate<ArmySquad> filter) {
        for (ArmySquad squad : List.copyOf(byId.values())) {
            if (filter.test(squad)) {
                remove(squad.id());
            }
        }
    }

    void clear() {
        byId.clear();
        byMember.clear();
        byKingdom.clear();
    }

    /**
     * The squad a soldier serves in.
     */
    Optional<ArmySquad> ofMember(UUID citizenId) {
        Long id = byMember.get(citizenId);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    /**
     * The squads of a kingdom in the order of the whole book: a squad put again keeps its place in both, a squad removed and
     * put again goes to the end of both.
     */
    List<ArmySquad> ofKingdom(UUID kingdomId) {
        Set<Long> ids = byKingdom.get(kingdomId);
        if (ids == null) {
            return new ArrayList<>();
        }
        List<ArmySquad> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            result.add(byId.get(id));
        }
        return result;
    }

    private void indexMembers(ArmySquad squad) {
        for (UUID member : squad.members()) {
            byMember.put(member, squad.id());
        }
    }

    private void unindexMembers(ArmySquad squad) {
        for (UUID member : squad.members()) {
            byMember.remove(member, squad.id());
        }
    }

    private void unindexKingdom(ArmySquad squad) {
        Set<Long> ids = byKingdom.get(squad.kingdomId());
        if (ids != null) {
            ids.remove(squad.id());
            if (ids.isEmpty()) {
                byKingdom.remove(squad.kingdomId());
            }
        }
    }
}
