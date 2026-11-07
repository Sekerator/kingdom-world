package io.github.sekerator.kingdomworld.core.production;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every recipe citizens may make, by result and by id, and the list of items that can be ordered. Built once from
 * the game's recipes and rebuilt when data packs reload; immutable.
 */
public final class RecipeBook {
    public static final RecipeBook EMPTY = new RecipeBook(List.of());

    /**
     * An item that can be ordered and the profession that makes it (the first of its recipes).
     */
    public record Entry(String item, String profession, RecipeKind kind) {
    }

    private final Map<String, List<RecipeOption>> byOutput;
    private final Map<String, RecipeOption> byId;
    private final List<Entry> catalog;

    public RecipeBook(List<RecipeOption> recipes) {
        List<RecipeOption> sorted = new ArrayList<>(recipes);
        // A stable order: the planner breaks ties by it.
        sorted.sort(Comparator.comparing(RecipeOption::id));
        Map<String, List<RecipeOption>> outputs = new LinkedHashMap<>();
        Map<String, RecipeOption> ids = new LinkedHashMap<>();
        for (RecipeOption recipe : sorted) {
            if (ids.putIfAbsent(recipe.id(), recipe) == null) {
                outputs.computeIfAbsent(recipe.output(), item -> new ArrayList<>()).add(recipe);
            }
        }
        Map<String, List<RecipeOption>> frozen = new LinkedHashMap<>();
        outputs.forEach((item, list) -> frozen.put(item, List.copyOf(list)));
        this.byOutput = Collections.unmodifiableMap(frozen);
        this.byId = Collections.unmodifiableMap(ids);
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, List<RecipeOption>> entry : frozen.entrySet()) {
            RecipeOption first = entry.getValue().get(0);
            entries.add(new Entry(entry.getKey(), first.profession(), first.kind()));
        }
        entries.sort(Comparator.comparing(Entry::item));
        this.catalog = List.copyOf(entries);
    }

    /**
     * Recipes that make the item, in a stable order.
     */
    public List<RecipeOption> recipesFor(String item) {
        return byOutput.getOrDefault(item, List.of());
    }

    public boolean canMake(String item) {
        return byOutput.containsKey(item);
    }

    public Optional<RecipeOption> recipe(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Recipes a profession makes.
     */
    public List<RecipeOption> recipesOf(String profession) {
        List<RecipeOption> result = new ArrayList<>();
        for (RecipeOption recipe : byId.values()) {
            if (recipe.profession().equals(profession)) {
                result.add(recipe);
            }
        }
        return result;
    }

    /**
     * Every item that can be ordered, by id.
     */
    public List<Entry> catalog() {
        return catalog;
    }

    public int size() {
        return byId.size();
    }
}
