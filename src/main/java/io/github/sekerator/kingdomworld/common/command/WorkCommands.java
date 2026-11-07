package io.github.sekerator.kingdomworld.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenActions;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Operator commands of work posts and storehouses, mostly for tests: list and set up posts, give a citizen a
 * profession, put items into a kingdom's storehouses, count them, and run steps of the simulation at once.
 */
final class WorkCommands {
    private static final String POSTS = "commands.kingdomworld.admin.posts.";
    private static final String STORAGE = "commands.kingdomworld.admin.storage.";
    private static final int LIST_LIMIT = 50;
    private static final int MAX_STEPS = 1_000;

    private static final DynamicCommandExceptionType NO_POST = new DynamicCommandExceptionType(
            pos -> Component.translatable(POSTS + "not_found", pos));
    private static final DynamicCommandExceptionType NO_ITEM = new DynamicCommandExceptionType(
            id -> Component.translatable(STORAGE + "unknown_item", id));
    private static final SuggestionProvider<CommandSourceStack> PROFESSIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Professions.REGISTRY.all().stream().map(profession -> profession.id()), builder);

    private WorkCommands() {
    }

    /**
     * {@code admin posts list|set}.
     */
    static LiteralArgumentBuilder<CommandSourceStack> posts() {
        return Commands.literal("posts")
                .then(Commands.literal("list")
                        .executes(context -> list(context, null))
                        .then(Commands.argument("kingdom", StringArgumentType.string())
                                .executes(context -> list(context, StringArgumentType.getString(context, "kingdom")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.literal("profession")
                                        .then(Commands.argument("profession", StringArgumentType.word()).suggests(PROFESSIONS)
                                                .executes(context -> set(context, pos -> KingdomWorldApi.state(context.getSource().getServer())
                                                        .setPostProfession(null, pos, StringArgumentType.getString(context, "profession"))))))
                                .then(Commands.literal("radius")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> set(context, pos -> KingdomWorldApi.state(context.getSource().getServer())
                                                        .setPostRadius(null, pos, IntegerArgumentType.getInteger(context, "value"),
                                                                GatheringSettings.rules().maxRadius())))))
                                .then(Commands.literal("seats")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 16))
                                                .executes(context -> set(context, pos -> KingdomWorldApi.state(context.getSource().getServer())
                                                        .setPostSeats(null, pos, IntegerArgumentType.getInteger(context, "value"),
                                                                GatheringSettings.rules().maxSeats())))))
                                .then(Commands.literal("herd")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(2, 64))
                                                .executes(context -> set(context, pos -> KingdomWorldApi.state(context.getSource().getServer())
                                                        .setPostHerdSize(null, pos, IntegerArgumentType.getInteger(context, "value"),
                                                                GatheringSettings.rules().maxHerd())))))
                                .then(Commands.literal("auto")
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                .executes(context -> set(context, pos -> KingdomWorldApi.state(context.getSource().getServer())
                                                        .setPostAuto(null, pos, com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "value"))))))
                                .then(Commands.literal("animal")
                                        .then(Commands.argument("animal", ResourceLocationArgument.id())
                                                .executes(context -> set(context, pos -> KingdomWorldApi.state(context.getSource().getServer())
                                                        .setPostAnimal(null, pos, ResourceLocationArgument.getId(context, "animal").toString())))))));
    }

    /**
     * {@code admin citizens profession <id> <profession>}.
     */
    static LiteralArgumentBuilder<CommandSourceStack> profession() {
        return Commands.literal("profession")
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("profession", StringArgumentType.word()).suggests(PROFESSIONS)
                                .executes(WorkCommands::setProfession)));
    }

    /**
     * {@code admin storage put|list} and {@code admin simulate}.
     */
    static LiteralArgumentBuilder<CommandSourceStack> storage() {
        return Commands.literal("storage")
                .then(Commands.literal("put")
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64 * 54))
                                        .executes(context -> put(context, null))
                                        .then(Commands.argument("kingdom", StringArgumentType.string())
                                                .executes(context -> put(context, StringArgumentType.getString(context, "kingdom")))))))
                .then(Commands.literal("list")
                        .executes(context -> storageList(context, null))
                        .then(Commands.argument("kingdom", StringArgumentType.string())
                                .executes(context -> storageList(context, StringArgumentType.getString(context, "kingdom")))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> simulate() {
        return Commands.literal("simulate")
                .then(Commands.argument("kingdom", StringArgumentType.string())
                        .executes(context -> simulate(context, 1))
                        .then(Commands.argument("steps", IntegerArgumentType.integer(1, MAX_STEPS))
                                .executes(context -> simulate(context, IntegerArgumentType.getInteger(context, "steps")))));
    }

    // ---------------------------------------------------------------- posts

    private static int list(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = CitizenCommands.kingdom(source, name);
        List<WorkPost> posts = KingdomWorldApi.state(server).citizens().posts().postsOf(kingdom.id());
        source.sendSuccess(() -> Component.translatable(POSTS + "list.header", KingdomActions.kingdomName(kingdom), posts.size()), false);
        long day = PostWork.gameDay(server);
        for (int i = 0; i < posts.size() && i < LIST_LIMIT; i++) {
            WorkPost post = posts.get(i);
            PostWork.refreshStatus(server, post);
            Component profession = post.hasProfession() ? CitizenTexts.profession(post.professionId())
                    : Component.translatable("kingdomworld.screen.post.no_profession");
            source.sendSuccess(() -> Component.translatable(POSTS + "list.entry", post.pos().coordinates(), profession,
                    post.workers().size(), post.seats(), post.radius(),
                    Component.translatable("kingdomworld.work.status." + post.status().id()), post.producedOn(day),
                    Math.round(post.yield().total()), Component.translatable(post.yield().measured() ? POSTS + "measured" : POSTS + "estimated")),
                    false);
        }
        return posts.size();
    }

    private static int set(CommandContext<CommandSourceStack> context, Function<WorldPos, Outcome> change) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        WorldPos at = new WorldPos(Dimensions.id(source.getLevel()), pos.getX(), pos.getY(), pos.getZ());
        if (PostWork.post(source.getServer(), at).isEmpty()) {
            throw NO_POST.create(at.coordinates());
        }
        Outcome outcome = KingdomWorldApi.change(source.getServer(), "change a work post", state -> change.apply(at));
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        WorkPost post = PostWork.post(source.getServer(), at).orElseThrow();
        ZoneSurveys.surveyNow(source.getServer(), post);
        PostWork.refreshStatus(source.getServer(), post);
        source.sendSuccess(() -> Component.translatable(POSTS + "set", at.coordinates(), CitizenTexts.profession(post.professionId()),
                post.radius(), post.seats()), true);
        return 1;
    }

    private static int setProfession(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Citizen citizen = CitizenCommands.citizen(source, StringArgumentType.getString(context, "id"));
        String profession = StringArgumentType.getString(context, "profession");
        Outcome outcome = KingdomWorldApi.change(source.getServer(), "give a citizen a profession",
                state -> state.setProfession(null, citizen.id(), profession));
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        CitizenActions.afterProfessionChange(source.getServer(), citizen);
        source.sendSuccess(() -> Component.translatable("kingdomworld.citizen.profession_set", CitizenTexts.name(citizen),
                CitizenTexts.profession(profession, citizen.gender())), true);
        return 1;
    }

    // ---------------------------------------------------------------- storehouses

    private static int put(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = CitizenCommands.kingdom(source, name);
        ResourceLocation id = ResourceLocationArgument.getId(context, "item");
        Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
        if (item == null || item == Items.AIR) {
            throw NO_ITEM.create(id.toString());
        }
        int count = IntegerArgumentType.getInteger(context, "count");
        int left = count;
        int stored = 0;
        while (left > 0) {
            int size = Math.min(left, new ItemStack(item).getMaxStackSize());
            ItemStack rest = KingdomStorage.insert(source.getServer(), kingdom.id(), new ItemStack(item, size), null);
            stored += size - rest.getCount();
            left -= size;
            if (!rest.isEmpty()) {
                break;
            }
        }
        int done = stored;
        source.sendSuccess(() -> Component.translatable(STORAGE + "put", done, id.toString(), KingdomActions.kingdomName(kingdom)), true);
        return done;
    }

    private static int storageList(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = CitizenCommands.kingdom(source, name);
        List<KingdomStorage.Total> totals = KingdomStorage.totals(source.getServer(), kingdom.id());
        source.sendSuccess(() -> Component.translatable(STORAGE + "list.header", KingdomActions.kingdomName(kingdom), totals.size(),
                KingdomStorage.storehouses(source.getServer(), kingdom.id(), null).size()), false);
        for (int i = 0; i < totals.size() && i < LIST_LIMIT; i++) {
            KingdomStorage.Total total = totals.get(i);
            source.sendSuccess(() -> Component.translatable(STORAGE + "list.entry", PostWork.itemId(total.sample()), total.count()), false);
        }
        return totals.size();
    }

    private static int simulate(CommandContext<CommandSourceStack> context, int steps) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = CitizenCommands.kingdom(source, StringArgumentType.getString(context, "kingdom"));
        for (int i = 0; i < steps; i++) {
            CitizenSimulation.step(source.getServer(), kingdom.id());
        }
        Optional<Kingdom> after = KingdomWorldApi.kingdom(source.getServer(), kingdom.id());
        source.sendSuccess(() -> Component.translatable("commands.kingdomworld.admin.simulate.done", steps,
                KingdomActions.kingdomName(after.orElse(kingdom))), true);
        return steps;
    }
}
