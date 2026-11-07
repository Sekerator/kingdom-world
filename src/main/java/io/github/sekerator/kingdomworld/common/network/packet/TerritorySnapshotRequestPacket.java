package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.kingdom.TerritorySnapshots;
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
 * Client to server: the land of the sender's dimension for a map integration (JourneyMap, Xaero's maps). Sent when
 * the player enters a world or a dimension, and when a partial answer no longer covers where the player is.
 */
public record TerritorySnapshotRequestPacket() {
    public static final RequestPolicy POLICY = new RequestPolicy("territory_snapshot", 0, 4, 10_000L);

    public void encode(FriendlyByteBuf buf) {
    }

    public static TerritorySnapshotRequestPacket decode(FriendlyByteBuf buf) {
        return new TerritorySnapshotRequestPacket();
    }

    public static void handle(TerritorySnapshotRequestPacket message, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        message.respond(sender).ifPresent(response -> KingdomWorldNetwork.sendToPlayer(sender, response));
    }

    /**
     * Validates the request and builds the answer on the server thread.
     *
     * @return the answer, or empty if the request was refused
     */
    public Optional<TerritorySnapshotPacket> respond(@Nullable ServerPlayer sender) {
        if (!ServerRequestGuard.admit(sender, POLICY)) {
            return Optional.empty();
        }
        return Optional.of(TerritorySnapshots.build(sender));
    }
}
