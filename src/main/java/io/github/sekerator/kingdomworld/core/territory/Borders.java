package io.github.sekerator.kingdomworld.core.territory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * Whom a state borders by its provinces: the kingdoms that hold a province next to one of its own. A ruler of a state sees its
 * neighbours this way, as a ruler of chunks sees them over the edge of its claims.
 */
public final class Borders {
    private Borders() {
    }

    /**
     * The kingdoms holding a province next to one of a kingdom's own, each once, in the order of its provinces and of their
     * neighbours, at most {@code most}; land of nobody and the kingdom itself are left out.
     *
     * @param own        the provinces the kingdom holds
     * @param neighbours the neighbours of a province
     * @param holder     the kingdom holding a province, {@code null} for nobody
     */
    public static Set<UUID> holders(Collection<Integer> own, IntFunction<List<Integer>> neighbours, IntFunction<UUID> holder, UUID self,
                                    int most) {
        Set<UUID> found = new LinkedHashSet<>();
        if (most <= 0) {
            return found;
        }
        for (int province : own) {
            for (int near : neighbours.apply(province)) {
                UUID owner = holder.apply(near);
                if (owner != null && !owner.equals(self) && found.add(owner) && found.size() >= most) {
                    return found;
                }
            }
        }
        return found;
    }

    /**
     * The kingdoms that stand in the way of a state that wants land: those that border it (see {@link #holders}), but nobody
     * while a province next to it is still free, for a state takes free wild land next to it by itself now and then.
     *
     * @param free whether a province is free for the state to take: wild land of nobody, not promised to anyone
     */
    public static Set<UUID> inTheWay(Collection<Integer> own, IntFunction<List<Integer>> neighbours, IntFunction<UUID> holder, IntPredicate free,
                                     UUID self, int most) {
        for (int province : own) {
            for (int near : neighbours.apply(province)) {
                if (free.test(near)) {
                    return new LinkedHashSet<>();
                }
            }
        }
        return holders(own, neighbours, holder, self, most);
    }
}
