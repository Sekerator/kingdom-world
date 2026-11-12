package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The field camp of an army of the style "eastern" (see {@link Eastern}): the four buildings a camp raises, each with the
 * footprint, the entrance to the south, the role and the counting blocks of the plain building of a camp it stands for
 * (barracks hall, drill yard, barn and timber watch tower), so that a camp of a kingdom of this style lays these instead.
 * A camp of the east is light timber on footings of stone under curving roofs of tiles: dark posts with boards below and
 * white plaster above, slatted windows and sliding screens of paper, red pillars and red lanterns of cloth under the eaves,
 * and everywhere the tall narrow banners of the army (nobori) on their poles. The training ground is closed by camp
 * curtains of white cloth, the archers shoot at round targets set in a mound of beaten earth under a little roof, the
 * store is a storehouse of thick white walls, and the watch tower is a tall frame of four posts carrying a lookout with a
 * red railing, a fire bell and a roof with corners swept up.
 */
final class EasternCamp {
    private static final String POST_Y = Kit.log(Eastern.POST, "y");
    private static final String PLASTER = Eastern.PLASTER;
    private static final String PLANKS = Eastern.PLANKS;
    private static final String FLOOR = Eastern.FLOOR;
    private static final String FENCE = Eastern.FENCE;
    private static final String ROUGH = Eastern.ROUGH;
    private static final String STONE = Eastern.STONE;
    private static final String TRIM = Eastern.TRIM;
    private static final Kit.Roof ROOF = Eastern.ROOF;
    /** The beaten earth of the archery mound: packed mud, a fixed accent. */
    private static final String EARTH = "minecraft:packed_mud";
    private static final String TARGET = "minecraft:target[power=0]";
    private static final String STRIPPED = "minecraft:stripped_spruce_log";

    private EasternCamp() {
    }

    /**
     * The four buildings of the camp: the barracks, the training ground, the store and the watch tower.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("eastern_camp_barracks", "defense", "barracks", "kingdomworld:barracks", 6, 0, 10,
                barracks(), "barracks", 1, "").params("\"bunks\": 12").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("eastern_camp_yard", "defense", "yard", "minecraft:target", 6, 1, 10, yard(),
                "none", 1, "").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("eastern_camp_store", "farming", "warehouse", "kingdomworld:storehouse", 6, 1, 10,
                store(), "warehouse", 1, "").params("\"containers\": 12").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("eastern_camp_tower", "defense", "tower", "minecraft:oak_fence", 3, 0, 6, tower(),
                "tower", 1, "").at("door", 3, 0, 6));
        return list;
    }

    // ---------------------------------------------------------------- drawing inside the box

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static void fill(Structure s, int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    put(s, x, y, z, state);
                }
            }
        }
    }

    private static boolean solid(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && s.solid(x, y, z);
    }

    private static String grindstone(String facing) {
        return "minecraft:grindstone[face=floor,facing=" + facing + "]";
    }

    /** A bale of rice (tawara): a bale of straw lying along its axis. */
    private static String bale(String axis) {
        return "minecraft:hay_block[axis=" + axis + "]";
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

    // ---------------------------------------------------------------- shared shapes

    /**
     * Boards along the foot of the walls of a frame on the rectangle: every cell of its walls from layer {@code y1} to
     * {@code y2} that is not a post becomes a board of timber, so that the walls are dark below and white above.
     */
    private static void wainscot(Structure s, int x1, int z1, int x2, int z2, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                    if (wall && !s.get(x, y, z).equals(POST_Y)) {
                        put(s, x, y, z, PLANKS);
                    }
                }
            }
        }
    }

    /**
     * A banner of the army (nobori): a foot of stone at layer {@code y}, a pole of timber {@code height} layers high on it and
     * the long banner of the cloth of the kingdom on its top, looking the way of {@code rotation} (0 south).
     */
    private static void nobori(Structure s, int x, int y, int z, int height, int rotation) {
        put(s, x, y, z, TRIM);
        fill(s, x, y + 1, z, x, y + height, z, FENCE);
        put(s, x, y + height + 1, z, Kit.banner("red", rotation));
    }

    /** A spear standing in its rack: a shaft of fence two high with a tip of copper. */
    private static void spear(Structure s, int x, int y, int z) {
        put(s, x, y, z, FENCE);
        put(s, x, y + 1, z, FENCE);
        put(s, x, y + 2, z, Eastern.ROD);
    }

    /** The lowest layer a piece of a curved roof of surface height {@code h} and rise {@code rise} takes. */
    private static int pieceBottom(int y, int h, int rise) {
        if (rise <= 1) {
            return h % 2 == 1 ? y + (h - 1) / 2 : y + h / 2 - 1;
        }
        return y + (h + 1) / 2 - 1 - (rise / 2 - 1);
    }

    /**
     * One piece of a curved roof whose surface stands {@code h} half blocks above the eaves at layer {@code y}: a slab where
     * it rises half a block, a stair where it rises one, full blocks under a stair where it rises more.
     */
    private static void piece(Structure s, int x, int y, int z, int h, int rise, String stair) {
        if (rise <= 1) {
            put(s, x, h % 2 == 1 ? y + (h - 1) / 2 : y + h / 2 - 1, z, h % 2 == 1 ? Kit.slab(ROOF.slab()) : Kit.topSlab(ROOF.slab()));
            return;
        }
        int top = y + (h + 1) / 2 - 1;
        for (int k = 1; k < rise / 2; k++) {
            put(s, x, top - k, z, ROOF.block());
        }
        put(s, x, top, z, stair);
    }

    /**
     * A curved gable roof of tiles over the rectangle of its eaves {@code x1..x2, z1..z2} whose ridge runs along z (the gable
     * to the street) whatever the proportions of the rectangle (an odd width across the ridge): rows rising from both eaves
     * as {@link Eastern#heights} says, a ridge of full blocks with horns at its ends, gables one block inside the ends of
     * white plaster with a king post of timber, filled from the layer {@code gableFrom} up, and the four corners swept up.
     *
     * @return the layer of the ridge
     */
    private static int gableToStreet(Structure s, int x1, int z1, int x2, int z2, int y, int pitch, int lift, int gableFrom) {
        int rings = (x2 - x1) / 2;
        int[] h = Eastern.heights(rings, pitch);
        for (int k = 0; k < rings; k++) {
            int rise = h[k] - (k == 0 ? 0 : h[k - 1]);
            for (int z = z1; z <= z2; z++) {
                piece(s, x1 + k, y, z, h[k], rise, Kit.stairs(ROOF.stairs(), "east"));
                piece(s, x2 - k, y, z, h[k], rise, Kit.stairs(ROOF.stairs(), "west"));
            }
        }
        int top = y + (h[rings - 1] + 1) / 2;
        int mx = x1 + rings;
        for (int z : new int[]{z1 + 1, z2 - 1}) {
            for (int x = x1 + 1; x <= x2 - 1; x++) {
                int k = Math.min(x - x1, x2 - x);
                int under = k >= rings ? top - 2 : pieceBottom(y, h[k], h[k] - (k == 0 ? 0 : h[k - 1])) - 1;
                for (int yy = gableFrom; yy <= under; yy++) {
                    if (!solid(s, x, yy, z)) {
                        put(s, x, yy, z, x == mx ? POST_Y : PLASTER);
                    }
                }
            }
        }
        for (int z = z1; z <= z2; z++) {
            put(s, mx, top - 1, z, ROOF.block());
            put(s, mx, top, z, ROOF.block());
        }
        put(s, mx, top + 1, z1, Kit.stairs(ROOF.stairs(), "north"));
        put(s, mx, top + 1, z2, Kit.stairs(ROOF.stairs(), "south"));
        if (lift > 0) {
            Eastern.sweep(s, x1, z1, x2, z2, y, lift, ROOF, true, false);
        }
        return top;
    }

    // ---------------------------------------------------------------- the barracks

    /**
     * The barracks of the camp: a hall of light timber on a low platform of stone, its frame of dark posts with boards along
     * the foot and white plaster above, under a hip-and-gable roof of tiles with white gables and corners swept up. Before
     * its front runs a veranda under the eaves on four red pillars whose brackets carry the beam; behind them the door
     * between two sliding screens of paper under a curtain of cloth, slatted windows to both sides, and red lanterns of cloth
     * hanging over the veranda. Windows of paper with sills and little hoods look out of the back and the sides. Banners of
     * the army stand at the four corners; spears, a water jar, firewood and lanterns under the side eaves. Inside, on mats
     * along both side walls, twelve beds on the floor for the soldiers, six to a side, their heads to the walls; down the
     * middle the barracks block at the back under the banner of the army between two racks of swords, a low table with
     * cushions, a chest and a water jar by the door, and lanterns hanging from the ceiling.
     */
    static Structure barracks() {
        Structure s = new Structure(13, 15, 11);
        Eastern.platform(s, 1, 0, 11, 10, 0, 0, 0, null);
        fill(s, 3, 0, 2, 9, 0, 7, FLOOR);
        fill(s, 2, 0, 9, 10, 0, 9, FLOOR);
        Eastern.tatami(s, 3, 2, 4, 7, 0, false);
        Eastern.tatami(s, 8, 2, 9, 7, 0, false);
        // the hall: dark posts, boards along the foot and white plaster above, the head beam at layer 5
        Eastern.frame(s, 2, 1, 10, 8, 0, 5, 0, POST_Y, PLASTER);
        for (int z = 2; z <= 7; z++) {
            boolean post = z == 3 || z == 6;
            fill(s, 2, 1, z, 2, 4, z, post ? POST_Y : PLASTER);
            fill(s, 10, 1, z, 10, 4, z, post ? POST_Y : PLASTER);
        }
        for (int x = 3; x <= 9; x++) {
            boolean back = x % 2 == 0;
            fill(s, x, 1, 1, x, 4, 1, back ? POST_Y : PLASTER);
            boolean front = x == 4 || x == 8;
            fill(s, x, 1, 8, x, 4, 8, front ? POST_Y : PLASTER);
        }
        wainscot(s, 2, 1, 10, 8, 1, 1);
        // the front: the door between two screens of paper under a curtain, slatted windows to the sides
        s.door(6, 1, 8, Eastern.DOOR, "north", "left");
        put(s, 6, 3, 8, Eastern.FENCE);
        Eastern.screen(s, 5, 8, 5, 8, 1, 3, false);
        Eastern.screen(s, 7, 8, 7, 8, 1, 3, false);
        Eastern.lattice(s, 3, 8, 3, 8, 2, 3);
        Eastern.lattice(s, 9, 8, 9, 8, 2, 3);
        Eastern.noren(s, 5, 8, 7, 8, 4, "south");
        // the veranda before it: red pillars carrying a beam with brackets under the eaves
        for (int x : new int[]{2, 4, 8, 10}) {
            Eastern.pillar(s, x, 1, 4, 9, false);
        }
        fill(s, 2, 5, 9, 10, 5, 9, Kit.log(Eastern.POST, "x"));
        for (int x : new int[]{2, 4, 8, 10}) {
            Eastern.bracket(s, x, 5, 9, "south", 1);
        }
        // windows of paper under little hoods at the back and on the sides
        Eastern.window(s, 3, 2, 1, "north", 1, 2);
        Eastern.window(s, 9, 2, 1, "north", 1, 2);
        Eastern.lattice(s, 5, 1, 5, 1, 3, 4);
        Eastern.lattice(s, 7, 1, 7, 1, 3, 4);
        for (int z : new int[]{4, 5}) {
            Eastern.window(s, 2, 2, z, "west", 1, 2);
            Eastern.window(s, 10, 2, z, "east", 1, 2);
        }
        fill(s, 3, 5, 2, 9, 5, 7, PLANKS);
        Eastern.roof(s, 1, 0, 11, 10, 6, 2, 1, 1, ROOF, PLASTER);
        // twelve beds on the mats along the long walls, their heads to the walls
        for (int z = 2; z <= 7; z++) {
            Eastern.futon(s, 4, 1, z, "west");
            Eastern.futon(s, 8, 1, z, "east");
        }
        // down the middle: the barracks block under the banner between the racks, the low table, the chest and the jar
        put(s, 6, 1, 2, "kingdomworld:barracks[facing=south]");
        put(s, 6, 4, 2, Kit.wallBanner("red", "south"));
        put(s, 5, 1, 2, grindstone("south"));
        put(s, 7, 1, 2, grindstone("south"));
        Eastern.lowTable(s, 6, 4, 6, 5, 1);
        put(s, 6, 2, 4, "minecraft:flower_pot");
        Furnish.chest(s, 5, 1, 7, "east");
        put(s, 7, 1, 7, "minecraft:water_cauldron[level=3]");
        put(s, 6, 4, 3, Kit.hangingLantern());
        put(s, 6, 4, 6, Kit.hangingLantern());
        put(s, 3, 4, 7, Kit.hangingLantern());
        put(s, 9, 4, 7, Kit.hangingLantern());
        // under the eaves: red lanterns on the veranda, spears and firewood along the sides
        Eastern.clothLantern(s, 3, 9, 4, 0);
        Eastern.clothLantern(s, 9, 9, 4, 0);
        spear(s, 1, 1, 2);
        spear(s, 1, 1, 3);
        put(s, 1, 1, 7, "minecraft:water_cauldron[level=3]");
        fill(s, 11, 1, 2, 11, 2, 3, Kit.log("minecraft:oak_log", "z"));
        Furnish.sack(s, 11, 1, 7, false);
        put(s, 1, 5, 7, Kit.hangingLantern());
        put(s, 11, 5, 7, Kit.hangingLantern());
        put(s, 6, 5, 0, Kit.hangingLantern());
        // the banners of the army at the corners
        nobori(s, 0, 0, 10, 5, 0);
        nobori(s, 12, 0, 10, 5, 0);
        nobori(s, 0, 0, 0, 5, 0);
        nobori(s, 12, 0, 0, 5, 0);
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the training ground

    /**
     * The training ground of the camp, level with the field: along both sides camp curtains of white cloth hang over a rail
     * between dark posts, a rail closes the front. At the back a mound of beaten earth on a course of rough stone holds three
     * round targets for the archers under a little roof of tiles laid on its top, lanterns hanging under its eaves. In the
     * field two straw men with arms and flat hats stand on posts for the sword and the spear; in the west corner a rack of
     * spears under a little roof of its own with racks of swords before it; in the east corner the war drum on its stand, a
     * bench and a jar of water. The way in passes a gate of two posts with a tie beam carrying the plaque of the army and a
     * top beam under a cap of tiles whose ends turn up, lanterns hanging from its ends, between two banners of the army;
     * stone lanterns stand at the front corners and two more banners at the back corners.
     */
    static Structure yard() {
        Structure s = new Structure(13, 10, 11);
        fill(s, 0, 0, 0, 12, 0, 10, "minecraft:coarse_dirt");
        fill(s, 1, 0, 2, 11, 0, 3, "minecraft:sand");
        fill(s, 5, 0, 4, 7, 0, 10, Eastern.GRAVEL);
        // the camp curtains along the sides: white cloth hanging over a rail between dark posts; a rail along the front
        for (int z = 1; z <= 8; z++) {
            boolean post = z == 1 || z == 5 || z == 8;
            for (int x : new int[]{0, 12}) {
                if (post) {
                    fill(s, x, 1, z, x, 3, z, POST_Y);
                } else {
                    put(s, x, 1, z, FENCE);
                    put(s, x, 2, z, Kit.banner("white", x == 0 ? 12 : 4));
                }
            }
        }
        fill(s, 2, 1, 9, 3, 1, 9, FENCE);
        fill(s, 9, 1, 9, 11, 1, 9, FENCE);
        fill(s, 0, 1, 9, 0, 2, 9, POST_Y);
        fill(s, 12, 1, 9, 12, 2, 9, POST_Y);
        put(s, 0, 3, 9, Kit.slab(Eastern.SLAB));
        put(s, 12, 3, 9, Kit.slab(Eastern.SLAB));
        // the archery mound with its targets under a little roof of tiles laid on its top
        fill(s, 1, 1, 0, 11, 3, 1, EARTH);
        fill(s, 1, 1, 0, 11, 1, 0, ROUGH);
        fill(s, 1, 1, 1, 1, 1, 1, ROUGH);
        fill(s, 11, 1, 1, 11, 1, 1, ROUGH);
        for (int x : new int[]{3, 6, 9}) {
            put(s, x, 2, 1, TARGET);
        }
        Eastern.roof(s, 1, 0, 11, 2, 4, Eastern.GABLE, 1, 1, ROOF, null);
        put(s, 4, 3, 2, Kit.hangingLantern());
        put(s, 8, 3, 2, Kit.hangingLantern());
        // straw men on posts with arms
        for (int x : new int[]{3, 9}) {
            put(s, x, 1, 5, FENCE);
            put(s, x, 2, 5, bale("y"));
            put(s, x - 1, 2, 5, FENCE);
            put(s, x + 1, 2, 5, FENCE);
            put(s, x, 3, 5, bale("x"));
            put(s, x, 4, 5, Kit.slab(Eastern.SLAB));
        }
        // the rack of spears under its own little roof, racks of swords before it
        fill(s, 1, 1, 4, 1, 3, 4, POST_Y);
        fill(s, 1, 1, 9, 1, 3, 9, POST_Y);
        fill(s, 1, 4, 4, 1, 4, 9, Kit.log(Eastern.POST, "z"));
        for (int z = 5; z <= 8; z++) {
            spear(s, 1, 1, z);
        }
        Eastern.roof(s, 0, 4, 2, 9, 5, Eastern.GABLE, 0, 1, ROOF, null);
        put(s, 2, 1, 6, grindstone("east"));
        put(s, 2, 1, 7, grindstone("east"));
        // the war drum on its stand between two legs, its head to the field, a bench and a jar of water
        fill(s, 9, 1, 7, 9, 2, 7, FENCE);
        fill(s, 11, 1, 7, 11, 2, 7, FENCE);
        put(s, 10, 1, 7, FENCE);
        put(s, 10, 2, 7, "minecraft:barrel[facing=south,open=false]");
        for (int z = 4; z <= 5; z++) {
            put(s, 11, 1, z, Kit.stairs(Eastern.FLOOR_STAIRS, "east"));
        }
        put(s, 11, 1, 8, "minecraft:water_cauldron[level=3]");
        // the gate: two posts, a tie beam with the plaque of the army, a top beam reaching out under a cap of tiles whose
        // ends turn up, lanterns hanging from the ends of the top beam
        fill(s, 4, 1, 9, 4, 4, 9, POST_Y);
        fill(s, 8, 1, 9, 8, 4, 9, POST_Y);
        fill(s, 5, 4, 9, 7, 4, 9, Kit.log(Eastern.POST, "x"));
        put(s, 6, 4, 10, Kit.wallBanner("red", "south"));
        fill(s, 3, 5, 9, 9, 5, 9, Kit.log(Eastern.POST, "x"));
        fill(s, 4, 6, 9, 8, 6, 9, Kit.slab(ROOF.slab()));
        put(s, 3, 6, 9, Kit.stairs(ROOF.stairs(), "west"));
        put(s, 9, 6, 9, Kit.stairs(ROOF.stairs(), "east"));
        put(s, 3, 4, 9, Kit.hangingLantern());
        put(s, 9, 4, 9, Kit.hangingLantern());
        // banners at the gate and the back corners, stone lanterns at the front corners
        nobori(s, 2, 0, 10, 4, 0);
        nobori(s, 10, 0, 10, 4, 0);
        nobori(s, 0, 0, 0, 5, 0);
        nobori(s, 12, 0, 0, 5, 0);
        Eastern.stoneLantern(s, 0, 1, 10, false);
        Eastern.stoneLantern(s, 12, 1, 10, false);
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the store

    /**
     * The store of the army in the field: a storehouse (kura) of thick white plaster walls over a skirt of dark boards on a
     * footing of rough stone, posts of timber only at its corners, small barred windows with sills and hoods high in the
     * sides and the back, under a curving gable roof of tiles with its gable to the street, the banner of the army hanging
     * on the white gable. Its door is a heavy frame of timber with both leaves standing open under a slatted transom and a
     * little pent roof on brackets. Inside, along the walls: the two storehouse blocks at the back, barrels of rice, water
     * and powder stacked two high, chests of arrows and armour, bales of rice and sacks; lanterns hang from the ceiling.
     * Outside, piles of rice bales under the west eaves and at the front, firewood and sacks under the east eaves, casks and a
     * jar of water at the front, lanterns under the eaves and two banners at the front corners.
     */
    static Structure store() {
        Structure s = new Structure(13, 15, 11);
        fill(s, 2, 0, 1, 10, 0, 9, ROUGH);
        fill(s, 3, 0, 2, 9, 0, 8, FLOOR);
        fill(s, 5, 0, 9, 7, 0, 10, STONE);
        put(s, 4, 0, 10, TRIM);
        put(s, 8, 0, 10, TRIM);
        // thick walls of white plaster over a skirt of dark boards, posts of timber only at the corners, a head beam
        s.walls(2, 1, 1, 10, 4, 9, PLASTER);
        wainscot(s, 2, 1, 10, 9, 1, 1);
        for (int[] c : new int[][]{{2, 1}, {10, 1}, {2, 9}, {10, 9}}) {
            fill(s, c[0], 1, c[1], c[0], 4, c[1], POST_Y);
        }
        s.beam(2, 1, 10, 9, 5, Eastern.POST);
        // the door: a heavy frame of timber with its two leaves standing open, a pent roof on brackets over it
        fill(s, 4, 1, 9, 4, 4, 9, POST_Y);
        fill(s, 8, 1, 9, 8, 4, 9, POST_Y);
        fill(s, 5, 4, 9, 7, 4, 9, Kit.log(Eastern.POST, "x"));
        fill(s, 5, 1, 9, 7, 3, 9, Structure.AIR);
        s.door(5, 1, 9, Eastern.DOOR, "south", "left");
        s.door(7, 1, 9, Eastern.DOOR, "south", "right");
        for (int y = 1; y <= 2; y++) {
            put(s, 5, y, 9, s.get(5, y, 9).replace("open=false", "open=true"));
            put(s, 7, y, 9, s.get(7, y, 9).replace("open=false", "open=true"));
        }
        put(s, 6, 3, 9, Eastern.FENCE);
        put(s, 5, 3, 9, Eastern.FENCE);
        put(s, 7, 3, 9, Eastern.FENCE);
        for (int x = 4; x <= 8; x++) {
            put(s, x, 5, 10, Kit.stairs(ROOF.stairs(), "north"));
        }
        put(s, 3, 5, 10, Kit.stairs(ROOF.stairs(), "west"));
        put(s, 9, 5, 10, Kit.stairs(ROOF.stairs(), "east"));
        put(s, 4, 4, 10, Kit.upside(Eastern.STAIRS, "north"));
        put(s, 8, 4, 10, Kit.upside(Eastern.STAIRS, "north"));
        // small barred windows under little hoods high in the sides and the back
        for (int x : new int[]{2, 10}) {
            put(s, x, 3, 5, Kit.BARS);
            put(s, x + (x == 2 ? -1 : 1), 4, 5, Kit.slab(Eastern.SLAB));
            put(s, x + (x == 2 ? -1 : 1), 2, 5, Kit.upside(Eastern.STAIRS, x == 2 ? "east" : "west"));
        }
        for (int x : new int[]{4, 8}) {
            put(s, x, 3, 1, Kit.BARS);
            put(s, x, 4, 0, Kit.slab(Eastern.SLAB));
            put(s, x, 2, 0, Kit.upside(Eastern.STAIRS, "south"));
        }
        fill(s, 3, 5, 2, 9, 5, 8, PLANKS);
        gableToStreet(s, 1, 0, 11, 10, 6, 1, 1, 6);
        put(s, 6, 8, 10, Kit.wallBanner("red", "south"));
        // the back wall: the storehouse blocks among barrels and a chest of armour
        put(s, 5, 1, 2, "kingdomworld:storehouse");
        put(s, 7, 1, 2, "kingdomworld:storehouse");
        put(s, 6, 1, 2, Kit.chest("south"));
        for (int x : new int[]{3, 4, 8, 9}) {
            put(s, x, 1, 2, Kit.barrel());
        }
        put(s, 3, 2, 2, Kit.barrel());
        put(s, 9, 2, 2, Kit.barrel());
        // the east wall: barrels stacked two high and chests of arrows
        for (int z : new int[]{3, 6}) {
            put(s, 9, 1, z, Kit.barrel());
            put(s, 9, 2, z, Kit.barrel());
        }
        put(s, 9, 1, 4, Kit.chest("west"));
        put(s, 9, 1, 5, Kit.chest("west"));
        put(s, 9, 1, 7, Kit.barrel());
        // the west wall: a chest, bales of rice stacked high and sacks
        put(s, 3, 1, 3, Kit.chest("east"));
        fill(s, 3, 1, 4, 3, 2, 6, bale("z"));
        put(s, 3, 3, 5, bale("z"));
        Furnish.sack(s, 3, 1, 7, false);
        Furnish.sack(s, 3, 1, 8, true);
        Furnish.sack(s, 4, 1, 8, false);
        put(s, 9, 1, 8, Kit.barrel());
        // light
        put(s, 6, 4, 4, Kit.hangingLantern());
        put(s, 6, 4, 7, Kit.hangingLantern());
        put(s, 6, 4, 10, Kit.hangingLantern());
        put(s, 1, 5, 3, Kit.hangingLantern());
        put(s, 11, 5, 3, Kit.hangingLantern());
        // outside: piles of rice bales under the west eaves and at the front, firewood and sacks under the east eaves,
        // casks of water at the front, banners at the front corners
        for (int z0 : new int[]{2, 6}) {
            fill(s, 1, 1, z0, 1, 1, z0 + 2, bale("z"));
            put(s, 1, 2, z0 + 1, bale("z"));
        }
        fill(s, 1, 1, 10, 3, 1, 10, bale("x"));
        put(s, 2, 2, 10, bale("x"));
        fill(s, 11, 1, 2, 11, 2, 4, Kit.log("minecraft:oak_log", "z"));
        Furnish.sack(s, 11, 1, 6, false);
        Furnish.sack(s, 11, 1, 7, true);
        Furnish.sack(s, 11, 1, 8, false);
        Furnish.sack(s, 11, 2, 7, false);
        put(s, 10, 1, 10, Kit.barrel());
        put(s, 11, 1, 10, Kit.barrel());
        put(s, 9, 1, 10, "minecraft:water_cauldron[level=3]");
        nobori(s, 0, 0, 10, 5, 0);
        nobori(s, 12, 0, 10, 5, 0);
        keepGround(s);
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the watch tower

    /**
     * The watch tower of the camp (yagura): four tall posts of dark timber on stones, tied by two rings of beams, carry a
     * platform reaching out on braces; a ladder climbs a mast in the middle through the platform. On the platform a red
     * railing runs around the lookout, whose four posts carry a little hip roof of tiles with its corners swept up and the
     * banner of the army on a pole over its point; under the roof hang the fire bell and lanterns, and a red lantern of
     * cloth hangs under the front eave. At its foot a tub of water against fire, a barrel of arrows and two stone lanterns
     * before the way in.
     */
    static Structure tower() {
        Structure s = new Structure(7, 24, 7);
        fill(s, 1, 0, 1, 5, 0, 5, ROUGH);
        put(s, 3, 0, 6, Kit.slab(Eastern.TRIM_SLAB));
        int[][] legs = {{1, 1}, {5, 1}, {1, 5}, {5, 5}};
        for (int[] leg : legs) {
            put(s, leg[0], 0, leg[1], TRIM);
        }
        // two rings of beams tie the posts together
        for (int y : new int[]{3, 7}) {
            s.beam(1, 1, 5, 5, y, Eastern.POST);
        }
        // the platform on a rim of logs, braces under its edge
        s.beam(0, 0, 6, 6, 10, Eastern.POST);
        fill(s, 1, 10, 1, 5, 10, 5, PLANKS);
        for (int[] leg : legs) {
            int sx = leg[0] == 1 ? -1 : 1;
            int sz = leg[1] == 1 ? -1 : 1;
            put(s, leg[0] + sx, 9, leg[1], Kit.upside(Eastern.STAIRS, sx < 0 ? "east" : "west"));
            put(s, leg[0], 9, leg[1] + sz, Kit.upside(Eastern.STAIRS, sz < 0 ? "south" : "north"));
        }
        // the posts run on up through the platform and carry the roof of the lookout
        for (int[] leg : legs) {
            fill(s, leg[0], 1, leg[1], leg[0], 13, leg[1], POST_Y);
        }
        s.beam(1, 1, 5, 5, 14, Eastern.POST);
        for (int[] leg : legs) {
            put(s, leg[0], 14, leg[1], POST_Y);
        }
        fill(s, 2, 14, 2, 4, 14, 4, PLANKS);
        // the mast with the ladder up through the platform
        fill(s, 3, 1, 3, 3, 9, 3, Kit.log(STRIPPED, "y"));
        for (int y = 1; y <= 10; y++) {
            put(s, 3, y, 4, Kit.ladder("south"));
        }
        // the red railing around the platform
        Eastern.balcony(s, 0, 0, 6, 6, 11);
        // the roof, the banner on its point, the bell and the lanterns under it
        int point = Eastern.roof(s, 0, 0, 6, 6, 15, Eastern.HIP, 1, 1, ROOF, null);
        fill(s, 3, point, 3, 3, point + 1, 3, FENCE);
        put(s, 3, point + 2, 3, Kit.banner("red", 0));
        put(s, 3, 13, 3, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        put(s, 2, 13, 2, Kit.hangingLantern());
        put(s, 4, 13, 4, Kit.hangingLantern());
        Eastern.clothLantern(s, 3, 6, 14, 0);
        // at the foot: water against fire, arrows, a lantern under the beams and two stone lanterns
        put(s, 2, 1, 2, "minecraft:water_cauldron[level=3]");
        put(s, 4, 1, 2, Kit.barrel());
        put(s, 2, 2, 5, Kit.hangingLantern());
        Eastern.stoneLantern(s, 0, 0, 6, false);
        Eastern.stoneLantern(s, 6, 0, 6, false);
        s.connect();
        return s.trimSky(3);
    }
}
