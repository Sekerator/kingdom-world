package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: the player entered or left the land of a kingdom. The client decides whether to show it.
 */
public record BorderCrossingPacket(boolean entered, String kingdomName, int color) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(entered);
        buf.writeUtf(kingdomName, KingdomNames.MAX_LENGTH);
        buf.writeInt(color);
    }

    public static BorderCrossingPacket decode(FriendlyByteBuf buf) {
        return new BorderCrossingPacket(buf.readBoolean(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt() & Kingdom.MAX_COLOR);
    }

    public static void handle(BorderCrossingPacket message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleBorderCrossing(message));
    }
}
