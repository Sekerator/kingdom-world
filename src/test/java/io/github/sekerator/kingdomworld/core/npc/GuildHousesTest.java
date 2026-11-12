package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Rank;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ranks of the guilds of the capitals: by level and seed, the only SS for the first large kingdom, kept with the place.
 */
class GuildHousesTest {
    @Test
    void theRankFollowsTheLevelAndTheSeed() {
        Set<Rank> small = EnumSet.noneOf(Rank.class);
        Set<Rank> medium = EnumSet.noneOf(Rank.class);
        for (long seed = 0; seed < 200; seed++) {
            long mixed = seed * 0x9E3779B97F4A7C15L + 11;
            Rank a = GuildHouses.rankOf(NpcLevel.SMALL, mixed, false);
            Rank b = GuildHouses.rankOf(NpcLevel.MEDIUM, mixed, true);
            assertEquals(a, GuildHouses.rankOf(NpcLevel.SMALL, mixed, true), "SS does not matter to a small kingdom");
            assertEquals(b, GuildHouses.rankOf(NpcLevel.MEDIUM, mixed, false));
            assertEquals(a, GuildHouses.rankOf(NpcLevel.SMALL, mixed, false), "the same seed, the same rank");
            small.add(a);
            medium.add(b);
            assertEquals(Rank.SS, GuildHouses.rankOf(NpcLevel.LARGE, mixed, false));
            assertEquals(Rank.S, GuildHouses.rankOf(NpcLevel.LARGE, mixed, true));
            // A medium kingdom has the higher rank exactly when a small one of the same seed has.
            assertEquals(a == Rank.A, b == Rank.S);
        }
        assertEquals(EnumSet.of(Rank.B, Rank.A), small);
        assertEquals(EnumSet.of(Rank.A, Rank.S), medium);
    }

    @Test
    void ranksAreReadFromTheirIds() {
        for (Rank rank : Rank.values()) {
            assertEquals(rank, GuildHouses.parse(rank.id()).orElseThrow());
        }
        assertEquals("ss", Rank.SS.id());
        assertTrue(GuildHouses.parse("").isEmpty());
        assertTrue(GuildHouses.parse("SS").isEmpty());
        assertTrue(GuildHouses.parse("c").isEmpty());
        assertTrue(GuildHouses.parse(null).isEmpty());
    }

    @Test
    void aPlaceKeepsItsRankOrTakesTheDefaultOfItsLevel() {
        NpcSite kept = new NpcSite(1, 0, 0, NpcLevel.SMALL, 3L, List.of(), NpcSite.LAYOUT_STYLED, "terem", "", "a");
        assertEquals(Rank.A, GuildHouses.of(kept));
        assertEquals(Rank.B, GuildHouses.of(new NpcSite(2, 0, 0, NpcLevel.SMALL, 3L, List.of(), NpcSite.LAYOUT_STYLED, "terem", "")));
        assertEquals(Rank.A, GuildHouses.of(new NpcSite(3, 0, 0, NpcLevel.MEDIUM, 3L, List.of(), NpcSite.LAYOUT_STYLED, "terem", "")));
        assertEquals(Rank.S, GuildHouses.of(new NpcSite(4, 0, 0, NpcLevel.LARGE, 3L, List.of(), NpcSite.LAYOUT_STYLED, "terem", "")));
        assertEquals(Rank.S, GuildHouses.of(new NpcSite(5, 0, 0, NpcLevel.LARGE, 3L, List.of(), NpcSite.LAYOUT_STYLED, "terem", "", "x")),
                "an unknown rank is the default");
        assertEquals(Rank.SS, GuildHouses.of(new NpcSite(6, 0, 0, NpcLevel.LARGE, 3L, List.of(), NpcSite.LAYOUT_STYLED, "terem", "", "ss")));
    }

    @Test
    void theSquareOfALevelHoldsTheHousesUpToItsRank() {
        assertEquals(Rank.A, GuildHouses.highest(NpcLevel.SMALL));
        assertEquals(Rank.S, GuildHouses.highest(NpcLevel.MEDIUM));
        assertEquals(Rank.SS, GuildHouses.highest(NpcLevel.LARGE));
        assertEquals(Rank.A, GuildHouses.fitting(NpcLevel.SMALL, Rank.SS));
        assertEquals(Rank.B, GuildHouses.fitting(NpcLevel.LARGE, Rank.B));
        assertEquals(Rank.A, GuildHouses.fitting(NpcLevel.MEDIUM, null));
        for (NpcLevel level : NpcLevel.values()) {
            for (long seed = 0; seed < 50; seed++) {
                Rank rank = GuildHouses.rankOf(level, seed, seed % 2 == 0);
                assertEquals(rank, GuildHouses.fitting(level, rank), level + " plans only ranks its square holds");
                assertTrue(rank.ordinal() >= GuildHouses.defaultOf(level).ordinal(), level + " " + rank);
            }
        }
    }
}
