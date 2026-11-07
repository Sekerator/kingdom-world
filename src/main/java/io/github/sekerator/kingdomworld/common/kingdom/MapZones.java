package io.github.sekerator.kingdomworld.common.kingdom;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.core.territory.MapZone;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Sources of the zones on the war layers of the maps (contested land, battle zones). None is registered yet: the
 * layers exist and stay empty until wars register a source here. Read on the server thread.
 */
public final class MapZones {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Function<MinecraftServer, Collection<MapZone>>> SOURCES = new CopyOnWriteArrayList<>();

    private MapZones() {
    }

    public static void register(Function<MinecraftServer, Collection<MapZone>> source) {
        SOURCES.add(Objects.requireNonNull(source, "source"));
    }

    /**
     * All zones of all sources; a failing source is logged and skipped.
     */
    public static List<MapZone> all(MinecraftServer server) {
        List<MapZone> zones = new ArrayList<>();
        for (Function<MinecraftServer, Collection<MapZone>> source : SOURCES) {
            try {
                zones.addAll(source.apply(server));
            } catch (RuntimeException e) {
                LOGGER.error("A source of map zones failed and is skipped", e);
            }
        }
        return zones;
    }
}
