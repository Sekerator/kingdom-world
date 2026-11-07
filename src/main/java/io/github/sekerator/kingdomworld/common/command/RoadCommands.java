package io.github.sekerator.kingdomworld.common.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.road.BanditWork;
import io.github.sekerator.kingdomworld.common.road.RoadActions;
import io.github.sekerator.kingdomworld.common.road.RoadBuilding;
import io.github.sekerator.kingdomworld.common.road.RoadCaravans;
import io.github.sekerator.kingdomworld.common.road.RoadPlanning;
import io.github.sekerator.kingdomworld.common.road.RoadRegistries;
import io.github.sekerator.kingdomworld.common.road.RoadSavedData;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.road.RoadSync;
import io.github.sekerator.kingdomworld.common.road.RoadTexts;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.trade.TradeWork;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.road.CaravanRoute;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/**
 * Operator commands of cities, roads and robbers under {@code /kingdomworld admin roads}: founding cities, planning and
 * building roads, camps of robbers and ambushes for checks, and the state of the data. Everything goes through the same
 * calls the screens and the server use; nothing is made from nothing, roads are paid from the storehouses.
 */
public final class RoadCommands {
    private static final String ADMIN = "commands.kingdomworld.admin.roads.";
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.economy.no_kingdom", name));
    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);
    private static final SuggestionProvider<CommandSourceStack> CLASSES = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("path", "gravel", "paved"), builder);
    private static final SuggestionProvider<CommandSourceStack> MODES = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("joint", "payer"), builder);

    private RoadCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("roads")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())))
                .then(Commands.literal("city")
                        .then(Commands.literal("list").executes(context -> cities(context.getSource())))
                        .then(Commands.literal("add")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> addCity(context, null))
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(context -> addCity(context, StringArgumentType.getString(context, "name")))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", LongArgumentType.longArg(1)).executes(RoadCommands::removeCity))))
                .then(Commands.literal("road")
                        .then(Commands.literal("list").executes(context -> roads(context.getSource())))
                        .then(Commands.literal("plan")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("class", StringArgumentType.word()).suggests(CLASSES)
                                                                .executes(context -> plan(context, Road.Mode.JOINT))
                                                                .then(Commands.argument("mode", StringArgumentType.word()).suggests(MODES)
                                                                        .executes(context -> plan(context, "payer".equals(
                                                                                StringArgumentType.getString(context, "mode")) ? Road.Mode.PAYER
                                                                                : Road.Mode.JOINT))))))))
                        .then(Commands.literal("pay")
                                .then(Commands.argument("id", LongArgumentType.longArg(1)).executes(RoadCommands::pay)))
                        .then(Commands.literal("place")
                                .then(Commands.argument("id", LongArgumentType.longArg(1)).executes(RoadCommands::place)))
                        .then(Commands.literal("wear")
                                .then(Commands.argument("id", LongArgumentType.longArg(1))
                                        .then(Commands.argument("condition", DoubleArgumentType.doubleArg(0, 1)).executes(RoadCommands::wear))))
                        .then(Commands.literal("answer")
                                .then(Commands.argument("request", LongArgumentType.longArg(1))
                                        .then(Commands.literal("accept").executes(context -> answer(context, true)))
                                        .then(Commands.literal("decline").executes(context -> answer(context, false)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", LongArgumentType.longArg(1)).executes(RoadCommands::removeRoad))))
                .then(Commands.literal("camp")
                        .then(Commands.literal("list").executes(context -> camps(context.getSource())))
                        .then(Commands.literal("add")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> addCamp(context, RoadSettings.bandits().startBandits()))
                                        .then(Commands.argument("bandits", IntegerArgumentType.integer(0, 64))
                                                .executes(context -> addCamp(context, IntegerArgumentType.getInteger(context, "bandits"))))))
                        .then(Commands.literal("grow")
                                .then(Commands.argument("id", LongArgumentType.longArg(1)).executes(RoadCommands::grow)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", LongArgumentType.longArg(1)).executes(RoadCommands::removeCamp))))
                .then(Commands.literal("ambush")
                        .then(Commands.argument("caravan", LongArgumentType.longArg(1)).executes(RoadCommands::ambush)))
                .then(Commands.literal("caravans").executes(context -> caravans(context.getSource())))
                .then(Commands.literal("gear").executes(context -> gear(context.getSource())));
    }

    // ---------------------------------------------------------------- helpers

    private static Kingdom kingdom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = KingdomNames.normalize(StringArgumentType.getString(context, "kingdom"));
        return KingdomWorldApi.findKingdom(context.getSource().getServer(), name).orElseThrow(() -> NO_KINGDOM.create(name));
    }

    private static int refused(CommandSourceStack source, Outcome outcome) {
        source.sendFailure(KingdomActions.errorMessage(outcome.error()));
        return 0;
    }

    private static Optional<Road> road(CommandContext<CommandSourceStack> context) {
        Optional<Road> road = RoadWork.state(context.getSource().getServer()).road(LongArgumentType.getLong(context, "id"));
        if (road.isEmpty()) {
            context.getSource().sendFailure(Component.translatable(ADMIN + "no_road", LongArgumentType.getLong(context, "id")));
        }
        return road;
    }

    // ---------------------------------------------------------------- status

    private static int status(CommandSourceStack source) {
        RoadSavedData data = RoadSavedData.get(source.getServer());
        RoadState roads = data.state();
        source.sendSuccess(() -> Component.translatable(ADMIN + "status", RoadTexts.cities(roads.cities().size()),
                RoadTexts.roads(roads.roads().size()), roads.camps().size(), roads.routes().size(), RoadSavedData.FORMAT_VERSION,
                data.readOnlyReason().map(reason -> Component.translatable(ADMIN + "read_only", reason)).orElse(Component.empty())), false);
        return 1;
    }

    private static int unlock(CommandSourceStack source) {
        RoadSavedData data = RoadSavedData.get(source.getServer());
        try {
            Optional<java.nio.file.Path> backup = data.unlock(source.getServer());
            if (backup.isEmpty()) {
                source.sendSuccess(() -> Component.translatable(ADMIN + "unlock.not_needed"), false);
                return 0;
            }
            source.sendSuccess(() -> Component.translatable(ADMIN + "unlocked", backup.get().toString()), true);
            return 1;
        } catch (java.io.IOException e) {
            source.sendFailure(Component.translatable(ADMIN + "unlock.failed", String.valueOf(e.getMessage())));
            return 0;
        }
    }

    // ---------------------------------------------------------------- cities

    private static int cities(CommandSourceStack source) {
        RoadState roads = RoadWork.state(source.getServer());
        source.sendSuccess(() -> Component.translatable(ADMIN + "cities", RoadTexts.cities(roads.cities().size())), false);
        for (City city : roads.cities()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "city", city.id(), city.name(),
                    KingdomWorldApi.kingdom(source.getServer(), city.kingdomId()).map(Kingdom::name).orElse("?"), city.x(), city.y(), city.z()), false);
        }
        return roads.cities().size();
    }

    private static int addCity(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        level.getChunk(pos);
        Outcome outcome = RoadWork.foundCity(level, null, kingdom.id(), pos, name);
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        level.setBlock(pos, RoadRegistries.TOWN_HALL.get().defaultBlockState(), 3);
        source.sendSuccess(() -> Component.translatable(ADMIN + "city_added", kingdom.name(), pos.getX(), pos.getY(), pos.getZ()), true);
        return 1;
    }

    private static int removeCity(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        long id = LongArgumentType.getLong(context, "id");
        Outcome outcome = RoadWork.change(source.getServer(), "remove a city", roads -> roads.removeCity(id).isPresent() ? Outcome.DONE
                : Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.UNKNOWN_CITY));
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        RoadSync.changed();
        source.sendSuccess(() -> Component.translatable(ADMIN + "city_removed", id), true);
        return 1;
    }

    // ---------------------------------------------------------------- roads

    private static int roads(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        RoadState roads = RoadWork.state(server);
        source.sendSuccess(() -> Component.translatable(ADMIN + "roads", RoadTexts.roads(roads.roads().size())), false);
        for (Road road : roads.roads()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "road", road.id(), road.roadClass().id(), road.status().id(), road.length(),
                    road.paid(), road.units(), String.format(java.util.Locale.ROOT, "%.2f", road.condition()),
                    KingdomWorldApi.kingdom(server, road.kingdomId()).map(Kingdom::name).orElse("?"), road.mode().id()), false);
        }
        return roads.roads().size();
    }

    private static int plan(CommandContext<CommandSourceStack> context, Road.Mode mode) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        BlockPos from = BlockPosArgument.getBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getBlockPos(context, "to");
        Optional<RoadClass> roadClass = RoadClass.byId(StringArgumentType.getString(context, "class"));
        if (roadClass.isEmpty()) {
            source.sendFailure(Component.translatable(ADMIN + "no_class", StringArgumentType.getString(context, "class")));
            return 0;
        }
        // The search runs over the next ticks, as for a king on the atlas; the road is built when the way is found.
        Outcome outcome = RoadPlanning.startAndBuild(source.getLevel(), kingdom.id(), from.getX(), from.getZ(), to.getX(), to.getZ(),
                roadClass.get(), mode);
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "searching", kingdom.name()), true);
        return 1;
    }

    /**
     * Pays for every block of a road from the storehouses of its builders, as their builders would.
     */
    private static int pay(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Road> road = road(context);
        if (road.isEmpty()) {
            return 0;
        }
        int[] paid = new int[1];
        Outcome outcome = RoadWork.change(source.getServer(), "pay for a road", roads -> {
            for (Road.Section section : road.get().sections()) {
                while (!section.done() && RoadBuilding.payNow(source.getServer(), road.get(), section)) {
                    paid[0]++;
                }
            }
            roads.changed();
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        RoadSync.changed();
        source.sendSuccess(() -> Component.translatable(ADMIN + "paid", road.get().id(), paid[0], road.get().paid(), road.get().units()), true);
        return paid[0];
    }

    private static int place(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Road> road = road(context);
        if (road.isEmpty() || RoadWork.isReadOnly(source.getServer())) {
            return 0;
        }
        int placed = RoadBuilding.placeNow(source.getServer(), road.get());
        source.sendSuccess(() -> Component.translatable(ADMIN + "placed", road.get().id(), placed), true);
        return placed;
    }

    private static int wear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Road> road = road(context);
        if (road.isEmpty()) {
            return 0;
        }
        double condition = DoubleArgumentType.getDouble(context, "condition");
        Outcome outcome = RoadWork.change(source.getServer(), "wear a road", roads -> {
            road.get().sections().forEach(section -> section.setCondition(condition));
            roads.changed();
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "worn", road.get().id(), String.format(java.util.Locale.ROOT, "%.2f", condition)), true);
        return 1;
    }

    private static int answer(CommandContext<CommandSourceStack> context, boolean accept) {
        CommandSourceStack source = context.getSource();
        long id = LongArgumentType.getLong(context, "request");
        Optional<io.github.sekerator.kingdomworld.core.road.Crossing> crossing = RoadWork.state(source.getServer()).crossing(id);
        if (crossing.isEmpty()) {
            return refused(source, Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.UNKNOWN_CROSSING));
        }
        KingdomActions.Feedback feedback = RoadActions.answer(source.getServer(), crossing.get().owner(), id, accept);
        if (feedback.success()) {
            source.sendSuccess(feedback::message, true);
            return 1;
        }
        source.sendFailure(feedback.message());
        return 0;
    }

    private static int removeRoad(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Road> road = road(context);
        if (road.isEmpty()) {
            return 0;
        }
        KingdomActions.Feedback feedback = RoadActions.applyTab(source.getServer(), null, new io.github.sekerator.kingdomworld.common.network.packet
                .RoadPackets.TabChange(road.get().kingdomId(), io.github.sekerator.kingdomworld.common.network.packet.RoadPackets.TabChange.Action.CANCEL,
                road.get().id(), 0, ""));
        if (feedback.success()) {
            source.sendSuccess(feedback::message, true);
            return 1;
        }
        source.sendFailure(feedback.message());
        return 0;
    }

    // ---------------------------------------------------------------- camps

    private static int camps(CommandSourceStack source) {
        RoadState roads = RoadWork.state(source.getServer());
        source.sendSuccess(() -> Component.translatable(ADMIN + "camps", roads.camps().size()), false);
        for (BanditCamp camp : roads.camps()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "camp", camp.id(), camp.x(), camp.y(), camp.z(), RoadTexts.bandits(camp.bandits()),
                    camp.leader() ? Component.translatable(ADMIN + "leader") : Component.empty(), camp.lootItems(), camp.raids(), camp.built()
                            ? Component.translatable(ADMIN + "built") : Component.empty()), false);
        }
        return roads.camps().size();
    }

    private static int addCamp(CommandContext<CommandSourceStack> context, int bandits) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        if (RoadWork.isReadOnly(source.getServer())) {
            return refused(source, Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.READ_ONLY));
        }
        BanditCamp camp = BanditWork.found(source.getServer(), Dimensions.id(source.getLevel()), pos.getX(), pos.getZ(), bandits);
        source.sendSuccess(() -> Component.translatable(ADMIN + "camp_added", camp.id(), pos.getX(), pos.getZ(), RoadTexts.bandits(bandits)), true);
        return (int) camp.id();
    }

    private static int grow(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        long id = LongArgumentType.getLong(context, "id");
        Outcome outcome = RoadWork.change(source.getServer(), "grow a camp", roads -> roads.camp(id).map(camp -> {
            camp.grow(RoadSettings.bandits().maxBandits());
            roads.changed();
            return Outcome.DONE;
        }).orElse(Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.UNKNOWN_CAMP)));
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        RoadSync.changed();
        int size = RoadWork.state(source.getServer()).camp(id).map(BanditCamp::size).orElse(0);
        source.sendSuccess(() -> Component.translatable(ADMIN + "grown", id, size), true);
        return size;
    }

    private static int removeCamp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        long id = LongArgumentType.getLong(context, "id");
        Outcome outcome = RoadWork.change(source.getServer(), "remove a camp", roads -> roads.removeCamp(id).isPresent() ? Outcome.DONE
                : Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.UNKNOWN_CAMP));
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        RoadSync.changed();
        source.sendSuccess(() -> Component.translatable(ADMIN + "camp_removed", id), true);
        return 1;
    }

    // ---------------------------------------------------------------- robbers

    /**
     * Arms robbers that are never added to the world and tells which items of which mods they took.
     */
    private static int gear(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        java.util.Map<String, java.util.Set<String>> byMod = new java.util.TreeMap<>();
        int armed = 0;
        for (int i = 0; i < 64; i++) {
            io.github.sekerator.kingdomworld.common.road.BanditEntity bandit = (i % 8 == 0 ? RoadRegistries.BANDIT_LEADER : RoadRegistries.BANDIT)
                    .get().create(level);
            if (bandit == null) {
                continue;
            }
            io.github.sekerator.kingdomworld.common.road.BanditGear.equip(bandit, level.getRandom());
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                net.minecraft.world.item.ItemStack stack = bandit.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (id != null) {
                        byMod.computeIfAbsent(id.getNamespace(), mod -> new java.util.TreeSet<>()).add(id.toString());
                    }
                }
            }
            if (!bandit.getMainHandItem().isEmpty()) {
                armed++;
            }
            bandit.discard();
        }
        int total = armed;
        source.sendSuccess(() -> Component.translatable(ADMIN + "gear", total, byMod.size()), false);
        byMod.forEach((mod, items) -> source.sendSuccess(() -> Component.translatable(ADMIN + "gear_line", mod, items.size(),
                String.join(", ", items.stream().limit(12).toList())), false));
        return total;
    }

    // ---------------------------------------------------------------- caravans

    /**
     * Robbers fall upon a caravan on the piece of way it is on now, whatever the chance.
     */
    private static int ambush(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        long id = LongArgumentType.getLong(context, "caravan");
        Optional<Caravan> caravan = KingdomWorldApi.state(server).trade().caravan(id);
        if (caravan.isEmpty() || caravan.get().state() != Caravan.State.TRAVELING || RoadWork.isReadOnly(server)) {
            source.sendFailure(Component.translatable(ADMIN + "no_caravan", id));
            return 0;
        }
        CaravanRoute route = RoadCaravans.route(server, caravan.get());
        double[] at = route.position(KingdomWorldApi.gameTime(server));
        Optional<BanditCamp> camp = RoadWork.state(server).nearestCamp(caravan.get().dimension(), (int) at[0], (int) at[1], Double.MAX_VALUE);
        if (camp.isEmpty()) {
            source.sendFailure(Component.translatable(ADMIN + "no_camp"));
            return 0;
        }
        long before = caravan.get().items();
        Optional<Caravan> left = RoadCaravans.forceAmbush(server, caravan.get(), camp.get(), TradeWork.dice());
        long after = left.map(Caravan::items).orElse(0L);
        source.sendSuccess(() -> Component.translatable(ADMIN + "ambushed", id, camp.get().id(), before - after, after), true);
        return (int) (before - after);
    }

    private static int caravans(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        long now = KingdomWorldApi.gameTime(server);
        int count = 0;
        for (Caravan caravan : KingdomWorldApi.state(server).trade().caravans()) {
            CaravanRoute route = RoadCaravans.route(server, caravan);
            double[] at = route.position(now);
            int points = route.plan().points().size();
            long roads = RoadCaravans.roadsOf(server, caravan).size();
            int onRoads = (int) Math.round(route.plan().onRoads());
            source.sendSuccess(() -> Component.translatable(ADMIN + "caravan_line", caravan.id(), caravan.dealId(), caravan.state().id(),
                    (int) at[0], (int) at[1], points, roads, onRoads, (int) Math.round(route.plan().length()), caravan.items(),
                    Math.max(0, caravan.arriveTime() - now)), false);
            count++;
        }
        int total = count;
        source.sendSuccess(() -> Component.translatable(ADMIN + "caravans", total), false);
        return count;
    }
}
