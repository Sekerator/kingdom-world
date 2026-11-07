package io.github.sekerator.kingdomworld.core.economy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What items are worth, in the smallest unit of money: the base of prices and of the wealth of kingdoms.
 *
 * <p>Raw materials get their worth from the config (by item id or by tag). Everything made gets the cheapest way of
 * making it: the worth of the ingredients plus a markup for the work, divided by what one making gives. Items of
 * other mods get their worth the same way, through their tags and recipes. What has neither gets a default.
 *
 * <p>The table is worked out by relaxation: makings that became cheaper make the things made of them cheaper, until
 * nothing changes. A markup above zero makes every cycle (ingot to block to ingot) dearer than the way in, so cycles
 * end on their own; a hard limit of steps ends them in any case. Pure Java, built once per data pack load.
 */
public final class ItemValues {
    /** Largest worth of one item. */
    public static final double MAX_VALUE = 1.0e12;
    /** Steps of relaxation per recipe at most; protection against endless loops of rounding. */
    private static final int STEPS_PER_RECIPE = 64;
    private static final double EPSILON = 1.0e-9;

    /**
     * Where the worth of an item comes from.
     */
    public enum Source {
        /** Set in the config, by id or tag. */
        BASE,
        /** The cheapest recipe that makes it. */
        RECIPE,
        /** Nothing known: the default of the config. */
        DEFAULT
    }

    /**
     * One ingredient of a recipe: any of the choices, {@code count} of them. A part without choices cannot be
     * fulfilled, so its recipe never counts.
     */
    public record Part(List<String> choices, int count) {
        public Part {
            choices = List.copyOf(choices);
            if (count < 1) {
                throw new IllegalArgumentException("An ingredient needs a count");
            }
        }
    }

    /**
     * A way of making {@code count} of {@code output}.
     */
    public record Recipe(String id, String output, int count, List<Part> parts) {
        public Recipe {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(output, "output");
            parts = List.copyOf(parts);
        }
    }

    /**
     * The worth of an item and where it comes from.
     */
    public record Value(double amount, Source source) {
        /**
         * The worth in whole units: at least 1 for anything worth something.
         */
        public long units() {
            if (amount <= 0.0) {
                return 0L;
            }
            return Math.max(1L, Math.min(Money.MAX, Math.round(amount)));
        }
    }

    private final Map<String, Value> values;
    private final Map<Source, Integer> counts;
    private final int recipes;
    private final double defaultValue;

    private ItemValues(Map<String, Value> values, int recipes, double defaultValue) {
        this.values = Collections.unmodifiableMap(values);
        this.recipes = recipes;
        this.defaultValue = defaultValue;
        Map<Source, Integer> bySource = new EnumMap<>(Source.class);
        for (Source source : Source.values()) {
            bySource.put(source, 0);
        }
        values.values().forEach(value -> bySource.merge(value.source(), 1, Integer::sum));
        this.counts = Collections.unmodifiableMap(bySource);
    }

    /**
     * Works out the table.
     *
     * @param base         worth of raw materials by item id
     * @param recipes      every way of making things
     * @param items        every item there is; items no recipe makes and no base names get the default
     * @param defaultValue worth of an item nothing is known about
     * @param markup       share added to the ingredients for the work, for example 0.1
     */
    public static ItemValues compute(Map<String, Double> base, Collection<Recipe> recipes, Collection<String> items, double defaultValue,
                                     double markup) {
        double fallback = clamp(defaultValue);
        double factor = 1.0 + Math.max(0.0, Double.isFinite(markup) ? markup : 0.0);
        Map<String, Double> worth = new HashMap<>();
        Map<String, Source> sources = new HashMap<>();
        base.forEach((item, value) -> {
            if (value != null && Double.isFinite(value) && value >= 0.0) {
                worth.put(item, clamp(value));
                sources.put(item, Source.BASE);
            }
        });

        List<Recipe> usable = new ArrayList<>();
        Set<String> made = new HashSet<>();
        Map<String, List<Recipe>> byIngredient = new HashMap<>();
        Set<String> known = new HashSet<>(items);
        for (Recipe recipe : recipes) {
            if (recipe.count() < 1 || recipe.parts().isEmpty()) {
                continue;
            }
            usable.add(recipe);
            made.add(recipe.output());
            known.add(recipe.output());
            for (Part part : recipe.parts()) {
                for (String choice : part.choices()) {
                    known.add(choice);
                    byIngredient.computeIfAbsent(choice, k -> new ArrayList<>()).add(recipe);
                }
            }
        }
        // Raw materials nobody makes and the config does not name get the default before any recipe is looked at.
        for (String item : known) {
            if (!worth.containsKey(item) && !made.contains(item)) {
                worth.put(item, fallback);
                sources.put(item, Source.DEFAULT);
            }
        }

        relax(usable, byIngredient, worth, sources, factor, new ArrayDeque<>(usable));

        // What is made only in cycles without a way in gets the default, then the recipes that use it are looked at again.
        Deque<Recipe> again = new ArrayDeque<>();
        for (String item : known) {
            if (!worth.containsKey(item)) {
                worth.put(item, fallback);
                sources.put(item, Source.DEFAULT);
                again.addAll(byIngredient.getOrDefault(item, List.of()));
            }
        }
        if (!again.isEmpty()) {
            relax(usable, byIngredient, worth, sources, factor, again);
        }

        Map<String, Value> table = new LinkedHashMap<>();
        for (String item : known) {
            table.put(item, new Value(worth.get(item), sources.get(item)));
        }
        return new ItemValues(table, usable.size(), fallback);
    }

    private static void relax(List<Recipe> recipes, Map<String, List<Recipe>> byIngredient, Map<String, Double> worth,
                              Map<String, Source> sources, double factor, Deque<Recipe> queue) {
        long budget = (long) Math.max(1, recipes.size()) * STEPS_PER_RECIPE;
        Set<Recipe> queued = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        queued.addAll(queue);
        while (!queue.isEmpty() && budget-- > 0) {
            Recipe recipe = queue.poll();
            queued.remove(recipe);
            if (sources.get(recipe.output()) == Source.BASE) {
                continue;
            }
            double cost = cost(recipe, worth);
            if (!Double.isFinite(cost)) {
                continue;
            }
            double candidate = clamp(cost * factor / recipe.count());
            Double current = worth.get(recipe.output());
            // A recipe always beats the default; among recipes the cheapest wins.
            boolean better = current == null || sources.get(recipe.output()) == Source.DEFAULT || candidate < current - EPSILON;
            if (!better) {
                continue;
            }
            worth.put(recipe.output(), candidate);
            sources.put(recipe.output(), Source.RECIPE);
            for (Recipe user : byIngredient.getOrDefault(recipe.output(), List.of())) {
                if (queued.add(user)) {
                    queue.add(user);
                }
            }
        }
    }

    /**
     * What the ingredients of one making cost, or infinity if one of them has no worth yet.
     */
    private static double cost(Recipe recipe, Map<String, Double> worth) {
        double sum = 0.0;
        for (Part part : recipe.parts()) {
            double cheapest = Double.POSITIVE_INFINITY;
            for (String choice : part.choices()) {
                Double value = worth.get(choice);
                if (value != null && value < cheapest) {
                    cheapest = value;
                }
            }
            if (!Double.isFinite(cheapest)) {
                return Double.POSITIVE_INFINITY;
            }
            sum += cheapest * part.count();
        }
        return sum;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            return 0.0;
        }
        return Math.min(MAX_VALUE, value);
    }

    // ---------------------------------------------------------------- reading

    /**
     * The worth of an item; an item the table does not know gets the default.
     */
    public Value value(String item) {
        Value value = values.get(item);
        return value != null ? value : new Value(defaultValue, Source.DEFAULT);
    }

    public Optional<Value> known(String item) {
        return Optional.ofNullable(values.get(item));
    }

    public int size() {
        return values.size();
    }

    /**
     * How many items got their worth from each source.
     */
    public Map<Source, Integer> counts() {
        return counts;
    }

    /**
     * Recipes the table was worked out from.
     */
    public int recipes() {
        return recipes;
    }

    public Map<String, Value> all() {
        return values;
    }
}
