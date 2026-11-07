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
        MinecraftForge.EVENT_BUS.addListener(LifeEvents::onSpawnSet);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGH, false,
                net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, LifeEvents::onUseBed);
    }

    /**
     * A player's point of return changed: a bed becomes the player's.
     */
    private static void onSpawnSet(net.minecraftforge.event.entity.player.PlayerSetSpawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            LifeWork.spawnSet(player.server, player.getUUID(), event.getSpawnLevel(), event.getNewSpawn());
        }
    }

    /**
     * A player lies down in a bed where a citizen sleeps: the citizen gets up and leaves the bed to the player.
     */
    private static void onUseBed(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) {
            return;
        }
        net.minecraft.core.BlockPos head = state.getValue(net.minecraft.world.level.block.BedBlock.PART)
                == net.minecraft.world.level.block.state.properties.BedPart.HEAD ? event.getPos()
                : event.getPos().relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING));
        for (io.github.sekerator.kingdomworld.common.citizen.CitizenEntity body : level.getEntitiesOfClass(
                io.github.sekerator.kingdomworld.common.citizen.CitizenEntity.class, new net.minecraft.world.phys.AABB(head).inflate(1.5),
                body -> body.isSleeping() && body.getSleepingPos().filter(head::equals).isPresent())) {
            body.stopSleeping();
        }
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
        LifeSettings.noChanceIllness(server instanceof net.minecraft.gametest.framework.GameTestServer);
        // Every way a citizen gets a home asks the same rule: never a room of a tavern or the bed of a player.
        KingdomWorldApi.state(server).citizens().setHomeRule((kingdom, bed) ->
                io.github.sekerator.kingdomworld.common.construction.BuildingEffects.bedUsable(server, kingdom, bed));
        LifeSavedData data = LifeSavedData.get(server);
        LifeState state = data.state();
        LifeWork.loaded(server);
        int food = 0;
        int drink = 0;
        java.util.Set<String> mods = new java.util.TreeSet<>();
        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
            boolean isFood = stack.is(LifeWork.TAVERN_FOOD);
            boolean isDrink = stack.is(LifeWork.TAVERN_DRINK);
            food += isFood ? 1 : 0;
            drink += isDrink ? 1 : 0;
            if (isFood || isDrink) {
                mods.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace());
            }
        }
        LOGGER.info("KingdomWorld taverns serve {} kind(s) of food and {} of drink from the tags, of the mods {}", food, drink, mods);
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
            // A bed a player slept in before the life of cities knew of it.
            if (!(player instanceof FakePlayer) && player.getRespawnPosition() != null) {
                LifeWork.spawnSet(player.server, player.getUUID(), player.getRespawnDimension(), player.getRespawnPosition());
            }
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
