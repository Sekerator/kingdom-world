package io.github.sekerator.kingdomworld.core.citizen;

import java.util.List;
import java.util.OptionalInt;

/**
 * Which of the tools in the storehouses a worker takes. A tool must suit the work (an axe for a lumberjack,
 * whatever mod made it); among those the worker takes the weakest one that is still strong enough for what the
 * zone holds, so that good tools are kept for work that needs them, and of equal ones the most worn, so that
 * tools are used up one after another.
 */
public final class ToolChoice {

    /**
     * A tool that suits the work.
     *
     * @param index          where the tool is, for the caller
     * @param tier           strength of the tool, higher is stronger (0 for tools without tiers)
     * @param durabilityLeft uses left before it breaks; {@link Integer#MAX_VALUE} for tools that do not wear
     */
    public record Candidate(int index, int tier, int durabilityLeft) {
    }

    private ToolChoice() {
    }

    /**
     * The tool to take, or empty if there is none.
     *
     * @param neededTier strength the work needs; with no tool that strong the strongest is taken
     */
    public static OptionalInt choose(List<Candidate> candidates, int neededTier) {
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (best == null || better(candidate, best, neededTier)) {
                best = candidate;
            }
        }
        return best == null ? OptionalInt.empty() : OptionalInt.of(best.index());
    }

    private static boolean better(Candidate a, Candidate b, int neededTier) {
        boolean aEnough = a.tier() >= neededTier;
        boolean bEnough = b.tier() >= neededTier;
        if (aEnough != bEnough) {
            return aEnough;
        }
        if (a.tier() != b.tier()) {
            // Strong enough: the weaker one; too weak: the stronger one.
            return aEnough ? a.tier() < b.tier() : a.tier() > b.tier();
        }
        return a.durabilityLeft() < b.durabilityLeft();
    }
}
