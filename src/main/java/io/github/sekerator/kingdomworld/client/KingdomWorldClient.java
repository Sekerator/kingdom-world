package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.client.network.ClientKingdomCache;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListRequestPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Client-only bootstrap. Called from the mod constructor on the physical client only.
 */
public final class KingdomWorldClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Opens the kingdom map; K by default, changeable in the controls. */
    public static final KeyMapping MAP_KEY = new KeyMapping("key.kingdomworld.map", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.kingdomworld");
    private static boolean titleScreenSeen;

    private KingdomWorldClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(KingdomWorldClient::onClientSetup);
        modBus.addListener(KingdomWorldClient::onRegisterKeys);
        MinecraftForge.EVENT_BUS.addListener(KingdomWorldClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(KingdomWorldClient::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(KingdomWorldClient::onScreenOpening);
        MinecraftForge.EVENT_BUS.addListener(KingdomWorldClient::onClientTick);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.debug("KingdomWorld client setup complete");
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(MAP_KEY);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientSelfTest.onClientTick();
        Minecraft minecraft = Minecraft.getInstance();
        while (MAP_KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ClientHooks.openKingdomMap();
            }
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientKingdomCache.clear();
        ClientTerritoryCache.clear();
        if (KingdomWorldConfig.CLIENT.requestKingdomListOnJoin.get()) {
            KingdomWorldNetwork.sendToServer(new KingdomListRequestPacket(0, KingdomListRequestPacket.MAX_PAGE_SIZE));
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientKingdomCache.clear();
        ClientTerritoryCache.clear();
    }

    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (!titleScreenSeen && event.getNewScreen() instanceof TitleScreen) {
            titleScreenSeen = true;
            LOGGER.debug("KingdomWorld client reached the title screen");
        }
        if (event.getNewScreen() instanceof TitleScreen) {
            ClientSelfTest.onTitleScreen();
        }
    }
}
