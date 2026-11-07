package io.github.sekerator.kingdomworld.core.construction;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The plan of a repair: the builders put back only the template blocks found missing or changed at the last look at the
 * building, and pay only for those.
 */
public final class Repairs {

    private Repairs() {
    }

    /**
     * The template blocks a repair leaves as they are: every block not found damaged.
     *
     * @param blocks blocks of the template
     */
    public static BitSet skip(int blocks, BitSet damaged) {
        BitSet skip = new BitSet(blocks);
        skip.set(0, Math.max(0, blocks));
        skip.andNot(damaged);
        return skip;
    }

    /**
     * What putting back the damaged blocks costs.
     *
     * @param costs what each template block costs, by its index
     */
    public static Map<String, Integer> cost(List<Map<String, Integer>> costs, BitSet damaged) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = damaged.nextSetBit(0); i >= 0 && i < costs.size(); i = damaged.nextSetBit(i + 1)) {
            costs.get(i).forEach((item, count) -> result.merge(item, count, Integer::sum));
        }
        return result;
    }

    /**
     * What a demolition gives back for a whole building: the cost of its blocks less the loss.
     */
    public static Map<String, Integer> salvage(Map<String, Integer> cost, int lossPercent) {
        Map<String, Integer> result = new LinkedHashMap<>();
        cost.forEach((item, count) -> {
            int back = Salvage.total(count, lossPercent);
            if (back > 0) {
                result.put(item, back);
            }
        });
        return result;
    }
}
