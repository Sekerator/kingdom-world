package io.github.sekerator.kingdomworld.core.army;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * Bands of mercenaries looking for work. Every day a kingdom gets a few offers: bands of ready fighters with gear of their
 * own (better gear for higher ranks), priced by their strength.
 */
public final class MercenaryMarket {

    /**
     * Gear of a mercenary of a rank: weapon id, its damage and speed, armor points and toughness.
     */
    record Kit(String weapon, double damage, double speed, boolean ranged, float armor, float toughness) {
    }

    private static final Kit[][] KITS = {
            {new Kit("minecraft:stone_sword", 4, 1.6, false, 3, 0), new Kit("minecraft:bow", 5, 1.0, true, 3, 0)},
            {new Kit("minecraft:iron_sword", 5, 1.6, false, 7, 0), new Kit("minecraft:bow", 6, 1.0, true, 7, 0)},
            {new Kit("minecraft:iron_axe", 8, 0.9, false, 12, 0), new Kit("minecraft:crossbow", 8, 0.8, true, 12, 0)},
            {new Kit("minecraft:diamond_sword", 6, 1.6, false, 15, 0), new Kit("minecraft:crossbow", 8, 0.8, true, 15, 0)},
            {new Kit("minecraft:diamond_sword", 6, 1.6, false, 20, 8), new Kit("minecraft:crossbow", 9, 0.8, true, 20, 8)},
            {new Kit("minecraft:netherite_sword", 7, 1.6, false, 20, 12), new Kit("minecraft:crossbow", 9, 0.8, true, 20, 12)}};

    private MercenaryMarket() {
    }

    /**
     * A band offered to a kingdom.
     *
     * @param random numbers from 0 to 1
     */
    public static MercenaryBand offer(long id, UUID kingdomId, long day, ArmyRules rules, DoubleSupplier random) {
        int size = rules.minBand() + (int) Math.floor(random.getAsDouble() * (rules.maxBand() - rules.minBand() + 1));
        size = Math.max(rules.minBand(), Math.min(rules.maxBand(), size));
        // Most bands are of low rank; a few veterans now and then.
        double roll = random.getAsDouble();
        int level = roll < 0.4 ? 0 : roll < 0.7 ? 1 : roll < 0.87 ? 2 : roll < 0.96 ? 3 : roll < 0.99 ? 4 : 5;
        List<Mercenary> fighters = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int own = Math.max(0, Math.min(SoldierRank.COUNT - 1, level + (random.getAsDouble() < 0.25 ? -1 : 0) + (i == 0 ? 1 : 0)));
            boolean ranged = random.getAsDouble() < 0.35;
            Kit kit = KITS[own][ranged ? 1 : 0];
            int seed = (int) Math.floor(random.getAsDouble() * Integer.MAX_VALUE);
            boolean female = random.getAsDouble() < 0.3;
            UUID fighter = new UUID(id * 7_919L + i, kingdomId.getLeastSignificantBits() ^ seed);
            fighters.add(new Mercenary(fighter, seed, female, own, kit.weapon(), kit.damage(), kit.speed(), kit.ranged(), kit.armor(),
                    kit.toughness(), (int) SoldierRank.ofIndex(own).experience(), 1.0));
        }
        MercenaryBand draft = new MercenaryBand(id, kingdomId, fighters, 1, day, MercenaryBand.OFFER);
        return new MercenaryBand(id, kingdomId, fighters, rules.price(draft.power()), day, MercenaryBand.OFFER);
    }
}
