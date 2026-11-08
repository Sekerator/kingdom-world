package io.github.sekerator.kingdomworld.common.nobility;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.nobility.NobilityRules;

/**
 * The rules of the nobility of the running server, built from the server config and refreshed now and then like the other
 * rules; game tests may replace them.
 */
public final class NobilitySettings {
    private static NobilityRules override;
    private static NobilityRules cached;

    private NobilitySettings() {
    }

    public static NobilityRules rules() {
        if (override != null) {
            return override;
        }
        NobilityRules rules = cached;
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
    public static void override(NobilityRules rules) {
        override = rules;
    }

    static NobilityRules fromConfig() {
        NobilityRules d = NobilityRules.DEFAULT;
        try {
            KingdomWorldConfig.Nobility c = KingdomWorldConfig.SERVER.nobility;
            return new NobilityRules(c.shareBaron.get(), c.shareCount.get(), c.shareDuke.get(), c.demandEveryDays.get(), c.demandDueDays.get(),
                    c.demandValue.get(), c.plotBelow.get(), c.plotChance.get(), c.plotDays.get(), c.secessionWarnDays.get(), c.secessionDays.get(),
                    c.rebelDays.get(), c.rebelPercent.get(), c.spyHire.get(), c.spyWage.get(), c.maxSpies.get(), c.npcMaxSpies.get(),
                    c.theftPercent.get(), c.theftMax.get(), c.ransom.get(), c.ransomDays.get(), d.counterBase(), d.caughtChance(),
                    c.marriageChance.get(), d.deathChance(), d.grantDealings(), d.grantReputation(), d.highStability(), d.moodLow(), d.moodHigh(),
                    c.maxHouses.get());
        } catch (RuntimeException e) {
            // A config that is not loaded yet (or a value out of range) leaves the defaults.
            return d;
        }
    }
}
