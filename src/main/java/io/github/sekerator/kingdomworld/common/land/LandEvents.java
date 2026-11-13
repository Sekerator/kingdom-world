package io.github.sekerator.kingdomworld.common.land;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puts the provinces of the world under the land of the kingdoms: when the server has started, and again whenever the data of
 * the kingdoms was loaded anew, so that whoever asks who owns a chunk sees the provinces. Every tick it tells the map mods of
 * up to {@value #CHUNKS_PER_TICK} chunks of provinces that changed hands.
 */
public final class LandEvents {
    /** Chunks of provinces that changed hands told to the listeners of other mods in a tick at most. */
    public static final int CHUNKS_PER_TICK = 4096;
    private static final Logger LOGGER = LogUtils.getLogger();

    private LandEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(LandEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(LandEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent event) -> {
            Wilderness.reset();
            StateGrowth.reset();
        });
        // Players act in the wilds of the kingdoms of the world as on land of nobody.
        Wilderness.register();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        attach(event.getServer());
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        TerritoryIndex territory = attach(server);
        if (territory.provinceChunksPending()) {
            territory.drainProvinceChunks(CHUNKS_PER_TICK);
        }
        if (server.getTickCount() % 20 == 7) {
            territory.provinceMap().ifPresent(map -> ProvinceTrade.expire(server, map));
        }
        StateGrowth.tick(server);
    }

    /**
     * Puts the provinces under the land of the kingdoms if they are not there yet.
     *
     * @return the land of the kingdoms
     */
    public static TerritoryIndex attach(MinecraftServer server) {
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        ProvinceMap map = ProvinceSavedData.get(server).map();
        if (territory.provinceMap().orElse(null) != map) {
            check(server, map, territory);
            territory.attachProvinces(map);
            // The money of charters waits in escrow for the sellers: it counts with the money of its currency.
            KingdomWorldApi.state(server).economy().setHeldElsewhere("charters", map::heldMoney);
        }
        return territory;
    }

    /**
     * Words in the log, nothing changed: provinces held by kingdoms the data of the kingdoms does not know (the data failed to
     * load, or was put back from a copy) keep their holders, so that they are theirs again once the kingdoms are back; a claim
     * inside a province of another kingdom stays a claim.
     */
    private static void check(MinecraftServer server, ProvinceMap map, TerritoryIndex territory) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        List<Integer> unknown = map.heldByUnknown(owner -> state.kingdoms().get(owner).isPresent());
        if (!unknown.isEmpty()) {
            LOGGER.warn("{} province(s) are held by kingdoms that are not in the data of the kingdoms (first: {}); they keep their holders "
                    + "and nothing is saved over them", unknown.size(), unknown.subList(0, Math.min(8, unknown.size())));
        }
        int under = 0;
        for (Map.Entry<Long, UUID> claim : territory.claimsIn(map.dimension()).entrySet()) {
            UUID holder = map.ownerAt(claim.getKey());
            if (holder != null && !holder.equals(claim.getValue())) {
                under++;
            }
        }
        if (under > 0) {
            LOGGER.warn("{} claimed chunk(s) lie in provinces of other kingdoms: the claims keep them", under);
        }
        if (map.size() > 0) {
            LOGGER.info("Provinces in {}: {} in {} tile(s), about {} KB", map.dimension(), map.size(), map.tileCount(), map.memoryBytes() / 1024);
        }
    }
}
