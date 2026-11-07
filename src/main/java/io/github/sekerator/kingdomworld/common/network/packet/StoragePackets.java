package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.StorageView;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The "Storehouses" tab of the throne screen: the client asks, the server answers members of the kingdom only.
 */
public final class StoragePackets {

    private StoragePackets() {
    }

    /**
     * Client to server: show me what the storehouses of this kingdom hold.
     */
    public record Request(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("storage_view", 0, 10, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readUUID());
        }

        public static void handle(Request message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)) {
                return;
            }
            view(sender.server, message.kingdomId(), sender.getUUID())
                    .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(sender, new Response(view)));
        }
    }

    /**
     * Server to client: the tab's contents.
     */
    public record Response(StorageView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static Response decode(FriendlyByteBuf buf) {
            return new Response(StorageView.decode(buf));
        }

        public static void handle(Response message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleStorageView(message.view()));
        }
    }

    /**
     * What the storehouses of a kingdom hold, for a member of the kingdom; empty for anybody else.
     */
    public static Optional<StorageView> view(MinecraftServer server, UUID kingdomId, UUID viewer) {
        if (!KingdomWorldApi.state(server).membership().isMember(viewer, kingdomId)) {
            return Optional.empty();
        }
        List<KingdomStorage.Total> totals = KingdomStorage.totals(server, kingdomId);
        List<StorageView.Entry> items = new ArrayList<>();
        long total = 0;
        for (KingdomStorage.Total entry : totals) {
            total += entry.count();
            if (items.size() < StorageView.MAX_ITEMS) {
                items.add(new StorageView.Entry(entry.sample(), entry.count()));
            }
        }
        List<StorageView.Storehouse> storehouses = new ArrayList<>();
        for (StorehouseInventory inventory : KingdomStorage.storehouses(server, kingdomId, null)) {
            if (storehouses.size() >= StorageView.MAX_STOREHOUSES) {
                break;
            }
            storehouses.add(new StorageView.Storehouse(new BlockPos(inventory.pos().x(), inventory.pos().y(), inventory.pos().z()),
                    inventory.getSlots() - KingdomStorage.freeSlots(inventory), inventory.getSlots()));
        }
        return Optional.of(new StorageView(kingdomId, items, storehouses, totals.size(), total));
    }
}
