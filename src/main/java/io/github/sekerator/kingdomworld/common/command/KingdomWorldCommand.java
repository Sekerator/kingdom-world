package io.github.sekerator.kingdomworld.common.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /kingdomworld} command tree for operators. Requires permission level {@value #PERMISSION_LEVEL}.
 *
 * <pre>
 * /kingdomworld debug create &lt;name&gt;      /kingdomworld debug list
 * /kingdomworld debug info &lt;name&gt;        /kingdomworld debug remove &lt;name&gt;
 * /kingdomworld admin found &lt;name&gt; &lt;pos&gt; [ruler]
 * /kingdomworld admin delete &lt;kingdom&gt;
 * /kingdomworld admin setruler &lt;kingdom&gt; &lt;player&gt;
 * /kingdomworld admin claim &lt;kingdom&gt; &lt;chunkX&gt; &lt;chunkZ&gt;
 * /kingdomworld admin unclaim &lt;kingdom&gt; &lt;chunkX&gt; &lt;chunkZ&gt;
 * /kingdomworld admin owner &lt;chunkX&gt; &lt;chunkZ&gt;
 * /kingdomworld admin bypass
 * /kingdomworld admin data status|unlock
 * /kingdomworld admin citizens list [kingdom]|spawn &lt;count&gt; [kingdom]|kill &lt;id&gt;|info &lt;id&gt;
 * </pre>
 *
 * <p>Kingdom names in the admin commands are single words or quoted ("Old Valoria").
 */
public final class KingdomWorldCommand {
    public static final int PERMISSION_LEVEL = 2;
    private static final String KEY = "commands.kingdomworld.debug.";
    private static final String ADMIN = "commands.kingdomworld.admin.";

    private static final DynamicCommandExceptionType NOT_FOUND = new DynamicCommandExceptionType(
            name -> Component.translatable(KEY + "not_found", name));

    private static final SuggestionProvider<CommandSourceStack> KINGDOM_NAMES = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    KingdomWorldApi.kingdoms(context.getSource().getServer()).stream().map(Kingdom::name),
                    builder);
    private static final SuggestionProvider<CommandSourceStack> QUOTED_KINGDOM_NAMES = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                            .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name),
                    builder);

    private KingdomWorldCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kingdomworld")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("debug")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> create(context.getSource(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource())))
                        .then(Commands.literal("info")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(KINGDOM_NAMES)
                                        .executes(context -> info(context.getSource(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(KINGDOM_NAMES)
                                        .executes(context -> remove(context.getSource(), StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("admin")
                        .then(Commands.literal("found")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> found(context, null))
                                                .then(Commands.argument("ruler", GameProfileArgument.gameProfile())
                                                        .executes(context -> found(context, single(context, "ruler")))))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(KINGDOM_NAMES)
                                        .executes(context -> remove(context.getSource(), StringArgumentType.getString(context, "kingdom")))))
                        .then(Commands.literal("setruler")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(QUOTED_KINGDOM_NAMES)
                                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                                .executes(KingdomWorldCommand::setRuler))))
                        .then(Commands.literal("claim")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(QUOTED_KINGDOM_NAMES)
                                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                        .executes(context -> claim(context, true))))))
                        .then(Commands.literal("unclaim")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(QUOTED_KINGDOM_NAMES)
                                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                        .executes(context -> claim(context, false))))))
                        .then(Commands.literal("owner")
                                .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                .executes(KingdomWorldCommand::owner))))
                        .then(Commands.literal("bypass")
                                .executes(context -> bypass(context.getSource())))
                        .then(CitizenCommands.adminCommand())
                        .then(WorkCommands.posts())
                        .then(WorkCommands.storage())
                        .then(WorkCommands.simulate())
                        .then(OrderCommands.orders())
                        .then(OrderCommands.recipes())
                        .then(EconomyCommands.adminCommand())
                        .then(TradeCommands.adminCommand())
                        .then(GuildCommands.adminCommand())
                        .then(ConstructionCommands.adminCommand())
                        .then(ArmyCommands.adminCommand())
                        .then(MagicCommands.adminCommand())
                        .then(CompanionCommands.adminCommand())
                        .then(RoadCommands.adminCommand())
                        .then(Commands.literal("data")
                                .then(Commands.literal("status").executes(context -> dataStatus(context.getSource())))
                                .then(Commands.literal("unlock").executes(context -> dataUnlock(context.getSource()))))));
    }

    // ---------------------------------------------------------------- debug

    private static int create(CommandSourceStack source, String name) {
        UUID ruler = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
        CreateKingdomResult result = KingdomWorldApi.createKingdom(source.getServer(), name, ruler);
        if (result instanceof CreateKingdomResult.Created created) {
            Kingdom kingdom = created.kingdom();
            source.sendSuccess(() -> Component.translatable(KEY + "create.success",
                    coloredName(kingdom), kingdom.id().toString()), true);
            return 1;
        }
        CreateKingdomResult.Error error = ((CreateKingdomResult.Rejected) result).error();
        source.sendFailure(KingdomActions.createErrorMessage(error, name));
        return 0;
    }

    private static int list(CommandSourceStack source) {
        List<Kingdom> kingdoms = KingdomWorldApi.kingdoms(source.getServer());
        if (kingdoms.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "list.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "list.header", kingdoms.size()), false);
        for (Kingdom kingdom : kingdoms) {
            source.sendSuccess(() -> Component.translatable(KEY + "list.entry",
                    coloredName(kingdom), kingdom.id().toString()), false);
        }
        return kingdoms.size();
    }

    private static int info(CommandSourceStack source, String name) throws CommandSyntaxException {
        Kingdom kingdom = find(source, name);
        MinecraftServer server = source.getServer();
        source.sendSuccess(() -> Component.translatable(KEY + "info.header", coloredName(kingdom)), false);
        source.sendSuccess(() -> Component.translatable(KEY + "info.id", kingdom.id().toString()), false);
        source.sendSuccess(() -> Component.translatable(KEY + "info.color",
                Component.literal(String.format(Locale.ROOT, "#%06X", kingdom.color()))
                        .withStyle(style -> style.withColor(TextColor.fromRgb(kingdom.color())))), false);
        source.sendSuccess(() -> Component.translatable(KEY + "info.ruler", rulerName(server, kingdom)), false);
        source.sendSuccess(() -> Component.translatable(KEY + "info.created", kingdom.createdGameTime()), false);
        return 1;
    }

    private static int remove(CommandSourceStack source, String name) throws CommandSyntaxException {
        Kingdom kingdom = find(source, name);
        if (KingdomWorldApi.dissolveKingdom(source.getServer(), kingdom.id()).isEmpty()) {
            source.sendFailure(Component.translatable(ADMIN + "read_only"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "remove.success", kingdom.name()), true);
        return 1;
    }

    // ---------------------------------------------------------------- admin

    private static GameProfile single(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, argument);
        if (profiles.size() != 1) {
            throw GameProfileArgument.ERROR_UNKNOWN_PLAYER.create();
        }
        return profiles.iterator().next();
    }

    private static int found(CommandContext<CommandSourceStack> context, GameProfile ruler) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        ServerLevel level = source.getLevel();
        if (!level.isInWorldBounds(pos)) {
            source.sendFailure(Component.translatable("argument.pos.outofworld"));
            return 0;
        }
        BlockState previous = level.getBlockState(pos);
        level.setBlockAndUpdate(pos, ModRegistries.THRONE.get().defaultBlockState());
        UUID id = UUID.randomUUID();
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, pos, name, KingdomColors.defaultFor(id),
                ruler == null ? null : ruler.getId());
        if (result instanceof CreateKingdomResult.Created created) {
            Kingdom kingdom = created.kingdom();
            int chunks = KingdomWorldApi.state(source.getServer()).territory().territory(kingdom.id()).map(Territory::size).orElse(0);
            source.sendSuccess(() -> Component.translatable(ADMIN + "found.success", coloredName(kingdom),
                    kingdom.id().toString(), chunks), true);
            return 1;
        }
        level.setBlockAndUpdate(pos, previous);
        source.sendFailure(KingdomActions.createErrorMessage(((CreateKingdomResult.Rejected) result).error(), name));
        return 0;
    }

    private static int setRuler(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = find(source, StringArgumentType.getString(context, "kingdom"));
        GameProfile player = single(context, "player");
        MinecraftServer server = source.getServer();
        Outcome outcome = KingdomWorldApi.change(server, "set ruler of " + kingdom.name(),
                state -> state.crown(kingdom.id(), player.getId(), KingdomWorldApi.gameTime(server)));
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "setruler.success", coloredName(kingdom), player.getName()), true);
        return 1;
    }

    private static int claim(CommandContext<CommandSourceStack> context, boolean claim) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = find(source, StringArgumentType.getString(context, "kingdom"));
        ChunkPos chunk = new ChunkPos(IntegerArgumentType.getInteger(context, "chunkX"), IntegerArgumentType.getInteger(context, "chunkZ"));
        Outcome outcome = claim
                ? KingdomWorldApi.claimChunk(source.getLevel(), chunk, kingdom.id(), null)
                : KingdomWorldApi.unclaimChunk(source.getLevel(), chunk, kingdom.id(), null);
        Component chunkText = KingdomActions.chunkText(chunk);
        if (!outcome.ok()) {
            source.sendFailure(Component.translatable(ADMIN + (claim ? "claim.failed" : "unclaim.failed"), chunkText,
                    KingdomActions.errorMessage(outcome.error())));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + (claim ? "claim.success" : "unclaim.success"), chunkText,
                coloredName(kingdom)), true);
        return 1;
    }

    private static int owner(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ChunkPos chunk = new ChunkPos(IntegerArgumentType.getInteger(context, "chunkX"), IntegerArgumentType.getInteger(context, "chunkZ"));
        ServerLevel level = source.getLevel();
        UUID owner = KingdomWorldApi.ownerOfChunk(level, chunk.x, chunk.z);
        Component chunkText = KingdomActions.chunkText(chunk);
        String dimension = Dimensions.id(level);
        Optional<Kingdom> kingdom = owner == null ? Optional.empty() : KingdomWorldApi.kingdom(source.getServer(), owner);
        if (kingdom.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "owner.none", chunkText, dimension), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "owner.kingdom", chunkText, dimension,
                coloredName(kingdom.get())), false);
        return 1;
    }

    private static int bypass(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean on = ProtectionService.toggleBypass(player.getUUID());
        source.sendSuccess(() -> Component.translatable(ADMIN + (on ? "bypass.on" : "bypass.off")), true);
        return on ? 1 : 0;
    }

    private static int dataStatus(CommandSourceStack source) {
        KingdomSavedData data = KingdomSavedData.get(source.getServer());
        Optional<Component> reason = data.readOnlyMessage();
        if (reason.isPresent()) {
            source.sendFailure(Component.translatable(ADMIN + "data.read_only", reason.get()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "data.ok", KingdomSavedData.FORMAT_VERSION,
                data.registry().size()), false);
        return 1;
    }

    private static int dataUnlock(CommandSourceStack source) {
        KingdomSavedData data = KingdomSavedData.get(source.getServer());
        try {
            Optional<Path> backup = data.unlock(source.getServer());
            if (backup.isEmpty()) {
                source.sendSuccess(() -> Component.translatable(ADMIN + "data.not_locked"), false);
                return 0;
            }
            source.sendSuccess(() -> Component.translatable(ADMIN + "data.unlocked", backup.get().toString()), true);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.translatable(ADMIN + "data.backup_failed", String.valueOf(e.getMessage())));
            return 0;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Kingdom find(CommandSourceStack source, String name) throws CommandSyntaxException {
        return KingdomWorldApi.findKingdom(source.getServer(), name)
                .orElseThrow(() -> NOT_FOUND.create(KingdomNames.normalize(name)));
    }

    private static MutableComponent coloredName(Kingdom kingdom) {
        return KingdomActions.kingdomName(kingdom);
    }

    private static Component rulerName(MinecraftServer server, Kingdom kingdom) {
        if (kingdom.rulerId() == null) {
            return Component.translatable(KEY + "info.ruler.none");
        }
        return Component.translatable(KEY + "info.ruler.named", PlayerNames.name(server, kingdom.rulerId()),
                kingdom.rulerId().toString());
    }
}
