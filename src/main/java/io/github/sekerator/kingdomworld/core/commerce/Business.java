package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A shop of citizens: where it stands, who keeps it, the goods it bought of the crown with what it paid for each, what it still
 * owes the crown for the goods it got on credit, its margin, its state and the last days of its trade. Its money lies in its
 * till among the purses of the economy; its goods lie only here, not in the storehouses and not in the world.
 */
public final class Business {
    /** The trade of every shop for now: it sells what it bought of the crown. */
    public static final String GENERAL_STORE = "general_store";
    /** Days of trade a shop remembers. */
    public static final int DAYS_KEPT = 8;

    /**
     * Where a shop stands.
     */
    public enum PlaceKind {
        /** A house of a merchant: one shop with room for two keepers. */
        MERCHANT_HOUSE(2, true),
        /** A house of a craftsman: one workshop with room for two keepers; it resells for now. */
        CRAFT_HOUSE(2, true),
        /** A stall of a market of the crown: one keeper. */
        MARKET_STALL(1, false),
        /** A counter a player put up: room for two keepers. */
        COUNTER(2, false);

        private final int room;
        private final boolean house;

        PlaceKind(int room, boolean house) {
            this.room = room;
            this.house = house;
        }

        public int room() {
            return room;
        }

        public boolean house() {
            return house;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<PlaceKind> byId(String id) {
            for (PlaceKind kind : values()) {
                if (kind.id().equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The state of a shop.
     */
    public enum Status {
        TRADING,
        FAMILY,
        /** Nothing to sell. */
        EMPTY,
        /** Closing: it sells what it has and buys nothing more, and the crown buys the rest when it can. */
        CLOSING;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Status> byId(String id) {
            for (Status status : values()) {
                if (status.id().equals(id)) {
                    return Optional.of(status);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The place of a shop: a building (a house, or the stall {@code index} of a market) or the block of a counter.
     */
    public record Place(PlaceKind kind, long building, int index, String dimension, int x, int y, int z) {
        public Place {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(dimension, "dimension");
        }

        /**
         * A key telling places apart: a building and stall, or the position of a counter.
         */
        public String key() {
            return kind == PlaceKind.COUNTER ? "counter/" + dimension + "/" + x + "/" + y + "/" + z : kind.id() + "/" + building + "/" + index;
        }
    }

    /**
     * Goods of one kind the shop holds.
     *
     * @param category  the kind of goods, see {@link GoodsCategory}
     * @param count     how many
     * @param costMilli what one of them cost the shop on average, in thousandths of a coin
     */
    public record Lot(int category, long count, long costMilli) {
        public Lot {
            count = Math.max(0, count);
            costMilli = Math.max(0, costMilli);
        }

        public long value() {
            return ShopPricing.total(costMilli, count);
        }
    }

    /**
     * One day of trade.
     *
     * @param sales     what it sold, without the tax
     * @param soldCost  what the goods it sold cost it
     * @param bought    what it paid the crown for goods
     * @param earned    what each keeper earned of it
     * @param categories what it sold of each kind of goods, at its worth
     */
    public record Day(long sales, long soldCost, long bought, long earned, long[] categories) {
        public Day {
            categories = categories == null || categories.length != GoodsCategory.values().length ? new long[GoodsCategory.values().length]
                    : categories.clone();
        }

        @Override
        public long[] categories() {
            return categories.clone();
        }
    }

    private final long id;
    private final UUID kingdomId;
    private long cityId;
    private final Place place;
    private String trade = GENERAL_STORE;
    private final List<UUID> keepers = new ArrayList<>();
    private boolean family;
    private final Map<String, Lot> goods = new LinkedHashMap<>();
    private long debt;
    private int marginPermille;
    private Status status = Status.TRADING;
    private int poorDays;
    private long opened;
    private long profitDue;
    private final NavigableMap<Long, long[]> days = new TreeMap<>();

    public Business(long id, UUID kingdomId, long cityId, Place place, long opened, int marginPermille) {
        this.id = id;
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.cityId = cityId;
        this.place = Objects.requireNonNull(place, "place");
        this.opened = opened;
        this.marginPermille = Math.max(0, marginPermille);
    }

    public long id() {
        return id;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public long cityId() {
        return cityId;
    }

    public void setCityId(long value) {
        cityId = value;
    }

    public Place place() {
        return place;
    }

    public String trade() {
        return trade;
    }

    public void setTrade(String value) {
        trade = value == null || value.isEmpty() ? GENERAL_STORE : value;
    }

    public List<UUID> keepers() {
        return Collections.unmodifiableList(keepers);
    }

    public void setKeepers(List<UUID> value, boolean familyShop) {
        keepers.clear();
        keepers.addAll(value);
        family = familyShop && value.isEmpty();
    }

    /**
     * Whether the family of the house keeps the shop in the evening, without keepers of its own.
     */
    public boolean family() {
        return family;
    }

    public boolean kept() {
        return family || !keepers.isEmpty();
    }

    public Map<String, Lot> goods() {
        return Collections.unmodifiableMap(goods);
    }

    public long count(String item) {
        Lot lot = goods.get(item);
        return lot == null ? 0 : lot.count();
    }

    /**
     * Goods come in at a cost; the cost of what was there and what came is averaged.
     */
    public void addGoods(String item, int category, long count, long costMilli) {
        if (count <= 0) {
            return;
        }
        Lot before = goods.get(item);
        if (before == null || before.count() == 0) {
            goods.put(item, new Lot(category, count, costMilli));
            return;
        }
        long all = before.count() + count;
        java.math.BigInteger sum = java.math.BigInteger.valueOf(before.count()).multiply(java.math.BigInteger.valueOf(before.costMilli()))
                .add(java.math.BigInteger.valueOf(count).multiply(java.math.BigInteger.valueOf(costMilli)));
        long cost = sum.divide(java.math.BigInteger.valueOf(all)).longValue();
        goods.put(item, new Lot(category, all, cost));
    }

    /**
     * Goods leave the shop: sold, or taken back.
     *
     * @return how many left it
     */
    public long removeGoods(String item, long count) {
        Lot lot = goods.get(item);
        if (lot == null || count <= 0) {
            return 0;
        }
        long taken = Math.min(count, lot.count());
        if (taken >= lot.count()) {
            goods.remove(item);
        } else {
            goods.put(item, new Lot(lot.category(), lot.count() - taken, lot.costMilli()));
        }
        return taken;
    }

    public void clearGoods() {
        goods.clear();
    }

    /**
     * What all its goods cost it.
     */
    public long goodsValue() {
        long sum = 0;
        for (Lot lot : goods.values()) {
            sum = Money.addCapped(sum, lot.value());
        }
        return sum;
    }

    /**
     * What its goods of one kind cost it.
     */
    public long goodsValue(int category) {
        long sum = 0;
        for (Lot lot : goods.values()) {
            if (lot.category() == category) {
                sum = Money.addCapped(sum, lot.value());
            }
        }
        return sum;
    }

    /**
     * What it still owes the crown for goods it got on credit.
     */
    public long debt() {
        return debt;
    }

    public void setDebt(long value) {
        debt = Math.max(0, Math.min(Money.MAX, value));
    }

    public int marginPermille() {
        return marginPermille;
    }

    public void setMarginPermille(int value) {
        marginPermille = Math.max(0, value);
    }

    public Status status() {
        return status;
    }

    public void setStatus(Status value) {
        status = Objects.requireNonNull(value, "status");
    }

    /**
     * Days in a row its keepers earned too little.
     */
    public int poorDays() {
        return poorDays;
    }

    public void setPoorDays(int value) {
        poorDays = Math.max(0, value);
    }

    public long opened() {
        return opened;
    }

    /**
     * What it earned and pays its keepers on the next payday; it lies in its till until then.
     */
    public long profitDue() {
        return profitDue;
    }

    public void setProfitDue(long value) {
        profitDue = Math.max(0, Math.min(Money.MAX, value));
    }

    // ---------------------------------------------------------------- days of trade

    /**
     * Writes the trade of a day.
     */
    public void record(long day, Day trade) {
        long[] values = new long[4 + GoodsCategory.values().length];
        values[0] = trade.sales();
        values[1] = trade.soldCost();
        values[2] = trade.bought();
        values[3] = trade.earned();
        long[] categories = trade.categories();
        System.arraycopy(categories, 0, values, 4, categories.length);
        long[] before = days.get(day);
        if (before != null) {
            for (int i = 0; i < values.length; i++) {
                values[i] = Money.addCapped(values[i], before[i]);
            }
        }
        days.put(day, values);
        while (!days.isEmpty() && days.firstKey() <= day - DAYS_KEPT) {
            days.pollFirstEntry();
        }
    }

    public Optional<Day> day(long day) {
        long[] values = days.get(day);
        if (values == null) {
            return Optional.empty();
        }
        long[] categories = new long[GoodsCategory.values().length];
        System.arraycopy(values, 4, categories, 0, categories.length);
        return Optional.of(new Day(values[0], values[1], values[2], values[3], categories));
    }

    public Map<Long, Day> days() {
        Map<Long, Day> copy = new TreeMap<>();
        for (Long day : days.keySet()) {
            day(day).ifPresent(found -> copy.put(day, found));
        }
        return copy;
    }

    /**
     * The average of what it sold a day over the last week before a day, without the tax.
     */
    public long averageSales(long today) {
        long sum = 0;
        int count = 0;
        for (Map.Entry<Long, long[]> entry : days.subMap(today - 7, true, today, false).entrySet()) {
            sum = Money.addCapped(sum, entry.getValue()[0]);
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    /**
     * The average of what it paid the crown a day over the last week before a day.
     */
    public long averageBought(long today) {
        long sum = 0;
        int count = 0;
        for (Map.Entry<Long, long[]> entry : days.subMap(today - 7, true, today, false).entrySet()) {
            sum = Money.addCapped(sum, entry.getValue()[2]);
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    /**
     * The average worth of the goods of one kind it sold a day over the last week before a day.
     */
    public long averageCategory(long today, int category) {
        long sum = 0;
        int count = 0;
        for (Map.Entry<Long, long[]> entry : days.subMap(today - 7, true, today, false).entrySet()) {
            sum = Money.addCapped(sum, entry.getValue()[4 + category]);
            count++;
        }
        return count == 0 ? -1 : sum / count;
    }

    /**
     * Restores what was read from saved data.
     */
    public void restore(String trade, List<UUID> keepers, boolean family, long debt, int margin, Status status, int poorDays, long profitDue) {
        setTrade(trade);
        this.keepers.clear();
        this.keepers.addAll(keepers);
        this.family = family && keepers.isEmpty();
        setDebt(debt);
        setMarginPermille(margin);
        this.status = status;
        setPoorDays(poorDays);
        setProfitDue(profitDue);
    }

    public void restoreGoods(String item, Lot lot) {
        if (lot.count() > 0) {
            goods.put(item, lot);
        }
    }

    public void restoreDay(long day, long[] values) {
        if (values.length == 4 + GoodsCategory.values().length) {
            days.put(day, values.clone());
        }
    }

    public Map<Long, long[]> rawDays() {
        Map<Long, long[]> copy = new TreeMap<>();
        days.forEach((day, values) -> copy.put(day, values.clone()));
        return copy;
    }
}
