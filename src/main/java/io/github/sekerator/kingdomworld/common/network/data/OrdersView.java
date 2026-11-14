package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.production.OrderBook;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the "Orders" tab of the throne screen shows to a member of the kingdom: the king's orders with their
 * sub-orders under them, how far each is, and what is missing.
 *
 * @param canManage whether the viewer may give, change and cancel orders
 * @param maxOrders  orders of the king the kingdom may have
 * @param kingOrders orders of the king the limit counts: those of the constructions for what they lack do not
 */
public record OrdersView(UUID kingdomId, boolean canManage, int maxOrders, List<Entry> orders, int kingOrders) {
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_MISSING = 8;
    /** Deepest a sub-order is shown. */
    public static final int MAX_DEPTH = 16;

    /**
     * Something missing for an order.
     *
     * @param gatherer the profession that gathers it, empty if none does
     */
    public record Missing(ItemStack sample, int count, String gatherer) {
        public Missing {
            Objects.requireNonNull(sample, "sample");
            Objects.requireNonNull(gatherer, "gatherer");
        }
    }

    /**
     * One order.
     *
     * @param depth    0 for an order of the king, 1 for its sub-orders and so on
     * @param progress items made so far for a make order, items in stock for a keep order
     * @param workshop for an order without workshop: the profession and the workstation it needs, else empty
     */
    public record Entry(long id, int depth, ItemStack sample, Order.Kind kind, int amount, long progress, Order.Priority priority,
                        boolean paused, Order.Status status, List<Missing> missing, String workshop, String workshopStation) {
        public Entry {
            Objects.requireNonNull(sample, "sample");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(workshop, "workshop");
            Objects.requireNonNull(workshopStation, "workshopStation");
            missing = List.copyOf(missing);
            if (missing.size() > MAX_MISSING) {
                throw new IllegalArgumentException("Too much missing");
            }
        }

        public boolean isSubOrder() {
            return depth > 0;
        }
    }

    public OrdersView(UUID kingdomId, boolean canManage, int maxOrders, List<Entry> orders) {
        this(kingdomId, canManage, maxOrders, orders, (int) orders.stream().filter(entry -> !entry.isSubOrder()).count());
    }

    public OrdersView {
        Objects.requireNonNull(kingdomId, "kingdomId");
        orders = List.copyOf(orders);
        if (orders.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Orders view too large");
        }
    }

    /**
     * Orders of the king, without sub-orders.
     */
    public int mainOrders() {
        int count = 0;
        for (Entry entry : orders) {
            if (!entry.isSubOrder()) {
                count++;
            }
        }
        return count;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(kingdomId);
        buf.writeBoolean(canManage);
        buf.writeVarInt(maxOrders);
        buf.writeVarInt(kingOrders);
        buf.writeVarInt(orders.size());
        for (Entry entry : orders) {
            buf.writeVarLong(entry.id());
            buf.writeVarInt(entry.depth());
            buf.writeItem(entry.sample());
            buf.writeEnum(entry.kind());
            buf.writeVarInt(entry.amount());
            buf.writeVarLong(entry.progress());
            buf.writeEnum(entry.priority());
            buf.writeBoolean(entry.paused());
            buf.writeEnum(entry.status());
            buf.writeVarInt(entry.missing().size());
            for (Missing missing : entry.missing()) {
                buf.writeItem(missing.sample());
                buf.writeVarInt(missing.count());
                buf.writeUtf(missing.gatherer(), Profession.MAX_ID_LENGTH);
            }
            buf.writeUtf(entry.workshop(), Profession.MAX_ID_LENGTH);
            buf.writeUtf(entry.workshopStation(), Profession.MAX_ID_LENGTH);
        }
    }

    public static OrdersView decode(FriendlyByteBuf buf) {
        UUID kingdomId = buf.readUUID();
        boolean canManage = buf.readBoolean();
        int maxOrders = buf.readVarInt();
        int kingOrders = buf.readVarInt();
        int size = KingdomView.readSize(buf, MAX_ENTRIES);
        List<Entry> orders = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            long id = buf.readVarLong();
            int depth = buf.readVarInt();
            if (depth < 0 || depth > MAX_DEPTH) {
                throw new DecoderException("Invalid depth of an order");
            }
            ItemStack sample = buf.readItem();
            Order.Kind kind = KingdomView.readEnum(buf, Order.Kind.class);
            int amount = buf.readVarInt();
            long progress = buf.readVarLong();
            Order.Priority priority = KingdomView.readEnum(buf, Order.Priority.class);
            boolean paused = buf.readBoolean();
            Order.Status status = KingdomView.readEnum(buf, Order.Status.class);
            int missingCount = KingdomView.readSize(buf, MAX_MISSING);
            List<Missing> missing = new ArrayList<>(missingCount);
            for (int j = 0; j < missingCount; j++) {
                missing.add(new Missing(buf.readItem(), buf.readVarInt(), buf.readUtf(Profession.MAX_ID_LENGTH)));
            }
            orders.add(new Entry(id, depth, sample, kind, amount, progress, priority, paused, status, missing,
                    buf.readUtf(Profession.MAX_ID_LENGTH), buf.readUtf(Profession.MAX_ID_LENGTH)));
        }
        return new OrdersView(kingdomId, canManage, maxOrders, orders, kingOrders);
    }

    /**
     * Largest number of entries a kingdom's orders give: its own orders and all sub-orders.
     */
    public static int limit(int maxOrders) {
        return Math.min(MAX_ENTRIES, maxOrders + OrderBook.MAX_SUB_ORDERS);
    }
}
