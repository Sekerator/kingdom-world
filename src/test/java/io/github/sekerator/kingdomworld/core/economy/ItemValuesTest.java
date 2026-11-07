package io.github.sekerator.kingdomworld.core.economy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemValuesTest {
    private static final double DEFAULT = 5.0;

    private static ItemValues.Part part(int count, String... choices) {
        return new ItemValues.Part(List.of(choices), count);
    }

    private static ItemValues.Recipe recipe(String output, int count, ItemValues.Part... parts) {
        return new ItemValues.Recipe("test:" + output, output, count, List.of(parts));
    }

    @Test
    void madeThingsCostTheirCheapestRecipeWithAMarkup() {
        ItemValues values = ItemValues.compute(Map.of("log", 4.0, "iron", 20.0),
                List.of(recipe("planks", 4, part(1, "log")),
                        recipe("stick", 4, part(2, "planks")),
                        recipe("sword", 1, part(2, "iron"), part(1, "stick")),
                        recipe("sword", 1, part(1, "gold"), part(1, "stick"))),
                Set.of("log", "iron", "gold", "planks", "stick", "sword"), DEFAULT, 0.1);
        assertEquals(1.1, values.value("planks").amount(), 1e-9);
        assertEquals(ItemValues.Source.RECIPE, values.value("planks").source());
        assertEquals(2.2 * 1.1 / 4, values.value("stick").amount(), 1e-9);
        // Gold has no worth of its own: the default; that way is the cheaper one.
        double viaGold = (DEFAULT + values.value("stick").amount()) * 1.1;
        assertEquals(viaGold, values.value("sword").amount(), 1e-9);
        assertEquals(ItemValues.Source.DEFAULT, values.value("gold").source());
        assertEquals(1L, values.value("stick").units(), "anything worth something is worth at least one unit");
        assertEquals(ItemValues.Source.BASE, values.value("iron").source());
    }

    @Test
    void choicesOfATagCostTheCheapestChoice() {
        ItemValues values = ItemValues.compute(Map.of("oak", 4.0, "birch", 8.0),
                List.of(recipe("chest", 1, part(8, "oak", "birch"))), Set.of(), DEFAULT, 0.0);
        assertEquals(32.0, values.value("chest").amount(), 1e-9);
    }

    @Test
    void cyclesEndAndDoNotMakeThingsCheaper() {
        ItemValues values = ItemValues.compute(Map.of("ingot", 20.0),
                List.of(recipe("block", 1, part(9, "ingot")), recipe("ingot", 9, part(1, "block")),
                        recipe("nugget", 9, part(1, "ingot")), recipe("ingot", 1, part(9, "nugget"))),
                Set.of(), DEFAULT, 0.1);
        assertEquals(20.0, values.value("ingot").amount(), 1e-9, "a base worth wins over recipes");
        assertEquals(198.0, values.value("block").amount(), 1e-9);
        assertEquals(20.0 * 1.1 / 9, values.value("nugget").amount(), 1e-9);

        // Without any way in, a cycle gets the default and stops; even without markup.
        ItemValues closed = ItemValues.compute(Map.of(),
                List.of(recipe("a", 1, part(1, "b")), recipe("b", 1, part(1, "a"))), Set.of(), DEFAULT, 0.0);
        assertTrue(closed.value("a").amount() >= DEFAULT - 1e-9 && closed.value("a").amount() < 100.0);
        assertTrue(closed.value("b").amount() >= DEFAULT - 1e-9 && closed.value("b").amount() < 100.0);
    }

    @Test
    void unknownItemsAndImpossibleRecipesGetTheDefault() {
        ItemValues values = ItemValues.compute(Map.of(),
                List.of(recipe("nothing", 1, new ItemValues.Part(List.of(), 1)), recipe("empty", 0, part(1, "x"))),
                Set.of("lonely"), DEFAULT, 0.1);
        assertEquals(DEFAULT, values.value("lonely").amount(), 1e-9);
        assertEquals(ItemValues.Source.DEFAULT, values.value("lonely").source());
        assertEquals(DEFAULT, values.value("nothing").amount(), 1e-9, "a recipe with an ingredient nobody has does not count");
        assertEquals(DEFAULT, values.value("never:seen").amount(), 1e-9);
        assertEquals(ItemValues.Source.DEFAULT, values.value("never:seen").source());
    }

    @Test
    void manyRecipesAreWorkedOutQuickly() {
        List<ItemValues.Recipe> recipes = new ArrayList<>();
        for (int i = 1; i < 20_000; i++) {
            recipes.add(recipe("item" + i, 1, part(1, "item" + (i - 1)), part(1, "item" + (i / 2))));
            recipes.add(recipe("item" + (i - 1), 2, part(3, "item" + i)));
        }
        long start = System.nanoTime();
        ItemValues values = ItemValues.compute(Map.of("item0", 1.0), recipes, Set.of(), DEFAULT, 0.1);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(millis < 5_000, "took " + millis + " ms");
        assertTrue(values.size() >= 20_000);
        for (ItemValues.Value value : values.all().values()) {
            assertTrue(Double.isFinite(value.amount()) && value.amount() >= 0.0 && value.amount() <= ItemValues.MAX_VALUE);
        }
    }
}
