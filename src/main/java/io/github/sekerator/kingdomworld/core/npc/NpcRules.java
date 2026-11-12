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
 * @param sizeQuota           how many kingdoms of each level the plan makes first; the largest are looked for first
 * @param sizeWeights         how often each level is chosen for the kingdoms beyond the quota
 * @param landRadius          the land around the capital, in chunks from the chunk of the throne
 * @param towns               towns around the capital
 * @param townDistance        how far towns lie from the capital, in blocks
 * @param money               the money of the treasury when the kingdom appears
 * @param foodDays            the food in the storehouses when it appears, in days of food for every citizen
 * @param materials           stacks of each building material in the storehouses when it appears
 * @param soldiers            soldiers of a kingdom of the first layout when it appears
 * @param blocksPerTick       blocks the appearance places or levels in a tick at most
 * @param chunksPerTick       chunks the appearance looks through in a tick at most
 * @param maxInhabitedMinutes a place where players spent more time than this (the game counts it for every chunk) is
 *                            left alone, so that a kingdom never appears over what players built
 * @param attemptsPerSite     candidate places the plan looks at for every kingdom at most
 * @param guards              soldiers of the city guard of the capital, officers included, by size
 * @param army                soldiers of the royal army who live in the capital (its reserve at the barracks and the palace),
 *                            officers included, by size
 * @param townGuards          soldiers of the guard every town of the kingdom has besides, by size
 * @param armyLeast           the least royal army of a kingdom, by size: a merchant keeps about that many
 * @param armyMost            the largest royal army of a kingdom, by size: a warrior keeps about that many
 * @param armyPerDay          recruits a royal army that lacks soldiers takes in a day, from abroad
 * @param campSoldiers        soldiers one camp of the army holds; the army beyond the capital lives in camps
 * @param campBodies          soldiers of one camp (or of the army of a kingdom away from its camps) who have a body near players
 *                            at most
 * @param armyBodies          soldiers of the royal armies of all kingdoms of the world who have a body at once at most; the
 *                            other citizens and the guard come first
 */
public record NpcRules(boolean generate, int count, int radius, int minDistance, int minSpawnDistance, int triggerChunks,
                       int[] sizeQuota, int[] sizeWeights, int[] landRadius, int[] towns, int townDistance, long[] money, int foodDays,
                       int[] materials, int[] soldiers, int blocksPerTick, int chunksPerTick, int maxInhabitedMinutes,
                       int attemptsPerSite, int[] guards, int[] army, int[] townGuards, int[] armyLeast, int[] armyMost, int armyPerDay,
                       int campSoldiers, int campBodies, int armyBodies) {
    /** The garrison of a kingdom by size: the guard of its capital, its army in the capital, and the guards of each town. */
    public static final int[] GUARDS = {10, 18, 30};
    public static final int[] ARMY = {6, 10, 20};
    public static final int[] TOWN_GUARDS = {2, 2, 4};
    /** The whole royal army by size: from the least (a merchant) to the largest (a warrior). */
    public static final int[] ARMY_LEAST = {50, 80, 100};
    public static final int[] ARMY_MOST = {100, 150, 200};
    public static final int ARMY_PER_DAY = 5;
    public static final int CAMP_SOLDIERS = 100;
    public static final int CAMP_BODIES = 30;
    public static final int ARMY_BODIES = 90;
    /** The most soldiers of one branch the rules take. */
    public static final int MAX_GARRISON = 256;
    /** The largest royal army the rules take. */
    public static final int MAX_ARMY = 400;
    /** The widest land of a capital the rules take, in chunks from the chunk of the throne. */
    public static final int MAX_LAND_RADIUS = 12;
    /** The most towns a kingdom is planned with. */
    public static final int MAX_TOWNS = 6;

    public static final NpcRules DEFAULT = new NpcRules(true, 8, 6000, 1200, 1000, 12, new int[]{5, 0, 3}, new int[]{5, 3, 2},
            new int[]{4, 6, 7}, new int[]{2, 3, 4}, 260, new long[]{6000, 15000, 40000}, 10, new int[]{4, 10, 16}, new int[]{2, 4, 8},
            2000, 4, 20, 60);

    /**
     * Rules with the garrisons of the defaults.
     */
    public NpcRules(boolean generate, int count, int radius, int minDistance, int minSpawnDistance, int triggerChunks, int[] sizeQuota,
                    int[] sizeWeights, int[] landRadius, int[] towns, int townDistance, long[] money, int foodDays, int[] materials,
                    int[] soldiers, int blocksPerTick, int chunksPerTick, int maxInhabitedMinutes, int attemptsPerSite) {
        this(generate, count, radius, minDistance, minSpawnDistance, triggerChunks, sizeQuota, sizeWeights, landRadius, towns, townDistance,
                money, foodDays, materials, soldiers, blocksPerTick, chunksPerTick, maxInhabitedMinutes, attemptsPerSite, GUARDS, ARMY,
                TOWN_GUARDS);
    }

    /**
     * Rules with the royal armies of the defaults.
     */
    public NpcRules(boolean generate, int count, int radius, int minDistance, int minSpawnDistance, int triggerChunks, int[] sizeQuota,
                    int[] sizeWeights, int[] landRadius, int[] towns, int townDistance, long[] money, int foodDays, int[] materials,
                    int[] soldiers, int blocksPerTick, int chunksPerTick, int maxInhabitedMinutes, int attemptsPerSite, int[] guards,
                    int[] army, int[] townGuards) {
        this(generate, count, radius, minDistance, minSpawnDistance, triggerChunks, sizeQuota, sizeWeights, landRadius, towns, townDistance,
                money, foodDays, materials, soldiers, blocksPerTick, chunksPerTick, maxInhabitedMinutes, attemptsPerSite, guards, army,
                townGuards, ARMY_LEAST, ARMY_MOST, ARMY_PER_DAY, CAMP_SOLDIERS, CAMP_BODIES, ARMY_BODIES);
    }

    public NpcRules {
        guards = three(guards, 0, MAX_GARRISON);
        army = three(army, 0, MAX_GARRISON);
        townGuards = three(townGuards, 0, 16);
        armyLeast = three(armyLeast, 0, MAX_ARMY);
        armyMost = three(armyMost, 0, MAX_ARMY);
        for (int i = 0; i < 3; i++) {
            // The largest army is never smaller than the least, nor the army in the capital larger than the whole.
            armyMost[i] = Math.max(armyMost[i], armyLeast[i]);
        }
        sizeQuota = three(sizeQuota, 0, 256);
        sizeWeights = three(sizeWeights, 0, 1000);
        landRadius = three(landRadius, 1, MAX_LAND_RADIUS);
        towns = three(towns, 0, MAX_TOWNS);
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
                || foodDays < 0 || blocksPerTick < 64 || chunksPerTick < 1 || maxInhabitedMinutes < 0 || attemptsPerSite < 1
                || armyPerDay < 0 || armyPerDay > 64 || campSoldiers < 10 || campSoldiers > MAX_ARMY || campBodies < 0 || campBodies > 256
                || armyBodies < 0 || armyBodies > 1024) {
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

    /** Soldiers of the guard of the capital, officers included. */
    public int guards(NpcLevel level) {
        return guards[level.ordinal()];
    }

    /** Soldiers of the royal army who live in the capital, officers included. */
    public int army(NpcLevel level) {
        return army[level.ordinal()];
    }

    /** The least royal army of a kingdom of a size. */
    public int armyLeast(NpcLevel level) {
        return armyLeast[level.ordinal()];
    }

    /** The largest royal army of a kingdom of a size. */
    public int armyMost(NpcLevel level) {
        return armyMost[level.ordinal()];
    }

    /** Soldiers of the guard of every town. */
    public int townGuards(NpcLevel level) {
        return townGuards[level.ordinal()];
    }

    @Override
    public int[] guards() {
        return guards.clone();
    }

    @Override
    public int[] army() {
        return army.clone();
    }

    @Override
    public int[] townGuards() {
        return townGuards.clone();
    }

    @Override
    public int[] armyLeast() {
        return armyLeast.clone();
    }

    @Override
    public int[] armyMost() {
        return armyMost.clone();
    }

    @Override
    public int[] sizeWeights() {
        return sizeWeights.clone();
    }

    @Override
    public int[] sizeQuota() {
        return sizeQuota.clone();
    }

    /**
     * How many kingdoms of a level the plan makes before it draws levels by their weights.
     */
    public int quota(NpcLevel level) {
        return sizeQuota[level.ordinal()];
    }

    /**
     * The distance at which a player makes the kingdom appear, in blocks.
     */
    public int triggerBlocks() {
        return triggerChunks * 16;
    }
}
