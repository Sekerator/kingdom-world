package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.production.OrderBook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * NBT form of the orders of one kingdom (format 5), stored inside the kingdom's compound, with what the kingdom set for buying of
 * its citizens, optional, absent in older worlds.
 *
 * <pre>
 * orders: { next, list: [ { id, item, kind, amount, made, priority, paused, parent, created } ],
 *           buyFromCitizens?, buyCapPercent?, buyPerDay? }
 * </pre>
 *
 * <p>Reading is strict like {@link KingdomNbt}: what cannot be understood throws {@link IllegalArgumentException}.
 * Statuses, recipes and what is missing are not saved; the planner works them out again.
 */
public final class OrderNbt {
    static final String ORDERS = "orders";
    private static final String NEXT = "next";
    private static final String LIST = "list";
    private static final String ID = "id";
    private static final String ITEM = "item";
    private static final String KIND = "kind";
    private static final String AMOUNT = "amount";
    private static final String MADE = "made";
    private static final String PRIORITY = "priority";
    private static final String PAUSED = "paused";
    private static final String PARENT = "parent";
    private static final String CREATED = "created";
    /** Longest item id an order keeps. */
    private static final int MAX_ITEM_LENGTH = 256;

    private static final String BUY = "buyFromCitizens";
    private static final String BUY_CAP = "buyCapPercent";
    private static final String BUY_PER_DAY = "buyPerDay";

    /**
     * The orders of a kingdom as read, not yet restored.
     *
     * @param buying what it set for buying of its citizens, {@code null} for nothing
     */
    public record Orders(List<Order> orders, long next, OrderBook.Buying buying) {
        public static final Orders EMPTY = new Orders(List.of(), 1, null);

        public Orders(List<Order> orders, long next) {
            this(orders, next, null);
        }
    }

    private OrderNbt() {
    }

    public static CompoundTag write(OrderBook book, UUID kingdomId) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(NEXT, book.nextId(kingdomId));
        ListTag list = new ListTag();
        for (Order order : book.ordersOf(kingdomId)) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(ID, order.id());
            entry.putString(ITEM, order.item());
            entry.putString(KIND, order.kind().id());
            entry.putInt(AMOUNT, order.amount());
            entry.putInt(MADE, order.made());
            entry.putString(PRIORITY, order.priority().id());
            entry.putBoolean(PAUSED, order.paused());
            entry.putLong(PARENT, order.parent());
            entry.putLong(CREATED, order.created());
            list.add(entry);
        }
        tag.put(LIST, list);
        book.buying(kingdomId).ifPresent(buying -> {
            tag.putBoolean(BUY, buying.on());
            tag.putInt(BUY_CAP, buying.capPercent());
            tag.putLong(BUY_PER_DAY, buying.perDay());
        });
        return tag;
    }

    /**
     * Reads the orders of a kingdom; an absent tag means none.
     *
     * @throws IllegalArgumentException if the tag cannot be understood
     */
    public static Orders read(CompoundTag kingdomTag, String name) {
        if (!kingdomTag.contains(ORDERS)) {
            return Orders.EMPTY;
        }
        if (!kingdomTag.contains(ORDERS, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Orders of " + name + " are not a compound");
        }
        CompoundTag tag = kingdomTag.getCompound(ORDERS);
        if (tag.contains(LIST) && !tag.contains(LIST, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Orders of " + name + " are not a list");
        }
        ListTag list = tag.getList(LIST, Tag.TAG_COMPOUND);
        if (tag.contains(LIST, Tag.TAG_LIST) && !((ListTag) tag.get(LIST)).isEmpty() && list.isEmpty()) {
            throw new IllegalArgumentException("Orders of " + name + " do not hold compounds");
        }
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            orders.add(readOrder(list.getCompound(i), name));
        }
        OrderBook.Buying buying = tag.contains(BUY) ? new OrderBook.Buying(tag.getBoolean(BUY), tag.getInt(BUY_CAP),
                Math.max(0, tag.getLong(BUY_PER_DAY))) : null;
        return new Orders(orders, Math.max(1, tag.getLong(NEXT)), buying);
    }

    private static Order readOrder(CompoundTag tag, String name) {
        long id = tag.getLong(ID);
        String where = "Order " + id + " of " + name;
        String item = tag.getString(ITEM);
        if (id <= 0 || item.isEmpty() || item.length() > MAX_ITEM_LENGTH) {
            throw new IllegalArgumentException(where + " has an invalid id or item");
        }
        Order.Kind kind = Order.Kind.byId(tag.getString(KIND))
                .orElseThrow(() -> new IllegalArgumentException(where + " has an unknown kind '" + tag.getString(KIND) + "'"));
        Order.Priority priority = Order.Priority.byId(tag.getString(PRIORITY))
                .orElseThrow(() -> new IllegalArgumentException(where + " has an unknown priority '" + tag.getString(PRIORITY) + "'"));
        int amount = tag.getInt(AMOUNT);
        int made = tag.getInt(MADE);
        long parent = tag.getLong(PARENT);
        if (amount < 1 || amount > Order.MAX_AMOUNT || made < 0 || parent < 0 || parent == id) {
            throw new IllegalArgumentException(where + " has an invalid amount or parent");
        }
        Order order = new Order(id, item, kind, amount, parent, tag.getLong(CREATED));
        order.restore(made, priority, tag.getBoolean(PAUSED));
        return order;
    }
}
