package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.client.screen.FoundKingdomScreen;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Entry points from common code into client screens. Only reached through {@code DistExecutor} on the client.
 */
public final class ClientHooks {

    private ClientHooks() {
    }

    public static void openKingdomMap() {
        Minecraft.getInstance().setScreen(new KingdomMapScreen(null));
    }

    public static void openTerritoryEditor(KingdomView view) {
        Minecraft.getInstance().setScreen(new KingdomMapScreen(view));
    }

    public static void openFounding(BlockPos throne, int suggestedColor) {
        Minecraft.getInstance().setScreen(new FoundKingdomScreen(throne, suggestedColor));
    }

    public static void openThrone(KingdomView view) {
        Minecraft.getInstance().setScreen(new ThroneScreen(view));
    }
}
