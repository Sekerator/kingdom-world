package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.guild.GuildRules;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildRanksTest {
    private static final GuildRules RULES = GuildRules.DEFAULT;

    private static Set<Rank> seen(NpcLevel level, boolean strongest) {
        Set<Rank> ranks = EnumSet.noneOf(Rank.class);
        for (long seed = 0; seed < 200; seed++) {
            ranks.add(GuildRanks.kingdom(level, seed * 7919L + 17, strongest, RULES));
        }
        return ranks;
    }

    @Test
    void weakKingdomsAreOfRankBOrAAndStrongOnesOfSOrSS() {
        assertEquals(EnumSet.of(Rank.B, Rank.A), seen(NpcLevel.SMALL, false));
        assertEquals(EnumSet.of(Rank.A, Rank.S), seen(NpcLevel.MEDIUM, false));
        // SS belongs to the strongest kingdom of the world alone: every other large one is S.
        assertEquals(EnumSet.of(Rank.S), seen(NpcLevel.LARGE, false));
        assertEquals(EnumSet.of(Rank.SS), seen(NpcLevel.LARGE, true));
        // Only a level whose range reaches SS can hold it.
        assertEquals(EnumSet.of(Rank.B, Rank.A), seen(NpcLevel.SMALL, true));
        assertTrue(GuildRanks.mayBeStrongest(NpcLevel.LARGE, RULES));
        assertTrue(!GuildRanks.mayBeStrongest(NpcLevel.SMALL, RULES));
    }

    @Test
    void theSameSeedGivesTheSameRanks() {
        for (long seed = -50; seed < 50; seed++) {
            assertEquals(GuildRanks.of(NpcLevel.SMALL, seed, RulerCharacter.MERCHANT, false, RULES),
                    GuildRanks.of(NpcLevel.SMALL, seed, RulerCharacter.MERCHANT, false, RULES));
        }
    }

    @Test
    void theGuildTheRulerCaresForStandsARankHigher() {
        GuildRanks.Ranks merchant = GuildRanks.of(NpcLevel.LARGE, 1, RulerCharacter.MERCHANT, true, RULES);
        assertEquals(new GuildRanks.Ranks(Rank.SS, Rank.SS), merchant);
        assertEquals(new GuildRanks.Ranks(Rank.SS, Rank.S), GuildRanks.of(NpcLevel.LARGE, 1, RulerCharacter.WARRIOR, true, RULES));
        assertEquals(new GuildRanks.Ranks(Rank.S, Rank.SS), GuildRanks.of(NpcLevel.LARGE, 1, RulerCharacter.MAGE, true, RULES));
        // A large kingdom stays at S, the lowest rank of its level, for both guilds.
        assertEquals(new GuildRanks.Ranks(Rank.S, Rank.S), GuildRanks.of(NpcLevel.LARGE, 1, RulerCharacter.WARRIOR, false, RULES));
        // Never below the lowest rank of the level: a small kingdom of rank B keeps B for both guilds.
        for (long seed = 0; seed < 100; seed++) {
            GuildRanks.Ranks ranks = GuildRanks.of(NpcLevel.SMALL, seed, RulerCharacter.WARRIOR, false, RULES);
            assertTrue(ranks.mages().atLeast(Rank.B) && ranks.adventurers().atLeast(ranks.mages()), ranks.toString());
        }
    }

    @Test
    void aGuildGrowsUpToTheHighestRankOfItsLevel() {
        assertEquals(Rank.A, GuildRanks.ceiling(NpcLevel.SMALL, false, RULES));
        assertEquals(Rank.S, GuildRanks.ceiling(NpcLevel.MEDIUM, false, RULES));
        assertEquals(Rank.S, GuildRanks.ceiling(NpcLevel.LARGE, false, RULES));
        assertEquals(Rank.SS, GuildRanks.ceiling(NpcLevel.LARGE, true, RULES));
    }

    @Test
    void aBranchHasAHeadOfItsRankAndAdventurersBelowIt() {
        assertEquals(java.util.List.of(Rank.B, Rank.C, Rank.C, Rank.D, Rank.D), GuildRanks.adventurers(Rank.B, RULES.worldAdventurers(Rank.B)));
        java.util.List<Rank> top = GuildRanks.adventurers(Rank.SS, RULES.worldAdventurers(Rank.SS));
        assertEquals(14, top.size());
        assertEquals(Rank.SS, top.get(0));
        assertEquals(3, top.stream().filter(rank -> rank == Rank.S).count());
        assertTrue(top.stream().skip(1).allMatch(rank -> rank.ordinal() < Rank.SS.ordinal() && rank.atLeast(Rank.B)), top.toString());
        assertEquals(8, RULES.worldAdventurers(Rank.A));
        assertEquals(11, RULES.worldAdventurers(Rank.S));
    }

    @Test
    void aStrongerBranchBringsMoreEnergyFromItsHunts() {
        long b = GuildRanks.huntEnergy(GuildRanks.adventurers(Rank.B, RULES.worldAdventurers(Rank.B)), RULES);
        long a = GuildRanks.huntEnergy(GuildRanks.adventurers(Rank.A, RULES.worldAdventurers(Rank.A)), RULES);
        long ss = GuildRanks.huntEnergy(GuildRanks.adventurers(Rank.SS, RULES.worldAdventurers(Rank.SS)), RULES);
        assertTrue(b > 0 && a > b && ss > a, b + " " + a + " " + ss);
        assertEquals(0, GuildRanks.huntEnergy(java.util.List.of(), RULES));
    }

    @Test
    void theRangesComeFromTheSettings() {
        GuildRules.Builder builder = new GuildRules.Builder();
        builder.worldRankMin = new Rank[]{Rank.C, Rank.B, Rank.A};
        builder.worldRankMax = new Rank[]{Rank.C, Rank.A, Rank.SSS};
        GuildRules rules = builder.build();
        assertEquals(Rank.C, GuildRanks.kingdom(NpcLevel.SMALL, 5, false, rules));
        assertEquals(Rank.SS, GuildRanks.kingdom(NpcLevel.LARGE, 5, true, rules));
        for (long seed = 0; seed < 50; seed++) {
            Rank large = GuildRanks.kingdom(NpcLevel.LARGE, seed, false, rules);
            assertTrue(large == Rank.A || large == Rank.S, large.label());
        }
        assertEquals(Rank.SSS, GuildRanks.ceiling(NpcLevel.LARGE, true, rules));
    }
}
