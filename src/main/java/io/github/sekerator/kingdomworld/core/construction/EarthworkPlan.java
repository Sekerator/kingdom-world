package io.github.sekerator.kingdomworld.core.construction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What every block of the fill of earthworks is made of, and what of the ground dug is left over. The fill takes what the work
 * itself digs first, then what the storehouses hold: earth first, then stone and cobblestone, then gravel and sand. The top
 * block of every filled column is earth while there is earth enough, otherwise whatever there is. What is dug and not wanted
 * for the fill is the spoil: it goes to the storehouses while they have room, and the rest goes to the spoil heap and is gone,
 * so that full storehouses never stop the work. Ores and everything else of worth always go to the storehouses.
 */
public final class EarthworkPlan {
    public static final String DIRT = "minecraft:dirt";
    /** The items of the ground a fill is made of, in the order it takes them. */
    public static final List<String> FILL = List.of(DIRT, "minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:andesite",
            "minecraft:diorite", "minecraft:granite", "minecraft:tuff", "minecraft:calcite", "minecraft:gravel", "minecraft:sand",
            "minecraft:red_sand");
    /** What a fill takes from the storehouses once what the work digs is used up, in that order. */
    public static final List<String> FROM_STOCK = List.of(DIRT, "minecraft:cobblestone");

    /**
     * A block of the fill: where it goes, from the north west corner of the work, and the item it is made of.
     */
    public record Fill(int dx, int y, int dz, String item) {
    }

    /**
     * The plan of the fill.
     *
     * @param fills   every block of the fill, column by column, each from its bottom up
     * @param fromDug what the fill takes of what the work digs, by item
     * @param spoil   what the work digs that the fill does not take, by item
     * @param missing blocks of the fill neither the work nor the storehouses have the earth for: they wait for earth
     */
    public record Result(List<Fill> fills, Map<String, Integer> fromDug, Map<String, Integer> fromStock, Map<String, Integer> spoil,
                         int missing) {
    }

    private EarthworkPlan() {
    }

    /**
     * Whether an item is of the ground a fill may be made of: what goes to the spoil heap when nothing wants it.
     */
    public static boolean soil(String item) {
        return FILL.contains(item) || "minecraft:stone".equals(item) || "minecraft:deepslate".equals(item) || "minecraft:grass_block".equals(item)
                || "minecraft:coarse_dirt".equals(item) || "minecraft:rooted_dirt".equals(item) || "minecraft:clay_ball".equals(item)
                || "minecraft:flint".equals(item) || "minecraft:snowball".equals(item);
    }

    public static Result of(Earthwork work) {
        Map<String, Integer> dug = new LinkedHashMap<>();
        Map<String, Integer> stock = new LinkedHashMap<>();
        for (String item : FILL) {
            dug.put(item, work.dug().getOrDefault(item, 0));
        }
        for (String item : FROM_STOCK) {
            stock.put(item, work.stock().getOrDefault(item, 0));
        }
        Map<String, Integer> fromDug = new LinkedHashMap<>();
        Map<String, Integer> fromStock = new LinkedHashMap<>();
        int[] missing = {0};
        // The top blocks first: earth while there is earth.
        String[] tops = new String[work.sizeX() * work.sizeZ()];
        for (int dz = 0; dz < work.sizeZ(); dz++) {
            for (int dx = 0; dx < work.sizeX(); dx++) {
                if (work.fillTo(dx, dz) >= work.fillFrom(dx, dz)) {
                    String item = takeEarth(dug, stock, fromDug, fromStock);
                    tops[dz * work.sizeX() + dx] = item != null ? item : take(dug, stock, fromDug, fromStock, missing);
                }
            }
        }
        List<Fill> fills = new ArrayList<>();
        for (int dz = 0; dz < work.sizeZ(); dz++) {
            for (int dx = 0; dx < work.sizeX(); dx++) {
                int from = work.fillFrom(dx, dz);
                int to = work.fillTo(dx, dz);
                for (int y = from; y <= to; y++) {
                    String item = y == to ? tops[dz * work.sizeX() + dx] : take(dug, stock, fromDug, fromStock, missing);
                    fills.add(new Fill(dx, y, dz, item));
                }
            }
        }
        Map<String, Integer> spoil = new LinkedHashMap<>();
        work.dug().forEach((item, count) -> {
            int left = count - fromDug.getOrDefault(item, 0);
            if (left > 0) {
                spoil.put(item, left);
            }
        });
        return new Result(List.copyOf(fills), Map.copyOf(fromDug), Map.copyOf(fromStock), Map.copyOf(spoil), missing[0]);
    }

    /**
     * Earth for a top block: of what the work digs, then of the storehouses; {@code null} when there is none.
     */
    private static String takeEarth(Map<String, Integer> dug, Map<String, Integer> stock, Map<String, Integer> fromDug,
                                    Map<String, Integer> fromStock) {
        if (dug.getOrDefault(DIRT, 0) > 0) {
            dug.merge(DIRT, -1, Integer::sum);
            fromDug.merge(DIRT, 1, Integer::sum);
            return DIRT;
        }
        if (stock.getOrDefault(DIRT, 0) > 0) {
            stock.merge(DIRT, -1, Integer::sum);
            fromStock.merge(DIRT, 1, Integer::sum);
            return DIRT;
        }
        return null;
    }

    /**
     * A block of the fill: of what the work digs in the order of {@link #FILL}, then of the storehouses in the order of
     * {@link #FROM_STOCK}; earth that is not there yet when there is nothing at all.
     */
    private static String take(Map<String, Integer> dug, Map<String, Integer> stock, Map<String, Integer> fromDug, Map<String, Integer> fromStock,
                               int[] missing) {
        for (String item : FILL) {
            if (dug.getOrDefault(item, 0) > 0) {
                dug.merge(item, -1, Integer::sum);
                fromDug.merge(item, 1, Integer::sum);
                return item;
            }
        }
        for (String item : FROM_STOCK) {
            if (stock.getOrDefault(item, 0) > 0) {
                stock.merge(item, -1, Integer::sum);
                fromStock.merge(item, 1, Integer::sum);
                return item;
            }
        }
        missing[0]++;
        return DIRT;
    }

    /**
     * Where a stack dug by the work goes: wanted at the site for the fill, to the storehouses, or to the spoil heap.
     */
    public enum Destination {
        SITE,
        STOREHOUSE,
        SPOIL
    }

    /**
     * Where items dug go: what the fill still lacks of them stays at the site; the rest of the ground goes to the storehouses
     * while they have room and then to the spoil heap; everything else always goes to the storehouses.
     *
     * @param lacking what the fill of the work still lacks of the item: its blocks not placed yet less what is at the site
     * @param room    whether the storehouses have room for it
     */
    public static Destination destination(String item, int lacking, boolean room) {
        if (lacking > 0 && FILL.contains(item)) {
            return Destination.SITE;
        }
        if (!soil(item) || room) {
            return Destination.STOREHOUSE;
        }
        return Destination.SPOIL;
    }
}
