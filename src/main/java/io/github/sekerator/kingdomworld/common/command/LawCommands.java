package io.github.sekerator.kingdomworld.common.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.law.LawActions;
import io.github.sekerator.kingdomworld.common.law.LawEntry;
import io.github.sekerator.kingdomworld.common.law.LawPunish;
import io.github.sekerator.kingdomworld.common.law.LawSavedData;
import io.github.sekerator.kingdomworld.common.law.LawSettings;
import io.github.sekerator.kingdomworld.common.law.LawTexts;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.law.CityGates;
import io.github.sekerator.kingdomworld.core.law.Confiscation;
import io.github.sekerator.kingdomworld.core.law.Crime;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.LawState;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Commands of the law. Operators under {@code /kingdomworld admin law}: the wanted and their crimes, pardons and fines,
 * executions with confiscation (also of a stand-in for a player who is not online, for checks), confiscated goods and their
 * ransom, reputation, debts, passes, a gate of a city and the court of a kingdom. Players under {@code /kingdom law}: their
 * window of the law. Everything goes through the same calls the game and the screens use.
 */
public final class LawCommands {
    private static final String ADMIN = "commands.kingdomworld.admin.law.";
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.economy.no_kingdom", name));
    private static final DynamicCommandExceptionType NO_PLAYER = new DynamicCommandExceptionType(
            name -> Component.translatable(ADMIN + "no_player", name));
    private static final DynamicCommandExceptionType NOT_ONLINE = new DynamicCommandExceptionType(
            name -> Component.translatable(ADMIN + "not_online", name));
    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);
    private static final SuggestionProvider<CommandSourceStack> CRIMES = (context, builder) ->
            SharedSuggestionProvider.suggest(java.util.Arrays.stream(CrimeKind.values()).map(CrimeKind::id), builder);

    private LawCommands() {
    }

    private static Kingdom kingdom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "kingdom");
        return KingdomWorldApi.kingdoms(context.getSource().getServer()).stream().filter(k -> k.name().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> NO_KINGDOM.create(name));
    }

    /**
     * A player by UUID or name: online, known to the server, or (for a stand-in) a name the server never saw.
     */
    private record Who(UUID id, String name) {
    }

    private static Who who(CommandContext<CommandSourceStack> context, boolean anyName) throws CommandSyntaxException {
        String text = StringArgumentType.getString(context, "player").strip();
        MinecraftServer server = context.getSource().getServer();
        try {
            UUID id = UUID.fromString(text);
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            return new Who(id, online != null ? online.getGameProfile().getName() : text);
        } catch (IllegalArgumentException e) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(text);
            if (online != null) {
                return new Who(online.getUUID(), online.getGameProfile().getName());
            }
            if (server.getProfileCache() != null) {
                Optional<GameProfile> profile = server.getProfileCache().get(text);
                if (profile.isPresent()) {
                    return new Who(profile.get().getId(), profile.get().getName());
                }
            }
            if (anyName && !text.isEmpty() && text.length() <= 16) {
                return new Who(UUID.nameUUIDFromBytes(("OfflinePlayer:" + text).getBytes(StandardCharsets.UTF_8)), text);
            }
            throw NO_PLAYER.create(text);
        }
    }

    /**
     * The player if online, or a stand-in that is not in the world: it stands at the throne of the kingdom, for executions and
     * ransoms of players who are not online.
     */
    private static ServerPlayer playerOrStandIn(MinecraftServer server, Who who, Kingdom kingdom) {
        ServerPlayer online = server.getPlayerList().getPlayer(who.id());
        if (online != null) {
            return online;
        }
        ServerLevel level = server.overworld();
        Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(kingdom.id()).map(Territory::capital);
        if (capital.isPresent()) {
            ServerLevel there = Dimensions.key(capital.get().dimension()).map(server::getLevel).orElse(null);
            if (there != null) {
                level = there;
            }
        }
        ServerPlayer standIn = new ServerPlayer(server, level, new GameProfile(who.id(), who.name()));
        capital.ifPresent(c -> standIn.setPos(c.x() + 0.5, c.y() + 1, c.z() + 1.5));
        return standIn;
    }

    private static boolean writable(CommandSourceStack source) {
        if (LawWork.isReadOnly(source.getServer())) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.READ_ONLY));
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- operators

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("law")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())))
                .then(Commands.literal("report").executes(context -> report(context.getSource())))
                .then(Commands.literal("wanted")
                        .executes(context -> wanted(context, false))
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS).executes(context -> wanted(context, true)))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.string())
                                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                                .then(Commands.argument("crime", StringArgumentType.word()).suggests(CRIMES)
                                                        .executes(context -> addCrime(context, 0))
                                                        .then(Commands.argument("worth", LongArgumentType.longArg(0, 1_000_000_000_000L))
                                                                .executes(context -> addCrime(context, LongArgumentType.getLong(context, "worth")))))))))
                .then(Commands.literal("pardon")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(LawCommands::pardon))))
                .then(Commands.literal("fine")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.literal("pay").executes(LawCommands::payFine))
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("amount", LongArgumentType.longArg(0, 1_000_000_000_000L))
                                                        .executes(LawCommands::setFine))))))
                .then(Commands.literal("execute")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> execute(context, false))
                                        .then(Commands.literal("kit").executes(context -> execute(context, true))))))
                .then(Commands.literal("confiscated")
                        .executes(context -> confiscated(context, false))
                        .then(Commands.argument("player", StringArgumentType.string()).executes(context -> confiscated(context, true)))
                        .then(Commands.literal("ransom")
                                .then(Commands.argument("id", LongArgumentType.longArg(1))
                                        .executes(LawCommands::ransom)))
                        .then(Commands.literal("store")
                                .then(Commands.argument("id", LongArgumentType.longArg(1))
                                        .executes(LawCommands::store))))
                .then(Commands.literal("reputation")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> reputation(context, false))
                                        .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                                .executes(context -> reputation(context, true))))))
                .then(Commands.literal("debt")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> debt(context, 0))
                                        .then(Commands.literal("add")
                                                .then(Commands.argument("amount", LongArgumentType.longArg(1, 1_000_000_000_000L))
                                                        .executes(context -> debt(context, LongArgumentType.getLong(context, "amount"))))))))
                .then(Commands.literal("pass")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> pass(context, 0))
                                        .then(Commands.literal("grant").executes(context -> pass(context, 1)))
                                        .then(Commands.literal("revoke").executes(context -> pass(context, -1))))))
                .then(Commands.literal("gate")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(LawCommands::gate))))
                .then(Commands.literal("court")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> court(context, "", 0))
                                .then(Commands.literal("multiplier").then(Commands.argument("value", LongArgumentType.longArg(0))
                                        .executes(context -> court(context, "multiplier", LongArgumentType.getLong(context, "value")))))
                                .then(Commands.literal("fee").then(Commands.argument("value", LongArgumentType.longArg(0))
                                        .executes(context -> court(context, "fee", LongArgumentType.getLong(context, "value")))))
                                .then(Commands.literal("bounty").then(Commands.argument("value", LongArgumentType.longArg(-1))
                                        .executes(context -> court(context, "bounty", LongArgumentType.getLong(context, "value")))))
                                .then(Commands.literal("slavery").then(Commands.argument("value", LongArgumentType.longArg(0, 1))
                                        .executes(context -> court(context, "slavery", LongArgumentType.getLong(context, "value")))))));
    }

    private static int status(CommandSourceStack source) {
        LawSavedData data = LawSavedData.get(source.getServer());
        LawState state = data.state();
        source.sendSuccess(() -> Component.translatable(ADMIN + "status", state.allWanted().size(), state.confiscations().size(),
                state.laws().size(), LawWork.pendingReports(), LawSavedData.FORMAT_VERSION,
                data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : ""), false);
        return 1;
    }

    private static int unlock(CommandSourceStack source) {
        try {
            Optional<java.nio.file.Path> backup = LawSavedData.get(source.getServer()).unlock(source.getServer());
            source.sendSuccess(() -> backup.<Component>map(path -> Component.translatable(ADMIN + "unlocked", path.toString()))
                    .orElseGet(() -> Component.translatable(ADMIN + "not_locked")), true);
            return 1;
        } catch (java.io.IOException e) {
            source.sendFailure(Component.translatable(ADMIN + "unlock_failed", e.getMessage()));
            return 0;
        }
    }

    private static int report(CommandSourceStack source) {
        int pending = LawWork.pendingReports();
        LawWork.deliverNow(source.getServer());
        source.sendSuccess(() -> Component.translatable(ADMIN + "reported", pending), false);
        return pending;
    }

    private static MutableComponent describe(MinecraftServer server, Wanted wanted) {
        CurrencyUnits units = Purses.units(server, wanted.kingdomId());
        MutableComponent crimes = Component.empty();
        for (int i = 0; i < wanted.crimes().size(); i++) {
            Crime crime = wanted.crimes().get(i);
            if (i > 0) {
                crimes.append(", ");
            }
            crimes.append(LawTexts.crime(crime, units));
        }
        String name = KingdomWorldApi.kingdom(server, wanted.kingdomId()).map(Kingdom::name).orElse(wanted.kingdomId().toString());
        return Component.translatable(ADMIN + "wanted.entry", wanted.name(), name, crimes, LawTexts.punishment(server, wanted),
                wanted.bounty());
    }

    private static int wanted(CommandContext<CommandSourceStack> context, boolean one) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        LawState state = LawWork.state(server);
        List<Wanted> list = one ? state.wantedIn(kingdom(context).id()) : List.copyOf(state.allWanted());
        if (list.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "wanted.none"), false);
            return 0;
        }
        for (Wanted wanted : list) {
            context.getSource().sendSuccess(() -> describe(server, wanted), false);
        }
        return list.size();
    }

    private static int addCrime(CommandContext<CommandSourceStack> context, long worth) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!writable(source)) {
            return 0;
        }
        Who who = who(context, true);
        Kingdom kingdom = kingdom(context);
        String id = StringArgumentType.getString(context, "crime");
        Optional<CrimeKind> kind = CrimeKind.byId(id);
        if (kind.isEmpty()) {
            source.sendFailure(Component.translatable(ADMIN + "unknown_crime", id));
            return 0;
        }
        LawWork.commit(source.getServer(), kingdom.id(), who.id(), who.name(), kind.get(), worth, "");
        source.sendSuccess(() -> Component.translatable(ADMIN + "crime", who.name(), kingdom.name(), LawTexts.crime(kind.get())), true);
        return 1;
    }

    private static int pardon(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Who who = who(context, true);
        Kingdom kingdom = kingdom(context);
        MinecraftServer server = source.getServer();
        Optional<Wanted> before = LawWork.state(server).wanted(kingdom.id(), who.id());
        Outcome outcome = LawWork.change(server, "pardon", state -> state.pardon(null, kingdom.id(), who.id(), KingdomWorldApi.state(server)));
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        before.ifPresent(record -> LawWork.cleared(server, record, true, "pardoned"));
        source.sendSuccess(() -> Component.translatable(ADMIN + "pardoned", who.name(), kingdom.name()), true);
        return 1;
    }

    private static int payFine(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!writable(source)) {
            return 0;
        }
        Who who = who(context, true);
        Kingdom kingdom = kingdom(context);
        MinecraftServer server = source.getServer();
        Optional<Wanted> wanted = LawWork.state(server).wanted(kingdom.id(), who.id());
        if (wanted.isEmpty() || wanted.get().executable()) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.NOT_WANTED));
            return 0;
        }
        ServerPlayer payer = playerOrStandIn(server, who, kingdom);
        Optional<LawPunish.Paid> paid = LawPunish.settle(payer, kingdom.id());
        if (paid.isEmpty()) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.NOT_WANTED));
            return 0;
        }
        CurrencyUnits units = Purses.units(server, kingdom.id());
        source.sendSuccess(() -> Component.translatable(ADMIN + "fine_paid", who.name(), MoneyTexts.amount(paid.get().total(), units),
                paid.get().fromCoins(), paid.get().fromAccount(), paid.get().toDebt()), true);
        return 1;
    }

    private static int setFine(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Who who = who(context, true);
        Kingdom kingdom = kingdom(context);
        long amount = LongArgumentType.getLong(context, "amount");
        MinecraftServer server = source.getServer();
        Outcome outcome = LawWork.change(server, "set a fine", state -> state.setFine(null, kingdom.id(), who.id(), amount, LawSettings.rules(),
                KingdomWorldApi.state(server)));
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "fine_set", who.name(), kingdom.name(), amount), true);
        return 1;
    }

    private static int execute(CommandContext<CommandSourceStack> context, boolean kit) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!writable(source)) {
            return 0;
        }
        Who who = who(context, true);
        Kingdom kingdom = kingdom(context);
        MinecraftServer server = source.getServer();
        ServerPlayer player = playerOrStandIn(server, who, kingdom);
        if (kit) {
            player.getInventory().add(new ItemStack(Items.IRON_SWORD));
            player.getInventory().add(new ItemStack(Items.BREAD, 16));
            player.getInventory().armor.set(2, new ItemStack(Items.IRON_CHESTPLATE));
        }
        if (LawWork.state(server).wanted(kingdom.id(), who.id()).map(w -> !w.executable()).orElse(true)) {
            LawWork.commit(server, kingdom.id(), who.id(), who.name(), CrimeKind.MURDER, 0, "");
        }
        long before = LawWork.state(server).nextConfiscationId();
        LawPunish.sentence(player, kingdom.id());
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        Optional<Confiscation> made = LawWork.state(server).confiscation(before);
        int left = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            left += player.getInventory().getItem(i).getCount();
        }
        int inventoryLeft = left;
        if (made.isPresent()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "executed", who.name(), kingdom.name(), made.get().id(), made.get().items(),
                    made.get().worth(), inventoryLeft), true);
        } else {
            source.sendSuccess(() -> Component.translatable(ADMIN + "executed_none", who.name(), kingdom.name(), inventoryLeft), true);
        }
        return made.isPresent() ? 1 : 0;
    }

    private static int confiscated(CommandContext<CommandSourceStack> context, boolean one) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        LawState state = LawWork.state(server);
        List<Confiscation> list = one ? state.confiscationsOf(who(context, true).id()) : List.copyOf(state.confiscations());
        if (list.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "confiscated.none"), false);
            return 0;
        }
        long today = TreasuryActions.day(server);
        for (Confiscation confiscation : list) {
            List<ItemStack> goods = LawSavedData.get(server).goods(confiscation.id());
            int count = goods.stream().mapToInt(ItemStack::getCount).sum();
            context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "confiscated.entry", confiscation.id(), confiscation.ownerName(),
                    confiscation.kingdomName(), count, confiscation.worth(), LawPunish.ransomPrice(server, confiscation),
                    confiscation.daysLeft(today, LawSettings.rules())), false);
        }
        return list.size();
    }

    private static int ransom(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        long id = LongArgumentType.getLong(context, "id");
        Optional<Confiscation> confiscation = LawWork.state(server).confiscation(id);
        if (confiscation.isEmpty()) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.UNKNOWN_CONFISCATION));
            return 0;
        }
        Kingdom kingdom = KingdomWorldApi.kingdom(server, confiscation.get().kingdomId()).orElse(null);
        ServerPlayer owner = server.getPlayerList().getPlayer(confiscation.get().owner());
        if (owner == null && kingdom != null) {
            owner = playerOrStandIn(server, new Who(confiscation.get().owner(), confiscation.get().ownerName()), kingdom);
        }
        if (owner == null) {
            source.sendFailure(Component.translatable(ADMIN + "not_online", confiscation.get().ownerName()));
            return 0;
        }
        Outcome outcome = LawPunish.ransom(owner, id, true);
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        int count = 0;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            count += owner.getInventory().getItem(i).getCount();
        }
        int returned = count;
        source.sendSuccess(() -> Component.translatable(ADMIN + "ransomed", id, confiscation.get().ownerName(), returned,
                LawPunish.ransomPrice(server, confiscation.get())), true);
        return 1;
    }

    private static int store(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        long id = LongArgumentType.getLong(context, "id");
        boolean stored = LawPunish.storeNow(server, id);
        source.sendSuccess(() -> Component.translatable(stored ? ADMIN + "stored" : ADMIN + "not_stored", id), true);
        return stored ? 1 : 0;
    }

    private static int reputation(CommandContext<CommandSourceStack> context, boolean set) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Who who = who(context, true);
        Kingdom kingdom = kingdom(context);
        MinecraftServer server = source.getServer();
        if (set) {
            if (!writable(source)) {
                return 0;
            }
            LawWork.state(server).setReputation(kingdom.id(), who.id(), IntegerArgumentType.getInteger(context, "value"));
        }
        int value = LawWork.state(server).reputation(kingdom.id(), who.id());
        source.sendSuccess(() -> Component.translatable(ADMIN + "reputation", who.name(), kingdom.name(), LawTexts.reputation(value)), set);
        return 1;
    }

    private static int debt(CommandContext<CommandSourceStack> context, long add) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Who who = who(context, true);
        Kingdom kingdom = kingdom(context);
        MinecraftServer server = source.getServer();
        Economy economy = KingdomWorldApi.state(server).economy();
        if (add > 0) {
            if (KingdomWorldApi.isReadOnly(server)) {
                source.sendFailure(KingdomActions.errorMessage(KingdomError.READ_ONLY));
                return 0;
            }
            economy.addDebt(kingdom.id(), who.id(), add);
        }
        long debt = economy.treasury(kingdom.id()).map(t -> t.debt(who.id())).orElse(0L);
        source.sendSuccess(() -> Component.translatable(ADMIN + "debt", who.name(), kingdom.name(), MoneyTexts.amount(debt,
                Purses.units(server, kingdom.id())), debt), add > 0);
        return 1;
    }

    private static int pass(CommandContext<CommandSourceStack> context, int change) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Who who = who(context, true);
        Kingdom kingdom = kingdom(context);
        MinecraftServer server = source.getServer();
        LawState state = LawWork.state(server);
        if (change != 0 && !writable(source)) {
            return 0;
        }
        if (change > 0) {
            state.grantPass(kingdom.id(), who.id(), LawWork.now(server), LawSettings.rules());
        } else if (change < 0) {
            state.revokePass(kingdom.id(), who.id());
        }
        long left = Math.max(0, state.passUntil(kingdom.id(), who.id()) - LawWork.now(server));
        source.sendSuccess(() -> Component.translatable(ADMIN + "pass", who.name(), kingdom.name(), left), change != 0);
        return left > 0 ? 1 : 0;
    }

    private static int gate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Who who = who(context, false);
        Kingdom kingdom = kingdom(context);
        ServerPlayer player = source.getServer().getPlayerList().getPlayer(who.id());
        if (player == null) {
            throw NOT_ONLINE.create(who.name());
        }
        boolean in = LawEntry.onEnter(player, new CityGates.City(kingdom.id(), kingdom.id(), kingdom.name(), true));
        source.sendSuccess(() -> Component.translatable(in ? ADMIN + "gate.in" : ADMIN + "gate.out", who.name(), kingdom.name()), true);
        return in ? 1 : 0;
    }

    private static int court(CommandContext<CommandSourceStack> context, String what, long value) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        MinecraftServer server = source.getServer();
        var world = KingdomWorldApi.state(server);
        Outcome outcome = switch (what) {
            case "multiplier" -> LawWork.change(server, "set the fines", state -> state.setMultiplier(null, kingdom.id(), (int) Math.min(Integer.MAX_VALUE, value), world));
            case "fee" -> LawWork.change(server, "set the entry fee", state -> state.setEntryFee(null, kingdom.id(), value, LawSettings.rules(), world));
            case "bounty" -> LawWork.change(server, "set the bounty", state -> state.setBountyReward(null, kingdom.id(), value, LawSettings.rules(), world));
            case "slavery" -> LawWork.change(server, "ban slavery", state -> state.setSlaveryBanned(null, kingdom.id(), value == 1, world));
            default -> Outcome.DONE;
        };
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        var law = LawWork.state(server).law(kingdom.id());
        source.sendSuccess(() -> Component.translatable(ADMIN + "court", kingdom.name(), law.fineMultiplierPermille(), law.entryFee(),
                law.bounty(LawSettings.rules()), law.slaveryBanned() ? 1 : 0), !what.isEmpty());
        return 1;
    }

    // ---------------------------------------------------------------- players

    /**
     * {@code /kingdom law}: the player's window of the law.
     */
    static LiteralArgumentBuilder<CommandSourceStack> playerCommand() {
        return Commands.literal("law").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            LawActions.openPlayer(player, true);
            return 1;
        });
    }
}
