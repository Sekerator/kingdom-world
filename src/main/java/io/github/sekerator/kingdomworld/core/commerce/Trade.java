package io.github.sekerator.kingdomworld.core.commerce;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A trade a shop of citizens takes up: the general store resells, a trading house buys of the masters of its city and exports,
 * and a craft makes goods of its kinds at the workstations of its place by real recipes, of raw materials it buys.
 *
 * <p>Trades come from data packs (the folder {@code kingdomworld_trades}), which add and change them; see {@link TradeRegistry}.
 *
 * @param id         the id, also the end of the key of its name
 * @param kind       what the trade does
 * @param goods      the kinds of goods it makes or sells
 * @param stations   the workstations its recipes are made at: one of each group must stand at the place
 * @param placeNeeds workstations that must stand at the place though no recipe is made at them (the loom of a tailor)
 * @param profession the profession of the crown whose usual wage is the usual income of its masters, empty for a shopkeeper
 * @param market     whether it needs a working market in its city or a house of a merchant as its place
 * @param only       item tags that narrow a kind of goods for this trade (the wooden tools of a carpenter), by kind
 */
public record Trade(String id, Kind kind, List<GoodsCategory> goods, List<List<String>> stations, List<String> placeNeeds, String profession,
                    boolean market, Map<GoodsCategory, String> only) {
    /** The trading house. */
    public static final String TRADER = "trader";

    /**
     * What a trade does.
     */
    public enum Kind {
        /** It resells what it bought of the crown and of players. */
        RESALE,
        /** Its masters make goods. */
        CRAFT,
        /** It buys what the masters of its city made and exports it. */
        EXPORT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id().equals(id)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Unknown kind of trade " + id);
        }
    }

    public Trade {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        goods = List.copyOf(goods);
        List<List<String>> groups = new ArrayList<>();
        for (List<String> group : stations) {
            if (!group.isEmpty()) {
                groups.add(List.copyOf(group));
            }
        }
        stations = List.copyOf(groups);
        placeNeeds = List.copyOf(placeNeeds);
        profession = profession == null ? "" : profession;
        Map<GoodsCategory, String> narrowed = new EnumMap<>(GoodsCategory.class);
        narrowed.putAll(only);
        only = java.util.Collections.unmodifiableMap(narrowed);
    }

    public boolean craft() {
        return kind == Kind.CRAFT;
    }

    public boolean makes(GoodsCategory category) {
        return goods.contains(category);
    }

    public boolean makes(int category) {
        return category >= 0 && category < GoodsCategory.values().length && makes(GoodsCategory.values()[category]);
    }

    /**
     * Every workstation its recipes may be made at.
     */
    public Set<String> recipeStations() {
        Set<String> all = new LinkedHashSet<>();
        for (List<String> group : stations) {
            all.addAll(group);
        }
        return all;
    }

    /**
     * Whether a place with these workstations serves the trade: one of each group, and everything the place needs.
     */
    public boolean fits(Collection<String> placeStations) {
        for (List<String> group : stations) {
            boolean any = false;
            for (String station : group) {
                if (placeStations.contains(station)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return placeStations.containsAll(placeNeeds);
    }

    /**
     * The workstations of a place its recipes are made at.
     */
    public Set<String> stationsAt(Collection<String> placeStations) {
        Set<String> found = new LinkedHashSet<>();
        for (String station : recipeStations()) {
            if (placeStations.contains(station)) {
                found.add(station);
            }
        }
        return found;
    }

    /**
     * Reads a trade from the JSON of a data pack:
     *
     * <pre>
     * { "kind": "craft", "goods": ["food"], "stations": [["crafting_table"], ["furnace"]], "place": ["loom"],
     *   "profession": "cook", "market": false, "only": { "tools": "kingdomworld:goods/wooden_tools" } }
     * </pre>
     */
    public static Trade parse(String id, JsonObject json) {
        Kind kind = Kind.byId(json.has("kind") ? json.get("kind").getAsString() : "craft");
        List<GoodsCategory> goods = new ArrayList<>();
        for (String name : strings(json, "goods")) {
            goods.add(GoodsCategory.byId(name).orElseThrow(() -> new IllegalArgumentException("Unknown kind of goods " + name)));
        }
        List<List<String>> stations = new ArrayList<>();
        if (json.has("stations")) {
            for (JsonElement element : json.getAsJsonArray("stations")) {
                List<String> group = new ArrayList<>();
                if (element.isJsonArray()) {
                    for (JsonElement station : element.getAsJsonArray()) {
                        group.add(station.getAsString());
                    }
                } else {
                    group.add(element.getAsString());
                }
                stations.add(group);
            }
        }
        Map<GoodsCategory, String> only = new EnumMap<>(GoodsCategory.class);
        if (json.has("only")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("only").entrySet()) {
                GoodsCategory category = GoodsCategory.byId(entry.getKey())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown kind of goods " + entry.getKey()));
                only.put(category, entry.getValue().getAsString());
            }
        }
        String profession = json.has("profession") ? json.get("profession").getAsString() : "";
        boolean market = json.has("market") && json.get("market").getAsBoolean();
        return new Trade(id, kind, goods, stations, strings(json, "place"), profession, market, only);
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> values = new ArrayList<>();
        if (json.has(key)) {
            JsonArray array = json.getAsJsonArray(key);
            for (JsonElement element : array) {
                values.add(element.getAsString());
            }
        }
        return values;
    }
}
