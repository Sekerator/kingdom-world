package io.github.sekerator.kingdomworld.core.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A crafting recipe as a grid of three by three: what lies in every slot (the items that may lie there, several for a tag) and
 * what comes out.
 *
 * @param slots   nine slots row by row, each the ids of the items that fit it, empty for an empty slot
 * @param result  the id of the item made
 * @param count   how many are made
 */
public record GuideRecipe(List<List<String>> slots, String result, int count, boolean shapeless) {
    public static final int SIDE = 3;
    private static final int MAX_TAG_DEPTH = 8;

    public GuideRecipe {
        if (slots.size() != SIDE * SIDE) {
            throw new IllegalArgumentException("A crafting grid has nine slots: " + slots.size());
        }
        slots = slots.stream().map(List::copyOf).toList();
    }

    /**
     * Reads a crafting recipe of a data pack ({@code minecraft:crafting_shaped} or {@code minecraft:crafting_shapeless}).
     *
     * @param tag the items of an item tag by its id, empty when unknown
     * @return the recipe, or empty for a recipe of another kind or one that cannot be read
     */
    public static Optional<GuideRecipe> fromJson(JsonObject json, Function<String, List<String>> tag) {
        try {
            String type = json.has("type") ? json.get("type").getAsString() : "";
            List<List<String>> slots = new ArrayList<>();
            for (int i = 0; i < SIDE * SIDE; i++) {
                slots.add(List.of());
            }
            boolean shapeless;
            if (type.equals("minecraft:crafting_shaped")) {
                shapeless = false;
                JsonArray pattern = json.getAsJsonArray("pattern");
                JsonObject key = json.getAsJsonObject("key");
                if (pattern.size() > SIDE) {
                    return Optional.empty();
                }
                for (int row = 0; row < pattern.size(); row++) {
                    String line = pattern.get(row).getAsString();
                    if (line.length() > SIDE) {
                        return Optional.empty();
                    }
                    for (int column = 0; column < line.length(); column++) {
                        char symbol = line.charAt(column);
                        if (symbol != ' ') {
                            slots.set(row * SIDE + column, items(key.get(String.valueOf(symbol)), tag));
                        }
                    }
                }
            } else if (type.equals("minecraft:crafting_shapeless")) {
                shapeless = true;
                JsonArray ingredients = json.getAsJsonArray("ingredients");
                if (ingredients.size() > SIDE * SIDE) {
                    return Optional.empty();
                }
                for (int i = 0; i < ingredients.size(); i++) {
                    slots.set(i, items(ingredients.get(i), tag));
                }
            } else {
                return Optional.empty();
            }
            JsonObject result = json.getAsJsonObject("result");
            int count = result.has("count") ? result.get("count").getAsInt() : 1;
            return Optional.of(new GuideRecipe(slots, result.get("item").getAsString(), count, shapeless));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * The items an ingredient of a recipe takes: an item, a tag or a list of both.
     */
    private static List<String> items(JsonElement ingredient, Function<String, List<String>> tag) {
        Set<String> items = new LinkedHashSet<>();
        if (ingredient == null) {
            return List.of();
        }
        if (ingredient.isJsonArray()) {
            for (JsonElement element : ingredient.getAsJsonArray()) {
                items.addAll(items(element, tag));
            }
        } else {
            JsonObject object = ingredient.getAsJsonObject();
            if (object.has("item")) {
                items.add(object.get("item").getAsString());
            } else if (object.has("tag")) {
                items.addAll(tag.apply(object.get("tag").getAsString()));
            }
        }
        return List.copyOf(items);
    }

    /**
     * The items of a tag from the files of tags ({@code data/<namespace>/tags/items/<path>.json}), following the tags it holds.
     *
     * @param files the text of a tag file by the id of its tag, empty when there is none
     */
    public static List<String> tagItems(String id, Function<String, Optional<JsonObject>> files) {
        Set<String> items = new LinkedHashSet<>();
        collectTag(id, files, items, new LinkedHashSet<>(), 0);
        return List.copyOf(items);
    }

    private static void collectTag(String id, Function<String, Optional<JsonObject>> files, Set<String> items, Set<String> seen,
                                   int depth) {
        if (depth > MAX_TAG_DEPTH || !seen.add(id)) {
            return;
        }
        Optional<JsonObject> file = files.apply(id);
        if (file.isEmpty() || !file.get().has("values")) {
            return;
        }
        for (JsonElement value : file.get().getAsJsonArray("values")) {
            String entry = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
            if (entry.startsWith("#")) {
                collectTag(entry.substring(1), files, items, seen, depth + 1);
            } else {
                items.add(entry);
            }
        }
    }

    /**
     * The path of the file of a data pack that holds the recipe or tag {@code id} under {@code folder}.
     */
    public static String dataPath(String folder, String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return "data/" + namespace + "/" + folder + "/" + path + ".json";
    }
}
