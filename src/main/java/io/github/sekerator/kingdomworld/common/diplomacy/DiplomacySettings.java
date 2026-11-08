package io.github.sekerator.kingdomworld.common.diplomacy;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules;

/**
 * The rules of diplomacy and war of the running server, built from the server config and refreshed once per second like the
 * other rules; game tests may replace them.
 */
public final class DiplomacySettings {
    private static DiplomacyRules override;
    private static DiplomacyRules cached;
    private static Boolean peacefulOverride;

    private DiplomacySettings() {
    }

    public static DiplomacyRules rules() {
        if (override != null) {
            return override;
        }
        DiplomacyRules rules = cached;
        if (rules == null) {
            rules = fromConfig();
            cached = rules;
        }
        return rules;
    }

    /**
     * Whether players of kingdoms at peace cannot hurt each other.
     */
    public static boolean protectPeacefulPlayers() {
        return peacefulOverride != null ? peacefulOverride : KingdomWorldConfig.SERVER.war.protectPeacefulPlayers.get();
    }

    public static void refresh() {
        cached = fromConfig();
    }

    public static void reset() {
        cached = null;
        override = null;
        peacefulOverride = null;
    }

    /**
     * Replaces the configured rules, for game tests and operators; {@code null} restores the config.
     */
    public static void override(DiplomacyRules rules) {
        override = rules;
    }

    public static void overridePeaceful(Boolean value) {
        peacefulOverride = value;
    }

    static DiplomacyRules fromConfig() {
        KingdomWorldConfig.War c = KingdomWorldConfig.SERVER.war;
        DiplomacyRules d = DiplomacyRules.DEFAULT;
        try {
            return new DiplomacyRules(c.preparationMinutes.get() * 1_200L, c.maxWarDays.get(), c.pactDays.get(), c.truceDays.get(),
                    c.proposalMinutes.get() * 1_200L, c.stepSeconds.get() * 20, c.battleChance.get(), c.raidChance.get(), d.scoreBattle(),
                    d.scoreKill(), d.scorePlayerKill(), d.scoreCaravan(), d.scoreBlockade(), d.scoreHoldDay(), d.pactPenalty(),
                    c.exhaustionPerDay.get(), d.exhaustionLoss(), d.exhaustionPoor(), d.exhaustionCaravan(), c.imposeLead.get(),
                    c.tributePermille.get(), c.tributeDays.get(), c.vassalPermille.get(), c.indemnityPermille.get(), d.landChunks(),
                    c.absenceMinutes.get() * 1_200L, c.fightGraceSeconds.get() * 20L, c.npcWarDays.get(), c.npcRecentWarDays.get(),
                    c.npcWarsOnPlayers.get(), c.npcPlayerWarDays.get(), d.npcStrength(), d.npcPeaceExhaustion());
        } catch (RuntimeException e) {
            // A config that is not loaded yet (or a value out of range) leaves the defaults.
            return d;
        }
    }
}
