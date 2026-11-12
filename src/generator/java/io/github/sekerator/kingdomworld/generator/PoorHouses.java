package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * Houses of the poor quarter that lies along the wall of a capital: a hut under a lopsided roof that sweeps down almost to
 * the ground, a clay hut with rounded corners under a hip roof like a hat, a shack of patched boards under a roof of one
 * slope with its woodshed and its washing, and a cottage of one and a half floors with a dormer, a porch and flower boxes.
 * They are small and plain, but each has its own outline and its own roof, so that a street of them is no row of the same
 * box. All face south and stand on a footing one block above the street.
 */
final class PoorHouses {
    private static final String OAK_LOG = "minecraft:oak_log";
    private static final String SPRUCE_LOG = "minecraft:spruce_log";
    private static final String STRIPPED_SPRUCE = "minecraft:stripped_spruce_log";
    private static final String OAK_PLANKS = "minecraft:oak_planks";
    private static final String SPRUCE_PLANKS = "minecraft:spruce_planks";
    private static final String OAK_STAIRS = "minecraft:oak_stairs";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String OAK_DOOR = "minecraft:oak_door";
    private static final String SPRUCE_DOOR = "minecraft:spruce_door";
    private static final String OAK_TRAPDOOR = "minecraft:oak_trapdoor";
    private static final String FENCE = Kit.fence("oak");
    private static final String SPRUCE_FENCE = Kit.fence("spruce");
    private static final String CRAFTING_TABLE = "minecraft:crafting_table";
    private static final String PACKED_MUD = "minecraft:packed_mud";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String CAULDRON = "minecraft:cauldron";
    private static final String FLOWER_POT = "minecraft:flower_pot";
    private static final String PRESSURE_PLATE = "minecraft:oak_pressure_plate[powered=false]";

    private PoorHouses() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(house("classic_house_hut", "minecraft:oak_log", hut()));
        list.add(house("classic_house_clay", "minecraft:white_terracotta", mudHut()));
        list.add(house("classic_house_shack", "minecraft:spruce_planks", shack()));
        list.add(house("classic_house_cottage", "minecraft:red_bed", rowCottage()));
        return list;
    }

    private static BuildingGenerator.Building house(String name, String icon, Structure s) {
        return new BuildingGenerator.Building(name, "housing", "house", icon, (s.sizeX - 1) / 2, 0, s.sizeZ - 1, s, "house", 1, "");
    }

    // ---------------------------------------------------------------- small states

    private static String furnace(String facing) {
        return "minecraft:furnace[facing=" + facing + ",lit=false]";
    }

    private static String smoker(String facing) {
        return "minecraft:smoker[facing=" + facing + ",lit=false]";
    }

    private static String bed(String color) {
        return "minecraft:" + color + "_bed";
    }

    private static String sunflower(boolean upper) {
        return "minecraft:sunflower[half=" + (upper ? "upper" : "lower") + "]";
    }

    // ---------------------------------------------------------------- roofs

    /**
     * One ring of a hip roof at one height: stairs along the four sides looking outwards and turned stairs at the corners.
     */
    private static void hipRing(Structure s, int west, int east, int back, int front, int y, Kit.Roof roof) {
        s.fill(west, y, back, east, y, back, Kit.stairs(roof.stairs(), "south"));
        s.fill(west, y, front, east, y, front, Kit.stairs(roof.stairs(), "north"));
        if (front - back > 1) {
            s.fill(west, y, back + 1, west, y, front - 1, Kit.stairs(roof.stairs(), "east"));
            s.fill(east, y, back + 1, east, y, front - 1, Kit.stairs(roof.stairs(), "west"));
        }
        s.set(west, y, back, Kit.stairs(roof.stairs(), "south", "outer_left"));
        s.set(east, y, back, Kit.stairs(roof.stairs(), "south", "outer_right"));
        s.set(west, y, front, Kit.stairs(roof.stairs(), "north", "outer_right"));
        s.set(east, y, front, Kit.stairs(roof.stairs(), "north", "outer_left"));
    }

    /**
     * A gable roof whose ridge runs along x: rows of stairs rising one block per row from the back eave ({@code z1}) and
     * the front eave ({@code z2}); a ridge of slabs where an odd number of rows leaves one in the middle.
     *
     * @param y height of the eaves
     */
    private static void gableRoof(Structure s, int x1, int x2, int z1, int z2, int y, Kit.Roof roof) {
        int row = 0;
        while (z1 + row < z2 - row) {
            s.fill(x1, y + row, z1 + row, x2, y + row, z1 + row, Kit.stairs(roof.stairs(), "south"));
            s.fill(x1, y + row, z2 - row, x2, y + row, z2 - row, Kit.stairs(roof.stairs(), "north"));
            row++;
        }
        if (z1 + row == z2 - row) {
            s.fill(x1, y + row, z1 + row, x2, y + row, z1 + row, Kit.slab(roof.slab()));
        }
    }

    // ---------------------------------------------------------------- the hut

    /**
     * A hut of one low room for one sleeper. Its gable looks at the street, and its roof is lopsided: on the west it sweeps
     * down almost to the ground, flat at the eave and steep near the ridge, while on the east it ends in a short fall over
     * a taller wall. The walls are a course of rough stone under boards between log posts; a crooked brick chimney climbs
     * the back gable and leans over at its top, logs are piled against the back and beside the door, and a lantern hangs
     * from a bracket at the door. Inside: a bed under the low slope, a furnace under a hood of rough stone, a crafting
     * table, a barrel and a chest.
     */
    static Structure hut() {
        Structure s = new Structure(5, 10, 7);
        Kit.Roof roof = Kit.SHINGLE;
        // The footing of rough stone and the floor of boards.
        s.fill(0, 0, 1, 4, 0, 5, Kit.ROUGH).fill(1, 0, 2, 3, 0, 4, SPRUCE_PLANKS);
        // The east wall: a course of rough stone, boards above it between posts under a lying beam.
        s.fill(4, 1, 1, 4, 1, 5, Kit.ROUGH).fill(4, 2, 1, 4, 3, 5, SPRUCE_PLANKS).fill(4, 4, 2, 4, 4, 4, Kit.log(OAK_LOG, "z"));
        s.fill(4, 2, 1, 4, 4, 1, Kit.log(OAK_LOG, "y")).fill(4, 2, 5, 4, 4, 5, Kit.log(OAK_LOG, "y"));
        // The two gables under the roof.
        for (int z : new int[]{1, 5}) {
            s.fill(1, 1, z, 3, 1, z, Kit.ROUGH);
            s.set(2, 2, z, SPRUCE_PLANKS).fill(3, 2, z, 3, 4, z, SPRUCE_PLANKS);
        }
        // The roof: from the ground on the west a flat eave that steepens towards the ridge, a short fall on the east.
        for (int z = 1; z <= 5; z++) {
            s.set(0, 1, z, Kit.stairs(roof.stairs(), "east")).set(1, 2, z, Kit.stairs(roof.stairs(), "east"));
            s.set(2, 3, z, roof.block()).set(2, 4, z, Kit.stairs(roof.stairs(), "east"));
            s.set(3, 5, z, roof.block()).set(3, 6, z, Kit.stairs(roof.stairs(), "east"));
            s.set(4, 5, z, Kit.stairs(roof.stairs(), "west"));
        }
        // The door with its step, a lintel and a little hood, a window beside it, a vent above it, a window in the east wall.
        s.door(3, 1, 5, SPRUCE_DOOR, "north", "left").set(3, 0, 6, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.set(3, 3, 5, Kit.log(OAK_LOG, "x")).set(3, 3, 6, Kit.stairs(roof.stairs(), "north"));
        s.set(3, 4, 5, Kit.GLASS).set(2, 2, 5, Kit.GLASS).fill(4, 2, 3, 4, 3, 3, Kit.GLASS);
        // Logs piled beside the door, a chopping block, a lantern on a bracket.
        s.fill(0, 0, 6, 1, 0, 6, Kit.log(OAK_LOG, "z")).set(0, 1, 6, Kit.log(OAK_LOG, "z"));
        s.set(4, 0, 6, Kit.log(OAK_LOG, "y")).set(4, 3, 6, SPRUCE_FENCE).set(4, 2, 6, Kit.hangingLantern());
        // The crooked chimney at the back gable: a stack beside the ridge whose pipe leans east on a corbel at its top.
        s.fill(3, 0, 0, 3, 6, 0, Kit.CHIMNEY);
        s.set(4, 6, 0, Kit.upside(Kit.CHIMNEY_STAIRS, "west")).set(4, 7, 0, Kit.CHIMNEY).set(4, 8, 0, Kit.CHIMNEY_POT);
        // Logs piled against the back gable.
        s.fill(0, 0, 0, 2, 0, 0, Kit.log(OAK_LOG, "x")).fill(1, 1, 0, 2, 1, 0, Kit.log(OAK_LOG, "x")).set(2, 2, 0, Kit.log(OAK_LOG, "x"));
        // Inside: the furnace against the chimney under a hood of rough stone.
        s.bed(1, 1, 3, bed("brown"), "north", 0, -1);
        s.set(3, 1, 2, furnace("south")).set(3, 2, 2, Kit.upside(Kit.ROUGH_STAIRS, "north")).fill(3, 3, 2, 3, 4, 2, Kit.ROUGH);
        s.set(2, 1, 2, CRAFTING_TABLE).set(2, 1, 4, Kit.barrel()).set(1, 1, 4, Kit.chest("east"));
        s.set(3, 4, 4, Kit.hangingLantern());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the clay hut

    /**
     * A clay hut: thick white walls with rounded corners on a plinth of rough stone, under a hip roof that sits on them like
     * a hat, overhanging on three sides, flat at the eave and steep above it; the white flue of the stove rises through it
     * with a cap of stone. Two tiny windows look at the street with a timber lintel over them and the door; the side windows
     * have shutters. In front lies a strip of yard behind a wattle fence with pots on its posts: a bench by the door on one
     * side, a patch of flowers and a sunflower on the other. Inside: two beds, the stove, a crafting table and a chest.
     */
    static Structure mudHut() {
        Structure s = new Structure(7, 10, 7);
        Kit.Roof roof = Kit.TEAL;
        // The walls on their plinth, the corners rounded off; a floor of clay inside.
        for (int x = 1; x <= 5; x++) {
            for (int z = 0; z <= 4; z++) {
                boolean edgeX = x == 1 || x == 5;
                boolean edgeZ = z == 0 || z == 4;
                if (edgeX && edgeZ) {
                    s.set(x, 0, z, Kit.slab(Kit.ROUGH_SLAB));
                } else if (edgeX || edgeZ) {
                    s.set(x, 0, z, Kit.ROUGH).fill(x, 1, z, x, 3, z, Kit.PLASTER);
                } else {
                    s.set(x, 0, z, PACKED_MUD);
                }
            }
        }
        // A steep hip roof like a hat: a flat eave all round, a steep ring above it, a short ridge on top.
        hipRing(s, 0, 6, 0, 5, 4, roof);
        s.walls(1, 5, 1, 5, 5, 4, roof.block());
        hipRing(s, 1, 5, 1, 4, 6, roof);
        s.fill(2, 6, 2, 4, 6, 3, roof.block());
        hipRing(s, 2, 4, 2, 3, 7, roof);
        // The flue of the stove, plastered white where it rises through the roof, with a cap of rough stone.
        s.fill(3, 2, 1, 3, 4, 1, Kit.ROUGH).fill(3, 5, 1, 3, 8, 1, Kit.PLASTER).set(3, 9, 1, Kit.slab(Kit.ROUGH_SLAB));
        // The front: the door and two tiny windows under timber lintels.
        s.door(3, 1, 4, OAK_DOOR, "north", "left").set(3, 0, 5, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.set(2, 2, 4, Kit.GLASS).set(4, 2, 4, Kit.GLASS);
        s.fill(2, 3, 4, 4, 3, 4, Kit.log(OAK_LOG, "x"));
        // The side windows with their shutters.
        s.set(1, 2, 2, Kit.GLASS).set(5, 2, 2, Kit.GLASS);
        s.set(0, 2, 1, Kit.shutter(Kit.SHUTTER, "west")).set(0, 2, 3, Kit.shutter(Kit.SHUTTER, "west"));
        s.set(6, 2, 1, Kit.shutter(Kit.SHUTTER, "east")).set(6, 2, 3, Kit.shutter(Kit.SHUTTER, "east"));
        // The yard: a bench by the door, flowers and a sunflower, a wattle fence with pots on its posts.
        s.fill(1, 0, 5, 2, 0, 5, Kit.stairs(SPRUCE_STAIRS, "north"));
        s.fill(4, 0, 5, 5, 0, 5, GRASS).set(4, 1, 5, "minecraft:cornflower");
        s.set(5, 1, 5, sunflower(false)).set(5, 2, 5, sunflower(true));
        s.fill(0, 0, 6, 2, 0, 6, FENCE).fill(4, 0, 6, 6, 0, 6, FENCE).set(0, 0, 5, FENCE).set(6, 0, 5, FENCE);
        for (int x : new int[]{0, 2, 4, 6}) {
            s.set(x, 1, 6, FLOWER_POT);
        }
        // A lantern under the eave over the bench, a barrel under the east eave.
        s.set(2, 3, 5, Kit.hangingLantern()).set(6, 0, 2, Kit.barrel());
        // Inside.
        s.bed(2, 1, 2, bed("white"), "north", 0, -1).bed(4, 1, 2, bed("light_gray"), "north", 0, -1);
        s.set(3, 1, 1, furnace("south")).fill(3, 2, 1, 3, 3, 1, Kit.ROUGH);
        s.set(2, 1, 3, CRAFTING_TABLE).set(4, 1, 3, Kit.chest("west"));
        s.set(3, 5, 2, Kit.hangingLantern());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the shack

    /** The height of the roof of the shack over a column in half blocks above layer 2: it falls from the west to the east. */
    private static int shackLevel(int x) {
        return 7 - x;
    }

    /** The layer of the slab of the shack's roof over a column. */
    private static int shackRoofY(int x) {
        return 2 + shackLevel(x) / 2;
    }

    /** Whether the slab of the shack's roof over a column lies in the top half of its layer. */
    private static boolean shackRoofTop(int x) {
        return shackLevel(x) % 2 == 1;
    }

    /** The highest block of a wall of the shack under its roof: it takes the place of a top slab, it stops under a bottom slab. */
    private static int shackWallTop(int x) {
        return shackRoofTop(x) ? shackRoofY(x) : shackRoofY(x) - 1;
    }

    /**
     * A shack of boards and logs whose roof of slabs falls in one slope from its tall west wall over the room and on over a
     * woodshed on the east. Its walls are patched: boards of two woods, a stripped log, a board nailed over a hole; some
     * slabs of the roof are of other kinds and moss grows on it. A thin brick stovepipe climbs the tall wall, a cauldron
     * catches the rain at the low corner, and across the yard in front runs a line of washing between two posts. Inside:
     * two beds along the tall wall, a furnace, a crafting table, a barrel and a chest.
     */
    static Structure shack() {
        Structure s = new Structure(7, 9, 9);
        Kit.Roof roof = Kit.TILE;
        // The footing and the floor.
        s.walls(1, 0, 0, 5, 0, 5, Kit.ROUGH).fill(2, 0, 1, 4, 0, 4, SPRUCE_PLANKS);
        // The walls follow the slope of the roof: tall on the west, low on the east, with posts at the corners.
        for (int x = 1; x <= 5; x++) {
            s.fill(x, 1, 0, x, shackWallTop(x), 0, OAK_PLANKS).fill(x, 1, 5, x, shackWallTop(x), 5, OAK_PLANKS);
        }
        s.fill(1, 1, 1, 1, shackWallTop(1), 4, OAK_PLANKS).fill(5, 1, 1, 5, shackWallTop(5), 4, OAK_PLANKS);
        for (int[] corner : new int[][]{{1, 0}, {5, 0}, {1, 5}, {5, 5}}) {
            s.fill(corner[0], 1, corner[1], corner[0], shackWallTop(corner[0]), corner[1], Kit.log(SPRUCE_LOG, "y"));
        }
        // Patches: boards of the other wood, a stripped log.
        s.fill(3, 1, 5, 3, 2, 5, SPRUCE_PLANKS).set(2, 4, 5, SPRUCE_PLANKS);
        s.fill(1, 1, 2, 1, 2, 2, SPRUCE_PLANKS).set(1, 4, 3, SPRUCE_PLANKS).set(5, 2, 2, SPRUCE_PLANKS);
        s.fill(2, 1, 0, 4, 1, 0, Kit.log(STRIPPED_SPRUCE, "x"));
        // The roof of one slope, with a few slabs of other kinds and moss on it.
        for (int x = 1; x <= 6; x++) {
            s.fill(x, shackRoofY(x), 0, x, shackRoofY(x), 6, shackRoofTop(x) ? Kit.topSlab(roof.slab()) : Kit.slab(roof.slab()));
        }
        for (int[] patch : new int[][]{{2, 2}, {3, 4}, {3, 5}, {5, 1}, {4, 3}}) {
            int x = patch[0];
            String slab = (patch[1] == 3 ? Kit.TEAL : Kit.SHINGLE).slab();
            s.set(x, shackRoofY(x), patch[1], shackRoofTop(x) ? Kit.topSlab(slab) : Kit.slab(slab));
        }
        // Where a front or back wall meets a top slab of the roof, the roof is closed with a full block of it.
        for (int x : new int[]{2, 4}) {
            s.set(x, shackRoofY(x), 0, roof.block()).set(x, shackRoofY(x), 5, roof.block());
        }
        s.set(2, shackRoofY(2) + 1, 3, "minecraft:moss_carpet").set(4, shackRoofY(4) + 1, 1, "minecraft:moss_carpet");
        s.set(6, shackRoofY(6) + 1, 4, "minecraft:moss_carpet");
        // The door with its step, windows and a board nailed over a hole.
        s.door(4, 1, 5, SPRUCE_DOOR, "north", "right").set(4, 0, 6, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.fill(2, 2, 5, 2, 3, 5, Kit.GLASS).set(1, 3, 3, Kit.GLASS).set(3, 2, 0, Kit.GLASS);
        s.set(3, 2, 6, Kit.shutter(OAK_TRAPDOOR, "south"));
        // The stovepipe on the tall wall, and odds and ends beside it.
        s.set(0, 0, 2, Kit.CHIMNEY).fill(0, 1, 2, 0, 6, 2, Kit.CHIMNEY_POT).set(0, 7, 2, Kit.slab(Kit.CHIMNEY_SLAB));
        s.set(0, 0, 4, Kit.barrel()).set(0, 0, 5, "minecraft:hay_block[axis=y]");
        // The woodshed: posts at its corners, logs piled with their ends to the east.
        s.fill(6, 0, 0, 6, 1, 0, Kit.log(SPRUCE_LOG, "y")).fill(6, 0, 5, 6, 1, 5, Kit.log(SPRUCE_LOG, "y"));
        s.fill(6, 0, 1, 6, 0, 4, Kit.log(OAK_LOG, "x")).fill(6, 1, 1, 6, 1, 3, Kit.log(OAK_LOG, "x"));
        // The rain barrel at the low corner, a lantern under the eave beside the door.
        s.set(6, 0, 6, CAULDRON).set(5, 0, 6, Kit.barrel()).set(5, 2, 6, Kit.hangingLantern());
        // The washing line across the yard.
        s.fill(0, 0, 8, 0, 2, 8, FENCE).fill(6, 0, 8, 6, 2, 8, FENCE).fill(1, 2, 8, 5, 2, 8, FENCE);
        s.set(1, 2, 7, Kit.wallBanner("white", "north")).set(2, 2, 7, Kit.wallBanner("light_blue", "north"));
        s.set(3, 2, 7, Kit.wallBanner("white", "north")).set(5, 2, 7, Kit.wallBanner("blue", "north"));
        // Inside.
        s.bed(2, 1, 2, bed("brown"), "north", 0, -1).bed(2, 1, 4, bed("green"), "north", 0, -1);
        s.set(3, 1, 1, CRAFTING_TABLE).set(4, 1, 1, furnace("south")).set(4, 1, 2, Kit.barrel()).set(4, 1, 3, Kit.chest("west"));
        s.set(3, 3, 2, Kit.hangingLantern());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the cottage

    /**
     * A cottage of one and a half floors for a row of the poor quarter. The ground floor is of rough stone with dressed
     * quoins at its corners; above a beam of logs a low wall of plaster between posts carries the roof, whose gables are
     * plastered too. Over the door a little gabled porch stands on two posts, and above it the front wall rises through the
     * eave into a dormer with a gable of its own; both windows beside the porch have flower boxes. A brick chimney with
     * stepped shoulders stands against the west gable, where the roof has no overhang. Inside: a hearth against the chimney,
     * two beds, a table, a crafting table, barrels and a chest below, and a ladder up to a third bed in the attic.
     */
    static Structure rowCottage() {
        Structure s = new Structure(9, 12, 9);
        Kit.Roof roof = Kit.TILE;
        // The plinth and the floor.
        s.walls(1, 0, 1, 7, 0, 6, Kit.ROUGH).fill(2, 0, 2, 6, 0, 5, SPRUCE_PLANKS);
        // The ground floor of rough stone with quoins of dressed stone.
        s.walls(1, 1, 1, 7, 3, 6, Kit.ROUGH);
        for (int[] corner : new int[][]{{1, 1, 1, 1}, {7, 1, -1, 1}, {1, 6, 1, -1}, {7, 6, -1, -1}}) {
            int x = corner[0];
            int z = corner[1];
            s.fill(x, 1, z, x, 3, z, Kit.WALL);
            s.set(x + corner[2], 1, z, Kit.WALL).set(x, 1, z + corner[3], Kit.WALL);
            s.set(x + corner[2], 3, z, Kit.WALL).set(x, 3, z + corner[3], Kit.WALL);
        }
        // The beam that carries the attic floor, and the low wall of the attic: plaster between posts.
        s.beam(1, 1, 7, 6, 4, OAK_LOG).fill(2, 4, 2, 6, 4, 5, SPRUCE_PLANKS);
        s.walls(1, 5, 1, 7, 6, 6, Kit.PLASTER);
        for (int x : new int[]{1, 3, 5, 7}) {
            s.fill(x, 5, 1, x, 6, 1, Kit.log(OAK_LOG, "y")).fill(x, 5, 6, x, 6, 6, Kit.log(OAK_LOG, "y"));
        }
        // The roof, without an overhang on the west where the chimney stands, and the plastered gables under it.
        gableRoof(s, 1, 8, 0, 7, 6, roof);
        for (int x : new int[]{1, 7}) {
            s.fill(x, 7, 2, x, 7, 5, Kit.PLASTER).fill(x, 8, 3, x, 8, 4, Kit.PLASTER);
        }
        // The dormer: the front wall rises through the eave between two posts into a gable of its own, whose roof overhangs
        // the face and runs back into the main roof.
        s.fill(3, 6, 7, 5, 6, 7, Structure.AIR);
        s.fill(2, 6, 6, 2, 6, 7, Kit.stairs(roof.stairs(), "east")).fill(6, 6, 6, 6, 6, 7, Kit.stairs(roof.stairs(), "west"));
        s.fill(3, 7, 5, 3, 7, 7, Kit.stairs(roof.stairs(), "east")).fill(5, 7, 5, 5, 7, 7, Kit.stairs(roof.stairs(), "west"));
        s.fill(4, 8, 5, 4, 8, 7, Kit.slab(roof.slab()));
        s.set(4, 6, 6, Kit.GLASS).set(4, 7, 6, Kit.PLASTER);
        // The porch: a deck, two posts and a little gable over the door.
        s.door(4, 1, 6, OAK_DOOR, "north", "left");
        s.fill(3, 0, 7, 5, 0, 7, SPRUCE_PLANKS).set(4, 0, 8, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.set(3, 0, 8, Kit.ROUGH).set(5, 0, 8, Kit.ROUGH).fill(3, 1, 8, 3, 2, 8, FENCE).fill(5, 1, 8, 5, 2, 8, FENCE);
        s.fill(3, 3, 7, 3, 3, 8, Kit.stairs(roof.stairs(), "east")).fill(5, 3, 7, 5, 3, 8, Kit.stairs(roof.stairs(), "west"));
        s.fill(4, 4, 7, 4, 4, 8, Kit.slab(roof.slab())).set(4, 3, 8, Kit.PLASTER).set(4, 3, 7, Kit.hangingLantern());
        // Windows: the front ones with flower boxes, the others with sills and shutters.
        for (int x : new int[]{2, 6}) {
            s.fill(x, 2, 6, x, 3, 6, Kit.GLASS);
            s.set(x, 1, 7, GRASS).set(x, 1, 8, Kit.shutter(Kit.SHUTTER, "south")).set(x, 0, 7, Kit.upside(Kit.TRIM_STAIRS, "north"));
        }
        s.set(2, 2, 7, "minecraft:poppy").set(6, 2, 7, "minecraft:allium");
        s.fill(7, 2, 3, 7, 2, 4, Kit.GLASS).set(8, 2, 2, Kit.shutter(Kit.SHUTTER, "east")).set(8, 2, 5, Kit.shutter(Kit.SHUTTER, "east"));
        s.fill(7, 1, 3, 7, 1, 4, Kit.TRIM).set(1, 1, 2, Kit.TRIM).set(1, 1, 5, Kit.TRIM);
        s.set(1, 2, 2, Kit.GLASS).set(1, 2, 5, Kit.GLASS).set(0, 2, 1, Kit.shutter(Kit.SHUTTER, "west")).set(0, 2, 6, Kit.shutter(Kit.SHUTTER, "west"));
        s.set(3, 2, 1, Kit.GLASS).set(5, 2, 1, Kit.GLASS);
        s.fill(7, 6, 3, 7, 7, 4, Kit.GLASS).set(1, 6, 2, Kit.GLASS).set(1, 6, 5, Kit.GLASS);
        // The chimney against the west gable: a breast with stepped shoulders, a stack above the ridge, two pots.
        s.fill(0, 0, 2, 0, 1, 5, Kit.CHIMNEY);
        s.set(0, 2, 2, Kit.stairs(Kit.CHIMNEY_STAIRS, "south")).set(0, 2, 5, Kit.stairs(Kit.CHIMNEY_STAIRS, "north"));
        s.fill(0, 2, 3, 0, 10, 4, Kit.CHIMNEY).set(0, 11, 3, Kit.CHIMNEY_POT).set(0, 11, 4, Kit.CHIMNEY_POT);
        // The ground floor: the hearth against the chimney, the beds, a table, the ladder.
        s.set(2, 1, 3, smoker("east")).set(2, 1, 4, furnace("east"));
        s.fill(2, 2, 3, 2, 2, 4, Kit.upside(Kit.ROUGH_STAIRS, "west")).fill(2, 3, 3, 2, 3, 4, Kit.ROUGH);
        s.bed(5, 1, 3, bed("red"), "north", 0, -1).bed(6, 1, 3, bed("blue"), "north", 0, -1);
        s.set(3, 1, 2, CRAFTING_TABLE).set(4, 1, 2, Kit.barrel()).set(2, 1, 5, Kit.barrel()).set(6, 1, 4, Kit.chest("west"));
        s.set(4, 1, 4, FENCE).set(4, 2, 4, PRESSURE_PLATE).set(3, 1, 4, Kit.stairs(OAK_STAIRS, "west"));
        for (int y = 1; y <= 5; y++) {
            s.set(6, y, 5, Kit.ladder("west"));
        }
        s.set(4, 3, 3, Kit.hangingLantern()).set(3, 3, 5, Kit.wallTorch("north"));
        // The attic: a bed under the slope, a chest, a barrel, a lantern.
        s.bed(2, 5, 4, bed("white"), "north", 0, -1).set(2, 5, 2, Kit.chest("east")).set(6, 5, 2, Kit.barrel());
        s.set(4, 5, 3, Kit.lantern());
        s.connect();
        return s;
    }
}
