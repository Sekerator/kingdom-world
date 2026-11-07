package io.github.sekerator.kingdomworld.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.companion.CompanionActions;
import io.github.sekerator.kingdomworld.common.companion.CompanionBodies;
import io.github.sekerator.kingdomworld.common.companion.CompanionSavedData;
import io.github.sekerator.kingdomworld.common.companion.CompanionSettings;
import io.github.sekerator.kingdomworld.common.companion.CompanionTexts;
import io.github.sekerator.kingdomworld.common.companion.CompanionWork;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.packet.CompanionPackets;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.CompanionRules;
import io.github.sekerator.kingdomworld.core.companion.CompanionState;
import io.github.sekerator.kingdomworld.core.companion.Offer;
import io.github.sekerator.kingdomworld.core.companion.Party;
import io.github.sekerator.kingdomworld.core.companion.Skills;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Commands of companions and parties. Operators under {@code /kingdomworld admin companions}: the offers of a market, giving
 * a slave, loyalty, parties and the state of the data. Players under {@code /kingdom party} for their party, and
 * {@code /party <message>} to talk to it. Everything goes through the same calls the screens use.
 */
public final class CompanionCommands {
    private static final String ADMIN = "commands.kingdomworld.admin.companions.";
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.economy.no_kingdom", name));
    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);

    private CompanionCommands() {
    }

    private static Kingdom kingdom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "kingdom");
        return KingdomWorldApi.kingdoms(context.getSource().getServer()).stream().filter(k -> k.name().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> NO_KINGDOM.create(name));
    }

    private static Component feedback(KingdomActions.Feedback feedback) {
        return feedback.message();
    }

    // ---------------------------------------------------------------- operators

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("companions")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())))
                .then(Commands.literal("offer")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> offers(context, false))
                                .then(Commands.literal("refresh").executes(context -> offers(context, true)))))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> give(context, 0))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, Companion.MAX_LEVEL))
                                        .executes(context -> give(context, IntegerArgumentType.getInteger(context, "level"))))))
                .then(Commands.literal("list")
                        .then(Commands.argument("player", EntityArgument.player()).executes(CompanionCommands::list)))
                .then(Commands.literal("loyalty")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, CompanionRules.MAX_LOYALTY))
                                        .executes(CompanionCommands::loyalty))))
                .then(Commands.literal("hunger")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("food", IntegerArgumentType.integer(0, CompanionRules.MAX_FOOD))
                                        .executes(CompanionCommands::hunger))))
                .then(Commands.literal("market")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("closed", BoolArgumentType.bool()).executes(CompanionCommands::market))))
                .then(Commands.literal("party")
                        .then(Commands.literal("add")
                                .then(Commands.argument("leader", EntityArgument.player())
                                        .then(Commands.argument("member", EntityArgument.player()).executes(CompanionCommands::partyAdd))))
                        .then(Commands.literal("fire")
                                .then(Commands.argument("leader", EntityArgument.player())
                                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(CompanionCommands::partyFire))))
                        .then(Commands.literal("disband")
                                .then(Commands.argument("leader", EntityArgument.player()).executes(CompanionCommands::partyDisband))));
    }

    private static int status(CommandSourceStack source) {
        CompanionSavedData data = CompanionSavedData.get(source.getServer());
        if (data.isReadOnly()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "status.read_only", data.readOnlyReason().orElse("?")), false);
            return 0;
        }
        CompanionState state = data.state();
        long slaves = state.companions().stream().filter(Companion::isSlave).count();
        source.sendSuccess(() -> Component.translatable(ADMIN + "status.ok", CompanionSavedData.FORMAT_VERSION, slaves,
                state.companions().size() - slaves, state.parties().parties().size()), false);
        return 1;
    }

    private static int unlock(CommandSourceStack source) {
        CompanionSavedData data = CompanionSavedData.get(source.getServer());
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

    private static int offers(CommandContext<CommandSourceStack> context, boolean refresh) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context);
        if (CompanionWork.isReadOnly(server)) {
            source.sendFailure(KingdomActions.errorMessage(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.READ_ONLY));
            return 0;
        }
        CompanionState state = CompanionWork.state(server);
        CompanionRules rules = CompanionSettings.rules();
        long day = TreasuryActions.day(server);
        List<Offer> offers = refresh ? state.refreshOffers(kingdom.id(), day, server.overworld().getSeed(), rules, CitizenLife.SKINS)
                : state.offers(kingdom.id(), day, server.overworld().getSeed(), rules, CitizenLife.SKINS);
        source.sendSuccess(() -> Component.translatable(ADMIN + "offers", KingdomActions.kingdomName(kingdom), offers.size()), false);
        for (Offer offer : offers) {
            source.sendSuccess(() -> Component.translatable(offer.slave() ? ADMIN + "offer.slave" : ADMIN + "offer.free", offer.id(),
                    io.github.sekerator.kingdomworld.common.citizen.CitizenTexts.name(offer.nameSeed(), offer.gender()), offer.level(),
                    CompanionTexts.money(server, kingdom.id(), offer.price())), false);
        }
        return offers.size();
    }

    private static int give(CommandContext<CommandSourceStack> context, int level) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        MinecraftServer server = source.getServer();
        if (CompanionWork.isReadOnly(server)) {
            source.sendFailure(KingdomActions.errorMessage(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.READ_ONLY));
            return 0;
        }
        UUID branch = KingdomWorldApi.state(server).membership().kingdomOf(player.getUUID())
                .or(() -> KingdomWorldApi.kingdoms(server).stream().map(Kingdom::id).findFirst()).orElse(null);
        if (branch == null) {
            source.sendFailure(Component.translatable(ADMIN + "no_kingdom"));
            return 0;
        }
        net.minecraft.util.RandomSource random = player.getRandom();
        Skills skills = new Skills(1 + random.nextInt(4), 1 + random.nextInt(4), 1 + random.nextInt(4));
        Companion companion = CompanionWork.state(server).give(player.getUUID(), UUID.randomUUID(), random.nextInt(),
                random.nextBoolean() ? Gender.MALE : Gender.FEMALE, random.nextInt(CitizenLife.SKINS), level, skills, branch,
                KingdomWorldApi.gameTime(server), TreasuryActions.day(server), CompanionSettings.rules());
        CompanionBodies.spawn(server, companion, player);
        source.sendSuccess(() -> Component.translatable(ADMIN + "given", CompanionTexts.name(companion), player.getDisplayName(), level), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        List<Companion> companions = CompanionWork.state(source.getServer()).ownedBy(player.getUUID());
        source.sendSuccess(() -> Component.translatable(ADMIN + "list", player.getDisplayName(), CompanionTexts.companions(companions.size())), false);
        for (Companion companion : companions) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "list.entry", CompanionTexts.name(companion),
                    CompanionTexts.status(companion.isSlave(), companion.gender()), companion.level(), companion.loyalty(), companion.food(),
                    (int) Math.ceil(companion.health()), CompanionTexts.order(companion.order()), itemCount(source.getServer(), companion)),
                    false);
        }
        return companions.size();
    }

    private static int itemCount(MinecraftServer server, Companion companion) {
        net.minecraft.world.SimpleContainer pack = CompanionSavedData.get(server).pack(companion.id());
        int count = 0;
        for (int i = 0; i < pack.getContainerSize(); i++) {
            count += pack.getItem(i).getCount();
        }
        return count;
    }

    private static int loyalty(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int value = IntegerArgumentType.getInteger(context, "value");
        MinecraftServer server = source.getServer();
        Outcome outcome = CompanionWork.change(server, "set loyalty", state -> {
            for (Companion companion : state.ownedBy(player.getUUID())) {
                state.setLoyalty(companion.id(), value, KingdomWorldApi.gameTime(server));
            }
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "loyalty", player.getDisplayName(), value), true);
        return 1;
    }

    private static int hunger(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int food = IntegerArgumentType.getInteger(context, "food");
        MinecraftServer server = source.getServer();
        Outcome outcome = CompanionWork.change(server, "set hunger", state -> {
            for (Companion companion : state.ownedBy(player.getUUID())) {
                state.setFood(companion.id(), food, KingdomWorldApi.gameTime(server));
            }
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "hunger", player.getDisplayName(), food), true);
        return 1;
    }

    private static int market(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        boolean closed = BoolArgumentType.getBool(context, "closed");
        MinecraftServer server = source.getServer();
        Outcome outcome = CompanionWork.change(server, "close a market",
                state -> state.setMarketClosed(null, kingdom.id(), closed, KingdomWorldApi.state(server)));
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(closed ? "kingdomworld.companion.market.closed" : "kingdomworld.companion.market.opened",
                KingdomActions.kingdomName(kingdom)), true);
        return 1;
    }

    private static int partyAdd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer leader = EntityArgument.getPlayer(context, "leader");
        ServerPlayer member = EntityArgument.getPlayer(context, "member");
        MinecraftServer server = source.getServer();
        long now = KingdomWorldApi.gameTime(server);
        CompanionRules rules = CompanionSettings.rules();
        Outcome outcome = CompanionWork.change(server, "add to a party", state -> {
            boolean leaderIn = GuildWork.state(server).member(leader.getUUID()).isPresent();
            boolean memberIn = GuildWork.state(server).member(member.getUUID()).isPresent();
            Outcome invited = state.parties().invite(leader.getUUID(), member.getUUID(), leaderIn, memberIn, now, rules);
            if (!invited.ok()) {
                return invited;
            }
            return state.parties().accept(member.getUUID(), state.parties().partyOf(leader.getUUID()).orElseThrow().id(), now, rules);
        });
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "party.added", member.getDisplayName(), leader.getDisplayName()), true);
        return 1;
    }

    private static int partyFire(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer leader = EntityArgument.getPlayer(context, "leader");
        boolean on = BoolArgumentType.getBool(context, "on");
        Component message = feedback(CompanionActions.applyParty(leader, CompanionPackets.PartyAction.Action.FIRE, leader.getUUID(), "", on ? 1 : 0));
        source.sendSuccess(() -> message, true);
        return 1;
    }

    private static int partyDisband(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer leader = EntityArgument.getPlayer(context, "leader");
        MinecraftServer server = source.getServer();
        Optional<Party> party = CompanionWork.state(server).parties().partyOf(leader.getUUID());
        if (party.isEmpty()) {
            source.sendFailure(KingdomActions.errorMessage(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.NOT_IN_PARTY));
            return 0;
        }
        Outcome outcome = CompanionWork.change(server, "disband a party", state -> {
            for (UUID member : List.copyOf(party.get().members())) {
                state.parties().leave(member);
            }
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "party.disbanded", leader.getDisplayName()), true);
        return 1;
    }

    // ---------------------------------------------------------------- players

    /**
     * {@code /kingdom party}: the player's party.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> playerCommand() {
        return Commands.literal("party")
                .executes(context -> info(context.getSource()))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> act(context, CompanionPackets.PartyAction.Action.INVITE,
                                        EntityArgument.getPlayer(context, "player"), 0))))
                .then(Commands.literal("accept").executes(context -> accept(context.getSource(), true)))
                .then(Commands.literal("decline").executes(context -> accept(context.getSource(), false)))
                .then(Commands.literal("leave").executes(context -> act(context, CompanionPackets.PartyAction.Action.LEAVE, null, 0)))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> act(context, CompanionPackets.PartyAction.Action.KICK,
                                        EntityArgument.getPlayer(context, "player"), 0))))
                .then(Commands.literal("leader")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> act(context, CompanionPackets.PartyAction.Action.LEADER,
                                        EntityArgument.getPlayer(context, "player"), 0))))
                .then(Commands.literal("fire")
                        .then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(context -> act(context, CompanionPackets.PartyAction.Action.FIRE, null,
                                        BoolArgumentType.getBool(context, "on") ? 1 : 0))))
                .then(Commands.literal("say")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> say(context.getSource(), StringArgumentType.getString(context, "message")))));
    }

    /**
     * {@code /party <message>}: a message to the party.
     */
    public static void registerChat(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("party")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> say(context.getSource(), StringArgumentType.getString(context, "message")))));
    }

    private static int info(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CompanionActions.sendParty(player, true);
        return 1;
    }

    private static int act(CommandContext<CommandSourceStack> context, CompanionPackets.PartyAction.Action action, ServerPlayer target, long value)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        KingdomActions.Feedback feedback = CompanionActions.applyParty(player, action, target == null ? player.getUUID() : target.getUUID(),
                target == null ? "" : target.getGameProfile().getName(), value);
        if (feedback.success()) {
            source.sendSuccess(feedback::message, false);
            return 1;
        }
        source.sendFailure(feedback.message());
        return 0;
    }

    private static int accept(CommandSourceStack source, boolean accept) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = player.server;
        var invites = CompanionWork.state(server).parties().invitesOf(player.getUUID(), KingdomWorldApi.gameTime(server));
        if (invites.isEmpty()) {
            source.sendFailure(KingdomActions.errorMessage(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.NOT_INVITED));
            return 0;
        }
        KingdomActions.Feedback feedback = CompanionActions.applyParty(player, accept ? CompanionPackets.PartyAction.Action.ACCEPT
                : CompanionPackets.PartyAction.Action.DECLINE, player.getUUID(), "", invites.get(invites.size() - 1).party());
        if (feedback.success()) {
            source.sendSuccess(feedback::message, false);
            return 1;
        }
        source.sendFailure(feedback.message());
        return 0;
    }

    private static int say(CommandSourceStack source, String message) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!CompanionActions.say(player, message)) {
            source.sendFailure(KingdomActions.errorMessage(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.NOT_IN_PARTY));
            return 0;
        }
        return 1;
    }
}
