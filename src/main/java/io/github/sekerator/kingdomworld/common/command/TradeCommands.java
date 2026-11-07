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
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import io.github.sekerator.kingdomworld.common.trade.MarketActions;
import io.github.sekerator.kingdomworld.common.trade.StallActions;
import io.github.sekerator.kingdomworld.common.trade.TradeActions;
import io.github.sekerator.kingdomworld.common.trade.TradeWork;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import io.github.sekerator.kingdomworld.core.trade.Market;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/**
 * Operator commands of trade under {@code /kingdomworld admin trade}: set up a market, order from abroad, propose and
 * accept deals for kingdoms, look at deals and caravans, bring caravans in at once, buy at a market for a player from the
 * account, and count the exchange rates now. Everything goes through the same calls the screens use.
 */
public final class TradeCommands {
    private static final String ADMIN = "commands.kingdomworld.admin.trade.";
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.economy.no_kingdom", name));
    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);

    private TradeCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("trade")
                .then(Commands.literal("sell")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .then(Commands.argument("price", LongArgumentType.longArg(0))
                                                .then(Commands.argument("keep", IntegerArgumentType.integer(0))
                                                        .executes(context -> market(context, TradePackets.MarketChange.Action.PUT_SELL,
                                                                LongArgumentType.getLong(context, "price"), IntegerArgumentType.getInteger(context, "keep"),
                                                                0)))))))
                .then(Commands.literal("buy")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .then(Commands.argument("price", LongArgumentType.longArg(0))
                                                .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("budget", LongArgumentType.longArg(0))
                                                                .executes(context -> market(context, TradePackets.MarketChange.Action.PUT_BUY,
                                                                        LongArgumentType.getLong(context, "price"),
                                                                        IntegerArgumentType.getInteger(context, "max"),
                                                                        LongArgumentType.getLong(context, "budget")))))))))
                .then(Commands.literal("export")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .then(Commands.argument("above", IntegerArgumentType.integer(0))
                                                .executes(context -> market(context, TradePackets.MarketChange.Action.PUT_EXPORT, 0,
                                                        IntegerArgumentType.getInteger(context, "above"), 0))))))
                .then(Commands.literal("import")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> market(context, TradePackets.MarketChange.Action.ORDER_IMPORT, 0,
                                                        IntegerArgumentType.getInteger(context, "count"), 0))))))
                .then(Commands.literal("wallet")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("buyer", StringArgumentType.word())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                .executes(TradeCommands::wallet)))))
                .then(Commands.literal("purchase")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("buyer", StringArgumentType.word())
                                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, StallActions.MAX_COUNT))
                                                        .executes(TradeCommands::purchase))))))
                .then(Commands.literal("propose")
                        .then(Commands.argument("seller", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("buyer", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, Deal.MAX_COUNT))
                                                        .then(Commands.argument("price", LongArgumentType.longArg(1))
                                                                .executes(context -> propose(context, 0))
                                                                .then(Commands.argument("period", IntegerArgumentType.integer(0, 365))
                                                                        .executes(context -> propose(context,
                                                                                IntegerArgumentType.getInteger(context, "period"))))))))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("deal", LongArgumentType.longArg(1))
                                        .executes(TradeCommands::accept))))
                .then(Commands.literal("deals").executes(context -> deals(context.getSource())))
                .then(Commands.literal("caravans").executes(context -> caravans(context.getSource())))
                .then(Commands.literal("arrive").executes(context -> arrive(context.getSource())))
                .then(Commands.literal("rates").executes(context -> rates(context.getSource())));
    }

    private static Kingdom kingdom(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, argument).strip();
        return KingdomWorldApi.findKingdom(context.getSource().getServer(), name)
                .orElseThrow(() -> NO_KINGDOM.create(KingdomNames.normalize(name)));
    }

    private static int run(CommandSourceStack source, KingdomActions.Feedback feedback) {
        if (feedback.success()) {
            source.sendSuccess(feedback::message, true);
            return 1;
        }
        source.sendFailure(feedback.message());
        return 0;
    }

    private static int market(CommandContext<CommandSourceStack> context, TradePackets.MarketChange.Action action, long price, long first,
                              long second) throws CommandSyntaxException {
        Kingdom kingdom = kingdom(context, "kingdom");
        String item = ResourceLocationArgument.getId(context, "item").toString();
        return run(context.getSource(), MarketActions.apply(context.getSource().getServer(), null, kingdom.id(),
                new TradePackets.MarketChange(kingdom.id(), action, item, price, first, second)));
    }

    /**
     * A test buyer known by name only, the way an offline server names its players; nobody has to be online.
     */
    private static GameProfile buyer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "buyer");
        return new GameProfile(net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(name), name);
    }

    /**
     * Gives a test buyer money on the account in a kingdom's currency, written into no journal (an operator's money).
     */
    private static int wallet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context, "kingdom");
        GameProfile profile = buyer(context);
        long amount = LongArgumentType.getLong(context, "amount");
        var outcome = KingdomWorldApi.change(source.getServer(), "fill a wallet",
                state -> state.economy().adminAccount(profile.getId(), kingdom.id(), amount));
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        long balance = KingdomWorldApi.balance(source.getServer(), profile.getId(), kingdom.id());
        source.sendSuccess(() -> Component.translatable(ADMIN + "wallet", profile.getName(), kingdom.name(), balance), true);
        return 1;
    }

    /**
     * Buys at a kingdom's market for a test buyer, from the buyer's account; the goods go to a stand-in that is thrown
     * away, so this only moves money and goods out of the storehouses.
     */
    private static int purchase(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context, "kingdom");
        GameProfile profile = buyer(context);
        var player = FakePlayerFactory.get(source.getLevel(), profile);
        player.getInventory().clearContent();
        try {
            return run(source, StallActions.buy(player, kingdom, ResourceLocationArgument.getId(context, "item").toString(),
                    IntegerArgumentType.getInteger(context, "count"), true, kingdom.id()));
        } finally {
            player.getInventory().clearContent();
        }
    }

    private static int propose(CommandContext<CommandSourceStack> context, int period) throws CommandSyntaxException {
        Kingdom seller = kingdom(context, "seller");
        Kingdom buyer = kingdom(context, "buyer");
        TradePackets.TradeChange change = new TradePackets.TradeChange(seller.id(), TradePackets.TradeChange.Action.PROPOSE, buyer.id(), 0,
                ResourceLocationArgument.getId(context, "item").toString(), IntegerArgumentType.getInteger(context, "count"),
                LongArgumentType.getLong(context, "price"), true, false, period);
        return run(context.getSource(), TradeActions.apply(context.getSource().getServer(), null, seller.id(), change));
    }

    private static int accept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Kingdom kingdom = kingdom(context, "kingdom");
        TradePackets.TradeChange change = new TradePackets.TradeChange(kingdom.id(), TradePackets.TradeChange.Action.ACCEPT, null,
                LongArgumentType.getLong(context, "deal"), "", 0, 0, false, false, 0);
        return run(context.getSource(), TradeActions.apply(context.getSource().getServer(), null, kingdom.id(), change));
    }

    private static String name(MinecraftServer server, UUID kingdom) {
        return KingdomWorldApi.kingdom(server, kingdom).map(Kingdom::name).orElse("?");
    }

    private static int deals(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Collection<Deal> deals = KingdomWorldApi.state(server).trade().deals();
        source.sendSuccess(() -> Component.translatable(ADMIN + "deals.header", deals.size()), false);
        for (Deal deal : deals) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "deals.entry", deal.id(), name(server, deal.seller()), name(server, deal.buyer()),
                    deal.item(), deal.count(), deal.price(), deal.status().id(), deal.deliveries()), false);
        }
        return deals.size();
    }

    private static int caravans(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        long now = KingdomWorldApi.gameTime(server);
        Collection<Caravan> caravans = KingdomWorldApi.state(server).trade().caravans();
        source.sendSuccess(() -> Component.translatable(ADMIN + "caravans.header", caravans.size()), false);
        for (Caravan caravan : caravans) {
            Goods first = caravan.cargo().get(0);
            source.sendSuccess(() -> Component.translatable(ADMIN + "caravans.entry", caravan.id(), name(server, caravan.seller()),
                    name(server, caravan.buyer()), first.item(), caravan.items(), caravan.payment(), caravan.state().id(),
                    Math.max(0, caravan.arriveTime() - now)), false);
        }
        return caravans.size();
    }

    /**
     * Every caravan on the way arrives now, without the chance of being lost.
     */
    private static int arrive(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (KingdomWorldApi.isReadOnly(server)) {
            source.sendFailure(KingdomActions.errorMessage(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.READ_ONLY));
            return 0;
        }
        int before = state.trade().caravans().size();
        TradeWork.overrideRoll(() -> 1.0);
        try {
            for (Caravan caravan : new ArrayList<>(state.trade().caravans())) {
                if (caravan.state() != Caravan.State.RETURNING) {
                    TradeWork.arrive(server, caravan, KingdomWorldApi.gameTime(server));
                }
            }
        } finally {
            TradeWork.overrideRoll(null);
        }
        int arrived = before - state.trade().caravans().size();
        source.sendSuccess(() -> Component.translatable(ADMIN + "arrived", arrived, state.trade().caravans().size()), true);
        return arrived;
    }

    private static int rates(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        KingdomWorldState state = KingdomWorldApi.state(server);
        Economy economy = state.economy();
        for (Kingdom kingdom : state.kingdoms().all()) {
            Treasury treasury = economy.treasury(kingdom.id()).orElse(null);
            if (treasury == null) {
                continue;
            }
            Market market = state.trade().existing(kingdom.id()).orElse(null);
            long sold = market == null ? 0 : market.stats(io.github.sekerator.kingdomworld.common.economy.TreasuryActions.day(server)).exported();
            source.sendSuccess(() -> Component.translatable(ADMIN + "rates.entry", kingdom.name(), economy.rate(kingdom.id()),
                    economy.supply(kingdom.id()), economy.coinsOut(kingdom.id()), treasury.funds(), treasury.reserves().size(), sold), false);
        }
        return state.kingdoms().size();
    }
}
