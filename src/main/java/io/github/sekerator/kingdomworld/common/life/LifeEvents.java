package io.github.sekerator.kingdomworld.common.life;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.LifeViews;
import io.github.sekerator.kingdomworld.common.network.packet.LifePackets;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.life.LifeState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wires the life of cities into the server: the professions and the rumors at start, every few seconds the festivals of the
 * day of founding, the fairs, the rooms whose day is over and the atlas of the players.
 */
public final class LifeEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, List<LifeViews.MapEvent>> SENT = new HashMap<>();

    private LifeEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(LifeEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(LifeEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(LifeEvents::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(LifeEvents::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(LifeEvents::onLoggedOut);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        try {
            if (tick % 20 == 0) {
                LifeSettings.refresh();
            }
            if (tick % 100 == 7 && !LifeWork.isReadOnly(server)) {
                LifeWork.foundingDays(server);
                LifeFairs.tick(server);
                TavernActions.endRooms(server);
            }
            if (tick % 40 == 13) {
                syncMaps(server);
            }
            if (tick % 6_000 == 11) {
                LifeWork.cleanUp(server);
            }
        } catch (RuntimeException e) {
            LOGGER.error("The life of cities failed in a tick", e);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        LifeSavedData data = LifeSavedData.get(server);
        LifeState state = data.state();
        LifeWork.loaded(server);
        LOGGER.info("KingdomWorld life of cities: {} ill, {} room(s), {} kingdom(s) with festivals, data format {}{}", state.allSick().size(),
                state.rooms().size(), state.kingdoms().size(), LifeSavedData.FORMAT_VERSION,
                data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : "");
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        LifeSettings.reset();
        LifeWork.reset();
        LifeRumors.reset();
        TavernActions.reset();
        SENT.clear();
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SENT.remove(player.getUUID());
            TavernActions.loggedIn(player);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
        TavernActions.forget(event.getEntity().getUUID());
    }

    /**
     * Festivals and fairs of today, for the atlas.
     */
    public static List<LifeViews.MapEvent> mapEvents(MinecraftServer server) {
        List<LifeViews.MapEvent> events = new ArrayList<>();
        LifeState life = LifeWork.state(server);
        long today = LifeWork.today(server);
        for (CityWalls.Place place : CityWalls.places(server)) {
            int color = KingdomWorldApi.kingdom(server, place.kingdomId()).map(Kingdom::color).orElse(0x808080);
            if (life.fairDay(LifeFairs.key(place)) == today) {
                events.add(new LifeViews.MapEvent(place.dimension(), place.x(), place.z(), true, color));
            }
            if (place.cityId() == 0 && life.festival(place.kingdomId()).festivalDay() == today) {
                events.add(new LifeViews.MapEvent(place.dimension(), place.x(), place.z(), false, color));
            }
        }
        return events;
    }

    private static void syncMaps(MinecraftServer server) {
        if (server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        List<LifeViews.MapEvent> events = mapEvents(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof FakePlayer) {
                continue;
            }
            if (!events.equals(SENT.get(player.getUUID()))) {
                SENT.put(player.getUUID(), events);
                KingdomWorldNetwork.sendToPlayer(player, new LifePackets.MapSync(events));
            }
        }
    }
}
