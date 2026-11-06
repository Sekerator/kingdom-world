package io.github.sekerator.kingdomworld;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.KingdomWorldClient;
import io.github.sekerator.kingdomworld.common.CommonEvents;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.compat.CompatModules;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Mod entry point. Wires registries, configs, networking, events and optional modules.
 *
 * <p>The server is the single source of truth: all simulation and state changes happen on the logical server,
 * clients only display data and send requests that the server validates.
 */
@Mod(KingdomWorld.MOD_ID)
public final class KingdomWorld {
    public static final String MOD_ID = "kingdomworld";
    private static final Logger LOGGER = LogUtils.getLogger();

    public KingdomWorld(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModRegistries.register(modBus);
        KingdomWorldConfig.register(context);
        modBus.addListener(KingdomWorld::onCommonSetup);
        CommonEvents.register();

        // Client classes are only touched on the physical client; a dedicated server never loads them.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KingdomWorldClient.init(modBus));
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            KingdomWorldNetwork.register();
            CompatModules.init();
        });
        LOGGER.debug("KingdomWorld common setup complete");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
