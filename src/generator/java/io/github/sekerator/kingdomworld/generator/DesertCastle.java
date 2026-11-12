package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle of the style "desert": a kasbah citadel of sandstone and mud brick that reads from afar as one mass, its great
 * dome and its one tall tower rising high over battered walls.
 *
 * <p>The fortress: walls of the wall stone three blocks thick on a battered plinth of rough stone, a corbel table and a band
 * of trim under their parapet, pointed merlons on the outer edge and the walk behind them, lit by lanterns on posts of rough
 * stone. Few square towers of the kasbah, each of its own size and height (a strong one at a back corner, a slimmer one at
 * the other, low ones at the front), drawn in two stages that step in as the towers of mud brick do. In the middle of the
 * front the gate: a block between two towers pierced by a great horseshoe arch in its frame of rough stone (alfiz), a
 * lattice in the head of the arch, a band of tiles over it and the walk crossing it in a gallery.
 *
 * <p>Inside, the palace: the throne hall, a square of thick walls under a ribbed dome of the spires on a drum with windows;
 * inside, two arcades of pointed arches on slender columns divide it into a nave and aisles (four free columns in the small
 * castle), the throne on its dais under a canopy at the north end of the nave before a panel of tiles, wall fountains,
 * divans along the aisles and a great lamp hanging from the dome. Its front is a raised portal (pishtaq) with a pointed
 * niche in its frame and the horseshoe doorway at the back of the niche. Low wings flank the hall under terraces: the
 * council room and a loggia open on the court behind an arcade (majlis) on one side, the treasury behind iron bars and the
 * armoury on the other, with small domes over the great castle's. Behind the hall rises the one dominant of the castle:
 * the main tower, a square keep on a battered foot with tall panels framed in rough stone and keyhole windows, a band of
 * glazed tiles and a corbelled parapet, and rising from its deck a minaret with its band of tiles, its balcony, its open
 * lantern and its small dome, far above everything else; inside floors on a ladder up to the deck and the door of the
 * minaret, its foot a library (the armoury in the small castle). At its sides, against the back wall, the royal chambers
 * under a small dome and the kitchen under a wind tower, each with a court of its own in the larger castles: a garden with
 * a star fountain and palms, and the court of the kitchen. Before the hall, between the gate and the portal, the court of
 * the pools: long basins of water lined with tiles between palms and lamps.
 *
 * <p>One program drawn in three sizes: the small castle 31 by 31 (its front towers are the towers of the gate and its walk
 * is reached from the roofs of the wings), the castle 41 by 41 and the great castle 47 by 47. The size gives the numbers
 * of every part; the idea stays the same. The throne stands in the middle of the ground on the street layer at the north
 * end of its hall, facing south; the castle leaves it and the ground under it out. Layer 0 replaces the ground, layer 1 is
 * level with the street. Every part is drawn with the blocks that stand for the parts of a look (see {@link Kit} and
 * {@link Desert}): sandstone or mud brick for the walls, red sandstone for plinths, frames and copings, turquoise, gilt or
 * white for the domes.
 */
final class DesertCastle {
    private static final String AIR = Structure.AIR;
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String IRON_DOOR = "minecraft:iron_door";
    private static final String WALL = Desert.WALL;
    private static final String BASE = Desert.BASE;
    private static final String TRIM = Desert.TRIM;
    private static final int SQUARE = Desert.SQUARE;

    /** The three sizes of the castle. */
    enum Size {
        SMALL, MEDIUM, LARGE
    }

    private DesertCastle() {
    }

    /**
     * The three castles of the style: small, medium and great, with their named places.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(building(Size.SMALL, "desert_castle_small", "minecraft:chiseled_sandstone"));
        list.add(building(Size.MEDIUM, "desert_castle_medium", "minecraft:chiseled_sandstone"));
        list.add(building(Size.LARGE, "desert_castle_large", "minecraft:gold_block"));
        return list;
    }

    /** One castle of a size as a template: a palace of defence, anchored in the middle of its front, with its named places. */
    private static BuildingGenerator.Building building(Size size, String name, String icon) {
        Plan p = new Plan(size);
        Structure s = castle(p);
        BuildingGenerator.Building b = new BuildingGenerator.Building(name, "defense", "palace", icon, p.c, 1, p.n - 1, s, "none", 1, "");
        b = b.at("door", p.c, 1, p.n - 1);
        for (int[] point : p.points) {
            b = b.at(POINT_NAMES[point[0]], point[1], point[2], point[3]);
        }
        return b;
    }

    private static final String[] POINT_NAMES = {"palace_hall", "palace_door", "castle_gate", "castle_wall", "treasury"};
    private static final int HALL = 0;
    private static final int PALACE_DOOR = 1;
    private static final int GATE = 2;
    private static final int WALK = 3;
    private static final int TREASURY = 4;

    // ---------------------------------------------------------------- the plan of a size

    /**
     * The numbers of a castle of a size: its ground, its walls and towers, the gate, the main tower, the hall and its wings,
     * the blocks at the back and the places named in it.
     */
    private static final class Plan {
        final Size size;
        /** The ground (square), the height of the box and its middle: the cell of the throne. */
        final int n;
        final int height;
        final int c;
        /** The outer face of the walls from every edge, and the layer of the floor of the walk (one walks on the layer above). */
        final int o;
        final int f;
        /** The towers of the walls: {middle x, middle z, width, height, 1 for a tower of the gate}. */
        final int[][] towers;
        /** The gate: width and jambs of its horseshoe, the block that holds it and the deck of its roof. */
        final int gateW;
        final int gateJamb;
        final int gx1;
        final int gx2;
        final int gz1;
        final int gz2;
        final int pd;
        /** The main tower: width and middle row of its square keep, the deck of the keep, the top of its foot, its floors. */
        final int mw;
        final int mz;
        final int md;
        final int mfoot;
        final int[] floors;
        /** The walls of the throne hall, its deck, the drum and the dome over it. */
        final int hx1;
        final int hx2;
        final int hz1;
        final int hz2;
        final int hd;
        final int drum;
        final int drumH;
        final int dome;
        /** The columns of its arcades: their distance from the middle and their rows (none: four free columns). */
        final int colX;
        final int[] cols;
        /** The portal before the hall: the niche in it (0 for none), its sides, front row and deck. */
        final int niche;
        final int nicheJamb;
        final int px1;
        final int px2;
        final int pz;
        final int ptop;
        /** The outer walls of the wings, the deck of their roofs and of the blocks at the back, the wall between their rooms. */
        final int wx1;
        final int wx2;
        final int wd;
        final int split;
        /** The blocks at the back: their south wall, the walls of the chambers (west) and of the kitchen (east). */
        final int bz;
        final int bwx1;
        final int bwx2;
        final int bex1;
        final int bex2;
        /** Named places: {kind, x, y, z}. */
        final List<int[]> points = new ArrayList<>();

        Plan(Size size) {
            this.size = size;
            switch (size) {
                case SMALL -> {
                    n = 31;
                    height = 40;
                    o = 1;
                    f = 8;
                    towers = new int[][]{{3, 3, 5, 10, 0}, {26, 4, 7, 13, 0}, {4, 26, 7, 12, 1}, {26, 26, 7, 12, 1}};
                    gateW = 3;
                    gateJamb = 2;
                    gx1 = 8;
                    gx2 = 22;
                    gz1 = 27;
                    gz2 = 30;
                    pd = 13;
                    mw = 7;
                    mz = 8;
                    md = 18;
                    mfoot = 4;
                    floors = new int[]{7, 13};
                    hx1 = 10;
                    hx2 = 20;
                    hz1 = 13;
                    hz2 = 21;
                    hd = 10;
                    drum = 7;
                    drumH = 4;
                    dome = 7;
                    colX = 3;
                    cols = new int[0];
                    niche = 0;
                    nicheJamb = 0;
                    px1 = 10;
                    px2 = 20;
                    pz = 22;
                    ptop = 13;
                    wx1 = 3;
                    wx2 = 27;
                    wd = 8;
                    split = 0;
                    bz = 13;
                    bwx1 = 3;
                    bwx2 = 11;
                    bex1 = 19;
                    bex2 = 27;
                }
                case MEDIUM -> {
                    n = 41;
                    height = 44;
                    o = 2;
                    f = 9;
                    towers = new int[][]{{4, 4, 7, 14, 0}, {36, 4, 7, 17, 0}, {12, 37, 5, 16, 1}, {28, 37, 5, 16, 1}};
                    gateW = 3;
                    gateJamb = 3;
                    gx1 = 15;
                    gx2 = 25;
                    gz1 = 36;
                    gz2 = 39;
                    pd = 14;
                    mw = 7;
                    mz = 12;
                    md = 22;
                    mfoot = 5;
                    floors = new int[]{8, 15};
                    hx1 = 14;
                    hx2 = 26;
                    hz1 = 17;
                    hz2 = 29;
                    hd = 11;
                    drum = 9;
                    drumH = 5;
                    dome = 9;
                    colX = 3;
                    cols = new int[]{19, 23, 27};
                    niche = 5;
                    nicheJamb = 5;
                    px1 = 15;
                    px2 = 25;
                    pz = 30;
                    ptop = 15;
                    wx1 = 8;
                    wx2 = 32;
                    wd = 7;
                    split = 23;
                    bz = 11;
                    bwx1 = 9;
                    bwx2 = 15;
                    bex1 = 25;
                    bex2 = 31;
                }
                default -> {
                    n = 47;
                    height = 48;
                    o = 2;
                    f = 10;
                    towers = new int[][]{{4, 4, 7, 16, 0}, {41, 5, 9, 20, 0}, {4, 42, 7, 13, 0}, {42, 42, 7, 13, 0},
                            {13, 42, 7, 18, 1}, {33, 42, 7, 18, 1}};
                    gateW = 5;
                    gateJamb = 3;
                    gx1 = 17;
                    gx2 = 29;
                    gz1 = 42;
                    gz2 = 45;
                    pd = 16;
                    mw = 9;
                    mz = 13;
                    md = 25;
                    mfoot = 6;
                    floors = new int[]{9, 17};
                    hx1 = 16;
                    hx2 = 30;
                    hz1 = 19;
                    hz2 = 33;
                    hd = 12;
                    drum = 11;
                    drumH = 6;
                    dome = 11;
                    colX = 4;
                    cols = new int[]{22, 26, 30};
                    niche = 7;
                    nicheJamb = 5;
                    px1 = 17;
                    px2 = 29;
                    pz = 34;
                    ptop = 17;
                    wx1 = 8;
                    wx2 = 38;
                    wd = 7;
                    split = 26;
                    bz = 11;
                    bwx1 = 9;
                    bwx2 = 17;
                    bex1 = 29;
                    bex2 = 35;
                }
            }
            c = (n - 1) / 2;
        }

        boolean small() {
            return size == Size.SMALL;
        }

        boolean large() {
            return size == Size.LARGE;
        }

        /** The walk on the west (or north) wall and on the east (or south) wall. */
        int walkLo() {
            return o + 1;
        }

        int walkHi() {
            return n - 2 - o;
        }

        void point(int kind, int x, int y, int z) {
            points.add(new int[]{kind, x, y, z});
        }
    }

    // ---------------------------------------------------------------- small helpers

    /** Sets a block where the box of the structure has room for it. */
    private static void put(Structure s, int x, int y, int z, String state) {
        Desert.put(s, x, y, z, state);
    }

    /** Fills a box of blocks, leaving out what falls outside the structure. */
    private static void box(Structure s, int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    put(s, x, y, z, state);
                }
            }
        }
    }

    /** A cell given by its place along a line ({@code a}) and across it ({@code c}). */
    private static void at(Structure s, boolean alongX, int a, int y, int c, String state) {
        put(s, alongX ? a : c, y, alongX ? c : a, state);
    }

    /** The direction across a line running along x (z) or along z (x), to the plus or the minus side. */
    private static String across(boolean alongX, int sign) {
        return alongX ? (sign > 0 ? "south" : "north") : (sign > 0 ? "east" : "west");
    }

    /** Whether a position lies in the structure and nothing stands there yet. */
    private static boolean free(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && !s.solid(x, y, z);
    }

    /** A lamp hanging on a chain from the first solid block above, only where there is a ceiling within reach. */
    private static void hang(Structure s, int x, int y, int z) {
        for (int yy = y + 1; yy < Math.min(s.sizeY, y + 12); yy++) {
            if (s.solid(x, yy, z)) {
                Desert.lamp(s, x, y, z);
                return;
            }
        }
    }

    /** A lantern standing on a post of rough stone. */
    private static void lampPost(Structure s, int x, int y, int z) {
        put(s, x, y, z, Desert.BASE_POST);
        put(s, x, y + 1, z, Kit.lantern());
    }

    /**
     * Stairs on the rim of a square of half-size {@code r} at layer {@code y}, their high side to the middle: a sloping
     * ledge, or hanging upside down a cornice.
     */
    private static void ledge(Structure s, int cx, int y, int cz, int r, String stairs, boolean upside) {
        String half = upside ? "top" : "bottom";
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Math.abs(dx) != r && Math.abs(dz) != r) {
                    continue;
                }
                String facing;
                String shape = "straight";
                if (Math.abs(dx) == r && Math.abs(dz) == r) {
                    facing = dz > 0 ? "north" : "south";
                    shape = dz > 0 == dx > 0 ? "outer_left" : "outer_right";
                } else if (Math.abs(dx) == r) {
                    facing = dx > 0 ? "west" : "east";
                } else {
                    facing = dz > 0 ? "north" : "south";
                }
                put(s, cx + dx, y, cz + dz, stairs + "[facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=false]");
            }
        }
    }

    /**
     * A flat roof over walls on x1, x2, z1 and z2 whose top course is at {@code y - 1}: the deck at {@code y} over the walls
     * and the rooms, and on the sides asked for a parapet ({@code y}, {@code y + 1}) crowned with merlons at {@code y + 2},
     * a pinnacle where two parapets meet.
     */
    private static void terrace(Structure s, int x1, int z1, int x2, int z2, int y, Desert.Merlons merlons, boolean north, boolean south,
                                boolean west, boolean east) {
        box(s, x1, y, z1, x2, y, z2, Desert.ROOF.block());
        boolean[] sides = {north, south, west, east};
        for (int i = 0; i < 4; i++) {
            if (!sides[i]) {
                continue;
            }
            boolean alongX = i < 2;
            int line = i == 0 ? z1 : i == 1 ? z2 : i == 2 ? x1 : x2;
            int a1 = alongX ? x1 : z1;
            int a2 = alongX ? x2 : z2;
            for (int a = a1; a <= a2; a++) {
                at(s, alongX, a, y, line, WALL);
                at(s, alongX, a, y + 1, line, WALL);
            }
            Desert.crown(s, alongX, a1, a2, line, y + 2, merlons);
        }
        int[][] corners = {{x1, z1, 0, 2}, {x2, z1, 0, 3}, {x1, z2, 1, 2}, {x2, z2, 1, 3}};
        for (int[] k : corners) {
            if (sides[k[2]] && sides[k[3]]) {
                put(s, k[0], y + 2, k[1], WALL);
                put(s, k[0], y + 3, k[1], Kit.slab(Desert.BASE_SLAB));
            } else if (sides[k[2]] || sides[k[3]]) {
                put(s, k[0], y + 2, k[1], Kit.slab(Desert.BASE_SLAB));
            }
        }
    }

    // ---------------------------------------------------------------- the castle

    /**
     * Draws the castle of a plan: the ground, the rooms of the palace and the hall, the main tower, the walls with their
     * towers and the gate, the courts, the stairs, the furniture and the lights.
     */
    private static Structure castle(Plan p) {
        Structure s = new Structure(p.n, p.height, p.n);
        ground(s, p);
        wings(s, p);
        backBlocks(s, p);
        hall(s, p);
        pishtaq(s, p);
        mainTower(s, p);
        curtain(s, p);
        for (int[] t : p.towers) {
            tower(s, p, t[0], t[1], t[2], t[3], t[4] == 1);
        }
        gate(s, p);
        courts(s, p);
        stairs(s, p);
        rooms(s, p);
        hallInside(s, p);
        lights(s, p);
        for (int z = 0; z < p.n; z++) {
            for (int x = 0; x < p.n; x++) {
                if (s.get(x, 0, z).equals(GRASS) && States.isFull(s.get(x, 1, z))) {
                    s.set(x, 0, z, "minecraft:dirt");
                }
            }
        }
        s.skip(p.c, 0, p.c).skip(p.c, 1, p.c);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The ground: grass round the castle, rough stone under its walls, paving inside them and a paved road from the gate to
     * the street.
     */
    private static void ground(Structure s, Plan p) {
        box(s, 0, 0, 0, p.n - 1, 0, p.n - 1, GRASS);
        box(s, p.o - 1, 0, p.o - 1, p.n - p.o, 0, p.n - p.o, BASE);
        box(s, p.o + 3, 0, p.o + 3, p.n - 4 - p.o, 0, p.n - 4 - p.o, Desert.PAVING);
        int w = p.gateW / 2 + 2;
        box(s, p.c - w, 0, p.gz2 + 1, p.c + w, 0, p.n - 1, Desert.PAVING);
        for (int z = p.gz2 + 1; z < p.n; z++) {
            put(s, p.c - w - 1, 0, z, Kit.slab(Desert.BASE_SLAB));
            put(s, p.c + w + 1, 0, z, Kit.slab(Desert.BASE_SLAB));
        }
    }

    // ---------------------------------------------------------------- the fortress

    /**
     * The four walls of the fortress: three blocks thick, on a battered plinth of rough stone, a corbel table and a band of
     * trim under the parapet, pointed merlons on the outer edge, the walk behind them and a coping of rough stone toward the
     * yard; narrow slits low under the corbels.
     */
    private static void curtain(Structure s, Plan p) {
        int lo = p.o;
        int hi = p.n - 1 - p.o;
        side(s, p, true, lo, 1);
        side(s, p, true, hi, -1);
        side(s, p, false, lo, 1);
        side(s, p, false, hi, -1);
    }

    /**
     * One wall on the line {@code line} (z when it runs along x, x when along z), its yard toward {@code in}.
     */
    private static void side(Structure s, Plan p, boolean alongX, int line, int in) {
        int lo = p.o;
        int hi = p.n - 1 - p.o;
        int f = p.f;
        String inward = across(alongX, in);
        for (int a = lo; a <= hi; a++) {
            for (int y = 1; y < f; y++) {
                for (int k = 0; k < 3; k++) {
                    at(s, alongX, a, y, line + k * in, y == 1 && k == 0 ? BASE : WALL);
                }
            }
            at(s, alongX, a, 1, line - in, BASE);
            at(s, alongX, a, 2, line - in, BASE);
            at(s, alongX, a, 3, line - in, Kit.stairs(Desert.BASE_STAIRS, inward));
            at(s, alongX, a, f - 1, line - in, Kit.upside(Desert.TRIM_STAIRS, inward));
            at(s, alongX, a, f, line, WALL);
            at(s, alongX, a, f, line + in, Desert.ROOF.block());
            at(s, alongX, a, f, line + 2 * in, WALL);
            at(s, alongX, a, f + 1, line, WALL);
            at(s, alongX, a, f + 1, line + in, AIR);
            at(s, alongX, a, f + 1, line + 2 * in, Kit.slab(Desert.BASE_SLAB));
            at(s, alongX, a, f + 2, line + in, AIR);
            at(s, alongX, a, f + 2, line + 2 * in, AIR);
            if (Math.floorMod(a - p.c, 6) == 3) {
                at(s, alongX, a, f - 4, line, AIR);
                at(s, alongX, a, f - 3, line, AIR);
            }
            // lanterns on posts along the walk
            if (Math.floorMod(a - p.c, 8) == 4) {
                at(s, alongX, a, f + 1, line + 2 * in, Desert.BASE_POST);
                at(s, alongX, a, f + 2, line + 2 * in, Kit.lantern());
            }
        }
        Desert.crown(s, alongX, lo, hi, line, f + 2, Desert.Merlons.POINTED);
        // a pinnacle on each corner (where no tower stands on it)
        for (int a : new int[]{lo, hi}) {
            at(s, alongX, a, f + 2, line, WALL);
            at(s, alongX, a, f + 3, line, Kit.slab(Desert.BASE_SLAB));
        }
    }

    /**
     * A tower of the walls (see {@link Desert#tower}): solid below the walk, a room on the walk through which the walk passes
     * by doorways in its faces, lit by a lamp under the deck; a lantern on the deck.
     */
    private static void tower(Structure s, Plan p, int cx, int cz, int w, int height, boolean gate) {
        int h = (w - 1) / 2;
        int deck = Desert.tower(s, cx, 1, cz, w, height, gate ? Desert.Merlons.STEPPED : Desert.Merlons.POINTED);
        int footTop = 1 + Math.max(2, height / 3);
        int step = 1 + height * 2 / 3;
        for (int y = 1; y < p.f; y++) {
            Desert.plate(s, cx, y, cz, y < footTop ? h : h - 1, SQUARE, WALL);
        }
        for (int y = p.f; y < deck; y++) {
            int r = (y < step ? h : h - 1) - 1;
            if (r >= 0) {
                Desert.plate(s, cx, y, cz, r, SQUARE, y == p.f ? Desert.PAVING : AIR);
            }
        }
        // the walk through the tower
        int lo = p.walkLo();
        int hi = p.walkHi();
        for (int x : new int[]{lo, hi}) {
            if (Math.abs(x - cx) <= h) {
                for (int z = Math.max(cz - h, lo); z <= Math.min(cz + h, hi); z++) {
                    passage(s, p, x, z);
                }
            }
        }
        for (int z : new int[]{lo, hi}) {
            if (Math.abs(z - cz) <= h) {
                for (int x = Math.max(cx - h, lo); x <= Math.min(cx + h, hi); x++) {
                    passage(s, p, x, z);
                }
            }
        }
        if (deck - 1 > p.f + 2) {
            hang(s, cx, deck - 1, cz);
        }
        put(s, cx, deck + 1, cz, Kit.lantern());
        if (gate) {
            Desert.shell(s, cx, deck - 2, deck - 2, cz, h - 1, SQUARE, Desert.glazed(Desert.TILE_CYAN, "north"));
        }
    }

    /** One cell of the walk through a tower: a floor of paving and room for a body over it. */
    private static void passage(Structure s, Plan p, int x, int z) {
        put(s, x, p.f, z, Desert.PAVING);
        put(s, x, p.f + 1, z, AIR);
        put(s, x, p.f + 2, z, AIR);
    }

    /**
     * The gate: a block between the two towers of the gate, pierced by a great horseshoe arch in its frame of rough stone
     * with a lattice in its head and a band of tiles over the frame; the walk crosses it in a gallery with lattice windows
     * toward the court; braziers before it; the guards in the passage.
     */
    private static void gate(Structure s, Plan p) {
        int c = p.c;
        box(s, p.gx1, 1, p.gz1, p.gx2, p.pd - 1, p.gz2, WALL);
        box(s, p.gx1, 1, p.gz2, p.gx2, 1, p.gz2, BASE);
        Desert.flatRoof(s, p.gx1, p.gz1, p.gx2, p.gz2, p.pd, Desert.Merlons.POINTED, false);
        int depth = p.gz2 - p.gz1 + 1;
        int top = Desert.arch(s, c, 1, p.gz2, "south", p.gateW, p.gateJamb, depth, Desert.Arch.HORSESHOE, null, true);
        int a = p.gateW / 2;
        for (int y = 1 + p.gateJamb; y < top; y++) {
            for (int x = c - a - 2; x <= c + a + 2; x++) {
                if (s.get(x, y, p.gz2).equals(AIR)) {
                    put(s, x, y, p.gz2, Kit.shutter(Desert.LATTICE, "north"));
                }
            }
        }
        for (int x = c - a - 4; x <= c + a + 4; x++) {
            put(s, x, top, p.gz2, Desert.glazed(Desert.TILE_CYAN, "south"));
        }
        // the gallery of the walk across the gate
        int wz = p.walkHi();
        for (int x = p.gx1; x <= p.gx2; x++) {
            put(s, x, p.f, wz, Desert.PAVING);
            box(s, x, p.f + 1, wz, x, p.f + 3, wz, AIR);
        }
        for (int x : new int[]{c - a - 3, c + a + 3}) {
            put(s, x, p.f + 1, p.gz1, Kit.shutter(Desert.LATTICE, "south"));
            put(s, x, p.f + 2, p.gz1, Kit.shutter(Desert.LATTICE, "south"));
        }
        hang(s, c - 2, p.f + 3, wz);
        hang(s, c + 2, p.f + 3, wz);
        // a lamp under the vault, the guards in the passage, braziers before the gate
        int mid = (p.gz1 + p.gz2 + 1) / 2;
        int y = 3;
        while (y + 1 < p.pd && s.get(c, y + 1, mid).equals(AIR)) {
            y++;
        }
        hang(s, c, y, mid);
        p.point(GATE, c - 1, 1, mid);
        p.point(GATE, c + 1, 1, mid);
        int bz = Math.min(p.gz2 + 1, p.n - 1);
        int bx = p.gz2 + 1 < p.n ? a + 3 : a + 5;
        Desert.brazier(s, c - bx, 1, bz);
        Desert.brazier(s, c + bx, 1, bz);
    }

    // ---------------------------------------------------------------- the main tower

    /**
     * The main tower, the one dominant of the castle: a square keep on a battered foot of rough stone, a band of trim over
     * the foot, on every face a tall panel framed in rough stone with carved corners and a keyhole window (none on the north
     * face, behind the ladder), small windows lower down, a band of glazed tiles under a corbelled parapet with stepped
     * merlons; from its deck rises a minaret (see {@link Desert#minaret}) with its balcony, lantern, dome and finial. Inside
     * floors on a ladder that runs up to the deck by the door of the minaret, a lamp on every floor; its foot opens on both
     * courts at the back (on the hall and the two rooms beside it in the small castle).
     */
    private static void mainTower(Structure s, Plan p) {
        int cx = p.c;
        int cz = p.mz;
        int h = (p.mw - 1) / 2;
        int top = p.md;
        // the foot and the shaft
        for (int y = 1; y <= p.mfoot; y++) {
            Desert.shell(s, cx, y, y, cz, h + 1, SQUARE, y <= 2 ? BASE : WALL);
        }
        ledge(s, cx, p.mfoot + 1, cz, h + 1, Desert.BASE_STAIRS, false);
        for (int y = 1; y < top; y++) {
            Desert.shell(s, cx, y, y, cz, h, SQUARE, WALL);
            Desert.plate(s, cx, y, cz, h - 1, SQUARE, AIR);
        }
        Desert.plate(s, cx, 0, cz, h - 1, SQUARE, Desert.PAVING);
        Desert.shell(s, cx, p.mfoot + 1, p.mfoot + 1, cz, h, SQUARE, TRIM);
        // on every face a tall panel in a frame of rough stone with a keyhole window (none behind the ladder), carved corners
        int ph = p.small() ? 6 : 8;
        int py = top - ph - 2;
        for (String face : new String[]{"north", "south", "east", "west"}) {
            int[] o = Furnish.step(face);
            int[] l = Furnish.step(Furnish.left(face));
            for (int row = 0; row < ph; row++) {
                for (int u = -(h - 1); u <= h - 1; u++) {
                    boolean frame = row == 0 || row == ph - 1 || Math.abs(u) == h - 1;
                    put(s, cx + o[0] * h + l[0] * u, py + row, cz + o[1] * h + l[1] * u, frame ? BASE : WALL);
                }
            }
            put(s, cx + o[0] * h + l[0] * (h - 2), py + ph - 2, cz + o[1] * h + l[1] * (h - 2), Desert.CARVED);
            put(s, cx + o[0] * h - l[0] * (h - 2), py + ph - 2, cz + o[1] * h - l[1] * (h - 2), Desert.CARVED);
            if (!face.equals("north")) {
                Desert.arch(s, cx + o[0] * h, py + 1, cz + o[1] * h, face, 1, p.small() ? 1 : 2, 1, Desert.Arch.HORSESHOE, Desert.GLASS, false);
            }
            for (int u = -h; u <= h; u++) {
                put(s, cx + o[0] * h + l[0] * u, top - 2, cz + o[1] * h + l[1] * u, Desert.glazed(Desert.TILE_CYAN, face));
            }
        }
        Desert.corbels(s, cx, top - 1, cz, h + 1, SQUARE);
        Desert.plate(s, cx, top, cz, h, SQUARE, Desert.ROOF.block());
        Desert.shell(s, cx, top, top + 1, cz, h + 1, SQUARE, WALL);
        Desert.crown(s, true, cx - h - 1, cx + h + 1, cz - h - 1, top + 2, Desert.Merlons.STEPPED);
        Desert.crown(s, true, cx - h - 1, cx + h + 1, cz + h + 1, top + 2, Desert.Merlons.STEPPED);
        Desert.crown(s, false, cz - h - 1, cz + h + 1, cx - h - 1, top + 2, Desert.Merlons.STEPPED);
        Desert.crown(s, false, cz - h - 1, cz + h + 1, cx + h + 1, top + 2, Desert.Merlons.STEPPED);
        for (int[] k : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            put(s, cx + k[0] * (h + 1), top + 2, cz + k[1] * (h + 1), WALL);
            put(s, cx + k[0] * (h + 1), top + 3, cz + k[1] * (h + 1), Kit.slab(Desert.BASE_SLAB));
        }
        // floors, the ladder, lamps and keyhole windows
        int[] levels = new int[p.floors.length + 2];
        levels[0] = 0;
        System.arraycopy(p.floors, 0, levels, 1, p.floors.length);
        levels[levels.length - 1] = top;
        for (int i = 1; i < levels.length - 1; i++) {
            Desert.plate(s, cx, levels[i], cz, h - 1, SQUARE, Desert.FLOOR);
        }
        for (int i = 0; i < levels.length - 1; i++) {
            int floor = levels[i];
            int ceiling = levels[i + 1];
            hang(s, cx + h - 2, ceiling - 1, cz + h - 2);
            if (i == 0) {
                continue;
            }
            for (String face : new String[]{"north", "south", "east", "west"}) {
                if (face.equals("south") && floor < p.hd + 2 || floor + 3 >= py) {
                    continue;
                }
                int[] o = Furnish.step(face);
                int[] l = Furnish.step(Furnish.left(face));
                int u = face.equals("north") ? -2 : 0;
                put(s, cx + o[0] * h + l[0] * u, floor + 2, cz + o[1] * h + l[1] * u, Desert.GLASS);
                put(s, cx + o[0] * h + l[0] * u, floor + 3, cz + o[1] * h + l[1] * u, Desert.GLASS);
            }
        }
        // on the deck the minaret: an eight-sided shaft with a band of tiles, the balcony, the lantern, the dome and its finial
        Desert.minaret(s, cx, top, cz, 5, p.height - 1 - top, "north");
        int lz = cz - (h - 1);
        for (int y = 1; y <= top; y++) {
            put(s, cx, y, lz, Kit.ladder("south"));
        }
        // the doors of its foot
        if (p.small()) {
            Desert.doorway(s, cx - 2, 1, p.hz1, "south", 1, 2, 3, Desert.Arch.POINTED, false);
        }
        Desert.doorway(s, cx - h - 1, 1, cz, "west", 1, 2, 2, Desert.Arch.POINTED, false);
        Desert.doorway(s, cx + h + 1, 1, cz, "east", 1, 2, 2, Desert.Arch.POINTED, false);
    }

    // ---------------------------------------------------------------- the palace

    /**
     * The shells of the wings beside the hall: their walls on a plinth of rough stone, the wall between their two rooms, and
     * a terrace over them with stepped merlons toward the courts and lanes; small domes on drums over the council room and
     * the treasury of the great castle.
     */
    private static void wings(Structure s, Plan p) {
        for (int side = -1; side <= 1; side += 2) {
            int x1 = side < 0 ? p.wx1 : p.hx2;
            int x2 = side < 0 ? p.hx1 : p.wx2;
            box(s, x1 + 1, 1, p.hz1 + 1, x2 - 1, p.wd - 1, p.hz2 - 1, AIR);
            for (int y = 1; y < p.wd; y++) {
                s.walls(x1, y, p.hz1, x2, y, p.hz2, y == 1 ? BASE : WALL);
            }
            box(s, x1 + 1, 0, p.hz1 + 1, x2 - 1, 0, p.hz2 - 1, Desert.PAVING);
            if (p.split > 0) {
                box(s, x1, 1, p.split, x2, p.wd - 1, p.split, WALL);
            }
            if (p.small()) {
                terrace(s, x1, p.hz1, x2, p.hz2, p.wd, Desert.Merlons.STEPPED, false, true, false, false);
            } else {
                terrace(s, x1, p.hz1, x2, p.hz2, p.wd, Desert.Merlons.STEPPED, true, true, side < 0, side > 0);
            }
            if (p.large()) {
                int cx = (x1 + x2) / 2;
                int cz = (p.hz1 + p.split) / 2 + 1;
                int top = Desert.drum(s, cx, p.wd + 1, cz, 5, 2, null);
                Desert.dome(s, cx, top, cz, 5, Desert.DomeKind.POINTED);
                Desert.plate(s, cx, p.wd, cz, 1, SQUARE, AIR);
            }
        }
    }

    /**
     * The blocks at the back, against the back wall on both sides of the main tower: the royal chambers (west) under a small
     * dome and the kitchen (east) under a wind tower, their terraces with stepped merlons toward their courts.
     */
    private static void backBlocks(Structure s, Plan p) {
        int z1 = p.o + 2;
        int z2 = p.bz;
        for (int side = -1; side <= 1; side += 2) {
            int x1 = side < 0 ? p.bwx1 : p.bex1;
            int x2 = side < 0 ? p.bwx2 : p.bex2;
            box(s, x1 + 1, 1, z1 + 1, x2 - 1, p.wd - 1, z2 - 1, AIR);
            for (int y = 1; y < p.wd; y++) {
                s.walls(x1, y, z1, x2, y, z2, y == 1 ? BASE : WALL);
            }
            box(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, Desert.PAVING);
            if (p.small()) {
                terrace(s, x1, z1, x2, z2, p.wd, Desert.Merlons.STEPPED, false, false, false, false);
            } else {
                terrace(s, x1, z1, x2, z2, p.wd, Desert.Merlons.STEPPED, false, true, side < 0, false);
            }
            int cx = (x1 + x2) / 2;
            int cz = (z1 + z2) / 2 + 1;
            if (side < 0) {
                int top = Desert.drum(s, cx, p.wd + 1, cz, 5, 2, null);
                Desert.dome(s, cx, top, cz, 5, Desert.DomeKind.SMOOTH);
                Desert.plate(s, cx, p.wd, cz, 1, SQUARE, AIR);
            } else {
                int wx = p.small() ? x1 + 2 : cx;
                int wz = p.small() ? z2 - 2 : cz;
                Desert.windTower(s, wx, p.wd, wz, 3, 6);
                put(s, wx, p.wd + 1, wz, Kit.lantern());
            }
        }
        // behind the main tower the space between the blocks is built up to their terraces
        if (!p.small()) {
            int h = (p.mw - 1) / 2;
            box(s, p.bwx2 + 1, 1, z1 + 1, p.bex1 - 1, p.wd - 1, p.mz - h - 2, WALL);
            box(s, p.bwx2 + 1, p.wd, z1 + 1, p.bex1 - 1, p.wd, p.mz - h - 2, Desert.ROOF.block());
            put(s, p.c + h, p.wd + 1, z1 + 1, Kit.lantern());
        }
    }

    /**
     * The throne hall: walls of the wall stone on a plinth of rough stone, a floor of glazed tiles, a flat roof with a
     * cornice and pointed merlons, the drum with its windows and the ribbed dome over the middle (the deck open under it),
     * the arcades or the free columns inside, lattice windows high in its sides over the wings.
     */
    private static void hall(Structure s, Plan p) {
        int x1 = p.hx1;
        int x2 = p.hx2;
        int z1 = p.hz1;
        int z2 = p.hz2;
        int hd = p.hd;
        int c = p.c;
        int mz = (z1 + z2) / 2;
        box(s, x1 + 1, 1, z1 + 1, x2 - 1, hd - 1, z2 - 1, AIR);
        s.walls(x1, 1, z1, x2, hd - 1, z2, WALL);
        s.walls(x1, 1, z1, x2, 1, z2, BASE);
        Desert.zellige(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, 0);
        Desert.flatRoof(s, x1, z1, x2, z2, hd, Desert.Merlons.POINTED, true);
        int top = Desert.drum(s, c, hd + 1, mz, p.drum, p.drumH, Desert.GLASS);
        int r = (p.drum - 1) / 2 - 1;
        Desert.plate(s, c, hd, mz, r, Desert.ROUND, AIR);
        Desert.dome(s, c, top, mz, p.dome, p.small() ? Desert.DomeKind.POINTED : Desert.DomeKind.RIBBED);
        if (!p.small()) {
            for (int z = mz - 2; z <= mz + 2; z += 4) {
                Desert.lattice(s, x1, p.wd + 2, z, "west", 1, 2, false);
                Desert.lattice(s, x2, p.wd + 2, z, "east", 1, 2, false);
            }
        }
        // the arcades of the nave: pointed arches on slender columns, a frieze of tiles over them
        if (p.cols.length >= 2) {
            int jamb = p.large() ? 4 : 3;
            int a1 = p.cols[0];
            int a2 = p.cols[p.cols.length - 1];
            for (int side = -1; side <= 1; side += 2) {
                int x = c + side * p.colX;
                String facing = side < 0 ? "east" : "west";
                box(s, x, 1, a1, x, hd - 1, a2, WALL);
                for (int i = 0; i + 1 < p.cols.length; i++) {
                    Desert.arch(s, x, 1, (p.cols[i] + p.cols[i + 1]) / 2, facing, 3, jamb, 1, Desert.Arch.POINTED, null, false);
                }
                for (int z : p.cols) {
                    put(s, x, 1, z, BASE);
                    for (int y = 2; y < jamb; y++) {
                        put(s, x, y, z, Desert.BASE_POST);
                    }
                    put(s, x, jamb, z, Desert.CARVED);
                }
                for (int z = a1; z <= a2; z++) {
                    put(s, x, hd - 2, z, Desert.glazed(Desert.TILE_CYAN, facing));
                }
            }
        } else {
            for (int dz : new int[]{0, 4}) {
                for (int side = -1; side <= 1; side += 2) {
                    int x = c + side * p.colX;
                    int z = c + dz;
                    put(s, x, 1, z, BASE);
                    for (int y = 2; y < hd - 2; y++) {
                        put(s, x, y, z, Desert.BASE_POST);
                    }
                    put(s, x, hd - 2, z, Desert.CARVED);
                    ledge(s, x, hd - 1, z, 1, Desert.TRIM_STAIRS, true);
                    put(s, x, hd - 1, z, WALL);
                }
            }
        }
    }

    /**
     * The portal of the hall (pishtaq): its front raised over the roofs with pointed merlons, a pointed niche in a frame of
     * rough stone and the horseshoe doorway at its back with a lattice window over it (in the small castle the doorway in its
     * frame stands in the face itself), a band of tiles over the frame, a lamp in the niche.
     */
    private static void pishtaq(Structure s, Plan p) {
        int c = p.c;
        box(s, p.px1, 1, p.hz2, p.px2, p.ptop - 1, p.pz, WALL);
        box(s, p.px1, 1, p.pz, p.px2, 1, p.pz, BASE);
        Desert.flatRoof(s, p.px1, p.hz2, p.px2, p.pz, p.ptop, Desert.Merlons.POINTED, false);
        int top;
        if (p.niche > 0) {
            top = Desert.arch(s, c, 1, p.pz, "south", p.niche, p.nicheJamb, p.pz - p.hz2, Desert.Arch.POINTED, null, true);
            int door = Desert.doorway(s, c, 1, p.hz2, "south", 3, 2, 1, Desert.Arch.HORSESHOE, false);
            Desert.lattice(s, c, door + 1, p.hz2, "south", 1, 2, false);
            hang(s, c, door + 1 > 0 ? door - 1 : 3, p.pz);
        } else {
            top = Desert.doorway(s, c, 1, p.pz, "south", 3, 2, p.pz - p.hz2 + 1, Desert.Arch.HORSESHOE, true);
        }
        for (int x = p.px1 + 1; x < p.px2; x++) {
            if (top < p.ptop - 1) {
                put(s, x, top, p.pz, Desert.glazed(Desert.TILE_CYAN, "south"));
            }
        }
    }

    // ---------------------------------------------------------------- courts

    /**
     * The courts: before the hall the court of the pools (long basins of water lined with tiles between palms and lamps);
     * behind, the garden of the chambers with its star fountain and palms and the court of the kitchen.
     */
    private static void courts(Structure s, Plan p) {
        int c = p.c;
        int zf = p.pz + 1;
        if (p.large()) {
            pool(s, 7, zf + 1, 16, zf + 3);
            pool(s, 30, zf + 1, 39, zf + 3);
            palmOn(s, 6, zf + 2, 8, null);
            palmOn(s, 40, zf + 2, 8, null);
            for (int x : new int[]{20, 26}) {
                lampPost(s, x, 1, zf);
                lampPost(s, x, 1, zf + 5);
            }
        } else if (p.size == Size.MEDIUM) {
            pool(s, 6, zf, 14, zf + 2);
            pool(s, 26, zf, 34, zf + 2);
            palmOn(s, 16, zf + 2, 7, "west");
            palmOn(s, 24, zf + 2, 7, "east");
            lampPost(s, 17, 1, zf);
            lampPost(s, 23, 1, zf);
            lampPost(s, 6, 1, zf + 4);
            lampPost(s, 34, 1, zf + 4);
        } else {
            pool(s, 10, 24, 13, 26);
            pool(s, 17, 24, 20, 26);
            lampPost(s, 9, 1, 23);
            lampPost(s, 21, 1, 23);
            palmOn(s, 9, 26, 6, null);
            palmOn(s, 21, 26, 6, null);
        }
        if (p.small()) {
            return;
        }
        // the garden of the chambers and the court of the kitchen
        int z1 = p.bz + 1;
        int z2 = p.hz1 - 1;
        int mx1 = p.c - (p.mw - 1) / 2 - 2;
        int mx2 = p.c + (p.mw - 1) / 2 + 2;
        if (p.large()) {
            Desert.courtyard(s, p.wx1, z1, mx1 - 1, z2, 0, false);
            for (int[] k : new int[][]{{p.wx1 + 1, z1 + 1, 6}, {mx1 - 2, z1 + 1, 7}, {p.wx1 + 1, z2 - 1, 7}, {mx1 - 2, z2 - 1, 6}}) {
                palmOn(s, k[0], k[1], k[2], null);
            }
        } else {
            box(s, p.wx1, 0, z1, mx1 - 1, 0, z2, Desert.PAVING);
            int fx = (p.wx1 + mx1 - 1) / 2;
            int fz = (z1 + z2) / 2;
            Desert.starFountain(s, fx, 0, fz, 1);
            palmOn(s, p.wx1, z2, 7, null);
            palmOn(s, mx1 - 1, z1, 6, null);
            lampPost(s, p.wx1, 1, z1);
        }
        // the court of the kitchen: a well basin, a palm, stores in the open
        int kx1 = mx2 + 1;
        int kx2 = p.wx2;
        box(s, kx1, 0, z1, kx2, 0, z2, Desert.PAVING);
        int fx = (kx1 + kx2) / 2;
        int fz = (z1 + z2) / 2;
        Desert.starFountain(s, fx, 0, fz, 1);
        palmOn(s, kx2, z2, p.large() ? 8 : 6, null);
        lampPost(s, kx2, 1, z1);
        Furnish.crate(s, kx1 + 1, 1, z2, "oak");
        Furnish.crate(s, kx1 + 1, 2, z2, "oak");
        Furnish.sack(s, kx1 + 2, 1, z2, true);
        Desert.jar(s, kx1 + 1, 1, z2 - 1);
    }

    /** A palm on a patch of grass in the paving. */
    private static void palmOn(Structure s, int x, int z, int height, String lean) {
        put(s, x, 0, z, GRASS);
        palm(s, x, 1, z, height, lean);
    }

    /** Sets a block only where nothing stands yet, so that a palm never cuts into a wall. */
    private static void grow(Structure s, int x, int y, int z, String state) {
        if (free(s, x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    /**
     * A date palm as {@link Desert#palm} (a trunk that steps once toward {@code lean}, a crown of eight fronds and two
     * clusters of dates), but growing only into free cells: where a wall or a roof stands in the way of a frond, the frond
     * stays behind it.
     */
    private static void palm(Structure s, int x, int y, int z, int height, String lean) {
        int tx = x;
        int tz = z;
        int bend = lean != null && height >= 5 ? height / 2 : -1;
        for (int k = 0; k < height; k++) {
            if (k == bend) {
                int[] d = Furnish.step(lean);
                String axis = d[0] != 0 ? "x" : "z";
                grow(s, tx, y + k, tz, Kit.log(Desert.PALM, axis));
                tx += d[0];
                tz += d[1];
                grow(s, tx, y + k, tz, Kit.log(Desert.PALM, axis));
                continue;
            }
            grow(s, tx, y + k, tz, Kit.log(Desert.PALM, "y"));
        }
        int ty = y + height;
        boolean big = height >= 8;
        grow(s, tx, ty, tz, Desert.FROND);
        grow(s, tx, ty + 1, tz, Desert.FROND);
        int reach = big ? 4 : 3;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int i = 1; i < reach; i++) {
                grow(s, tx + d[0] * i, ty, tz + d[1] * i, Desert.FROND);
            }
            grow(s, tx + d[0] * reach, ty - 1, tz + d[1] * reach, Desert.FROND);
            grow(s, tx + d[0] * reach, ty - 2, tz + d[1] * reach, Desert.FROND);
        }
        for (int[] d : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            grow(s, tx + d[0], ty, tz + d[1], Desert.FROND);
            grow(s, tx + d[0] * 2, ty - 1, tz + d[1] * 2, Desert.FROND);
            if (big) {
                grow(s, tx + d[0] * 3, ty - 2, tz + d[1] * 3, Desert.FROND);
            }
        }
        grow(s, tx + 1, ty - 1, tz, "minecraft:cocoa[age=2,facing=west]");
        grow(s, tx - 1, ty - 1, tz, "minecraft:cocoa[age=2,facing=east]");
    }

    /**
     * A pool from (x1, z1) to (x2, z2): a floor of glazed tiles, a rim of slabs of rough stone, water inside it and a
     * border of cyan tiles in the paving round it.
     */
    private static void pool(Structure s, int x1, int z1, int x2, int z2) {
        String[] turn = {"north", "east", "west", "south"};
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                boolean border = x < x1 || x > x2 || z < z1 || z > z2;
                boolean rim = !border && (x == x1 || x == x2 || z == z1 || z == z2);
                if (border) {
                    if (s.get(x, 0, z).equals(Desert.PAVING)) {
                        put(s, x, 0, z, Desert.glazed(Desert.TILE_CYAN, turn[Math.floorMod(x + z, 4)]));
                    }
                    continue;
                }
                put(s, x, 0, z, Desert.glazed(rim ? Desert.TILE_BLUE : Desert.TILE, turn[Math.floorMod(x, 2) + 2 * Math.floorMod(z, 2)]));
                put(s, x, 1, z, rim ? Kit.slab(Desert.BASE_SLAB) : Desert.WATER);
            }
        }
    }

    /**
     * The ways up to the walk: in the great castle and the castle stairs along the walls in the lanes beside the wings; in
     * the small castle a ladder in the council room up to the terraces of the wings, which meet the walk.
     */
    private static void stairs(Structure s, Plan p) {
        if (p.small()) {
            for (int y = 1; y <= p.wd; y++) {
                put(s, p.wx1 + 1, y, p.hz2 - 1, Kit.ladder("east"));
            }
            return;
        }
        int zs = p.hz2;
        for (int x : new int[]{p.o + 3, p.n - 4 - p.o}) {
            for (int k = 1; k <= p.f; k++) {
                int z = zs - k + 1;
                for (int y = 1; y < k; y++) {
                    put(s, x, y, z, WALL);
                }
                put(s, x, k, z, Kit.stairs(Desert.WALL_STAIRS, "north"));
                put(s, x, k + 1, z, AIR);
                put(s, x, k + 2, z, AIR);
            }
        }
    }

    // ---------------------------------------------------------------- the rooms

    /**
     * The rooms of the palace and their furniture: the council room, the loggia, the treasury, the armoury, the chambers,
     * the kitchen, the library and the floors of the main tower; doors and windows.
     */
    private static void rooms(Structure s, Plan p) {
        int c = p.c;
        int y = 1;
        if (p.small()) {
            // the council room in the west wing, the treasury in the east one, both opening on the hall
            Desert.doorway(s, p.hx1, 1, c + 1, "east", 1, 2, 1, Desert.Arch.POINTED, false);
            council(s, p.wx1 + 1, p.hz1 + 1, p.hx1 - 1, p.hz2 - 2, y, false);
            treasuryDoor(s, p, p.hx2, c + 1);
            treasury(s, p.hx2 + 1, p.hz1 + 1, p.wx2 - 1, p.hz2 - 1, y);
            // the chambers and the kitchen at the back, the armoury in the foot of the main tower
            chambers(s, p, p.bwx1 + 1, p.o + 3, p.bwx2 - 1, p.bz - 1, y);
            kitchen(s, p.bex1 + 1, p.bz - 4, p.bex2 - 1, p.bz - 1, y);
            armoury(s, c - 2, p.mz - 2, c + 2, p.mz + 2, y, true);
            lattice(s, p.bwx1 + 4, 3, p.bz, "south", false);
        } else {
            int wn = p.split - 1;
            // the west wing: the council room on the hall, the loggia on the court
            Desert.doorway(s, p.hx1, 1, c - 1, "east", 1, 2, 1, Desert.Arch.POINTED, false);
            council(s, p.wx1 + 1, p.hz1 + 1, p.hx1 - 1, wn, y, true);
            Desert.arcade(s, p.wx1, p.hz2, p.hx1, p.hz2, 1, 3, p.wd - 1, "south", 3, Desert.Arch.POINTED);
            majlis(s, p.wx1 + 1, p.split + 1, p.hx1 - 1, p.hz2 - 1, y);
            // the east wing: the treasury on the hall, the armoury on the court
            treasuryDoor(s, p, p.hx2, c - 1);
            treasury(s, p.hx2 + 1, p.hz1 + 1, p.wx2 - 1, wn, y);
            int ax = (p.hx2 + p.wx2) / 2;
            Desert.doorway(s, ax, 1, p.hz2, "south", 1, 2, 1, Desert.Arch.POINTED, false);
            armoury(s, p.hx2 + 1, p.split + 1, p.wx2 - 1, p.hz2 - 1, y, false);
            for (int z : new int[]{(p.hz1 + wn) / 2 + 1, (p.split + p.hz2) / 2}) {
                Desert.lattice(s, p.wx1, 3, z, "west", 1, 2, false);
                Desert.lattice(s, p.wx2, 3, z, "east", 1, 2, false);
            }
            // the chambers and the kitchen at the back with their doors on the courts, the library in the foot of the tower
            int cx = (p.bwx1 + p.bwx2) / 2;
            Desert.doorway(s, cx, 1, p.bz, "south", 1, 2, 1, Desert.Arch.POINTED, false);
            chambers(s, p, p.bwx1 + 1, p.o + 3, p.bwx2 - 1, p.bz - 1, y);
            lattice(s, cx - 2, 3, p.bz, "south", false);
            int kx = (p.bex1 + p.bex2) / 2;
            Desert.doorway(s, kx, 1, p.bz, "south", 1, 2, 1, Desert.Arch.POINTED, false);
            kitchen(s, p.bex1 + 1, p.o + 3, p.bex2 - 1, p.bz - 1, y);
            library(s, p, c - (p.mw - 3) / 2, p.mz - (p.mw - 3) / 2, c + (p.mw - 3) / 2, p.mz + (p.mw - 3) / 2, y);
        }
        towerFloors(s, p);
    }

    /** A flat lattice window with its sill and hood, cut through a wall one block thick. */
    private static void lattice(Structure s, int x, int y, int z, String facing, boolean wide) {
        Desert.lattice(s, x, y, z, facing, wide ? 3 : 1, 2, false);
    }

    /** The iron door of the treasury in the side wall of the hall, in a pointed arch; the guard's place before it. */
    private static void treasuryDoor(Structure s, Plan p, int x, int z) {
        Desert.arch(s, x, 1, z, "west", 1, 2, 1, Desert.Arch.POINTED, null, false);
        s.door(x, 1, z, IRON_DOOR, "east", "left");
        p.point(TREASURY, x - 1, 1, z);
    }

    /**
     * The council room: a rug, a long low table of brass in the middle with seats along both sides and the seat of the king
     * at its head under the cloth of the kingdom, lamps over the table.
     */
    private static void council(Structure s, int x1, int z1, int x2, int z2, int y, boolean doorEast) {
        int mx = (x1 + x2) / 2;
        Desert.rug(s, mx - 2, z1 + 1, mx + 2, z2, y);
        for (int z = z1 + 2; z <= z2 - 1; z++) {
            put(s, mx, y, z, Kit.topSlab(Desert.BRASS_SLAB));
            put(s, mx - 1, y, z, Kit.stairs(Desert.FLOOR_STAIRS, "west"));
            put(s, mx + 1, y, z, Kit.stairs(Desert.FLOOR_STAIRS, "east"));
        }
        put(s, mx, y + 1, z1 + 2, Furnish.candles(3));
        put(s, mx, y + 1, z2 - 1, Furnish.candles(3));
        put(s, mx, y, z1 + 1, Kit.stairs(Desert.TRIM_STAIRS, "north"));
        put(s, mx, y + 2, z1, Kit.wallBanner("red", "south"));
        put(s, mx - 1, y + 3, z1, Kit.wallBanner("red", "south"));
        put(s, mx + 1, y + 3, z1, Kit.wallBanner("red", "south"));
        hang(s, mx, y + 3, z1 + 2);
        hang(s, mx, y + 3, z2 - 1);
        Desert.jar(s, x1, y, z1);
        Desert.jar(s, x2, y, z1);
    }

    /**
     * The loggia (majlis) open on the court behind its arcade: a deep divan along the back wall, a rug, tray tables, a
     * cluster of lamps and jars in the corners.
     */
    private static void majlis(Structure s, int x1, int z1, int x2, int z2, int y) {
        Desert.divan(s, x1, z1, x2, z1, y, "south", true);
        int mx = (x1 + x2) / 2;
        Desert.rug(s, x1 + 1, z1 + 3, x2 - 1, z2 - 1, y);
        Desert.trayTable(s, mx - 1, y, z1 + 3);
        Desert.trayTable(s, mx + 1, y, z1 + 3);
        Desert.lamps(s, mx, y + 2, (z1 + z2) / 2 + 1);
        Desert.jar(s, x1, y, z2);
        Desert.jar(s, x2, y, z2);
    }

    /**
     * The treasury: iron bars with an iron door across its far side, the chests and the gold of the kingdom behind them, a
     * counting table with candles before them, lamps.
     */
    private static void treasury(Structure s, int x1, int z1, int x2, int z2, int y) {
        int bars = x2 - 2;
        box(s, bars, y, z1, bars, y + 3, z2, Kit.BARS);
        s.door(bars, y, (z1 + z2) / 2, IRON_DOOR, "east", "left");
        Furnish.chest(s, x2, y, z1, "west");
        Furnish.chest(s, x2, y, z2, "west");
        for (int z = z1 + 1; z < z2; z++) {
            put(s, x2, y, z, z % 2 == 0 ? Desert.GILT : "minecraft:raw_gold_block");
        }
        put(s, x2, y + 1, z1 + 1, Desert.GILT);
        put(s, x2 - 1, y, z1, "minecraft:gold_block");
        Desert.jar(s, x2 - 1, y, z2);
        int tx = x1 + 1;
        put(s, tx, y, z2, Kit.topSlab(Desert.FLOOR_SLAB));
        put(s, tx + 1, y, z2, Kit.topSlab(Desert.FLOOR_SLAB));
        put(s, tx, y + 1, z2, Furnish.candles(3));
        put(s, tx + 1, y + 1, z2, "minecraft:gold_block");
        put(s, tx, y, z2 - 1, Kit.stairs(Desert.FLOOR_STAIRS, "south"));
        hang(s, x1 + 1, y + 3, (z1 + z2) / 2);
        hang(s, x2 - 1, y + 3, (z1 + z2) / 2);
    }

    /**
     * The armoury: racks of weapons (grindstones) along a wall, spears in a rack of posts, the anvil, the smithing table and
     * the bench of the fletcher, crates of arrows and hay for the targets, lamps.
     */
    private static void armoury(Structure s, int x1, int z1, int x2, int z2, int y, boolean tower) {
        if (tower) {
            // the foot of the tower: doors in the middle of three sides, the ladder on the fourth
            put(s, x1, y, z1, "minecraft:fletching_table");
            put(s, x1 + 1, y, z1, "minecraft:smithing_table");
            put(s, x2 - 1, y, z1, "minecraft:anvil[facing=north]");
            Furnish.crate(s, x2, y, z1, "spruce");
            Furnish.crate(s, x2, y + 1, z1, "spruce");
            Furnish.weaponRack(s, x2, y, z2, "west", "spruce");
            Furnish.weaponRack(s, x2 - 1, y, z2, "north", "spruce");
            put(s, x1 + 1, y, z2, "minecraft:hay_block[axis=y]");
            hang(s, (x1 + x2) / 2 + 1, y + 3, (z1 + z2) / 2 + 1);
            return;
        }
        for (int z = z1 + 1; z < z2; z += 2) {
            Furnish.weaponRack(s, x2, y, z, "west", "spruce");
        }
        put(s, x1, y, z2, "minecraft:anvil[facing=north]");
        put(s, x1, y, z1 + (tower ? 1 : 0), "minecraft:smithing_table");
        put(s, tower ? x1 + 2 : x1 + 1, y, z2, "minecraft:fletching_table");
        Furnish.crate(s, x2, y, z2, "spruce");
        Furnish.crate(s, x2, y + 1, z2, "spruce");
        if (!tower) {
            box(s, x1, y, z1 + 2, x1, y + 1, z1 + 2, Desert.FENCE);
            put(s, x1, y, z1 + 3, "minecraft:hay_block[axis=y]");
            put(s, x1 + 1, y, z1, "minecraft:target[power=0]");
            Furnish.crate(s, x2, y, z1, "spruce");
        }
        hang(s, (x1 + x2) / 2, y + 3, (z1 + z2) / 2);
    }

    /**
     * The royal chambers: a bed of state under its canopy (no bed of the game: this is no home), the one chest, a rug, a
     * divan, a wall fountain, niches with candles in the back wall, lamps.
     */
    private static void chambers(Structure s, Plan p, int x1, int z1, int x2, int z2, int y) {
        int mx = (x1 + x2) / 2;
        Furnish.canopyBed(s, x1 + 1, y, z2 - 2, "north", "spruce");
        Furnish.chest(s, x1, y, z2, "east");
        Desert.rug(s, mx, z1 + 1, x2, z2 - 1, y);
        if (p.small()) {
            // the door of the small castle's chambers is in their east wall: the divan stands along the south wall
            Desert.divan(s, mx + 1, z2, x2 - 1, z2, y, "north", false);
        } else {
            Desert.divan(s, x2, z1 + 1, x2, z2 - 1, y, "west", false);
            Desert.niche(s, mx - 2, y + 1, z1 - 1, "south", Furnish.candles(3));
            Desert.niche(s, x2 - 1, y + 1, z1 - 1, "south", "minecraft:decorated_pot[facing=south,waterlogged=false]");
        }
        Desert.trayTable(s, mx + 1, y, (z1 + z2) / 2);
        Desert.wallFountain(s, mx, y, z1, "south");
        hang(s, mx, y + 3, z2 - 1);
        hang(s, x2 - 1, y + 4, z1 + 2);
    }

    /**
     * The kitchen: the cooking corner (cauldron, fire, board), a smoker and a furnace, a work table, the one barrel, jars
     * and sacks of the stores, lamps.
     */
    private static void kitchen(Structure s, int x1, int z1, int x2, int z2, int y) {
        Furnish.kitchen(s, x1, y, z1, "south", "oak");
        put(s, x2, y, z1, "minecraft:smoker[facing=south,lit=false]");
        put(s, x2 - 1, y, z1, "minecraft:furnace[facing=south,lit=false]");
        int mz = (z1 + z2) / 2;
        put(s, x1 + 1, y, mz + 1, Kit.topSlab(Desert.FLOOR_SLAB));
        put(s, x1 + 2, y, mz + 1, Kit.topSlab(Desert.FLOOR_SLAB));
        put(s, x1 + 1, y + 1, mz + 1, "minecraft:cake[bites=0]");
        put(s, x2, y, z2, Kit.barrel());
        Desert.jar(s, x2, y, z2 - 1);
        Desert.jar(s, x2 - 1, y, z2);
        Furnish.sack(s, x1, y, z2, true);
        Furnish.sack(s, x1, y, z2 - 1, false);
        hang(s, x1 + 2, y + 3, mz);
    }

    /**
     * The library in the foot of the main tower: shelves of books along the walls, a desk with a candle, a reading stand, a
     * rug; the ladder of the tower climbs its north wall.
     */
    private static void library(Structure s, Plan p, int x1, int z1, int x2, int z2, int y) {
        Furnish.books(s, x1, z2, x1, z2, y, y + 2);
        Furnish.books(s, x2, z2, x2, z2, y, y + 2);
        Furnish.books(s, x1, z1, x1, z1 + 1, y, y + 2);
        Furnish.books(s, x2, z1, x2, z1 + 1, y, y + 2);
        Desert.rug(s, x1 + 1, z1 + 2, x2 - 1, z2 - 1, y);
        Furnish.desk(s, x1 + 1, y, z2, "north", "oak");
        put(s, x2 - 1, y, z2, "minecraft:lectern[facing=north,has_book=false,powered=false]");
    }

    /**
     * The upper floors of the main tower: a guard room with a divan, a store of jars and sacks, the lookout under the deck
     * with a rug, a divan and a tray table.
     */
    private static void towerFloors(Structure s, Plan p) {
        int c = p.c;
        int h = (p.mw - 1) / 2;
        int x1 = c - h + 1;
        int x2 = c + h - 1;
        int z1 = p.mz - h + 1;
        int z2 = p.mz + h - 1;
        for (int i = 0; i < p.floors.length; i++) {
            int y = p.floors[i] + 1;
            if (i == p.floors.length - 1) {
                Desert.rug(s, x1 + 1, z1 + 1, x2 - 1, z2, y);
                Desert.divan(s, x1, z2, x2, z2, y, "north", false);
                Desert.trayTable(s, c, y, p.mz);
            } else if (i % 2 == 0) {
                Desert.divan(s, x1, z1 + 1, x1, z2 - 1, y, "east", false);
                Desert.jar(s, x2, y, z2);
                Furnish.weaponRack(s, x2, y, z1 + 1, "west", "spruce");
            } else {
                Desert.jar(s, x1, y, z2);
                Desert.jar(s, x2, y, z2);
                Furnish.sack(s, x1, y, z2 - 1, true);
                Furnish.crate(s, x2, y, z2 - 1, "oak");
                Furnish.crate(s, x2, y, z1 + 1, "oak");
            }
        }
    }

    /**
     * The inside of the throne hall: the throne's place on a dais under a canopy with a gilt top, a panel of tiles and the
     * cloth of the kingdom on the wall behind it, wall fountains, a runner from the door to the dais and a rug under the
     * dome, divans along the aisles, lamps and the great lamp hanging from the dome; the places of the guards.
     */
    private static void hallInside(Structure s, Plan p) {
        int c = p.c;
        int x1 = p.hx1;
        int x2 = p.hx2;
        int z1 = p.hz1;
        int z2 = p.hz2;
        int hd = p.hd;
        int mz = (z1 + z2) / 2;
        // the dais and the canopy
        put(s, c - 1, 1, c, Kit.stairs(Desert.TRIM_STAIRS, "east"));
        put(s, c + 1, 1, c, Kit.stairs(Desert.TRIM_STAIRS, "west"));
        put(s, c - 1, 1, c + 1, Kit.stairs(Desert.TRIM_STAIRS, "north"));
        put(s, c + 1, 1, c + 1, Kit.stairs(Desert.TRIM_STAIRS, "north"));
        box(s, c - 1, 1, c - 1, c + 1, 1, c - 1, Desert.CARVED);
        for (int x : new int[]{c - 1, c + 1}) {
            box(s, x, 2, c - 1, x, 4, c - 1, Desert.BASE_POST);
        }
        if (p.small()) {
            put(s, c - 1, 5, c - 1, Desert.GILT);
            put(s, c + 1, 5, c - 1, Desert.GILT);
            put(s, c, 2, c - 1, Desert.GILT);
        } else {
            box(s, c - 1, 5, c - 1, c + 1, 5, c, Kit.topSlab(Desert.TRIM_SLAB));
            put(s, c - 1, 5, c + 1, Kit.upside(Desert.TRIM_STAIRS, "north"));
            put(s, c, 5, c + 1, Kit.upside(Desert.TRIM_STAIRS, "north"));
            put(s, c + 1, 5, c + 1, Kit.upside(Desert.TRIM_STAIRS, "north"));
            put(s, c, 6, c - 1, Desert.GILT);
            put(s, c, 6, c, Kit.slab(Desert.BRASS_SLAB));
        }
        box(s, c - 2, 0, c - 1, c + 2, 0, c + 1, Desert.glazed(Desert.TILE_BLUE, "south"));
        // the wall behind: a panel of tiles framed in rough stone, the cloth of the kingdom
        int pw = p.small() ? 0 : 2;
        int ptop = Math.min(hd - 3, 8);
        for (int y = 2; y <= ptop; y++) {
            for (int x = c - pw - 1; x <= c + pw + 1; x++) {
                boolean frame = Math.abs(x - c) == pw + 1 || y == 2 || y == ptop;
                put(s, x, y, z1, frame ? BASE : Desert.glazed(Math.floorMod(x + y, 2) == 0 ? Desert.TILE : Desert.TILE_BLUE,
                        Math.floorMod(x - y, 4) < 2 ? "north" : "east"));
            }
        }
        put(s, c, ptop + 1, z1, Desert.CARVED);
        if (!p.small()) {
            put(s, c - pw - 2, 5, z1 + 1, Kit.wallBanner("red", "south"));
            put(s, c + pw + 2, 5, z1 + 1, Kit.wallBanner("red", "south"));
        }
        Desert.wallFountain(s, c - p.colX, 1, z1 + 1, "south");
        Desert.wallFountain(s, c + p.colX, 1, z1 + 1, "south");
        // the runner and the rug under the dome
        if (p.small()) {
            box(s, c, 1, c + 2, c, 1, z2 - 1, Kit.CLOTH_CARPET);
            Desert.trayTable(s, x1 + 1, 1, z2 - 1);
            Desert.trayTable(s, x2 - 1, 1, z2 - 1);
            Desert.jar(s, x1 + 1, 1, z1 + 1);
            Desert.jar(s, x2 - 1, 1, z1 + 1);
        } else {
            int rz1 = c + 4;
            Desert.rug(s, c - 2, rz1, c + 2, Math.min(rz1 + 4, z2 - 2), 1);
            box(s, c, 1, c + 2, c, 1, rz1 - 1, Kit.CLOTH_CARPET);
            box(s, c, 1, Math.min(rz1 + 5, z2 - 1), c, 1, z2 - 1, Kit.CLOTH_CARPET);
            // divans along the aisles, tray tables, jars
            int dz1 = c + 1;
            int dz2 = z2 - 2;
            Desert.divan(s, x1 + 1, dz1, x1 + 1, dz2, 1, "east", false);
            Desert.divan(s, x2 - 1, dz1, x2 - 1, dz2, 1, "west", false);
            Desert.jar(s, x1 + 1, 1, z2 - 1);
            Desert.jar(s, x2 - 1, 1, z2 - 1);
            for (int side = -1; side <= 1; side += 2) {
                int ax = c + side * (p.colX + 1);
                for (int z = c - 1; z <= z2 - 2; z += 4) {
                    hang(s, ax, hd - 3, z);
                }
            }
        }
        // the great lamp from the dome, lamps under the deck by the dais
        int top = hd;
        while (top + 1 < s.sizeY && !s.solid(c, top + 1, mz)) {
            top++;
        }
        Furnish.greatChandelier(s, c, mz, top, top - (hd - 2));
        hang(s, c - 2, hd - 2, z1 + 1);
        hang(s, c + 2, hd - 2, z1 + 1);
        if (p.small()) {
            hang(s, x1 + 1, hd - 2, z2 - 1);
            hang(s, x2 - 1, hd - 2, z2 - 1);
        }
        // the places of the guards and the door
        p.point(HALL, c - 2, 1, c + 3);
        p.point(HALL, c + 2, 1, c + 3);
        p.point(HALL, c - 2, 1, c + 1);
        p.point(HALL, c + 2, 1, c + 1);
        p.point(PALACE_DOOR, c - 1, 1, z2 - 1);
        p.point(PALACE_DOOR, c + 1, 1, z2 - 1);
    }

    // ---------------------------------------------------------------- lights and the walk

    /**
     * Light everywhere outside: lanterns on brackets in the lanes, braziers on the terraces and the roofs, lanterns on the
     * deck of the main tower; the places of the guards on the walk.
     */
    private static void lights(Structure s, Plan p) {
        int c = p.c;
        if (!p.small()) {
            for (int z = p.hz1 + 2; z <= p.hz2; z += 6) {
                put(s, p.wx1 - 1, 5, z, Kit.upside(Desert.TRIM_STAIRS, "east"));
                put(s, p.wx1 - 1, 4, z, Kit.hangingLantern());
                put(s, p.wx2 + 1, 5, z, Kit.upside(Desert.TRIM_STAIRS, "west"));
                put(s, p.wx2 + 1, 4, z, Kit.hangingLantern());
            }
        }
        // braziers on the terraces of the wings and the blocks at the back, on the roof of the hall
        for (int side = -1; side <= 1; side += 2) {
            int wx = side < 0 ? p.wx1 + 2 : p.wx2 - 2;
            Desert.brazier(s, wx, p.wd + 1, p.hz2 - 2);
            int bx = side < 0 ? p.bwx1 + 1 : p.bex2 - 1;
            if (free(s, bx, p.wd + 1, p.bz - 1)) {
                Desert.brazier(s, bx, p.wd + 1, p.bz - 1);
            }
            put(s, c + side * (p.hx2 - p.hx1 - 4) / 2, p.hd + 1, p.hz2 - 2, Kit.lantern());
            put(s, c + side * (p.hx2 - p.hx1 - 4) / 2, p.hd + 1, p.hz1 + 2, Kit.lantern());
        }
        int h = (p.mw - 1) / 2;
        put(s, c - h, p.md + 1, p.mz + h, Kit.lantern());
        put(s, c + h, p.md + 1, p.mz - h, Kit.lantern());
        put(s, c, p.pd + 1, (p.gz1 + p.gz2) / 2, Kit.lantern());
        // the guards on the walk
        int lo = p.walkLo();
        int hi = p.walkHi();
        int y = p.f + 1;
        if (p.small()) {
            p.point(WALK, 10, y, lo);
            p.point(WALK, 20, y, lo);
            p.point(WALK, lo, y, 11);
            p.point(WALK, lo, y, 19);
            p.point(WALK, hi, y, 13);
            p.point(WALK, hi, y, 20);
        } else if (p.large()) {
            p.point(WALK, 12, y, lo);
            p.point(WALK, 33, y, lo);
            p.point(WALK, lo, y, 16);
            p.point(WALK, lo, y, 31);
            p.point(WALK, hi, y, 18);
            p.point(WALK, hi, y, 31);
            p.point(WALK, 9, y, hi);
            p.point(WALK, 37, y, hi);
        } else {
            p.point(WALK, 12, y, lo);
            p.point(WALK, 28, y, lo);
            p.point(WALK, lo, y, 14);
            p.point(WALK, lo, y, 28);
            p.point(WALK, hi, y, 14);
            p.point(WALK, hi, y, 28);
            p.point(WALK, 8, y, hi);
            p.point(WALK, 32, y, hi);
        }
    }
}
