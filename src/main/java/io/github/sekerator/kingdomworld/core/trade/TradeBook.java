package io.github.sekerator.kingdomworld.core.trade;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Trade of one world: the market of every kingdom, the deals between kingdoms, the caravans on the way, and goods waiting
 * to go back to players (lots of a market that closed). Money is not kept here: payments of deals wait in escrow in the
 * {@link io.github.sekerator.kingdomworld.core.economy.Economy}.
 *
 * <p>Rights are checked by {@link io.github.sekerator.kingdomworld.core.state.KingdomWorldState}; this class checks the
 * rules of the market itself. Not thread safe; the server uses it from the server thread only.
 */
public final class TradeBook {
    /** Game ticks a closed deal stays in the lists. */
    public static final long CLOSED_DEAL_TICKS = 2L * 24_000L;

    private final Map<UUID, Market> markets = new LinkedHashMap<>();
    private final Map<Long, Deal> deals = new LinkedHashMap<>();
    private final Map<Long, Caravan> caravans = new LinkedHashMap<>();
    private final Map<UUID, List<Goods>> returns = new LinkedHashMap<>();
    private long nextId = 1;
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
        markets.values().forEach(market -> market.setChangeListener(listener));
    }

    private void changed() {
        changeListener.run();
    }

    public long nextId() {
        return nextId++;
    }

    public long peekNextId() {
        return nextId;
    }

    // ---------------------------------------------------------------- markets

    /**
     * The market of a kingdom, opened with the default exchange fee on first use.
     */
    public Market market(UUID kingdomId, TradeRules rules) {
        Market market = markets.get(kingdomId);
        if (market == null) {
            market = new Market(kingdomId, rules.defaultExchangeFeePercent() * 10);
            market.setChangeListener(changeListener);
            markets.put(kingdomId, market);
            changed();
        }
        return market;
    }

    public Optional<Market> existing(UUID kingdomId) {
        return Optional.ofNullable(markets.get(kingdomId));
    }

    public Collection<Market> markets() {
        return Collections.unmodifiableCollection(markets.values());
    }

    public Outcome putSell(UUID kingdomId, Market.SellEntry entry, TradeRules rules) {
        return market(kingdomId, rules).putSell(entry, rules.maxEntries()) ? Outcome.DONE : Outcome.fail(KingdomError.LIST_FULL);
    }

    public Outcome putBuy(UUID kingdomId, Market.BuyEntry entry, TradeRules rules) {
        return market(kingdomId, rules).putBuy(entry, rules.maxEntries()) ? Outcome.DONE : Outcome.fail(KingdomError.LIST_FULL);
    }

    public Outcome putExport(UUID kingdomId, Market.ExportRule rule, TradeRules rules) {
        return market(kingdomId, rules).putExport(rule, rules.maxEntries()) ? Outcome.DONE : Outcome.fail(KingdomError.LIST_FULL);
    }

    public Outcome removeSell(UUID kingdomId, String item) {
        Market market = markets.get(kingdomId);
        return market != null && market.removeSell(item) ? Outcome.DONE : Outcome.fail(KingdomError.UNKNOWN_ENTRY);
    }

    public Outcome removeBuy(UUID kingdomId, String item) {
        Market market = markets.get(kingdomId);
        return market != null && market.removeBuy(item) ? Outcome.DONE : Outcome.fail(KingdomError.UNKNOWN_ENTRY);
    }

    public Outcome removeExport(UUID kingdomId, String item) {
        Market market = markets.get(kingdomId);
        return market != null && market.removeExport(item) ? Outcome.DONE : Outcome.fail(KingdomError.UNKNOWN_ENTRY);
    }

    public Outcome setOpenToOutsiders(UUID kingdomId, boolean open, TradeRules rules) {
        Market market = market(kingdomId, rules);
        if (market.openToOutsiders() == open) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        market.setOpenToOutsiders(open);
        return Outcome.DONE;
    }

    public Outcome setForeignCurrency(UUID kingdomId, boolean allowed, TradeRules rules) {
        Market market = market(kingdomId, rules);
        if (market.foreignCurrency() == allowed) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        market.setForeignCurrency(allowed);
        return Outcome.DONE;
    }

    public Outcome setExchangeFee(UUID kingdomId, int permille, TradeRules rules) {
        if (permille < 0 || permille > rules.maxExchangeFeePercent() * 10) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        Market market = market(kingdomId, rules);
        if (market.exchangeFeePermille() == permille) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        market.setExchangeFee(permille);
        return Outcome.DONE;
    }

    /**
     * Puts the goods of a player up for sale; the caller took them from the player in the same moment.
     */
    public Outcome addLot(UUID kingdomId, UUID seller, Goods goods, long price, long time, TradeRules rules) {
        Market market = market(kingdomId, rules);
        if (market.lotsOf(seller) >= rules.maxLotsPerPlayer()) {
            return Outcome.fail(KingdomError.TOO_MANY_LOTS);
        }
        if (price < 1 || price > io.github.sekerator.kingdomworld.core.economy.Money.MAX) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        market.addLot(new Market.Lot(nextId(), seller, goods, price, time));
        return Outcome.DONE;
    }

    /**
     * Takes a lot off the market, sold or taken back.
     */
    public Optional<Market.Lot> takeLot(UUID kingdomId, long id) {
        Market market = markets.get(kingdomId);
        return market == null ? Optional.empty() : market.removeLot(id);
    }

    public Market.ImportOrder addImport(UUID kingdomId, String item, int count, long cost, long time, long arrival, TradeRules rules) {
        Market.ImportOrder order = new Market.ImportOrder(nextId(), item, count, cost, time, arrival);
        market(kingdomId, rules).addImport(order);
        return order;
    }

    public Optional<Market.ImportOrder> takeImport(UUID kingdomId, long id) {
        Market market = markets.get(kingdomId);
        return market == null ? Optional.empty() : market.removeImport(id);
    }

    public void recordSale(UUID kingdomId, long day, long money, TradeRules rules) {
        market(kingdomId, rules).recordSale(day, money);
    }

    public void recordPurchase(UUID kingdomId, long day, String item, long money, TradeRules rules) {
        market(kingdomId, rules).recordPurchase(day, item, money);
    }

    public void recordLotTax(UUID kingdomId, long day, long tax, TradeRules rules) {
        market(kingdomId, rules).recordLotTax(day, tax);
    }

    public void recordExport(UUID kingdomId, long day, int items, long income, TradeRules rules) {
        market(kingdomId, rules).recordExport(day, items, income);
    }

    public void recordImport(UUID kingdomId, long day, int items, long cost, TradeRules rules) {
        market(kingdomId, rules).recordImport(day, items, cost);
    }

    // ---------------------------------------------------------------- deals

    public Optional<Deal> deal(long id) {
        return Optional.ofNullable(deals.get(id));
    }

    public Collection<Deal> deals() {
        return Collections.unmodifiableCollection(deals.values());
    }

    /**
     * Deals and proposals of a kingdom, the newest first.
     */
    public List<Deal> dealsOf(UUID kingdomId) {
        List<Deal> result = new ArrayList<>();
        for (Deal deal : deals.values()) {
            if (deal.involves(kingdomId)) {
                result.add(deal);
            }
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Deals of a kingdom that are not over yet.
     */
    public int openDeals(UUID kingdomId) {
        int count = 0;
        for (Deal deal : deals.values()) {
            if (deal.involves(kingdomId) && !deal.status().closed()) {
                count++;
            }
        }
        return count;
    }

    public Deal addDeal(UUID seller, UUID buyer, UUID proposer, String item, int count, long price, UUID currency, int periodDays,
                        long time, long expires) {
        Deal deal = new Deal(nextId(), seller, buyer, proposer, item, count, price, currency, periodDays, Deal.Status.PROPOSED, time,
                expires, time, 0, 0);
        deals.put(deal.id(), deal);
        changed();
        return deal;
    }

    public void putDeal(Deal deal) {
        if (!deal.equals(deals.put(deal.id(), deal))) {
            changed();
        }
    }

    /**
     * Forgets deals that were over long enough ago.
     */
    public void prune(long now) {
        boolean removed = deals.values().removeIf(deal -> deal.status().closed() && deal.nextTime() + CLOSED_DEAL_TICKS < now);
        if (removed) {
            changed();
        }
    }

    // ---------------------------------------------------------------- caravans

    public Optional<Caravan> caravan(long id) {
        return Optional.ofNullable(caravans.get(id));
    }

    public Collection<Caravan> caravans() {
        return Collections.unmodifiableCollection(caravans.values());
    }

    public void putCaravan(Caravan caravan) {
        if (!caravan.equals(caravans.put(caravan.id(), caravan))) {
            changed();
        }
    }

    public Optional<Caravan> removeCaravan(long id) {
        Caravan removed = caravans.remove(id);
        if (removed != null) {
            changed();
        }
        return Optional.ofNullable(removed);
    }

    // ---------------------------------------------------------------- goods going back to players

    /**
     * Goods waiting to go back to a player, for example the lots of a market whose kingdom was dissolved.
     */
    public void addReturn(UUID player, Goods goods) {
        returns.computeIfAbsent(player, p -> new ArrayList<>()).add(goods);
        changed();
    }

    public List<Goods> returnsOf(UUID player) {
        return Collections.unmodifiableList(returns.getOrDefault(player, List.of()));
    }

    public Map<UUID, List<Goods>> returns() {
        return Collections.unmodifiableMap(returns);
    }

    /**
     * Takes the goods waiting for a player; the caller hands them over in the same moment.
     */
    public List<Goods> takeReturns(UUID player) {
        List<Goods> taken = returns.remove(player);
        if (taken != null) {
            changed();
        }
        return taken == null ? List.of() : taken;
    }

    // ---------------------------------------------------------------- kingdoms that go away

    /**
     * A kingdom was dissolved: its market closes and the lots on it wait for their sellers, its import orders are lost,
     * and its open deals end.
     */
    public void closeMarket(UUID kingdomId) {
        Market market = markets.remove(kingdomId);
        if (market != null) {
            for (Market.Lot lot : market.lots()) {
                returns.computeIfAbsent(lot.seller(), p -> new ArrayList<>()).add(lot.goods());
            }
        }
        for (Deal deal : new ArrayList<>(deals.values())) {
            if (deal.involves(kingdomId) && !deal.status().closed()) {
                deals.put(deal.id(), deal.withStatus(Deal.Status.CANCELLED));
            }
        }
        changed();
    }

    /**
     * Forgets everything of a kingdom whose saved data could not be restored.
     */
    public void forget(UUID kingdomId) {
        markets.remove(kingdomId);
    }

    // ---------------------------------------------------------------- saved data

    public Market restoreMarket(UUID kingdomId, int exchangeFeePermille) {
        if (markets.containsKey(kingdomId)) {
            throw new IllegalArgumentException("Two markets for " + kingdomId);
        }
        Market market = new Market(kingdomId, exchangeFeePermille);
        market.setChangeListener(changeListener);
        markets.put(kingdomId, market);
        return market;
    }

    public void restoreDeal(Deal deal) {
        if (deals.containsKey(deal.id())) {
            throw new IllegalArgumentException("Two deals with the id " + deal.id());
        }
        deals.put(deal.id(), deal);
    }

    public void restoreCaravan(Caravan caravan) {
        if (caravans.containsKey(caravan.id())) {
            throw new IllegalArgumentException("Two caravans with the id " + caravan.id());
        }
        caravans.put(caravan.id(), caravan);
    }

    public void restoreReturns(UUID player, List<Goods> goods) {
        if (!goods.isEmpty()) {
            returns.computeIfAbsent(player, p -> new ArrayList<>()).addAll(goods);
        }
    }

    public void restoreNextId(long value) {
        nextId = Math.max(nextId, value);
    }
}
