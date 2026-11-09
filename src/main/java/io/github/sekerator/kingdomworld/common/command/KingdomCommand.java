package io.github.sekerator.kingdomworld.common.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.membership.KingdomMembers;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * {@code /kingdom}: commands for every player. All changes go through {@link KingdomActions}, so they follow
 * the same rules and rights as the throne screen.
 *
 * <pre>
 * /kingdom info [name]            /kingdom list
 * /kingdom join &lt;name&gt;           /kingdom accept [name]      /kingdom decline &lt;name&gt;
 * /kingdom leave                  /kingdom invite &lt;player&gt;
 * /kingdom approve &lt;player&gt;      /kingdom reject &lt;player&gt;    /kingdom kick &lt;player&gt;
 * /kingdom role &lt;player&gt; &lt;role&gt;  /kingdom abdicate &lt;player&gt;
 * /kingdom claim                  /kingdom unclaim
 * /kingdom dissolve [confirm]
 * /kingdom citizens
 * /kingdom balance                /kingdom pay &lt;player&gt; &lt;amount&gt; [kingdom]   /kingdom pay confirm
 * /kingdom treasury [pay &lt;player&gt; &lt;amount&gt; | deposit &lt;amount&gt; [kingdom]]
 * /kingdom value [item]
 * </pre>
 */
public final class KingdomCommand {
    private static final String KEY = "commands.kingdomworld.kingdom.";
    private static final long CONFIRM_WINDOW_MILLIS = 30_000L;
    private static final Map<UUID, Long> DISSOLVE_REQUESTS = new ConcurrentHashMap<>();

    private static final DynamicCommandExceptionType NOT_FOUND = new DynamicCommandExceptionType(
            name -> Component.translatable(KEY + "not_found", name));
    private static final DynamicCommandExceptionType UNKNOWN_PLAYER = new DynamicCommandExceptionType(
            name -> Component.translatable(KEY + "unknown_player", name));

    private static final SuggestionProvider<CommandSourceStack> KINGDOM_NAMES = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream().map(Kingdom::name), builder);
    private static final SuggestionProvider<CommandSourceStack> INVITATIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(invitationNames(context.getSource()), builder);
    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (context, builder) ->
            SharedSuggestionProvider.suggest(ownKingdom(context.getSource())
                    .map(members -> members.members().keySet().stream()
                            .map(id -> PlayerNames.name(context.getSource().getServer(), id)))
                    .orElse(Stream.empty()), builder);
    private static final SuggestionProvider<CommandSourceStack> APPLICANTS = (context, builder) ->
            SharedSuggestionProvider.suggest(ownKingdom(context.getSource())
                    .map(members -> members.applications().keySet().stream()
                            .map(id -> PlayerNames.name(context.getSource().getServer(), id)))
                    .orElse(Stream.empty()), builder);
    private static final SuggestionProvider<CommandSourceStack> ROLES = (context, builder) ->
            SharedSuggestionProvider.suggest(Stream.of(Role.values()).filter(Role::isAssignable).map(Role::id), builder);

    private KingdomCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("kingdom");
        EconomyCommands.addPlayerCommands(root);
        root.then(Commands.literal("guide").executes(context -> guide(context.getSource())));
        root.then(Commands.literal("trophies")
                .executes(context -> trophies(context.getSource()))
                .then(Commands.literal("buy").then(Commands.argument("id", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                        .executes(context -> run(context.getSource(), io.github.sekerator.kingdomworld.common.army.ArmyTrophies.buy(player(context),
                                com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "id")))))));
        dispatcher.register(root
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource(), null))
                        .then(Commands.argument("name", StringArgumentType.greedyString()).suggests(KINGDOM_NAMES)
                                .executes(context -> info(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(CitizenCommands.playerCommand())
                .then(CompanionCommands.playerCommand())
                .then(LawCommands.playerCommand())
                .then(io.github.sekerator.kingdomworld.common.siege.SiegeCommands.playerCommand())
                .then(Commands.literal("join")
                        .then(Commands.argument("name", StringArgumentType.greedyString()).suggests(KINGDOM_NAMES)
                                .executes(context -> join(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("accept")
                        .executes(context -> accept(context.getSource(), null))
                        .then(Commands.argument("name", StringArgumentType.greedyString()).suggests(INVITATIONS)
                                .executes(context -> accept(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("name", StringArgumentType.greedyString()).suggests(INVITATIONS)
                                .executes(context -> decline(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("leave").executes(context -> run(context.getSource(), KingdomActions.leave(player(context)))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(KingdomCommand::invite)))
                .then(Commands.literal("approve")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(APPLICANTS)
                                .executes(context -> run(context.getSource(), KingdomActions.approve(player(context), applicant(context))))))
                .then(Commands.literal("reject")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(APPLICANTS)
                                .executes(context -> run(context.getSource(), KingdomActions.reject(player(context), applicant(context))))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(MEMBERS)
                                .executes(context -> run(context.getSource(), KingdomActions.kick(player(context), member(context))))))
                .then(Commands.literal("role")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(MEMBERS)
                                .then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES)
                                        .executes(KingdomCommand::role))))
                .then(Commands.literal("abdicate")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(MEMBERS)
                                .executes(context -> run(context.getSource(), KingdomActions.abdicate(player(context), member(context))))))
                .then(Commands.literal("claim")
                        .executes(context -> run(context.getSource(), KingdomActions.claim(player(context), player(context).chunkPosition()))))
                .then(Commands.literal("unclaim")
                        .executes(context -> run(context.getSource(), KingdomActions.unclaim(player(context), player(context).chunkPosition()))))
                .then(Commands.literal("dissolve")
                        .executes(context -> requestDissolve(context.getSource()))
                        .then(Commands.literal("confirm").executes(context -> confirmDissolve(context.getSource())))));
    }

    /**
     * The guide book again, for a player who carries none.
     */
    private static int guide(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!io.github.sekerator.kingdomworld.common.item.GuideBookItem.giveAgain(player)) {
            source.sendFailure(Component.translatable("kingdomworld.guide.already"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("kingdomworld.guide.taken"), false);
        return 1;
    }

    /**
     * Trophies the kingdom of the player may buy back, and those it holds of others.
     */
    private static int trophies(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        java.util.Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(source.getServer(), player.getUUID());
        if (kingdom.isEmpty()) {
            source.sendFailure(KingdomActions.errorMessage(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.NOT_A_MEMBER));
            return 0;
        }
        java.util.List<io.github.sekerator.kingdomworld.common.army.TrophySavedData.Trophy> list =
                io.github.sekerator.kingdomworld.common.army.ArmyTrophies.of(source.getServer(), kingdom.get().id());
        if (list.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("kingdomworld.army.trophy.none"), false);
            return 1;
        }
        io.github.sekerator.kingdomworld.core.army.TrophyRules rules = io.github.sekerator.kingdomworld.common.army.ArmyTrophies.rules();
        for (io.github.sekerator.kingdomworld.common.army.TrophySavedData.Trophy trophy : list) {
            boolean ours = trophy.owner().equals(kingdom.get().id());
            UUID other = ours ? trophy.holder() : trophy.owner();
            Component state = trophy.peaceDay() == 0 ? Component.translatable("kingdomworld.army.trophy.state.war")
                    : Component.translatable("kingdomworld.army.trophy.state.until", trophy.peaceDay() + rules.days());
            source.sendSuccess(() -> Component.translatable("kingdomworld.army.trophy." + (ours ? "line_ours" : "line_theirs"), trophy.id(), trophy.count(),
                    io.github.sekerator.kingdomworld.common.diplomacy.WarWork.name(source.getServer(), other),
                    io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(rules.price(trophy.worth()),
                            io.github.sekerator.kingdomworld.common.trade.Purses.units(source.getServer(), trophy.owner())), state), false);
        }
        return list.size();
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    private static int run(CommandSourceStack source, KingdomActions.Feedback feedback) {
        if (feedback.success()) {
            source.sendSuccess(feedback::message, false);
            return 1;
        }
        source.sendFailure(feedback.message());
        return 0;
    }

    private static Kingdom find(CommandSourceStack source, String name) throws CommandSyntaxException {
        return KingdomWorldApi.findKingdom(source.getServer(), name)
                .orElseThrow(() -> NOT_FOUND.create(KingdomNames.normalize(name)));
    }

    private static Optional<KingdomMembers> ownKingdom(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return Optional.empty();
        }
        KingdomWorldState state = KingdomWorldApi.state(source.getServer());
        return state.membership().kingdomOf(player.getUUID()).flatMap(id -> state.membership().kingdom(id));
    }

    private static Stream<String> invitationNames(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return Stream.empty();
        }
        KingdomWorldState state = KingdomWorldApi.state(source.getServer());
        return state.membership().invitationsOf(player.getUUID()).stream()
                .map(id -> state.kingdoms().get(id)).flatMap(Optional::stream).map(Kingdom::name);
    }

    /**
     * Finds a player among ids by name, ignoring case.
     */
    private static UUID byName(MinecraftServer server, Collection<UUID> ids, String name) throws CommandSyntaxException {
        for (UUID id : ids) {
            if (PlayerNames.name(server, id).equalsIgnoreCase(name)) {
                return id;
            }
        }
        throw UNKNOWN_PLAYER.create(name);
    }

    private static UUID member(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "player");
        Collection<UUID> ids = ownKingdom(context.getSource()).map(m -> m.members().keySet()).orElse(java.util.Set.of());
        return byName(context.getSource().getServer(), new ArrayList<>(ids), name);
    }

    private static UUID applicant(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "player");
        Collection<UUID> ids = ownKingdom(context.getSource()).map(m -> m.applications().keySet()).orElse(java.util.Set.of());
        return byName(context.getSource().getServer(), new ArrayList<>(ids), name);
    }

    // ---------------------------------------------------------------- information

    private static int info(CommandSourceStack source, String name) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        Kingdom kingdom;
        if (name != null) {
            kingdom = find(source, name);
        } else {
            ServerPlayer player = source.getPlayerOrException();
            Optional<Kingdom> own = KingdomWorldApi.kingdomOf(server, player.getUUID());
            if (own.isEmpty()) {
                UUID here = KingdomWorldApi.ownerAt(player.level(), player.blockPosition());
                own = here == null ? Optional.empty() : KingdomWorldApi.kingdom(server, here);
            }
            if (own.isEmpty()) {
                source.sendFailure(Component.translatable(KEY + "info.none"));
                return 0;
            }
            kingdom = own.get();
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Territory> territory = state.territory().territory(kingdom.id());
        int members = state.membership().kingdom(kingdom.id()).map(KingdomMembers::size).orElse(0);
        int chunks = territory.map(Territory::size).orElse(0);
        int limit = KingdomWorldApi.chunkLimit(server, kingdom.id());
        source.sendSuccess(() -> Component.translatable(KEY + "info.header", KingdomActions.kingdomName(kingdom)), false);
        source.sendSuccess(() -> Component.translatable(KEY + "info.ruler", PlayerNames.display(server, kingdom.rulerId())), false);
        source.sendSuccess(() -> Component.translatable(KEY + "info.members", members), false);
        source.sendSuccess(() -> Component.translatable(KEY + "info.chunks", chunks, limit), false);
        if (territory.isPresent()) {
            Capital capital = territory.get().capital();
            source.sendSuccess(() -> Component.translatable(KEY + "info.capital", capital.x(), capital.y(), capital.z(),
                    capital.dimension()), false);
        } else {
            source.sendSuccess(() -> Component.translatable(KEY + "info.no_capital"), false);
        }
        ServerPlayer viewer = source.getPlayer();
        if (viewer != null) {
            state.membership().kingdom(kingdom.id()).flatMap(m -> m.member(viewer.getUUID())).map(Member::role)
                    .ifPresent(role -> source.sendSuccess(() -> Component.translatable(KEY + "info.role",
                            KingdomActions.roleName(role)), false));
        }
        return 1;
    }

    private static int list(CommandSourceStack source) {
        List<Kingdom> kingdoms = KingdomWorldApi.kingdoms(source.getServer());
        if (kingdoms.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "list.empty"), false);
            return 0;
        }
        KingdomWorldState state = KingdomWorldApi.state(source.getServer());
        source.sendSuccess(() -> Component.translatable(KEY + "list.header", kingdoms.size()), false);
        for (Kingdom kingdom : kingdoms) {
            int members = state.membership().kingdom(kingdom.id()).map(KingdomMembers::size).orElse(0);
            int chunks = state.territory().territory(kingdom.id()).map(Territory::size).orElse(0);
            source.sendSuccess(() -> Component.translatable(KEY + "list.entry", KingdomActions.kingdomName(kingdom),
                    PlayerNames.display(source.getServer(), kingdom.rulerId()), members, chunks), false);
        }
        return kingdoms.size();
    }

    // ---------------------------------------------------------------- membership

    private static int join(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return run(source, KingdomActions.apply(player, find(source, name)));
    }

    private static int accept(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Kingdom kingdom;
        if (name != null) {
            kingdom = find(source, name);
        } else {
            KingdomWorldState state = KingdomWorldApi.state(source.getServer());
            List<UUID> invitations = new ArrayList<>(state.membership().invitationsOf(player.getUUID()));
            if (invitations.size() != 1) {
                source.sendFailure(invitations.isEmpty() ? KingdomActions.errorMessage(KingdomError.NO_INVITATION)
                        : Component.translatable(KEY + "accept.which"));
                return 0;
            }
            kingdom = state.kingdoms().get(invitations.get(0)).orElseThrow(() -> NOT_FOUND.create("?"));
        }
        return run(source, KingdomActions.acceptInvitation(player, kingdom));
    }

    private static int decline(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return run(source, KingdomActions.declineInvitation(player, find(source, name)));
    }

    private static int invite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        int done = 0;
        for (GameProfile profile : profiles) {
            done += run(context.getSource(), KingdomActions.invite(player, profile.getId(), profile.getName()));
        }
        return done;
    }

    private static int role(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String roleId = StringArgumentType.getString(context, "role");
        Optional<Role> role = Role.byId(roleId);
        if (role.isEmpty()) {
            context.getSource().sendFailure(Component.translatable(KEY + "role.unknown", roleId));
            return 0;
        }
        return run(context.getSource(), KingdomActions.setRole(player(context), member(context), role.get()));
    }

    // ---------------------------------------------------------------- dissolution

    private static int requestDissolve(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(source.getServer(), player.getUUID());
        if (kingdom.isEmpty() || !player.getUUID().equals(kingdom.get().rulerId())) {
            source.sendFailure(KingdomActions.errorMessage(kingdom.isEmpty() ? KingdomError.NOT_IN_KINGDOM : KingdomError.NO_PERMISSION));
            return 0;
        }
        DISSOLVE_REQUESTS.put(player.getUUID(), Util.getMillis());
        Component confirm = KingdomActions.commandButton("/kingdom dissolve confirm", net.minecraft.ChatFormatting.RED,
                ClickEvent.Action.SUGGEST_COMMAND);
        source.sendSuccess(() -> Component.translatable(KEY + "dissolve.confirm", KingdomActions.kingdomName(kingdom.get()),
                CONFIRM_WINDOW_MILLIS / 1000, confirm), false);
        return 1;
    }

    private static int confirmDissolve(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Long requested = DISSOLVE_REQUESTS.remove(player.getUUID());
        if (requested == null || Util.getMillis() - requested > CONFIRM_WINDOW_MILLIS) {
            source.sendFailure(Component.translatable(KEY + "dissolve.expired"));
            return 0;
        }
        return run(source, KingdomActions.dissolve(player));
    }

    public static void forget(UUID playerId) {
        DISSOLVE_REQUESTS.remove(playerId);
        EconomyCommands.forget(playerId);
    }
}
