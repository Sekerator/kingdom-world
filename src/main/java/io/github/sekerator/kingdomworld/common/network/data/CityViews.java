package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The cities of a kingdom as the "Treasury" tab shows them: a row of each city, and the window of one city with its statement,
 * its taxes, its grants, the evasion and the means of its people and its shops.
 */
public final class CityViews {
    /** Rows of cities a view carries: one page. */
    public static final int PAGE = 64;
    /** Shops of a city a window carries. */
    public static final int MAX_SHOPS = 64;
    public static final int MAX_NAME = 64;

    private CityViews() {
    }

    /**
     * The taxes and the share of the crown, in thousandths.
     */
    public record Taxes(int salesTax, int tithe, int crownShare) {
        public static final Taxes NONE = new Taxes(0, 0, 0);

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(salesTax + 1);
            buf.writeVarInt(tithe + 1);
            buf.writeVarInt(crownShare + 1);
        }

        static Taxes decode(FriendlyByteBuf buf) {
            return new Taxes(permille(buf), permille(buf), permille(buf));
        }
    }

    /**
     * One city: the averages of the last week a day.
     *
     * @param income  the taxes its treasury collected
     * @param kept    what of them it kept
     * @param toCrown what it sent the crown
     * @param spent   what it spent
     * @param balance what its treasury holds now
     * @param money   the money of the crown from it
     * @param goods   its goods for the crown at the price of export
     */
    public record CityRow(long id, String name, boolean capital, long income, long kept, long toCrown, long spent, long balance, long money,
                          long goods) {
        public CityRow {
            Objects.requireNonNull(name, "name");
            name = name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        }

        public long total() {
            return money + goods;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUtf(name, MAX_NAME);
            buf.writeBoolean(capital);
            buf.writeVarLong(income);
            buf.writeVarLong(kept);
            buf.writeVarLong(toCrown);
            buf.writeVarLong(spent);
            buf.writeVarLong(balance);
            buf.writeLong(money);
            buf.writeLong(goods);
        }

        static CityRow decode(FriendlyByteBuf buf) {
            return new CityRow(buf.readVarLong(), buf.readUtf(MAX_NAME), buf.readBoolean(), amount(buf), amount(buf), amount(buf), amount(buf),
                    amount(buf), signed(buf), signed(buf));
        }
    }

    /**
     * The cities of a kingdom: the taxes it set, a page of rows and the totals.
     *
     * @param enabled     whether the money of the cities is on for the kingdom
     * @param maxSalesTax the most the sales tax may be, in percent
     * @param crownPerDay what the cities brought the crown a day, the last week
     * @param held        what the treasuries of the cities hold together
     */
    public record Cities(boolean enabled, Taxes taxes, int maxSalesTax, int maxTithe, List<CityRow> rows, int page, int count, long crownPerDay,
                         long held) {
        public static final Cities NONE = new Cities(false, Taxes.NONE, 25, 50, List.of(), 0, 0, 0, 0);

        public Cities {
            Objects.requireNonNull(taxes, "taxes");
            rows = List.copyOf(rows);
            if (rows.size() > PAGE) {
                throw new IllegalArgumentException("Too many cities in a view");
            }
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(enabled);
            taxes.encode(buf);
            buf.writeVarInt(maxSalesTax);
            buf.writeVarInt(maxTithe);
            buf.writeVarInt(rows.size());
            for (CityRow row : rows) {
                row.encode(buf);
            }
            buf.writeVarInt(page);
            buf.writeVarInt(count);
            buf.writeLong(crownPerDay);
            buf.writeVarLong(held);
        }

        public static Cities decode(FriendlyByteBuf buf) {
            boolean enabled = buf.readBoolean();
            Taxes taxes = Taxes.decode(buf);
            int maxSalesTax = buf.readVarInt();
            int maxTithe = buf.readVarInt();
            int size = size(buf, PAGE);
            List<CityRow> rows = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                rows.add(CityRow.decode(buf));
            }
            return new Cities(enabled, taxes, maxSalesTax, maxTithe, rows, buf.readVarInt(), buf.readVarInt(), buf.readLong(), amount(buf));
        }
    }

    /**
     * A shop of a city, as its window lists it.
     *
     * @param trade   the trade of the shop, such as {@code general_store}
     * @param keeper  the names of its keepers, empty for the family of a house
     * @param status  the id of its state
     * @param sales   what it sold a day, the last week
     * @param margin  its margin in thousandths
     * @param debt    what it still owes the crown
     */
    public record ShopRow(long id, String trade, String keeper, String status, long sales, int margin, long debt) {
        public ShopRow {
            Objects.requireNonNull(trade, "trade");
            Objects.requireNonNull(keeper, "keeper");
            Objects.requireNonNull(status, "status");
            keeper = keeper.length() > MAX_NAME ? keeper.substring(0, MAX_NAME) : keeper;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUtf(trade, MAX_NAME);
            buf.writeUtf(keeper, MAX_NAME);
            buf.writeUtf(status, MAX_NAME);
            buf.writeVarLong(sales);
            buf.writeVarInt(margin);
            buf.writeVarLong(debt);
        }

        static ShopRow decode(FriendlyByteBuf buf) {
            return new ShopRow(buf.readVarLong(), buf.readUtf(MAX_NAME), buf.readUtf(MAX_NAME), buf.readUtf(MAX_NAME), amount(buf),
                    buf.readVarInt(), amount(buf));
        }
    }

    /**
     * Why new shops do not open in a city whose people want more than its shops sell.
     */
    public enum Blocked {
        NONE,
        NO_PLACES,
        NO_TOWNSFOLK,
        NO_GOODS
    }

    /**
     * The window of one city.
     *
     * @param own         the taxes set for the city itself, -1 for those of the kingdom
     * @param effective   the taxes the city has
     * @param average     the lines of its statements, the average a day of the last week
     * @param latest      the lines of its last statement
     * @param days        how many statements it has
     * @param evasion     the share of the taxes its people evade, in thousandths
     * @param means       the means of its people: burden, shops, recall, share, all
     * @param crownFunds  what the treasury of the kingdom holds, for the grant
     * @param blocked     why new shops do not open while its people want more
     * @param shortfall   what its people wanted and did not find, a day
     * @param missing     an item the crown has too little of for its shops, empty for none
     */
    public record CityDetail(UUID kingdomId, long cityId, String name, boolean canManage, long balance, long granted, long committed, Taxes own,
                             Taxes effective, long[] average, long[] latest, int days, int evasion, int[] means, boolean head, long crownFunds,
                             List<ShopRow> shops, int shopCount, Blocked blocked, long shortfall, String missing) {
        public CityDetail {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(name, "name");
            name = name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
            average = fit(average, CityStatement.LINES);
            latest = fit(latest, CityStatement.LINES);
            means = means == null || means.length != 5 ? new int[5] : means.clone();
            shops = List.copyOf(shops);
            missing = missing == null ? "" : missing;
            if (shops.size() > MAX_SHOPS) {
                throw new IllegalArgumentException("Too many shops in a view");
            }
        }

        private static long[] fit(long[] values, int length) {
            long[] fixed = new long[length];
            if (values != null) {
                System.arraycopy(values, 0, fixed, 0, Math.min(values.length, length));
            }
            return fixed;
        }

        public long average(CityStatement.Line line) {
            return average[line.ordinal()];
        }

        public long latest(CityStatement.Line line) {
            return latest[line.ordinal()];
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeVarLong(cityId);
            buf.writeUtf(name, MAX_NAME);
            buf.writeBoolean(canManage);
            buf.writeVarLong(balance);
            buf.writeVarLong(granted);
            buf.writeVarLong(committed);
            own.encode(buf);
            effective.encode(buf);
            for (long value : average) {
                buf.writeVarLong(value);
            }
            for (long value : latest) {
                buf.writeVarLong(value);
            }
            buf.writeVarInt(days);
            buf.writeVarInt(evasion);
            for (int value : means) {
                buf.writeInt(value);
            }
            buf.writeBoolean(head);
            buf.writeVarLong(crownFunds);
            buf.writeVarInt(shops.size());
            for (ShopRow shop : shops) {
                shop.encode(buf);
            }
            buf.writeVarInt(shopCount);
            buf.writeEnum(blocked);
            buf.writeVarLong(shortfall);
            buf.writeUtf(missing, MAX_NAME * 4);
        }

        public static CityDetail decode(FriendlyByteBuf buf) {
            UUID kingdom = buf.readUUID();
            long city = buf.readVarLong();
            String name = buf.readUtf(MAX_NAME);
            boolean canManage = buf.readBoolean();
            long balance = amount(buf);
            long granted = amount(buf);
            long committed = amount(buf);
            Taxes own = Taxes.decode(buf);
            Taxes effective = Taxes.decode(buf);
            long[] average = new long[CityStatement.LINES];
            for (int i = 0; i < average.length; i++) {
                average[i] = amount(buf);
            }
            long[] latest = new long[CityStatement.LINES];
            for (int i = 0; i < latest.length; i++) {
                latest[i] = amount(buf);
            }
            int days = buf.readVarInt();
            int evasion = buf.readVarInt();
            int[] means = new int[5];
            for (int i = 0; i < means.length; i++) {
                means[i] = buf.readInt();
            }
            boolean head = buf.readBoolean();
            long crownFunds = amount(buf);
            int size = size(buf, MAX_SHOPS);
            List<ShopRow> shops = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                shops.add(ShopRow.decode(buf));
            }
            return new CityDetail(kingdom, city, name, canManage, balance, granted, committed, own, effective, average, latest, days, evasion, means,
                    head, crownFunds, shops, buf.readVarInt(), KingdomView.readEnum(buf, Blocked.class), amount(buf), buf.readUtf(MAX_NAME * 4));
        }
    }

    static long amount(FriendlyByteBuf buf) {
        long value = buf.readVarLong();
        if (!Money.isAmount(value)) {
            throw new DecoderException("Amount out of range: " + value);
        }
        return value;
    }

    static long signed(FriendlyByteBuf buf) {
        long value = buf.readLong();
        if (Math.abs(value) > Money.MAX) {
            throw new DecoderException("Amount out of range: " + value);
        }
        return value;
    }

    static int permille(FriendlyByteBuf buf) {
        int value = buf.readVarInt() - 1;
        if (value < -1 || value > 1000) {
            throw new DecoderException("Share out of range: " + value);
        }
        return value;
    }

    static int size(FriendlyByteBuf buf, int max) {
        int size = buf.readVarInt();
        if (size < 0 || size > max) {
            throw new DecoderException("List size out of range: " + size);
        }
        return size;
    }
}
