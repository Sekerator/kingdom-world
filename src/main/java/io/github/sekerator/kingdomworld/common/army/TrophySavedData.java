package io.github.sekerator.kingdomworld.common.army;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Trophies of war in saved data: gear taken from the fallen of an enemy and brought to a storehouse, kept with the kingdom
 * it belonged to until it is bought back after peace or the time to buy it back is over.
 *
 * <pre>
 * trophies: [ { id, owner, holder, worth, peaceDay, items: [ item ] } ], nextId
 * </pre>
 */
public final class TrophySavedData extends SavedData {
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_trophies";
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Gear of one kingdom held by another.
     *
     * @param worth    what the gear was worth when it was brought in
     * @param peaceDay the day the war between the two ended, 0 while it goes on
     */
    public record Trophy(long id, UUID owner, UUID holder, long worth, long peaceDay, List<ItemStack> items) {
        public Trophy {
            items = items.stream().map(ItemStack::copy).toList();
        }

        public Trophy withPeaceDay(long day) {
            return new Trophy(id, owner, holder, worth, day, items);
        }

        public Trophy withItems(List<ItemStack> rest) {
            return new Trophy(id, owner, holder, worth, peaceDay, rest);
        }

        public int count() {
            return items.stream().mapToInt(ItemStack::getCount).sum();
        }
    }

    private final Map<Long, Trophy> trophies = new LinkedHashMap<>();
    private long nextId = 1;

    public static TrophySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TrophySavedData::load, TrophySavedData::new, DATA_NAME);
    }

    public Collection<Trophy> all() {
        return Collections.unmodifiableCollection(trophies.values());
    }

    public Optional<Trophy> trophy(long id) {
        return Optional.ofNullable(trophies.get(id));
    }

    public Trophy add(UUID owner, UUID holder, long worth, long peaceDay, List<ItemStack> items) {
        Trophy trophy = new Trophy(nextId++, owner, holder, worth, peaceDay, items);
        trophies.put(trophy.id(), trophy);
        setDirty();
        return trophy;
    }

    public void put(Trophy trophy) {
        trophies.put(trophy.id(), trophy);
        setDirty();
    }

    public Optional<Trophy> remove(long id) {
        Trophy removed = trophies.remove(id);
        if (removed != null) {
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Trophy trophy : trophies.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", trophy.id());
            entry.putUUID("owner", trophy.owner());
            entry.putUUID("holder", trophy.holder());
            entry.putLong("worth", trophy.worth());
            entry.putLong("peaceDay", trophy.peaceDay());
            ListTag items = new ListTag();
            for (ItemStack stack : trophy.items()) {
                items.add(stack.save(new CompoundTag()));
            }
            entry.put("items", items);
            list.add(entry);
        }
        tag.put("trophies", list);
        tag.putLong("nextId", nextId);
        return tag;
    }

    public static TrophySavedData load(CompoundTag tag) {
        TrophySavedData data = new TrophySavedData();
        ListTag list = tag.getList("trophies", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                List<ItemStack> items = new ArrayList<>();
                ListTag stacks = entry.getList("items", Tag.TAG_COMPOUND);
                for (int j = 0; j < stacks.size(); j++) {
                    ItemStack stack = ItemStack.of(stacks.getCompound(j));
                    if (!stack.isEmpty()) {
                        items.add(stack);
                    }
                }
                Trophy trophy = new Trophy(entry.getLong("id"), entry.getUUID("owner"), entry.getUUID("holder"), entry.getLong("worth"),
                        entry.getLong("peaceDay"), items);
                data.trophies.put(trophy.id(), trophy);
            } catch (RuntimeException e) {
                LOGGER.warn("KingdomWorld could not read trophy #{}: {}", entry.getLong("id"), e.getMessage());
            }
        }
        long max = data.trophies.keySet().stream().mapToLong(Long::longValue).max().orElse(0);
        data.nextId = Math.max(max + 1, tag.getLong("nextId"));
        return data;
    }
}
