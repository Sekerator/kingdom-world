package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle of the style "terem": a white-stone kremlin of old Muscovy with a wooden terem palace inside. Its masses are
 * kept apart by their colours so that it reads from afar and from above: white walls and towers, dark log frames, light
 * shingle roofs and coloured onion domes.
 *
 * <p>The fortress: walls of the wall stone on a plinth of rough stone with a batter, swallow-tail merlons on their outer
 * edge and a walk behind them under a lean-to roof of shingles, carried on the yard side by an arcade. Few towers, each of
 * its own size: two strong towers at the back corners (machicolations, merlons, an upper tier and a tall tent), lower and
 * slimmer ones at the front corners, and in the middle of the front the gate tower, the tallest of the walls, with a vaulted
 * passage under a portal with the icon of the kingdom, a crenellated platform, an upper tier and a tall tent crowned by a
 * watch lantern and a gilt needle. The walks run all round through the towers.
 *
 * <p>Inside, the palace: a cluster of log frames of different heights in the manner of Kolomenskoye, each under a roof of
 * its own (a barrel, a steep carved gable, a tent), joined by galleries and high porches with birch carving, around the
 * throne hall; the kitchen, the treasury behind iron bars, the council room and the royal chambers. Behind the hall rises the
 * one dominant of the kremlin, white from the ground to its dome: the church with its bell tower in tiers that step in
 * (the small kremlin has a tented bell tower of stone), at least eight blocks higher than any tower. A courtyard with a
 * roofed well, flower beds and birches.
 *
 * <p>One program drawn in three sizes: the small kremlin 31 by 31, the kremlin 41 by 41 and the great kremlin 47 by 47. The
 * size gives the sizes and heights of the towers, the blocks of the palace and the tiers of the dominant; the idea stays the
 * same. The throne stands in the middle of the ground on the street layer at the north end of its hall, facing south; the
 * castle leaves it and the ground under it out. Layer 0 replaces the ground, layer 1 is level with the street. Every part is
 * drawn with the blocks that stand for the parts of a look (see {@link Kit} and {@link Terem}).
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
        /** The outer line of the walls from every edge, and the middle of the corner towers. */
        final int w;
        final int t;
        /** The layer of the floor of the fighting walk; one walks on the layer above. */
        final int f;
        /** The strong towers at the back corners: width, top of the body, top of the upper tier. */
        final int backW;
        final int backBody;
        final int backTier;
        /** The slimmer towers at the front corners: width and top of the body (no upper tier). */
        final int frontW;
        final int frontBody;
        /** The gate tower: its middle row, width, top of the body and of the upper tier. */
        final int gz;
        final int gw;
        final int gateBody;
        final int gateTier;
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
                    f = 5;
                    backW = 7;
                    backBody = 9;
                    backTier = 13;
                    frontW = 5;
                    frontBody = 9;
                    gz = 27;
                    gw = 7;
                    gateBody = 9;
                    gateTier = 13;
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
                    f = 5;
                    backW = 7;
                    backBody = 11;
                    backTier = 16;
                    frontW = 5;
                    frontBody = 10;
                    gz = 35;
                    gw = 7;
                    gateBody = 11;
                    gateTier = 17;
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
                    f = 6;
                    backW = 9;
                    backBody = 13;
                    backTier = 19;
                    frontW = 7;
                    frontBody = 12;
                    gz = 41;
                    gw = 9;
                    gateBody = 13;
                    gateTier = 21;
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
     * Draws the kremlin of a plan: the ground, the fortress with its towers and gate, the palace around the throne hall, the
     * church and the courtyard.
     */
    private static Structure castle(Plan p) {
        Structure s = new Structure(p.n, p.height, p.n);
        ground(s, p);
        fortress(s, p);
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

    // ---------------------------------------------------------------- shapes of white stone

    /**
     * Whether a column lies on the rim of a plan of the given half-size (see {@link Terem#inPlan}).
     */
    private static boolean onRim(int dx, int dz, int r, int plan) {
        return Terem.inPlan(dx, dz, r, plan) && (!Terem.inPlan(dx + 1, dz, r, plan) || !Terem.inPlan(dx - 1, dz, r, plan)
                || !Terem.inPlan(dx, dz + 1, r, plan) || !Terem.inPlan(dx, dz - 1, r, plan));
    }

    /**
     * Stairs on the rim of a plan with their high side to its middle (hanging upside down for a corbel or a cornice that
     * carries what is above it); a corner stair where the rim turns.
     */
    private static String slope(String block, int dx, int dz, int r, int plan, boolean upside) {
        boolean outX = dx != 0 && !Terem.inPlan(dx + Integer.signum(dx), dz, r, plan);
        boolean outZ = dz != 0 && !Terem.inPlan(dx, dz + Integer.signum(dz), r, plan);
        String half = upside ? "top" : "bottom";
        String facing;
        String shape = "straight";
        if (outX && outZ) {
            facing = dz > 0 ? "north" : "south";
            shape = dz > 0 == dx > 0 ? "outer_left" : "outer_right";
        } else if (outX || !outZ && Math.abs(dx) >= Math.abs(dz)) {
            facing = dx > 0 ? "west" : "east";
        } else {
            facing = dz > 0 ? "north" : "south";
        }
        return block + "[facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=false]";
    }

    /**
     * A ring of stairs of the wall stone on the rim of a plan at layer {@code y}: a cornice or corbel table hanging upside
     * down, or a sloping ledge; only where nothing stands yet.
     */
    private static void ring(Structure s, int cx, int y, int cz, int r, int plan, boolean upside) {
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (onRim(dx, dz, r, plan) && s.inside(cx + dx, y, cz + dz) && !s.solid(cx + dx, y, cz + dz)) {
                    s.set(cx + dx, y, cz + dz, slope(Kit.WALL_STAIRS, dx, dz, r, plan, upside));
                }
            }
        }
    }

    /**
     * The walls of a body of a plan from layer {@code y1} to {@code y2}, hollow inside: the rim of the wall stone, the lowest
     * {@code rough} layers of rough stone.
     */
    private static void shell(Structure s, int cx, int y1, int y2, int cz, int r, int plan, int rough) {
        for (int y = y1; y <= y2; y++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!Terem.inPlan(dx, dz, r, plan)) {
                        continue;
                    }
                    put(s, cx + dx, y, cz + dz, onRim(dx, dz, r, plan) ? (y < y1 + rough ? Kit.ROUGH : Kit.WALL) : AIR);
                }
            }
        }
    }

    /**
     * Swallow-tail merlons on the rim of a square of half-size {@code r} at layer {@code y}: pairs of blocks with a gap
     * between the pairs, each pair forked on top by two stairs whose high sides look away from each other, and a solid post
     * at every corner.
     */
    private static void merlons(Structure s, int cx, int y, int cz, int r) {
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Math.abs(dx) != r && Math.abs(dz) != r) {
                    continue;
                }
                if (Math.abs(dx) == r && Math.abs(dz) == r) {
                    put(s, cx + dx, y, cz + dz, Kit.WALL);
                    put(s, cx + dx, y + 1, cz + dz, Kit.WALL);
                    continue;
                }
                boolean alongX = Math.abs(dz) == r;
                int u = alongX ? dx : dz;
                int m = Math.abs(u) % 3;
                if (m == 0) {
                    continue;
                }
                int sign = Integer.signum(u) * (m == 1 ? -1 : 1);
                put(s, cx + dx, y, cz + dz, Kit.WALL);
                put(s, cx + dx, y + 1, cz + dz, Kit.stairs(Kit.WALL_STAIRS, along(alongX, sign == 0 ? 1 : sign)));
            }
        }
    }

    /**
     * A row of kokoshniks (small pointed gables of the wall stone) along a line from {@code a1} to {@code a2} at layer
     * {@code y}, three wide with a gap of one between them; the corners that fall outside the rhythm get a post.
     */
    private static void kokoshniki(Structure s, boolean alongX, int a1, int a2, int c, int y) {
        int len = a2 - a1 + 1;
        int start = (len + 1) % 4 == 0 ? a1 : a1 + 1;
        for (int a = start; a + 2 <= a2; a += 4) {
            at(s, alongX, a, y, c, Kit.stairs(Kit.WALL_STAIRS, along(alongX, 1)));
            at(s, alongX, a + 1, y, c, Kit.WALL);
            at(s, alongX, a + 2, y, c, Kit.stairs(Kit.WALL_STAIRS, along(alongX, -1)));
            at(s, alongX, a + 1, y + 1, c, Kit.slab(Kit.WALL_SLAB));
        }
        if (start > a1) {
            for (int a : new int[]{a1, a2}) {
                at(s, alongX, a, y, c, Kit.WALL);
                at(s, alongX, a, y + 1, c, Kit.slab(Kit.WALL_SLAB));
            }
        }
    }

    /**
     * Arched openings in the four straight faces of an eight-sided tier of half-size {@code r} around (cx, cz), from layer
     * {@code lo} to {@code hi}: one block wide in a face of three, three wide in a face of five with haunches at the top.
     *
     * @param glass panes in the openings (windows of a drum) instead of open arches (a belfry)
     */
    private static void arches(Structure s, int cx, int cz, int r, int lo, int hi, boolean glass) {
        int half = (r * 141 + 50) / 100 - r;
        String fill = glass ? Kit.GLASS : AIR;
        for (int side = 0; side < 4; side++) {
            boolean alongX = side < 2;
            int c = (side % 2 == 0 ? -r : r);
            int w = half >= 2 ? 1 : 0;
            for (int u = -w; u <= w; u++) {
                for (int y = lo; y <= hi; y++) {
                    boolean haunch = w > 0 && y == hi && u != 0;
                    String state = haunch ? Kit.upside(Kit.WALL_STAIRS, along(alongX, u)) : fill;
                    at(s, alongX, (alongX ? cx : cz) + u, y, (alongX ? cz : cx) + c, state);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the fortress

    /**
     * The fortress: four walls between the corner towers, the strong towers at the back and the slimmer ones at the
     * front, stairs from the yard up to the walks, and the gate tower.
     */
    private static void fortress(Structure s, Plan p) {
        int lo = p.w;
        int hi = p.n - 1 - p.w;
        int t1 = p.t;
        int t2 = p.n - 1 - p.t;
        wall(s, p, true, lo, t1, t2, 1);
        wall(s, p, true, hi, t1, t2, -1);
        wall(s, p, false, lo, t1, t2, 1);
        wall(s, p, false, hi, t1, t2, -1);
        // Stairs up to the walks along the side walls.
        int zTop = p.small() ? p.c - 1 : p.hz2 + 1;
        wallStair(s, p, false, lo, 1, zTop, -1);
        if (!p.small()) {
            wallStair(s, p, false, hi, -1, zTop, -1);
        }
        int a = p.w + 1;
        int b = p.w + 2;
        // The corner towers; the walks enter them through doors at their own height.
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                boolean back = sz < 0;
                int width = back ? p.backW : p.frontW;
                int h = (width - 1) / 2;
                int cx = sx < 0 ? t1 : t2;
                int cz = sz < 0 ? t1 : t2;
                int ax = sx < 0 ? a : p.n - 1 - a;
                int bx = sx < 0 ? b : p.n - 1 - b;
                int az = sz < 0 ? a : p.n - 1 - a;
                int bz = sz < 0 ? b : p.n - 1 - b;
                String toYardX = sx < 0 ? "east" : "west";
                String toYardZ = sz < 0 ? "south" : "north";
                tower(s, p, cx, cz, width, back ? p.backBody : p.frontBody, back ? p.backTier : 0, false, true, new int[][]{
                        {cx - sx * h, az}, {cx - sx * h, bz}, {ax, cz - sz * h}, {bx, cz - sz * h}},
                        new String[]{toYardX, toYardX, toYardZ, toYardZ});
                // a guard's place on the walk before the tower's door: right at the door of a strong one, a step on at a slim one
                int out = back ? 1 : 2;
                p.point(WALL, sx < 0 ? cx + h + out : cx - h - out, p.f + 1, az);
            }
        }
        gate(s, p);
    }

    /**
     * A wall on the line {@code line} (z when it runs along x, x when along z) from {@code a1} to {@code a2}, four blocks
     * thick: outside a footing of rough stone with a batter, the face of rough stone for two layers and of the wall stone
     * above, a corbel course under the parapet and swallow-tail merlons; on top the walk two blocks wide on the layer over
     * {@link Plan#f}, with a carved railing on its yard edge and a lean-to roof of shingles that rises against the merlons;
     * on the yard side the walk rests on an arcade of piers and arches. Lanterns under the roof and in the arches.
     *
     * @param in the side of the yard: +1 or -1 across the line
     */
    private static void wall(Structure s, Plan p, boolean alongX, int line, int a1, int a2, int in) {
        int f = p.f;
        String toWall = across(alongX, -in);
        String toYard = across(alongX, in);
        for (int a = a1; a <= a2; a++) {
            int m4 = Math.floorMod(a - a1, 4);
            int m3 = Math.floorMod(a - a1, 3);
            at(s, alongX, a, 0, line - in, Kit.ROUGH);
            at(s, alongX, a, 1, line - in, Kit.stairs(Kit.ROUGH_STAIRS, toYard));
            for (int k = 0; k <= 3; k++) {
                at(s, alongX, a, 0, line + in * k, Kit.ROUGH);
            }
            for (int y = 1; y <= f; y++) {
                at(s, alongX, a, y, line, y <= 2 ? Kit.ROUGH : Kit.WALL);
                at(s, alongX, a, y, line + in, Kit.WALL);
            }
            at(s, alongX, a, f, line - in, Kit.upside(Kit.WALL_STAIRS, toYard));
            // the arcade under the yard half of the walk
            for (int k = 2; k <= 3; k++) {
                for (int y = 1; y < f; y++) {
                    String state = m4 == 0 ? Kit.WALL : AIR;
                    if (y == f - 1 && m4 == 1) {
                        state = Kit.upside(Kit.WALL_STAIRS, along(alongX, -1));
                    } else if (y == f - 1 && m4 == 3) {
                        state = Kit.upside(Kit.WALL_STAIRS, along(alongX, 1));
                    }
                    at(s, alongX, a, y, line + in * k, state);
                }
                at(s, alongX, a, f, line + in * k, Kit.WALL);
            }
            if (m4 == 2) {
                at(s, alongX, a, f - 1, line + 3 * in, Kit.hangingLantern());
            }
            // the parapet: a breastwork and the swallow-tail merlons in pairs
            at(s, alongX, a, f + 1, line, Kit.WALL);
            if (m3 != 0) {
                at(s, alongX, a, f + 2, line, Kit.WALL);
                at(s, alongX, a, f + 3, line, Kit.stairs(Kit.WALL_STAIRS, along(alongX, m3 == 1 ? -1 : 1)));
            }
            // the walk, its railing and its roof
            at(s, alongX, a, f + 1, line + 3 * in, m3 == 0 ? Kit.log(Terem.POST, "y") : Terem.CARVE_FENCE);
            at(s, alongX, a, f + 4, line + in, Kit.stairs(Terem.ROOF.stairs(), toWall));
            at(s, alongX, a, f + 3, line + 2 * in, Kit.stairs(Terem.ROOF.stairs(), toWall));
            at(s, alongX, a, f + 2, line + 3 * in, Kit.stairs(Terem.ROOF.stairs(), toWall));
            if (m4 == 2) {
                at(s, alongX, a, f + 3, line + in, Kit.hangingLantern());
            }
        }
    }

    /**
     * A flight of stone steps from the yard up to a walk along the yard face of its arcade, arriving at a gap in the railing
     * at {@code aTop}, with a carved railing on its open side; the eave over the gap is cut away so that one walks in
     * upright.
     *
     * @param dir the way the flight climbs along the line: +1 or -1
     */
    private static void wallStair(Structure s, Plan p, boolean alongX, int line, int in, int aTop, int dir) {
        int f = p.f;
        int lane = line + 4 * in;
        String up = along(alongX, dir);
        for (int k = 1; k <= f; k++) {
            int a = aTop - dir * (f - k);
            at(s, alongX, a, k, lane, Kit.stairs(Kit.WALL_STAIRS, up));
            for (int y = 1; y < k; y++) {
                at(s, alongX, a, y, lane, Kit.WALL);
            }
            if (k >= 2) {
                at(s, alongX, a, k + 1, lane + in, Terem.CARVE_FENCE);
            }
        }
        at(s, alongX, aTop, f + 1, line + 3 * in, AIR);
        at(s, alongX, aTop, f + 2, line + 3 * in, AIR);
        at(s, alongX, aTop + dir, f + 1, line + 3 * in, Kit.log(Terem.POST, "y"));
        p.point(WALL, alongX ? aTop - dir * 2 : line + in, f + 1, alongX ? line + in : aTop - dir * 2);
    }

    /**
     * A tower of the fortress standing on the ground, square, of the wall stone on a plinth of rough stone with a batter:
     * a room at its foot reached by a ladder from the floor of the walk, which the walks cross through doors in its walls;
     * loopholes where its walls look out. A tower with an upper tier ({@code tier > 0}) carries over its body a corbel
     * table and a platform behind swallow-tail merlons, the tier one block narrower on every side, and a tent over the
     * tier; one without carries its tent right over a cornice of its body. The tent of shingles ends in a gilt needle with
     * the flag of the kingdom, or in a watch lantern with a gilt needle (the gate). Lanterns on every storey.
     *
     * @param watch  whether the tent ends in the watch lantern of the style instead of a needle with a flag
     * @param ladder whether a ladder leads from the room at its foot to the floor of the walk
     * @param doors  the wall cells where the walks come in, each with the side it looks to in {@code outs}
     * @return the layer of the top of its point
     */
    private static int tower(Structure s, Plan p, int cx, int cz, int width, int body, int tier, boolean watch, boolean ladder,
                             int[][] doors, String[] outs) {
        int h = (width - 1) / 2;
        int f = p.f;
        Terem.plate(s, cx, 0, cz, h + 1, Terem.SQUARE, Kit.ROUGH);
        for (int dz = -h - 1; dz <= h + 1; dz++) {
            for (int dx = -h - 1; dx <= h + 1; dx++) {
                if ((Math.abs(dx) == h + 1 || Math.abs(dz) == h + 1) && s.inside(cx + dx, 1, cz + dz) && !s.solid(cx + dx, 1, cz + dz)) {
                    put(s, cx + dx, 1, cz + dz, slope(Kit.ROUGH_STAIRS, dx, dz, h + 1, Terem.SQUARE, false));
                }
            }
        }
        shell(s, cx, 1, body, cz, h, Terem.SQUARE, 2);
        Terem.plate(s, cx, 0, cz, h - 1, Terem.SQUARE, Terem.FLOOR);
        Terem.plate(s, cx, f, cz, h - 1, Terem.SQUARE, Terem.FLOOR);
        // Loopholes where the walls look out.
        for (int[] d : new int[][]{{h, 0}, {-h, 0}, {0, h}, {0, -h}}) {
            for (int y : new int[]{3, f + 2, body - 1}) {
                int ox = cx + d[0] + Integer.signum(d[0]);
                int oz = cz + d[1] + Integer.signum(d[1]);
                if (y > 2 && y <= body && s.inside(ox, y, oz) && !s.solid(ox, y, oz) && !s.solid(ox, y - 1, oz)) {
                    put(s, cx + d[0], y, cz + d[1], AIR);
                }
            }
        }
        for (int i = 0; i < doors.length; i++) {
            s.door(doors[i][0], f + 1, doors[i][1], Terem.DOOR, Furnish.opposite(outs[i]), "left");
        }
        if (ladder) {
            for (int y = 1; y <= f; y++) {
                put(s, cx, y, cz - h + 1, Kit.ladder("south"));
            }
        }
        put(s, cx + 1, f - 1, cz, Kit.hangingLantern());
        put(s, cx + 1, body, cz, Kit.hangingLantern());
        int eaves;
        int r0;
        if (tier > 0) {
            int u = Math.max(1, h - 1);
            ring(s, cx, body + 1, cz, h + 1, Terem.SQUARE, true);
            Terem.plate(s, cx, body + 1, cz, h, Terem.SQUARE, Kit.WALL);
            merlons(s, cx, body + 2, cz, h + 1);
            shell(s, cx, body + 2, tier, cz, u, Terem.SQUARE, 0);
            put(s, cx, tier, cz, Kit.hangingLantern());
            for (int[] d : new int[][]{{u, 0}, {-u, 0}, {0, u}, {0, -u}}) {
                put(s, cx + d[0], tier - 1, cz + d[1], AIR);
            }
            eaves = tier + 1;
            r0 = h;
        } else {
            ring(s, cx, body, cz, h + 1, Terem.SQUARE, true);
            eaves = body + 1;
            r0 = h + 1;
        }
        if (!watch) {
            int top = Terem.tent(s, Terem.EVERYWHERE, cx, cz, r0, eaves, Terem.SQUARE, Terem.SQUARE, Terem.TENT_RISES, 0, -1);
            flag(s, cx, cz);
            return top - 1;
        }
        int layer = Terem.tent(s, Terem.EVERYWHERE, cx, cz, r0, eaves, Terem.SQUARE, Terem.SQUARE, Terem.TENT_RISES, 0, 1);
        return Terem.watch(s, cx, layer, cz) - 1;
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
     * The gate tower in the middle of the front, the tallest of the walls: a vaulted passage three wide through its solid
     * foot, a portal of the wall stone on its front under a kokoshnik with the icon of the kingdom, banners of the kingdom;
     * a room over the passage that the walks of the front pass through; over its body the platform behind merlons, the upper
     * tier and the tall tent with the watch lantern and the gilt needle.
     */
    private static void gate(Structure s, Plan p) {
        int cx = p.c;
        int cz = p.gz;
        int h = (p.gw - 1) / 2;
        int f = p.f;
        int la = p.n - 1 - (p.w + 1);
        int lb = p.n - 1 - (p.w + 2);
        tower(s, p, cx, cz, p.gw, p.gateBody, p.gateTier, true, false,
                new int[][]{{cx - h, la}, {cx - h, lb}, {cx + h, la}, {cx + h, lb}},
                new String[]{"west", "west", "east", "east"});
        // The solid foot and the vaulted passage through it.
        box(s, cx - h + 1, 1, cz - h + 1, cx + h - 1, f - 1, cz + h - 1, Kit.WALL);
        box(s, cx - 1, 1, cz - h, cx + 1, f - 1, cz + h, AIR);
        box(s, cx - 1, f - 1, cz - h, cx - 1, f - 1, cz + h, Kit.upside(Kit.WALL_STAIRS, "west"));
        box(s, cx + 1, f - 1, cz - h, cx + 1, f - 1, cz + h, Kit.upside(Kit.WALL_STAIRS, "east"));
        box(s, cx - 1, 0, cz - h - 1, cx + 1, 0, cz + h + 1, PATH);
        put(s, cx, f - 1, cz, Kit.hangingLantern());
        put(s, cx, f - 1, cz + h - 1, Kit.hangingLantern());
        // The open leaves of the gate against the walls of the passage.
        for (int y = 1; y <= f - 3; y++) {
            put(s, cx - 1, y, cz + h - 1, Kit.shutter("minecraft:oak_trapdoor", "east"));
            put(s, cx + 1, y, cz + h - 1, Kit.shutter("minecraft:oak_trapdoor", "west"));
        }
        // The icon in the face over the arch; where the ground leaves room before the face (all but the small kremlin), the
        // portal: pilasters, a cornice and a kokoshnik with the icon in it, banners of the kingdom under the corbels.
        put(s, cx, f + 1, cz + h, Terem.GILT);
        int zo = cz + h + 1;
        if (zo > p.n - 1) {
            put(s, cx - 2, p.gateBody - 1, cz - h - 1, Kit.wallBanner("red", "north"));
            put(s, cx + 2, p.gateBody - 1, cz - h - 1, Kit.wallBanner("red", "north"));
        }
        for (int y = 1; y <= f - 1; y++) {
            put(s, cx - 2, y, zo, y <= 1 ? Kit.ROUGH : Kit.WALL);
            put(s, cx + 2, y, zo, y <= 1 ? Kit.ROUGH : Kit.WALL);
        }
        box(s, cx - 2, f, zo, cx + 2, f, zo, Kit.upside(Kit.WALL_STAIRS, "north"));
        put(s, cx - 2, f + 1, zo, Kit.stairs(Kit.WALL_STAIRS, "east"));
        put(s, cx - 1, f + 1, zo, Kit.WALL);
        put(s, cx, f + 1, zo, Terem.GILT);
        put(s, cx + 1, f + 1, zo, Kit.WALL);
        put(s, cx + 2, f + 1, zo, Kit.stairs(Kit.WALL_STAIRS, "west"));
        put(s, cx - 1, f + 2, zo, Kit.stairs(Kit.WALL_STAIRS, "east"));
        put(s, cx, f + 2, zo, Kit.WALL);
        put(s, cx + 1, f + 2, zo, Kit.stairs(Kit.WALL_STAIRS, "west"));
        put(s, cx, f + 3, zo, Kit.slab(Kit.WALL_SLAB));
        put(s, cx - 3, 3, zo, Kit.wallTorch("south"));
        put(s, cx + 3, 3, zo, Kit.wallTorch("south"));
        put(s, cx - 2, p.gateBody - 1, zo, Kit.wallBanner("red", "south"));
        put(s, cx + 2, p.gateBody - 1, zo, Kit.wallBanner("red", "south"));
        // The room over the passage: benches and lanterns.
        if (h >= 3) {
            Terem.lavka(s, cx - h + 2, cz + h - 1, cx + h - 2, cz + h - 1, f + 1);
        }
        hang(s, cx + 1, f + 3, cz);
        // the guards of the gate stand in the passage under the line of the walk, the one over it in the room
        p.point(GATE, cx - 1, 1, la);
        p.point(GATE, cx + 1, 1, la);
        p.point(WALL, cx, f + 1, la + 1);
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
     * against its walls and its roof: a steep carved gable over the small one, a barrel over the others (no dome of its
     * own, so that the dominant behind it stands alone).
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
            Terem.barrelRoof(s, z1 - 1, z2 + 1, c, top, x2 - x1 + 3, false, Terem.ROOF, null, true, true);
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
        if (p.small()) {
            // the door from the hall into the bell tower, through the cloth behind the throne
            s.door(p.c, 1, p.hz1, Terem.DOOR, "south", "left");
        }
        hallPorch(s, p);
    }

    /**
     * The terem of the small kremlin behind the hall, sharing its north wall: two log frames of two floors under steep
     * carved gables looking west and east, and between them the white bell tower with the stair, the dominant of the small
     * kremlin (see {@link #smallBelfry}). Below, the kitchen (west) and the treasury behind iron bars, its iron door in the
     * hall (east); above, the royal chamber over the warm stove and the council room.
     */
    private static void smallTerem(Structure s, Plan p) {
        int top = 10;
        srub(s, 8, 6, 13, 12, top, 5);
        srub(s, 17, 6, 22, 12, top, 5);
        Terem.steepGable(s, 8, 6, 13, 12, top, true);
        Terem.steepGable(s, 17, 6, 22, 12, top, true);
        smallBelfry(s);
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
     * two terems of three floors, the western under a tall tent of shingles with a gilt needle, the eastern under a barrel
     * with a small onion dome, joined to the wings by roofed galleries.
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
            terem(s, p, tx1, ter[1], tx2, ter[3], west);
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
     * A terem of three floors behind a wing: a log frame under a tall tent of shingles with a gilt needle (west) or a barrel
     * with a small onion dome (east); a door to the yard on the ground floor and a ladder up through all floors; windows in carved frames on
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
            put(s, mx, point - 1, mz, Terem.GILT);
            put(s, mx, point, mz, Terem.ROD);
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

    // ---------------------------------------------------------------- the dominant

    /**
     * The dominant of the small kremlin, between the two frames of its terem: a tented bell tower of the wall stone. Its
     * square foot holds the stair of the terem on three floors and rises over the roofs with kokoshniks on its north front;
     * on it an eight-sided belfry with open arches and the bell, a cornice, a tent of the wall stone itself and the onion
     * dome on a carved band with a gilt finial: white from the ground to the dome, its point 34 above the street.
     */
    private static void smallBelfry(Structure s) {
        int x1 = 13;
        int x2 = 17;
        int z1 = 6;
        int z2 = 12;
        int cx = 15;
        int cz = 9;
        int top = 12;
        // The roofs of the frames end against the tower: what of them stands in it becomes its stone or goes.
        for (int y = 11; y <= 20; y++) {
            for (int z = 5; z <= 10; z++) {
                for (int x = x1; x <= x2; x++) {
                    boolean wall = x == x1 || x == x2;
                    if (!wall || s.solid(x, y, z)) {
                        s.set(x, y, z, wall ? Kit.WALL : AIR);
                    }
                }
            }
        }
        box(s, x1, 0, z1, x2, 0, z2, Kit.ROUGH);
        for (int y = 1; y <= top; y++) {
            s.walls(x1, y, z1, x2, y, z2, y == 1 ? Kit.ROUGH : Kit.WALL);
        }
        box(s, x1 + 1, 1, z1 + 1, x2 - 1, top - 1, z2 - 1, AIR);
        for (int y : new int[]{0, 5}) {
            box(s, x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, Terem.FLOOR);
        }
        box(s, x1 + 1, top, z1 + 1, x2 - 1, top, z2 - 1, Kit.WALL);
        Terem.window(s, cx, 10, z1, "north", 1, 2, false);
        kokoshniki(s, true, x1, x2, z1, top + 1);
        // The belfry, its cornice, the stone tent and the onion.
        shell(s, cx, top + 1, top + 5, cz, 2, Terem.OCTAGON, 0);
        arches(s, cx, cz, 2, top + 2, top + 4, false);
        put(s, cx, top + 5, cz, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        put(s, cx + 1, top + 1, cz, Kit.lantern());
        Terem.plate(s, cx, top + 6, cz, 2, Terem.OCTAGON, Kit.WALL);
        ring(s, cx, top + 6, cz, 3, Terem.OCTAGON, true);
        int point = stoneTent(s, cx, top + 7, cz, 3, 3);
        Terem.plate(s, cx, point, cz, 1, Terem.ROUND, Terem.CARVE);
        Terem.plate(s, cx, point + 1, cz, 1, Terem.ROUND, Terem.DOME.block());
        int layer = point + 2;
        // a bulb five wide that swells, holds its width and draws in to a point over two layers
        int[][] bulb = {{2, 0}, {2, 1}, {2, 2}, {1, 1}, {1, 2}, {0, 1}};
        for (int[] step : bulb) {
            int r = step[0];
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Terem.inPlan(dx, dz, r, Terem.ROUND)) {
                        boolean edge = step[1] != 1 && r > 0 && onRim(dx, dz, r, Terem.ROUND);
                        put(s, cx + dx, layer, cz + dz, edge ? slope(Terem.DOME.stairs(), dx, dz, r, Terem.ROUND, step[1] == 0)
                                : Terem.DOME.block());
                    }
                }
            }
            layer++;
        }
        Terem.finial(s, cx, layer, cz, 1);
    }

    /**
     * A solid tent of the wall stone over an eight-sided plan from layer {@code y}: rings narrowing by one block, the eaves
     * one layer high and every other ring {@code rise} layers, each ring edged with stairs in its last layer.
     *
     * @return the layer above its point
     */
    private static int stoneTent(Structure s, int cx, int y, int cz, int r0, int rise) {
        int layer = y;
        for (int r = r0; r >= 1; r--) {
            int count = r == r0 ? 1 : rise;
            for (int j = 0; j < count; j++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (Terem.inPlan(dx, dz, r, Terem.OCTAGON)) {
                            boolean edge = j == count - 1 && onRim(dx, dz, r, Terem.OCTAGON);
                            put(s, cx + dx, layer, cz + dz, edge ? slope(Kit.WALL_STAIRS, dx, dz, r, Terem.OCTAGON, false) : Kit.WALL);
                        }
                    }
                }
                layer++;
            }
        }
        return layer;
    }

    /**
     * The dominant of the kremlin and the great kremlin, behind the hall, white from the ground to its dome: the church of
     * the wall stone on a plinth of rough stone under a cornice and a crown of kokoshniks, and rising out of its roof the bell
     * tower in eight-sided tiers that step in, each on a cornice of its own: the belfry with open arches and its bell (the
     * great kremlin has a second, narrower one over it), the drum and the great onion dome with its gilt cross, 38 above
     * the street (46 in the great kremlin). Inside, the iconostasis of gold and ochre with candles before it, a runner, a
     * bench and a chandelier of candles; the door looks to the hall under a carved hood.
     */
    private static void church(Structure s, Plan p) {
        if (p.small()) {
            return;
        }
        boolean large = p.large();
        int cx = p.c;
        int x1 = large ? 18 : 16;
        int x2 = p.n - 1 - x1;
        int z1 = 8;
        int z2 = large ? 16 : 14;
        int cz = (z1 + z2) / 2;
        int top = large ? 13 : 11;
        // The church: plinth, walls, roof, cornice and kokoshniks.
        box(s, x1 - 1, 0, z1 - 1, x2 + 1, 0, z2 + 1, Kit.ROUGH);
        for (int y = 1; y <= top; y++) {
            s.walls(x1, y, z1, x2, y, z2, y == 1 ? Kit.ROUGH : Kit.WALL);
        }
        box(s, x1 + 1, 1, z1 + 1, x2 - 1, top - 1, z2 - 1, AIR);
        box(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, Terem.FLOOR);
        box(s, x1 + 1, top, z1 + 1, x2 - 1, top, z2 - 1, Kit.WALL);
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            putFree(s, x, top, z1 - 1, Kit.upside(Kit.WALL_STAIRS, "south"));
            putFree(s, x, top, z2 + 1, Kit.upside(Kit.WALL_STAIRS, "north"));
        }
        for (int z = z1; z <= z2; z++) {
            putFree(s, x1 - 1, top, z, Kit.upside(Kit.WALL_STAIRS, "east"));
            putFree(s, x2 + 1, top, z, Kit.upside(Kit.WALL_STAIRS, "west"));
        }
        kokoshniki(s, true, x1, x2, z1, top + 1);
        kokoshniki(s, true, x1, x2, z2, top + 1);
        kokoshniki(s, false, z1, z2, x1, top + 1);
        kokoshniki(s, false, z1, z2, x2, top + 1);
        // The tiers of the bell tower: {half-size, first layer, last layer, lowest and highest layer of the openings, glass}.
        int[][] tiers = large
                ? new int[][]{{4, top + 1, top + 10, top + 4, top + 7, 0}, {3, top + 12, top + 16, top + 13, top + 15, 0},
                        {2, top + 18, top + 19, top + 18, top + 19, 1}}
                : new int[][]{{3, top + 1, top + 8, top + 4, top + 6, 0}, {2, top + 10, top + 14, top + 11, top + 12, 1}};
        for (int i = 0; i < tiers.length; i++) {
            int[] t = tiers[i];
            int r = t[0];
            shell(s, cx, t[1], t[2], cz, r, Terem.OCTAGON, 0);
            if (i > 0) {
                ring(s, cx, t[1], cz, r + 1, Terem.OCTAGON, false);
            }
            arches(s, cx, cz, r, t[3], t[4], t[5] == 1);
            put(s, cx + 1, t[1], cz, Kit.lantern());
            if (t[5] == 0) {
                put(s, cx, t[2], cz, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
            }
            boolean last = i == tiers.length - 1;
            if (!last || !large) {
                Terem.plate(s, cx, t[2] + 1, cz, r, Terem.OCTAGON, Kit.WALL);
                ring(s, cx, t[2] + 1, cz, r + 1, Terem.OCTAGON, true);
            }
        }
        int[] last = tiers[tiers.length - 1];
        Terem.onionDome(s, cx, last[2] + (large ? 1 : 2), cz, large ? 9 : 7, 0, null);
        // Windows in carved frames, the door under its hood.
        for (int y : large ? new int[]{3, 8} : new int[]{3, 7}) {
            Terem.window(s, x1, y, cz, "west", 1, 2, false);
            Terem.window(s, x2, y, cz, "east", 1, 2, false);
        }
        s.door(cx, 1, z2, Terem.DOOR, "north", "left");
        put(s, cx - 1, 3, z2 + 1, Kit.upside(Terem.CARVE_STAIRS, "west"));
        put(s, cx + 1, 3, z2 + 1, Kit.upside(Terem.CARVE_STAIRS, "east"));
        put(s, cx, 4, z2 + 1, Kit.slab(Terem.CARVE_SLAB));
        put(s, cx, 3, z2 + 1, Kit.wallTorch("south"));
        // The iconostasis across the church before the altar, candles before it, the runner and the chandelier.
        int zi = z1 + 2;
        for (int x = x1 + 2; x <= x2 - 2; x++) {
            for (int y = 1; y <= 6; y++) {
                put(s, x, y, zi, Math.floorMod(x + y, 2) == 0 ? "minecraft:yellow_terracotta" : "minecraft:orange_terracotta");
            }
            put(s, x, 7, zi, Kit.slab(Terem.CARVE_SLAB));
        }
        put(s, cx, 1, zi, "minecraft:spruce_fence_gate[facing=south,in_wall=false,open=false,powered=false]");
        put(s, cx, 2, zi, Terem.GILT);
        put(s, cx, 3, zi, Terem.GILT);
        put(s, cx - 1, 3, zi, Terem.GILT);
        put(s, cx + 1, 3, zi, Terem.GILT);
        put(s, cx, 1, z1 + 1, Terem.GILT);
        put(s, cx + 1, 1, z1 + 1, Kit.lantern());
        for (int dx : new int[]{-2, 2}) {
            put(s, cx + dx, 1, zi + 1, Terem.CARVE_FENCE);
            put(s, cx + dx, 2, zi + 1, Furnish.candles(3));
        }
        box(s, cx, 1, zi + 1, cx, 1, z2 - 1, Kit.CLOTH_CARPET);
        Terem.lavka(s, x1 + 1, zi + 2, x1 + 1, z2 - 1, 1);
        Terem.lavka(s, x2 - 1, zi + 2, x2 - 1, z2 - 1, 1);
        horos(s, cx, cz + 1, top - 4);
        put(s, x2 - 1, 1, zi + 1, Kit.lantern());
        put(s, x1 + 1, 1, zi + 1, Kit.lantern());
    }

    /** A block where nothing stands yet. */
    private static void putFree(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z) && !s.solid(x, y, z)) {
            s.set(x, y, z, state);
        }
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
