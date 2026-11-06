package io.github.sekerator.kingdomworld.client.network;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListResponsePacket;
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
}
