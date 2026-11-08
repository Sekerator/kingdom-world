package io.github.sekerator.kingdomworld.common.trade;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.ProductionLedger;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.ItemValues;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.ExchangeRates;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.Prices;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * The work of trade over time: caravans leave for regular deals, travel and arrive or are lost, proposals expire, goods
 * ordered from abroad arrive, merchants sell surplus abroad in every simulation step, and once a game day the exchange
 * rates move. Runs whether players are near or not; nothing happens while the kingdom data is read-only.
 */
public final class TradeWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.trade.";
    private static final Random RANDOM = new Random();
    /** Chance of loss, replaced by game tests. */
    private static DoubleSupplier roll = RANDOM::nextDouble;
    private static List<TradeViews.CaravanInfo> lastSent;

    private TradeWork() {
    }

    public static void reset() {
        lastSent = null;
        roll = RANDOM::nextDouble;
    }

    /**
     * Replaces the dice of caravan losses, for game tests; {@code null} restores chance.
     */
    public static void overrideRoll(DoubleSupplier value) {
        roll = value == null ? RANDOM::nextDouble : value;
    }

    /**
     * The dice of trade: robbers on the roads take their numbers from it, so that game tests decide them.
     */
    public static DoubleSupplier dice() {
        return roll;
    }

    private static long day(MinecraftServer server) {
        return Math.floorDiv(KingdomWorldApi.gameTime(server), CitizenNeeds.TICKS_PER_DAY);
    }

    private static Component kingdomName(MinecraftServer server, UUID id) {
        return KingdomWorldApi.kingdom(server, id).<Component>map(KingdomActions::kingdomName).orElseGet(() -> Component.literal("?"));
    }

    private static void notify(MinecraftServer server, UUID kingdomId, Component message) {
        KingdomWorldApi.kingdom(server, kingdomId).ifPresent(kingdom -> KingdomActions.notifyMembers(server, kingdom, Permission.MANAGE_TRADE,
                message));
    }

    private static Component goodsName(Goods goods) {
        List<ItemStack> stacks = GoodsStacks.stacks(goods.withCount(1));
        return stacks.isEmpty() ? Component.literal(goods.item()) : stacks.get(0).getHoverName();
    }

    // ---------------------------------------------------------------- deliveries

    /**
     * Sends the caravan of a delivery: the goods leave the seller's storehouses and the payment the buyer's treasury into
     * escrow, together, or nothing happens.
     */
    public static Outcome deliver(MinecraftServer server, Deal deal) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (KingdomWorldApi.isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        Outcome check = state.checkDelivery(deal);
        if (!check.ok()) {
            return check;
        }
        // In war deals between the enemies are frozen, and caravans of an enemy do not pass the land of the other side.
        if (io.github.sekerator.kingdomworld.common.diplomacy.WarWork.frozen(server, deal.seller(), deal.buyer())) {
            return Outcome.fail(KingdomError.AT_WAR);
        }
        Optional<UUID> blocker = io.github.sekerator.kingdomworld.common.diplomacy.WarWork.blocker(server, deal);
        if (blocker.isPresent()) {
            io.github.sekerator.kingdomworld.common.diplomacy.WarWork.blocked(server, deal, blocker.get());
            return Outcome.fail(KingdomError.BLOCKADE);
        }
        Optional<Capital> from = state.territory().territory(deal.seller()).map(Territory::capital);
        Optional<Capital> to = state.territory().territory(deal.buyer()).map(Territory::capital);
        if (from.isEmpty() || to.isEmpty()) {
            return Outcome.fail(KingdomError.NO_CAPITAL);
        }
        Item item = GoodsStacks.item(deal.item());
        if (item == null) {
            return Outcome.fail(KingdomError.BAD_ITEM);
        }
        WorldPos near = new WorldPos(from.get().dimension(), from.get().x(), from.get().y(), from.get().z());
        List<ItemStack> taken = MarketStock.take(server, deal.seller(), item, deal.count(), near);
        if (taken == null) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_GOODS);
        }
        // The caravan takes the fastest way through the roads; across country it goes straight.
        io.github.sekerator.kingdomworld.core.road.TravelPlan plan = io.github.sekerator.kingdomworld.common.road.RoadCaravans.plan(server,
                from.get().dimension(), from.get().x(), from.get().z(), to.get().x(), to.get().z());
        long ticks = plan.ticks();
        Optional<Caravan> caravan = state.sendCaravan(deal, MarketStock.goods(taken), from.get().dimension(), from.get().x(), from.get().z(),
                to.get().x(), to.get().z(), KingdomWorldApi.gameTime(server), ticks);
        if (caravan.isEmpty()) {
            MarketStock.putBack(server, deal.seller(), taken, near);
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        io.github.sekerator.kingdomworld.common.road.RoadCaravans.departed(server, caravan.get(), plan);
        LOGGER.info("A caravan left {} for {} with {} x{}, arriving in {} ticks", kingdomName(server, deal.seller()).getString(),
                kingdomName(server, deal.buyer()).getString(), deal.item(), deal.count(), ticks);
        Component goods = Component.translatable(KEY + "goods", goodsName(Goods.of(deal.item(), 1)), TradeTexts.items(deal.count()));
        notify(server, deal.seller(), Component.translatable(KEY + "caravan_left", goods, kingdomName(server, deal.buyer()), TradeTexts.ticks(ticks)));
        notify(server, deal.buyer(), Component.translatable(KEY + "caravan_coming", goods, kingdomName(server, deal.seller()), TradeTexts.ticks(ticks)));
        return Outcome.DONE;
    }

    /**
     * Accepts a proposal: the first delivery leaves at once, or the proposal stays open and the reason is given.
     *
     * @param actor the player answering for the kingdom; {@code null} for an operator or a kingdom without a king
     */
    public static Outcome accept(MinecraftServer server, UUID actor, UUID kingdomId, long dealId) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Outcome check = KingdomWorldApi.change(server, "accept a deal", s -> s.checkAnswer(actor, kingdomId, dealId));
        if (!check.ok()) {
            return check;
        }
        Deal deal = state.trade().deal(dealId).orElseThrow();
        Outcome delivered = deliver(server, deal);
        if (!delivered.ok()) {
            return delivered;
        }
        long now = KingdomWorldApi.gameTime(server);
        Deal accepted = deal.regular()
                ? deal.withStatus(Deal.Status.ACTIVE).withNext(now + (long) deal.periodDays() * CitizenNeeds.TICKS_PER_DAY, 1, 0)
                : deal.withStatus(Deal.Status.DONE).withNext(now, 1, 0);
        state.trade().putDeal(accepted);
        notify(server, deal.proposer(), Component.translatable(KEY + "accepted", kingdomName(server, kingdomId)).withStyle(ChatFormatting.GREEN));
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- every second

    /**
     * Moves trade on: deals, caravans and goods from abroad. Called once a second.
     */
    public static void tick(MinecraftServer server) {
        if (KingdomWorldApi.isReadOnly(server)) {
            syncCaravans(server);
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        long now = KingdomWorldApi.gameTime(server);
        for (Deal deal : new ArrayList<>(state.trade().deals())) {
            if (deal.status() == Deal.Status.PROPOSED && now >= deal.expiresTime()) {
                state.trade().putDeal(deal.withStatus(Deal.Status.EXPIRED).withNext(now, deal.deliveries(), deal.failures()));
                notify(server, deal.proposer(), Component.translatable(KEY + "expired", kingdomName(server, deal.answerer())));
            } else if (deal.status() == Deal.Status.ACTIVE && now >= deal.nextTime()) {
                regularDelivery(server, deal, now);
            }
        }
        state.trade().prune(now);
        for (Caravan caravan : new ArrayList<>(state.trade().caravans())) {
            if (caravan.state() == Caravan.State.RETURNING) {
                goBack(server, caravan);
            } else if (now >= caravan.arriveTime()) {
                arrive(server, caravan, now);
            }
        }
        for (Market market : new ArrayList<>(state.trade().markets())) {
            for (Market.ImportOrder order : new ArrayList<>(market.imports())) {
                if (now >= order.arrivalTime()) {
                    importArrives(server, market.kingdomId(), order);
                }
            }
        }
        syncCaravans(server);
    }

    private static void regularDelivery(MinecraftServer server, Deal deal, long now) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        long next = deal.nextTime() + (long) Math.max(1, deal.periodDays()) * CitizenNeeds.TICKS_PER_DAY;
        if (io.github.sekerator.kingdomworld.common.diplomacy.WarWork.frozen(server, deal.seller(), deal.buyer())) {
            // A deal between enemies waits for the peace without counting failures.
            state.trade().putDeal(deal.withNext(Math.max(next, now + 1), deal.deliveries(), deal.failures()));
            return;
        }
        Outcome delivered = deliver(server, deal);
        if (delivered.ok()) {
            state.trade().putDeal(deal.withNext(Math.max(next, now + 1), deal.deliveries() + 1, 0));
            return;
        }
        int failures = deal.failures() + 1;
        Component reason = KingdomActions.errorMessage(delivered.error());
        if (failures >= Deal.MAX_FAILURES) {
            state.trade().putDeal(deal.withStatus(Deal.Status.CANCELLED).withNext(now, deal.deliveries(), failures));
            for (UUID kingdom : List.of(deal.seller(), deal.buyer())) {
                notify(server, kingdom, Component.translatable(KEY + "called_off", kingdomName(server, deal.partnerOf(kingdom)), reason)
                        .withStyle(ChatFormatting.GOLD));
            }
        } else {
            state.trade().putDeal(deal.withNext(Math.max(next, now + 1), deal.deliveries(), failures));
            for (UUID kingdom : List.of(deal.seller(), deal.buyer())) {
                notify(server, kingdom, Component.translatable(KEY + "skipped", kingdomName(server, deal.partnerOf(kingdom)), reason)
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /**
     * A caravan reached the buyer's capital: it is lost by chance, or the goods go into the buyer's storehouses and the
     * payment to the seller. Without room it waits.
     */
    public static void arrive(MinecraftServer server, Caravan caravan, long now) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        long day = day(server);
        if (caravan.state() == Caravan.State.TRAVELING) {
            // Robbers on the pieces of way not reckoned with yet; what they took went into their camp.
            Optional<Caravan> left = io.github.sekerator.kingdomworld.common.road.RoadCaravans.beforeArrival(server, caravan, roll);
            if (left.isEmpty()) {
                LOGGER.info("A caravan from {} to {} was robbed of everything on the way", kingdomName(server, caravan.seller()).getString(),
                        kingdomName(server, caravan.buyer()).getString());
                return;
            }
            caravan = left.get();
        }
        Component goods = cargoText(caravan);
        List<ItemStack> stacks = MarketStock.stacks(caravan.cargo());
        if (state.kingdoms().get(caravan.buyer()).isEmpty()) {
            goBack(server, caravan.withState(Caravan.State.RETURNING));
            return;
        }
        if (caravan.state() == Caravan.State.WAITING && now - caravan.arriveTime() >= TradeSettings.caravanMaxWaitTicks()) {
            giveUpWaiting(server, caravan, now);
            return;
        }
        if (!KingdomStorage.fits(server, caravan.buyer(), stacks)) {
            if (caravan.state() != Caravan.State.WAITING) {
                state.trade().putCaravan(caravan.withState(Caravan.State.WAITING));
                notify(server, caravan.buyer(), Component.translatable(KEY + "waiting", goods, kingdomName(server, caravan.seller()))
                        .withStyle(ChatFormatting.GOLD));
            }
            return;
        }
        WorldPos near = new WorldPos(caravan.dimension(), caravan.toX(), 64, caravan.toZ());
        List<ItemStack> rest = MarketStock.putBack(server, caravan.buyer(), stacks, near);
        if (!rest.isEmpty()) {
            // The storehouses took less than they said: the rest stays on the caravan, which waits for room.
            state.trade().putCaravan(new Caravan(caravan.id(), caravan.dealId(), caravan.seller(), caravan.buyer(), MarketStock.goods(rest),
                    caravan.currency(), caravan.payment(), caravan.escrowId(), caravan.dimension(), caravan.fromX(), caravan.fromZ(),
                    caravan.toX(), caravan.toZ(), caravan.departTime(), caravan.arriveTime(), Caravan.State.WAITING));
            return;
        }
        state.economy().releaseEscrow(caravan.escrowId(), day);
        state.trade().removeCaravan(caravan.id());
        Component money = MoneyTexts.amount(caravan.payment(), Purses.units(server, caravan.currency()));
        LOGGER.info("A caravan from {} arrived at {}", kingdomName(server, caravan.seller()).getString(), kingdomName(server, caravan.buyer()).getString());
        notify(server, caravan.seller(), Component.translatable(KEY + "arrived_seller", goods, kingdomName(server, caravan.buyer()), money)
                .withStyle(ChatFormatting.GREEN));
        notify(server, caravan.buyer(), Component.translatable(KEY + "arrived_buyer", goods, kingdomName(server, caravan.seller()))
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * A caravan waited too long for room in the storehouses of the buyer: the deal is closed, the payment goes back to the
     * buyer (both journals record it) and the goods go back to the seller.
     */
    private static void giveUpWaiting(MinecraftServer server, Caravan caravan, long now) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Component goods = cargoText(caravan);
        state.economy().refundEscrow(caravan.escrowId(), day(server));
        state.trade().deal(caravan.dealId()).filter(deal -> !deal.status().closed())
                .ifPresent(deal -> state.trade().putDeal(deal.withStatus(Deal.Status.CANCELLED).withNext(now, deal.deliveries(), deal.failures())));
        Caravan back = caravan.withState(Caravan.State.RETURNING);
        state.trade().putCaravan(back);
        LOGGER.info("A caravan from {} waited too long at {} and goes back; the payment was returned",
                kingdomName(server, caravan.seller()).getString(), kingdomName(server, caravan.buyer()).getString());
        notify(server, caravan.buyer(), Component.translatable(KEY + "wait_over_buyer", goods, kingdomName(server, caravan.seller()))
                .withStyle(ChatFormatting.GOLD));
        notify(server, caravan.seller(), Component.translatable(KEY + "wait_over_seller", goods, kingdomName(server, caravan.buyer()))
                .withStyle(ChatFormatting.GOLD));
        goBack(server, back);
    }

    /**
     * A caravan that was called off brings its goods back to the seller; if the seller is gone, they are lost.
     */
    private static void goBack(MinecraftServer server, Caravan caravan) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (state.kingdoms().get(caravan.seller()).isEmpty()) {
            state.trade().removeCaravan(caravan.id());
            return;
        }
        List<ItemStack> stacks = MarketStock.stacks(caravan.cargo());
        if (!KingdomStorage.fits(server, caravan.seller(), stacks)) {
            state.trade().putCaravan(caravan.withState(Caravan.State.RETURNING));
            return;
        }
        WorldPos near = new WorldPos(caravan.dimension(), caravan.fromX(), 64, caravan.fromZ());
        List<ItemStack> rest = MarketStock.putBack(server, caravan.seller(), stacks, near);
        if (rest.isEmpty()) {
            state.trade().removeCaravan(caravan.id());
            notify(server, caravan.seller(), Component.translatable(KEY + "returned", cargoText(caravan)));
        } else {
            state.trade().putCaravan(new Caravan(caravan.id(), caravan.dealId(), caravan.seller(), caravan.buyer(), MarketStock.goods(rest),
                    caravan.currency(), caravan.payment(), caravan.escrowId(), caravan.dimension(), caravan.fromX(), caravan.fromZ(), caravan.toX(),
                    caravan.toZ(), caravan.departTime(), caravan.arriveTime(), Caravan.State.RETURNING));
        }
    }

    static Component cargoText(Caravan caravan) {
        Goods first = caravan.cargo().get(0);
        return Component.translatable(KEY + "goods", goodsName(first), TradeTexts.items(caravan.items()));
    }

    /**
     * Goods ordered from abroad arrived: into the storehouses, or they wait for room.
     */
    private static void importArrives(MinecraftServer server, UUID kingdomId, Market.ImportOrder order) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        List<ItemStack> stacks = MarketStock.stacks(List.of(Goods.of(order.item(), order.count())));
        if (stacks.isEmpty()) {
            state.trade().takeImport(kingdomId, order.id());
            return;
        }
        if (!KingdomStorage.fits(server, kingdomId, stacks)) {
            return;
        }
        Optional<Market.ImportOrder> taken = state.trade().takeImport(kingdomId, order.id());
        if (taken.isEmpty()) {
            return;
        }
        List<ItemStack> rest = MarketStock.putBack(server, kingdomId, stacks, null);
        if (!rest.isEmpty()) {
            // The storehouses took less than they said: the rest arrives again later.
            int left = rest.stream().mapToInt(ItemStack::getCount).sum();
            state.trade().addImport(kingdomId, order.item(), left, 0, order.orderedTime(), order.arrivalTime(), TradeSettings.rules());
        }
        state.kingdoms().get(kingdomId).ifPresent(kingdom -> KingdomActions.notifyMembers(server, kingdom, Permission.MANAGE_MARKET,
                Component.translatable(KEY + "import_arrived", stacks.get(0).getHoverName(), TradeTexts.items(order.count()))));
    }

    // ---------------------------------------------------------------- abroad

    /**
     * One simulation step of a kingdom's trade abroad: its merchants sell what the storehouses hold above the king's
     * thresholds, within the day's volume of the merchants and the day's limit of income.
     */
    public static void exportStep(MinecraftServer server, UUID kingdomId, int intervalTicks) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (KingdomWorldApi.isReadOnly(server) || state.economy().treasury(kingdomId).isEmpty()) {
            return;
        }
        TradeRules rules = TradeSettings.rules();
        Optional<Market> found = state.trade().existing(kingdomId);
        if (found.isEmpty() || found.get().exports().isEmpty()) {
            return;
        }
        Market market = found.get();
        List<Citizen> merchants = MerchantBehavior.working(server, kingdomId);
        if (merchants.isEmpty()) {
            return;
        }
        long volume = 0;
        for (Citizen merchant : merchants) {
            volume += rules.exportVolume(merchant.experience());
        }
        long day = day(server);
        Market.DayStats today = market.stats(day);
        // The day's volume is spread over the day, so that a day's surplus does not leave in one step.
        long perStep = Math.max(1, (volume * Math.max(1, intervalTicks) + CitizenNeeds.TICKS_PER_DAY - 1) / CitizenNeeds.TICKS_PER_DAY);
        long allowance = Math.min(perStep, volume - today.exported());
        long incomeLeft = rules.exportMaxIncomePerDay() - today.exportIncome();
        if (allowance <= 0 || incomeLeft <= 0) {
            return;
        }
        int sold = 0;
        long income = 0;
        for (Market.ExportRule rule : market.exports()) {
            if (allowance <= 0 || incomeLeft <= 0) {
                break;
            }
            Item item = GoodsStacks.item(rule.item());
            if (item == null) {
                continue;
            }
            long surplus = MarketStock.free(server, kingdomId, item) - rule.above();
            if (surplus <= 0) {
                continue;
            }
            ItemValues.Value worth = MarketStock.worth(server, item);
            int count = (int) Math.min(Math.min(surplus, allowance), Integer.MAX_VALUE);
            long money = Prices.exportIncome(worth.amount(), count, rules);
            if (money > incomeLeft) {
                // Only as many as the day's limit of income still pays for.
                long each = Math.max(1, Prices.exportIncome(worth.amount(), 1_000, rules));
                count = (int) Math.min(count, incomeLeft * 1_000 / each);
                money = Math.min(incomeLeft, Prices.exportIncome(worth.amount(), count, rules));
            }
            if (count <= 0 || money <= 0) {
                continue;
            }
            List<ItemStack> taken = MarketStock.take(server, kingdomId, item, count, null);
            if (taken == null) {
                continue;
            }
            Outcome paid = state.economy().exportIncome(kingdomId, money, day);
            if (!paid.ok()) {
                MarketStock.putBack(server, kingdomId, taken, null);
                break;
            }
            sold += count;
            income += money;
            allowance -= count;
            incomeLeft -= money;
        }
        if (sold > 0) {
            state.trade().recordExport(kingdomId, day, sold, income, rules);
            // Merchants learn by selling, a point for every eight items.
            int share = Math.max(1, sold / 8 / merchants.size());
            for (Citizen merchant : merchants) {
                merchant.addExperience(share);
            }
            LOGGER.debug("Merchants of {} sold {} item(s) abroad for {}", kingdomName(server, kingdomId).getString(), sold, income);
        }
    }

    // ---------------------------------------------------------------- exchange rates

    /**
     * Once a game day: the rate of every currency moves towards the wealth of its kingdom over its money.
     */
    public static void updateRates(MinecraftServer server) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        long day = day(server);
        Economy economy = state.economy();
        if (KingdomWorldApi.isReadOnly(server) || day <= economy.rateDay()) {
            return;
        }
        TradeRules rules = TradeSettings.rules();
        Map<UUID, Long> targets = new HashMap<>();
        for (Kingdom kingdom : state.kingdoms().all()) {
            Optional<Treasury> treasury = economy.treasury(kingdom.id());
            if (treasury.isEmpty()) {
                continue;
            }
            ExchangeRates.Wealth wealth = wealth(server, kingdom.id(), day);
            targets.put(kingdom.id(), ExchangeRates.target(wealth.total(rules.productionDays()), economy.moneyMass(kingdom.id()), rules));
        }
        economy.updateRates(day, targets, rules);
        io.github.sekerator.kingdomworld.common.economy.EconomyEvents.currenciesChanged();
    }

    /**
     * What a kingdom owns, in units of worth.
     */
    public static ExchangeRates.Wealth wealth(MinecraftServer server, UUID kingdomId, long day) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Economy economy = state.economy();
        ItemValues table = ItemPrices.table(server);
        long goods = 0;
        for (KingdomStorage.Total total : KingdomStorage.totals(server, kingdomId)) {
            goods = Money.addCapped(goods, (long) Math.min(Money.MAX, ItemPrices.value(server, total.sample()).amount() * total.count()));
        }
        Treasury treasury = economy.treasury(kingdomId).orElseThrow();
        long coins = ExchangeRates.worth(treasury.vault().value(treasury.units()), economy.rate(kingdomId));
        long reserves = 0;
        for (Map.Entry<UUID, Long> reserve : treasury.reserves().entrySet()) {
            reserves = Money.addCapped(reserves, ExchangeRates.worth(reserve.getValue(), economy.rate(reserve.getKey())));
        }
        long production = 0;
        ProductionLedger.Day yesterday = state.citizens().ledger().yesterday(kingdomId, day);
        for (Map<String, Long> byItem : yesterday.counts().values()) {
            for (Map.Entry<String, Long> entry : byItem.entrySet()) {
                if (!ProductionLedger.OTHER.equals(entry.getKey())) {
                    production = Money.addCapped(production, (long) Math.min(Money.MAX, table.value(entry.getKey()).amount() * entry.getValue()));
                }
            }
        }
        return new ExchangeRates.Wealth(goods, coins, reserves, production);
    }

    // ---------------------------------------------------------------- caravans on the map

    public static List<TradeViews.CaravanInfo> caravans(MinecraftServer server) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        List<TradeViews.CaravanInfo> list = new ArrayList<>();
        for (Caravan caravan : state.trade().caravans()) {
            if (caravan.state() == Caravan.State.RETURNING || list.size() >= 4_096) {
                continue;
            }
            int color = state.kingdoms().get(caravan.seller()).map(Kingdom::color).orElse(0x808080);
            // The way through the roads, at most so many points of it.
            List<io.github.sekerator.kingdomworld.core.road.TravelPlan.Point> points =
                    io.github.sekerator.kingdomworld.common.road.RoadCaravans.points(server, caravan);
            List<Long> way = new ArrayList<>();
            List<Long> wayTicks = new ArrayList<>();
            int step = Math.max(1, (points.size() + TradeViews.CaravanInfo.MAX_WAY - 2) / (TradeViews.CaravanInfo.MAX_WAY - 1));
            for (int i = 0; i < points.size(); i += step) {
                way.add(TradeViews.CaravanInfo.pack(points.get(i).x(), points.get(i).z()));
                wayTicks.add(points.get(i).tick());
            }
            if (!points.isEmpty() && (points.size() - 1) % step != 0) {
                way.add(TradeViews.CaravanInfo.pack(points.get(points.size() - 1).x(), points.get(points.size() - 1).z()));
                wayTicks.add(points.get(points.size() - 1).tick());
            }
            list.add(new TradeViews.CaravanInfo(caravan.id(), caravan.seller(), caravan.buyer(), color, caravan.dimension(), caravan.fromX(),
                    caravan.fromZ(), caravan.toX(), caravan.toZ(), caravan.departTime(), caravan.arriveTime(), way, wayTicks));
        }
        return list;
    }

    private static void syncCaravans(MinecraftServer server) {
        List<TradeViews.CaravanInfo> now = caravans(server);
        if (!now.equals(lastSent)) {
            lastSent = List.copyOf(now);
            KingdomWorldNetwork.sendToAll(new TradePackets.Caravans(now));
        }
    }
}
