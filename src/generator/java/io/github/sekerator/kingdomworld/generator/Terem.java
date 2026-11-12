package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The style "terem": the wooden architecture of the Russian north and of old Muscovy. A building of this style is a cluster
 * of log frames (srub) of different heights, each under a roof of its own and joined by galleries and high porches: steep
 * gables with carved boards, tall tented roofs (shatyor), barrel roofs (bochka) with a pointed ridge and small onion domes on
 * drums; small windows in carved white frames (nalichnik) with shutters, round log ends sticking out at the corners, stone
 * footings. Its fortresses (ostrog) are palisades of upright sharpened logs with a roofed fighting walk behind them, and log
 * towers whose fighting storey juts out over the walls under a tent with a watch lantern on its top. Inside: a whitewashed
 * Russian stove with its warm bed on top, benches along the walls, a long table, the red corner with its candles and
 * towels, a carved chest and a chandelier of candles.
 *
 * <p>The materials are the parts of a look (see {@link Kit}): logs, planks, posts, floors and doors are the timber (oak and
 * spruce, they become the wood of the place); footings are rough stone; the roofs of halls, porches and galleries the wooden
 * roof; tents and onion domes the spires, so that a look may make them green, gilt or shingled; stoves the plaster; towels,
 * carpets and the cloth of the beds the cloth; shutters the shutters. Fixed accents that never change: birch for the
 * carving, white like fresh wood whatever the wood of the place; gold for the finials; mica (stained glass) in the windows.
 *
 * <p>This class holds the data and the shapes of the style; buildings are drawn with them by other classes. Coordinates
 * as everywhere: x from west to east, z from the back (north) to the front (south), y up. A shape draws only inside the
 * box of the structure and leaves out what would fall outside it.
 */
final class Terem {
    // ---------------------------------------------------------------- materials

    /** The logs of the walls, lying along their wall (timber). */
    static final String LOG = "minecraft:spruce_log";
    /** Posts of porches and galleries and the ridge logs: hewn logs, lighter than the walls (timber). */
    static final String POST = "minecraft:stripped_spruce_log";
    static final String PLANKS = "minecraft:spruce_planks";
    static final String STAIRS = "minecraft:spruce_stairs";
    static final String SLAB = "minecraft:spruce_slab";
    static final String FENCE = "minecraft:spruce_fence";
    static final String DOOR = "minecraft:spruce_door";
    /** Floors, tables and benches: oak, lighter than the walls (timber). */
    static final String FLOOR = "minecraft:oak_planks";
    static final String FLOOR_SLAB = "minecraft:oak_slab";
    static final String FLOOR_STAIRS = "minecraft:oak_stairs";
    /** The carving: birch, a fixed accent that stays white whatever the wood of the place. */
    static final String CARVE = "minecraft:birch_planks";
    static final String CARVE_STAIRS = "minecraft:birch_stairs";
    static final String CARVE_SLAB = "minecraft:birch_slab";
    static final String CARVE_FENCE = "minecraft:birch_fence";
    /** The shingles of roofs of halls, porches and galleries (the roof of a look). */
    static final Kit.Roof ROOF = Kit.SHINGLE;
    /** The shingles of tents and onion domes (the spires of a look). */
    static final Kit.Roof DOME = Kit.SLATE;
    /** Footings: rough stone. */
    static final String FOOTING = Kit.ROUGH;
    static final String FOOTING_STAIRS = Kit.ROUGH_STAIRS;
    /** The whitewash of the stoves (plaster). */
    static final String WHITEWASH = Kit.PLASTER;
    /** Mica in the windows: panes of pale yellow glass. */
    static final String MICA = "minecraft:yellow_stained_glass_pane";
    /** Gilding of the finials. */
    static final String GILT = "minecraft:gold_block";
    static final String ROD = "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]";

    /** Plans of the round shapes: a square, an octagon, a disc. */
    static final int SQUARE = 0;
    static final int OCTAGON = 1;
    static final int ROUND = 2;

    /** Kinds of the layers of a body: edged with stairs hanging upside down (it widens), full, edged with stairs (it narrows). */
    private static final int UP = 0;
    private static final int FULL = 1;
    private static final int STEP = 2;

    /** How many layers each ring of a tent rises: the eaves one, the others two (a slope of two in one). */
    static final int[] TENT_RISES = {1, 2};

    private Terem() {
    }

    // ---------------------------------------------------------------- where a shape may draw

    /**
     * The columns a shape may draw in: everywhere, or only before a wall.
     */
    @FunctionalInterface
    interface Area {
        boolean has(int x, int z);
    }

    static final Area EVERYWHERE = (x, z) -> true;

    private static void put(Structure s, Area area, int x, int y, int z, String state) {
        if (s.inside(x, y, z) && area.has(x, z)) {
            s.set(x, y, z, state);
        }
    }

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static boolean solid(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && s.solid(x, y, z);
    }

    private static String stairs(String block, String facing, String half, String shape) {
        return block + "[facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=false]";
    }

    // ---------------------------------------------------------------- plans

    /**
     * Whether a column lies in a plan of the given half-size around its middle: a square, an octagon (the corners cut so
     * that the eight sides are nearly equal) or a disc.
     */
    static boolean inPlan(int dx, int dz, int r, int plan) {
        if (r < 0) {
            return false;
        }
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        if (ax > r || az > r) {
            return false;
        }
        return switch (plan) {
            case OCTAGON -> ax + az <= (r * 141 + 50) / 100;
            case ROUND -> dx * dx + dz * dz <= r * r + r / 2;
            default -> true;
        };
    }

    private static boolean onRim(int dx, int dz, int r, int plan) {
        return inPlan(dx, dz, r, plan) && (!inPlan(dx + 1, dz, r, plan) || !inPlan(dx - 1, dz, r, plan) || !inPlan(dx, dz + 1, r, plan)
                || !inPlan(dx, dz - 1, r, plan));
    }

    /**
     * Stairs on the rim of a plan with their high side to its middle; where the rim turns a corner, a corner stair.
     *
     * @param upside stairs hanging upside down, the edge of a body that widens above
     */
    private static String slope(String block, int dx, int dz, int r, int plan, boolean upside) {
        boolean outX = dx != 0 && !inPlan(dx + Integer.signum(dx), dz, r, plan);
        boolean outZ = dz != 0 && !inPlan(dx, dz + Integer.signum(dz), r, plan);
        String half = upside ? "top" : "bottom";
        if (outX && outZ) {
            String facing = dz > 0 ? "north" : "south";
            String shape = dz > 0 == dx > 0 ? "outer_left" : "outer_right";
            return stairs(block, facing, half, shape);
        }
        if (outX || !outZ && Math.abs(dx) >= Math.abs(dz)) {
            return stairs(block, dx > 0 ? "west" : "east", half, "straight");
        }
        return stairs(block, dz > 0 ? "north" : "south", half, "straight");
    }

    /**
     * One layer of a plan filled with a block.
     */
    static void plate(Structure s, int cx, int y, int cz, int r, int plan, String state) {
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (inPlan(dx, dz, r, plan)) {
                    put(s, cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /**
     * A body built layer by layer from {@code y} up: each layer a plan of the given half-size, filled, its rim edged with
     * stairs as its kind says.
     *
     * @param profile pairs {half-size, kind} from the bottom up
     * @return the layer above the last
     */
    private static int body(Structure s, Area area, int cx, int y, int cz, int[][] profile, int plan, Kit.Roof roof) {
        int layer = y;
        for (int[] step : profile) {
            int r = step[0];
            int kind = step[1];
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!inPlan(dx, dz, r, plan)) {
                        continue;
                    }
                    boolean edge = kind != FULL && r > 0 && onRim(dx, dz, r, plan);
                    put(s, area, cx + dx, layer, cz + dz, edge ? slope(roof.stairs(), dx, dz, r, plan, kind == UP) : roof.block());
                }
            }
            layer++;
        }
        return layer;
    }

    // ---------------------------------------------------------------- log frames

    /**
     * A log frame (srub): four walls of lying logs on the lines {@code x1}, {@code x2}, {@code z1} and {@code z2} from layer
     * {@code y1} to {@code y2}, hollow inside. The logs of the walls along x lie along x, those of the walls along z along z;
     * at the corners they cross course by course in turn, and the crossing log sticks out one block beyond the corner, as the
     * round ends of a frame cut into each other. So the frame takes one block more on every side than its walls.
     */
    static void logWalls(Structure s, int x1, int y1, int z1, int x2, int y2, int z2) {
        logWalls(s, x1, y1, z1, x2, y2, z2, LOG, true);
    }

    /**
     * A log frame of the given log, with or without the ends sticking out at the corners (without them for the upper storey
     * of a tower, whose corners rest on brackets).
     */
    static void logWalls(Structure s, int x1, int y1, int z1, int x2, int y2, int z2, String log, boolean ends) {
        int e = ends ? 1 : 0;
        for (int y = y1; y <= y2; y++) {
            boolean alongX = Math.floorMod(y, 2) == 0;
            for (int x = x1 - (alongX ? e : -1); x <= x2 + (alongX ? e : -1); x++) {
                put(s, x, y, z1, Kit.log(log, "x"));
                put(s, x, y, z2, Kit.log(log, "x"));
            }
            for (int z = z1 - (alongX ? -1 : e); z <= z2 + (alongX ? -1 : e); z++) {
                put(s, x1, y, z, Kit.log(log, "z"));
                put(s, x2, y, z, Kit.log(log, "z"));
            }
        }
    }

    /**
     * An eight-sided log frame (vosmerik) of the given half-size around (cx, cz), from layer {@code y1} to {@code y2}: the
     * logs of the four straight sides lie along them, those of the cut corners cross course by course.
     */
    static void octWalls(Structure s, int cx, int y1, int y2, int cz, int r) {
        for (int y = y1; y <= y2; y++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!onRim(dx, dz, r, OCTAGON)) {
                        continue;
                    }
                    String axis = Math.abs(dx) == r ? "z" : Math.abs(dz) == r ? "x" : Math.floorMod(y, 2) == 0 ? "x" : "z";
                    put(s, cx + dx, y, cz + dz, Kit.log(LOG, axis));
                }
            }
        }
    }

    // ---------------------------------------------------------------- gable roofs

    /**
     * A steep gable roof over a log frame with carved gables. The roof overhangs the walls by one block on every side; its
     * eaves lie at layer {@code y}, the top course of the walls, outside them. The first {@code steep} rows above the eaves
     * rise two layers each (a stair on a full block), the others one; on the ridge lies a log (okhlupen) whose ends stick out
     * beyond the gables. The gables are filled with lying logs, and on each one a carved birch board (prichelina) follows
     * the slopes under the edge of the roof, with a carved towel (polotence) hanging from the ridge.
     *
     * @param x1     the west wall
     * @param z1     the back wall
     * @param x2     the east wall
     * @param z2     the front wall
     * @param alongX whether the ridge runs along x (gables to the west and the east) or along z (gables to the back and to
     *               the street)
     * @return the layer of the ridge log
     */
    static int gableRoof(Structure s, int x1, int z1, int x2, int z2, int y, boolean alongX, int steep) {
        return gableRoof(s, x1, z1, x2, z2, y, alongX, steep, ROOF, null, true);
    }

    /**
     * The high roof of a terem: a carved gable roof whose every row rises two layers, so that its slopes stand twice as
     * steep as they are wide (see {@link #gableRoof(Structure, int, int, int, int, int, boolean, int)} for the rest).
     *
     * @return the layer of the ridge log
     */
    static int steepGable(Structure s, int x1, int z1, int x2, int z2, int y, boolean alongX) {
        return gableRoof(s, x1, z1, x2, z2, y, alongX, Integer.MAX_VALUE);
    }

    /**
     * The same of a given roof; the gables filled with {@code gable} (lying logs of the frame when null), carved or plain.
     *
     * @return the layer of the ridge
     */
    static int gableRoof(Structure s, int x1, int z1, int x2, int z2, int y, boolean alongX, int steep, Kit.Roof roof, String gable,
                         boolean carve) {
        int a1 = (alongX ? x1 : z1) - 1;
        int a2 = (alongX ? x2 : z2) + 1;
        int c1 = (alongX ? z1 : x1) - 1;
        int c2 = (alongX ? z2 : x2) + 1;
        String fill = gable != null ? gable : Kit.log(LOG, alongX ? "z" : "x");
        return gable(s, alongX, a1, a2, c1, c2, y, steep, roof, fill, a1 + 1, a2 - 1, carve, true);
    }

    private static void at(Structure s, boolean alongX, int a, int y, int c, String state) {
        put(s, alongX ? a : c, y, alongX ? c : a, state);
    }

    private static boolean solidAt(Structure s, boolean alongX, int a, int y, int c) {
        return solid(s, alongX ? a : c, y, alongX ? c : a);
    }

    /**
     * A gable roof from its eaves: along {@code a1..a2}, across from the eave {@code c1} to the eave {@code c2}.
     *
     * @param wall1 the gable wall at {@code a1}'s end to fill under the roof, or -1
     * @param wall2 the gable wall at {@code a2}'s end, or -1
     * @return the layer of the ridge
     */
    private static int gable(Structure s, boolean alongX, int a1, int a2, int c1, int c2, int y, int steep, Kit.Roof roof, String fill,
                             int wall1, int wall2, boolean carve, boolean ridgeLog) {
        String up = alongX ? "south" : "east";
        String down = alongX ? "north" : "west";
        int span = c2 - c1;
        int[] lowest = new int[span + 1];
        int h = y;
        int row = 0;
        for (; c1 + row < c2 - row; row++) {
            boolean steepRow = row > 0 && row <= steep;
            if (row > 0) {
                h += steepRow ? 2 : 1;
            }
            int lo = c1 + row;
            int hi = c2 - row;
            for (int a = a1; a <= a2; a++) {
                if (steepRow) {
                    at(s, alongX, a, h - 1, lo, roof.block());
                    at(s, alongX, a, h - 1, hi, roof.block());
                }
                at(s, alongX, a, h, lo, Kit.stairs(roof.stairs(), up));
                at(s, alongX, a, h, hi, Kit.stairs(roof.stairs(), down));
            }
            lowest[row] = steepRow ? h - 1 : h;
            lowest[span - row] = lowest[row];
        }
        int mid = c1 + row == c2 - row ? c1 + row : -1;
        int top = h;
        if (mid >= 0) {
            for (int a = a1; a <= a2; a++) {
                at(s, alongX, a, h, mid, roof.block());
            }
            lowest[mid - c1] = h;
            if (ridgeLog) {
                for (int a = a1 - 1; a <= a2 + 1; a++) {
                    at(s, alongX, a, h + 1, mid, Kit.log(POST, alongX ? "x" : "z"));
                }
            } else {
                for (int a = a1; a <= a2; a++) {
                    at(s, alongX, a, h + 1, mid, Kit.slab(roof.slab()));
                }
            }
            top = h + 1;
        }
        for (int wall : new int[]{wall1, wall2}) {
            if (wall < 0) {
                continue;
            }
            for (int c = c1 + 1; c < c2; c++) {
                for (int hh = y + 1; hh < lowest[c - c1]; hh++) {
                    if (!solidAt(s, alongX, wall, hh, c)) {
                        at(s, alongX, wall, hh, c, fill);
                    }
                }
            }
        }
        if (carve) {
            for (int a : new int[]{a1, a2}) {
                // The bargeboard: under the edge of the roof, a tooth of white wood hanging on the eave side of every row.
                for (int c = c1 + 1; c < c2; c++) {
                    if (c == mid) {
                        continue;
                    }
                    int hh = lowest[c - c1] - 1;
                    if (!solidAt(s, alongX, a, hh, c)) {
                        at(s, alongX, a, hh, c, Kit.upside(CARVE_STAIRS, c < (c1 + c2) / 2.0 ? down : up));
                    }
                }
                // The towel hanging from the ridge.
                if (mid >= 0) {
                    int length = Math.max(1, row / 2);
                    at(s, alongX, a, h - 1, mid, CARVE);
                    for (int i = 1; i <= length; i++) {
                        if (!solidAt(s, alongX, a, h - 1 - i, mid)) {
                            at(s, alongX, a, h - 1 - i, mid, CARVE_FENCE);
                        }
                    }
                }
            }
        }
        return top;
    }

    // ---------------------------------------------------------------- tents and domes

    /**
     * A tall tented roof (shatyor) over a square plan whose eaves are {@code x1..x2, z1..z2} (overhang included, an odd
     * square): rings narrowing by one block on every side, the eaves one layer high and every other ring two (a full block
     * under a stair), so that the tent climbs twice as high as it draws in; with {@code sides} 8 its rings are eight-sided.
     * A tent nine or more wide gets a little window (slukh) under a carved hood on each of its four faces. On its point a
     * needle with a gilt ball, or an onion dome of the given width (3, 5 or 7) on a neck of three layers. Built of the
     * spires and solid.
     *
     * @param y         the layer of the eaves (the layer above the top course of the walls under it)
     * @param sides     4 or 8
     * @param domeWidth the width of the onion on its point, or 0 for the needle
     * @return the layer above the top
     */
    static int tentRoof(Structure s, int x1, int z1, int x2, int z2, int y, int sides, int domeWidth) {
        int plan = sides == 8 ? OCTAGON : SQUARE;
        return tent(s, EVERYWHERE, (x1 + x2) / 2, (z1 + z2) / 2, (x2 - x1) / 2, y, plan, plan, TENT_RISES, domeWidth, -1);
    }

    /**
     * A tent around (cx, cz) of the eave half-size {@code r0}, drawn only in the columns of {@code area} (a porch clips it
     * at its wall): the eave ring of plan {@code eaves} ({@link #SQUARE} or {@link #OCTAGON}), the others of plan
     * {@code plan}, each ring rising as {@code rises} says (the last number for all the rings after it).
     *
     * @param stopAt the half-size at which the tent stops in a flat top (3 wide for a watch lantern: 1), or -1 for a needle
     *               or a dome
     * @return the layer above the top
     */
    static int tent(Structure s, Area area, int cx, int cz, int r0, int y, int eaves, int plan, int[] rises, int domeWidth, int stopAt) {
        int[][] bulb = domeWidth >= 3 ? onionProfile(domeWidth) : null;
        int stop = stopAt >= 0 ? stopAt : bulb != null ? bulb[0][0] - 1 : 0;
        int layer = y;
        int dormers = -1;
        for (int k = 0; r0 - k >= stop; k++) {
            int r = r0 - k;
            int rise = rises[Math.min(k, rises.length - 1)];
            if (r == stop && bulb != null) {
                // the neck of the dome: long enough to show the bulb swelling over it
                rise = Math.max(rise, 3);
            }
            if (k == 2 && r0 >= 4 && rises[Math.min(1, rises.length - 1)] >= 2) {
                dormers = layer;
            }
            for (int j = 0; j < rise; j++) {
                int kind = j == rise - 1 && r > stop ? STEP : FULL;
                layer = body(s, area, cx, layer, cz, new int[][]{{r, kind}}, k == 0 ? eaves : plan, DOME);
            }
        }
        if (dormers >= 0) {
            // Little windows (slukhi) on the four faces, each under a carved hood.
            int d = r0 - 1;
            for (int[] p : new int[][]{{d, 0}, {-d, 0}, {0, d}, {0, -d}}) {
                put(s, area, cx + p[0], dormers, cz + p[1], MICA);
                put(s, area, cx + p[0], dormers + 1, cz + p[1], Kit.slab(CARVE_SLAB));
            }
        }
        if (stopAt >= 0) {
            return layer;
        }
        if (bulb != null) {
            layer = body(s, area, cx, layer, cz, bulb, ROUND, DOME);
            return area.has(cx, cz) ? finial(s, cx, layer, cz, domeWidth >= 7 ? 2 : domeWidth >= 5 ? 1 : 0) : layer;
        }
        return area.has(cx, cz) ? finial(s, cx, layer, cz, 1) : layer;
    }

    /**
     * The profile of the bulb of an onion dome of an odd width from 3 up: from a neck two blocks narrower on every side it
     * swells in two layers of stairs hanging upside down to its widest, rounds over its shoulder (from 9 wide a layer
     * fuller), draws in by stairs to a short concave point one block wide, so that its outline is that of an onion and not
     * of a cone; 7 wide it is 8 layers high, 9 wide 10.
     *
     * @return pairs {half-size, kind} from the bottom up; the neck under it is one block narrower than its first layer
     */
    static int[][] onionProfile(int width) {
        int r = Math.max(1, (width - 1) / 2);
        if (r == 1) {
            return new int[][]{{1, UP}, {1, FULL}, {1, STEP}, {0, FULL}};
        }
        if (r == 2) {
            return new int[][]{{2, UP}, {2, FULL}, {2, STEP}, {1, STEP}, {0, FULL}};
        }
        // From a narrow neck the bulb swells in two layers to its widest, rounds over its shoulder like a ball and draws in
        // to a short concave point.
        List<int[]> steps = new ArrayList<>();
        steps.add(new int[]{r - 1, UP});
        steps.add(new int[]{r, UP});
        steps.add(new int[]{r, FULL});
        steps.add(new int[]{r, STEP});
        if (r >= 4) {
            steps.add(new int[]{r - 1, FULL});
        }
        for (int i = r - 1; i >= 2; i--) {
            steps.add(new int[]{i, STEP});
        }
        steps.add(new int[]{1, FULL});
        steps.add(new int[]{1, STEP});
        steps.add(new int[]{0, FULL});
        return steps.toArray(new int[0][]);
    }

    /**
     * An onion dome on its drum, standing on layer {@code y} around (cx, cz): a slender drum of planks as wide as the neck
     * (1 block under a dome 3 wide, 3 under 5 and 7, 5 under 9), one or two layers high with a white carved band on its top,
     * a layer of neck of the spires, the bulb (see {@link #onionProfile}) and a gilt finial: a lightning rod on a dome 3
     * wide, a gilt ball and rod on one 5 wide, a gilt ball and a cross of end rods on wider ones. Solid.
     *
     * @param width the width of the bulb at its widest: 3, 5, 7, 9...
     * @return the layer above the finial
     */
    static int onionDome(Structure s, int cx, int y, int cz, int width) {
        return onionDome(s, cx, y, cz, width, width >= 7 ? 2 : 1, PLANKS);
    }

    /**
     * The same with a drum of the given height and block (0 for no drum: the neck then stands on {@code y}).
     */
    static int onionDome(Structure s, int cx, int y, int cz, int width, int drum, String drumBlock) {
        int[][] bulb = onionProfile(width);
        int neck = Math.max(0, bulb[0][0] - 1);
        int drumR = neck;
        int layer = y;
        for (int i = 0; i < drum; i++) {
            plate(s, cx, layer, cz, drumR, ROUND, i == drum - 1 && drumR > 0 ? CARVE : drumBlock);
            layer++;
        }
        plate(s, cx, layer, cz, neck, ROUND, DOME.block());
        layer++;
        layer = body(s, EVERYWHERE, cx, layer, cz, bulb, ROUND, DOME);
        return finial(s, cx, layer, cz, width >= 7 ? 2 : width >= 5 ? 1 : 0);
    }

    /**
     * A finial on the point of a dome or a tent: 0 a lightning rod; 1 a gilt ball with a rod; 2 a gilt ball with a cross of
     * end rods.
     *
     * @return the layer above it
     */
    static int finial(Structure s, int x, int y, int z, int size) {
        if (size <= 0) {
            put(s, x, y, z, ROD);
            return y + 1;
        }
        put(s, x, y, z, GILT);
        if (size == 1) {
            put(s, x, y + 1, z, ROD);
            return y + 2;
        }
        for (int i = 1; i <= 3; i++) {
            put(s, x, y + i, z, "minecraft:end_rod[facing=up]");
        }
        put(s, x - 1, y + 2, z, "minecraft:end_rod[facing=west]");
        put(s, x + 1, y + 2, z, "minecraft:end_rod[facing=east]");
        return y + 4;
    }

    // ---------------------------------------------------------------- barrel roofs

    /**
     * The cross-section of a barrel roof of an odd width from 5 up: it springs out of the walls in stairs hanging upside
     * down, swells, narrows in stairs and ends in a pointed ridge.
     */
    static int[][] barrelProfile(int width) {
        int r = Math.max(2, (width - 1) / 2);
        if (r == 2) {
            return new int[][]{{2, UP}, {2, STEP}, {1, STEP}, {0, FULL}};
        }
        List<int[]> steps = new ArrayList<>();
        steps.add(new int[]{r, UP});
        steps.add(new int[]{r, FULL});
        for (int i = r; i >= 2; i--) {
            steps.add(new int[]{i, STEP});
        }
        steps.add(new int[]{1, FULL});
        steps.add(new int[]{1, STEP});
        steps.add(new int[]{0, FULL});
        return steps.toArray(new int[0][]);
    }

    /**
     * A barrel roof (bochka) whose ridge runs along x from {@code a1} to {@code a2} (or along z), its middle on the line
     * {@code c} across: in section it springs out of the walls, swells and rises to a pointed ridge, like a keel. It is a
     * shell, a vault seen from below; its ends are closed one block inside them ({@code a1 + 1}, {@code a2 - 1}) by a
     * pediment of {@code gable} in the shape of the section, so that the shell frames it like a kokoshnik, and a carved
     * birch board follows the arch inside the frame. {@code a1} and {@code a2} are the overhanging ends: over a log frame
     * they are the columns of its log ends, and its end walls fall on the pediments.
     *
     * @param c      the middle across: z when the ridge runs along x, x when it runs along z
     * @param y      the layer of the eaves, the top course of the walls under it
     * @param gable  the block of the pediments, or null for lying logs
     * @param width  the width of the section (odd, 5 or more): the walls under it stand one block inside its edges
     * @param close1 whether to close the end at {@code a1}
     * @param close2 whether to close the end at {@code a2}
     * @return the layer above the ridge
     */
    static int barrelRoof(Structure s, int a1, int a2, int c, int y, int width, boolean alongX, Kit.Roof roof, String gable,
                          boolean close1, boolean close2) {
        int[][] profile = barrelProfile(width);
        if (gable == null) {
            gable = Kit.log(LOG, alongX ? "z" : "x");
        }
        String toPlus = alongX ? "south" : "east";
        String toMinus = alongX ? "north" : "west";
        int layer = y;
        for (int[] step : profile) {
            int r = step[0];
            int kind = step[1];
            for (int a = a1; a <= a2; a++) {
                boolean pediment = close1 && a == a1 + 1 || close2 && a == a2 - 1;
                for (int d = -r; d <= r; d++) {
                    boolean rim = Math.abs(d) == r;
                    String state;
                    if (rim) {
                        state = kind == FULL || r == 0 ? roof.block()
                                : kind == UP ? Kit.upside(roof.stairs(), d > 0 ? toMinus : toPlus)
                                : Kit.stairs(roof.stairs(), d > 0 ? toMinus : toPlus);
                    } else if (pediment) {
                        state = gable;
                    } else {
                        continue;
                    }
                    at(s, alongX, a, layer, c + d, state);
                }
            }
            // At a closed end the carved board follows the arch one block inside the shell.
            for (int a : new int[]{close1 ? a1 : Integer.MIN_VALUE, close2 ? a2 : Integer.MIN_VALUE}) {
                if (a == Integer.MIN_VALUE || r < 1) {
                    continue;
                }
                for (int d = -(r - 1); d <= r - 1; d += Math.max(1, 2 * (r - 1))) {
                    if (!solidAt(s, alongX, a, layer, c + d)) {
                        at(s, alongX, a, layer, c + d, d == 0 ? Kit.topSlab(CARVE_SLAB) : Kit.upside(CARVE_STAIRS, d > 0 ? toPlus : toMinus));
                    }
                }
            }
            layer++;
        }
        return layer;
    }

    // ---------------------------------------------------------------- windows, porches, galleries

    /**
     * A small window in a log wall looking the way of {@code facing}, its lowest pane at (x, y, z) in the wall and as wide as
     * {@code width} (odd) around it: panes of mica in the wall and, one block out before the wall, a carved birch frame
     * (nalichnik): a sill of upside-down stairs under it, a little pediment over it (a gable of stairs with a slab on its
     * point) and at its sides shutters standing open against the wall or, without them, carved posts.
     */
    static void window(Structure s, int x, int y, int z, String facing, int width, int height, boolean shutters) {
        window(s, x, y, z, facing, width, height, shutters, MICA);
    }

    /**
     * The same with panes of the given glass.
     */
    static void window(Structure s, int x, int y, int z, String facing, int width, int height, boolean shutters, String glass) {
        int[] out = Furnish.step(facing);
        String plus = Furnish.left(facing);
        String minus = Furnish.opposite(plus);
        int[] along = Furnish.step(plus);
        int hw = (width - 1) / 2;
        for (int u = -hw; u <= hw; u++) {
            for (int v = 0; v < height; v++) {
                put(s, x + along[0] * u, y + v, z + along[1] * u, glass);
            }
        }
        int fx = x + out[0];
        int fz = z + out[1];
        for (int u = -hw - 1; u <= hw + 1; u++) {
            put(s, fx + along[0] * u, y - 1, fz + along[1] * u, Kit.upside(CARVE_STAIRS, Furnish.opposite(facing)));
        }
        for (int side = -1; side <= 1; side += 2) {
            int u = side * (hw + 1);
            for (int v = 0; v < height; v++) {
                put(s, fx + along[0] * u, y + v, fz + along[1] * u, shutters ? Kit.shutter(Kit.SHUTTER, facing) : CARVE_FENCE);
            }
        }
        for (int k = 0; ; k++) {
            int lo = -hw - 1 + k;
            int hi = hw + 1 - k;
            int yy = y + height + k;
            if (lo == hi) {
                put(s, fx + along[0] * lo, yy, fz + along[1] * lo, Kit.slab(CARVE_SLAB));
                break;
            }
            if (lo > hi) {
                break;
            }
            put(s, fx + along[0] * lo, yy, fz + along[1] * lo, Kit.stairs(CARVE_STAIRS, plus));
            put(s, fx + along[0] * hi, yy, fz + along[1] * hi, Kit.stairs(CARVE_STAIRS, minus));
            for (int u = lo + 1; u < hi; u++) {
                put(s, fx + along[0] * u, yy, fz + along[1] * u, CARVE);
            }
        }
    }

    /**
     * A high porch (kryltso) before a door in a wall looking the way of {@code facing}: the door itself at (x, y, z), a landing
     * three blocks wide and three deep before it at the level of its threshold, standing on posts, with a railing of carved
     * birch; a flight of stairs two blocks wide along the wall from the street at layer {@code ground} up to the side of the
     * landing, on a sawn stringer, with balusters; and over the landing, on two posts, a roof of its own with its eaves three
     * layers above the landing: a small steep tent with a gilt needle (cut off at the wall), or a barrel roof whose carved
     * pediment looks out. A lantern hangs under the roof. It takes the cells from one to four blocks out of the wall, three
     * to the sides of the door and the flight {@code y - ground} more along the wall; the roof may cut into the eaves of
     * the house above it.
     *
     * @param y      the layer of the lower half of the door, where one stands on the landing
     * @param side   +1 when the flight runs to the left of {@code facing} (east of a door that looks south), -1 to the right
     * @param barrel a barrel roof instead of the tent
     */
    static void porch(Structure s, int x, int y, int z, String facing, int ground, int side, boolean barrel) {
        int[] out = Furnish.step(facing);
        int[] al = Furnish.step(Furnish.left(facing));
        String toLanding = Furnish.left(facing);
        if (side > 0) {
            toLanding = Furnish.opposite(toLanding);
        }
        String away = Furnish.opposite(toLanding);
        Local p = (u, v) -> new int[]{x + al[0] * u + out[0] * v, z + al[1] * u + out[1] * v};
        s.door(x, y, z, DOOR, Furnish.opposite(facing), "left");
        // The landing on its posts.
        for (int u = -1; u <= 1; u++) {
            for (int v = 1; v <= 3; v++) {
                int[] c = p.at(u, v);
                put(s, c[0], y - 1, c[1], FLOOR);
            }
        }
        for (int u = -1; u <= 1; u += 2) {
            for (int v = 1; v <= 3; v += 2) {
                int[] c = p.at(u, v);
                for (int yy = ground; yy < y - 1; yy++) {
                    put(s, c[0], yy, c[1], Kit.log(POST, "y"));
                }
            }
            int[] c = p.at(u, 3);
            for (int yy = y; yy <= y + 2; yy++) {
                put(s, c[0], yy, c[1], Kit.log(POST, "y"));
            }
        }
        // Its railing: the front and the side away from the flight.
        int[] front = p.at(0, 3);
        put(s, front[0], y, front[1], CARVE_FENCE);
        for (int v = 1; v <= 2; v++) {
            int[] c = p.at(-side, v);
            put(s, c[0], y, c[1], CARVE_FENCE);
        }
        // The flight along the wall down to the street.
        int steps = y - ground;
        for (int i = 0; i < steps; i++) {
            int u = side * (2 + i);
            int layer = y - 1 - i;
            for (int v = 1; v <= 2; v++) {
                int[] c = p.at(u, v);
                put(s, c[0], layer, c[1], Kit.stairs(FLOOR_STAIRS, toLanding));
                if (layer - 1 >= ground) {
                    put(s, c[0], layer - 1, c[1], Kit.upside(FLOOR_STAIRS, away));
                }
            }
            int[] r = p.at(u, 3);
            put(s, r[0], layer + 1, r[1], CARVE_FENCE);
        }
        // The roof over the landing.
        int[] mid = p.at(0, 2);
        Area before = (px, pz) -> (px - x) * out[0] + (pz - z) * out[1] >= 1;
        int[] lamp = p.at(0, 2);
        if (barrel) {
            boolean alongX = out[0] != 0;
            int[] near = p.at(0, 1);
            int[] far = p.at(0, 4);
            int a1 = alongX ? Math.min(near[0], far[0]) : Math.min(near[1], far[1]);
            int a2 = alongX ? Math.max(near[0], far[0]) : Math.max(near[1], far[1]);
            boolean farIsHigh = alongX ? far[0] > near[0] : far[1] > near[1];
            int ridge = barrelRoof(s, a1, a2, alongX ? mid[1] : mid[0], y + 3, 5, alongX, ROOF, null, !farIsHigh, farIsHigh);
            // the lantern hangs on a chain from the ridge of the vault
            for (int yy = y + 3; yy < ridge - 1; yy++) {
                put(s, lamp[0], yy, lamp[1], Kit.chain());
            }
            put(s, lamp[0], y + 2, lamp[1], Kit.hangingLantern());
            // the barrel's shell rests on a beam over the posts
            for (int u = -1; u <= 1; u++) {
                int[] c = p.at(u, 3);
                put(s, c[0], y + 3, c[1], Kit.log(POST, alongX ? "z" : "x"));
            }
        } else {
            tent(s, before, mid[0], mid[1], 2, y + 3, SQUARE, SQUARE, new int[]{1, 2, 2}, 0, -1);
            put(s, lamp[0], y + 2, lamp[1], Kit.hangingLantern());
        }
    }

    @FunctionalInterface
    private interface Local {
        int[] at(int u, int v);
    }

    /**
     * A covered gallery on posts from (x1, z1) to (x2, z2), its length along x or z: a floor of planks at layer {@code y - 1}
     * so that one walks at {@code y}, posts of hewn logs every two blocks on both edges from the ground ({@code ground}) up
     * to the roof, a railing of carved birch between them, beams along the edges and a small gable roof along it, with
     * lanterns under the ridge. Its ends are open, to join the buildings at both ends; its roof reaches one block beyond
     * its edges and rises to two layers above the beams (three wide) or more.
     *
     * @param y      the layer one walks at (at least three wide across, the posts and railing on its two edges)
     * @param ground the layer the posts stand on
     * @param alongX whether its length runs along x
     */
    static void gallery(Structure s, int x1, int z1, int x2, int z2, int y, int ground, boolean alongX) {
        int a1 = alongX ? x1 : z1;
        int a2 = alongX ? x2 : z2;
        int c1 = alongX ? z1 : x1;
        int c2 = alongX ? z2 : x2;
        for (int a = a1; a <= a2; a++) {
            for (int c = c1; c <= c2; c++) {
                at(s, alongX, a, y - 1, c, FLOOR);
            }
            for (int c : new int[]{c1, c2}) {
                boolean post = (a - a1) % 2 == 0 || a == a2;
                if (post) {
                    for (int yy = ground; yy <= y + 2; yy++) {
                        if (yy != y - 1) {
                            at(s, alongX, a, yy, c, Kit.log(POST, "y"));
                        }
                    }
                } else {
                    at(s, alongX, a, y, c, CARVE_FENCE);
                }
                at(s, alongX, a, y + 3, c, Kit.log(POST, alongX ? "x" : "z"));
            }
        }
        gable(s, alongX, a1, a2, c1 - 1, c2 + 1, y + 3, 0, ROOF, null, -1, -1, false, false);
        int mid = (c1 + c2) / 2;
        for (int a = a1 + 1; a < a2; a += 3) {
            at(s, alongX, a, y + 2 + (c2 - c1) / 2, mid, Kit.hangingLantern());
        }
    }

    // ---------------------------------------------------------------- fortresses

    /**
     * A palisade (tyn) of the ostrog along x or z: upright logs from layer {@code y} up, {@code height} high, sharpened in
     * pairs at the top; behind it, on the side {@code inward}, a fighting walk two blocks wide five layers below the top on
     * posts, under a lean-to roof (zaborola) that rises against the logs so that their points stand above it, with loopholes
     * at the height of the eye and lanterns under the roof. A ladder leads up to the walk at the first end.
     *
     * @param inward north, south, east or west: the side of the walk (the inside of the fortress)
     */
    static void palisade(Structure s, int x1, int z1, int x2, int z2, int y, int height, String inward) {
        boolean alongX = z1 == z2;
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int c = alongX ? z1 : x1;
        int[] in = Furnish.step(inward);
        int dc = alongX ? in[1] : in[0];
        String plus = alongX ? "east" : "south";
        String minus = alongX ? "west" : "north";
        String toWall = Furnish.opposite(inward);
        int floor = y + height - 5;
        for (int a = a1; a <= a2; a++) {
            for (int yy = y; yy < y + height; yy++) {
                at(s, alongX, a, yy, c, Kit.log(LOG, "y"));
            }
            at(s, alongX, a, y + height, c, Kit.stairs(STAIRS, (a - a1) % 2 == 0 ? plus : minus));
            if ((a - a1) % 3 == 1) {
                at(s, alongX, a, floor + 2, c, FENCE);
            }
            // the walk and its roof
            at(s, alongX, a, floor, c + dc, PLANKS);
            at(s, alongX, a, floor, c + 2 * dc, PLANKS);
            at(s, alongX, a, floor + 4, c + dc, Kit.stairs(ROOF.stairs(), toWall));
            at(s, alongX, a, floor + 3, c + 2 * dc, Kit.stairs(ROOF.stairs(), toWall));
            at(s, alongX, a, floor + 2, c + 3 * dc, Kit.stairs(ROOF.stairs(), toWall));
            if ((a - a1) % 3 == 0 || a == a2) {
                for (int yy = y; yy < floor; yy++) {
                    at(s, alongX, a, yy, c + 2 * dc, Kit.log(POST, "y"));
                }
                for (int yy = floor + 1; yy <= floor + 2; yy++) {
                    at(s, alongX, a, yy, c + 2 * dc, Kit.log(POST, "y"));
                }
            }
            if ((a - a1) % 4 == 2) {
                at(s, alongX, a, floor + 3, c + dc, Kit.hangingLantern());
            }
        }
        for (int yy = y; yy <= floor; yy++) {
            at(s, alongX, a1 + 1, yy, c + dc, Kit.ladder(inward));
        }
    }

    /**
     * A log tower of the ostrog around (cx, cz), standing on layer {@code y}: a footing of rough stone with a floor of planks,
     * walls of logs (square with the log ends sticking out, or eight-sided) {@code height} layers high, then the fighting
     * storey jutting out one block on every side over brackets, its walls three logs high with loopholes, and over it a
     * tent whose eaves reach out one block more. On the tent's top a watch lantern (smotrilnya: an open box on four posts
     * with a lantern and a little tent of its own), an onion dome or a gilt needle. Inside, a ladder along the back wall up to
     * the fighting storey and a lantern on each storey. A door looking {@code door} at the foot, unless null. It takes
     * {@code width + 4} blocks across at the eaves of the tent; a palisade meets its walls.
     *
     * @param width odd, 5 or more
     * @param top   1 for the watch lantern, 0 for a needle, 3 or 5 for an onion dome of that width
     * @return the layer of the floor of the fighting storey (one stands at the layer above it)
     */
    static int tower(Structure s, int cx, int y, int cz, int width, int height, boolean eight, int top, String door) {
        int h = (width - 1) / 2;
        int plan = eight ? OCTAGON : SQUARE;
        plate(s, cx, y, cz, h + 1, plan, FOOTING);
        plate(s, cx, y, cz, h - 1, plan, FLOOR);
        int floor = y + height;
        if (eight) {
            octWalls(s, cx, y + 1, floor - 1, cz, h);
        } else {
            logWalls(s, cx - h, y + 1, cz - h, cx + h, floor - 1, cz + h);
        }
        for (int dz = -h - 1; dz <= h + 1; dz++) {
            for (int dx = -h - 1; dx <= h + 1; dx++) {
                if (inPlan(dx, dz, h + 1, plan) && !inPlan(dx, dz, h, plan)) {
                    put(s, cx + dx, floor, cz + dz, slope(STAIRS, dx, dz, h + 1, plan, true));
                }
            }
        }
        plate(s, cx, floor, cz, h, plan, FLOOR);
        if (eight) {
            octWalls(s, cx, floor + 1, floor + 3, cz, h + 1);
        } else {
            logWalls(s, cx - h - 1, floor + 1, cz - h - 1, cx + h + 1, floor + 3, cz + h + 1, LOG, false);
        }
        // Loopholes at the height of the eye.
        for (int dz = -h - 1; dz <= h + 1; dz++) {
            for (int dx = -h - 1; dx <= h + 1; dx++) {
                if (onRim(dx, dz, h + 1, plan) && (Math.abs(dx) == h + 1 && Math.abs(dz) < h && dz % 2 == 0
                        || Math.abs(dz) == h + 1 && Math.abs(dx) < h && dx % 2 == 0)) {
                    put(s, cx + dx, floor + 2, cz + dz, Structure.AIR);
                }
            }
        }
        int layer;
        if (top == 1) {
            layer = tent(s, EVERYWHERE, cx, cz, h + 2, floor + 4, plan, plan, TENT_RISES, 0, 1);
            watch(s, cx, layer, cz);
        } else {
            tent(s, EVERYWHERE, cx, cz, h + 2, floor + 4, plan, plan, TENT_RISES, top >= 3 ? top : 0, -1);
        }
        for (int yy = y + 1; yy <= floor; yy++) {
            put(s, cx, yy, cz - h + 1, Kit.ladder("south"));
        }
        put(s, cx + 1, floor - 1, cz, Kit.hangingLantern());
        put(s, cx + 1, floor + 3, cz, Kit.hangingLantern());
        if (door != null) {
            int[] d = Furnish.step(door);
            int dx = cx + d[0] * h;
            int dz = cz + d[1] * h;
            s.door(dx, y + 1, dz, DOOR, Furnish.opposite(door), "left");
        }
        return floor;
    }

    /**
     * A watch lantern (smotrilnya) on the flat top of a tent at layer {@code y}: four posts at the corners of a square three
     * wide, a lantern between them and a small tent of its own with a gilt needle.
     *
     * @return the layer above its top
     */
    static int watch(Structure s, int cx, int y, int cz) {
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                put(s, cx + dx, y, cz + dz, Kit.log(POST, "y"));
                put(s, cx + dx, y + 1, cz + dz, Kit.log(POST, "y"));
            }
        }
        put(s, cx, y, cz, Kit.lantern());
        return tent(s, EVERYWHERE, cx, cz, 2, y + 2, SQUARE, SQUARE, new int[]{1, 2}, 0, -1);
    }

    // ---------------------------------------------------------------- the stove and the furniture

    /**
     * A whitewashed Russian stove whose mouth looks the way of {@code facing}: a body of plaster three wide and three deep,
     * two layers high, standing on the floor at layer {@code y} with (x, z) the middle of its front; its mouth a fire behind
     * an iron grate in the middle of the front, the hood over the front row with pots and a candle on it, and the stack from
     * the hood up to layer {@code top}; on the two back rows the warm bed (lezhanka) under cloth; along its side a bench.
     */
    static void stove(Structure s, int x, int y, int z, String facing, int top) {
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        for (int u = -1; u <= 1; u++) {
            for (int w = 0; w >= -2; w--) {
                int px = x + a[0] * u + f[0] * w;
                int pz = z + a[1] * u + f[1] * w;
                put(s, px, y, pz, WHITEWASH);
                put(s, px, y + 1, pz, WHITEWASH);
                put(s, px, y + 2, pz, w == 0 ? WHITEWASH : Kit.CLOTH_CARPET);
            }
        }
        put(s, x, y + 1, z, Kit.BARS);
        put(s, x - f[0], y + 1, z - f[1], "minecraft:campfire[facing=" + facing + ",lit=true,signal_fire=false,waterlogged=false]");
        for (int yy = y + 3; yy <= top; yy++) {
            put(s, x, yy, z, WHITEWASH);
        }
        put(s, x + a[0], y + 3, z + a[1], "minecraft:flower_pot");
        put(s, x - a[0], y + 3, z - a[1], Furnish.candles(2));
        for (int w = 0; w >= -2; w--) {
            put(s, x + a[0] * 2 + f[0] * w, y, z + a[1] * 2 + f[1] * w, Kit.slab(FLOOR_SLAB));
        }
    }

    /**
     * A bench along a wall (lavka): a row of slabs of the timber from (x1, z1) to (x2, z2) at layer {@code y}.
     */
    static void lavka(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                put(s, x, y, z, Kit.slab(FLOOR_SLAB));
            }
        }
    }

    /**
     * The red corner of a room, in the corner cell (x, z) whose walls lie the ways {@code wall1} and {@code wall2}: a shelf
     * two layers above the floor {@code y} with candles on it, and towels of cloth hanging under it on both walls.
     */
    static void redCorner(Structure s, int x, int y, int z, String wall1, String wall2) {
        put(s, x, y + 2, z, Kit.topSlab(FLOOR_SLAB));
        put(s, x, y + 3, z, Furnish.candles(3));
        int[] b1 = Furnish.step(Furnish.opposite(wall2));
        int[] b2 = Furnish.step(Furnish.opposite(wall1));
        put(s, x + b1[0], y + 2, z + b1[1], Kit.wallBanner("red", Furnish.opposite(wall1)));
        put(s, x + b2[0], y + 2, z + b2[1], Kit.wallBanner("red", Furnish.opposite(wall2)));
    }

    /**
     * The one chest of a household against a wall, its lid looking {@code facing}, with a carpet before it and a towel of
     * cloth hanging over it on the wall behind.
     */
    static void chestPlace(Structure s, int x, int y, int z, String facing) {
        put(s, x, y, z, Kit.chest(facing));
        int[] f = Furnish.step(facing);
        put(s, x + f[0], y, z + f[1], Kit.CLOTH_CARPET);
        put(s, x, y + 2, z, Kit.wallBanner("red", facing));
    }

    /**
     * A long feast table from (x1, z1) to (x2, z2) at layer {@code y}: upper slabs of the timber under a white cloth, candles
     * at its ends; with {@code benches}, benches along both long sides.
     */
    static void feastTable(Structure s, int x1, int z1, int x2, int z2, int y, boolean benches) {
        boolean alongX = Math.abs(x2 - x1) >= Math.abs(z2 - z1);
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int c = alongX ? z1 : x1;
        for (int a = a1; a <= a2; a++) {
            at(s, alongX, a, y, c, Kit.topSlab(FLOOR_SLAB));
            at(s, alongX, a, y + 1, c, a == a1 || a == a2 ? Furnish.candles(3) : "minecraft:white_carpet");
            if (benches) {
                at(s, alongX, a, y, c - 1, Kit.slab(FLOOR_SLAB));
                at(s, alongX, a, y, c + 1, Kit.slab(FLOOR_SLAB));
            }
        }
    }

    /**
     * A chandelier of candles (horos) hanging on a chain from the ceiling at layer {@code top} down {@code drop} layers: a hub
     * with four carved arms, three candles on each arm.
     */
    static void candleChandelier(Structure s, int x, int z, int top, int drop) {
        int hub = top - drop;
        for (int yy = hub + 1; yy <= top; yy++) {
            put(s, x, yy, z, Kit.chain());
        }
        put(s, x, hub, z, CARVE_FENCE);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            put(s, x + d[0], hub, z + d[1], CARVE_FENCE);
            put(s, x + d[0], hub + 1, z + d[1], Furnish.candles(3));
        }
    }
}
