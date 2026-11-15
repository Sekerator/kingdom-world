package io.github.sekerator.kingdomworld.core.balance;

import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.CityDay;
import io.github.sekerator.kingdomworld.core.commerce.CityMeans;
import io.github.sekerator.kingdomworld.core.commerce.CraftRules;
import io.github.sekerator.kingdomworld.core.commerce.CrownPurchase;
import io.github.sekerator.kingdomworld.core.commerce.Evasion;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import io.github.sekerator.kingdomworld.core.commerce.OutsideDemand;
import io.github.sekerator.kingdomworld.core.commerce.ShopMaking;
import io.github.sekerator.kingdomworld.core.commerce.ShopPricing;
import io.github.sekerator.kingdomworld.core.economy.CityEconomyRules;
import io.github.sekerator.kingdomworld.core.economy.CityLedger;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.CityTreasury;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.Payroll;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.trade.Prices;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A model of the money of the cities of one kingdom of players over game days, without the world, built of the very parts the
 * game uses: the books of the economy with their purses, tills and treasuries of cities, the day of a city, the prices and the
 * margins of the shops, the work and the prices of workshops, the trading houses and the demand from abroad, the purchases of
 * the crown, evasion, the means of the people and the treasury of a city with its share of the crown. Whole coins only, the
 * fractions carried as the game carries them.
 *
 * <p>Every day it checks that money only moves: what the books hold now less what they held at the start is what came from
 * abroad and from players less what left the world. The goods of a city are counted at the price of export (the lower of the
 * share merchants get abroad and what the crown would pay at its stock), so that a surplus beyond the target of the storehouses
 * counts for less.
 */
public final class CityBudget {
    /** The worth a worker of the crown adds a day, by profession (in the game the ledger of production measures it). */
    public static final Map<String, Integer> MAKES = Map.of("farmer", 45, "lumberjack", 50, "miner", 60, "fisher", 40, "herder", 30, "smelter",
            15, "cook", 15, "crafter", 20, "smith", 25);
    /** The worth of one item of goods. */
    private static final double WORTH = 3.0;
    private static final long WORTH_MILLI = 3_000;
    /** The worth of the raw materials of one item of goods: made goods are worth their raw materials and a tenth. */
    private static final double RAW_WORTH = WORTH / 1.1;
    /**
     * Items a master makes by hand in a working day at the usual pace, as the game measures it: a working day over the ticks of
     * one making; its money for raw materials bounds it more.
     */
    private static final int MASTER_ITEMS = (int) (GatheringRules.WORK_TICKS_PER_DAY
            / io.github.sekerator.kingdomworld.core.production.ProductionRules.DEFAULT.craftTicks());
    /** The profession whose usual wage a master earns, and that of a merchant for a trading house. */
    private static final String CRAFTER = "crafter";
    private static final String MERCHANT = "merchant";

    private CityBudget() {
    }

    /**
     * A city of the example.
     *
     * @param crown     workers of the crown by profession, merchants among them
     * @param keepers   townspeople with a shop that resells
     * @param idle      townspeople without work
     * @param head      whether a head spends its money
     * @param players   what players spend in its shops a day
     * @param houses    houses that keep a share of the wages
     * @param masters   townspeople with a workshop each
     * @param traders   townspeople with a trading house each
     * @param roads     roads to other cities
     * @param market    whether a market works
     * @param crownBuys the worth of goods the crown needs a day that its workshops cannot make (orders without a workshop,
     *                  constructions): it buys them of the masters of the city, and imports what they do not have
     */
    public record Profile(String name, CityLevel level, Map<String, Integer> crown, int keepers, int idle, boolean head, boolean tavern,
                          long players, int houses, int masters, int traders, int roads, boolean market, long crownBuys) {
        public Profile {
            Objects.requireNonNull(name, "name");
            crown = Map.copyOf(crown);
        }

        public Profile(String name, CityLevel level, Map<String, Integer> crown, int keepers, int idle, boolean head, boolean tavern, long players,
                       int houses) {
            this(name, level, crown, keepers, idle, head, tavern, players, houses, 0, 0, 0, false, 0);
        }

        public int people() {
            int sum = keepers + idle + masters + traders;
            for (int count : crown.values()) {
                sum += count;
            }
            return sum;
        }

        int merchants() {
            return crown.getOrDefault("merchant", 0);
        }

        /**
         * The city abandoned: nobody keeps a shop or a workshop, no players, no market and no roads.
         */
        public Profile neglected() {
            return new Profile(name, level, crown, 0, idle + keepers + masters + traders, head, tavern, 0, houses, 0, 0, 0, false, 0);
        }

        public Profile withoutHead() {
            return new Profile(name, level, crown, keepers, idle, false, tavern, players, houses, masters, traders, roads, market, crownBuys);
        }

        /**
         * The same people as in the first stage of the shops: everybody with a shop resells.
         */
        public Profile resellersOnly() {
            return new Profile(name, level, crown, keepers + masters + traders, idle, head, tavern, players, houses, 0, 0, roads, market, crownBuys);
        }
    }

    /**
     * The rules of the model.
     *
     * @param old           today's rules: wages leave the world, no shops, no treasuries of cities
     * @param exportPerMerchant what a merchant of the crown sells abroad a day
     * @param foodPerHead   the worth of what one person eats a day
     * @param goodsKinds    kinds of goods the storehouses keep a target of
     * @param rawTargets    the stock of the storehouses at the start, in targets: the price of the raw materials the masters buy
     *                      falls as it grows
     */
    public record Rules(CityEconomyRules city, EconomyRules economy, TradeRules trade, CraftRules craft, long exportPerMerchant, int foodPerHead,
                        int goodsKinds, long crownStart, boolean old, double rawTargets) {
        public static Rules standard() {
            return new Rules(CityEconomyRules.DEFAULT, EconomyRules.DEFAULT, TradeRules.DEFAULT, CraftRules.DEFAULT, 115, 8, 40, 3_000, false, 1.0);
        }

        public Rules withCity(CityEconomyRules value) {
            return new Rules(value, economy, trade, craft, exportPerMerchant, foodPerHead, goodsKinds, crownStart, old, rawTargets);
        }

        public Rules today() {
            return new Rules(city, economy, trade, craft, exportPerMerchant, foodPerHead, goodsKinds, crownStart, true, rawTargets);
        }

        public Rules withRawTargets(double value) {
            return new Rules(city, economy, trade, craft, exportPerMerchant, foodPerHead, goodsKinds, crownStart, old, value);
        }
    }

    /**
     * What a city comes to, the averages a day over all the days.
     *
     * @param exports      what its trading houses got abroad
     * @param demand       its demand from abroad as the kingdom counts it, worth
     * @param exportedWorth the worth its trading houses exported
     * @param traderIncome what a trading house earned for its keeper, after its first week
     * @param masterIncome what a master earned
     * @param keeperIncome what a keeper of a general store earned
     * @param crownBought  what the crown paid its masters
     * @param imported     what the crown paid abroad for goods it needed and its masters did not have
     * @param tradersClosed trading houses that closed
     * @param masterSales  what its workshops sold, the export included
     */
    public record CityResult(String name, long income, long kept, long toCrown, long spent, long balance, long crownMoney, long crownGoods,
                             long sales, int closed, int shops, long exports, double demand, double exportedWorth, double traderIncome,
                             double masterIncome, double keeperIncome, long crownBought, long imported, int tradersClosed, long masterSales) {
        public long total() {
            return crownMoney + crownGoods;
        }
    }

    /**
     * What the kingdom comes to.
     *
     * @param left       money that left the world in all the days
     * @param fromAbroad money that came from abroad (the merchants of the crown and the trading houses of citizens)
     * @param fromPlayers money players brought into the shops
     * @param massFirst  the money of the currency after the first day
     * @param massLast   after the last
     * @param balanced   whether the money added up on every day
     * @param dailyExpense what the crown spent a day on average
     * @param citizenExports what the trading houses of citizens got abroad in all the days
     * @param crownBoughtMost the most the crown spent on goods of citizens on one day
     * @param keptWages  whether the purchases of the crown never took its treasury below the days of wages it keeps
     */
    public record Result(List<CityResult> cities, long crownStart, long crownEnd, long crownLowest, long left, long fromAbroad, long fromPlayers,
                         long massFirst, long massLast, boolean balanced, long dailyExpense, int evasionPercent, int means, long citizenExports,
                         long crownBoughtMost, boolean keptWages) {
        public long total() {
            long sum = 0;
            for (CityResult city : cities) {
                sum += city.total();
            }
            return sum;
        }

        public long spent() {
            long sum = 0;
            for (CityResult city : cities) {
                sum += city.spent();
            }
            return sum;
        }
    }

    /**
     * The three cities of the example as the first stage of the shops had them: a village without a head, a town and a large city
     * with one, everybody with a shop reselling, the crown needing nothing it cannot make.
     */
    public static List<Profile> example() {
        List<Profile> first = new ArrayList<>();
        for (Profile profile : resellers()) {
            first.add(new Profile(profile.name(), profile.level(), profile.crown(), profile.keepers(), profile.idle(), profile.head(),
                    profile.tavern(), profile.players(), profile.houses()));
        }
        return first;
    }

    /**
     * The people of the cities of the example with workshops, everybody with a shop reselling as in the first stage, the crown
     * importing what it needs and cannot make.
     */
    public static List<Profile> resellers() {
        List<Profile> first = new ArrayList<>();
        for (Profile profile : withCrafts()) {
            first.add(profile.resellersOnly());
        }
        return first;
    }

    /**
     * The three cities of the example with workshops and trading houses: a village of ten with a shop, a town of thirty with a
     * shop, four masters and a trading house, a mayor, a tavern, a market and two roads, and a large city of sixty-four with four
     * shops, ten masters and two trading houses, a baron, a tavern, a market and three roads.
     */
    public static List<Profile> withCrafts() {
        Map<String, Integer> village = new LinkedHashMap<>();
        village.put("farmer", 4);
        village.put("lumberjack", 2);
        village.put("fisher", 1);
        village.put("herder", 1);
        Map<String, Integer> town = new LinkedHashMap<>();
        town.put("farmer", 6);
        town.put("miner", 2);
        town.put("lumberjack", 2);
        town.put("fisher", 1);
        town.put("herder", 1);
        town.put("smelter", 1);
        town.put("cook", 2);
        town.put("crafter", 2);
        town.put("smith", 1);
        town.put("carrier", 2);
        town.put("merchant", 1);
        town.put("innkeeper", 1);
        town.put("builder", 1);
        Map<String, Integer> city = new LinkedHashMap<>();
        city.put("farmer", 10);
        city.put("fisher", 4);
        city.put("herder", 2);
        city.put("miner", 4);
        city.put("lumberjack", 3);
        city.put("smelter", 2);
        city.put("cook", 3);
        city.put("crafter", 3);
        city.put("smith", 2);
        city.put("carrier", 3);
        city.put("merchant", 3);
        city.put("innkeeper", 1);
        city.put("builder", 3);
        return List.of(new Profile("Olkhovka", CityLevel.VILLAGE, village, 1, 1, false, false, 0, 4, 0, 0, 1, false, 0),
                new Profile("Kamenka", CityLevel.TOWN, town, 1, 1, true, true, 15, 10, 4, 1, 2, true, 10),
                new Profile("Belograd", CityLevel.CITY, city, 4, 5, true, true, 60, 20, 10, 2, 3, true, 30));
    }

    // ---------------------------------------------------------------- the model

    private enum Kind {
        STORE,
        MASTER,
        TRADER
    }

    private static final class City {
        final Profile profile;
        final long id;
        final List<Business> shops = new ArrayList<>();
        final Map<Long, Kind> kinds = new HashMap<>();
        /** What the trading house of the city asked of each master, worth. */
        final Map<Long, Long> orders = new HashMap<>();
        final Map<UUID, String> citizens = new LinkedHashMap<>();
        long houses;
        long housesCap;
        long[] carry = new long[GoodsCategory.values().length];
        int closed;
        int tradersClosed;
        long sales;
        long spent;
        long income;
        long kept;
        long toCrown;
        long crownMoney;
        long crownGoods;
        long exports;
        double exportedWorth;
        double traderEarned;
        double masterEarned;
        double keeperEarned;
        long traderDays;
        long masterDays;
        long keeperDays;
        long crownBought;
        long imported;
        /** The worth of what the crown bought of the masters today, for its storehouses. */
        double boughtWorthToday;
        /** The worth of the raw materials the masters bought of the crown today. */
        double rawWorthToday;
        long masterSales;
        double demand;

        City(Profile profile, long id) {
            this.profile = profile;
            this.id = id;
        }

        Kind kind(Business shop) {
            return kinds.getOrDefault(shop.id(), Kind.STORE);
        }
    }

    private static String goods(int kind) {
        return "goods" + kind;
    }

    /**
     * Runs the cities of a kingdom for some days.
     */
    public static Result run(List<Profile> profiles, Rules rules, int days) {
        Economy economy = new Economy();
        UUID kingdom = new UUID(7, 7);
        economy.open(kingdom, 0);
        economy.adminTreasury(kingdom, rules.crownStart(), 0);
        CityEconomyRules city = rules.city();
        List<City> cities = new ArrayList<>();
        long nextShop = 1;
        for (int i = 0; i < profiles.size(); i++) {
            City model = new City(profiles.get(i), i);
            Profile profile = model.profile;
            profile.crown().forEach((profession, count) -> {
                for (int n = 0; n < count; n++) {
                    model.citizens.put(UUID.randomUUID(), profession);
                }
            });
            for (int n = 0; n < profile.keepers() + profile.idle() + profile.masters() + profile.traders(); n++) {
                model.citizens.put(UUID.randomUUID(), "unemployed");
            }
            model.housesCap = Money.multiplyCapped(profile.houses(), 300);
            if (!rules.old()) {
                for (int n = 0; n < profile.keepers(); n++) {
                    model.shops.add(shop(nextShop++, kingdom, model, Business.GENERAL_STORE, Kind.STORE));
                }
                for (int n = 0; n < profile.masters(); n++) {
                    Business master = shop(nextShop++, kingdom, model, "bakery", Kind.MASTER);
                    model.shops.add(master);
                }
                for (int n = 0; n < profile.traders(); n++) {
                    model.shops.add(shop(nextShop++, kingdom, model, "trader", Kind.TRADER));
                }
            }
            cities.add(model);
        }
        // The demand from abroad of every city, one market abroad for the kingdom.
        double[] own = new double[cities.size()];
        for (int i = 0; i < cities.size(); i++) {
            Profile profile = cities.get(i).profile;
            own[i] = OutsideDemand.of(profile.level(), profile.people(), profile.roads(), profile.market(), false, rules.craft());
        }
        double[] weighted = OutsideDemand.weighted(own, rules.craft().outsideDemandFullCities());
        for (int i = 0; i < cities.size(); i++) {
            cities.get(i).demand = weighted[i];
        }
        int burden = city.salesTaxPercent() * 10 + city.tithePercent() * 10;
        int evasion = Evasion.permille(burden, city.evasionStartPercent(), city.evasionMaxPercent());
        CityMeans means = CityMeans.of(burden, 0, false, false, city.crownSharePercent() * 10, city.crownShareFairPercent());
        double speed = Payroll.workSpeed(50 + means.value(), rules.economy().moodWorkEffect());
        long held0 = held(economy, kingdom, cities);
        long fromAbroad = 0;
        long fromPlayers = 0;
        long citizenExports = 0;
        long left = 0;
        long lowest = rules.crownStart();
        long massFirst = 0;
        boolean balanced = true;
        boolean keptWages = true;
        long boughtMost = 0;
        long stockItems = Math.round((double) rules.goodsKinds() * rules.trade().stockTarget() * rules.rawTargets());
        long crownExpense = 0;
        int tax = city.salesTaxPercent() * 10;
        int tithe = city.tithePercent() * 10;
        int share = city.crownSharePercent() * 10;
        long laborMilli = Money.share(Money.multiplyCapped(rules.economy().defaultWage(CRAFTER), 1_000), 200, GatheringRules.WORK_TICKS_PER_DAY);
        for (int day = 1; day <= days; day++) {
            // The raw materials of the masters cost what the crown asks at its stock today.
            long rawMilli = Math.round(RAW_WORTH * Prices.demandFactor(stockItems / Math.max(1, rules.goodsKinds()), rules.trade().stockTarget(),
                    rules.trade()) * ShopPricing.MILLI);
            // 1. The payday: wages into the purses of the cities, or out of the world under today's rules.
            List<Economy.WageClaim> claims = new ArrayList<>();
            Map<UUID, Long> cityOf = new HashMap<>();
            for (City model : cities) {
                model.citizens.forEach((id, profession) -> {
                    claims.add(new Economy.WageClaim(id, profession));
                    cityOf.put(id, model.id);
                });
            }
            Economy.Payday payday = economy.payday(kingdom, day, List.of(), claims, rules.economy(), id -> rules.old() ? -1 : cityOf.getOrDefault(id, 0L));
            if (rules.old()) {
                left += payday.summary().paid();
            }
            for (City model : cities) {
                long wages = 0;
                for (UUID id : model.citizens.keySet()) {
                    wages += payday.pay().getOrDefault(id, 0L);
                }
                model.crownMoney -= wages;
                // A share of every wage goes home, as far as the houses have room.
                long home = Math.max(0, Math.min(Money.share(wages, 20, 100), model.housesCap - model.houses));
                if (rules.old()) {
                    model.houses += home;
                    left -= home;
                } else {
                    model.houses += economy.purseHold(CityPurses.PurseKey.cityPurse(kingdom, model.id), home);
                }
            }
            long keep = Money.multiplyCapped(rules.craft().crownBuyKeepWageDays(), economy.treasury(kingdom).orElseThrow().lastPayroll().due());
            long boughtToday = 0;
            for (City model : cities) {
                if (!rules.old()) {
                    payShops(economy, kingdom, model, tithe, evasion, day);
                    make(economy, kingdom, model, rules, day, speed, rawMilli, laborMilli);
                    long[] exported = export(economy, kingdom, model, rules, day, speed);
                    fromAbroad += exported[0];
                    citizenExports += exported[0];
                    dayOfCity(economy, kingdom, model, rules, day, tax, evasion, speed);
                }
                // The merchants of the crown sell abroad.
                long ex = Money.multiplyCapped(model.profile.merchants(), rules.exportPerMerchant());
                if (ex > 0) {
                    economy.exportIncome(kingdom, ex, day);
                    fromAbroad += ex;
                    model.crownMoney += ex;
                }
            }
            // The players who spent in the shops brought new money; the crown buys of the masters what its workshops lack.
            for (City model : cities) {
                long paid = model.profile.players() > 0 && !rules.old() ? playersBuy(economy, kingdom, model, model.profile.players(), tax, evasion, day)
                        : 0;
                fromPlayers += paid;
                if (!rules.old() && model.profile.crownBuys() > 0) {
                    // What the crown needs: of its masters within its money, the rest from abroad at the price of import.
                    long funds = economy.treasury(kingdom).orElseThrow().funds();
                    long budget = CrownPurchase.budget(funds, keep, rules.craft().crownBuyPerDay(), boughtToday);
                    long needItems = (long) Math.ceil(model.profile.crownBuys() / WORTH);
                    double before = model.boughtWorthToday;
                    long bought = crownBuys(economy, kingdom, model, rules, day, budget, needItems);
                    boughtToday += bought;
                    if (bought > 0 && economy.treasury(kingdom).orElseThrow().funds() < keep) {
                        keptWages = false;
                    }
                    long gotItems = Math.round((model.boughtWorthToday - before) / WORTH);
                    long importItems = needItems - gotItems;
                    long cost = Prices.importCost(WORTH, importItems, rules.trade());
                    if (importItems > 0 && economy.importPayment(kingdom, cost, day).ok()) {
                        left += cost;
                        model.crownMoney -= cost;
                        model.imported += cost;
                        model.boughtWorthToday += importItems * WORTH;
                    }
                }
            }
            boughtMost = Math.max(boughtMost, boughtToday);
            for (City model : cities) {
                if (!rules.old()) {
                    endOfDay(economy, kingdom, model, rules, day, share, rawMilli, laborMilli);
                }
            }
            // Goods of the crown: made by its workers, eaten by the people, sold to the shops and the masters, exported, bought of the
            // masters.
            for (City model : cities) {
                long makes = 0;
                for (Map.Entry<String, Integer> entry : model.profile.crown().entrySet()) {
                    makes += (long) MAKES.getOrDefault(entry.getKey(), 0) * entry.getValue();
                }
                long madeHere = Math.round(makes * (rules.old() ? 1.0 : speed));
                long eaten = (long) model.profile.people() * rules.foodPerHead();
                // The raw materials of the masters leave the storehouses at their worth.
                long sold = Math.round(model.rawWorthToday);
                model.rawWorthToday = 0;
                for (Business shop : model.shops) {
                    if (model.kind(shop) == Kind.STORE) {
                        sold += shop.day(day).map(Business.Day::bought).orElse(0L);
                    }
                }
                long exported = (long) Math.ceil(Money.multiplyCapped(model.profile.merchants(), rules.exportPerMerchant()) / rules.trade().exportFactor());
                long bought = Math.round(model.boughtWorthToday);
                model.boughtWorthToday = 0;
                long net = madeHere - eaten - sold - exported + bought;
                double factor = exportFactor(rules, stockItems);
                model.crownGoods += Math.round(net * factor);
                stockItems = Math.max(0, stockItems + Math.round(net / WORTH));
            }
            for (City model : cities) {
                if (!rules.old()) {
                    CityStatement statement = economy.sealCity(kingdom, model.id, day);
                    model.income += statement.taxIncome();
                    model.kept += statement.get(CityStatement.Line.KEPT);
                    model.toCrown += statement.get(CityStatement.Line.TO_CROWN);
                    model.spent += statement.get(CityStatement.Line.SPENT);
                    model.crownMoney += statement.get(CityStatement.Line.CROWN_SHARE) + statement.get(CityStatement.Line.CROWN_GOODS_SOLD)
                            + statement.get(CityStatement.Line.CROWN_TAVERNS) - statement.get(CityStatement.Line.CROWN_GRANTS);
                    for (CityPurses.Outflow outflow : CityPurses.Outflow.values()) {
                        left += economy.purses().outflows(kingdom, model.id, day).getOrDefault(outflow, 0L);
                    }
                }
            }
            long funds = economy.treasury(kingdom).orElseThrow().funds();
            lowest = Math.min(lowest, funds);
            crownExpense += economy.treasury(kingdom).orElseThrow().journal()
                    .sum(day, io.github.sekerator.kingdomworld.core.economy.TreasuryJournal.Period.TODAY).expense();
            long drift = (held(economy, kingdom, cities) - held0) - (fromAbroad + fromPlayers - left);
            if (drift != 0) {
                balanced = false;
            }
            if (day == 1) {
                massFirst = economy.moneyMass(kingdom);
            }
        }
        List<CityResult> results = new ArrayList<>();
        for (City model : cities) {
            int shops = 0;
            for (Business shop : model.shops) {
                shops++;
            }
            results.add(new CityResult(model.profile.name(), model.income / days, model.kept / days, model.toCrown / days, model.spent / days,
                    economy.purse(CityPurses.PurseKey.cityTreasury(kingdom, model.id)), model.crownMoney / days, model.crownGoods / days,
                    model.sales / days, model.closed, shops, model.exports / days, model.demand, model.exportedWorth / days,
                    model.traderDays == 0 ? 0 : model.traderEarned / model.traderDays, model.masterDays == 0 ? 0 : model.masterEarned / model.masterDays,
                    model.keeperDays == 0 ? 0 : model.keeperEarned / model.keeperDays, model.crownBought / days, model.imported / days, model.tradersClosed,
                    model.masterSales / days));
        }
        return new Result(results, rules.crownStart(), economy.treasury(kingdom).orElseThrow().funds(), lowest, left, fromAbroad, fromPlayers,
                massFirst, economy.moneyMass(kingdom), balanced, crownExpense / days, Evasion.percent(burden, city.evasionStartPercent(),
                city.evasionMaxPercent()), means.value(), citizenExports, boughtMost, keptWages);
    }

    private static Business shop(long id, UUID kingdom, City model, String trade, Kind kind) {
        Business shop = new Business(id, kingdom, model.id, new Business.Place(Business.PlaceKind.MERCHANT_HOUSE, id, 0, "w", 0, 0, 0), 0, 200);
        shop.setTrade(trade);
        model.kinds.put(id, kind);
        return shop;
    }

    /**
     * All the money of the model: the treasury of the kingdom, every purse, till and treasury of a city, and the houses.
     */
    private static long held(Economy economy, UUID kingdom, List<City> cities) {
        long sum = economy.supply(kingdom);
        for (City model : cities) {
            sum += model.houses;
        }
        return sum;
    }

    /**
     * The price of export of a worth of goods, for a stock of the storehouses: a surplus beyond the target counts for less.
     */
    static double exportFactor(Rules rules, long stockItems) {
        long perKind = stockItems / Math.max(1, rules.goodsKinds());
        double home = Prices.autoBuyPrice(WORTH, perKind, rules.trade()) / WORTH;
        return Math.min(rules.trade().exportFactor(), home);
    }

    private static void payShops(Economy economy, UUID kingdom, City model, int tithe, int evasion, long day) {
        for (Business shop : model.shops) {
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
            long profit = Math.min(shop.profitDue(), economy.purse(till));
            shop.setProfitDue(0);
            if (profit <= 0) {
                continue;
            }
            long collected = Evasion.collected(Money.share(profit, tithe, 1000), evasion);
            economy.purseMove(till, CityPurses.PurseKey.cityTreasury(kingdom, model.id), collected, CityLedger.TITHE, day);
            long rest = profit - collected;
            long home = Math.max(0, Math.min(Money.share(rest, 20, 100), model.housesCap - model.houses));
            model.houses += economy.purseHold(till, home);
            economy.purseMove(till, CityPurses.PurseKey.cityPurse(kingdom, model.id), economy.purse(till) >= rest - home ? rest - home : 0,
                    CityLedger.OTHER, day);
        }
    }

    private static double strength(City model, CityEconomyRules city, double speed) {
        double strength = city.turnoverPerWorker() * speed;
        if (model.profile.level() == CityLevel.CITY) {
            strength *= 1.25;
        }
        return strength;
    }

    /**
     * The masters of a city buy the raw materials of what they lack of their target and make it, a day of work at most, paying the
     * crown out of their tills (a new workshop on credit).
     */
    private static void make(Economy economy, UUID kingdom, City model, Rules rules, long day, double speed, long rawMilli, long laborMilli) {
        CityEconomyRules city = rules.city();
        int index = 0;
        for (Business shop : model.shops) {
            if (model.kind(shop) != Kind.MASTER) {
                continue;
            }
            int kind = index++ % 5;
            boolean fresh = shop.startingOut();
            long budget = fresh ? city.shopCredit() : Math.max(0, economy.purse(CityPurses.PurseKey.till(kingdom, shop.id())) - shop.profitDue());
            GoodsCategory category = GoodsCategory.values()[kind];
            long target = ShopMaking.target(shop.averageCategory(day, kind), strength(model, city, speed),
                    ShopMaking.sharePercent(category, city.basket()), city.stockDays(), model.orders.getOrDefault(shop.id(), 0L));
            long held = Math.round(shop.count(Business.key(goods(kind), true)) * WORTH);
            long lacking = (long) Math.ceil(Math.max(0, target - held) / WORTH);
            long count = Math.min(lacking, (long) Math.floor(MASTER_ITEMS * speed));
            count = Math.min(count, Money.share(budget, ShopPricing.MILLI, Math.max(1, rawMilli)));
            if (count <= 0) {
                continue;
            }
            long paid = ShopPricing.total(rawMilli, count);
            if (fresh) {
                shop.setDebt(shop.debt() + paid);
            } else if (!economy.purseToTreasury(CityPurses.PurseKey.till(kingdom, shop.id()), paid, LedgerCategory.COMMERCE, day).ok()) {
                continue;
            } else {
                economy.cityTally(kingdom, model.id).add(CityStatement.Line.CROWN_GOODS_SOLD, paid);
            }
            shop.addMade(goods(kind), kind, count, rawMilli, laborMilli);
            model.rawWorthToday += count * RAW_WORTH;
            shop.record(day, new Business.Day(0, 0, paid, 0, null, Math.round(count * WORTH), 0, 0));
        }
    }

    /**
     * The trading houses of a city buy what its masters made beyond their days of stock, from masters whose least price is within
     * their share of the export, and export it within the demand from abroad of the city; what they could not take is asked of
     * the masters for the next day.
     *
     * @return { money from abroad }
     */
    private static long[] export(Economy economy, UUID kingdom, City model, Rules rules, long day, double speed) {
        CraftRules craft = rules.craft();
        List<Business> houses = new ArrayList<>();
        List<Business> masters = new ArrayList<>();
        for (Business shop : model.shops) {
            if (model.kind(shop) == Kind.TRADER) {
                houses.add(shop);
            } else if (model.kind(shop) == Kind.MASTER) {
                masters.add(shop);
            }
        }
        model.orders.clear();
        if (houses.isEmpty() || model.demand <= 0) {
            return new long[1];
        }
        long exportMilli = (long) Math.floor(WORTH * craft.craftExportPercent(model.profile.level()) * 10.0);
        long ceiling = ShopPricing.ceilingMilli(WORTH, Prices.importCost(WORTH, 1, rules.trade()));
        record Offer(Business master, String key, long count, long priceMilli) {
        }
        List<Offer> offers = new ArrayList<>();
        List<Business> passing = new ArrayList<>();
        for (Business master : masters) {
            for (Map.Entry<String, Business.Lot> entry : master.goods().entrySet()) {
                Business.Lot lot = entry.getValue();
                long price = ShopMaking.wholesaleMilli(lot, master.marginPermille(), ceiling, exportMilli, craft.traderBuyPercent());
                if (price < 0) {
                    continue;
                }
                passing.add(master);
                // The master keeps what its own city buys of it in its days of stock, or its strength by the share of the kind.
                long keepWorth = CityDay.stockTarget(master.averageCategory(day, lot.category()), strength(model, rules.city(), speed),
                        ShopMaking.sharePercent(GoodsCategory.values()[lot.category()], rules.city().basket()), rules.city().stockDays());
                long spare = lot.count() - (long) Math.ceil(keepWorth / WORTH);
                if (spare > 0) {
                    offers.add(new Offer(master, entry.getKey(), spare, price));
                }
            }
            if (master.goods().isEmpty()) {
                passing.add(master);
            }
        }
        offers.sort(Comparator.comparingLong(Offer::priceMilli).thenComparingLong(offer -> offer.master().id()));
        double left = model.demand;
        long income = 0;
        int next = 0;
        long[] counts = new long[offers.size()];
        for (int i = 0; i < offers.size(); i++) {
            counts[i] = offers.get(i).count();
        }
        // The houses share what the masters have to spare, each as far as its strength goes.
        double spareWorth = 0;
        for (Offer offer : offers) {
            spareWorth += offer.count() * WORTH;
        }
        double each = Math.max(WORTH, Math.min(spareWorth, model.demand) / houses.size());
        for (Business house : houses) {
            double room = Math.min(strength(model, rules.city(), speed), each);
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, house.id());
            long houseIncome = 0;
            long housePaid = 0;
            for (int i = next; i < offers.size() && left > 0 && room > 0; i++) {
                Offer offer = offers.get(i);
                long count = Math.min(counts[i], (long) Math.floor(Math.min(left, room) / WORTH));
                if (count <= 0) {
                    continue;
                }
                Business.Lot lot = offer.master().goods().get(offer.key());
                long money = Money.share(Money.multiplyCapped(exportMilli, count), 1, ShopPricing.MILLI);
                long paid = ShopPricing.total(offer.priceMilli(), count);
                if (lot == null || paid > money || !economy.purseFromAbroad(till, money, day).ok()) {
                    continue;
                }
                economy.purseMove(till, CityPurses.PurseKey.till(kingdom, offer.master().id()), paid, CityLedger.OTHER, day);
                offer.master().removeGoods(offer.key(), count);
                counts[i] -= count;
                offer.master().record(day, new Business.Day(paid, ShopPricing.total(lot.costMilli(), count), 0, 0, null));
                model.masterSales += paid;
                houseIncome += money;
                housePaid += paid;
                left -= count * WORTH;
                room -= count * WORTH;
                model.exportedWorth += count * WORTH;
            }
            if (houseIncome > 0) {
                house.record(day, new Business.Day(houseIncome, housePaid, 0, 0, null, 0, houseIncome, 0));
                economy.cityTally(kingdom, model.id).add(CityStatement.Line.CITIZEN_EXPORTS, houseIncome);
                income += houseIncome;
            }
        }
        model.exports += income;
        long unchosen = (long) Math.floor(Math.max(0.0, left));
        List<Business> asked = passing.stream().distinct().toList();
        for (Business master : asked) {
            model.orders.put(master.id(), unchosen / Math.max(1, asked.size()));
        }
        return new long[]{income};
    }

    private static void dayOfCity(Economy economy, UUID kingdom, City model, Rules rules, long day, int tax, int evasion, double speed) {
        CityEconomyRules city = rules.city();
        CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom, model.id);
        // The taverns of the crown: half the people spend an evening there.
        if (model.profile.tavern()) {
            long evenings = Math.min(economy.purse(purse), Money.multiplyCapped(model.profile.people() / 2, city.tavernEveningPrice()));
            if (evenings > 0 && economy.purseToTreasury(purse, evenings, LedgerCategory.LIFE, day).ok()) {
                economy.cityTally(kingdom, model.id).add(CityStatement.Line.CROWN_TAVERNS, evenings);
            }
        }
        // The shops buy of the crown: three days of their sales, a new one its strength by the basket on credit.
        long ceiling = ShopPricing.ceilingMilli(WORTH, Prices.importCost(WORTH, 1, rules.trade()));
        long cost = (long) WORTH;
        for (Business shop : model.shops) {
            if (model.kind(shop) != Kind.STORE) {
                continue;
            }
            boolean fresh = shop.startingOut();
            long budget = fresh ? city.shopCredit() : Math.max(0, economy.purse(CityPurses.PurseKey.till(kingdom, shop.id())) - shop.profitDue());
            for (GoodsCategory kind : GoodsCategory.values()) {
                if (!kind.basket()) {
                    continue;
                }
                long average = shop.averageCategory(day, kind.ordinal());
                long target = CityDay.stockTarget(average, strength(model, city, speed), city.basketShare(kind.ordinal()), city.stockDays());
                long lacking = target - shop.goodsValue(kind.ordinal());
                long count = Math.min((long) Math.ceil(Math.max(0, lacking) / WORTH), budget / cost);
                if (count <= 0) {
                    continue;
                }
                long paid = count * cost;
                if (fresh) {
                    shop.setDebt(shop.debt() + paid);
                } else if (!economy.purseToTreasury(CityPurses.PurseKey.till(kingdom, shop.id()), paid, LedgerCategory.COMMERCE, day).ok()) {
                    continue;
                } else {
                    economy.cityTally(kingdom, model.id).add(CityStatement.Line.CROWN_GOODS_SOLD, paid);
                }
                budget -= paid;
                shop.addGoods(goods(kind.ordinal()), kind.ordinal(), count, cost * ShopPricing.MILLI);
                shop.record(day, new Business.Day(0, 0, paid, 0, new long[GoodsCategory.values().length]));
            }
        }
        // The townspeople spend their purse.
        long balance = economy.purse(purse);
        long afar = CityDay.afar(balance, city.afarPercent());
        economy.purseLeaves(purse, model.id, afar, CityPurses.Outflow.AFAR, day);
        CityDay.Budgets budgets = CityDay.budgets(balance, model.carry, city.basket(), model.profile.level() != CityLevel.VILLAGE);
        model.carry = budgets.carry();
        Map<Long, Double> capacity = new HashMap<>();
        for (Business shop : model.shops) {
            if (model.kind(shop) != Kind.TRADER) {
                capacity.put(shop.id(), strength(model, city, speed));
            }
        }
        for (GoodsCategory kind : GoodsCategory.values()) {
            List<CityDay.Offer> offers = new ArrayList<>();
            for (Business shop : model.shops) {
                for (boolean made : new boolean[]{false, true}) {
                    String key = Business.key(goods(kind.ordinal()), made);
                    Business.Lot lot = shop.goods().get(key);
                    if (lot != null && lot.count() > 0) {
                        offers.add(new CityDay.Offer(shop.id(), key, kind.ordinal(), WORTH, ShopMaking.askedMilli(lot, shop.marginPermille(), ceiling, WORTH_MILLI),
                                lot.costMilli(), lot.count()));
                    }
                }
            }
            for (CityDay.Sale sale : CityDay.spendKind(Math.min(budgets.budgets()[kind.ordinal()], economy.purse(purse)), offers, capacity, tax)) {
                long collected = Evasion.collected(sale.tax(), evasion);
                long toTill = sale.total() + sale.tax() - collected;
                economy.purseMove(purse, CityPurses.PurseKey.till(kingdom, sale.shop()), toTill, CityLedger.OTHER, day);
                economy.purseMove(purse, CityPurses.PurseKey.cityTreasury(kingdom, model.id), collected, CityLedger.SALES_TAX, day);
                for (Business shop : model.shops) {
                    if (shop.id() == sale.shop()) {
                        shop.removeGoods(sale.item(), sale.count());
                        long[] categories = new long[GoodsCategory.values().length];
                        categories[kind.ordinal()] = Math.round(sale.count() * WORTH);
                        shop.record(day, new Business.Day(toTill, sale.cost(), 0, 0, categories));
                        if (model.kind(shop) == Kind.MASTER) {
                            model.masterSales += toTill;
                        }
                    }
                }
                model.sales += sale.total();
            }
        }
    }

    /**
     * Players buy in the shops of a city with money they bring.
     *
     * @return what they paid
     */
    private static long playersBuy(Economy economy, UUID kingdom, City model, long money, int tax, int evasion, long day) {
        long paid = 0;
        long left = money;
        for (Business shop : model.shops) {
            for (Map.Entry<String, Business.Lot> entry : new LinkedHashMap<>(shop.goods()).entrySet()) {
                if (left <= 0) {
                    break;
                }
                Business.Lot lot = entry.getValue();
                long price = ShopMaking.askedMilli(lot, shop.marginPermille(), Long.MAX_VALUE, WORTH_MILLI);
                long count = Math.min(lot.count(), Money.share(left, ShopPricing.MILLI, ShopPricing.withTaxMilli(price, tax)));
                long total = ShopPricing.total(price, count);
                long taxed = ShopPricing.tax(total, tax);
                while (count > 0 && total + taxed > left) {
                    count--;
                    total = ShopPricing.total(price, count);
                    taxed = ShopPricing.tax(total, tax);
                }
                if (count <= 0) {
                    continue;
                }
                long collected = Evasion.collected(taxed, evasion);
                // Players pay with coins: money comes into the books.
                economy.coinsIssued(kingdom, total + taxed);
                economy.playerPurchase(null, CityPurses.PurseKey.till(kingdom, shop.id()), CityPurses.PurseKey.cityTreasury(kingdom, model.id),
                        total + taxed - collected, collected, day);
                economy.coinsIssued(kingdom, -(total + taxed));
                shop.removeGoods(entry.getKey(), count);
                long[] categories = new long[GoodsCategory.values().length];
                categories[lot.category()] = Math.round(count * WORTH);
                shop.record(day, new Business.Day(total + taxed - collected, ShopPricing.total(lot.costMilli(), count), 0, 0, categories));
                if (model.kind(shop) == Kind.MASTER) {
                    model.masterSales += total + taxed - collected;
                }
                model.sales += total;
                paid += total + taxed;
                left -= total + taxed;
            }
        }
        return paid;
    }

    /**
     * The crown buys goods the masters of a city made, the cheapest first, at their price without the tax and within its money.
     *
     * @return what it paid
     */
    private static long crownBuys(Economy economy, UUID kingdom, City model, Rules rules, long day, long budget, long need) {
        if (budget <= 0) {
            return 0;
        }
        long ceiling = ShopPricing.ceilingMilli(WORTH, Prices.importCost(WORTH, 1, rules.trade()));
        List<CrownPurchase.Offer> offers = new ArrayList<>();
        for (Business shop : model.shops) {
            for (Map.Entry<String, Business.Lot> entry : shop.goods().entrySet()) {
                if (entry.getValue().made()) {
                    offers.add(new CrownPurchase.Offer(shop.id(), entry.getKey(), entry.getValue().count(),
                            ShopMaking.askedMilli(entry.getValue(), shop.marginPermille(), ceiling, WORTH_MILLI), WORTH));
                }
            }
        }
        long paid = 0;
        for (CrownPurchase.Take take : CrownPurchase.choose(offers, need, CrownPurchase.capMilli(WORTH,
                rules.craft().crownBuyCapPercent()), budget)) {
            for (Business shop : model.shops) {
                if (shop.id() != take.shop()) {
                    continue;
                }
                Business.Lot lot = shop.goods().get(take.key());
                if (lot == null || !economy.treasuryToPurse(CityPurses.PurseKey.till(kingdom, shop.id()), take.total(), LedgerCategory.COMMERCE,
                        day).ok()) {
                    continue;
                }
                shop.removeGoods(take.key(), take.count());
                long[] categories = new long[GoodsCategory.values().length];
                categories[lot.category()] = Math.round(take.count() * WORTH);
                shop.record(day, new Business.Day(take.total(), ShopPricing.total(lot.costMilli(), take.count()), 0, 0, categories, 0, 0,
                        take.total()));
                economy.cityTally(kingdom, model.id).add(CityStatement.Line.CROWN_BOUGHT, take.total());
                model.crownMoney -= take.total();
                model.crownBought += take.total();
                model.boughtWorthToday += take.count() * WORTH;
                model.masterSales += take.total();
                paid += take.total();
            }
        }
        return paid;
    }

    private static void endOfDay(Economy economy, UUID kingdom, City model, Rules rules, long day, int share, long rawMilli, long laborMilli) {
        CityEconomyRules city = rules.city();
        // The shops: upkeep, the debt first, profit, margin, and closing.
        for (Business shop : new ArrayList<>(model.shops)) {
            Kind kind = model.kind(shop);
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
            Business.Day today = shop.day(day).orElse(new Business.Day(0, 0, 0, 0, null));
            long working = Money.multiplyCapped(2, Math.max(shop.averageBought(day), today.bought()));
            CityDay.Result result = CityDay.result(economy.purse(till), today.sales(), shop.debt(), working, shop.profitDue(),
                    city.businessUpkeepPercent());
            economy.purseLeaves(till, model.id, result.upkeep(), CityPurses.Outflow.UPKEEP, day);
            if (result.debtPaid() > 0 && economy.purseToTreasury(till, result.debtPaid(), LedgerCategory.COMMERCE, day).ok()) {
                shop.setDebt(shop.debt() - result.debtPaid());
                economy.cityTally(kingdom, model.id).add(CityStatement.Line.CROWN_GOODS_SOLD, result.debtPaid());
            }
            shop.setProfitDue(shop.profitDue() + result.profit());
            long usual = kind == Kind.MASTER ? rules.economy().defaultWage(CRAFTER) : kind == Kind.TRADER ? rules.economy().defaultWage(MERCHANT)
                    : city.keeperUsualIncome();
            long earned = CityDay.earned(today.sales(), today.soldCost(), result.upkeep());
            if (kind == Kind.MASTER) {
                // The work in the goods sold pays the master part of its fair income; the margin aims at the rest.
                long items = Money.share(today.soldCost(), ShopPricing.MILLI, Math.max(1, rawMilli));
                long labor = ShopPricing.total(laborMilli, items);
                long fair = Money.share(usual, city.fairIncomePercent(), 100);
                shop.setMarginPermille(ShopPricing.nextMargin(shop.marginPermille(), Math.max(0, fair - labor), 1, today.soldCost() + labor,
                        city.minMarginPercent(), city.maxMarginPercent(), city.marginStepPercent(), city.businessUpkeepPercent()));
                model.masterEarned += earned;
                model.masterDays++;
            } else if (kind == Kind.TRADER) {
                // A trading house is measured once it settled, after its first week.
                if (day > 7) {
                    model.traderEarned += earned;
                    model.traderDays++;
                }
            } else {
                shop.setMarginPermille(ShopPricing.nextMargin(shop.marginPermille(), city.fairIncome(), 1, today.soldCost(), city.minMarginPercent(),
                        city.maxMarginPercent(), city.marginStepPercent(), city.businessUpkeepPercent()));
                model.keeperEarned += earned;
                model.keeperDays++;
            }
            boolean poor = day - shop.opened() >= 2 && CityDay.poor(earned, usual);
            shop.setPoorDays(poor ? shop.poorDays() + 1 : 0);
            if (shop.poorDays() >= city.closeAfterDays()) {
                // The crown buys the goods back at what the shop paid, the debt first; the till goes to the townspeople.
                long value = 0;
                for (Business.Lot lot : shop.goods().values()) {
                    value += ShopPricing.buyback((long) Math.floor(WORTH * 0.7), lot.costMilli()) * lot.count();
                }
                long settle = value - shop.debt();
                if (settle > 0) {
                    economy.treasuryToPurse(till, settle, LedgerCategory.COMMERCE, day);
                } else if (settle < 0) {
                    economy.purseToTreasury(till, Math.min(-settle, economy.purse(till)), LedgerCategory.COMMERCE, day);
                }
                economy.purseMove(till, CityPurses.PurseKey.cityPurse(kingdom, model.id), economy.purse(till), CityLedger.OTHER, day);
                model.shops.remove(shop);
                model.closed++;
                if (kind == Kind.TRADER) {
                    model.tradersClosed++;
                }
            }
        }
        // The treasury of the city: its share to the crown, a head spends, what it keeps beyond its reserve goes too.
        CityTreasury treasury = economy.openCityTreasury(kingdom, model.id);
        CityStatement.Tally tally = treasury.tally();
        long taxToday = tally.get(CityStatement.Line.TITHE) + tally.get(CityStatement.Line.SALES_TAX);
        if (model.profile.head()) {
            long reserve = Money.multiplyCapped(city.headReserveDays(), treasury.averageTax());
            long free = Math.max(0, economy.purse(treasury.key()) - treasury.committed() - reserve - Money.share(taxToday, share, 1000));
            long spend = free / 2;
            if (spend > 0) {
                long materials = Money.share(spend, 60, 100);
                long local = Money.share(spend, 30, 100);
                long imports = spend - materials - local;
                economy.purseToTreasury(treasury.key(), materials, LedgerCategory.CITIES, CityLedger.MATERIALS, day);
                economy.cityTally(kingdom, model.id).add(CityStatement.Line.CROWN_GOODS_SOLD, materials);
                // What the head buys of its own shops: a workshop where there is one, else a shop, else the townspeople.
                Business seller = null;
                for (Business shop : model.shops) {
                    if (seller == null || model.kind(shop) == Kind.MASTER && model.kind(seller) != Kind.MASTER) {
                        seller = shop;
                    }
                }
                if (seller != null) {
                    economy.purseMove(treasury.key(), CityPurses.PurseKey.till(kingdom, seller.id()), local, CityLedger.PURCHASES, day);
                    economy.cityTally(kingdom, model.id).add(CityStatement.Line.HEAD_BOUGHT, local);
                } else {
                    economy.purseMove(treasury.key(), CityPurses.PurseKey.cityPurse(kingdom, model.id), local, CityLedger.PURCHASES, day);
                }
                economy.purseLeaves(treasury.key(), model.id, imports, CityPurses.Outflow.IMPORT, day);
            }
        }
        CityTreasury.Settlement settlement = CityTreasury.settle(economy.purse(treasury.key()), treasury.granted(), taxToday, treasury.averageTax(),
                share, model.profile.head(), treasury.averageWithToday(CityStatement.Line.KEPT), treasury.committed(), city);
        long kept = settlement.kept();
        if (settlement.share() > 0 && !economy.purseToTreasury(treasury.key(), settlement.share(), LedgerCategory.CITIES, CityLedger.CROWN_SHARE,
                day).ok()) {
            kept += settlement.share();
        }
        tally.add(CityStatement.Line.KEPT, kept);
        if (settlement.idle() > 0) {
            economy.purseToTreasury(treasury.key(), settlement.idle(), LedgerCategory.CITIES, CityLedger.IDLE_SHARE, day);
        }
        // The purse keeps three days of what comes in.
        CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom, model.id);
        long keep = Money.multiplyCapped(city.purseKeepDays(), treasury.averageWithToday(CityStatement.Line.PURSE_IN));
        long over = economy.purse(purse) - keep;
        if (over > 0) {
            economy.purseLeaves(purse, model.id, over, CityPurses.Outflow.PURSE_FULL, day);
        }
    }
}
