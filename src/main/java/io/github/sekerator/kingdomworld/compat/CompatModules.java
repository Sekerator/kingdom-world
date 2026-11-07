package io.github.sekerator.kingdomworld.compat;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.compat.bluemap.BlueMapCompat;
import io.github.sekerator.kingdomworld.compat.create.CreateCompat;
import io.github.sekerator.kingdomworld.compat.dynmap.DynmapCompat;
import io.github.sekerator.kingdomworld.compat.journeymap.JourneyMapCompat;
import io.github.sekerator.kingdomworld.compat.xaero.XaeroMinimapCompat;
import io.github.sekerator.kingdomworld.compat.xaero.XaeroWorldMapCompat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Activates soft compatibility modules for mods that are installed.
 *
 * <p>Modules are listed by mod id with the physical side they run on and a factory: modules of client-only mods
 * (JourneyMap, Xaero's maps) are created on the client only, so a dedicated server never loads their classes. The
 * factory is a supplier of a supplier so that the module class is only resolved when the outer supplier runs, which
 * happens only after {@link ModList} reports the mod as loaded. A failing module is logged and skipped; it never
 * prevents the game from starting.
 */
public final class CompatModules {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * @param side the physical side the module runs on, {@code null} for both
     */
    private record Entry(String modId, Dist side, Supplier<Supplier<CompatModule>> factory) {
    }

    private static final List<Entry> MODULES = List.of(
            new Entry("create", null, () -> CreateCompat::new),
            new Entry("journeymap", Dist.CLIENT, () -> JourneyMapCompat::new),
            new Entry("xaeroworldmap", Dist.CLIENT, () -> XaeroWorldMapCompat::new),
            new Entry("xaerominimap", Dist.CLIENT, () -> XaeroMinimapCompat::new),
            new Entry("bluemap", null, () -> BlueMapCompat::new),
            new Entry("dynmap", null, () -> DynmapCompat::new));

    private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();

    private CompatModules() {
    }

    public static void init() {
        List<? extends String> disabled = KingdomWorldConfig.COMMON.disabledCompatModules.get();
        for (Entry entry : MODULES) {
            if (!ModList.get().isLoaded(entry.modId())) {
                LOGGER.debug("{} not installed, compat module inactive", entry.modId());
                continue;
            }
            if (entry.side() != null && entry.side() != FMLEnvironment.dist) {
                LOGGER.debug("{} compat module only runs on the {}", entry.modId(), entry.side());
                continue;
            }
            if (disabled.contains(entry.modId())) {
                LOGGER.info("{} compat module disabled in config", entry.modId());
                continue;
            }
            try {
                entry.factory().get().get().init();
                ACTIVE.add(entry.modId());
            } catch (Exception | LinkageError e) {
                LOGGER.error("{} compat module failed to initialise and stays inactive", entry.modId(), e);
            }
        }
    }

    public static boolean isActive(String modId) {
        return ACTIVE.contains(modId);
    }
}
