package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.magic.MagicRules;

/**
 * The magic rules of the running server, built from the server config and refreshed once per second like the other rules;
 * game tests may replace them.
 */
public final class MagicSettings {
    private static MagicRules override;
    private static MagicRules cached;

    private MagicSettings() {
    }

    public static MagicRules rules() {
        if (override != null) {
            return override;
        }
        MagicRules rules = cached;
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
    public static void override(MagicRules rules) {
        override = rules;
    }

    /**
     * The rules of the config, ready to be changed by a game test.
     */
    public static MagicRules.Builder configured() {
        MagicRules.Builder b = new MagicRules.Builder();
        KingdomWorldConfig.Magic c = KingdomWorldConfig.SERVER.magic;
        b.baseCapacity = c.baseCapacity.get();
        b.accumulatorCapacity = c.accumulatorCapacity.get();
        b.dissolveTicks = c.dissolveTicks.get();
        b.crystalsAtPost = c.crystalsAtPost.get();
        b.mageFireballCost = c.mageFireballCost.get();
        b.mageHealCost = c.mageHealCost.get();
        b.mageCastTicks = c.mageCastTicks.get();
        KingdomWorldConfig.Magic.parse(c.researchCost.get(), 8).ifPresent(table -> b.researchCost = table);
        KingdomWorldConfig.Magic.parse(c.researchMinutes.get(), 8).ifPresent(table -> b.researchMinutes = ints(table));
        KingdomWorldConfig.Magic.parse(c.spellPrice.get(), 5).ifPresent(table -> b.spellPrice = table);
        b.wardMaxChunks = c.wardMaxChunks.get();
        b.wardReduction = c.wardReduction.get();
        b.healingPerSecond = c.healingPerSecond.get().floatValue();
        b.fertilityRadius = c.fertilityRadius.get();
        b.fertilityGrowthPerSecond = c.fertilityGrowthPerSecond.get();
        b.tailwindShare = c.tailwindShare.get();
        b.maxGates = c.maxGates.get();
        KingdomWorldConfig.Magic.parse(c.spellCost.get(), 6).ifPresent(table -> b.playerCost = table);
        KingdomWorldConfig.Magic.parse(c.spellCooldownTicks.get(), 6).ifPresent(table -> b.playerCooldown = ints(table));
        KingdomWorldConfig.Magic.parseRanks(c.spellRank.get()).ifPresent(table -> b.playerRank = table);
        KingdomWorldConfig.Magic.parse(c.spellTowerLevel.get(), 6).ifPresent(table -> b.playerTowerLevel = ints(table));
        KingdomWorldConfig.Magic.parse(c.lessonPrice.get(), 6).ifPresent(table -> b.lessonPrice = table);
        b.staffSlots = c.staffSlots.get();
        b.fireballDamage = c.fireballDamage.get().floatValue();
        b.healAmount = c.healAmount.get().floatValue();
        b.shieldSeconds = c.shieldSeconds.get();
        b.lightSeconds = c.lightSeconds.get();
        b.detectRadius = c.detectRadius.get();
        b.blinkDistance = c.blinkDistance.get();
        b.blinkPrepareSeconds = c.blinkPrepareSeconds.get();
        b.feRate = c.feRate.get();
        b.feMaxPerTick = c.feMaxPerTick.get();
        b.ironsWell = c.ironsWell.get();
        b.ironsManaPerEnergy = c.ironsManaPerEnergy.get();
        b.arsWell = c.arsWell.get();
        b.arsManaPerEnergy = c.arsManaPerEnergy.get();
        b.arsSourcePerEnergy = c.arsSourcePerEnergy.get();
        b.wellRadius = c.wellRadius.get();
        b.wellIntervalTicks = c.wellIntervalTicks.get();
        b.wellMaxEnergy = c.wellMaxEnergy.get();
        KingdomWorldConfig.Magic.parse(c.lessonLevelByRank.get(), 9).ifPresent(table -> b.lessonLevel = ints(table));
        KingdomWorldConfig.Magic.parseDecimals(c.levelPower.get(), 4).ifPresent(table -> b.levelPower = table);
        KingdomWorldConfig.Magic.parseDecimals(c.levelPrice.get(), 4).ifPresent(table -> b.levelPrice = table);
        b.foreignerPrice = c.foreignerPrice.get();
        KingdomWorldConfig.Magic.parse(c.worldResearch.get(), 9).ifPresent(table -> b.worldResearch = ints(table));
        KingdomWorldConfig.Magic.parse(c.worldMages.get(), 9).ifPresent(table -> b.worldMages = ints(table));
        KingdomWorldConfig.Magic.parse(c.worldEnergy.get(), 9).ifPresent(table -> b.worldEnergy = table);
        KingdomWorldConfig.Magic.parseDecimals(c.mageHealth.get(), 9).ifPresent(table -> b.mageHealth = table);
        KingdomWorldConfig.Magic.parseDecimals(c.mageSpellPower.get(), 9).ifPresent(table -> b.mageSpellPower = table);
        KingdomWorldConfig.Magic.parse(c.guildScore.get(), 9).ifPresent(table -> b.guildScore = table);
        b.researchWorth = c.researchWorth.get();
        b.mageWorth = c.mageWorth.get();
        b.towerWorth = c.towerWorth.get();
        return b;
    }

    private static MagicRules fromConfig() {
        try {
            return configured().build();
        } catch (RuntimeException e) {
            return MagicRules.DEFAULT;
        }
    }

    private static int[] ints(long[] table) {
        int[] result = new int[table.length];
        for (int i = 0; i < table.length; i++) {
            result[i] = (int) Math.min(Integer.MAX_VALUE, table[i]);
        }
        return result;
    }
}
