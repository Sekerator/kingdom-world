package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCellsTest {
    private static List<RouteCell> way() {
        List<RouteCell> cells = new ArrayList<>();
        int x = 1000;
        int y = 70;
        int z = -2000;
        for (int i = 0; i < 1000; i++) {
            x += i % 3 == 0 ? 1 : 0;
            z += i % 5 == 0 ? -1 : 1;
            y += (i % 40 == 0) ? -3 : (i % 41 == 0 ? 2 : 0);
            RouteCell.Kind kind = i >= 300 && i < 320 ? RouteCell.Kind.BRIDGE : i >= 500 && i < 504 ? RouteCell.Kind.FILL : RouteCell.Kind.GROUND;
            cells.add(new RouteCell(x, y, z, kind, kind == RouteCell.Kind.FILL ? 2 : 0, null));
        }
        // A jump left by the joining of two stretches, and a deep step down a cliff.
        cells.add(new RouteCell(x + 3, y, z + 2, RouteCell.Kind.GROUND, 0, null));
        cells.add(new RouteCell(x + 4, y - 20, z + 2, RouteCell.Kind.GROUND, 0, null));
        return cells;
    }

    @Test
    void aWayComesBackBlockForBlock() {
        List<RouteCell> cells = way();
        RoadCells.Packed packed = RoadCells.pack(cells);
        assertEquals(cells, RoadCells.unpack(packed));
        assertEquals(cells.size() - 1, packed.steps().length);
        assertEquals(6, packed.jumps().length, "two blocks a step does not reach");
        // A thousand blocks in about a thousand bytes and a few runs, against sixteen thousand of the first format.
        int bytes = packed.steps().length + 4 * (packed.jumps().length + packed.kinds().length + packed.start().length);
        assertTrue(bytes < 1_200, "bytes " + bytes);
    }

    @Test
    void theRunsOfKindsAreShort() {
        RoadCells.Packed packed = RoadCells.pack(way());
        assertArrayEquals(new int[]{0, 300, 256, 20, 0, 180, 2 * 256 + 2, 4, 0, 498}, packed.kinds());
    }

    @Test
    void anEmptyWayAndADamagedOneAreToldApart() {
        assertEquals(List.of(), RoadCells.unpack(RoadCells.pack(List.of())));
        RoadCells.Packed packed = RoadCells.pack(way());
        assertThrows(IllegalArgumentException.class, () -> RoadCells.unpack(new RoadCells.Packed(packed.start(), packed.steps(), new int[0],
                packed.kinds())));
        assertThrows(IllegalArgumentException.class, () -> RoadCells.unpack(new RoadCells.Packed(packed.start(), packed.steps(), packed.jumps(),
                new int[]{0, 5})));
    }

    @Test
    void cuttingsAndTunnelsKeepTheirKindAndDepth() {
        List<RouteCell> cells = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            RouteCell.Kind kind = i < 5 ? RouteCell.Kind.CUT : i < 15 ? RouteCell.Kind.TUNNEL : RouteCell.Kind.BRIDGE;
            cells.add(new RouteCell(i, 64, 0, kind, i < 5 ? 7 : i < 15 ? 30 : 12, null));
        }
        cells.add(new RouteCell(20, 64, 0, RouteCell.Kind.CUT, 400, null));
        List<RouteCell> back = RoadCells.unpack(RoadCells.pack(cells));
        assertEquals(cells.subList(0, 20), back.subList(0, 20));
        assertEquals(RouteCell.Kind.TUNNEL, back.get(10).kind());
        assertEquals(12, back.get(17).depth(), "the height of the pier of a bridge is kept");
        assertEquals(255, back.get(20).depth(), "a depth only shown is kept up to a byte");
        assertTrue(back.get(3).dug() && !back.get(3).raised());
        assertTrue(back.get(17).raised() && !back.get(17).dug());
        assertEquals(0, new RouteCell(0, 0, 0, RouteCell.Kind.GROUND, 5, null).depth());
    }

    @Test
    void surveysComeBackInRunsWithCountsBeyondAByte() {
        List<RoadLayout.ColumnSurvey> surveys = new ArrayList<>();
        RoadLayout.ColumnSurvey deep = new RoadLayout.ColumnSurvey(15, 3_000, 400, 0, 0, 1, false, false);
        RoadLayout.ColumnSurvey pier = new RoadLayout.ColumnSurvey(0, 0, 0, 0, 300, 0, false, false);
        RoadLayout.ColumnSurvey lit = new RoadLayout.ColumnSurvey(0, 9, 0, 0, 0, 2, true, false);
        RoadLayout.ColumnSurvey road = new RoadLayout.ColumnSurvey(0, 0, 0, 0, 0, 0, false, true);
        RoadLayout.ColumnSurvey fill = new RoadLayout.ColumnSurvey(0, 0, 0, 16, 0, 0, false, false);
        for (int i = 0; i < 100; i++) {
            surveys.add(RoadLayout.ColumnSurvey.EMPTY);
        }
        surveys.add(deep);
        surveys.add(deep);
        surveys.add(pier);
        surveys.add(lit);
        surveys.add(road);
        surveys.add(fill);
        int[] runs = RoadCells.packSurveys(surveys);
        assertEquals(6 * 4, runs.length, "a hundred empty columns in one run");
        assertEquals(surveys, RoadCells.unpackSurveys(runs));
        assertEquals(List.of(), RoadCells.unpackSurveys(RoadCells.packSurveys(List.of())));
        assertThrows(IllegalArgumentException.class, () -> RoadCells.unpackSurveys(new int[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> RoadCells.unpackSurveys(new int[]{1, 2, 3, 0}));
    }
}
