package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The field camp of an army of the classic style, the stone and half-timbered style of a kingdom of central Europe: the long
 * striped tent of the soldiers, a training ground behind a palisade, the store of the army in a half-timbered shed under a
 * shingle roof, and a watch tower of stone and timber under a slate needle. Each takes the place of a plain building of a
 * camp (the barracks hall, the drill yard, the barn and the timber watch tower) on the same ground, with its front to the
 * same side and with what counts in the game: twelve bunks and the barracks block in the tent, neither bed nor post of work
 * in the yard, the storehouse blocks and more than twelve chests and barrels in the store.
 *
 * <p>They are drawn with the blocks that stand for the parts of a look, so that the cloth of the tents, the pennants and the
 * banners takes the colour of the kingdom, the timber its wood, the stone its stone, the roofs and the needle of the tower
 * its roofs; the canvas between the stripes is white wool, which stays.
 */
final class ClassicCamp {
    private static final String AIR = Structure.AIR;
    private static final String SPRUCE_LOG = "minecraft:spruce_log";
    private static final String STRIPPED_SPRUCE = "minecraft:stripped_spruce_log";
    private static final String SPRUCE_PLANKS = "minecraft:spruce_planks";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String DOOR = "minecraft:spruce_door";
    private static final String HATCH = "minecraft:oak_trapdoor";
    private static final String CANVAS = "minecraft:white_wool";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String COARSE = "minecraft:coarse_dirt";
    private static final String GRAVEL = "minecraft:gravel";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String TARGET = "minecraft:target[power=0]";
    private static final String CAULDRON = "minecraft:water_cauldron[level=3]";
    private static final String BARRACKS = "kingdomworld:barracks[facing=south]";
    private static final String STOREHOUSE = "kingdomworld:storehouse";

    private ClassicCamp() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("classic_camp_barracks", "defense", "barracks", "kingdomworld:barracks", 6, 0, 10,
                barracks(), "barracks", 1, "").params("\"bunks\": 12")
                .at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("classic_camp_yard", "defense", "yard", "minecraft:target", 6, 1, 10,
                yard(), "none", 1, "")
                .at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("classic_camp_store", "farming", "warehouse", "kingdomworld:storehouse", 6, 1, 10,
                store(), "warehouse", 1, "").params("\"containers\": 12")
                .at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("classic_camp_tower", "defense", "tower", "minecraft:oak_fence", 3, 0, 6,
                tower(), "tower", 1, "")
                .at("door", 3, 1, 6));
        return list;
    }

    // ---------------------------------------------------------------- shared shapes

    private static String post() {
        return Kit.log(SPRUCE_LOG, "y");
    }

    private static String fence() {
        return Kit.fence("spruce");
    }

    /**
     * A rack of weapons: a grindstone.
     */
    private static String rack(String facing) {
        return "minecraft:grindstone[face=floor,facing=" + facing + "]";
    }

    /**
     * Whether a column of a striped canvas is of the cloth of the kingdom: stripes two wide from a white one in the middle of
     * the barracks tent.
     */
    private static boolean red(int x) {
        int d = Math.abs(x - 6) % 4;
        return d == 1 || d == 2;
    }

    private static String crate() {
        return "minecraft:stripped_spruce_wood[axis=y]";
    }

    /**
     * Quoins on a corner of a wall: the corner column of rough stone, and on every second layer one more block of it on
     * both faces.
     *
     * @param dx the way along x from the corner into its wall (+1 or -1)
     * @param dz the way along z from the corner into its wall (+1 or -1)
     */
    private static void quoins(Structure s, int x, int z, int dx, int dz, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            s.set(x, y, z, Kit.ROUGH);
            if ((y - y1) % 2 == 0) {
                s.set(x + dx, y, z, Kit.ROUGH).set(x, y, z + dz, Kit.ROUGH);
            }
        }
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
     * A gable roof whose ridge runs along z, its gable to the front: rows of stairs rising from the west eave {@code x1} and
     * the east eave {@code x2}; the first {@code steep} rows above the eaves climb two layers each over a full block of the
     * roof, the rows above climb one, and an odd width is closed with a ridge of slabs.
     *
     * @return for every column from x1 to x2 the lowest layer the roof takes in it
     */
    private static int[] roofZ(Structure s, int x1, int x2, int z1, int z2, int y, int steep, Kit.Roof roof) {
        int[] under = new int[x2 - x1 + 1];
        int height = y;
        for (int row = 0; x1 + row <= x2 - row; row++) {
            boolean steepRow = row > 0 && row <= steep;
            if (row > 0) {
                height += steepRow ? 2 : 1;
            }
            int west = x1 + row;
            int east = x2 - row;
            if (steepRow) {
                s.fill(west, height - 1, z1, west, height - 1, z2, roof.block());
                s.fill(east, height - 1, z1, east, height - 1, z2, roof.block());
            }
            if (west < east) {
                s.fill(west, height, z1, west, height, z2, Kit.stairs(roof.stairs(), "east"));
                s.fill(east, height, z1, east, height, z2, Kit.stairs(roof.stairs(), "west"));
            } else {
                s.fill(west, height, z1, west, height, z2, Kit.slab(roof.slab()));
            }
            under[west - x1] = steepRow ? height - 1 : height;
            under[east - x1] = under[west - x1];
        }
        return under;
    }

    /**
     * Closes the gable of a roof along z at the wall {@code z}: every column between the eaves from {@code y} up to the
     * underside of the roof, where nothing stands yet.
     */
    private static void gableAtZ(Structure s, int x1, int[] under, int z, int y, String state) {
        for (int i = 1; i < under.length - 1; i++) {
            for (int h = y; h < under[i]; h++) {
                if (!s.solid(x1 + i, h, z)) {
                    s.set(x1 + i, h, z, state);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the soldiers' tent

    /**
     * The tent of the soldiers, in place of the barracks hall: a long tent of canvas on a deck of planks over a footing of
     * rough stone, its roof striped in the cloth of the kingdom and white from the eaves to the ridge, a valance of banners
     * hanging along the eaves, the ridge pole running out past both gables on poles with the pennants of the kingdom and a
     * taller pennant over the middle pole, and a fly on two poles over the entrance. Inside, twelve bunks with shelves over
     * their heads, six against the back on both sides of the barracks block with its banner and two racks, six against the
     * front; between them a table round the middle pole with benches, candles and two more racks, two lanterns from the
     * ridge, and a chest and a barrel of the soldiers by the entrance. Stakes with lanterns at the corners, firewood and a
     * wash basin outside.
     */
    static Structure barracks() {
        Structure s = new Structure(13, 11, 11);
        // The deck on its footing, a step up to the entrance.
        s.fill(1, 0, 1, 11, 0, 9, Kit.ROUGH);
        s.fill(2, 0, 2, 10, 0, 8, SPRUCE_PLANKS);
        s.set(6, 0, 9, SPRUCE_PLANKS);
        s.set(6, 0, 10, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.set(5, 0, 10, Kit.ROUGH).set(7, 0, 10, Kit.ROUGH);
        // The canvas: walls two high, then the roof one row up for every row in from the eaves, striped across.
        for (int z = 1; z <= 9; z++) {
            int roof = 3 + Math.min(z - 1, 9 - z);
            for (int x = 1; x <= 11; x++) {
                s.set(x, roof, z, red(x) ? Kit.CLOTH : CANVAS);
            }
            s.fill(1, 1, z, 1, roof - 1, z, CANVAS);
            s.fill(11, 1, z, 11, roof - 1, z, CANVAS);
        }
        s.fill(1, 1, 1, 11, 2, 1, CANVAS).fill(1, 1, 9, 11, 2, 9, CANVAS);
        // Poles of the frame at the corners and on both sides of the entrance.
        for (int[] pole : new int[][]{{1, 1}, {11, 1}, {1, 9}, {11, 9}, {5, 9}, {7, 9}}) {
            s.fill(pole[0], 1, pole[1], pole[0], 2, pole[1], post());
        }
        s.fill(6, 1, 9, 6, 2, 9, AIR);
        // The fly over the entrance on two poles, striped like the roof.
        s.fill(5, 1, 10, 5, 2, 10, fence()).fill(7, 1, 10, 7, 2, 10, fence());
        for (int x = 5; x <= 7; x++) {
            s.set(x, 3, 10, red(x) ? Kit.CLOTH : CANVAS);
        }
        // The valance along the eaves: a banner under every stripe, white under the cloth, the cloth under the white.
        for (int x = 1; x <= 11; x++) {
            String color = red(x) ? "white" : "red";
            s.set(x, 3, 0, Kit.wallBanner(color, "north"));
            if (x < 5 || x > 7) {
                s.set(x, 3, 10, Kit.wallBanner(color, "south"));
            }
        }
        // The ridge pole runs out past the gables onto poles of their own, each with a pennant; the middle pole of the tent
        // stands through the ridge with a taller one.
        for (int x : new int[]{0, 12}) {
            s.fill(x, 0, 5, x, 6, 5, fence());
            s.set(x, 7, 5, Kit.log(STRIPPED_SPRUCE, "x"));
            s.set(x, 8, 5, fence()).set(x, 9, 5, Kit.banner("red", 0));
        }
        s.fill(6, 1, 5, 6, 6, 5, fence());
        s.set(6, 7, 5, post());
        s.fill(6, 8, 5, 6, 9, 5, fence());
        s.set(6, 10, 5, Kit.banner("red", 0));
        // Twelve bunks, a shelf over the head of each.
        for (int x : new int[]{2, 3, 4, 8, 9, 10}) {
            Furnish.bed(s, x, 1, 3, "north", "red");
            Furnish.bed(s, x, 1, 7, "south", "red");
            s.set(x, 3, 2, Kit.trapdoor(HATCH, "south", true));
            s.set(x, 3, 8, Kit.trapdoor(HATCH, "north", true));
        }
        // The barracks block at the back under the banner of the company, a rack on either side of it.
        s.set(6, 1, 2, BARRACKS);
        s.set(5, 1, 2, rack("south")).set(7, 1, 2, rack("south"));
        s.set(6, 3, 2, Kit.wallBanner("red", "south"));
        // The table round the middle pole with its benches and candles, racks at both ends of the room.
        for (int x : new int[]{4, 5, 7, 8}) {
            s.set(x, 1, 5, Kit.topSlab(SPRUCE_SLAB));
            s.set(x, 1, 4, Kit.stairs(SPRUCE_STAIRS, "north"));
            s.set(x, 1, 6, Kit.stairs(SPRUCE_STAIRS, "south"));
        }
        s.set(4, 2, 5, Furnish.candles(2)).set(8, 2, 5, Furnish.candles(3));
        s.set(2, 1, 5, rack("east")).set(10, 1, 5, rack("west"));
        // The chest and the barrel of the soldiers by the entrance.
        s.set(5, 1, 8, Kit.chest("east")).set(7, 1, 8, Kit.barrel());
        // Lanterns on chains from the ridge.
        for (int x : new int[]{3, 9}) {
            s.set(x, 6, 5, Kit.chain()).set(x, 5, 5, Kit.hangingLantern());
        }
        // Stakes with lanterns at the corners, firewood under the east gable and a wash basin by the west one.
        for (int[] stake : new int[][]{{0, 0}, {12, 0}, {0, 10}, {12, 10}}) {
            s.set(stake[0], 0, stake[1], fence()).set(stake[0], 1, stake[1], Kit.lantern());
        }
        s.fill(12, 0, 2, 12, 1, 3, Kit.log(SPRUCE_LOG, "z"));
        s.set(12, 0, 7, Kit.log(SPRUCE_LOG, "z"));
        s.set(0, 0, 7, CAULDRON);
        s.set(0, 0, 3, crate());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the training ground

    /**
     * The training ground of the camp, in place of the drill yard and level with the field: a palisade of spruce stakes at
     * the back as the butt behind three targets on bales of hay, with pennant poles at its corners and lanterns on top; low
     * rails on stone posts with lanterns along the sides and the front; a gate of two posts under a beam with lanterns and
     * the banner of the kingdom. Inside, a ring of stones with a pell in the middle for sword work, two dummies with arms and
     * tabards, a weapon corner under a striped awning with racks, an anvil, a barrel of arrows and a crate, and an officers'
     * bench with a wash basin and a standard.
     */
    static Structure yard() {
        Structure s = new Structure(13, 7, 11);
        // The ground: beaten earth, worn gravel before the targets and on the way in, a ring of stones in the middle.
        s.fill(0, 0, 0, 12, 0, 10, COARSE);
        s.fill(2, 0, 1, 10, 0, 2, GRAVEL);
        s.fill(5, 0, 8, 7, 0, 10, GRAVEL);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (Kit.onRim(dx, dz, 5)) {
                    s.set(6 + dx, 0, 5 + dz, Kit.ROUGH);
                }
            }
        }
        s.set(1, 0, 3, GRASS).set(11, 0, 6, GRASS).set(9, 0, 8, GRASS).set(3, 0, 4, GRASS);
        s.fill(0, 0, 0, 12, 0, 0, Kit.ROUGH);
        // The palisade at the back: stakes three high, every second one pointed higher, pennant poles at the corners.
        for (int x = 0; x <= 12; x++) {
            s.fill(x, 1, 0, x, 3, 0, post());
            if (x % 2 == 1) {
                s.set(x, 4, 0, fence());
            }
        }
        for (int x : new int[]{0, 12}) {
            s.set(x, 4, 0, post()).set(x, 5, 0, fence()).set(x, 6, 0, Kit.banner("red", 0));
        }
        for (int x : new int[]{2, 6, 10}) {
            s.set(x, 4, 0, Kit.lantern());
        }
        // The rails on the sides and the front, on stone posts with lanterns.
        for (int x : new int[]{0, 12}) {
            s.fill(x, 1, 1, x, 1, 9, fence());
            for (int z : new int[]{4, 10}) {
                s.set(x, 1, z, Kit.ROUGH).set(x, 2, z, Kit.ROUGH_FENCE).set(x, 3, z, Kit.lantern());
            }
        }
        s.fill(1, 1, 10, 3, 1, 10, fence()).fill(9, 1, 10, 11, 1, 10, fence());
        // The gate: two posts under a beam, lanterns hanging from it, the banner of the kingdom over it.
        s.fill(4, 1, 10, 4, 3, 10, post()).fill(8, 1, 10, 8, 3, 10, post());
        s.fill(4, 4, 10, 8, 4, 10, Kit.log(STRIPPED_SPRUCE, "x"));
        s.set(5, 3, 10, Kit.hangingLantern()).set(7, 3, 10, Kit.hangingLantern());
        s.set(6, 5, 10, Kit.banner("red", 0));
        // Targets on bales before the palisade, spare bales in the back corners.
        for (int x : new int[]{3, 6, 9}) {
            s.set(x, 1, 1, HAY).set(x, 2, 1, TARGET);
        }
        s.set(1, 1, 1, HAY).set(1, 2, 1, HAY).set(1, 1, 2, HAY);
        s.set(11, 1, 1, HAY).set(11, 2, 1, HAY).set(11, 1, 2, HAY);
        // The pell in the ring.
        s.fill(6, 1, 5, 6, 2, 5, Kit.log(STRIPPED_SPRUCE, "y"));
        // Two dummies with arms, each wearing a tabard of the kingdom.
        for (int x : new int[]{2, 10}) {
            s.set(x, 1, 5, fence()).set(x, 2, 5, HAY).set(x, 3, 5, "minecraft:carved_pumpkin[facing=south]");
            s.set(x - 1, 2, 5, fence()).set(x + 1, 2, 5, fence());
            s.set(x, 2, 6, Kit.wallBanner("red", "south"));
        }
        // The weapon corner under a striped awning on four poles, its valance to the front.
        for (int[] pole : new int[][]{{1, 7}, {3, 7}, {1, 9}, {3, 9}}) {
            s.fill(pole[0], 1, pole[1], pole[0], 2, pole[1], fence());
        }
        for (int z = 7; z <= 9; z++) {
            s.fill(1, 3, z, 3, 3, z, z == 8 ? Kit.CLOTH : CANVAS);
        }
        for (int x = 1; x <= 3; x++) {
            s.set(x, 3, 10, Kit.wallBanner("red", "south"));
        }
        s.set(1, 1, 8, rack("east")).set(2, 1, 8, "minecraft:anvil[facing=north]");
        s.set(2, 1, 7, Kit.barrel()).set(2, 1, 9, crate());
        s.set(2, 2, 8, Kit.hangingLantern());
        // The officers' bench by the front rail, a wash basin and the standard of the company.
        s.fill(9, 1, 9, 11, 1, 9, Kit.stairs(SPRUCE_STAIRS, "south"));
        s.set(11, 1, 7, CAULDRON);
        s.fill(11, 1, 8, 11, 3, 8, fence()).set(11, 4, 8, Kit.banner("red", 0));
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the store of the army

    /**
     * The store of the army, in place of the barn: a half-timbered shed on a plinth of rough stone with its gable to the
     * front under a steep shingle roof that overhangs every side. A doorway three wide between two posts under a pent roof
     * on brackets with lanterns, banners of the kingdom on both sides, a loft door in the timbered gable with a hoist beam
     * and its lantern, a pennant over the ridge, shuttered windows in the sides. Inside, the two storehouse blocks at the
     * back among barrels, chests and barrels along both side walls, bales, sacks and crates on two stacks in the middle, the
     * desk of the quartermaster by the door, a loft over the back half on a summer beam with hay and sacks, a ladder up to
     * it and lanterns under the beams. Firewood, bales, sacks, barrels and crates under the eaves outside.
     */
    static Structure store() {
        Structure s = new Structure(13, 15, 11);
        // The footing and the floor, the apron before the doorway.
        s.fill(1, 0, 1, 11, 0, 9, Kit.ROUGH);
        s.fill(2, 0, 2, 10, 0, 8, SPRUCE_PLANKS);
        s.fill(5, 0, 9, 7, 0, 9, SPRUCE_PLANKS);
        s.fill(4, 0, 10, 8, 0, 10, Kit.ROUGH);
        // A plinth of rough stone, panels of plaster in a frame of spruce, a beam round the top.
        s.walls(1, 1, 1, 11, 1, 9, Kit.ROUGH);
        s.walls(1, 2, 1, 11, 4, 9, Kit.PLASTER);
        s.beam(1, 1, 11, 9, 5, SPRUCE_LOG);
        for (int x : new int[]{1, 11}) {
            for (int z : new int[]{1, 4, 6, 9}) {
                s.fill(x, 2, z, x, 4, z, post());
            }
        }
        for (int x : new int[]{4, 8}) {
            s.fill(x, 2, 1, x, 4, 1, post());
        }
        s.fill(4, 1, 9, 4, 4, 9, post()).fill(8, 1, 9, 8, 4, 9, post());
        // Rails across the panels at the middle of the wall.
        s.fill(2, 3, 1, 3, 3, 1, Kit.log(SPRUCE_LOG, "x")).fill(9, 3, 1, 10, 3, 1, Kit.log(SPRUCE_LOG, "x"));
        s.fill(2, 3, 9, 3, 3, 9, Kit.log(SPRUCE_LOG, "x")).fill(9, 3, 9, 10, 3, 9, Kit.log(SPRUCE_LOG, "x"));
        for (int x : new int[]{1, 11}) {
            s.fill(x, 3, 2, x, 3, 3, Kit.log(SPRUCE_LOG, "z")).fill(x, 3, 7, x, 3, 8, Kit.log(SPRUCE_LOG, "z"));
        }
        // The doorway between its posts under a lintel.
        s.fill(5, 1, 9, 7, 3, 9, AIR);
        s.fill(5, 4, 9, 7, 4, 9, Kit.log(SPRUCE_LOG, "x"));
        // Shuttered windows in the sides.
        for (int x : new int[]{1, 11}) {
            int out = x == 1 ? -1 : 1;
            String facing = x == 1 ? "west" : "east";
            s.fill(x, 2, 5, x, 3, 5, Kit.GLASS);
            for (int y = 2; y <= 3; y++) {
                s.set(x + out, y, 4, Kit.shutter(Kit.SHUTTER, facing)).set(x + out, y, 6, Kit.shutter(Kit.SHUTTER, facing));
            }
        }
        // The roof, overhanging the walls on every side, and the gables in their frame.
        int[] under = roofZ(s, 0, 12, 0, 10, 5, 1, Kit.SHINGLE);
        for (int z : new int[]{1, 9}) {
            gableAtZ(s, 0, under, z, 6, Kit.PLASTER);
            for (int x : new int[]{3, 9}) {
                s.fill(x, 6, z, x, under[x] - 1, z, post());
            }
            for (int x : new int[]{5, 7}) {
                s.fill(x, 6, z, x, under[x] - 1, z, post());
            }
            s.fill(4, 8, z, 8, 8, z, Kit.log(SPRUCE_LOG, "x"));
            s.set(6, 10, z, Kit.GLASS);
        }
        // The loft door in the front gable, the hoist beam over it with its lantern, the pennant over the ridge.
        s.door(6, 6, 9, DOOR, "north", "left");
        s.set(6, 9, 10, Kit.log(SPRUCE_LOG, "z"));
        s.set(6, 8, 10, Kit.hangingLantern());
        s.set(6, 7, 1, Kit.GLASS);
        s.set(6, 12, 9, Kit.SHINGLE.block());
        s.set(6, 13, 9, fence()).set(6, 14, 9, Kit.banner("red", 0));
        // The pent roof over the doorway on brackets, lanterns under it, banners on both sides.
        s.fill(4, 5, 10, 8, 5, 10, Kit.stairs(Kit.SHINGLE.stairs(), "north"));
        s.set(4, 4, 10, Kit.upside(SPRUCE_STAIRS, "north")).set(8, 4, 10, Kit.upside(SPRUCE_STAIRS, "north"));
        s.set(5, 4, 10, Kit.hangingLantern()).set(7, 4, 10, Kit.hangingLantern());
        s.set(2, 4, 10, Kit.wallBanner("red", "south")).set(10, 4, 10, Kit.wallBanner("red", "south"));
        // The loft over the back half on a summer beam, its rail and the ladder up to it.
        s.fill(2, 5, 2, 10, 5, 5, SPRUCE_PLANKS);
        s.fill(2, 4, 5, 10, 4, 5, Kit.log(STRIPPED_SPRUCE, "x"));
        s.fill(2, 6, 5, 10, 6, 5, fence());
        for (int y = 1; y <= 5; y++) {
            s.set(6, y, 2, Kit.ladder("south"));
        }
        // A tie beam over the front half.
        s.fill(2, 5, 7, 10, 5, 7, Kit.log(STRIPPED_SPRUCE, "x"));
        // Below: the storehouse blocks at the back among barrels, chests and barrels along the sides.
        s.set(4, 1, 2, STOREHOUSE).set(8, 1, 2, STOREHOUSE);
        for (int x : new int[]{2, 3, 5, 7, 9, 10}) {
            s.set(x, 1, 2, Kit.barrel());
        }
        s.set(2, 2, 2, Kit.barrel()).set(10, 2, 2, Kit.barrel());
        for (int x : new int[]{2, 10}) {
            String in = x == 2 ? "east" : "west";
            s.set(x, 1, 3, Kit.chest(in)).set(x, 1, 4, Kit.barrel()).set(x, 1, 5, Kit.barrel()).set(x, 2, 5, Kit.barrel());
            s.set(x, 1, 6, Kit.chest(in));
        }
        s.set(2, 1, 7, HAY).set(2, 1, 8, HAY).set(2, 2, 8, HAY);
        Furnish.crate(s, 10, 1, 7, "spruce");
        Furnish.sack(s, 10, 1, 8, false);
        Furnish.sack(s, 10, 2, 8, true);
        // Two stacks in the middle: crates and sacks on the west, bales and sacks on the east.
        Furnish.crate(s, 4, 1, 4, "spruce");
        Furnish.crate(s, 4, 2, 4, "spruce");
        Furnish.sack(s, 4, 1, 5, true);
        Furnish.sack(s, 4, 1, 6, false);
        s.set(8, 1, 4, HAY).set(8, 2, 4, HAY);
        Furnish.sack(s, 8, 1, 5, false);
        Furnish.crate(s, 8, 1, 6, "spruce");
        // The desk of the quartermaster by the door.
        s.set(3, 1, 8, Kit.topSlab(SPRUCE_SLAB));
        s.set(3, 2, 8, Furnish.candle(""));
        Furnish.chair(s, 3, 1, 7, "south", "spruce");
        // The loft: hay, sacks and a crate, lit from the back gable.
        s.set(2, 6, 2, HAY).set(3, 6, 2, HAY).set(2, 7, 2, HAY).set(2, 6, 3, HAY);
        Furnish.sack(s, 9, 6, 2, true);
        Furnish.sack(s, 10, 6, 2, false);
        Furnish.sack(s, 10, 7, 2, true);
        Furnish.sack(s, 10, 6, 3, false);
        Furnish.crate(s, 8, 6, 2, "spruce");
        s.set(4, 7, 2, Kit.wallTorch("south")).set(8, 7, 2, Kit.wallTorch("south"));
        // Lanterns under the summer beam and the tie beam, torches by the doorway inside.
        for (int x : new int[]{4, 8}) {
            s.set(x, 3, 5, Kit.hangingLantern());
            s.set(x, 4, 7, Kit.hangingLantern());
        }
        s.set(4, 3, 8, Kit.wallTorch("north")).set(8, 3, 8, Kit.wallTorch("north"));
        // Outside under the eaves: firewood and barrels on the west, bales and sacks on the east, stores by the front.
        s.fill(0, 1, 2, 0, 2, 3, Kit.log(SPRUCE_LOG, "z"));
        s.set(0, 1, 7, Kit.barrel()).set(0, 1, 8, Kit.barrel()).set(0, 2, 8, Kit.lantern());
        s.set(12, 1, 2, HAY).set(12, 1, 3, HAY).set(12, 2, 2, HAY);
        Furnish.sack(s, 12, 1, 7, false);
        Furnish.crate(s, 12, 1, 8, "spruce");
        s.set(12, 2, 8, Kit.lantern());
        s.set(1, 1, 10, HAY).set(1, 2, 10, HAY);
        Furnish.sack(s, 2, 1, 10, false);
        s.set(10, 1, 10, Kit.barrel()).set(10, 2, 10, Kit.lantern());
        Furnish.crate(s, 11, 1, 10, "spruce");
        Furnish.crate(s, 11, 2, 10, "spruce");
        keepGround(s);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the watch tower

    /**
     * One ring of stairs around a square plan, facing inwards, turned stairs at the corners: the eave of a tented roof.
     */
    private static void stairRing(Structure s, int west, int east, int back, int front, int y, String stairs) {
        s.fill(west, y, back, east, y, back, Kit.stairs(stairs, "south"));
        s.fill(west, y, front, east, y, front, Kit.stairs(stairs, "north"));
        s.fill(west, y, back + 1, west, y, front - 1, Kit.stairs(stairs, "east"));
        s.fill(east, y, back + 1, east, y, front - 1, Kit.stairs(stairs, "west"));
        s.set(west, y, back, Kit.stairs(stairs, "south", "outer_left"));
        s.set(east, y, back, Kit.stairs(stairs, "south", "outer_right"));
        s.set(west, y, front, Kit.stairs(stairs, "north", "outer_right"));
        s.set(east, y, front, Kit.stairs(stairs, "north", "outer_left"));
    }

    /**
     * A needle over a square tower, steeper than a tented roof: rings that step in by one block every three layers, each
     * two layers of the full block under a ring of stairs, hollow inside, and a point two blocks high.
     *
     * @param y the layer of the lowest ring
     * @return the layer over the point
     */
    private static int needle(Structure s, int x1, int z1, int x2, int z2, int y, Kit.Roof roof) {
        int layer = y;
        int ring = 0;
        while (x2 - ring - (x1 + ring) >= 2 && z2 - ring - (z1 + ring) >= 2) {
            s.walls(x1 + ring, layer, z1 + ring, x2 - ring, layer + 1, z2 - ring, roof.block());
            stairRing(s, x1 + ring, x2 - ring, z1 + ring, z2 - ring, layer + 2, roof.stairs());
            layer += 3;
            ring++;
        }
        s.fill(x1 + ring, layer, z1 + ring, x2 - ring, layer + 1, z2 - ring, roof.block());
        return layer + 2;
    }

    /**
     * The watch tower of the camp, in place of the timber watch tower: a stone base on a battered foot, with quoins, arrow
     * slits and a door under a carved keystone; a half-timbered storey above it with shuttered windows; a lookout jutting out
     * on a ring of brackets with a rail between corner posts and the banner of the kingdom hanging under it; and over the
     * lookout a steep needle of slate on an eave of its own with the pennant of the kingdom on its point. A ladder runs up
     * inside from the floor to the lookout; torches light the door and the shaft, a lantern hangs inside the needle.
     */
    static Structure tower() {
        Structure s = new Structure(7, 24, 7);
        // The footing with its battered foot, the step to the door.
        s.fill(1, 0, 1, 5, 0, 5, Kit.ROUGH);
        s.fill(2, 0, 2, 4, 0, 4, SPRUCE_PLANKS);
        stairRing(s, 0, 6, 0, 6, 0, Kit.ROUGH_STAIRS);
        // The stone base with its quoins, slits and door.
        s.walls(1, 1, 1, 5, 3, 5, Kit.WALL);
        s.walls(1, 1, 1, 5, 1, 5, Kit.ROUGH);
        quoins(s, 1, 1, 1, 1, 1, 3);
        quoins(s, 5, 1, -1, 1, 1, 3);
        quoins(s, 1, 5, 1, -1, 1, 3);
        quoins(s, 5, 5, -1, -1, 1, 3);
        s.set(1, 2, 3, AIR).set(5, 2, 3, AIR);
        s.door(3, 1, 5, DOOR, "north", "left");
        s.set(3, 3, 5, Kit.CARVED);
        s.set(2, 2, 6, Kit.wallTorch("south")).set(4, 2, 6, Kit.wallTorch("south")).set(3, 2, 0, Kit.wallTorch("north"));
        // The half-timbered storey: a sill and a head beam, posts at the corners and plaster between them, a shuttered
        // window in every face but the back, where the ladder runs.
        s.beam(1, 1, 5, 5, 4, SPRUCE_LOG);
        s.walls(1, 5, 1, 5, 8, 5, Kit.PLASTER);
        s.corners(1, 5, 1, 5, 8, 5, SPRUCE_LOG);
        s.beam(1, 1, 5, 5, 9, SPRUCE_LOG);
        for (int x : new int[]{1, 5}) {
            int out = x == 1 ? -1 : 1;
            String facing = x == 1 ? "west" : "east";
            s.fill(x, 6, 3, x, 7, 3, Kit.GLASS);
            for (int y = 6; y <= 7; y++) {
                s.set(x + out, y, 2, Kit.shutter(Kit.SHUTTER, facing)).set(x + out, y, 4, Kit.shutter(Kit.SHUTTER, facing));
            }
        }
        s.fill(3, 6, 5, 3, 7, 5, Kit.GLASS);
        for (int y = 6; y <= 7; y++) {
            s.set(2, y, 6, Kit.shutter(Kit.SHUTTER, "south")).set(4, y, 6, Kit.shutter(Kit.SHUTTER, "south"));
        }
        s.fill(2, 5, 1, 4, 5, 1, Kit.log(SPRUCE_LOG, "x"));
        // Inside: the ladder up the back wall, a barrel and a rack below, torches on the way up.
        for (int y = 1; y <= 10; y++) {
            s.set(3, y, 2, Kit.ladder("south"));
        }
        s.set(2, 1, 2, Kit.barrel()).set(4, 1, 2, rack("south"));
        s.set(2, 2, 4, Kit.wallTorch("east")).set(4, 7, 4, Kit.wallTorch("west"));
        // The lookout on its brackets, the banner of the kingdom hanging under its front, a rail between the corner posts.
        for (int i = 1; i <= 5; i++) {
            s.set(0, 9, i, Kit.upside(SPRUCE_STAIRS, "east")).set(6, 9, i, Kit.upside(SPRUCE_STAIRS, "west"));
            s.set(i, 9, 0, Kit.upside(SPRUCE_STAIRS, "south")).set(i, 9, 6, Kit.upside(SPRUCE_STAIRS, "north"));
        }
        s.set(3, 9, 6, Kit.wallBanner("red", "south"));
        s.beam(0, 0, 6, 6, 10, SPRUCE_LOG);
        s.fill(1, 10, 1, 5, 10, 5, SPRUCE_PLANKS);
        s.set(3, 10, 2, Kit.ladder("south"));
        s.corners(0, 11, 0, 6, 12, 6, SPRUCE_LOG);
        s.fill(1, 11, 0, 5, 11, 0, fence()).fill(1, 11, 6, 5, 11, 6, fence());
        s.fill(0, 11, 1, 0, 11, 5, fence()).fill(6, 11, 1, 6, 11, 5, fence());
        // The eave on the corner posts, the needle of slate over it with its pennant, a lantern hanging inside.
        stairRing(s, 0, 6, 0, 6, 13, Kit.SLATE.stairs());
        int top = needle(s, 1, 1, 5, 5, 14, Kit.SLATE);
        s.set(3, top, 3, fence());
        s.set(3, top + 1, 3, Kit.banner("red", 0));
        s.fill(3, 14, 3, 3, 19, 3, Kit.chain());
        s.set(3, 13, 3, Kit.hangingLantern());
        s.connect();
        return s;
    }
}
