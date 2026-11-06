package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
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
 * Client to server: asks for one page of the public kingdom list.
 */
public record KingdomListRequestPacket(int page, int pageSize) {
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_PAGE = 10_000;
    /** Any player may ask, at most 5 times per 10 seconds. */
    public static final RequestPolicy POLICY = new RequestPolicy("kingdom_list", 0, 5, 10_000L);

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(page);
        buf.writeVarInt(pageSize);
    }

    public static KingdomListRequestPacket decode(FriendlyByteBuf buf) {
        // Values are validated on the server thread in respond(); decoding only reads fixed-size fields.
        return new KingdomListRequestPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(KingdomListRequestPacket message, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        message.respond(sender).ifPresent(response -> KingdomWorldNetwork.sendToPlayer(sender, response));
    }

    /**
     * Validates the request and builds the answer. Runs on the server thread.
     *
     * @return the response, or empty if the request was rejected
     */
    public Optional<KingdomListResponsePacket> respond(@Nullable ServerPlayer sender) {
        if (!ServerRequestGuard.admit(sender, POLICY)
                || !ServerRequestGuard.requireRange(sender, POLICY, "page", page, 0, MAX_PAGE)
                || !ServerRequestGuard.requireRange(sender, POLICY, "pageSize", pageSize, 1, MAX_PAGE_SIZE)) {
            return Optional.empty();
        }
        return Optional.of(KingdomListResponsePacket.page(KingdomWorldApi.kingdoms(sender.server), page, pageSize));
    }
}
