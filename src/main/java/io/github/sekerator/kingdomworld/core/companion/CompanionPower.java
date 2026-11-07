package io.github.sekerator.kingdomworld.core.companion;

import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;
import io.github.sekerator.kingdomworld.core.combat.CombatPower;
import io.github.sekerator.kingdomworld.core.combat.Fighter;

import java.util.UUID;

/**
 * How strong a companion is and what it is worth, with the same model of strength as soldiers and adventurers: the skill of
 * fighting and the level give the standing of the fighter, the gear it carries and its health count as for a soldier, and
 * a starving companion is weaker. The worth on the market is the strength without gear, a little more for a strong back
 * and stamina.
 */
public final class CompanionPower {
    private CompanionPower() {
    }

    /**
     * The standing of a companion in the model of strength.
     */
    public static int fighterLevel(int level, Skills skills) {
        return (skills.combat() - Skills.MIN) / 2 + Math.max(0, level) / 4;
    }

    /**
     * Experience of a companion as the model counts it: a level is worth a tenth of a veteran.
     */
    static int fighterExperience(int level) {
        return Math.min(TaskRisks.VETERAN_EXPERIENCE, Math.max(0, level) * TaskRisks.VETERAN_EXPERIENCE / 10);
    }

    /**
     * Strength without gear at full health.
     */
    public static double bare(int level, Skills skills) {
        return CombatPower.of(Fighter.of(new UUID(0, 0), fighterLevel(level, skills), 0.0, 0.0f, fighterExperience(level)));
    }

    /**
     * Strength of a companion with the gear it carries.
     *
     * @param weapon      attack damage of its weapon, 0 without one
     * @param attackSpeed attacks per second of the weapon, 0 when unknown
     * @param armor       armor points it wears
     * @param toughness   armor toughness it wears
     */
    public static double of(Companion companion, double weapon, double attackSpeed, float armor, float toughness, long now, CompanionRules rules) {
        double health = companion.maxHealth() > 0 ? companion.health() / companion.maxHealth() : 0.0;
        double power = CombatPower.of(new Fighter(companion.id(), fighterLevel(companion.level(), companion.skills()), weapon, attackSpeed, armor,
                toughness, fighterExperience(companion.level()), health, companion.isWounded()));
        return companion.isStarving(now, rules) ? power * rules.starvingStrength : power;
    }

    /**
     * What a companion is worth: its bare strength, a twentieth more for every point of carrying and stamina above the lowest.
     */
    public static double worth(int level, Skills skills) {
        return bare(level, skills) * (1.0 + (skills.carrying() + skills.stamina() - 2 * Skills.MIN) / 20.0);
    }

    /**
     * What a slave costs on the market.
     */
    public static long price(int level, Skills skills, CompanionRules rules) {
        return Math.max(rules.minPrice, Math.round(worth(level, skills) * rules.pricePerPower));
    }

    /**
     * The daily wage a free companion asks.
     */
    public static long wage(int level, Skills skills, CompanionRules rules) {
        return Math.max(rules.minWage, Math.round(worth(level, skills) * rules.wagePerPower));
    }

    /**
     * What the market pays for a slave sold back: a share of its price.
     */
    public static long buyBack(int level, Skills skills, CompanionRules rules) {
        return price(level, skills, rules) * rules.sellPercent / 100;
    }

    /**
     * Base attack damage of the body: one point and a half for every point of fighting.
     */
    public static double attackDamage(Skills skills) {
        return 1.0 + skills.combat() * 0.5;
    }
}
