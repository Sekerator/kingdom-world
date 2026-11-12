package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The field camp of an army of the style "nordic" (see {@link Nordic}): four buildings that take the places of the plain
 * barracks hall, drill yard, barn and timber watch tower of a camp, each with the same footprint, entrance (to the south),
 * role and counting blocks as the one it replaces.
 *
 * <p>A host of the north in the field sleeps under tents of striped wool whose gables are edged with crossed boards ending
 * in carved dragon heads, trains in a yard behind a palisade of pointed logs, keeps its stores in a storehouse raised on
 * stone piers under a roof of turf and watches from a timber tower with a gallery under a needle of shingles. The white of
 * the canvas is a fixed accent, its stripes, the bunks of the soldiers, the banners and the shields are the cloth, the
 * colour of the kingdom; everything else is timber, rough stone, shingles and the spires of a look like the town.
 *
 * <p>Coordinates as everywhere: x from west to east, z from the back (north) to the front (south), y up.
 */
final class NordicCamp {
    private static final String AIR = Structure.AIR;
    private static final String LOG = Nordic.LOG;
    private static final String POST = Kit.log(Nordic.LOG, "y");
    private static final String STAVE = Kit.log(Nordic.STAVE, "y");
    private static final String PLANKS = Nordic.PLANKS;
    private static final String STAIRS = Nordic.STAIRS;
    private static final String FENCE = Nordic.FENCE;
    private static final String DOOR = Nordic.DOOR;
    private static final String FLOOR = Nordic.FLOOR;
    private static final String FLOOR_STAIRS = Nordic.FLOOR_STAIRS;
    private static final String FOOTING = Nordic.FOOTING;
    private static final String FOOTING_STAIRS = Nordic.FOOTING_STAIRS;
    private static final String FOOTING_SLAB = Nordic.FOOTING_SLAB;
    /** The canvas of tents: white wool, a fixed accent. */
    private static final String CANVAS = "minecraft:white_wool";
    /** The stripes of the canvas: the cloth, the colour of the kingdom. */
    private static final String STRIPE = Kit.CLOTH;
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String EARTH = "minecraft:coarse_dirt";
    private static final String PATH = "minecraft:dirt_path";
    /** Firewood: logs of the timber, the wood of the place. */
    private static final String FIREWOOD = "minecraft:oak_log";
    /** A bundle of stockfish, the dried cod an army of the north lives on. */
    private static final String STOCKFISH = "minecraft:dried_kelp_block";
    private static final String STOREHOUSE = "kingdomworld:storehouse";
    private static final String BARRACKS = "kingdomworld:barracks[facing=south]";
    /** Rises of the canvas of a tent: one layer and two in turn, so that it climbs three layers for every two blocks in. */
    private static final int[] CANVAS_RISES = {1, 2, 1, 2, 1, 2, 1, 2};

    private NordicCamp() {
    }

    /**
     * The four buildings of the camp: the tent of the soldiers (barracks with twelve bunks), the training yard, the store
     * of the army (a warehouse with its containers) and the watch tower.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("nordic_camp_barracks", "defense", "barracks", "kingdomworld:barracks", 6, 0, 10,
                barracks(), "barracks", 1, "").params("\"bunks\": 12").at("door", 6, 0, 10));
        list.add(new BuildingGenerator.Building("nordic_camp_yard", "defense", "yard", "minecraft:target", 6, 1, 10,
                yard(), "none", 1, "").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("nordic_camp_store", "farming", "warehouse", "kingdomworld:storehouse", 6, 1, 10,
                store(), "warehouse", 1, "").params("\"containers\": 12").at("door", 5, 1, 10));
        list.add(new BuildingGenerator.Building("nordic_camp_tower", "defense", "tower", "minecraft:oak_fence", 3, 0, 6,
                tower(), "tower", 1, "").at("door", 3, 1, 6));
        return list;
    }

    // ---------------------------------------------------------------- small shapes

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static boolean solid(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && s.solid(x, y, z);
    }

    /** Fills a box with a block, leaving out what falls outside the structure. */
    private static void fill(Structure s, int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    put(s, x, y, z, state);
                }
            }
        }
    }

    /** A lantern hanging at (x, y, z) on a chain from the lowest block above it. */
    private static void hang(Structure s, int x, int y, int z) {
        int top = y + 1;
        while (top < s.sizeY && !solid(s, x, top, z)) {
            top++;
        }
        if (top >= s.sizeY) {
            return;
        }
        for (int yy = y + 1; yy < top; yy++) {
            put(s, x, yy, z, Kit.chain());
        }
        put(s, x, y, z, Kit.hangingLantern());
    }

    /**
     * A standard: a pole of {@code height} posts from layer {@code y} with a banner of {@code color} on its top looking to
     * the street.
     */
    private static void standard(Structure s, int x, int y, int z, int height, String color) {
        fill(s, x, y, z, x, y + height - 1, z, FENCE);
        put(s, x, y + height, z, Kit.banner(color, 0));
    }

    /** A lantern standing on a post of {@code height} from layer {@code y}. */
    private static void lampPost(Structure s, int x, int y, int z, int height) {
        fill(s, x, y, z, x, y + height - 1, z, FENCE);
        put(s, x, y + height, z, Kit.lantern());
    }

    // ---------------------------------------------------------------- the tent

    /**
     * A tent of the north over the columns {@code x1..x2} (an odd width) and the rows {@code z1..z2}, its ridge along z: the
     * canvas comes down to the eaves at layer {@code y} on both long sides and climbs to the ridge as {@code rises} says,
     * white wool with stripes of the cloth on the rows {@code stripe} names; a ridge pole of a log under the top of the
     * canvas; at both ends a gable of canvas whose edges are boards of the timber that cross over the ridge, and with
     * {@code dragons} a carved dragon head of the spires on each horn of the cross, looking out to the sides like those of
     * the old ship tents.
     *
     * @return the layer of the ridge
     */
    private static int tent(Structure s, int x1, int x2, int z1, int z2, int y, int[] rises, IntPredicate stripe, boolean dragons) {
        int cx = (x1 + x2) / 2;
        int half = cx - x1;
        int ridge = y + Nordic.rise(rises, half);
        for (int z = z1; z <= z2; z++) {
            boolean end = z == z1 || z == z2;
            for (int x = x1; x <= x2; x++) {
                int d = Math.min(x - x1, x2 - x);
                int top = y + Nordic.rise(rises, d);
                int bottom = d == 0 ? top : y + Nordic.rise(rises, d - 1) + 1;
                for (int yy = bottom; yy <= top; yy++) {
                    String state;
                    if (end) {
                        state = yy == top && d < half ? Kit.stairs(STAIRS, x < cx ? "east" : "west") : PLANKS;
                    } else {
                        state = stripe.test(z) ? STRIPE : CANVAS;
                    }
                    put(s, x, yy, z, state);
                }
                if (end) {
                    for (int yy = y; yy < bottom; yy++) {
                        put(s, x, yy, z, CANVAS);
                    }
                } else if (d == half) {
                    // The ridge pole under the canvas, seen only from inside.
                    put(s, x, bottom - 1, z, Kit.log(LOG, "z"));
                }
            }
        }
        for (int z : new int[]{z1, z2}) {
            if (dragons) {
                Nordic.dragonHead(s, cx - 1, ridge + 1, z, "west", Nordic.SPIRE);
                Nordic.dragonHead(s, cx + 1, ridge + 1, z, "east", Nordic.SPIRE);
            }
            put(s, cx - 1, ridge, z, PLANKS);
            put(s, cx + 1, ridge, z, PLANKS);
            put(s, cx - 1, ridge + 1, z, Kit.stairs(STAIRS, "west"));
            put(s, cx + 1, ridge + 1, z, Kit.stairs(STAIRS, "east"));
        }
        return ridge;
    }

    // ---------------------------------------------------------------- the barracks

    /**
     * The tent of the soldiers (13 by 11, like the barracks hall): a great tent of striped wool on a floor of boards, its
     * gable to the street with crossed boards and dragon heads on both ends; in the gable the entrance with its flaps tied
     * back (banners of the cloth), before it a doorstep of stones, two standards and two lamps. Inside twelve bunks under
     * the low canvas along both sides, six on each with furs of white, grey and brown; between them the racks of weapons
     * (grindstones) with shields over them and two sea chests; down the middle a board with benches and candles, at the back
     * the seat of the captain (the barracks block) between two pillars with lanterns under a banner; lanterns hang from the
     * ridge pole and the canvas.
     */
    static Structure barracks() {
        Structure s = new Structure(13, 15, 11);
        // The floor of boards on a curb of rough stone where the canvas is pegged down.
        s.fill(0, 0, 0, 12, 0, 9, FLOOR);
        s.walls(0, 0, 0, 12, 0, 9, FOOTING);
        int ridge = tent(s, 0, 12, 0, 9, 1, CANVAS_RISES, z -> z == 2 || z == 7, true);
        // The entrance in the front gable, its flaps tied back, the doorstep, the standards and the lamps.
        fill(s, 6, 1, 9, 6, 3, 9, AIR);
        put(s, 6, 4, 9, Kit.log(LOG, "x"));
        put(s, 5, 3, 10, Kit.wallBanner("red", "south"));
        put(s, 7, 3, 10, Kit.wallBanner("red", "south"));
        fill(s, 5, 0, 10, 7, 0, 10, Kit.slab(FOOTING_SLAB));
        standard(s, 2, 0, 10, 3, "red");
        standard(s, 10, 0, 10, 3, "red");
        lampPost(s, 4, 0, 10, 2);
        lampPost(s, 8, 0, 10, 2);
        // Twelve bunks under the low canvas, six on each side, with furs of white, grey and brown.
        String[] furs = {"white", "light_gray", "brown"};
        int i = 0;
        for (int z : new int[]{1, 2, 3, 5, 6, 7}) {
            Furnish.bed(s, 2, 1, z, "west", furs[i % 3]);
            Furnish.bed(s, 10, 1, z, "east", furs[(i + 1) % 3]);
            i++;
        }
        // The racks of weapons between the bunks with shields over them, the sea chests by the entrance.
        Furnish.weaponRack(s, 1, 1, 4, "east", "spruce");
        Furnish.weaponRack(s, 11, 1, 4, "west", "spruce");
        put(s, 2, 2, 4, Kit.wallBanner("red", "east"));
        put(s, 10, 2, 4, Kit.wallBanner("white", "west"));
        Furnish.weaponRack(s, 1, 1, 8, "east", "spruce");
        Furnish.weaponRack(s, 11, 1, 8, "west", "spruce");
        Furnish.chest(s, 2, 1, 8, "east");
        Furnish.chest(s, 10, 1, 8, "west");
        // The board down the middle with its benches and candles.
        Nordic.feastBoard(s, 6, 3, 6, 6, 1);
        // The seat of the captain at the back: the barracks block between two pillars with lanterns, a banner over it.
        put(s, 6, 1, 1, BARRACKS);
        put(s, 5, 1, 1, POST);
        put(s, 7, 1, 1, POST);
        put(s, 5, 2, 1, Kit.lantern());
        put(s, 7, 2, 1, Kit.lantern());
        put(s, 6, 3, 1, Kit.wallBanner("red", "south"));
        Nordic.furs(s, 5, 2, 7, 2, 1);
        // Light: lanterns from the ridge pole and under the canvas over both aisles.
        hang(s, 6, ridge - 5, 2);
        hang(s, 6, ridge - 5, 7);
        for (int z : new int[]{2, 6}) {
            hang(s, 3, 4, z);
            hang(s, 9, 4, z);
        }
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the training yard

    /**
     * The training yard (13 by 11, like the drill yard; its ground replaces the ground): trodden earth inside a palisade of
     * pointed logs at the back that stops the arrows and low fences of wattle on posts at the sides and the front; the gate
     * between two posts under a little roof of shingles with crossed boards and two dragon heads looking out along the
     * fence, banners of the cloth on the posts and lanterns under the lintel. At the back three archery butts with shields
     * hung on the palisade over them; in the middle the hazelled square for single combat, its border of stones and a
     * lantern on each of its corner poles; two straw men with shields at the sides; in the front west corner the weapon rack
     * with spears, shields and grindstones, in the front east corner a fire pit ringed with stones and a bench; a trough and
     * hay by the butts.
     */
    static Structure yard() {
        Structure s = new Structure(13, 10, 11);
        s.fill(0, 0, 0, 12, 0, 10, EARTH);
        fill(s, 5, 0, 8, 7, 0, 10, PATH);
        // The palisade at the back: logs of two heights with pointed tops on a footing of stone.
        for (int x = 0; x <= 12; x++) {
            int head = x % 2 == 0 ? 5 : 4;
            put(s, x, 0, 0, FOOTING);
            fill(s, x, 1, 0, x, head, 0, POST);
            put(s, x, head + 1, 0, FENCE);
        }
        // Wattle fences on posts at the sides and the front, lanterns on the posts.
        for (int z = 1; z <= 9; z++) {
            for (int x : new int[]{0, 12}) {
                put(s, x, 0, z, FOOTING);
                boolean post = z == 3 || z == 6 || z == 9;
                put(s, x, 1, z, post ? POST : FENCE);
                if (post) {
                    put(s, x, 2, z, POST);
                    put(s, x, 3, z, Kit.lantern());
                }
            }
        }
        for (int x : new int[]{1, 2, 3, 9, 10, 11}) {
            put(s, x, 0, 9, FOOTING);
            put(s, x, 1, 9, FENCE);
        }
        // The gate: two posts and a lintel under a little roof of shingles along the fence, crossed boards at its ends and
        // dragon heads looking out along the fence both ways; banners on the posts, lanterns under the lintel.
        for (int x : new int[]{4, 8}) {
            put(s, x, 0, 9, FOOTING);
            fill(s, x, 1, 9, x, 4, 9, POST);
            put(s, x, 3, 10, Kit.wallBanner("red", "south"));
        }
        fill(s, 4, 5, 9, 8, 5, 9, Kit.log(LOG, "x"));
        Nordic.gableRoof(s, 5, 9, 7, 9, 5, true, Nordic.STEEP, Nordic.ROOF, null, Nordic.BOARD, Nordic.SPIRE);
        hang(s, 5, 4, 9);
        hang(s, 7, 4, 9);
        // Three archery butts at the back, shields on the palisade over them.
        for (int x : new int[]{3, 6, 9}) {
            put(s, x, 1, 1, HAY);
            put(s, x, 2, 1, "minecraft:target[power=0]");
        }
        String[] shields = {"yellow", "red", "white", "red"};
        int[] at = {1, 4, 8, 11};
        for (int k = 0; k < at.length; k++) {
            put(s, at[k], 4, 1, Kit.wallBanner(shields[k], "south"));
        }
        put(s, 1, 1, 1, HAY);
        put(s, 1, 2, 1, HAY);
        put(s, 2, 1, 1, HAY);
        put(s, 11, 1, 1, "minecraft:water_cauldron[level=3]");
        // The hazelled square for single combat: a border of stones, poles with lanterns at its corners.
        for (int z = 3; z <= 7; z++) {
            for (int x = 4; x <= 8; x++) {
                if (x == 4 || x == 8 || z == 3 || z == 7) {
                    put(s, x, 0, z, FOOTING);
                }
            }
        }
        for (int[] c : new int[][]{{4, 3}, {8, 3}, {4, 7}, {8, 7}}) {
            lampPost(s, c[0], 1, c[1], 1);
        }
        // Two straw men with shields on their arms.
        for (int x : new int[]{2, 10}) {
            put(s, x, 1, 4, FENCE);
            put(s, x, 2, 4, HAY);
            put(s, x, 3, 4, "minecraft:carved_pumpkin[facing=south]");
            put(s, x - 1, 2, 4, FENCE);
            put(s, x + 1, 2, 4, FENCE);
            put(s, x, 2, 5, Kit.wallBanner(x < 6 ? "red" : "white", "south"));
        }
        // The weapon rack in the front west corner: a beam on two posts with spears between them and shields hanging from
        // it, grindstones for the blades and a crate before it, a lantern on the beam.
        fill(s, 1, 1, 6, 1, 2, 6, POST);
        fill(s, 1, 1, 8, 1, 2, 8, POST);
        fill(s, 1, 1, 7, 1, 2, 7, FENCE);
        fill(s, 1, 3, 6, 1, 3, 8, Kit.log(LOG, "z"));
        put(s, 2, 3, 6, Kit.wallBanner("white", "east"));
        put(s, 2, 3, 8, Kit.wallBanner("red", "east"));
        Furnish.weaponRack(s, 2, 1, 7, "east", "spruce");
        Furnish.weaponRack(s, 2, 1, 8, "east", "spruce");
        Furnish.crate(s, 3, 1, 8, "spruce");
        put(s, 1, 4, 7, Kit.lantern());
        // The fire pit in the front east corner, ringed with stones, and a bench beside it.
        put(s, 10, 1, 7, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        for (int[] c : new int[][]{{9, 7}, {11, 7}, {10, 6}, {10, 8}}) {
            put(s, c[0], 1, c[1], Kit.slab(FOOTING_SLAB));
        }
        Furnish.bench(s, 9, 5, 11, 5, 1, "south", "spruce");
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the store

    /**
     * Barge boards on the end row {@code a} of a turf roof drawn from the field {@code f} (its eaves at layer {@code eave}):
     * boards of the timber follow both slopes in place of the turf and cross over the ridge as two horns.
     */
    private static void turfBoards(Structure s, Nordic.Field f, boolean alongX, int a, int c1, int c2, int eave) {
        int ridge = Integer.MIN_VALUE;
        int lo = c1;
        int hi = c1;
        for (int c = c1; c <= c2; c++) {
            int h = alongX ? f.at(a, c) : f.at(c, a);
            if (h > ridge) {
                ridge = h;
                lo = c;
            }
            if (h == ridge) {
                hi = c;
            }
        }
        String minus = alongX ? "north" : "west";
        String plus = alongX ? "south" : "east";
        for (int c = c1; c <= c2; c++) {
            int h = alongX ? f.at(a, c) : f.at(c, a);
            if (h == Nordic.NONE || h <= eave) {
                continue;
            }
            int x = alongX ? a : c;
            int z = alongX ? c : a;
            put(s, x, h, z, h == ridge ? PLANKS : Kit.stairs(STAIRS, c < lo ? plus : minus));
            put(s, x, h + 1, z, AIR);
        }
        put(s, alongX ? a : lo - 1, ridge, alongX ? lo - 1 : a, Kit.stairs(STAIRS, minus));
        put(s, alongX ? a : hi + 1, ridge, alongX ? hi + 1 : a, Kit.stairs(STAIRS, plus));
    }

    /**
     * The store of the army (13 by 11, like the barn; its ground replaces the ground): a storehouse of the north (stabbur)
     * raised on piers of rough stone above the damp, stave walls on a sill of logs under a roof of turf with crossed barge
     * boards; its gable to the street juts out to the end of the roof on a row of brackets, with a carved window between two
     * banners of the cloth, over the door reached by two steps of boards; firewood stacked under the west eaves, an open shed of shingles on the east
     * side with hay, sacks and bundles of stockfish, before the house hay, sacks, casks and crates. Inside barrels stacked
     * along the back wall, chests and barrels along the sides and the two storehouse blocks by the door, sacks in the middle
     * and lanterns.
     */
    static Structure store() {
        Structure s = new Structure(13, 12, 11);
        s.fill(0, 0, 0, 12, 0, 10, EARTH);
        fill(s, 4, 0, 8, 6, 0, 10, PATH);
        // Piers of rough stone under the sill, the gap under the floor open.
        for (int x : new int[]{1, 3, 5, 7, 9}) {
            for (int z : new int[]{1, 7}) {
                fill(s, x, 0, z, x, 1, z, FOOTING);
            }
        }
        for (int z : new int[]{3, 5}) {
            for (int x : new int[]{1, 9}) {
                fill(s, x, 0, z, x, 1, z, FOOTING);
            }
        }
        // The house: a floor of boards, stave walls, the roof of turf with its barge boards; the back gable on the wall,
        // the front gable jutting out to the end of the roof on a row of brackets over the door.
        s.fill(2, 2, 2, 8, 2, 6, FLOOR);
        Nordic.staveWalls(s, 1, 2, 1, 9, 5, 7);
        Nordic.turfRoof(s, 1, 1, 9, 7, 5, false, null);
        Nordic.Field f = Nordic.gableField(1, 1, 9, 7, 5, false, Nordic.PITCH);
        Nordic.gableWall(s, f, false, 1, 1, 9, 6, STAVE);
        for (int x = 2; x <= 8; x++) {
            put(s, x, 5, 8, Kit.upside(STAIRS, "north"));
        }
        fill(s, 3, 6, 8, 7, 6, 8, Kit.log(LOG, "x"));
        Nordic.gableWall(s, f, false, 8, 2, 8, 7, STAVE);
        turfBoards(s, f, false, 0, 0, 10, 5);
        turfBoards(s, f, false, 8, 0, 10, 5);
        // The door between carved posts, the steps up to it, the window in the jutting gable, lanterns under it.
        put(s, 4, 3, 7, POST);
        put(s, 4, 4, 7, POST);
        put(s, 6, 3, 7, POST);
        put(s, 6, 4, 7, POST);
        s.door(5, 3, 7, DOOR, "north", "left");
        put(s, 5, 0, 8, FOOTING);
        put(s, 5, 1, 8, FOOTING);
        put(s, 5, 2, 8, Kit.stairs(FLOOR_STAIRS, "north"));
        put(s, 5, 1, 9, Kit.stairs(FLOOR_STAIRS, "north"));
        Nordic.window(s, 5, 8, 8, "south", 1, false);
        put(s, 3, 8, 9, Kit.wallBanner("red", "south"));
        put(s, 7, 8, 9, Kit.wallBanner("red", "south"));
        hang(s, 3, 4, 8);
        hang(s, 7, 4, 8);
        // A small window in the west wall with its shutters, firewood under the west eaves.
        put(s, 1, 4, 4, Kit.GLASS);
        put(s, 0, 4, 3, Kit.shutter(Nordic.TRAPDOOR, "west"));
        put(s, 0, 4, 5, Kit.shutter(Nordic.TRAPDOOR, "west"));
        fill(s, 0, 1, 2, 0, 2, 6, Kit.log(FIREWOOD, "z"));
        // The open shed on the east side: posts, a roof of shingles leaning on the house, hay and sacks under it.
        for (int z = 1; z <= 7; z++) {
            put(s, 11, 4, z, Kit.stairs(Nordic.ROOF.stairs(), "west"));
            put(s, 12, 3, z, Kit.stairs(Nordic.ROOF.stairs(), "west"));
        }
        for (int z : new int[]{1, 4, 7}) {
            fill(s, 12, 1, z, 12, 2, z, POST);
        }
        put(s, 11, 1, 2, HAY);
        put(s, 12, 1, 2, HAY);
        put(s, 11, 2, 2, HAY);
        put(s, 11, 1, 3, HAY);
        Furnish.sack(s, 12, 1, 5, true);
        Furnish.sack(s, 12, 1, 6, false);
        Furnish.sack(s, 12, 2, 5, false);
        // Bundles of stockfish hanging from a beam under the shed roof.
        fill(s, 11, 3, 5, 11, 3, 6, Kit.log(LOG, "z"));
        put(s, 11, 2, 5, STOCKFISH);
        put(s, 11, 2, 6, STOCKFISH);
        hang(s, 11, 3, 4);
        // Before the house: hay and sacks to the west, casks and crates to the east, a lamp by the steps.
        put(s, 1, 1, 9, HAY);
        put(s, 2, 1, 9, HAY);
        put(s, 1, 2, 9, HAY);
        put(s, 1, 1, 10, HAY);
        Furnish.sack(s, 3, 1, 10, true);
        Furnish.sack(s, 2, 1, 10, false);
        put(s, 10, 1, 9, Kit.barrel());
        put(s, 11, 1, 9, Kit.barrel());
        put(s, 11, 2, 9, Kit.barrel());
        put(s, 12, 1, 9, "minecraft:barrel[facing=west,open=false]");
        Furnish.crate(s, 11, 1, 10, "spruce");
        Furnish.crate(s, 9, 1, 10, "spruce");
        lampPost(s, 7, 1, 9, 2);
        // Inside: barrels along the back wall, chests and barrels at the sides, the storehouse blocks by the door.
        for (int x = 2; x <= 8; x++) {
            put(s, x, 3, 2, Kit.barrel());
        }
        for (int x : new int[]{2, 3, 7, 8}) {
            put(s, x, 4, 2, Kit.barrel());
        }
        Furnish.chest(s, 2, 3, 3, "east");
        Furnish.chest(s, 2, 3, 4, "east");
        Furnish.chest(s, 8, 3, 3, "west");
        Furnish.chest(s, 8, 3, 4, "west");
        put(s, 2, 3, 5, Kit.barrel());
        put(s, 8, 3, 5, Kit.barrel());
        put(s, 2, 3, 6, STOREHOUSE);
        put(s, 8, 3, 6, STOREHOUSE);
        Furnish.sack(s, 4, 3, 3, true);
        Furnish.sack(s, 6, 3, 3, false);
        Furnish.sack(s, 4, 4, 3, false);
        put(s, 2, 4, 5, Kit.lantern());
        put(s, 8, 4, 5, Kit.lantern());
        hang(s, 5, 6, 4);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the watch tower

    /** Rises of the needle of the watch tower: steep from its eaves and steeper towards its point. */
    private static final int[] TOWER_NEEDLE = {2, 3, 4};

    /**
     * The watch tower (7 by 7, like the timber watch tower): four tall legs of logs on a floor of rough stone, tied by a ring
     * of beams halfway up with brackets under it and knee braces under the deck; a ladder up a mast in the middle; the deck
     * jutting out one block on every side over brackets, a parapet of staves round its edge; four posts on the legs carry
     * the plate of a steep needle of the spires that overhangs the parapet, a pennant on its vane, lanterns hanging under
     * its eaves and shields of the cloth hung from its posts. At the foot a barrel, a crate and firewood for the watch fire
     * on the deck; before the entrance a standard and a lamp.
     */
    static Structure tower() {
        Structure s = new Structure(7, 26, 7);
        s.fill(1, 0, 1, 5, 0, 5, FOOTING);
        put(s, 3, 0, 6, Kit.stairs(FOOTING_STAIRS, "north"));
        // The legs, the ring of beams halfway up with brackets under it, the knee braces under the deck.
        for (int[] c : new int[][]{{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
            fill(s, c[0], 1, c[1], c[0], 8, c[1], POST);
        }
        s.beam(1, 1, 5, 5, 4, Nordic.STAVE);
        for (int y : new int[]{3, 8}) {
            for (int z : new int[]{1, 5}) {
                put(s, 2, y, z, Kit.upside(STAIRS, "west"));
                put(s, 4, y, z, Kit.upside(STAIRS, "east"));
            }
            for (int x : new int[]{1, 5}) {
                put(s, x, y, 2, Kit.upside(STAIRS, "north"));
                put(s, x, y, 4, Kit.upside(STAIRS, "south"));
            }
        }
        // The mast with the ladder, the deck jutting out over brackets, the parapet round it.
        fill(s, 3, 1, 3, 3, 8, 3, STAVE);
        for (int u : new int[]{1, 3, 5}) {
            put(s, u, 8, 0, Kit.upside(STAIRS, "south"));
            put(s, u, 8, 6, Kit.upside(STAIRS, "north"));
            put(s, 0, 8, u, Kit.upside(STAIRS, "east"));
            put(s, 6, 8, u, Kit.upside(STAIRS, "west"));
        }
        s.fill(0, 9, 0, 6, 9, 6, PLANKS);
        s.beam(0, 0, 6, 6, 9, LOG);
        fill(s, 3, 1, 4, 3, 9, 4, Kit.ladder("south"));
        s.walls(0, 10, 0, 6, 10, 6, STAVE);
        s.corners(0, 10, 0, 6, 10, 6, LOG);
        // The posts on the legs, the plate and the needle overhanging the parapet.
        for (int[] c : new int[][]{{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
            fill(s, c[0], 10, c[1], c[0], 13, c[1], POST);
        }
        s.beam(1, 1, 5, 5, 14, LOG);
        Nordic.needle(s, 3, 14, 3, 7, TOWER_NEEDLE, Nordic.SPIRE);
        // Shields hung from the posts, lanterns under the eaves and in the middle, the watch fire on its hearth.
        put(s, 0, 12, 1, Kit.wallBanner("red", "west"));
        put(s, 6, 12, 5, Kit.wallBanner("red", "east"));
        put(s, 5, 12, 0, Kit.wallBanner("white", "north"));
        put(s, 1, 12, 6, Kit.wallBanner("white", "south"));
        for (int[] c : new int[][]{{3, 0}, {3, 6}, {0, 3}, {6, 3}}) {
            hang(s, c[0], 13, c[1]);
        }
        hang(s, 3, 12, 3);
        put(s, 4, 9, 2, FOOTING);
        put(s, 4, 10, 2, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        // At the foot: the stores of the watch, firewood, a lantern under the ring.
        put(s, 2, 1, 2, Kit.barrel());
        Furnish.crate(s, 4, 1, 2, "spruce");
        fill(s, 2, 1, 4, 2, 2, 4, Kit.log(FIREWOOD, "z"));
        hang(s, 3, 3, 1);
        hang(s, 3, 3, 5);
        // Before the entrance: a standard and a lamp.
        standard(s, 1, 0, 6, 2, "red");
        lampPost(s, 5, 0, 6, 2);
        s.connect();
        return s.trimSky(3);
    }
}
