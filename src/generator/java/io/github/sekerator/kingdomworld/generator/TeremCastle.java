package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle of the style "terem": a wooden kremlin. An ostrog of upright sharpened logs on a plinth of rough stone, with a
 * roofed fighting gallery (zaborola) behind the logs that runs all round through the towers; log towers at the corners
 * whose fighting storey juts out over the walls under a tall tent with a watch lantern or a needle and a flag; and in the
 * middle of the front the gate tower, higher than the others, with a deep passage, a carved gate under a kokoshnik with the
 * icon of the kingdom, its banners, a tent and a small onion dome. Inside, the palace: a cluster of log frames of
 * different heights in the manner of Kolomenskoye, each under a roof of its own (a barrel, a steep carved gable, a tent),
 * joined by galleries, stairs and high porches, around the throne hall; the kitchen, the treasury behind iron bars, the
 * council room and the royal chambers; and over everything the church, its onion domes rising in tiers. A courtyard with a
 * roofed well, flower beds and birches.
 *
 * <p>One program drawn in three sizes: the small kremlin 31 by 31, the kremlin 41 by 41 and the great kremlin 47 by 47. The
 * size gives the number of towers, of the blocks of the palace and of the domes, and the height; the idea stays the same.
 * The throne stands in the middle of the ground on the street layer at the north end of its hall, facing south; the castle
 * leaves it and the ground under it out. Layer 0 replaces the ground, layer 1 is level with the street. Every part is drawn
 * with the blocks that stand for the parts of a look (see {@link Kit} and {@link Terem}).
 */
final class TeremCastle {
    private static final String AIR = Structure.AIR;
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String PATH = "minecraft:spruce_planks";
    private static final String BIRCH_LOG = "minecraft:birch_log";

    /** The three sizes of the kremlin. */
    enum Size {
        SMALL, MEDIUM, LARGE
    }

    private TeremCastle() {
    }

    /**
     * The three kremlins: small, medium and great, with their named places.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(building(Size.SMALL, "terem_castle_small", "minecraft:spruce_log"));
        list.add(building(Size.MEDIUM, "terem_castle_medium", "minecraft:spruce_log"));
        list.add(building(Size.LARGE, "terem_castle_large", "minecraft:gold_block"));
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
     * The numbers of a kremlin of a size: its ground, its walls and towers, its hall and the places named in it.
     */
    private static final class Plan {
        final Size size;
        /** The ground (square) and the height of the box. */
        final int n;
        final int height;
        /** The middle of the ground: the cell of the throne. */
        final int c;
        /** The line of the palisade from every edge, and the middle of the corner towers. */
        final int w;
        final int t;
        /** The corner towers: their width, the layers of their lower walls, eight-sided or square, their top. */
        final int tw;
        final int cornerLower;
        final boolean eight;
        final int cornerTop;
        /** The layer of the floor of the fighting walk; one walks on the layer above. */
        final int f;
        /** The gate tower: its middle row, width, lower walls and the onion on its tent. */
        final int gz;
        final int gw;
        final int gateLower;
        final int gateTop;
        /** Towers in the middle of the walls: {x, z, width}. */
        final int[][] wallTowers;
        /** The walls of the throne hall and the course of its ceiling. */
        final int hx1;
        final int hx2;
        final int hz1;
        final int hz2;
        final int htop;
        /** Named places: {kind, x, y, z}. */
        final List<int[]> points = new ArrayList<>();

        Plan(Size size) {
            this.size = size;
            switch (size) {
                case SMALL -> {
                    n = 31;
                    height = 40;
                    w = 3;
                    t = 4;
                    tw = 5;
                    cornerLower = 9;
                    eight = false;
                    cornerTop = 0;
                    f = 5;
                    gz = 26;
                    gw = 5;
                    gateLower = 11;
                    gateTop = 3;
                    wallTowers = new int[0][];
                    hx1 = 10;
                    hx2 = 20;
                    hz1 = 12;
                    hz2 = 20;
                    htop = 9;
                }
                case MEDIUM -> {
                    n = 41;
                    height = 44;
                    w = 4;
                    t = 5;
                    tw = 5;
                    cornerLower = 9;
                    eight = false;
                    cornerTop = 1;
                    f = 5;
                    gz = 35;
                    gw = 7;
                    gateLower = 13;
                    gateTop = 5;
                    wallTowers = new int[][]{{5, 20, 5}, {35, 20, 5}};
                    hx1 = 14;
                    hx2 = 26;
                    hz1 = 16;
                    hz2 = 27;
                    htop = 10;
                }
                default -> {
                    n = 47;
                    height = 48;
                    w = 4;
                    t = 5;
                    tw = 7;
                    cornerLower = 10;
                    eight = false;
                    cornerTop = 1;
                    f = 6;
                    gz = 41;
                    gw = 7;
                    gateLower = 14;
                    gateTop = 5;
                    wallTowers = new int[][]{{5, 23, 5}, {41, 23, 5}};
                    hx1 = 16;
                    hx2 = 30;
                    hz1 = 19;
                    hz2 = 31;
                    htop = 11;
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

    /** The direction along a line. */
    private static String along(boolean alongX, int sign) {
        return alongX ? (sign > 0 ? "east" : "west") : (sign > 0 ? "south" : "north");
    }

    /** A lantern hanging on a chain from the first solid block above it. */
    private static void hang(Structure s, int x, int y, int z) {
        put(s, x, y, z, Kit.hangingLantern());
        for (int yy = y + 1; yy < s.sizeY && !s.solid(x, yy, z); yy++) {
            s.set(x, yy, z, Kit.chain());
        }
    }

    // ---------------------------------------------------------------- the kremlin

    /**
     * Draws the kremlin of a plan: the ground, the ostrog with its towers and gate, the palace around the throne hall, the
     * church and the courtyard.
     */
    private static Structure castle(Plan p) {
        Structure s = new Structure(p.n, p.height, p.n);
        ground(s, p);
        ostrog(s, p);
        palace(s, p);
        church(s, p);
        courtyard(s, p);
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
     * The ground: grass round the kremlin and in its yards, a log road from the street through the gate.
     */
    private static void ground(Structure s, Plan p) {
        box(s, 0, 0, 0, p.n - 1, 0, p.n - 1, GRASS);
        box(s, p.c - 1, 0, p.gz, p.c + 1, 0, p.n - 1, PATH);
    }

    // ---------------------------------------------------------------- the ostrog

    /**
     * The ostrog: four palisades with their fighting walks between the corner towers, the towers in the middle of the walls
     * (the medium and the great kremlin), stairs from the yard up to the walks, and the gate tower.
     */
    private static void ostrog(Structure s, Plan p) {
        int lo = p.w;
        int hi = p.n - 1 - p.w;
        int t1 = p.t;
        int t2 = p.n - 1 - p.t;
        palisade(s, p, true, lo, t1, t2, 1);
        palisade(s, p, true, hi, t1, t2, -1);
        palisade(s, p, false, lo, t1, t2, 1);
        palisade(s, p, false, hi, t1, t2, -1);
        // Stairs up to the walks along the side walls, and along the back wall in the great kremlin.
        int zTop = p.small() ? p.c - 1 : p.hz2 + 1;
        wallStair(s, p, false, lo, 1, zTop, -1);
        if (!p.small()) {
            wallStair(s, p, false, hi, -1, zTop, -1);
        }
        int h = (p.tw - 1) / 2;
        int a = p.w + 1;
        int b = p.w + 2;
        // The corner towers; the walks enter them through doors, and a door leads from each into the yard.
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                int cx = sx < 0 ? t1 : t2;
                int cz = sz < 0 ? t1 : t2;
                int ax = sx < 0 ? a : p.n - 1 - a;
                int bx = sx < 0 ? b : p.n - 1 - b;
                int az = sz < 0 ? a : p.n - 1 - a;
                int bz = sz < 0 ? b : p.n - 1 - b;
                String toYardX = sx < 0 ? "east" : "west";
                String toYardZ = sz < 0 ? "south" : "north";
                tower(s, p, cx, cz, p.tw, p.cornerLower, p.eight, p.cornerTop, new int[][]{
                        {cx - sx * h, az}, {cx - sx * h, bz}, {ax, cz - sz * h}, {bx, cz - sz * h}},
                        new String[]{toYardX, toYardX, toYardZ, toYardZ}, cx - sx * h, cz, toYardX);
                // the walk crosses the tower on a floor at its own height
                Terem.plate(s, cx, p.f, cz, h - 1, p.eight ? Terem.OCTAGON : Terem.SQUARE, Terem.FLOOR);
                for (int y = 1; y <= p.cornerLower; y++) {
                    put(s, cx, y, cz - h + 1, Kit.ladder("south"));
                }
                p.point(WALL, sx < 0 ? cx + h + 2 : cx - h - 2, p.f + 1, az);
            }
        }
        // Towers in the middle of the walls: the walk runs through them.
        for (int[] tw : p.wallTowers) {
            int cx = tw[0];
            int cz = tw[1];
            int th = (tw[2] - 1) / 2;
            boolean onSide = cx == t1 || cx == t2;
            int in = onSide ? (cx == t1 ? 1 : -1) : (cz == t1 ? 1 : -1);
            int[][] doors;
            String[] outs;
            if (onSide) {
                int la = cx == t1 ? a : p.n - 1 - a;
                int lb = cx == t1 ? b : p.n - 1 - b;
                doors = new int[][]{{la, cz - th}, {lb, cz - th}, {la, cz + th}, {lb, cz + th}};
                outs = new String[]{"north", "north", "south", "south"};
                tower(s, p, cx, cz, tw[2], p.cornerLower - 1, p.large(), 0, doors, outs, cx + in * th, cz, in > 0 ? "east" : "west");
            } else {
                int la = cz == t1 ? a : p.n - 1 - a;
                int lb = cz == t1 ? b : p.n - 1 - b;
                doors = new int[][]{{cx - th, la}, {cx - th, lb}, {cx + th, la}, {cx + th, lb}};
                outs = new String[]{"west", "west", "east", "east"};
                tower(s, p, cx, cz, tw[2], p.cornerLower - 1, p.large(), 0, doors, outs, cx, cz + in * th, in > 0 ? "south" : "north");
            }
            Terem.plate(s, cx, p.f, cz, th - 1, Terem.SQUARE, Terem.FLOOR);
            for (int y = 1; y <= p.cornerLower - 1; y++) {
                put(s, cx, y, cz - th + 1, Kit.ladder("south"));
            }
        }
        gate(s, p);
    }

    /**
     * A palisade on the line {@code line} (z when it runs along x, x when along z) from {@code a1} to {@code a2}: upright logs
     * on a plinth of rough stone with a batter outside, sharpened in pairs at the top, loopholes at the height of the eye;
     * inside, the fighting walk two blocks wide on the layer over {@link Plan#f}, on posts with a carved railing on its
     * inner edge, under a lean-to roof of three rows that rises against the logs; lanterns under the roof and under the
     * walk.
     *
     * @param in the side of the yard: +1 or -1 across the line
     */
    private static void palisade(Structure s, Plan p, boolean alongX, int line, int a1, int a2, int in) {
        int f = p.f;
        String toWall = across(alongX, -in);
        for (int a = a1; a <= a2; a++) {
            at(s, alongX, a, 0, line, Terem.FOOTING);
            at(s, alongX, a, 1, line, Terem.FOOTING);
            at(s, alongX, a, 2, line, Terem.FOOTING);
            at(s, alongX, a, 0, line - in, Terem.FOOTING);
            at(s, alongX, a, 1, line - in, Kit.stairs(Terem.FOOTING_STAIRS, across(alongX, in)));
            for (int y = 3; y <= f + 4; y++) {
                at(s, alongX, a, y, line, Kit.log(Terem.LOG, "y"));
            }
            at(s, alongX, a, f + 5, line, Kit.stairs(Terem.STAIRS, along(alongX, (a - a1) % 2 == 0 ? 1 : -1)));
            if ((a - a1) % 3 == 1) {
                at(s, alongX, a, f + 2, line, Terem.FENCE);
            }
            for (int k = 1; k <= 3; k++) {
                at(s, alongX, a, f, line + in * k, Terem.PLANKS);
            }
            at(s, alongX, a, f + 4, line + in, Kit.stairs(Terem.ROOF.stairs(), toWall));
            at(s, alongX, a, f + 3, line + 2 * in, Kit.stairs(Terem.ROOF.stairs(), toWall));
            at(s, alongX, a, f + 2, line + 3 * in, Kit.stairs(Terem.ROOF.stairs(), toWall));
            if ((a - a1) % 3 == 0 || a == a2) {
                for (int y = 1; y <= f + 1; y++) {
                    at(s, alongX, a, y, line + 3 * in, Kit.log(Terem.POST, "y"));
                }
            } else {
                at(s, alongX, a, f + 1, line + 3 * in, Terem.CARVE_FENCE);
            }
            if ((a - a1) % 4 == 2) {
                at(s, alongX, a, f + 3, line + in, Kit.hangingLantern());
                at(s, alongX, a, f - 1, line + in, Kit.hangingLantern());
            }
        }
    }

    /**
     * A flight of wooden stairs from the yard up to a walk, along the inner edge of its gallery, arriving at a gap in the
     * railing at {@code aTop}; the eave over the gap is cut away so that one walks in upright.
     *
     * @param dir the way the flight climbs along the line: +1 or -1
     */
    private static void wallStair(Structure s, Plan p, boolean alongX, int line, int in, int aTop, int dir) {
        int f = p.f;
        int lane = line + 4 * in;
        String up = along(alongX, dir);
        for (int k = 1; k <= f; k++) {
            int a = aTop - dir * (f - k);
            at(s, alongX, a, k, lane, Kit.stairs(Terem.FLOOR_STAIRS, up));
            if (k >= 2) {
                at(s, alongX, a, k - 1, lane, Kit.upside(Terem.FLOOR_STAIRS, Furnish.opposite(up)));
            }
            if (k >= 3 && k % 2 == 1) {
                for (int y = 1; y <= k - 2; y++) {
                    at(s, alongX, a, y, lane, Kit.log(Terem.POST, "y"));
                }
            }
            if (k >= 2) {
                at(s, alongX, a, k + 1, lane + in, Terem.CARVE_FENCE);
            }
        }
        at(s, alongX, aTop, f + 1, line + 3 * in, AIR);
        at(s, alongX, aTop, f + 2, line + 3 * in, AIR);
        at(s, alongX, aTop, f, line + 3 * in, Terem.PLANKS);
        at(s, alongX, aTop + dir, f + 1, line + 3 * in, Kit.log(Terem.POST, "y"));
        p.point(WALL, alongX ? aTop - dir * 2 : line + in, f + 1, alongX ? line + in : aTop - dir * 2);
    }

    /**
     * A log tower of the ostrog standing on the ground, drawn with {@link Terem#tower}: cleared first, then a floor at the
     * height of the walk inside it, doors from the walks through its walls, a door at its foot to the yard, a lantern on
     * every storey and a flag of the kingdom on its point.
     *
     * @param doors    the wall cells where the walks come in, each with the side it looks to in {@code outs}
     * @param doorX    the cell of the door at its foot
     * @param doorOut  where that door looks
     */
    private static void tower(Structure s, Plan p, int cx, int cz, int width, int lower, boolean eight, int top, int[][] doors,
                              String[] outs, int doorX, int doorZ, String doorOut) {
        int h = (width - 1) / 2;
        box(s, cx - h, 1, cz - h, cx + h, lower + 4, cz + h, AIR);
        Terem.tower(s, cx, 0, cz, width, lower, eight, top, null);
        for (int i = 0; i < doors.length; i++) {
            s.door(doors[i][0], p.f + 1, doors[i][1], Terem.DOOR, Furnish.opposite(outs[i]), "left");
        }
        s.door(doorX, 1, doorZ, Terem.DOOR, Furnish.opposite(doorOut), "left");
        put(s, cx, p.f - 1, cz + 1, Kit.hangingLantern());
        flag(s, cx, cz);
    }

    /**
     * Puts a flag of the kingdom on the point of a tent: the lightning rod of its finial becomes a banner on its pole.
     */
    private static void flag(Structure s, int x, int z) {
        for (int y = s.sizeY - 1; y > 0; y--) {
            String state = s.get(x, y, z);
            if (state.startsWith("minecraft:lightning_rod")) {
                s.set(x, y, z, Kit.banner("red", 0));
                return;
            }
            if (s.solid(x, y, z)) {
                return;
            }
        }
    }

    /**
     * The gate tower (prokhodnaya bashnya) in the middle of the front, higher than the others: a deep passage through it
     * between open leaves, a carved gate on its front under a kokoshnik with the icon of the kingdom, banners of the
     * kingdom on its fighting storey, a room over the passage that the walks of the front pass through, and a tent with an
     * onion dome.
     */
    private static void gate(Structure s, Plan p) {
        int cx = p.c;
        int cz = p.gz;
        int h = (p.gw - 1) / 2;
        int k = h - 1;
        int f = p.f;
        int la = p.n - 1 - (p.w + 1);
        int lb = p.n - 1 - (p.w + 2);
        tower(s, p, cx, cz, p.gw, p.gateLower, false, p.gateTop, new int[][]{{cx - h, la}, {cx - h, lb}, {cx + h, la}, {cx + h, lb}},
                new String[]{"west", "west", "east", "east"}, cx, cz - h, "north");
        // The passage, the floor of the room over it and the ladder from that room up to the fighting storey.
        box(s, cx - k, 1, cz - h, cx + k, f - 1, cz + h, AIR);
        // the log ends at its inner corners leave the way free along the yard
        box(s, cx - h, 1, cz - h - 1, cx - h, 3, cz - h - 1, AIR);
        box(s, cx + h, 1, cz - h - 1, cx + h, 3, cz - h - 1, AIR);
        box(s, cx - k, 0, cz - h - 1, cx + k, 0, cz + h + 1, PATH);
        box(s, cx - k, f, cz - h + 1, cx + k, f, cz + h - 1, Terem.FLOOR);
        for (int y = f + 1; y <= p.gateLower; y++) {
            put(s, cx, y, cz - h + 1, Kit.ladder("south"));
        }
        put(s, cx, f, cz - h + 1, Terem.FLOOR);
        // The inner arch, the open leaves of the gate against the passage walls and lanterns in the passage.
        for (int z : new int[]{cz - h, cz + h}) {
            put(s, cx - k, f - 1, z, Kit.upside(Terem.CARVE_STAIRS, "west"));
            put(s, cx + k, f - 1, z, Kit.upside(Terem.CARVE_STAIRS, "east"));
        }
        for (int y = 1; y <= f - 2; y++) {
            put(s, cx - k, y, cz + h - 1, "minecraft:oak_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]");
            put(s, cx + k, y, cz + h - 1, "minecraft:oak_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]");
        }
        put(s, cx, f - 1, cz, Kit.hangingLantern());
        // The carved gate: pilasters, a band and an ogee kokoshnik over the opening with the icon in it.
        int zo = cz + h + 1;
        for (int y = 1; y <= f - 1; y++) {
            put(s, cx - k - 1, y, zo, Terem.CARVE_FENCE);
            put(s, cx + k + 1, y, zo, Terem.CARVE_FENCE);
        }
        box(s, cx - k - 1, f, zo, cx + k + 1, f, zo, Kit.upside(Terem.CARVE_STAIRS, "north"));
        for (int i = 0; ; i++) {
            int lo = cx - k - 1 + i;
            int hi = cx + k + 1 - i;
            int y = f + 1 + i;
            if (lo >= hi) {
                put(s, lo, y, zo, Kit.slab(Terem.CARVE_SLAB));
                put(s, lo, y + 1, zo, Terem.GILT);
                put(s, lo, y + 2, zo, Terem.ROD);
                break;
            }
            put(s, lo, y, zo, Kit.stairs(Terem.CARVE_STAIRS, "east"));
            put(s, hi, y, zo, Kit.stairs(Terem.CARVE_STAIRS, "west"));
            for (int x = lo + 1; x < hi; x++) {
                put(s, x, y, zo, Terem.CARVE);
            }
        }
        put(s, cx, f + 1, zo, Terem.GILT);
        // Banners of the kingdom on the fighting storey, a lantern over the gate.
        int storey = p.gateLower + 2;
        put(s, cx - 2, storey, cz + h + 2, Kit.wallBanner("red", "south"));
        put(s, cx + 2, storey, cz + h + 2, Kit.wallBanner("red", "south"));
        put(s, cx, f + 3, zo, Kit.wallTorch("south"));
        // The room over the passage: benches and a lantern.
        Terem.lavka(s, cx - k + 1, cz + h - 1, cx + k - 1, cz + h - 1, f + 1);
        hang(s, cx + 1, f + 3, cz);
        p.point(GATE, cx - 1, 1, cz);
        p.point(GATE, cx + 1, 1, cz);
        p.point(WALL, cx, f + 1, cz + 1);
    }

    // ---------------------------------------------------------------- frames, porches and roofs of the palace

    /**
     * A log frame of the palace on its footing: a course of rough stone under the walls, the logs above it up to the course
     * {@code top}, the inside cleared, a floor on the ground and a floor at every layer of {@code floors}.
     */
    private static void srub(Structure s, int x1, int z1, int x2, int z2, int top, int... floors) {
        box(s, x1 - 1, 0, z1 - 1, x2 + 1, 0, z2 + 1, Terem.FOOTING);
        Terem.logWalls(s, x1, 1, z1, x2, top, z2);
        s.walls(x1, 1, z1, x2, 1, z2, Terem.FOOTING);
        for (int[] end : new int[][]{{x1, z1 - 1}, {x2, z1 - 1}, {x1, z2 + 1}, {x2, z2 + 1}}) {
            put(s, end[0], 1, end[1], Terem.FOOTING);
        }
        box(s, x1 + 1, 1, z1 + 1, x2 - 1, top, z2 - 1, AIR);
        box(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, Terem.FLOOR);
        for (int floor : floors) {
            box(s, x1 + 1, floor, z1 + 1, x2 - 1, floor, z2 - 1, Terem.FLOOR);
        }
    }

    /**
     * A high porch (kryltso) before a door of an upper floor, its flight running straight out from the wall: the door at (x,
     * y, z) in a wall looking {@code facing}; a landing three wide and three deep at its threshold on four hewn posts with a
     * carved railing, under a steep tent with a gilt needle cut off at the wall; then a flight three wide down to the ground
     * at layer {@code ground} on a sawn stringer with carved balusters on both sides.
     */
    private static void kryltso(Structure s, int x, int y, int z, String facing, int ground) {
        int[] out = Furnish.step(facing);
        int[] al = Furnish.step(Furnish.left(facing));
        String up = Furnish.opposite(facing);
        s.door(x, y, z, Terem.DOOR, Furnish.opposite(facing), "left");
        int steps = y - ground;
        for (int u = -1; u <= 1; u++) {
            for (int v = 1; v <= 3; v++) {
                put(s, x + al[0] * u + out[0] * v, y - 1, z + al[1] * u + out[1] * v, Terem.FLOOR);
            }
            for (int i = 0; i < steps; i++) {
                int v = 4 + i;
                int layer = y - 1 - i;
                int px = x + al[0] * u + out[0] * v;
                int pz = z + al[1] * u + out[1] * v;
                put(s, px, layer, pz, Kit.stairs(Terem.FLOOR_STAIRS, up));
                if (layer - 1 >= ground) {
                    put(s, px, layer - 1, pz, Kit.upside(Terem.FLOOR_STAIRS, facing));
                }
            }
        }
        // the posts of the landing, its railing and the balusters of the flight
        for (int u = -1; u <= 1; u += 2) {
            for (int v = 1; v <= 3; v += 2) {
                int px = x + al[0] * u + out[0] * v;
                int pz = z + al[1] * u + out[1] * v;
                for (int yy = ground; yy <= y + 2; yy++) {
                    if (yy != y - 1) {
                        put(s, px, yy, pz, Kit.log(Terem.POST, "y"));
                    }
                }
            }
            put(s, x + al[0] * u * 2 + out[0] * 2, y, z + al[1] * u * 2 + out[1] * 2, AIR);
            put(s, x + al[0] * u + out[0] * 2, y, z + al[1] * u + out[1] * 2, Terem.CARVE_FENCE);
            for (int i = 1; i < steps; i++) {
                int v = 4 + i;
                put(s, x + al[0] * u * 2 + out[0] * v, y - i, z + al[1] * u * 2 + out[1] * v, Terem.CARVE_FENCE);
                put(s, x + al[0] * u * 2 + out[0] * v, y - 1 - i, z + al[1] * u * 2 + out[1] * v, Kit.upside(Terem.FLOOR_STAIRS, facing));
            }
            // a post under the middle of the flight
            int vm = 4 + steps / 2;
            for (int yy = ground; yy < y - 1 - steps / 2 - 1; yy++) {
                put(s, x + al[0] * u + out[0] * vm, yy, z + al[1] * u + out[1] * vm, Kit.log(Terem.POST, "y"));
            }
        }
        int mx = x + out[0] * 2;
        int mz = z + out[1] * 2;
        Terem.Area before = (px, pz) -> (px - x) * out[0] + (pz - z) * out[1] >= 1;
        Terem.tent(s, before, mx, mz, 2, y + 3, Terem.SQUARE, Terem.SQUARE, new int[]{1, 2, 2}, 0, -1);
        put(s, mx, y + 2, mz, Kit.hangingLantern());
    }

    /**
     * A small onion dome standing on the ridge of a roof at (x, z): a base of the roof's blocks under its drum, then the
     * dome of {@link Terem#onionDome}.
     */
    private static void ridgeDome(Structure s, int x, int y, int z, int width) {
        int r = width >= 5 ? 1 : 0;
        box(s, x - r, y - 1, z - r, x + r, y - 1, z + r, Terem.ROOF.block());
        Terem.onionDome(s, x, y, z, width);
    }

    /**
     * The chimney of a stove: a stack of the chimney above the roof from {@code y1} to {@code y2} with a pot on it.
     */
    private static void chimney(Structure s, int x, int z, int y1, int y2) {
        box(s, x, y1, z, x, y2, z, Kit.CHIMNEY);
        put(s, x, y2 + 1, z, Kit.CHIMNEY_POT);
    }

    // ---------------------------------------------------------------- the throne hall

    /**
     * The shell of the throne hall: a log frame, a painted ceiling, side windows in carved frames where nothing stands
     * against its walls and its roof: a steep carved gable over the small one, a barrel with onion domes on its ridge over
     * the others.
     */
    private static void hallShell(Structure s, Plan p, int[] windowRows) {
        int x1 = p.hx1;
        int x2 = p.hx2;
        int z1 = p.hz1;
        int z2 = p.hz2;
        int top = p.htop;
        int c = p.c;
        srub(s, x1, z1, x2, z2, top);
        box(s, x1 + 1, top, z1 + 1, x2 - 1, top, z2 - 1, AIR);
        if (p.small()) {
            Terem.steepGable(s, x1, z1, x2, z2, top, false);
        } else {
            int ridge = Terem.barrelRoof(s, z1 - 1, z2 + 1, c, top, x2 - x1 + 3, false, Terem.ROOF, null, true, true);
            int mid = (z1 + z2) / 2;
            ridgeDome(s, c, ridge, mid, 5);
            if (p.large()) {
                ridgeDome(s, c, ridge, mid - 4, 3);
                ridgeDome(s, c, ridge, mid + 4, 3);
            }
        }
        ceiling(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, top);
        // lanterns in the space under the roof, so that nothing lives in the dark there
        for (int x = x1 + 2; x <= x2 - 2; x += 4) {
            for (int z = z1 + 2; z <= z2 - 2; z += 4) {
                if (!s.solid(x, top + 1, z)) {
                    put(s, x, top + 1, z, Kit.lantern());
                }
            }
        }
        for (int z : windowRows) {
            Terem.window(s, x1, 3, z, "west", 1, 3, true);
            Terem.window(s, x2, 3, z, "east", 1, 3, true);
        }
        // the front: a window in the gable or pediment over the door
        Terem.window(s, c, top + 2, z2, "south", 1, 2, false);
    }

    /**
     * A painted ceiling: a border of red, a band of green, and a field of ochre with gilt rosettes.
     */
    private static void ceiling(Structure s, int x1, int z1, int x2, int z2, int y) {
        int mx = (x1 + x2) / 2;
        int mz = (z1 + z2) / 2;
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                int ring = Math.min(Math.min(x - x1, x2 - x), Math.min(z - z1, z2 - z));
                String state;
                if (ring == 0) {
                    state = "minecraft:red_terracotta";
                } else if (ring == 1) {
                    state = "minecraft:green_terracotta";
                } else if (Math.floorMod(x - mx, 3) == 0 && Math.floorMod(z - mz, 3) == 0) {
                    String[] turn = {"north", "east", "south", "west"};
                    state = "minecraft:yellow_glazed_terracotta[facing=" + turn[Math.floorMod((x - mx) / 3 + (z - mz) / 3, 4)] + "]";
                } else {
                    state = "minecraft:orange_terracotta";
                }
                put(s, x, y, z, state);
            }
        }
    }

    /**
     * The inside of the throne hall: the throne's place on a carved dais under a canopy before a wall hung with the cloth
     * and gold of the kingdom, a red runner from the door to the dais, carved posts as columns with brackets, lanterns and
     * banners, benches along the walls for the boyars, chandeliers of candles, the door with its porch outside, and the
     * places of the guards.
     */
    private static void hallInside(Structure s, Plan p) {
        int c = p.c;
        int x1 = p.hx1;
        int x2 = p.hx2;
        int z1 = p.hz1;
        int z2 = p.hz2;
        int top = p.htop;
        // The dais and the canopy over the throne.
        put(s, c - 1, 1, c, Kit.stairs(Terem.CARVE_STAIRS, "east"));
        put(s, c + 1, 1, c, Kit.stairs(Terem.CARVE_STAIRS, "west"));
        put(s, c - 1, 1, c + 1, Kit.stairs(Terem.CARVE_STAIRS, "north"));
        put(s, c + 1, 1, c + 1, Kit.stairs(Terem.CARVE_STAIRS, "north"));
        box(s, c - 1, 1, c - 1, c + 1, 1, c - 1, Terem.CARVE);
        for (int x : new int[]{c - 1, c + 1}) {
            box(s, x, 2, c - 1, x, 4, c - 1, Terem.CARVE_FENCE);
        }
        box(s, c - 1, 5, c - 1, c + 1, 5, c, Kit.topSlab(Terem.CARVE_SLAB));
        put(s, c - 1, 5, c + 1, Kit.upside(Terem.CARVE_STAIRS, "north"));
        put(s, c + 1, 5, c + 1, Kit.upside(Terem.CARVE_STAIRS, "north"));
        put(s, c, 5, c + 1, Kit.upside(Terem.CARVE_STAIRS, "north"));
        put(s, c, 6, c - 1, Terem.GILT);
        // The wall behind it: cloth of the kingdom and gold.
        box(s, c - 2, 2, z1, c + 2, top - 2, z1, Kit.CLOTH);
        box(s, c, 3, z1, c, top - 2, z1, "minecraft:yellow_wool");
        put(s, c, top - 1, z1, Terem.GILT);
        put(s, c - 2, top - 3, z1 + 1, Kit.wallBanner("red", "south"));
        put(s, c + 2, top - 3, z1 + 1, Kit.wallBanner("red", "south"));
        // The floor of the dais of carved white boards, the runner from the door to the dais.
        box(s, c - 2, 0, c - 1, c + 2, 0, c + 1, Terem.CARVE);
        box(s, c, 1, c + 1, c, 1, z2 - 1, Kit.CLOTH_CARPET);
        if (!p.small()) {
            box(s, c - 1, 1, c + 2, c + 1, 1, z2 - 1, Kit.CLOTH_CARPET);
        }
        // Banners of the kingdom along the side walls, where the wall is logs.
        for (int z = z1 + 2; z <= z2 - 2; z += 2) {
            if (s.get(x1, 5, z).startsWith(Terem.LOG) && s.get(x1 + 1, 5, z).equals(AIR)) {
                put(s, x1 + 1, 5, z, Kit.wallBanner("red", "east"));
            }
            if (s.get(x2, 5, z).startsWith(Terem.LOG) && s.get(x2 - 1, 5, z).equals(AIR)) {
                put(s, x2 - 1, 5, z, Kit.wallBanner("red", "west"));
            }
        }
        // Columns: carved posts with brackets, a lantern on each, banners hanging toward the aisle.
        int cs = p.large() ? 4 : 3;
        int[] rows = p.large() ? new int[]{c - 1, c + 3, c + 6} : p.small() ? new int[]{c - 1, c + 3} : new int[]{c - 1, c + 3, c + 6};
        for (int z : rows) {
            for (int side = -1; side <= 1; side += 2) {
                int x = c + side * cs;
                box(s, x, 1, z, x, top - 1, z, Kit.log(Terem.POST, "y"));
                put(s, x, 1, z, Terem.FOOTING);
                put(s, x, top - 1, z - 1, Kit.upside(Terem.CARVE_STAIRS, "south"));
                put(s, x, top - 1, z + 1, Kit.upside(Terem.CARVE_STAIRS, "north"));
                put(s, x - side, top - 1, z, Kit.upside(Terem.CARVE_STAIRS, side < 0 ? "west" : "east"));
                put(s, x - side, 4, z, Kit.wallBanner("red", side < 0 ? "east" : "west"));
                put(s, x - side, top - 2, z, Kit.wallTorch(side < 0 ? "east" : "west"));
            }
        }
        // Benches along the walls for the boyars, banners over them.
        Terem.lavka(s, x1 + 1, c + 1, x1 + 1, z2 - 2, 1);
        Terem.lavka(s, x2 - 1, c + 1, x2 - 1, z2 - 2, 1);
        Terem.lavka(s, x1 + 1, z1 + 1, x1 + 1, c - 2, 1);
        Terem.lavka(s, x2 - 1, z1 + 1, x2 - 1, c - 2, 1);
        // Chandeliers of candles along the aisle.
        for (int z = c + 2; z <= z2 - 2; z += 4) {
            Terem.candleChandelier(s, c, z, top - 1, 2);
        }
        if (!p.small()) {
            Terem.candleChandelier(s, c - cs + 1 + (p.large() ? 1 : 0), c + 1, top - 1, 3);
            Terem.candleChandelier(s, c + cs - 1 - (p.large() ? 1 : 0), c + 1, top - 1, 3);
        }
        // The door and the places of the guards, the door and the hall.
        s.door(c, 1, z2, Terem.DOOR, "north", "left");
        put(s, c - 1, 3, z2 - 1, Kit.wallTorch("north"));
        put(s, c + 1, 3, z2 - 1, Kit.wallTorch("north"));
        p.point(HALL, c - 2, 1, c + 3);
        p.point(HALL, c + 2, 1, c + 3);
        p.point(HALL, c - 2, 1, c + 1);
        p.point(HALL, c + 2, 1, c + 1);
        p.point(PALACE_DOOR, c - 1, 1, z2 - 1);
        p.point(PALACE_DOOR, c + 1, 1, z2 - 1);
    }

    /**
     * The red porch of the hall before its door: a floor on posts with a carved railing, under a barrel whose kokoshnik looks
     * at the gate (a tent with an onion over the great kremlin's), a lantern under it and banners of the kingdom.
     */
    private static void hallPorch(Structure s, Plan p) {
        int c = p.c;
        int z2 = p.hz2;
        int r = p.large() ? 3 : 2;
        int depth = p.small() ? 2 : 3;
        box(s, c - r, 0, z2 + 1, c + r, 0, z2 + depth, Terem.FLOOR);
        for (int side = -1; side <= 1; side += 2) {
            int x = c + side * r;
            box(s, x, 1, z2 + depth, x, 3, z2 + depth, Kit.log(Terem.POST, "y"));
            box(s, x, 1, z2 + 1, x, 1, z2 + depth - 1, Terem.CARVE_FENCE);
        }
        if (p.large()) {
            box(s, c - r, 4, z2 + 1, c + r, 4, z2 + depth, Kit.log(Terem.POST, "x"));
            box(s, c - r + 1, 4, z2 + 1, c + r - 1, 4, z2 + depth - 1, Terem.PLANKS);
            Terem.Area before = (px, pz) -> pz > z2;
            Terem.tent(s, before, c, z2 + 2, 3, 5, Terem.SQUARE, Terem.OCTAGON, new int[]{1, 2}, 3, -1);
        } else {
            Terem.barrelRoof(s, z2 + 1, z2 + depth + 1, c, 4, 2 * r + 1, false, Terem.ROOF, null, false, true);
            for (int x = c - r; x <= c + r; x++) {
                put(s, x, 4, z2 + depth, Kit.log(Terem.POST, "x"));
            }
        }
        hang(s, c, 3, z2 + 2);
        put(s, c - r - 1, 3, z2 + 1, Kit.wallBanner("red", "south"));
        put(s, c + r + 1, 3, z2 + 1, Kit.wallBanner("red", "south"));
    }

    // ---------------------------------------------------------------- the palace

    /**
     * The palace: its blocks first, then the throne hall over them where they meet, its inside and its porch.
     */
    private static void palace(Structure s, Plan p) {
        if (p.small()) {
            hallShell(s, p, new int[]{p.c - 1, p.c + 3});
            smallTerem(s, p);
            // the terem meets the hall at its north wall: the hall keeps its inside and its floor
            box(s, p.hx1 + 1, 1, p.hz1 + 1, p.hx2 - 1, p.htop - 1, p.hz2 - 1, AIR);
            box(s, p.hx1 + 1, 0, p.hz1 + 1, p.hx2 - 1, 0, p.hz2 - 1, Terem.FLOOR);
        } else {
            wings(s, p);
            hallShell(s, p, p.large() ? new int[]{p.hz2 - 2} : new int[0]);
            // the doors from the hall into the kitchen and into the treasury
            int[] wing = p.large() ? new int[]{9, 16} : new int[]{8, 14};
            s.door(wing[1], 1, p.c + 1, Terem.DOOR, "east", "left");
            s.door(p.n - 1 - wing[1], 1, p.c - 1, "minecraft:iron_door", "west", "left");
            p.point(TREASURY, p.n - 2 - wing[1], 1, p.c - 1);
        }
        hallInside(s, p);
        hallPorch(s, p);
    }

    /**
     * The terem of the small kremlin behind the hall, sharing its north wall: two log frames of two floors under steep
     * carved gables looking west and east, and between them a bay with the stair under a barrel, from which the tented bell
     * tower rises over everything with an onion dome. Below, the kitchen (west) and the treasury behind iron bars, its iron
     * door in the hall (east); above, the royal chamber over the warm stove and the council room.
     */
    private static void smallTerem(Structure s, Plan p) {
        int top = 10;
        srub(s, 8, 6, 13, 12, top, 5);
        srub(s, 17, 6, 22, 12, top, 5);
        srub(s, 13, 6, 17, 12, top, 5, 10);
        Terem.steepGable(s, 8, 6, 13, 12, top, true);
        Terem.steepGable(s, 17, 6, 22, 12, top, true);
        Terem.barrelRoof(s, 5, 13, 15, top, 7, false, Terem.ROOF, null, true, true);
        // The bell tower over the bay: a square frame, an eight-sided belfry and a tent with an onion.
        box(s, 13, 11, 7, 17, 18, 11, AIR);
        Terem.logWalls(s, 13, 11, 7, 17, 14, 11);
        box(s, 14, 11, 8, 16, 14, 10, AIR);
        Terem.plate(s, 15, 15, 9, 2, Terem.SQUARE, Terem.FLOOR);
        for (int[] d : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            put(s, 15 + d[0], 15, 9 + d[1], Kit.slab(Terem.ROOF.slab()));
        }
        Terem.octWalls(s, 15, 15, 18, 9, 2);
        for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            box(s, 15 + d[0], 16, 9 + d[1], 15 + d[0], 17, 9 + d[1], AIR);
        }
        box(s, 14, 16, 8, 16, 18, 10, AIR);
        put(s, 15, 18, 9, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        Terem.tent(s, Terem.EVERYWHERE, 15, 9, 3, 19, Terem.OCTAGON, Terem.OCTAGON, Terem.TENT_RISES, 5, -1);
        put(s, 15, 11, 9, Kit.lantern());
        put(s, 16, 16, 9, Kit.lantern());
        // Windows in carved frames.
        Terem.window(s, 8, 7, 9, "west", 1, 2, true);
        Terem.window(s, 8, 2, 8, "west", 1, 1, true);
        Terem.window(s, 22, 7, 9, "east", 1, 2, true);
        put(s, 22, 2, 9, Kit.BARS);
        // The bay: the door from the hall behind the throne, the stair up, doors to both chambers.
        s.door(15, 1, 12, Terem.DOOR, "south", "left");
        for (int k = 1; k <= 5; k++) {
            put(s, 14, k, 12 - k, Kit.stairs(Terem.FLOOR_STAIRS, "north"));
            if (k >= 2) {
                put(s, 14, k - 1, 12 - k, Kit.upside(Terem.FLOOR_STAIRS, "south"));
            }
        }
        box(s, 14, 5, 8, 14, 5, 11, AIR);
        put(s, 16, 4, 9, Kit.hangingLantern());
        s.door(13, 6, 9, Terem.DOOR, "east", "left");
        s.door(17, 6, 9, Terem.DOOR, "west", "left");
        Terem.redCorner(s, 16, 6, 7, "north", "east");
        hang(s, 15, 9, 10);
        // The kitchen: the stove with its bench, a cauldron, a smoker, sacks; a ladder up to the chamber.
        Terem.stove(s, 10, 1, 9, "south", 15);
        chimney(s, 10, 9, 16, 18);
        s.door(8, 1, 10, Terem.DOOR, "east", "left");
        put(s, 9, 1, 11, "minecraft:water_cauldron[level=3]");
        put(s, 10, 1, 11, "minecraft:smoker[facing=north,lit=false]");
        Furnish.sack(s, 11, 1, 11, true);
        for (int y = 1; y <= 5; y++) {
            put(s, 12, y, 11, Kit.ladder("west"));
        }
        hang(s, 11, 4, 10);
        // The royal chamber over the kitchen, warmed by the stack of the stove.
        Furnish.canopyBed(s, 11, 6, 10, "north", "spruce");
        Furnish.chest(s, 9, 6, 11, "east");
        Terem.redCorner(s, 9, 6, 7, "north", "west");
        Furnish.rug(s, 9, 9, 9, 10, 6, Kit.CLOTH_CARPET);
        hang(s, 9, 9, 9);
        // The treasury: iron bars before the chests and the gold, an iron door in them, a counting table.
        s.door(19, 1, 12, "minecraft:iron_door", "south", "left");
        box(s, 18, 1, 9, 21, 4, 9, Kit.BARS);
        s.door(20, 1, 9, "minecraft:iron_door", "south", "left");
        Furnish.chest(s, 18, 1, 7, "south");
        Furnish.chest(s, 21, 1, 7, "south");
        put(s, 19, 1, 7, Terem.GILT);
        put(s, 20, 1, 7, "minecraft:raw_gold_block");
        put(s, 19, 2, 7, Terem.GILT);
        put(s, 18, 1, 8, "minecraft:gold_block");
        put(s, 21, 1, 11, Kit.topSlab(Terem.FLOOR_SLAB));
        put(s, 21, 2, 11, Furnish.candles(2));
        put(s, 21, 1, 10, Kit.stairs(Terem.FLOOR_STAIRS, "north"));
        hang(s, 19, 4, 10);
        hang(s, 20, 4, 8);
        p.point(TREASURY, 19, 1, 13);
        // The council room over the treasury: a long table, benches, the red corner and candles.
        box(s, 19, 6, 8, 20, 6, 10, Kit.topSlab(Terem.FLOOR_SLAB));
        box(s, 18, 6, 8, 18, 6, 10, Kit.stairs(Terem.FLOOR_STAIRS, "west"));
        box(s, 21, 6, 8, 21, 6, 10, Kit.stairs(Terem.FLOOR_STAIRS, "east"));
        put(s, 19, 6, 11, Kit.stairs(Terem.CARVE_STAIRS, "south"));
        put(s, 19, 7, 9, Furnish.candles(3));
        put(s, 20, 7, 8, Furnish.candles(1));
        Terem.redCorner(s, 21, 6, 7, "north", "east");
        hang(s, 20, 9, 9);
    }

    /**
     * The palace of the medium and the great kremlin around the hall: two wings of two floors against its sides under steep
     * carved gables, with high porches to their upper floors (the medium kremlin has one, on the east wing); behind them
     * two terems of three floors, the western under a tall tent of shingles, the eastern under a barrel, each with a small
     * onion dome (in the great kremlin the north-east one is the bell tower), joined to the wings by roofed galleries.
     * The west wing holds the kitchen and the council room over it, the east wing the treasury (its iron door in the hall)
     * and the royal chambers.
     */
    private static void wings(Structure s, Plan p) {
        boolean large = p.large();
        int[] wing = large ? new int[]{9, 21, 16, 27} : new int[]{8, 17, 14, 23};
        int[] ter = large ? new int[]{9, 8, 15, 14} : new int[]{8, 8, 14, 14};
        int top = large ? 10 : 9;
        for (int side = -1; side <= 1; side += 2) {
            boolean west = side < 0;
            int x1 = west ? wing[0] : p.n - 1 - wing[2];
            int x2 = west ? wing[2] : p.n - 1 - wing[0];
            int z1 = wing[1];
            int z2 = wing[3];
            srub(s, x1, z1, x2, z2, top, 5);
            Terem.steepGable(s, x1, z1, x2, z2, top, true);
            // the roof ends against the hall
            int inner = west ? x2 : x1;
            box(s, inner - (west ? 0 : 2), top, z1 - 2, inner + (west ? 2 : 0), top + 12, z2 + 2, AIR);
            int mx = west ? (x1 + x2) / 2 + 1 : p.n - 2 - (wing[0] + wing[2]) / 2;
            if (large || !west) {
                kryltso(s, mx, 6, z2, "south", 1);
            }
            Terem.window(s, west ? x1 : x2, 7, (z1 + z2) / 2, west ? "west" : "east", 1, 2, true);
            Terem.window(s, west ? x1 : x2, 2, (z1 + z2) / 2, west ? "west" : "east", 1, 2, true);
            // the terem behind the wing and the gallery between them
            int tx1 = west ? ter[0] : p.n - 1 - ter[2];
            int tx2 = west ? ter[2] : p.n - 1 - ter[0];
            int gx = (tx1 + tx2) / 2;
            Terem.gallery(s, gx - 1, ter[3] + 1, gx + 1, z1 - 1, 6, 1, false);
            if (large && !west) {
                bellTower(s, p, tx1, ter[1], tx2, ter[3]);
            } else {
                terem(s, p, tx1, ter[1], tx2, ter[3], west);
            }
            s.door(gx, 6, ter[3], Terem.DOOR, "north", "left");
            s.door(gx, 6, z1, Terem.DOOR, "south", "left");
            int ix1 = x1 + 1;
            int ix2 = x2 - 1;
            if (west) {
                kitchen(s, ix1, z1 + 1, ix2, z2 - 1, 1, top + 5);
                s.door(mx, 1, z2, Terem.DOOR, "north", "left");
                council(s, ix1, z1 + 1, ix2, z2 - 1, 6);
            } else {
                treasury(s, ix1, z1 + 1, ix2, z2 - 1, 1);
                chamber(s, ix1, z1 + 1, ix2, z2 - 1, 6);
            }
            box(s, mx, 6, z2 - 1, mx, 7, z2 - 1, AIR);
        }
    }

    /**
     * A terem of three floors behind a wing: a log frame under a tall tent of shingles (west) or a barrel (east) with a small
     * onion dome; a door to the yard on the ground floor and a ladder up through all floors; windows in carved frames on
     * every floor. The western one holds the armoury, the chamber of the queen and a bright room for needlework at the top;
     * the eastern one a store, the library and a room to look out from.
     */
    private static void terem(Structure s, Plan p, int x1, int z1, int x2, int z2, boolean west) {
        int top = 14;
        srub(s, x1, z1, x2, z2, top, 5, 10);
        int mx = (x1 + x2) / 2;
        int mz = (z1 + z2) / 2;
        if (west) {
            int point = Kit.spire(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, top + 1, Terem.ROOF);
            Terem.onionDome(s, mx, point - 1, mz, 3);
        } else {
            int ridge = Terem.barrelRoof(s, x1 - 1, x2 + 1, mz, top, z2 - z1 + 3, true, Terem.ROOF, null, true, true);
            ridgeDome(s, mx, ridge, mz, 3);
        }
        String out = west ? "west" : "east";
        int ox = west ? x1 : x2;
        int ix = west ? x2 : x1;
        for (int y : new int[]{2, 7, 11}) {
            Terem.window(s, ox, y, mz, out, 1, 2, true);
        }
        Terem.window(s, mx, 11, z1, "north", 1, 2, true);
        Terem.window(s, mx, 7, z1, "north", 1, 2, true);
        s.door(ix, 1, mz, Terem.DOOR, west ? "west" : "east", "left");
        int lx = west ? x1 + 1 : x2 - 1;
        for (int y = 1; y <= 10; y++) {
            put(s, lx, y, z1 + 1, Kit.ladder("south"));
        }
        int a = x1 + 1;
        int b = x2 - 1;
        int c1 = z1 + 1;
        int c2 = z2 - 1;
        if (west) {
            // the armoury
            Furnish.weaponRack(s, b, 1, c1, "west", "spruce");
            put(s, b, 1, c1 + 1, "minecraft:anvil[facing=north]");
            Furnish.crate(s, a, 1, c2, "spruce");
            Furnish.crate(s, a + 1, 1, c2, "spruce");
            Furnish.crate(s, a, 2, c2, "spruce");
            put(s, b, 1, c2, "minecraft:smithing_table");
            put(s, a + 2, 3, c1, Kit.wallBanner("red", "south"));
            // the chamber of the queen
            Furnish.canopyBed(s, b - 1, 6, c2 - 1, "north", "spruce");
            Terem.chestPlace(s, a, 6, c2, "east");
            put(s, b, 8, c1, Kit.topSlab(Terem.FLOOR_SLAB));
            put(s, b, 9, c1, Furnish.candles(3));
            Furnish.rug(s, a + 1, c1 + 1, a + 2, c2, 6, Kit.CLOTH_CARPET);
            // the bright room: a table with cloth, benches under the windows, a spinning corner of wool
            Terem.feastTable(s, a + 1, mz, b - 1, mz, 11, false);
            Terem.lavka(s, a, c2, b, c2, 11);
            Furnish.sack(s, b, 11, c1, true);
            put(s, b, 12, c1, "minecraft:white_carpet");
        } else {
            // the store
            Furnish.crate(s, a, 1, c2, "spruce");
            Furnish.crate(s, a + 1, 1, c2, "spruce");
            Furnish.sack(s, b, 1, c2, false);
            Furnish.sack(s, b, 1, c2 - 1, true);
            Furnish.sack(s, b, 2, c2, false);
            put(s, a, 1, c1 + 2, "minecraft:hay_block[axis=y]");
            // the library
            Furnish.books(s, a, c1, a, c2, 6, 8);
            Furnish.desk(s, a + 2, 6, mz - 1, "south", "spruce");
            put(s, a + 1, 6, c1, Kit.lantern());
            // the room to look out from
            Terem.lavka(s, a, c2, b, c2, 11);
            Terem.lavka(s, b, c1, b, c2, 11);
        }
        hang(s, mx, 4, mz);
        hang(s, mx, 9, mz);
        hang(s, mx, 14, mz);
    }

    /**
     * The bell tower of the great kremlin: a square log frame of three floors, an eight-sided belfry with open arches and
     * bells, and a tall tent with a needle and a flag over it.
     */
    private static void bellTower(Structure s, Plan p, int x1, int z1, int x2, int z2) {
        int top = 14;
        srub(s, x1, z1, x2, z2, top, 5, 10);
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        Terem.plate(s, cx, top + 1, cz, 4, Terem.SQUARE, Kit.slab(Terem.ROOF.slab()));
        Terem.plate(s, cx, top + 1, cz, 3, Terem.SQUARE, Terem.FLOOR);
        Terem.octWalls(s, cx, top + 1, top + 5, cz, 3);
        for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            box(s, cx + d[0], top + 2, cz + d[1], cx + d[0], top + 4, cz + d[1], AIR);
            put(s, cx + d[0], top + 2, cz + d[1], Terem.CARVE_FENCE);
        }
        box(s, cx - 2, top + 2, cz - 1, cx + 2, top + 5, cz + 1, AIR);
        box(s, cx - 1, top + 2, cz - 2, cx + 1, top + 5, cz + 2, AIR);
        Terem.tent(s, Terem.EVERYWHERE, cx, cz, 4, top + 6, Terem.OCTAGON, Terem.OCTAGON, Terem.TENT_RISES, 0, -1);
        flag(s, cx, cz);
        put(s, cx, top + 5, cz, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        put(s, cx + 1, top + 5, cz, "minecraft:bell[attachment=ceiling,facing=east,powered=false]");
        put(s, cx - 1, top + 5, cz, "minecraft:bell[attachment=ceiling,facing=west,powered=false]");
        put(s, cx, top + 2, cz + 1, Kit.lantern());
        for (int y : new int[]{2, 7, 11}) {
            Terem.window(s, x2, y, cz, "east", 1, 2, true);
            Terem.window(s, cx, y, z1, "north", 1, 2, false);
        }
        s.door(x1, 1, cz, Terem.DOOR, "east", "left");
        for (int y = 1; y <= top - 1; y++) {
            put(s, x2 - 1, y, z1 + 1, Kit.ladder("south"));
        }
        Terem.lavka(s, x1 + 1, z2 - 1, x2 - 1, z2 - 1, 6);
        Terem.redCorner(s, x1 + 1, 6, z1 + 1, "north", "west");
        hang(s, cx, 4, cz);
        hang(s, cx, 9, cz);
        hang(s, cx, 13, cz);
    }

    // ---------------------------------------------------------------- rooms

    /**
     * The kitchen: the Russian stove in its corner with the stack through the roof, a smoker, a cauldron of water, a table
     * for cutting, sacks and crates, a lantern.
     */
    private static void kitchen(Structure s, int x1, int z1, int x2, int z2, int y, int stackTop) {
        Terem.stove(s, x1 + 1, y, z1 + 2, "south", stackTop);
        chimney(s, x1 + 1, z1 + 2, stackTop + 1, stackTop + 3);
        put(s, x2, y, z1, "minecraft:smoker[facing=west,lit=false]");
        put(s, x2, y, z1 + 1, "minecraft:water_cauldron[level=3]");
        put(s, x2, y, z2, Kit.topSlab(Terem.FLOOR_SLAB));
        put(s, x2, y + 1, z2, "minecraft:oak_pressure_plate[powered=false]");
        put(s, x2 - 1, y, z2, Kit.topSlab(Terem.FLOOR_SLAB));
        Furnish.sack(s, x1, y, z2, true);
        Furnish.sack(s, x1, y + 1, z2, false);
        Furnish.crate(s, x1 + 1, y, z2, "oak");
        hang(s, (x1 + x2) / 2 + 1, y + 3, (z1 + z2) / 2 + 1);
    }

    /**
     * The treasury: iron bars with an iron door across the far side, chests and the gold of the kingdom behind them, a
     * counting table with candles before them, lanterns.
     */
    private static void treasury(Structure s, int x1, int z1, int x2, int z2, int y) {
        int bars = x2 - 2;
        box(s, bars, y, z1, bars, y + 3, z2, Kit.BARS);
        s.door(bars, y, (z1 + z2) / 2, "minecraft:iron_door", "west", "left");
        Furnish.chest(s, x2, y, z1, "west");
        Furnish.chest(s, x2, y, z2, "west");
        for (int z = z1 + 1; z < z2; z++) {
            put(s, x2, y, z, (z % 2 == 0) ? Terem.GILT : "minecraft:raw_gold_block");
        }
        put(s, x2, y + 1, z1 + 1, Terem.GILT);
        put(s, x2 - 1, y, z1, "minecraft:gold_block");
        put(s, x1, y, z2, Kit.topSlab(Terem.FLOOR_SLAB));
        put(s, x1 + 1, y, z2, Kit.topSlab(Terem.FLOOR_SLAB));
        put(s, x1, y + 1, z2, Furnish.candles(3));
        put(s, x1 + 1, y + 1, z2, "minecraft:gold_block");
        put(s, x1 + 1, y, z2 - 1, Kit.stairs(Terem.FLOOR_STAIRS, "south"));
        hang(s, x1 + 1, y + 3, (z1 + z2) / 2);
        hang(s, x2 - 1, y + 3, (z1 + z2) / 2);
    }

    /**
     * The council room: a long table under cloth with benches on both sides and the carved seat of the king at its head,
     * the red corner, candles and a chandelier.
     */
    private static void council(Structure s, int x1, int z1, int x2, int z2, int y) {
        int mz = (z1 + z2) / 2;
        box(s, x1 + 2, y, mz, x2 - 1, y, mz, Kit.topSlab(Terem.FLOOR_SLAB));
        box(s, x1 + 2, y + 1, mz, x2 - 1, y + 1, mz, "minecraft:white_carpet");
        put(s, x1 + 2, y + 1, mz, Furnish.candles(3));
        put(s, x2 - 1, y + 1, mz, Furnish.candles(3));
        box(s, x1 + 2, y, mz - 1, x2 - 1, y, mz - 1, Kit.stairs(Terem.FLOOR_STAIRS, "north"));
        box(s, x1 + 2, y, mz + 1, x2 - 1, y, mz + 1, Kit.stairs(Terem.FLOOR_STAIRS, "south"));
        put(s, x2, y, mz, Kit.stairs(Terem.CARVE_STAIRS, "east"));
        put(s, x2, y + 2, mz, Kit.wallBanner("red", "west"));
        Terem.redCorner(s, x1, y, z1, "north", "west");
        horos(s, (x1 + x2) / 2 + 1, mz, y + 2);
    }

    /**
     * A chandelier of candles (horos) with its hub at layer {@code hub}, hanging on a chain from the first solid block above.
     */
    private static void horos(Structure s, int x, int z, int hub) {
        put(s, x, hub, z, Terem.CARVE_FENCE);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            put(s, x + d[0], hub, z + d[1], Terem.CARVE_FENCE);
            put(s, x + d[0], hub + 1, z + d[1], Furnish.candles(3));
        }
        for (int y = hub + 1; y < s.sizeY && !s.solid(x, y, z); y++) {
            s.set(x, y, z, Kit.chain());
        }
    }

    /**
     * The royal chamber: a bed of state under its canopy (no bed of the game: this is no home), the one chest, the red
     * corner, a rug, a bench under the window and a lantern.
     */
    private static void chamber(Structure s, int x1, int z1, int x2, int z2, int y) {
        int mx = (x1 + x2) / 2;
        Furnish.canopyBed(s, mx, y, z1 + 3, "north", "spruce");
        Terem.chestPlace(s, x1, y, z2 - 1, "east");
        Terem.redCorner(s, x2, y, z1, "north", "east");
        Furnish.rug(s, mx - 1, z1 + 4, mx + 1, z2, y, Kit.CLOTH_CARPET);
        Terem.lavka(s, x2, z1 + 2, x2, z2 - 1, y);
        hang(s, mx + 1, y + 3, z2 - 1);
    }

    // ---------------------------------------------------------------- the church

    /**
     * The church behind the hall, the highest point of the kremlin (the small kremlin has its tented bell tower instead):
     * an eight-sided log frame on a stone footing, its roof rising in tiers of onion domes on drums, in the manner of Kizhi.
     * The kremlin has four domes round a tall drum with the great dome; the great kremlin eight domes on the first tier,
     * four on an eight-sided second tier and the great dome over them, so that from the gate one sees five, three and one.
     * Inside, the iconostasis of gold and ochre with candles before it, and a chandelier of candles.
     */
    private static void church(Structure s, Plan p) {
        if (p.small()) {
            return;
        }
        boolean large = p.large();
        int cx = p.c;
        int cz = large ? 12 : 11;
        int r = large ? 5 : 3;
        int top = large ? 13 : 14;
        Terem.plate(s, cx, 0, cz, r + 1, Terem.OCTAGON, Terem.FOOTING);
        Terem.plate(s, cx, 0, cz, r - 1, Terem.OCTAGON, Terem.FLOOR);
        for (int y = 1; y <= top + 2; y++) {
            Terem.plate(s, cx, y, cz, r, Terem.OCTAGON, AIR);
        }
        Terem.octWalls(s, cx, 1, top, cz, r);
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Terem.inPlan(dx, dz, r, Terem.OCTAGON) && !Terem.inPlan(dx, dz, r - 1, Terem.OCTAGON)) {
                    put(s, cx + dx, 1, cz + dz, Terem.FOOTING);
                }
            }
        }
        // The first tier: a stepped roof and its domes.
        Terem.tent(s, Terem.EVERYWHERE, cx, cz, r + 1, top + 1, Terem.OCTAGON, Terem.OCTAGON, new int[]{1, 1}, 0, r - 1);
        int domeY = top + 3;
        for (int[] d : new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
            Terem.onionDome(s, cx + d[0], domeY, cz + d[1], 3, 1, Terem.PLANKS);
        }
        int mainY;
        if (large) {
            int d4 = r - 1;
            for (int[] d : new int[][]{{d4, d4}, {-d4, d4}, {d4, -d4}, {-d4, -d4}}) {
                Terem.onionDome(s, cx + d[0], domeY - 1, cz + d[1], 3, 2, Terem.PLANKS);
            }
            // The second tier: an eight-sided frame with four domes on its roof.
            int y1 = top + 4;
            int y2 = y1 + 6;
            for (int y = y1; y <= y2; y++) {
                Terem.plate(s, cx, y, cz, 2, Terem.OCTAGON, AIR);
            }
            Terem.octWalls(s, cx, y1, y2, cz, 3);
            for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                Terem.window(s, cx + d[0], y1 + 2, cz + d[1], d[0] > 0 ? "east" : d[0] < 0 ? "west" : d[1] > 0 ? "south" : "north", 1, 2, false);
            }
            Terem.tent(s, Terem.EVERYWHERE, cx, cz, 4, y2 + 1, Terem.OCTAGON, Terem.OCTAGON, new int[]{1, 1}, 0, 2);
            put(s, cx, y1, cz, Kit.lantern());
            for (int[] d : new int[][]{{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
                Terem.onionDome(s, cx + d[0], y2 + 2, cz + d[1], 3, 1, Terem.PLANKS);
            }
            mainY = y2 + 4;
        } else {
            mainY = top + 4;
        }
        Terem.onionDome(s, cx, mainY, cz, large ? 9 : 7, large ? 5 : 6, Kit.log(Terem.LOG, "y"));
        // Windows, the door to the south under a carved hood.
        for (String face : new String[]{"east", "west", "south"}) {
            int[] d = Furnish.step(face);
            Terem.window(s, cx + d[0] * r, 4, cz + d[1] * r, face, 1, 2, false);
            Terem.window(s, cx + d[0] * r, 9, cz + d[1] * r, face, 1, 2, false);
        }
        box(s, cx, 4, cz + r, cx, 6, cz + r + 1, AIR);
        s.door(cx, 1, cz + r, Terem.DOOR, "north", "left");
        put(s, cx - 1, 3, cz + r + 1, Kit.upside(Terem.CARVE_STAIRS, "west"));
        put(s, cx + 1, 3, cz + r + 1, Kit.upside(Terem.CARVE_STAIRS, "east"));
        put(s, cx, 4, cz + r + 1, Kit.slab(Terem.CARVE_SLAB));
        put(s, cx, 3, cz + r + 1, Kit.wallTorch("south"));
        // The iconostasis on the east side, candles before it, the chandelier.
        int ix = cx + r - 2;
        int iconTop = Math.min(top - 2, 6);
        for (int dz = -(r - 2); dz <= r - 2; dz++) {
            for (int y = 1; y <= iconTop; y++) {
                boolean icon = Math.floorMod(dz + y, 2) == 0;
                put(s, ix, y, cz + dz, icon ? "minecraft:yellow_terracotta" : "minecraft:orange_terracotta");
            }
            put(s, ix, iconTop + 1, cz + dz, Kit.slab(Terem.CARVE_SLAB));
        }
        put(s, ix, 1, cz, "minecraft:spruce_fence_gate[facing=west,in_wall=false,open=false,powered=false]");
        put(s, ix, 2, cz, Terem.GILT);
        put(s, ix, 3, cz, Terem.GILT);
        put(s, ix, 3, cz - 1, Terem.GILT);
        put(s, ix, 3, cz + 1, Terem.GILT);
        for (int dz : new int[]{-1, 1}) {
            put(s, ix - 1, 1, cz + dz * 2, Terem.CARVE_FENCE);
            put(s, ix - 1, 2, cz + dz * 2, Furnish.candles(3));
        }
        Terem.lavka(s, cx - r + 1, cz - 1, cx - r + 1, cz + 1, 1);
        horos(s, cx, cz, top - 4);
        put(s, cx - 1, 1, cz + r - 1, Kit.lantern());
    }

    // ---------------------------------------------------------------- the courtyard

    /**
     * The courtyard: a roofed well of logs, flower beds, birches, a log road from the gate to the porch of the hall and
     * lamps by the gate.
     */
    private static void courtyard(Structure s, Plan p) {
        int c = p.c;
        int[] well;
        int[][] birches;
        int[][] beds;
        switch (p.size) {
            case SMALL -> {
                well = new int[]{22, 16};
                birches = new int[][]{{10, 22}, {20, 22}};
                beds = new int[][]{{11, 21, 12, 23}, {18, 21, 19, 23}};
            }
            case MEDIUM -> {
                well = new int[]{11, 26};
                birches = new int[][]{{15, 30}, {24, 31}};
                beds = new int[][]{{10, 30, 13, 32}, {14, 31, 16, 32}};
            }
            default -> {
                well = new int[]{12, 38};
                birches = new int[][]{{17, 33}, {29, 33}};
                beds = new int[][]{{16, 36, 19, 38}, {27, 36, 30, 38}};
            }
        }
        well(s, well[0], well[1]);
        for (int[] b : birches) {
            birch(s, b[0], b[1], p.small() ? 1 : 2);
        }
        String[] flowers = {"minecraft:poppy", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:azure_bluet",
                "minecraft:allium", "minecraft:red_tulip", "minecraft:dandelion", "minecraft:lily_of_the_valley"};
        for (int[] b : beds) {
            int i = 0;
            for (int z = b[1]; z <= b[3]; z++) {
                for (int x = b[0]; x <= b[2]; x++) {
                    if (s.get(x, 1, z).equals(AIR) && s.get(x, 0, z).equals(GRASS)) {
                        put(s, x, 1, z, flowers[Math.floorMod(x * 3 + z * 5 + i, flowers.length)]);
                    }
                    i++;
                }
            }
        }
        // The road from the gate to the porch, lamps on both sides of the gate.
        int from = p.hz2 + (p.small() ? 3 : 4);
        box(s, c - 1, 0, from, c + 1, 0, p.gz, PATH);
        if (!p.small()) {
            for (int side = -1; side <= 1; side += 2) {
                int x = c + side * 3;
                int z = p.gz - (p.gw - 1) / 2 - 2;
                box(s, x, 1, z, x, 2, z, Terem.CARVE_FENCE);
                put(s, x, 3, z, Kit.lantern());
            }
        }
    }

    /**
     * A well: a frame of logs three by three round the water, carved posts at its corners and a little gable roof with a
     * lantern under its ridge.
     */
    private static void well(Structure s, int x, int z) {
        Terem.logWalls(s, x - 1, 1, z - 1, x + 1, 1, z + 1, Terem.LOG, false);
        put(s, x, 0, z, Terem.FOOTING);
        put(s, x, 1, z, "minecraft:water[level=0]");
        for (int[] d : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            box(s, x + d[0], 2, z + d[1], x + d[0], 3, z + d[1], Terem.CARVE_FENCE);
        }
        Terem.gableRoof(s, x - 1, z - 1, x + 1, z + 1, 4, true, 0, Terem.ROOF, Terem.PLANKS, true);
        put(s, x, 3, z, Kit.chain());
        put(s, x, 2, z, Kit.hangingLantern());
    }

    /**
     * A birch: a white trunk and a crown of leaves that never fall.
     */
    private static void birch(Structure s, int x, int z, int crown) {
        int h = 3 + crown * 2;
        box(s, x, 1, z, x, h, z, Kit.log(BIRCH_LOG, "y"));
        for (int y = h - crown * 2 + 1; y <= h + 1; y++) {
            int r = y == h + 1 ? 1 : y == h - crown * 2 + 1 ? Math.max(1, crown - 1) : crown;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.abs(dx) + Math.abs(dz) <= r + (crown > 1 ? 1 : 0) && s.inside(x + dx, y, z + dz) && !s.solid(x + dx, y, z + dz)) {
                        s.set(x + dx, y, z + dz, Kit.leaves("birch"));
                    }
                }
            }
        }
        put(s, x, h + 2, z, Kit.leaves("birch"));
    }
}
