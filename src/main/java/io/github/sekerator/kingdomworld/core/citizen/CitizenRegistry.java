package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The living citizens of all kingdoms, the time of the last arrival in each kingdom and each kingdom's chronicle
 * of deaths. Dead citizens leave the population; what is remembered of them is their {@link DeathRecord}.
 *
 * <p>Places (home, workplace) are kept consistent by
 * {@link io.github.sekerator.kingdomworld.core.state.KingdomWorldState}. Every change notifies the change
 * listener. Not thread safe.
 */
public final class CitizenRegistry {
    /** Entries each chronicle keeps; older ones are forgotten. */
    public static final int CHRONICLE_SIZE = 100;
    /** Value of the last arrival time while nobody has arrived yet. */
    public static final long NEVER = Long.MIN_VALUE;

    private final Map<UUID, Citizen> byId = new LinkedHashMap<>();
    private final Map<UUID, Map<UUID, Citizen>> byKingdom = new LinkedHashMap<>();
    private final Map<UUID, Long> lastArrival = new LinkedHashMap<>();
    private final Map<UUID, Deque<DeathRecord>> chronicles = new LinkedHashMap<>();
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
        for (Citizen citizen : byId.values()) {
            citizen.setChangeListener(this::changed);
        }
    }

    private void changed() {
        changeListener.run();
    }

    // ---------------------------------------------------------------- queries

    public Optional<Citizen> get(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Living citizens of a kingdom in arrival order.
     */
    public List<Citizen> citizensOf(UUID kingdomId) {
        Map<UUID, Citizen> citizens = byKingdom.get(kingdomId);
        return citizens == null ? List.of() : List.copyOf(citizens.values());
    }

    public int count(UUID kingdomId) {
        Map<UUID, Citizen> citizens = byKingdom.get(kingdomId);
        return citizens == null ? 0 : citizens.size();
    }

    /**
     * All living citizens of all kingdoms.
     */
    public Collection<Citizen> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() {
        return byId.size();
    }

    /**
     * Citizens whose id starts with the given text (ignoring case), for operator commands that take short ids.
     */
    public List<Citizen> findByIdPrefix(String prefix) {
        String wanted = prefix.toLowerCase(java.util.Locale.ROOT);
        List<Citizen> result = new ArrayList<>();
        for (Citizen citizen : byId.values()) {
            if (citizen.id().toString().startsWith(wanted)) {
                result.add(citizen);
            }
        }
        return result;
    }

    /**
     * Game time of the last arrival in a kingdom, or {@link #NEVER}.
     */
    public long lastArrival(UUID kingdomId) {
        return lastArrival.getOrDefault(kingdomId, NEVER);
    }

    /**
     * The chronicle of a kingdom, newest entry first.
     */
    public List<DeathRecord> chronicle(UUID kingdomId) {
        Deque<DeathRecord> entries = chronicles.get(kingdomId);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    /**
     * Deaths in a kingdom at or after the given game time.
     */
    public int deathsSince(UUID kingdomId, long gameTime) {
        int count = 0;
        for (DeathRecord record : chronicle(kingdomId)) {
            if (record.gameTime() >= gameTime) {
                count++;
            }
        }
        return count;
    }

    /**
     * Kingdoms that have citizens, a chronicle or an arrival time.
     */
    public List<UUID> kingdoms() {
        java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>(byKingdom.keySet());
        ids.addAll(lastArrival.keySet());
        ids.addAll(chronicles.keySet());
        return List.copyOf(ids);
    }

    // ---------------------------------------------------------------- changes

    /**
     * Adds a living citizen without home or workplace.
     *
     * @throws IllegalArgumentException if the id is taken or the citizen is dead
     */
    public void add(Citizen citizen) {
        Objects.requireNonNull(citizen, "citizen");
        if (byId.containsKey(citizen.id())) {
            throw new IllegalArgumentException("Citizen " + citizen.id() + " exists already");
        }
        if (!citizen.isAlive()) {
            throw new IllegalArgumentException("Citizen " + citizen.id() + " is dead");
        }
        put(citizen);
        changed();
    }

    /**
     * Records that a newcomer arrived now; growth waits from this time.
     */
    public void markArrival(UUID kingdomId, long gameTime) {
        lastArrival.put(kingdomId, gameTime);
        changed();
    }

    /**
     * Removes a citizen from the population without a chronicle entry (used when a kingdom ends).
     */
    public Optional<Citizen> remove(UUID id) {
        Citizen citizen = byId.remove(id);
        if (citizen == null) {
            return Optional.empty();
        }
        Map<UUID, Citizen> citizens = byKingdom.get(citizen.kingdomId());
        if (citizens != null) {
            citizens.remove(id);
            if (citizens.isEmpty()) {
                byKingdom.remove(citizen.kingdomId());
            }
        }
        citizen.setChangeListener(() -> {
        });
        changed();
        return Optional.of(citizen);
    }

    /**
     * Marks a citizen dead, removes it from the population and writes its chronicle entry. Places are released
     * by the caller.
     */
    public Optional<DeathRecord> die(UUID id, CauseOfDeath cause, String killer, long gameTime, WorldPos place) {
        Citizen citizen = byId.get(id);
        if (citizen == null) {
            return Optional.empty();
        }
        DeathRecord record = new DeathRecord(id, citizen.nameSeed(), citizen.gender(), citizen.professionId(), cause,
                killer, gameTime, place == null ? citizen.lastPos() : place);
        citizen.markDead();
        remove(id);
        addToChronicle(citizen.kingdomId(), record);
        return Optional.of(record);
    }

    /**
     * Writes the death of somebody who was no citizen (a companion of a subject) into a kingdom's chronicle.
     */
    public void remember(UUID kingdomId, DeathRecord record) {
        addToChronicle(kingdomId, record);
    }

    private void addToChronicle(UUID kingdomId, DeathRecord record) {
        Deque<DeathRecord> entries = chronicles.computeIfAbsent(kingdomId, k -> new ArrayDeque<>());
        entries.addFirst(record);
        while (entries.size() > CHRONICLE_SIZE) {
            entries.removeLast();
        }
        changed();
    }

    /**
     * Forgets everything about a kingdom.
     *
     * @return the citizens that were removed
     */
    public List<Citizen> removeKingdom(UUID kingdomId) {
        List<Citizen> removed = new ArrayList<>(citizensOf(kingdomId));
        for (Citizen citizen : removed) {
            remove(citizen.id());
        }
        boolean changed = lastArrival.remove(kingdomId) != null;
        changed |= chronicles.remove(kingdomId) != null;
        if (changed) {
            changed();
        }
        return removed;
    }

    /**
     * Restores the population of a kingdom from saved data. Either everything is restored or nothing.
     *
     * @param chronicle entries newest first
     * @throws IllegalArgumentException if a citizen conflicts with one restored before or belongs elsewhere
     */
    public void restore(UUID kingdomId, List<Citizen> citizens, long arrival, List<DeathRecord> chronicle) {
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (Citizen citizen : citizens) {
            if (!citizen.kingdomId().equals(kingdomId)) {
                throw new IllegalArgumentException("Citizen " + citizen.id() + " belongs to another kingdom");
            }
            if (byId.containsKey(citizen.id()) || !seen.add(citizen.id())) {
                throw new IllegalArgumentException("Citizen " + citizen.id() + " listed twice");
            }
            if (!citizen.isAlive()) {
                throw new IllegalArgumentException("Citizen " + citizen.id() + " is dead");
            }
        }
        for (Citizen citizen : citizens) {
            put(citizen);
        }
        if (arrival != NEVER) {
            lastArrival.put(kingdomId, arrival);
        }
        if (!chronicle.isEmpty()) {
            Deque<DeathRecord> entries = new ArrayDeque<>();
            for (DeathRecord record : chronicle) {
                if (entries.size() < CHRONICLE_SIZE) {
                    entries.addLast(record);
                }
            }
            chronicles.put(kingdomId, entries);
        }
        changed();
    }

    private void put(Citizen citizen) {
        byId.put(citizen.id(), citizen);
        byKingdom.computeIfAbsent(citizen.kingdomId(), k -> new LinkedHashMap<>()).put(citizen.id(), citizen);
        citizen.setChangeListener(this::changed);
    }
}
