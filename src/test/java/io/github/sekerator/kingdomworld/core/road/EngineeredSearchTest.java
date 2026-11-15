package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The planner of levelled roads finds its way on made-up land wherever its old search gave up: long ways over hills, through
 * mountains and over rivers with high banks, a step of two blocks either way, a goal by a building, a band of foreign land,
 * a long diagonal, wide water and lava; buildings across the corridor are gone round, across the whole frame they stop the leg
 * with the place; and a point between is held to one column where both legs meet.
 */
class EngineeredSearchTest {

    static LegPlanner.Leg check(String what, LegPlanner.Leg leg, int grade) {
        assertTrue(leg.found(), what + ": " + leg.failure() + " at " + (leg.stuck() == null ? "" : leg.stuck()[0] + " " + leg.stuck()[1]));
        List<RouteCell> cells = leg.cells();
        int since = grade;
        for (int i = 1; i < cells.size(); i++) {
            RouteCell a = cells.get(i - 1);
            RouteCell b = cells.get(i);
            assertEquals(1, Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()), what + ": blocks side by side at " + i);
            int dy = Math.abs(a.y() - b.y());
            assertTrue(dy <= 1, what + ": a step of " + dy + " at " + i);
            if (dy == 1) {
                assertTrue(since >= grade, what + ": a change of height " + since + " blocks after the last at " + i);
                since = 1;
            } else {
                since++;
            }
        }
        LegPlanner.Stats stats = leg.stats();
        System.out.printf("%s: %d blocks, coarse %.1f ms, fine %.1f ms, profile %.1f ms, expanded %s, %.1f MB, fallback %s%n", what,
                cells.size(), stats.coarseNanos() / 1e6, stats.fineNanos() / 1e6, stats.profileNanos() / 1e6, stats.expanded(),
                stats.bytes() / 1e6, stats.fallback());
        return leg;
    }

    private static int hills(int x, int z, int height, int wave) {
        return 64 + (int) Math.round(height * Math.sin(2 * Math.PI * x / wave) * Math.sin(2 * Math.PI * z / wave + 1.3));
    }

    @Test
    void hillsOfSixAndTwelveAreCrossedOverAThousandBlocks() {
        SyntheticLand six = new SyntheticLand((x, z) -> SyntheticLand.land(hills(x, z, 6, 24)));
        check("hills 6/24, 1300", six.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(six.spec(0, 0, 1300, 40)), 2);
        SyntheticLand twelve = new SyntheticLand((x, z) -> SyntheticLand.land(hills(x, z, 12, 20)));
        check("hills 12/20, 1000", twelve.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(twelve.spec(0, 0, 1000, 0)), 2);
    }

    @Test
    void twoThousandBlocksThroughMountainsAndOverARiverWithHighBanks() {
        SyntheticLand land = new SyntheticLand((x, z) -> {
            if (Math.abs(x - 600) < 6) {
                return LandColumn.water(58, 62);
            }
            int ground = hills(x, z, 12, 20) + 1;
            if (x >= 1200 && x < 1350) {
                ground += 40;
            }
            if (Math.abs(x - 600) < 9) {
                ground = 65;
            }
            return SyntheticLand.land(ground);
        });
        LegPlanner.Leg leg = check("mountains and a river, 2000", land.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN)
                .plan(land.spec(0, 0, 2000, 0)), 2);
        assertTrue(leg.cells().stream().anyMatch(cell -> cell.kind() == RouteCell.Kind.BRIDGE), "no bridge over the river");
        LegPlanner.Leg diagonal = check("mountains, 1500 diagonal", land.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN)
                .plan(land.spec(100, 0, 1160, 1060)), 2);
        assertFalse(diagonal.cells().isEmpty());
    }

    @Test
    void highBanksAStepOfTwoAndWideWaterAreNoWall() {
        SyntheticLand banks = new SyntheticLand((x, z) -> x >= 100 && x < 112 ? LandColumn.water(56, 62) : SyntheticLand.land(65));
        check("banks 3 above the water", banks.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(banks.spec(0, 0, 220, 0)), 2);
        SyntheticLand step = new SyntheticLand((x, z) -> SyntheticLand.land(x < 100 ? 64 : 66));
        check("a step of two up", step.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(step.spec(0, 0, 200, 0)), 2);
        check("a step of two down", step.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(step.spec(200, 0, 0, 0)), 2);
        SyntheticLand lake = new SyntheticLand((x, z) -> x >= 100 && x < 140 ? LandColumn.water(50, 62) : SyntheticLand.land(63));
        LegPlanner.Leg wide = check("water 40 wide", lake.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(lake.spec(0, 0, 240, 0)), 2);
        assertTrue(wide.cells().stream().filter(cell -> cell.kind() == RouteCell.Kind.BRIDGE).count() >= 40, "the water is not bridged");
    }

    @Test
    void aGoalByABuildingEightHigherIsReached() {
        // A house of nine by nine with its roof eight above the land, the point of the goal on its roof: it moves to the nearest land.
        SyntheticLand land = new SyntheticLand((x, z) -> Math.abs(x - 500) <= 4 && Math.abs(z) <= 4 ? LandColumn.built(72)
                : SyntheticLand.land(64));
        PlanPoints.Snap end = PlanPoints.end(land, 500, 0);
        assertTrue(end.ok());
        assertEquals(5, Math.max(Math.abs(end.x() - 500), Math.abs(end.z())), "the end moves off the house to the nearest land");
        check("a goal by a roof 8 higher", land.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(land.spec(0, 0, end.x(), end.z())), 2);
    }

    @Test
    void aBandOfForeignLandALongDiagonalAndLavaAreCrossed() {
        UUID them = new UUID(9, 9);
        SyntheticLand foreign = new SyntheticLand((x, z) -> SyntheticLand.land(64), (x, z) -> x >= 350 && x < 650 ? them : null);
        LegPlanner.Leg band = check("a band of 300 foreign", foreign.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(
                foreign.spec(0, 0, 1000, 0)), 2);
        assertTrue(band.cells().stream().anyMatch(cell -> them.equals(cell.owner())), "the band is not crossed");
        SyntheticLand flat = new SyntheticLand((x, z) -> SyntheticLand.land(64));
        check("a diagonal of 800, 800", flat.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(flat.spec(0, 0, 800, 800)), 2);
        SyntheticLand lava = new SyntheticLand((x, z) -> Math.abs(x - 150) < 20 && Math.abs(z) < 60
                ? new LandColumn(63, LandColumn.NONE, 0, 0, LandColumn.LAVA, LandColumn.NONE, 0, LandColumn.NONE, LandColumn.NONE, false)
                : SyntheticLand.land(64));
        LegPlanner.Leg round = check("a lava lake", lava.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(lava.spec(0, 0, 300, 0)), 2);
        assertTrue(round.cells().stream().noneMatch(cell -> Math.abs(cell.x() - 150) < 20 && Math.abs(cell.z()) < 60
                && cell.kind() != RouteCell.Kind.TUNNEL), "the road went over lava");
    }

    @Test
    void buildingsAcrossTheCorridorAreGoneRoundAndAcrossTheFrameStopTheLeg() {
        SyntheticLand row = new SyntheticLand((x, z) -> x >= 200 && x < 204 && z > -70 && z < 70 ? LandColumn.built(70)
                : SyntheticLand.land(64));
        LegPlanner.Leg round = check("a row of houses", row.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(row.spec(0, 0, 400, 0)), 2);
        assertTrue(round.cells().stream().noneMatch(cell -> cell.x() >= 200 && cell.x() < 204 && Math.abs(cell.z()) < 70));
        SyntheticLand wall = new SyntheticLand((x, z) -> x >= 200 && x < 204 ? LandColumn.built(70) : SyntheticLand.land(64));
        LegPlanner.Leg stopped = wall.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(wall.spec(0, 0, 400, 0));
        assertFalse(stopped.found());
        assertEquals(LegPlanner.Reason.BLOCKED, stopped.failure());
        assertTrue(Math.abs(stopped.stuck()[0] - 200) <= 3, "the place it got stuck: " + stopped.stuck()[0]);
    }

    @Test
    void aPointOnTheEdgeOfACliffHoldsBothLegs() {
        SyntheticLand cliff = new SyntheticLand((x, z) -> SyntheticLand.land(x < 200 ? 90 : 64));
        PlanPoints.Snap point = PlanPoints.waypoint(cliff, 199, 30, new int[]{0, 0}, new int[]{400, 0});
        assertTrue(point.ok());
        assertEquals(199, point.x());
        LegPlanner planner = cliff.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN);
        LegPlanner.Leg first = check("to the edge", planner.plan(new LegPlanner.Spec(List.of(new int[]{0, 0, 90}),
                List.of(new EngineeredSearch.Goal(point.x(), point.z(), EngineeredSearch.ANY)), 0, 0, point.x(), point.z())), 2);
        RouteCell end = first.cells().get(first.cells().size() - 1);
        assertEquals(point.x(), end.x());
        assertEquals(point.z(), end.z());
        LegPlanner.Leg second = check("from the edge", planner.plan(new LegPlanner.Spec(List.of(new int[]{end.x(), end.z(), end.y()}),
                cliff.spec(400, 0, 400, 0).goals(), point.x(), point.z(), 400, 0)), 2);
        RouteCell start = second.cells().get(0);
        assertEquals(end.x(), start.x());
        assertEquals(end.z(), start.z());
    }

    @Test
    void theWayAlongTheLandGoesRoundARidgeAndTheStraightOneThroughIt() {
        // A ridge 60 above the land, 100 blocks across the line with sheer sides, its end 100 blocks off the line, within the frame
        // of the leg: going round it is cheaper than a tunnel, which is cheaper than ramps up and down it.
        SyntheticLand ridge = new SyntheticLand((x, z) -> SyntheticLand.land(x >= 150 && x < 250 && z <= 100 ? 124 : 64));
        LegPlanner.Leg along = check("ridge along the land", ridge.planner(RoadClass.GRAVEL, RouteStyle.TERRAIN).plan(ridge.spec(0, 0, 400, 0)), 2);
        LegPlanner.Leg straight = check("ridge straight on", ridge.planner(RoadClass.GRAVEL, RouteStyle.DIRECT).plan(ridge.spec(0, 0, 400, 0)), 2);
        assertTrue(along.cells().stream().noneMatch(cell -> cell.dug() || cell.raised()), "the way along the land climbs or digs the ridge");
        assertTrue(straight.cells().stream().filter(cell -> cell.kind() == RouteCell.Kind.TUNNEL || cell.kind() == RouteCell.Kind.CUT).count() > 80,
                "the straight way does not go through the ridge");
        RoadEstimate a = RoadEstimate.of(along.cells(), RoadClass.GRAVEL, ridge, RoadRules.DEFAULT, EarthRules.DEFAULT, 400, 200, SyntheticLand.US);
        RoadEstimate b = RoadEstimate.of(straight.cells(), RoadClass.GRAVEL, ridge, RoadRules.DEFAULT, EarthRules.DEFAULT, 400, 200,
                SyntheticLand.US);
        assertTrue(b.length() < a.length(), "the straight way is not shorter");
        assertTrue(b.caravanTicks() < a.caravanTicks(), "a caravan is not faster on the straight way");
        assertTrue(b.miners() + b.builders() > a.miners() + a.builders(), "the straight way is not more work");
        SyntheticLand hill = new SyntheticLand((x, z) -> SyntheticLand.land(64 + (int) Math.round(20 * Math.max(0, 1 - Math.abs(x - 200) / 75.0))));
        for (RouteStyle style : new RouteStyle[]{RouteStyle.TERRAIN, RouteStyle.DIRECT}) {
            LegPlanner.Leg over = check("a gentle hill, " + style.id(), hill.planner(RoadClass.GRAVEL, style).plan(hill.spec(0, 0, 400, 0)), 2);
            assertTrue(over.cells().stream().allMatch(cell -> Math.abs(cell.z()) <= 12), "the way goes round a gentle hill");
            assertEquals(0, over.cells().stream().filter(cell -> cell.kind() == RouteCell.Kind.TUNNEL).count(), "a tunnel under a gentle hill");
        }
    }
}
