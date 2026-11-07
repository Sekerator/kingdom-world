package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.CatalogView;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.OrdersView;
import io.github.sekerator.kingdomworld.common.production.OrderActions;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The "Orders" tab of the throne screen: the client asks for the orders and for pages of the items citizens can
 * make, and sends the king's decisions; the server answers members only and checks every change (the right to
 * manage production, the item, the amount, the limits).
 */
public final class OrderPackets {
    /** Longest item id an order names. */
    public static final int MAX_ITEM_LENGTH = 256;
    /** Items the client matched by their translated names that a catalog request may name. */
    public static final int MAX_HINTS = 1_024;

    private OrderPackets() {
    }

    /**
     * Client to server: show me the orders of this kingdom.
     */
    public record Request(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("orders_view", 0, 20, 10_000L);

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
            OrderActions.sendView(sender, message.kingdomId());
        }
    }

    /**
     * Server to client: the orders.
     */
    public record Response(OrdersView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static Response decode(FriendlyByteBuf buf) {
            return new Response(OrdersView.decode(buf));
        }

        public static void handle(Response message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleOrdersView(message.view()));
        }
    }

    /**
     * Client to server: a page of the items that can be ordered.
     *
     * @param search text the item's id or name must contain, empty for all
     * @param hints  items (by their numeric ids, which client and server share) whose names in the player's
     *               language contain the search; the server cannot translate names itself
     */
    public record CatalogRequest(UUID kingdomId, String search, int page, int pageSize, List<Integer> hints) {
        public static final RequestPolicy POLICY = new RequestPolicy("order_catalog", 0, 20, 10_000L);

        public CatalogRequest {
            search = search == null ? "" : search;
            hints = List.copyOf(hints);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeUtf(search, CatalogView.MAX_SEARCH);
            buf.writeVarInt(page);
            buf.writeVarInt(pageSize);
            buf.writeVarInt(hints.size());
            for (int hint : hints) {
                buf.writeVarInt(hint);
            }
        }

        public static CatalogRequest decode(FriendlyByteBuf buf) {
            UUID kingdomId = buf.readUUID();
            String search = buf.readUtf(CatalogView.MAX_SEARCH);
            int page = buf.readVarInt();
            int pageSize = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_HINTS) {
                throw new io.netty.handler.codec.DecoderException("Too many hints: " + count);
            }
            List<Integer> hints = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                hints.add(buf.readVarInt());
            }
            return new CatalogRequest(kingdomId, search, page, pageSize, hints);
        }

        public static void handle(CatalogRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "page", message.page(), 0, 100_000)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "pageSize", message.pageSize(), 1, CatalogView.MAX_PAGE_SIZE)) {
                return;
            }
            OrderActions.sendCatalog(sender, message);
        }
    }

    /**
     * Server to client: a page of the items that can be ordered.
     */
    public record Catalog(CatalogView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static Catalog decode(FriendlyByteBuf buf) {
            return new Catalog(CatalogView.decode(buf));
        }

        public static void handle(Catalog message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleCatalog(message.view()));
        }
    }

    /**
     * Client to server: a decision about the orders.
     *
     * @param order  the order to change, 0 when a new one is given
     * @param item   the item of a new order, otherwise empty
     * @param amount the amount of a new order or the new amount
     */
    public record Change(UUID kingdomId, Action action, long order, String item, Order.Kind kind, int amount, Order.Priority priority) {
        public static final RequestPolicy POLICY = new RequestPolicy("order_change", 0, 20, 10_000L);

        public enum Action {
            CREATE,
            CANCEL,
            PAUSE,
            RESUME,
            PRIORITY,
            AMOUNT,
            KIND,
            /** Amount, kind and priority at once, from the form that changes an order. */
            UPDATE
        }

        public Change {
            item = item == null ? "" : item;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeVarLong(order);
            buf.writeUtf(item, MAX_ITEM_LENGTH);
            buf.writeEnum(kind);
            buf.writeVarInt(amount);
            buf.writeEnum(priority);
        }

        public static Change decode(FriendlyByteBuf buf) {
            return new Change(buf.readUUID(), KingdomView.readEnum(buf, Action.class), buf.readVarLong(), buf.readUtf(MAX_ITEM_LENGTH),
                    KingdomView.readEnum(buf, Order.Kind.class), buf.readVarInt(), KingdomView.readEnum(buf, Order.Priority.class));
        }

        public static void handle(Change message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireLength(sender, POLICY, "item", message.item(), MAX_ITEM_LENGTH)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "amount", message.amount(), 0, Order.MAX_AMOUNT)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "order", message.order(), 0, Long.MAX_VALUE)) {
                return;
            }
            OrderActions.handle(sender, message);
        }
    }
}
