package io.github.sekerator.kingdomworld.common.flag;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Wires the flags of kingdoms into the game: the kingdoms of the world get their flags when the server starts and whenever new
 * ones appear, the clients learn every flag when they join and what changed once a second.
 */
public final class FlagEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_TICKS = 20;
    /** The kingdoms as they were at the last check: the version of their register and the number of those of the world. */
    private static long lastKingdoms = Long.MIN_VALUE;

    private FlagEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(FlagEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(FlagEvents::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(FlagEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(FlagEvents::onServerStopped);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            FlagWork.fillWorldFlags(server);
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld could not give the kingdoms of the world their flags", e);
        }
        lastKingdoms = kingdoms(server);
        FlagDirectory.pushNow(server);
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            FlagDirectory.sendAll(player);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = kingdoms(server);
        if (now != lastKingdoms) {
            lastKingdoms = now;
            try {
                FlagWork.fillWorldFlags(server);
            } catch (RuntimeException e) {
                LOGGER.error("KingdomWorld could not give the kingdoms of the world their flags", e);
            }
            FlagDirectory.pushNow(server);
        }
    }

    /**
     * What tells that kingdoms appeared, changed or went: the version of their register, and the number of kingdoms of the world,
     * whose records come a moment after the kingdom itself.
     */
    private static long kingdoms(MinecraftServer server) {
        return KingdomWorldApi.state(server).kingdoms().version() * 31 + NpcWork.state(server).kingdoms().size();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        lastKingdoms = Long.MIN_VALUE;
        FlagDirectory.reset();
    }
}
