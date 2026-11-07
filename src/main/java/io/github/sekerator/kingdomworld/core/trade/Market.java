package io.github.sekerator.kingdomworld.core.trade;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The market of a kingdom: what it sells to players from its storehouses, what it buys from them, the lots players put
 * up for sale, what it sells abroad and what it ordered from abroad, with the counters of the current game day.
 *
 * <p>Changed through {@link TradeBook} and {@link io.github.sekerator.kingdomworld.core.state.KingdomWorldState}; every
 * change notifies the listener so that the data is saved.
 */
public final class Market {
    /** Largest number in a setting of an entry: how many to keep, buy or export. */
    public static final int MAX_COUNT = 1_000_000;

    /**
     * An item the kingdom sells from its storehouses.
     *
     * @param price the king's price per item, or 0 for the auto price
     * @param keep  items that always stay in the storehouses
     */
    public record SellEntry(String item, long price, int keep) {
        public SellEntry {
            check(item);
            if (!Money.isAmount(price) || keep < 0 || keep > MAX_COUNT) {
                throw new IllegalArgumentException("Sell entry out of range: " + item);
            }
        }
    }

    /**
     * An item the kingdom buys from players.
     *
     * @param price  the king's price per item, or 0 for the auto price
     * @param max    the kingdom buys while its storehouses hold fewer than this
     * @param budget most money the kingdom pays for it in a game day
     */
    public record BuyEntry(String item, long price, int max, long budget) {
        public BuyEntry {
            check(item);
            if (!Money.isAmount(price) || max < 1 || max > MAX_COUNT || !Money.isAmount(budget)) {
                throw new IllegalArgumentException("Buy entry out of range: " + item);
            }
        }
    }

    /**
     * Items the merchants sell abroad: whatever the storehouses hold above {@code above}.
     */
    public record ExportRule(String item, int above) {
        public ExportRule {
            check(item);
            if (above < 0 || above > MAX_COUNT) {
                throw new IllegalArgumentException("Export rule out of range: " + item);
            }
        }
    }

    /**
     * Goods a player put up for sale; the market keeps them until they are sold or taken back.
     *
     * @param price what the whole lot costs
     */
    public record Lot(long id, UUID seller, Goods goods, long price, long listedTime) {
        public Lot {
            Objects.requireNonNull(seller, "seller");
            Objects.requireNonNull(goods, "goods");
            if (!Money.isPayment(price)) {
                throw new IllegalArgumentException("Lot price out of range: " + price);
            }
        }
    }

    /**
     * Items ordered from abroad, paid for when ordered.
     *
     * @param cost        what was paid
     * @param arrivalTime game time they arrive at
     */
    public record ImportOrder(long id, String item, int count, long cost, long orderedTime, long arrivalTime) {
        public ImportOrder {
            check(item);
            if (count < 1 || count > MAX_COUNT || !Money.isAmount(cost)) {
                throw new IllegalArgumentException("Import order out of range: " + item);
            }
        }
    }

    /**
     * What happened on the market on one game day.
     *
     * @param sold        money players paid the kingdom for its goods
     * @param bought      money the kingdom paid players for theirs
     * @param lotTax      tax the treasury took from the lots of players
     * @param exported    items sold abroad
     * @param exportIncome money they brought in
     * @param imported    items ordered from abroad
     * @param importCost  money paid for them
     */
    public record DayStats(long day, long sold, long bought, long lotTax, int exported, long exportIncome, int imported, long importCost) {
        public static DayStats empty(long day) {
            return new DayStats(day, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private static void check(String item) {
        if (item == null || item.isEmpty() || item.length() > Goods.MAX_ITEM) {
            throw new IllegalArgumentException("Bad item id");
        }
    }

    private final UUID kingdomId;
    private final List<SellEntry> sells = new ArrayList<>();
    private final List<BuyEntry> buys = new ArrayList<>();
    private final List<ExportRule> exports = new ArrayList<>();
    private final List<Lot> lots = new ArrayList<>();
    private final List<ImportOrder> imports = new ArrayList<>();
    /** Money paid today for each item the kingdom buys. */
    private final Map<String, Long> spentToday = new LinkedHashMap<>();
    private boolean openToOutsiders = true;
    private boolean foreignCurrency;
    private int exchangeFeePermille;
    private DayStats today = DayStats.empty(-1);
    private Runnable changeListener = () -> {
    };

    public Market(UUID kingdomId, int exchangeFeePermille) {
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.exchangeFeePermille = Math.max(0, Math.min(1000, exchangeFeePermille));
    }

    void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    private void changed() {
        changeListener.run();
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public List<SellEntry> sells() {
        return Collections.unmodifiableList(sells);
    }

    public List<BuyEntry> buys() {
        return Collections.unmodifiableList(buys);
    }

    public List<ExportRule> exports() {
        return Collections.unmodifiableList(exports);
    }

    public List<Lot> lots() {
        return Collections.unmodifiableList(lots);
    }

    public List<ImportOrder> imports() {
        return Collections.unmodifiableList(imports);
    }

    public boolean openToOutsiders() {
        return openToOutsiders;
    }

    public boolean foreignCurrency() {
        return foreignCurrency;
    }

    public int exchangeFeePermille() {
        return exchangeFeePermille;
    }

    public Optional<SellEntry> sell(String item) {
        return sells.stream().filter(entry -> entry.item().equals(item)).findFirst();
    }

    public Optional<BuyEntry> buy(String item) {
        return buys.stream().filter(entry -> entry.item().equals(item)).findFirst();
    }

    public Optional<Lot> lot(long id) {
        return lots.stream().filter(lot -> lot.id() == id).findFirst();
    }

    public int lotsOf(UUID player) {
        return (int) lots.stream().filter(lot -> lot.seller().equals(player)).count();
    }

    /**
     * The counters of a game day; a new day starts at zero.
     */
    public DayStats stats(long day) {
        return today.day() == day ? today : DayStats.empty(day);
    }

    /**
     * Money paid today for an item the kingdom buys.
     */
    public long spent(String item, long day) {
        return today.day() == day ? spentToday.getOrDefault(item, 0L) : 0L;
    }

    private void roll(long day) {
        if (today.day() != day) {
            today = DayStats.empty(day);
            spentToday.clear();
        }
    }

    // ---------------------------------------------------------------- changes

    /**
     * Adds an item to what the kingdom sells, or changes its entry.
     *
     * @return whether a new entry was added; {@code false} also when the list is full
     */
    boolean putSell(SellEntry entry, int maxEntries) {
        for (int i = 0; i < sells.size(); i++) {
            if (sells.get(i).item().equals(entry.item())) {
                sells.set(i, entry);
                changed();
                return true;
            }
        }
        if (sells.size() >= maxEntries) {
            return false;
        }
        sells.add(entry);
        changed();
        return true;
    }

    boolean putBuy(BuyEntry entry, int maxEntries) {
        for (int i = 0; i < buys.size(); i++) {
            if (buys.get(i).item().equals(entry.item())) {
                buys.set(i, entry);
                changed();
                return true;
            }
        }
        if (buys.size() >= maxEntries) {
            return false;
        }
        buys.add(entry);
        changed();
        return true;
    }

    boolean putExport(ExportRule rule, int maxEntries) {
        for (int i = 0; i < exports.size(); i++) {
            if (exports.get(i).item().equals(rule.item())) {
                exports.set(i, rule);
                changed();
                return true;
            }
        }
        if (exports.size() >= maxEntries) {
            return false;
        }
        exports.add(rule);
        changed();
        return true;
    }

    boolean removeSell(String item) {
        boolean removed = sells.removeIf(entry -> entry.item().equals(item));
        if (removed) {
            changed();
        }
        return removed;
    }

    boolean removeBuy(String item) {
        boolean removed = buys.removeIf(entry -> entry.item().equals(item));
        if (removed) {
            changed();
        }
        return removed;
    }

    boolean removeExport(String item) {
        boolean removed = exports.removeIf(rule -> rule.item().equals(item));
        if (removed) {
            changed();
        }
        return removed;
    }

    void setOpenToOutsiders(boolean value) {
        if (openToOutsiders != value) {
            openToOutsiders = value;
            changed();
        }
    }

    void setForeignCurrency(boolean value) {
        if (foreignCurrency != value) {
            foreignCurrency = value;
            changed();
        }
    }

    void setExchangeFee(int permille) {
        if (exchangeFeePermille != permille) {
            exchangeFeePermille = permille;
            changed();
        }
    }

    void addLot(Lot lot) {
        lots.add(lot);
        changed();
    }

    Optional<Lot> removeLot(long id) {
        for (int i = 0; i < lots.size(); i++) {
            if (lots.get(i).id() == id) {
                Lot lot = lots.remove(i);
                changed();
                return Optional.of(lot);
            }
        }
        return Optional.empty();
    }

    void addImport(ImportOrder order) {
        imports.add(order);
        changed();
    }

    Optional<ImportOrder> removeImport(long id) {
        for (int i = 0; i < imports.size(); i++) {
            if (imports.get(i).id() == id) {
                ImportOrder order = imports.remove(i);
                changed();
                return Optional.of(order);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- counters

    void recordSale(long day, long money) {
        roll(day);
        today = new DayStats(day, Money.addCapped(today.sold(), money), today.bought(), today.lotTax(), today.exported(),
                today.exportIncome(), today.imported(), today.importCost());
        changed();
    }

    void recordPurchase(long day, String item, long money) {
        roll(day);
        spentToday.merge(item, money, Money::addCapped);
        today = new DayStats(day, today.sold(), Money.addCapped(today.bought(), money), today.lotTax(), today.exported(),
                today.exportIncome(), today.imported(), today.importCost());
        changed();
    }

    void recordLotTax(long day, long tax) {
        roll(day);
        today = new DayStats(day, today.sold(), today.bought(), Money.addCapped(today.lotTax(), tax), today.exported(),
                today.exportIncome(), today.imported(), today.importCost());
        changed();
    }

    void recordExport(long day, int items, long income) {
        roll(day);
        today = new DayStats(day, today.sold(), today.bought(), today.lotTax(), (int) Math.min(Integer.MAX_VALUE, (long) today.exported() + items),
                Money.addCapped(today.exportIncome(), income), today.imported(), today.importCost());
        changed();
    }

    void recordImport(long day, int items, long cost) {
        roll(day);
        today = new DayStats(day, today.sold(), today.bought(), today.lotTax(), today.exported(), today.exportIncome(),
                (int) Math.min(Integer.MAX_VALUE, (long) today.imported() + items), Money.addCapped(today.importCost(), cost));
        changed();
    }

    // ---------------------------------------------------------------- saved data

    /**
     * Restores the market from saved data. Does not notify.
     */
    public void restore(List<SellEntry> sells, List<BuyEntry> buys, List<ExportRule> exports, List<Lot> lots, List<ImportOrder> imports,
                        boolean openToOutsiders, boolean foreignCurrency, int exchangeFeePermille, DayStats today, Map<String, Long> spent) {
        this.sells.clear();
        this.sells.addAll(sells);
        this.buys.clear();
        this.buys.addAll(buys);
        this.exports.clear();
        this.exports.addAll(exports);
        this.lots.clear();
        this.lots.addAll(lots);
        this.imports.clear();
        this.imports.addAll(imports);
        this.openToOutsiders = openToOutsiders;
        this.foreignCurrency = foreignCurrency;
        if (exchangeFeePermille < 0 || exchangeFeePermille > 1000) {
            throw new IllegalArgumentException("Exchange fee out of range");
        }
        this.exchangeFeePermille = exchangeFeePermille;
        this.today = Objects.requireNonNull(today, "today");
        this.spentToday.clear();
        spent.forEach((item, money) -> {
            if (!Money.isAmount(money)) {
                throw new IllegalArgumentException("Spent money out of range");
            }
            this.spentToday.put(item, money);
        });
    }

    /**
     * Money spent today by item, for saving.
     */
    public Map<String, Long> spentToday() {
        return Collections.unmodifiableMap(spentToday);
    }

    /**
     * The counters as they are, for saving.
     */
    public DayStats rawStats() {
        return today;
    }
}
