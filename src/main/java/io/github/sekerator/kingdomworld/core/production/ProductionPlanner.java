package io.github.sekerator.kingdomworld.core.production;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * Plans the orders of a kingdom and hands out work.
 *
 * <p>For every order the planner chooses a recipe (one whose ingredients are there if it can, else one whose
 * missing ingredients can be made or gathered), works out what is missing, and asks for missing ingredients that
 * the kingdom can make by adding sub-orders: a sword asks for ingots, the ingots ask for smelted raw iron. Chains
 * stop at {@code maxDepth} and never come back to an item already on the way (an ingot made from a block made from
 * ingots), so they cannot loop. Ingredients nobody makes are reported with the profession that gathers them.
 *
 * <p>Items set aside by working citizens ({@link Reservations}) do not count as available, nor do items a keep
 * order of the king protects: an order never eats into a stock the king asked to keep.
 */
public final class ProductionPlanner {

    /**
     * What the planner needs to know about a kingdom.
     *
     * @param stock     items of the kind in the kingdom's storehouses
     * @param workable  whether the kingdom has a post that can make the recipe (its profession, its workstation)
     * @param gatherer  the gathering profession that brings an item, {@code ""} if none does
     * @param maxDepth  how many steps of sub-orders a chain may have
     */
    public record World(UUID kingdomId, RecipeBook book, ToLongFunction<String> stock, Reservations reservations,
                        Predicate<RecipeOption> workable, Function<String, String> gatherer, int maxDepth, long gameTime) {
        public World {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(book, "book");
            Objects.requireNonNull(stock, "stock");
            Objects.requireNonNull(reservations, "reservations");
            Objects.requireNonNull(workable, "workable");
            Objects.requireNonNull(gatherer, "gatherer");
        }
    }

    /**
     * One making of a recipe for a worker.
     *
     * @param order the order it is for, 0 for work of a post's own (the auto mode)
     * @param picks the items to take, by item id
     */
    public record Job(long order, RecipeOption recipe, Map<String, Integer> picks) {
    }

    private ProductionPlanner() {
    }

    // ---------------------------------------------------------------- availability

    /**
     * Amounts of items the king's keep orders protect from being used as ingredients.
     */
    static Map<String, Integer> protectedStock(OrderBook orders, UUID kingdomId) {
        Map<String, Integer> result = new HashMap<>();
        for (Order order : orders.mainOrdersOf(kingdomId)) {
            if (order.kind() == Order.Kind.KEEP && !order.paused()) {
                result.merge(order.item(), order.amount(), Math::max);
            }
        }
        return result;
    }

    /**
     * Items of a kind that may be used as ingredients: in stock, not set aside by others, not protected.
     */
    static long available(World world, Map<String, Integer> protectedStock, String item, UUID except) {
        long value = world.stock().applyAsLong(item) - world.reservations().reserved(world.kingdomId(), item, except)
                - protectedStock.getOrDefault(item, 0);
        return Math.max(0, value);
    }

    /**
     * Items of a kind in stock that are not set aside, as a keep order counts them.
     */
    static long inStock(World world, String item) {
        return Math.max(0, world.stock().applyAsLong(item) - world.reservations().reserved(world.kingdomId(), item, null));
    }

    /**
     * How many more items an order asks for right now.
     */
    public static int need(OrderBook orders, World world, Order order) {
        return need(world, protectedStock(orders, world.kingdomId()), order);
    }

    static int need(World world, Map<String, Integer> protectedStock, Order order) {
        long pending = world.reservations().pending(world.kingdomId(), order.id());
        long value;
        if (order.kind() == Order.Kind.MAKE) {
            value = (long) order.amount() - order.made() - pending;
        } else if (order.isSubOrder()) {
            value = order.amount() - available(world, protectedStock, order.item(), null) - pending;
        } else {
            value = order.amount() - inStock(world, order.item()) - pending;
        }
        return (int) Math.max(0, Math.min(Order.MAX_AMOUNT, value));
    }

    // ---------------------------------------------------------------- planning

    /**
     * Brings the statuses, recipes and sub-orders of a kingdom's orders up to date. Finished make orders leave the
     * book; so do sub-orders nothing needs any more.
     *
     * @return the finished orders
     */
    public static List<Order> refresh(OrderBook orders, World world) {
        UUID kingdom = world.kingdomId();
        Map<String, Integer> protectedStock = protectedStock(orders, kingdom);
        Set<Long> touched = new HashSet<>();
        List<Order> done = new ArrayList<>();
        for (Order order : orders.mainOrdersOf(kingdom)) {
            touched.add(order.id());
            if (order.kind() == Order.Kind.MAKE && order.made() >= order.amount()) {
                done.add(order);
                continue;
            }
            if (order.paused()) {
                order.plan(Order.Status.PAUSED, order.recipe(), List.of(), "", "");
                continue;
            }
            int need = need(world, protectedStock, order);
            if (need <= 0) {
                order.plan(order.kind() == Order.Kind.KEEP ? Order.Status.SATISFIED : Order.Status.WORKING, order.recipe(), List.of(), "", "");
                continue;
            }
            Set<String> visited = new HashSet<>();
            visited.add(order.item());
            resolve(orders, world, protectedStock, order, need, 0, visited, touched);
        }
        for (Order order : orders.ordersOf(kingdom)) {
            if (order.isSubOrder() && !touched.contains(order.id())) {
                orders.remove(kingdom, order.id());
            }
        }
        for (Order order : done) {
            order.plan(Order.Status.DONE, order.recipe(), List.of(), "", "");
            orders.remove(kingdom, order.id());
        }
        return done;
    }

    private static void resolve(OrderBook orders, World world, Map<String, Integer> protectedStock, Order order, int need, int depth,
                                Set<String> visited, Set<Long> touched) {
        List<RecipeOption> all = world.book().recipesFor(order.item());
        if (all.isEmpty()) {
            order.plan(Order.Status.NO_RECIPE, "", List.of(), "", "");
            return;
        }
        List<RecipeOption> options = new ArrayList<>();
        for (RecipeOption option : all) {
            if (world.workable().test(option)) {
                options.add(option);
            }
        }
        if (options.isEmpty()) {
            RecipeOption first = all.get(0);
            order.plan(Order.Status.NO_WORKSHOP, "", List.of(), first.profession(), first.kind().station());
            return;
        }
        RecipeOption chosen = choose(world, protectedStock, options, depth, visited);
        int crafts = (need + chosen.outputCount() - 1) / chosen.outputCount();
        boolean ready = true;
        boolean lackingWithoutSupply = false;
        Map<String, Order.Missing> missing = new LinkedHashMap<>();
        Map<String, Integer> used = new HashMap<>();
        List<Order> children = orders.children(world.kingdomId(), order.id());
        for (RecipeOption.Part part : chosen.parts()) {
            long total = (long) part.count() * crafts;
            String pick = pickChoice(world, protectedStock, part, total, depth, visited, used);
            // Every item the ingredient accepts counts, as at a crafting table: planks of oak and of spruce make sticks together.
            long available = availableFor(world, protectedStock, part, used, null);
            long others = available - Math.max(0, available(world, protectedStock, pick, null) - used.getOrDefault(pick, 0));
            use(world, protectedStock, part, pick, Math.min(total, available), used);
            boolean lacksForOne = available < part.count();
            if (lacksForOne) {
                ready = false;
            }
            if (available >= total) {
                continue;
            }
            int shortBy = (int) Math.min(Order.MAX_AMOUNT, total - Math.max(0, available));
            // A sub-order for the item picked asks only for what the other items of the ingredient leave missing.
            int subAmount = (int) Math.min(Order.MAX_AMOUNT, Math.max(1, total - others));
            if (depth < world.maxDepth() && !visited.contains(pick) && hasWorkableRecipe(world, pick)) {
                Order sub = null;
                for (Order child : children) {
                    if (child.item().equals(pick)) {
                        sub = child;
                    }
                }
                if (sub == null) {
                    sub = orders.addSub(world.kingdomId(), order, pick, subAmount, world.gameTime()).orElse(null);
                }
                if (sub != null) {
                    touched.add(sub.id());
                    orders.setAmount(sub, subAmount);
                    orders.setPriority(sub, order.priority());
                    int subNeed = need(world, protectedStock, sub);
                    if (subNeed > 0) {
                        Set<String> deeper = new HashSet<>(visited);
                        deeper.add(pick);
                        resolve(orders, world, protectedStock, sub, subNeed, depth + 1, deeper, touched);
                    } else {
                        sub.plan(Order.Status.WORKING, sub.recipe(), List.of(), "", "");
                    }
                    for (Order.Missing leaf : sub.missing()) {
                        missing.merge(leaf.item(), leaf, (a, b) -> a.count() >= b.count() ? a : b);
                    }
                    continue;
                }
            }
            if (lacksForOne) {
                lackingWithoutSupply = true;
            }
            missing.merge(pick, new Order.Missing(pick, shortBy, world.gatherer().apply(pick)),
                    (a, b) -> a.count() >= b.count() ? a : b);
        }
        Order.Status status;
        if (ready) {
            status = world.reservations().pending(world.kingdomId(), order.id()) > 0 ? Order.Status.WORKING : Order.Status.READY;
        } else {
            status = lackingWithoutSupply ? Order.Status.MISSING : Order.Status.WAITING_FOR_PARTS;
        }
        order.plan(status, chosen.id(), new ArrayList<>(missing.values()), "", "");
    }

    /**
     * Items an ingredient may take, of all the items it accepts: in stock, not set aside, not protected, not counted on already by
     * other ingredients of the same making.
     */
    static long availableFor(World world, Map<String, Integer> protectedStock, RecipeOption.Part part, Map<String, Integer> used,
                             UUID except) {
        long total = 0;
        for (String choice : new java.util.LinkedHashSet<>(part.choices())) {
            total += Math.max(0, available(world, protectedStock, choice, except) - used.getOrDefault(choice, 0));
        }
        return total;
    }

    /**
     * Counts {@code amount} items of an ingredient as used: of the item picked first, then of the others with most in stock.
     */
    private static void use(World world, Map<String, Integer> protectedStock, RecipeOption.Part part, String pick, long amount,
                            Map<String, Integer> used) {
        long left = amount;
        for (String choice : byStock(world, protectedStock, part, pick, used, null)) {
            if (left <= 0) {
                break;
            }
            long free = Math.max(0, available(world, protectedStock, choice, null) - used.getOrDefault(choice, 0));
            int taken = (int) Math.min(Integer.MAX_VALUE, Math.min(left, free));
            if (taken > 0) {
                used.merge(choice, taken, Integer::sum);
                left -= taken;
            }
        }
    }

    /**
     * The items an ingredient accepts, the one given first (if any), then by what may be used of them, most first; ties keep the
     * order of the recipe.
     */
    private static List<String> byStock(World world, Map<String, Integer> protectedStock, RecipeOption.Part part, String first,
                                        Map<String, Integer> used, UUID except) {
        List<String> choices = new ArrayList<>(new java.util.LinkedHashSet<>(part.choices()));
        Map<String, Long> free = new HashMap<>();
        for (String choice : choices) {
            free.put(choice, available(world, protectedStock, choice, except) - used.getOrDefault(choice, 0));
        }
        choices.sort(Comparator.comparing((String choice) -> !choice.equals(first)).thenComparing(choice -> -free.get(choice)));
        return choices;
    }

    /** The cost of an item that cannot be had at all. */
    static final int UNOBTAINABLE = 1_000_000;
    /** Looks into chains the cost estimate makes at most for one choice of recipe, so that wide mods stay cheap. */
    private static final int COST_BUDGET = 4_096;

    /**
     * Estimates how hard items are to get: 0 for an item in stock, 1 for one that is gathered, and for one the
     * kingdom makes one more than the cheapest of its recipes, whose cost is the sum of its ingredients. Chains stop
     * at the depth limit and never return to an item already on the way, so a cycle (ingots from a block made of
     * ingots) costs {@link #UNOBTAINABLE}.
     */
    private static final class Costs {
        private final World world;
        private final Map<String, Integer> protectedStock;
        private int budget = COST_BUDGET;

        Costs(World world, Map<String, Integer> protectedStock) {
            this.world = world;
            this.protectedStock = protectedStock;
        }

        /**
         * @param depth depth of the order that needs the item; the item may be made by a sub-order below it
         */
        int item(String item, int count, int depth, Set<String> visited) {
            if (available(world, protectedStock, item, null) >= count) {
                return 0;
            }
            int best = world.gatherer().apply(item).isEmpty() ? UNOBTAINABLE : 1;
            if (best > 1 && depth < world.maxDepth() && !visited.contains(item) && --budget > 0) {
                Set<String> deeper = new HashSet<>(visited);
                deeper.add(item);
                for (RecipeOption option : world.book().recipesFor(item)) {
                    if (world.workable().test(option)) {
                        best = Math.min(best, 1 + recipe(option, depth + 1, deeper));
                    }
                }
            }
            return best;
        }

        int part(RecipeOption.Part part, int depth, Set<String> visited) {
            if (availableFor(world, protectedStock, part, Map.of(), null) >= part.count()) {
                // Several items of the ingredient together are there.
                return 0;
            }
            int best = UNOBTAINABLE;
            for (String choice : part.choices()) {
                best = Math.min(best, item(choice, part.count(), depth, visited));
                if (best == 0) {
                    break;
                }
            }
            return best;
        }

        int recipe(RecipeOption option, int depth, Set<String> visited) {
            long sum = 0;
            for (RecipeOption.Part part : option.parts()) {
                sum += part(part, depth, visited);
            }
            return (int) Math.min(UNOBTAINABLE, sum);
        }
    }

    /**
     * The recipe to make an item by: the cheapest one, which is one whose ingredients are there if any, else one
     * whose missing ingredients take the shortest ways to make or gather; ties go to the first.
     */
    static RecipeOption choose(World world, Map<String, Integer> protectedStock, List<RecipeOption> options, int depth,
                               Set<String> visited) {
        Costs costs = new Costs(world, protectedStock);
        RecipeOption best = null;
        int bestCost = Integer.MAX_VALUE;
        for (RecipeOption option : options) {
            int cost = costs.recipe(option, depth, visited);
            if (cost < bestCost) {
                best = option;
                bestCost = cost;
            }
        }
        return best;
    }

    /**
     * Which of the items an ingredient accepts to count on: the one in stock if enough is there, else the one that
     * is easiest to get (in stock for at least one making, then the shortest way to make or gather it), the one
     * with most in stock among equals.
     */
    static String pickChoice(World world, Map<String, Integer> protectedStock, RecipeOption.Part part, long total, int depth,
                             Set<String> visited, Map<String, Integer> used) {
        String best = part.choices().get(0);
        long bestCount = -1;
        for (String choice : part.choices()) {
            long count = available(world, protectedStock, choice, null) - used.getOrDefault(choice, 0);
            if (count > bestCount) {
                best = choice;
                bestCount = count;
            }
        }
        if (bestCount >= total || part.choices().size() == 1) {
            return best;
        }
        Costs costs = new Costs(world, protectedStock);
        String cheapest = best;
        int cheapestCost = Integer.MAX_VALUE;
        long cheapestCount = -1;
        for (String choice : part.choices()) {
            long count = available(world, protectedStock, choice, null) - used.getOrDefault(choice, 0);
            int cost = count >= part.count() ? 0 : costs.item(choice, part.count(), depth, visited);
            if (cost < cheapestCost || cost == cheapestCost && count > cheapestCount) {
                cheapest = choice;
                cheapestCost = cost;
                cheapestCount = count;
            }
        }
        return cheapest;
    }

    private static boolean hasWorkableRecipe(World world, String item) {
        for (RecipeOption option : world.book().recipesFor(item)) {
            if (world.workable().test(option)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- handing out work

    /**
     * The items to take for one making of a recipe, or {@code null} if they are not all there.
     *
     * @param worker whose own reservation counts as available, may be {@code null}
     */
    static Map<String, Integer> picks(World world, Map<String, Integer> protectedStock, RecipeOption recipe, UUID worker) {
        Map<String, Integer> picks = new LinkedHashMap<>();
        for (RecipeOption.Part part : recipe.parts()) {
            // The item there is most of; when no one item is enough, several items of the ingredient together, as at a crafting
            // table.
            int left = part.count();
            for (String choice : byStock(world, protectedStock, part, null, picks, worker)) {
                long count = available(world, protectedStock, choice, worker) - picks.getOrDefault(choice, 0);
                int taken = (int) Math.min(left, Math.max(0, count));
                if (taken > 0) {
                    picks.merge(choice, taken, Integer::sum);
                    left -= taken;
                }
                if (left == 0) {
                    break;
                }
            }
            if (left > 0) {
                return null;
            }
        }
        return picks;
    }

    /**
     * The next making for a worker of a profession at a post with the given workstations: the most urgent order it
     * can work on whose ingredients are there; sub-orders go first, as their parents wait for them.
     *
     * @return {@code null} if there is nothing to make
     */
    public static Job nextJob(OrderBook orders, World world, String profession, Set<String> stations, UUID worker) {
        Map<String, Integer> protectedStock = protectedStock(orders, world.kingdomId());
        List<Order> candidates = new ArrayList<>();
        for (Order order : orders.ordersOf(world.kingdomId())) {
            if (!order.paused() && !order.recipe().isEmpty()
                    && (order.status() == Order.Status.READY || order.status() == Order.Status.WORKING)) {
                candidates.add(order);
            }
        }
        candidates.sort(Comparator.comparing((Order order) -> order.priority().ordinal()).reversed()
                .thenComparing(order -> order.isSubOrder() ? 0 : 1)
                .thenComparingLong(Order::id));
        for (Order order : candidates) {
            RecipeOption recipe = world.book().recipe(order.recipe()).orElse(null);
            if (recipe == null || !recipe.profession().equals(profession) || !stations.contains(recipe.kind().station())) {
                continue;
            }
            if (need(world, protectedStock, order) <= 0) {
                continue;
            }
            Map<String, Integer> picks = picks(world, protectedStock, recipe, worker);
            if (picks != null) {
                return new Job(order.id(), recipe, picks);
            }
        }
        return null;
    }

    /**
     * Work a post does of its own when no order needs it (the auto mode): the first of the candidate recipes whose
     * ingredients are there.
     *
     * @return {@code null} if none can be made
     */
    public static Job autoJob(OrderBook orders, World world, List<RecipeOption> candidates, Set<String> stations, UUID worker) {
        Map<String, Integer> protectedStock = protectedStock(orders, world.kingdomId());
        for (RecipeOption recipe : candidates) {
            if (!stations.contains(recipe.kind().station())) {
                continue;
            }
            Map<String, Integer> picks = picks(world, protectedStock, recipe, worker);
            if (picks != null) {
                return new Job(0, recipe, picks);
            }
        }
        return null;
    }

    /**
     * Why a worker of a profession at a post with the given workstations has nothing to make while orders are there.
     */
    public enum Wait {
        /** No order waits for it: there is none, or the others of its profession are making all of it. */
        NOTHING,
        /** An order waits for ingredients. */
        INGREDIENTS,
        /** Orders could be made, but at a workstation the post does not have. */
        WORKSTATION
    }

    /**
     * Why a worker has nothing to make, as the window of orders tells it: an order missing ingredients (or waiting for its
     * parts) waits for ingredients; an order ready to be made waits for a workstation the post lacks; an order all of which
     * others are making already waits for nothing.
     */
    public static Wait waitFor(OrderBook orders, World world, String profession, Set<String> stations) {
        Map<String, Integer> protectedStock = protectedStock(orders, world.kingdomId());
        boolean station = false;
        for (Order order : orders.ordersOf(world.kingdomId())) {
            if (order.paused() || order.recipe().isEmpty() || order.status() == Order.Status.SATISFIED) {
                continue;
            }
            RecipeOption recipe = world.book().recipe(order.recipe()).orElse(null);
            if (recipe == null || !recipe.profession().equals(profession)) {
                continue;
            }
            boolean ready = order.status() == Order.Status.READY || order.status() == Order.Status.WORKING;
            if (ready && need(world, protectedStock, order) <= 0) {
                continue;
            }
            if (ready && !stations.contains(recipe.kind().station())) {
                station = true;
                continue;
            }
            return Wait.INGREDIENTS;
        }
        return station ? Wait.WORKSTATION : Wait.NOTHING;
    }

    /**
     * Whether any order of the kingdom waits for a profession: it would have work if its ingredients were there.
     */
    public static boolean ordersWaitFor(OrderBook orders, World world, String profession) {
        for (Order order : orders.ordersOf(world.kingdomId())) {
            if (order.paused() || order.recipe().isEmpty()) {
                continue;
            }
            RecipeOption recipe = world.book().recipe(order.recipe()).orElse(null);
            if (recipe != null && recipe.profession().equals(profession) && order.status() != Order.Status.SATISFIED) {
                return true;
            }
        }
        return false;
    }
}
