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
}
