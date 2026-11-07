package io.github.sekerator.kingdomworld.common.command;

import com.mojang.brigadier.CommandDispatcher;
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
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.network.packet.CompanionPackets;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.CompanionRules;
import io.github.sekerator.kingdomworld.core.companion.CompanionState;
import io.github.sekerator.kingdomworld.core.companion.Offer;
import io.github.sekerator.kingdomworld.core.companion.Party;
import io.github.sekerator.kingdomworld.core.companion.Skills;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
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
    private static final DynamicCommandExceptionType NO_OWNER = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.admin.companions.no_owner", name));
    private static final DynamicCommandExceptionType NO_COMPANION = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.admin.companions.no_companion", name));
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

    // ---------------------------------------------------------------- operators

    /**
     * A player given by UUID or by name (online, or known to the server), so that operators and scripts reach owners who are
     * offline.
     */
    private static UUID owner(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        String text = StringArgumentType.getString(context, argument).strip();
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            MinecraftServer server = context.getSource().getServer();
            ServerPlayer online = server.getPlayerList().getPlayerByName(text);
            if (online != null) {
                return online.getUUID();
            }
            if (server.getProfileCache() != null) {
                Optional<com.mojang.authlib.GameProfile> profile = server.getProfileCache().get(text);
                if (profile.isPresent()) {
                    return profile.get().getId();
                }
            }
            throw NO_OWNER.create(text);
        }
    }

    private static Companion first(CommandContext<CommandSourceStack> context, UUID owner) throws CommandSyntaxException {
        List<Companion> companions = CompanionWork.state(context.getSource().getServer()).ownedBy(owner);
        if (companions.isEmpty()) {
            throw NO_COMPANION.create(owner.toString());
        }
        return companions.get(0);
    }

    /**
     * An operator's change: refused while the data is read-only.
     */
    private static boolean writable(CommandSourceStack source) {
        if (CompanionWork.isReadOnly(source.getServer())) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.READ_ONLY));
            return false;
        }
        return true;
    }

    /**
     * Makes a player a member of the guild at rank F if needed, for operators who set up parties and companions.
     */
    private static void enroll(MinecraftServer server, UUID player) {
        if (GuildWork.state(server).member(player).isEmpty()) {
            GuildWork.state(server).setRank(player, Rank.F, KingdomWorldApi.gameTime(server));
        }
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("companions")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())))
                .then(Commands.literal("offer")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> offers(context, false))
                                .then(Commands.literal("refresh").executes(context -> offers(context, true)))))
                .then(Commands.literal("give")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .executes(context -> give(context, 0, null))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, Companion.MAX_LEVEL))
                                        .executes(context -> give(context, IntegerArgumentType.getInteger(context, "level"), null))
                                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                                .executes(context -> give(context, IntegerArgumentType.getInteger(context, "level"),
                                                        kingdom(context)))))))
                .then(Commands.literal("fund")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1, 1_000_000_000L))
                                                .executes(CompanionCommands::fund)))))
                .then(Commands.literal("buy")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(CompanionCommands::buy))))
                .then(Commands.literal("pack")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .then(Commands.argument("item", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(CompanionCommands::pack)))))
                .then(Commands.literal("gear")
                        .then(Commands.argument("owner", StringArgumentType.string()).executes(CompanionCommands::gear)))
                .then(Commands.literal("list")
                        .then(Commands.argument("owner", StringArgumentType.string()).executes(CompanionCommands::list)))
                .then(Commands.literal("loyalty")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, CompanionRules.MAX_LOYALTY))
                                        .executes(CompanionCommands::loyalty))))
                .then(Commands.literal("hunger")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .then(Commands.argument("food", IntegerArgumentType.integer(0, CompanionRules.MAX_FOOD))
                                        .executes(CompanionCommands::hunger))))
                .then(Commands.literal("market")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("closed", BoolArgumentType.bool()).executes(CompanionCommands::market))))
                .then(Commands.literal("party")
                        .then(Commands.literal("add")
                                .then(Commands.argument("leader", StringArgumentType.string())
                                        .then(Commands.argument("member", StringArgumentType.string()).executes(CompanionCommands::partyAdd))))
                        .then(Commands.literal("fire")
                                .then(Commands.argument("leader", StringArgumentType.string())
                                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(CompanionCommands::partyFire))))
                        .then(Commands.literal("disband")
                                .then(Commands.argument("leader", StringArgumentType.string()).executes(CompanionCommands::partyDisband))));
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
        if (!writable(source)) {
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

    private static int give(CommandContext<CommandSourceStack> context, int level, Kingdom chosen) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID owner = owner(context, "owner");
        MinecraftServer server = source.getServer();
        if (!writable(source)) {
            return 0;
        }
        UUID branch = chosen != null ? chosen.id() : KingdomWorldApi.state(server).membership().kingdomOf(owner)
                .or(() -> KingdomWorldApi.kingdoms(server).stream().map(Kingdom::id).findFirst()).orElse(null);
        if (branch == null) {
            source.sendFailure(Component.translatable(ADMIN + "no_kingdom"));
            return 0;
        }
        net.minecraft.util.RandomSource random = server.overworld().getRandom();
        Skills skills = new Skills(1 + random.nextInt(4), 1 + random.nextInt(4), 1 + random.nextInt(4));
        Companion companion = CompanionWork.state(server).give(owner, UUID.randomUUID(), random.nextInt(),
                random.nextBoolean() ? Gender.MALE : Gender.FEMALE, random.nextInt(CitizenLife.SKINS), level, skills, branch,
                KingdomWorldApi.gameTime(server), TreasuryActions.day(server), CompanionSettings.rules());
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) {
            CompanionBodies.spawn(server, companion, online);
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "given", CompanionTexts.name(companion), PlayerNames.name(server, owner), level,
                companion.id().toString()), true);
        return 1;
    }

    private static int fund(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID owner = owner(context, "owner");
        Kingdom kingdom = kingdom(context);
        long amount = LongArgumentType.getLong(context, "amount");
        MinecraftServer server = source.getServer();
        if (!writable(source)) {
            return 0;
        }
        Outcome outcome = KingdomWorldApi.change(server, "fund an account", world -> world.economy().adminAccount(owner, kingdom.id(), amount));
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "funded", PlayerNames.name(server, owner), CompanionTexts.money(server, kingdom.id(),
                amount)), true);
        return 1;
    }

    /**
     * Buys the first slave on the market of a kingdom for a player, from the player's account, by the rules of the market;
     * the player joins the guild first if needed.
     */
    private static int buy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID owner = owner(context, "owner");
        Kingdom kingdom = kingdom(context);
        MinecraftServer server = source.getServer();
        if (!writable(source)) {
            return 0;
        }
        enroll(server, owner);
        CompanionState state = CompanionWork.state(server);
        CompanionRules rules = CompanionSettings.rules();
        long day = TreasuryActions.day(server);
        Optional<Offer> slave = state.offers(kingdom.id(), day, server.overworld().getSeed(), rules, CitizenLife.SKINS).stream()
                .filter(Offer::slave).findFirst();
        if (slave.isEmpty()) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.UNKNOWN_OFFER));
            return 0;
        }
        Optional<Rank> rank = GuildWork.state(server).member(owner).map(io.github.sekerator.kingdomworld.core.guild.GuildMember::rank);
        CompanionState.Made made = state.take(owner, rank, kingdom.id(), slave.get().id(), owner, UUID.randomUUID(),
                KingdomWorldApi.gameTime(server), day, rules, KingdomWorldApi.state(server));
        if (!made.outcome().ok()) {
            source.sendFailure(KingdomActions.errorMessage(made.outcome().error()));
            return 0;
        }
        Companion companion = made.companion().orElseThrow();
        source.sendSuccess(() -> Component.translatable(ADMIN + "bought", PlayerNames.name(server, owner), CompanionTexts.name(companion),
                slave.get().price(), companion.id().toString()), true);
        return 1;
    }

    private static int pack(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID owner = owner(context, "owner");
        net.minecraft.resources.ResourceLocation key = net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "item");
        String id = key.toString();
        int count = IntegerArgumentType.getInteger(context, "count");
        if (!writable(source)) {
            return 0;
        }
        Companion companion = first(context, owner);
        if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)) {
            source.sendFailure(Component.translatable(ADMIN + "bad_item", id));
            return 0;
        }
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key),
                count);
        net.minecraft.world.SimpleContainer pack = CompanionSavedData.get(source.getServer()).pack(companion.id());
        net.minecraft.world.item.ItemStack rest = pack.addItem(stack);
        int put = count - rest.getCount();
        source.sendSuccess(() -> Component.translatable(ADMIN + "packed", put, id, CompanionTexts.name(companion)), true);
        return put;
    }

    private static int gear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID owner = owner(context, "owner");
        Companion companion = first(context, owner);
        var view = CompanionActions.view(source.getServer(), companion);
        source.sendSuccess(() -> Component.translatable(ADMIN + "gear", CompanionTexts.name(companion), view.power()), false);
        String[] slots = {"mainhand", "offhand", "head", "chest", "legs", "feet"};
        for (int i = 0; i < view.gear().size(); i++) {
            net.minecraft.world.item.ItemStack stack = view.gear().get(i);
            String item = stack.isEmpty() ? "-" : String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
            String slot = slots[i];
            source.sendSuccess(() -> Component.translatable(ADMIN + "gear.entry", slot, item), false);
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID owner = owner(context, "owner");
        MinecraftServer server = source.getServer();
        List<Companion> companions = CompanionWork.state(server).ownedBy(owner);
        source.sendSuccess(() -> Component.translatable(ADMIN + "list", PlayerNames.name(server, owner), CompanionTexts.companions(companions.size())),
                false);
        for (Companion companion : companions) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "list.entry", CompanionTexts.name(companion),
                    CompanionTexts.status(companion.isSlave(), companion.gender()), companion.level(), companion.loyalty(), companion.food(),
                    (int) Math.ceil(companion.health()), CompanionTexts.order(companion.order()), itemCount(server, companion)), false);
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
        UUID owner = owner(context, "owner");
        int value = IntegerArgumentType.getInteger(context, "value");
        MinecraftServer server = source.getServer();
        Outcome outcome = CompanionWork.change(server, "set loyalty", state -> {
            for (Companion companion : state.ownedBy(owner)) {
                state.setLoyalty(companion.id(), value, KingdomWorldApi.gameTime(server));
            }
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "loyalty", PlayerNames.name(server, owner), value), true);
        return 1;
    }

    private static int hunger(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID owner = owner(context, "owner");
        int food = IntegerArgumentType.getInteger(context, "food");
        MinecraftServer server = source.getServer();
        Outcome outcome = CompanionWork.change(server, "set hunger", state -> {
            for (Companion companion : state.ownedBy(owner)) {
                state.setFood(companion.id(), food, KingdomWorldApi.gameTime(server));
            }
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "hunger", PlayerNames.name(server, owner), food), true);
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

    /**
     * Puts a member into the party of a leader (founding it if needed); both join the guild first if needed.
     */
    private static int partyAdd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID leader = owner(context, "leader");
        UUID member = owner(context, "member");
        MinecraftServer server = source.getServer();
        long now = KingdomWorldApi.gameTime(server);
        CompanionRules rules = CompanionSettings.rules();
        if (!writable(source)) {
            return 0;
        }
        enroll(server, leader);
        enroll(server, member);
        Outcome outcome = CompanionWork.change(server, "add to a party", state -> {
            Outcome invited = state.parties().invite(leader, member, true, true, now, rules);
            if (!invited.ok()) {
                return invited;
            }
            return state.parties().accept(member, state.parties().partyOf(leader).orElseThrow().id(), now, rules);
        });
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        long id = CompanionWork.state(server).parties().partyOf(leader).map(Party::id).orElse(0L);
        source.sendSuccess(() -> Component.translatable(ADMIN + "party.added", PlayerNames.name(server, member), PlayerNames.name(server, leader), id),
                true);
        return 1;
    }

    private static int partyFire(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID leader = owner(context, "leader");
        boolean on = BoolArgumentType.getBool(context, "on");
        Outcome outcome = CompanionWork.change(source.getServer(), "change friendly fire", state -> state.parties().setFriendlyFire(leader, on));
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(on ? "kingdomworld.party.fire_on" : "kingdomworld.party.fire_off"), true);
        return 1;
    }

    private static int partyDisband(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID leader = owner(context, "leader");
        MinecraftServer server = source.getServer();
        Optional<Party> party = CompanionWork.state(server).parties().partyOf(leader);
        if (party.isEmpty()) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.NOT_IN_PARTY));
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
        source.sendSuccess(() -> Component.translatable(ADMIN + "party.disbanded", PlayerNames.name(server, leader)), true);
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
            source.sendFailure(KingdomActions.errorMessage(KingdomError.NOT_INVITED));
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
            source.sendFailure(KingdomActions.errorMessage(KingdomError.NOT_IN_PARTY));
            return 0;
        }
        return 1;
    }
}
