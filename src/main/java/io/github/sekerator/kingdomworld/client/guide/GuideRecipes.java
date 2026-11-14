package io.github.sekerator.kingdomworld.client.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sekerator.kingdomworld.core.guide.GuideRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The crafting grids of the guide, taken from the recipes of the game: those the server sent when the player is in a world, so
 * that a data pack that changes or removes a recipe changes the guide too. Without a world (the check of the layout on the
 * title screen) the recipes are read from the files of the mods and of the game.
 */
public final class GuideRecipes {
    private static final Map<String, Optional<Grid>> CACHE = new HashMap<>();
    private static RecipeManager cachedFor;

    /**
     * A crafting grid: nine slots row by row, each with the items that fit it (several for a tag, shown in turn).
     */
    public record Grid(List<List<ItemStack>> slots, ItemStack result, boolean shapeless) {
    }

    private GuideRecipes() {
    }

    /**
     * The grid of the crafting recipe {@code id}, or empty when the game has no such crafting recipe.
     */
    public static Optional<Grid> find(String id) {
        Minecraft minecraft = Minecraft.getInstance();
        RecipeManager manager = minecraft.level == null ? null : minecraft.level.getRecipeManager();
        if (manager != cachedFor) {
            CACHE.clear();
            cachedFor = manager;
        }
        return CACHE.computeIfAbsent(id, key -> manager != null ? fromGame(manager, key) : fromFiles(key));
    }

    /**
     * Forgets the grids, when the player leaves a world.
     */
    public static void clear() {
        CACHE.clear();
        cachedFor = null;
    }

    private static Optional<Grid> fromGame(RecipeManager manager, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return Optional.empty();
        }
        Optional<? extends Recipe<?>> found = manager.byKey(location);
        if (found.isEmpty() || found.get().getType() != RecipeType.CRAFTING) {
            return Optional.empty();
        }
        Recipe<?> recipe = found.get();
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int width = recipe instanceof IShapedRecipe<?> shaped ? shaped.getRecipeWidth() : GuideRecipe.SIDE;
        List<List<ItemStack>> slots = emptySlots();
        for (int i = 0; i < ingredients.size(); i++) {
            int row = i / Math.max(1, width);
            int column = i % Math.max(1, width);
            if (row < GuideRecipe.SIDE && column < GuideRecipe.SIDE) {
                slots.set(row * GuideRecipe.SIDE + column, Arrays.stream(ingredients.get(i).getItems()).map(ItemStack::copy).toList());
            }
        }
        ItemStack result = recipe.getResultItem(Minecraft.getInstance().level.registryAccess()).copy();
        return Optional.of(new Grid(slots, result, !(recipe instanceof IShapedRecipe<?>)));
    }

    private static Optional<Grid> fromFiles(String id) {
        return json(GuideRecipe.dataPath("recipes", id))
                .flatMap(json -> GuideRecipe.fromJson(json, GuideRecipes::tagItems))
                .map(recipe -> {
                    List<List<ItemStack>> slots = emptySlots();
                    for (int i = 0; i < recipe.slots().size(); i++) {
                        slots.set(i, recipe.slots().get(i).stream().map(GuideRecipes::stack).filter(stack -> !stack.isEmpty()).toList());
                    }
                    ItemStack result = stack(recipe.result());
                    result.setCount(recipe.count());
                    return new Grid(slots, result, recipe.shapeless());
                });
    }

    /**
     * The items of a tag: the tag of the game when it is bound, otherwise its files.
     */
    private static List<String> tagItems(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location != null) {
            List<String> bound = new ArrayList<>();
            BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, location)).ifPresent(set -> set.forEach(holder ->
                    bound.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString())));
            if (!bound.isEmpty()) {
                return bound;
            }
        }
        return GuideRecipe.tagItems(id, tag -> json(GuideRecipe.dataPath("tags/items", tag)));
    }

    /**
     * An item by its id, empty for an unknown one.
     */
    public static ItemStack stack(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(location);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static List<List<ItemStack>> emptySlots() {
        List<List<ItemStack>> slots = new ArrayList<>();
        for (int i = 0; i < GuideRecipe.SIDE * GuideRecipe.SIDE; i++) {
            slots.add(List.of());
        }
        return slots;
    }

    /**
     * A file of data of the mods or of the game ({@code data/minecraft/recipes/bread.json}).
     */
    private static Optional<JsonObject> json(String path) {
        try {
            for (IModFileInfo info : ModList.get().getModFiles()) {
                Path file = info.getFile().findResource(path.split("/"));
                if (Files.isRegularFile(file)) {
                    return Optional.of(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
                }
            }
            String[] parts = path.split("/", 3);
            IoSupplier<InputStream> vanilla = Minecraft.getInstance().getVanillaPackResources()
                    .getResource(PackType.SERVER_DATA, ResourceLocation.fromNamespaceAndPath(parts[1], parts[2]));
            if (vanilla != null) {
                try (InputStream stream = vanilla.get()) {
                    return Optional.of(JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
                }
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
