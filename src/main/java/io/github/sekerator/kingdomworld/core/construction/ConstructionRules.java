package io.github.sekerator.kingdomworld.core.construction;

import java.util.Map;
import java.util.function.Predicate;

/**
 * The settings of construction and the formulas of the builders' work.
 *
 * @param maxConstructions constructions one kingdom may have at the same time
 * @param builderRadius    how far from its post a builder takes constructions, in blocks
 * @param reach            how far a builder places blocks from where it stands, in blocks
 * @param farReach         how far it places when it cannot walk closer, in blocks
 * @param giveUpTicks      how long a builder tries to reach a place before it goes on with another block
 * @param ticksPerBlock    ticks a beginner needs for one block
 * @param fallChance       chance to fall when placing a block high above the ground near players
 * @param maxWidth         largest template along x, in blocks
 * @param maxHeight        largest template along y
 * @param maxLength        largest template along z
 */
public record ConstructionRules(int maxConstructions, int builderRadius, double reach, double farReach, int giveUpTicks, int ticksPerBlock,
                                double fallChance, int maxWidth, int maxHeight, int maxLength) {
    /** Experience at which a builder works twice as fast. */
    public static final int VETERAN_EXPERIENCE = 1_000;
    /** Height above its feet from which placing a block counts as work at height. */
    public static final int HEIGHT = 4;

    public static final ConstructionRules DEFAULT = new ConstructionRules(4, 64, 4.5, 12.0, 300, 40, 0.002, 48, 32, 48);

    public ConstructionRules {
        if (maxConstructions < 1 || builderRadius < 1 || !(reach >= 1.0) || !(farReach >= reach) || giveUpTicks < 20 || ticksPerBlock < 1
                || !(fallChance >= 0.0 && fallChance <= 1.0) || maxWidth < 1 || maxHeight < 1 || maxLength < 1) {
            throw new IllegalArgumentException("Construction rules out of range");
        }
    }

    /**
     * Ticks a builder with this experience needs for one block: up to twice as fast with experience.
     */
    public double ticksPerBlock(int experience) {
        double skill = Math.max(0.0, Math.min(1.0, experience / (double) VETERAN_EXPERIENCE));
        return ticksPerBlock / (1.0 + skill);
    }

    /**
     * Blocks a builder with this experience places far from players in a number of ticks.
     *
     * @param factor other effects on the pace (mood)
     */
    public double blocks(int ticks, int experience, double factor) {
        return Math.max(0, ticks) / ticksPerBlock(experience) * Math.max(0.0, factor);
    }

    /**
     * Whether a template of this size may be built.
     */
    public boolean fits(int sizeX, int sizeY, int sizeZ) {
        return sizeX <= maxWidth && sizeY <= maxHeight && sizeZ <= maxLength;
    }

    /**
     * What the work far from players did in one step.
     *
     * @param steps   steps done or paid for
     * @param starved whether the work stopped because materials were missing
     * @param carry   work left over for the next step, in blocks
     */
    public record Progress(int steps, boolean starved, double carry) {
    }

    /**
     * Work far from players: as many steps of the plan as the time allows, in order. Clearing costs nothing; every block is
     * paid for in one go when its turn comes (all its items or none), and then waits until its place is loaded. The work
     * stops at the first block whose items are missing.
     *
     * @param budget blocks of work available, with the carry of the last step
     * @param pay    takes the items of one block, all or none
     */
    public static Progress work(BuildPlan plan, Construction construction, double budget, Predicate<Map<String, Integer>> pay) {
        int done = 0;
        double left = budget;
        while (left >= 1.0) {
            java.util.OptionalInt next = plan.next(construction, step -> false);
            if (next.isEmpty()) {
                return new Progress(done, false, 0.0);
            }
            BuildPlan.Step step = plan.step(next.getAsInt());
            if (step.kind() == BuildPlan.Kind.PLACE && !step.cost().isEmpty() && !pay.test(step.cost())) {
                return new Progress(done, true, 0.0);
            }
            construction.markPending(step.index());
            done++;
            left -= 1.0;
        }
        return new Progress(done, false, Math.max(0.0, left));
    }
}
