package io.github.sekerator.kingdomworld.common.commerce;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.StallActions;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.CraftRules;
import io.github.sekerator.kingdomworld.core.commerce.CrownPurchase;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import io.github.sekerator.kingdomworld.core.commerce.ShopMaking;
import io.github.sekerator.kingdomworld.core.commerce.ShopPricing;
import io.github.sekerator.kingdomworld.core.commerce.Trade;
import io.github.sekerator.kingdomworld.core.economy.CityLedger;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.production.OrderBook;
import io.github.sekerator.kingdomworld.core.production.ProductionPlanner;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.Prices;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What the crown buys of the workshops of its citizens. Once a kingdom step, after its orders are planned, the crown buys what its
 * own workshops cannot make: an order or a sub-order without a workshop or short of raw materials, an order to make that did not
 * move for a whole day (the orders of constructions and of works far away among them). It takes the cheapest goods citizens made,
 * in the shops of any city of the kingdom, never above the price it allows and never beyond its sum of the day, and never so
 * that its treasury falls below the days of wages it keeps; it pays the price of the shop without the tax into the till, and the
 * items go into the storehouse nearest the shop. Where none are made, a master who can make the item is paid in advance: the money
 * waits in escrow, the master makes it in the next day of its city and delivers, or the money goes back after a few days.
 *
 * <p>Shops with more goods made by citizens than a few days of their sales sell them to the buy list of the market of the king, at
 * his price, when it pays at least what they cost and a little more. What a shop bought of the crown never goes back to it.
 */
public final class CrownPurchases {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks an order to make may stand still before the crown buys it of citizens: a day. */
    static final long STILL_TICKS = 24_000L;
    /** Since when each order made nothing, by kingdom and order: { made, since }. */
    private static final Map<String, long[]> STILL = new HashMap<>();

    private CrownPurchases() {
    }

    public static void reset() {
        STILL.clear();
    }

    // ---------------------------------------------------------------- what the kingdom set

    /**
     * What a kingdom set for buying of its citizens, else the config.
     */
    public static OrderBook.Buying buying(MinecraftServer server, UUID kingdom) {
        CraftRules rules = CraftSettings.rules();
        return KingdomWorldApi.state(server).orders().buying(kingdom)
                .orElse(new OrderBook.Buying(rules.crownBuysFromCitizens(), rules.crownBuyCapPercent(), rules.crownBuyPerDay()));
    }

    /**
     * A member with the right over production sets the buying of citizens.
     */
    public static KingdomActions.Feedback set(MinecraftServer server, UUID actor, UUID kingdom, boolean on, int capPercent, long perDay) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        if (actor != null && !KingdomWorldApi.state(server).membership().hasPermission(actor, kingdom, Permission.MANAGE_PRODUCTION)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        if (capPercent < CraftRules.MIN_BUY_CAP_PERCENT || capPercent > CraftRules.MAX_BUY_CAP_PERCENT || perDay < 0 || perDay > Money.MAX) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        KingdomWorldApi.state(server).orders().setBuying(kingdom, new OrderBook.Buying(on, capPercent, perDay));
        return KingdomActions.Feedback.ok("kingdomworld.orders.buy_from_citizens.set");
    }

    /**
     * What the treasury of a kingdom keeps out of the purchases: days of its wages.
     */
    static long keep(MinecraftServer server, UUID kingdom) {
        long wages = KingdomWorldApi.state(server).economy().treasury(kingdom).map(Treasury::lastPayroll).map(summary -> summary.due()).orElse(0L);
        return Money.multiplyCapped(CraftSettings.rules().crownBuyKeepWageDays(), wages);
    }

    /**
     * The money the crown may spend on goods of citizens now.
     */
    static long budget(MinecraftServer server, UUID kingdom, long day) {
        long funds = KingdomWorldApi.state(server).economy().treasury(kingdom).map(Treasury::funds).orElse(0L);
        long[] today = CommerceSavedData.get(server).crownDay(kingdom, day);
        return CrownPurchase.budget(funds, keep(server, kingdom), buying(server, kingdom).perDay(), today[0]);
    }

    // ---------------------------------------------------------------- the step of a kingdom

    /**
     * The crown buys of its citizens what its workshops cannot make, and the shops sell to its buy list; once a kingdom step,
     * after the orders are planned.
     */
    public static void step(MinecraftServer server, UUID kingdom) {
        if (KingdomWorldApi.isReadOnly(server) || !CityOf.active(server, kingdom)) {
            return;
        }
        if (buying(server, kingdom).on()) {
            buyForOrders(server, kingdom);
        }
        sellToBuyList(server, kingdom);
    }

    /**
     * What the orders of a kingdom want of its citizens now, by order.
     */
    private static Map<Order, Integer> wants(MinecraftServer server, UUID kingdom) {
        OrderBook orders = KingdomWorldApi.state(server).orders();
        List<Order> all = orders.ordersOf(kingdom);
        Map<Order, Integer> wants = new LinkedHashMap<>();
        if (all.isEmpty()) {
            return wants;
        }
        long now = KingdomWorldApi.gameTime(server);
        ProductionPlanner.World world = Production.world(server, kingdom);
        Set<Long> ids = new HashSet<>();
        for (Order order : all) {
            ids.add(order.id());
            if (order.paused()) {
                continue;
            }
            boolean stuck = order.status() == Order.Status.NO_WORKSHOP || order.status() == Order.Status.MISSING;
            if (!stuck && order.kind() == Order.Kind.MAKE) {
                String key = kingdom + "/" + order.id();
                long[] seen = STILL.get(key);
                if (seen == null || seen[0] != order.made()) {
                    STILL.put(key, new long[]{order.made(), now});
                } else {
                    stuck = now - seen[1] >= STILL_TICKS;
                }
            }
            if (!stuck) {
                continue;
            }
            int need = ProductionPlanner.need(orders, world, order);
            if (need > 0) {
                wants.put(order, need);
            }
        }
        STILL.keySet().removeIf(key -> key.startsWith(kingdom + "/") && !ids.contains(Long.parseLong(key.substring(key.indexOf('/') + 1))));
        CommerceSavedData.get(server).keepOrders(kingdom, ids);
        return wants;
    }

    private static void buyForOrders(MinecraftServer server, UUID kingdom) {
        Map<Order, Integer> wants = wants(server, kingdom);
        if (wants.isEmpty()) {
            return;
        }
        long day = TreasuryActions.day(server);
        OrderBook.Buying buying = buying(server, kingdom);
        Map<String, Long> counts = KingdomStorage.countsById(server, kingdom);
        for (Map.Entry<Order, Integer> want : wants.entrySet()) {
            Order order = want.getKey();
            Item item = GoodsStacks.item(order.item());
            if (item == null) {
                continue;
            }
            long budget = budget(server, kingdom, day);
            if (budget <= 0) {
                LOGGER.debug("The crown of {} keeps its days of wages and buys nothing more of its citizens today", kingdom);
                return;
            }
            long capMilli = CrownPurchase.capMilli(Shops.worth(server, item), buying.capPercent());
            long need = want.getValue() - committed(server, kingdom, order.id());
            if (need <= 0) {
                continue;
            }
            long bought = buy(server, kingdom, order.item(), item, need, capMilli, budget, order, day);
            if (bought < need && committed(server, kingdom, order.id()) == 0) {
                long left = budget(server, kingdom, day);
                if (!commission(server, kingdom, order, item, need - bought, capMilli, left, counts, day)) {
                    // Nobody makes it: building materials wanted of the citizens tell the cities to take up a craft for them.
                    int category = Shops.categoryOf(server, item);
                    if (category == GoodsCategory.MATERIALS.ordinal()) {
                        CommerceSavedData.get(server).city(kingdom, 0).wantMaterials((long) Math.ceil((need - bought) * Shops.worth(server, item)));
                    }
                }
            }
        }
    }

    /**
     * How many of an order the masters of a kingdom were paid for and did not deliver yet.
     */
    static long committed(MinecraftServer server, UUID kingdom, long order) {
        long sum = 0;
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom)) {
            for (Business.Commission commission : shop.commissions()) {
                if (commission.order() == order) {
                    sum += commission.count();
                }
            }
        }
        return sum;
    }

    /**
     * The goods citizens made of an item the crown may buy: the asked price of each shop without the tax.
     */
    static List<CrownPurchase.Offer> offers(MinecraftServer server, UUID kingdom, String id, Item item) {
        List<CrownPurchase.Offer> offers = new ArrayList<>();
        String key = Business.key(id, true);
        double worth = Shops.worth(server, item);
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom)) {
            Business.Lot lot = shop.goods().get(key);
            if (lot != null && lot.count() > 0 && shop.status() != Business.Status.CLOSING) {
                offers.add(new CrownPurchase.Offer(shop.id(), key, lot.count(), Shops.askedMilli(server, shop, key, lot), worth));
            }
        }
        return offers;
    }

    /**
     * The crown buys ready goods citizens made: the cheapest first, within the price allowed and the money, as far as the
     * storehouses near the shops take them.
     *
     * @param order the order they are for, {@code null} for none
     * @return how many it bought
     */
    static long buy(MinecraftServer server, UUID kingdom, String id, Item item, long need, long capMilli, long budget, Order order, long day) {
        List<CrownPurchase.Take> takes = CrownPurchase.choose(offers(server, kingdom, id, item), need, capMilli, budget);
        long bought = 0;
        for (CrownPurchase.Take take : takes) {
            Optional<Business> found = CommerceSavedData.get(server).shop(take.shop());
            if (found.isPresent() && deliver(server, found.get(), take.key(), item, take.count(), take.total(), order, day, false)) {
                bought += take.count();
            }
        }
        return bought;
    }

    /**
     * Goods of a shop go into the storehouse nearest it and the crown pays for them: out of its treasury, or out of the escrow of a
     * commission. Nothing happens when the storehouses have no room.
     *
     * @param escrow whether the money was held for a commission
     * @return whether it happened
     */
    static boolean deliver(MinecraftServer server, Business shop, String key, Item item, long count, long total, Order order, long day, boolean escrow) {
        UUID kingdom = shop.kingdomId();
        Business.Lot lot = shop.goods().get(key);
        if (lot == null || lot.count() < count || count <= 0) {
            return false;
        }
        WorldPos near = new WorldPos(shop.place().dimension(), shop.place().x(), shop.place().y(), shop.place().z());
        List<ItemStack> stacks = new ArrayList<>();
        long left = count;
        while (left > 0) {
            int part = (int) Math.min(left, item.getMaxStackSize());
            stacks.add(new ItemStack(item, part));
            left -= part;
        }
        if (!KingdomStorage.fits(server, kingdom, stacks, near)) {
            return false;
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
        if (escrow) {
            if (economy.purseRelease(till, total, day) != total) {
                return false;
            }
        } else if (!economy.treasuryToPurse(till, total, LedgerCategory.COMMERCE, CityLedger.OTHER, day).ok()) {
            return false;
        }
        shop.removeGoods(key, count);
        long stored = 0;
        for (ItemStack stack : stacks) {
            ItemStack rest = KingdomStorage.insert(server, kingdom, stack, near);
            stored += stack.getCount() - rest.getCount();
            if (!rest.isEmpty()) {
                LOGGER.warn("A storehouse of {} took fewer goods of citizens than it had room for; {} of {} are lost", kingdom, rest.getCount(),
                        GoodsStacks.itemId(item));
            }
        }
        long[] sold = Shops.sold(shop.id());
        sold[0] += total;
        sold[1] += ShopPricing.total(lot.costMilli(), count);
        sold[2] += ShopPricing.total(lot.laborMilli(), count);
        if (lot.category() >= 0 && lot.category() < GoodsCategory.values().length) {
            sold[3 + lot.category()] += (long) Math.floor(Shops.worth(server, item) * count);
        }
        shop.record(day, new Business.Day(0, 0, 0, 0, null, 0, 0, total));
        economy.cityTally(kingdom, shop.cityId()).add(CityStatement.Line.CROWN_BOUGHT, total);
        CommerceSavedData data = CommerceSavedData.get(server);
        if (!escrow) {
            data.addCrownDay(kingdom, day, total, count);
        }
        if (order != null) {
            OrderBook orders = KingdomWorldApi.state(server).orders();
            int delivered = (int) Math.min(Integer.MAX_VALUE, stored);
            orders.get(kingdom, order.id()).filter(found -> found.kind() == Order.Kind.MAKE).ifPresent(found -> orders.addMade(found, delivered));
            data.addBoughtFor(kingdom, order.id(), delivered);
        }
        data.setDirty();
        return true;
    }

    /**
     * No ready goods: a master whose place can make the item and who can buy its raw materials of the crown is paid in advance,
     * the cheapest first, the money held in escrow.
     *
     * @return whether a master took the work
     */
    private static boolean commission(MinecraftServer server, UUID kingdom, Order order, Item item, long count, long capMilli, long budget,
                                      Map<String, Long> counts, long day) {
        if (count <= 0 || budget <= 0) {
            return false;
        }
        String id = order.item();
        Business best = null;
        long bestPrice = Long.MAX_VALUE;
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom)) {
            if (!shop.crafts() || !shop.kept() || shop.status() == Business.Status.CLOSING || !shop.nextTrade().isEmpty()) {
                continue;
            }
            Trade trade = Workshops.trade(shop);
            Set<String> stations = Workshops.placeStations(server, shop);
            if (!trade.fits(stations)) {
                continue;
            }
            ShopMaking.Context context = Workshops.context(server, trade, stations, other -> {
                Item raw = GoodsStacks.item(other);
                long stock = counts.getOrDefault(other, 0L);
                return raw == null || stock <= 0 ? -1 : Shops.crownPrice(server, kingdom, other, raw, stock) * ShopPricing.MILLI;
            }, null);
            long unit = new ShopMaking.Planner(context).madeUnitMilli(id);
            if (unit >= Long.MAX_VALUE / 16) {
                continue;
            }
            long price = Math.max(ShopPricing.priceMilli(unit, ShopPricing.LEAST_MARGIN), ShopPricing.priceMilli(unit, shop.marginPermille()));
            if (price <= capMilli && price < bestPrice) {
                best = shop;
                bestPrice = price;
            }
        }
        if (best == null) {
            return false;
        }
        long afford = Math.min(count, Money.share(budget, ShopPricing.MILLI, bestPrice));
        long paid = ShopPricing.total(bestPrice, afford);
        while (afford > 0 && paid > budget) {
            afford--;
            paid = ShopPricing.total(bestPrice, afford);
        }
        if (afford <= 0 || afford > Integer.MAX_VALUE) {
            return false;
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        CommerceSavedData data = CommerceSavedData.get(server);
        // The escrow comes into the books of the shops of citizens in the same moment it leaves the treasury.
        Business.Commission commission = new Business.Commission(data.takeCommissionId(), order.id(), id, (int) afford, paid, day);
        best.addCommission(commission);
        if (!economy.holdFromTreasury(kingdom, paid, LedgerCategory.COMMERCE, day).ok()) {
            best.removeCommission(commission.id());
            return false;
        }
        data.addCrownDay(kingdom, day, paid, 0);
        data.setDirty();
        LOGGER.debug("The crown of {} paid shop {} {} in advance for {} x {}", kingdom, best.id(), paid, afford, id);
        return true;
    }

    /**
     * In the day of its city after it worked a master delivers what it made of the work paid in advance, before the townspeople
     * buy, paid out of the escrow by what it delivers; what is not delivered after a few days goes back to the crown.
     */
    static void deliverCommissions(MinecraftServer server, Business shop, long day) {
        if (shop.commissions().isEmpty()) {
            return;
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        OrderBook orders = KingdomWorldApi.state(server).orders();
        for (Business.Commission commission : List.copyOf(shop.commissions())) {
            Item item = GoodsStacks.item(commission.item());
            String key = Business.key(commission.item(), true);
            long have = shop.count(key);
            if (item != null && have > 0 && day > commission.day()) {
                long count = Math.min(have, commission.count());
                long pay = count >= commission.count() ? commission.paid() : Money.share(commission.paid(), count, commission.count());
                Business.Commission rest = new Business.Commission(commission.id(), commission.order(), commission.item(), commission.count() - (int) count,
                        commission.paid() - pay, commission.day());
                // The escrow leaves the books of the shops as it comes into the till.
                shop.removeCommission(commission.id());
                if (rest.count() > 0) {
                    shop.addCommission(rest);
                }
                Order order = orders.get(shop.kingdomId(), commission.order()).orElse(null);
                if (!deliver(server, shop, key, item, count, pay, order, day, true)) {
                    shop.removeCommission(rest.id());
                    shop.addCommission(commission);
                    continue;
                }
                commission = rest;
                if (rest.count() <= 0) {
                    continue;
                }
            }
            if (day - commission.day() > CraftRules.COMMISSION_DAYS) {
                shop.removeCommission(commission.id());
                if (commission.paid() > 0 && !economy.releaseToTreasury(shop.kingdomId(), shop.kingdomId(), commission.paid(), LedgerCategory.COMMERCE,
                        day).ok()) {
                    shop.addCommission(commission);
                }
            }
        }
        CommerceSavedData.get(server).setDirty();
    }

    /**
     * A shop closes: the work the crown paid it for in advance and that it did not deliver goes back to the crown.
     */
    static void cancelCommissions(MinecraftServer server, Business shop, long day) {
        if (shop.commissions().isEmpty()) {
            return;
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        for (Business.Commission commission : List.copyOf(shop.commissions())) {
            // The escrow leaves the books of the shops in the same moment it goes back to the treasury.
            shop.removeCommission(commission.id());
            if (commission.paid() > 0 && !economy.releaseToTreasury(shop.kingdomId(), shop.kingdomId(), commission.paid(), LedgerCategory.COMMERCE,
                    day).ok()) {
                shop.addCommission(commission);
            }
        }
        CommerceSavedData.get(server).setDirty();
    }

    // ---------------------------------------------------------------- the head of a city buys of its own shops

    /**
     * The head of a city buys materials its constructions lack in the shops and workshops of its city, out of the treasury of the
     * city and without the sales tax: goods citizens made, or bought goods cheaper than the crown sells them, so that the city never
     * pays a margin on goods of the crown. The items go into the storehouse nearest the shop.
     *
     * @return how many it bought and what it spent
     */
    public static long[] cityBuys(MinecraftServer server, UUID kingdom, long city, String id, long count, long budget, long day) {
        Item item = GoodsStacks.item(id);
        if (item == null || count <= 0 || budget <= 0 || !CityOf.active(server, kingdom)) {
            return new long[2];
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        CityPurses.PurseKey treasury = CityPurses.PurseKey.cityTreasury(kingdom, city);
        long crownMilli = Money.multiplyCapped(Shops.crownPrice(server, kingdom, id, item, KingdomStorage.countsById(server, kingdom)
                .getOrDefault(id, 0L)), ShopPricing.MILLI);
        List<CrownPurchase.Offer> offers = new ArrayList<>();
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom, city)) {
            for (boolean made : new boolean[]{true, false}) {
                String key = Business.key(id, made);
                Business.Lot lot = shop.goods().get(key);
                if (lot == null || lot.count() <= 0 || shop.status() == Business.Status.CLOSING) {
                    continue;
                }
                long price = Shops.askedMilli(server, shop, key, lot);
                if (made || price < crownMilli) {
                    offers.add(new CrownPurchase.Offer(shop.id(), key, lot.count(), price, Shops.worth(server, item)));
                }
            }
        }
        long money = Math.min(budget, economy.purse(treasury));
        long bought = 0;
        long spent = 0;
        for (CrownPurchase.Take take : CrownPurchase.choose(offers, count, Long.MAX_VALUE, money)) {
            Optional<Business> found = CommerceSavedData.get(server).shop(take.shop());
            if (found.isEmpty()) {
                continue;
            }
            Business shop = found.get();
            Business.Lot lot = shop.goods().get(take.key());
            WorldPos near = new WorldPos(shop.place().dimension(), shop.place().x(), shop.place().y(), shop.place().z());
            List<ItemStack> stacks = new ArrayList<>();
            long left = take.count();
            while (left > 0) {
                int part = (int) Math.min(left, item.getMaxStackSize());
                stacks.add(new ItemStack(item, part));
                left -= part;
            }
            if (lot == null || !KingdomStorage.fits(server, kingdom, stacks, near)
                    || !economy.purseMove(treasury, CityPurses.PurseKey.till(kingdom, shop.id()), take.total(), CityLedger.PURCHASES, day).ok()) {
                continue;
            }
            shop.removeGoods(take.key(), take.count());
            for (ItemStack stack : stacks) {
                KingdomStorage.insert(server, kingdom, stack, near);
            }
            long[] sold = Shops.sold(shop.id());
            sold[0] += take.total();
            sold[1] += ShopPricing.total(lot.costMilli(), take.count());
            sold[2] += ShopPricing.total(lot.laborMilli(), take.count());
            if (lot.category() >= 0 && lot.category() < GoodsCategory.values().length) {
                sold[3 + lot.category()] += (long) Math.floor(Shops.worth(server, item) * take.count());
            }
            economy.cityTally(kingdom, city).add(CityStatement.Line.HEAD_BOUGHT, take.total());
            bought += take.count();
            spent += take.total();
        }
        if (bought > 0) {
            CommerceSavedData.get(server).setDirty();
            LOGGER.debug("The head of city {} of {} bought {} {} of its own shops for {}", city, kingdom, bought, id, spent);
        }
        if (bought < count && Shops.categoryOf(server, item) == GoodsCategory.MATERIALS.ordinal()) {
            CommerceSavedData.get(server).city(kingdom, city).wantMaterials((long) Math.ceil((count - bought) * Shops.worth(server, item)));
        }
        return new long[]{bought, spent};
    }

    // ---------------------------------------------------------------- the buy list of the market

    /**
     * Shops holding goods made by citizens beyond a few days of their sales sell them to the buy list of the market of the king,
     * at his price, when it pays at least what they cost and a little more.
     */
    private static void sellToBuyList(MinecraftServer server, UUID kingdom) {
        TradeRules rules = TradeSettings.rules();
        var state = KingdomWorldApi.state(server);
        Optional<Market> found = state.trade().existing(kingdom);
        if (found.isEmpty() || found.get().buys().isEmpty()) {
            return;
        }
        Market market = found.get();
        long day = TreasuryActions.day(server);
        for (Market.BuyEntry entry : market.buys()) {
            Item item = GoodsStacks.item(entry.item());
            if (item == null) {
                continue;
            }
            long price = StallActions.buyPrice(server, kingdom, entry, item);
            long wanted = StallActions.wantedNow(server, kingdom, market, entry, item, price, day);
            if (wanted <= 0) {
                continue;
            }
            String key = Business.key(entry.item(), true);
            double worth = Shops.worth(server, item);
            for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom)) {
                if (wanted <= 0) {
                    break;
                }
                Business.Lot lot = shop.goods().get(key);
                if (lot == null || lot.count() <= 0 || ShopPricing.priceMilli(lot.baseMilli(), ShopPricing.LEAST_MARGIN) > price * ShopPricing.MILLI) {
                    continue;
                }
                long daily = Math.max(0, shop.averageCategory(day, lot.category()));
                long keepCount = (long) Math.ceil(Money.multiplyCapped(CraftRules.CROWN_LIST_DAYS, daily) / Math.max(0.01, worth));
                long count = Math.min(wanted, lot.count() - keepCount);
                if (count <= 0) {
                    continue;
                }
                long total = Prices.total(price, count);
                if (total <= 0 || !deliver(server, shop, key, item, count, total, null, day, false)) {
                    continue;
                }
                state.trade().recordPurchase(kingdom, day, entry.item(), total, rules);
                wanted -= count;
            }
        }
    }

    // ---------------------------------------------------------------- the view of imports

    /**
     * An item citizens sell cheaper than it comes from abroad: how many of their goods cost no more than the import, and the
     * cheapest price of one, in whole coins.
     */
    public record Cheaper(String item, long count, long price, long importCost) {
    }

    /**
     * The goods made by citizens of a kingdom cheaper than imports, the cheapest for their worth first.
     */
    public static List<Cheaper> cheaper(MinecraftServer server, UUID kingdom, int max) {
        Map<String, long[]> byItem = new LinkedHashMap<>();
        TradeRules rules = TradeSettings.rules();
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom)) {
            for (Map.Entry<String, Business.Lot> entry : shop.goods().entrySet()) {
                if (!entry.getValue().made()) {
                    continue;
                }
                String id = Business.itemOf(entry.getKey());
                Item item = GoodsStacks.item(id);
                if (item == null) {
                    continue;
                }
                long importCost = Prices.importCost(Shops.worth(server, item), 1, rules);
                long price = ShopPricing.total(Shops.askedMilli(server, shop, entry.getKey(), entry.getValue()), 1);
                if (price > importCost) {
                    continue;
                }
                long[] known = byItem.computeIfAbsent(id, k -> new long[]{0, Long.MAX_VALUE, importCost});
                known[0] += entry.getValue().count();
                known[1] = Math.min(known[1], price);
            }
        }
        List<Cheaper> rows = new ArrayList<>();
        byItem.forEach((id, values) -> rows.add(new Cheaper(id, values[0], values[1], values[2])));
        rows.sort(java.util.Comparator.comparingDouble((Cheaper row) -> row.price() / (double) Math.max(1, row.importCost()))
                .thenComparing(Cheaper::item));
        return rows.size() > max ? List.copyOf(rows.subList(0, max)) : rows;
    }

    /**
     * A member with the right over the market buys of citizens instead of importing: the same purchase, at once, never dearer than
     * the import and the price the kingdom allows.
     */
    public static KingdomActions.Feedback buyNow(MinecraftServer server, UUID actor, UUID kingdom, String id, int count) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        if (actor != null && !KingdomWorldApi.state(server).membership().hasPermission(actor, kingdom, Permission.MANAGE_MARKET)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        Item item = GoodsStacks.item(id);
        if (item == null || count < 1) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        long day = TreasuryActions.day(server);
        double worth = Shops.worth(server, item);
        long capMilli = Math.min(CrownPurchase.capMilli(worth, buying(server, kingdom).capPercent()),
                Money.multiplyCapped(Prices.importCost(worth, 1, TradeSettings.rules()), ShopPricing.MILLI));
        long budget = budget(server, kingdom, day);
        if (budget <= 0) {
            return KingdomActions.Feedback.fail("kingdomworld.market.import.citizens_no_money");
        }
        long bought = buy(server, kingdom, id, item, count, capMilli, budget, null, day);
        if (bought <= 0) {
            return KingdomActions.Feedback.fail("kingdomworld.market.import.citizens_none");
        }
        return KingdomActions.Feedback.ok("kingdomworld.market.import.citizens_bought", bought, new ItemStack(item).getHoverName());
    }
}
