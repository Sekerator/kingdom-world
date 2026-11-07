package io.github.sekerator.kingdomworld.core.road;

import io.github.sekerator.kingdomworld.core.combat.BattleRules;
import io.github.sekerator.kingdomworld.core.combat.Fighter;
import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbushesTest {
    private static final BanditRules RULES = BanditRules.DEFAULT;

    private static Side guards(int count) {
        List<Fighter> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new Fighter(new UUID(9, i), 300, 6, 1.6, 15, 0, 0, 1.0, false));
        }
        return Side.of(Side.Kind.SOLDIERS, list);
    }

    @Test
    void noCampNoAmbush() {
        assertEquals(0.0, Ambushes.chance(100, null, 0.0, 1.0, RULES));
        assertEquals(0.0, Ambushes.campFactor(5, RULES.campReach() + 1, RULES));
        assertEquals(0.0, Ambushes.campFactor(0, 10, RULES));
    }

    @Test
    void theChanceGrowsWithTheCampAndFallsWithDistanceRoadsAndProtection() {
        double full = Ambushes.campFactor(RULES.maxBandits(), 0, RULES);
        double small = Ambushes.campFactor(1, 0, RULES);
        double far = Ambushes.campFactor(RULES.maxBandits(), RULES.campReach() * 0.8, RULES);
        assertEquals(1.0, full, 1e-9);
        assertTrue(small < full && far < full);
        double country = Ambushes.chance(100, null, full, 1.0, RULES);
        assertEquals(RULES.ambushChance(), country, 1e-9);
        double previous = country;
        for (RoadClass roadClass : RoadClass.values()) {
            double onRoad = Ambushes.chance(100, roadClass, full, 1.0, RULES);
            assertTrue(onRoad < previous, "a better road is not safer: " + roadClass);
            previous = onRoad;
        }
        double guarded = Ambushes.chance(100, null, full, Ambushes.protection(1, 0, false, RULES), RULES);
        double patrolled = Ambushes.chance(100, null, full, Ambushes.protection(0, 1, false, RULES), RULES);
        double inLand = Ambushes.chance(100, null, full, Ambushes.protection(0, 0, true, RULES), RULES);
        assertTrue(guarded < country && patrolled < country && inLand < country);
    }

    @Test
    void thePiecesOfAWayAddUpToTheWhole() {
        double whole = Ambushes.chance(400, null, 1.0, 1.0, RULES);
        double safe = 1.0;
        for (int i = 0; i < 8; i++) {
            safe *= 1.0 - Ambushes.chance(50, null, 1.0, 1.0, RULES);
        }
        assertEquals(whole, 1.0 - safe, 1e-9);
    }

    @Test
    void guardsFightAndRobbersTakeAShareWhenTheyWin() {
        Side bandits = Side.crowd(Side.Kind.BANDITS, 18 * 5, 5);
        Ambushes.Ambush none = Ambushes.resolve(0.1, Side.NOBODY, bandits, RULES, BattleRules.DEFAULT, () -> 0.5);
        assertFalse(none.happened());
        Ambushes.Ambush unguarded = Ambushes.resolve(0.1, Side.NOBODY, bandits, RULES, BattleRules.DEFAULT, () -> 0.0);
        assertTrue(unguarded.happened() && unguarded.robbed());
        assertEquals(RULES.unguardedLoot(), unguarded.share(), 1e-9);
        Ambushes.Ambush beaten = Ambushes.resolve(1.0, guards(8), bandits, RULES, BattleRules.DEFAULT, () -> 0.0);
        assertTrue(beaten.happened() && !beaten.robbed(), "eight veterans lost to five robbers");
        assertTrue(Ambushes.robbery(0.5, 1_000, bandits.power()) < Ambushes.robbery(0.5, 0, bandits.power()));
    }

    @Test
    void robbedGoodsAreSplitWithoutMakingOrLosingItems() {
        List<Goods> cargo = List.of(Goods.of("minecraft:wheat", 30), Goods.of("minecraft:iron_ingot", 10));
        Ambushes.Split half = Ambushes.split(cargo, 0.5);
        assertEquals(20, half.takenItems());
        assertEquals(40, half.takenItems() + half.keptItems());
        Ambushes.Split all = Ambushes.split(cargo, 1.0);
        assertTrue(all.kept().isEmpty());
        assertEquals(40, all.takenItems());
        Ambushes.Split tiny = Ambushes.split(List.of(Goods.of("minecraft:diamond", 1)), 0.1);
        assertEquals(1, tiny.takenItems());
        assertEquals(50, Ambushes.refund(100, 20, 40));
        assertEquals(100, Ambushes.refund(100, 40, 40));
        assertEquals(0, Ambushes.refund(100, 0, 40));
    }

    @Test
    void campsGrowOnlyWhileNobodyDisturbsThem() {
        BanditCamp camp = new BanditCamp(1, "minecraft:overworld", 0, 64, 0, 3, true, 0);
        assertTrue(Ambushes.grow(camp, 1, RULES, () -> 0.0));
        assertEquals(4, camp.bandits());
        assertFalse(Ambushes.grow(camp, 1, RULES, () -> 0.0), "a camp grew twice in a day");
        camp.setLastGrowth(5);
        assertFalse(Ambushes.grow(camp, 5, RULES, () -> 0.0), "a camp disturbed today grew");
        assertFalse(Ambushes.grow(camp, 6, RULES, () -> 0.99), "a camp grew against the odds");
        for (int day = 7; day < 100; day++) {
            Ambushes.grow(camp, day, RULES, () -> 0.0);
        }
        assertEquals(RULES.maxBandits(), camp.bandits());
        camp.lose(camp.bandits());
        camp.killLeader();
        assertTrue(camp.cleared());
        assertFalse(Ambushes.grow(camp, 200, RULES, () -> 0.0), "a cleared camp grew");
    }

    @Test
    void aCampKeepsWhatItRobbed() {
        BanditCamp camp = new BanditCamp(1, "minecraft:overworld", 0, 64, 0, 3, true, 0);
        camp.addLoot(List.of(Goods.of("minecraft:wheat", 30)));
        camp.addLoot(List.of(Goods.of("minecraft:wheat", 5), Goods.of("minecraft:bread", 2)));
        assertEquals(37, camp.lootItems());
        assertEquals(2, camp.loot().size());
        assertEquals(37, Ambushes.items(camp.takeLoot()));
        assertTrue(camp.loot().isEmpty());
        assertEquals(3 * RULES.banditStrength() + RULES.leaderStrength(), camp.side(RULES, 10).power(), 1e-9);
    }
}
