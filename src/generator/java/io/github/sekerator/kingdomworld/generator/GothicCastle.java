package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle of the style "gothic": a dark cathedral-castle of slate and needles, drawn with the shapes of {@link Gothic}.
 *
 * <p>Its heart is one mass on the axis from the gate to the back: the throne hall built like the nave of a cathedral, and
 * behind it the keep. The hall has a high nave under a very steep roof with an iron cresting and lucarnes, lit by a
 * clerestory of stained lancets over low aisles under lean-to roofs; flying buttresses spring over the aisles from piers
 * with pinnacles and gargoyles. Its front looks at the gate: a grand portal under a wimperg and a rose window in the
 * parapet gable. Inside, a ribbed vault on clustered shafts, pointed arcades into the aisles, checkered paving, coronas of
 * candles, and at the north end the throne's place on a dais under a canopy before a wall of the cloth of the kingdom. The
 * keep rises behind it far above everything: string courses, tall lancets, a rose over the hall, a crown of pierced
 * parapets, great pinnacles and gargoyles, and over that a thin needle spire with lucarnes and an iron cross. Two low
 * ranges with dormers lean against the keep on both sides.
 *
 * <p>The ward: a curtain of dark ashlar on a rough plinth, with buttresses and a blind arcade of pale niches on its outer
 * face, a cornice and a pale tracery parapet over its walk, and a corbel with an iron railing where its inner face looks
 * into the court. At the front two slim octagonal towers of different heights with needle spires, and between them the
 * gatehouse: a pointed passage with a portcullis under a wimperg, a guard room over it and a crown with pinnacles. In the
 * larger castles arcaded cloister walks under lean-to roofs run along the inside of the curtain round the court; the court
 * has a checkered path, lantern posts, yews and (in the large castle) a well-head.
 *
 * <p>Rooms: the throne hall; in the keep a vestibule (in the larger castles the treasury beside it, its iron door in the
 * hall), the council room under a ribbed vault, the royal chambers with a bed of state (no bed that counts as a home), and
 * a chapel under the rose; in the west range the kitchen with its hearth and its stack through the roof and a room over
 * it; in the east range the armoury (in the small castle the treasury with the armoury over it); in the gatehouse the guard
 * room. The walks run all round through the towers and the gatehouse, reached by ladders in the towers.
 *
 * <p>One program drawn in three sizes: small 31 by 31, medium 41 by 41 and large 47 by 47. The throne stands in the
 * middle of the ground on the street layer at the north end of its hall, facing south; the castle leaves it and the ground
 * under it out. Layer 0 replaces the ground, layer 1 is level with the street.
 */
final class GothicCastle {
    private static final String AIR = Structure.AIR;
    private static final String GRASS = "minecraft:grass_block[snowy=false]";

    /** The three sizes of the castle. */
    enum Size {
        SMALL, MEDIUM, LARGE
    }

    private static final String[] POINT_NAMES = {"palace_hall", "palace_door", "castle_gate", "castle_wall", "treasury"};
    private static final int HALL = 0;
    private static final int PALACE_DOOR = 1;
    private static final int GATE = 2;
    private static final int WALK = 3;
    private static final int TREASURY = 4;

    private GothicCastle() {
    }

    /**
     * The three castles of the style: small, medium and large, with their named places.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(building(Size.SMALL, "gothic_castle_small", "minecraft:deepslate_bricks"));
        list.add(building(Size.MEDIUM, "gothic_castle_medium", "minecraft:purple_stained_glass"));
        list.add(building(Size.LARGE, "gothic_castle_large", "minecraft:wither_skeleton_skull"));
        return list;
    }

    /**
     * The castle of a size as a template of the game: a palace of defence anchored on the street before its gate, with its
     * named places.
     */
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

    // ---------------------------------------------------------------- the plan of a size

    /**
     * The numbers of a castle of a size: its curtain, its hall, its keep, its ranges, its gatehouse and towers, and the
     * places named in it.
     */
    private static final class Plan {
        final Size size;
        /** The ground (square), the height of the box and its middle: the cell of the throne. */
        final int n;
        final int height;
        final int c;
        /** The curtain: its outer line from every edge and its top layer (one walks on the layer above). */
        final int o;
        final int f;
        /** The first and the last cell of the ward inside the curtain. */
        final int in1;
        final int in2;
        /** The nave: half its width between its walls, the inner width of an aisle, its north wall and its front. */
        final int hw;
        final int aw;
        final int hz1;
        final int hz2;
        /** Heights of the hall: the aisle walls, the clerestory lights (first layer, height), the vault, the eaves. */
        final int aisleTop;
        final int clerY;
        final int clerH;
        final int vaultY;
        final int eaves;
        /** The flying buttresses: the layer where their arches meet the nave and how far they reach out. */
        final int flyY;
        final int reach;
        /** The keep: its walls (its south wall is the north wall of the hall) and its crown. */
        final int kx1;
        final int kx2;
        final int kz1;
        final int keepTop;
        /** The needle spire on the keep: its radius and height. */
        final int spireR;
        final int spireH;
        /** The floors of the keep: the council room, the chambers and the chapel. */
        final int kf1;
        final int kf2;
        final int kf3;
        /** The ranges beside the keep: their south wall, eaves and upper floor. */
        final int rz2;
        final int rEaves;
        final int rFloor;
        /** The gatehouse: half its width, its north face, the height of its passage and its crown. */
        final int gw;
        final int gz1;
        final int ph;
        final int gateTop;
        /** The towers at the front corners: half-size, top of the walls, radius and height of the needle. */
        final int[] west;
        final int[] east;
        /** Named places: {kind, x, y, z}. */
        final List<int[]> points = new ArrayList<>();

        Plan(Size size) {
            this.size = size;
            switch (size) {
                case SMALL -> {
                    n = 31;
                    height = 40;
                    o = 1;
                    f = 7;
                    hw = 3;
                    aw = 2;
                    hz1 = 12;
                    hz2 = 22;
                    aisleTop = 4;
                    clerY = 9;
                    clerH = 2;
                    vaultY = 10;
                    eaves = 13;
                    flyY = 11;
                    reach = 4;
                    kx1 = 11;
                    kx2 = 19;
                    kz1 = 4;
                    keepTop = 25;
                    spireR = 2;
                    spireH = 15;
                    kf1 = 7;
                    kf2 = 14;
                    kf3 = 19;
                    rz2 = 10;
                    rEaves = 8;
                    rFloor = 5;
                    gw = 3;
                    gz1 = 26;
                    ph = 3;
                    gateTop = 13;
                    west = new int[]{3, 15, 2, 14};
                    east = new int[]{3, 12, 2, 11};
                }
                case MEDIUM -> {
                    n = 41;
                    height = 44;
                    o = 2;
                    f = 8;
                    hw = 4;
                    aw = 2;
                    hz1 = 15;
                    hz2 = 29;
                    aisleTop = 4;
                    clerY = 9;
                    clerH = 3;
                    vaultY = 10;
                    eaves = 14;
                    flyY = 12;
                    reach = 4;
                    kx1 = 16;
                    kx2 = 24;
                    kz1 = 5;
                    keepTop = 29;
                    spireR = 2;
                    spireH = 15;
                    kf1 = 7;
                    kf2 = 15;
                    kf3 = 22;
                    rz2 = 11;
                    rEaves = 9;
                    rFloor = 5;
                    gw = 4;
                    gz1 = 34;
                    ph = 4;
                    gateTop = 16;
                    west = new int[]{3, 18, 2, 15};
                    east = new int[]{3, 14, 2, 12};
                }
                default -> {
                    n = 47;
                    height = 48;
                    o = 2;
                    f = 9;
                    hw = 4;
                    aw = 3;
                    hz1 = 17;
                    hz2 = 31;
                    aisleTop = 4;
                    clerY = 10;
                    clerH = 3;
                    vaultY = 11;
                    eaves = 15;
                    flyY = 13;
                    reach = 5;
                    kx1 = 18;
                    kx2 = 28;
                    kz1 = 5;
                    keepTop = 32;
                    spireR = 2;
                    spireH = 16;
                    kf1 = 8;
                    kf2 = 17;
                    kf3 = 25;
                    rz2 = 11;
                    rEaves = 10;
                    rFloor = 6;
                    gw = 5;
                    gz1 = 40;
                    ph = 5;
                    gateTop = 18;
                    west = new int[]{3, 22, 2, 16};
                    east = new int[]{3, 16, 2, 13};
                }
            }
            c = (n - 1) / 2;
            in1 = o + 3;
            in2 = n - 1 - in1;
        }

        boolean small() {
            return size == Size.SMALL;
        }

        boolean large() {
            return size == Size.LARGE;
        }

        /** The middle of a front tower along x: the west one for {@code sx} -1, the east one for +1. */
        int towerX(int[] t, int sx) {
            int k = o + t[0] - 1;
            return sx < 0 ? k : n - 1 - k;
        }

        /** The middle of a front tower along z. */
        int towerZ(int[] t) {
            return n - 1 - (o + t[0] - 1);
        }

        /** The middle row of the keep. */
        int kcz() {
            return (kz1 + hz1) / 2;
        }

        void point(int kind, int x, int y, int z) {
            points.add(new int[]{kind, x, y, z});
        }
    }

    // ---------------------------------------------------------------- small helpers

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static void putFree(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z) && !s.solid(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

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

    /** A lantern hanging on a chain from the first solid block above it. */
    private static void hang(Structure s, int x, int y, int z, String lantern) {
        put(s, x, y, z, lantern);
        for (int yy = y + 1; yy < s.sizeY && !s.solid(x, yy, z); yy++) {
            s.set(x, yy, z, Kit.chain());
        }
    }

    /** A post of black stone with a soul lantern on it, standing on layer {@code y}. */
    private static void lanternPost(Structure s, int x, int y, int z) {
        put(s, x, y, z, Gothic.STAND);
        put(s, x, y + 1, z, Gothic.STAND);
        put(s, x, y + 2, z, Gothic.SOUL_LANTERN);
    }

    /** Whether a column lies on the rim of an octagon of half-size {@code r}: inside it with a neighbour outside. */
    private static boolean onRim(int dx, int dz, int r) {
        return Gothic.inOctagon(dx, dz, r) && (!Gothic.inOctagon(dx + 1, dz, r) || !Gothic.inOctagon(dx - 1, dz, r)
                || !Gothic.inOctagon(dx, dz + 1, r) || !Gothic.inOctagon(dx, dz - 1, r));
    }

    /** The farthest column from the middle of an octagon in a row {@code dz} of it, or -1 outside it. */
    private static int reach(int dz, int r) {
        for (int ax = r; ax >= 0; ax--) {
            if (Gothic.inOctagon(ax, dz, r)) {
                return ax;
            }
        }
        return -1;
    }

    /**
     * Stairs on the rim of an octagon with their high side to its middle; a corner stair where the rim turns.
     */
    private static String octSlope(String block, int dx, int dz, int r) {
        boolean outX = dx != 0 && !Gothic.inOctagon(dx + Integer.signum(dx), dz, r);
        boolean outZ = dz != 0 && !Gothic.inOctagon(dx, dz + Integer.signum(dz), r);
        if (outX && outZ) {
            return Kit.stairs(block, dz > 0 ? "north" : "south", dz > 0 == dx > 0 ? "outer_left" : "outer_right");
        }
        if (outX || !outZ && Math.abs(dx) >= Math.abs(dz)) {
            return Kit.stairs(block, dx > 0 ? "west" : "east");
        }
        return Kit.stairs(block, dz > 0 ? "north" : "south");
    }

    /** The rows of the transverse ribs of the nave, one every four from the north end; the flying buttresses stand there. */
    private static int[] ribs(Plan p) {
        int count = (p.hz2 - 1 - (p.hz1 + 1)) / 4 + 1;
        int[] ribs = new int[count];
        for (int i = 0; i < count; i++) {
            ribs[i] = p.hz1 + 1 + 4 * i;
        }
        return ribs;
    }

    /** The middle rows of the bays between the ribs: the windows and the arcades. */
    private static int[] bays(Plan p) {
        int[] ribs = ribs(p);
        int[] bays = new int[ribs.length - 1];
        for (int i = 0; i < bays.length; i++) {
            bays[i] = ribs[i] + 2;
        }
        return bays;
    }

    /** A cornice of hanging trim stairs before a wall looking {@code facing}, only where nothing stands. */
    private static void corniceFree(Structure s, int x1, int z1, int x2, int z2, int y, String facing) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                putFree(s, x, y, z, Kit.upside(Gothic.TRIM_STAIRS, Furnish.opposite(facing)));
            }
        }
    }

    /** A yew: a dark spire of leaves on a short trunk of the timber, {@code h} layers high, in a bed of grass. */
    private static void yew(Structure s, int x, int z, int h) {
        put(s, x, 0, z, GRASS);
        put(s, x, 1, z, Kit.log(Gothic.BEAM, "y"));
        String leaves = Kit.leaves("spruce");
        for (int y = 2; y <= h; y++) {
            put(s, x, y, z, leaves);
            if (y <= h - 2) {
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    put(s, x + d[0], y, z + d[1], leaves);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the castle

    /**
     * Draws the castle of a plan: the ground, the curtain with its cloisters, the hall, the ranges, the keep, the front
     * towers and the gatehouse, the court, then the rooms and their furniture.
     */
    private static Structure castle(Plan p) {
        Structure s = new Structure(p.n, p.height, p.n);
        ground(s, p);
        curtain(s, p);
        if (!p.small()) {
            cloisters(s, p);
        }
        hall(s, p);
        ranges(s, p);
        keep(s, p);
        towers(s, p);
        gatehouse(s, p);
        railings(s, p);
        court(s, p);
        hallInside(s, p);
        keepInside(s, p);
        westRange(s, p);
        eastRange(s, p);
        walkPoints(p);
        // Grass only where nothing stands on it.
        for (int z = 0; z < p.n; z++) {
            for (int x = 0; x < p.n; x++) {
                if (s.get(x, 0, z).equals(GRASS) && s.solid(x, 1, z) && States.isFull(s.get(x, 1, z))) {
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
     * The ground: grass round the castle, the ward paved with rough stone.
     */
    private static void ground(Structure s, Plan p) {
        box(s, 0, 0, 0, p.n - 1, 0, p.n - 1, GRASS);
        box(s, p.in1, 0, p.in1, p.in2, 0, p.in2, Gothic.ROUGH);
    }

    // ---------------------------------------------------------------- the curtain

    /**
     * The curtain: four walls three blocks thick on the outer lines, each with its walk.
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
     * One side of the curtain on the line {@code line}, {@code in} the way to the ward: the wall stone on a rough course
     * with a sloping plinth outside, a cornice of trim under a tracery parapet on its outer line, the walk behind it on
     * the two lines inside; on the outer face buttresses every six blocks with two blind lancets of plaster between them,
     * kept clear of the towers and the gate.
     */
    private static void side(Structure s, Plan p, boolean alongX, int line, int in) {
        int f = p.f;
        int a1 = p.o;
        int a2 = p.n - 1 - p.o;
        String toIn = across(alongX, in);
        String out = across(alongX, -in);
        for (int a = a1; a <= a2; a++) {
            for (int k = 0; k <= 2; k++) {
                at(s, alongX, a, 0, line + in * k, Gothic.ROUGH);
                for (int y = 1; y <= f; y++) {
                    at(s, alongX, a, y, line + in * k, k == 0 && y == 1 ? Gothic.ROUGH : Gothic.WALL);
                }
            }
            at(s, alongX, a, 1, line - in, Kit.stairs(Gothic.ROUGH_STAIRS, toIn));
        }
        int x1 = alongX ? a1 : line;
        int z1 = alongX ? line : a1;
        int x2 = alongX ? a2 : line;
        int z2 = alongX ? line : a2;
        Gothic.parapet(s, x1, z1, x2, z2, f + 1);
        for (int a = a1 + 3; a <= a2 - 3; a += 6) {
            at(s, alongX, a, f + 3, line, Gothic.SOUL_LANTERN);
        }
        int ox = alongX ? 0 : -in;
        int oz = alongX ? -in : 0;
        Gothic.cornice(s, x1 + ox, z1 + oz, x2 + ox, z2 + oz, f, out);
        boolean south = alongX && in < 0;
        int frontEnd = p.towerZ(p.west) - p.west[0] - 2;
        int westEnd = p.towerX(p.west, -1) + p.west[0] + 2;
        int eastEnd = p.towerX(p.east, 1) - p.east[0] - 2;
        int nh = f - 5;
        for (int k = -4; k <= 4; k++) {
            int a = p.c + 6 * k;
            for (int j = 0; j <= 4; j += 2) {
                int b = a + j;
                if (b < a1 + 3 || b > a2 - 3) {
                    continue;
                }
                if (south && (Math.abs(b - p.c) <= p.gw + 2 || b <= westEnd || b >= eastEnd)) {
                    continue;
                }
                if (!alongX && b >= frontEnd) {
                    continue;
                }
                int x = alongX ? b : line;
                int z = alongX ? line : b;
                if (j == 0) {
                    Gothic.buttress(s, x, 1, z, out, f - 1, 1, false);
                } else {
                    Gothic.blindLancet(s, x, 3, z, out, 1, nh);
                    if (j == 2) {
                        at(s, alongX, b + 1, 3 + nh, line - in, Gothic.TRACERY);
                    }
                }
            }
        }
    }

    /**
     * The cloisters of the ward: arcaded walks along the inside of the curtain under lean-to roofs that rise to its walk,
     * on the west and east from the ranges to the front towers, and in the large castle also along the front on both sides
     * of the gatehouse.
     */
    private static void cloisters(Structure s, Plan p) {
        int z1 = p.rz2 + 1;
        int wx = p.towerX(p.west, -1);
        int wz = p.towerZ(p.west);
        int z2 = p.n;
        for (int x = p.in1; x <= p.in1 + 2; x++) {
            z2 = Math.min(z2, wz - reach(x - wx, p.west[0]) - 1);
        }
        cloister(s, p, false, z1, z2, p.in1 + 2, -1);
        cloister(s, p, false, z1, z2, p.in2 - 2, 1);
        if (p.large()) {
            int x1 = wx + 1;
            for (int z = p.in2 - 2; z <= p.in2; z++) {
                x1 = Math.max(x1, wx + reach(z - wz, p.west[0]) + 1);
            }
            int ex = p.towerX(p.east, 1);
            int ez = p.towerZ(p.east);
            int x4 = ex - 1;
            for (int z = p.in2 - 2; z <= p.in2; z++) {
                x4 = Math.min(x4, ex - reach(z - ez, p.east[0]) - 1);
            }
            cloister(s, p, true, x1, p.c - p.gw - 1, p.in2 - 2, 1);
            cloister(s, p, true, p.c + p.gw + 1, x4, p.in2 - 2, 1);
        }
    }

    /**
     * One cloister walk along a line from {@code a1} to {@code a2}: its arcade on the line {@code arc} (piers of the wall
     * stone between pointed openings with heads of tracery), the walk two wide behind it toward the curtain ({@code in})
     * paved in checkers, under a lean-to roof whose top row lies level with the walk of the curtain, soul lanterns under it.
     */
    private static void cloister(Structure s, Plan p, boolean alongX, int a1, int a2, int arc, int in) {
        int f = p.f;
        for (int a = a1; a <= a2; a++) {
            at(s, alongX, a, 0, arc, Gothic.ROUGH);
            for (int y = 1; y <= f - 3; y++) {
                at(s, alongX, a, y, arc, y == 1 ? Gothic.ROUGH : Gothic.WALL);
            }
            for (int k = 1; k <= 2; k++) {
                int cc = arc + in * k;
                at(s, alongX, a, 0, cc, Math.floorMod(a + cc, 2) == 0 ? Gothic.PAVING_LIGHT : Gothic.PAVING_DARK);
                for (int y = 1; y <= f - 1; y++) {
                    at(s, alongX, a, y, cc, AIR);
                }
            }
        }
        int lo = Math.min(arc, arc + 2 * in);
        int hi = Math.max(arc, arc + 2 * in);
        if (alongX) {
            Gothic.shedRoof(s, a1, lo, a2, hi, f - 2, across(true, in), 1);
        } else {
            Gothic.shedRoof(s, lo, a1, hi, a2, f - 2, across(false, in), 1);
        }
        String face = across(alongX, -in);
        int oh = f - 5;
        for (int a = a1 + 2; a + 1 <= a2; a += 4) {
            Gothic.pointedArch(s, alongX ? a : arc, 1, alongX ? arc : a, face, 3, oh, 1, Gothic.TRACERY_STAIRS, Gothic.TRACERY_SLAB);
            put(s, alongX ? a : arc + in, f - 2, alongX ? arc + in : a, Gothic.SOUL_LANTERN_HANGING);
        }
    }

    /**
     * Where the inside of the curtain looks into the open court, the walk gets a corbel of trim on its inner edge with an
     * iron railing on it, so that no one falls into the court.
     */
    private static void railings(Structure s, Plan p) {
        for (int a = p.in1; a <= p.in2; a++) {
            rail(s, p, a, p.in1, "north");
            rail(s, p, a, p.in2, "south");
            rail(s, p, p.in1, a, "west");
            rail(s, p, p.in2, a, "east");
        }
    }

    /** The corbel and railing on one cell of the inner edge of the walk, if the column under it is open to the court. */
    private static void rail(Structure s, Plan p, int x, int z, String toWall) {
        for (int y = 1; y <= p.f + 2; y++) {
            if (s.solid(x, y, z)) {
                return;
            }
        }
        put(s, x, p.f, z, Kit.upside(Gothic.TRIM_STAIRS, toWall));
        put(s, x, p.f + 1, z, Gothic.IRON);
    }

    // ---------------------------------------------------------------- the front towers

    /**
     * The two front corner towers, the west one taller than the east one.
     */
    private static void towers(Structure s, Plan p) {
        tower(s, p, p.west, -1);
        tower(s, p, p.east, 1);
    }

    /**
     * A slim octagonal tower on a front corner of the curtain ({@code sx} -1 west, +1 east): a rough plinth with a sloping
     * footing, walls of the wall stone, a door from the court and a ladder up the inside of its south wall to the floor of
     * the walks, which come in through openings in its walls; rooms above with lancets where it looks out, small pinnacles
     * on its diagonals and a needle spire with lucarnes and a cross.
     */
    private static void tower(Structure s, Plan p, int[] t, int sx) {
        int r = t[0];
        int top = t[1];
        int f = p.f;
        int cx = p.towerX(t, sx);
        int cz = p.towerZ(t);
        int mid = top - f > 9 ? (f + top) / 2 : -1;
        for (int dz = -r - 1; dz <= r + 1; dz++) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                int x = cx + dx;
                int z = cz + dz;
                if (Gothic.inOctagon(dx, dz, r)) {
                    boolean rim = onRim(dx, dz, r);
                    put(s, x, 0, z, rim ? Gothic.ROUGH : Math.floorMod(x + z, 2) == 0 ? Gothic.PAVING_LIGHT : Gothic.PAVING_DARK);
                    for (int y = 1; y <= top; y++) {
                        if (rim) {
                            put(s, x, y, z, y == 1 ? Gothic.ROUGH : Gothic.WALL);
                        } else {
                            put(s, x, y, z, y == f || y == top || y == mid ? Gothic.PLANKS : AIR);
                        }
                    }
                } else if (Gothic.inOctagon(dx, dz, r + 1)) {
                    putFree(s, x, 1, z, octSlope(Gothic.ROUGH_STAIRS, dx, dz, r + 1));
                }
            }
        }
        // a pale string course over the walks where the tower stands free
        for (int dz = -r - 1; dz <= r + 1; dz++) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                if (!Gothic.inOctagon(dx, dz, r) && Gothic.inOctagon(dx, dz, r + 1)) {
                    putFree(s, cx + dx, f + 3, cz + dz, Kit.upside(Gothic.TRACERY_STAIRS, facingOf(octSlope(Gothic.TRACERY_STAIRS, dx, dz, r + 1))));
                }
            }
        }
        // the ladder up the inside of the south wall
        int ladderTop = mid > 0 ? mid : f;
        for (int y = 1; y <= ladderTop; y++) {
            put(s, cx, y, cz + r - 1, Kit.ladder("north"));
        }
        // the walks come in: the side walk from the north, the front walk from the gate's side
        int[] cols = {sx < 0 ? p.o + 1 : p.n - 2 - p.o, sx < 0 ? p.o + 2 : p.n - 3 - p.o};
        for (int x : cols) {
            for (int z = cz - r; z <= cz; z++) {
                if (Gothic.inOctagon(x - cx, z - cz, r)) {
                    box(s, x, f + 1, z, x, f + 2, z, AIR);
                    break;
                }
            }
        }
        int[] rows = {p.n - 2 - p.o, p.n - 3 - p.o};
        for (int z : rows) {
            for (int k = r; k >= -r; k--) {
                int x = cx - sx * k;
                if (Gothic.inOctagon(x - cx, z - cz, r)) {
                    box(s, x, f + 1, z, x, f + 2, z, AIR);
                    break;
                }
            }
        }
        // the door from the court, in its last row
        int dz = p.in2 - cz;
        int dx = reach(dz, r);
        s.door(cx - sx * dx, 1, cz + dz, Gothic.DOOR, sx < 0 ? "west" : "east", "left");
        // lancets where it looks out and over the court
        String outX = sx < 0 ? "west" : "east";
        int upper = (mid > 0 ? mid : f) + 2;
        int h = Math.min(4, top - upper - 2);
        if (h >= 1) {
            Gothic.lancet(s, cx + sx * r, upper, cz, outX, 1, h, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, cx, upper, cz + r, "south", 1, h, Gothic.VIOLET, Gothic.CRIMSON);
            if (upper >= f + 5) {
                Gothic.lancet(s, cx, upper, cz - r, "north", 1, h, Gothic.VIOLET, Gothic.CRIMSON);
                Gothic.lancet(s, cx - sx * r, upper, cz, sx < 0 ? "east" : "west", 1, h, Gothic.VIOLET, Gothic.CRIMSON);
            }
        }
        if (mid > 0) {
            Gothic.lancet(s, cx + sx * r, f + 2, cz, outX, 1, 2, Gothic.AZURE, Gothic.CRIMSON);
        }
        Gothic.lancet(s, cx + sx * r, 2, cz, outX, 1, 2, Gothic.AZURE, Gothic.CRIMSON);
        // the spire, small pinnacles on the diagonals
        Gothic.needleSpire(s, cx, top + 1, cz, t[2], t[3]);
        int d = r - 1;
        for (int ddx = -d; ddx <= d; ddx += 2 * d) {
            for (int ddz = -d; ddz <= d; ddz += 2 * d) {
                Gothic.pinnacle(s, cx + ddx, top + 1, cz + ddz, 3);
            }
        }
        // light on every floor and under the spire, a bench by the walk
        put(s, cx - sx, f - 1, cz, Kit.hangingLantern());
        put(s, cx - sx, top - 1, cz, Kit.hangingLantern());
        if (mid > 0) {
            put(s, cx - sx, mid - 1, cz, Kit.hangingLantern());
        }
        put(s, cx, top + 1, cz, Kit.lantern());
        Furnish.bench(s, cx - sx * (r - 1), cz - 1, cx - sx * (r - 1), cz, f + 1, sx < 0 ? "east" : "west", Gothic.WOOD);
    }

    /** The facing of a stairs state. */
    private static String facingOf(String stairs) {
        int i = stairs.indexOf("facing=") + 7;
        return stairs.substring(i, stairs.indexOf(',', i));
    }

    // ---------------------------------------------------------------- the gatehouse

    /**
     * The gatehouse in the middle of the front: a block of the wall stone through which a pointed passage runs, its
     * arches of tracery, a portcullis half raised under the outer arch, a hood and a wimperg of tracery with a plaster
     * field and a pinnacle over the outer arch between pinnacled piers, banners; over the passage the guard room that the
     * walks of the front run through (in the larger castles a ladder from the court too), and a crown of parapet,
     * great pinnacles and gargoyles.
     */
    private static void gatehouse(Structure s, Plan p) {
        int c = p.c;
        int x1 = c - p.gw;
        int x2 = c + p.gw;
        int z1 = p.gz1;
        int z2 = p.n - 2;
        int f = p.f;
        int roof = p.gateTop - 1;
        box(s, x1, 0, z1, x2, 0, z2, Gothic.ROUGH);
        box(s, x1, 1, z1, x2, f, z2, Gothic.WALL);
        for (int y = f + 1; y <= roof; y++) {
            s.walls(x1, y, z1, x2, y, z2, Gothic.WALL);
        }
        s.walls(x1, 1, z1, x2, 1, z2, Gothic.ROUGH);
        box(s, x1 + 1, f + 1, z1 + 1, x2 - 1, roof - 1, z2 - 1, AIR);
        box(s, x1 + 1, f, z1 + 1, x2 - 1, f, z2 - 1, Gothic.PLANKS);
        box(s, x1 + 1, roof, z1 + 1, x2 - 1, roof, z2 - 1, Gothic.WALL);
        // the passage, paved, with the portcullis under its outer arch
        Gothic.pointedArch(s, c, 1, z2, "south", 3, p.ph, z2 - z1 + 1, Gothic.TRACERY_STAIRS, Gothic.TRACERY_SLAB);
        Gothic.paving(s, c - 1, z1 - 1, c + 1, p.n - 1, 0);
        box(s, c - 1, p.ph, z2 - 1, c + 1, p.ph, z2 - 1, Gothic.IRON);
        put(s, c, p.ph + 1, z2 - 1, Gothic.IRON);
        int zm = (z1 + z2) / 2;
        put(s, c, p.ph + 1, zm, Gothic.SOUL_LANTERN_HANGING);
        put(s, c, p.ph + 1, z1 + 1, Gothic.SOUL_LANTERN_HANGING);
        // the walks of the front run through the guard room
        for (int z = p.n - 3 - p.o; z <= p.n - 2 - p.o; z++) {
            box(s, x1, f + 1, z, x1, f + 2, z, AIR);
            box(s, x2, f + 1, z, x2, f + 2, z, AIR);
        }
        // a ladder from the court up to the guard room, where the gatehouse is wide enough
        if (p.gw >= 4) {
            s.door(x1 + 1, 1, z1, Gothic.DOOR, "south", "left");
            box(s, x1 + 1, 1, z1 + 1, x1 + 1, f - 1, z1 + 2, AIR);
            for (int y = 1; y <= f; y++) {
                put(s, x1 + 1, y, z1 + 2, Kit.ladder("north"));
            }
            put(s, x1 + 1, f - 1, z1 + 1, Kit.hangingLantern());
        }
        archFront(s, c, z2 + 1, p.ph, roof - 1, true);
        archFront(s, c, z1 - 1, p.ph, f + 2, false);
        // banners of the kingdom on the front
        put(s, x1 + 1, f - 1, z2 + 1, Kit.wallBanner("red", "south"));
        put(s, x2 - 1, f - 1, z2 + 1, Kit.wallBanner("red", "south"));
        // lancets of the guard room over the piers, and on its back
        int lh = Math.max(1, roof - f - 5);
        for (int side = -1; side <= 1; side += 2) {
            Gothic.lancet(s, c + side * (p.gw - 1), f + 3, z2, "south", 1, lh, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, c + side * 2, f + 3, z1, "north", 1, lh, Gothic.VIOLET, Gothic.CRIMSON);
        }
        Gothic.crown(s, x1, z1, x2, z2, p.gateTop, true);
        // the guard room: benches, a table, weapons, light
        int y = f + 1;
        Furnish.bench(s, c - 1, z2 - 1, c + 1, z2 - 1, y, "north", Gothic.WOOD);
        Furnish.table(s, c + 1, y, z1 + 2, Gothic.WOOD);
        Furnish.chair(s, c, y, z1 + 2, "east", Gothic.WOOD);
        Furnish.weaponRack(s, x2 - 1, y, z1 + 1, "south", Gothic.WOOD);
        hang(s, c, roof - 1, zm, Kit.hangingLantern());
        p.point(GATE, c - 1, 1, zm);
        p.point(GATE, c + 1, 1, zm);
    }

    /**
     * The face of the gate's arch in the cells before its wall at row {@code z}: colonnettes of tracery at its sides, a
     * pointed hood over its head and, on the front, a wimperg rising two layers a column to a carved apex with a pinnacle,
     * its field of plaster with a carved rosette, between piers of the wall stone with pinnacles; nothing rises over layer
     * {@code limit}.
     */
    private static void archFront(Structure s, int c, int z, int ph, int limit, boolean front) {
        String plus = "east";
        String minus = "west";
        for (int y = 1; y <= ph; y++) {
            put(s, c - 2, y, z, Gothic.TRACERY_WALL);
            put(s, c + 2, y, z, Gothic.TRACERY_WALL);
        }
        int hood = ph + 1;
        put(s, c - 2, hood, z, Kit.stairs(Gothic.TRACERY_STAIRS, plus));
        put(s, c + 2, hood, z, Kit.stairs(Gothic.TRACERY_STAIRS, minus));
        put(s, c - 1, hood, z, Kit.upside(Gothic.TRACERY_STAIRS, minus));
        put(s, c + 1, hood, z, Kit.upside(Gothic.TRACERY_STAIRS, plus));
        put(s, c - 1, hood + 1, z, Kit.stairs(Gothic.TRACERY_STAIRS, plus));
        put(s, c + 1, hood + 1, z, Kit.stairs(Gothic.TRACERY_STAIRS, minus));
        put(s, c, hood + 1, z, Kit.topSlab(Gothic.TRACERY_SLAB));
        put(s, c, hood + 2, z, Kit.slab(Gothic.TRACERY_SLAB));
        if (!front) {
            return;
        }
        // the wimperg: edges rising two layers a column from the hood to an apex, a field of plaster
        int apex = hood + 6;
        if (apex + 1 <= limit) {
            for (int au = 3; au >= 1; au--) {
                int edge = hood + 2 * (3 - au);
                for (int y = hood + 1; y < edge; y++) {
                    if (au == 3 || y > hood + 1) {
                        put(s, c - au, y, z, Gothic.PLASTER);
                        put(s, c + au, y, z, Gothic.PLASTER);
                    }
                }
                put(s, c - au, edge, z, Kit.stairs(Gothic.TRACERY_STAIRS, plus));
                put(s, c + au, edge, z, Kit.stairs(Gothic.TRACERY_STAIRS, minus));
            }
            for (int y = hood + 2; y < apex; y++) {
                put(s, c, y, z, y == hood + 3 ? Gothic.CARVED : Gothic.PLASTER);
            }
            put(s, c, apex, z, Gothic.CARVED);
            int ph2 = Math.min(3, limit - apex);
            if (ph2 >= 1) {
                Gothic.pinnacle(s, c, apex + 1, z, ph2);
            }
        }
        // piers with pinnacles at its sides
        for (int side = -1; side <= 1; side += 2) {
            int x = c + side * 4;
            for (int y = 0; y <= ph + 1; y++) {
                put(s, x, y, z, y <= 1 ? Gothic.ROUGH : Gothic.WALL);
            }
            Gothic.pinnacle(s, x, ph + 2, z, Math.min(3, limit - ph - 1));
        }
    }

    // ---------------------------------------------------------------- the throne hall

    /**
     * The shell of the throne hall: the nave and its aisles with their walls, the arcades between them, a lean-to roof
     * over each aisle and the steep roof of the nave with its cresting and lucarnes, the clerestory and the aisle windows,
     * the flying buttresses on every rib, and the front: buttresses, the grand portal, blind lancets and the rose in the
     * parapet gable.
     */
    private static void hall(Structure s, Plan p) {
        int c = p.c;
        int xw = c - p.hw;
        int xe = c + p.hw;
        int aww = xw - p.aw - 1;
        int awe = xe + p.aw + 1;
        int z1 = p.hz1;
        int z2 = p.hz2;
        int[] ribs = ribs(p);
        int[] bays = bays(p);
        box(s, aww, 0, z1, awe, 0, z2, Gothic.ROUGH);
        int ridgeTop = p.eaves + (p.hw + 1) * 2;
        box(s, xw + 1, 1, z1 + 1, xe - 1, ridgeTop, z2 - 1, AIR);
        for (int y = 1; y < p.eaves; y++) {
            String st = y == 1 ? Gothic.ROUGH : Gothic.WALL;
            box(s, xw, y, z1, xw, y, z2, st);
            box(s, xe, y, z1, xe, y, z2, st);
            box(s, xw, y, z2, xe, y, z2, st);
        }
        // the aisles: their outer walls, their ends under the slope of their roofs, the inside cleared
        for (int k = 0; k <= p.aw; k++) {
            int top = p.aisleTop + k;
            for (int side = -1; side <= 1; side += 2) {
                int x = side < 0 ? aww + k : awe - k;
                for (int y = 1; y <= top; y++) {
                    String st = y == 1 ? Gothic.ROUGH : Gothic.WALL;
                    if (k == 0) {
                        box(s, x, y, z1, x, y, z2, st);
                    } else {
                        box(s, x, y, z1 + 1, x, y, z2 - 1, AIR);
                        put(s, x, y, z1, st);
                        put(s, x, y, z2, st);
                    }
                }
            }
        }
        // arcades from the nave into the aisles
        for (int zb : bays) {
            Gothic.pointedArch(s, xw, 1, zb, "west", 3, p.aisleTop - 1, 1, Gothic.TRACERY_STAIRS, Gothic.TRACERY_SLAB);
            Gothic.pointedArch(s, xe, 1, zb, "east", 3, p.aisleTop - 1, 1, Gothic.TRACERY_STAIRS, Gothic.TRACERY_SLAB);
        }
        // roofs
        Gothic.shedRoof(s, aww, z1, xw - 1, z2, p.aisleTop + 1, "east", 1);
        Gothic.shedRoof(s, xe + 1, z1, awe, z2, p.aisleTop + 1, "west", 1);
        int ridge = Gothic.steepRoof(s, xw, z1, xe, z2, p.eaves, false, 2, Gothic.OPEN, Gothic.PARAPET);
        Gothic.cresting(s, c, z1 + 1, c, z2 - 1, ridge);
        for (int i = 0; i < bays.length; i += 2) {
            Gothic.dormer(s, xw, z1, xe, z2, p.eaves, false, 2, bays[i], true, 3);
            Gothic.dormer(s, xw, z1, xe, z2, p.eaves, false, 2, bays[i], false, 3);
        }
        // the clerestory and the windows of the aisles
        for (int zb : bays) {
            Gothic.lancet(s, xw, p.clerY, zb, "west", 1, p.clerH, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, xe, p.clerY, zb, "east", 1, p.clerH, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, aww, 2, zb, "west", 1, p.aisleTop - 2, Gothic.AZURE, Gothic.CRIMSON);
            Gothic.lancet(s, awe, 2, zb, "east", 1, p.aisleTop - 2, Gothic.AZURE, Gothic.CRIMSON);
        }
        // the flying buttresses
        for (int zr : ribs) {
            Gothic.flyingButtress(s, xw, p.flyY, zr, "west", p.reach, 1, true);
            Gothic.flyingButtress(s, xe, p.flyY, zr, "east", p.reach, 1, true);
        }
        // the front: buttresses where the portal leaves room, the grand portal, the rose in the gable
        if (p.hw >= 4) {
            Gothic.buttress(s, xw, 1, z2, "south", p.eaves - 1, 2, true);
            Gothic.buttress(s, xe, 1, z2, "south", p.eaves - 1, 2, true);
        }
        Gothic.portal(s, c, 1, z2, "south", 3, true);
        int rose = p.small() ? 5 : 7;
        Gothic.rose(s, c, p.eaves + (rose - 1) / 2, z2, "south", rose);
        if (!p.small()) {
            for (int side = -1; side <= 1; side += 2) {
                Gothic.blindLancet(s, c + side * 2, 12, z2, "south", 1, Math.max(1, p.eaves - 14));
            }
        }
        Gothic.lancet(s, c, p.eaves + rose + 1, z2, "south", 1, 1, Gothic.VIOLET, Gothic.CRIMSON);
        int ax = aww + 1 + (p.aw - 1) / 2;
        Gothic.lancet(s, ax, 2, z2, "south", 1, p.aisleTop - 2, Gothic.AZURE, Gothic.CRIMSON);
        Gothic.lancet(s, p.n - 1 - ax, 2, z2, "south", 1, p.aisleTop - 2, Gothic.AZURE, Gothic.CRIMSON);
    }

    /**
     * The inside of the throne hall: checkered paving, the ribbed vault on clustered shafts with lunettes before the
     * clerestory, the dais of the throne with its canopy before a wall hung with the cloth of the kingdom, a runner to the
     * door, coronas, candelabra, pews and banners in the aisles, lanterns in the aisles and over the vault, the doors into
     * the keep and the treasury, and the places of the guards.
     */
    private static void hallInside(Structure s, Plan p) {
        int c = p.c;
        int xw = c - p.hw;
        int xe = c + p.hw;
        int aww = xw - p.aw - 1;
        int awe = xe + p.aw + 1;
        int z1 = p.hz1;
        int z2 = p.hz2;
        int[] ribs = ribs(p);
        int[] bays = bays(p);
        Gothic.paving(s, xw + 1, z1 + 1, xe - 1, z2 - 1, 0);
        Gothic.paving(s, aww + 1, z1 + 1, xw - 1, z2 - 1, 0);
        Gothic.paving(s, xe + 1, z1 + 1, awe - 1, z2 - 1, 0);
        int crown = Gothic.vault(s, xw + 1, z1 + 1, xe - 1, z2 - 1, p.vaultY, false, 4, z1 + 1);
        for (int zb : bays) {
            for (int y = p.vaultY; y <= p.clerY + p.clerH; y++) {
                put(s, xw + 1, y, zb, AIR);
                put(s, xe - 1, y, zb, AIR);
            }
        }
        for (int i = 1; i < ribs.length - 1; i++) {
            box(s, xw + 1, 1, ribs[i], xw + 1, p.vaultY - 1, ribs[i], Gothic.TRACERY_WALL);
            box(s, xe - 1, 1, ribs[i], xe - 1, p.vaultY - 1, ribs[i], Gothic.TRACERY_WALL);
        }
        // the dais and the canopy of the throne
        box(s, c - 2, 0, z1 + 1, c + 2, 0, c + 1, Gothic.TRIM);
        put(s, c - 1, 1, c, Kit.stairs(Gothic.TRIM_STAIRS, "east"));
        put(s, c + 1, 1, c, Kit.stairs(Gothic.TRIM_STAIRS, "west"));
        for (int x = c - 1; x <= c + 1; x += 2) {
            box(s, x, 1, c - 1, x, 3, c - 1, Gothic.STAND);
        }
        box(s, c - 1, 4, c - 1, c + 1, 4, c, Kit.topSlab(Gothic.TRACERY_SLAB));
        box(s, c - 1, 5, c - 1, c + 1, 5, c - 1, Kit.slab(Gothic.TRACERY_SLAB));
        put(s, c - 1, 5, c, Kit.stairs(Gothic.TRACERY_STAIRS, "east"));
        put(s, c + 1, 5, c, Kit.stairs(Gothic.TRACERY_STAIRS, "west"));
        put(s, c, 5, c, Gothic.CARVED);
        Gothic.pinnacle(s, c, 6, c, 2);
        // the cloth of the kingdom on the wall behind it, banners
        int cloth = Math.min(2, p.hw - 2);
        box(s, c - cloth, 2, z1, c + cloth, p.vaultY - 1, z1, Gothic.CLOTH);
        put(s, c, p.vaultY - 2, z1 + 1, Kit.wallBanner("red", "south"));
        // the runner from the door to the dais
        box(s, c, 1, c + 1, c, 1, z2 - 1, Gothic.CLOTH_CARPET);
        if (!p.small()) {
            box(s, c - 1, 1, c + 2, c + 1, 1, z2 - 1, Gothic.CLOTH_CARPET);
        }
        // light: coronas from the vault, candelabra along the nave, lanterns in the aisles and over the vault
        for (int zb : bays) {
            Gothic.corona(s, c, zb, crown - 1, crown - p.vaultY);
            Gothic.candelabrum(s, xw + 1, 1, zb);
            Gothic.candelabrum(s, xe - 1, 1, zb);
            put(s, c, crown + 1, zb, Kit.lantern());
            Gothic.pew(s, aww + 1, zb - 1, aww + 1, zb + 1, 1, "east");
            Gothic.pew(s, awe - 1, zb - 1, awe - 1, zb + 1, 1, "west");
        }
        for (int zr : ribs) {
            put(s, aww + 1, p.aisleTop + 1, zr, Kit.hangingLantern());
            put(s, awe - 1, p.aisleTop + 1, zr, Kit.hangingLantern());
            if (zr > z1 + 1 && zr < z2 - 1) {
                put(s, aww + 1, 3, zr, Kit.wallBanner("red", "east"));
                put(s, awe - 1, 3, zr, Kit.wallBanner("red", "west"));
            }
        }
        for (int zb : bays) {
            put(s, xw + 1, p.aisleTop + 3, zb, Kit.wallBanner("red", "east"));
            put(s, xe - 1, p.aisleTop + 3, zb, Kit.wallBanner("red", "west"));
        }
        put(s, c - 2, p.vaultY - 1, z2 - 1, Kit.wallBanner("red", "north"));
        put(s, c + 2, p.vaultY - 1, z2 - 1, Kit.wallBanner("red", "north"));
        // doors behind the dais: into the keep, and in the larger castles into the treasury
        int dx = Math.min(3, p.hw - 1);
        s.door(c - dx, 1, z1, Gothic.DOOR, "north", "left");
        put(s, c - dx, 4, z1 + 1, Kit.wallBanner("red", "south"));
        if (!p.small()) {
            s.door(c + dx, 1, z1, "minecraft:iron_door", "north", "left");
            p.point(TREASURY, c + dx, 1, z1 + 1);
        } else {
            put(s, c + dx, 4, z1 + 1, Kit.wallBanner("red", "south"));
        }
        p.point(HALL, c - 2, 1, c + 3);
        p.point(HALL, c + 2, 1, c + 3);
        p.point(HALL, c - 2, 1, c + 1);
        p.point(HALL, c + 2, 1, c + 1);
        p.point(PALACE_DOOR, c - 1, 1, z2 - 1);
        p.point(PALACE_DOOR, c + 1, 1, z2 - 1);
    }

    // ---------------------------------------------------------------- the ranges

    /**
     * The two low ranges beside the keep along the back of the curtain: a ground floor and a room under a steep roof
     * whose ridge runs from the curtain to the keep, a parapet gable against the curtain, dormers on the slope that looks
     * into the yard, and a door, lancets and a lantern in the wall to the yard.
     */
    private static void ranges(Structure s, Plan p) {
        range(s, p, p.in1, p.kx1 - 1, true);
        range(s, p, p.kx2 + 1, p.in2, false);
    }

    /**
     * One range over the cells x1..x2 from the north curtain to its south wall: the west one with its parapet gable at
     * {@code x1}, the east one at {@code x2}; the other end is open against the keep.
     */
    private static void range(Structure s, Plan p, int x1, int x2, boolean west) {
        int z1 = p.kz1;
        int z2 = p.rz2;
        int e = p.rEaves;
        box(s, x1, 0, z1, x2, 0, z2, Gothic.ROUGH);
        for (int y = 1; y < e; y++) {
            s.walls(x1, y, z1, x2, y, z2, y == 1 ? Gothic.ROUGH : Gothic.WALL);
        }
        box(s, x1 + 1, 1, z1 + 1, x2 - 1, e + (z2 - z1) + 2, z2 - 1, AIR);
        box(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, Gothic.PLANKS);
        box(s, x1 + 1, p.rFloor, z1 + 1, x2 - 1, p.rFloor, z2 - 1, Gothic.PLANKS);
        Gothic.steepRoof(s, x1, z1, x2, z2, e, true, 2, west ? Gothic.PARAPET : Gothic.OPEN, west ? Gothic.OPEN : Gothic.PARAPET);
        // dormers on the slope to the yard, lancets and the door in its wall
        int outer = west ? x1 : x2;
        int inner = west ? x2 : x1;
        int dir = west ? 1 : -1;
        for (int x = outer + 2 * dir; west ? x <= inner - 1 : x >= inner + 1; x += 3 * dir) {
            Gothic.dormer(s, x1, z1, x2, z2, e, true, 2, x, true, 2);
        }
        int door = outer + 4 * dir;
        s.door(door, 1, z2, Gothic.DOOR, "north", "left");
        put(s, door, 3, z2 + 1, Kit.upside(Gothic.TRIM_STAIRS, "north"));
        lanternPost(s, door + 2 * dir, 1, z2 + 1);
        if (!p.small()) {
            Gothic.lancet(s, outer + 2 * dir, 2, z2, "south", 1, 2, Gothic.AZURE, Gothic.CRIMSON);
            for (int x = door + 4 * dir; west ? x <= inner - 1 : x >= inner + 1; x += 3 * dir) {
                Gothic.lancet(s, x, 2, z2, "south", 1, 2, Gothic.AZURE, Gothic.CRIMSON);
            }
        }
        // the gable against the curtain shows over its parapet: a lancet in it
        Gothic.lancet(s, outer, e + 2, (z1 + z2) / 2, west ? "west" : "east", 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
    }

    /**
     * The west range: the kitchen with its chimneypiece and the stack through the roof, its stoves, a cauldron, a table,
     * sacks and crates and the one barrel; over it the store (small), the servants' hall (medium) or the library (large).
     */
    private static void westRange(Structure s, Plan p) {
        int x1 = p.in1 + 1;
        int x2 = p.kx1 - 2;
        int z1 = p.kz1 + 1;
        int z2 = p.rz2 - 1;
        int up = p.rFloor + 1;
        int e = p.rEaves;
        int mx = (x1 + x2) / 2;
        // the hearth on the north wall and its stack through the roof
        int hx = x1 + 1;
        Gothic.fireplace(s, hx, 1, p.kz1, "south", p.rFloor - 1);
        box(s, hx, up, z1, hx, e + 2, z1, Gothic.WALL);
        box(s, hx, e + 3, z1, hx, e + 6, z1, Kit.CHIMNEY);
        put(s, hx, e + 7, z1, Kit.CHIMNEY_POT);
        // stoves and a cauldron along the north wall
        put(s, x1 + 3, 1, z1, "minecraft:smoker[facing=south,lit=false]");
        put(s, x1 + 4, 1, z1, "minecraft:furnace[facing=south,lit=false]");
        // a table with a bench, the cauldron, the stores along the east wall, the one barrel
        Furnish.longTable(s, x1 + 2, z2 - 1, x2 - 1, z2 - 1, 1, Gothic.WOOD);
        Furnish.bench(s, x1 + 2, z2 - 2, x2 - 1, z2 - 2, 1, "south", Gothic.WOOD);
        put(s, x1 + 3, 2, z2 - 1, Furnish.candles(2));
        put(s, x2, 1, z1 + 1, "minecraft:water_cauldron[level=3]");
        Furnish.sack(s, x2, 1, z1 + 2, true);
        Furnish.crate(s, x2, 2, z1 + 2, Gothic.WOOD);
        put(s, x2, 1, z1 + 3, Kit.barrel());
        // the ladder up and light
        for (int y = 1; y <= p.rFloor; y++) {
            put(s, x1, y, z2, Kit.ladder("north"));
        }
        put(s, mx, p.rFloor - 1, z1 + 2, Kit.hangingLantern());
        // the room over the kitchen
        if (p.small()) {
            for (int x = x1 + 2; x <= x2; x++) {
                Furnish.crate(s, x, up, z1, Gothic.WOOD);
                Furnish.sack(s, x, up, z1 + 1, x % 2 == 0);
            }
        } else if (p.large()) {
            Gothic.bookcase(s, x1 + 4, up, z1, "south", 2);
            Gothic.bookcase(s, x2 - 1, up, z1, "south", 2);
            Furnish.desk(s, x1 + 4, up, z2 - 1, "south", Gothic.WOOD);
            Furnish.desk(s, x2 - 2, up, z2 - 1, "south", Gothic.WOOD);
            Furnish.rug(s, x1 + 2, z1 + 1, x2 - 1, z1 + 2, up, Gothic.CLOTH_CARPET);
        } else {
            Furnish.longTable(s, x1 + 2, z1 + 1, x2 - 1, z1 + 1, up, Gothic.WOOD);
            Furnish.bench(s, x1 + 2, z1 + 2, x2 - 1, z1 + 2, up, "north", Gothic.WOOD);
            put(s, x1 + 3, up + 1, z1 + 1, Furnish.candles(3));
        }
        hang(s, mx, e + 2, (z1 + z2) / 2, Kit.hangingLantern());
    }

    /**
     * The east range: in the small castle the treasury with the armoury over it, in the larger ones the armoury with a
     * hall for the knights over it.
     */
    private static void eastRange(Structure s, Plan p) {
        int x1 = p.kx2 + 2;
        int x2 = p.in2 - 1;
        int z1 = p.kz1 + 1;
        int z2 = p.rz2 - 1;
        int up = p.rFloor + 1;
        int mx = (x1 + x2) / 2;
        for (int y = 1; y <= p.rFloor; y++) {
            put(s, x2, y, z2, Kit.ladder("north"));
        }
        if (p.small()) {
            treasuryRoom(s, x1, z1, x2 - 1, z2 - 1, p.rFloor - 1);
            p.point(TREASURY, p.in2 - 4, 1, p.rz2 + 1);
            armoury(s, x1, z1, x2, z2, up);
        } else {
            armoury(s, x1, z1, x2, z2, 1);
            Furnish.longTable(s, x1, z1 + 1, x2 - 2, z1 + 1, up, Gothic.WOOD);
            Furnish.bench(s, x1, z1 + 2, x2 - 2, z1 + 2, up, "north", Gothic.WOOD);
            put(s, x1 + 1, up + 1, z1 + 1, Furnish.candles(3));
            put(s, x2 - 3, up + 1, z1 + 1, Furnish.candles(2));
            put(s, x2, up + 2, z1 + 1, Kit.wallBanner("red", "west"));
        }
        put(s, mx, p.rFloor - 1, (z1 + z2) / 2, Kit.hangingLantern());
        hang(s, mx, p.rEaves + 2, (z1 + z2) / 2, Kit.hangingLantern());
    }

    /**
     * An armoury on layer {@code y} over the cells x1..x2, z1..z2: racks of weapons along the north wall, an anvil, crates
     * of arrows, a table and a banner of the kingdom.
     */
    private static void armoury(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1; x <= x2 - 1; x += 2) {
            Furnish.weaponRack(s, x, y, z1, "south", Gothic.WOOD);
        }
        put(s, x1, y, z2, "minecraft:anvil[facing=east]");
        Furnish.crate(s, x1, y, z2 - 1, Gothic.WOOD);
        Furnish.table(s, (x1 + x2) / 2, y, (z1 + z2) / 2, Gothic.WOOD);
        Furnish.chair(s, (x1 + x2) / 2 + 1, y, (z1 + z2) / 2, "west", Gothic.WOOD);
        put(s, x2, y + 2, (z1 + z2) / 2, Kit.wallBanner("red", "west"));
    }

    /**
     * A treasury over the cells x1..x2, z1..z2 from the floor at layer 0: two chests and gold along the north wall behind a
     * grille of iron bars with an iron door, a counting desk with candles, lanterns.
     */
    private static void treasuryRoom(Structure s, int x1, int z1, int x2, int z2, int ceiling) {
        Furnish.chest(s, x1, 1, z1, "south");
        Furnish.chest(s, x2, 1, z1, "south");
        for (int x = x1 + 1; x < x2; x++) {
            put(s, x, 1, z1, x % 2 == 0 ? "minecraft:gold_block" : "minecraft:raw_gold_block");
        }
        put(s, x1 + 1, 2, z1, "minecraft:gold_block");
        box(s, x1, 1, z1 + 1, x2, 3, z1 + 1, Gothic.IRON);
        s.door((x1 + x2) / 2, 1, z1 + 1, "minecraft:iron_door", "north", "left");
        Furnish.desk(s, x1, 1, z2, "east", Gothic.WOOD);
        put(s, x2, 1, z2 - 1, Kit.topSlab(Gothic.WOOD_SLAB));
        put(s, x2, 2, z2 - 1, Furnish.candles(3));
        hang(s, x1 + 1, ceiling, z1 + 2, Kit.hangingLantern());
    }

    // ---------------------------------------------------------------- the keep

    /**
     * The keep: a tall block on the axis behind the hall, the dominant of the castle. Its walls of the wall stone carry
     * string courses where they show over the roofs, lancets in its upper floors and a rose over the hall; a crown of
     * parapet, great pinnacles and gargoyles, and in it the needle spire among small pinnacles, far over every other point
     * of the castle.
     */
    private static void keep(Structure s, Plan p) {
        int x1 = p.kx1;
        int x2 = p.kx2;
        int z1 = p.kz1;
        int z2 = p.hz1;
        int roof = p.keepTop - 1;
        int cx = p.c;
        int cz = p.kcz();
        box(s, x1, 0, z1, x2, 0, z2, Gothic.ROUGH);
        for (int y = 1; y <= roof; y++) {
            s.walls(x1, y, z1, x2, y, z2, y == 1 ? Gothic.ROUGH : Gothic.WALL);
        }
        box(s, x1 + 1, 1, z1 + 1, x2 - 1, roof - 1, z2 - 1, AIR);
        box(s, x1 + 1, roof, z1 + 1, x2 - 1, roof, z2 - 1, Gothic.WALL);
        for (int fl : new int[]{p.kf1, p.kf2, p.kf3}) {
            box(s, x1 + 1, fl, z1 + 1, x2 - 1, fl, z2 - 1, Gothic.PLANKS);
        }
        // string courses where the walls show
        for (int y : new int[]{p.kf2, p.kf3}) {
            corniceFree(s, x1, z1 - 1, x2, z1 - 1, y, "north");
            corniceFree(s, x1 - 1, z1, x1 - 1, z2, y, "west");
            corniceFree(s, x2 + 1, z1, x2 + 1, z2, y, "east");
            corniceFree(s, x1, z2 + 1, x2, z2 + 1, y, "south");
        }
        // lancets: the chambers and the chapel on the sides and the back, a lancet into the vestibule from the yards
        int wide = (x2 - x1) >= 12 ? 3 : 2;
        int deep = (z2 - z1) >= 12 ? 3 : 2;
        int chH = Math.max(1, p.kf3 - p.kf2 - 3);
        int topH = Math.max(1, roof - p.kf3 - 3);
        for (int side = -1; side <= 1; side += 2) {
            Gothic.lancet(s, cx + side * wide, p.kf2 + 2, z1, "north", 1, chH, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, cx + side * wide, p.kf3 + 2, z1, "north", 1, topH, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x1, p.kf2 + 2, cz + side * deep, "west", 1, chH, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x2, p.kf2 + 2, cz + side * deep, "east", 1, chH, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x1, p.kf3 + 2, cz + side * deep, "west", 1, topH, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x2, p.kf3 + 2, cz + side * deep, "east", 1, topH, Gothic.VIOLET, Gothic.CRIMSON);
        }
        int yz = (p.rz2 + 1 + z2) / 2;
        Gothic.lancet(s, x1, 2, yz, "west", 1, 3, Gothic.AZURE, Gothic.CRIMSON);
        Gothic.lancet(s, x2, 2, yz, "east", 1, 3, Gothic.AZURE, Gothic.CRIMSON);
        Gothic.rose(s, cx, p.kf3 + 3, z2, "south", 5);
        // the crown, the belfry and the needle
        Gothic.crown(s, x1, z1, x2, z2, p.keepTop, true);
        Gothic.needleSpire(s, cx, p.keepTop, cz, p.spireR, p.spireH);
        put(s, cx, p.keepTop, cz, Kit.lantern());
        if (z2 - z1 > 8) {
            for (int ddx = -3; ddx <= 3; ddx += 6) {
                for (int ddz = -3; ddz <= 3; ddz += 6) {
                    Gothic.pinnacle(s, cx + ddx, p.keepTop, cz + ddz, 4);
                }
            }
        }
        put(s, x2 - 1, p.keepTop, z2 - 1, Gothic.SOUL_LANTERN);
        put(s, x2 - 1, p.keepTop, z1 + 1, Gothic.SOUL_LANTERN);
    }

    /**
     * The rooms of the keep: on the ground the vestibule with the ladder that climbs through every floor to the roof (and
     * in the larger castles the treasury beside it), the council room under its ribbed vault, the royal chambers with
     * their bed of state, and the chapel under the rose.
     */
    private static void keepInside(Structure s, Plan p) {
        int x1 = p.kx1 + 1;
        int x2 = p.kx2 - 1;
        int z1 = p.kz1 + 1;
        int z2 = p.hz1 - 1;
        int c = p.c;
        int cz = p.kcz();
        int roof = p.keepTop - 1;
        // the ground floor
        Gothic.paving(s, x1, z1, x2, z2, 0);
        int vx2 = x2;
        if (!p.small()) {
            box(s, c, 1, z1, c, p.kf1 - 1, z2, Gothic.WALL);
            treasuryRoom(s, c + 1, z1, x2, z2, p.kf1 - 1);
            vx2 = c - 1;
        }
        Furnish.bench(s, x1 + 2, z1, vx2 - 1, z1, 1, "south", Gothic.WOOD);
        Gothic.candelabrum(s, vx2, 1, z1);
        put(s, x1 + 1, 3, z1, Kit.wallBanner("red", "south"));
        hang(s, (x1 + vx2) / 2, p.kf1 - 1, cz, Kit.hangingLantern());
        // the council room
        int spring = p.kf2 - 1 - (z2 - z1) / 2;
        int crown = Gothic.vault(s, x1, z1, x2, z2, spring, true, 4, c);
        int y = p.kf1 + 1;
        int half = Math.min(3, (x2 - x1) / 2 - 2);
        Furnish.longTable(s, c - half, cz, c + half, cz, y, Gothic.WOOD);
        Furnish.bench(s, c - half, cz - 1, c + half, cz - 1, y, "south", Gothic.WOOD);
        Furnish.bench(s, c - half, cz + 1, c + half, cz + 1, y, "north", Gothic.WOOD);
        Furnish.chair(s, c - half - 1, y, cz, "east", Gothic.WOOD);
        Furnish.chair(s, c + half + 1, y, cz, "west", Gothic.WOOD);
        put(s, c - half + 1, y + 1, cz, Furnish.candles(3));
        put(s, c + half - 1, y + 1, cz, Furnish.candles(3));
        Gothic.corona(s, c, cz, crown - 1, Math.max(2, crown - 1 - (p.kf1 + 3)));
        Gothic.fireplace(s, c, y, p.kz1, "south", spring - 1);
        put(s, x1, y + 2, cz, Kit.wallBanner("red", "east"));
        put(s, x2, y + 2, cz, Kit.wallBanner("red", "west"));
        Gothic.candelabrum(s, x2, y, z2);
        Gothic.candelabrum(s, x1 + 1, y, z2);
        // the chambers
        y = p.kf2 + 1;
        Gothic.stateBed(s, x1 + 2, y, cz, "west");
        Gothic.fireplace(s, c + 1, y, p.kz1, "south", p.kf3 - 1);
        Furnish.desk(s, x2, y, z2 - 1, "west", Gothic.WOOD);
        Gothic.bookcase(s, x2, y, cz, "west", 2);
        Furnish.rug(s, x1 + 3, cz - 1, x2 - 2, cz + 1, y, Gothic.CLOTH_CARPET);
        Gothic.candelabrum(s, x1 + 1, y, z2);
        Furnish.chandelier(s, c, cz, p.kf3 - 1, 1);
        // the chapel under the rose
        y = p.kf3 + 1;
        Gothic.altar(s, c, y, z1, "south");
        Gothic.candelabrum(s, c - 2, y, z1);
        Gothic.candelabrum(s, c + 2, y, z1);
        for (int z = cz; z <= z2 - 1; z += 2) {
            Gothic.pew(s, c - 3, z, c - 2, z, y, "north");
            Gothic.pew(s, c + 2, z, c + 3, z, y, "north");
        }
        Furnish.chandelier(s, c, cz, roof - 1, 1);
        // the ladder through every floor to the roof
        for (int yy = 1; yy <= roof; yy++) {
            put(s, x1, yy, z1, Kit.ladder("south"));
        }
    }

    // ---------------------------------------------------------------- the court

    /**
     * The court: a checkered path from the gate to the portal, lantern posts, and in the larger castles grass beds with
     * yews (and in the large one a well-head).
     */
    private static void court(Structure s, Plan p) {
        int c = p.c;
        int z1 = p.hz2 + 2;
        int z2 = p.gz1 - 1;
        Gothic.paving(s, c - 1, z1, c + 1, z2, 0);
        int lz = Math.min(z2, z1 + 1);
        lanternPost(s, c - 5, 1, lz);
        lanternPost(s, c + 5, 1, lz);
        if (!p.small()) {
            int gx = p.large() ? 6 : 4;
            for (int side = -1; side <= 1; side += 2) {
                int px1 = c + side * (p.hw + 3);
                int px2 = c + side * (p.hw + 3 + gx);
                box(s, px1, 0, z1, px2, 0, z2 - 1, GRASS);
                int yx = (px1 + px2) / 2;
                int yz = (z1 + z2 - 1) / 2;
                if (p.large() && side < 0) {
                    well(s, yx + 1, yz);
                    yew(s, yx - 2, yz, 5);
                } else {
                    yew(s, yx, yz, 6);
                }
            }
        }
    }

    /** A well-head: a ring of tracery stone round a cauldron of water, two posts with a beam, a lantern on a chain. */
    private static void well(Structure s, int x, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                put(s, x + dx, 0, z + dz, Gothic.ROUGH);
                if (dx != 0 || dz != 0) {
                    put(s, x + dx, 1, z + dz, Gothic.TRACERY);
                }
            }
        }
        put(s, x, 1, z, "minecraft:water_cauldron[level=3]");
        for (int dx = -1; dx <= 1; dx += 2) {
            box(s, x + dx, 2, z, x + dx, 3, z, Gothic.TRACERY_WALL);
        }
        box(s, x - 1, 4, z, x + 1, 4, z, Kit.slab(Gothic.TRACERY_SLAB));
        put(s, x, 3, z, Kit.chain());
        put(s, x, 2, z, Gothic.SOUL_LANTERN_HANGING);
    }

    // ---------------------------------------------------------------- the walks

    /**
     * The places of the guards on the walks: two on each side of the curtain.
     */
    private static void walkPoints(Plan p) {
        int y = p.f + 1;
        int wx = p.o + 1;
        int ex = p.n - 2 - p.o;
        int nz = p.o + 1;
        int sz = p.n - 2 - p.o;
        p.point(WALK, wx, y, p.kz1 + 4);
        p.point(WALK, wx, y, p.hz2 - 2);
        p.point(WALK, ex, y, p.kz1 + 4);
        p.point(WALK, ex, y, p.hz2 - 2);
        p.point(WALK, p.kx1 - 4, y, nz);
        p.point(WALK, p.kx2 + 4, y, nz);
        int wEdge = p.towerX(p.west, -1) + p.west[0];
        int eEdge = p.towerX(p.east, 1) - p.east[0];
        p.point(WALK, (wEdge + p.c - p.gw) / 2, y, sz);
        p.point(WALK, (eEdge + p.c + p.gw) / 2, y, sz);
    }
}
