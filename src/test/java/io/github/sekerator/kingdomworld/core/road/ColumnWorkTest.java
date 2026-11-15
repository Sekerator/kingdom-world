package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The work and items of a column of a levelled road, in seconds of work: builders dig soil, miners rock, and everything put in
 * takes its seconds and its item.
 */
class ColumnWorkTest {
    private static final RoadRules ROADS = RoadRules.DEFAULT;
    private static final EarthRules EARTH = EarthRules.DEFAULT;

    private static RoadLayout.Block block(RouteCell.Kind kind, RoadLayout.Role role, RoadLayout.ColumnSurvey survey) {
        return new RoadLayout.Block(0, 0, 64, 0, kind, 0, role, survey);
    }

    private static RoadLayout.ColumnSurvey survey(int soil, int rock, int logs, int fill, int pier, int lining, boolean light) {
        return new RoadLayout.ColumnSurvey(soil, rock, logs, fill, pier, lining, light, false);
    }

    @Test
    void aBlockOfRoadOnLevelGroundCostsWhatTheSearchCountsForIt() {
        double sum = 0;
        for (RoadLayout.Role role : new RoadLayout.Role[]{RoadLayout.Role.MIDDLE, RoadLayout.Role.SIDE, RoadLayout.Role.SIDE,
                RoadLayout.Role.VERGE, RoadLayout.Role.VERGE}) {
            sum += ColumnWork.of(block(RouteCell.Kind.GROUND, role, RoadLayout.ColumnSurvey.EMPTY), RoadClass.GRAVEL, ROADS, EARTH).effort();
        }
        assertEquals(12.0, sum, 1e-9, "three blocks of gravel at three seconds and an item each, verges free");
        assertEquals(sum, EarthRules.base(RoadClass.GRAVEL, ROADS), 1e-9);
        assertEquals(1.0, EarthRules.base(RoadClass.PATH, ROADS), 1e-9);
        assertEquals(18.0, EarthRules.base(RoadClass.PAVED, ROADS), 1e-9);
        ColumnWork path = ColumnWork.of(block(RouteCell.Kind.GROUND, RoadLayout.Role.MIDDLE, RoadLayout.ColumnSurvey.EMPTY), RoadClass.PATH,
                ROADS, EARTH);
        assertEquals(1.0, path.builders(), 1e-9);
        assertEquals(0, path.items(RoadLayout.Material.SURFACE), "a path takes no item");
    }

    @Test
    void theWeightOfLengthGrowsWithTheStyle() {
        assertEquals(0.0, EARTH.lambda(RoadClass.GRAVEL, RouteStyle.TERRAIN, ROADS), 1e-9);
        assertEquals(24.0, EARTH.lambda(RoadClass.GRAVEL, RouteStyle.MODERATE, ROADS), 1e-9);
        assertEquals(240.0, EARTH.lambda(RoadClass.GRAVEL, RouteStyle.DIRECT, ROADS), 1e-9);
        assertEquals(2, EARTH.grade(RoadClass.GRAVEL));
        assertEquals(5, EarthRules.fullWidth(RoadClass.GRAVEL));
        assertEquals(3, EarthRules.fullWidth(RoadClass.PATH));
    }

    @Test
    void soilIsDugByBuildersAndRockByMinersLongerInATunnel() {
        ColumnWork cut = ColumnWork.of(block(RouteCell.Kind.CUT, RoadLayout.Role.MIDDLE, survey(2, 3, 0, 0, 0, 0, false)), RoadClass.GRAVEL,
                ROADS, EARTH);
        assertEquals(2 * 2.0 + 3.0, cut.builders(), 1e-9);
        assertEquals(3 * 5.0, cut.miners(), 1e-9);
        ColumnWork tunnel = ColumnWork.of(block(RouteCell.Kind.TUNNEL, RoadLayout.Role.SIDE, survey(1, 2, 0, 0, 0, 1, false)),
                RoadClass.GRAVEL, ROADS, EARTH);
        assertEquals(1 * 2.0 * 1.5 + 1 * 2.0 + 3.0, tunnel.builders(), 1e-9);
        assertEquals(2 * 5.0 * 1.5, tunnel.miners(), 1e-9);
        assertEquals(1, tunnel.items(RoadLayout.Material.LINING));
        assertEquals(tunnel.builders() + tunnel.miners() * 2.0, tunnel.buildersAlone(EARTH), 1e-9,
                "without miners the builders dig the rock twice as long");
        ColumnWork verge = ColumnWork.of(block(RouteCell.Kind.TUNNEL, RoadLayout.Role.VERGE, survey(0, 3, 0, 0, 0, 0, true)),
                RoadClass.GRAVEL, ROADS, EARTH);
        assertEquals(0.5, verge.builders(), 1e-9, "a torch on the verge, no surface");
        assertEquals(1, verge.items(RoadLayout.Material.TORCH));
        assertEquals(0, verge.items(RoadLayout.Material.SURFACE));
    }

    @Test
    void anEmbankmentABridgeAndItsPierTakeTheirItems() {
        ColumnWork fill = ColumnWork.of(block(RouteCell.Kind.FILL, RoadLayout.Role.MIDDLE, survey(0, 0, 2, 3, 0, 0, false)), RoadClass.GRAVEL,
                ROADS, EARTH);
        assertEquals(2 * 1.0 + 3 * 2.0 + 3.0, fill.builders(), 1e-9);
        assertEquals(3, fill.items(RoadLayout.Material.FILL));
        assertEquals(1, fill.items(RoadLayout.Material.SURFACE));
        ColumnWork pathFill = ColumnWork.of(block(RouteCell.Kind.FILL, RoadLayout.Role.MIDDLE, survey(0, 0, 0, 1, 0, 0, false)),
                RoadClass.PATH, ROADS, EARTH);
        assertEquals(2, pathFill.items(RoadLayout.Material.FILL), "the embankment of a path is topped with earth");
        ColumnWork pier = ColumnWork.of(block(RouteCell.Kind.BRIDGE, RoadLayout.Role.MIDDLE, survey(0, 0, 0, 0, 5, 0, false)),
                RoadClass.GRAVEL, ROADS, EARTH);
        assertEquals(5 * 3.0 + 3.0, pier.builders(), 1e-9);
        assertEquals(5, pier.items(RoadLayout.Material.PIER));
        assertEquals(1, pier.items(RoadLayout.Material.BRIDGE));
        ColumnWork rail = ColumnWork.of(block(RouteCell.Kind.BRIDGE, RoadLayout.Role.VERGE, RoadLayout.ColumnSurvey.EMPTY), RoadClass.PAVED,
                ROADS, EARTH);
        assertEquals(3.0 + 1.0, rail.builders(), 1e-9);
        assertEquals(1, rail.items(RoadLayout.Material.RAIL));
        ColumnWork pathRail = ColumnWork.of(block(RouteCell.Kind.BRIDGE, RoadLayout.Role.VERGE, RoadLayout.ColumnSurvey.EMPTY),
                RoadClass.PATH, ROADS, EARTH);
        assertEquals(0, pathRail.items(RoadLayout.Material.RAIL), "a bridge of a path has no rails");
        assertEquals(1, pathRail.items(RoadLayout.Material.BRIDGE));
    }

    @Test
    void aColumnOnAnotherRoadAndOneOfTheFirstLayoutCostNothing() {
        RoadLayout.ColumnSurvey onRoad = new RoadLayout.ColumnSurvey(4, 4, 0, 0, 0, 0, false, true);
        assertSame(ColumnWork.NONE, ColumnWork.of(block(RouteCell.Kind.CUT, RoadLayout.Role.MIDDLE, onRoad), RoadClass.GRAVEL, ROADS, EARTH));
        assertSame(ColumnWork.NONE, ColumnWork.of(new RoadLayout.Block(0, 0, 64, 0, RouteCell.Kind.GROUND, 0, false), RoadClass.GRAVEL,
                ROADS, EARTH));
        assertEquals(0, new RoadLayout.Block(0, 0, 64, 0, RouteCell.Kind.CUT, 0, RoadLayout.Role.MIDDLE, onRoad).needs(RoadClass.GRAVEL)
                .size());
    }

    @Test
    void theEarthworksLeaveTheSurfaceOut() {
        RoadLayout.Block cut = block(RouteCell.Kind.CUT, RoadLayout.Role.MIDDLE, survey(2, 1, 0, 0, 0, 0, false));
        assertEquals(2 * 2.0 + 5.0, ColumnWork.earthwork(cut, RoadClass.GRAVEL, ROADS, EARTH), 1e-9);
        assertEquals(0.0, ColumnWork.earthwork(block(RouteCell.Kind.GROUND, RoadLayout.Role.MIDDLE, RoadLayout.ColumnSurvey.EMPTY),
                RoadClass.GRAVEL, ROADS, EARTH), 1e-9);
    }
}
