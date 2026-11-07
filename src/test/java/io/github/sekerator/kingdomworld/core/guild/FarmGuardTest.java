package io.github.sekerator.kingdomworld.core.guild;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmGuardTest {
    private static final GuildRules RULES = GuildRules.DEFAULT;
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void huntingInDifferentPlacesKeepsTheFullChance() {
        FarmGuard guard = new FarmGuard();
        for (int i = 0; i < 100; i++) {
            assertEquals(1.0, guard.record(OVERWORLD, i * 64, 0, i, false, RULES), "kill " + i);
        }
    }

    @Test
    void manyKillsInOnePlaceLowerTheChance() {
        FarmGuard guard = new FarmGuard();
        double last = 1.0;
        for (int i = 0; i < RULES.farmFreeKills; i++) {
            assertEquals(1.0, guard.record(OVERWORLD, 5, 5, i, false, RULES), "the first kills are free");
        }
        for (int i = 0; i < 40; i++) {
            double factor = guard.record(OVERWORLD, 10, 20, 100 + i, false, RULES);
            assertTrue(factor <= last, "the chance never grows while the farm runs");
            last = factor;
        }
        assertTrue(last < 0.05, "forty kills over the free ones leave almost nothing: " + last);
        assertEquals(0.5, FarmGuard.factor(RULES.farmFreeKills + RULES.farmHalfKills, false, RULES), 1e-9, "each half count halves");
        assertEquals(1.0, guard.record("minecraft:the_nether", 10, 20, 200, false, RULES), "another dimension is another place");
    }

    @Test
    void spawnersGiveLessFromTheFirstKill() {
        FarmGuard guard = new FarmGuard();
        double first = guard.record(OVERWORLD, 0, 0, 0, true, RULES);
        assertEquals(RULES.spawnerFactor, first, 1e-9);
        double second = guard.record(OVERWORLD, 0, 0, 1, true, RULES);
        assertTrue(second < first, "the chance falls with each kill at a spawner");
        double later = 1.0;
        for (int i = 2; i < 30; i++) {
            later = guard.record(OVERWORLD, 0, 0, i, true, RULES);
        }
        assertTrue(later < 0.05, "a spawner farm gives almost nothing: " + later);
    }

    @Test
    void placesRecoverAfterTheWindow() {
        FarmGuard guard = new FarmGuard();
        for (int i = 0; i < 60; i++) {
            guard.record(OVERWORLD, 0, 0, i, false, RULES);
        }
        assertTrue(guard.record(OVERWORLD, 0, 0, 61, false, RULES) < 0.1);
        assertEquals(1.0, guard.record(OVERWORLD, 0, 0, 61 + RULES.farmWindowTicks, false, RULES), "old kills are forgotten");
        guard.cleanup(10 * RULES.farmWindowTicks, RULES);
        assertEquals(0, guard.recent(OVERWORLD, 0, 0, 10 * RULES.farmWindowTicks, RULES));
    }
}
