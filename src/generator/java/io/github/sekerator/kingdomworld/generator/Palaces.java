package io.github.sekerator.kingdomworld.generator;

import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.BARREL;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.COBBLE;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.DARK_SLAB;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.DARK_STAIRS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.DOOR;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.HANGING_LANTERN;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.LANTERN;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.OAK_LOG;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.OAK_PLANKS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.PANE_X;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.PANE_Z;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.SPRUCE_LOG;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.SPRUCE_PLANKS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.STONE_BRICKS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.STONE_BRICK_STAIRS;

import java.util.ArrayList;
import java.util.List;

/**
 * The palaces of the kingdoms of the world: a great timber hall for a small kingdom and a castle with a courtyard for a
 * large one. The throne of the kingdom stands in the world before its palace is built, in the middle of the ground; the
 * palace is built around it and leaves the throne and the ground under it out, so that the throne stays where it is.
 * Both stand flush with the street: layer 0 replaces the ground, the throne stands on layer 1.
 */
final class Palaces {
    private static final String STONE_BRICK_SLAB = "minecraft:stone_brick_slab";
    private static final String CHISELED = "minecraft:chiseled_stone_bricks";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String DARK_LOG = "minecraft:dark_oak_log";
    private static final String PRISMARINE_STAIRS = "minecraft:dark_prismarine_stairs";
    private static final String PRISMARINE_SLAB = "minecraft:dark_prismarine_slab";
    private static final String CARPET = "minecraft:red_carpet";
    private static final String RED_WOOL = "minecraft:red_wool";
    private static final String YELLOW_WOOL = "minecraft:yellow_wool";
    private static final String BANNER = "minecraft:red_wall_banner[facing=south]";
    private static final String BOOKSHELF = "minecraft:bookshelf";
    private static final String CHAIN = "minecraft:chain[axis=y,waterlogged=false]";
    private static final String BARS_X = "minecraft:iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String GRAVEL = "minecraft:gravel";
    private static final String SMOOTH_STONE = "minecraft:smooth_stone";
    private static final String TABLE_SLAB = "minecraft:spruce_slab[type=top,waterlogged=false]";
    private static final String FENCE = fence(false, false, false, false);
    private static final String FENCE_Z = fence(true, true, false, false);

    // ---------------------------------------------------------------- the great hall

    /** The throne of the great hall, in the middle of its ground. */
    private static final int HALL_THRONE_X = 8;
    private static final int HALL_THRONE_Z = 10;
    /** The wall with the banners behind the throne; the back rooms lie behind it. */
    private static final int HALL_BANNER_Z = 9;
    /** The front wall with the door; the roof overhangs it by one row. */
    private static final int HALL_FRONT_Z = 19;
    /** The two rows of pillars between the nave and the aisles, and the outer walls of the aisles. */
    private static final int NAVE_WEST = 5;
    private static final int NAVE_EAST = 11;
    private static final int AISLE_WEST = 1;
    private static final int AISLE_EAST = 15;
    /** The top of the aisle walls and the eaves of the high roof of the nave. */
    private static final int AISLE_TOP = 4;
    private static final int NAVE_EAVES = 11;

    // ---------------------------------------------------------------- the castle

    /** The throne of the castle, in the middle of its ground. */
    private static final int THRONE_X = 17;
    private static final int THRONE_Z = 17;
    /** The curtain wall: its walk lies on top of this layer, the hoarding of planks on its inner side at the same height. */
    private static final int WALK = 5;
    /** The keep: its walls, the wall behind the throne, the floor of its upper storey and its eaves. */
    private static final int KEEP_X1 = 8;
    private static final int KEEP_X2 = 26;
    private static final int KEEP_Z1 = 4;
    private static final int KEEP_Z2 = 24;
    private static final int THRONE_WALL_Z = 15;
    private static final int UPPER_FLOOR = 8;
    private static final int KEEP_EAVES = 13;
    /** The donjon at the back of the keep, rising over its roof. */
    private static final int DONJON_X1 = 14;
    private static final int DONJON_X2 = 20;
    private static final int DONJON_Z1 = 4;
    private static final int DONJON_Z2 = 10;
    private static final int DONJON_TOP = 24;
    /** The gate passage through the gatehouse, clear from the street to the courtyard. */
    private static final int GATE_X1 = 16;
    private static final int GATE_X2 = 18;
    private static final int GATE_HEIGHT = 4;

    private Palaces() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("great_hall", "defense", "palace", "minecraft:red_carpet", 8, 1, 20, greatHall(),
                "none", 1, ""));
        list.add(new BuildingGenerator.Building("castle", "defense", "palace", "minecraft:stone_bricks", 17, 1, 34, castle(),
                "none", 1, ""));
        return list;
    }

    private static String stairs(String block, String facing) {
        return block + "[facing=" + facing + ",half=bottom,shape=straight,waterlogged=false]";
    }

    private static String upsideDown(String block, String facing) {
        return block + "[facing=" + facing + ",half=top,shape=straight,waterlogged=false]";
    }

    private static String slab(String block) {
        return block + "[type=bottom,waterlogged=false]";
    }

    private static String wallTorch(String facing) {
        return "minecraft:wall_torch[facing=" + facing + "]";
    }

    private static String chest(String facing) {
        return "minecraft:chest[facing=" + facing + ",type=single,waterlogged=false]";
    }

    private static String fence(boolean north, boolean south, boolean east, boolean west) {
        return "minecraft:oak_fence[east=" + east + ",north=" + north + ",south=" + south + ",waterlogged=false,west=" + west + "]";
    }

    /**
     * A lantern hanging on a chain from the roof or the ceiling: the chain from {@code top} down, the lantern under it.
     */
    private static void chandelier(Structure s, int x, int z, int lanternY, int top) {
        s.fill(x, lanternY + 1, z, x, top, z, CHAIN);
        s.set(x, lanternY, z, HANGING_LANTERN);
    }

    /**
     * A lean-to roof over a side aisle: rows of stairs along z rising one block per row from the eave column toward the nave
     * ({@code step} +1 rises to the east, -1 to the west), and the end walls closed under it above the top of the aisle walls.
     */
    private static void leanTo(Structure s, int eaveX, int rows, int step, int z1, int z2, int y, String stairs, String gable,
                               int wallTop, int wallZ1, int wallZ2) {
        for (int row = 0; row < rows; row++) {
            int x = eaveX + row * step;
            int height = y + row;
            s.fill(x, height, z1, x, height, z2, stairs(stairs, step > 0 ? "east" : "west"));
            for (int wall : new int[]{wallZ1, wallZ2}) {
                if (height - 1 > wallTop) {
                    s.fill(x, wallTop + 1, wall, x, height - 1, wall, gable);
                }
            }
        }
    }

    /**
     * A steep tented roof of dark prismarine over a tower: a hip roof whose rings are filled inside, so that no hollow is
     * left under it.
     */
    private static void tentRoof(Structure s, int x1, int z1, int x2, int z2, int y) {
        s.hipRoof(x1, x2, z1, z2, y, PRISMARINE_STAIRS, PRISMARINE_SLAB);
        for (int ring = 0; x1 + ring + 1 <= x2 - ring - 1 && z1 + ring + 1 <= z2 - ring - 1; ring++) {
            s.fill(x1 + ring + 1, y + ring, z1 + ring + 1, x2 - ring - 1, y + ring, z2 - ring - 1, SPRUCE_PLANKS);
        }
    }

    /**
     * A parapet along one line of the outer face of a wall and merlons on every second block of it.
     */
    private static void battlements(Structure s, int x1, int z1, int x2, int z2, int y) {
        s.fill(x1, y, z1, x2, y, z2, STONE_BRICKS);
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if ((x + z) % 2 == 0) {
                    s.set(x, y + 1, z, STONE_BRICKS);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the great hall

    /**
     * The palace of a small kingdom: a great timber hall on a stone footing, built like a basilica. A high nave under a steep
     * roof whose gable with a big window looks at the street, two rows of log pillars, low aisles under lean-to roofs and two
     * tall chimneys. A double door leads along a red carpet to the throne on its dais before a wall hung with banners;
     * behind that wall lie the council room and the treasury.
     */
    static Structure greatHall() {
        Structure s = new Structure(17, 16, 21);
        // The ground: a cobblestone apron all round, planks in the aisles and the back rooms, stone bricks in the nave.
        s.fill(0, 0, 0, 16, 0, 20, COBBLE);
        s.fill(2, 0, 1, 14, 0, 18, SPRUCE_PLANKS);
        s.fill(NAVE_WEST + 1, 0, HALL_THRONE_Z, NAVE_EAST - 1, 0, 18, STONE_BRICKS);
        hallWalls(s);
        hallRoofs(s);
        hallNave(s);
        hallBackRooms(s);
        s.skip(HALL_THRONE_X, 0, HALL_THRONE_Z).skip(HALL_THRONE_X, 1, HALL_THRONE_Z);
        return s;
    }

    /**
     * The walls: the low aisle walls of planks in an oak frame on a cobblestone course, the arcades of log pillars between
     * nave and aisles carrying the high walls of the nave with their windows, and the front and back walls of the nave.
     */
    private static void hallWalls(Structure s) {
        s.walls(AISLE_WEST, 1, 0, AISLE_EAST, AISLE_TOP, HALL_FRONT_Z, OAK_PLANKS);
        s.walls(AISLE_WEST, 1, 0, AISLE_EAST, 1, HALL_FRONT_Z, COBBLE);
        for (int z : new int[]{0, 4, HALL_BANNER_Z, 13, 16, HALL_FRONT_Z}) {
            s.fill(AISLE_WEST, 1, z, AISLE_WEST, AISLE_TOP, z, OAK_LOG + "[axis=y]");
            s.fill(AISLE_EAST, 1, z, AISLE_EAST, AISLE_TOP, z, OAK_LOG + "[axis=y]");
        }
        for (int x : new int[]{AISLE_WEST, AISLE_EAST}) {
            for (int z : new int[]{2, 7, 11, 12, 14, 15, 17, 18}) {
                s.set(x, 2, z, PANE_Z).set(x, 3, z, PANE_Z);
            }
        }
        s.set(3, 2, HALL_FRONT_Z, PANE_X).set(3, 3, HALL_FRONT_Z, PANE_X).set(13, 2, HALL_FRONT_Z, PANE_X).set(13, 3, HALL_FRONT_Z, PANE_X);
        // The high walls of the nave, from the back wall to the front gable.
        for (int x : new int[]{NAVE_WEST, NAVE_EAST}) {
            s.fill(x, AISLE_TOP + 1, 0, x, NAVE_EAVES, HALL_FRONT_Z, OAK_PLANKS);
            s.fill(x, 1, 0, x, NAVE_EAVES, 0, OAK_LOG + "[axis=y]");
            s.fill(x, 1, HALL_FRONT_Z, x, NAVE_EAVES, HALL_FRONT_Z, OAK_LOG + "[axis=y]");
            s.fill(x, NAVE_EAVES, 1, x, NAVE_EAVES, HALL_FRONT_Z - 1, OAK_LOG + "[axis=z]");
            // The arcade: log pillars with a beam over them, open between.
            s.fill(x, AISLE_TOP, HALL_BANNER_Z, x, AISLE_TOP, HALL_FRONT_Z - 1, SPRUCE_LOG + "[axis=z]");
            for (int z : new int[]{HALL_THRONE_Z, 13, 16}) {
                s.fill(x, 1, z, x, AISLE_TOP - 1, z, SPRUCE_LOG + "[axis=y]");
                s.fill(x, AISLE_TOP + 1, z, x, NAVE_EAVES - 1, z, OAK_LOG + "[axis=y]");
            }
            // Windows of the clerestory over the aisle roofs.
            for (int z : new int[]{11, 12, 14, 15, 17, 18}) {
                s.set(x, 9, z, PANE_Z).set(x, 10, z, PANE_Z);
            }
        }
        // The front and back walls of the nave up to the eaves; the gables over them come with the roof.
        for (int z : new int[]{0, HALL_FRONT_Z}) {
            s.fill(NAVE_WEST + 1, 1, z, NAVE_EAST - 1, NAVE_EAVES, z, OAK_PLANKS);
            s.fill(NAVE_WEST + 1, 1, z, NAVE_EAST - 1, 1, z, COBBLE);
        }
        // Two tall chimneys of stone in the aisle walls by the back rooms.
        for (int x : new int[]{AISLE_WEST, AISLE_EAST}) {
            s.fill(x, 1, 5, x, 13, 6, COBBLE);
        }
    }

    /**
     * The roofs: the steep gable roof of the nave with its ridge along the hall, the lean-to roofs of the aisles under it,
     * the big window in the front gable and a small one in the back gable.
     */
    private static void hallRoofs(Structure s) {
        s.gableRoofZ(NAVE_WEST - 1, NAVE_EAST + 1, 0, HALL_FRONT_Z + 1, NAVE_EAVES, DARK_STAIRS, DARK_SLAB, OAK_PLANKS, 0, HALL_FRONT_Z);
        leanTo(s, 0, 5, 1, 0, HALL_FRONT_Z + 1, AISLE_TOP, SPRUCE_STAIRS, OAK_PLANKS, AISLE_TOP, 0, HALL_FRONT_Z);
        leanTo(s, 16, 5, -1, 0, HALL_FRONT_Z + 1, AISLE_TOP, SPRUCE_STAIRS, OAK_PLANKS, AISLE_TOP, 0, HALL_FRONT_Z);
        // The chimneys rise through the lean-to roofs.
        for (int x : new int[]{AISLE_WEST, AISLE_EAST}) {
            s.fill(x, AISLE_TOP + 1, 5, x, 13, 6, COBBLE);
            s.fill(x, 14, 5, x, 14, 6, slab("minecraft:cobblestone_slab"));
        }
        // The great window of the front gable over the door, with a stone sill.
        s.fill(7, 5, HALL_FRONT_Z, 9, 12, HALL_FRONT_Z, PANE_X);
        s.set(8, 13, HALL_FRONT_Z, PANE_X);
        s.fill(7, 4, HALL_FRONT_Z, 9, 4, HALL_FRONT_Z, STONE_BRICKS);
        s.fill(8, 8, 0, 8, 11, 0, PANE_X);
        // The double door of the front, a stone lintel over it and torches on either side.
        s.door(7, 1, HALL_FRONT_Z, DOOR, "north", "left").door(8, 1, HALL_FRONT_Z, DOOR, "north", "right");
        s.set(6, 3, HALL_FRONT_Z, upsideDown(STONE_BRICK_STAIRS, "west")).set(9, 3, HALL_FRONT_Z, upsideDown(STONE_BRICK_STAIRS, "east"));
        s.fill(7, 3, HALL_FRONT_Z, 8, 3, HALL_FRONT_Z, STONE_BRICKS);
        s.set(6, 2, HALL_FRONT_Z + 1, wallTorch("south")).set(9, 2, HALL_FRONT_Z + 1, wallTorch("south"));
    }

    /**
     * The hall proper: the wall of banners behind the throne, the dais, the red carpet from the door, benches in the aisles,
     * lanterns hanging from the ridge and torches in the aisles. The guards stand free on either side of the carpet.
     */
    private static void hallNave(Structure s) {
        // The wall behind the throne: planks in the aisles, stone in the nave up to the roof.
        s.fill(AISLE_WEST + 1, 1, HALL_BANNER_Z, AISLE_EAST - 1, AISLE_TOP, HALL_BANNER_Z, OAK_PLANKS);
        for (int x = NAVE_WEST + 1; x <= NAVE_EAST - 1; x++) {
            int under = NAVE_EAVES + Math.min(x - (NAVE_WEST - 1), NAVE_EAST + 1 - x) - 1;
            s.fill(x, 1, HALL_BANNER_Z, x, under, HALL_BANNER_Z, STONE_BRICKS);
        }
        // Banners of wool: red hangings on either side, the yellow of the crown over the throne, banners hung before them.
        s.fill(6, 3, HALL_BANNER_Z, 6, 8, HALL_BANNER_Z, RED_WOOL).fill(10, 3, HALL_BANNER_Z, 10, 8, HALL_BANNER_Z, RED_WOOL);
        s.fill(8, 4, HALL_BANNER_Z, 8, 9, HALL_BANNER_Z, YELLOW_WOOL);
        s.set(7, 4, HALL_THRONE_Z, BANNER).set(9, 4, HALL_THRONE_Z, BANNER);
        // The doors to the back rooms, left and right of the dais.
        s.door(4, 1, HALL_BANNER_Z, DOOR, "south", "left").door(12, 1, HALL_BANNER_Z, DOOR, "south", "right");
        // The dais: a step of stone brick slabs around the throne.
        for (int x : new int[]{6, 7, 9, 10}) {
            s.set(x, 1, HALL_THRONE_Z, slab(STONE_BRICK_SLAB));
        }
        s.set(7, 1, HALL_THRONE_Z + 1, slab(STONE_BRICK_SLAB)).set(9, 1, HALL_THRONE_Z + 1, slab(STONE_BRICK_SLAB));
        s.fill(HALL_THRONE_X, 1, HALL_THRONE_Z + 1, HALL_THRONE_X, 1, HALL_FRONT_Z - 1, CARPET);
        // Benches along the aisles between the pillars.
        for (int z : new int[]{11, 12, 14, 15, 17, 18}) {
            s.set(AISLE_WEST + 1, 1, z, stairs(SPRUCE_STAIRS, "west"));
            s.set(AISLE_EAST - 1, 1, z, stairs(SPRUCE_STAIRS, "east"));
        }
        for (int z : new int[]{HALL_THRONE_Z, 13, 16}) {
            s.set(AISLE_WEST + 1, 3, z, wallTorch("east")).set(AISLE_EAST - 1, 3, z, wallTorch("west"));
        }
        for (int z : new int[]{12, 15, 18}) {
            chandelier(s, HALL_THRONE_X, z, 10, 14);
        }
    }

    /**
     * Behind the wall of banners: the council room with a table of slabs, benches and bookshelves, and the treasury with
     * chests and barrels behind a grille of iron bars, both under a plank ceiling. The space under the roof over them is
     * closed and lit.
     */
    private static void hallBackRooms(Structure s) {
        s.fill(AISLE_WEST + 1, AISLE_TOP + 1, 1, AISLE_EAST - 1, AISLE_TOP + 1, HALL_BANNER_Z - 1, SPRUCE_PLANKS);
        s.fill(HALL_THRONE_X, 1, 1, HALL_THRONE_X, AISLE_TOP, HALL_BANNER_Z - 1, STONE_BRICKS);
        // The narrow spaces under the lean-to roofs over the back rooms are filled up to the roof.
        for (int z = 1; z <= HALL_BANNER_Z; z++) {
            for (int x : new int[]{2, 3, 4, 12, 13, 14}) {
                int roof = AISLE_TOP + Math.min(x, 16 - x);
                s.fill(x, AISLE_TOP + 1, z, x, roof - 1, z, OAK_PLANKS);
            }
        }
        s.set(HALL_THRONE_X, AISLE_TOP + 2, 4, LANTERN);
        // The council room.
        s.fill(2, 1, 1, 7, 3, 1, BOOKSHELF);
        s.fill(4, 1, 4, 5, 1, 6, TABLE_SLAB);
        for (int z = 4; z <= 6; z++) {
            s.set(3, 1, z, stairs(SPRUCE_STAIRS, "west")).set(6, 1, z, stairs(SPRUCE_STAIRS, "east"));
        }
        s.set(4, 2, 5, LANTERN).set(5, 4, 8, HANGING_LANTERN);
        // The treasury: chests and barrels in the vault behind the grille, more barrels before it.
        s.fill(9, 1, 4, 14, AISLE_TOP, 4, BARS_X);
        s.door(12, 1, 4, DOOR, "south", "left");
        s.set(9, 1, 1, chest("south")).set(11, 1, 1, chest("south")).set(13, 1, 1, chest("south"));
        s.set(10, 1, 1, BARREL).set(12, 1, 1, BARREL).set(14, 1, 1, BARREL).set(14, 1, 2, BARREL).set(9, 1, 2, BARREL);
        s.set(14, 1, 7, BARREL).set(14, 1, 8, BARREL).set(9, 1, 8, BARREL).set(9, 2, 8, BARREL);
        s.set(11, 4, 2, HANGING_LANTERN).set(10, 4, 6, HANGING_LANTERN);
    }

    // ---------------------------------------------------------------- the castle

    /**
     * The palace of a large kingdom: a castle with an inner courtyard. A curtain wall with a walk behind battlements and four
     * corner towers under tented roofs, a gatehouse over a clear passage, and in the middle the keep of two storeys under a
     * steep hip roof with a donjon rising at its back. The throne hall takes the front of the keep's ground floor; the council
     * room and the treasury lie behind the wall of the throne, stairs lead up to a library over them.
     */
    static Structure castle() {
        Structure s = new Structure(35, 30, 35);
        courtyardGround(s);
        curtainWall(s);
        for (int[] corner : new int[][]{{0, 0}, {28, 0}, {0, 28}, {28, 28}}) {
            tower(s, corner[0], corner[1]);
        }
        towerDoors(s);
        gatehouse(s);
        keep(s);
        donjon(s);
        throneHall(s);
        keepRooms(s);
        upperStorey(s);
        courtyard(s);
        s.skip(THRONE_X, 0, THRONE_Z).skip(THRONE_X, 1, THRONE_Z);
        return s;
    }

    /**
     * The ground: cobblestone everywhere, gravel along the passages beside the keep and a road of stone bricks from the
     * gate to the door of the keep.
     */
    private static void courtyardGround(Structure s) {
        s.fill(0, 0, 0, 34, 0, 34, COBBLE);
        s.fill(4, 0, 7, 5, 0, KEEP_Z2, GRAVEL).fill(29, 0, 7, 30, 0, KEEP_Z2, GRAVEL);
        s.fill(9, 0, 2, 25, 0, 2, GRAVEL);
        s.fill(GATE_X1, 0, KEEP_Z2 + 1, GATE_X2, 0, 34, STONE_BRICKS);
        s.fill(GATE_X1 - 1, 0, KEEP_Z2 + 1, GATE_X1 - 1, 0, 29, GRAVEL).fill(GATE_X2 + 1, 0, KEEP_Z2 + 1, GATE_X2 + 1, 0, 29, GRAVEL);
    }

    /**
     * The curtain wall between the towers: two blocks thick, solid up to the walk, a parapet with merlons on its outer edge,
     * a hoarding of planks on corbels along its inner side that widens the walk, and lanterns in the crenels.
     */
    private static void curtainWall(Structure s) {
        s.fill(0, 1, 7, 1, WALK, 27, STONE_BRICKS).fill(33, 1, 7, 34, WALK, 27, STONE_BRICKS);
        s.fill(7, 1, 0, 27, WALK, 1, STONE_BRICKS).fill(7, 1, 33, 27, WALK, 34, STONE_BRICKS);
        s.fill(0, 1, 7, 0, 1, 27, COBBLE).fill(34, 1, 7, 34, 1, 27, COBBLE);
        s.fill(7, 1, 0, 27, 1, 0, COBBLE).fill(7, 1, 34, 27, 1, 34, COBBLE);
        battlements(s, 0, 7, 0, 27, WALK + 1);
        battlements(s, 34, 7, 34, 27, WALK + 1);
        battlements(s, 7, 0, 27, 0, WALK + 1);
        battlements(s, 7, 34, 27, 34, WALK + 1);
        // The hoarding: on the sides and the back along the whole wall, on the front only by the towers, where the stairs
        // up from the yard do not stand.
        s.fill(2, WALK, 7, 2, WALK, 27, SPRUCE_PLANKS).fill(32, WALK, 7, 32, WALK, 27, SPRUCE_PLANKS);
        s.fill(7, WALK, 2, 27, WALK, 2, SPRUCE_PLANKS);
        s.set(7, WALK, 32, SPRUCE_PLANKS).set(27, WALK, 32, SPRUCE_PLANKS);
        for (int i = 8; i <= 26; i += 4) {
            s.set(2, WALK - 1, i, upsideDown(STONE_BRICK_STAIRS, "west")).set(32, WALK - 1, i, upsideDown(STONE_BRICK_STAIRS, "east"));
            s.set(i, WALK - 1, 2, upsideDown(STONE_BRICK_STAIRS, "north"));
        }
        s.set(7, WALK - 1, 32, upsideDown(STONE_BRICK_STAIRS, "south")).set(27, WALK - 1, 32, upsideDown(STONE_BRICK_STAIRS, "south"));
        for (int i : new int[]{11, 17, 23}) {
            s.set(0, WALK + 2, i, LANTERN).set(34, WALK + 2, i, LANTERN).set(i, WALK + 2, 0, LANTERN);
        }
        s.set(9, WALK + 2, 34, LANTERN).set(25, WALK + 2, 34, LANTERN);
    }

    /**
     * A corner tower of seven by seven: solid below the walk, a guard room at the height of the walk with arrow slits, a plank
     * ceiling and a tented roof of dark prismarine over it, quoins of cobblestone at its edges.
     */
    private static void tower(Structure s, int x0, int z0) {
        int x1 = x0 + 6;
        int z1 = z0 + 6;
        s.fill(x0, 1, z0, x1, 10, z1, STONE_BRICKS);
        s.walls(x0, 1, z0, x1, 1, z1, COBBLE);
        for (int[] edge : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            s.fill(edge[0], 1, edge[1], edge[0], 10, edge[1], COBBLE);
        }
        s.fill(x0 + 1, WALK, z0 + 1, x1 - 1, WALK, z1 - 1, SPRUCE_PLANKS);
        s.fill(x0 + 1, WALK + 1, z0 + 1, x1 - 1, 9, z1 - 1, Structure.AIR);
        s.fill(x0 + 1, 10, z0 + 1, x1 - 1, 10, z1 - 1, SPRUCE_PLANKS);
        for (int y : new int[]{7, 8}) {
            s.set(x0 + 3, y, z0, PANE_X).set(x0 + 3, y, z1, PANE_X).set(x0, y, z0 + 3, PANE_Z).set(x1, y, z0 + 3, PANE_Z);
        }
        s.set(x0 + 3, WALK + 1, z0 + 3, LANTERN);
        s.set(x0 + 1, WALK + 1, z0 + 1, BARREL).set(x1 - 1, WALK + 1, z1 - 1, BARREL);
        tentRoof(s, x0, z0, x1, z1, 11);
    }

    /**
     * The doors from the walk of the curtain wall into the towers, two to every tower.
     */
    private static void towerDoors(Structure s) {
        int y = WALK + 1;
        s.door(1, y, 6, DOOR, "south", "left").door(6, y, 1, DOOR, "east", "left");
        s.door(33, y, 6, DOOR, "south", "left").door(28, y, 1, DOOR, "west", "left");
        s.door(1, y, 28, DOOR, "north", "left").door(6, y, 33, DOOR, "east", "left");
        s.door(33, y, 28, DOOR, "north", "left").door(28, y, 33, DOOR, "west", "left");
    }

    /**
     * The gatehouse in the middle of the front wall, taller than the wall: a passage three wide and four high under arches,
     * lit by lanterns in niches, a guard room over it with doors to the walk, and a platform with battlements on top.
     */
    private static void gatehouse(Structure s) {
        s.fill(13, 1, 30, 21, 9, 34, STONE_BRICKS);
        s.walls(13, 1, 30, 21, 1, 34, COBBLE);
        s.fill(GATE_X1, 1, 30, GATE_X2, GATE_HEIGHT, 34, Structure.AIR);
        // The arches over both mouths of the passage, beams of spruce over it between them.
        for (int z : new int[]{30, 34}) {
            s.set(GATE_X1, GATE_HEIGHT + 1, z, upsideDown(STONE_BRICK_STAIRS, "west"));
            s.set(GATE_X2, GATE_HEIGHT + 1, z, upsideDown(STONE_BRICK_STAIRS, "east"));
            s.set(THRONE_X, GATE_HEIGHT + 1, z, CHISELED);
        }
        s.fill(GATE_X1, GATE_HEIGHT + 1, 31, GATE_X2, GATE_HEIGHT + 1, 33, SPRUCE_LOG + "[axis=x]");
        s.set(GATE_X1 - 1, 2, 32, LANTERN).set(GATE_X2 + 1, 2, 32, LANTERN);
        // The guard room.
        s.fill(14, WALK, 31, 20, WALK, 33, SPRUCE_PLANKS);
        s.fill(GATE_X1, WALK, 31, GATE_X2, WALK, 33, SPRUCE_LOG + "[axis=x]");
        s.fill(14, WALK + 1, 31, 20, 9, 33, Structure.AIR);
        s.door(13, WALK + 1, 33, DOOR, "east", "left").door(21, WALK + 1, 33, DOOR, "west", "left");
        for (int x : new int[]{15, 17, 19}) {
            s.set(x, 7, 34, PANE_X).set(x, 8, 34, PANE_X).set(x, 7, 30, PANE_X).set(x, 8, 30, PANE_X);
        }
        s.set(14, WALK + 1, 31, LANTERN).set(20, WALK + 1, 31, BARREL).set(20, WALK + 2, 31, BARREL);
        s.set(14, WALK + 1, 32, chest("east"));
        s.set(17, 9, 32, HANGING_LANTERN);
        // The platform on top with its parapet and merlons.
        s.fill(13, 10, 30, 21, 10, 34, STONE_BRICKS);
        s.walls(13, 11, 30, 21, 11, 34, STONE_BRICKS);
        for (int x = 13; x <= 21; x++) {
            for (int z = 30; z <= 34; z++) {
                boolean edge = x == 13 || x == 21 || z == 30 || z == 34;
                if (edge && (x + z) % 2 == 1) {
                    s.set(x, 12, z, STONE_BRICKS);
                }
            }
        }
        s.set(15, 11, 32, LANTERN).set(19, 11, 32, LANTERN);
    }

    /**
     * The shell of the keep: stone walls on a cobblestone course with quoins, the floor of the upper storey, a ring of dark
     * oak under the eaves, the steep hip roof, the great door under an arch and the windows of both storeys.
     */
    private static void keep(Structure s) {
        s.fill(KEEP_X1 + 1, 0, KEEP_Z1 + 1, KEEP_X2 - 1, 0, KEEP_Z2 - 1, SPRUCE_PLANKS);
        s.walls(KEEP_X1, 1, KEEP_Z1, KEEP_X2, KEEP_EAVES - 1, KEEP_Z2, STONE_BRICKS);
        s.walls(KEEP_X1, 1, KEEP_Z1, KEEP_X2, 1, KEEP_Z2, COBBLE);
        s.walls(KEEP_X1, UPPER_FLOOR, KEEP_Z1, KEEP_X2, UPPER_FLOOR, KEEP_Z2, COBBLE);
        for (int[] edge : new int[][]{{KEEP_X1, KEEP_Z1}, {KEEP_X2, KEEP_Z1}, {KEEP_X1, KEEP_Z2}, {KEEP_X2, KEEP_Z2}}) {
            s.fill(edge[0], 1, edge[1], edge[0], KEEP_EAVES - 1, edge[1], COBBLE);
        }
        s.fill(KEEP_X1 + 1, UPPER_FLOOR, KEEP_Z1 + 1, KEEP_X2 - 1, UPPER_FLOOR, KEEP_Z2 - 1, SPRUCE_PLANKS);
        s.beam(KEEP_X1, KEEP_Z1, KEEP_X2, KEEP_Z2, KEEP_EAVES - 1, DARK_LOG);
        s.hipRoof(KEEP_X1 - 1, KEEP_X2 + 1, KEEP_Z1 - 1, KEEP_Z2 + 1, KEEP_EAVES, DARK_STAIRS, DARK_SLAB);
        // The great door: open, three wide, under an arch.
        s.fill(GATE_X1, 1, KEEP_Z2, GATE_X2, 3, KEEP_Z2, Structure.AIR);
        s.set(GATE_X1, 4, KEEP_Z2, upsideDown(STONE_BRICK_STAIRS, "west")).set(GATE_X2, 4, KEEP_Z2, upsideDown(STONE_BRICK_STAIRS, "east"));
        s.set(THRONE_X, 4, KEEP_Z2, CHISELED);
        // Windows: tall ones in the throne hall, smaller ones in the back rooms, a row in the upper storey.
        for (int y = 2; y <= 5; y++) {
            for (int z : new int[]{18, 19, 21, 22}) {
                s.set(KEEP_X1, y, z, PANE_Z).set(KEEP_X2, y, z, PANE_Z);
            }
            for (int x : new int[]{11, 12, 22, 23}) {
                s.set(x, y, KEEP_Z2, PANE_X);
            }
        }
        for (int y = 2; y <= 4; y++) {
            for (int z : new int[]{7, 11}) {
                s.set(KEEP_X1, y, z, PANE_Z).set(KEEP_X2, y, z, PANE_Z);
            }
            s.set(11, y, KEEP_Z1, PANE_X).set(23, y, KEEP_Z1, PANE_X);
        }
        for (int y = 10; y <= 11; y++) {
            for (int z : new int[]{7, 11, 15, 19, 22}) {
                s.set(KEEP_X1, y, z, PANE_Z).set(KEEP_X2, y, z, PANE_Z);
            }
            for (int x : new int[]{11, 14, 17, 20, 23}) {
                s.set(x, y, KEEP_Z2, PANE_X);
            }
            for (int x : new int[]{10, 12, 22, 24}) {
                s.set(x, y, KEEP_Z1, PANE_X);
            }
        }
        // Torches on the outer walls of the keep light the courtyard around it.
        for (int z : new int[]{5, 9, 14, 20, 24}) {
            s.set(KEEP_X1 - 1, 3, z, wallTorch("west")).set(KEEP_X2 + 1, 3, z, wallTorch("east"));
        }
        for (int x : new int[]{9, 14, 20, 25}) {
            s.set(x, 3, KEEP_Z1 - 1, wallTorch("north"));
        }
        s.set(14, 3, KEEP_Z2 + 1, wallTorch("south")).set(20, 3, KEEP_Z2 + 1, wallTorch("south"));
    }

    /**
     * The donjon at the back of the keep: its walls stand on the upper floor and rise through the roof of the keep to a
     * tented roof of dark prismarine, the tallest point of the castle. A study opens to the upper storey; above its ceiling
     * the shaft of the tower is lit.
     */
    private static void donjon(Structure s) {
        s.walls(DONJON_X1, UPPER_FLOOR + 1, DONJON_Z1, DONJON_X2, DONJON_TOP, DONJON_Z2, STONE_BRICKS);
        s.fill(DONJON_X1 + 1, UPPER_FLOOR + 1, DONJON_Z1 + 1, DONJON_X2 - 1, DONJON_TOP, DONJON_Z2 - 1, Structure.AIR);
        for (int[] edge : new int[][]{{DONJON_X1, DONJON_Z1}, {DONJON_X2, DONJON_Z1}, {DONJON_X1, DONJON_Z2}, {DONJON_X2, DONJON_Z2}}) {
            s.fill(edge[0], KEEP_EAVES, edge[1], edge[0], DONJON_TOP, edge[1], COBBLE);
        }
        s.fill(DONJON_X1 + 1, KEEP_EAVES, DONJON_Z1 + 1, DONJON_X2 - 1, KEEP_EAVES, DONJON_Z2 - 1, SPRUCE_PLANKS);
        s.set(THRONE_X, KEEP_EAVES + 1, 7, LANTERN);
        for (int y = 19; y <= 21; y++) {
            s.set(THRONE_X, y, DONJON_Z1, PANE_X).set(THRONE_X, y, DONJON_Z2, PANE_X);
            s.set(DONJON_X1, y, 7, PANE_Z).set(DONJON_X2, y, 7, PANE_Z);
        }
        tentRoof(s, DONJON_X1 - 1, DONJON_Z1 - 1, DONJON_X2 + 1, DONJON_Z2 + 1, DONJON_TOP + 1);
        // The study: an open doorway from the upper storey, bookshelves and a lectern.
        s.fill(THRONE_X, UPPER_FLOOR + 1, DONJON_Z2, THRONE_X, UPPER_FLOOR + 2, DONJON_Z2, Structure.AIR);
        s.fill(DONJON_X1 + 1, UPPER_FLOOR + 1, DONJON_Z1 + 1, DONJON_X2 - 1, UPPER_FLOOR + 2, DONJON_Z1 + 1, BOOKSHELF);
        s.set(DONJON_X1 + 1, UPPER_FLOOR + 1, 7, "minecraft:lectern[facing=east,has_book=false,powered=false]");
        s.set(DONJON_X2 - 1, UPPER_FLOOR + 1, 8, LANTERN);
    }

    /**
     * The throne hall on the ground floor of the keep: the wall behind the throne hung with wool and banners, the dais of
     * stone brick slabs, the carpet from the great door, two pairs of spruce pillars with beams, chandeliers of chains and
     * lanterns. The guards stand free on either side of the carpet.
     */
    private static void throneHall(Structure s) {
        s.fill(KEEP_X1 + 1, 0, THRONE_WALL_Z + 1, KEEP_X2 - 1, 0, KEEP_Z2 - 1, SMOOTH_STONE);
        s.walls(KEEP_X1 + 1, 0, THRONE_WALL_Z + 1, KEEP_X2 - 1, 0, KEEP_Z2 - 1, STONE_BRICKS);
        s.fill(THRONE_X - 1, 0, THRONE_WALL_Z + 1, THRONE_X + 1, 0, KEEP_Z2 - 1, STONE_BRICKS);
        s.fill(KEEP_X1 + 1, 1, THRONE_WALL_Z, KEEP_X2 - 1, UPPER_FLOOR - 1, THRONE_WALL_Z, STONE_BRICKS);
        // Banners of wool on the wall: red hangings on either side, the yellow of the crown over the throne.
        s.fill(15, 2, THRONE_WALL_Z, 15, 6, THRONE_WALL_Z, RED_WOOL).fill(19, 2, THRONE_WALL_Z, 19, 6, THRONE_WALL_Z, RED_WOOL);
        s.fill(12, 3, THRONE_WALL_Z, 12, 6, THRONE_WALL_Z, RED_WOOL).fill(22, 3, THRONE_WALL_Z, 22, 6, THRONE_WALL_Z, RED_WOOL);
        s.fill(THRONE_X, 4, THRONE_WALL_Z, THRONE_X, 7, THRONE_WALL_Z, YELLOW_WOOL);
        s.set(14, 4, THRONE_WALL_Z + 1, BANNER).set(20, 4, THRONE_WALL_Z + 1, BANNER);
        // The doors to the back rooms.
        s.door(11, 1, THRONE_WALL_Z, DOOR, "south", "left").door(23, 1, THRONE_WALL_Z, DOOR, "south", "right");
        // The dais around the throne.
        s.fill(13, 1, THRONE_WALL_Z + 1, 21, 1, THRONE_WALL_Z + 1, slab(STONE_BRICK_SLAB));
        s.fill(14, 1, THRONE_Z, 20, 1, THRONE_Z, slab(STONE_BRICK_SLAB));
        s.set(16, 1, THRONE_Z + 1, slab(STONE_BRICK_SLAB)).set(18, 1, THRONE_Z + 1, slab(STONE_BRICK_SLAB));
        s.fill(THRONE_X, 1, THRONE_Z + 1, THRONE_X, 1, KEEP_Z2, CARPET);
        // Pillars of spruce with beams across the hall under the ceiling.
        for (int z : new int[]{18, 21}) {
            s.fill(KEEP_X1 + 1, UPPER_FLOOR - 1, z, KEEP_X2 - 1, UPPER_FLOOR - 1, z, SPRUCE_LOG + "[axis=x]");
            for (int x : new int[]{11, 23}) {
                s.fill(x, 1, z, x, UPPER_FLOOR - 2, z, SPRUCE_LOG + "[axis=y]");
            }
        }
        for (int[] spot : new int[][]{{13, 20}, {21, 20}, {13, 23}, {21, 23}, {THRONE_X, 20}}) {
            chandelier(s, spot[0], spot[1], 5, UPPER_FLOOR - 1);
        }
    }

    /**
     * Behind the throne wall: the council room with its table, benches and bookshelves and the stairs up, and the treasury
     * with chests and barrels behind a grille of iron bars.
     */
    private static void keepRooms(Structure s) {
        s.fill(THRONE_X, 1, KEEP_Z1 + 1, THRONE_X, UPPER_FLOOR - 1, THRONE_WALL_Z - 1, STONE_BRICKS);
        // The council room.
        s.fill(10, 1, KEEP_Z1 + 1, 16, 3, KEEP_Z1 + 1, BOOKSHELF);
        s.fill(12, 1, 9, 14, 1, 11, TABLE_SLAB);
        for (int z = 9; z <= 11; z++) {
            s.set(11, 1, z, stairs(SPRUCE_STAIRS, "west")).set(15, 1, z, stairs(SPRUCE_STAIRS, "east"));
        }
        s.set(13, 2, 10, LANTERN).set(13, 7, 7, HANGING_LANTERN).set(13, 7, 13, HANGING_LANTERN);
        // The stairs up along the west wall, on a wall of stone, through a hole in the floor above.
        for (int k = 0; k < UPPER_FLOOR; k++) {
            int z = 13 - k;
            if (k > 0) {
                s.fill(KEEP_X1 + 1, 1, z, KEEP_X1 + 1, k, z, STONE_BRICKS);
            }
            s.set(KEEP_X1 + 1, 1 + k, z, stairs(STONE_BRICK_STAIRS, "north"));
        }
        s.fill(KEEP_X1 + 1, UPPER_FLOOR, 7, KEEP_X1 + 1, UPPER_FLOOR, 12, Structure.AIR);
        // The treasury: the vault behind the grille with its door, a counting desk before it.
        s.fill(18, 1, 9, 25, UPPER_FLOOR - 1, 9, BARS_X);
        s.door(23, 1, 9, DOOR, "south", "left");
        for (int x = 18; x <= 25; x++) {
            s.set(x, 1, KEEP_Z1 + 1, x % 2 == 0 ? chest("south") : BARREL);
        }
        s.set(19, 2, KEEP_Z1 + 1, BARREL).set(25, 2, KEEP_Z1 + 1, BARREL).set(25, 1, 7, BARREL).set(18, 1, 7, BARREL);
        s.set(19, 1, 12, "minecraft:lectern[facing=south,has_book=false,powered=false]");
        s.set(25, 1, 13, BARREL).set(25, 1, 14, BARREL).set(24, 1, 14, BARREL).set(18, 1, 14, BARREL);
        s.set(21, 7, 7, HANGING_LANTERN).set(21, 7, 12, HANGING_LANTERN);
    }

    /**
     * The upper storey of the keep, open to the timbers of the roof: a library with bookshelves between the windows, two
     * reading tables with benches and lanterns, a railing around the stairs.
     */
    private static void upperStorey(Structure s) {
        int y = UPPER_FLOOR + 1;
        s.fill(KEEP_X1 + 2, y, 7, KEEP_X1 + 2, y, 12, FENCE_Z);
        s.set(KEEP_X1 + 2, y, 13, fence(true, false, false, true)).set(KEEP_X1 + 1, y, 13, fence(false, false, true, false));
        for (int z : new int[]{14, 16, 17, 18, 20, 21, 23}) {
            s.fill(KEEP_X1 + 1, y, z, KEEP_X1 + 1, y + 2, z, BOOKSHELF);
        }
        for (int z : new int[]{5, 6, 8, 9, 10, 12, 13, 14, 16, 17, 18, 20, 21, 23}) {
            s.fill(KEEP_X2 - 1, y, z, KEEP_X2 - 1, y + 2, z, BOOKSHELF);
        }
        for (int x : new int[]{13, 21}) {
            s.fill(x, y, 17, x + 1, y, 18, TABLE_SLAB);
            for (int z = 17; z <= 18; z++) {
                s.set(x - 1, y, z, stairs(SPRUCE_STAIRS, "west")).set(x + 2, y, z, stairs(SPRUCE_STAIRS, "east"));
            }
            s.set(x, y + 1, 17, LANTERN);
        }
        s.set(12, y, 6, LANTERN).set(23, y, 6, LANTERN).set(THRONE_X, y, 13, LANTERN).set(12, y, 22, LANTERN).set(23, y, 22, LANTERN);
    }

    /**
     * The courtyard: two flights of stone stairs up to the walk by the gatehouse, a well head with a roof, barrels and a hay
     * stack, lamp posts on either side of the road.
     */
    private static void courtyard(Structure s) {
        // The flights up to the walk: five steps each, rising away from the gatehouse.
        for (int k = 0; k <= WALK - 1; k++) {
            int west = 12 - k;
            int east = 22 + k;
            if (k > 0) {
                s.fill(west, 1, 32, west, k, 32, STONE_BRICKS).fill(east, 1, 32, east, k, 32, STONE_BRICKS);
            }
            s.set(west, 1 + k, 32, stairs(STONE_BRICK_STAIRS, "west")).set(east, 1 + k, 32, stairs(STONE_BRICK_STAIRS, "east"));
        }
        // The well head: a ring of stone around water standing in a cauldron, a roof of slabs on four posts.
        s.walls(9, 1, 26, 11, 1, 28, COBBLE);
        s.set(10, 1, 27, "minecraft:water_cauldron[level=3]");
        for (int[] post : new int[][]{{9, 26}, {11, 26}, {9, 28}, {11, 28}}) {
            s.fill(post[0], 2, post[1], post[0], 3, post[1], FENCE);
        }
        s.fill(9, 4, 26, 11, 4, 28, slab(SPRUCE_SLAB));
        s.set(10, 3, 27, HANGING_LANTERN);
        // Stores in the yard.
        s.set(23, 1, 27, BARREL).set(24, 1, 27, BARREL).set(23, 2, 27, BARREL).set(24, 1, 28, BARREL);
        s.set(25, 1, 29, HAY).set(26, 1, 29, HAY).set(26, 1, 28, HAY).set(26, 2, 29, HAY);
        // Lamp posts on either side of the road.
        for (int[] post : new int[][]{{14, 27}, {20, 27}}) {
            s.fill(post[0], 1, post[1], post[0], 2, post[1], FENCE);
            s.set(post[0], 3, post[1], LANTERN);
        }
    }
}
