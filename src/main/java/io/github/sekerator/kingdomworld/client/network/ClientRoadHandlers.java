package io.github.sekerator.kingdomworld.client.network;

import io.github.sekerator.kingdomworld.client.map.ClientRoadCache;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import net.minecraft.client.Minecraft;

/**
 * Client-side handling of the packets of roads. Runs on the client main thread.
 */
public final class ClientRoadHandlers {

    private ClientRoadHandlers() {
    }

    public static void handleTab(RoadViews.RoadTabView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateRoads(view);
        }
    }

    public static void handlePlan(RoadViews.PlanView view) {
        ClientRoadCache.setPlan(view);
        if (Minecraft.getInstance().screen instanceof KingdomMapScreen map) {
            map.planUpdated(view);
        }
    }

    public static void handleMap(RoadViews.MapData data) {
        ClientRoadCache.replace(data);
    }
}
