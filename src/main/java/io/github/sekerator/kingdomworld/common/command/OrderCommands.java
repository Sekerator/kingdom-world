package io.github.sekerator.kingdomworld.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.production.ProductionSettings;
import io.github.sekerator.kingdomworld.common.production.Recipes;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.production.RecipeBook;
import io.github.sekerator.kingdomworld.core.production.RecipeOption;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Operator commands of the king's orders and the recipes citizens make, mostly for tests: list, give and cancel
 * orders, list what a profession makes, and put the ingredients of an item into a kingdom's storehouses.
 */
final class OrderCommands {
    private static final String ORDERS = "commands.kingdomworld.admin.orders.";
    private static final String RECIPES = "commands.kingdomworld.admin.recipes.";
    private static final int LIST_LIMIT = 50;

    private static final DynamicCommandExceptionType NO_RECIPE = new DynamicCommandExceptionType(
            id -> Component.translatable(RECIPES + "unknown", id));
    private static final SuggestionProvider<CommandSourceStack> KINDS = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("keep", "make"), builder);
    private static final SuggestionProvider<CommandSourceStack> WORKSHOPS = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("smelter", "cook", "crafter", "smith"), builder);

    private OrderCommands() {
    }

    /**
     * {@code admin orders list|add|cancel}.
     */
    static LiteralArgumentBuilder<CommandSourceStack> orders() {
        return Commands.literal("orders")
                .then(Commands.literal("list")
                        .executes(context -> list(context, null))
                        .then(Commands.argument("kingdom", StringArgumentType.string())
                                .executes(context -> list(context, StringArgumentType.getString(context, "kingdom")))))
                .then(Commands.literal("add")
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, Order.MAX_AMOUNT))
                                        .then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
                                                .executes(context -> add(context, null))
                                                .then(Commands.argument("kingdom", StringArgumentType.string())
                                                        .executes(context -> add(context, StringArgumentType.getString(context, "kingdom"))))))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(context -> cancel(context, null))
                                .then(Commands.argument("kingdom", StringArgumentType.string())
                                        .executes(context -> cancel(context, StringArgumentType.getString(context, "kingdom"))))));
    }

    /**
     * {@code admin recipes list|stock}.
     */
    static LiteralArgumentBuilder<CommandSourceStack> recipes() {
        return Commands.literal("recipes")
                .then(Commands.literal("list")
                        .then(Commands.argument("profession", StringArgumentType.word()).suggests(WORKSHOPS)
                                .executes(context -> recipeList(context, ""))
                                .then(Commands.argument("filter", StringArgumentType.word())
                                        .executes(context -> recipeList(context, StringArgumentType.getString(context, "filter"))))))
                .then(Commands.literal("stock")
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(context -> stock(context, null))
                                .then(Commands.argument("kingdom", StringArgumentType.string())
                                        .executes(context -> stock(context, StringArgumentType.getString(context, "kingdom"))))));
    }

    // ---------------------------------------------------------------- orders

    private static Component itemName(String id) {
        Item item = Recipes.item(id);
        return item == null ? Component.literal(id) : new ItemStack(item).getHoverName();
    }

    private static int list(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = CitizenCommands.kingdom(source, name);
        Production.plan(source.getServer(), kingdom.id());
        List<Order> orders = KingdomWorldApi.state(source.getServer()).orders().ordersOf(kingdom.id());
        source.sendSuccess(() -> Component.translatable(ORDERS + "list.header", KingdomActions.kingdomName(kingdom), orders.size()), false);
        for (int i = 0; i < orders.size() && i < LIST_LIMIT; i++) {
            Order order = orders.get(i);
            Component parent = order.isSubOrder() ? Component.translatable(ORDERS + "list.sub", order.parent()) : Component.empty();
            source.sendSuccess(() -> Component.translatable(ORDERS + "list.entry", order.id(), order.item(), itemName(order.item()),
                    order.kind().id(), order.amount(), order.made(), order.status().id(), parent), false);
            for (Order.Missing missing : order.missing()) {
                source.sendSuccess(() -> Component.translatable(ORDERS + "list.missing", missing.item(), missing.count(),
                        missing.gatherer().isEmpty() ? "-" : missing.gatherer()), false);
            }
        }
        return orders.size();
    }

    private static int add(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = CitizenCommands.kingdom(source, name);
        String item = ResourceLocationArgument.getId(context, "item").toString();
        Order.Kind kind = Order.Kind.byId(StringArgumentType.getString(context, "kind").toLowerCase(Locale.ROOT)).orElse(Order.Kind.MAKE);
        int count = IntegerArgumentType.getInteger(context, "count");
        KingdomWorldState.Ordering[] ordering = new KingdomWorldState.Ordering[1];
        Outcome outcome = KingdomWorldApi.change(source.getServer(), "give an order", state -> {
            ordering[0] = state.order(null, kingdom.id(), item, kind, count, Order.Priority.NORMAL, KingdomWorldApi.gameTime(source.getServer()),
                    ProductionSettings.rules().maxOrders(), id -> Production.orderable(source.getServer(), id));
            return ordering[0].outcome();
        });
        if (!outcome.ok() || ordering[0] == null || ordering[0].order().isEmpty()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.ok() ? io.github.sekerator.kingdomworld.core.kingdom.KingdomError.NOT_ORDERABLE
                    : outcome.error()));
            return 0;
        }
        Order order = ordering[0].order().get();
        Production.plan(source.getServer(), kingdom.id());
        source.sendSuccess(() -> Component.translatable(ORDERS + "added", order.id(), item, count, kind.id(), KingdomActions.kingdomName(kingdom)),
                true);
        return (int) order.id();
    }

    private static int cancel(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = CitizenCommands.kingdom(source, name);
        long id = LongArgumentType.getLong(context, "id");
        Outcome outcome = KingdomWorldApi.change(source.getServer(), "cancel an order", state -> state.cancelOrder(null, kingdom.id(), id));
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ORDERS + "cancelled", id, KingdomActions.kingdomName(kingdom)), true);
        return 1;
    }

    // ---------------------------------------------------------------- recipes

    /**
     * What a profession makes; the filter {@code mods} leaves out the items of the base game, any other filter is
     * a part of the item id.
     */
    private static int recipeList(CommandContext<CommandSourceStack> context, String filter) {
        CommandSourceStack source = context.getSource();
        String profession = StringArgumentType.getString(context, "profession");
        RecipeBook book = Recipes.book(source.getServer());
        List<RecipeOption> recipes = new ArrayList<>();
        for (RecipeOption recipe : book.recipesOf(profession)) {
            boolean matches = filter.isEmpty() || (filter.equals("mods") ? !recipe.output().startsWith("minecraft:") : recipe.output().contains(filter));
            if (matches) {
                recipes.add(recipe);
            }
        }
        source.sendSuccess(() -> Component.translatable(RECIPES + "list.header", CitizenTexts.profession(profession), recipes.size()), false);
        for (int i = 0; i < recipes.size() && i < LIST_LIMIT; i++) {
            RecipeOption recipe = recipes.get(i);
            source.sendSuccess(() -> Component.translatable(RECIPES + "list.entry", recipe.output(), recipe.kind().id(), recipe.id(),
                    recipe.parts().size()), false);
        }
        return recipes.size();
    }

    /**
     * Puts what one making of an item takes into the storehouses: the first item each ingredient accepts, and fuel
     * for what cooks.
     */
    private static int stock(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = CitizenCommands.kingdom(source, name);
        String item = ResourceLocationArgument.getId(context, "item").toString();
        List<RecipeOption> recipes = Recipes.book(source.getServer()).recipesFor(item);
        if (recipes.isEmpty()) {
            throw NO_RECIPE.create(item);
        }
        RecipeOption recipe = recipes.get(0);
        List<String> stored = new ArrayList<>();
        for (RecipeOption.Part part : recipe.parts()) {
            Item choice = Recipes.item(part.choices().get(0));
            if (choice != null) {
                ItemStack rest = KingdomStorage.insert(source.getServer(), kingdom.id(), new ItemStack(choice, part.count()), null);
                stored.add(part.choices().get(0) + " x" + (part.count() - rest.getCount()));
            }
        }
        if (recipe.kind().needsFuel()) {
            ItemStack rest = KingdomStorage.insert(source.getServer(), kingdom.id(), new ItemStack(Items.COAL), null);
            stored.add("minecraft:coal x" + (1 - rest.getCount()));
        }
        String list = String.join(", ", stored);
        source.sendSuccess(() -> Component.translatable(RECIPES + "stocked", item, recipe.id(), list, KingdomActions.kingdomName(kingdom)), true);
        return stored.size();
    }
}
