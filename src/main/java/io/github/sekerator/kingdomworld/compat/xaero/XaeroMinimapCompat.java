package io.github.sekerator.kingdomworld.compat.xaero;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.compat.CompatModule;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import xaero.common.minimap.highlight.DimensionHighlighterHandler;
import xaero.common.minimap.highlight.HighlighterRegistry;
import xaero.common.minimap.write.MinimapWriter;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;

import java.util.List;
import java.util.Set;

/**
 * Kingdoms on Xaero's Minimap, through its chunk highlights like on the World Map: fill and borders in the kingdom's
 * color and the kingdom and ruler in the minimap's information lines. Client only.
 *
 * <p>Classes of Xaero's Minimap are named only in this package. If they changed, the integration stays off and says
 * so in one line of the log.
 */
public final class XaeroMinimapCompat implements CompatModule, XaeroMaps.Target {
    public static final String MOD_ID = "xaerominimap";
    private static final Logger LOGGER = LogUtils.getLogger();

    private MinimapKingdomHighlighter highlighter;
    private MinimapSession attached;
    private String version = "unknown";

    @Override
    public void init() {
        version = ModList.get().getModContainerById(MOD_ID).map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        if (!KingdomWorldConfig.CLIENT.xaeroMaps.get()) {
            LOGGER.info("Xaero's Minimap {} detected, kingdoms on it switched off in the client config", version);
            return;
        }
        try {
            List<String> missing = XaeroLinks.unimplemented(MinimapKingdomHighlighter.class);
            if (!missing.isEmpty()) {
                LOGGER.warn("Xaero's Minimap {}: its highlighters changed ({}), kingdoms are not shown on it", version, missing);
                return;
            }
            highlighter = new MinimapKingdomHighlighter(KingdomHighlights.INSTANCE);
        } catch (LinkageError e) {
            LOGGER.warn("Xaero's Minimap {}: its classes are not as expected ({}), kingdoms are not shown on it", version, e.toString());
            return;
        }
        XaeroMaps.add(this);
        LOGGER.info("Xaero's Minimap {} detected, kingdoms shown as chunk highlights", version);
    }

    @Override
    public String name() {
        return "Xaero's Minimap " + version;
    }

    @Override
    public boolean attach() throws Exception {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null || session.getProcessor() == null) {
            attached = null;
            return false;
        }
        if (session != attached) {
            MinimapWriter writer = session.getProcessor().getMinimapWriter();
            if (writer == null) {
                return false;
            }
            Object registry = XaeroLinks.fieldOfType(writer, HighlighterRegistry.class);
            if (!XaeroLinks.wrapsOurs(registry)) {
                XaeroLinks.addToRegistry(registry, highlighter);
            }
            attached = session;
            refresh(null);
        }
        return true;
    }

    @Override
    public void refresh(Set<Long> regions) {
        if (attached == null) {
            return;
        }
        DimensionHighlighterHandler handler = attached.getProcessor().getMinimapWriter().getDimensionHighlightHandler();
        if (handler != null) {
            handler.requestRefresh();
        }
    }
}
