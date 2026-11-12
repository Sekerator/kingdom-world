package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle of the style "southern": a palazzo-fortress of the Mediterranean south, a square palace that is its own
 * fortress, drawn with the shapes of {@link Southern}.
 *
 * <p>Outside it reads as one block: the outer walls of its ranges are a base of dressed stone with a battered foot, a band
 * of trim at the upper floor and whitewashed render above with stone quoins, windows with shutters and balconies on corbels
 * with iron railings, crowned all round by machicolations (a parapet thrust out on corbels) with swallowtail merlons, a walk
 * behind them and the low roofs of red tiles rising behind the walk. Few towers, each of its own size and height: at the
 * back a strong square one under a terrace behind machicolations (on the small castle a slim one with an open belvedere
 * under a tile pyramid), at the front corner a lower one with that belvedere and pyramid. At the south-west corner of the
 * front rises the keep, the one dominant of the castle, in the manner of the towers of the communal palaces of the south:
 * out of the crowned corner of the block a slender shaft of render between stone quoins, banded at every floor, corbels
 * of trim carrying the open belfry with its bell out to the width of the base, and over it a pyramid of tiles (on the large
 * castle a cupola on a drum rising out of a skirt of tiles). In the middle of the front, where the curtain is low, the
 * gatehouse: a round arch, over it a loggia in the manner of a serliana from which the king shows himself, machicolations
 * on its top.
 *
 * <p>Inside, the palace stands around its courts. Behind the gate the court of honour, paved, with a fountain, lamp posts
 * and citrus trees in pots, its long sides two storeys of loggias on columns with an open stone stair up to the upper
 * loggia and the walk of the curtain. At its head the throne hall, rising over the ranges under its own low gable whose
 * pediments look at the court and the garden: a portal with a pediment, inside two arcades on columns that part the nave
 * from the aisles, a dado of glazed tiles, a coffered ceiling with lanterns in its coffers, the throne's place on a dais under
 * a canopy before a cloth of state. Behind the hall a garden court with a fountain, cypresses and a balcony over it.
 * Around, in the ranges: the kitchen, the treasury behind iron bars whose door opens from the hall, the council room, the
 * chambers with beds of state (no beds of the game: this is no home), a chapel, a library, the armoury, guard rooms and
 * stores; every room has a floor, a ceiling and light. The small castle keeps the idea in less room: its court lies between
 * the keep and one loggia, its fountain aside from the path, and the north range holds the armoury and the council room.
 *
 * <p>One program drawn in three sizes: small 31 by 31, medium 41 by 41 and large 47 by 47. The size gives the plan (the
 * width of the ranges, the depth of the hall and of the courts), the towers and the heights; the idea stays the same. The
 * throne stands in the middle of the ground on the street layer at the north end of its hall, facing south; the castle
 * leaves it and the ground under it out. Layer 0 replaces the ground, layer 1 is level with the street.
 */
final class SouthernCastle {
    private static final String AIR = Structure.AIR;
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String RENDER = Southern.RENDER;
    private static final String STONE = Southern.STONE;
    private static final String STONE_STAIRS = Southern.STONE_STAIRS;
    private static final String PAVING = Southern.PAVING;
    private static final String TRIM = Southern.TRIM;
    private static final String TRIM_STAIRS = Southern.TRIM_STAIRS;
    private static final String TRIM_SLAB = Southern.TRIM_SLAB;
    private static final String CARVED = Southern.CARVED;
    private static final String COTTO = Southern.COTTO;
    private static final String PLANKS = Southern.PLANKS;
    private static final String BEAM = Southern.BEAM;
    private static final String DOOR = Southern.DOOR;
    private static final String IRON = Southern.IRON;
    private static final String WOOD = Southern.WOOD;
    private static final String IRON_DOOR = "minecraft:iron_door";
    private static final String LEAVES_GATE = "minecraft:oak_trapdoor";

    /** The kinds of the tops of the towers: a terrace behind machicolations, an open belvedere under a pyramid of tiles. */
    private static final int TERRACE = 0;
    private static final int ROOFED = 1;

    /** The three sizes of the castle. */
    enum Size {
        SMALL, MEDIUM, LARGE
    }

    private SouthernCastle() {
    }

    /**
     * The three palazzo-fortresses: small, medium and large, with their named places.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(building(Size.SMALL, "southern_castle_small", "minecraft:smooth_sandstone"));
        list.add(building(Size.MEDIUM, "southern_castle_medium", "minecraft:smooth_sandstone"));
        list.add(building(Size.LARGE, "southern_castle_large", "minecraft:gold_block"));
        return list;
    }

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
    private static final int WALL = 3;
    private static final int TREASURY = 4;

    // ---------------------------------------------------------------- the plan of a size

    /**
     * The numbers of a castle of a size: its block, its ranges and courts, the hall, the keep, the gate and the towers.
     */
    private static final class Plan {
        final Size size;
        /** The ground (square), the height of the box and its middle (the cell of the throne). */
        final int n;
        final int height;
        final int c;
        /** The outer faces of the block: from {@code o} to {@code far} both ways. */
        final int o = 3;
        final int far;
        /** The inner walls of the west and east ranges (x) and of the north range (z). */
        final int wIn;
        final int eIn;
        final int nIn;
        /** The north and south walls of the throne hall and the top course of its walls. */
        final int hz1;
        final int hz2;
        final int htop;
        /** The inner face of the south curtain (two thick, to {@code far}). */
        final int sIn;
        /** The top course of the ranges and of the curtain; one walks on the layer above. */
        final int top = 11;
        final int ct = 6;
        /** Whether the west side of the court is a loggia (not in the small castle) and whether there is a garden court. */
        final boolean wLoggia;
        final boolean garden;
        /** The span of the arches of the court loggias. */
        final int span;
        /** The arcades of the hall: their distance from the middle, length, span and height. */
        final int ax;
        final int hallLen;
        final int hallSpan;
        final int hallArch;
        /** The keep: the north-west corner and width of its base, the top course of its shaft, and whether a dome crowns it. */
        final int kx;
        final int kz;
        final int kw;
        final int ktop;
        final boolean dome;
        /** The gatehouse: its width, its inner and outer face and the course of its terrace. */
        final int gw;
        final int gz1;
        final int gz2;
        final int gtop;
        /** The other towers: {x1, z1, width along x, width along z, top course, kind}. */
        final int[][] towers;
        /** Named places: {kind, x, y, z}. */
        final List<int[]> points = new ArrayList<>();

        Plan(Size size) {
            this.size = size;
            switch (size) {
                case SMALL -> {
                    n = 31;
                    height = 40;
                    wIn = 8;
                    eIn = 22;
                    nIn = 13;
                    hz1 = 13;
                    hz2 = 20;
                    htop = 13;
                    sIn = 26;
                    wLoggia = false;
                    garden = false;
                    span = 4;
                    ax = 3;
                    hallLen = 7;
                    hallSpan = 2;
                    hallArch = 6;
                    kx = 2;
                    kz = 22;
                    kw = 7;
                    ktop = 28;
                    dome = false;
                    gw = 7;
                    gz1 = 25;
                    gz2 = 29;
                    gtop = 12;
                    towers = new int[][]{{22, 2, 7, 7, 15, ROOFED}};
                }
                case MEDIUM -> {
                    n = 41;
                    height = 44;
                    wIn = 11;
                    eIn = 29;
                    nIn = 10;
                    hz1 = 18;
                    hz2 = 27;
                    htop = 15;
                    sIn = 36;
                    wLoggia = true;
                    garden = true;
                    span = 3;
                    ax = 4;
                    hallLen = 9;
                    hallSpan = 3;
                    hallArch = 7;
                    kx = 2;
                    kz = 32;
                    kw = 7;
                    ktop = 32;
                    dome = false;
                    gw = 9;
                    gz1 = 35;
                    gz2 = 39;
                    gtop = 13;
                    towers = new int[][]{{30, 2, 9, 9, 16, TERRACE}, {32, 32, 7, 7, 15, ROOFED}};
                }
                default -> {
                    n = 47;
                    height = 48;
                    wIn = 12;
                    eIn = 34;
                    nIn = 11;
                    hz1 = 21;
                    hz2 = 31;
                    htop = 16;
                    sIn = 42;
                    wLoggia = true;
                    garden = true;
                    span = 4;
                    ax = 5;
                    hallLen = 11;
                    hallSpan = 4;
                    hallArch = 8;
                    kx = 1;
                    kz = 37;
                    kw = 9;
                    ktop = 32;
                    dome = true;
                    gw = 11;
                    gz1 = 41;
                    gz2 = 45;
                    gtop = 14;
                    towers = new int[][]{{35, 2, 10, 10, 17, TERRACE}, {37, 37, 9, 9, 16, ROOFED}};
                }
            }
            c = (n - 1) / 2;
            far = n - 1 - o;
        }

        boolean small() {
            return size == Size.SMALL;
        }

        boolean large() {
            return size == Size.LARGE;
        }

        /** The west and east edges of the court: the arcades of the loggias or the faces of the keep. */
        int courtX1() {
            return wIn + 1;
        }

        int courtX2() {
            return eIn - 1;
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

    /** A block only where nothing stands yet. */
    private static void add(Structure s, int x, int y, int z, String state) {
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

    /** A lantern hanging on a chain from the first solid block above it. */
    private static void hang(Structure s, int x, int y, int z) {
        put(s, x, y, z, Kit.hangingLantern());
        for (int yy = y + 1; yy < s.sizeY && !s.solid(x, yy, z); yy++) {
            s.set(x, yy, z, Kit.chain());
        }
    }

    /** Whether a column lies outside the block of the castle, on the ground around it. */
    private static boolean outside(Plan p, int x, int z) {
        return x < p.o || x > p.far || z < p.o || z > p.far;
    }

    /**
     * Fills the hollow under a roof over x1..x2, z1..z2 from layer {@code y} up to the roof with planks, so that no dark
     * room is left under the tiles.
     */
    private static void attic(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                int roof = -1;
                for (int yy = y; yy < Math.min(s.sizeY, y + 8); yy++) {
                    if (s.solid(x, yy, z)) {
                        roof = yy;
                        break;
                    }
                }
                for (int yy = y; yy < roof; yy++) {
                    put(s, x, yy, z, PLANKS);
                }
            }
        }
    }

    /**
     * Fills the inside of a pyramid of rings over the eaves x1..x2, z1..z2 from layer {@code y}: every layer within the ring
     * of that layer becomes the block of the roof, so that the pyramid is solid.
     */
    private static void fillPyramid(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int ring = 1; x2 - ring - (x1 + ring) >= 0 && z2 - ring - (z1 + ring) >= 0; ring++) {
            box(s, x1 + ring, y + ring - 1, z1 + ring, x2 - ring, y + ring - 1, z2 - ring, Southern.TILES.block());
        }
    }

    // ---------------------------------------------------------------- the castle

    /**
     * Draws the castle of a plan: the ground, the block of the ranges and the curtain, the hall, the loggias and courts,
     * the roofs, the crowns, the towers, the keep and the gate, the windows, the hall and the rooms inside, the trees.
     */
    private static Structure castle(Plan p) {
        Structure s = new Structure(p.n, p.height, p.n);
        ground(s, p);
        block(s, p);
        hallShell(s, p);
        loggias(s, p);
        court(s, p);
        garden(s, p);
        roofs(s, p);
        crowns(s, p);
        for (int[] t : p.towers) {
            tower(s, p, t);
        }
        keep(s, p);
        gate(s, p);
        hallInside(s, p);
        rooms(s, p);
        int mz = (p.hz1 + p.hz2) / 2;
        balcony(s, mz, p.o, "west");
        balcony(s, mz, p.far, "east");
        balcony(s, p.c, p.o, "north");
        windows(s, p);
        outside(s, p);
        clearDoors(s);
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
     * The ground: grass round the castle, a paved road from the street to the gate.
     */
    private static void ground(Structure s, Plan p) {
        box(s, 0, 0, 0, p.n - 1, 0, p.n - 1, GRASS);
        int r = p.large() ? 2 : 1;
        box(s, p.c - r, 0, p.gz2, p.c + r, 0, p.n - 1, PAVING);
        box(s, p.c - r - 1, 0, p.n - 1, p.c + r + 1, 0, p.n - 1, PAVING);
    }

    // ---------------------------------------------------------------- the block

    /** The outer wall at a layer: stone to the upper floor, a band of trim, render with quoins, a stone top course. */
    private static String outer(Plan p, int y, boolean corner) {
        if (y <= p.ct - 1 || y == p.top) {
            return STONE;
        }
        if (y == p.ct) {
            return TRIM;
        }
        return corner ? STONE : RENDER;
    }

    /** An inner wall at a layer: render on a stone plinth with a band of trim at the upper floor, stone at the corners. */
    private static String inner(Plan p, int y, boolean corner) {
        if (y == 1 || corner) {
            return STONE;
        }
        return y == p.ct ? TRIM : RENDER;
    }

    /**
     * The block of the palace: the footing, the outer walls of the west, north and east ranges with their battered foot,
     * the south curtain, the inner walls towards the courts, the partitions of the rooms and the floors and ceilings.
     */
    private static void block(Structure s, Plan p) {
        int o = p.o;
        int f = p.far;
        box(s, o - 1, 0, o - 1, f + 1, 0, f + 1, STONE);
        box(s, o + 1, 0, o + 1, f - 1, 0, f - 1, COTTO);
        for (int y = 1; y <= p.top; y++) {
            for (int z = o; z < p.sIn; z++) {
                put(s, o, y, z, outer(p, y, z == o));
                put(s, f, y, z, outer(p, y, z == o));
            }
            for (int x = o; x <= f; x++) {
                put(s, x, y, o, outer(p, y, x == o || x == f));
            }
        }
        // the curtain on the south, two thick, and its top course
        box(s, o, 1, p.sIn, f, p.ct, f, STONE);
        box(s, o, p.ct, p.sIn, f, p.ct, f, TRIM);
        // the battered foot of the outer walls
        for (int x = o - 1; x <= f + 1; x++) {
            add(s, x, 1, o - 1, Kit.stairs(STONE_STAIRS, "south"));
            add(s, x, 1, f + 1, Kit.stairs(STONE_STAIRS, "north"));
        }
        for (int z = o; z <= f; z++) {
            add(s, o - 1, 1, z, Kit.stairs(STONE_STAIRS, "east"));
            add(s, f + 1, 1, z, Kit.stairs(STONE_STAIRS, "west"));
        }
        // inner walls of the ranges along their whole length; the loggias and the hall take their parts later
        for (int y = 1; y <= p.top; y++) {
            for (int z = o + 1; z < p.sIn; z++) {
                put(s, p.wIn, y, z, inner(p, y, false));
                put(s, p.eIn, y, z, inner(p, y, false));
            }
            if (p.garden) {
                for (int x = p.wIn; x <= p.eIn; x++) {
                    put(s, x, y, p.nIn, inner(p, y, x == p.wIn || x == p.eIn));
                }
            }
        }
        // floors and ceilings of the ranges
        int nz = p.garden ? p.nIn - 1 : p.hz1 - 1;
        for (int y : new int[]{p.ct, p.top}) {
            Southern.beamCeiling(s, o + 1, o + 1, p.wIn - 1, p.sIn - 1, y, true);
            Southern.beamCeiling(s, p.eIn + 1, o + 1, f - 1, p.sIn - 1, y, true);
            Southern.beamCeiling(s, p.wIn, o + 1, p.eIn, nz, y, false);
        }
        // partitions of the rooms
        int[] rows = p.garden ? new int[]{p.nIn, p.hz1, p.hz2} : new int[]{p.hz1, p.hz2};
        for (int z : rows) {
            for (int y = 1; y <= p.top; y++) {
                for (int x = o + 1; x < p.wIn; x++) {
                    if (p.garden || z != p.hz2) {
                        put(s, x, y, z, y == p.ct ? TRIM : RENDER);
                    }
                }
                for (int x = p.eIn + 1; x < f; x++) {
                    put(s, x, y, z, y == p.ct ? TRIM : RENDER);
                }
            }
        }
        if (p.garden) {
            for (int y = 1; y <= p.top; y++) {
                for (int z = o + 1; z < p.nIn; z++) {
                    put(s, p.wIn, y, z, y == p.ct ? TRIM : RENDER);
                    put(s, p.eIn, y, z, y == p.ct ? TRIM : RENDER);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the hall

    /**
     * The shell of the throne hall between the west and east ranges: render on a stone plinth with quoins and a band of
     * trim, rising over the roofs of the ranges to its own top course.
     */
    private static void hallShell(Structure s, Plan p) {
        for (int y = 1; y <= p.htop; y++) {
            for (int z = p.hz1; z <= p.hz2; z++) {
                boolean corner = z == p.hz1 || z == p.hz2;
                put(s, p.wIn, y, z, inner(p, y, corner));
                put(s, p.eIn, y, z, inner(p, y, corner));
            }
            for (int x = p.wIn + 1; x < p.eIn; x++) {
                put(s, x, y, p.hz1, inner(p, y, false));
                put(s, x, y, p.hz2, inner(p, y, false));
            }
        }
        box(s, p.wIn + 1, p.htop, p.hz1 + 1, p.eIn - 1, p.htop, p.hz2 - 1, PLANKS);
    }

    // ---------------------------------------------------------------- the loggias and the court

    /**
     * The loggias on the long sides of the court: two storeys of round arches on columns, the upper one with a railing of
     * wrought iron, opening at its south end onto the walk of the curtain; in each an open stair of stone up to it.
     */
    private static void loggias(Structure s, Plan p) {
        int z1 = p.hz2;
        int z2 = p.sIn - 1;
        if (p.wLoggia) {
            int x1 = p.wIn - 2;
            box(s, x1 - 1, 1, z1, x1 - 1, p.top, z2, RENDER);
            box(s, x1 - 1, 1, z1, x1 - 1, 1, z2, STONE);
            box(s, x1 - 1, p.ct, z1, x1 - 1, p.ct, z2, TRIM);
            Southern.loggia(s, x1, z1, p.wIn, z2, p.ct + 1, 4, p.span, "east", true);
            Southern.loggia(s, x1, z1, p.wIn, z2, 1, p.ct - 1, p.span, "east", false);
            box(s, x1, 1, z1, p.wIn - 1, p.top, z1, RENDER);
            loggiaStair(s, p, x1);
            int x2 = p.eIn + 2;
            box(s, x2 + 1, 1, z1, x2 + 1, p.top, z2, RENDER);
            box(s, x2 + 1, 1, z1, x2 + 1, 1, z2, STONE);
            box(s, x2 + 1, p.ct, z1, x2 + 1, p.ct, z2, TRIM);
            Southern.loggia(s, p.eIn, z1, x2, z2, p.ct + 1, 4, p.span, "west", true);
            Southern.loggia(s, p.eIn, z1, x2, z2, 1, p.ct - 1, p.span, "west", false);
            box(s, p.eIn + 1, 1, z1, x2, p.top, z1, RENDER);
            loggiaStair(s, p, x2);
        } else {
            int x2 = p.far - 1;
            Southern.loggia(s, p.eIn, z1, x2, z2, p.ct + 1, 4, p.span, "west", true);
            Southern.loggia(s, p.eIn, z1, x2, z2, 1, p.ct - 1, p.span, "west", false);
            box(s, p.eIn + 1, 1, z1, x2, p.top, z1, RENDER);
            // an open stair in two flights: east along the curtain, then north along the outer wall
            int z = z2;
            for (int k = 1; k <= 4; k++) {
                int x = p.eIn + k;
                put(s, x, k, z, Kit.stairs(STONE_STAIRS, "east"));
                if (k > 1) {
                    box(s, x, 1, z, x, k - 1, z, STONE);
                }
            }
            put(s, x2, 5, z - 1, Kit.stairs(STONE_STAIRS, "north"));
            box(s, x2, 1, z - 1, x2, 4, z - 1, STONE);
            put(s, x2, 6, z - 2, Kit.stairs(STONE_STAIRS, "north"));
            box(s, x2, 1, z - 2, x2, 5, z - 2, STONE);
            put(s, x2, 6, z, AIR);
            put(s, x2, 6, z - 1, AIR);
            put(s, x2 - 1, 7, z - 1, Kit.lantern());
        }
    }

    /**
     * An open stair of stone along the back of a loggia, climbing south from the court floor to the upper loggia; the floor
     * of the upper loggia is open over it.
     */
    private static void loggiaStair(Structure s, Plan p, int x) {
        for (int k = 1; k <= p.ct; k++) {
            int z = p.sIn - 2 - p.ct + k;
            put(s, x, k, z, Kit.stairs(STONE_STAIRS, "south"));
            if (k > 1) {
                box(s, x, 1, z, x, k - 1, z, STONE);
            }
            if (k < p.ct) {
                put(s, x, p.ct, z, AIR);
            }
        }
    }

    /**
     * The court of honour between the hall and the gate: paving with a border of trim and paths of cotto, a fountain with
     * lamp posts round it and citrus trees in pots in the corners; in the narrow court of the small castle the fountain
     * stands aside from the path between the gate and the hall.
     */
    private static void court(Structure s, Plan p) {
        int x1 = p.courtX1();
        int x2 = p.courtX2();
        int z1 = p.hz2 + 1;
        int z2 = p.sIn - 1;
        box(s, x1, 1, z1, x2, p.top + 4, z2, AIR);
        if (p.small()) {
            box(s, x1, 0, z1, x2, 0, z2, PAVING);
            box(s, p.c - 1, 0, z1, p.c + 1, 0, z2, COTTO);
            int fx = p.c + 3;
            int fz = (z1 + z2) / 2;
            Kit.disc(s, fx, 0, fz, 7, COTTO);
            Southern.fountain(s, fx, 1, fz, 5);
            Southern.citrus(s, x1 + 2, 1, z2 - 1, true);
            Southern.lampPost(s, x1 + 2, 1, z1);
            Southern.lampPost(s, x2, 1, z1);
            Southern.stoneBench(s, x1 + 1, z1 + 1, x1 + 1, z1 + 2, 1, "east");
            return;
        }
        Southern.courtyard(s, x1, z1, x2, z2, 1, 5);
    }

    /**
     * The garden court behind the hall: grass with paths of cotto, a fountain, cypresses, citrus trees and benches, a wall
     * fountain on the back of the hall.
     */
    private static void garden(Structure s, Plan p) {
        if (!p.garden) {
            return;
        }
        int x1 = p.wIn + 1;
        int x2 = p.eIn - 1;
        int z1 = p.nIn + 1;
        int z2 = p.hz1 - 1;
        int cz = (z1 + z2) / 2;
        box(s, x1, 1, z1, x2, p.top + 4, z2, AIR);
        box(s, x1, 0, z1, x2, 0, z2, GRASS);
        box(s, p.c - 1, 0, z1, p.c + 1, 0, z2, COTTO);
        box(s, x1, 0, cz, x2, 0, cz, COTTO);
        Kit.disc(s, p.c, 0, cz, 7, COTTO);
        Southern.fountain(s, p.c, 1, cz, 5);
        Southern.cypress(s, x1 + 1, 1, z1 + 1, 9);
        Southern.cypress(s, x2 - 1, 1, z1 + 1, 9);
        Southern.cypress(s, x1 + 1, 1, z2 - 1, 8);
        Southern.cypress(s, x2 - 1, 1, z2 - 1, 8);
        Southern.citrus(s, p.c - 5, 1, z1 + 1, true);
        Southern.citrus(s, p.c + 5, 1, z1 + 1, true);
        Southern.stoneBench(s, p.c - 5, z2, p.c - 3, z2, 1, "north");
        Southern.stoneBench(s, p.c + 3, z2, p.c + 5, z2, 1, "north");
        Southern.lampPost(s, p.c - 4, 1, cz);
        Southern.lampPost(s, p.c + 4, 1, cz);
        Southern.wallFountain(s, p.c, 1, z2, "north");
    }

    // ---------------------------------------------------------------- roofs and crowns

    /**
     * The roofs of tiles: one low roof over the three ranges with valleys at the inner corners, its gables closed with
     * render, and the higher roof of the hall, a low gable whose pediments look at the court and the garden, each with a
     * cornice and a carved medallion; the hollows under the roofs filled.
     */
    private static void roofs(Structure s, Plan p) {
        int o = p.o;
        int f = p.far;
        int y = p.top + 1;
        int nz = p.garden ? p.nIn + 1 : p.hz1 + 1;
        Southern.lowRoof(s, y, Southern.TILES, TRIM_STAIRS, RENDER,
                new int[]{o + 1, o + 1, f - 1, nz, Southern.RIDGE_X},
                new int[]{o + 1, o + 1, p.wIn + 1, p.sIn, Southern.RIDGE_Z},
                new int[]{p.eIn - 1, o + 1, f - 1, p.sIn, Southern.RIDGE_Z});
        attic(s, o + 1, o + 1, p.wIn - 1, p.sIn - 1, y);
        attic(s, p.eIn + 1, o + 1, f - 1, p.sIn - 1, y);
        attic(s, p.wIn, o + 1, p.eIn, nz - 1, y);
        box(s, p.wIn + 1, y, p.hz1 + 1, p.eIn - 1, p.htop - 1, p.hz2 - 1, AIR);
        // the hall: a low gable with its pediments looking at the court and the garden
        Southern.lowRoof(s, p.htop + 1, Southern.TILES, TRIM_STAIRS, RENDER,
                new int[]{p.wIn - 1, p.hz1 - 1, p.eIn + 1, p.hz2 + 1, Southern.RIDGE_Z});
        attic(s, p.wIn + 1, p.hz1 + 1, p.eIn - 1, p.hz2 - 1, p.htop + 1);
        for (int z : new int[]{p.hz1, p.hz2}) {
            int out = z == p.hz1 ? -1 : 1;
            for (int x = p.wIn; x <= p.eIn; x++) {
                add(s, x, p.htop + 1, z + out, Kit.upside(TRIM_STAIRS, out < 0 ? "south" : "north"));
                put(s, x, p.htop, z, TRIM);
            }
            int my = p.htop + 3;
            put(s, p.c, my, z, CARVED);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                put(s, p.c + d[0], my + d[1], z, TRIM);
            }
        }
    }

    /**
     * The crowns of the walls: on the ranges a parapet thrust out on corbels with swallowtail merlons, a walk behind it on
     * the outer walls in front of the roofs; on the curtain the same, lower; lanterns in the crenels.
     */
    private static void crowns(Structure s, Plan p) {
        int o = p.o;
        int f = p.far;
        int y = p.top + 1;
        crown(s, false, o - 1, p.sIn - 1, o - 1, y, "east");
        crown(s, false, o - 1, p.sIn - 1, f + 1, y, "west");
        crown(s, true, o - 1, f + 1, o - 1, y, "south");
        for (int x : new int[]{o - 1, f + 1}) {
            box(s, x, y, o - 1, x, y + 2, o - 1, STONE);
            box(s, x, y, p.sIn - 1, x, y + 2, p.sIn - 1, STONE);
        }
        int yc = p.ct + 1;
        crown(s, true, o - 1, f + 1, f + 1, yc, "north");
        crown(s, false, p.sIn, f + 1, o - 1, yc, "east");
        crown(s, false, p.sIn, f + 1, f + 1, yc, "west");
        for (int x : new int[]{o - 1, f + 1}) {
            box(s, x, yc, f + 1, x, yc + 2, f + 1, STONE);
        }
    }

    /**
     * A crown on a straight wall whose outer face lies one block inside {@code line}: a parapet of stone on that line at
     * {@code y}, under every second cell two corbels of upside-down stairs of trim (y - 1 and y - 2), swallowtail merlons
     * from {@code y + 1} and lanterns in some crenels.
     *
     * @param toWall the side of the wall from the parapet
     */
    private static void crown(Structure s, boolean alongX, int a1, int a2, int line, int y, String toWall) {
        for (int a = a1; a <= a2; a++) {
            at(s, alongX, a, y, line, STONE);
            if (Math.floorMod(a - a1, 2) == 0) {
                at(s, alongX, a, y - 1, line, Kit.upside(TRIM_STAIRS, toWall));
                at(s, alongX, a, y - 2, line, Kit.upside(TRIM_STAIRS, toWall));
            }
        }
        Southern.merlons(s, alongX ? a1 : line, alongX ? line : a1, alongX ? a2 : line, alongX ? line : a2, y + 1, STONE, STONE_STAIRS, true);
        for (int a = a1 + 2; a <= a2 - 2; a++) {
            int m = Math.min(a - a1, a2 - a);
            if (m % 3 == 2 && Math.floorMod(a - a1, 9) < 3) {
                int x = alongX ? a : line;
                int z = alongX ? line : a;
                if (s.inside(x, y + 1, z) && !s.solid(x, y + 1, z)) {
                    put(s, x, y + 1, z, Kit.lantern());
                }
            }
        }
    }

    // ---------------------------------------------------------------- towers

    /**
     * The body of a square tower of dressed stone on the ground over x1..x2, z1..z2: a footing, a battered foot where it
     * stands on open ground, walls up to the top course {@code top} with a band of trim at the upper floor of the ranges,
     * the inside cleared, floors of planks at the given layers, a floor of cotto on the top course, a lantern under every
     * floor.
     */
    private static void shaft(Structure s, Plan p, int x1, int z1, int x2, int z2, int top, int[] floors) {
        box(s, x1, 0, z1, x2, 0, z2, STONE);
        box(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, PAVING);
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                boolean ring = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1;
                if (!ring || !outside(p, x, z)) {
                    continue;
                }
                put(s, x, 0, z, STONE);
                String toTower = x == x1 - 1 ? "east" : x == x2 + 1 ? "west" : z == z1 - 1 ? "south" : "north";
                boolean corner = (x == x1 - 1 || x == x2 + 1) && (z == z1 - 1 || z == z2 + 1);
                if (!corner) {
                    put(s, x, 1, z, Kit.stairs(STONE_STAIRS, toTower));
                } else {
                    put(s, x, 1, z, AIR);
                }
            }
        }
        for (int y = 1; y <= top; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                    put(s, x, y, z, wall ? (y == p.ct ? TRIM : STONE) : AIR);
                }
            }
        }
        for (int floor : floors) {
            if (floor < top) {
                box(s, x1 + 1, floor, z1 + 1, x2 - 1, floor, z2 - 1, PLANKS);
            }
        }
        box(s, x1 + 1, top, z1 + 1, x2 - 1, top, z2 - 1, COTTO);
        for (int floor : floors) {
            if (floor < top) {
                hang(s, x2 - 1, floor - 1, z2 - 1);
            }
        }
        hang(s, x2 - 1, top - 1, z2 - 1);
    }

    /**
     * A tower at a corner of the block: its body with floors at the levels of the ranges, a ladder through all of them,
     * doors to the walks of the walls that meet it and narrow windows; on its top a terrace behind machicolations with
     * swallowtail merlons, or an open belvedere with an arch on every face under a cornice and a pyramid of tiles.
     */
    private static void tower(Structure s, Plan p, int[] t) {
        int x1 = t[0];
        int z1 = t[1];
        int x2 = x1 + t[2] - 1;
        int z2 = z1 + t[3] - 1;
        int top = t[4];
        boolean roofed = t[5] == ROOFED;
        List<Integer> floors = new ArrayList<>(List.of(p.ct, p.top));
        if (roofed) {
            if (top - 4 > p.top + 2) {
                floors.add(top - 4);
            }
        } else {
            for (int y = p.top + 5; y < top - 2; y += 5) {
                floors.add(y);
            }
        }
        shaft(s, p, x1, z1, x2, z2, top, floors.stream().mapToInt(Integer::intValue).toArray());
        for (int y = 1; y <= top; y++) {
            put(s, x1 + 1, y, z1 + 1, Kit.ladder("south"));
        }
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        // doors to the walks of the ranges (one walks on the layer above the top course) and to the curtain
        int w = p.top + 1;
        if (z1 <= p.o) {
            if (x1 > p.c) {
                s.door(x1, w, p.o, DOOR, "west", "left");
            } else {
                s.door(x2, w, p.o, DOOR, "east", "left");
            }
        }
        if (x2 >= p.far && z1 <= p.o) {
            s.door(p.far, w, z2, DOOR, "south", "left");
        }
        if (x2 >= p.far && z1 > p.c) {
            s.door(p.far, w, z1, DOOR, "north", "left");
            s.door(x1, p.ct + 1, p.sIn, DOOR, "west", "left");
        }
        // narrow windows on the faces that look out
        for (int y = 3; y < top - 1; y += 5) {
            for (String side : new String[]{"north", "south", "west", "east"}) {
                int[] d = Furnish.step(side);
                int wx = d[0] == 0 ? cx : d[0] < 0 ? x1 : x2;
                int wz = d[1] == 0 ? cz : d[1] < 0 ? z1 : z2;
                if (outside(p, wx + d[0], wz + d[1]) && (!roofed || y < top - 4)) {
                    Southern.window(s, wx, y, wz, side, 1, 2, false, false, y < p.ct ? IRON : Southern.GLASS);
                }
            }
        }
        if (!roofed) {
            Southern.machicolations(s, x1, z1, x2, z2, top + 1, STONE, STONE_STAIRS, true);
            put(s, x2 - 1, top + 1, z2 - 1, Kit.lantern());
            put(s, x1 + 2, top + 1, z2 - 1, Kit.lantern());
            return;
        }
        // the belvedere: an arch on every face over a railing, under the cornice and the pyramid
        for (String side : new String[]{"north", "south", "west", "east"}) {
            int[] d = Furnish.step(side);
            int wx = d[0] == 0 ? cx : d[0] < 0 ? x1 : x2;
            int wz = d[1] == 0 ? cz : d[1] < 0 ? z1 : z2;
            Southern.window(s, wx, top - 3, wz, side, 3, 3, false, false, AIR);
            String along = Furnish.left(side);
            int[] a = Furnish.step(along);
            for (int u = -1; u <= 1; u++) {
                put(s, wx + a[0] * u, top - 3, wz + a[1] * u, IRON);
            }
        }
        Southern.cornice(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, top, TRIM_STAIRS);
        int apex = Southern.pyramid(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, top + 1, Southern.TILES);
        fillPyramid(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, top + 1);
        Southern.finial(s, cx, apex, cz);
    }

    /**
     * The keep at the south-west corner, the dominant of the castle, in the manner of the tall towers of the communal
     * palaces of the south: its base is the corner of the block, as high as the ranges and crowned like them, with a walk
     * round the shaft that the walk of the west range runs into; out of it rises a slender shaft of render between quoins
     * of stone, a band of trim at every floor and windows on its faces; at its top corbels of trim carry the floor of the
     * belfry out to the width of the base; the open belfry with its bell, and over it a pyramid of tiles, or on the large
     * castle a cupola on a drum rising out of a low skirt of tiles. A ladder on a pillar climbs from the ground to the
     * belfry; doors open to the loggia or the court, to the walk of the curtain and from the walk of the base into the
     * shaft.
     */
    private static void keep(Structure s, Plan p) {
        int x1 = p.kx;
        int z1 = p.kz;
        int x2 = x1 + p.kw - 1;
        int z2 = z1 + p.kw - 1;
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        int top = p.ktop;
        int base = p.top;
        // the base: the corner of the block, as high as the ranges, in their materials
        box(s, x1, base + 1, z1, x2, p.height - 1, z2, AIR);
        box(s, x1, 0, z1, x2, 0, z2, STONE);
        box(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, PAVING);
        for (int y = 1; y <= base; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                    boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                    put(s, x, y, z, wall ? outer(p, y, corner) : AIR);
                }
            }
        }
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                boolean ring = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1;
                boolean corner = (x == x1 - 1 || x == x2 + 1) && (z == z1 - 1 || z == z2 + 1);
                if (ring && outside(p, x, z)) {
                    put(s, x, 0, z, STONE);
                    put(s, x, 1, z, corner ? AIR : Kit.stairs(STONE_STAIRS, x == x1 - 1 ? "east" : x == x2 + 1 ? "west" : z == z1 - 1 ? "south" : "north"));
                }
            }
        }
        box(s, x1 + 1, p.ct, z1 + 1, x2 - 1, p.ct, z2 - 1, PLANKS);
        box(s, x1 + 1, base, z1 + 1, x2 - 1, base, z2 - 1, PLANKS);
        hang(s, x2 - 1, p.ct - 1, z2 - 1);
        hang(s, x2 - 1, base - 1, z2 - 1);
        // its crown on the faces that look out, continuing the crowns of the walls
        crown(s, false, z1 - 1, z2 + 1, x1 - 1, base + 1, "east");
        crown(s, true, x1 - 1, x2 + 1, z2 + 1, base + 1, "north");
        box(s, x1 - 1, base + 1, z2 + 1, x1 - 1, base + 3, z2 + 1, STONE);
        // the shaft: render between quoins of stone, a band of trim at every floor, windows on its faces
        int sx1 = x1 + 1;
        int sz1 = z1 + 1;
        int sx2 = x2 - 1;
        int sz2 = z2 - 1;
        List<Integer> floors = new ArrayList<>();
        for (int y = base + 5; y < top - 2; y += 5) {
            floors.add(y);
        }
        for (int y = base + 1; y < top; y++) {
            boolean band = floors.contains(y);
            for (int z = sz1; z <= sz2; z++) {
                for (int x = sx1; x <= sx2; x++) {
                    boolean wall = x == sx1 || x == sx2 || z == sz1 || z == sz2;
                    boolean corner = (x == sx1 || x == sx2) && (z == sz1 || z == sz2);
                    put(s, x, y, z, !wall ? AIR : band ? TRIM : corner || y == base + 1 ? STONE : RENDER);
                }
            }
        }
        for (int y : floors) {
            box(s, sx1 + 1, y, sz1 + 1, sx2 - 1, y, sz2 - 1, PLANKS);
            hang(s, sx2 - 1, y - 1, sz2 - 1);
        }
        for (int y = base + 6; y < top - 3; y += 5) {
            for (String side : new String[]{"north", "south", "west", "east"}) {
                int[] d = Furnish.step(side);
                Southern.window(s, d[0] == 0 ? cx : d[0] < 0 ? sx1 : sx2, y, d[1] == 0 ? cz : d[1] < 0 ? sz1 : sz2, side, 1, 2, false, false);
            }
        }
        // the crown of the shaft: corbels carrying the floor of the belfry out to the width of the base
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                boolean ring = x == x1 || x == x2 || z == z1 || z == z2;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                int along = z == z1 || z == z2 ? x - x1 : z - z1;
                if (ring) {
                    put(s, x, top, z, STONE);
                    if (corner || along % 2 == 0) {
                        String in = x == x1 ? "east" : x == x2 ? "west" : z == z1 ? "south" : "north";
                        put(s, x, top - 1, z, Kit.upside(TRIM_STAIRS, in));
                        put(s, x, top - 2, z, Kit.upside(TRIM_STAIRS, in));
                    }
                } else {
                    boolean shaftRing = x == sx1 || x == sx2 || z == sz1 || z == sz2;
                    put(s, x, top, z, shaftRing ? TRIM : PLANKS);
                }
            }
        }
        hang(s, sx2 - 1, top - 1, sz2 - 1);
        // the ladder through the base and the shaft, on a pillar in the base
        int lx = sx1 + 1;
        int lz = sz1 + 1;
        for (int y = 1; y <= base; y++) {
            put(s, lx, y, sz1, STONE);
        }
        for (int y = 1; y <= top; y++) {
            put(s, lx, y, lz, Kit.ladder("south"));
        }
        // the doors: to the loggia or the court, to the walk of the curtain, from the walk of the base into the shaft
        int dz = p.small() ? z1 + 2 : p.sIn - 1;
        s.door(x2, 1, dz, DOOR, "east", "left");
        s.door(x2, p.ct + 1, p.sIn, DOOR, "east", "left");
        s.door(cx, base + 1, sz2, DOOR, "south", "left");
        put(s, cx, base + 3, sz2, TRIM);
        Southern.window(s, x1, 3, cz, "west", 1, 1, false, false, IRON);
        Southern.window(s, x1, p.ct + 2, cz, "west", 1, 2, true, false);
        Southern.window(s, cx, p.ct + 2, z2, "south", 1, 2, true, false);
        put(s, x2, base + 1, z2, Kit.lantern());
        // the belfry over the crown and its pyramid or dome
        int eaves = Southern.belfry(s, x1, z1, top + 1, p.kw);
        if (p.dome) {
            // a cupola narrower than the belfry on a drum with windows, rising out of a low skirt of tiles, lit inside
            Southern.lowRoof(s, eaves, Southern.TILES, null, null, new int[]{x1 - 1, z1 - 1, x2 + 1, z2 + 1, Southern.HIP});
            for (int y = eaves; y <= eaves + 4; y++) {
                Kit.disc(s, cx, y, cz, p.kw - 4, AIR);
            }
            put(s, cx + 1, eaves, cz, Kit.lantern());
            Southern.dome(s, cx, eaves, cz, p.kw - 2, 4);
        } else {
            int apex = Southern.pyramid(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, eaves, Southern.TILES);
            fillPyramid(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, eaves);
            Southern.finial(s, cx, apex, cz);
        }
    }

    // ---------------------------------------------------------------- the gatehouse

    /**
     * The gatehouse in the middle of the front, standing out of the curtain on both sides: a solid foot of stone with the
     * passage through it under a vault, a round arch with a carved keystone on both faces, open leaves of the gate, banners
     * and lanterns; over it the room of the guard, reached from the walks of the curtain, whose front opens in a loggia in
     * the manner of a serliana with a railing; a ladder up to the terrace behind machicolations and swallowtail merlons.
     */
    private static void gate(Structure s, Plan p) {
        int c = p.c;
        int h = p.gw / 2;
        int x1 = c - h;
        int x2 = c + h;
        int z1 = p.gz1;
        int z2 = p.gz2;
        int hw = p.large() ? 2 : 1;
        int floor = p.ct;
        int ceil = p.gtop - 1;
        box(s, x1, 0, z1, x2, 0, z2, STONE);
        box(s, x1, 1, z1, x2, floor - 1, z2, STONE);
        for (int y = floor; y <= p.gtop; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                    put(s, x, y, z, wall ? (y == floor ? TRIM : STONE) : y == floor ? PLANKS : y == p.gtop ? COTTO : y == ceil ? PLANKS : AIR);
                }
            }
        }
        Southern.beamCeiling(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, ceil, true);
        // the battered foot on the open ground before it
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            if (x < c - hw - 1 || x > c + hw + 1) {
                put(s, x, 1, z2 + 1, Kit.stairs(STONE_STAIRS, "north"));
                put(s, x, 0, z2 + 1, STONE);
            }
        }
        for (int z = p.far + 1; z <= z2; z++) {
            put(s, x1 - 1, 1, z, Kit.stairs(STONE_STAIRS, "east"));
            put(s, x2 + 1, 1, z, Kit.stairs(STONE_STAIRS, "west"));
        }
        // the passage and its vault
        box(s, c - hw, 1, z1, c + hw, floor - 1, z2, AIR);
        box(s, c - hw, 0, z1, c + hw, 0, z2 + 1, PAVING);
        for (int z = z1; z <= z2; z++) {
            put(s, c - hw, floor - 1, z, Kit.upside(TRIM_STAIRS, "west"));
            put(s, c + hw, floor - 1, z, Kit.upside(TRIM_STAIRS, "east"));
            if (hw == 2) {
                put(s, c - 1, floor - 1, z, AIR);
                put(s, c + 1, floor - 1, z, AIR);
                put(s, c - 2, floor - 2, z, Kit.upside(TRIM_STAIRS, "west"));
                put(s, c + 2, floor - 2, z, Kit.upside(TRIM_STAIRS, "east"));
                put(s, c - 2, floor - 1, z, STONE);
                put(s, c + 2, floor - 1, z, STONE);
                put(s, c - 1, floor - 1, z, Kit.upside(TRIM_STAIRS, "west"));
                put(s, c + 1, floor - 1, z, Kit.upside(TRIM_STAIRS, "east"));
            }
        }
        // the arches of both faces: a frame of trim and a carved keystone
        for (int z : new int[]{z1, z2}) {
            for (int y = 1; y < floor; y++) {
                put(s, c - hw - 1, y, z, y == 1 ? STONE : TRIM);
                put(s, c + hw + 1, y, z, y == 1 ? STONE : TRIM);
            }
            for (int x = c - hw - 1; x <= c + hw + 1; x++) {
                put(s, x, floor, z, TRIM);
            }
            put(s, c, floor, z, CARVED);
        }
        hang(s, c, floor - 2, z1 + 1);
        hang(s, c, floor - 2, z2 - 1);
        // the open leaves of the gate against the walls of the passage
        for (int y = 1; y <= floor - 3; y++) {
            put(s, c - hw, y, z2 - 1, Kit.shutter(LEAVES_GATE, "east"));
            put(s, c + hw, y, z2 - 1, Kit.shutter(LEAVES_GATE, "west"));
        }
        // banners and lanterns on the front
        put(s, c - hw - 2, floor - 2, z2 + 1, Kit.wallBanner("red", "south"));
        put(s, c + hw + 2, floor - 2, z2 + 1, Kit.wallBanner("red", "south"));
        Southern.wallLantern(s, c - hw - 1, 3, z2 + 1);
        Southern.wallLantern(s, c + hw + 1, 3, z2 + 1);
        Southern.wallLantern(s, c - hw - 1, 3, z1 - 1);
        Southern.wallLantern(s, c + hw + 1, 3, z1 - 1);
        // the serliana: a middle arch between two columns, flat openings beside them, a railing
        int y0 = floor + 1;
        int col = hw + 1;
        for (int dx = -(h - 1); dx <= h - 1; dx++) {
            int x = c + dx;
            int a = Math.abs(dx);
            if (a == col) {
                put(s, x, y0, z2, STONE);
                put(s, x, y0 + 1, z2, STONE);
                put(s, x, y0 + 2, z2, TRIM);
                put(s, x, y0 + 3, z2, TRIM);
            } else if (a < col) {
                box(s, x, y0, z2, x, y0 + 3, z2, AIR);
                put(s, x, y0, z2, IRON);
                if (a == hw) {
                    put(s, x, y0 + 3, z2, Kit.upside(TRIM_STAIRS, dx < 0 ? "west" : "east"));
                }
            } else {
                box(s, x, y0, z2, x, y0 + 2, z2, AIR);
                put(s, x, y0, z2, IRON);
                put(s, x, y0 + 3, z2, TRIM);
            }
        }
        if (hw == 2) {
            put(s, c - 1, y0 + 4, z2, Kit.upside(TRIM_STAIRS, "west"));
            put(s, c + 1, y0 + 4, z2, Kit.upside(TRIM_STAIRS, "east"));
            put(s, c, y0 + 4, z2, AIR);
            put(s, c, y0 + 5, z2, CARVED);
        } else {
            put(s, c, y0 + 4, z2, CARVED);
        }
        for (int dx = -(h - 1); dx <= h - 1; dx++) {
            add(s, c + dx, floor, z2 + 1, Kit.upside(TRIM_STAIRS, "north"));
        }
        // the window over the inner arch
        Southern.window(s, c, y0 + 1, z1, "north", 1, 2, false, false);
        // the room of the guard: doors from the walks, a ladder to the terrace, benches and lanterns
        s.door(x1, y0, p.sIn, DOOR, "west", "left");
        s.door(x2, y0, p.sIn, DOOR, "east", "left");
        for (int y = y0; y <= p.gtop; y++) {
            put(s, x1 + 1, y, z1 + 1, Kit.ladder("south"));
        }
        Furnish.bench(s, c - 1, z2 - 1, c + 1, z2 - 1, y0, "south", WOOD);
        hang(s, c, ceil - 1, (z1 + z2) / 2);
        Southern.machicolations(s, x1, z1, x2, z2, p.gtop + 1, STONE, STONE_STAIRS, true);
        put(s, x2 - 1, p.gtop + 1, z2 - 1, Kit.lantern());
        put(s, x1 + 2, p.gtop + 1, z2 - 1, Kit.lantern());
        // the guards of the gate in the passage, of the walls on the curtain and on the terrace
        p.point(GATE, c - 1, 1, z1 + 1);
        p.point(GATE, c + 1, 1, z1 + 1);
        p.point(WALL, x1 + 2, p.gtop + 1, z1 + 2);
        p.point(WALL, x2 - 2, p.gtop + 1, z1 + 2);
    }

    // ---------------------------------------------------------------- windows

    /**
     * Windows of the outer walls (narrow ones with shutters on the upper floor, small ones with grilles below) and of the
     * faces towards the courts.
     */
    private static void windows(Structure s, Plan p) {
        int o = p.o;
        int f = p.far;
        int upper = p.ct + 2;
        for (int z = o + 3; z < p.sIn - 1; z += 4) {
            for (int x : new int[]{o, f}) {
                String side = x == o ? "west" : "east";
                int out = x == o ? x - 1 : x + 1;
                if (s.get(x, upper, z).equals(RENDER) && !s.solid(out, upper, z) && !s.solid(out, upper, z - 1) && !s.solid(out, upper, z + 1)) {
                    Southern.window(s, x, upper, z, side, 1, 2, true, false);
                }
                if (s.get(x, 3, z).equals(STONE) && s.get(x + (x == o ? 1 : -1), 3, z).equals(AIR)) {
                    Southern.window(s, x, 3, z, side, 1, 1, false, false, IRON);
                }
            }
        }
        for (int x = o + 3; x < f - 1; x += 4) {
            if (s.get(x, upper, o).equals(RENDER) && !s.solid(x, upper, o - 1) && !s.solid(x - 1, upper, o - 1) && !s.solid(x + 1, upper, o - 1)) {
                Southern.window(s, x, upper, o, "north", 1, 2, true, false);
            }
            if (s.get(x, 3, o).equals(STONE) && s.get(x, 3, o + 1).equals(AIR)) {
                Southern.window(s, x, 3, o, "north", 1, 1, false, false, IRON);
            }
        }
        // the court front of the hall: windows of the aisles below and above
        int ww = p.c - p.ax - 2;
        int we = p.c + p.ax + 2;
        for (int x : new int[]{ww, we}) {
            Southern.window(s, x, 3, p.hz2, "south", 1, 2, true, true);
            Southern.window(s, x, p.ct + 3, p.hz2, "south", p.small() ? 1 : 3, 3, false, false);
        }
        Southern.window(s, p.c, p.ct + 3, p.hz2, "south", 3, 3, false, false);
        // the garden front of the hall and the garden faces of the ranges
        if (p.garden) {
            for (int x : new int[]{ww, we}) {
                Southern.window(s, x, p.ct + 3, p.hz1, "north", 3, 3, false, false);
            }
            int gz = (p.nIn + p.hz1) / 2;
            for (int dz : new int[]{-2, 2}) {
                Southern.window(s, p.wIn, p.ct + 2, gz + dz, "east", 1, 2, true, true);
                Southern.window(s, p.eIn, p.ct + 2, gz + dz, "west", 1, 2, true, true);
            }
            for (int x = p.c - 6; x <= p.c + 6; x += 4) {
                if (x != p.c - 2 && x != p.c + 2) {
                    Southern.window(s, x, p.ct + 2, p.nIn, "south", 1, 2, true, true);
                }
            }
            Southern.balcony(s, p.c, p.ct + 1, p.nIn, "south", 5, 2);
        }
    }

    /**
     * A balcony on an outer wall at the upper floor, three wide on corbels with an iron railing, its door at (a, c) in the
     * wall that looks the way of {@code facing} (a along the wall, c the line of the wall); moved along the wall until the
     * room behind the door is free.
     */
    private static void balcony(Structure s, int a, int c, String facing) {
        boolean alongX = facing.equals("north") || facing.equals("south");
        int[] in = Furnish.step(Furnish.opposite(facing));
        int y = 7;
        for (int shift : new int[]{0, 2, -2, 4, -4}) {
            int x = alongX ? a + shift : c;
            int z = alongX ? c : a + shift;
            if (!s.solid(x + in[0], y, z + in[1]) && !s.solid(x + in[0], y + 1, z + in[1])) {
                Southern.balcony(s, x, y, z, facing, 3, 2);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- the throne hall

    /**
     * The inside of the throne hall: a floor of cotto with the nave of trim; two arcades on columns parting the nave from
     * the aisles; a coffered ceiling with lanterns hanging in its coffers; the throne's place on a dais of carved stone under
     * a canopy before a cloth of state; a red runner from the portal to the dais; banners on the columns, benches of stone in
     * the aisles, a great chandelier; the portal with its pediment, the doors to the kitchen, the treasury and the garden;
     * the places of the guards.
     */
    private static void hallInside(Structure s, Plan p) {
        int c = p.c;
        int x1 = p.wIn + 1;
        int x2 = p.eIn - 1;
        int z1 = p.hz1 + 1;
        int z2 = p.hz2 - 1;
        int cl = p.htop - 1;
        box(s, x1, 1, z1, x2, cl, z2, AIR);
        box(s, x1, 0, z1, x2, 0, z2, COTTO);
        box(s, c - p.ax + 1, 0, z1, c + p.ax - 1, 0, z2, TRIM);
        for (int side = -1; side <= 1; side += 2) {
            Southern.arcade(s, c + side * p.ax, p.hz1, c + side * p.ax, p.hz1 + p.hallLen - 1, 1, p.hallArch, p.hallSpan, TRIM);
        }
        // the coffered ceiling: beams on the lines of the walls, the arcades and the axis
        List<Integer> xs = new ArrayList<>(List.of(p.wIn, c - p.ax, c, c + p.ax, p.eIn));
        if (p.ax > 4) {
            xs.add(c - p.ax - 3);
            xs.add(c + p.ax + 3);
        }
        List<Integer> zs = new ArrayList<>();
        for (int z = p.hz1; z <= p.hz2; z++) {
            if ((z - p.hz1) % (p.hallSpan + 1) == 0) {
                zs.add(z);
            }
        }
        zs.add(p.hz2);
        zs.add(p.hz2 - 1);
        coffers(s, x1, z1, x2, z2, cl, xs, zs);
        // the doors to the kitchen (west), the treasury (east) and the garden or the rooms behind
        s.door(p.wIn, 1, c + 3, DOOR, "west", "left");
        s.door(p.eIn, 1, c + 3, IRON_DOOR, "east", "left");
        put(s, p.eIn - 1, 3, c + 2, Kit.wallTorch("west"));
        int ga = c - p.ax - 2;
        int gb = c + p.ax + 2;
        s.door(ga, 1, p.hz1, DOOR, "north", "left");
        s.door(gb, 1, p.hz1, DOOR, "north", "left");
        dais(s, p);
        // the runner from the portal to the dais
        int rw = p.small() ? 0 : 1;
        box(s, c - rw, 1, c + 2, c + rw, 1, z2, Kit.CLOTH_CARPET);
        // banners on the middle columns, towards the nave
        for (int z = p.hz1 + p.hallSpan + 1; z < p.hz1 + p.hallLen - 1; z += p.hallSpan + 1) {
            put(s, c - p.ax + 1, p.hallArch - 2, z, Kit.wallBanner("red", "east"));
            put(s, c + p.ax - 1, p.hallArch - 2, z, Kit.wallBanner("red", "west"));
        }
        // benches of stone along the walls of the aisles, lanterns on brackets over them, plants
        for (int side = -1; side <= 1; side += 2) {
            int x = side < 0 ? x1 : x2;
            String face = side < 0 ? "east" : "west";
            for (int z = z1; z <= z2 - 1; z++) {
                if (z != c) {
                    piece(s, x, 1, z, Kit.stairs(TRIM_STAIRS, Furnish.opposite(face)));
                }
            }
            for (int z = z1 + 1; z <= z2 - 1; z += 3) {
                Southern.wallLantern(s, x, 4, z);
            }
            // a dado of glazed tiles in the side walls, behind the benches
            int wall = side < 0 ? p.wIn : p.eIn;
            for (int z = z1; z <= z2; z++) {
                if (!s.get(wall, 1, z).contains("_door[") && !s.get(wall, 2, z).contains("_door[")) {
                    for (int y = 2; y <= 3; y++) {
                        String[] turn = {"north", "east", "west", "south"};
                        put(s, wall, y, z, Southern.AZULEJO + "[facing=" + turn[(z & 1) + 2 * (y & 1)] + "]");
                    }
                }
            }
            planter(s, c + side * (p.ax - 1), 1, z2, true);
        }
        int chain = p.htop - p.hallArch - 3;
        Furnish.greatChandelier(s, c, c + 4, cl - 1, Math.max(2, chain));
        // the portal: three doors under an arch, a pediment on pilasters before them
        int dz = p.hz2;
        s.door(c - 1, 1, dz, DOOR, "south", "left");
        s.door(c, 1, dz, DOOR, "south", "left");
        s.door(c + 1, 1, dz, DOOR, "south", "right");
        put(s, c - 1, 3, dz, Kit.upside(TRIM_STAIRS, "west"));
        put(s, c + 1, 3, dz, Kit.upside(TRIM_STAIRS, "east"));
        put(s, c, 3, dz, CARVED);
        int pz = dz + 1;
        for (int y = 1; y <= 3; y++) {
            put(s, c - 2, y, pz, y == 1 ? STONE : TRIM);
            put(s, c + 2, y, pz, y == 1 ? STONE : TRIM);
        }
        box(s, c - 2, 4, pz, c + 2, 4, pz, TRIM);
        put(s, c - 3, 4, pz, Kit.upside(TRIM_STAIRS, "east"));
        put(s, c + 3, 4, pz, Kit.upside(TRIM_STAIRS, "west"));
        put(s, c - 2, 5, pz, Kit.stairs(TRIM_STAIRS, "east"));
        put(s, c - 1, 5, pz, TRIM);
        put(s, c, 5, pz, CARVED);
        put(s, c + 1, 5, pz, TRIM);
        put(s, c + 2, 5, pz, Kit.stairs(TRIM_STAIRS, "west"));
        put(s, c - 1, 6, pz, Kit.stairs(TRIM_STAIRS, "east"));
        put(s, c, 6, pz, TRIM);
        put(s, c + 1, 6, pz, Kit.stairs(TRIM_STAIRS, "west"));
        put(s, c, 7, pz, Kit.slab(TRIM_SLAB));
        Southern.wallLantern(s, c - 3, 2, pz);
        Southern.wallLantern(s, c + 3, 2, pz);
        // the guards of the hall, the door of the hall and the treasury
        p.point(HALL, c - 2, 1, c + 1);
        p.point(HALL, c + 2, 1, c + 1);
        p.point(HALL, c - 2, 1, c + 3);
        p.point(HALL, c + 2, 1, c + 3);
        p.point(PALACE_DOOR, c - 1, 1, z2);
        p.point(PALACE_DOOR, c + 1, 1, z2);
        p.point(TREASURY, p.eIn - 1, 1, c + 3);
    }

    /**
     * A coffered ceiling over x1..x2, z1..z2 at layer {@code y}: beams of the timber on the given lines; in every coffer
     * between them a rim of upside-down stairs of trim stepping up into the coffer, a panel of carved stone above, and a
     * lantern hanging in a coffer that has room for it.
     */
    private static void coffers(Structure s, int x1, int z1, int x2, int z2, int y, List<Integer> xs, List<Integer> zs) {
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                boolean bx = xs.contains(x);
                boolean bz = zs.contains(z);
                if (bx || bz) {
                    put(s, x, y, z, bx && !bz ? Kit.log(BEAM, "z") : Kit.log(BEAM, "x"));
                    continue;
                }
                String to = xs.contains(x - 1) ? "west" : xs.contains(x + 1) ? "east" : zs.contains(z - 1) ? "north"
                        : zs.contains(z + 1) ? "south" : null;
                put(s, x, y + 1, z, to == null ? "minecraft:yellow_terracotta" : "minecraft:blue_terracotta");
                put(s, x, y, z, to == null ? AIR : Kit.upside(TRIM_STAIRS, to));
            }
        }
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                if (!xs.contains(x) && !zs.contains(z) && s.get(x, y, z).equals(AIR) && s.get(x - 1, y, z).equals(AIR) == false
                        && s.get(x, y, z - 1).equals(AIR) == false) {
                    put(s, x, y, z, Kit.hangingLantern());
                }
            }
        }
    }

    /**
     * The dais of the throne: steps of trim round its place on a floor of carved stone, the back of the dais, a cloth of
     * state on it between two posts, and a canopy of trim and cloth over all.
     */
    private static void dais(Structure s, Plan p) {
        int c = p.c;
        box(s, c - 2, 0, c - 1, c + 2, 0, c + 1, CARVED);
        put(s, c - 1, 1, c, Kit.stairs(TRIM_STAIRS, "east"));
        put(s, c + 1, 1, c, Kit.stairs(TRIM_STAIRS, "west"));
        put(s, c - 1, 1, c + 1, Kit.stairs(TRIM_STAIRS, "north"));
        put(s, c + 1, 1, c + 1, Kit.stairs(TRIM_STAIRS, "north"));
        put(s, c, 1, c + 1, Kit.slab(TRIM_SLAB));
        box(s, c - 2, 1, c - 1, c + 2, 1, c - 1, TRIM);
        box(s, c - 1, 2, c - 1, c + 1, 5, c - 1, Kit.CLOTH);
        box(s, c, 3, c - 1, c, 5, c - 1, "minecraft:yellow_wool");
        for (int x : new int[]{c - 2, c + 2}) {
            box(s, x, 2, c - 1, x, 5, c - 1, STONE);
            put(s, x, 6, c - 1, TRIM);
            put(s, x, 1, c, Kit.stairs(TRIM_STAIRS, x < c ? "east" : "west"));
        }
        box(s, c - 1, 6, c - 1, c + 1, 6, c - 1, Kit.CLOTH);
        box(s, c - 2, 7, c - 1, c + 2, 7, c + 1, Kit.topSlab(TRIM_SLAB));
        box(s, c - 2, 7, c + 1, c + 2, 7, c + 1, Kit.upside(TRIM_STAIRS, "north"));
        put(s, c - 2, 7, c, Kit.upside(TRIM_STAIRS, "east"));
        put(s, c + 2, 7, c, Kit.upside(TRIM_STAIRS, "west"));
        box(s, c - 1, 8, c - 1, c + 1, 8, c, Kit.CLOTH);
        put(s, c, 9, c - 1, CARVED);
        put(s, c - 1, 6, c + 1, Kit.wallBanner("red", "south"));
        put(s, c + 1, 6, c + 1, Kit.wallBanner("red", "south"));
    }

    // ---------------------------------------------------------------- rooms

    /**
     * The rooms of the ranges and the towers, by size: kitchen, stores, treasury, chapel, guard rooms and the armoury below;
     * the chambers, the council room and the library above. The doors between them come first, so that the furniture keeps
     * clear of them.
     */
    private static void rooms(Structure s, Plan p) {
        int o = p.o;
        int f = p.far;
        int g = 1;
        int u = p.ct + 1;
        int wx1 = o + 1;
        int wx2 = p.wIn - 1;
        int ex1 = p.eIn + 1;
        int ex2 = f - 1;
        int hz1 = p.hz1 + 1;
        int hz2 = p.hz2 - 1;
        int[] ne = p.towers[0];
        int nex1 = ne[0] + 1;
        int nez1 = ne[1] + 1;
        int nex2 = ne[0] + ne[2] - 2;
        int nez2 = ne[1] + ne[3] - 2;
        int tz = ne[1] + ne[3] - 1;
        if (p.small()) {
            int nz2 = p.hz1 - 1;
            int ez1 = tz + 1;
            // doors below: the kitchen to the court and to the store, the north range to the store of the east range,
            // that store to the tower; above: from the upper loggia along the east range, the north range and the west range
            s.door(p.wIn, g, p.kz - 1, DOOR, "west", "left");
            s.door(wx2, g, p.hz1, DOOR, "north", "left");
            s.door(p.eIn, g, nz2 - 1, DOOR, "east", "left");
            s.door(ex2 - 1, g, tz, DOOR, "north", "left");
            s.door(ex1 + 1, u, p.hz2, DOOR, "south", "left");
            s.door(ex2, u, p.hz1, DOOR, "north", "left");
            s.door(p.eIn, u, nz2 - 1, DOOR, "east", "left");
            s.door(p.wIn, u, nz2 - 1, DOOR, "west", "left");
            s.door(wx2, u, p.hz1, DOOR, "north", "left");
            s.door(ex2 - 1, u, tz, DOOR, "north", "left");
            kitchen(s, p, wx1, hz1, wx2, p.kz - 1, g);
            treasury(s, ex1, hz1, ex2, hz2, g);
            store(s, wx1, o + 1, wx2, nz2, g);
            armoury(s, p.wIn + 1, o + 1, p.eIn - 1, nz2, g);
            store(s, ex1, ez1, ex2, nz2, g);
            chamber(s, wx1, hz1, wx2, p.kz - 1, u, "north", true);
            chamber(s, wx1, o + 1, wx2, nz2, u, "south", false);
            council(s, p.wIn + 1, o + 1, p.eIn - 1, nz2, u);
            chamber(s, ex1, hz1, ex2, hz2, u, "north", false);
            library(s, ex1, ez1, ex2, nz2, u);
            guard(s, nex1, nez1, nex2, nez2, g);
            guard(s, nex1, nez1, nex2, nez2, u);
            guard(s, p.kx + 1, p.kz + 1, p.kx + p.kw - 2, p.kz + p.kw - 2, g);
            return;
        }
        int gz1 = p.nIn + 1;
        int gz2 = p.hz1 - 1;
        int gm = (gz1 + gz2) / 2 + 1;
        int nz1 = o + 1;
        int nz2 = p.nIn - 1;
        int nm = (nz1 + nz2) / 2;
        int lw = p.wIn - 4;
        int le = p.eIn + 4;
        int cz1 = p.hz2 + 1;
        int[] se = p.towers[1];
        // doors below
        s.door(wx2, g, p.hz2, DOOR, "south", "left");
        s.door(wx1 + 1, g, p.hz1, DOOR, "north", "left");
        s.door(p.wIn, g, gm, DOOR, "west", "left");
        s.door(wx1 + 1, g, p.nIn, DOOR, "north", "left");
        s.door(p.c, g, p.nIn, DOOR, "north", "left");
        s.door(p.eIn, g, gm, DOOR, "east", "left");
        s.door(ex2 - 1, g, tz, DOOR, "north", "left");
        s.door(lw + 1, g, cz1, DOOR, "east", "left");
        s.door(le - 1, g, cz1, DOOR, "west", "left");
        s.door(se[0], g, p.sIn - 1, DOOR, "west", "left");
        // doors above: the enfilades of the west and east ranges from the upper loggias, the library between them
        s.door(wx2, u, p.hz2, DOOR, "south", "left");
        s.door(wx1 + 1, u, p.hz1, DOOR, "north", "left");
        s.door(wx1 + 1, u, p.nIn, DOOR, "north", "left");
        s.door(p.wIn, u, nm, DOOR, "west", "left");
        s.door(ex1, u, p.hz2, DOOR, "south", "left");
        s.door(ex2 - 1, u, p.hz1, DOOR, "north", "left");
        s.door(ex2 - 1, u, tz, DOOR, "north", "left");
        s.door(lw + 1, u, cz1, DOOR, "east", "left");
        s.door(le - 1, u, cz1, DOOR, "west", "left");
        s.door(ne[0], u, nm, DOOR, "west", "left");
        if (ne[0] > p.eIn) {
            box(s, p.eIn + 1, u, nm, ne[0] - 1, u + 1, nm, AIR);
            s.door(p.eIn, u, nm, DOOR, "west", "left");
        }
        // below
        kitchen(s, p, wx1, hz1, wx2, hz2, g);
        treasury(s, ex1, hz1, ex2, hz2, g);
        store(s, wx1, gz1, wx2, gz2, g);
        store(s, wx1, nz1, wx2, nz2, g);
        chapel(s, p.wIn + 1, nz1, p.eIn - 1, nz2, g);
        guard(s, ex1, gz1, ex2, gz2, g);
        guard(s, wx1, cz1, lw, p.kz - 1, g);
        store(s, le, cz1, ex2, se[1] - 1, g);
        armoury(s, nex1, nez1, nex2, nez2, g);
        guard(s, p.kx + 1, p.kz + 1, p.kx + p.kw - 2, p.kz + p.kw - 2, g);
        store(s, se[0] + 2, se[1] + 2, se[0] + se[2] - 2, se[1] + se[3] - 2, g);
        // above
        chamber(s, wx1, hz1, wx2, hz2, u, "north", true);
        chamber(s, wx1, gz1, wx2, gz2, u, "north", false);
        chamber(s, wx1, nz1, wx2, nz2, u, "south", false);
        library(s, p.wIn + 1, nz1, p.eIn - 1, nz2, u);
        council(s, ex1, hz1, ex2, hz2, u);
        chamber(s, ex1, gz1, ex2, gz2, u, "north", false);
        chamber(s, wx1, cz1, lw, p.kz - 1, u, "north", false);
        guard(s, le, cz1, ex2, se[1] - 1, u);
        guard(s, nex1, nez1, nex2, nez2, u);
    }

    // ---------------------------------------------------------------- furniture that keeps clear of the doors

    /**
     * Whether a piece of furniture may stand in a cell: inside, empty, and not where one stands to pass a door.
     */
    private static boolean free(Structure s, int x, int y, int z) {
        if (!s.inside(x, y, z) || s.solid(x, y, z)) {
            return false;
        }
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int yy = y - 1; yy <= y + 1; yy++) {
                if (s.inside(x + d[0], yy, z + d[1]) && s.get(x + d[0], yy, z + d[1]).contains("_door[")) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A block of furniture where it is free. */
    private static void piece(Structure s, int x, int y, int z, String state) {
        if (free(s, x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static void crate(Structure s, int x, int y, int z) {
        piece(s, x, y, z, "minecraft:stripped_" + WOOD + "_wood[axis=y]");
    }

    private static void sack(Structure s, int x, int y, int z, boolean white) {
        piece(s, x, y, z, white ? "minecraft:white_wool" : "minecraft:brown_wool");
    }

    private static void amphora(Structure s, int x, int y, int z) {
        piece(s, x, y, z, "minecraft:decorated_pot[facing=north,waterlogged=false]");
    }

    private static void chair(Structure s, int x, int y, int z, String facing) {
        piece(s, x, y, z, Kit.stairs("minecraft:" + WOOD + "_stairs", Furnish.opposite(facing)));
    }

    /** A bench of stairs from one cell to another, its seat looking the way of {@code facing}. */
    private static void bench(Structure s, int x1, int z1, int x2, int z2, int y, String facing) {
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                chair(s, x, y, z, facing);
            }
        }
    }

    private static void table(Structure s, int x, int y, int z) {
        if (free(s, x, y, z) && free(s, x, y + 1, z)) {
            Furnish.table(s, x, y, z, WOOD);
        }
    }

    /** A long table of upper slabs from one cell to another. */
    private static void longTable(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                piece(s, x, y, z, Kit.topSlab("minecraft:" + WOOD + "_slab"));
            }
        }
    }

    /** A shelf of books from one cell to another, {@code height} high. */
    private static void books(Structure s, int x1, int z1, int x2, int z2, int y, int height) {
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                if (free(s, x, y, z)) {
                    for (int yy = y; yy < y + height; yy++) {
                        piece(s, x, yy, z, "minecraft:bookshelf");
                    }
                }
            }
        }
    }

    private static void planter(Structure s, int x, int y, int z, boolean flowering) {
        if (free(s, x, y, z) && free(s, x, y + 1, z)) {
            Southern.planter(s, x, y, z, flowering);
        }
    }

    private static void rack(Structure s, int x, int y, int z, String facing) {
        piece(s, x, y, z, "minecraft:grindstone[face=floor,facing=" + facing + "]");
    }

    /** A rug of the cloth of the kingdom over the free floor of x1..x2, z1..z2 (clamped to the room). */
    private static void rug(Structure s, int x1, int z1, int x2, int z2, int y, int rx1, int rz1, int rx2, int rz2) {
        for (int z = Math.max(z1, rz1); z <= Math.min(z2, rz2); z++) {
            for (int x = Math.max(x1, rx1); x <= Math.min(x2, rx2); x++) {
                if (s.inside(x, y, z) && !s.solid(x, y, z)) {
                    s.set(x, y, z, Kit.CLOTH_CARPET);
                }
            }
        }
    }

    /**
     * A bed of state (no bed of the game) with its foot at (x, y, z) and its head towards {@code head}, if all its parts
     * are free.
     */
    private static boolean bedOfState(Structure s, int x, int y, int z, String head) {
        int[] d = Furnish.step(head);
        int[] a = Furnish.step(Furnish.left(head));
        int[][] cells = {{x, z}, {x + d[0], z + d[1]}, {x + 2 * d[0], z + 2 * d[1]}, {x + a[0], z + a[1]}, {x - a[0], z - a[1]},
                {x + a[0] + 2 * d[0], z + a[1] + 2 * d[1]}, {x - a[0] + 2 * d[0], z - a[1] + 2 * d[1]}};
        for (int i = 0; i < cells.length; i++) {
            int[] cell = cells[i];
            boolean post = i >= 3;
            if (post && s.inside(cell[0], y, cell[1]) && s.solid(cell[0], y, cell[1])) {
                continue;
            }
            if (!free(s, cell[0], y, cell[1]) || !free(s, cell[0], y + 1, cell[1])) {
                return false;
            }
        }
        Furnish.canopyBed(s, x, y, z, head, "spruce");
        return true;
    }

    // ---------------------------------------------------------------- the rooms

    /**
     * The kitchen: the fire under its hood of plaster against the outer wall with its flue and a chimney over the roof, the
     * counter of glazed tiles with the sink, a smoker, a table with benches, amphorae, sacks and a crate, lanterns.
     */
    private static void kitchen(Structure s, Plan p, int x1, int z1, int x2, int z2, int y) {
        int mz = (z1 + z2) / 2;
        Southern.hearth(s, x1, y, mz, "east", p.top);
        for (int yy = p.top + 1; yy <= p.top + 3; yy++) {
            put(s, x1, yy, mz, Kit.CHIMNEY);
        }
        put(s, x1, p.top + 4, mz, Kit.slab(Kit.CHIMNEY_SLAB));
        Southern.kitchen(s, x2, y, z1 + 1, "west", Math.min(4, p.c + 2 - z1));
        piece(s, x1, y, z2, "minecraft:smoker[facing=east,lit=false]");
        int tx = (x1 + x2) / 2;
        longTable(s, tx, mz - 1, tx, mz + 1, y);
        bench(s, tx + 1, mz - 1, tx + 1, mz + 1, y, "west");
        piece(s, tx, y + 1, mz, Southern.POT);
        piece(s, tx, y + 1, mz + 1, Furnish.candles(2));
        amphora(s, x1, y, z1);
        amphora(s, x1 + 1, y, z1);
        sack(s, x1 + 1, y, z2, true);
        crate(s, x1 + 2, y, z2);
        hang(s, tx, y + 3, mz - 2);
        hang(s, tx, y + 3, mz + 2);
    }

    /**
     * The treasury behind the hall: iron bars across the room with an iron door, behind them the chests and the gold of the
     * kingdom; before them the counting table with candles, a chair, a shelf of ledgers and lanterns.
     */
    private static void treasury(Structure s, int x1, int z1, int x2, int z2, int y) {
        int bars = x2 - 2;
        box(s, bars, y, z1, bars, y + 3, z2, IRON);
        s.door(bars, y, (z1 + z2) / 2, IRON_DOOR, "east", "left");
        Furnish.chest(s, x2, y, z1, "west");
        Furnish.chest(s, x2, y, z2, "west");
        for (int z = z1 + 1; z < z2; z++) {
            put(s, x2, y, z, z % 2 == 0 ? "minecraft:gold_block" : "minecraft:raw_gold_block");
        }
        put(s, x2, y + 1, z1 + 1, "minecraft:gold_block");
        put(s, x2 - 1, y, z1, "minecraft:gold_block");
        longTable(s, x1 + 1, z1 + 1, x1 + 1, z1 + 2, y);
        piece(s, x1 + 1, y + 1, z1 + 1, Furnish.candles(3));
        piece(s, x1 + 1, y + 1, z1 + 2, "minecraft:gold_block");
        chair(s, x1 + 2, y, z1 + 1, "west");
        books(s, x1, z2, x1, z2, y, 2);
        hang(s, x1 + 1, y + 3, (z1 + z2) / 2 + 1);
        hang(s, x2 - 1, y + 3, (z1 + z2) / 2);
    }

    /**
     * A store: crates, sacks of grain and flour, amphorae and hay, a lantern.
     */
    private static void store(Structure s, int x1, int z1, int x2, int z2, int y) {
        crate(s, x1, y, z1);
        crate(s, x1 + 1, y, z1);
        crate(s, x1, y + 1, z1);
        sack(s, x2, y, z1, true);
        sack(s, x2, y, z1 + 1, false);
        sack(s, x2, y + 1, z1, false);
        amphora(s, x1, y, z2);
        amphora(s, x1 + 1, y, z2);
        amphora(s, x2, y, z2);
        piece(s, x2 - 1, y, z2, "minecraft:hay_block[axis=y]");
        hang(s, (x1 + x2) / 2, y + 3, (z1 + z2) / 2);
    }

    /**
     * A guard room: a table with two chairs, a bench along the west wall, a rack of weapons in a corner, a lantern.
     */
    private static void guard(Structure s, int x1, int z1, int x2, int z2, int y) {
        int mx = (x1 + x2) / 2 + 1;
        int mz = (z1 + z2) / 2;
        table(s, mx, y, mz);
        chair(s, mx, y, mz - 1, "south");
        chair(s, mx, y, mz + 1, "north");
        bench(s, x1, mz, x1, mz + 1, y, "east");
        rack(s, x2, y, z1, "west");
        hang(s, mx - 1, y + 3, mz);
    }

    /**
     * The armoury: racks of weapons, an anvil and a smithing table, crates of arms, banners of the kingdom, lanterns.
     */
    private static void armoury(Structure s, int x1, int z1, int x2, int z2, int y) {
        rack(s, x2, y, z1, "west");
        rack(s, x2, y, z1 + 2, "west");
        piece(s, x2, y, z1 + 1, "minecraft:anvil[facing=north]");
        piece(s, x2, y, z2, "minecraft:smithing_table");
        crate(s, x1, y, z2);
        crate(s, x1, y, z2 - 1);
        crate(s, x1, y + 1, z2);
        put(s, x1 + 3, y + 2, z1, Kit.wallBanner("red", "south"));
        put(s, x2 - 2, y + 2, z2, Kit.wallBanner("red", "north"));
        hang(s, (x1 + x2) / 2, y + 3, (z1 + z2) / 2);
    }

    /**
     * A chamber: a bed of state under its canopy (no bed of the game) against the wall of its head, a rug before it, a desk
     * with a chair, a crate for the clothes, a plant, a lantern; the king's chamber has the one chest of the household.
     */
    private static void chamber(Structure s, int x1, int z1, int x2, int z2, int y, String head, boolean king) {
        boolean north = head.equals("north");
        int bz = north ? z1 + 2 : z2 - 2;
        int far = north ? z2 : z1;
        int mx = (x1 + x2) / 2;
        int bx = mx;
        for (int x : new int[]{mx, x1 + 1, x2 - 1, mx + 1, mx - 1}) {
            if (x > x1 && x < x2 && bedOfState(s, x, y, bz, head)) {
                bx = x;
                break;
            }
        }
        int rz = north ? bz + 1 : bz - 3;
        rug(s, bx - 1, rz, bx + 1, rz + 2, y, x1, z1, x2, z2);
        int dz = north ? far - 1 : far + 1;
        piece(s, x2, y, dz, Kit.topSlab("minecraft:" + WOOD + "_slab"));
        piece(s, x2, y + 1, dz, Furnish.candle("white"));
        chair(s, x2 - 1, y, dz, "east");
        crate(s, x1, y, far);
        if (king) {
            piece(s, x1, y, dz, Kit.chest("east"));
        }
        planter(s, x2, y, north ? z1 : z2, true);
        hang(s, bx, y + 3, dz);
    }

    /**
     * The council room: a long table under cloth with chairs along it and the king's seat at its head under a banner,
     * candles, shelves of books and lanterns.
     */
    private static void council(Structure s, int x1, int z1, int x2, int z2, int y) {
        int mx = (x1 + x2) / 2;
        int ta = z1 + 2;
        int tb = z2 - 2;
        longTable(s, mx, ta, mx, tb, y);
        for (int z = ta; z <= tb; z++) {
            if (s.get(mx, y, z).contains("slab")) {
                put(s, mx, y + 1, z, "minecraft:white_carpet");
            }
            chair(s, mx - 1, y, z, "east");
            chair(s, mx + 1, y, z, "west");
        }
        put(s, mx, y + 1, ta, Furnish.candles(3));
        put(s, mx, y + 1, tb, Furnish.candles(3));
        piece(s, mx, y, z1, Kit.stairs(TRIM_STAIRS, "north"));
        put(s, mx, y + 2, z1, Kit.wallBanner("red", "south"));
        books(s, x1, z1, x1, z1 + 1, y, 2);
        books(s, x2, z1, x2, z1 + 1, y, 2);
        hang(s, mx - 2, y + 3, (z1 + z2) / 2);
        hang(s, mx + 2, y + 3, (z1 + z2) / 2);
    }

    /**
     * A library: shelves of books along the back wall with lanterns in niches, a reading table with a bench and candles, a
     * rug, a desk, lanterns.
     */
    private static void library(Structure s, int x1, int z1, int x2, int z2, int y) {
        int mid = (x1 + x2) / 2;
        for (int x = x1 + 1; x <= x2 - 1; x++) {
            if (Math.abs(x - mid) < 2) {
                continue;
            }
            if (x % 4 == 0) {
                piece(s, x, y, z1, Kit.lantern());
                piece(s, x, y + 2, z1, "minecraft:bookshelf");
            } else {
                books(s, x, z1, x, z1, y, 3);
            }
        }
        int mz = Math.min(z2 - 1, (z1 + z2) / 2 + 1);
        longTable(s, mid - 1, mz, mid + 1, mz, y);
        bench(s, mid - 1, mz + 1, mid + 1, mz + 1, y, "north");
        piece(s, mid, y + 1, mz, Furnish.candles(2));
        rug(s, mid - 2, mz - 1, mid + 2, mz - 1, y, x1, z1, x2, z2);
        piece(s, x2, y, z2, Kit.topSlab("minecraft:" + WOOD + "_slab"));
        piece(s, x2, y + 1, z2, Furnish.candle("white"));
        chair(s, x2 - 1, y, z2, "east");
        hang(s, Math.max(x1, mid - 3), y + 3, mz);
        hang(s, Math.min(x2, mid + 3), y + 3, mz);
    }

    /**
     * A chapel: an altar of trim with candles before a panel of glazed tiles in the east wall and a carved cross over it,
     * benches facing it, a runner and lanterns.
     */
    private static void chapel(Structure s, int x1, int z1, int x2, int z2, int y) {
        int mz = (z1 + z2) / 2;
        Southern.tiles(s, x2 + 1, y + 1, mz - 2, x2 + 1, y + 3, mz + 2);
        box(s, x2, y, mz - 1, x2, y, mz + 1, TRIM);
        put(s, x2, y, mz, CARVED);
        put(s, x2, y + 1, mz - 1, Furnish.candles(3));
        put(s, x2, y + 1, mz + 1, Furnish.candles(3));
        put(s, x2, y + 1, mz, Southern.POT);
        put(s, x2, y + 4, mz, CARVED);
        box(s, x2 - 1, y - 1, mz - 1, x2 - 1, y - 1, mz + 1, TRIM);
        for (int x = x2 - 4; x >= x1 + 1; x -= 2) {
            bench(s, x, z1, x, mz - 1, y, "east");
            bench(s, x, mz + 1, x, z2, y, "east");
        }
        rug(s, x1, mz, x2 - 2, mz, y, x1, z1, x2, z2);
        hang(s, x2 - 2, y + 3, mz);
        hang(s, (x1 + x2) / 2, y + 3, mz);
        hang(s, x1 + 2, y + 3, mz);
    }

    /**
     * Keeps every door passable: the cells before and behind a door, where one stands to pass it, hold no furniture.
     */
    private static void clearDoors(Structure s) {
        for (int y = 1; y < s.sizeY - 1; y++) {
            for (int z = 0; z < s.sizeZ; z++) {
                for (int x = 0; x < s.sizeX; x++) {
                    String state = s.get(x, y, z);
                    if (!state.contains("_door[") || !state.contains("half=lower")) {
                        continue;
                    }
                    boolean alongZ = state.contains("facing=north") || state.contains("facing=south");
                    for (int side = -1; side <= 1; side += 2) {
                        int nx = alongZ ? x : x + side;
                        int nz = alongZ ? z + side : z;
                        for (int yy = y; yy <= y + 1; yy++) {
                            if (!s.inside(nx, yy, nz) || !s.solid(nx, yy, nz)) {
                                continue;
                            }
                            String there = s.get(nx, yy, nz);
                            if (there.contains("_door[") || there.contains("ladder") || there.contains("carpet") || there.contains("torch")
                                    || there.contains("banner")) {
                                continue;
                            }
                            s.set(nx, yy, nz, AIR);
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- around the castle

    /** A cypress on the ground at (x, z) where it has room to stand, {@code height} layers high. */
    private static void cypress(Structure s, int x, int z, int height) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int y = 1; y <= height; y++) {
                    if (s.inside(x + dx, y, z + dz) && s.solid(x + dx, y, z + dz)) {
                        return;
                    }
                }
            }
        }
        Southern.cypress(s, x, 1, z, height);
    }

    /**
     * Cypresses round the castle and before its front, and the guards' places on the walks of the walls.
     */
    private static void outside(Structure s, Plan p) {
        int f = p.far;
        int n = p.n;
        int g = p.gw / 2;
        // a pair of cypresses on each side of the road before the gate, clumps along the other sides
        cypress(s, p.c - g - 3, n - 1, 10);
        cypress(s, p.c + g + 3, n - 1, 11);
        cypress(s, p.c - g - 5, n - 1, 8);
        cypress(s, p.c + g + 5, n - 1, 9);
        for (int z = p.o + 4; z < p.kz - 2; z += 6) {
            cypress(s, 0, z, 9 + z % 3);
            cypress(s, 0, z + 2, 8 + z % 2);
        }
        for (int z = p.c - 4; z < f - 4; z += 6) {
            cypress(s, n - 1, z, 10 - z % 2);
        }
        for (int x = p.o + 5; x < p.c + 4; x += 7) {
            cypress(s, x, 0, 9 + x % 3);
            cypress(s, x + 2, 0, 8);
        }
        // the walks: on the curtain at both sides of the gate and on the outer walls of the west and east ranges
        int y = p.ct + 1;
        int gx1 = p.c - p.gw / 2;
        int gx2 = p.c + p.gw / 2;
        int kx2 = p.kx + p.kw - 1;
        p.point(WALL, kx2 + 1, y, f);
        p.point(WALL, gx1 - 1, y, f);
        p.point(WALL, gx2 + 1, y, f);
        p.point(WALL, p.small() ? f - 1 : p.towers[1][0] - 1, y, f);
        p.point(WALL, p.o, p.top + 1, p.c);
        p.point(WALL, f, p.top + 1, p.c);
    }
}
