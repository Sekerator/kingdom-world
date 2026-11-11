package io.github.sekerator.kingdomworld.core.combat;

import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;

import java.util.List;

/**
 * How strong a fighter is: one formula for the squads of the guild, the soldiers and garrisons of the army and the
 * mercenaries. Standing, weapon, armor and experience multiply a base strength; the speed of the weapon, armor toughness,
 * health and wounds count when they are known. A fighter of level 0 with bare hands, no armor and no experience at full
 * health has {@link #BASE}.
 */
public final class CombatPower {
    /** Strength of a fighter of level 0 with bare hands, no armor and no experience. */
    public static final double BASE = 10.0;
    /** Attacks per second of a sword, the measure of the speed of weapons. */
    public static final double SWORD_SPEED = 1.6;
    /** What a wound leaves of a fighter's strength. */
    public static final double WOUNDED_SHARE = 0.5;

    private CombatPower() {
    }

    /**
     * The strength of one fighter.
     */
    public static double of(Fighter fighter) {
        double weapon = 1.0 + fighter.weapon() / 8.0;
        double armor = 1.0 + fighter.armor() / 20.0;
        double skill = 1.0 + Math.min(1.0, fighter.experience() / (double) TaskRisks.VETERAN_EXPERIENCE) * 0.5;
        // The branch and the post of a soldier make it stronger than its gear alone tells.
        double power = BASE * (fighter.level() + 1) * weapon * armor * skill * fighter.might();
        if (fighter.attackSpeed() > 0.0) {
            // Fast weapons hit more often, slow ones harder; the damage is in the weapon already, so the speed counts mildly.
            power *= Math.max(0.75, Math.min(1.5, Math.sqrt(fighter.attackSpeed() / SWORD_SPEED)));
        }
        if (fighter.toughness() > 0.0f) {
            power *= 1.0 + Math.min(20.0f, fighter.toughness()) / 40.0;
        }
        power *= 0.5 + 0.5 * fighter.health();
        if (fighter.wounded()) {
            power *= WOUNDED_SHARE;
        }
        return power;
    }

    /**
     * The strength of a group: the sum of its fighters.
     */
    public static double of(List<Fighter> fighters) {
        double sum = 0.0;
        for (Fighter fighter : fighters) {
            sum += of(fighter);
        }
        return sum;
    }
}
