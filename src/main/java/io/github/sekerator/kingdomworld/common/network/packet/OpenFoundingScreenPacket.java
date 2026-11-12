package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: open the founding screen for the empty throne at {@code throne}.
 *
 * @param suggestedStyle the index of the architectural style the land suggests
 */
public record OpenFoundingScreenPacket(BlockPos throne, int suggestedColor, int suggestedStyle) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(throne);
        buf.writeInt(suggestedColor);
        buf.writeVarInt(suggestedStyle);
    }

    public static OpenFoundingScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenFoundingScreenPacket(buf.readBlockPos(), buf.readInt() & Kingdom.MAX_COLOR, buf.readVarInt());
    }

    public static void handle(OpenFoundingScreenPacket message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleOpenFounding(message));
    }
}
