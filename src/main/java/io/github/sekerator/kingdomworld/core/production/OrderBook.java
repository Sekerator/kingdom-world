package io.github.sekerator.kingdomworld.core.production;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The orders of every kingdom. Ids count up per kingdom and are never reused. Removing an order removes its
 * sub-orders too.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class OrderBook {
    /** Sub-orders one kingdom may have at most, besides its own orders. */
    public static final int MAX_SUB_ORDERS = 128;

    private static final class Book {
        long nextId = 1;
        final Map<Long, Order> orders = new LinkedHashMap<>();
    }

    private final Map<UUID, Book> books = new LinkedHashMap<>();
    /** What each kingdom set for buying of its citizens; absent while it set nothing. */
    private final Map<UUID, Buying> buying = new LinkedHashMap<>();

    /**
     * What a kingdom set for buying of its citizens what its workshops cannot make.
     *
     * @param on         whether it buys
     * @param capPercent the most it pays, in percent of the worth
     * @param perDay     the most it spends a day
     */
    public record Buying(boolean on, int capPercent, long perDay) {
    }

    public Optional<Buying> buying(UUID kingdomId) {
        return Optional.ofNullable(buying.get(kingdomId));
    }

    public void setBuying(UUID kingdomId, Buying value) {
        Buying before = value == null ? buying.remove(kingdomId) : buying.put(kingdomId, value);
        if (!Objects.equals(before, value)) {
            changed();
        }
    }

    /**
     * Restores what a kingdom set for buying of its citizens. Does not notify.
     */
    public void restoreBuying(UUID kingdomId, Buying value) {
        if (value == null) {
            buying.remove(kingdomId);
        } else {
            buying.put(kingdomId, value);
        }
    }
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    void changed() {
        changeListener.run();
    }

    /**
     * Orders of a kingdom in the order they were given, sub-orders included.
     */
    public List<Order> ordersOf(UUID kingdomId) {
        Book book = books.get(kingdomId);
        return book == null ? List.of() : List.copyOf(book.orders.values());
    }

    /**
     * The king's own orders of a kingdom, without sub-orders.
     */
    public List<Order> mainOrdersOf(UUID kingdomId) {
        List<Order> result = new ArrayList<>();
        for (Order order : ordersOf(kingdomId)) {
            if (!order.isSubOrder()) {
                result.add(order);
            }
        }
        return result;
    }

    public Optional<Order> get(UUID kingdomId, long id) {
        Book book = books.get(kingdomId);
        return book == null ? Optional.empty() : Optional.ofNullable(book.orders.get(id));
    }

    /**
     * Sub-orders that supply an order directly.
     */
    public List<Order> children(UUID kingdomId, long id) {
        List<Order> result = new ArrayList<>();
        for (Order order : ordersOf(kingdomId)) {
            if (order.parent() == id) {
                result.add(order);
            }
        }
        return result;
    }

    public int count(UUID kingdomId) {
        return mainOrdersOf(kingdomId).size();
    }

    /**
     * The id the next order of the kingdom gets.
     */
    public long nextId(UUID kingdomId) {
        Book book = books.get(kingdomId);
        return book == null ? 1 : book.nextId;
    }

    // ---------------------------------------------------------------- changes

    /**
     * Gives an order of the king.
     */
    public Order add(UUID kingdomId, String item, Order.Kind kind, int amount, Order.Priority priority, long gameTime) {
        Book book = books.computeIfAbsent(kingdomId, k -> new Book());
        Order order = new Order(book.nextId++, item, kind, amount, 0, gameTime);
        order.setPriority(priority);
        book.orders.put(order.id(), order);
        changed();
        return order;
    }

    /**
     * Adds a sub-order that keeps in stock what a parent order needs of an ingredient; empty if the kingdom has
     * too many sub-orders already.
     */
    Optional<Order> addSub(UUID kingdomId, Order parent, String item, int amount, long gameTime) {
        Book book = books.computeIfAbsent(kingdomId, k -> new Book());
        long subs = book.orders.values().stream().filter(Order::isSubOrder).count();
        if (subs >= MAX_SUB_ORDERS) {
            return Optional.empty();
        }
        Order order = new Order(book.nextId++, item, Order.Kind.KEEP, amount, parent.id(), gameTime);
        order.setPriority(parent.priority());
        book.orders.put(order.id(), order);
        changed();
        return Optional.of(order);
    }

    /**
     * Removes an order with all its sub-orders.
     *
     * @return the orders removed, the order itself first; empty if it was not there
     */
    public List<Order> remove(UUID kingdomId, long id) {
        Book book = books.get(kingdomId);
        List<Order> removed = new ArrayList<>();
        if (book == null || !book.orders.containsKey(id)) {
            return removed;
        }
        collect(book, id, removed);
        for (Order order : removed) {
            book.orders.remove(order.id());
        }
        changed();
        return removed;
    }

    private static void collect(Book book, long id, List<Order> into) {
        Order order = book.orders.get(id);
        if (order == null || into.contains(order)) {
            return;
        }
        into.add(order);
        for (Order child : List.copyOf(book.orders.values())) {
            if (child.parent() == id) {
                collect(book, child.id(), into);
            }
        }
    }

    public void setPriority(Order order, Order.Priority priority) {
        if (order.priority() != priority) {
            order.setPriority(priority);
            changed();
        }
    }

    public void setPaused(Order order, boolean paused) {
        if (order.paused() != paused) {
            order.setPaused(paused);
            changed();
        }
    }

    public void setAmount(Order order, int amount) {
        if (order.amount() != Order.clampAmount(amount)) {
            order.setAmount(amount);
            changed();
        }
    }

    public void setKind(Order order, Order.Kind kind) {
        if (order.kind() != kind) {
            order.setKind(kind);
            changed();
        }
    }

    /**
     * Counts items made for an order.
     */
    public void addMade(Order order, int count) {
        if (count > 0) {
            order.addMade(count);
            changed();
        }
    }

    public void removeKingdom(UUID kingdomId) {
        boolean gone = books.remove(kingdomId) != null;
        gone |= buying.remove(kingdomId) != null;
        if (gone) {
            changed();
        }
    }

    /**
     * Restores the orders of a kingdom from saved data. Does not notify.
     *
     * @throws IllegalArgumentException if two orders share an id, an id is not below {@code nextId}, or a sub-order
     *                                  names a parent that is not there
     */
    public void restore(UUID kingdomId, List<Order> orders, long nextId) {
        Book book = new Book();
        long highest = 0;
        for (Order order : orders) {
            if (book.orders.put(order.id(), order) != null) {
                throw new IllegalArgumentException("Two orders with id " + order.id());
            }
            highest = Math.max(highest, order.id());
        }
        for (Order order : orders) {
            if (order.isSubOrder() && !book.orders.containsKey(order.parent())) {
                throw new IllegalArgumentException("Order " + order.id() + " supplies order " + order.parent() + ", which is missing");
            }
        }
        book.nextId = Math.max(highest + 1, Math.max(1, nextId));
        if (!book.orders.isEmpty() || nextId > 1) {
            books.put(kingdomId, book);
        }
    }
}
