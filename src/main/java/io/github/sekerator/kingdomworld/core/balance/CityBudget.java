package io.github.sekerator.kingdomworld.core.balance;

import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.CityDay;
import io.github.sekerator.kingdomworld.core.commerce.CityMeans;
import io.github.sekerator.kingdomworld.core.commerce.Evasion;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A model of the money of the cities of one kingdom of players over game days, without the world, built of the very parts the
 * game uses: the books of the economy with their purses, tills and treasuries of cities, the day of a city, the prices and the
 * margins of the shops, evasion, the means of the people and the treasury of a city with its share of the crown. Whole coins
 * only, the fractions carried as the game carries them.
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

    private CityBudget() {
    }

    /**
     * A city of the example.
     *
     * @param crown    workers of the crown by profession, merchants among them
     * @param keepers  townspeople with a shop each
     * @param idle     townspeople without work
     * @param head     whether a head spends its money
     * @param players  what players spend in its shops a day
     * @param houses   houses that keep a share of the wages
     */
    public record Profile(String name, CityLevel level, Map<String, Integer> crown, int keepers, int idle, boolean head, boolean tavern,
                          long players, int houses) {
        public Profile {
            Objects.requireNonNull(name, "name");
            crown = Map.copyOf(crown);
        }

        public int people() {
            int sum = keepers + idle;
            for (int count : crown.values()) {
                sum += count;
            }
            return sum;
        }

        int merchants() {
            return crown.getOrDefault("merchant", 0);
        }

        public Profile neglected() {
            return new Profile(name, level, crown, 0, idle + keepers, head, tavern, 0, houses);
        }

        public Profile withoutHead() {
            return new Profile(name, level, crown, keepers, idle, false, tavern, players, houses);
        }
    }

    /**
     * The rules of the model.
     *
     * @param old           today's rules: wages leave the world, no shops, no treasuries of cities
     * @param exportPerMerchant what a merchant of the crown sells abroad a day
     * @param foodPerHead   the worth of what one person eats a day
     * @param goodsKinds    kinds of goods the storehouses keep a target of
     */
    public record Rules(CityEconomyRules city, EconomyRules economy, TradeRules trade, long exportPerMerchant, int foodPerHead, int goodsKinds,
                        long crownStart, boolean old) {
        public static Rules standard() {
            return new Rules(CityEconomyRules.DEFAULT, EconomyRules.DEFAULT, TradeRules.DEFAULT, 115, 8, 40, 3_000, false);
        }

        public Rules withCity(CityEconomyRules value) {
            return new Rules(value, economy, trade, exportPerMerchant, foodPerHead, goodsKinds, crownStart, old);
        }

        public Rules today() {
            return new Rules(city, economy, trade, exportPerMerchant, foodPerHead, goodsKinds, crownStart, true);
        }
    }

    /**
     * What a city comes to, the averages a day over all the days.
     */
    public record CityResult(String name, long income, long kept, long toCrown, long spent, long balance, long crownMoney, long crownGoods,
                             long sales, int closed, int shops) {
        public long total() {
            return crownMoney + crownGoods;
        }
    }

    /**
     * What the kingdom comes to.
     *
     * @param left       money that left the world in all the days
     * @param fromAbroad money that came from abroad (the merchants of the crown)
     * @param fromPlayers money players brought into the shops
     * @param massFirst  the money of the currency after the first day
     * @param massLast   after the last
     * @param balanced   whether the money added up on every day
     * @param dailyExpense what the crown spent a day on average
     */
    public record Result(List<CityResult> cities, long crownStart, long crownEnd, long crownLowest, long left, long fromAbroad, long fromPlayers,
                         long massFirst, long massLast, boolean balanced, long dailyExpense, int evasionPercent, int means) {
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
     * The three cities of the example: a village without a head, a town and a large city with one.
     */
    public static List<Profile> example() {
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
        return List.of(new Profile("Olkhovka", CityLevel.VILLAGE, village, 1, 1, false, false, 0, 4),
                new Profile("Kamenka", CityLevel.TOWN, town, 6, 1, true, true, 15, 10),
                new Profile("Belograd", CityLevel.CITY, city, 16, 5, true, true, 60, 20));
    }

    // ---------------------------------------------------------------- the model

    private static final class City {
        final Profile profile;
        final long id;
        final List<Business> shops = new ArrayList<>();
        final Map<UUID, String> citizens = new LinkedHashMap<>();
        long houses;
        long housesCap;
        long[] carry = new long[GoodsCategory.values().length];
        int closed;
        long sales;
        long spent;
        long income;
        long kept;
        long toCrown;
        long crownMoney;
        long crownGoods;

        City(Profile profile, long id) {
            this.profile = profile;
            this.id = id;
        }
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
            for (int n = 0; n < profile.keepers() + profile.idle(); n++) {
                model.citizens.put(UUID.randomUUID(), "unemployed");
            }
            model.housesCap = Money.multiplyCapped(profile.houses(), 300);
            if (!rules.old()) {
                for (int n = 0; n < profile.keepers(); n++) {
                    model.shops.add(new Business(nextShop++, kingdom, model.id, new Business.Place(Business.PlaceKind.MERCHANT_HOUSE, nextShop, 0,
                            "w", 0, 0, 0), 0, 200));
                }
            }
            cities.add(model);
        }
        int burden = city.salesTaxPercent() * 10 + city.tithePercent() * 10;
        int evasion = Evasion.permille(burden, city.evasionStartPercent(), city.evasionMaxPercent());
        CityMeans means = CityMeans.of(burden, 0, false, false, city.crownSharePercent() * 10, city.crownShareFairPercent());
        double speed = Payroll.workSpeed(50 + means.value(), rules.economy().moodWorkEffect());
        long held0 = held(economy, kingdom, cities);
        long fromAbroad = 0;
        long fromPlayers = 0;
        long left = 0;
        long lowest = rules.crownStart();
        long massFirst = 0;
        boolean balanced = true;
        long stockItems = (long) rules.goodsKinds() * rules.trade().stockTarget();
        long crownExpense = 0;
        int tax = city.salesTaxPercent() * 10;
        int tithe = city.tithePercent() * 10;
        int share = city.crownSharePercent() * 10;
        for (int day = 1; day <= days; day++) {
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
            for (City model : cities) {
                if (!rules.old()) {
                    payShops(economy, kingdom, model, tithe, evasion, day);
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
            // The players who spent in the shops brought new money.
            for (City model : cities) {
                long paid = model.profile.players() > 0 && !rules.old() ? playersBuy(economy, kingdom, model, model.profile.players(), tax, evasion, day)
                        : 0;
                fromPlayers += paid;
            }
            for (City model : cities) {
                if (!rules.old()) {
                    endOfDay(economy, kingdom, model, rules, day, share);
                }
            }
            // Goods of the crown: made by its workers, eaten by the people, sold to the shops, exported.
            long made = 0;
            for (City model : cities) {
                long makes = 0;
                for (Map.Entry<String, Integer> entry : model.profile.crown().entrySet()) {
                    makes += (long) MAKES.getOrDefault(entry.getKey(), 0) * entry.getValue();
                }
                long madeHere = Math.round(makes * (rules.old() ? 1.0 : speed));
                long eaten = (long) model.profile.people() * rules.foodPerHead();
                long sold = 0;
                for (Business shop : model.shops) {
                    sold += shop.day(day).map(Business.Day::bought).orElse(0L);
                }
                long exported = (long) Math.ceil(Money.multiplyCapped(model.profile.merchants(), rules.exportPerMerchant()) / rules.trade().exportFactor());
                long net = madeHere - eaten - sold - exported;
                double factor = exportFactor(rules, stockItems);
                model.crownGoods += Math.round(net * factor);
                stockItems = Math.max(0, stockItems + Math.round(net / WORTH));
                made += madeHere;
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
            results.add(new CityResult(model.profile.name(), model.income / days, model.kept / days, model.toCrown / days, model.spent / days,
                    economy.purse(CityPurses.PurseKey.cityTreasury(kingdom, model.id)), model.crownMoney / days, model.crownGoods / days,
                    model.sales / days, model.closed, model.shops.size()));
        }
        return new Result(results, rules.crownStart(), economy.treasury(kingdom).orElseThrow().funds(), lowest, left, fromAbroad, fromPlayers,
                massFirst, economy.moneyMass(kingdom), balanced, crownExpense / days, Evasion.percent(burden, city.evasionStartPercent(),
                city.evasionMaxPercent()), means.value());
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
            boolean fresh = shop.days().isEmpty() && shop.goods().isEmpty() && shop.debt() == 0;
            long budget = fresh ? city.shopCredit() : Math.max(0, economy.purse(CityPurses.PurseKey.till(kingdom, shop.id())) - shop.profitDue());
            for (GoodsCategory kind : GoodsCategory.values()) {
                long average = shop.averageCategory(day, kind.ordinal());
                long target = average < 0 ? (long) (strength(model, city, speed) * city.basketShare(kind.ordinal()) / 100.0)
                        : Money.multiplyCapped(city.stockDays(), average);
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
                shop.addGoods("goods" + kind.ordinal(), kind.ordinal(), count, cost * ShopPricing.MILLI);
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
            capacity.put(shop.id(), strength(model, city, speed));
        }
        for (GoodsCategory kind : GoodsCategory.values()) {
            List<CityDay.Offer> offers = new ArrayList<>();
            for (Business shop : model.shops) {
                Business.Lot lot = shop.goods().get("goods" + kind.ordinal());
                if (lot != null && lot.count() > 0) {
                    offers.add(new CityDay.Offer(shop.id(), "goods" + kind.ordinal(), kind.ordinal(), WORTH,
                            ShopPricing.askedMilli(lot.costMilli(), shop.marginPermille(), ceiling), lot.costMilli(), lot.count()));
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
                long price = ShopPricing.askedMilli(lot.costMilli(), shop.marginPermille(), Long.MAX_VALUE);
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
                model.sales += total;
                paid += total + taxed;
                left -= total + taxed;
            }
        }
        return paid;
    }

    private static void endOfDay(Economy economy, UUID kingdom, City model, Rules rules, long day, int share) {
        CityEconomyRules city = rules.city();
        // The shops: upkeep, the debt first, profit, margin, and closing.
        for (Business shop : new ArrayList<>(model.shops)) {
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
            shop.setMarginPermille(ShopPricing.nextMargin(shop.marginPermille(), city.fairIncome(), 1, today.soldCost(), city.minMarginPercent(),
                    city.maxMarginPercent(), city.marginStepPercent(), city.businessUpkeepPercent()));
            boolean poor = day - shop.opened() >= 2 && CityDay.poor(CityDay.earned(today.sales(), today.soldCost(), result.upkeep()),
                    city.keeperUsualIncome());
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
                if (!model.shops.isEmpty()) {
                    economy.purseMove(treasury.key(), CityPurses.PurseKey.till(kingdom, model.shops.get(0).id()), local, CityLedger.PURCHASES, day);
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
