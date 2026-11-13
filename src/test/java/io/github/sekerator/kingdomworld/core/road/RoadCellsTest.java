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
}
