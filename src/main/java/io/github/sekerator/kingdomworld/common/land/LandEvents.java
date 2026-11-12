package io.github.sekerator.kingdomworld.common.land;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

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
        }
        return territory;
    }

    /**
     * A holder that is no kingdom any more lets its province go; a claim inside a province of another kingdom stays a claim,
     * with a word in the log.
     */
    private static void check(MinecraftServer server, ProvinceMap map, TerritoryIndex territory) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Province province : map.provinces()) {
            UUID owner = map.owner(province.id());
            if (owner != null && state.kingdoms().get(owner).isEmpty()) {
                LOGGER.warn("Province {} was held by {}, which is no kingdom any more: it belongs to nobody now", province.id(), owner);
                map.restore(province.id(), null, map.acquiredDay(province.id()), map.hold(province.id()).orElse(null));
            }
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
