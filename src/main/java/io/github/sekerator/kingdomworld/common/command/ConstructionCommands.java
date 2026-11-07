package io.github.sekerator.kingdomworld.common.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionSettings;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Operator commands of construction under {@code /kingdomworld admin build}: the templates and whether they can be built,
 * the constructions and buildings of a kingdom with exact numbers, laying a construction out, finishing it at once (the
 * register remembers an operator did), cancelling it, giving its kingdom the materials it still needs and placing what
 * was paid for now. Everything goes through the same calls the game uses.
 */
public final class ConstructionCommands {
    private static final String KEY = "commands.kingdomworld.admin.build.";
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.economy.no_kingdom", name));
    private static final DynamicCommandExceptionType NO_CONSTRUCTION = new DynamicCommandExceptionType(
            id -> Component.translatable(KEY + "unknown", id));
    private static final DynamicCommandExceptionType NO_TEMPLATE = new DynamicCommandExceptionType(
            id -> Component.translatable(KEY + "unknown_template", id));
    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);
    private static final SuggestionProvider<CommandSourceStack> TEMPLATES = (context, builder) ->
            SharedSuggestionProvider.suggestResource(BuildingTemplates.all().stream().map(BuildingTemplate::id), builder);

    private ConstructionCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("build")
                .then(Commands.literal("templates").executes(context -> templates(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), null))
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> list(context.getSource(), kingdom(context)))))
                .then(Commands.literal("buildings")
                        .executes(context -> buildings(context.getSource(), null))
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> buildings(context.getSource(), kingdom(context)))))
                .then(Commands.literal("place")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("template", ResourceLocationArgument.id()).suggests(TEMPLATES)
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> place(context, 0, false))
                                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                                                        .executes(context -> place(context, IntegerArgumentType.getInteger(context, "rotation"), false))
                                                        .then(Commands.argument("mirror", BoolArgumentType.bool())
                                                                .executes(context -> place(context, IntegerArgumentType.getInteger(context, "rotation"),
                                                                        BoolArgumentType.getBool(context, "mirror")))))))))
                .then(Commands.literal("finish")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(context -> finish(context.getSource(), construction(context)))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(context -> cancel(context.getSource(), construction(context)))))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(context -> info(context.getSource(), construction(context)))))
                .then(Commands.literal("stock")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(context -> stock(context.getSource(), construction(context)))))
                .then(Commands.literal("tick").executes(context -> tick(context.getSource())));
    }

    private static Kingdom kingdom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "kingdom").strip();
        return KingdomWorldApi.findKingdom(context.getSource().getServer(), name)
                .orElseThrow(() -> NO_KINGDOM.create(KingdomNames.normalize(name)));
    }

    private static Construction construction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        long id = LongArgumentType.getLong(context, "id");
        return KingdomWorldApi.state(context.getSource().getServer()).construction().get(id).orElseThrow(() -> NO_CONSTRUCTION.create(id));
    }

    private static String name(MinecraftServer server, java.util.UUID kingdom) {
        return KingdomWorldApi.kingdom(server, kingdom).map(Kingdom::name).orElse("?");
    }

    // ---------------------------------------------------------------- looking

    private static int templates(CommandSourceStack source) {
        List<BuildingTemplate> all = BuildingTemplates.sorted();
        source.sendSuccess(() -> Component.translatable(KEY + "templates", all.size()), false);
        for (BuildingTemplate template : all) {
            Component problem = BuildingTemplates.problem(template, ConstructionSettings.rules());
            Component state = problem == null ? Component.translatable(KEY + "available") : problem;
            source.sendSuccess(() -> Component.translatable(KEY + "templates.entry", template.id().toString(), template.name(), template.sizeX(),
                    template.sizeY(), template.sizeZ(), template.cost().blocks(), template.cost().total(), state), false);
        }
        return all.size();
    }

    private static int list(CommandSourceStack source, Kingdom only) {
        MinecraftServer server = source.getServer();
        KingdomWorldState state = KingdomWorldApi.state(server);
        int count = 0;
        for (Construction construction : state.construction().all()) {
            if (only != null && !construction.kingdomId().equals(only.id())) {
                continue;
            }
            count++;
            Optional<BuildPlan> plan = ConstructionWork.plan(construction);
            int placed = plan.map(p -> p.placed(construction)).orElse(0);
            int total = plan.map(BuildPlan::places).orElse(0);
            source.sendSuccess(() -> Component.translatable(KEY + "list.entry", construction.id(), name(server, construction.kingdomId()),
                    construction.templateId(), construction.anchor().coordinates(), Component.translatable("kingdomworld.construction.status."
                            + construction.status().id()), placed, total, construction.pending().size(), construction.priority().id(),
                    construction.paused()), false);
        }
        int shown = count;
        source.sendSuccess(() -> Component.translatable(KEY + "list", shown), false);
        return count;
    }

    private static int buildings(CommandSourceStack source, Kingdom only) {
        MinecraftServer server = source.getServer();
        int count = 0;
        for (Building building : KingdomWorldApi.state(server).construction().buildings()) {
            if (only != null && !building.kingdomId().equals(only.id())) {
                continue;
            }
            count++;
            source.sendSuccess(() -> Component.translatable(KEY + "buildings.entry", building.id(), name(server, building.kingdomId()),
                    building.templateId(), building.type(), building.anchor().coordinates(), building.integrity(), building.admin()), false);
        }
        int shown = count;
        source.sendSuccess(() -> Component.translatable(KEY + "buildings", shown), false);
        return count;
    }

    /**
     * Exact numbers of a construction: steps, blocks placed and paid for, materials at the site, still needed and set aside.
     */
    private static int info(CommandSourceStack source, Construction construction) {
        MinecraftServer server = source.getServer();
        Optional<BuildPlan> plan = ConstructionWork.plan(construction);
        if (plan.isEmpty()) {
            source.sendFailure(Component.translatable("kingdomworld.construction.status." + construction.status().id()));
            return 0;
        }
        BuildPlan steps = plan.get();
        Map<String, Integer> remaining = new TreeMap<>(steps.remaining(construction));
        Map<String, Integer> delivered = new TreeMap<>(construction.delivered());
        Map<String, Integer> reserved = new TreeMap<>();
        var hold = Production.reservations().hold(ConstructionWork.holdKey(construction));
        if (hold != null) {
            reserved.putAll(hold.items());
        }
        source.sendSuccess(() -> Component.translatable(KEY + "info", construction.id(), construction.templateId(), construction.anchor().coordinates(),
                construction.doneCount(), steps.size(), steps.placed(construction), steps.places(), construction.pending().size(),
                Component.translatable("kingdomworld.construction.status." + construction.status().id())), false);
        source.sendSuccess(() -> Component.translatable(KEY + "info.items", text(remaining), text(delivered), text(reserved)), false);
        return 1;
    }

    private static String text(Map<String, Integer> items) {
        if (items.isEmpty()) {
            return "-";
        }
        StringBuilder text = new StringBuilder();
        items.forEach((item, count) -> text.append(text.length() == 0 ? "" : ", ").append(item).append(" x").append(count));
        return text.toString();
    }

    // ---------------------------------------------------------------- changing

    private static int place(CommandContext<CommandSourceStack> context, int rotation, boolean mirror) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        String id = ResourceLocationArgument.getId(context, "template").toString();
        BuildingTemplate template = BuildingTemplates.get(id).orElseThrow(() -> NO_TEMPLATE.create(id));
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        KingdomWorldState.ConstructionChange change = ConstructionWork.lay(source.getLevel(), null, kingdom.id(), template, pos,
                new Placement(rotation, mirror));
        if (!change.outcome().ok()) {
            source.sendFailure(KingdomActions.errorMessage(change.outcome().error()));
            return 0;
        }
        Construction construction = change.construction().orElseThrow();
        source.sendSuccess(() -> Component.translatable(KEY + "placed", construction.id(), template.name(), construction.anchor().coordinates(),
                construction.steps()), true);
        return 1;
    }

    private static int finish(CommandSourceStack source, Construction construction) {
        Building building = ConstructionWork.finishNow(source.getServer(), construction);
        source.sendSuccess(() -> Component.translatable(KEY + "finished", construction.id(), building.id()), true);
        return 1;
    }

    private static int cancel(CommandSourceStack source, Construction construction) {
        KingdomWorldState.ConstructionChange change = ConstructionWork.cancel(source.getServer(), null, construction.kingdomId(), construction.id());
        if (!change.outcome().ok()) {
            source.sendFailure(KingdomActions.errorMessage(change.outcome().error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "cancelled", construction.id()), true);
        return 1;
    }

    /**
     * Puts what a construction still needs into the storehouses of its kingdom, for tests.
     */
    private static int stock(CommandSourceStack source, Construction construction) {
        MinecraftServer server = source.getServer();
        Optional<BuildPlan> plan = ConstructionWork.plan(construction);
        if (plan.isEmpty()) {
            return 0;
        }
        int lost = 0;
        int given = 0;
        for (Map.Entry<String, Integer> entry : plan.get().needed(construction).entrySet()) {
            Item item = PostWork.item(entry.getKey());
            if (item == null) {
                continue;
            }
            int left = entry.getValue();
            while (left > 0) {
                int count = Math.min(left, new ItemStack(item).getMaxStackSize());
                left -= count;
                ItemStack rest = KingdomStorage.insert(server, construction.kingdomId(), new ItemStack(item, count), construction.anchor());
                lost += rest.getCount();
                given += count - rest.getCount();
            }
        }
        ConstructionWork.refreshReservations(server);
        int put = given;
        int over = lost;
        source.sendSuccess(() -> Component.translatable(KEY + "stocked", construction.id(), put, over), true);
        return given;
    }

    /**
     * Moves construction on now: blocks paid for are placed where loaded, statuses and reservations are refreshed.
     */
    private static int tick(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ConstructionWork.tick(server, 0);
        source.sendSuccess(() -> Component.translatable(KEY + "ticked", KingdomWorldApi.state(server).construction().all().size()), true);
        return 1;
    }
}
