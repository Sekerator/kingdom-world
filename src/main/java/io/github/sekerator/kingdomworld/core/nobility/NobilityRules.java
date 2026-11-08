package io.github.sekerator.kingdomworld.core.nobility;

/**
 * The numbers of the nobility, intrigues and spies, from the config.
 *
 * @param shareBaron       percent of the income of its fief a baron gets each day
 * @param shareCount       percent a count gets
 * @param shareDuke        percent a duke gets
 * @param demandEveryDays  played days between two demands of a court, at least
 * @param demandDueDays    played days a demand waits for an answer
 * @param demandValue      what works and the gift of a bargain cost, in money
 * @param plotBelow        stability below which plots may start
 * @param plotChance       chance a discontented house starts a plot on a played day of low stability
 * @param plotDays         played days a plot takes to ripen
 * @param secessionWarnDays played days of low stability after which the crown of players is warned a city may leave
 * @param secessionDays    played days of low stability after which a ripe plot takes its city out of the kingdom
 * @param rebelDays        played days rebels keep part of the income after a plot
 * @param rebelPercent     percent of the income of a day rebels keep
 * @param spyHire          what hiring a spy costs
 * @param spyWage          what a spy costs each day
 * @param maxSpies         spies a kingdom of players may keep
 * @param npcMaxSpies      spies a ruler of the world keeps at most
 * @param theftPercent     percent of a treasury a spy takes
 * @param theftMax         most money a spy takes at once
 * @param ransom           the ransom of a caught spy
 * @param ransomDays       game days a caught spy is held before being put to death
 * @param counterBase      counter-intelligence of every kingdom, in percent
 * @param caughtChance     chance a spy who failed is caught
 * @param marriageChance   chance of a marriage of houses of two kingdoms on a game day
 * @param deathChance      chance the head of a house dies on a game day
 * @param grantDealings    how well a ruler of the world must know a player to make them a baron
 * @param grantReputation  the reputation a player needs in a kingdom of the world for a title
 * @param highStability    stability from which citizens are happier
 * @param moodLow          the mood of citizens of a kingdom of low stability changes by this
 * @param moodHigh         the mood of citizens of a kingdom of high stability changes by this
 * @param maxHouses        noble houses a kingdom may have; the crown raises no more
 */
public record NobilityRules(int shareBaron, int shareCount, int shareDuke, int demandEveryDays, int demandDueDays, long demandValue,
                            int plotBelow, double plotChance, int plotDays, int secessionWarnDays, int secessionDays, int rebelDays,
                            int rebelPercent, long spyHire, long spyWage, int maxSpies, int npcMaxSpies, int theftPercent, long theftMax,
                            long ransom, int ransomDays, int counterBase, double caughtChance, double marriageChance, double deathChance,
                            int grantDealings, int grantReputation, int highStability, int moodLow, int moodHigh, int maxHouses) {
    public static final NobilityRules DEFAULT = new NobilityRules(10, 15, 20, 3, 2, 150, 30, 0.35, 4, 3, 6, 4, 25, 100, 5, 4, 2, 5, 400,
            150, 3, 15, 0.6, 0.04, 0.004, 40, 60, 70, -10, 5, 8);

    public NobilityRules {
        shareBaron = clamp(shareBaron, 0, 50);
        shareCount = clamp(shareCount, 0, 50);
        shareDuke = clamp(shareDuke, 0, 50);
        demandEveryDays = clamp(demandEveryDays, 1, 60);
        demandDueDays = clamp(demandDueDays, 1, 30);
        demandValue = Math.max(1, Math.min(1_000_000, demandValue));
        plotBelow = clamp(plotBelow, 0, 100);
        plotChance = Math.max(0, Math.min(1, plotChance));
        plotDays = clamp(plotDays, 1, 60);
        secessionWarnDays = clamp(secessionWarnDays, 1, 120);
        secessionDays = Math.max(secessionWarnDays + 1, clamp(secessionDays, 1, 240));
        rebelDays = clamp(rebelDays, 1, 60);
        rebelPercent = clamp(rebelPercent, 0, 90);
        spyHire = Math.max(0, Math.min(1_000_000, spyHire));
        spyWage = Math.max(0, Math.min(1_000_000, spyWage));
        maxSpies = clamp(maxSpies, 0, 16);
        npcMaxSpies = clamp(npcMaxSpies, 0, 8);
        theftPercent = clamp(theftPercent, 0, 50);
        theftMax = Math.max(0, Math.min(1_000_000_000L, theftMax));
        ransom = Math.max(0, Math.min(1_000_000, ransom));
        ransomDays = clamp(ransomDays, 1, 30);
        counterBase = clamp(counterBase, 0, 80);
        caughtChance = Math.max(0, Math.min(1, caughtChance));
        marriageChance = Math.max(0, Math.min(1, marriageChance));
        deathChance = Math.max(0, Math.min(1, deathChance));
        grantDealings = clamp(grantDealings, 0, 1000);
        grantReputation = clamp(grantReputation, -1000, 1000);
        highStability = clamp(highStability, plotBelow + 1, 100);
        moodLow = clamp(moodLow, -50, 0);
        moodHigh = clamp(moodHigh, 0, 50);
        maxHouses = clamp(maxHouses, 1, 32);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * The same rules with another chance a failed spy is caught.
     */
    public NobilityRules withCaughtChance(double value) {
        return new NobilityRules(shareBaron, shareCount, shareDuke, demandEveryDays, demandDueDays, demandValue, plotBelow, plotChance, plotDays,
                secessionWarnDays, secessionDays, rebelDays, rebelPercent, spyHire, spyWage, maxSpies, npcMaxSpies, theftPercent, theftMax, ransom,
                ransomDays, counterBase, value, marriageChance, deathChance, grantDealings, grantReputation, highStability, moodLow, moodHigh, maxHouses);
    }

    /**
     * The same rules with another limit of houses.
     */
    public NobilityRules withMaxHouses(int value) {
        return new NobilityRules(shareBaron, shareCount, shareDuke, demandEveryDays, demandDueDays, demandValue, plotBelow, plotChance, plotDays,
                secessionWarnDays, secessionDays, rebelDays, rebelPercent, spyHire, spyWage, maxSpies, npcMaxSpies, theftPercent, theftMax, ransom,
                ransomDays, counterBase, caughtChance, marriageChance, deathChance, grantDealings, grantReputation, highStability, moodLow, moodHigh,
                value);
    }

    /**
     * Percent of the income of its fief a title gets.
     */
    public int share(Title title) {
        return switch (title) {
            case BARON -> shareBaron;
            case COUNT -> shareCount;
            case DUKE -> shareDuke;
        };
    }
}
