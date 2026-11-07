package io.github.sekerator.kingdomworld.common.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.EconomyEvents;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.production.Recipes;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.ItemValues;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.RetiredCurrency;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Commands of money. For every player under {@code /kingdom}: the balance of the accounts, payments to other players
 * (large ones ask for confirmation), the treasury of the own kingdom (look, pay a player from it, pay into it) and the
 * worth of an item. For operators under {@code /kingdomworld admin economy}: give and take money, hold a payday, look at
 * a treasury and work the worth of items out again; what an operator does to a treasury goes into its journal as the
 * operator's.
 */
public final class EconomyCommands {
    private static final String KEY = "commands.kingdomworld.economy.";
    private static final String ADMIN = "commands.kingdomworld.admin.economy.";
    private static final long CONFIRM_WINDOW_MILLIS = 30_000L;
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(UUID target, String targetName, UUID currency, long amount, long time) {
    }

    private static final DynamicCommandExceptionType BAD_AMOUNT = new DynamicCommandExceptionType(
            text -> Component.translatable(KEY + "bad_amount", text));
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable(KEY + "no_kingdom", name));
    private static final DynamicCommandExceptionType UNKNOWN_ITEM = new DynamicCommandExceptionType(
            id -> Component.translatable(KEY + "unknown_item", id));
    private static final SuggestionProvider<CommandSourceStack> QUOTED_KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);
    private static final SuggestionProvider<CommandSourceStack> AMOUNTS = (context, builder) ->
            SharedSuggestionProvider.suggest(java.util.List.of("100", "1g", "5s", "1g5s"), builder);

    private EconomyCommands() {
    }

    // ---------------------------------------------------------------- the tree

    static void addPlayerCommands(LiteralArgumentBuilder<CommandSourceStack> kingdom) {
        kingdom.then(Commands.literal("balance").executes(context -> balance(context.getSource())))
                .then(Commands.literal("pay")
                        .then(Commands.literal("confirm").executes(context -> confirmPay(context.getSource())))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", StringArgumentType.string()).suggests(AMOUNTS)
                                        .executes(context -> pay(context, null))
                                        .then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(QUOTED_KINGDOMS)
                                                .executes(context -> pay(context, StringArgumentType.getString(context, "kingdom")))))))
                .then(Commands.literal("treasury")
                        .executes(context -> treasury(context.getSource()))
                        .then(Commands.literal("pay")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount", StringArgumentType.string()).suggests(AMOUNTS)
                                                .executes(EconomyCommands::treasuryPay))))
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("amount", StringArgumentType.string()).suggests(AMOUNTS)
                                        .executes(context -> treasuryDeposit(context, null))
                                        .then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(QUOTED_KINGDOMS)
                                                .executes(context -> treasuryDeposit(context, StringArgumentType.getString(context, "kingdom")))))))
                .then(Commands.literal("value")
                        .executes(context -> value(context.getSource(), null))
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(context -> value(context.getSource(), ResourceLocationArgument.getId(context, "item")))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("economy")
                .then(Commands.literal("give")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", StringArgumentType.string())
                                        .executes(context -> adminAccount(context, null, true))
                                        .then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(QUOTED_KINGDOMS)
                                                .executes(context -> adminAccount(context, StringArgumentType.getString(context, "kingdom"), true))))))
                .then(Commands.literal("take")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", StringArgumentType.string())
                                        .executes(context -> adminAccount(context, null, false))
                                        .then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(QUOTED_KINGDOMS)
                                                .executes(context -> adminAccount(context, StringArgumentType.getString(context, "kingdom"), false))))))
                .then(Commands.literal("treasury")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(QUOTED_KINGDOMS)
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", StringArgumentType.string())
                                                .executes(context -> adminTreasury(context, true))))
                                .then(Commands.literal("take")
                                        .then(Commands.argument("amount", StringArgumentType.string())
                                                .executes(context -> adminTreasury(context, false))))))
                .then(Commands.literal("payday")
                        .then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(QUOTED_KINGDOMS)
                                .executes(context -> payday(context.getSource(), StringArgumentType.getString(context, "kingdom")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(QUOTED_KINGDOMS)
                                .executes(context -> info(context.getSource(), StringArgumentType.getString(context, "kingdom")))))
                .then(Commands.literal("accounts")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> accounts(context.getSource(), single(context)))))
                .then(Commands.literal("values").executes(context -> values(context.getSource())))
                .then(Commands.literal("value")
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(context -> value(context.getSource(), ResourceLocationArgument.getId(context, "item")))));
    }

    // ---------------------------------------------------------------- helpers

    private static GameProfile single(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            throw new DynamicCommandExceptionType(n -> Component.translatable(KEY + "one_player")).create("");
        }
        return profiles.iterator().next();
    }

    private static int run(CommandSourceStack source, KingdomActions.Feedback feedback) {
        if (feedback.success()) {
            source.sendSuccess(feedback::message, false);
            return 1;
        }
        source.sendFailure(feedback.message());
        return 0;
    }

    /**
     * The named kingdom, or the kingdom of the source player.
     */
    private static Kingdom kingdom(CommandSourceStack source, String name) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        if (name != null) {
            String unquoted = name.strip();
            if (unquoted.length() >= 2 && unquoted.startsWith("\"") && unquoted.endsWith("\"")) {
                unquoted = unquoted.substring(1, unquoted.length() - 1);
            }
            String wanted = unquoted;
            return KingdomWorldApi.findKingdom(server, wanted).orElseThrow(() -> NO_KINGDOM.create(KingdomNames.normalize(wanted)));
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            Optional<Kingdom> own = KingdomWorldApi.kingdomOf(server, player.getUUID());
            if (own.isPresent()) {
                return own.get();
            }
        }
        throw NO_KINGDOM.create("?");
    }

    private static CurrencyUnits units(MinecraftServer server, UUID currency) {
        return KingdomWorldApi.state(server).economy().units(currency).orElse(CurrencyUnits.DEFAULT);
    }

    private static long amount(CommandContext<CommandSourceStack> context, CurrencyUnits units) throws CommandSyntaxException {
        String text = StringArgumentType.getString(context, "amount");
        Optional<Long> amount = Money.parse(text, units);
        if (amount.isEmpty() || amount.get() <= 0) {
            throw BAD_AMOUNT.create(text);
        }
        return amount.get();
    }

    private static MutableComponent currencyName(MinecraftServer server, UUID currency) {
        Economy economy = KingdomWorldApi.state(server).economy();
        Optional<Treasury> treasury = economy.treasury(currency);
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, currency);
        if (treasury.isPresent() && kingdom.isPresent()) {
            return MoneyTexts.currency(treasury.get().currencyName(), kingdom.get().name())
                    .withStyle(style -> style.withColor(kingdom.get().color()));
        }
        RetiredCurrency old = economy.retired().get(currency);
        if (old != null) {
            return Component.translatable(KEY + "old_currency", MoneyTexts.currency(old.name(), old.kingdomName()));
        }
        return Component.literal(currency.toString().substring(0, 8));
    }

    // ---------------------------------------------------------------- players

    private static int balance(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        Map<UUID, Long> accounts = KingdomWorldApi.state(server).economy().accounts().of(player.getUUID());
        Map<UUID, Long> debts = EconomyEvents.debtsOf(server, player.getUUID());
        if (accounts.isEmpty() && debts.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "balance.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "balance.header"), false);
        accounts.forEach((currency, balance) -> source.sendSuccess(() -> Component.translatable(KEY + "balance.entry",
                currencyName(server, currency), MoneyTexts.amount(balance, units(server, currency))), false));
        debts.forEach((currency, debt) -> source.sendSuccess(() -> Component.translatable(KEY + "balance.debt",
                currencyName(server, currency), MoneyTexts.amount(debt, units(server, currency))).withStyle(ChatFormatting.GOLD), false));
        return accounts.size();
    }

    private static int pay(CommandContext<CommandSourceStack> context, String kingdomName) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(source, kingdomName);
        GameProfile target = single(context);
        long amount = amount(context, units(server, kingdom.id()));
        if (amount > EconomySettings.rules().confirmAbove()) {
            PENDING.put(player.getUUID(), new Pending(target.getId(), target.getName(), kingdom.id(), amount, Util.getMillis()));
            Component confirm = KingdomActions.commandButton("/kingdom pay confirm", ChatFormatting.GOLD, ClickEvent.Action.RUN_COMMAND);
            source.sendSuccess(() -> Component.translatable(KEY + "pay.confirm", MoneyTexts.amount(amount, units(server, kingdom.id())),
                    target.getName(), CONFIRM_WINDOW_MILLIS / 1000, confirm), false);
            return 1;
        }
        return run(source, transfer(player, target.getId(), target.getName(), kingdom.id(), amount));
    }

    private static int confirmPay(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null || Util.getMillis() - pending.time() > CONFIRM_WINDOW_MILLIS) {
            source.sendFailure(Component.translatable(KEY + "pay.expired"));
            return 0;
        }
        return run(source, transfer(player, pending.target(), pending.targetName(), pending.currency(), pending.amount()));
    }

    /**
     * A player pays another from the account in a currency.
     */
    public static KingdomActions.Feedback transfer(ServerPlayer player, UUID target, String targetName, UUID currency, long amount) {
        MinecraftServer server = player.server;
        Outcome outcome = KingdomWorldApi.change(server, "pay a player",
                state -> state.economy().transfer(player.getUUID(), target, currency, amount));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Component money = MoneyTexts.amount(amount, units(server, currency));
        KingdomActions.tell(server, target, Component.translatable(KEY + "pay.received", player.getDisplayName(), money,
                currencyName(server, currency)));
        return KingdomActions.Feedback.ok(KEY + "pay.sent", money, targetName, currencyName(server, currency));
    }

    private static int treasury(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(source, null);
        Optional<Treasury> found = KingdomWorldApi.treasury(server, kingdom.id());
        if (found.isEmpty()) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.UNKNOWN_CURRENCY));
            return 0;
        }
        describe(source, server, kingdom, found.get(), false);
        return 1;
    }

    private static void describe(CommandSourceStack source, MinecraftServer server, Kingdom kingdom, Treasury treasury, boolean admin) {
        CurrencyUnits units = treasury.units();
        long today = TreasuryActions.day(server);
        String key = admin ? ADMIN + "info." : KEY + "treasury.";
        source.sendSuccess(() -> Component.translatable(key + "header", KingdomActions.kingdomName(kingdom), currencyName(server, kingdom.id())),
                false);
        source.sendSuccess(() -> Component.translatable(key + "funds", MoneyTexts.amount(treasury.funds(), units),
                MoneyTexts.amount(treasury.balance(), units), MoneyTexts.coins(treasury.vault())), false);
        source.sendSuccess(() -> Component.translatable(key + "tax", MoneyTexts.amount(treasury.pollTax(), units)), false);
        TreasuryJournal.Totals week = treasury.journal().sum(today, TreasuryJournal.Period.WEEK);
        source.sendSuccess(() -> Component.translatable(key + "week", MoneyTexts.amount(week.income(), units),
                MoneyTexts.amount(week.expense(), units)), false);
        var payroll = treasury.lastPayroll();
        if (payroll.day() >= 0) {
            source.sendSuccess(() -> Component.translatable(key + "payroll", MoneyTexts.amount(payroll.paid(), units),
                    MoneyTexts.amount(payroll.due(), units), payroll.citizens()), false);
        }
        long debts = 0;
        for (long debt : treasury.debts().values()) {
            debts = Money.addCapped(debts, debt);
        }
        long total = debts;
        source.sendSuccess(() -> Component.translatable(key + "debts", treasury.debts().size(), MoneyTexts.amount(total, units)), false);
        if (admin) {
            long supply = KingdomWorldApi.state(server).economy().supply(kingdom.id());
            source.sendSuccess(() -> Component.translatable(ADMIN + "info.raw", treasury.balance(), treasury.vault().copper(),
                    treasury.vault().silver(), treasury.vault().gold(), treasury.funds(), supply), false);
        }
    }

    private static int treasuryPay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Kingdom kingdom = kingdom(source, null);
        GameProfile target = single(context);
        long amount = amount(context, units(source.getServer(), kingdom.id()));
        return run(source, TreasuryActions.pay(player, target.getId(), target.getName(), amount));
    }

    private static int treasuryDeposit(CommandContext<CommandSourceStack> context, String kingdomName) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Kingdom kingdom = kingdom(source, kingdomName);
        long amount = amount(context, units(source.getServer(), kingdom.id()));
        return run(source, TreasuryActions.deposit(player, kingdom, amount));
    }

    private static int value(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        ItemStack stack;
        if (id == null) {
            stack = source.getPlayerOrException().getMainHandItem();
            if (stack.isEmpty()) {
                source.sendFailure(Component.translatable(KEY + "value.empty_hand"));
                return 0;
            }
        } else {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                throw UNKNOWN_ITEM.create(id.toString());
            }
            stack = new ItemStack(item);
        }
        CurrencyUnits units = CurrencyUnits.DEFAULT;
        if (source.getEntity() instanceof ServerPlayer player) {
            units = KingdomWorldApi.kingdomOf(server, player.getUUID()).map(k -> units(server, k.id())).orElse(units);
        }
        ItemValues.Value value = ItemPrices.value(server, stack);
        CurrencyUnits shown = units;
        source.sendSuccess(() -> Component.translatable(KEY + "value.result", stack.getHoverName(), MoneyTexts.amount(value.units(), shown),
                Component.translatable(KEY + "value.source." + value.source().name().toLowerCase(java.util.Locale.ROOT)),
                Recipes.itemId(stack.getItem()), value.units()), false);
        return (int) Math.min(Integer.MAX_VALUE, value.units());
    }

    public static void forget(UUID player) {
        PENDING.remove(player);
    }

    // ---------------------------------------------------------------- operators

    private static int adminAccount(CommandContext<CommandSourceStack> context, String kingdomName, boolean give) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(source, kingdomName);
        GameProfile target = single(context);
        long amount = amount(context, units(server, kingdom.id()));
        Outcome outcome = KingdomWorldApi.change(server, "change an account",
                state -> state.economy().adminAccount(target.getId(), kingdom.id(), give ? amount : -amount));
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        long balance = KingdomWorldApi.balance(server, target.getId(), kingdom.id());
        source.sendSuccess(() -> Component.translatable(give ? ADMIN + "given" : ADMIN + "taken", amount, target.getName(), kingdom.name(),
                balance), true);
        return 1;
    }

    private static int adminTreasury(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(source, StringArgumentType.getString(context, "kingdom"));
        long amount = amount(context, units(server, kingdom.id()));
        Outcome outcome = KingdomWorldApi.change(server, "change a treasury",
                state -> state.economy().adminTreasury(kingdom.id(), add ? amount : -amount, TreasuryActions.day(server)));
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        long funds = KingdomWorldApi.treasury(server, kingdom.id()).map(Treasury::funds).orElse(0L);
        source.sendSuccess(() -> Component.translatable(add ? ADMIN + "treasury.added" : ADMIN + "treasury.taken", amount, kingdom.name(), funds),
                true);
        return 1;
    }

    private static int payday(CommandSourceStack source, String name) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(source, name);
        if (KingdomWorldApi.isReadOnly(server)) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.READ_ONLY));
            return 0;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        Treasury treasury = state.economy().treasury(kingdom.id()).orElseThrow(() -> NO_KINGDOM.create(kingdom.name()));
        long today = Math.floorDiv(KingdomWorldApi.gameTime(server), CitizenNeeds.TICKS_PER_DAY);
        long day = Math.max(today, treasury.lastPayday() + 1);
        if (treasury.lastPayday() == Treasury.NO_PAYDAY) {
            // Upgraded data starts the count first.
            EconomyEvents.payday(server, kingdom, day - 1);
        }
        Economy.Payday payday = EconomyEvents.payday(server, kingdom, day);
        long taxes = payday.taxes().stream().mapToLong(Economy.TaxPaid::paid).sum();
        source.sendSuccess(() -> Component.translatable(ADMIN + "payday", kingdom.name(), day, payday.taxes().size(), taxes,
                payday.summary().paid(), payday.summary().due(), payday.summary().citizens()), true);
        return 1;
    }

    private static int info(CommandSourceStack source, String name) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(source, name);
        Treasury treasury = KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow(() -> NO_KINGDOM.create(kingdom.name()));
        describe(source, server, kingdom, treasury, true);
        return 1;
    }

    private static int accounts(CommandSourceStack source, GameProfile profile) {
        MinecraftServer server = source.getServer();
        Map<UUID, Long> accounts = KingdomWorldApi.state(server).economy().accounts().of(profile.getId());
        source.sendSuccess(() -> Component.translatable(ADMIN + "accounts.header", profile.getName(), accounts.size()), false);
        accounts.forEach((currency, balance) -> source.sendSuccess(() -> Component.translatable(ADMIN + "accounts.entry",
                currencyName(server, currency), balance), false));
        return accounts.size();
    }

    private static int values(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ItemPrices.invalidate();
        ItemPrices.Report report = ItemPrices.report(server);
        source.sendSuccess(() -> Component.translatable(ADMIN + "values", report.items(), report.fromRecipes(), report.fromConfig(),
                report.byDefault(), report.recipes(), report.errors()), true);
        return report.items();
    }

    static String playerName(MinecraftServer server, UUID id) {
        return PlayerNames.name(server, id);
    }
}
