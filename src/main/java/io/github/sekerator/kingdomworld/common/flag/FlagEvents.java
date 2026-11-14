package io.github.sekerator.kingdomworld.common.flag;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.block.FlagBlocks;
import io.github.sekerator.kingdomworld.common.block.KingdomFlagBlockEntity;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Wires the flags of kingdoms into the game: the kingdoms of the world get their flags when the server starts and whenever new
 * ones appear, the clients learn every flag when they join and what changed once a second, and the keeper of the flags in the
 * world works every tick, takes in the chunks that load and notes the flags members of their kingdom take down.
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
        MinecraftForge.EVENT_BUS.addListener(FlagEvents::onChunkLoad);
        // Last and only when no protection refused it: a break that does not happen takes nothing down.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, FlagEvents::onBreak);
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
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS == 0) {
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
        try {
            FlagWork.tick(server);
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld could not look after the flags in the world", e);
        }
    }

    /**
     * What tells that kingdoms appeared, changed or went: the version of their register, and the number of kingdoms of the world,
     * whose records come a moment after the kingdom itself.
     */
    private static long kingdoms(MinecraftServer server) {
        return KingdomWorldApi.state(server).kingdoms().version() * 31 + NpcWork.state(server).kingdoms().size();
    }

    /**
     * A chunk loaded: its buildings wait for a pass, nothing in the world changes while it loads.
     */
    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FlagWork.chunkLoaded(level, event.getChunk().getPos());
        }
    }

    /**
     * A member of the kingdom of a flag takes it down: the place stays empty.
     */
    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !FlagBlocks.is(event.getState())
                || !(level.getBlockEntity(event.getPos()) instanceof KingdomFlagBlockEntity flag) || flag.kingdom() == null) {
            return;
        }
        UUID kingdom = flag.kingdom();
        Optional<Kingdom> own = KingdomWorldApi.kingdomOf(level.getServer(), event.getPlayer().getUUID());
        if (own.isPresent() && own.get().id().equals(kingdom)) {
            FlagWork.takenDown(level, event.getPos(), flag);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        lastKingdoms = Long.MIN_VALUE;
        FlagDirectory.reset();
        FlagWork.reset();
    }
}
