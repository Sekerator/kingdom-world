package io.github.sekerator.kingdomworld.core.npc;

/**
 * The rules of the kingdoms of the world. Values by level are given for small, medium and large kingdoms in this order.
 *
 * @param generate            whether kingdoms of the world are planned and appear at all
 * @param count               how many kingdoms a world plans
 * @param radius              how far from the spawn of the world their capitals may lie, in blocks
 * @param minDistance         the least distance between two capitals, in blocks
 * @param minSpawnDistance    the least distance of a capital from the spawn of the world, in blocks
 * @param triggerChunks       a kingdom appears once a player comes this close to its place, in chunks
 * @param sizeWeights         how often each level is chosen
 * @param landRadius          the land around the capital, in chunks from the chunk of the throne
 * @param towns               towns around the capital
 * @param townDistance        how far towns lie from the capital, in blocks
 * @param money               the money of the treasury when the kingdom appears
 * @param foodDays            the food in the storehouses when it appears, in days of food for every citizen
 * @param materials           stacks of each building material in the storehouses when it appears
 * @param soldiers            soldiers of the kingdom when it appears
 * @param blocksPerTick       blocks the appearance places or levels in a tick at most
 * @param chunksPerTick       chunks the appearance looks through in a tick at most
 * @param maxInhabitedMinutes a place where players spent more time than this (the game counts it for every chunk) is
 *                            left alone, so that a kingdom never appears over what players built
 * @param attemptsPerSite     candidate places the plan looks at for every kingdom at most
 */
public record NpcRules(boolean generate, int count, int radius, int minDistance, int minSpawnDistance, int triggerChunks,
                       int[] sizeWeights, int[] landRadius, int[] towns, int townDistance, long[] money, int foodDays,
                       int[] materials, int[] soldiers, int blocksPerTick, int chunksPerTick, int maxInhabitedMinutes,
                       int attemptsPerSite) {
    public static final NpcRules DEFAULT = new NpcRules(true, 8, 6000, 1200, 1000, 12, new int[]{5, 3, 2}, new int[]{3, 4, 5},
            new int[]{1, 2, 3}, 200, new long[]{1500, 4000, 10000}, 10, new int[]{4, 8, 16}, new int[]{2, 4, 8}, 4000, 4, 20, 60);

    public NpcRules {
        sizeWeights = three(sizeWeights, 0, 1000);
        landRadius = three(landRadius, 1, 8);
        towns = three(towns, 0, 4);
        materials = three(materials, 0, 64);
        soldiers = three(soldiers, 0, 32);
        if (money == null || money.length != 3) {
            throw new IllegalArgumentException("Three amounts of money are needed");
        }
        money = money.clone();
        for (long value : money) {
            if (value < 0 || value > 1_000_000_000L) {
                throw new IllegalArgumentException("Money out of range: " + value);
            }
        }
        if (count < 0 || radius < 256 || minDistance < 256 || minSpawnDistance < 0 || triggerChunks < 2 || townDistance < 64
                || foodDays < 0 || blocksPerTick < 64 || chunksPerTick < 1 || maxInhabitedMinutes < 0 || attemptsPerSite < 1) {
            throw new IllegalArgumentException("Rules of the kingdoms of the world out of range");
        }
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

    public int landRadius(NpcLevel level) {
        return landRadius[level.ordinal()];
    }

    public int towns(NpcLevel level) {
        return towns[level.ordinal()];
    }

    public long money(NpcLevel level) {
        return money[level.ordinal()];
    }

    public int materials(NpcLevel level) {
        return materials[level.ordinal()];
    }

    public int soldiers(NpcLevel level) {
        return soldiers[level.ordinal()];
    }

    @Override
    public int[] sizeWeights() {
        return sizeWeights.clone();
    }

    /**
     * The distance at which a player makes the kingdom appear, in blocks.
     */
    public int triggerBlocks() {
        return triggerChunks * 16;
    }
}
