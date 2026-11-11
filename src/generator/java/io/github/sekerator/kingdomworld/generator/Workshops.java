package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.COBBLE;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.COBBLE_STAIRS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.DARK_SLAB;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.DARK_STAIRS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.DOOR;
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
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.STONE_BRICK_STAIRS;

/**
 * Workshops of the trades and a fountain for the squares: a smithy with its open forge, a carpentry with a timber yard, a
 * weavery of two floors, a bakery with its oven and a basin of stone. Each looks like its trade from the street, so that a
 * town of several workshops does not repeat one box.
 */
final class Workshops {
    private static final String CHEST = "minecraft:chest[facing=south,type=single,waterlogged=false]";
    private static final String WORK_POST = "kingdomworld:work_post";
    private static final String STONE_BRICK_SLAB = "minecraft:stone_brick_slab";
    private static final String STONE_WALL_POST = "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]";

    private Workshops() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("smithy", "production", "workshop", "minecraft:anvil", 3, 0, 8, smithy(),
                "workshop", 1, ""));
        list.add(new BuildingGenerator.Building("carpentry", "production", "workshop", "minecraft:crafting_table", 3, 0, 10,
                carpentry(), "workshop", 1, ""));
        list.add(new BuildingGenerator.Building("weavery", "production", "workshop", "minecraft:loom", 4, 0, 8, weavery(),
                "workshop", 1, ""));
        list.add(new BuildingGenerator.Building("bakery", "production", "workshop", "minecraft:bread", 3, 0, 8, bakery(),
                "workshop", 1, ""));
        list.add(new BuildingGenerator.Building("fountain", "decoration", "well", "minecraft:stone_brick_slab", 3, 1, 6, fountain(),
                "well", 1, ""));
        return list;
    }

    private static String stairs(String block, String facing) {
        return block + "[facing=" + facing + ",half=bottom,shape=straight,waterlogged=false]";
    }

    private static String stairs(String block, String facing, String shape) {
        return block + "[facing=" + facing + ",half=bottom,shape=" + shape + ",waterlogged=false]";
    }

    private static String slab(String block, String type) {
        return block + "[type=" + type + ",waterlogged=false]";
    }

    private static String wallTorch(String facing) {
        return "minecraft:wall_torch[facing=" + facing + "]";
    }

    private static String fence(String wood, boolean north, boolean east, boolean south, boolean west) {
        return "minecraft:" + wood + "_fence[east=" + east + ",north=" + north + ",south=" + south + ",waterlogged=false,west=" + west + "]";
    }

    /**
     * A steep gable roof whose ridge runs along z, so that the gable looks at the street: every row of stairs stands on a
     * full block, so the roof climbs two blocks for each block inwards; the gable ends are filled with {@code gable}.
     *
     * @param y height of the eaves
     */
    private static void steepRoofZ(Structure s, int x1, int x2, int z1, int z2, int y, String stairs, String block, String gable,
                                   int wallZ1, int wallZ2) {
        int row = 0;
        while (x1 + row < x2 - row) {
            int west = x1 + row;
            int east = x2 - row;
            int top = y + 2 * row;
            if (row > 0) {
                s.fill(west, top - 1, z1, west, top - 1, z2, block);
                s.fill(east, top - 1, z1, east, top - 1, z2, block);
            }
            s.fill(west, top, z1, west, top, z2, stairs + "[facing=east,half=bottom,shape=straight,waterlogged=false]");
            s.fill(east, top, z1, east, top, z2, stairs + "[facing=west,half=bottom,shape=straight,waterlogged=false]");
            if (row > 0 && east - west > 1) {
                for (int wall : new int[]{wallZ1, wallZ2}) {
                    if (wall >= 0) {
                        s.fill(west + 1, top - 1, wall, east - 1, top, wall, gable);
                    }
                }
            }
            row++;
        }
    }

    // ---------------------------------------------------------------- the buildings

    /**
     * A smithy of stone: a hall under a roof whose gable looks at the street, with the smithing table, a furnace and the
     * smith's post, and beside it an open forge under a lean-to roof on posts, with a blast furnace at the foot of a great
     * square chimney, an anvil, a grindstone and a cauldron to quench in.
     */
    static Structure smithy() {
        Structure s = new Structure(11, 12, 9);
        s.fill(1, 0, 1, 10, 0, 7, COBBLE).fill(2, 0, 2, 4, 0, 6, STONE_BRICKS).fill(6, 0, 2, 9, 0, 7, "minecraft:smooth_stone");
        s.set(3, 0, 8, stairs(COBBLE_STAIRS, "north"));
        // The hall: a plinth of cobblestone, walls of stone bricks, a doorway into the forge.
        s.walls(1, 1, 1, 5, 1, 7, COBBLE).walls(1, 2, 1, 5, 4, 7, STONE_BRICKS);
        s.fill(1, 1, 1, 1, 4, 1, COBBLE).fill(5, 1, 1, 5, 4, 1, COBBLE).fill(1, 1, 7, 1, 4, 7, COBBLE).fill(5, 1, 7, 5, 4, 7, COBBLE);
        s.fill(5, 1, 4, 5, 2, 4, Structure.AIR);
        s.set(1, 2, 3, PANE_Z).set(1, 2, 5, PANE_Z).set(3, 2, 1, PANE_X).set(3, 3, 1, PANE_X);
        s.door(3, 1, 7, DOOR, "north", "left");
        s.beam(1, 1, 5, 7, 5, SPRUCE_LOG);
        s.gableRoofZ(0, 6, 0, 8, 5, DARK_STAIRS, DARK_SLAB, SPRUCE_PLANKS, 1, 7);
        s.set(3, 6, 7, "minecraft:spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]");
        // The forge: a back wall, the chimney and posts carrying a lean-to roof that falls to the east.
        s.fill(6, 1, 1, 10, 2, 1, COBBLE);
        s.fill(8, 1, 1, 9, 10, 2, STONE_BRICKS);
        for (int y = 1; y <= 10; y += 3) {
            s.set(8, y, 2, COBBLE).set(9, y, 1, COBBLE);
        }
        s.fill(8, 11, 1, 9, 11, 2, slab(STONE_BRICK_SLAB, "bottom"));
        for (int z : new int[]{1, 4, 7}) {
            s.fill(10, 1, z, 10, 2, z, SPRUCE_LOG + "[axis=y]");
        }
        s.fill(10, 3, 1, 10, 3, 7, SPRUCE_LOG + "[axis=z]");
        s.fill(6, 4, 0, 6, 4, 8, slab(ROOF_SLAB, "bottom"));
        s.fill(6, 3, 1, 6, 3, 7, SPRUCE_PLANKS);
        s.fill(7, 4, 0, 8, 4, 8, stairs(ROOF_STAIRS, "west"));
        s.fill(9, 3, 0, 10, 3, 8, stairs(ROOF_STAIRS, "west"));
        s.fill(7, 3, 1, 8, 3, 7, SPRUCE_PLANKS);
        // The chimney stands through the lean-to roof.
        s.fill(8, 3, 1, 9, 4, 2, STONE_BRICKS);
        s.set(8, 3, 1, COBBLE);
        s.set(8, 1, 3, "minecraft:blast_furnace[facing=south,lit=false]");
        s.set(9, 1, 3, "minecraft:cauldron");
        s.set(7, 1, 5, "minecraft:anvil[facing=south]");
        s.set(9, 1, 6, "minecraft:grindstone[face=floor,facing=south]");
        s.set(6, 1, 2, "minecraft:barrel[facing=up,open=false]");
        s.set(7, 2, 4, HANGING_LANTERN);
        // Inside the hall.
        s.set(4, 1, 2, "minecraft:smithing_table");
        s.set(3, 1, 2, "minecraft:furnace[facing=south,lit=false]");
        s.set(2, 1, 4, WORK_POST);
        s.set(2, 1, 3, CHEST);
        s.set(2, 1, 2, "minecraft:barrel[facing=up,open=false]");
        s.set(4, 3, 3, wallTorch("west")).set(2, 3, 5, wallTorch("east"));
        s.set(2, 3, 8, wallTorch("south")).set(4, 3, 8, wallTorch("south"));
        return s;
    }

    /**
     * A carpentry: a timber workshop under a steep roof whose gable looks at the street, open to it in a wide bay, with the
     * carpenter's tables inside and a fenced yard beside it where logs and planks lie stacked.
     */
    static Structure carpentry() {
        Structure s = new Structure(11, 11, 11);
        s.fill(1, 0, 1, 6, 0, 9, COBBLE).fill(2, 0, 2, 5, 0, 9, SPRUCE_PLANKS);
        s.fill(2, 0, 10, 5, 0, 10, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 6, 1, 9, COBBLE).walls(1, 2, 1, 6, 3, 9, OAK_PLANKS);
        s.corners(1, 1, 1, 6, 3, 9, SPRUCE_LOG);
        s.fill(1, 1, 5, 1, 3, 5, SPRUCE_LOG + "[axis=y]").fill(6, 1, 3, 6, 3, 3, SPRUCE_LOG + "[axis=y]");
        s.fill(6, 1, 7, 6, 3, 7, SPRUCE_LOG + "[axis=y]");
        // The front stands open across the whole bay.
        s.fill(2, 1, 9, 5, 3, 9, Structure.AIR);
        s.set(1, 2, 3, PANE_Z).set(1, 2, 7, PANE_Z).set(3, 2, 1, PANE_X).set(4, 2, 1, PANE_X);
        // A doorway into the yard.
        s.fill(6, 1, 5, 6, 2, 5, Structure.AIR);
        s.beam(1, 1, 6, 9, 4, SPRUCE_LOG);
        s.fill(2, 4, 3, 5, 4, 3, SPRUCE_LOG + "[axis=x]").fill(2, 4, 6, 5, 4, 6, SPRUCE_LOG + "[axis=x]");
        steepRoofZ(s, 0, 7, 0, 10, 4, "minecraft:spruce_stairs", SPRUCE_PLANKS, OAK_PLANKS, 1, 9);
        // A loft door and a hoist beam in the front gable.
        s.set(3, 6, 9, "minecraft:spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]");
        s.set(4, 6, 9, "minecraft:spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]");
        s.set(3, 8, 9, PANE_X).set(4, 8, 9, PANE_X);
        s.set(3, 7, 10, SPRUCE_LOG + "[axis=z]");
        s.set(3, 6, 10, "minecraft:chain[axis=y,waterlogged=false]");
        // The yard: trodden earth inside a fence, a gate to the street, stacked logs and planks.
        s.fill(7, 0, 1, 10, 0, 9, "minecraft:coarse_dirt");
        s.fill(7, 1, 1, 9, 1, 1, fence("oak", false, true, false, true));
        s.set(10, 1, 1, fence("oak", false, false, true, true));
        s.fill(10, 1, 2, 10, 1, 8, fence("oak", true, false, true, false));
        s.set(10, 1, 9, fence("oak", true, false, false, true));
        s.set(9, 1, 9, fence("oak", false, true, false, true));
        s.set(7, 1, 9, fence("oak", false, true, false, true));
        s.set(8, 1, 9, "minecraft:oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]");
        s.set(8, 0, 10, stairs(COBBLE_STAIRS, "north"));
        s.fill(8, 1, 2, 9, 2, 4, SPRUCE_LOG + "[axis=z]");
        s.fill(9, 3, 2, 9, 3, 4, OAK_LOG + "[axis=z]");
        s.fill(8, 1, 6, 9, 1, 7, OAK_PLANKS).set(9, 2, 6, OAK_PLANKS).set(9, 2, 7, slab("minecraft:oak_slab", "bottom"));
        s.set(8, 2, 6, slab("minecraft:oak_slab", "bottom"));
        s.set(7, 1, 7, "minecraft:stripped_oak_log[axis=y]");
        // Inside.
        s.set(2, 1, 2, "minecraft:crafting_table");
        s.set(3, 1, 2, "minecraft:stonecutter[facing=south]");
        s.set(5, 1, 2, "minecraft:fletching_table");
        s.set(4, 1, 2, "minecraft:barrel[facing=up,open=false]");
        s.set(2, 1, 6, WORK_POST);
        s.set(2, 1, 7, CHEST);
        s.set(5, 1, 7, "minecraft:stripped_spruce_log[axis=x]");
        s.set(3, 3, 3, HANGING_LANTERN).set(4, 3, 6, HANGING_LANTERN);
        s.set(1, 3, 10, wallTorch("south")).set(6, 3, 10, wallTorch("south"));
        return s;
    }

    /**
     * A weavery: a timber-framed house of a floor and a loft under a hip roof, the frame of logs filled with white plaster,
     * bales of coloured wool under an awning striped with carpets by the door, two looms and the weaver's post inside.
     */
    static Structure weavery() {
        Structure s = new Structure(9, 11, 9);
        String plaster = "minecraft:white_terracotta";
        s.fill(1, 0, 1, 7, 0, 7, COBBLE).fill(2, 0, 2, 6, 0, 5, SPRUCE_PLANKS);
        s.set(4, 0, 8, stairs(COBBLE_STAIRS, "north"));
        // The frame: a plinth of stone, plaster between logs, a beam at the loft floor and one under the roof.
        s.walls(1, 1, 1, 7, 1, 6, COBBLE).walls(1, 2, 1, 7, 3, 6, plaster).walls(1, 5, 1, 7, 6, 6, plaster);
        s.corners(1, 1, 1, 7, 6, 6, OAK_LOG);
        s.fill(4, 2, 1, 4, 6, 1, OAK_LOG + "[axis=y]");
        s.fill(1, 2, 4, 1, 6, 4, OAK_LOG + "[axis=y]").fill(7, 2, 4, 7, 6, 4, OAK_LOG + "[axis=y]");
        s.fill(2, 5, 6, 2, 6, 6, OAK_LOG + "[axis=y]").fill(6, 5, 6, 6, 6, 6, OAK_LOG + "[axis=y]");
        s.beam(1, 1, 7, 6, 4, OAK_LOG).beam(1, 1, 7, 6, 7, OAK_LOG);
        s.fill(2, 4, 2, 6, 4, 5, SPRUCE_PLANKS);
        s.set(1, 2, 2, PANE_Z).set(1, 2, 5, PANE_Z).set(7, 2, 5, PANE_Z);
        s.set(2, 2, 1, PANE_X).set(6, 2, 1, PANE_X).set(2, 2, 6, PANE_X).set(6, 2, 6, PANE_X);
        s.set(1, 5, 2, PANE_Z).set(7, 5, 2, PANE_Z).set(3, 5, 1, PANE_X).set(5, 5, 1, PANE_X).set(4, 5, 6, PANE_X);
        s.door(4, 1, 6, OAK_DOOR, "north", "left");
        s.hipRoof(0, 8, 0, 7, 7, DARK_STAIRS, DARK_SLAB);
        // The porch: an awning on posts striped with carpets, bales of wool under it.
        s.fill(1, 1, 7, 1, 2, 7, fence("oak", true, false, false, false));
        s.fill(7, 1, 7, 7, 2, 7, fence("oak", true, false, false, false));
        s.fill(1, 3, 7, 7, 3, 7, slab("minecraft:spruce_slab", "top"));
        String[] stripes = {"minecraft:red_carpet", "minecraft:yellow_carpet", "minecraft:blue_carpet"};
        for (int x = 1; x <= 7; x++) {
            s.set(x, 4, 7, stripes[(x - 1) % 3]);
        }
        s.set(5, 1, 7, "minecraft:red_wool").set(6, 1, 7, "minecraft:yellow_wool").set(6, 2, 7, "minecraft:blue_wool");
        s.set(2, 1, 7, "minecraft:blue_wool");
        s.set(3, 2, 7, wallTorch("south")).set(5, 2, 7, wallTorch("south"));
        // The weaving room.
        s.set(2, 1, 2, "minecraft:loom[facing=south]").set(3, 1, 2, "minecraft:loom[facing=south]");
        s.set(5, 1, 2, "minecraft:crafting_table");
        s.set(2, 1, 4, WORK_POST);
        s.set(2, 1, 5, CHEST);
        s.set(4, 1, 3, "minecraft:red_carpet").set(4, 1, 4, "minecraft:yellow_carpet").set(3, 1, 4, "minecraft:blue_carpet");
        s.set(3, 1, 3, "minecraft:red_carpet");
        s.set(4, 3, 2, wallTorch("south"));
        // A ladder up to the loft, where the wool is stored.
        s.set(6, 4, 3, Structure.AIR);
        for (int y = 1; y <= 5; y++) {
            s.set(6, y, 3, "minecraft:ladder[facing=west,waterlogged=false]");
        }
        s.set(2, 5, 2, "minecraft:white_wool").set(2, 5, 3, "minecraft:red_wool").set(3, 5, 2, "minecraft:yellow_wool");
        s.set(2, 6, 2, "minecraft:blue_wool");
        s.set(3, 5, 4, "minecraft:yellow_carpet").set(4, 5, 4, "minecraft:red_carpet").set(4, 5, 3, "minecraft:blue_carpet");
        s.set(5, 5, 5, LANTERN);
        return s;
    }

    /**
     * A bakery: a small house of stone below and timber above, a brick oven with its chimney built against the west end, and
     * in the front wall beside the door a serving hatch with a counter and a shutter; two smokers, flour and the baker's post
     * inside.
     */
    static Structure bakery() {
        Structure s = new Structure(9, 11, 9);
        String bricks = "minecraft:bricks";
        s.fill(2, 0, 1, 7, 0, 7, COBBLE).fill(3, 0, 2, 6, 0, 6, SPRUCE_PLANKS).fill(0, 0, 2, 1, 0, 6, COBBLE);
        s.set(3, 0, 8, stairs(COBBLE_STAIRS, "north"));
        s.walls(2, 1, 1, 7, 2, 7, STONE_BRICKS).walls(2, 3, 1, 7, 3, 7, OAK_PLANKS);
        s.fill(2, 3, 1, 2, 3, 7, STONE_BRICKS);
        s.corners(2, 1, 1, 7, 3, 7, SPRUCE_LOG);
        s.fill(2, 1, 1, 2, 3, 1, COBBLE).fill(2, 1, 7, 2, 3, 7, COBBLE);
        s.set(7, 2, 3, PANE_Z).set(7, 2, 5, PANE_Z).set(5, 2, 1, PANE_X);
        s.door(3, 1, 7, DOOR, "north", "left");
        // The serving hatch: a slab for a counter, the shutter propped up over it.
        s.fill(5, 2, 7, 6, 2, 7, slab("minecraft:spruce_slab", "bottom"));
        s.fill(5, 3, 7, 6, 3, 7, Structure.AIR);
        s.fill(5, 3, 8, 6, 3, 8, "minecraft:spruce_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]");
        s.beam(2, 1, 7, 7, 4, SPRUCE_LOG);
        s.gableRoof(2, 8, 0, 8, 4, DARK_STAIRS, DARK_SLAB, OAK_PLANKS, 2, 7);
        // The west gable is of stone, like the oven against it.
        for (int y = 5; y <= 8; y++) {
            for (int z = 1; z <= 7; z++) {
                if (s.get(2, y, z).equals(OAK_PLANKS)) {
                    s.set(2, y, z, STONE_BRICKS);
                }
            }
        }
        s.set(7, 6, 4, PANE_Z);
        // The oven: a vault of bricks with the chimney rising from its middle well above the ridge.
        s.fill(0, 1, 2, 1, 2, 6, bricks);
        s.set(0, 2, 2, stairs("minecraft:brick_stairs", "east")).set(0, 2, 6, stairs("minecraft:brick_stairs", "east"));
        s.fill(1, 3, 2, 1, 3, 2, slab("minecraft:brick_slab", "bottom")).fill(1, 3, 5, 1, 3, 6, slab("minecraft:brick_slab", "bottom"));
        s.set(0, 3, 5, slab("minecraft:brick_slab", "bottom"));
        s.fill(0, 1, 3, 1, 9, 4, bricks);
        s.fill(0, 10, 3, 1, 10, 4, slab("minecraft:brick_slab", "bottom"));
        s.set(0, 1, 4, "minecraft:iron_bars[east=false,north=true,south=true,waterlogged=false,west=false]");
        // Inside: the smokers against the back wall, flour and barrels, the baker's post behind the hatch.
        s.set(3, 1, 2, "minecraft:smoker[facing=south,lit=false]").set(4, 1, 2, "minecraft:smoker[facing=south,lit=false]");
        s.set(6, 1, 2, "minecraft:barrel[facing=up,open=false]");
        s.set(6, 1, 3, "minecraft:hay_block[axis=y]");
        s.set(6, 1, 5, WORK_POST);
        s.set(6, 1, 4, CHEST);
        s.set(3, 1, 4, "minecraft:crafting_table");
        s.set(4, 2, 2, LANTERN);
        s.set(3, 3, 5, wallTorch("east"));
        s.set(2, 3, 8, wallTorch("south")).set(7, 3, 8, wallTorch("south"));
        return s;
    }

    /**
     * A fountain for a square, level with the street: a round-cornered basin with a rim of stone brick slabs and stairs, a
     * pool floored with dark prismarine around a pillar that carries a lantern, and four low posts with lanterns at the
     * corners. The water is not built (templates leave liquids out); dry, the basin still looks right.
     */
    static Structure fountain() {
        Structure s = new Structure(7, 5, 7);
        s.fill(0, 0, 0, 6, 0, 6, STONE_BRICKS);
        s.fill(1, 0, 1, 5, 0, 5, "minecraft:smooth_stone");
        s.fill(2, 0, 2, 4, 0, 4, "minecraft:dark_prismarine");
        // The rim: slabs along the sides, stairs turned at the rounded corners.
        String slab = slab(STONE_BRICK_SLAB, "bottom");
        s.fill(2, 1, 1, 4, 1, 1, slab).fill(2, 1, 5, 4, 1, 5, slab).fill(1, 1, 2, 1, 1, 4, slab).fill(5, 1, 2, 5, 1, 4, slab);
        s.set(3, 1, 1, stairs(STONE_BRICK_STAIRS, "north")).set(3, 1, 5, stairs(STONE_BRICK_STAIRS, "south"));
        s.set(1, 1, 3, stairs(STONE_BRICK_STAIRS, "west")).set(5, 1, 3, stairs(STONE_BRICK_STAIRS, "east"));
        s.fill(2, 1, 2, 4, 1, 4, "minecraft:water[level=0]");
        // The pillar with its lantern.
        s.set(3, 1, 3, "minecraft:chiseled_stone_bricks");
        s.set(3, 2, 3, STONE_WALL_POST);
        s.set(3, 3, 3, slab(STONE_BRICK_SLAB, "top"));
        s.set(3, 4, 3, LANTERN);
        // Low posts at the corners; the paved corners inside them are free to walk across.
        for (int[] corner : new int[][]{{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
            s.set(corner[0], 1, corner[1], STONE_WALL_POST);
            s.set(corner[0], 2, corner[1], "minecraft:chain[axis=y,waterlogged=false]");
            s.set(corner[0], 3, corner[1], LANTERN);
        }
        return s;
    }
}
