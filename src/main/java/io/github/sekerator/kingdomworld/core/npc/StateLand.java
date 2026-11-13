package io.github.sekerator.kingdomworld.core.npc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What each state of the world was planned to hold and what it did with its land since: the provinces and chunks the plan
 * gave it, the day of its last sale and how many it sold, the day it last took a wild province. Kept with the provinces.
 *
 * <p>Server thread only.
 */
public final class StateLand {
    /** A day long before any sale. */
    public static final long NEVER = Long.MIN_VALUE / 4;

    /**
     * The land of one state.
     *
     * @param plannedProvinces the provinces the plan gave it
     * @param plannedChunks    their chunks
     * @param lastSaleDay      the day of its last sale, {@link #NEVER} before the first
     * @param sold             the provinces it sold in all
     * @param lastGrowthDay    the day it last took a wild province, {@link #NEVER} before the first
     */
    public record Entry(int plannedProvinces, long plannedChunks, long lastSaleDay, int sold, long lastGrowthDay) {
        public Entry {
            plannedProvinces = Math.max(0, plannedProvinces);
            plannedChunks = Math.max(0, plannedChunks);
            sold = Math.max(0, sold);
        }

        public static Entry planned(int provinces, long chunks) {
            return new Entry(provinces, chunks, NEVER, 0, NEVER);
        }

        public Entry withSale(long day) {
            return new Entry(plannedProvinces, plannedChunks, day, sold + 1, lastGrowthDay);
        }

        public Entry withGrowth(long day) {
            return new Entry(plannedProvinces, plannedChunks, lastSaleDay, sold, day);
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    public Optional<Entry> entry(UUID kingdomId) {
        return Optional.ofNullable(entries.get(kingdomId));
    }

    public Map<UUID, Entry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    /**
     * Remembers what the plan gave a state; what it sold and took stays.
     */
    public void plan(UUID kingdomId, int provinces, long chunks) {
        Entry old = entries.get(kingdomId);
        Entry entry = old == null ? Entry.planned(provinces, chunks)
                : new Entry(provinces, chunks, old.lastSaleDay(), old.sold(), old.lastGrowthDay());
        put(kingdomId, entry);
    }

    /**
     * The entry of a state, made from what it holds now when the plan left none.
     */
    public Entry entryOr(UUID kingdomId, int provinces, long chunks) {
        Entry entry = entries.get(kingdomId);
        if (entry == null) {
            entry = Entry.planned(provinces, chunks);
            put(kingdomId, entry);
        }
        return entry;
    }

    public void sold(UUID kingdomId, long day) {
        Entry entry = entries.get(kingdomId);
        put(kingdomId, (entry == null ? Entry.planned(0, 0) : entry).withSale(day));
    }

    public void grew(UUID kingdomId, long day) {
        Entry entry = entries.get(kingdomId);
        put(kingdomId, (entry == null ? Entry.planned(0, 0) : entry).withGrowth(day));
    }

    public void forget(UUID kingdomId) {
        if (entries.remove(kingdomId) != null) {
            changeListener.run();
        }
    }

    /**
     * Puts an entry back as it was saved, for loading.
     */
    public void restore(UUID kingdomId, Entry entry) {
        entries.put(Objects.requireNonNull(kingdomId, "kingdomId"), Objects.requireNonNull(entry, "entry"));
    }

    private void put(UUID kingdomId, Entry entry) {
        entries.put(Objects.requireNonNull(kingdomId, "kingdomId"), entry);
        changeListener.run();
    }
}
