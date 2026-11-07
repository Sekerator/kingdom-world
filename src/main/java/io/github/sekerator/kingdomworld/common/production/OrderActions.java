package io.github.sekerator.kingdomworld.common.production;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CatalogView;
import io.github.sekerator.kingdomworld.common.network.data.OrdersView;
import io.github.sekerator.kingdomworld.common.network.packet.OrderPackets;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.production.OrderBook;
import io.github.sekerator.kingdomworld.core.production.RecipeBook;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What players do with the king's orders: look at them, find items to order, give, change and cancel orders.
 * Members of the kingdom see the orders; changing them needs the right to manage production. The server checks
 * every request and answers with the result and a fresh list.
 */
public final class OrderActions {
    private static final String KEY = "kingdomworld.order.";

    private OrderActions() {
    }

    // ---------------------------------------------------------------- the list

    public static void sendView(ServerPlayer player, UUID kingdomId) {
        view(player.server, kingdomId, player.getUUID())
                .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new OrderPackets.Response(view)));
    }

    /**
     * The orders of a kingdom for one of its members, planned afresh; empty for anybody else.
     */
    public static Optional<OrdersView> view(MinecraftServer server, UUID kingdomId, UUID viewer) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (!state.membership().isMember(viewer, kingdomId)) {
            return Optional.empty();
        }
        Production.plan(server, kingdomId);
        Map<String, Long> stock = KingdomStorage.countsById(server, kingdomId);
        OrderBook orders = state.orders();
        List<OrdersView.Entry> entries = new ArrayList<>();
        int limit = OrdersView.limit(ProductionSettings.rules().maxOrders());
        for (Order order : orders.mainOrdersOf(kingdomId)) {
            add(orders, kingdomId, order, 0, stock, entries, limit, new HashSet<>());
        }
        return Optional.of(new OrdersView(kingdomId, state.membership().hasPermission(viewer, kingdomId, Permission.MANAGE_PRODUCTION),
                ProductionSettings.rules().maxOrders(), entries));
    }

    private static void add(OrderBook orders, UUID kingdomId, Order order, int depth, Map<String, Long> stock, List<OrdersView.Entry> into,
                            int limit, Set<Long> seen) {
        if (into.size() >= limit || depth > OrdersView.MAX_DEPTH || !seen.add(order.id())) {
            return;
        }
        long progress = order.kind() == Order.Kind.MAKE ? order.made()
                : Math.max(0, stock.getOrDefault(order.item(), 0L) - Production.reservations().reserved(kingdomId, order.item(), null));
        List<OrdersView.Missing> missing = new ArrayList<>();
        for (Order.Missing entry : order.missing()) {
            if (missing.size() < OrdersView.MAX_MISSING) {
                missing.add(new OrdersView.Missing(sample(entry.item()), entry.count(), entry.gatherer()));
            }
        }
        into.add(new OrdersView.Entry(order.id(), depth, sample(order.item()), order.kind(), order.amount(), progress, order.priority(),
                order.paused(), order.status(), missing, order.workshop(), order.workshopStation()));
        for (Order child : orders.children(kingdomId, order.id())) {
            add(orders, kingdomId, child, depth + 1, stock, into, limit, seen);
        }
    }

    /**
     * One item of the kind, or a barrier named by the id if the item is gone (its mod was removed).
     */
    static ItemStack sample(String itemId) {
        Item item = Recipes.item(itemId);
        if (item != null) {
            return new ItemStack(item);
        }
        ItemStack unknown = new ItemStack(Items.BARRIER);
        unknown.setHoverName(Component.literal(itemId));
        return unknown;
    }

    // ---------------------------------------------------------------- the catalog

    /**
     * A page of the items citizens can make that match a search: by item id, by the name the server knows, or
     * among the items the client found by their names in the player's language.
     */
    public static void sendCatalog(ServerPlayer player, OrderPackets.CatalogRequest request) {
        MinecraftServer server = player.server;
        if (!KingdomWorldApi.state(server).membership().isMember(player.getUUID(), request.kingdomId())) {
            return;
        }
        KingdomWorldNetwork.sendToPlayer(player, new OrderPackets.Catalog(catalog(server, request.kingdomId(), request.search(),
                request.page(), request.pageSize(), request.hints())));
    }

    public static CatalogView catalog(MinecraftServer server, UUID kingdomId, String search, int page, int pageSize, List<Integer> hints) {
        String wanted = search.trim().toLowerCase(Locale.ROOT);
        Set<Integer> named = new HashSet<>(hints);
        RecipeBook book = Recipes.book(server);
        List<RecipeBook.Entry> matches = new ArrayList<>();
        for (RecipeBook.Entry entry : book.catalog()) {
            Item item = Recipes.item(entry.item());
            if (item == null) {
                continue;
            }
            if (wanted.isEmpty() || entry.item().contains(wanted) || named.contains(Item.getId(item))
                    || new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(wanted)) {
                matches.add(entry);
            }
        }
        int size = Math.max(1, Math.min(CatalogView.MAX_PAGE_SIZE, pageSize));
        int pages = Math.max(1, (matches.size() + size - 1) / size);
        int shown = Math.max(0, Math.min(page, pages - 1));
        List<CatalogView.Entry> entries = new ArrayList<>();
        for (int i = shown * size; i < matches.size() && entries.size() < size; i++) {
            RecipeBook.Entry entry = matches.get(i);
            entries.add(new CatalogView.Entry(new ItemStack(Recipes.item(entry.item())), entry.profession()));
        }
        String echoed = search.length() > CatalogView.MAX_SEARCH ? search.substring(0, CatalogView.MAX_SEARCH) : search;
        return new CatalogView(kingdomId, echoed, shown, size, matches.size(), entries);
    }

    // ---------------------------------------------------------------- changes

    /**
     * Handles a decision from the orders tab and sends the fresh list.
     */
    public static void handle(ServerPlayer player, OrderPackets.Change change) {
        MinecraftServer server = player.server;
        UUID kingdom = change.kingdomId();
        if (!KingdomWorldApi.state(server).membership().isMember(player.getUUID(), kingdom)) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER));
            return;
        }
        KingdomActions.Feedback feedback = switch (change.action()) {
            case CREATE -> create(player, change);
            case CANCEL -> simple(player, change, "cancel an order", state -> state.cancelOrder(player.getUUID(), kingdom, change.order()),
                    "cancelled");
            case PAUSE -> simple(player, change, "pause an order", state -> state.pauseOrder(player.getUUID(), kingdom, change.order(), true),
                    "paused");
            case RESUME -> simple(player, change, "resume an order",
                    state -> state.pauseOrder(player.getUUID(), kingdom, change.order(), false), "resumed");
            case PRIORITY -> simple(player, change, "change the priority of an order",
                    state -> state.setOrderPriority(player.getUUID(), kingdom, change.order(), change.priority()), "priority_set");
            case AMOUNT -> simple(player, change, "change the amount of an order",
                    state -> state.setOrderAmount(player.getUUID(), kingdom, change.order(), change.amount()), "amount_set");
            case KIND -> simple(player, change, "change the kind of an order",
                    state -> state.setOrderKind(player.getUUID(), kingdom, change.order(), change.kind()), "kind_set");
            case UPDATE -> simple(player, change, "change an order", state -> update(state, player.getUUID(), change), "updated");
        };
        KingdomActions.reply(player, feedback);
        sendView(player, kingdom);
    }

    private static KingdomActions.Feedback create(ServerPlayer player, OrderPackets.Change change) {
        MinecraftServer server = player.server;
        KingdomWorldState.Ordering[] ordering = new KingdomWorldState.Ordering[1];
        Outcome outcome = KingdomWorldApi.change(server, "give an order", state -> {
            ordering[0] = state.order(player.getUUID(), change.kingdomId(), change.item(), change.kind(), change.amount(), change.priority(),
                    KingdomWorldApi.gameTime(server), ProductionSettings.rules().maxOrders(), item -> Production.orderable(server, item));
            return ordering[0].outcome();
        });
        if (!outcome.ok() || ordering[0] == null || ordering[0].order().isEmpty()) {
            return KingdomActions.Feedback.error(outcome.ok() ? KingdomError.NOT_ORDERABLE : outcome.error());
        }
        Order order = ordering[0].order().get();
        Production.plan(server, change.kingdomId());
        return KingdomActions.Feedback.ok(KEY + "created", sample(order.item()).getHoverName(), order.amount(),
                Component.translatable(KEY + "kind." + order.kind().id()));
    }

    /**
     * Changes amount, kind and priority of an order together; what stays the same is not an error.
     */
    private static Outcome update(KingdomWorldState state, UUID actor, OrderPackets.Change change) {
        List<Outcome> outcomes = List.of(state.setOrderAmount(actor, change.kingdomId(), change.order(), change.amount()),
                state.setOrderKind(actor, change.kingdomId(), change.order(), change.kind()),
                state.setOrderPriority(actor, change.kingdomId(), change.order(), change.priority()));
        boolean changed = false;
        for (Outcome outcome : outcomes) {
            if (!outcome.ok() && outcome.error() != KingdomError.UNCHANGED) {
                return outcome;
            }
            changed |= outcome.ok();
        }
        return changed ? Outcome.DONE : Outcome.fail(KingdomError.UNCHANGED);
    }

    private static KingdomActions.Feedback simple(ServerPlayer player, OrderPackets.Change change, String what,
                                                  java.util.function.Function<KingdomWorldState, Outcome> action, String done) {
        MinecraftServer server = player.server;
        Optional<Order> before = KingdomWorldApi.state(server).orders().get(change.kingdomId(), change.order());
        Outcome outcome = KingdomWorldApi.change(server, what, action);
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Production.plan(server, change.kingdomId());
        Component name = before.map(order -> sample(order.item()).getHoverName()).orElse(Component.empty());
        return switch (done) {
            case "priority_set" -> KingdomActions.Feedback.ok(KEY + "priority_set", name,
                    Component.translatable(KEY + "priority." + change.priority().id()));
            case "amount_set" -> KingdomActions.Feedback.ok(KEY + "amount_set", name, change.amount());
            case "kind_set" -> KingdomActions.Feedback.ok(KEY + "kind_set", name, Component.translatable(KEY + "kind." + change.kind().id()));
            case "paused" -> KingdomActions.Feedback.ok(KEY + "paused", name);
            case "updated" -> KingdomActions.Feedback.ok(KEY + "updated", name, change.amount(),
                    Component.translatable(KEY + "kind." + change.kind().id()), Component.translatable(KEY + "priority." + change.priority().id()));
            case "resumed" -> KingdomActions.Feedback.ok(KEY + "resumed", name);
            default -> KingdomActions.Feedback.ok(KEY + "cancelled", name);
        };
    }
}
