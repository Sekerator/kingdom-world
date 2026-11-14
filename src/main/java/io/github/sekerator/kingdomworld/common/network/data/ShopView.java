package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The window of a shop of citizens for a player: a page of what it sells, the cheapest first, each with its price with the tax
 * as this player pays it, and what the shop is.
 *
 * @param keeper     the names of its keepers, empty for the family of a house
 * @param status     the id of its state
 * @param rows       one page of its goods
 * @param count      all the kinds of goods it has
 * @param sales      what it sold a day, the last week
 * @param debt       what it still owes the crown
 * @param canBuy     whether this player may buy now
 * @param why        the key of the reason it may not, empty when it may
 */
public record ShopView(long shopId, UUID kingdomId, String kingdomName, String currencyName, long silver, long gold, String trade, String keeper,
                       String status, List<Row> rows, int page, int count, int margin, int salesTax, long sales, long debt, boolean canBuy,
                       String why) {
    public static final int PAGE = 64;
    public static final int MAX_NAME = 64;

    /**
     * Goods of one kind.
     *
     * @param price what one costs with the tax
     * @param tax   the tax in it
     */
    public record Row(ItemStack item, long count, long price, long tax) {
        public Row {
            Objects.requireNonNull(item, "item");
        }
    }

    public ShopView {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(kingdomName, "kingdomName");
        Objects.requireNonNull(currencyName, "currencyName");
        Objects.requireNonNull(trade, "trade");
        Objects.requireNonNull(keeper, "keeper");
        Objects.requireNonNull(status, "status");
        why = why == null ? "" : why;
        rows = List.copyOf(rows);
        keeper = keeper.length() > MAX_NAME * 2 ? keeper.substring(0, MAX_NAME * 2) : keeper;
        if (rows.size() > PAGE) {
            throw new IllegalArgumentException("Too many goods in a view");
        }
    }

    public CurrencyUnits units() {
        return CurrencyUnits.valid(silver, gold) ? new CurrencyUnits(silver, gold) : CurrencyUnits.DEFAULT;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(shopId);
        buf.writeUUID(kingdomId);
        buf.writeUtf(kingdomName, MAX_NAME);
        buf.writeUtf(currencyName, MAX_NAME);
        buf.writeVarLong(silver);
        buf.writeVarLong(gold);
        buf.writeUtf(trade, MAX_NAME);
        buf.writeUtf(keeper, MAX_NAME * 2);
        buf.writeUtf(status, MAX_NAME);
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeItem(row.item());
            buf.writeVarLong(row.count());
            buf.writeVarLong(row.price());
            buf.writeVarLong(row.tax());
        }
        buf.writeVarInt(page);
        buf.writeVarInt(count);
        buf.writeVarInt(margin);
        buf.writeVarInt(salesTax);
        buf.writeVarLong(sales);
        buf.writeVarLong(debt);
        buf.writeBoolean(canBuy);
        buf.writeUtf(why, MAX_NAME * 2);
    }

    public static ShopView decode(FriendlyByteBuf buf) {
        long shop = buf.readVarLong();
        UUID kingdom = buf.readUUID();
        String kingdomName = buf.readUtf(MAX_NAME);
        String currency = buf.readUtf(MAX_NAME);
        long silver = buf.readVarLong();
        long gold = buf.readVarLong();
        String trade = buf.readUtf(MAX_NAME);
        String keeper = buf.readUtf(MAX_NAME * 2);
        String status = buf.readUtf(MAX_NAME);
        int size = buf.readVarInt();
        if (size < 0 || size > PAGE) {
            throw new DecoderException("Too many goods: " + size);
        }
        List<Row> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new Row(buf.readItem(), amount(buf), amount(buf), amount(buf)));
        }
        return new ShopView(shop, kingdom, kingdomName, currency, silver, gold, trade, keeper, status, rows, buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), amount(buf), amount(buf), buf.readBoolean(), buf.readUtf(MAX_NAME * 2));
    }

    private static long amount(FriendlyByteBuf buf) {
        long value = buf.readVarLong();
        if (!Money.isAmount(value)) {
            throw new DecoderException("Amount out of range: " + value);
        }
        return value;
    }
}
