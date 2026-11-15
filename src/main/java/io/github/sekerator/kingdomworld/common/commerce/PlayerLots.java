package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.Evasion;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import io.github.sekerator.kingdomworld.core.commerce.ShopMaking;
import io.github.sekerator.kingdomworld.core.commerce.ShopPricing;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.Prices;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The townspeople of every city of a kingdom buy lots players put on its market, whole, when one costs per item no more than the
 * reference price of their city: the cheapest offer of the item in the shops of the city with its sales tax, or, where the shops do
 * not hold it, the price of the crown with the least margin and the tax. Only goods of the basket, plain, not dearer than the worth
 * and a half. The lot pays the sales tax of the city of the buyers into its treasury instead of the tax of the market, as a shop
 * would, and what is bought is used up.
 */
public final class PlayerLots {
    private PlayerLots() {
    }

    /**
     * The price one of an item costs the townspeople of a city with the tax, in thousandths: the cheapest in its shops, else the
     * price of the crown with the least margin, below 0 when there is none.
     */
    static long referenceMilli(MinecraftServer server, UUID kingdom, List<Business> shops, String item, Item found, int tax,
                               Map<String, Long> counts) {
        long best = -1;
        for (Business shop : shops) {
            for (boolean made : new boolean[]{false, true}) {
                Business.Lot lot = shop.goods().get(Business.key(item, made));
                if (lot == null || lot.count() <= 0) {
                    continue;
                }
                long asked = ShopPricing.withTaxMilli(ShopMaking.askedMilli(lot, shop.marginPermille(), Shops.ceilingMilli(server, found),
                        Shops.worthMilli(server, found)), tax);
                best = best < 0 ? asked : Math.min(best, asked);
            }
        }
        if (best >= 0) {
            return best;
        }
        long crown = Shops.crownPrice(server, kingdom, item, found, counts.getOrDefault(item, 0L));
        long withMargin = ShopPricing.priceMilli(Money.multiplyCapped(crown, ShopPricing.MILLI), CityEconomySettings.rules().minMarginPercent() * 10);
        return ShopPricing.withTaxMilli(withMargin, tax);
    }

    /**
     * The townspeople of a city buy lots of a kind of goods within a budget.
     *
     * @param shops the shops of the city, for the reference price
     * @return what they spent
     */
    static long buy(MinecraftServer server, UUID kingdom, long city, long day, int kind, long budget, List<Business> shops, int tax, int evasion) {
        if (budget <= 0 || kind < 0 || kind >= GoodsCategory.values().length || !GoodsCategory.values()[kind].basket()) {
            return 0;
        }
        var state = KingdomWorldApi.state(server);
        Optional<Market> market = state.trade().existing(kingdom);
        if (market.isEmpty() || market.get().lots().isEmpty()) {
            return 0;
        }
        Map<String, Long> counts = null;
        record Candidate(Market.Lot lot, Item item, long unitMilli, double worth) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Market.Lot lot : market.get().lots()) {
            if (!lot.goods().data().isEmpty() || lot.goods().count() <= 0 || lot.price() > budget
                    || KingdomWorldApi.kingdom(server, lot.seller()).isPresent()) {
                continue;
            }
            Item item = GoodsStacks.item(lot.goods().item());
            if (item == null || Shops.categoryOf(server, item) != kind) {
                continue;
            }
            double worth = Shops.worth(server, item);
            long unitMilli = Money.share(Money.multiplyCapped(lot.price(), ShopPricing.MILLI), 1, lot.goods().count());
            if (unitMilli > (long) Math.floor(worth * 1.5 * ShopPricing.MILLI)) {
                continue;
            }
            if (counts == null) {
                counts = KingdomStorage.countsById(server, kingdom);
            }
            long reference = referenceMilli(server, kingdom, shops, lot.goods().item(), item, tax, counts);
            if (!ShopPricing.lotWanted(unitMilli, reference, worth)) {
                continue;
            }
            candidates.add(new Candidate(lot, item, unitMilli, worth));
        }
        if (candidates.isEmpty()) {
            return 0;
        }
        candidates.sort(Comparator.comparingDouble((Candidate candidate) -> candidate.unitMilli() / Math.max(0.01, candidate.worth()))
                .thenComparingLong(candidate -> candidate.lot().id()));
        Economy economy = state.economy();
        CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom, city);
        CityPurses.PurseKey treasury = CityPurses.PurseKey.cityTreasury(kingdom, city);
        long left = budget;
        for (Candidate candidate : candidates) {
            Market.Lot lot = candidate.lot();
            if (lot.price() > left || lot.price() > economy.purse(purse)) {
                continue;
            }
            Prices.Sale split = Prices.split(lot.price(), tax);
            long collected = Evasion.collected(split.tax(), evasion);
            if (!economy.purseBuysLot(purse, treasury, lot.seller(), lot.price(), collected, day).ok()) {
                continue;
            }
            state.trade().takeLot(kingdom, lot.id());
            left -= lot.price();
            CityStatement.Tally tally = economy.cityTally(kingdom, city);
            tally.add(CityStatement.Line.BASKET_SPENT, lot.price());
            tally.add(CityStatement.Line.PLAYER_LOTS, lot.price());
            Component name = new ItemStack(candidate.item(), Math.max(1, Math.min(64, lot.goods().count()))).getHoverName();
            KingdomActions.tell(server, lot.seller(), Component.translatable("kingdomworld.stall.lot_sold", name,
                    KingdomWorldApi.kingdom(server, kingdom).map(KingdomActions::kingdomName).orElse(Component.literal("?")),
                    MoneyTexts.amount(lot.price(), Purses.units(server, kingdom)), MoneyTexts.amount(lot.price() - collected, Purses.units(server, kingdom)),
                    MoneyTexts.amount(collected, Purses.units(server, kingdom))));
        }
        return budget - left;
    }

    /**
     * Whether a lot would sell to the townspeople of a city now, for checks: a basket item, plain, no dearer than the worth and a
     * half and no dearer per item than the reference price of the city.
     */
    static boolean wanted(MinecraftServer server, UUID kingdom, long city, Market.Lot lot) {
        Item item = GoodsStacks.item(lot.goods().item());
        if (item == null || !lot.goods().data().isEmpty() || lot.goods().count() <= 0) {
            return false;
        }
        int kind = Shops.categoryOf(server, item);
        if (kind < 0 || !GoodsCategory.values()[kind].basket()) {
            return false;
        }
        int tax = CommerceWork.salesTax(server, kingdom, city);
        long unitMilli = Money.share(Money.multiplyCapped(lot.price(), ShopPricing.MILLI), 1, lot.goods().count());
        long reference = referenceMilli(server, kingdom, CommerceSavedData.get(server).shopsOf(kingdom, city), lot.goods().item(), item, tax,
                KingdomStorage.countsById(server, kingdom));
        return ShopPricing.lotWanted(unitMilli, reference, Shops.worth(server, item));
    }
}
