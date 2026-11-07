package io.github.sekerator.kingdomworld.client.network;

import io.github.sekerator.kingdomworld.client.screen.GateScreen;
import io.github.sekerator.kingdomworld.client.screen.MagicFocusScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.network.data.MagicViews;
import net.minecraft.client.Minecraft;

/**
 * Client-side handling of the packets of magic. Runs on the client main thread.
 */
public final class ClientMagicHandlers {

    private ClientMagicHandlers() {
    }

    public static void handleTab(MagicViews.TabView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateMagic(view);
        }
    }

    public static void handleFocus(MagicViews.FocusView view, boolean open) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MagicFocusScreen focus && focus.pos().equals(view.pos())) {
            focus.update(view);
        } else if (open) {
            minecraft.setScreen(new MagicFocusScreen(view));
        }
    }

    public static void handleGate(MagicViews.GateView view) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof GateScreen gate && gate.pos().equals(view.pos())) {
            gate.update(view);
        } else {
            minecraft.setScreen(new GateScreen(view));
        }
    }
}
