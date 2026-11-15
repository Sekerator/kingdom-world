package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.production.ProductionRules;
import io.github.sekerator.kingdomworld.core.production.RecipeBook;
import io.github.sekerator.kingdomworld.core.production.RecipeKind;
import io.github.sekerator.kingdomworld.core.production.RecipeOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * The work of a workshop of citizens, without the world: which of its goods it makes, by which recipes, of which raw materials,
 * what one costs, and the making itself, which uses its raw materials up whole or does not happen.
 *
 * <p>A recipe is made at a workstation of the place of the workshop. An ingredient is taken from what the workshop holds, else
 * bought of the crown, else made by another recipe of the place, whichever costs less, two steps of recipes at most (planks of
 * logs for a door). What one item costs is its raw materials at what the workshop paid, its fuel, and the work of its masters at
 * the usual wage of their profession for the share of a working day the recipe takes.
 */
public final class ShopMaking {
    /** Steps of recipes a chain may have: the item, and its ingredients made of raw materials. */
    public static final int MAX_DEPTH = 2;
    /** No way to get an item. */
    static final long NONE = Long.MAX_VALUE / 8;

    private ShopMaking() {
    }

    /**
     * Fuel the crown can sell: the item, the ticks it burns in a furnace and what one costs.
     */
    public record Fuel(String item, int burn, long priceMilli) {
    }

    /**
     * What the work of a workshop depends on.
     *
     * @param book       every recipe
     * @param stations   the workstations of the place its trade makes recipes at
     * @param usable     recipes a workshop may make (no remainders that would be lost, nothing special)
     * @param crownMilli what one raw item costs of the crown, in thousandths, or below 0 when the crown cannot sell it
     * @param fuel       the fuel the crown sells, {@code null} for none
     * @param wage       the usual wage of a master a day
     */
    public record Context(RecipeBook book, Set<String> stations, Predicate<RecipeOption> usable, ToLongFunction<String> crownMilli, Fuel fuel,
                          long wage, ProductionRules rules) {
        public Context {
            stations = Set.copyOf(stations);
        }

        /**
         * Ticks one making takes a master.
         */
        public long ticks(RecipeOption recipe) {
            return rules.ticks(recipe, 0);
        }

        /**
         * The work of one making, at the usual wage, in thousandths of a coin.
         */
        public long laborMilli(RecipeOption recipe) {
            return Money.share(Money.multiplyCapped(Math.max(0, wage), 1_000), ticks(recipe), GatheringRules.WORK_TICKS_PER_DAY);
        }

        /**
         * The fuel one making burns, in thousandths of a coin, or {@link #NONE} without fuel.
         */
        long fuelMilli(RecipeOption recipe) {
            long ticks = fuelTicks(recipe);
            if (ticks == 0) {
                return 0;
            }
            if (fuel == null || fuel.burn() <= 0) {
                return NONE;
            }
            return Money.share(fuel.priceMilli(), ticks, fuel.burn());
        }

        List<RecipeOption> recipesFor(String item) {
            List<RecipeOption> found = new ArrayList<>();
            for (RecipeOption recipe : book.recipesFor(item)) {
                if (stations.contains(recipe.kind().station()) && usable.test(recipe)) {
                    found.add(recipe);
                }
            }
            return found;
        }
    }

    /**
     * Ticks of furnace burning one making needs, counted as in a furnace: a smoker or a blast furnace burns twice as fast.
     */
    public static long fuelTicks(RecipeOption recipe) {
        if (!recipe.kind().needsFuel()) {
            return 0;
        }
        long ticks = Math.max(ProductionRules.MIN_TICKS, recipe.ticks());
        return recipe.kind() == RecipeKind.SMELTING ? ticks : ticks * 2;
    }

    // ---------------------------------------------------------------- costs

    /**
     * Works out what items cost a workshop, remembering what it found.
     */
    public static final class Planner {
        private final Context context;
        private final Map<String, Long> units = new HashMap<>();

        public Planner(Context context) {
            this.context = context;
        }

        public Context context() {
            return context;
        }

        /**
         * What one of an item costs at a step of a chain: bought of the crown, or made by a recipe of the place while the chain
         * allows, whichever is less; {@link #NONE} when neither can be.
         */
        long unitMilli(String item, int depth, Set<String> chain) {
            String key = item + "@" + depth;
            Long known = units.get(key);
            if (known != null) {
                return known;
            }
            long best = context.crownMilli().applyAsLong(item);
            best = best < 0 ? NONE : best;
            if (depth < MAX_DEPTH && !chain.contains(item)) {
                Set<String> deeper = new HashSet<>(chain);
                deeper.add(item);
                for (RecipeOption recipe : context.recipesFor(item)) {
                    long cost = makingMilli(recipe, depth, deeper);
                    if (cost < NONE) {
                        best = Math.min(best, Money.share(cost, 1, recipe.outputCount()));
                    }
                }
            }
            units.put(key, best);
            return best;
        }

        /**
         * What one making of a recipe costs: its ingredients, its fuel and its work.
         */
        long makingMilli(RecipeOption recipe, int depth, Set<String> chain) {
            long sum = Money.addCapped(context.laborMilli(recipe), context.fuelMilli(recipe));
            if (sum >= NONE) {
                return NONE;
            }
            for (RecipeOption.Part part : recipe.parts()) {
                long cheapest = NONE;
                for (String choice : part.choices()) {
                    cheapest = Math.min(cheapest, unitMilli(choice, depth + 1, chain));
                }
                if (cheapest >= NONE) {
                    return NONE;
                }
                sum = Money.addCapped(sum, Money.multiplyCapped(cheapest, part.count()));
                if (sum >= NONE) {
                    return NONE;
                }
            }
            return sum;
        }

        /**
         * The cheapest recipe of the place that makes an item, if any can.
         */
        public Optional<RecipeOption> bestRecipe(String item) {
            return bestRecipe(item, 0, Set.of(item));
        }

        Optional<RecipeOption> bestRecipe(String item, int depth, Set<String> chain) {
            RecipeOption best = null;
            long bestUnit = NONE;
            for (RecipeOption recipe : context.recipesFor(item)) {
                long cost = makingMilli(recipe, depth, chain);
                if (cost >= NONE) {
                    continue;
                }
                long unit = Money.share(cost, 1, recipe.outputCount());
                if (best == null || unit < bestUnit) {
                    best = recipe;
                    bestUnit = unit;
                }
            }
            return Optional.ofNullable(best);
        }

        /**
         * What one of an item the workshop makes costs, its work included, or {@link #NONE}.
         */
        public long madeUnitMilli(String item) {
            Optional<RecipeOption> recipe = bestRecipe(item);
            if (recipe.isEmpty()) {
                return NONE;
            }
            return Money.share(makingMilli(recipe.get(), 0, Set.of(item)), 1, recipe.get().outputCount());
        }
    }

    /**
     * An item the workshop would make for a kind of goods.
     *
     * @param unitMilli what one costs, its work included
     */
    public record Choice(String item, long unitMilli, double worth) {
    }

    /**
     * The item of a list the workshop makes most cheaply for its worth, one it can sell at least a little above what it costs
     * under the ceiling of its price.
     */
    public static Optional<Choice> choose(Planner planner, List<String> items, ToDoubleFunction<String> worth, ToLongFunction<String> ceilingMilli) {
        Choice best = null;
        double bestRatio = Double.MAX_VALUE;
        for (String item : items) {
            long unit = planner.madeUnitMilli(item);
            if (unit >= NONE || !ShopPricing.worthBuying(Math.max(1, unit), ceilingMilli.applyAsLong(item))) {
                continue;
            }
            double value = Math.max(0.01, worth.applyAsDouble(item));
            double ratio = unit / value;
            if (best == null || ratio < bestRatio) {
                best = new Choice(item, unit, value);
                bestRatio = ratio;
            }
        }
        return Optional.ofNullable(best);
    }

    // ---------------------------------------------------------------- plans

    /**
     * Makings of one recipe.
     */
    public record Step(RecipeOption recipe, long makings) {
    }

    /**
     * How to make a number of an item.
     *
     * @param steps     the makings, the ingredients made before what they go into, the item last
     * @param buy       the raw materials to buy of the crown, beyond what the workshop holds, fuel included
     * @param held      what it takes of the raw materials the workshop holds
     * @param ticks     the work all the makings take
     * @param fuelTicks the burning of a furnace the makings take
     * @param unitMilli what one of the item costs, its work included
     */
    public record Plan(String item, int category, long count, List<Step> steps, Map<String, Long> buy, Map<String, Long> held, long ticks,
                       long fuelTicks, long unitMilli) {
        public Plan {
            steps = List.copyOf(steps);
            buy = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(buy));
            held = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(held));
        }

        /**
         * What the raw materials to buy cost, in whole coins, by the price of one of each.
         */
        public long buyCost(ToLongFunction<String> priceMilli) {
            long sum = 0;
            for (Map.Entry<String, Long> entry : buy.entrySet()) {
                sum = Money.addCapped(sum, ShopPricing.total(Math.max(0, priceMilli.applyAsLong(entry.getKey())), entry.getValue()));
            }
            return sum;
        }
    }

    /**
     * Plans the making of a number of an item.
     *
     * @param have     the raw materials the workshop holds, used first
     * @param fuelBank ticks of burning the workshop has of fuel already burned
     * @return the plan, empty when the place cannot make it
     */
    public static Optional<Plan> plan(Planner planner, String item, int category, long count, Map<String, Long> have, long fuelBank) {
        Optional<RecipeOption> recipe = planner.bestRecipe(item);
        if (recipe.isEmpty() || count <= 0) {
            return Optional.empty();
        }
        Map<String, Long> stock = new HashMap<>(have);
        Map<String, Long> buy = new LinkedHashMap<>();
        List<Step> steps = new ArrayList<>();
        long makings = (count + recipe.get().outputCount() - 1) / recipe.get().outputCount();
        if (!expand(planner, recipe.get(), makings, 0, Set.of(item), stock, buy, steps)) {
            return Optional.empty();
        }
        steps.add(new Step(recipe.get(), makings));
        Context context = planner.context();
        long fuel = 0;
        long ticks = 0;
        for (Step step : steps) {
            fuel = Money.addCapped(fuel, Money.multiplyCapped(fuelTicks(step.recipe()), step.makings()));
            ticks = Money.addCapped(ticks, Money.multiplyCapped(context.ticks(step.recipe()), step.makings()));
        }
        long owed = fuel - Math.max(0, fuelBank);
        if (owed > 0) {
            if (context.fuel() == null || context.fuel().burn() <= 0) {
                return Optional.empty();
            }
            long items = (owed + context.fuel().burn() - 1) / context.fuel().burn();
            String fuelItem = context.fuel().item();
            long left = stock.getOrDefault(fuelItem, 0L);
            long fromStock = Math.min(items, left);
            if (fromStock > 0) {
                stock.put(fuelItem, left - fromStock);
            }
            if (items > fromStock) {
                buy.merge(fuelItem, items - fromStock, Long::sum);
            }
        }
        Map<String, Long> held = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : have.entrySet()) {
            long taken = entry.getValue() - Math.min(entry.getValue(), stock.getOrDefault(entry.getKey(), 0L));
            if (taken > 0) {
                held.put(entry.getKey(), taken);
            }
        }
        return Optional.of(new Plan(item, category, makings * recipe.get().outputCount(), steps, buy, held, ticks, fuel,
                planner.madeUnitMilli(item)));
    }

    private static boolean expand(Planner planner, RecipeOption recipe, long makings, int depth, Set<String> chain, Map<String, Long> stock,
                                  Map<String, Long> buy, List<Step> steps) {
        Context context = planner.context();
        for (RecipeOption.Part part : recipe.parts()) {
            long total = Money.multiplyCapped(part.count(), makings);
            for (String choice : part.choices()) {
                long held = stock.getOrDefault(choice, 0L);
                long take = Math.min(total, held);
                if (take > 0) {
                    stock.put(choice, held - take);
                    total -= take;
                }
            }
            if (total <= 0) {
                continue;
            }
            String best = null;
            long bestCost = NONE;
            RecipeOption made = null;
            for (String choice : part.choices()) {
                long price = context.crownMilli().applyAsLong(choice);
                if (price >= 0 && price < bestCost) {
                    best = choice;
                    bestCost = price;
                    made = null;
                }
                if (depth + 1 < MAX_DEPTH && !chain.contains(choice)) {
                    Set<String> deeper = new HashSet<>(chain);
                    deeper.add(choice);
                    Optional<RecipeOption> sub = planner.bestRecipe(choice, depth + 1, deeper);
                    if (sub.isPresent()) {
                        long unit = Money.share(planner.makingMilli(sub.get(), depth + 1, deeper), 1, sub.get().outputCount());
                        if (unit < bestCost) {
                            best = choice;
                            bestCost = unit;
                            made = sub.get();
                        }
                    }
                }
            }
            if (best == null) {
                return false;
            }
            if (made == null) {
                buy.merge(best, total, Long::sum);
                continue;
            }
            long subMakings = (total + made.outputCount() - 1) / made.outputCount();
            Set<String> deeper = new HashSet<>(chain);
            deeper.add(best);
            if (!expand(planner, made, subMakings, depth + 1, deeper, stock, buy, steps)) {
                return false;
            }
            steps.add(new Step(made, subMakings));
            long left = subMakings * made.outputCount() - total;
            if (left > 0) {
                stock.merge(best, left, Long::sum);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- making

    /**
     * What a day of work made.
     *
     * @param count the items of the plan made
     * @param ticks the work it took
     * @param used  the raw materials used up, fuel included
     */
    public record Made(String item, long count, long ticks, Map<String, Long> used) {
    }

    /**
     * Makes what the raw materials and the time of the masters allow of a plan, step by step. A making takes its ingredients
     * whole from the raw materials of the workshop or does not happen; what a step makes for a later step goes among the raw
     * materials, and the item of the plan among the goods made by citizens, at the cost of what went into it and the work in it.
     *
     * @param workTicks the work the masters can do
     */
    public static Made make(Business shop, Plan plan, long workTicks, Context context) {
        long left = Math.max(0, workTicks);
        Map<String, Long> used = new LinkedHashMap<>();
        long made = 0;
        for (int index = 0; index < plan.steps().size(); index++) {
            Step step = plan.steps().get(index);
            boolean last = index == plan.steps().size() - 1;
            RecipeOption recipe = step.recipe();
            long ticks = Math.max(1, context.ticks(recipe));
            long count = Math.min(step.makings(), left / ticks);
            for (RecipeOption.Part part : recipe.parts()) {
                long held = 0;
                for (String choice : new java.util.LinkedHashSet<>(part.choices())) {
                    held = Money.addCapped(held, shop.input(choice));
                }
                count = Math.min(count, held / part.count());
            }
            long fuelEach = fuelTicks(recipe);
            if (fuelEach > 0) {
                long fuelItems = context.fuel() == null ? 0 : shop.input(context.fuel().item());
                // The fuel item may be an ingredient as well; it burns only what the ingredients leave.
                long burnable = shop.fuelTicks() + (context.fuel() == null ? 0 : Money.multiplyCapped(fuelItems, context.fuel().burn()));
                count = Math.min(count, burnable / fuelEach);
            }
            if (count <= 0) {
                continue;
            }
            long cost = 0;
            long labor = 0;
            for (RecipeOption.Part part : recipe.parts()) {
                long need = Money.multiplyCapped(part.count(), count);
                for (String choice : new java.util.LinkedHashSet<>(part.choices())) {
                    if (need <= 0) {
                        break;
                    }
                    Business.Lot lot = shop.inputs().get(choice);
                    if (lot == null) {
                        continue;
                    }
                    long take = shop.removeInput(choice, Math.min(need, lot.count()));
                    cost = Money.addCapped(cost, Money.multiplyCapped(lot.costMilli(), take));
                    labor = Money.addCapped(labor, Money.multiplyCapped(lot.laborMilli(), take));
                    used.merge(choice, take, Long::sum);
                    need -= take;
                }
            }
            if (fuelEach > 0) {
                long needTicks = Money.multiplyCapped(fuelEach, count);
                while (shop.fuelTicks() < needTicks && context.fuel() != null && shop.input(context.fuel().item()) > 0) {
                    Business.Lot lot = shop.inputs().get(context.fuel().item());
                    shop.removeInput(context.fuel().item(), 1);
                    shop.setFuel(shop.fuelTicks() + context.fuel().burn(), Money.addCapped(shop.fuelCostMilli(), lot.costMilli()));
                    used.merge(context.fuel().item(), 1L, Long::sum);
                }
                long taken = Math.min(needTicks, shop.fuelTicks());
                long fuelCost = shop.fuelTicks() <= 0 ? 0 : Money.share(shop.fuelCostMilli(), taken, shop.fuelTicks());
                shop.setFuel(shop.fuelTicks() - taken, shop.fuelCostMilli() - fuelCost);
                cost = Money.addCapped(cost, fuelCost);
            }
            labor = Money.addCapped(labor, Money.multiplyCapped(context.laborMilli(recipe), count));
            long out = Money.multiplyCapped(count, recipe.outputCount());
            long costEach = Money.share(cost, 1, out);
            long laborEach = Money.share(labor, 1, out);
            if (last) {
                shop.addMade(plan.item(), plan.category(), out, costEach, laborEach);
                made += out;
            } else {
                shop.addInput(recipe.output(), out, costEach, laborEach);
            }
            left -= count * ticks;
        }
        return new Made(plan.item(), made, Math.max(0, workTicks) - left, used);
    }

    // ---------------------------------------------------------------- stock and prices

    /**
     * The share of the strength of a new workshop a kind of goods gets, in percent: its share of the basket, or a tenth for
     * building materials, which the townspeople do not buy.
     */
    public static int sharePercent(GoodsCategory category, int[] basket) {
        if (!category.basket()) {
            return CraftRules.MATERIALS_SHARE_PERCENT;
        }
        return category.ordinal() < basket.length ? Math.max(0, basket[category.ordinal()]) : 0;
    }

    /**
     * How much of a kind of goods a workshop keeps, worth: the days of stock of what it sold of the kind lately (to townspeople,
     * players and the crown), or its strength by the share of the kind while it sold none, and what the trading house and the
     * crown asked of it on top.
     *
     * @param orders the worth asked of it by a trading house and work the crown paid for
     */
    public static long target(long averageSold, double strength, int sharePercent, int stockDays, long orders) {
        return Money.addCapped(CityDay.stockTarget(averageSold, strength, sharePercent, stockDays), Math.max(0, orders));
    }

    /**
     * The price a shop asks for goods without the tax, in thousandths: what goods made by citizens cost with the work in them, plus
     * the margin, never above the ceiling and never below what they cost and a little more; goods bought as before.
     */
    public static long askedMilli(Business.Lot lot, int marginPermille, long ceilingMilli) {
        return askedMilli(lot, marginPermille, ceilingMilli, 0);
    }

    /**
     * The price a shop asks for goods without the tax, in thousandths; goods its masters made it sells to the townspeople and
     * players no cheaper than their worth, as the market of the city values them, while they cost it less.
     *
     * @param worthMilli the worth of one, in thousandths; 0 to leave it out
     */
    public static long askedMilli(Business.Lot lot, int marginPermille, long ceilingMilli, long worthMilli) {
        if (!lot.made()) {
            return ShopPricing.askedMilli(lot.costMilli(), marginPermille, ceilingMilli);
        }
        long base = lot.baseMilli();
        long least = ShopPricing.priceMilli(base, ShopPricing.LEAST_MARGIN);
        long asked = Math.max(ShopPricing.priceMilli(base, marginPermille), Math.max(0, worthMilli));
        return Math.max(least, Math.min(asked, ceilingMilli));
    }

    /**
     * What a trading house pays a master for goods made by citizens, wholesale, in thousandths: the least the master sells for,
     * what they cost it and a little more, for goods beyond what its own city buys of it, which it would not sell otherwise; below
     * 0 when that is above the share of what the house gets abroad it may pay, and the house does not buy.
     *
     * @param exportMilli what one brings abroad, in thousandths
     * @param buyPercent  the most a trading house pays, in percent of that
     */
    public static long wholesaleMilli(Business.Lot lot, int marginPermille, long ceilingMilli, long exportMilli, int buyPercent) {
        long most = Money.share(Math.max(0, exportMilli), Math.max(0, buyPercent), 100);
        long least = ShopPricing.priceMilli(lot.baseMilli(), ShopPricing.LEAST_MARGIN);
        if (!lot.made() || least > most) {
            return -1;
        }
        return least;
    }

    /**
     * Work ticks the masters of a workshop do in a day: a working day each at the pace of its mood, half a day for the family of
     * a house.
     *
     * @param pace the paces of the masters, 1 for the usual
     */
    public static long workTicks(double pace, boolean family, int familyPercent) {
        double days = family ? familyPercent / 100.0 : Math.max(0.0, pace);
        return (long) Math.floor(GatheringRules.WORK_TICKS_PER_DAY * days);
    }
}
