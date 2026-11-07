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
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.data.CitizenView;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Citizen commands: {@code /kingdom citizens} for everybody and {@code /kingdomworld admin citizens ...} for
 * operators (list, spawn, kill and info, mostly for tests).
 */
final class CitizenCommands {
    private static final String KEY = "commands.kingdomworld.kingdom.citizens.";
    private static final String ADMIN = "commands.kingdomworld.admin.citizens.";
    /** Lines {@code list} prints at most. */
    private static final int LIST_LIMIT = 50;
    /** Citizens {@code spawn} adds at once at most. */
    static final int MAX_SPAWN = 100;

    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.debug.not_found", name));
    private static final DynamicCommandExceptionType NO_CITIZEN = new DynamicCommandExceptionType(
            id -> Component.translatable(ADMIN + "not_found", id));
    private static final DynamicCommandExceptionType AMBIGUOUS = new DynamicCommandExceptionType(
            id -> Component.translatable(ADMIN + "ambiguous", id));

    private static final SuggestionProvider<CommandSourceStack> QUOTED_KINGDOM_NAMES = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);
    private static final SuggestionProvider<CommandSourceStack> CITIZEN_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.state(context.getSource().getServer()).citizens().registry().all()
                    .stream().map(Citizen::shortId), builder);

    private CitizenCommands() {
    }

    // ---------------------------------------------------------------- /kingdom citizens

    static LiteralArgumentBuilder<CommandSourceStack> playerCommand() {
        return Commands.literal("citizens").executes(context -> summary(context.getSource()));
    }

    private static int summary(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, player.getUUID());
        if (kingdom.isEmpty()) {
            UUID here = KingdomWorldApi.ownerAt(player.level(), player.blockPosition());
            kingdom = here == null ? Optional.empty() : KingdomWorldApi.kingdom(server, here);
        }
        if (kingdom.isEmpty()) {
            source.sendFailure(Component.translatable("commands.kingdomworld.kingdom.info.none"));
            return 0;
        }
        Kingdom realm = kingdom.get();
        CitizenActions.Summary summary = CitizenActions.summary(server, realm.id());
        source.sendSuccess(() -> Component.translatable(KEY + "header", KingdomActions.kingdomName(realm)), false);
        source.sendSuccess(() -> Component.translatable(KEY + "population", summary.population(), summary.bedsUsed(),
                summary.bedsTotal()), false);
        source.sendSuccess(() -> summary.foodDays() == CitizenView.UNLIMITED_FOOD
                ? Component.translatable(KEY + "supplies_unlimited", summary.storehouses(), summary.mood())
                : Component.translatable(KEY + "supplies", summary.foodDays(), summary.storehouses(), summary.mood()), false);
        source.sendSuccess(() -> Component.translatable("kingdomworld.screen.citizens.growth."
                + summary.growth().name().toLowerCase(Locale.ROOT)), false);
        return summary.population();
    }

    // ---------------------------------------------------------------- /kingdomworld admin citizens

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("citizens")
                .then(Commands.literal("list")
                        .executes(context -> list(context, null))
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(QUOTED_KINGDOM_NAMES)
                                .executes(context -> list(context, StringArgumentType.getString(context, "kingdom")))))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_SPAWN))
                                .executes(context -> spawn(context, null))
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(QUOTED_KINGDOM_NAMES)
                                        .executes(context -> spawn(context, StringArgumentType.getString(context, "kingdom"))))))
                .then(Commands.literal("kill")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(CITIZEN_IDS)
                                .executes(CitizenCommands::kill)))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(CITIZEN_IDS)
                                .executes(CitizenCommands::info)))
                .then(WorkCommands.profession());
    }

    /**
     * The named kingdom, or the source player's kingdom, or the kingdom whose land the source stands on.
     */
    static Kingdom kingdom(CommandSourceStack source, String name) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        if (name != null) {
            return KingdomWorldApi.findKingdom(server, name).orElseThrow(() -> NO_KINGDOM.create(KingdomNames.normalize(name)));
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            Optional<Kingdom> own = KingdomWorldApi.kingdomOf(server, player.getUUID());
            if (own.isPresent()) {
                return own.get();
            }
        }
        UUID here = KingdomWorldApi.ownerAt(source.getLevel(), BlockPos.containing(source.getPosition()));
        return Optional.ofNullable(here).flatMap(id -> KingdomWorldApi.kingdom(server, id))
                .orElseThrow(() -> NO_KINGDOM.create("?"));
    }

    private static int list(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(source, name);
        List<Citizen> citizens = KingdomWorldApi.state(source.getServer()).citizens().registry().citizensOf(kingdom.id());
        long bodies = citizens.stream().filter(citizen -> CitizenBodies.body(citizen.id()) != null).count();
        source.sendSuccess(() -> Component.translatable(ADMIN + "list.header", KingdomActions.kingdomName(kingdom),
                citizens.size(), bodies), false);
        for (int i = 0; i < citizens.size() && i < LIST_LIMIT; i++) {
            Citizen citizen = citizens.get(i);
            source.sendSuccess(() -> Component.translatable(ADMIN + "list.entry", citizen.shortId(), CitizenTexts.name(citizen),
                    CitizenTexts.profession(citizen.professionId(), citizen.gender()), CitizenTexts.state(citizen.state(), citizen.gender()),
                    Math.round(citizen.health()), place(citizen.home())), false);
        }
        if (citizens.size() > LIST_LIMIT) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "list.more", citizens.size() - LIST_LIMIT), false);
        }
        return citizens.size();
    }

    private static int spawn(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(source, name);
        int count = IntegerArgumentType.getInteger(context, "count");
        MinecraftServer server = source.getServer();
        WorldPos place;
        Entity entity = source.getEntity();
        if (entity != null) {
            BlockPos pos = entity.blockPosition();
            place = new WorldPos(Dimensions.id(entity.level()), pos.getX(), pos.getY(), pos.getZ());
        } else {
            Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(kingdom.id()).map(Territory::capital);
            if (capital.isEmpty()) {
                source.sendFailure(Component.translatable(ADMIN + "spawn.no_place", KingdomActions.kingdomName(kingdom)));
                return 0;
            }
            place = new WorldPos(capital.get().dimension(), capital.get().x(), capital.get().y(), capital.get().z());
        }
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (CitizenLife.arrive(server, kingdom.id(), place, false).isPresent()) {
                spawned++;
            }
        }
        if (spawned == 0) {
            source.sendFailure(Component.translatable("commands.kingdomworld.admin.read_only"));
            return 0;
        }
        int done = spawned;
        source.sendSuccess(() -> Component.translatable(ADMIN + "spawn.success", done, KingdomActions.kingdomName(kingdom)), true);
        return spawned;
    }

    static Citizen citizen(CommandSourceStack source, String id) throws CommandSyntaxException {
        return find(source, id);
    }

    private static Citizen find(CommandSourceStack source, String id) throws CommandSyntaxException {
        List<Citizen> found = KingdomWorldApi.state(source.getServer()).citizens().registry().findByIdPrefix(id);
        if (found.isEmpty()) {
            throw NO_CITIZEN.create(id);
        }
        if (found.size() > 1) {
            throw AMBIGUOUS.create(id);
        }
        return found.get(0);
    }

    private static int kill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Citizen citizen = find(source, StringArgumentType.getString(context, "id"));
        if (CitizenLife.die(source.getServer(), citizen.id(), CauseOfDeath.COMMAND, "", citizen.lastPos()).isEmpty()) {
            source.sendFailure(Component.translatable("commands.kingdomworld.admin.read_only"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(CitizenTexts.gendered(ADMIN + "kill.success", citizen.gender()),
                CitizenTexts.name(citizen), citizen.shortId()), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Citizen citizen = find(source, StringArgumentType.getString(context, "id"));
        Component kingdom = KingdomWorldApi.kingdom(server, citizen.kingdomId()).<Component>map(KingdomActions::kingdomName)
                .orElse(Component.literal(citizen.kingdomId().toString()));
        long gameTime = KingdomWorldApi.gameTime(server);
        source.sendSuccess(() -> Component.translatable(CitizenTexts.gendered(ADMIN + "info.header", citizen.gender()),
                CitizenTexts.name(citizen), citizen.id().toString()), false);
        source.sendSuccess(() -> Component.translatable(ADMIN + "info.kingdom", kingdom), false);
        source.sendSuccess(() -> Component.translatable(ADMIN + "info.profession",
                CitizenTexts.profession(citizen.professionId(), citizen.gender()), citizen.experience()), false);
        source.sendSuccess(() -> Component.translatable(ADMIN + "info.health", CitizenTexts.state(citizen.state(), citizen.gender()),
                String.format(Locale.ROOT, "%.1f", citizen.health()), String.format(Locale.ROOT, "%.1f", citizen.food()),
                citizen.mood()), false);
        source.sendSuccess(() -> Component.translatable(ADMIN + "info.age", citizen.ageDays(gameTime)), false);
        source.sendSuccess(() -> Component.translatable(ADMIN + "info.home", place(citizen.home())), false);
        source.sendSuccess(() -> Component.translatable(ADMIN + "info.workplace", place(citizen.workplace()),
                citizen.workCapacity(), citizen.workProduct().isEmpty() ? "-" : citizen.workProduct()), false);
        boolean body = CitizenBodies.body(citizen.id()) != null;
        source.sendSuccess(() -> Component.translatable(ADMIN + "info.position", place(citizen.lastPos()),
                Component.translatable(body ? ADMIN + "info.body" : ADMIN + "info.no_body")), false);
        return 1;
    }

    private static Component place(WorldPos pos) {
        return pos == null ? Component.translatable(ADMIN + "none") : CitizenTexts.place(pos);
    }
}
