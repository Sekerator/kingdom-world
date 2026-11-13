package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.land.SurveyClassifier.Probe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyClassifierTest {
    private static final int SEA = 63;
    private static final int DRY = 70;

    private static TerrainClass cell(int floor, Probe... probes) {
        return SurveyClassifier.cell(probes, floor, SEA);
    }

    @Test
    void mostOfTheCellInTheSeaIsTheSea() {
        assertEquals(TerrainClass.OCEAN, cell(40, Probe.OCEAN, Probe.OCEAN, Probe.OCEAN, Probe.OCEAN));
        assertEquals(TerrainClass.OCEAN, cell(40, Probe.OCEAN, Probe.OCEAN, Probe.OCEAN, Probe.RIVER), "the sea before a river");
        assertEquals(TerrainClass.OCEAN, cell(DRY, Probe.OCEAN, Probe.OCEAN, Probe.OCEAN, Probe.LAND), "whatever the height");
    }

    @Test
    void aRiverAnywhereInTheCellMakesItARiver() {
        assertEquals(TerrainClass.RIVER, cell(DRY, Probe.RIVER, Probe.LAND, Probe.LAND, Probe.LAND));
        assertEquals(TerrainClass.RIVER, cell(SEA, Probe.OCEAN, Probe.OCEAN, Probe.RIVER, Probe.BEACH), "before the shore");
        assertEquals(TerrainClass.RIVER, cell(DRY, Probe.MOUNTAIN, Probe.MOUNTAIN, Probe.MOUNTAIN, Probe.RIVER), "before the mountains");
    }

    @Test
    void aLittleSeaOrABeachMakesTheShore() {
        assertEquals(TerrainClass.BEACH, cell(DRY, Probe.OCEAN, Probe.OCEAN, Probe.LAND, Probe.LAND));
        assertEquals(TerrainClass.BEACH, cell(DRY, Probe.BEACH, Probe.LAND, Probe.LAND, Probe.LAND));
        assertEquals(TerrainClass.BEACH, cell(50, Probe.OCEAN, Probe.LAND, Probe.LAND, Probe.LAND), "before a lake");
    }

    @Test
    void lowGroundAwayFromTheSeaIsALake() {
        assertEquals(TerrainClass.LAKE, cell(SEA, Probe.LAND, Probe.LAND, Probe.LAND, Probe.LAND));
        assertEquals(TerrainClass.LAKE, cell(50, Probe.SWAMP, Probe.SWAMP, Probe.SWAMP, Probe.SWAMP), "before a swamp");
        assertEquals(TerrainClass.LAKE, cell(50, Probe.MOUNTAIN, Probe.MOUNTAIN, Probe.LAND, Probe.LAND), "before the mountains");
        assertEquals(TerrainClass.LAND, cell(SEA + 1, Probe.LAND, Probe.LAND, Probe.LAND, Probe.LAND), "just above the sea is land");
    }

    @Test
    void twoPointsInTheMountainsMakeItSteep() {
        assertEquals(TerrainClass.STEEP, cell(120, Probe.MOUNTAIN, Probe.MOUNTAIN, Probe.LAND, Probe.LAND));
        assertEquals(TerrainClass.STEEP, cell(120, Probe.MOUNTAIN, Probe.MOUNTAIN, Probe.SWAMP, Probe.SWAMP), "before a swamp");
        assertEquals(TerrainClass.LAND, cell(120, Probe.MOUNTAIN, Probe.LAND, Probe.LAND, Probe.LAND), "one point is not enough");
    }

    @Test
    void twoPointsOfSwampMakeASwamp() {
        assertEquals(TerrainClass.SWAMP, cell(DRY, Probe.SWAMP, Probe.SWAMP, Probe.LAND, Probe.LAND));
        assertEquals(TerrainClass.SWAMP, cell(DRY, Probe.SWAMP, Probe.SWAMP, Probe.DESERT, Probe.DESERT), "before a desert");
        assertEquals(TerrainClass.LAND, cell(DRY, Probe.SWAMP, Probe.LAND, Probe.LAND, Probe.LAND));
    }

    @Test
    void twoPointsOfDesertMakeADesert() {
        assertEquals(TerrainClass.DESERT, cell(DRY, Probe.DESERT, Probe.DESERT, Probe.LAND, Probe.LAND));
        assertEquals(TerrainClass.DESERT, cell(DRY, Probe.DESERT, Probe.DESERT, Probe.HILL, Probe.HILL), "before hills");
        assertEquals(TerrainClass.LAND, cell(DRY, Probe.DESERT, Probe.LAND, Probe.LAND, Probe.LAND));
    }

    @Test
    void twoPointsOfHillsMakeHillsAndAMountainCountsAsAHill() {
        assertEquals(TerrainClass.HILLS, cell(90, Probe.HILL, Probe.HILL, Probe.LAND, Probe.LAND));
        assertEquals(TerrainClass.HILLS, cell(90, Probe.HILL, Probe.MOUNTAIN, Probe.LAND, Probe.LAND));
        assertEquals(TerrainClass.LAND, cell(90, Probe.HILL, Probe.LAND, Probe.LAND, Probe.LAND));
    }

    @Test
    void everythingElseIsOpenLand() {
        assertEquals(TerrainClass.LAND, cell(DRY, Probe.LAND, Probe.LAND, Probe.LAND, Probe.LAND));
        assertEquals(TerrainClass.LAND, cell(DRY, Probe.SWAMP, Probe.DESERT, Probe.HILL, Probe.LAND), "one of each is not enough");
        assertEquals(TerrainClass.LAND, cell(DRY, null, null, Probe.LAND, null), "a missing point is open land");
    }

    @Test
    void aChunkIsTheWaterOrTheLandAtItsMiddle() {
        assertEquals(TerrainClass.OCEAN, SurveyClassifier.chunk(Probe.OCEAN, DRY, SEA));
        assertEquals(TerrainClass.RIVER, SurveyClassifier.chunk(Probe.RIVER, 50, SEA));
        assertEquals(TerrainClass.BEACH, SurveyClassifier.chunk(Probe.BEACH, 50, SEA));
        assertEquals(TerrainClass.LAKE, SurveyClassifier.chunk(Probe.LAND, SEA, SEA));
        assertEquals(TerrainClass.LAKE, SurveyClassifier.chunk(Probe.SWAMP, 60, SEA), "a low swamp is a lake");
        assertEquals(TerrainClass.SWAMP, SurveyClassifier.chunk(Probe.SWAMP, DRY, SEA));
        assertEquals(TerrainClass.LAND, SurveyClassifier.chunk(Probe.MOUNTAIN, 150, SEA));
        assertEquals(TerrainClass.LAND, SurveyClassifier.chunk(Probe.HILL, DRY, SEA));
        assertEquals(TerrainClass.LAND, SurveyClassifier.chunk(Probe.DESERT, DRY, SEA));
        assertEquals(TerrainClass.LAND, SurveyClassifier.chunk(Probe.LAND, SEA + 1, SEA));
    }

    @Test
    void theHeightDecidesOnlyWhereNoPointSaysWater() {
        assertTrue(SurveyClassifier.heightDecides(new Probe[]{Probe.LAND, Probe.HILL, Probe.SWAMP, Probe.DESERT}));
        assertTrue(SurveyClassifier.heightDecides(new Probe[]{Probe.MOUNTAIN, Probe.LAND, Probe.LAND, Probe.LAND}));
        assertFalse(SurveyClassifier.heightDecides(new Probe[]{Probe.LAND, Probe.LAND, Probe.LAND, Probe.RIVER}));
        assertFalse(SurveyClassifier.heightDecides(new Probe[]{Probe.OCEAN, Probe.LAND, Probe.LAND, Probe.LAND}));
        assertFalse(SurveyClassifier.heightDecides(new Probe[]{Probe.BEACH, Probe.LAND, Probe.LAND, Probe.LAND}));
        // Where the height does not decide, the class is the same whatever the height.
        for (Probe[] probes : new Probe[][]{{Probe.RIVER, Probe.LAND, Probe.LAND, Probe.LAND}, {Probe.OCEAN, Probe.OCEAN, Probe.OCEAN, Probe.LAND},
                {Probe.BEACH, Probe.LAND, Probe.LAND, Probe.LAND}}) {
            assertEquals(SurveyClassifier.cell(probes, SEA - 20, SEA), SurveyClassifier.cell(probes, SEA + 20, SEA));
        }
    }

    @Test
    void groundFarFromItsNeighbourBecomesSteep() {
        // A row of three: land at 70, land at 100 and hills at 110. Only the step of 30 is too much.
        byte[] classes = {TerrainClass.LAND.id(), TerrainClass.LAND.id(), TerrainClass.HILLS.id()};
        short[] heights = {70, 100, 110};
        SurveyClassifier.steepen(classes, heights, 3, 1, SurveyClassifier.STEEP_STEP);
        assertEquals(TerrainClass.STEEP.id(), classes[0]);
        assertEquals(TerrainClass.STEEP.id(), classes[1]);
        assertEquals(TerrainClass.HILLS.id(), classes[2], "10 higher than its neighbour");
    }

    @Test
    void aStepOfExactlyTheThresholdIsNotSteep() {
        byte[] classes = {TerrainClass.LAND.id(), TerrainClass.DESERT.id()};
        short[] heights = {70, 70 + SurveyClassifier.STEEP_STEP};
        SurveyClassifier.steepen(classes, heights, 2, 1, SurveyClassifier.STEEP_STEP);
        assertEquals(TerrainClass.LAND.id(), classes[0]);
        assertEquals(TerrainClass.DESERT.id(), classes[1]);
        heights[1]++;
        SurveyClassifier.steepen(classes, heights, 2, 1, SurveyClassifier.STEEP_STEP);
        assertEquals(TerrainClass.STEEP.id(), classes[0]);
        assertEquals(TerrainClass.STEEP.id(), classes[1]);
    }

    @Test
    void onlyLandHillsDesertAndSwampBecomeSteep() {
        // A row of cells of every class at 80 over a row of open land at 0.
        TerrainClass[] kinds = TerrainClass.values();
        byte[] classes = new byte[kinds.length * 2];
        short[] heights = new short[kinds.length * 2];
        for (int i = 0; i < kinds.length; i++) {
            classes[i] = kinds[i].id();
            heights[i] = 80;
            classes[kinds.length + i] = TerrainClass.LAND.id();
            heights[kinds.length + i] = 0;
        }
        // The neighbours of a cell of the upper row along it are at 80 too: only the row below is far away.
        SurveyClassifier.steepen(classes, heights, kinds.length, 2, SurveyClassifier.STEEP_STEP);
        for (int i = 0; i < kinds.length; i++) {
            TerrainClass expected = switch (kinds[i]) {
                case LAND, HILLS, DESERT, SWAMP -> TerrainClass.STEEP;
                default -> kinds[i];
            };
            assertEquals(expected, TerrainClass.byId(classes[i]), kinds[i].name());
        }
    }

    @Test
    void aNeighbourNeverSurveyedDoesNotCount() {
        byte[] classes = {TerrainClass.LAND.id(), TerrainClass.UNKNOWN.id()};
        short[] heights = {70, 0};
        SurveyClassifier.steepen(classes, heights, 2, 1, SurveyClassifier.STEEP_STEP);
        assertEquals(TerrainClass.LAND.id(), classes[0]);
        assertEquals(TerrainClass.UNKNOWN.id(), classes[1]);
    }

    @Test
    void theFloorOfOpenWaterDoesNotMakeTheShoreSteepButARiverCounts() {
        byte[] classes = {TerrainClass.OCEAN.id(), TerrainClass.LAND.id(), TerrainClass.LAKE.id(), TerrainClass.LAND.id(),
                TerrainClass.RIVER.id(), TerrainClass.LAND.id()};
        short[] heights = {30, 70, 20, 66, 30, 66};
        SurveyClassifier.steepen(classes, heights, 6, 1, SurveyClassifier.STEEP_STEP);
        assertEquals(TerrainClass.OCEAN.id(), classes[0]);
        assertEquals(TerrainClass.LAND.id(), classes[1], "40 above the floor of the sea, at the shore");
        assertEquals(TerrainClass.LAKE.id(), classes[2]);
        assertEquals(TerrainClass.STEEP.id(), classes[3], "36 above the bed of a river beside it");
        assertEquals(TerrainClass.RIVER.id(), classes[4]);
        assertEquals(TerrainClass.STEEP.id(), classes[5]);
    }

    @Test
    void theNeighboursAboveAndBelowCountToo() {
        // A column: land at 70 over land at 70 over land at 120.
        byte[] classes = {TerrainClass.LAND.id(), TerrainClass.LAND.id(), TerrainClass.LAND.id()};
        short[] heights = {70, 70, 120};
        SurveyClassifier.steepen(classes, heights, 1, 3, SurveyClassifier.STEEP_STEP);
        assertEquals(TerrainClass.LAND.id(), classes[0]);
        assertEquals(TerrainClass.STEEP.id(), classes[1]);
        assertEquals(TerrainClass.STEEP.id(), classes[2]);
    }

    @Test
    void aCellMadeSteepDoesNotSpreadInTheSamePass() {
        // Land at 70, 100, 110, 120: the first step of 30 makes the first two steep, the rest stays as it was.
        byte[] classes = {TerrainClass.LAND.id(), TerrainClass.LAND.id(), TerrainClass.LAND.id(), TerrainClass.LAND.id()};
        short[] heights = {70, 100, 110, 120};
        SurveyClassifier.steepen(classes, heights, 4, 1, SurveyClassifier.STEEP_STEP);
        assertEquals(TerrainClass.STEEP.id(), classes[0]);
        assertEquals(TerrainClass.STEEP.id(), classes[1]);
        assertEquals(TerrainClass.LAND.id(), classes[2]);
        assertEquals(TerrainClass.LAND.id(), classes[3]);
    }

    @Test
    void theArraysMustFitTheGrid() {
        assertThrows(IllegalArgumentException.class, () -> SurveyClassifier.steepen(new byte[3], new short[4], 2, 2, 24));
        assertThrows(IllegalArgumentException.class, () -> SurveyClassifier.steepen(new byte[4], new short[3], 2, 2, 24));
    }
}
