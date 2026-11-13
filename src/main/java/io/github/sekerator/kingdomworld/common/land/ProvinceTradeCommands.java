package io.github.sekerator.kingdomworld.common.land;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * The commands of players for the provinces of the states of the world:
 *
 * <pre>
 * /kingdom province price [id]   what a province costs (the one where one stands without a number) and why it is not for sale
 * /kingdom province buy &lt;id&gt;     buys it for one's kingdom, or a charter for it without one
 * </pre>
 */
public final class ProvinceTradeCommands {
    private static final String KEY = "kingdomworld.province.sale.";

    private ProvinceTradeCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> playerCommand() {
        return Commands.literal("province")
                .then(Commands.literal("price")
                        .executes(context -> price(context.getSource(), here(context.getSource())))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1, ProvinceMap.MAX_ID))
                                .executes(context -> price(context.getSource(), IntegerArgumentType.getInteger(context, "id")))))
                .then(Commands.literal("buy")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1, ProvinceMap.MAX_ID))
                                .executes(context -> buy(context.getSource(), IntegerArgumentType.getInteger(context, "id")))));
    }

    private static int here(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ProvinceMap map = LandEvents.attach(source.getServer()).provinceMap().orElse(null);
        if (map == null || !map.dimension().equals(io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(player.level()))) {
            return 0;
        }
        return map.provinceAt(ChunkKey.of(player.chunkPosition().x, player.chunkPosition().z));
    }

    private static int price(CommandSourceStack source, int id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        if (id == 0) {
            source.sendFailure(Component.translatable(KEY + "none_here"));
            return 0;
        }
        UUID buyer = KingdomWorldApi.kingdomOf(server, player.getUUID()).map(Kingdom::id).orElse(null);
        ProvinceTrade.Quote quote = ProvinceTrade.quote(server, id, buyer, player.getUUID());
        if (quote.price() > 0) {
            Component cost = MoneyTexts.amount(quote.price(), Purses.units(server, quote.seller()));
            Component paid = buyer == null ? Component.empty() : Component.translatable(KEY + "price_yours",
                    MoneyTexts.amount(KingdomWorldApi.state(server).economy().landCost(buyer, quote.seller(), quote.price()), Purses.units(server, buyer)));
            source.sendSuccess(() -> Component.translatable(KEY + "price", ProvinceTrade.name(server, id), WarWork.name(server, quote.seller()),
                    quote.chunks(), cost, paid), false);
        }
        Optional<Component> why = quote.refusal().map(refusal -> ProvinceTrade.refusalText(server, quote));
        if (why.isPresent()) {
            source.sendFailure(why.get());
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(buyer == null ? KEY + "for_sale_charter" : KEY + "for_sale", id), false);
        return (int) Math.min(Integer.MAX_VALUE, quote.price());
    }

    private static int buy(CommandSourceStack source, int id) throws CommandSyntaxException {
        KingdomActions.Feedback feedback = ProvinceTrade.buy(source.getPlayerOrException(), id);
        if (feedback.success()) {
            source.sendSuccess(feedback::message, true);
            return 1;
        }
        source.sendFailure(feedback.message());
        return 0;
    }
}
