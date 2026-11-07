package io.github.sekerator.kingdomworld.common.companion;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.companion.CompanionRules;
import io.github.sekerator.kingdomworld.core.guild.GuildRules;

/**
 * The rules of companions and parties of the running server, built from the server config and refreshed once per second
 * like the other rules; game tests may replace them.
 */
public final class CompanionSettings {
    private static CompanionRules override;
    private static CompanionRules cached;

    private CompanionSettings() {
    }

    public static CompanionRules rules() {
        if (override != null) {
            return override;
        }
        CompanionRules rules = cached;
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
    public static void override(CompanionRules rules) {
        override = rules;
    }

    /**
     * The rules of the config, ready to be changed by a game test.
     */
    public static CompanionRules.Builder configured() {
        CompanionRules.Builder b = new CompanionRules.Builder();
        KingdomWorldConfig.Companions c = KingdomWorldConfig.SERVER.companions;
        b.slaveOffers = c.slaveOffers.get();
        b.freeOffers = c.freeOffers.get();
        b.refreshDays = c.refreshDays.get();
        b.pricePerPower = c.pricePerPower.get();
        b.minPrice = c.minPrice.get();
        b.wagePerPower = c.wagePerPower.get();
        b.minWage = c.minWage.get();
        b.sellPercent = c.sellPercent.get();
        b.maxOfferLevel = c.maxOfferLevel.get();
        GuildRules.parseTable(c.rankLimits.get()).ifPresent(table -> {
            int[] limits = new int[table.length];
            for (int i = 0; i < table.length; i++) {
                limits[i] = (int) Math.min(64, Math.round(table[i]));
            }
            b.rankLimits = limits;
        });
        b.hungerTicks = c.hungerTicks.get();
        b.starveTicks = c.starveTicks.get();
        b.starvingStrength = c.starvingStrength.get();
        b.healTicks = c.healTicks.get();
        b.infirmaryHealTicks = c.infirmaryHealTicks.get();
        b.slaveLoyalty = c.slaveLoyalty.get();
        b.freeLoyalty = c.freeLoyalty.get();
        b.freedLoyalty = c.freedLoyalty.get();
        b.hungerLoss = c.hungerLoss.get();
        b.woundLoss = c.woundLoss.get();
        b.beatenLoss = c.beatenLoss.get();
        b.wellKeptTicks = c.wellKeptTicks.get();
        b.wellKeptCap = c.wellKeptCap.get();
        b.giftPerPoint = c.giftPerPoint.get();
        b.maxGift = c.maxGift.get();
        b.escapeBelow = c.escapeBelow.get();
        b.escapeTicks = c.escapeTicks.get();
        b.unpaidDays = c.unpaidDays.get();
        b.experiencePerLevel = c.experiencePerLevel.get();
        b.maxLevel = c.maxLevel.get();
        b.maxParty = c.maxParty.get();
        b.inviteTicks = c.inviteTicks.get();
        b.bodyRange = c.bodyRange.get();
        return b;
    }

    private static CompanionRules fromConfig() {
        try {
            return configured().build();
        } catch (RuntimeException e) {
            return CompanionRules.DEFAULT;
        }
    }
}
