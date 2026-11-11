package io.github.sekerator.kingdomworld.core.siege;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The damaged blocks of a kingdom, so that they are put back. There are two kinds. Blocks of buildings and walls broken in a
 * war are mended by the builders from the storehouses after the war (or earlier, when no enemy was near for a while);
 * blocks players built outside the register of buildings are not recorded then, they are a risk of war. Blocks of a city,
 * a building, a wall or a road broken in peace (damaged property, a crime) are recorded as they stood, whatever they were,
 * and come back for free once the offender is gone; a block a stranger placed in a city is recorded with what it covered
 * and is taken out again.
 *
 * <p>Only the first state of a place is kept: a block broken, placed again by somebody else and broken again is mended to
 * what the kingdom had there.
 */
public final class WarDamage {
    /**
     * Most damaged blocks kept for one kingdom. Beyond that the oldest damage of war goes unmended, and damage of peace is
     * refused: a city whose book is full cannot be damaged further until it is mended. A record is two short texts and a
     * few numbers, about 300 bytes with its place in the map, so a kingdom whose city is being torn down holds 5 MB at most.
     */
    public static final int MAX_PER_KINGDOM = 16_384;

    /**
     * A damaged block.
     *
     * @param war      the war it was broken in, 0 in peace
     * @param state    the block state as the world writes it, to put it back
     * @param item     the item it is made from, to pay for it; empty when it is put back for free
     * @param building the building of the register it belongs to, 0 for a city wall without one and for anything else
     * @param free     whether the block comes back for free: damaged in peace, not in a war
     * @param placed   the block state a stranger placed there since, to take it out; empty for none
     */
    public record Entry(long war, UUID kingdom, String dimension, long pos, String state, String item, long building, long time, boolean free,
                        String placed) {
        public Entry {
            Objects.requireNonNull(kingdom, "kingdom");
            Objects.requireNonNull(dimension, "dimension");
            state = state == null ? "" : state;
            item = item == null ? "" : item;
            placed = placed == null ? "" : placed;
        }

        /**
         * A block broken in a war, mended from the storehouses.
         */
        public Entry(long war, UUID kingdom, String dimension, long pos, String state, String item, long building, long time) {
            this(war, kingdom, dimension, pos, state, item, building, time, false, "");
        }

        /**
         * A block damaged in peace, put back for free.
         *
         * @param placed what a stranger placed there, empty when the block was only broken
         */
        public static Entry inPeace(UUID kingdom, String dimension, long pos, String state, String placed, long time) {
            return new Entry(0, kingdom, dimension, pos, state, "", 0, time, true, placed);
        }

        String key() {
            return dimension + "@" + pos;
        }
    }

    private final Map<UUID, LinkedHashMap<String, Entry>> broken = new LinkedHashMap<>();

    /**
     * Records a damaged block. A book that is full takes no more damage of peace: nothing gives way to it, so whoever asks
     * must not break what cannot be written down. Damage of war pushes out the oldest damage of war, as it always did, and
     * is lost only when the whole book is damage of peace.
     *
     * @return whether it is new (not recorded before, and the book had room)
     */
    public boolean record(Entry entry) {
        LinkedHashMap<String, Entry> of = broken.get(entry.kingdom());
        if (of != null && of.containsKey(entry.key())) {
            return false;
        }
        if (of != null && of.size() >= MAX_PER_KINGDOM) {
            if (entry.free()) {
                return false;
            }
            String oldestOfWar = null;
            for (Entry kept : of.values()) {
                if (!kept.free()) {
                    oldestOfWar = kept.key();
                    break;
                }
            }
            if (oldestOfWar == null) {
                return false;
            }
            of.remove(oldestOfWar);
        }
        broken.computeIfAbsent(entry.kingdom(), k -> new LinkedHashMap<>()).put(entry.key(), entry);
        return true;
    }

    /**
     * How many more records the book of a kingdom takes.
     */
    public int room(UUID kingdom) {
        return Math.max(0, MAX_PER_KINGDOM - count(kingdom));
    }

    /**
     * A stranger placed a block where a record is kept: the record remembers it, to take it out when the place is mended.
     *
     * @return whether a record of the place exists
     */
    public boolean placedOver(UUID kingdom, String dimension, long pos, String placed) {
        LinkedHashMap<String, Entry> of = broken.get(kingdom);
        Entry entry = of == null ? null : of.get(dimension + "@" + pos);
        if (entry == null) {
            return false;
        }
        of.put(entry.key(), new Entry(entry.war(), entry.kingdom(), entry.dimension(), entry.pos(), entry.state(), entry.item(), entry.building(),
                entry.time(), entry.free(), placed));
        return true;
    }

    public Optional<Entry> at(UUID kingdom, String dimension, long pos) {
        LinkedHashMap<String, Entry> of = broken.get(kingdom);
        return of == null ? Optional.empty() : Optional.ofNullable(of.get(dimension + "@" + pos));
    }

    public List<Entry> of(UUID kingdom) {
        LinkedHashMap<String, Entry> of = broken.get(kingdom);
        return of == null ? List.of() : List.copyOf(of.values());
    }

    /**
     * How many blocks of a kingdom wait to be put back for free.
     */
    public int countFree(UUID kingdom) {
        LinkedHashMap<String, Entry> of = broken.get(kingdom);
        if (of == null) {
            return 0;
        }
        int count = 0;
        for (Entry entry : of.values()) {
            if (entry.free()) {
                count++;
            }
        }
        return count;
    }

    public int count(UUID kingdom) {
        LinkedHashMap<String, Entry> of = broken.get(kingdom);
        return of == null ? 0 : of.size();
    }

    public Map<UUID, List<Entry>> all() {
        Map<UUID, List<Entry>> result = new LinkedHashMap<>();
        broken.forEach((kingdom, of) -> result.put(kingdom, List.copyOf(of.values())));
        return Collections.unmodifiableMap(result);
    }

    /**
     * What mending all broken blocks of a kingdom takes: items and how many of each, the most needed first.
     */
    public Map<String, Integer> cost(UUID kingdom) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : of(kingdom)) {
            if (!entry.item().isEmpty()) {
                counts.merge(entry.item(), 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> b.getValue() != a.getValue().intValue() ? Integer.compare(b.getValue(), a.getValue()) : a.getKey().compareTo(b.getKey()));
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : sorted) {
            result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    /**
     * The value of mending all broken blocks of a kingdom, by a price of each item.
     */
    public long value(UUID kingdom, java.util.function.ToLongFunction<String> price) {
        long sum = 0;
        for (Map.Entry<String, Integer> e : cost(kingdom).entrySet()) {
            sum += Math.max(0, price.applyAsLong(e.getKey())) * e.getValue();
        }
        return sum;
    }

    /**
     * Forgets a block, mended or given up.
     */
    public boolean mended(UUID kingdom, String dimension, long pos) {
        LinkedHashMap<String, Entry> of = broken.get(kingdom);
        if (of == null) {
            return false;
        }
        boolean removed = of.remove(dimension + "@" + pos) != null;
        if (of.isEmpty()) {
            broken.remove(kingdom);
        }
        return removed;
    }

    /**
     * Whether a place is recorded as broken for a kingdom.
     */
    public boolean contains(UUID kingdom, String dimension, long pos) {
        LinkedHashMap<String, Entry> of = broken.get(kingdom);
        return of != null && of.containsKey(dimension + "@" + pos);
    }

    /**
     * Hands the broken blocks of a kingdom to another (a city or a kingdom changed hands).
     *
     * @param inArea which entries move, the rest stay
     */
    public void move(UUID from, UUID to, java.util.function.Predicate<Entry> inArea) {
        LinkedHashMap<String, Entry> of = broken.get(from);
        if (of == null || from.equals(to)) {
            return;
        }
        for (Entry entry : List.copyOf(of.values())) {
            if (inArea.test(entry)) {
                of.remove(entry.key());
                record(new Entry(entry.war(), to, entry.dimension(), entry.pos(), entry.state(), entry.item(), entry.building(), entry.time(),
                        entry.free(), entry.placed()));
            }
        }
        if (of.isEmpty()) {
            broken.remove(from);
        }
    }

    public void forget(UUID kingdom) {
        broken.remove(kingdom);
    }

    public void clear() {
        broken.clear();
    }
}
