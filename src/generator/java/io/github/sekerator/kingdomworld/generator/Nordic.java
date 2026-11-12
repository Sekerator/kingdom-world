package io.github.sekerator.kingdomworld.generator;

/**
 * The style "nordic": the timber architecture of the Scandinavian north, its stave churches, longhouses and farms. A building
 * of this style is dark tarred timber on a footing of rough stone: walls of upright planks (staves) standing between posts
 * on a lying sill and carrying a wall plate; very steep roofs of shingles, stacked in tiers like those of a stave church
 * (the low roof of the gallery around it, the roof of the aisles, the steep gable of the nave and a turret with a needle on
 * its ridge); the gables edged with boards that cross over the ridge, and carved dragon heads with red tongues looking out
 * from the ends of the ridges. Halls are longhouses whose walls bow out like the hull of a ship, so that their ridge sags
 * towards the gables. Galleries (svalgang) with small arches run around the great buildings; windows are few and small, in
 * carved frames. Poor houses wear roofs of turf; houses in town are of boards painted red, ochre or white with white
 * trims; round towers of stone with a timber storey under a flared cone of shingles stand only in castles. Inside: a long
 * hearth in the middle of the hall, raised sleeping benches with furs along the walls, the high seat between carved pillars,
 * closed beds, a loom and tapestries.
 *
 * <p>The materials are the parts of a look (see {@link Kit}): posts, staves, sills, planks, floors and doors are the timber
 * (oak and spruce, they become the wood of the place, dark oak or spruce in a nordic kingdom); footings, plinths and the
 * cores of walls rough stone; the walls of towers and curtains the wall stone; the roofs of halls, houses and the tiers of
 * stave churches the wooden roof (spruce or dark oak shingles); turrets, needles, cones and the dragon heads the spires (dark
 * oak or deepslate), so that a look sets them apart, darker, from the roofs below them; painted boards the plaster (red,
 * ochre or white); tapestries, pennants and the cloth of beds the cloth; chimneys the chimney stone. Fixed accents that never change, so that a town is no brown mush: turf (grass and moss) on the roofs of
 * the poor, white paint of trims (smooth quartz), red glass for the tongues of the dragons, furs of white, grey and brown
 * carpet. No wood is used as a fixed accent: in a kingdom every wood of a template becomes the wood of the place.
 *
 * <p>This class holds the data and the shapes of the style; buildings are drawn with them by other classes. Coordinates as
 * everywhere: x from west to east, z from the back (north) to the front (south), y up. A shape draws only inside the box of
 * the structure and leaves out what would fall outside it. Roofs are drawn as height fields (see {@link #roof}): every column
 * gets the stair of its slope at the height the field gives, the roof blocks under it down to its lower neighbour, and the
 * corners of hips, valleys and curves are found by themselves.
 */
final class Nordic {
    // ---------------------------------------------------------------- materials

    /** Posts, sills, wall plates and every lying log (timber). */
    static final String LOG = "minecraft:spruce_log";
    /** The staves of the walls: hewn upright planks, a shade lighter than the posts (timber). */
    static final String STAVE = "minecraft:stripped_spruce_log";
    static final String PLANKS = "minecraft:spruce_planks";
    static final String STAIRS = "minecraft:spruce_stairs";
    static final String SLAB = "minecraft:spruce_slab";
    static final String FENCE = "minecraft:spruce_fence";
    static final String DOOR = "minecraft:spruce_door";
    /** The boards of carved frames (timber). */
    static final String TRAPDOOR = "minecraft:oak_trapdoor";
    /** Floors, benches and tables: oak, lighter than the walls (timber). */
    static final String FLOOR = "minecraft:oak_planks";
    static final String FLOOR_SLAB = "minecraft:oak_slab";
    static final String FLOOR_STAIRS = "minecraft:oak_stairs";
    /** The timber as the blocks of a roof: the boards of gables, the hoods of windows (timber). */
    static final Kit.Roof BOARD = new Kit.Roof(PLANKS, STAIRS, SLAB);
    /** The shingles of halls and houses (the roof of a look). */
    static final Kit.Roof ROOF = Kit.SHINGLE;
    /**
     * The shingles of turrets, needles and cones and the carving of dragon heads (the spires of a look: dark oak or
     * deepslate in a nordic kingdom, darker than its roofs).
     */
    static final Kit.Roof SPIRE = Kit.SLATE;
    /** White paint of the trims of painted houses: smooth quartz, a fixed accent. */
    static final Kit.Roof WHITE = new Kit.Roof("minecraft:smooth_quartz", "minecraft:smooth_quartz_stairs", "minecraft:smooth_quartz_slab");
    /** White corner boards of painted houses: a quartz pillar standing up. */
    static final String WHITE_POST = "minecraft:quartz_pillar[axis=y]";
    /** Painted boards of houses in town (plaster: red, ochre or white). */
    static final String PAINT = Kit.PLASTER;
    /** Turf of roofs: grass on a deck of boards, a fixed accent. */
    static final String TURF = "minecraft:grass_block[snowy=false]";
    /** Moss on old turf and on the ridges of turf roofs, a fixed accent. */
    static final String MOSS = "minecraft:moss_block";
    /** Footings, plinths and the curbs of hearths: rough stone. */
    static final String FOOTING = Kit.ROUGH;
    static final String FOOTING_STAIRS = Kit.ROUGH_STAIRS;
    static final String FOOTING_SLAB = Kit.ROUGH_SLAB;
    /** The walls of round towers: the wall stone. */
    static final String STONE = Kit.WALL;
    static final String STONE_STAIRS = Kit.WALL_STAIRS;
    /** The tongue of a dragon head: red glass, a fixed accent. */
    static final String TONGUE = "minecraft:red_stained_glass_pane";
    /** The bed of embers of a hearth. */
    static final String EMBERS = "minecraft:blackstone";
    /** Furs on benches and floors: white, grey and brown carpets. */
    static final String[] FURS = {"minecraft:white_carpet", "minecraft:light_gray_carpet", "minecraft:brown_carpet"};

    /** Rises of a steep roof from its eaves: one layer for the first row (a kick at the eaves), two for every other row. */
    static final int[] STEEP = {1, 2};
    /** Rises of a plain roof: one layer for every row. */
    static final int[] PITCH = {1};
    /** Rises of a needle: one layer for the eaves, then three, then four for every other row. */
    static final int[] NEEDLE = {1, 3, 4};
    /** Rises of a flared cone: one layer at the eaves, then two, then three for every row, so that it curves up to a point. */
    static final int[] FLARE = {1, 2, 3};
    /** Rises of the hull of a longhouse: steep at the eaves, gentler towards the ridge, like an upturned boat. */
    static final int[] HULL = {1, 2, 2, 2, 1};

    /** The value of a height field outside its roof. */
    static final int NONE = Integer.MIN_VALUE;

    private Nordic() {
    }

    // ---------------------------------------------------------------- where a shape may draw

    /**
     * The columns a shape may draw in.
     */
    @FunctionalInterface
    interface Area {
        boolean has(int x, int z);
    }

    /** Every column. */
    static final Area EVERYWHERE = (x, z) -> true;

    /**
     * A height field: the layer of the top of a roof over a column (where the stair of its slope sits), or {@link #NONE}
     * where the roof does not reach.
     */
    @FunctionalInterface
    interface Field {
        int at(int x, int z);
    }

    /** Sets a block if the position lies inside the structure. */
    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static boolean solid(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && s.solid(x, y, z);
    }

    private static String get(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) ? s.get(x, y, z) : Structure.AIR;
    }

    /** A cell of a shape whose length runs along x ({@code alongX}) or z: {@code a} along it, {@code c} across. */
    private static void at(Structure s, boolean alongX, int a, int y, int c, String state) {
        put(s, alongX ? a : c, y, alongX ? c : a, state);
    }

    /**
     * How high a roof stands {@code k} rows in from its eaves: the sum of the first {@code k} rises (the last rise for all
     * the rows after it).
     */
    static int rise(int[] rises, int k) {
        int h = 0;
        for (int i = 0; i < k; i++) {
            h += rises[Math.min(i, rises.length - 1)];
        }
        return h;
    }

    /** The side of a column of a round plan that looks to its middle. */
    private static String inward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? "west" : "east";
        }
        return dz > 0 ? "north" : "south";
    }

    // ---------------------------------------------------------------- roofs as height fields

    private static final String[] SIDES = {"north", "south", "west", "east"};
    private static final int[][] STEPS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    /**
     * A roof drawn from a height field over the columns {@code x1..x2, z1..z2} of {@code area}: in every column a stair at
     * the layer of the field whose high side looks to its higher neighbour (an outer corner where only a diagonal one is
     * higher, an inner corner where two that meet are), a full block where none is higher (the ridge, the point), and the
     * blocks of the roof under it down to the layer above its lowest neighbour, so that the roof is closed seen from
     * outside. Neighbours outside {@code area} still shape the stairs: a field may give columns that are not drawn, such
     * as a wall the roof leans on.
     */
    static void roof(Structure s, Field f, Area area, int x1, int z1, int x2, int z2, Kit.Roof roof) {
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                if (!area.has(x, z)) {
                    continue;
                }
                int h = f.at(x, z);
                if (h == NONE) {
                    continue;
                }
                int lower = Integer.MAX_VALUE;
                for (int[] d : STEPS) {
                    int n = f.at(x + d[0], z + d[1]);
                    if (n != NONE && n < h) {
                        lower = Math.min(lower, n);
                    }
                }
                if (lower != Integer.MAX_VALUE) {
                    for (int y = lower + 1; y < h; y++) {
                        put(s, x, y, z, roof.block());
                    }
                }
                put(s, x, h, z, top(f, x, z, h, roof.stairs(), roof.block()));
            }
        }
    }

    /**
     * The block on top of a column of a height field: a stair of its slope, a corner or a full block.
     */
    private static String top(Field f, int x, int z, int h, String stairs, String block) {
        boolean[] up = new boolean[4];
        int count = 0;
        for (int i = 0; i < 4; i++) {
            int n = f.at(x + STEPS[i][0], z + STEPS[i][1]);
            if (n != NONE && n > h) {
                up[i] = true;
                count++;
            }
        }
        if (count == 1) {
            for (int i = 0; i < 4; i++) {
                if (up[i]) {
                    return Kit.stairs(stairs, SIDES[i]);
                }
            }
        }
        if (count == 2) {
            String zs = up[0] ? "north" : up[1] ? "south" : null;
            String xs = up[2] ? "west" : up[3] ? "east" : null;
            if (zs != null && xs != null) {
                return Kit.stairs(stairs, zs, xs.equals(Furnish.left(zs)) ? "inner_left" : "inner_right");
            }
            return block;
        }
        if (count == 0) {
            int best = h;
            String zs = null;
            String xs = null;
            for (int dz = -1; dz <= 1; dz += 2) {
                for (int dx = -1; dx <= 1; dx += 2) {
                    int n = f.at(x + dx, z + dz);
                    if (n != NONE && n > best) {
                        best = n;
                        zs = dz < 0 ? "north" : "south";
                        xs = dx < 0 ? "west" : "east";
                    }
                }
            }
            if (zs != null) {
                return Kit.stairs(stairs, zs, xs.equals(Furnish.left(zs)) ? "outer_left" : "outer_right");
            }
        }
        return block;
    }

    /**
     * The height field of a gable roof over walls on the lines {@code x1..x2, z1..z2}: it overhangs them by one block on
     * every side, its eaves at layer {@code y} and every row in rising as {@code rises} says; its ridge runs along x
     * ({@code alongX}) or along z.
     */
    static Field gableField(int x1, int z1, int x2, int z2, int y, boolean alongX, int[] rises) {
        int a1 = (alongX ? x1 : z1) - 1;
        int a2 = (alongX ? x2 : z2) + 1;
        int c1 = (alongX ? z1 : x1) - 1;
        int c2 = (alongX ? z2 : x2) + 1;
        int ridge = (c2 - c1) / 2;
        return (x, z) -> {
            int a = alongX ? x : z;
            int c = alongX ? z : x;
            if (a < a1 || a > a2 || c < c1 || c > c2) {
                return NONE;
            }
            int k = Math.min(c - c1, c2 - c);
            // The ridge rises one layer over the last row, so that the two slopes meet in a sharp ridge.
            return y + (k == ridge && k >= 2 ? rise(rises, k - 1) + 1 : rise(rises, k));
        };
    }

    /**
     * Fills a gable wall on the line {@code a} (x when the ridge runs along x, else z) under the roof of a field, from layer
     * {@code y} up to the roof, across {@code c1..c2}, wherever nothing stands yet.
     */
    static void gableWall(Structure s, Field f, boolean alongX, int a, int c1, int c2, int y, String fill) {
        for (int c = Math.min(c1, c2); c <= Math.max(c1, c2); c++) {
            int x = alongX ? a : c;
            int z = alongX ? c : a;
            int h = f.at(x, z);
            if (h == NONE) {
                continue;
            }
            for (int yy = y; yy < h; yy++) {
                if (!solid(s, x, yy, z)) {
                    put(s, x, yy, z, fill);
                }
            }
        }
    }

    /**
     * A steep gable roof of the style over walls on the lines {@code x1..x2, z1..z2} whose top course (the plate) lies at
     * layer {@code y}: it overhangs the walls by one block on every side, its eaves beside the plate at {@code y}, its rows
     * rising as {@code rises} says ({@link #STEEP}: a kick at the eaves, then two layers for every block in); the gables
     * filled with {@code gable} from the plate up (none when null); at both ends crossed gable boards of {@code boards}
     * (see {@link #crossedBoards}) and dragon heads of {@code dragons} (see {@link #dragonHead}), each left out when null.
     *
     * @param alongX whether the ridge runs along x (gables to the west and the east) or along z (gables to the back and to
     *               the street)
     * @return the layer of the ridge
     */
    static int gableRoof(Structure s, int x1, int z1, int x2, int z2, int y, boolean alongX, int[] rises, Kit.Roof roof, String gable,
                         Kit.Roof boards, Kit.Roof dragons) {
        Field f = gableField(x1, z1, x2, z2, y, alongX, rises);
        roof(s, f, EVERYWHERE, x1 - 1, z1 - 1, x2 + 1, z2 + 1, roof);
        int a1 = alongX ? x1 : z1;
        int a2 = alongX ? x2 : z2;
        int c1 = alongX ? z1 : x1;
        int c2 = alongX ? z2 : x2;
        if (gable != null) {
            gableWall(s, f, alongX, a1, c1, c2, y + 1, gable);
            gableWall(s, f, alongX, a2, c1, c2, y + 1, gable);
        }
        int cm = (c1 + c2) / 2;
        int ridge = alongX ? f.at(a1, cm) : f.at(cm, a1);
        String low = alongX ? "west" : "north";
        String high = alongX ? "east" : "south";
        if (boards != null) {
            crossedBoards(s, alongX, a1 - 1, c1 - 1, c2 + 1, roof, boards, true);
            crossedBoards(s, alongX, a2 + 1, c1 - 1, c2 + 1, roof, boards, true);
        }
        if (dragons != null) {
            dragonHead(s, alongX ? a1 - 1 : cm, ridge + 1, alongX ? cm : a1 - 1, low, dragons);
            dragonHead(s, alongX ? a2 + 1 : cm, ridge + 1, alongX ? cm : a2 + 1, high, dragons);
        }
        return ridge;
    }

    /**
     * A pent roof around a rectangle (the walls x1..x2, z1..z2 it leans on): {@code depth} rings of roof going out from the
     * walls, the outermost (the eaves) at layer {@code y} and every ring in rising as {@code rises} says, turning the corners
     * as hips. The roof over a gallery, the skirt of a turret, a tier of a stave church.
     *
     * @return the layer of the ring against the walls
     */
    static int pentRoof(Structure s, int x1, int z1, int x2, int z2, int y, int depth, int[] rises, Kit.Roof roof) {
        Field f = (x, z) -> {
            int d = Math.max(Math.max(x1 - x, x - x2), Math.max(z1 - z, z - z2));
            if (d <= 0) {
                return y + 1000;
            }
            return d > depth ? NONE : y + rise(rises, depth - d);
        };
        Area ring = (x, z) -> {
            int d = Math.max(Math.max(x1 - x, x - x2), Math.max(z1 - z, z - z2));
            return d >= 1 && d <= depth;
        };
        roof(s, f, ring, x1 - depth, z1 - depth, x2 + depth, z2 + depth, roof);
        return y + rise(rises, depth - 1);
    }

    /**
     * The height field of a cone over a round plan of an odd width (the eaves) around (cx, cz): rings narrowing by one block
     * on every side, the eaves at layer {@code y} and every ring rising as {@code rises} says.
     */
    static Field coneField(int cx, int cz, int y, int width, int[] rises) {
        return (x, z) -> {
            int dx = x - cx;
            int dz = z - cz;
            if (!Kit.inDisc(dx, dz, width)) {
                return NONE;
            }
            int k = 0;
            while (width - 2 * (k + 1) >= 1 && Kit.inDisc(dx, dz, width - 2 * (k + 1))) {
                k++;
            }
            return y + rise(rises, k);
        };
    }

    /**
     * A cone of shingles over a round plan of an odd width (the eaves, one block over the walls under it on every side)
     * around (cx, cz), its eaves at layer {@code y}, its rings rising as {@code rises} says ({@link #FLARE} flares at the
     * eaves), with a vane on its point (see {@link #vane}).
     *
     * @return the layer above the vane
     */
    static int cone(Structure s, int cx, int y, int cz, int width, int[] rises, Kit.Roof roof) {
        Field f = coneField(cx, cz, y, width, rises);
        int r = (width - 1) / 2;
        roof(s, f, EVERYWHERE, cx - r, cz - r, cx + r, cz + r, roof);
        return vane(s, cx, f.at(cx, cz) + 1, cz);
    }

    /**
     * A needle: a steep pyramid of shingles over a square of an odd width (the eaves) around (cx, cz), its eaves at layer
     * {@code y}, its rings rising as {@code rises} says ({@link #NEEDLE}), with a vane on its point.
     *
     * @return the layer above the vane
     */
    static int needle(Structure s, int cx, int y, int cz, int width, int[] rises, Kit.Roof roof) {
        int r = (width - 1) / 2;
        Field f = (x, z) -> {
            int d = Math.max(Math.abs(x - cx), Math.abs(z - cz));
            return d > r ? NONE : y + rise(rises, r - d);
        };
        roof(s, f, EVERYWHERE, cx - r, cz - r, cx + r, cz + r, roof);
        return vane(s, cx, f.at(cx, cz) + 1, cz);
    }

    /**
     * A vane on a point at layer {@code y}: a post with a pennant of cloth on it.
     *
     * @return the layer above it
     */
    static int vane(Structure s, int x, int y, int z) {
        put(s, x, y, z, FENCE);
        put(s, x, y + 1, z, Kit.banner("red", 4));
        return y + 2;
    }

    // ---------------------------------------------------------------- gables: boards and dragons

    /** The same block of another roof: its stairs, slab or full block with the same properties. */
    private static String swap(String state, Kit.Roof from, Kit.Roof to) {
        for (String[] pair : new String[][]{{from.stairs(), to.stairs()}, {from.slab(), to.slab()}}) {
            if (state.startsWith(pair[0] + "[")) {
                return pair[1] + state.substring(pair[0].length());
            }
        }
        return state.equals(from.block()) ? to.block() : state;
    }

    /** Whether a block is one of a roof: its full block, stairs or slab. */
    private static boolean of(String state, Kit.Roof roof) {
        return state.equals(roof.block()) || state.startsWith(roof.stairs() + "[") || state.startsWith(roof.slab() + "[");
    }

    /**
     * Crossed gable boards (vindskier) on the end of a roof drawn with {@code roof}: in the row of the roof on the line
     * {@code a} (the overhanging end, x when the ridge runs along x) from {@code c1} to {@code c2}, the stairs of the roof
     * are drawn of {@code board} instead (with {@code thick} the blocks under them too), so that a board follows each slope;
     * over the ridge the two boards cross and stick out beyond each other as two horns rising outwards on both sides of the
     * ridge. Call it after the roof is drawn.
     */
    static void crossedBoards(Structure s, boolean alongX, int a, int c1, int c2, Kit.Roof roof, Kit.Roof board, boolean thick) {
        int ridge = Integer.MIN_VALUE;
        int ridgeLo = c1;
        int ridgeHi = c1;
        for (int c = Math.min(c1, c2); c <= Math.max(c1, c2); c++) {
            int x = alongX ? a : c;
            int z = alongX ? c : a;
            int y = s.sizeY - 1;
            while (y >= 0 && !of(get(s, x, y, z), roof)) {
                y--;
            }
            if (y < 0) {
                continue;
            }
            if (y > ridge) {
                ridge = y;
                ridgeLo = c;
            }
            if (y == ridge) {
                ridgeHi = c;
            }
            boolean first = true;
            while (y >= 0 && of(get(s, x, y, z), roof) && (first || thick)) {
                put(s, x, y, z, swap(get(s, x, y, z), roof, board));
                first = false;
                y--;
            }
        }
        if (ridge == Integer.MIN_VALUE) {
            return;
        }
        // The horns: each board goes on past the ridge, standing above the slope of the other side.
        String minus = alongX ? "north" : "west";
        String plus = alongX ? "south" : "east";
        for (int side = -1; side <= 1; side += 2) {
            int c = side < 0 ? ridgeLo - 1 : ridgeHi + 1;
            int x = alongX ? a : c;
            int z = alongX ? c : a;
            for (int y = ridge - 1; y < ridge; y++) {
                if (!solid(s, x, y, z)) {
                    put(s, x, y, z, board.block());
                }
            }
            put(s, x, ridge, z, Kit.stairs(board.stairs(), side < 0 ? minus : plus));
        }
    }

    /**
     * A carved dragon head looking out from the end of a ridge to the side {@code facing}: its neck rises from the cell
     * (x, y, z) above the end of the ridge, curls out one block and lifts its head two layers up, the snout reaching two
     * blocks out with the jaw open under it and a red tongue sticking out of it three blocks out; drawn with the stairs and
     * blocks of {@code material} (the spires of a look, whose stairs are dark oak in a nordic kingdom).
     */
    static void dragonHead(Structure s, int x, int y, int z, String facing, Kit.Roof material) {
        int[] o = Furnish.step(facing);
        String back = Furnish.opposite(facing);
        put(s, x, y, z, Kit.stairs(material.stairs(), facing));
        put(s, x + o[0], y, z + o[1], Kit.upside(material.stairs(), back));
        put(s, x + o[0], y + 1, z + o[1], material.block());
        put(s, x + o[0], y + 2, z + o[1], Kit.stairs(material.stairs(), back));
        put(s, x + 2 * o[0], y + 1, z + 2 * o[1], Kit.upside(material.stairs(), back));
        put(s, x + 2 * o[0], y, z + 2 * o[1], TONGUE);
        put(s, x + 3 * o[0], y, z + 3 * o[1], TONGUE);
    }

    // ---------------------------------------------------------------- walls

    /**
     * A footing of rough stone on layer {@code y} under walls on the lines {@code x1..x2, z1..z2}, and a floor of boards
     * inside them on the same layer.
     */
    static void footing(Structure s, int x1, int z1, int x2, int z2, int y) {
        s.fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, FLOOR);
        s.walls(x1, y, z1, x2, y, z2, FOOTING);
    }

    /**
     * Stave walls on the lines {@code x1..x2, z1..z2} from layer {@code y1} to {@code y2}: a sill of lying logs at the
     * bottom, the staves (upright planks) between, a wall plate of lying logs on top, posts at the corners and every four
     * blocks along walls longer than six. Openings are cut afterwards.
     */
    static void staveWalls(Structure s, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int y = y1; y <= y2; y++) {
            boolean beam = y == y1 || y == y2;
            for (int x = x1; x <= x2; x++) {
                String state = beam ? Kit.log(LOG, "x") : post(x - x1, x2 - x1) ? Kit.log(LOG, "y") : Kit.log(STAVE, "y");
                put(s, x, y, z1, state);
                put(s, x, y, z2, state);
            }
            for (int z = z1 + 1; z < z2; z++) {
                String state = beam ? Kit.log(LOG, "z") : post(z - z1, z2 - z1) ? Kit.log(LOG, "y") : Kit.log(STAVE, "y");
                put(s, x1, y, z, state);
                put(s, x2, y, z, state);
            }
        }
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            for (int y = y1; y <= y2; y++) {
                put(s, c[0], y, c[1], Kit.log(LOG, "y"));
            }
        }
    }

    /** Whether a stave wall has a post {@code u} blocks along a wall {@code length} long: at the ends and every four. */
    private static boolean post(int u, int length) {
        return u == 0 || u == length || length > 6 && u % 4 == 0 && length - u >= 3;
    }

    /**
     * Walls of painted boards on the lines {@code x1..x2, z1..z2} from layer {@code y1} to {@code y2}: the boards of the
     * plaster (red, ochre or white in a look), white corner boards, a sill of lying logs at the bottom.
     */
    static void paintedWalls(Structure s, int x1, int y1, int z1, int x2, int y2, int z2) {
        s.walls(x1, y1 + 1, z1, x2, y2, z2, PAINT);
        for (int x = x1; x <= x2; x++) {
            put(s, x, y1, z1, Kit.log(LOG, "x"));
            put(s, x, y1, z2, Kit.log(LOG, "x"));
        }
        for (int z = z1 + 1; z < z2; z++) {
            put(s, x1, y1, z, Kit.log(LOG, "z"));
            put(s, x2, y1, z, Kit.log(LOG, "z"));
        }
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            for (int y = y1 + 1; y <= y2; y++) {
                put(s, c[0], y, c[1], WHITE_POST);
            }
        }
    }

    // ---------------------------------------------------------------- longhouse

    /**
     * The half-width of a longhouse at a row along its length: {@code end} at the gables, swelling to {@code mid} in the
     * middle like the hull of a ship.
     */
    static int hullWidth(int a, int a1, int a2, int end, int mid) {
        double half = (a2 - a1) / 2.0;
        double t = (a - (a1 + a2) / 2.0) / half;
        return end + (int) Math.round((mid - end) * (1 - t * t));
    }

    /**
     * A longhouse (langhus): a hall whose long walls bow out like the hull of a ship, from the gable wall on the line
     * {@code a1} to that on {@code a2} (along x when {@code alongX}, else along z), its middle line across at {@code c}. Its
     * half-width (from the middle line to the outer face of a long wall) is {@code end} at the gables and swells to
     * {@code mid} halfway. On layer {@code y} a footing of rough stone under the walls and a floor of boards; stave walls
     * (posts where the wall steps out) up to the plate at {@code y + wall}; over them a roof of {@code roof} that overhangs
     * by one block, its rows rising as {@code rises} says, so that its ridge, highest where the hull is widest, sags towards
     * the gables; gables of staves, crossed boards and dragon heads of the spires at both ends.
     *
     * @return the layer of the ridge in the middle
     */
    static int longhouse(Structure s, int a1, int a2, int c, int y, int end, int mid, int wall, boolean alongX, int[] rises, Kit.Roof roof) {
        Area plan = (x, z) -> {
            int a = alongX ? x : z;
            int cc = alongX ? z : x;
            return a >= a1 && a <= a2 && Math.abs(cc - c) <= hullWidth(a, a1, a2, end, mid);
        };
        int top = y + wall;
        for (int a = a1; a <= a2; a++) {
            int w = hullWidth(a, a1, a2, end, mid);
            for (int cc = c - w; cc <= c + w; cc++) {
                int x = alongX ? a : cc;
                int z = alongX ? cc : a;
                boolean rim = !plan.has(x + 1, z) || !plan.has(x - 1, z) || !plan.has(x, z + 1) || !plan.has(x, z - 1);
                if (!rim) {
                    put(s, x, y, z, FLOOR);
                    continue;
                }
                put(s, x, y, z, FOOTING);
                boolean gableEnd = a == a1 || a == a2;
                boolean step = !gableEnd && (Math.abs(cc - c) < w || hullWidth(a - 1, a1, a2, end, mid) != w
                        || hullWidth(a + 1, a1, a2, end, mid) != w);
                boolean corner = gableEnd && Math.abs(cc - c) == w;
                for (int yy = y + 1; yy <= top; yy++) {
                    String state;
                    if (corner || step && yy < top) {
                        state = Kit.log(LOG, "y");
                    } else if (yy == top || yy == y + 1) {
                        state = Kit.log(LOG, gableEnd ? (alongX ? "z" : "x") : (alongX ? "x" : "z"));
                    } else {
                        state = Kit.log(STAVE, "y");
                    }
                    put(s, x, yy, z, state);
                }
            }
        }
        Field f = (x, z) -> {
            int a = alongX ? x : z;
            int cc = alongX ? z : x;
            if (a < a1 - 1 || a > a2 + 1) {
                return NONE;
            }
            int w = hullWidth(Math.max(a1, Math.min(a2, a)), a1, a2, end, mid) + 1;
            int d = Math.abs(cc - c);
            return d > w ? NONE : top + rise(rises, w - d);
        };
        int span = mid + 2;
        roof(s, f, EVERYWHERE, alongX ? a1 - 1 : c - span, alongX ? c - span : a1 - 1, alongX ? a2 + 1 : c + span,
                alongX ? c + span : a2 + 1, roof);
        int we = hullWidth(a1, a1, a2, end, mid);
        gableWall(s, f, alongX, a1, c - we, c + we, top + 1, Kit.log(STAVE, "y"));
        gableWall(s, f, alongX, a2, c - we, c + we, top + 1, Kit.log(STAVE, "y"));
        String low = alongX ? "west" : "north";
        String high = alongX ? "east" : "south";
        crossedBoards(s, alongX, a1 - 1, c - we - 1, c + we + 1, roof, BOARD, true);
        crossedBoards(s, alongX, a2 + 1, c - we - 1, c + we + 1, roof, BOARD, true);
        int endRidge = alongX ? f.at(a1, c) : f.at(c, a1);
        dragonHead(s, alongX ? a1 - 1 : c, endRidge + 1, alongX ? c : a1 - 1, low, SPIRE);
        dragonHead(s, alongX ? a2 + 1 : c, endRidge + 1, alongX ? c : a2 + 1, high, SPIRE);
        int am = (a1 + a2) / 2;
        return alongX ? f.at(am, c) : f.at(c, am);
    }

    /**
     * A smoke louvre (ljore) on a ridge at layer {@code y} over (x, z), the ridge running along x or z: for three blocks
     * along it the ridge is open slats (posts), and over them a small gable cap of shingles one layer higher, with a dark
     * slot under its eaves where the smoke of the hearth goes out.
     */
    static void louvre(Structure s, int x, int y, int z, boolean alongX, Kit.Roof roof) {
        int a0 = alongX ? x : z;
        int c = alongX ? z : x;
        String toMinus = alongX ? "north" : "west";
        String toPlus = alongX ? "south" : "east";
        for (int i = -1; i <= 1; i++) {
            at(s, alongX, a0 + i, y, c, FENCE);
            at(s, alongX, a0 + i, y + 1, c - 1, Kit.stairs(roof.stairs(), toPlus));
            at(s, alongX, a0 + i, y + 1, c, roof.block());
            at(s, alongX, a0 + i, y + 1, c + 1, Kit.stairs(roof.stairs(), toMinus));
            at(s, alongX, a0 + i, y + 2, c, Kit.slab(roof.slab()));
        }
    }

    // ---------------------------------------------------------------- turf

    /**
     * A turf roof over walls on the lines {@code x1..x2, z1..z2} whose plate lies at layer {@code y}: a gentle gable (one
     * layer for every block in) overhanging the walls by one block, its eaves a lying log (the turf log that holds the sods),
     * every other row turf on a deck of boards, moss along the ridge and tufts of grass here and there; gables of
     * {@code gable} (none when null).
     *
     * @return the layer of the ridge
     */
    static int turfRoof(Structure s, int x1, int z1, int x2, int z2, int y, boolean alongX, String gable) {
        Field f = gableField(x1, z1, x2, z2, y, alongX, PITCH);
        int ridge = Integer.MIN_VALUE;
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                int h = f.at(x, z);
                if (h == NONE) {
                    continue;
                }
                ridge = Math.max(ridge, h);
                if (h == y) {
                    put(s, x, h, z, Kit.log(LOG, alongX ? "x" : "z"));
                    continue;
                }
                put(s, x, h - 1, z, PLANKS);
                put(s, x, h, z, TURF);
            }
        }
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                int h = f.at(x, z);
                if (h == NONE || h == y) {
                    continue;
                }
                if (h == ridge) {
                    put(s, x, h, z, MOSS);
                }
                int hash = Math.floorMod(x * 7 + z * 13 + h * 5, 9);
                if (hash == 0) {
                    put(s, x, h + 1, z, "minecraft:grass");
                } else if (hash == 4 && h != ridge) {
                    put(s, x, h + 1, z, "minecraft:dandelion");
                }
            }
        }
        if (gable != null) {
            int a1 = alongX ? x1 : z1;
            int a2 = alongX ? x2 : z2;
            int c1 = alongX ? z1 : x1;
            int c2 = alongX ? z2 : x2;
            gableWall(s, f, alongX, a1, c1, c2, y + 1, gable);
            gableWall(s, f, alongX, a2, c1, c2, y + 1, gable);
        }
        return ridge;
    }

    // ---------------------------------------------------------------- galleries and tiers

    /**
     * A covered gallery (svalgang) around walls on the lines {@code x1..x2, z1..z2}: a walk of boards on layer {@code y}
     * reaching {@code width - 1} blocks out from the walls, its outer edge on the ring {@code width} blocks out (a footing
     * of rough stone); along that edge a closed parapet of staves one layer high, slender colonnettes of timber every two
     * blocks with openings between them under a lintel, posts at the corners and a beam on top at layer {@code y + height};
     * over it all a pent roof of {@code roof} leaning on the walls, its eaves one block beyond the beam at {@code y + height}
     * and its rows rising as {@code rises} says; lanterns under it. The parapet opens in the middle of the sides named in
     * {@code openings} ("n", "s", "w", "e").
     *
     * @param height at least 4: the parapet, openings of at least one layer, the lintel and the beam
     * @return the layer of the top row of the roof, against the walls (the walls must reach it)
     */
    static int gallery(Structure s, int x1, int z1, int x2, int z2, int y, int width, int height, int[] rises, Kit.Roof roof, String openings) {
        int ox1 = x1 - width;
        int ox2 = x2 + width;
        int oz1 = z1 - width;
        int oz2 = z2 + width;
        int mx = (x1 + x2) / 2;
        int mz = (z1 + z2) / 2;
        for (int z = oz1; z <= oz2; z++) {
            for (int x = ox1; x <= ox2; x++) {
                int d = Math.max(Math.max(x1 - x, x - x2), Math.max(z1 - z, z - z2));
                if (d < 1 || d > width) {
                    continue;
                }
                put(s, x, y, z, d == width ? FOOTING : FLOOR);
                if (d != width) {
                    continue;
                }
                boolean corner = (x == ox1 || x == ox2) && (z == oz1 || z == oz2);
                boolean northSouth = z == oz1 || z == oz2;
                int u = northSouth ? x - mx : z - mz;
                char side = z == oz1 ? 'n' : z == oz2 ? 's' : x == ox1 ? 'w' : 'e';
                boolean door = !corner && openings.indexOf(side) >= 0 && Math.abs(u) <= 1;
                boolean colonnette = !corner && Math.floorMod(u, 2) == 0;
                for (int yy = y + 1; yy <= y + height; yy++) {
                    String state;
                    if (yy == y + height) {
                        state = Kit.log(LOG, northSouth ? "x" : "z");
                    } else if (corner) {
                        state = Kit.log(LOG, "y");
                    } else if (yy == y + height - 1) {
                        state = colonnette ? Kit.log(LOG, "y") : Kit.topSlab(SLAB);
                    } else if (door) {
                        state = Structure.AIR;
                    } else if (yy == y + 1) {
                        state = Kit.log(STAVE, "y");
                    } else {
                        state = colonnette ? FENCE : Structure.AIR;
                    }
                    put(s, x, yy, z, state);
                }
            }
        }
        int top = pentRoof(s, x1, z1, x2, z2, y + height, width + 1, rises, roof);
        // Lanterns under the roof along the walls.
        int under = y + height + rise(rises, width - 1);
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                int d = Math.max(Math.max(x1 - x, x - x2), Math.max(z1 - z, z - z2));
                boolean corner = (x == x1 - 1 || x == x2 + 1) && (z == z1 - 1 || z == z2 + 1);
                int u = z == z1 - 1 || z == z2 + 1 ? x - mx : z - mz;
                if (d == 1 && (corner || Math.floorMod(u, 4) == 2 && !corner)) {
                    put(s, x, under, z, Kit.hangingLantern());
                }
            }
        }
        return top;
    }

    /**
     * The layers of the tiers of a stave church drawn by {@link #staveTiers}.
     *
     * @param navePlate the plate of the raised walls of the nave
     * @param ridge     the ridge of the nave
     * @param top       the layer above the vane of its turret
     */
    record Tiers(int navePlate, int ridge, int top) {
    }

    /**
     * The upper tiers of a stave church over a hall whose aisle walls stand on the lines {@code x1..x2, z1..z2} with their
     * plate at layer {@code plate} (the first tier is the roof of the gallery around them, {@link #gallery}): a steep pent
     * roof of {@code roof} over the aisles (the second tier) leaning on the raised walls of the nave, which stand
     * {@code inset} blocks inside the aisle walls and rise {@code clerestory} layers of staves with small windows above
     * the top of the pent roof; over the nave a steep gable roof of {@code roof} with crossed boards of the timber and
     * dragon heads of {@code spire} (the third tier), its ridge along x or z; on the middle of the ridge a turret of
     * {@code spire} (see {@link #turret}). Under the raised walls the nave stands open to the aisles between masts at its
     * corners, which reach down to the floor ({@code floor}). Shingles of the roof for the tiers and the spires for the
     * turret and the dragons keep the tiers apart in a look whose spires are darker than its roofs.
     */
    static Tiers staveTiers(Structure s, int x1, int z1, int x2, int z2, int floor, int plate, int inset, int clerestory, boolean alongX,
                            Kit.Roof roof, Kit.Roof spire) {
        int nx1 = x1 + inset;
        int nx2 = x2 - inset;
        int nz1 = z1 + inset;
        int nz2 = z2 - inset;
        int aisleTop = pentRoof(s, nx1, nz1, nx2, nz2, plate, inset + 1, STEEP, roof);
        int navePlate = aisleTop + clerestory + 1;
        staveWalls(s, nx1, aisleTop - 1, nz1, nx2, navePlate, nz2);
        for (int[] c : new int[][]{{nx1, nz1}, {nx2, nz1}, {nx1, nz2}, {nx2, nz2}}) {
            for (int y = floor + 1; y < aisleTop; y++) {
                put(s, c[0], y, c[1], Kit.log(LOG, "y"));
            }
        }
        // Small windows high in the clerestory.
        int wy = aisleTop + clerestory / 2 + 1;
        if (alongX) {
            for (int x = nx1 + 2; x <= nx2 - 2; x += 3) {
                put(s, x, wy, nz1, Kit.GLASS);
                put(s, x, wy, nz2, Kit.GLASS);
            }
        } else {
            for (int z = nz1 + 2; z <= nz2 - 2; z += 3) {
                put(s, nx1, wy, z, Kit.GLASS);
                put(s, nx2, wy, z, Kit.GLASS);
            }
        }
        int ridge = gableRoof(s, nx1, nz1, nx2, nz2, navePlate, alongX, STEEP, roof, Kit.log(STAVE, "y"), BOARD, spire);
        int cx = (nx1 + nx2) / 2;
        int cz = (nz1 + nz2) / 2;
        int top = turret(s, cx, ridge - 3, cz, ridge + 2, spire);
        return new Tiers(navePlate, ridge, top);
    }

    /**
     * A ridge turret (takryttar) of a stave church around (cx, cz): a body of staves three wide from layer {@code y} (inside
     * the roof it stands on) up to {@code top}, a skirt of {@code spire} jutting out two blocks around its top, a belfry
     * three wide with open louvres on its four sides and a lantern in it, a second skirt and a needle with a vane.
     *
     * @return the layer above the vane
     */
    static int turret(Structure s, int cx, int y, int cz, int top, Kit.Roof spire) {
        int[] skirt = {2};
        int belfry = top + rise(skirt, 1);
        staveWalls(s, cx - 1, y, cz - 1, cx + 1, belfry - 1, cz + 1);
        pentRoof(s, cx - 1, cz - 1, cx + 1, cz + 1, top, 2, skirt, spire);
        staveWalls(s, cx - 1, belfry, cz - 1, cx + 1, belfry + 3, cz + 1);
        for (int[] d : STEPS) {
            put(s, cx + d[0], belfry + 1, cz + d[1], FENCE);
            put(s, cx + d[0], belfry + 2, cz + d[1], FENCE);
        }
        put(s, cx, belfry, cz, PLANKS);
        put(s, cx, belfry + 1, cz, Kit.lantern());
        pentRoof(s, cx - 1, cz - 1, cx + 1, cz + 1, belfry + 3, 1, skirt, spire);
        return needle(s, cx, belfry + 4, cz, 5, new int[]{1, 4}, spire);
    }

    /**
     * A square stave tower around (cx, cz) standing on layer {@code y}, built in storeys like the tower of a stave church
     * (for a hall of mages, a bell tower, the tower of a guild): storey {@code i} has stave walls (see {@link #staveWalls})
     * {@code width - 2 * step * i} wide and {@code storeys[i]} layers high from its sill to its plate, a floor of boards
     * inside on the layer of its sill and a lantern hanging under the floor above; around the top of every storey but the
     * last a skirt of {@code spire} that covers the step to the next storey and juts out {@code skirt} blocks beyond it,
     * rising two layers for every block in; over the last storey a needle of {@code spire} one block wider than its walls
     * on every side, rising as {@link #NEEDLE} says, with a vane. With {@code step} 0 a ladder runs up along the inside of
     * the back wall through every floor. Windows and doors are cut afterwards.
     *
     * @param width odd, the outer width of the first storey
     * @return the layers of the floors of the storeys from the bottom up and, last, the layer above the vane
     */
    static int[] staveTower(Structure s, int cx, int y, int cz, int width, int[] storeys, int step, int skirt, Kit.Roof spire) {
        int[] layers = new int[storeys.length + 1];
        int base = y;
        int[] rises = {2};
        for (int i = 0; i < storeys.length; i++) {
            int r = (width - 1) / 2 - step * i;
            int top = base + storeys[i] - 1;
            layers[i] = base;
            s.fill(cx - r + 1, base, cz - r + 1, cx + r - 1, base, cz + r - 1, FLOOR);
            staveWalls(s, cx - r, base, cz - r, cx + r, top, cz + r);
            if (i > 0) {
                put(s, cx + 1, base - 1, cz, Kit.hangingLantern());
            }
            if (i == storeys.length - 1) {
                put(s, cx + 1, top - 1, cz, Kit.lantern());
                layers[i + 1] = needle(s, cx, top, cz, 2 * r + 3, NEEDLE, spire);
                break;
            }
            int next = r - step;
            pentRoof(s, cx - next, cz - next, cx + next, cz + next, top, step + skirt, rises, spire);
            base = top + 1;
        }
        if (step == 0) {
            int r = (width - 1) / 2;
            for (int yy = y + 1; yy < layers[storeys.length - 1] + 1; yy++) {
                put(s, cx, yy, cz - r + 1, Kit.ladder("south"));
            }
        }
        return layers;
    }

    /**
     * A covered wall walk on a curtain wall of stone along x or z, from (x1, z1) to (x2, z2) on one line, standing on layer
     * {@code y}: the wall three blocks thick (its faces of the wall stone, its core rough stone, a footing of rough stone)
     * up to layer {@code y + height - 1}, whose top is the walk; before its outer face a hoarding of staves jutting out one
     * block over corbels of stone, two layers high with loopholes every three blocks, on its inner face posts every three
     * blocks, wall plates on both and over the walk a gable roof of {@code roof} along the wall overhanging both, with
     * lanterns under it. A ladder leads up the inner face at the first end. The court lies on the side {@code inward}; the
     * wall takes the line {@code c} (its outer face) and the three lines beyond it inwards, the hoarding and the eaves two
     * lines outwards. Its ends are open, for towers to close them.
     */
    static void curtain(Structure s, int x1, int z1, int x2, int z2, int y, int height, String inward, Kit.Roof roof) {
        boolean alongX = z1 == z2;
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int c = alongX ? z1 : x1;
        int[] in = Furnish.step(inward);
        int dc = alongX ? in[1] : in[0];
        int walk = y + height;
        String lying = alongX ? "x" : "z";
        for (int a = a1; a <= a2; a++) {
            for (int k = 0; k <= 2; k++) {
                at(s, alongX, a, y, c + k * dc, FOOTING);
                for (int yy = y + 1; yy < walk; yy++) {
                    at(s, alongX, a, yy, c + k * dc, k == 1 ? FOOTING : STONE);
                }
            }
            boolean bay = Math.floorMod(a - a1, 3) == 1;
            at(s, alongX, a, walk - 1, c - dc, Kit.upside(STONE_STAIRS, inward));
            at(s, alongX, a, walk, c - dc, Kit.log(STAVE, "y"));
            at(s, alongX, a, walk + 1, c - dc, bay ? FENCE : Kit.log(STAVE, "y"));
            at(s, alongX, a, walk + 2, c - dc, Kit.log(LOG, lying));
            if (bay && a != a1 + 1) {
                at(s, alongX, a, walk, c + 2 * dc, Kit.log(LOG, "y"));
                at(s, alongX, a, walk + 1, c + 2 * dc, Kit.log(LOG, "y"));
            }
            at(s, alongX, a, walk + 2, c + 2 * dc, Kit.log(LOG, lying));
            if (Math.floorMod(a - a1, 4) == 2) {
                at(s, alongX, a, walk + 3, c + dc, Kit.hangingLantern());
            }
        }
        int lo = Math.min(c - dc, c + 2 * dc);
        int hi = Math.max(c - dc, c + 2 * dc);
        Field f = gableField(alongX ? a1 : lo, alongX ? lo : a1, alongX ? a2 : hi, alongX ? hi : a2, walk + 2, alongX, PITCH);
        Area within = (px, pz) -> {
            int a = alongX ? px : pz;
            return a >= a1 && a <= a2;
        };
        roof(s, f, within, alongX ? a1 : lo - 1, alongX ? lo - 1 : a1, alongX ? a2 : hi + 1, alongX ? hi + 1 : a2, roof);
        for (int yy = y + 1; yy < walk; yy++) {
            at(s, alongX, a1 + 1, yy, c + 3 * dc, Kit.ladder(inward));
        }
    }

    /**
     * A chimney stack of the chimney stone at (x, z) from layer {@code y1} to {@code y2} with a pot on its top. A look may
     * leave chimneys out, so a stack must stand free: on a ridge or against a wall, never as a part of either.
     */
    static void chimney(Structure s, int x, int z, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            put(s, x, y, z, Kit.CHIMNEY);
        }
        put(s, x, y2 + 1, z, Kit.CHIMNEY_POT);
    }

    // ---------------------------------------------------------------- towers

    /**
     * A round tower of stone (for castles only) around (cx, cz) standing on layer {@code y}: a plinth of rough stone two
     * blocks wider with a sloping skirt, a round wall of the wall stone {@code diameter} wide up to layer
     * {@code y + height - 1} with floors of boards every five layers, a ladder along its back wall, slits of glass and
     * lanterns on every floor; on top a timber storey jutting out one block on every side over stone brackets, its walls of
     * staves three layers high with lookouts to the four sides; over it a flared cone of {@code roof} with a vane. A door at
     * the foot looking {@code door}, unless null.
     *
     * @param diameter odd, 7 or more
     * @return the layer of the floor of the timber storey (one stands on the layer above it)
     */
    static int roundTower(Structure s, int cx, int y, int cz, int diameter, int height, String door, Kit.Roof roof) {
        int r = (diameter - 1) / 2;
        int top = y + height;
        Kit.disc(s, cx, y, cz, diameter + 2, FOOTING);
        int rr = r + 1;
        for (int dz = -rr; dz <= rr; dz++) {
            for (int dx = -rr; dx <= rr; dx++) {
                if (Kit.onRim(dx, dz, diameter + 2)) {
                    put(s, cx + dx, y + 1, cz + dz, Kit.stairs(FOOTING_STAIRS, inward(dx, dz)));
                    put(s, cx + dx, top - 1, cz + dz, Kit.upside(STONE_STAIRS, inward(dx, dz)));
                }
            }
        }
        Kit.tube(s, cx, y + 1, top - 1, cz, diameter, STONE);
        for (int f = y; f < top; f += 5) {
            Kit.disc(s, cx, f, cz, diameter - 2, FLOOR);
            if (f > y) {
                put(s, cx + 1, f - 1, cz, Kit.hangingLantern());
            }
            put(s, cx, f + 2, cz + r, Kit.GLASS);
            put(s, cx, f + 2, cz - r, Kit.GLASS);
            put(s, cx + r, f + 2, cz, Kit.GLASS);
            put(s, cx - r, f + 2, cz, Kit.GLASS);
        }
        put(s, cx + 1, top - 1, cz, Kit.hangingLantern());
        Kit.disc(s, cx, top, cz, diameter + 2, FLOOR);
        for (int dz = -rr; dz <= rr; dz++) {
            for (int dx = -rr; dx <= rr; dx++) {
                if (!Kit.onRim(dx, dz, diameter + 2)) {
                    continue;
                }
                boolean axis = dx == 0 || dz == 0;
                for (int yy = top + 1; yy <= top + 3; yy++) {
                    String state = yy == top + 3 ? PLANKS : axis && yy == top + 2 ? FENCE : axis ? Kit.log(LOG, "y") : Kit.log(STAVE, "y");
                    put(s, cx + dx, yy, cz + dz, state);
                }
            }
        }
        put(s, cx, top + 1, cz + 1, Kit.lantern());
        for (int yy = y + 1; yy <= top; yy++) {
            put(s, cx, yy, cz - r + 1, Kit.ladder("south"));
        }
        cone(s, cx, top + 4, cz, diameter + 4, FLARE, roof);
        if (door != null) {
            int[] d = Furnish.step(door);
            s.door(cx + d[0] * r, y + 1, cz + d[1] * r, DOOR, Furnish.opposite(door), "left");
            put(s, cx + d[0] * (r + 1), y + 1, cz + d[1] * (r + 1), Structure.AIR);
            put(s, cx + d[0] * (r + 1), y, cz + d[1] * (r + 1), FOOTING);
        }
        return top;
    }

    // ---------------------------------------------------------------- windows and doors

    /**
     * A small window in a wall looking the way of {@code facing}: panes of glass at (x, y, z) and {@code height - 1} layers
     * above. With {@code white} a frame of white paint in the wall around it (posts of quartz at its sides, a white lintel)
     * and a white sill jutting out; else a carved frame of the timber one block out before the wall: boards standing against
     * the wall at its sides, a thin hood board over it and a thin sill board under it, both three blocks wide.
     */
    static void window(Structure s, int x, int y, int z, String facing, int height, boolean white) {
        int[] out = Furnish.step(facing);
        int[] al = Furnish.step(Furnish.left(facing));
        for (int v = 0; v < height; v++) {
            put(s, x, y + v, z, Kit.GLASS);
        }
        int fx = x + out[0];
        int fz = z + out[1];
        String in = Furnish.opposite(facing);
        if (white) {
            for (int side = -1; side <= 1; side += 2) {
                for (int v = -1; v <= height; v++) {
                    put(s, x + al[0] * side, y + v, z + al[1] * side, WHITE_POST);
                }
            }
            put(s, x, y + height, z, WHITE.block());
            put(s, x, y - 1, z, WHITE.block());
            put(s, fx, y - 1, fz, Kit.upside(WHITE.stairs(), in));
            return;
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int v = 0; v < height; v++) {
                put(s, fx + al[0] * side, y + v, fz + al[1] * side, Kit.shutter(TRAPDOOR, facing));
            }
        }
        for (int u = -1; u <= 1; u++) {
            put(s, fx + al[0] * u, y + height, fz + al[1] * u, Kit.slab(SLAB));
            put(s, fx + al[0] * u, y - 1, fz + al[1] * u, Kit.topSlab(SLAB));
        }
    }

    /**
     * A carved portal in a wall looking the way of {@code facing}: at (x, y, z) a door (or with {@code door} false an open
     * passage), carved posts beside it in the wall and a lintel log over it; before it a porch two blocks deep on two posts
     * with a floor of boards on layer {@code y - 1}, low rails at its sides, a lantern under its own steep little gable roof
     * of {@code roof}, which has crossed boards of {@code boards} and a dragon head of {@code dragons} looking out (none
     * when null). It takes one block to each side of the door, two more for the eaves, and four out of the wall, six with
     * the dragon.
     */
    static void portal(Structure s, int x, int y, int z, String facing, boolean door, Kit.Roof roof, Kit.Roof boards, Kit.Roof dragons) {
        int[] out = Furnish.step(facing);
        int[] al = Furnish.step(Furnish.left(facing));
        String in = Furnish.opposite(facing);
        if (door) {
            s.door(x, y, z, DOOR, in, "left");
        } else {
            put(s, x, y, z, Structure.AIR);
            put(s, x, y + 1, z, Structure.AIR);
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int v = 0; v <= 2; v++) {
                put(s, x + al[0] * side, y + v, z + al[1] * side, Kit.log(LOG, "y"));
            }
        }
        put(s, x, y + 2, z, Kit.log(LOG, out[0] != 0 ? "z" : "x"));
        for (int v = 1; v <= 2; v++) {
            int px = x + out[0] * v;
            int pz = z + out[1] * v;
            for (int u = -1; u <= 1; u++) {
                put(s, px + al[0] * u, y - 1, pz + al[1] * u, FLOOR);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            int px = x + out[0] * 2 + al[0] * side;
            int pz = z + out[1] * 2 + al[1] * side;
            for (int v = 0; v <= 2; v++) {
                put(s, px, y + v, pz, Kit.log(LOG, "y"));
            }
            put(s, px, y + 3, pz, Kit.log(LOG, out[0] != 0 ? "x" : "z"));
            put(s, x + out[0] + al[0] * side, y + 3, z + out[1] + al[1] * side, Kit.log(LOG, out[0] != 0 ? "x" : "z"));
            put(s, x + out[0] + al[0] * side, y, z + out[1] + al[1] * side, FENCE);
        }
        // The roof over the porch, cut off at the wall.
        boolean alongX = out[0] != 0;
        int c = alongX ? z : x;
        int a0 = alongX ? x : z;
        int aFar = a0 + (alongX ? out[0] : out[1]) * 2;
        int lo = Math.min(a0 + (alongX ? out[0] : out[1]), aFar);
        int hi = Math.max(a0 + (alongX ? out[0] : out[1]), aFar);
        Field f = gableField(alongX ? lo : c - 1, alongX ? c - 1 : lo, alongX ? hi : c + 1, alongX ? c + 1 : hi, y + 3, alongX, STEEP);
        int dir = alongX ? out[0] : out[1];
        Area before = (px, pz) -> ((alongX ? px : pz) - a0) * dir >= 1;
        int bx1 = alongX ? Math.min(lo, hi) - 1 : c - 2;
        int bx2 = alongX ? Math.max(lo, hi) + 1 : c + 2;
        int bz1 = alongX ? c - 2 : Math.min(lo, hi) - 1;
        int bz2 = alongX ? c + 2 : Math.max(lo, hi) + 1;
        roof(s, f, before, bx1, bz1, bx2, bz2, roof);
        int aEnd = dir > 0 ? hi + 1 : lo - 1;
        crossedBoards(s, alongX, aEnd, c - 2, c + 2, roof, boards, true);
        int ridge = alongX ? f.at(aEnd, c) : f.at(c, aEnd);
        if (dragons != null) {
            dragonHead(s, alongX ? aEnd : x, ridge + 1, alongX ? z : aEnd, facing, dragons);
        }
        for (int yy = y + 3; yy < ridge; yy++) {
            put(s, x + out[0], yy, z + out[1], Kit.chain());
        }
        put(s, x + out[0], y + 2, z + out[1], Kit.hangingLantern());
    }

    // ---------------------------------------------------------------- the hearth and the furniture

    /**
     * A long hearth (langeld) in the middle of a hall over {@code x1..x2, z1..z2} (three or more wide across, long along
     * the other way), the layer one stands on being {@code y}: a bed of embers sunk into the floor, a curb of rough stone
     * slabs around it, fires along its middle line every other block and a cauldron hanging over the fire nearest the middle
     * on a chain from the layer {@code ceiling} (the lowest block of the roof over it; none when it is lower than
     * {@code y + 4}).
     */
    static void longHearth(Structure s, int x1, int z1, int x2, int z2, int y, int ceiling) {
        boolean alongX = x2 - x1 >= z2 - z1;
        int mx = (x1 + x2) / 2;
        int mz = (z1 + z2) / 2;
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                boolean rim = x == x1 || x == x2 || z == z1 || z == z2;
                put(s, x, y - 1, z, rim ? FOOTING : EMBERS);
                if (rim) {
                    put(s, x, y, z, Kit.slab(FOOTING_SLAB));
                    continue;
                }
                boolean middle = alongX ? z == mz : x == mx;
                int a = alongX ? x - x1 : z - z1;
                put(s, x, y, z, middle && a % 2 == 1 ? "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]"
                        : Structure.AIR);
            }
        }
        if (ceiling >= y + 4) {
            // over the fire nearest the middle
            int hx = alongX ? mx + ((mx - x1) % 2 == 1 ? 0 : 1) : mx;
            int hz = alongX ? mz : mz + ((mz - z1) % 2 == 1 ? 0 : 1);
            for (int yy = y + 3; yy < ceiling; yy++) {
                put(s, hx, yy, hz, Kit.chain());
            }
            put(s, hx, y + 2, hz, "minecraft:water_cauldron[level=3]");
        }
    }

    /**
     * A raised sleeping bench (set) along a wall from (x1, z1) to (x2, z2), the cells against the wall: upper slabs of the
     * timber with furs of white, grey and brown on them, and a step of lower slabs before it on the side {@code toRoom}.
     */
    static void sleepingBench(Structure s, int x1, int z1, int x2, int z2, int y, String toRoom) {
        int[] r = Furnish.step(toRoom);
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                put(s, x, y, z, Kit.topSlab(FLOOR_SLAB));
                int hash = Math.floorMod(x * 3 + z * 5, 7);
                if (hash < 5) {
                    put(s, x, y + 1, z, FURS[hash % 3]);
                }
                if (!solid(s, x + r[0], y, z + r[1])) {
                    put(s, x + r[0], y, z + r[1], Kit.slab(FLOOR_SLAB));
                }
            }
        }
    }

    /**
     * The high seat (hásæti) of the master of a hall at (x, y, z) looking {@code facing}, its back to a wall: a dais of
     * boards three wide with a step before it, a chair on it between two carved pillars of logs with lanterns on their
     * tops, a hanging of cloth on the wall behind and a fur before it.
     */
    static void highSeat(Structure s, int x, int y, int z, String facing) {
        int[] f = Furnish.step(facing);
        int[] al = Furnish.step(Furnish.left(facing));
        String back = Furnish.opposite(facing);
        for (int u = -1; u <= 1; u++) {
            put(s, x + al[0] * u, y, z + al[1] * u, PLANKS);
            put(s, x + al[0] * u + f[0], y, z + al[1] * u + f[1], Kit.stairs(FLOOR_STAIRS, back));
        }
        put(s, x, y + 1, z, Kit.stairs(STAIRS, back));
        for (int side = -1; side <= 1; side += 2) {
            int px = x + al[0] * side;
            int pz = z + al[1] * side;
            put(s, px, y + 1, pz, Kit.log(LOG, "y"));
            put(s, px, y + 2, pz, Kit.log(LOG, "y"));
            put(s, px, y + 3, pz, Kit.lantern());
        }
        put(s, x, y + 2, z, Kit.wallBanner("red", facing));
        put(s, x + 2 * f[0], y, z + 2 * f[1], FURS[1]);
    }

    /**
     * A closed bed (lokrekkja) whose foot is at (x, y, z) and whose head lies the way of {@code facing}: a board at its
     * head, a roof of slabs over it on posts at its foot, the cloth of its cover of {@code color}. With {@code home} a real
     * bed, a home for one (only in houses); else a mattress of wool under a fur, which is no home.
     */
    static void closedBed(Structure s, int x, int y, int z, String facing, String color, boolean home) {
        int[] f = Furnish.step(facing);
        int[] al = Furnish.step(Furnish.left(facing));
        int hx = x + f[0];
        int hz = z + f[1];
        if (home) {
            Furnish.bed(s, x, y, z, facing, color);
        } else {
            put(s, x, y, z, "minecraft:white_wool");
            put(s, hx, y, hz, "minecraft:white_wool");
            put(s, x, y + 1, z, FURS[2]);
        }
        put(s, hx + f[0], y, hz + f[1], PLANKS);
        put(s, hx + f[0], y + 1, hz + f[1], PLANKS);
        for (int side = -1; side <= 1; side += 2) {
            int px = x + al[0] * side - f[0];
            int pz = z + al[1] * side - f[1];
            if (!solid(s, x + al[0] * side, y, z + al[1] * side)) {
                put(s, px, y, pz, FENCE);
                put(s, px, y + 1, pz, FENCE);
            }
        }
        for (int i = -1; i <= 2; i++) {
            put(s, x + f[0] * i, y + 2, z + f[1] * i, Kit.topSlab(SLAB));
        }
    }

    /**
     * An upright loom against a wall at (x, y, z), the weaver standing on the side {@code facing}: two posts with a beam
     * across them and a woven cloth hanging between.
     */
    static void loom(Structure s, int x, int y, int z, String facing) {
        int[] al = Furnish.step(Furnish.left(facing));
        for (int side = -1; side <= 1; side += 2) {
            put(s, x + al[0] * side, y, z + al[1] * side, FENCE);
            put(s, x + al[0] * side, y + 1, z + al[1] * side, FENCE);
        }
        for (int u = -1; u <= 1; u++) {
            put(s, x + al[0] * u, y + 2, z + al[1] * u, Kit.log(LOG, al[0] != 0 ? "x" : "z"));
        }
        put(s, x, y + 1, z, Kit.wallBanner("white", facing));
    }

    /**
     * Tapestries along a wall from (x1, z1) to (x2, z2) at layer {@code y}, hanging on the wall behind them and looking
     * {@code facing}: banners of the cloth and of white in turn.
     */
    static void tapestry(Structure s, int x1, int z1, int x2, int z2, int y, String facing) {
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                put(s, x, y, z, Kit.wallBanner(Math.floorMod(x + z, 2) == 0 ? "red" : "white", facing));
            }
        }
    }

    /**
     * A rug of furs over {@code x1..x2, z1..z2} on layer {@code y}: carpets of white, grey and brown in patches.
     */
    static void furs(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                put(s, x, y, z, FURS[Math.floorMod(x / 2 + z / 2, 3)]);
            }
        }
    }

    /**
     * A board for a feast from (x1, z1) to (x2, z2), along x or z, at layer {@code y}: a table of upper slabs with candles,
     * benches of lower slabs along both sides.
     */
    static void feastBoard(Structure s, int x1, int z1, int x2, int z2, int y) {
        boolean alongX = Math.abs(x2 - x1) >= Math.abs(z2 - z1);
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int c = alongX ? z1 : x1;
        for (int a = a1; a <= a2; a++) {
            at(s, alongX, a, y, c, Kit.topSlab(FLOOR_SLAB));
            if ((a - a1) % 3 == 1) {
                at(s, alongX, a, y + 1, c, Furnish.candles(2));
            }
            at(s, alongX, a, y, c - 1, Kit.slab(FLOOR_SLAB));
            at(s, alongX, a, y, c + 1, Kit.slab(FLOOR_SLAB));
        }
    }
}
