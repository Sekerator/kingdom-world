package io.github.sekerator.kingdomworld.common.citizen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What every citizen carries for work: the tool in hand, a second tool (shears of a herder) and armor. It belongs
 * to the citizen rather than to its body, so a tool keeps wearing out far from players and a body that appears
 * again holds the same tool. Saved with the kingdom data: {@code gear: [ { citizen, <slot>: item } ]}.
 *
 * <p>Server thread only.
 */
public final class GearRegistry {
    /** Slots citizens use. */
    public static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final String CITIZEN = "citizen";

    private final Map<UUID, Map<EquipmentSlot, ItemStack>> byCitizen = new LinkedHashMap<>();
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * What a citizen has in a slot; an empty stack if nothing. The stack is the stored one: changing it changes the
     * gear, so callers that change it call {@link #changed()}.
     */
    public ItemStack get(UUID citizen, EquipmentSlot slot) {
        Map<EquipmentSlot, ItemStack> gear = byCitizen.get(citizen);
        return gear == null ? ItemStack.EMPTY : gear.getOrDefault(slot, ItemStack.EMPTY);
    }

    public void set(UUID citizen, EquipmentSlot slot, ItemStack stack) {
        ItemStack current = get(citizen, slot);
        if (stack.isEmpty() && current.isEmpty()) {
            return;
        }
        if (!stack.isEmpty() && ItemStack.matches(stack, current)) {
            return;
        }
        if (stack.isEmpty()) {
            Map<EquipmentSlot, ItemStack> gear = byCitizen.get(citizen);
            gear.remove(slot);
            if (gear.isEmpty()) {
                byCitizen.remove(citizen);
            }
        } else {
            byCitizen.computeIfAbsent(citizen, id -> new EnumMap<>(EquipmentSlot.class)).put(slot, stack);
        }
        changeListener.run();
    }

    /**
     * Marks the gear changed after a stored stack was changed in place (a tool wore down).
     */
    public void changed() {
        changeListener.run();
    }

    /**
     * Takes everything a citizen carries, for example when it dies or leaves its work.
     */
    public List<ItemStack> takeAll(UUID citizen) {
        Map<EquipmentSlot, ItemStack> gear = byCitizen.remove(citizen);
        if (gear == null) {
            return List.of();
        }
        changeListener.run();
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : gear.values()) {
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    public Optional<ItemStack> take(UUID citizen, EquipmentSlot slot) {
        ItemStack stack = get(citizen, slot);
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        set(citizen, slot, ItemStack.EMPTY);
        return Optional.of(stack);
    }

    /**
     * Citizens that carry anything.
     */
    public Set<UUID> citizens() {
        return Set.copyOf(byCitizen.keySet());
    }

    public Collection<Map<EquipmentSlot, ItemStack>> all() {
        return byCitizen.values();
    }

    public int size() {
        return byCitizen.size();
    }

    public ListTag save() {
        ListTag list = new ListTag();
        byCitizen.forEach((citizen, gear) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(CITIZEN, citizen);
            gear.forEach((slot, stack) -> {
                if (!stack.isEmpty()) {
                    entry.put(slot.getName(), stack.save(new CompoundTag()));
                }
            });
            list.add(entry);
        });
        return list;
    }

    /**
     * Reads every entry that can be read.
     *
     * @return descriptions of the entries that could not be read
     */
    public List<String> load(Tag raw) {
        List<String> problems = new ArrayList<>();
        if (!(raw instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            problems.add("'gear' is not a list of compounds");
            return problems;
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID(CITIZEN)) {
                problems.add("gear #" + i + " names no citizen");
                continue;
            }
            Map<EquipmentSlot, ItemStack> gear = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : SLOTS) {
                if (entry.contains(slot.getName())) {
                    if (!entry.contains(slot.getName(), Tag.TAG_COMPOUND)) {
                        problems.add("gear #" + i + " has an unreadable " + slot.getName());
                        continue;
                    }
                    ItemStack stack = ItemStack.of(entry.getCompound(slot.getName()));
                    if (!stack.isEmpty()) {
                        gear.put(slot, stack);
                    }
                }
            }
            if (!gear.isEmpty()) {
                byCitizen.put(entry.getUUID(CITIZEN), gear);
            }
        }
        return problems;
    }
}
