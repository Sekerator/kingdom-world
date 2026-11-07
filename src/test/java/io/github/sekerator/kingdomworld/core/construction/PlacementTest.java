package io.github.sekerator.kingdomworld.core.construction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Turning and mirroring a template: positions and directions turn together, the way the game turns its own structures
 * (mirror front to back first, then clockwise quarter turns), and the box of a template follows.
 */
class PlacementTest {

    @Test
    void quarterTurnsMoveAPositionClockwise() {
        // A block one step east and two steps south of the anchor.
        assertArrayEquals(new int[]{1, 0, 2}, new Placement(0, false).apply(1, 0, 2));
        assertArrayEquals(new int[]{-2, 0, 1}, new Placement(1, false).apply(1, 0, 2));
        assertArrayEquals(new int[]{-1, 0, -2}, new Placement(2, false).apply(1, 0, 2));
        assertArrayEquals(new int[]{2, 0, -1}, new Placement(3, false).apply(1, 0, 2));
        assertArrayEquals(new int[]{2, 5, -1}, new Placement(-1, false).apply(1, 5, 2), "-1 is three quarters");
    }

    @Test
    void mirrorFlipsXBeforeTurning() {
        assertArrayEquals(new int[]{-1, 0, 2}, new Placement(0, true).apply(1, 0, 2));
        assertArrayEquals(new int[]{-2, 0, -1}, new Placement(1, true).apply(1, 0, 2));
    }

    @Test
    void invertUndoesEveryPlacement() {
        for (int rotation = 0; rotation < 4; rotation++) {
            for (boolean mirror : new boolean[]{false, true}) {
                Placement placement = new Placement(rotation, mirror);
                for (int[] p : new int[][]{{1, 2, 3}, {-4, 0, 5}, {0, 0, 0}, {7, -1, -9}}) {
                    int[] placed = placement.apply(p[0], p[1], p[2]);
                    assertArrayEquals(p, placement.invert(placed[0], placed[1], placed[2]), placement + " " + p[0] + "," + p[2]);
                }
            }
        }
    }

    @Test
    void directionsTurnLikePositions() {
        for (int rotation = 0; rotation < 4; rotation++) {
            for (boolean mirror : new boolean[]{false, true}) {
                Placement placement = new Placement(rotation, mirror);
                for (Facing facing : Facing.values()) {
                    int[] step = placement.apply(facing.dx(), 0, facing.dz());
                    assertEquals(Facing.of(step[0], step[2]).orElseThrow(), placement.apply(facing), placement + " " + facing);
                }
            }
        }
        assertEquals(Facing.EAST, new Placement(1, false).apply(Facing.NORTH));
        assertEquals(Facing.WEST, new Placement(0, true).apply(Facing.EAST));
        assertEquals(Facing.NORTH, new Placement(0, true).apply(Facing.NORTH), "the mirror leaves north and south");
    }

    @Test
    void theBoxFollowsTheTurn() {
        // A template 5 wide, 3 high and 7 deep, anchored in the middle of its front.
        Box straight = Placement.NONE.box(5, 3, 7, 2, 0, 6, 100, 64, 100);
        assertEquals(new Box(98, 64, 94, 102, 66, 100), straight);
        Box turned = new Placement(1, false).box(5, 3, 7, 2, 0, 6, 100, 64, 100);
        assertEquals(7, turned.sizeX());
        assertEquals(5, turned.sizeZ());
        assertEquals(3, turned.sizeY());
        assertEquals(new Box(100, 64, 98, 106, 66, 102), turned);
        for (int rotation = 0; rotation < 4; rotation++) {
            Box box = new Placement(rotation, true).box(5, 3, 7, 2, 0, 6, 0, 0, 0);
            assertEquals(35 * 3, box.volume());
            assertEquals(true, box.contains(0, 0, 0), "the anchor lies in the box");
        }
    }

    @Test
    void boxesMeetAndCoverChunks() {
        Box a = new Box(0, 0, 0, 9, 5, 9);
        assertEquals(true, a.intersects(new Box(9, 5, 9, 12, 8, 12)));
        assertEquals(false, a.intersects(new Box(10, 0, 0, 12, 5, 9)));
        assertEquals(1, a.chunks().size());
        assertEquals(4, new Box(-1, 0, -1, 1, 0, 1).chunks().size());
        assertEquals(0, a.index(0, 0, 0));
        assertEquals(a.volume() - 1, a.index(9, 5, 9));
    }

    @Test
    void turningFourTimesComesBack() {
        Placement placement = new Placement(0, true);
        assertEquals(placement, placement.turned().turned().turned().turned());
        assertEquals(new Placement(0, false), placement.flipped());
    }
}
