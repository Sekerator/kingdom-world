package io.github.sekerator.kingdomworld.core.production;

import java.util.List;
import java.util.Objects;

/**
 * A recipe as the production planner sees it: which profession makes what at which workstation, from what. The
 * game turns the recipes of every mod into these; an ingredient that accepts several items (a tag such as
 * "any planks") lists them all as choices.
 *
 * @param id          the recipe's id, for example {@code minecraft:iron_sword}
 * @param profession  the profession that makes it
 * @param output      item id of the result
 * @param outputCount items one making gives
 * @param parts       the ingredients
 * @param ticks       how long it cooks, for recipes that cook; 0 for recipes put together by hand
 */
public record RecipeOption(String id, RecipeKind kind, String profession, String output, int outputCount, List<Part> parts,
                           int ticks) {

    /**
     * One ingredient: any of the choices, {@code count} times.
     */
    public record Part(List<String> choices, int count) {
        public Part {
            choices = List.copyOf(choices);
            if (choices.isEmpty() || count < 1) {
                throw new IllegalArgumentException("An ingredient needs a choice and a count");
            }
        }

        public boolean accepts(String item) {
            return choices.contains(item);
        }
    }

    public RecipeOption {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(profession, "profession");
        Objects.requireNonNull(output, "output");
        parts = List.copyOf(parts);
        if (outputCount < 1 || parts.isEmpty()) {
            throw new IllegalArgumentException("Recipe " + id + " makes nothing or takes nothing");
        }
        ticks = Math.max(0, ticks);
    }

    /**
     * Whether an item is one of the ingredients.
     */
    public boolean uses(String item) {
        for (Part part : parts) {
            if (part.accepts(item)) {
                return true;
            }
        }
        return false;
    }
}
