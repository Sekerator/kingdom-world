package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle of the style "nordic": the hold of a jarl in the Scandinavian north, drawn with the shapes of {@link Nordic}.
 *
 * <p>Its one mass rises from the gate to the back. Before the throne lies the great hall, a longhouse of dark staves on a
 * plinth of rough stone whose long walls bow out like the hull of a ship, its ridge sagging towards gables with crossed
 * boards and dragon heads; inside, carved pillars, long hearths under smoke louvres, sleeping benches with furs and the
 * place of the high seat in the middle of the north wall, where the throne stands, as in the halls of the north; shields
 * hang along its walls outside. Behind the throne, joined to the hall, rises the dominant of the hold: a great tower built
 * like a stave church on a storey of stone, its dark roofs stacked in tiers (a skirt over the stone, the skirt of the aisles,
 * raised walls with small windows, a cross of steep gables with four dragon heads and a ridge turret with a belfry and a
 * needle), far above everything else. Its roofs are drawn as spires, so that a look makes it the darkest mass of the hold,
 * like the tarred churches of the north. A stone wall closes the yard: its walk runs open behind a hoarding of staves on
 * corbels under a small roof of shingles, with a parapet and lamps on its inner edge; few round towers of stone, each of its
 * own size, stand at its corners under flared cones over timber lookouts, the other corners are closed by the hoardings; in
 * the middle of the front the gate tower: a vaulted passage in stone and above it the armoury in a timber storey under a
 * steep roof with dragon heads at both ends and a gable with crossed boards looking down the street.
 *
 * <p>In the yard: the cookhouse under a roof of turf, the lodge of the jarl of boards painted in the colour of the kingdom
 * with white trims (in the medium and great holds; the small hold keeps the chambers in a round tower), a well and
 * runestones. The great tower holds the treasury behind iron bars on its ground floor and the council room above it.
 *
 * <p>One program drawn in three sizes: small 31 by 31, medium 41 by 41 and great 47 by 47. The throne stands in the middle
 * of the ground on the street layer facing south and is left out; layer 0 replaces the ground and layer 1 is level with
 * the street. Every part is drawn with the blocks that stand for the parts of a look (see {@link Kit}).
 */
final class NordicCastle {
    private static final String AIR = Structure.AIR;
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String STAVE = Kit.log(Nordic.STAVE, "y");
    private static final String POST = Kit.log(Nordic.LOG, "y");
    private static final int NONE = Nordic.NONE;

    /** The three sizes of the hold. */
    enum Size {
        SMALL, MEDIUM, LARGE
    }

    private NordicCastle() {
    }

    /**
     * The three holds of a jarl: small, medium and great, with their named places.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(building(Size.SMALL, "nordic_castle_small", "minecraft:spruce_log"));
        list.add(building(Size.MEDIUM, "nordic_castle_medium", "minecraft:dark_oak_log"));
        list.add(building(Size.LARGE, "nordic_castle_large", "minecraft:gold_block"));
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
     * The numbers of a hold of a size.
     */
    private static final class Plan {
        final Size size;
        /** The ground (square), the height of the box and its middle (the cell of the throne). */
        final int n;
        final int height;
        final int c;
        /** The outer face of the curtain walls from every edge, and the layer one stands on on their walk. */
        final int w;
        final int walk;
        /** The hall: its gable walls on x, its middle line on z, its half-widths at the gables and in the middle, its plate. */
        final int hx1;
        final int hx2;
        final int hz;
        final int hend;
        final int hmid;
        final int hplate;
        /** Layers of rough stone of the plinth of the hall and the great tower. */
        final int plinth;
        /** The great tower: its walls, the plate of its walls, the floor of its upper storey, the inset and height of its raised walls. */
        final int tx1;
        final int tx2;
        final int tz1;
        final int tz2;
        final int tplate;
        final int tstorey;
        final int tinset;
        final int tclere;
        /** The stone of the gate tower and the plate of its timber storey. */
        final int gx1;
        final int gx2;
        final int gz1;
        final int gz2;
        final int gplate;
        /** The round towers: {x, z, diameter, height}; index 0 the north-west one. */
        final int[][] towers;
        /** The corners without a tower: north-west, north-east, south-west, south-east. */
        final boolean[] bare;
        /** The cookhouse and (not in the small hold) the lodge: {x1, z1, x2, z2}. */
        final int[] cook;
        final int[] lodge;
        /** Named places: {kind, x, y, z}. */
        final List<int[]> points = new ArrayList<>();

        Plan(Size size) {
            this.size = size;
            switch (size) {
                case SMALL -> {
                    n = 31;
                    height = 40;
                    w = 2;
                    walk = 6;
                    hx1 = 8;
                    hx2 = 22;
                    hz = 17;
                    hend = 3;
                    hmid = 4;
                    hplate = 5;
                    plinth = 2;
                    tx1 = 11;
                    tx2 = 19;
                    tz1 = 5;
                    tz2 = 13;
                    tplate = 11;
                    tstorey = 5;
                    tinset = 1;
                    tclere = 4;
                    gx1 = 12;
                    gx2 = 18;
                    gz1 = 25;
                    gz2 = 28;
                    gplate = 12;
                    towers = new int[][]{{5, 5, 7, 11}, {25, 25, 7, 9}};
                    bare = new boolean[]{false, true, true, false};
                    cook = new int[]{21, 5, 24, 10};
                    lodge = null;
                }
                case MEDIUM -> {
                    n = 41;
                    height = 44;
                    w = 3;
                    walk = 7;
                    hx1 = 11;
                    hx2 = 29;
                    hz = 22;
                    hend = 3;
                    hmid = 4;
                    hplate = 6;
                    plinth = 2;
                    tx1 = 15;
                    tx2 = 25;
                    tz1 = 8;
                    tz2 = 18;
                    tplate = 14;
                    tstorey = 7;
                    tinset = 1;
                    tclere = 4;
                    gx1 = 17;
                    gx2 = 23;
                    gz1 = 34;
                    gz2 = 37;
                    gplate = 13;
                    towers = new int[][]{{6, 6, 9, 11}, {35, 5, 7, 10}};
                    bare = new boolean[]{false, false, true, true};
                    cook = new int[]{9, 29, 14, 33};
                    lodge = new int[]{26, 29, 32, 33};
                }
                default -> {
                    n = 47;
                    height = 48;
                    w = 4;
                    walk = 7;
                    hx1 = 13;
                    hx2 = 33;
                    hz = 25;
                    hend = 3;
                    hmid = 4;
                    hplate = 7;
                    plinth = 3;
                    tx1 = 17;
                    tx2 = 29;
                    tz1 = 9;
                    tz2 = 21;
                    tplate = 15;
                    tstorey = 8;
                    tinset = 1;
                    tclere = 4;
                    gx1 = 19;
                    gx2 = 27;
                    gz1 = 39;
                    gz2 = 42;
                    gplate = 13;
                    towers = new int[][]{{6, 6, 9, 13}, {40, 6, 7, 12}, {6, 40, 7, 10}};
                    bare = new boolean[]{false, false, false, true};
                    cook = new int[]{10, 31, 17, 37};
                    lodge = new int[]{29, 31, 37, 37};
                }
            }
            c = (n - 1) / 2;
        }

        boolean small() {
            return size == Size.SMALL;
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

    /** Whether a block is one of a roof: its full block, stairs or slab. */
    private static boolean roofBlock(String state, Kit.Roof roof) {
        return state.equals(roof.block()) || state.startsWith(roof.stairs() + "[") || state.startsWith(roof.slab() + "[");
    }

    /** The higher of two height fields wherever either reaches. */
    private static Nordic.Field max(Nordic.Field a, Nordic.Field b) {
        return (x, z) -> {
            int u = a.at(x, z);
            int v = b.at(x, z);
            return u == NONE ? v : v == NONE ? u : Math.max(u, v);
        };
    }

    // ---------------------------------------------------------------- the hold

    /**
     * Draws the hold of a plan: the ground, the walls with their towers and gate, the hall and the great tower, the houses
     * of the yard and the yard itself.
     */
    private static Structure castle(Plan p) {
        Structure s = new Structure(p.n, p.height, p.n);
        box(s, 0, 0, 0, p.n - 1, 0, p.n - 1, GRASS);
        walls(s, p);
        gate(s, p);
        hall(s, p);
        greatTower(s, p);
        hallRoof(s, p);
        hallInside(s, p);
        cookhouse(s, p);
        if (p.lodge != null) {
            lodge(s, p);
        }
        yard(s, p);
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

    // ---------------------------------------------------------------- the walls

    /**
     * The roof field of the hoarding of a curtain wall on the line {@code c} (its outer face) whose court lies on the side
     * {@code dc}: a small gable over the hoarding, its eaves out over the corbels and in over the walk, the same all along
     * the wall.
     */
    private static Nordic.Field curtainField(boolean alongX, int c, int dc, int walk) {
        int line = c - dc;
        return Nordic.gableField(alongX ? -1000 : line, alongX ? line : -1000, alongX ? 1000 : line, alongX ? line : 1000, walk + 2, alongX,
                Nordic.PITCH);
    }

    /**
     * A curtain wall of the style along x or z from {@code a1} to {@code a2} on the line {@code c}, its court on the side
     * {@code dc}: three blocks of stone (faces of the wall stone, a core of rough stone) up to the walk; a hoarding of staves
     * on stone corbels jutting out over the outer face, two layers high with loopholes every three blocks, under a small
     * gable of shingles; the walk open to the sky behind it with a parapet of stone on its inner edge and lanterns on it (see
     * {@link Nordic#curtain}; here lighter, so that the stone of the walls shows, and without its ladder: stairs lead up).
     */
    private static void curtain(Structure s, Plan p, boolean alongX, int a1, int a2, int c, int dc) {
        int walk = p.walk;
        String lying = alongX ? "x" : "z";
        String inward = across(alongX, dc);
        for (int a = a1; a <= a2; a++) {
            for (int k = 0; k <= 2; k++) {
                at(s, alongX, a, 0, c + k * dc, Nordic.FOOTING);
                for (int yy = 1; yy < walk; yy++) {
                    at(s, alongX, a, yy, c + k * dc, k == 1 ? Nordic.FOOTING : Nordic.STONE);
                }
                for (int yy = walk; yy <= walk + 4; yy++) {
                    at(s, alongX, a, yy, c + k * dc, AIR);
                }
            }
            boolean bay = Math.floorMod(a, 3) == 1;
            at(s, alongX, a, walk - 1, c - dc, Kit.upside(Nordic.STONE_STAIRS, inward));
            at(s, alongX, a, walk, c - dc, STAVE);
            at(s, alongX, a, walk + 1, c - dc, bay ? Nordic.FENCE : STAVE);
            at(s, alongX, a, walk + 2, c - dc, Kit.log(Nordic.LOG, lying));
            at(s, alongX, a, walk, c + 2 * dc, Kit.WALL_FENCE);
            if (Math.floorMod(a, 4) == 2) {
                at(s, alongX, a, walk + 1, c + 2 * dc, Kit.lantern());
            }
        }
        Nordic.Field f = curtainField(alongX, c, dc, walk);
        int lo = Math.min(c - 2 * dc, c);
        int hi = Math.max(c - 2 * dc, c);
        Nordic.Area within = (x, z) -> {
            int a = alongX ? x : z;
            return a >= a1 && a <= a2;
        };
        Nordic.roof(s, f, within, alongX ? a1 : lo, alongX ? lo : a1, alongX ? a2 : hi, alongX ? hi : a2, Nordic.ROOF);
    }

    /**
     * A flight of stone stairs up the court face of a curtain wall to its walk: it starts at {@code a0} on the ground and
     * climbs along the wall the way of {@code dir}, every step on a wall of stone.
     */
    private static void wallStair(Structure s, Plan p, boolean alongX, int c, int dc, int a0, int dir) {
        int line = c + 3 * dc;
        String up = along(alongX, dir);
        for (int i = 0; i < p.walk - 1; i++) {
            int a = a0 + i * dir;
            for (int yy = 1; yy <= i; yy++) {
                at(s, alongX, a, yy, line, Nordic.STONE);
            }
            at(s, alongX, a, i + 1, line, Kit.stairs(Nordic.STONE_STAIRS, up));
            at(s, alongX, a, i + 2, line, AIR);
            at(s, alongX, a, i + 3, line, AIR);
        }
        // The cell of the walk where the stair arrives stays free of posts.
        int top = a0 + (p.walk - 2) * dir;
        at(s, alongX, top, p.walk, c + 2 * dc, AIR);
        at(s, alongX, top, p.walk + 1, c + 2 * dc, AIR);
        at(s, alongX, top + dir, p.walk, c + 2 * dc, AIR);
        at(s, alongX, top + dir, p.walk + 1, c + 2 * dc, AIR);
    }

    /**
     * A corner of the walls without a tower: the two curtains meet in a square of stone, their hoardings turn the corner on a
     * post and their roofs join in a hip outside and a valley inside.
     *
     * @param west  whether it is a corner on the west side
     * @param north whether it is a corner on the north side
     */
    private static void bareCorner(Structure s, Plan p, boolean west, boolean north) {
        int xc = west ? p.w : p.n - 1 - p.w;
        int zc = north ? p.w : p.n - 1 - p.w;
        int dx = west ? 1 : -1;
        int dz = north ? 1 : -1;
        int walk = p.walk;
        Nordic.Field fa = curtainField(true, zc, dz, walk);
        Nordic.Field fb = curtainField(false, xc, dx, walk);
        Nordic.Field f = max(fa, fb);
        int x1 = Math.min(xc - 2 * dx, xc);
        int x2 = Math.max(xc - 2 * dx, xc);
        int z1 = Math.min(zc - 2 * dz, zc);
        int z2 = Math.max(zc - 2 * dz, zc);
        for (int y = walk + 2; y <= walk + 8; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    if (s.inside(x, y, z) && roofBlock(s.get(x, y, z), Nordic.ROOF)) {
                        s.set(x, y, z, AIR);
                    }
                }
            }
        }
        // The hoarding turns the corner on a post.
        int hx = xc - dx;
        int hz = zc - dz;
        put(s, hx, walk - 1, hz, Nordic.FOOTING);
        put(s, hx, walk, hz, POST);
        put(s, hx, walk + 1, hz, POST);
        put(s, hx, walk + 2, hz, POST);
        Nordic.roof(s, f, Nordic.EVERYWHERE, x1, z1, x2, z2, Nordic.ROOF);
        // The walks run on round the corner: the parapets of each wall end where they cross the walk of the other.
        for (int k = 0; k <= 1; k++) {
            put(s, xc + k * dx, walk, zc + 2 * dz, AIR);
            put(s, xc + 2 * dx, walk, zc + k * dz, AIR);
        }
        put(s, xc + 2 * dx, walk + 1, zc + 2 * dz, Kit.lantern());
    }

    /**
     * The walls of the hold: four curtains, the round towers at their corners, the bare corners, stairs up to the walks and
     * the places of the guards on them.
     */
    private static void walls(Structure s, Plan p) {
        int lo = p.w;
        int hi = p.n - 1 - p.w;
        curtain(s, p, true, lo, hi, lo, 1);
        curtain(s, p, true, lo, hi, hi, -1);
        curtain(s, p, false, lo, hi, lo, 1);
        curtain(s, p, false, lo, hi, hi, -1);
        boolean[][] corners = {{true, true}, {false, true}, {true, false}, {false, false}};
        for (int i = 0; i < 4; i++) {
            if (p.bare[i]) {
                bareCorner(s, p, corners[i][0], corners[i][1]);
            }
        }
        // Stairs up to the walks of the west and the east wall, climbing from the front towards the back.
        int start = hi - (p.small() ? 8 : 7);
        wallStair(s, p, false, lo, 1, start, -1);
        wallStair(s, p, false, hi, -1, start, -1);
        for (int i = 0; i < p.towers.length; i++) {
            int[] t = p.towers[i];
            roundTower(s, p, t[0], t[2], t[1], t[3]);
            towerRoom(s, p, t[0], t[2], t[1], i == 0 && p.small() ? 0 : i == 0 ? 1 : 2);
        }
        // The guards of the walls: two on every walk, where they can stand.
        int mid = p.w + 1;
        int far = p.n - 1 - mid;
        int k = (p.n - 1) / 4;
        int[][] guards = {{mid, p.c - 3}, {far, p.c - 3}, {mid, p.c + 5}, {far, p.c + 5}, {p.c - k, far}, {p.c + k, far},
                {p.c - k, mid}, {p.c + k, mid}};
        for (int[] g : guards) {
            p.point(WALL, g[0], p.walk, g[1]);
        }
    }

    // ---------------------------------------------------------------- round towers

    /**
     * A round tower of stone at a corner of the walls (see {@link Nordic#roundTower}): a plinth with a sloping skirt, a round
     * wall of the wall stone with a floor on the level of the walks and doors out to them, a ladder inside, slits and
     * lanterns, and on top a timber lookout jutting out over stone corbels under a flared cone of shingles with a vane.
     * The cone is lower than that of the shape of the style, so that the towers stay well below the great tower.
     */
    private static void roundTower(Structure s, Plan p, int cx, int diameter, int cz, int height) {
        int r = (diameter - 1) / 2;
        int top = height;
        // Clear what the curtains left inside it.
        for (int y = 1; y <= top + 4; y++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                for (int dx = -r - 2; dx <= r + 2; dx++) {
                    if (Kit.inDisc(dx, dz, diameter + 2) && (y >= top - 1 || Kit.inDisc(dx, dz, diameter))) {
                        put(s, cx + dx, y, cz + dz, AIR);
                    }
                }
            }
        }
        Kit.disc(s, cx, 0, cz, diameter + 2, Nordic.FOOTING);
        int rr = r + 1;
        for (int dz = -rr; dz <= rr; dz++) {
            for (int dx = -rr; dx <= rr; dx++) {
                if (Kit.onRim(dx, dz, diameter + 2)) {
                    putFree(s, cx + dx, 1, cz + dz, Kit.stairs(Nordic.FOOTING_STAIRS, inward(dx, dz)));
                    put(s, cx + dx, top - 1, cz + dz, Kit.upside(Nordic.STONE_STAIRS, inward(dx, dz)));
                }
            }
        }
        Kit.tube(s, cx, 1, top - 1, cz, diameter, Nordic.STONE);
        int[] floors = {0, p.walk - 1};
        for (int f : floors) {
            Kit.disc(s, cx, f, cz, diameter - 2, Nordic.FLOOR);
            put(s, cx, f + 2, cz + r, Kit.GLASS);
            put(s, cx, f + 2, cz - r, Kit.GLASS);
            put(s, cx + r, f + 2, cz, Kit.GLASS);
            put(s, cx - r, f + 2, cz, Kit.GLASS);
        }
        Kit.disc(s, cx, top, cz, diameter + 2, Nordic.FLOOR);
        put(s, cx + 1, p.walk - 2, cz + 1, Kit.hangingLantern());
        if (r > 3) {
            put(s, cx - 2, p.walk - 2, cz - 2, Kit.hangingLantern());
        }
        hang(s, cx + 1, Math.min(p.walk + 2, top - 1), cz + 1);
        for (int dz = -rr; dz <= rr; dz++) {
            for (int dx = -rr; dx <= rr; dx++) {
                if (!Kit.onRim(dx, dz, diameter + 2)) {
                    continue;
                }
                boolean axis = dx == 0 || dz == 0;
                for (int yy = top + 1; yy <= top + 3; yy++) {
                    String state = yy == top + 3 ? Nordic.PLANKS : axis && yy == top + 2 ? Nordic.FENCE : axis ? POST : STAVE;
                    put(s, cx + dx, yy, cz + dz, state);
                }
            }
        }
        put(s, cx, top + 1, cz + 1, Kit.lantern());
        // A ladder along the inside, against the wall that looks to the court.
        int ldz = cz < p.c ? r - 1 : -r + 1;
        String ladder = cz < p.c ? "north" : "south";
        for (int yy = 1; yy <= top; yy++) {
            put(s, cx, yy, cz + ldz, Kit.ladder(ladder));
        }
        Nordic.cone(s, cx, top + 4, cz, diameter + 4, new int[]{1, 1, 1, 2, 2, 2}, Nordic.SPIRE);
        // Doors out to the walks: where the middle lines of the walks cross the round wall.
        int[] lines = {p.w + 1, p.n - 2 - p.w};
        for (int line : lines) {
            for (int dx = -r; dx <= r; dx++) {
                int dz = line - cz;
                if (Kit.onRim(dx, dz, diameter) && Math.abs(dx) >= Math.abs(dz)) {
                    put(s, cx + dx, p.walk, line, AIR);
                    put(s, cx + dx, p.walk + 1, line, AIR);
                }
            }
            for (int dz = -r; dz <= r; dz++) {
                int dx = line - cx;
                if (Kit.onRim(dx, dz, diameter) && Math.abs(dz) >= Math.abs(dx)) {
                    put(s, line, p.walk, cz + dz, AIR);
                    put(s, line, p.walk + 1, cz + dz, AIR);
                }
            }
        }
    }

    /**
     * The ground floor of a round tower, entered by a door that looks across the yard: the chambers of the jarl in the small
     * hold (a bed of state, a chest, a table with candles, furs and a hanging), the guard room with its weapons (kind 1) or a
     * store of crates, sacks and hay (kind 2).
     */
    private static void towerRoom(Structure s, Plan p, int cx, int diameter, int cz, int kind) {
        int r = (diameter - 1) / 2;
        int sx = cx < p.c ? 1 : -1;
        int sz = cz < p.c ? 1 : -1;
        int dx = cx + sx * (r - 1);
        int dz = cz + sz * (r - 1);
        String in = sx > 0 ? "west" : "east";
        s.door(dx, 1, dz, Nordic.DOOR, in, "left");
        put(s, dx + sx, 1, dz, AIR);
        put(s, dx + sx, 2, dz, AIR);
        put(s, dx + sx, 0, dz, Nordic.FOOTING);
        put(s, dx + sx, 3, dz, Kit.wallTorch(sx > 0 ? "east" : "west"));
        int back = -sz;
        int side = -sx;
        switch (kind) {
            case 0 -> {
                Furnish.canopyBed(s, cx, 1, cz + sz, sz > 0 ? "north" : "south", "spruce");
                hang(s, cx + side, p.walk - 2, cz + 2 * sz);
                Furnish.chest(s, cx + side * 2, 1, cz, sx > 0 ? "east" : "west");
                put(s, cx + side, 1, cz + 2 * sz, Nordic.FURS[0]);
                put(s, cx + side * 2, 1, cz + back, Kit.topSlab(Nordic.FLOOR_SLAB));
                put(s, cx + side * 2, 2, cz + back, Furnish.candles(2));
                put(s, cx - side * 2, 3, cz, Kit.wallBanner("red", sx > 0 ? "west" : "east"));
            }
            case 1 -> {
                for (int k = -1; k <= 1; k++) {
                    Furnish.weaponRack(s, cx + side * (r - 1), 1, cz + k, sx > 0 ? "east" : "west", "spruce");
                }
                put(s, cx, 1, cz, Nordic.FENCE);
                put(s, cx, 2, cz, "minecraft:spruce_pressure_plate[powered=false]");
                Furnish.chair(s, cx + sx, 1, cz, sx > 0 ? "west" : "east", "spruce");
                Furnish.chair(s, cx, 1, cz + back, sz > 0 ? "south" : "north", "spruce");
                Furnish.crate(s, cx + side, 1, cz + back * (r - 1), "spruce");
                put(s, cx + side * (r - 1), 3, cz, Kit.wallBanner("red", sx > 0 ? "east" : "west"));
                put(s, cx + side * (r - 1), 3, cz + 1, Kit.wallBanner("white", sx > 0 ? "east" : "west"));
            }
            default -> {
                Furnish.crate(s, cx + side * (r - 1), 1, cz, "spruce");
                Furnish.crate(s, cx + side * (r - 1), 2, cz, "spruce");
                Furnish.crate(s, cx + side * (r - 1), 1, cz + back, "spruce");
                Furnish.sack(s, cx + side, 1, cz + back * (r - 1), false);
                Furnish.sack(s, cx, 1, cz + back * (r - 1), true);
                put(s, cx + side * (r - 1), 1, cz - back, "minecraft:hay_block[axis=y]");
            }
        }
    }

    /** The side of a column of a round plan that looks to its middle. */
    private static String inward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? "west" : "east";
        }
        return dz > 0 ? "north" : "south";
    }

    // ---------------------------------------------------------------- the gate

    /**
     * The gate tower in the middle of the front: a block of stone with a vaulted passage through it, gate leaves standing
     * open against its sides, lanterns; above it a timber storey jutting out over stone corbels to the street and to the
     * sides, the armoury of the guards, reached from the walks; over it a steep roof whose ridge runs along the front with
     * crossed boards and dragon heads at both ends, crossed by a gable with crossed boards and windows looking down the street.
     */
    private static void gate(Structure s, Plan p) {
        int c = p.c;
        int walk = p.walk;
        int x1 = p.gx1;
        int x2 = p.gx2;
        int z1 = p.gz1;
        int z2 = p.gz2;
        box(s, x1, 1, z1 - 1, x2, walk + 6, z2 + 2, AIR);
        box(s, x1 - 1, walk, z1 - 1, x2 + 1, walk + 6, z2 + 2, AIR);
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                boolean rim = x == x1 || x == x2 || z == z1 || z == z2;
                put(s, x, 0, z, Nordic.FOOTING);
                for (int y = 1; y < walk; y++) {
                    put(s, x, y, z, rim ? Nordic.STONE : Nordic.FOOTING);
                }
            }
        }
        // The passage with its vault.
        for (int z = z1; z <= z2; z++) {
            box(s, c - 1, 1, z, c + 1, 2, z, AIR);
            put(s, c, 3, z, AIR);
            put(s, c - 1, 3, z, Kit.upside(Nordic.STONE_STAIRS, "east"));
            put(s, c + 1, 3, z, Kit.upside(Nordic.STONE_STAIRS, "west"));
        }
        // Quoins of rough stone at the corners of the front, a carved stone over the passage.
        for (int y = 1; y < walk; y++) {
            put(s, x1, y, z2, y % 2 == 0 ? Nordic.FOOTING : Nordic.STONE);
            put(s, x2, y, z2, y % 2 == 0 ? Nordic.FOOTING : Nordic.STONE);
        }
        put(s, c, 4, z2, Kit.CARVED);
        // The leaves of the gate stand open against the sides of the passage.
        s.set(c - 1, 1, z1 + 1, Nordic.DOOR + "[facing=north,half=lower,hinge=left,open=true,powered=false]");
        s.set(c - 1, 2, z1 + 1, Nordic.DOOR + "[facing=north,half=upper,hinge=left,open=true,powered=false]");
        s.set(c + 1, 1, z1 + 1, Nordic.DOOR + "[facing=north,half=lower,hinge=right,open=true,powered=false]");
        s.set(c + 1, 2, z1 + 1, Nordic.DOOR + "[facing=north,half=upper,hinge=right,open=true,powered=false]");
        put(s, c - 1, 2, z2 - 1, Kit.wallTorch("east"));
        put(s, c + 1, 2, z2 - 1, Kit.wallTorch("west"));
        p.point(GATE, c - 1, 1, z1 + 2);
        p.point(GATE, c + 1, 1, z1 + 2);
        // The timber storey: out one block over corbels to the sides and to the street.
        int sx1 = x1 - 1;
        int sx2 = x2 + 1;
        int sz1 = z1;
        int sz2 = z2 + 1;
        for (int x = sx1; x <= sx2; x++) {
            put(s, x, walk - 1, sz2, Kit.upside(Nordic.STONE_STAIRS, "north"));
        }
        for (int z = sz1; z < sz2; z++) {
            put(s, sx1, walk - 1, z, Kit.upside(Nordic.STONE_STAIRS, "east"));
            put(s, sx2, walk - 1, z, Kit.upside(Nordic.STONE_STAIRS, "west"));
        }
        box(s, sx1 + 1, walk - 1, sz1 + 1, sx2 - 1, walk - 1, sz2 - 1, Nordic.FLOOR);
        Nordic.staveWalls(s, sx1, walk, sz1, sx2, p.gplate, sz2);
        box(s, sx1 + 1, walk, sz1 + 1, sx2 - 1, p.gplate, sz2 - 1, AIR);
        // The roof: a steep ridge along the front with dragon heads at both ends, crossed by a gable that looks down the
        // street with its crossed boards.
        int mz = (sz1 + sz2) / 2;
        Nordic.Field main = Nordic.gableField(sx1, sz1, sx2, sz2, p.gplate, true, Nordic.STEEP);
        Nordic.Field cross = Nordic.gableField(c - 2, mz, c + 2, sz2, p.gplate, false, Nordic.STEEP);
        Nordic.Field front = (x, z) -> z >= mz ? cross.at(x, z) : NONE;
        Nordic.Field f = max(main, front);
        Nordic.roof(s, f, Nordic.EVERYWHERE, sx1 - 1, sz1 - 1, sx2 + 1, sz2 + 1, Nordic.ROOF);
        Nordic.gableWall(s, f, true, sx1, sz1, sz2, p.gplate + 1, STAVE);
        Nordic.gableWall(s, f, true, sx2, sz1, sz2, p.gplate + 1, STAVE);
        Nordic.gableWall(s, f, false, sz2, c - 2, c + 2, p.gplate + 1, STAVE);
        Nordic.crossedBoards(s, true, sx1 - 1, sz1 - 1, sz2 + 1, Nordic.ROOF, Nordic.BOARD, true);
        Nordic.crossedBoards(s, true, sx2 + 1, sz1 - 1, sz2 + 1, Nordic.ROOF, Nordic.BOARD, true);
        Nordic.crossedBoards(s, false, sz2 + 1, c - 3, c + 3, Nordic.ROOF, Nordic.BOARD, true);
        int ridge = main.at(sx1, mz);
        Nordic.dragonHead(s, sx1 - 1, ridge + 1, mz, "west", Nordic.SPIRE);
        Nordic.dragonHead(s, sx2 + 1, ridge + 1, mz, "east", Nordic.SPIRE);
        Nordic.window(s, c, walk + 1, sz2, "south", 2, false);
        Nordic.window(s, c, p.gplate + 1, sz2, "south", 2, false);
        Nordic.window(s, c - 3, walk + 1, sz2, "south", 1, false);
        Nordic.window(s, c + 3, walk + 1, sz2, "south", 1, false);
        // Doors from the walks into the armoury.
        int wz = p.n - 2 - p.w;
        for (int x : new int[]{sx1, sx2}) {
            put(s, x, walk, wz, AIR);
            put(s, x, walk + 1, wz, AIR);
        }
        // The armoury: racks of weapons along the back, shields of cloth on the walls, a table, crates and light.
        for (int x = sx1 + 2; x <= sx2 - 2; x++) {
            if (Math.abs(x - c) == 2) {
                Furnish.weaponRack(s, x, walk, sz1 + 1, "south", "spruce");
            } else if (Math.abs(x - c) >= 3) {
                Furnish.crate(s, x, walk, sz1 + 1, "spruce");
            }
        }
        put(s, c, walk, sz1 + 1, Nordic.FENCE);
        put(s, c, walk + 1, sz1 + 1, Nordic.FENCE);
        put(s, c - 1, walk + 2, sz1 + 1, Kit.wallBanner("red", "south"));
        put(s, c + 1, walk + 2, sz1 + 1, Kit.wallBanner("red", "south"));
        put(s, c, walk + 2, sz1 + 1, Kit.wallBanner("white", "south"));
        Furnish.longTable(s, c - 1, sz2 - 2, c + 1, sz2 - 2, walk, "spruce");
        put(s, c, walk + 1, sz2 - 2, Furnish.candles(3));
        hang(s, c - 2, walk + 3, (sz1 + sz2) / 2);
        hang(s, c + 2, walk + 3, (sz1 + sz2) / 2);
        // Banners of the kingdom on both sides of the gate.
        for (int side = -1; side <= 1; side += 2) {
            put(s, c + side * 2, 3, z2 + 1, Kit.wallBanner("red", "south"));
        }
    }

    // ---------------------------------------------------------------- the hall

    /** The half-width of the hall at a column along its length. */
    private static int hw(Plan p, int x) {
        return Nordic.hullWidth(Math.max(p.hx1, Math.min(p.hx2, x)), p.hx1, p.hx2, p.hend, p.hmid);
    }

    private static boolean inHall(Plan p, int x, int z) {
        return x >= p.hx1 && x <= p.hx2 && Math.abs(z - p.hz) <= hw(p, x);
    }

    /**
     * The walls of the great hall (see {@link Nordic#longhouse}): along x, its long walls bowing out like a hull, standing on
     * a plinth of rough stone {@code plinth} layers high with a sloping skirt, then a sill, staves between posts and a plate;
     * a floor of boards inside.
     */
    private static void hall(Structure s, Plan p) {
        int top = p.hplate;
        for (int x = p.hx1; x <= p.hx2; x++) {
            int w = hw(p, x);
            for (int z = p.hz - w; z <= p.hz + w; z++) {
                boolean rim = !inHall(p, x + 1, z) || !inHall(p, x - 1, z) || !inHall(p, x, z + 1) || !inHall(p, x, z - 1);
                if (!rim) {
                    put(s, x, 0, z, Nordic.FLOOR);
                    box(s, x, 1, z, x, top + 12, z, AIR);
                    continue;
                }
                put(s, x, 0, z, Nordic.FOOTING);
                boolean gableEnd = x == p.hx1 || x == p.hx2;
                boolean step = !gableEnd && (Math.abs(z - p.hz) < w || hw(p, x - 1) != w || hw(p, x + 1) != w);
                boolean corner = gableEnd && Math.abs(z - p.hz) == w;
                boolean post = corner || step || !gableEnd && Math.floorMod(x - p.c, 4) == 0
                        || gableEnd && Math.floorMod(z - p.hz, 3) == 0;
                for (int y = 1; y <= top; y++) {
                    String state;
                    if (y <= p.plinth) {
                        state = Nordic.FOOTING;
                    } else if (post && y < top) {
                        state = POST;
                    } else if (y == top || y == p.plinth + 1) {
                        state = Kit.log(Nordic.LOG, gableEnd ? "z" : "x");
                    } else {
                        state = STAVE;
                    }
                    put(s, x, y, z, state);
                }
            }
        }
        // The sloping skirt of the plinth.
        for (int x = p.hx1 - 1; x <= p.hx2 + 1; x++) {
            for (int z = p.hz - p.hmid - 1; z <= p.hz + p.hmid + 1; z++) {
                if (inHall(p, x, z)) {
                    continue;
                }
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (inHall(p, x + d[0], z + d[1])) {
                        String facing = d[0] > 0 ? "east" : d[0] < 0 ? "west" : d[1] > 0 ? "south" : "north";
                        putFree(s, x, 1, z, Kit.stairs(Nordic.FOOTING_STAIRS, facing));
                        break;
                    }
                }
            }
        }
    }

    /** The field of the roof of the hall. */
    private static Nordic.Field hallField(Plan p) {
        return (x, z) -> {
            if (x < p.hx1 - 1 || x > p.hx2 + 1) {
                return NONE;
            }
            int w = hw(p, x) + 1;
            int d = Math.abs(z - p.hz);
            return d > w ? NONE : p.hplate + Nordic.rise(Nordic.HULL, w - d);
        };
    }

    /**
     * The roof of the great hall: a hull of shingles over the bowed walls, cut off where the great tower stands, gables of
     * staves, crossed boards and dragon heads at both ends, smoke louvres over the hearths, small windows in carved frames
     * and the carved portal with its porch in the middle of the south wall.
     */
    private static void hallRoof(Structure s, Plan p) {
        Nordic.Field f = hallField(p);
        Nordic.Area outside = (x, z) -> !(x >= p.tx1 && x <= p.tx2 && z <= p.tz2);
        Nordic.roof(s, f, outside, p.hx1 - 1, p.hz - p.hmid - 2, p.hx2 + 1, p.hz + p.hmid + 2, Nordic.ROOF);
        int we = hw(p, p.hx1);
        Nordic.gableWall(s, f, true, p.hx1, p.hz - we, p.hz + we, p.hplate + 1, STAVE);
        Nordic.gableWall(s, f, true, p.hx2, p.hz - we, p.hz + we, p.hplate + 1, STAVE);
        Nordic.crossedBoards(s, true, p.hx1 - 1, p.hz - we - 1, p.hz + we + 1, Nordic.ROOF, Nordic.BOARD, true);
        Nordic.crossedBoards(s, true, p.hx2 + 1, p.hz - we - 1, p.hz + we + 1, Nordic.ROOF, Nordic.BOARD, true);
        int endRidge = f.at(p.hx1, p.hz);
        Nordic.dragonHead(s, p.hx1 - 1, endRidge + 1, p.hz, "west", Nordic.SPIRE);
        Nordic.dragonHead(s, p.hx2 + 1, endRidge + 1, p.hz, "east", Nordic.SPIRE);
        // Smoke louvres over the two hearths.
        for (int x : hearthMiddles(p)) {
            Nordic.louvre(s, x, f.at(x, p.hz), p.hz, true, Nordic.ROOF);
        }
        // Windows in carved frames on both long walls, the portal in the middle of the south wall.
        int y = p.plinth + 2;
        for (int x = p.hx1 + 3; x <= p.hx2 - 3; x += 4) {
            if (Math.abs(x - p.c) <= 2) {
                continue;
            }
            int w = hw(p, x);
            Nordic.window(s, x, y, p.hz + w, "south", 1, false);
            if (x < p.tx1 - 1 || x > p.tx2 + 1) {
                Nordic.window(s, x, y, p.hz - w, "north", 1, false);
            }
        }
        int sz = p.hz + p.hmid;
        Nordic.portal(s, p.c, 1, sz, "south", true, Nordic.ROOF, Nordic.BOARD, p.small() ? null : Nordic.SPIRE);
        // Shields of the household along the long walls under the eaves, like those along the side of a ship.
        for (int x = p.hx1 + 1; x <= p.hx2 - 1; x += 2) {
            int w = hw(p, x);
            String color = Math.floorMod(x - p.c, 4) == 0 ? "white" : "red";
            putFree(s, x, p.hplate - 1, p.hz + w + 1, Kit.wallBanner(color, "south"));
            if (x < p.tx1 - 1 || x > p.tx2 + 1) {
                putFree(s, x, p.hplate - 1, p.hz - w - 1, Kit.wallBanner(color, "north"));
            }
        }
        p.point(PALACE_DOOR, p.c, 1, sz - 1);
    }

    /** The middles of the two long hearths, west and east of the place of the throne. */
    private static int[] hearthMiddles(Plan p) {
        int[] h = hearth(p);
        return new int[]{(h[0] + h[1]) / 2, (h[2] + h[3]) / 2};
    }

    /** The two long hearths along the middle line of the hall: {west x1, west x2, east x1, east x2}. */
    private static int[] hearth(Plan p) {
        return new int[]{p.hx1 + 2, p.c - 3, p.c + 3, p.hx2 - 2};
    }

    /**
     * Inside the great hall: the place of the throne in the middle of the north wall between two carved high-seat pillars
     * with lanterns, a hanging of cloth behind it and furs before it; two long hearths west and east of it on the middle line
     * with cauldrons on chains; sleeping benches with furs along both long walls, rows of carved pillars, tapestries,
     * lanterns, and a runner of cloth from the portal to the throne.
     */
    private static void hallInside(Structure s, Plan p) {
        int c = p.c;
        int north = p.hz - p.hmid;
        int south = p.hz + p.hmid;
        Nordic.Field f = hallField(p);
        int[] h = hearth(p);
        for (int k = 0; k < 2; k++) {
            int x1 = h[2 * k];
            int x2 = h[2 * k + 1];
            int ceiling = f.at((x1 + x2) / 2, p.hz) - 1;
            Nordic.longHearth(s, x1, p.hz - 1, x2, p.hz + 1, 1, ceiling);
        }
        // The place of the throne.
        for (int side = -1; side <= 1; side += 2) {
            int x = c + 2 * side;
            box(s, x, 1, north + 1, x, 3, north + 1, POST);
            put(s, x, 4, north + 1, Kit.lantern());
        }
        put(s, c - 1, 1, north + 1, Kit.stairs(Nordic.FLOOR_STAIRS, "north"));
        put(s, c + 1, 1, north + 1, Kit.stairs(Nordic.FLOOR_STAIRS, "north"));
        put(s, c, 1, north + 1, Kit.slab(Nordic.FLOOR_SLAB));
        box(s, c - 1, 2, north + 1, c + 1, 3, north + 1, AIR);
        put(s, c, 3, north + 1, Kit.wallBanner("red", "south"));
        put(s, c - 1, 3, north + 1, Kit.wallBanner("white", "south"));
        put(s, c + 1, 3, north + 1, Kit.wallBanner("white", "south"));
        Nordic.furs(s, c - 1, c + 1, c + 1, c + 1, 1);
        for (int z = c + 2; z < south; z++) {
            put(s, c, 1, z, Kit.CLOTH_CARPET);
        }
        // Benches along the long walls, pillars and lanterns.
        for (int x = p.hx1 + 2; x <= p.hx2 - 2; x++) {
            int w = hw(p, x);
            if (Math.abs(x - c) > 3) {
                Nordic.sleepingBench(s, x, p.hz - w + 1, x, p.hz - w + 1, 1, "south");
            }
            if (Math.abs(x - c) > 2) {
                Nordic.sleepingBench(s, x, p.hz + w - 1, x, p.hz + w - 1, 1, "north");
            }
        }
        // Hangings on the long walls over the benches, between the pillars.
        for (int x = p.hx1 + 2; x <= p.hx2 - 2; x++) {
            int w = hw(p, x);
            if (Math.floorMod(x - c, 4) == 2) {
                continue;
            }
            if (Math.abs(x - c) > 3) {
                putFree(s, x, 3, p.hz - w + 1, Kit.wallBanner(Math.floorMod(x, 2) == 0 ? "red" : "white", "south"));
            }
            if (Math.abs(x - c) > 3) {
                putFree(s, x, 3, p.hz + w - 1, Kit.wallBanner(Math.floorMod(x, 2) == 0 ? "red" : "white", "north"));
            }
        }
        for (int x = c - 6; x >= p.hx1 + 2; x -= 4) {
            pillar(s, p, x);
        }
        for (int x = c + 6; x <= p.hx2 - 2; x += 4) {
            pillar(s, p, x);
        }
        for (int x = p.hx1 + 2; x <= p.hx2 - 2; x += 4) {
            hang(s, x, p.hplate + 1, p.hz);
        }
        p.point(HALL, c - 2, 1, c + 3);
        p.point(HALL, c + 2, 1, c + 3);
        p.point(HALL, c - 2, 1, south - 1);
        p.point(HALL, c + 2, 1, south - 1);
    }

    /**
     * A pair of carved pillars across the hall at a column, carrying the roof: posts from the floor to the roof with carved
     * brackets under it, a band of hewn wood at half height and a torch on the side that looks to the hearth.
     */
    private static void pillar(Structure s, Plan p, int x) {
        int w = hw(p, x);
        Nordic.Field f = hallField(p);
        for (int side = -1; side <= 1; side += 2) {
            int z = p.hz + side * (w - 2);
            int top = f.at(x, z);
            for (int y = 1; y < top; y++) {
                if (!s.solid(x, y, z) || s.get(x, y, z).startsWith(Nordic.FLOOR_SLAB)) {
                    put(s, x, y, z, y == 3 ? STAVE : POST);
                }
            }
            putFree(s, x - 1, top - 1, z, Kit.upside(Nordic.STAIRS, "east"));
            putFree(s, x + 1, top - 1, z, Kit.upside(Nordic.STAIRS, "west"));
            putFree(s, x, 4, z - side, Kit.wallTorch(side < 0 ? "south" : "north"));
        }
    }

    // ---------------------------------------------------------------- the great tower

    /**
     * The great tower, the dominant of the hold, built like a stave church behind the throne: a storey of the wall stone with
     * quoins of rough stone and above it walls of staves; a skirt round the stone storey on the three sides free of the hall
     * (the first tier); over the walls the steep skirt of the aisles leaning on raised walls of staves with small windows;
     * on them a cross of steep gables with crossed boards and a dragon head at each of its four ends; on the crossing a ridge
     * turret with its skirts, belfry and needle. All its roofs are of the spires. Inside, see {@link #towerInside}.
     */
    private static void greatTower(Structure s, Plan p) {
        int x1 = p.tx1;
        int x2 = p.tx2;
        int z1 = p.tz1;
        int z2 = p.tz2;
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        box(s, x1 - 2, 1, z1 - 2, x2 + 2, p.tplate + 12, z2, AIR);
        Nordic.footing(s, x1, z1, x2, z2, 0);
        // The ground storey of stone with quoins of rough stone, the storeys above of staves.
        for (int y = 1; y < p.tstorey; y++) {
            s.walls(x1, y, z1, x2, y, z2, y <= p.plinth ? Nordic.FOOTING : Nordic.STONE);
            for (int[] q : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
                put(s, q[0], y, q[1], y % 2 == 0 ? Nordic.FOOTING : Nordic.STONE);
            }
        }
        Nordic.staveWalls(s, x1, p.tstorey, z1, x2, p.tplate, z2);
        box(s, x1 + 1, p.tstorey, z1 + 1, x2 - 1, p.tstorey, z2 - 1, Nordic.FLOOR);
        box(s, x1 + 1, p.tplate, z1 + 1, x2 - 1, p.tplate, z2 - 1, Nordic.FLOOR);
        // The lowest tier: a skirt of shingles round the stone storey on the three sides free of the hall.
        int skirtY = p.tstorey;
        Nordic.Field skirt = (x, z) -> {
            int d = Math.max(Math.max(x1 - x, x - x2), Math.max(z1 - z, z - z2));
            if (d <= 0) {
                return skirtY + 1000;
            }
            return d > 2 ? NONE : skirtY + Nordic.rise(Nordic.STEEP, 2 - d);
        };
        Nordic.Area ring = (x, z) -> {
            int d = Math.max(Math.max(x1 - x, x - x2), Math.max(z1 - z, z - z2));
            return d >= 1 && d <= 2 && z <= z2;
        };
        Nordic.roof(s, skirt, ring, x1 - 2, z1 - 2, x2 + 2, z2, Nordic.SPIRE);
        int nx1 = x1 + p.tinset;
        int nx2 = x2 - p.tinset;
        int nz1 = z1 + p.tinset;
        int nz2 = z2 - p.tinset;
        int aisleTop = Nordic.pentRoof(s, nx1, nz1, nx2, nz2, p.tplate, p.tinset + 1, Nordic.STEEP, Nordic.SPIRE);
        int navePlate = aisleTop + p.tclere + 1;
        Nordic.staveWalls(s, nx1, aisleTop - 1, nz1, nx2, navePlate, nz2);
        box(s, nx1 + 1, aisleTop - 1, nz1 + 1, nx2 - 1, navePlate, nz2 - 1, AIR);
        int wy = aisleTop + p.tclere / 2 + 1;
        for (int u = -1; u <= 1; u += 2) {
            put(s, cx + u * 2, wy, nz2, Kit.GLASS);
            put(s, cx + u * 2, wy, nz1, Kit.GLASS);
            put(s, nx1, wy, cz + u * 2, Kit.GLASS);
            put(s, nx2, wy, cz + u * 2, Kit.GLASS);
        }
        // The cross of gables.
        Nordic.Field fx = Nordic.gableField(nx1, nz1, nx2, nz2, navePlate, true, Nordic.STEEP);
        Nordic.Field fz = Nordic.gableField(nx1, nz1, nx2, nz2, navePlate, false, Nordic.STEEP);
        Nordic.Field f = max(fx, fz);
        Nordic.roof(s, f, Nordic.EVERYWHERE, nx1 - 1, nz1 - 1, nx2 + 1, nz2 + 1, Nordic.SPIRE);
        Nordic.gableWall(s, f, true, nx1, nz1, nz2, navePlate + 1, STAVE);
        Nordic.gableWall(s, f, true, nx2, nz1, nz2, navePlate + 1, STAVE);
        Nordic.gableWall(s, f, false, nz1, nx1, nx2, navePlate + 1, STAVE);
        Nordic.gableWall(s, f, false, nz2, nx1, nx2, navePlate + 1, STAVE);
        Nordic.crossedBoards(s, true, nx1 - 1, nz1 - 1, nz2 + 1, Nordic.SPIRE, Nordic.BOARD, true);
        Nordic.crossedBoards(s, true, nx2 + 1, nz1 - 1, nz2 + 1, Nordic.SPIRE, Nordic.BOARD, true);
        Nordic.crossedBoards(s, false, nz1 - 1, nx1 - 1, nx2 + 1, Nordic.SPIRE, Nordic.BOARD, true);
        Nordic.crossedBoards(s, false, nz2 + 1, nx1 - 1, nx2 + 1, Nordic.SPIRE, Nordic.BOARD, true);
        int end = f.at(nx1 - 1, cz);
        Nordic.dragonHead(s, nx1 - 1, end + 1, cz, "west", Nordic.SPIRE);
        Nordic.dragonHead(s, nx2 + 1, end + 1, cz, "east", Nordic.SPIRE);
        Nordic.dragonHead(s, cx, end + 1, nz1 - 1, "north", Nordic.SPIRE);
        Nordic.dragonHead(s, cx, end + 1, nz2 + 1, "south", Nordic.SPIRE);
        int ridge = f.at(cx, cz);
        Nordic.window(s, cx, navePlate + 2, nz2, "south", 2, false);
        Nordic.turret(s, cx, ridge - 3, cz, ridge + 2, Nordic.SPIRE);
        box(s, cx, ridge - 2, cz, cx, ridge + 3, cz, Nordic.PLANKS);
        // Light in the loft and under the cross.
        put(s, cx + 1, p.tplate + 1, cz + 1, Kit.lantern());
        hang(s, cx, navePlate - 1, cz);
        towerInside(s, p);
    }

    /**
     * The rooms of the great tower: on the ground floor, west, the stair hall (from the hall beside the throne) with the
     * ladder up, a bench, a rack of weapons and hangings; east, the anteroom of the treasury with the desk of the treasurer
     * and, behind a grille of iron bars and an iron door, the treasury with its chests, gold and crates; above, the council
     * room with its long table on furs, the chair of the jarl at its head, benches along the walls, bookshelves with candles,
     * hangings and a great chandelier.
     */
    private static void towerInside(Structure s, Plan p) {
        int x1 = p.tx1;
        int x2 = p.tx2;
        int z1 = p.tz1;
        int z2 = p.tz2;
        int c = p.c;
        int mz = (z1 + z2) / 2;
        // The doors from the hall: west into the stair hall, east into the anteroom.
        s.door(c - 3, 1, z2, Nordic.DOOR, "north", "left");
        s.door(c + 3, 1, z2, Nordic.DOOR, "north", "right");
        // A wall of staves between the two parts, with a door near the hall.
        for (int z = z1 + 1; z < z2; z++) {
            for (int y = 1; y < p.tstorey; y++) {
                put(s, c, y, z, STAVE);
            }
        }
        s.door(c, 1, z2 - 2, Nordic.DOOR, "east", "left");
        // The stair hall: the ladder in the north-west corner, a bench, weapons, hangings.
        for (int y = 1; y <= p.tstorey; y++) {
            put(s, x1 + 1, y, z1 + 1, Kit.ladder("east"));
        }
        Furnish.bench(s, x1 + 1, mz - 1, x1 + 1, z2 - 2, 1, "east", "spruce");
        Furnish.weaponRack(s, c - 1, 1, z1 + 1, "south", "spruce");
        Furnish.crate(s, c - 1, 1, z1 + 2, "spruce");
        Nordic.tapestry(s, x1 + 1, mz - 1, x1 + 1, z2 - 2, 3, "east");
        hang(s, x1 + 2, 3, z1 + 3);
        hang(s, x1 + 2, 3, z2 - 2);
        // The treasury behind bars in the north half of the east part.
        for (int x = c + 1; x < x2; x++) {
            box(s, x, 1, mz, x, p.tstorey - 1, mz, Kit.BARS);
        }
        s.door(c + 2, 1, mz, "minecraft:iron_door", "north", "left");
        put(s, c + 1, 2, mz, "minecraft:lever[face=wall,facing=east,powered=false]");
        for (int x = c + 1; x < x2; x++) {
            boolean chestHere = x == c + 1 || x == x2 - 1;
            if (chestHere) {
                Furnish.chest(s, x, 1, z1 + 1, "south");
            } else {
                put(s, x, 1, z1 + 1, Math.floorMod(x, 2) == 0 ? "minecraft:gold_block" : "minecraft:raw_gold_block");
            }
        }
        for (int z = z1 + 2; z < mz; z++) {
            Furnish.crate(s, x2 - 1, 1, z, "spruce");
            Furnish.sack(s, x2 - 1, 2, z, false);
        }
        hang(s, c + 2, 3, z1 + 2);
        // The anteroom: the desk of the treasurer with his chair, a bench by the wall.
        Furnish.desk(s, x2 - 1, 1, mz + 2, "west", "spruce");
        Furnish.bench(s, x2 - 1, mz + 4, x2 - 1, z2 - 1, 1, "west", "spruce");
        put(s, c + 1, 3, mz + 2, Kit.wallBanner("red", "east"));
        hang(s, c + 3, 3, z2 - 2);
        p.point(TREASURY, c + 2, 1, mz + 1);
        // The council room above.
        int y = p.tstorey + 1;
        int mx = (x1 + x2) / 2;
        int half = p.small() ? 2 : 3;
        Nordic.furs(s, mx - half - 1, mz - 2, mx + half + 1, mz + 2, y);
        Furnish.longTable(s, mx - half, mz, mx + half, mz, y, "spruce");
        for (int x = mx - half; x <= mx + half; x++) {
            Furnish.chair(s, x, y, mz - 1, "south", "spruce");
            Furnish.chair(s, x, y, mz + 1, "north", "spruce");
            if (Math.floorMod(x - mx, 2) == 0) {
                put(s, x, y + 1, mz, Furnish.candles(2));
            }
        }
        Furnish.chair(s, mx + half + 1, y, mz, "west", "spruce");
        put(s, mx + half + 1, y + 1, mz + 1, AIR);
        Nordic.tapestry(s, x1 + 2, z1 + 1, x2 - 2, z1 + 1, y + 2, "south");
        put(s, x2 - 1, y + 2, mz, Kit.wallBanner("red", "west"));
        Furnish.greatChandelier(s, mx, mz, p.tplate - 1, 1);
        for (int[] d : new int[][]{{x1 + 2, z2 - 1}, {x2 - 1, z2 - 1}, {x2 - 1, z1 + 1}, {x1 + 2, z1 + 1}}) {
            put(s, d[0], y, d[1], "minecraft:bookshelf");
            put(s, d[0], y + 1, d[1], Furnish.candles(3));
        }
        if (!p.small()) {
            Furnish.bench(s, x1 + 1, mz - 2, x1 + 1, mz + 2, y, "east", "spruce");
            Furnish.bench(s, mx - 2, z2 - 1, mx + 2, z2 - 1, y, "north", "spruce");
        }
        for (int x = x1 + 3; x <= x2 - 3; x += 3) {
            Nordic.window(s, x, y + 2, z1, "north", 1, false);
        }
        for (int z = z1 + 3; z <= z2 - 3; z += 3) {
            Nordic.window(s, x1, y + 2, z, "west", 1, false);
            Nordic.window(s, x2, y + 2, z, "east", 1, false);
        }
    }

    // ---------------------------------------------------------------- houses of the yard

    /**
     * The cookhouse (eldhus): a low house of staves on a footing under a roof of turf with its stack of stone on the ridge;
     * inside the kitchen of the hold: an oven and a smoker, a cauldron, a fire with a pot over it, a cutting board, shelves,
     * sacks of flour, crates and light. Its door looks to the yard.
     */
    private static void cookhouse(Structure s, Plan p) {
        int[] k = p.cook;
        int x1 = k[0];
        int z1 = k[1];
        int x2 = k[2];
        int z2 = k[3];
        boolean alongX = x2 - x1 >= z2 - z1;
        box(s, x1 - 1, 1, z1 - 1, x2 + 1, 8, z2 + 1, AIR);
        Nordic.footing(s, x1, z1, x2, z2, 0);
        Nordic.staveWalls(s, x1, 1, z1, x2, 3, z2);
        int ridge = Nordic.turfRoof(s, x1, z1, x2, z2, 3, alongX, STAVE);
        int mx = (x1 + x2) / 2;
        int mz = (z1 + z2) / 2;
        if (p.small()) {
            s.door(mx, 1, z2, Nordic.DOOR, "north", "left");
            put(s, x1 + 1, 1, z1 + 1, "minecraft:smoker[facing=south,lit=true]");
            put(s, x1 + 2, 1, z1 + 1, "minecraft:furnace[facing=south,lit=true]");
            put(s, x2 - 1, 1, z1 + 1, "minecraft:water_cauldron[level=3]");
            Furnish.sack(s, x1 + 1, 1, z2 - 1, true);
            Furnish.crate(s, x2 - 1, 1, z2 - 1, "spruce");
            put(s, x1 + 1, 1, mz, Kit.topSlab(Nordic.FLOOR_SLAB));
            put(s, x1 + 1, 2, mz, "minecraft:oak_pressure_plate[powered=false]");
            Nordic.window(s, x1, 2, mz + 1, "west", 1, false);
            Nordic.chimney(s, x1 + 1, mz, ridge + 1, ridge + 2);
            put(s, x2 + 1, 2, mz, Kit.wallTorch("east"));
        } else {
            s.door(x2, 1, mz, Nordic.DOOR, "west", "left");
            Furnish.kitchen(s, x1 + 1, 1, z1 + 1, "south", "spruce");
            put(s, x1 + 1, 1, z2 - 1, "minecraft:smoker[facing=north,lit=true]");
            put(s, x1 + 2, 1, z2 - 1, "minecraft:furnace[facing=north,lit=true]");
            Furnish.sack(s, x2 - 1, 1, z1 + 1, true);
            Furnish.sack(s, x2 - 1, 2, z1 + 1, false);
            Furnish.crate(s, x2 - 1, 1, z2 - 1, "spruce");
            if (x2 - x1 > 5) {
                Furnish.longTable(s, x1 + 4, z2 - 1, x2 - 2, z2 - 1, 1, "spruce");
                put(s, x1 + 4, 2, z2 - 1, Furnish.candles(2));
            }
            Nordic.window(s, mx, 2, z1, "north", 1, false);
            Nordic.window(s, mx - 1, 2, z2, "south", 1, false);
            Nordic.chimney(s, x1 + 1, mz, ridge + 1, ridge + 2);
        }
        hang(s, mx, 3, mz);
    }

    /**
     * The lodge of the jarl: a house of two storeys of boards painted in the colour of the kingdom on a log sill with white
     * corner boards, white windows and a steep roof with white gable boards and dragon heads; inside, below, the hall of
     * the household with a loom, a table and benches, above, the chambers of the jarl and his kin with beds of state, a
     * chest, furs, a tapestry and light.
     */
    private static void lodge(Structure s, Plan p) {
        int[] k = p.lodge;
        int x1 = k[0];
        int z1 = k[1];
        int x2 = k[2];
        int z2 = k[3];
        boolean alongX = x2 - x1 > z2 - z1;
        box(s, x1 - 1, 1, z1 - 1, x2 + 1, 20, z2 + 1, AIR);
        Nordic.footing(s, x1, z1, x2, z2, 0);
        int plate = 9;
        Nordic.paintedWalls(s, x1, 1, z1, x2, plate, z2);
        box(s, x1 + 1, 5, z1 + 1, x2 - 1, 5, z2 - 1, Nordic.FLOOR);
        for (int x = x1 + 1; x < x2; x++) {
            put(s, x, 5, z1, Kit.log(Nordic.LOG, "x"));
            put(s, x, 5, z2, Kit.log(Nordic.LOG, "x"));
        }
        for (int z = z1 + 1; z < z2; z++) {
            put(s, x1, 5, z, Kit.log(Nordic.LOG, "z"));
            put(s, x2, 5, z, Kit.log(Nordic.LOG, "z"));
        }
        int ridge = Nordic.gableRoof(s, x1, z1, x2, z2, plate, alongX, Nordic.STEEP, Nordic.ROOF, Nordic.PAINT, Nordic.WHITE, Nordic.SPIRE);
        int mx = (x1 + x2) / 2;
        int mz = (z1 + z2) / 2;
        // The door: a carved portal on the west gable of the great hold, a door under a white hood in the medium one.
        if (alongX) {
            Nordic.portal(s, x1, 1, mz, "west", true, Nordic.ROOF, Nordic.WHITE, null);
        } else {
            s.door(x1, 1, mz, Nordic.DOOR, "east", "left");
            put(s, x1, 3, mz, Nordic.WHITE.block());
            put(s, x1 - 1, 3, mz, Kit.upside(Nordic.WHITE.stairs(), "east"));
            put(s, x1 - 1, 4, mz, Kit.slab(Nordic.WHITE.slab()));
            put(s, x1 - 1, 4, mz - 1, Kit.stairs(Nordic.WHITE.stairs(), "south"));
            put(s, x1 - 1, 4, mz + 1, Kit.stairs(Nordic.WHITE.stairs(), "north"));
            put(s, x1 - 1, 2, mz - 1, Kit.lantern());
            put(s, x1 - 1, 1, mz - 1, Nordic.FENCE);
        }
        // White windows on both storeys.
        if (alongX) {
            for (int x = x1 + 2; x <= x2 - 2; x += 2) {
                Nordic.window(s, x, 2, z1, "north", 2, true);
                Nordic.window(s, x, 6, z1, "north", 2, true);
                Nordic.window(s, x, 6, z2, "south", 2, true);
                Nordic.window(s, x, 2, z2, "south", 2, true);
            }
        } else {
            for (int z = z1 + 2; z <= z2 - 2; z += 2) {
                if (Math.abs(z - mz) > 1) {
                    Nordic.window(s, x1, 2, z, "west", 2, true);
                }
                Nordic.window(s, x1, 6, z, "west", 2, true);
                Nordic.window(s, x2, 6, z, "east", 2, true);
                Nordic.window(s, x2, 2, z, "east", 2, true);
            }
        }
        Nordic.chimney(s, mx + 1, mz, ridge + 1, ridge + 2);
        // Below: the hall of the household.
        int lx = x2 - 1;
        int lz = z1 + 1;
        for (int y = 1; y <= 5; y++) {
            put(s, lx, y, lz, Kit.ladder(alongX ? "west" : "south"));
        }
        if (alongX) {
            Nordic.loom(s, mx, 1, z2 - 1, "north");
            Furnish.longTable(s, mx - 1, mz, mx + 1, mz, 1, "oak");
            Furnish.bench(s, mx - 1, mz - 1, mx + 1, mz - 1, 1, "south", "oak");
        } else {
            Nordic.loom(s, x2 - 1, 1, z2 - 2, "west");
            Furnish.longTable(s, mx, mz - 1, mx, mz + 1, 1, "oak");
            Furnish.bench(s, mx + 1, mz - 1, mx + 1, mz + 1, 1, "west", "oak");
        }
        put(s, mx, 2, mz, Furnish.candles(3));
        hang(s, mx, 4, mz + (alongX ? 0 : 2));
        // Above: the chambers.
        int y = 6;
        if (alongX) {
            Furnish.canopyBed(s, x1 + 2, y, z1 + 2, "north", "spruce");
            Furnish.canopyBed(s, x2 - 3, y, z1 + 2, "north", "spruce");
            Furnish.chest(s, mx, y, z2 - 1, "north");
            Nordic.furs(s, x1 + 1, z2 - 1, mx - 1, z2 - 1, y);
        } else {
            Furnish.canopyBed(s, x1 + 2, y, z1 + 2, "north", "spruce");
            Furnish.canopyBed(s, x1 + 2, y, z2 - 2, "south", "spruce");
            Furnish.chest(s, x2 - 1, y, mz, "west");
            Nordic.furs(s, x2 - 2, mz - 1, x2 - 2, mz + 1, y);
        }
        hang(s, mx, y + 2, mz);
    }

    // ---------------------------------------------------------------- the yard

    /**
     * The yard: a boardwalk from the street through the gate to the portal of the hall, lamps on posts by the gate, a well
     * of stone under a little roof, runestones, a stack of firewood.
     */
    private static void yard(Structure s, Plan p) {
        int c = p.c;
        int from = p.hz + p.hmid + 3;
        for (int z = from; z < p.gz1; z++) {
            box(s, c - 1, 0, z, c + 1, 0, z, Nordic.PLANKS);
        }
        for (int z = p.gz2 + 1; z < p.n; z++) {
            box(s, c - 1, 0, z, c + 1, 0, z, Nordic.PLANKS);
        }
        int[] well;
        int[][] stones;
        int[] wood;
        switch (p.size) {
            case SMALL -> {
                well = new int[]{9, 10};
                stones = new int[][]{{9, 23}, {21, 23}};
                wood = new int[]{24, 12, 24, 12};
            }
            case MEDIUM -> {
                well = new int[]{30, 12};
                stones = new int[][]{{28, 15}, {33, 16}};
                wood = new int[]{12, 13, 12, 16};
            }
            default -> {
                well = new int[]{35, 14};
                stones = new int[][]{{33, 18}, {37, 17}, {32, 10}};
                wood = new int[]{13, 14, 13, 19};
            }
        }
        well(s, well[0], well[1]);
        for (int[] st : stones) {
            runestone(s, st[0], st[1]);
        }
        for (int z = wood[1]; z <= wood[3]; z++) {
            put(s, wood[0], 1, z, Kit.log(Nordic.LOG, "x"));
            put(s, wood[0], 2, z, Kit.log(Nordic.LOG, "x"));
        }
        if (!p.small()) {
            for (int side = -1; side <= 1; side += 2) {
                int x = c + side * 3;
                int z = p.gz1 - 2;
                box(s, x, 1, z, x, 2, z, Nordic.FENCE);
                put(s, x, 3, z, Kit.lantern());
            }
        }
    }

    /**
     * A well: a ring of rough stone round the water, two posts with a beam across and a little gable roof of shingles over
     * it, a lantern hanging from the beam.
     */
    private static void well(Structure s, int x, int z) {
        box(s, x - 1, 0, z - 1, x + 1, 0, z + 1, Nordic.FOOTING);
        box(s, x - 1, 1, z - 1, x + 1, 1, z + 1, Nordic.FOOTING);
        put(s, x, 1, z, "minecraft:water[level=0]");
        for (int side = -1; side <= 1; side += 2) {
            box(s, x + side, 2, z, x + side, 3, z, Nordic.FENCE);
        }
        box(s, x - 1, 4, z, x + 1, 4, z, Kit.log(Nordic.LOG, "x"));
        Nordic.gableRoof(s, x - 1, z, x + 1, z, 4, true, Nordic.PITCH, Nordic.ROOF, null, Nordic.BOARD, null);
        put(s, x, 3, z, Kit.hangingLantern());
    }

    /**
     * A runestone: an upright stone of carved blocks, rounded at its top, on a step of rough stone.
     */
    private static void runestone(Structure s, int x, int z) {
        put(s, x, 0, z, Nordic.FOOTING);
        put(s, x, 1, z, Kit.CARVED);
        put(s, x, 2, z, Kit.CARVED);
        put(s, x, 3, z, Kit.WALL_FENCE);
    }
}
