package io.github.sekerator.kingdomworld.common.economy;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.production.Recipes;
import io.github.sekerator.kingdomworld.core.economy.ItemValues;
import io.github.sekerator.kingdomworld.core.production.RecipeBook;
import io.github.sekerator.kingdomworld.core.production.RecipeOption;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The worth of every item of the running server (see {@link ItemValues}): raw materials from the config by id and by
 * tag, made things from the recipes of the game and of every mod. Recipes of the crafting table, the furnaces, the
 * stonecutter and the smithing table are read the way the workshops read them; recipes of other mods' machines count
 * through the ingredients and the result they declare. Built on first use and again after data packs or tags reload.
 * Server thread only.
 */
public final class ItemPrices {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The worth of raw materials in a new config, in copper coins of a currency with the default coin values. */
    public static final List<String> DEFAULTS = List.of(
            "#forge:cobblestone=1", "#forge:stone=1", "minecraft:dirt=1", "#minecraft:sand=1", "#forge:gravel=1", "minecraft:netherrack=1",
            "minecraft:cobbled_deepslate=1", "minecraft:clay_ball=1", "minecraft:flint=1", "minecraft:snowball=1",
            "#minecraft:logs=4", "#minecraft:saplings=1", "minecraft:bamboo=1", "minecraft:sugar_cane=2", "minecraft:cactus=1",
            "minecraft:wheat=3", "minecraft:wheat_seeds=1", "minecraft:carrot=2", "minecraft:potato=2", "minecraft:beetroot=2",
            "minecraft:pumpkin=4", "minecraft:melon_slice=1", "minecraft:cocoa_beans=2", "minecraft:sweet_berries=1", "minecraft:apple=3",
            "minecraft:egg=2", "minecraft:beef=6", "minecraft:porkchop=6", "minecraft:mutton=5", "minecraft:chicken=4", "minecraft:rabbit=4",
            "minecraft:cod=4", "minecraft:salmon=5", "minecraft:tropical_fish=4", "minecraft:pufferfish=4",
            "minecraft:leather=6", "#minecraft:wool=4", "minecraft:string=2", "minecraft:feather=2", "minecraft:bone=2",
            "minecraft:rotten_flesh=1", "minecraft:spider_eye=4", "minecraft:gunpowder=8", "minecraft:slime_ball=10", "minecraft:ink_sac=3",
            "minecraft:honeycomb=4", "minecraft:ender_pearl=40", "minecraft:blaze_rod=50", "minecraft:nether_wart=4",
            "minecraft:coal=5", "#forge:raw_materials/copper=3", "#forge:ingots/copper=5", "#forge:raw_materials/iron=15",
            "#forge:ingots/iron=20", "#forge:raw_materials/gold=80", "#forge:ingots/gold=100", "#forge:raw_materials/silver=25",
            "#forge:ingots/silver=30", "#forge:dusts/redstone=4", "#forge:gems/lapis=8", "#forge:gems/quartz=6", "#forge:gems/amethyst=6",
            "#forge:gems/emerald=150", "#forge:gems/diamond=400", "minecraft:glowstone_dust=6", "minecraft:obsidian=10",
            "minecraft:prismarine_shard=6", "minecraft:netherite_scrap=500", "minecraft:ancient_debris=480", "minecraft:shulker_shell=200",
            "minecraft:nether_star=5000", "#forge:ores/coal=5", "#forge:ores/copper=6", "#forge:ores/iron=15", "#forge:ores/gold=80",
            "#forge:ores/redstone=16", "#forge:ores/lapis=32", "#forge:ores/diamond=400", "#forge:ores/emerald=150");

    /**
     * One entry of the config.
     *
     * @param target an item id, or a tag id after {@code #}
     */
    public record Entry(String target, double value) {
        public boolean isTag() {
            return target.startsWith("#");
        }
    }

    /**
     * What building the table found.
     *
     * @param errors recipes that could not be read
     */
    public record Report(int items, int recipes, int fromRecipes, int fromConfig, int byDefault, int errors) {
    }

    private static ItemValues table;
    private static Report report;

    private ItemPrices() {
    }

    /**
     * Reads one entry of the config, {@code item=value} or {@code #tag=value}.
     */
    public static Optional<Entry> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String[] sides = text.strip().split("=");
        if (sides.length != 2 || sides[0].isBlank()) {
            return Optional.empty();
        }
        String target = sides[0].strip();
        if (ResourceLocation.tryParse(target.startsWith("#") ? target.substring(1) : target) == null) {
            return Optional.empty();
        }
        try {
            double value = Double.parseDouble(sides[1].strip());
            return Double.isFinite(value) && value >= 0.0 && value <= ItemValues.MAX_VALUE ? Optional.of(new Entry(target, value))
                    : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Forgets the table; it is built again on next use.
     */
    public static void invalidate() {
        table = null;
        report = null;
    }

    public static ItemValues table(MinecraftServer server) {
        ItemValues current = table;
        if (current == null) {
            current = build(server);
            table = current;
        }
        return current;
    }

    /**
     * What the last build found; builds the table if needed.
     */
    public static Report report(MinecraftServer server) {
        table(server);
        return report;
    }

    /**
     * The worth of one item of the stack's kind.
     */
    public static ItemValues.Value value(MinecraftServer server, ItemStack stack) {
        return table(server).value(Recipes.itemId(stack.getItem()));
    }

    @SuppressWarnings("unchecked")
    private static ItemValues build(MinecraftServer server) {
        long start = System.nanoTime();
        KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
        List<Entry> entries = new ArrayList<>();
        for (String text : config.itemValues.get()) {
            parse(text).ifPresent(entries::add);
        }
        Map<String, Double> byId = new HashMap<>();
        List<Map.Entry<TagKey<Item>, Double>> byTag = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.isTag()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.target().substring(1));
                if (id != null) {
                    byTag.add(Map.entry(TagKey.create(Registries.ITEM, id), entry.value()));
                }
            } else {
                byId.putIfAbsent(entry.target(), entry.value());
            }
        }
        Set<String> items = new LinkedHashSet<>();
        Map<String, Double> base = new LinkedHashMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == Items.AIR) {
                continue;
            }
            String id = Recipes.itemId(item);
            items.add(id);
            Double value = byId.get(id);
            if (value == null) {
                ItemStack stack = new ItemStack(item);
                for (Map.Entry<TagKey<Item>, Double> tag : byTag) {
                    if (stack.is(tag.getKey())) {
                        value = tag.getValue();
                        break;
                    }
                }
            }
            if (value != null) {
                base.put(id, value);
            }
        }

        List<ItemValues.Recipe> recipes = new ArrayList<>();
        RecipeBook book = Recipes.book(server);
        for (RecipeBook.Entry entry : book.catalog()) {
            for (RecipeOption option : book.recipesFor(entry.item())) {
                List<ItemValues.Part> parts = new ArrayList<>();
                for (RecipeOption.Part part : option.parts()) {
                    parts.add(new ItemValues.Part(part.choices(), part.count()));
                }
                recipes.add(new ItemValues.Recipe(option.id(), option.output(), option.outputCount(), parts));
            }
        }
        int errors = 0;
        RegistryAccess access = server.registryAccess();
        Set<RecipeType<?>> known = Set.of(RecipeType.CRAFTING, RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING,
                RecipeType.CAMPFIRE_COOKING, RecipeType.STONECUTTING, RecipeType.SMITHING);
        for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            if (known.contains(recipe.getType()) || recipe instanceof CustomRecipe) {
                continue;
            }
            try {
                ItemValues.Recipe converted = convert(recipe, access);
                if (converted != null) {
                    recipes.add(converted);
                }
            } catch (RuntimeException e) {
                errors++;
                LOGGER.debug("KingdomWorld could not read the recipe {} for item values", recipe.getId(), e);
            }
        }
        ItemValues result = ItemValues.compute(base, recipes, items, config.defaultItemValue.get(), config.laborMarkup.get());
        Map<ItemValues.Source, Integer> counts = result.counts();
        report = new Report(result.size(), result.recipes(), counts.get(ItemValues.Source.RECIPE), counts.get(ItemValues.Source.BASE),
                counts.get(ItemValues.Source.DEFAULT), errors);
        LOGGER.info("KingdomWorld item values: {} items ({} from recipes, {} from the config, {} by default), {} recipes, {} unreadable, "
                        + "{} ms", report.items(), report.fromRecipes(), report.fromConfig(), report.byDefault(), report.recipes(), errors,
                (System.nanoTime() - start) / 1_000_000);
        return result;
    }

    /**
     * A recipe of another mod's machine, by the ingredients and the result it declares; {@code null} if it declares
     * none.
     */
    private static ItemValues.Recipe convert(Recipe<?> recipe, RegistryAccess access) {
        if (recipe.isSpecial()) {
            return null;
        }
        ItemStack result = recipe.getResultItem(access);
        if (result == null || result.isEmpty()) {
            return null;
        }
        List<ItemValues.Part> parts = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            Set<String> choices = new LinkedHashSet<>();
            for (ItemStack choice : ingredient.getItems()) {
                if (!choice.isEmpty()) {
                    choices.add(Recipes.itemId(choice.getItem()));
                }
            }
            parts.add(new ItemValues.Part(new ArrayList<>(choices), 1));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return new ItemValues.Recipe(recipe.getId().toString(), Recipes.itemId(result.getItem()), result.getCount(), parts);
    }
}
