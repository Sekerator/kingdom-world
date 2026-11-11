package io.github.sekerator.kingdomworld.core.combat;

import java.util.Objects;
import java.util.UUID;

/**
 * One fighter as the model of strength sees it: a soldier, an adventurer of the guild, a mercenary. Monsters and robbers
 * that are not followed one by one join a battle as a {@link Side} with a strength of their own.
 *
 * @param level       the fighter's standing: the rank of an adventurer (F is 0) or of a soldier (a recruit is 0)
 * @param weapon      attack damage of the weapon in hand (for a bow, what its arrows hit for), 0 without one
 * @param attackSpeed attacks per second of the weapon, 0 when it does not count (the guild's expeditions)
 * @param armor       armor points worn
 * @param toughness   armor toughness worn
 * @param experience  experience of the fighter
 * @param health      share of full health, from 0 to 1
 * @param wounded     whether the fighter is wounded
 * @param might       how much stronger than a plain fighter with the same gear it is: the branch and the post of a soldier;
 *                    1 for everybody else
 */
public record Fighter(UUID id, int level, double weapon, double attackSpeed, float armor, float toughness, int experience, double health,
                      boolean wounded, double might) {
    public Fighter(UUID id, int level, double weapon, double attackSpeed, float armor, float toughness, int experience, double health,
                   boolean wounded) {
        this(id, level, weapon, attackSpeed, armor, toughness, experience, health, wounded, 1.0);
    }

    public Fighter {
        Objects.requireNonNull(id, "id");
        level = Math.max(0, level);
        weapon = Double.isFinite(weapon) ? Math.max(0.0, weapon) : 0.0;
        attackSpeed = Double.isFinite(attackSpeed) ? Math.max(0.0, attackSpeed) : 0.0;
        armor = Float.isFinite(armor) ? Math.max(0.0f, armor) : 0.0f;
        toughness = Float.isFinite(toughness) ? Math.max(0.0f, toughness) : 0.0f;
        experience = Math.max(0, experience);
        health = Double.isFinite(health) ? Math.max(0.0, Math.min(1.0, health)) : 0.0;
        might = Double.isFinite(might) && might > 0.0 ? Math.min(100.0, might) : 1.0;
    }

    /**
     * The same fighter, stronger or weaker by its branch and post.
     */
    public Fighter withMight(double value) {
        return new Fighter(id, level, weapon, attackSpeed, armor, toughness, experience, health, wounded, value);
    }

    /**
     * A fighter at full health, as the guild counts its adventurers: rank, weapon, armor and experience.
     */
    public static Fighter of(UUID id, int level, double weapon, float armor, int experience) {
        return new Fighter(id, level, weapon, 0.0, armor, 0.0f, experience, 1.0, false);
    }
}
