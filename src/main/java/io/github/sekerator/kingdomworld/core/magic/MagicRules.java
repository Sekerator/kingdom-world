package io.github.sekerator.kingdomworld.core.magic;

import io.github.sekerator.kingdomworld.core.guild.Rank;

/**
 * The settings of the energy of kingdoms and of the mage tower: how much energy a kingdom may hold, how fast mages
 * dissolve crystals, what research and spells cost, the spells of players, the crystal generators and the wells for the
 * magic of other mods. Built from the server config; the defaults are the defaults of a new config. Tables by research,
 * by spell of a kingdom and by spell of players hold one value per constant of {@link Research}, {@link KingdomSpell} and
 * {@link PlayerSpell}.
 */
public final class MagicRules {
    // ---------------------------------------------------------------- the store
    /** Energy every kingdom may hold without accumulators. */
    public final long baseCapacity;
    /** Energy each crystal accumulator on the kingdom's land adds to it. */
    public final long accumulatorCapacity;

    // ---------------------------------------------------------------- mages
    /** Ticks a mage of usual speed takes to dissolve one crystal. */
    public final int dissolveTicks;
    /** Crystals a mage post asks the carriers to keep in its container. */
    public final int crystalsAtPost;
    /** Energy a fireball of a mage defending the land costs. */
    public final long mageFireballCost;
    /** Energy healing a wounded neighbor costs a mage. */
    public final long mageHealCost;
    /** Ticks between two spells of a mage defending the land. */
    public final int mageCastTicks;
    /** How far a mage sees monsters and hurt neighbors, in blocks. */
    public final int mageRange;

    // ---------------------------------------------------------------- research
    private final long[] researchCost;
    private final int[] researchMinutes;

    // ---------------------------------------------------------------- spells of kingdoms
    private final long[] spellPrice;
    /** Chunks one kingdom may keep under the ward. */
    public final int wardMaxChunks;
    /** Share of the natural spawns of hostile monsters the ward stops, from 0 to 1. */
    public final double wardReduction;
    /** Health a wounded citizen in the infirmary gets each second besides the usual healing. */
    public final float healingPerSecond;
    /** Radius around farm and herd posts that fertility reaches, in blocks. */
    public final int fertilityRadius;
    /** Extra growth steps of crops around each post per second. */
    public final int fertilityGrowthPerSecond;
    /** Share of the remaining way a caravan saves with the fair wind, from 0 to 1. */
    public final double tailwindShare;
    /** Gates one kingdom may have. */
    public final int maxGates;

    // ---------------------------------------------------------------- spells of players
    private final long[] playerCost;
    private final int[] playerCooldown;
    private final Rank[] playerRank;
    private final int[] playerTowerLevel;
    private final long[] lessonPrice;
    /** Spells a staff holds. */
    public final int staffSlots;
    public final float fireballDamage;
    public final float healAmount;
    public final int shieldSeconds;
    public final int lightSeconds;
    public final int detectRadius;
    public final int detectSeconds;
    public final int blinkDistance;

    // ---------------------------------------------------------------- machines and other magic
    /** Forge Energy a generator gives for one unit of the kingdom's energy. */
    public final int feRate;
    /** Forge Energy one generator gives at most per tick. */
    public final int feMaxPerTick;
    /** Whether the well of a focus gives mana to the magic of Iron's Spells 'n Spellbooks. */
    public final boolean ironsWell;
    /** Mana of Iron's Spells for one unit of energy. */
    public final int ironsManaPerEnergy;
    /** Whether the focus gives source to the jars of Ars Nouveau and mana to its players. */
    public final boolean arsWell;
    public final int arsManaPerEnergy;
    public final int arsSourcePerEnergy;
    /** How far the well of a focus reaches, in blocks. */
    public final int wellRadius;
    /** Ticks between two pulses of a well. */
    public final int wellIntervalTicks;
    /** Energy one well spends at most per player or jar and pulse. */
    public final long wellMaxEnergy;

    public static final MagicRules DEFAULT = new Builder().build();

    private MagicRules(Builder b) {
        baseCapacity = Math.max(0, b.baseCapacity);
        accumulatorCapacity = Math.max(0, b.accumulatorCapacity);
        dissolveTicks = Math.max(1, b.dissolveTicks);
        crystalsAtPost = Math.max(1, b.crystalsAtPost);
        mageFireballCost = Math.max(0, b.mageFireballCost);
        mageHealCost = Math.max(0, b.mageHealCost);
        mageCastTicks = Math.max(1, b.mageCastTicks);
        mageRange = Math.max(1, b.mageRange);
        researchCost = table(b.researchCost, Research.COUNT);
        researchMinutes = table(b.researchMinutes, Research.COUNT);
        spellPrice = table(b.spellPrice, KingdomSpell.COUNT);
        wardMaxChunks = Math.max(0, b.wardMaxChunks);
        wardReduction = Math.max(0.0, Math.min(1.0, b.wardReduction));
        healingPerSecond = Math.max(0.0f, b.healingPerSecond);
        fertilityRadius = Math.max(1, b.fertilityRadius);
        fertilityGrowthPerSecond = Math.max(0, b.fertilityGrowthPerSecond);
        tailwindShare = Math.max(0.0, Math.min(0.95, b.tailwindShare));
        maxGates = Math.max(0, b.maxGates);
        playerCost = table(b.playerCost, PlayerSpell.COUNT);
        playerCooldown = table(b.playerCooldown, PlayerSpell.COUNT);
        if (b.playerRank.length != PlayerSpell.COUNT) {
            throw new IllegalArgumentException("A table by spell needs " + PlayerSpell.COUNT + " values");
        }
        playerRank = b.playerRank.clone();
        playerTowerLevel = table(b.playerTowerLevel, PlayerSpell.COUNT);
        lessonPrice = table(b.lessonPrice, PlayerSpell.COUNT);
        staffSlots = Math.max(1, Math.min(9, b.staffSlots));
        fireballDamage = Math.max(0.0f, b.fireballDamage);
        healAmount = Math.max(0.0f, b.healAmount);
        shieldSeconds = Math.max(1, b.shieldSeconds);
        lightSeconds = Math.max(1, b.lightSeconds);
        detectRadius = Math.max(1, b.detectRadius);
        detectSeconds = Math.max(1, b.detectSeconds);
        blinkDistance = Math.max(1, b.blinkDistance);
        feRate = Math.max(1, b.feRate);
        feMaxPerTick = Math.max(0, b.feMaxPerTick);
        ironsWell = b.ironsWell;
        ironsManaPerEnergy = Math.max(1, b.ironsManaPerEnergy);
        arsWell = b.arsWell;
        arsManaPerEnergy = Math.max(1, b.arsManaPerEnergy);
        arsSourcePerEnergy = Math.max(1, b.arsSourcePerEnergy);
        wellRadius = Math.max(1, b.wellRadius);
        wellIntervalTicks = Math.max(1, b.wellIntervalTicks);
        wellMaxEnergy = Math.max(1, b.wellMaxEnergy);
    }

    private static long[] table(long[] values, int size) {
        if (values.length != size) {
            throw new IllegalArgumentException("The table needs " + size + " values, not " + values.length);
        }
        long[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] = Math.max(0, copy[i]);
        }
        return copy;
    }

    private static int[] table(int[] values, int size) {
        if (values.length != size) {
            throw new IllegalArgumentException("The table needs " + size + " values, not " + values.length);
        }
        int[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] = Math.max(0, copy[i]);
        }
        return copy;
    }

    /** Energy a research costs when it starts. */
    public long researchCost(Research research) {
        return researchCost[research.ordinal()];
    }

    /** Minutes of work of one mage of usual speed a research takes. */
    public int researchMinutes(Research research) {
        return researchMinutes[research.ordinal()];
    }

    /** Ticks of work of one mage of usual speed a research takes. */
    public long researchTicks(Research research) {
        return Math.max(1L, researchMinutes[research.ordinal()] * 1_200L);
    }

    /**
     * The price of a spell of a kingdom: per chunk and day for the ward, per post and day for fertility, per wounded
     * citizen for healing, per caravan for the fair wind, per trip for the gates.
     */
    public long spellPrice(KingdomSpell spell) {
        return spellPrice[spell.ordinal()];
    }

    /** Energy one cast of a spell of players costs. */
    public long cost(PlayerSpell spell) {
        return playerCost[spell.ordinal()];
    }

    /** Ticks before the same spell can be cast again. */
    public int cooldown(PlayerSpell spell) {
        return playerCooldown[spell.ordinal()];
    }

    /** The guild rank a player needs to learn a spell. */
    public Rank minRank(PlayerSpell spell) {
        return playerRank[spell.ordinal()];
    }

    /** The level of the tower (finished researches) a focus needs to teach a spell. */
    public int towerLevel(PlayerSpell spell) {
        return playerTowerLevel[spell.ordinal()];
    }

    /** What learning a spell costs, paid to the treasury of the focus's kingdom. */
    public long lessonPrice(PlayerSpell spell) {
        return lessonPrice[spell.ordinal()];
    }

    /**
     * Settings with mutable fields, filled from the config or changed by tests.
     */
    public static final class Builder {
        public long baseCapacity = 5_000;
        public long accumulatorCapacity = 20_000;
        public int dissolveTicks = 200;
        public int crystalsAtPost = 16;
        public long mageFireballCost = 1;
        public long mageHealCost = 2;
        public int mageCastTicks = 40;
        public int mageRange = 12;
        public long[] researchCost = {500, 500, 800, 800, 2_000, 1_000, 5_000, 20_000};
        public int[] researchMinutes = {20, 20, 30, 30, 60, 30, 60, 120};
        public long[] spellPrice = {5, 20, 10, 30, 10};
        public int wardMaxChunks = 64;
        public double wardReduction = 0.8;
        public float healingPerSecond = 0.5f;
        public int fertilityRadius = 12;
        public int fertilityGrowthPerSecond = 4;
        public double tailwindShare = 0.5;
        public int maxGates = 16;
        public long[] playerCost = {5, 8, 2, 4, 15, 10};
        public int[] playerCooldown = {20, 100, 200, 200, 600, 60};
        public Rank[] playerRank = {Rank.F, Rank.F, Rank.F, Rank.E, Rank.D, Rank.C};
        public int[] playerTowerLevel = {0, 0, 0, 1, 2, 3};
        public long[] lessonPrice = {20, 30, 10, 40, 100, 150};
        public int staffSlots = 3;
        public float fireballDamage = 6.0f;
        public float healAmount = 6.0f;
        public int shieldSeconds = 30;
        public int lightSeconds = 120;
        public int detectRadius = 24;
        public int detectSeconds = 10;
        public int blinkDistance = 8;
        public int feRate = 40;
        public int feMaxPerTick = 400;
        public boolean ironsWell = true;
        public int ironsManaPerEnergy = 5;
        public boolean arsWell = true;
        public int arsManaPerEnergy = 5;
        public int arsSourcePerEnergy = 20;
        public int wellRadius = 8;
        public int wellIntervalTicks = 40;
        public long wellMaxEnergy = 20;

        public MagicRules build() {
            return new MagicRules(this);
        }
    }
}
