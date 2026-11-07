package io.github.sekerator.kingdomworld.client.network;

import io.github.sekerator.kingdomworld.client.WantedHud;
import io.github.sekerator.kingdomworld.client.screen.LawDialogScreen;
import io.github.sekerator.kingdomworld.client.screen.LawScreen;
import io.github.sekerator.kingdomworld.client.screen.RansomScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.network.data.LawViews;
import net.minecraft.client.Minecraft;

/**
 * Client-side handling of the packets of the law. Runs on the client main thread.
 */
public final class ClientLawHandlers {

    private ClientLawHandlers() {
    }

    public static void handleTab(LawViews.TabView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateLaw(view);
        }
    }

    public static void handlePlayer(LawViews.PlayerView view, boolean open) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof LawScreen screen) {
            screen.update(view);
        } else if (open) {
            minecraft.setScreen(new LawScreen(view));
        }
    }

    public static void handleDialog(LawViews.DialogView view) {
        Minecraft.getInstance().setScreen(new LawDialogScreen(view));
    }

    public static void handleRansom(LawViews.RansomView view) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RansomScreen screen && screen.id() == view.confiscation().id()) {
            screen.update(view);
        } else {
            minecraft.setScreen(new RansomScreen(view));
        }
    }

    public static void handleLine(LawViews.WantedLine line) {
        WantedHud.setLine(line);
    }
}
