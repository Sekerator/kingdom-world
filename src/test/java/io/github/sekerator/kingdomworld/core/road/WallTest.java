package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallTest {
    private static final UUID US = new UUID(1, 1);
    private static final UUID THEM = new UUID(2, 2);
    private static final String WORLD = "minecraft:overworld";

    /**
     * Hills: the ground rises to the east and falls to the south.
     */
    private static int height(int x, int z) {
        return 64 + x / 3 - z / 4;
    }

    private static Wall wall(Wall.WallClass wallClass, List<RouteCell> ring, List<Integer> gates) {
        return new Wall(1, 0, US, wallClass, WORLD, 0, 0, ring, gates, 0);
    }

    @Test
    void theRingFollowsTheLayOfTheLand() {
        List<RouteCell> ring = WallContour.ring((x, z) -> Terrain.Column.land(height(x, z)), 0, 0, 12);
        assertNotNull(ring);
        assertEquals(8 * 12, ring.size(), "a square ring of radius 12 has 96 blocks");
        Set<String> seen = new java.util.HashSet<>();
        for (RouteCell cell : ring) {
            assertEquals(height(cell.x(), cell.z()), cell.y(), "each block stands on the ground at " + cell.x() + " " + cell.z());
            assertEquals(12, Math.max(Math.abs(cell.x()), Math.abs(cell.z())), "every block is on the edge of the square");
            assertTrue(seen.add(cell.x() + " " + cell.z()), "no block twice");
        }
        // The ring goes round: each block is next to the one before it.
        for (int i = 0; i < ring.size(); i++) {
            RouteCell a = ring.get(i);
            RouteCell b = ring.get((i + 1) % ring.size());
            assertEquals(1, Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()));
        }
    }

    @Test
    void waterAndDangerLeaveGapsAndUnloadedLandWaits() {
        List<RouteCell> ring = WallContour.ring((x, z) -> x >= 2 && x <= 5 ? Terrain.Column.water(62)
                : x == -12 && z == 3 ? new Terrain.Column(64, Terrain.Surface.DANGER, null) : Terrain.Column.land(64), 0, 0, 12);
        assertNotNull(ring);
        // Four blocks of water on the north side and four on the south, one of danger on the west.
        assertEquals(8 * 12 - 9, ring.size());
        assertTrue(ring.stream().noneMatch(cell -> cell.x() >= 2 && cell.x() <= 5));
        assertNull(WallContour.ring((x, z) -> x > 10 ? null : Terrain.Column.land(64), 0, 0, 12), "a ring over land not loaded yet waits");
    }

    @Test
    void roadsThatCrossTheRingGetGates() {
        List<RouteCell> ring = WallContour.ring((x, z) -> Terrain.Column.land(64), 0, 0, 12);
        assertNotNull(ring);
        // A road from west to east through the middle, a little crooked, and one that stays outside.
        List<RouteCell> through = new ArrayList<>();
        for (int x = -30; x <= 30; x++) {
            through.add(new RouteCell(x, 64, x > 0 ? 1 : 0, RouteCell.Kind.GROUND, 0, null));
        }
        List<RouteCell> outside = new ArrayList<>();
        for (int z = -30; z <= 30; z++) {
            outside.add(new RouteCell(40, 64, z, RouteCell.Kind.GROUND, 0, null));
        }
        List<Integer> gates = WallContour.gates(ring, List.of(through, outside));
        assertEquals(2, gates.size(), "a gate on each side the road crosses");
        Wall wall = wall(Wall.WallClass.PALISADE, ring, gates);
        List<RouteCell> cells = wall.gateCells();
        assertTrue(cells.stream().anyMatch(cell -> cell.x() == 12 && Math.abs(cell.z() - 1) <= 1), "a gate in the east side at the road");
        assertTrue(cells.stream().anyMatch(cell -> cell.x() == -12 && Math.abs(cell.z()) <= 1), "a gate in the west side at the road");
        // No column of the wall stands on the road.
        for (Wall.Block block : wall.blocks()) {
            for (RouteCell cell : through) {
                assertFalse(block.x() == cell.x() && block.z() == cell.z(), "a column on the road at " + cell.x() + " " + cell.z());
            }
        }
        assertTrue(WallContour.gates(ring, List.of(outside)).isEmpty());
    }

    @Test
    void classesHaveTheirHeightAndStoneHasBattlements() {
        List<RouteCell> ring = WallContour.ring((x, z) -> Terrain.Column.land(64), 0, 0, 8);
        assertNotNull(ring);
        Wall palisade = wall(Wall.WallClass.PALISADE, ring, List.of());
        assertTrue(palisade.blocks().stream().allMatch(block -> block.height() == 4));
        assertEquals(ring.size() * 4, palisade.items());
        Wall stone = wall(Wall.WallClass.STONE, ring, List.of());
        assertEquals(Set.of(5, 6), new java.util.HashSet<>(stone.blocks().stream().map(Wall.Block::height).toList()));
        assertTrue(stone.items() > palisade.items());
        assertTrue(stone.inside(0, 0));
        assertTrue(stone.inside(7, -7));
        assertFalse(stone.inside(8, 0));
        assertEquals(8, stone.radius());
    }

    @Test
    void aGateCutLaterKeepsWhatWasPaid() {
        List<RouteCell> ring = WallContour.ring((x, z) -> Terrain.Column.land(64), 0, 0, 8);
        assertNotNull(ring);
        Wall wall = wall(Wall.WallClass.PALISADE, ring, List.of());
        int before = wall.blocks().size();
        Road.Section first = wall.sections().get(0);
        for (int i = 0; i < 5; i++) {
            first.markPaid(Map.of("minecraft:oak_log", 4));
        }
        assertFalse(wall.allPaid());
        int cell = wall.nearestCell(-8, 0);
        assertTrue(wall.addGate(cell));
        assertFalse(wall.addGate(cell + 1), "a gate next to a gate is the same gate");
        assertEquals(before - (2 * Wall.GATE_HALF + 1), wall.blocks().size());
        assertTrue(wall.inGate(cell));
        assertEquals(5, wall.sections().get(0).paid(), "what was paid stays paid");
        assertEquals(20, wall.sections().get(0).pool().get("minecraft:oak_log"));
        for (Road.Section section : wall.sections()) {
            while (!section.done()) {
                section.markPaid(Map.of("minecraft:oak_log", 4));
            }
            while (!section.placedAll()) {
                section.markPlaced();
            }
        }
        assertTrue(wall.allPaid());
        assertTrue(wall.built());
    }

    @Test
    void aGateCutAmongTheColumnsBuiltLeavesNoHoleInTheWall() {
        List<RouteCell> ring = WallContour.ring((x, z) -> Terrain.Column.land(64), 0, 0, 8);
        assertNotNull(ring);
        Wall wall = wall(Wall.WallClass.PALISADE, ring, List.of());
        // The columns standing in the world, by their block of the ring.
        Set<Integer> standing = new java.util.HashSet<>();
        Road.Section first = wall.sections().get(0);
        for (int i = 0; i < 10; i++) {
            first.markPaid(Map.of("minecraft:oak_log", 4));
        }
        for (int i = 0; i < 8; i++) {
            standing.add(wall.blocksOf(first).get(first.placed()).cell());
            first.markPlaced();
        }
        // A gate among the columns that stand, in the first section: its three columns are taken away.
        assertTrue(wall.addGate(3));
        standing.removeIf(wall::inGate);
        Road.Section cut = wall.sections().get(0);
        assertEquals(5, cut.placed(), "the five columns that stand outside the gate are placed");
        assertEquals(7, cut.paid(), "the columns of the gate are no longer paid for");
        assertEquals(40, cut.pool().get("minecraft:oak_log"), "what was paid for them stays in the pool");
        for (Road.Section section : wall.sections()) {
            while (!section.placedAll()) {
                if (section.paid() == section.placed()) {
                    section.markPaid(Map.of("minecraft:oak_log", 4));
                }
                standing.add(wall.blocksOf(section).get(section.placed()).cell());
                section.markPlaced();
            }
        }
        assertTrue(wall.built());
        for (Wall.Block block : wall.blocks()) {
            assertTrue(standing.contains(block.cell()), "the column at block " + block.cell() + " of the ring was never built");
        }
        assertEquals(wall.blocks().size(), standing.size(), "no column stands in the gate");
    }

    @Test
    void theStateKeepsOneWallPerCity() {
        RoadState state = new RoadState();
        List<RouteCell> ring = WallContour.ring((x, z) -> Terrain.Column.land(64), 0, 0, 8);
        assertNotNull(ring);
        Wall capital = new Wall(state.nextWallId(), 0, US, Wall.WallClass.STONE, WORLD, 0, 0, ring, List.of(), 0);
        state.putWall(capital);
        Wall theirs = new Wall(state.nextWallId(), 0, THEM, Wall.WallClass.PALISADE, WORLD, 0, 0, ring, List.of(), 0);
        state.putWall(theirs);
        assertEquals(capital.id(), state.wallOf(0, US).orElseThrow().id());
        assertEquals(theirs.id(), state.wallOf(0, THEM).orElseThrow().id());
        assertTrue(state.wallOf(5, US).isEmpty());
        state.forgetKingdom(THEM);
        assertTrue(state.wallOf(0, THEM).isEmpty(), "the walls of a kingdom that is gone are forgotten");
        assertTrue(state.removeWall(capital.id()).isPresent());
        assertTrue(state.walls().isEmpty());
    }

    @Test
    void theLevelOfACityFollowsItsPeopleAndBuildings() {
        RoadRules rules = new RoadRules.Builder().build();
        assertEquals(CityLevel.VILLAGE, CityLevel.of(0, 0, rules));
        assertEquals(CityLevel.VILLAGE, CityLevel.of(rules.townPeople(), rules.townBuildings() - 1, rules), "both thresholds count");
        assertEquals(CityLevel.TOWN, CityLevel.of(rules.townPeople(), rules.townBuildings(), rules));
        assertEquals(CityLevel.TOWN, CityLevel.of(rules.cityPeople() * 10, rules.cityBuildings() - 1, rules));
        assertEquals(CityLevel.CITY, CityLevel.of(rules.cityPeople(), rules.cityBuildings(), rules));
        assertTrue(CityLevel.VILLAGE.wallRadius(rules) < CityLevel.TOWN.wallRadius(rules));
        assertTrue(CityLevel.TOWN.wallRadius(rules) < CityLevel.CITY.wallRadius(rules));
        RoadRules.Builder small = new RoadRules.Builder();
        small.townPeople = 2;
        small.townBuildings = 1;
        small.cityPeople = 3;
        small.cityBuildings = 2;
        assertEquals(CityLevel.CITY, CityLevel.of(3, 2, small.build()), "the thresholds come from the config");
    }

    @Test
    void theGateGoesIntoTheWallThePlayerStandsBy() {
        // The capital from x -12 to 12, a city from 20 to 40.
        List<RouteCell> flat = WallContour.ring((x, z) -> Terrain.Column.land(64), 0, 0, 12);
        List<RouteCell> town = WallContour.ring((x, z) -> Terrain.Column.land(64), 30, 0, 10);
        Wall capital = new Wall(1, 0, US, Wall.WallClass.PALISADE, WORLD, 0, 0, flat, List.of(), 0);
        Wall city = new Wall(2, 7, US, Wall.WallClass.PALISADE, WORLD, 30, 0, town, List.of(), 0);
        Wall below = new Wall(3, 8, US, Wall.WallClass.PALISADE, "minecraft:the_nether", 30, 0, town, List.of(), 0);
        Wall foreign = new Wall(4, 9, THEM, Wall.WallClass.PALISADE, WORLD, 30, 0, town, List.of(), 0);
        List<Wall> walls = List.of(capital, city, below, foreign);

        // The chosen wall while the player stands within reach of it, though another wall is nearer.
        assertEquals(5, capital.reach(17, 0));
        assertEquals(3, city.reach(17, 0));
        assertEquals(capital, Wall.forGate(walls, US, 0, WORLD, 17, 0, 6).orElseThrow());
        assertEquals(city, Wall.forGate(walls, US, 7, WORLD, 14, 0, 6).orElseThrow(), "6 blocks off the city, 2 off the capital");
        // The chosen wall is out of reach: the nearest one.
        assertEquals(city, Wall.forGate(walls, US, 0, WORLD, 19, 0, 6).orElseThrow(), "7 blocks off the capital");
        assertEquals(capital, Wall.forGate(walls, US, 7, WORLD, 5, 0, 6).orElseThrow());
        // Far from every wall: the nearest one, however far, so that the answer can say how far.
        Wall far = Wall.forGate(walls, US, 0, WORLD, 300, 0, 6).orElseThrow();
        assertEquals(city, far);
        assertEquals(260, far.reach(300, 0));
        // Only walls of the kingdom, in the dimension of the player.
        assertEquals(below, Wall.forGate(walls, US, 0, "minecraft:the_nether", 17, 0, 6).orElseThrow());
        assertTrue(Wall.forGate(walls, US, 0, "minecraft:the_end", 17, 0, 6).isEmpty());
        assertEquals(foreign, Wall.forGate(walls, THEM, 0, WORLD, 0, 0, 6).orElseThrow());
    }

    private static List<RouteCell> flatRing(int radius, int y) {
        return WallContour.ring((x, z) -> Terrain.Column.land(y), 0, 0, radius);
    }

    @Test
    void aWallKnowsTheGroundOfItsColumns() {
        Wall wall = wall(Wall.WallClass.PALISADE, flatRing(8, 64), List.of(0));
        RouteCell first = wall.cells().get(5);
        assertEquals(5, wall.cellAt(first.x(), first.z()));
        assertEquals(64, wall.groundAt(first.x(), first.z()).orElseThrow());
        assertEquals(-1, wall.cellAt(0, 0), "the middle is off the ring");
        assertTrue(wall.groundAt(0, 0).isEmpty());
        // A column of a palisade: from one over its foot to four over it, never in a gate.
        assertFalse(wall.inColumn(Wall.pack(first.x(), 64, first.z())), "the foot is the ground");
        assertTrue(wall.inColumn(Wall.pack(first.x(), 65, first.z())));
        assertTrue(wall.inColumn(Wall.pack(first.x(), 68, first.z())));
        assertFalse(wall.inColumn(Wall.pack(first.x(), 69, first.z())), "above a palisade");
        RouteCell gate = wall.cells().get(1);
        assertFalse(wall.inColumn(Wall.pack(gate.x(), 65, gate.z())), "in a gate");
        // Packed as the game packs block positions.
        long packed = Wall.pack(-1234, -40, 98765);
        assertEquals(net.minecraft.core.BlockPos.asLong(-1234, -40, 98765), packed);
        assertEquals(-1234, Wall.packedX(packed));
        assertEquals(-40, Wall.packedY(packed));
        assertEquals(98765, Wall.packedZ(packed));
    }

    @Test
    void aNewWallTakesOverTheBlocksOfTheOld() {
        List<RouteCell> ring = flatRing(8, 64);
        Wall stone = wall(Wall.WallClass.STONE, ring, List.of());
        RouteCell a = ring.get(10);
        RouteCell b = ring.get(30);
        for (int up = 1; up <= 6; up++) {
            stone.markPlaced(Wall.pack(a.x(), 64 + up, a.z()));
        }
        stone.markPlaced(Wall.pack(b.x(), 65, b.z()));
        Wall palisade = new Wall(2, 0, US, Wall.WallClass.PALISADE, WORLD, 0, 0, ring, List.of(30), 0);
        palisade.inherit(stone, List.of(Wall.pack(0, 70, 0)));
        assertTrue(palisade.hasReplaced());
        assertEquals(8, palisade.replacedBlocks().length);
        assertTrue(palisade.replaces(Wall.pack(a.x(), 66, a.z())));
        assertTrue(palisade.inColumn(Wall.pack(a.x(), 68, a.z())), "swapped when the column goes up");
        assertFalse(palisade.inColumn(Wall.pack(a.x(), 69, a.z())), "the battlement over a palisade is taken down");
        assertFalse(palisade.inColumn(Wall.pack(b.x(), 65, b.z())), "the old block in a gate is taken down");
        assertFalse(palisade.inColumn(Wall.pack(0, 70, 0)), "off the ring");
        palisade.forgetReplaced(Wall.pack(0, 70, 0));
        assertFalse(palisade.replaces(Wall.pack(0, 70, 0)));
        Wall read = new Wall(2, 0, US, Wall.WallClass.PALISADE, WORLD, 0, 0, ring, List.of(30), 0);
        read.restoreReplaced(palisade.replacedBlocks());
        assertEquals(java.util.Arrays.toString(palisade.replacedBlocks()), java.util.Arrays.toString(read.replacedBlocks()));
        // What the new wall placed itself is never the old wall's.
        Wall again = wall(Wall.WallClass.STONE, ring, List.of());
        again.markPlaced(Wall.pack(a.x(), 65, a.z()));
        again.inherit(stone, List.of());
        assertFalse(again.replaces(Wall.pack(a.x(), 65, a.z())));
    }

    @Test
    void theGatesOfTheOldWallStayOnTheNewRing() {
        List<RouteCell> small = flatRing(8, 64);
        List<RouteCell> wide = flatRing(12, 64);
        List<RouteCell> gates = List.of(small.get(4), small.get(40));
        assertEquals(List.of(4, 40), WallContour.keptGates(small, gates, 2), "the same ring keeps the same places");
        List<Integer> moved = WallContour.keptGates(wide, gates, 4 + Wall.GATE_HALF + 1);
        assertEquals(2, moved.size());
        for (int i = 0; i < 2; i++) {
            RouteCell old = gates.get(i);
            RouteCell now = wide.get(moved.get(i));
            assertTrue(Math.abs(now.x() - old.x()) <= 6 && Math.abs(now.z() - old.z()) <= 6, "the gate went far: " + now);
        }
        assertTrue(WallContour.keptGates(wide, gates, 1).isEmpty(), "out of reach");
    }

    @Test
    void aWallAtABaseHeightStandsThere() {
        List<RouteCell> laid = WallContour.ring((x, z) -> Terrain.Column.land(height(x, z)), 0, 0, 12);
        int[] ground = new int[laid.size()];
        List<RouteCell> cells = new ArrayList<>();
        for (int i = 0; i < laid.size(); i++) {
            RouteCell cell = laid.get(i);
            ground[i] = cell.y();
            cells.add(new RouteCell(cell.x(), 69, cell.z(), cell.kind(), cell.depth(), cell.owner()));
        }
        Wall wall = new Wall(1, 0, US, Wall.WallClass.PALISADE, WORLD, 0, 0, cells, List.of(), 0, 70, ground);
        assertTrue(wall.hasBase());
        assertEquals(70, wall.baseY());
        int fill = 0;
        int cut = 0;
        for (Wall.Block block : wall.blocks()) {
            assertEquals(69, block.y());
            assertEquals(Math.max(0, 69 - ground[block.cell()]), wall.fill(block));
            assertEquals(Math.max(0, ground[block.cell()] - 69), wall.cut(block));
            fill += wall.fill(block);
            cut += wall.cut(block);
        }
        assertEquals(fill, wall.earth());
        assertEquals(cut, wall.cutTotal());
        assertTrue(fill > 0 && cut > 0, "the hills are both above and below 69");
        assertEquals(wall.blocks().size() * 4, wall.items(), "the earth is no item of the wall");
        // A gate keeps the base and the ground of the other columns.
        wall.addGate(20);
        assertEquals(70, wall.baseY());
        assertEquals(ground[5], wall.ground(5));
    }

    @Test
    void aWallOnTheGroundMovesNoEarth() {
        Wall wall = wall(Wall.WallClass.STONE, WallContour.ring((x, z) -> Terrain.Column.land(height(x, z)), 0, 0, 12), List.of());
        assertFalse(wall.hasBase());
        assertEquals(Wall.FOLLOW_GROUND, wall.baseY());
        assertEquals(0, wall.earth());
        assertEquals(0, wall.cutTotal());
        // The spoil of a wall: what it dug, taken one by one.
        wall.addSpoil("minecraft:dirt", 2);
        assertTrue(wall.useSpoil("minecraft:dirt"));
        assertEquals(1, wall.spoil().get("minecraft:dirt"));
        assertEquals(java.util.Map.of("minecraft:dirt", 1), wall.takeSpoil());
        assertTrue(wall.spoil().isEmpty());
        assertFalse(wall.useSpoil("minecraft:dirt"));
    }
}
