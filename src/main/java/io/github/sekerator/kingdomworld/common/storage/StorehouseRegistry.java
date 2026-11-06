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
 * {@code storehouses: [ { pos, items } ]}.
 */
public final class StorehouseRegistry {
    private static final String POS = "pos";
    private static final String ITEMS = "items";

    private final Map<WorldPos, StorehouseInventory> byPos = new LinkedHashMap<>();
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
            changeListener.run();
        }
        return inventory;
    }

    public Optional<StorehouseInventory> remove(WorldPos pos) {
        StorehouseInventory inventory = byPos.remove(pos);
        if (inventory != null) {
            inventory.setChangeListener(() -> {
            });
            changeListener.run();
        }
        return Optional.ofNullable(inventory);
    }

    public Collection<StorehouseInventory> all() {
        return Collections.unmodifiableCollection(byPos.values());
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
                inventory.setChangeListener(changeListener);
                byPos.put(pos, inventory);
            } catch (RuntimeException e) {
                problems.add("storehouse #" + i + ": " + e.getMessage());
            }
        }
        return problems;
    }
}
