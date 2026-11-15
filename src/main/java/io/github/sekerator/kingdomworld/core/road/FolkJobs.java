package io.github.sekerator.kingdomworld.core.road;

import java.util.Set;

/**
 * Where the townsfolk of a city work: on the free seats of the posts of the crown in its square first, then as hands in the
 * workshops and shops of citizens; the rest are idle. Named citizens always come first: a named worker hired on a post takes the
 * seat of one of the townsfolk, who is idle again. Pure functions, so that the server and the tests share them.
 */
public final class FolkJobs {
    /**
     * The posts the townsfolk take: the gatherers, the workshops and the carriers. Never the merchants of the crown, its builders,
     * the mint, mages, innkeepers, registrars or soldiers.
     */
    public static final Set<String> POSTS = Set.of("farmer", "fisher", "herder", "lumberjack", "miner", "smelter", "cook", "crafter", "smith",
            "carrier");

    private FolkJobs() {
    }

    /**
     * Whether the townsfolk take the seats of a post of a profession.
     */
    public static boolean takes(String profession) {
        return POSTS.contains(profession);
    }

    /**
     * How many of the idle townsfolk each post takes, in order: its free seats, while the treasury holds the days of all the wages
     * of the kingdom the rules ask with those seats. The townsfolk raise the wages of the crown by themselves: the seats wait
     * rather than ruin the king.
     *
     * @param idle        the idle townsfolk of the city
     * @param free        the seats of each post no named worker and none of the townsfolk took
     * @param wages       the wage of the profession of each post
     * @param funds       the money of the treasury of the kingdom
     * @param dailyWages  all the wages of the kingdom a day, of the named citizens and of the townsfolk on posts
     * @param reserveDays the days of wages the treasury must hold
     * @return how many each post takes
     */
    public static int[] fill(int idle, int[] free, long[] wages, long funds, long dailyWages, int reserveDays) {
        int[] taken = new int[free.length];
        int left = Math.max(0, idle);
        long wagesNow = Math.max(0, dailyWages);
        long days = Math.max(0, reserveDays);
        for (int i = 0; i < free.length && left > 0; i++) {
            int seats = Math.min(left, Math.max(0, free[i]));
            long wage = Math.max(0, wages[i]);
            if (seats <= 0) {
                continue;
            }
            if (wage > 0 && days > 0) {
                // The seats the treasury still holds the days of wages for: funds >= days * (wages + n * wage).
                long room = funds / days - wagesNow;
                seats = (int) Math.max(0, Math.min(seats, room / wage));
            }
            taken[i] = seats;
            left -= seats;
            wagesNow += seats * wage;
        }
        return taken;
    }

    /**
     * Who of the working townsfolk leaves work when they shrank below those at work: the idle went first, then the hands of the
     * workshops, then those on the posts.
     *
     * @return those taken off the hands, then those taken off the posts
     */
    public static int[] shrink(int folk, int hands, int posts) {
        long excess = (long) Math.max(0, hands) + Math.max(0, posts) - Math.max(0, folk);
        if (excess <= 0) {
            return new int[]{0, 0};
        }
        int fromHands = (int) Math.min(Math.max(0, hands), excess);
        int fromPosts = (int) Math.min(Math.max(0, posts), excess - fromHands);
        return new int[]{fromHands, fromPosts};
    }

    /**
     * A change of the hands of a workshop or a shop in a day.
     */
    public enum HandChange {
        /** It takes one more of the townsfolk. */
        HIRE,
        /** It lets one go. */
        RELEASE,
        /** It keeps those it has. */
        KEEP
    }

    /**
     * Whether a workshop or a shop takes one of the townsfolk as a hand, lets one go or keeps those it has, once a day. Those who
     * earn of it share by the work they give: a keeper or a master its share, a hand {@link FolkWorkRules#handShare} of it, so that
     * a hand adds as much to what they make as it takes of it. It takes one more when it sold out the day before and both a hand
     * would earn at least half the usual income of the townsfolk and its masters at least
     * {@link FolkWorkRules#releaseMasterPercent()} of theirs; it lets one go when either falls below that.
     *
     * @param soldOut     whether it sold out the day before: it held less than a day of its sales
     * @param shared      what those who earn of it shared the day before, after the tithe
     * @param keepers     its keepers or masters; a place without them takes no hands
     * @param hands       its hands of the townsfolk
     * @param keeperUsual the usual income of a keeper or a master of its trade
     * @param keeperShare the share of a keeper or a master, in thousandths
     */
    public static HandChange handChange(boolean soldOut, long shared, int keepers, int hands, long keeperUsual, int keeperShare,
                                        FolkWorkRules rules) {
        if (keepers <= 0) {
            return hands > 0 ? HandChange.RELEASE : HandChange.KEEP;
        }
        int handShare = rules.handShare(keeperShare);
        long total = (long) keepers * keeperShare + (long) Math.max(0, hands) * handShare;
        if (total <= 0 || handShare <= 0) {
            return HandChange.KEEP;
        }
        long sharedMilli = Math.max(0, shared) * 1_000L;
        long handMilli = sharedMilli / total * handShare + sharedMilli % total * handShare / total;
        long keeperMilli = sharedMilli / total * keeperShare + sharedMilli % total * keeperShare / total;
        boolean handEnough = handMilli * 2 >= rules.usualIncome() * 1_000L;
        boolean keeperEnough = keeperMilli * 100 >= keeperUsual * 1_000L * rules.releaseMasterPercent();
        if (hands > 0 && (!handEnough || !keeperEnough)) {
            return HandChange.RELEASE;
        }
        if (soldOut && handEnough && keeperEnough) {
            return HandChange.HIRE;
        }
        return HandChange.KEEP;
    }
}
