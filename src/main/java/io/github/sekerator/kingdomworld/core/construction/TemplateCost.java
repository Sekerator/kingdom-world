package io.github.sekerator.kingdomworld.core.construction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a whole template costs: the items of all its blocks, with doors and beds counted once, double slabs twice, candles
 * by their number; liquids are left out and noted, and blocks without an item make the template unbuildable.
 *
 * @param items         item id to count, in the order the items first appear
 * @param blocks        blocks a builder places
 * @param liquids       liquid blocks left out
 * @param unobtainable  ids of blocks no item makes
 */
public record TemplateCost(Map<String, Integer> items, int blocks, int liquids, Set<String> unobtainable) {
    public TemplateCost {
        items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
        unobtainable = Collections.unmodifiableSet(new LinkedHashSet<>(unobtainable));
    }

    public static TemplateCost of(List<BlockCost> costs) {
        Map<String, Integer> items = new LinkedHashMap<>();
        Set<String> unobtainable = new LinkedHashSet<>();
        int blocks = 0;
        int liquids = 0;
        for (BlockCost cost : costs) {
            switch (cost.kind()) {
                case LIQUID -> liquids++;
                case UNOBTAINABLE -> unobtainable.add(cost.item());
                default -> {
                    if (cost.placed()) {
                        blocks++;
                    }
                    cost.asMap().forEach((item, count) -> items.merge(item, count, Integer::sum));
                }
            }
        }
        return new TemplateCost(items, blocks, liquids, unobtainable);
    }

    /**
     * Items in all, of every kind.
     */
    public long total() {
        long sum = 0;
        for (int count : items.values()) {
            sum += count;
        }
        return sum;
    }

    public boolean buildable() {
        return unobtainable.isEmpty();
    }
}
