package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.life.LifeWork;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.ShopView;
import io.github.sekerator.kingdomworld.common.network.packet.CommercePackets;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.CityDay;
import io.github.sekerator.kingdomworld.core.commerce.CraftRules;
import io.github.sekerator.kingdomworld.core.commerce.Evasion;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import io.github.sekerator.kingdomworld.core.commerce.ShopMaking;
import io.github.sekerator.kingdomworld.core.commerce.ShopPricing;
import io.github.sekerator.kingdomworld.core.commerce.Trade;
import io.github.sekerator.kingdomworld.core.commerce.TradeRegistry;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.economy.CityEconomyRules;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.life.DaySchedule;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.trade.Market;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Players in the shops of citizens: a click on a counter or on a keeper opens the window of the shop, a click on a person of a
 * house in the evening that of the family shop of the house. A player buys from the goods of the shop, whole items that fit into
 * the inventory, from the account or with coins in the currency of the kingdom of the shop only: the price into the till, the
 * tax into the treasury of the city. A player sells a shop what it can sell on at a profit (goods of its trade, raw materials of
 * its workshop) for a share of the lower of the price of the crown and the worth, onto the account, out of the till. Strangers are
 * served while the market of the kingdom is open to them. A member with the right over citizens fixes the trade of the place.
 */
public final class ShopActions {
    private static final String KEY = "kingdomworld.screen.shop.";
    /** How far from the shop or its keeper a player buys. */
    private static final double REACH = 8.0;
    /** The page each player looks at, by shop. */
    private static final Map<UUID, Integer> PAGES = new HashMap<>();

    private ShopActions() {
    }

    // ---------------------------------------------------------------- opening

    /**
     * A click on a counter.
     */
    public static void openAtCounter(ServerPlayer player, BlockPos pos) {
        String key = "counter/" + CommerceSavedData.counterKey(Dimensions.id(player.level()), pos.getX(), pos.getY(), pos.getZ());
        Optional<Business> shop = CommerceSavedData.get(player.server).at(key);
        if (shop.isEmpty()) {
            player.sendSystemMessage(Component.translatable("kingdomworld.shop.counter.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        open(player, shop.get(), true);
    }

    /**
     * A click on a citizen: its shop if it keeps one, or the family shop of its house in the evening.
     *
     * @return whether a shop window was opened
     */
    public static boolean openForCitizen(ServerPlayer player, CitizenEntity body) {
        MinecraftServer server = player.server;
        if (body.citizenId() == null || player.isShiftKeyDown()) {
            return false;
        }
        Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(body.citizenId());
        if (citizen.isEmpty()) {
            return false;
        }
        CommerceSavedData data = CommerceSavedData.get(server);
        if (citizen.get().business() != 0) {
            Optional<Business> shop = data.shop(citizen.get().business());
            if (shop.isPresent()) {
                open(player, shop.get(), true);
                return true;
            }
        }
        if (LifeWork.phase(server, citizen.get().id()) != DaySchedule.Phase.EVENING) {
            return false;
        }
        for (Business shop : data.shopsOf(citizen.get().kingdomId())) {
            if (!shop.family()) {
                continue;
            }
            Optional<Building> house = KingdomWorldApi.state(server).construction().building(shop.place().building());
            if (house.isPresent() && citizen.get().home() != null && house.get().box().contains(citizen.get().home().x(), citizen.get().home().y(),
                    citizen.get().home().z())) {
                open(player, shop, true);
                return true;
            }
        }
        return false;
    }

    public static void page(ServerPlayer player, long shopId, int page) {
        PAGES.put(player.getUUID(), page);
        CommerceSavedData.get(player.server).shop(shopId).ifPresent(shop -> open(player, shop, false));
    }

    /**
     * Opens the window of a shop for a player, for the self-check of the client.
     */
    public static void openShop(ServerPlayer player, long shopId) {
        CommerceSavedData.get(player.server).shop(shopId).ifPresent(shop -> open(player, shop, true));
    }

    private static void open(ServerPlayer player, Business shop, boolean open) {
        if (open) {
            PAGES.remove(player.getUUID());
        }
        KingdomWorldNetwork.sendToPlayer(player, new CommercePackets.ShopOpen(view(player, shop), open));
    }

    // ---------------------------------------------------------------- the view

    /**
     * Why a player may not trade with a shop now, or empty when it may.
     */
    static Optional<String> refusal(ServerPlayer player, Business shop) {
        MinecraftServer server = player.server;
        if (!near(player, shop)) {
            return Optional.of(KEY + "too_far");
        }
        if (shop.family()) {
            boolean evening = false;
            for (Citizen resident : residents(server, shop)) {
                if (LifeWork.phase(server, resident.id()) == DaySchedule.Phase.EVENING) {
                    evening = true;
                    break;
                }
            }
            if (!evening) {
                return Optional.of(KEY + "evening_only");
            }
        } else {
            boolean able = false;
            for (UUID keeper : shop.keepers()) {
                Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(keeper);
                if (citizen.isPresent() && citizen.get().state() != CitizenState.WOUNDED && !LifeWork.isSick(keeper)) {
                    able = true;
                    break;
                }
            }
            if (!able) {
                return Optional.of(KEY + "keeper_unwell");
            }
        }
        boolean member = KingdomWorldApi.state(server).membership().isMember(player.getUUID(), shop.kingdomId());
        if (!member && !KingdomWorldApi.state(server).trade().market(shop.kingdomId(), TradeSettings.rules()).openToOutsiders()) {
            return Optional.of(KEY + "strangers");
        }
        return Optional.empty();
    }

    private static List<Citizen> residents(MinecraftServer server, Business shop) {
        Optional<Building> house = KingdomWorldApi.state(server).construction().building(shop.place().building());
        return house.isEmpty() ? List.of() : Shops.residents(house.get(), KingdomWorldApi.state(server).citizens().registry().citizensOf(shop.kingdomId()));
    }

    /**
     * Whether a player is near enough to a shop: to its counter, its building, or one of its keepers.
     */
    static boolean near(ServerPlayer player, Business shop) {
        Business.Place place = shop.place();
        if (!Dimensions.id(player.level()).equals(place.dimension())) {
            return false;
        }
        double reach = REACH * REACH;
        if (player.distanceToSqr(place.x() + 0.5, place.y() + 0.5, place.z() + 0.5) <= reach) {
            return true;
        }
        if (place.kind() != Business.PlaceKind.COUNTER) {
            Optional<Building> building = KingdomWorldApi.state(player.server).construction().building(place.building());
            if (building.isPresent()) {
                var box = building.get().box();
                double dx = Math.max(0, Math.max(box.minX() - player.getX(), player.getX() - box.maxX() - 1));
                double dy = Math.max(0, Math.max(box.minY() - player.getY(), player.getY() - box.maxY() - 1));
                double dz = Math.max(0, Math.max(box.minZ() - player.getZ(), player.getZ() - box.maxZ() - 1));
                if (dx * dx + dy * dy + dz * dz <= reach) {
                    return true;
                }
            }
        }
        for (var entity : player.level().getEntitiesOfClass(CitizenEntity.class, player.getBoundingBox().inflate(REACH))) {
            if (entity.citizenId() != null && shop.keepers().contains(entity.citizenId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * What goods of a shop cost a player without the tax, in whole coins, for a number of them: the price of the shop as the
     * reputation of the player makes it, never below what they cost the shop with the work in them.
     */
    static long playerTotal(MinecraftServer server, Business shop, UUID player, Business.Lot lot, Item item, long count) {
        long asked = ShopMaking.askedMilli(lot, shop.marginPermille(), Shops.ceilingMilli(server, item), Shops.worthMilli(server, item));
        long total = ShopPricing.total(asked, count);
        long reputation = LawWork.priceFor(server, shop.kingdomId(), player, total);
        return Math.max(ShopPricing.total(lot.baseMilli(), count), reputation);
    }

    static ShopView view(ServerPlayer player, Business shop) {
        MinecraftServer server = player.server;
        Economy economy = KingdomWorldApi.state(server).economy();
        int tax = CommerceWork.salesTax(server, shop.kingdomId(), shop.cityId());
        List<ShopView.Row> rows = new ArrayList<>();
        List<Map.Entry<String, Business.Lot>> goods = new ArrayList<>(shop.goods().entrySet());
        Map<String, Long> unit = new HashMap<>();
        for (Map.Entry<String, Business.Lot> entry : goods) {
            Item item = GoodsStacks.item(Business.itemOf(entry.getKey()));
            unit.put(entry.getKey(), item == null ? Long.MAX_VALUE : playerTotal(server, shop, player.getUUID(), entry.getValue(), item, 1));
        }
        goods.sort(Comparator.comparingLong((Map.Entry<String, Business.Lot> entry) -> unit.get(entry.getKey())).thenComparing(Map.Entry::getKey));
        int page = Math.max(0, Math.min(PAGES.getOrDefault(player.getUUID(), 0), (goods.size() - 1) / ShopView.PAGE));
        for (int i = page * ShopView.PAGE; i < goods.size() && rows.size() < ShopView.PAGE; i++) {
            Map.Entry<String, Business.Lot> entry = goods.get(i);
            Item item = GoodsStacks.item(Business.itemOf(entry.getKey()));
            if (item == null) {
                continue;
            }
            long total = unit.get(entry.getKey());
            long taxOf = ShopPricing.tax(total, tax);
            rows.add(new ShopView.Row(new ItemStack(item), entry.getValue().count(), total + taxOf, taxOf, entry.getKey(), entry.getValue().made()));
        }
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, shop.kingdomId());
        // A kingdom of the world is named in the alphabet of the player.
        String kingdomName = kingdom.map(found -> io.github.sekerator.kingdomworld.common.npc.NpcWork.localKingdom(found.id(), found.name(),
                player.getLanguage())).orElse("?");
        var treasury = economy.treasury(shop.kingdomId());
        String currency = MoneyTexts.currencyShort(treasury.map(t -> t.currencyName()).orElse(""), kingdomName).getString();
        long silver = treasury.map(t -> t.units().silver()).orElse(io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT.silver());
        long gold = treasury.map(t -> t.units().gold()).orElse(io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT.gold());
        Optional<String> refused = refusal(player, shop);
        long day = TreasuryActions.day(server);
        return new ShopView(shop.id(), shop.kingdomId(), kingdomName.length() > ShopView.MAX_NAME ? kingdomName.substring(0, ShopView.MAX_NAME) : kingdomName,
                currency.length() > ShopView.MAX_NAME ? currency.substring(0, ShopView.MAX_NAME) : currency, silver, gold, shop.trade(),
                Shops.keeperNames(server, shop), shop.status().id(), rows, page, goods.size(), shop.marginPermille(), tax,
                shop.averageSales(day + 1), shop.debt(), refused.isEmpty(), refused.orElse(""), workshop(player, shop), sellRows(player, shop));
    }

    /**
     * The trade of a shop and its place for its window.
     */
    private static ShopView.Workshop workshop(ServerPlayer player, Business shop) {
        MinecraftServer server = player.server;
        Optional<Set<String>> stations = PlaceSurvey.stations(server, shop.place().key());
        List<String> trades = new ArrayList<>();
        for (Trade trade : TradeRegistry.current().all()) {
            if (fixable(server, shop, trade, stations.orElse(Set.of()))) {
                trades.add(trade.id());
            }
        }
        boolean canPin = KingdomWorldApi.state(server).membership().hasPermission(player.getUUID(), shop.kingdomId(), Permission.MANAGE_CITIZENS);
        return new ShopView.Workshop(new ArrayList<>(stations.orElse(Set.of())), stations.isPresent(), shop.pinned(), shop.nextTrade(), trades,
                canPin);
    }

    /**
     * Whether a trade may be fixed for the place of a shop: the general store anywhere, a craft where its workstations stand, a
     * trading house in a house of a merchant or where a market works.
     */
    private static boolean fixable(MinecraftServer server, Business shop, Trade trade, Set<String> stations) {
        if (trade.kind() == Trade.Kind.RESALE) {
            return true;
        }
        if (trade.craft()) {
            return trade.fits(stations);
        }
        if (shop.place().kind() == Business.PlaceKind.MERCHANT_HOUSE) {
            return true;
        }
        for (BusinessPlaces.Spot spot : BusinessPlaces.of(server, shop.kingdomId(), shop.cityId())) {
            if (spot.place().kind() == Business.PlaceKind.MARKET_STALL) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- buying

    public static void buy(ServerPlayer player, CommercePackets.ShopTrade trade) {
        MinecraftServer server = player.server;
        Optional<Business> found = CommerceSavedData.get(server).shop(trade.shopId());
        if (found.isEmpty()) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail(KEY + "gone"));
            return;
        }
        Business shop = found.get();
        KingdomActions.reply(player, purchase(player, shop, trade));
        open(player, shop, false);
    }

    public static KingdomActions.Feedback purchase(ServerPlayer player, Business shop, CommercePackets.ShopTrade trade) {
        MinecraftServer server = player.server;
        if (KingdomWorldApi.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        Optional<String> refused = refusal(player, shop);
        if (refused.isPresent()) {
            return KingdomActions.Feedback.fail(refused.get());
        }
        Business.Lot lot = shop.goods().get(trade.item());
        Item item = GoodsStacks.item(Business.itemOf(trade.item()));
        if (lot == null || item == null || lot.count() < trade.count()) {
            return KingdomActions.Feedback.fail(KEY + "less_stock");
        }
        UUID kingdom = shop.kingdomId();
        int taxRate = CommerceWork.salesTax(server, kingdom, shop.cityId());
        long unit = playerTotal(server, shop, player.getUUID(), lot, item, 1);
        if (unit + ShopPricing.tax(unit, taxRate) > trade.seen()) {
            return KingdomActions.Feedback.fail(KEY + "price_changed");
        }
        long total = playerTotal(server, shop, player.getUUID(), lot, item, trade.count());
        long tax = ShopPricing.tax(total, taxRate);
        long cost = total + tax;
        if (!Money.isPayment(cost)) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        List<ItemStack> stacks = new ArrayList<>();
        long left = trade.count();
        while (left > 0) {
            int part = (int) Math.min(left, item.getMaxStackSize());
            stacks.add(new ItemStack(item, part));
            left -= part;
        }
        if (!GoodsStacks.fits(player.getInventory(), stacks)) {
            return KingdomActions.Feedback.error(KingdomError.NO_ROOM);
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
        CityPurses.PurseKey treasury = CityPurses.PurseKey.cityTreasury(kingdom, shop.cityId());
        long collected = Evasion.collected(tax, CommerceWork.evasion(server, kingdom, shop.cityId()));
        long toTill = cost - collected;
        Object prepared = Purses.preparePayment(player, kingdom, kingdom, cost, trade.account(),
                payer -> economy.checkPlayerPurchase(payer, till, treasury, toTill, collected));
        if (prepared instanceof KingdomActions.Feedback feedback) {
            if (!trade.account() && Purses.plan(player, kingdom, cost).isEmpty()) {
                // Coins of another currency do not count: the shop takes only its own.
                return KingdomActions.Feedback.fail(KEY + "only_currency", currencyName(server, kingdom));
            }
            return feedback;
        }
        Purses.Payment payment = (Purses.Payment) prepared;
        if (payment.payer() == null && !Purses.take(player, kingdom, payment.coins())) {
            return KingdomActions.Feedback.error(KingdomError.NOT_ENOUGH_MONEY);
        }
        long day = TreasuryActions.day(server);
        Outcome paid = economy.playerPurchase(payment.payer(), till, treasury, toTill, collected, day);
        if (!paid.ok()) {
            if (payment.payer() == null) {
                Purses.giveBack(player, kingdom, payment.coins());
            }
            return KingdomActions.Feedback.error(paid.error());
        }
        Purses.change(player, payment);
        long soldCost = ShopPricing.total(lot.costMilli(), trade.count());
        shop.removeGoods(trade.item(), trade.count());
        for (ItemStack stack : stacks) {
            GoodsStacks.give(player, stack);
        }
        long[] categories = new long[GoodsCategory.values().length];
        if (lot.category() >= 0 && lot.category() < categories.length) {
            categories[lot.category()] = (long) Math.floor(Shops.worth(server, item) * trade.count());
        }
        shop.record(day, new Business.Day(toTill, soldCost, 0, 0, categories));
        CommerceSavedData.get(server).setDirty();
        CityStatement.Tally tally = economy.cityTally(kingdom, shop.cityId());
        tally.add(CityStatement.Line.SALES, total);
        tally.add(CityStatement.Line.PLAYERS, cost);
        LawWork.traded(server, kingdom, player.getUUID(), cost);
        return KingdomActions.Feedback.ok(KEY + "bought", trade.count(), new ItemStack(item).getHoverName(),
                MoneyTexts.amount(cost, Purses.units(server, kingdom)));
    }

    private static String currencyName(MinecraftServer server, UUID kingdom) {
        return MoneyTexts.currencyShort(KingdomWorldApi.state(server).economy().treasury(kingdom).map(t -> t.currencyName()).orElse(""),
                KingdomWorldApi.kingdom(server, kingdom).map(Kingdom::name).orElse("?")).getString();
    }

    // ---------------------------------------------------------------- selling

    /**
     * What a shop pays a player for one of an item and how many it takes.
     *
     * @param unit  what it pays for one
     * @param max   how many it takes by its stock and its needs
     * @param money how many its till can pay for
     * @param goods whether the item goes among its goods, else among the raw materials of its workshop
     */
    record SellOffer(long unit, long max, long money, boolean goods) {
    }

    /**
     * What a shop would pay a player for items: the share of the lower of the price of the crown and the worth for an item the
     * crown sells, else the share of its own price without the tax, both never above the worth, the reputation of the player too.
     * It takes goods of its trade it can sell at least a little above that under the ceiling of their price, up to its target, and
     * the raw materials of what its workshop makes under that ceiling, as much as a day of its work uses; the general store takes
     * goods only of the kinds it lacks. Empty when it takes none of the item.
     *
     * @param counts the count of each item in the storehouses
     */
    static Optional<SellOffer> sellOffer(MinecraftServer server, Business shop, UUID player, Item item, Map<String, Long> counts) {
        if (shop.trader() || shop.status() == Business.Status.CLOSING || !shop.nextTrade().isEmpty()) {
            return Optional.empty();
        }
        UUID kingdom = shop.kingdomId();
        CraftRules craft = CraftSettings.rules();
        CityEconomyRules rules = CityEconomySettings.rules();
        String id = GoodsStacks.itemId(item);
        double worth = Shops.worth(server, item);
        long stock = counts.getOrDefault(id, 0L);
        Market market = KingdomWorldApi.state(server).trade().market(kingdom, TradeSettings.rules());
        Optional<Market.SellEntry> entry = market.sell(id);
        boolean crownSells = entry.isPresent() && entry.get().price() > 0 || stock > 0;
        long crown = crownSells ? Shops.crownPrice(server, kingdom, id, item, stock) : 0;
        long unit = ShopPricing.playerSellUnit(crownSells, crown, crownSells ? 0 : ownPriceMilli(server, shop, id), worth, craft.playerSellPercent(),
                craft.playerSellOwnPercent());
        unit = ShopPricing.capToWorth(LawWork.payoutFor(server, kingdom, player, unit), worth);
        if (unit < 1) {
            return Optional.empty();
        }
        int category = Shops.categoryOf(server, item);
        Trade trade = Workshops.trade(shop);
        long freeMoney = Math.max(0, KingdomWorldApi.state(server).economy().purse(CityPurses.PurseKey.till(kingdom, shop.id())) - shop.profitDue());
        long byMoney = freeMoney / unit;
        long day = TreasuryActions.day(server);
        double strength = Shops.strength(server, shop, 0, rules);
        if (category >= 0 && (trade.makes(category) && shop.crafts() || !shop.crafts())) {
            if (!ShopPricing.worthBuying(unit * ShopPricing.MILLI, Shops.ceilingMilli(server, item))) {
                return Optional.empty();
            }
            GoodsCategory kind = GoodsCategory.values()[category];
            long average = shop.averageCategory(day, category);
            long target = shop.crafts()
                    ? ShopMaking.target(average, strength, ShopMaking.sharePercent(kind, rules.basket()), rules.stockDays(), 0)
                    : CityDay.stockTarget(average, strength, ShopMaking.sharePercent(kind, rules.basket()), rules.stockDays());
            long lacking = target - Shops.heldWorth(server, shop, category);
            long max = lacking <= 0 ? 0 : (long) Math.floor(lacking / Math.max(0.01, worth));
            if (max <= 0 && !shop.crafts()) {
                return Optional.empty();
            }
            return Optional.of(new SellOffer(unit, Math.max(0, max), byMoney, true));
        }
        if (!shop.crafts()) {
            return Optional.empty();
        }
        long rawMax = rawWanted(server, shop, trade, id, unit, counts);
        if (rawMax < 0) {
            return Optional.empty();
        }
        return Optional.of(new SellOffer(unit, rawMax, byMoney, false));
    }

    /**
     * The price the shop asks for an item without the tax, in thousandths, or 0 when it has none.
     */
    private static long ownPriceMilli(MinecraftServer server, Business shop, String id) {
        long best = 0;
        for (boolean made : new boolean[]{false, true}) {
            String key = Business.key(id, made);
            Business.Lot lot = shop.goods().get(key);
            if (lot != null) {
                long asked = Shops.askedMilli(server, shop, key, lot);
                best = best == 0 ? asked : Math.min(best, asked);
            }
        }
        return best;
    }

    /**
     * How many of a raw material the workshop of a shop takes at a price: as much as a day of its target work uses beyond what it
     * holds, of what it makes with it under the ceiling of the price; below 0 when it makes nothing of it at that price.
     */
    private static long rawWanted(MinecraftServer server, Business shop, Trade trade, String id, long unit, Map<String, Long> counts) {
        Set<String> stations = Workshops.placeStations(server, shop);
        if (!trade.fits(stations)) {
            return -1;
        }
        UUID kingdom = shop.kingdomId();
        ShopMaking.Context context = Workshops.context(server, trade, stations, other -> {
            if (other.equals(id)) {
                return unit * ShopPricing.MILLI;
            }
            Item item = GoodsStacks.item(other);
            long stock = counts.getOrDefault(other, 0L);
            return item == null || stock <= 0 ? -1 : Shops.crownPrice(server, kingdom, other, item, stock) * ShopPricing.MILLI;
        }, null);
        ShopMaking.Planner planner = new ShopMaking.Planner(context);
        Map<String, Long> have = new HashMap<>();
        shop.inputs().forEach((item, lot) -> have.put(item, lot.count()));
        CityEconomyRules rules = CityEconomySettings.rules();
        double strength = Shops.strength(server, shop, 0, rules);
        long day = TreasuryActions.day(server);
        long wanted = -1;
        for (Map.Entry<GoodsCategory, List<String>> entry : Workshops.candidates(server, trade, stations).entrySet()) {
            for (String product : entry.getValue()) {
                long unitMilli = planner.madeUnitMilli(product);
                if (!ShopPricing.worthBuying(Math.max(1, unitMilli), Shops.ceilingMilli(server, product))) {
                    continue;
                }
                long target = ShopMaking.target(shop.averageCategory(day, entry.getKey().ordinal()), strength,
                        ShopMaking.sharePercent(entry.getKey(), rules.basket()), rules.stockDays(), 0);
                long count = Math.max(1, (long) Math.ceil(target / Math.max(0.01, Shops.worth(server, product))));
                Optional<ShopMaking.Plan> plan = ShopMaking.plan(planner, product, entry.getKey().ordinal(), count, have, shop.fuelTicks());
                if (plan.isEmpty() || !plan.get().buy().containsKey(id) && !plan.get().held().containsKey(id)) {
                    continue;
                }
                wanted = Math.max(wanted, plan.get().buy().getOrDefault(id, 0L));
            }
        }
        return wanted;
    }

    private static List<ShopView.SellRow> sellRows(ServerPlayer player, Business shop) {
        MinecraftServer server = player.server;
        Inventory inventory = player.getInventory();
        Map<Item, Integer> have = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && GoodsStacks.plain(stack)) {
                have.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        if (have.isEmpty() || shop.trader()) {
            return List.of();
        }
        Map<String, Long> counts = KingdomStorage.countsById(server, shop.kingdomId());
        List<ShopView.SellRow> rows = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : have.entrySet()) {
            if (rows.size() >= ShopView.MAX_SELL) {
                break;
            }
            sellOffer(server, shop, player.getUUID(), entry.getKey(), counts).ifPresent(offer -> rows.add(new ShopView.SellRow(new ItemStack(entry.getKey()),
                    offer.unit(), offer.max(), offer.money(), entry.getValue())));
        }
        return rows;
    }

    public static void sell(ServerPlayer player, CommercePackets.ShopTrade trade) {
        MinecraftServer server = player.server;
        Optional<Business> found = CommerceSavedData.get(server).shop(trade.shopId());
        if (found.isEmpty()) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail(KEY + "gone"));
            return;
        }
        Business shop = found.get();
        KingdomActions.reply(player, sale(player, shop, trade));
        open(player, shop, false);
    }

    /**
     * A player sells a shop items of the inventory: whole plain items, as many as it takes and its till pays for, onto the account
     * of the player in the currency of the kingdom of the shop.
     */
    public static KingdomActions.Feedback sale(ServerPlayer player, Business shop, CommercePackets.ShopTrade trade) {
        MinecraftServer server = player.server;
        if (KingdomWorldApi.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        Optional<String> refused = refusal(player, shop);
        if (refused.isPresent()) {
            return KingdomActions.Feedback.fail(refused.get());
        }
        Item item = GoodsStacks.item(trade.item());
        if (item == null) {
            return KingdomActions.Feedback.fail(KEY + "not_wanted");
        }
        int have = GoodsStacks.countPlain(player.getInventory(), item);
        if (have <= 0) {
            return KingdomActions.Feedback.error(KingdomError.NO_ITEMS);
        }
        UUID kingdom = shop.kingdomId();
        Optional<SellOffer> offer = sellOffer(server, shop, player.getUUID(), item, KingdomStorage.countsById(server, kingdom));
        if (offer.isEmpty() || offer.get().max() <= 0) {
            return KingdomActions.Feedback.fail(KEY + "not_wanted");
        }
        if (offer.get().unit() < trade.seen()) {
            return KingdomActions.Feedback.fail(KEY + "price_changed");
        }
        if (offer.get().money() <= 0) {
            return KingdomActions.Feedback.fail(KEY + "till_short");
        }
        long count = Math.min(Math.min(trade.count(), have), Math.min(offer.get().max(), offer.get().money()));
        long total = Money.multiplyCapped(offer.get().unit(), count);
        Economy economy = KingdomWorldApi.state(server).economy();
        CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
        if (economy.purse(till) - shop.profitDue() < total) {
            return KingdomActions.Feedback.fail(KEY + "till_short");
        }
        int taken = GoodsStacks.takePlain(player.getInventory(), item, (int) count);
        if (taken != count) {
            GoodsStacks.give(player, new ItemStack(item, taken));
            return KingdomActions.Feedback.error(KingdomError.NO_ITEMS);
        }
        long day = TreasuryActions.day(server);
        Outcome paid = economy.purseToAccount(till, player.getUUID(), total, day);
        if (!paid.ok()) {
            GoodsStacks.give(player, new ItemStack(item, taken));
            return KingdomActions.Feedback.error(paid.error());
        }
        String id = GoodsStacks.itemId(item);
        if (offer.get().goods()) {
            shop.addGoods(id, Shops.categoryOf(server, item), count, offer.get().unit() * ShopPricing.MILLI);
        } else {
            shop.addInput(id, count, offer.get().unit() * ShopPricing.MILLI);
        }
        if (shop.status() == Business.Status.EMPTY) {
            shop.setStatus(shop.family() ? Business.Status.FAMILY : Business.Status.TRADING);
        }
        shop.record(day, new Business.Day(0, 0, total, 0, null));
        CommerceSavedData.get(server).setDirty();
        LawWork.traded(server, kingdom, player.getUUID(), total);
        return KingdomActions.Feedback.ok(KEY + "sold", count, new ItemStack(item).getHoverName(),
                MoneyTexts.amount(total, Purses.units(server, kingdom)));
    }

    // ---------------------------------------------------------------- fixing the trade

    /**
     * A member with the right over citizens fixes the trade of the place of a shop, or lets it choose again with an empty trade.
     * The shop takes up a new trade once it sold off what it holds.
     */
    public static void pin(ServerPlayer player, long shopId, String trade) {
        MinecraftServer server = player.server;
        Optional<Business> found = CommerceSavedData.get(server).shop(shopId);
        if (found.isEmpty()) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail(KEY + "gone"));
            return;
        }
        Business shop = found.get();
        KingdomActions.reply(player, fix(player, shop, trade));
        open(player, shop, false);
    }

    /**
     * Fixes the trade of a shop as {@link #pin} does, without the reply and the window.
     */
    public static KingdomActions.Feedback fix(ServerPlayer player, Business shop, String trade) {
        MinecraftServer server = player.server;
        if (KingdomWorldApi.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        if (!KingdomWorldApi.state(server).membership().hasPermission(player.getUUID(), shop.kingdomId(), Permission.MANAGE_CITIZENS)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        if (!near(player, shop)) {
            return KingdomActions.Feedback.fail(KEY + "too_far");
        }
        if (trade.isEmpty()) {
            shop.setPinned("");
            CommerceSavedData.get(server).setDirty();
            return KingdomActions.Feedback.ok(KEY + "unpinned");
        }
        Optional<Trade> chosen = TradeRegistry.current().get(trade);
        if (chosen.isEmpty() || !fixable(server, shop, chosen.get(), PlaceSurvey.stations(server, shop.place().key()).orElse(Set.of()))) {
            return KingdomActions.Feedback.fail(KEY + "pin_unfit");
        }
        shop.setPinned(trade);
        shop.setNextTrade(trade);
        if (!shop.nextTrade().isEmpty() && shop.goods().isEmpty() && Shops.returnInputs(server, shop)) {
            Shops.changeTrade(server, shop, trade, TreasuryActions.day(server));
        }
        CommerceSavedData.get(server).setDirty();
        return KingdomActions.Feedback.ok(KEY + "pinned", Component.translatable("kingdomworld.business.trade." + trade));
    }

    /**
     * The count of an item in the storehouses, for checks.
     */
    static long stock(MinecraftServer server, UUID kingdom, String id) {
        return KingdomStorage.countsById(server, kingdom).getOrDefault(id, 0L);
    }
}
