package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.land.ProvinceKind;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The sale of a province of a kingdom of the world to a player or a kingdom of players. A ruler sells an empty province on
 * the edge or in the core of its land (not its heart, not next to its heart, none with a settlement), as long as it keeps
 * most of what it was planned to hold and its land stays together around its capital, now and then and not too much of it in
 * all, and not to an enemy. The price is its chunks at a price each, more from a warrior or a proud ruler, less from a
 * merchant, more from one it thinks ill of; a goal to save up for, not a purchase on the way.
 */
public final class ProvinceSale {
    /** Why a province is not for sale. */
    public enum Refusal {
        NOT_OF_THE_WORLD, HEART, NEXT_TO_HEART, SETTLED, WILD, KEEPS_TOO_LITTLE, SPLITS_THE_LAND, TOO_SOON, SOLD_ENOUGH, AT_WAR,
        ILL_WILL, NOT_NEXT_TO_BUYER, HELD_FOR_ANOTHER, UNWILLING
    }

    /**
     * The numbers of the sales.
     *
     * @param pricePerChunk  the price of a chunk before the character and the relation
     * @param minPrice       the least price of a province
     * @param cooldownDays   days a ruler waits between two sales
     * @param keepShare      the share of its planned provinces a ruler keeps
     * @param connectedShare the share of its land that stays together with its capital
     * @param soldShare      the share of its planned provinces a ruler sells in all
     * @param minRelation    the worst relation a ruler still sells at
     * @param holdDays       days a province is held for the bearer of a charter
     */
    public record Rules(double pricePerChunk, long minPrice, int cooldownDays, double keepShare, double connectedShare, double soldShare,
                        int minRelation, int holdDays) {
        public static final Rules DEFAULT = new Rules(3.0, 1_500, 10, 0.75, 0.75, 0.25, -20, 3);
    }

    /**
     * What is known of a sale.
     *
     * @param ofWorld        whether the seller is a kingdom of the world
     * @param nextToHeart    whether the province borders the heart province of the seller
     * @param settled        whether a settlement stands in the province
     * @param plannedHeld    provinces the seller was planned to hold
     * @param heldAfter      provinces the seller holds once this one is sold
     * @param connectedAfter the share of the land of the seller that stays together with its capital once this one is sold
     * @param lastSaleDay    the day of its last sale, or a very old day
     * @param soldBefore     provinces it sold before
     * @param relation       what the seller thinks of the buyer, -100 to 100
     * @param nextToBuyer    whether the province borders the land of the buying kingdom (always true for a charter)
     * @param heldForOther   whether the province is held for the bearer of another charter
     */
    public record Facts(boolean ofWorld, ProvinceKind kind, boolean nextToHeart, boolean settled, int plannedHeld, int heldAfter,
                        double connectedAfter, long lastSaleDay, long day, int soldBefore, boolean atWar, int relation, boolean nextToBuyer,
                        boolean heldForOther) {
    }

    private ProvinceSale() {
    }

    /**
     * Why a province is not for sale, or nothing if it is.
     */
    public static Optional<Refusal> refusal(Facts facts, Rules rules) {
        if (!facts.ofWorld()) {
            return Optional.of(Refusal.NOT_OF_THE_WORLD);
        }
        if (facts.kind() == ProvinceKind.HEART) {
            return Optional.of(Refusal.HEART);
        }
        if (facts.kind() == ProvinceKind.WILD) {
            return Optional.of(Refusal.WILD);
        }
        if (facts.nextToHeart()) {
            return Optional.of(Refusal.NEXT_TO_HEART);
        }
        if (facts.settled()) {
            return Optional.of(Refusal.SETTLED);
        }
        if (facts.heldForOther()) {
            return Optional.of(Refusal.HELD_FOR_ANOTHER);
        }
        if (facts.atWar()) {
            return Optional.of(Refusal.AT_WAR);
        }
        if (facts.relation() < rules.minRelation()) {
            return Optional.of(Refusal.ILL_WILL);
        }
        int planned = Math.max(1, facts.plannedHeld());
        if (facts.heldAfter() < Math.ceil(planned * rules.keepShare())) {
            return Optional.of(Refusal.KEEPS_TOO_LITTLE);
        }
        if (facts.connectedAfter() < rules.connectedShare()) {
            return Optional.of(Refusal.SPLITS_THE_LAND);
        }
        if (facts.day() - facts.lastSaleDay() < rules.cooldownDays()) {
            return Optional.of(Refusal.TOO_SOON);
        }
        if (facts.soldBefore() + 1 > Math.floor(planned * rules.soldShare())) {
            return Optional.of(Refusal.SOLD_ENOUGH);
        }
        if (!facts.nextToBuyer()) {
            return Optional.of(Refusal.NOT_NEXT_TO_BUYER);
        }
        return Optional.empty();
    }

    /**
     * What a ruler of a character asks for a province of a number of chunks from a buyer it thinks of as it does.
     */
    public static long price(int chunks, RulerCharacter character, Collection<RulerCharacter.Trait> traits, int relation, Rules rules) {
        double price = chunks * rules.pricePerChunk() * characterFactor(character, traits) * relationFactor(relation);
        return Math.max(rules.minPrice(), Math.round(price));
    }

    /**
     * How the character of a ruler weighs on its prices: a merchant 0.85, a generous ruler 0.9, a builder or a mage 1.0, a
     * thrifty (greedy) or proud one 1.25, a warrior 1.4. A trait weighs over the leaning when there is one.
     */
    public static double characterFactor(RulerCharacter character, Collection<RulerCharacter.Trait> traits) {
        double factor = switch (character) {
            case MERCHANT -> 0.85;
            case WARRIOR -> 1.4;
            default -> 1.0;
        };
        for (RulerCharacter.Trait trait : traits) {
            if (trait == RulerCharacter.Trait.THRIFTY || trait == RulerCharacter.Trait.PROUD) {
                factor = Math.max(factor, 1.25);
            } else if (trait == RulerCharacter.Trait.GENEROUS) {
                factor = Math.min(factor, 0.9);
            }
        }
        return factor;
    }

    /**
     * How the relation weighs on a price: 1.2 at 0 and below, falling to 0.9 at 50 and above.
     */
    public static double relationFactor(int relation) {
        if (relation <= 0) {
            return 1.2;
        }
        if (relation >= 50) {
            return 0.9;
        }
        return 1.2 - 0.3 * relation / 50.0;
    }

    /**
     * Whether a ruler is willing to sell today, as it answers a deal: a merchant readily, a warrior seldom, the others in
     * between, a little more readily to one it thinks well of. The same on the same day for the same province.
     */
    public static boolean willing(RulerCharacter character, int relation, long seed) {
        double chance = switch (character) {
            case MERCHANT -> 0.9;
            case WARRIOR -> 0.25;
            default -> 0.6;
        } + Math.max(0, Math.min(50, relation)) / 250.0;
        long z = seed + 0x632BE59BD9B4E019L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        double roll = ((z ^ (z >>> 31)) >>> 11) / (double) (1L << 53);
        return roll < chance;
    }

    /**
     * The share of the land in provinces of a seller that stays together with the province of its capital once one is gone.
     *
     * @param held       the provinces it holds
     * @param neighbours the neighbours of each province
     * @param chunks     the chunks of each province
     * @param capital    the province of its capital
     * @param removed    the province sold
     */
    public static double connectedShare(Set<Integer> held, Map<Integer, List<Integer>> neighbours, Map<Integer, Integer> chunks, int capital,
                                        int removed) {
        long total = 0;
        for (int id : held) {
            if (id != removed) {
                total += chunks.getOrDefault(id, 0);
            }
        }
        if (total == 0 || capital == removed || !held.contains(capital)) {
            return 0.0;
        }
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(capital);
        seen.add(capital);
        long together = 0;
        while (!queue.isEmpty()) {
            int id = queue.poll();
            together += chunks.getOrDefault(id, 0);
            for (int next : neighbours.getOrDefault(id, List.of())) {
                if (next != removed && held.contains(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return together / (double) total;
    }
}
