package io.github.sekerator.kingdomworld.core.commerce;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A place takes up what its city lacks most among the crafts whose workstations stand there, a trading house where masters make
 * more than the city buys, else the general store; a place nobody looked at resells; a fixed trade holds, and a shop keeps its
 * trade at least a week.
 */
class TradeChoiceTest {
    private static final TradeRegistry TRADES = TradeRegistry.builtIn();

    private static long[] lacking(GoodsCategory category, long worth) {
        long[] unmet = new long[GoodsCategory.values().length];
        unmet[category.ordinal()] = worth;
        return unmet;
    }

    private static String choose(Set<String> stations, boolean surveyed, boolean tradeHouse, TradeChoice.City city) {
        return TradeChoice.choose(TRADES.all(), new TradeChoice.Place(stations, surveyed, tradeHouse), city);
    }

    @Test
    void aPlaceNobodyLookedAtResells() {
        TradeChoice.City hungry = new TradeChoice.City(lacking(GoodsCategory.FOOD, 100), false, 0);
        assertEquals(Business.GENERAL_STORE, choose(Set.of("crafting_table", "furnace"), false, false, hungry));
    }

    @Test
    void whatTheCityLacksMostAmongTheCraftsOfThePlace() {
        TradeChoice.City hungry = new TradeChoice.City(lacking(GoodsCategory.FOOD, 100), false, 0);
        assertEquals("bakery", choose(Set.of("crafting_table", "furnace"), true, false, hungry));
        // A smoker alone makes a cookshop.
        assertEquals("cookshop", choose(Set.of("smoker"), true, false, hungry));
        // Without an oven or a smoker nothing makes food: the place resells.
        assertEquals(Business.GENERAL_STORE, choose(Set.of("crafting_table"), true, false, hungry));
        long[] unmet = new long[GoodsCategory.values().length];
        unmet[GoodsCategory.FOOD.ordinal()] = 30;
        unmet[GoodsCategory.TOOLS.ordinal()] = 80;
        TradeChoice.City tools = new TradeChoice.City(unmet, false, 0);
        assertEquals("toolmaker", choose(Set.of("crafting_table", "furnace", "smithing_table"), true, false, tools));
    }

    @Test
    void theTailorNeedsALoomAtItsPlace() {
        TradeChoice.City ragged = new TradeChoice.City(lacking(GoodsCategory.CLOTHING, 60), false, 0);
        assertEquals(Business.GENERAL_STORE, choose(Set.of("crafting_table"), true, false, ragged));
        assertEquals("tailor", choose(Set.of("crafting_table", "loom"), true, false, ragged));
    }

    @Test
    void buildingMaterialsTheCrownWantedMakeAMason() {
        TradeChoice.City building = new TradeChoice.City(lacking(GoodsCategory.MATERIALS, 50), false, 0);
        assertEquals("mason", choose(Set.of("stonecutter"), true, false, building));
    }

    @Test
    void aTradingHouseWhereMastersMakeMoreThanTheCityBuys() {
        TradeChoice.City nothingLacking = new TradeChoice.City(new long[GoodsCategory.values().length], true, 40);
        assertEquals(Trade.TRADER, choose(Set.of(), true, true, nothingLacking));
        // Not without masters, not without demand from abroad, not where it may not stand.
        assertEquals(Business.GENERAL_STORE, choose(Set.of(), true, true, new TradeChoice.City(null, false, 40)));
        assertEquals(Business.GENERAL_STORE, choose(Set.of(), true, true, new TradeChoice.City(null, true, 0)));
        assertEquals(Business.GENERAL_STORE, choose(Set.of(), true, false, nothingLacking));
    }

    @Test
    void aWorkshopKeepsItsCraftOnceTheCityLacksNothing() {
        TradeChoice.Place oven = new TradeChoice.Place(Set.of("crafting_table", "furnace"), true, false);
        TradeChoice.City fed = new TradeChoice.City(new long[GoodsCategory.values().length], false, 0);
        assertEquals("bakery", TradeChoice.chooseFor(TRADES.all(), oven, fed, "bakery"), "a bakery that fed the city stays a bakery");
        long[] unmet = new long[GoodsCategory.values().length];
        unmet[GoodsCategory.FOOD.ordinal()] = 10;
        unmet[GoodsCategory.HOUSEHOLD.ordinal()] = 90;
        TradeChoice.City poor = new TradeChoice.City(unmet, false, 0);
        assertEquals("glazier", TradeChoice.chooseFor(TRADES.all(), oven, poor, "bakery"), "a craft that gives the city more takes over");
        // The workstations are gone: the place resells.
        TradeChoice.Place bare = new TradeChoice.Place(Set.of(), true, false);
        assertEquals(Business.GENERAL_STORE, TradeChoice.chooseFor(TRADES.all(), bare, fed, "bakery"));
    }

    @Test
    void aFixedTradeHoldsAndATradeLastsAWeek() {
        assertEquals("mason", TradeChoice.next("bakery", "mason", "bakery", 0, 100));
        assertEquals("bakery", TradeChoice.next("bakery", "", "glazier", 10, 10 + TradeChoice.CHANGE_DAYS - 1));
        assertEquals("glazier", TradeChoice.next("bakery", "", "glazier", 10, 10 + TradeChoice.CHANGE_DAYS));
        assertEquals("bakery", TradeChoice.next("bakery", "", "bakery", 0, 100));
    }
}
