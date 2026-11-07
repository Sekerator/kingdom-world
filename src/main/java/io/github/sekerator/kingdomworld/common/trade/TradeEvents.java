package io.github.sekerator.kingdomworld.common.trade;

import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Wires trade into the game: once a second the server moves deals, caravans and orders from abroad on and counts the
 * exchange rates when a new game day began; players get the caravans for their map and the goods that wait for them when
 * they join.
 */
public final class TradeEvents {
    private static final int CHECK_TICKS = 20;

    private TradeEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(TradeEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(TradeEvents::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(TradeEvents::onTags);
    }

    public static void reset() {
        TradeSettings.reset();
        TradeWork.reset();
        MerchantBehavior.reset();
        MarketActions.invalidate();
    }

    private static void onTags(TagsUpdatedEvent event) {
        MarketActions.invalidate();
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        TradeSettings.refresh();
        TradeWork.tick(server);
        TradeWork.updateRates(server);
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        KingdomWorldNetwork.sendToPlayer(player, new TradePackets.Caravans(TradeWork.caravans(player.server)));
        StallActions.deliverReturns(player);
    }
}
