package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.road.RoadClass;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPlannerTest {
    @Test
    void theWayGoesRoundTheSea() {
        // A wall of sea across the land with an opening at its southern end.
        SurveyRaster raster = SyntheticLand.fill(SyntheticLand.land(60, 40), 30, 0, 31, 33, TerrainClass.OCEAN).build();
        RoadPlanner.Search search = new RoadPlanner.Search(raster);
        int start = raster.index(10, 5);
        int goal = raster.index(50, 5);
        int[] path = search.route(start, goal);
        assertNotNull(path);
        assertEquals(start, path[0]);
        assertEquals(goal, path[path.length - 1]);
        boolean south = false;
        for (int cell : path) {
            assertTrue(raster.terrain(cell).passable(), "the way never crosses the sea");
            south |= raster.cellZ(cell) >= 34;
        }
        assertTrue(south, "the way goes round the end of the sea");
        int[] points = RoadPlanner.points(raster, path);
        for (int i = 1; i < points.length; i++) {
            assertTrue(RoadPlanner.clear(raster, points[i - 1], points[i]), "no piece of the road crosses the sea");
        }
        assertTrue(Arrays.equals(path, search.route(start, goal)), "the search gives the same way again");
        SurveyRaster walled = SyntheticLand.fill(SyntheticLand.land(60, 40), 30, 0, 31, 39, TerrainClass.OCEAN).build();
        assertEquals(null, new RoadPlanner.Search(walled).route(start, goal), "no way across a sea from end to end");
    }

    @Test
    void theRoadsOfAWorldLinkEverySettlement() {
        ProvincePlan provinces = SettlementPlannerTest.provinces(11);
        SettlementPlan settlements = SettlementPlanner.plan(provinces, SettlementRules.DEFAULT, 11);
        RoadPlan roads = RoadPlanner.plan(settlements);
        SurveyRaster raster = provinces.raster();
        int n = settlements.slots().size();
        assertTrue(roads.edges().size() <= 1.5 * n, roads.edges().size() + " roads for " + n + " settlements");
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (PlannedEdge edge : roads.edges()) {
            parent[find(parent, edge.from())] = find(parent, edge.to());
            SettlementSlot from = settlements.slot(edge.from());
            SettlementSlot to = settlements.slot(edge.to());
            assertEquals(from.x(), edge.x(0));
            assertEquals(from.z(), edge.z(0));
            assertEquals(to.x(), edge.x(edge.points() - 1));
            assertEquals(to.z(), edge.z(edge.points() - 1));
            assertEquals(RoadPlanner.classOf(from.tier(), to.tier()), edge.roadClass());
            for (int i = 1; i < edge.points(); i++) {
                double step = Math.hypot(edge.x(i) - edge.x(i - 1), edge.z(i) - edge.z(i - 1));
                assertTrue(step <= RoadPlanner.MAX_STEP_CELLS * SurveyRaster.CELL + 1e-9, "points " + step + " blocks apart");
                int a = raster.cellAt(edge.x(i - 1), edge.z(i - 1));
                int b = raster.cellAt(edge.x(i), edge.z(i));
                assertTrue(RoadPlanner.clear(raster, a, b), "the road keeps off the sea");
            }
        }
        int capital = find(parent, 0);
        for (int i = 0; i < n; i++) {
            if (find(parent, i) != capital) {
                // Only a state across the sea may stand apart; within a state every settlement is linked.
                SettlementSlot slot = settlements.slot(i);
                int ownCapital = 0;
                for (SettlementSlot other : settlements.slots()) {
                    if (other.state() == slot.state() && other.tier() == Tier.CAPITAL) {
                        ownCapital = other.index();
                    }
                }
                assertEquals(find(parent, ownCapital), find(parent, i), "settlement " + i + " is linked to its capital");
                assertTrue(roads.failed() > 0);
            }
        }
        boolean paved = false;
        boolean path = false;
        for (PlannedEdge edge : roads.edges()) {
            paved |= edge.roadClass() == RoadClass.PAVED;
            path |= edge.roadClass() == RoadClass.PATH;
        }
        assertTrue(paved && path);
    }

    @Test
    void onLandWithoutSeaEverySettlementOfTheWorldIsLinked() {
        SurveyRaster raster = SyntheticLand.world(21, StateRules.RADIUS, 0);
        StatePlan states = StatePlanner.plan(raster, StateRules.defaults(0, 0), 21);
        ProvincePlan provinces = ProvincePlanner.plan(states, ProvinceRules.DEFAULT, 21);
        SettlementPlan settlements = SettlementPlanner.plan(provinces, SettlementRules.DEFAULT, 21);
        RoadPlan roads = RoadPlanner.plan(settlements);
        assertEquals(0, roads.failed());
        int n = settlements.slots().size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (PlannedEdge edge : roads.edges()) {
            parent[find(parent, edge.from())] = find(parent, edge.to());
        }
        for (int i = 0; i < n; i++) {
            assertEquals(find(parent, 0), find(parent, i), "settlement " + i + " is linked");
        }
        assertTrue(roads.edges().size() <= 1.5 * n);
        RoadPlan again = RoadPlanner.plan(settlements);
        assertEquals(roads.edges(), again.edges());
        assertTrue(Arrays.equals(roads.edges().get(0).xs(), again.edges().get(0).xs()));
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
