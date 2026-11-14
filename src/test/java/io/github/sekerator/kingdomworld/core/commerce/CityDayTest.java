package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.economy.CityEconomyRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The day of a city: money and items add up, the demand that found no goods, goods from afar, the fractions carried, the share
 * of the crown for all the shops of a kingdom together, nothing edible below the rations, no new shops while the crown has no
 * goods, and the end of the day of a shop.
 */
class CityDayTest {
    private static final int[] BASKET = CityEconomyRules.DEFAULT.basket();

    @Test
    void moneyAndItemsAddUpOnEveryDay() {
        Random random = new Random(7);
        for (int round = 0; round < 500; round++) {
            long purse = random.nextInt(5_000);
            List<CityDay.Offer> offers = new ArrayList<>();
            Map<Long, Double> capacity = new HashMap<>();
            Map<String, Long> stock = new HashMap<>();
            for (long shop = 1; shop <= 1 + random.nextInt(5); shop++) {
                capacity.put(shop, 50.0 + random.nextInt(300));
                for (int i = 0; i < 1 + random.nextInt(6); i++) {
                    int kind = random.nextInt(GoodsCategory.values().length);
                    long count = random.nextInt(80);
                    String item = "item" + shop + "_" + i;
                    stock.put(shop + "/" + item, count);
                    offers.add(new CityDay.Offer(shop, item, kind, 1 + random.nextInt(8), 500 + random.nextInt(9_000), 400, count));
                }
            }
            long[] carry = {random.nextInt(100), random.nextInt(100), 0, 0, random.nextInt(100)};
            CityDay.Spending spending = CityDay.spend(purse, carry, BASKET, 10, round % 2 == 0, offers, capacity, 100);
            long paid = 0;
            for (CityDay.Sale sale : spending.sales()) {
                paid += sale.total() + sale.tax();
                String key = sale.shop() + "/" + sale.item();
                long left = stock.get(key) - sale.count();
                assertTrue(left >= 0, "sold more than the shop had");
                stock.put(key, left);
                assertTrue(sale.count() > 0);
            }
            assertEquals(spending.spent(), paid);
            assertEquals(purse / 10, spending.afar(), "a tenth from afar");
            assertTrue(spending.afar() + spending.spent() <= purse, "never more than the purse: " + purse);
            assertEquals(spending.wanted() - spending.spent(), spending.unspent());
            for (double left : capacity.values()) {
                assertTrue(left >= -1e-9, "a shop sold beyond its strength");
            }
        }
    }

    @Test
    void theFractionsOfTheSplitAreCarriedKindByKind() {
        CityDay.Budgets first = CityDay.budgets(7, new long[5], BASKET, true);
        // 7 x 35 = 245 hundredths: 2 coins of food and 45 hundredths carried.
        assertEquals(2, first.budgets()[0]);
        assertEquals(45, first.carry()[0]);
        CityDay.Budgets second = CityDay.budgets(7, first.carry(), BASKET, true);
        assertEquals(2, second.budgets()[0]);
        assertEquals(90, second.carry()[0]);
        CityDay.Budgets third = CityDay.budgets(7, second.carry(), BASKET, true);
        assertEquals(3, third.budgets()[0], "the fractions of three days make a coin");
        CityDay.Budgets village = CityDay.budgets(100, new long[5], BASKET, false);
        assertEquals(0, village.budgets()[GoodsCategory.LUXURY.ordinal()], "a village wants no luxury");
        assertEquals(30, village.budgets()[GoodsCategory.HOUSEHOLD.ordinal()], "its share goes to household goods");
        assertEquals(90, village.total(), "the rest of the purse is from afar");
    }

    @Test
    void theCheapestForTheirWorthSellFirstAndTheDemandWithoutGoodsStays() {
        Map<Long, Double> capacity = new HashMap<>(Map.of(1L, 1_000.0, 2L, 1_000.0));
        List<CityDay.Offer> offers = List.of(new CityDay.Offer(1, "dear", 0, 1.0, 3_000, 2_000, 100),
                new CityDay.Offer(2, "cheap", 0, 1.0, 2_000, 1_500, 5));
        List<CityDay.Sale> sales = CityDay.spendKind(30, offers, capacity, 0);
        assertEquals("cheap", sales.get(0).item(), "the cheap shop sells first");
        assertEquals(5, sales.get(0).count(), "all it has");
        assertEquals(6, sales.get(1).count(), "the rest buys as many dear ones as the money allows: (30 - 10) / 3");
        List<CityDay.Sale> none = CityDay.spendKind(100, List.of(), capacity, 0);
        assertTrue(none.isEmpty(), "without goods the money stays in the purse");
        Map<Long, Double> weak = new HashMap<>(Map.of(1L, 3.0));
        List<CityDay.Sale> limited = CityDay.spendKind(1_000, List.of(new CityDay.Offer(1, "a", 0, 1.0, 1_000, 900, 100)), weak, 0);
        assertEquals(3, limited.get(0).count(), "no shop sells beyond its strength");
    }

    @Test
    void aBigPurseCostsNoMoreThanASmallOne() {
        Map<Long, Double> capacity = new HashMap<>(Map.of(1L, 1e12));
        List<CityDay.Offer> offers = List.of(new CityDay.Offer(1, "a", 0, 2.0, 2_500, 2_000, 1_000_000_000L));
        long start = System.nanoTime();
        List<CityDay.Sale> sales = CityDay.spendKind(1_000_000_000L, offers, capacity, 100);
        assertTrue(System.nanoTime() - start < 50_000_000L, "a single division, not a loop over the items");
        assertTrue(sales.get(0).count() > 300_000_000L);
    }

    @Test
    void theShareOfTheCrownIsSharedByAllTheCitiesOfAKingdom() {
        List<RestockPlan.Need> needs = new ArrayList<>();
        for (long shop = 1; shop <= 10; shop++) {
            needs.add(new RestockPlan.Need(shop, "bread", 0, 100));
        }
        RestockPlan.Result result = RestockPlan.allocate(needs, Map.of("bread", 1_000L), 25, Map.of(), Long.MAX_VALUE);
        long taken = result.takes().stream().mapToLong(RestockPlan.Take::count).sum();
        assertEquals(250, taken, "a quarter of the free stock for all the shops together, not for each");
        assertEquals(10, result.short_().size());
        assertEquals("bread", result.missing());
        for (RestockPlan.Take take : result.takes()) {
            assertEquals(25, take.count(), "split by what each lacks");
        }
        RestockPlan.Result plenty = RestockPlan.allocate(List.of(new RestockPlan.Need(1, "bread", 0, 10)), Map.of("bread", 1_000L), 25, Map.of(),
                Long.MAX_VALUE);
        assertEquals(10, plenty.takes().get(0).count());
        assertTrue(plenty.short_().isEmpty());
    }

    @Test
    void nothingEdibleIsTakenBelowTheRations() {
        List<RestockPlan.Need> needs = List.of(new RestockPlan.Need(1, "bread", 0, 100), new RestockPlan.Need(1, "torch", 3, 100));
        RestockPlan.Result result = RestockPlan.allocate(needs, Map.of("bread", 1_000L, "torch", 1_000L), 25, Map.of("bread", 5), 60);
        long bread = result.takes().stream().filter(take -> take.item().equals("bread")).mapToLong(RestockPlan.Take::count).sum();
        long torches = result.takes().stream().filter(take -> take.item().equals("torch")).mapToLong(RestockPlan.Take::count).sum();
        assertEquals(12, bread, "60 points of nutrition to spare are 12 loaves");
        assertEquals(100, torches, "what is not eaten is not held back");
        RestockPlan.Result hungry = RestockPlan.allocate(needs, Map.of("bread", 1_000L), 25, Map.of("bread", 5), 0);
        assertTrue(hungry.takes().stream().noneMatch(take -> take.item().equals("bread")));
    }

    @Test
    void shopsOpenByDemandAndNotWhileTheCrownHasNoGoods() {
        assertEquals(1, CityDay.openings(false, 0, 6, 150, false), "the first shop of a city opens without demand");
        assertEquals(0, CityDay.openings(true, 12, 6, 150, false), "no demand without goods");
        assertEquals(1, CityDay.openings(true, 13, 6, 150, false));
        assertEquals(3, CityDay.openings(true, 900, 6, 150, false), "as many as the demand would feed");
        assertEquals(0, CityDay.openings(true, 900, 6, 150, true), "the crown gives no goods: nothing to trade");
        assertEquals(0, CityDay.openings(false, 900, 6, 150, true));
    }

    @Test
    void theEndOfTheDayOfAShop() {
        CityDay.Result result = CityDay.result(200, 100, 30, 50, 10, 5);
        assertEquals(5, result.upkeep(), "five in a hundred of the takings");
        assertEquals(30, result.debtPaid(), "the debt first, out of what is beyond the working money");
        assertEquals(200 - 5 - 10 - 30 - 50, result.profit(), "beyond the working money, the debt and what the keepers are owed");
        CityDay.Result young = CityDay.result(20, 20, 100, 50, 0, 5);
        assertEquals(0, young.debtPaid(), "a young shop builds its working money first");
        assertEquals(0, young.profit());
        assertEquals(15, CityDay.earned(100, 80, 5), "its trade earned all the same");
        assertTrue(CityDay.poor(2, 5));
        assertFalse(CityDay.poor(3, 5));
    }

    @Test
    void theKeepersOfAPlace() {
        java.util.UUID resident = new java.util.UUID(0, 1);
        java.util.UUID near = new java.util.UUID(0, 2);
        java.util.UUID far = new java.util.UUID(0, 3);
        java.util.UUID worker = new java.util.UUID(0, 4);
        List<KeeperChoice.Candidate> candidates = List.of(new KeeperChoice.Candidate(far, true, false, 900),
                new KeeperChoice.Candidate(near, true, false, 4), new KeeperChoice.Candidate(resident, true, true, 100),
                new KeeperChoice.Candidate(worker, false, true, 0));
        KeeperChoice.Choice two = KeeperChoice.choose(candidates, 2, true);
        assertEquals(List.of(resident, near), two.keepers(), "those living in the house first, then the nearest");
        assertFalse(two.family());
        KeeperChoice.Choice family = KeeperChoice.choose(List.of(new KeeperChoice.Candidate(worker, false, true, 0)), 2, true);
        assertTrue(family.family() && family.keepers().isEmpty(), "people with posts keep it as a family in the evening");
        KeeperChoice.Choice none = KeeperChoice.choose(List.of(new KeeperChoice.Candidate(worker, false, true, 0)), 1, false);
        assertFalse(none.any(), "a counter or a stall needs a townsperson");
        assertEquals(List.of(near), KeeperChoice.choose(candidates, 1, false).keepers());
    }

    @Test
    void goodsOfAShopKeepTheirAverageCost() {
        Business shop = new Business(1, new java.util.UUID(1, 1), 0, new Business.Place(Business.PlaceKind.COUNTER, 0, 0, "w", 0, 0, 0), 0, 200);
        shop.addGoods("bread", 0, 10, 2_000);
        shop.addGoods("bread", 0, 10, 4_000);
        assertEquals(20, shop.count("bread"));
        assertEquals(3_000, shop.goods().get("bread").costMilli());
        assertEquals(5, shop.removeGoods("bread", 5));
        assertEquals(15, shop.removeGoods("bread", 50));
        assertTrue(shop.goods().isEmpty());
        shop.record(3, new Business.Day(10, 8, 20, 1, new long[]{4, 0, 0, 0, 0}));
        shop.record(4, new Business.Day(30, 20, 0, 3, new long[]{8, 0, 0, 0, 0}));
        assertEquals(20, shop.averageSales(5));
        assertEquals(10, shop.averageBought(5));
        assertEquals(6, shop.averageCategory(5, 0));
        assertEquals(-1, shop.averageCategory(1, 0), "no days yet");
    }
}
