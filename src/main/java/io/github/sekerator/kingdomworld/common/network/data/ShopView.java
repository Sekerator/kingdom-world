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
 * as this player pays it, what of the inventory of the player it would buy and for how much, and what the shop is: its trade, the
 * workstations of its place and whether its trade is fixed.
 *
 * @param keeper     the names of its keepers, empty for the family of a house
 * @param status     the id of its state
 * @param rows       one page of its goods
 * @param count      all the kinds of goods it has
 * @param sales      what it sold a day, the last week
 * @param debt       what it still owes the crown
 * @param canBuy     whether this player may buy now
 * @param why        the key of the reason it may not, empty when it may
 * @param workshop   its trade and its place
 * @param sell       what of the inventory of the player it buys
 */
public record ShopView(long shopId, UUID kingdomId, String kingdomName, String currencyName, long silver, long gold, String trade, String keeper,
                       String status, List<Row> rows, int page, int count, int margin, int salesTax, long sales, long debt, boolean canBuy,
                       String why, Workshop workshop, List<SellRow> sell) {
    public static final int PAGE = 64;
    public static final int MAX_NAME = 64;
    public static final int MAX_SELL = 64;
    public static final int MAX_STATIONS = 16;
    public static final int MAX_TRADES = 32;

    /**
     * Goods of one kind.
     *
     * @param key   the key of the goods in the shop, which buying names
     * @param price what one costs with the tax
     * @param tax   the tax in it
     * @param made  whether citizens made them
     */
    public record Row(ItemStack item, long count, long price, long tax, String key, boolean made) {
        public Row {
            Objects.requireNonNull(item, "item");
            key = key == null ? "" : key;
        }

        public Row(ItemStack item, long count, long price, long tax) {
            this(item, count, price, tax, "", false);
        }
    }

    /**
     * An item of the inventory of the player the shop would buy.
     *
     * @param price what it pays for one
     * @param max   how many it takes at most, by its stock
     * @param money how many its till can pay for
     * @param have  how many the player has
     */
    public record SellRow(ItemStack item, long price, long max, long money, int have) {
        public SellRow {
            Objects.requireNonNull(item, "item");
        }
    }

    /**
     * The trade of a shop and its place.
     *
     * @param stations  the workstations of its place, empty when nobody looked at it yet
     * @param surveyed  whether its place was looked at
     * @param pinned    the trade fixed for the place, empty for none
     * @param nextTrade the trade it takes up once it sold off, empty for none
     * @param trades    the trades its place serves, for fixing one
     * @param canPin    whether the player may fix the trade
     */
    public record Workshop(List<String> stations, boolean surveyed, String pinned, String nextTrade, List<String> trades, boolean canPin) {
        public static final Workshop NONE = new Workshop(List.of(), false, "", "", List.of(), false);

        public Workshop {
            stations = List.copyOf(stations.size() > MAX_STATIONS ? stations.subList(0, MAX_STATIONS) : stations);
            trades = List.copyOf(trades.size() > MAX_TRADES ? trades.subList(0, MAX_TRADES) : trades);
            pinned = pinned == null ? "" : pinned;
            nextTrade = nextTrade == null ? "" : nextTrade;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(stations.size());
            for (String station : stations) {
                buf.writeUtf(station, MAX_NAME);
            }
            buf.writeBoolean(surveyed);
            buf.writeUtf(pinned, MAX_NAME);
            buf.writeUtf(nextTrade, MAX_NAME);
            buf.writeVarInt(trades.size());
            for (String trade : trades) {
                buf.writeUtf(trade, MAX_NAME);
            }
            buf.writeBoolean(canPin);
        }

        static Workshop decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_STATIONS) {
                throw new DecoderException("Too many workstations: " + size);
            }
            List<String> stations = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                stations.add(buf.readUtf(MAX_NAME));
            }
            boolean surveyed = buf.readBoolean();
            String pinned = buf.readUtf(MAX_NAME);
            String next = buf.readUtf(MAX_NAME);
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_TRADES) {
                throw new DecoderException("Too many trades: " + count);
            }
            List<String> trades = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                trades.add(buf.readUtf(MAX_NAME));
            }
            return new Workshop(stations, surveyed, pinned, next, trades, buf.readBoolean());
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
        workshop = workshop == null ? Workshop.NONE : workshop;
        sell = List.copyOf(sell == null ? List.of() : sell.size() > MAX_SELL ? sell.subList(0, MAX_SELL) : sell);
        keeper = keeper.length() > MAX_NAME * 2 ? keeper.substring(0, MAX_NAME * 2) : keeper;
        if (rows.size() > PAGE) {
            throw new IllegalArgumentException("Too many goods in a view");
        }
    }

    public ShopView(long shopId, UUID kingdomId, String kingdomName, String currencyName, long silver, long gold, String trade, String keeper,
                    String status, List<Row> rows, int page, int count, int margin, int salesTax, long sales, long debt, boolean canBuy, String why) {
        this(shopId, kingdomId, kingdomName, currencyName, silver, gold, trade, keeper, status, rows, page, count, margin, salesTax, sales, debt,
                canBuy, why, Workshop.NONE, List.of());
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
            buf.writeUtf(row.key(), 256);
            buf.writeBoolean(row.made());
        }
        buf.writeVarInt(page);
        buf.writeVarInt(count);
        buf.writeVarInt(margin);
        buf.writeVarInt(salesTax);
        buf.writeVarLong(sales);
        buf.writeVarLong(debt);
        buf.writeBoolean(canBuy);
        buf.writeUtf(why, MAX_NAME * 2);
        workshop.encode(buf);
        buf.writeVarInt(sell.size());
        for (SellRow row : sell) {
            buf.writeItem(row.item());
            buf.writeVarLong(row.price());
            buf.writeVarLong(row.max());
            buf.writeVarLong(row.money());
            buf.writeVarInt(row.have());
        }
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
            rows.add(new Row(buf.readItem(), amount(buf), amount(buf), amount(buf), buf.readUtf(256), buf.readBoolean()));
        }
        int page = buf.readVarInt();
        int count = buf.readVarInt();
        int margin = buf.readVarInt();
        int salesTax = buf.readVarInt();
        long sales = amount(buf);
        long debt = amount(buf);
        boolean canBuy = buf.readBoolean();
        String why = buf.readUtf(MAX_NAME * 2);
        Workshop workshop = Workshop.decode(buf);
        int sells = buf.readVarInt();
        if (sells < 0 || sells > MAX_SELL) {
            throw new DecoderException("Too many items to sell: " + sells);
        }
        List<SellRow> sell = new ArrayList<>(sells);
        for (int i = 0; i < sells; i++) {
            sell.add(new SellRow(buf.readItem(), amount(buf), amount(buf), amount(buf), buf.readVarInt()));
        }
        return new ShopView(shop, kingdom, kingdomName, currency, silver, gold, trade, keeper, status, rows, page, count, margin, salesTax, sales,
                debt, canBuy, why, workshop, sell);
    }

    private static long amount(FriendlyByteBuf buf) {
        long value = buf.readVarLong();
        if (!Money.isAmount(value)) {
            throw new DecoderException("Amount out of range: " + value);
        }
        return value;
    }
}
