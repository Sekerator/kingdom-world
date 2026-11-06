package io.github.sekerator.kingdomworld.common;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.command.KingdomWorldCommand;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Game event listeners that run on both physical sides (they act on the logical server).
 */
public final class CommonEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CommonEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(CommonEvents::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(CommonEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(CommonEvents::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(CommonEvents::onPlayerLoggedOut);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        KingdomWorldCommand.register(event.getDispatcher());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
        LOGGER.info("KingdomWorld server config: maxActiveNpcs={}, simulationIntervalTicks={}, afkTimeoutSeconds={}, combatTagSeconds={}",
                config.maxActiveNpcs.get(), config.simulationIntervalTicks.get(),
                config.afkTimeoutSeconds.get(), config.combatTagSeconds.get());
        // Load the data eagerly so that problems with it show up at startup rather than on first use.
        int kingdoms = KingdomSavedData.get(event.getServer()).registry().size();
        LOGGER.info("KingdomWorld loaded {} kingdom(s), data format {}", kingdoms, KingdomSavedData.FORMAT_VERSION);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ServerRequestGuard.reset();
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerRequestGuard.forget(event.getEntity().getUUID());
    }
}
