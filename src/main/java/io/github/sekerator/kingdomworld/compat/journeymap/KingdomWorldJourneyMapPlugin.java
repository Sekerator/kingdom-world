package io.github.sekerator.kingdomworld.compat.journeymap;

import io.github.sekerator.kingdomworld.KingdomWorld;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;

/**
 * The plugin JourneyMap discovers by its annotation and initialises with its client API. Without JourneyMap nothing
 * reads the annotation and this class is never loaded.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class KingdomWorldJourneyMapPlugin implements IClientPlugin {

    @Override
    public String getModId() {
        return KingdomWorld.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI api) {
        JourneyMapLayer.attach(api);
    }
}
