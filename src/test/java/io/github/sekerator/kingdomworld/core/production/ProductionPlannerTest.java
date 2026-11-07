package io.github.sekerator.kingdomworld.core.production;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Choosing recipes, tag ingredients, chains of sub-orders with their limits, reservations and quotas.
 */
class ProductionPlannerTest {
    private static final UUID KINGDOM = UUID.randomUUID();
    private static final Set<String> SMITHY = Set.of(Stations.CRAFTING_TABLE);
    private static final Set<String> FOUNDRY = Set.of(Stations.FURNACE);

    private static RecipeOption.Part part(int count, String... items) {
        return new RecipeOption.Part(List.of(items), count);
    }

    private static RecipeOption crafting(String id, String profession, String output, int count, RecipeOption.Part... parts) {
        return new RecipeOption(id, RecipeKind.CRAFTING, profession, output, count, List.of(parts), 0);
    }

    private static final RecipeBook BOOK = new RecipeBook(List.of(
            crafting("iron_sword", "smith", "iron_sword", 1, part(2, "iron_ingot"), part(1, "stick")),
            new RecipeOption("iron_ingot_from_smelting", RecipeKind.SMELTING, "smelter", "iron_ingot", 1, List.of(part(1, "raw_iron")), 200),
            crafting("iron_ingot_from_iron_block", "crafter", "iron_ingot", 9, part(1, "iron_block")),
            crafting("iron_ingot_from_nuggets", "crafter", "iron_ingot", 1, part(9, "iron_nugget")),
            crafting("iron_block", "crafter", "iron_block", 1, part(9, "iron_ingot")),
            crafting("iron_nugget", "crafter", "iron_nugget", 9, part(1, "iron_ingot")),
            crafting("stick", "crafter", "stick", 4, part(2, "oak_planks", "spruce_planks")),
            crafting("oak_planks", "crafter", "oak_planks", 4, part(1, "oak_log")),
            crafting("spruce_planks", "crafter", "spruce_planks", 4, part(1, "spruce_log"))));

    private final Map<String, Long> stock = new HashMap<>();
    private final Reservations reservations = new Reservations();
    private OrderBook orders;
    private Predicate<RecipeOption> workable;
    private int depth;

    @BeforeEach
    void setUp() {
        stock.clear();
        reservations.clear();
        orders = new OrderBook();
        workable = recipe -> true;
        depth = 3;
    }

    private ProductionPlanner.World world() {
        return new ProductionPlanner.World(KINGDOM, BOOK, item -> stock.getOrDefault(item, 0L), reservations, workable,
                item -> switch (item) {
                    case "raw_iron" -> "miner";
                    case "oak_log", "spruce_log" -> "lumberjack";
                    default -> "";
                }, depth, 100L);
    }

    private Order order(String item, Order.Kind kind, int amount) {
        return orders.add(KINGDOM, item, kind, amount, Order.Priority.NORMAL, 1L);
    }

    private List<Order> refresh() {
        return ProductionPlanner.refresh(orders, world());
    }

    private Order sub(Order parent, String item) {
        for (Order child : orders.children(KINGDOM, parent.id())) {
            if (child.item().equals(item)) {
                return child;
            }
        }
        return null;
    }

    private static Set<String> missingItems(Order order) {
        Set<String> items = new HashSet<>();
        for (Order.Missing missing : order.missing()) {
            items.add(missing.item());
        }
        return items;
    }

    // ---------------------------------------------------------------- recipes and tags

    @Test
    void anIngredientOfATagTakesAnyOfItsItems() {
        stock.put("spruce_planks", 2L);
        Order sticks = order("stick", Order.Kind.MAKE, 4);
        refresh();
        assertEquals(Order.Status.READY, sticks.status());
        ProductionPlanner.Job job = ProductionPlanner.nextJob(orders, world(), "crafter", SMITHY, UUID.randomUUID());
        assertNotNull(job);
        assertEquals(Map.of("spruce_planks", 2), job.picks());
        assertEquals(sticks.id(), job.order());
    }

    @Test
    void aRecipeWhoseIngredientsAreThereIsPreferred() {
        stock.put("iron_block", 1L);
        Order ingots = order("iron_ingot", Order.Kind.MAKE, 9);
        refresh();
        assertEquals("iron_ingot_from_iron_block", ingots.recipe());
        assertEquals(Order.Status.READY, ingots.status());

        stock.clear();
        stock.put("raw_iron", 3L);
        refresh();
        assertEquals("iron_ingot_from_smelting", ingots.recipe());
        assertEquals(Order.Status.READY, ingots.status());
    }

    @Test
    void aWorkerGetsOnlyWorkOfItsProfessionAndStations() {
        stock.put("raw_iron", 2L);
        order("iron_ingot", Order.Kind.MAKE, 2);
        refresh();
        assertNull(ProductionPlanner.nextJob(orders, world(), "smith", SMITHY, UUID.randomUUID()));
        assertNull(ProductionPlanner.nextJob(orders, world(), "smelter", SMITHY, UUID.randomUUID()), "no furnace at that post");
        assertNotNull(ProductionPlanner.nextJob(orders, world(), "smelter", FOUNDRY, UUID.randomUUID()));
    }

    // ---------------------------------------------------------------- chains

    @Test
    void missingIngredientsThatCanBeMadeBecomeSubOrders() {
        stock.put("raw_iron", 2L);
        stock.put("stick", 1L);
        Order sword = order("iron_sword", Order.Kind.MAKE, 1);
        refresh();
        assertEquals(Order.Status.WAITING_FOR_PARTS, sword.status());
        Order ingots = sub(sword, "iron_ingot");
        assertNotNull(ingots, "no sub-order for the ingots");
        assertEquals(2, ingots.amount());
        assertEquals("iron_ingot_from_smelting", ingots.recipe());
        assertEquals(Order.Status.READY, ingots.status());
        assertNull(ProductionPlanner.nextJob(orders, world(), "smith", SMITHY, UUID.randomUUID()), "the sword cannot be made yet");
        ProductionPlanner.Job smelting = ProductionPlanner.nextJob(orders, world(), "smelter", FOUNDRY, UUID.randomUUID());
        assertNotNull(smelting);
        assertEquals(ingots.id(), smelting.order());

        // The ingots are smelted: the sword can be made and the sub-order goes away.
        stock.put("raw_iron", 0L);
        stock.put("iron_ingot", 2L);
        refresh();
        assertEquals(Order.Status.READY, sword.status());
        assertTrue(orders.children(KINGDOM, sword.id()).isEmpty(), "the sub-order stayed after it was done");
        ProductionPlanner.Job job = ProductionPlanner.nextJob(orders, world(), "smith", SMITHY, UUID.randomUUID());
        assertNotNull(job);
        assertEquals(Map.of("iron_ingot", 2, "stick", 1), job.picks());
    }

    @Test
    void ingredientsNobodyMakesAreReportedWithWhoGathersThem() {
        Order sword = order("iron_sword", Order.Kind.MAKE, 1);
        refresh();
        assertEquals(Order.Status.WAITING_FOR_PARTS, sword.status());
        Map<String, String> gatherers = new HashMap<>();
        for (Order.Missing missing : sword.missing()) {
            gatherers.put(missing.item(), missing.gatherer());
        }
        assertEquals("miner", gatherers.get("raw_iron"));
        assertEquals("lumberjack", gatherers.get("oak_log"));
        Order ingots = sub(sword, "iron_ingot");
        assertNotNull(ingots);
        assertEquals(Order.Status.MISSING, ingots.status());
        assertEquals(Set.of("raw_iron"), missingItems(ingots));
        assertNotNull(sub(sub(sword, "stick"), "oak_planks"), "the sticks did not ask for planks");
    }

    @Test
    void chainsNeverLoopBetweenIngotsAndBlocks() {
        Order block = order("iron_block", Order.Kind.MAKE, 1);
        refresh();
        Order ingots = sub(block, "iron_ingot");
        assertNotNull(ingots);
        assertEquals("iron_ingot_from_smelting", ingots.recipe(), "ingots should come from ore, not from the block on the way");
        assertTrue(orders.children(KINGDOM, ingots.id()).isEmpty(), "the ingots asked for blocks or nuggets: " + orders.ordersOf(KINGDOM));
        assertEquals(2, orders.ordersOf(KINGDOM).size());
        assertEquals(Set.of("raw_iron"), missingItems(block));
        // Planning again changes nothing.
        refresh();
        assertEquals(2, orders.ordersOf(KINGDOM).size());
    }

    @Test
    void chainsStopAtTheirDepth() {
        depth = 1;
        Order sword = order("iron_sword", Order.Kind.MAKE, 1);
        refresh();
        Order sticks = sub(sword, "stick");
        assertNotNull(sticks);
        assertTrue(orders.children(KINGDOM, sticks.id()).isEmpty(), "a chain went deeper than allowed");
        assertEquals(Order.Status.MISSING, sticks.status());

        depth = 0;
        orders = new OrderBook();
        Order shallow = order("iron_sword", Order.Kind.MAKE, 1);
        refresh();
        assertTrue(orders.children(KINGDOM, shallow.id()).isEmpty());
        assertEquals(Order.Status.MISSING, shallow.status());
        assertEquals(Set.of("iron_ingot", "stick"), missingItems(shallow));
    }

    @Test
    void anItemWithoutWorkshopOrRecipeSaysSo() {
        workable = recipe -> !recipe.profession().equals("smith");
        Order sword = order("iron_sword", Order.Kind.MAKE, 1);
        Order dirt = order("dirt", Order.Kind.KEEP, 10);
        refresh();
        assertEquals(Order.Status.NO_WORKSHOP, sword.status());
        assertEquals("smith", sword.workshop());
        assertEquals(Stations.CRAFTING_TABLE, sword.workshopStation());
        assertEquals(Order.Status.NO_RECIPE, dirt.status());
    }

    // ---------------------------------------------------------------- reservations and quotas

    @Test
    void reservationsKeepTwoWorkersOffOneIngredient() {
        stock.put("oak_log", 1L);
        Order planks = order("oak_planks", Order.Kind.MAKE, 8);
        refresh();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ProductionPlanner.Job job = ProductionPlanner.nextJob(orders, world(), "crafter", SMITHY, first);
        assertNotNull(job);
        reservations.reserve(first, new Reservations.Hold(KINGDOM, job.order(), job.picks(), "oak_planks", 4));
        assertNull(ProductionPlanner.nextJob(orders, world(), "crafter", SMITHY, second), "the second worker got the same log");
        assertEquals(4, reservations.pending(KINGDOM, planks.id()));
        assertEquals(1, reservations.reserved(KINGDOM, "oak_log", second));
        assertEquals(0, reservations.reserved(KINGDOM, "oak_log", first), "a worker's own reservation counts for it");
        // The worker takes its log: it is no longer reserved, but no longer in stock either.
        reservations.taken(first, "oak_log", 1);
        stock.put("oak_log", 0L);
        assertEquals(0, reservations.reserved(KINGDOM, "oak_log", null));
        // The work is given up: the output is no longer expected.
        reservations.release(first);
        assertEquals(0, reservations.pending(KINGDOM, planks.id()));
        stock.put("oak_log", 1L);
        assertNotNull(ProductionPlanner.nextJob(orders, world(), "crafter", SMITHY, second));
    }

    @Test
    void aKeepOrderAsksOnlyForWhatIsMissing() {
        stock.put("stick", 6L);
        stock.put("oak_planks", 10L);
        Order sticks = order("stick", Order.Kind.KEEP, 8);
        refresh();
        assertEquals(2, ProductionPlanner.need(orders, world(), sticks));
        assertEquals(Order.Status.READY, sticks.status());
        UUID worker = UUID.randomUUID();
        reservations.reserve(worker, new Reservations.Hold(KINGDOM, sticks.id(), Map.of("oak_planks", 2), "stick", 4));
        assertEquals(0, ProductionPlanner.need(orders, world(), sticks), "work under way counts");
        assertNull(ProductionPlanner.nextJob(orders, world(), "crafter", SMITHY, UUID.randomUUID()));
        reservations.release(worker);
        stock.put("stick", 9L);
        refresh();
        assertEquals(Order.Status.SATISFIED, sticks.status());
        assertNull(ProductionPlanner.nextJob(orders, world(), "crafter", SMITHY, worker));
    }

    @Test
    void aKeepOrderProtectsItsStockFromOtherOrders() {
        stock.put("iron_ingot", 4L);
        stock.put("stick", 1L);
        order("iron_ingot", Order.Kind.KEEP, 4);
        Order sword = order("iron_sword", Order.Kind.MAKE, 1);
        refresh();
        assertEquals(Order.Status.WAITING_FOR_PARTS, sword.status(), "the sword ate into the ingots the king keeps");
        assertNull(ProductionPlanner.nextJob(orders, world(), "smith", SMITHY, UUID.randomUUID()));
        stock.put("iron_ingot", 6L);
        refresh();
        assertEquals(Order.Status.READY, sword.status());
    }

    @Test
    void finishedOrdersLeaveAndPausedOrdersRest() {
        stock.put("oak_planks", 4L);
        Order sticks = order("stick", Order.Kind.MAKE, 4);
        Order sword = order("iron_sword", Order.Kind.MAKE, 1);
        refresh();
        assertNotNull(sub(sword, "iron_ingot"));
        orders.setPaused(sword, true);
        refresh();
        assertEquals(Order.Status.PAUSED, sword.status());
        assertTrue(orders.children(KINGDOM, sword.id()).isEmpty(), "a paused order kept its sub-orders");
        orders.addMade(sticks, 4);
        List<Order> done = refresh();
        assertEquals(List.of(sticks), done);
        assertEquals(Order.Status.DONE, sticks.status());
        assertTrue(orders.get(KINGDOM, sticks.id()).isEmpty());
    }

    @Test
    void theAutoModeTakesTheFirstRecipeThatCanBeMade() {
        stock.put("raw_iron", 1L);
        List<RecipeOption> candidates = new ArrayList<>(BOOK.recipesFor("iron_ingot"));
        ProductionPlanner.Job job = ProductionPlanner.autoJob(orders, world(), candidates, FOUNDRY, UUID.randomUUID());
        assertNotNull(job);
        assertEquals(0, job.order());
        assertEquals("iron_ingot_from_smelting", job.recipe().id());
        stock.clear();
        assertNull(ProductionPlanner.autoJob(orders, world(), candidates, FOUNDRY, UUID.randomUUID()));
    }

    // ---------------------------------------------------------------- the book of orders

    @Test
    void removingAnOrderRemovesItsSubOrders() {
        Order sword = order("iron_sword", Order.Kind.MAKE, 1);
        refresh();
        assertTrue(orders.ordersOf(KINGDOM).size() > 2);
        List<Order> removed = orders.remove(KINGDOM, sword.id());
        assertEquals(sword, removed.get(0));
        assertTrue(orders.ordersOf(KINGDOM).isEmpty());
        assertTrue(orders.nextId(KINGDOM) > sword.id(), "ids are never reused");
    }

    @Test
    void restoringOrdersChecksIdsAndParents() {
        Order main = new Order(3, "stick", Order.Kind.KEEP, 8, 0, 1L);
        Order child = new Order(5, "oak_planks", Order.Kind.KEEP, 2, 3, 1L);
        OrderBook book = new OrderBook();
        book.restore(KINGDOM, List.of(main, child), 2);
        assertEquals(6, book.nextId(KINGDOM));
        assertEquals(List.of(child), book.children(KINGDOM, 3));
        assertThrows(IllegalArgumentException.class, () -> new OrderBook().restore(KINGDOM, List.of(child), 9));
        assertThrows(IllegalArgumentException.class, () -> new OrderBook().restore(KINGDOM, List.of(main, main), 9));
        assertThrows(IllegalArgumentException.class, () -> new Order(0, "stick", Order.Kind.MAKE, 1, 0, 0L));
    }

    @Test
    void amountsStayInRange() {
        Order order = order("stick", Order.Kind.MAKE, 0);
        assertEquals(1, order.amount());
        orders.setAmount(order, Integer.MAX_VALUE);
        assertEquals(Order.MAX_AMOUNT, order.amount());
        assertFalse(order.isSubOrder());
    }
}
