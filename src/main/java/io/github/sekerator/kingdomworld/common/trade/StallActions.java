package io.github.sekerator.kingdomworld.common.trade;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.CoinStacks;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.CoinPayment;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.trade.ExchangeRates;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.Prices;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do at a market stall: buy from the kingdom what it sells, sell it what it buys, buy the lots of other
 * players, put up their own and take them back. Everything happens on the server in one tick and is checked first: the
 * goods, the money, the room in the inventory or the storehouses. Then the goods and the money move together, or the
 * part that moved goes back, so nothing is made or lost.
 */
public final class StallActions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;
    /** Most items one purchase or sale moves. */
    public static final int MAX_COUNT = 4_096;
    private static final String KEY = "kingdomworld.stall.";

    private StallActions() {
    }

    // ---------------------------------------------------------------- opening

    private static Optional<Kingdom> kingdomAt(ServerLevel level, BlockPos pos) {
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        return owner == null ? Optional.empty() : KingdomWorldApi.kingdom(level.getServer(), owner);
    }

    /**
     * A player used a stall: opens its screen, or says why it is closed.
     */
    public static void open(ServerPlayer player, BlockPos pos) {
        Optional<Kingdom> kingdom = kingdomAt(player.serverLevel(), pos);
        if (kingdom.isEmpty()) {
            player.displayClientMessage(Component.translatable(KEY + "outside"), true);
            return;
        }
        if (KingdomWorldApi.isReadOnly(player.server)) {
            player.displayClientMessage(Component.translatable(KEY + "read_only"), true);
            return;
        }
        KingdomActions.Feedback refused = access(player, kingdom.get(), pos);
        if (refused != null) {
            player.displayClientMessage(refused.message(), true);
            return;
        }
        KingdomWorldNetwork.sendToPlayer(player, new TradePackets.StallOpen(view(player, pos, kingdom.get()), true));
    }

    /**
     * Why a player may not trade at a stall now, or {@code null} if they may: a merchant must keep it, and outsiders are
     * served only if the king allows it.
     */
    public static KingdomActions.Feedback access(ServerPlayer player, Kingdom kingdom, BlockPos pos) {
        MinecraftServer server = player.server;
        WorldPos stall = new WorldPos(Dimensions.id(player.level()), pos.getX(), pos.getY(), pos.getZ());
        if (!MerchantBehavior.keepsOpen(server, kingdom.id(), stall)) {
            return KingdomActions.Feedback.error(KingdomError.MARKET_CLOSED);
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        boolean member = state.membership().isMember(player.getUUID(), kingdom.id());
        boolean open = state.trade().existing(kingdom.id()).map(Market::openToOutsiders).orElse(true);
        if (!member && !open) {
            return KingdomActions.Feedback.error(KingdomError.OUTSIDERS_REFUSED);
        }
        return null;
    }

    // ---------------------------------------------------------------- the view

    /**
     * What the stall shows a player.
     */
    public static TradeViews.StallView view(ServerPlayer player, BlockPos pos, Kingdom kingdom) {
        MinecraftServer server = player.server;
        KingdomWorldState state = KingdomWorldApi.state(server);
        TradeRules rules = TradeSettings.rules();
        Market market = state.trade().market(kingdom.id(), rules);
        long day = TreasuryActions.day(server);
        List<TradeViews.Offer> offers = new ArrayList<>();
        for (Market.SellEntry entry : market.sells()) {
            Item item = GoodsStacks.item(entry.item());
            if (item == null || offers.size() >= TradeViews.MAX_ROWS) {
                continue;
            }
            offers.add(new TradeViews.Offer(new ItemStack(item), LawWork.priceFor(server, kingdom.id(), player.getUUID(),
                    sellPrice(server, kingdom.id(), entry, item)),
                    Math.max(0, MarketStock.free(server, kingdom.id(), item) - entry.keep())));
        }
        List<TradeViews.Wanted> wanted = new ArrayList<>();
        for (Market.BuyEntry entry : market.buys()) {
            Item item = GoodsStacks.item(entry.item());
            if (item == null || wanted.size() >= TradeViews.MAX_ROWS) {
                continue;
            }
            long price = LawWork.payoutFor(server, kingdom.id(), player.getUUID(), buyPrice(server, kingdom.id(), entry, item));
            wanted.add(new TradeViews.Wanted(new ItemStack(item), price, wantedNow(server, kingdom.id(), market, entry, item, price, day),
                    GoodsStacks.countPlain(player.getInventory(), item)));
        }
        List<TradeViews.LotRow> lots = lotRows(server, market, player.getUUID());
        List<TradeViews.Purse> purses = new ArrayList<>();
        Economy economy = state.economy();
        Purses.currency(server, kingdom.id()).ifPresent(own -> purses.add(new TradeViews.Purse(own,
                economy.accounts().balance(player.getUUID(), kingdom.id()), CoinStacks.count(player.getInventory(), kingdom.id()))));
        if (market.foreignCurrency()) {
            for (Treasury treasury : economy.treasuries()) {
                UUID currency = treasury.kingdomId();
                if (currency.equals(kingdom.id()) || purses.size() >= TradeViews.MAX_ROWS) {
                    continue;
                }
                long balance = economy.accounts().balance(player.getUUID(), currency);
                Coins carried = CoinStacks.count(player.getInventory(), currency);
                if (balance > 0 || !carried.isEmpty()) {
                    Purses.currency(server, currency).ifPresent(info -> purses.add(new TradeViews.Purse(info, balance, carried)));
                }
            }
        }
        Treasury treasury = economy.treasury(kingdom.id()).orElseThrow();
        return new TradeViews.StallView(pos, kingdom.id(), kingdom.name(), kingdom.color(), Purses.currency(server, kingdom.id()).orElseThrow(),
                state.membership().isMember(player.getUUID(), kingdom.id()), treasury.salesTaxPermille(),
                rulesFor(server, kingdom.id()).maxLotsPerPlayer(),
                market.exchangeFeePermille(), offers, wanted, lots, purses);
    }

    /**
     * The trade rules at the stalls of a kingdom: its working markets let players keep more lots.
     */
    static TradeRules rulesFor(MinecraftServer server, UUID kingdomId) {
        TradeRules rules = TradeSettings.rules();
        int extra = io.github.sekerator.kingdomworld.common.construction.BuildingEffects.extraLots(server, kingdomId);
        return extra <= 0 ? rules : rules.withMaxLots(rules.maxLotsPerPlayer() + extra);
    }

    static List<TradeViews.LotRow> lotRows(MinecraftServer server, Market market, UUID viewer) {
        List<TradeViews.LotRow> lots = new ArrayList<>();
        // The viewer's own lots first, so that they are never cut off.
        List<Market.Lot> ordered = new ArrayList<>(market.lots());
        ordered.sort((a, b) -> Boolean.compare(!a.seller().equals(viewer), !b.seller().equals(viewer)));
        for (Market.Lot lot : ordered) {
            if (lots.size() >= TradeViews.MAX_ROWS) {
                break;
            }
            List<ItemStack> stacks = GoodsStacks.stacks(lot.goods());
            if (stacks.isEmpty()) {
                continue;
            }
            ItemStack shown = stacks.get(0).copyWithCount(Math.min(lot.goods().count(), stacks.get(0).getMaxStackSize()));
            String seller = PlayerNames.name(server, lot.seller());
            lots.add(new TradeViews.LotRow(lot.id(), shown, lot.price(), seller.length() > TradeViews.MAX_NAME
                    ? seller.substring(0, TradeViews.MAX_NAME) : seller, lot.seller().equals(viewer)));
        }
        return lots;
    }

    // ---------------------------------------------------------------- prices

    /**
     * The price the market of a kingdom asks for an item now, if it sells it; for the worth of stolen goods.
     */
    public static java.util.OptionalLong marketPrice(MinecraftServer server, UUID kingdom, Item item) {
        Market market = KingdomWorldApi.state(server).trade().market(kingdom, TradeSettings.rules());
        Optional<Market.SellEntry> entry = market.sell(GoodsStacks.itemId(new ItemStack(item)));
        return entry.isPresent() ? java.util.OptionalLong.of(sellPrice(server, kingdom, entry.get(), item)) : java.util.OptionalLong.empty();
    }

    static long sellPrice(MinecraftServer server, UUID kingdom, Market.SellEntry entry, Item item) {
        if (entry.price() > 0) {
            return entry.price();
        }
        return Prices.autoSellPrice(MarketStock.worth(server, item).amount(), MarketStock.stock(server, kingdom, item), TradeSettings.rules());
    }

    static long buyPrice(MinecraftServer server, UUID kingdom, Market.BuyEntry entry, Item item) {
        if (entry.price() > 0) {
            return entry.price();
        }
        return Prices.autoBuyPrice(MarketStock.worth(server, item).amount(), MarketStock.stock(server, kingdom, item), TradeSettings.rules());
    }

    /**
     * Items the kingdom takes now: up to its target stock and within what is left of today's budget (0 for no limit).
     */
    static long wantedNow(MinecraftServer server, UUID kingdom, Market market, Market.BuyEntry entry, Item item, long price, long day) {
        if (price < 1) {
            return 0;
        }
        long room = entry.max() - MarketStock.stock(server, kingdom, item);
        if (entry.budget() > 0) {
            long left = Math.max(0, entry.budget() - market.spent(entry.item(), day));
            room = Math.min(room, left / price);
        }
        return Math.max(0, room);
    }

    /**
     * What a price of the kingdom costs in the currency a player pays with, or -1 if the market does not take it.
     */
    static long cost(MinecraftServer server, UUID kingdom, Market market, UUID currency, long price) {
        if (currency.equals(kingdom)) {
            return price;
        }
        if (!market.foreignCurrency()) {
            return -1;
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        if (!economy.isActive(currency)) {
            return -1;
        }
        return ExchangeRates.priceIn(price, economy.rate(kingdom), economy.rate(currency), market.exchangeFeePermille());
    }

    // ---------------------------------------------------------------- requests

    /**
     * Handles a request from the stall screen and refreshes it.
     */
    public static void handle(ServerPlayer player, TradePackets.StallAction action) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = action.pos();
        if (player.isSpectator() || !level.isLoaded(pos) || !level.getBlockState(pos).is(ModRegistries.MARKET_STALL.get())
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail(KEY + "too_far"));
            return;
        }
        Optional<Kingdom> kingdom = kingdomAt(level, pos);
        if (kingdom.isEmpty()) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail(KEY + "outside"));
            return;
        }
        if (KingdomWorldApi.isReadOnly(player.server)) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.READ_ONLY));
            return;
        }
        KingdomActions.Feedback refused = access(player, kingdom.get(), pos);
        if (refused != null) {
            KingdomActions.reply(player, refused);
            return;
        }
        UUID currency = action.currency() == null ? kingdom.get().id() : action.currency();
        KingdomActions.Feedback feedback = switch (action.action()) {
            case BUY -> buy(player, kingdom.get(), action.item(), action.count(), action.account(), currency);
            case SELL -> sell(player, kingdom.get(), action.item(), action.count(), action.account());
            case BUY_LOT -> buyLot(player, kingdom.get(), action.id(), action.account(), currency);
            case LIST_LOT -> listLot(player, kingdom.get(), action.count(), action.price());
            case WITHDRAW_LOT -> withdrawLot(player, kingdom.get(), action.id());
        };
        KingdomActions.reply(player, feedback);
        KingdomWorldNetwork.sendToPlayer(player, new TradePackets.StallOpen(view(player, pos, kingdom.get()), false));
    }

    /**
     * Money a player is about to pay: from the account, or coins with the change for them.
     */
    private record Payment(UUID currency, long cost, UUID payer, Coins coins, long change) {
    }

    private static Object preparePayment(ServerPlayer player, UUID kingdom, UUID currency, long cost, boolean account) {
        Economy economy = KingdomWorldApi.state(player.server).economy();
        if (account && LawWork.overDebtLimit(player.server, kingdom, player.getUUID())) {
            // A debtor over the limit pays the market only in coins.
            return KingdomActions.Feedback.error(KingdomError.DEBT_TOO_HIGH);
        }
        if (account) {
            Outcome check = economy.checkPayKingdom(kingdom, currency, cost, player.getUUID());
            return check.ok() ? new Payment(currency, cost, player.getUUID(), Coins.NONE, 0) : KingdomActions.Feedback.error(check.error());
        }
        Optional<CoinPayment.Plan> plan = Purses.plan(player, currency, cost);
        if (plan.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.NOT_ENOUGH_MONEY);
        }
        Outcome check = economy.checkPayKingdom(kingdom, currency, cost, null);
        return check.ok() ? new Payment(currency, cost, null, plan.get().coins(), plan.get().change())
                : KingdomActions.Feedback.error(check.error());
    }

    /**
     * Hands the change for coins back; what does not fit goes onto the account.
     *
     * @return the change that went onto the account
     */
    private static long change(ServerPlayer player, Payment payment) {
        return payment.payer() == null && payment.change() > 0 ? Purses.payOut(player, payment.currency(), payment.change()) : 0;
    }

    private static Component money(MinecraftServer server, UUID currency, long amount) {
        return MoneyTexts.amount(amount, Purses.units(server, currency));
    }

    /**
     * The player buys items the kingdom sells, paying from the account or with coins in the kingdom's or, where the
     * market takes them, another currency.
     */
    public static KingdomActions.Feedback buy(ServerPlayer player, Kingdom kingdom, String itemId, int count, boolean account, UUID currency) {
        MinecraftServer server = player.server;
        KingdomWorldState state = KingdomWorldApi.state(server);
        TradeRules rules = TradeSettings.rules();
        Market market = state.trade().market(kingdom.id(), rules);
        Optional<Market.SellEntry> entry = market.sell(itemId);
        Item item = GoodsStacks.item(itemId);
        if (entry.isEmpty() || item == null) {
            return KingdomActions.Feedback.error(KingdomError.NOT_FOR_SALE);
        }
        if (count < 1 || count > MAX_COUNT) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (MarketStock.free(server, kingdom.id(), item) - entry.get().keep() < count) {
            return KingdomActions.Feedback.error(KingdomError.OUT_OF_STOCK);
        }
        long price = Prices.total(LawWork.priceFor(server, kingdom.id(), player.getUUID(), sellPrice(server, kingdom.id(), entry.get(), item)), count);
        if (price < 1) {
            return KingdomActions.Feedback.error(price < 0 ? KingdomError.TOO_MUCH_MONEY : KingdomError.NOT_FOR_SALE);
        }
        long cost = cost(server, kingdom.id(), market, currency, price);
        if (cost < 1) {
            return KingdomActions.Feedback.error(KingdomError.FOREIGN_CURRENCY_REFUSED);
        }
        ItemStack sample = new ItemStack(item);
        List<ItemStack> wanted = MarketStock.stacks(List.of(new Goods(itemId, count, "")));
        if (!GoodsStacks.fits(player.getInventory(), wanted)) {
            return KingdomActions.Feedback.error(KingdomError.NO_ROOM);
        }
        Object prepared = preparePayment(player, kingdom.id(), currency, cost, account);
        if (prepared instanceof KingdomActions.Feedback refused) {
            return refused;
        }
        Payment payment = (Payment) prepared;
        WorldPos near = new WorldPos(Dimensions.id(player.level()), player.getBlockX(), player.getBlockY(), player.getBlockZ());
        List<ItemStack> goods = MarketStock.take(server, kingdom.id(), item, count, near);
        if (goods == null) {
            return KingdomActions.Feedback.error(KingdomError.OUT_OF_STOCK);
        }
        if (payment.payer() == null && !Purses.take(player, currency, payment.coins())) {
            MarketStock.putBack(server, kingdom.id(), goods, near);
            return KingdomActions.Feedback.error(KingdomError.NOT_ENOUGH_MONEY);
        }
        long day = TreasuryActions.day(server);
        Outcome paid = state.economy().payKingdom(kingdom.id(), currency, cost, payment.payer(), day);
        if (!paid.ok()) {
            // Cannot happen after the checks: everything goes back.
            if (payment.payer() == null) {
                Purses.giveBack(player, currency, payment.coins());
            }
            MarketStock.putBack(server, kingdom.id(), goods, near);
            LOGGER.error("A purchase at the market of {} failed after its checks: {}", kingdom.name(), paid.error());
            return KingdomActions.Feedback.error(paid.error());
        }
        long toAccount = change(player, payment);
        for (ItemStack stack : goods) {
            GoodsStacks.give(player, stack);
        }
        state.trade().recordSale(kingdom.id(), day, price, rules);
        LawWork.traded(server, kingdom.id(), player.getUUID(), price);
        LOGGER.debug("{} bought {} x{} from {} for {}", player.getGameProfile().getName(), itemId, count, kingdom.name(), cost);
        KingdomActions.Feedback done = KingdomActions.Feedback.ok(KEY + "bought", sample.getHoverName(), TradeTexts.items(count),
                money(server, currency, cost));
        return toAccount > 0 ? withAccountNote(done, server, currency, toAccount) : done;
    }

    private static KingdomActions.Feedback withAccountNote(KingdomActions.Feedback done, MinecraftServer server, UUID currency, long amount) {
        return KingdomActions.Feedback.ok(KEY + "and_account", done.message(), money(server, currency, amount));
    }

    /**
     * The player sells plain items the kingdom buys; the treasury pays onto the account or in coins.
     */
    public static KingdomActions.Feedback sell(ServerPlayer player, Kingdom kingdom, String itemId, int requested, boolean account) {
        MinecraftServer server = player.server;
        KingdomWorldState state = KingdomWorldApi.state(server);
        TradeRules rules = TradeSettings.rules();
        Market market = state.trade().market(kingdom.id(), rules);
        Optional<Market.BuyEntry> entry = market.buy(itemId);
        Item item = GoodsStacks.item(itemId);
        if (entry.isEmpty() || item == null) {
            return KingdomActions.Feedback.error(KingdomError.NOT_BOUGHT);
        }
        if (requested < 1 || requested > MAX_COUNT) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        int have = GoodsStacks.countPlain(player.getInventory(), item);
        if (have <= 0) {
            return KingdomActions.Feedback.error(KingdomError.NO_ITEMS);
        }
        long day = TreasuryActions.day(server);
        long unit = LawWork.payoutFor(server, kingdom.id(), player.getUUID(), buyPrice(server, kingdom.id(), entry.get(), item));
        if (unit < 1) {
            return KingdomActions.Feedback.error(KingdomError.NOT_BOUGHT);
        }
        if (MarketStock.stock(server, kingdom.id(), item) >= entry.get().max()) {
            return KingdomActions.Feedback.error(KingdomError.ENOUGH_STOCK);
        }
        long wanted = wantedNow(server, kingdom.id(), market, entry.get(), item, unit, day);
        if (wanted <= 0) {
            return KingdomActions.Feedback.error(KingdomError.BUDGET_SPENT);
        }
        int count = (int) Math.min(Math.min(requested, have), wanted);
        long total = Prices.total(unit, count);
        if (total < 1) {
            return KingdomActions.Feedback.error(KingdomError.TOO_MUCH_MONEY);
        }
        List<ItemStack> stacks = MarketStock.stacks(List.of(new Goods(itemId, count, "")));
        if (!io.github.sekerator.kingdomworld.common.storage.KingdomStorage.fits(server, kingdom.id(), stacks)) {
            return KingdomActions.Feedback.error(KingdomError.STOREHOUSES_FULL);
        }
        Outcome check = state.economy().checkKingdomPays(kingdom.id(), total, account ? player.getUUID() : null);
        if (!check.ok()) {
            return KingdomActions.Feedback.error(check.error());
        }
        int taken = GoodsStacks.takePlain(player.getInventory(), item, count);
        if (taken != count) {
            GoodsStacks.give(player, new ItemStack(item, taken));
            return KingdomActions.Feedback.error(KingdomError.NO_ITEMS);
        }
        WorldPos near = new WorldPos(Dimensions.id(player.level()), player.getBlockX(), player.getBlockY(), player.getBlockZ());
        List<ItemStack> rest = MarketStock.putBack(server, kingdom.id(), stacks, near);
        int notStored = 0;
        for (ItemStack stack : rest) {
            // The storehouses took less after all: the player keeps what did not fit and is paid for the rest.
            notStored += stack.getCount();
            GoodsStacks.give(player, stack);
        }
        count -= notStored;
        if (count <= 0) {
            return KingdomActions.Feedback.error(KingdomError.STOREHOUSES_FULL);
        }
        total = unit * count;
        Outcome paid = state.economy().kingdomPays(kingdom.id(), total, account ? player.getUUID() : null, LedgerCategory.TRADE, day);
        if (!paid.ok()) {
            // Cannot happen after the checks: the goods go back to the player.
            for (ItemStack stack : io.github.sekerator.kingdomworld.common.storage.KingdomStorage.take(server, kingdom.id(),
                    stack -> stack.is(item) && GoodsStacks.plain(stack), count, near)) {
                GoodsStacks.give(player, stack);
            }
            LOGGER.error("A sale at the market of {} failed after its checks: {}", kingdom.name(), paid.error());
            return KingdomActions.Feedback.error(paid.error());
        }
        long toAccount = account ? 0 : Purses.payOut(player, kingdom.id(), total);
        state.trade().recordPurchase(kingdom.id(), day, itemId, total, rules);
        LawWork.traded(server, kingdom.id(), player.getUUID(), total);
        KingdomActions.Feedback done = KingdomActions.Feedback.ok(KEY + "sold", new ItemStack(item).getHoverName(), TradeTexts.items(count),
                money(server, kingdom.id(), total));
        return toAccount > 0 ? withAccountNote(done, server, kingdom.id(), toAccount) : done;
    }

    /**
     * The player buys the lot of another player; the seller gets the price less the tax on sales.
     */
    public static KingdomActions.Feedback buyLot(ServerPlayer player, Kingdom kingdom, long lotId, boolean account, UUID currency) {
        MinecraftServer server = player.server;
        KingdomWorldState state = KingdomWorldApi.state(server);
        TradeRules rules = TradeSettings.rules();
        Market market = state.trade().market(kingdom.id(), rules);
        Optional<Market.Lot> lot = market.lot(lotId);
        if (lot.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_LOT);
        }
        long cost = cost(server, kingdom.id(), market, currency, lot.get().price());
        if (cost < 1) {
            return KingdomActions.Feedback.error(KingdomError.FOREIGN_CURRENCY_REFUSED);
        }
        List<ItemStack> stacks = GoodsStacks.stacks(lot.get().goods());
        if (!GoodsStacks.fits(player.getInventory(), stacks)) {
            return KingdomActions.Feedback.error(KingdomError.NO_ROOM);
        }
        UUID payer = account ? player.getUUID() : null;
        Outcome check = state.checkBuyLot(player.getUUID(), kingdom.id(), lotId, currency, cost, payer);
        if (!check.ok()) {
            return KingdomActions.Feedback.error(check.error());
        }
        if (account && LawWork.overDebtLimit(server, kingdom.id(), player.getUUID())) {
            return KingdomActions.Feedback.error(KingdomError.DEBT_TOO_HIGH);
        }
        Optional<CoinPayment.Plan> plan = account ? Optional.empty() : Purses.plan(player, currency, cost);
        if (!account && (plan.isEmpty() || !Purses.take(player, currency, plan.get().coins()))) {
            return KingdomActions.Feedback.error(KingdomError.NOT_ENOUGH_MONEY);
        }
        long day = TreasuryActions.day(server);
        KingdomWorldState.LotSale sale = state.buyLot(player.getUUID(), kingdom.id(), lotId, currency, cost, payer, day, rules);
        if (!sale.outcome().ok()) {
            plan.ifPresent(found -> Purses.giveBack(player, currency, found.coins()));
            return KingdomActions.Feedback.error(sale.outcome().error());
        }
        long toAccount = plan.map(found -> found.change() > 0 ? Purses.payOut(player, currency, found.change()) : 0L).orElse(0L);
        for (ItemStack stack : stacks) {
            GoodsStacks.give(player, stack);
        }
        Market.Lot sold = sale.lot().orElseThrow();
        Component name = stacks.isEmpty() ? Component.literal(sold.goods().item()) : stacks.get(0).getHoverName();
        Prices.Sale split = Prices.split(sold.price(), state.economy().treasury(kingdom.id()).map(Treasury::salesTaxPermille).orElse(0));
        KingdomActions.tell(server, sold.seller(), Component.translatable(KEY + "lot_sold", name, KingdomActions.kingdomName(kingdom),
                money(server, kingdom.id(), sold.price()), money(server, kingdom.id(), split.seller()), money(server, kingdom.id(), split.tax())));
        LOGGER.debug("{} bought lot {} at the market of {} for {}", player.getGameProfile().getName(), lotId, kingdom.name(), cost);
        LawWork.traded(server, kingdom.id(), player.getUUID(), sold.price());
        KingdomActions.Feedback done = KingdomActions.Feedback.ok(KEY + "lot_bought", name, money(server, currency, cost));
        return toAccount > 0 ? withAccountNote(done, server, currency, toAccount) : done;
    }

    /**
     * A subject puts what it holds in the main hand up for sale: {@code count} of it for {@code price}.
     */
    public static KingdomActions.Feedback listLot(ServerPlayer player, Kingdom kingdom, int count, long price) {
        MinecraftServer server = player.server;
        KingdomWorldState state = KingdomWorldApi.state(server);
        TradeRules rules = rulesFor(server, kingdom.id());
        if (LawWork.overDebtLimit(server, kingdom.id(), player.getUUID())) {
            return KingdomActions.Feedback.error(KingdomError.DEBT_TOO_HIGH);
        }
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty() || GoodsStacks.untradeable(hand)) {
            return KingdomActions.Feedback.error(KingdomError.BAD_ITEM);
        }
        if (count < 1 || count > hand.getCount() || !Money.isPayment(price)) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        Outcome check = state.checkLot(player.getUUID(), kingdom.id(), rules);
        if (!check.ok()) {
            return KingdomActions.Feedback.error(check.error());
        }
        ItemStack listed = hand.split(count);
        player.getInventory().setChanged();
        Goods goods;
        try {
            goods = GoodsStacks.goods(listed);
        } catch (IllegalArgumentException e) {
            GoodsStacks.give(player, listed);
            return KingdomActions.Feedback.error(KingdomError.BAD_ITEM);
        }
        Outcome done = state.listLot(player.getUUID(), kingdom.id(), goods, price, KingdomWorldApi.gameTime(server), rules);
        if (!done.ok()) {
            GoodsStacks.give(player, listed);
            return KingdomActions.Feedback.error(done.error());
        }
        return KingdomActions.Feedback.ok(KEY + "listed", listed.getHoverName(), TradeTexts.items(count), money(server, kingdom.id(), price));
    }

    /**
     * The seller takes a lot back.
     */
    public static KingdomActions.Feedback withdrawLot(ServerPlayer player, Kingdom kingdom, long lotId) {
        Optional<Market.Lot> lot = KingdomWorldApi.state(player.server).withdrawLot(player.getUUID(), kingdom.id(), lotId);
        if (lot.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_LOT);
        }
        List<ItemStack> stacks = GoodsStacks.stacks(lot.get().goods());
        for (ItemStack stack : stacks) {
            GoodsStacks.give(player, stack);
        }
        Component name = stacks.isEmpty() ? Component.literal(lot.get().goods().item()) : stacks.get(0).getHoverName();
        return KingdomActions.Feedback.ok(KEY + "withdrawn", name);
    }

    /**
     * Gives a player the goods that wait for them, for example the lots of a market that closed.
     */
    public static void deliverReturns(ServerPlayer player) {
        if (KingdomWorldApi.isReadOnly(player.server)) {
            return;
        }
        List<Goods> goods = KingdomWorldApi.state(player.server).trade().takeReturns(player.getUUID());
        if (goods.isEmpty()) {
            return;
        }
        long items = 0;
        for (Goods entry : goods) {
            for (ItemStack stack : GoodsStacks.stacks(entry)) {
                GoodsStacks.give(player, stack);
            }
            items += entry.count();
        }
        player.sendSystemMessage(Component.translatable(KEY + "returned", TradeTexts.items(items)));
    }
}
