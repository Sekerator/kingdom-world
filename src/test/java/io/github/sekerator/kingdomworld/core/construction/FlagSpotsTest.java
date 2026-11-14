package io.github.sekerator.kingdomworld.core.construction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where buildings get the flags their templates do not have: beside the door, on the roof, at the front corners.
 */
class FlagSpotsTest {

    /**
     * A grid of cells, air unless set.
     */
    private static final class TestGrid implements FlagSpots.Grid {
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final FlagSpots.Cell[] cells;

        TestGrid(int sizeX, int sizeY, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            cells = new FlagSpots.Cell[sizeX * sizeY * sizeZ];
            java.util.Arrays.fill(cells, FlagSpots.Cell.AIR);
        }

        TestGrid fill(int x0, int y0, int z0, int x1, int y1, int z1, FlagSpots.Cell cell) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, cell);
                    }
                }
            }
            return this;
        }

        TestGrid set(int x, int y, int z, FlagSpots.Cell cell) {
            cells[(y * sizeZ + z) * sizeX + x] = cell;
            return this;
        }

        @Override
        public int sizeX() {
            return sizeX;
        }

        @Override
        public int sizeY() {
            return sizeY;
        }

        @Override
        public int sizeZ() {
            return sizeZ;
        }

        @Override
        public FlagSpots.Cell at(int x, int y, int z) {
            return cells[(y * sizeZ + z) * sizeX + x];
        }
    }

    /**
     * A hall of 13 by 13 (a footprint of 169, two flags): a floor, walls three high and a flat roof, with a margin of air
     * around, the door in the middle of the south wall.
     */
    private static TestGrid hall() {
        TestGrid grid = new TestGrid(13, 6, 13);
        grid.fill(1, 0, 1, 11, 0, 11, FlagSpots.Cell.SOLID);
        grid.fill(1, 1, 1, 11, 3, 1, FlagSpots.Cell.SOLID).fill(1, 1, 11, 11, 3, 11, FlagSpots.Cell.SOLID);
        grid.fill(1, 1, 1, 1, 3, 11, FlagSpots.Cell.SOLID).fill(11, 1, 1, 11, 3, 11, FlagSpots.Cell.SOLID);
        grid.fill(1, 4, 1, 11, 4, 11, FlagSpots.Cell.SOLID);
        grid.set(6, 1, 11, FlagSpots.Cell.DOOR).set(6, 2, 11, FlagSpots.Cell.OTHER);
        return grid;
    }

    private static FlagSpots.Spot wall(int x, int y, int z) {
        return new FlagSpots.Spot(x, y, z, FlagSpots.Kind.WALL, Facing.SOUTH, 0);
    }

    @Test
    void howManyFlags() {
        assertEquals(1, FlagSpots.wanted(30, 30, true), "a house flies one");
        assertEquals(1, FlagSpots.wanted(10, 12, false));
        assertEquals(2, FlagSpots.wanted(11, 11, false));
        assertEquals(2, FlagSpots.wanted(15, 20, false));
        assertEquals(3, FlagSpots.wanted(16, 20, false));
        assertEquals(0, FlagSpots.rotation(Facing.SOUTH));
        assertEquals(4, FlagSpots.rotation(Facing.WEST));
        assertEquals(8, FlagSpots.rotation(Facing.NORTH));
        assertEquals(12, FlagSpots.rotation(Facing.EAST));
    }

    @Test
    void aPairBesideTheDoorAtTheTopOfIt() {
        List<FlagSpots.Spot> spots = FlagSpots.find(hall(), Facing.SOUTH, false, false);
        assertEquals(List.of(wall(7, 3, 12), wall(5, 3, 12)), spots);
    }

    @Test
    void aHouseFliesOneAndFlagsOfTheTemplateCount() {
        assertEquals(List.of(wall(7, 3, 12)), FlagSpots.find(hall(), Facing.SOUTH, true, false));
        TestGrid flagged = hall().set(2, 5, 2, FlagSpots.Cell.FLAG);
        assertEquals(List.of(wall(7, 3, 12)), FlagSpots.find(flagged, Facing.SOUTH, false, false), "one of the two is the template's");
        TestGrid both = hall().set(2, 5, 2, FlagSpots.Cell.FLAG).set(10, 5, 10, FlagSpots.Cell.FLAG);
        assertEquals(List.of(), FlagSpots.find(both, Facing.SOUTH, false, false));
    }

    @Test
    void aDoubleDoorIsPassedOver() {
        TestGrid grid = hall().set(7, 1, 11, FlagSpots.Cell.DOOR).set(7, 2, 11, FlagSpots.Cell.OTHER);
        List<FlagSpots.Spot> spots = FlagSpots.find(grid, Facing.SOUTH, false, false);
        assertEquals(List.of(wall(8, 3, 12), wall(5, 3, 12)), spots);
    }

    @Test
    void aWindowNeverHoldsAFlag() {
        TestGrid grid = hall().fill(5, 2, 11, 5, 3, 11, FlagSpots.Cell.WINDOW).fill(7, 2, 11, 7, 3, 11, FlagSpots.Cell.WINDOW);
        List<FlagSpots.Spot> spots = FlagSpots.find(grid, Facing.SOUTH, false, false);
        assertEquals(2, spots.size());
        for (FlagSpots.Spot spot : spots) {
            if (spot.kind() == FlagSpots.Kind.WALL) {
                assertEquals(FlagSpots.Cell.SOLID, grid.at(spot.x() - spot.facing().dx(), spot.y(), spot.z() - spot.facing().dz()));
            }
            assertNotEquals(5, spot.x());
            assertNotEquals(7, spot.x());
        }
    }

    @Test
    void flagsStandInAirOfTheTemplateAndNeverBeforeTheDoor() {
        for (boolean housing : new boolean[]{false, true}) {
            for (boolean defense : new boolean[]{false, true}) {
                TestGrid grid = hall();
                for (FlagSpots.Spot spot : FlagSpots.find(grid, Facing.SOUTH, housing, defense)) {
                    assertEquals(FlagSpots.Cell.AIR, grid.at(spot.x(), spot.y(), spot.z()));
                    boolean beforeDoor = spot.x() == 6 && spot.z() == 12 && spot.y() <= 2;
                    assertTrue(!beforeDoor, "before the door: " + spot);
                }
            }
        }
    }

    @Test
    void aFlagOnTheRoofStandsHighestNearTheMiddle() {
        TestGrid grid = new TestGrid(9, 9, 9);
        grid.fill(1, 0, 1, 7, 4, 7, FlagSpots.Cell.SOLID).fill(2, 1, 2, 6, 3, 6, FlagSpots.Cell.AIR);
        grid.fill(4, 5, 4, 4, 6, 4, FlagSpots.Cell.SOLID);
        assertEquals(List.of(new FlagSpots.Spot(4, 7, 4, FlagSpots.Kind.STANDING, Facing.SOUTH, 0)),
                FlagSpots.find(grid, Facing.SOUTH, false, false));
    }

    @Test
    void towersGetFlagsApart() {
        TestGrid grid = new TestGrid(21, 10, 9);
        grid.fill(1, 0, 1, 19, 4, 7, FlagSpots.Cell.SOLID);
        // Two hollow towers: their walls are solid down to the ground, their roofs cover a room.
        grid.fill(2, 5, 3, 4, 8, 5, FlagSpots.Cell.SOLID).fill(3, 5, 4, 3, 7, 4, FlagSpots.Cell.AIR);
        grid.fill(16, 5, 3, 18, 8, 5, FlagSpots.Cell.SOLID).fill(17, 5, 4, 17, 7, 4, FlagSpots.Cell.AIR);
        List<FlagSpots.Spot> spots = FlagSpots.find(grid, Facing.NORTH, false, false);
        assertEquals(List.of(new FlagSpots.Spot(3, 9, 4, FlagSpots.Kind.STANDING, Facing.NORTH, 8),
                new FlagSpots.Spot(17, 9, 4, FlagSpots.Kind.STANDING, Facing.NORTH, 8)), spots);
    }

    @Test
    void aDefenseFliesOneMoreOnItsRoof() {
        List<FlagSpots.Spot> spots = FlagSpots.find(hall(), Facing.SOUTH, false, true);
        assertEquals(3, spots.size());
        assertEquals(new FlagSpots.Spot(6, 5, 6, FlagSpots.Kind.STANDING, Facing.SOUTH, 0), spots.get(2));
        TestGrid high = hall().set(2, 5, 2, FlagSpots.Cell.FLAG);
        assertEquals(List.of(wall(7, 3, 12)), FlagSpots.find(high, Facing.SOUTH, false, true), "a flag of the template stands high already");
    }

    @Test
    void aGardenGetsAFlagAtAFrontCornerOffTheWayIn() {
        TestGrid garden = new TestGrid(9, 3, 9);
        garden.fill(0, 0, 0, 8, 0, 8, FlagSpots.Cell.SOLID).fill(4, 0, 0, 4, 0, 8, FlagSpots.Cell.PATH);
        garden.fill(0, 1, 0, 8, 2, 8, FlagSpots.Cell.AIR);
        garden.fill(0, 1, 0, 0, 2, 8, FlagSpots.Cell.OTHER);
        // The way in at the left front corner: a gate with nothing to hang a flag on beside it.
        garden.set(1, 1, 8, FlagSpots.Cell.OTHER).set(2, 1, 7, FlagSpots.Cell.DOOR);
        List<FlagSpots.Spot> spots = FlagSpots.find(garden, Facing.SOUTH, false, false);
        assertEquals(List.of(new FlagSpots.Spot(7, 1, 7, FlagSpots.Kind.STANDING, Facing.SOUTH, 0)), spots);
        TestGrid open = new TestGrid(9, 3, 9);
        open.fill(0, 0, 0, 8, 0, 8, FlagSpots.Cell.SOLID).fill(4, 0, 0, 4, 0, 8, FlagSpots.Cell.PATH);
        List<FlagSpots.Spot> roofless = FlagSpots.find(open, Facing.SOUTH, false, false);
        assertEquals(1, roofless.size());
        assertNotEquals(4, roofless.get(0).x(), "never on the way in");
    }

    @Test
    void theSameTemplateGivesTheSamePlaces() {
        assertEquals(FlagSpots.find(hall(), Facing.SOUTH, false, true), FlagSpots.find(hall(), Facing.SOUTH, false, true));
        assertEquals(FlagSpots.find(hall(), Facing.EAST, false, false), FlagSpots.find(hall(), Facing.EAST, false, false));
    }
}
