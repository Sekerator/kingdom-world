package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.TradeBook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NBT form of trade (format 7), at the root of the kingdom data:
 *
 * <pre>
 * trade: { nextId,
 *          markets: [ { kingdom, open, foreign, fee, sells: [ { item, price, keep } ], buys: [ { item, price, max, budget } ],
 *                       exports: [ { item, above } ], lots: [ { id, seller, goods, price, time } ],
 *                       imports: [ { id, item, count, cost, ordered, arrival } ],
 *                       day: { day, sold, bought, lotTax, exported, exportIncome, imported, importCost }, spent: { item: money } } ],
 *          deals: [ { id, seller, buyer, proposer, item, count, price, currency, period, status, proposed, expires, next,
 *                     deliveries, failures } ],
 *          caravans: [ { id, deal, seller, buyer, cargo: [ goods ], currency, payment, escrow, dimension, fromX, fromZ, toX, toZ,
 *                        depart, arrive, state } ],
 *          returns: [ { player, goods: [ goods ] } ] }
 * goods: { item, count, data }
 * </pre>
 *
 * <p>Reading is strict: what cannot be understood is reported, so the data becomes read-only instead of losing goods or
 * money.
 */
public final class TradeNbt {
    static final String TRADE = "trade";

    private TradeNbt() {
    }

    // ---------------------------------------------------------------- writing

    public static CompoundTag write(TradeBook trade) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("nextId", trade.peekNextId());
        ListTag markets = new ListTag();
        for (Market market : trade.markets()) {
            markets.add(writeMarket(market));
        }
        tag.put("markets", markets);
        ListTag deals = new ListTag();
        for (Deal deal : trade.deals()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", deal.id());
            entry.putUUID("seller", deal.seller());
            entry.putUUID("buyer", deal.buyer());
            entry.putUUID("proposer", deal.proposer());
            entry.putString("item", deal.item());
            entry.putInt("count", deal.count());
            entry.putLong("price", deal.price());
            entry.putUUID("currency", deal.currency());
            entry.putInt("period", deal.periodDays());
            entry.putString("status", deal.status().id());
            entry.putLong("proposed", deal.proposedTime());
            entry.putLong("expires", deal.expiresTime());
            entry.putLong("next", deal.nextTime());
            entry.putInt("deliveries", deal.deliveries());
            entry.putInt("failures", deal.failures());
            deals.add(entry);
        }
        tag.put("deals", deals);
        ListTag caravans = new ListTag();
        for (Caravan caravan : trade.caravans()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", caravan.id());
            entry.putLong("deal", caravan.dealId());
            entry.putUUID("seller", caravan.seller());
            entry.putUUID("buyer", caravan.buyer());
            entry.put("cargo", writeGoods(caravan.cargo()));
            entry.putUUID("currency", caravan.currency());
            entry.putLong("payment", caravan.payment());
            entry.putLong("escrow", caravan.escrowId());
            entry.putString("dimension", caravan.dimension());
            entry.putInt("fromX", caravan.fromX());
            entry.putInt("fromZ", caravan.fromZ());
            entry.putInt("toX", caravan.toX());
            entry.putInt("toZ", caravan.toZ());
            entry.putLong("depart", caravan.departTime());
            entry.putLong("arrive", caravan.arriveTime());
            entry.putString("state", caravan.state().id());
            caravans.add(entry);
        }
        tag.put("caravans", caravans);
        ListTag returns = new ListTag();
        trade.returns().forEach((player, goods) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.put("goods", writeGoods(goods));
            returns.add(entry);
        });
        tag.put("returns", returns);
        return tag;
    }

    private static CompoundTag writeMarket(Market market) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("kingdom", market.kingdomId());
        tag.putBoolean("open", market.openToOutsiders());
        tag.putBoolean("foreign", market.foreignCurrency());
        tag.putInt("fee", market.exchangeFeePermille());
        ListTag sells = new ListTag();
        for (Market.SellEntry entry : market.sells()) {
            CompoundTag item = new CompoundTag();
            item.putString("item", entry.item());
            item.putLong("price", entry.price());
            item.putInt("keep", entry.keep());
            sells.add(item);
        }
        tag.put("sells", sells);
        ListTag buys = new ListTag();
        for (Market.BuyEntry entry : market.buys()) {
            CompoundTag item = new CompoundTag();
            item.putString("item", entry.item());
            item.putLong("price", entry.price());
            item.putInt("max", entry.max());
            item.putLong("budget", entry.budget());
            buys.add(item);
        }
        tag.put("buys", buys);
        ListTag exports = new ListTag();
        for (Market.ExportRule rule : market.exports()) {
            CompoundTag item = new CompoundTag();
            item.putString("item", rule.item());
            item.putInt("above", rule.above());
            exports.add(item);
        }
        tag.put("exports", exports);
        ListTag lots = new ListTag();
        for (Market.Lot lot : market.lots()) {
            CompoundTag item = new CompoundTag();
            item.putLong("id", lot.id());
            item.putUUID("seller", lot.seller());
            item.put("goods", writeGoods(lot.goods()));
            item.putLong("price", lot.price());
            item.putLong("time", lot.listedTime());
            lots.add(item);
        }
        tag.put("lots", lots);
        ListTag imports = new ListTag();
        for (Market.ImportOrder order : market.imports()) {
            CompoundTag item = new CompoundTag();
            item.putLong("id", order.id());
            item.putString("item", order.item());
            item.putInt("count", order.count());
            item.putLong("cost", order.cost());
            item.putLong("ordered", order.orderedTime());
            item.putLong("arrival", order.arrivalTime());
            imports.add(item);
        }
        tag.put("imports", imports);
        Market.DayStats stats = market.rawStats();
        CompoundTag day = new CompoundTag();
        day.putLong("day", stats.day());
        day.putLong("sold", stats.sold());
        day.putLong("bought", stats.bought());
        day.putLong("lotTax", stats.lotTax());
        day.putInt("exported", stats.exported());
        day.putLong("exportIncome", stats.exportIncome());
        day.putInt("imported", stats.imported());
        day.putLong("importCost", stats.importCost());
        tag.put("day", day);
        CompoundTag spent = new CompoundTag();
        market.spentToday().forEach(spent::putLong);
        tag.put("spent", spent);
        return tag;
    }

    static CompoundTag writeGoods(Goods goods) {
        CompoundTag tag = new CompoundTag();
        tag.putString("item", goods.item());
        tag.putInt("count", goods.count());
        if (goods.hasData()) {
            tag.putString("data", goods.data());
        }
        return tag;
    }

    private static ListTag writeGoods(List<Goods> list) {
        ListTag tag = new ListTag();
        for (Goods goods : list) {
            tag.add(writeGoods(goods));
        }
        return tag;
    }

    // ---------------------------------------------------------------- reading

    /**
     * Reads trade into the state; markets and deals of kingdoms that do not exist are problems.
     *
     * @return what could not be read; empty if everything was
     */
    public static List<String> read(Tag raw, KingdomWorldState state) {
        List<String> problems = new ArrayList<>();
        if (!(raw instanceof CompoundTag tag)) {
            problems.add("'" + TRADE + "' is not a compound");
            return problems;
        }
        TradeBook trade = state.trade();
        long maxId = 0;
        for (CompoundTag entry : compounds(tag, "markets", problems)) {
            try {
                maxId = Math.max(maxId, readMarket(entry, state));
            } catch (RuntimeException e) {
                problems.add("market: " + e.getMessage());
            }
        }
        for (CompoundTag entry : compounds(tag, "deals", problems)) {
            try {
                Deal deal = new Deal(entry.getLong("id"), uuid(entry, "seller"), uuid(entry, "buyer"), uuid(entry, "proposer"),
                        entry.getString("item"), entry.getInt("count"), entry.getLong("price"), uuid(entry, "currency"), entry.getInt("period"),
                        status(entry.getString("status")), entry.getLong("proposed"), entry.getLong("expires"), entry.getLong("next"),
                        entry.getInt("deliveries"), entry.getInt("failures"));
                trade.restoreDeal(deal);
                maxId = Math.max(maxId, deal.id());
            } catch (RuntimeException e) {
                problems.add("deal: " + e.getMessage());
            }
        }
        for (CompoundTag entry : compounds(tag, "caravans", problems)) {
            try {
                if (!entry.contains("cargo", Tag.TAG_LIST)) {
                    throw new IllegalArgumentException("a caravan without cargo");
                }
                Caravan caravan = new Caravan(entry.getLong("id"), entry.getLong("deal"), uuid(entry, "seller"), uuid(entry, "buyer"),
                        readGoods(entry.getList("cargo", Tag.TAG_COMPOUND)), uuid(entry, "currency"), entry.getLong("payment"),
                        entry.getLong("escrow"), entry.getString("dimension"), entry.getInt("fromX"), entry.getInt("fromZ"), entry.getInt("toX"),
                        entry.getInt("toZ"), entry.getLong("depart"), entry.getLong("arrive"), caravanState(entry.getString("state")));
                trade.restoreCaravan(caravan);
                maxId = Math.max(maxId, caravan.id());
            } catch (RuntimeException e) {
                problems.add("caravan: " + e.getMessage());
            }
        }
        for (CompoundTag entry : compounds(tag, "returns", problems)) {
            try {
                trade.restoreReturns(uuid(entry, "player"), readGoods(entry.getList("goods", Tag.TAG_COMPOUND)));
            } catch (RuntimeException e) {
                problems.add("returns: " + e.getMessage());
            }
        }
        trade.restoreNextId(Math.max(tag.getLong("nextId"), maxId + 1));
        return problems;
    }

    /**
     * @return the largest id the market uses
     */
    private static long readMarket(CompoundTag tag, KingdomWorldState state) {
        UUID kingdom = uuid(tag, "kingdom");
        if (state.kingdoms().get(kingdom).isEmpty()) {
            throw new IllegalArgumentException("a market of the unknown kingdom " + kingdom);
        }
        long maxId = 0;
        List<Market.SellEntry> sells = new ArrayList<>();
        for (CompoundTag entry : list(tag, "sells")) {
            sells.add(new Market.SellEntry(entry.getString("item"), entry.getLong("price"), entry.getInt("keep")));
        }
        List<Market.BuyEntry> buys = new ArrayList<>();
        for (CompoundTag entry : list(tag, "buys")) {
            buys.add(new Market.BuyEntry(entry.getString("item"), entry.getLong("price"), entry.getInt("max"), entry.getLong("budget")));
        }
        List<Market.ExportRule> exports = new ArrayList<>();
        for (CompoundTag entry : list(tag, "exports")) {
            exports.add(new Market.ExportRule(entry.getString("item"), entry.getInt("above")));
        }
        List<Market.Lot> lots = new ArrayList<>();
        for (CompoundTag entry : list(tag, "lots")) {
            if (!entry.contains("goods", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("a lot without goods");
            }
            Market.Lot lot = new Market.Lot(entry.getLong("id"), uuid(entry, "seller"), readGoods(entry.getCompound("goods")),
                    entry.getLong("price"), entry.getLong("time"));
            lots.add(lot);
            maxId = Math.max(maxId, lot.id());
        }
        List<Market.ImportOrder> imports = new ArrayList<>();
        for (CompoundTag entry : list(tag, "imports")) {
            Market.ImportOrder order = new Market.ImportOrder(entry.getLong("id"), entry.getString("item"), entry.getInt("count"),
                    entry.getLong("cost"), entry.getLong("ordered"), entry.getLong("arrival"));
            imports.add(order);
            maxId = Math.max(maxId, order.id());
        }
        CompoundTag day = tag.getCompound("day");
        Market.DayStats stats = new Market.DayStats(day.contains("day") ? day.getLong("day") : -1, amount(day, "sold"), amount(day, "bought"),
                amount(day, "lotTax"), Math.max(0, day.getInt("exported")), amount(day, "exportIncome"), Math.max(0, day.getInt("imported")),
                amount(day, "importCost"));
        Map<String, Long> spent = new LinkedHashMap<>();
        CompoundTag spentTag = tag.getCompound("spent");
        for (String item : spentTag.getAllKeys()) {
            spent.put(item, spentTag.getLong(item));
        }
        Market market = state.trade().restoreMarket(kingdom, tag.getInt("fee"));
        market.restore(sells, buys, exports, lots, imports, tag.getBoolean("open"), tag.getBoolean("foreign"), tag.getInt("fee"), stats, spent);
        return maxId;
    }

    private static long amount(CompoundTag tag, String key) {
        long value = tag.getLong(key);
        if (!Money.isAmount(value)) {
            throw new IllegalArgumentException(key + " out of range");
        }
        return value;
    }

    static Goods readGoods(CompoundTag tag) {
        return new Goods(tag.getString("item"), tag.getInt("count"), tag.getString("data"));
    }

    private static List<Goods> readGoods(ListTag list) {
        List<Goods> goods = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            goods.add(readGoods(list.getCompound(i)));
        }
        return goods;
    }

    private static Deal.Status status(String id) {
        for (Deal.Status status : Deal.Status.values()) {
            if (status.id().equals(id)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown deal status '" + id + "'");
    }

    private static Caravan.State caravanState(String id) {
        for (Caravan.State state : Caravan.State.values()) {
            if (state.id().equals(id)) {
                return state;
            }
        }
        throw new IllegalArgumentException("unknown caravan state '" + id + "'");
    }

    private static UUID uuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException("'" + key + "' is missing");
        }
        return tag.getUUID(key);
    }

    private static List<CompoundTag> list(CompoundTag parent, String key) {
        if (!parent.contains(key)) {
            return List.of();
        }
        if (!parent.contains(key, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("'" + key + "' is not a list");
        }
        ListTag list = (ListTag) parent.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("'" + key + "' does not hold compounds");
        }
        List<CompoundTag> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getCompound(i));
        }
        return result;
    }

    private static List<CompoundTag> compounds(CompoundTag parent, String key, List<String> problems) {
        try {
            return list(parent, key);
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
            return List.of();
        }
    }
}
