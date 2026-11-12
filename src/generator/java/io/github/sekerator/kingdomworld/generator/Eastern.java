package io.github.sekerator.kingdomworld.generator;

/**
 * The style "eastern": the architecture of the far east in dark timber under great curving roofs. A building of this style
 * stands on a platform of stone reached by flights of steps; its frame of dark posts and beams carries walls of white
 * plaster and sliding screens of paper; a veranda of red-lacquered pillars runs around it, and on the heads of the pillars
 * brackets of stepped wooden arms carry the eaves far out. The roofs are the face of the style: wide and heavy, nearly flat
 * at the eaves and steep at the top, so that their slopes curve; their corners sweep upward, and their ridges end in raised
 * horns. Halls have hip roofs or hip-and-gable roofs (a hip below, a gable with a white face above), often a second skirt
 * roof below the main one; towers are pagodas, storey on storey, each under a roof of its own stepping in, crowned by a
 * spire of rings. Gates carry heavy roofs on red pillars; garden walls of white plaster carry little roofs of tiles; round
 * moon gates and round windows open in them. Gardens hold ponds crossed by arched bridges, stone lanterns, pines pruned into
 * flat pads of needles and blossoming cherries. Inside: floors of woven mats, low tables with cushions around them, an alcove
 * with a hanging scroll, a household shrine, folding screens, paper lamps and lanterns of red cloth.
 *
 * <p>The materials are the parts of a look (see {@link Kit}): posts, beams, floors, doors and furniture are the timber (oak and
 * spruce: dark oak or spruce in a kingdom of this style); platforms, steps, ramparts and the footings of garden walls the
 * wall stone; the foot courses and battered faces the rough stone; the copings of platforms, bases of pillars and frames of
 * moon gates the trim; walls and the faces of gables the plaster (white); roofs of every kind the roof (grey-black tiles,
 * teal, black or green copper); the rings of the finials the spires (gold-like copper); lanterns, cushions and hangings the
 * cloth (red or yellow). Fixed accents that never change: the red lacquer of pillars, bridges, balconies and railings (red
 * concrete and red nether bricks), the paper of screens and windows (white stained glass and white wool), the woven mats
 * (bamboo mosaic), the grey granite of stone lanterns (andesite), the light inside paper lamps (ochre froglight) and the
 * copper rods of the finials.
 *
 * <p>This class holds the data and the shapes of the style; buildings are drawn with them by other classes. Coordinates as
 * everywhere: x from west to east, z from the back (north) to the front (south), y up. A shape draws only inside the box of
 * the structure and leaves out what would fall outside it. Layers named {@code y} of roofs are the layers of their eaves;
 * the layers of things that stand are the layers of their feet (the floor is the layer below).
 */
final class Eastern {
    // ---------------------------------------------------------------- materials

    /** Posts and beams: logs upright or lying (timber, dark oak or spruce in a kingdom of this style). */
    static final String POST = "minecraft:spruce_log";
    /** Boards of walls, kneeboards of screens, gables (timber). */
    static final String PLANKS = "minecraft:spruce_planks";
    static final String STAIRS = "minecraft:spruce_stairs";
    static final String SLAB = "minecraft:spruce_slab";
    static final String FENCE = "minecraft:spruce_fence";
    static final String DOOR = "minecraft:spruce_door";
    /** Trapdoors of the timber for lids, shelves and the fronts of drawers. */
    static final String TRAPDOOR = "minecraft:oak_trapdoor";
    /** The wood of floors and furniture (timber), as Furnish takes it. */
    static final String WOOD = "oak";
    static final String FLOOR = "minecraft:oak_planks";
    static final String FLOOR_SLAB = "minecraft:oak_slab";
    static final String FLOOR_STAIRS = "minecraft:oak_stairs";
    /** Woven mats: bamboo mosaic, a fixed accent, pale whatever the wood of the place. */
    static final String TATAMI = "minecraft:bamboo_mosaic";
    static final String TATAMI_SLAB = "minecraft:bamboo_mosaic_slab";
    /** White walls between the posts (plaster). */
    static final String PLASTER = Kit.PLASTER;
    /** Platforms, steps and garden walls (the wall stone). */
    static final String STONE = Kit.WALL;
    static final String STONE_STAIRS = Kit.WALL_STAIRS;
    static final String STONE_SLAB = Kit.WALL_SLAB;
    static final String STONE_WALL = Kit.WALL_FENCE;
    /** Footings, the battered faces of bases, garden rocks (rough stone). */
    static final String ROUGH = Kit.ROUGH;
    static final String ROUGH_STAIRS = Kit.ROUGH_STAIRS;
    static final String ROUGH_SLAB = Kit.ROUGH_SLAB;
    /** Copings, lips of platforms, stone lanterns, bases of pillars (trim). */
    static final String TRIM = Kit.TRIM;
    static final String TRIM_STAIRS = Kit.TRIM_STAIRS;
    static final String TRIM_SLAB = Kit.TRIM_SLAB;
    static final String CARVED = Kit.CARVED;
    /** Every roof of the style: halls, skirts, pagodas, gates, copings of walls (the roof of a look). */
    static final Kit.Roof ROOF = Kit.SHINGLE;
    /** The finials of pagodas and pavilions (the spires of a look: gold-like copper). */
    static final Kit.Roof FINIAL = Kit.SLATE;
    /** Red lacquer of pillars: a fixed accent. */
    static final String RED = "minecraft:red_concrete";
    /** Red lacquer of slender posts, railings and the sides of bridges: red nether bricks, a fixed accent. */
    static final String RED_POST = "minecraft:red_nether_brick_wall";
    static final String RED_BLOCK = "minecraft:red_nether_bricks";
    static final String RED_STAIRS = "minecraft:red_nether_brick_stairs";
    static final String RED_SLAB = "minecraft:red_nether_brick_slab";
    /** Paper of screens and windows: panes of white glass (lit from behind), or white wool where it must be opaque. */
    static final String PAPER = "minecraft:white_stained_glass_pane";
    static final String PAPER_SOLID = "minecraft:white_wool";
    /** The light inside a paper lamp. */
    static final String GLOW = "minecraft:ochre_froglight[axis=y]";
    /** The stone of lanterns, rocks of gardens and the caps of bridge posts: andesite, a fixed grey like granite. */
    static final String GRANITE = "minecraft:andesite";
    static final String GRANITE_STAIRS = "minecraft:andesite_stairs";
    static final String GRANITE_SLAB = "minecraft:andesite_slab";
    static final String GRANITE_WALL = "minecraft:andesite_wall";
    /** Gilding for halls of state (the dais of a throne, the ornaments of a palace ridge): a fixed accent, sparingly. */
    static final String GOLD = "minecraft:gold_block";
    /** The mast of a finial: copper rods, whose knots read as rings. */
    static final String ROD = "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]";
    /** Water of ponds, only in closed basins (it may stay empty when built). */
    static final String WATER = "minecraft:water[level=0]";
    /** The ground of gardens, raked gravel of paths (it needs a block under it) and moss on rocks. */
    static final String GRASS = "minecraft:grass_block[snowy=false]";
    static final String GRAVEL = "minecraft:gravel";
    static final String MOSS = "minecraft:moss_block";

    /** Kinds of roofs by how many rings of it are hips: all (a hip roof) or none (a gable roof); between, hip-and-gable. */
    static final int HIP = 1000;
    static final int GABLE = 0;

    private Eastern() {
    }

    // ---------------------------------------------------------------- drawing inside the box

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static void clear(Structure s, int x, int y, int z) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, Structure.AIR);
        }
    }

    private static boolean solid(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && s.solid(x, y, z);
    }

    private static void fill(Structure s, int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    put(s, x, y, z, state);
                }
            }
        }
    }

    private static void at(Structure s, boolean alongX, int a, int y, int c, String state) {
        put(s, alongX ? a : c, y, alongX ? c : a, state);
    }

    private static String stairs(String block, String facing, String half, String shape) {
        return block + "[facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=false]";
    }

    /** The corner stair of a ring whose corner lies to the side (sx, sz) of its middle: its high quarter inward. */
    private static String inner(String block, int sx, int sz, String half) {
        return stairs(block, sz < 0 ? "south" : "north", half, (sz < 0) == (sx < 0) ? "outer_left" : "outer_right");
    }

    /** The corner stair of a ring whose corner lies to the side (sx, sz) of its middle: its high quarter outward. */
    private static String outer(String block, int sx, int sz, String half) {
        return stairs(block, sz < 0 ? "north" : "south", half, (sz < 0) == (sx < 0) ? "outer_left" : "outer_right");
    }

    // ---------------------------------------------------------------- curved roofs

    /**
     * The surface heights of the rings of a curved roof from the eaves inward, in half blocks above the layer of the eaves:
     * the first two rings are slabs rising half a block each, so that the eaves lie nearly flat; the next rings are stairs
     * rising a block each; with {@code pitch} 1 the upper half of the rings and with 2 all of them after the eaves rise two
     * blocks each (a full block under a stair), so that the slope curves from flat eaves to a steep top.
     *
     * @param rings how many rings
     * @param pitch 0 gentle, 1 curving up to a steep top, 2 steep
     */
    static int[] heights(int rings, int pitch) {
        int[] h = new int[Math.max(0, rings)];
        int steepFrom = pitch <= 0 ? Integer.MAX_VALUE : pitch == 1 ? Math.max(3, (rings + 3) / 2) : 2;
        for (int k = 0; k < h.length; k++) {
            int rise = k < 2 ? 1 : k >= steepFrom ? 4 : 2;
            h[k] = (k == 0 ? 0 : h[k - 1]) + rise;
        }
        return h;
    }

    /**
     * How many layers the ridge of a curved roof (see {@link #roof}) stands above its eaves: the ridge block lies at
     * {@code y + roofRise(...)}, its horns one layer higher. For the width {@code across} of the eaves across the ridge (odd)
     * and the pitch: about half the width at pitch 0, three quarters at pitch 1.
     */
    static int roofRise(int across, int pitch) {
        int rings = (across - 1) / 2;
        int[] h = heights(rings, pitch);
        return rings > 0 ? (h[rings - 1] + 1) / 2 : 0;
    }

    private static int rise(int[] h, int k) {
        return h[k] - (k == 0 ? 0 : h[k - 1]);
    }

    /** The lowest layer a piece of a roof of surface height {@code h} and rise {@code rise} takes. */
    private static int pieceBottom(int y, int h, int rise) {
        if (rise <= 1) {
            return h % 2 == 1 ? y + (h - 1) / 2 : y + h / 2 - 1;
        }
        return y + (h + 1) / 2 - 1 - (rise / 2 - 1);
    }

    /**
     * One piece of a roof whose surface stands {@code h} half blocks above the eaves at layer {@code y}: a slab where it
     * rises half a block, a stair where it rises one, full blocks under a stair where it rises more.
     */
    private static void piece(Structure s, int x, int y, int z, int h, int rise, String stair, Kit.Roof roof) {
        if (rise <= 1) {
            put(s, x, h % 2 == 1 ? y + (h - 1) / 2 : y + h / 2 - 1, z, h % 2 == 1 ? Kit.slab(roof.slab()) : Kit.topSlab(roof.slab()));
            return;
        }
        int top = y + (h + 1) / 2 - 1;
        for (int k = 1; k < rise / 2; k++) {
            put(s, x, top - k, z, roof.block());
        }
        put(s, x, top, z, stair);
    }

    /** The stair of a ring of a roof at (x, z): facing the inside, turned at the corners where both sides are drawn. */
    private static String ringStair(Kit.Roof roof, int x, int z, int west, int back, int east, int front, boolean rowsX, boolean rowsZ) {
        boolean onX = (z == back || z == front) && rowsX;
        boolean onZ = (x == west || x == east) && rowsZ;
        if (onX && onZ) {
            return inner(roof.stairs(), x == west ? -1 : 1, z == back ? -1 : 1, "bottom");
        }
        if (onX) {
            return Kit.stairs(roof.stairs(), z == back ? "south" : "north");
        }
        return Kit.stairs(roof.stairs(), x == west ? "east" : "west");
    }

    /**
     * One ring of a roof on the rectangle west..east, back..front: the rows along x on the back and the front when
     * {@code rowsX}, the rows along z on the two sides when {@code rowsZ}.
     */
    private static void ring(Structure s, int west, int back, int east, int front, int y, int h, int rise, Kit.Roof roof,
                             boolean rowsX, boolean rowsZ) {
        if (rowsX) {
            for (int x = west; x <= east; x++) {
                for (int z : new int[]{back, front}) {
                    piece(s, x, y, z, h, rise, ringStair(roof, x, z, west, back, east, front, true, rowsZ), roof);
                }
            }
        }
        if (rowsZ) {
            for (int z = back; z <= front; z++) {
                for (int x : new int[]{west, east}) {
                    piece(s, x, y, z, h, rise, ringStair(roof, x, z, west, back, east, front, rowsX, true), roof);
                }
            }
        }
    }

    /**
     * A curved roof over the rectangle of its eaves {@code x1..x2, z1..z2} (overhang included; an odd width across the
     * ridge), its eaves at layer {@code y}. Its slopes curve as {@link #heights} says: slabs at the eaves, stairs, then
     * steep. The ridge runs along the longer side; it is a row of full blocks one layer above the slopes, and its two ends
     * rise in horns (stairs turned outward). Its four corners sweep upward: the last {@code lift} blocks of the eaves before
     * each corner rise half a block each and the corner itself ends in a tip turned up and out.
     *
     * <p>{@code hipRings} says how the ends are made: {@link #HIP} (or more rings than the roof has) a hip roof sloping on
     * all four sides, a pyramid over a square; {@link #GABLE} (0) a gable roof whose ends are gables one block inside the
     * eaves; between, a hip-and-gable roof: the first {@code hipRings} rings slope on all sides, above them the roof
     * continues as a gable roof whose gables stand {@code hipRings} blocks inside the ends of the eaves, over the hip, with
     * the gable roof overhanging them by one block. The gables are filled with {@code gable} (nothing when null) from the
     * layer above the eaves up, wherever nothing stands yet.
     *
     * @param pitch 0 gentle, 1 curving to a steep top (halls, gates), 2 steep
     * @param lift  how far the corners sweep up: 0 not at all, 1 a little, 2 or 3 more (large roofs)
     * @return the layer of the ridge (the top of the roof without its horns), or the layer above the point of a pyramid
     */
    static int roof(Structure s, int x1, int z1, int x2, int z2, int y, int hipRings, int pitch, int lift, Kit.Roof roof, String gable) {
        boolean alongX = x2 - x1 >= z2 - z1;
        int a1 = alongX ? x1 : z1;
        int a2 = alongX ? x2 : z2;
        int c1 = alongX ? z1 : x1;
        int c2 = alongX ? z2 : x2;
        int rings = (c2 - c1) / 2;
        int[] h = heights(rings, pitch);
        int g = Math.min(hipRings, rings);
        int inset = g == 0 ? 0 : g - 1;
        for (int k = 0; k < rings; k++) {
            int r = rise(h, k);
            if (k < g) {
                ring(s, x1 + k, z1 + k, x2 - k, z2 - k, y, h[k], r, roof, true, true);
            } else if (alongX) {
                ring(s, x1 + inset, z1 + k, x2 - inset, z2 - k, y, h[k], r, roof, true, false);
            } else {
                ring(s, x1 + k, z1 + inset, x2 - k, z2 - inset, y, h[k], r, roof, false, true);
            }
        }
        int lastH = rings > 0 ? h[rings - 1] : 0;
        int top = y + (lastH + 1) / 2;
        int m1 = c1 + rings;
        int m2 = c2 - rings;
        int from = g >= rings ? a1 + rings : a1 + inset;
        int to = g >= rings ? a2 - rings : a2 - inset;
        // The gables, filled under the rows of the gable roof above them; a gable of plaster gets a frame of timber: a tie
        // beam along its foot and a king post up its middle.
        if (g < rings && gable != null) {
            int w = Math.max(1, g);
            boolean framed = gable.equals(PLASTER);
            for (int wall : new int[]{a1 + w, a2 - w}) {
                for (int c = c1 + w; c <= c2 - w; c++) {
                    int k = Math.min(c - c1, c2 - c);
                    int under = k >= rings ? top - 2 : pieceBottom(y, h[k], rise(h, k)) - 1;
                    for (int yy = y + 1; yy <= under; yy++) {
                        int px = alongX ? wall : c;
                        int pz = alongX ? c : wall;
                        if (!solid(s, px, yy, pz)) {
                            String state = gable;
                            if (framed && c == m1) {
                                state = Kit.log(POST, "y");
                            } else if (framed && yy == y + 1) {
                                state = Kit.log(POST, alongX ? "z" : "x");
                            }
                            put(s, px, yy, pz, state);
                        }
                    }
                }
            }
        }
        // The ridge: a bed between the last rows and a row of full blocks above it, its ends rising in horns.
        for (int a = from; a <= to; a++) {
            for (int c = m1; c <= m2; c++) {
                at(s, alongX, a, top - 1, c, roof.block());
                at(s, alongX, a, top, c, roof.block());
            }
        }
        if (from < to) {
            // horns turned outward; on a large roof a slab before each, so that the ridge curls up into them
            for (int c = m1; c <= m2; c++) {
                at(s, alongX, from, top + 1, c, Kit.stairs(roof.stairs(), alongX ? "west" : "north"));
                at(s, alongX, to, top + 1, c, Kit.stairs(roof.stairs(), alongX ? "east" : "south"));
                if (rings >= 5 && to - from >= 4) {
                    at(s, alongX, from + 1, top + 1, c, Kit.slab(roof.slab()));
                    at(s, alongX, to - 1, top + 1, c, Kit.slab(roof.slab()));
                }
            }
        }
        if (lift > 0) {
            sweep(s, x1, z1, x2, z2, y, lift, roof, g > 0 || !alongX, g > 0 || alongX);
        }
        return from == to && m1 == m2 ? top + 1 : top;
    }

    /**
     * Sweeps up the four corners of the eaves of a roof whose eave ring (slabs at layer {@code y}) lies on the rectangle:
     * the last {@code lift} blocks of each eave before the corner rise half a block each, the corner itself rises further
     * and ends in a stair turned up and out.
     *
     * @param sidesZ whether the eaves run along the west and east sides (a hip), so that they sweep up there too
     * @param sidesX whether they run along the back and the front
     */
    static void sweep(Structure s, int x1, int z1, int x2, int z2, int y, int lift, Kit.Roof roof, boolean sidesZ, boolean sidesX) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int cx = sx < 0 ? x1 : x2;
                int cz = sz < 0 ? z1 : z2;
                for (int d = 1; d <= lift; d++) {
                    int h = 2 + lift - d;
                    if (sidesX) {
                        raise(s, cx - sx * d, y, cz, h, roof);
                    }
                    if (sidesZ) {
                        raise(s, cx, y, cz - sz * d, h, roof);
                    }
                }
                int h0 = 2 + lift;
                for (int yy = y; yy <= y + lift + 1; yy++) {
                    clear(s, cx, yy, cz);
                }
                if (h0 % 2 == 1) {
                    put(s, cx, y + (h0 - 1) / 2, cz, outer(roof.stairs(), sx, sz, "bottom"));
                } else {
                    put(s, cx, y + h0 / 2 - 1, cz, Kit.topSlab(roof.slab()));
                    put(s, cx, y + h0 / 2, cz, outer(roof.stairs(), sx, sz, "bottom"));
                }
            }
        }
    }

    /** Puts a thin piece of an eave (a slab) at the surface height {@code h} in half blocks above layer {@code y}. */
    private static void raise(Structure s, int x, int y, int z, int h, Kit.Roof roof) {
        for (int yy = y; yy <= y + h / 2; yy++) {
            clear(s, x, yy, z);
        }
        put(s, x, h % 2 == 1 ? y + (h - 1) / 2 : y + h / 2 - 1, z, h % 2 == 1 ? Kit.slab(roof.slab()) : Kit.topSlab(roof.slab()));
    }

    /** A hip roof (see {@link #roof}) with corners sweeping up by one or two blocks by its size. */
    static int hipRoof(Structure s, int x1, int z1, int x2, int z2, int y, int pitch) {
        return roof(s, x1, z1, x2, z2, y, HIP, pitch, lift(x1, z1, x2, z2), ROOF, null);
    }

    /**
     * A hip-and-gable roof (see {@link #roof}): the hips take the outer half of the rings, the gables above them are of
     * white plaster with a dark frame.
     */
    static int hipGableRoof(Structure s, int x1, int z1, int x2, int z2, int y, int pitch) {
        int rings = Math.min(x2 - x1, z2 - z1) / 2;
        return roof(s, x1, z1, x2, z2, y, Math.max(1, rings / 2), pitch, lift(x1, z1, x2, z2), ROOF, PLASTER);
    }

    /** A gable roof (see {@link #roof}) with gables of timber boards. */
    static int gableRoof(Structure s, int x1, int z1, int x2, int z2, int y, int pitch) {
        return roof(s, x1, z1, x2, z2, y, GABLE, pitch, lift(x1, z1, x2, z2), ROOF, PLANKS);
    }

    /** How far the corners of the eaves of a roof of this size sweep up: 1 for small roofs, 2 from 13 across, 3 from 21. */
    static int lift(int x1, int z1, int x2, int z2) {
        int across = Math.min(x2 - x1, z2 - z1) + 1;
        return across >= 21 ? 3 : across >= 13 ? 2 : 1;
    }

    /**
     * A skirt roof around a storey whose walls stand on the rectangle {@code x1..x2, z1..z2}: the eaves {@code overhang}
     * blocks out from the walls at layer {@code y} (the layer of the head beam), rings of a curved roof rising inward over
     * the walls and {@code step} blocks further in, where the next storey stands on a floor laid inside the last ring; the
     * corners sweep up. With {@code step} 0 the next storey stands right on the walls. This is the roof of a storey of a
     * pagoda or a keep, and the lower roof of a hall with two roofs (whose upper walls then go on up from the walls).
     *
     * @return the layer of the sill of the next storey, standing {@code step} blocks inside the walls
     */
    static int skirt(Structure s, int x1, int z1, int x2, int z2, int y, int overhang, int step, int lift, Kit.Roof roof) {
        int rings = overhang + step;
        int[] h = heights(rings, 0);
        for (int k = 0; k < rings; k++) {
            ring(s, x1 - overhang + k, z1 - overhang + k, x2 + overhang - k, z2 + overhang - k, y, h[k], rise(h, k), roof, true, true);
        }
        if (lift > 0) {
            sweep(s, x1 - overhang, z1 - overhang, x2 + overhang, z2 + overhang, y, lift, roof, true, true);
        }
        return y + (h[rings - 1] + 1) / 2 - 1;
    }

    // ---------------------------------------------------------------- finials, ridge ends

    /**
     * The spire of rings on the point of a pagoda or a pavilion (sorin), {@code height} layers from layer {@code y}: a slab
     * of the spire (the dew basin), then a mast of copper rods carrying rings, slabs of the spire on every second layer up
     * to two layers under the top, and a bare tip.
     *
     * @return the layer above its top
     */
    static int finial(Structure s, int x, int y, int z, int height) {
        put(s, x, y, z, Kit.slab(FINIAL.slab()));
        for (int yy = y + 1; yy < y + height; yy++) {
            boolean ring = (yy - y) % 2 == 0 && yy < y + height - 2;
            put(s, x, yy, z, ring ? Kit.slab(FINIAL.slab()) : ROD);
        }
        return y + height;
    }

    // ---------------------------------------------------------------- platforms and steps

    /**
     * A platform of stone over the rectangle {@code x1..x2, z1..z2} from layer {@code y1} up to its floor at {@code y2}
     * (one stands at {@code y2 + 1}): a course of rough stone at its foot, faces of the wall stone, a coping of trim along its
     * edge at the floor and the inside filled. With {@code batter} greater than 0 its faces lean in: they step in one block
     * every {@code batter} layers from the foot up, as the stone bases of keeps and gate towers, so that the rectangle is the
     * foot and the floor is smaller.
     *
     * @param floor the block of its floor inside the coping (stone slabs, planks, mats...), or null for the wall stone
     */
    static void platform(Structure s, int x1, int z1, int x2, int z2, int y1, int y2, int batter, String floor) {
        int in = 0;
        for (int y = y1; y <= y2; y++) {
            if (batter > 0 && y > y1 && (y - y1) % batter == 0) {
                in++;
            }
            int w = x1 + in;
            int e = x2 - in;
            int b = z1 + in;
            int f = z2 - in;
            fill(s, w, y, b, e, y, f, y == y1 ? ROUGH : STONE);
            if (batter > 0 && y < y2 && (y + 1 - y1) % batter == 0) {
                // the step where the face draws in: stairs of rough stone leaning on the face above
                ringOf(s, w, b, e, f, y, ROUGH_STAIRS, true);
            }
            if (y == y2) {
                for (int x = w; x <= e; x++) {
                    put(s, x, y, b, TRIM);
                    put(s, x, y, f, TRIM);
                }
                for (int z = b; z <= f; z++) {
                    put(s, w, y, z, TRIM);
                    put(s, e, y, z, TRIM);
                }
                if (floor != null && e - w >= 2 && f - b >= 2) {
                    fill(s, w + 1, y, b + 1, e - 1, y, f - 1, floor);
                }
            }
        }
    }

    /**
     * A ring of stairs on the rectangle, facing inward (their high side to the inside), turned at the corners.
     */
    private static void ringOf(Structure s, int w, int b, int e, int f, int y, String block, boolean bottom) {
        String half = bottom ? "bottom" : "top";
        for (int x = w + 1; x < e; x++) {
            put(s, x, y, b, stairs(block, "south", half, "straight"));
            put(s, x, y, f, stairs(block, "north", half, "straight"));
        }
        for (int z = b + 1; z < f; z++) {
            put(s, w, y, z, stairs(block, "east", half, "straight"));
            put(s, e, y, z, stairs(block, "west", half, "straight"));
        }
        put(s, w, y, b, inner(block, -1, -1, half));
        put(s, e, y, b, inner(block, 1, -1, half));
        put(s, w, y, f, inner(block, -1, 1, half));
        put(s, e, y, f, inner(block, 1, 1, half));
    }

    /**
     * A flight of steps of stone down from the edge of a platform, going the way of {@code facing}: {@code width} (odd)
     * steps side by side around the line {@code x} (or {@code z}), the first at (x, top, z), one layer and one block lower
     * each, down to the layer {@code bottom}; with cheeks of trim along both sides that step down with it (so it takes
     * {@code width + 2} across). With {@code ramp} the middle of a flight five or more wide is a way of carved stone between
     * stairs of trim, set apart from the two outer flights (the way of the ruler).
     */
    static void steps(Structure s, int x, int z, String facing, int width, int top, int bottom, boolean ramp) {
        int[] f = Furnish.step(facing);
        int[] side = Furnish.step(Furnish.left(facing));
        int hw = (width - 1) / 2;
        String up = Furnish.opposite(facing);
        for (int i = 0; top - i >= bottom; i++) {
            int layer = top - i;
            int px = x + f[0] * i;
            int pz = z + f[1] * i;
            for (int u = -hw; u <= hw; u++) {
                int qx = px + side[0] * u;
                int qz = pz + side[1] * u;
                boolean middle = ramp && width >= 5 && Math.abs(u) <= hw - 2;
                put(s, qx, layer, qz, middle ? (u == 0 ? CARVED : Kit.stairs(TRIM_STAIRS, up)) : Kit.stairs(STONE_STAIRS, up));
                for (int yy = bottom; yy < layer; yy++) {
                    put(s, qx, yy, qz, STONE);
                }
            }
            for (int u : new int[]{-hw - 1, hw + 1}) {
                int qx = px + side[0] * u;
                int qz = pz + side[1] * u;
                for (int yy = bottom; yy <= layer; yy++) {
                    put(s, qx, yy, qz, yy == layer ? TRIM : STONE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- pillars, brackets, frames

    /**
     * A red-lacquered pillar from layer {@code y1} (its base, the layer of the feet) to {@code y2}: a base block of trim and a
     * column of red over it; slender, a post of red nether brick wall on a base of stone wall, for canopies, small gates and
     * light pavilions.
     */
    static void pillar(Structure s, int x, int y1, int y2, int z, boolean slender) {
        put(s, x, y1, z, slender ? STONE_WALL : TRIM);
        for (int y = y1 + 1; y <= y2; y++) {
            put(s, x, y, z, slender ? RED_POST : RED);
        }
    }

    /**
     * A bracket set on the head of a pillar (dougong) whose top is at layer {@code y - 1}: at layer {@code y} a bearing block
     * of timber on the pillar with arms of stairs hanging upside down to both sides along the beam and one arm reaching out
     * the way of {@code facing}; with {@code reach} 2 a second tier at {@code y + 1} reaches one block further out, so that
     * the eaves above rest on a tip two blocks out from the pillar.
     */
    static void bracket(Structure s, int x, int y, int z, String facing, int reach) {
        int[] f = Furnish.step(facing);
        int[] side = Furnish.step(Furnish.left(facing));
        String back = Furnish.opposite(facing);
        put(s, x, y, z, PLANKS);
        for (int u = -1; u <= 1; u += 2) {
            int qx = x + side[0] * u;
            int qz = z + side[1] * u;
            put(s, qx, y, qz, Kit.upside(STAIRS, u < 0 ? Furnish.left(facing) : Furnish.opposite(Furnish.left(facing))));
        }
        put(s, x + f[0], y, z + f[1], Kit.upside(STAIRS, back));
        if (reach >= 2) {
            put(s, x + f[0], y + 1, z + f[1], PLANKS);
            put(s, x + 2 * f[0], y + 1, z + 2 * f[1], Kit.upside(STAIRS, back));
            put(s, x, y + 1, z, PLANKS);
        }
    }

    /**
     * A frame of timber with walls between its posts on the rectangle {@code x1..x2, z1..z2} from layer {@code y1} to
     * {@code y2}: upright posts at the corners and every {@code bay} blocks along each side, a head beam of lying logs at
     * {@code y2} and a sill of lying logs at {@code y1}, the panels between filled with {@code infill}. With {@code bay} 0
     * the posts stand at the corners and, on sides seven or more long, on both sides of the middle, which then frame a
     * door or window there. Openings are cut afterwards with {@link #screen}, {@link #window} and doors. Panels two or three
     * wide read best: a bay of 2 gives stripes of single panels, right for screens.
     *
     * @param post the state of the posts: {@code Kit.log(POST, "y")} or {@link #RED}
     */
    static void frame(Structure s, int x1, int z1, int x2, int z2, int y1, int y2, int bay, String post, String infill) {
        for (int y = y1 + 1; y < y2; y++) {
            for (int x = x1; x <= x2; x++) {
                String state = isPost(x - x1, x2 - x1, bay) ? post : infill;
                put(s, x, y, z1, state);
                put(s, x, y, z2, state);
            }
            for (int z = z1 + 1; z < z2; z++) {
                String state = isPost(z - z1, z2 - z1, bay) ? post : infill;
                put(s, x1, y, z, state);
                put(s, x2, y, z, state);
            }
        }
        s.beam(x1, z1, x2, z2, y1, POST);
        s.beam(x1, z1, x2, z2, y2, POST);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            put(s, c[0], y1, c[1], post);
            put(s, c[0], y2, c[1], post);
        }
    }

    private static boolean isPost(int i, int length, int bay) {
        if (i == 0 || i == length) {
            return true;
        }
        if (bay > 0) {
            return i % bay == 0;
        }
        return length >= 6 && Math.abs(i - length / 2) == 1;
    }

    /**
     * A sliding screen of paper in a straight wall from (x1, z1) to (x2, z2) (one of them equal), from layer {@code y1} to
     * {@code y2}: a kneeboard of timber in the lowest layer when it is three or more high, a lattice rail of timber in the
     * top layer when it is five or more high, and panes of paper between; with {@code solid} the paper is white wool (an
     * opaque screen inside a house).
     */
    static void screen(Structure s, int x1, int z1, int x2, int z2, int y1, int y2, boolean solid) {
        int high = y2 - y1 + 1;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int y = y1; y <= y2; y++) {
                    String state = solid ? PAPER_SOLID : PAPER;
                    if (high >= 3 && y == y1) {
                        state = PLANKS;
                    } else if (high >= 5 && y == y2) {
                        state = FENCE;
                    }
                    put(s, x, y, z, state);
                }
            }
        }
    }

    /**
     * A window of paper in a wall that looks the way of {@code facing}, its lowest middle pane at (x, y, z), {@code width}
     * (odd) wide and {@code height} high: panes of paper in the wall, a sill of timber stairs hanging upside down before the
     * wall under it and a little hood of timber slabs over it.
     */
    static void window(Structure s, int x, int y, int z, String facing, int width, int height) {
        int[] out = Furnish.step(facing);
        int[] along = Furnish.step(Furnish.left(facing));
        int hw = (width - 1) / 2;
        for (int u = -hw; u <= hw; u++) {
            for (int v = 0; v < height; v++) {
                put(s, x + along[0] * u, y + v, z + along[1] * u, PAPER);
            }
            put(s, x + along[0] * u + out[0], y - 1, z + along[1] * u + out[1], Kit.upside(STAIRS, Furnish.opposite(facing)));
            put(s, x + along[0] * u + out[0], y + height, z + along[1] * u + out[1], Kit.slab(SLAB));
        }
    }

    /**
     * A round opening in a straight wall (along x when {@code alongX}, else along z) through the cell line of (x, z):
     * {@code diameter} (odd, 3 or more) wide and high, its lowest row at layer {@code y}. The cells of the disc are cleared
     * (or filled with {@code fill}, panes of a round window); the cells of the wall that the circle cuts at its steps become
     * stairs of {@code stairs} so that the outline rounds, and when {@code frame} is given the cells around the opening become
     * a ring of it (the stairs then of {@code stairs} as well). A round gate (moon gate) is an opening whose lowest row is
     * at the feet: with a diameter of 5 or more one walks through it.
     */
    static void moonGate(Structure s, int x, int y, int z, boolean alongX, int diameter, String frame, String stairs, String fill) {
        int r = (diameter - 1) / 2;
        String plus = alongX ? "east" : "south";
        String minus = alongX ? "west" : "north";
        for (int v = -r - 1; v <= r + 1; v++) {
            for (int u = -r - 1; u <= r + 1; u++) {
                int px = alongX ? x + u : x;
                int pz = alongX ? z : z + u;
                int py = y + r + v;
                if (round(u, v, r)) {
                    put(s, px, py, pz, fill != null ? fill : Structure.AIR);
                    continue;
                }
                boolean inward = u != 0 && round(u - Integer.signum(u), v, r);
                boolean openAbove = round(u, v + 1, r);
                boolean openBelow = round(u, v - 1, r);
                boolean ring = frame != null && (round(u + 1, v, r) || round(u - 1, v, r) || openAbove || openBelow);
                String facing = u > 0 ? plus : minus;
                if (inward && openAbove && !openBelow) {
                    put(s, px, py, pz, stairs(stairs, facing, "bottom", "straight"));
                } else if (inward && openBelow && !openAbove) {
                    put(s, px, py, pz, stairs(stairs, facing, "top", "straight"));
                } else if (ring) {
                    put(s, px, py, pz, frame);
                }
            }
        }
    }

    /** Whether a cell lies in a round opening of the half-size {@code r} (a cross for 1, rounded beyond). */
    private static boolean round(int u, int v, int r) {
        return Math.abs(u) <= r && Math.abs(v) <= r && u * u + v * v <= r * r + r / 2;
    }

    /** A round window of paper (see {@link #moonGate}) of the given diameter in a wall of plaster, framed with timber. */
    static void roundWindow(Structure s, int x, int y, int z, boolean alongX, int diameter) {
        moonGate(s, x, y, z, alongX, diameter, PLANKS, STAIRS, PAPER);
    }

    /**
     * A railing along a straight line from (x1, z1) to (x2, z2) at layer {@code y}: rails of timber fence, or with
     * {@code red} a low balustrade of red lacquer (red nether brick wall); {@link Structure#connect()} joins it to the
     * pillars or walls at its ends. Draw it between pillars, not through them.
     */
    static void railing(Structure s, int x1, int z1, int x2, int z2, int y, boolean red) {
        fill(s, x1, y, z1, x2, y, z2, red ? RED_POST : FENCE);
    }

    // ---------------------------------------------------------------- whole parts

    /**
     * A storey of a pagoda (or a keep) on the rectangle of its walls {@code x1..x2, z1..z2}, standing on the floor at layer
     * {@code floor}: a frame of posts with white walls between them, the head beam {@code height} layers above the floor
     * (posts at the corners and on both sides of the middle, see {@link #frame} with bay 0), a door or a tall window of paper
     * in the middle of every side, a lantern inside, a ceiling, then
     * its skirt roof (see {@link #skirt}) with the eaves {@code overhang} blocks out at the layer of the head beam, stepping
     * {@code step} blocks in for the storey above, whose floor it lays.
     *
     * @param door the side whose middle holds a door (north, south, east, west), or null for windows all round
     * @param post the state of the posts: {@code Kit.log(POST, "y")} for dark posts or {@link #RED} for red ones
     * @return the floor of the storey above (its walls stand {@code step} blocks inside these)
     */
    static int storey(Structure s, int x1, int z1, int x2, int z2, int floor, int height, int overhang, int step, String door, String post) {
        int head = floor + height;
        frame(s, x1, z1, x2, z2, floor, head, 0, post, PLASTER);
        fill(s, x1 + 1, floor, z1 + 1, x2 - 1, floor, z2 - 1, FLOOR);
        int mx = (x1 + x2) / 2;
        int mz = (z1 + z2) / 2;
        int wh = Math.max(1, height - 2);
        for (String side : new String[]{"north", "south", "west", "east"}) {
            int px = side.equals("west") ? x1 : side.equals("east") ? x2 : mx;
            int pz = side.equals("north") ? z1 : side.equals("south") ? z2 : mz;
            if (side.equals(door)) {
                s.door(px, floor + 1, pz, DOOR, Furnish.opposite(side), "left");
                put(s, px, floor + 3, pz, PLASTER);
            } else {
                for (int yy = floor + 2; yy < floor + 2 + wh && yy < head; yy++) {
                    put(s, px, yy, pz, PAPER);
                }
            }
        }
        put(s, mx, head - 1, mz, Kit.hangingLantern());
        fill(s, x1 + 1, head, z1 + 1, x2 - 1, head, z2 - 1, PLANKS);
        int next = skirt(s, x1, z1, x2, z2, head, overhang, step, overhang >= 3 ? 2 : 1, ROOF);
        fill(s, x1 + 1, head + 1, z1 + 1, x2 - 1, next, z2 - 1, PLANKS);
        return next;
    }

    /**
     * A pagoda around the column (cx, cz), its ground storey on the floor at layer {@code floor}: storeys of white walls
     * between red posts whose walls have the half-sizes {@code halves} from the ground up (the head beam of the first
     * {@code ground} layers above its floor, of the others {@code upper}), each under its skirt roof with eaves
     * {@code overhang} blocks out and the upper ones ringed by a red balcony railing standing on the roof below; the last
     * under a pyramid roof whose point carries a spire of rings (see {@link #finial}). Ladders lead from storey to storey
     * along the back wall. The door of the ground storey looks south. With storeys 4 high and eaves 3 out, two layers of each
     * wall show between the roofs.
     *
     * @return the layer above the top of the spire
     */
    static int pagoda(Structure s, int cx, int cz, int floor, int[] halves, int ground, int upper, int overhang) {
        int f = floor;
        for (int i = 0; i < halves.length; i++) {
            int r = halves[i];
            int next = i + 1 < halves.length ? halves[i + 1] : -1;
            int height = i == 0 ? ground : upper;
            if (i > 0) {
                balcony(s, cx - r - 1, cz - r - 1, cx + r + 1, cz + r + 1, f + 1);
            }
            if (next >= 0) {
                int nf = storey(s, cx - r, cz - r, cx + r, cz + r, f, height, overhang, r - next, i == 0 ? "south" : null, RED);
                // the ladder up into the storey above, on a post where that storey steps in
                int lz = cz - Math.min(r, next) + 1;
                for (int yy = f + 1; yy <= nf; yy++) {
                    if (next < r) {
                        put(s, cx, yy, lz - 1, PLANKS);
                    }
                    put(s, cx, yy, lz, Kit.ladder("south"));
                }
                f = nf;
            } else {
                int head = f + height;
                frame(s, cx - r, cz - r, cx + r, cz + r, f, head, 0, RED, PLASTER);
                fill(s, cx - r + 1, f, cz - r + 1, cx + r - 1, f, cz + r - 1, FLOOR);
                for (int yy = f + 2; yy < head; yy++) {
                    put(s, cx, yy, cz - r, PAPER);
                    put(s, cx, yy, cz + r, PAPER);
                    put(s, cx - r, yy, cz, PAPER);
                    put(s, cx + r, yy, cz, PAPER);
                }
                put(s, cx, head - 1, cz, Kit.hangingLantern());
                fill(s, cx - r + 1, head, cz - r + 1, cx + r - 1, head, cz + r - 1, PLANKS);
                int top = roof(s, cx - r - overhang, cz - r - overhang, cx + r + overhang, cz + r + overhang, head, HIP, 1,
                        overhang >= 3 ? 2 : 1, ROOF, null);
                return finial(s, cx, top, cz, 6);
            }
        }
        return f;
    }

    /**
     * A balcony railing of red lacquer around the rectangle at layer {@code y}: a low wall of red nether bricks (joined into
     * a balustrade by {@link Structure#connect()}), standing on whatever is under it (the top ring of a roof below).
     */
    static void balcony(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1; x <= x2; x++) {
            put(s, x, y, z1, RED_POST);
            put(s, x, y, z2, RED_POST);
        }
        for (int z = z1; z <= z2; z++) {
            put(s, x1, y, z, RED_POST);
            put(s, x2, y, z, RED_POST);
        }
    }

    /**
     * An open pavilion (ting) around (cx, cz) standing on layer {@code y}: a square of red pillars {@code 2 * r + 1} wide
     * (pillars at the corners and every two blocks) {@code height} layers high on a low platform of stone with a step on
     * each side, beams and brackets on their heads, benches of timber between the pillars at the back and the sides, a
     * lantern of red cloth in the middle, and a pyramid roof with sweeping corners and a spire of rings on its point.
     *
     * @return the layer above the top of the spire
     */
    static int pavilion(Structure s, int cx, int y, int cz, int r, int height) {
        platform(s, cx - r - 1, cz - r - 1, cx + r + 1, cz + r + 1, y, y, 0, STONE);
        for (String side : new String[]{"north", "south", "west", "east"}) {
            int[] d = Furnish.step(side);
            put(s, cx + d[0] * (r + 2), y, cz + d[1] * (r + 2), Kit.stairs(STONE_STAIRS, Furnish.opposite(side)));
        }
        int head = y + height + 1;
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                if (Math.abs(dx) == r || Math.abs(dz) == r) {
                    pillar(s, cx + dx, y + 1, head - 1, cz + dz, false);
                }
            }
        }
        s.beam(cx - r, cz - r, cx + r, cz + r, head, POST);
        for (int dx = -r; dx <= r; dx += 2) {
            bracket(s, cx + dx, head, cz - r, "north", 1);
            bracket(s, cx + dx, head, cz + r, "south", 1);
        }
        for (int dz = -r + 2; dz <= r - 2; dz += 2) {
            bracket(s, cx - r, head, cz + dz, "west", 1);
            bracket(s, cx + r, head, cz + dz, "east", 1);
        }
        // benches between the pillars on three sides, open to the south
        for (int i = -r + 1; i <= r - 1; i++) {
            if (Math.floorMod(i + r, 2) == 1) {
                put(s, cx + i, y + 1, cz - r, Kit.slab(SLAB));
                put(s, cx - r, y + 1, cz + i, Kit.slab(SLAB));
                put(s, cx + r, y + 1, cz + i, Kit.slab(SLAB));
            }
        }
        fill(s, cx - r + 1, head, cz - r + 1, cx + r - 1, head, cz + r - 1, PLANKS);
        clothLantern(s, cx, cz, head - 1, 1);
        int top = roof(s, cx - r - 2, cz - r - 2, cx + r + 2, cz + r + 2, head + 1, HIP, 1, 2, ROOF, null);
        return finial(s, cx, top, cz, 4);
    }

    /**
     * A gate under a heavy roof in a wall that runs along x, its passage {@code width} (odd) wide through the cells
     * {@code z - 1 .. z + 1} around the column {@code cx}, standing on layer {@code y} (the feet): four red pillars
     * {@code height} layers high at the corners of the passage, walls of plaster between the pillars of each side, door
     * leaves standing open, a lintel beam with a plaque of cloth over the passage, brackets on the heads of the pillars and a
     * heavy hip-and-gable roof whose eaves reach three blocks beyond the pillars, its corners swept up. Lanterns of red cloth
     * hang under the eaves before and behind the passage. It takes {@code width + 8} blocks along the wall and 9 across
     * ({@code z - 4 .. z + 4}); the roof rises about {@code height + 7} layers above {@code y}.
     *
     * @return the layer of the ridge of its roof
     */
    static int gate(Structure s, int cx, int y, int z, int width, int height) {
        int hw = (width - 1) / 2;
        int w = cx - hw - 1;
        int e = cx + hw + 1;
        int head = y + height;
        for (int x : new int[]{w, e}) {
            for (int dz = -1; dz <= 1; dz += 2) {
                pillar(s, x, y, head - 1, z + dz, false);
            }
            for (int yy = y; yy < head; yy++) {
                put(s, x, yy, z, yy == y ? TRIM : PLASTER);
            }
        }
        fill(s, w + 1, y - 1, z - 1, e - 1, y - 1, z + 1, TRIM);
        for (int x = w + 1; x <= e - 1; x++) {
            put(s, x, head - 2, z, PLASTER);
            put(s, x, head - 1, z, Kit.log(POST, "x"));
        }
        put(s, cx, head - 2, z + 1, Kit.wallBanner("red", "south"));
        put(s, cx, head - 2, z - 1, Kit.wallBanner("red", "north"));
        s.door(w + 1, y, z, DOOR, "south", "left");
        s.door(e - 1, y, z, DOOR, "south", "right");
        for (int yy = y; yy <= y + 1; yy++) {
            put(s, w + 1, yy, z, s.get(w + 1, yy, z).replace("open=false", "open=true"));
            put(s, e - 1, yy, z, s.get(e - 1, yy, z).replace("open=false", "open=true"));
        }
        s.beam(w, z - 1, e, z + 1, head, POST);
        for (int x : new int[]{w, e}) {
            bracket(s, x, head, z - 1, "north", 2);
            bracket(s, x, head, z + 1, "south", 2);
            put(s, x, head, z, PLANKS);
        }
        fill(s, w + 1, head, z, e - 1, head, z, PLANKS);
        fill(s, w + 1, head + 1, z - 1, e - 1, head + 1, z + 1, PLANKS);
        clothLantern(s, cx, z + 2, head + 1, 2);
        clothLantern(s, cx, z - 2, head + 1, 2);
        return roof(s, w - 3, z - 4, e + 3, z + 4, head + 2, 2, 1, 2, ROOF, PLASTER);
    }

    /**
     * A garden wall of white plaster on a footing of stone with a little roof of tiles on top (a coping): from layer
     * {@code y} up, {@code height} layers to the top of the plaster, the coping above. It runs along a straight line from
     * (x1, z1) to (x2, z2) or, with {@code closed}, around the rectangle they span. The coping is a little gable roof three
     * blocks wide over the wall (one block out on each side) with a ridge of slabs, turned at the corners; posts of timber
     * stand in the plaster every {@code bay} blocks.
     */
    static void gardenWall(Structure s, int x1, int z1, int x2, int z2, int y, int height, boolean closed, int bay) {
        int w = Math.min(x1, x2);
        int e = Math.max(x1, x2);
        int b = Math.min(z1, z2);
        int f = Math.max(z1, z2);
        boolean[][] line = new boolean[s.sizeX][s.sizeZ];
        for (int x = w; x <= e; x++) {
            for (int z = b; z <= f; z++) {
                boolean on = closed ? x == w || x == e || z == b || z == f : true;
                if (on && s.inside(x, 0, z)) {
                    line[x][z] = true;
                }
            }
        }
        int top = y + height;
        for (int x = 0; x < s.sizeX; x++) {
            for (int z = 0; z < s.sizeZ; z++) {
                if (!line[x][z]) {
                    continue;
                }
                put(s, x, y, z, STONE);
                boolean post = (x - w + z - b) % bay == 0;
                for (int yy = y + 1; yy < top; yy++) {
                    put(s, x, yy, z, post ? Kit.log(POST, "y") : PLASTER);
                }
                put(s, x, top, z, ROOF.block());
                put(s, x, top + 1, z, Kit.slab(ROOF.slab()));
            }
        }
        // The coping: stairs around the line, facing it, turned at the outer corners.
        for (int x = 0; x < s.sizeX; x++) {
            for (int z = 0; z < s.sizeZ; z++) {
                if (line[x][z]) {
                    continue;
                }
                boolean n1 = z > 0 && line[x][z - 1];
                boolean s1 = z + 1 < s.sizeZ && line[x][z + 1];
                boolean w1 = x > 0 && line[x - 1][z];
                boolean e1 = x + 1 < s.sizeX && line[x + 1][z];
                String state = null;
                if (n1 || s1 || w1 || e1) {
                    if (n1 && (w1 || e1)) {
                        state = stairs(ROOF.stairs(), "north", "bottom", w1 ? "inner_left" : "inner_right");
                    } else if (s1 && (w1 || e1)) {
                        state = stairs(ROOF.stairs(), "south", "bottom", w1 ? "inner_right" : "inner_left");
                    } else if (n1) {
                        state = Kit.stairs(ROOF.stairs(), "north");
                    } else if (s1) {
                        state = Kit.stairs(ROOF.stairs(), "south");
                    } else if (w1) {
                        state = Kit.stairs(ROOF.stairs(), "west");
                    } else {
                        state = Kit.stairs(ROOF.stairs(), "east");
                    }
                } else {
                    boolean nw = x > 0 && z > 0 && line[x - 1][z - 1];
                    boolean ne = x + 1 < s.sizeX && z > 0 && line[x + 1][z - 1];
                    boolean sw = x > 0 && z + 1 < s.sizeZ && line[x - 1][z + 1];
                    boolean se = x + 1 < s.sizeX && z + 1 < s.sizeZ && line[x + 1][z + 1];
                    if (nw) {
                        state = stairs(ROOF.stairs(), "north", "bottom", "outer_left");
                    } else if (ne) {
                        state = stairs(ROOF.stairs(), "north", "bottom", "outer_right");
                    } else if (sw) {
                        state = stairs(ROOF.stairs(), "south", "bottom", "outer_right");
                    } else if (se) {
                        state = stairs(ROOF.stairs(), "south", "bottom", "outer_left");
                    }
                }
                if (state != null && !solid(s, x, top, z)) {
                    put(s, x, top, z, state);
                }
            }
        }
    }

    /**
     * A fortress wall along a straight line from (x1, z1) to (x2, z2), its outer face on that line looking the way of
     * {@code outward}, standing on layer {@code y}: a base of stone {@code height} layers high and three blocks thick (the
     * line and two blocks inward), its lower half battered by a skirt of rough stone one block out with stairs on top; a
     * walk on the top of the base (one stands at {@code y + height}); along the outer edge a parapet of white plaster two
     * high with loopholes every three blocks, under a coping of tiles (see {@link #gardenWall}). Corner towers, gates and
     * stairs up to the walk are drawn by the castle.
     */
    static void rampart(Structure s, int x1, int z1, int x2, int z2, int y, int height, String outward) {
        int[] o = Furnish.step(outward);
        int top = y + height - 1;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int k = 0; k <= 2; k++) {
                    for (int yy = y; yy <= top; yy++) {
                        put(s, x - o[0] * k, yy, z - o[1] * k, yy == y ? ROUGH : STONE);
                    }
                }
                int batter = y + height / 2;
                for (int yy = y; yy < batter; yy++) {
                    put(s, x + o[0], yy, z + o[1], ROUGH);
                }
                put(s, x + o[0], batter, z + o[1], Kit.stairs(ROUGH_STAIRS, Furnish.opposite(outward)));
            }
        }
        gardenWall(s, x1, z1, x2, z2, top, 3, false, 99);
        int n = 0;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                if (n % 3 == 1) {
                    put(s, x, top + 2, z, Kit.BARS);
                }
                n++;
            }
        }
    }

    /**
     * A lattice front of slats (koshi) in a straight wall from (x1, z1) to (x2, z2), layers {@code y1} to {@code y2}: posts of
     * timber fence in every cell, which {@link Structure#connect()} joins into a grid of slats and rails, as the fronts of
     * shops and town houses.
     */
    static void lattice(Structure s, int x1, int z1, int x2, int z2, int y1, int y2) {
        fill(s, x1, y1, z1, x2, y2, z2, FENCE);
    }

    /**
     * A shop curtain (noren) over a door: banners of cloth hanging at layer {@code y} on the face of a wall from (x1, z1) to
     * (x2, z2), the cells before the wall on the side {@code facing}; a banner hangs about two blocks down, so at the layer
     * above a door it covers the top of the doorway.
     */
    static void noren(Structure s, int x1, int z1, int x2, int z2, int y, String facing) {
        int[] f = Furnish.step(facing);
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                put(s, x + f[0], y, z + f[1], Kit.wallBanner("red", facing));
            }
        }
    }

    // ---------------------------------------------------------------- garden

    /**
     * A stone lantern (toro) of grey granite standing on layer {@code y}. Large (3 wide, 7 high): a plinth of stairs, a
     * shaft two high, a block carrying the light, a lantern on it, a roof three wide with its corners turned up and a knob on
     * top. Small (1 wide, 4 high): a post, a block, a lantern on it and a cap.
     */
    static void stoneLantern(Structure s, int x, int y, int z, boolean large) {
        if (!large) {
            put(s, x, y, z, GRANITE_WALL);
            put(s, x, y + 1, z, GRANITE);
            put(s, x, y + 2, z, Kit.lantern());
            put(s, x, y + 3, z, Kit.slab(GRANITE_SLAB));
            return;
        }
        ringOf(s, x - 1, z - 1, x + 1, z + 1, y, GRANITE_STAIRS, true);
        put(s, x, y, z, GRANITE);
        put(s, x, y + 1, z, GRANITE_WALL);
        put(s, x, y + 2, z, GRANITE_WALL);
        put(s, x, y + 3, z, GRANITE);
        put(s, x, y + 4, z, Kit.lantern());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    put(s, x, y + 5, z, GRANITE);
                } else if (dx != 0 && dz != 0) {
                    put(s, x + dx, y + 5, z + dz, outer(GRANITE_STAIRS, dx, dz, "bottom"));
                } else {
                    put(s, x + dx, y + 5, z + dz, Kit.slab(GRANITE_SLAB));
                }
            }
        }
        put(s, x, y + 6, z, GRANITE_WALL);
    }

    /**
     * A lantern of red cloth hanging under the layer {@code top + 1} on a chain {@code chain} layers long (the chain from
     * {@code top - chain + 1} to {@code top}): a body of cloth with a light hanging under it.
     */
    static void clothLantern(Structure s, int x, int z, int top, int chain) {
        for (int y = top - chain + 1; y <= top; y++) {
            put(s, x, y, z, Kit.chain());
        }
        put(s, x, top - chain, z, Kit.CLOTH);
        put(s, x, top - chain - 1, z, Kit.hangingLantern());
    }

    /**
     * A lantern of cloth on a post of timber standing on layer {@code y}, as along the paths of a garden or a street: a post
     * of fence {@code height} layers high, a bracket of fence turned the way of {@code facing} on its top and the lantern
     * hanging from it.
     */
    static void lanternPost(Structure s, int x, int y, int z, int height, String facing) {
        int[] f = Furnish.step(facing);
        for (int yy = y; yy < y + height; yy++) {
            put(s, x, yy, z, FENCE);
        }
        put(s, x, y + height, z, Kit.slab(SLAB));
        put(s, x + f[0], y + height, z + f[1], Kit.slab(SLAB));
        clothLantern(s, x + f[0], z + f[1], y + height - 1, 0);
    }

    /**
     * A pond in a closed basin: water at layer {@code y} over the oval inside the rectangle, its bottom at {@code y - 1} of
     * rough stone, its rim of rough stone and moss at layer {@code y} with a few rocks standing on it at {@code y + 1}.
     */
    static void pond(Structure s, int x1, int z1, int x2, int z2, int y) {
        double cx = (x1 + x2) / 2.0;
        double cz = (z1 + z2) / 2.0;
        double rx = (x2 - x1) / 2.0;
        double rz = (z2 - z1) / 2.0;
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                double d = Math.pow((x - cx) / (rx + 0.5), 2) + Math.pow((z - cz) / (rz + 0.5), 2);
                double rim = Math.pow((x - cx) / (rx + 1.5), 2) + Math.pow((z - cz) / (rz + 1.5), 2);
                if (d <= 1.0) {
                    put(s, x, y - 1, z, ROUGH);
                    put(s, x, y, z, WATER);
                } else if (rim <= 1.0) {
                    put(s, x, y - 1, z, ROUGH);
                    put(s, x, y, z, (x * 7 + z * 3) % 4 == 0 ? MOSS : ROUGH);
                    if ((x * 5 + z * 11) % 9 == 0) {
                        put(s, x, y + 1, z, (x + z) % 2 == 0 ? Kit.slab(ROUGH_SLAB) : ROUGH);
                    }
                }
            }
        }
    }

    /**
     * An arched bridge (drum bridge) over water from {@code a1} to {@code a2} along x (or along z), its deck {@code width}
     * (odd, 3 or 5) wide around the line {@code c}, its ends standing on layer {@code y} (the feet): the deck of timber rises
     * steeply in stairs from both ends and flattens in slabs over the middle; along its two edges runs a parapet of red
     * lacquer that follows the curve of the deck one layer above it, carried on a red band under the deck, so that from the
     * side the bridge shows as a smooth red arch; at its four ends stand red posts with caps of copper. One walks on the
     * {@code width - 2} middle cells.
     *
     * @return the layer of the feet on its crest
     */
    static int bridge(Structure s, int a1, int a2, int c, int y, int width, boolean alongX) {
        int half = (a2 - a1) / 2;
        int stairsUp = Math.max(1, (half + 1) / 2);
        int hw = (width - 1) / 2;
        String toPlus = alongX ? "east" : "south";
        String toMinus = alongX ? "west" : "north";
        int crest = y;
        for (int a = a1; a <= a2; a++) {
            int e = Math.min(a - a1, a2 - a);
            boolean rising = a - a1 < a2 - a;
            boolean flat = a - a1 == a2 - a;
            int h = e < stairsUp ? 2 * (e + 1) : 2 * stairsUp + (e - stairsUp + 1);
            int layer;
            String deck;
            String red;
            if (e < stairsUp && !flat) {
                layer = y + e;
                deck = Kit.stairs(FLOOR_STAIRS, rising ? toPlus : toMinus);
                red = Kit.stairs(RED_STAIRS, rising ? toPlus : toMinus);
            } else if (h % 2 == 1) {
                layer = y + (h - 1) / 2;
                deck = Kit.slab(FLOOR_SLAB);
                red = Kit.slab(RED_SLAB);
            } else {
                layer = y + h / 2 - 1;
                deck = Kit.topSlab(FLOOR_SLAB);
                red = RED_BLOCK;
            }
            crest = Math.max(crest, h % 2 == 1 ? layer : layer + 1);
            for (int d = -hw; d <= hw; d++) {
                boolean edge = Math.abs(d) == hw;
                if (!edge) {
                    at(s, alongX, a, layer, c + d, deck);
                    continue;
                }
                // the edge: a red band under the deck, the parapet above following the deck
                at(s, alongX, a, layer, c + d, RED_BLOCK);
                if (e > 0) {
                    at(s, alongX, a, layer - 1, c + d, Kit.upside(RED_STAIRS, rising ? toMinus : toPlus));
                }
                if (e == 0) {
                    at(s, alongX, a, layer + 1, c + d, RED_POST);
                    at(s, alongX, a, layer + 2, c + d, RED_POST);
                    at(s, alongX, a, layer + 3, c + d, Kit.slab(FINIAL.slab()));
                } else {
                    at(s, alongX, a, layer + 1, c + d, red);
                }
            }
        }
        return crest;
    }

    /**
     * A pine pruned into flat pads of needles (as in the gardens and as a bonsai grown large), standing on layer {@code y}:
     * a trunk of logs {@code height} (4 to 9) layers high that steps twice toward {@code lean}, a domed pad on its top, a
     * long low branch reaching back over its foot with a flat pad at its end; from 6 high a short branch to the side with a
     * small pad, from 7 high one more toward the lean. It reaches about four blocks around its foot; the pads are leaves that
     * never decay.
     */
    static void pine(Structure s, int x, int y, int z, int height, String lean) {
        int[] l = Furnish.step(lean);
        int[] side = Furnish.step(Furnish.left(lean));
        String along = l[0] != 0 ? "x" : "z";
        String across = side[0] != 0 ? "x" : "z";
        int b1 = y + Math.max(1, height / 3);
        int b2 = y + Math.max(2, 2 * height / 3);
        int tx = x;
        int tz = z;
        int lowX = x;
        int lowZ = z;
        for (int yy = y; yy < y + height; yy++) {
            put(s, tx, yy, tz, Kit.log(POST, "y"));
            if (yy == b1 || yy == b2) {
                if (yy == b1) {
                    lowX = tx;
                    lowZ = tz;
                }
                put(s, tx + l[0], yy, tz + l[1], Kit.log(POST, along));
                tx += l[0];
                tz += l[1];
            }
        }
        int top = y + height;
        boolean big = height >= 6;
        pad(s, tx, top, tz, big ? 5 : 3, true);
        // a long branch back over the foot, low; on taller pines a short one to the side, high, and one toward the lean
        int reach = big ? 3 : 2;
        for (int i = 1; i <= reach; i++) {
            put(s, lowX - l[0] * i, b1 + 1, lowZ - l[1] * i, Kit.log(POST, along));
        }
        pad(s, lowX - l[0] * (reach + 1), b1 + 1, lowZ - l[1] * (reach + 1), big ? 5 : 3, false);
        if (big) {
            for (int i = 1; i <= 2; i++) {
                put(s, tx + side[0] * i, b2, tz + side[1] * i, Kit.log(POST, across));
            }
            pad(s, tx + side[0] * 3, b2, tz + side[1] * 3, 3, false);
        }
        if (height >= 7) {
            put(s, tx + l[0], b2 + 1, tz + l[1], Kit.log(POST, along));
            pad(s, tx + l[0] * 2, b2 + 1, tz + l[1] * 2, 3, false);
        }
    }

    /**
     * A flat pad of needles around (x, z): a disc of leaves {@code width} wide at layer {@code y} and, with {@code dome}, a
     * disc two narrower on it.
     */
    private static void pad(Structure s, int x, int y, int z, int width, boolean dome) {
        int r = (width - 1) / 2;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (round(dx, dz, r) && !solid(s, x + dx, y, z + dz)) {
                    put(s, x + dx, y, z + dz, Kit.leaves("spruce"));
                }
                if (dome && r >= 1 && round(dx, dz, r - 1) && !solid(s, x + dx, y + 1, z + dz)) {
                    put(s, x + dx, y + 1, z + dz, Kit.leaves("spruce"));
                }
            }
        }
    }

    /** A number from 0 to 99 that a position always gives, to break the outline of leaves the same way every time. */
    private static int noise(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return Math.floorMod(h, 100);
    }

    /**
     * A blossoming cherry standing on layer {@code y}: a trunk of logs {@code height} (4 to 7) layers high that parts into
     * three branches spreading up and out, each carrying a cloud of pink leaves, and a cloud over the middle, so that the crown
     * is wide, low and broken at its edges, with a few blossoms hanging under it.
     */
    static void cherry(Structure s, int x, int y, int z, int height) {
        String log = "minecraft:cherry_log";
        int fork = y + height - 2;
        for (int yy = y; yy <= fork; yy++) {
            put(s, x, yy, z, Kit.log(log, "y"));
        }
        int[][] branches = {{2, 0, 1}, {-2, 1, 0}, {0, -2, 1}, {-1, -1, 2}};
        for (int[] b : branches) {
            int bx = x + Integer.signum(b[0]);
            int bz = z + Integer.signum(b[1]);
            put(s, bx, fork + 1, bz, Kit.log(log, b[0] != 0 ? "x" : "z"));
            int ex = x + b[0];
            int ez = z + b[1];
            put(s, ex, fork + 1 + b[2], ez, Kit.log(log, "y"));
            cloud(s, ex, fork + 2 + b[2], ez, 2);
        }
        put(s, x, fork + 1, z, Kit.log(log, "y"));
        cloud(s, x, fork + 3, z, 2);
    }

    /** A cloud of blossom around (x, y, z): a flattened ball of pink leaves of the given half-width, its edge broken. */
    private static void cloud(Structure s, int x, int y, int z, int r) {
        for (int dy = -1; dy <= 1; dy++) {
            double rr = dy == 0 ? r + 0.6 : dy < 0 ? r - 0.2 : r - 0.6;
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    boolean edge = d > rr - 1;
                    if (d <= rr && (!edge || noise(x + dx, y + dy, z + dz) < 70) && !solid(s, x + dx, y + dy, z + dz)) {
                        put(s, x + dx, y + dy, z + dz, Kit.leaves("cherry"));
                        if (dy == -1 && edge && noise(x + dx, y - 9, z + dz) < 25 && !solid(s, x + dx, y - 2, z + dz)) {
                            put(s, x + dx, y - 2, z + dz, Kit.leaves("cherry"));
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- furniture

    /**
     * Mats over the floor: the rectangle at layer {@code y} (the floor itself) of woven mats, with a border of the floor
     * planks when {@code border}.
     */
    static void tatami(Structure s, int x1, int z1, int x2, int z2, int y, boolean border) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                put(s, x, y, z, border && edge ? FLOOR : TATAMI);
            }
        }
    }

    /**
     * A low table on the floor: bottom slabs of timber over the rectangle at layer {@code y} (the feet), with cushions of
     * cloth (carpets) on the free floor cells around it.
     */
    static void lowTable(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                put(s, x, y, z, Kit.slab(FLOOR_SLAB));
            }
        }
        for (int x = x1; x <= x2; x++) {
            for (int z : new int[]{z1 - 1, z2 + 1}) {
                if (!solid(s, x, y, z) && solid(s, x, y - 1, z)) {
                    put(s, x, y, z, Kit.CLOTH_CARPET);
                }
            }
        }
        for (int z = z1; z <= z2; z++) {
            for (int x : new int[]{x1 - 1, x2 + 1}) {
                if (!solid(s, x, y, z) && solid(s, x, y - 1, z)) {
                    put(s, x, y, z, Kit.CLOTH_CARPET);
                }
            }
        }
    }

    /**
     * A bed on the floor for one (a futon): a real bed, so a home, white, its head the way of {@code facing}. Only in houses,
     * the rooms of mages, the rooms of inns and barracks.
     */
    static void futon(Structure s, int x, int y, int z, String facing) {
        Furnish.bed(s, x, y, z, facing, "white");
    }

    /**
     * An alcove (tokonoma) in the cell (x, y, z) before a wall, {@code y} the layer of the feet: a raised board of timber (an
     * upper slab) with a bowl (a flower pot) on it and a hanging scroll (a white banner) on the wall behind above it;
     * {@code facing} is where the alcove opens to the room, the wall behind is on the other side.
     */
    static void alcove(Structure s, int x, int y, int z, String facing) {
        put(s, x, y, z, Kit.topSlab(FLOOR_SLAB));
        put(s, x, y + 1, z, "minecraft:flower_pot");
        put(s, x, y + 2, z, Kit.wallBanner("white", facing));
    }

    /**
     * A household shrine on a wall, in the cells over (x, y, z) before it ({@code y} the layer of the feet): a shelf of
     * timber at {@code y + 2} with candles and a little roof of timber over it, a hanging of cloth under it; {@code facing}
     * is where it looks into the room. One can still walk under it.
     */
    static void shrine(Structure s, int x, int y, int z, String facing) {
        put(s, x, y + 2, z, Kit.topSlab(FLOOR_SLAB));
        put(s, x, y + 3, z, Furnish.candles(3));
        put(s, x, y + 4, z, Kit.upside(STAIRS, Furnish.opposite(facing)));
        put(s, x, y + 1, z, Kit.wallBanner("red", facing));
    }

    /**
     * A folding screen standing in a room from (x1, z1) to (x2, z2) along x or z at layer {@code y}: panels of paper two
     * high between posts of timber at its ends.
     */
    static void foldingScreen(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                boolean end = x == x1 && z == z1 || x == x2 && z == z2;
                put(s, x, y, z, end ? FENCE : PAPER);
                put(s, x, y + 1, z, end ? FENCE : PAPER);
            }
        }
    }

    /**
     * A chest of drawers (tansu) against a wall: {@code height} blocks of stripped timber (no inventory) with a lamp of paper
     * or a candle on top.
     */
    static void cabinet(Structure s, int x, int y, int z, int height) {
        for (int yy = y; yy < y + height; yy++) {
            Furnish.crate(s, x, yy, z, "spruce");
        }
        put(s, x, y + height, z, Furnish.candles(2));
    }

    /**
     * A paper lamp (andon) standing on the floor: a post of fence and a glowing block of paper on it.
     */
    static void paperLamp(Structure s, int x, int y, int z) {
        put(s, x, y, z, FENCE);
        put(s, x, y + 1, z, GLOW);
    }
}
