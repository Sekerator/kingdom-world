package io.github.sekerator.kingdomworld.core.diplomacy;

/**
 * Settings of diplomacy and war, from the server config.
 *
 * @param preparationTicks      game ticks between the declaration of a war and its start
 * @param maxWarDays            game days a war lasts at most; then it ends in a white peace
 * @param pactDays              game days a pact of non-aggression holds
 * @param truceDays             game days of truce after a war
 * @param proposalTicks         game ticks an offer waits for an answer
 * @param stepTicks             game ticks between two steps of the wars far from players
 * @param battleChance          chance of a squad that advances on the land of the enemy to meet its defenders in a step
 * @param raidChance            chance of a side with squads in the field to fall upon a caravan of the enemy in a step
 * @param scoreBattle           points for a battle won far from players
 * @param scoreKill             points for every enemy soldier put down far from players
 * @param scorePlayerKill       points for an enemy player killed (online and at the keyboard)
 * @param scoreCaravan          points for a caravan of the enemy robbed
 * @param scoreBlockade         points for a deal of the enemy blocked
 * @param scoreHoldDay          points a day for holding the goal of the war (sieges and captures of a later stage)
 * @param pactPenalty           points the victim of a broken pact starts its war with
 * @param exhaustionDay         exhaustion a day of war adds
 * @param exhaustionLoss        exhaustion for every fighter lost
 * @param exhaustionPoor        exhaustion a day of war adds to a side whose treasury is empty
 * @param exhaustionCaravan     exhaustion for a caravan lost to the enemy
 * @param imposeLead            lead in score from which a side may ask for its goal in full
 * @param tributeShare          share of daily income a tribute takes, in permille
 * @param tributeDays           game days a tribute is paid
 * @param vassalShare           share of daily income a vassal pays its overlord, in permille
 * @param indemnityShare        share of the treasury of the loser an indemnity takes, in permille
 * @param landChunks            chunks a war for land claims
 * @param absenceTicks          game ticks after the last player of a kingdom left (or went away) until its land is shielded
 * @param fightGraceTicks       game ticks after a fight near the players of a kingdom during which it gets no shield
 * @param npcWarDays            game days between two wars a ruler of the world declares
 * @param npcRecentWarDays      game days after a war during which rulers of the world leave a kingdom alone
 * @param npcWarsOnPlayers      whether rulers of the world declare wars on kingdoms of players at all
 * @param npcPlayerWarDays      game days between two wars of rulers of the world on one kingdom of players
 * @param npcStrength           how much stronger than a neighbour a warrior must be to attack it
 * @param npcPeaceExhaustion    exhaustion at which a ruler of the world asks for peace
 */
public record DiplomacyRules(long preparationTicks, int maxWarDays, int pactDays, int truceDays, long proposalTicks, int stepTicks,
                             double battleChance, double raidChance, double scoreBattle, double scoreKill, double scorePlayerKill,
                             double scoreCaravan, double scoreBlockade, double scoreHoldDay, double pactPenalty, double exhaustionDay,
                             double exhaustionLoss, double exhaustionPoor, double exhaustionCaravan, double imposeLead, int tributeShare,
                             int tributeDays, int vassalShare, int indemnityShare, int landChunks, long absenceTicks, long fightGraceTicks,
                             int npcWarDays, int npcRecentWarDays, boolean npcWarsOnPlayers, int npcPlayerWarDays, double npcStrength,
                             double npcPeaceExhaustion) {
    public static final DiplomacyRules DEFAULT = new DiplomacyRules(24_000L, 7, 10, 10, 24_000L, 1_200, 0.35, 0.25, 8.0, 1.0, 5.0, 6.0,
            3.0, 5.0, 15.0, 8.0, 1.0, 5.0, 2.0, 40.0, 200, 5, 100, 300, 4, 6_000L, 1_200L, 10, 5, true, 20, 1.5, 60.0);

    public DiplomacyRules {
        if (preparationTicks < 0 || maxWarDays < 1 || pactDays < 1 || truceDays < 0 || proposalTicks < 20 || stepTicks < 20
                || !chance(battleChance) || !chance(raidChance) || !points(scoreBattle) || !points(scoreKill) || !points(scorePlayerKill)
                || !points(scoreCaravan) || !points(scoreBlockade) || !points(scoreHoldDay) || !points(pactPenalty)
                || !points(exhaustionDay) || !points(exhaustionLoss) || !points(exhaustionPoor) || !points(exhaustionCaravan)
                || !points(imposeLead) || tributeShare < 0 || tributeShare > PeaceTerms.MAX_SHARE || tributeDays < 0
                || tributeDays > PeaceTerms.MAX_DAYS || vassalShare < 0 || vassalShare > PeaceTerms.MAX_SHARE || indemnityShare < 0
                || indemnityShare > 1_000 || landChunks < 0 || landChunks > PeaceTerms.MAX_CHUNKS || absenceTicks < 0 || fightGraceTicks < 0
                || npcWarDays < 1 || npcRecentWarDays < 0 || npcPlayerWarDays < 1 || !(npcStrength >= 1.0 && npcStrength <= 10.0)
                || !points(npcPeaceExhaustion)) {
            throw new IllegalArgumentException("Diplomacy rules out of range");
        }
    }

    private static boolean chance(double value) {
        return value >= 0.0 && value <= 1.0;
    }

    private static boolean points(double value) {
        return value >= 0.0 && value <= War.MAX;
    }

    /**
     * The same rules with other limits on the wars of rulers of the world, for operators and checks.
     */
    public DiplomacyRules withNpcWars(boolean onPlayers, int days, int playerDays) {
        return new DiplomacyRules(preparationTicks, maxWarDays, pactDays, truceDays, proposalTicks, stepTicks, battleChance, raidChance,
                scoreBattle, scoreKill, scorePlayerKill, scoreCaravan, scoreBlockade, scoreHoldDay, pactPenalty, exhaustionDay, exhaustionLoss,
                exhaustionPoor, exhaustionCaravan, imposeLead, tributeShare, tributeDays, vassalShare, indemnityShare, landChunks, absenceTicks,
                fightGraceTicks, days, npcRecentWarDays, onPlayers, playerDays, npcStrength, npcPeaceExhaustion);
    }

    /**
     * The same rules with another time of preparation, for checks.
     */
    public DiplomacyRules withPreparation(long ticks) {
        return new DiplomacyRules(ticks, maxWarDays, pactDays, truceDays, proposalTicks, stepTicks, battleChance, raidChance, scoreBattle,
                scoreKill, scorePlayerKill, scoreCaravan, scoreBlockade, scoreHoldDay, pactPenalty, exhaustionDay, exhaustionLoss, exhaustionPoor,
                exhaustionCaravan, imposeLead, tributeShare, tributeDays, vassalShare, indemnityShare, landChunks, absenceTicks, fightGraceTicks,
                npcWarDays, npcRecentWarDays, npcWarsOnPlayers, npcPlayerWarDays, npcStrength, npcPeaceExhaustion);
    }
}
