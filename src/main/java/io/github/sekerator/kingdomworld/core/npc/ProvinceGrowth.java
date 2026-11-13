package io.github.sekerator.kingdomworld.core.npc;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * How a state of the world grows: instead of claiming chunk after chunk, its ruler takes one wild province next to its land
 * now and then, while its land is a little over what the plan gave it. Not near the spawn, not where a player claimed land
 * or near it, not where players lived, and not when too little of the land around would stay neutral.
 */
public final class ProvinceGrowth {
    /** Why a wild province is not taken. */
    public enum Refusal {
        NOT_WILD, SPAWN, PLAYER_NEAR, INHABITED, TOO_LITTLE_NEUTRAL
    }

    /**
     * The numbers of the growth.
     *
     * @param intervalDays     days between two provinces taken
     * @param maxLandShare     the land a state grows to, as a share of the land the plan gave it
     * @param playerChunks     chunks from a claim of a player within which a province is not taken
     * @param minNeutralShare  the share of the land of the provinces that stays neutral at least
     */
    public record Rules(int intervalDays, double maxLandShare, int playerChunks, double minNeutralShare) {
        public static final Rules DEFAULT = new Rules(12, 1.3, 4, 0.35);
    }

    /**
     * A wild province next to the land of a state, as the server found it.
     *
     * @param wild           whether it is wild and nobody's
     * @param inSpawn        whether it lies in the zone around the spawn
     * @param playerNear     whether a claim of a player lies in it or within {@link Rules#playerChunks()} of it
     * @param inhabited      whether players spent long in a chunk of it
     * @param neutralAfter   the share of the land of the provinces that stays neutral once it is taken
     * @param distance       how far it lies from the capital, in blocks
     */
    public record Candidate(int province, int chunks, boolean wild, boolean inSpawn, boolean playerNear, boolean inhabited,
                            double neutralAfter, long distance) {
    }

    private ProvinceGrowth() {
    }

    /**
     * Whether a state takes a province today: the days since the last one are over and its land is under its share of the
     * plan. A state the plan gave nothing never grows so.
     */
    public static boolean due(long day, long lastGrowthDay, long land, long plannedChunks, Rules rules) {
        if (plannedChunks <= 0) {
            return false;
        }
        return day - lastGrowthDay >= rules.intervalDays() && land < plannedChunks * rules.maxLandShare();
    }

    /**
     * Why a province is not taken, or nothing if it may be; the players' lives in it are looked at last, being the dearest
     * to find out.
     */
    public static Optional<Refusal> refusal(Candidate candidate, Rules rules) {
        if (!candidate.wild()) {
            return Optional.of(Refusal.NOT_WILD);
        }
        if (candidate.inSpawn()) {
            return Optional.of(Refusal.SPAWN);
        }
        if (candidate.playerNear()) {
            return Optional.of(Refusal.PLAYER_NEAR);
        }
        if (candidate.neutralAfter() < rules.minNeutralShare()) {
            return Optional.of(Refusal.TOO_LITTLE_NEUTRAL);
        }
        if (candidate.inhabited()) {
            return Optional.of(Refusal.INHABITED);
        }
        return Optional.empty();
    }

    /**
     * The order the provinces are looked at in: the nearest to the capital first, the smaller of two as far.
     */
    public static List<Candidate> ordered(List<Candidate> candidates) {
        return candidates.stream().sorted(Comparator.comparingLong(Candidate::distance).thenComparingInt(Candidate::chunks)
                .thenComparingInt(Candidate::province)).toList();
    }

    /**
     * The share of the land of the provinces that stays neutral once a province of so many chunks is taken.
     *
     * @param neutralChunks the chunks of the provinces nobody holds
     * @param allChunks     the chunks of all provinces
     */
    public static double neutralAfter(long neutralChunks, long allChunks, int taken) {
        if (allChunks <= 0) {
            return 0.0;
        }
        return Math.max(0, neutralChunks - taken) / (double) allChunks;
    }
}
