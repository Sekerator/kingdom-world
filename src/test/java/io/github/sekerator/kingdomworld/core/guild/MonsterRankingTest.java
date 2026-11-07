package io.github.sekerator.kingdomworld.core.guild;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterRankingTest {
    private static final GuildRules RULES = GuildRules.DEFAULT;

    private static Rank rank(String id, double health, double attack, double armor, double speed, boolean boss, String... tags) {
        return MonsterRanking.rank(new MonsterRanking.Stats(id, health, attack, armor, speed, boss, List.of(tags)), RULES).rank();
    }

    @Test
    void vanillaMonstersSpreadOverTheRanks() {
        assertEquals(Rank.F, rank("minecraft:silverfish", 8, 1, 0, 0.25, false));
        assertEquals(Rank.E, rank("minecraft:zombie", 20, 3, 2, 0.23, false));
        assertEquals(Rank.E, rank("minecraft:skeleton", 20, 2, 0, 0.25, false));
        assertEquals(Rank.D, rank("minecraft:blaze", 20, 6, 0, 0.23, false));
        assertEquals(Rank.C, rank("minecraft:enderman", 40, 7, 0, 0.3, false));
        assertEquals(Rank.B, rank("minecraft:ravager", 100, 12, 0, 0.3, false));
        assertEquals(Rank.S, rank("some:giant", 600, 30, 0, 0.3, false));
    }

    @Test
    void strongerAttributesNeverLowerTheRank() {
        Rank previous = Rank.F;
        for (int health = 1; health < 3000; health += 37) {
            Rank next = rank("test:grower", health, 4, 2, 0.25, false);
            assertTrue(next.atLeast(previous), "rank fell at " + health);
            previous = next;
        }
        assertEquals(Rank.SSS, previous);
    }

    @Test
    void bossesStandHigher() {
        assertEquals(Rank.D, rank("modded:knight", 30, 5, 0, 0.25, false));
        assertEquals(Rank.A, rank("modded:knight", 30, 5, 0, 0.25, true), "a weak boss is at least A");
        // A strong boss of another mod: strength A, two ranks more.
        assertEquals(Rank.SS, rank("cataclysm:ignis", 300, 15, 10, 0.3, true));
        assertEquals(Rank.SSS, rank("modded:titan", 900, 30, 20, 0.3, true));
    }

    @Test
    void overridesComeFirst() {
        assertEquals(Rank.SS, rank("minecraft:ender_dragon", 200, 0, 0, 0.7, true));
        assertEquals(Rank.S, rank("minecraft:warden", 500, 30, 0, 0.3, false));
        GuildRules.Builder builder = new GuildRules.Builder();
        GuildRules.readOverrides(builder, List.of("minecraft:zombie=c", "#forge:bosses=sss", "bad entry", "#modded:weak=f", "x=zz"));
        GuildRules rules = builder.build();
        MonsterRanking.Ranked zombie = MonsterRanking.rank(new MonsterRanking.Stats("minecraft:zombie", 20, 3, 2, 0.23, false,
                List.of("forge:bosses")), rules);
        assertEquals(Rank.C, zombie.rank(), "an id beats a tag");
        assertTrue(zombie.override());
        assertEquals(Rank.SSS, MonsterRanking.rank(new MonsterRanking.Stats("modded:lord", 50, 1, 0, 0.2, true,
                List.of("modded:weak", "forge:bosses")), rules).rank(), "the highest tag wins");
        assertFalse(MonsterRanking.rank(new MonsterRanking.Stats("modded:rat", 5, 1, 0, 0.2, false, List.of()), rules).override());
        assertEquals(1, rules.idOverrides.size(), "unreadable entries are skipped: " + rules.idOverrides);
        assertEquals(2, rules.tagOverrides.size());
    }

    @Test
    void brokenNumbersCountAsNothing() {
        assertEquals(Rank.F, rank("modded:glitch", Double.NaN, Double.POSITIVE_INFINITY, -5, -1, false));
    }

    @Test
    void tablesByRankAreReadAndWritten() {
        double[] table = GuildRules.parseTable("1, 2, 3, 4 5 6;7,8,9").orElseThrow();
        assertEquals(9, table.length);
        assertEquals(9.0, table[8]);
        assertTrue(GuildRules.parseTable("1,2,3").isEmpty());
        assertTrue(GuildRules.parseTable("1,2,3,4,5,6,7,8,x").isEmpty());
        assertTrue(GuildRules.parseTable("1,2,3,4,5,6,7,8,-1").isEmpty());
        assertEquals("10, 30, 100, 300, 1000, 3000, 10000, 30000, 100000", GuildRules.formatTable(new GuildRules.Builder().crystalEnergy));
        assertEquals("0.05, 0.08, 0.12, 0.2, 0.35, 0.5, 1, 1, 1", GuildRules.formatTable(new GuildRules.Builder().crystalChance));
    }

    @Test
    void crystalEnergyGrowsExponentiallyByRank() {
        for (Rank rank : Rank.values()) {
            rank.next().ifPresent(next -> {
                double ratio = RULES.crystalEnergy(next) / (double) RULES.crystalEnergy(rank);
                assertTrue(ratio >= 3.0 && ratio <= 3.4, rank + " to " + next + ": " + ratio);
                assertTrue(RULES.crystalValue(next) > RULES.crystalValue(rank));
                assertTrue(RULES.crystalChance(next) >= RULES.crystalChance(rank));
            });
        }
    }
}
