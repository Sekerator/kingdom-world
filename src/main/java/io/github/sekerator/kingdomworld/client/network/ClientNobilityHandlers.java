package io.github.sekerator.kingdomworld.client.network;

import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.network.data.NobilityViews;
import net.minecraft.client.Minecraft;

/**
 * Client-side handling of the packets of the nobility. Runs on the client main thread.
 */
public final class ClientNobilityHandlers {
    private ClientNobilityHandlers() {
    }

    public static void handleTab(NobilityViews.TabView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateNobility(view);
        }
    }
}
