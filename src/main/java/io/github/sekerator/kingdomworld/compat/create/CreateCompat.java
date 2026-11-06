package io.github.sekerator.kingdomworld.compat.create;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.compat.CompatModule;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Integration with Create. For now it only reports that Create is present; later stages add kinetic
 * production for kingdom workshops here.
 *
 * <p>This is the only package allowed to reference Create classes, and it has no compile-time dependency on
 * Create yet.
 */
public final class CreateCompat implements CompatModule {
    public static final String MOD_ID = "create";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void init() {
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        LOGGER.info("Create {} detected, Create compat module active", version);
    }
}
