package io.github.sekerator.kingdomworld.core.life;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What the life of cities keeps apart from the kingdoms: who is ill, who spent an evening in a tavern, the festivals and
 * fairs of each kingdom and their chronicle, rooms players rent in taverns and the camps players bought marked on their atlas.
 * Every change notifies the listener, so that the data is saved.
 */
public final class LifeState {
    /** Entries the chronicle of festivals of a kingdom keeps. */
    public static final int EVENTS = 50;
    /** Most camps one player keeps marked. */
    public static final int MAX_MARKS = 64;

    /**
     * An ill citizen.
     *
     * @param since    game time it fell ill
     * @param progress how much of the way to health it made, from 0 to 1
     */
    public record Sick(long since, double progress) {
    }

    /**
     * A festival, a fair or the day of the founding, for the chronicle of a kingdom.
     *
     * @param kind  {@code festival}, {@code founding} or {@code fair}
     * @param place the name of the city where it was, empty for the capital
     */
    public record Event(String kind, long day, String place) {
        public Event {
            Objects.requireNonNull(kind, "kind");
            place = place == null ? "" : place;
        }
    }

    /**
     * The festivals of a kingdom.
     *
     * @param called       the day of the last festival the king called, or {@link Long#MIN_VALUE}
     * @param festivalDay  the day of the last or next festival, or {@link Long#MIN_VALUE}
     * @param treated      whether its treat was served
     * @param foundingYear the last year whose day of founding was held
     */
    public record Festival(long called, long festivalDay, boolean treated, long foundingYear) {
        public static final Festival NONE = new Festival(Long.MIN_VALUE, Long.MIN_VALUE, false, 0);
    }

    /**
     * A room a player rents in a tavern: the bed is the player's point of return until {@code until}; then the point the
     * player had before comes back.
     *
     * @param previous the point of return before, or {@code null} if there was none
     */
    public record Room(UUID kingdomId, WorldPos bed, long until, WorldPos previous, float previousAngle, boolean previousForced) {
        public Room {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(bed, "bed");
        }
    }

    /**
     * Goods merchants of another kingdom put up on the market of a fair; unsold, they go home after the fair.
     */
    public record FairLot(long lotId, UUID host, UUID visitor, long day) {
    }

    private final Map<UUID, Sick> sick = new LinkedHashMap<>();
    private final Map<UUID, Long> tavern = new LinkedHashMap<>();
    private final Map<UUID, Festival> festivals = new LinkedHashMap<>();
    private final Map<UUID, Deque<Event>> events = new LinkedHashMap<>();
    private final Map<String, Long> fairs = new LinkedHashMap<>();
    private final Map<Long, FairLot> fairLots = new LinkedHashMap<>();
    private final Map<UUID, Room> rooms = new LinkedHashMap<>();
    private final Map<UUID, Set<Long>> marks = new LinkedHashMap<>();
    /** The bed each player sleeps in as their point of return: nobody's home. */
    private final Map<UUID, WorldPos> playerBeds = new LinkedHashMap<>();
    private Runnable listener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    private void changed() {
        listener.run();
    }

    // ---------------------------------------------------------------- illness

    public Optional<Sick> sick(UUID citizen) {
        return Optional.ofNullable(sick.get(citizen));
    }

    public boolean isSick(UUID citizen) {
        return sick.containsKey(citizen);
    }

    public Map<UUID, Sick> allSick() {
        return java.util.Collections.unmodifiableMap(sick);
    }

    public void fallIll(UUID citizen, long now) {
        if (sick.putIfAbsent(citizen, new Sick(now, 0.0)) == null) {
            changed();
        }
    }

    /**
     * Moves an ill citizen towards health.
     *
     * @return whether it is well again
     */
    public boolean recover(UUID citizen, double amount) {
        Sick current = sick.get(citizen);
        if (current == null) {
            return true;
        }
        double progress = current.progress() + Math.max(0, amount);
        if (progress >= 1.0) {
            sick.remove(citizen);
            changed();
            return true;
        }
        if (amount > 0) {
            sick.put(citizen, new Sick(current.since(), progress));
            changed();
        }
        return false;
    }

    public void heal(UUID citizen) {
        if (sick.remove(citizen) != null) {
            changed();
        }
    }

    // ---------------------------------------------------------------- the tavern

    /**
     * The last day the citizen spent an evening in a tavern with food and drink, or {@link Long#MIN_VALUE}.
     */
    public long tavernDay(UUID citizen) {
        return tavern.getOrDefault(citizen, Long.MIN_VALUE);
    }

    public void visitedTavern(UUID citizen, long day) {
        Long before = tavern.put(citizen, day);
        if (before == null || before != day) {
            changed();
        }
    }

    /**
     * Mood the tavern still gives a citizen today.
     */
    public int tavernMood(UUID citizen, long today, LifeRules rules) {
        long day = tavernDay(citizen);
        return day != Long.MIN_VALUE && today >= day && today - day < rules.tavernMoodDays() ? rules.tavernMood() : 0;
    }

    /**
     * Forgets a citizen who died or left.
     */
    public void forgetCitizen(UUID citizen) {
        boolean changed = sick.remove(citizen) != null;
        changed |= tavern.remove(citizen) != null;
        if (changed) {
            changed();
        }
    }

    public Set<UUID> knownCitizens() {
        Set<UUID> ids = new LinkedHashSet<>(sick.keySet());
        ids.addAll(tavern.keySet());
        return ids;
    }

    // ---------------------------------------------------------------- festivals

    public Festival festival(UUID kingdomId) {
        return festivals.getOrDefault(kingdomId, Festival.NONE);
    }

    public void setFestival(UUID kingdomId, Festival festival) {
        if (!festival.equals(festivals.put(kingdomId, festival))) {
            changed();
        }
    }

    public List<Event> events(UUID kingdomId) {
        Deque<Event> list = events.get(kingdomId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public void addEvent(UUID kingdomId, Event event) {
        Deque<Event> list = events.computeIfAbsent(kingdomId, k -> new ArrayDeque<>());
        list.addFirst(event);
        while (list.size() > EVENTS) {
            list.removeLast();
        }
        changed();
    }

    public Set<UUID> kingdoms() {
        Set<UUID> ids = new LinkedHashSet<>(festivals.keySet());
        ids.addAll(events.keySet());
        return ids;
    }

    public void forgetKingdom(UUID kingdomId) {
        boolean changed = festivals.remove(kingdomId) != null;
        changed |= events.remove(kingdomId) != null;
        changed |= fairs.keySet().removeIf(key -> key.startsWith(kingdomId + "/"));
        if (changed) {
            changed();
        }
    }

    // ---------------------------------------------------------------- fairs

    /**
     * The day a city last held its fair, or {@link Long#MIN_VALUE}.
     */
    public long fairDay(String cityKey) {
        return fairs.getOrDefault(cityKey, Long.MIN_VALUE);
    }

    public void setFairDay(String cityKey, long day) {
        Long before = fairs.put(cityKey, day);
        if (before == null || before != day) {
            changed();
        }
    }

    public Map<String, Long> fairs() {
        return java.util.Collections.unmodifiableMap(fairs);
    }

    public Collection<FairLot> fairLots() {
        return List.copyOf(fairLots.values());
    }

    public void addFairLot(FairLot lot) {
        fairLots.put(lot.lotId(), lot);
        changed();
    }

    public Optional<FairLot> fairLot(long lotId) {
        return Optional.ofNullable(fairLots.get(lotId));
    }

    public void removeFairLot(long lotId) {
        if (fairLots.remove(lotId) != null) {
            changed();
        }
    }

    // ---------------------------------------------------------------- rooms

    public Optional<Room> room(UUID player) {
        return Optional.ofNullable(rooms.get(player));
    }

    public Map<UUID, Room> rooms() {
        return java.util.Collections.unmodifiableMap(rooms);
    }

    public void rent(UUID player, Room room) {
        rooms.put(player, room);
        changed();
    }

    public Optional<Room> leave(UUID player) {
        Room room = rooms.remove(player);
        if (room != null) {
            changed();
        }
        return Optional.ofNullable(room);
    }

    // ---------------------------------------------------------------- camps marked on atlases

    public Set<Long> marks(UUID player) {
        Set<Long> set = marks.get(player);
        return set == null ? Set.of() : java.util.Collections.unmodifiableSet(set);
    }

    public Map<UUID, Set<Long>> allMarks() {
        return java.util.Collections.unmodifiableMap(marks);
    }

    /**
     * Marks a camp on a player's atlas; the oldest mark goes when there are too many.
     *
     * @return whether the camp was not marked before
     */
    public boolean mark(UUID player, long campId) {
        Set<Long> set = marks.computeIfAbsent(player, p -> new LinkedHashSet<>());
        if (!set.add(campId)) {
            return false;
        }
        while (set.size() > MAX_MARKS) {
            set.remove(set.iterator().next());
        }
        changed();
        return true;
    }

    /**
     * Forgets camps that are gone.
     */
    public void keepMarks(java.util.function.LongPredicate exists) {
        boolean changed = false;
        for (Set<Long> set : new ArrayList<>(marks.values())) {
            changed |= set.removeIf(id -> !exists.test(id));
        }
        changed |= marks.values().removeIf(Set::isEmpty);
        if (changed) {
            changed();
        }
    }

    // ---------------------------------------------------------------- beds of players

    /**
     * Remembers the bed a player sleeps in as their point of return, or forgets it ({@code null}).
     */
    public void setPlayerBed(UUID player, WorldPos bed) {
        WorldPos before = bed == null ? playerBeds.remove(player) : playerBeds.put(player, bed);
        if (!Objects.equals(before, bed)) {
            changed();
        }
    }

    public Map<UUID, WorldPos> playerBeds() {
        return java.util.Collections.unmodifiableMap(playerBeds);
    }

    public boolean isPlayerBed(WorldPos bed) {
        return playerBeds.containsValue(bed);
    }

    public void restorePlayerBed(UUID player, WorldPos bed) {
        playerBeds.put(player, bed);
    }

    // ---------------------------------------------------------------- restoring saved data

    public void restoreSick(UUID citizen, Sick value) {
        sick.put(citizen, value);
    }

    public void restoreTavern(UUID citizen, long day) {
        tavern.put(citizen, day);
    }

    public void restoreFestival(UUID kingdomId, Festival festival, List<Event> list) {
        festivals.put(kingdomId, festival);
        if (!list.isEmpty()) {
            Deque<Event> deque = new ArrayDeque<>();
            for (Event event : list) {
                if (deque.size() < EVENTS) {
                    deque.addLast(event);
                }
            }
            events.put(kingdomId, deque);
        }
    }

    public void restoreFair(String cityKey, long day) {
        fairs.put(cityKey, day);
    }

    public void restoreFairLot(FairLot lot) {
        fairLots.put(lot.lotId(), lot);
    }

    public void restoreRoom(UUID player, Room room) {
        rooms.put(player, room);
    }

    public void restoreMarks(UUID player, Set<Long> camps) {
        if (!camps.isEmpty()) {
            marks.put(player, new LinkedHashSet<>(camps));
        }
    }
}
