package io.github.sekerator.kingdomworld.core.guild;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;
import io.github.sekerator.kingdomworld.core.combat.Battle;
import io.github.sekerator.kingdomworld.core.combat.CombatPower;
import io.github.sekerator.kingdomworld.core.combat.Fighter;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Expeditions of squads to contracts far away, by formula: how strong a squad is (ranks, weapons, armor, experience), how
 * hard a contract is, how long it takes and how it ends. The dangers on the way go through {@link TaskRisks}, like the
 * accidents of other work far from players, so armor and experience protect here as well. Strength, the chance to win and
 * the risk of a fight come from the model of battles the army uses too ({@link CombatPower}, {@link Battle}).
 */
public final class Expeditions {
    /** Strength of an adventurer of rank F with bare hands, no armor and no experience. */
    public static final double BASE_POWER = CombatPower.BASE;
    /** Strength of an F contract for one monster. */
    public static final double BASE_DIFFICULTY = 10.0;

    /**
     * How an expedition ended.
     */
    public enum Result {
        /** The contract is done. */
        SUCCESS,
        /** Part of the monsters killed; the contract goes back to the board. */
        PARTIAL,
        /** Nothing done. */
        FAILURE
    }

    /**
     * A member of a squad as an expedition sees it.
     *
     * @param weapon     attack damage of the weapon in hand, 0 without one
     * @param armor      armor points worn
     * @param experience the citizen's experience
     */
    public record Member(UUID id, Rank rank, double weapon, float armor, int experience) {
        public Member {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(rank, "rank");
        }

        /**
         * The member as a fighter of the model of battles: the rank is its level.
         */
        public Fighter fighter() {
            return Fighter.of(id, rank.ordinal(), weapon, armor, experience);
        }
    }

    /**
     * What happened to one member.
     */
    public record Fate(UUID id, TaskRisks.Accident accident) {
    }

    /**
     * The end of an expedition.
     *
     * @param kills monsters killed
     */
    public record Outcome(Result result, int kills, List<Fate> fates) {
    }

    private Expeditions() {
    }

    /**
     * The strength of one adventurer.
     */
    public static double power(Member member) {
        return CombatPower.of(member.fighter());
    }

    /**
     * The strength of a squad: the sum of its members.
     */
    public static double power(List<Member> members) {
        double sum = 0.0;
        for (Member member : members) {
            sum += power(member);
        }
        return sum;
    }

    /**
     * The rank of a squad: that of its best member.
     */
    public static Rank rank(List<Rank> ranks) {
        Rank best = Rank.F;
        for (Rank rank : ranks) {
            best = Rank.max(best, rank);
        }
        return best;
    }

    /**
     * How hard a contract is.
     */
    public static double difficulty(Contract contract) {
        double rank = contract.targetRank().ordinal() + 1;
        int left = Math.max(1, contract.count() - contract.progress());
        double many = contract.kind() == Contract.Kind.GATHER ? 1.0 : Math.sqrt(left);
        return BASE_DIFFICULTY * rank * many * (contract.boss() || contract.kind() == Contract.Kind.BOUNTY ? 2.0 : 1.0);
    }

    /**
     * How long an expedition to a contract takes, in ticks.
     */
    public static long duration(Contract contract, GuildRules rules) {
        long minutes = rules.expeditionBaseMinutes + (long) rules.expeditionMinutesPerRank * contract.targetRank().ordinal()
                + Math.min(60, Math.max(0, contract.count() - contract.progress()));
        return minutes * CitizenNeeds.REFERENCE_STEP_TICKS;
    }

    /**
     * The chance of a squad to finish a contract: one half for an even fight, more for a stronger squad.
     */
    public static double successChance(double power, double difficulty) {
        return Battle.winChance(power, difficulty);
    }

    /**
     * How an expedition ends.
     *
     * @param resultRoll a random number from 0 to 1 for the result
     * @param rolls      two random numbers from 0 to 1 per member, for death and for wounds
     */
    public static Outcome resolve(List<Member> members, double power, Contract contract, GuildRules rules, double resultRoll, double[] rolls) {
        double difficulty = difficulty(contract);
        double success = successChance(power, difficulty);
        Result result;
        if (resultRoll < success) {
            result = Result.SUCCESS;
        } else if (resultRoll < success + (1.0 - success) / 2.0) {
            result = Result.PARTIAL;
        } else {
            result = Result.FAILURE;
        }
        int left = Math.max(0, contract.count() - contract.progress());
        int kills = switch (result) {
            case SUCCESS -> left;
            case PARTIAL -> Math.max(0, Math.min(left - 1, Math.max(1, left / 2)));
            case FAILURE -> 0;
        };
        double ratio = difficulty <= 0 ? 10.0 : Math.max(0.1, power / difficulty);
        double hardship = switch (result) {
            case SUCCESS -> 1.0;
            case PARTIAL -> 1.5;
            case FAILURE -> 2.0;
        };
        TaskRisks.Risk risk = Battle.casualtyRisk(ratio, hardship, rules.expeditionInjury, rules.expeditionDeath);
        List<Fate> fates = new java.util.ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            double deathRoll = rolls.length > 2 * i ? rolls[2 * i] : 1.0;
            double injuryRoll = rolls.length > 2 * i + 1 ? rolls[2 * i + 1] : 1.0;
            TaskRisks.Accident accident = TaskRisks.roll(risk, rules.riskMultiplier, CitizenNeeds.REFERENCE_STEP_TICKS, member.armor(),
                    member.experience(), deathRoll, injuryRoll);
            fates.add(new Fate(member.id(), accident));
        }
        return new Outcome(result, kills, List.copyOf(fates));
    }

    /**
     * Experience each member gains.
     */
    public static int experience(Result result, Contract contract) {
        int base = 20 * (contract.targetRank().ordinal() + 1);
        return switch (result) {
            case SUCCESS -> base * 2;
            case PARTIAL -> base;
            case FAILURE -> base / 4;
        };
    }
}
