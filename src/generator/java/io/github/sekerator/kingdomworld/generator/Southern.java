package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The style "southern": the architecture of the Mediterranean south. Its buildings are cubes of whitewashed render on a
 * footing of dressed stone, with stone quoins at the corners and a band of white trim at every floor, under LOW roofs of
 * red clay tiles that rise half a block for every block inwards (hip roofs on houses and halls, gable roofs on wings,
 * lean-to roofs on porticoes) and overhang the walls by a deep eave carried on a cornice of stairs hanging upside down.
 * Their ground floors open in arcades of round arches on columns; upper floors have loggias, balconies on corbels with
 * railings of wrought iron, and tall narrow windows with a sill of trim and shutters standing open beside them. Above the
 * flat line of the roofs rise square bell towers (campanile) with an open belfry under a pyramid of tiles or a small dome,
 * and domes on drums over chapels and halls. Around them courts paved in stone and cotto with a fountain in the middle,
 * pergolas of stone piers and timber beams under vines, potted citrus trees, terracotta pots and the dark spindles of
 * cypresses. Castles of the style are of dressed stone with square towers crowned by machicolations (a parapet thrust out
 * on corbels) and swallowtail merlons. Inside: floors of cotto, ceilings of dark beams, kitchens with a hood of plaster over
 * the fire and counters of glazed tiles, beds with headboards of wrought iron, amphorae and lanterns on iron brackets.
 *
 * <p>The materials are the parts of a look (see {@link Kit}): walls are the plaster (render: white, cream, ochre or apricot
 * in a look); footings, columns, quoins and the shafts of towers the wall stone (smooth sandstone or a light stone);
 * paving the rough stone; cornices, sills, bands, arches and the rims of basins the trim; keystones and capitals the
 * carved stone; all roofs and pyramids the roof (clay tiles: bricks, mangrove, granite or red sandstone); domes the spire
 * (brick or green copper); shutters the shutters; beams, planks, doors and furniture the timber. Fixed accents that never
 * change: plain terracotta (cotto floors, planters), blue and white glazed terracotta (azulejos), iron bars (railings,
 * grilles, brackets), glass, decorated pots (amphorae), flower pots, persistent leaves (dark cypresses of spruce, green
 * foliage of oak, bougainvillea of flowering azalea), water in closed basins and sea lanterns under it, bells.
 *
 * <p>This class holds the data and the shapes of the style; buildings are drawn with them by other classes. Coordinates as
 * everywhere: x from west to east, z from the back (north) to the front (south), y up. A shape draws only inside the box
 * of the structure. "facing" of a feature on a wall is the way the wall looks out (a window in the south wall faces
 * south); "along" a wall is to the left of that (east for a wall facing south). Walls are drawn first, then openings
 * and features on them, then roofs; call {@link Structure#connect()} once at the end so that railings, bars and fences join.
 */
final class Southern {
    // ---------------------------------------------------------------- materials

    /** Whitewashed render of the walls: the plaster of a look (white, cream, ochre or apricot). */
    static final String RENDER = Kit.PLASTER;
    /** Dressed stone: footings, plinths, quoins, columns, piers and the shafts of towers (the wall stone of a look). */
    static final String STONE = Kit.WALL;
    static final String STONE_STAIRS = Kit.WALL_STAIRS;
    static final String STONE_SLAB = Kit.WALL_SLAB;
    /** Paving of courts, terraces, streets and steps (the rough stone of a look). */
    static final String PAVING = Kit.ROUGH;
    static final String PAVING_STAIRS = Kit.ROUGH_STAIRS;
    static final String PAVING_SLAB = Kit.ROUGH_SLAB;
    /** Cornices, sills, lintels, bands, arches, capitals, copings and the rims of basins (the trim of a look). */
    static final String TRIM = Kit.TRIM;
    static final String TRIM_STAIRS = Kit.TRIM_STAIRS;
    static final String TRIM_SLAB = Kit.TRIM_SLAB;
    /** Keystones and the masks of fountains (the carved stone of a look). */
    static final String CARVED = Kit.CARVED;
    /** Clay tiles of every roof and pyramid (the roof of a look). */
    static final Kit.Roof TILES = Kit.TILE;
    /** Domes (the spire of a look: brick or green copper). */
    static final Kit.Roof DOME = Kit.SLATE;
    /** Shutters, standing open beside windows and doors (a look may make them teal, brown or white, or take them away). */
    static final String SHUTTER = Kit.SHUTTER;
    /** Timber (the wood of the place): beams of ceilings, pergolas and lintels. */
    static final String BEAM = "minecraft:stripped_spruce_log";
    static final String PLANKS = "minecraft:spruce_planks";
    static final String PLANK_SLAB = "minecraft:spruce_slab";
    static final String PLANK_STAIRS = "minecraft:spruce_stairs";
    static final String FENCE = "minecraft:spruce_fence";
    static final String DOOR = "minecraft:spruce_door";
    static final String TRUNK = "minecraft:spruce_log[axis=y]";
    /** The wood of furniture (Furnish pieces), lighter than the beams; timber as well. */
    static final String WOOD = "oak";
    /** Floors of baked clay tiles (cotto), planters and big pots: plain terracotta, a fixed accent. */
    static final String COTTO = "minecraft:terracotta";
    /** Glazed tiles (azulejos, majolica) of kitchens, fountains and chapels: fixed accents. */
    static final String AZULEJO = "minecraft:light_blue_glazed_terracotta";
    static final String AZULEJO_BLUE = "minecraft:blue_glazed_terracotta";
    /** Wrought iron: railings, grilles, the brackets of lanterns, lamp posts. */
    static final String IRON = Kit.BARS;
    static final String GLASS = Kit.GLASS;
    static final String WATER = "minecraft:water[level=0]";
    static final String SEA_LANTERN = "minecraft:sea_lantern";
    /** Leaves that never decay: dark cypress, green foliage (citrus, vines, climbers), bougainvillea in flower. */
    static final String CYPRESS = Kit.leaves("spruce");
    static final String FOLIAGE = Kit.leaves("oak");
    static final String BLOSSOM = Kit.leaves("flowering_azalea");
    static final String POT = "minecraft:flower_pot";
    static final String BELL = "minecraft:bell[attachment=ceiling,facing=north,powered=false]";
    static final String ROD = "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]";

    /** Kinds of the parts of a low roof (see {@link #lowRoof}): a hip roof, a gable roof whose ridge runs along x or z. */
    static final int HIP = 0;
    static final int RIDGE_X = 1;
    static final int RIDGE_Z = 2;
    /** A lean-to roof rising towards the north (its high side at z1, against a wall), south, west or east. */
    static final int LEAN_NORTH = 3;
    static final int LEAN_SOUTH = 4;
    static final int LEAN_WEST = 5;
    static final int LEAN_EAST = 6;

    /** Tops of a campanile (see {@link #campanile}): a pyramid of tiles, a small dome, a flat terrace behind merlons. */
    static final int PYRAMID = 0;
    static final int DOMED = 1;
    static final int TERRACE = 2;

    private static final String[] SIDES = {"north", "south", "west", "east"};

    private Southern() {
    }

    // ---------------------------------------------------------------- helpers

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    /** Puts a block only where the building has nothing yet. */
    private static void add(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z) && !s.solid(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static boolean solid(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && s.solid(x, y, z);
    }

    /** A cell on a line along x (a, c = x, z) or along z (a, c = z, x). */
    private static void at(Structure s, boolean alongX, int a, int y, int c, String state) {
        put(s, alongX ? a : c, y, alongX ? c : a, state);
    }

    static String stairs(String block, String facing, String half, String shape) {
        return block + "[facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=false]";
    }

    /**
     * Stairs whose high part covers the corner between the sides {@code a} and {@code b} of their cell: one quarter (outer)
     * or three (inner, the halves a and b).
     */
    private static String cornerStairs(String block, String a, String b, String half, boolean inner) {
        return stairs(block, a, half, (inner ? "inner_" : "outer_") + (Furnish.left(a).equals(b) ? "left" : "right"));
    }

    private static boolean perpendicular(String a, String b) {
        return (a.equals("north") || a.equals("south")) != (b.equals("north") || b.equals("south"));
    }

    /**
     * Stairs on the ring x1..x2, z1..z2 with their high part to the inside of the ring; at its corners a quarter to the inside.
     */
    private static String ringStair(String block, String half, int x, int z, int x1, int z1, int x2, int z2) {
        String inX = x == x1 ? "east" : x == x2 ? "west" : null;
        String inZ = z == z1 ? "south" : z == z2 ? "north" : null;
        if (inX != null && inZ != null) {
            return cornerStairs(block, inZ, inX, half, false);
        }
        return stairs(block, inX != null ? inX : inZ, half, "straight");
    }

    /**
     * Stairs on a round rim around its middle (dx, dz from it) with their high part towards the middle; on the diagonals a
     * quarter (or three quarters with {@code inner}) towards it.
     */
    private static String roundStair(String block, String half, int dx, int dz, boolean inner) {
        String toX = dx > 0 ? "west" : "east";
        String toZ = dz > 0 ? "north" : "south";
        if (Math.abs(dx) == Math.abs(dz) && dx != 0) {
            return cornerStairs(block, toZ, toX, half, inner);
        }
        return stairs(block, Math.abs(dx) > Math.abs(dz) ? toX : toZ, half, "straight");
    }

    /** A number of a column that looks random but is the same every time, for irregular patterns. */
    private static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    private static boolean onOutline(int x, int z, int x1, int z1, int x2, int z2) {
        return x == x1 || x == x2 || z == z1 || z == z2;
    }

    // ---------------------------------------------------------------- walls

    /**
     * The walls of a house or a hall on the outline x1..x2, z1..z2 from layer {@code y1} to {@code y2}: whitewashed render,
     * dressed stone at the four corners (quoins) and along the bottom layer (the plinth). Hollow inside.
     */
    static void walls(Structure s, int x1, int z1, int x2, int z2, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    if (!onOutline(x, z, x1, z1, x2, z2)) {
                        continue;
                    }
                    boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                    put(s, x, y, z, corner || y == y1 ? STONE : RENDER);
                }
            }
        }
    }

    /**
     * A string course: a band of trim on the outline x1..x2, z1..z2 at layer {@code y}, usually at the level of a floor.
     */
    static void band(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                if (onOutline(x, z, x1, z1, x2, z2)) {
                    put(s, x, y, z, TRIM);
                }
            }
        }
    }

    /**
     * A cornice: stairs hanging upside down on the ring x1..x2, z1..z2 at layer {@code y}, their lower step against the
     * inside of the ring (the wall they stand out of), the corners turned. Drawn only where nothing stands yet.
     */
    static void cornice(Structure s, int x1, int z1, int x2, int z2, int y, String stairs) {
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                if (onOutline(x, z, x1, z1, x2, z2)) {
                    add(s, x, y, z, ringStair(stairs, "top", x, z, x1, z1, x2, z2));
                }
            }
        }
    }

    /**
     * A flat ceiling (and the floor above it) at layer {@code y} over x1..x2, z1..z2: dark beams of the timber every second
     * line, running along x or along z, with planks between them.
     */
    static void beamCeiling(Structure s, int x1, int z1, int x2, int z2, int y, boolean beamsAlongX) {
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                boolean beam = (beamsAlongX ? z - z1 : x - x1) % 2 == 0;
                put(s, x, y, z, beam ? Kit.log(BEAM, beamsAlongX ? "x" : "z") : PLANKS);
            }
        }
    }

    // ---------------------------------------------------------------- low roofs

    /**
     * A low hip roof of tiles over the eaves rectangle x1..x2, z1..z2 (one block beyond the walls on every side). See
     * {@link #lowRoof}.
     *
     * @param y the layer of the eaves, the layer above the top course of the walls
     * @return the layer above the ridge
     */
    static int lowHipRoof(Structure s, int x1, int z1, int x2, int z2, int y) {
        return lowRoof(s, y, TILES, TRIM_STAIRS, null, new int[]{x1, z1, x2, z2, HIP});
    }

    /**
     * A low gable roof of tiles over the eaves rectangle x1..x2, z1..z2 (one block beyond the walls on every side), its
     * ridge along x or along z, the gables filled with render under it. See {@link #lowRoof}.
     *
     * @param y the layer of the eaves, the layer above the top course of the walls
     * @return the layer above the ridge
     */
    static int lowGableRoof(Structure s, int x1, int z1, int x2, int z2, int y, boolean alongX) {
        return lowRoof(s, y, TILES, TRIM_STAIRS, RENDER, new int[]{x1, z1, x2, z2, alongX ? RIDGE_X : RIDGE_Z});
    }

    private static int rise(int[] part, int x, int z) {
        if (x < part[0] || x > part[2] || z < part[1] || z > part[3]) {
            return -1;
        }
        return switch (part[4]) {
            case RIDGE_X -> Math.min(z - part[1], part[3] - z);
            case RIDGE_Z -> Math.min(x - part[0], part[2] - x);
            case LEAN_NORTH -> part[3] - z;
            case LEAN_SOUTH -> z - part[1];
            case LEAN_WEST -> part[2] - x;
            case LEAN_EAST -> x - part[0];
            default -> Math.min(Math.min(x - part[0], part[2] - x), Math.min(z - part[1], part[3] - z));
        };
    }

    /**
     * A low roof of one or more parts with valleys where they meet: the roof of an L, T or U plan, a house with its wing,
     * a portico leaning on a wall. Every column of the roof lies a number of rows {@code k} in from the nearest eave of its
     * part (the highest where parts overlap) and holds one slab at layer {@code y + k / 2}, a lower slab on even rows and an
     * upper slab on odd rows, so that the roof rises half a block for every block inwards: the low pitch of tiles of the
     * south. Under the eaves (row 0) a cornice of upside-down stairs of {@code eave} at layer {@code y - 1} stands out of
     * the walls, turned at the corners, outer and inner. With a {@code gable} block the ends of gable and lean-to parts are
     * closed under the roof one block in from their ends (where the walls stand), and the half-open cell under an upper
     * slab at a gable is filled with the full block of the roof. Slabs are laid only where nothing stands yet, so a lower
     * wing tucks its roof against a taller wall.
     *
     * @param y     the layer of the eaves (the layer above the top course of the walls)
     * @param eave  the stairs of the cornice under the eaves, or null for none
     * @param gable the block of the gables, or null to leave them open
     * @param parts each {x1, z1, x2, z2, kind}: an eaves rectangle (overhang included) and {@link #HIP}, {@link #RIDGE_X},
     *              {@link #RIDGE_Z} or a lean-to ({@link #LEAN_NORTH}... rising towards that side)
     * @return the layer above the ridge
     */
    static int lowRoof(Structure s, int y, Kit.Roof roof, String eave, String gable, int[]... parts) {
        int bx1 = Integer.MAX_VALUE;
        int bz1 = Integer.MAX_VALUE;
        int bx2 = Integer.MIN_VALUE;
        int bz2 = Integer.MIN_VALUE;
        for (int[] p : parts) {
            bx1 = Math.min(bx1, p[0]);
            bz1 = Math.min(bz1, p[1]);
            bx2 = Math.max(bx2, p[2]);
            bz2 = Math.max(bz2, p[3]);
        }
        int[][] k = new int[bx2 - bx1 + 1][bz2 - bz1 + 1];
        for (int[] row : k) {
            Arrays.fill(row, -1);
        }
        for (int x = bx1; x <= bx2; x++) {
            for (int z = bz1; z <= bz2; z++) {
                for (int[] p : parts) {
                    k[x - bx1][z - bz1] = Math.max(k[x - bx1][z - bz1], rise(p, x, z));
                }
            }
        }
        int top = y;
        for (int x = bx1; x <= bx2; x++) {
            for (int z = bz1; z <= bz2; z++) {
                int r = k[x - bx1][z - bz1];
                if (r < 0) {
                    continue;
                }
                int layer = y + r / 2;
                add(s, x, layer, z, r % 2 == 0 ? Kit.slab(roof.slab()) : Kit.topSlab(roof.slab()));
                top = Math.max(top, layer);
            }
        }
        if (eave != null) {
            for (int x = bx1; x <= bx2; x++) {
                for (int z = bz1; z <= bz2; z++) {
                    if (k[x - bx1][z - bz1] != 0) {
                        continue;
                    }
                    List<String> in = new ArrayList<>();
                    List<String> along = new ArrayList<>();
                    for (String side : SIDES) {
                        int[] d = Furnish.step(side);
                        int nx = x + d[0];
                        int nz = z + d[1];
                        int n = nx < bx1 || nx > bx2 || nz < bz1 || nz > bz2 ? -1 : k[nx - bx1][nz - bz1];
                        if (n >= 1) {
                            in.add(side);
                        } else if (n == 0) {
                            along.add(side);
                        }
                    }
                    String state = null;
                    if (in.size() == 1) {
                        state = Kit.upside(eave, in.get(0));
                    } else if (in.size() == 2 && perpendicular(in.get(0), in.get(1))) {
                        state = cornerStairs(eave, in.get(0), in.get(1), "top", true);
                    } else if (in.isEmpty() && along.size() == 2 && perpendicular(along.get(0), along.get(1))) {
                        state = cornerStairs(eave, along.get(0), along.get(1), "top", false);
                    }
                    if (state != null) {
                        add(s, x, y - 1, z, state);
                    }
                }
            }
        }
        for (int[] p : parts) {
            // A lean-to ends at its high side without an overhang: an upper slab there is a full block, closing it.
            if (p[4] < LEAN_NORTH) {
                continue;
            }
            boolean rowX = p[4] == LEAN_NORTH || p[4] == LEAN_SOUTH;
            int fixed = switch (p[4]) {
                case LEAN_NORTH -> p[1];
                case LEAN_SOUTH -> p[3];
                case LEAN_WEST -> p[0];
                default -> p[2];
            };
            for (int a = rowX ? p[0] : p[1]; a <= (rowX ? p[2] : p[3]); a++) {
                int x = rowX ? a : fixed;
                int z = rowX ? fixed : a;
                int r = k[x - bx1][z - bz1];
                int layer = y + r / 2;
                if (r % 2 == 1 && r == rise(p, x, z) && s.inside(x, layer, z) && s.get(x, layer, z).startsWith(roof.slab())) {
                    put(s, x, layer, z, roof.block());
                }
            }
        }
        if (gable != null) {
            for (int[] p : parts) {
                if (p[4] == HIP) {
                    continue;
                }
                boolean endsX = p[4] == RIDGE_X || p[4] == LEAN_NORTH || p[4] == LEAN_SOUTH;
                int[] ends = endsX ? new int[]{p[0] + 1, p[2] - 1} : new int[]{p[1] + 1, p[3] - 1};
                int c1 = endsX ? p[1] : p[0];
                int c2 = endsX ? p[3] : p[2];
                for (int wall : ends) {
                    for (int c = c1; c <= c2; c++) {
                        int x = endsX ? wall : c;
                        int z = endsX ? c : wall;
                        int r = k[x - bx1][z - bz1];
                        if (r < 1 || r != rise(p, x, z)) {
                            continue;
                        }
                        int layer = y + r / 2;
                        for (int yy = y; yy < layer; yy++) {
                            add(s, x, yy, z, gable);
                        }
                        if (r % 2 == 1 && s.inside(x, layer, z) && s.get(x, layer, z).startsWith(roof.slab())) {
                            put(s, x, layer, z, roof.block());
                        }
                    }
                }
            }
        }
        return top + 1;
    }

    /**
     * A pyramid roof of tiles over a square tower (or a hip at forty-five degrees over a rectangle): rings of stairs rising one
     * layer for every block inwards from the eaves ring x1..x2, z1..z2 (the overhang included) at layer {@code y}, closed
     * with a full block (or a ridge of blocks) on the top. Draw its cornice with {@link #cornice} at {@code y - 1}.
     *
     * @return the layer above the top, where a finial goes
     */
    static int pyramid(Structure s, int x1, int z1, int x2, int z2, int y, Kit.Roof roof) {
        int layer = y;
        int ring = 0;
        while (x2 - ring - (x1 + ring) >= 2 && z2 - ring - (z1 + ring) >= 2) {
            int a1 = x1 + ring;
            int a2 = x2 - ring;
            int b1 = z1 + ring;
            int b2 = z2 - ring;
            for (int z = b1; z <= b2; z++) {
                for (int x = a1; x <= a2; x++) {
                    if (onOutline(x, z, a1, b1, a2, b2)) {
                        put(s, x, layer, z, ringStair(roof.stairs(), "bottom", x, z, a1, b1, a2, b2));
                    }
                }
            }
            ring++;
            layer++;
        }
        if (x1 + ring <= x2 - ring && z1 + ring <= z2 - ring) {
            s.fill(x1 + ring, layer, z1 + ring, x2 - ring, layer, z2 - ring, roof.block());
            layer++;
        }
        return layer;
    }

    // ---------------------------------------------------------------- arcades, loggias, windows, balconies

    /**
     * An arcade of round arches on columns along a line from (x1, z1) to (x2, z2) (one of x or z the same at both ends),
     * standing on layer {@code y}: a column at both ends and after every {@code span} open cells, each a shaft of dressed
     * stone with a capital of trim at the springing; the arches of upside-down stairs of trim springing from the capitals
     * (span 2: a pointed round arch of two stairs; 3 or 4: a stair beside each column, the middle open; 5 and more: a
     * semicircle of two steps on each side; 1: a straight opening), so that the openings are {@code height} layers high
     * at their crown; over the arches a band of {@code band} one layer high (the spandrels). The openings are cleared. The
     * length must be a multiple of {@code span + 1} plus one (span 3: 5, 9, 13, 17...).
     *
     * @return the layer above the band
     */
    static int arcade(Structure s, int x1, int z1, int x2, int z2, int y, int height, int span, String band) {
        boolean alongX = z1 == z2;
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int c = alongX ? z1 : x1;
        String toStart = alongX ? "west" : "north";
        String toEnd = alongX ? "east" : "south";
        int crown = y + height - 1;
        for (int a = a1; a <= a2; a++) {
            int i = (a - a1) % (span + 1);
            if (i == 0) {
                for (int yy = y; yy < crown; yy++) {
                    at(s, alongX, a, yy, c, STONE);
                }
                at(s, alongX, a, crown, c, TRIM);
            } else {
                int fromStart = i;
                int fromEnd = span + 1 - i;
                int near = Math.min(fromStart, fromEnd);
                String toColumn = fromStart <= fromEnd ? toStart : toEnd;
                for (int yy = y; yy <= crown; yy++) {
                    at(s, alongX, a, yy, c, Structure.AIR);
                }
                if (span >= 5) {
                    if (near == 1) {
                        at(s, alongX, a, crown, c, band);
                        at(s, alongX, a, crown - 1, c, Kit.upside(TRIM_STAIRS, toColumn));
                    } else if (near == 2) {
                        at(s, alongX, a, crown, c, Kit.upside(TRIM_STAIRS, toColumn));
                    }
                } else if (span >= 2 && near == 1) {
                    at(s, alongX, a, crown, c, Kit.upside(TRIM_STAIRS, toColumn));
                }
            }
            at(s, alongX, a, crown + 1, c, band);
        }
        return crown + 2;
    }

    /**
     * A loggia: a gallery behind an arcade, open to the side {@code facing}, over the rectangle x1..x2, z1..z2 whose edge on
     * that side carries the arcade ({@link #arcade} of the given height and span, band of trim). Its floor of cotto lies at
     * layer {@code y - 1} (under the arcade a band of trim where nothing stands yet, so that a loggia over another shows a
     * band between them); its ceiling at layer {@code y + height} has dark beams running in from every column with planks
     * between them; with {@code railing} a railing of wrought iron closes the openings at layer {@code y} (a loggia on an
     * upper floor); a lantern hangs in the middle of every bay. Its back and end walls are the building's.
     *
     * @return the layer above the ceiling
     */
    static int loggia(Structure s, int x1, int z1, int x2, int z2, int y, int height, int span, String facing, boolean railing) {
        boolean alongX = facing.equals("south") || facing.equals("north");
        int line = switch (facing) {
            case "south" -> z2;
            case "north" -> z1;
            case "west" -> x1;
            default -> x2;
        };
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                if ((alongX ? z : x) == line) {
                    add(s, x, y - 1, z, TRIM);
                } else {
                    put(s, x, y - 1, z, COTTO);
                }
            }
        }
        if (alongX) {
            arcade(s, x1, line, x2, line, y, height, span, TRIM);
        } else {
            arcade(s, line, z1, line, z2, y, height, span, TRIM);
        }
        int top = y + height;
        int a1 = alongX ? x1 : z1;
        int a2 = alongX ? x2 : z2;
        int[] in = Furnish.step(Furnish.opposite(facing));
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                if ((alongX ? z : x) == line) {
                    continue;
                }
                int a = alongX ? x : z;
                boolean beam = (a - a1) % (span + 1) == 0;
                put(s, x, top, z, beam ? Kit.log(BEAM, alongX ? "z" : "x") : PLANKS);
            }
        }
        for (int a = a1; a <= a2; a++) {
            int i = (a - a1) % (span + 1);
            if (railing && i != 0) {
                at(s, alongX, a, y, line, IRON);
            }
            if (i == (span + 1) / 2) {
                at(s, alongX, a, top - 1, line + (alongX ? in[1] : in[0]), Kit.hangingLantern());
            }
        }
        return top + 1;
    }

    /**
     * A window in a wall looking the way of {@code facing}, its lowest pane at (x, y, z) in the wall: {@code width} (1, or 3
     * for an arched window) panes wide and {@code height} high. A narrow window has a lintel of trim over it; a window
     * three wide has its upper corners rounded by upside-down stairs of trim and a carved keystone. Before the wall a sill
     * of upside-down stairs of trim under it, and with {@code shutters} shutters standing open beside it (a look may take
     * them away; the window is whole without them); with {@code pot} terracotta flower pots stand on the sill.
     */
    static void window(Structure s, int x, int y, int z, String facing, int width, int height, boolean shutters, boolean pot) {
        window(s, x, y, z, facing, width, height, shutters, pot, GLASS);
    }

    /**
     * The same with its own panes: {@link #IRON} for a grille (reja) on a ground floor, a stained pane, or air.
     */
    static void window(Structure s, int x, int y, int z, String facing, int width, int height, boolean shutters, boolean pot, String pane) {
        int[] out = Furnish.step(facing);
        String plus = Furnish.left(facing);
        String minus = Furnish.opposite(plus);
        int[] al = Furnish.step(plus);
        int hw = (width - 1) / 2;
        for (int u = -hw; u <= hw; u++) {
            for (int v = 0; v < height; v++) {
                put(s, x + al[0] * u, y + v, z + al[1] * u, pane);
            }
        }
        if (hw > 0) {
            put(s, x + al[0] * hw, y + height - 1, z + al[1] * hw, Kit.upside(TRIM_STAIRS, plus));
            put(s, x - al[0] * hw, y + height - 1, z - al[1] * hw, Kit.upside(TRIM_STAIRS, minus));
            put(s, x, y + height, z, CARVED);
        } else {
            put(s, x, y + height, z, TRIM);
        }
        int fx = x + out[0];
        int fz = z + out[1];
        for (int u = -hw; u <= hw; u++) {
            put(s, fx + al[0] * u, y - 1, fz + al[1] * u, Kit.upside(TRIM_STAIRS, Furnish.opposite(facing)));
        }
        if (shutters) {
            for (int side = -1; side <= 1; side += 2) {
                int u = side * (hw + 1);
                for (int v = 0; v < height; v++) {
                    put(s, fx + al[0] * u, y + v, fz + al[1] * u, Kit.shutter(SHUTTER, facing));
                }
            }
        }
        if (pot) {
            for (int u = -hw; u <= hw; u += Math.max(1, 2 * hw)) {
                put(s, fx + al[0] * u, y, fz + al[1] * u, POT);
            }
        }
    }

    /**
     * A balcony before a door in a wall looking the way of {@code facing}: the door at (x, y, z) (its lower half at layer
     * {@code y}), with a lintel of trim over it and shutters standing open beside it on the wall; before it a floor of upper
     * slabs of trim at layer {@code y - 1}, {@code width} wide (odd, 3 or more) and {@code depth} deep, carried on corbels of
     * upside-down stairs of trim under every second cell (two steps when it is two deep); around it a railing of wrought
     * iron (the front row and the sides), with a lantern on each front corner. One stands on the floor at layer y inside
     * the railing (width 3 and depth 2: one cell before the door).
     */
    static void balcony(Structure s, int x, int y, int z, String facing, int width, int depth) {
        int[] out = Furnish.step(facing);
        int[] al = Furnish.step(Furnish.left(facing));
        String toWall = Furnish.opposite(facing);
        int hw = (width - 1) / 2;
        s.door(x, y, z, DOOR, toWall, "left");
        put(s, x, y + 2, z, TRIM);
        for (int v = 1; v <= depth; v++) {
            for (int u = -hw; u <= hw; u++) {
                int px = x + al[0] * u + out[0] * v;
                int pz = z + al[1] * u + out[1] * v;
                put(s, px, y - 1, pz, Kit.topSlab(TRIM_SLAB));
                if ((u + hw) % 2 == 0) {
                    put(s, px, y - 2, pz, Kit.upside(TRIM_STAIRS, toWall));
                    if (depth >= 2 && v == 1) {
                        put(s, px, y - 3, pz, Kit.upside(TRIM_STAIRS, toWall));
                    }
                }
                boolean rail = v == depth || Math.abs(u) == hw;
                put(s, px, y, pz, rail ? IRON : Structure.AIR);
                put(s, px, y + 1, pz, Structure.AIR);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            put(s, x + al[0] * hw * side + out[0] * depth, y + 1, z + al[1] * hw * side + out[1] * depth, Kit.lantern());
            int u = side * (hw + 1);
            for (int v = 0; v < 2; v++) {
                put(s, x + al[0] * u + out[0], y + v, z + al[1] * u + out[1], Kit.shutter(SHUTTER, facing));
            }
        }
    }

    /**
     * A flat roof terrace's parapet on the outline x1..x2, z1..z2: a low wall of render at layer {@code y} capped with
     * slabs of trim at {@code y + 1}.
     */
    static void parapet(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                if (onOutline(x, z, x1, z1, x2, z2)) {
                    put(s, x, y, z, RENDER);
                    put(s, x, y + 1, z, Kit.slab(TRIM_SLAB));
                }
            }
        }
    }

    // ---------------------------------------------------------------- towers

    /**
     * The shaft of a square tower {@code width} wide (odd, 5 or more) with its north-west corner at (x1, z1), standing on
     * layer {@code y}: a footing of dressed stone with a floor of paving at {@code y}; walls of {@code face} (render for a
     * bell tower, dressed stone for a castle) with quoins and a plinth of dressed stone, up to the top course at
     * {@code y + height}; a band of trim and a floor of planks every seven layers and at the top course, with a lantern
     * under each, and with {@code cornices} at every stage a cornice standing out one block around the shaft (a bell tower;
     * a castle tower stays sheer up to its machicolations); a ladder in the north-west inner corner from the ground through
     * every floor to the top; windows with sills on the four faces of every stage but the first (narrow ones; on towers 7
     * wide and more arched ones three wide from the third stage); a door at the foot of the face {@code door} (or none).
     *
     * @return the layer above the top course, where one stands on its top
     */
    static int towerShaft(Structure s, int x1, int z1, int y, int width, int height, String face, boolean cornices, String door) {
        int x2 = x1 + width - 1;
        int z2 = z1 + width - 1;
        int cx = x1 + width / 2;
        int cz = z1 + width / 2;
        int top = y + height;
        s.fill(x1, y, z1, x2, y, z2, STONE);
        s.fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, PAVING);
        for (int yy = y + 1; yy <= top; yy++) {
            boolean floor = yy == top || (yy - y) % 7 == 0 && yy <= top - 3;
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    if (!onOutline(x, z, x1, z1, x2, z2)) {
                        continue;
                    }
                    boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                    put(s, x, yy, z, floor ? TRIM : corner || yy <= y + 2 ? STONE : face);
                }
            }
            if (floor) {
                s.fill(x1 + 1, yy, z1 + 1, x2 - 1, yy, z2 - 1, PLANKS);
                put(s, x2 - 1, yy - 1, z2 - 1, Kit.hangingLantern());
                if (cornices && yy < top) {
                    cornice(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, yy, TRIM_STAIRS);
                }
            }
        }
        for (int k = 1; y + 7 * k + 5 < top; k++) {
            boolean arched = width >= 7 && k >= 2 && y + 7 * k + 6 < top;
            int wy = y + 7 * k + 3;
            int ww = arched ? 3 : 1;
            int wh = arched ? 3 : 2;
            window(s, cx, wy, z1, "north", ww, wh, false, false);
            window(s, cx, wy, z2, "south", ww, wh, false, false);
            window(s, x1, wy, cz, "west", ww, wh, false, false);
            window(s, x2, wy, cz, "east", ww, wh, false, false);
        }
        for (int yy = y + 1; yy <= top; yy++) {
            put(s, x1 + 1, yy, z1 + 1, Kit.ladder("south"));
        }
        if (door != null) {
            int[] d = Furnish.step(door);
            int dx = cx + d[0] * (width / 2);
            int dz = cz + d[1] * (width / 2);
            s.door(dx, y + 1, dz, DOOR, Furnish.opposite(door), "left");
            put(s, dx, y + 3, dz, CARVED);
        }
        return top + 1;
    }

    /**
     * The open belfry of a bell tower on the top of its shaft: on each face an arcade from corner to corner, four layers
     * high from layer {@code y} (5 wide: one arch; 7: two arches; 9: two arches of three), with a balustrade of wrought
     * iron in its openings; a bell hanging from the ceiling in the middle, lanterns in two corners; over it a ceiling of
     * dressed stone with a band of trim, and a cornice standing out one block around it.
     *
     * @param y the layer above the top course of the shaft
     * @return the layer of the eaves of what covers it (above the cornice)
     */
    static int belfry(Structure s, int x1, int z1, int y, int width) {
        int x2 = x1 + width - 1;
        int z2 = z1 + width - 1;
        int cx = x1 + width / 2;
        int cz = z1 + width / 2;
        int span = width == 7 ? 2 : 3;
        int h = 4;
        arcade(s, x1, z1, x2, z1, y, h, span, TRIM);
        arcade(s, x1, z2, x2, z2, y, h, span, TRIM);
        arcade(s, x1, z1, x1, z2, y, h, span, TRIM);
        arcade(s, x2, z1, x2, z2, y, h, span, TRIM);
        s.fill(x1 + 1, y + h, z1 + 1, x2 - 1, y + h, z2 - 1, STONE);
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                if (onOutline(x, z, x1, z1, x2, z2) && !solid(s, x, y, z)) {
                    put(s, x, y, z, IRON);
                }
            }
        }
        put(s, cx, y + h - 1, cz, BELL);
        put(s, x1 + 1, y + h - 1, z2 - 1, Kit.hangingLantern());
        put(s, x2 - 1, y + h - 1, z1 + 1, Kit.hangingLantern());
        cornice(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, y + h, TRIM_STAIRS);
        return y + h + 1;
    }

    /**
     * A bell tower (campanile): a slender square shaft of render with quoins of dressed stone ({@link #towerShaft}), an open
     * belfry with its bell ({@link #belfry}) and over it a pyramid of tiles with a finial ({@link #PYRAMID}), a small dome
     * as wide as the shaft ({@link #DOMED}: on a short drum when the tower is 7 wide or more) or a terrace behind swallowtail
     * merlons ({@link #TERRACE}). It reads from afar as the mark of the style: keep it clearly higher than the roofs around.
     * Width 5, 7 or 9; with a shaft of {@code height} it rises about height + 12 (pyramid) above {@code y}.
     *
     * @param door the face of its door at the foot, or null
     * @return the layer above its top
     */
    static int campanile(Structure s, int x1, int z1, int y, int width, int height, int top, String door) {
        int b = towerShaft(s, x1, z1, y, width, height, RENDER, true, door);
        int eaves = belfry(s, x1, z1, b, width);
        int cx = x1 + width / 2;
        int cz = z1 + width / 2;
        if (top == PYRAMID) {
            int apex = pyramid(s, x1 - 1, z1 - 1, x1 + width, z1 + width, eaves, TILES);
            return finial(s, cx, apex, cz);
        }
        if (top == DOMED) {
            return dome(s, cx, eaves, cz, width, width >= 7 ? 2 : 0);
        }
        merlons(s, x1, z1, x1 + width - 1, z1, eaves - 1, STONE, STONE_STAIRS, true);
        merlons(s, x1, z1 + width - 1, x1 + width - 1, z1 + width - 1, eaves - 1, STONE, STONE_STAIRS, true);
        merlons(s, x1, z1 + 1, x1, z1 + width - 2, eaves - 1, STONE, STONE_STAIRS, true);
        merlons(s, x1 + width - 1, z1 + 1, x1 + width - 1, z1 + width - 2, eaves - 1, STONE, STONE_STAIRS, true);
        return eaves + 2;
    }

    /**
     * A finial on the top of a pyramid or a dome: a slender rod of copper.
     *
     * @return the layer above it
     */
    static int finial(Structure s, int x, int y, int z) {
        put(s, x, y, z, ROD);
        return y + 1;
    }

    /**
     * A small dome around (cx, cz) of an odd {@code diameter} (5 to 11): with a {@code drum} (layers, 0 for none) first a
     * round drum of render two narrower than the dome from layer {@code y}, with small windows on its four axes, a band of
     * trim on its top and around the band a cornice of upside-down stairs flaring out to the width of the dome; then the
     * dome of the spire material (brick or green copper in a look) from the layer above: a hemisphere stepped with
     * stairs, a hollow shell open to the room below, as high as half its diameter plus one; a finial on its top. Without a
     * drum the dome stands directly on layer {@code y} (on a tower's ceiling).
     *
     * @return the layer above the finial
     */
    static int dome(Structure s, int cx, int y, int cz, int diameter, int drum) {
        int r = (diameter - 1) / 2;
        int layer = y;
        if (drum > 0) {
            int dr = r - 1;
            Kit.tube(s, cx, y, y + drum - 1, cz, diameter - 2, RENDER);
            int w1 = drum >= 3 ? y + drum - 3 : y;
            for (int yy = w1; yy <= y + drum - 2 || yy == w1; yy++) {
                put(s, cx + dr, yy, cz, GLASS);
                put(s, cx - dr, yy, cz, GLASS);
                put(s, cx, yy, cz + dr, GLASS);
                put(s, cx, yy, cz - dr, GLASS);
            }
            Kit.tube(s, cx, y + drum, y + drum, cz, diameter - 2, TRIM);
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (inRound(dx, dz, r) && !Kit.inDisc(dx, dz, diameter - 2)) {
                        put(s, cx + dx, y + drum, cz + dz, roundStair(TRIM_STAIRS, "top", dx, dz, false));
                    }
                }
            }
            layer = y + drum + 1;
        }
        int[] radii = domeRadii(r);
        for (int i = 0; i < radii.length; i++) {
            int ri = radii[i];
            int next = i + 1 < radii.length ? radii[i + 1] : -1;
            for (int dz = -ri; dz <= ri; dz++) {
                for (int dx = -ri; dx <= ri; dx++) {
                    if (!inRound(dx, dz, ri)) {
                        continue;
                    }
                    String state;
                    if (!inRound(dx, dz, next)) {
                        // a step of the dome: stairs rising to the middle
                        state = dx == 0 && dz == 0 ? DOME.block() : roundStair(DOME.stairs(), "bottom", dx, dz, false);
                    } else if (inRound(dx + 1, dz, next) && inRound(dx - 1, dz, next) && inRound(dx, dz + 1, next)
                            && inRound(dx, dz - 1, next)) {
                        // covered on every side: the hollow inside the shell
                        continue;
                    } else {
                        state = DOME.block();
                    }
                    put(s, cx + dx, layer + i, cz + dz, state);
                }
            }
        }
        return finial(s, cx, layer + radii.length, cz);
    }

    /**
     * Whether a column lies in a round plan of the given radius (a single block for 0, nothing below).
     */
    private static boolean inRound(int dx, int dz, int r) {
        return r >= 0 && dx * dx + dz * dz <= r * r + r / 2;
    }

    /**
     * The radii of the layers of a dome of a radius from the bottom up: a quarter circle with two upright layers at its
     * foot, drawing in to a radius of two or less, closed with a single block.
     */
    private static int[] domeRadii(int r) {
        if (r <= 2) {
            return r == 2 ? new int[]{2, 1, 0} : new int[]{1, 0};
        }
        List<Integer> list = new ArrayList<>();
        double edge = (r + 0.5) * (r + 0.5);
        for (int i = 0; edge - (i + 0.5) * (i + 0.5) >= 1; i++) {
            list.add((int) Math.floor(Math.sqrt(edge - (i + 0.5) * (i + 0.5))));
        }
        while (list.get(list.size() - 1) > 2) {
            list.add(list.get(list.size() - 1) - 1);
        }
        list.add(0);
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * A crown of machicolations around the top of a tower or a wall whose outer faces lie on the outline x1..x2, z1..z2: a
     * parapet of {@code block} one block outside the outline at layers {@code y} and {@code y + 1}, carried on corbels of
     * two upside-down stairs of trim (layers y - 1 and y - 2) under every second cell and every corner, so that dark gaps
     * open between them; merlons on the parapet from {@code y + 2} ({@link #merlons}, corners as square posts). One walks
     * inside at layer y; the floor at y - 1 within the outline is the caller's (the top course of the walls).
     *
     * @return the layer above the merlons
     */
    static int machicolations(Structure s, int x1, int z1, int x2, int z2, int y, String block, String stairs, boolean swallowtail) {
        int rx1 = x1 - 1;
        int rz1 = z1 - 1;
        int rx2 = x2 + 1;
        int rz2 = z2 + 1;
        for (int z = rz1; z <= rz2; z++) {
            for (int x = rx1; x <= rx2; x++) {
                if (!onOutline(x, z, rx1, rz1, rx2, rz2)) {
                    continue;
                }
                boolean corner = (x == rx1 || x == rx2) && (z == rz1 || z == rz2);
                int along = z == rz1 || z == rz2 ? x - rx1 : z - rz1;
                if (corner || along % 2 == 0) {
                    String corbel = ringStair(TRIM_STAIRS, "top", x, z, rx1, rz1, rx2, rz2);
                    add(s, x, y - 1, z, corbel);
                    add(s, x, y - 2, z, corbel);
                }
                put(s, x, y, z, block);
                put(s, x, y + 1, z, block);
                if (corner) {
                    put(s, x, y + 2, z, block);
                    if (swallowtail) {
                        put(s, x, y + 3, z, block);
                    }
                }
            }
        }
        merlons(s, rx1 + 1, rz1, rx2 - 1, rz1, y + 2, block, stairs, swallowtail, true);
        merlons(s, rx1 + 1, rz2, rx2 - 1, rz2, y + 2, block, stairs, swallowtail, true);
        merlons(s, rx1, rz1 + 1, rx1, rz2 - 1, y + 2, block, stairs, swallowtail, true);
        merlons(s, rx2, rz1 + 1, rx2, rz2 - 1, y + 2, block, stairs, swallowtail, true);
        return y + (swallowtail ? 4 : 3);
    }

    /**
     * Merlons along a straight line from (x1, z1) to (x2, z2) at layer {@code y} (on the top of a wall or a parapet):
     * pairs of blocks with a crenel between pairs, symmetric from both ends. Swallowtail merlons (the Ghibelline merlons of
     * the south) carry a V of two stairs on each pair, one layer higher; plain merlons are one layer high.
     */
    static void merlons(Structure s, int x1, int z1, int x2, int z2, int y, String block, String stairs, boolean swallowtail) {
        merlons(s, x1, z1, x2, z2, y, block, stairs, swallowtail, false);
    }

    private static void merlons(Structure s, int x1, int z1, int x2, int z2, int y, String block, String stairs, boolean swallowtail,
                                boolean betweenPosts) {
        boolean alongX = z1 == z2;
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        int c = alongX ? z1 : x1;
        int n = a2 - a1 + 1;
        boolean[] merlon = new boolean[n];
        for (int p = 0; p < n; p++) {
            int m = Math.min(p, n - 1 - p);
            merlon[p] = betweenPosts ? m % 3 != 0 : m % 3 != 2;
        }
        String toStart = alongX ? "west" : "north";
        String toEnd = alongX ? "east" : "south";
        for (int p = 0; p < n; p++) {
            if (!merlon[p]) {
                continue;
            }
            at(s, alongX, a1 + p, y, c, block);
            if (!swallowtail) {
                continue;
            }
            boolean left = p > 0 && merlon[p - 1];
            boolean right = p < n - 1 && merlon[p + 1];
            if (!left && right) {
                at(s, alongX, a1 + p, y + 1, c, Kit.stairs(stairs, toStart));
            } else if (left && !right) {
                at(s, alongX, a1 + p, y + 1, c, Kit.stairs(stairs, toEnd));
            }
        }
    }

    // ---------------------------------------------------------------- courts, water and gardens

    /**
     * A fountain around (cx, cz) of an odd {@code diameter} (5 to 11), standing on the paving at layer {@code y - 1}: a round
     * basin whose rim of trim one block high closes water at layer {@code y}, over a floor of trim with sea lanterns under
     * the water (diameter 7 and more); in its middle a column of dressed stone with a carved head carrying an upper bowl of
     * upside-down stairs of trim around a cell of water at {@code y + 3}. The water is closed on every side: it never runs.
     */
    static void fountain(Structure s, int cx, int y, int cz, int diameter) {
        int r = (diameter - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (!Kit.inDisc(dx, dz, diameter)) {
                    continue;
                }
                put(s, cx + dx, y - 1, cz + dz, TRIM);
                put(s, cx + dx, y, cz + dz, Kit.onRim(dx, dz, diameter) ? TRIM : WATER);
            }
        }
        if (diameter >= 7) {
            int q = r - 1;
            for (int[] d : new int[][]{{q, 0}, {-q, 0}, {0, q}, {0, -q}}) {
                put(s, cx + d[0], y - 1, cz + d[1], SEA_LANTERN);
            }
        }
        put(s, cx, y, cz, STONE);
        put(s, cx, y + 1, cz, STONE);
        put(s, cx, y + 2, cz, CARVED);
        put(s, cx, y + 3, cz, WATER);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dz != 0) {
                    put(s, cx + dx, y + 3, cz + dz, roundStair(TRIM_STAIRS, "top", dx, dz, false));
                }
            }
        }
    }

    /**
     * A court over x1..x2, z1..z2 with a fountain in its middle: its paving at layer {@code y - 1} of the rough stone with
     * a border of trim, paths of cotto on its two axes and a ring of cotto around the basin; the {@link #fountain} of the
     * given diameter in the middle, four lamp posts of wrought iron on the diagonals around it and citrus trees in pots in
     * the four corners. One walks at layer y.
     */
    static void courtyard(Structure s, int x1, int z1, int x2, int z2, int y, int fountain) {
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        int g = y - 1;
        s.fill(x1, g, z1, x2, g, z2, PAVING);
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                if (onOutline(x, z, x1, z1, x2, z2)) {
                    put(s, x, g, z, TRIM);
                }
            }
        }
        s.fill(cx - 1, g, z1 + 1, cx + 1, g, z2 - 1, COTTO);
        s.fill(x1 + 1, g, cz - 1, x2 - 1, g, cz + 1, COTTO);
        Kit.disc(s, cx, g, cz, fountain + 4, COTTO);
        fountain(s, cx, y, cz, fountain);
        int q = (fountain - 1) / 2;
        for (int[] d : new int[][]{{q, q}, {-q, q}, {q, -q}, {-q, -q}}) {
            lampPost(s, cx + d[0], y, cz + d[1]);
        }
        citrus(s, x1 + 2, y, z1 + 2, true);
        citrus(s, x2 - 2, y, z1 + 2, true);
        citrus(s, x1 + 2, y, z2 - 2, true);
        citrus(s, x2 - 2, y, z2 - 2, true);
    }

    /**
     * A cypress standing on the ground at layer {@code y - 1}: a short trunk at (x, y, z) and a tall narrow flame of dark
     * leaves {@code height} layers high in all (7 to 12): a single column at its foot (from 8 high), three wide (a column
     * with arms on its four sides) up to the middle, a single column above it to the point.
     */
    static void cypress(Structure s, int x, int y, int z, int height) {
        put(s, x, y, z, TRUNK);
        for (int i = 1; i < height; i++) {
            put(s, x, y + i, z, CYPRESS);
        }
        for (int yy = y + (height >= 8 ? 2 : 1); yy <= y + 1 + height / 2; yy++) {
            for (String side : SIDES) {
                int[] d = Furnish.step(side);
                put(s, x + d[0], yy, z + d[1], CYPRESS);
            }
        }
    }

    /**
     * A citrus (or olive) tree: a short trunk, on a big pot of terracotta when {@code potted}, under a round crown of green
     * leaves three wide and two high with a few twigs in flower. Potted it is four layers high from {@code y}, else three.
     */
    static void citrus(Structure s, int x, int y, int z, boolean potted) {
        int b = y;
        if (potted) {
            put(s, x, y, z, COTTO);
            b++;
        }
        put(s, x, b, z, TRUNK);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                boolean flower = hash(x + dx, z + dz) % 5 == 0;
                put(s, x + dx, b + 1, z + dz, flower ? BLOSSOM : FOLIAGE);
                if (dx == 0 || dz == 0) {
                    put(s, x + dx, b + 2, z + dz, FOLIAGE);
                }
            }
        }
    }

    /**
     * A pergola over x1..x2, z1..z2 (its length the longer side): square piers of dressed stone on both long edges every
     * three blocks and at the ends, {@code height} layers high from {@code y}; on them a beam of the timber along each
     * edge at {@code y + height}, rafters of posts across every second block between the beams, and vines of green leaves
     * with bougainvillea in flower lying on them in an irregular patchwork, a few hanging down beside the piers; lanterns
     * hang under the rafters along the middle.
     */
    static void pergola(Structure s, int x1, int z1, int x2, int z2, int y, int height) {
        boolean alongX = x2 - x1 >= z2 - z1;
        int a1 = alongX ? x1 : z1;
        int a2 = alongX ? x2 : z2;
        int c1 = alongX ? z1 : x1;
        int c2 = alongX ? z2 : x2;
        int top = y + height;
        int mid = (c1 + c2) / 2;
        for (int a = a1; a <= a2; a++) {
            boolean pier = (a - a1) % 3 == 0 || a == a2;
            for (int c : new int[]{c1, c2}) {
                if (pier) {
                    for (int yy = y; yy < top; yy++) {
                        at(s, alongX, a, yy, c, STONE);
                    }
                } else if (hash(a * 3, c) % 3 == 0) {
                    at(s, alongX, a, top - 1, c, hash(a, c) % 2 == 0 ? BLOSSOM : FOLIAGE);
                }
                at(s, alongX, a, top, c, Kit.log(BEAM, alongX ? "x" : "z"));
            }
            if ((a - a1) % 2 == 0) {
                for (int c = c1 + 1; c < c2; c++) {
                    at(s, alongX, a, top, c, FENCE);
                }
            }
            for (int c = c1; c <= c2; c++) {
                int h = hash(a, c * 7 + 1);
                if (h % 10 < 6) {
                    at(s, alongX, a, top + 1, c, h % 10 == 0 ? BLOSSOM : FOLIAGE);
                }
            }
            if ((a - a1) % 4 == 2 && (a - a1) % 2 == 0) {
                at(s, alongX, a, top - 1, mid, Kit.hangingLantern());
            }
        }
    }

    /**
     * A lamp post of wrought iron standing on the ground at layer {@code y - 1}: a foot of trim, a shaft of iron two high and
     * a lantern on it (four layers in all).
     */
    static void lampPost(Structure s, int x, int y, int z) {
        put(s, x, y, z, TRIM);
        put(s, x, y + 1, z, IRON);
        put(s, x, y + 2, z, IRON);
        put(s, x, y + 3, z, Kit.lantern());
    }

    /**
     * A lantern on a bracket of wrought iron on a wall: the lantern hangs at (x, y, z), the cell before the wall, from an
     * arm of iron bars above it that joins the wall (after {@link Structure#connect()}).
     */
    static void wallLantern(Structure s, int x, int y, int z) {
        put(s, x, y + 1, z, IRON);
        put(s, x, y, z, Kit.hangingLantern());
    }

    /**
     * A planter: a big pot of terracotta with a shrub of green leaves, or of bougainvillea in flower.
     */
    static void planter(Structure s, int x, int y, int z, boolean flowering) {
        put(s, x, y, z, COTTO);
        put(s, x, y + 1, z, flowering ? BLOSSOM : FOLIAGE);
    }

    /**
     * An amphora: a decorated pot of clay (it holds nothing).
     */
    static void amphora(Structure s, int x, int y, int z) {
        put(s, x, y, z, "minecraft:decorated_pot[facing=north,waterlogged=false]");
    }

    /**
     * A bench of stone (trim stairs) along x or z, its seat looking the way of {@code facing}.
     */
    static void stoneBench(Structure s, int x1, int z1, int x2, int z2, int y, String facing) {
        s.fill(x1, y, z1, x2, y, z2, Kit.stairs(TRIM_STAIRS, Furnish.opposite(facing)));
    }

    // ---------------------------------------------------------------- furniture

    /**
     * The fire of a southern kitchen or hall against a wall, opening the way of {@code facing}: (x, y, z) the fire on the
     * floor at layer {@code y} before the wall; jambs of dressed stone two high beside it, a mantel shelf of upside-down
     * stairs of trim over them at {@code y + 2}, and over it the hood of plaster (campana): three wide one layer, then the
     * flue against the wall up to layer {@code top}.
     */
    static void hearth(Structure s, int x, int y, int z, String facing, int top) {
        int[] a = Furnish.step(Furnish.left(facing));
        put(s, x, y, z, "minecraft:campfire[facing=" + facing + ",lit=true,signal_fire=false,waterlogged=false]");
        put(s, x, y + 1, z, Structure.AIR);
        for (int side = -1; side <= 1; side += 2) {
            put(s, x + a[0] * side, y, z + a[1] * side, STONE);
            put(s, x + a[0] * side, y + 1, z + a[1] * side, STONE);
        }
        for (int u = -1; u <= 1; u++) {
            put(s, x + a[0] * u, y + 2, z + a[1] * u, Kit.upside(TRIM_STAIRS, Furnish.opposite(facing)));
            put(s, x + a[0] * u, y + 3, z + a[1] * u, RENDER);
        }
        for (int yy = y + 4; yy <= top; yy++) {
            put(s, x, yy, z, RENDER);
        }
    }

    /**
     * A panel of glazed tiles (azulejos) over the box x1..x2, y1..y2, z1..z2 (one layer thick: a wall panel, or a floor when
     * y1 == y2): the tiles turned a quarter from cell to cell so that their pattern closes into rosettes two by two.
     */
    static void tiles(Structure s, int x1, int y1, int z1, int x2, int y2, int z2) {
        String[] turn = {"north", "east", "west", "south"};
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    int u = y1 == y2 ? x : z1 == z2 ? x : z;
                    int v = y1 == y2 ? z : y;
                    put(s, x, y, z, AZULEJO + "[facing=" + turn[(u & 1) + 2 * (v & 1)] + "]");
                }
            }
        }
    }

    /**
     * A kitchen counter along a wall, looking the way of {@code facing}: {@code length} cells from (x, y, z) towards the
     * left of facing; a sink (a cauldron of water) in the first, counters of glazed tiles in the rest with a cutting board,
     * a jug (flower pot) and candles on them, and over all a shelf (closed trapdoors of the furniture wood) at
     * {@code y + 2} with pots on it.
     */
    static void kitchen(Structure s, int x, int y, int z, String facing, int length) {
        int[] a = Furnish.step(Furnish.left(facing));
        for (int i = 0; i < length; i++) {
            int px = x + a[0] * i;
            int pz = z + a[1] * i;
            if (i == 0) {
                put(s, px, y, pz, "minecraft:water_cauldron[level=3]");
            } else {
                put(s, px, y, pz, AZULEJO_BLUE + "[facing=" + facing + "]");
                String on = switch (i % 3) {
                    case 1 -> "minecraft:" + WOOD + "_pressure_plate[powered=false]";
                    case 2 -> POT;
                    default -> Furnish.candles(2);
                };
                put(s, px, y + 1, pz, on);
            }
            put(s, px, y + 2, pz, Kit.trapdoor("minecraft:" + WOOD + "_trapdoor", facing, true));
            if (i % 2 == 1) {
                put(s, px, y + 3, pz, POT);
            }
        }
    }

    /**
     * A bed with a headboard of wrought iron: a real bed (a home: only in houses, the rooms of mages, inns and barracks)
     * whose foot is at (x, y, z) and head towards {@code facing}, iron bars at the head between it and the wall, and night
     * stands with a candle on both sides of the head where there is room.
     */
    static void ironBed(Structure s, int x, int y, int z, String facing, String color) {
        Furnish.bed(s, x, y, z, facing, color);
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        int hx = x + f[0];
        int hz = z + f[1];
        put(s, hx + f[0], y, hz + f[1], IRON);
        for (int side = -1; side <= 1; side += 2) {
            int nx = hx + a[0] * side;
            int nz = hz + a[1] * side;
            if (s.inside(nx, y, nz) && !s.solid(nx, y, nz)) {
                put(s, nx, y, nz, Kit.topSlab("minecraft:" + WOOD + "_slab"));
                put(s, nx, y + 1, nz, Furnish.candles(1));
            }
        }
    }

    /**
     * A fountain on a wall looking the way of {@code facing}: (x, y, z) the cell before the wall on the floor, holding a
     * basin (a cauldron of water) with a spout (a rod) over it from a carved mask in the wall; around the mask a panel of
     * glazed tiles in the wall, a cornice of upside-down stairs of trim over the panel and a flower pot on each side.
     */
    static void wallFountain(Structure s, int x, int y, int z, String facing) {
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        int wx = x - f[0];
        int wz = z - f[1];
        put(s, x, y, z, "minecraft:water_cauldron[level=3]");
        put(s, x, y + 1, z, "minecraft:lightning_rod[facing=" + facing + ",powered=false,waterlogged=false]");
        for (int u = -1; u <= 1; u++) {
            tiles(s, wx + a[0] * u, y + 1, wz + a[1] * u, wx + a[0] * u, y + 2, wz + a[1] * u);
            put(s, x + a[0] * u, y + 3, z + a[1] * u, Kit.upside(TRIM_STAIRS, Furnish.opposite(facing)));
        }
        put(s, wx, y + 1, wz, CARVED);
        put(s, x + a[0], y, z + a[1], POT);
        put(s, x - a[0], y, z - a[1], POT);
    }
}
