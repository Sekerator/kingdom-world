package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The town of the style "terem" (see {@link Terem}): its houses, from the half sunken hut of the poor under a roof of turf
 * to the boyar's mansion of two log blocks round a fenced court, its smithy, its tavern and a piece of its wooden wall
 * with the gate. All of them are log frames on footings of rough stone under steep carved roofs, with windows in white
 * carved frames, porches on posts and a whitewashed stove inside, furnished for those who live or work there.
 */
final class TeremTown {
    private static final String LOG = Terem.LOG;
    private static final String POST = Terem.POST;
    private static final String PLANKS = Terem.PLANKS;
    private static final String FLOOR = Terem.FLOOR;
    private static final String CARVE = Terem.CARVE;
    private static final String CARVE_STAIRS = Terem.CARVE_STAIRS;
    private static final String CARVE_SLAB = Terem.CARVE_SLAB;
    private static final String CARVE_FENCE = Terem.CARVE_FENCE;
    private static final String FOOTING = Terem.FOOTING;
    private static final String WHITEWASH = Terem.WHITEWASH;
    private static final String MOSS = "minecraft:moss_block";
    private static final String MOSS_CARPET = "minecraft:moss_carpet";
    private static final String EARTH_FLOOR = "minecraft:dirt_path";

    private TeremTown() {
    }

    // ---------------------------------------------------------------- the plans of the houses

    /**
     * The houses the house program draws: their names, their ground and their beds.
     */
    enum Plan {
        IZBA("izba", 7, 9, 2, "minecraft:spruce_log"),
        PYATISTENOK("pyatistenok", 9, 11, 3, "minecraft:spruce_door"),
        MERCHANT("merchant", 11, 11, 3, "minecraft:yellow_banner"),
        TOWER("tower", 9, 11, 3, "minecraft:gold_block"),
        BOYAR("boyar", 13, 15, 4, "minecraft:birch_log"),
        HUT("hut", 5, 7, 1, "minecraft:moss_block");

        final String key;
        final int width;
        final int depth;
        final int beds;
        final String icon;

        Plan(String key, int width, int depth, int beds, String icon) {
            this.key = key;
            this.width = width;
            this.depth = depth;
            this.beds = beds;
            this.icon = icon;
        }
    }

    /**
     * The town buildings of the style: the six houses of the house program, the smithy, the tavern and the piece of the
     * town wall with its gate.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Plan plan : Plan.values()) {
            Structure s = house(plan);
            list.add(new BuildingGenerator.Building("terem_house_" + plan.key, "housing", "house", plan.icon, (s.sizeX - 1) / 2, 0,
                    s.sizeZ - 1, s, "house", 1, ""));
        }
        Structure smithy = smithy();
        list.add(new BuildingGenerator.Building("terem_smithy", "production", "workshop", "minecraft:anvil", (smithy.sizeX - 1) / 2, 0,
                smithy.sizeZ - 1, smithy, "workshop", 1, "").at("door", 3, 1, 9));
        Structure tavern = tavern();
        list.add(new BuildingGenerator.Building("terem_tavern", "trade", "tavern", "minecraft:barrel", (tavern.sizeX - 1) / 2, 0,
                tavern.sizeZ - 1, tavern, "tavern", 1, "").at("door", 6, 3, 10));
        Structure gate = wallGate();
        list.add(new BuildingGenerator.Building("terem_wall_gate", "defense", "gatehouse", "minecraft:spruce_log", (gate.sizeX - 1) / 2, 0,
                gate.sizeZ - 1, gate, "tower", 1, "")
                .at("door", 11, 0, 10)
                .at("castle_gate", 10, 1, 5).at("castle_gate", 12, 1, 5)
                .at("castle_wall", 3, 6, 4).at("castle_wall", 19, 6, 4));
        return list;
    }

    /**
     * The house program: draws the house of a plan.
     */
    static Structure house(Plan plan) {
        return switch (plan) {
            case IZBA -> izba();
            case PYATISTENOK -> pyatistenok();
            case MERCHANT -> merchant();
            case TOWER -> terem();
            case BOYAR -> boyar();
            case HUT -> hut();
        };
    }

    // ---------------------------------------------------------------- shared shapes

    /**
     * A footing of rough stone under the walls of a frame from (x1, z1) to (x2, z2) at layer {@code y}, and a floor of
     * boards inside it.
     */
    private static void footing(Structure s, int x1, int z1, int x2, int z2, int y) {
        s.fill(x1, y, z1, x2, y, z2, FOOTING);
        s.fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, FLOOR);
    }

    /**
     * A small Russian stove of two by two cells on the floor at layer {@code y}, (x1, z1) its corner of the lowest x and z,
     * its mouth looking {@code facing}: a whitewashed body three layers high, the fire behind an iron grate in the front
     * row, the warm bed under cloth on the back row and the stack rising from the front corner up to layer {@code top}.
     */
    private static void pech(Structure s, int x1, int z1, int y, String facing, int top) {
        int[][] cells = {{x1, z1}, {x1 + 1, z1}, {x1, z1 + 1}, {x1 + 1, z1 + 1}};
        int[] f = Furnish.step(facing);
        // front cells: those whose neighbour in the facing lies outside the stove
        int[] mouth = null;
        int[] side = null;
        for (int[] c : cells) {
            boolean front = !inStove(c[0] + f[0], c[1] + f[1], x1, z1);
            for (int yy = y; yy <= y + 2; yy++) {
                s.set(c[0], yy, c[1], WHITEWASH);
            }
            if (front) {
                if (mouth == null) {
                    mouth = c;
                } else {
                    side = c;
                }
            }
        }
        for (int[] c : cells) {
            boolean front = c == mouth || c == side;
            if (!front) {
                s.set(c[0], y + 3, c[1], Kit.CLOTH_CARPET);
            }
        }
        s.set(mouth[0], y + 1, mouth[1], Kit.BARS);
        s.set(mouth[0] - f[0], y + 1, mouth[1] - f[1], "minecraft:campfire[facing=" + facing + ",lit=true,signal_fire=false,waterlogged=false]");
        for (int yy = y + 3; yy <= top; yy++) {
            s.set(mouth[0], yy, mouth[1], WHITEWASH);
        }
        s.set(side[0], y + 3, side[1], "minecraft:flower_pot");
    }

    /**
     * Whether a column lies in the two by two cells of a small stove whose corner is (x1, z1).
     */
    private static boolean inStove(int x, int z, int x1, int z1) {
        return x >= x1 && x <= x1 + 1 && z >= z1 && z <= z1 + 1;
    }

    /**
     * Two windows side by side in a narrow log wall, one block to each side of (x, z), drawn as one carved frame: a sill
     * under both, a carved post between them and a lintel over both with a crown over each window.
     *
     * @param x the column between the two windows
     */
    private static void windowPair(Structure s, int x, int y, int z, String facing) {
        int[] out = Furnish.step(facing);
        int[] al = Furnish.step(Furnish.left(facing));
        String plus = Furnish.left(facing);
        String minus = Furnish.opposite(plus);
        int fx = x + out[0];
        int fz = z + out[1];
        for (int u : new int[]{-1, 1}) {
            s.fill(x + al[0] * u, y, z + al[1] * u, x + al[0] * u, y + 1, z + al[1] * u, Terem.MICA);
        }
        for (int u = -1; u <= 1; u++) {
            s.set(fx + al[0] * u, y - 1, fz + al[1] * u, Kit.upside(CARVE_STAIRS, Furnish.opposite(facing)));
            s.set(fx + al[0] * u, y + 2, fz + al[1] * u, CARVE);
        }
        s.set(fx, y, fz, CARVE_FENCE).set(fx, y + 1, fz, CARVE_FENCE);
        s.set(fx - al[0] * 2, y + 2, fz - al[1] * 2, Kit.stairs(CARVE_STAIRS, plus));
        s.set(fx + al[0] * 2, y + 2, fz + al[1] * 2, Kit.stairs(CARVE_STAIRS, minus));
        s.set(fx - al[0], y + 3, fz - al[1], Kit.slab(CARVE_SLAB));
        s.set(fx + al[0], y + 3, fz + al[1], Kit.slab(CARVE_SLAB));
    }

    /**
     * The fifth wall of a five-walled house: an inner wall of lying logs along x at {@code z} between the side walls
     * {@code x1} and {@code x2}, whose logs on every other course pass through the side walls and stick out beyond them like
     * those of the corners.
     */
    private static void crossWall(Structure s, int x1, int x2, int z, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            boolean through = Math.floorMod(y, 2) == 0;
            for (int x = through ? x1 - 1 : x1 + 1; x <= (through ? x2 + 1 : x2 - 1); x++) {
                if (s.inside(x, y, z)) {
                    s.set(x, y, z, Kit.log(LOG, "x"));
                }
            }
        }
    }

    /**
     * The carved horse (konyok) on the end of a ridge log at (x, y, z): a white wedge rising toward the way it looks.
     */
    private static void konyok(Structure s, int x, int y, int z, String outward) {
        if (s.inside(x, y + 1, z)) {
            s.set(x, y + 1, z, Kit.stairs(CARVE_STAIRS, outward));
        }
    }

    /**
     * The compass name of a step along x or z.
     */
    private static String toward(int dx, int dz) {
        return dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
    }

    /**
     * A flight of {@code n} steps from (x, y, z) rising one layer for every block the way of (dx, dz), each step carried
     * by a stair hanging upside down under it like a sawn stringer.
     */
    private static void flight(Structure s, int x, int y, int z, int dx, int dz, int n, String stairs) {
        String up = toward(dx, dz);
        for (int i = 0; i < n; i++) {
            int px = x + dx * i;
            int pz = z + dz * i;
            s.set(px, y + i, pz, Kit.stairs(stairs, up));
            if (y + i - 1 >= 0 && i > 0) {
                s.set(px, y + i - 1, pz, Kit.upside(stairs, Furnish.opposite(up)));
            }
        }
    }

    /**
     * A birch: a white trunk from layer {@code y} and a crown of leaves that never wither around its top.
     */
    private static void birch(Structure s, int x, int y, int z, int height) {
        String leaves = Kit.leaves("birch");
        int top = y + height;
        for (int yy = top - 3; yy <= top + 1; yy++) {
            int r = yy == top + 1 ? 0 : yy == top - 3 || yy == top ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    if (!corner && s.inside(x + dx, yy, z + dz) && !s.solid(x + dx, yy, z + dz)) {
                        s.set(x + dx, yy, z + dz, leaves);
                    }
                }
            }
        }
        s.fill(x, y, z, x, top, z, Kit.log("minecraft:birch_log", "y"));
    }

    // ---------------------------------------------------------------- the izba

    /**
     * A northern izba: one log room on a footing of rough stone, its steep gable to the street with carved boards, a towel
     * under the ridge and a little window in the gable over two windows in one carved frame. The door is on the east side
     * under a little roof on carved brackets, with a step and a bench by the wall. Inside: the stove in the back corner with
     * its stack through the roof, two beds, the chest, a table with benches in the red corner across from the stove, and a
     * chandelier of candles.
     */
    static Structure izba() {
        Structure s = new Structure(7, 14, 9);
        footing(s, 1, 1, 5, 7, 0);
        Terem.logWalls(s, 1, 1, 1, 5, 6, 7);
        s.fill(2, 6, 2, 4, 6, 6, PLANKS);
        int ridge = Terem.gableRoof(s, 1, 1, 5, 7, 6, false, 2);
        konyok(s, 3, ridge, 8, "south");
        konyok(s, 3, ridge, 0, "north");
        // The front: two windows in one frame, the gable window under the towel.
        windowPair(s, 3, 2, 7, "south");
        s.set(3, 7, 7, Terem.MICA).set(3, 6, 8, Kit.upside(CARVE_STAIRS, "north"));
        s.set(2, 7, 8, CARVE_FENCE).set(4, 7, 8, CARVE_FENCE);
        // The back gable window and a window to the west.
        s.set(3, 7, 1, Terem.MICA).set(3, 6, 0, Kit.upside(CARVE_STAIRS, "south"));
        Terem.window(s, 1, 2, 4, "west", 1, 2, true);
        // The door on the east side under its little roof.
        s.door(5, 1, 5, Terem.DOOR, "west", "left");
        s.set(6, 0, 5, Kit.slab(Terem.FLOOR_SLAB));
        s.fill(6, 0, 4, 6, 1, 4, CARVE_FENCE).fill(6, 0, 6, 6, 1, 6, CARVE_FENCE);
        s.set(6, 2, 4, Kit.upside(CARVE_STAIRS, "south")).set(6, 2, 6, Kit.upside(CARVE_STAIRS, "north"));
        s.fill(6, 3, 3, 6, 3, 6, Kit.stairs(Terem.ROOF.stairs(), "west"));
        s.fill(6, 0, 2, 6, 0, 3, Kit.slab(Terem.FLOOR_SLAB));
        s.set(6, 2, 3, Kit.hangingLantern());
        s.set(3, 9, 4, Kit.hangingLantern());
        // Inside: the stove in the back west corner, its mouth to the front, its stack through the roof.
        pech(s, 2, 2, 1, "south", 10);
        s.fill(2, 11, 3, 2, 12, 3, Kit.CHIMNEY).set(2, 13, 3, Kit.CHIMNEY_POT);
        Furnish.bed(s, 4, 1, 3, "north", "red");
        Furnish.bed(s, 2, 1, 5, "north", "red");
        Terem.chestPlace(s, 2, 1, 6, "east");
        s.set(3, 1, 6, Kit.topSlab(Terem.FLOOR_SLAB)).set(3, 2, 6, Furnish.candles(2));
        Terem.lavka(s, 4, 6, 4, 6, 1);
        Terem.redCorner(s, 4, 1, 6, "south", "east");
        Terem.candleChandelier(s, 3, 4, 5, 1);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the five-walled house

    /**
     * A five-walled house (pyatistenok): a long log frame whose gable looks at the street, cut in two by an inner wall whose
     * log ends stick out of both sides between the corners. The back room is the kitchen with the stove, the front room
     * the clean room with two beds and the red corner, and under the steep carved roof a light room (svetelka) with the
     * third bed looks out of the gable over two windows in one carved frame. The door is in the east side under a porch
     * with a gable roof of its own on carved posts, a bench and a lantern; a ladder leads up to the light room.
     */
    static Structure pyatistenok() {
        Structure s = new Structure(9, 14, 11);
        footing(s, 1, 1, 5, 9, 0);
        Terem.logWalls(s, 1, 1, 1, 5, 5, 9);
        s.fill(2, 5, 2, 4, 5, 8, PLANKS);
        crossWall(s, 1, 5, 5, 1, 5);
        s.door(3, 1, 5, Terem.DOOR, "south", "left");
        // Windows before the roof, so that its eaves keep their place.
        windowPair(s, 3, 2, 9, "south");
        Terem.window(s, 1, 2, 3, "west", 1, 2, true);
        Terem.window(s, 1, 2, 7, "west", 1, 2, true);
        Terem.window(s, 5, 2, 7, "east", 1, 2, true);
        int ridge = Terem.gableRoof(s, 1, 1, 5, 9, 5, false, 2, Terem.ROOF, PLANKS, true);
        konyok(s, 3, ridge, 10, "south");
        konyok(s, 3, ridge, 0, "north");
        // The light room's window in the boarded gable under the towel, behind a little balcony on carved brackets; a
        // little window in the back gable.
        s.set(3, 6, 9, Terem.MICA);
        s.fill(2, 5, 10, 4, 5, 10, FLOOR).fill(2, 6, 10, 4, 6, 10, CARVE_FENCE);
        s.set(2, 4, 10, Kit.upside(CARVE_STAIRS, "north")).set(4, 4, 10, Kit.upside(CARVE_STAIRS, "north"));
        s.set(2, 7, 10, CARVE_FENCE).set(4, 7, 10, CARVE_FENCE);
        s.set(3, 6, 1, Terem.MICA).set(3, 5, 0, Kit.upside(CARVE_STAIRS, "south"));
        // The porch on the east side: a deck, two carved posts, beams and a gable roof of its own.
        s.door(5, 1, 3, Terem.DOOR, "west", "left");
        s.fill(6, 0, 2, 8, 0, 4, FLOOR);
        s.fill(8, 1, 2, 8, 3, 2, Kit.log(POST, "y")).fill(8, 1, 4, 8, 3, 4, Kit.log(POST, "y"));
        s.set(8, 1, 3, CARVE_FENCE);
        s.fill(6, 4, 2, 8, 4, 2, Kit.log(POST, "x")).fill(6, 4, 4, 8, 4, 4, Kit.log(POST, "x"));
        s.fill(6, 4, 3, 8, 4, 3, PLANKS);
        s.fill(7, 4, 1, 8, 4, 1, Kit.stairs(Terem.ROOF.stairs(), "south"));
        s.fill(7, 4, 5, 8, 4, 5, Kit.stairs(Terem.ROOF.stairs(), "north"));
        s.fill(6, 5, 2, 8, 5, 2, Kit.stairs(Terem.ROOF.stairs(), "south"));
        s.fill(6, 5, 4, 8, 5, 4, Kit.stairs(Terem.ROOF.stairs(), "north"));
        s.fill(6, 5, 3, 7, 5, 3, Terem.ROOF.block()).set(8, 5, 3, CARVE);
        s.fill(6, 6, 3, 8, 6, 3, Kit.slab(Terem.ROOF.slab()));
        s.set(8, 3, 1, Kit.upside(CARVE_STAIRS, "north")).set(8, 3, 5, Kit.upside(CARVE_STAIRS, "south"));
        s.set(7, 3, 3, Kit.hangingLantern()).set(6, 1, 4, Kit.slab(Terem.FLOOR_SLAB));
        // The kitchen: the stove with its stack through the ridge, the ladder up, a table and a bench.
        pech(s, 2, 2, 1, "south", 9);
        s.fill(2, 10, 3, 2, 11, 3, Kit.CHIMNEY).set(2, 12, 3, Kit.CHIMNEY_POT);
        for (int y = 1; y <= 5; y++) {
            s.set(4, y, 2, Kit.ladder("south"));
        }
        s.set(4, 1, 4, Kit.topSlab(Terem.FLOOR_SLAB)).set(4, 2, 4, Furnish.candles(2));
        Terem.lavka(s, 2, 4, 2, 4, 1);
        s.set(3, 4, 3, Kit.hangingLantern());
        // The clean room: two beds, the chest, the table and the red corner, a chandelier.
        Furnish.bed(s, 2, 1, 7, "north", "red");
        Furnish.bed(s, 4, 1, 7, "north", "red");
        Furnish.chest(s, 2, 1, 8, "east");
        s.set(2, 3, 8, Kit.wallBanner("red", "east"));
        s.set(3, 1, 8, Kit.topSlab(Terem.FLOOR_SLAB)).set(3, 2, 8, Furnish.candles(3));
        Terem.lavka(s, 4, 8, 4, 8, 1);
        Terem.redCorner(s, 4, 1, 8, "south", "east");
        Furnish.chandelier(s, 3, 6, 4, 1);
        // The light room under the roof: the third bed, a bench and a lantern.
        Furnish.bed(s, 4, 6, 7, "north", "red");
        Terem.lavka(s, 2, 8, 2, 8, 6);
        s.set(3, 8, 5, Kit.hangingLantern());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the merchant's house

    /**
     * A merchant's house: a ground floor of stone with rough quoins and a band of trim, where the shop opens to the street
     * with its door, a wide window under a carved keystone and a sign; over it a log storey with a balcony on brackets and
     * carved windows, under a great barrel roof whose carved arch frames a little window in the pediment. In the shop a
     * counter with the merchant's chest behind it, bolts of cloth, sacks and crates, and a stair up to the living room,
     * which is open to the vault: three beds, the stove with its stack through the barrel, a table with benches, the red
     * corner and a chandelier of candles on a long chain.
     */
    static Structure merchant() {
        Structure s = new Structure(11, 19, 11);
        // The ground floor of stone.
        s.fill(1, 0, 1, 9, 0, 8, FOOTING).fill(2, 0, 2, 8, 0, 7, FLOOR);
        s.walls(1, 1, 1, 9, 3, 8, Kit.WALL);
        for (int[] c : new int[][]{{1, 1}, {9, 1}, {1, 8}, {9, 8}}) {
            s.fill(c[0], 1, c[1], c[0], 3, c[1], Kit.ROUGH);
        }
        s.walls(1, 4, 1, 9, 4, 8, Kit.TRIM).fill(2, 4, 2, 8, 4, 7, FLOOR);
        // The shop front: the door with its step, the wide window under a lintel with a keystone, the sign.
        s.door(3, 1, 8, Terem.DOOR, "north", "left").set(3, 0, 9, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.fill(5, 1, 8, 7, 2, 8, Kit.GLASS).fill(5, 3, 8, 7, 3, 8, Kit.TRIM).set(6, 3, 8, Kit.CARVED);
        s.fill(5, 0, 9, 7, 0, 9, Kit.slab(Kit.TRIM_SLAB));
        s.set(8, 2, 9, Kit.wallBanner("yellow", "south"));
        for (int z : new int[]{3, 6}) {
            s.set(1, 2, z, Kit.GLASS).set(9, 2, z, Kit.GLASS).set(0, 1, z, Kit.upside(Kit.TRIM_STAIRS, "east"));
            s.set(10, 1, z, Kit.upside(Kit.TRIM_STAIRS, "west"));
        }
        s.set(3, 2, 1, Kit.GLASS);
        // The log storey and its windows; on the balcony side the frames have no sills.
        Terem.logWalls(s, 1, 5, 1, 9, 9, 8);
        for (int x : new int[]{3, 7}) {
            Terem.window(s, x, 6, 8, "south", 1, 2, true);
            s.fill(x - 1, 5, 9, x + 1, 5, 9, Structure.AIR);
            Terem.window(s, x, 6, 1, "north", 1, 2, true);
        }
        Terem.window(s, 1, 6, 4, "west", 1, 2, true);
        Terem.window(s, 9, 6, 4, "east", 1, 2, true);
        // The balcony on brackets with a carved railing and its door.
        s.fill(2, 4, 9, 8, 4, 10, FLOOR);
        for (int x = 2; x <= 8; x += 2) {
            s.set(x, 3, 9, Kit.upside(Terem.STAIRS, "north"));
        }
        s.fill(2, 5, 10, 8, 5, 10, CARVE_FENCE).set(2, 5, 9, CARVE_FENCE).set(8, 5, 9, CARVE_FENCE);
        s.door(5, 5, 8, Terem.DOOR, "north", "left");
        // The barrel roof and the window in its front pediment.
        Terem.barrelRoof(s, 0, 9, 5, 9, 11, false, Terem.ROOF, null, true, true);
        s.fill(5, 11, 8, 5, 12, 8, Terem.MICA).set(5, 10, 9, Kit.upside(CARVE_STAIRS, "north"));
        s.fill(4, 11, 9, 4, 12, 9, CARVE_FENCE).fill(6, 11, 9, 6, 12, 9, CARVE_FENCE).set(5, 13, 9, Kit.slab(CARVE_SLAB));
        s.set(5, 11, 1, Terem.MICA).set(5, 10, 0, Kit.upside(CARVE_STAIRS, "south"));
        // The stair up along the back wall, its well and railing.
        for (int i = 0; i < 4; i++) {
            int x = 8 - i;
            s.set(x, 1 + i, 2, Kit.stairs(Terem.FLOOR_STAIRS, "west"));
            if (i > 0) {
                s.fill(x, 1, 2, x, i, 2, PLANKS);
            }
        }
        s.fill(6, 4, 2, 8, 4, 2, Structure.AIR).fill(6, 5, 3, 8, 5, 3, CARVE_FENCE);
        // The shop: the counter, the chest behind it, goods on the floor and by the walls.
        s.fill(5, 1, 5, 7, 1, 5, Kit.upside(Terem.STAIRS, "north")).set(6, 2, 5, Furnish.candles(2));
        Furnish.chest(s, 6, 1, 3, "south");
        Furnish.crate(s, 7, 1, 3, "spruce");
        Furnish.sack(s, 5, 1, 3, true);
        Furnish.crate(s, 2, 1, 2, "spruce");
        Furnish.sack(s, 2, 2, 2, false);
        Furnish.sack(s, 3, 1, 2, false);
        s.set(2, 1, 4, "minecraft:white_wool").set(2, 2, 4, "minecraft:light_blue_wool").set(2, 1, 5, "minecraft:yellow_wool");
        s.set(5, 1, 7, "minecraft:white_wool").set(7, 1, 7, Kit.CLOTH).set(6, 1, 7, Kit.CLOTH_CARPET);
        s.set(3, 3, 4, Kit.hangingLantern()).set(6, 3, 6, Kit.hangingLantern());
        // The living room under the vault.
        pech(s, 7, 4, 5, "west", 14);
        s.fill(7, 15, 4, 7, 16, 4, Kit.CHIMNEY).set(7, 17, 4, Kit.CHIMNEY_POT);
        Furnish.bed(s, 2, 5, 4, "north", "red");
        Furnish.bed(s, 2, 5, 7, "north", "red");
        Furnish.bed(s, 8, 5, 7, "north", "red");
        Terem.feastTable(s, 3, 5, 5, 5, 5, true);
        Terem.redCorner(s, 2, 5, 2, "north", "west");
        Terem.candleChandelier(s, 4, 5, 14, 6);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the terem house

    /**
     * A terem house (teremok): a log house raised on a low base of logs, its carved gable to the street, from whose ridge
     * rises a little lookout tower (smotrilnya) of logs with a window to each side under a steep tent and a gilt needle.
     * A high porch with a tented roof leads up to the door. Inside: the stove, two beds, the chest, a table and the red
     * corner under a chandelier of candles; a ladder up to the room under the roof with the third bed, and another into
     * the lookout.
     */
    static Structure terem() {
        Structure s = new Structure(9, 23, 11);
        s.fill(1, 0, 1, 7, 0, 6, FOOTING).fill(2, 1, 2, 6, 1, 5, FOOTING).fill(2, 2, 2, 6, 2, 5, FLOOR);
        Terem.logWalls(s, 1, 1, 1, 7, 7, 6);
        s.fill(2, 7, 2, 6, 7, 5, PLANKS);
        Terem.window(s, 1, 4, 4, "west", 1, 2, true);
        Terem.window(s, 7, 4, 3, "east", 1, 2, true);
        Terem.gableRoof(s, 1, 1, 7, 6, 7, false, 1);
        // The lookout: plain corners inside the roof, log ends above it, a window to each side, the tent.
        Terem.logWalls(s, 3, 8, 2, 5, 11, 4, LOG, false);
        Terem.logWalls(s, 3, 12, 2, 5, 15, 4);
        s.fill(4, 8, 3, 4, 15, 3, Structure.AIR).fill(4, 8, 4, 4, 9, 4, Structure.AIR);
        for (int[] w : new int[][]{{4, 2, 0, -1}, {4, 4, 0, 1}, {3, 3, -1, 0}, {5, 3, 1, 0}}) {
            s.fill(w[0], 13, w[1], w[0], 14, w[1], Terem.MICA);
            String out = toward(w[2], w[3]);
            s.set(w[0] + w[2], 12, w[1] + w[3], Kit.upside(CARVE_STAIRS, Furnish.opposite(out)));
            s.set(w[0] + w[2], 15, w[1] + w[3], Kit.slab(CARVE_SLAB));
        }
        Terem.tentRoof(s, 2, 1, 6, 5, 16, 4, 0);
        for (int y = 8; y <= 12; y++) {
            s.set(4, y, 3, Kit.ladder("south"));
        }
        s.set(4, 15, 3, Kit.wallTorch("south"));
        // The window in the front gable and the high porch.
        s.set(5, 9, 6, Terem.MICA).set(5, 8, 7, Kit.upside(CARVE_STAIRS, "north"));
        Terem.porch(s, 3, 3, 6, "south", 0, 1, false);
        // The living room.
        pech(s, 2, 2, 3, "south", 10);
        s.fill(2, 11, 3, 2, 12, 3, Kit.CHIMNEY).set(2, 13, 3, Kit.CHIMNEY_POT);
        for (int y = 3; y <= 7; y++) {
            s.set(6, y, 2, Kit.ladder("south"));
        }
        Furnish.bed(s, 4, 3, 3, "north", "red");
        Furnish.bed(s, 5, 3, 3, "north", "red");
        Terem.chestPlace(s, 2, 3, 5, "east");
        Terem.lavka(s, 2, 4, 2, 4, 3);
        s.set(4, 3, 5, Kit.topSlab(Terem.FLOOR_SLAB)).set(4, 4, 5, Furnish.candles(3));
        Terem.lavka(s, 5, 5, 6, 5, 3);
        Terem.redCorner(s, 6, 3, 5, "south", "east");
        Terem.candleChandelier(s, 4, 4, 6, 1);
        // The room under the roof around the lookout.
        Furnish.bed(s, 2, 8, 4, "south", "red");
        s.set(2, 9, 2, Kit.hangingLantern()).set(3, 10, 5, Kit.hangingLantern());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the boyar's mansion

    /**
     * A boyar's mansion (khoromy): an L of two log blocks with plain dovetailed corners. At the back to the west stands
     * the terem, two storeys high under an eight-sided tent with an onion on it; to the east the lower hall runs to the
     * street with its carved gable. A gallery on posts before the terem joins them, and from it a high porch under a barrel
     * roof goes down into a court fenced with boards, with a carved gate and a wicket under a little roof and a birch by
     * the fence. In the terem the boyar's chamber with the chest and the red corner, and above it the bedchamber; in the
     * hall the stove, a long table with benches and a bed.
     */
    static Structure boyar() {
        Structure s = new Structure(13, 26, 15);
        // The gallery first: the walls of the two blocks close its ends.
        Terem.gallery(s, 1, 6, 6, 8, 3, 0, true);
        s.set(2, 7, 7, Kit.chain()).set(5, 7, 7, Kit.chain());
        // The terem: a high base, two storeys, the tent with its onion.
        s.fill(1, 0, 1, 5, 0, 5, FOOTING).fill(2, 1, 2, 4, 1, 4, FOOTING).fill(2, 2, 2, 4, 2, 4, FLOOR);
        Terem.logWalls(s, 1, 1, 1, 5, 11, 5, LOG, false);
        s.fill(2, 7, 2, 4, 7, 4, FLOOR);
        Terem.window(s, 1, 4, 4, "west", 1, 2, true);
        Terem.window(s, 5, 4, 3, "east", 1, 2, true);
        for (String side : new String[]{"west", "east", "north", "south"}) {
            int[] d = Furnish.step(side);
            Terem.window(s, 3 + d[0] * 2, 9, 3 + d[1] * 2, side, 1, 2, true);
        }
        int top = Terem.tent(s, Terem.EVERYWHERE, 3, 3, 3, 12, Terem.OCTAGON, Terem.OCTAGON, Terem.TENT_RISES, 0, 1);
        Terem.onionDome(s, 3, top, 3, 3, 0, PLANKS);
        s.door(4, 3, 5, Terem.DOOR, "north", "left").set(4, 3, 6, Structure.AIR);
        // The hall: one storey on a high base, its steep gable to the street.
        s.fill(7, 0, 1, 11, 0, 8, FOOTING).fill(8, 1, 2, 10, 1, 7, FOOTING).fill(8, 2, 2, 10, 2, 7, FLOOR);
        Terem.logWalls(s, 7, 1, 1, 11, 7, 8, LOG, false);
        s.fill(8, 7, 2, 10, 7, 7, PLANKS);
        windowPair(s, 9, 4, 8, "south");
        Terem.window(s, 11, 4, 3, "east", 1, 2, true);
        Terem.window(s, 11, 4, 6, "east", 1, 2, true);
        Terem.gableRoof(s, 7, 1, 11, 8, 7, false, 2);
        s.set(9, 8, 8, Terem.MICA).set(9, 7, 9, Kit.upside(CARVE_STAIRS, "north"));
        s.set(8, 8, 9, CARVE_FENCE).set(10, 8, 9, CARVE_FENCE);
        s.door(7, 3, 7, Terem.DOOR, "east", "left");
        // The high porch from the gallery down into the court, under a barrel roof.
        Terem.porch(s, 4, 3, 8, "south", 0, 1, true);
        s.fill(4, 3, 8, 4, 4, 8, Structure.AIR);
        // The court: a fence of boards between carved pillars, the gate and the wicket under a little roof, a birch.
        for (int x = 0; x <= 12; x++) {
            if (x < 2 || x > 7) {
                fenceBay(s, x, 13, x % 3 == 0 || x == 12);
            }
        }
        for (int z = 9; z <= 12; z++) {
            fenceBay(s, 0, z, z == 9);
            fenceBay(s, 12, z, z == 9 || z == 12);
        }
        for (int x : new int[]{2, 5, 7}) {
            s.fill(x, 0, 13, x, 3, 13, Kit.log(POST, "y"));
        }
        s.door(3, 0, 13, Terem.DOOR, "south", "left").door(4, 0, 13, Terem.DOOR, "south", "right");
        s.door(6, 0, 13, Terem.DOOR, "south", "left");
        s.fill(3, 2, 13, 4, 2, 13, Kit.log(POST, "x")).set(6, 2, 13, Kit.log(POST, "x"));
        s.fill(3, 3, 13, 4, 3, 13, CARVE_FENCE).set(6, 3, 13, CARVE_FENCE);
        s.fill(1, 4, 12, 8, 4, 12, Kit.stairs(Terem.ROOF.stairs(), "south"));
        s.fill(1, 4, 14, 8, 4, 14, Kit.stairs(Terem.ROOF.stairs(), "north"));
        s.fill(1, 5, 13, 8, 5, 13, Kit.slab(Terem.ROOF.slab())).fill(2, 4, 13, 7, 4, 13, Terem.ROOF.block());
        s.set(1, 4, 13, CARVE).set(8, 4, 13, CARVE);
        s.set(5, 3, 12, Kit.hangingLantern());
        s.fill(3, 0, 12, 8, 0, 12, Kit.slab(Kit.ROUGH_SLAB)).set(8, 0, 11, Kit.slab(Kit.ROUGH_SLAB));
        birch(s, 11, 0, 12, 5);
        // The terem: the boyar's chamber below, the bedchamber above, a ladder between them.
        for (int y = 3; y <= 7; y++) {
            s.set(2, y, 2, Kit.ladder("east"));
        }
        Furnish.bed(s, 4, 3, 3, "north", "red");
        Terem.chestPlace(s, 3, 3, 2, "south");
        Terem.lavka(s, 2, 3, 2, 4, 3);
        Terem.redCorner(s, 2, 3, 4, "south", "west");
        s.set(3, 6, 3, Kit.hangingLantern());
        Furnish.bed(s, 3, 8, 3, "north", "red");
        Furnish.bed(s, 4, 8, 3, "north", "red");
        s.set(4, 8, 4, Kit.topSlab(Terem.FLOOR_SLAB)).set(4, 9, 4, Furnish.candles(3));
        Terem.candleChandelier(s, 3, 4, 11, 1);
        // The hall: the stove, the bed, the long table with benches, the red corner, light.
        pech(s, 8, 2, 3, "south", 11);
        s.fill(8, 12, 3, 8, 13, 3, Kit.CHIMNEY).set(8, 14, 3, Kit.CHIMNEY_POT);
        Furnish.bed(s, 10, 3, 3, "north", "red");
        Terem.feastTable(s, 9, 4, 9, 6, 3, true);
        Terem.lavka(s, 10, 7, 10, 7, 3);
        Terem.redCorner(s, 10, 3, 7, "south", "east");
        Terem.candleChandelier(s, 9, 5, 6, 1);
        s.set(9, 10, 5, Kit.hangingLantern());
        s.connect();
        return s;
    }

    /**
     * One bay of a fence of boards at (x, z): boards two high under a slab, or a carved pillar of a hewn log.
     */
    private static void fenceBay(Structure s, int x, int z, boolean pillar) {
        if (pillar) {
            s.fill(x, 0, z, x, 2, z, Kit.log(POST, "y")).set(x, 3, z, Kit.slab(CARVE_SLAB));
        } else {
            s.fill(x, 0, z, x, 1, z, PLANKS).set(x, 2, z, Kit.slab(Terem.SLAB));
        }
    }

    // ---------------------------------------------------------------- the smithy

    /**
     * A smithy: a log hall under a carved gable roof whose front stands open between two log ends onto a yard paved with
     * rough stone under a lean-to on posts, where the anvil, the grindstone and the quenching tub stand. Inside the forge of
     * rough stone with its fire under a hood, the blast furnace, the smithing table, the smith's post with his chest, coal
     * and logs for the fire. A tall brick stack stands free against the east gable, a woodpile and a heap of coal beside
     * it.
     */
    static Structure smithy() {
        Structure s = new Structure(13, 15, 11);
        s.fill(1, 0, 1, 9, 0, 5, FOOTING);
        Terem.logWalls(s, 1, 1, 1, 9, 6, 5);
        s.fill(3, 1, 5, 7, 3, 5, Structure.AIR);
        Terem.window(s, 5, 3, 1, "north", 1, 2, true);
        Terem.window(s, 1, 3, 3, "west", 1, 2, true);
        Terem.gableRoof(s, 1, 1, 9, 5, 6, true, 1);
        // The yard under the lean-to on posts.
        s.fill(1, 0, 6, 9, 0, 9, FOOTING);
        for (int x : new int[]{1, 5, 9}) {
            s.fill(x, 1, 9, x, 3, 9, Kit.log(POST, "y"));
        }
        s.fill(1, 4, 9, 9, 4, 9, Kit.log(POST, "x"));
        s.fill(1, 5, 7, 9, 5, 7, Kit.stairs(Terem.ROOF.stairs(), "north"));
        s.fill(1, 5, 8, 9, 5, 9, Kit.slab(Terem.ROOF.slab()));
        s.fill(1, 4, 10, 9, 4, 10, Kit.stairs(Terem.ROOF.stairs(), "north"));
        for (int x : new int[]{1, 9}) {
            s.set(x, 3, 8, Kit.upside(CARVE_STAIRS, "north")).set(x, 4, 8, CARVE);
        }
        s.set(3, 3, 9, Kit.upside(CARVE_STAIRS, "east")).set(7, 3, 9, Kit.upside(CARVE_STAIRS, "west"));
        s.set(4, 1, 7, "minecraft:anvil[facing=west]");
        s.set(6, 1, 7, "minecraft:grindstone[face=floor,facing=south]");
        s.set(8, 1, 8, "minecraft:water_cauldron[level=3]");
        s.set(2, 1, 8, Kit.log("minecraft:stripped_oak_log", "y"));
        s.set(5, 4, 8, Kit.hangingLantern()).set(2, 4, 7, Kit.hangingLantern());
        // The forge: a bed of rough stone, its fire, a hood, the flue through the gable into the stack outside.
        s.fill(7, 1, 2, 8, 1, 3, FOOTING).set(7, 2, 2, FOOTING);
        s.set(8, 2, 2, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        s.fill(7, 2, 3, 8, 2, 3, Kit.slab(Kit.ROUGH_SLAB));
        s.fill(7, 4, 3, 8, 4, 3, Kit.upside(Kit.ROUGH_STAIRS, "north")).fill(7, 4, 2, 8, 5, 2, FOOTING);
        s.fill(7, 5, 3, 8, 5, 3, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.fill(11, 0, 1, 12, 12, 2, Kit.CHIMNEY).fill(10, 3, 2, 10, 5, 2, Kit.CHIMNEY);
        s.set(11, 13, 1, Kit.CHIMNEY_POT).set(12, 13, 2, Kit.CHIMNEY_POT);
        // The woodpile and the coal by the east gable.
        s.fill(11, 0, 4, 11, 2, 7, Kit.log("minecraft:oak_log", "x")).fill(12, 0, 4, 12, 1, 7, Kit.log("minecraft:oak_log", "x"));
        s.fill(11, 3, 4, 11, 3, 7, Kit.slab(Terem.SLAB)).fill(12, 2, 4, 12, 2, 7, Kit.slab(Terem.SLAB));
        s.fill(11, 0, 8, 12, 0, 9, "minecraft:coal_block").set(11, 1, 8, "minecraft:coal_block");
        // Inside: the smith's post with his chest, the tools, coal, logs and sacks.
        s.set(2, 1, 2, "minecraft:smithing_table").set(3, 1, 2, "minecraft:coal_block");
        s.set(4, 1, 2, "kingdomworld:work_post");
        Furnish.chest(s, 5, 1, 2, "south");
        s.set(6, 1, 2, "minecraft:blast_furnace[facing=south,lit=false]");
        s.fill(2, 1, 3, 2, 2, 4, Kit.log("minecraft:oak_log", "z"));
        Furnish.sack(s, 7, 1, 4, false);
        s.set(8, 1, 4, "minecraft:cauldron");
        Furnish.chandelier(s, 5, 3, 8, 2);
        Furnish.chandelier(s, 3, 4, 8, 3);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the tavern

    /**
     * A tavern (kabak) of two log storeys on a plinth of rough stone, its long side to the street under a steep carved roof.
     * A high porch with a tented roof leads up to the taproom; from the corner log end a bracket carries the sign, two
     * banners on a carved board under a spruce bough, the old sign of a kabak. In the taproom the counter with the barrels
     * and the innkeeper's post and chest behind it, a great whitewashed stove with its bench, two long tables with benches
     * under chandeliers of candles, and a stair up along the wall to two rooms for guests with two beds each.
     */
    static Structure tavern() {
        Structure s = new Structure(13, 23, 15);
        s.fill(1, 0, 1, 11, 1, 9, FOOTING);
        Terem.logWalls(s, 1, 2, 1, 11, 12, 9);
        s.fill(2, 2, 2, 10, 2, 8, FLOOR).fill(2, 7, 2, 10, 7, 8, FLOOR).fill(2, 12, 2, 10, 12, 8, PLANKS);
        // Windows of both storeys, then the roof.
        Terem.window(s, 3, 4, 9, "south", 1, 2, true);
        Terem.window(s, 4, 9, 9, "south", 1, 2, true);
        Terem.window(s, 9, 9, 9, "south", 1, 2, true);
        Terem.window(s, 6, 4, 1, "north", 1, 2, true);
        Terem.window(s, 4, 9, 1, "north", 1, 2, true);
        Terem.window(s, 9, 9, 1, "north", 1, 2, true);
        Terem.window(s, 11, 4, 6, "east", 1, 2, true);
        Terem.window(s, 11, 9, 6, "east", 1, 2, true);
        Terem.window(s, 1, 9, 6, "west", 1, 2, true);
        int ridge = Terem.gableRoof(s, 1, 1, 11, 9, 12, true, 2);
        konyok(s, 0, ridge, 5, "west");
        konyok(s, 12, ridge, 5, "east");
        // Little windows (slukhi) in both slopes, each under a carved hood.
        for (int x : new int[]{3, 9}) {
            s.set(x, 16, 8, Terem.MICA).set(x, 17, 8, Kit.slab(CARVE_SLAB));
            s.set(x, 16, 2, Terem.MICA).set(x, 17, 2, Kit.slab(CARVE_SLAB));
        }
        Terem.porch(s, 6, 3, 9, "south", 0, 1, false);
        // The sign: a bracket out of the corner log end, a carved board with two banners, a spruce bough.
        s.fill(11, 10, 10, 11, 10, 11, Kit.log(POST, "z")).set(11, 9, 11, CARVE);
        s.set(10, 9, 11, Kit.wallBanner("red", "west")).set(12, 9, 11, Kit.wallBanner("red", "east"));
        s.set(11, 10, 12, Kit.leaves("spruce"));
        // The stair up along the west wall on a closed base.
        for (int i = 0; i < 5; i++) {
            int z = 7 - i;
            s.set(2, 3 + i, z, Kit.stairs(Terem.FLOOR_STAIRS, "north"));
            if (i > 0) {
                s.fill(2, 3, z, 2, 2 + i, z, PLANKS);
            }
        }
        s.fill(2, 7, 4, 2, 7, 7, Structure.AIR);
        // The counter, the barrels, the innkeeper's post and chest, a shelf with candles.
        s.set(2, 3, 2, Kit.barrel()).set(3, 3, 2, Kit.barrel()).set(3, 3, 3, Kit.barrel());
        s.set(4, 3, 2, "kingdomworld:work_post");
        Furnish.chest(s, 5, 3, 2, "south");
        s.fill(3, 3, 4, 4, 3, 4, Kit.upside(Terem.STAIRS, "south")).set(4, 4, 4, Furnish.candles(2));
        s.fill(3, 5, 2, 5, 5, 2, Kit.topSlab(Terem.FLOOR_SLAB)).set(3, 6, 2, "minecraft:flower_pot").set(5, 6, 2, Furnish.candles(3));
        s.set(4, 6, 3, Kit.hangingLantern());
        // The stove, the tables with their benches, the chandeliers.
        Terem.stove(s, 8, 3, 3, "west", 17);
        s.fill(8, 18, 3, 8, 19, 3, Kit.CHIMNEY).set(8, 20, 3, Kit.CHIMNEY_POT);
        Terem.feastTable(s, 4, 6, 4, 8, 3, true);
        Terem.feastTable(s, 8, 6, 8, 8, 3, true);
        Terem.candleChandelier(s, 4, 7, 6, 1);
        Terem.candleChandelier(s, 8, 7, 6, 1);
        Terem.redCorner(s, 10, 3, 8, "south", "east");
        // The upper storey: a corridor along the back, two rooms for guests with two beds each.
        s.fill(3, 8, 3, 10, 11, 3, PLANKS).set(8, 8, 3, WHITEWASH).fill(8, 8, 3, 8, 11, 3, WHITEWASH);
        s.fill(3, 8, 4, 3, 11, 8, PLANKS).fill(7, 8, 4, 7, 11, 8, PLANKS);
        s.door(5, 8, 3, Terem.DOOR, "south", "left").door(9, 8, 3, Terem.DOOR, "south", "left");
        for (int x : new int[]{4, 8}) {
            Furnish.bed(s, x, 8, 7, "south", "red");
            Furnish.bed(s, x + 2, 8, 7, "south", "red");
            s.set(x + 1, 8, 8, Kit.topSlab(Terem.FLOOR_SLAB)).set(x + 1, 9, 8, Furnish.candles(1));
            s.set(x + 1, 11, 6, Kit.hangingLantern());
        }
        s.set(6, 11, 2, Kit.hangingLantern()).set(2, 11, 6, Kit.hangingLantern());
        s.set(4, 17, 5, Kit.hangingLantern()).set(9, 17, 5, Kit.hangingLantern());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the town wall and its gate

    /**
     * A piece of the town wall with its gate: palisades of upright sharpened logs on a plinth of rough stone, each with a
     * roofed fighting walk behind it and a stair up to it from the town, meeting a log gate tower. Through the tower runs a
     * passage three blocks wide under carved arches, closed on the field side by a double gate of planks with a red banner
     * over it; over the passage the gate room opens onto both walks, and a ladder leads up to the fighting storey that juts
     * out on brackets with loopholes all round, under a tall tent with a watch lantern on its top.
     */
    static Structure wallGate() {
        Structure s = new Structure(23, 33, 11);
        // The palisades and the stairs up to their walks.
        s.fill(0, 0, 3, 6, 0, 6, FOOTING).fill(16, 0, 3, 22, 0, 6, FOOTING);
        Terem.palisade(s, 0, 5, 6, 5, 1, 9, "north");
        Terem.palisade(s, 16, 5, 22, 5, 1, 9, "north");
        flight(s, 0, 0, 2, 1, 0, 6, Terem.FLOOR_STAIRS);
        flight(s, 22, 0, 2, -1, 0, 6, Terem.FLOOR_STAIRS);
        s.set(5, 7, 2, Structure.AIR).set(17, 7, 2, Structure.AIR);
        // Where a palisade meets the tower.
        for (int x : new int[]{7, 15}) {
            s.fill(x, 0, 3, x, 0, 6, FOOTING).fill(x, 1, 5, x, 9, 5, Kit.log(LOG, "y")).set(x, 10, 5, Kit.stairs(Terem.STAIRS, "east"));
            s.fill(x, 5, 3, x, 5, 4, PLANKS);
            s.set(x, 9, 4, Kit.stairs(Terem.ROOF.stairs(), "south")).set(x, 8, 3, Kit.stairs(Terem.ROOF.stairs(), "south"));
            s.set(x, 7, 2, Kit.stairs(Terem.ROOF.stairs(), "south"));
        }
        // The gate tower: the frame, the passage between walls of logs, the gate room over it.
        s.fill(7, 0, 1, 15, 0, 9, FOOTING);
        Terem.logWalls(s, 8, 1, 2, 14, 9, 8);
        s.fill(10, 1, 2, 12, 4, 8, Structure.AIR);
        s.fill(9, 1, 3, 9, 4, 7, Kit.log(LOG, "z")).fill(13, 1, 3, 13, 4, 7, Kit.log(LOG, "z"));
        s.fill(9, 5, 3, 13, 5, 7, FLOOR);
        for (int z : new int[]{2, 8}) {
            s.set(10, 4, z, Kit.upside(CARVE_STAIRS, "west")).set(12, 4, z, Kit.upside(CARVE_STAIRS, "east"));
            s.set(11, 4, z, Kit.topSlab(CARVE_SLAB));
        }
        s.set(10, 1, 8, Terem.DOOR + "[facing=north,half=lower,hinge=left,open=true,powered=false]");
        s.set(10, 2, 8, Terem.DOOR + "[facing=north,half=upper,hinge=left,open=true,powered=false]");
        s.set(12, 1, 8, Terem.DOOR + "[facing=north,half=lower,hinge=right,open=true,powered=false]");
        s.set(12, 2, 8, Terem.DOOR + "[facing=north,half=upper,hinge=right,open=true,powered=false]");
        s.set(11, 6, 9, Kit.wallBanner("red", "south"));
        s.fill(8, 6, 4, 8, 7, 4, Structure.AIR).fill(14, 6, 4, 14, 7, 4, Structure.AIR);
        s.set(11, 4, 5, Kit.hangingLantern()).set(11, 9, 5, Kit.hangingLantern());
        for (int y = 6; y <= 10; y++) {
            s.set(9, y, 7, Kit.ladder("east"));
        }
        // The fighting storey on its brackets, its loopholes, the tent and the watch lantern.
        for (int x = 7; x <= 15; x++) {
            for (int z = 1; z <= 9; z++) {
                int dx = x - 11;
                int dz = z - 5;
                if (Math.abs(dx) == 4 || Math.abs(dz) == 4) {
                    String facing = Math.abs(dz) == 4 ? (dz > 0 ? "north" : "south") : (dx > 0 ? "west" : "east");
                    s.set(x, 10, z, Kit.upside(Terem.STAIRS, facing));
                }
            }
        }
        s.fill(8, 10, 2, 14, 10, 8, FLOOR).set(9, 10, 7, Kit.ladder("east"));
        Terem.logWalls(s, 7, 11, 1, 15, 13, 9, LOG, false);
        for (int i : new int[]{-2, 0, 2}) {
            s.set(11 + i, 12, 1, Structure.AIR).set(11 + i, 12, 9, Structure.AIR);
            s.set(7, 12, 5 + i, Structure.AIR).set(15, 12, 5 + i, Structure.AIR);
        }
        s.set(10, 12, 10, Kit.wallBanner("red", "south")).set(12, 12, 10, Kit.wallBanner("red", "south"));
        int top = Terem.tent(s, Terem.EVERYWHERE, 11, 5, 5, 14, Terem.SQUARE, Terem.SQUARE, Terem.TENT_RISES, 0, 1);
        Terem.watch(s, 11, top, 5);
        s.set(11, 13, 5, Kit.hangingLantern()).set(8, 13, 2, Kit.hangingLantern());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the hut

    /**
     * A hut of the poor, low and half sunken: two courses of logs under a roof of turf on boards that comes down nearly to
     * the ground, a log gable to the street with the door and one tiny window with a shutter, a stack of rough stone through
     * the turf, logs piled under the overhang and a chopping block. Inside, on a floor of trodden earth: a bed, a stove of
     * rough stone with its fire, the chest, a table with a candle and a lantern under the boards.
     */
    static Structure hut() {
        Structure s = new Structure(5, 8, 7);
        int[] ceiling = {2, 2, 3, 2, 2};
        // The floor of trodden earth and the low side walls.
        s.fill(1, 0, 1, 3, 0, 4, EARTH_FLOOR);
        for (int x : new int[]{0, 4}) {
            s.fill(x, 0, 0, x, 0, 5, "minecraft:rooted_dirt");
        }
        // The roof of turf on boards from the back to the overhang over the door, coming down to the earth at the sides,
        // grass on its ridge.
        for (int z = 0; z <= 6; z++) {
            for (int x = 0; x <= 4; x++) {
                if (x == 0 || x == 4) {
                    s.fill(x, 1, z, x, 2, z, MOSS);
                } else {
                    s.set(x, ceiling[x], z, PLANKS);
                    s.set(x, ceiling[x] + 1, z, x == 2 ? "minecraft:grass_block[snowy=false]" : MOSS);
                }
            }
        }
        s.set(2, 3, 6, PLANKS).set(1, 2, 6, PLANKS).set(3, 2, 6, PLANKS);
        // The gables of lying logs.
        for (int z : new int[]{0, 5}) {
            for (int x = 1; x <= 3; x++) {
                for (int y = 0; y < ceiling[x]; y++) {
                    s.set(x, y, z, Kit.log(LOG, "x"));
                }
            }
        }
        // The door, the tiny window with its shutter.
        s.door(2, 0, 5, Terem.DOOR, "north", "left");
        s.set(3, 1, 5, Terem.MICA).set(4, 1, 6, Kit.shutter(Kit.SHUTTER, "south"));
        // Tufts on the turf, the stack of rough stone.
        s.set(1, 4, 2, "minecraft:fern").set(3, 4, 4, MOSS_CARPET).set(0, 3, 4, "minecraft:fern").set(4, 3, 1, MOSS_CARPET);
        s.set(2, 5, 3, "minecraft:grass").set(2, 5, 5, "minecraft:fern").set(0, 3, 1, "minecraft:grass");
        s.fill(3, 0, 1, 3, 0, 2, FOOTING).set(3, 1, 1, "minecraft:campfire[facing=west,lit=true,signal_fire=false,waterlogged=false]");
        s.set(3, 1, 2, FOOTING).fill(3, 2, 1, 3, 4, 1, FOOTING).set(3, 5, 1, Kit.ROUGH_FENCE);
        // The woodpile and the chopping block under the overhang, a lantern over them.
        s.fill(0, 0, 6, 1, 0, 6, Kit.log("minecraft:oak_log", "z")).set(0, 1, 6, Kit.log("minecraft:oak_log", "z"));
        s.set(4, 0, 6, Kit.log(POST, "y"));
        s.set(1, 1, 6, Kit.hangingLantern());
        // Inside.
        Furnish.bed(s, 1, 0, 2, "north", "brown");
        Furnish.chest(s, 3, 0, 3, "west");
        Furnish.table(s, 3, 0, 4, "spruce");
        s.set(3, 1, 4, Furnish.candles(1));
        s.set(1, 0, 4, Kit.stairs("minecraft:spruce_stairs", "north"));
        s.set(2, 2, 3, Kit.hangingLantern());
        s.connect();
        return s;
    }
}
