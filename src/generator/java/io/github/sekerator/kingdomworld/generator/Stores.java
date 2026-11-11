package io.github.sekerator.kingdomworld.generator;

import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.BARREL;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.BED;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.COBBLE;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.COBBLE_STAIRS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.DARK_SLAB;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.DARK_STAIRS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.FENCE;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.HANGING_LANTERN;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.LANTERN;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.OAK_DOOR;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.OAK_LOG;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.PANE_X;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.PANE_Z;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.SPRUCE_LOG;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.SPRUCE_PLANKS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.STONE_BRICKS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.STONE_BRICK_WALL;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores and trade houses of a town: a timber barn, a walled store yard, a covered market hall and a wayside inn. Each has
 * an outline of its own (a broken barn roof, a wall round a yard, a two-tier roof on posts, an inn of two wings), so that a
 * street of them does not look like a row of the same box.
 */
final class Stores {
    private static final String DARK_PLANKS = "minecraft:dark_oak_planks";
    private static final String DARK_LOG = "minecraft:dark_oak_log";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String GRAVEL = "minecraft:gravel";
    private static final String WHITE_TERRACOTTA = "minecraft:white_terracotta";
    private static final String STOREHOUSE = "kingdomworld:storehouse";
    private static final String WORK_POST = "kingdomworld:work_post";
    private static final String CHAIN = "minecraft:chain[axis=y,waterlogged=false]";
    private static final String SLAB = "minecraft:spruce_slab[type=bottom,waterlogged=false]";

    private Stores() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("barn", "farming", "warehouse", "minecraft:hay_block", 6, 1, 10, barn(),
                "warehouse", 1, ""));
        list.add(new BuildingGenerator.Building("storage_yard", "farming", "warehouse", STOREHOUSE, 10, 1, 10, storageYard(),
                "warehouse", 1, ""));
        list.add(new BuildingGenerator.Building("market_hall", "trade", "market", "kingdomworld:market_stall", 6, 1, 10, marketHall(),
                "market", 1, ""));
        list.add(new BuildingGenerator.Building("inn", "trade", "tavern", "minecraft:barrel", 9, 0, 10, inn(), "tavern", 1, ""));
        return list;
    }

    private static String stairs(String block, String facing) {
        return roof(block, facing, "straight");
    }

    private static String roof(String block, String facing, String shape) {
        return block + "[facing=" + facing + ",half=bottom,shape=" + shape + ",waterlogged=false]";
    }

    private static String wallTorch(String facing) {
        return "minecraft:wall_torch[facing=" + facing + "]";
    }

    private static String chest(String facing) {
        return "minecraft:chest[facing=" + facing + ",type=single,waterlogged=false]";
    }

    private static String ladder(String facing) {
        return "minecraft:ladder[facing=" + facing + ",waterlogged=false]";
    }

    private static String fence(boolean north, boolean south, boolean east, boolean west) {
        return "minecraft:oak_fence[north=" + north + ",south=" + south + ",east=" + east + ",west=" + west + ",waterlogged=false]";
    }

    /**
     * Leaves out every position of the ground layer that the building does not pave, so that the ground there stays.
     */
    private static void keepGround(Structure s) {
        for (int z = 0; z < s.sizeZ; z++) {
            for (int x = 0; x < s.sizeX; x++) {
                if (!s.solid(x, 0, z)) {
                    s.skip(x, 0, z);
                }
            }
        }
    }

    /**
     * One ring of a hip roof: stairs on all four sides facing inwards, turned stairs at the corners.
     */
    private static void hipRing(Structure s, int west, int east, int back, int front, int y, String block) {
        s.fill(west, y, back, east, y, back, roof(block, "south", "straight"));
        s.fill(west, y, front, east, y, front, roof(block, "north", "straight"));
        s.fill(west, y, back + 1, west, y, front - 1, roof(block, "east", "straight"));
        s.fill(east, y, back + 1, east, y, front - 1, roof(block, "west", "straight"));
        s.set(west, y, back, roof(block, "south", "outer_left"));
        s.set(east, y, back, roof(block, "south", "outer_right"));
        s.set(west, y, front, roof(block, "north", "outer_right"));
        s.set(east, y, front, roof(block, "north", "outer_left"));
    }

    // ---------------------------------------------------------------- the barn

    /**
     * Height of the lowest roof block of the barn above a column, by its distance from the nearer eave.
     */
    private static int barnRoofFoot(int distance) {
        return switch (distance) {
            case 0 -> 4;
            case 1 -> 5;
            case 2 -> 7;
            default -> 6 + distance;
        };
    }

    /**
     * A big timber barn on a stone footing, its broken roof steep at the eaves and flatter above, with the gable to the
     * street: a wide doorway two storeys high under a hoist beam with a lantern, a hay loft over the front half reached by a
     * ladder, two storehouse blocks and chests and barrels along the walls below.
     */
    static Structure barn() {
        Structure s = new Structure(13, 13, 11);
        s.fill(1, 0, 1, 11, 0, 9, COBBLE).fill(2, 0, 2, 10, 0, 8, SPRUCE_PLANKS);
        s.fill(5, 0, 10, 7, 0, 10, COBBLE);
        // A footing of stone two blocks high, plank walls above it in a frame of spruce.
        s.walls(1, 1, 1, 11, 2, 9, COBBLE).walls(1, 3, 1, 11, 3, 9, SPRUCE_PLANKS);
        s.corners(1, 3, 1, 11, 3, 9, SPRUCE_LOG);
        s.set(1, 3, 5, SPRUCE_LOG + "[axis=y]").set(11, 3, 5, SPRUCE_LOG + "[axis=y]");
        s.set(4, 3, 1, SPRUCE_LOG + "[axis=y]").set(8, 3, 1, SPRUCE_LOG + "[axis=y]");
        s.beam(1, 1, 11, 9, 4, SPRUCE_LOG);
        // The wide doorway between two posts, open three high.
        s.fill(4, 1, 9, 4, 3, 9, SPRUCE_LOG + "[axis=y]").fill(8, 1, 9, 8, 3, 9, SPRUCE_LOG + "[axis=y]");
        s.fill(5, 1, 9, 7, 3, 9, Structure.AIR);
        s.set(1, 3, 3, PANE_Z).set(1, 3, 7, PANE_Z).set(11, 3, 3, PANE_Z).set(11, 3, 7, PANE_Z);
        s.set(2, 3, 1, PANE_X).set(6, 3, 1, PANE_X).set(10, 3, 1, PANE_X);
        // The broken roof: two blocks up per column at the eaves, one above, the ridge along z.
        for (int x = 0; x <= 12; x++) {
            int d = Math.min(x, 12 - x);
            String facing = x < 6 ? "east" : "west";
            int foot = barnRoofFoot(d);
            if (d == 6) {
                s.fill(x, foot, 0, x, foot, 10, DARK_SLAB + "[type=bottom,waterlogged=false]");
            } else if (d == 1 || d == 2) {
                s.fill(x, foot, 0, x, foot, 10, DARK_PLANKS);
                s.fill(x, foot + 1, 0, x, foot + 1, 10, stairs(DARK_STAIRS, facing));
            } else {
                s.fill(x, foot, 0, x, foot, 10, stairs(DARK_STAIRS, facing));
            }
            // The gables at the back and the front close the roof down to the beam.
            if (d >= 2) {
                s.fill(x, 5, 1, x, foot - 1, 1, SPRUCE_PLANKS);
                s.fill(x, 5, 9, x, foot - 1, 9, SPRUCE_PLANKS);
            }
        }
        s.fill(2, 5, 1, 10, 5, 1, SPRUCE_LOG + "[axis=x]").fill(2, 5, 9, 10, 5, 9, SPRUCE_LOG + "[axis=x]");
        s.set(6, 8, 1, PANE_X);
        // The hay door in the front gable, the hoist beam over it with a lantern under its end.
        s.fill(6, 6, 9, 6, 7, 9, Structure.AIR);
        s.set(6, 9, 9, SPRUCE_LOG + "[axis=z]").set(6, 9, 10, SPRUCE_LOG + "[axis=z]");
        s.set(6, 8, 10, HANGING_LANTERN);
        // The loft over the front half on a post, a rail along its edge and a ladder up beside the post.
        s.fill(2, 5, 5, 10, 5, 8, SPRUCE_PLANKS);
        s.fill(9, 1, 5, 9, 4, 5, SPRUCE_LOG + "[axis=y]");
        for (int y = 1; y <= 5; y++) {
            s.set(9, y, 4, ladder("north"));
        }
        for (int x = 2; x <= 8; x++) {
            s.set(x, 6, 4, fence(false, false, x < 8, x > 2));
        }
        s.fill(3, 6, 7, 5, 6, 8, HAY).set(4, 7, 8, HAY).set(8, 6, 8, HAY).set(8, 6, 7, HAY);
        // Below: the two storehouse blocks at the back wall among chests and barrels.
        s.set(4, 1, 2, STOREHOUSE).set(8, 1, 2, STOREHOUSE);
        s.set(2, 1, 2, BARREL).set(2, 2, 2, BARREL).set(3, 1, 2, chest("south"));
        s.set(5, 1, 2, BARREL).set(6, 1, 2, HAY).set(6, 2, 2, HAY).set(7, 1, 2, BARREL);
        s.set(9, 1, 2, chest("south")).set(10, 1, 2, BARREL).set(10, 2, 2, BARREL);
        s.set(2, 1, 3, chest("east")).set(2, 1, 4, BARREL);
        s.set(10, 1, 3, chest("west")).set(10, 1, 4, BARREL);
        s.set(2, 1, 6, HAY).set(2, 1, 7, HAY).set(2, 2, 7, HAY).set(3, 1, 8, HAY);
        s.set(10, 1, 7, HAY).set(10, 1, 8, HAY).set(10, 2, 8, HAY);
        // Light below, on the loft and outside.
        s.set(4, 4, 6, HANGING_LANTERN).set(8, 4, 7, HANGING_LANTERN);
        s.set(2, 3, 4, wallTorch("east")).set(10, 3, 4, wallTorch("west")).set(5, 3, 2, wallTorch("south"));
        s.set(3, 7, 6, wallTorch("east")).set(9, 7, 6, wallTorch("west"));
        s.set(3, 3, 10, wallTorch("south")).set(9, 3, 10, wallTorch("south"));
        keepGround(s);
        return s;
    }

    // ---------------------------------------------------------------- the store yard

    /**
     * One open shed of the store yard against the back wall: a roof leaning on the wall and rising to the front, carried by
     * posts, the end over the side wall closed.
     *
     * @param x1    the first column of the roof
     * @param x2    the last column
     * @param outer the column over the side wall of the yard, whose triangle under the roof is closed
     */
    private static void shed(Structure s, int x1, int x2, int outer) {
        s.fill(x1, 4, 1, x2, 4, 1, stairs(DARK_STAIRS, "south"));
        s.fill(x1, 5, 2, x2, 5, 2, stairs(DARK_STAIRS, "south"));
        s.fill(x1, 6, 3, x2, 6, 3, stairs(DARK_STAIRS, "south"));
        s.fill(x1, 7, 4, x2, 7, 4, stairs(DARK_STAIRS, "south"));
        s.set(outer, 5, 3, SPRUCE_PLANKS).set(outer, 5, 4, SPRUCE_PLANKS).set(outer, 6, 4, SPRUCE_PLANKS);
    }

    /**
     * Shelves of one shed: the storehouse block in the middle of the back wall, chests beside it, barrels stacked at the
     * ends and one lying on its side as a crate.
     *
     * @param x the column of the storehouse block
     */
    private static void shedGoods(Structure s, int x, int crateX, String crateFacing) {
        s.set(x, 1, 1, STOREHOUSE);
        s.set(x - 1, 1, 1, chest("south")).set(x + 1, 1, 1, chest("south"));
        s.set(x - 2, 1, 1, BARREL).set(x - 3, 1, 1, BARREL).set(x + 2, 1, 1, BARREL).set(x + 3, 1, 1, BARREL);
        s.set(x - 3, 2, 1, BARREL).set(x + 3, 2, 1, BARREL);
        s.set(crateX, 1, 2, "minecraft:barrel[facing=" + crateFacing + ",open=false]");
        s.set(x, 4, 2, HANGING_LANTERN);
    }

    /**
     * A walled store yard with one gate: a stone wall four high all round, a gate under a timber lintel with lantern posts
     * before it, a yard of gravel and cobbles, two open sheds leaning on the back wall with a storehouse block each, a guard
     * booth just inside the gate, stacked logs and hay. The guard stands in the middle just inside the gate.
     */
    static Structure storageYard() {
        Structure s = new Structure(21, 8, 11);
        s.fill(0, 0, 0, 20, 0, 9, COBBLE);
        s.fill(1, 0, 1, 19, 0, 8, GRAVEL);
        s.fill(9, 0, 1, 11, 0, 9, COBBLE).fill(1, 0, 6, 19, 0, 6, COBBLE);
        s.fill(1, 0, 1, 7, 0, 4, SPRUCE_PLANKS).fill(13, 0, 1, 19, 0, 4, SPRUCE_PLANKS);
        s.fill(8, 0, 10, 12, 0, 10, COBBLE);
        // The wall: cobbles with a course of stone bricks on top and pillars with caps.
        s.walls(0, 1, 0, 20, 3, 9, COBBLE).walls(0, 4, 0, 20, 4, 9, STONE_BRICKS);
        for (int[] pillar : new int[][]{{0, 0}, {20, 0}, {0, 9}, {20, 9}, {5, 0}, {15, 0}, {10, 0}, {4, 9}, {16, 9}, {8, 9}, {12, 9}}) {
            s.fill(pillar[0], 1, pillar[1], pillar[0], 4, pillar[1], STONE_BRICKS);
            s.set(pillar[0], 5, pillar[1], STONE_BRICK_WALL);
        }
        // The one gate, three wide and three high under a lintel of logs.
        s.fill(9, 1, 9, 11, 3, 9, Structure.AIR);
        s.fill(9, 4, 9, 11, 4, 9, DARK_LOG + "[axis=x]");
        s.fill(9, 5, 9, 11, 5, 9, DARK_SLAB + "[type=bottom,waterlogged=false]");
        for (int x : new int[]{7, 13}) {
            s.fill(x, 1, 10, x, 2, 10, FENCE).set(x, 3, 10, LANTERN);
        }
        s.set(3, 3, 10, wallTorch("south")).set(17, 3, 10, wallTorch("south"));
        // Two sheds leaning on the back wall.
        shed(s, 0, 7, 0);
        shed(s, 13, 20, 20);
        s.set(0, 4, 1, STONE_BRICKS).set(20, 4, 1, STONE_BRICKS);
        for (int x : new int[]{1, 4, 7, 13, 16, 19}) {
            s.fill(x, 1, 4, x, 6, 4, SPRUCE_LOG + "[axis=y]");
        }
        shedGoods(s, 4, 1, "east");
        shedGoods(s, 16, 19, "west");
        // The guard booth east of the gate: a plank side, a roof of slabs, a fence post, a seat and a lantern.
        s.fill(14, 1, 7, 14, 2, 8, SPRUCE_PLANKS);
        s.fill(12, 3, 7, 14, 3, 8, DARK_SLAB + "[type=bottom,waterlogged=false]");
        s.fill(12, 1, 7, 12, 2, 7, FENCE);
        s.set(13, 1, 8, stairs(DARK_STAIRS, "south"));
        s.set(13, 2, 7, HANGING_LANTERN);
        // Stacked logs on the west side, hay on the east, hay and a barrel at the back between the sheds.
        s.fill(1, 1, 7, 4, 1, 8, OAK_LOG + "[axis=x]").fill(1, 2, 8, 3, 2, 8, OAK_LOG + "[axis=x]");
        s.fill(17, 1, 8, 19, 1, 8, HAY).fill(18, 1, 7, 19, 1, 7, HAY).fill(18, 2, 8, 19, 2, 8, HAY);
        s.set(8, 1, 1, HAY).set(12, 1, 1, HAY).set(12, 2, 1, HAY).set(9, 1, 1, BARREL);
        // Light in the yard.
        s.set(10, 3, 1, wallTorch("south"));
        s.set(1, 3, 6, wallTorch("east")).set(19, 3, 6, wallTorch("west"));
        s.set(6, 3, 8, wallTorch("north")).set(16, 3, 8, wallTorch("north"));
        keepGround(s);
        return s;
    }

    // ---------------------------------------------------------------- the market hall

    /**
     * A covered market hall open on all sides: a paved floor, two rows of stout posts on stone bases carrying a hip roof
     * with a small raised roof on top that lets the light in, a striped awning along the front eave; under it the market
     * stall with the merchant's post behind it, two counters, barrels and hay.
     */
    static Structure marketHall() {
        Structure s = new Structure(13, 10, 11);
        s.fill(0, 0, 0, 12, 0, 10, COBBLE);
        s.walls(0, 0, 0, 12, 0, 10, STONE_BRICKS);
        s.fill(3, 0, 3, 9, 0, 7, "minecraft:smooth_stone");
        // Posts of logs on stone bases, tied by a ring of beams.
        for (int x : new int[]{2, 4, 8, 10}) {
            for (int z : new int[]{2, 8}) {
                s.set(x, 1, z, STONE_BRICKS).fill(x, 2, z, x, 3, z, DARK_LOG + "[axis=y]");
            }
        }
        s.set(2, 1, 5, STONE_BRICKS).fill(2, 2, 5, 2, 3, 5, DARK_LOG + "[axis=y]");
        s.set(10, 1, 5, STONE_BRICKS).fill(10, 2, 5, 10, 3, 5, DARK_LOG + "[axis=y]");
        s.beam(2, 2, 10, 8, 4, SPRUCE_LOG);
        // The hip roof from eaves over the edge of the floor up to an opening in the middle.
        hipRing(s, 1, 11, 1, 9, 4, DARK_STAIRS);
        hipRing(s, 2, 10, 2, 8, 5, DARK_STAIRS);
        // Over the opening a band of fences between posts and a small roof overhanging it.
        s.walls(3, 5, 3, 9, 5, 7, SPRUCE_PLANKS);
        for (int x = 4; x <= 8; x++) {
            s.set(x, 6, 3, fence(false, false, true, true)).set(x, 6, 7, fence(false, false, true, true));
        }
        for (int z = 4; z <= 6; z++) {
            s.set(3, 6, z, fence(true, true, false, false)).set(9, 6, z, fence(true, true, false, false));
        }
        s.corners(3, 6, 3, 9, 6, 7, SPRUCE_LOG);
        hipRing(s, 2, 10, 2, 8, 7, DARK_STAIRS);
        hipRing(s, 3, 9, 3, 7, 8, DARK_STAIRS);
        s.fill(4, 9, 4, 8, 9, 6, DARK_SLAB + "[type=bottom,waterlogged=false]");
        // The striped awning along the front eave.
        for (int x = 1; x <= 11; x++) {
            s.set(x, 4, 10, x % 2 == 1 ? "minecraft:yellow_wool" : "minecraft:blue_wool");
        }
        // The stall with the merchant's post behind it, counters of slabs on fences beside it.
        s.set(6, 1, 4, "kingdomworld:market_stall");
        s.set(6, 1, 3, WORK_POST);
        s.set(4, 1, 4, FENCE).set(4, 2, 4, SLAB).set(8, 1, 4, FENCE).set(8, 2, 4, SLAB);
        s.set(5, 1, 1, BARREL).set(6, 1, 1, BARREL).set(7, 1, 1, BARREL).set(6, 2, 1, BARREL);
        s.set(1, 1, 3, BARREL).set(11, 1, 6, BARREL);
        s.set(1, 1, 6, HAY).set(1, 1, 7, HAY).set(1, 2, 7, HAY);
        s.set(11, 1, 3, HAY).set(11, 1, 4, HAY).set(11, 2, 3, HAY);
        // Lanterns under the beams and on a chain from the small roof, lamps at the front corners.
        s.set(6, 3, 2, HANGING_LANTERN).set(6, 3, 8, HANGING_LANTERN).set(2, 3, 4, HANGING_LANTERN).set(10, 3, 6, HANGING_LANTERN);
        s.set(6, 8, 5, CHAIN).set(6, 7, 5, CHAIN).set(6, 6, 5, HANGING_LANTERN);
        for (int x : new int[]{0, 12}) {
            s.set(x, 1, 10, FENCE).set(x, 2, 10, LANTERN);
        }
        return s;
    }

    // ---------------------------------------------------------------- the inn

    /**
     * Fills the walls of the inn's L at the given heights: a back wing across the whole width and a front wing on the west.
     */
    private static void innWalls(Structure s, int y1, int y2, String state) {
        s.fill(1, y1, 1, 11, y2, 1, state);
        s.fill(11, y1, 1, 11, y2, 6, state);
        s.fill(6, y1, 6, 11, y2, 6, state);
        s.fill(6, y1, 6, 6, y2, 9, state);
        s.fill(1, y1, 9, 6, y2, 9, state);
        s.fill(1, y1, 1, 1, y2, 9, state);
    }

    /**
     * A ring of lying logs round the inn's L at one height.
     */
    private static void innBeam(Structure s, int y, String log) {
        s.fill(1, y, 1, 11, y, 1, log + "[axis=x]");
        s.fill(6, y, 6, 11, y, 6, log + "[axis=x]");
        s.fill(1, y, 9, 6, y, 9, log + "[axis=x]");
        s.fill(11, y, 2, 11, y, 5, log + "[axis=z]");
        s.fill(6, y, 7, 6, y, 8, log + "[axis=z]");
        s.fill(1, y, 2, 1, y, 8, log + "[axis=z]");
    }

    /**
     * A wayside inn of two floors in an L: a back wing with its gable to the east side and a chimney there, a front wing
     * with its gable to the street, a stone ground floor and a timber frame with white infill above, a terrace in the
     * corner of the L with the door, a sign post with a lantern and a bench. Below: a counter with the innkeeper's post
     * behind it, barrels and a smoker, three tables with benches; above, reached by a ladder, four beds for guests.
     */
    static Structure inn() {
        Structure s = new Structure(13, 14, 11);
        s.fill(1, 0, 1, 11, 0, 6, COBBLE).fill(1, 0, 7, 6, 0, 9, COBBLE).fill(7, 0, 7, 11, 0, 9, COBBLE);
        s.fill(2, 0, 2, 10, 0, 5, SPRUCE_PLANKS).fill(2, 0, 6, 5, 0, 8, SPRUCE_PLANKS);
        s.set(9, 0, 10, stairs(COBBLE_STAIRS, "north"));
        // The ground floor of stone with cobbled corners.
        innWalls(s, 1, 4, STONE_BRICKS);
        for (int[] corner : new int[][]{{1, 1}, {11, 1}, {11, 6}, {6, 6}, {6, 9}, {1, 9}}) {
            s.fill(corner[0], 1, corner[1], corner[0], 4, corner[1], COBBLE);
        }
        s.fill(5, 2, 1, 5, 3, 1, PANE_X).fill(1, 2, 4, 1, 3, 4, PANE_Z).fill(1, 2, 7, 1, 3, 7, PANE_Z);
        s.fill(11, 2, 5, 11, 3, 5, PANE_Z).fill(3, 2, 9, 4, 3, 9, PANE_X).fill(7, 2, 6, 7, 3, 6, PANE_X).fill(6, 2, 8, 6, 3, 8, PANE_Z);
        s.door(9, 1, 6, OAK_DOOR, "north", "left");
        s.fill(8, 4, 7, 10, 4, 7, stairs(DARK_STAIRS, "north"));
        // The upper floor: white infill in a frame of oak.
        s.fill(2, 5, 2, 10, 5, 5, SPRUCE_PLANKS).fill(2, 5, 6, 5, 5, 8, SPRUCE_PLANKS);
        innWalls(s, 6, 8, WHITE_TERRACOTTA);
        for (int[] post : new int[][]{{1, 1}, {11, 1}, {11, 6}, {6, 6}, {6, 9}, {1, 9}, {4, 1}, {8, 1}, {1, 5}, {9, 6}}) {
            s.fill(post[0], 6, post[1], post[0], 8, post[1], OAK_LOG + "[axis=y]");
        }
        s.set(6, 7, 1, PANE_X).set(2, 7, 1, PANE_X).set(10, 7, 1, PANE_X);
        s.set(1, 7, 3, PANE_Z).set(1, 7, 7, PANE_Z).set(11, 7, 2, PANE_Z);
        s.set(3, 7, 9, PANE_X).set(4, 7, 9, PANE_X).set(7, 7, 6, PANE_X).set(10, 7, 6, PANE_X).set(6, 7, 8, PANE_Z);
        // The back wing's roof with its gables east and west, then the front wing's roof, which wins where it stands higher.
        s.gableRoof(0, 12, 0, 7, 9, DARK_STAIRS, DARK_SLAB, WHITE_TERRACOTTA, 1, 11);
        for (int z = 3; z <= 10; z++) {
            int back = z <= 7 ? 9 + Math.min(z, 7 - z) : -1;
            for (int x = 0; x <= 7; x++) {
                int height = 9 + Math.min(x, 7 - x);
                if (height > back || (height == back && z > 4)) {
                    s.set(x, height, z, stairs(DARK_STAIRS, x <= 3 ? "east" : "west"));
                }
            }
        }
        for (int x = 2; x <= 5; x++) {
            s.fill(x, 10, 9, x, 9 + Math.min(x, 7 - x) - 1, 9, WHITE_TERRACOTTA);
        }
        s.set(3, 10, 9, OAK_LOG + "[axis=y]").set(4, 10, 9, OAK_LOG + "[axis=y]");
        innBeam(s, 5, OAK_LOG);
        innBeam(s, 9, OAK_LOG);
        // A chimney of stone up the east gable.
        s.fill(12, 0, 3, 12, 13, 4, STONE_BRICKS).fill(12, 0, 3, 12, 1, 4, COBBLE);
        // The counter with the innkeeper's post behind it; the gap at its west end lets the innkeeper out.
        s.fill(8, 1, 4, 10, 1, 4, "minecraft:stripped_spruce_log[axis=x]");
        s.set(9, 1, 2, WORK_POST);
        s.set(8, 1, 2, "minecraft:smoker[facing=south,lit=false]");
        s.set(7, 1, 2, BARREL).set(10, 1, 2, BARREL).set(10, 2, 2, BARREL).set(10, 1, 3, BARREL);
        // Three tables with benches.
        for (int[] table : new int[][]{{3, 8}, {3, 5}, {5, 2}}) {
            s.set(table[0], 1, table[1], FENCE).set(table[0], 2, table[1], "minecraft:oak_pressure_plate[powered=false]");
            s.set(table[0] - 1, 1, table[1], stairs(DARK_STAIRS, "east")).set(table[0] + 1, 1, table[1], stairs(DARK_STAIRS, "west"));
        }
        s.set(3, 4, 7, HANGING_LANTERN).set(5, 4, 4, HANGING_LANTERN).set(9, 4, 3, HANGING_LANTERN);
        // A ladder in the north-west corner up through the floor.
        s.set(2, 5, 2, Structure.AIR);
        for (int y = 1; y <= 6; y++) {
            s.set(2, y, 2, ladder("east"));
        }
        // Four beds for guests along the back wall, a room with a table in the front wing.
        for (int x : new int[]{4, 6, 8, 10}) {
            s.bed(x, 6, 3, BED, "north", 0, -1);
        }
        s.set(5, 6, 2, BARREL).set(7, 6, 2, LANTERN).set(9, 6, 2, chest("south"));
        s.set(3, 6, 7, FENCE).set(3, 7, 7, "minecraft:oak_pressure_plate[powered=false]");
        s.set(2, 6, 7, stairs(DARK_STAIRS, "east")).set(5, 6, 8, BARREL).set(2, 6, 8, chest("east"));
        s.set(2, 8, 5, wallTorch("east")).set(10, 8, 5, wallTorch("west")).set(4, 8, 8, wallTorch("north"));
        // Before the door: lamps, a sign post with a lantern on its arm and a bench.
        s.set(8, 3, 7, wallTorch("south")).set(10, 3, 7, wallTorch("south"));
        s.set(2, 3, 10, wallTorch("south")).set(5, 3, 10, wallTorch("south"));
        s.set(8, 1, 9, FENCE).set(8, 2, 9, FENCE).set(8, 3, 9, fence(false, false, false, true));
        s.set(7, 3, 9, fence(false, false, true, false)).set(7, 2, 9, HANGING_LANTERN);
        s.set(10, 1, 7, stairs(DARK_STAIRS, "north")).set(11, 1, 7, stairs(DARK_STAIRS, "north"));
        return s;
    }
}
