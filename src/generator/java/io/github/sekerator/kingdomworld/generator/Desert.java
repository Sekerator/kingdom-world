package io.github.sekerator.kingdomworld.generator;

/**
 * The style "desert": the architecture of sandstone and mud brick of hot dry lands. A building of this style is a block of
 * thick walls with few openings under a flat roof, its parapet crowned with pointed or stepped merlons; over the halls that
 * matter rise domes on drums (smooth, pointed or ribbed, tiled turquoise, gilt or white) and slender minarets with a
 * balcony on corbels and a small dome; on the roofs of houses stand wind towers with slotted vents. Doors and gates open in
 * horseshoe and pointed arches whose ring of voussoirs is framed by a rectangular band (alfiz); windows are small, often
 * screened with a wooden lattice (mashrabiya), flat in the wall or in a box jutting out on corbels. The rooms open on a
 * courtyard with an arcade, a star-shaped fountain lined with glazed tiles, beds of flowers and date palms; cloth awnings
 * on poles shade the doors of shops. Inside: tiled floors, rugs, low divans along the walls, brass tray tables, niches with
 * candles, wall fountains and lamps hanging on chains.
 *
 * <p>The materials are the parts of a look (see {@link Kit}): walls the wall (sandstone, smooth sandstone or mud brick in a
 * desert kingdom); plinths, the frames of arches (alfiz), every other voussoir of their rings (ablaq), the copings of parapets
 * and the points of merlons, balustrades and the shafts of columns the rough stone (red sandstone), which stands out from the
 * walls in every look of the style; cornices, bands, sills, corbels and the curves of arches the trim (smooth red or smooth
 * sandstone); keystones, capitals and spandrels the carved stone (chiseled sandstone); domes and the caps of minarets the
 * spires (turquoise tiles, gilt copper or white), so that a look colours them all at once; the decks of flat roofs and the
 * rare canopies the roof; awnings, rugs and cushions the cloth; doors, lattices, floors of upper storeys and furniture the
 * timber (oak and spruce, the wood of the place). Fixed accents that never change: glazed terracotta of light blue, blue and
 * cyan for the tiles of fountains, bands and floors; smooth sandstone for the paving of courts; gold for the finials; brass
 * (waxed cut copper) for the tray tables; jungle leaves for the palms (their trunks of jungle logs follow the wood of the
 * place where a look changes all wood); coloured glass in the windows.
 *
 * <p>This class holds the data and the shapes of the style; buildings are drawn with them by other classes. Coordinates as
 * everywhere: x from west to east, z from the back (north) to the front (south), y up. A shape draws only inside the box of
 * the structure and leaves out what falls outside it. Where a shape stands on a wall it takes the way the wall looks
 * ({@code facing}: north, south, east or west); along such a face "u" runs to the left of the way it looks (east along a face
 * that looks south) and "v" out of it. Shapes return the layer above what they drew where that is useful.
 */
final class Desert {
    // ---------------------------------------------------------------- materials

    /** The walls (the wall of a look). */
    static final String WALL = Kit.WALL;
    static final String WALL_STAIRS = Kit.WALL_STAIRS;
    static final String WALL_SLAB = Kit.WALL_SLAB;
    /**
     * Plinths, footings, frames of arches, every other voussoir, copings and the points of merlons, the shafts of columns and
     * balustrades (the rough stone of a look: red sandstone, the accent that stands out in every look of the style).
     */
    static final String BASE = Kit.ROUGH;
    static final String BASE_STAIRS = Kit.ROUGH_STAIRS;
    static final String BASE_SLAB = Kit.ROUGH_SLAB;
    /** A slender column or a baluster: a post of the rough stone wall. */
    static final String BASE_POST = Kit.ROUGH_FENCE;
    /** Cornices, bands, sills, corbels and the curves of arches (the trim of a look). */
    static final String TRIM = Kit.TRIM;
    static final String TRIM_STAIRS = Kit.TRIM_STAIRS;
    static final String TRIM_SLAB = Kit.TRIM_SLAB;
    /** Keystones, capitals, spandrels and carved panels (the carved stone of a look). */
    static final String CARVED = Kit.CARVED;
    /** Whitewash of plastered walls and the bodies of ovens (the plaster of a look). */
    static final String WHITEWASH = Kit.PLASTER;
    /** The domes and the caps of minarets (the spires of a look: turquoise tiles, gilt copper or white). */
    static final Kit.Roof DOME = Kit.SLATE;
    /** The decks of flat roofs, low roofs and canopies (the roof of a look). */
    static final Kit.Roof ROOF = Kit.TILE;
    /** The wall stone as a material of domes and ribs. */
    static final Kit.Roof STONE = new Kit.Roof(WALL, WALL_STAIRS, WALL_SLAB);
    /** The trim as a material of domes and ribs. */
    static final Kit.Roof TRIMS = new Kit.Roof(TRIM, TRIM_STAIRS, TRIM_SLAB);
    /** The cloth of awnings, rugs, cushions and hangings (the cloth of a look). */
    static final String CLOTH = Kit.CLOTH;
    static final String CLOTH_CARPET = Kit.CLOTH_CARPET;

    /** Beams, lintels and the poles of awnings (timber). */
    static final String BEAM = "minecraft:spruce_log";
    static final String POST = "minecraft:stripped_spruce_log";
    static final String PLANKS = "minecraft:spruce_planks";
    static final String STAIRS = "minecraft:spruce_stairs";
    static final String SLAB = "minecraft:spruce_slab";
    static final String FENCE = "minecraft:spruce_fence";
    static final String DOOR = "minecraft:spruce_door";
    /** Floors of upper storeys, tables and benches: oak (timber). */
    static final String FLOOR = "minecraft:oak_planks";
    static final String FLOOR_SLAB = "minecraft:oak_slab";
    static final String FLOOR_STAIRS = "minecraft:oak_stairs";
    /** The wooden lattice of screens (mashrabiya): oak trapdoors (timber), never the shutters of a look. */
    static final String LATTICE = "minecraft:oak_trapdoor";

    /** Glazed tiles, fixed accents: light blue (the field), blue (borders) and cyan (bands). */
    static final String TILE = "minecraft:light_blue_glazed_terracotta";
    static final String TILE_BLUE = "minecraft:blue_glazed_terracotta";
    static final String TILE_CYAN = "minecraft:cyan_glazed_terracotta";
    /** The paving of courts and the floors of halls: smooth sandstone, a fixed accent. */
    static final String PAVING = "minecraft:smooth_sandstone";
    static final String PAVING_SLAB = "minecraft:smooth_sandstone_slab";
    /** Gilding of finials. */
    static final String GILT = "minecraft:gold_block";
    static final String ROD = "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]";
    /** Brass of tray tables and lamps: waxed cut copper. */
    static final String BRASS_SLAB = "minecraft:waxed_cut_copper_slab";
    /** Coloured glass of windows. */
    static final String GLASS = "minecraft:light_blue_stained_glass_pane";
    static final String GLASS_WARM = "minecraft:orange_stained_glass_pane";
    static final String WATER = "minecraft:water[level=0]";
    /** Date palms: jungle logs and leaves that never wither. */
    static final String PALM = "minecraft:jungle_log";
    static final String FROND = Kit.leaves("jungle");

    /** Plans of round shapes: a square, an octagon, a disc. */
    static final int SQUARE = 0;
    static final int OCTAGON = 1;
    static final int ROUND = 2;

    /** The crown of a parapet: a plain coping, stepped merlons or pointed merlons. */
    enum Merlons { NONE, STEPPED, POINTED }

    /** The shape of a dome: a smooth hemisphere, a pointed dome, a pointed dome with eight ribs of trim. */
    enum DomeKind { SMOOTH, POINTED, RIBBED }

    /** The head of an arch: round, pointed (two arcs meeting) or horseshoe (the arc returning inwards below its middle). */
    enum Arch { ROUND, POINTED, HORSESHOE }

    private Desert() {
    }

    // ---------------------------------------------------------------- helpers

    /** Sets a block where the box of the structure has room for it. */
    static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static String stairs(String block, String facing, String half, String shape) {
        return block + "[facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=false]";
    }

    /** A glazed tile turned the given way. */
    static String glazed(String tile, String facing) {
        return tile + "[facing=" + facing + "]";
    }

    /**
     * A face of a wall: (x, z) its origin, u along it to the left of the way it looks, v out of it.
     */
    private record Face(int x, int z, String facing) {
        int px(int u, int v) {
            int[] a = Furnish.step(Furnish.left(facing));
            int[] o = Furnish.step(facing);
            return x + a[0] * u + o[0] * v;
        }

        int pz(int u, int v) {
            int[] a = Furnish.step(Furnish.left(facing));
            int[] o = Furnish.step(facing);
            return z + a[1] * u + o[1] * v;
        }

        String plus() {
            return Furnish.left(facing);
        }

        String minus() {
            return Furnish.opposite(Furnish.left(facing));
        }

        String in() {
            return Furnish.opposite(facing);
        }

        void put(Structure s, int u, int y, int v, String state) {
            Desert.put(s, px(u, v), y, pz(u, v), state);
        }
    }

    // ---------------------------------------------------------------- plans

    /**
     * Whether a column lies in a plan of the given half-size around its middle: a square, an octagon (corners cut) or a
     * disc (as {@link Kit#inDisc}).
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
            case ROUND -> dx * dx + dz * dz <= r * r + r;
            default -> true;
        };
    }

    private static boolean onRim(int dx, int dz, int r, int plan) {
        return inPlan(dx, dz, r, plan) && (!inPlan(dx + 1, dz, r, plan) || !inPlan(dx - 1, dz, r, plan) || !inPlan(dx, dz + 1, r, plan)
                || !inPlan(dx, dz - 1, r, plan));
    }

    /** One layer of a plan filled with a block. */
    static void plate(Structure s, int cx, int y, int cz, int r, int plan, String state) {
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (inPlan(dx, dz, r, plan)) {
                    put(s, cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /** The rim of a plan from layer {@code y1} to {@code y2}: the wall of a round, octagonal or square tower. */
    static void shell(Structure s, int cx, int y1, int y2, int cz, int r, int plan, String state) {
        for (int y = y1; y <= y2; y++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (onRim(dx, dz, r, plan)) {
                        put(s, cx + dx, y, cz + dz, state);
                    }
                }
            }
        }
    }

    /**
     * Stairs on a cell of the ring between the plans {@code r - 1} and {@code r}, their high side to the middle (upside down:
     * hanging, a corbel); at the turns of the ring corner stairs.
     */
    private static String rimStairs(String block, int dx, int dz, int r, int plan, boolean upside) {
        boolean outX = dx != 0 && !inPlan(dx + Integer.signum(dx), dz, r, plan);
        boolean outZ = dz != 0 && !inPlan(dx, dz + Integer.signum(dz), r, plan);
        String half = upside ? "top" : "bottom";
        if (outX && outZ) {
            return stairs(block, dz > 0 ? "north" : "south", half, dz > 0 == dx > 0 ? "outer_left" : "outer_right");
        }
        if (outX || !outZ && Math.abs(dx) >= Math.abs(dz)) {
            return stairs(block, dx > 0 ? "west" : "east", half, "straight");
        }
        return stairs(block, dz > 0 ? "north" : "south", half, "straight");
    }

    /**
     * A ring of corbels (stairs of trim hanging upside down, their foot to the middle) on the cells of plan {@code r} outside
     * plan {@code r - 1} at layer {@code y}: a cornice under a balcony or the eaves of a dome.
     */
    static void corbels(Structure s, int cx, int y, int cz, int r, int plan) {
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (inPlan(dx, dz, r, plan) && !inPlan(dx, dz, r - 1, plan)) {
                    put(s, cx + dx, y, cz + dz, rimStairs(TRIM_STAIRS, dx, dz, r, plan, true));
                }
            }
        }
    }

    // ---------------------------------------------------------------- flat roofs

    /**
     * A flat roof over walls on the lines {@code x1}, {@code x2}, {@code z1} and {@code z2} whose top course is at layer
     * {@code y - 1}: the deck of the roof at layer {@code y} inside the walls (the ceiling of the rooms below; one walks on the
     * terrace at {@code y + 1}), the walls rising through {@code y} and {@code y + 1} as a parapet and crowned at {@code y + 2}
     * with the merlons of the given kind (see {@link #crown}); on each corner a pinnacle of wall under a slab of rough stone.
     * With {@code cornice} the course at {@code y} is a band of trim and a cornice of trim hangs one block out of the walls at
     * {@code y} (upside-down stairs, so it needs one block of room on every side). The roof reaches {@code y + 3}.
     */
    static void flatRoof(Structure s, int x1, int z1, int x2, int z2, int y, Merlons merlons, boolean cornice) {
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                boolean rim = x == x1 || x == x2 || z == z1 || z == z2;
                put(s, x, y, z, rim ? cornice ? TRIM : WALL : ROOF.block());
                if (rim) {
                    put(s, x, y + 1, z, WALL);
                }
            }
        }
        if (cornice) {
            for (int x = x1; x <= x2; x++) {
                put(s, x, y, z1 - 1, Kit.upside(TRIM_STAIRS, "south"));
                put(s, x, y, z2 + 1, Kit.upside(TRIM_STAIRS, "north"));
            }
            for (int z = z1; z <= z2; z++) {
                put(s, x1 - 1, y, z, Kit.upside(TRIM_STAIRS, "east"));
                put(s, x2 + 1, y, z, Kit.upside(TRIM_STAIRS, "west"));
            }
            put(s, x1 - 1, y, z1 - 1, stairs(TRIM_STAIRS, "south", "top", "outer_left"));
            put(s, x2 + 1, y, z1 - 1, stairs(TRIM_STAIRS, "south", "top", "outer_right"));
            put(s, x1 - 1, y, z2 + 1, stairs(TRIM_STAIRS, "north", "top", "outer_right"));
            put(s, x2 + 1, y, z2 + 1, stairs(TRIM_STAIRS, "north", "top", "outer_left"));
        }
        crown(s, true, x1, x2, z1, y + 2, merlons);
        crown(s, true, x1, x2, z2, y + 2, merlons);
        crown(s, false, z1, z2, x1, y + 2, merlons);
        crown(s, false, z1, z2, x2, y + 2, merlons);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            put(s, c[0], y + 2, c[1], WALL);
            put(s, c[0], y + 3, c[1], Kit.slab(BASE_SLAB));
        }
    }

    /**
     * A flat roof over a building round a court: as {@link #flatRoof} over the walls on {@code x1}, {@code x2}, {@code z1} and
     * {@code z2}, but open over the court whose walls stand on the lines {@code cx1}, {@code cx2}, {@code cz1} and {@code cz2};
     * along the court the walls rise as a low parapet with a coping of slabs of rough stone at {@code y + 2}, so that the roof
     * is one terrace round the court.
     */
    static void courtRoof(Structure s, int x1, int z1, int x2, int z2, int cx1, int cz1, int cx2, int cz2, int y, Merlons merlons,
                          boolean cornice) {
        flatRoof(s, x1, z1, x2, z2, y, merlons, cornice);
        for (int z = cz1; z <= cz2; z++) {
            for (int x = cx1; x <= cx2; x++) {
                boolean rim = x == cx1 || x == cx2 || z == cz1 || z == cz2;
                if (rim) {
                    put(s, x, y, z, cornice ? TRIM : WALL);
                    put(s, x, y + 1, z, WALL);
                    put(s, x, y + 2, z, Kit.slab(BASE_SLAB));
                } else {
                    put(s, x, y, z, Structure.AIR);
                }
            }
        }
    }

    /**
     * The crown of a parapet from {@code a1} to {@code a2} (its corners left out) along x or z on the line {@code c}, its foot
     * at layer {@code y}: a coping of slabs of rough stone ({@link Merlons#NONE}, half a block); stepped merlons three wide
     * ({@link Merlons#STEPPED}: a slab, a block under a slab, a slab: steps of a half, one and a half and a half); or pointed
     * merlons three wide ({@link Merlons#POINTED}: a block between two stairs rising to it, a slab of rough stone on its
     * point). Every fourth block is a crenel; the merlons are set out from the middle of the line, so that it is symmetric.
     */
    static void crown(Structure s, boolean alongX, int a1, int a2, int c, int y, Merlons kind) {
        int mid2 = a1 + a2;
        String up = alongX ? "east" : "south";
        String down = alongX ? "west" : "north";
        for (int a = a1 + 1; a < a2; a++) {
            int x = alongX ? a : c;
            int z = alongX ? c : a;
            if (kind == Merlons.NONE) {
                put(s, x, y, z, Kit.slab(BASE_SLAB));
                continue;
            }
            int d2 = 2 * a - mid2;
            int t = (Math.abs(d2) / 2 + 1) % 4;
            if (t == 3) {
                continue;
            }
            if (t == 1) {
                put(s, x, y, z, WALL);
                put(s, x, y + 1, z, Kit.slab(kind == Merlons.POINTED ? BASE_SLAB : WALL_SLAB));
                continue;
            }
            int sign = d2 > 0 ? 1 : -1;
            if (t == 2) {
                sign = -sign;
            }
            put(s, x, y, z, kind == Merlons.POINTED ? Kit.stairs(WALL_STAIRS, sign > 0 ? up : down) : Kit.slab(WALL_SLAB));
        }
    }

    // ---------------------------------------------------------------- domes

    /**
     * A dome springing at layer {@code y} around (cx, cz), {@code diameter} wide at its foot (odd, 3 or more): a smooth
     * hemisphere ({@link DomeKind#SMOOTH}, about half as high as wide), a pointed dome ({@link DomeKind#POINTED}, two arcs
     * meeting at the top, higher), or a pointed dome with eight ribs of trim ({@link DomeKind#RIBBED}); of the spires of the
     * look, a shell one block thick (hollow, so that it vaults a hall seen from below), every ledge of it a stair leaning
     * inwards or a slab, and a gilt finial on its top (see {@link #finial}). Its foot is the disc of {@link Kit#inDisc}: it
     * fits a {@link #drum} of the same diameter. Layers from foot to apex ({@link #domeLayers}): 5 wide 3 smooth or 4
     * pointed, 7 wide 4 or 6, 9 wide 5 or 8, 11 wide 6 or 9, 13 wide 7 or 11, 15 wide 8 or 13; the finial adds 1, 2 or 5.
     * Domes from 9 wide read round from afar; 5 and 7 wide suit side chambers, kiosks and the tops of minarets.
     *
     * @return the layer above the finial
     */
    static int dome(Structure s, int cx, int y, int cz, int diameter, DomeKind kind) {
        boolean pointed = kind != DomeKind.SMOOTH;
        return dome(s, cx, y, cz, diameter, pointed, DOME, kind == DomeKind.RIBBED ? TRIMS : null, finialSize(diameter));
    }

    /**
     * A dome of the given materials: its shell of {@code shell}, eight ribs of {@code ribs} along the axes and the diagonals
     * (null for none) and a finial of the given size.
     *
     * @return the layer above the finial
     */
    static int dome(Structure s, int cx, int y, int cz, int diameter, boolean pointed, Kit.Roof shell, Kit.Roof ribs, int finial) {
        int r = (diameter - 1) / 2;
        double rho = r + 0.5;
        int layers = domeLayers(diameter, pointed);
        int n = 2 * r + 1;
        // in[h]: the cells of layer h, a disc whose radius follows the profile at the middle of the layer.
        boolean[][][] in = new boolean[layers + 1][n][n];
        for (int h = 0; h < layers; h++) {
            double rm = domeRadius(rho, pointed, h + 0.5);
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    in[h][dx + r][dz + r] = rm > 0 && dx * dx + dz * dz <= rm * rm || dx == 0 && dz == 0;
                }
            }
        }
        // The apex stands for the layer over it, so that the stairs round it lean against it.
        in[layers][r][r] = true;
        for (int h = 0; h < layers; h++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!in[h][dx + r][dz + r]) {
                        continue;
                    }
                    boolean covered = in[h + 1][dx + r][dz + r];
                    if (covered && hollow(in, h, dx + r, dz + r, n)) {
                        continue;
                    }
                    Kit.Roof m = ribs != null && onRib(dx, dz) ? ribs : shell;
                    boolean apex = h == layers - 1 && dx == 0 && dz == 0;
                    put(s, cx + dx, y + h, cz + dz, covered || apex ? m.block() : slope(in[h + 1], dx, dz, r, m));
                }
            }
        }
        return finial(s, cx, y + layers, cz, finial);
    }

    /**
     * How many layers a dome of the given diameter takes from its foot to its apex (the finial stands on the layer above).
     * The highest free cell over its middle inside is two layers under the apex.
     */
    static int domeLayers(int diameter, boolean pointed) {
        double rho = (diameter - 1) / 2 + 0.5;
        int h = 0;
        while (domeRadius(rho, pointed, h + 0.5) > 0.3) {
            h++;
        }
        return Math.max(1, h);
    }

    private static double domeRadius(double rho, boolean pointed, double t) {
        if (pointed) {
            double rc = 2 * rho;
            double v = rc * rc - t * t;
            return v <= 0 ? -1 : Math.sqrt(v) - (rc - rho);
        }
        double height = rho * 1.05;
        return t >= height ? -1 : rho * Math.sqrt(1 - t / height * (t / height));
    }

    private static boolean hollow(boolean[][][] full, int h, int i, int j, int n) {
        if (h + 1 >= full.length || !full[h + 1][i][j]) {
            return false;
        }
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            int a = i + d[0];
            int b = j + d[1];
            if (a < 0 || b < 0 || a >= n || b >= n || !full[h][a][b]) {
                return false;
            }
        }
        return true;
    }

    /** A cell of a dome that is filled only in its lower half: a stair leaning to the full cells inside it, or a slab. */
    private static String slope(boolean[][] full, int dx, int dz, int r, Kit.Roof m) {
        int sx = Integer.signum(dx);
        int sz = Integer.signum(dz);
        boolean fx = dx != 0 && full[dx - sx + r][dz + r];
        boolean fz = dz != 0 && full[dx + r][dz - sz + r];
        if (fx && fz && Math.abs(dx) == Math.abs(dz)) {
            return stairs(m.stairs(), dz > 0 ? "north" : "south", "bottom", dz > 0 == dx > 0 ? "outer_left" : "outer_right");
        }
        if (fx && (!fz || Math.abs(dx) >= Math.abs(dz))) {
            return Kit.stairs(m.stairs(), dx > 0 ? "west" : "east");
        }
        if (fz) {
            return Kit.stairs(m.stairs(), dz > 0 ? "north" : "south");
        }
        return Kit.slab(m.slab());
    }

    /** Whether a column of a dome lies on one of its eight ribs: the axes and the diagonals from its middle. */
    private static boolean onRib(int dx, int dz) {
        return dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
    }

    /** The finial that suits a dome of the given diameter: 0 up to 5 wide, 1 up to 9, 2 wider. */
    static int finialSize(int diameter) {
        return diameter <= 5 ? 0 : diameter <= 9 ? 1 : 2;
    }

    /**
     * A gilt finial (jamur) standing on layer {@code y}: 0 a gilt rod; 1 a gilt ball under a rod; 2 two gilt balls on a rod,
     * one over the other, under a long needle.
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
        put(s, x, y + 1, z, Kit.chain());
        put(s, x, y + 2, z, GILT);
        put(s, x, y + 3, z, ROD);
        put(s, x, y + 4, z, ROD);
        return y + 5;
    }

    /**
     * A round drum under a dome standing on layer {@code y} around (cx, cz): a ring of wall {@code diameter} wide and
     * {@code height} layers high (hollow), its lowest and top courses bands of trim with a cornice of trim corbels one block
     * out round the top, and windows of {@code glass} (null for none) in its middle layers at the four quarters (and the four
     * diagonals from 9 wide). Standing on a flat roof it should rise clear of the parapet: 4 to 6 layers high, and at least
     * two blocks narrower than the roof inside its walls. To open a hall into the dome, clear the deck inside the drum.
     *
     * @return the layer above it, where a dome of the same diameter springs
     */
    static int drum(Structure s, int cx, int y, int cz, int diameter, int height, String glass) {
        int r = (diameter - 1) / 2;
        int top = y + height - 1;
        for (int yy = y; yy <= top; yy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!onRim(dx, dz, r, ROUND)) {
                        continue;
                    }
                    boolean window = glass != null && yy > y && yy < top
                            && (dx == 0 || dz == 0 || r >= 4 && Math.abs(dx) == Math.abs(dz));
                    put(s, cx + dx, yy, cz + dz, window ? glass : yy == top || yy == y ? TRIM : WALL);
                }
            }
        }
        corbels(s, cx, top, cz, r + 1, ROUND);
        return y + height;
    }

    // ---------------------------------------------------------------- minarets

    /**
     * A minaret standing on layer {@code y} around (cx, cz), about {@code height} layers high to the tip of its finial and
     * {@code width} wide (3, 5 or 7): a plinth of rough stone, a square shaft of wall to some two fifths of its height ending
     * in a band of trim, then an eight-sided shaft (a cross of five blocks at width 3) with a band of glazed tiles; the
     * balcony on a ring of corbels, a block wider than the shaft on every side, with a balustrade of rough stone posts;
     * over the balcony a lantern: four posts three layers high under a lintel of trim (open between them, a lamp hanging
     * inside; a single post at width 3), a cornice and a small pointed dome as wide as the shaft with its gilt finial. A
     * minaret 5 or 7 wide is hollow: a door at its foot looking {@code door} (null for none), a ladder up the inside opposite
     * the door through the floor of the balcony, small windows and lamps; one stands in the lantern and walks out on the
     * balcony. A minaret 3 wide is solid. Heights of 20 to 45 suit it; the balcony lies about ten layers under the top.
     *
     * @return the layer of the floor of the balcony (one stands on the layer above it)
     */
    static int minaret(Structure s, int cx, int y, int cz, int width, int height, String door) {
        int h = Math.max(1, Math.min(3, (width - 1) / 2));
        boolean hollow = h >= 2;
        int lantern = h >= 2 ? 4 : 2;
        int domeWidth = 2 * h + 1;
        int domeH = domeLayers(domeWidth, true);
        int balcony = y + height - (lantern + 1 + domeH + finialSize(domeWidth) + 1);
        int square = y + Math.max(3, (balcony - y) * 2 / 5);
        // plinth and square shaft
        plate(s, cx, y, cz, h, SQUARE, BASE);
        shell(s, cx, y + 1, y + 1, cz, h, SQUARE, BASE);
        for (int yy = y + 2; yy < square; yy++) {
            if (hollow) {
                shell(s, cx, yy, yy, cz, h, SQUARE, WALL);
            } else {
                plate(s, cx, yy, cz, h, SQUARE, WALL);
            }
        }
        if (hollow) {
            plate(s, cx, y, cz, h - 1, SQUARE, PAVING);
            shell(s, cx, square, square, cz, h, SQUARE, TRIM);
        } else {
            plate(s, cx, square, cz, h, SQUARE, TRIM);
        }
        // the corners of the square chamfered under the eight-sided shaft
        for (int[] c : new int[][]{{h, h}, {-h, h}, {h, -h}, {-h, -h}}) {
            if (!inPlan(c[0], c[1], h, OCTAGON)) {
                put(s, cx + c[0], square, cz + c[1], rimStairs(TRIM_STAIRS, c[0], c[1], h, SQUARE, false));
            }
        }
        int band = (square + balcony) / 2;
        for (int yy = square + 1; yy < balcony; yy++) {
            String state = yy == band ? glazed(TILE_CYAN, "north") : WALL;
            if (hollow) {
                shell(s, cx, yy, yy, cz, h, OCTAGON, state);
            } else {
                plate(s, cx, yy, cz, h, OCTAGON, state);
            }
        }
        // the balcony
        corbels(s, cx, balcony - 1, cz, h + 1, OCTAGON);
        put(s, cx, balcony - 1, cz, hollow ? Structure.AIR : WALL);
        plate(s, cx, balcony, cz, h + 1, OCTAGON, TRIM);
        for (int dz = -h - 1; dz <= h + 1; dz++) {
            for (int dx = -h - 1; dx <= h + 1; dx++) {
                if (onRim(dx, dz, h + 1, OCTAGON)) {
                    put(s, cx + dx, balcony + 1, cz + dz, BASE_POST);
                }
            }
        }
        // the lantern: posts, lintel, cornice and dome
        int p = h - 1;
        for (int yy = balcony + 1; yy < balcony + lantern; yy++) {
            if (p == 0) {
                put(s, cx, yy, cz, WALL);
            } else {
                for (int[] c : new int[][]{{p, p}, {-p, p}, {p, -p}, {-p, -p}}) {
                    put(s, cx + c[0], yy, cz + c[1], WALL);
                }
            }
        }
        int lintel = balcony + lantern;
        plate(s, cx, lintel, cz, Math.max(1, p), p == 0 ? OCTAGON : SQUARE, TRIM);
        corbels(s, cx, lintel + 1, cz, h, OCTAGON);
        plate(s, cx, lintel + 1, cz, h - 1, OCTAGON, TRIM);
        if (p > 0) {
            put(s, cx, lintel - 1, cz, Kit.hangingLantern());
        }
        dome(s, cx, lintel + 2, cz, domeWidth, true, DOME, null, finialSize(domeWidth));
        if (hollow) {
            String[] dirs = {"north", "south", "east", "west"};
            String to = door == null ? "south" : door;
            int[] d = Furnish.step(to);
            // the ladder on the wall opposite the door, up to the floor of the balcony
            int lx = cx - d[0] * (h - 1);
            int lz = cz - d[1] * (h - 1);
            for (int yy = y + 1; yy <= balcony; yy++) {
                put(s, lx, yy, lz, Kit.ladder(to));
            }
            if (door != null) {
                s.door(cx + d[0] * h, y + 1, cz + d[1] * h, DOOR, Furnish.opposite(door), "left");
            }
            // lamps on corbels inside, windows in the faces
            int[] side = Furnish.step(Furnish.left(to));
            for (int yy = y + 3; yy < balcony - 1; yy += 6) {
                put(s, cx + side[0] * (h - 1) + d[0] * (h - 1), yy + 1, cz + side[1] * (h - 1) + d[1] * (h - 1), TRIM);
                put(s, cx + side[0] * (h - 1) + d[0] * (h - 1), yy, cz + side[1] * (h - 1) + d[1] * (h - 1), Kit.hangingLantern());
            }
            for (int yy = y + 5; yy < balcony - 2; yy += 6) {
                for (String dir : dirs) {
                    if (dir.equals(to)) {
                        continue;
                    }
                    int[] w = Furnish.step(dir);
                    put(s, cx + w[0] * h, yy, cz + w[1] * h, GLASS);
                }
            }
        }
        return balcony;
    }

    // ---------------------------------------------------------------- arches

    private static final int OPEN = 0;
    private static final int SOLID = 1;
    /** Only the lower half open: a slab at the top of the cell. */
    private static final int LOW = 2;
    /** Only the upper half open: a slab at the bottom. */
    private static final int HIGH = 3;
    /** Only the lower inner quarter open: stairs upside down looking out of the arch. */
    private static final int HAUNCH = 4;
    /** Only the upper inner quarter open: stairs looking out of the arch. */
    private static final int FOOT = 5;

    /** Whether a point (x across from the middle, y above the springing) lies inside the opening of an arch of half-width a. */
    private static boolean opening(Arch kind, int a, double x, double y) {
        double rho = a + 0.5;
        if (y < 0) {
            return x < rho;
        }
        return switch (kind) {
            case ROUND -> x * x + y * y <= rho * rho;
            case POINTED -> {
                double c = 0.6 * rho;
                yield (x + c) * (x + c) + y * y <= (rho + c) * (rho + c);
            }
            case HORSESHOE -> {
                double rr = rho + 1;
                double e = Math.sqrt(rr * rr - rho * rho);
                yield x * x + (y - e) * (y - e) <= rr * rr;
            }
        };
    }

    private static double centreHeight(Arch kind, int a) {
        if (kind != Arch.HORSESHOE) {
            return 0;
        }
        double rho = a + 0.5;
        return Math.sqrt((rho + 1) * (rho + 1) - rho * rho);
    }

    /** What a cell of the head of an arch is: u blocks from its middle, k rows above the springing. */
    private static int headCell(Arch kind, int a, int u, int k) {
        double xi = u == 0 ? 0.25 : u - 0.25;
        double xo = u + 0.25;
        boolean li = opening(kind, a, xi, k + 0.25);
        boolean lo = opening(kind, a, xo, k + 0.25);
        boolean ui = opening(kind, a, xi, k + 0.75);
        boolean uo = opening(kind, a, xo, k + 0.75);
        int n = (li ? 1 : 0) + (lo ? 1 : 0) + (ui ? 1 : 0) + (uo ? 1 : 0);
        if (n == 4) {
            return OPEN;
        }
        if (n == 0) {
            return SOLID;
        }
        if (li && lo && !ui && !uo) {
            return LOW;
        }
        if (ui && uo && !li && !lo) {
            return HIGH;
        }
        if (n == 1 && li) {
            return HAUNCH;
        }
        if (n == 1 && ui) {
            return FOOT;
        }
        if (n == 2 && li && ui) {
            return k + 0.5 < centreHeight(kind, a) ? FOOT : HAUNCH;
        }
        return opening(kind, a, u, k + 0.5) ? OPEN : SOLID;
    }

    private static int archCell(Arch kind, int a, int height, int u, int row) {
        if (row < 0) {
            return SOLID;
        }
        if (row < height) {
            return Math.abs(u) <= a ? OPEN : SOLID;
        }
        return headCell(kind, a, Math.abs(u), row - height);
    }

    /**
     * An arched opening through a wall whose face looks {@code facing}: (x, y, z) the middle of its threshold in the face,
     * {@code width} wide (odd), its straight jambs {@code height} layers high and over them the head of the given kind, cut
     * {@code depth} blocks into the wall (the face and the layers behind it). The curve is drawn with stairs and slabs: of
     * trim in the face, of wall behind it. In the face a ring of voussoirs follows the opening, rough stone and trim in turn
     * (ablaq), with a carved keystone and carved imposts (an arch one wide has only its side stones and keystone); with
     * {@code frame} a rectangular frame of rough stone (alfiz) stands a block clear of the ring from the imposts up, two
     * carved spandrels in its upper corners. A horseshoe arch swells one block wider than its jambs on each side (a door 3 wide
     * takes 7 with its ring and 11 with its frame, and its head is 5 rows high); a pointed arch rises to a point (its head
     * is 2 rows high at width 3, 4 at width 5, 6 at width 7); a round one is a half circle; an opening one wide with a
     * horseshoe head is a keyhole. The open cells of the face get {@code fill} (glass for a window; null leaves them open).
     *
     * @return the layer above the top of its ring (or of its frame)
     */
    static int arch(Structure s, int x, int y, int z, String facing, int width, int height, int depth, Arch kind, String fill, boolean frame) {
        Face f = new Face(x, z, facing);
        int a = Math.max(0, (width - 1) / 2);
        int reach = a + 3;
        int head = 0;
        while (head < 4 * a + 8) {
            boolean any = false;
            for (int u = 0; u <= reach; u++) {
                any |= headCell(kind, a, u, head) != SOLID;
            }
            if (!any) {
                break;
            }
            head++;
        }
        int total = height + head;
        for (int v = 0; v < Math.max(1, depth); v++) {
            Kit.Roof m = v == 0 ? TRIMS : STONE;
            for (int row = 0; row < total; row++) {
                for (int u = -reach; u <= reach; u++) {
                    int c = archCell(kind, a, height, u, row);
                    if (c == SOLID) {
                        continue;
                    }
                    String out = u > 0 ? f.plus() : f.minus();
                    String state = switch (c) {
                        case OPEN -> v == 0 && fill != null ? fill : Structure.AIR;
                        case LOW -> Kit.topSlab(m.slab());
                        case HIGH -> Kit.slab(m.slab());
                        case HAUNCH -> Kit.upside(m.stairs(), out);
                        default -> Kit.stairs(m.stairs(), out);
                    };
                    f.put(s, u, y + row, -v, state);
                }
            }
        }
        // The ring of voussoirs on the face.
        int ringTop = height - 1;
        int ringReach = a + 1;
        for (int row = height - 1; row <= total; row++) {
            for (int u = -reach - 1; u <= reach + 1; u++) {
                if (archCell(kind, a, height, u, row) != SOLID && row < total) {
                    continue;
                }
                boolean touches = false;
                for (int dr = -1; dr <= 1 && !touches; dr++) {
                    for (int du = -1; du <= 1 && !touches; du++) {
                        // a narrow arch has its ring only beside and over its head, a wider one all round it
                        int nr = row + dr;
                        touches = (a > 0 || dr == 0 || du == 0) && nr >= height && nr < total && archCell(kind, a, height, u + du, nr) != SOLID;
                    }
                }
                if (!touches) {
                    continue;
                }
                ringTop = Math.max(ringTop, row);
                ringReach = Math.max(ringReach, Math.abs(u));
                boolean impost = row == height - 1;
                f.put(s, u, y + row, 0, impost ? CARVED : Math.floorMod(Math.abs(u) + row, 2) == 0 ? BASE : TRIM);
            }
        }
        f.put(s, 0, y + ringTop, 0, CARVED);
        int top = ringTop;
        if (frame) {
            // The frame stands a block clear of the ring, so that the spandrels of wall show between them.
            int w = ringReach + 2;
            top = ringTop + 2;
            for (int row = height - 1; row <= top; row++) {
                for (int u = -w; u <= w; u++) {
                    if (Math.abs(u) == w || row == top) {
                        f.put(s, u, y + row, 0, BASE);
                    }
                }
            }
            f.put(s, -w + 1, y + top - 1, 0, CARVED);
            f.put(s, w - 1, y + top - 1, 0, CARVED);
        }
        return y + top + 1;
    }

    /**
     * A doorway: an {@link #arch} with a door in it. The door (of timber) stands in the middle of the face at {@code y}; the
     * rest of the jambs is closed with leaves of timber planks, and the open part of the head with a lattice, as the great
     * doors of the style are made. One enters toward the inside of the wall (opposite to {@code facing}).
     *
     * @return the layer above the top of its ring (or of its frame)
     */
    static int doorway(Structure s, int x, int y, int z, String facing, int width, int height, int depth, Arch kind, boolean frame) {
        int top = arch(s, x, y, z, facing, width, height, depth, kind, null, frame);
        Face f = new Face(x, z, facing);
        int a = Math.max(0, (width - 1) / 2);
        for (int row = 0; row < top - y; row++) {
            for (int u = -a - 2; u <= a + 2; u++) {
                if (archCell(kind, a, height, u, row) != OPEN) {
                    continue;
                }
                String state;
                if (u == 0 && row < 2) {
                    continue;
                } else if (row < height) {
                    state = PLANKS;
                } else {
                    state = Kit.shutter(LATTICE, f.in());
                }
                f.put(s, u, y + row, 0, state);
            }
        }
        s.door(f.px(0, 0), y, f.pz(0, 0), DOOR, f.in(), "left");
        return top;
    }

    /**
     * An arcade along x or z from (x1, z1) to (x2, z2) whose face looks {@code facing} (into a court): a wall of one block
     * from layer {@code y} to {@code top} pierced by arches {@code span} wide (odd) with jambs {@code height} high, between
     * slender columns of rough stone posts with carved capitals; the ends are solid piers. The bays are set out from the
     * middle; what is left over widens the end piers. Use pointed or round arches: a horseshoe swells into its columns
     * unless the bays are three blocks wider than the span. The gallery behind it and its roof are the caller's.
     */
    static void arcade(Structure s, int x1, int z1, int x2, int z2, int y, int height, int top, String facing, int span, Arch kind) {
        boolean alongX = z1 == z2;
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int c = alongX ? z1 : x1;
        for (int a = a1; a <= a2; a++) {
            for (int yy = y; yy <= top; yy++) {
                put(s, alongX ? a : c, yy, alongX ? c : a, WALL);
            }
        }
        int bay = span + 1;
        int bays = (a2 - a1) / bay;
        int off = (a2 - a1 - bays * bay) / 2;
        for (int k = 0; k < bays; k++) {
            int mid = a1 + off + k * bay + (span + 1) / 2;
            arch(s, alongX ? mid : c, y, alongX ? c : mid, facing, span, height, 1, kind, null, false);
        }
        for (int k = 1; k < bays; k++) {
            int p = a1 + off + k * bay;
            int px = alongX ? p : c;
            int pz = alongX ? c : p;
            put(s, px, y, pz, BASE);
            for (int yy = y + 1; yy < y + height - 1; yy++) {
                put(s, px, yy, pz, BASE_POST);
            }
            put(s, px, y + height - 1, pz, CARVED);
        }
    }

    // ---------------------------------------------------------------- lattice screens

    /**
     * A lattice screen (mashrabiya) in a window of a wall whose face looks {@code facing}: (x, y, z) the middle of its lowest
     * row in the face, {@code width} wide and {@code height} high. Flat: the opening is filled with a wooden lattice flush with
     * the face, a sill of trim under it and a hood of rough stone over it, one block out. As an oriel ({@code oriel}): the
     * opening is left open into the room and a box of lattice juts out one block before it, its front and sides of lattice
     * (it takes a block more on each side), its floor on corbels of trim with a pendant under the middle, a little roof of
     * timber slabs on top.
     */
    static void lattice(Structure s, int x, int y, int z, String facing, int width, int height, boolean oriel) {
        Face f = new Face(x, z, facing);
        int hw = Math.max(0, (width - 1) / 2);
        if (!oriel) {
            for (int u = -hw; u <= hw; u++) {
                for (int r = 0; r < height; r++) {
                    f.put(s, u, y + r, 0, Kit.shutter(LATTICE, f.in()));
                }
            }
            for (int u = -hw; u <= hw; u++) {
                f.put(s, u, y - 1, 1, Kit.upside(TRIM_STAIRS, f.in()));
                f.put(s, u, y + height, 1, Kit.slab(BASE_SLAB));
            }
            return;
        }
        for (int u = -hw; u <= hw; u++) {
            for (int r = 0; r < height; r++) {
                f.put(s, u, y + r, 0, Structure.AIR);
                f.put(s, u, y + r, 1, Kit.shutter(LATTICE, f.in()));
            }
        }
        for (int r = 0; r < height; r++) {
            f.put(s, hw + 1, y + r, 1, Kit.shutter(LATTICE, f.plus()));
            f.put(s, -hw - 1, y + r, 1, Kit.shutter(LATTICE, f.minus()));
        }
        for (int u = -hw - 1; u <= hw + 1; u++) {
            f.put(s, u, y - 1, 1, Kit.upside(TRIM_STAIRS, f.in()));
            f.put(s, u, y + height, 1, Kit.slab(SLAB));
        }
        f.put(s, 0, y - 2, 1, Kit.upside(TRIM_STAIRS, f.in()));
    }

    // ---------------------------------------------------------------- wind towers

    /**
     * A wind tower (badgir) rising from a flat roof whose deck is at layer {@code y}: a square shaft {@code width} wide (3 or
     * 5) around (cx, cz), hollow, its walls rising {@code height} layers above the deck (5 to 8); in its top four layers tall
     * open vents on all four faces (one in the middle of a face 3 wide, two beside a fin on a face 5 wide) over a band of
     * trim, a flat cap of trim with a slab of rough stone and a pinnacle on each corner.
     *
     * @return the layer above its top
     */
    static int windTower(Structure s, int cx, int y, int cz, int width, int height) {
        int h = Math.max(1, (width - 1) / 2);
        int top = y + height;
        for (int yy = y + 1; yy <= top; yy++) {
            shell(s, cx, yy, yy, cz, h, SQUARE, WALL);
        }
        int vents = Math.min(4, height - 1);
        for (int yy = top - vents + 1; yy <= top; yy++) {
            for (int u = -h + 1; u <= h - 1; u++) {
                if (h >= 2 && u == 0) {
                    continue;
                }
                put(s, cx + u, yy, cz - h, Structure.AIR);
                put(s, cx + u, yy, cz + h, Structure.AIR);
                put(s, cx - h, yy, cz + u, Structure.AIR);
                put(s, cx + h, yy, cz + u, Structure.AIR);
            }
        }
        // a band of trim under the vents, the cap over them
        shell(s, cx, top - vents, top - vents, cz, h, SQUARE, TRIM);
        plate(s, cx, top + 1, cz, h, SQUARE, TRIM);
        plate(s, cx, top + 2, cz, h - 1, SQUARE, Kit.slab(BASE_SLAB));
        for (int[] c : new int[][]{{h, h}, {-h, h}, {h, -h}, {-h, -h}}) {
            put(s, cx + c[0], top + 2, cz + c[1], WALL);
            put(s, cx + c[0], top + 3, cz + c[1], Kit.slab(BASE_SLAB));
        }
        return top + 4;
    }

    // ---------------------------------------------------------------- towers

    /**
     * A square tower of a kasbah or a castle standing on layer {@code y} around (cx, cz), {@code width} wide (odd, 5 or more)
     * and rising {@code height} layers to its roof deck: a battered foot two blocks wider than the shaft for its lowest third
     * (rough stone at the ground, its top sloping in with stairs), the shaft of wall, its upper part drawn in by one block
     * on every side from two thirds of its height (so the tower tapers in two steps, as the towers of mud brick do), bands of
     * trim at the steps, narrow slits on every face every four layers, and at its top a parapet jutting out one block on a
     * ring of corbels (machicolations) crowned with merlons of the given kind; the deck of the roof inside the parapet. The
     * shaft is hollow: its floors, stairs and doors are the caller's (the inside is {@code width - 2} wide below the upper
     * step and {@code width - 4} above it).
     *
     * @return the layer of the roof deck (one stands on the layer above it)
     */
    static int tower(Structure s, int cx, int y, int cz, int width, int height, Merlons merlons) {
        int h = Math.max(2, (width - 1) / 2);
        int deck = y + height;
        int foot = y + Math.max(2, height / 3);
        int step = y + height * 2 / 3;
        int hu = h - 1;
        // the battered foot
        for (int yy = y; yy < foot; yy++) {
            shell(s, cx, yy, yy, cz, h + 1, SQUARE, yy == y ? BASE : WALL);
            plate(s, cx, yy, cz, h, SQUARE, Structure.AIR);
        }
        for (int dz = -h - 1; dz <= h + 1; dz++) {
            for (int dx = -h - 1; dx <= h + 1; dx++) {
                if (inPlan(dx, dz, h + 1, SQUARE) && !inPlan(dx, dz, h, SQUARE)) {
                    put(s, cx + dx, foot, cz + dz, rimStairs(BASE_STAIRS, dx, dz, h + 1, SQUARE, false));
                }
            }
        }
        // the shaft in two stages
        for (int yy = foot; yy < deck; yy++) {
            int r = yy < step ? h : hu;
            shell(s, cx, yy, yy, cz, r, SQUARE, yy == foot || yy == step - 1 ? TRIM : WALL);
            if (yy == step && hu < h) {
                for (int dz = -h; dz <= h; dz++) {
                    for (int dx = -h; dx <= h; dx++) {
                        if (inPlan(dx, dz, h, SQUARE) && !inPlan(dx, dz, hu, SQUARE)) {
                            put(s, cx + dx, yy, cz + dz, rimStairs(TRIM_STAIRS, dx, dz, h, SQUARE, false));
                        }
                    }
                }
            }
            int rr = yy < step ? h : hu;
            if ((yy - y) % 4 == 2 && yy + 1 < deck - 1) {
                for (int[] d : new int[][]{{0, rr}, {0, -rr}, {rr, 0}, {-rr, 0}}) {
                    put(s, cx + d[0], yy, cz + d[1], Structure.AIR);
                    put(s, cx + d[0], yy + 1, cz + d[1], Structure.AIR);
                }
            }
        }
        // the corbelled parapet and the deck
        corbels(s, cx, deck - 1, cz, hu + 1, SQUARE);
        plate(s, cx, deck, cz, hu, SQUARE, ROOF.block());
        shell(s, cx, deck, deck + 1, cz, hu + 1, SQUARE, WALL);
        int a1 = cx - hu - 1;
        int a2 = cx + hu + 1;
        int b1 = cz - hu - 1;
        int b2 = cz + hu + 1;
        crown(s, true, a1, a2, b1, deck + 2, merlons);
        crown(s, true, a1, a2, b2, deck + 2, merlons);
        crown(s, false, b1, b2, a1, deck + 2, merlons);
        crown(s, false, b1, b2, a2, deck + 2, merlons);
        for (int[] c : new int[][]{{a1, b1}, {a2, b1}, {a1, b2}, {a2, b2}}) {
            put(s, c[0], deck + 2, c[1], WALL);
            put(s, c[0], deck + 3, c[1], Kit.slab(BASE_SLAB));
        }
        return deck;
    }

    // ---------------------------------------------------------------- courtyards, fountains, palms

    /**
     * Whether a column lies in an eight-pointed star of the given half-size: a square of half-size {@code r} crossed by a
     * diamond whose points reach {@link #starReach} from the middle.
     */
    static boolean inStar(int dx, int dz, int r) {
        return Math.abs(dx) <= r && Math.abs(dz) <= r || Math.abs(dx) + Math.abs(dz) <= starReach(r);
    }

    /** How far the points of a star of half-size {@code r} reach along the axes: r times the root of two, rounded. */
    static int starReach(int r) {
        return (r * 141 + 50) / 100;
    }

    /** Whether a column lies in a star of half-size {@code r} or next to it (sides and corners). */
    private static boolean nearStar(int dx, int dz, int r) {
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (inStar(dx + a, dz + b, r)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A fountain whose basin is an eight-pointed star (a square of half-size {@code r} crossed by a diamond, 2 or 3) around
     * (cx, cz) on the paving at layer {@code y}: the floor of the basin at {@code y} of glazed tiles laid in a pattern turning
     * round the middle, a low rim of trim slabs at {@code y + 1} following the star, water inside, and in the middle a carved
     * pedestal bearing a small bowl of trim with water in it. Its points reach {@link #starReach} blocks from its middle
     * along the axes: it is 7 wide for {@code r} 2 and 9 wide for 3.
     */
    static void starFountain(Structure s, int cx, int y, int cz, int r) {
        int reach = starReach(r) + 1;
        String[] turn = {"north", "east", "south", "west"};
        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                if (!inStar(dx, dz, r)) {
                    continue;
                }
                boolean rim = !inStar(dx + 1, dz, r) || !inStar(dx - 1, dz, r) || !inStar(dx, dz + 1, r) || !inStar(dx, dz - 1, r);
                int q = (dx >= 0 ? 1 : 0) + (dz >= 0 ? 2 : 0);
                String tile = Math.abs(dx) + Math.abs(dz) <= 1 ? TILE_BLUE : TILE;
                put(s, cx + dx, y, cz + dz, glazed(rim ? TILE_CYAN : tile, turn[q == 0 ? 0 : q == 1 ? 1 : q == 3 ? 2 : 3]));
                put(s, cx + dx, y + 1, cz + dz, rim ? Kit.slab(TRIM_SLAB) : WATER);
            }
        }
        put(s, cx, y + 1, cz, CARVED);
        put(s, cx, y + 2, cz, WATER);
        put(s, cx + 1, y + 2, cz, Kit.upside(TRIM_STAIRS, "west"));
        put(s, cx - 1, y + 2, cz, Kit.upside(TRIM_STAIRS, "east"));
        put(s, cx, y + 2, cz + 1, Kit.upside(TRIM_STAIRS, "north"));
        put(s, cx, y + 2, cz - 1, Kit.upside(TRIM_STAIRS, "south"));
    }

    /**
     * A courtyard (sahn) from (x1, z1) to (x2, z2) paved at layer {@code y}: in the middle a {@link #starFountain}; from 13
     * wide a garden of four beds (chahar bagh): paths three wide in a cross from the fountain to the middles of the sides and
     * a path one wide along the walls, edged with bands of glazed tiles, the beds of grass with flowers and bushes behind a
     * low curb of trim, a palm in each bed (with {@code palms}); smaller courts are paved all over with a palm in each
     * corner. Lamps on posts stand at the corners of the fountain.
     */
    static void courtyard(Structure s, int x1, int z1, int x2, int z2, int y, boolean palms) {
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        int w = x2 - x1 + 1;
        int d = z2 - z1 + 1;
        int star = Math.min(w, d) >= 15 ? 3 : 2;
        boolean garden = Math.min(w, d) >= 13;
        String[] flowers = {"minecraft:red_tulip", "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:poppy", "minecraft:azalea",
                "minecraft:flowering_azalea", "minecraft:pink_tulip", "minecraft:fern"};
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                if (!garden || isPath(x, z, x1, z1, x2, z2, cx, cz, star)) {
                    // the paths: paving, the cross edged with tiles where it meets the beds
                    boolean edge = garden && !nearStar(x - cx, z - cz, star) && (Math.abs(x - cx) == 1 || Math.abs(z - cz) == 1)
                            && x > x1 && x < x2 && z > z1 && z < z2;
                    put(s, x, y, z, edge ? glazed(TILE_CYAN, Math.abs(x - cx) == 1 ? "north" : "east") : PAVING);
                    continue;
                }
                put(s, x, y, z, "minecraft:grass_block[snowy=false]");
                boolean curb = isPath(x + 1, z, x1, z1, x2, z2, cx, cz, star) || isPath(x - 1, z, x1, z1, x2, z2, cx, cz, star)
                        || isPath(x, z + 1, x1, z1, x2, z2, cx, cz, star) || isPath(x, z - 1, x1, z1, x2, z2, cx, cz, star);
                if (curb) {
                    put(s, x, y + 1, z, Kit.slab(TRIM_SLAB));
                } else if (Math.floorMod(x + 2 * z, 3) != 0) {
                    put(s, x, y + 1, z, flowers[Math.floorMod(x * 7 + z * 11, flowers.length)]);
                }
            }
        }
        starFountain(s, cx, y, cz, star);
        for (int[] c : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            // lamps on posts at the corners of the fountain, on the paving between its points
            int lx = cx + c[0] * (star + 1);
            int lz = cz + c[1] * (star + 1);
            if (!inStar(lx - cx, lz - cz, star)) {
                put(s, lx, y, lz, PAVING);
                put(s, lx, y + 1, lz, BASE_POST);
                put(s, lx, y + 2, lz, Kit.lantern());
            }
        }
        if (!palms) {
            return;
        }
        if (garden) {
            int px = (x1 + 1 + cx - 2) / 2;
            int qx = (cx + 2 + x2 - 1 + 1) / 2;
            int pz = (z1 + 1 + cz - 2) / 2;
            int qz = (cz + 2 + z2 - 1 + 1) / 2;
            int[][] spots = {{px, pz}, {qx, pz}, {px, qz}, {qx, qz}};
            String[] leans = {"west", "north", "south", "east"};
            for (int i = 0; i < spots.length; i++) {
                put(s, spots[i][0], y, spots[i][1], "minecraft:grass_block[snowy=false]");
                put(s, spots[i][0], y + 1, spots[i][1], Structure.AIR);
                palm(s, spots[i][0], y + 1, spots[i][1], 7 + (i == 1 || i == 2 ? 2 : 0), leans[i]);
            }
        } else {
            int[][] spots = {{x1 + 1, z1 + 1}, {x2 - 1, z1 + 1}, {x1 + 1, z2 - 1}, {x2 - 1, z2 - 1}};
            for (int[] p : spots) {
                put(s, p[0], y, p[1], "minecraft:grass_block[snowy=false]");
                palm(s, p[0], y + 1, p[1], 6, null);
            }
        }
    }

    /** Whether a column of a garden court is paved: its border, the cross of paths and the ring round the fountain. */
    private static boolean isPath(int x, int z, int x1, int z1, int x2, int z2, int cx, int cz, int star) {
        int dx = x - cx;
        int dz = z - cz;
        return x <= x1 || x >= x2 || z <= z1 || z >= z2 || Math.abs(dx) <= 1 || Math.abs(dz) <= 1 || nearStar(dx, dz, star);
    }

    /**
     * A date palm standing on layer {@code y} at (x, z): a trunk of jungle logs {@code height} layers high (5 to 10) that
     * steps once toward {@code lean} halfway up (null: straight); on its top a crown of leaves that never wither: a heart and
     * a tuft, four fronds along the axes running out level and hanging down at their tips three blocks from the trunk (four
     * from height 8), four shorter ones along the diagonals drooping sooner, and two clusters of dates (cocoa pods) under the
     * crown. The crown reaches 3 (or 4) blocks from the top of the trunk, one layer above it and two below it.
     */
    static void palm(Structure s, int x, int y, int z, int height, String lean) {
        int tx = x;
        int tz = z;
        int bend = lean != null && height >= 5 ? height / 2 : -1;
        for (int k = 0; k < height; k++) {
            if (k == bend) {
                int[] d = Furnish.step(lean);
                String axis = d[0] != 0 ? "x" : "z";
                put(s, tx, y + k, tz, Kit.log(PALM, axis));
                tx += d[0];
                tz += d[1];
                put(s, tx, y + k, tz, Kit.log(PALM, axis));
                continue;
            }
            put(s, tx, y + k, tz, Kit.log(PALM, "y"));
        }
        int ty = y + height;
        boolean big = height >= 8;
        put(s, tx, ty, tz, FROND);
        put(s, tx, ty + 1, tz, FROND);
        // Eight fronds: along the axes they run out level and their tips hang down; along the diagonals they droop sooner.
        int reach = big ? 4 : 3;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int i = 1; i < reach; i++) {
                put(s, tx + d[0] * i, ty, tz + d[1] * i, FROND);
            }
            put(s, tx + d[0] * reach, ty - 1, tz + d[1] * reach, FROND);
            put(s, tx + d[0] * reach, ty - 2, tz + d[1] * reach, FROND);
        }
        for (int[] d : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            put(s, tx + d[0], ty, tz + d[1], FROND);
            put(s, tx + d[0] * 2, ty - 1, tz + d[1] * 2, FROND);
            if (big) {
                put(s, tx + d[0] * 3, ty - 2, tz + d[1] * 3, FROND);
            }
        }
        put(s, tx + 1, ty - 1, tz, "minecraft:cocoa[age=2,facing=west]");
        put(s, tx - 1, ty - 1, tz, "minecraft:cocoa[age=2,facing=east]");
    }

    // ---------------------------------------------------------------- awnings

    /**
     * A cloth awning over the area from (x1, z1) to (x2, z2) before a wall, its outer edge on the side {@code facing}: a flat
     * roof of timber slabs (upper halves) at layer {@code y} covered with striped carpets of the cloth and white, on posts of
     * timber fences standing on layer {@code ground} at the outer corners and every three blocks between, with a valance of
     * banners of the cloth hanging from its outer edge between the posts.
     */
    static void awning(Structure s, int x1, int z1, int x2, int z2, int y, int ground, String facing) {
        boolean alongX = facing.equals("north") || facing.equals("south");
        int lo = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int hi = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int outer = switch (facing) {
            case "south" -> Math.max(z1, z2);
            case "north" -> Math.min(z1, z2);
            case "east" -> Math.max(x1, x2);
            default -> Math.min(x1, x2);
        };
        int[] o = Furnish.step(facing);
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                put(s, x, y, z, Kit.topSlab(SLAB));
                int a = alongX ? x : z;
                put(s, x, y + 1, z, Math.floorMod(a, 2) == 0 ? CLOTH_CARPET : "minecraft:white_carpet");
            }
        }
        for (int a = lo; a <= hi; a++) {
            int px = alongX ? a : outer;
            int pz = alongX ? outer : a;
            boolean post = a == lo || a == hi || (a - lo) % 3 == 0;
            if (post) {
                for (int yy = ground; yy < y; yy++) {
                    put(s, px, yy, pz, FENCE);
                }
            } else {
                put(s, px + o[0], y, pz + o[1], Kit.wallBanner("red", facing));
            }
        }
    }

    // ---------------------------------------------------------------- furniture

    /**
     * A divan along a wall from (x1, z1) to (x2, z2) at layer {@code y}, its seat looking {@code facing} (away from the wall).
     * Deep: a row of cushions of the cloth against the wall and before it a seat of timber slabs with a carpet of the cloth
     * before the seat (it takes two rows and the floor before them); shallow: a bench of timber stairs with lattice arms at
     * its ends and a carpet before it.
     */
    static void divan(Structure s, int x1, int z1, int x2, int z2, int y, String facing, boolean deep) {
        int[] f = Furnish.step(facing);
        boolean alongX = z1 == z2;
        int lo = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int hi = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        for (int a = lo; a <= hi; a++) {
            int x = alongX ? a : x1;
            int z = alongX ? z1 : a;
            if (deep) {
                put(s, x, y, z, CLOTH);
                put(s, x + f[0], y, z + f[1], Kit.slab(FLOOR_SLAB));
                put(s, x + 2 * f[0], y, z + 2 * f[1], CLOTH_CARPET);
            } else {
                put(s, x, y, z, Kit.stairs(FLOOR_STAIRS, Furnish.opposite(facing)));
                put(s, x + f[0], y, z + f[1], CLOTH_CARPET);
            }
        }
        if (!deep) {
            String along = alongX ? "east" : "south";
            int[] e = Furnish.step(along);
            put(s, (alongX ? lo : x1) - e[0], y, (alongX ? z1 : lo) - e[1], Kit.shutter(LATTICE, along));
            put(s, (alongX ? hi : x1) + e[0], y, (alongX ? z1 : hi) + e[1], Kit.shutter(LATTICE, Furnish.opposite(along)));
        }
    }

    /** A low tray table of brass at layer {@code y}. */
    static void trayTable(Structure s, int x, int y, int z) {
        put(s, x, y, z, Kit.slab(BRASS_SLAB));
    }

    /**
     * A rug from (x1, z1) to (x2, z2) at layer {@code y}: a border of blue, a field of the cloth and, on a rug at least 5 by
     * 5, a medallion of yellow and white in its middle.
     */
    static void rug(Structure s, int x1, int z1, int x2, int z2, int y) {
        int cx2 = x1 + x2;
        int cz2 = z1 + z2;
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                boolean border = x == Math.min(x1, x2) || x == Math.max(x1, x2) || z == Math.min(z1, z2) || z == Math.max(z1, z2);
                int dx = Math.abs(2 * x - cx2);
                int dz = Math.abs(2 * z - cz2);
                boolean big = Math.abs(x2 - x1) >= 4 && Math.abs(z2 - z1) >= 4;
                String carpet = border ? "minecraft:blue_carpet" : big && dx + dz <= 1 ? "minecraft:white_carpet"
                        : big && dx + dz <= 4 ? "minecraft:yellow_carpet" : CLOTH_CARPET;
                put(s, x, y, z, carpet);
            }
        }
    }

    /**
     * A lamp hanging at layer {@code y} over (x, z): a lantern on a chain up to the first block above it (the ceiling).
     */
    static void lamp(Structure s, int x, int y, int z) {
        int top = y + 1;
        while (s.inside(x, top, z) && !s.solid(x, top, z)) {
            top++;
        }
        for (int yy = y + 1; yy < top; yy++) {
            put(s, x, yy, z, Kit.chain());
        }
        put(s, x, y, z, Kit.hangingLantern());
    }

    /** A cluster of three lamps of a bazaar hanging at different heights around (x, z), the lowest at layer {@code y}. */
    static void lamps(Structure s, int x, int y, int z) {
        lamp(s, x, y, z);
        lamp(s, x + 1, y + 1, z);
        lamp(s, x, y + 2, z + 1);
    }

    /** A jar of clay standing at layer {@code y}: a decorated pot, which holds nothing in this version of the game. */
    static void jar(Structure s, int x, int y, int z) {
        put(s, x, y, z, "minecraft:decorated_pot[facing=south,waterlogged=false]");
    }

    /**
     * A niche in a wall whose face looks {@code facing}: the cell of the wall at (x, y, z) opened with what stands in it
     * ({@code item}, e.g. candles or a jar; null for nothing) and over it a hood of trim (a stair hanging upside down, its
     * foot at the back). Only in a wall two or more blocks thick, the niche in its inner course: in a wall one block thick it
     * would pierce the wall.
     */
    static void niche(Structure s, int x, int y, int z, String facing, String item) {
        put(s, x, y, z, item == null ? Structure.AIR : item);
        put(s, x, y + 1, z, Kit.upside(TRIM_STAIRS, Furnish.opposite(facing)));
    }

    /**
     * A wall fountain before a wall whose face looks {@code facing}: a basin (a water cauldron) at (x, y, z) before the
     * wall, on the wall above it a panel of glazed tiles with a spout (a rod) over the basin, a carved block over the panel.
     */
    static void wallFountain(Structure s, int x, int y, int z, String facing) {
        int[] in = Furnish.step(Furnish.opposite(facing));
        put(s, x, y, z, "minecraft:water_cauldron[level=3]");
        put(s, x + in[0], y + 1, z + in[1], glazed(TILE, facing));
        put(s, x + in[0], y + 2, z + in[1], CARVED);
        put(s, x, y + 1, z, "minecraft:lightning_rod[facing=" + facing + ",powered=false,waterlogged=false]");
    }

    /**
     * A floor of glazed tiles (zellige) from (x1, z1) to (x2, z2) at layer {@code y}: a field of light blue tiles turned so
     * that every four of them make a star, a border of blue.
     */
    static void zellige(Structure s, int x1, int z1, int x2, int z2, int y) {
        String[] turn = {"north", "east", "west", "south"};
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                boolean border = x == Math.min(x1, x2) || x == Math.max(x1, x2) || z == Math.min(z1, z2) || z == Math.max(z1, z2);
                put(s, x, y, z, glazed(border ? TILE_BLUE : TILE, turn[Math.floorMod(x, 2) + 2 * Math.floorMod(z, 2)]));
            }
        }
    }

    /** A brazier standing on layer {@code y}: a post of rough stone with a fire on it. */
    static void brazier(Structure s, int x, int y, int z) {
        put(s, x, y, z, BASE_POST);
        put(s, x, y + 1, z, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
    }
}
