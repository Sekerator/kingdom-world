package io.github.sekerator.kingdomworld.client.network;

import io.github.sekerator.kingdomworld.client.screen.BarracksScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.network.data.ArmyViews;
import net.minecraft.client.Minecraft;

/**
 * Client-side handling of the army's packets. Runs on the client main thread.
 */
public final class ClientArmyHandlers {

    private ClientArmyHandlers() {
    }

    public static void handleTab(ArmyViews.ArmyTabView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateArmy(view);
        }
    }

    public static void handleBarracks(ArmyViews.BarracksView view, boolean open) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof BarracksScreen barracks && barracks.pos().equals(view.pos())) {
            barracks.update(view);
        } else if (open) {
            minecraft.setScreen(new BarracksScreen(view));
        }
    }
}
