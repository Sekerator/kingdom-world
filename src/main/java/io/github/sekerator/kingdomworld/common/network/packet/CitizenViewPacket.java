package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.network.data.CitizenView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: the "Citizens" tab of the throne screen.
 */
public record CitizenViewPacket(CitizenView view) {

    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
    }

    public static CitizenViewPacket decode(FriendlyByteBuf buf) {
        return new CitizenViewPacket(CitizenView.decode(buf));
    }

    public static void handle(CitizenViewPacket message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleCitizenView(message));
    }
}
