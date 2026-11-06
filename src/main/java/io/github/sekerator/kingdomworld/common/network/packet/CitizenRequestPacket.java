package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.citizen.CitizenActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client to server: show me the citizens of this kingdom. Answered only for members of that kingdom.
 */
public record CitizenRequestPacket(UUID kingdomId) {
    public static final RequestPolicy POLICY = new RequestPolicy("citizen_view", 0, 10, 10_000L);

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(kingdomId);
    }

    public static CitizenRequestPacket decode(FriendlyByteBuf buf) {
        return new CitizenRequestPacket(buf.readUUID());
    }

    public static void handle(CitizenRequestPacket message, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (!ServerRequestGuard.admit(sender, POLICY)) {
            return;
        }
        CitizenActions.sendView(sender, message.kingdomId());
    }
}
