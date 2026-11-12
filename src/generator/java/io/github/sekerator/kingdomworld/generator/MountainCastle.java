package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle of the style "mountain": a hold that seems cut out of the cliff it stands on, its masses stepping up from the
 * gate to the keep like the slopes of a mountain.
 *
 * <p>At the front the gate block: a deep tunnel through its solid foot with a portcullis hanging half down, before it a
 * portal of receding arches standing proud of the wall, and over the portal the great carved face of a crowned and bearded
 * forefather with glowing eyes, under a heavy crenellated top on corbels. Short pieces of wall join it to two low thick
 * towers with cut corners at the front corners, great braziers burning on their flat tops. Behind the gate lies a small
 * paved forecourt; on both sides of the throne hall broad flights of stairs climb between the hall and the side walls up
 * to landings, from which one walks onto the walls, into the towers and onto the terrace over the throne hall. At the back
 * corners stand two stronger square towers with dark quoins under low pyramids of stone, and between them, from the ground
 * at the back, rises the keep: a huge square tower with a battered foot, piers and dark bands, few small windows, a deep
 * portal from the terrace under a smaller carved face, and on top a dark stone crown jutting out on two rings of corbels,
 * its corners and the middles of its sides rising into gilt points, and in the crown a short watch tower with the beacon
 * of the hold (in the small hold the beacon burns on the crown itself). The outline is a stepped mountain of stone: walls, towers, gate, terrace, keep and crown.
 *
 * <p>Inside: the throne hall, a pillared hall with heavy beams on stone capitals, hearths in its side walls, braziers and
 * gold, the place of the throne on a dark dais before the carved face of the forefather; behind it in the foot of the keep
 * the treasury; above it the council room, the chambers, the library and the watch room; the kitchen in the western back
 * tower and the armoury in the eastern one, both reached from the hall through vaulted passages under the landings; guard
 * rooms in the towers and the room of the winch over the gate tunnel.
 *
 * <p>One program drawn in three sizes: the small hold 31 by 31, the hold 41 by 41 and the great hold 47 by 47. The throne
 * stands in the middle of the ground on the street layer at the north end of its hall, facing south; the castle leaves it
 * and the ground under it out. Layer 0 replaces the ground, layer 1 is level with the street. Every part is drawn with the
 * shapes and materials of {@link Mountain}.
 */
final class MountainCastle {
    private static final String AIR = Structure.AIR;
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String WALL = Mountain.WALL;
    private static final String ROUGH = Mountain.ROUGH;
    private static final String TRIM = Mountain.TRIM;
    private static final String FLOOR = Mountain.FLOOR;
    private static final String DARK = Mountain.DARK;
    private static final String PLANKS = Mountain.PLANKS;
    private static final String WOOD = Mountain.WOOD;
    private static final String CARPET = Mountain.CARPET;
    private static final int[] SIDES = {-1, 1};

    /** The three sizes of the hold. */
    enum Size {
        SMALL, MEDIUM, LARGE
    }

    private MountainCastle() {
    }

    /**
     * The three holds: small, medium and great, with their named places.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(building(Size.SMALL, "mountain_castle_small", "minecraft:polished_blackstone_bricks"));
        list.add(building(Size.MEDIUM, "mountain_castle_medium", "minecraft:polished_blackstone_bricks"));
        list.add(building(Size.LARGE, "mountain_castle_large", "minecraft:gilded_blackstone"));
        return list;
    }

    /** One hold of a size with its anchor in the middle of the front edge and its named places. */
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
     * The numbers of a hold of a size. Everything on the west side is mirrored to the east.
     */
    private static final class Plan {
        final Size size;
        /** The ground (square), the height of the box and its middle (the cell of the throne). */
        final int n;
        final int height;
        final int c;
        /** The layer of the floor of the walks, the landings, the terrace and the first storeys; one walks on the next. */
        final int walk;
        /** The inside of the throne hall, and the distance of its pillars from its middle. */
        final int hx1;
        final int hx2;
        final int hz1;
        final int hz2;
        final int pillar;
        /** The plan of the keep, its floors above the ground and its top. */
        final int kx1;
        final int kx2;
        final int kz1;
        final int kz2;
        final int[] floors;
        final int kf;
        /** The western side wall: its outer and inner face. */
        final int cw1;
        final int cw2;
        /** The western flight of stairs: its columns. */
        final int fx1;
        final int fx2;
        /** The strong back towers: width, middle of the western one, height. */
        final int bw;
        final int bcx;
        final int bcz;
        final int bh;
        /** The front towers: width, middle of the western one, height. */
        final int fw;
        final int fcx;
        final int fcz;
        final int fh;
        /** The gate block and the layer of its top floor. */
        final int gx1;
        final int gx2;
        final int gz1;
        final int gz2;
        final int gtop;
        /** The pieces of front wall between the front towers and the gate: their inner and outer face. */
        final int sz1;
        final int sz2;
        /** The bottom of the great face over the gate, and the width of the watch tower on the keep (0 for none). */
        final int faceY;
        final int watch;
        /** Named places: {kind, x, y, z}. */
        final List<int[]> points = new ArrayList<>();

        Plan(Size size) {
            this.size = size;
            switch (size) {
                case SMALL -> {
                    n = 31;
                    height = 40;
                    walk = 8;
                    hx1 = 10;
                    hx2 = 20;
                    hz1 = 13;
                    hz2 = 19;
                    pillar = 4;
                    kx1 = 10;
                    kx2 = 20;
                    kz1 = 2;
                    kz2 = 12;
                    floors = new int[]{8, 14, 20, 26};
                    kf = 32;
                    cw1 = 3;
                    cw2 = 5;
                    fx1 = 6;
                    fx2 = 7;
                    bw = 9;
                    bcx = 5;
                    bcz = 5;
                    bh = 13;
                    fw = 7;
                    fcx = 4;
                    fcz = 26;
                    fh = 12;
                    gx1 = 10;
                    gx2 = 20;
                    gz1 = 24;
                    gz2 = 28;
                    gtop = 20;
                    sz1 = 24;
                    sz2 = 27;
                    faceY = 7;
                    watch = 0;
                }
                case MEDIUM -> {
                    n = 41;
                    height = 44;
                    walk = 10;
                    hx1 = 13;
                    hx2 = 27;
                    hz1 = 18;
                    hz2 = 26;
                    pillar = 5;
                    kx1 = 13;
                    kx2 = 27;
                    kz1 = 2;
                    kz2 = 16;
                    floors = new int[]{10, 17, 23, 29};
                    kf = 34;
                    cw1 = 4;
                    cw2 = 7;
                    fx1 = 8;
                    fx2 = 10;
                    bw = 11;
                    bcx = 7;
                    bcz = 7;
                    bh = 15;
                    fw = 9;
                    fcx = 5;
                    fcz = 35;
                    fh = 14;
                    gx1 = 14;
                    gx2 = 26;
                    gz1 = 33;
                    gz2 = 38;
                    gtop = 20;
                    sz1 = 33;
                    sz2 = 36;
                    faceY = 7;
                    watch = 7;
                }
                default -> {
                    n = 47;
                    height = 48;
                    walk = 11;
                    hx1 = 15;
                    hx2 = 31;
                    hz1 = 21;
                    hz2 = 30;
                    pillar = 6;
                    kx1 = 15;
                    kx2 = 31;
                    kz1 = 2;
                    kz2 = 18;
                    floors = new int[]{11, 18, 25, 31};
                    kf = 37;
                    cw1 = 4;
                    cw2 = 7;
                    fx1 = 8;
                    fx2 = 12;
                    bw = 13;
                    bcx = 8;
                    bcz = 8;
                    bh = 16;
                    fw = 11;
                    fcx = 7;
                    fcz = 39;
                    fh = 16;
                    gx1 = 16;
                    gx2 = 30;
                    gz1 = 38;
                    gz2 = 44;
                    gtop = 21;
                    sz1 = 38;
                    sz2 = 42;
                    faceY = 7;
                    watch = 7;
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

        /** The front face of the hall, the row of its door. */
        int front() {
            return hz2 + 2;
        }

        /** The row of the top step of the flights, level with the walks. */
        int flightTop() {
            return front() + 1 - walk;
        }

        /** The first row south of the back towers' bodies, where the landings begin. */
        int landing() {
            return bcz + (bw - 1) / 2 + 1;
        }

        /** The last row of the side walls before the bodies of the front towers. */
        int sideEnd() {
            return fcz - (fw - 1) / 2 - 1;
        }

        /** The middle row of the keep. */
        int kzc() {
            return (kz1 + kz2) / 2;
        }

        void point(int kind, int x, int y, int z) {
            points.add(new int[]{kind, x, y, z});
        }
    }

    // ---------------------------------------------------------------- small helpers

    /** Puts a block, leaving out what falls outside the box. */
    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    /** Whether a cell inside the box holds nothing yet. */
    private static boolean free(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && !s.solid(x, y, z) && !s.leftOut(x, y, z);
    }

    /** Puts a block only where nothing stands yet: furniture that gives way to doors, ladders and what is built. */
    private static void place(Structure s, int x, int y, int z, String state) {
        if (free(s, x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    /** Fills a box of cells with a block. */
    private static void box(Structure s, int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    put(s, x, y, z, state);
                }
            }
        }
    }

    /** The column of a western x on a side: as it is on the west (-1), mirrored on the east (1). */
    private static int sx(Plan p, int x, int side) {
        return side < 0 ? x : p.n - 1 - x;
    }

    /** A way as drawn for the west, turned for a side: east and west change places on the east. */
    private static String sf(String facing, int side) {
        if (side < 0) {
            return facing;
        }
        return facing.equals("east") ? "west" : facing.equals("west") ? "east" : facing;
    }

    /** A lantern hanging on a chain from the first solid block above it. */
    private static void hang(Structure s, int x, int y, int z) {
        if (!free(s, x, y, z)) {
            return;
        }
        put(s, x, y, z, Kit.hangingLantern());
        for (int yy = y + 1; yy < s.sizeY && !s.solid(x, yy, z); yy++) {
            s.set(x, yy, z, Kit.chain());
        }
    }

    /** A lantern on a post of dressed stone standing on a low wall or a floor. */
    private static void lampPost(Structure s, int x, int y, int z) {
        put(s, x, y, z, TRIM);
        put(s, x, y + 1, z, Kit.lantern());
    }

    /**
     * A battered foot of rough stone like {@link Mountain#batter}, but only into cells nothing stands in yet and, when
     * {@code facing} is given, only on that side of the plan: a mass drawn after others leans its foot against them
     * without cutting into them.
     */
    private static int batter(Structure s, Mountain.Plan wall, int y, int out, int rise, String facing) {
        for (int k = 0; k < out; k++) {
            Mountain.Plan p = wall.grow(out - k);
            for (int j = 0; j < rise; j++) {
                int layer = y + k * rise + j;
                boolean top = j == rise - 1;
                for (int z = p.z1(); z <= p.z2(); z++) {
                    for (int x = p.x1(); x <= p.x2(); x++) {
                        if (!p.has(x, z) || wall.has(x, z) || !free(s, x, layer, z)) {
                            continue;
                        }
                        if (facing != null && !beyond(wall, x, z, facing)) {
                            continue;
                        }
                        String state = top && p.rim(x, z) ? Mountain.slope(p, x, z, Mountain.ROUGH_STAIRS, false) : null;
                        put(s, x, layer, z, state != null ? state : ROUGH);
                    }
                }
            }
        }
        return y + out * rise;
    }

    /** Whether a column lies beyond one side of a plan, within its length. */
    private static boolean beyond(Mountain.Plan p, int x, int z, String facing) {
        return switch (facing) {
            case "north" -> z < p.z1() && x >= p.x1() && x <= p.x2();
            case "south" -> z > p.z2() && x >= p.x1() && x <= p.x2();
            case "west" -> x < p.x1() && z >= p.z1() && z <= p.z2();
            default -> x > p.x2() && z >= p.z1() && z <= p.z2();
        };
    }

    // ---------------------------------------------------------------- the hold

    /**
     * Draws the hold of a plan: the corner towers, the keep, the throne hall and its terrace, the side walls, the gate with
     * its pieces of wall, the flights and landings, the doors between them, the rooms, the named places and the ground.
     */
    private static Structure castle(Plan p) {
        Structure s = new Structure(p.n, p.height, p.n);
        towers(s, p);
        keep(s, p);
        hall(s, p);
        sideWalls(s, p);
        gate(s, p);
        flights(s, p);
        doors(s, p);
        throneHall(s, p);
        treasury(s, p);
        keepRooms(s, p);
        kitchen(s, p);
        armoury(s, p);
        guardRooms(s, p);
        gateRoom(s, p);
        forecourt(s, p);
        places(s, p);
        ground(s, p);
        s.skip(p.c, 0, p.c).skip(p.c, 1, p.c);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The four corner towers: the stronger square ones with dark quoins under low pyramids of stone at the back, the lower
     * ones with cut corners and a great brazier burning on their flat tops at the front. Their first storeys lie on the
     * level of the walks.
     */
    private static void towers(Structure s, Plan p) {
        for (int side : SIDES) {
            int bx = sx(p, p.bcx, side);
            int r = (p.bw - 1) / 2;
            Mountain.tower(s, bx, 0, p.bcz, p.bw, p.bh, Mountain.SQUARE, Mountain.HELM_TOP, null, p.walk);
            for (int d : new int[]{-3, 3}) {
                Mountain.pier(s, bx + d, 4, p.bh - 4, p.bcz - r, "north", 1);
                Mountain.pier(s, bx + side * r, 4, p.bh - 4, p.bcz + d, sf("west", side), 1);
            }
            int top = Mountain.tower(s, sx(p, p.fcx, side), 0, p.fcz, p.fw, p.fh, Mountain.CHAMFER, Mountain.FLAT_TOP, null, p.walk);
            Mountain.brazier(s, sx(p, p.fcx, side), top + 1, p.fcz, true);
        }
    }

    // ---------------------------------------------------------------- the keep

    /**
     * The keep: a huge square tower rising from the ground at the back between the back towers, on a battered foot, walls
     * two thick with dark bands at its floors and dark quoins, piers on its faces, small deep windows, a deep portal from
     * the terrace under a small carved face and long hangings; on top a dark crown of merlons jutting out on two rings of
     * corbels with gilt points at its corners and in the middles of its sides, and in the crown the short watch tower with
     * the beacon of the hold.
     */
    private static void keep(Structure s, Plan p) {
        Mountain.Plan k = Mountain.Plan.box(p.kx1, p.kz1, p.kx2, p.kz2);
        Mountain.Plan in = k.grow(-2);
        int f = p.kf;
        int c = p.c;
        int band = batter(s, k, 0, 2, 2, null);
        Mountain.walls(s, k, 0, f - 1, 2, WALL);
        Mountain.plate(s, in, 0, FLOOR);
        for (int fl : p.floors) {
            Mountain.plate(s, in, fl, fl == p.walk ? FLOOR : PLANKS);
        }
        Mountain.ring(s, k, band, DARK);
        for (int fl : p.floors) {
            Mountain.ring(s, k, fl, DARK);
        }
        Mountain.ring(s, k, f - 3, DARK);
        // Dark quoins between the bands.
        for (int y = band + 1; y < f - 3; y++) {
            boolean even = Math.floorMod(y - band, 2) == 0;
            for (int[] corner : new int[][]{{k.x1(), k.z1(), 1, 1}, {k.x2(), k.z1(), -1, 1}, {k.x1(), k.z2(), 1, -1}, {k.x2(), k.z2(), -1, -1}}) {
                if (s.get(corner[0], y, corner[1]).equals(DARK)) {
                    continue;
                }
                put(s, corner[0], y, corner[1], DARK);
                put(s, corner[0] + (even ? corner[2] : 0), y, corner[1] + (even ? 0 : corner[3]), DARK);
            }
        }
        // Piers: on the back face from the batter, on the front from the terrace, on the sides above the back towers.
        int[] across = p.small() ? new int[]{p.kx1 + 2, p.kx2 - 2} : new int[]{p.kx1 + 2, p.kx2 - 2};
        for (int x : across) {
            Mountain.pier(s, x, band, f - 4, p.kz1, "north", 1);
        }
        for (int x : new int[]{p.kx1 + 2, p.kx2 - 2}) {
            Mountain.pier(s, x, p.walk + 1, f - 4, p.kz2, "south", 1);
        }
        for (int side : SIDES) {
            int x = side < 0 ? p.kx1 : p.kx2;
            String out = side < 0 ? "west" : "east";
            for (int z : new int[]{p.kz1 + 3, p.kz2 - 3}) {
                Mountain.pier(s, x, p.bh + 5, f - 4, z, out, 1);
            }
        }
        // Small deep windows on every storey above the walks; on the front only above the carved face.
        int faceTop = p.walk + 14;
        for (int fl : p.floors) {
            int wy = fl + 2;
            for (int dx : new int[]{-4, 4}) {
                if (wy > faceTop) {
                    Mountain.window(s, c + dx, wy, p.kz2, "south", 2, 2);
                }
                Mountain.window(s, c + dx, wy, p.kz1, "north", 2, 2);
            }
            for (int side : SIDES) {
                int x = side < 0 ? p.kx1 : p.kx2;
                String out = side < 0 ? "west" : "east";
                if (wy > p.bh + 9) {
                    Mountain.window(s, x, wy, p.kzc(), out, 2, 2);
                } else if (wy > p.walk + 1) {
                    Mountain.window(s, x, wy, p.kz2 - 2, out, 2, 2);
                }
            }
        }
        // The crown: dark merlons and a dark floor on the corbels, gilt points.
        int jut = p.small() ? 1 : 2;
        Mountain.corbels(s, k, f - 1, jut);
        Mountain.Plan q = k.grow(jut);
        Mountain.plate(s, q, f, DARK);
        Mountain.plate(s, q.grow(-2), f, FLOOR);
        crown(s, q, f + 1);
        if (p.watch > 0) {
            int top = Mountain.tower(s, c, f, p.kzc(), p.watch, 6, Mountain.SQUARE, Mountain.FLAT_TOP, "south", 0);
            Mountain.brazier(s, c, top + 1, p.kzc(), true);
        } else {
            Mountain.brazier(s, c, f + 1, p.kzc(), true);
        }
        // The ladder from the council room up to the crown, in the north-western corner.
        int lx = p.kx1 + 2;
        int lz = p.kz1 + 2;
        for (int y = p.walk + 1; y <= f; y++) {
            put(s, lx, y, lz, Kit.ladder("south"));
        }
        // A balcony of timber on brackets on the back face before the library, a door onto it.
        int bw = p.small() ? 2 : 3;
        Mountain.balcony(s, c - bw, p.kz1, c + bw, p.kz1, p.floors[2], "north", 2);
        Mountain.archDoor(s, c, p.floors[2] + 1, p.kz1 + 1, "north", 1, 1);
        // The portal from the terrace, the face over it, hangings beside it.
        Mountain.archDoor(s, c, p.walk + 1, p.kz2 - 1, "south", 3, 2);
        Mountain.face(s, c, p.walk + 7, p.kz2, "south", 0);
        int hx = (p.kx2 - p.kx1) / 2 - 1;
        for (int dx : new int[]{-hx, hx}) {
            Mountain.hanging(s, c + dx, f - 5, p.kz2 + 1, "south", 7);
        }
    }

    /**
     * A crown of stone on the edge of a plan at layer {@code y}: a heavy parapet of dark stone, and at its corners and in
     * the middles of its sides merlons rising one layer higher into points of gold.
     */
    private static void crown(Structure s, Mountain.Plan q, int y) {
        Mountain.parapet(s, q, y);
        for (int z = q.z1(); z <= q.z2(); z++) {
            for (int x = q.x1(); x <= q.x2(); x++) {
                if (q.rim(x, z) && s.inside(x, y + 1, z)) {
                    put(s, x, y, z, DARK);
                    if (s.get(x, y + 1, z).equals(WALL)) {
                        put(s, x, y + 1, z, DARK);
                    }
                }
            }
        }
        int[][] points = {{q.x1(), q.z1()}, {q.x2(), q.z1()}, {q.x1(), q.z2()}, {q.x2(), q.z2()}, {q.cx(), q.z1()}, {q.cx(), q.z2()},
                {q.x1(), q.cz()}, {q.x2(), q.cz()}};
        for (int[] pt : points) {
            put(s, pt[0], y + 1, pt[1], DARK);
            put(s, pt[0], y + 2, pt[1], Mountain.GILDED);
            put(s, pt[0], y + 3, pt[1], Mountain.GOLD);
        }
    }

    // ---------------------------------------------------------------- the block of the throne hall

    /**
     * The block of the throne hall: walls two thick on a low battered toe at the front, a terrace on its roof level with the
     * walks, paved with a dark runner from its parapet to the portal of the keep, with a parapet along its front and along
     * the flights, great braziers before the keep and stones of runes by the parapet; a frieze of runes, a deep portal and
     * two small windows in its front.
     */
    private static void hall(Structure s, Plan p) {
        int c = p.c;
        Mountain.Plan h = Mountain.Plan.box(p.hx1 - 2, p.hz1 - 2, p.hx2 + 2, p.front());
        batter(s, h, 0, 1, 2, "south");
        Mountain.walls(s, h, 0, p.walk - 1, 2, WALL);
        box(s, p.hx1, 1, p.hz1, p.hx2, p.walk - 1, p.hz2, AIR);
        Mountain.Plan shown = Mountain.Plan.box(p.hx1 - 2, p.hz1 - 1, p.hx2 + 2, p.front());
        Mountain.ring(s, shown, 2, DARK);
        // The terrace.
        box(s, p.hx1 - 2, p.walk, p.kz2 + 1, p.hx2 + 2, p.walk, p.front(), FLOOR);
        Mountain.ring(s, shown, p.walk, DARK);
        for (int z = p.kz2 + 2; z < p.front(); z++) {
            for (int x = c - 1; x <= c + 1; x++) {
                put(s, x, p.walk, z, x == c && Math.floorMod(z - p.kz2, 3) == 0 ? Mountain.GILDED : DARK);
            }
        }
        Mountain.parapet(s, Mountain.Plan.box(p.hx1 - 2, p.front(), p.hx2 + 2, p.front()), p.walk + 1);
        for (int side : SIDES) {
            int x = side < 0 ? p.hx1 - 2 : p.hx2 + 2;
            Mountain.parapet(s, Mountain.Plan.box(x, p.flightTop() + 1, x, p.front()), p.walk + 1);
            lampPost(s, x, p.walk + 1, p.front());
            if (p.small()) {
                Mountain.brazier(s, c + side * 3, p.walk + 1, p.kz2 + 3, false);
            } else {
                Mountain.brazier(s, c + side * ((p.hx2 - p.hx1) / 2 - 1), p.walk + 1, p.kz2 + 4, true);
                Mountain.runeStone(s, c + side * 5, p.walk + 1, p.front() - 3, "south", 2);
            }
        }
        // The front: a frieze of runes, piers at the corners, windows, the portal.
        Mountain.runeBand(s, p.hx1 - 2, p.front(), p.hx2 + 2, p.front(), 2);
        for (int x : new int[]{p.hx1 - 1, p.hx2 + 1}) {
            Mountain.pier(s, x, 2, p.walk - 1, p.front(), "south", 1);
        }
        for (int dx : new int[]{-5, 5}) {
            Mountain.window(s, c + dx, 4, p.front(), "south", 2, 2);
        }
        Mountain.archDoor(s, c, 1, p.hz2 + 1, "south", 3, 2);
    }

    // ---------------------------------------------------------------- the side walls

    /**
     * The side walls between the back and the front towers: solid stone on a battered foot outside, a walk on top behind a
     * crenellated parapet, and along the flights a low inner wall with lanterns.
     */
    private static void sideWalls(Structure s, Plan p) {
        for (int side : SIDES) {
            int x1 = sx(p, p.cw1, side);
            int x2 = sx(p, p.cw2, side);
            String out = side < 0 ? "west" : "east";
            Mountain.Plan w = Mountain.Plan.box(x1, p.landing(), x2, p.sideEnd());
            for (int y = 0; y < p.walk; y++) {
                Mountain.plate(s, w, y, WALL);
            }
            Mountain.plate(s, w, p.walk, FLOOR);
            int band = batter(s, w, 0, 2, 2, out);
            for (int z = p.landing(); z <= p.sideEnd(); z++) {
                put(s, x1, band, z, DARK);
                put(s, x1, p.walk - 1, z, DARK);
            }
            Mountain.parapet(s, Mountain.Plan.box(x1, p.landing(), x1, p.sideEnd()), p.walk + 1);
            for (int z = p.flightTop() + 1; z <= p.sideEnd(); z++) {
                put(s, x2, p.walk + 1, z, WALL);
                put(s, x2, p.walk + 2, z, Kit.slab(Mountain.TRIM_SLAB));
            }
            for (int z = p.flightTop() + 2; z <= p.sideEnd(); z += 4) {
                lampPost(s, x2, p.walk + 2, z);
            }
            for (int z = p.landing() + 3; z <= p.sideEnd() - 2; z += 5) {
                Mountain.pier(s, x1, band, p.walk - 2, z, out, 1);
            }
        }
    }

    // ---------------------------------------------------------------- the gate

    /**
     * The gate block in the middle of the front and the pieces of wall that join it to the front towers: a solid foot with
     * the tunnel through it, the room of the winch over it, a heavy crenellated top on corbels with small braziers at its
     * corners, the portal and the great face on its front, long hangings beside the face.
     */
    private static void gate(Structure s, Plan p) {
        int c = p.c;
        // The pieces of wall.
        for (int side : SIDES) {
            int a = sx(p, p.fcx + (p.fw - 1) / 2 + 1, side);
            int b = sx(p, p.gx1 - 1, side);
            Mountain.Plan w = Mountain.Plan.box(a, p.sz1, b, p.sz2);
            for (int y = 0; y < p.walk; y++) {
                Mountain.plate(s, w, y, WALL);
            }
            Mountain.plate(s, w, p.walk, FLOOR);
            int band = batter(s, w, 0, 2, 2, "south");
            for (int x = Math.min(a, b); x <= Math.max(a, b); x++) {
                put(s, x, band, p.sz2, DARK);
                put(s, x, p.walk - 1, p.sz2, DARK);
                put(s, x, p.walk + 1, p.sz1, WALL);
                put(s, x, p.walk + 2, p.sz1, Kit.slab(Mountain.TRIM_SLAB));
            }
            lampPost(s, (a + b) / 2, p.walk + 2, p.sz1);
            Mountain.parapet(s, Mountain.Plan.box(a, p.sz2, b, p.sz2), p.walk + 1);
        }
        // The block.
        Mountain.Plan g = Mountain.Plan.box(p.gx1, p.gz1, p.gx2, p.gz2);
        batter(s, g, 0, 1, 2, "south");
        Mountain.walls(s, g, 0, p.gtop - 1, 2, WALL);
        Mountain.Plan in = g.grow(-2);
        for (int y = 0; y < p.walk; y++) {
            Mountain.plate(s, in, y, WALL);
        }
        Mountain.plate(s, in, p.walk, FLOOR);
        Mountain.ring(s, g, 2, DARK);
        Mountain.ring(s, g, p.walk, DARK);
        Mountain.ring(s, g, p.gtop - 3, DARK);
        Mountain.corbels(s, g, p.gtop - 1, 1);
        Mountain.plate(s, g.grow(1), p.gtop, DARK);
        Mountain.plate(s, g.grow(-1), p.gtop, FLOOR);
        Mountain.parapet(s, g.grow(1), p.gtop + 1);
        Mountain.runeBand(s, p.gx1, p.gz2, p.gx2, p.gz2, p.gtop - 3);
        for (int x : new int[]{p.gx1, p.gx2}) {
            Mountain.brazier(s, x, p.gtop + 1, p.gz2, false);
            Mountain.brazier(s, x, p.gtop + 1, p.gz1, false);
        }
        // The tunnel: three wide and four high under a rounded vault, a portcullis hanging half down behind its mouth.
        for (int z = p.gz1; z <= p.gz2; z++) {
            box(s, c - 1, 1, z, c + 1, 3, z, AIR);
            put(s, c, 4, z, AIR);
            put(s, c - 1, 4, z, Kit.upside(Mountain.TRIM_STAIRS, "east"));
            put(s, c + 1, 4, z, Kit.upside(Mountain.TRIM_STAIRS, "west"));
        }
        box(s, c - 1, 0, p.gz1, c + 1, 0, p.gz2, FLOOR);
        for (int x = c - 1; x <= c + 1; x++) {
            put(s, x, 3, p.gz2 - 1, Mountain.IRON);
            put(s, x, 0, p.gz2 - 1, DARK);
        }
        put(s, c, 4, p.gz2 - 1, Mountain.IRON);
        hang(s, c, 4, p.gz1 + 1);
        hang(s, c, 4, p.gz2 - 3);
        // The open leaves of the gate against the walls of the tunnel.
        for (int y = 1; y <= 3; y++) {
            put(s, c - 1, y, p.gz1 + 1, Kit.shutter("minecraft:oak_trapdoor", "east"));
            put(s, c + 1, y, p.gz1 + 1, Kit.shutter("minecraft:oak_trapdoor", "west"));
        }
        portal(s, c, p.gz2);
        Mountain.face(s, c, p.faceY, p.gz2, "south", 1);
        int hx = (p.gx2 - p.gx1) / 2 - 1;
        for (int dx : new int[]{-hx, hx}) {
            Mountain.hanging(s, c + dx, p.gtop - 2, p.gz2 + 1, "south", p.small() ? 6 : 8);
        }
        // Windows of the room over the tunnel onto the forecourt.
        for (int dx : new int[]{-3, 3}) {
            Mountain.window(s, c + dx, p.walk + 3, p.gz1, "north", 2, 2);
        }
    }

    /**
     * The portal before the tunnel at the front face {@code z}: two orders of arches standing proud of the wall, each one
     * block wider on both sides and one layer higher than the one behind it, with rounded haunches; the outer order is
     * dressed with trim and has a carved keystone and a lantern.
     */
    private static void portal(Structure s, int c, int z) {
        for (int v = 1; v <= 2; v++) {
            int h = 1 + v;
            int open = 4 + v;
            for (int dx = -4; dx <= 4; dx++) {
                for (int y = 1; y <= open + 1; y++) {
                    boolean opening = Math.abs(dx) <= h && y <= open;
                    String state;
                    if (opening) {
                        boolean haunch = Math.abs(dx) == h && y == open;
                        state = haunch ? Kit.upside(Mountain.TRIM_STAIRS, dx < 0 ? "east" : "west") : AIR;
                    } else if (v == 2 && Math.abs(dx) <= h && y == open + 1) {
                        state = dx == 0 ? Mountain.CARVED : TRIM;
                    } else if (v == 2 && Math.abs(dx) == 4) {
                        state = TRIM;
                    } else {
                        state = WALL;
                    }
                    put(s, c + dx, y, z + v, state);
                }
                put(s, c + dx, 0, z + v, Math.abs(dx) <= h ? FLOOR : ROUGH);
            }
        }
        put(s, c, 6, z + 2, Kit.hangingLantern());
    }

    // ---------------------------------------------------------------- flights and landings

    /**
     * The flights of stairs between the hall and the side walls, climbing from the forecourt to the landings; the
     * landings level with the walks, over vaulted passages from the hall to the back towers.
     */
    private static void flights(Structure s, Plan p) {
        int top = p.flightTop();
        for (int side : SIDES) {
            int x1 = Math.min(sx(p, p.fx1, side), sx(p, p.fx2, side));
            int x2 = Math.max(sx(p, p.fx1, side), sx(p, p.fx2, side));
            for (int l = 1; l <= p.walk; l++) {
                int z = p.front() + 1 - l;
                for (int x = x1; x <= x2; x++) {
                    box(s, x, 0, z, x, l - 1, z, ROUGH);
                    put(s, x, l, z, Kit.stairs(Mountain.TRIM_STAIRS, "north"));
                    box(s, x, l + 1, z, x, l + 3, z, AIR);
                }
            }
            // The landing over the passage.
            int a1 = Math.min(sx(p, p.fx1, side), sx(p, p.hx1 - 1, side));
            int a2 = Math.max(sx(p, p.fx1, side), sx(p, p.hx1 - 1, side));
            box(s, a1, p.walk, p.landing(), a2, p.walk, top - 1, FLOOR);
            box(s, x1, 1, p.landing(), x2, p.walk - 1, top - 1, AIR);
            box(s, x1, 0, p.landing(), x2, 0, top - 1, FLOOR);
            int b1 = Math.min(sx(p, p.fx2 + 1, side), sx(p, p.hx1 - 1, side));
            int b2 = Math.max(sx(p, p.fx2 + 1, side), sx(p, p.hx1 - 1, side));
            box(s, b1, 0, p.landing(), b2, p.walk - 1, p.hz1 - 3, WALL);
            int mx = sx(p, (p.fx1 + p.fx2) / 2, side);
            for (int z = p.landing() + 1; z < top; z += 3) {
                hang(s, mx, p.walk - 1, z);
            }
            // Casks and crates along the passage.
            Mountain.casks(s, sx(p, p.fx1, side), p.landing() + 2, sx(p, p.fx1, side), top - 2, 1, sf("east", side));
            lampPost(s, sx(p, p.hx1 - 2, side), p.walk + 1, p.flightTop());
        }
    }

    /**
     * Doors between the parts: from the hall into the passages under the landings, from the passages into the back
     * towers, from the landings into the towers' first storeys, from the walks into the front towers and the gate.
     */
    private static void doors(Structure s, Plan p) {
        int w = p.walk + 1;
        int r = (p.bw - 1) / 2;
        int fr = (p.fw - 1) / 2;
        int thick = p.fw >= 9 ? 2 : 1;
        for (int side : SIDES) {
            String in = sf("east", side);
            String out = sf("west", side);
            Mountain.archDoor(s, sx(p, p.hx1 - 1, side), 1, p.hz1, out, 1, 1);
            int dx = sx(p, (p.fx1 + p.fx2) / 2, side);
            Mountain.archDoor(s, dx, 1, p.bcz + r - 1, "south", 1, 1);
            Mountain.archDoor(s, dx, w, p.bcz + r - 1, "south", 1, 1);
            Mountain.archDoor(s, sx(p, (p.cw1 + p.cw2 + 1) / 2, side), w, p.fcz - fr + thick - 1, "north", 1, thick - 1);
            Mountain.archDoor(s, sx(p, p.fcx + fr - thick + 1, side), w, (p.sz1 + p.sz2) / 2, in, 1, thick - 1);
            Mountain.archDoor(s, sx(p, p.gx1 + 1, side), w, (p.gz1 + p.gz2) / 2, out, 1, 1);
        }
    }

    // ---------------------------------------------------------------- the throne hall

    /**
     * The throne hall: a floor of dressed stone with a dark border; the place of the throne on a dark dais with gilt
     * corners, a high back of runes and gold, before the carved face of the forefather between two hangings; two rows of
     * pillars carrying heavy beams of hewn logs on stone corbels; hearths in the side walls, braziers by the dais, benches of
     * stone along the walls, a frieze of runes and gilt stone under the beams, a red runner from the door to the dais, great
     * chandeliers and lanterns, banners.
     */
    private static void throneHall(Structure s, Plan p) {
        int c = p.c;
        int x1 = p.hx1;
        int x2 = p.hx2;
        int z1 = p.hz1;
        int z2 = p.hz2;
        int top = p.walk - 1;
        // The floor: a dark border, the dais.
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                put(s, x, 0, z, edge ? DARK : FLOOR);
            }
        }
        box(s, c - 2, 0, z1, c + 2, 0, c + 1, DARK);
        for (int x : new int[]{c - 2, c + 2}) {
            put(s, x, 0, c + 1, Mountain.GILDED);
        }
        // The throne's place: arm rests, a step before it, a high back of runes and gold.
        put(s, c - 1, 1, c, Kit.stairs(Mountain.DARK_STAIRS, "east"));
        put(s, c + 1, 1, c, Kit.stairs(Mountain.DARK_STAIRS, "west"));
        put(s, c - 1, 1, c + 1, Kit.stairs(Mountain.DARK_STAIRS, "north"));
        put(s, c + 1, 1, c + 1, Kit.stairs(Mountain.DARK_STAIRS, "north"));
        box(s, c - 1, 1, c - 1, c + 1, 1, c - 1, DARK);
        put(s, c, 2, c - 1, Mountain.RUNE);
        put(s, c, 3, c - 1, Mountain.GOLD);
        put(s, c - 1, 2, c - 1, Kit.stairs(Mountain.DARK_STAIRS, "east"));
        put(s, c + 1, 2, c - 1, Kit.stairs(Mountain.DARK_STAIRS, "west"));
        // The carved face of the forefather on the wall behind it, hangings beside it.
        Mountain.face(s, c, Math.max(0, p.walk - 9), z1 - 1, "south", 0);
        if (!p.small()) {
            for (int dx : new int[]{-4, 4}) {
                Mountain.hanging(s, c + dx, top - 1, z1, "south", top - 2);
            }
        }
        // Pillars and beams.
        int[] rows = p.small() ? new int[]{c - 1, c + 3} : new int[]{c - 1, c + 2, c + 5};
        for (int z : rows) {
            for (int side : SIDES) {
                int x = c + side * p.pillar;
                Mountain.column(s, x, z, 1, top - 1);
                put(s, x - side, 3, z, Kit.wallTorch(side < 0 ? "east" : "west"));
            }
            for (int x = x1; x <= x2; x++) {
                put(s, x, top, z, Kit.log(Mountain.BEAM, "x"));
            }
            put(s, x1, top - 1, z, Kit.upside(Mountain.TRIM_STAIRS, "west"));
            put(s, x2, top - 1, z, Kit.upside(Mountain.TRIM_STAIRS, "east"));
        }
        for (int side : SIDES) {
            int x = c + side * p.pillar;
            for (int z = z1; z <= z2; z++) {
                boolean row = false;
                for (int r : rows) {
                    row |= r == z;
                }
                if (!row) {
                    put(s, x, top, z, Kit.log(Mountain.BEAM, "z"));
                }
            }
        }
        // Hearths in the side walls, their stacks on the terrace.
        int zm = (z1 + z2 + 1) / 2;
        for (int side : SIDES) {
            int x = side < 0 ? x1 : x2;
            Mountain.fireplace(s, x, 1, zm, side < 0 ? "east" : "west", top);
            Mountain.stack(s, x + side, zm, p.walk, p.walk + 2, 1);
            // Benches of stone along the walls on both sides of the hearth.
            for (int z = z1 + 2; z <= z2 - 1; z++) {
                if (Math.abs(z - zm) > 1) {
                    place(s, x, 1, z, Kit.stairs(Mountain.TRIM_STAIRS, side < 0 ? "west" : "east"));
                }
            }
            for (int z = z1 + 2; z <= z2; z += 3) {
                if (Math.abs(z - zm) > 1) {
                    place(s, x, 4, z, Kit.wallBanner("red", side < 0 ? "east" : "west"));
                }
            }
            // Braziers by the dais.
            Mountain.brazier(s, c + side * (p.small() ? 2 : 3), 1, c - 1, false);
        }
        // A frieze of runes and gilt stone round the walls under the beams.
        Mountain.runeBand(s, x1 - 1, z1, x1 - 1, z2, top - 2);
        Mountain.runeBand(s, x2 + 1, z1, x2 + 1, z2, top - 2);
        Mountain.runeBand(s, x1, z2 + 1, x2, z2 + 1, top - 2);
        // The runner from the door to the dais, chandeliers and lanterns.
        int rw = p.small() ? 0 : 1;
        box(s, c - rw, 1, c + 2, c + rw, 1, z2, CARPET);
        Furnish.greatChandelier(s, c, c + 3, top, 2);
        if (!p.small()) {
            Furnish.greatChandelier(s, c, z2 - 1, top, 2);
        }
        for (int side : SIDES) {
            for (int z = z1 + 1; z <= z2; z += 3) {
                hang(s, c + side * (p.pillar + 2), top - 1, z);
            }
        }
    }

    // ---------------------------------------------------------------- the treasury

    /**
     * The treasury in the foot of the keep behind the hall: a counting room with a table of stone, chairs, ledgers and
     * candles before a grille of iron with a door in it, and behind the grille the hoard: the two chests of the treasury,
     * heaps of gold and raw gold, gilt stone, bins of raw ore and casks; lanterns under the vault.
     */
    private static void treasury(Structure s, Plan p) {
        int c = p.c;
        int i1 = p.kx1 + 2;
        int i2 = p.kx2 - 2;
        int j1 = p.kz1 + 2;
        int j2 = p.kz2 - 2;
        int dx = p.hx1 + 2;
        // The door from the hall.
        Mountain.archDoor(s, dx, 1, p.hz1 - 1, "south", 1, 0);
        box(s, dx, 1, j2 + 1, dx, 2, p.hz1 - 2, AIR);
        // The grille.
        int gz = j1 + (j2 - j1) / 2;
        for (int x = i1; x <= i2; x++) {
            boolean post = Math.floorMod(x - c, 3) == 0 && x != c;
            for (int y = 1; y <= 3; y++) {
                put(s, x, y, gz, post ? Mountain.DARK_WALL : Mountain.IRON);
            }
            put(s, x, 4, gz, DARK);
        }
        s.door(c, 1, gz, Mountain.DOOR, "north", "left");
        // The hoard.
        Furnish.chest(s, c - 2, 1, j1, "south");
        Furnish.chest(s, c + 2, 1, j1, "south");
        for (int side : SIDES) {
            int x = side < 0 ? i1 : i2;
            put(s, x, 1, j1, Mountain.GOLD);
            put(s, x, 2, j1, Mountain.GOLD);
            put(s, x - side, 1, j1, Mountain.GOLD);
            put(s, x, 1, j1 + 1, "minecraft:raw_gold_block");
            put(s, x - side, 1, j1 + 1, Mountain.GILDED);
            if (gz - j1 >= 4) {
                Mountain.stockPile(s, x, j1 + 2, x, gz - 1, 1);
            }
        }
        if (!p.small()) {
            for (int side : SIDES) {
                Mountain.column(s, c + side * 3, gz - 2, 1, p.walk - 1);
            }
        }
        put(s, c - 1, 1, j1, Mountain.GOLD);
        put(s, c + 1, 1, j1, Mountain.GOLD);
        put(s, c, 1, j1, "minecraft:raw_gold_block");
        put(s, c, 2, j1, Mountain.GOLD);
        // The counting room.
        int tz = gz + 2;
        if (tz < j2) {
            Mountain.stoneTable(s, c - 2, tz, c + 2, tz, 1, false);
            for (int x = c - 1; x <= c + 1; x += 2) {
                place(s, x, 1, tz + 1, Kit.stairs(Mountain.STAIRS, "south"));
            }
        }
        for (int z = gz + 1; z <= j2; z++) {
            place(s, i2, 1, z, "minecraft:bookshelf");
            place(s, i2, 2, z, "minecraft:bookshelf");
        }
        Furnish.rug(s, c - 1, gz + 1, c + 1, gz + 1, 1, CARPET);
        for (int z = j1 + 1; z <= j2; z += 3) {
            hang(s, c, p.walk - 1, z);
            hang(s, c - 3, p.walk - 1, z);
            hang(s, c + 3, p.walk - 1, z);
        }
        p.point(TREASURY, dx, 1, p.hz1);
    }

    // ---------------------------------------------------------------- the keep's rooms

    /**
     * The rooms of the keep above the treasury: the council room on the level of the terrace (a long table of stone with
     * benches and the lord's chair at its head, a hearth, shelves of books, banners and a great chandelier), the chambers
     * (a bed of state under its canopy, a chest, a hearth, a table with chairs, a rug), the library (shelves of books along
     * the walls, a reading table and a lectern, a stone of runes) and the watch room under the crown (benches, a table, a
     * rack of weapons, casks); lanterns on every floor.
     */
    private static void keepRooms(Structure s, Plan p) {
        int c = p.c;
        int i1 = p.kx1 + 2;
        int i2 = p.kx2 - 2;
        int j1 = p.kz1 + 2;
        int j2 = p.kz2 - 2;
        int kc = p.kzc();
        int[] fl = p.floors;
        // The council room.
        int y = fl[0] + 1;
        Mountain.stoneTable(s, c, j1 + 2, c, j2 - 3, y, true);
        Furnish.chair(s, c, y, j1 + 1, "south", WOOD);
        Mountain.fireplace(s, i2, y, kc, "west", fl[1] - 1);
        for (int x = i1 + 2; x <= i2; x++) {
            if (Math.abs(x - c) > 1) {
                place(s, x, y, j1, "minecraft:bookshelf");
                place(s, x, y + 1, j1, "minecraft:bookshelf");
            }
        }
        for (int z = j1 + 2; z <= j2 - 1; z += 3) {
            place(s, i1, y + 2, z, Kit.wallBanner("red", "east"));
        }
        Furnish.greatChandelier(s, c, kc, fl[1] - 1, 2);
        hang(s, i1 + 1, fl[1] - 1, j2);
        hang(s, i2 - 1, fl[1] - 1, j2);
        // The chambers.
        y = fl[1] + 1;
        Furnish.canopyBed(s, c, y, j1 + 2, "north", WOOD);
        Furnish.chest(s, i2, y, j1, "west");
        place(s, c + 2, y, j1 + 1, "minecraft:stripped_spruce_wood[axis=y]");
        place(s, c + 2, y + 1, j1 + 1, Furnish.candles(2));
        place(s, i1 + 1, y, j2, "minecraft:stripped_spruce_wood[axis=y]");
        place(s, i1 + 1, y + 1, j2, "minecraft:stripped_spruce_wood[axis=y]");
        place(s, i1, y + 2, j1 + 2, Kit.wallBanner("red", "east"));
        Mountain.fireplace(s, i1, y, kc + 1, "east", fl[2] - 1);
        Furnish.table(s, i2 - 1, y, j2 - 1, WOOD);
        Furnish.chair(s, i2 - 2, y, j2 - 1, "east", WOOD);
        Furnish.rug(s, c - 1, j1 + 4, c + 1, j1 + 5, y, CARPET);
        place(s, i2, y, j2, "minecraft:bookshelf");
        place(s, i2, y + 1, j2, "minecraft:bookshelf");
        place(s, i2, y + 2, kc, Kit.wallBanner("red", "west"));
        hang(s, c, fl[2] - 1, kc + 1);
        hang(s, i1 + 1, fl[2] - 1, j2);
        hang(s, i2 - 1, fl[2] - 1, j1 + 2);
        hang(s, i2 - 1, fl[2] - 1, j2 - 2);
        // The library.
        y = fl[2] + 1;
        for (int z = j1 + 1; z <= j2; z++) {
            for (int yy = y; yy <= y + 2; yy++) {
                place(s, i1, yy, z, "minecraft:bookshelf");
                place(s, i2, yy, z, "minecraft:bookshelf");
            }
        }
        for (int x = i1 + 2; x <= i2 - 1; x++) {
            if (Math.abs(x - c) > 1) {
                place(s, x, y, j1, "minecraft:bookshelf");
                place(s, x, y + 1, j1, "minecraft:bookshelf");
            }
        }
        Mountain.stoneTable(s, c - 1, kc + 1, c + 1, kc + 1, y, false);
        place(s, c - 1, y, kc + 2, Kit.stairs(Mountain.STAIRS, "south"));
        place(s, c + 1, y, kc + 2, Kit.stairs(Mountain.STAIRS, "south"));
        place(s, c, y, j1 + 1, "minecraft:lectern[facing=south,has_book=false,powered=false]");
        hang(s, c, fl[3] - 1, kc);
        hang(s, c, fl[3] - 1, j2 - 1);
        // The watch room.
        if (fl.length > 3) {
            y = fl[3] + 1;
            Mountain.stoneTable(s, c - 1, kc, c + 1, kc, y, true);
            place(s, i2, y, j1, "minecraft:grindstone[face=floor,facing=west]");
            place(s, i2, y, j1 + 1, "minecraft:grindstone[face=floor,facing=west]");
            Mountain.casks(s, i1, j2, i1 + 2, j2, y, "north");
            hang(s, c, p.kf - 1, kc - 2);
            hang(s, c, p.kf - 1, j2 - 1);
        }
    }

    // ---------------------------------------------------------------- the back towers

    /**
     * The kitchen on the ground of the western back tower: a wide hearth in the west wall, the cooking corner with its
     * cauldron and fire, a smoker and a furnace, a table for the cooks, casks, sacks and the one barrel of the household; on
     * the storey above, the hall of the guards with its table and benches.
     */
    private static void kitchen(Structure s, Plan p) {
        int r = (p.bw - 1) / 2;
        int cx = p.bcx;
        int cz = p.bcz;
        int i1 = cx - r + 2;
        int i2 = cx + r - 2;
        int j1 = cz - r + 2;
        int j2 = cz + r - 2;
        Mountain.fireplace(s, i1, 1, cz, "east", p.walk - 1);
        if (i1 + 2 < cx) {
            Furnish.kitchen(s, i1, 1, j1, "south", WOOD);
        } else {
            place(s, i1, 1, j1, "minecraft:water_cauldron[level=3]");
        }
        place(s, i2, 1, j1 + 1, "minecraft:smoker[facing=west,lit=true]");
        place(s, i2, 1, j1 + 2, "minecraft:furnace[facing=west,lit=true]");
        place(s, i2, 1, j2 - 1, Kit.barrel());
        Furnish.sack(s, i2, 1, j2, true);
        place(s, i2, 2, j2, "minecraft:brown_wool");
        Mountain.casks(s, i1, j2, i1 + 1, j2, 1, "north");
        Mountain.stoneTable(s, cx - 1, cz + 1, cx + (p.small() ? 0 : 1), cz + 1, 1, false);
        // The hall of the guards above.
        int y = p.walk + 1;
        Mountain.stoneTable(s, cx - 1, cz, cx + 1, cz, y, true);
        place(s, i1, y, j2, "minecraft:grindstone[face=floor,facing=east]");
        place(s, i1, y + 1, cz, Kit.wallBanner("red", "east"));
    }

    /**
     * The armoury on the ground of the eastern back tower: a forge under its copper hood against the east wall (without
     * furnaces: it is no workshop), an anvil, racks of weapons, a bin of iron ore and coal, shields of the kingdom's colour
     * on the walls; on the storey above, the store of arms with more racks and casks.
     */
    private static void armoury(Structure s, Plan p) {
        int r = (p.bw - 1) / 2;
        int cx = p.n - 1 - p.bcx;
        int cz = p.bcz;
        int i1 = cx - r + 2;
        int i2 = cx + r - 2;
        int j1 = cz - r + 2;
        int j2 = cz + r - 2;
        if (!p.small()) {
            Mountain.forge(s, i2 - 2, 1, cz, "west", p.walk - 1, false);
            for (int z = cz - 1; z <= cz + 1; z++) {
                put(s, i2, p.walk, z, PLANKS);
                put(s, i2 + 1, p.walk, z, WALL);
            }
            place(s, i2 - 4, 1, cz, "minecraft:anvil[facing=north]");
        } else {
            place(s, i2, 1, cz, "minecraft:anvil[facing=north]");
            Mountain.brazier(s, i2, 1, j2, false);
        }
        place(s, i1, 1, j1 + 1, "minecraft:grindstone[face=floor,facing=east]");
        place(s, i1, 1, j1 + 3, "minecraft:grindstone[face=floor,facing=east]");
        Mountain.stockPile(s, i1, j1, i1 + 1, j1, 1);
        for (int z = j1 + 1; z <= j2 - 1; z += 2) {
            place(s, i1, 3, z, Kit.wallBanner("red", "east"));
        }
        // The store of arms above.
        int y = p.walk + 1;
        place(s, i2, y, cz - 1, "minecraft:grindstone[face=floor,facing=west]");
        place(s, i2, y, cz + 1, "minecraft:grindstone[face=floor,facing=west]");
        Mountain.casks(s, i1, j1, i1 + 1, j1, y, "south");
        place(s, i2, y + 1, cz, Kit.wallBanner("red", "west"));
    }

    // ---------------------------------------------------------------- the front towers and the gate

    /**
     * The guard rooms of the front towers: a table with benches and a rack of weapons on the storey of the walks, stores on
     * the ground below (crates, sacks, hay).
     */
    private static void guardRooms(Structure s, Plan p) {
        int fr = (p.fw - 1) / 2;
        int thick = p.fw >= 9 ? 2 : 1;
        for (int side : SIDES) {
            int cx = sx(p, p.fcx, side);
            int cz = p.fcz;
            int in = fr - thick;
            int y = p.walk + 1;
            Mountain.stoneTable(s, cx, cz, cx, cz + 1, y, true);
            place(s, cx - side * in, y, cz + in - 1, "minecraft:grindstone[face=floor,facing=" + sf("east", side) + "]");
            place(s, cx - side * in, 1, cz + in - 1, "minecraft:stripped_spruce_wood[axis=y]");
            place(s, cx - side * in, 2, cz + in - 1, "minecraft:stripped_spruce_wood[axis=y]");
            place(s, cx - side * (in - 1), 1, cz + in, "minecraft:brown_wool");
            place(s, cx + side * in, 1, cz + in, "minecraft:hay_block[axis=y]");
            place(s, cx + side * in, 1, cz, "minecraft:hay_block[axis=x]");
        }
    }

    /**
     * The room of the winch over the gate tunnel: the drum of the portcullis on its posts with the chain going down, benches,
     * a ladder up to the top of the gate, lanterns.
     */
    private static void gateRoom(Structure s, Plan p) {
        int c = p.c;
        int y = p.walk + 1;
        int zr = p.gz2 - 2;
        if (!p.small()) {
            for (int x = c - 2; x <= c + 2; x++) {
                put(s, x, y + 1, zr, Kit.log(Mountain.BEAM, "x"));
            }
            for (int x : new int[]{c - 3, c + 3}) {
                put(s, x, y, zr, Mountain.FENCE);
                put(s, x, y + 1, zr, Mountain.FENCE);
            }
            put(s, c, y, zr, Kit.chain());
            for (int x = p.gx1 + 3; x <= p.gx2 - 3 && p.gz2 - p.gz1 >= 6; x++) {
                if (Math.abs(x - c) > 1) {
                    place(s, x, y, p.gz1 + 2, Kit.stairs(Mountain.STAIRS, "north"));
                }
            }
            for (int yy = y; yy <= p.gtop; yy++) {
                put(s, p.gx1 + 2, yy, zr, Kit.ladder("east"));
            }
        } else {
            for (int yy = y; yy <= p.gtop; yy++) {
                put(s, c, yy, p.gz1 + 2, Kit.ladder("south"));
            }
        }
        hang(s, c - 2, p.gtop - 1, p.gz1 + 2);
        hang(s, c + 2, p.gtop - 1, p.gz1 + 2);
    }

    // ---------------------------------------------------------------- the forecourt

    /**
     * The forecourt between the gate and the hall: great braziers on both sides of the path (small ones in the small hold)
     * and stones of runes by the flights.
     */
    private static void forecourt(Structure s, Plan p) {
        int c = p.c;
        int depth = p.gz1 - p.front() - 1;
        for (int side : SIDES) {
            if (depth >= 4) {
                Mountain.brazier(s, c + side * 6, 1, p.gz1 - 2, true);
            } else {
                Mountain.brazier(s, c + side * 5, 1, p.front() + 1, false);
            }
        }
    }

    // ---------------------------------------------------------------- the named places

    /**
     * The named places: the guards of the hall, the door of the palace, the gate passage and the walks.
     */
    private static void places(Structure s, Plan p) {
        int c = p.c;
        p.point(HALL, c - 2, 1, c + 3);
        p.point(HALL, c + 2, 1, c + 3);
        p.point(HALL, c - 2, 1, c + 1);
        p.point(HALL, c + 2, 1, c + 1);
        p.point(PALACE_DOOR, c - 1, 1, p.hz2);
        p.point(PALACE_DOOR, c + 1, 1, p.hz2);
        p.point(GATE, c - 1, 1, p.gz1 + 2);
        p.point(GATE, c + 1, 1, p.gz1 + 2);
        int w = p.walk + 1;
        for (int side : SIDES) {
            p.point(WALK, sx(p, p.cw1 + 1, side), w, (p.landing() + p.sideEnd()) / 2);
            p.point(WALK, sx(p, p.fcx, side), p.fh + 1, p.fcz + 2);
            p.point(WALK, c + side * 3, p.gtop + 1, (p.gz1 + p.gz2) / 2);
            p.point(WALK, c + side * 4, w, p.front() - 1);
        }
    }

    // ---------------------------------------------------------------- the ground

    /**
     * The ground: the forecourt paved with rough stone and a path of dressed stone from the gate to the hall, grass round
     * the hold.
     */
    private static void ground(Structure s, Plan p) {
        int c = p.c;
        for (int z = p.front() + 1; z < p.gz1; z++) {
            for (int x = p.fx1; x <= p.n - 1 - p.fx1; x++) {
                if (free(s, x, 0, z)) {
                    put(s, x, 0, z, Math.abs(x - c) <= 1 ? FLOOR : ROUGH);
                }
            }
        }
        for (int z = 0; z < p.n; z++) {
            for (int x = 0; x < p.n; x++) {
                if (free(s, x, 0, z)) {
                    put(s, x, 0, z, GRASS);
                }
            }
        }
    }
}
