package io.github.sekerator.kingdomworld.core.combat;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * A battle of two sides far from players, by formula: who wins, who is wounded or falls, how many of a crowd are lost, who
 * retreats and how much of the spoils the winner takes. The chance to win grows with the square of the ratio of strengths
 * (an even fight is a toss of a coin, twice the strength wins four times in five); the loser suffers more. Wounds and deaths
 * of fighters go through {@link TaskRisks}, so armor and experience protect in battle as they do at work.
 *
 * <p>The same model decides the expeditions of the guild ({@link io.github.sekerator.kingdomworld.core.guild.Expeditions}),
 * the skirmishes of garrisons, the guards of caravans and later the ambushes on the roads and the wars.
 *
 * <p>Deterministic: every random number comes from the supplier, always in the same order (the result, then two numbers
 * for every fighter of the first side and of the second, then one per crowd).
 */
public final class Battle {
    /** Strength ratio at which a side counts as overwhelming; ratios are kept between its inverse and it. */
    public static final double MAX_RATIO = 10.0;

    /**
     * Which side won.
     */
    public enum Winner {
        FIRST,
        SECOND,
        /** Nobody fought. */
        NONE
    }

    /**
     * How a battle went.
     *
     * @param chance       the chance the first side had to win
     * @param fates        what happened to every fighter of both sides
     * @param firstLost    head count the crowd of the first side lost
     * @param secondLost   head count the crowd of the second side lost
     * @param firstFled    whether the first side retreated with survivors
     * @param secondFled   whether the second side retreated with survivors
     * @param lootShare    the share of the loser's spoils the winner takes: all of it when nobody of the loser is left
     */
    public record Result(Winner winner, double chance, double firstPower, double secondPower, Map<UUID, TaskRisks.Accident> fates,
                         int firstLost, int secondLost, boolean firstFled, boolean secondFled, double lootShare) {
        public static final Result NONE = new Result(Winner.NONE, 0.5, 0.0, 0.0, Map.of(), 0, 0, false, false, 0.0);

        public Result {
            Objects.requireNonNull(winner, "winner");
            fates = Collections.unmodifiableMap(new LinkedHashMap<>(fates));
        }

        public TaskRisks.Accident fate(UUID fighter) {
            return fates.getOrDefault(fighter, TaskRisks.Accident.NONE);
        }

        /**
         * Fighters of a side that fell.
         */
        public int fallen(Side side) {
            int count = 0;
            for (Fighter fighter : side.fighters()) {
                if (fate(fighter.id()) == TaskRisks.Accident.DEATH) {
                    count++;
                }
            }
            return count;
        }

        /**
         * Enemies the first side put down: fallen fighters and lost head count of the second.
         */
        public int killedByFirst(Side second) {
            return fallen(second) + secondLost;
        }

        public int killedBySecond(Side first) {
            return fallen(first) + firstLost;
        }
    }

    private Battle() {
    }

    /**
     * The chance of a side to win against another: one half for an even fight, more for the stronger side.
     */
    public static double winChance(double power, double against) {
        if (against <= 0) {
            return 1.0;
        }
        double ratio = Math.max(0.0, power) / against;
        return ratio * ratio / (ratio * ratio + 1.0);
    }

    /**
     * The risk of a fighter in a fight: the base chances of an even fight, more for a weaker side and for hard going
     * (a lost fight), much less for an overwhelming side.
     *
     * @param ratio    own strength by the enemy's
     * @param hardship 1 for a won fight, more for worse ones
     */
    public static TaskRisks.Risk casualtyRisk(double ratio, double hardship, double injury, double death) {
        double bounded = Math.max(0.1, ratio);
        return new TaskRisks.Risk(Math.min(0.9, Math.max(0.0, injury) * hardship / bounded),
                Math.min(0.5, Math.max(0.0, death) * hardship / (bounded * bounded)));
    }

    private static double ratio(double own, double enemy) {
        if (enemy <= 0) {
            return MAX_RATIO;
        }
        return Math.max(1.0 / MAX_RATIO, Math.min(MAX_RATIO, own / enemy));
    }

    /**
     * Fights a battle.
     */
    public static Result resolve(Side first, Side second, BattleRules rules, DoubleSupplier random) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(random, "random");
        boolean firstEmpty = first.isEmpty();
        boolean secondEmpty = second.isEmpty();
        if (firstEmpty && secondEmpty) {
            return Result.NONE;
        }
        double firstPower = first.power();
        double secondPower = second.power();
        if (firstEmpty || secondEmpty) {
            // Nobody to fight: the other side takes the field without a blow.
            return new Result(firstEmpty ? Winner.SECOND : Winner.FIRST, firstEmpty ? 0.0 : 1.0, firstPower, secondPower, Map.of(), 0, 0,
                    false, false, 1.0);
        }
        double chance = winChance(firstPower, secondPower);
        boolean firstWins = random.getAsDouble() < chance;
        Map<UUID, TaskRisks.Accident> fates = new LinkedHashMap<>();
        rollFates(first.fighters(), ratio(firstPower, secondPower), firstWins ? 1.0 : 2.0, rules, random, fates);
        rollFates(second.fighters(), ratio(secondPower, firstPower), firstWins ? 2.0 : 1.0, rules, random, fates);
        int firstLost = crowdLoss(first.units(), ratio(firstPower, secondPower), firstWins, random);
        int secondLost = crowdLoss(second.units(), ratio(secondPower, firstPower), !firstWins, random);
        Side loser = firstWins ? second : first;
        int loserLost = firstWins ? secondLost : firstLost;
        boolean loserLeft = loserLost < loser.units();
        for (Fighter fighter : loser.fighters()) {
            if (fates.get(fighter.id()) != TaskRisks.Accident.DEATH) {
                loserLeft = true;
                break;
            }
        }
        double loot = loserLeft ? 0.5 : 1.0;
        return new Result(firstWins ? Winner.FIRST : Winner.SECOND, chance, firstPower, secondPower, fates, firstLost, secondLost,
                !firstWins && loserLeft, firstWins && loserLeft, loot);
    }

    private static void rollFates(List<Fighter> fighters, double ratio, double hardship, BattleRules rules, DoubleSupplier random,
                                  Map<UUID, TaskRisks.Accident> fates) {
        TaskRisks.Risk risk = casualtyRisk(ratio, hardship, rules.injury(), rules.death());
        for (Fighter fighter : fighters) {
            double deathRoll = random.getAsDouble();
            double injuryRoll = random.getAsDouble();
            TaskRisks.Accident accident = TaskRisks.roll(risk, rules.riskMultiplier(), CitizenNeeds.REFERENCE_STEP_TICKS, fighter.armor(),
                    fighter.experience(), deathRoll, injuryRoll);
            if (accident == TaskRisks.Accident.NONE && fighter.wounded()) {
                // The wounded stay wounded.
                accident = TaskRisks.Accident.INJURY;
            }
            fates.put(fighter.id(), accident);
        }
    }

    /**
     * Head count a crowd loses: a winner little, less the stronger it was; a loser most of it, all of it against an
     * overwhelming enemy.
     */
    static int crowdLoss(int units, double ratio, boolean won, DoubleSupplier random) {
        if (units <= 0) {
            return 0;
        }
        double share = won ? Math.min(0.5, 0.25 / ratio) : Math.min(1.0, 0.5 + 0.25 / ratio);
        int lost = (int) Math.floor(units * share + random.getAsDouble());
        return Math.max(0, Math.min(units, lost));
    }
}
