package io.github.sekerator.kingdomworld.generator;

import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.BARREL;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.BED;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.COBBLE;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.COBBLE_STAIRS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.DARK_SLAB;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.DARK_STAIRS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.DOOR;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.FENCE;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.HANGING_LANTERN;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.LANTERN;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.OAK_DOOR;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.OAK_LOG;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.OAK_PLANKS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.PANE_X;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.PANE_Z;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.ROOF_SLAB;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.ROOF_STAIRS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.SPRUCE_LOG;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.SPRUCE_PLANKS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.STONE_BRICKS;

import java.util.ArrayList;
import java.util.List;

/**
 * Houses of other shapes than the small and the large house, so that a street of a kingdom is not a row of the same box:
 * a cottage under a steep gable, a stone cottage under a hip roof, a house under a roof of one slope with its woodshed, a
 * tall town house with a jutting upper floor, a manor and a long house with dormers. All face south and stand on a
 * raised floor like the other houses.
 */
final class Houses {
    private static final String DARK_PLANKS = "minecraft:dark_oak_planks";
    private static final String WHITE = "minecraft:white_terracotta";
    private static final String SLATE_STAIRS = "minecraft:dark_prismarine_stairs";
    private static final String SLATE_SLAB = "minecraft:dark_prismarine_slab";
    private static final String OAK_SLAB = "minecraft:oak_slab";
    private static final String CRAFTING_TABLE = "minecraft:crafting_table";
    private static final String BOOKSHELF = "minecraft:bookshelf";
    private static final String FURNACE = "minecraft:furnace[facing=south,lit=false]";
    private static final String CHIMNEY_POT = "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]";

    private Houses() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(house("cottage", "minecraft:oak_door", 3, cottage()));
        list.add(house("stone_cottage", "minecraft:cobblestone", 4, stoneCottage()));
        list.add(house("lean_house", "minecraft:oak_log", 3, leanHouse()));
        list.add(house("town_house", "minecraft:white_terracotta", 4, townHouse()));
        list.add(house("manor_house", "minecraft:red_bed", 6, manorHouse()));
        list.add(house("long_house", "minecraft:spruce_door", 4, longHouse()));
        return list;
    }

    private static BuildingGenerator.Building house(String name, String icon, int doorX, Structure structure) {
        return new BuildingGenerator.Building(name, "housing", "house", icon, doorX, 0, structure.sizeZ - 1, structure, "house", 1, "");
    }

    private static String stairs(String block, String facing) {
        return block + "[facing=" + facing + ",half=bottom,shape=straight,waterlogged=false]";
    }

    private static String upsideDownStairs(String block, String facing) {
        return block + "[facing=" + facing + ",half=top,shape=straight,waterlogged=false]";
    }

    private static String slab(String block, String type) {
        return block + "[type=" + type + ",waterlogged=false]";
    }

    private static String wallTorch(String facing) {
        return "minecraft:wall_torch[facing=" + facing + "]";
    }

    private static String ladder(String facing) {
        return "minecraft:ladder[facing=" + facing + ",waterlogged=false]";
    }

    private static String chest(String facing) {
        return "minecraft:chest[facing=" + facing + ",type=single,waterlogged=false]";
    }

    /**
     * An open shutter: a trapdoor standing flat against the wall behind it, the wall on the side opposite to where it faces.
     */
    private static String shutter(String facing) {
        return "minecraft:spruce_trapdoor[facing=" + facing + ",half=top,open=true,powered=false,waterlogged=false]";
    }

    // ---------------------------------------------------------------- roofs

    /**
     * A steep gable roof whose ridge runs along z, the gable to the street: the first {@code steepRows} rows above the eaves
     * rise two blocks each (a full block under a stair), the rows above them one block; the ridge is a slab. The gable
     * walls are filled with {@code gable} under the roof where nothing stands yet.
     *
     * @param y      height of the eaves
     * @param wallZ1 the gable wall at the back, or -1 for none
     * @param wallZ2 the gable wall at the front, or -1 for none
     */
    private static void steepGableZ(Structure s, int x1, int x2, int z1, int z2, int y, int steepRows, String stairs, String block,
                                    String slab, String gable, int wallZ1, int wallZ2) {
        int[] lowest = new int[x2 - x1 + 1];
        int height = y;
        for (int row = 0; x1 + row <= x2 - row; row++) {
            boolean steep = row > 0 && row <= steepRows;
            if (row > 0) {
                height += steep ? 2 : 1;
            }
            int west = x1 + row;
            int east = x2 - row;
            if (steep) {
                s.fill(west, height - 1, z1, west, height - 1, z2, block).fill(east, height - 1, z1, east, height - 1, z2, block);
            }
            if (west < east) {
                s.fill(west, height, z1, west, height, z2, stairs + "[facing=east,half=bottom,shape=straight,waterlogged=false]");
                s.fill(east, height, z1, east, height, z2, stairs + "[facing=west,half=bottom,shape=straight,waterlogged=false]");
            } else {
                s.fill(west, height, z1, west, height, z2, slab(slab, "bottom"));
            }
            lowest[west - x1] = steep ? height - 1 : height;
            lowest[east - x1] = lowest[west - x1];
        }
        for (int wall : new int[]{wallZ1, wallZ2}) {
            if (wall < 0) {
                continue;
            }
            for (int x = x1 + 1; x < x2; x++) {
                for (int h = y; h < lowest[x - x1]; h++) {
                    if (!s.solid(x, h, wall)) {
                        s.set(x, h, wall, gable);
                    }
                }
            }
        }
    }

    /**
     * A roof of one slope rising half a block per row from the back eave ({@code z1}) to the front eave ({@code z2}): bottom
     * and top slabs in turn.
     *
     * @param y height of the back eave
     */
    private static void leanRoof(Structure s, int x1, int x2, int z1, int z2, int y, String slab) {
        for (int z = z1; z <= z2; z++) {
            int row = z - z1;
            s.fill(x1, y + row / 2, z, x2, y + row / 2, z, slab(slab, row % 2 == 0 ? "bottom" : "top"));
        }
    }

    /**
     * The highest block of a wall under a roof of one slope, in the given row counted from its back eave: under a top slab the
     * wall takes the place of the slab, under a bottom slab it stops below it.
     */
    private static int leanWallTop(int y, int row) {
        return row % 2 == 1 ? y + row / 2 : y + row / 2 - 1;
    }

    /**
     * A small gabled window in the front slope of a roof whose ridge runs along x: a pane at {@code (x, y, z)} between two
     * cheeks, a little roof of stairs over it running back to the ridge, closed with a slab.
     */
    private static void dormer(Structure s, int x, int y, int z, String stairs, String slab, String wall) {
        s.set(x - 1, y, z, wall).set(x + 1, y, z, wall).set(x, y, z, PANE_X).set(x, y + 1, z, wall);
        s.fill(x - 1, y + 1, z - 1, x - 1, y + 1, z, stairs(stairs, "east"));
        s.fill(x + 1, y + 1, z - 1, x + 1, y + 1, z, stairs(stairs, "west"));
        s.fill(x, y + 2, z - 1, x, y + 2, z, slab(slab, "bottom"));
        s.set(x, y + 1, z - 1, Structure.AIR);
    }

    // ---------------------------------------------------------------- the houses

    /**
     * A narrow cottage of planks on a stone footing whose steep gable looks at the street, a stone chimney rising at the
     * back above the ridge, a hearth inside and a tiny window beside the door; one bed.
     */
    static Structure cottage() {
        Structure s = new Structure(7, 12, 9);
        s.fill(1, 0, 1, 5, 0, 7, COBBLE).fill(2, 0, 2, 4, 0, 6, SPRUCE_PLANKS);
        s.set(3, 0, 8, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 5, 1, 7, COBBLE).walls(1, 2, 1, 5, 4, 7, OAK_PLANKS);
        s.corners(1, 1, 1, 5, 4, 7, SPRUCE_LOG);
        steepGableZ(s, 0, 6, 1, 8, 4, 3, DARK_STAIRS, DARK_PLANKS, DARK_SLAB, OAK_PLANKS, 1, 7);
        // The chimney stands against the back gable and ends above the ridge.
        s.fill(3, 0, 0, 3, 10, 0, COBBLE).set(3, 11, 0, CHIMNEY_POT);
        s.door(3, 1, 7, OAK_DOOR, "north", "left");
        s.set(2, 2, 7, PANE_X).set(3, 6, 7, PANE_X);
        s.set(1, 2, 4, PANE_Z).set(5, 2, 4, PANE_Z);
        s.bed(2, 1, 3, BED, "north", 0, -1);
        s.set(3, 1, 2, FURNACE).set(4, 1, 2, BARREL).set(4, 1, 3, CRAFTING_TABLE);
        s.set(2, 3, 5, wallTorch("east")).set(4, 3, 5, wallTorch("west"));
        s.set(2, 3, 8, wallTorch("south")).set(4, 3, 8, wallTorch("south"));
        return s;
    }

    /**
     * A stone cottage: low walls of rubble under a hip roof, a small porch roof on two posts over the door and shutters at
     * the windows; two beds.
     */
    static Structure stoneCottage() {
        Structure s = new Structure(9, 8, 9);
        s.fill(1, 0, 1, 7, 0, 6, COBBLE).fill(2, 0, 2, 6, 0, 5, OAK_PLANKS);
        s.fill(3, 0, 7, 5, 0, 7, STONE_BRICKS).set(3, 0, 8, STONE_BRICKS).set(5, 0, 8, STONE_BRICKS);
        s.set(4, 0, 8, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 7, 3, 6, COBBLE).corners(1, 1, 1, 7, 3, 6, OAK_LOG);
        s.beam(1, 1, 7, 6, 4, OAK_LOG);
        s.hipRoof(0, 8, 0, 7, 4, ROOF_STAIRS, ROOF_SLAB);
        // The porch: a roof of one row of stairs on two posts, tucked under the eave.
        s.fill(3, 1, 8, 3, 2, 8, FENCE).fill(5, 1, 8, 5, 2, 8, FENCE);
        s.fill(3, 3, 7, 5, 3, 7, slab(ROOF_SLAB, "top"));
        s.fill(3, 3, 8, 5, 3, 8, stairs(ROOF_STAIRS, "north"));
        s.door(4, 1, 6, DOOR, "north", "left");
        // Windows with their shutters open against the wall.
        s.set(2, 2, 6, PANE_X).set(6, 2, 6, PANE_X).set(1, 2, 7, shutter("south")).set(7, 2, 7, shutter("south"));
        s.set(1, 2, 3, PANE_Z).set(0, 2, 2, shutter("west")).set(0, 2, 4, shutter("west"));
        s.set(7, 2, 3, PANE_Z).set(8, 2, 2, shutter("east")).set(8, 2, 4, shutter("east"));
        s.set(4, 2, 1, PANE_X).set(3, 2, 0, shutter("north")).set(5, 2, 0, shutter("north"));
        s.bed(2, 1, 3, "minecraft:blue_bed", "north", 0, -1).bed(3, 1, 3, "minecraft:blue_bed", "north", 0, -1);
        s.set(5, 1, 2, BOOKSHELF).set(6, 1, 2, BARREL).set(6, 1, 3, CRAFTING_TABLE).set(6, 2, 3, LANTERN);
        s.set(2, 3, 5, wallTorch("east")).set(6, 3, 5, wallTorch("west"));
        s.set(3, 2, 7, wallTorch("south")).set(5, 2, 7, wallTorch("south"));
        return s;
    }

    /**
     * A house under a roof of one slope that falls from a tall front to a low back, and beside it an open woodshed on posts
     * under a lower roof of the same slope, with piled logs, a chopping block and a barrel; two beds.
     */
    static Structure leanHouse() {
        Structure s = new Structure(11, 9, 9);
        s.fill(0, 0, 1, 6, 0, 7, COBBLE).fill(1, 0, 2, 5, 0, 6, SPRUCE_PLANKS);
        s.fill(7, 0, 1, 10, 0, 7, COBBLE);
        s.set(3, 0, 8, stairs(COBBLE_STAIRS, "north"));
        leanRoof(s, 0, 6, 0, 8, 4, OAK_SLAB);
        leanRoof(s, 7, 10, 0, 8, 3, OAK_SLAB);
        // The walls follow the slope: low at the back, tall at the front, a course of stone at the foot.
        for (int z = 1; z <= 7; z++) {
            int top = leanWallTop(4, z);
            s.fill(0, 1, z, 0, top, z, SPRUCE_PLANKS).fill(6, 1, z, 6, top, z, SPRUCE_PLANKS);
        }
        s.fill(1, 1, 1, 5, leanWallTop(4, 1), 1, SPRUCE_PLANKS).fill(1, 1, 7, 5, leanWallTop(4, 7), 7, SPRUCE_PLANKS);
        s.walls(0, 1, 1, 6, 1, 7, COBBLE);
        s.corners(0, 1, 1, 6, leanWallTop(4, 1), 7, SPRUCE_LOG);
        s.fill(0, 1, 7, 0, leanWallTop(4, 7), 7, SPRUCE_LOG + "[axis=y]").fill(6, 1, 7, 6, leanWallTop(4, 7), 7, SPRUCE_LOG + "[axis=y]");
        s.door(3, 1, 7, DOOR, "north", "left");
        s.set(1, 2, 7, PANE_X).set(5, 2, 7, PANE_X).set(2, 5, 7, PANE_X).set(4, 5, 7, PANE_X);
        s.set(0, 2, 4, PANE_Z).set(6, 2, 4, PANE_Z).set(3, 2, 1, PANE_X);
        // The woodshed: posts at its open side, logs piled at the back.
        for (int z : new int[]{2, 4, 6}) {
            s.fill(10, 1, z, 10, leanWallTop(3, z), z, OAK_LOG + "[axis=y]");
        }
        s.fill(7, 1, 1, 9, 2, 1, OAK_LOG + "[axis=x]").fill(7, 1, 2, 9, 1, 2, OAK_LOG + "[axis=x]");
        s.set(9, 1, 5, OAK_LOG + "[axis=y]").set(8, 1, 6, BARREL);
        s.bed(1, 1, 3, BED, "north", 0, -1).bed(5, 1, 3, BED, "north", 0, -1);
        s.set(2, 1, 2, CRAFTING_TABLE).set(3, 1, 2, FURNACE).set(4, 1, 2, BARREL);
        s.set(1, 3, 5, wallTorch("east")).set(5, 3, 5, wallTorch("west")).set(3, 5, 4, HANGING_LANTERN);
        s.set(2, 3, 8, wallTorch("south")).set(4, 3, 8, wallTorch("south"));
        return s;
    }

    /**
     * A tall narrow town house of three levels: a ground floor of stone, an upper floor of timber frame with white infill
     * that juts one block over the street on brackets, and an attic under a steep gable that looks at the street; a ladder
     * runs up through all of them. Two beds above, one in the attic.
     */
    static Structure townHouse() {
        Structure s = new Structure(9, 15, 11);
        s.fill(1, 0, 1, 7, 0, 9, COBBLE).fill(2, 0, 2, 6, 0, 8, SPRUCE_PLANKS);
        s.set(4, 0, 10, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 7, 3, 9, STONE_BRICKS);
        for (int[] corner : new int[][]{{1, 1}, {7, 1}, {1, 9}, {7, 9}}) {
            s.fill(corner[0], 1, corner[1], corner[0], 3, corner[1], COBBLE);
        }
        // The upper floor lies one row further to the front than the ground floor, on brackets.
        s.beam(1, 1, 7, 10, 4, OAK_LOG).fill(2, 4, 2, 6, 4, 9, SPRUCE_PLANKS);
        for (int x : new int[]{1, 4, 7}) {
            s.set(x, 3, 10, upsideDownStairs(DARK_STAIRS, "north"));
        }
        s.walls(1, 5, 1, 7, 7, 10, WHITE).corners(1, 5, 1, 7, 7, 10, OAK_LOG);
        s.fill(4, 5, 1, 4, 7, 1, OAK_LOG + "[axis=y]").fill(4, 5, 10, 4, 7, 10, OAK_LOG + "[axis=y]");
        s.fill(1, 5, 5, 1, 7, 5, OAK_LOG + "[axis=y]").fill(7, 5, 5, 7, 7, 5, OAK_LOG + "[axis=y]");
        s.beam(1, 1, 7, 10, 8, OAK_LOG).fill(2, 8, 2, 6, 8, 9, SPRUCE_PLANKS);
        steepGableZ(s, 0, 8, 0, 10, 8, 2, DARK_STAIRS, DARK_PLANKS, DARK_SLAB, WHITE, 1, 10);
        s.fill(4, 9, 10, 4, 13, 10, OAK_LOG + "[axis=y]").fill(4, 9, 1, 4, 13, 1, OAK_LOG + "[axis=y]");
        s.set(3, 10, 10, PANE_X).set(5, 10, 10, PANE_X).set(3, 10, 1, PANE_X).set(5, 10, 1, PANE_X);
        s.door(4, 1, 9, DOOR, "north", "left");
        s.set(2, 2, 9, PANE_X).set(6, 2, 9, PANE_X).set(1, 2, 5, PANE_Z).set(7, 2, 5, PANE_Z).set(3, 2, 1, PANE_X);
        for (int x : new int[]{2, 3, 5, 6}) {
            s.set(x, 6, 10, PANE_X);
        }
        s.set(1, 6, 3, PANE_Z).set(1, 6, 7, PANE_Z).set(7, 6, 7, PANE_Z).set(2, 6, 1, PANE_X).set(6, 6, 1, PANE_X);
        // A ladder along the east wall through both floors.
        for (int y = 1; y <= 9; y++) {
            s.set(6, y, 2, ladder("west"));
        }
        s.set(2, 1, 2, CRAFTING_TABLE).set(3, 1, 2, FURNACE).set(4, 1, 2, BARREL).set(2, 1, 8, BARREL);
        s.set(4, 1, 6, FENCE).set(4, 2, 6, "minecraft:oak_pressure_plate[powered=false]");
        s.set(3, 1, 6, stairs(DARK_STAIRS, "east")).set(5, 1, 6, stairs(DARK_STAIRS, "west"));
        s.set(2, 3, 6, wallTorch("east")).set(6, 3, 6, wallTorch("west"));
        s.bed(2, 5, 3, BED, "north", 0, -1).bed(3, 5, 3, BED, "north", 0, -1);
        s.set(2, 5, 9, chest("east")).set(6, 5, 9, BOOKSHELF);
        s.set(2, 7, 6, wallTorch("east")).set(6, 7, 6, wallTorch("west"));
        s.bed(4, 9, 6, "minecraft:white_bed", "north", 0, -1);
        s.set(4, 9, 8, LANTERN);
        s.set(3, 2, 10, wallTorch("south")).set(5, 2, 10, wallTorch("south"));
        return s;
    }

    /**
     * A manor of two floors: stone below, planks above, under a hip roof of slate with a chimney at each end. The middle
     * bay with the door stands one block forward under a small gable of its own; glass on both floors, four beds above.
     */
    static Structure manorHouse() {
        Structure s = new Structure(13, 14, 11);
        s.fill(1, 0, 1, 11, 0, 8, COBBLE).fill(2, 0, 2, 10, 0, 7, SPRUCE_PLANKS);
        s.fill(4, 0, 9, 8, 0, 9, COBBLE).fill(5, 0, 8, 7, 0, 8, SPRUCE_PLANKS);
        s.set(6, 0, 10, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 11, 3, 8, STONE_BRICKS).walls(4, 1, 8, 8, 3, 9, STONE_BRICKS);
        s.beam(1, 1, 11, 8, 4, OAK_LOG).fill(2, 4, 2, 10, 4, 7, SPRUCE_PLANKS);
        s.fill(4, 4, 9, 8, 4, 9, OAK_LOG + "[axis=x]");
        s.walls(1, 5, 1, 11, 7, 8, OAK_PLANKS).walls(4, 5, 8, 8, 7, 9, OAK_PLANKS);
        // The bay opens into the rooms behind it on both floors.
        s.fill(5, 1, 8, 7, 7, 8, Structure.AIR).fill(5, 4, 8, 7, 4, 8, SPRUCE_PLANKS);
        s.corners(1, 1, 1, 11, 7, 8, SPRUCE_LOG);
        s.fill(4, 1, 9, 4, 7, 9, SPRUCE_LOG + "[axis=y]").fill(8, 1, 9, 8, 7, 9, SPRUCE_LOG + "[axis=y]");
        s.beam(1, 1, 11, 8, 8, OAK_LOG);
        s.hipRoof(0, 12, 0, 9, 8, SLATE_STAIRS, SLATE_SLAB);
        s.gableRoofZ(4, 8, 7, 10, 8, SLATE_STAIRS, SLATE_SLAB, WHITE, -1, 9);
        s.fill(5, 8, 9, 7, 8, 9, WHITE).set(6, 9, 9, "minecraft:glass_pane[east=false,west=false,north=false,south=false,waterlogged=false]");
        // A chimney at each end, against the side walls.
        s.fill(0, 0, 4, 0, 12, 5, COBBLE).fill(12, 0, 4, 12, 12, 5, COBBLE);
        s.set(0, 13, 4, CHIMNEY_POT).set(12, 13, 5, CHIMNEY_POT);
        s.door(6, 1, 9, DOOR, "north", "left");
        for (int y : new int[]{2, 6}) {
            for (int x : new int[]{2, 3, 9, 10}) {
                s.set(x, y, 8, PANE_X);
            }
            for (int x : new int[]{3, 6, 9}) {
                s.set(x, y, 1, PANE_X);
            }
            s.set(1, y, 3, PANE_Z).set(1, y, 6, PANE_Z).set(11, y, 3, PANE_Z).set(11, y, 6, PANE_Z);
        }
        s.fill(5, 6, 9, 7, 6, 9, PANE_X);
        // A ladder in the north-east corner.
        for (int y = 1; y <= 5; y++) {
            s.set(10, y, 2, ladder("west"));
        }
        s.set(2, 1, 2, BOOKSHELF).set(3, 1, 2, BOOKSHELF).set(4, 1, 2, CRAFTING_TABLE).set(5, 1, 2, FURNACE).set(7, 1, 2, BARREL);
        s.set(5, 1, 5, FENCE).set(5, 2, 5, "minecraft:oak_pressure_plate[powered=false]");
        s.set(4, 1, 5, stairs(DARK_STAIRS, "east")).set(6, 1, 5, stairs(DARK_STAIRS, "west"));
        s.set(4, 3, 4, HANGING_LANTERN).set(8, 3, 4, HANGING_LANTERN).set(6, 3, 7, HANGING_LANTERN);
        s.bed(2, 5, 3, BED, "north", 0, -1).bed(3, 5, 3, BED, "north", 0, -1);
        s.bed(6, 5, 3, BED, "north", 0, -1).bed(7, 5, 3, BED, "north", 0, -1);
        s.set(2, 5, 7, chest("east")).set(10, 5, 7, BOOKSHELF);
        s.set(2, 7, 5, wallTorch("east")).set(10, 7, 5, wallTorch("west"));
        s.set(5, 2, 10, wallTorch("south")).set(7, 2, 10, wallTorch("south"));
        return s;
    }

    /**
     * A long low house of rubble with timber posts: one floor, a loft under the roof reached by a ladder with three beds, two
     * dormers in the front slope and the door off the middle.
     */
    static Structure longHouse() {
        Structure s = new Structure(13, 9, 9);
        s.fill(1, 0, 1, 11, 0, 7, COBBLE).fill(2, 0, 2, 10, 0, 6, SPRUCE_PLANKS);
        s.set(4, 0, 8, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 11, 3, 7, COBBLE).corners(1, 1, 1, 11, 3, 7, OAK_LOG);
        s.fill(6, 1, 1, 6, 3, 1, OAK_LOG + "[axis=y]").fill(6, 1, 7, 6, 3, 7, OAK_LOG + "[axis=y]");
        s.beam(1, 1, 11, 7, 4, OAK_LOG).fill(2, 4, 2, 10, 4, 6, SPRUCE_PLANKS);
        s.gableRoof(0, 12, 0, 8, 4, ROOF_STAIRS, ROOF_SLAB, OAK_PLANKS, 1, 11);
        dormer(s, 3, 6, 6, ROOF_STAIRS, ROOF_SLAB, OAK_PLANKS);
        dormer(s, 9, 6, 6, ROOF_STAIRS, ROOF_SLAB, OAK_PLANKS);
        s.door(4, 1, 7, DOOR, "north", "left");
        s.set(2, 2, 7, PANE_X).set(8, 2, 7, PANE_X).set(9, 2, 7, PANE_X);
        s.set(3, 2, 1, PANE_X).set(9, 2, 1, PANE_X).set(11, 2, 4, PANE_Z).set(1, 6, 4, PANE_Z).set(11, 6, 4, PANE_Z);
        // A ladder at the west wall up into the loft.
        for (int y = 1; y <= 5; y++) {
            s.set(2, y, 4, ladder("east"));
        }
        s.set(10, 1, 2, CRAFTING_TABLE).set(9, 1, 2, BARREL).set(8, 1, 2, FURNACE).set(2, 1, 2, BARREL);
        s.set(7, 1, 5, FENCE).set(7, 2, 5, "minecraft:oak_pressure_plate[powered=false]");
        s.set(6, 1, 5, stairs(DARK_STAIRS, "east")).set(8, 1, 5, stairs(DARK_STAIRS, "west"));
        s.set(5, 3, 2, wallTorch("south")).set(10, 3, 4, wallTorch("west"));
        for (int x : new int[]{5, 7, 9}) {
            s.bed(x, 5, 4, "minecraft:green_bed", "north", 0, -1);
        }
        s.set(10, 5, 4, chest("west"));
        s.set(2, 6, 5, wallTorch("east")).set(10, 6, 3, wallTorch("west"));
        s.set(3, 3, 8, wallTorch("south")).set(5, 3, 8, wallTorch("south"));
        return s;
    }
}
