package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle of the style "eastern": the seat of a lord of the far east, a walled enclosure of stone crowned by one tall
 * keep. The castle reads from afar as one mass rising in steps: the gate, low and heavy, the wide roof of the palace hall
 * behind it, and over both the white keep, storey on storey, each under a curving roof of its own.
 *
 * <p>The keep (tenshu) stands at the back in the line of the north wall, on a high base of stone whose faces flare out at
 * the foot. Its storeys of white plaster between dark posts and beams step in as they rise; every storey sits under a skirt
 * roof whose corners sweep up, little gables of plaster (chidori-hafu) stand on the skirts, turning from the sides to the
 * front from one storey to the next, and the top storey with a red balustrade carries a hip-and-gable roof with gilt
 * ornaments on the ends of its ridge. Rows of dark lattice windows look out of the white walls. Inside: the council room,
 * the lord's chamber, a study and a lookout, reached from the throne hall by a stair inside the base.
 *
 * <p>The palace hall before the keep holds the throne: red-lacquered pillars in its front and inside, paper screens, mats
 * on the floor, a ceiling of painted coffers and, behind the throne, a wall of gold with a painted pine. Behind its back
 * wall two passages run into the base of the keep: the stair up to the keep, and the treasury, a vault of stone behind an
 * iron door. The walls of the enclosure are bases of stone, battered at the foot, with a white parapet under a coping of
 * tiles and a walk behind it. Few towers, each of its own size: two-storey corner towers (yagura) at the front corners, a
 * taller one of three storeys at a back corner of the larger castles. The gate in the middle of the front is a gatehouse
 * over a passage through the wall, under a heavy roof with a gilt gable over the passage; in the larger castles its inner
 * half stands on red pillars over the court. The court is laid with gravel; beside the hall a garden with a pond, a red
 * arched bridge, stone lanterns, pruned pines and a cherry, behind a white wall with a round moon gate; the kitchen stands
 * in the other side court, the armoury in the base of a corner tower.
 *
 * <p>One program in three sizes: small 31 by 31, medium 41 by 41, large 47 by 47; the size gives the sizes and storeys of
 * the keep, the hall, the gate and the towers. The throne stands in the middle of the ground on the street layer at the
 * north end of the hall, facing south; the castle leaves it and the ground under it out. Layer 0 replaces the ground,
 * layer 1 is level with the street. Every part is drawn with the blocks that stand for the parts of a look (see
 * {@link Kit} and {@link Eastern}).
 */
final class EasternCastle {
    private static final String AIR = Structure.AIR;
    private static final String POST = Eastern.POST;
    private static final String PLANKS = Eastern.PLANKS;
    private static final String STAIRS = Eastern.STAIRS;
    private static final String FENCE = Eastern.FENCE;
    private static final String DOOR = Eastern.DOOR;
    private static final String FLOOR = Eastern.FLOOR;
    private static final String FLOOR_SLAB = Eastern.FLOOR_SLAB;
    private static final String FLOOR_STAIRS = Eastern.FLOOR_STAIRS;
    private static final String WOOD = Eastern.WOOD;
    private static final String PLASTER = Eastern.PLASTER;
    private static final String STONE = Eastern.STONE;
    private static final String STONE_STAIRS = Eastern.STONE_STAIRS;
    private static final String ROUGH = Eastern.ROUGH;
    private static final String TRIM = Eastern.TRIM;
    private static final Kit.Roof ROOF = Eastern.ROOF;
    private static final String RED = Eastern.RED;
    private static final String RED_BLOCK = Eastern.RED_BLOCK;
    private static final String GOLD = Eastern.GOLD;
    private static final String GRASS = Eastern.GRASS;
    private static final String GRAVEL = Eastern.GRAVEL;
    private static final String DIRT = "minecraft:dirt";
    private static final String IRON_DOOR = "minecraft:iron_door";
    /** The painted coffers of the ceiling of the throne hall: glazed tiles of gold and flowers, a fixed accent. */
    private static final String COFFER = "minecraft:yellow_glazed_terracotta";
    /** The green of the pine painted on the gold wall behind the throne, a fixed accent. */
    private static final String PAINT_GREEN = "minecraft:green_terracotta";

    private static final String[] POINT_NAMES = {"palace_hall", "palace_door", "castle_gate", "castle_wall", "treasury"};
    private static final int HALL = 0;
    private static final int PALACE_DOOR = 1;
    private static final int GATE = 2;
    private static final int WALK = 3;
    private static final int TREASURY = 4;

    /** The three sizes of the castle. */
    enum Size {
        SMALL, MEDIUM, LARGE
    }

    private EasternCastle() {
    }

    /**
     * The three castles of the style: small, medium and large, with their named places.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(building(Size.SMALL, "eastern_castle_small", "minecraft:red_concrete"));
        list.add(building(Size.MEDIUM, "eastern_castle_medium", "minecraft:red_concrete"));
        list.add(building(Size.LARGE, "eastern_castle_large", "minecraft:gold_block"));
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

    // ---------------------------------------------------------------- the plan of a size

    /**
     * The numbers of a castle of a size: its ground, walls, keep, hall, gate and towers, and the places named in it.
     */
    private static final class Plan {
        final Size size;
        /** The ground (square), the height of the box and the middle of the ground (the cell of the throne). */
        final int n;
        final int height;
        final int c;
        /** The outer face of the west and north walls, and of the east and south walls. */
        final int line;
        final int south;
        /** The top layer of the stone of the walls; one walks on the layer above. */
        final int rh;
        /** Where the flights of steps up to the walks start (their foot), climbing north. */
        final int stairZ;
        /** The walls of the first storey of the keep, and the layer of its floor (the top of the base). */
        final int kx1;
        final int kz1;
        final int kx2;
        final int kz2;
        final int kb;
        /** The storeys of the keep from the ground up: {inset from the first, height, overhang of its roof, step in}. */
        final int[][] storeys;
        /** The walls of the throne hall, the head of its walls (the eaves) and, for a hall with two roofs, of its upper walls. */
        final int hx1;
        final int hz1;
        final int hx2;
        final int hz2;
        final int hh;
        final int upper;
        /** The red pillars of the veranda before the hall: how far out of its front wall (0: none). */
        final int veranda;
        /** The north eave of the roof of a hall with one roof. */
        final int roofZ1;
        /** The doors in the back wall of the hall stand this far to the sides of the throne. */
        final int dd;
        /** The gatehouse: from gx1 to gx2, from gz1 to the south wall; the half width of its passage. */
        final int gx1;
        final int gx2;
        final int gz1;
        final int pass;
        /** The corner towers: {x1, z1, size, storeys}. */
        final int[][] towers;
        /** The garden beside the hall: {x1, z1, x2, z2}. */
        final int[] garden;
        /** Named places: {kind, x, y, z}. */
        final List<int[]> points = new ArrayList<>();

        Plan(Size size) {
            this.size = size;
            switch (size) {
                case SMALL -> {
                    n = 31;
                    height = 40;
                    rh = 4;
                    stairZ = 20;
                    kx1 = 10;
                    kz1 = 2;
                    kx2 = 20;
                    kz2 = 10;
                    kb = 6;
                    storeys = new int[][]{{0, 6, 2, 1}, {1, 5, 2, 1}, {2, 4, 2, 0}, {2, 4, 2, 0}};
                    hx1 = 10;
                    hz1 = 13;
                    hx2 = 20;
                    hz2 = 20;
                    hh = 7;
                    upper = 0;
                    veranda = 0;
                    roofZ1 = 10;
                    dd = 3;
                    gx1 = 11;
                    gx2 = 19;
                    gz1 = 26;
                    pass = 1;
                    towers = new int[][]{{2, 22, 7, 1}, {22, 22, 7, 1}};
                    garden = new int[]{22, 12, 25, 21};
                }
                case MEDIUM -> {
                    n = 41;
                    height = 44;
                    rh = 5;
                    stairZ = 28;
                    kx1 = 14;
                    kz1 = 2;
                    kx2 = 26;
                    kz2 = 12;
                    kb = 8;
                    storeys = new int[][]{{0, 6, 2, 1}, {1, 5, 2, 1}, {2, 5, 2, 1}, {3, 4, 2, 0}, {3, 4, 2, 0}};
                    hx1 = 14;
                    hz1 = 17;
                    hx2 = 26;
                    hz2 = 25;
                    hh = 8;
                    upper = 0;
                    veranda = 2;
                    roofZ1 = 13;
                    dd = 4;
                    gx1 = 16;
                    gx2 = 24;
                    gz1 = 34;
                    pass = 1;
                    towers = new int[][]{{2, 32, 7, 2}, {32, 32, 7, 2}, {32, 2, 7, 3}};
                    garden = new int[]{29, 11, 35, 31};
                }
                default -> {
                    n = 47;
                    height = 48;
                    rh = 5;
                    stairZ = 34;
                    kx1 = 16;
                    kz1 = 2;
                    kx2 = 30;
                    kz2 = 14;
                    kb = 9;
                    storeys = new int[][]{{0, 7, 2, 1}, {1, 6, 2, 1}, {2, 5, 2, 1}, {3, 5, 2, 0}, {3, 4, 2, 0}};
                    hx1 = 16;
                    hz1 = 20;
                    hx2 = 30;
                    hz2 = 30;
                    hh = 8;
                    upper = 13;
                    veranda = 2;
                    roofZ1 = 18;
                    dd = 4;
                    gx1 = 18;
                    gx2 = 28;
                    gz1 = 40;
                    pass = 2;
                    towers = new int[][]{{2, 38, 7, 2}, {38, 38, 7, 2}, {36, 2, 9, 3}, {2, 2, 7, 2}};
                    garden = new int[]{35, 12, 41, 36};
                }
            }
            c = (n - 1) / 2;
            line = 2;
            south = n - 1 - line;
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

    private static boolean solid(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && s.solid(x, y, z);
    }

    /** A cell given by its place along a line ({@code a}) and across it ({@code c}). */
    private static void at(Structure s, boolean alongX, int a, int y, int c, String state) {
        put(s, alongX ? a : c, y, alongX ? c : a, state);
    }

    /** The direction along a line running along x or along z, to the plus or the minus side. */
    private static String along(boolean alongX, int sign) {
        return alongX ? (sign > 0 ? "east" : "west") : (sign > 0 ? "south" : "north");
    }

    private static String stairs(String block, String facing, String half, String shape) {
        return block + "[facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=false]";
    }

    /** The corner stair of a ring that leans inward, its corner to the side (sx, sz) of the middle. */
    private static String cornerStair(String block, int sx, int sz) {
        return stairs(block, sz < 0 ? "south" : "north", "bottom", (sz < 0) == (sx < 0) ? "outer_left" : "outer_right");
    }

    /** Whether a block is a piece of a roof: a block, stair or slab of the roof stand-in. */
    private static boolean roofPiece(String state) {
        String id = States.id(state);
        return id.equals(ROOF.block()) || id.equals(ROOF.stairs()) || id.equals(ROOF.slab());
    }

    /** A banner of a colour hanging on the wall behind it, where that wall is a whole block (not a lattice or a pane). */
    private static void banner(Structure s, int x, int y, int z, String color, String facing) {
        int[] back = Furnish.step(Furnish.opposite(facing));
        if (s.inside(x + back[0], y, z + back[1]) && States.isFull(s.get(x + back[0], y, z + back[1])) && !solid(s, x, y, z)) {
            put(s, x, y, z, Kit.wallBanner(color, facing));
        }
    }

    /** A lantern hanging on a chain from the first solid block above it. */
    private static void hang(Structure s, int x, int y, int z) {
        put(s, x, y, z, Kit.hangingLantern());
        for (int yy = y + 1; yy < s.sizeY && !s.solid(x, yy, z); yy++) {
            s.set(x, yy, z, Kit.chain());
        }
    }

    /** A ladder from one layer to another facing the given way, with a column of planks behind it where nothing stands. */
    private static void ladder(Structure s, int x, int z, int y1, int y2, String facing) {
        int[] back = Furnish.step(Furnish.opposite(facing));
        for (int y = y1; y <= y2; y++) {
            if (!solid(s, x + back[0], y, z + back[1])) {
                put(s, x + back[0], y, z + back[1], PLANKS);
            }
            put(s, x, y, z, Kit.ladder(facing));
        }
    }

    // ---------------------------------------------------------------- the castle

    /**
     * Draws the castle of a plan: the ground and the walls, the base of the keep, the palace hall, the keep over them,
     * the vaults in the base, the gate, the corner towers, the court and the garden.
     */
    private static Structure castle(Plan p) {
        Structure s = new Structure(p.n, p.height, p.n);
        ground(s, p);
        walls(s, p);
        keepBase(s, p);
        hall(s, p);
        backFill(s, p);
        keep(s, p);
        vaults(s, p);
        hallInside(s, p);
        gate(s, p);
        for (int[] t : p.towers) {
            tower(s, p, t);
        }
        court(s, p);
        walkLights(s, p);
        // Grass only where nothing stands on it.
        for (int z = 0; z < p.n; z++) {
            for (int x = 0; x < p.n; x++) {
                if (s.get(x, 0, z).equals(GRASS) && s.solid(x, 1, z) && States.isFull(s.get(x, 1, z))) {
                    s.set(x, 0, z, DIRT);
                }
            }
        }
        s.skip(p.c, 0, p.c).skip(p.c, 1, p.c);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The ground: grass around, raked gravel in the court inside the walls, a paved way from the street through the gate to
     * the door of the hall.
     */
    private static void ground(Structure s, Plan p) {
        box(s, 0, 0, 0, p.n - 1, 0, p.n - 1, GRASS);
        int i1 = p.line + 3;
        int i2 = p.south - 3;
        box(s, i1, 0, i1, i2, 0, i2, GRAVEL);
        box(s, p.c - 1, 0, p.hz2 + 1, p.c + 1, 0, p.n - 1, STONE);
    }

    // ---------------------------------------------------------------- the walls

    /**
     * The walls of the enclosure: bases of stone battered at the foot with a white parapet under a coping of tiles and a walk
     * behind it, the north wall running into the base of the keep from both sides; and the flights of steps up to the walks
     * along the inner faces of the west and east walls.
     */
    private static void walls(Structure s, Plan p) {
        int a = p.line;
        int b = p.south;
        Eastern.rampart(s, a, a, a, b, 1, p.rh, "west");
        Eastern.rampart(s, b, a, b, b, 1, p.rh, "east");
        Eastern.rampart(s, a, b, b, b, 1, p.rh, "south");
        Eastern.rampart(s, a, a, p.kx1 - 1, a, 1, p.rh, "north");
        Eastern.rampart(s, p.kx2 + 1, a, b, a, 1, p.rh, "north");
        // the flights up to the walks, climbing north along the inner faces
        for (int x : new int[]{a + 3, b - 3}) {
            for (int k = 1; k <= p.rh; k++) {
                int z = p.stairZ - (k - 1);
                box(s, x, 1, z, x, k - 1, z, STONE);
                put(s, x, k, z, Kit.stairs(STONE_STAIRS, "north"));
                box(s, x, k + 1, z, x, k + 3, z, AIR);
            }
        }
        // guards' places on the west and east walks
        int w = p.rh + 1;
        for (int z : new int[]{p.c - 6, p.stairZ - p.rh - 1}) {
            p.point(WALK, a + 1, w, z);
            p.point(WALK, b - 1, w, z);
        }
    }

    /**
     * Lanterns standing on the inner edge of the walks every few blocks, wherever the walk is free there, so that no walk
     * lies dark at night.
     */
    private static void walkLights(Structure s, Plan p) {
        int a = p.line;
        int b = p.south;
        int y = p.rh + 1;
        for (int k = a + 4; k <= b - 4; k += 6) {
            int[][] cells = {{a + 2, k}, {b - 2, k}, {k, a + 2}, {k, b - 2}};
            for (int[] q : cells) {
                if (!solid(s, q[0], y, q[1]) && !solid(s, q[0], y + 1, q[1]) && s.get(q[0], y - 1, q[1]).equals(STONE)) {
                    put(s, q[0], y, q[1], Kit.lantern());
                }
            }
        }
    }

    // ---------------------------------------------------------------- the keep

    /** How far a face of the base of the keep has stepped in at layer {@code y}: evenly from the foot to the top. */
    private static int flare(int total, int y, int y1, int y2) {
        if (y2 <= y1) {
            return total;
        }
        return (int) Math.round(total * (double) (y - y1) / (y2 - y1));
    }

    /** The rectangle of the base of the keep at layer {@code y}: {x1, z1, x2, z2}. */
    private static int[] baseRect(Plan p, int y) {
        int y1 = 1;
        int y2 = p.kb - 1;
        int side = flare(2, y, y1, y2);
        int front = flare(p.hz1 - 1 - p.kz2, y, y1, y2);
        return new int[]{p.kx1 - 2 + side, p.kz1 - 2 + side, p.kx2 + 2 - side, p.hz1 - 1 - front};
    }

    /**
     * The base of the keep: stone from the ground up to the floor of the first storey, its faces leaning in by even steps
     * from a foot two blocks wider than the keep, every step a row of stairs, the corners dressed with long stones; a
     * course of rough stone at its foot. Its south foot reaches the back wall of the hall.
     */
    private static void keepBase(Structure s, Plan p) {
        int y2 = p.kb - 1;
        for (int y = 1; y <= y2; y++) {
            int[] r = baseRect(p, y);
            box(s, r[0], y, r[1], r[2], y, r[3], y == 1 ? ROUGH : STONE);
        }
        for (int y = 1; y < y2; y++) {
            int[] r = baseRect(p, y);
            int[] u = baseRect(p, y + 1);
            for (int z = r[1]; z <= r[3]; z++) {
                for (int x = r[0]; x <= r[2]; x++) {
                    boolean w = x < u[0];
                    boolean e = x > u[2];
                    boolean nn = z < u[1];
                    boolean ss = z > u[3];
                    if (!w && !e && !nn && !ss) {
                        continue;
                    }
                    String state;
                    if ((w || e) && (nn || ss)) {
                        state = cornerStair(STONE_STAIRS, w ? -1 : 1, nn ? -1 : 1);
                    } else if (w) {
                        state = Kit.stairs(STONE_STAIRS, "east");
                    } else if (e) {
                        state = Kit.stairs(STONE_STAIRS, "west");
                    } else if (nn) {
                        state = Kit.stairs(STONE_STAIRS, "south");
                    } else {
                        state = Kit.stairs(STONE_STAIRS, "north");
                    }
                    put(s, x, y, z, state);
                }
            }
        }
        // quoins: the corners of the upper base of long dressed stones of trim, every second course
        int[] top = baseRect(p, y2);
        for (int y = 2; y <= y2; y += 2) {
            int[] r = baseRect(p, y);
            if (r[0] == top[0]) {
                put(s, r[0], y, r[1], TRIM);
                put(s, r[2], y, r[1], TRIM);
            }
        }
    }

    /**
     * Fills the space between the base of the keep and the back wall of the hall with stone, up to the roof of the hall,
     * so that the hall leans against the base and nothing is left hollow under its roof.
     */
    private static void backFill(Structure s, Plan p) {
        for (int z = p.kz2 + 1; z < p.hz1; z++) {
            for (int x = p.hx1 - 1; x <= p.hx2 + 1; x++) {
                // up to the roof of the hall over it, or to the top of the base where no roof covers it
                int top = p.kb - 1;
                for (int y = 1; y <= p.hh + 10; y++) {
                    if (roofPiece(s.get(x, y, z))) {
                        top = y - 1;
                        break;
                    }
                }
                for (int y = 1; y <= top; y++) {
                    if (!s.solid(x, y, z) || s.get(x, y, z).contains("_stairs")) {
                        s.set(x, y, z, STONE);
                    }
                }
            }
        }
    }

    /**
     * The keep: its storeys of white plaster between dark posts from the base up, each under a skirt roof with sweeping
     * corners that steps in to the next storey, little gables on the skirts (on the sides of the first, on the front and back
     * of the second, and so on), rows of dark lattice windows, ladders from storey to storey; the top storey ringed by a red
     * balustrade under a hip-and-gable roof with gilt ornaments on the ends of its ridge. Each storey is furnished (see
     * {@link #keepRoom}).
     */
    private static void keep(Structure s, Plan p) {
        int f = p.kb;
        int last = p.storeys.length - 1;
        String gablesBelow = "";
        int rBelow = 0;
        List<int[]> ladders = new ArrayList<>();
        for (int i = 0; i <= last; i++) {
            int[] st = p.storeys[i];
            int x1 = p.kx1 + st[0];
            int z1 = p.kz1 + st[0];
            int x2 = p.kx2 - st[0];
            int z2 = p.kz2 - st[0];
            int head = f + st[1];
            // white walls with dark posts only at the corners; above the first storey the sill is white too
            Eastern.frame(s, x1, z1, x2, z2, f, head, 99, Kit.log(POST, "y"), PLASTER);
            if (i > 0) {
                for (int x = x1 + 1; x < x2; x++) {
                    put(s, x, f, z1, PLASTER);
                    put(s, x, f, z2, PLASTER);
                }
                for (int z = z1 + 1; z < z2; z++) {
                    put(s, x1, f, z, PLASTER);
                    put(s, x2, f, z, PLASTER);
                }
            }
            box(s, x1 + 1, f, z1 + 1, x2 - 1, f, z2 - 1, FLOOR);
            box(s, x1 + 1, f + 1, z1 + 1, x2 - 1, head - 1, z2 - 1, AIR);
            box(s, x1 + 1, head, z1 + 1, x2 - 1, head, z2 - 1, PLANKS);
            lattices(s, x1, z1, x2, z2, f + 2, 2, i == last ? 1 : 2, gablesBelow, rBelow);
            if (i < last) {
                int ovh = st[2];
                int step = st[3];
                int next = Eastern.skirt(s, x1, z1, x2, z2, head, ovh, step, ovh >= 3 ? 2 : 1, ROOF);
                box(s, x1 + 1, head + 1, z1 + 1, x2 - 1, next, z2 - 1, PLANKS);
                gablesBelow = "";
                if (step > 0) {
                    boolean sides = i % 2 == 0;
                    int len = sides ? z2 - z1 : x2 - x1;
                    int r = len >= 10 ? 2 : 1;
                    if (sides) {
                        int mz = (z1 + z2) / 2;
                        dormer(s, "west", mz, x1 - ovh, ovh, head, r, false);
                        dormer(s, "east", mz, x2 + ovh, ovh, head, r, false);
                        gablesBelow = "west east";
                    } else {
                        int mx = (x1 + x2) / 2;
                        dormer(s, "north", mx, z1 - ovh, ovh, head, r, false);
                        dormer(s, "south", mx, z2 + ovh, ovh, head, r, i > 0);
                        gablesBelow = "north south";
                    }
                    rBelow = r;
                }
                // the ladder up, in the north-east of the storey above
                int[] up = p.storeys[i + 1];
                int lx = p.kx2 - up[0] - 1;
                int lz = p.kz1 + up[0] + 1;
                ladders.add(new int[]{lx, lz, f + 1, next});
                keepRoom(s, p, i, x1, z1, x2, z2, f, head);
                f = next;
            } else {
                int ovh = st[2];
                if (i > 0 && p.storeys[i - 1][3] == 0) {
                    Eastern.balcony(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, f + 1);
                }
                int rx1 = x1 - ovh;
                int rz1 = z1 - ovh;
                int rx2 = x2 + ovh;
                int rz2 = z2 + ovh;
                int rings = Math.min(rx2 - rx1, rz2 - rz1) / 2;
                int hip = Math.max(1, rings / 2);
                int top = Eastern.roof(s, rx1, rz1, rx2, rz2, head, hip, 1, 2, ROOF, PLASTER);
                // gilt ornaments (shachi) on both ends of the ridge
                int mz = rz1 + rings;
                int from = rx1 + hip - 1;
                int to = rx2 - hip + 1;
                put(s, from, top + 1, mz, GOLD);
                put(s, to, top + 1, mz, GOLD);
                put(s, (x1 + x2) / 2, head + 1, (z1 + z2) / 2, Kit.lantern());
                keepRoom(s, p, i, x1, z1, x2, z2, f, head);
            }
        }
        // the ladders last, through the floors laid over them
        for (int[] l : ladders) {
            ladder(s, l[0], l[1], l[2], l[3], "south");
        }
    }

    /**
     * Windows of dark lattice (fence slats) in the faces of a frame on the rectangle, {@code h} rows from layer {@code y},
     * symmetric about the middle of every face, every second panel; {@code margin} keeps them that far from the corners. The
     * faces named in {@code skip} keep the middle {@code skipHalf} cells to each side blank (a gable stands before them).
     */
    private static void lattices(Structure s, int x1, int z1, int x2, int z2, int y, int h, int margin, String skip, int skipHalf) {
        for (String face : new String[]{"north", "south", "west", "east"}) {
            boolean alongX = face.equals("north") || face.equals("south");
            int a1 = alongX ? x1 : z1;
            int a2 = alongX ? x2 : z2;
            int line = face.equals("north") ? z1 : face.equals("south") ? z2 : face.equals("west") ? x1 : x2;
            int m = (a1 + a2) / 2;
            boolean skipped = skip.contains(face);
            for (int a = a1 + margin; a <= a2 - margin; a++) {
                if (Math.floorMod(a - m, 2) != 0 || skipped && Math.abs(a - m) <= skipHalf) {
                    continue;
                }
                int px = alongX ? a : line;
                int pz = alongX ? line : a;
                if (s.get(px, y, pz).startsWith(POST)) {
                    continue;
                }
                for (int yy = y; yy < y + h; yy++) {
                    at(s, alongX, a, yy, line, FENCE);
                }
            }
        }
    }

    /**
     * A little gable (chidori-hafu) standing on the slope of a roof: a steep gable roof whose ridge runs out to the eave,
     * its face of white plaster framed in timber one block inside the eave, its body filled under its slopes. It stands on
     * the eave {@code front} (the row of the eave slab at layer {@code y}), looks the way of {@code facing}, is centred on
     * {@code m} along the eave, reaches {@code depth} rows back into the roof and has a face {@code 2r + 1} wide at its foot;
     * with {@code gilt} a gilt ornament hangs in its apex.
     */
    private static void dormer(Structure s, String facing, int m, int front, int depth, int y, int r, boolean gilt) {
        boolean alongX = facing.equals("north") || facing.equals("south");
        int out = facing.equals("south") || facing.equals("east") ? 1 : -1;
        for (int d = 0; d <= depth; d++) {
            int row = front - out * d;
            for (int j = -(r + 1); j <= r + 1; j++) {
                int aj = Math.abs(j);
                int a = m + j;
                int stairLayer = y + 1 + (r + 1 - aj);
                if (aj >= 1) {
                    at(s, alongX, a, stairLayer, row, Kit.stairs(ROOF.stairs(), along(alongX, j < 0 ? 1 : -1)));
                } else {
                    at(s, alongX, a, y + 1 + r, row, ROOF.block());
                    at(s, alongX, a, y + 2 + r, row, Kit.slab(ROOF.slab()));
                }
                if (d >= 1 && aj <= r) {
                    int under = aj >= 1 ? stairLayer - 1 : y + r;
                    for (int yy = y + 1; yy <= under; yy++) {
                        String state;
                        if (d == 1) {
                            if (yy == y + 1) {
                                state = Kit.log(POST, alongX ? "x" : "z");
                            } else if (j == 0) {
                                state = gilt && yy == under ? GOLD : Kit.log(POST, "y");
                            } else {
                                state = PLASTER;
                            }
                        } else {
                            state = ROOF.block();
                        }
                        at(s, alongX, a, yy, row, state);
                    }
                }
            }
        }
    }

    /**
     * The furniture of a storey of the keep, by its place: the council room on the first storey (a long low table with
     * cushions, the alcove with its scroll, a shrine), the lord's chamber on the second (a bed of state, chests of drawers,
     * a folding screen), a study on the third of the larger keeps (shelves of books and a desk), a tea room under the top and
     * the lookout at the top; mats on the floors, paper lamps and lanterns everywhere.
     */
    private static void keepRoom(Structure s, Plan p, int i, int x1, int z1, int x2, int z2, int f, int head) {
        int last = p.storeys.length - 1;
        int y = f + 1;
        int mx = (x1 + x2) / 2;
        int mz = (z1 + z2) / 2;
        int ix1 = x1 + 1;
        int iz1 = z1 + 1;
        int ix2 = x2 - 1;
        int iz2 = z2 - 1;
        // mats over the floor where nothing opens in it
        for (int z = iz1; z <= iz2; z++) {
            for (int x = ix1; x <= ix2; x++) {
                if (s.get(x, f, z).equals(FLOOR)) {
                    boolean edge = x == ix1 || x == ix2 || z == iz1 || z == iz2;
                    s.set(x, f, z, edge ? FLOOR : Eastern.TATAMI);
                }
            }
        }
        // light: lanterns under the ceiling and paper lamps in the corners
        put(s, mx, head - 1, mz, Kit.hangingLantern());
        if (ix2 - ix1 >= 6) {
            put(s, ix1 + 1, head - 1, mz, Kit.hangingLantern());
            put(s, ix2 - 1, head - 1, mz, Kit.hangingLantern());
        }
        boolean firstRoom = i == 0;
        if (i == last) {
            // the lookout: a low table with cushions and lamps, the view all round
            Eastern.lowTable(s, mx, mz, mx, mz, y);
            Eastern.paperLamp(s, ix1, y, iz2);
            Eastern.paperLamp(s, ix2, y, iz2);
            return;
        }
        int role = p.small() && i == 2 ? 3 : i;
        switch (role) {
            case 0 -> {
                // the council room
                Eastern.lowTable(s, mx - 1, mz, mx + 2, mz, y);
                Eastern.alcove(s, mx + 1, y, iz1, "south");
                Eastern.shrine(s, ix2, y, mz + 1, "west");
                Eastern.paperLamp(s, ix2, y, iz2);
                banner(s, ix2 - 1, y + 2, iz2, "red", "north");
                Eastern.cabinet(s, mx - 2, y, iz1, 2);
                Eastern.cabinet(s, mx + 2, y, iz1, 1);
                if (ix2 - ix1 >= 10) {
                    // the larger council room: a second table for the scribes, a folding screen, banners of the kingdom
                    Eastern.lowTable(s, mx - 1, mz + 3, mx + 1, mz + 3, y);
                    Eastern.foldingScreen(s, ix2 - 3, iz2, ix2 - 3, iz2 - 1, y);
                    banner(s, mx - 1, y + 2, iz2, "yellow", "north");
                    banner(s, mx + 1, y + 2, iz2, "yellow", "north");
                }
            }
            case 1 -> {
                // the lord's chamber
                Furnish.canopyBed(s, ix1 + 1, y, iz2 - 1, "north", WOOD);
                Eastern.cabinet(s, ix2, y, iz2, 2);
                Eastern.cabinet(s, ix2 - 1, y, iz2, 1);
                Eastern.foldingScreen(s, mx, iz2, mx, iz2 - 1, y);
                Eastern.alcove(s, mx + 1, y, iz1, "south");
                Eastern.lowTable(s, mx + 2, mz, mx + 2, mz, y);
                Eastern.paperLamp(s, ix1, y, iz1);
                Furnish.rug(s, ix1 + 2, iz2 - 3, ix1 + 2, iz2 - 1, y, Kit.CLOTH_CARPET);
            }
            case 2 -> {
                // the study: shelves of books along the west wall, a desk under the window
                Furnish.books(s, ix1, iz1, ix1, iz2, y, y + 1);
                Furnish.desk(s, mx, y, iz2, "north", WOOD);
                Eastern.paperLamp(s, ix2, y, iz2);
                Eastern.cabinet(s, ix2, y, mz, 1);
            }
            default -> {
                // the tea room under the top: a low table, the alcove, a lamp
                Eastern.lowTable(s, mx, mz, mx, mz, y);
                Eastern.alcove(s, mx + 1, y, iz1, "south");
                Eastern.paperLamp(s, ix1, y, iz2);
            }
        }
        if (firstRoom) {
            Eastern.paperLamp(s, ix1, y, iz1);
        }
    }

    // ---------------------------------------------------------------- the vaults in the base

    /**
     * The two passages from the back wall of the hall into the base of the keep: on the west a door and a straight stair
     * climbing north under the keep up into its first storey (a railing round the opening, lanterns in niches); on the east
     * an iron door into the treasury, a vault of stone with chests, gold and a counting table.
     */
    private static void vaults(Structure s, Plan p) {
        int c = p.c;
        // the stair up into the keep
        int sx = c - p.dd;
        box(s, sx, 1, p.hz1 - 1, sx, 3, p.hz1 - 1, AIR);
        for (int k = 1; k <= p.kb; k++) {
            int z = p.hz1 - 1 - k;
            put(s, sx, k, z, Kit.stairs(FLOOR_STAIRS, "north"));
            if (k > 1) {
                put(s, sx, k - 1, z, PLANKS);
            }
            box(s, sx, k + 1, z, sx, k + 3, z, AIR);
        }
        // the railing round the opening in the floor of the keep
        for (int k = p.kb - 3; k <= p.kb; k++) {
            int z = p.hz1 - 1 - k;
            if (z < p.kz2) {
                for (int x : new int[]{sx - 1, sx + 1}) {
                    if (!solid(s, x, p.kb + 1, z)) {
                        put(s, x, p.kb + 1, z, FENCE);
                    }
                }
            }
        }
        // lanterns in niches beside the stair
        put(s, sx + 1, 2, p.hz1 - 1, Kit.lantern());
        int mid = Math.max(2, p.kb / 2);
        put(s, sx + 1, mid + 1, p.hz1 - 1 - mid, Kit.lantern());
        s.door(sx, 1, p.hz1, DOOR, "north", "left");
        // the treasury
        int vx1 = c + 1;
        int vx2 = c + p.dd + 1;
        int vz2 = p.hz1 - 1;
        int vz1 = vz2 - (p.small() ? 4 : 5);
        int vh = Math.min(4, p.kb - 3);
        box(s, vx1, 1, vz1, vx2, vh, vz2, AIR);
        box(s, vx1, 0, vz1, vx2, 0, vz2, STONE);
        s.door(c + p.dd, 1, p.hz1, IRON_DOOR, "north", "left");
        // buttons that open the iron door, in the hall and in the vault
        put(s, c + p.dd + 1, 2, p.hz1 + 1, "minecraft:stone_button[face=wall,facing=south,powered=false]");
        put(s, c + p.dd - 1, 2, p.hz1 - 1, "minecraft:stone_button[face=wall,facing=north,powered=false]");
        Furnish.chest(s, vx1, 1, vz1, "south");
        Furnish.chest(s, vx2, 1, vz1, "south");
        put(s, vx1 + 1, 1, vz1, GOLD);
        put(s, vx1 + 2, 1, vz1, "minecraft:raw_gold_block");
        put(s, vx1 + 1, 2, vz1, GOLD);
        put(s, vx1, 1, vz1 + 1, GOLD);
        // iron bars before the hoard
        for (int x = vx1; x <= vx2; x++) {
            if (x != c + p.dd) {
                put(s, x, 1, vz1 + 2, Kit.BARS);
                put(s, x, 2, vz1 + 2, Kit.BARS);
            }
        }
        // the counting table by the door
        put(s, vx1, 1, vz2, Kit.topSlab(FLOOR_SLAB));
        put(s, vx1, 2, vz2, Furnish.candles(3));
        put(s, vx1, 1, vz2 - 1, Kit.stairs(FLOOR_STAIRS, "south"));
        hang(s, vx1 + 1, vh, vz1 + 1);
        hang(s, vx2, vh, vz2 - 1);
        p.point(TREASURY, c + p.dd, 1, p.hz1 + 1);
    }

    // ---------------------------------------------------------------- the throne hall

    /**
     * The palace hall: a frame of dark posts and white walls, its front a row of red pillars with paper screens between
     * them and the door in the middle, paper windows on its sides; in the larger castles a veranda of red pillars with
     * bracket sets before it; one wide hip-and-gable roof with sweeping corners, or for the large castle a skirt roof over the
     * veranda and the upper walls under the main roof, with a gilt gable over the door.
     */
    private static void hall(Structure s, Plan p) {
        int x1 = p.hx1;
        int x2 = p.hx2;
        int z1 = p.hz1;
        int z2 = p.hz2;
        int c = p.c;
        int hh = p.hh;
        Eastern.frame(s, x1, z1, x2, z2, 0, hh, 0, Kit.log(POST, "y"), PLASTER);
        // the front: red pillars, paper screens between them, the door in the middle
        for (int x = x1 + 1; x < x2; x++) {
            if (Math.floorMod(x - c, 2) == 1) {
                box(s, x, 1, z2, x, hh - 1, z2, RED);
            } else {
                Eastern.screen(s, x, z2, x, z2, 1, hh - 1, false);
            }
        }
        s.door(c, 1, z2, DOOR, "north", "left");
        // paper windows on the sides
        for (int z = z1 + 2; z <= z2 - 2; z += 3) {
            if (!s.get(x1, 4, z).startsWith(POST)) {
                Eastern.window(s, x1, 3, z, "west", 1, 2);
                Eastern.window(s, x2, 3, z, "east", 1, 2);
            }
        }
        if (p.veranda > 0) {
            veranda(s, p);
        }
        if (p.upper > 0) {
            int ovh = p.veranda + 2;
            Eastern.skirt(s, x1, z1, x2, z2, hh, ovh, 0, 2, ROOF);
            Eastern.frame(s, x1, z1, x2, z2, hh, p.upper, 2, Kit.log(POST, "y"), PLASTER);
            for (int x = x1 + 1; x < x2; x++) {
                if (Math.floorMod(x - x1, 2) == 1) {
                    box(s, x, hh + 2, z2, x, hh + 3, z2, Eastern.PAPER);
                    box(s, x, hh + 2, z1, x, hh + 3, z1, Eastern.PAPER);
                }
            }
            for (int z = z1 + 1; z < z2; z++) {
                if (Math.floorMod(z - z1, 2) == 1) {
                    box(s, x1, hh + 2, z, x1, hh + 3, z, Eastern.PAPER);
                    box(s, x2, hh + 2, z, x2, hh + 3, z, Eastern.PAPER);
                }
            }
            int rx1 = x1 - 2;
            int rz1 = z1 - 2;
            int rx2 = x2 + 2;
            int rz2 = z2 + 2;
            int rings = Math.min(rx2 - rx1, rz2 - rz1) / 2;
            // the gable over the door first, so that the roof keeps its surface where the two meet
            dormer(s, "south", c, rz2, 4, p.upper, 3, true);
            Eastern.roof(s, rx1, rz1, rx2, rz2, p.upper, Math.max(2, rings / 3), 1, 3, ROOF, PLASTER);
        } else {
            int rx1 = x1 - 2;
            int rz1 = p.roofZ1;
            int rx2 = x2 + 2;
            int rz2 = z2 + p.veranda + 2;
            int rings = Math.min(rx2 - rx1, rz2 - rz1) / 2;
            if (!p.small()) {
                dormer(s, "south", c, rz2, 4, hh, 3, true);
            }
            Eastern.roof(s, rx1, rz1, rx2, rz2, hh, Math.max(2, rings / 3), 1, rings >= 8 ? 3 : 2, ROOF, PLASTER);
            // the front and the back walls go up to the roof where it stands high over them
            for (int x = x1; x <= x2; x++) {
                for (int z : new int[]{z1, z2}) {
                    for (int y = hh + 1; y < hh + 8 && !solid(s, x, y, z); y++) {
                        s.set(x, y, z, PLASTER);
                    }
                }
            }
        }
        // the attic under the roof is lit
        int top = p.upper > 0 ? p.upper : hh;
        put(s, c - 3, top + 1, (z1 + z2) / 2, Kit.lantern());
        put(s, c + 3, top + 1, (z1 + z2) / 2, Kit.lantern());
        if (p.small()) {
            // lanterns of cloth under the eaves by the door
            Eastern.clothLantern(s, c - 2, z2 + 1, hh - 1, 0);
            Eastern.clothLantern(s, c + 2, z2 + 1, hh - 1, 0);
        }
    }

    /**
     * The veranda of the hall: red pillars on bases of trim before the front (and, for a hall with two roofs, the sides) with
     * a beam and bracket sets on their heads, a floor of boards, lanterns of cloth under its ceiling, and the space over the
     * ceiling closed with boards up to the roof.
     */
    private static void veranda(Structure s, Plan p) {
        int x1 = p.hx1;
        int x2 = p.hx2;
        int z1 = p.hz1;
        int z2 = p.hz2;
        int v = p.veranda;
        int c = p.c;
        int beam = p.hh - 2;
        boolean sides = p.upper > 0;
        int px1 = sides ? x1 - v : x1;
        int px2 = sides ? x2 + v : x2;
        int pz = z2 + v;
        // the floor of boards with a lip of trim
        box(s, px1, 0, z2 + 1, px2, 0, pz, FLOOR);
        box(s, px1, 0, pz, px2, 0, pz, TRIM);
        if (sides) {
            box(s, px1, 0, z1, x1 - 1, 0, pz, FLOOR);
            box(s, x2 + 1, 0, z1, px2, 0, pz, FLOOR);
            box(s, px1, 0, z1, px1, 0, pz, TRIM);
            box(s, px2, 0, z1, px2, 0, pz, TRIM);
        }
        // the beams on the pillars
        box(s, px1, beam, pz, px2, beam, pz, Kit.log(POST, "x"));
        if (sides) {
            box(s, px1, beam, z1, px1, beam, pz - 1, Kit.log(POST, "z"));
            box(s, px2, beam, z1, px2, beam, pz - 1, Kit.log(POST, "z"));
        }
        // the pillars and their bracket sets, every four blocks from the middle
        for (int x = px1; x <= px2; x++) {
            if (Math.floorMod(x - c, 4) == 2 || x == px1 || x == px2) {
                Eastern.pillar(s, x, 1, beam - 1, pz, false);
                Eastern.bracket(s, x, beam, pz, "south", 2);
            }
        }
        if (sides) {
            for (int z = z1; z < pz; z++) {
                if (Math.floorMod(pz - z, 4) == 0) {
                    for (int x : new int[]{px1, px2}) {
                        Eastern.pillar(s, x, 1, beam - 1, z, false);
                        Eastern.bracket(s, x, beam, z, x == px1 ? "west" : "east", 2);
                    }
                }
            }
        }
        // the ceiling of the veranda and the closed space above it
        for (int z = z2 + 1; z < pz; z++) {
            for (int x = px1; x <= px2; x++) {
                put(s, x, beam, z, PLANKS);
            }
        }
        if (sides) {
            for (int z = z1; z <= z2; z++) {
                for (int x = px1 + 1; x < x1; x++) {
                    put(s, x, beam, z, PLANKS);
                }
                for (int x = x2 + 1; x < px2; x++) {
                    put(s, x, beam, z, PLANKS);
                }
            }
        }
        // lanterns of cloth under the ceiling of the veranda
        for (int x = c - 4; x <= c + 4; x += 4) {
            Eastern.clothLantern(s, x, z2 + 1, beam - 1, 0);
        }
        if (sides) {
            for (int z : new int[]{z1 + 1, (z1 + z2) / 2 + 1}) {
                Eastern.clothLantern(s, x1 - 1, z, beam - 1, 0);
                Eastern.clothLantern(s, x2 + 1, z, beam - 1, 0);
            }
        }
    }

    /**
     * Closes the space over the ceiling of the veranda up to the roof with boards, once the roofs are drawn.
     */
    private static void closeVeranda(Structure s, Plan p) {
        if (p.veranda == 0) {
            return;
        }
        boolean sides = p.upper > 0;
        int beam = p.hh - 2;
        int px1 = sides ? p.hx1 - p.veranda : p.hx1;
        int px2 = sides ? p.hx2 + p.veranda : p.hx2;
        int pz = p.hz2 + p.veranda;
        for (int z = sides ? p.hz1 : p.hz2 + 1; z <= pz; z++) {
            for (int x = px1; x <= px2; x++) {
                boolean outside = z > p.hz2 || x < p.hx1 || x > p.hx2;
                if (!outside) {
                    continue;
                }
                for (int y = beam + 1; y < beam + 8 && !solid(s, x, y, z); y++) {
                    s.set(x, y, z, PLANKS);
                }
            }
        }
    }

    /**
     * The inside of the throne hall: mats bordered with boards, the throne's place at the north end on a dais of dark
     * boards before the wall of gold with its painted pine, paper lamps and lacquered stands beside it; red pillars in two
     * rows, a ceiling of painted coffers between dark beams, a red runner from the door to the dais, cushions for the
     * retainers, chandeliers and lamps, banners on the walls; the places of the guards.
     */
    private static void hallInside(Structure s, Plan p) {
        closeVeranda(s, p);
        int c = p.c;
        int x1 = p.hx1;
        int x2 = p.hx2;
        int z1 = p.hz1;
        int z2 = p.hz2;
        int top = p.upper > 0 ? p.upper : p.hh;
        Eastern.tatami(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, 0, true);
        // the dais: dark boards edged with red lacquer
        box(s, c - 2, 0, z1 + 1, c + 2, 0, c + 1, PLANKS);
        box(s, c - 2, 0, c + 1, c + 2, 0, c + 1, RED_BLOCK);
        // the wall of gold with a painted pine behind the throne
        goldWall(s, c, z1, p.dd - 1, Math.min(5, top - 2));
        // lacquered stands with candles and lamps beside the throne
        put(s, c - 2, 1, c - 1, Kit.topSlab(Eastern.RED_SLAB));
        put(s, c - 2, 2, c - 1, Furnish.candles(3));
        put(s, c + 2, 1, c - 1, Kit.topSlab(Eastern.RED_SLAB));
        put(s, c + 2, 2, c - 1, Furnish.candles(3));
        // the red pillars in two rows
        int px = p.small() ? 3 : p.large() ? 4 : 3;
        int[] rows = p.small() ? new int[]{c + 1, c + 3} : p.large() ? new int[]{c - 1, c + 2, c + 5} : new int[]{c - 1, c + 2};
        for (int z : rows) {
            for (int side = -1; side <= 1; side += 2) {
                int x = c + side * px;
                put(s, x, 1, z, TRIM);
                box(s, x, 2, z, x, top - 1, z, RED);
                put(s, x - side, top - 1, z, Kit.upside(STAIRS, side < 0 ? "west" : "east"));
                put(s, x + side, top - 1, z, Kit.upside(STAIRS, side < 0 ? "east" : "west"));
            }
        }
        // the ceiling of painted coffers between dark beams
        for (int z = z1 + 1; z <= z2 - 1; z++) {
            for (int x = x1 + 1; x <= x2 - 1; x++) {
                boolean beamX = Math.floorMod(z - z1, 3) == 0;
                boolean beamZ = Math.floorMod(x - c, 3) == 0;
                String state;
                if (beamX) {
                    state = Kit.log(POST, "x");
                } else if (beamZ) {
                    state = Kit.log(POST, "z");
                } else {
                    String[] turn = {"north", "east", "south", "west"};
                    state = COFFER + "[facing=" + turn[Math.floorMod(x + 2 * z, 4)] + "]";
                }
                put(s, x, top, z, state);
            }
        }
        // the runner from the door to the dais, cushions along the sides for the retainers
        box(s, c, 1, c + 1, c, 1, z2 - 1, Kit.CLOTH_CARPET);
        if (!p.small()) {
            for (int z = c + 4; z <= z2 - 2; z += 2) {
                put(s, c - 2, 1, z, Kit.CLOTH_CARPET);
                put(s, c + 2, 1, z, Kit.CLOTH_CARPET);
            }
        }
        // chandeliers along the aisle and paper lamps along the walls
        for (int z = c + 2; z <= z2 - 1; z += 3) {
            Furnish.chandelier(s, c, z, top - 1, p.small() ? 1 : 2);
        }
        Furnish.chandelier(s, c - px, c, top - 1, 1);
        Furnish.chandelier(s, c + px, c, top - 1, 1);
        for (int z = c + 2; z <= z2 - 1; z += 3) {
            Eastern.paperLamp(s, x1 + 1, 1, z);
            Eastern.paperLamp(s, x2 - 1, 1, z);
        }
        // banners of the kingdom on the side walls
        for (int z = z1 + 2; z <= z2 - 2; z += 2) {
            if (s.get(x1, 4, z).equals(PLASTER) && !solid(s, x1 + 1, 4, z)) {
                put(s, x1 + 1, 4, z, Kit.wallBanner(Math.floorMod(z, 4) == 0 ? "yellow" : "red", "east"));
            }
            if (s.get(x2, 4, z).equals(PLASTER) && !solid(s, x2 - 1, 4, z)) {
                put(s, x2 - 1, 4, z, Kit.wallBanner(Math.floorMod(z, 4) == 0 ? "yellow" : "red", "west"));
            }
        }
        // the places of the guards, the door and the hall
        p.point(HALL, c - 2, 1, c + 3);
        p.point(HALL, c + 2, 1, c + 3);
        p.point(HALL, c - 2, 1, c + 1);
        p.point(HALL, c + 2, 1, c + 1);
        p.point(PALACE_DOOR, c - 1, 1, z2 - 1);
        p.point(PALACE_DOOR, c + 1, 1, z2 - 1);
    }

    /**
     * The wall of gold behind the throne: gold from the floor up in the back wall {@code half} blocks to each side of the
     * throne, {@code high} layers high, with a pine painted on it, its trunk leaning and its pads spreading over the throne.
     */
    private static void goldWall(Structure s, int c, int z, int half, int high) {
        box(s, c - half, 1, z, c + half, high, z, GOLD);
        String trunk = Kit.log(POST, "y");
        put(s, c - 1, 1, z, trunk);
        put(s, c - 1, 2, z, trunk);
        put(s, c, 3, z, trunk);
        put(s, c, Math.min(high, 4), z, trunk);
        for (int dx = -half + 1; dx <= 1; dx++) {
            put(s, c + dx, high, z, PAINT_GREEN);
        }
        put(s, c + 1, high - 1, z, PAINT_GREEN);
        put(s, c + 2, high - 1, z, PAINT_GREEN);
        if (half >= 3) {
            put(s, c + 3, high - 1, z, PAINT_GREEN);
            put(s, c - 2, high - 2, z, PAINT_GREEN);
            put(s, c - 3, high - 2, z, PAINT_GREEN);
        }
    }

    // ---------------------------------------------------------------- the gate

    /**
     * The main gate in the middle of the south wall: a passage through the wall lined with dark posts, its leaves standing
     * open and a banner of the kingdom over its mouth; over it a gatehouse of white plaster with lattice windows and doors
     * to the walks on both sides, under a heavy hip-and-gable roof with a gilt gable over the passage. In the larger castles
     * the inner half of the gatehouse stands over the court on red pillars.
     */
    private static void gate(Structure s, Plan p) {
        int c = p.c;
        int b = p.south;
        int rh = p.rh;
        int x1 = p.gx1;
        int x2 = p.gx2;
        int z1 = p.gz1;
        int pass = p.pass;
        // clear the parapet where the gatehouse stands, cut the passage
        box(s, x1, rh + 1, b - 2, x2, rh + 6, b + 1, AIR);
        box(s, c - pass, 1, z1, c + pass, rh - 1, b + 1, AIR);
        box(s, c - pass, 0, z1, c + pass, 0, b + 1, TRIM);
        for (int x : new int[]{c - pass - 1, c + pass + 1}) {
            Eastern.pillar(s, x, 1, rh - 1, b, false);
            Eastern.pillar(s, x, 1, rh - 1, b - 2, false);
        }
        // the inner half on red pillars over the court
        if (z1 < b - 2) {
            box(s, x1, 1, z1, x2, rh - 1, b - 3, AIR);
            box(s, x1, 0, z1, x2, 0, b - 3, STONE);
            box(s, x1, rh, z1, x2, rh, b - 3, PLANKS);
            for (int x : new int[]{x1, c - pass - 1, c + pass + 1, x2}) {
                Eastern.pillar(s, x, 1, rh - 1, z1, false);
            }
            box(s, x1, rh, z1, x2, rh, z1, Kit.log(POST, "x"));
            for (int x = x1 + 1; x < x2; x++) {
                if (x < c - pass - 1 || x > c + pass + 1) {
                    hang(s, x, rh - 1, z1 + 1);
                    x += 2;
                }
            }
        }
        // the gatehouse
        int head = rh + 5;
        Eastern.frame(s, x1, z1, x2, b, rh, head, 0, Kit.log(POST, "y"), PLASTER);
        box(s, x1 + 1, rh, z1 + 1, x2 - 1, rh, b - 1, FLOOR);
        box(s, x1 + 1, rh + 1, z1 + 1, x2 - 1, head - 1, b - 1, AIR);
        box(s, x1 + 1, head, z1 + 1, x2 - 1, head, b - 1, PLANKS);
        lattices(s, x1, z1, x2, b, rh + 2, 2, 2, "south", 2);
        s.door(x1, rh + 1, b - 1, DOOR, "east", "left");
        s.door(x2, rh + 1, b - 1, DOOR, "west", "left");
        put(s, c, head - 1, (z1 + b) / 2, Kit.hangingLantern());
        // a bench and a rack for the guards inside
        put(s, c - 2, rh + 1, z1 + 1, Kit.stairs(FLOOR_STAIRS, "north"));
        put(s, c + 2, rh + 1, z1 + 1, Kit.stairs(FLOOR_STAIRS, "north"));
        // the heavy roof and the gilt gable over the passage
        int rx1 = x1 - 2;
        int rz1 = z1 - 2;
        int rx2 = x2 + 2;
        int rz2 = b + 2;
        int rings = Math.min(rx2 - rx1, rz2 - rz1) / 2;
        dormer(s, "south", c, rz2, Math.min(3, rings), head, p.small() ? 2 : 3, true);
        int ridge = Eastern.roof(s, rx1, rz1, rx2, rz2, head, Eastern.HIP, p.small() ? 2 : 1, p.small() ? 2 : 3, ROOF, null);
        // gilt ornaments on the ends of the ridge
        put(s, rx1 + rings, ridge + 1, rz1 + rings, GOLD);
        put(s, rx2 - rings, ridge + 1, rz1 + rings, GOLD);
        put(s, c, head + 1, (z1 + b) / 2, Kit.lantern());
        // the leaves of the gate standing open, the banner of the kingdom over its mouth, lanterns in the passage
        s.door(c - pass, 1, b - 1, DOOR, "south", "left");
        s.door(c + pass, 1, b - 1, DOOR, "south", "right");
        for (int y = 1; y <= 2; y++) {
            put(s, c - pass, y, b - 1, s.get(c - pass, y, b - 1).replace("open=false", "open=true"));
            put(s, c + pass, y, b - 1, s.get(c + pass, y, b - 1).replace("open=false", "open=true"));
        }
        put(s, c, rh, b + 1, Kit.wallBanner("red", "south"));
        put(s, c - pass, rh - 1, b - 2, Kit.hangingLantern());
        put(s, c + pass, rh - 1, b - 2, Kit.hangingLantern());
        p.point(GATE, c - 1, 1, b - 1);
        p.point(GATE, c + 1, 1, b - 1);
        p.point(WALK, c, rh + 1, b - 1);
        p.point(WALK, x1 - 2, rh + 1, b - 1);
        p.point(WALK, x2 + 2, rh + 1, b - 1);
    }

    // ---------------------------------------------------------------- the corner towers

    /**
     * A corner tower (yagura) standing on a square base of stone at a corner of the walls, as high as the walls, with a room
     * inside the base entered from the court (the kitchen, the armoury or a store) and a ladder up; on the base its storeys
     * of white plaster between dark posts under skirt roofs with little gables looking out, doors to the walks of both
     * walls, and a hip-and-gable roof on top. {@code t} is {x1, z1, size, storeys}.
     */
    private static void tower(Structure s, Plan p, int[] t) {
        int x1 = t[0];
        int z1 = t[1];
        int size = t[2];
        int count = t[3];
        int x2 = x1 + size - 1;
        int z2 = z1 + size - 1;
        boolean west = x1 < p.c;
        boolean north = z1 < p.c;
        int rh = p.rh;
        // the base and the room inside it
        box(s, x1, 1, z1, x2, rh, z2, STONE);
        box(s, x1, 1, z1, x2, 1, z2, ROUGH);
        box(s, x1 + 1, 1, z1 + 1, x2 - 1, rh - 1, z2 - 1, AIR);
        box(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, STONE);
        int dx = west ? x2 : x1;
        int dz = north ? z2 - 2 : z1 + 2;
        s.door(dx, 1, dz, DOOR, west ? "west" : "east", "left");
        int zm = z1 + size / 2;
        // a tower of one storey stands back from the edges of its base, a lantern turret on a bastion
        int[] insets = count == 3 ? (size >= 9 ? new int[]{0, 1, 2} : new int[]{0, 0, 1}) : count == 2 ? new int[]{0, 1} : new int[]{1};
        int first = insets[0];
        // the parapets and copings of the walls give way to the tower on its base
        box(s, x1 - 1, rh + 1, z1 - 1, x2 + 1, rh + 5, z2 + 1, AIR);
        if (first > 0) {
            // a low white parapet under a coping along the outer edges of the base, round the turret
            int ox = west ? x1 : x2;
            int oz = north ? z1 : z2;
            for (int k = 0; k < size; k++) {
                put(s, ox, rh + 1, z1 + k, PLASTER);
                put(s, ox, rh + 2, z1 + k, Kit.slab(ROOF.slab()));
                put(s, x1 + k, rh + 1, oz, PLASTER);
                put(s, x1 + k, rh + 2, oz, Kit.slab(ROOF.slab()));
            }
        }
        int lz = north ? z1 + first + 1 : z2 - first - 1;
        List<int[]> ladders = new ArrayList<>();
        ladders.add(new int[]{west ? x1 + first + 1 : x2 - first - 1, lz, 1, rh});
        towerRoom(s, p, x1, z1, x2, z2, west, north);
        // the storeys
        int f = rh;
        for (int i = 0; i < count; i++) {
            int in = insets[i];
            int sx1 = x1 + in;
            int sz1 = z1 + in;
            int sx2 = x2 - in;
            int sz2 = z2 - in;
            int head = f + (count == 1 ? 5 : i == 0 ? 4 : 3);
            Eastern.frame(s, sx1, sz1, sx2, sz2, f, head, 0, Kit.log(POST, "y"), PLASTER);
            box(s, sx1 + 1, f, sz1 + 1, sx2 - 1, f, sz2 - 1, FLOOR);
            box(s, sx1 + 1, f + 1, sz1 + 1, sx2 - 1, head - 1, sz2 - 1, AIR);
            box(s, sx1 + 1, head, sz1 + 1, sx2 - 1, head, sz2 - 1, PLANKS);
            lattices(s, sx1, sz1, sx2, sz2, f + 2, 1, i == count - 1 ? 1 : 2, "", 0);
            if (head - f >= 4) {
                put(s, (sx1 + sx2) / 2, head - 1, (sz1 + sz2) / 2, Kit.hangingLantern());
            } else {
                // a low room: the lantern stands on the floor in a corner, out of the way
                put(s, west ? sx2 - 1 : sx1 + 1, f + 1, north ? sz2 - 1 : sz1 + 1, Kit.lantern());
            }
            if (i == 0) {
                // the guard room on the walk: a bench and a rack of weapons, the doors to both walks
                s.door(west ? sx1 + 1 : sx2 - 1, f + 1, north ? sz2 : sz1, DOOR, north ? "north" : "south", "left");
                s.door(west ? sx2 : sx1, f + 1, north ? sz1 + 1 : sz2 - 1, DOOR, west ? "west" : "east", "left");
            }
            if (i < count - 1) {
                int step = insets[i + 1] - in;
                int next = Eastern.skirt(s, sx1, sz1, sx2, sz2, head, 2, step, 1, ROOF);
                box(s, sx1 + 1, head + 1, sz1 + 1, sx2 - 1, next, sz2 - 1, PLANKS);
                if (step > 0) {
                    int r = size >= 9 ? 2 : 1;
                    dormer(s, north ? "north" : "south", (sx1 + sx2) / 2, north ? sz1 - 2 : sz2 + 2, 2, head, r, false);
                    dormer(s, west ? "west" : "east", (sz1 + sz2) / 2, west ? sx1 - 2 : sx2 + 2, 2, head, r, false);
                }
                int up = insets[i + 1];
                // each flight one row further, so that it does not stand on the head of the one below
                ladders.add(new int[]{west ? x1 + up + 1 : x2 - up - 1, zm + i, f + 1, next});
                f = next;
            } else {
                int rx1 = sx1 - 2;
                int rz1 = sz1 - 2;
                int rx2 = sx2 + 2;
                int rz2 = sz2 + 2;
                int rings = Math.min(rx2 - rx1, rz2 - rz1) / 2;
                Eastern.roof(s, rx1, rz1, rx2, rz2, head, Math.max(1, rings / 2), 1, size >= 9 ? 2 : 1, ROOF, PLASTER);
                put(s, (sx1 + sx2) / 2, head + 1, (sz1 + sz2) / 2, Kit.lantern());
            }
        }
        // the ladders last, through the floors laid over them
        for (int[] l : ladders) {
            ladder(s, l[0], l[1], l[2], l[3], west ? "east" : "west");
        }
    }

    /**
     * The room in the base of a corner tower: the kitchen of the small castle in the south-west, the armoury in the
     * south-east, a store of rice and goods or a guard room elsewhere.
     */
    private static void towerRoom(Structure s, Plan p, int x1, int z1, int x2, int z2, boolean west, boolean north) {
        int ix1 = x1 + 1;
        int iz1 = z1 + 1;
        int ix2 = x2 - 1;
        int iz2 = z2 - 1;
        int y = 1;
        int top = p.rh - 1;
        // the far side from the door, where things stand against the outer walls
        int fx = west ? ix1 + 1 : ix2 - 1;
        int fz = north ? iz1 : iz2;
        hang(s, (ix1 + ix2) / 2, top, (iz1 + iz2) / 2);
        if (!north && west && p.small()) {
            // the kitchen
            Furnish.kitchen(s, ix1 + 1, y, fz, north ? "south" : "north", WOOD);
            put(s, ix2, y, fz, "minecraft:smoker[facing=" + (north ? "south" : "north") + ",lit=false]");
            put(s, ix2, y, fz - 1, Kit.barrel());
            Furnish.sack(s, ix2, y, iz1, true);
            Furnish.sack(s, ix2, y + 1, iz1, false);
            Furnish.longTable(s, ix1 + 2, iz1 + 1, ix1 + 3, iz1 + 1, y, WOOD);
        } else if (!north && !west) {
            // the armoury: racks of weapons along the outer wall under banners, a chest of arms, crates, an anvil
            Furnish.weaponRack(s, ix1 + 1, y, iz2, "east", WOOD);
            Furnish.weaponRack(s, ix1 + 2, y, iz2, "east", WOOD);
            Furnish.weaponRack(s, ix1 + 3, y, iz2, "east", WOOD);
            put(s, ix1, y, iz2, "minecraft:anvil[facing=north]");
            for (int x = ix1 + 1; x <= ix1 + 3; x += 2) {
                put(s, x, y + 1, iz2, Kit.wallBanner("red", "north"));
            }
            Furnish.chest(s, ix2, y, iz1, "west");
            Furnish.crate(s, ix2 - 1, y, iz1, WOOD);
            Furnish.crate(s, ix2 - 1, y + 1, iz1, WOOD);
        } else {
            // a store of rice and goods
            Furnish.sack(s, fx, y, fz, false);
            Furnish.sack(s, fx, y + 1, fz, true);
            Furnish.sack(s, fx + (west ? 1 : -1), y, fz, true);
            Furnish.crate(s, fx, y, fz + (north ? 1 : -1), WOOD);
            put(s, fx + (west ? 2 : -2), y, fz, "minecraft:hay_block[axis=y]");
            Furnish.crate(s, fx + (west ? 2 : -2), y + 1, fz, WOOD);
        }
    }

    // ---------------------------------------------------------------- the court and the garden

    /**
     * The court: stone lanterns along the way to the hall, pines; the garden beside the hall behind a white wall with a moon
     * gate: a pond with a red arched bridge, stone lanterns, pines and a cherry; in the other side court the kitchen of the
     * larger castles.
     */
    private static void court(Structure s, Plan p) {
        int c = p.c;
        int[] g = p.garden;
        if (p.small()) {
            Eastern.pond(s, g[0] + 1, g[1] + 2, g[2] - 1, g[3] - 3, 1);
            Eastern.stoneLantern(s, g[0] + 2, 1, g[3] - 1, false);
            Eastern.stoneLantern(s, c - 3, 1, p.hz2 + 3, false);
            Eastern.stoneLantern(s, c + 3, 1, p.hz2 + 3, false);
            Eastern.lanternPost(s, p.line + 4, 1, p.c - 2, 3, "east");
            Eastern.lanternPost(s, p.south - 4, 1, p.kz2, 3, "west");
            return;
        }
        // the garden: its wall with a moon gate toward the front court
        int wz = g[3];
        Eastern.gardenWall(s, g[0], wz, g[2], wz, 1, 6, false, 3);
        int gx = (g[0] + g[2]) / 2;
        Eastern.moonGate(s, gx, 1, wz, true, 5, TRIM, Eastern.TRIM_STAIRS, null);
        box(s, gx - 1, 0, wz - 1, gx + 1, 0, wz + 1, STONE);
        // the pond in the north of the garden and the bridge over it
        int pz1 = g[1] + 2;
        int pz2 = pz1 + (p.large() ? 9 : 7);
        Eastern.pond(s, gx - 2, pz1, gx + 2, pz2, 1);
        Eastern.bridge(s, pz1 - 1, pz2 + 1, gx, 1, 3, false);
        box(s, gx, 0, pz2 + 2, gx, 0, wz - 1, GRAVEL);
        Eastern.stoneLantern(s, gx - 2, 1, pz2 + 3, true);
        Eastern.stoneLantern(s, gx + 2, 1, pz1 - 2, false);
        Eastern.cherry(s, gx - 2, 1, wz - 4, 4);
        Eastern.lanternPost(s, gx + 2, 1, pz1 - 2, 3, "east");
        // the front court: stone lanterns flanking the way, pines in the corners
        Eastern.stoneLantern(s, c - 4, 1, p.hz2 + p.veranda + 4, true);
        Eastern.stoneLantern(s, c + 4, 1, p.hz2 + p.veranda + 4, true);
        // the kitchen in the west side court
        int kx1 = p.line + 4;
        int kx2 = kx1 + 4;
        int kz1 = p.large() ? 11 : 7;
        int kz2 = kz1 + 8;
        kitchen(s, kx1, kz1, kx2, kz2);
        Eastern.pine(s, p.line + 5, 1, p.stairZ + 3, 5, "east");
        Eastern.lanternPost(s, kx2 + 2, 1, kz2 + 2, 3, "west");
        Eastern.lanternPost(s, p.c - 2, 1, p.gz1 - 1, 3, "west");
        Eastern.lanternPost(s, p.c + 2, 1, p.gz1 - 1, 3, "east");
    }

    /**
     * The kitchen of the larger castles: a low house of plaster between dark posts under a gable roof, its floor of stone;
     * inside the cooking fire, the cauldron and the board, a smoker and a furnace, a barrel of stores, sacks and crates,
     * a long table, lanterns.
     */
    private static void kitchen(Structure s, int x1, int z1, int x2, int z2) {
        int head = 5;
        Eastern.frame(s, x1, z1, x2, z2, 0, head, 2, Kit.log(POST, "y"), PLASTER);
        box(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, STONE);
        box(s, x1 + 1, 1, z1 + 1, x2 - 1, head - 1, z2 - 1, AIR);
        box(s, x1 + 1, head, z1 + 1, x2 - 1, head, z2 - 1, PLANKS);
        Eastern.roof(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, head, Eastern.GABLE, 1, 1, ROOF, PLANKS);
        int mz = (z1 + z2) / 2;
        s.door(x2, 1, mz, DOOR, "west", "left");
        Eastern.noren(s, x2, mz - 1, x2, mz + 1, 3, "east");
        for (int z = z1 + 2; z <= z2 - 2; z += 4) {
            if (z != mz) {
                Eastern.window(s, x2, 2, z, "east", 1, 2);
            }
            Eastern.window(s, x1, 2, z, "west", 1, 2);
        }
        Furnish.kitchen(s, x1 + 1, 1, z1 + 1, "south", WOOD);
        put(s, x2 - 1, 1, z1 + 1, "minecraft:smoker[facing=south,lit=false]");
        put(s, x2 - 1, 1, z2 - 1, "minecraft:furnace[facing=north,lit=false]");
        put(s, x1 + 1, 1, z2 - 1, Kit.barrel());
        Furnish.sack(s, x1 + 1, 1, z2 - 2, true);
        Furnish.sack(s, x1 + 1, 2, z2 - 2, false);
        Furnish.crate(s, x1 + 2, 1, z2 - 1, WOOD);
        Furnish.longTable(s, x1 + 2, mz - 1, x1 + 2, mz + 1, 1, WOOD);
        hang(s, x1 + 2, head - 1, z1 + 2);
        hang(s, x1 + 2, head - 1, z2 - 2);
        put(s, (x1 + x2) / 2, head + 1, mz, Kit.lantern());
    }
}
