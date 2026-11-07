package io.github.sekerator.kingdomworld.compat.xaero;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.compat.CompatModule;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.highlight.DimensionHighlighterHandler;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

import java.util.List;
import java.util.Set;

/**
 * Kingdoms on Xaero's World Map. The map has no official API for other mods; the land is shown through its chunk
 * highlights (the way it shows claims of Open Parties and Claims): fill and borders in the kingdom's color, a stronger
 * capital chunk, and the kingdom, ruler and currency when the pointer rests on the land. Client only.
 *
 * <p>Classes of Xaero's World Map are named only in this package. If they changed, the integration stays off and says
 * so in one line of the log.
 */
public final class XaeroWorldMapCompat implements CompatModule, XaeroMaps.Target {
    public static final String MOD_ID = "xaeroworldmap";
    private static final Logger LOGGER = LogUtils.getLogger();

    private WorldMapKingdomHighlighter highlighter;
    private WorldMapSession attached;
    private String version = "unknown";

    @Override
    public void init() {
        version = ModList.get().getModContainerById(MOD_ID).map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        if (!KingdomWorldConfig.CLIENT.xaeroMaps.get()) {
            LOGGER.info("Xaero's World Map {} detected, kingdoms on it switched off in the client config", version);
            return;
        }
        try {
            List<String> missing = XaeroLinks.unimplemented(WorldMapKingdomHighlighter.class);
            if (!missing.isEmpty()) {
                LOGGER.warn("Xaero's World Map {}: its highlighters changed ({}), kingdoms are not shown on it", version, missing);
                return;
            }
            highlighter = new WorldMapKingdomHighlighter(KingdomHighlights.INSTANCE);
        } catch (LinkageError e) {
            LOGGER.warn("Xaero's World Map {}: its classes are not as expected ({}), kingdoms are not shown on it", version, e.toString());
            return;
        }
        XaeroMaps.add(this);
        LOGGER.info("Xaero's World Map {} detected, kingdoms shown as chunk highlights", version);
    }

    @Override
    public String name() {
        return "Xaero's World Map " + version;
    }

    @Override
    public boolean attach() throws Exception {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null) {
            attached = null;
            return false;
        }
        if (session != attached) {
            MapProcessor processor = session.getMapProcessor();
            if (processor == null || processor.getHighlighterRegistry() == null) {
                return false;
            }
            XaeroLinks.addToRegistry(processor.getHighlighterRegistry(), highlighter);
            attached = session;
            refresh(null);
        }
        return true;
    }

    @Override
    public void refresh(Set<Long> regions) {
        String dimension = KingdomHighlights.INSTANCE.dimensionId();
        if (attached == null || dimension == null) {
            return;
        }
        MapWorld world = attached.getMapProcessor().getMapWorld();
        if (world == null) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) {
            return;
        }
        MapDimension mapDimension = world.getDimension(ResourceKey.create(Registries.DIMENSION, id));
        if (mapDimension == null || mapDimension.getHighlightHandler() == null) {
            return;
        }
        DimensionHighlighterHandler handler = mapDimension.getHighlightHandler();
        if (regions == null) {
            handler.clearCachedHashes();
        } else {
            for (long region : regions) {
                handler.clearCachedHash(ChunkKey.x(region), ChunkKey.z(region));
            }
        }
    }
}
