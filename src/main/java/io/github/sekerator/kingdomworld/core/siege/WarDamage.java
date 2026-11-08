package io.github.sekerator.kingdomworld.core.siege;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The blocks of buildings and walls of a kingdom broken in a war, so that its builders put them back from the storehouses
 * after the war (or earlier, when no enemy was near for a while). Only the first state of a place is kept: a block broken,
 * placed again by the enemy and broken again is mended to what the kingdom built. Blocks players built outside the register
 * of buildings are not recorded; they are a risk of war.
 */
public final class WarDamage {
    /** Most broken blocks kept for one kingdom; the oldest beyond go unmended. */
    public static final int MAX_PER_KINGDOM = 4_096;

    /**
     * A broken block.
     *
     * @param state    the block state as the world writes it, to put it back
     * @param item     the item it is made from, to pay for it
     * @param building the building of the register it belongs to, 0 for a city wall without one
     */
    public record Entry(long war, UUID kingdom, String dimension, long pos, String state, String item, long building, long time) {
        public Entry {
            Objects.requireNonNull(kingdom, "kingdom");
            Objects.requireNonNull(dimension, "dimension");
            state = state == null ? "" : state;
            item = item == null ? "" : item;
        }

        String key() {
            return dimension + "@" + pos;
        }
    }

    private final Map<UUID, LinkedHashMap<String, Entry>> broken = new LinkedHashMap<>();

    /**
     * Records a broken block.
     *
     * @return whether it is new (not recorded before)
     */
    public boolean record(Entry entry) {
        LinkedHashMap<String, Entry> of = broken.computeIfAbsent(entry.kingdom(), k -> new LinkedHashMap<>());
        if (of.containsKey(entry.key())) {
            return false;
        }
        of.put(entry.key(), entry);
        while (of.size() > MAX_PER_KINGDOM) {
            String first = of.keySet().iterator().next();
            of.remove(first);
        }
        return true;
    }

    public List<Entry> of(UUID kingdom) {
        LinkedHashMap<String, Entry> of = broken.get(kingdom);
        return of == null ? List.of() : List.copyOf(of.values());
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
                record(new Entry(entry.war(), to, entry.dimension(), entry.pos(), entry.state(), entry.item(), entry.building(), entry.time()));
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
