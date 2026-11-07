package io.github.sekerator.kingdomworld.client.network;

import io.github.sekerator.kingdomworld.client.PartyHud;
import io.github.sekerator.kingdomworld.client.screen.CompanionScreen;
import io.github.sekerator.kingdomworld.client.screen.GuildDeskScreen;
import io.github.sekerator.kingdomworld.client.screen.PartyScreen;
import io.github.sekerator.kingdomworld.common.network.data.CompanionViews;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Client-side handling of the packets of companions and parties. Runs on the client main thread.
 */
public final class ClientCompanionHandlers {

    private ClientCompanionHandlers() {
    }

    public static void handleMarket(CompanionViews.MarketView view) {
        if (Minecraft.getInstance().screen instanceof GuildDeskScreen desk && desk.pos().equals(view.pos())) {
            desk.updateMarket(view);
        }
    }

    public static void handleCompanion(CompanionViews.CompanionView view) {
        if (Minecraft.getInstance().screen instanceof CompanionScreen screen) {
            screen.update(view);
        }
    }

    public static void handleParty(CompanionViews.PartyView view, boolean open) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PartyScreen screen) {
            screen.update(view);
        } else if (open) {
            minecraft.setScreen(new PartyScreen(view));
        }
    }

    public static void handleHud(List<CompanionViews.HudLine> lines) {
        PartyHud.setLines(lines);
    }
}
