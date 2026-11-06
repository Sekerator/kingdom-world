package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: claim or give up a chunk of the sender's current dimension for the sender's kingdom.
 */
public record ClaimChunkPacket(int chunkX, int chunkZ, boolean claim) {
    /** Chunk coordinate of the world border (30 million blocks). */
    public static final int MAX_CHUNK_COORDINATE = 1_875_000;
    public static final RequestPolicy POLICY = new RequestPolicy("claim_chunk", 0, 20, 10_000L);

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeBoolean(claim);
    }

    public static ClaimChunkPacket decode(FriendlyByteBuf buf) {
        return new ClaimChunkPacket(buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    public static void handle(ClaimChunkPacket message, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (!ServerRequestGuard.admit(sender, POLICY)
                || !ServerRequestGuard.requireRange(sender, POLICY, "chunkX", message.chunkX, -MAX_CHUNK_COORDINATE, MAX_CHUNK_COORDINATE)
                || !ServerRequestGuard.requireRange(sender, POLICY, "chunkZ", message.chunkZ, -MAX_CHUNK_COORDINATE, MAX_CHUNK_COORDINATE)) {
            return;
        }
        KingdomActions.claimFromMap(sender, message.chunkX, message.chunkZ, message.claim);
    }
}
