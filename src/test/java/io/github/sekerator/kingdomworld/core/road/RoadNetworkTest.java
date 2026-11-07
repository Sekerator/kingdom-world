package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkTest {
    private static final UUID KINGDOM = new UUID(3, 3);
    private static final String WORLD = "minecraft:overworld";
    private static final double PACE = 60.0;

    /**
     * A road along the x axis from one x to another, every section paid for.
     */
    private static Road road(long id, RoadClass roadClass, int fromX, int toX, int z, boolean built) {
        List<RouteCell> cells = new ArrayList<>();
        int step = fromX <= toX ? 1 : -1;
        for (int x = fromX; x != toX + step; x += step) {
            cells.add(new RouteCell(x, 64, z, RouteCell.Kind.GROUND, 0, null));
        }
        Road road = new Road(id, KINGDOM, roadClass, WORLD, Endpoint.point(fromX, z), Endpoint.point(toX, z), cells, Road.Mode.OWN, 0,
                Road.Status.BUILDING, List.of());
        if (built) {
            for (Road.Section section : road.sections()) {
                while (!section.done()) {
                    section.markPaid(Map.of());
                }
            }
        } else {
            road.section(0).markPaid(Map.of());
        }
        return road;
    }

    private static TravelPlan plan(List<Road> roads, int toX, int toZ) {
        return RoadNetwork.plan(roads, WORLD, 0, 0, toX, toZ, PACE, RoadRules.DEFAULT, 1, Long.MAX_VALUE);
    }

    @Test
    void withoutRoadsACaravanGoesStraight() {
        TravelPlan plan = plan(List.of(), 600, 0);
        assertEquals(600, plan.length(), 1.0);
        assertEquals(0.0, plan.onRoads(), 1e-9);
        assertEquals(600 / PACE * 1200, plan.ticks(), 2.0);
    }

    @Test
    void aBuiltRoadIsTakenAndIsFaster() {
        Road road = road(1, RoadClass.GRAVEL, 0, 600, 0, true);
        TravelPlan plan = plan(List.of(road), 600, 0);
        assertTrue(plan.onRoads() > 550, "the caravan did not take the road: " + plan.onRoads());
        double straight = 600 / PACE * 1200;
        double expected = straight / RoadRules.DEFAULT.caravanSpeed(RoadClass.GRAVEL);
        assertEquals(expected, plan.ticks(), expected * 0.05);
    }

    @Test
    void aRoadAsideIsTakenWhenTheDetourPays() {
        // A paved road forty blocks to the side of the straight way.
        Road road = road(1, RoadClass.PAVED, 0, 1000, 40, true);
        TravelPlan plan = plan(List.of(road), 1000, 0);
        assertTrue(plan.onRoads() > 900, "the detour over a fast road was not taken");
        // Far to the side it is not worth it.
        Road far = road(2, RoadClass.PATH, 0, 300, 400, true);
        TravelPlan straight = plan(List.of(far), 300, 0);
        assertEquals(0.0, straight.onRoads(), 1e-9);
    }

    @Test
    void anUnbuiltRoadIsNoFasterThanTheCountry() {
        Road road = road(1, RoadClass.PAVED, 0, 600, 0, false);
        TravelPlan plan = plan(List.of(road), 600, 0);
        assertEquals(600 / PACE * 1200, plan.ticks(), 600 / PACE * 1200 * 0.1);
    }

    @Test
    void betterRoadsAreFasterAndWornOnesSlower() {
        long path = plan(List.of(road(1, RoadClass.PATH, 0, 800, 0, true)), 800, 0).ticks();
        long gravel = plan(List.of(road(1, RoadClass.GRAVEL, 0, 800, 0, true)), 800, 0).ticks();
        long paved = plan(List.of(road(1, RoadClass.PAVED, 0, 800, 0, true)), 800, 0).ticks();
        assertTrue(paved < gravel && gravel < path, "classes are not in order: " + path + " " + gravel + " " + paved);
        Road worn = road(1, RoadClass.PAVED, 0, 800, 0, true);
        worn.sections().forEach(section -> section.setCondition(0.0));
        assertTrue(plan(List.of(worn), 800, 0).ticks() > paved, "a worn road is as fast as a new one");
        assertTrue(RoadNetwork.speedFactor(RoadClass.PAVED, 0.0, RoadRules.DEFAULT) > 1.0, "a worn road is no road");
    }

    @Test
    void roadsJoinedAtACityMakeOneWay() {
        Road first = road(1, RoadClass.GRAVEL, 0, 400, 0, true);
        Road second = road(2, RoadClass.GRAVEL, 410, 800, 0, true);
        TravelPlan plan = plan(List.of(first, second), 800, 0);
        assertTrue(plan.onRoads() > 750, "the caravan did not change roads: " + plan.onRoads());
    }

    @Test
    void thePositionFollowsTheWay() {
        Road road = road(1, RoadClass.GRAVEL, 0, 200, 0, true);
        TravelPlan plan = plan(List.of(road), 200, 0);
        double[] start = plan.position(0);
        double[] middle = plan.position(plan.ticks() / 2);
        double[] end = plan.position(plan.ticks());
        assertEquals(0.0, start[0], 1.0);
        assertEquals(100.0, middle[0], 10.0);
        assertEquals(200.0, end[0], 1.0);
        CaravanRoute route = new CaravanRoute(7, WORLD, 1_000, plan, 0);
        assertEquals(0, route.passed(1_000));
        assertEquals(plan.pieces().size(), route.passed(1_000 + plan.ticks()));
    }
}
