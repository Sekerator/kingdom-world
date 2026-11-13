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
    /** Seconds a blink into the protected land of another kingdom takes to prepare. */
    public final int blinkPrepareSeconds;

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

    // ---------------------------------------------------------------- the guild of mages
    /** Levels a learned spell has, from I to IV. */
    public static final int MAX_LEVEL = 4;
    /** The highest level of a spell a guild of mages teaches, by its rank. */
    private final int[] lessonLevel;
    /** How much stronger (damage, healing, reach, time) each level of a spell is than the first, I to IV. */
    private final double[] levelPower;
    /** What the lesson for each level costs, as a share of the lesson for the first, I to IV. */
    private final double[] levelPrice;
    /** What a lesson costs someone who is not a subject of the kingdom, as a share of what a subject pays. */
    public final double foreignerPrice;
    /** Researches, mages and energy the guild of mages of a kingdom of the world has from its founding, by its rank. */
    private final int[] worldResearch;
    private final int[] worldMages;
    private final long[] worldEnergy;
    /** Health of the body of a mage, and how much stronger the spells of a mage are, by the rank of the guild. */
    private final double[] mageHealth;
    private final double[] mageSpellPower;
    /** The worth a guild of mages of players needs for each rank, and what a research, a mage and a mage tower are worth. */
    private final long[] guildScore;
    public final int researchWorth;
    public final int mageWorth;
    public final int towerWorth;

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
        blinkPrepareSeconds = Math.max(1, b.blinkPrepareSeconds);
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
        lessonLevel = table(b.lessonLevel, Rank.COUNT);
        for (int i = 0; i < lessonLevel.length; i++) {
            lessonLevel[i] = Math.max(1, Math.min(MAX_LEVEL, lessonLevel[i]));
        }
        levelPower = byLevel(b.levelPower);
        levelPrice = byLevel(b.levelPrice);
        foreignerPrice = Math.max(1.0, b.foreignerPrice);
        worldResearch = table(b.worldResearch, Rank.COUNT);
        worldMages = table(b.worldMages, Rank.COUNT);
        worldEnergy = table(b.worldEnergy, Rank.COUNT);
        mageHealth = byRank(b.mageHealth);
        mageSpellPower = byRank(b.mageSpellPower);
        guildScore = table(b.guildScore, Rank.COUNT);
        researchWorth = Math.max(0, b.researchWorth);
        mageWorth = Math.max(0, b.mageWorth);
        towerWorth = Math.max(0, b.towerWorth);
    }

    private static double[] byLevel(double[] values) {
        if (values.length != MAX_LEVEL) {
            throw new IllegalArgumentException("A table by level needs " + MAX_LEVEL + " values, not " + values.length);
        }
        double[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] = Math.max(i == 0 ? 1.0 : copy[i - 1], Double.isFinite(copy[i]) ? copy[i] : 1.0);
        }
        return copy;
    }

    private static double[] byRank(double[] values) {
        if (values.length != Rank.COUNT) {
            throw new IllegalArgumentException("A table by rank needs " + Rank.COUNT + " values, not " + values.length);
        }
        double[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] = Math.max(0.0, Double.isFinite(copy[i]) ? copy[i] : 0.0);
        }
        return copy;
    }

    /** The highest level of a spell a guild of mages of a rank teaches. */
    public int lessonLevel(Rank guildRank) {
        return lessonLevel[guildRank.ordinal()];
    }

    /** How much stronger a spell of a level (1 to 4) is than one of the first. */
    public double levelPower(int level) {
        return levelPower[Math.max(1, Math.min(MAX_LEVEL, level)) - 1];
    }

    /**
     * What the lesson for a level of a spell costs.
     *
     * @param guest whether the one who learns is not a subject of the kingdom
     */
    public long lessonPrice(PlayerSpell spell, int level, boolean guest) {
        double price = lessonPrice(spell) * levelPrice[Math.max(1, Math.min(MAX_LEVEL, level)) - 1] * (guest ? foreignerPrice : 1.0);
        return Math.max(0, Math.min(io.github.sekerator.kingdomworld.core.economy.Money.MAX, Math.round(price)));
    }

    /** Researches the guild of mages of a kingdom of the world has from its founding. */
    public int worldResearch(Rank guildRank) {
        return worldResearch[guildRank.ordinal()];
    }

    /** Mages of a kingdom of the world, by the rank of its guild of mages. */
    public int worldMages(Rank guildRank) {
        return worldMages[guildRank.ordinal()];
    }

    /** Energy the guild of mages of a kingdom of the world holds at its founding. */
    public long worldEnergy(Rank guildRank) {
        return worldEnergy[guildRank.ordinal()];
    }

    /** Health of the body of a mage of a guild of a rank. */
    public double mageHealth(Rank guildRank) {
        return mageHealth[guildRank.ordinal()];
    }

    /** How much stronger the spells of the mages of a guild of a rank are. */
    public double mageSpellPower(Rank guildRank) {
        return Math.max(0.1, mageSpellPower[guildRank.ordinal()]);
    }

    /**
     * The rank of a guild of mages of players: its worth from researches, mages and mage towers against the table.
     */
    public Rank guildRankOf(int researches, int mages, int towers) {
        long worth = (long) researches * researchWorth + (long) mages * mageWorth + (long) towers * towerWorth;
        Rank rank = Rank.F;
        for (Rank next = rank.next().orElse(null); next != null && worth >= guildScore[next.ordinal()]; next = next.next().orElse(null)) {
            rank = next;
        }
        return rank;
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
        public int blinkPrepareSeconds = 8;
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
        public int[] lessonLevel = {1, 1, 1, 1, 1, 2, 3, 4, 4};
        public double[] levelPower = {1.0, 1.4, 1.8, 2.3};
        public double[] levelPrice = {1.0, 2.5, 6.0, 15.0};
        public double foreignerPrice = 2.0;
        public int[] worldResearch = {0, 0, 0, 0, 3, 5, 7, 8, 8};
        public int[] worldMages = {0, 0, 0, 0, 3, 5, 7, 10, 10};
        public long[] worldEnergy = {0, 0, 0, 0, 1_000, 2_000, 3_000, 4_000, 5_000};
        public double[] mageHealth = {20, 22, 25, 30, 40, 55, 75, 100, 130};
        public double[] mageSpellPower = {1.0, 1.0, 1.0, 1.0, 1.0, 1.25, 1.5, 2.0, 2.5};
        public long[] guildScore = {0, 2, 4, 7, 11, 16, 22, 30, 40};
        public int researchWorth = 3;
        public int mageWorth = 1;
        public int towerWorth = 2;

        public MagicRules build() {
            return new MagicRules(this);
        }
    }
}
