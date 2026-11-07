package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SitePlannerTest {
    private static final NpcRules RULES = NpcRules.DEFAULT;

    /**
     * Runs a plan to its end with a judge of candidates, as the server does a candidate a tick.
     */
    private static List<SitePlanner.Candidate> plan(long seed, Function<SitePlanner.Candidate, SitePlanner.Verdict> judge) {
        SitePlanner planner = new SitePlanner(seed, RULES, 100, -200, List.of(), 0, 0);
        List<SitePlanner.Candidate> accepted = new ArrayList<>();
        for (int guard = 0; guard < 100_000 && !planner.done(); guard++) {
            Optional<SitePlanner.Candidate> current = planner.current();
            if (current.isEmpty()) {
                break;
            }
            planner.judge(judge.apply(current.get())).ifPresent(accepted::add);
        }
        return accepted;
    }

    @Test
    void thePlanIsTheSameForTheSameSeed() {
        Function<SitePlanner.Candidate, SitePlanner.Verdict> land = c -> Math.floorMod(c.x() * 7 + c.z() * 3, 5) == 0
                ? SitePlanner.Verdict.UNSUITABLE : SitePlanner.Verdict.GOOD;
        List<SitePlanner.Candidate> first = plan(12345L, land);
        List<SitePlanner.Candidate> second = plan(12345L, land);
        assertEquals(first, second);
        assertEquals(RULES.count(), first.size());
        assertNotEquals(first, plan(54321L, land), "another seed plans other places");
    }

    @Test
    void capitalsKeepTheirDistancesFromEachOtherAndFromTheSpawn() {
        List<SitePlanner.Candidate> sites = plan(777L, c -> SitePlanner.Verdict.GOOD);
        assertEquals(RULES.count(), sites.size());
        for (SitePlanner.Candidate site : sites) {
            double fromSpawn = Math.hypot(site.x() - 100, site.z() + 200);
            assertTrue(fromSpawn >= RULES.minSpawnDistance() && fromSpawn <= RULES.radius(), "from the spawn: " + fromSpawn);
            for (SitePlanner.Candidate other : sites) {
                if (other != site) {
                    assertTrue(Math.hypot(site.x() - other.x(), site.z() - other.z()) >= RULES.minDistance(), site + " and " + other);
                }
            }
        }
    }

    @Test
    void anOccupiedPlaceIsShiftedOrSkippedButNeverTaken() {
        // Everything within 400 blocks of one point is taken by players.
        int takenX = 2_000;
        int takenZ = 0;
        Function<SitePlanner.Candidate, SitePlanner.Verdict> judge = c -> Math.hypot(c.x() - takenX, c.z() - takenZ) < 400
                ? SitePlanner.Verdict.OCCUPIED : SitePlanner.Verdict.GOOD;
        SitePlanner planner = new SitePlanner(99L, RULES, 0, 0, List.of(), 0, 0);
        boolean shifted = false;
        for (int guard = 0; guard < 100_000 && !planner.done(); guard++) {
            Optional<SitePlanner.Candidate> current = planner.current();
            if (current.isEmpty()) {
                break;
            }
            SitePlanner.Verdict verdict = judge.apply(current.get());
            Optional<SitePlanner.Candidate> accepted = planner.judge(verdict);
            accepted.ifPresent(site -> assertTrue(Math.hypot(site.x() - takenX, site.z() - takenZ) >= 400, "a site on taken land: " + site));
            shifted |= verdict == SitePlanner.Verdict.OCCUPIED && planner.shift() > 0;
        }
        // A candidate of the plan made to fall on the taken land is shifted around it.
        SitePlanner probe = new SitePlanner(99L, RULES, 0, 0, List.of(), 0, 0);
        SitePlanner.Candidate base = probe.candidate(0, 0);
        SitePlanner.Candidate moved = probe.candidate(0, 1);
        assertEquals(SitePlanner.SHIFT_STEP, Math.round(Math.hypot(moved.x() - base.x(), moved.z() - base.z())), 1.0);
        assertTrue(shifted || planner.done());
    }

    @Test
    void thePlanGoesOnFromWhereItStopped() {
        Function<SitePlanner.Candidate, SitePlanner.Verdict> land = c -> Math.floorMod(c.x() + c.z(), 3) == 0
                ? SitePlanner.Verdict.UNSUITABLE : SitePlanner.Verdict.GOOD;
        List<SitePlanner.Candidate> whole = plan(4242L, land);
        // Stop after three places and go on with a new planner from the saved cursor and the places made.
        SitePlanner first = new SitePlanner(4242L, RULES, 100, -200, List.of(), 0, 0);
        NpcState state = new NpcState();
        while (state.sites().size() < 3) {
            SitePlanner.Candidate current = first.current().orElseThrow();
            first.judge(land.apply(current)).ifPresent(c -> state.addSite(c.x(), c.z(), c.level(), c.seed(), List.of()));
        }
        SitePlanner second = new SitePlanner(4242L, RULES, 100, -200, List.copyOf(state.sites()), first.cursor(), first.shift());
        List<SitePlanner.Candidate> rest = new ArrayList<>();
        while (!second.done()) {
            SitePlanner.Candidate current = second.current().orElseThrow();
            second.judge(land.apply(current)).ifPresent(rest::add);
        }
        assertEquals(whole.subList(3, whole.size()), rest);
    }

    @Test
    void townsStandApartOnLandThatSuitsThem() {
        List<int[]> candidates = SitePlanner.townCandidates(0, 0, 5L, 200);
        assertEquals(16, candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            double distance = Math.hypot(candidates.get(i)[0], candidates.get(i)[1]);
            double least = i < 8 ? 199 : 269;
            assertTrue(distance >= least && distance <= least + 42, "distance " + distance);
        }
        List<int[]> chosen = SitePlanner.chooseTowns(candidates, point -> point != candidates.get(0), 3, 160);
        assertEquals(3, chosen.size());
        assertFalse(chosen.contains(candidates.get(0)), "unsuitable land is passed over");
        for (int[] a : chosen) {
            for (int[] b : chosen) {
                if (a != b) {
                    assertTrue(Math.hypot(a[0] - b[0], a[1] - b[1]) >= 160);
                }
            }
        }
    }

    @Test
    void levelsFollowTheirWeights() {
        assertEquals(NpcLevel.SMALL, NpcLevel.pick(new int[]{1, 0, 0}, 0.99));
        assertEquals(NpcLevel.LARGE, NpcLevel.pick(new int[]{0, 0, 1}, 0.0));
        assertEquals(NpcLevel.MEDIUM, NpcLevel.pick(new int[]{1, 1, 1}, 0.5));
        assertEquals(NpcLevel.SMALL, NpcLevel.pick(new int[]{0, 0, 0}, 0.5));
        int[] counts = new int[3];
        SitePlanner planner = new SitePlanner(1L, RULES, 0, 0, List.of(), 0, 0);
        for (int i = 0; i < 1000; i++) {
            counts[planner.candidate(i, 0).level().ordinal()]++;
        }
        assertTrue(counts[0] > counts[1] && counts[1] > counts[2], "small kingdoms are the most common: " + java.util.Arrays.toString(counts));
    }
}
