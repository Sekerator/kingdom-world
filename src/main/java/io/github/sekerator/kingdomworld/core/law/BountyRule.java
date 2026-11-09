package io.github.sekerator.kingdomworld.core.law;

/**
 * What the head of an outlaw pays a hunter. The first heads of a player pay the full reward; from the third head one hunter
 * collects of the same player, the reward is the least of the full reward, the ransom of the goods taken from the player and
 * the coins taken from the player, so that a kingdom never pays more than it gained.
 */
public final class BountyRule {
    /** Heads of one player one hunter collects for the full reward. */
    public static final int FULL_HEADS = 2;

    private BountyRule() {
    }

    /**
     * @param reward   the reward of the contract
     * @param before   heads of this player the hunter collected before
     * @param ransom   the ransom of the goods taken from the player this time
     * @param coins    the coins taken from the player this time
     */
    public static long reward(long reward, int before, long ransom, long coins) {
        long full = Math.max(0, reward);
        if (before < FULL_HEADS) {
            return full;
        }
        return Math.min(full, Math.min(Math.max(0, ransom), Math.max(0, coins)));
    }

    /**
     * Whether a head pays less than the full reward.
     */
    public static boolean reduced(int before) {
        return before >= FULL_HEADS;
    }
}
