package io.github.sekerator.kingdomworld.core.trade;

import java.util.Objects;

/**
 * Items as trade sees them: an item id, a count, and the item's own data as text (empty for an item without data). The
 * core never reads the data; the game turns it back into an item, so enchantments and names survive a lot on the market
 * or a trip in a caravan.
 *
 * @param item  the item id, for example {@code minecraft:iron_ingot}
 * @param count how many, from 1 to {@link #MAX_COUNT}
 * @param data  the item's data as the game writes it, or an empty string
 */
public record Goods(String item, int count, String data) {
    /** Most items one entry of goods holds. */
    public static final int MAX_COUNT = 1_000_000;
    /** Longest data of an item the trade keeps. */
    public static final int MAX_DATA = 32_768;
    /** Longest item id. */
    public static final int MAX_ITEM = 256;

    public Goods {
        Objects.requireNonNull(item, "item");
        data = data == null ? "" : data;
        if (item.isEmpty() || item.length() > MAX_ITEM || count < 1 || count > MAX_COUNT || data.length() > MAX_DATA) {
            throw new IllegalArgumentException("Goods out of range: " + item + " x" + count);
        }
    }

    public static Goods of(String item, int count) {
        return new Goods(item, count, "");
    }

    /**
     * The same goods in another number.
     */
    public Goods withCount(int value) {
        return new Goods(item, value, data);
    }

    public boolean hasData() {
        return !data.isEmpty();
    }
}
