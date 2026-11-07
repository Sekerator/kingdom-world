package io.github.sekerator.kingdomworld.compat.journeymap;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.compat.CompatModule;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Integration with JourneyMap through its official API (2.x). JourneyMap finds {@link KingdomWorldJourneyMapPlugin}
 * by its annotation and hands it the API; this module only switches the integration on. Client only.
 *
 * <p>This package and {@code compat.xaero} are the client-side compat modules: they may use client classes, and
 * {@code CompatModules} creates them on the physical client only.
 */
public final class JourneyMapCompat implements CompatModule {
    public static final String MOD_ID = "journeymap";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean active;

    @Override
    public void init() {
        active = true;
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        LOGGER.info("JourneyMap {} detected, JourneyMap compat module active", version);
        JourneyMapLayer.start();
    }

    static boolean active() {
        return active;
    }
}
