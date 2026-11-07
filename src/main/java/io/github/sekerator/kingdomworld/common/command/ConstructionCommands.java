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
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
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
    private static final DynamicCommandExceptionType NO_BUILDING = new DynamicCommandExceptionType(
            id -> Component.translatable(KEY + "unknown_building", id));
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
                                                                        BoolArgumentType.getBool(context, "mirror")))
                                                                .then(Commands.argument("wood", StringArgumentType.string())
                                                                        .executes(context -> place(context, IntegerArgumentType.getInteger(context,
                                                                                "rotation"), BoolArgumentType.getBool(context, "mirror"),
                                                                                StringArgumentType.getString(context, "wood"), ""))
                                                                        .then(Commands.argument("stone", StringArgumentType.string())
                                                                                .executes(context -> place(context, IntegerArgumentType.getInteger(context,
                                                                                        "rotation"), BoolArgumentType.getBool(context, "mirror"),
                                                                                        StringArgumentType.getString(context, "wood"),
                                                                                        StringArgumentType.getString(context, "stone")))))))))))
                .then(Commands.literal("role")
                        .then(Commands.argument("building", LongArgumentType.longArg(1))
                                .executes(context -> role(context.getSource(), building(context)))))
                .then(Commands.literal("upgrade")
                        .then(Commands.argument("building", LongArgumentType.longArg(1))
                                .executes(context -> work(context.getSource(), building(context),
                                        io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.Change.Action.UPGRADE))))
                .then(Commands.literal("repair")
                        .then(Commands.argument("building", LongArgumentType.longArg(1))
                                .executes(context -> work(context.getSource(), building(context),
                                        io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.Change.Action.REPAIR))))
                .then(Commands.literal("demolish")
                        .then(Commands.argument("building", LongArgumentType.longArg(1))
                                .executes(context -> work(context.getSource(), building(context),
                                        io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.Change.Action.DEMOLISH))))
                .then(Commands.literal("autorepair")
                        .then(Commands.argument("building", LongArgumentType.longArg(1))
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(context -> work(context.getSource(), building(context), BoolArgumentType.getBool(context, "on")
                                                ? io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.Change.Action.AUTO_REPAIR_ON
                                                : io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.Change.Action.AUTO_REPAIR_OFF)))))
                .then(Commands.literal("check")
                        .then(Commands.argument("building", LongArgumentType.longArg(1))
                                .executes(context -> check(context.getSource(), building(context)))))
                .then(Commands.literal("damage")
                        .then(Commands.argument("building", LongArgumentType.longArg(1))
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 100_000))
                                        .executes(context -> damage(context.getSource(), building(context),
                                                IntegerArgumentType.getInteger(context, "blocks"))))))
                .then(Commands.literal("priority")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .then(Commands.argument("priority", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("low", "normal", "high"), builder))
                                        .executes(context -> priority(context.getSource(), construction(context),
                                                StringArgumentType.getString(context, "priority"))))))
                .then(Commands.literal("materials").executes(context -> materials(context.getSource())))
                .then(Commands.literal("blueprints")
                        .executes(context -> blueprints(context.getSource(), null))
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> blueprints(context.getSource(), kingdom(context)))))
                .then(Commands.literal("capture")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .then(Commands.argument("name", StringArgumentType.string())
                                                        .executes(context -> capture(context, "", "decoration"))
                                                        .then(Commands.argument("role", StringArgumentType.string())
                                                                .executes(context -> capture(context, StringArgumentType.getString(context, "role"),
                                                                        "decoration"))
                                                                .then(Commands.argument("category", StringArgumentType.string())
                                                                        .executes(context -> capture(context, StringArgumentType.getString(context, "role"),
                                                                                StringArgumentType.getString(context, "category"))))))))))
                .then(Commands.literal("finish")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(context -> finish(context.getSource(), construction(context)))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(context -> cancel(context.getSource(), construction(context)))))
                .then(Commands.literal("pause")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(context -> pause(context.getSource(), construction(context), true))))
                .then(Commands.literal("resume")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(context -> pause(context.getSource(), construction(context), false))))
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

    private static Building building(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        long id = LongArgumentType.getLong(context, "building");
        return KingdomWorldApi.state(context.getSource().getServer()).construction().building(id).orElseThrow(() -> NO_BUILDING.create(id));
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
        return place(context, rotation, mirror, "", "");
    }

    /**
     * Lays a construction out; {@code wood} {@code auto} takes the wood of the place, {@code none} or empty the template's own.
     */
    private static int place(CommandContext<CommandSourceStack> context, int rotation, boolean mirror, String wood, String stone)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        String id = ResourceLocationArgument.getId(context, "template").toString();
        BuildingTemplate template = BuildingTemplates.get(id).orElseThrow(() -> NO_TEMPLATE.create(id));
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        io.github.sekerator.kingdomworld.core.construction.Materials materials = new io.github.sekerator.kingdomworld.core.construction.Materials(
                "none".equals(wood) ? "" : wood, "none".equals(stone) ? "" : stone);
        if (!io.github.sekerator.kingdomworld.common.construction.TemplateMaterials.known(materials)) {
            source.sendFailure(Component.translatable("kingdomworld.construction.unknown_material"));
            return 0;
        }
        KingdomWorldState.ConstructionChange change = ConstructionWork.lay(source.getLevel(), null, kingdom.id(), template, pos,
                new Placement(rotation, mirror), materials);
        if (!change.outcome().ok()) {
            source.sendFailure(KingdomActions.errorMessage(change.outcome().error()));
            return 0;
        }
        Construction construction = change.construction().orElseThrow();
        source.sendSuccess(() -> Component.translatable(KEY + "placed", construction.id(), template.name(), construction.anchor().coordinates(),
                construction.steps()), true);
        return 1;
    }

    // ---------------------------------------------------------------- buildings of the register

    /**
     * The role of a building: its state and why, its level and numbers, what it costs to upgrade and repair.
     */
    private static int role(CommandSourceStack source, Building building) {
        MinecraftServer server = source.getServer();
        var effect = io.github.sekerator.kingdomworld.common.construction.BuildingEffects.of(server, building).orElse(null);
        if (effect == null) {
            source.sendFailure(Component.translatable(KEY + "unknown_building", building.id()));
            return 0;
        }
        Map<String, Integer> upgrade = new TreeMap<>(io.github.sekerator.kingdomworld.common.construction.BuildingWork.upgradeDiff(building)
                .map(io.github.sekerator.kingdomworld.core.construction.TemplateDiff.Result::cost).orElse(Map.of()));
        Map<String, Integer> repair = new TreeMap<>(io.github.sekerator.kingdomworld.common.construction.BuildingWork.repairCost(building));
        source.sendSuccess(() -> Component.translatable(KEY + "role", building.id(), building.templateId(), building.role().isEmpty() ? "-"
                        : building.role(), effect.status().id(), building.level(), building.integrity(), building.damagedCount(), effect.beds(),
                effect.residents(), effect.params().toString(), building.materials().wood().isEmpty() ? "-" : building.materials().wood(),
                building.materials().stone().isEmpty() ? "-" : building.materials().stone(), building.autoRepair()), false);
        source.sendSuccess(() -> Component.translatable(KEY + "role.costs", text(upgrade), text(repair)), false);
        return effect.works() ? 1 : 0;
    }

    private static int work(CommandSourceStack source, Building building,
                            io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.Change.Action action) {
        KingdomActions.Feedback feedback = io.github.sekerator.kingdomworld.common.construction.ConstructionActions.applyBuilding(source.getServer(),
                null, building.kingdomId(), action, building.id());
        if (!feedback.success()) {
            source.sendFailure(feedback.message());
            return 0;
        }
        long construction = KingdomWorldApi.state(source.getServer()).construction().forBuilding(building.id()).map(Construction::id).orElse(0L);
        source.sendSuccess(() -> Component.translatable(KEY + "work", feedback.message(), construction), true);
        return 1;
    }

    /**
     * Looks at a whole building now, when its place is loaded.
     */
    private static int check(CommandSourceStack source, Building building) {
        int integrity = io.github.sekerator.kingdomworld.common.construction.ConstructionWork.checkNow(source.getServer(), building.id());
        if (integrity < 0) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.AREA_NOT_LOADED));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "checked", building.id(), integrity), false);
        return integrity;
    }

    /**
     * Takes away blocks of a building from the top down, as a war or a griefer would, for tests: they are gone, nothing drops.
     */
    private static int damage(CommandSourceStack source, Building building, int blocks) {
        int removed = io.github.sekerator.kingdomworld.common.construction.BuildingWork.damage(source.getServer(), building, blocks);
        source.sendSuccess(() -> Component.translatable(KEY + "damaged", building.id(), removed), true);
        return removed;
    }

    private static int priority(CommandSourceStack source, Construction construction, String value) {
        Construction.Priority priority = Construction.Priority.byId(value);
        KingdomActions.Feedback feedback = io.github.sekerator.kingdomworld.common.construction.ConstructionActions.apply(source.getServer(), null,
                new io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets.Change(construction.kingdomId(),
                        io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets.Change.Action.PRIORITY, construction.id(),
                        priority.ordinal()));
        if (!feedback.success()) {
            source.sendFailure(feedback.message());
            return 0;
        }
        source.sendSuccess(feedback::message, true);
        return 1;
    }

    /**
     * The woods and stones templates can be built of, from the blocks of the game and its mods.
     */
    private static int materials(CommandSourceStack source) {
        var table = io.github.sekerator.kingdomworld.common.construction.TemplateMaterials.table();
        source.sendSuccess(() -> Component.translatable(KEY + "materials", table.woods().size(), String.join(", ", table.woods()),
                table.stones().size(), String.join(", ", table.stones())), false);
        return table.woods().size();
    }

    private static int blueprints(CommandSourceStack source, Kingdom only) {
        MinecraftServer server = source.getServer();
        int count = 0;
        for (var blueprint : KingdomWorldApi.state(server).construction().blueprints()) {
            if (only != null && !blueprint.kingdomId().equals(only.id())) {
                continue;
            }
            count++;
            source.sendSuccess(() -> Component.translatable(KEY + "blueprints.entry", blueprint.templateId(), name(server, blueprint.kingdomId()),
                    blueprint.name(), blueprint.role().isEmpty() ? "-" : blueprint.role(), blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ()),
                    false);
        }
        int shown = count;
        source.sendSuccess(() -> Component.translatable(KEY + "blueprints", shown), false);
        return count;
    }

    private static int capture(CommandContext<CommandSourceStack> context, String role, String category) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        BlockPos from = BlockPosArgument.getLoadedBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(context, "to");
        KingdomActions.Feedback feedback = io.github.sekerator.kingdomworld.common.construction.BlueprintCapture.capture(source.getLevel(), null,
                kingdom.id(), from, to, StringArgumentType.getString(context, "name"), category, role,
                io.github.sekerator.kingdomworld.core.construction.Facing.SOUTH);
        if (!feedback.success()) {
            source.sendFailure(feedback.message());
            return 0;
        }
        source.sendSuccess(feedback::message, true);
        return 1;
    }

    private static int finish(CommandSourceStack source, Construction construction) {
        Optional<Building> building = ConstructionWork.finishNow(source.getServer(), construction);
        if (building.isEmpty()) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.CONSTRUCTION_OUTSIDE_LAND));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "finished", construction.id(), building.get().id()), true);
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
     * Pauses a construction or lets it go on: while paused, no builder works on it and nothing is set aside for it.
     */
    private static int pause(CommandSourceStack source, Construction construction, boolean paused) {
        KingdomWorldState.ConstructionChange change = KingdomWorldApi.state(source.getServer()).pauseConstruction(null, construction.kingdomId(),
                construction.id(), paused);
        if (!change.outcome().ok()) {
            source.sendFailure(KingdomActions.errorMessage(change.outcome().error()));
            return 0;
        }
        ConstructionWork.refreshReservations(source.getServer());
        source.sendSuccess(() -> Component.translatable(KEY + (paused ? "paused" : "resumed"), construction.id()), true);
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
