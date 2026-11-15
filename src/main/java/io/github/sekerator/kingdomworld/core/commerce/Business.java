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
 * A shop of citizens: where it stands, who keeps it, its trade, the goods it bought (of the crown or of players) and the goods
 * its masters made with what each cost it, the raw materials of its workshop, what it still owes the crown for the goods it got
 * on credit, its margin, its state and the last days of its trade. Its money lies in its till among the purses of the economy;
 * its goods lie only here, not in the storehouses and not in the world.
 *
 * <p>Goods made by citizens are kept apart from goods bought, under their own key, and never merge with them: the crown buys,
 * and the trading houses export, only what citizens made.
 */
public final class Business {
    /** The trade that sells what it bought of the crown and of players. */
    public static final String GENERAL_STORE = "general_store";
    /** Days of trade a shop remembers. */
    public static final int DAYS_KEPT = 8;
    /** The share of a keeper or a master in what a shop earns, in thousandths. */
    public static final int KEEPER_SHARE = 2_000;
    /** The suffix of the key of goods made by citizens. */
    private static final String MADE = "#made";
    /** The values of a day: sales, sold cost, bought, earned, the kinds, then made, exported and sold to the crown. */
    private static final int DAY_VALUES = 4 + GoodsCategory.values().length + 3;
    /** The values of a day as the first shops wrote them, with five kinds and nothing made. */
    private static final int OLD_DAY_VALUES = 4 + 5;

    /**
     * Where a shop stands.
     */
    public enum PlaceKind {
        /** A house of a merchant: one shop with room for two keepers. */
        MERCHANT_HOUSE(2, true),
        /** A house of a craftsman: one workshop with room for two masters. */
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
     * @param category   the kind of goods, see {@link GoodsCategory}; -1 for raw materials of no kind
     * @param count      how many
     * @param costMilli  what one of them cost the shop in money on average, in thousandths of a coin: what it paid, or the raw
     *                   materials it took
     * @param laborMilli the work of its masters in one, at their usual wage; 0 for goods bought
     * @param made       whether citizens made them
     */
    public record Lot(int category, long count, long costMilli, long laborMilli, boolean made) {
        public Lot {
            count = Math.max(0, count);
            costMilli = Math.max(0, costMilli);
            laborMilli = Math.max(0, laborMilli);
        }

        public Lot(int category, long count, long costMilli) {
            this(category, count, costMilli, 0, false);
        }

        /**
         * What one cost with the work in it: the price is reckoned from it, and the shop never sells below it.
         */
        public long baseMilli() {
            return Money.addCapped(costMilli, laborMilli);
        }

        public long value() {
            return ShopPricing.total(costMilli, count);
        }
    }

    /**
     * The key of goods: the item, with a mark for goods made by citizens.
     */
    public static String key(String item, boolean made) {
        return made ? item + MADE : item;
    }

    /**
     * The item of the key of goods.
     */
    public static String itemOf(String key) {
        return key.endsWith(MADE) ? key.substring(0, key.length() - MADE.length()) : key;
    }

    public static boolean isMade(String key) {
        return key.endsWith(MADE);
    }

    /**
     * One day of trade.
     *
     * @param sales      what it sold, without the tax
     * @param soldCost   what the goods it sold cost it
     * @param bought     what it paid the crown for goods
     * @param earned     what each keeper earned of it
     * @param categories what it sold of each kind of goods, at its worth
     * @param made       the worth of what its masters made
     * @param exported   what came from abroad for what it exported
     * @param toCrown    what the crown paid it for goods
     */
    public record Day(long sales, long soldCost, long bought, long earned, long[] categories, long made, long exported, long toCrown) {
        public Day {
            // Fewer kinds (as the first shops counted) are padded with nothing of the new ones.
            long[] all = new long[GoodsCategory.values().length];
            if (categories != null) {
                System.arraycopy(categories, 0, all, 0, Math.min(categories.length, all.length));
            }
            categories = all;
        }

        public Day(long sales, long soldCost, long bought, long earned, long[] categories) {
            this(sales, soldCost, bought, earned, categories, 0, 0, 0);
        }

        @Override
        public long[] categories() {
            return categories.clone();
        }
    }

    /**
     * Work the crown paid a master for in advance: the items are made in the next day of the city and go into the storehouses,
     * or the money goes back to the crown after a few days.
     *
     * @param id    the number of the commission among those of the kingdom
     * @param order the order of the king it is for, 0 for none
     * @param item  the item
     * @param count how many
     * @param paid  the money held for it, in escrow until it is delivered
     * @param day   the day it was given
     */
    public record Commission(long id, long order, String item, int count, long paid, long day) {
        public Commission {
            Objects.requireNonNull(item, "item");
            count = Math.max(0, count);
            paid = Math.max(0, paid);
        }
    }

    private final long id;
    private final UUID kingdomId;
    private long cityId;
    private final Place place;
    private String trade = GENERAL_STORE;
    private String pinned = "";
    private String nextTrade = "";
    private long tradeSince;
    private final List<UUID> keepers = new ArrayList<>();
    private boolean family;
    private final Map<String, Lot> goods = new LinkedHashMap<>();
    private final Map<String, Lot> inputs = new LinkedHashMap<>();
    private final List<Commission> commissions = new ArrayList<>();
    private long fuelTicks;
    private long fuelCostMilli;
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
        this.tradeSince = opened;
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

    // ---------------------------------------------------------------- the trade

    public String trade() {
        return trade;
    }

    public void setTrade(String value) {
        trade = value == null || value.isEmpty() ? GENERAL_STORE : value;
    }

    /**
     * Takes up a trade on a day: what it sold of the old one no longer tells what it sells of the new.
     */
    public void changeTrade(String value, long day) {
        String before = trade;
        setTrade(value);
        nextTrade = "";
        if (!before.equals(trade)) {
            tradeSince = day;
            days.clear();
        }
    }

    /**
     * The trade a member with the right over citizens fixed for the place, empty for none.
     */
    public String pinned() {
        return pinned;
    }

    public void setPinned(String value) {
        pinned = value == null ? "" : value;
    }

    /**
     * The trade the shop takes up once its goods are sold off, empty for none.
     */
    public String nextTrade() {
        return nextTrade;
    }

    public void setNextTrade(String value) {
        nextTrade = value == null || value.equals(trade) ? "" : value;
    }

    /**
     * The day it took up its trade.
     */
    public long tradeSince() {
        return tradeSince;
    }

    /**
     * Whether its masters make goods: a trade that is neither the general store nor a trading house.
     */
    public boolean crafts() {
        return !GENERAL_STORE.equals(trade) && !Trade.TRADER.equals(trade);
    }

    /**
     * Whether it is a trading house, which buys of the masters of its city and exports.
     */
    public boolean trader() {
        return Trade.TRADER.equals(trade);
    }

    // ---------------------------------------------------------------- keepers

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

    /**
     * The shares of those who earn of the shop, in thousandths, in the order of its keepers: {@value #KEEPER_SHARE} each, or one
     * share for the family of a house.
     */
    public int[] shares() {
        if (family || keepers.isEmpty()) {
            return new int[]{KEEPER_SHARE};
        }
        int[] shares = new int[keepers.size()];
        java.util.Arrays.fill(shares, KEEPER_SHARE);
        return shares;
    }

    /**
     * Splits money by shares in thousandths, the largest remainders first, so that nothing is lost and nothing made.
     */
    public static long[] split(long amount, int[] shares) {
        long[] parts = new long[shares.length];
        if (amount <= 0 || shares.length == 0) {
            return parts;
        }
        long total = 0;
        for (int share : shares) {
            total += Math.max(0, share);
        }
        if (total <= 0) {
            parts[0] = amount;
            return parts;
        }
        long given = 0;
        long[] remainders = new long[shares.length];
        for (int i = 0; i < shares.length; i++) {
            java.math.BigInteger[] division = java.math.BigInteger.valueOf(amount).multiply(java.math.BigInteger.valueOf(Math.max(0, shares[i])))
                    .divideAndRemainder(java.math.BigInteger.valueOf(total));
            parts[i] = division[0].longValue();
            remainders[i] = division[1].longValue();
            given += parts[i];
        }
        while (given < amount) {
            int best = 0;
            for (int i = 1; i < shares.length; i++) {
                if (remainders[i] > remainders[best]) {
                    best = i;
                }
            }
            parts[best]++;
            remainders[best] = -1;
            given++;
        }
        return parts;
    }

    // ---------------------------------------------------------------- goods

    public Map<String, Lot> goods() {
        return Collections.unmodifiableMap(goods);
    }

    /**
     * How many of the goods under a key the shop holds.
     */
    public long count(String key) {
        Lot lot = goods.get(key);
        return lot == null ? 0 : lot.count();
    }

    /**
     * How many of an item the shop holds, bought and made.
     */
    public long countItem(String item) {
        return count(item) + count(key(item, true));
    }

    /**
     * Goods bought come in at a cost; the cost of what was there and what came is averaged.
     */
    public void addGoods(String item, int category, long count, long costMilli) {
        put(goods, item, category, count, costMilli, 0, false);
    }

    /**
     * Goods its masters made come in at the cost of their raw materials and the work in them; kept apart from goods bought.
     */
    public void addMade(String item, int category, long count, long costMilli, long laborMilli) {
        put(goods, key(item, true), category, count, costMilli, laborMilli, true);
    }

    private static void put(Map<String, Lot> into, String key, int category, long count, long costMilli, long laborMilli, boolean made) {
        if (count <= 0) {
            return;
        }
        Lot before = into.get(key);
        if (before == null || before.count() == 0) {
            into.put(key, new Lot(category, count, costMilli, laborMilli, made));
            return;
        }
        long all = before.count() + count;
        into.put(key, new Lot(category, all, average(before.count(), before.costMilli(), count, costMilli, all),
                average(before.count(), before.laborMilli(), count, laborMilli, all), made));
    }

    private static long average(long countA, long valueA, long countB, long valueB, long all) {
        java.math.BigInteger sum = java.math.BigInteger.valueOf(countA).multiply(java.math.BigInteger.valueOf(valueA))
                .add(java.math.BigInteger.valueOf(countB).multiply(java.math.BigInteger.valueOf(valueB)));
        return sum.divide(java.math.BigInteger.valueOf(Math.max(1, all))).min(java.math.BigInteger.valueOf(Money.MAX)).longValue();
    }

    /**
     * Goods leave the shop: sold, or taken back.
     *
     * @return how many left it
     */
    public long removeGoods(String key, long count) {
        return take(goods, key, count);
    }

    private static long take(Map<String, Lot> from, String key, long count) {
        Lot lot = from.get(key);
        if (lot == null || count <= 0) {
            return 0;
        }
        long taken = Math.min(count, lot.count());
        if (taken >= lot.count()) {
            from.remove(key);
        } else {
            from.put(key, new Lot(lot.category(), lot.count() - taken, lot.costMilli(), lot.laborMilli(), lot.made()));
        }
        return taken;
    }

    /**
     * Its goods and its raw materials are gone: bought back, or loot.
     */
    public void clearGoods() {
        goods.clear();
        inputs.clear();
        fuelTicks = 0;
        fuelCostMilli = 0;
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

    // ---------------------------------------------------------------- the raw materials of a workshop

    /**
     * The raw materials its masters make goods of, with what each cost it.
     */
    public Map<String, Lot> inputs() {
        return Collections.unmodifiableMap(inputs);
    }

    public long input(String item) {
        Lot lot = inputs.get(item);
        return lot == null ? 0 : lot.count();
    }

    public void addInput(String item, long count, long costMilli) {
        put(inputs, item, -1, count, costMilli, 0, false);
    }

    /**
     * Raw materials its masters made for a later step come in at what went into them and the work in them.
     */
    public void addInput(String item, long count, long costMilli, long laborMilli) {
        put(inputs, item, -1, count, costMilli, laborMilli, false);
    }

    /**
     * Raw materials are used up or taken back.
     *
     * @return how many left the workshop
     */
    public long removeInput(String item, long count) {
        return take(inputs, item, count);
    }

    /**
     * What all its raw materials cost it.
     */
    public long inputsValue() {
        long sum = 0;
        for (Lot lot : inputs.values()) {
            sum = Money.addCapped(sum, lot.value());
        }
        return sum;
    }

    /**
     * Ticks of burning its furnace still has of fuel already burned.
     */
    public long fuelTicks() {
        return fuelTicks;
    }

    /**
     * What the fuel already burned and not used yet cost it, in thousandths of a coin.
     */
    public long fuelCostMilli() {
        return fuelCostMilli;
    }

    public void setFuel(long ticks, long costMilli) {
        fuelTicks = Math.max(0, ticks);
        fuelCostMilli = fuelTicks == 0 ? 0 : Math.max(0, costMilli);
    }

    // ---------------------------------------------------------------- work paid in advance

    public List<Commission> commissions() {
        return Collections.unmodifiableList(commissions);
    }

    public void addCommission(Commission commission) {
        commissions.add(Objects.requireNonNull(commission, "commission"));
    }

    public void removeCommission(long id) {
        commissions.removeIf(commission -> commission.id() == id);
    }

    /**
     * The money held for its commissions.
     */
    public long commissioned() {
        long sum = 0;
        for (Commission commission : commissions) {
            sum = Money.addCapped(sum, commission.paid());
        }
        return sum;
    }

    // ---------------------------------------------------------------- money

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
        long[] values = new long[DAY_VALUES];
        values[0] = trade.sales();
        values[1] = trade.soldCost();
        values[2] = trade.bought();
        values[3] = trade.earned();
        long[] categories = trade.categories();
        System.arraycopy(categories, 0, values, 4, categories.length);
        int extra = 4 + categories.length;
        values[extra] = trade.made();
        values[extra + 1] = trade.exported();
        values[extra + 2] = trade.toCrown();
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
        int extra = 4 + categories.length;
        return Optional.of(new Day(values[0], values[1], values[2], values[3], categories, values[extra], values[extra + 1], values[extra + 2]));
    }

    /**
     * Whether the shop is just starting out and gets its first goods on credit: it owes nothing, holds nothing and has never bought
     * or sold, though the days it stood empty since it opened are written.
     */
    public boolean startingOut() {
        if (debt != 0 || !goods.isEmpty() || !inputs.isEmpty()) {
            return false;
        }
        for (long[] values : days.values()) {
            if (values[0] != 0 || values[2] != 0) {
                return false;
            }
        }
        return true;
    }

    public Map<Long, Day> days() {
        Map<Long, Day> copy = new TreeMap<>();
        for (Long day : days.keySet()) {
            day(day).ifPresent(found -> copy.put(day, found));
        }
        return copy;
    }

    private long average(long today, int index) {
        long sum = 0;
        int count = 0;
        for (Map.Entry<Long, long[]> entry : days.subMap(today - 7, true, today, false).entrySet()) {
            sum = Money.addCapped(sum, entry.getValue()[index]);
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    /**
     * The average of what it sold a day over the last week before a day, without the tax.
     */
    public long averageSales(long today) {
        return average(today, 0);
    }

    /**
     * The average of what it paid the crown a day over the last week before a day.
     */
    public long averageBought(long today) {
        return average(today, 2);
    }

    /**
     * The average worth of what its masters made a day over the last week before a day.
     */
    public long averageMade(long today) {
        return average(today, 4 + GoodsCategory.values().length);
    }

    /**
     * The average of what came from abroad for its exports a day over the last week before a day.
     */
    public long averageExported(long today) {
        return average(today, 4 + GoodsCategory.values().length + 1);
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
        nextTrade = "";
        this.keepers.clear();
        this.keepers.addAll(keepers);
        this.family = family && keepers.isEmpty();
        setDebt(debt);
        setMarginPermille(margin);
        this.status = status;
        setPoorDays(poorDays);
        setProfitDue(profitDue);
    }

    /**
     * Restores the trade the place was fixed to, the trade it takes up next and the day it took up its own.
     */
    public void restoreTrade(String pinned, String nextTrade, long tradeSince) {
        setPinned(pinned);
        setNextTrade(nextTrade);
        this.tradeSince = tradeSince;
    }

    public void restoreGoods(String key, Lot lot) {
        if (lot.count() > 0) {
            goods.put(key, lot);
        }
    }

    public void restoreInput(String item, Lot lot) {
        if (lot.count() > 0) {
            inputs.put(item, lot);
        }
    }

    /**
     * Restores a day; a day of the first shops, with five kinds of goods and nothing made, gets the new values as 0.
     */
    public void restoreDay(long day, long[] values) {
        if (values.length == DAY_VALUES) {
            days.put(day, values.clone());
        } else if (values.length == OLD_DAY_VALUES) {
            long[] padded = new long[DAY_VALUES];
            System.arraycopy(values, 0, padded, 0, values.length);
            days.put(day, padded);
        }
    }

    public Map<Long, long[]> rawDays() {
        Map<Long, long[]> copy = new TreeMap<>();
        days.forEach((day, values) -> copy.put(day, values.clone()));
        return copy;
    }
}
