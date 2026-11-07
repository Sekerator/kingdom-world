package io.github.sekerator.kingdomworld.common.law;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.law.LawRules;

/**
 * The rules of the law of the running server, built from the server config and refreshed once per second like the other
 * rules; game tests may replace them.
 */
public final class LawSettings {
    private static LawRules override;
    private static LawRules cached;

    private LawSettings() {
    }

    public static LawRules rules() {
        if (override != null) {
            return override;
        }
        LawRules rules = cached;
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
    public static void override(LawRules rules) {
        override = rules;
    }

    /**
     * The rules of the config, ready to be changed by a game test.
     */
    public static LawRules.Builder configured() {
        LawRules.Builder b = new LawRules.Builder();
        KingdomWorldConfig.Law c = KingdomWorldConfig.SERVER.law;
        b.witnessRadius = c.witnessRadius.get();
        b.reportMinTicks = c.reportMinSeconds.get() * 20;
        b.reportMaxTicks = c.reportMaxSeconds.get() * 20;
        b.pettyDays = c.pettyDays.get();
        b.theftFinePercent = c.theftFinePercent.get();
        b.assaultFine = c.assaultFine.get();
        b.trespassFine = c.trespassFine.get();
        b.murderFine = c.murderFine.get();
        b.mergeTicks = c.mergeSeconds.get() * 20;
        b.bountyReward = c.bountyReward.get();
        b.bountyDays = c.bountyDays.get();
        b.ransomPercent = c.ransomPercent.get();
        b.confiscationDays = c.confiscationDays.get();
        b.debtLimit = c.debtLimit.get();
        b.hailTicks = c.hailSeconds.get() * 20;
        b.beatenHealth = c.beatenHealth.get().floatValue();
        b.beatenWeaknessTicks = c.beatenWeaknessSeconds.get() * 20;
        b.theftReputation = c.theftReputation.get();
        b.assaultReputation = c.assaultReputation.get();
        b.murderReputation = c.murderReputation.get();
        b.trespassReputation = c.trespassReputation.get();
        b.tradePerPoint = c.tradePerPoint.get();
        b.taxReputation = c.taxReputation.get();
        b.contractReputation = c.contractReputation.get();
        b.highReputation = c.highReputation.get();
        b.priceEffectPercent = c.priceEffectPercent.get();
        b.slaveryFine = c.slaveryFine.get();
        b.maxEntryFee = c.maxEntryFee.get();
        b.maxBounty = c.maxBounty.get();
        b.slaveryWarnBlocks = c.slaveryWarnBlocks.get();
        return b;
    }

    private static LawRules fromConfig() {
        try {
            return configured().build();
        } catch (IllegalStateException e) {
            // The config is not loaded yet (early in start-up, or in a test without a world).
            return LawRules.DEFAULT;
        }
    }
}
