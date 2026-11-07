package io.github.sekerator.kingdomworld.core.guild;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The settings of the adventurers' guild: how monsters are ranked, what crystals are worth, how farms are told apart,
 * how many points the ranks need, and the limits of contracts and squads. Built from the server config; the defaults
 * are the defaults of a new config. Tables by rank hold one value per {@link Rank}, from F to SSS.
 */
public final class GuildRules {
    // ---------------------------------------------------------------- monsters
    /** Weight of the maximum health in the strength of a monster. */
    public final double healthWeight;
    /** Weight of the attack damage. */
    public final double damageWeight;
    /** Weight of the armor points. */
    public final double armorWeight;
    /** Weight of the movement speed (a zombie has 0.23). */
    public final double speedWeight;
    /** The strength a monster needs for each rank; F needs 0. */
    private final double[] rankScores;
    /** Ranks a boss stands above what its strength alone gives. */
    public final int bossRankBonus;
    /** The lowest rank of a boss. */
    public final Rank bossMinRank;
    /** Ranks of monsters given by their id, ahead of everything else. */
    public final Map<String, Rank> idOverrides;
    /** Ranks of monsters given by an entity tag (without {@code #}), ahead of the strength. */
    public final Map<String, Rank> tagOverrides;

    // ---------------------------------------------------------------- crystals
    private final long[] crystalEnergy;
    private final double[] crystalChance;
    private final long[] crystalValue;

    // ---------------------------------------------------------------- farms
    /** How long kills in one place are remembered, in ticks. */
    public final long farmWindowTicks;
    /** Kills in one place within the window before the chance and the points start to fall. */
    public final int farmFreeKills;
    /** Each so many further kills halve the chance and the points. */
    public final int farmHalfKills;
    /** Share of the chance and the points a monster from a spawner gives, before the falling. */
    public final double spawnerFactor;

    // ---------------------------------------------------------------- ranks
    private final long[] rankPoints;
    private final int[] killPoints;
    private final int[] examKills;
    /** Time an exam may take, in ticks. */
    public final long examTicks;
    /** Time after a failed exam before the next try, in ticks. */
    public final long examCooldownTicks;
    /** Share of a contract's points an adventurer loses for failing or abandoning it, in percent. */
    public final int penaltyPercent;
    /** What joining the guild costs, paid to the treasury of the branch's kingdom; 0 is free. */
    public final long joinFee;

    // ---------------------------------------------------------------- contracts
    /** Contracts one player may hold at the same time. */
    public final int maxActiveContracts;
    /** Open contracts one branch may have on its board. */
    public final int maxContractsPerBranch;
    /** Contracts one player may have posted at the same time. */
    public final int maxPostedPerPlayer;
    public final int minCommissionPermille;
    public final int maxCommissionPermille;
    public final int defaultCommissionPermille;
    /** What a player pays the branch for posting a contract, in permille of the reward; at least one copper. */
    public final int postingFeePermille;
    /** Longest time a contract may run, in game days. */
    public final int maxContractDays;
    public final long defaultDailyBudget;
    public final long maxDailyBudget;
    private final long[] autoRewardPerKill;
    /** Contracts of the guild itself one branch keeps open at most. */
    public final int maxAutoContracts;

    // ---------------------------------------------------------------- squads
    public final int minSquad;
    public final int maxSquad;
    /** Minutes an expedition takes at least. */
    public final int expeditionBaseMinutes;
    /** Further minutes for each rank of the contract above F. */
    public final int expeditionMinutesPerRank;
    /** Chance of a member to be wounded on an even expedition. */
    public final double expeditionInjury;
    /** Chance of a member to die on an even expedition. */
    public final double expeditionDeath;
    /** The server's {@code taskRiskMultiplier}: 0 turns deaths and wounds on expeditions off. */
    public final double riskMultiplier;

    public static final GuildRules DEFAULT = new Builder().build();

    private GuildRules(Builder b) {
        healthWeight = b.healthWeight;
        damageWeight = b.damageWeight;
        armorWeight = b.armorWeight;
        speedWeight = b.speedWeight;
        rankScores = table(b.rankScores);
        bossRankBonus = b.bossRankBonus;
        bossMinRank = b.bossMinRank;
        idOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(b.idOverrides));
        tagOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(b.tagOverrides));
        crystalEnergy = table(b.crystalEnergy);
        crystalChance = table(b.crystalChance);
        crystalValue = table(b.crystalValue);
        farmWindowTicks = Math.max(1, b.farmWindowTicks);
        farmFreeKills = Math.max(0, b.farmFreeKills);
        farmHalfKills = Math.max(1, b.farmHalfKills);
        spawnerFactor = Math.max(0.0, Math.min(1.0, b.spawnerFactor));
        rankPoints = table(b.rankPoints);
        killPoints = table(b.killPoints);
        examKills = table(b.examKills);
        examTicks = Math.max(1, b.examTicks);
        examCooldownTicks = Math.max(0, b.examCooldownTicks);
        penaltyPercent = Math.max(0, Math.min(100, b.penaltyPercent));
        joinFee = Math.max(0, b.joinFee);
        maxActiveContracts = Math.max(1, b.maxActiveContracts);
        maxContractsPerBranch = Math.max(1, b.maxContractsPerBranch);
        maxPostedPerPlayer = Math.max(0, b.maxPostedPerPlayer);
        minCommissionPermille = Math.max(0, Math.min(1000, b.minCommissionPermille));
        maxCommissionPermille = Math.max(minCommissionPermille, Math.min(1000, b.maxCommissionPermille));
        defaultCommissionPermille = Math.max(minCommissionPermille, Math.min(maxCommissionPermille, b.defaultCommissionPermille));
        postingFeePermille = Math.max(0, Math.min(1000, b.postingFeePermille));
        maxContractDays = Math.max(1, b.maxContractDays);
        maxDailyBudget = Math.max(0, b.maxDailyBudget);
        defaultDailyBudget = Math.max(0, Math.min(maxDailyBudget, b.defaultDailyBudget));
        autoRewardPerKill = table(b.autoRewardPerKill);
        maxAutoContracts = Math.max(0, b.maxAutoContracts);
        minSquad = Math.max(1, b.minSquad);
        maxSquad = Math.max(minSquad, b.maxSquad);
        expeditionBaseMinutes = Math.max(1, b.expeditionBaseMinutes);
        expeditionMinutesPerRank = Math.max(0, b.expeditionMinutesPerRank);
        expeditionInjury = Math.max(0.0, Math.min(1.0, b.expeditionInjury));
        expeditionDeath = Math.max(0.0, Math.min(1.0, b.expeditionDeath));
        riskMultiplier = Math.max(0.0, b.riskMultiplier);
    }

    private static double[] table(double[] values) {
        if (values.length != Rank.COUNT) {
            throw new IllegalArgumentException("A table by rank needs " + Rank.COUNT + " values, not " + values.length);
        }
        return values.clone();
    }

    private static long[] table(long[] values) {
        if (values.length != Rank.COUNT) {
            throw new IllegalArgumentException("A table by rank needs " + Rank.COUNT + " values, not " + values.length);
        }
        return values.clone();
    }

    private static int[] table(int[] values) {
        if (values.length != Rank.COUNT) {
            throw new IllegalArgumentException("A table by rank needs " + Rank.COUNT + " values, not " + values.length);
        }
        return values.clone();
    }

    /** The strength a monster needs for a rank. */
    public double rankScore(Rank rank) {
        return rankScores[rank.ordinal()];
    }

    /** The energy of a crystal of a rank. */
    public long crystalEnergy(Rank rank) {
        return crystalEnergy[rank.ordinal()];
    }

    /** The chance a monster of a rank leaves a crystal, from 0 to 1. */
    public double crystalChance(Rank rank) {
        return Math.max(0.0, Math.min(1.0, crystalChance[rank.ordinal()]));
    }

    /** The worth of a crystal of a rank, in copper coins of a currency with the default coin values. */
    public long crystalValue(Rank rank) {
        return crystalValue[rank.ordinal()];
    }

    /** The points an adventurer needs for a rank (to take its exam); F needs none. */
    public long rankPoints(Rank rank) {
        return rankPoints[rank.ordinal()];
    }

    /** The points for killing a monster of a rank. */
    public int killPoints(Rank rank) {
        return killPoints[rank.ordinal()];
    }

    /** Monsters the exam for a rank asks for. */
    public int examKills(Rank rank) {
        return Math.max(1, examKills[rank.ordinal()]);
    }

    /** What the guild pays per monster of a rank in its own contracts. */
    public long autoRewardPerKill(Rank rank) {
        return autoRewardPerKill[rank.ordinal()];
    }

    /**
     * Whether the exam for a rank asks for a boss rather than for monsters of the rank.
     */
    public boolean examNeedsBoss(Rank rank) {
        return rank.atLeast(Rank.S);
    }

    // ---------------------------------------------------------------- reading settings

    /**
     * Reads a table by rank written as nine numbers separated by commas or spaces; empty if it does not hold nine numbers.
     */
    public static Optional<double[]> parseTable(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String[] parts = text.strip().split("[,;\\s]+");
        if (parts.length != Rank.COUNT) {
            return Optional.empty();
        }
        double[] values = new double[Rank.COUNT];
        try {
            for (int i = 0; i < parts.length; i++) {
                values[i] = Double.parseDouble(parts[i]);
                if (!Double.isFinite(values[i]) || values[i] < 0) {
                    return Optional.empty();
                }
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(values);
    }

    /**
     * Writes a table by rank for the config.
     */
    public static String formatTable(double[] values) {
        StringBuilder text = new StringBuilder();
        for (double value : values) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(value == Math.rint(value) && Math.abs(value) < 1e15 ? Long.toString((long) value)
                    : java.math.BigDecimal.valueOf(value).toPlainString());
        }
        return text.toString();
    }

    public static String formatTable(long[] values) {
        return formatTable(Arrays.stream(values).asDoubleStream().toArray());
    }

    public static String formatTable(int[] values) {
        return formatTable(Arrays.stream(values).asDoubleStream().toArray());
    }

    /**
     * Reads one override of the rank of monsters, {@code id=rank} or {@code #tag=rank}.
     */
    public static Optional<Map.Entry<String, Rank>> parseOverride(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String[] sides = text.strip().split("=");
        if (sides.length != 2 || sides[0].isBlank()) {
            return Optional.empty();
        }
        return Rank.byId(sides[1]).map(rank -> Map.entry(sides[0].strip(), rank));
    }

    /**
     * Fills the overrides of a builder from settings; unreadable entries are skipped.
     */
    public static void readOverrides(Builder builder, List<? extends String> settings) {
        builder.idOverrides.clear();
        builder.tagOverrides.clear();
        for (String setting : settings) {
            parseOverride(setting).ifPresent(entry -> {
                if (entry.getKey().startsWith("#")) {
                    builder.tagOverrides.put(entry.getKey().substring(1), entry.getValue());
                } else {
                    builder.idOverrides.put(entry.getKey(), entry.getValue());
                }
            });
        }
    }

    /**
     * Settings with mutable fields, filled from the config or changed by tests.
     */
    public static final class Builder {
        public double healthWeight = 1.0;
        public double damageWeight = 4.0;
        public double armorWeight = 2.0;
        public double speedWeight = 20.0;
        public double[] rankScores = {0, 25, 45, 70, 120, 250, 500, 1000, 2000};
        public int bossRankBonus = 2;
        public Rank bossMinRank = Rank.A;
        public final Map<String, Rank> idOverrides = new LinkedHashMap<>(Map.of(
                "minecraft:ender_dragon", Rank.SS, "minecraft:wither", Rank.SS, "minecraft:warden", Rank.S,
                "minecraft:elder_guardian", Rank.B));
        public final Map<String, Rank> tagOverrides = new LinkedHashMap<>();
        public long[] crystalEnergy = {10, 30, 100, 300, 1_000, 3_000, 10_000, 30_000, 100_000};
        public double[] crystalChance = {0.05, 0.08, 0.12, 0.2, 0.35, 0.5, 1.0, 1.0, 1.0};
        public long[] crystalValue = {2, 6, 20, 60, 200, 600, 2_000, 6_000, 20_000};
        public long farmWindowTicks = 12_000;
        public int farmFreeKills = 12;
        public int farmHalfKills = 8;
        public double spawnerFactor = 0.5;
        public long[] rankPoints = {0, 30, 100, 300, 800, 2_000, 5_000, 12_000, 30_000};
        public int[] killPoints = {1, 2, 4, 8, 16, 32, 64, 128, 256};
        public int[] examKills = {1, 5, 5, 4, 3, 1, 1, 1, 1};
        public long examTicks = 72_000;
        public long examCooldownTicks = 24_000;
        public int penaltyPercent = 50;
        public long joinFee = 0;
        public int maxActiveContracts = 3;
        public int maxContractsPerBranch = 32;
        public int maxPostedPerPlayer = 5;
        public int minCommissionPermille = 0;
        public int maxCommissionPermille = 300;
        public int defaultCommissionPermille = 100;
        public int postingFeePermille = 20;
        public int maxContractDays = 7;
        public long defaultDailyBudget = 50;
        public long maxDailyBudget = 100_000;
        public long[] autoRewardPerKill = {1, 2, 5, 12, 30, 80, 200, 500, 1_500};
        public int maxAutoContracts = 3;
        public int minSquad = 2;
        public int maxSquad = 5;
        public int expeditionBaseMinutes = 10;
        public int expeditionMinutesPerRank = 5;
        public double expeditionInjury = 0.25;
        public double expeditionDeath = 0.04;
        public double riskMultiplier = 1.0;

        public GuildRules build() {
            return new GuildRules(this);
        }
    }
}
