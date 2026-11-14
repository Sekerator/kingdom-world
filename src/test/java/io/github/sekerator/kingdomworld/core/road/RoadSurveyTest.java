package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The earthworks of a column from the land under it: soil and rock dug, embankment, piers, lining and torches.
 */
class RoadSurveyTest {
    private static final int H = 3;

    @Test
    void aCuttingDigsTheSoilFromTheGroundDownAndTheRestIsRock() {
        LandColumn hill = LandColumn.land(80, 4);
        RoadLayout.ColumnSurvey cut = RoadSurvey.column(hill, 70, RouteCell.Kind.CUT, RoadLayout.Role.MIDDLE, false, false, H);
        assertEquals(4, cut.soil());
        assertEquals(6, cut.rock(), "ten blocks above the deck, four of them soil");
        assertEquals(0, cut.fill());
        LandColumn hollow = hill.withCave(75, 73, false);
        assertEquals(3, cut.rock() - RoadSurvey.column(hollow, 70, RouteCell.Kind.CUT, RoadLayout.Role.SIDE, false, false, H).rock(),
                "the hollow of a cave is nothing to dig");
        LandColumn deepSoil = LandColumn.land(80, 30);
        assertEquals(LandColumn.DEEP_SOIL, deepSoil.soil());
        RoadLayout.ColumnSurvey deep = RoadSurvey.column(deepSoil, 60, RouteCell.Kind.CUT, RoadLayout.Role.MIDDLE, false, false, H);
        assertEquals(15, deep.soil());
        assertEquals(5, deep.rock(), "soil deeper than fifteen counts as rock: dearer, never cheaper");
    }

    @Test
    void anEmbankmentFillsUpToTheDeckOnAVergeAndBelowItOnTheWay() {
        LandColumn low = LandColumn.land(60, 3);
        assertEquals(3, RoadSurvey.column(low, 64, RouteCell.Kind.FILL, RoadLayout.Role.MIDDLE, false, false, H).fill());
        assertEquals(3, RoadSurvey.column(low, 64, RouteCell.Kind.GROUND, RoadLayout.Role.SIDE, false, false, H).fill());
        assertEquals(4, RoadSurvey.column(low, 64, RouteCell.Kind.FILL, RoadLayout.Role.VERGE, false, false, H).fill());
        LandColumn water = LandColumn.water(55, 62);
        assertEquals(8, RoadSurvey.column(water, 64, RouteCell.Kind.FILL, RoadLayout.Role.MIDDLE, false, false, H).fill(),
                "an embankment through water stands on its bed");
    }

    @Test
    void aBridgeHasAPierFromTheBedOnEveryFourthBlock() {
        List<RouteCell> cells = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            cells.add(new RouteCell(i, 70, 0, RouteCell.Kind.BRIDGE, 0, null));
        }
        List<RoadLayout.ColumnSurvey> surveys = RoadSurvey.survey(cells, RoadClass.GRAVEL, (x, z) -> LandColumn.water(50, 64),
                EarthRules.DEFAULT);
        List<RoadLayout.Block> blocks = RoadLayout.blocks(cells, RoadClass.GRAVEL, RoadLayout.LEVELLED, surveys);
        int piers = 0;
        for (RoadLayout.Block block : blocks) {
            if (block.survey().pier() > 0) {
                piers++;
                assertEquals(RoadLayout.Role.MIDDLE, block.role());
                assertEquals(0, block.cell() % EarthRules.PIER_EVERY);
                assertEquals(19, block.survey().pier(), "from the bed up to the block under the deck");
            }
        }
        assertEquals(3, piers);
        int[] items = RoadLayout.materials(blocks, RoadClass.GRAVEL);
        assertEquals(60, items[RoadLayout.Material.BRIDGE.ordinal()], "a deck on the whole width with its verges");
        assertEquals(24, items[RoadLayout.Material.RAIL.ordinal()], "a rail on each verge");
        assertEquals(57, items[RoadLayout.Material.PIER.ordinal()]);
    }

    @Test
    void aTunnelIsDugToItsHeightItsRoofOfSoilIsProppedAndItIsLit() {
        LandColumn mountain = LandColumn.land(100, 3);
        RoadLayout.ColumnSurvey rock = RoadSurvey.column(mountain, 70, RouteCell.Kind.TUNNEL, RoadLayout.Role.MIDDLE, false, false, H);
        assertEquals(0, rock.soil());
        assertEquals(3, rock.rock());
        assertEquals(0, rock.lining(), "a roof of rock holds");
        RoadLayout.ColumnSurvey shallow = RoadSurvey.column(LandColumn.land(76, 3), 70, RouteCell.Kind.TUNNEL, RoadLayout.Role.MIDDLE,
                false, false, H);
        assertEquals(1, shallow.lining(), "a roof in the soil is propped");
        List<RouteCell> cells = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            cells.add(new RouteCell(i, 70, 0, RouteCell.Kind.TUNNEL, 27, null));
        }
        List<RoadLayout.ColumnSurvey> surveys = RoadSurvey.survey(cells, RoadClass.GRAVEL, (x, z) -> mountain, EarthRules.DEFAULT);
        List<RoadLayout.Block> blocks = RoadLayout.blocks(cells, RoadClass.GRAVEL, RoadLayout.LEVELLED, surveys);
        long torches = blocks.stream().filter(block -> block.survey().light()).count();
        assertEquals(5, torches, "one torch every eight blocks");
        assertTrue(blocks.stream().filter(block -> block.survey().light()).allMatch(block -> block.role() == RoadLayout.Role.VERGE
                && block.cell() % 8 == 4));
    }

    @Test
    void aCaveUnderTheDeckIsCoveredAndAColumnOnARoadIsLeftAlone() {
        LandColumn cave = LandColumn.land(70, 2).withCave(69, 60, false);
        assertEquals(1, RoadSurvey.column(cave, 70, RouteCell.Kind.GROUND, RoadLayout.Role.MIDDLE, false, false, H).lining());
        RoadLayout.ColumnSurvey road = RoadSurvey.column(LandColumn.land(70, 2).withRoad(70, true), 70, RouteCell.Kind.CUT,
                RoadLayout.Role.SIDE, false, false, H);
        assertTrue(road.onRoad());
        assertFalse(road.dug());
        assertEquals(RoadLayout.ColumnSurvey.EMPTY, RoadSurvey.column(null, 70, RouteCell.Kind.CUT, RoadLayout.Role.SIDE, false, false, H));
    }
}
