package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.npc.NpcLevel;

/**
 * What the plan of the states of a world asks. Values by level are given for small, medium and large states in this order.
 *
 * @param spawnX       the spawn of the world, in blocks
 * @param spawnZ       the spawn of the world, in blocks
 * @param spawnZone    no state comes this close to the spawn, in blocks
 * @param radius       the seeds of the states lie within this distance of the spawn, in blocks; their land may reach a little
 *                     beyond it
 * @param gapBlocks    the least neutral land between two states, in blocks, after their borders are drawn chunk by chunk
 * @param neutralShare the share of the land of the world (beyond the spawn zone) that stays neutral at least
 * @param ringShare    the share of the ring between the spawn zone and the radius the states take at most
 * @param quota        how many states of each level the plan makes; the largest are seeded first
 * @param minChunks    the least land of a state of each level, in chunks
 * @param maxChunks    the largest land of a state of each level, in chunks; the seed of the world picks between the two
 */
public record StateRules(int spawnX, int spawnZ, int spawnZone, int radius, int gapBlocks, double neutralShare, double ringShare,
                         int[] quota, int[] minChunks, int[] maxChunks) {
    public static final int SPAWN_ZONE = 1500;
    public static final int RADIUS = 7000;
    /**
     * The largest radius the plan of states of a world takes from the config, in blocks: the survey and its file grow with the
     * square of the radius, and a few of its steps (the end of the survey, the copy for the worker, the writing of the file) go
     * over every cell in one tick. At this radius the survey has about 415 000 cells.
     */
    public static final int MAX_RADIUS = 20_000;
    public static final int GAP_BLOCKS = 384;
    public static final double NEUTRAL_SHARE = 0.35;
    public static final double RING_SHARE = 0.40;
    public static final int[] QUOTA = {5, 0, 3};
    public static final int[] MIN_CHUNKS = {9_000, 16_000, 28_000};
    public static final int[] MAX_CHUNKS = {13_000, 22_000, 36_000};
    /** The most states a plan makes. */
    public static final int MAX_STATES = 64;

    public StateRules {
        quota = three(quota, 0, MAX_STATES);
        minChunks = three(minChunks, 16, 1 << 20);
        maxChunks = three(maxChunks, 16, 1 << 20);
        int states = 0;
        for (int i = 0; i < 3; i++) {
            maxChunks[i] = Math.max(maxChunks[i], minChunks[i]);
            states += quota[i];
        }
        if (states > MAX_STATES || spawnZone < 0 || radius < 256 || gapBlocks < 0 || !(neutralShare >= 0 && neutralShare < 1)
                || !(ringShare > 0 && ringShare <= 1)) {
            throw new IllegalArgumentException("Rules of the plan of states out of range");
        }
    }

    /**
     * The radius a plan of states of a world takes for the radius of the config: no larger than {@value #MAX_RADIUS} blocks and
     * no smaller than 256.
     */
    public static int planRadius(int configured) {
        return Math.max(256, Math.min(MAX_RADIUS, configured));
    }

    /**
     * The rules of the plan around a spawn, with the defaults.
     */
    public static StateRules defaults(int spawnX, int spawnZ) {
        return new StateRules(spawnX, spawnZ, SPAWN_ZONE, RADIUS, GAP_BLOCKS, NEUTRAL_SHARE, RING_SHARE, QUOTA, MIN_CHUNKS, MAX_CHUNKS);
    }

    /**
     * The same rules with another quota.
     */
    public StateRules withQuota(int small, int medium, int large) {
        return new StateRules(spawnX, spawnZ, spawnZone, radius, gapBlocks, neutralShare, ringShare, new int[]{small, medium, large},
                minChunks, maxChunks);
    }

    /**
     * The same rules with another spawn zone and radius.
     */
    public StateRules withRing(int spawnZone, int radius) {
        return new StateRules(spawnX, spawnZ, spawnZone, radius, gapBlocks, neutralShare, ringShare, quota, minChunks, maxChunks);
    }

    /**
     * The same rules with other sizes of states, in chunks.
     */
    public StateRules withSizes(int[] minChunks, int[] maxChunks) {
        return new StateRules(spawnX, spawnZ, spawnZone, radius, gapBlocks, neutralShare, ringShare, quota, minChunks, maxChunks);
    }

    public int quota(NpcLevel level) {
        return quota[level.ordinal()];
    }

    public int minChunks(NpcLevel level) {
        return minChunks[level.ordinal()];
    }

    public int maxChunks(NpcLevel level) {
        return maxChunks[level.ordinal()];
    }

    @Override
    public int[] quota() {
        return quota.clone();
    }

    @Override
    public int[] minChunks() {
        return minChunks.clone();
    }

    @Override
    public int[] maxChunks() {
        return maxChunks.clone();
    }

    private static int[] three(int[] values, int min, int max) {
        if (values == null || values.length != 3) {
            throw new IllegalArgumentException("Three values are needed");
        }
        for (int value : values) {
            if (value < min || value > max) {
                throw new IllegalArgumentException("Value out of range: " + value);
            }
        }
        return values.clone();
    }
}
