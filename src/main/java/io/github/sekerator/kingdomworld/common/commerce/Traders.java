package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.CraftRules;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import io.github.sekerator.kingdomworld.core.commerce.ShopMaking;
import io.github.sekerator.kingdomworld.core.commerce.ShopPricing;
import io.github.sekerator.kingdomworld.core.commerce.Trade;
import io.github.sekerator.kingdomworld.core.commerce.TradeRegistry;
import io.github.sekerator.kingdomworld.core.economy.CityEconomyRules;
import io.github.sekerator.kingdomworld.core.economy.CityLedger;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The trading houses of the citizens: once a day of a city each buys of the masters of its city, wholesale and without the sales
 * tax, what they made beyond the days of stock of their local sales, only what citizens made, from masters whose least price (what
 * the goods cost them and a little more) is within a share of what the house gets abroad, paying the price of the shop but never
 * more than that share, and exports it at once: the money comes from abroad into its till, new money of the currency, within the demand
 * from abroad of the city. What the trading houses could not take of that demand is asked of the masters whose price passes, as
 * their orders of the next day; a city without a trading house keeps it as the demand a new one would feed.
 */
public final class Traders {
    private Traders() {
    }

    /**
     * Goods of a master a trading house could buy.
     */
    private record Offer(Business master, String key, long count, long priceMilli, double worth, long exportMilli) {
    }

    /**
     * The trading houses of a city do their day; called in the day of the city, and by game tests.
     */
    public static void day(MinecraftServer server, UUID kingdom, long city, long day) {
        CommerceSavedData data = CommerceSavedData.get(server);
        CommerceSavedData.CityTrade trade = data.city(kingdom, city);
        CraftRules craft = CraftSettings.rules();
        CityEconomyRules rules = CityEconomySettings.rules();
        CityTies.Ties ties = CityTies.of(server, kingdom);
        double demand = ties.demand(city);
        CityLevel level = ties.level(city);
        int exportPercent = craft.craftExportPercent(level);
        List<Business> traders = new ArrayList<>();
        List<Business> masters = new ArrayList<>();
        for (Business shop : data.shopsOf(kingdom, city)) {
            if (shop.trader() && shop.kept() && shop.status() != Business.Status.CLOSING) {
                traders.add(shop);
            } else if (shop.crafts()) {
                masters.add(shop);
            }
        }
        List<Offer> offers = new ArrayList<>();
        boolean anyPasses = false;
        for (Business master : masters) {
            boolean passes = true;
            boolean holds = false;
            Map<Integer, Double> spare = new HashMap<>();
            for (GoodsCategory category : Workshops.trade(master).goods()) {
                // The master keeps what its own city buys of it in its days of stock, or its strength by the share of the kind.
                long keep = io.github.sekerator.kingdomworld.core.commerce.CityDay.stockTarget(master.averageCategory(day, category.ordinal()),
                        Shops.strength(server, master, 0, rules), ShopMaking.sharePercent(category, rules.basket()), rules.stockDays());
                double held = 0;
                for (Map.Entry<String, Business.Lot> entry : master.goods().entrySet()) {
                    if (entry.getValue().made() && entry.getValue().category() == category.ordinal()) {
                        held += Shops.worth(server, Business.itemOf(entry.getKey())) * entry.getValue().count();
                    }
                }
                spare.put(category.ordinal(), Math.max(0.0, held - keep));
            }
            List<Map.Entry<String, Business.Lot>> made = new ArrayList<>();
            for (Map.Entry<String, Business.Lot> entry : master.goods().entrySet()) {
                if (entry.getValue().made()) {
                    made.add(entry);
                }
            }
            for (Map.Entry<String, Business.Lot> entry : made) {
                holds = true;
                String item = Business.itemOf(entry.getKey());
                Item found = GoodsStacks.item(item);
                if (found == null) {
                    continue;
                }
                double worth = Shops.worth(server, found);
                long exportMilli = (long) Math.floor(worth * exportPercent * 10.0);
                long price = ShopMaking.wholesaleMilli(entry.getValue(), master.marginPermille(), Shops.ceilingMilli(server, found), exportMilli,
                        craft.traderBuyPercent());
                if (price < 0) {
                    passes = false;
                    continue;
                }
                double left = spare.getOrDefault(entry.getValue().category(), 0.0);
                long count = Math.min(entry.getValue().count(), (long) Math.floor(left / Math.max(0.01, worth)));
                if (count > 0) {
                    offers.add(new Offer(master, entry.getKey(), count, price, worth, exportMilli));
                    spare.put(entry.getValue().category(), left - count * worth);
                }
            }
            anyPasses |= passes || !holds;
        }
        double exported = 0;
        if (!traders.isEmpty() && demand > 0 && !offers.isEmpty()) {
            offers.sort(Comparator.comparingDouble((Offer offer) -> offer.priceMilli() / Math.max(0.01, offer.worth()))
                    .thenComparingLong(offer -> offer.master().id()).thenComparing(Offer::key));
            exported = export(server, kingdom, city, day, traders, offers, demand);
        }
        long unchosen = (long) Math.floor(Math.max(0.0, demand - exported));
        trade.outside = unchosen;
        trade.mastersPass = anyPasses;
        trade.orders.clear();
        if (!traders.isEmpty() && unchosen > 0) {
            // The demand no trading house took is asked of the masters whose price passes, as their orders of the next day.
            List<Business> asked = new ArrayList<>();
            for (Business master : masters) {
                if (passes(server, master, exportPercent, craft)) {
                    asked.add(master);
                }
            }
            for (Business master : asked) {
                trade.orders.put(master.id(), unchosen / asked.size());
            }
        }
        data.setDirty();
    }

    /**
     * Whether a master could sell to a trading house: its cheapest goods made by citizens for their worth at most the share of
     * what they get abroad, or none held yet.
     */
    private static boolean passes(MinecraftServer server, Business master, int exportPercent, CraftRules craft) {
        boolean holds = false;
        for (Map.Entry<String, Business.Lot> entry : master.goods().entrySet()) {
            if (!entry.getValue().made()) {
                continue;
            }
            holds = true;
            Item found = GoodsStacks.item(Business.itemOf(entry.getKey()));
            if (found == null) {
                continue;
            }
            double worth = Shops.worth(server, found);
            long exportMilli = (long) Math.floor(worth * exportPercent * 10.0);
            if (ShopMaking.wholesaleMilli(entry.getValue(), master.marginPermille(), Shops.ceilingMilli(server, found), exportMilli,
                    craft.traderBuyPercent()) >= 0) {
                return true;
            }
        }
        return !holds;
    }

    /**
     * The trading houses buy and export, the cheapest offers first, within the demand from abroad and the strength of each.
     *
     * @return the worth exported
     */
    private static double export(MinecraftServer server, UUID kingdom, long city, long day, List<Business> traders, List<Offer> offers,
                                 double demand) {
        Economy economy = KingdomWorldApi.state(server).economy();
        CityEconomyRules rules = CityEconomySettings.rules();
        double left = demand;
        double exported = 0;
        int next = 0;
        long[] counts = new long[offers.size()];
        for (int i = 0; i < offers.size(); i++) {
            counts[i] = offers.get(i).count();
        }
        // The houses share what the masters have to spare, each as far as its strength goes.
        double spare = 0;
        for (Offer offer : offers) {
            spare += offer.count() * offer.worth();
        }
        double each = Math.max(1.0, Math.min(spare, demand) / traders.size());
        for (Business house : traders) {
            double room = Math.min(Shops.strength(server, house, 0, rules), each);
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, house.id());
            long income = 0;
            long paidAll = 0;
            for (int i = next; i < offers.size() && left > 0 && room > 0; i++) {
                Offer offer = offers.get(i);
                long count = Math.min(counts[i], (long) Math.floor(Math.min(left, room) / Math.max(0.01, offer.worth())));
                if (count <= 0) {
                    continue;
                }
                Business.Lot lot = offer.master().goods().get(offer.key());
                if (lot == null) {
                    continue;
                }
                count = Math.min(count, lot.count());
                long money = Money.share(Money.multiplyCapped(offer.exportMilli(), count), 1, ShopPricing.MILLI);
                long paid = ShopPricing.total(offer.priceMilli(), count);
                if (money <= 0 || paid > money) {
                    continue;
                }
                // The money comes from abroad first, then the house pays the master out of it: both in one go or neither.
                if (!economy.purseFromAbroad(till, money, day).ok()) {
                    continue;
                }
                CityPurses.PurseKey masterTill = CityPurses.PurseKey.till(kingdom, offer.master().id());
                if (!economy.purseMove(till, masterTill, paid, CityLedger.OTHER, day).ok()) {
                    economy.purseLeaves(till, city, money, CityPurses.Outflow.UPKEEP, day);
                    continue;
                }
                offer.master().removeGoods(offer.key(), count);
                counts[i] -= count;
                long[] sold = Shops.sold(offer.master().id());
                sold[0] += paid;
                sold[1] += ShopPricing.total(lot.costMilli(), count);
                sold[2] += ShopPricing.total(lot.laborMilli(), count);
                long[] houseSold = Shops.sold(house.id());
                houseSold[0] += money;
                houseSold[1] += paid;
                income = Money.addCapped(income, money);
                paidAll = Money.addCapped(paidAll, paid);
                double worth = count * offer.worth();
                left -= worth;
                room -= worth;
                exported += worth;
            }
            if (income > 0) {
                // What it sold and paid is written at the end of its day; what came from abroad is written now.
                house.record(day, new Business.Day(0, 0, 0, 0, null, 0, income, 0));
                economy.cityTally(kingdom, city).add(CityStatement.Line.CITIZEN_EXPORTS, income);
                if (house.status() == Business.Status.EMPTY) {
                    house.setStatus(house.family() ? Business.Status.FAMILY : Business.Status.TRADING);
                }
            }
        }
        return exported;
    }

    /**
     * What a trading house asked of a workshop for a kind of goods, worth: its order split among the kinds of its trade.
     */
    static long order(MinecraftServer server, Business shop, GoodsCategory category) {
        Long order = CommerceSavedData.get(server).city(shop.kingdomId(), shop.cityId()).orders.get(shop.id());
        if (order == null || order <= 0) {
            return 0;
        }
        Trade trade = Workshops.trade(shop);
        return trade.makes(category) ? order / Math.max(1, trade.goods().size()) : 0;
    }

    /**
     * The demand from abroad of a city a new trading house would feed: what no trading house took at its last day, while a master
     * could sell to one and a share of it feeds a merchant half its usual wage.
     */
    static double unchosen(MinecraftServer server, UUID kingdom, long city) {
        CommerceSavedData.CityTrade trade = CommerceSavedData.get(server).city(kingdom, city);
        if (!trade.mastersPass || trade.outside <= 0) {
            return 0;
        }
        long wage = Workshops.usualIncome(TradeRegistry.current().trade(Trade.TRADER));
        CraftRules craft = CraftSettings.rules();
        // A trading house earns about what it gets abroad less what it pays the masters, less its upkeep.
        double margin = (100 - craft.traderBuyPercent() - CityEconomySettings.rules().businessUpkeepPercent()) / 100.0;
        return trade.outside * Math.max(0.0, margin) * 2 >= wage ? trade.outside : 0;
    }
}
