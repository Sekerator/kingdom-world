package io.github.sekerator.kingdomworld.core.construction;

import java.util.Map;
import java.util.Objects;

/**
 * What one block of a template costs, as the game side describes it: the item it is built from and how it counts.
 *
 * @param item  item id, empty when the block costs nothing
 * @param kind  how the block counts
 * @param count items for {@link Kind#SEVERAL}
 */
public record BlockCost(String item, Kind kind, int count) {
    public enum Kind {
        /** One item. */
        ONE,
        /** The second part of a block counted once (the upper half of a door or a tall plant, the head of a bed). */
        PART,
        /** Two items: a double slab. */
        DOUBLE,
        /** Several items in one block: candles, sea pickles. */
        SEVERAL,
        /** A liquid; skipped by default, the template notes it. */
        LIQUID,
        /** Air, fire and the like: nothing to build. */
        NOTHING,
        /** A block no item makes (a portal, a spawner, bedrock): the template cannot be built. */
        UNOBTAINABLE,
        /** A block placed for nothing: the flag of a kingdom, which has no item. */
        FREE
    }

    public static final BlockCost NOTHING = new BlockCost("", Kind.NOTHING, 0);

    public BlockCost {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(kind, "kind");
        count = Math.max(0, count);
    }

    public static BlockCost one(String item) {
        return new BlockCost(item, Kind.ONE, 1);
    }

    public static BlockCost part(String item) {
        return new BlockCost(item, Kind.PART, 0);
    }

    public static BlockCost twice(String item) {
        return new BlockCost(item, Kind.DOUBLE, 2);
    }

    public static BlockCost several(String item, int count) {
        return new BlockCost(item, Kind.SEVERAL, count);
    }

    public static BlockCost liquid(String item) {
        return new BlockCost(item, Kind.LIQUID, 0);
    }

    public static BlockCost unobtainable(String item) {
        return new BlockCost(item, Kind.UNOBTAINABLE, 0);
    }

    /**
     * A block placed for nothing.
     */
    public static BlockCost free() {
        return new BlockCost("", Kind.FREE, 0);
    }

    /**
     * Items placing the block takes.
     */
    public int items() {
        return switch (kind) {
            case ONE -> 1;
            case DOUBLE -> 2;
            case SEVERAL -> Math.max(1, count);
            default -> 0;
        };
    }

    /**
     * Whether a builder places the block at all; parts come with their main block, liquids and nothing are left out.
     */
    public boolean placed() {
        return kind == Kind.ONE || kind == Kind.DOUBLE || kind == Kind.SEVERAL || kind == Kind.FREE;
    }

    /**
     * The items as a map, empty when the block costs nothing.
     */
    public Map<String, Integer> asMap() {
        int items = items();
        return items <= 0 || item.isEmpty() ? Map.of() : Map.of(item, items);
    }
}
