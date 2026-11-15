package io.github.sekerator.kingdomworld.core.npc;

/**
 * How the ruler of a kingdom of the world thinks of war and of its soldiers: how many it keeps by its wealth and the dangers
 * around it, how many squads it may send away while a reserve stays home, whether a war is worth starting, and whether a fight
 * far from home is still worth fighting. Pure numbers, so that they can be tested without the game.
 */
public final class MilitaryMind {
    /** The most a danger raises the soldiers a ruler keeps, as a level from 0 (none) to {@value}. */
    public static final int MAX_THREAT = 3;
    /** Days of upkeep below which a ruler takes no new soldiers from abroad. */
    public static final int RECRUIT_DAYS = 20;
    /** Days a treasury that loses money must have left for its ruler to take new soldiers from abroad. */
    public static final int RECRUIT_RUNWAY = 30;
    /** Days of upkeep a ruler keeps besides a war chest before it starts a war. */
    public static final int WAR_KEEP_DAYS = 30;
    /** Days of war a ruler counts it pays its soldiers for when it weighs a war. */
    public static final int WAR_DAYS = 7;
    /** Forces at a place against the enemy's below which a ruler calls its squads back. */
    public static final double RETREAT_RATIO = 0.6;
    /** Forces at a place against the enemy's above which a ruler presses on. */
    public static final double PRESS_RATIO = 1.5;
    /** Forces a ruler wants against the garrison of a city before it besieges it. */
    public static final double SIEGE_RATIO = 1.2;
    /** Checks of a war in a row that go badly before a ruler asks for peace. */
    public static final int LOSING_CHECKS = 2;

    private MilitaryMind() {
    }

    // ---------------------------------------------------------------- dangers

    /**
     * How endangered a kingdom is, from 0 to {@link #MAX_THREAT}: one step for murderers wanted in it or robbers near its roads,
     * one for every war it fights (two at most), one for its people killed in its cities in the last days, one for a neighbour
     * far stronger than itself.
     *
     * @param murderers        players wanted for murder in the kingdom
     * @param campsNear        camps of robbers near its capital and roads
     * @param wars             wars the kingdom fights
     * @param killed           its people killed in its cities lately
     * @param strongestRatio   the power of its strongest neighbour over its own, 0 when unknown
     */
    public static int threat(int murderers, int campsNear, int wars, int killed, double strongestRatio) {
        int level = 0;
        if (murderers > 0 || campsNear > 0) {
            level++;
        }
        level += Math.min(2, Math.max(0, wars));
        if (killed >= 2) {
            level++;
        }
        if (strongestRatio >= 1.5) {
            level++;
        }
        return Math.min(MAX_THREAT, level);
    }

    // ---------------------------------------------------------------- how many

    /**
     * How much more of an army a ruler keeps for its wealth: a rich treasury keeps more soldiers, up to half again as many.
     *
     * @param keepDays days of upkeep the treasury pays
     */
    public static double wealthFactor(long keepDays) {
        if (keepDays >= 730) {
            return 1.5;
        }
        if (keepDays >= RulerMind.RICH_DAYS) {
            return 1.3;
        }
        if (keepDays >= 120) {
            return 1.15;
        }
        return 1.0;
    }

    /**
     * How much more of an army a ruler keeps for the dangers around it: a sixth more for every step.
     */
    public static double threatFactor(int threat) {
        return 1.0 + Math.max(0, Math.min(MAX_THREAT, threat)) / 6.0;
    }

    /**
     * The soldiers a ruler keeps of a kind (its guard, its army): what its kingdom's size and its leaning give, more for its
     * wealth and the dangers around it, never above a most.
     *
     * @param base     what the size of the kingdom, its seed and its ruler give
     * @param keepDays days of upkeep the treasury pays
     * @param threat   the dangers around it, from 0 to {@link #MAX_THREAT}
     * @param most     the most it keeps whatever its wealth
     */
    public static int keep(int base, long keepDays, int threat, int most) {
        if (base <= 0) {
            return 0;
        }
        long wanted = Math.round(base * wealthFactor(keepDays) * threatFactor(threat));
        return (int) Math.max(base, Math.min(Math.max(base, most), wanted));
    }

    /**
     * Whether a ruler takes new soldiers from abroad: its treasury pays some weeks of upkeep and, if it loses money, lasts a
     * month.
     *
     * @param runway days the treasury lasts at the pace of the last week, {@link Long#MAX_VALUE} while it earns more than it spends
     */
    public static boolean recruits(long keepDays, long runway) {
        return keepDays >= RECRUIT_DAYS && runway >= RECRUIT_RUNWAY;
    }

    // ---------------------------------------------------------------- squads away

    /**
     * The squads of its field army a ruler keeps home: a third, one more under a danger of two steps.
     *
     * @param total the squads of the field army, those away included
     */
    public static int reserve(int total, int threat) {
        if (total <= 0) {
            return 0;
        }
        return Math.min(total, (total + 2) / 3 + (threat >= 2 ? 1 : 0));
    }

    /**
     * How many squads a ruler may send away now: of the squads at home, as many as leave the reserve home once those already
     * away (at a siege, holding a place, relieving a city) are counted. Every order of war takes from the same count, so that
     * two sieges do not melt the reserve.
     *
     * @param free  squads of the field army at home
     * @param away  squads of the field army away on orders of war
     */
    public static int available(int free, int away, int threat) {
        int total = Math.max(0, free) + Math.max(0, away);
        return Math.max(0, Math.min(free, total - reserve(total, threat) - Math.max(0, away)));
    }

    // ---------------------------------------------------------------- war

    /**
     * What a ruler weighs before it starts a war.
     *
     * @param ownField     the power of the army it can send: its field army without the reserve
     * @param enemyDefence the power that would meet it: the enemy's soldiers, more behind walls, with its allies who would join
     * @param strength     how much stronger it wants to be (1.5 of the rules, more for a cautious ruler)
     * @param keepDays     days of upkeep its treasury pays
     * @param payroll      what its soldiers cost a day
     * @param upkeep       what a day costs the kingdom
     * @param balance      what its treasury holds
     * @param gain         what the war would bring: tribute, land, loot, in money
     * @param otherWars    wars it fights already
     */
    public record WarCase(double ownField, double enemyDefence, double strength, long keepDays, long payroll, long upkeep, long balance,
                          long gain, int otherWars) {
    }

    /**
     * Why a ruler does not start a war, or empty when it may.
     */
    public static String refuseWar(WarCase war) {
        if (war.otherWars() > 0) {
            return "busy";
        }
        if (war.ownField() <= 0 || war.ownField() < war.enemyDefence() * war.strength()) {
            return "weak";
        }
        long chest = Math.max(0, war.payroll()) * WAR_DAYS;
        if (war.keepDays() < WAR_KEEP_DAYS || war.balance() < chest + Math.max(1, war.upkeep()) * WAR_KEEP_DAYS) {
            return "poor";
        }
        if (war.gain() < chest / 2) {
            return "worthless";
        }
        return "";
    }

    /**
     * What a ruler makes of a fight at a place: its squads and the enemy's there, by their power.
     */
    public enum Field {
        /** It is losing: its squads come home and the reserve stays there. */
        RETREAT,
        /** Neither side has the upper hand. */
        HOLD,
        /** It is winning: more squads may follow, never the reserve. */
        PRESS
    }

    public static Field assess(double ours, double theirs) {
        if (ours <= 0 && theirs <= 0) {
            return Field.HOLD;
        }
        if (theirs <= 0) {
            return Field.PRESS;
        }
        double ratio = ours / theirs;
        if (ratio < RETREAT_RATIO) {
            return Field.RETREAT;
        }
        return ratio > PRESS_RATIO ? Field.PRESS : Field.HOLD;
    }

    /**
     * Whether a ruler asks for peace: its war went badly at some checks in a row, or its treasury lasts no more than a few days.
     *
     * @param losingChecks checks of the war in a row at which its forces were below the enemy's
     * @param runway       days its treasury lasts at the pace of the last week
     */
    public static boolean suesForPeace(int losingChecks, long runway) {
        return losingChecks >= LOSING_CHECKS || runway < 3;
    }
}
