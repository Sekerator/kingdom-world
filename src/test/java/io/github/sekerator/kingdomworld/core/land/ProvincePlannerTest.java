package io.github.sekerator.kingdomworld.core.land;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvincePlannerTest {
    private static final long SEED = 0xBEEFL;

    /**
     * A state of 30 by 12 cells of land with a river down its middle, split into two provinces.
     */
    static ProvincePlan riverState() {
        SurveyRaster raster = SyntheticLand.fill(SyntheticLand.land(30, 12), 15, 0, 15, 11, TerrainClass.RIVER).build();
        StatePlan states = StatePlanner.plan(raster, SyntheticLand.openRules(0, 0, 1, 16, 16, 400 * 16), SEED,
                new int[]{raster.index(5, 6)});
        return ProvincePlanner.plan(states, new ProvinceRules(180, 90, 360), SEED);
    }

    @Test
    void aRiverAcrossAStateDividesTwoProvinces() {
        ProvincePlan plan = riverState();
        SurveyRaster raster = plan.raster();
        assertEquals(raster.cells(), plan.states().state(0).cells(), "the state takes the whole island");
        assertEquals(2, plan.provincesOf(0).size());
        int heart = plan.heart(0);
        int other = plan.provincesOf(0).get(1);
        assertEquals(ProvinceKind.HEART, plan.province(heart).kind());
        assertEquals(heart, plan.provinceAt(plan.throneCell(0)), "the heart holds the throne");
        int edges = 0;
        int onRiver = 0;
        for (int cell = 0; cell < raster.cells(); cell++) {
            int x = raster.cellX(cell);
            int z = raster.cellZ(cell);
            int[][] steps = {{1, 0}, {0, 1}};
            for (int[] step : steps) {
                if (!raster.contains(x + step[0], z + step[1])) {
                    continue;
                }
                int next = raster.index(x + step[0], z + step[1]);
                if (plan.provinceAt(cell) != plan.provinceAt(next)) {
                    edges++;
                    if (raster.terrain(cell) == TerrainClass.RIVER || raster.terrain(next) == TerrainClass.RIVER) {
                        onRiver++;
                    }
                }
            }
        }
        assertTrue(edges > 0);
        assertTrue(onRiver >= 0.6 * edges, onRiver + " of " + edges + " edges on the river");
        assertEquals(BorderKind.RIVER, plan.province(heart).borderWith(other));
        assertEquals(BorderKind.RIVER, plan.province(other).borderWith(heart));
    }

    @Test
    void theProvincesOfAWorldKeepTheRules() {
        StatePlan states = StatePlanner.plan(SyntheticLand.world(SEED, StateRules.RADIUS, 0.08), StateRules.defaults(0, 0), SEED);
        ProvincePlan plan = ProvincePlanner.plan(states, ProvinceRules.DEFAULT, SEED);
        SurveyRaster raster = plan.raster();
        assertEquals(8, states.states().size());
        for (int s = 0; s < states.states().size(); s++) {
            List<Integer> mine = plan.provincesOf(s);
            int cells = states.state(s).cells();
            assertTrue(Math.abs(mine.size() - cells / 100.0) <= 3, "state " + s + " of " + cells + " cells has " + mine.size() + " provinces");
            int hearts = 0;
            int frontier = 0;
            for (int id : mine) {
                PlannedProvince province = plan.province(id);
                assertEquals(s, province.state());
                assertTrue(province.cells() >= 50 && province.cells() <= 160, "province " + id + " of " + province.cells() + " cells");
                if (province.kind() == ProvinceKind.HEART) {
                    hearts++;
                } else if (province.kind() == ProvinceKind.FRONTIER) {
                    frontier++;
                } else {
                    assertEquals(ProvinceKind.CORE, province.kind());
                }
            }
            assertEquals(1, hearts);
            assertEquals(plan.heart(s), plan.provinceAt(plan.throneCell(s)), "the heart holds the throne");
            assertEquals(ProvincePlanner.frontierCount(mine.size()), frontier);
            assertTrue(frontier >= 1 && frontier <= 3);
        }
        int wild = 0;
        int wildInRange = 0;
        for (PlannedProvince province : plan.provinces()) {
            if (province.wild()) {
                wild++;
                assertTrue(province.cells() >= 50, "wild province " + province.id() + " of " + province.cells() + " cells");
                if (province.cells() <= 160) {
                    wildInRange++;
                }
            }
            assertEquals(province.id(), plan.provinceAt(province.centerCell()) == province.id() ? province.id() : -1,
                    "the middle of a province lies in it");
            for (int i = 0; i < province.neighbours().size(); i++) {
                PlannedProvince neighbour = plan.province(province.neighbours().get(i));
                assertEquals(province.borderKinds().get(i), neighbour.borderWith(province.id()), "borders agree on both sides");
            }
        }
        assertTrue(wild > 0);
        assertTrue(wildInRange >= 0.9 * wild, wildInRange + " of " + wild + " wild provinces in range");
        for (int cell = 0; cell < raster.cells(); cell++) {
            int province = plan.provinceAt(cell);
            int state = states.stateAt(cell);
            if (state >= 0) {
                assertEquals(state, plan.stateOf(province), "every cell of a state is in a province of it");
            } else if (states.inSpawnZone(cell) || !raster.terrain(cell).passable()) {
                assertEquals(0, province, "no province in the spawn zone or the sea");
            } else {
                assertTrue(province > 0 && plan.province(province).wild(), "the neutral land is wild");
            }
        }
    }

    @Test
    void theSameSeedGivesTheSameProvinces() {
        StatePlan states = StatePlanner.plan(SyntheticLand.world(3, 5000, 0.1), StateRules.defaults(0, 0).withRing(1500, 5000), 3);
        ProvincePlan first = ProvincePlanner.plan(states, ProvinceRules.DEFAULT, 3);
        ProvincePlan second = ProvincePlanner.plan(states, ProvinceRules.DEFAULT, 3);
        assertEquals(first.provinces(), second.provinces());
        for (int cell = 0; cell < states.raster().cells(); cell++) {
            assertEquals(first.provinceAt(cell), second.provinceAt(cell));
        }
    }

    @Test
    void aThroneChosenByTheServerMakesTheHeart() {
        StatePlan states = StatePlanner.plan(SyntheticLand.world(SEED, StateRules.RADIUS, 0), StateRules.defaults(0, 0), SEED);
        PlannedState state = states.state(0);
        int chosen = state.throneCandidates().get(state.throneCandidates().size() - 1);
        int[] thrones = new int[states.states().size()];
        java.util.Arrays.fill(thrones, -1);
        thrones[0] = chosen;
        ProvincePlan plan = ProvincePlanner.plan(states, thrones, ProvinceRules.DEFAULT, SEED);
        assertEquals(chosen, plan.throneCell(0));
        assertEquals(plan.heart(0), plan.provinceAt(chosen));
        assertNotNull(plan.province(plan.heart(0)));
        assertFalse(plan.provincesOf(0).isEmpty());
    }

    @Test
    void aThroneNobodyJudgedDoesNotComeDownOnGeneratedLand() {
        // The land around the seed was generated (players went there): the state has no candidate for a throne, and the one the
        // provinces take must stand on land never generated, past the marked cells.
        SurveyRaster.Builder builder = SyntheticLand.fill(SyntheticLand.land(30, 12), 15, 0, 15, 11, TerrainClass.RIVER);
        for (int z = 0; z < 12; z++) {
            for (int x = 0; x <= 13; x++) {
                builder.mark(x, z, SurveyRaster.GENERATED);
            }
        }
        SurveyRaster raster = builder.build();
        StatePlan states = StatePlanner.plan(raster, SyntheticLand.openRules(0, 0, 1, 16, 16, 400 * 16), SEED,
                new int[]{raster.index(5, 6)});
        assertTrue(states.state(0).throneCandidates().isEmpty(), "the state has candidates: " + states.state(0).throneCandidates());
        ProvincePlan plan = ProvincePlanner.plan(states, new ProvinceRules(180, 90, 360), SEED);
        int throne = plan.throneCell(0);
        assertFalse(raster.generated(throne), "the throne stands on generated land at " + raster.cellX(throne) + ", " + raster.cellZ(throne));
        assertTrue(raster.terrain(throne).settles(), "the throne stands on land");
        assertEquals(0, states.stateAt(throne), "the throne stands in its state");
    }

    @Test
    void aThroneOnWaterIsNotTaken() {
        ProvincePlan rivers = riverState();
        StatePlan states = rivers.states();
        int water = states.raster().index(15, 6);
        assertEquals(TerrainClass.RIVER, states.raster().terrain(water));
        assertEquals(0, states.stateAt(water), "the river lies in the state");
        ProvincePlan plan = ProvincePlanner.plan(states, new int[]{water}, new ProvinceRules(180, 90, 360), SEED);
        assertTrue(states.raster().terrain(plan.throneCell(0)).settles(), "the throne stands on land");
        assertEquals(plan.heart(0), plan.provinceAt(plan.throneCell(0)));
        // Every throne of a world stands on land, whatever the server gave.
        StatePlan world = StatePlanner.plan(SyntheticLand.world(SEED, StateRules.RADIUS, 0.3), StateRules.defaults(0, 0), SEED);
        int[] wet = new int[world.states().size()];
        for (int s = 0; s < wet.length; s++) {
            wet[s] = -1;
            for (int cell = 0; cell < world.raster().cells() && wet[s] < 0; cell++) {
                if (world.stateAt(cell) == s && !world.raster().terrain(cell).settles()) {
                    wet[s] = cell;
                }
            }
        }
        ProvincePlan wide = ProvincePlanner.plan(world, wet, ProvinceRules.DEFAULT, SEED);
        for (int s = 0; s < wet.length; s++) {
            assertTrue(world.raster().terrain(wide.throneCell(s)).settles(), "the throne of state " + s + " stands on land");
        }
    }
}
