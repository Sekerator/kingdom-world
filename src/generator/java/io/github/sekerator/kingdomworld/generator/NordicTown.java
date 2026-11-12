package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The town of the style "nordic" (see {@link Nordic}): its houses, from the hut of the poor under a roof of turf to the
 * homestead of a chieftain behind its fence, its smithy, its tavern and a piece of its palisade with the gate tower.
 *
 * <p>All of them are dark timber on footings of rough stone under very steep roofs of shingles or under turf, their gables
 * edged with crossed boards and on the rich houses carved dragon heads looking out from the ends of their ridges. So that a
 * street of them is no brown mush, they differ in what they are clad in: the green turf of the poor, the dry stone of the
 * boat shed and the smithy, the painted boards of the houses in town (red, ochre or white in a look) with white corners and
 * window frames, the stone of the merchant's shop, and in their outlines: the upturned hull of the longhouse coming down low
 * over its gallery, the jettied storey of the merchant, the cross gable of the painted house, the stacked roofs and the
 * belfry of the chieftain.
 *
 * <p>Coordinates as everywhere: x from west to east, z from the back (north) to the front (south, the street), y up; layer 0
 * stands on the ground and holds footings and floors.
 */
final class NordicTown {
    private static final String AIR = Structure.AIR;
    private static final String LOG = Nordic.LOG;
    private static final String POST = Kit.log(Nordic.LOG, "y");
    private static final String STAVE = Kit.log(Nordic.STAVE, "y");
    private static final String PLANKS = Nordic.PLANKS;
    private static final String STAIRS = Nordic.STAIRS;
    private static final String SLAB = Nordic.SLAB;
    private static final String FENCE = Nordic.FENCE;
    private static final String DOOR = Nordic.DOOR;
    private static final String FLOOR = Nordic.FLOOR;
    private static final String FLOOR_SLAB = Nordic.FLOOR_SLAB;
    private static final String FLOOR_STAIRS = Nordic.FLOOR_STAIRS;
    private static final String FOOTING = Nordic.FOOTING;
    private static final String FOOTING_STAIRS = Nordic.FOOTING_STAIRS;
    private static final String FOOTING_SLAB = Nordic.FOOTING_SLAB;
    private static final String PAINT = Nordic.PAINT;
    private static final String WORK_POST = "kingdomworld:work_post";
    private static final String WATER = "minecraft:water_cauldron[level=3]";
    /** Firewood: logs of the timber, the wood of the place. */
    private static final String FIREWOOD = "minecraft:oak_log";
    /** A lying bundle of stockfish on its rack. */
    private static final String STOCKFISH = "minecraft:dried_kelp_block";
    private static final String GATE_OPEN = "minecraft:spruce_fence_gate[facing=south,in_wall=false,open=true,powered=false]";

    private NordicTown() {
    }

    // ---------------------------------------------------------------- the plans of the houses

    /**
     * The houses the house program draws, from the poorest to the noblest: their names, their beds and their icons.
     */
    enum Plan {
        HUT("hut", 1, "minecraft:moss_block"),
        FISHER("fisher", 2, "minecraft:fishing_rod"),
        RED("red", 3, "minecraft:red_terracotta"),
        LONGHOUSE("longhouse", 2, "minecraft:spruce_log"),
        MERCHANT("merchant", 3, "minecraft:emerald"),
        CHIEFTAIN("chieftain", 4, "minecraft:shield");

        final String key;
        final int beds;
        final String icon;

        Plan(String key, int beds, String icon) {
            this.key = key;
            this.beds = beds;
            this.icon = icon;
        }
    }

    /**
     * The town buildings of the style: the six houses of the house program, the smithy, the tavern and the piece of the town
     * wall with its gate tower.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Plan plan : Plan.values()) {
            Structure s = house(plan);
            list.add(new BuildingGenerator.Building("nordic_house_" + plan.key, "housing", "house", plan.icon, (s.sizeX - 1) / 2, 0,
                    s.sizeZ - 1, s, "house", 1, ""));
        }
        Structure smithy = smithy();
        list.add(new BuildingGenerator.Building("nordic_smithy", "production", "workshop", "minecraft:anvil", (smithy.sizeX - 1) / 2, 0,
                smithy.sizeZ - 1, smithy, "workshop", 1, "").at("door", 5, 1, 9));
        Structure tavern = tavern();
        list.add(new BuildingGenerator.Building("nordic_tavern", "trade", "tavern", "minecraft:barrel", (tavern.sizeX - 1) / 2, 0,
                tavern.sizeZ - 1, tavern, "tavern", 1, "").at("door", 5, 1, 17));
        Structure gate = wallGate();
        list.add(new BuildingGenerator.Building("nordic_wall_gate", "defense", "gatehouse", "minecraft:spruce_log", (gate.sizeX - 1) / 2, 0,
                gate.sizeZ - 1, gate, "tower", 1, "")
                .at("door", 11, 0, 10)
                .at("castle_gate", 10, 1, 4).at("castle_gate", 12, 1, 4)
                .at("castle_wall", 3, 5, 4).at("castle_wall", 19, 5, 4));
        return list;
    }

    /**
     * The house program: draws the house of a plan.
     */
    static Structure house(Plan plan) {
        return switch (plan) {
            case HUT -> hut();
            case FISHER -> fisher();
            case RED -> redHouse();
            case LONGHOUSE -> longhouse();
            case MERCHANT -> merchant();
            case CHIEFTAIN -> chieftain();
        };
    }

    // ---------------------------------------------------------------- small shapes

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static boolean solid(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && s.solid(x, y, z);
    }

    /** Fills a box with a block, leaving out what falls outside the structure. */
    private static void fill(Structure s, int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    put(s, x, y, z, state);
                }
            }
        }
    }

    private static String campfire(String facing) {
        return "minecraft:campfire[facing=" + facing + ",lit=true,signal_fire=false,waterlogged=false]";
    }

    /** The lowest block above layer {@code y} in the column (x, z): the ceiling a lantern hangs from. */
    private static int ceiling(Structure s, int x, int y, int z) {
        int yy = y + 1;
        while (yy < s.sizeY && !solid(s, x, yy, z)) {
            yy++;
        }
        return yy;
    }

    /** A lantern hanging at (x, y, z) on a chain from the ceiling over it. */
    private static void hang(Structure s, int x, int y, int z) {
        int top = ceiling(s, x, y, z);
        if (top >= s.sizeY) {
            return;
        }
        for (int yy = y + 1; yy < top; yy++) {
            put(s, x, yy, z, Kit.chain());
        }
        put(s, x, y, z, Kit.hangingLantern());
    }

    /**
     * Barge boards on the end row {@code a} of a turf roof drawn from the field {@code f} (its eaves at layer {@code eave}):
     * boards of the timber follow both slopes in place of the turf and cross over the ridge as two horns.
     */
    private static void turfBoards(Structure s, Nordic.Field f, boolean alongX, int a, int c1, int c2, int eave) {
        int ridge = Integer.MIN_VALUE;
        int lo = c1;
        int hi = c1;
        for (int c = c1; c <= c2; c++) {
            int h = alongX ? f.at(a, c) : f.at(c, a);
            if (h > ridge) {
                ridge = h;
                lo = c;
            }
            if (h == ridge) {
                hi = c;
            }
        }
        String minus = alongX ? "north" : "west";
        String plus = alongX ? "south" : "east";
        for (int c = c1; c <= c2; c++) {
            int h = alongX ? f.at(a, c) : f.at(c, a);
            if (h == Nordic.NONE || h <= eave) {
                continue;
            }
            int x = alongX ? a : c;
            int z = alongX ? c : a;
            put(s, x, h, z, h == ridge ? PLANKS : Kit.stairs(STAIRS, c < lo ? plus : minus));
            put(s, x, h + 1, z, AIR);
        }
        put(s, alongX ? a : lo - 1, ridge, alongX ? lo - 1 : a, Kit.stairs(STAIRS, minus));
        put(s, alongX ? a : hi + 1, ridge, alongX ? hi + 1 : a, Kit.stairs(STAIRS, plus));
    }

    /**
     * Plain barge boards on the end row {@code a} of a roof drawn with {@code roof}: the top stair of every column from
     * {@code c1} to {@code c2} is drawn of {@code board} instead, so that a thin board follows each slope up to the ridge
     * without crossing over it. Call it after the roof is drawn.
     */
    private static void bargeBoards(Structure s, boolean alongX, int a, int c1, int c2, Kit.Roof roof, Kit.Roof board) {
        for (int c = Math.min(c1, c2); c <= Math.max(c1, c2); c++) {
            int x = alongX ? a : c;
            int z = alongX ? c : a;
            for (int y = s.sizeY - 1; y >= 0; y--) {
                String state = s.inside(x, y, z) ? s.get(x, y, z) : AIR;
                if (state.startsWith(roof.stairs() + "[")) {
                    put(s, x, y, z, board.stairs() + state.substring(roof.stairs().length()));
                    break;
                }
                if (state.equals(roof.block())) {
                    put(s, x, y, z, board.block());
                    break;
                }
            }
        }
    }

    /**
     * A box bed: a real bed (a home for one) whose foot is at (x, y, z) and whose head lies the way of {@code facing}, a board
     * at its head and a canopy of slabs over it, both only where nothing stands yet, so that it may stand against a wall or
     * under a roof; with {@code posts} two posts at the corners behind its foot where there is room.
     */
    private static void boxBed(Structure s, int x, int y, int z, String facing, String color, boolean posts) {
        int[] f = Furnish.step(facing);
        int[] al = Furnish.step(Furnish.left(facing));
        Furnish.bed(s, x, y, z, facing, color);
        int bx = x + 2 * f[0];
        int bz = z + 2 * f[1];
        int to = 1;
        if (s.inside(bx, y, bz) && !solid(s, bx, y, bz)) {
            put(s, bx, y, bz, PLANKS);
            if (!solid(s, bx, y + 1, bz)) {
                put(s, bx, y + 1, bz, PLANKS);
            }
            to = 2;
        }
        int from = 0;
        if (posts) {
            for (int side = -1; side <= 1; side += 2) {
                int sx = x + al[0] * side;
                int sz = z + al[1] * side;
                int px = sx - f[0];
                int pz = sz - f[1];
                if (s.inside(sx, y, sz) && !solid(s, sx, y, sz) && s.inside(px, y, pz) && !solid(s, px, y, pz)) {
                    put(s, px, y, pz, FENCE);
                    put(s, px, y + 1, pz, FENCE);
                    from = -1;
                }
            }
        }
        for (int i = from; i <= to; i++) {
            int cx = x + f[0] * i;
            int cz = z + f[1] * i;
            if (s.inside(cx, y + 2, cz) && !solid(s, cx, y + 2, cz)) {
                put(s, cx, y + 2, cz, Kit.topSlab(SLAB));
            }
        }
    }

    /**
     * A flight of {@code n} steps from (x, y, z) rising one layer for every block the way of (dx, dz), each step carried by
     * a stair hanging upside down under it like a sawn stringer.
     */
    private static void flight(Structure s, int x, int y, int z, int dx, int dz, int n, String stairs) {
        String up = dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
        for (int i = 0; i < n; i++) {
            int px = x + dx * i;
            int pz = z + dz * i;
            put(s, px, y + i, pz, Kit.stairs(stairs, up));
            if (i > 0) {
                put(s, px, y + i - 1, pz, Kit.upside(stairs, Furnish.opposite(up)));
            }
        }
    }

    /**
     * A small window of glass at (x, y, z) in a wall under low eaves, looking {@code facing}: board shutters standing open
     * at its sides and a sill board under it, no hood (the eaves are its hood).
     */
    private static void slit(Structure s, int x, int y, int z, String facing) {
        int[] out = Furnish.step(facing);
        int[] al = Furnish.step(Furnish.left(facing));
        put(s, x, y, z, Kit.GLASS);
        int fx = x + out[0];
        int fz = z + out[1];
        put(s, fx + al[0], y, fz + al[1], Kit.shutter(Nordic.TRAPDOOR, facing));
        put(s, fx - al[0], y, fz - al[1], Kit.shutter(Nordic.TRAPDOOR, facing));
        for (int u = -1; u <= 1; u++) {
            put(s, fx + al[0] * u, y - 1, fz + al[1] * u, Kit.topSlab(SLAB));
        }
    }

    /**
     * A window of a painted house looking {@code facing}: panes of glass at (x, y, z) and {@code height - 1} layers above, a
     * white lintel board over them in the wall, a white sill jutting out under them and board shutters standing open at
     * both sides, so that the paint of the wall shows round it.
     */
    private static void paintedWindow(Structure s, int x, int y, int z, String facing, int height) {
        int[] out = Furnish.step(facing);
        int[] al = Furnish.step(Furnish.left(facing));
        for (int v = 0; v < height; v++) {
            put(s, x, y + v, z, Kit.GLASS);
            for (int side = -1; side <= 1; side += 2) {
                put(s, x + out[0] + al[0] * side, y + v, z + out[1] + al[1] * side, Kit.shutter(Kit.SHUTTER, facing));
            }
        }
        put(s, x, y + height, z, Nordic.WHITE.block());
        put(s, x + out[0], y - 1, z + out[1], Kit.upside(Nordic.WHITE.stairs(), Furnish.opposite(facing)));
    }

    /**
     * A pentice: one row of shingles at layer {@code y} all round walls on the lines {@code x1..x2, z1..z2}, leaning on them
     * like a lower tier of roof, its corners turned; on the front (the line z2 + 1) it leaves out the columns
     * {@code skip1..skip2}.
     */
    private static void pentice(Structure s, int x1, int z1, int x2, int z2, int y, int skip1, int skip2) {
        Nordic.Field f = (x, z) -> {
            if (x >= x1 && x <= x2 && z >= z1 && z <= z2) {
                return y + 100;
            }
            return x >= x1 - 1 && x <= x2 + 1 && z >= z1 - 1 && z <= z2 + 1 ? y : Nordic.NONE;
        };
        Nordic.Area ring = (x, z) -> f.at(x, z) == y && !(z == z2 + 1 && x >= skip1 && x <= skip2);
        Nordic.roof(s, f, ring, x1 - 1, z1 - 1, x2 + 1, z2 + 1, Nordic.ROOF);
    }

    /** Rises of the slender needle of a belfry: a low skirt, then a tall point. */
    private static final int[] SLENDER = {2, 4};

    /**
     * A belfry on a ridge around (cx, cz): a body of staves three wide from layer {@code y} (inside the roof) up to
     * {@code top}, open louvres on its four sides under the top, a bell (or a watch lantern) hanging in it and a slender
     * needle of the spires with a vane over it.
     *
     * @return the layer above the vane
     */
    private static int belfry(Structure s, int cx, int y, int cz, int top, boolean bell) {
        Nordic.staveWalls(s, cx - 1, y, cz - 1, cx + 1, top, cz + 1);
        for (int[] d : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            put(s, cx + d[0], top - 1, cz + d[1], FENCE);
            put(s, cx + d[0], top - 2, cz + d[1], FENCE);
        }
        put(s, cx, top, cz, PLANKS);
        put(s, cx, top - 3, cz, PLANKS);
        put(s, cx, top - 1, cz, bell ? "minecraft:bell[attachment=ceiling,facing=north,powered=false]" : Kit.hangingLantern());
        if (!bell) {
            put(s, cx, top - 2, cz, Kit.lantern());
        }
        return Nordic.needle(s, cx, top, cz, 5, SLENDER, Nordic.SPIRE);
    }

    /** A pile of firewood: logs of the timber lying along x or z. */
    private static void woodpile(Structure s, int x1, int z1, int x2, int z2, int y1, int y2, String axis) {
        fill(s, x1, y1, z1, x2, y2, z2, Kit.log(FIREWOOD, axis));
    }

    // ---------------------------------------------------------------- the hut

    /**
     * The hut of the poor: one low room of staves on a footing of rough stone under a gentle roof of turf whose gable looks
     * at the street, edged with barge boards crossing over the ridge; a little window in the gable over the door, another in
     * the side wall in a carved frame, a short stack of rough stone through the turf, firewood piled under the eaves, a
     * chopping block and a lantern by the door. Inside: a hearth in the corner, a box bed, the chest, a table with a candle and
     * a stool, a sack of flour and a lantern under the turf.
     */
    static Structure hut() {
        Structure s = new Structure(7, 9, 9);
        Nordic.footing(s, 1, 1, 5, 7, 0);
        Nordic.staveWalls(s, 1, 1, 1, 5, 3, 7);
        Nordic.window(s, 1, 2, 4, "west", 1, false);
        s.door(3, 1, 7, DOOR, "north", "left");
        Nordic.turfRoof(s, 1, 1, 5, 7, 3, false, STAVE);
        Nordic.Field f = Nordic.gableField(1, 1, 5, 7, 3, false, Nordic.PITCH);
        turfBoards(s, f, false, 0, 0, 6, 3);
        turfBoards(s, f, false, 8, 0, 6, 3);
        put(s, 3, 4, 7, Kit.GLASS);
        // The hearth in the back corner and its stack through the turf.
        Furnish.hearth(s, 2, 1, 2, "south");
        fill(s, 2, 3, 2, 2, 6, 2, FOOTING);
        put(s, 2, 7, 2, Kit.ROUGH_FENCE);
        // The box bed, the table with its stool, the chest, a sack and a crate.
        boxBed(s, 4, 1, 3, "south", "brown", true);
        Furnish.crate(s, 4, 1, 2, "spruce");
        put(s, 2, 1, 4, Kit.topSlab(SLAB));
        put(s, 2, 2, 4, Furnish.candles(1));
        Furnish.chair(s, 2, 1, 5, "north", "spruce");
        Furnish.chest(s, 2, 1, 6, "east");
        Furnish.sack(s, 4, 1, 6, true);
        hang(s, 3, 4, 4);
        // Outside: the step, the firewood under the east eaves, the chopping block, the lantern.
        put(s, 3, 0, 8, Kit.slab(FOOTING_SLAB));
        woodpile(s, 6, 2, 6, 6, 0, 1, "z");
        fill(s, 6, 2, 3, 6, 2, 5, Kit.slab(SLAB));
        put(s, 5, 0, 8, Kit.log("minecraft:stripped_spruce_log", "y"));
        put(s, 2, 3, 8, Kit.hangingLantern());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the fisherman's house

    /**
     * A fisherman's house: a narrow cottage of staves with its steep gable to the street, crossed boards on both gables, a
     * door under a little hood and a stone stack beside the ridge; beside it, tucked under its eaves, the boat shed (naust):
     * low walls of dry stone, a roof of turf and a gable of boards open to the water, where the boat lies on its rollers, a
     * hull of planks with a white strake, its stems curling up, the mast and the folded sail laid in it. Before the house a
     * rack of stockfish. Inside the cottage the hearth, a table, the chest, oars in the corner and a ladder up to the loft
     * under the roof with two beds.
     */
    static Structure fisher() {
        Structure s = new Structure(13, 13, 15);
        // The cottage.
        Nordic.footing(s, 1, 3, 5, 11, 0);
        Nordic.staveWalls(s, 1, 1, 3, 5, 5, 11);
        fill(s, 2, 5, 4, 4, 5, 10, PLANKS);
        Nordic.window(s, 1, 2, 6, "west", 1, false);
        Nordic.window(s, 1, 2, 9, "west", 1, false);
        Nordic.window(s, 3, 7, 11, "south", 1, false);
        Nordic.window(s, 3, 7, 3, "north", 1, false);
        s.door(3, 1, 11, DOOR, "north", "left");
        Nordic.gableRoof(s, 1, 3, 5, 11, 5, false, Nordic.STEEP, Nordic.ROOF, STAVE, Nordic.BOARD, null);
        put(s, 2, 3, 12, Kit.stairs(STAIRS, "east"));
        put(s, 4, 3, 12, Kit.stairs(STAIRS, "west"));
        put(s, 3, 3, 12, PLANKS);
        put(s, 3, 4, 12, Kit.slab(SLAB));
        put(s, 2, 2, 12, Kit.hangingLantern());
        put(s, 3, 0, 12, Kit.slab(FOOTING_SLAB));
        // The boat shed: dry stone, a gable of boards open to the water, turf.
        s.fill(7, 0, 2, 11, 0, 13, FOOTING);
        fill(s, 8, 0, 3, 10, 0, 12, "minecraft:gravel");
        fill(s, 7, 1, 2, 7, 2, 12, FOOTING);
        fill(s, 11, 1, 2, 11, 2, 12, FOOTING);
        fill(s, 8, 1, 2, 10, 2, 2, FOOTING);
        fill(s, 7, 3, 2, 11, 3, 2, Kit.log(LOG, "x"));
        fill(s, 7, 1, 13, 7, 3, 13, POST);
        fill(s, 11, 1, 13, 11, 3, 13, POST);
        fill(s, 8, 3, 13, 10, 3, 13, Kit.log(LOG, "x"));
        Nordic.turfRoof(s, 7, 2, 11, 13, 3, false, STAVE);
        Nordic.Field turf = Nordic.gableField(7, 2, 11, 13, 3, false, Nordic.PITCH);
        turfBoards(s, turf, false, 1, 6, 12, 3);
        turfBoards(s, turf, false, 14, 6, 12, 3);
        put(s, 9, 4, 13, Kit.GLASS);
        boat(s, 9, 1, 4, 12);
        fill(s, 8, 0, 14, 10, 0, 14, Kit.log(LOG, "x"));
        fill(s, 8, 0, 7, 10, 0, 7, Kit.log(LOG, "x"));
        hang(s, 9, 4, 6);
        hang(s, 9, 4, 10);
        fill(s, 10, 1, 3, 10, 2, 3, FENCE);
        Furnish.crate(s, 8, 1, 3, "spruce");
        // The rack of stockfish before the cottage.
        fill(s, 0, 0, 14, 0, 2, 14, FENCE);
        fill(s, 4, 0, 14, 4, 2, 14, FENCE);
        fill(s, 0, 3, 14, 4, 3, 14, Kit.log(LOG, "x"));
        for (int x = 1; x <= 3; x++) {
            put(s, x, 2, 14, STOCKFISH);
        }
        // The hearth and its stack, the cauldron, the ladder up.
        Furnish.hearth(s, 2, 1, 4, "south");
        fill(s, 2, 3, 4, 2, 8, 4, FOOTING);
        Nordic.chimney(s, 2, 4, 9, 10);
        put(s, 4, 1, 4, WATER);
        fill(s, 3, 1, 4, 3, 5, 4, Kit.ladder("south"));
        // The table with its chairs, the chest, a sack, a crate, the oars in the corner.
        put(s, 3, 1, 7, Kit.topSlab(FLOOR_SLAB));
        put(s, 3, 2, 7, Furnish.candles(2));
        Furnish.chair(s, 2, 1, 7, "east", "oak");
        Furnish.chair(s, 4, 1, 7, "west", "oak");
        Furnish.chest(s, 2, 1, 9, "east");
        Furnish.sack(s, 4, 1, 9, false);
        Furnish.crate(s, 4, 1, 10, "spruce");
        fill(s, 2, 1, 10, 2, 2, 10, FENCE);
        hang(s, 3, 4, 6);
        hang(s, 3, 4, 9);
        // The loft: two beds under the roof, furs, a lantern.
        boxBed(s, 2, 6, 7, "north", "blue", false);
        boxBed(s, 4, 6, 7, "north", "blue", false);
        Nordic.furs(s, 3, 8, 3, 9, 6);
        Furnish.sack(s, 2, 6, 9, true);
        hang(s, 3, 8, 8);
        s.connect();
        return s;
    }

    /**
     * A boat lying along z with its keel on the line {@code cx} at layer {@code y}, its stern stem at {@code z1} and its bow
     * stem at {@code z2}: a bottom of planks, the bilge of stairs rising outwards, a white strake along the gunwale, thwarts,
     * the mast laid along it, the folded sail and the stems curling up at both ends.
     */
    private static void boat(Structure s, int cx, int y, int z1, int z2) {
        for (int z = z1 + 1; z <= z2 - 1; z++) {
            boolean end = z == z1 + 1 || z == z2 - 1;
            put(s, cx, y, z, PLANKS);
            put(s, cx - 1, y, z, Kit.stairs(STAIRS, "west"));
            put(s, cx + 1, y, z, Kit.stairs(STAIRS, "east"));
            if (!end) {
                put(s, cx - 1, y + 1, z, Kit.slab(Nordic.WHITE.slab()));
                put(s, cx + 1, y + 1, z, Kit.slab(Nordic.WHITE.slab()));
            }
        }
        for (int z : new int[]{z1 + 3, z2 - 3}) {
            put(s, cx, y + 1, z, Kit.slab(SLAB));
        }
        fill(s, cx, y + 2, z1 + 2, cx, y + 2, z2 - 2, Kit.log(LOG, "z"));
        put(s, cx, y + 1, (z1 + z2) / 2, Kit.CLOTH);
        put(s, cx, y + 1, (z1 + z2) / 2 + 1, Kit.CLOTH);
        for (int[] e : new int[][]{{z1, -1}, {z2, 1}}) {
            String out = e[1] < 0 ? "north" : "south";
            put(s, cx, y, e[0], Kit.stairs(STAIRS, Furnish.opposite(out)));
            put(s, cx, y + 1, e[0], POST);
            put(s, cx, y + 2, e[0], Kit.stairs(STAIRS, out));
        }
    }

    // ---------------------------------------------------------------- the painted house

    /**
     * A house in town of boards painted red (ochre or white in a look) with white corner boards, one and a half storeys under
     * a steep roof of shingles whose long side looks at the street. In the middle of the front a bay (ark) stands one block
     * before the wall and rises through the eaves to its own steep gable with white boards crossing over its ridge: the door
     * below under a little white pediment, a tall window above it. Windows with white lintels and sills and board shutters
     * on every side, plain white barge boards on the gables, the chimney on the ridge, a bench and a lamp before the house.
     * Inside: the stone stove in the middle, its stack up through the ridge, dividing the parlour (a table with chairs, a
     * bench, the chest, a rug of furs) from the kitchen (cauldron, fire, board and shelf); a ladder up to the loft with two
     * box beds on either side of the stack and a third in the bay.
     */
    static Structure redHouse() {
        Structure s = new Structure(11, 16, 11);
        Nordic.footing(s, 1, 1, 9, 7, 0);
        Nordic.paintedWalls(s, 1, 1, 1, 9, 5, 7);
        fill(s, 2, 5, 2, 8, 5, 6, PLANKS);
        for (int x : new int[]{2, 8}) {
            paintedWindow(s, x, 2, 7, "south", 2);
        }
        for (int x : new int[]{3, 7}) {
            paintedWindow(s, x, 2, 1, "north", 2);
        }
        paintedWindow(s, 1, 2, 3, "west", 2);
        paintedWindow(s, 9, 2, 3, "east", 2);
        // The main roof and its gables.
        Nordic.Field main = Nordic.gableField(1, 1, 9, 7, 5, true, Nordic.STEEP);
        Nordic.roof(s, main, Nordic.EVERYWHERE, 0, 0, 10, 8, Nordic.ROOF);
        Nordic.gableWall(s, main, true, 1, 1, 7, 6, PAINT);
        Nordic.gableWall(s, main, true, 9, 1, 7, 6, PAINT);
        // The dormer bay: it stands one block before the front wall and rises through the eaves to its own gable.
        fill(s, 3, 5, 8, 7, 6, 8, AIR);
        fill(s, 3, 6, 7, 7, 6, 7, AIR);
        fill(s, 4, 6, 6, 6, 9, 6, AIR);
        fill(s, 3, 0, 8, 7, 0, 8, FOOTING);
        fill(s, 3, 1, 8, 7, 1, 8, Kit.log(LOG, "x"));
        fill(s, 3, 2, 8, 7, 7, 8, PAINT);
        fill(s, 3, 2, 8, 3, 7, 8, Nordic.WHITE_POST);
        fill(s, 7, 2, 8, 7, 7, 8, Nordic.WHITE_POST);
        fill(s, 3, 6, 7, 3, 7, 7, PAINT);
        fill(s, 7, 6, 7, 7, 7, 7, PAINT);
        Nordic.Field arkGable = Nordic.gableField(3, 5, 7, 8, 7, false, Nordic.STEEP);
        Nordic.Field ark = (x, z) -> z < 5 ? Nordic.NONE : arkGable.at(x, z);
        Nordic.roof(s, ark, (x, z) -> ark.at(x, z) > main.at(x, z), 2, 5, 8, 9, Nordic.ROOF);
        Nordic.gableWall(s, ark, false, 8, 3, 7, 8, PAINT);
        paintedWindow(s, 5, 6, 8, "south", 2);
        put(s, 5, 9, 8, Kit.GLASS);
        bargeBoards(s, true, 0, 0, 8, Nordic.ROOF, Nordic.WHITE);
        bargeBoards(s, true, 10, 0, 8, Nordic.ROOF, Nordic.WHITE);
        Nordic.crossedBoards(s, false, 9, 2, 8, Nordic.ROOF, Nordic.WHITE, false);
        // The door in the bay under a little white pediment, the doorway through the front wall behind it.
        s.door(5, 1, 8, DOOR, "north", "left");
        fill(s, 5, 1, 7, 5, 2, 7, AIR);
        put(s, 5, 3, 8, Nordic.WHITE.block());
        put(s, 4, 4, 9, Kit.stairs(Nordic.WHITE.stairs(), "east"));
        put(s, 6, 4, 9, Kit.stairs(Nordic.WHITE.stairs(), "west"));
        put(s, 5, 4, 9, Nordic.WHITE.block());
        put(s, 5, 0, 9, Kit.slab(FOOTING_SLAB));
        fill(s, 4, 6, 7, 6, 7, 7, AIR);
        // The windows of the loft in the gables.
        paintedWindow(s, 1, 7, 4, "west", 2);
        paintedWindow(s, 9, 7, 4, "east", 2);
        // The stove in the middle and its stack through the ridge.
        fill(s, 5, 1, 3, 5, 2, 3, FOOTING);
        Furnish.hearth(s, 5, 1, 4, "west");
        fill(s, 5, 3, 4, 5, 10, 4, FOOTING);
        Nordic.chimney(s, 5, 4, 12, 13);
        // The parlour: bench, chest, table with chairs, rug.
        Furnish.bench(s, 2, 2, 2, 3, 1, "east", "spruce");
        Furnish.chest(s, 3, 1, 2, "south");
        put(s, 3, 1, 5, Kit.topSlab(FLOOR_SLAB));
        put(s, 3, 2, 5, Furnish.candles(3));
        Furnish.chair(s, 2, 1, 5, "east", "oak");
        Furnish.chair(s, 3, 1, 6, "north", "oak");
        Nordic.furs(s, 3, 3, 3, 4, 1);
        hang(s, 3, 4, 3);
        // The kitchen: cauldron, fire, board and shelf, a table, a crate and a sack, the ladder up.
        Furnish.kitchen(s, 6, 1, 2, "south", "spruce");
        put(s, 7, 1, 4, Kit.topSlab(FLOOR_SLAB));
        put(s, 7, 2, 4, Furnish.candles(2));
        Furnish.crate(s, 6, 1, 6, "spruce");
        Furnish.sack(s, 8, 1, 6, true);
        fill(s, 8, 1, 5, 8, 5, 5, Kit.ladder("west"));
        hang(s, 7, 4, 4);
        // The loft: two box beds on either side of the stack, the third in the dormer, furs and lanterns.
        boxBed(s, 3, 6, 4, "west", "red", false);
        boxBed(s, 7, 6, 4, "east", "red", false);
        boxBed(s, 4, 6, 5, "south", "red", false);
        Nordic.furs(s, 6, 5, 7, 5, 6);
        hang(s, 3, 8, 3);
        hang(s, 7, 8, 3);
        hang(s, 5, 9, 6);
        // Before the house: a lamp on a post and a bench under the window.
        fill(s, 9, 0, 9, 9, 1, 9, FENCE);
        put(s, 9, 2, 9, Kit.lantern());
        Furnish.bench(s, 1, 9, 2, 9, 0, "south", "spruce");
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the longhouse

    /**
     * A longhouse with a gallery (svalgang): its long walls of staves bow out like the hull of a ship, and its curved roof of
     * shingles goes on down beyond the eaves to slender posts, so that a covered gallery runs along both sides of the hall
     * and the whole house sits low like an upturned boat; crossed boards and dragon heads on both gables, a smoke louvre on
     * the ridge, a carved porch at the door and small windows with shutters under the gallery roof. Inside: the long hearth
     * with its cauldron, sleeping benches with furs along the walls, the high seat between carved pillars, two box beds and
     * the loom at the back gable, the chest, tapestries and lanterns.
     */
    static Structure longhouse() {
        Structure s = new Structure(13, 17, 15);
        int c = 6;
        int a1 = 3;
        int a2 = 11;
        int ridge = galleryHall(s, a1, a2, c, 0, 3, 4, 5);
        Nordic.louvre(s, c, ridge, 7, false, Nordic.ROOF);
        Nordic.portal(s, c, 1, a2, "south", true, Nordic.ROOF, Nordic.BOARD, null);
        for (int z : new int[]{6, 8}) {
            slit(s, 2, 4, z, "west");
            slit(s, 10, 4, z, "east");
        }
        // The back gable: two box beds and the loom between them.
        boxBed(s, 4, 1, 5, "north", "red", false);
        boxBed(s, 8, 1, 5, "north", "blue", false);
        Nordic.loom(s, c, 1, 4, "south");
        // The benches along the walls and the high seat.
        Nordic.sleepingBench(s, 3, 6, 3, 9, 1, "east");
        Nordic.highSeat(s, 9, 1, 7, "west");
        Nordic.sleepingBench(s, 9, 9, 9, 9, 1, "west");
        Nordic.sleepingBench(s, 9, 5, 9, 5, 1, "west");
        Nordic.tapestry(s, 3, 6, 3, 9, 3, "east");
        Nordic.tapestry(s, 9, 9, 9, 9, 3, "west");
        // The long hearth with its cauldron.
        Nordic.longHearth(s, 5, 5, 7, 9, 1, ceiling(s, c, 1, 8));
        // By the door: the chest, a sack, a crate, a water cauldron.
        Furnish.chest(s, 4, 1, 10, "east");
        Furnish.sack(s, 5, 1, 10, false);
        put(s, 7, 1, 10, WATER);
        Furnish.crate(s, 8, 1, 10, "spruce");
        hang(s, 4, 4, 8);
        hang(s, 8, 4, 10);
        hang(s, 5, 4, 10);
        hang(s, 6, 6, 5);
        // Lanterns under the gallery roof.
        for (int z : new int[]{4, 7, 10}) {
            int w = Nordic.hullWidth(z, a1, a2, 3, 4);
            hang(s, c - w - 1, 4, z);
            hang(s, c + w + 1, 4, z);
        }
        s.connect();
        return s.trimSky(3);
    }

    /**
     * A longhouse along z whose roof runs on down over a gallery: walls of staves from the gable on the line {@code a1} to
     * that on {@code a2}, their half-width {@code end} at the gables swelling to {@code mid} halfway, on a footing at layer
     * {@code y} with a floor inside; the plate at {@code y + wall}; a hull roof of shingles over them, overhanging one block,
     * and beyond its eaves one row lower again over a gallery whose floor runs all round, carried on posts; stave gables,
     * crossed boards of the timber and dragon heads of the spires at both ends.
     *
     * @return the layer of the ridge in the middle
     */
    private static int galleryHall(Structure s, int a1, int a2, int c, int y, int end, int mid, int wall) {
        Nordic.Area plan = (x, z) -> z >= a1 && z <= a2 && Math.abs(x - c) <= Nordic.hullWidth(z, a1, a2, end, mid);
        int top = y + wall;
        // The gallery floor all round, its outer edge of rough stone.
        for (int z = a1 - 1; z <= a2 + 1; z++) {
            int wh = Nordic.hullWidth(Math.max(a1, Math.min(a2, z)), a1, a2, end, mid);
            for (int x = c - wh - 2; x <= c + wh + 2; x++) {
                if (!plan.has(x, z)) {
                    put(s, x, y, z, Math.abs(x - c) == wh + 2 ? FOOTING : FLOOR);
                }
            }
        }
        for (int a = a1; a <= a2; a++) {
            int w = Nordic.hullWidth(a, a1, a2, end, mid);
            for (int x = c - w; x <= c + w; x++) {
                boolean rim = !plan.has(x + 1, a) || !plan.has(x - 1, a) || !plan.has(x, a + 1) || !plan.has(x, a - 1);
                if (!rim) {
                    put(s, x, y, a, FLOOR);
                    continue;
                }
                put(s, x, y, a, FOOTING);
                boolean gableEnd = a == a1 || a == a2;
                boolean step = !gableEnd && (Math.abs(x - c) < w || Nordic.hullWidth(a - 1, a1, a2, end, mid) != w
                        || Nordic.hullWidth(a + 1, a1, a2, end, mid) != w);
                boolean corner = gableEnd && Math.abs(x - c) == w;
                for (int yy = y + 1; yy <= top; yy++) {
                    String state;
                    if (corner || step && yy < top) {
                        state = POST;
                    } else if (yy == top || yy == y + 1) {
                        state = Kit.log(LOG, gableEnd ? "x" : "z");
                    } else {
                        state = STAVE;
                    }
                    put(s, x, yy, a, state);
                }
            }
        }
        Nordic.Field f = (x, z) -> {
            if (z < a1 - 1 || z > a2 + 1) {
                return Nordic.NONE;
            }
            int wh = Nordic.hullWidth(Math.max(a1, Math.min(a2, z)), a1, a2, end, mid);
            int d = Math.abs(x - c);
            if (d <= wh + 1) {
                return top + Nordic.rise(Nordic.HULL, wh + 1 - d);
            }
            return d == wh + 2 ? top - 1 : Nordic.NONE;
        };
        int span = mid + 2;
        Nordic.roof(s, f, Nordic.EVERYWHERE, c - span, a1 - 1, c + span, a2 + 1, Nordic.ROOF);
        // The posts of the gallery.
        int am = (a1 + a2) / 2;
        for (int z : new int[]{a1 - 1, am - 2, am, am + 2, a2 + 1}) {
            int wh = Nordic.hullWidth(Math.max(a1, Math.min(a2, z)), a1, a2, end, mid);
            for (int side = -1; side <= 1; side += 2) {
                fill(s, c + side * (wh + 2), y + 1, z, c + side * (wh + 2), top - 2, z, POST);
            }
        }
        int we = Nordic.hullWidth(a1, a1, a2, end, mid);
        Nordic.gableWall(s, f, false, a1, c - we, c + we, top + 1, STAVE);
        Nordic.gableWall(s, f, false, a2, c - we, c + we, top + 1, STAVE);
        Nordic.crossedBoards(s, false, a1 - 1, c - we - 2, c + we + 2, Nordic.ROOF, Nordic.BOARD, true);
        Nordic.crossedBoards(s, false, a2 + 1, c - we - 2, c + we + 2, Nordic.ROOF, Nordic.BOARD, true);
        int endRidge = f.at(c, a1);
        Nordic.dragonHead(s, c, endRidge + 1, a1 - 1, "north", Nordic.SPIRE);
        Nordic.dragonHead(s, c, endRidge + 1, a2 + 1, "south", Nordic.SPIRE);
        return f.at(c, (a1 + a2) / 2);
    }

    // ---------------------------------------------------------------- the merchant's house

    /**
     * A merchant's house of three storeys: the shop below in walls of rough stone; over it the dwelling jettied out on every
     * side on carved brackets, of boards painted red, ochre or white with white corners and white-framed windows; over that
     * the steep gable to the street with white crossed boards and dragon heads on both ends of the ridge, and the loft for
     * the goods and a bed under it. In the shop a counter with candles, the merchant's chest behind it, bolts of cloth, sacks
     * and crates, shutters at its windows and lanterns and a yellow sign under the jetty, a stair up; in the dwelling the
     * hearth with its stone stack, a table with benches, two box beds with a night table, the loom; a ladder to the loft.
     */
    static Structure merchant() {
        Structure s = new Structure(13, 21, 15);
        // The shop of rough stone.
        s.fill(3, 0, 4, 9, 0, 10, FOOTING);
        fill(s, 4, 0, 5, 8, 0, 9, FLOOR);
        s.walls(3, 1, 4, 9, 3, 10, FOOTING);
        s.beam(3, 4, 9, 10, 4, LOG);
        // The jettied dwelling of painted boards, its floor and ceiling.
        Nordic.paintedWalls(s, 2, 4, 3, 10, 8, 11);
        fill(s, 4, 4, 5, 8, 4, 9, PLANKS);
        fill(s, 3, 8, 4, 9, 8, 10, PLANKS);
        for (int z = 4; z <= 10; z += 2) {
            put(s, 2, 3, z, Kit.upside(STAIRS, "east"));
            put(s, 10, 3, z, Kit.upside(STAIRS, "west"));
        }
        for (int x = 3; x <= 9; x += 2) {
            put(s, x, 3, 11, Kit.upside(STAIRS, "north"));
            put(s, x, 3, 3, Kit.upside(STAIRS, "south"));
        }
        // The shop front: the door with its step, two windows with shutters, lanterns and the sign under the jetty.
        s.door(6, 1, 10, DOOR, "north", "left");
        put(s, 6, 0, 11, Kit.slab(FOOTING_SLAB));
        for (int x : new int[]{4, 8}) {
            put(s, x, 2, 10, Kit.GLASS);
            put(s, x, 2, 4, Kit.GLASS);
        }
        for (int x : new int[]{3, 5, 7, 9}) {
            put(s, x, 2, 11, Kit.shutter(Kit.SHUTTER, "south"));
        }
        put(s, 3, 2, 7, Kit.GLASS);
        put(s, 9, 2, 7, Kit.GLASS);
        put(s, 2, 2, 9, Kit.wallBanner("yellow", "west"));
        put(s, 10, 2, 9, Kit.wallBanner("yellow", "east"));
        hang(s, 4, 3, 11);
        hang(s, 8, 3, 11);
        // The windows of the dwelling and of the loft, before the roof.
        for (int x : new int[]{4, 8}) {
            paintedWindow(s, x, 6, 11, "south", 2);
            paintedWindow(s, x, 6, 3, "north", 2);
        }
        paintedWindow(s, 2, 6, 5, "west", 2);
        paintedWindow(s, 2, 6, 9, "west", 2);
        paintedWindow(s, 10, 6, 5, "east", 2);
        paintedWindow(s, 10, 6, 9, "east", 2);
        int ridge = Nordic.gableRoof(s, 2, 3, 10, 11, 8, false, Nordic.STEEP, Nordic.ROOF, PAINT, null, Nordic.SPIRE);
        Nordic.crossedBoards(s, false, 2, 1, 11, Nordic.ROOF, Nordic.WHITE, false);
        Nordic.crossedBoards(s, false, 12, 1, 11, Nordic.ROOF, Nordic.WHITE, false);
        paintedWindow(s, 6, 10, 11, "south", 2);
        paintedWindow(s, 6, 10, 3, "north", 2);
        // The stair up from the shop along the east wall.
        flight(s, 8, 1, 9, 0, -1, 4, FLOOR_STAIRS);
        fill(s, 8, 1, 8, 8, 1, 8, PLANKS);
        fill(s, 8, 1, 7, 8, 2, 7, PLANKS);
        fill(s, 8, 1, 6, 8, 3, 6, PLANKS);
        fill(s, 8, 4, 7, 8, 4, 8, AIR);
        fill(s, 7, 5, 7, 7, 5, 8, FENCE);
        // The shop: the counter, the chest behind it, goods.
        fill(s, 4, 1, 7, 6, 1, 7, Kit.upside(STAIRS, "north"));
        put(s, 5, 2, 7, Furnish.candles(3));
        Furnish.chest(s, 4, 1, 5, "south");
        Furnish.crate(s, 5, 1, 5, "spruce");
        Furnish.sack(s, 6, 1, 5, true);
        Furnish.sack(s, 6, 2, 5, false);
        put(s, 7, 1, 5, "minecraft:white_wool");
        put(s, 7, 2, 5, "minecraft:yellow_wool");
        Furnish.crate(s, 4, 1, 9, "spruce");
        Furnish.sack(s, 4, 2, 9, false);
        put(s, 4, 1, 8, Kit.CLOTH);
        hang(s, 5, 3, 6);
        hang(s, 6, 3, 9);
        // The dwelling: the hearth and its stack, the table with benches, the beds, the loom.
        Furnish.hearth(s, 3, 5, 7, "east");
        fill(s, 3, 7, 7, 3, 11, 7, FOOTING);
        Nordic.chimney(s, 3, 7, 12, 13);
        fill(s, 4, 5, 9, 6, 5, 9, Kit.topSlab(FLOOR_SLAB));
        put(s, 5, 6, 9, Furnish.candles(3));
        Furnish.bench(s, 4, 8, 6, 8, 5, "south", "oak");
        Furnish.bench(s, 4, 10, 6, 10, 5, "north", "oak");
        boxBed(s, 4, 5, 5, "north", "red", false);
        boxBed(s, 6, 5, 5, "north", "red", false);
        put(s, 5, 5, 4, Kit.topSlab(FLOOR_SLAB));
        put(s, 5, 6, 4, Furnish.candles(1));
        Nordic.loom(s, 8, 5, 4, "south");
        Nordic.furs(s, 5, 6, 6, 6, 5);
        fill(s, 7, 5, 10, 7, 8, 10, Kit.ladder("north"));
        hang(s, 5, 7, 7);
        hang(s, 8, 7, 9);
        // The loft under the roof: the third bed, the stock of goods, a lantern.
        boxBed(s, 6, 9, 6, "north", "red", false);
        Furnish.crate(s, 4, 9, 5, "spruce");
        Furnish.crate(s, 4, 9, 9, "spruce");
        Furnish.crate(s, 4, 10, 9, "spruce");
        Furnish.sack(s, 8, 9, 5, true);
        Furnish.sack(s, 8, 9, 6, false);
        put(s, 8, 9, 8, "minecraft:white_wool");
        put(s, 8, 10, 8, "minecraft:red_wool");
        hang(s, 6, ridge - 4, 8);
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the chieftain's homestead

    /**
     * A chieftain's homestead: a long hall of staves of two storeys with its long side to the yard, a pentice of shingles
     * round it at the floor of the upper storey and a steep roof over it with crossed boards, so that its roofs stand in
     * tiers; in the middle a cross gable (ark) juts out over the door with a dragon head looking out over the yard, and on
     * the ridge behind it a belfry with a bell under a needle. Before it the yard behind a fence of boards on posts, a gate
     * between carved posts under a little roof, the well with its winch on one side, firewood and a chopping block on the
     * other, the banner of the house on a pole. In the hall the long hearth, benches along the walls, the high seat between
     * its pillars, tapestries, the weapons and the chest; above, the chambers with four box beds, the loom and furs.
     */
    static Structure chieftain() {
        Structure s = new Structure(13, 28, 15);
        // The hall: walls of staves with a belt at the upper floor, its floors.
        Nordic.footing(s, 1, 2, 11, 8, 0);
        Nordic.staveWalls(s, 1, 1, 2, 11, 8, 8);
        s.beam(1, 2, 11, 8, 5, LOG);
        fill(s, 2, 5, 3, 10, 5, 7, PLANKS);
        Nordic.footing(s, 4, 8, 8, 11, 0);
        // Windows before the roofs.
        for (int x : new int[]{2, 10}) {
            Nordic.window(s, x, 2, 8, "south", 1, false);
            Nordic.window(s, x, 6, 8, "south", 2, true);
        }
        for (int x : new int[]{4, 8}) {
            Nordic.window(s, x, 6, 2, "north", 2, true);
        }
        Nordic.window(s, 11, 6, 4, "east", 2, true);
        Nordic.window(s, 11, 2, 3, "east", 1, false);
        Nordic.window(s, 1, 2, 3, "west", 1, false);
        // The main roof and its gables.
        Nordic.Field main = Nordic.gableField(1, 2, 11, 8, 8, true, Nordic.STEEP);
        Nordic.roof(s, main, Nordic.EVERYWHERE, 0, 1, 12, 9, Nordic.ROOF);
        Nordic.gableWall(s, main, true, 1, 2, 8, 9, STAVE);
        Nordic.gableWall(s, main, true, 11, 2, 8, 9, STAVE);
        // The ark over the door: the main roof gives way inside it, its walls, its floors, its own roof and gable.
        fill(s, 5, 9, 8, 7, 11, 8, AIR);
        fill(s, 5, 8, 9, 7, 11, 10, AIR);
        Nordic.staveWalls(s, 4, 1, 8, 8, 8, 11);
        s.beam(4, 8, 8, 11, 5, LOG);
        fill(s, 5, 5, 9, 7, 5, 10, PLANKS);
        Nordic.window(s, 6, 6, 11, "south", 2, true);
        Nordic.Field arkGable = Nordic.gableField(4, 7, 8, 11, 8, false, Nordic.STEEP);
        Nordic.Field ark = (x, z) -> z < 7 ? Nordic.NONE : arkGable.at(x, z);
        Nordic.roof(s, ark, (x, z) -> ark.at(x, z) > main.at(x, z), 3, 7, 9, 12, Nordic.ROOF);
        Nordic.gableWall(s, ark, false, 11, 4, 8, 9, STAVE);
        put(s, 6, 10, 11, Kit.GLASS);
        Nordic.crossedBoards(s, true, 0, 1, 9, Nordic.ROOF, Nordic.BOARD, true);
        Nordic.crossedBoards(s, true, 12, 1, 9, Nordic.ROOF, Nordic.BOARD, true);
        Nordic.crossedBoards(s, false, 12, 3, 9, Nordic.ROOF, Nordic.BOARD, true);
        Nordic.dragonHead(s, 6, ark.at(6, 12) + 1, 12, "south", Nordic.SPIRE);
        // The pentice round the hall at the upper floor, the lower tier of its roofs.
        pentice(s, 1, 2, 11, 8, 5, 4, 8);
        // The belfry on the ridge behind the ark.
        int ridge = main.at(6, 5);
        belfry(s, 6, ridge - 1, 5, ridge + 4, true);
        // The doors: the carved door of the ark under its hood, the opening into the hall, the openings above.
        s.door(6, 1, 11, DOOR, "north", "left");
        fill(s, 5, 1, 11, 5, 2, 11, POST);
        fill(s, 7, 1, 11, 7, 2, 11, POST);
        put(s, 6, 3, 11, Kit.log(LOG, "x"));
        put(s, 5, 4, 12, Kit.stairs(STAIRS, "east"));
        put(s, 7, 4, 12, Kit.stairs(STAIRS, "west"));
        put(s, 6, 4, 12, PLANKS);
        put(s, 6, 5, 12, Kit.slab(SLAB));
        put(s, 5, 3, 12, Kit.hangingLantern());
        put(s, 7, 3, 12, Kit.hangingLantern());
        put(s, 6, 0, 12, Kit.slab(FOOTING_SLAB));
        fill(s, 6, 1, 8, 6, 2, 8, AIR);
        fill(s, 5, 6, 8, 7, 7, 8, AIR);
        // The hall: the high seat at the west gable, the long hearth, benches, tapestries, weapons, the chest, a ladder up.
        Nordic.highSeat(s, 2, 1, 5, "east");
        Nordic.longHearth(s, 4, 4, 8, 6, 1, 5);
        Nordic.sleepingBench(s, 3, 3, 10, 3, 1, "south");
        Nordic.sleepingBench(s, 3, 7, 5, 7, 1, "north");
        Nordic.sleepingBench(s, 7, 7, 8, 7, 1, "north");
        Nordic.tapestry(s, 4, 3, 8, 3, 3, "south");
        Nordic.tapestry(s, 3, 7, 5, 7, 3, "north");
        Nordic.tapestry(s, 7, 7, 8, 7, 3, "north");
        fill(s, 10, 1, 5, 10, 5, 5, Kit.ladder("west"));
        Furnish.weaponRack(s, 9, 1, 6, "west", "spruce");
        Furnish.chest(s, 10, 1, 7, "west");
        Furnish.crate(s, 9, 1, 7, "spruce");
        hang(s, 4, 4, 5);
        hang(s, 8, 4, 5);
        hang(s, 6, 4, 9);
        hang(s, 9, 4, 4);
        // The chambers above: four box beds, night tables, the loom, furs, lanterns.
        for (int x : new int[]{3, 5, 8}) {
            boxBed(s, x, 6, 4, "north", "red", false);
        }
        put(s, 4, 6, 3, Kit.topSlab(FLOOR_SLAB));
        put(s, 4, 7, 3, Furnish.candles(2));
        put(s, 9, 6, 3, Kit.topSlab(FLOOR_SLAB));
        put(s, 9, 7, 3, Furnish.candles(1));
        boxBed(s, 6, 6, 9, "south", "red", false);
        Nordic.loom(s, 2, 6, 5, "east");
        Nordic.furs(s, 4, 5, 8, 6, 6);
        Nordic.tapestry(s, 6, 3, 7, 3, 7, "south");
        hang(s, 4, 9, 5);
        hang(s, 8, 9, 5);
        hang(s, 6, 9, 9);
        // The yard: the fence of boards on posts, the gate under its roof, the well, the firewood, the banner pole.
        for (int x = 0; x <= 12; x++) {
            if (x < 4 || x > 8) {
                fenceBay(s, x, 14, x % 3 == 0);
            }
        }
        for (int z = 10; z <= 13; z++) {
            fenceBay(s, 0, z, z == 10);
            fenceBay(s, 12, z, z == 10);
        }
        fill(s, 4, 0, 14, 4, 3, 14, POST);
        fill(s, 8, 0, 14, 8, 3, 14, POST);
        fill(s, 3, 4, 14, 9, 4, 14, Kit.log(LOG, "x"));
        fill(s, 3, 5, 13, 9, 5, 13, Kit.stairs(Nordic.ROOF.stairs(), "south"));
        fill(s, 3, 5, 14, 9, 5, 14, Kit.stairs(Nordic.ROOF.stairs(), "north"));
        put(s, 3, 5, 13, Kit.stairs(Nordic.BOARD.stairs(), "south"));
        put(s, 3, 5, 14, Kit.stairs(Nordic.BOARD.stairs(), "north"));
        put(s, 9, 5, 13, Kit.stairs(Nordic.BOARD.stairs(), "south"));
        put(s, 9, 5, 14, Kit.stairs(Nordic.BOARD.stairs(), "north"));
        fill(s, 5, 0, 14, 7, 0, 14, GATE_OPEN);
        put(s, 6, 3, 14, Kit.hangingLantern());
        well(s, 10, 11);
        woodpile(s, 1, 10, 1, 13, 0, 1, "z");
        fill(s, 1, 2, 10, 1, 2, 13, Kit.slab(SLAB));
        put(s, 3, 0, 12, Kit.log("minecraft:stripped_spruce_log", "y"));
        fill(s, 11, 0, 13, 11, 5, 13, FENCE);
        put(s, 11, 6, 13, Kit.banner("red", 0));
        fill(s, 5, 0, 13, 7, 0, 13, Kit.slab(FOOTING_SLAB));
        s.connect();
        return s.trimSky(3);
    }

    /**
     * One bay of a fence of painted boards at (x, z) standing on the ground: boards two high under a capping slab, or a post
     * of the timber with a cap.
     */
    private static void fenceBay(Structure s, int x, int z, boolean post) {
        if (post) {
            fill(s, x, 0, z, x, 2, z, POST);
            put(s, x, 3, z, Kit.slab(SLAB));
        } else {
            fill(s, x, 0, z, x, 1, z, PAINT);
            put(s, x, 2, z, Kit.slab(Nordic.WHITE.slab()));
        }
    }

    /**
     * A well with its centre at (x, z) on the ground: a ring of rough stone round the water, two posts with a winch beam,
     * the chain down to the water and a little roof of shingles over it.
     */
    private static void well(Structure s, int x, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                put(s, x + dx, 0, z + dz, dx == 0 && dz == 0 ? WATER : Kit.ROUGH_FENCE);
            }
        }
        fill(s, x - 1, 1, z, x - 1, 2, z, FENCE);
        fill(s, x + 1, 1, z, x + 1, 2, z, FENCE);
        fill(s, x - 1, 3, z, x + 1, 3, z, Kit.log(LOG, "x"));
        fill(s, x, 1, z, x, 2, z, Kit.chain());
        fill(s, x - 1, 4, z - 1, x + 1, 4, z - 1, Kit.stairs(Nordic.ROOF.stairs(), "south"));
        fill(s, x - 1, 4, z + 1, x + 1, 4, z + 1, Kit.stairs(Nordic.ROOF.stairs(), "north"));
        fill(s, x - 1, 4, z, x + 1, 4, z, Nordic.ROOF.block());
        fill(s, x - 1, 5, z, x + 1, 5, z, Kit.slab(Nordic.ROOF.slab()));
    }

    // ---------------------------------------------------------------- the smithy

    /**
     * A smithy: a forge house of rough stone with a timber plate under a steep roof of shingles with crossed boards, its
     * long side open to the street in a wide doorway; a tall stack of rough stone against the east gable, joined to the forge
     * by a flue through the wall; firewood and sacks of charcoal under the west eaves, a paved apron with a grindstone, a
     * bench and a lamp before it. Inside the forge on its stone bed under a hood, the anvil, the blast furnace, the smithing
     * table, the smith's post with his chest, the quenching tub, a bin of charcoal, a rack of tools and iron stock; a loft
     * under the roof over a ceiling of boards.
     */
    static Structure smithy() {
        Structure s = new Structure(13, 15, 11);
        s.fill(2, 0, 1, 8, 0, 7, FOOTING);
        s.walls(2, 1, 1, 8, 3, 7, FOOTING);
        for (int[] q : new int[][]{{2, 1}, {8, 1}, {2, 7}, {8, 7}}) {
            fill(s, q[0], 1, q[1], q[0], 3, q[1], Nordic.STONE);
        }
        s.beam(2, 1, 8, 7, 4, LOG);
        fill(s, 3, 4, 2, 7, 4, 6, PLANKS);
        fill(s, 4, 1, 7, 6, 3, 7, AIR);
        put(s, 4, 2, 1, Kit.BARS);
        put(s, 6, 2, 1, Kit.BARS);
        put(s, 2, 3, 4, Kit.BARS);
        Nordic.gableRoof(s, 2, 1, 8, 7, 4, true, Nordic.STEEP, Nordic.ROOF, STAVE, Nordic.BOARD, null);
        // The forge: a stone bed with its fire under a hood, the flue through the gable into the stack outside.
        fill(s, 7, 1, 3, 7, 1, 4, FOOTING);
        put(s, 7, 2, 3, Kit.slab(FOOTING_SLAB));
        put(s, 7, 2, 4, campfire("west"));
        fill(s, 6, 3, 3, 6, 3, 4, Kit.upside(FOOTING_STAIRS, "east"));
        fill(s, 7, 3, 3, 7, 4, 4, FOOTING);
        fill(s, 6, 4, 3, 6, 4, 4, FOOTING);
        fill(s, 8, 2, 3, 9, 3, 4, FOOTING);
        fill(s, 10, 0, 3, 11, 12, 4, FOOTING);
        fill(s, 9, 0, 3, 9, 1, 4, FOOTING);
        put(s, 10, 13, 3, Kit.slab(FOOTING_SLAB));
        put(s, 11, 13, 4, Kit.slab(FOOTING_SLAB));
        fill(s, 10, 6, 2, 11, 6, 2, Kit.stairs(FOOTING_STAIRS, "south"));
        fill(s, 10, 6, 5, 11, 6, 5, Kit.stairs(FOOTING_STAIRS, "north"));
        // The smith's post with his chest, the tools, the stock.
        Furnish.chest(s, 3, 1, 2, "south");
        put(s, 4, 1, 2, WORK_POST);
        put(s, 5, 1, 2, "minecraft:smithing_table");
        put(s, 6, 1, 2, "minecraft:blast_furnace[facing=south,lit=false]");
        put(s, 7, 1, 2, "minecraft:coal_block");
        put(s, 5, 1, 4, "minecraft:anvil[facing=west]");
        put(s, 7, 1, 5, WATER);
        put(s, 3, 1, 4, "minecraft:raw_iron_block");
        put(s, 3, 1, 5, "minecraft:iron_block");
        fill(s, 3, 1, 6, 3, 2, 6, FENCE);
        put(s, 3, 3, 6, Kit.topSlab(SLAB));
        put(s, 7, 1, 6, "minecraft:coal_block");
        hang(s, 4, 3, 4);
        hang(s, 6, 3, 6);
        hang(s, 5, 8, 4);
        // Outside: firewood and charcoal to the west, the apron with a grindstone, a bench and a lamp.
        woodpile(s, 1, 2, 1, 6, 0, 2, "z");
        put(s, 0, 0, 3, "minecraft:brown_wool");
        put(s, 0, 0, 4, "minecraft:brown_wool");
        put(s, 0, 1, 3, "minecraft:brown_wool");
        put(s, 0, 0, 5, "minecraft:coal_block");
        s.fill(2, 0, 8, 8, 0, 9, FOOTING);
        put(s, 3, 1, 9, "minecraft:grindstone[face=floor,facing=east]");
        Furnish.bench(s, 7, 9, 8, 9, 1, "south", "spruce");
        fill(s, 2, 1, 9, 2, 2, 9, FENCE);
        put(s, 2, 3, 9, Kit.lantern());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the tavern

    /**
     * A tavern of two storeys of staves under a very steep roof with its gable to the street, crossed boards and dragon
     * heads on both ends of the ridge; along its east side the roof runs on down over a gallery on posts where the doors of
     * the guest rooms open, reached by an outer stair from the street; a carved porch at the door, a stone stack on the
     * west slope and the old sign of the house: banners on a bracket under a spruce bough. In the taproom the counter with
     * three barrels, the innkeeper's post and chest behind it, two long boards with benches under lanterns, the fireplace;
     * above, three rooms for guests with two box beds each.
     */
    static Structure tavern() {
        Structure s = new Structure(13, 22, 19);
        Nordic.footing(s, 1, 3, 9, 15, 0);
        Nordic.staveWalls(s, 1, 1, 3, 9, 9, 15);
        s.beam(1, 3, 9, 15, 5, LOG);
        fill(s, 2, 5, 4, 8, 5, 14, PLANKS);
        fill(s, 2, 9, 4, 8, 9, 14, PLANKS);
        // Windows before the roof.
        for (int x : new int[]{2, 8}) {
            Nordic.window(s, x, 2, 15, "south", 1, false);
            Nordic.window(s, x, 7, 15, "south", 1, false);
        }
        for (int z : new int[]{5, 9, 13}) {
            Nordic.window(s, 1, 7, z, "west", 1, false);
        }
        Nordic.window(s, 1, 2, 7, "west", 1, false);
        Nordic.window(s, 5, 2, 3, "north", 1, false);
        for (int z : new int[]{7, 11}) {
            put(s, 9, 2, z, Kit.GLASS);
        }
        // The roof, running on down over the gallery on the east side.
        Nordic.Field main = Nordic.gableField(1, 3, 9, 15, 9, false, Nordic.STEEP);
        Nordic.Field roof = (x, z) -> z >= 3 && z <= 15 && x == 11 ? 8 : main.at(x, z);
        Nordic.roof(s, roof, Nordic.EVERYWHERE, 0, 2, 11, 16, Nordic.ROOF);
        Nordic.gableWall(s, main, false, 3, 1, 9, 10, STAVE);
        Nordic.gableWall(s, main, false, 15, 1, 9, 10, STAVE);
        Nordic.crossedBoards(s, false, 2, 0, 10, Nordic.ROOF, Nordic.BOARD, true);
        Nordic.crossedBoards(s, false, 16, 0, 10, Nordic.ROOF, Nordic.BOARD, true);
        int ridge = main.at(5, 9);
        Nordic.dragonHead(s, 5, ridge + 1, 2, "north", Nordic.SPIRE);
        Nordic.dragonHead(s, 5, ridge + 1, 16, "south", Nordic.SPIRE);
        put(s, 5, 12, 15, Kit.GLASS);
        put(s, 5, 12, 3, Kit.GLASS);
        // The porch at the door.
        Nordic.portal(s, 5, 1, 15, "south", true, Nordic.ROOF, Nordic.BOARD, null);
        // The gallery: its deck on posts, the rail, the outer stair.
        fill(s, 10, 0, 4, 11, 0, 14, FOOTING);
        fill(s, 10, 5, 4, 11, 5, 14, PLANKS);
        fill(s, 11, 6, 4, 11, 6, 14, FENCE);
        for (int z : new int[]{4, 7, 11, 14}) {
            fill(s, 11, 1, z, 11, 7, z, POST);
            if (z > 4) {
                put(s, 11, 4, z - 1, Kit.upside(STAIRS, "south"));
            }
            if (z < 14) {
                put(s, 11, 4, z + 1, Kit.upside(STAIRS, "north"));
            }
        }
        put(s, 11, 6, 9, AIR);
        flight(s, 12, 1, 13, 0, -1, 5, FLOOR_STAIRS);
        put(s, 12, 0, 13, FOOTING);
        for (int z : new int[]{5, 9, 13}) {
            s.door(9, 6, z, DOOR, "west", "left");
        }
        for (int z : new int[]{6, 12}) {
            hang(s, 10, 4, z);
            hang(s, 10, 8, z);
        }
        // The sign: banners on a bracket out of the west wall, a spruce bough over them.
        put(s, 0, 4, 14, Kit.log(LOG, "x"));
        put(s, 0, 4, 15, Kit.wallBanner("red", "south"));
        put(s, 0, 4, 13, Kit.wallBanner("white", "north"));
        put(s, 0, 5, 14, Kit.leaves("spruce"));
        // Before the house: a board with benches for drinking outdoors, a lantern on it, casks stacked by the porch.
        Furnish.longTable(s, 1, 17, 2, 17, 0, "spruce");
        Furnish.bench(s, 1, 16, 2, 16, 0, "south", "spruce");
        Furnish.bench(s, 1, 18, 2, 18, 0, "north", "spruce");
        put(s, 2, 1, 17, Kit.lantern());
        Furnish.crate(s, 8, 0, 17, "spruce");
        Furnish.crate(s, 9, 0, 17, "spruce");
        Furnish.crate(s, 8, 1, 17, "spruce");
        Furnish.crate(s, 9, 0, 16, "spruce");
        // The counter at the back: the barrels, the innkeeper's post and chest, a shelf with candles.
        fill(s, 2, 1, 6, 5, 1, 6, Kit.upside(STAIRS, "north"));
        put(s, 3, 2, 6, Furnish.candles(2));
        put(s, 2, 1, 4, Kit.barrel());
        put(s, 2, 2, 4, Kit.barrel());
        put(s, 2, 1, 5, Kit.barrel());
        put(s, 3, 1, 4, WORK_POST);
        Furnish.chest(s, 4, 1, 4, "south");
        fill(s, 5, 3, 4, 7, 3, 4, Kit.topSlab(SLAB));
        put(s, 5, 4, 4, Furnish.candles(3));
        put(s, 7, 4, 4, "minecraft:flower_pot");
        Furnish.crate(s, 7, 1, 4, "spruce");
        Furnish.sack(s, 8, 1, 4, false);
        // The fireplace in the west wall and its stack, the long boards with benches, lanterns.
        Furnish.hearth(s, 2, 1, 13, "east");
        fill(s, 2, 3, 13, 2, 12, 13, FOOTING);
        Nordic.chimney(s, 2, 13, 13, 14);
        Nordic.feastBoard(s, 3, 8, 3, 11, 1);
        Nordic.feastBoard(s, 7, 8, 7, 12, 1);
        Nordic.tapestry(s, 2, 8, 2, 11, 3, "east");
        Nordic.furs(s, 3, 13, 4, 14, 1);
        Furnish.crate(s, 8, 1, 14, "spruce");
        hang(s, 3, 4, 9);
        hang(s, 7, 4, 10);
        hang(s, 5, 4, 6);
        hang(s, 5, 4, 13);
        // The guest rooms above: partitions, two box beds each, night tables, rugs, lanterns.
        fill(s, 2, 6, 7, 8, 8, 7, PLANKS);
        fill(s, 2, 6, 11, 8, 8, 11, PLANKS);
        for (int z0 : new int[]{4, 8, 12}) {
            boxBed(s, 3, 6, z0, "west", "red", false);
            boxBed(s, 3, 6, z0 + 2, "west", "red", false);
            if (z0 != 12) {
                put(s, 2, 6, z0 + 1, Kit.topSlab(FLOOR_SLAB));
                put(s, 2, 7, z0 + 1, Furnish.candles(1));
            }
            Nordic.furs(s, 5, z0, 6, z0 + 2, 6);
            Furnish.chair(s, 7, 6, z0, "south", "oak");
            hang(s, 6, 8, z0 + 1);
        }
        hang(s, 5, ridge - 4, 9);
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the town wall and its gate

    /**
     * A piece of the town wall with its gate: palisades of upright logs on a plinth of rough stone, their tops cut to points,
     * loopholes at the height of a fighting walk of boards behind them, reached by stairs from the town; between them the gate
     * tower: a ground storey of stone with the passage through it, closed on the field side by a double gate, and over it a
     * timber storey jutting out on brackets, with loopholes and doors onto both walks, under a very steep gable roof with
     * crossed boards, a dragon head looking out over the field and a watch turret with a lantern on its ridge.
     */
    static Structure wallGate() {
        Structure s = new Structure(23, 31, 11);
        // The palisades with their plinth, walks and stairs.
        for (int x = 0; x <= 22; x++) {
            if (x >= 8 && x <= 14) {
                continue;
            }
            put(s, x, 0, 6, FOOTING);
            put(s, x, 0, 7, Kit.slab(FOOTING_SLAB));
            int head = x % 2 == 0 ? 7 : 6;
            fill(s, x, 1, 6, x, head, 6, POST);
            put(s, x, head + 1, 6, FENCE);
            if (x % 3 == 1 && x != 7 && x != 15) {
                put(s, x, 6, 6, FENCE);
            }
        }
        for (int side = 0; side <= 1; side++) {
            int x1 = side == 0 ? 0 : 16;
            int x2 = side == 0 ? 6 : 22;
            fill(s, x1, 4, 3, x2, 4, 5, PLANKS);
            for (int x : new int[]{x1, (x1 + x2) / 2, x2}) {
                fill(s, x, 0, 3, x, 3, 3, POST);
                put(s, x, 3, 4, Kit.upside(STAIRS, "north"));
            }
            for (int x = x1; x <= x2; x++) {
                int landing = side == 0 ? 4 : 18;
                if (x != landing) {
                    put(s, x, 5, 3, FENCE);
                }
            }
            put(s, side == 0 ? 1 : 21, 6, 3, Kit.lantern());
            put(s, side == 0 ? 6 : 16, 6, 3, Kit.lantern());
            hang(s, side == 0 ? 2 : 20, 3, 4);
        }
        flight(s, 1, 1, 2, 1, 0, 4, FLOOR_STAIRS);
        flight(s, 21, 1, 2, -1, 0, 4, FLOOR_STAIRS);
        // The gate tower: the stone ground storey and its passage.
        s.fill(7, 0, 0, 15, 0, 8, FOOTING);
        fill(s, 8, 1, 1, 9, 3, 7, Nordic.STONE);
        fill(s, 13, 1, 1, 14, 3, 7, Nordic.STONE);
        fill(s, 10, 0, 1, 12, 0, 7, FOOTING);
        fill(s, 7, 1, 6, 7, 3, 6, POST);
        fill(s, 15, 1, 6, 15, 3, 6, POST);
        for (int z : new int[]{1, 7}) {
            fill(s, 10, 3, z, 12, 3, z, Kit.log(LOG, "x"));
            put(s, 9, 3, z, Kit.CARVED);
            put(s, 13, 3, z, Kit.CARVED);
        }
        put(s, 10, 1, 7, DOOR + "[facing=north,half=lower,hinge=left,open=true,powered=false]");
        put(s, 10, 2, 7, DOOR + "[facing=north,half=upper,hinge=left,open=true,powered=false]");
        put(s, 12, 1, 7, DOOR + "[facing=north,half=lower,hinge=right,open=true,powered=false]");
        put(s, 12, 2, 7, DOOR + "[facing=north,half=upper,hinge=right,open=true,powered=false]");
        // The timber storey jutting out on brackets, its floor, doors to the walks and loopholes.
        Nordic.staveWalls(s, 7, 4, 1, 15, 9, 7);
        fill(s, 8, 4, 2, 14, 4, 6, PLANKS);
        fill(s, 8, 9, 2, 14, 9, 6, PLANKS);
        for (int z = 1; z <= 7; z += 2) {
            put(s, 7, 3, z, Kit.upside(STAIRS, "east"));
            put(s, 15, 3, z, Kit.upside(STAIRS, "west"));
        }
        fill(s, 7, 5, 4, 7, 6, 4, AIR);
        fill(s, 15, 5, 4, 15, 6, 4, AIR);
        for (int x : new int[]{9, 13}) {
            put(s, x, 6, 7, FENCE);
            put(s, x, 6, 1, FENCE);
        }
        put(s, 11, 6, 7, Kit.GLASS);
        put(s, 11, 6, 1, Kit.GLASS);
        put(s, 7, 6, 2, FENCE);
        put(s, 15, 6, 6, FENCE);
        put(s, 11, 7, 8, Kit.wallBanner("red", "south"));
        // The roof, its dragon to the field, the watch turret.
        int ridge = Nordic.gableRoof(s, 7, 1, 15, 7, 9, false, Nordic.STEEP, Nordic.ROOF, STAVE, Nordic.BOARD, null);
        Nordic.dragonHead(s, 11, ridge + 1, 8, "south", Nordic.SPIRE);
        put(s, 11, 12, 7, Kit.GLASS);
        put(s, 11, 12, 1, Kit.GLASS);
        belfry(s, 11, ridge - 2, 4, ridge + 4, false);
        // Inside: the passage lit, the guard room with a brazier, benches, weapons, a ladder to the lookout.
        hang(s, 11, 3, 3);
        hang(s, 11, 3, 5);
        fill(s, 12, 5, 2, 12, 9, 2, Kit.ladder("south"));
        put(s, 11, 5, 4, FOOTING);
        put(s, 11, 6, 4, campfire("north"));
        Furnish.bench(s, 9, 2, 10, 2, 5, "south", "spruce");
        Furnish.bench(s, 9, 6, 10, 6, 5, "north", "spruce");
        Furnish.weaponRack(s, 14, 5, 6, "west", "spruce");
        Furnish.crate(s, 14, 5, 5, "spruce");
        hang(s, 9, 8, 4);
        hang(s, 13, 8, 5);
        hang(s, 11, ridge - 3, 4);
        s.connect();
        return s.trimSky(3);
    }
}
