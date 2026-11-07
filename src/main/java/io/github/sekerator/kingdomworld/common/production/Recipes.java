package io.github.sekerator.kingdomworld.common.production;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.production.RecipeBook;
import io.github.sekerator.kingdomworld.core.production.RecipeKind;
import io.github.sekerator.kingdomworld.core.production.RecipeOption;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The recipes citizens make, read from the game's recipe manager, so that recipes of every mod and every data
 * pack count: crafting table recipes (shaped and shapeless), the stonecutter, upgrades at the smithing table and
 * everything the furnace, the blast furnace, the smoker and the campfire cook. Special recipes (fireworks, map
 * copies, dyed armor), recipes whose result is empty, whose ingredients depend on the data of an item, and
 * recipes of other mods' machines are left out. Each recipe goes to one profession: food to the cook, tools,
 * weapons and armor to the smith, ores to the smelter, everything else to the crafter.
 *
 * <p>The book is built on first use and rebuilt after data packs reload. Server thread only.
 */
public final class Recipes {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<ToolAction> TOOL_ACTIONS = List.of(ToolActions.AXE_DIG, ToolActions.PICKAXE_DIG, ToolActions.SHOVEL_DIG,
            ToolActions.HOE_DIG, ToolActions.SWORD_DIG, ToolActions.SHEARS_DIG, ToolActions.SHIELD_BLOCK, ToolActions.FISHING_ROD_CAST);

    /**
     * What is known about the recipes of the running server.
     *
     * @param ingredients what each part of each recipe accepts, by recipe id, in the order of its parts
     * @param auto        recipes the workshops make of their own, by profession, the preferred ones first
     */
    private record Cache(RecipeBook book, Map<String, Recipe<?>> recipes, Map<String, List<Predicate<ItemStack>>> ingredients,
                         Map<String, List<RecipeOption>> auto) {
    }

    private static Cache cache;

    private Recipes() {
    }

    /**
     * Forgets the recipes; they are read again on next use. Called when data packs reload and when the server
     * stops.
     */
    public static void invalidate() {
        cache = null;
    }

    private static Cache cache(MinecraftServer server) {
        Cache current = cache;
        if (current == null) {
            current = build(server);
            cache = current;
        }
        return current;
    }

    public static RecipeBook book(MinecraftServer server) {
        return cache(server).book();
    }

    /**
     * The game's recipe behind a recipe of the book.
     */
    public static Optional<Recipe<?>> recipe(MinecraftServer server, String id) {
        return Optional.ofNullable(cache(server).recipes().get(id));
    }

    /**
     * Whether a stack fits a part of a recipe of the book, as the recipe itself tests it (tags included).
     */
    public static boolean accepts(MinecraftServer server, RecipeOption recipe, int part, ItemStack stack) {
        List<Predicate<ItemStack>> tests = cache(server).ingredients().get(recipe.id());
        return tests != null && part >= 0 && part < tests.size() && tests.get(part).test(stack);
    }

    /**
     * Recipes a workshop of the profession makes of its own when nothing is ordered, the preferred first: a smelter
     * smelts ore and raw metal, a cook cooks raw food and bakes from crops.
     */
    public static List<RecipeOption> autoRecipes(MinecraftServer server, String profession) {
        return cache(server).auto().getOrDefault(profession, List.of());
    }

    // ---------------------------------------------------------------- reading the recipes

    private static Cache build(MinecraftServer server) {
        RegistryAccess access = server.registryAccess();
        List<RecipeOption> options = new ArrayList<>();
        Map<String, Recipe<?>> recipes = new HashMap<>();
        Map<String, List<Predicate<ItemStack>>> ingredients = new HashMap<>();
        int skipped = 0;
        for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            Converted converted;
            try {
                converted = convert(recipe, access);
            } catch (RuntimeException e) {
                // A broken recipe of some mod must not stop the others.
                LOGGER.debug("KingdomWorld skipped recipe {}: {}", recipe.getId(), e.toString());
                converted = null;
            }
            if (converted == null) {
                skipped++;
                continue;
            }
            options.add(converted.option());
            recipes.put(converted.option().id(), recipe);
            ingredients.put(converted.option().id(), converted.tests());
        }
        RecipeBook book = new RecipeBook(options);
        Map<String, List<RecipeOption>> auto = new HashMap<>();
        auto.put(Professions.SMELTER.id(), autoSmelting(book));
        auto.put(Professions.COOK.id(), autoCooking(book));
        LOGGER.info("KingdomWorld recipes: {} that citizens make ({} items to order), {} left out", book.size(), book.catalog().size(), skipped);
        return new Cache(book, Collections.unmodifiableMap(recipes), Collections.unmodifiableMap(ingredients), Collections.unmodifiableMap(auto));
    }

    private record Converted(RecipeOption option, List<Predicate<ItemStack>> tests) {
    }

    @Nullable
    private static Converted convert(Recipe<?> recipe, RegistryAccess access) {
        if (recipe.isSpecial() || recipe.isIncomplete() || recipe instanceof CustomRecipe) {
            return null;
        }
        ItemStack result = recipe.getResultItem(access);
        if (result == null || result.isEmpty()) {
            return null;
        }
        String id = recipe.getId().toString();
        String output = itemId(result.getItem());
        RecipeType<?> type = recipe.getType();
        if (type == RecipeType.CRAFTING && recipe instanceof CraftingRecipe) {
            List<Ingredient> grid = new ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (!ingredient.isEmpty()) {
                    grid.add(ingredient);
                }
            }
            return hand(id, RecipeKind.CRAFTING, professionFor(result, RecipeKind.CRAFTING), output, result.getCount(), grid);
        }
        if (type == RecipeType.STONECUTTING && recipe instanceof SingleItemRecipe) {
            return hand(id, RecipeKind.STONECUTTING, Professions.CRAFTER.id(), output, result.getCount(), recipe.getIngredients());
        }
        if (type == RecipeType.SMITHING && recipe instanceof SmithingTransformRecipe smithing) {
            return smithing(id, smithing, output, result.getCount());
        }
        if (recipe instanceof AbstractCookingRecipe cooking) {
            RecipeKind kind = type == RecipeType.SMELTING ? RecipeKind.SMELTING : type == RecipeType.BLASTING ? RecipeKind.BLASTING
                    : type == RecipeType.SMOKING ? RecipeKind.SMOKING : type == RecipeType.CAMPFIRE_COOKING ? RecipeKind.CAMPFIRE : null;
            if (kind == null || cooking.getIngredients().isEmpty()) {
                return null;
            }
            Ingredient input = cooking.getIngredients().get(0);
            // Melting tools and armor down to nuggets would eat the kingdom's gear.
            List<String> choices = choices(input, stack -> !stack.isDamageableItem());
            if (choices == null || choices.size() < input.getItems().length) {
                return null;
            }
            RecipeOption option = new RecipeOption(id, kind, professionFor(result, kind), output, result.getCount(),
                    List.of(new RecipeOption.Part(choices, 1)), cooking.getCookingTime());
            return new Converted(option, List.of(input));
        }
        return null;
    }

    /**
     * A recipe put together by hand: equal ingredients are counted together.
     */
    @Nullable
    private static Converted hand(String id, RecipeKind kind, String profession, String output, int count, List<Ingredient> grid) {
        Map<List<String>, Integer> counts = new LinkedHashMap<>();
        Map<List<String>, Ingredient> tests = new LinkedHashMap<>();
        for (Ingredient ingredient : grid) {
            List<String> choices = choices(ingredient, stack -> true);
            if (choices == null) {
                return null;
            }
            counts.merge(choices, 1, Integer::sum);
            tests.putIfAbsent(choices, ingredient);
        }
        if (counts.isEmpty()) {
            return null;
        }
        List<RecipeOption.Part> parts = new ArrayList<>();
        List<Predicate<ItemStack>> predicates = new ArrayList<>();
        counts.forEach((choices, times) -> {
            parts.add(new RecipeOption.Part(choices, times));
            predicates.add(tests.get(choices));
        });
        return new Converted(new RecipeOption(id, kind, profession, output, count, parts, 0), predicates);
    }

    /**
     * An upgrade at the smithing table: its template, base and addition are found by testing every item, since the
     * recipe does not list them.
     */
    @Nullable
    private static Converted smithing(String id, SmithingTransformRecipe recipe, String output, int count) {
        List<String> templates = new ArrayList<>();
        List<String> bases = new ArrayList<>();
        List<String> additions = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (recipe.isTemplateIngredient(stack)) {
                templates.add(itemId(item));
            }
            if (recipe.isBaseIngredient(stack)) {
                bases.add(itemId(item));
            }
            if (recipe.isAdditionIngredient(stack)) {
                additions.add(itemId(item));
            }
        }
        if (templates.isEmpty() || bases.isEmpty() || additions.isEmpty()) {
            return null;
        }
        List<RecipeOption.Part> parts = List.of(new RecipeOption.Part(templates, 1), new RecipeOption.Part(bases, 1),
                new RecipeOption.Part(additions, 1));
        List<Predicate<ItemStack>> tests = List.of(recipe::isTemplateIngredient, recipe::isBaseIngredient, recipe::isAdditionIngredient);
        return new Converted(new RecipeOption(id, RecipeKind.SMITHING, Professions.SMITH.id(), output, count, parts, 0), tests);
    }

    /**
     * The items an ingredient accepts that pass the filter; {@code null} if it depends on the data of the item
     * (enchantments, potions) or accepts nothing.
     */
    @Nullable
    private static List<String> choices(Ingredient ingredient, Predicate<ItemStack> filter) {
        if (!ingredient.isSimple()) {
            return null;
        }
        Set<String> items = new LinkedHashSet<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty() && filter.test(stack)) {
                items.add(itemId(stack.getItem()));
            }
        }
        return items.isEmpty() ? null : new ArrayList<>(items);
    }

    // ---------------------------------------------------------------- who makes what

    /**
     * Whether an item is a tool, a weapon or armor: by its kind (tiered items, armor, shields, bows), by the Forge
     * tags of tools and armor, by what it can do (tool actions) or because it wears out.
     */
    public static boolean isGear(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof TieredItem || item instanceof ArmorItem || item instanceof ShieldItem || item instanceof ProjectileWeaponItem
                || item instanceof TridentItem || item instanceof ShearsItem || item instanceof FishingRodItem
                || item instanceof FlintAndSteelItem) {
            return true;
        }
        if (stack.is(Tags.Items.TOOLS) || stack.is(Tags.Items.ARMORS)) {
            return true;
        }
        for (ToolAction action : TOOL_ACTIONS) {
            if (stack.canPerformAction(action)) {
                return true;
            }
        }
        return stack.isDamageableItem();
    }

    public static boolean isFood(ItemStack stack) {
        return stack.getItem().isEdible() || stack.getFoodProperties(null) != null;
    }

    static String professionFor(ItemStack result, RecipeKind kind) {
        if (kind == RecipeKind.SMOKING || kind == RecipeKind.CAMPFIRE) {
            return Professions.COOK.id();
        }
        if (kind == RecipeKind.SMELTING || kind == RecipeKind.BLASTING) {
            return isFood(result) ? Professions.COOK.id() : Professions.SMELTER.id();
        }
        if (kind == RecipeKind.SMITHING || isGear(result)) {
            return Professions.SMITH.id();
        }
        if (kind == RecipeKind.CRAFTING && isFood(result)) {
            return Professions.COOK.id();
        }
        return Professions.CRAFTER.id();
    }

    /**
     * Smelting the smelter does of its own: ore and raw metal (Forge tags {@code forge:ores} and
     * {@code forge:raw_materials}), the blast furnace first, as it is faster.
     */
    private static List<RecipeOption> autoSmelting(RecipeBook book) {
        List<RecipeOption> blasting = new ArrayList<>();
        List<RecipeOption> smelting = new ArrayList<>();
        for (RecipeOption option : book.recipesOf(Professions.SMELTER.id())) {
            if (!option.kind().cooks() || !allChoices(option, Recipes::isOreOrRawMetal)) {
                continue;
            }
            (option.kind() == RecipeKind.BLASTING ? blasting : smelting).add(option);
        }
        blasting.addAll(smelting);
        return List.copyOf(blasting);
    }

    private static boolean isOreOrRawMetal(Item item) {
        ItemStack stack = new ItemStack(item);
        return stack.is(Tags.Items.ORES) || stack.is(Tags.Items.RAW_MATERIALS);
    }

    /**
     * Cooking the cook does of its own: raw food cooked (smoker and campfire first), and baking from crops and eggs,
     * whenever the result feeds more than its ingredients did.
     */
    private static List<RecipeOption> autoCooking(RecipeBook book) {
        List<RecipeOption> first = new ArrayList<>();
        List<RecipeOption> then = new ArrayList<>();
        for (RecipeOption option : book.recipesOf(Professions.COOK.id())) {
            Item output = item(option.output());
            if (output == null) {
                continue;
            }
            int gained = nutrition(new ItemStack(output)) * option.outputCount();
            int spent = 0;
            boolean plain = true;
            for (RecipeOption.Part part : option.parts()) {
                Item choice = item(part.choices().get(0));
                if (choice == null) {
                    plain = false;
                    break;
                }
                ItemStack stack = new ItemStack(choice);
                spent += nutrition(stack) * part.count();
                plain &= isFood(stack) || stack.is(Tags.Items.CROPS) || stack.is(Tags.Items.EGGS) || stack.is(Tags.Items.MUSHROOMS)
                        || stack.is(Items.BOWL) || stack.is(Items.SUGAR);
            }
            if (!plain || gained <= spent) {
                continue;
            }
            (option.kind() == RecipeKind.SMOKING || option.kind() == RecipeKind.CAMPFIRE ? first : then).add(option);
        }
        first.addAll(then);
        return List.copyOf(first);
    }

    private static int nutrition(ItemStack stack) {
        var food = stack.getFoodProperties(null);
        return food == null ? 0 : Math.max(0, food.getNutrition());
    }

    private static boolean allChoices(RecipeOption option, Predicate<Item> test) {
        for (RecipeOption.Part part : option.parts()) {
            for (String choice : part.choices()) {
                Item item = item(choice);
                if (item == null || !test.test(item)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- ids

    public static String itemId(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "" : key.toString();
    }

    @Nullable
    public static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null || !ForgeRegistries.ITEMS.containsKey(key) ? null : ForgeRegistries.ITEMS.getValue(key);
        return item == null || item == Items.AIR ? null : item;
    }
}
