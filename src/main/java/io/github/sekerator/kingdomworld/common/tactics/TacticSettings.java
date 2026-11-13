package io.github.sekerator.kingdomworld.common.tactics;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.tactics.TacticRules;

/**
 * The rules of the tactics of squads of the running server, built from the server config and refreshed once a second;
 * game tests may replace them.
 */
public final class TacticSettings {
    private static TacticRules override;
    private static TacticRules cached;
    private static Boolean enabledOverride;

    private TacticSettings() {
    }

    public static TacticRules rules() {
        if (override != null) {
            return override;
        }
        TacticRules rules = cached;
        if (rules == null) {
            rules = configured().build();
            cached = rules;
        }
        return rules;
    }

    /** Whether squads fight by the plans of their leaders. */
    public static boolean enabled() {
        if (enabledOverride != null) {
            return enabledOverride;
        }
        try {
            return KingdomWorldConfig.SERVER.tactics.enabled.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static void refresh() {
        try {
            cached = configured().build();
        } catch (RuntimeException e) {
            cached = TacticRules.DEFAULT;
        }
    }

    public static void reset() {
        cached = null;
        override = null;
        enabledOverride = null;
    }

    /** Replaces the configured rules, for game tests; {@code null} restores the config. */
    public static void override(TacticRules rules) {
        override = rules;
    }

    /** Switches the tactics on or off, for game tests; {@code null} restores the config. */
    public static void overrideEnabled(Boolean enabled) {
        enabledOverride = enabled;
    }

    /** The rules of the config, ready to be changed by a game test. */
    public static TacticRules.Builder configured() {
        TacticRules.Builder b = new TacticRules.Builder();
        KingdomWorldConfig.Tactics c;
        try {
            c = KingdomWorldConfig.SERVER.tactics;
            c.reactionTicks.get();
        } catch (RuntimeException e) {
            return b;
        }
        int[] ticks = KingdomWorldConfig.Tactics.parseTicks(c.decideTicks.get());
        if (ticks != null) {
            b.decideTicks = ticks;
        }
        b.reactionTicks = c.reactionTicks.get();
        b.ringRadius = c.ringRadius.get();
        b.exitRadius = c.exitRadius.get();
        b.flankRadius = c.flankRadius.get();
        b.archerMin = c.archerMin.get();
        b.archerMax = c.archerMax.get();
        b.archerTooClose = c.archerTooClose.get();
        b.fallBack = c.fallBack.get();
        b.woundedShare = c.woundedPercent.get() / 100.0;
        b.retreatLosses = c.retreatLossesPercent.get() / 100.0;
        b.retreatBalance = c.retreatBalancePercent.get() / 100.0;
        b.pincerFoes = c.pincerFoes.get();
        b.flankBonus = c.flankBonusPercent.get() / 100.0;
        b.rearBonus = c.rearBonusPercent.get() / 100.0;
        b.budgetMicros = c.budgetMicros.get();
        b.sight = c.sight.get();
        return b;
    }
}
