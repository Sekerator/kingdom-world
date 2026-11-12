package io.github.sekerator.kingdomworld.common.army;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.Branch;
import io.github.sekerator.kingdomworld.core.army.ForceRules;
import io.github.sekerator.kingdomworld.core.army.GearChoice;
import io.github.sekerator.kingdomworld.core.army.Kit;
import io.github.sekerator.kingdomworld.core.army.Post;
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
    private static io.github.sekerator.kingdomworld.core.army.FieldRules fieldOverride;
    private static io.github.sekerator.kingdomworld.core.army.FieldRules fieldCached;
    private static ArmyRules cached;
    private static ForceRules forceOverride;
    private static ForceRules forceCached;
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

    /**
     * The rules for the army of one kingdom: a kingdom of the world keeps a garrison larger than the limits of players
     * allow.
     */
    public static ArmyRules rulesFor(net.minecraft.server.MinecraftServer server, java.util.UUID kingdomId) {
        ArmyRules rules = rules();
        return io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, kingdomId)
                ? rules.withLimits(ArmyRules.WORLD_SOLDIERS, ArmyRules.WORLD_SQUADS, ArmyRules.WORLD_SQUAD_SIZE) : rules;
    }

    /**
     * The wage of a soldier of a kingdom of the world, in thousandths of the wage of a soldier: its garrison lives in the
     * barracks and eats from the storehouses.
     */
    public static int armyWagePermille() {
        try {
            return KingdomWorldConfig.SERVER.npcKingdoms.armyWagePercent.get() * 10;
        } catch (RuntimeException e) {
            return 20;
        }
    }

    public static int garrisonWagePermille() {
        try {
            return KingdomWorldConfig.SERVER.npcKingdoms.garrisonWagePercent.get() * 10;
        } catch (RuntimeException e) {
            return 100;
        }
    }

    /**
     * How squads march, see and meet in the field.
     */
    public static io.github.sekerator.kingdomworld.core.army.FieldRules field() {
        if (fieldOverride != null) {
            return fieldOverride;
        }
        io.github.sekerator.kingdomworld.core.army.FieldRules rules = fieldCached;
        if (rules == null) {
            rules = fieldFromConfig();
            fieldCached = rules;
        }
        return rules;
    }

    /**
     * Replaces the field rules, for game tests; {@code null} restores the config.
     */
    public static void overrideField(io.github.sekerator.kingdomworld.core.army.FieldRules rules) {
        fieldOverride = rules;
    }

    private static io.github.sekerator.kingdomworld.core.army.FieldRules fieldFromConfig() {
        try {
            KingdomWorldConfig.Army c = KingdomWorldConfig.SERVER.army;
            return new io.github.sekerator.kingdomworld.core.army.FieldRules(c.marchSpeed.get(), c.roadSpeedBonus.get(), c.sightRadius.get(),
                    c.archerSightBonus.get(), c.postSightRadius.get(), c.scoutSightBonus.get(), c.fogSeconds.get(), c.lastSeenMinutes.get() * 1_200L,
                    c.spyRevealMinutes.get() * 1_200L, c.engageDistance.get(), c.fieldBattleSeconds.get(),
                    new io.github.sekerator.kingdomworld.core.combat.Flank.Rules(c.flankBonus.get(), c.rearBonus.get()));
        } catch (RuntimeException e) {
            return io.github.sekerator.kingdomworld.core.army.FieldRules.DEFAULT;
        }
    }

    /**
     * How strong the guard, the army and their officers are, when the army comes out and how the fallen are replaced.
     */
    public static ForceRules force() {
        if (forceOverride != null) {
            return forceOverride;
        }
        ForceRules rules = forceCached;
        if (rules == null) {
            rules = forceFromConfig();
            forceCached = rules;
        }
        return rules;
    }

    /**
     * Replaces the rules of the guard and the army, for game tests; {@code null} restores the config.
     */
    public static void overrideForce(ForceRules rules) {
        forceOverride = rules;
    }

    /**
     * The rules of the guard and the army of the config, ready to be changed by a game test.
     */
    public static ForceRules.Builder configuredForce() {
        ForceRules.Builder b = new ForceRules.Builder();
        KingdomWorldConfig.Force c = KingdomWorldConfig.SERVER.army.force;
        int index = 0;
        for (Branch branch : Branch.values()) {
            for (Post post : Post.values()) {
                List<? extends Double> body = c.bodies.get(index).get();
                if (body.size() == 4) {
                    b.set(branch, post, new ForceRules.Stats(number(body.get(0)), number(body.get(1)), number(body.get(2)), number(body.get(3))));
                }
                Kit.parse(c.kits.get(index).get()).ifPresent(kit -> b.set(branch, post, kit));
                index++;
            }
        }
        b.guardHealth = c.guardHealthMultiplier.get();
        b.guardDamage = c.guardDamageMultiplier.get();
        b.armyHealth = c.armyHealthMultiplier.get();
        b.armyDamage = c.armyDamageMultiplier.get();
        b.officerHealth = c.officerHealthMultiplier.get();
        b.officerDamage = c.officerDamageMultiplier.get();
        b.blockChance = c.shieldBlockChance.get();
        b.enchantedArmor = c.officerArmorEnchanted.get();
        b.viceLosses = c.viceLosses.get();
        b.viceTicks = c.viceSeconds.get() * 20L;
        b.captainLosses = c.captainLosses.get();
        b.captainTicks = c.captainSeconds.get() * 20L;
        b.armyLosses = c.armyLosses.get();
        b.armyTicks = c.armySeconds.get() * 20L;
        b.allLosses = c.allLosses.get();
        b.armyShare = c.armyFirstShare.get();
        b.officerOut = c.officerOutPercent.get() / 100.0;
        b.replacePerDay = c.replacePerDay.get();
        b.officerReplaceDays = c.officerReplaceDays.get();
        b.viceMinBranch = c.viceMinBranch.get();
        b.captainMinBranch = c.captainMinBranch.get();
        b.viceWagePermille = c.viceWagePercent.get() * 10;
        b.captainWagePermille = c.captainWagePercent.get() * 10;
        b.enchantPercent = c.kitEnchantPercent.get();
        return b;
    }

    /** A number of a list of the config, which reads whole numbers as integers. */
    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static ForceRules forceFromConfig() {
        try {
            return configuredForce().build();
        } catch (RuntimeException e) {
            return ForceRules.DEFAULT;
        }
    }

    public static void refresh() {
        fieldCached = fieldFromConfig();
        cached = fromConfig();
        forceCached = forceFromConfig();
        gear = null;
        ranged = null;
    }

    public static void reset() {
        cached = null;
        override = null;
        fieldCached = null;
        fieldOverride = null;
        forceCached = null;
        forceOverride = null;
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
