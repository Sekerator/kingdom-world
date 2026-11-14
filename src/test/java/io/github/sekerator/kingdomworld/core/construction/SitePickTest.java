package io.github.sekerator.kingdomworld.core.construction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which place far from players is loaded next: one worth it, the kingdoms of players and of the world taking turns, the
 * kingdoms of each taking turns, and a place that failed waiting.
 */
class SitePickTest {
    private static final UUID PLAYERS_A = new UUID(1, 1);
    private static final UUID PLAYERS_B = new UUID(1, 2);
    private static final UUID WORLD_A = new UUID(2, 1);
    private static final UUID WORLD_B = new UUID(2, 2);
    private static final long NOW = 1_000_000L;

    private static SitePick.Candidate site(long id, UUID kingdom, boolean players, int pending, int remaining, long since) {
        return new SitePick.Candidate(SitePick.Kind.CONSTRUCTION, id, kingdom, players, pending, remaining, since, false);
    }

    @Test
    void aPlaceIsWorthLoadingWhenPaidForEnoughOrLongAgo() {
        assertTrue(site(1, PLAYERS_A, true, 40, 40, NOW).worth(NOW), "(a) everything left is paid for");
        assertFalse(site(1, PLAYERS_A, true, 39, 40, NOW).worth(NOW));
        assertTrue(site(1, PLAYERS_A, true, 256, 1_000, NOW).worth(NOW), "(b) 256 steps paid for");
        assertFalse(site(1, PLAYERS_A, true, 256, 5_000, NOW).worth(NOW), "(b) but less than a quarter of a large place");
        assertTrue(site(1, PLAYERS_A, true, 300, 1_200, NOW).worth(NOW), "(b) a quarter of what is left");
        assertFalse(site(1, PLAYERS_A, true, 300, 1_300, NOW).worth(NOW));
        assertTrue(site(1, PLAYERS_A, true, 3, 1_000, NOW - SitePick.LONG_WAIT).worth(NOW), "(c) the first step waits long");
        assertFalse(site(1, PLAYERS_A, true, 3, 1_000, NOW - SitePick.LONG_WAIT + 1).worth(NOW));
        assertFalse(site(1, PLAYERS_A, true, 0, 1_000, 0).worth(NOW), "nothing paid for, nothing to put in");
        assertTrue(new SitePick.Candidate(SitePick.Kind.CONSTRUCTION, 1, PLAYERS_A, true, 0, 0, -1, true).worth(NOW),
                "(d) finished, but scaffolds stand where nothing is loaded");
        assertFalse(new SitePick.Candidate(SitePick.Kind.CONSTRUCTION, 1, PLAYERS_A, true, 0, 0, -1, false).worth(NOW));
        SitePick.Candidate drops = new SitePick.Candidate(SitePick.Kind.DROPS, 7, PLAYERS_A, true, 0, 0, NOW - SitePick.DROP_WAIT, false);
        assertTrue(drops.worth(NOW), "items wait a game day to be dropped");
        assertFalse(new SitePick.Candidate(SitePick.Kind.DROPS, 7, PLAYERS_A, true, 0, 0, NOW - 100, false).worth(NOW));
    }

    @Test
    void playersAndTheWorldTakeTurnsAndSoDoKingdoms() {
        SitePick pick = new SitePick();
        List<SitePick.Candidate> all = new ArrayList<>();
        all.add(site(1, WORLD_A, false, 50, 50, NOW));
        all.add(site(2, WORLD_A, false, 60, 60, NOW));
        all.add(site(3, WORLD_B, false, 50, 50, NOW));
        all.add(site(4, PLAYERS_A, true, 50, 50, NOW));
        all.add(site(5, PLAYERS_B, true, 50, 50, NOW));
        List<UUID> kingdoms = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            kingdoms.add(pick.pick(all, NOW).orElseThrow().kingdomId());
        }
        assertEquals(List.of(PLAYERS_A, WORLD_A, PLAYERS_B, WORLD_B), kingdoms, "one of players, one of the world, kingdoms in turn");
    }

    @Test
    void withinAKingdomWhatIsPaidInFullComesFirstThenTheMostPaid() {
        SitePick pick = new SitePick();
        List<SitePick.Candidate> all = List.of(site(1, PLAYERS_A, true, 300, 1_000, NOW), site(2, PLAYERS_A, true, 900, 1_000, NOW),
                site(3, PLAYERS_A, true, 20, 20, NOW));
        assertEquals(3, pick.pick(all, NOW).orElseThrow().id(), "paid in full");
        assertEquals(2, pick.pick(List.of(all.get(0), all.get(1)), NOW).orElseThrow().id(), "the most paid for");
    }

    @Test
    void aPlaceThatFailedWaits() {
        SitePick pick = new SitePick();
        SitePick.Candidate only = site(1, PLAYERS_A, true, 50, 50, NOW);
        pick.defer(only.ref(), NOW);
        assertEquals(Optional.empty(), pick.pick(List.of(only), NOW + SitePick.DEFER - 1));
        assertEquals(1, pick.pick(List.of(only), NOW + SitePick.DEFER).orElseThrow().id());
        assertEquals(Optional.empty(), pick.pick(List.of(), NOW));
    }

    @Test
    void aLonelyHalfIsServedEveryTime() {
        SitePick pick = new SitePick();
        List<SitePick.Candidate> world = List.of(site(1, WORLD_A, false, 50, 50, NOW), site(2, WORLD_B, false, 50, 50, NOW));
        assertEquals(WORLD_A, pick.pick(world, NOW).orElseThrow().kingdomId());
        assertEquals(WORLD_B, pick.pick(world, NOW).orElseThrow().kingdomId());
        assertEquals(WORLD_A, pick.pick(world, NOW).orElseThrow().kingdomId());
    }
}
