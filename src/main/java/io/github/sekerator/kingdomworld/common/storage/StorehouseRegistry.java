package io.github.sekerator.kingdomworld.common.storage;

import io.github.sekerator.kingdomworld.common.data.CitizenNbt;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Every storehouse of the world by position, with its contents. Saved with the kingdom data:
 * {@code storehouses: [ { pos, items, accepts? } ]}, where {@code accepts} lists the professions the storehouse takes in from and
 * is left out for a storehouse that takes in from everybody (as storehouses saved before it existed do).
 */
public final class StorehouseRegistry {
    private static final String POS = "pos";
    private static final String ITEMS = "items";
    private static final String ACCEPTS = "accepts";

    private final Map<WorldPos, StorehouseInventory> byPos = new LinkedHashMap<>();
    /** The storehouses of every chunk, so that a chunk is looked at without the whole world. */
    private final io.github.sekerator.kingdomworld.core.citizen.ChunkBuckets<StorehouseInventory> byChunk =
            new io.github.sekerator.kingdomworld.core.citizen.ChunkBuckets<>();
    private long version;
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
        for (StorehouseInventory inventory : byPos.values()) {
            inventory.setChangeListener(listener);
        }
    }

    public Optional<StorehouseInventory> get(WorldPos pos) {
        return Optional.ofNullable(byPos.get(pos));
    }

    /**
     * The storehouse at the position, created empty if there is none yet.
     */
    public StorehouseInventory getOrCreate(WorldPos pos) {
        StorehouseInventory inventory = byPos.get(pos);
        if (inventory == null) {
            inventory = new StorehouseInventory(pos);
            inventory.setChangeListener(changeListener);
            byPos.put(pos, inventory);
            byChunk.add(pos.dimension(), pos.chunk(), inventory);
            version++;
            changeListener.run();
        }
        return inventory;
    }

    public Optional<StorehouseInventory> remove(WorldPos pos) {
        StorehouseInventory inventory = byPos.remove(pos);
        if (inventory != null) {
            byChunk.remove(pos.dimension(), pos.chunk(), inventory);
            version++;
            inventory.setChangeListener(() -> {
            });
            changeListener.run();
        }
        return Optional.ofNullable(inventory);
    }

    public Collection<StorehouseInventory> all() {
        return Collections.unmodifiableCollection(byPos.values());
    }

    /**
     * The storehouses of a chunk, in the order they were made; a view that must not outlive a change of the storehouses.
     */
    public List<StorehouseInventory> inChunk(String dimension, long chunk) {
        return byChunk.get(dimension, chunk);
    }

    /**
     * Grows whenever a storehouse is made or taken away: lists built from the storehouses compare it.
     */
    public long version() {
        return version;
    }

    public int size() {
        return byPos.size();
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (StorehouseInventory inventory : byPos.values()) {
            CompoundTag entry = new CompoundTag();
            entry.put(POS, CitizenNbt.writePos(inventory.pos()));
            entry.put(ITEMS, inventory.serializeNBT());
            if (!inventory.accepts().all()) {
                ListTag accepts = new ListTag();
                for (String profession : inventory.accepts().professions()) {
                    accepts.add(net.minecraft.nbt.StringTag.valueOf(profession));
                }
                entry.put(ACCEPTS, accepts);
            }
            list.add(entry);
        }
        return list;
    }

    /**
     * Reads every storehouse that can be read.
     *
     * @return descriptions of the entries that could not be read
     */
    public List<String> load(Tag raw) {
        List<String> problems = new ArrayList<>();
        if (!(raw instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            problems.add("'storehouses' is not a list of compounds");
            return problems;
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                WorldPos pos = CitizenNbt.readPos(entry, POS, "storehouse #" + i);
                if (byPos.containsKey(pos)) {
                    throw new IllegalArgumentException("two storehouses at " + pos);
                }
                if (!entry.contains(ITEMS, Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("storehouse #" + i + " has no items");
                }
                StorehouseInventory inventory = new StorehouseInventory(pos);
                inventory.deserializeNBT(entry.getCompound(ITEMS));
                if (inventory.getSlots() != StorehouseInventory.SLOTS) {
                    throw new IllegalArgumentException("storehouse #" + i + " has " + inventory.getSlots() + " slots");
                }
                if (entry.contains(ACCEPTS, Tag.TAG_LIST)) {
                    List<String> professions = new ArrayList<>();
                    ListTag accepts = entry.getList(ACCEPTS, Tag.TAG_STRING);
                    for (int p = 0; p < accepts.size(); p++) {
                        professions.add(accepts.getString(p));
                    }
                    inventory.setAccepts(io.github.sekerator.kingdomworld.core.citizen.StorehouseRules.Accepts.of(professions));
                }
                inventory.setChangeListener(changeListener);
                byPos.put(pos, inventory);
                byChunk.add(pos.dimension(), pos.chunk(), inventory);
                version++;
            } catch (RuntimeException e) {
                problems.add("storehouse #" + i + ": " + e.getMessage());
            }
        }
        return problems;
    }
}
