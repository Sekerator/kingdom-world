package io.github.sekerator.kingdomworld.compat.xaero;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.map.ClientMapSync;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Drives the highlighters on Xaero's World Map and Minimap: every half second it brings {@link KingdomHighlights} up
 * to date, hands the highlighter to a map session that appeared (a new world or a reconnect), and asks the maps to
 * redraw the regions where land changed. A map whose classes do not behave as expected is switched off with one line
 * in the log; the game goes on without it.
 */
final class XaeroMaps {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INTERVAL_TICKS = 10;
    private static final List<Target> TARGETS = new CopyOnWriteArrayList<>();
    private static boolean registered;
    private static int ticks;

    private XaeroMaps() {
    }

    /**
     * One of Xaero's maps.
     */
    interface Target {
        String name();

        /**
         * Hands the highlighter to the current session of the map if it does not have it yet.
         *
         * @return whether the map has it now
         */
        boolean attach() throws Exception;

        /**
         * Redraws regions of the current dimension; {@code null} means all of them.
         */
        void refresh(Set<Long> regions) throws Exception;
    }

    static void add(Target target) {
        TARGETS.add(target);
        KingdomHighlights.INSTANCE.listen();
        ClientMapSync.enable("xaero", XaeroMaps::stats);
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.addListener(XaeroMaps::onClientTick);
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        KingdomHighlights.INSTANCE.sync();
        Set<Long> dirty = KingdomHighlights.INSTANCE.takeDirty();
        for (Target target : TARGETS) {
            try {
                if (target.attach() && (dirty == null || !dirty.isEmpty())) {
                    target.refresh(dirty);
                }
            } catch (Exception | LinkageError e) {
                TARGETS.remove(target);
                LOGGER.warn("KingdomWorld: {} does not work as expected ({}), kingdoms are not shown on it", target.name(), e.toString());
            }
        }
    }

    private static String stats() {
        return TARGETS.stream().map(Target::name).toList() + ", " + KingdomHighlights.INSTANCE.landChunks() + " chunk(s) of land";
    }
}
