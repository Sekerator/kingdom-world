package io.github.sekerator.kingdomworld.client.network;

import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews;
import net.minecraft.client.Minecraft;

/**
 * Client-side handling of the packets of diplomacy. Runs on the client main thread.
 */
public final class ClientDiplomacyHandlers {
    private static volatile DiplomacyViews.MapView marks = DiplomacyViews.MapView.EMPTY;

    private ClientDiplomacyHandlers() {
    }

    public static void handleTab(DiplomacyViews.TabView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateDiplomacy(view);
        }
    }

    public static void handleMap(DiplomacyViews.MapView view) {
        marks = view;
    }

    /**
     * How the player's kingdom stands with the others, for the atlas.
     */
    public static DiplomacyViews.MapView marks() {
        return marks;
    }

    /**
     * Replaces the marks, for the layout check.
     */
    public static void setMarksForCheck(DiplomacyViews.MapView view) {
        marks = view;
    }

    public static void reset() {
        marks = DiplomacyViews.MapView.EMPTY;
    }
}
