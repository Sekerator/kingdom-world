package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatePlannerTest {
    private static final long SEED = 0x51A7E5L;

    @Test
    void aRiverBetweenTwoStatesRunsThroughTheGapBetweenThem() {
        SurveyRaster raster = SyntheticLand.fill(SyntheticLand.land(200, 80), 100, 0, 100, 79, TerrainClass.RIVER).build();
        StateRules rules = SyntheticLand.openRules(0, 0, 2, 16, 16, 9_000 * 16);
        // The left seed is seven cells nearer the river than the right one.
        StatePlan plan = StatePlanner.plan(raster, rules, SEED, new int[]{raster.index(55, 40), raster.index(152, 40)});
        assertEquals(2, plan.states().size());
        for (int cell = 0; cell < raster.cells(); cell++) {
            if (plan.stateAt(cell) == 0) {
                assertTrue(raster.cellX(cell) < 100, "the left state stays on its bank");
            } else if (plan.stateAt(cell) == 1) {
                assertTrue(raster.cellX(cell) > 100, "the right state stays on its bank");
            }
        }
        for (int state = 0; state < 2; state++) {
            List<Integer> border = SyntheticLand.borderCells(plan, state);
            int onRiver = 0;
            for (int cell : border) {
                if (Math.abs(raster.cellX(cell) - 100) <= 5) {
                    onRiver++;
                }
            }
            assertTrue(onRiver >= 0.6 * border.size(), "state " + state + ": " + onRiver + " of " + border.size() + " on the river");
        }
        assertGap(plan);
    }

    @Test
    void theSeaIsNeverCrossed() {
        SurveyRaster raster = SyntheticLand.fill(SyntheticLand.land(200, 80), 90, 0, 109, 79, TerrainClass.OCEAN).build();
        StateRules rules = SyntheticLand.openRules(0, 0, 1, 16, 16, 9_000 * 16);
        StatePlan plan = StatePlanner.plan(raster, rules, SEED, new int[]{raster.index(40, 40)});
        PlannedState state = plan.state(0);
        for (int cell = 0; cell < raster.cells(); cell++) {
            if (plan.stateAt(cell) == 0) {
                assertTrue(raster.cellX(cell) < 90, "the state does not cross the sea");
            }
        }
        assertEquals(90 * 80, state.cells(), "the state takes all its shore");
        assertTrue(state.cramped(), "a state hemmed in by the sea is cramped");
    }

    @Test
    void theStatesOfAWorldKeepTheRules() {
        SurveyRaster raster = SyntheticLand.world(SEED, StateRules.RADIUS, 0);
        StateRules rules = StateRules.defaults(0, 0);
        StatePlan plan = StatePlanner.plan(raster, rules, SEED);
        assertEquals(8, plan.states().size());
        assertEquals(0, plan.missing());
        assertEquals(3, SyntheticLand.count(plan, NpcLevel.LARGE));
        assertEquals(5, SyntheticLand.count(plan, NpcLevel.SMALL));
        for (int i = 0; i < 3; i++) {
            assertEquals(NpcLevel.LARGE, plan.state(i).level(), "the large states come first");
        }
        assertFalse(plan.shrunk(), "three large and five small states fit a ring of 7000 blocks");
        for (int cell = 0; cell < raster.cells(); cell++) {
            if (plan.stateAt(cell) >= 0) {
                assertTrue(raster.distanceTo(cell, 0, 0) >= StateRules.SPAWN_ZONE, "no state in the spawn zone");
                assertTrue(raster.terrain(cell).passable());
            }
        }
        for (PlannedState state : plan.states()) {
            int target = state.targetCells();
            assertTrue(Math.abs(state.cells() - target) <= 0.15 * target,
                    "state " + state.index() + " got " + state.cells() + " of " + target + " cells");
            assertTrue(state.targetChunks() >= rules.minChunks(state.level()) && state.targetChunks() <= rules.maxChunks(state.level()));
            assertTrue(SyntheticLand.connected(plan, state.index()), "state " + state.index() + " hangs together");
            double radius = StatePlanner.radiusOf(state.targetChunks());
            double fromSpawn = Math.hypot(state.seedX(), state.seedZ());
            assertTrue(fromSpawn >= StateRules.SPAWN_ZONE + radius - 64 && fromSpawn <= StateRules.RADIUS - radius / 2 + 64,
                    "seed of state " + state.index() + " at " + fromSpawn);
            assertFalse(state.throneCandidates().isEmpty());
            for (int throne : state.throneCandidates()) {
                assertEquals(state.index(), plan.stateAt(throne));
                assertTrue(raster.terrain(throne).settles());
            }
        }
        assertTrue(plan.neutralShare() >= StateRules.NEUTRAL_SHARE, "neutral land " + plan.neutralShare());
        assertGap(plan);
    }

    @Test
    void theSameSeedGivesTheSamePlan() {
        SurveyRaster raster = SyntheticLand.world(7, 5000, 0.1);
        StateRules rules = StateRules.defaults(0, 0).withRing(StateRules.SPAWN_ZONE, 5000);
        StatePlan first = StatePlanner.plan(raster, rules, 99);
        StatePlan second = StatePlanner.plan(raster, rules, 99);
        assertEquals(first.states(), second.states());
        for (int cell = 0; cell < raster.cells(); cell++) {
            assertEquals(first.stateAt(cell), second.stateAt(cell));
        }
        StatePlan other = StatePlanner.plan(raster, rules, 100);
        assertFalse(first.states().equals(other.states()), "another seed gives another plan");
    }

    @Test
    void landOfPlayersIsNeverTaken() {
        SurveyRaster world = SyntheticLand.world(SEED, StateRules.RADIUS, 0);
        SurveyRaster.Builder builder = new SurveyRaster.Builder(world.originX(), world.originZ(), world.width(), world.height());
        int marked = 0;
        for (int cell = 0; cell < world.cells(); cell++) {
            int x = world.cellX(cell);
            int z = world.cellZ(cell);
            builder.set(x, z, world.terrain(cell), world.groundHeight(cell));
            // A broad band of the land of players across the ring.
            if (Math.abs(world.centerX(cell)) < 1500) {
                builder.mark(x, z, SurveyRaster.PLAYER);
                marked++;
            }
        }
        SurveyRaster raster = builder.build();
        StatePlan plan = StatePlanner.plan(raster, StateRules.defaults(0, 0), SEED);
        assertTrue(marked > 0);
        assertFalse(plan.states().isEmpty());
        for (int cell = 0; cell < raster.cells(); cell++) {
            if (raster.player(cell)) {
                assertEquals(-1, plan.stateAt(cell), "the land of a player is no state's");
            }
        }
    }

    @Test
    void targetsTooLargeForTheRingShrink() {
        SurveyRaster raster = SyntheticLand.world(SEED, StateRules.RADIUS, 0);
        StateRules rules = StateRules.defaults(0, 0).withQuota(0, 0, 8);
        StatePlan plan = StatePlanner.plan(raster, rules, SEED);
        assertTrue(plan.shrunk());
        long targets = 0;
        for (PlannedState state : plan.states()) {
            targets += state.targetCells();
        }
        assertTrue(targets <= rules.ringShare() * plan.ringCells() + plan.states().size(), "the states take at most the share of the ring");
        assertTrue(plan.neutralShare() >= StateRules.NEUTRAL_SHARE);
        assertGap(plan);
    }

    /**
     * No two cells of different states closer, edge to edge, than the gap and the reach of the borders on both sides.
     */
    static void assertGap(StatePlan plan) {
        SurveyRaster raster = plan.raster();
        double least = plan.rules().gapBlocks() + 2.0 * BorderRefiner.REACH;
        int count = plan.states().size();
        List<List<Integer>> borders = new java.util.ArrayList<>();
        for (int s = 0; s < count; s++) {
            borders.add(SyntheticLand.borderCells(plan, s));
        }
        for (int a = 0; a < count; a++) {
            for (int b = a + 1; b < count; b++) {
                for (int cell : borders.get(a)) {
                    for (int other : borders.get(b)) {
                        assertTrue(SyntheticLand.edgeDistance(raster, cell, other) >= least - 1e-9,
                                "states " + a + " and " + b + " closer than the gap");
                    }
                }
            }
        }
    }
}
