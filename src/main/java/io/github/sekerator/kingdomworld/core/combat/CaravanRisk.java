package io.github.sekerator.kingdomworld.core.combat;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * The dangers of a caravan on the road. Something lies in wait with a chance on every journey (the server's
 * {@code caravanLossChance}); an unguarded caravan is lost to it, a guarded one is lost only if its guards lose the fight.
 * Guards therefore lower the chance of a loss by their strength against the threat.
 *
 * <p>For now the threat on the road is abstract ({@link #roadThreat}); the robbers of a later stage come in as an
 * {@link Side.Kind#BANDITS} side of their own through {@link #journey(double, Side, Side, BattleRules, DoubleSupplier)}.
 */
public final class CaravanRisk {

    /**
     * How a journey went.
     *
     * @param ambushed whether something lay in wait
     * @param lost     whether the caravan was lost
     * @param battle   the fight of the guards, {@link Battle.Result#NONE} without one
     */
    public record Journey(boolean ambushed, boolean lost, Battle.Result battle) {
        public static final Journey SAFE = new Journey(false, false, Battle.Result.NONE);

        public Journey {
            Objects.requireNonNull(battle, "battle");
        }
    }

    private CaravanRisk() {
    }

    /**
     * The abstract threat on the road: a crowd of robbers of a given strength.
     */
    public static Side roadThreat(double strength, int units) {
        return Side.crowd(Side.Kind.BANDITS, strength, Math.max(1, units));
    }

    /**
     * The chance a caravan is lost: the chance of an ambush times the chance its guards lose; without guards, the chance
     * of an ambush.
     */
    public static double lossChance(double ambushChance, double guardPower, double threatPower) {
        double ambush = Math.max(0.0, Math.min(1.0, ambushChance));
        if (guardPower <= 0) {
            return ambush;
        }
        return ambush * (1.0 - Battle.winChance(guardPower, threatPower));
    }

    /**
     * A journey: one random number decides whether something lies in wait (exactly as an unguarded caravan was lost
     * before there were guards); then the guards fight it, with the numbers of the battle.
     *
     * @param guards the guards of the caravan, {@link Side#NOBODY} without them
     * @param ambush what lies in wait
     */
    public static Journey journey(double ambushChance, Side guards, Side ambush, BattleRules rules, DoubleSupplier random) {
        if (random.getAsDouble() >= Math.max(0.0, Math.min(1.0, ambushChance))) {
            return Journey.SAFE;
        }
        if (guards.isEmpty()) {
            return new Journey(true, true, Battle.Result.NONE);
        }
        Battle.Result battle = Battle.resolve(guards, ambush, rules, random);
        return new Journey(true, battle.winner() != Battle.Winner.FIRST, battle);
    }
}
