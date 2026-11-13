package io.github.sekerator.kingdomworld.core.citizen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Server settings of mines: how far the tunnels reach, how much of a vein a miner follows, how long it stays on a
 * level without ore, and the depth each kind of ore is looked for at.
 *
 * @param corridorLength blocks of the main corridor to each side of the staircase
 * @param branchLength   blocks of a branch to each side of the corridor
 * @param veinBlocks     most blocks of ore a miner takes from one vein beside a tunnel; 0 leaves veins alone
 * @param barrenBlocks   blocks a miner opens on one level without meeting ore before it goes on to the next level;
 *                       0 makes it finish every level
 * @param oreDepths      the height a kind of ore is mined at when the depth is chosen automatically, by tag id
 */
public record MineRules(int corridorLength, int branchLength, int veinBlocks, int barrenBlocks,
                        Map<String, Integer> oreDepths) {
    /** The height of a kind of ore the settings do not name. */
    public static final int DEFAULT_ORE_DEPTH = 16;
    /** The height anything is first dug at when the depth is chosen automatically. */
    public static final int ANYTHING_DEPTH = 16;

    private static final List<String> DEFAULT_DEPTHS = List.of("forge:ores/coal=96", "forge:ores/copper=48", "forge:ores/iron=16",
            "forge:ores/lapis=0", "forge:ores/gold=-16", "forge:ores/redstone=-58", "forge:ores/diamond=-58", "forge:ores/emerald=232");

    public static final MineRules DEFAULT = new MineRules(48, 16, 24, 200, parseDepths(DEFAULT_DEPTHS));

    public MineRules {
        if (corridorLength < 1 || branchLength < 1 || veinBlocks < 0 || barrenBlocks < 0) {
            throw new IllegalArgumentException("Mine rules out of range");
        }
        oreDepths = Map.copyOf(Objects.requireNonNull(oreDepths, "oreDepths"));
    }

    /**
     * The default depths as the lines of the config.
     */
    public static List<String> defaultDepths() {
        return DEFAULT_DEPTHS;
    }

    /**
     * Reads one line of the depths, {@code tag=y}; empty if it is not one.
     */
    public static Optional<Map.Entry<String, Integer>> parseDepth(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int split = text.lastIndexOf('=');
        if (split <= 0) {
            return Optional.empty();
        }
        String ore = text.substring(0, split).trim();
        if (ore.isEmpty() || !MineGoal.isValidOre(ore)) {
            return Optional.empty();
        }
        try {
            int y = Integer.parseInt(text.substring(split + 1).trim());
            if (y < MineGoal.MIN_DEPTH || y > MineGoal.MAX_DEPTH) {
                return Optional.empty();
            }
            return Optional.of(Map.entry(ore, y));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads the lines of the depths; lines that are not one are skipped, a kind named twice takes the last line.
     */
    public static Map<String, Integer> parseDepths(List<? extends String> lines) {
        Map<String, Integer> depths = new LinkedHashMap<>();
        for (String line : lines) {
            parseDepth(line).ifPresent(entry -> depths.put(entry.getKey(), entry.getValue()));
        }
        return depths;
    }

    /**
     * The height a goal asks for before the world limits it: the one the player set, or the one of the kind of ore;
     * anything at an automatic depth starts at {@link #ANYTHING_DEPTH}, or on the level of a post that stands lower.
     */
    public int wantedDepth(MineGoal goal, int postY) {
        if (!goal.autoDepth()) {
            return goal.depth();
        }
        return goal.anything() ? Math.min(postY, ANYTHING_DEPTH) : oreDepths.getOrDefault(goal.ore(), DEFAULT_ORE_DEPTH);
    }

    /**
     * The floor of the first level of the mine of a goal.
     *
     * @param worldBottom the lowest block of the world
     */
    public int depth(MineGoal goal, int postY, int worldBottom) {
        return MinePlan.clampDepth(wantedDepth(goal, postY), postY, worldBottom);
    }

    /**
     * Blocks between two levels of the mine of a goal: anything at an automatic depth goes clearly deeper with every
     * level, down to the layers of diamonds; a kind of ore and a depth the player chose keep to neighbouring layers.
     */
    public static int levelStep(MineGoal goal) {
        return goal.anything() && goal.autoDepth() ? GatheringRules.MINE_DEEP_STEP : GatheringRules.MINE_LEVEL_STEP;
    }
}
