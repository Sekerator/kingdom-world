package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.citizen.Profession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One page of the items citizens can make, matching a search, for choosing what to order.
 *
 * @param page  the page shown, from 0
 * @param total items that match the search
 */
public record CatalogView(UUID kingdomId, String search, int page, int pageSize, int total, List<Entry> entries) {
    /** Longest search text. */
    public static final int MAX_SEARCH = 64;
    /** Most items on one page. */
    public static final int MAX_PAGE_SIZE = 32;

    /**
     * An item and the profession that makes it.
     */
    public record Entry(ItemStack sample, String profession) {
        public Entry {
            Objects.requireNonNull(sample, "sample");
            Objects.requireNonNull(profession, "profession");
        }
    }

    public CatalogView {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(search, "search");
        entries = List.copyOf(entries);
        if (entries.size() > MAX_PAGE_SIZE || search.length() > MAX_SEARCH) {
            throw new IllegalArgumentException("Catalog page too large");
        }
    }

    public int pages() {
        return Math.max(1, (total + Math.max(1, pageSize) - 1) / Math.max(1, pageSize));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(kingdomId);
        buf.writeUtf(search, MAX_SEARCH);
        buf.writeVarInt(page);
        buf.writeVarInt(pageSize);
        buf.writeVarInt(total);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeItem(entry.sample());
            buf.writeUtf(entry.profession(), Profession.MAX_ID_LENGTH);
        }
    }

    public static CatalogView decode(FriendlyByteBuf buf) {
        UUID kingdomId = buf.readUUID();
        String search = buf.readUtf(MAX_SEARCH);
        int page = buf.readVarInt();
        int pageSize = buf.readVarInt();
        int total = buf.readVarInt();
        int size = KingdomView.readSize(buf, MAX_PAGE_SIZE);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readItem(), buf.readUtf(Profession.MAX_ID_LENGTH)));
        }
        return new CatalogView(kingdomId, search, page, pageSize, total, entries);
    }
}
