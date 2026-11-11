package io.github.sekerator.kingdomworld.core.combat;

/**
 * A fight of one player against one soldier, reckoned by the game's own formulas of armor and Protection. It tells how
 * strong the guards, the army and their officers are against a player in a given gear, and pins that down for tests.
 *
 * <p>The player of the model is a capable one: fed (a full stomach heals {@link #SATURATION_HEALTH} during the fight on
 * top of the {@link #PLAYER_HEALTH} of a player), striking with a sword as fast as it recovers and landing every blow as a
 * critical one. The soldier strikes once a second, as the bodies of soldiers do, and wards off a share of the blows with
 * its shield. Potions, golden apples, a shield of the player and help of others are outside the model: they are what
 * "may win" leaves to the player.
 */
public final class Duel {
    /** Health of a player. */
    public static final double PLAYER_HEALTH = 20.0;
    /** Health a full stomach gives back during a fight. */
    public static final double SATURATION_HEALTH = 13.0;
    /** What a critical blow multiplies the damage of a weapon by. */
    public static final double CRITICAL = 1.5;
    /** Seconds between two blows of a sword at full strength. */
    public static final double PLAYER_SECONDS = 0.625;
    /** Seconds between two blows of a soldier. */
    public static final double SOLDIER_SECONDS = 1.0;

    /**
     * One side of the fight.
     *
     * @param damage     damage of one blow before the armor of the other
     * @param armor      armor points
     * @param toughness  armor toughness
     * @param protection the sum of the levels of Protection on the armor
     * @param block      share of the blows a shield wards off, from 0 to 1
     */
    public record Combatant(double health, double damage, double armor, double toughness, int protection, double block) {
        public Combatant {
            if (!(health > 0) || !(damage >= 0) || !(armor >= 0) || !(toughness >= 0) || protection < 0 || !(block >= 0 && block < 1)) {
                throw new IllegalArgumentException("Combatant out of range");
            }
        }
    }

    /**
     * How the fight ends.
     *
     * @param killSeconds seconds until the player has killed the soldier
     * @param dieSeconds  seconds until the soldier has killed the player
     */
    public record Result(double killSeconds, double dieSeconds) {
        public boolean playerWins() {
            return killSeconds < dieSeconds;
        }
    }

    private Duel() {
    }

    /**
     * Damage left after armor, by the formula of the game.
     */
    public static double afterArmor(double damage, double armor, double toughness) {
        double reach = 2.0 + toughness / 4.0;
        double defense = Math.max(armor * 0.2, Math.min(20.0, armor - damage / reach));
        return damage * (1.0 - defense / 25.0);
    }

    /**
     * Damage left after Protection, by the formula of the game.
     */
    public static double afterProtection(double damage, int protection) {
        return damage * (1.0 - Math.min(20, Math.max(0, protection)) / 25.0);
    }

    /**
     * What a blow takes from the health of its target on average.
     */
    public static double blow(double damage, Combatant target) {
        return afterProtection(afterArmor(damage, target.armor(), target.toughness()), target.protection()) * (1.0 - target.block());
    }

    /**
     * A capable player with a weapon and armor.
     *
     * @param weapon damage of the weapon in hand at full strength, before the critical blow
     */
    public static Combatant player(double weapon, double armor, double toughness, int protection) {
        return new Combatant(PLAYER_HEALTH + SATURATION_HEALTH, weapon * CRITICAL, armor, toughness, protection, 0.0);
    }

    /**
     * Lets a player and a soldier fight to the end. The player strikes first.
     */
    public static Result fight(Combatant player, Combatant soldier) {
        double dealt = blow(player.damage(), soldier);
        double taken = blow(soldier.damage(), player);
        double kill = dealt <= 0 ? Double.POSITIVE_INFINITY : (Math.ceil(soldier.health() / dealt) - 1) * PLAYER_SECONDS;
        double die = taken <= 0 ? Double.POSITIVE_INFINITY : Math.ceil(player.health() / taken) * SOLDIER_SECONDS;
        return new Result(kill, die);
    }
}
