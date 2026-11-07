package io.github.sekerator.kingdomworld.common.magic;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.core.magic.MagicDevice;
import io.github.sekerator.kingdomworld.core.magic.MagicState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Wires the magic of kingdoms into the game: the ward stops natural spawns of monsters in its chunks, mages near players
 * defend the land, once a second the spells, research and devices are looked after, and the wells of the foci pulse.
 */
public final class MagicEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_TICKS = 20;
    private static final int DEFEND_TICKS = 10;

    private MagicEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(MagicEvents::onFinalizeSpawn);
        MinecraftForge.EVENT_BUS.addListener(MagicEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(MagicEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(MagicEvents::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(MagicEvents::onLoggedOut);
    }

    private static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || event.getLevel().isClientSide()) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        try {
            if (MagicWork.wardStops(level, BlockPos.containing(event.getX(), event.getY(), event.getZ()), event.getEntity())) {
                event.setSpawnCancelled(true);
            }
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld ward could not judge a spawn", e);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        try {
            if (tick % CHECK_TICKS == 0) {
                MagicSettings.refresh();
                MagicWork.tick(server);
            }
            if (tick % DEFEND_TICKS == 0) {
                MagicWork.defend(server);
            }
            if (tick % MagicSettings.rules().wellIntervalTicks == 0) {
                MagicWork.wells(server);
            }
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld magic tick failed", e);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MagicSavedData data = MagicSavedData.get(event.getServer());
        MagicState magic = data.state();
        long energy = 0;
        for (var kingdom : magic.kingdoms()) {
            energy += kingdom.energy();
        }
        int learned = magic.allLearned().size();
        LOGGER.info("KingdomWorld magic: {} kingdom(s) with {} energy in all, {} device(s) ({} focus(es), {} gate(s), {} generator(s)), "
                        + "{} player(s) with spells, wells: {}, data format {}{}",
                magic.kingdoms().size(), energy, magic.devices().size(), count(magic, MagicDevice.Kind.FOCUS), count(magic, MagicDevice.Kind.GATE),
                count(magic, MagicDevice.Kind.GENERATOR), learned, MagicWells.any() ? String.join(", ", wellNames()) : "none",
                MagicSavedData.FORMAT_VERSION, data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : "");
    }

    private static long count(MagicState magic, MagicDevice.Kind kind) {
        return magic.devices().stream().filter(device -> device.kind() == kind).count();
    }

    private static java.util.List<String> wellNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        MagicWells.mana().forEach(well -> names.add(well.name()));
        MagicWells.source().forEach(well -> names.add(well.name()));
        return names;
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        MagicSettings.reset();
        MagicWork.reset();
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerSpells.forget(event.getEntity().getUUID());
    }
}
