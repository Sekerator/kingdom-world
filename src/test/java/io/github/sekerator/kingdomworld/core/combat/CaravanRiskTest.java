package io.github.sekerator.kingdomworld.core.combat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaravanRiskTest {
    private static final BattleRules RULES = BattleRules.DEFAULT;

    private static Side guards(int count, int level) {
        List<Fighter> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new Fighter(new UUID(7, i), level, 5, 1.6, 12, 0, 0, 1.0, false));
        }
        return Side.of(Side.Kind.SOLDIERS, list);
    }

    @Test
    void guardsLowerTheChanceOfALossByTheirStrength() {
        double threat = CaravanRisk.roadThreat(120, 5).power();
        assertEquals(0.02, CaravanRisk.lossChance(0.02, 0, threat), 1e-12, "without guards the chance stays");
        double previous = 0.02;
        for (int count = 1; count <= 8; count++) {
            double chance = CaravanRisk.lossChance(0.02, guards(count, 1).power(), threat);
            assertTrue(chance < previous, count + " guards should lower the chance below " + previous + ", not " + chance);
            previous = chance;
        }
        assertTrue(previous < 0.002, "a strong guard makes losses rare");
        assertEquals(0.0, CaravanRisk.lossChance(0.0, 100, threat), 1e-12);
    }

    @Test
    void anUnguardedJourneyRollsOnceAsBefore() {
        CaravanRisk.Journey safe = CaravanRisk.journey(0.02, Side.NOBODY, CaravanRisk.roadThreat(120, 5), RULES, () -> 0.5);
        assertFalse(safe.ambushed() || safe.lost());
        CaravanRisk.Journey lost = CaravanRisk.journey(0.02, Side.NOBODY, CaravanRisk.roadThreat(120, 5), RULES, () -> 0.01);
        assertTrue(lost.ambushed() && lost.lost());
        assertEquals(Battle.Winner.NONE, lost.battle().winner(), "no guards, no fight");
    }

    @Test
    void guardsWhoWinSaveTheCaravan() {
        Side strong = guards(6, 3);
        CaravanRisk.Journey journey = CaravanRisk.journey(1.0, strong, CaravanRisk.roadThreat(120, 5), RULES, new Random(3)::nextDouble);
        assertTrue(journey.ambushed());
        assertEquals(journey.battle().winner() != Battle.Winner.FIRST, journey.lost());
        int lost = 0;
        Random random = new Random(11);
        for (int i = 0; i < 2_000; i++) {
            if (CaravanRisk.journey(0.5, guards(1, 0), CaravanRisk.roadThreat(120, 5), RULES, random::nextDouble).lost()) {
                lost++;
            }
        }
        double expected = CaravanRisk.lossChance(0.5, guards(1, 0).power(), CaravanRisk.roadThreat(120, 5).power());
        assertEquals(expected, lost / 2_000.0, 0.04, "losses follow the chance");
    }
}
