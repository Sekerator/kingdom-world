package io.github.sekerator.kingdomworld.core.law;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.economy.Money;

/**
 * The numbers of the law: who notices a crime and how soon it is reported, fines, how fast petty crimes are forgotten,
 * bounties, the ransom of confiscated goods, reputation and its weight, debts, the entry into cities and the ban of
 * slavery. Money is in copper coins of the kingdom, times in ticks.
 */
public final class LawRules {
    public static final LawRules DEFAULT = new Builder().build();

    /** How far a witness sees a crime, in blocks. */
    public final int witnessRadius;
    /** Shortest and longest time a citizen needs to run to the guards with a report. */
    public final int reportMinTicks;
    public final int reportMaxTicks;
    /** Game days after which a petty crime is forgotten, for a player of neutral reputation. */
    public final double pettyDays;
    /** Fine for a theft, in percent of what was stolen. */
    public final int theftFinePercent;
    public final long assaultFine;
    public final long trespassFine;
    /** Fine that replaces the death of a murderer when the court allows it. */
    public final long murderFine;
    /** Thefts (and blows at one victim) closer together than this make one crime. */
    public final int mergeTicks;
    /** Reward of the guild contract for the head of a wanted murderer, from the treasury; 0 posts none. */
    public final long bountyReward;
    /** Days a contract for a head runs before it is posted anew. */
    public final int bountyDays;
    /** Share of the worth of confiscated goods their owner pays to get them back, in percent. */
    public final int ransomPercent;
    /** Game days confiscated goods wait for their owner before they go to the storehouses. */
    public final int confiscationDays;
    /** Debt to a treasury above which the market of the kingdom serves the player only for coins and takes no new lots. */
    public final long debtLimit;
    /** Ticks a guard gives a wanted player to give up after calling out. */
    public final int hailTicks;
    /** Health a player beaten by guards for a petty crime keeps, in points. */
    public final float beatenHealth;
    /** Ticks of weakness after the beating. */
    public final int beatenWeaknessTicks;
    public final int theftReputation;
    public final int assaultReputation;
    public final int murderReputation;
    public final int trespassReputation;
    /** Money traded with a kingdom that wins a point of reputation there. */
    public final long tradePerPoint;
    public final int taxReputation;
    public final int contractReputation;
    /** Reputation from which entry is free and petty crimes are forgotten faster. */
    public final int highReputation;
    /** How much reputation moves market prices at most, in percent. */
    public final int priceEffectPercent;
    /** Fine of an owner whose slave the law of a kingdom sets free. */
    public final long slaveryFine;
    /** Highest entry fee a king may ask. */
    public final long maxEntryFee;
    /** How long a paid entry lasts, in ticks: a game day. */
    public final long passTicks;
    /** Highest bounty a king may set. */
    public final long maxBounty;
    /** Blocks from the border of a kingdom that forbids slaves at which an owner is warned. */
    public final int slaveryWarnBlocks;

    private LawRules(Builder b) {
        witnessRadius = Math.max(1, b.witnessRadius);
        reportMinTicks = Math.max(0, b.reportMinTicks);
        reportMaxTicks = Math.max(reportMinTicks, b.reportMaxTicks);
        pettyDays = Math.max(0.01, b.pettyDays);
        theftFinePercent = Math.max(0, b.theftFinePercent);
        assaultFine = clamp(b.assaultFine);
        trespassFine = clamp(b.trespassFine);
        murderFine = clamp(b.murderFine);
        mergeTicks = Math.max(0, b.mergeTicks);
        bountyReward = clamp(b.bountyReward);
        bountyDays = Math.max(1, b.bountyDays);
        ransomPercent = Math.max(0, Math.min(1000, b.ransomPercent));
        confiscationDays = Math.max(1, b.confiscationDays);
        debtLimit = clamp(b.debtLimit);
        hailTicks = Math.max(0, b.hailTicks);
        beatenHealth = Math.max(1.0f, b.beatenHealth);
        beatenWeaknessTicks = Math.max(0, b.beatenWeaknessTicks);
        theftReputation = b.theftReputation;
        assaultReputation = b.assaultReputation;
        murderReputation = b.murderReputation;
        trespassReputation = b.trespassReputation;
        tradePerPoint = Math.max(1, b.tradePerPoint);
        taxReputation = b.taxReputation;
        contractReputation = b.contractReputation;
        highReputation = Reputation.clamp(b.highReputation);
        priceEffectPercent = Math.max(0, Math.min(100, b.priceEffectPercent));
        slaveryFine = clamp(b.slaveryFine);
        maxEntryFee = clamp(b.maxEntryFee);
        passTicks = Math.max(1, b.passTicks);
        maxBounty = clamp(b.maxBounty);
        slaveryWarnBlocks = Math.max(0, b.slaveryWarnBlocks);
    }

    private static long clamp(long value) {
        return Math.max(0, Math.min(Money.MAX, value));
    }

    /**
     * Ticks after which a petty crime is forgotten: faster with a good name, slower with a bad one (half the time at the best
     * name, one and a half at the worst).
     */
    public long decayTicks(int reputation) {
        double base = pettyDays * CitizenNeeds.TICKS_PER_DAY;
        return Math.max(1L, Math.round(base * (2 * Reputation.MAX - Reputation.clamp(reputation)) / (2.0 * Reputation.MAX)));
    }

    /**
     * What the owner pays to get back goods worth this much: the share of the worth, rounded up, at least a coin.
     */
    public long ransom(long worth) {
        if (worth <= 0 || ransomPercent == 0) {
            return 0;
        }
        long price = Money.share(worth, ransomPercent, 100);
        if (Money.multiplyCapped(price, 100) < Money.multiplyCapped(worth, ransomPercent)) {
            price = Money.addCapped(price, 1);
        }
        return Math.max(1, price);
    }

    /**
     * The fine for a theft of goods worth this much, before the multiplier of the court.
     */
    public long theftFine(long worth) {
        if (worth <= 0) {
            return 0;
        }
        return Math.max(1, Money.share(worth, theftFinePercent, 100));
    }

    /**
     * Rules with every number settable, for the config and for tests.
     */
    public static final class Builder {
        public int witnessRadius = 16;
        public int reportMinTicks = 200;
        public int reportMaxTicks = 600;
        public double pettyDays = 3.0;
        public int theftFinePercent = 200;
        public long assaultFine = 50;
        public long trespassFine = 30;
        public long murderFine = 1_000;
        public int mergeTicks = 1_200;
        public long bountyReward = 200;
        public int bountyDays = 3;
        public int ransomPercent = 50;
        public int confiscationDays = 7;
        public long debtLimit = 500;
        public int hailTicks = 100;
        public float beatenHealth = 2.0f;
        public int beatenWeaknessTicks = 200;
        public int theftReputation = -5;
        public int assaultReputation = -8;
        public int murderReputation = -40;
        public int trespassReputation = -3;
        public long tradePerPoint = 500;
        public int taxReputation = 1;
        public int contractReputation = 2;
        public int highReputation = 50;
        public int priceEffectPercent = 10;
        public long slaveryFine = 300;
        public long maxEntryFee = 10_000;
        public long passTicks = CitizenNeeds.TICKS_PER_DAY;
        public long maxBounty = 1_000_000;
        public int slaveryWarnBlocks = 32;

        public LawRules build() {
            return new LawRules(this);
        }
    }
}
