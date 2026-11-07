package io.github.sekerator.kingdomworld.core.citizen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Accidents at work far from players. Near players the dangers are real (monsters in the mine, falls, lava,
 * water); far from them a worker has a small chance per step to be wounded and a smaller one to die, by
 * profession. Armor and experience lower both.
 */
public final class TaskRisks {
    /** Armor points (a full iron set has 15) at which armor gives its full protection. */
    public static final float FULL_ARMOR = 20.0f;
    /** Share of the risk full armor takes away. */
    public static final double ARMOR_PROTECTION = 0.75;
    /** Experience at which a worker is as careful as it gets. */
    public static final int VETERAN_EXPERIENCE = 1_000;
    /** Share of the risk full experience takes away. */
    public static final double EXPERIENCE_PROTECTION = 0.5;
    /** Health a wounded worker is left with, as a share of the wounded threshold. */
    public static final float INJURY_HEALTH_SHARE = 0.4f;

    /**
     * The risk of a profession per minute of work.
     *
     * @param injury chance to be wounded
     * @param death  chance to die
     */
    public record Risk(double injury, double death) {
        public static final Risk NONE = new Risk(0.0, 0.0);

        public Risk {
            if (!(injury >= 0.0 && injury <= 1.0) || !(death >= 0.0 && death <= 1.0)) {
                throw new IllegalArgumentException("Risk chances must lie between 0 and 1");
            }
        }
    }

    /** Defaults per profession: mining is the most dangerous work, carrying and farming the safest. */
    public static final Map<String, Risk> DEFAULTS = defaults();

    public enum Accident {
        NONE,
        INJURY,
        DEATH
    }

    private TaskRisks() {
    }

    private static Map<String, Risk> defaults() {
        Map<String, Risk> risks = new LinkedHashMap<>();
        risks.put(Professions.MINER.id(), new Risk(0.004, 0.0004));
        risks.put(Professions.LUMBERJACK.id(), new Risk(0.003, 0.0002));
        risks.put(Professions.HERDER.id(), new Risk(0.0015, 0.0001));
        risks.put(Professions.FISHER.id(), new Risk(0.001, 0.0001));
        risks.put(Professions.FARMER.id(), new Risk(0.0005, 0.0));
        risks.put(Professions.CARRIER.id(), new Risk(0.0005, 0.0));
        risks.put(Professions.SMELTER.id(), new Risk(0.0005, 0.0));
        risks.put(Professions.SMITH.id(), new Risk(0.0004, 0.0));
        risks.put(Professions.COOK.id(), new Risk(0.0003, 0.0));
        risks.put(Professions.CRAFTER.id(), new Risk(0.0002, 0.0));
        // Builders work on walls and roofs; a fall is rare and seldom deadly.
        risks.put(Professions.BUILDER.id(), new Risk(0.001, 0.00005));
        return java.util.Collections.unmodifiableMap(risks);
    }

    /**
     * Factor by which armor and experience lower a risk, from 1 (no protection) down.
     */
    public static double protection(float armorPoints, int experience) {
        double armor = Math.max(0.0, Math.min(1.0, armorPoints / FULL_ARMOR)) * ARMOR_PROTECTION;
        double skill = Math.max(0.0, Math.min(1.0, experience / (double) VETERAN_EXPERIENCE)) * EXPERIENCE_PROTECTION;
        return (1.0 - armor) * (1.0 - skill);
    }

    /**
     * Chance of an event over a step, given its chance per minute (one {@link CitizenNeeds#REFERENCE_STEP_TICKS}).
     */
    public static double overStep(double perMinute, int intervalTicks) {
        double p = Math.max(0.0, Math.min(1.0, perMinute));
        if (p >= 1.0) {
            return 1.0;
        }
        return 1.0 - Math.pow(1.0 - p, intervalTicks / (double) CitizenNeeds.REFERENCE_STEP_TICKS);
    }

    /**
     * What happens to a worker during one step of work far from players.
     *
     * @param multiplier the server's {@code taskRiskMultiplier}
     * @param deathRoll  a random number from 0 to 1
     * @param injuryRoll another random number from 0 to 1
     */
    public static Accident roll(Risk risk, double multiplier, int intervalTicks, float armorPoints, int experience,
                                double deathRoll, double injuryRoll) {
        double factor = Math.max(0.0, multiplier) * protection(armorPoints, experience);
        if (factor <= 0.0) {
            return Accident.NONE;
        }
        if (deathRoll < overStep(risk.death() * factor, intervalTicks)) {
            return Accident.DEATH;
        }
        if (injuryRoll < overStep(risk.injury() * factor, intervalTicks)) {
            return Accident.INJURY;
        }
        return Accident.NONE;
    }

    /**
     * Health of a worker after an injury: well below the wounded threshold, so that it is out of work for a while.
     */
    public static float injuredHealth(float health, float woundedFraction) {
        float target = Citizen.MAX_HEALTH * woundedFraction * INJURY_HEALTH_SHARE;
        return Math.max(1.0f, Math.min(health, target));
    }

    /**
     * Reads a risk setting of the form {@code profession:injury:death}, for example {@code miner:0.004:0.0004}.
     */
    public static Optional<Map.Entry<String, Risk>> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String[] parts = text.trim().split(":");
        if (parts.length != 3 || !Profession.isValidId(parts[0])) {
            return Optional.empty();
        }
        try {
            double injury = Double.parseDouble(parts[1]);
            double death = Double.parseDouble(parts[2]);
            return Optional.of(Map.entry(parts[0], new Risk(injury, death)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * The risks of a list of settings; unreadable entries are skipped, professions without an entry have no risk.
     */
    public static Map<String, Risk> parseAll(List<? extends String> settings) {
        Map<String, Risk> result = new LinkedHashMap<>();
        for (String setting : settings) {
            parse(setting).ifPresent(entry -> result.put(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * The defaults as settings, for the server config.
     */
    public static List<String> defaultSettings() {
        return DEFAULTS.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + java.math.BigDecimal.valueOf(entry.getValue().injury()).toPlainString()
                        + ":" + java.math.BigDecimal.valueOf(entry.getValue().death()).toPlainString())
                .toList();
    }
}
