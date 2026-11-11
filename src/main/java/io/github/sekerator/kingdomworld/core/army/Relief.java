package io.github.sekerator.kingdomworld.core.army;

/**
 * Who comes to an alarm of a city. The fighters of the guard answer at once. When the guard is hard pressed (soldiers out
 * of the fight, an officer out, or a fight that drags on) the next come out, step by step: the vice-captain of the guard,
 * its captain, the royal army with its vice-captain, and at last the captain of the army with everybody left. A step once
 * reached is kept until the alarm ends.
 */
public final class Relief {

    /**
     * How far the answer to an alarm has gone.
     */
    public enum Step {
        /** The fighters of the guard. */
        GUARDS,
        /** And the vice-captain of the guard. */
        GUARD_VICE,
        /** And the captain of the guard. */
        GUARD_CAPTAIN,
        /** And the vice-captain of the army with a part of its fighters. */
        ARMY,
        /** And the captain of the army with all the rest. */
        ARMY_CAPTAIN;

        public boolean reached(Step other) {
            return ordinal() >= other.ordinal();
        }

        public Step max(Step other) {
            return other != null && other.ordinal() > ordinal() ? other : this;
        }
    }

    /**
     * What is known of an alarm.
     *
     * @param guards      fighters and officers of the guard the kingdom has
     * @param losses      soldiers the offender killed or wounded since the alarm was raised
     * @param fightTicks  ticks since the first blow, 0 while nobody was struck
     * @param viceOut     whether the vice-captain of the guard who came is killed or badly wounded
     * @param captainOut  the same for the captain of the guard
     * @param armyViceOut the same for the vice-captain of the army
     */
    public record Facts(int guards, int losses, long fightTicks, boolean viceOut, boolean captainOut, boolean armyViceOut) {
    }

    private Relief() {
    }

    /**
     * The step the facts of an alarm call for.
     */
    public static Step step(Facts facts, ForceRules rules) {
        Step step = Step.GUARDS;
        if (facts.losses() >= rules.viceLosses || facts.fightTicks() >= rules.viceTicks) {
            step = Step.GUARD_VICE;
        }
        if (facts.losses() >= rules.captainLosses || facts.viceOut() || facts.fightTicks() >= rules.captainTicks) {
            step = Step.GUARD_CAPTAIN;
        }
        // A kingdom without a guard has nobody to wait for: its army answers as the guard would.
        if (facts.losses() >= rules.armyLosses || facts.captainOut() || facts.fightTicks() >= rules.armyTicks || facts.guards() <= 0) {
            step = Step.ARMY;
        }
        if (facts.armyViceOut() || facts.losses() >= rules.allLosses) {
            step = Step.ARMY_CAPTAIN;
        }
        return step;
    }

    /**
     * Whether a soldier answers an alarm that has come to a step.
     *
     * @param firstWave whether a fighter of the army belongs to the part that comes out with its vice-captain
     */
    public static boolean answers(Step step, Branch branch, Post post, boolean firstWave) {
        if (branch == Branch.GUARD) {
            return switch (post) {
                case FIGHTER -> true;
                case VICE_CAPTAIN -> step.reached(Step.GUARD_VICE);
                case CAPTAIN -> step.reached(Step.GUARD_CAPTAIN);
            };
        }
        return switch (post) {
            case FIGHTER -> step.reached(Step.ARMY_CAPTAIN) || (firstWave && step.reached(Step.ARMY));
            case VICE_CAPTAIN -> step.reached(Step.ARMY);
            case CAPTAIN -> step.reached(Step.ARMY_CAPTAIN);
        };
    }

    /**
     * How many fighters of an army come out with its vice-captain.
     */
    public static int firstWave(int fighters, ForceRules rules) {
        return fighters <= 0 ? 0 : Math.max(1, (int) Math.ceil(fighters * rules.armyShare));
    }
}
