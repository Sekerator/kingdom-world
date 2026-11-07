package io.github.sekerator.kingdomworld.client.network;

import io.github.sekerator.kingdomworld.client.screen.GuildDeskScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.network.data.GuildViews;
import net.minecraft.client.Minecraft;

/**
 * Client-side handling of the guild's packets. Runs on the client main thread.
 */
public final class ClientGuildHandlers {

    private ClientGuildHandlers() {
    }

    public static void handleDesk(GuildViews.DeskView view, boolean open) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof GuildDeskScreen desk && desk.pos().equals(view.pos())) {
            desk.update(view);
        } else if (open) {
            minecraft.setScreen(new GuildDeskScreen(view));
        }
    }

    public static void handleTab(GuildViews.GuildTabView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateGuild(view);
        }
    }
}
