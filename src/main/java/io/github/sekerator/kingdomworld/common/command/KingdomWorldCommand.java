package io.github.sekerator.kingdomworld.common.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /kingdomworld} command tree. Requires permission level {@value #PERMISSION_LEVEL}.
 *
 * <pre>
 * /kingdomworld debug create &lt;name&gt;
 * /kingdomworld debug list
 * /kingdomworld debug info &lt;name&gt;
 * /kingdomworld debug remove &lt;name&gt;
 * </pre>
 */
public final class KingdomWorldCommand {
    public static final int PERMISSION_LEVEL = 2;
    private static final String KEY = "commands.kingdomworld.debug.";

    private static final DynamicCommandExceptionType NOT_FOUND = new DynamicCommandExceptionType(
            name -> Component.translatable(KEY + "not_found", name));

    private static final SuggestionProvider<CommandSourceStack> KINGDOM_NAMES = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    KingdomWorldApi.kingdoms(context.getSource().getServer()).stream().map(Kingdom::name),
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
                                        .executes(context -> remove(context.getSource(), StringArgumentType.getString(context, "name")))))));
    }

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
        source.sendFailure(Component.translatable("kingdomworld.kingdom.error." + error.name().toLowerCase(Locale.ROOT),
                KingdomNames.normalize(name), KingdomNames.MIN_LENGTH, KingdomNames.MAX_LENGTH));
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
        KingdomWorldApi.removeKingdom(source.getServer(), kingdom.id());
        source.sendSuccess(() -> Component.translatable(KEY + "remove.success", kingdom.name()), true);
        return 1;
    }

    private static Kingdom find(CommandSourceStack source, String name) throws CommandSyntaxException {
        return KingdomWorldApi.findKingdom(source.getServer(), name)
                .orElseThrow(() -> NOT_FOUND.create(KingdomNames.normalize(name)));
    }

    private static MutableComponent coloredName(Kingdom kingdom) {
        return Component.literal(kingdom.name()).withStyle(style -> style.withColor(TextColor.fromRgb(kingdom.color())));
    }

    private static Component rulerName(MinecraftServer server, Kingdom kingdom) {
        if (kingdom.rulerId() == null) {
            return Component.translatable(KEY + "info.ruler.none");
        }
        String name = server.getProfileCache() == null ? null
                : server.getProfileCache().get(kingdom.rulerId()).map(GameProfile::getName).orElse(null);
        return Component.literal(name != null ? name + " (" + kingdom.rulerId() + ")" : kingdom.rulerId().toString());
    }
}
