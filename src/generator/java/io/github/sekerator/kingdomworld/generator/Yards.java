package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The yards where the workers of a kingdom gather in place of the posts that stood in the open on the square: the yard of
 * the builders with its shed, crane and half-built wall, and the yard of the carters with its stable, wagons and loading
 * dock. Both stand on the ground at the level of the street behind a fence with a gate; the work post of each, with a
 * chest beside it, stands under a roof where the workers meet.
 */
final class Yards {
    private static final String AIR = Structure.AIR;
    private static final String OAK_PLANKS = "minecraft:oak_planks";
    private static final String SPRUCE_PLANKS = "minecraft:spruce_planks";
    private static final String OAK_LOG = "minecraft:oak_log";
    private static final String SPRUCE_LOG = "minecraft:spruce_log";
    private static final String STRIPPED_OAK = "minecraft:stripped_oak_log";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String OAK_STAIRS = "minecraft:oak_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String SACK = "minecraft:brown_wool";
    private static final String CANVAS = "minecraft:white_wool";
    private static final String DOOR = "minecraft:spruce_door";
    private static final String CAULDRON = "minecraft:water_cauldron[level=3]";
    private static final String TABLE_TOP = "minecraft:oak_pressure_plate[powered=false]";
    private static final String OPEN_GATE = "minecraft:oak_fence_gate[facing=south,in_wall=false,open=true,powered=false]";
    private static final String STALL_GATE = "minecraft:oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]";
    private static final String FENCE = Kit.fence("oak");

    private Yards() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("classic_builders_yard", "production", "yard", "minecraft:bricks", 8, 0, 12, buildersYard(),
                "none", 1, "")
                .at("door", 8, 0, 12));
        list.add(new BuildingGenerator.Building("classic_carters_yard", "farming", "yard", "minecraft:barrel", 8, 0, 14, cartersYard(),
                "none", 1, "")
                .at("door", 8, 0, 14));
        return list;
    }

    private static String upright(String log) {
        return Kit.log(log, "y");
    }

    /**
     * A wheel of a wagon: a dark oak trapdoor standing open against the side of the wagon beside it, its lattice the spokes.
     *
     * @param facing the way out from the wagon at this wheel
     */
    private static String wheel(String facing) {
        return "minecraft:dark_oak_trapdoor[facing=" + facing + ",half=bottom,open=true,powered=false,waterlogged=false]";
    }

    // ---------------------------------------------------------------- the builders' yard

    /**
     * The yard of the builders of the kingdom: across the back a shed open to the yard under a shingle roof with the
     * workbenches, stonecutters and the post of the master builder, and beside it a timber office with a lectern for the
     * plans whose plastered gable looks at the street; in the yard a half-built wall with an opening for a window and a
     * scaffold of fences, a wooden crane lowering a block of stone on to its end, stacks of logs, planks and stone, a heap
     * of rubble and a tub of mortar; ladders leaning; a fence round it with a gate between two posts with lanterns.
     */
    static Structure buildersYard() {
        Structure s = new Structure(17, 12, 13);
        buildersShed(s);
        buildersOffice(s);
        buildersFence(s);
        buildersWall(s);
        buildersCrane(s);
        buildersStock(s);
        s.connect();
        return s;
    }

    /**
     * The shed: walls of planks on a course of rough stone with posts of logs, open to the yard between its posts, under a
     * gable roof of shingles; crafting tables, stonecutters and a smithing table at the back wall, the post of the master
     * builder with its chest, a sawhorse, barrels, lanterns hanging from the tie beams.
     */
    private static void buildersShed(Structure s) {
        s.fill(0, 0, 0, 9, 3, 0, OAK_PLANKS);
        s.fill(0, 0, 1, 0, 4, 4, OAK_PLANKS).fill(9, 0, 1, 9, 4, 4, OAK_PLANKS);
        s.fill(0, 0, 0, 9, 0, 0, Kit.ROUGH).fill(0, 0, 1, 0, 0, 4, Kit.ROUGH).fill(9, 0, 1, 9, 0, 4, Kit.ROUGH);
        for (int x : new int[]{0, 3, 6, 9}) {
            s.fill(x, 0, 0, x, 3, 0, upright(SPRUCE_LOG));
            s.fill(x, 0, 4, x, 3, 4, upright(SPRUCE_LOG));
        }
        s.fill(0, 4, 4, 9, 4, 4, Kit.log(SPRUCE_LOG, "x"));
        s.fill(3, 4, 1, 3, 4, 3, Kit.log(SPRUCE_LOG, "z")).fill(6, 4, 1, 6, 4, 3, Kit.log(SPRUCE_LOG, "z"));
        s.gableRoof(0, 9, 0, 5, 4, Kit.SHINGLE.stairs(), Kit.SHINGLE.slab(), OAK_PLANKS, 0, 9);
        s.set(0, 2, 2, Kit.GLASS).set(9, 2, 2, Kit.GLASS);
        s.set(1, 0, 1, "minecraft:crafting_table").set(2, 0, 1, "minecraft:crafting_table");
        s.set(4, 0, 1, "minecraft:stonecutter[facing=south]").set(5, 0, 1, "minecraft:stonecutter[facing=south]");
        s.set(7, 0, 1, Kit.chest("south")).set(7, 0, 2, "kingdomworld:work_post");
        s.set(8, 0, 1, "minecraft:smithing_table").set(8, 0, 3, Kit.barrel()).set(8, 1, 3, Kit.barrel());
        s.set(4, 0, 3, Kit.fence("spruce")).set(5, 0, 3, Kit.fence("spruce"));
        s.set(4, 1, 3, Kit.slab(SPRUCE_SLAB)).set(5, 1, 3, Kit.slab(SPRUCE_SLAB));
        s.set(1, 0, 3, "minecraft:grindstone[face=floor,facing=south]");
        s.set(3, 3, 2, Kit.hangingLantern()).set(6, 3, 2, Kit.hangingLantern());
    }

    /**
     * The office of the master builder beside the shed: a timber frame filled with plaster on a footing of rough stone under
     * a gable roof of tiles turned to the street, a door with a step and a banner over it, windows with shutters and one in
     * the gable; inside, a lectern with the plans, a chest, a shelf, a table with a chair, a lantern hanging from the ridge;
     * a ladder leaning on its side.
     */
    private static void buildersOffice(Structure s) {
        s.fill(11, 0, 0, 15, 0, 4, Kit.ROUGH);
        s.fill(12, 0, 1, 14, 0, 3, SPRUCE_PLANKS);
        s.walls(11, 1, 0, 15, 3, 4, Kit.PLASTER);
        s.corners(11, 1, 0, 15, 3, 4, OAK_LOG);
        s.fill(11, 1, 2, 11, 3, 2, upright(OAK_LOG)).fill(15, 1, 2, 15, 3, 2, upright(OAK_LOG));
        s.beam(11, 0, 15, 4, 4, OAK_LOG);
        s.gableRoofZ(10, 16, 0, 5, 4, Kit.TILE.stairs(), Kit.TILE.slab(), Kit.PLASTER, 0, 4);
        s.door(13, 1, 4, DOOR, "north", "left");
        s.set(13, 0, 5, Kit.stairs(OAK_STAIRS, "north"));
        s.set(12, 2, 4, Kit.GLASS).set(14, 2, 4, Kit.GLASS).set(13, 5, 4, Kit.GLASS).set(15, 2, 3, Kit.GLASS).set(11, 2, 3, Kit.GLASS);
        s.set(11, 2, 5, Kit.shutter(Kit.SHUTTER, "south")).set(15, 2, 5, Kit.shutter(Kit.SHUTTER, "south"));
        s.set(13, 4, 5, Kit.wallBanner("red", "south"));
        s.set(13, 1, 1, "minecraft:lectern[facing=south,has_book=false,powered=false]").set(12, 1, 1, Kit.chest("south"));
        s.set(14, 1, 1, "minecraft:bookshelf").set(14, 2, 1, "minecraft:bookshelf");
        s.set(12, 1, 3, FENCE).set(12, 2, 3, TABLE_TOP).set(12, 1, 2, Kit.stairs(OAK_STAIRS, "north"));
        s.set(14, 1, 3, Kit.barrel());
        s.fill(13, 4, 2, 13, 6, 2, Kit.chain()).set(13, 3, 2, Kit.hangingLantern());
        s.fill(10, 0, 1, 10, 2, 1, Kit.ladder("west"));
        s.set(10, 0, 0, FENCE);
    }

    /**
     * The fence round the yard and the gate in the middle of its front: two posts of logs with lanterns on them, the gate
     * standing open, a banner of the kingdom on a post, a lamp post among the stocks.
     */
    private static void buildersFence(Structure s) {
        s.fill(0, 0, 5, 0, 0, 12, FENCE).fill(16, 0, 5, 16, 0, 12, FENCE);
        s.fill(0, 0, 12, 5, 0, 12, FENCE).fill(11, 0, 12, 16, 0, 12, FENCE);
        for (int x : new int[]{6, 10}) {
            s.fill(x, 0, 12, x, 2, 12, upright(SPRUCE_LOG)).set(x, 3, 12, Kit.lantern());
        }
        s.set(7, 0, 12, OPEN_GATE).set(9, 0, 12, OPEN_GATE);
        s.set(5, 2, 12, Kit.wallBanner("red", "west"));
        s.fill(10, 0, 9, 10, 2, 9, FENCE).set(10, 3, 9, Kit.lantern());
    }

    /**
     * The half-built wall in the front of the yard: one block thick on a footing of rough stone, a quoin of rough stone with
     * a coping of trim at its finished end, an opening for a window with a sill and a lintel of trim, then stepping down to
     * its first course; a scaffold of fences with boards and a rail on the yard side with a lantern on a post, a ladder
     * leaning on its street side.
     */
    private static void buildersWall(Structure s) {
        int z = 10;
        s.fill(1, 0, z, 6, 0, z, Kit.ROUGH);
        s.fill(1, 1, z, 1, 4, z, Kit.ROUGH).set(1, 5, z, Kit.slab(Kit.TRIM_SLAB));
        s.fill(2, 1, z, 2, 4, z, Kit.WALL).fill(3, 1, z, 3, 3, z, Kit.WALL);
        s.fill(4, 1, z, 4, 2, z, Kit.WALL).set(5, 1, z, Kit.WALL);
        s.set(2, 1, z, Kit.TRIM).set(3, 1, z, Kit.TRIM).set(2, 2, z, AIR).set(3, 2, z, AIR);
        s.set(2, 3, z, Kit.TRIM).set(3, 3, z, Kit.TRIM);
        for (int x : new int[]{2, 4, 6}) {
            s.fill(x, 0, z - 1, x, x == 6 ? 2 : 3, z - 1, Kit.fence("spruce"));
        }
        s.set(2, 4, z - 1, Kit.fence("spruce")).set(2, 5, z - 1, Kit.lantern());
        s.set(3, 2, z - 1, Kit.topSlab(SPRUCE_SLAB)).set(5, 2, z - 1, Kit.topSlab(SPRUCE_SLAB));
        s.set(3, 3, z - 1, Kit.fence("spruce"));
        s.fill(4, 0, z + 1, 4, 2, z + 1, Kit.ladder("south"));
    }

    /**
     * The crane beside the wall: a mast of a log on a stone with a lantern on its top, a jib of fences on a knee brace
     * reaching over the end of the wall, a chain from it holding a block of stone just above the first course, a
     * counterweight of rubble on the short arm and a winch at the foot of the mast.
     */
    private static void buildersCrane(Structure s) {
        s.set(6, 0, 7, Kit.ROUGH);
        s.fill(6, 1, 7, 6, 10, 7, upright(SPRUCE_LOG));
        s.set(6, 11, 7, Kit.lantern());
        s.fill(6, 9, 8, 6, 9, 10, FENCE).set(6, 9, 6, FENCE);
        s.set(6, 8, 8, Kit.upside(SPRUCE_STAIRS, "north"));
        s.fill(6, 3, 10, 6, 8, 10, Kit.chain()).set(6, 2, 10, Kit.WALL);
        s.set(6, 8, 6, Kit.chain()).set(6, 7, 6, Kit.ROUGH);
        s.set(7, 1, 7, "minecraft:grindstone[face=wall,facing=east]");
    }

    /**
     * The stocks of the yard: a pile of logs, a stack of planks, a pallet of stone and a heap of rubble on the east side, a
     * tub of mortar and a barrel by the wall.
     */
    private static void buildersStock(Structure s) {
        s.fill(12, 0, 6, 15, 0, 6, Kit.log(OAK_LOG, "x")).fill(12, 0, 7, 15, 0, 7, Kit.log(SPRUCE_LOG, "x"));
        s.fill(13, 1, 6, 15, 1, 6, Kit.log(STRIPPED_OAK, "x"));
        s.fill(13, 0, 10, 15, 0, 11, OAK_PLANKS).fill(14, 1, 10, 15, 1, 11, SPRUCE_PLANKS);
        s.fill(15, 2, 10, 15, 2, 11, Kit.slab(SPRUCE_SLAB));
        s.fill(11, 0, 11, 12, 1, 11, Kit.WALL);
        s.set(12, 0, 9, Kit.ROUGH).set(13, 0, 9, Kit.ROUGH);
        s.set(12, 1, 9, Kit.slab(Kit.ROUGH_SLAB)).set(14, 0, 9, Kit.slab(Kit.ROUGH_SLAB));
        s.set(1, 0, 7, CAULDRON).set(1, 0, 8, Kit.barrel());
    }

    // ---------------------------------------------------------------- the carters' yard

    /**
     * The yard of the carters who drive goods between the storehouses: across the back a stable for two draught horses with
     * stalls, troughs and hay under a roof of shingles, its hay loft open in a dormer over the stalls and in the east gable
     * with a hoist; on the east side a loading dock on posts under a gable roof with barrels, crates and sacks, the post of
     * the carters and a banner of the kingdom; a covered wagon at the dock and a loaded wagon in the yard, both on spoked
     * wheels; a wide gate under a beam with a banner standing on it and lanterns.
     */
    static Structure cartersYard() {
        Structure s = new Structure(17, 9, 15);
        cartersStable(s);
        cartersDock(s);
        coveredWagon(s, 7, 7);
        openWagon(s, 1, 10);
        cartersGate(s);
        s.connect();
        return s;
    }

    /**
     * The stable: walls of planks with posts of logs on a course of rough stone, two stalls with low fences and gates to the
     * yard, troughs of cauldrons and hay, a hay loft over them with hay showing in a dormer on the front and at the door in
     * the east gable with a hoist and a lantern over it, a lantern under the front eave, a spare wheel leaning on the wall
     * and torches on the back wall.
     */
    private static void cartersStable(Structure s) {
        s.fill(0, 0, 1, 8, 4, 1, SPRUCE_PLANKS);
        s.fill(0, 0, 1, 0, 4, 5, SPRUCE_PLANKS).fill(8, 0, 1, 8, 4, 5, SPRUCE_PLANKS);
        s.fill(0, 0, 1, 8, 0, 1, Kit.ROUGH).fill(0, 0, 1, 0, 0, 5, Kit.ROUGH).fill(8, 0, 1, 8, 0, 5, Kit.ROUGH);
        s.fill(1, 3, 2, 7, 3, 5, SPRUCE_PLANKS);
        for (int[] c : new int[][]{{0, 1}, {8, 1}, {0, 5}, {8, 5}, {4, 1}, {4, 5}}) {
            s.fill(c[0], 0, c[1], c[0], 3, c[1], upright(SPRUCE_LOG));
        }
        s.fill(0, 4, 1, 8, 4, 1, Kit.log(SPRUCE_LOG, "x")).fill(0, 4, 5, 8, 4, 5, Kit.log(SPRUCE_LOG, "x"));
        s.gableRoof(0, 8, 0, 6, 4, Kit.SHINGLE.stairs(), Kit.SHINGLE.slab(), SPRUCE_PLANKS, 0, 8);
        // The dormer of the hay loft over the stalls, open, with hay behind it and a lantern under the eave below it.
        s.set(3, 5, 5, SPRUCE_PLANKS).set(5, 5, 5, SPRUCE_PLANKS).set(4, 5, 5, AIR).set(4, 6, 5, SPRUCE_PLANKS);
        s.fill(3, 6, 4, 3, 6, 5, Kit.stairs(Kit.SHINGLE.stairs(), "east")).fill(5, 6, 4, 5, 6, 5, Kit.stairs(Kit.SHINGLE.stairs(), "west"));
        s.fill(4, 7, 4, 4, 7, 5, Kit.slab(Kit.SHINGLE.slab())).set(4, 6, 4, AIR);
        s.set(4, 3, 6, Kit.hangingLantern());
        for (int[] c : new int[][]{{1, 2}, {2, 2}, {1, 3}, {4, 2}, {4, 3}, {4, 4}, {6, 2}, {7, 2}, {7, 3}, {7, 4}}) {
            s.set(c[0], 4, c[1], HAY);
        }
        s.set(1, 5, 3, HAY).set(7, 5, 3, HAY).set(4, 5, 4, HAY).set(4, 5, 3, HAY);
        s.fill(8, 4, 3, 8, 5, 3, AIR);
        s.set(9, 6, 3, Kit.fence("spruce")).set(9, 5, 3, Kit.chain()).set(9, 4, 3, Kit.hangingLantern());
        // The stalls: low fences with gates, a partition, troughs and hay.
        for (int x : new int[]{1, 3, 5, 7}) {
            s.set(x, 0, 5, FENCE);
        }
        s.set(2, 0, 5, STALL_GATE).set(6, 0, 5, STALL_GATE);
        s.fill(4, 0, 2, 4, 1, 4, FENCE);
        s.set(1, 0, 2, CAULDRON).set(7, 0, 2, CAULDRON);
        s.set(2, 0, 2, HAY).set(2, 1, 2, HAY).set(6, 0, 2, HAY).set(3, 0, 2, Kit.barrel());
        s.set(2, 2, 3, Kit.hangingLantern()).set(6, 2, 3, Kit.hangingLantern());
        s.set(0, 2, 3, Kit.GLASS).set(8, 2, 3, Kit.GLASS);
        // Outside: a trough and hay by the front, a spare wheel, torches on the back wall.
        s.set(3, 0, 6, CAULDRON).set(1, 0, 6, HAY).set(1, 1, 6, HAY).set(5, 0, 6, HAY);
        s.set(9, 0, 5, wheel("east"));
        s.set(2, 2, 0, Kit.wallTorch("north")).set(6, 2, 0, Kit.wallTorch("north"));
    }

    /**
     * The loading dock: a platform of planks edged with rough stone, raised to the height of a wagon bed, with steps from
     * the yard, on posts of logs under a gable roof closed at the back; barrels and crates stacked, sacks, a bale of hay,
     * the post of the carters with its chest, lanterns under the ridge and a banner of the kingdom hanging from the front
     * beam.
     */
    private static void cartersDock(Structure s) {
        s.fill(11, 0, 0, 15, 0, 8, SPRUCE_PLANKS);
        s.fill(11, 0, 0, 11, 0, 8, Kit.ROUGH).fill(11, 0, 8, 15, 0, 8, Kit.ROUGH);
        for (int x : new int[]{11, 15}) {
            for (int z : new int[]{0, 4, 8}) {
                s.fill(x, 1, z, x, 4, z, upright(SPRUCE_LOG));
            }
            s.fill(x, 4, 1, x, 4, 3, Kit.log(SPRUCE_LOG, "z")).fill(x, 4, 5, x, 4, 7, Kit.log(SPRUCE_LOG, "z"));
        }
        s.fill(12, 1, 0, 14, 4, 0, SPRUCE_PLANKS);
        s.fill(12, 4, 8, 14, 4, 8, Kit.log(SPRUCE_LOG, "x"));
        s.gableRoofZ(10, 16, 0, 9, 4, Kit.TEAL.stairs(), Kit.TEAL.slab(), SPRUCE_PLANKS, 0, -1);
        s.fill(12, 0, 9, 14, 0, 9, Kit.stairs(SPRUCE_STAIRS, "north"));
        s.fill(12, 1, 1, 14, 1, 1, Kit.barrel()).set(12, 2, 1, Kit.barrel()).set(13, 2, 1, Kit.barrel()).set(14, 1, 2, Kit.barrel());
        s.set(12, 1, 3, SACK).set(12, 1, 4, SACK).set(12, 2, 3, SACK).set(14, 1, 7, HAY).set(14, 1, 6, SACK);
        s.set(13, 1, 5, "kingdomworld:work_post").set(14, 1, 5, Kit.chest("west"));
        s.set(13, 6, 3, Kit.chain()).set(13, 5, 3, Kit.hangingLantern());
        s.set(13, 6, 6, Kit.chain()).set(13, 5, 6, Kit.hangingLantern());
        s.set(13, 4, 9, Kit.wallBanner("red", "south"));
    }

    /**
     * A covered wagon three wide and four long standing along the yard with its front to the south: spoked wheels at its
     * sides, axles of fences, a bed of boards, a canvas of white wool over the back three cells with a barrel inside, the
     * driver's seat and the shaft.
     */
    private static void coveredWagon(Structure s, int x0, int z0) {
        for (int z : new int[]{z0, z0 + 3}) {
            s.set(x0 - 1, 0, z, wheel("west")).set(x0 + 3, 0, z, wheel("east")).fill(x0, 0, z, x0 + 2, 0, z, Kit.fence("spruce"));
        }
        s.fill(x0, 1, z0, x0 + 2, 1, z0 + 3, Kit.topSlab(SPRUCE_SLAB));
        s.fill(x0, 2, z0, x0, 2, z0 + 2, CANVAS).fill(x0 + 2, 2, z0, x0 + 2, 2, z0 + 2, CANVAS);
        s.fill(x0, 3, z0, x0 + 2, 3, z0 + 2, CANVAS);
        s.set(x0 + 1, 2, z0, Kit.barrel()).set(x0 + 1, 2, z0 + 1, SACK);
        s.set(x0 + 1, 2, z0 + 3, Kit.stairs(SPRUCE_STAIRS, "north"));
        s.fill(x0 + 1, 0, z0 + 4, x0 + 1, 0, z0 + 5, Kit.fence("spruce"));
    }

    /**
     * An open wagon two wide and four long standing across the yard with its front to the east: spoked wheels at its sides,
     * axles of fences, a bed of boards loaded with barrels, sacks and hay, and the shaft.
     */
    private static void openWagon(Structure s, int x0, int z0) {
        for (int x : new int[]{x0, x0 + 3}) {
            s.set(x, 0, z0 - 1, wheel("north")).set(x, 0, z0 + 2, wheel("south")).fill(x, 0, z0, x, 0, z0 + 1, Kit.fence("spruce"));
        }
        s.fill(x0, 1, z0, x0 + 3, 1, z0 + 1, Kit.topSlab(SPRUCE_SLAB));
        s.set(x0, 2, z0, Kit.barrel()).set(x0 + 1, 2, z0 + 1, Kit.barrel());
        s.set(x0 + 1, 2, z0, SACK).set(x0, 2, z0 + 1, SACK).set(x0 + 2, 2, z0 + 1, SACK).set(x0 + 1, 3, z0, SACK);
        s.set(x0 + 2, 2, z0, HAY);
        s.fill(x0 + 4, 0, z0, x0 + 5, 0, z0, Kit.fence("spruce"));
    }

    /**
     * The front of the yard: a fence on three sides, a gate five wide between two posts of logs under a beam with a banner
     * of the kingdom standing on it, lanterns hanging under the beam, the leaves of the gate standing open, a lamp post by
     * the dock.
     */
    private static void cartersGate(Structure s) {
        s.fill(0, 0, 6, 0, 0, 14, FENCE).fill(16, 0, 0, 16, 0, 14, FENCE).fill(9, 0, 0, 10, 0, 0, FENCE);
        s.fill(0, 0, 14, 4, 0, 14, FENCE).fill(12, 0, 14, 16, 0, 14, FENCE);
        for (int x : new int[]{5, 11}) {
            s.fill(x, 0, 14, x, 3, 14, upright(SPRUCE_LOG));
        }
        s.fill(5, 4, 14, 11, 4, 14, Kit.log(SPRUCE_LOG, "x"));
        s.set(8, 5, 14, Kit.banner("red", 0));
        s.set(6, 3, 14, Kit.hangingLantern()).set(10, 3, 14, Kit.hangingLantern());
        s.set(6, 0, 14, OPEN_GATE).set(10, 0, 14, OPEN_GATE);
        s.fill(12, 0, 11, 12, 2, 11, FENCE).set(12, 3, 11, Kit.lantern());
    }
}
