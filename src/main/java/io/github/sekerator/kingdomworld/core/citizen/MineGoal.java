package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What a miner's post is asked for: a kind of ore, how much of it the storehouses should hold, and the depth of
 * the mine.
 *
 * @param ore       id of the tag of the kind of ore, for example {@code forge:ores/iron}; empty for anything
 * @param amount    items of what the ore drops the storehouses should hold; 0 for no limit
 * @param autoDepth whether the depth comes from the kind of ore (otherwise from {@code depth})
 * @param depth     the height the player chose; counts only without {@code autoDepth}
 */
public record MineGoal(String ore, int amount, boolean autoDepth, int depth) {
    /** Longest id of a kind of ore; ids travel over the network and into saved data. */
    public static final int MAX_ORE_LENGTH = 128;
    public static final int MAX_AMOUNT = 9_984;
    public static final int MIN_DEPTH = -2_048;
    public static final int MAX_DEPTH = 2_047;
    /** Anything, without a limit, at the automatic depth: what a post does before anybody asks for more. */
    public static final MineGoal ANYTHING = new MineGoal("", 0, true, 0);

    private static final Pattern TAG_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public MineGoal {
        Objects.requireNonNull(ore, "ore");
        if (!isValidOre(ore)) {
            throw new IllegalArgumentException("Invalid kind of ore: " + ore);
        }
        if (amount < 0 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("Amount out of range: " + amount);
        }
        if (depth < MIN_DEPTH || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Depth out of range: " + depth);
        }
        if (autoDepth) {
            depth = 0;
        }
    }

    /**
     * Whether a text may name a kind of ore: empty for anything, or the id of a tag.
     */
    public static boolean isValidOre(String ore) {
        return ore != null && (ore.isEmpty() || ore.length() <= MAX_ORE_LENGTH && TAG_ID.matcher(ore).matches());
    }

    public boolean anything() {
        return ore.isEmpty();
    }

    /**
     * Whether the storehouses hold what was asked for.
     */
    public boolean metBy(long stock) {
        return !anything() && amount > 0 && stock >= amount;
    }

    /**
     * Whether another goal leads to the same mine: the same kind of ore at the same depth.
     */
    public boolean sameMine(MineGoal other) {
        return ore.equals(other.ore) && autoDepth == other.autoDepth && depth == other.depth;
    }

    public MineGoal withOre(String value) {
        return new MineGoal(value, amount, autoDepth, depth);
    }

    public MineGoal withAmount(int value) {
        return new MineGoal(ore, value, autoDepth, depth);
    }

    public MineGoal withAutoDepth() {
        return new MineGoal(ore, amount, true, 0);
    }

    public MineGoal withDepth(int value) {
        return new MineGoal(ore, amount, false, value);
    }
}
