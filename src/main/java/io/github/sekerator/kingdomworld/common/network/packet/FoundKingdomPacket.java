package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: found a kingdom on the empty throne at {@code throne} with the given name, color and architectural style.
 * The server checks the throne, the distance to it, the crown in hand and every founding rule.
 *
 * @param style the index of the style among the architectural styles
 */
public record FoundKingdomPacket(BlockPos throne, String name, int color, int style) {
    /** Raw names may carry extra whitespace that normalization removes. */
    public static final int MAX_RAW_NAME = 64;
    public static final RequestPolicy POLICY = new RequestPolicy("found_kingdom", 0, 3, 10_000L);

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(throne);
        buf.writeUtf(name, MAX_RAW_NAME);
        buf.writeInt(color);
        buf.writeVarInt(style);
    }

    public static FoundKingdomPacket decode(FriendlyByteBuf buf) {
        return new FoundKingdomPacket(buf.readBlockPos(), buf.readUtf(MAX_RAW_NAME), buf.readInt(), buf.readVarInt());
    }

    public static void handle(FoundKingdomPacket message, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (!ServerRequestGuard.admit(sender, POLICY)
                || !ServerRequestGuard.requireLength(sender, POLICY, "name", message.name, MAX_RAW_NAME)
                || !ServerRequestGuard.requireRange(sender, POLICY, "color", message.color, 0, Kingdom.MAX_COLOR)) {
            return;
        }
        KingdomActions.foundFromThrone(sender, message.throne, message.name, message.color);
    }
}
