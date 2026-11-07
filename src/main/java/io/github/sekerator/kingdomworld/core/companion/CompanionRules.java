package io.github.sekerator.kingdomworld.core.companion;

import io.github.sekerator.kingdomworld.core.guild.Rank;

/**
 * The settings of companions and of the parties of players: the market of slaves and of free companions, prices and wages,
 * the limits by rank, hunger, loyalty and escape, experience, and the size of parties. Built from the server config; the
 * defaults are the defaults of a new config. Times are in ticks, money in copper coins.
 */
public final class CompanionRules {
    /** Ticks of one game day. */
    public static final long DAY = 24_000L;
    /** Food a companion holds when full, like a player. */
    public static final int MAX_FOOD = 20;
    /** Most loyalty. */
    public static final int MAX_LOYALTY = 100;

    // ---------------------------------------------------------------- the market
    /** Slaves on the market of one branch each day. */
    public final int slaveOffers;
    /** Free companions looking for work at one branch each day. */
    public final int freeOffers;
    /** Game days between new offers. */
    public final int refreshDays;
    /** What a slave costs per point of strength. */
    public final double pricePerPower;
    /** What a slave costs at least. */
    public final long minPrice;
    /** The daily wage of a free companion per point of strength. */
    public final double wagePerPower;
    /** The daily wage at least. */
    public final long minWage;
    /** The share of the worth the market pays for a slave sold back, in percent. */
    public final int sellPercent;
    /** Highest level of companions on the market. */
    public final int maxOfferLevel;
    private final int[] rankLimits;

    // ---------------------------------------------------------------- needs
    /** Ticks in which a companion of average stamina uses one point of food. */
    public final long hungerTicks;
    /** A companion eats when its food falls to this. */
    public final int eatBelow;
    /** Ticks without food before the strength falls and loyalty starts to drop. */
    public final long starveTicks;
    /** Share of the strength left to a starving companion. */
    public final double starvingStrength;
    /** Ticks per point of health healed. */
    public final long healTicks;
    /** Ticks per point of health healed in an infirmary of the owner's kingdom. */
    public final long infirmaryHealTicks;

    // ---------------------------------------------------------------- loyalty
    public final int slaveLoyalty;
    public final int freeLoyalty;
    public final int freedLoyalty;
    /** Loyalty lost per starving period of {@link #starveTicks}. */
    public final int hungerLoss;
    /** Loyalty lost for a heavy wound. */
    public final int woundLoss;
    /** Loyalty lost for a blow of the owner. */
    public final int beatenLoss;
    /** Ticks of good keeping that win one point of loyalty. */
    public final long wellKeptTicks;
    /** Loyalty a slave reaches at most by good keeping alone. */
    public final int wellKeptCap;
    /** Worth of a gift per point of loyalty. */
    public final long giftPerPoint;
    /** Loyalty one gift wins at most. */
    public final int maxGift;
    /** Below this loyalty a companion warns that it will leave. */
    public final int escapeBelow;
    /** Ticks a companion stays below {@link #escapeBelow} after the warning before it leaves. */
    public final long escapeTicks;

    // ---------------------------------------------------------------- wages, experience, parties
    /** Days a free companion waits for its wage before it leaves. */
    public final int unpaidDays;
    /** Experience a level needs: this times the level. */
    public final long experiencePerLevel;
    public final int maxLevel;
    /** Players in one party at most, the leader included. */
    public final int maxParty;
    /** Ticks an invitation to a party is good for. */
    public final long inviteTicks;
    /** Distance at which a companion has a body near its owner, in blocks. */
    public final int bodyRange;

    public static final CompanionRules DEFAULT = new Builder().build();

    private CompanionRules(Builder b) {
        slaveOffers = Math.max(0, Math.min(16, b.slaveOffers));
        freeOffers = Math.max(0, Math.min(16, b.freeOffers));
        refreshDays = Math.max(1, b.refreshDays);
        pricePerPower = Math.max(0.0, b.pricePerPower);
        minPrice = Math.max(1, b.minPrice);
        wagePerPower = Math.max(0.0, b.wagePerPower);
        minWage = Math.max(1, b.minWage);
        sellPercent = Math.max(0, Math.min(100, b.sellPercent));
        maxOfferLevel = Math.max(0, Math.min(Companion.MAX_LEVEL, b.maxOfferLevel));
        if (b.rankLimits.length != Rank.COUNT) {
            throw new IllegalArgumentException("A table by rank needs " + Rank.COUNT + " values, not " + b.rankLimits.length);
        }
        rankLimits = b.rankLimits.clone();
        hungerTicks = Math.max(20, b.hungerTicks);
        eatBelow = Math.max(0, Math.min(MAX_FOOD - 1, b.eatBelow));
        starveTicks = Math.max(20, b.starveTicks);
        starvingStrength = Math.max(0.1, Math.min(1.0, b.starvingStrength));
        healTicks = Math.max(20, b.healTicks);
        infirmaryHealTicks = Math.max(20, Math.min(healTicks, b.infirmaryHealTicks));
        slaveLoyalty = clampLoyalty(b.slaveLoyalty);
        freeLoyalty = clampLoyalty(b.freeLoyalty);
        freedLoyalty = clampLoyalty(b.freedLoyalty);
        hungerLoss = Math.max(0, b.hungerLoss);
        woundLoss = Math.max(0, b.woundLoss);
        beatenLoss = Math.max(0, b.beatenLoss);
        wellKeptTicks = Math.max(20, b.wellKeptTicks);
        wellKeptCap = clampLoyalty(b.wellKeptCap);
        giftPerPoint = Math.max(1, b.giftPerPoint);
        maxGift = Math.max(0, b.maxGift);
        escapeBelow = clampLoyalty(b.escapeBelow);
        escapeTicks = Math.max(20, b.escapeTicks);
        unpaidDays = Math.max(0, b.unpaidDays);
        experiencePerLevel = Math.max(1, b.experiencePerLevel);
        maxLevel = Math.max(0, Math.min(Companion.MAX_LEVEL, b.maxLevel));
        maxParty = Math.max(2, Math.min(Party.MAX_MEMBERS, b.maxParty));
        inviteTicks = Math.max(20, b.inviteTicks);
        bodyRange = Math.max(16, b.bodyRange);
    }

    private static int clampLoyalty(int value) {
        return Math.max(0, Math.min(MAX_LOYALTY, value));
    }

    /**
     * Companions a member of a rank keeps at most.
     */
    public int limit(Rank rank) {
        return Math.max(0, rankLimits[rank.ordinal()]);
    }

    public int[] rankLimits() {
        return rankLimits.clone();
    }

    /**
     * Experience a companion needs to reach a level from the one below.
     */
    public long experienceFor(int level) {
        return experiencePerLevel * Math.max(1, level);
    }

    /**
     * Settings with mutable fields, filled from the config or changed by tests.
     */
    public static final class Builder {
        public int slaveOffers = 4;
        public int freeOffers = 3;
        public int refreshDays = 1;
        public double pricePerPower = 3.0;
        public long minPrice = 30;
        public double wagePerPower = 0.25;
        public long minWage = 2;
        public int sellPercent = 50;
        public int maxOfferLevel = 8;
        public int[] rankLimits = {1, 1, 2, 2, 3, 3, 4, 4, 5};
        public long hungerTicks = 1_200;
        public int eatBelow = 14;
        public long starveTicks = 6_000;
        public double starvingStrength = 0.6;
        public long healTicks = 200;
        public long infirmaryHealTicks = 40;
        public int slaveLoyalty = 40;
        public int freeLoyalty = 60;
        public int freedLoyalty = 85;
        public int hungerLoss = 3;
        public int woundLoss = 2;
        public int beatenLoss = 5;
        public long wellKeptTicks = 6_000;
        public int wellKeptCap = 80;
        public long giftPerPoint = 5;
        public int maxGift = 15;
        public int escapeBelow = 20;
        public long escapeTicks = 24_000;
        public int unpaidDays = 1;
        public long experiencePerLevel = 60;
        public int maxLevel = 20;
        public int maxParty = 5;
        public long inviteTicks = 6_000;
        public int bodyRange = 96;

        public CompanionRules build() {
            return new CompanionRules(this);
        }
    }
}
