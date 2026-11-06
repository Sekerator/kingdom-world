package io.github.sekerator.kingdomworld.client;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.network.ClientKingdomCache;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListRequestPacket;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

/**
 * Client-only bootstrap. Called from the mod constructor on the physical client only.
 */
public final class KingdomWorldClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean titleScreenSeen;

    private KingdomWorldClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(KingdomWorldClient::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(KingdomWorldClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(KingdomWorldClient::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(KingdomWorldClient::onScreenOpening);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.debug("KingdomWorld client setup complete");
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientKingdomCache.clear();
        if (KingdomWorldConfig.CLIENT.requestKingdomListOnJoin.get()) {
            KingdomWorldNetwork.sendToServer(new KingdomListRequestPacket(0, KingdomListRequestPacket.MAX_PAGE_SIZE));
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientKingdomCache.clear();
    }

    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (!titleScreenSeen && event.getNewScreen() instanceof TitleScreen) {
            titleScreenSeen = true;
            LOGGER.debug("KingdomWorld client reached the title screen");
        }
    }
}
