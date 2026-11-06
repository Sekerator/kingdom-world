package io.github.sekerator.kingdomworld.client.network;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.ClientHooks;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.client.screen.ResultListener;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.network.packet.ActionResultPacket;
import io.github.sekerator.kingdomworld.common.network.packet.BorderCrossingPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListResponsePacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomViewPacket;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaPacket;
import io.github.sekerator.kingdomworld.common.network.packet.OpenFoundingScreenPacket;
import io.github.sekerator.kingdomworld.common.network.packet.TerritoryDeltaPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.slf4j.Logger;

/**
 * Client-side handling of server packets. Runs on the client main thread.
 */
public final class ClientPacketHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientPacketHandlers() {
    }

    public static void handleKingdomList(KingdomListResponsePacket packet) {
        ClientKingdomCache.update(packet.total(), packet.kingdoms());
        LOGGER.debug("Received kingdom list page {}: {} of {} kingdom(s)", packet.page(), packet.kingdoms().size(), packet.total());
    }

    public static void handleOpenFounding(OpenFoundingScreenPacket packet) {
        ClientHooks.openFounding(packet.throne(), packet.suggestedColor());
    }

    public static void handleKingdomView(KingdomViewPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ThroneScreen throne && throne.kingdomId().equals(packet.view().id())) {
            throne.update(packet.view());
        } else if (packet.open()) {
            ClientHooks.openThrone(packet.view());
        }
    }

    public static void handleActionResult(ActionResultPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ResultListener listener) {
            listener.onResult(packet.success(), packet.message());
        } else if (minecraft.player != null) {
            minecraft.player.displayClientMessage(packet.message().copy()
                    .withStyle(packet.success() ? ChatFormatting.WHITE : ChatFormatting.RED), false);
        }
    }

    public static void handleMapArea(MapAreaPacket packet) {
        ClientTerritoryCache.applyArea(packet);
    }

    public static void handleTerritoryDelta(TerritoryDeltaPacket packet) {
        ClientTerritoryCache.applyDelta(packet);
    }

    public static void handleBorderCrossing(BorderCrossingPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !KingdomWorldConfig.CLIENT.showBorderMessages.get()) {
            return;
        }
        Component name = Component.literal(packet.kingdomName())
                .withStyle(style -> style.withColor(TextColor.fromRgb(packet.color())));
        minecraft.player.displayClientMessage(Component.translatable(packet.entered()
                ? "kingdomworld.border.entered" : "kingdomworld.border.left", name), true);
    }
}
