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
import io.github.sekerator.kingdomworld.core.commerce.ShopPricing;
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
 * The shops of the citizens of the kingdoms of players: where they open, who keeps them, what they buy of the crown, what the
 * townspeople buy of them, what is left at the end of their day, what their keepers earn and how they close.
 *
 * <p>Nothing is made out of nothing. Goods come out of the storehouses of the crown and are paid for (or owed, the first goods
 * of a new shop); what the townspeople buy disappears (eaten, worn out); what players buy goes into their inventories; what a
 * closing shop has left goes back into the storehouses, bought back. Money moves between the purse of the townspeople, the
 * tills, the treasury of the city and that of the kingdom, or leaves the world for the upkeep of the shops.
 */
public final class Shops {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<GoodsCategory, TagKey<Item>> TAGS = new HashMap<>();
    /** The kind of goods of each item, -1 for none, as the tags said on the day noted. */
    private static final Map<String, Integer> CATEGORY = new HashMap<>();
    private static long categoryDay = Long.MIN_VALUE;
    /** How far the day of each city went. */
    private static final Map<String, Progress> PROGRESS = new HashMap<>();
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
        long wanted;
        long spent;
        int tax;
        int evasion;
        final Map<Long, Double> capacity = new HashMap<>();
        final Map<Long, long[]> sold = new HashMap<>();
        List<Long> shops = List.of();
        int cursor;

        Progress(long day) {
            this.day = day;
        }
    }

    private Shops() {
    }

    public static void reset() {
        CATEGORY.clear();
        categoryDay = Long.MIN_VALUE;
        PROGRESS.clear();
        BusinessPlaces.reset();
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
                    shop.marginPermille(), shop.debt()));
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
            double pace = 0;
            for (UUID keeper : shop.keepers()) {
                pace += KingdomWorldApi.state(server).citizens().get(keeper).map(citizen -> EconomySettings.workSpeed(citizen.mood())).orElse(1.0);
            }
            base = rules.turnoverPerWorker() * pace;
        }
        if (shop.place().kind() == Business.PlaceKind.MARKET_STALL && marketLevel > 0) {
            base *= 1.0 + 0.25 * marketLevel;
        }
        return base;
    }

    // ---------------------------------------------------------------- the payday of the shops

    /**
     * The payday of a kingdom: every shop pays what it earned the day before, the tithe to the treasury of its city (less what
     * its people evade), the rest to its keepers as a wage: a fifth home to the house of each, the rest into the purse of the
     * townspeople of the city of each. The mood of a keeper follows what it earned, never below that of the idle.
     */
    static void payday(MinecraftServer server, UUID kingdom, long day) {
        CommerceSavedData data = CommerceSavedData.get(server);
        Economy economy = KingdomWorldApi.state(server).economy();
        CityEconomyRules rules = CityEconomySettings.rules();
        HouseholdRules household = Households.rules();
        for (Business shop : data.shopsOf(kingdom)) {
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
            long profit = Math.min(shop.profitDue(), economy.purse(till));
            shop.setProfitDue(0);
            List<Citizen> earners = earners(server, shop);
            if (profit <= 0 || earners.isEmpty()) {
                markKeepers(shop, earners, 0, rules);
                continue;
            }
            long tithe = Money.share(profit, CommerceWork.tithe(server, kingdom, shop.cityId()), 1000);
            long collected = Evasion.collected(tithe, CommerceWork.evasion(server, kingdom, shop.cityId()));
            economy.purseMove(till, CityPurses.PurseKey.cityTreasury(kingdom, shop.cityId()), collected, CityLedger.TITHE, day);
            long rest = profit - collected;
            long each = rest / earners.size();
            long first = rest - each * (earners.size() - 1);
            for (int i = 0; i < earners.size(); i++) {
                Citizen earner = earners.get(i);
                long pay = i == 0 ? first : each;
                long home = household.share(pay);
                long kept = home > 0 ? toHouse(server, economy, till, earner, home) : 0;
                economy.purseMove(till, CityPurses.PurseKey.cityPurse(kingdom, CityOf.of(server, earner)), pay - kept, CityLedger.OTHER, day);
            }
            markKeepers(shop, earners, profit / Math.max(1, earners.size()), rules);
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
    private static void markKeepers(Business shop, List<Citizen> earners, long earned, CityEconomyRules rules) {
        if (shop.family()) {
            return;
        }
        for (Citizen keeper : earners) {
            int percent = Payroll.percent(earned, rules.keeperUsualIncome());
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

    private static boolean newShop(Business shop) {
        return shop.startingOut();
    }

    /**
     * The shops of a kingdom buy of the crown, once a day before the days of its cities: one count of the storehouses, the split of
     * what the crown can spare, one taking. A new shop gets its first goods on credit.
     */
    static void restock(MinecraftServer server, UUID kingdom, long day) {
        CommerceSavedData data = CommerceSavedData.get(server);
        List<Business> shops = data.shopsOf(kingdom);
        if (shops.isEmpty()) {
            return;
        }
        var state = KingdomWorldApi.state(server);
        Economy economy = state.economy();
        CityEconomyRules rules = CityEconomySettings.rules();
        TradeRules tradeRules = TradeSettings.rules();
        Market market = state.trade().market(kingdom, tradeRules);
        Map<Long, CommerceSavedData.CityTrade> trades = new HashMap<>();
        for (Business shop : shops) {
            trades.computeIfAbsent(shop.cityId(), city -> data.city(kingdom, city));
        }
        for (CommerceSavedData.CityTrade trade : trades.values()) {
            trade.short_ = false;
            trade.missing = "";
        }
        if (!market.sellToCitizens()) {
            LOGGER.debug("The market of {} sells nothing to the shops of citizens", kingdom);
            return;
        }
        Map<String, Long> counts = KingdomStorage.countsById(server, kingdom);
        Map<String, Long> kept = new HashMap<>();
        for (var order : state.orders().ordersOf(kingdom)) {
            if (order.kind() == io.github.sekerator.kingdomworld.core.production.Order.Kind.KEEP && !order.paused()) {
                kept.merge(order.item(), (long) order.amount(), Long::sum);
            }
        }
        Map<String, Integer> reserved = io.github.sekerator.kingdomworld.common.production.Production.reservations().reserved(kingdom);
        Map<String, Long> free = new HashMap<>();
        counts.forEach((id, count) -> free.put(id, Math.max(0, count - kept.getOrDefault(id, 0L) - reserved.getOrDefault(id, 0))));
        long eaters = state.citizens().registry().count(kingdom) + io.github.sekerator.kingdomworld.common.road.RoadWork.state(server).folkOf(kingdom);
        var citizenRules = io.github.sekerator.kingdomworld.common.citizen.CitizenSettings.rules();
        long floor = Money.multiplyCapped(Money.multiplyCapped(citizenRules.growthFoodDays(), citizenRules.dailyNutrition()), eaters);
        long spare = Math.max(0, KingdomStorage.totalNutrition(server, kingdom) - floor);

        Map<String, Integer> nutrition = new HashMap<>();
        Map<String, Long> unitCost = new HashMap<>();
        Map<String, Item> items = new HashMap<>();
        List<RestockPlan.Need> needs = new ArrayList<>();
        Set<Long> credit = new HashSet<>();
        Map<Long, Integer> marketLevels = marketLevels(server, kingdom);
        // Why goods were not bought, for the debug log: shops closing or without keepers, items the crown cannot spare, items too
        // dear to sell on.
        int idle = 0;
        int unspared = 0;
        int dear = 0;
        for (Business shop : shops) {
            if (shop.status() == Business.Status.CLOSING || !shop.kept()) {
                idle++;
                continue;
            }
            boolean onCredit = newShop(shop);
            if (onCredit) {
                credit.add(shop.id());
            }
            long budget = onCredit ? rules.shopCredit()
                    : Math.max(0, economy.purse(CityPurses.PurseKey.till(kingdom, shop.id())) - shop.profitDue());
            double strength = strength(server, shop, marketLevels.getOrDefault(shop.id(), 0), rules);
            long left = budget;
            for (GoodsCategory category : GoodsCategory.values()) {
                long average = shop.averageCategory(day, category.ordinal());
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
                    if (free.getOrDefault(id, 0L) <= 0) {
                        unspared++;
                        continue;
                    }
                    long cost = crownPrice(server, kingdom, id, item, counts.getOrDefault(id, 0L));
                    if (!ShopPricing.worthBuying(cost * ShopPricing.MILLI, ceilingMilli(server, item))) {
                        dear++;
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
                items.put(id, best);
                unitCost.put(id, bestCost);
                int each = KingdomStorage.nutrition(new ItemStack(best));
                if (each > 0) {
                    nutrition.put(id, each);
                }
                needs.add(new RestockPlan.Need(shop.id(), id, category.ordinal(), count));
            }
        }
        if (needs.isEmpty()) {
            LOGGER.debug("The shops of {} bought nothing of the crown on day {}: {} shop(s), {} closing or without keepers, {} item(s) "
                    + "the crown cannot spare, {} too dear to sell on", kingdom, day, shops.size(), idle, unspared, dear);
            return;
        }
        RestockPlan.Result plan = RestockPlan.allocate(needs, free, rules.takeFromCrownPercent(), nutrition, spare);
        for (Long lacking : plan.short_()) {
            data.shop(lacking).ifPresent(shop -> {
                CommerceSavedData.CityTrade trade = trades.get(shop.cityId());
                trade.short_ = true;
                trade.missing = plan.missing();
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
            long unit = unitCost.getOrDefault(take.item(), 1L);
            long cost = Money.multiplyCapped(unit, count);
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
            boolean onCredit = credit.contains(shop.id());
            if (onCredit) {
                // The first goods of a new shop come on credit at the price of the crown.
                shop.setDebt(shop.debt() + cost);
            } else if (!economy.purseToTreasury(till, cost, LedgerCategory.COMMERCE, day).ok()) {
                continue;
            }
            got.put(take.item(), have - count);
            boughtItems += count;
            boughtFor = Money.addCapped(boughtFor, cost);
            shop.addGoods(take.item(), take.category(), count, unit * ShopPricing.MILLI);
            shop.record(day, new Business.Day(0, 0, cost, 0, new long[GoodsCategory.values().length]));
            if (!onCredit) {
                economy.cityTally(kingdom, shop.cityId()).add(CityStatement.Line.CROWN_GOODS_SOLD, cost);
            }
            Item sold = items.get(take.item());
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

    /**
     * Lives as much of the day of the shops of a city as the time allows: the keepers and new shops first, then the spending of
     * the townspeople kind by kind, then the end of the day of every shop.
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
            if (progress.stage == 0) {
                open(server, kingdom, city, day);
                beginSpending(server, kingdom, city, day, progress);
                progress.stage = 1;
            } else if (progress.stage <= kinds) {
                spendKind(server, kingdom, city, day, progress, progress.stage - 1);
                progress.stage++;
            } else if (progress.stage == kinds + 1) {
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
     * close, and new shops open where people want more than the shops sell.
     */
    private static void open(MinecraftServer server, UUID kingdom, long city, long day) {
        CommerceSavedData data = CommerceSavedData.get(server);
        CityEconomyRules rules = CityEconomySettings.rules();
        List<BusinessPlaces.Spot> spots = BusinessPlaces.of(server, kingdom, city);
        Map<String, BusinessPlaces.Spot> byKey = new LinkedHashMap<>();
        for (BusinessPlaces.Spot spot : spots) {
            byKey.put(spot.place().key(), spot);
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
            } else if (shop.status() == Business.Status.FAMILY && !shop.family()) {
                shop.setStatus(Business.Status.TRADING);
            } else if (shop.status() == Business.Status.TRADING && shop.family()) {
                shop.setStatus(Business.Status.FAMILY);
            }
        }
        CommerceSavedData.CityTrade trade = data.city(kingdom, city);
        List<Business> standing = data.shopsOf(kingdom, city);
        int openings = CityDay.openings(!standing.isEmpty(), trade.unspent, rules.fairIncome(), rules.turnoverPerWorker(), trade.short_);
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
            freePlace = true;
            KeeperChoice.Choice choice = choose(server, spot.place(), people, 0, spot.place().kind().room(), taken);
            if (!choice.any()) {
                continue;
            }
            Business shop = data.open(kingdom, city, spot.place(), day,
                    Math.max(rules.minMarginPercent() * 10, Math.min(rules.maxMarginPercent() * 10, START_MARGIN)));
            shop.setKeepers(choice.keepers(), choice.family());
            shop.setStatus(choice.family() ? Business.Status.FAMILY : Business.Status.TRADING);
            for (UUID keeper : choice.keepers()) {
                taken.add(keeper);
                KingdomWorldApi.state(server).citizens().get(keeper).ifPresent(citizen -> citizen.setBusiness(shop.id()));
            }
            used.add(placeKey);
            opened++;
            LOGGER.debug("A shop opened at {} in city {} of {}", placeKey, city, kingdom);
        }
        trade.noPlaces = !freePlace;
        trade.noKeepers = freePlace && opened == 0;
        data.setDirty();
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
        Map<Long, Integer> levels = marketLevels(server, kingdom);
        for (Business shop : data.shopsOf(kingdom, city)) {
            progress.capacity.put(shop.id(), strength(server, shop, levels.getOrDefault(shop.id(), 0), rules));
        }
    }

    private static void spendKind(MinecraftServer server, UUID kingdom, long city, long day, Progress progress, int kind) {
        CommerceSavedData data = CommerceSavedData.get(server);
        Economy economy = KingdomWorldApi.state(server).economy();
        CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom, city);
        long budget = Math.min(progress.budgets[kind], economy.purse(purse));
        if (budget <= 0) {
            return;
        }
        List<CityDay.Offer> offers = new ArrayList<>();
        for (Business shop : data.shopsOf(kingdom, city)) {
            for (Map.Entry<String, Business.Lot> entry : shop.goods().entrySet()) {
                Business.Lot lot = entry.getValue();
                Item item = GoodsStacks.item(entry.getKey());
                if (lot.category() != kind || lot.count() <= 0 || item == null) {
                    continue;
                }
                long price = ShopPricing.askedMilli(lot.costMilli(), shop.marginPermille(), ceilingMilli(server, item));
                offers.add(new CityDay.Offer(shop.id(), entry.getKey(), kind, worth(server, item), price, lot.costMilli(), lot.count()));
            }
        }
        if (offers.isEmpty()) {
            return;
        }
        for (CityDay.Sale sale : CityDay.spendKind(budget, offers, progress.capacity, progress.tax)) {
            Optional<Business> shop = data.shop(sale.shop());
            if (shop.isEmpty()) {
                continue;
            }
            long collected = Evasion.collected(sale.tax(), progress.evasion);
            long toTill = sale.total() + sale.tax() - collected;
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, sale.shop());
            if (!economy.purseMove(purse, till, toTill, CityLedger.OTHER, day).ok()) {
                continue;
            }
            economy.purseMove(purse, CityPurses.PurseKey.cityTreasury(kingdom, city), collected, CityLedger.SALES_TAX, day);
            shop.get().removeGoods(sale.item(), sale.count());
            long[] sold = progress.sold.computeIfAbsent(sale.shop(), k -> new long[2 + GoodsCategory.values().length]);
            sold[0] += toTill;
            sold[1] += sale.cost();
            sold[2 + kind] += (long) Math.floor(sale.count() * sale.worth());
            progress.spent += sale.total() + sale.tax();
            CityStatement.Tally tally = economy.cityTally(kingdom, city);
            tally.add(CityStatement.Line.SALES, sale.total());
            tally.add(CityStatement.Line.BASKET_SPENT, sale.total() + sale.tax());
        }
        data.setDirty();
    }

    /**
     * The end of the day of the shops of a city, a shop at a time: the upkeep leaves the world, the debt to the crown is paid
     * back first, what is beyond the working money is profit for the keepers, the margin moves, and a shop that has fed its keepers
     * too little for too long closes.
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
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
            long[] sold = progress.sold.getOrDefault(shop.id(), new long[2 + GoodsCategory.values().length]);
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
            System.arraycopy(sold, 2, categories, 0, categories.length);
            shop.record(day, new Business.Day(sold[0], sold[1], 0, earned, categories));
            shop.setMarginPermille(ShopPricing.nextMargin(shop.marginPermille(), rules.fairIncome(), earners, sold[1], rules.minMarginPercent(),
                    rules.maxMarginPercent(), rules.marginStepPercent(), rules.businessUpkeepPercent()));
            boolean young = day - shop.opened() < 2;
            boolean poor = !young && CityDay.poor(earned, rules.keeperUsualIncome());
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
     * Closes a shop. The crown buys its goods back into the storehouses at the lower of its price of buying and what the shop
     * paid, the debt of the shop first; a shop whose place stands waits and sells off while the crown cannot pay or has no room.
     * A shop whose place is gone is bought back as far as the crown can pay and store, the rest of its goods are lost. The till
     * goes home with its keeper, else to the townspeople of its city.
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
        if (why == Closing.HANDED_OVER) {
            // The goods are loot of the winner, as far as its storehouses take them; the debt to the old crown is gone with it.
            if (winner != null) {
                for (Map.Entry<String, Business.Lot> entry : shop.goods().entrySet()) {
                    insertAll(server, winner, entry.getKey(), entry.getValue().count(), near);
                }
            }
            shop.clearGoods();
            shop.setDebt(0);
        } else {
            boolean placeStands = why != Closing.PLACE_GONE;
            TradeRules rules = TradeSettings.rules();
            Map<String, Long> counts = shop.goods().isEmpty() ? Map.of() : KingdomStorage.countsById(server, kingdom);
            long value = 0;
            List<ItemStack> stacks = new ArrayList<>();
            Map<String, Long> prices = new HashMap<>();
            for (Map.Entry<String, Business.Lot> entry : shop.goods().entrySet()) {
                Item item = GoodsStacks.item(entry.getKey());
                if (item == null) {
                    continue;
                }
                long buy = Prices.autoBuyPrice(worth(server, item), counts.getOrDefault(entry.getKey(), 0L), rules);
                long price = ShopPricing.buyback(buy, entry.getValue().costMilli());
                prices.put(entry.getKey(), price);
                value = Money.addCapped(value, Money.multiplyCapped(price, entry.getValue().count()));
                long count = entry.getValue().count();
                while (count > 0) {
                    int part = (int) Math.min(count, item.getMaxStackSize());
                    stacks.add(new ItemStack(item, part));
                    count -= part;
                }
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
            for (Map.Entry<String, Business.Lot> entry : new LinkedHashMap<>(shop.goods()).entrySet()) {
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
        data.remove(shop.id());
        LOGGER.info("The shop {} at {} of {} closed: {}", shop.id(), shop.place().key(), kingdom, why);
        return true;
    }

    /**
     * Puts items into the storehouses of a kingdom as far as they have room.
     *
     * @return how many went in
     */
    private static long insertAll(MinecraftServer server, UUID kingdom, String id, long count, WorldPos near) {
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
     * The worth of the goods of all the shops of a kingdom, for the wealth that the exchange rate counts.
     */
    public static long goodsWorth(MinecraftServer server, UUID kingdom) {
        long sum = 0;
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom)) {
            for (Map.Entry<String, Business.Lot> entry : shop.goods().entrySet()) {
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
