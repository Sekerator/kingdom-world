package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.production.ProductionRules;
import io.github.sekerator.kingdomworld.core.production.RecipeBook;
import io.github.sekerator.kingdomworld.core.production.RecipeKind;
import io.github.sekerator.kingdomworld.core.production.RecipeOption;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The work of a workshop of citizens: a making uses its raw materials up whole or does not happen, ingredients come from what the
 * workshop holds, then from the crown or from a recipe of the place, what one costs is its raw materials, its fuel and the work of
 * its masters, a shop never sells below what goods cost it and a little more, its target counts what a trading house asked of it,
 * and the mark of goods made by citizens never passes to goods bought.
 */
class ShopMakingTest {
    private static final String WHEAT = "minecraft:wheat";
    private static final String BREAD = "minecraft:bread";
    private static final String LOG = "minecraft:oak_log";
    private static final String PLANKS = "minecraft:oak_planks";
    private static final String DOOR = "minecraft:oak_door";
    private static final String SAND = "minecraft:sand";
    private static final String GLASS = "minecraft:glass";
    private static final String COAL = "minecraft:coal";
    /** The usual wage of a cook a day. */
    private static final long WAGE = 6;

    private static RecipeOption crafting(String id, String output, int count, String input, int each) {
        return new RecipeOption(id, RecipeKind.CRAFTING, "crafter", output, count, List.of(new RecipeOption.Part(List.of(input), each)), 0);
    }

    private static final RecipeBook BOOK = new RecipeBook(List.of(
            crafting("minecraft:bread", BREAD, 1, WHEAT, 3),
            crafting("minecraft:oak_planks", PLANKS, 4, LOG, 1),
            crafting("minecraft:oak_door", DOOR, 3, PLANKS, 6),
            new RecipeOption("minecraft:glass", RecipeKind.SMELTING, "smelter", GLASS, 1, List.of(new RecipeOption.Part(List.of(SAND), 1)), 200)));

    /** What the crown sells, in thousandths: wheat, logs, sand and coal, but no planks. */
    private static final Map<String, Long> CROWN = Map.of(WHEAT, 1_000L, LOG, 2_000L, SAND, 500L, COAL, 4_000L);

    private static ShopMaking.Context context(Set<String> stations, ShopMaking.Fuel fuel) {
        return new ShopMaking.Context(BOOK, stations, recipe -> true, item -> CROWN.getOrDefault(item, -1L), fuel, WAGE, ProductionRules.DEFAULT);
    }

    private static Business shop() {
        Business.Place place = new Business.Place(Business.PlaceKind.CRAFT_HOUSE, 1, 0, "minecraft:overworld", 0, 64, 0);
        return new Business(1, UUID.randomUUID(), 0, place, 0, 200);
    }

    @Test
    void aMakingUsesItsRawMaterialsUpWholeOrDoesNotHappen() {
        ShopMaking.Context context = context(Set.of("crafting_table"), null);
        ShopMaking.Planner planner = new ShopMaking.Planner(context);
        ShopMaking.Plan plan = ShopMaking.plan(planner, BREAD, GoodsCategory.FOOD.ordinal(), 2, new HashMap<>(), 0).orElseThrow();
        assertEquals(Map.of(WHEAT, 6L), plan.buy(), "two loaves take six wheat");
        Business shop = shop();
        shop.addInput(WHEAT, 5, 1_000);
        ShopMaking.Made made = ShopMaking.make(shop, plan, 1_000_000, context);
        assertEquals(1, made.count(), "five wheat make one loaf, not one and two thirds");
        assertEquals(2, shop.input(WHEAT), "the wheat of the loaf not made stays whole");
        assertEquals(1, shop.count(Business.key(BREAD, true)));
        assertEquals(0, shop.count(BREAD), "the loaf is made by citizens, not bought");
    }

    @Test
    void whatOneCostsIsItsRawMaterialsAndTheWorkInIt() {
        ShopMaking.Context context = context(Set.of("crafting_table"), null);
        Business shop = shop();
        shop.addInput(WHEAT, 3, 1_000);
        ShopMaking.Plan plan = ShopMaking.plan(new ShopMaking.Planner(context), BREAD, GoodsCategory.FOOD.ordinal(), 1, Map.of(WHEAT, 3L), 0)
                .orElseThrow();
        assertTrue(plan.buy().isEmpty(), "what the workshop holds is used first");
        ShopMaking.make(shop, plan, 1_000_000, context);
        Business.Lot bread = shop.goods().get(Business.key(BREAD, true));
        assertEquals(3_000, bread.costMilli(), "the raw materials at what the workshop paid");
        // A making by hand takes 200 ticks of a working day of 12 000: a sixtieth of the usual wage of 6.
        assertEquals(100, bread.laborMilli());
        assertEquals(3_100, bread.baseMilli());
        assertTrue(bread.made());
    }

    @Test
    void anIngredientTheCrownDoesNotSellIsMadeByARecipeOfThePlace() {
        ShopMaking.Context context = context(Set.of("crafting_table"), null);
        ShopMaking.Plan plan = ShopMaking.plan(new ShopMaking.Planner(context), DOOR, GoodsCategory.MATERIALS.ordinal(), 3, new HashMap<>(), 0)
                .orElseThrow();
        assertEquals(Map.of(LOG, 2L), plan.buy(), "six planks of two logs");
        assertEquals(2, plan.steps().size());
        Business shop = shop();
        shop.addInput(LOG, 2, 2_000);
        ShopMaking.Made made = ShopMaking.make(shop, plan, 1_000_000, context);
        assertEquals(3, made.count());
        assertEquals(2, shop.input(PLANKS), "the planks left over stay among the raw materials");
        assertEquals(0, shop.input(LOG));
        Business.Lot doors = shop.goods().get(Business.key(DOOR, true));
        // Six planks of the eight cost 3 000 and the work of planing; the doors add their own work.
        assertTrue(doors.costMilli() > 0 && doors.laborMilli() > 0);
        assertEquals(4_000 * 6 / 8 / 3, doors.costMilli());
    }

    @Test
    void aFurnaceBurnsFuelBoughtWithTheRawMaterials() {
        ShopMaking.Fuel coal = new ShopMaking.Fuel(COAL, 1_600, 4_000);
        ShopMaking.Context context = context(Set.of("furnace"), coal);
        ShopMaking.Plan eight = ShopMaking.plan(new ShopMaking.Planner(context), GLASS, GoodsCategory.MATERIALS.ordinal(), 8, new HashMap<>(), 0)
                .orElseThrow();
        assertEquals(Map.of(SAND, 8L, COAL, 1L), eight.buy());
        ShopMaking.Plan nine = ShopMaking.plan(new ShopMaking.Planner(context), GLASS, GoodsCategory.MATERIALS.ordinal(), 9, new HashMap<>(), 0)
                .orElseThrow();
        assertEquals(2L, nine.buy().get(COAL));
        Business shop = shop();
        shop.addInput(SAND, 8, 500);
        shop.addInput(COAL, 1, 4_000);
        assertEquals(8, ShopMaking.make(shop, eight, 1_000_000, context).count());
        assertEquals(0, shop.input(COAL));
        assertEquals(0, shop.fuelTicks(), "eight smeltings burn one coal whole");
        Business.Lot glass = shop.goods().get(Business.key(GLASS, true));
        assertEquals(500 + 500, glass.costMilli(), "the sand and an eighth of the coal");
        // Without fuel a furnace makes nothing.
        assertTrue(ShopMaking.plan(new ShopMaking.Planner(context(Set.of("furnace"), null)), GLASS, GoodsCategory.MATERIALS.ordinal(), 1,
                new HashMap<>(), 0).isEmpty());
    }

    @Test
    void theWorkOfTheMastersBoundsTheMaking() {
        ShopMaking.Context context = context(Set.of("crafting_table"), null);
        ShopMaking.Plan plan = ShopMaking.plan(new ShopMaking.Planner(context), BREAD, GoodsCategory.FOOD.ordinal(), 10, new HashMap<>(), 0)
                .orElseThrow();
        Business shop = shop();
        shop.addInput(WHEAT, 30, 1_000);
        assertEquals(4, ShopMaking.make(shop, plan, 800, context).count(), "800 ticks make four loaves of 200");
        assertEquals(18, shop.input(WHEAT));
        assertEquals(ShopMaking.workTicks(1.0, false, 50) / 2, ShopMaking.workTicks(0, true, 50), "a family works half a day");
    }

    @Test
    void aShopNeverSellsBelowWhatGoodsCostItAndALittleMore() {
        Business.Lot made = new Business.Lot(GoodsCategory.FOOD.ordinal(), 4, 3_000, 100, true);
        assertEquals(3_100L * 105 / 100, ShopMaking.askedMilli(made, 0, 1_000));
        assertEquals(3_100L * 120 / 100, ShopMaking.askedMilli(made, 200, 100_000));
        assertEquals(4_000, ShopMaking.askedMilli(made, 500, 4_000), "the ceiling bounds the margin");
        // An item it could not sell a little above what it costs under the ceiling is not made.
        ShopMaking.Planner planner = new ShopMaking.Planner(context(Set.of("crafting_table"), null));
        Optional<ShopMaking.Choice> dear = ShopMaking.choose(planner, List.of(BREAD), id -> 2.0, id -> 3_000);
        assertTrue(dear.isEmpty());
        Optional<ShopMaking.Choice> fair = ShopMaking.choose(planner, List.of(BREAD), id -> 3.0, id -> 4_500);
        assertEquals(BREAD, fair.orElseThrow().item());
    }

    @Test
    void aTradingHouseBuysWholesaleWithinItsShareOfTheExportAndOnlyWhatCitizensMade() {
        // Goods worth 3 export at 70 percent: 2.1, of which the house pays at most 80 percent, 1.68.
        long export = 2_100;
        Business.Lot cheap = new Business.Lot(GoodsCategory.FOOD.ordinal(), 5, 1_364, 116, true);
        // What it would not sell in its city it sells wholesale for what it cost and a little more, whatever its own price.
        assertEquals((1_480L * 1_050) / 1_000, ShopMaking.wholesaleMilli(cheap, 300, 4_500, export, 80));
        assertEquals((1_480L * 1_050) / 1_000, ShopMaking.wholesaleMilli(cheap, 50, 4_500, export, 80));
        // Raw materials at their worth: the master cannot sell for the share, and the house buys nothing.
        Business.Lot dear = new Business.Lot(GoodsCategory.FOOD.ordinal(), 5, 2_727, 116, true);
        assertEquals(-1, ShopMaking.wholesaleMilli(dear, 50, 4_500, export, 80));
        assertEquals(-1, ShopMaking.wholesaleMilli(new Business.Lot(GoodsCategory.FOOD.ordinal(), 5, 1_000), 50, 4_500, export, 80),
                "goods bought of the crown are never exported");
    }

    @Test
    void theTargetCountsWhatTheTradingHouseAskedOfIt() {
        assertEquals(30 + 40, ShopMaking.target(10, 150.0, 35, 3, 40));
        assertEquals(52 + 40, ShopMaking.target(-1, 150.0, 35, 3, 40), "a new trade stocks its strength by the share of the kind");
        assertEquals(CraftRules.MATERIALS_SHARE_PERCENT, ShopMaking.sharePercent(GoodsCategory.MATERIALS, new int[]{35, 15, 10, 20, 10}));
        assertEquals(35, ShopMaking.sharePercent(GoodsCategory.FOOD, new int[]{35, 15, 10, 20, 10}));
    }

    @Test
    void theMarkOfGoodsMadeByCitizensNeverPassesToGoodsBought() {
        Business shop = shop();
        shop.addMade(BREAD, GoodsCategory.FOOD.ordinal(), 3, 3_000, 100);
        shop.addGoods(BREAD, GoodsCategory.FOOD.ordinal(), 5, 2_000);
        assertEquals(3, shop.count(Business.key(BREAD, true)));
        assertEquals(5, shop.count(BREAD));
        assertEquals(8, shop.countItem(BREAD));
        assertFalse(shop.goods().get(BREAD).made(), "bought bread is not made by citizens");
        assertEquals(0, shop.goods().get(BREAD).laborMilli());
        shop.removeGoods(Business.key(BREAD, true), 3);
        assertEquals(0, shop.count(Business.key(BREAD, true)));
        assertEquals(5, shop.count(BREAD), "selling the made bread leaves the bought one");
        assertEquals(BREAD, Business.itemOf(Business.key(BREAD, true)));
        assertTrue(Business.isMade(Business.key(BREAD, true)));
    }

    @Test
    void sharesSplitMoneyWithoutLosingACoin() {
        assertEquals(List.of(50L, 50L), toList(Business.split(100, new int[]{2_000, 2_000})));
        assertEquals(List.of(51L, 50L), toList(Business.split(101, new int[]{2_000, 2_000})));
        // A hand of a lesser share, as the folk of a later stage will be.
        long[] parts = Business.split(1_000, new int[]{2_000, 2_000, 800});
        assertEquals(1_000, parts[0] + parts[1] + parts[2]);
        assertEquals(166, parts[2], "the coins left over go to the largest remainders, the first ones on a tie");
    }

    private static List<Long> toList(long[] values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }
}
