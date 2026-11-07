package io.github.sekerator.kingdomworld.core.road;

import io.github.sekerator.kingdomworld.core.combat.Battle;
import io.github.sekerator.kingdomworld.core.combat.BattleRules;
import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.trade.Goods;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Robbers on the roads. A caravan far from players may be fallen upon on every piece of its way near a camp: the chance
 * grows with the robbers of the camp and falls with the distance to it, with a better road, on the land of a kingdom and
 * near its garrisons and patrols. The guards of the caravan fight the robbers by the numbers of {@link Battle}; without
 * guards, or if the guards lose, the robbers take some of the goods or all of them. Camps nobody disturbs grow.
 */
public final class Ambushes {

    /**
     * How an ambush went.
     *
     * @param happened whether the robbers fell upon the caravan
     * @param robbed   whether they took goods
     * @param share    the share of the goods they took
     * @param battle   the fight of the guards, {@link Battle.Result#NONE} without one
     */
    public record Ambush(boolean happened, boolean robbed, double share, Battle.Result battle) {
        public static final Ambush NONE = new Ambush(false, false, 0.0, Battle.Result.NONE);

        public Ambush {
            Objects.requireNonNull(battle, "battle");
        }
    }

    /**
     * Goods split in two.
     */
    public record Split(List<Goods> taken, List<Goods> kept) {
        public Split {
            taken = List.copyOf(taken);
            kept = List.copyOf(kept);
        }

        public long takenItems() {
            return items(taken);
        }

        public long keptItems() {
            return items(kept);
        }
    }

    private Ambushes() {
    }

    public static long items(List<Goods> goods) {
        long sum = 0;
        for (Goods entry : goods) {
            sum += entry.count();
        }
        return sum;
    }

    /**
     * How much a camp threatens a place: from 0 to 1, more with more robbers and less far from the camp.
     */
    public static double campFactor(int size, double distance, BanditRules rules) {
        if (size <= 0 || distance >= rules.campReach()) {
            return 0.0;
        }
        double strength = Math.sqrt(Math.min(1.0, size / (double) Math.max(1, rules.maxBandits())));
        return strength * (1.0 - Math.max(0.0, distance) / rules.campReach());
    }

    /**
     * How much the protection of a place lowers the chance of an ambush there.
     */
    public static double protection(int garrisons, int patrols, boolean ownLand, BanditRules rules) {
        double factor = Math.pow(rules.garrisonFactor(), Math.max(0, garrisons)) * Math.pow(rules.patrolFactor(), Math.max(0, patrols));
        return ownLand ? factor * rules.ownLandFactor() : factor;
    }

    /**
     * The chance of an ambush on a piece of way.
     *
     * @param length     blocks of the piece
     * @param roadClass  the road, or {@code null} across country
     * @param camp       the threat of the nearest camp, from {@link #campFactor}
     * @param protection from {@link #protection}
     */
    public static double chance(double length, RoadClass roadClass, double camp, double protection, BanditRules rules) {
        if (!rules.enabled() || camp <= 0 || length <= 0) {
            return 0.0;
        }
        double per100 = rules.ambushChance() * (roadClass == null ? 1.0 : rules.classFactor(roadClass)) * Math.min(1.0, camp)
                * Math.max(0.0, Math.min(1.0, protection));
        per100 = Math.max(0.0, Math.min(1.0, per100));
        return 1.0 - Math.pow(1.0 - per100, length / 100.0);
    }

    /**
     * The chance that a caravan is robbed on a piece of way: the chance of an ambush times the chance its guards lose.
     */
    public static double robbery(double ambush, double guardPower, double banditPower) {
        if (guardPower <= 0) {
            return ambush;
        }
        return ambush * (1.0 - Battle.winChance(guardPower, banditPower));
    }

    /**
     * Whether robbers fall upon a caravan on a piece of way, and how it goes. One random number decides the ambush, then
     * the battle takes its numbers.
     */
    public static Ambush resolve(double chance, Side guards, Side bandits, BanditRules rules, BattleRules battleRules, DoubleSupplier random) {
        if (random.getAsDouble() >= Math.max(0.0, Math.min(1.0, chance)) || bandits.isEmpty()) {
            return Ambush.NONE;
        }
        if (guards.isEmpty()) {
            return new Ambush(true, true, rules.unguardedLoot(), Battle.Result.NONE);
        }
        Battle.Result battle = Battle.resolve(guards, bandits, battleRules, random);
        boolean robbed = battle.winner() != Battle.Winner.FIRST;
        return new Ambush(true, robbed, robbed ? battle.lootShare() : 0.0, battle);
    }

    /**
     * Splits goods: a share of the items, rounded down but at least one item for any share above nothing, goes to the
     * first part, taken from every entry in turn.
     */
    public static Split split(List<Goods> goods, double share) {
        long total = items(goods);
        long wanted = share <= 0 ? 0 : Math.max(1, (long) Math.floor(total * Math.min(1.0, share)));
        List<Goods> taken = new ArrayList<>();
        List<Goods> kept = new ArrayList<>();
        long left = Math.min(wanted, total);
        for (Goods entry : goods) {
            int take = (int) Math.min(entry.count(), left);
            left -= take;
            if (take > 0) {
                taken.add(entry.withCount(take));
            }
            if (entry.count() - take > 0) {
                kept.add(entry.withCount(entry.count() - take));
            }
        }
        return new Split(taken, kept);
    }

    /**
     * The part of a payment that pays for goods that were lost, in proportion to the items.
     */
    public static long refund(long payment, long lostItems, long allItems) {
        if (allItems <= 0 || lostItems <= 0) {
            return 0;
        }
        if (lostItems >= allItems) {
            return payment;
        }
        return Math.min(payment, (long) Math.floor(payment * (lostItems / (double) allItems)));
    }

    /**
     * A day passes for a camp: nobody disturbed it since its last growth, so it may gain a robber.
     *
     * @return whether it grew
     */
    public static boolean grow(BanditCamp camp, long day, BanditRules rules, DoubleSupplier random) {
        if (camp.cleared() || day <= camp.lastGrowth()) {
            return false;
        }
        camp.setLastGrowth(day);
        if (camp.bandits() >= rules.maxBandits() || random.getAsDouble() >= rules.growthChance()) {
            return false;
        }
        camp.grow(rules.maxBandits());
        return true;
    }
}
