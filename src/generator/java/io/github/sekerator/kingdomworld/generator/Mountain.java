package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The style "mountain": the hold of a people of miners and smiths, a fortress of heavy stone that seems cut out of the
 * rock it stands on. Its buildings are massive and low, their masses stepped back in tiers: walls two blocks thick that
 * slope outwards at their foot (a battered foot of rough stone), piers that stand out of them between the bays, bands of
 * dark stone; thick towers on such a foot that carry a heavy crenellated parapet with dark caps on one or two rings of
 * corbels and so grow wider at the top again, a brazier glowing on their flat top or a low pyramid of stone rising behind
 * the merlons; few and small windows deep in the walls, deep doors set in receding arches with an iron grille over them.
 * Roofs are low and of stone: slabs that climb half a block for every block inwards over a heavy cornice, their eaves on
 * beams of timber sticking out of the wall, their gables coped and crowned with a rune; chimneys are stout stacks that
 * smoke. Buildings stand on terraces reached by broad flights of stairs between sloping cheek walls; their walls carry the
 * carved faces of crowned and bearded forefathers with glowing eyes, friezes and standing stones of runes, long hangings of
 * cloth. Accents are those of a forge: dark stone, gold, the orange glow of embers, windows of amber glass, hoods of copper.
 * Inside: stone floors, great forges under copper hoods, wide hearths, stone tables, casks lying in racks, beds set in stone
 * alcoves, pillars with flared bases and capitals.
 *
 * <p>The materials are the parts of a look (see {@link Kit}): the walls and piers are the wall (stone bricks: stone
 * bricks, andesite or cobbled deepslate in a look of this style); batters, footings, stacks and the fill of terraces the
 * rough stone; cornices, corbels, copings, lintels, sills and floors the trim; keystones the carved stone; the roofs of
 * halls and houses the wooden roof of the look (it becomes deepslate tiles, cobblestone or spruce); the low pyramids of
 * towers the spires (deepslate, blackstone or copper); hangings, carpets and the covers of beds the cloth; beams, brackets,
 * floors of upper storeys, doors, casks and benches the timber. Fixed accents that never change: polished blackstone
 * (bricks for bands, quoins, merlon caps, hearths and the niches of the faces, the chiseled block and the gilded one for
 * runes), natural stone for the carved faces, gold, shroomlight and magma for embers, waxed copper for the hoods of forges,
 * iron bars, amber (orange) glass.
 *
 * <p>This class holds the data and the shapes of the style; buildings are drawn with them by other classes. Coordinates
 * as everywhere: x from west to east, z from the back (north) to the front (south), y up. A shape draws only inside the
 * box of the structure and leaves out what would fall outside it. Where a shape takes a {@code facing}, it is the way the
 * shape looks out (the outside of a wall, the room before a hearth); "u" then runs along it to the left of that way (to
 * the east for south) and "v" outwards along it.
 */
final class Mountain {
    // ---------------------------------------------------------------- materials

    /** The ashlar of the walls (the wall of a look). */
    static final String WALL = Kit.WALL;
    static final String WALL_STAIRS = Kit.WALL_STAIRS;
    static final String WALL_SLAB = Kit.WALL_SLAB;
    static final String WALL_FENCE = Kit.WALL_FENCE;
    /** Batters, footings, the fill of terraces and the rock (rough stone). */
    static final String ROUGH = Kit.ROUGH;
    static final String ROUGH_STAIRS = Kit.ROUGH_STAIRS;
    static final String ROUGH_SLAB = Kit.ROUGH_SLAB;
    static final String ROUGH_FENCE = Kit.ROUGH_FENCE;
    /** Cornices, corbels, lintels, sills, copings, floors and the carved faces (trim). */
    static final String TRIM = Kit.TRIM;
    static final String TRIM_STAIRS = Kit.TRIM_STAIRS;
    static final String TRIM_SLAB = Kit.TRIM_SLAB;
    /** Keystones and the carved stones of a wall (carved stone). */
    static final String CARVED = Kit.CARVED;
    /** The low stone roofs of halls and houses (the roof of a look: deepslate tiles, cobblestone or spruce in this style). */
    static final Kit.Roof ROOF = Kit.SHINGLE;
    /** The low pyramids over towers (the spires of a look: deepslate, blackstone or copper in this style). */
    static final Kit.Roof HELM = Kit.SLATE;
    /** Hangings, carpets and covers (cloth). */
    static final String CLOTH = Kit.CLOTH;
    static final String CARPET = Kit.CLOTH_CARPET;
    /** The wood of furniture given to {@link Furnish} (timber). */
    static final String WOOD = "spruce";
    /** Beams and brackets: hewn logs (timber). */
    static final String BEAM = "minecraft:stripped_spruce_log";
    /** Casks and posts: logs (timber). */
    static final String LOG = "minecraft:spruce_log";
    static final String PLANKS = "minecraft:spruce_planks";
    static final String STAIRS = "minecraft:spruce_stairs";
    static final String SLAB = "minecraft:spruce_slab";
    static final String FENCE = "minecraft:spruce_fence";
    static final String DOOR = "minecraft:spruce_door";
    /** Floors of the storeys that stand on the ground: dressed stone (trim). Upper storeys have floors of planks. */
    static final String FLOOR = TRIM;

    /** Dark stone: bands, quoins, hearths, the stelae of runes (a fixed accent). */
    static final String DARK = "minecraft:polished_blackstone_bricks";
    static final String DARK_STAIRS = "minecraft:polished_blackstone_brick_stairs";
    static final String DARK_SLAB = "minecraft:polished_blackstone_brick_slab";
    static final String DARK_WALL = "minecraft:polished_blackstone_brick_wall";
    /** A carved rune (a fixed accent). */
    static final String RUNE = "minecraft:chiseled_polished_blackstone";
    /** Dark stone flecked with gold (a fixed accent). */
    static final String GILDED = "minecraft:gilded_blackstone";
    static final String GOLD = "minecraft:gold_block";
    /** Embers: the glowing heart of braziers and forges and the eyes of the carved faces (a fixed accent). */
    static final String EMBER = "minecraft:shroomlight";
    static final String MAGMA = "minecraft:magma_block";
    /** The hoods of forges (a fixed accent that never weathers). */
    static final String COPPER = "minecraft:waxed_cut_copper";
    static final String COPPER_STAIRS = "minecraft:waxed_cut_copper_stairs";
    static final String COPPER_SLAB = "minecraft:waxed_cut_copper_slab";
    static final String IRON = Kit.BARS;
    /** Amber glass in the windows, warm like the light of a forge. */
    static final String GLASS = "minecraft:orange_stained_glass_pane";
    /** The living rock the faces are carved of: natural stone, light against their dark niches (a fixed accent). */
    static final String ROCK = "minecraft:stone";
    static final String ROCK_STAIRS = "minecraft:stone_stairs";
    static final String ROCK_SLAB = "minecraft:stone_slab";

    /** Plans of towers: a square, a square with its corners cut, a disc. */
    static final int SQUARE = 0;
    static final int CHAMFER = 1;
    static final int ROUND = 2;

    /** Tops of towers: a crenellated platform with a brazier, a low pyramid of stone on the corbels. */
    static final int FLAT_TOP = 0;
    static final int HELM_TOP = 1;

    /** Kinds of the low stone roofs: four slopes, a ridge along x, a ridge along z. */
    static final int HIP = 0;
    static final int GABLE_X = 1;
    static final int GABLE_Z = 2;

    private Mountain() {
    }

    // ---------------------------------------------------------------- plans

    /**
     * The plan of a body: the columns of a box from (x1, z1) to (x2, z2), with its corners cut by {@code cut} blocks (0 for
     * a sharp square), or, when {@code round}, the disc inscribed in it (the box must then be square and odd). A plan grows
     * and shrinks by whole blocks, so that the same plan gives the outer face of a wall, its inner face and its batter.
     */
    record Plan(int x1, int z1, int x2, int z2, int cut, boolean round) {
        /** A rectangle with sharp corners. */
        static Plan box(int x1, int z1, int x2, int z2) {
            return new Plan(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2), 0, false);
        }

        /** The plan of a tower of an odd width around (cx, cz): {@link #SQUARE}, {@link #CHAMFER} or {@link #ROUND}. */
        static Plan of(int cx, int cz, int width, int kind) {
            int r = (width - 1) / 2;
            int cut = kind == CHAMFER ? Math.max(1, (r + 1) / 2) : 0;
            return new Plan(cx - r, cz - r, cx + r, cz + r, cut, kind == ROUND);
        }

        /** The plan grown by {@code d} blocks on every side (shrunk for a negative {@code d}). */
        Plan grow(int d) {
            return new Plan(x1 - d, z1 - d, x2 + d, z2 + d, cut > 0 ? Math.max(0, cut + d) : 0, round);
        }

        /** The middle column along x (the west one of the two middles of an even width). */
        int cx() {
            return (x1 + x2) / 2;
        }

        /** The middle column along z. */
        int cz() {
            return (z1 + z2) / 2;
        }

        /** Whether the plan has shrunk to nothing. */
        boolean empty() {
            return x1 > x2 || z1 > z2;
        }

        /** Whether a column lies inside the plan. */
        boolean has(int x, int z) {
            if (x < x1 || x > x2 || z < z1 || z > z2) {
                return false;
            }
            if (round) {
                return Kit.inDisc(x - cx(), z - cz(), x2 - x1 + 1);
            }
            return cut <= 0 || Math.min(x - x1, x2 - x) + Math.min(z - z1, z2 - z) >= cut;
        }

        /** Whether a column lies on the edge of the plan: inside it with a side neighbour outside. */
        boolean rim(int x, int z) {
            return has(x, z) && (!has(x + 1, z) || !has(x - 1, z) || !has(x, z + 1) || !has(x, z - 1));
        }

        int rimCount() {
            int n = 0;
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    if (rim(x, z)) {
                        n++;
                    }
                }
            }
            return n;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static void putIfFree(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z) && !s.solid(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static boolean solid(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && s.solid(x, y, z);
    }

    private static String stairs(String block, String facing, String half, String shape) {
        return block + "[facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=false]";
    }

    /** The column {@code u} blocks along and {@code v} blocks out from (x, z) for a shape looking the way of {@code facing}. */
    private static int[] local(int x, int z, String facing, int u, int v) {
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        return new int[]{x + a[0] * u + f[0] * v, z + a[1] * u + f[1] * v};
    }

    private static void putLocal(Structure s, int x, int y, int z, String facing, int u, int v, String state) {
        int[] c = local(x, z, facing, u, v);
        put(s, c[0], y, c[1], state);
    }

    /**
     * Stairs on the edge of a plan with their high side to its inside, a corner stair where the edge turns; null where the
     * plan is one block thin there.
     *
     * @param upside stairs hanging upside down: a corbel, the lip of a cornice or of a bowl
     */
    static String slope(Plan p, int x, int z, String block, boolean upside) {
        boolean n = !p.has(x, z - 1);
        boolean so = !p.has(x, z + 1);
        boolean w = !p.has(x - 1, z);
        boolean e = !p.has(x + 1, z);
        String half = upside ? "top" : "bottom";
        int count = (n ? 1 : 0) + (so ? 1 : 0) + (w ? 1 : 0) + (e ? 1 : 0);
        if (count == 1) {
            return stairs(block, n ? "south" : so ? "north" : w ? "east" : "west", half, "straight");
        }
        if (count == 2 && (n || so) && (w || e)) {
            return stairs(block, so ? "north" : "south", half, so == e ? "outer_left" : "outer_right");
        }
        return null;
    }

    /**
     * One layer of a plan filled with a block.
     */
    static void plate(Structure s, Plan p, int y, String state) {
        for (int z = p.z1(); z <= p.z2(); z++) {
            for (int x = p.x1(); x <= p.x2(); x++) {
                if (p.has(x, z)) {
                    put(s, x, y, z, state);
                }
            }
        }
    }

    /**
     * The edge of a plan at one layer.
     */
    static void ring(Structure s, Plan p, int y, String state) {
        for (int z = p.z1(); z <= p.z2(); z++) {
            for (int x = p.x1(); x <= p.x2(); x++) {
                if (p.rim(x, z)) {
                    put(s, x, y, z, state);
                }
            }
        }
    }

    // ---------------------------------------------------------------- walls

    /**
     * The walls of a plan from layer {@code y1} to {@code y2}, {@code thick} blocks thick inwards from its edge, hollow
     * inside. Walls of this style are two thick; one only for the upper storeys of small houses.
     */
    static void walls(Structure s, Plan p, int y1, int y2, int thick, String block) {
        Plan inner = p.grow(-thick);
        for (int y = y1; y <= y2; y++) {
            for (int z = p.z1(); z <= p.z2(); z++) {
                for (int x = p.x1(); x <= p.x2(); x++) {
                    if (p.has(x, z) && !inner.has(x, z)) {
                        put(s, x, y, z, block);
                    }
                }
            }
        }
    }

    /**
     * A battered foot (talus) of rough stone around the outer face of a wall whose plan is {@code wall}: from layer
     * {@code y} up it reaches {@code out} blocks out of the face and draws in one block every {@code rise} layers, each step
     * edged with stairs, so that the wall stands on a sloping skirt {@code out * rise} layers high (1 is a slope of 45
     * degrees, 2 a steep one). It fills only outside the plan; the wall itself is drawn by the caller. A door in the wall
     * cuts through it (see {@link #archDoor}), and its lowest step serves as the doorstep.
     *
     * @return the layer above the batter
     */
    static int batter(Structure s, Plan wall, int y, int out, int rise) {
        return batter(s, wall, y, out, rise, ROUGH, ROUGH_STAIRS);
    }

    /**
     * The same of a given stone and its stairs.
     */
    static int batter(Structure s, Plan wall, int y, int out, int rise, String block, String stairs) {
        for (int k = 0; k < out; k++) {
            Plan p = wall.grow(out - k);
            for (int j = 0; j < rise; j++) {
                int layer = y + k * rise + j;
                boolean top = j == rise - 1;
                for (int z = p.z1(); z <= p.z2(); z++) {
                    for (int x = p.x1(); x <= p.x2(); x++) {
                        if (!p.has(x, z) || wall.has(x, z)) {
                            continue;
                        }
                        String state = top && p.rim(x, z) ? slope(p, x, z, stairs, false) : null;
                        put(s, x, layer, z, state != null ? state : block);
                    }
                }
            }
        }
        return y + out * rise;
    }

    /**
     * A ring of corbels: stairs of trim hanging upside down on the ring one block outside the plan at layer {@code y}, so
     * that whatever stands on the layer above (the floor of a tower's top, a parapet, the eaves of a helm) juts out one block
     * over the wall.
     */
    static void corbels(Structure s, Plan p, int y) {
        corbels(s, p, y, 1);
    }

    /**
     * Corbels jutting out {@code depth} blocks (1 or 2) by layer {@code y}: with 2 a first ring of corbels one layer lower
     * carries a ring of trim on which the second ring of corbels stands, so that the top above juts out two blocks.
     */
    static void corbels(Structure s, Plan p, int y, int depth) {
        for (int d = 1; d <= depth; d++) {
            Plan inner = p.grow(d - 1);
            Plan q = p.grow(d);
            int layer = y - depth + d;
            for (int z = q.z1(); z <= q.z2(); z++) {
                for (int x = q.x1(); x <= q.x2(); x++) {
                    if (q.has(x, z) && !inner.has(x, z)) {
                        String state = slope(q, x, z, TRIM_STAIRS, true);
                        put(s, x, layer, z, state != null ? state : TRIM);
                        for (int yy = layer + 1; yy <= y; yy++) {
                            put(s, x, yy, z, TRIM);
                        }
                    }
                }
            }
        }
    }

    /**
     * Whether a cell on the edge of a plan carries a merlon: on a straight side two merlons and a gap in turn, counted from
     * the middle of the side so that both ends match; always at corners; on a disc by the angle around it.
     */
    private static boolean merlon(Plan p, int x, int z) {
        int cx = p.cx();
        int cz = p.cz();
        if (p.round()) {
            int n = p.rimCount();
            double a = Math.atan2(z - cz, x - cx) + Math.PI;
            int i = (int) Math.round(a / (2 * Math.PI) * n);
            return Math.floorMod(i, 3) != 1;
        }
        boolean ns = z == p.z1() || z == p.z2();
        boolean we = x == p.x1() || x == p.x2();
        if (ns == we) {
            return true;
        }
        int t = ns ? x - cx : z - cz;
        return Math.floorMod(Math.abs(t), 3) != 1;
    }

    /**
     * A heavy parapet on the edge of a plan (or along a line: a plan one block thin): a wall of stone at layer {@code y},
     * and on it merlons two blocks wide one layer high under a coping of trim slabs; the gaps between them are closed with a
     * slab, so that the parapet is never lower than one and a half blocks.
     */
    static void parapet(Structure s, Plan p, int y) {
        for (int z = p.z1(); z <= p.z2(); z++) {
            for (int x = p.x1(); x <= p.x2(); x++) {
                if (!p.rim(x, z)) {
                    continue;
                }
                put(s, x, y, z, WALL);
                if (merlon(p, x, z)) {
                    put(s, x, y + 1, z, WALL);
                    put(s, x, y + 2, z, Kit.slab(DARK_SLAB));
                } else {
                    put(s, x, y + 1, z, Kit.slab(TRIM_SLAB));
                }
            }
        }
    }

    /**
     * A pier (a pilaster buttress) standing out of a wall looking the way of {@code facing}: (x, z) is the middle of its foot
     * in the outer face of the wall, {@code width} (odd) across, one block deep, from layer {@code y1} (the top of a batter)
     * up to {@code y2} (under a cornice). Its foot flares out one block more in a sloping stair of rough stone, the dark bands
     * of the wall behind it run on across it (draw them first) and its head carries a capital of trim stairs hanging upside
     * down. Piers between the bays of a facade give it depth and a vertical rhythm.
     */
    static void pier(Structure s, int x, int y1, int y2, int z, String facing, int width) {
        int hw = (width - 1) / 2;
        String in = Furnish.opposite(facing);
        for (int u = -hw; u <= hw; u++) {
            int[] behind = local(x, z, facing, u, 0);
            for (int y = y1; y <= y2; y++) {
                boolean band = s.inside(behind[0], y, behind[1]) && s.get(behind[0], y, behind[1]).equals(DARK);
                putLocal(s, x, y, z, facing, u, 1, band ? DARK : WALL);
            }
            putLocal(s, x, y1, z, facing, u, 2, Kit.stairs(ROUGH_STAIRS, in));
            putLocal(s, x, y2, z, facing, u, 2, Kit.upside(TRIM_STAIRS, in));
        }
    }

    /**
     * A chimney stack of rough stone {@code width} by {@code width} (1 or 3) around (x, z) from layer {@code y1} to
     * {@code y2}: a dark band below its head; a stack three wide ends in a collar of dark stone with a fire sunk in its middle,
     * so that it smokes; a stack one wide in a dark post.
     */
    static void stack(Structure s, int x, int z, int y1, int y2, int width) {
        int h = (width - 1) / 2;
        for (int y = y1; y <= y2; y++) {
            for (int dz = -h; dz <= h; dz++) {
                for (int dx = -h; dx <= h; dx++) {
                    String state = y == y2 - 1 ? DARK : y == y2 ? (h == 0 ? DARK_WALL : DARK) : ROUGH;
                    put(s, x + dx, y, z + dz, state);
                }
            }
        }
        if (h > 0) {
            put(s, x, y2, z, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
            for (int dz = -h; dz <= h; dz += 2 * h) {
                for (int dx = -h; dx <= h; dx += 2 * h) {
                    put(s, x + dx, y2 + 1, z + dz, DARK_WALL);
                }
            }
        }
    }

    // ---------------------------------------------------------------- towers

    /**
     * A massive low tower around (cx, cz) standing on layer {@code y}: a plan of an odd width (7 to 13) that is square,
     * square with its corners cut or round; on a battered foot of rough stone (two blocks out from 9 wide, one below); walls
     * of stone two blocks thick (one below 9 wide) with a band of dark stone where the batter ends and another under the
     * corbels, dark quoins on the corners of a square; storeys of planks every {@code storey} layers (0 for none) with a ladder
     * up the wall opposite to the door and a lantern under every ceiling, and a small deep window of amber glass in the middle
     * of each side of every storey above the batter. Under layer {@code y + height} corbels (two rings from 11 wide, one
     * below), at {@code y + height} the floor of the top jutting out over them (two blocks from 11 wide, one below); on its
     * edge a heavy crenellated parapet with dark caps, and inside it either a great brazier ({@link #FLAT_TOP}; the ladder
     * comes up through a hatch) or a low pyramid of the spires with a gilt point that rises behind the merlons
     * ({@link #HELM_TOP}; the ladder ends in the last storey). A deep arched door in the side looking {@code door} at the
     * foot, unless null. The tower takes {@code width + 4} blocks across at its batter and at its top (+2 below 11 wide).
     *
     * @param kind {@link #SQUARE}, {@link #CHAMFER} or {@link #ROUND}
     * @param top  {@link #FLAT_TOP} or {@link #HELM_TOP}
     * @return the layer of the floor of the top (one stands at the layer above it)
     */
    static int tower(Structure s, int cx, int y, int cz, int width, int height, int kind, int top, String door, int storey) {
        Plan p = Plan.of(cx, cz, width, kind);
        int r = (width - 1) / 2;
        int thick = width >= 9 ? 2 : 1;
        int out = width >= 9 ? 2 : 1;
        Plan in = p.grow(-thick);
        int f = y + height;
        int deep = width >= 11 ? 2 : 1;
        int band = batter(s, p, y, out, 2);
        walls(s, p, y, f - 1, thick, WALL);
        plate(s, in, y, FLOOR);
        ring(s, p, band, DARK);
        ring(s, p, f - deep - 1, DARK);
        // Dark quoins on the corners of a square.
        if (kind == SQUARE) {
            for (int yy = band + 1; yy < f - deep - 1; yy++) {
                for (int z = p.z1(); z <= p.z2(); z++) {
                    for (int x = p.x1(); x <= p.x2(); x++) {
                        boolean edgeX = x == p.x1() || x == p.x2();
                        boolean edgeZ = z == p.z1() || z == p.z2();
                        int ax = Math.min(x - p.x1(), p.x2() - x);
                        int az = Math.min(z - p.z1(), p.z2() - z);
                        boolean even = Math.floorMod(yy - band, 2) == 0;
                        if (edgeX && edgeZ || edgeZ && ax == 1 && even || edgeX && az == 1 && !even) {
                            put(s, x, yy, z, DARK);
                        }
                    }
                }
            }
        }
        // Storeys, ladder and lanterns.
        List<Integer> floors = new ArrayList<>();
        floors.add(y);
        if (storey > 0) {
            for (int fl = y + storey; fl <= f - 4; fl += storey) {
                plate(s, in, fl, PLANKS);
                floors.add(fl);
            }
        }
        // The ladder runs up the wall opposite to the door.
        String back = door != null ? Furnish.opposite(door) : "north";
        int[] b = Furnish.step(back);
        int ladderX = cx + b[0] * (r - thick);
        int ladderZ = cz + b[1] * (r - thick);
        String ladder = Kit.ladder(Furnish.opposite(back));
        int ladderTop = top == FLAT_TOP ? f : floors.get(floors.size() - 1);
        for (int yy = y + 1; yy <= ladderTop; yy++) {
            put(s, ladderX, yy, ladderZ, ladder);
        }
        for (int i = 0; i < floors.size(); i++) {
            int ceiling = i + 1 < floors.size() ? floors.get(i + 1) : f;
            int lamp = cx + (in.x2() - in.x1() >= 4 ? 1 : 0);
            put(s, lamp, ceiling - 1, cz + 1, Kit.hangingLantern());
        }
        // Windows: small and deep, one in the middle of each side of a storey above the batter.
        for (int fl : floors) {
            int wy = fl + 2;
            if (wy <= band || wy + 2 >= f - deep - 1) {
                continue;
            }
            for (String side : new String[]{"south", "north", "east", "west"}) {
                if (side.equals(door) && wy < y + 7) {
                    continue;
                }
                int[] d = Furnish.step(side);
                window(s, cx + d[0] * r, wy, cz + d[1] * r, side, 2, thick);
            }
        }
        // The top.
        corbels(s, p, f - 1, deep);
        Plan q = p.grow(deep);
        plate(s, q, f, FLOOR);
        parapet(s, q, f + 1);
        if (top == FLAT_TOP) {
            put(s, ladderX, f, ladderZ, ladder);
            if (width >= 9) {
                brazier(s, cx, f + 1, cz, true);
            } else {
                put(s, cx, f + 1, cz, Kit.lantern());
            }
        } else {
            helm(s, q.grow(-1), f + 1, HELM);
        }
        if (door != null) {
            int[] d = Furnish.step(door);
            archDoor(s, cx + d[0] * (r - thick + 1), y + 1, cz + d[1] * (r - thick + 1), door, 1, thick);
        }
        return f;
    }

    /**
     * A low pyramid of stone over a plan (square, cut or round): rings drawing in one block for every layer from the eaves at
     * layer {@code y}, stairs on their edges and the block of the roof inside, solid, with a block of gold on its point.
     *
     * @return the layer above the point
     */
    static int helm(Structure s, Plan p, int y, Kit.Roof roof) {
        int layer = y;
        for (int k = 0; ; k++) {
            Plan r = p.grow(-k);
            if (r.empty() || !r.has(r.cx(), r.cz())) {
                break;
            }
            for (int z = r.z1(); z <= r.z2(); z++) {
                for (int x = r.x1(); x <= r.x2(); x++) {
                    if (!r.has(x, z)) {
                        continue;
                    }
                    String state = r.rim(x, z) ? slope(r, x, z, roof.stairs(), false) : null;
                    put(s, x, layer, z, state != null ? state : roof.block());
                }
            }
            layer++;
        }
        put(s, p.cx(), layer, p.cz(), GOLD);
        return layer + 1;
    }

    // ---------------------------------------------------------------- doors and windows

    /**
     * A deep arched door looking the way of {@code facing}: the door itself (one leaf, or three side by side when
     * {@code width} is 3) at (x, y, z), its lower half at {@code y}, under a grille of iron; before it {@code depth} orders of
     * arches, each one block wider on both sides and one layer higher than the one behind it, their upper corners rounded
     * with stairs, so that the opening recedes like a funnel into the wall. All orders stand in one solid mass of the wall
     * {@code width + 2 * depth + 2} wide and {@code depth + 3} high over the door, as deep as {@code depth + 1}: in a wall
     * two thick with the door in its inner face, a depth of 2 makes the portal stand one block proud of the wall. The
     * outermost order is dressed with trim and a carved keystone, a lantern hangs in it, and its threshold is paved. It
     * clears the opening (through a batter as well).
     *
     * @param width 1 or 3
     * @param depth 0 to 3
     */
    static void archDoor(Structure s, int x, int y, int z, String facing, int width, int depth) {
        int hw = width >= 3 ? 1 : 0;
        String plus = Furnish.left(facing);
        String minus = Furnish.opposite(plus);
        int hd = hw + depth;
        int headTop = y + 2 + Math.max(1, depth);
        for (int v = 0; v <= depth; v++) {
            int h = hw + v;
            int open = 2 + v;
            for (int u = -(hd + 1); u <= hd + 1; u++) {
                int[] c = local(x, z, facing, u, v);
                for (int yy = y; yy <= headTop; yy++) {
                    boolean opening = Math.abs(u) <= h && yy < y + open;
                    String state;
                    if (opening) {
                        if (v == 0) {
                            continue;
                        }
                        boolean haunch = Math.abs(u) == h && yy == y + open - 1;
                        state = haunch ? Kit.upside(TRIM_STAIRS, u < 0 ? minus : plus) : Structure.AIR;
                    } else if (v == 0 && Math.abs(u) <= hw && yy == y + 2) {
                        state = IRON;
                    } else if (Math.abs(u) <= h && yy == y + open && v == depth) {
                        state = u == 0 ? CARVED : TRIM;
                    } else if (v == depth && Math.abs(u) == hd + 1) {
                        state = TRIM;
                    } else {
                        state = WALL;
                    }
                    put(s, c[0], yy, c[1], state);
                }
                if (Math.abs(u) <= h) {
                    put(s, c[0], y - 1, c[1], FLOOR);
                }
            }
        }
        String doorFacing = Furnish.opposite(facing);
        for (int u = -hw; u <= hw; u++) {
            int[] c = local(x, z, facing, u, 0);
            s.door(c[0], y, c[1], DOOR, doorFacing, u > 0 ? "right" : "left");
        }
        if (depth > 0) {
            int[] c = local(x, z, facing, 0, depth);
            put(s, c[0], y + depth + 1, c[1], Kit.hangingLantern());
        }
    }

    /**
     * A small deep window in a wall looking the way of {@code facing}, its lowest cell at (x, y, z) in the outer face of the
     * wall, one block wide and {@code height} high: in a wall two thick the outer cell stays open as a reveal and the amber
     * pane sits in the inner one; a lintel of three dressed stones with a carved middle over it and a moulded sill sticking
     * out under it.
     */
    static void window(Structure s, int x, int y, int z, String facing, int height, int thick) {
        int[] f = Furnish.step(facing);
        for (int v = 0; v < height; v++) {
            if (thick >= 2) {
                put(s, x, y + v, z, Structure.AIR);
                put(s, x - f[0], y + v, z - f[1], GLASS);
            } else {
                put(s, x, y + v, z, GLASS);
            }
        }
        for (int u = -1; u <= 1; u++) {
            putLocal(s, x, y + height, z, facing, u, 0, u == 0 ? CARVED : TRIM);
        }
        putLocal(s, x, y - 1, z, facing, 0, 1, Kit.upside(TRIM_STAIRS, Furnish.opposite(facing)));
    }

    // ---------------------------------------------------------------- roofs

    /**
     * A low stone roof of slabs over the box of its eaves (x1..x2, z1..z2, the overhang included): a heavy cornice of stairs
     * hanging upside down along the eaves at layer {@code y} (the top course of the walls under it), then rows of slabs and
     * blocks in turn, so that the roof climbs half a block for every block inwards. Of four slopes ({@link #HIP}) or of two
     * with the ridge along x ({@link #GABLE_X}) or along z ({@link #GABLE_Z}). A gable roof with a {@code gable} block has
     * coped gables: the box then ends at the gable walls themselves (no overhang there), which are filled with that block
     * under the roof and rise half a block above it under a coping of trim, with a carved rune on the apex; without it (null)
     * the ends are open verges. Under the rows a second layer of the roof closes the shell, so that it is solid seen from
     * below and from beyond the walls; the attic stays hollow.
     *
     * @return the layer above the ridge
     */
    static int slabRoof(Structure s, int x1, int z1, int x2, int z2, int y, int kind, String gable) {
        Plan box = Plan.box(x1, z1, x2, z2);
        int top = y + 1;
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                int k = switch (kind) {
                    case GABLE_X -> Math.min(z - z1, z2 - z);
                    case GABLE_Z -> Math.min(x - x1, x2 - x);
                    default -> Math.min(Math.min(x - x1, x2 - x), Math.min(z - z1, z2 - z));
                };
                if (k == 0) {
                    String state;
                    if (kind == HIP) {
                        state = slope(box, x, z, ROOF.stairs(), true);
                    } else if (kind == GABLE_X) {
                        state = Kit.upside(ROOF.stairs(), z == z1 ? "south" : "north");
                    } else {
                        state = Kit.upside(ROOF.stairs(), x == x1 ? "east" : "west");
                    }
                    put(s, x, y, z, state != null ? state : ROOF.block());
                    continue;
                }
                int m = k - 1;
                int layer = y + 1 + m / 2;
                put(s, x, layer, z, m % 2 == 0 ? Kit.slab(ROOF.slab()) : ROOF.block());
                if (layer - 1 >= y && (k == 1 || layer - 1 > y)) {
                    putIfFree(s, x, layer - 1, z, ROOF.block());
                }
                top = Math.max(top, layer + 1);
            }
        }
        if (gable != null && kind != HIP) {
            boolean alongX = kind == GABLE_X;
            int[] walls = alongX ? new int[]{x1, x2} : new int[]{z1, z2};
            int c1 = alongX ? z1 : x1;
            int c2 = alongX ? z2 : x2;
            for (int w : walls) {
                for (int c = c1; c <= c2; c++) {
                    int px = alongX ? w : c;
                    int pz = alongX ? c : w;
                    int k = Math.min(c - c1, c2 - c);
                    if (k == 0) {
                        put(s, px, y, pz, TRIM);
                        continue;
                    }
                    int m = k - 1;
                    int layer = y + 1 + m / 2;
                    for (int yy = y; yy < layer; yy++) {
                        put(s, px, yy, pz, gable);
                    }
                    put(s, px, layer, pz, TRIM);
                    if (m % 2 == 1) {
                        put(s, px, layer + 1, pz, Kit.slab(TRIM_SLAB));
                    }
                    if (c == (c1 + c2) / 2 || c == (c1 + c2 + 1) / 2) {
                        put(s, px, layer + 1, pz, RUNE);
                        top = Math.max(top, layer + 2);
                    }
                }
            }
        }
        return top;
    }

    // ---------------------------------------------------------------- terraces and stairs

    /**
     * A broad flight of stone stairs going down the way of {@code facing}: its top step at (x, y, z) in the middle of its
     * width, every next step one block further out and one layer lower, {@code steps} in all, so that the last one stands on
     * layer {@code y - steps + 1}; rough stone fills under them. Beside it two cheek walls of stone slope down with it under a
     * coping of trim stairs and end in a newel at the foot with a lantern on it.
     *
     * @param width odd
     */
    static void flight(Structure s, int x, int y, int z, String facing, int width, int steps) {
        int hw = (width - 1) / 2;
        String up = Furnish.opposite(facing);
        int ground = y - steps + 1;
        for (int i = 0; i < steps; i++) {
            int layer = y - i;
            for (int u = -hw; u <= hw; u++) {
                int[] c = local(x, z, facing, u, i);
                for (int yy = ground; yy < layer; yy++) {
                    put(s, c[0], yy, c[1], ROUGH);
                }
                put(s, c[0], layer, c[1], Kit.stairs(TRIM_STAIRS, up));
                put(s, c[0], layer + 1, c[1], Structure.AIR);
                put(s, c[0], layer + 2, c[1], Structure.AIR);
            }
            for (int side = -1; side <= 1; side += 2) {
                int[] c = local(x, z, facing, side * (hw + 1), i);
                for (int yy = ground; yy <= layer; yy++) {
                    put(s, c[0], yy, c[1], WALL);
                }
                put(s, c[0], layer + 1, c[1], Kit.stairs(TRIM_STAIRS, up));
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            int[] c = local(x, z, facing, side * (hw + 1), steps);
            put(s, c[0], ground, c[1], WALL);
            put(s, c[0], ground + 1, c[1], TRIM);
            put(s, c[0], ground + 2, c[1], Kit.lantern());
        }
    }

    /**
     * A terrace: a platform of rough stone over the box x1..x2, z1..z2 from layer {@code y} up, {@code height} layers high,
     * paved with trim at its top layer so that one walks on it at {@code y + height}; its sides stand on a battered toe one
     * block out. A parapet of stone with a slab coping runs along its edges except the back (the side opposite to
     * {@code facing}, where a building stands) and except the head of a broad flight of {@code stairWidth} steps (odd, 0 for
     * none) going down from the middle of its front edge to layer {@code y}; with a lantern on each end of the parapet
     * beside the flight.
     *
     * @param facing the side of the front: north, south, east or west
     * @return the layer one walks at on the terrace
     */
    static int terrace(Structure s, int x1, int z1, int x2, int z2, int y, int height, String facing, int stairWidth) {
        Plan box = Plan.box(x1, z1, x2, z2);
        int walk = y + height;
        batter(s, box, y, 1, Math.min(2, height - 1));
        for (int yy = y; yy < walk - 1; yy++) {
            plate(s, box, yy, ROUGH);
        }
        plate(s, box, walk - 1, FLOOR);
        for (int yy = walk; yy <= walk + 3; yy++) {
            plate(s, box, yy, Structure.AIR);
        }
        int[] f = Furnish.step(facing);
        int hw = (stairWidth - 1) / 2;
        int fx = f[0] != 0 ? (f[0] > 0 ? x2 : x1) : (x1 + x2) / 2;
        int fz = f[1] != 0 ? (f[1] > 0 ? z2 : z1) : (z1 + z2) / 2;
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                if (!box.rim(x, z)) {
                    continue;
                }
                boolean back = f[0] > 0 && x == x1 || f[0] < 0 && x == x2 || f[1] > 0 && z == z1 || f[1] < 0 && z == z2;
                boolean front = (x - fx) * f[0] + (z - fz) * f[1] == 0;
                int along = f[0] != 0 ? z - fz : x - fx;
                if (back && !front || stairWidth > 0 && front && Math.abs(along) <= hw) {
                    continue;
                }
                put(s, x, walk, z, WALL);
                put(s, x, walk + 1, z, Kit.slab(TRIM_SLAB));
                if (stairWidth > 0 && front && Math.abs(along) == hw + 1) {
                    put(s, x, walk + 1, z, TRIM);
                    put(s, x, walk + 2, z, Kit.lantern());
                }
            }
        }
        if (stairWidth > 0) {
            flight(s, fx + f[0], walk - 1, fz + f[1], facing, stairWidth, height);
        }
        return walk;
    }

    // ---------------------------------------------------------------- carvings

    /**
     * A small carved face: relief (crown, brow, nose, moustache, beard), the inlaid niche of the wall face (dark, the skin
     * of the face, eyes of embers, the mouth) and the tip of the nose.
     */
    private static final String[][] FACE_SMALL = {
            {".GGGGG.", ".vvTvv.", "...T...", ".>>T<<.", ".TT.TT.", ".TTTTT.", "..TgT..", "...T..."},
            {"DDDDDDD", "DTTTTTD", "DTETETD", "DTTTTTD", "DTTDTTD", "DTTTTTD", "DDTTTDD", "DDDTDDD"},
            {".......", ".......", "...v...", ".......", ".......", ".......", ".......", "......."}};
    /** A great carved face with a crown of points and a beard ending in two braids with gilt rings. */
    private static final String[][] FACE_GREAT = {
            {".G.G.G.G.", ".GGGGGGG.", ".TTTTTTT.", ".vvvTvvv.", "....T....", "....T....", ".>>>T<<<.", ".TTT.TTT.", ".TTTTTTT.",
                    "..TTTTT..", "..g.T.g..", "..T...T.."},
            {"DDDDDDDDD", "DDDDDDDDD", "DTTTTTTTD", "DTTTTTTTD", "DTETTTETD", "DTTTTTTTD", "DTTTTTTTD", "DTTTDTTTD", "DTTTTTTTD",
                    "DDTTTTTDD", "DDTTTTTDD", "DDTDDDTDD"},
            {".........", ".........", ".........", ".........", "....T....", "....v....", ".........", ".........", ".........",
                    ".........", ".........", "........."}};

    private static String glyph(char c, String facing) {
        String in = Furnish.opposite(facing);
        String plus = Furnish.left(facing);
        String minus = Furnish.opposite(plus);
        return switch (c) {
            case 'T' -> ROCK;
            case 'C' -> CARVED;
            case 'W' -> WALL;
            case 'D' -> DARK;
            case 'R' -> RUNE;
            case 'g' -> GILDED;
            case 'G' -> GOLD;
            case 'E' -> EMBER;
            case 'v' -> Kit.upside(ROCK_STAIRS, in);
            case '^' -> Kit.stairs(ROCK_STAIRS, in);
            case '>' -> Kit.stairs(ROCK_STAIRS, plus);
            case '<' -> Kit.stairs(ROCK_STAIRS, minus);
            case ')' -> Kit.upside(ROCK_STAIRS, plus);
            case '(' -> Kit.upside(ROCK_STAIRS, minus);
            case 's' -> Kit.slab(ROCK_SLAB);
            case 'S' -> Kit.topSlab(ROCK_SLAB);
            default -> null;
        };
    }

    /**
     * A carved face of a bearded forefather on a wall looking the way of {@code facing}: the bottom of its beard at layer
     * {@code y}, its middle in the column (x, z) of the outer face of the wall. The face is carved of the living rock (natural
     * stone, light whatever the look) on a niche of dark stone inlaid in the wall face: the skin of the face, eyes of glowing
     * embers and a dark mouth lie in the wall face; a crown of gold, the heavy brow, the nose, a drooping moustache and the
     * beard stand out one block, the tip of the nose one more. {@code size} 0 is seven wide and eight high (a crown band, a
     * beard with one gilt ring), 1 nine wide and twelve high (a crown of points, a beard parting into two braids with gilt
     * rings). Stone needs no support, but the wall must stand behind the face.
     */
    static void face(Structure s, int x, int y, int z, String facing, int size) {
        String[][] pattern = size >= 1 ? FACE_GREAT : FACE_SMALL;
        int rows = pattern[0].length;
        int hw = (pattern[0][0].length() - 1) / 2;
        int[] depth = {1, 0, 2};
        for (int layer = 0; layer < pattern.length; layer++) {
            for (int row = 0; row < rows; row++) {
                String line = pattern[layer][row];
                int yy = y + rows - 1 - row;
                for (int i = 0; i < line.length(); i++) {
                    String state = glyph(line.charAt(i), facing);
                    if (state != null) {
                        putLocal(s, x, yy, z, facing, i - hw, depth[layer], state);
                    }
                }
            }
        }
    }

    /**
     * A standing stone of runes (a stele) on layer {@code y}, its face looking the way of {@code facing}: a plinth of rough
     * stone three wide with a step before it; a shaft of dark stone one wide and {@code height} high whose blocks are carved
     * runes and gilt stone in turn, between two lower shoulders that slope to it, and a dark cap.
     */
    static void runeStone(Structure s, int x, int y, int z, String facing, int height) {
        String plus = Furnish.left(facing);
        String minus = Furnish.opposite(plus);
        for (int u = -1; u <= 1; u++) {
            putLocal(s, x, y, z, facing, u, 0, ROUGH);
            putLocal(s, x, y, z, facing, u, 1, Kit.slab(ROUGH_SLAB));
        }
        for (int i = 1; i <= height; i++) {
            putLocal(s, x, y + i, z, facing, 0, 0, i % 2 == 1 ? RUNE : GILDED);
        }
        int shoulder = Math.max(1, height - 1);
        for (int i = 1; i <= shoulder; i++) {
            boolean last = i == shoulder;
            putLocal(s, x, y + i, z, facing, -1, 0, last ? Kit.stairs(DARK_STAIRS, plus) : DARK);
            putLocal(s, x, y + i, z, facing, 1, 0, last ? Kit.stairs(DARK_STAIRS, minus) : DARK);
        }
        putLocal(s, x, y + height + 1, z, facing, 0, 0, Kit.slab(DARK_SLAB));
    }

    /**
     * A frieze of runes along a straight line of a wall face at layer {@code y}: carved runes with dark stone and gilt stone
     * between them.
     */
    static void runeBand(Structure s, int x1, int z1, int x2, int z2, int y) {
        int i = 0;
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                put(s, x, y, z, i % 2 == 0 ? RUNE : i % 4 == 1 ? DARK : GILDED);
                i++;
            }
        }
    }

    /**
     * A long hanging of cloth on a wall looking the way of {@code facing}: from (x, y, z) in front of the wall down
     * {@code length} layers, blocks of cloth and a banner at its lower end.
     */
    static void hanging(Structure s, int x, int y, int z, String facing, int length) {
        for (int i = 0; i < length - 1; i++) {
            put(s, x, y - i, z, CLOTH);
        }
        put(s, x, y - length + 1, z, Kit.wallBanner("red", facing));
    }

    // ---------------------------------------------------------------- timber

    /**
     * Heavy timber brackets along the outer face of a wall: from the line of wall cells (x1, z1)..(x2, z2) every
     * {@code every} blocks a beam of hewn logs at layer {@code y} sticks out {@code reach} blocks the way of {@code facing},
     * carried by a stepped strut of beams under it whose lowest block is a stair hanging upside down against the wall. A
     * bracket whose first beam would fall into something already built is left out.
     */
    static void brackets(Structure s, int x1, int z1, int x2, int z2, int y, String facing, int reach, int every) {
        boolean alongX = z1 == z2;
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int[] f = Furnish.step(facing);
        String axis = f[0] != 0 ? "x" : "z";
        String toWall = Furnish.opposite(facing);
        for (int a = a1; a <= a2; a += Math.max(1, every)) {
            int bx = alongX ? a : x1;
            int bz = alongX ? z1 : a;
            if (solid(s, bx + f[0], y, bz + f[1])) {
                continue;
            }
            for (int d = 1; d <= reach; d++) {
                put(s, bx + f[0] * d, y, bz + f[1] * d, Kit.log(BEAM, axis));
            }
            for (int j = 1; j < reach; j++) {
                for (int d = 1; d <= reach - j; d++) {
                    put(s, bx + f[0] * d, y - j, bz + f[1] * d, d == reach - j ? Kit.upside(STAIRS, toWall) : Kit.log(BEAM, axis));
                }
            }
        }
    }

    /**
     * A timber balcony on brackets before a wall looking the way of {@code facing}: along the wall cells (x1, z1)..(x2, z2)
     * a floor of planks {@code depth} blocks deep at layer {@code y} (one walks at {@code y + 1}), carried by brackets every
     * two blocks under it, with a railing of posts on its outer edge and its ends and a lantern on the corner posts.
     */
    static void balcony(Structure s, int x1, int z1, int x2, int z2, int y, String facing, int depth) {
        boolean alongX = z1 == z2;
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int[] f = Furnish.step(facing);
        brackets(s, x1, z1, x2, z2, y - 1, facing, depth, 2);
        for (int a = a1; a <= a2; a++) {
            int bx = alongX ? a : x1;
            int bz = alongX ? z1 : a;
            for (int d = 1; d <= depth; d++) {
                int px = bx + f[0] * d;
                int pz = bz + f[1] * d;
                put(s, px, y, pz, PLANKS);
                boolean edge = d == depth || a == a1 || a == a2;
                if (edge) {
                    put(s, px, y + 1, pz, FENCE);
                }
            }
        }
        for (int a : new int[]{a1, a2}) {
            int bx = (alongX ? a : x1) + f[0] * depth;
            int bz = (alongX ? z1 : a) + f[1] * depth;
            put(s, bx, y + 2, bz, Kit.lantern());
        }
    }

    // ---------------------------------------------------------------- fire

    /**
     * A brazier standing on layer {@code y} around (x, z). A small one: a post of dark stone with a fire on it. A great one
     * (three wide): a dark pedestal, on it a bowl of dark stairs hanging upside down around a heart of embers, and a fire on
     * the embers; it glows from afar on the top of a tower or beside a flight of stairs.
     */
    static void brazier(Structure s, int x, int y, int z, boolean big) {
        String fire = "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]";
        if (!big) {
            put(s, x, y, z, DARK_WALL);
            put(s, x, y + 1, z, fire);
            return;
        }
        put(s, x, y, z, DARK);
        Plan bowl = Plan.box(x - 1, z - 1, x + 1, z + 1);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    put(s, x, y + 1, z, EMBER);
                } else {
                    String state = slope(bowl, x + dx, z + dz, DARK_STAIRS, true);
                    put(s, x + dx, y + 1, z + dz, state != null ? state : DARK);
                }
            }
        }
        put(s, x, y + 2, z, fire);
    }

    /**
     * A great forge against a wall, opening the way of {@code facing} into the room: (x, z) is the middle of its front,
     * {@code y} the layer one stands at before it. Five wide and three deep: a hearth of dark stone between two cheeks, on it a
     * bed of glowing magma with the fire in its middle and embers at its back; over it a hood of copper that flares out over
     * the hearth and draws in to a flue of dark stone three wide against the wall, which rises to layer {@code top} and ends
     * in a cap. With {@code furnaces} the cheeks hold two blast furnaces (only in a workshop: they count as stoves).
     */
    static void forge(Structure s, int x, int y, int z, String facing, int top, boolean furnaces) {
        String in = Furnish.opposite(facing);
        String plus = Furnish.left(facing);
        String minus = Furnish.opposite(plus);
        String fire = "minecraft:campfire[facing=" + facing + ",lit=true,signal_fire=false,waterlogged=false]";
        for (int v = -2; v <= 0; v++) {
            for (int u = -1; u <= 1; u++) {
                putLocal(s, x, y, z, facing, u, v, DARK);
            }
            for (int u = -2; u <= 2; u += 4) {
                putLocal(s, x, y, z, facing, u, v, DARK);
                putLocal(s, x, y + 1, z, facing, u, v, DARK);
            }
        }
        if (furnaces) {
            putLocal(s, x, y + 1, z, facing, -2, 0, "minecraft:blast_furnace[facing=" + facing + ",lit=true]");
            putLocal(s, x, y + 1, z, facing, 2, 0, "minecraft:blast_furnace[facing=" + facing + ",lit=true]");
        }
        putLocal(s, x, y + 1, z, facing, -1, -1, MAGMA);
        putLocal(s, x, y + 1, z, facing, 0, -1, fire);
        putLocal(s, x, y + 1, z, facing, 1, -1, MAGMA);
        for (int u = -1; u <= 1; u++) {
            putLocal(s, x, y + 1, z, facing, u, -2, u == 0 ? EMBER : MAGMA);
            putLocal(s, x, y + 1, z, facing, u, 0, Structure.AIR);
        }
        // The hood: a lip over the hearth, a slope drawing in, the flue.
        for (int u = -2; u <= 2; u++) {
            putLocal(s, x, y + 2, z, facing, u, 0, Kit.upside(COPPER_STAIRS, in));
            for (int v = -2; v <= -1; v++) {
                putLocal(s, x, y + 2, z, facing, u, v, Math.abs(u) == 2 ? Kit.upside(COPPER_STAIRS, u < 0 ? plus : minus) : COPPER);
            }
        }
        for (int u = -2; u <= 2; u++) {
            String state;
            if (Math.abs(u) == 2) {
                state = Kit.stairs(COPPER_STAIRS, u < 0 ? plus : minus);
            } else {
                state = Kit.stairs(COPPER_STAIRS, in);
            }
            putLocal(s, x, y + 3, z, facing, u, -1, state);
            putLocal(s, x, y + 3, z, facing, u, -2, Math.abs(u) == 2 ? Kit.stairs(COPPER_STAIRS, u < 0 ? plus : minus) : COPPER);
        }
        for (int yy = y + 4; yy <= top; yy++) {
            for (int u = -1; u <= 1; u++) {
                putLocal(s, x, yy, z, facing, u, -2, yy == y + 4 && u == 0 ? GOLD : DARK);
            }
        }
        for (int u = -1; u <= 1; u++) {
            putLocal(s, x, top + 1, z, facing, u, -2, u == 0 ? Structure.AIR : DARK_WALL);
        }
    }

    /**
     * A wide hearth set against a wall, opening the way of {@code facing}: (x, y, z) is the cell of its fire, before the
     * wall. Cheeks of dark stone on both sides two layers high, a mantel of trim stairs over the fire, a sloping hood of trim
     * above it with a rune in its middle, and a flue in the wall behind (the cell beyond the fire) rising to layer
     * {@code top}, where it ends in a cap of dark posts; embers glow at the back of the fire.
     */
    static void fireplace(Structure s, int x, int y, int z, String facing, int top) {
        String in = Furnish.opposite(facing);
        putLocal(s, x, y, z, facing, 0, 0, "minecraft:campfire[facing=" + facing + ",lit=true,signal_fire=false,waterlogged=false]");
        putLocal(s, x, y + 1, z, facing, 0, 0, Structure.AIR);
        for (int u = -1; u <= 1; u += 2) {
            putLocal(s, x, y, z, facing, u, 0, DARK);
            putLocal(s, x, y + 1, z, facing, u, 0, DARK);
        }
        putLocal(s, x, y - 1, z, facing, 0, 0, DARK);
        putLocal(s, x, y, z, facing, 0, -1, EMBER);
        for (int u = -1; u <= 1; u++) {
            putLocal(s, x, y + 2, z, facing, u, 0, Kit.upside(TRIM_STAIRS, in));
            putLocal(s, x, y + 3, z, facing, u, 0, u == 0 ? RUNE : Kit.stairs(TRIM_STAIRS, in));
        }
        for (int yy = y + 1; yy <= top; yy++) {
            putLocal(s, x, yy, z, facing, 0, -1, DARK);
        }
        putLocal(s, x, top + 1, z, facing, 0, -1, DARK_WALL);
    }

    // ---------------------------------------------------------------- furniture

    /**
     * Casks lying in a rack along a wall, their ends looking the way of {@code facing}: from (x1, z1) to (x2, z2) at layer
     * {@code y}, two tiers of logs lying on their side (the timber), a tap on every lower cask. No inventory: casks are looks;
     * the one barrel of a household or a workplace is set apart by the caller.
     */
    static void casks(Structure s, int x1, int z1, int x2, int z2, int y, String facing) {
        int[] f = Furnish.step(facing);
        String axis = f[0] != 0 ? "x" : "z";
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                put(s, x, y, z, Kit.log(LOG, axis));
                put(s, x, y + 1, z, Kit.log(LOG, axis));
                putIfFree(s, x + f[0], y, z + f[1], "minecraft:tripwire_hook[attached=false,facing=" + facing + ",powered=false]");
            }
        }
    }

    /**
     * A bed set in an alcove of stone, its head at (x, y, z) against a wall and its foot towards {@code facing}: cheeks of
     * stone two high along both sides, a canopy of trim over it with a moulded lip at the foot and a candle on it. A bed is a
     * home: only in houses, the rooms of mages and barracks.
     */
    static void bedAlcove(Structure s, int x, int y, int z, String facing, String color) {
        String in = Furnish.opposite(facing);
        int[] foot = local(x, z, facing, 0, 1);
        Furnish.bed(s, foot[0], y, foot[1], in, color);
        for (int v = 0; v <= 1; v++) {
            for (int u = -1; u <= 1; u += 2) {
                putLocal(s, x, y, z, facing, u, v, WALL);
                putLocal(s, x, y + 1, z, facing, u, v, WALL);
            }
            for (int u = -1; u <= 1; u++) {
                putLocal(s, x, y + 2, z, facing, u, v, v == 1 ? Kit.upside(TRIM_STAIRS, in) : TRIM);
            }
        }
        putLocal(s, x, y + 3, z, facing, 0, 0, Furnish.candles(2));
        putLocal(s, x, y + 1, z, facing, 0, 0, Kit.wallBanner("red", facing));
    }

    /**
     * A heavy table of stone from (x1, z1) to (x2, z2) at layer {@code y}: a top of trim slabs with candles on its ends and
     * tankards between, and with {@code benches} wooden benches along both long sides.
     */
    static void stoneTable(Structure s, int x1, int z1, int x2, int z2, int y, boolean benches) {
        boolean alongX = Math.abs(x2 - x1) >= Math.abs(z2 - z1);
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int c = alongX ? z1 : x1;
        for (int a = a1; a <= a2; a++) {
            int tx = alongX ? a : c;
            int tz = alongX ? c : a;
            put(s, tx, y, tz, Kit.topSlab(TRIM_SLAB));
            put(s, tx, y + 1, tz, a == a1 || a == a2 ? Furnish.candles(3) : (a - a1) % 2 == 1 ? "minecraft:flower_pot" : Structure.AIR);
            if (benches) {
                put(s, alongX ? tx : tx - 1, y, alongX ? tz - 1 : tz, Kit.slab(SLAB));
                put(s, alongX ? tx : tx + 1, y, alongX ? tz + 1 : tz, Kit.slab(SLAB));
            }
        }
    }

    /**
     * A pillar of a hall standing on layer {@code y1} at (x, z) up to layer {@code y2} under a ceiling: a shaft of stone
     * with a carved rune at the height of the eye, a flared base of dark stairs around its foot and a capital of trim stairs
     * hanging upside down around its head.
     */
    static void column(Structure s, int x, int z, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            put(s, x, y, z, y == y1 ? DARK : y == y2 ? TRIM : y == y1 + 2 ? RUNE : WALL);
        }
        for (String side : new String[]{"north", "south", "east", "west"}) {
            int[] d = Furnish.step(side);
            put(s, x + d[0], y1, z + d[1], Kit.stairs(DARK_STAIRS, Furnish.opposite(side)));
            put(s, x + d[0], y2, z + d[1], Kit.upside(TRIM_STAIRS, Furnish.opposite(side)));
        }
    }

    /**
     * A bin of raw stock for a smith from (x1, z1) to (x2, z2) at layer {@code y}: raw iron, coal, raw copper and raw gold
     * heaped in turn, higher in the middle.
     */
    static void stockPile(Structure s, int x1, int z1, int x2, int z2, int y) {
        String[] stock = {"minecraft:raw_iron_block", "minecraft:coal_block", "minecraft:raw_copper_block", "minecraft:raw_gold_block"};
        int i = 0;
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                put(s, x, y, z, stock[i % stock.length]);
                i++;
            }
        }
        put(s, (x1 + x2) / 2, y + 1, (z1 + z2) / 2, "minecraft:coal_block");
    }
}
