package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: the throne screen data of a kingdom.
 *
 * @param open {@code true} to open the throne screen, {@code false} to only refresh it if it shows this kingdom
 */
public record KingdomViewPacket(KingdomView view, boolean open) {

    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
        buf.writeBoolean(open);
    }

    public static KingdomViewPacket decode(FriendlyByteBuf buf) {
        return new KingdomViewPacket(KingdomView.decode(buf), buf.readBoolean());
    }

    public static void handle(KingdomViewPacket message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleKingdomView(message));
    }
}
