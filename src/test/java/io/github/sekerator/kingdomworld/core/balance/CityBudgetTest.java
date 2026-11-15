package io.github.sekerator.kingdomworld.core.balance;

import io.github.sekerator.kingdomworld.core.economy.CityEconomyRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The money of the cities of the example kingdom over 60 days: every city brings the crown something, a village less than a town
 * and a town less than a large city, no purse, treasury or till below 0, the money adds up to the coin every day, neglected
 * cities and heavy taxes bring less, the crown earns most at a burden of 30 percent or less, the share of the crown bounds what
 * the cities spend, and the treasury does not grow without end. With workshops and trading houses every city brings at least as
 * much as with resellers only, the citizens export no more than the demand from abroad of their city, the trading houses feed
 * their keepers and stay open, nothing is exported while raw materials cost their worth, and the purchases of the crown keep
 * within their sum of the day and its days of wages.
 */
class CityBudgetTest {
    private static final int DAYS = 60;

    private static CityBudget.Result run(List<CityBudget.Profile> profiles, CityBudget.Rules rules) {
        return CityBudget.run(profiles, rules, DAYS);
    }

    private static void print(String label, CityBudget.Result result) {
        System.out.printf("== %s | crown %d -> %d (lowest %d) | left %d, from abroad %d, from players %d | mass day 1 %d, day %d %d"
                        + " | evasion %d%%, means %+d | balanced %s%n", label, result.crownStart(), result.crownEnd(), result.crownLowest(),
                result.left(), result.fromAbroad(), result.fromPlayers(), result.massFirst(), DAYS, result.massLast(), result.evasionPercent(),
                result.means(), result.balanced());
        System.out.println("   city       | income/day | to city | to crown | spent | treasury d60 | crown money | goods | total/day | sales | shops"
                + " closed");
        for (CityBudget.CityResult city : result.cities()) {
            System.out.printf("   %-10s | %10d | %7d | %8d | %5d | %12d | %11d | %5d | %9d | %5d | %d of %d%n", city.name(), city.income(),
                    city.kept(), city.toCrown(), city.spent(), city.balance(), city.crownMoney(), city.crownGoods(), city.total(), city.sales(),
                    city.closed(), city.closed() + city.shops());
        }
    }

    private static void printCrafts(String label, CityBudget.Result result) {
        print(label, result);
        System.out.printf("   citizens exported %d in all, the crown bought of them %d at most a day, kept its days of wages %s%n",
                result.citizenExports(), result.crownBoughtMost(), result.keptWages());
        System.out.println("   city       | demand | exported worth | exports | master sales | crown bought | imported | keeper / master / trader"
                + " earn | traders closed");
        for (CityBudget.CityResult city : result.cities()) {
            System.out.printf("   %-10s | %6.1f | %14.1f | %7d | %12d | %12d | %8d | %5.1f / %5.1f / %5.1f | %d%n", city.name(), city.demand(),
                    city.exportedWorth(), city.exports(), city.masterSales(), city.crownBought(), city.imported(), city.keeperIncome(),
                    city.masterIncome(), city.traderIncome(), city.tradersClosed());
        }
    }

    /**
     * A kingdom whose storehouses hold a surplus of raw materials, four targets: the masters buy them cheap and export.
     */
    private static CityBudget.Rules surplus() {
        return CityBudget.Rules.standard().withRawTargets(4.0);
    }

    @Test
    void workshopsAndTradingHousesBringTheCrownAtLeastAsMuch() {
        CityBudget.Rules rules = surplus();
        CityBudget.Result resellers = run(CityBudget.example(), rules);
        CityBudget.Result crafts = run(CityBudget.withCrafts(), rules);
        printCrafts("first stage: resellers only, the crown imports what it needs", resellers);
        printCrafts("second stage: workshops and trading houses", crafts);
        assertTrue(crafts.balanced(), "the money did not add up on some day");
        for (int i = 0; i < crafts.cities().size(); i++) {
            CityBudget.CityResult city = crafts.cities().get(i);
            CityBudget.CityResult before = resellers.cities().get(i);
            assertTrue(city.total() >= before.total(), city.name() + " brings less than with resellers: " + city.total() + " < " + before.total());
            assertTrue(city.exportedWorth() <= city.demand() + 1e-6, city.name() + " exports beyond its demand from abroad: "
                    + city.exportedWorth() + " > " + city.demand());
            assertTrue(city.balance() >= 0, "a treasury of a city below 0");
        }
        long merchant = rules.economy().defaultWage("merchant");
        for (CityBudget.CityResult city : crafts.cities()) {
            if (city.exports() > 0 || city.traderIncome() > 0) {
                assertTrue(city.traderIncome() * 2 > merchant, "the trading houses of " + city.name() + " do not feed their keepers: "
                        + city.traderIncome());
            }
            assertEquals(0, city.tradersClosed(), "a trading house of " + city.name() + " closed");
        }
        assertTrue(crafts.citizenExports() > 0, "the citizens exported nothing with a surplus of raw materials");
        assertTrue(crafts.crownBoughtMost() <= rules.craft().crownBuyPerDay(), "the crown bought beyond its sum of a day");
        assertTrue(crafts.keptWages(), "the purchases of the crown took the treasury below its days of wages");
        assertTrue(crafts.crownLowest() >= 0, "the treasury of the kingdom went below 0");
    }

    @Test
    void nothingIsExportedWhileRawMaterialsCostTheirWorth() {
        CityBudget.Result atTarget = run(CityBudget.withCrafts(), CityBudget.Rules.standard());
        printCrafts("raw materials at the target of the storehouses", atTarget);
        assertTrue(atTarget.balanced());
        assertEquals(0, atTarget.citizenExports(), "citizens exported with raw materials at their worth");
    }

    @Test
    void theVariantsOfTheSecondStage() {
        CityBudget.Rules rules = surplus();
        List<CityBudget.Profile> neglected = new ArrayList<>();
        List<CityBudget.Profile> headless = new ArrayList<>();
        for (CityBudget.Profile profile : CityBudget.withCrafts()) {
            neglected.add(profile.neglected());
            headless.add(profile.withoutHead());
        }
        CityBudget.Result standard = run(CityBudget.withCrafts(), rules);
        CityBudget.Result abandoned = run(neglected, rules);
        printCrafts("second stage, neglected: no shops, no market, no roads", abandoned);
        assertTrue(abandoned.balanced());
        assertTrue(abandoned.total() < standard.total(), "neglected cities bring as much");
        int[][] taxes = {{15, 15}, {20, 20}, {20, 30}};
        for (int[] pair : taxes) {
            CityBudget.Result taxed = run(CityBudget.withCrafts(), rules.withCity(CityEconomyRules.DEFAULT.withTaxes(pair[0], pair[1], 50)));
            printCrafts("second stage, sales tax " + pair[0] + "%, tithe " + pair[1] + "%", taxed);
            assertTrue(taxed.balanced());
        }
        for (int crownShare : new int[]{0, 100}) {
            CityBudget.Result shared = run(CityBudget.withCrafts(), rules.withCity(CityEconomyRules.DEFAULT.withTaxes(10, 10, crownShare)));
            printCrafts("second stage, crown share " + crownShare + "%", shared);
            assertTrue(shared.balanced());
        }
        CityBudget.Result idle = run(headless, rules);
        printCrafts("second stage, no heads", idle);
        assertTrue(idle.balanced());
    }

    @Test
    void everyCityBringsTheCrownSomethingAndTheMoneyAddsUp() {
        CityBudget.Result result = run(CityBudget.example(), CityBudget.Rules.standard());
        print("default", result);
        print("today's rules", run(CityBudget.example(), CityBudget.Rules.standard().today()));
        assertTrue(result.balanced(), "the money did not add up on some day");
        List<CityBudget.CityResult> cities = result.cities();
        for (CityBudget.CityResult city : cities) {
            assertTrue(city.total() > 0, city.name() + " brings the crown nothing: " + city.total());
            assertTrue(city.balance() >= 0, "a treasury of a city below 0");
        }
        assertTrue(cities.get(0).total() < cities.get(1).total(), "a village brings more than a town");
        assertTrue(cities.get(1).total() < cities.get(2).total(), "a town brings more than a large city");
        assertTrue(result.crownLowest() >= 0, "the treasury of the kingdom went below 0");
        long growth = result.crownEnd() - result.crownStart();
        assertTrue(growth < 1_000L * Math.max(1, result.dailyExpense()), "the treasury grows without end: " + growth);
    }

    @Test
    void neglectedCitiesAndHeavyTaxesBringLess() {
        CityBudget.Result standard = run(CityBudget.example(), CityBudget.Rules.standard());
        List<CityBudget.Profile> neglected = new ArrayList<>();
        for (CityBudget.Profile profile : CityBudget.example()) {
            neglected.add(profile.neglected());
        }
        CityBudget.Result abandoned = run(neglected, CityBudget.Rules.standard());
        print("neglected: no shops, no players", abandoned);
        assertTrue(abandoned.balanced());
        assertTrue(abandoned.total() < standard.total(), "neglected cities bring as much: " + abandoned.total() + " / " + standard.total());
        CityEconomyRules heavy = CityEconomyRules.DEFAULT.withTaxes(20, 30, 50);
        CityBudget.Result taxed = run(CityBudget.example(), CityBudget.Rules.standard().withCity(heavy));
        print("sales tax 20%, tithe 30%", taxed);
        assertTrue(taxed.balanced());
        assertTrue(taxed.total() < standard.total(), "heavy taxes bring as much: " + taxed.total() + " / " + standard.total());
        assertTrue(taxed.means() < 0 && taxed.evasionPercent() > 0, "heavy taxes neither choke nor are evaded");
    }

    @Test
    void theCrownEarnsMostAtABurdenOfThirtyPercentOrLess() {
        long best = Long.MIN_VALUE;
        int bestBurden = -1;
        for (int burden = 10; burden <= 50; burden += 10) {
            int sales = Math.min(CityEconomyRules.MAX_SALES_TAX_PERCENT, burden / 2);
            int tithe = burden - sales;
            CityBudget.Result result = run(CityBudget.example(), CityBudget.Rules.standard().withCity(CityEconomyRules.DEFAULT.withTaxes(sales,
                    tithe, 50)));
            print("burden " + burden + "%", result);
            assertTrue(result.balanced());
            if (result.total() > best) {
                best = result.total();
                bestBurden = burden;
            }
        }
        assertTrue(bestBurden <= 30, "the crown earns most at a burden of " + bestBurden + "%");
    }

    @Test
    void theShareOfTheCrownBoundsWhatTheCitiesSpend() {
        CityBudget.Result none = run(CityBudget.example(), CityBudget.Rules.standard().withCity(CityEconomyRules.DEFAULT.withTaxes(10, 10, 0)));
        CityBudget.Result all = run(CityBudget.example(), CityBudget.Rules.standard().withCity(CityEconomyRules.DEFAULT.withTaxes(10, 10, 100)));
        print("crown share 0%", none);
        print("crown share 100%", all);
        assertTrue(none.balanced() && all.balanced());
        assertTrue(none.spent() > 0, "with no share of the crown the cities with a head spend nothing");
        assertEquals(0, all.spent(), "with all of it for the crown the cities spend nothing");
        assertTrue(all.cities().get(1).kept() == 0, "a city keeps something of a share of 100%");
        List<CityBudget.Profile> headless = new ArrayList<>();
        for (CityBudget.Profile profile : CityBudget.example()) {
            headless.add(profile.withoutHead());
        }
        CityBudget.Result idle = run(headless, CityBudget.Rules.standard());
        print("no heads", idle);
        assertTrue(idle.balanced());
        assertEquals(0, idle.spent(), "a city without a head spends nothing");
        for (CityBudget.CityResult city : idle.cities()) {
            // A week of its own share at most, beyond what it collected today.
            long week = (long) (CityEconomyRules.DEFAULT.idleReserveDays() + 1) * Math.max(1, city.income());
            assertTrue(city.balance() <= week, city.name() + " keeps more than a week: " + city.balance());
        }
    }

    @Test
    void goodsCountAtThePriceOfExportAndASurplusCountsForLess() {
        CityBudget.Rules rules = CityBudget.Rules.standard();
        double atTarget = CityBudget.exportFactor(rules, (long) rules.goodsKinds() * rules.trade().stockTarget());
        double surplus = CityBudget.exportFactor(rules, (long) rules.goodsKinds() * rules.trade().stockTarget() * 20);
        assertTrue(atTarget <= rules.trade().exportFactor() + 1e-9, "goods never count above the share merchants get abroad");
        assertTrue(surplus < atTarget, "a surplus counts as much: " + surplus + " / " + atTarget);
    }
}
