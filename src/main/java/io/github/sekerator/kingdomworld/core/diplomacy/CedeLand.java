package io.github.sekerator.kingdomworld.core.diplomacy;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.LongToIntFunction;
import java.util.function.ToIntFunction;

/**
 * What the land says of a province asked for in a cession, for the rules of diplomacy, which know no land of their own: who
 * holds it, whether the capital of its holder stands in it, and whether it touches the land of the kingdom that asks for
 * it. The server reads it from the territory of the world when the offer is made and again when it is accepted; {@code null}
 * stands for a province that does not exist.
 *
 * @param holder  the kingdom that holds the province, {@code null} for nobody
 * @param heart   whether the capital of its holder stands in it: the heart of a state is ceded only by an annexation
 * @param borders whether a neighbour province or a claimed chunk of the asking kingdom touches it
 */
public record CedeLand(UUID holder, boolean heart, boolean borders) {
    /**
     * Whether a kingdom may be asked for a province, as far as the land goes: the province exists, the kingdom holds it, it
     * is not its heart and it touches the land of the one who asks.
     *
     * @param to   the kingdom asked to cede it
     * @param land what the land says of the province, {@code null} when there is no such province
     */
    public static Outcome check(UUID to, int province, CedeLand land) {
        if (province < 1 || land == null) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (!to.equals(land.holder())) {
            return Outcome.fail(KingdomError.NOT_OWNED);
        }
        if (land.heart()) {
            return Outcome.fail(KingdomError.CAPITAL_CHUNK);
        }
        if (!land.borders()) {
            return Outcome.fail(KingdomError.NOT_ADJACENT);
        }
        return Outcome.DONE;
    }

    /**
     * The provinces of others that touch the land of a kingdom: the neighbours of its provinces and the provinces a claim of
     * it shares a side with. Its own provinces are not among them.
     *
     * @param own        the provinces the kingdom holds
     * @param neighbours the neighbours of a province
     * @param claims     the chunks the kingdom claims, in the world of the provinces
     * @param provinceAt the province of a chunk, 0 for none
     */
    public static Set<Integer> bordering(Collection<Integer> own, IntFunction<List<Integer>> neighbours, Iterable<Long> claims,
                                         LongToIntFunction provinceAt) {
        Set<Integer> touching = new HashSet<>();
        for (int province : own) {
            touching.addAll(neighbours.apply(province));
        }
        for (long chunk : claims) {
            for (long side : ChunkKey.sides(chunk)) {
                touching.add(provinceAt.applyAsInt(side));
            }
        }
        touching.remove(0);
        touching.removeAll(own);
        return touching;
    }

    /**
     * The provinces a kingdom may ask each of its neighbours to cede: those of the neighbour that touch its land, never the
     * heart of the neighbour nor one held for the bearer of a charter, the lowest numbers first and at most a few of each.
     *
     * @param bordering the provinces that touch the land of the asker (see {@link #bordering})
     * @param holder    the kingdom that holds a province, {@code null} for nobody
     * @param heart     the province of the capital of a kingdom, 0 for none
     * @param chartered whether a province is held for the bearer of a charter
     * @param most      how many provinces of a neighbour at most
     * @return the provinces by the kingdom that holds them
     */
    public static Map<UUID, List<Integer>> candidates(Set<Integer> bordering, IntFunction<UUID> holder, UUID asker, ToIntFunction<UUID> heart,
                                                      IntPredicate chartered, int most) {
        Map<UUID, List<Integer>> byHolder = new HashMap<>();
        Map<UUID, Integer> hearts = new HashMap<>();
        for (int province : new TreeSet<>(bordering)) {
            UUID owner = holder.apply(province);
            if (owner == null || owner.equals(asker)) {
                continue;
            }
            List<Integer> list = byHolder.computeIfAbsent(owner, key -> new ArrayList<>());
            if (list.size() >= most || hearts.computeIfAbsent(owner, heart::applyAsInt) == province || chartered.test(province)) {
                continue;
            }
            list.add(province);
        }
        byHolder.values().removeIf(List::isEmpty);
        return byHolder;
    }
}
