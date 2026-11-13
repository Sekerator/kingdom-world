package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plan of a mine (the staircase, the corridor and its branches, the levels, the edge of the land), the depth a
 * goal asks for and the veins of ore beside a tunnel.
 */
class MineTest {
    private static final int BOTTOM = -64;
    private static final MinePlan.Land EVERYWHERE = (x, z) -> true;

    private static List<MinePlan.Cell> plan(int postY, int depth, int levels, MinePlan.Land land) {
        return MinePlan.cells(0, postY, 0, depth, 48, 16, levels, BOTTOM, land);
    }

    private static List<MinePlan.Cell> of(List<MinePlan.Cell> cells, MinePlan.Kind kind, int level) {
        List<MinePlan.Cell> found = new ArrayList<>();
        for (MinePlan.Cell cell : cells) {
            if (cell.kind() == kind && cell.level() == level) {
                found.add(cell);
            }
        }
        return found;
    }

    /**
     * What every plan keeps: a miner can walk it and never digs away its post or the floor of a cell above.
     */
    private static void assertWalkable(List<MinePlan.Cell> cells, int postY) {
        Map<Long, List<MinePlan.Cell>> columns = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < cells.size(); i++) {
            MinePlan.Cell cell = cells.get(i);
            assertTrue(seen.add(cell.x() + "," + cell.y() + "," + cell.z()), "a cell is planned twice: " + cell);
            assertTrue(cell.x() != 0 || cell.z() != 0, "the miner digs at its own post: " + cell);
            assertTrue(cell.y() <= postY, "a mine never rises above its post: " + cell);
            assertTrue(cell.y() >= BOTTOM + MinePlan.BOTTOM_MARGIN, "a mine keeps away from the bottom of the world: " + cell);
            if (i == 0) {
                assertEquals(-1, cell.from(), "the first cell is entered from the post");
                assertEquals(1, Math.abs(cell.x()) + Math.abs(cell.z()), "the mine starts beside the post");
                assertEquals(postY, cell.y());
            } else {
                assertTrue(cell.from() >= 0 && cell.from() < i, "a cell is entered from one dug before it");
                MinePlan.Cell from = cells.get(cell.from());
                assertEquals(1, Math.abs(cell.x() - from.x()) + Math.abs(cell.z() - from.z()),
                        "neighbouring cells touch side by side: " + from + " -> " + cell);
                assertTrue(Math.abs(cell.y() - from.y()) <= 1, "a step is one block: " + from + " -> " + cell);
                if (cell.y() < from.y()) {
                    assertEquals(MinePlan.STAIR_HEIGHT, cell.height(), "stepping down needs a high cell: " + cell);
                }
                if (cell.y() > from.y()) {
                    assertEquals(MinePlan.STAIR_HEIGHT, from.height(), "stepping up needs a high cell below: " + from);
                }
            }
            columns.computeIfAbsent(((long) cell.x() << 32) ^ (cell.z() & 0xFFFFFFFFL), key -> new ArrayList<>()).add(cell);
        }
        for (List<MinePlan.Cell> column : columns.values()) {
            for (MinePlan.Cell upper : column) {
                for (MinePlan.Cell lower : column) {
                    if (lower.y() < upper.y()) {
                        assertTrue(lower.y() + lower.height() < upper.y(),
                                "a cell takes the floor from under the one above it: " + lower + " under " + upper);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- the staircase

    @Test
    void theStaircaseStaysInItsSquareBesideThePost() {
        // Every length of staircase, so every place of the square its last step can lie on.
        for (int depth = 99; depth >= 60; depth--) {
            List<MinePlan.Cell> cells = plan(100, depth, 1, EVERYWHERE);
            assertWalkable(cells, 100);
            int lowest = 100;
            for (MinePlan.Cell cell : cells) {
                if (cell.kind() == MinePlan.Kind.STAIR) {
                    assertTrue(Math.abs(cell.x()) <= 1 && cell.z() >= 1 && cell.z() <= 3, "a step leaves the square: " + cell);
                    assertFalse(cell.x() == 0 && cell.z() == 2, "the middle of the square stays as the newel: " + cell);
                    lowest = Math.min(lowest, cell.y());
                } else {
                    assertEquals(depth, cell.y(), "tunnels lie on the working depth: " + cell);
                    assertFalse(Math.abs(cell.x()) <= 1 && cell.z() >= 1 && cell.z() <= 3, "a tunnel cuts the staircase: " + cell);
                }
            }
            assertTrue(lowest == depth || lowest == depth + 1, "the staircase ends at the working depth " + depth + ": " + lowest);
            assertFalse(of(cells, MinePlan.Kind.CORRIDOR, 0).isEmpty(), "the staircase leads to a corridor at depth " + depth);
        }
    }

    @Test
    void aMineAtTheLevelOfThePostNeedsNoStairs() {
        List<MinePlan.Cell> cells = plan(60, 60, 1, EVERYWHERE);
        assertWalkable(cells, 60);
        for (MinePlan.Cell cell : cells) {
            assertEquals(60, cell.y());
            assertEquals(MinePlan.TUNNEL_HEIGHT, cell.height());
        }
        assertEquals(0, cells.get(0).x());
        assertEquals(1, cells.get(0).z(), "the mine starts in front of the post");
    }

    // ---------------------------------------------------------------- corridor and branches

    @Test
    void theCorridorRunsBothWaysWithBranchesEveryThirdBlock() {
        List<MinePlan.Cell> cells = MinePlan.cells(0, 100, 0, 20, 48, 16, 1, BOTTOM, EVERYWHERE);
        assertWalkable(cells, 100);
        List<MinePlan.Cell> corridor = of(cells, MinePlan.Kind.CORRIDOR, 0);
        // The land is the same along both axes: the corridor runs along x, past the staircase.
        Set<Integer> along = new TreeSet<>();
        for (MinePlan.Cell cell : corridor) {
            assertEquals(MinePlan.CORRIDOR_OFFSET, cell.z(), "the corridor is one block wide: " + cell);
            assertEquals(MinePlan.TUNNEL_HEIGHT, cell.height());
            along.add(cell.x());
        }
        assertEquals(97, along.size(), "48 blocks each way and the middle");
        assertEquals(-48, along.iterator().next());
        assertTrue(along.contains(48));

        Map<Integer, int[]> branches = new HashMap<>();
        for (MinePlan.Cell cell : of(cells, MinePlan.Kind.BRANCH, 0)) {
            int[] reach = branches.computeIfAbsent(cell.x(), x -> new int[]{MinePlan.CORRIDOR_OFFSET, MinePlan.CORRIDOR_OFFSET});
            reach[0] = Math.min(reach[0], cell.z());
            reach[1] = Math.max(reach[1], cell.z());
        }
        for (int x = -48; x <= 48; x++) {
            assertEquals(x % MinePlan.BRANCH_SPACING == 0, branches.containsKey(x), "branches leave every third block: " + x);
        }
        for (Map.Entry<Integer, int[]> branch : branches.entrySet()) {
            assertEquals(MinePlan.CORRIDOR_OFFSET + 16, branch.getValue()[1], "a branch is 16 blocks long: " + branch.getKey());
            // Behind the middle of the corridor stands the staircase.
            assertEquals(branch.getKey() == 0 ? MinePlan.CORRIDOR_OFFSET : MinePlan.CORRIDOR_OFFSET - 16, branch.getValue()[0],
                    "a branch goes to both sides: " + branch.getKey());
        }
        List<MinePlan.Cell> shorter = MinePlan.cells(0, 100, 0, 20, 8, 4, 1, BOTTOM, EVERYWHERE);
        for (MinePlan.Cell cell : shorter) {
            if (cell.kind() != MinePlan.Kind.STAIR) {
                assertTrue(Math.abs(cell.x()) <= 8 && Math.abs(cell.z() - MinePlan.CORRIDOR_OFFSET) <= 4, "the lengths are settings: " + cell);
            }
        }
    }

    @Test
    void tunnelsStopAtTheEdgeOfTheLand() {
        // The land ends 10 blocks east of the post and 6 blocks south of the corridor.
        MinePlan.Land land = (x, z) -> x <= 10 && z <= MinePlan.CORRIDOR_OFFSET + 6;
        List<MinePlan.Cell> cells = plan(100, 20, 4, land);
        assertWalkable(cells, 100);
        int east = Integer.MIN_VALUE;
        int south = Integer.MIN_VALUE;
        int west = Integer.MAX_VALUE;
        for (MinePlan.Cell cell : cells) {
            assertTrue(land.owns(cell.x(), cell.z()), "a cell lies off the land: " + cell);
            east = Math.max(east, cell.x());
            west = Math.min(west, cell.x());
            south = Math.max(south, cell.z());
        }
        assertEquals(10, east, "the corridor goes up to the edge");
        assertEquals(-48, west, "and its full length where the land allows");
        assertEquals(MinePlan.CORRIDOR_OFFSET + 6, south);
    }

    @Test
    void theCorridorTakesTheAxisWithMoreLand() {
        assertTrue(MinePlan.layout(0, 0, 48, EVERYWHERE).alongX(), "x when the land is the same");
        assertEquals(1, MinePlan.layout(0, 0, 48, EVERYWHERE).dz(), "in front of the post");
        // A strip of land 12 blocks wide along z.
        MinePlan.Land strip = (x, z) -> Math.abs(x) <= 6;
        MinePlan.Layout layout = MinePlan.layout(0, 0, 48, strip);
        assertFalse(layout.alongX());
        List<MinePlan.Cell> cells = plan(100, 20, 1, strip);
        assertWalkable(cells, 100);
        int reach = 0;
        for (MinePlan.Cell cell : of(cells, MinePlan.Kind.CORRIDOR, 0)) {
            reach = Math.max(reach, Math.abs(cell.z()));
        }
        assertEquals(48, reach, "the corridor runs along the strip");
        // The staircase goes to the side of the post where its square is on the land.
        MinePlan.Land north = (x, z) -> z <= 1;
        assertEquals(-1, MinePlan.layout(0, 0, 48, north).dz());
        assertWalkable(plan(100, 20, 2, north), 100);
    }

    // ---------------------------------------------------------------- levels and depth

    @Test
    void theNextLevelLiesFourBlocksLower() {
        assertEquals(4, GatheringRules.MINE_LEVEL_STEP);
        assertEquals(List.of(20, 16, 12, 8), MinePlan.floors(20, 100, BOTTOM, 4));
        assertEquals(List.of(20), MinePlan.floors(20, 100, BOTTOM, 1));
        assertEquals(List.of(60, 56, 52), MinePlan.floors(60, 60, BOTTOM, 3), "a mine at the level of the post goes down too");
        // At the bottom of the world the levels go up instead.
        assertEquals(List.of(-58, -54, -50, -46), MinePlan.floors(-58, 70, BOTTOM, 4));
        assertEquals(List.of(-56, -52, -48), MinePlan.floors(-56, 70, BOTTOM, 3));
        assertEquals(List.of(-55), MinePlan.floors(-55, -50, BOTTOM, 4), "but never up to the surface of the post");

        List<MinePlan.Cell> cells = plan(100, 20, 4, EVERYWHERE);
        assertWalkable(cells, 100);
        Set<Integer> floors = new TreeSet<>();
        int level = 0;
        for (MinePlan.Cell cell : cells) {
            assertTrue(cell.level() >= level, "a level is planned after the one before it");
            level = cell.level();
            if (cell.kind() != MinePlan.Kind.STAIR) {
                floors.add(cell.y());
                assertEquals(20 - 4 * cell.level(), cell.y());
            }
        }
        assertEquals(Set.of(20, 16, 12, 8), floors);
        for (int depth = -58; depth <= -50; depth++) {
            List<MinePlan.Cell> deep = plan(70, depth, 4, EVERYWHERE);
            assertWalkable(deep, 70);
            for (int floor : MinePlan.floors(depth, 70, BOTTOM, 4)) {
                boolean found = false;
                for (MinePlan.Cell cell : deep) {
                    found |= cell.kind() == MinePlan.Kind.CORRIDOR && cell.y() == floor;
                }
                assertTrue(found, "the level at " + floor + " of a mine at " + depth + " has a corridor");
            }
        }
    }

    @Test
    void theDepthStaysBetweenTheBottomOfTheWorldAndThePost() {
        assertEquals(16, MinePlan.clampDepth(16, 70, BOTTOM));
        assertEquals(70, MinePlan.clampDepth(232, 70, BOTTOM), "a depth above the post means the level of the post");
        assertEquals(-58, MinePlan.clampDepth(-64, 70, BOTTOM));
        assertEquals(-58, MinePlan.clampDepth(-2_000, 70, BOTTOM));
        assertEquals(-60, MinePlan.clampDepth(-58, -60, BOTTOM), "a post below the margin digs at its own level");

        MineRules rules = MineRules.DEFAULT;
        assertEquals(70, rules.depth(MineGoal.ANYTHING, 70, BOTTOM), "anything is dug right at the post");
        assertEquals(16, rules.depth(MineGoal.ANYTHING.withOre("forge:ores/iron"), 70, BOTTOM));
        assertEquals(-58, rules.depth(MineGoal.ANYTHING.withOre("forge:ores/diamond"), 70, BOTTOM));
        assertEquals(70, rules.depth(MineGoal.ANYTHING.withOre("forge:ores/emerald"), 70, BOTTOM));
        assertEquals(232, rules.depth(MineGoal.ANYTHING.withOre("forge:ores/emerald"), 250, BOTTOM));
        assertEquals(16, rules.depth(MineGoal.ANYTHING.withOre("forge:ores/tin"), 70, BOTTOM), "an unknown kind is looked for at 16");
        assertEquals(40, rules.depth(MineGoal.ANYTHING.withOre("forge:ores/iron").withDepth(40), 70, BOTTOM), "the player's depth wins");
        assertEquals(-58, rules.depth(MineGoal.ANYTHING.withDepth(-200), 70, BOTTOM));
        assertEquals(12, rules.depth(MineGoal.ANYTHING.withOre("forge:ores/iron"), 12, BOTTOM));
    }

    // ---------------------------------------------------------------- settings and goals

    @Test
    void depthsOfOresAreReadFromTheConfig() {
        assertEquals(Map.entry("forge:ores/iron", 16), MineRules.parseDepth("forge:ores/iron=16").orElseThrow());
        assertEquals(Map.entry("forge:ores/diamond", -58), MineRules.parseDepth(" forge:ores/diamond = -58 ").orElseThrow());
        assertTrue(MineRules.parseDepth("forge:ores/iron").isEmpty());
        assertTrue(MineRules.parseDepth("forge:ores/iron=deep").isEmpty());
        assertTrue(MineRules.parseDepth("=16").isEmpty());
        assertTrue(MineRules.parseDepth("Iron Ore=16").isEmpty());
        assertTrue(MineRules.parseDepth("forge:ores/iron=99999").isEmpty());
        assertTrue(MineRules.parseDepth(null).isEmpty());
        Map<String, Integer> depths = MineRules.parseDepths(List.of("forge:ores/tin=40", "nonsense", "forge:ores/tin=44", "mod:ores/silver=-8"));
        assertEquals(Map.of("forge:ores/tin", 44, "mod:ores/silver", -8), depths);
        Map<String, Integer> defaults = MineRules.parseDepths(MineRules.defaultDepths());
        assertEquals(8, defaults.size(), "every default line is a depth");
        assertEquals(96, defaults.get("forge:ores/coal"));
        assertEquals(48, defaults.get("forge:ores/copper"));
        assertEquals(0, defaults.get("forge:ores/lapis"));
        assertEquals(-16, defaults.get("forge:ores/gold"));
        assertEquals(-58, defaults.get("forge:ores/redstone"));
        assertEquals(232, defaults.get("forge:ores/emerald"));
        assertEquals(48, MineRules.DEFAULT.corridorLength());
        assertEquals(16, MineRules.DEFAULT.branchLength());
        assertEquals(24, MineRules.DEFAULT.veinBlocks());
    }

    @Test
    void aGoalIsMetWhenTheStorehousesHoldTheAmount() {
        MineGoal iron = new MineGoal("forge:ores/iron", 64, true, 0);
        assertFalse(iron.metBy(63));
        assertTrue(iron.metBy(64));
        assertTrue(iron.metBy(1_000));
        assertFalse(iron.withAmount(0).metBy(1_000), "without a limit a goal is never met");
        assertFalse(MineGoal.ANYTHING.metBy(1_000));
        assertTrue(iron.sameMine(iron.withAmount(128)), "the amount does not move the mine");
        assertFalse(iron.sameMine(iron.withOre("forge:ores/gold")));
        assertFalse(iron.sameMine(iron.withDepth(16)));
        assertEquals(0, iron.withDepth(40).withAutoDepth().depth());
        assertTrue(MineGoal.isValidOre(""));
        assertTrue(MineGoal.isValidOre("forge:ores/iron"));
        assertFalse(MineGoal.isValidOre("iron"));
        assertFalse(MineGoal.isValidOre("forge:ores/Iron"));
    }

    // ---------------------------------------------------------------- veins

    @Test
    void aVeinIsTheOreTouchingTheUncoveredBlock() {
        Set<Veins.Block> ore = new HashSet<>();
        // A bent vein of five blocks, a block that touches it only by a corner, and a vein behind a block of rock.
        ore.add(new Veins.Block(1, 10, 0));
        ore.add(new Veins.Block(2, 10, 0));
        ore.add(new Veins.Block(2, 11, 0));
        ore.add(new Veins.Block(2, 11, 1));
        ore.add(new Veins.Block(3, 11, 1));
        ore.add(new Veins.Block(4, 12, 2));
        ore.add(new Veins.Block(1, 10, 2));
        Veins.Ore test = (x, y, z) -> ore.contains(new Veins.Block(x, y, z));

        List<Veins.Block> vein = Veins.blocks(1, 10, 0, test, 24);
        assertEquals(5, vein.size());
        assertEquals(new Veins.Block(1, 10, 0), vein.get(0), "the uncovered block comes first");
        assertFalse(vein.contains(new Veins.Block(4, 12, 2)), "a corner is not a side");
        assertFalse(vein.contains(new Veins.Block(1, 10, 2)), "ore behind rock stays unknown");
        for (int i = 1; i < vein.size(); i++) {
            boolean touches = false;
            for (int j = 0; j < i; j++) {
                Veins.Block a = vein.get(i);
                Veins.Block b = vein.get(j);
                touches |= Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z()) == 1;
            }
            assertTrue(touches, "every block is reached from the hole of one taken before it: " + vein.get(i));
        }
        assertEquals(List.of(new Veins.Block(1, 10, 0), new Veins.Block(2, 10, 0), new Veins.Block(2, 11, 0)),
                Veins.blocks(1, 10, 0, test, 3), "the nearest blocks up to the limit");
        assertTrue(Veins.blocks(1, 10, 0, test, 0).isEmpty(), "a limit of 0 leaves veins alone");
        assertTrue(Veins.blocks(0, 10, 0, test, 24).isEmpty(), "rock is not a vein");
    }
}
