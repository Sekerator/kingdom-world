package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.combat.Fighter;

import java.util.Objects;
import java.util.UUID;

/**
 * A mercenary: a fighter with gear of its own, not a citizen. It needs no food, bed or mood, only its pay; what it carries is
 * its own and never falls into the world.
 *
 * @param level       its rank as a soldier's (0 is a recruit)
 * @param weapon      item id of its weapon, for the screens
 * @param damage      attack damage of the weapon
 * @param attackSpeed attacks per second of the weapon
 * @param ranged      whether it shoots
 * @param armor       armor points worn
 * @param toughness   armor toughness worn
 * @param health      share of full health, from 0 to 1
 */
public record Mercenary(UUID id, int nameSeed, boolean female, int level, String weapon, double damage, double attackSpeed, boolean ranged,
                        float armor, float toughness, int experience, double health) {
    /** Longest item id of a weapon. */
    public static final int MAX_ITEM = 128;

    public Mercenary {
        Objects.requireNonNull(id, "id");
        weapon = weapon == null ? "" : weapon;
        if (level < 0 || level >= SoldierRank.COUNT || weapon.length() > MAX_ITEM || !Double.isFinite(damage) || damage < 0
                || !Double.isFinite(attackSpeed) || attackSpeed < 0 || !Float.isFinite(armor) || armor < 0 || !Float.isFinite(toughness)
                || toughness < 0 || experience < 0 || !Double.isFinite(health) || health < 0 || health > 1) {
            throw new IllegalArgumentException("Mercenary out of range: " + id);
        }
    }

    public SoldierRank rank() {
        return SoldierRank.ofIndex(level);
    }

    public boolean wounded() {
        return health < 0.5;
    }

    public Fighter fighter() {
        return new Fighter(id, level, damage, attackSpeed, armor, toughness, experience, health, wounded());
    }

    public Mercenary withHealth(double value) {
        return new Mercenary(id, nameSeed, female, level, weapon, damage, attackSpeed, ranged, armor, toughness, experience,
                Math.max(0.0, Math.min(1.0, value)));
    }

    public Mercenary withExperience(int more) {
        int next = (int) Math.min(Integer.MAX_VALUE, (long) experience + Math.max(0, more));
        int reached = Math.max(level, SoldierRank.of(next).ordinal());
        return new Mercenary(id, nameSeed, female, reached, weapon, damage, attackSpeed, ranged, armor, toughness, next, health);
    }
}
