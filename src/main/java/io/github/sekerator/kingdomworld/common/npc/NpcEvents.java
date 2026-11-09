package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Wires the kingdoms of the world into the game: the plan and the appearance move on every server tick.
 */
public final class NpcEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private NpcEvents() {
    }

    public static void register() {
        // Rulers hold feasts through the life of towns, as a king calls them: the treasury pays, the storehouses treat.
        RulerFestivals.setHost(new RulerFestivals.Host() {
            @Override
            public io.github.sekerator.kingdomworld.core.kingdom.Outcome hold(MinecraftServer server, java.util.UUID kingdomId, long budget) {
                return io.github.sekerator.kingdomworld.common.life.LifeWork.callFestival(server, null, kingdomId).success()
                        ? io.github.sekerator.kingdomworld.core.kingdom.Outcome.DONE
                        : io.github.sekerator.kingdomworld.core.kingdom.Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.UNCHANGED);
            }

            @Override
            public long cost(MinecraftServer server, java.util.UUID kingdomId) {
                int people = io.github.sekerator.kingdomworld.common.api.KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId).size();
                return io.github.sekerator.kingdomworld.core.life.Festivals.cost(people, io.github.sekerator.kingdomworld.common.life.LifeSettings.rules());
            }
        });
        MinecraftForge.EVENT_BUS.addListener(NpcEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(NpcEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(NpcEvents::onServerStopped);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 == 0) {
            NpcSettings.refresh();
        }
        try {
            NpcWork.tick(server);
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld kingdoms of the world failed", e);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        NpcWork.reset();
        NpcSavedData data = NpcSavedData.get(server);
        NpcState state = data.state();
        NpcWork.loaded(server);
        long planned = state.sites().stream().filter(site -> site.status() == NpcSite.Status.PLANNED).count();
        long spawned = state.sites().stream().filter(site -> site.status() == NpcSite.Status.SPAWNED).count();
        long founded = state.sites().stream().filter(site -> site.status() == NpcSite.Status.FOUNDED).count();
        LOGGER.info("KingdomWorld kingdoms of the world: {} place(s) planned ({} waiting, {} founded, {} appeared{}), {} kingdom(s), plan {}, "
                        + "data format {}{}, buildings raised by {}",
                state.sites().size(), planned, founded, spawned, state.spawning().map(site -> ", 1 appearing at " + site.stage().id()).orElse(""),
                state.kingdoms().size(), state.planned() ? "made" : "being made", NpcSavedData.FORMAT_VERSION,
                data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : "", NpcSettings.build().mode().id());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        NpcSettings.reset();
        NpcWork.reset();
    }
}
