package io.github.sekerator.kingdomworld.generator;

/**
 * The style "gothic": the dark gothic of slate and needles. Its buildings stand tall and narrow, walls of dark ashlar pierced
 * by tall pointed windows, the weight of their vaults carried out to buttresses that step back as they rise and end in
 * pinnacles; over the aisles of great halls the buttresses fly as half arches to the high walls. Roofs are very steep, their
 * stone gables rise above the slates with a coping and a pinnacle on the apex; towers end in a pierced parapet with
 * pinnacles at the corners and a thin octagonal needle spire with small dormers (lucarnes) and an iron cross. Gargoyles
 * jut from the cornices, iron railings close the yards, and the windows glow purple, red and blue. Inside: ribbed vaults
 * with pale webs, checkered paving, tall pointed bookcases, iron candelabra and coronas of candles, chimneypieces under a
 * hood. Gloomy but beautiful, never a ruin.
 *
 * <p>The materials are the parts of a look (see {@link Kit}): the ashlar of walls, gables and buttresses is the wall; plinths
 * and footings the rough stone; sills, string courses, cornices, mantels and the ribs of vaults the trim; keystones,
 * rosettes and the bases of pinnacles the carved stone; roofs of halls and houses the roof; needle spires, the gablets of
 * great pinnacles and the roofs of lucarnes and oriels the spire; the panels of the upper floors, blind lancets, the
 * fields of wimpergs and the webs of vaults the plaster; banners and the covers of beds the cloth; floors, doors, pews and
 * shelves the timber. Fixed accents that never change, so that the dark stone of every look is drawn over with light lines:
 * the tracery (pale smooth stone with posts of andesite) of windows, roses, parapets, gable copings, flying arches and
 * pinnacles; stained glass (purple, red, blue) and clear glass in the lights of houses; iron (railings, crestings, crosses,
 * coronas); black stone of the gargoyles and candelabra with the skulls of their heads; checkered paving of calcite and
 * polished blackstone; soul lanterns outdoors.
 *
 * <p>This class holds the data and the shapes of the style; buildings are drawn with them by other classes. Coordinates as
 * everywhere: x from west to east, z from the back (north) to the front (south), y up. A shape that stands on a wall takes
 * the way its face looks ({@code facing}: north, south, west or east) and works in the plane of that wall: {@code u} runs
 * along the wall (to the left of one who stands with the back to it), {@code w} out of it (the wall itself is {@code w = 0},
 * mouldings and frames stand in front of it at {@code w = 1}). A shape draws only inside the box of the structure.
 */
final class Gothic {
    // ---------------------------------------------------------------- materials

    /** The ashlar of walls, gables, buttresses and piers (the wall of a look). */
    static final String WALL = Kit.WALL;
    static final String WALL_STAIRS = Kit.WALL_STAIRS;
    static final String WALL_SLAB = Kit.WALL_SLAB;
    /** Colonnettes, mullions and the posts of pinnacles: the wall of the look in its slender form. */
    static final String WALL_FENCE = Kit.WALL_FENCE;
    /** Plinths, footings and the lowest course of every wall (rough stone). */
    static final String ROUGH = Kit.ROUGH;
    static final String ROUGH_STAIRS = Kit.ROUGH_STAIRS;
    static final String ROUGH_SLAB = Kit.ROUGH_SLAB;
    /** Sills, hoods, string courses, copings, cornices and ribs (trim). */
    static final String TRIM = Kit.TRIM;
    static final String TRIM_STAIRS = Kit.TRIM_STAIRS;
    static final String TRIM_SLAB = Kit.TRIM_SLAB;
    /** Keystones, kneelers, the bases of pinnacles (carved stone). */
    static final String CARVED = Kit.CARVED;
    /** The steep roofs of halls and houses (the roof of a look). */
    static final Kit.Roof ROOF = Kit.SHINGLE;
    /** Needle spires, the gablets of pinnacles and the roofs of lucarnes (the spires of a look). */
    static final Kit.Roof SPIRE = Kit.SLATE;
    /** The panels of upper floors, blind niches in gables and the webs of vaults (plaster). */
    static final String PLASTER = Kit.PLASTER;
    /** Cloth of the kingdom: banners, the covers of beds. */
    static final String CLOTH = Kit.CLOTH;
    static final String CLOTH_CARPET = Kit.CLOTH_CARPET;

    /** The wood of the place for {@link Furnish} (timber: spruce becomes the wood of the look). */
    static final String WOOD = "spruce";
    static final String PLANKS = "minecraft:spruce_planks";
    static final String WOOD_STAIRS = "minecraft:spruce_stairs";
    static final String WOOD_SLAB = "minecraft:spruce_slab";
    static final String WOOD_FENCE = "minecraft:spruce_fence";
    static final String DOOR = "minecraft:spruce_door";
    /** Beams of ceilings and belfries (timber). */
    static final String BEAM = "minecraft:spruce_log";
    /** Panels of furniture: an oak trapdoor (timber; never the spruce trapdoor, which is the shutter of a look). */
    static final String PANEL = "minecraft:oak_trapdoor";

    /**
     * Tracery: the slender pale stonework of windows, roses, parapets, copings, flying arches and pinnacles, which stands out
     * of the dark walls whatever the look (fixed): smooth stone, its stairs and slabs, and posts of andesite.
     */
    static final String TRACERY = "minecraft:stone";
    static final String TRACERY_STAIRS = "minecraft:stone_stairs";
    static final String TRACERY_SLAB = "minecraft:stone_slab";
    static final String TRACERY_WALL = "minecraft:andesite_wall";
    /** Clear glass in the lights of houses (fixed). */
    static final String GLASS = Kit.GLASS;
    /** Stained glass, the colour of the style (fixed). */
    static final String VIOLET = Kit.stainedPane("purple");
    static final String CRIMSON = Kit.stainedPane("red");
    static final String AZURE = Kit.stainedPane("blue");
    /** Iron bars: railings, crosses, the hubs of coronas (fixed). */
    static final String IRON = Kit.BARS;
    /** The body of a gargoyle: black stone stairs (fixed). */
    static final String GARGOYLE_STAIRS = "minecraft:polished_blackstone_stairs";
    /** The head of a gargoyle: a black skull on the end of its body (fixed). */
    static final String GARGOYLE_HEAD = "minecraft:wither_skeleton_wall_skull";
    /** The stand of a candelabrum: a black stone post (fixed). */
    static final String STAND = "minecraft:polished_blackstone_wall";
    /** Checkered paving of halls: pale and black stone (fixed). */
    static final String PAVING_LIGHT = "minecraft:calcite";
    static final String PAVING_DARK = "minecraft:polished_blackstone";
    /** A cold lamp for outdoors (fixed). */
    static final String SOUL_LANTERN = "minecraft:soul_lantern[hanging=false,waterlogged=false]";
    static final String SOUL_LANTERN_HANGING = "minecraft:soul_lantern[hanging=true,waterlogged=false]";

    /** Kinds of the end of a steep roof (see {@link #steepRoof}). */
    static final int OPEN = 0;
    static final int PLAIN = 1;
    static final int PARAPET = 2;

    private Gothic() {
    }

    // ---------------------------------------------------------------- helpers

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static boolean solid(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && s.solid(x, y, z);
    }

    private static void putFree(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z) && !s.solid(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static String stairs(String block, String facing, String shape) {
        return Kit.stairs(block, facing, shape);
    }

    private static void at(Structure s, boolean alongX, int a, int y, int c, String state) {
        put(s, alongX ? a : c, y, alongX ? c : a, state);
    }

    /**
     * The plane of a wall that looks the way of {@code facing}: {@code u} along the wall to the left of one who stands with
     * the back to it, {@code v} up, {@code w} out of the wall.
     */
    private static final class Face {
        final int x;
        final int y;
        final int z;
        final int ax;
        final int az;
        final int ox;
        final int oz;
        final String out;
        final String in;
        final String plus;
        final String minus;

        Face(int x, int y, int z, String facing) {
            this.x = x;
            this.y = y;
            this.z = z;
            int[] o = Furnish.step(facing);
            plus = Furnish.left(facing);
            int[] a = Furnish.step(plus);
            ax = a[0];
            az = a[1];
            ox = o[0];
            oz = o[1];
            out = facing;
            in = Furnish.opposite(facing);
            minus = Furnish.opposite(plus);
        }

        int px(int u, int w) {
            return x + ax * u + ox * w;
        }

        int pz(int u, int w) {
            return z + az * u + oz * w;
        }

        void put(Structure s, int u, int v, int w, String state) {
            Gothic.put(s, px(u, w), y + v, pz(u, w), state);
        }

        void putFree(Structure s, int u, int v, int w, String state) {
            Gothic.putFree(s, px(u, w), y + v, pz(u, w), state);
        }
    }

    // ---------------------------------------------------------------- plans

    /**
     * Whether a column lies in an octagon of the given half-size around its middle (the corners cut so that the eight sides
     * are nearly equal).
     */
    static boolean inOctagon(int dx, int dz, int r) {
        if (r < 0) {
            return false;
        }
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        return ax <= r && az <= r && ax + az <= (r * 141 + 50) / 100;
    }

    private static boolean onOctagonRim(int dx, int dz, int r) {
        return inOctagon(dx, dz, r) && (!inOctagon(dx + 1, dz, r) || !inOctagon(dx - 1, dz, r) || !inOctagon(dx, dz + 1, r)
                || !inOctagon(dx, dz - 1, r));
    }

    /**
     * A stair on the rim of an octagon with its high side to the middle; a corner stair where the rim turns.
     */
    private static String octagonSlope(String block, int dx, int dz, int r) {
        boolean outX = dx != 0 && !inOctagon(dx + Integer.signum(dx), dz, r);
        boolean outZ = dz != 0 && !inOctagon(dx, dz + Integer.signum(dz), r);
        if (outX && outZ) {
            return stairs(block, dz > 0 ? "north" : "south", dz > 0 == dx > 0 ? "outer_left" : "outer_right");
        }
        if (outX || !outZ && Math.abs(dx) >= Math.abs(dz)) {
            return Kit.stairs(block, dx > 0 ? "west" : "east");
        }
        return Kit.stairs(block, dz > 0 ? "north" : "south");
    }

    // ---------------------------------------------------------------- arches and windows

    /**
     * A pointed arch cut through a wall: an opening of {@code width} cells (odd widths centred on (x, z), even ones with
     * the extra cell on the {@code u} side) and {@code height} layers from {@code y}, closed above by upside-down stairs that
     * step in from both jambs, one cell a layer, until they meet: two stairs meeting in a point for even widths, a hanging
     * slab in the middle for odd ones. The arch is cut through {@code depth} planes, from the face inwards, for thick walls
     * and passages; the stairs and the slab are of the given family (trim or wall).
     *
     * @return the layer above the top of the arch
     */
    static int pointedArch(Structure s, int x, int y, int z, String facing, int width, int height, int depth, String stairs, String slab) {
        Face f = new Face(x, y, z, facing);
        int lo = -(width - 1) / 2;
        int hi = lo + width - 1;
        int top = y + height;
        for (int w = 0; w > -depth; w--) {
            for (int v = 0; v < height; v++) {
                for (int u = lo; u <= hi; u++) {
                    f.put(s, u, v, w, Structure.AIR);
                }
            }
            for (int k = 0; ; k++) {
                int l = lo + k;
                int r = hi - k;
                int v = height + k;
                if (l < r) {
                    f.put(s, l, v, w, Kit.upside(stairs, f.minus));
                    f.put(s, r, v, w, Kit.upside(stairs, f.plus));
                    for (int u = l + 1; u < r; u++) {
                        f.put(s, u, v, w, Structure.AIR);
                    }
                    top = Math.max(top, y + v + 1);
                } else {
                    if (l == r) {
                        f.put(s, l, v, w, Kit.topSlab(slab));
                        top = Math.max(top, y + v + 1);
                    }
                    break;
                }
            }
        }
        return top;
    }

    /**
     * A lancet window in a wall looking the way of {@code facing}, its lowest middle pane at (x, y, z) in the wall. The
     * lights fill {@code width} columns (odd: 1, 3 or 5) and {@code height} layers with {@code glass}; above them the head of
     * the window, of {@code head} glass, narrows by a column on each side a layer to a point. In front of the wall
     * ({@code w = 1}) stands its frame of tracery: a sill of trim stairs under it, slender colonnettes at both sides and
     * mullions between the lights (a light on every second column: 3 wide is a two-light window, 5 a three-light one), and
     * over the head a pointed arch of hanging stairs under a hood of stairs that follows it to an apex slab. So a light
     * column shows a pointed top, and two lights with the opening over their mullion read as plate tracery. The window takes
     * {@code width + 2} columns and the layers from {@code y - 1} to {@code y + height + (width - 1) / 2 + 1}; windows two
     * columns apart share a colonnette (set the shared cell of their hood row, {@code y + height} at {@code w = 1}, to
     * {@link #TRACERY}).
     */
    static void lancet(Structure s, int x, int y, int z, String facing, int width, int height, String glass, String head) {
        frame(s, new Face(x, y, z, facing), width, height, glass, head, true);
    }

    private static void frame(Structure s, Face f, int width, int height, String glass, String head, boolean sill) {
        int hw = (width - 1) / 2;
        for (int v = 0; v < height; v++) {
            for (int u = -hw; u <= hw; u++) {
                f.put(s, u, v, 0, glass);
            }
        }
        for (int k = 0; k <= hw; k++) {
            for (int u = -(hw - k); u <= hw - k; u++) {
                f.put(s, u, height + k, 0, head);
            }
        }
        if (sill) {
            for (int u = -hw - 1; u <= hw + 1; u++) {
                f.put(s, u, -1, 1, Kit.upside(TRIM_STAIRS, f.in));
            }
        }
        for (int v = 0; v < height; v++) {
            f.put(s, -hw - 1, v, 1, TRACERY_WALL);
            f.put(s, hw + 1, v, 1, TRACERY_WALL);
            for (int u = -hw + 1; u <= hw - 1; u += 2) {
                f.put(s, u, v, 1, TRACERY_WALL);
            }
        }
        for (int k = 0; k <= hw; k++) {
            int v = height + k;
            int e = hw - k;
            f.put(s, -(e + 1), v, 1, Kit.stairs(TRACERY_STAIRS, f.plus));
            f.put(s, e + 1, v, 1, Kit.stairs(TRACERY_STAIRS, f.minus));
            if (e > 0) {
                f.put(s, -e, v, 1, Kit.upside(TRACERY_STAIRS, f.minus));
                f.put(s, e, v, 1, Kit.upside(TRACERY_STAIRS, f.plus));
                for (int u = -e + 1; u < e; u++) {
                    f.put(s, u, v, 1, Structure.AIR);
                }
            } else {
                f.put(s, 0, v, 1, Kit.topSlab(TRACERY_SLAB));
            }
        }
        f.put(s, 0, height + hw + 1, 1, Kit.slab(TRACERY_SLAB));
    }

    /**
     * A blind lancet: the frame of {@link #lancet} without a sill over a panel of plaster instead of glass, for gables,
     * towers and the piers of fronts, where a window would look into nothing.
     */
    static void blindLancet(Structure s, int x, int y, int z, String facing, int width, int height) {
        frame(s, new Face(x, y, z, facing), width, height, PLASTER, PLASTER, false);
    }

    /**
     * A pointed doorway in a wall looking the way of {@code facing}: for {@code width} 1 one door, for 3 two doors on both
     * sides of a carved trumeau, their lower halves at (x, y, z) and beside it, under a transom of red glass and the frame of
     * a lancet (colonnettes, the pointed arch and its hood, see {@link #lancet}) without a sill. A grand doorway also gets a
     * wimperg: a steep pointed gable over the hood, two layers up for every column in, edged with tracery stairs over a field
     * of plaster with a carved rosette in it and a pinnacle on its apex, flanked by two piers of wall with pinnacles; it
     * takes two more columns on each side ({@code width + 4} in all, at {@code w = 1}) and the layers from {@code y - 1} to
     * {@code y + width + 7}. Doors are of the timber; the threshold under them is the caller's.
     */
    static void portal(Structure s, int x, int y, int z, String facing, int width, boolean grand) {
        Face f = new Face(x, y, z, facing);
        int hw = (width - 1) / 2;
        int height = 2;
        frame(s, f, width, height, CRIMSON, CRIMSON, false);
        if (hw == 0) {
            s.door(f.px(0, 0), y, f.pz(0, 0), DOOR, f.in, "left");
        } else {
            for (int u = -hw; u <= hw; u++) {
                if (u == 0) {
                    f.put(s, 0, 0, 0, CARVED);
                    f.put(s, 0, 1, 0, CARVED);
                } else {
                    s.door(f.px(u, 0), y, f.pz(u, 0), DOOR, f.in, u < 0 ? "left" : "right");
                }
            }
        }
        if (!grand) {
            return;
        }
        // The wimperg: a steep gable over the hood, two layers up for every column in, a panel of plaster in it.
        int apexHood = height + hw + 1;
        for (int j = 0; j <= hw + 1; j++) {
            int au = hw + 1 - j;
            int cop = height + 2 * j + 1;
            int hood = au == 0 ? apexHood : height + hw - au + 1;
            for (int v = hood + 1; v < cop; v++) {
                f.put(s, -au, v, 1, PLASTER);
                f.put(s, au, v, 1, PLASTER);
            }
            if (au > 0) {
                f.put(s, -au, cop, 1, Kit.stairs(TRACERY_STAIRS, f.plus));
                f.put(s, au, cop, 1, Kit.stairs(TRACERY_STAIRS, f.minus));
            } else {
                f.put(s, 0, apexHood, 1, TRACERY);
                f.put(s, 0, cop, 1, CARVED);
                pinnacle(s, f.px(0, 1), y + cop + 1, f.pz(0, 1), 3);
            }
        }
        f.put(s, 0, apexHood + 2, 1, CARVED);
        // The piers at its sides, each with a pinnacle.
        for (int side = -1; side <= 1; side += 2) {
            int u = side * (hw + 2);
            for (int v = -1; v <= height + 1; v++) {
                f.put(s, u, v, 1, v == -1 ? ROUGH : WALL);
            }
            pinnacle(s, f.px(u, 1), y + height + 2, f.pz(u, 1), 3);
        }
    }

    /**
     * A rose window in a wall looking the way of {@code facing}, its middle at (x, y, z): a round of the given odd diameter
     * (5, 7, 9) in the wall, framed by a ring of tracery, filled with stained glass in a wheel: a red heart, a ring of blue,
     * petals of purple and red between spokes of blue; in front of the wall ({@code w = 1}) a hood of hanging tracery stairs
     * follows the upper half of the ring.
     */
    static void rose(Structure s, int x, int y, int z, String facing, int diameter) {
        Face f = new Face(x, y, z, facing);
        int r = (diameter - 1) / 2;
        for (int dv = -r; dv <= r; dv++) {
            for (int du = -r; du <= r; du++) {
                if (!Kit.inDisc(du, dv, diameter)) {
                    continue;
                }
                if (Kit.onRim(du, dv, diameter)) {
                    f.put(s, du, dv, 0, TRACERY);
                    if (dv >= 0) {
                        f.put(s, du, dv, 1, Kit.upside(TRACERY_STAIRS, f.in));
                    }
                } else {
                    f.put(s, du, dv, 0, roseGlass(du, dv));
                }
            }
        }
    }

    private static String roseGlass(int du, int dv) {
        int d2 = du * du + dv * dv;
        if (d2 == 0) {
            return CRIMSON;
        }
        if (d2 <= 2) {
            return AZURE;
        }
        if (du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv)) {
            return AZURE;
        }
        return Math.abs(du) > Math.abs(dv) == (du > 0 == dv > 0) ? VIOLET : CRIMSON;
    }

    // ---------------------------------------------------------------- buttresses and pinnacles

    /**
     * A pinnacle standing on layer {@code y} at (x, z): a carved base and a slender post of tracery over it, {@code height}
     * layers in all, a pale spike over the dark stone.
     *
     * @return the layer above its top
     */
    static int pinnacle(Structure s, int x, int y, int z, int height) {
        put(s, x, y, z, CARVED);
        for (int v = 1; v < height; v++) {
            put(s, x, y + v, z, TRACERY_WALL);
        }
        return y + height;
    }

    /**
     * A great pinnacle for the corners of towers and the piers of great halls: a carved base, over it a block of the spire
     * ringed by four spire stairs (a little spire three wide, the stairs only where nothing stands), and a post of tracery
     * over that, {@code height} layers in all (5 is usual).
     *
     * @return the layer above its top
     */
    static int greatPinnacle(Structure s, int x, int y, int z, int height) {
        put(s, x, y, z, CARVED);
        for (String facing : new String[]{"north", "south", "west", "east"}) {
            int[] d = Furnish.step(facing);
            putFree(s, x + d[0], y + 1, z + d[1], Kit.stairs(SPIRE.stairs(), Furnish.opposite(facing)));
        }
        put(s, x, y + 1, z, SPIRE.block());
        for (int v = 2; v < height; v++) {
            put(s, x, y + v, z, TRACERY_WALL);
        }
        return y + height;
    }

    /**
     * A buttress against a wall looking the way of {@code facing}: (x, z) is a block of the face of the wall, the buttress
     * one column wide stands before it from layer {@code y}, {@code depth} blocks deep at its foot and stepping back by one
     * block with a sloping weathering of wall stairs every {@code height / (depth + 1)} layers, on a plinth of rough stone
     * with a sloping footing. Its innermost part rises {@code height} layers; with {@code pinnacle} it ends in a pinnacle
     * (four layers more), otherwise in a weathering.
     *
     * @return the layer above its top
     */
    static int buttress(Structure s, int x, int y, int z, String facing, int height, int depth, boolean pinnacle) {
        Face f = new Face(x, y, z, facing);
        int step = Math.max(2, height / (depth + 1));
        for (int w = 1; w <= depth; w++) {
            int partTop = height - 1 - (w - 1) * step;
            for (int v = 0; v < partTop; v++) {
                f.put(s, 0, v, w, v == 0 ? ROUGH : WALL);
            }
            if (w == 1 && pinnacle) {
                f.put(s, 0, partTop, w, WALL);
            } else {
                f.put(s, 0, partTop, w, Kit.stairs(WALL_STAIRS, f.in));
            }
        }
        f.put(s, 0, 0, depth + 1, Kit.stairs(ROUGH_STAIRS, f.in));
        if (pinnacle) {
            return pinnacle(s, f.px(0, 1), y + height, f.pz(0, 1), 4);
        }
        return y + height;
    }

    /**
     * A flying buttress: a half arch that springs from a free pier and leans against a high wall looking the way of
     * {@code facing}, over the roof of an aisle. (x, z) is a block of the face of the high wall and {@code y} the layer where
     * the arch meets it; the arch descends one layer for every block outwards (a coping of tracery stairs, a pale line, over
     * a soffit of hanging wall stairs) to the pier, {@code reach} blocks out of the wall. The pier stands from layer
     * {@code ground} up to {@code y - reach + 1} with a stepped base and a sloping footing further out, and carries a
     * pinnacle four layers high; with {@code gargoyle} a gargoyle juts from the top of the pier. It takes {@code reach + 2}
     * blocks out of the wall in one column; the roof under the arch must stay below {@code y - k} at {@code k} blocks out.
     */
    static void flyingButtress(Structure s, int x, int y, int z, String facing, int reach, int ground, boolean gargoyle) {
        Face f = new Face(x, y, z, facing);
        for (int k = 1; k < reach; k++) {
            f.put(s, 0, -(k - 1), k, Kit.stairs(TRACERY_STAIRS, f.in));
            f.put(s, 0, -k, k, Kit.upside(WALL_STAIRS, f.out));
        }
        int pierTop = y - reach + 1;
        for (int yy = ground; yy <= pierTop; yy++) {
            put(s, f.px(0, reach), yy, f.pz(0, reach), yy == ground ? ROUGH : WALL);
        }
        int base = ground + (pierTop - ground) / 2;
        for (int yy = ground; yy < base; yy++) {
            put(s, f.px(0, reach + 1), yy, f.pz(0, reach + 1), yy == ground ? ROUGH : WALL);
        }
        put(s, f.px(0, reach + 1), base, f.pz(0, reach + 1), Kit.stairs(WALL_STAIRS, f.in));
        put(s, f.px(0, reach + 2), ground, f.pz(0, reach + 2), Kit.stairs(ROUGH_STAIRS, f.in));
        pinnacle(s, f.px(0, reach), pierTop + 1, f.pz(0, reach), 4);
        if (gargoyle) {
            gargoyle(s, f.px(0, reach + 1), pierTop, f.pz(0, reach + 1), facing);
        }
    }

    /**
     * A gargoyle jutting out of a wall the way of {@code facing}: its body, a hanging stair of black stone, in the cell
     * (x, y, z) before the wall, and its head, a black skull, one cell further out.
     */
    static void gargoyle(Structure s, int x, int y, int z, String facing) {
        int[] o = Furnish.step(facing);
        put(s, x, y, z, Kit.upside(GARGOYLE_STAIRS, Furnish.opposite(facing)));
        put(s, x + o[0], y, z + o[1], GARGOYLE_HEAD + "[facing=" + facing + "]");
    }

    // ---------------------------------------------------------------- parapets, cornices, railings

    /**
     * A cornice: a row of hanging trim stairs before a wall looking the way of {@code facing}, in the cells from (x1, z1)
     * to (x2, z2) at layer {@code y} (one block out of the wall).
     */
    static void cornice(Structure s, int x1, int z1, int x2, int z2, int y, String facing) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                put(s, x, y, z, Kit.upside(TRIM_STAIRS, Furnish.opposite(facing)));
            }
        }
    }

    /**
     * A tracery parapet along a straight line from (x1, z1) to (x2, z2), standing on layer {@code y}: an arcade of pointed
     * openings two cells wide between slender posts every third cell (a single opening under a hanging slab where the line
     * ends short), the heads of the arches hanging stairs meeting in a point, and a coping of slabs over all, all of
     * tracery; three layers high ({@code y} to {@code y + 2}), so that those who walk behind it cannot fall. Its ends are
     * posts; it stands on the wall line itself and owns its cells (the openings are cleared).
     */
    static void parapet(Structure s, int x1, int z1, int x2, int z2, int y) {
        boolean alongX = z1 == z2;
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int c = alongX ? z1 : x1;
        String plus = alongX ? "east" : "south";
        String minus = alongX ? "west" : "north";
        for (int a = a1; a <= a2; a++) {
            int i = a - a1;
            boolean post = i % 3 == 0 || a == a2;
            if (post) {
                at(s, alongX, a, y, c, TRACERY_WALL);
                at(s, alongX, a, y + 1, c, TRACERY);
            } else {
                at(s, alongX, a, y, c, Structure.AIR);
                boolean left = i % 3 == 1;
                if (left && a + 1 == a2) {
                    at(s, alongX, a, y + 1, c, Kit.topSlab(TRACERY_SLAB));
                } else {
                    at(s, alongX, a, y + 1, c, Kit.upside(TRACERY_STAIRS, left ? minus : plus));
                }
            }
            at(s, alongX, a, y + 2, c, Kit.slab(TRACERY_SLAB));
        }
    }

    /**
     * A tracery parapet around a rectangle whose corners are (x1, z1) and (x2, z2), standing on layer {@code y}.
     */
    static void parapetRing(Structure s, int x1, int z1, int x2, int z2, int y) {
        parapet(s, x1, z1, x2, z1, y);
        parapet(s, x1, z2, x2, z2, y);
        parapet(s, x1, z1, x1, z2, y);
        parapet(s, x2, z1, x2, z2, y);
    }

    /**
     * The crown of a square tower whose walls stand on the lines x1, x2, z1 and z2 up to layer {@code y - 1}: a cornice of
     * hanging trim stairs around it at {@code y - 1} one block out, a tracery parapet on the walls from {@code y} to
     * {@code y + 2}, a great pinnacle (see {@link #greatPinnacle}) on each corner rising from the coping to {@code y + 6},
     * and with {@code gargoyles} a gargoyle under each corner looking to the front or the back (its head two blocks out).
     *
     * @return the layer above the coping (where a spire inside the parapet may begin to show)
     */
    static int crown(Structure s, int x1, int z1, int x2, int z2, int y, boolean gargoyles) {
        cornice(s, x1 + 1, z1 - 1, x2 - 1, z1 - 1, y - 1, "north");
        cornice(s, x1 + 1, z2 + 1, x2 - 1, z2 + 1, y - 1, "south");
        cornice(s, x1 - 1, z1 + 1, x1 - 1, z2 - 1, y - 1, "west");
        cornice(s, x2 + 1, z1 + 1, x2 + 1, z2 - 1, y - 1, "east");
        parapetRing(s, x1, z1, x2, z2, y);
        for (int x : new int[]{x1, x2}) {
            for (int z : new int[]{z1, z2}) {
                greatPinnacle(s, x, y + 2, z, 5);
                if (gargoyles) {
                    gargoyle(s, x, y - 1, z == z1 ? z1 - 1 : z2 + 1, z == z1 ? "north" : "south");
                }
            }
        }
        return y + 3;
    }

    /**
     * An iron railing along a straight line from (x1, z1) to (x2, z2), standing on layer {@code y}: iron bars
     * {@code height} layers high with a spike on every second bar, and with {@code posts} a stone post every fourth cell and
     * at the ends, each with a soul lantern on it. Without posts it is the railing of a balcony or the cresting of a ridge.
     */
    static void ironFence(Structure s, int x1, int z1, int x2, int z2, int y, int height, boolean posts) {
        boolean alongX = z1 == z2;
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int c = alongX ? z1 : x1;
        for (int a = a1; a <= a2; a++) {
            boolean post = posts && ((a - a1) % 4 == 0 || a == a2);
            if (post) {
                at(s, alongX, a, y, c, WALL);
                for (int v = 1; v < height; v++) {
                    at(s, alongX, a, y + v, c, WALL_FENCE);
                }
                at(s, alongX, a, y + height, c, SOUL_LANTERN);
            } else {
                for (int v = 0; v < height; v++) {
                    at(s, alongX, a, y + v, c, IRON);
                }
                if ((a - a1) % 2 == 1) {
                    at(s, alongX, a, y + height, c, IRON);
                }
            }
        }
    }

    /**
     * An iron cresting along the ridge of a steep roof from (x1, z1) to (x2, z2): bars with spikes in place of the ridge
     * slabs, at the layer {@link #steepRoof} returns (only for a roof with a middle row, an odd span).
     */
    static void cresting(Structure s, int x1, int z1, int x2, int z2, int y) {
        ironFence(s, x1, z1, x2, z2, y, 1, false);
    }

    /**
     * An oriel: a bay window jutting from a wall looking the way of {@code facing} on a floor at layer {@code y}, opening
     * from the room through the wall cell (x, z) above that floor: a nook one cell deep before the wall, closed by glass on
     * its three sides between corner posts of tracery, {@code height} layers high, on a base of trim over a corbel, under a
     * small steep roof of the spire with a slab on its point. It takes three columns and two cells before the wall, from
     * {@code y - 1} to {@code y + height + 2}.
     */
    static void oriel(Structure s, int x, int y, int z, String facing, int height) {
        Face f = new Face(x, y, z, facing);
        for (int v = 1; v <= height; v++) {
            f.put(s, 0, v, 0, Structure.AIR);
            f.put(s, -1, v, 1, GLASS);
            f.put(s, 1, v, 1, GLASS);
            f.put(s, 0, v, 2, GLASS);
            f.put(s, 0, v, 1, Structure.AIR);
            f.put(s, -1, v, 2, TRACERY_WALL);
            f.put(s, 1, v, 2, TRACERY_WALL);
        }
        f.put(s, 0, 0, 1, PLANKS);
        for (int u = -1; u <= 1; u++) {
            if (u != 0) {
                f.put(s, u, 0, 1, TRIM);
            }
            f.put(s, u, 0, 2, Kit.upside(TRIM_STAIRS, f.in));
        }
        f.put(s, 0, -1, 1, Kit.upside(TRIM_STAIRS, f.in));
        int r = height + 1;
        f.put(s, -1, r, 1, Kit.stairs(SPIRE.stairs(), f.plus));
        f.put(s, 1, r, 1, Kit.stairs(SPIRE.stairs(), f.minus));
        f.put(s, -1, r, 2, stairs(SPIRE.stairs(), f.in, "outer_right"));
        f.put(s, 1, r, 2, stairs(SPIRE.stairs(), f.in, "outer_left"));
        f.put(s, 0, r, 2, Kit.stairs(SPIRE.stairs(), f.in));
        f.put(s, 0, r, 1, SPIRE.block());
        f.put(s, 0, r + 1, 1, Kit.slab(SPIRE.slab()));
    }

    // ---------------------------------------------------------------- roofs

    /**
     * A very steep gable roof over walls on the lines x1, x2, z1 and z2: rows of roof stairs from eaves that overhang the
     * long walls by one block at layer {@code y} (the walls stand up to {@code y - 1}), each row rising {@code pitch} layers
     * (2 is steep, 3 very steep: a stair on full blocks), closed on the ridge by a row of slabs when the walls stand an odd
     * number of cells apart (an odd footprint), at layer {@code y + ((c2 - c1) / 2 + 1) * pitch} where c1, c2 are the long
     * walls. Its two ends ({@code end1} at the low end, {@code end2} at the high one) are each {@link #PARAPET}: a stone
     * gable on the end wall rising one block above the slope under a coping of tracery stairs, a small pinnacle at each
     * foot and a pinnacle three high on the apex, the roof between the gables; {@link #PLAIN}: the roof overhangs the end
     * wall by a block and the gable under it is filled with wall; {@link #OPEN}: the roof stops on the end line, for an end
     * that leans against a higher wall drawn later. The roof is a shell (the space under it is free for an attic or a vault).
     *
     * @param alongX whether the ridge runs along x (gables to the west and the east) or along z (gables to the back and to
     *               the street)
     * @return the layer of the ridge slab
     */
    static int steepRoof(Structure s, int x1, int z1, int x2, int z2, int y, boolean alongX, int pitch, int end1, int end2) {
        int a1 = alongX ? x1 : z1;
        int a2 = alongX ? x2 : z2;
        int c1 = alongX ? z1 : x1;
        int c2 = alongX ? z2 : x2;
        String up = alongX ? "south" : "east";
        String down = alongX ? "north" : "west";
        int from = end1 == PARAPET ? a1 + 1 : end1 == PLAIN ? a1 - 1 : a1;
        int to = end2 == PARAPET ? a2 - 1 : end2 == PLAIN ? a2 + 1 : a2;
        int r = 0;
        int top = y;
        for (; c1 - 1 + r < c2 + 1 - r; r++) {
            int lo = c1 - 1 + r;
            int hi = c2 + 1 - r;
            int h = y + r * pitch;
            for (int a = from; a <= to; a++) {
                if (r > 0) {
                    for (int hh = h - pitch; hh < h; hh++) {
                        at(s, alongX, a, hh, lo, ROOF.block());
                        at(s, alongX, a, hh, hi, ROOF.block());
                    }
                }
                at(s, alongX, a, h, lo, Kit.stairs(ROOF.stairs(), up));
                at(s, alongX, a, h, hi, Kit.stairs(ROOF.stairs(), down));
            }
            top = h;
        }
        int mid = c1 - 1 + r;
        if (mid == c2 + 1 - r) {
            int h = y + r * pitch;
            for (int a = from; a <= to; a++) {
                for (int hh = h - pitch; hh < h; hh++) {
                    at(s, alongX, a, hh, mid, ROOF.block());
                }
                at(s, alongX, a, h, mid, Kit.slab(ROOF.slab()));
            }
            top = h;
        }
        if (end1 != OPEN) {
            gableEnd(s, alongX, a1, c1, c2, y, pitch, end1 == PARAPET);
        }
        if (end2 != OPEN) {
            gableEnd(s, alongX, a2, c1, c2, y, pitch, end2 == PARAPET);
        }
        return top;
    }

    /**
     * The gable wall of a steep roof on the end line {@code a}, between the long walls {@code c1} and {@code c2}.
     */
    private static void gableEnd(Structure s, boolean alongX, int a, int c1, int c2, int y, int pitch, boolean parapet) {
        String up = alongX ? "south" : "east";
        String down = alongX ? "north" : "west";
        int mid2 = c1 + c2;
        for (int c = c1; c <= c2; c++) {
            int r = Math.min(c - (c1 - 1), (c2 + 1) - c);
            int h = y + r * pitch;
            if (parapet) {
                for (int hh = y; hh <= h; hh++) {
                    at(s, alongX, a, hh, c, WALL);
                }
                if (2 * c == mid2) {
                    pinnacle(s, alongX ? a : c, h + 1, alongX ? c : a, 3);
                } else if (c == c1 || c == c2) {
                    pinnacle(s, alongX ? a : c, h + 1, alongX ? c : a, 2);
                } else {
                    at(s, alongX, a, h + 1, c, Kit.stairs(TRACERY_STAIRS, 2 * c < mid2 ? up : down));
                }
            } else {
                for (int hh = y; hh < h - pitch; hh++) {
                    at(s, alongX, a, hh, c, WALL);
                }
            }
        }
    }

    /**
     * A lean-to roof over the cells x1..x2, z1..z2, rising toward {@code rising} (north, south, west or east) one row of
     * roof stairs every block, the lowest row at layer {@code y}, each row {@code pitch} layers above the last with full
     * roof blocks under its stair. For aisles against a high wall and for sheds.
     *
     * @return the layer of the highest row
     */
    static int shedRoof(Structure s, int x1, int z1, int x2, int z2, int y, String rising, int pitch) {
        int[] d = Furnish.step(rising);
        boolean acrossX = d[0] != 0;
        int c1 = acrossX ? Math.min(x1, x2) : Math.min(z1, z2);
        int c2 = acrossX ? Math.max(x1, x2) : Math.max(z1, z2);
        int a1 = acrossX ? Math.min(z1, z2) : Math.min(x1, x2);
        int a2 = acrossX ? Math.max(z1, z2) : Math.max(x1, x2);
        int h = y;
        for (int i = 0; i <= c2 - c1; i++) {
            int c = (acrossX ? d[0] : d[1]) > 0 ? c1 + i : c2 - i;
            h = y + i * pitch;
            for (int a = a1; a <= a2; a++) {
                for (int hh = Math.max(y, h - pitch + 1); hh < h; hh++) {
                    at(s, !acrossX, a, hh, c, ROOF.block());
                }
                at(s, !acrossX, a, h, c, Kit.stairs(ROOF.stairs(), rising));
            }
        }
        return h;
    }

    /**
     * A dormer on a slope of a steep roof drawn by {@link #steepRoof} with the same walls, eaves and pitch: a window of two
     * panes of glass in row {@code row} of the slope (2 or more; row 1 stands over the wall) at {@code at} along the ridge,
     * on the high side ({@code high}: south or east) or the low one, between cheeks of roof blocks under a small gable roof
     * of its own with a slab on its point; the attic behind it is opened to the window.
     */
    static void dormer(Structure s, int x1, int z1, int x2, int z2, int y, boolean alongX, int pitch, int at, boolean high, int row) {
        int c1 = alongX ? z1 : x1;
        int c2 = alongX ? z2 : x2;
        int c = high ? c2 + 1 - row : c1 - 1 + row;
        int h = y + row * pitch;
        String facing = alongX ? (high ? "south" : "north") : (high ? "east" : "west");
        Face f = new Face(alongX ? at : c, 0, alongX ? c : at, facing);
        f.put(s, 0, h - 1, 0, GLASS);
        f.put(s, 0, h, 0, GLASS);
        f.put(s, 0, h, -1, Structure.AIR);
        for (int side = -1; side <= 1; side += 2) {
            f.put(s, side, h - 1, 0, ROOF.block());
            f.put(s, side, h, 0, ROOF.block());
        }
        f.put(s, -1, h + 1, 0, Kit.stairs(ROOF.stairs(), f.plus));
        f.put(s, 1, h + 1, 0, Kit.stairs(ROOF.stairs(), f.minus));
        f.put(s, 0, h + 1, 0, ROOF.block());
        f.put(s, 0, h + 2, 0, Kit.slab(ROOF.slab()));
        f.put(s, 0, h - 2, 1, Kit.upside(TRIM_STAIRS, f.in));
    }

    // ---------------------------------------------------------------- spires

    /**
     * A needle spire: a tall slender octagonal spire on layer {@code y} around (cx, cz), of half-size {@code radius} at its
     * foot (2 to 4), reaching {@code height} layers in all. Its rings draw in by one block on every side, each ring several
     * layers high with a row of spire stairs on its top edge, every ring taller than the one under it so that it thins
     * into a needle; then a needle of single spire blocks and an iron cross on its point. Four lucarnes look out of its
     * faces: a pane of purple glass between cheeks under a small gable of spire stairs, one block out of the ring they stand
     * on; for a foot of half-size 2 that is the foot itself, so the spire takes a square of {@code 2 * radius + 3} (on a
     * tower, the walk inside its parapet), from 3 up the second ring, so they sit on the first. It is a shell two blocks
     * thick.
     *
     * @return the layer above the cross
     */
    static int needleSpire(Structure s, int cx, int y, int cz, int radius, int height) {
        int cross = 3;
        int needle = Math.max(2, Math.min(5, height / 5));
        int body = Math.max(radius, height - cross - needle);
        // The rings share the body evenly, the upper ones taking what is left over.
        int[] rise = new int[radius + 1];
        for (int r = radius; r >= 1; r--) {
            rise[r] = body / radius + (r <= body % radius ? 1 : 0);
        }
        int layer = y;
        int lucarneRing = radius >= 3 ? radius - 1 : radius;
        int lucarnes = y;
        for (int r = radius; r >= 1; r--) {
            if (r == lucarneRing) {
                lucarnes = layer;
            }
            for (int j = 0; j < rise[r]; j++) {
                boolean last = j == rise[r] - 1;
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (!inOctagon(dx, dz, r) || inOctagon(dx, dz, r - 2)) {
                            continue;
                        }
                        boolean rim = onOctagonRim(dx, dz, r);
                        put(s, cx + dx, layer, cz + dz, rim && last && r > 1 ? octagonSlope(SPIRE.stairs(), dx, dz, r) : SPIRE.block());
                    }
                }
                layer++;
            }
        }
        for (int i = 0; i < needle; i++) {
            put(s, cx, layer++, cz, SPIRE.block());
        }
        put(s, cx, layer, cz, IRON);
        put(s, cx, layer + 1, cz, IRON);
        put(s, cx - 1, layer + 1, cz, IRON);
        put(s, cx + 1, layer + 1, cz, IRON);
        put(s, cx, layer + 2, cz, IRON);
        if (radius >= 2) {
            int r = lucarneRing;
            for (String facing : new String[]{"north", "south", "west", "east"}) {
                int[] d = Furnish.step(facing);
                Face f = new Face(cx + d[0] * (r + 1), lucarnes, cz + d[1] * (r + 1), facing);
                f.put(s, 0, 0, 0, VIOLET);
                f.put(s, 0, 0, -1, Structure.AIR);
                f.put(s, -1, 0, 0, SPIRE.block());
                f.put(s, 1, 0, 0, SPIRE.block());
                f.put(s, -1, 1, 0, Kit.stairs(SPIRE.stairs(), f.plus));
                f.put(s, 1, 1, 0, Kit.stairs(SPIRE.stairs(), f.minus));
                f.put(s, 0, 1, 0, SPIRE.block());
                f.put(s, 0, 2, 0, Kit.slab(SPIRE.slab()));
            }
        }
        return layer + 3;
    }

    /**
     * The walls of an octagonal tower or stair turret around (cx, cz), of half-size {@code r}, from layer {@code y1} to
     * {@code y2}: one block thick, hollow inside (see {@link #inOctagon} for its plan).
     */
    static void octagonWalls(Structure s, int cx, int y1, int y2, int cz, int r, String block) {
        for (int y = y1; y <= y2; y++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (onOctagonRim(dx, dz, r)) {
                        put(s, cx + dx, y, cz + dz, block);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- facades

    /**
     * The narrow gabled street front of a town house in a wall looking the way of {@code facing}, its middle at (x, z), its
     * ground floor at layer {@code y} (the floor; one stands at {@code y + 1}): {@code width} columns (odd, 5 to 11),
     * {@code floors} storeys of {@code storey} layers (5 or more) and over them the gable of a steep roof of the given pitch,
     * the same as {@link #steepRoof} with a {@link #PARAPET} end draws on this line, with its coping, kneelers and apex
     * pinnacle. The ground floor is of wall on a rough plinth with a pointed doorway in the middle ({@link #portal} 1 wide,
     * its door at {@code y + 1}) and, from 7 wide, a lancet of clear glass on each side sharing its colonnettes; every upper
     * floor an arcade of narrow lancets of clear glass under purple heads ({@code storey - 3} high, one every second column
     * from {@code 1 - width / 2} to {@code width / 2 - 1}), set in plaster between corner piers of wall and standing on a
     * string course of trim at the floor line; in the gable a lancet (5 wide) or a rose window 5 across (7 and more), with
     * niches of plaster beside it from 9 wide. Draw the roof first: the front draws its gable over it. Mouldings stand one
     * block before the wall.
     *
     * @return the layer of the gable's foot (the eaves, {@code y + floors * storey})
     */
    static int facade(Structure s, int x, int y, int z, String facing, int width, int floors, int storey, int pitch) {
        Face f = new Face(x, y, z, facing);
        int hw = (width - 1) / 2;
        int eaves = floors * storey;
        for (int v = 0; v < eaves; v++) {
            for (int u = -hw; u <= hw; u++) {
                f.put(s, u, v, 0, v == 0 ? ROUGH : v < storey || Math.abs(u) == hw || v % storey == 0 ? WALL : PLASTER);
            }
        }
        for (int i = 1; i <= floors; i++) {
            for (int u = -hw; u <= hw; u++) {
                f.put(s, u, i * storey, 1, Kit.upside(TRIM_STAIRS, f.in));
            }
        }
        // The ground floor: the doorway and, when there is room, a lancet on each side of it.
        portal(s, f.px(0, 0), y + 1, f.pz(0, 0), facing, 1, false);
        if (hw >= 3) {
            for (int side = -1; side <= 1; side += 2) {
                frame(s, new Face(f.px(2 * side, 0), y + 1, f.pz(2 * side, 0), facing), 1, 2, GLASS, CRIMSON, false);
                f.put(s, side, 3, 1, TRACERY);
            }
        }
        // The upper floors: narrow lancets side by side, standing on the string course.
        int lights = storey - 3;
        for (int i = 1; i < floors; i++) {
            int fl = i * storey;
            for (int u = -hw + 1; u <= hw - 1; u += 2) {
                frame(s, new Face(f.px(u, 0), y + fl + 2, f.pz(u, 0), facing), 1, lights, GLASS, VIOLET, false);
            }
            for (int u = -hw + 2; u <= hw - 2; u += 2) {
                f.put(s, u, fl + 2 + lights, 1, TRACERY);
            }
        }
        // The gable.
        boolean alongX = facing.equals("west") || facing.equals("east");
        int a = alongX ? x : z;
        int c = alongX ? z : x;
        gableEnd(s, alongX, a, c - hw, c + hw, y + eaves, pitch, true);
        int e = eaves;
        if (hw <= 2) {
            frame(s, new Face(f.px(0, 0), y + e + 1, f.pz(0, 0), facing), 1, 2, GLASS, VIOLET, false);
        } else {
            rose(s, f.px(0, 0), y + e + 3, f.pz(0, 0), facing, 5);
            // niches of plaster beside the rose, as high as the gable allows
            for (int side = -1; side <= 1 && hw >= 4; side += 2) {
                int u = side * (hw - 1);
                int gable = e + 2 * pitch - 1;
                for (int v = e + 1; v <= Math.min(gable, e + 4); v++) {
                    f.put(s, u, v, 0, PLASTER);
                }
            }
            int apex = e + (hw + 1) * pitch;
            for (int v = e + 7; v <= apex - 2; v++) {
                f.put(s, 0, v, 0, PLASTER);
            }
        }
        return y + eaves;
    }

    // ---------------------------------------------------------------- vaults

    /**
     * A ribbed vault over a room whose inside spans x1..x2 and z1..z2 (the walls stand outside it), springing at layer
     * {@code y} from the long sides: across the room the vault steps in by one cell a layer from both walls until the two
     * sides meet in a point. Its webs are plaster stepping up in courses, so that the ceiling shows pale; every {@code bay}
     * cells along it (counted from {@code ribAt}) and at both ends a transverse rib of hanging trim stairs, and a ridge rib
     * of trim slabs along its crown. The vault is closed above (a floor or the space under a roof may follow).
     *
     * @param alongX whether the vault runs along x (springing from the walls at z1 - 1 and z2 + 1) or along z
     * @return the layer of its crown
     */
    static int vault(Structure s, int x1, int z1, int x2, int z2, int y, boolean alongX, int bay, int ribAt) {
        int a1 = alongX ? x1 : z1;
        int a2 = alongX ? x2 : z2;
        int c1 = alongX ? z1 : x1;
        int c2 = alongX ? z2 : x2;
        String toLow = alongX ? "north" : "west";
        String toHigh = alongX ? "south" : "east";
        int crown = y;
        for (int k = 0; ; k++) {
            int lo = c1 + k;
            int hi = c2 - k;
            int v = y + k;
            crown = v;
            for (int a = a1; a <= a2; a++) {
                boolean rib = Math.floorMod(a - ribAt, bay) == 0 || a == a1 || a == a2;
                for (int c = c1; c < Math.min(lo, hi + 1); c++) {
                    at(s, alongX, a, v, c, PLASTER);
                }
                for (int c = Math.max(hi + 1, lo); c <= c2; c++) {
                    at(s, alongX, a, v, c, PLASTER);
                }
                if (lo < hi) {
                    at(s, alongX, a, v, lo, rib ? Kit.upside(TRIM_STAIRS, toLow) : PLASTER);
                    at(s, alongX, a, v, hi, rib ? Kit.upside(TRIM_STAIRS, toHigh) : PLASTER);
                } else if (lo == hi) {
                    at(s, alongX, a, v, lo, Kit.topSlab(TRIM_SLAB));
                }
            }
            if (lo >= hi) {
                break;
            }
        }
        return crown;
    }

    // ---------------------------------------------------------------- furniture

    /**
     * A pew: a bench of timber stairs from (x1, z1) to (x2, z2) on layer {@code y}, its seat looking {@code facing}, closed
     * at both ends by panels (open trapdoors of timber) in the cells beyond them.
     */
    static void pew(Structure s, int x1, int z1, int x2, int z2, int y, String facing) {
        Furnish.bench(s, x1, z1, x2, z2, y, facing, WOOD);
        if (z1 == z2) {
            putFree(s, Math.min(x1, x2) - 1, y, z1, Kit.shutter(PANEL, "west"));
            putFree(s, Math.max(x1, x2) + 1, y, z1, Kit.shutter(PANEL, "east"));
        } else {
            putFree(s, x1, y, Math.min(z1, z2) - 1, Kit.shutter(PANEL, "north"));
            putFree(s, x1, y, Math.max(z1, z2) + 1, Kit.shutter(PANEL, "south"));
        }
    }

    /**
     * A candelabrum standing on layer {@code y}: a black stone stand two layers high with three lit candles on it.
     */
    static void candelabrum(Structure s, int x, int y, int z) {
        put(s, x, y, z, STAND);
        put(s, x, y + 1, z, STAND);
        put(s, x, y + 2, z, Furnish.candles(3));
    }

    /**
     * A corona: an iron wheel of candles hanging on a chain from the ceiling; {@code top} is the highest free layer under the
     * ceiling and the wheel hangs {@code drop} layers below it: a hub of iron with four iron arms, three candles on each
     * arm and a lantern under the hub.
     */
    static void corona(Structure s, int x, int z, int top, int drop) {
        int hub = top - drop;
        for (int yy = hub + 1; yy <= top; yy++) {
            put(s, x, yy, z, Kit.chain());
        }
        put(s, x, hub, z, IRON);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            put(s, x + d[0], hub, z + d[1], IRON);
            put(s, x + d[0], hub + 1, z + d[1], Furnish.candles(3));
        }
        put(s, x, hub - 1, z, Kit.hangingLantern());
    }

    /**
     * A gothic chimneypiece against a wall: (x, z) is a block of the wall and the room lies the way of {@code facing}, its
     * floor under layer {@code y}. The wall stays whole (no fire shows outside); before it, in the first cell of the room, a
     * fire on a hearth of rough stone between two jambs of trim and carved stone, behind an iron grate one cell further
     * out; a mantel of hanging trim stairs over it and above a pointed hood (a gable of trim stairs) under a chimney breast
     * of wall up to layer {@code top}. It takes three columns and two cells of the room.
     */
    static void fireplace(Structure s, int x, int y, int z, String facing, int top) {
        Face f = new Face(x, y, z, facing);
        f.put(s, 0, -1, 1, ROUGH);
        f.put(s, 0, 0, 1, "minecraft:campfire[facing=" + facing + ",lit=true,signal_fire=false,waterlogged=false]");
        f.put(s, 0, 0, 2, IRON);
        f.put(s, 0, 1, 1, Structure.AIR);
        for (int side = -1; side <= 1; side += 2) {
            f.put(s, side, 0, 1, TRIM);
            f.put(s, side, 1, 1, CARVED);
        }
        for (int u = -1; u <= 1; u++) {
            f.put(s, u, 2, 1, Kit.upside(TRIM_STAIRS, f.in));
        }
        f.put(s, -1, 3, 1, Kit.stairs(TRIM_STAIRS, f.plus));
        f.put(s, 1, 3, 1, Kit.stairs(TRIM_STAIRS, f.minus));
        for (int v = 3; y + v <= top; v++) {
            f.put(s, 0, v, 1, WALL);
        }
    }

    /**
     * A tall bookcase against a wall, its books looking the way of {@code facing}: three columns of bookshelves
     * {@code height} layers high around (x, z) on layer {@code y}, under a pointed top of timber stairs with a candle on its
     * apex.
     */
    static void bookcase(Structure s, int x, int y, int z, String facing, int height) {
        Face f = new Face(x, y, z, facing);
        Furnish.books(s, f.px(-1, 0), f.pz(-1, 0), f.px(1, 0), f.pz(1, 0), y, y + height - 1);
        f.put(s, -1, height, 0, Kit.stairs(WOOD_STAIRS, f.plus));
        f.put(s, 1, height, 0, Kit.stairs(WOOD_STAIRS, f.minus));
        f.put(s, 0, height, 0, PLANKS);
        f.put(s, 0, height + 1, 0, Furnish.candle(""));
    }

    /**
     * A bed of state for castles and halls, no home: the canopy bed of {@link Furnish#canopyBed} of the timber, with a
     * banner of the cloth hanging on the wall over its head when a wall stands there.
     *
     * @param facing where its head lies from its foot
     */
    static void stateBed(Structure s, int x, int y, int z, String facing) {
        Furnish.canopyBed(s, x, y, z, facing, WOOD);
        int[] d = Furnish.step(facing);
        int bx = x + 3 * d[0];
        int bz = z + 3 * d[1];
        if (sturdy(s, bx, y + 2, bz)) {
            putFree(s, x + 2 * d[0], y + 2, z + 2 * d[1], Kit.wallBanner("red", Furnish.opposite(facing)));
        }
    }

    /**
     * A bed of a home (it counts: a home for one) with a small table and a lit candle beside its head on the side
     * {@code side} (+1 to the left of one lying in it, -1 to the right).
     *
     * @param facing where its head lies from its foot
     */
    static void bed(Structure s, int x, int y, int z, String facing, int side) {
        Furnish.bed(s, x, y, z, facing, "red");
        int[] d = Furnish.step(facing);
        int[] l = Furnish.step(Furnish.left(facing));
        int tx = x + d[0] + l[0] * side;
        int tz = z + d[1] + l[1] * side;
        if (s.inside(tx, y, tz) && !s.solid(tx, y, tz)) {
            put(s, tx, y, tz, Kit.topSlab(WOOD_SLAB));
            put(s, tx, y + 1, tz, Furnish.candle(""));
        }
    }

    /**
     * An altar before a wall, facing {@code facing}: a carved block between two hanging trim stairs at (x, y, z) and its
     * sides, a white cloth on it with candles at its ends, and a banner of the cloth on the wall behind it.
     */
    static void altar(Structure s, int x, int y, int z, String facing) {
        Face f = new Face(x, y, z, facing);
        f.put(s, 0, 0, 0, CARVED);
        f.put(s, -1, 0, 0, Kit.upside(TRIM_STAIRS, f.minus));
        f.put(s, 1, 0, 0, Kit.upside(TRIM_STAIRS, f.plus));
        f.put(s, 0, 1, 0, "minecraft:white_carpet");
        f.put(s, -1, 1, 0, Furnish.candles(3));
        f.put(s, 1, 1, 0, Furnish.candles(3));
        if (sturdy(s, f.px(0, -1), y + 2, f.pz(0, -1))) {
            f.putFree(s, 0, 2, 0, Kit.wallBanner("red", facing));
        }
    }

    /**
     * Whether a position holds a whole block that a banner may hang on (not glass, bars or air).
     */
    private static boolean sturdy(Structure s, int x, int y, int z) {
        return solid(s, x, y, z) && States.isFull(s.get(x, y, z)) && !s.get(x, y, z).contains("glass");
    }

    /**
     * Checkered paving of pale and black stone over the cells x1..x2, z1..z2 at layer {@code y}.
     */
    static void paving(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                put(s, x, y, z, Math.floorMod(x + z, 2) == 0 ? PAVING_LIGHT : PAVING_DARK);
            }
        }
    }
}
