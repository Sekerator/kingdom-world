package io.github.sekerator.kingdomworld.client.network;

import io.github.sekerator.kingdomworld.client.screen.TavernScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.network.data.LifeViews;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Client-side handling of the packets of the life of cities. Runs on the client main thread.
 */
public final class ClientLifeHandlers {
    private static volatile List<LifeViews.MapEvent> events = List.of();

    private ClientLifeHandlers() {
    }

    public static void handleTavern(LifeViews.TavernView view, boolean open) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TavernScreen screen) {
            screen.update(view);
        } else if (open) {
            minecraft.setScreen(new TavernScreen(view));
        }
    }

    public static void handleTab(LifeViews.TabView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateLife(view);
        }
    }

    public static void handleMap(List<LifeViews.MapEvent> list) {
        events = List.copyOf(list);
    }

    /**
     * Festivals and fairs of today, for the atlas.
     */
    public static List<LifeViews.MapEvent> events() {
        return events;
    }

    public static void reset() {
        events = List.of();
    }
}
