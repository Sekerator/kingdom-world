package io.github.sekerator.kingdomworld.core.commerce;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The trades of the running server, read from data packs; nothing of it is saved. Until data is read, and wherever none says
 * otherwise, the trades of the mod hold: the general store, the trading house and seven crafts. The general store and the
 * trading house are always there, so that a place always has a trade.
 */
public final class TradeRegistry {
    /** The workstation that only marks the place of a tailor; no recipe is made at it. */
    public static final String LOOM = io.github.sekerator.kingdomworld.core.production.Stations.LOOM;

    private static volatile TradeRegistry current = builtIn();

    private final Map<String, Trade> trades;

    public TradeRegistry(Collection<Trade> trades) {
        Map<String, Trade> byId = new LinkedHashMap<>();
        for (Trade trade : trades) {
            byId.put(trade.id(), trade);
        }
        if (!byId.containsKey(Business.GENERAL_STORE) || !byId.containsKey(Trade.TRADER)) {
            for (Trade trade : defaults()) {
                if (trade.id().equals(Business.GENERAL_STORE) || trade.id().equals(Trade.TRADER)) {
                    byId.putIfAbsent(trade.id(), trade);
                }
            }
        }
        this.trades = Collections.unmodifiableMap(byId);
    }

    public static TradeRegistry current() {
        return current;
    }

    /**
     * Takes the trades read from data packs.
     */
    public static void set(TradeRegistry registry) {
        current = registry == null ? builtIn() : registry;
    }

    public Optional<Trade> get(String id) {
        return Optional.ofNullable(trades.get(id));
    }

    /**
     * A trade by id; an unknown one (a data pack took it away) resells.
     */
    public Trade trade(String id) {
        Trade trade = trades.get(id);
        return trade != null ? trade : trades.get(Business.GENERAL_STORE);
    }

    public Collection<Trade> all() {
        return trades.values();
    }

    /**
     * The crafts, in a stable order.
     */
    public List<Trade> crafts() {
        List<Trade> crafts = new ArrayList<>();
        for (Trade trade : trades.values()) {
            if (trade.craft()) {
                crafts.add(trade);
            }
        }
        return crafts;
    }

    // ---------------------------------------------------------------- the trades of the mod

    /**
     * The trades of the mod, the same as its data.
     */
    public static TradeRegistry builtIn() {
        return new TradeRegistry(defaults());
    }

    private static List<String> group(String... stations) {
        return List.of(stations);
    }

    public static List<Trade> defaults() {
        Map<GoodsCategory, String> none = new EnumMap<>(GoodsCategory.class);
        Map<GoodsCategory, String> wooden = new EnumMap<>(GoodsCategory.class);
        wooden.put(GoodsCategory.TOOLS, "kingdomworld:goods/wooden_tools");
        List<Trade> trades = new ArrayList<>();
        trades.add(new Trade(Business.GENERAL_STORE, Trade.Kind.RESALE, List.of(GoodsCategory.values()), List.of(), List.of(), "", false, none));
        trades.add(new Trade("bakery", Trade.Kind.CRAFT, List.of(GoodsCategory.FOOD), List.of(group("crafting_table"), group("furnace")), List.of(),
                "cook", false, none));
        trades.add(new Trade("cookshop", Trade.Kind.CRAFT, List.of(GoodsCategory.FOOD), List.of(group("smoker", "campfire")), List.of(), "cook",
                false, none));
        trades.add(new Trade("carpenter", Trade.Kind.CRAFT, List.of(GoodsCategory.HOUSEHOLD, GoodsCategory.MATERIALS, GoodsCategory.TOOLS),
                List.of(group("crafting_table")), List.of(), "crafter", false, wooden));
        trades.add(new Trade("toolmaker", Trade.Kind.CRAFT, List.of(GoodsCategory.TOOLS), List.of(group("crafting_table"), group("smithing_table")),
                List.of(), "smith", false, none));
        trades.add(new Trade("tailor", Trade.Kind.CRAFT, List.of(GoodsCategory.CLOTHING), List.of(group("crafting_table")), List.of(LOOM),
                "crafter", false, none));
        trades.add(new Trade("mason", Trade.Kind.CRAFT, List.of(GoodsCategory.MATERIALS), List.of(group("stonecutter")), List.of(), "crafter",
                false, none));
        trades.add(new Trade("glazier", Trade.Kind.CRAFT, List.of(GoodsCategory.HOUSEHOLD, GoodsCategory.MATERIALS), List.of(group("furnace")),
                List.of(), "smelter", false, none));
        trades.add(new Trade(Trade.TRADER, Trade.Kind.EXPORT, List.of(), List.of(), List.of(), "merchant", true, none));
        return trades;
    }
}
