package io.github.sekerator.kingdomworld.common.commerce;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.economy.HouseholdSavedData;
import io.github.sekerator.kingdomworld.common.economy.Households;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.network.data.CityViews;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.CityDay;
import io.github.sekerator.kingdomworld.core.commerce.Evasion;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import io.github.sekerator.kingdomworld.core.commerce.KeeperChoice;
import io.github.sekerator.kingdomworld.core.commerce.RestockPlan;
import io.github.sekerator.kingdomworld.core.commerce.ShopMaking;
import io.github.sekerator.kingdomworld.core.commerce.ShopPricing;
import io.github.sekerator.kingdomworld.core.commerce.Trade;
import io.github.sekerator.kingdomworld.core.commerce.TradeChoice;
import io.github.sekerator.kingdomworld.core.commerce.TradeRegistry;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.economy.CityEconomyRules;
import io.github.sekerator.kingdomworld.core.economy.CityLedger;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.HouseholdRules;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.Payroll;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.Prices;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
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
 * The shops of the citizens of the kingdoms of players: where they open, which trade they take up, who keeps them, what they buy of
 * the crown, what their masters make, what the townspeople buy of them, what is left at the end of their day, what their keepers
 * earn and how they close.
 *
 * <p>Nothing is made out of nothing. Goods and raw materials come out of the storehouses of the crown and are paid for (or owed,
 * the first goods of a new shop); a making uses its raw materials up whole; what the townspeople buy disappears (eaten, worn out);
 * what players buy goes into their inventories; what a closing shop has left goes back into the storehouses, bought back. Money
 * moves between the purse of the townspeople, the tills, the treasury of the city and that of the kingdom, or leaves the world for
 * the upkeep of the shops.
 */
public final class Shops {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<GoodsCategory, TagKey<Item>> TAGS = new HashMap<>();
    /** The kind of goods of each item, -1 for none, as the tags said on the day noted. */
    private static final Map<String, Integer> CATEGORY = new HashMap<>();
    private static long categoryDay = Long.MIN_VALUE;
    /** How far the day of each city went. */
    private static final Map<String, Progress> PROGRESS = new HashMap<>();
    /** How far the buying of the shops of each kingdom went. */
    private static final Map<UUID, Restock> RESTOCK = new HashMap<>();
    /** What the workshops plan to make today, by shop; worked out when they buy of the crown, made in the day of their city. */
    private static final Map<Long, List<ShopMaking.Plan>> PLANS = new HashMap<>();
    /** The fuel the crown sold the workshops of each kingdom today. */
    private static final Map<UUID, ShopMaking.Fuel> FUEL = new HashMap<>();
    /**
     * What each shop sold since the end of its last day: what came into its till, what the goods sold cost it, the work in them,
     * then the worth sold of each kind; townspeople of other cities and trading houses buy of it outside the day of its own city.
     */
    private static final Map<Long, long[]> SOLD = new HashMap<>();
    /** The worth each shop can still sell today, and the day it is for. */
    private static final Map<Long, Double> ROOM = new HashMap<>();
    private static long roomDay = Long.MIN_VALUE;
    /** The starting margin of a new shop, in thousandths. */
    private static final int START_MARGIN = 200;

    static {
        for (GoodsCategory category : GoodsCategory.values()) {
            TAGS.put(category, TagKey.create(Registries.ITEM, new ResourceLocation(KingdomWorld.MOD_ID, "goods/" + category.id())));
        }
    }

    /**
     * How far the day of the shops of a city went.
     */
    private static final class Progress {
        final long day;
        int stage;
        long[] budgets = new long[GoodsCategory.values().length];
        long[] spentKind = new long[GoodsCategory.values().length];
        long wanted;
        long spent;
        int tax;
        int evasion;
        List<Long> shops = List.of();
        int cursor;
        List<Long> workshops;
        int makeCursor;

        Progress(long day) {
            this.day = day;
        }
    }

    /**
     * How far the buying of the shops of a kingdom of a day went.
     */
    private static final class Restock {
        final long day;
        int stage;
        int cursor;
        List<Long> shops = List.of();
        Map<String, Long> counts = Map.of();
        final Map<String, Long> free = new HashMap<>();
        long spare;
        final Map<String, Integer> nutrition = new HashMap<>();
        final Map<String, Long> unitCost = new HashMap<>();
        final Map<String, Long> crownPrices = new HashMap<>();
        final Map<String, Item> items = new HashMap<>();
        final List<RestockPlan.Need> needs = new ArrayList<>();
        final Set<Long> credit = new HashSet<>();
        Map<Long, Integer> marketLevels = Map.of();
        final Map<Long, CommerceSavedData.CityTrade> trades = new HashMap<>();
        ShopMaking.Fuel fuel;
        int idle;
        int unspared;
        int dear;

        Restock(long day) {
            this.day = day;
        }
    }

    private Shops() {
    }

    public static void reset() {
        CATEGORY.clear();
        categoryDay = Long.MIN_VALUE;
        PROGRESS.clear();
        RESTOCK.clear();
        PLANS.clear();
        FUEL.clear();
        SOLD.clear();
        ROOM.clear();
        roomDay = Long.MIN_VALUE;
        CityTies.reset();
        BusinessPlaces.reset();
        Workshops.invalidate();
    }

    private static String key(UUID kingdom, long city) {
        return kingdom + "/" + city;
    }

    // ---------------------------------------------------------------- goods

    /**
     * The kind of goods of an item by the tags, or -1.
     */
    public static int categoryOf(MinecraftServer server, Item item) {
        long day = TreasuryActions.day(server);
        if (day != categoryDay) {
            CATEGORY.clear();
            categoryDay = day;
        }
        String id = KingdomStorage.itemId(new ItemStack(item));
        Integer known = CATEGORY.get(id);
        if (known != null) {
            return known;
        }
        int found = -1;
        ItemStack stack = new ItemStack(item);
        for (GoodsCategory category : GoodsCategory.values()) {
            if (stack.is(TAGS.get(category))) {
                found = category.ordinal();
                break;
            }
        }
        CATEGORY.put(id, found);
        return found;
    }

    /**
     * The items of a kind of goods, by the tags.
     */
    static List<Item> itemsOf(GoodsCategory category) {
        List<Item> items = new ArrayList<>();
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(TAGS.get(category)).forEach(holder -> items.add(holder.value()));
        return items;
    }

    static double worth(MinecraftServer server, Item item) {
        return Math.max(0.01, ItemPrices.value(server, new ItemStack(item)).amount());
    }

    static double worth(MinecraftServer server, String id) {
        Item item = GoodsStacks.item(id);
        return item == null ? 0.01 : worth(server, item);
    }

    /**
     * What the crown asks for one of an item: the price the king set, else the automatic one at the stock.
     */
    static long crownPrice(MinecraftServer server, UUID kingdom, String id, Item item, long stock) {
        TradeRules rules = TradeSettings.rules();
        Market market = KingdomWorldApi.state(server).trade().market(kingdom, rules);
        Optional<Market.SellEntry> entry = market.sell(id);
        if (entry.isPresent() && entry.get().price() > 0) {
            return entry.get().price();
        }
        return Prices.autoSellPrice(worth(server, item), stock, rules);
    }

    static long ceilingMilli(MinecraftServer server, Item item) {
        double worth = worth(server, item);
        return ShopPricing.ceilingMilli(worth, Prices.importCost(worth, 1, TradeSettings.rules()));
    }

    /**
     * The worth of one of an item, in thousandths.
     */
    static long worthMilli(MinecraftServer server, Item item) {
        return (long) Math.floor(worth(server, item) * ShopPricing.MILLI);
    }

    static long ceilingMilli(MinecraftServer server, String id) {
        Item item = GoodsStacks.item(id);
        return item == null ? 0 : ceilingMilli(server, item);
    }

    /**
     * What a shop asks for its goods under a key without the tax, in thousandths.
     */
    static long askedMilli(MinecraftServer server, Business shop, String key, Business.Lot lot) {
        Item item = GoodsStacks.item(Business.itemOf(key));
        return item == null ? Long.MAX_VALUE / 4 : ShopMaking.askedMilli(lot, shop.marginPermille(), ceilingMilli(server, item), worthMilli(server, item));
    }

    /**
     * The worth of the goods of a kind a shop holds, bought and made.
     */
    static long heldWorth(MinecraftServer server, Business shop, int category) {
        double sum = 0;
        for (Map.Entry<String, Business.Lot> entry : shop.goods().entrySet()) {
            if (entry.getValue().category() == category) {
                sum += worth(server, Business.itemOf(entry.getKey())) * entry.getValue().count();
            }
        }
        return (long) Math.floor(sum);
    }

    // ---------------------------------------------------------------- views

    public static boolean exists(MinecraftServer server, long shop, UUID kingdom) {
        return CommerceSavedData.get(server).shop(shop).filter(found -> found.kingdomId().equals(kingdom)).isPresent();
    }

    /**
     * The shops of a city for its window, and why new ones do not open.
     */
    public record Overview(List<CityViews.ShopRow> rows, int count, CityViews.Blocked blocked, long shortfall, String missing) {
    }

    public static Overview overview(MinecraftServer server, UUID kingdom, long city) {
        CommerceSavedData data = CommerceSavedData.get(server);
        long day = TreasuryActions.day(server);
        List<CityViews.ShopRow> rows = new ArrayList<>();
        List<Business> shops = data.shopsOf(kingdom, city);
        for (Business shop : shops) {
            if (rows.size() >= CityViews.MAX_SHOPS) {
                break;
            }
            rows.add(new CityViews.ShopRow(shop.id(), shop.trade(), keeperNames(server, shop), shop.status().id(), shop.averageSales(day + 1),
                    shop.marginPermille(), shop.debt(), !shop.pinned().isEmpty(), shop.averageMade(day + 1), shop.averageExported(day + 1)));
        }
        CommerceSavedData.CityTrade trade = data.city(kingdom, city);
        long shortfall = trade.unspent();
        CityViews.Blocked blocked = CityViews.Blocked.NONE;
        if (shortfall > Money.multiplyCapped(2, CityEconomySettings.rules().fairIncome())) {
            blocked = trade.crownShort() ? CityViews.Blocked.NO_GOODS : trade.noPlaces ? CityViews.Blocked.NO_PLACES
                    : trade.noKeepers ? CityViews.Blocked.NO_TOWNSFOLK : CityViews.Blocked.NONE;
        }
        String missing = "";
        if (!trade.missing().isEmpty()) {
            Item item = GoodsStacks.item(trade.missing());
            missing = item == null ? trade.missing() : new ItemStack(item).getHoverName().getString();
        }
        return new Overview(rows, shops.size(), blocked, shortfall, missing);
    }

    /**
     * The names of the keepers of a shop, empty for the family of a house.
     */
    static String keeperNames(MinecraftServer server, Business shop) {
        List<String> names = new ArrayList<>();
        for (UUID keeper : shop.keepers()) {
            KingdomWorldApi.state(server).citizens().get(keeper).ifPresent(citizen -> names.add(
                    io.github.sekerator.kingdomworld.common.citizen.CitizenTexts.name(citizen).getString()));
        }
        return String.join(", ", names);
    }

    // ---------------------------------------------------------------- keepers

    /**
     * Whether a citizen may keep a shop: a townsperson without a post of the crown, no soldier, and without another shop.
     */
    static boolean townsperson(Citizen citizen, long shop) {
        return citizen.isAlive() && Professions.UNEMPLOYED.id().equals(citizen.professionId()) && !Professions.isMilitary(citizen.professionId())
                && (citizen.business() == 0 || citizen.business() == shop);
    }

    private static Optional<Building> buildingOf(MinecraftServer server, Business.Place place) {
        if (place.kind() == Business.PlaceKind.COUNTER) {
            return Optional.empty();
        }
        return KingdomWorldApi.state(server).construction().building(place.building());
    }

    /**
     * The citizens whose home is in a house.
     */
    static List<Citizen> residents(Building house, List<Citizen> citizens) {
        List<Citizen> found = new ArrayList<>();
        for (Citizen citizen : citizens) {
            WorldPos home = citizen.home();
            if (home != null && home.dimension().equals(house.dimension()) && house.box().contains(home.x(), home.y(), home.z())) {
                found.add(citizen);
            }
        }
        return found;
    }

    private static KeeperChoice.Choice choose(MinecraftServer server, Business.Place place, List<Citizen> cityPeople, long shopId, int room,
                                             Set<UUID> taken) {
        Optional<Building> house = place.kind().house() ? buildingOf(server, place) : Optional.empty();
        List<KeeperChoice.Candidate> candidates = new ArrayList<>();
        for (Citizen citizen : cityPeople) {
            if (taken.contains(citizen.id())) {
                continue;
            }
            boolean resident = house.isPresent() && citizen.home() != null && house.get().box().contains(citizen.home().x(), citizen.home().y(),
                    citizen.home().z());
            WorldPos at = citizen.home() != null ? citizen.home() : citizen.lastPos();
            double distance = at.dimension().equals(place.dimension()) ? sq(at.x() - place.x()) + sq(at.z() - place.z()) : Double.MAX_VALUE;
            candidates.add(new KeeperChoice.Candidate(citizen.id(), townsperson(citizen, shopId), resident, distance));
        }
        return KeeperChoice.choose(candidates, room, place.kind().house());
    }

    private static double sq(double value) {
        return value * value;
    }

    /**
     * How much worth a shop sells a day at most: its keepers at the pace of their mood, half of one for a family, a quarter more
     * for every level of the market of a stall.
     */
    static double strength(MinecraftServer server, Business shop, int marketLevel, CityEconomyRules rules) {
        double base;
        if (shop.family()) {
            base = rules.turnoverPerWorker() * rules.familyShopPercent() / 100.0;
        } else {
            base = rules.turnoverPerWorker() * Workshops.pace(server, shop);
        }
        if (shop.place().kind() == Business.PlaceKind.MARKET_STALL && marketLevel > 0) {
            base *= 1.0 + 0.25 * marketLevel;
        }
        return base;
    }

    // ---------------------------------------------------------------- the payday of the shops

    /**
     * The payday of a kingdom: every shop pays what it earned the day before, the tithe to the treasury of its city (less what
     * its people evade), the rest to those who earn of it by their shares as a wage: a fifth home to the house of each, the rest
     * into the purse of the townspeople of the city of each. The mood of a keeper or a master follows what it earned against the
     * usual income of its trade, never below that of the idle.
     */
    static void payday(MinecraftServer server, UUID kingdom, long day) {
        CommerceSavedData data = CommerceSavedData.get(server);
        Economy economy = KingdomWorldApi.state(server).economy();
        HouseholdRules household = Households.rules();
        for (Business shop : data.shopsOf(kingdom)) {
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
            long profit = Math.min(shop.profitDue(), economy.purse(till));
            shop.setProfitDue(0);
            List<Citizen> earners = earners(server, shop);
            long usual = Workshops.usualIncome(Workshops.trade(shop));
            if (profit <= 0 || earners.isEmpty()) {
                markKeepers(shop, earners, 0, usual);
                continue;
            }
            long tithe = Money.share(profit, CommerceWork.tithe(server, kingdom, shop.cityId()), 1000);
            long collected = Evasion.collected(tithe, CommerceWork.evasion(server, kingdom, shop.cityId()));
            economy.purseMove(till, CityPurses.PurseKey.cityTreasury(kingdom, shop.cityId()), collected, CityLedger.TITHE, day);
            long rest = profit - collected;
            int[] shares = new int[earners.size()];
            int[] own = shop.shares();
            for (int i = 0; i < shares.length; i++) {
                shares[i] = shop.family() || i >= own.length ? Business.KEEPER_SHARE : own[i];
            }
            long[] parts = Business.split(rest, shares);
            for (int i = 0; i < earners.size(); i++) {
                Citizen earner = earners.get(i);
                long pay = parts[i];
                long home = household.share(pay);
                long kept = home > 0 ? toHouse(server, economy, till, earner, home) : 0;
                economy.purseMove(till, CityPurses.PurseKey.cityPurse(kingdom, CityOf.of(server, earner)), pay - kept, CityLedger.OTHER, day);
            }
            markKeepers(shop, earners, profit / Math.max(1, earners.size()), usual);
        }
        data.setDirty();
    }

    /**
     * Who shares what a shop earned: its keepers, or the people of the house of a family shop.
     */
    private static List<Citizen> earners(MinecraftServer server, Business shop) {
        List<Citizen> earners = new ArrayList<>();
        var citizens = KingdomWorldApi.state(server).citizens();
        if (shop.family()) {
            buildingOf(server, shop.place()).ifPresent(house -> earners.addAll(residents(house, citizens.registry().citizensOf(shop.kingdomId()))));
            return earners;
        }
        for (UUID keeper : shop.keepers()) {
            citizens.get(keeper).ifPresent(earners::add);
        }
        return earners;
    }

    /**
     * The keepers of a shop feel what they earned as a worker feels a wage, but never worse than the idle; the family of a house
     * keeps the mood its wages give.
     */
    private static void markKeepers(Business shop, List<Citizen> earners, long earned, long usual) {
        if (shop.family()) {
            return;
        }
        for (Citizen keeper : earners) {
            int percent = Payroll.percent(earned, usual);
            keeper.setPayPercent(percent >= 100 ? percent : Payroll.NOTHING_EXPECTED);
        }
    }

    /**
     * A share of what a keeper earned goes home with it, as far as the purse of its house has room.
     *
     * @return what went home
     */
    private static long toHouse(MinecraftServer server, Economy economy, CityPurses.PurseKey from, Citizen citizen, long amount) {
        Optional<Building> house = Households.houseOf(server, citizen);
        if (house.isEmpty()) {
            return 0;
        }
        HouseholdSavedData data = HouseholdSavedData.get(server);
        long before = data.amount(house.get().id());
        long want = Math.max(0, Math.min(amount, Households.rules().purseMax() - before));
        long moved = economy.purseHold(from, want);
        if (moved > 0) {
            data.set(house.get().id(), citizen.kingdomId(), before + moved);
        }
        return moved;
    }

    // ---------------------------------------------------------------- what the shops buy of the crown

    /**
     * The shops of a kingdom buy of the crown, once a day before the days of its cities, over as many ticks as it takes: one count
     * of the storehouses, every shop works out what it wants (the general stores their goods, the workshops the raw materials of
     * what they plan to make), the split of what the crown can spare, one taking. A new shop gets its first goods on credit.
     *
     * @param start when the tick began, for the budget
     * @return whether it is done
     */
    static boolean restock(MinecraftServer server, UUID kingdom, long day, long start) {
        Restock restock = RESTOCK.get(kingdom);
        if (restock == null || restock.day != day) {
            restock = new Restock(day);
            RESTOCK.put(kingdom, restock);
        }
        while (System.nanoTime() - start < CommerceWork.BUDGET_NANOS) {
            if (restock.stage == 0) {
                if (!beginRestock(server, kingdom, restock)) {
                    RESTOCK.remove(kingdom);
                    return true;
                }
                restock.stage = 1;
            } else if (restock.stage == 1) {
                if (restock.cursor >= restock.shops.size()) {
                    restock.stage = 2;
                    continue;
                }
                Optional<Business> shop = CommerceSavedData.get(server).shop(restock.shops.get(restock.cursor++));
                if (shop.isPresent()) {
                    want(server, kingdom, restock, shop.get());
                }
            } else {
                takeAndPay(server, kingdom, restock);
                RESTOCK.remove(kingdom);
                return true;
            }
        }
        return false;
    }

    /**
     * Counts the storehouses once for the day.
     *
     * @return whether the shops buy at all today
     */
    private static boolean beginRestock(MinecraftServer server, UUID kingdom, Restock restock) {
        CommerceSavedData data = CommerceSavedData.get(server);
        List<Business> shops = data.shopsOf(kingdom);
        // The plans of yesterday that were not made are forgotten.
        for (Business shop : shops) {
            PLANS.remove(shop.id());
        }
        FUEL.remove(kingdom);
        if (shops.isEmpty()) {
            return false;
        }
        var state = KingdomWorldApi.state(server);
        TradeRules tradeRules = TradeSettings.rules();
        Market market = state.trade().market(kingdom, tradeRules);
        for (Business shop : shops) {
            restock.trades.computeIfAbsent(shop.cityId(), city -> data.city(kingdom, city));
        }
        for (CommerceSavedData.CityTrade trade : restock.trades.values()) {
            trade.short_ = false;
            trade.missing = "";
        }
        if (!market.sellToCitizens()) {
            LOGGER.debug("The market of {} sells nothing to the shops of citizens", kingdom);
            return false;
        }
        restock.counts = KingdomStorage.countsById(server, kingdom);
        Map<String, Long> kept = new HashMap<>();
        for (var order : state.orders().ordersOf(kingdom)) {
            if (order.kind() == io.github.sekerator.kingdomworld.core.production.Order.Kind.KEEP && !order.paused()) {
                kept.merge(order.item(), (long) order.amount(), Long::sum);
            }
        }
        Map<String, Integer> reserved = io.github.sekerator.kingdomworld.common.production.Production.reservations().reserved(kingdom);
        restock.counts.forEach((id, count) -> restock.free.put(id, Math.max(0, count - kept.getOrDefault(id, 0L) - reserved.getOrDefault(id, 0))));
        long eaters = state.citizens().registry().count(kingdom) + io.github.sekerator.kingdomworld.common.road.RoadWork.state(server).folkOf(kingdom);
        var citizenRules = io.github.sekerator.kingdomworld.common.citizen.CitizenSettings.rules();
        long floor = Money.multiplyCapped(Money.multiplyCapped(citizenRules.growthFoodDays(), citizenRules.dailyNutrition()), eaters);
        restock.spare = Math.max(0, KingdomStorage.totalNutrition(server, kingdom) - floor);
        restock.marketLevels = marketLevels(server, kingdom);
        List<Long> ids = new ArrayList<>();
        for (Business shop : shops) {
            ids.add(shop.id());
        }
        restock.shops = ids;
        final Restock current = restock;
        restock.fuel = Workshops.fuel(restock.free, id -> crownCoins(server, kingdom, current, id));
        if (restock.fuel != null) {
            FUEL.put(kingdom, restock.fuel);
        }
        return true;
    }

    /**
     * What the crown asks for one of an item today, worked out once.
     */
    private static long crownCoins(MinecraftServer server, UUID kingdom, Restock restock, String id) {
        return restock.crownPrices.computeIfAbsent(id, key -> {
            Item item = GoodsStacks.item(key);
            return item == null ? Long.MAX_VALUE / 4 : crownPrice(server, kingdom, key, item, restock.counts.getOrDefault(key, 0L));
        });
    }

    /**
     * What one shop wants of the crown today.
     */
    private static void want(MinecraftServer server, UUID kingdom, Restock restock, Business shop) {
        CityEconomyRules rules = CityEconomySettings.rules();
        Economy economy = KingdomWorldApi.state(server).economy();
        if (shop.status() == Business.Status.CLOSING || !shop.kept() || !shop.nextTrade().isEmpty() || shop.trader()) {
            restock.idle++;
            return;
        }
        boolean onCredit = shop.startingOut();
        if (onCredit) {
            restock.credit.add(shop.id());
        }
        long budget = onCredit ? rules.shopCredit()
                : Math.max(0, economy.purse(CityPurses.PurseKey.till(kingdom, shop.id())) - shop.profitDue());
        double strength = strength(server, shop, restock.marketLevels.getOrDefault(shop.id(), 0), rules);
        if (shop.crafts()) {
            wantRaw(server, kingdom, restock, shop, budget, strength, rules);
            return;
        }
        long left = budget;
        for (GoodsCategory category : GoodsCategory.values()) {
            long average = shop.averageCategory(restock.day, category.ordinal());
            long target = CityDay.stockTarget(average, strength, rules.basketShare(category.ordinal()), rules.stockDays());
            long lacking = target - shop.goodsValue(category.ordinal());
            if (lacking <= 0 || left <= 0) {
                continue;
            }
            // The cheapest item of the kind for its worth that the crown can spare and the shop can sell above what it pays.
            Item best = null;
            long bestCost = 0;
            double bestRatio = Double.MAX_VALUE;
            for (Item item : itemsOf(category)) {
                String id = KingdomStorage.itemId(new ItemStack(item));
                if (restock.free.getOrDefault(id, 0L) <= 0) {
                    restock.unspared++;
                    continue;
                }
                long cost = crownCoins(server, kingdom, restock, id);
                if (!ShopPricing.worthBuying(cost * ShopPricing.MILLI, ceilingMilli(server, item))) {
                    restock.dear++;
                    continue;
                }
                double ratio = cost / worth(server, item);
                if (ratio < bestRatio) {
                    bestRatio = ratio;
                    best = item;
                    bestCost = cost;
                }
            }
            if (best == null) {
                continue;
            }
            String id = KingdomStorage.itemId(new ItemStack(best));
            long count = Math.max(1, (long) Math.ceil(lacking / Math.max(0.01, worth(server, best))));
            count = Math.min(count, Math.max(0, left / Math.max(1, bestCost)));
            if (count <= 0) {
                continue;
            }
            left -= count * bestCost;
            need(restock, shop, id, best, category.ordinal(), count, bestCost);
        }
    }

    private static void need(Restock restock, Business shop, String id, Item item, int category, long count, long unit) {
        restock.items.put(id, item);
        restock.unitCost.put(id, unit);
        int each = KingdomStorage.nutrition(new ItemStack(item));
        if (each > 0) {
            restock.nutrition.put(id, each);
        }
        restock.needs.add(new RestockPlan.Need(shop.id(), id, category, count));
    }

    /**
     * A workshop works out what it makes today and the raw materials it buys for it: of each kind of its goods the item it makes
     * most cheaply for its worth, as many as it lacks of its target, within the work of its masters and its money.
     */
    private static void wantRaw(MinecraftServer server, UUID kingdom, Restock restock, Business shop, long budget, double strength,
                                CityEconomyRules rules) {
        Trade trade = Workshops.trade(shop);
        Set<String> placeStations = Workshops.placeStations(server, shop);
        if (!trade.fits(placeStations)) {
            restock.idle++;
            return;
        }
        ShopMaking.Context context = Workshops.context(server, trade, placeStations,
                id -> restock.free.getOrDefault(id, 0L) > 0 ? Money.multiplyCapped(crownCoins(server, kingdom, restock, id), ShopPricing.MILLI) : -1,
                restock.fuel);
        ShopMaking.Planner planner = new ShopMaking.Planner(context);
        Map<GoodsCategory, List<String>> candidates = Workshops.candidates(server, trade, placeStations);
        long ticks = ShopMaking.workTicks(Workshops.pace(server, shop), shop.family(), rules.familyShopPercent());
        Map<String, Long> have = new HashMap<>();
        shop.inputs().forEach((item, lot) -> have.put(item, lot.count()));
        long fuelBank = shop.fuelTicks();
        long left = budget;
        List<ShopMaking.Plan> plans = new ArrayList<>();
        for (WorkOrder order : orders(server, shop)) {
            if (ticks <= 0) {
                break;
            }
            Optional<ShopMaking.Plan> plan = fit(planner, order.item(), order.category(), order.count(), have, fuelBank, ticks, left,
                    id -> context.crownMilli().applyAsLong(id));
            if (plan.isEmpty()) {
                continue;
            }
            ticks -= plan.get().ticks();
            left -= plan.get().buyCost(id -> context.crownMilli().applyAsLong(id));
            fuelBank = Math.max(0, fuelBank - plan.get().fuelTicks());
            take(have, plan.get().held());
            plans.add(plan.get());
        }
        for (GoodsCategory category : trade.goods()) {
            if (ticks <= 0) {
                break;
            }
            long average = shop.averageCategory(restock.day, category.ordinal());
            long target = ShopMaking.target(average, strength, ShopMaking.sharePercent(category, rules.basket()), rules.stockDays(),
                    traderOrder(server, shop, category));
            long lacking = target - heldWorth(server, shop, category.ordinal());
            if (lacking <= 0) {
                continue;
            }
            Optional<ShopMaking.Choice> choice = ShopMaking.choose(planner, candidates.getOrDefault(category, List.of()),
                    id -> worth(server, id), id -> ceilingMilli(server, id));
            if (choice.isEmpty()) {
                restock.dear++;
                continue;
            }
            long count = Math.max(1, (long) Math.ceil(lacking / Math.max(0.01, choice.get().worth())));
            Optional<ShopMaking.Plan> plan = fit(planner, choice.get().item(), category.ordinal(), count, have, fuelBank, ticks, left,
                    id -> context.crownMilli().applyAsLong(id));
            if (plan.isEmpty()) {
                continue;
            }
            ticks -= plan.get().ticks();
            left -= plan.get().buyCost(id -> context.crownMilli().applyAsLong(id));
            fuelBank = Math.max(0, fuelBank - plan.get().fuelTicks());
            take(have, plan.get().held());
            plans.add(plan.get());
        }
        if (plans.isEmpty()) {
            return;
        }
        PLANS.put(shop.id(), plans);
        for (ShopMaking.Plan plan : plans) {
            for (Map.Entry<String, Long> entry : plan.buy().entrySet()) {
                Item item = GoodsStacks.item(entry.getKey());
                if (item != null && entry.getValue() > 0) {
                    need(restock, shop, entry.getKey(), item, -1, entry.getValue(), crownCoins(server, kingdom, restock, entry.getKey()));
                }
            }
        }
    }

    /**
     * Work a workshop is asked for besides its own stock: what the crown paid for in advance.
     */
    record WorkOrder(String item, int category, long count) {
    }

    /**
     * The work the crown paid a workshop for in advance, the oldest first.
     */
    static List<WorkOrder> orders(MinecraftServer server, Business shop) {
        List<WorkOrder> orders = new ArrayList<>();
        for (Business.Commission commission : shop.commissions()) {
            Item item = GoodsStacks.item(commission.item());
            if (item == null) {
                continue;
            }
            long have = shop.count(Business.key(commission.item(), true));
            long lacking = commission.count() - have;
            if (lacking > 0) {
                orders.add(new WorkOrder(commission.item(), Math.max(0, categoryOf(server, item)), lacking));
            }
        }
        return orders;
    }

    /**
     * The worth of goods of a kind the trading house of the city of a workshop asked of it; nothing while no trading house works
     * there.
     */
    static long traderOrder(MinecraftServer server, Business shop, GoodsCategory category) {
        return Traders.order(server, shop, category);
    }

    private static void take(Map<String, Long> have, Map<String, Long> taken) {
        taken.forEach((item, count) -> have.merge(item, -count, Long::sum));
    }

    /**
     * A plan for as many of an item as the work left and the money allow.
     */
    private static Optional<ShopMaking.Plan> fit(ShopMaking.Planner planner, String item, int category, long count, Map<String, Long> have,
                                                 long fuelBank, long ticks, long money, java.util.function.ToLongFunction<String> priceMilli) {
        long wanted = count;
        for (int attempt = 0; attempt < 4 && wanted > 0; attempt++) {
            Optional<ShopMaking.Plan> plan = ShopMaking.plan(planner, item, category, wanted, have, fuelBank);
            if (plan.isEmpty()) {
                return plan;
            }
            long cost = plan.get().buyCost(priceMilli);
            if (plan.get().ticks() <= ticks && cost <= money) {
                return plan;
            }
            double byTime = plan.get().ticks() <= 0 ? 1.0 : ticks / (double) plan.get().ticks();
            double byMoney = cost <= 0 ? 1.0 : money / (double) cost;
            long fewer = (long) Math.floor(wanted * Math.min(byTime, byMoney));
            wanted = Math.min(wanted - 1, fewer);
        }
        return Optional.empty();
    }

    /**
     * Splits what the crown can spare among the shops, takes it from the storehouses in one go and hands it out: goods to the
     * general stores, raw materials to the workshops, paid out of the tills or owed by a new shop.
     */
    private static void takeAndPay(MinecraftServer server, UUID kingdom, Restock restock) {
        CommerceSavedData data = CommerceSavedData.get(server);
        Economy economy = KingdomWorldApi.state(server).economy();
        CityEconomyRules rules = CityEconomySettings.rules();
        long day = restock.day;
        if (restock.needs.isEmpty()) {
            LOGGER.debug("The shops of {} bought nothing of the crown on day {}: {} shop(s), {} closing, without keepers or selling off, {} item(s) "
                    + "the crown cannot spare, {} too dear to sell on", kingdom, day, restock.shops.size(), restock.idle, restock.unspared, restock.dear);
            return;
        }
        RestockPlan.Result plan = RestockPlan.allocate(restock.needs, restock.free, rules.takeFromCrownPercent(), restock.nutrition, restock.spare);
        for (Long lacking : plan.short_()) {
            data.shop(lacking).ifPresent(shop -> {
                CommerceSavedData.CityTrade trade = restock.trades.get(shop.cityId());
                if (trade != null && !shop.crafts()) {
                    trade.short_ = true;
                    trade.missing = plan.missing();
                }
            });
        }
        // One taking of everything planned, plain stacks only, so that the goods can be made again by their id.
        Map<String, Integer> left = new HashMap<>();
        for (RestockPlan.Take take : plan.takes()) {
            left.merge(take.item(), (int) Math.min(Integer.MAX_VALUE, take.count()), Integer::sum);
        }
        WorldPos near = CityWalls.place(server, kingdom, 0).map(place -> new WorldPos(place.dimension(), place.x(), place.y(), place.z()))
                .orElse(null);
        List<KingdomStorage.Take> takes = new ArrayList<>();
        for (KingdomStorage.Found found : KingdomStorage.find(server, kingdom,
                stack -> left.getOrDefault(KingdomStorage.itemId(stack), 0) > 0 && GoodsStacks.plain(stack), near)) {
            String id = KingdomStorage.itemId(found.stack());
            int still = left.getOrDefault(id, 0);
            if (still <= 0) {
                continue;
            }
            int count = Math.min(still, found.stack().getCount());
            takes.add(new KingdomStorage.Take(found, count));
            left.put(id, still - count);
        }
        List<ItemStack> taken = takes.isEmpty() ? List.of() : KingdomStorage.takePlanned(server, takes);
        if (taken == null) {
            LOGGER.debug("The storehouses of {} changed while the shops bought; they buy tomorrow", kingdom);
            return;
        }
        long boughtItems = 0;
        long boughtFor = 0;
        Map<String, Long> got = new HashMap<>();
        for (ItemStack stack : taken) {
            got.merge(KingdomStorage.itemId(stack), (long) stack.getCount(), Long::sum);
        }
        for (RestockPlan.Take take : plan.takes()) {
            Optional<Business> found = data.shop(take.shop());
            long have = got.getOrDefault(take.item(), 0L);
            long count = Math.min(take.count(), have);
            if (found.isEmpty() || count <= 0) {
                continue;
            }
            Business shop = found.get();
            long unit = restock.unitCost.getOrDefault(take.item(), 1L);
            long cost = Money.multiplyCapped(unit, count);
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
            boolean onCredit = restock.credit.contains(shop.id());
            if (onCredit) {
                // The first goods of a new shop come on credit at the price of the crown.
                shop.setDebt(shop.debt() + cost);
            } else if (!economy.purseToTreasury(till, cost, LedgerCategory.COMMERCE, day).ok()) {
                continue;
            }
            got.put(take.item(), have - count);
            boughtItems += count;
            boughtFor = Money.addCapped(boughtFor, cost);
            if (take.category() < 0) {
                shop.addInput(take.item(), count, unit * ShopPricing.MILLI);
            } else {
                shop.addGoods(take.item(), take.category(), count, unit * ShopPricing.MILLI);
            }
            shop.record(day, new Business.Day(0, 0, cost, 0, new long[GoodsCategory.values().length]));
            if (!onCredit) {
                economy.cityTally(kingdom, shop.cityId()).add(CityStatement.Line.CROWN_GOODS_SOLD, cost);
            }
            Item sold = restock.items.get(take.item());
            if (sold != null) {
                CityGoods.out(server, kingdom, shop.cityId(), sold, count);
            }
            if (shop.status() == Business.Status.EMPTY) {
                shop.setStatus(shop.family() ? Business.Status.FAMILY : Business.Status.TRADING);
            }
        }
        // What was taken and found no shop goes back.
        for (Map.Entry<String, Long> rest : got.entrySet()) {
            insertAll(server, kingdom, rest.getKey(), rest.getValue(), near);
        }
        LOGGER.debug("The shops of {} bought {} item(s) of the crown for {} on day {}", kingdom, boughtItems, boughtFor, day);
        data.setDirty();
    }

    /**
     * The level of the market of each stall of a kingdom, by shop.
     */
    private static Map<Long, Integer> marketLevels(MinecraftServer server, UUID kingdom) {
        Map<Long, Integer> levels = new HashMap<>();
        Map<String, Integer> byPlace = new HashMap<>();
        for (List<BusinessPlaces.Spot> spots : BusinessPlaces.all(server, kingdom).values()) {
            for (BusinessPlaces.Spot spot : spots) {
                byPlace.put(spot.place().key(), spot.marketLevel());
            }
        }
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom)) {
            levels.put(shop.id(), byPlace.getOrDefault(shop.place().key(), 0));
        }
        return levels;
    }

    // ---------------------------------------------------------------- the day of the shops of a city

    /** The stages of the day of the shops of a city before the spending of each kind of goods. */
    private static final int STAGE_OPEN = 0;
    private static final int STAGE_MAKE = 1;
    private static final int STAGE_SPEND = 2;

    /**
     * Lives as much of the day of the shops of a city as the time allows: the keepers, the trades and new shops first, then the
     * work of the workshops, then the spending of the townspeople kind by kind, then the end of the day of every shop.
     *
     * @return whether it is done
     */
    static boolean dayOfShops(MinecraftServer server, UUID kingdom, long city, long day, long start) {
        String key = key(kingdom, city);
        Progress progress = PROGRESS.get(key);
        if (progress == null || progress.day != day) {
            progress = new Progress(day);
            PROGRESS.put(key, progress);
        }
        int kinds = GoodsCategory.values().length;
        while (System.nanoTime() - start < CommerceWork.BUDGET_NANOS) {
            if (progress.stage == STAGE_OPEN) {
                open(server, kingdom, city, day);
                progress.stage = STAGE_MAKE;
            } else if (progress.stage == STAGE_MAKE) {
                if (make(server, kingdom, city, day, progress, start)) {
                    // The trading houses buy of the masters and export before the townspeople spend.
                    Traders.day(server, kingdom, city, day);
                    beginSpending(server, kingdom, city, day, progress);
                    progress.stage = STAGE_SPEND;
                }
            } else if (progress.stage < STAGE_SPEND + kinds) {
                spendKind(server, kingdom, city, day, progress, progress.stage - STAGE_SPEND);
                progress.stage++;
            } else if (progress.stage == STAGE_SPEND + kinds) {
                if (endOfDay(server, kingdom, city, day, progress, start)) {
                    progress.stage++;
                }
            } else {
                finish(server, kingdom, city, progress);
                PROGRESS.remove(key);
                return true;
            }
        }
        return false;
    }

    /**
     * The keepers of the shops of a city are looked at, shops whose place is gone or whose keepers left without anybody to follow
     * close, every shop takes up the trade its place and its city call for (at most once a week, after it sold off), and new shops
     * open where people want more than the shops sell.
     */
    private static void open(MinecraftServer server, UUID kingdom, long city, long day) {
        CommerceSavedData data = CommerceSavedData.get(server);
        CityEconomyRules rules = CityEconomySettings.rules();
        List<BusinessPlaces.Spot> spots = BusinessPlaces.of(server, kingdom, city);
        Map<String, BusinessPlaces.Spot> byKey = new LinkedHashMap<>();
        boolean market = false;
        for (BusinessPlaces.Spot spot : spots) {
            byKey.put(spot.place().key(), spot);
            market |= spot.place().kind() == Business.PlaceKind.MARKET_STALL;
        }
        Map<UUID, Long> cities = CityOf.known(kingdom);
        List<Citizen> people = new ArrayList<>();
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom)) {
            Long known = cities.get(citizen.id());
            if ((known != null ? known : CityOf.of(server, citizen)) == city) {
                people.add(citizen);
            }
        }
        for (Citizen citizen : people) {
            if (citizen.business() != 0 && data.shop(citizen.business()).isEmpty()) {
                // The shop is gone: the citizen is a free townsperson again.
                citizen.setBusiness(0);
            }
        }
        Set<UUID> taken = new HashSet<>();
        for (Citizen citizen : people) {
            if (citizen.business() != 0) {
                taken.add(citizen.id());
            }
        }
        CommerceSavedData.CityTrade trade = data.city(kingdom, city);
        for (Business shop : data.shopsOf(kingdom, city)) {
            if (!byKey.containsKey(shop.place().key())) {
                close(server, shop, Closing.PLACE_GONE, null);
                continue;
            }
            List<UUID> keepers = new ArrayList<>();
            for (UUID keeper : shop.keepers()) {
                Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(keeper);
                Long keeperCity = cities.get(keeper);
                if (citizen.isPresent() && townsperson(citizen.get(), shop.id()) && (keeperCity == null || keeperCity == city)) {
                    keepers.add(keeper);
                    citizen.get().setBusiness(shop.id());
                    taken.add(keeper);
                } else {
                    citizen.filter(found -> found.business() == shop.id()).ifPresent(found -> {
                        found.setBusiness(0);
                        taken.remove(found.id());
                    });
                }
            }
            boolean family = false;
            if (keepers.size() < shop.place().kind().room()) {
                KeeperChoice.Choice more = choose(server, shop.place(), people, shop.id(), shop.place().kind().room() - keepers.size(), taken);
                for (UUID keeper : more.keepers()) {
                    keepers.add(keeper);
                    taken.add(keeper);
                    KingdomWorldApi.state(server).citizens().get(keeper).ifPresent(citizen -> citizen.setBusiness(shop.id()));
                }
                family = keepers.isEmpty() && more.family();
            }
            shop.setKeepers(keepers, family);
            if (!shop.kept()) {
                close(server, shop, Closing.KEEPER_GONE, null);
                continue;
            } else if (shop.status() == Business.Status.FAMILY && !shop.family()) {
                shop.setStatus(Business.Status.TRADING);
            } else if (shop.status() == Business.Status.TRADING && shop.family()) {
                shop.setStatus(Business.Status.FAMILY);
            }
            if (shop.status() != Business.Status.CLOSING) {
                String chosen = chooseTrade(server, kingdom, city, shop.place(), trade, market, shop.id(), shop.trade());
                String next = TradeChoice.next(shop.trade(), shop.pinned(), chosen, shop.tradeSince(), day);
                shop.setNextTrade(next);
                if (!shop.nextTrade().isEmpty() && shop.goods().isEmpty() && returnInputs(server, shop)) {
                    LOGGER.debug("The shop {} of {} takes up the trade {} instead of {}", shop.id(), kingdom, shop.nextTrade(), shop.trade());
                    shop.changeTrade(shop.nextTrade(), day);
                }
            }
        }
        List<Business> standing = data.shopsOf(kingdom, city);
        int openings = CityDay.openings(!standing.isEmpty(), trade.unspent, rules.fairIncome(), rules.turnoverPerWorker(), false);
        trade.noPlaces = false;
        trade.noKeepers = false;
        if (openings <= 0) {
            data.setDirty();
            return;
        }
        Set<String> used = new HashSet<>();
        for (Business shop : standing) {
            used.add(shop.place().key());
        }
        boolean freePlace = false;
        int opened = 0;
        for (BusinessPlaces.Spot spot : spots) {
            if (opened >= openings) {
                break;
            }
            String placeKey = spot.place().key();
            Long closed = trade.closed.get(placeKey);
            if (used.contains(placeKey) || (closed != null && day - closed < rules.closeAfterDays())) {
                continue;
            }
            String chosen = chooseTrade(server, kingdom, city, spot.place(), trade, market, 0, "");
            // A general store needs the goods of the crown; a workshop makes its own of raw materials.
            if (chosen.equals(Business.GENERAL_STORE) && trade.short_) {
                continue;
            }
            freePlace = true;
            KeeperChoice.Choice choice = choose(server, spot.place(), people, 0, spot.place().kind().room(), taken);
            if (!choice.any()) {
                continue;
            }
            Business shop = data.open(kingdom, city, spot.place(), day,
                    Math.max(rules.minMarginPercent() * 10, Math.min(rules.maxMarginPercent() * 10, START_MARGIN)));
            shop.changeTrade(chosen, day);
            shop.setKeepers(choice.keepers(), choice.family());
            shop.setStatus(choice.family() ? Business.Status.FAMILY : Business.Status.TRADING);
            for (UUID keeper : choice.keepers()) {
                taken.add(keeper);
                KingdomWorldApi.state(server).citizens().get(keeper).ifPresent(citizen -> citizen.setBusiness(shop.id()));
            }
            used.add(placeKey);
            opened++;
            LOGGER.debug("A shop of the trade {} opened at {} in city {} of {}", chosen, placeKey, city, kingdom);
        }
        trade.noPlaces = !freePlace;
        trade.noKeepers = freePlace && opened == 0;
        data.setDirty();
    }

    /**
     * The trade a place of a city would take up now.
     *
     * @param market  whether the city has a working market
     * @param except  the shop of the place, not counted among the masters of the city
     * @param current the trade the shop of the place has, empty for a free place
     */
    static String chooseTrade(MinecraftServer server, UUID kingdom, long city, Business.Place place, CommerceSavedData.CityTrade trade,
                              boolean market, long except, String current) {
        Optional<CommerceSavedData.Survey> survey = CommerceSavedData.get(server).survey(place.key());
        boolean masters = false;
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom, city)) {
            if (shop.id() != except && shop.crafts()) {
                masters = true;
                break;
            }
        }
        TradeChoice.Place known = new TradeChoice.Place(survey.map(CommerceSavedData.Survey::stations).orElse(Set.of()), survey.isPresent(),
                market || place.kind() == Business.PlaceKind.MERCHANT_HOUSE);
        TradeChoice.City needs = new TradeChoice.City(trade.unmet, masters, outsideUnchosen(server, kingdom, city));
        return current.isEmpty() ? TradeChoice.choose(TradeRegistry.current().all(), known, needs)
                : TradeChoice.chooseFor(TradeRegistry.current().all(), known, needs, current);
    }

    /**
     * The demand from abroad of a city no trading house took yesterday, as far as it would feed a new one.
     */
    static double outsideUnchosen(MinecraftServer server, UUID kingdom, long city) {
        return Traders.unchosen(server, kingdom, city);
    }

    /**
     * A workshop gives up its trade: the crown buys back its raw materials at the lower of its price of buying and what they cost,
     * as far as it can pay and store them.
     *
     * @return whether nothing is left
     */
    static boolean returnInputs(MinecraftServer server, Business shop) {
        if (shop.inputs().isEmpty()) {
            shop.setFuel(0, 0);
            return true;
        }
        UUID kingdom = shop.kingdomId();
        Economy economy = KingdomWorldApi.state(server).economy();
        TradeRules rules = TradeSettings.rules();
        Map<String, Long> counts = KingdomStorage.countsById(server, kingdom);
        List<ItemStack> stacks = new ArrayList<>();
        long value = 0;
        Map<String, Long> prices = new HashMap<>();
        for (Map.Entry<String, Business.Lot> entry : shop.inputs().entrySet()) {
            Item item = GoodsStacks.item(entry.getKey());
            if (item == null) {
                continue;
            }
            long price = ShopPricing.buyback(Prices.autoBuyPrice(worth(server, item), counts.getOrDefault(entry.getKey(), 0L), rules),
                    entry.getValue().costMilli());
            prices.put(entry.getKey(), price);
            value = Money.addCapped(value, Money.multiplyCapped(price, entry.getValue().count()));
            stacks.addAll(stacks(item, entry.getValue().count()));
        }
        long crown = economy.treasury(kingdom).map(treasury -> treasury.funds()).orElse(0L);
        if (value > crown || !KingdomStorage.fits(server, kingdom, stacks)) {
            return false;
        }
        long day = TreasuryActions.day(server);
        WorldPos near = new WorldPos(shop.place().dimension(), shop.place().x(), shop.place().y(), shop.place().z());
        long paid = 0;
        for (Map.Entry<String, Business.Lot> entry : new LinkedHashMap<>(shop.inputs()).entrySet()) {
            long stored = insertAll(server, kingdom, entry.getKey(), entry.getValue().count(), near);
            paid = Money.addCapped(paid, Money.multiplyCapped(prices.getOrDefault(entry.getKey(), 0L), stored));
            shop.removeInput(entry.getKey(), entry.getValue().count());
        }
        if (paid > 0) {
            economy.treasuryToPurse(CityPurses.PurseKey.till(kingdom, shop.id()), paid, LedgerCategory.COMMERCE, day);
        }
        shop.setFuel(0, 0);
        return true;
    }

    private static List<ItemStack> stacks(Item item, long count) {
        List<ItemStack> stacks = new ArrayList<>();
        while (count > 0) {
            int part = (int) Math.min(count, item.getMaxStackSize());
            stacks.add(new ItemStack(item, part));
            count -= part;
        }
        return stacks;
    }

    /**
     * The workshops of a city make what they planned and their raw materials allow, a workshop at a time: their raw materials are
     * used up, their goods made by citizens come in at what went into them, and the record of production of the kingdom counts
     * what they made.
     *
     * @return whether every workshop is done
     */
    private static boolean make(MinecraftServer server, UUID kingdom, long city, long day, Progress progress, long start) {
        CommerceSavedData data = CommerceSavedData.get(server);
        if (progress.workshops == null) {
            List<Long> ids = new ArrayList<>();
            for (Business shop : data.shopsOf(kingdom, city)) {
                if (shop.crafts() && PLANS.containsKey(shop.id())) {
                    ids.add(shop.id());
                }
            }
            progress.workshops = ids;
        }
        CityEconomyRules rules = CityEconomySettings.rules();
        while (progress.makeCursor < progress.workshops.size()) {
            if (System.nanoTime() - start >= CommerceWork.BUDGET_NANOS) {
                return false;
            }
            long id = progress.workshops.get(progress.makeCursor++);
            List<ShopMaking.Plan> plans = PLANS.remove(id);
            Optional<Business> found = data.shop(id);
            if (plans == null || found.isEmpty() || !found.get().kept()) {
                continue;
            }
            Business shop = found.get();
            Trade trade = Workshops.trade(shop);
            ShopMaking.Context context = Workshops.context(server, trade, Workshops.placeStations(server, shop), item -> -1, FUEL.get(kingdom));
            long ticks = ShopMaking.workTicks(Workshops.pace(server, shop), shop.family(), rules.familyShopPercent());
            double madeWorth = 0;
            String profession = trade.profession().isEmpty() ? Professions.CRAFTER.id() : trade.profession();
            for (ShopMaking.Plan plan : plans) {
                if (ticks <= 0) {
                    break;
                }
                ShopMaking.Made made = ShopMaking.make(shop, plan, ticks, context);
                ticks -= made.ticks();
                if (made.count() > 0) {
                    madeWorth += worth(server, made.item()) * made.count();
                    KingdomWorldApi.state(server).citizens().ledger().record(kingdom, day, profession, made.item(), made.count());
                }
            }
            if (madeWorth > 0) {
                shop.record(day, new Business.Day(0, 0, 0, 0, null, (long) Math.floor(madeWorth), 0, 0));
                if (shop.status() == Business.Status.EMPTY) {
                    shop.setStatus(shop.family() ? Business.Status.FAMILY : Business.Status.TRADING);
                }
            }
        }
        data.setDirty();
        return true;
    }

    private static void beginSpending(MinecraftServer server, UUID kingdom, long city, long day, Progress progress) {
        Economy economy = KingdomWorldApi.state(server).economy();
        CityEconomyRules rules = CityEconomySettings.rules();
        CommerceSavedData data = CommerceSavedData.get(server);
        CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom, city);
        long balance = economy.purse(purse);
        long afar = CityDay.afar(balance, rules.afarPercent());
        if (afar > 0) {
            economy.purseLeaves(purse, city, afar, CityPurses.Outflow.AFAR, day);
        }
        CityLevel level = CityWalls.level(server, kingdom, city);
        CommerceSavedData.CityTrade trade = data.city(kingdom, city);
        CityDay.Budgets budgets = CityDay.budgets(balance, trade.carry, rules.basket(), level != CityLevel.VILLAGE);
        trade.carry = budgets.carry();
        progress.budgets = budgets.budgets();
        progress.wanted = budgets.total();
        progress.tax = CommerceWork.salesTax(server, kingdom, city);
        progress.evasion = CommerceWork.evasion(server, kingdom, city);
        room(server, kingdom, day);
    }

    /**
     * What a shop sold since the end of its last day, to be filled.
     */
    static long[] sold(long shop) {
        return SOLD.computeIfAbsent(shop, k -> new long[3 + GoodsCategory.values().length]);
    }

    /**
     * The worth the shops of a kingdom can still sell today: their strength, less what they sold today to townspeople of any city.
     */
    private static Map<Long, Double> room(MinecraftServer server, UUID kingdom, long day) {
        if (day != roomDay) {
            ROOM.clear();
            roomDay = day;
        }
        CityEconomyRules rules = CityEconomySettings.rules();
        Map<Long, Integer> levels = null;
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom)) {
            if (!ROOM.containsKey(shop.id())) {
                if (levels == null) {
                    levels = marketLevels(server, kingdom);
                }
                ROOM.put(shop.id(), strength(server, shop, levels.getOrDefault(shop.id(), 0), rules));
            }
        }
        return ROOM;
    }

    private static List<CityDay.Offer> offers(MinecraftServer server, List<Business> shops, int kind) {
        List<CityDay.Offer> offers = new ArrayList<>();
        for (Business shop : shops) {
            for (Map.Entry<String, Business.Lot> entry : shop.goods().entrySet()) {
                Business.Lot lot = entry.getValue();
                Item item = GoodsStacks.item(Business.itemOf(entry.getKey()));
                if (lot.category() != kind || lot.count() <= 0 || item == null) {
                    continue;
                }
                long price = ShopMaking.askedMilli(lot, shop.marginPermille(), ceilingMilli(server, item), worthMilli(server, item));
                offers.add(new CityDay.Offer(shop.id(), entry.getKey(), kind, worth(server, item), price, lot.costMilli(), lot.count()));
            }
        }
        return offers;
    }

    /**
     * The townspeople of a city spend the budget of a kind of goods: the lots of players cheaper than the shops of the city first,
     * then the shops of their own city, then, what is left, the shops of their regional market, the nearest larger city of their
     * kingdom on a road. The sales tax goes to the treasury of the city where the shop stands.
     */
    private static void spendKind(MinecraftServer server, UUID kingdom, long city, long day, Progress progress, int kind) {
        CommerceSavedData data = CommerceSavedData.get(server);
        Economy economy = KingdomWorldApi.state(server).economy();
        CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom, city);
        long budget = Math.min(progress.budgets[kind], economy.purse(purse));
        if (budget <= 0) {
            return;
        }
        List<Business> own = data.shopsOf(kingdom, city);
        long left = budget - PlayerLots.buy(server, kingdom, city, day, kind, budget, own, progress.tax, progress.evasion);
        long spentLots = budget - left;
        progress.spent += spentLots;
        progress.spentKind[kind] += spentLots;
        left -= spendIn(server, kingdom, city, city, day, progress, kind, left, own, progress.tax, progress.evasion);
        if (left > 0 && CraftSettings.rules().regionalMarket()) {
            Long regional = CityTies.of(server, kingdom).regional().get(city);
            if (regional != null) {
                spendIn(server, kingdom, city, regional, day, progress, kind, left, data.shopsOf(kingdom, regional),
                        CommerceWork.salesTax(server, kingdom, regional), CommerceWork.evasion(server, kingdom, regional));
            }
        }
        data.setDirty();
    }

    /**
     * The townspeople of a city buy in the shops of a city, theirs or their regional market.
     *
     * @return what they spent, the tax included
     */
    private static long spendIn(MinecraftServer server, UUID kingdom, long city, long shopCity, long day, Progress progress, int kind, long budget,
                                List<Business> shops, int tax, int evasion) {
        if (budget <= 0) {
            return 0;
        }
        List<CityDay.Offer> offers = offers(server, shops, kind);
        if (offers.isEmpty()) {
            return 0;
        }
        CommerceSavedData data = CommerceSavedData.get(server);
        Economy economy = KingdomWorldApi.state(server).economy();
        CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom, city);
        long spent = 0;
        for (CityDay.Sale sale : CityDay.spendKind(budget, offers, room(server, kingdom, day), tax)) {
            Optional<Business> shop = data.shop(sale.shop());
            if (shop.isEmpty()) {
                continue;
            }
            Business.Lot lot = shop.get().goods().get(sale.item());
            long collected = Evasion.collected(sale.tax(), evasion);
            long toTill = sale.total() + sale.tax() - collected;
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, sale.shop());
            if (lot == null || !economy.purseMove(purse, till, toTill, CityLedger.OTHER, day).ok()) {
                continue;
            }
            economy.purseMove(purse, CityPurses.PurseKey.cityTreasury(kingdom, shopCity), collected, CityLedger.SALES_TAX, day);
            shop.get().removeGoods(sale.item(), sale.count());
            long[] sold = sold(sale.shop());
            sold[0] += toTill;
            sold[1] += sale.cost();
            sold[2] += ShopPricing.total(lot.laborMilli(), sale.count());
            sold[3 + kind] += (long) Math.floor(sale.count() * sale.worth());
            progress.spent += sale.total() + sale.tax();
            progress.spentKind[kind] += sale.total() + sale.tax();
            spent += sale.total() + sale.tax();
            economy.cityTally(kingdom, shopCity).add(CityStatement.Line.SALES, sale.total());
            economy.cityTally(kingdom, city).add(CityStatement.Line.BASKET_SPENT, sale.total() + sale.tax());
        }
        return spent;
    }

    /**
     * The end of the day of the shops of a city, a shop at a time: the upkeep leaves the world, the debt to the crown is paid
     * back first, what is beyond the working money is profit for the keepers, the margin moves, and a shop that has fed its keepers
     * too little for too long closes. Masters earn the work in the goods they sold as well as the margin; they are measured against
     * the usual wage of their trade.
     *
     * @return whether every shop is done
     */
    private static boolean endOfDay(MinecraftServer server, UUID kingdom, long city, long day, Progress progress, long start) {
        CommerceSavedData data = CommerceSavedData.get(server);
        Economy economy = KingdomWorldApi.state(server).economy();
        CityEconomyRules rules = CityEconomySettings.rules();
        if (progress.cursor == 0) {
            List<Long> ids = new ArrayList<>();
            for (Business shop : data.shopsOf(kingdom, city)) {
                ids.add(shop.id());
            }
            progress.shops = ids;
        }
        while (progress.cursor < progress.shops.size()) {
            if (System.nanoTime() - start >= CommerceWork.BUDGET_NANOS) {
                return false;
            }
            Optional<Business> found = data.shop(progress.shops.get(progress.cursor++));
            if (found.isEmpty()) {
                continue;
            }
            Business shop = found.get();
            // The work the crown paid for in advance is delivered before the day of the shop is counted.
            CrownPurchases.deliverCommissions(server, shop, day);
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
            long[] sold = SOLD.remove(shop.id());
            if (sold == null) {
                sold = new long[3 + GoodsCategory.values().length];
            }
            long workingCapital = Money.multiplyCapped(2, Math.max(shop.averageBought(day), shop.day(day).map(Business.Day::bought).orElse(0L)));
            CityDay.Result result = CityDay.result(economy.purse(till), sold[0], shop.debt(), workingCapital, shop.profitDue(),
                    rules.businessUpkeepPercent());
            if (result.upkeep() > 0) {
                economy.purseLeaves(till, city, result.upkeep(), CityPurses.Outflow.UPKEEP, day);
            }
            if (result.debtPaid() > 0 && economy.purseToTreasury(till, result.debtPaid(), LedgerCategory.COMMERCE, day).ok()) {
                shop.setDebt(shop.debt() - result.debtPaid());
                economy.cityTally(kingdom, city).add(CityStatement.Line.CROWN_GOODS_SOLD, result.debtPaid());
            }
            shop.setProfitDue(shop.profitDue() + result.profit());
            int earners = shop.family() ? 1 : Math.max(1, shop.keepers().size());
            // What the trade earned today, before what the shop keeps for its goods and its debt: whether it feeds its keepers.
            long earned = CityDay.earned(sold[0], sold[1], result.upkeep()) / earners;
            long[] categories = new long[GoodsCategory.values().length];
            System.arraycopy(sold, 3, categories, 0, categories.length);
            shop.record(day, new Business.Day(sold[0], sold[1], 0, earned, categories));
            Trade trade = Workshops.trade(shop);
            long usual = Workshops.usualIncome(trade);
            long fair = Money.share(usual, rules.fairIncomePercent(), 100);
            // The work in the goods sold already pays the masters part of their fair income; the margin aims at the rest.
            long fairLeft = Math.max(0, Money.multiplyCapped(fair, earners) - sold[2]) / earners;
            shop.setMarginPermille(ShopPricing.nextMargin(shop.marginPermille(), fairLeft, earners, sold[1] + sold[2], rules.minMarginPercent(),
                    rules.maxMarginPercent(), rules.marginStepPercent(), rules.businessUpkeepPercent()));
            boolean young = day - shop.opened() < 2;
            boolean poor = !young && CityDay.poor(earned, usual);
            shop.setPoorDays(poor ? shop.poorDays() + 1 : 0);
            if (shop.status() == Business.Status.CLOSING || shop.poorDays() >= rules.closeAfterDays()) {
                if (close(server, shop, Closing.POOR, null)) {
                    data.city(kingdom, city).closed.put(shop.place().key(), day);
                }
                continue;
            }
            if (shop.goods().isEmpty()) {
                shop.setStatus(Business.Status.EMPTY);
            } else {
                shop.setStatus(shop.family() ? Business.Status.FAMILY : Business.Status.TRADING);
            }
        }
        data.setDirty();
        return true;
    }

    private static void finish(MinecraftServer server, UUID kingdom, long city, Progress progress) {
        CommerceSavedData data = CommerceSavedData.get(server);
        CommerceSavedData.CityTrade trade = data.city(kingdom, city);
        trade.unspent = Math.max(0, progress.wanted - progress.spent);
        for (GoodsCategory category : GoodsCategory.values()) {
            int kind = category.ordinal();
            trade.unmet[kind] = category.basket() ? Math.max(0, progress.budgets[kind] - progress.spentKind[kind]) : trade.wantedMaterials;
        }
        trade.wantedMaterials = 0;
        trade.closed.entrySet().removeIf(entry -> progress.day - entry.getValue() > CityEconomySettings.rules().closeAfterDays());
        CityStatement.Tally tally = KingdomWorldApi.state(server).economy().cityTally(kingdom, city);
        tally.add(CityStatement.Line.BASKET_WANTED, progress.wanted);
        tally.add(CityStatement.Line.UNSPENT, trade.unspent);
        data.setDirty();
    }

    // ---------------------------------------------------------------- closing

    /**
     * Why a shop closes.
     */
    enum Closing {
        /** Its keepers earned too little for too long. */
        POOR,
        /** Its keepers left and nobody follows. */
        KEEPER_GONE,
        /** Its place is gone: no tries again. */
        PLACE_GONE,
        /** Its city went to another kingdom: no buying back, the goods are loot. */
        HANDED_OVER
    }

    /**
     * Closes a shop. The crown buys its goods and raw materials back into the storehouses at the lower of its price of buying and
     * what the shop paid, the debt of the shop first; a shop whose place stands waits and sells off while the crown cannot pay or
     * has no room. A shop whose place is gone is bought back as far as the crown can pay and store, the rest of its goods are lost.
     * Work paid in advance and not delivered goes back to the crown. The till goes home with its keeper, else to the townspeople of
     * its city.
     *
     * @param winner the kingdom that took the city of a shop handed over
     * @return whether it closed
     */
    static boolean close(MinecraftServer server, Business shop, Closing why, UUID winner) {
        CommerceSavedData data = CommerceSavedData.get(server);
        Economy economy = KingdomWorldApi.state(server).economy();
        UUID kingdom = shop.kingdomId();
        long day = TreasuryActions.day(server);
        CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
        WorldPos near = new WorldPos(shop.place().dimension(), shop.place().x(), shop.place().y(), shop.place().z());
        // Everything it holds, goods and raw materials, by item.
        Map<String, Business.Lot> holdings = new LinkedHashMap<>();
        for (Map.Entry<String, Business.Lot> entry : shop.goods().entrySet()) {
            holdings.merge(Business.itemOf(entry.getKey()), entry.getValue(), Shops::merge);
        }
        for (Map.Entry<String, Business.Lot> entry : shop.inputs().entrySet()) {
            holdings.merge(entry.getKey(), entry.getValue(), Shops::merge);
        }
        if (why == Closing.HANDED_OVER) {
            // The goods are loot of the winner, as far as its storehouses take them; the debt to the old crown is gone with it.
            if (winner != null) {
                for (Map.Entry<String, Business.Lot> entry : holdings.entrySet()) {
                    insertAll(server, winner, entry.getKey(), entry.getValue().count(), near);
                }
            }
            shop.clearGoods();
            shop.setDebt(0);
        } else {
            boolean placeStands = why != Closing.PLACE_GONE;
            TradeRules rules = TradeSettings.rules();
            Map<String, Long> counts = holdings.isEmpty() ? Map.of() : KingdomStorage.countsById(server, kingdom);
            long value = 0;
            List<ItemStack> stacks = new ArrayList<>();
            Map<String, Long> prices = new HashMap<>();
            for (Map.Entry<String, Business.Lot> entry : holdings.entrySet()) {
                Item item = GoodsStacks.item(entry.getKey());
                if (item == null) {
                    continue;
                }
                long buy = Prices.autoBuyPrice(worth(server, item), counts.getOrDefault(entry.getKey(), 0L), rules);
                long price = ShopPricing.buyback(buy, entry.getValue().costMilli());
                prices.put(entry.getKey(), price);
                value = Money.addCapped(value, Money.multiplyCapped(price, entry.getValue().count()));
                stacks.addAll(stacks(item, entry.getValue().count()));
            }
            long net = value - shop.debt();
            long crown = economy.treasury(kingdom).map(treasury -> treasury.funds()).orElse(0L);
            boolean room = stacks.isEmpty() || KingdomStorage.fits(server, kingdom, stacks);
            if (placeStands && (!room || net > crown)) {
                // The crown cannot buy it all now: the shop sells off without buying and tries again tomorrow.
                if (shop.status() != Business.Status.CLOSING) {
                    shop.setStatus(Business.Status.CLOSING);
                    data.setDirty();
                }
                return false;
            }
            long bought = 0;
            for (Map.Entry<String, Business.Lot> entry : holdings.entrySet()) {
                long price = prices.getOrDefault(entry.getKey(), 0L);
                long count = entry.getValue().count();
                long afford = price <= 0 ? count : Math.max(0, (crown + shop.debt() - bought) / price);
                long stored = insertAll(server, kingdom, entry.getKey(), Math.min(count, afford), near);
                bought = Money.addCapped(bought, Money.multiplyCapped(price, stored));
            }
            shop.clearGoods();
            // The debt is paid first out of what the crown pays; what the shop still owes, its till pays; the rest is written off.
            long settle = bought - shop.debt();
            if (settle > 0) {
                economy.treasuryToPurse(till, settle, LedgerCategory.COMMERCE, day);
            } else if (settle < 0) {
                long owed = Math.min(-settle, economy.purse(till));
                if (owed > 0 && economy.purseToTreasury(till, owed, LedgerCategory.COMMERCE, day).ok()) {
                    economy.cityTally(kingdom, shop.cityId()).add(CityStatement.Line.CROWN_GOODS_SOLD, owed);
                }
            }
            shop.setDebt(0);
        }
        CrownPurchases.cancelCommissions(server, shop, day);
        // The till goes home with the first keeper, the rest to the townspeople of the city.
        long money = economy.purse(till);
        if (money > 0) {
            Optional<Citizen> keeper = shop.keepers().isEmpty() ? Optional.empty()
                    : KingdomWorldApi.state(server).citizens().get(shop.keepers().get(0));
            long home = keeper.map(citizen -> toHouse(server, economy, till, citizen, money)).orElse(0L);
            economy.purseMove(till, CityPurses.PurseKey.cityPurse(kingdom, shop.cityId()), money - home, CityLedger.OTHER, day);
        }
        for (UUID id : shop.keepers()) {
            KingdomWorldApi.state(server).citizens().get(id).filter(citizen -> citizen.business() == shop.id())
                    .ifPresent(citizen -> citizen.setBusiness(0));
        }
        PLANS.remove(shop.id());
        data.remove(shop.id());
        LOGGER.info("The shop {} at {} of {} closed: {}", shop.id(), shop.place().key(), kingdom, why);
        return true;
    }

    /**
     * Two lots of one item together, at the cost of both.
     */
    private static Business.Lot merge(Business.Lot a, Business.Lot b) {
        long count = a.count() + b.count();
        java.math.BigInteger cost = java.math.BigInteger.valueOf(a.count()).multiply(java.math.BigInteger.valueOf(a.costMilli()))
                .add(java.math.BigInteger.valueOf(b.count()).multiply(java.math.BigInteger.valueOf(b.costMilli())));
        return new Business.Lot(a.category(), count, cost.divide(java.math.BigInteger.valueOf(Math.max(1, count))).longValue());
    }

    /**
     * Puts items into the storehouses of a kingdom as far as they have room.
     *
     * @return how many went in
     */
    static long insertAll(MinecraftServer server, UUID kingdom, String id, long count, WorldPos near) {
        Item item = GoodsStacks.item(id);
        if (item == null || count <= 0) {
            return 0;
        }
        long stored = 0;
        while (count > 0) {
            int part = (int) Math.min(count, item.getMaxStackSize());
            ItemStack rest = KingdomStorage.insert(server, kingdom, new ItemStack(item, part), near);
            stored += part - rest.getCount();
            count -= part;
            if (!rest.isEmpty()) {
                break;
            }
        }
        return stored;
    }

    /**
     * The place of a shop is gone (a counter broken): its shop closes for good.
     */
    static void placeGone(MinecraftServer server, String placeKey) {
        CommerceSavedData.get(server).at(placeKey).ifPresent(shop -> close(server, shop, Closing.PLACE_GONE, null));
    }

    /**
     * The shops of a city close: with the crown buying back their goods, or as loot of a winner.
     */
    static void closeCity(MinecraftServer server, UUID kingdom, long city, boolean buyout) {
        CommerceSavedData data = CommerceSavedData.get(server);
        for (Business shop : data.shopsOf(kingdom, city)) {
            close(server, shop, Closing.PLACE_GONE, null);
        }
        data.forgetCity(kingdom, city);
        PROGRESS.remove(key(kingdom, city));
    }

    /**
     * A city went to another kingdom: its shops close at once, without buying back and without tries again, their goods loot of
     * the winner, their tills to the townspeople of the city, who go to the winner next.
     */
    static void handOver(MinecraftServer server, UUID from, long city, UUID to) {
        CommerceSavedData data = CommerceSavedData.get(server);
        for (Business shop : data.shopsOf(from, city)) {
            close(server, shop, Closing.HANDED_OVER, to);
        }
        data.forgetCity(from, city);
        PROGRESS.remove(key(from, city));
    }

    /**
     * A keeper took a post of the crown: it leaves its shop, and the next day of the city finds another keeper, a family or none.
     */
    public static void keeperLeft(MinecraftServer server, Citizen citizen) {
        if (citizen.business() == 0) {
            return;
        }
        CommerceSavedData data = CommerceSavedData.get(server);
        data.shop(citizen.business()).ifPresent(shop -> {
            List<UUID> keepers = new ArrayList<>(shop.keepers());
            keepers.remove(citizen.id());
            shop.setKeepers(keepers, false);
            data.setDirty();
        });
        citizen.setBusiness(0);
    }

    /**
     * The worth of the goods and raw materials of all the shops of a kingdom, for the wealth that the exchange rate counts.
     */
    public static long goodsWorth(MinecraftServer server, UUID kingdom) {
        long sum = 0;
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom)) {
            for (Map.Entry<String, Business.Lot> entry : shop.goods().entrySet()) {
                Item item = GoodsStacks.item(Business.itemOf(entry.getKey()));
                if (item != null) {
                    sum = Money.addCapped(sum, (long) Math.floor(worth(server, item) * entry.getValue().count()));
                }
            }
            for (Map.Entry<String, Business.Lot> entry : shop.inputs().entrySet()) {
                Item item = GoodsStacks.item(entry.getKey());
                if (item != null) {
                    sum = Money.addCapped(sum, (long) Math.floor(worth(server, item) * entry.getValue().count()));
                }
            }
        }
        return sum;
    }

    /**
     * The place of the shop a citizen keeps, for its body to stand at.
     */
    public static Optional<Business.Place> placeOf(MinecraftServer server, long shop) {
        return CommerceSavedData.get(server).shop(shop).map(Business::place);
    }
}
