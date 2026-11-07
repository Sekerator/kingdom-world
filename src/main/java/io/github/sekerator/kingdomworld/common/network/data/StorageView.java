package io.github.sekerator.kingdomworld.common.network.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the "Storehouses" tab of the throne screen shows to a member of the kingdom: everything all its storehouses
 * hold, by kind of item, and the storehouses with how full they are. Only for looking.
 *
 * @param kinds kinds of items in all storehouses; the list may be shorter
 * @param total items in all storehouses
 */
public record StorageView(UUID kingdomId, List<Entry> items, List<Storehouse> storehouses, int kinds, long total) {
    public static final int MAX_ITEMS = 512;
    public static final int MAX_STOREHOUSES = 64;

    /**
     * One kind of item.
     *
     * @param sample   one item of the kind, with its tag
     * @param reserved items of the kind workers set aside for their work, part of {@code count}
     */
    public record Entry(ItemStack sample, long count, long reserved) {
        public Entry {
            Objects.requireNonNull(sample, "sample");
        }
    }

    /**
     * One storehouse.
     *
     * @param used slots holding something
     */
    public record Storehouse(BlockPos pos, int used, int slots) {
        public Storehouse {
            Objects.requireNonNull(pos, "pos");
        }
    }

    public StorageView {
        Objects.requireNonNull(kingdomId, "kingdomId");
        items = List.copyOf(items);
        storehouses = List.copyOf(storehouses);
        if (items.size() > MAX_ITEMS || storehouses.size() > MAX_STOREHOUSES) {
            throw new IllegalArgumentException("Storage view too large");
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(kingdomId);
        buf.writeVarInt(items.size());
        for (Entry entry : items) {
            buf.writeItem(entry.sample());
            buf.writeVarLong(entry.count());
            buf.writeVarLong(entry.reserved());
        }
        buf.writeVarInt(storehouses.size());
        for (Storehouse storehouse : storehouses) {
            buf.writeBlockPos(storehouse.pos());
            buf.writeVarInt(storehouse.used());
            buf.writeVarInt(storehouse.slots());
        }
        buf.writeVarInt(kinds);
        buf.writeVarLong(total);
    }

    public static StorageView decode(FriendlyByteBuf buf) {
        UUID kingdomId = buf.readUUID();
        int size = KingdomView.readSize(buf, MAX_ITEMS);
        List<Entry> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(new Entry(buf.readItem(), buf.readVarLong(), buf.readVarLong()));
        }
        int count = KingdomView.readSize(buf, MAX_STOREHOUSES);
        List<Storehouse> storehouses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            storehouses.add(new Storehouse(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));
        }
        return new StorageView(kingdomId, items, storehouses, buf.readVarInt(), buf.readVarLong());
    }
}
