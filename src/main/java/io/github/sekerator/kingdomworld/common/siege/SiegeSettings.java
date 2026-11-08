package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;

/**
 * The rules of sieges of the running server, built from the server config and refreshed once per second like the other
 * rules; game tests may replace them.
 */
public final class SiegeSettings {
    private static SiegeRules override;
    private static SiegeRules cached;

    private SiegeSettings() {
    }

    public static SiegeRules rules() {
        if (override != null) {
            return override;
        }
        SiegeRules rules = cached;
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
     * Replaces the configured rules, for game tests and operators; {@code null} restores the config.
     */
    public static void override(SiegeRules rules) {
        override = rules;
    }

    static SiegeRules fromConfig() {
        SiegeRules d = SiegeRules.DEFAULT;
        try {
            KingdomWorldConfig.Siege c = KingdomWorldConfig.SERVER.siege;
            return new SiegeRules(c.woodGateStrength.get(), c.ironGateStrength.get(), d.soldierHit(), d.gateSlots(), d.ramHit(), d.maxRams(),
                    d.abstractShare(), c.occupyMinutes.get() * 1_200L, c.plazaMinutes.get() * 1_200L, c.throneMinutes.get() * 1_200L,
                    d.plazaRadius(), d.throneRadius(), d.closeRadius(), d.openRadius(), c.repairQuietMinutes.get() * 1_200L,
                    c.explosions.get(), c.maxBodies.get(), d.ramPlayerFactor());
        } catch (RuntimeException e) {
            // A config that is not loaded yet (or a value out of range) leaves the defaults.
            return d;
        }
    }
}
