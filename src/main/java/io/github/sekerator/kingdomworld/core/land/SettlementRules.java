package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.npc.NpcLevel;

/**
 * How many settlements a state has and where they may stand. Counts are ranges for small, medium and large states in this
 * order; the seed of the world picks within them.
 *
 * @param cities       large cities by the size of the state, least and most
 * @param towns        small towns
 * @param villages     villages and hamlets together
 * @param villageShare the share of villages among villages and hamlets
 * @param distances    the least distance of a settlement of each rank from any other, in blocks: two settlements keep the
 *                     distance of the lower of them
 * @param childMin     a village or a hamlet stands at least this far from the settlement it belongs to, in blocks
 * @param childMax     and at most this far
 * @param childrenMin  villages and hamlets around one settlement, before any gets more
 * @param childrenMax  villages and hamlets around one settlement at most
 * @param alternates   cells offered besides the place of a settlement, for when the world turns the place down
 */
public record SettlementRules(int[][] cities, int[][] towns, int[][] villages, double villageShare, int[] distances, int childMin,
                              int childMax, int childrenMin, int childrenMax, int alternates) {
    /** The words of the owner: a large state has 4-8 large cities, 10-15 small towns, 30-40 villages and hamlets. */
    public static final SettlementRules DEFAULT = new SettlementRules(new int[][]{{1, 2}, {2, 4}, {4, 8}},
            new int[][]{{3, 6}, {6, 10}, {10, 15}}, new int[][]{{8, 15}, {15, 25}, {30, 40}}, 0.6, new int[]{600, 600, 350, 200, 150},
            300, 700, 2, 5, 3);

    public SettlementRules {
        cities = ranges(cities);
        towns = ranges(towns);
        villages = ranges(villages);
        if (distances == null || distances.length != Tier.values().length) {
            throw new IllegalArgumentException("A distance for every rank");
        }
        distances = distances.clone();
        for (int distance : distances) {
            if (distance < 0 || distance > 10_000) {
                throw new IllegalArgumentException("Distance out of range: " + distance);
            }
        }
        if (!(villageShare >= 0 && villageShare <= 1) || childMin < 0 || childMax < childMin || childMax > 4096 || childrenMin < 0
                || childrenMax < Math.max(1, childrenMin) || alternates < 0 || alternates > 24) {
            throw new IllegalArgumentException("Rules of settlements out of range");
        }
    }

    /**
     * The least distance of two settlements, in blocks: the distance of the lower of them.
     */
    public int apart(Tier a, Tier b) {
        return Math.min(distances[a.ordinal()], distances[b.ordinal()]);
    }

    public int distance(Tier tier) {
        return distances[tier.ordinal()];
    }

    public int[] cities(NpcLevel level) {
        return cities[level.ordinal()].clone();
    }

    public int[] towns(NpcLevel level) {
        return towns[level.ordinal()].clone();
    }

    public int[] villages(NpcLevel level) {
        return villages[level.ordinal()].clone();
    }

    @Override
    public int[][] cities() {
        return copy(cities);
    }

    @Override
    public int[][] towns() {
        return copy(towns);
    }

    @Override
    public int[][] villages() {
        return copy(villages);
    }

    @Override
    public int[] distances() {
        return distances.clone();
    }

    private static int[][] ranges(int[][] values) {
        if (values == null || values.length != 3) {
            throw new IllegalArgumentException("A range for every size");
        }
        int[][] copy = new int[3][];
        for (int i = 0; i < 3; i++) {
            if (values[i] == null || values[i].length != 2 || values[i][0] < 0 || values[i][1] < values[i][0] || values[i][1] > 256) {
                throw new IllegalArgumentException("A range is a least and a most count");
            }
            copy[i] = values[i].clone();
        }
        return copy;
    }

    private static int[][] copy(int[][] values) {
        int[][] copy = new int[values.length][];
        for (int i = 0; i < values.length; i++) {
            copy[i] = values[i].clone();
        }
        return copy;
    }
}
