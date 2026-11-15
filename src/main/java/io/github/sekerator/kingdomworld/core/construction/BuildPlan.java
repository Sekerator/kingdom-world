package io.github.sekerator.kingdomworld.core.construction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * The steps of one construction in the order they are done: first the blocks in the way are cleared from the top down,
 * then the template is built from the bottom up (see {@link PlacementOrder}). The plan comes from the template, the way it
 * is laid and the blocks that stood in the way when it was laid, so it is the same every time it is made again; a
 * {@link Construction} only remembers which steps are done.
 */
public final class BuildPlan {

    public enum Kind {
        CLEAR,
        PLACE
    }

    /**
     * One step.
     *
     * @param block index of the template block placed, -1 for clearing
     * @param cost  items the step takes
     */
    public record Step(int index, Kind kind, int x, int y, int z, int block, Map<String, Integer> cost) {
        public Step {
            Objects.requireNonNull(kind, "kind");
            cost = cost.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(cost));
        }
    }

    private final List<Step> steps;
    private final int clears;
    /**
     * The tier of every step: steps one after another of the same tier (a layer of the same kind of work) may be done in any
     * order, those of a later tier come after them.
     */
    private final int[] tiers;
    /** The steps that place a block leaning on others (a torch, a door). */
    private final java.util.BitSet leaning;
    /** The steps by their place, looked up when first asked: clearing and placing apart. */
    @org.jetbrains.annotations.Nullable
    private Map<Long, Integer> clearAt;
    @org.jetbrains.annotations.Nullable
    private Map<Long, Integer> placeAt;

    private BuildPlan(List<Step> steps, int clears, List<Object> kinds, java.util.BitSet leaning) {
        this.steps = List.copyOf(steps);
        this.clears = clears;
        this.leaning = leaning;
        this.tiers = new int[steps.size()];
        int tier = 0;
        for (int i = 1; i < steps.size(); i++) {
            if (steps.get(i).y() != steps.get(i - 1).y() || !kinds.get(i).equals(kinds.get(i - 1))) {
                tier++;
            }
            tiers[i] = tier;
        }
    }

    /**
     * A plan from the positions to clear and the blocks to place, both in the world.
     *
     * @param clear  positions {x, y, z} with a block in the way
     * @param blocks blocks of the template to place
     * @param costs  what each template block costs, by its index
     */
    public static BuildPlan of(List<int[]> clear, List<PlacementOrder.Block> blocks, List<Map<String, Integer>> costs) {
        List<Step> steps = new ArrayList<>();
        List<Object> kinds = new ArrayList<>();
        java.util.BitSet leaning = new java.util.BitSet();
        for (int[] pos : PlacementOrder.clearing(clear)) {
            steps.add(new Step(steps.size(), Kind.CLEAR, pos[0], pos[1], pos[2], -1, Map.of()));
            kinds.add(Kind.CLEAR);
        }
        int clears = steps.size();
        for (PlacementOrder.Block block : PlacementOrder.building(blocks)) {
            Map<String, Integer> cost = block.index() < costs.size() ? costs.get(block.index()) : Map.of();
            if (block.phase() == PlacementOrder.Phase.LEANING) {
                leaning.set(steps.size());
            }
            steps.add(new Step(steps.size(), Kind.PLACE, block.x(), block.y(), block.z(), block.index(), cost));
            kinds.add(block.phase());
        }
        return new BuildPlan(steps, clears, kinds, leaning);
    }

    /**
     * A plan that takes a building down: blocks that lean on others first, from the top down, then the standing ones from
     * the top down, so that nothing falls off and drops on its own.
     *
     * @param leaning  positions {x, y, z} of leaning blocks
     * @param standing positions of standing blocks
     */
    public static BuildPlan demolition(List<int[]> leaning, List<int[]> standing) {
        List<Step> steps = new ArrayList<>();
        List<Object> kinds = new ArrayList<>();
        for (int[] pos : PlacementOrder.clearing(leaning)) {
            steps.add(new Step(steps.size(), Kind.CLEAR, pos[0], pos[1], pos[2], -1, Map.of()));
            kinds.add(PlacementOrder.Phase.LEANING);
        }
        for (int[] pos : PlacementOrder.clearing(standing)) {
            steps.add(new Step(steps.size(), Kind.CLEAR, pos[0], pos[1], pos[2], -1, Map.of()));
            kinds.add(PlacementOrder.Phase.STANDING);
        }
        return new BuildPlan(steps, steps.size(), kinds, new java.util.BitSet());
    }

    public List<Step> steps() {
        return steps;
    }

    public int size() {
        return steps.size();
    }

    public Step step(int index) {
        return steps.get(index);
    }

    /**
     * Steps that clear; they come first.
     */
    public int clears() {
        return clears;
    }

    /**
     * Blocks the plan places.
     */
    public int places() {
        return steps.size() - clears;
    }

    /**
     * The next step to do: the first one that is neither done nor waiting, among those {@code busy} does not exclude (taken
     * by another builder, or put off for a while). Building starts only when every step of clearing is done or waiting.
     */
    public OptionalInt next(Construction construction, IntPredicate busy) {
        boolean cleared = true;
        for (Step step : steps) {
            int index = step.index();
            if (construction.isDone(index) || construction.isPending(index)) {
                continue;
            }
            if (step.kind() == Kind.CLEAR) {
                cleared = false;
                if (!busy.test(index)) {
                    return OptionalInt.of(index);
                }
                continue;
            }
            if (!cleared) {
                return OptionalInt.empty();
            }
            if (!busy.test(index)) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * The tier of a step: the steps of one tier (a layer of the same kind of work, found one after another in the plan) may be
     * done in any order, so that builders working together may share them out; those of a later tier come after them.
     */
    public int tier(int index) {
        return tiers[index];
    }

    /**
     * Whether a step waits for an earlier one that is neither done nor paid for: a block for the block of the plan under it, a
     * block that leans on others (a torch, a door) for the blocks of the plan beside and over it too, clearing for the block of
     * the plan over it. Steps done in their order never wait; a builder that leaves a step for later (another builder works on
     * it, somebody stands there) builds nothing on what is not there yet.
     */
    public boolean waits(Construction construction, int index) {
        Step step = steps.get(index);
        if (step.kind() == Kind.CLEAR) {
            return waitsOn(construction, index, step.x(), step.y() + 1, step.z());
        }
        if (waitsOn(construction, index, step.x(), step.y() - 1, step.z())) {
            return true;
        }
        return leaning.get(index) && (waitsOn(construction, index, step.x() + 1, step.y(), step.z())
                || waitsOn(construction, index, step.x() - 1, step.y(), step.z())
                || waitsOn(construction, index, step.x(), step.y(), step.z() + 1)
                || waitsOn(construction, index, step.x(), step.y(), step.z() - 1)
                || waitsOn(construction, index, step.x(), step.y() + 1, step.z()));
    }

    /**
     * Whether a step of the same kind at a place belongs to an earlier tier and is neither done nor paid for.
     */
    private boolean waitsOn(Construction construction, int index, int x, int y, int z) {
        Integer other = (steps.get(index).kind() == Kind.CLEAR ? clearIndex() : placeIndex()).get(pack(x, y, z));
        return other != null && tiers[other] < tiers[index] && !construction.isDone(other) && !construction.isPending(other);
    }

    /**
     * The step that places a block at a place, or -1 if no block of the plan goes there.
     */
    public int placeAt(int x, int y, int z) {
        Integer index = placeIndex().get(pack(x, y, z));
        return index == null ? -1 : index;
    }

    private Map<Long, Integer> clearIndex() {
        if (clearAt == null) {
            clearAt = index(0, clears);
        }
        return clearAt;
    }

    private Map<Long, Integer> placeIndex() {
        if (placeAt == null) {
            placeAt = index(clears, steps.size());
        }
        return placeAt;
    }

    private Map<Long, Integer> index(int from, int to) {
        Map<Long, Integer> result = new java.util.HashMap<>();
        for (int i = from; i < to; i++) {
            Step step = steps.get(i);
            result.putIfAbsent(pack(step.x(), step.y(), step.z()), i);
        }
        return result;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /**
     * Goes through the steps {@link #next} gives one after another were each of them taken in turn, in their order, in one
     * pass over the plan, until the visitor says to stop.
     *
     * @param visitor told every such step; {@code false} stops
     * @return whether the visitor stopped before the steps ran out
     */
    public boolean forEachNext(Construction construction, IntPredicate busy, java.util.function.Predicate<Step> visitor) {
        boolean cleared = true;
        for (Step step : steps) {
            int index = step.index();
            if (construction.isDone(index) || construction.isPending(index)) {
                continue;
            }
            if (step.kind() == Kind.CLEAR) {
                cleared = false;
            } else if (!cleared) {
                return false;
            }
            if (!busy.test(index) && !visitor.test(step)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every step of clearing is done or waiting to be done in the world.
     */
    public boolean cleared(Construction construction) {
        for (int i = 0; i < clears; i++) {
            if (!construction.isDone(i) && !construction.isPending(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * What the blocks not built yet still cost: steps neither done nor paid for.
     */
    public Map<String, Integer> remaining(Construction construction) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = clears; i < steps.size(); i++) {
            if (!construction.isDone(i) && !construction.isPending(i)) {
                steps.get(i).cost().forEach((item, count) -> result.merge(item, count, Integer::sum));
            }
        }
        return result;
    }

    /**
     * What the construction still needs brought: the cost of the rest less what lies at the site already.
     */
    public Map<String, Integer> needed(Construction construction) {
        Map<String, Integer> result = new LinkedHashMap<>();
        remaining(construction).forEach((item, count) -> {
            int left = count - construction.delivered().getOrDefault(item, 0);
            if (left > 0) {
                result.put(item, left);
            }
        });
        return result;
    }

    /**
     * Blocks placed or paid for and waiting to be placed.
     */
    public int built(Construction construction) {
        int count = 0;
        for (int i = clears; i < steps.size(); i++) {
            if (construction.isDone(i) || construction.isPending(i)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether every step is done in the world.
     */
    public boolean finished(Construction construction) {
        for (int i = 0; i < steps.size(); i++) {
            if (!construction.isDone(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Built share from 0 to 100, counting placed blocks only.
     */
    public int percent(Construction construction) {
        int places = places();
        if (places == 0) {
            // A demolition: the share of blocks taken down.
            if (steps.isEmpty()) {
                return finished(construction) ? 100 : 0;
            }
            return (int) Math.min(100, construction.doneCount() * 100L / steps.size());
        }
        int placed = 0;
        for (int i = clears; i < steps.size(); i++) {
            if (construction.isDone(i)) {
                placed++;
            }
        }
        return (int) Math.min(100, placed * 100L / places);
    }

    /**
     * Blocks placed in the world.
     */
    public int placed(Construction construction) {
        int placed = 0;
        for (int i = clears; i < steps.size(); i++) {
            if (construction.isDone(i)) {
                placed++;
            }
        }
        return placed;
    }
}
