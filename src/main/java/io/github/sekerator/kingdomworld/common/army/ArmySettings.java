package io.github.sekerator.kingdomworld.common.army;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.GearChoice;
import io.github.sekerator.kingdomworld.core.citizen.Professions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The army rules of the running server, built from the server config and refreshed once per second like the other rules;
 * game tests may replace them.
 */
public final class ArmySettings {
    private static ArmyRules override;
    private static ArmyRules cached;
    private static GearChoice.Overrides gear;
    private static List<String> ranged;

    private ArmySettings() {
    }

    public static ArmyRules rules() {
        if (override != null) {
            return override;
        }
        ArmyRules rules = cached;
        if (rules == null) {
            rules = fromConfig();
            cached = rules;
        }
        return rules;
    }

    public static void refresh() {
        cached = fromConfig();
        gear = null;
        ranged = null;
    }

    public static void reset() {
        cached = null;
        override = null;
        gear = null;
        ranged = null;
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(ArmyRules rules) {
        override = rules;
    }

    /**
     * The rules of the config, ready to be changed by a game test.
     */
    public static ArmyRules.Builder configured() {
        ArmyRules.Builder b = new ArmyRules.Builder();
        KingdomWorldConfig.Army c = KingdomWorldConfig.SERVER.army;
        b.maxSoldiers = c.maxSoldiers.get();
        b.maxSquads = c.maxSquads.get();
        b.maxSquadSize = c.maxSquadSize.get();
        b.trainingPerMinute = c.trainingPerMinute.get();
        b.trainingCap = c.trainingCap.get();
        b.barracksCapacity = c.barracksCapacity.get();
        b.battleInjury = c.battleInjury.get();
        b.battleDeath = c.battleDeath.get();
        b.riskMultiplier = KingdomWorldConfig.SERVER.taskRiskMultiplier.get();
        b.battleExperience = c.battleExperience.get();
        b.offersPerDay = c.mercenaryOffersPerDay.get();
        b.minBand = c.minBand.get();
        b.maxBand = Math.max(b.minBand, c.maxBand.get());
        b.pricePerPower = c.mercenaryPricePerPower.get();
        b.advanceDays = c.mercenaryAdvanceDays.get();
        b.maxMercenaries = c.maxMercenaries.get();
        b.skirmishChance = c.skirmishChance.get();
        b.monsterStrength = c.monsterStrength.get();
        b.caravanThreat = c.caravanThreat.get();
        b.caravanThreatUnits = c.caravanThreatUnits.get();
        b.alarmRadius = c.alarmRadius.get();
        return b;
    }

    private static ArmyRules fromConfig() {
        try {
            return configured().build();
        } catch (RuntimeException e) {
            return ArmyRules.DEFAULT;
        }
    }

    /**
     * Scores of gear the config overrides or forbids.
     */
    public static GearChoice.Overrides gear() {
        GearChoice.Overrides value = gear;
        if (value == null) {
            try {
                value = GearChoice.parseAll(KingdomWorldConfig.SERVER.army.gearScores.get());
            } catch (RuntimeException e) {
                value = GearChoice.Overrides.NONE;
            }
            gear = value;
        }
        return value;
    }

    /**
     * Items and tags ({@code #namespace:path}) archers shoot with besides bows and crossbows.
     */
    public static List<String> rangedWeapons() {
        List<String> value = ranged;
        if (value == null) {
            try {
                value = new ArrayList<>(KingdomWorldConfig.SERVER.army.rangedWeapons.get());
            } catch (RuntimeException e) {
                value = List.of("#forge:tools/bows", "#forge:tools/crossbows");
            }
            ranged = value;
        }
        return value;
    }

    /**
     * Adds the usual wage of soldiers to the wages of the economy, unless the list of wages names them already.
     */
    public static void addWages(Map<String, Long> wages) {
        long wage;
        try {
            wage = KingdomWorldConfig.SERVER.army.soldierWage.get();
        } catch (RuntimeException e) {
            wage = 15L;
        }
        wages.putIfAbsent(Professions.WARRIOR.id(), wage);
        wages.putIfAbsent(Professions.ARCHER.id(), wage);
    }
}
