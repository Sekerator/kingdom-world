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
     * Rules like the default ones with another count, quota and weights.
     */
    private static NpcRules rules(int count, int[] quota, int[] weights) {
        return new NpcRules(true, count, RULES.radius(), RULES.minDistance(), RULES.minSpawnDistance(), RULES.triggerChunks(), quota,
                weights, new int[]{4, 6, 7}, new int[]{2, 3, 4}, RULES.townDistance(), new long[]{6000, 15000, 40000}, RULES.foodDays(),
                new int[]{6, 10, 16}, new int[]{2, 4, 8}, RULES.blocksPerTick(), RULES.chunksPerTick(), RULES.maxInhabitedMinutes(),
                RULES.attemptsPerSite());
    }

    /**
     * Runs a plan to its end with a judge of candidates, as the server does a candidate a tick.
     */
    private static List<SitePlanner.Candidate> plan(long seed, Function<SitePlanner.Candidate, SitePlanner.Verdict> judge) {
        return run(new SitePlanner(seed, RULES, 100, -200, List.of(), 0, 0), judge);
    }

    private static List<SitePlanner.Candidate> run(SitePlanner planner, Function<SitePlanner.Candidate, SitePlanner.Verdict> judge) {
        List<SitePlanner.Candidate> accepted = new ArrayList<>();
        for (int guard = 0; guard < 1_000_000 && !planner.done(); guard++) {
            Optional<SitePlanner.Candidate> current = planner.current();
            if (current.isEmpty()) {
                break;
            }
            planner.judge(judge.apply(current.get())).ifPresent(accepted::add);
        }
        return accepted;
    }

    private static int count(List<SitePlanner.Candidate> sites, NpcLevel level) {
        int count = 0;
        for (SitePlanner.Candidate site : sites) {
            if (site.level() == level) {
                count++;
            }
        }
        return count;
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
        // Large kingdoms find no place on the first pass, so the plan stops on a later one.
        Function<SitePlanner.Candidate, SitePlanner.Verdict> land = c -> Math.floorMod(c.x() + c.z(), 3) == 0
                || (c.level() == NpcLevel.LARGE && c.relax() < 1) ? SitePlanner.Verdict.UNSUITABLE : SitePlanner.Verdict.GOOD;
        List<SitePlanner.Candidate> whole = plan(4242L, land);
        assertEquals(RULES.count(), whole.size());
        for (int stop : new int[]{3, 6}) {
            // Stop after some places and go on with a new planner from the saved cursor, shift, pass and the places made.
            SitePlanner first = new SitePlanner(4242L, RULES, 100, -200, List.of(), 0, 0);
            NpcState state = new NpcState();
            while (state.sites().size() < stop) {
                SitePlanner.Candidate current = first.current().orElseThrow();
                first.judge(land.apply(current)).ifPresent(c -> state.addSite(c.x(), c.z(), c.level(), c.seed(), List.of(),
                        NpcSite.LAYOUT_DISTRICTS, "stone", ""));
            }
            state.setPlanProgress(first.cursor(), first.shift(), first.pass(), false);
            if (stop == 6) {
                assertTrue(state.planPass() >= 1, "the sixth place is a large one of a later pass");
            }
            SitePlanner second = new SitePlanner(4242L, RULES, 100, -200, List.copyOf(state.sites()), state.planCursor(),
                    state.planShift(), state.planPass());
            for (NpcLevel level : NpcLevel.values()) {
                assertEquals(first.made(level), second.made(level), level.id());
            }
            List<SitePlanner.Candidate> rest = run(second, land);
            assertEquals(whole.subList(stop, whole.size()), rest, "after " + stop);
        }
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
    void levelsAreDrawnByWeights() {
        assertEquals(NpcLevel.SMALL, NpcLevel.pick(new int[]{1, 0, 0}, 0.99));
        assertEquals(NpcLevel.LARGE, NpcLevel.pick(new int[]{0, 0, 1}, 0.0));
        assertEquals(NpcLevel.MEDIUM, NpcLevel.pick(new int[]{1, 1, 1}, 0.5));
        assertEquals(NpcLevel.SMALL, NpcLevel.pick(new int[]{0, 0, 0}, 0.5));
    }

    @Test
    void onGoodLandThePlanMakesTheQuotaOnTheFirstPass() {
        for (long seed = 1; seed <= 25; seed++) {
            SitePlanner planner = new SitePlanner(seed * 7919, RULES, 0, 0, List.of(), 0, 0);
            List<SitePlanner.Candidate> sites = run(planner, c -> SitePlanner.Verdict.GOOD);
            assertEquals(8, sites.size(), "seed " + seed);
            assertEquals(3, count(sites, NpcLevel.LARGE), "seed " + seed);
            assertEquals(5, count(sites, NpcLevel.SMALL), "seed " + seed);
            assertEquals(0, planner.pass(), "seed " + seed);
            assertEquals(3, planner.made(NpcLevel.LARGE));
            // The largest are looked for first.
            for (int i = 0; i < 3; i++) {
                assertEquals(NpcLevel.LARGE, sites.get(i).level());
                assertEquals(0, sites.get(i).relax());
            }
        }
    }

    @Test
    void largeKingdomsAreFoundWhereFewPlacesSuitThem() {
        // Only about one place in five is smooth enough for a large kingdom.
        Function<SitePlanner.Candidate, SitePlanner.Verdict> judge = c -> c.level() == NpcLevel.LARGE
                && Math.floorMod(c.x() * 31 + c.z() * 17, 5) != 0 ? SitePlanner.Verdict.UNSUITABLE : SitePlanner.Verdict.GOOD;
        for (long seed = 1; seed <= 10; seed++) {
            SitePlanner planner = new SitePlanner(seed, RULES, 0, 0, List.of(), 0, 0);
            List<SitePlanner.Candidate> sites = run(planner, judge);
            assertEquals(8, sites.size(), "seed " + seed);
            assertEquals(3, count(sites, NpcLevel.LARGE), "seed " + seed);
            assertEquals(5, count(sites, NpcLevel.SMALL), "seed " + seed);
            for (SitePlanner.Candidate site : sites) {
                if (site.level() == NpcLevel.LARGE) {
                    assertEquals(0, Math.floorMod(site.x() * 31 + site.z() * 17, 5), "a large kingdom on rough land: " + site);
                }
            }
        }
    }

    @Test
    void smallerKingdomsTakeThePlacesLargeOnesNeverFind() {
        for (long seed = 1; seed <= 5; seed++) {
            SitePlanner planner = new SitePlanner(seed, RULES, 0, 0, List.of(), 0, 0);
            List<SitePlanner.Candidate> sites = run(planner, c -> c.level() == NpcLevel.LARGE
                    ? SitePlanner.Verdict.UNSUITABLE : SitePlanner.Verdict.GOOD);
            assertEquals(8, sites.size(), "seed " + seed);
            assertEquals(0, count(sites, NpcLevel.LARGE));
            assertEquals(SitePlanner.PASSES - 1, planner.pass(), "the planner went through its passes");
            for (SitePlanner.Candidate site : sites.subList(5, 8)) {
                assertEquals(SitePlanner.PASSES - 1, site.relax(), "only the last pass gives the places of the large ones away");
                assertNotEquals(NpcLevel.LARGE, site.level());
            }
        }
        // Large kingdoms that suit only the last pass still get their places there.
        SitePlanner planner = new SitePlanner(3L, RULES, 0, 0, List.of(), 0, 0);
        List<SitePlanner.Candidate> sites = run(planner, c -> c.level() == NpcLevel.LARGE && c.relax() < SitePlanner.PASSES - 1
                ? SitePlanner.Verdict.UNSUITABLE : SitePlanner.Verdict.GOOD);
        assertEquals(8, sites.size());
        assertEquals(3, count(sites, NpcLevel.LARGE));
        assertEquals(SitePlanner.PASSES - 1, planner.pass());
    }

    @Test
    void aLaterPassAsksLessOfAPlace() {
        for (long seed = 1; seed <= 5; seed++) {
            SitePlanner planner = new SitePlanner(seed, RULES, 0, 0, List.of(), 0, 0);
            List<SitePlanner.Candidate> sites = run(planner, c -> c.level() == NpcLevel.LARGE && c.relax() < 1
                    ? SitePlanner.Verdict.UNSUITABLE : SitePlanner.Verdict.GOOD);
            assertEquals(8, sites.size());
            int large = 0;
            for (SitePlanner.Candidate site : sites) {
                if (site.level() == NpcLevel.LARGE) {
                    large++;
                    assertEquals(1, site.relax(), "accepted on the second pass: " + site);
                } else {
                    assertEquals(0, site.relax());
                }
            }
            assertEquals(3, large);
            assertEquals(1, planner.pass());
        }
        assertEquals(RULES.minDistance(), SitePlanner.minDistance(RULES, SitePlanner.CLOSER_FROM - 1), 1e-9);
        assertEquals(RULES.minDistance() * 0.75, SitePlanner.minDistance(RULES, SitePlanner.CLOSER_FROM), 1e-9);
    }

    @Test
    void theCountOfThePlanAndTheQuotaGiveWayToEachOther() {
        // A quota of 8 with a count of 4: the plan stops at 4, the largest first.
        SitePlanner few = new SitePlanner(11L, rules(4, new int[]{5, 0, 3}, new int[]{5, 3, 2}), 0, 0, List.of(), 0, 0);
        List<SitePlanner.Candidate> four = run(few, c -> SitePlanner.Verdict.GOOD);
        assertEquals(4, four.size());
        assertEquals(3, count(four, NpcLevel.LARGE));
        assertEquals(1, count(four, NpcLevel.SMALL));
        assertTrue(few.done());
        // A count of 11 with the same quota: the three beyond it are drawn by the weights, here all medium.
        SitePlanner many = new SitePlanner(11L, rules(11, new int[]{5, 0, 3}, new int[]{0, 1, 0}), 0, 0, List.of(), 0, 0);
        List<SitePlanner.Candidate> eleven = run(many, c -> SitePlanner.Verdict.GOOD);
        assertEquals(11, eleven.size());
        assertEquals(3, count(eleven, NpcLevel.LARGE));
        assertEquals(5, count(eleven, NpcLevel.SMALL));
        assertEquals(3, count(eleven, NpcLevel.MEDIUM));
        // No quota at all: every level is drawn by the weights, small ones the most common.
        int[] counts = new int[3];
        SitePlanner drawn = new SitePlanner(1L, rules(8, new int[]{0, 0, 0}, new int[]{5, 3, 2}), 0, 0, List.of(), 0, 0);
        for (int i = 0; i < 1000; i++) {
            counts[drawn.candidate(i, 0).level().ordinal()]++;
        }
        assertTrue(counts[0] > counts[1] && counts[1] > counts[2], "small kingdoms are the most common: " + java.util.Arrays.toString(counts));
    }
}
