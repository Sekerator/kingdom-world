package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.kingdom.KingdomViews;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Client to server: the political map of a rectangle of chunks in the sender's dimension.
 */
public record MapAreaRequestPacket(int minChunkX, int minChunkZ, int width, int height) {
    /** Largest side of a requested rectangle, in chunks. */
    public static final int MAX_SIDE = 128;
    public static final RequestPolicy POLICY = new RequestPolicy("map_area", 0, 20, 10_000L);

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(minChunkX);
        buf.writeInt(minChunkZ);
        buf.writeVarInt(width);
        buf.writeVarInt(height);
    }

    public static MapAreaRequestPacket decode(FriendlyByteBuf buf) {
        return new MapAreaRequestPacket(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(MapAreaRequestPacket message, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        message.respond(sender).ifPresent(response -> KingdomWorldNetwork.sendToPlayer(sender, response));
    }

    /**
     * Validates the request and builds the answer on the server thread.
     *
     * @return the answer, or empty if the request was refused
     */
    public Optional<MapAreaPacket> respond(@Nullable ServerPlayer sender) {
        int limit = ClaimChunkPacket.MAX_CHUNK_COORDINATE;
        if (!ServerRequestGuard.admit(sender, POLICY)
                || !ServerRequestGuard.requireRange(sender, POLICY, "width", width, 1, MAX_SIDE)
                || !ServerRequestGuard.requireRange(sender, POLICY, "height", height, 1, MAX_SIDE)
                || !ServerRequestGuard.requireRange(sender, POLICY, "minChunkX", minChunkX, -limit, limit)
                || !ServerRequestGuard.requireRange(sender, POLICY, "minChunkZ", minChunkZ, -limit, limit)) {
            return Optional.empty();
        }
        return Optional.of(KingdomViews.mapArea(sender.serverLevel(), minChunkX, minChunkZ, width, height));
    }
}
