package io.github.sekerator.kingdomworld.core.citizen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Server settings of mines: how far the tunnels reach, how much of a vein a miner follows, and the depth each kind
 * of ore is looked for at.
 *
 * @param corridorLength blocks of the main corridor to each side of the staircase
 * @param branchLength   blocks of a branch to each side of the corridor
 * @param veinBlocks     most blocks of ore a miner takes from one vein beside a tunnel; 0 leaves veins alone
 * @param oreDepths      the height a kind of ore is mined at when the depth is chosen automatically, by tag id
 */
public record MineRules(int corridorLength, int branchLength, int veinBlocks, Map<String, Integer> oreDepths) {
    /** The height of a kind of ore the settings do not name. */
    public static final int DEFAULT_ORE_DEPTH = 16;

    private static final List<String> DEFAULT_DEPTHS = List.of("forge:ores/coal=96", "forge:ores/copper=48", "forge:ores/iron=16",
            "forge:ores/lapis=0", "forge:ores/gold=-16", "forge:ores/redstone=-58", "forge:ores/diamond=-58", "forge:ores/emerald=232");

    public static final MineRules DEFAULT = new MineRules(48, 16, 24, parseDepths(DEFAULT_DEPTHS));

    public MineRules {
        if (corridorLength < 1 || branchLength < 1 || veinBlocks < 0) {
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
     * anything at an automatic depth is dug on the level of the post.
     */
    public int wantedDepth(MineGoal goal, int postY) {
        if (!goal.autoDepth()) {
            return goal.depth();
        }
        return goal.anything() ? postY : oreDepths.getOrDefault(goal.ore(), DEFAULT_ORE_DEPTH);
    }

    /**
     * The floor of the first level of the mine of a goal.
     *
     * @param worldBottom the lowest block of the world
     */
    public int depth(MineGoal goal, int postY, int worldBottom) {
        return MinePlan.clampDepth(wantedDepth(goal, postY), postY, worldBottom);
    }
}
