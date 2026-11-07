package io.github.sekerator.kingdomworld.common.trade;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.economy.ItemValues;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.Prices;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the throne screen's "Market" tab does: shows the lists of the market, the lots of players, trade abroad and the
 * counters of the day, and changes them for members with the right to manage the market. Ordering goods from abroad is
 * part of it. The server checks every request.
 */
public final class MarketActions {
    private static final String KEY = "kingdomworld.market.";
    private static List<String> importable;

    private MarketActions() {
    }

    /**
     * Forgets the list of items that come from abroad; it is built again after data packs reload.
     */
    public static void invalidate() {
        importable = null;
    }

    /**
     * Items that come from abroad: those with a worth in the config, without the denied ones.
     */
    public static List<String> importable(MinecraftServer server) {
        List<String> list = importable;
        if (list == null) {
            list = new ArrayList<>();
            ItemValues table = ItemPrices.table(server);
            for (Item item : ForgeRegistries.ITEMS) {
                if (list.size() >= TradeViews.MAX_IMPORTABLE) {
                    break;
                }
                String id = GoodsStacks.itemId(item);
                if (table.known(id).map(value -> value.source() == ItemValues.Source.BASE && value.amount() > 0).orElse(false)
                        && !TradeSettings.importDenied(new ItemStack(item))) {
                    list.add(id);
                }
            }
            importable = List.copyOf(list);
        }
        return list;
    }

    public static boolean isImportable(MinecraftServer server, Item item) {
        return importable(server).contains(GoodsStacks.itemId(item));
    }

    // ---------------------------------------------------------------- the view

    public static void sendView(ServerPlayer player, UUID kingdomId) {
        view(player.server, kingdomId, player.getUUID())
                .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new TradePackets.MarketResponse(view)));
    }

    /**
     * The market of a kingdom for one of its members; empty for anybody else.
     */
    public static Optional<TradeViews.MarketView> view(MinecraftServer server, UUID kingdomId, UUID viewer) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Kingdom> kingdom = state.kingdoms().get(kingdomId);
        Optional<Treasury> treasury = state.economy().treasury(kingdomId);
        if (kingdom.isEmpty() || treasury.isEmpty() || !state.membership().isMember(viewer, kingdomId)) {
            return Optional.empty();
        }
        TradeRules rules = TradeSettings.rules();
        Market market = state.trade().market(kingdomId, rules);
        long day = TreasuryActions.day(server);
        long now = KingdomWorldApi.gameTime(server);
        List<TradeViews.SellRow> sells = new ArrayList<>();
        for (Market.SellEntry entry : market.sells()) {
            Item item = GoodsStacks.item(entry.item());
            if (item != null && sells.size() < TradeViews.MAX_ROWS) {
                long stock = MarketStock.stock(server, kingdomId, item);
                sells.add(new TradeViews.SellRow(new ItemStack(item), entry.price(),
                        Prices.autoSellPrice(MarketStock.worth(server, item).amount(), stock, rules), entry.keep(), stock));
            }
        }
        List<TradeViews.BuyRow> buys = new ArrayList<>();
        for (Market.BuyEntry entry : market.buys()) {
            Item item = GoodsStacks.item(entry.item());
            if (item != null && buys.size() < TradeViews.MAX_ROWS) {
                long stock = MarketStock.stock(server, kingdomId, item);
                buys.add(new TradeViews.BuyRow(new ItemStack(item), entry.price(),
                        Prices.autoBuyPrice(MarketStock.worth(server, item).amount(), stock, rules), entry.max(), entry.budget(),
                        market.spent(entry.item(), day), stock));
            }
        }
        List<TradeViews.ExportRow> exports = new ArrayList<>();
        for (Market.ExportRule rule : market.exports()) {
            Item item = GoodsStacks.item(rule.item());
            if (item != null && exports.size() < TradeViews.MAX_ROWS) {
                exports.add(new TradeViews.ExportRow(new ItemStack(item), rule.above(), MarketStock.stock(server, kingdomId, item),
                        Prices.exportIncome(MarketStock.worth(server, item).amount(), 1_000, rules)));
            }
        }
        List<TradeViews.ImportRow> imports = new ArrayList<>();
        for (Market.ImportOrder order : market.imports()) {
            Item item = GoodsStacks.item(order.item());
            if (item != null && imports.size() < TradeViews.MAX_ROWS) {
                imports.add(new TradeViews.ImportRow(new ItemStack(item), order.count(), order.cost(), Math.max(0, order.arrivalTime() - now)));
            }
        }
        Market.DayStats stats = market.stats(day);
        int volume = 0;
        List<io.github.sekerator.kingdomworld.core.citizen.Citizen> merchants = MerchantBehavior.working(server, kingdomId);
        for (io.github.sekerator.kingdomworld.core.citizen.Citizen merchant : merchants) {
            volume = (int) Math.min(Integer.MAX_VALUE, (long) volume + rules.exportVolume(merchant.experience()));
        }
        TradeViews.Day today = new TradeViews.Day(stats.sold(), stats.bought(), stats.lotTax(), merchants.size(), volume, stats.exported(),
                stats.exportIncome(), rules.exportMaxIncomePerDay(), stats.imported(), rules.importMaxPerDay(), stats.importCost());
        return Optional.of(new TradeViews.MarketView(kingdomId, state.membership().hasPermission(viewer, kingdomId, Permission.MANAGE_MARKET),
                Purses.currency(server, kingdomId).orElseThrow(), sells, buys, StallActions.lotRows(server, market, viewer), exports, imports,
                market.openToOutsiders(), market.foreignCurrency(), treasury.get().salesTaxPermille(), rules.maxSalesTaxPercent() * 10,
                market.exchangeFeePermille(), rules.maxExchangeFeePercent() * 10, today, importable(server), rules.maxEntries()));
    }

    // ---------------------------------------------------------------- changes

    /**
     * Handles a decision from the market tab and sends the fresh view.
     */
    public static void handle(ServerPlayer player, TradePackets.MarketChange change) {
        MinecraftServer server = player.server;
        UUID kingdom = change.kingdomId();
        if (!KingdomWorldApi.state(server).membership().isMember(player.getUUID(), kingdom)) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER));
            return;
        }
        KingdomActions.reply(player, apply(player.server, player.getUUID(), kingdom, change));
        sendView(player, kingdom);
    }

    /**
     * Applies a decision about the market of a kingdom.
     *
     * @param actor the player, who needs the right to manage the market; {@code null} for an operator
     */
    public static KingdomActions.Feedback apply(MinecraftServer server, UUID actor, UUID kingdom, TradePackets.MarketChange change) {
        TradeRules rules = TradeSettings.rules();
        Item item = GoodsStacks.item(change.item());
        boolean needsItem = switch (change.action()) {
            case PUT_SELL, PUT_BUY, PUT_EXPORT, ORDER_IMPORT -> true;
            default -> false;
        };
        if (needsItem && (item == null || new ItemStack(item).is(ModRegistries.COIN.get()))) {
            return KingdomActions.Feedback.error(KingdomError.BAD_ITEM);
        }
        int first = (int) Math.min(Market.MAX_COUNT, Math.max(0, change.first()));
        Outcome outcome;
        String done;
        try {
            switch (change.action()) {
                case PUT_SELL -> {
                    outcome = KingdomWorldApi.change(server, "set a sell entry",
                            state -> state.putSell(actor, kingdom, new Market.SellEntry(change.item(), change.price(), first), rules));
                    done = "sell_set";
                }
                case REMOVE_SELL -> {
                    outcome = KingdomWorldApi.change(server, "remove a sell entry", state -> state.removeSell(actor, kingdom, change.item()));
                    done = "removed";
                }
                case PUT_BUY -> {
                    outcome = KingdomWorldApi.change(server, "set a buy entry", state -> state.putBuy(actor, kingdom,
                            new Market.BuyEntry(change.item(), change.price(), Math.max(1, first), Math.min(Money.MAX, change.second())), rules));
                    done = "buy_set";
                }
                case REMOVE_BUY -> {
                    outcome = KingdomWorldApi.change(server, "remove a buy entry", state -> state.removeBuy(actor, kingdom, change.item()));
                    done = "removed";
                }
                case PUT_EXPORT -> {
                    outcome = KingdomWorldApi.change(server, "set an export rule",
                            state -> state.putExport(actor, kingdom, new Market.ExportRule(change.item(), first), rules));
                    done = "export_set";
                }
                case REMOVE_EXPORT -> {
                    outcome = KingdomWorldApi.change(server, "remove an export rule", state -> state.removeExport(actor, kingdom, change.item()));
                    done = "removed";
                }
                case ORDER_IMPORT -> {
                    return orderImport(server, actor, kingdom, item, first);
                }
                case SET_OPEN -> {
                    outcome = KingdomWorldApi.change(server, "open a market", state -> state.setOpenToOutsiders(actor, kingdom, change.first() != 0, rules));
                    done = change.first() != 0 ? "opened" : "closed";
                }
                case SET_FOREIGN -> {
                    outcome = KingdomWorldApi.change(server, "set foreign money",
                            state -> state.setForeignCurrency(actor, kingdom, change.first() != 0, rules));
                    done = change.first() != 0 ? "foreign_on" : "foreign_off";
                }
                case SET_TAX -> {
                    outcome = KingdomWorldApi.change(server, "set the sales tax", state -> state.setSalesTax(actor, kingdom, first, rules));
                    if (outcome.ok()) {
                        return KingdomActions.Feedback.ok(KEY + "tax_set", TradeTexts.percent(first));
                    }
                    done = "";
                }
                case SET_FEE -> {
                    outcome = KingdomWorldApi.change(server, "set the exchange fee", state -> state.setExchangeFee(actor, kingdom, first, rules));
                    if (outcome.ok()) {
                        return KingdomActions.Feedback.ok(KEY + "fee_set", TradeTexts.percent(first));
                    }
                    done = "";
                }
                default -> throw new IllegalStateException();
            }
        } catch (IllegalArgumentException e) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Component name = item == null ? Component.literal(change.item()) : new ItemStack(item).getHoverName();
        return KingdomActions.Feedback.ok(KEY + done, name);
    }

    /**
     * Orders goods from abroad: only common goods with a worth in the config, paid now at the import factor.
     */
    public static KingdomActions.Feedback orderImport(MinecraftServer server, UUID actor, UUID kingdom, Item item, int count) {
        TradeRules rules = TradeSettings.rules();
        if (item == null || !isImportable(server, item)) {
            return KingdomActions.Feedback.error(KingdomError.NOT_IMPORTABLE);
        }
        if (count < 1) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        long cost = Prices.importCost(MarketStock.worth(server, item).amount(), count, rules);
        long now = KingdomWorldApi.gameTime(server);
        Outcome outcome = KingdomWorldApi.change(server, "order goods from abroad", state -> state.orderImport(actor, kingdom,
                GoodsStacks.itemId(item), count, cost, now, now + rules.importDelayTicks(), rules));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Component money = MoneyTexts.amount(cost, Purses.units(server, kingdom));
        return KingdomActions.Feedback.ok(KEY + "imported", new ItemStack(item).getHoverName(), TradeTexts.items(count), money,
                TradeTexts.ticks(rules.importDelayTicks()));
    }
}
