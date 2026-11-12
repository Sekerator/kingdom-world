package io.github.sekerator.kingdomworld.core.land;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole plan on one thread, step by step, with the time of each step printed.
 */
class RealmPlanTimingTest {
    /** A survey of about 60 thousand cells. */
    private static final int RADIUS = 7250;

    @Test
    void eightLargeStatesArePlannedWithinTwoSeconds() {
        SurveyRaster raster = SyntheticLand.world(42, RADIUS, 0.08);
        assertTrue(raster.cells() >= 60_000, raster.cells() + " cells");
        long total = run("8 large", raster, StateRules.defaults(0, 0).withRing(StateRules.SPAWN_ZONE, RADIUS).withQuota(0, 0, 8), 42);
        assertTrue(total < 2_000, "the plan took " + total + " ms");
    }

    @Test
    void threeLargeAndFiveSmallStates() {
        SurveyRaster raster = SyntheticLand.world(43, StateRules.RADIUS, 0.08);
        run("3 large, 5 small", raster, StateRules.defaults(0, 0), 43);
    }

    private static long run(String name, SurveyRaster raster, StateRules rules, long seed) {
        long start = System.nanoTime();
        long mark = start;
        StatePlan states = StatePlanner.plan(raster, rules, seed);
        long statesTime = System.nanoTime() - mark;
        mark = System.nanoTime();
        ProvincePlan provinces = ProvincePlanner.plan(states, ProvinceRules.DEFAULT, seed);
        long provincesTime = System.nanoTime() - mark;
        mark = System.nanoTime();
        int[] probe = BorderRefiner.probeCells(provinces);
        long probeTime = System.nanoTime() - mark;
        ChunkProbes probes = BorderRefinerTest.probes(raster, probe);
        mark = System.nanoTime();
        RefinedLand land = BorderRefiner.refine(provinces, probes, seed);
        long refineTime = System.nanoTime() - mark;
        mark = System.nanoTime();
        long[] keys = land.tileKeys();
        int filled = 0;
        for (long key : keys) {
            if (!RefinedLand.empty(land.tile(RefinedLand.tileX(key), RefinedLand.tileZ(key)))) {
                filled++;
            }
        }
        long tilesTime = System.nanoTime() - mark;
        mark = System.nanoTime();
        SettlementPlan settlements = SettlementPlanner.plan(provinces, SettlementRules.DEFAULT, seed);
        long settleTime = System.nanoTime() - mark;
        mark = System.nanoTime();
        RoadPlan roads = RoadPlanner.plan(settlements);
        long roadsTime = System.nanoTime() - mark;
        long total = (System.nanoTime() - start) / 1_000_000;
        long work = total - (tilesTime / 1_000_000);

        StringBuilder out = new StringBuilder();
        out.append(String.format("%s on %d cells (%d x %d):%n", name, raster.cells(), raster.width(), raster.height()));
        out.append(String.format("  states %d ms, provinces %d ms, cells to probe %d ms (%d cells), water %d ms, tiles %d ms (%d of %d filled),"
                        + " settlements %d ms, roads %d ms; all %d ms, without the tiles %d ms%n", ms(statesTime), ms(provincesTime), ms(probeTime),
                probe.length, ms(refineTime), ms(tilesTime), filled, keys.length, ms(settleTime), ms(roadsTime), total, work));
        out.append(String.format("  scale %.3f, neutral land %.3f, ring %d cells, land %d cells%n", states.scale(), states.neutralShare(),
                states.ringCells(), states.landCells()));
        int wild = 0;
        for (PlannedProvince province : provinces.provinces()) {
            if (province.wild()) {
                wild++;
            }
        }
        out.append(String.format("  provinces %d, of them wild %d; edges on a river: %.3f of the borders of provinces, %.3f of all%n",
                provinces.provinces().size(), wild, riverShare(provinces, true), riverShare(provinces, false)));
        for (PlannedState state : states.states()) {
            int s = state.index();
            out.append(String.format("  state %d %s: %d of %d chunks%s, moves %d, provinces %d; cities %d, towns %d, villages %d, hamlets %d%n", s,
                    state.level().id(), state.chunks(), state.targetChunks(), state.cramped() ? " (cramped)" : "", state.moves(),
                    provinces.provincesOf(s).size(), settlements.count(s, Tier.CITY), settlements.count(s, Tier.TOWN),
                    settlements.count(s, Tier.VILLAGE), settlements.count(s, Tier.HAMLET)));
        }
        out.append(String.format("  settlements %d, roads %d (failed %d)%n", settlements.slots().size(), roads.edges().size(), roads.failed()));
        System.out.print(out);
        assertEquals(states.states().size(), countStates(provinces));
        return total;
    }

    /**
     * The share of the edges between two cells of land with a river or a lake on a side: among the edges between two provinces,
     * or among all edges.
     */
    private static double riverShare(ProvincePlan provinces, boolean borders) {
        SurveyRaster raster = provinces.raster();
        long edges = 0;
        long river = 0;
        for (int cell = 0; cell < raster.cells(); cell++) {
            for (int d = 0; d < 4; d += 2) {
                int x = raster.cellX(cell) + LandMath.DX4[d];
                int z = raster.cellZ(cell) + LandMath.DZ4[d];
                if (!raster.contains(x, z)) {
                    continue;
                }
                int next = raster.index(x, z);
                int a = provinces.provinceAt(cell);
                int b = provinces.provinceAt(next);
                if (a == 0 || b == 0 || (borders && a == b)) {
                    continue;
                }
                edges++;
                if (BorderKind.between(raster.terrain(cell), raster.terrain(next)) == BorderKind.RIVER) {
                    river++;
                }
            }
        }
        return edges == 0 ? 0 : (double) river / edges;
    }

    private static int countStates(ProvincePlan provinces) {
        int count = 0;
        for (int s = 0; s < provinces.states().states().size(); s++) {
            if (!provinces.provincesOf(s).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static long ms(long nanos) {
        return nanos / 1_000_000;
    }
}
