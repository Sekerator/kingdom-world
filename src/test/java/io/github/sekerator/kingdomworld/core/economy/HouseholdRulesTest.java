package io.github.sekerator.kingdomworld.core.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseholdRulesTest {
    private final HouseholdRules rules = new HouseholdRules(20, 300, 6, 2);

    @Test
    void aShareOfThePaidWageGoesHome() {
        assertEquals(4, rules.share(20));
        assertEquals(2, rules.share(10));
        assertEquals(0, rules.share(4), "A share is never rounded up: no coin is made");
        assertEquals(0, rules.share(0));
        assertEquals(0, rules.share(-5));
        assertEquals(0, new HouseholdRules(0, 300, 6, 2).share(1_000), "No share keeps the houses empty");
        assertEquals(1_000, new HouseholdRules(250, 300, 6, 2).share(1_000), "Never more than the wage");
    }

    @Test
    void moneySetAsideBuysALittleFoodFirstAndTheRestLiesAsCoins() {
        HouseholdRules.Settlement plan = rules.settle(100, 0, 0, 8, 50);
        assertEquals(2, plan.food(), "A portion at a time");
        assertEquals(16, plan.paid());
        assertEquals(84, plan.coins());
        assertEquals(0, plan.lost());
        assertEquals(100, plan.paid() + plan.coins() + plan.lost(), "Every coin set aside is accounted for");
    }

    @Test
    void aFullHouseBuysNothingAndKeepsNoMoreThanItsLimit() {
        HouseholdRules.Settlement fed = rules.settle(100, 0, 6, 8, 50);
        assertEquals(0, fed.food());
        assertEquals(100, fed.coins());
        HouseholdRules.Settlement nearlyFed = rules.settle(100, 0, 5, 8, 50);
        assertEquals(1, nearlyFed.food());
        HouseholdRules.Settlement rich = rules.settle(100, 250, 6, 8, 50);
        assertEquals(50, rich.coins());
        assertEquals(50, rich.lost(), "What goes beyond the limit leaves the world");
        HouseholdRules.Settlement full = rules.settle(100, 400, 6, 8, 50);
        assertEquals(0, full.coins());
        assertEquals(100, full.lost());
    }

    @Test
    void foodIsBoughtOnlyWhenTheStorehousesSpareItAndTheMoneyIsThere() {
        assertEquals(0, rules.settle(100, 0, 0, 8, 0).food(), "The storehouses keep their reserve");
        assertEquals(1, rules.settle(100, 0, 0, 8, 1).food());
        assertEquals(0, rules.settle(7, 0, 0, 8, 50).food(), "Too little for one loaf");
        assertEquals(7, rules.settle(7, 0, 0, 8, 50).coins());
        assertEquals(1, rules.settle(15, 0, 0, 8, 50).food());
        assertEquals(0, rules.settle(100, 0, 0, 0, 50).food(), "Nothing on offer has no price");
        HouseholdRules.Settlement none = rules.settle(0, 0, 0, 8, 50);
        assertEquals(0, none.food() + none.coins() + none.lost() + none.paid());
    }

    @Test
    void aHouseNobodyLooksAtGathersNoFortune() {
        assertEquals(600, rules.purseMax());
    }
}
