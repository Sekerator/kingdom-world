package io.github.sekerator.kingdomworld.core.road;

/**
 * How the townsfolk of the cities of a kingdom of players work and earn: on the posts of the crown, as hands in the workshops and
 * shops of citizens, and when they grow or leave; and whether the kingdoms of the world keep shops of citizens.
 *
 * @param atPosts              whether the townsfolk take the free seats of the posts of the crown
 * @param postRatePercent      how much of the work of a named worker one of the townsfolk does on a post, in percent
 * @param handPercent          how much of the work of a master (or the strength of a keeper) a hand of the townsfolk adds, in percent
 * @param usualIncome          what one of the townsfolk usually earns a day
 * @param idleMaxPercent       the townsfolk grow only while no more than this share of them is idle, in percent
 * @param leaveIdlePercent     beyond this share of idle townsfolk some of them leave, in percent
 * @param leavePercent         the share of the townsfolk that leaves on a bad day, in percent, at least one
 * @param wageReserveDays      the townsfolk take new seats only while the treasury holds this many days of all the wages of the kingdom
 *                             with those seats
 * @param releaseMasterPercent a workshop lets a hand go once its masters earn less than this share of their usual income, in percent
 * @param worldShops           whether the capitals of the kingdoms of the world keep shops of citizens
 */
public record FolkWorkRules(boolean atPosts, int postRatePercent, int handPercent, long usualIncome, int idleMaxPercent, int leaveIdlePercent,
                            int leavePercent, int wageReserveDays, int releaseMasterPercent, boolean worldShops) {
    public static final FolkWorkRules DEFAULT = new FolkWorkRules(true, 75, 40, 3, 10, 25, 3, 7, 75, true);

    public FolkWorkRules {
        if (postRatePercent < 0 || postRatePercent > 100 || handPercent < 0 || handPercent > 100 || usualIncome < 0 || idleMaxPercent < 0
                || idleMaxPercent > 100 || leaveIdlePercent < 0 || leaveIdlePercent > 100 || leavePercent < 0 || leavePercent > 100
                || wageReserveDays < 0 || releaseMasterPercent < 0 || releaseMasterPercent > 100) {
            throw new IllegalArgumentException("Rules of the work of the townsfolk out of range");
        }
    }

    /**
     * The share of a hand of the townsfolk in what a shop earns, in thousandths: the share of a keeper by the work it adds.
     */
    public int handShare(int keeperShare) {
        return (int) ((long) keeperShare * handPercent / 100);
    }

    public FolkWorkRules withAtPosts(boolean value) {
        return new FolkWorkRules(value, postRatePercent, handPercent, usualIncome, idleMaxPercent, leaveIdlePercent, leavePercent, wageReserveDays,
                releaseMasterPercent, worldShops);
    }

    public FolkWorkRules withWorldShops(boolean value) {
        return new FolkWorkRules(atPosts, postRatePercent, handPercent, usualIncome, idleMaxPercent, leaveIdlePercent, leavePercent, wageReserveDays,
                releaseMasterPercent, value);
    }
}
