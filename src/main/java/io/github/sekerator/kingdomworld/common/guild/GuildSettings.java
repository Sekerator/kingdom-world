package io.github.sekerator.kingdomworld.common.guild;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.guild.GuildRules;
import io.github.sekerator.kingdomworld.core.guild.Rank;

/**
 * The guild rules of the running server, built from the server config and refreshed once per second like the other
 * rules; game tests may replace them.
 */
public final class GuildSettings {
    private static GuildRules override;
    private static GuildRules cached;

    private GuildSettings() {
    }

    public static GuildRules rules() {
        if (override != null) {
            return override;
        }
        GuildRules rules = cached;
        if (rules == null) {
            rules = fromConfig();
            cached = rules;
        }
        return rules;
    }

    public static void refresh() {
        cached = fromConfig();
    }

    public static void reset() {
        cached = null;
        override = null;
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(GuildRules rules) {
        override = rules;
    }

    /**
     * The rules of the config, ready to be changed by a game test.
     */
    public static GuildRules.Builder configured() {
        GuildRules.Builder b = new GuildRules.Builder();
        KingdomWorldConfig.Guild c = KingdomWorldConfig.SERVER.guild;
        b.healthWeight = c.healthWeight.get();
        b.damageWeight = c.damageWeight.get();
        b.armorWeight = c.armorWeight.get();
        b.speedWeight = c.speedWeight.get();
        GuildRules.parseTable(c.monsterRankScores.get()).ifPresent(table -> b.rankScores = table);
        b.bossRankBonus = c.bossRankBonus.get();
        b.bossMinRank = Rank.byId(c.bossMinRank.get()).orElse(b.bossMinRank);
        GuildRules.readOverrides(b, c.monsterRanks.get());
        GuildRules.parseTable(c.crystalEnergy.get()).ifPresent(table -> b.crystalEnergy = longs(table));
        GuildRules.parseTable(c.crystalChance.get()).ifPresent(table -> b.crystalChance = table);
        GuildRules.parseTable(c.crystalValue.get()).ifPresent(table -> b.crystalValue = longs(table));
        b.farmWindowTicks = c.farmWindowMinutes.get() * 1_200L;
        b.farmFreeKills = c.farmFreeKills.get();
        b.farmHalfKills = c.farmHalfKills.get();
        b.spawnerFactor = c.spawnerFactor.get();
        GuildRules.parseTable(c.rankPoints.get()).ifPresent(table -> b.rankPoints = longs(table));
        GuildRules.parseTable(c.killPoints.get()).ifPresent(table -> b.killPoints = ints(table));
        GuildRules.parseTable(c.examKills.get()).ifPresent(table -> b.examKills = ints(table));
        b.examTicks = c.examMinutes.get() * 1_200L;
        b.examCooldownTicks = c.examCooldownMinutes.get() * 1_200L;
        b.penaltyPercent = c.penaltyPercent.get();
        b.joinFee = c.joinFee.get();
        b.maxActiveContracts = c.maxActiveContracts.get();
        b.maxContractsPerBranch = c.maxContractsPerBranch.get();
        b.maxPostedPerPlayer = c.maxPostedPerPlayer.get();
        b.minCommissionPermille = c.minCommissionPercent.get() * 10;
        b.maxCommissionPermille = c.maxCommissionPercent.get() * 10;
        b.defaultCommissionPermille = c.defaultCommissionPercent.get() * 10;
        b.postingFeePermille = c.postingFeePermille.get();
        b.maxContractDays = c.maxContractDays.get();
        b.defaultDailyBudget = c.defaultDailyBudget.get();
        b.maxDailyBudget = c.maxDailyBudget.get();
        GuildRules.parseTable(c.autoRewardPerKill.get()).ifPresent(table -> b.autoRewardPerKill = longs(table));
        b.maxAutoContracts = c.maxAutoContracts.get();
        b.minSquad = c.minSquadSize.get();
        b.maxSquad = c.maxSquadSize.get();
        b.expeditionBaseMinutes = c.expeditionBaseMinutes.get();
        b.expeditionMinutesPerRank = c.expeditionMinutesPerRank.get();
        b.expeditionInjury = c.expeditionInjury.get();
        b.expeditionDeath = c.expeditionDeath.get();
        b.riskMultiplier = KingdomWorldConfig.SERVER.taskRiskMultiplier.get();
        return b;
    }

    private static GuildRules fromConfig() {
        try {
            return configured().build();
        } catch (RuntimeException e) {
            return GuildRules.DEFAULT;
        }
    }

    private static long[] longs(double[] table) {
        long[] result = new long[table.length];
        for (int i = 0; i < table.length; i++) {
            result[i] = Math.round(table[i]);
        }
        return result;
    }

    private static int[] ints(double[] table) {
        int[] result = new int[table.length];
        for (int i = 0; i < table.length; i++) {
            result[i] = (int) Math.min(Integer.MAX_VALUE, Math.round(table[i]));
        }
        return result;
    }
}
