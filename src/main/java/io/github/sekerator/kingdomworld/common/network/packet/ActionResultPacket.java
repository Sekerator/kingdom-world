package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: the outcome of a request made from a screen. An open screen shows it in its status line,
 * otherwise it goes to chat.
 */
public record ActionResultPacket(boolean success, Component message) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeComponent(message);
    }

    public static ActionResultPacket decode(FriendlyByteBuf buf) {
        return new ActionResultPacket(buf.readBoolean(), buf.readComponent());
    }

    public static void handle(ActionResultPacket message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleActionResult(message));
    }
}
