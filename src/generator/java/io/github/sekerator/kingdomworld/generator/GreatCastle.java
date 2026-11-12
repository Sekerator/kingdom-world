package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The citadel of a large kingdom: the inner castle that stands in the middle of its capital, the most majestic building of
 * the kingdoms of the world.
 *
 * <p>A high curtain wall with a walk behind corbelled battlements runs all round between four massive round towers at the
 * corners and two smaller ones in the middle of the sides. The gate in the south wall lies deep between two round towers
 * under a balcony hung with the banners of the kingdom. Inside, a courtyard with a fountain, gardens and trees leads to the
 * palace keep: a throne hall built like a cathedral, with a nave and aisles, arcades of columns, a gallery and windows of
 * stained glass, its front between two stair turrets. Behind the hall lie the royal lodgings under steep roofs with
 * bartizans at their corners: the council room, the kitchen, the treasury vault, the library, the royal chamber and the
 * chapel with its steeple. Over everything rises the donjon with a spiral stair, a lookout under its spire and the great
 * flag of the kingdom on top.
 *
 * <p>The throne of the kingdom stands in the world before the castle is built, in the middle of the ground; the castle leaves
 * its place and the ground under it out. Layer 0 replaces the ground, the throne and the street stand on layer 1.
 */
final class GreatCastle {
    private static final int SIZE = 47;
    private static final int HEIGHT = 48;
    /** The throne, in the middle of the ground. */
    private static final int CX = 23;
    private static final int CZ = 23;
    /** The floor of the wall walk; the feet of the guards on it stand one layer higher. */
    private static final int WALK = 8;

    /** The palace: outer walls, the walls between its parts, the floor of its upper storey and the top of its walls. */
    private static final int PAL_X1 = 9;
    private static final int PAL_X2 = 37;
    private static final int PAL_Z1 = 7;
    private static final int PAL_Z2 = 20;
    private static final int PART_W = 17;
    private static final int PART_E = 29;
    private static final int UPPER = 7;
    private static final int PAL_TOP = 12;

    /** The throne hall: the outer walls of the aisles, the arcades, the front wall and the eaves of the nave. */
    private static final int AISLE_W = 16;
    private static final int AISLE_E = 30;
    private static final int ARCADE_W = 19;
    private static final int ARCADE_E = 27;
    private static final int HALL_FRONT = 30;
    private static final int GALLERY = 7;
    private static final int NAVE_EAVES = 20;

    /** The donjon at the back. */
    private static final int DON_X1 = 18;
    private static final int DON_X2 = 28;
    private static final int DON_Z1 = 2;
    private static final int DON_Z2 = 12;
    private static final int DON_TOP = 32;

    private static final String AIR = Structure.AIR;
    private static final String PLANKS = "minecraft:spruce_planks";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String OAK_STAIRS = "minecraft:oak_stairs";
    private static final String OAK_LOG = "minecraft:oak_log";
    private static final String DARK_LOG = "minecraft:dark_oak_log";
    private static final String DOOR = "minecraft:spruce_door";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String BOOKSHELF = "minecraft:bookshelf";
    private static final String GOLD = "minecraft:gold_block";
    private static final String YELLOW = "minecraft:yellow_wool";
    private static final String ROD = "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]";
    private static final String WATER = "minecraft:water[level=0]";
    private static final String TABLE = "minecraft:spruce_slab[type=top,waterlogged=false]";
    private static final String PLATE = "minecraft:oak_pressure_plate[powered=false]";

    private GreatCastle() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("classic_castle_large", "defense", "palace", "minecraft:gold_block", CX, 1, SIZE - 1, castleLarge(),
                "none", 1, "")
                .at("palace_hall", CX - 2, 1, CZ + 3).at("palace_hall", CX + 2, 1, CZ + 3)
                .at("palace_hall", CX - 1, 1, CZ + 5).at("palace_hall", CX + 1, 1, CZ + 5)
                .at("palace_door", CX - 1, 1, HALL_FRONT - 1).at("palace_door", CX + 1, 1, HALL_FRONT - 1)
                .at("castle_gate", CX - 1, 1, 42).at("castle_gate", CX + 1, 1, 42)
                .at("castle_wall", 5, WALK + 1, 15).at("castle_wall", 5, WALK + 1, 31)
                .at("castle_wall", 41, WALK + 1, 15).at("castle_wall", 41, WALK + 1, 31)
                .at("castle_wall", 13, WALK + 1, 5).at("castle_wall", 33, WALK + 1, 5)
                .at("castle_wall", 12, WALK + 1, 41).at("castle_wall", 34, WALK + 1, 41)
                .at("treasury", CX, 1, 17)
                .at("door", CX, 1, SIZE - 1));
        return list;
    }

    /**
     * The citadel of a large kingdom, 47 by 47: the curtain wall with its eight towers and the gatehouse, the courtyard, the
     * palace keep with the throne hall and the royal lodgings, and the donjon. The throne stands in the middle at the north
     * end of the hall on a dais; a red carpet runs from it to the great door, and the way goes on round the fountain to the
     * gate in the south wall.
     */
    static Structure castleLarge() {
        Structure s = new Structure(SIZE, HEIGHT, SIZE);
        ground(s);
        curtain(s);
        palace(s);
        palaceRooms(s);
        throneHall(s);
        hallFront(s);
        donjon(s);
        towers(s);
        gatehouse(s);
        courtyard(s);
        s.skip(CX, 0, CZ).skip(CX, 1, CZ);
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- shapes

    /** The side toward the middle of a round plan from a column of it. */
    private static String inward(int dx, int dz) {
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz > 0 ? "north" : "south";
        }
        return dx > 0 ? "west" : "east";
    }

    private static String opposite(String side) {
        return switch (side) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** A stair on the edge of a ring of a round roof, rising toward the middle; turned stairs where the ring turns. */
    private static String coneStair(int dx, int dz, int width, String stairs) {
        int sx = Integer.signum(dx);
        int sz = Integer.signum(dz);
        boolean outX = sx != 0 && !Kit.inDisc(dx + sx, dz, width);
        boolean outZ = sz != 0 && !Kit.inDisc(dx, dz + sz, width);
        if (outX && outZ) {
            if (sz > 0) {
                return Kit.stairs(stairs, "north", sx > 0 ? "outer_left" : "outer_right");
            }
            return Kit.stairs(stairs, "south", sx < 0 ? "outer_left" : "outer_right");
        }
        if (outZ) {
            return Kit.stairs(stairs, sz > 0 ? "north" : "south");
        }
        if (outX) {
            return Kit.stairs(stairs, sx > 0 ? "west" : "east");
        }
        return Kit.stairs(stairs, inward(dx, dz));
    }

    /**
     * A round spire of slate: rings narrowing by one block on every side, each as many layers high as its rise, the top
     * layer of every ring in stairs and the layers under it in full blocks, solid inside, a gilded finial on the point.
     * Small rises at the eaves and large ones near the point give the flared outline of a witch's hat.
     *
     * @param diameter the width of the eaves
     * @return the layer of the finial
     */
    private static int cone(Structure s, int cx, int y, int cz, int diameter, int... rises) {
        int layer = y;
        int ring = 0;
        for (int width = diameter; width >= 1; width -= 2, ring++) {
            int rise = rises[Math.min(ring, rises.length - 1)];
            int r = (width - 1) / 2;
            for (int step = 0; step < rise; step++) {
                boolean top = step == rise - 1 && width > 1;
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (!Kit.inDisc(dx, dz, width) || !s.inside(cx + dx, layer, cz + dz)) {
                            continue;
                        }
                        boolean edge = !Kit.inDisc(dx, dz, width - 2);
                        s.set(cx + dx, layer, cz + dz, top && edge ? coneStair(dx, dz, width, Kit.SLATE.stairs()) : Kit.SLATE.block());
                    }
                }
                layer++;
            }
        }
        s.set(cx, layer, cz, ROD);
        return layer;
    }

    /** The cells of a round plan that lie outside a smaller one around the same middle, in one layer. */
    private static void band(Structure s, int cx, int y, int cz, int outer, int inner, String state) {
        int r = (outer - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Kit.inDisc(dx, dz, outer) && !Kit.inDisc(dx, dz, inner) && s.inside(cx + dx, y, cz + dz)) {
                    s.set(cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /** A ring of corbels under a projecting parapet or eaves: stairs hanging upside down against the tower. */
    private static void corbels(Structure s, int cx, int y, int cz, int outer, int inner, String stairs) {
        int r = (outer - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Kit.inDisc(dx, dz, outer) && !Kit.inDisc(dx, dz, inner) && s.inside(cx + dx, y, cz + dz)) {
                    s.set(cx + dx, y, cz + dz, Kit.upside(stairs, inward(dx, dz)));
                }
            }
        }
    }

    /**
     * The shaft of a round tower from the ground to {@code top}: a wall of stone on two courses of rough stone with a band
     * of trim at the height of the walk, solid inside except a room from {@code floor} (its floor of planks) to {@code roof}
     * (the layer under its ceiling), lit by a lantern hanging in the middle.
     */
    private static void roundTower(Structure s, int cx, int cz, int diameter, int top, int floor, int roof) {
        int r = (diameter - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (!Kit.inDisc(dx, dz, diameter)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                boolean rim = Kit.onRim(dx, dz, diameter);
                s.set(x, 0, z, Kit.ROUGH);
                for (int y = 1; y <= top; y++) {
                    String state;
                    if (rim) {
                        state = y <= 2 ? Kit.ROUGH : y == WALK ? Kit.TRIM : Kit.WALL;
                    } else if (y == floor || y == roof + 1) {
                        state = PLANKS;
                    } else {
                        state = y > floor && y <= roof ? AIR : Kit.WALL;
                    }
                    s.set(x, y, z, state);
                }
            }
        }
        s.set(cx, roof, cz, Kit.hangingLantern());
    }

    /** A sloping foot of rough stone around a round tower: a course of blocks and a course of slabs. */
    private static void batter(Structure s, int cx, int cz, int diameter) {
        band(s, cx, 0, cz, diameter + 2, diameter, Kit.ROUGH);
        band(s, cx, 1, cz, diameter + 2, diameter, Kit.ROUGH);
        band(s, cx, 2, cz, diameter + 2, diameter, Kit.slab(Kit.ROUGH_SLAB));
    }

    /** A small round turret with a slit of a window, a ring of corbels under it and a cone on top. */
    private static void bartizan(Structure s, int cx, int cz, int bottom, int top, int eaves, int... rises) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dz != 0) && !s.solid(cx + dx, bottom - 1, cz + dz)) {
                    s.set(cx + dx, bottom - 1, cz + dz, Kit.upside(Kit.WALL_STAIRS, inward(dx, dz)));
                }
            }
        }
        s.fill(cx - 1, bottom, cz - 1, cx + 1, top, cz + 1, Kit.WALL);
        s.fill(cx - 1, bottom, cz - 1, cx + 1, bottom, cz + 1, Kit.TRIM);
        s.fill(cx - 1, top, cz - 1, cx + 1, top, cz + 1, Kit.TRIM);
        cone(s, cx, top + 1, cz, eaves, rises);
    }

    /** A lantern on a chain hanging from {@code top} down to {@code y}. */
    private static void chandelier(Structure s, int x, int y, int z, int top) {
        s.fill(x, y + 1, z, x, top, z, Kit.chain());
        s.set(x, y, z, Kit.hangingLantern());
    }

    /** A table of a fence with a pressure plate on it. */
    private static void table(Structure s, int x, int y, int z) {
        s.set(x, y, z, Kit.fence("spruce")).set(x, y + 1, z, PLATE);
    }

    /** A lamp post: a post of wall on a base with a lantern on top. */
    private static void lampPost(Structure s, int x, int z) {
        s.set(x, 1, z, Kit.TRIM).set(x, 2, z, Kit.WALL_FENCE).set(x, 3, z, Kit.WALL_FENCE).set(x, 4, z, Kit.lantern());
    }

    // ---------------------------------------------------------------- ground and curtain

    /**
     * The ground: a lawn outside the walls, a cobbled yard inside them and the road of trim from the gate to the street.
     */
    private static void ground(Structure s) {
        s.fill(0, 0, 0, SIZE - 1, 0, SIZE - 1, GRASS);
        s.fill(7, 0, 7, 39, 0, 38, Kit.ROUGH);
        s.fill(21, 0, 39, 25, 0, SIZE - 1, Kit.ROUGH);
        s.fill(22, 0, 39, 24, 0, SIZE - 1, Kit.TRIM);
    }

    /**
     * The curtain wall: four stretches between the towers, and the gaps in the rails where the stairs from the yard come up.
     */
    private static void curtain(Structure s) {
        curtainRun(s, true, 4, -1, 10, 35);
        curtainRun(s, true, 42, 1, 10, 35);
        curtainRun(s, false, 4, -1, 10, 36);
        curtainRun(s, false, 41, 1, 10, 36);
        s.set(6, WALK + 1, 27, AIR).set(6, WALK + 2, 27, AIR);
        s.set(40, WALK + 1, 27, AIR).set(40, WALK + 2, 27, AIR);
    }

    /**
     * A stretch of the curtain wall, three blocks thick and solid up to the walk: a foot of rough stone sloping out, a band of
     * trim along the inner edge of the walk, a parapet with merlons carried on a row of corbels one block out, a low rail on
     * the inner edge with lanterns on some of its posts, and lanterns in some crenels. The stretch runs along z when
     * {@code alongZ}, along x otherwise; {@code outer} is its outer face and {@code out} the step outwards.
     */
    private static void curtainRun(Structure s, boolean alongZ, int outer, int out, int from, int to) {
        String toWall = alongZ ? (out < 0 ? "east" : "west") : (out < 0 ? "south" : "north");
        int inner = outer - 2 * out;
        int parapet = outer + out;
        for (int t = from; t <= to; t++) {
            for (int c = inner; c != parapet + out; c += out) {
                put(s, alongZ, c, 0, t, Kit.ROUGH);
            }
            for (int c = inner; c != parapet; c += out) {
                for (int y = 1; y <= WALK; y++) {
                    put(s, alongZ, c, y, t, y <= (c == outer ? 2 : 1) ? Kit.ROUGH : Kit.WALL);
                }
            }
            put(s, alongZ, inner, WALK, t, Kit.TRIM);
            put(s, alongZ, parapet, 1, t, Kit.stairs(Kit.ROUGH_STAIRS, toWall));
            put(s, alongZ, parapet, WALK - 1, t, Kit.upside(Kit.WALL_STAIRS, toWall));
            put(s, alongZ, parapet, WALK, t, Kit.WALL);
            put(s, alongZ, parapet, WALK + 1, t, Kit.WALL);
            int x = alongZ ? parapet : t;
            int z = alongZ ? t : parapet;
            if ((x + z) % 2 == 0) {
                put(s, alongZ, parapet, WALK + 2, t, Kit.WALL);
            } else if (t % 6 == 3) {
                put(s, alongZ, parapet, WALK + 2, t, Kit.lantern());
            }
            put(s, alongZ, inner, WALK + 1, t, Kit.WALL_FENCE);
            if (t % 6 == 0) {
                put(s, alongZ, outer, 4, t, AIR);
                put(s, alongZ, outer, 5, t, AIR);
            }
            if (t % 6 == 0) {
                put(s, alongZ, inner, WALK + 2, t, Kit.lantern());
            }
        }
    }

    /** Sets a block of a stretch of wall: across it at {@code c}, along it at {@code t}. */
    private static void put(Structure s, boolean alongZ, int c, int y, int t, String state) {
        if (alongZ) {
            s.set(c, y, t, state);
        } else {
            s.set(t, y, c, state);
        }
    }

    // ---------------------------------------------------------------- the royal lodgings

    /**
     * The royal lodgings behind the hall, against the back wall: two storeys of stone on a course of rough stone with quoins,
     * a band of trim at the upper floor and a cornice under the eaves. Two wings under steep roofs turn their gables to the
     * gardens, a lower roof between them runs from the donjon to the hall, slim bartizans
     * stand on the front corners, the kitchen chimney climbs the west wall and the steeple of the chapel rides the east roof.
     */
    private static void palace(Structure s) {
        s.fill(PAL_X1 + 1, 0, PAL_Z1 + 1, PAL_X2 - 1, 0, PAL_Z2 - 1, PLANKS);
        s.walls(PAL_X1, 0, PAL_Z1, PAL_X2, PAL_TOP, PAL_Z2, Kit.WALL);
        s.walls(PAL_X1, 0, PAL_Z1, PAL_X2, 1, PAL_Z2, Kit.ROUGH);
        s.walls(PAL_X1, UPPER, PAL_Z1, PAL_X2, UPPER, PAL_Z2, Kit.TRIM);
        for (int[] corner : new int[][]{{PAL_X1, PAL_Z1, 1, 1}, {PAL_X2, PAL_Z1, -1, 1}, {PAL_X1, PAL_Z2, 1, -1}, {PAL_X2, PAL_Z2, -1, -1}}) {
            for (int y = 1; y <= PAL_TOP; y++) {
                s.set(corner[0], y, corner[1], Kit.ROUGH);
                if (y % 2 == 0) {
                    s.set(corner[0] + corner[2], y, corner[1], Kit.ROUGH);
                } else {
                    s.set(corner[0], y, corner[1] + corner[3], Kit.ROUGH);
                }
            }
        }
        // The walls between the wings and the middle, the upper floor and the ceilings under the attics.
        s.fill(PART_W, 1, PAL_Z1 + 1, PART_W, PAL_TOP, PAL_Z2 - 1, Kit.WALL);
        s.fill(PART_E, 1, PAL_Z1 + 1, PART_E, PAL_TOP, PAL_Z2 - 1, Kit.WALL);
        s.fill(PAL_X1 + 1, UPPER, PAL_Z1 + 1, PART_W - 1, UPPER, PAL_Z2 - 1, PLANKS);
        s.fill(PART_W + 1, UPPER, DON_Z2 + 1, PART_E - 1, UPPER, PAL_Z2 - 1, PLANKS);
        s.fill(PAL_X1 + 1, PAL_TOP, PAL_Z1 + 1, PART_W - 1, PAL_TOP, PAL_Z2 - 1, PLANKS);
        s.fill(PART_W + 1, PAL_TOP, DON_Z2 + 1, PART_E - 1, PAL_TOP, PAL_Z2 - 1, PLANKS);
        for (int z = PAL_Z1 + 2; z < PAL_Z2; z += 3) {
            s.fill(PAL_X1 + 1, PAL_TOP, z, PART_W - 1, PAL_TOP, z, Kit.log(DARK_LOG, "x"));
        }
        for (int x = PART_W + 2; x < PART_E; x += 3) {
            s.fill(x, PAL_TOP, DON_Z2 + 1, x, PAL_TOP, PAL_Z2 - 1, Kit.log(DARK_LOG, "z"));
        }
        // The cornice under the eaves of the long sides and under the gables.
        s.fill(PAL_X1 - 1, PAL_TOP - 1, PAL_Z1, PAL_X1 - 1, PAL_TOP - 1, PAL_Z2, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.fill(PAL_X2 + 1, PAL_TOP - 1, PAL_Z1, PAL_X2 + 1, PAL_TOP - 1, PAL_Z2, Kit.upside(Kit.TRIM_STAIRS, "west"));
        s.fill(PAL_X1, PAL_TOP, PAL_Z2 + 1, AISLE_W - 2, PAL_TOP, PAL_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.fill(AISLE_E + 2, PAL_TOP, PAL_Z2 + 1, PAL_X2, PAL_TOP, PAL_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        // The roofs: the wings with their gables to the front, the middle between them lower.
        s.gableRoofZ(PAL_X1 - 1, PART_W + 1, PAL_Z1 - 1, PAL_Z2, PAL_TOP, Kit.TILE.stairs(), Kit.TILE.slab(), Kit.WALL, PAL_Z1, PAL_Z2);
        s.gableRoofZ(PART_E - 1, PAL_X2 + 1, PAL_Z1 - 1, PAL_Z2, PAL_TOP, Kit.TILE.stairs(), Kit.TILE.slab(), Kit.WALL, PAL_Z1, PAL_Z2);
        s.gableRoof(PART_W + 1, PART_E - 1, DON_Z2, PAL_Z2, PAL_TOP, Kit.TILE.stairs(), Kit.TILE.slab(), Kit.WALL, PART_W + 1, PART_E - 1);
        // Round windows in the gables of the wings: plain at the back, stained in the front of the chapel.
        for (int x : new int[]{13, 33}) {
            s.set(x, 14, PAL_Z1, Kit.GLASS).set(x, 13, PAL_Z1, Kit.CARVED);
            String pane = x == 13 ? Kit.GLASS : Kit.stainedPane("yellow");
            s.set(x, 14, PAL_Z2, pane).set(x - 1, 14, PAL_Z2, pane).set(x + 1, 14, PAL_Z2, pane);
            s.set(x, 13, PAL_Z2, pane).set(x, 15, PAL_Z2, pane);
            s.set(x, 14, PAL_Z2, x == 13 ? Kit.GLASS : Kit.stainedPane("red"));
        }
        s.set(13, PAL_TOP + 1, 10, Kit.lantern()).set(13, PAL_TOP + 1, 17, Kit.lantern()).set(23, PAL_TOP + 1, 16, Kit.lantern());
        // The kitchen chimney outside the west wall.
        s.fill(PAL_X1 - 2, 1, 10, PAL_X1 - 1, PAL_TOP - 1, 11, Kit.CHIMNEY);
        s.fill(PAL_X1 - 2, PAL_TOP, 10, PAL_X1 - 2, 19, 11, Kit.CHIMNEY);
        s.set(PAL_X1 - 2, 20, 10, Kit.CHIMNEY_POT).set(PAL_X1 - 2, 20, 11, Kit.CHIMNEY_POT);
        // Bartizans on the front corners.
        bartizan(s, PAL_X1, PAL_Z2, UPPER + 1, PAL_TOP + 1, 3, 2, 3);
        bartizan(s, PAL_X2, PAL_Z2, UPPER + 1, PAL_TOP + 1, 3, 2, 3);
        steeple(s);
    }

    /**
     * The steeple of the chapel on the ridge of the east wing: a square stone shaft carried by the timbers of the open roof,
     * a belfry with its bell and a spire of slate.
     */
    private static void steeple(Structure s) {
        int x = 33;
        int z = 14;
        s.fill(x - 1, 16, z - 1, x + 1, 19, z + 1, Kit.WALL);
        s.fill(x - 1, 16, z - 1, x + 1, 16, z + 1, Kit.TRIM);
        for (int y = 17; y <= 18; y++) {
            s.set(x, y, z - 1, AIR).set(x, y, z + 1, AIR).set(x - 1, y, z, AIR).set(x + 1, y, z, AIR);
        }
        s.set(x, 17, z, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        s.set(x, 18, z, Kit.WALL);
        s.fill(x - 1, 19, z - 1, x + 1, 19, z + 1, Kit.TRIM);
        cone(s, x, 20, z, 3, 1, 4);
        // The timbers that carry it: tie beams across the chapel and a king post under the shaft.
        for (int zb = z - 1; zb <= z + 1; zb++) {
            s.fill(PART_E + 1, PAL_TOP, zb, PAL_X2 - 1, PAL_TOP, zb, Kit.log(DARK_LOG, "x"));
        }
        s.fill(x, PAL_TOP + 1, z, x, 15, z, Kit.log(DARK_LOG, "y"));
    }

    /**
     * The rooms of the lodgings. In the west wing the kitchen with its hearth and the council room with its long table below,
     * the library above; in the middle the treasury vault behind a grille with its antechamber below and the royal chamber
     * with its fireplace above, looking into the throne hall; the east wing is the chapel, open to its roof, with an altar
     * under a golden cross, pews, tall windows of stained glass and a balcony for the king.
     */
    private static void palaceRooms(Structure s) {
        int up = UPPER + 1;
        // Doors and windows of the outer walls.
        s.door(13, 1, PAL_Z2, DOOR, "south", "left");
        s.door(33, 1, PAL_Z2, DOOR, "south", "left");
        for (int x : new int[]{13, 33}) {
            s.set(x, 3, PAL_Z2, Kit.CARVED).set(x - 1, 3, PAL_Z2, Kit.upside(Kit.TRIM_STAIRS, "west")).set(x + 1, 3, PAL_Z2, Kit.upside(Kit.TRIM_STAIRS, "east"));
            s.set(x - 1, 2, PAL_Z2 + 1, Kit.wallTorch("south")).set(x + 1, 2, PAL_Z2 + 1, Kit.wallTorch("south"));
        }
        for (int x : new int[]{11, 15}) {
            window(s, x, 2, 4, PAL_Z2, Kit.GLASS);
            window(s, x, up + 1, up + 2, PAL_Z2, Kit.GLASS);
            window(s, x + 20, 2, 6, PAL_Z2, Kit.stainedPane(x == 11 ? "blue" : "purple"));
        }
        for (int z : new int[]{17}) {
            window(s, PAL_X1, 2, 4, z, Kit.GLASS);
        }
        for (int z : new int[]{9, 12, 15, 18}) {
            window(s, PAL_X1, up + 1, up + 2, z, Kit.GLASS);
        }
        String[] chapel = {"blue", "yellow", "purple"};
        for (int i = 0; i < 3; i++) {
            window(s, PAL_X2, 3, 9, 10 + 3 * i, Kit.stainedPane(chapel[i]));
        }
        for (int x : new int[]{11, 15}) {
            window(s, x, up + 1, up + 2, PAL_Z1, Kit.GLASS);
        }
        window(s, 33, 8, 10, PAL_Z1, Kit.stainedPane("yellow"));

        // The kitchen: a hearth of rough stone on the chimney wall, a stove, an oven, barrels and a table.
        s.fill(11, 1, 13, 16, UPPER - 1, 13, Kit.WALL);
        s.door(13, 1, 13, DOOR, "south", "left");
        s.fill(10, 1, 9, 10, UPPER - 1, 12, Kit.ROUGH);
        s.fill(10, 1, 10, 10, 2, 11, AIR);
        s.set(10, 1, 10, "minecraft:campfire[facing=east,lit=true,signal_fire=false,waterlogged=false]");
        s.set(10, 1, 11, "minecraft:campfire[facing=east,lit=true,signal_fire=false,waterlogged=false]");
        s.set(10, 3, 10, Kit.upside(Kit.TRIM_STAIRS, "west")).set(10, 3, 11, Kit.upside(Kit.TRIM_STAIRS, "west"));
        s.set(11, 1, 8, "minecraft:smoker[facing=south,lit=false]").set(12, 1, 8, "minecraft:furnace[facing=south,lit=false]");
        s.set(13, 1, 8, "minecraft:crafting_table").set(14, 1, 8, Kit.barrel()).set(15, 1, 8, Kit.barrel()).set(16, 1, 8, Kit.barrel());
        s.set(16, 2, 8, Kit.barrel()).set(16, 1, 9, "minecraft:water_cauldron[level=3]");
        s.set(13, 1, 10, TABLE).set(14, 1, 10, TABLE).set(13, 1, 11, TABLE).set(14, 1, 11, TABLE);
        s.set(14, 2, 10, "minecraft:cake[bites=0]");
        s.set(16, 1, 12, "minecraft:hay_block[axis=y]").set(11, 1, 12, Kit.barrel());
        s.set(13, UPPER - 1, 10, Kit.hangingLantern());
        // The opening into the donjon.
        s.door(PART_W, 1, 10, DOOR, "east", "left");

        // The council room: the long table with chairs, banners and the stairs up to the library.
        for (int k = 0; k < UPPER; k++) {
            int z = 19 - k;
            if (k > 0) {
                s.fill(10, 1, z, 10, k, z, Kit.WALL);
            }
            s.set(10, 1 + k, z, Kit.stairs(SPRUCE_STAIRS, "north"));
        }
        s.set(10, UPPER, 14, AIR).set(10, UPPER, 15, AIR);
        s.set(11, up, 13, Kit.fence("spruce")).set(11, up, 14, Kit.fence("spruce")).set(11, up, 15, Kit.fence("spruce"));
        s.set(11, up, 16, Kit.fence("spruce")).set(10, up, 16, Kit.fence("spruce"));
        for (int x = 12; x <= 14; x++) {
            s.set(x, 1, 16, TABLE).set(x, 1, 17, TABLE);
            s.set(x, 1, 15, Kit.stairs(SPRUCE_STAIRS, "north")).set(x, 1, 18, Kit.stairs(SPRUCE_STAIRS, "south"));
        }
        s.set(15, 1, 16, Kit.stairs(SPRUCE_STAIRS, "east")).set(15, 1, 17, Kit.stairs(SPRUCE_STAIRS, "east"));
        s.set(13, 2, 16, "minecraft:candle[candles=3,lit=true,waterlogged=false]");
        s.set(13, 2, 17, "minecraft:candle[candles=2,lit=true,waterlogged=false]");
        s.set(16, 4, 15, Kit.wallBanner("red", "west")).set(16, 4, 17, Kit.wallBanner("red", "west"));
        s.fill(14, 1, 14, 16, 3, 14, BOOKSHELF);
        s.set(16, 1, 19, "minecraft:lectern[facing=west,has_book=false,powered=false]");
        s.set(13, UPPER - 1, 16, Kit.hangingLantern()).set(12, UPPER - 1, 19, Kit.hangingLantern());
        s.door(PART_W, 1, 18, DOOR, "east", "left");

        // The antechamber of the treasury and the vault behind its grille.
        s.fill(PART_W + 1, 0, DON_Z2 + 1, PART_E - 1, 0, PAL_Z2 - 1, Kit.TRIM);
        s.fill(PART_W + 1, 1, 16, PART_E - 1, UPPER - 1, 16, Kit.BARS);
        s.door(CX, 1, 16, DOOR, "south", "left");
        s.fill(PART_W + 1, 1, 18, PART_E - 1, 1, 18, Kit.CLOTH_CARPET);
        s.set(PART_W + 1, 1, 17, "minecraft:lectern[facing=east,has_book=false,powered=false]");
        s.set(PART_E - 1, 1, 17, "minecraft:lectern[facing=west,has_book=false,powered=false]");
        for (int x = PART_W + 1; x <= PART_E - 1; x++) {
            boolean gold = x == PART_W + 1 || x == PART_E - 1 || x == CX;
            s.set(x, 1, DON_Z2 + 1, gold ? GOLD : x % 2 == 0 ? Kit.chest("south") : Kit.barrel());
        }
        s.set(CX, 2, DON_Z2 + 1, "minecraft:raw_gold_block").set(PART_W + 1, 2, DON_Z2 + 1, Kit.barrel()).set(PART_E - 1, 2, DON_Z2 + 1, Kit.barrel());
        s.set(PART_W + 1, 1, 14, Kit.chest("east")).set(PART_E - 1, 1, 14, Kit.chest("west"));
        s.set(PART_W + 1, 1, 15, GOLD).set(PART_E - 1, 1, 15, GOLD).set(PART_W + 1, 2, 15, "minecraft:emerald_block");
        s.set(20, UPPER - 1, 14, Kit.hangingLantern()).set(26, UPPER - 1, 14, Kit.hangingLantern());
        s.set(20, UPPER - 1, 18, Kit.hangingLantern()).set(26, UPPER - 1, 18, Kit.hangingLantern());
        s.door(PART_W + 1, 1, PAL_Z2, DOOR, "south", "left").door(PART_E - 1, 1, PAL_Z2, DOOR, "south", "right");
        s.door(PART_E, 1, 18, DOOR, "west", "left");

        // The library over the kitchen and the council room.
        s.fill(11, up, PAL_Z1 + 1, 16, up + 2, PAL_Z1 + 1, BOOKSHELF);
        s.fill(10, up, PAL_Z1 + 1, 10, up + 2, 12, BOOKSHELF);
        s.fill(16, up, 9, 16, up + 2, 14, BOOKSHELF);
        s.fill(16, up, 18, 16, up + 2, 19, BOOKSHELF);
        s.set(13, up, 11, "minecraft:enchanting_table");
        s.set(12, up, 11, "minecraft:lectern[facing=east,has_book=false,powered=false]");
        for (int z : new int[]{17, 18}) {
            s.set(13, up, z, TABLE).set(12, up, z, Kit.stairs(SPRUCE_STAIRS, "west")).set(14, up, z, Kit.stairs(SPRUCE_STAIRS, "east"));
        }
        s.set(13, up + 1, 17, Kit.lantern());
        s.set(13, PAL_TOP - 1, 9, Kit.hangingLantern()).set(13, PAL_TOP - 1, 14, Kit.hangingLantern());
        s.door(PART_W, up, 16, DOOR, "east", "left");

        // The royal chamber: a carpet, a fireplace against the donjon, armchairs, a desk, chests, windows into the hall.
        s.fill(20, up, 15, 26, up, 18, Kit.CLOTH_CARPET);
        s.set(22, up, DON_Z2 + 1, Kit.ROUGH).set(22, up + 1, DON_Z2 + 1, Kit.ROUGH).set(24, up, DON_Z2 + 1, Kit.ROUGH).set(24, up + 1, DON_Z2 + 1, Kit.ROUGH);
        s.set(CX, up, DON_Z2 + 1, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        s.fill(21, up + 2, DON_Z2 + 1, 25, up + 2, DON_Z2 + 1, Kit.TRIM);
        s.set(21, up + 2, DON_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "east")).set(25, up + 2, DON_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "west"));
        s.set(22, up, 15, Kit.stairs(OAK_STAIRS, "south")).set(24, up, 15, Kit.stairs(OAK_STAIRS, "south"));
        s.set(19, up, 18, TABLE).set(19, up, 17, Kit.stairs(OAK_STAIRS, "north"));
        s.set(19, up + 1, 18, "minecraft:candle[candles=2,lit=true,waterlogged=false]");
        s.set(PART_W + 1, up, DON_Z2 + 1, Kit.chest("south")).set(PART_E - 1, up, DON_Z2 + 1, Kit.barrel()).set(PART_E - 1, up + 1, DON_Z2 + 1, Kit.barrel());
        s.set(PART_E - 1, up, 14, BOOKSHELF).set(PART_E - 1, up + 1, 14, BOOKSHELF).set(PART_W + 1, up, 14, Kit.chest("east"));
        for (int x : new int[]{20, 26}) {
            s.set(x, up + 1, PAL_Z2, Kit.GLASS).set(x, up + 2, PAL_Z2, Kit.GLASS);
        }
        s.set(20, PAL_TOP - 1, 16, Kit.hangingLantern()).set(26, PAL_TOP - 1, 16, Kit.hangingLantern()).set(CX, PAL_TOP - 1, 18, Kit.hangingLantern());
        s.door(PART_W + 1, up, PAL_Z2, DOOR, "south", "left").door(PART_E - 1, up, PAL_Z2, DOOR, "south", "right");
        s.door(PART_E, up, 16, DOOR, "east", "left");

        // The chapel: a floor of trim, the altar under a golden cross, pews on both sides of a carpet, chandeliers, and the
        // king's balcony on brackets by the door from his chamber.
        s.fill(PART_E + 1, 0, PAL_Z1 + 1, PAL_X2 - 1, 0, PAL_Z2 - 1, Kit.TRIM);
        s.fill(31, 0, 8, 35, 0, 10, Kit.CARVED);
        s.set(32, 1, 9, Kit.TRIM).set(34, 1, 9, Kit.TRIM).set(33, 1, 9, Kit.CARVED);
        s.set(32, 2, 9, "minecraft:candle[candles=4,lit=true,waterlogged=false]").set(34, 2, 9, "minecraft:candle[candles=4,lit=true,waterlogged=false]");
        s.fill(33, 3, 8, 33, 6, 8, GOLD).set(32, 5, 8, GOLD).set(34, 5, 8, GOLD);
        s.fill(33, 1, 11, 33, 1, PAL_Z2 - 1, Kit.CLOTH_CARPET);
        for (int z = 12; z <= 16; z += 2) {
            for (int x : new int[]{30, 31, 32, 34, 35, 36}) {
                s.set(x, 1, z, Kit.stairs(SPRUCE_STAIRS, "south"));
            }
        }
        s.set(30, 1, 9, "minecraft:lectern[facing=east,has_book=false,powered=false]");
        for (int z : new int[]{11, 17}) {
            chandelier(s, 33, 8, z, 16);
        }
        s.set(30, 3, 10, Kit.wallTorch("east")).set(36, 3, 18, Kit.wallTorch("west")).set(30, 3, 18, Kit.wallTorch("east"));
        s.fill(30, UPPER, 15, 30, UPPER, 17, PLANKS);
        for (int z = 15; z <= 17; z++) {
            s.set(30, UPPER - 1, z, Kit.upside(SPRUCE_STAIRS, "west"));
            s.set(31, up, z, Kit.fence("spruce"));
        }
        s.set(30, up, 14, Kit.fence("spruce")).set(30, up, 18, Kit.fence("spruce"));
        s.set(31, up, 15, Kit.fence("spruce"));
        s.set(31, up + 1, 15, Kit.lantern());
    }

    /**
     * A tall window from {@code y1} to {@code y2} in a wall one block thick: panes in the opening, a sill of trim below and
     * a carved head above.
     */
    private static void window(Structure s, int x, int y1, int y2, int z, String pane) {
        s.fill(x, y1, z, x, y2, z, pane);
        s.set(x, y2 + 1, z, Kit.CARVED);
        s.set(x, y1 - 1, z, Kit.TRIM);
    }

    // ---------------------------------------------------------------- the throne hall

    /**
     * The throne hall, built like a cathedral: a nave seven blocks wide and twenty high under a steep roof of tiles, two
     * aisles under lean-to roofs, arcades of carved columns with trim capitals and arches, a gallery over the aisles behind a
     * balustrade, clerestory windows of stained glass, buttresses with pinnacles outside. At the north end the throne
     * stands on a dais of trim before a tapestry of the kingdom with its crown, under a rose window; a red carpet runs from
     * it to the great door, chandeliers hang from the roof.
     */
    private static void throneHall(Structure s) {
        // Floors: trim in the nave, stone in the aisles; the walls on a footing of rough stone.
        s.fill(AISLE_W, 0, PAL_Z2 + 1, AISLE_E, 0, HALL_FRONT, Kit.ROUGH);
        s.fill(AISLE_W + 1, 0, PAL_Z2 + 1, AISLE_E - 1, 0, HALL_FRONT - 1, Kit.WALL);
        s.fill(ARCADE_W + 1, 0, PAL_Z2 + 1, ARCADE_E - 1, 0, HALL_FRONT - 1, Kit.TRIM);
        for (int x = ARCADE_W + 1; x <= ARCADE_E - 1; x++) {
            for (int z = CZ + 2; z < HALL_FRONT; z++) {
                if ((x + z) % 2 == 0) {
                    s.set(x, 0, z, "minecraft:calcite");
                }
            }
        }
        s.fill(CX - 2, 0, PAL_Z2 + 1, CX + 2, 0, CZ + 1, Kit.CARVED);
        s.fill(ARCADE_W, PAL_TOP + 1, PAL_Z2, ARCADE_E, NAVE_EAVES, PAL_Z2, Kit.WALL);
        for (int side = -1; side <= 1; side += 2) {
            int outer = side < 0 ? AISLE_W : AISLE_E;
            int arcade = side < 0 ? ARCADE_W : ARCADE_E;
            String in = side < 0 ? "east" : "west";
            String out = opposite(in);
            // The outer wall of the aisle and its gallery, with windows in pairs.
            s.fill(outer, 1, PAL_Z2 + 1, outer, PAL_TOP, HALL_FRONT - 1, Kit.WALL);
            s.fill(outer, 1, PAL_Z2 + 1, outer, 1, HALL_FRONT - 1, Kit.ROUGH);
            s.fill(outer, GALLERY, PAL_Z2 + 1, outer, GALLERY, HALL_FRONT - 1, Kit.TRIM);
            for (int z : new int[]{23, 24, 26, 27}) {
                s.fill(outer, 2, z, outer, 4, z, Kit.GLASS);
                s.fill(outer, GALLERY + 2, z, outer, GALLERY + 3, z, Kit.GLASS);
            }
            // The lean-to roof over the aisle and the gallery.
            for (int k = 0; k <= 3; k++) {
                int x = outer + side - k * side;
                s.fill(x, PAL_TOP + k, PAL_Z2 + 1, x, PAL_TOP + k, HALL_FRONT - 1, Kit.stairs(Kit.TILE.stairs(), in));
            }
            // The gallery floor and the arcade: columns, arches, the balustrade of the gallery and the clerestory.
            s.fill(outer - side, GALLERY, PAL_Z2 + 1, arcade + side, GALLERY, HALL_FRONT - 1, PLANKS);
            s.fill(arcade, 1, PAL_Z2 + 1, arcade, NAVE_EAVES, HALL_FRONT - 1, Kit.WALL);
            s.fill(arcade, GALLERY, PAL_Z2 + 1, arcade, GALLERY, HALL_FRONT - 1, Kit.TRIM);
            s.fill(arcade, 15, PAL_Z2 + 1, arcade, 15, HALL_FRONT - 1, Kit.TRIM);
            for (int z : new int[]{22, 25, 28}) {
                s.set(arcade, 1, z, Kit.TRIM).fill(arcade, 2, z, arcade, 4, z, Kit.CARVED).set(arcade, 5, z, Kit.TRIM);
                s.set(arcade, 6, z, Kit.TRIM);
                s.set(arcade + side, 3, z, Kit.wallTorch(out));
                s.set(arcade - side, 3, z, Kit.wallTorch(in));
                // A buttress with a pinnacle outside, against the aisle wall.
                s.fill(outer + side, 0, z, outer + side, PAL_TOP + 1, z, Kit.ROUGH);
                s.fill(outer + 2 * side, 0, z, outer + 2 * side, 4, z, Kit.ROUGH);
                s.set(outer + 2 * side, 5, z, Kit.stairs(Kit.ROUGH_STAIRS, in));
                s.set(outer + side, PAL_TOP + 2, z, Kit.CARVED).set(outer + side, PAL_TOP + 3, z, Kit.WALL_FENCE);
                s.set(outer + side, PAL_TOP + 4, z, ROD);
                s.set(outer - side, 4, z, Kit.wallTorch(in)).set(outer - side, GALLERY + 3, z, Kit.wallTorch(in));
            }
            for (int[] bay : new int[][]{{23, 24}, {26, 27}}) {
                for (int z = bay[0]; z <= bay[1]; z++) {
                    s.fill(arcade, 1, z, arcade, 5, z, AIR);
                    s.set(arcade, 6, z, Kit.upside(Kit.WALL_STAIRS, z == bay[0] ? "north" : "south"));
                    s.set(arcade, GALLERY + 1, z, Kit.WALL_FENCE);
                    s.fill(arcade, GALLERY + 2, z, arcade, GALLERY + 3, z, AIR);
                    s.set(arcade, GALLERY + 4, z, Kit.upside(Kit.WALL_STAIRS, z == bay[0] ? "north" : "south"));
                    s.fill(arcade, 16, z, arcade, 18, z, Kit.stainedPane(z % 3 == 0 ? "blue" : "light_blue"));
                    // Benches against the aisle wall.
                    s.set(outer - side, 1, z, Kit.stairs(SPRUCE_STAIRS, out));
                }
            }
            s.fill(arcade + side, NAVE_EAVES - 1, PAL_Z2 + 1, arcade + side, NAVE_EAVES - 1, HALL_FRONT, Kit.upside(Kit.TRIM_STAIRS, in));
            // Patches of wall where the roofs of the aisle and the lodgings meet the hall.
            for (int x = outer; x != arcade; x -= side) {
                for (int y = PAL_TOP; y <= PAL_TOP + Math.abs(x - outer - side); y++) {
                    if (!s.solid(x, y, PAL_Z2)) {
                        s.set(x, y, PAL_Z2, Kit.WALL);
                    }
                }
            }
            s.set(arcade - side, GALLERY + 4, 21, Kit.wallTorch("south"));
        }
        // The roof of the nave with its gables, the back one over the lodgings.
        s.gableRoofZ(ARCADE_W - 1, ARCADE_E + 1, PAL_Z2, HALL_FRONT + 1, NAVE_EAVES, Kit.TILE.stairs(), Kit.TILE.slab(), Kit.WALL, PAL_Z2, HALL_FRONT);
        // The throne wall: a tapestry of the kingdom with its crown behind the throne, a canopy over it, banners, and the rose
        // window high above, over the roof of the lodgings.
        s.fill(CX - 1, 3, PAL_Z2 + 1, CX + 1, 9, PAL_Z2 + 1, Kit.CLOTH);
        s.set(CX - 1, 7, PAL_Z2 + 1, YELLOW).set(CX, 7, PAL_Z2 + 1, YELLOW).set(CX + 1, 7, PAL_Z2 + 1, YELLOW);
        s.set(CX - 1, 8, PAL_Z2 + 1, YELLOW).set(CX + 1, 8, PAL_Z2 + 1, YELLOW).set(CX, 8, PAL_Z2 + 1, GOLD);
        s.fill(CX - 2, 10, PAL_Z2 + 1, CX + 2, 10, PAL_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(CX, 10, PAL_Z2 + 1, Kit.CARVED).set(CX, 11, PAL_Z2 + 1, Kit.slab(Kit.TRIM_SLAB));
        s.set(ARCADE_W + 1, 6, PAL_Z2 + 1, Kit.wallBanner("red", "south")).set(ARCADE_E - 1, 6, PAL_Z2 + 1, Kit.wallBanner("red", "south"));
        rose(s, CX, 19, PAL_Z2);
        // The dais: slabs of trim around and behind the throne, a step before it, banners and candlesticks on either side.
        s.fill(CX - 2, 1, PAL_Z2 + 1, CX + 2, 1, CZ, Kit.slab(Kit.TRIM_SLAB));
        for (int x = CX - 2; x <= CX + 2; x++) {
            s.set(x, 1, CZ + 1, x == CX ? Kit.CLOTH_CARPET : Kit.stairs(Kit.TRIM_STAIRS, "north"));
        }
        s.set(ARCADE_W + 1, 1, CZ - 1, Kit.banner("red", 0)).set(ARCADE_E - 1, 1, CZ - 1, Kit.banner("red", 0));
        s.set(ARCADE_W + 1, 1, CZ + 1, Kit.CARVED).set(ARCADE_W + 1, 2, CZ + 1, Kit.lantern());
        s.set(ARCADE_E - 1, 1, CZ + 1, Kit.CARVED).set(ARCADE_E - 1, 2, CZ + 1, Kit.lantern());
        s.fill(CX, 1, CZ + 2, CX, 1, HALL_FRONT + 1, Kit.CLOTH_CARPET);
        // Chandeliers from the roof.
        for (int z : new int[]{24, 27}) {
            chandelier(s, CX, 10, z, NAVE_EAVES + 4);
            chandelier(s, CX - 2, 11, z, NAVE_EAVES + 2);
            chandelier(s, CX + 2, 11, z, NAVE_EAVES + 2);
        }
        chandelier(s, CX, 13, PAL_Z2 + 2, NAVE_EAVES + 4);
    }

    /**
     * The front of the hall to the courtyard, like the west front of a cathedral: the gable of the nave with a rose window
     * between two round stair turrets under tall spires, and before it a portal of two arches, the outer one taller under
     * a gablet, lit by torches. The turrets hold spiral stairs from the aisles up to the gallery.
     */
    private static void hallFront(Structure s) {
        s.fill(CX - 2, 1, HALL_FRONT, CX + 2, NAVE_EAVES, HALL_FRONT, Kit.WALL);
        s.fill(CX - 2, 1, HALL_FRONT, CX + 2, 1, HALL_FRONT, Kit.ROUGH);
        s.fill(CX - 2, GALLERY, HALL_FRONT, CX + 2, GALLERY, HALL_FRONT, Kit.TRIM);
        // The inner arch in the wall of the hall.
        s.fill(CX - 1, 1, HALL_FRONT, CX + 1, 4, HALL_FRONT, AIR);
        s.set(CX, 5, HALL_FRONT, AIR);
        s.set(CX - 1, 5, HALL_FRONT, Kit.upside(Kit.WALL_STAIRS, "west")).set(CX + 1, 5, HALL_FRONT, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(CX, 6, HALL_FRONT, Kit.CARVED);
        // The outer arch, taller, between jambs of rough stone, under a gablet of trim.
        int f = HALL_FRONT + 1;
        s.fill(CX - 2, 0, f, CX + 2, 0, f, Kit.TRIM);
        s.fill(CX - 2, 1, f, CX - 2, 7, f, Kit.ROUGH).fill(CX + 2, 1, f, CX + 2, 7, f, Kit.ROUGH);
        s.set(CX - 1, 6, f, Kit.upside(Kit.WALL_STAIRS, "west")).set(CX + 1, 6, f, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(CX - 1, 7, f, Kit.WALL).set(CX + 1, 7, f, Kit.WALL).set(CX, 7, f, Kit.CARVED);
        s.set(CX - 2, 8, f, Kit.stairs(Kit.TRIM_STAIRS, "east")).set(CX + 2, 8, f, Kit.stairs(Kit.TRIM_STAIRS, "west"));
        s.fill(CX - 1, 8, f, CX + 1, 8, f, Kit.WALL);
        s.set(CX - 1, 9, f, Kit.stairs(Kit.TRIM_STAIRS, "east")).set(CX + 1, 9, f, Kit.stairs(Kit.TRIM_STAIRS, "west"));
        s.set(CX, 9, f, Kit.WALL).set(CX, 10, f, Kit.TRIM).set(CX, 11, f, ROD);
        s.set(CX - 2, 4, f + 1, Kit.wallTorch("south")).set(CX + 2, 4, f + 1, Kit.wallTorch("south"));
        s.set(CX - 3, 5, f + 2, Kit.wallTorch("south")).set(CX + 3, 5, f + 2, Kit.wallTorch("south"));
        // The rose window of the front and a lancet over it.
        rose(s, CX, 13, HALL_FRONT);
        s.fill(CX, 17, HALL_FRONT, CX, 19, HALL_FRONT, Kit.stainedPane("light_blue"));
        s.set(CX, 16, HALL_FRONT, Kit.TRIM).set(CX, 20, HALL_FRONT, Kit.CARVED);
        s.set(CX, NAVE_EAVES + 2, HALL_FRONT, Kit.GLASS);
        // The stair turrets.
        stairTurret(s, 18, -1);
        stairTurret(s, 28, 1);
    }

    /**
     * A round stair turret of five at a front corner of the nave: a spiral stair round a solid core from the door in the end
     * of the aisle up to the door of the gallery, solid above, windows outside, a ring of corbels and a spire.
     *
     * @param mirror -1 for the west turret, 1 for the east one, which is its mirror
     */
    private static void stairTurret(Structure s, int cx, int mirror) {
        int cz = HALL_FRONT + 1;
        int top = NAVE_EAVES;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (!Kit.inDisc(dx, dz, 5)) {
                    continue;
                }
                s.set(cx + dx, 0, cz + dz, Kit.ROUGH);
                for (int y = 1; y <= top; y++) {
                    boolean rim = Kit.onRim(dx, dz, 5);
                    s.set(cx + dx, y, cz + dz, !rim && y < 11 && (dx != 0 || dz != 0) ? AIR
                            : rim && (y <= 2 || y == GALLERY || y == 15) ? (y <= 2 ? Kit.ROUGH : Kit.TRIM) : Kit.WALL);
                }
            }
        }
        // The steps: up from the door of the aisle round the core to the door of the gallery, each with stone under it.
        int[][] steps = {{-1, -1}, {-1, 0}, {-1, 1}, {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}};
        for (int i = 0; i < steps.length; i++) {
            int x = cx - mirror * steps[i][0];
            int z = cz + steps[i][1];
            int h = i + 1;
            String facing;
            if (i == steps.length - 1) {
                facing = "north";
            } else {
                int dx = -mirror * (steps[i + 1][0] - steps[i][0]);
                int dz = steps[i + 1][1] - steps[i][1];
                facing = dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
            }
            int under = i == steps.length - 1 ? 3 : 1;
            if (h > under) {
                s.fill(x, under, z, x, h - 1, z, Kit.WALL);
            }
            s.set(x, h, z, Kit.stairs(SPRUCE_STAIRS, facing));
            s.fill(x, h + 3, z, x, top, z, Kit.WALL);
        }
        s.door(cx, 1, cz - 2, DOOR, "north", "left");
        s.door(cx, GALLERY + 1, cz - 2, DOOR, "north", "left");
        s.set(cx + mirror, 4, cz, Kit.wallTorch(mirror < 0 ? "west" : "east"));
        s.set(cx - mirror, 8, cz, Kit.wallTorch(mirror < 0 ? "east" : "west"));
        // Windows to the courtyard and the side.
        s.set(cx, 5, cz + 2, Kit.GLASS).set(cx, 6, cz + 2, Kit.GLASS);
        s.set(cx, 13, cz + 2, Kit.GLASS).set(cx, 14, cz + 2, Kit.GLASS);
        s.set(cx + 2 * mirror, 13, cz, Kit.GLASS).set(cx + 2 * mirror, 14, cz, Kit.GLASS);
        corbels(s, cx, top, cz, 7, 5, Kit.WALL_STAIRS);
        cone(s, cx, top + 1, cz, 7, 1, 3, 3, 4);
    }

    /** A round window of stained glass in a wall along x: a ring of red and blue around a yellow heart. */
    private static void rose(Structure s, int cx, int cy, int z) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (Kit.inDisc(dx, dy, 5)) {
                    String color = dx == 0 && dy == 0 ? "yellow" : Math.abs(dx) + Math.abs(dy) == 1 ? "orange" : (dx + dy) % 2 == 0 ? "red" : "blue";
                    s.set(cx + dx, cy + dy, z, Kit.stainedPane(color));
                }
            }
        }
    }

    // ---------------------------------------------------------------- the donjon

    /**
     * The donjon, the tower of the kingdom, rising at the back over everything: a square stone tower on a sloping foot with
     * quoins of rough stone and a band of trim at every floor. A spiral stair of stone winds round a solid core from the
     * cellar past the guard room on the wall walk, the armoury and the study to a platform behind corbelled battlements with
     * a bartizan on every corner. On the platform an open lookout of arches carries a tall spire of slate with the flagpole
     * and the great flag of the kingdom.
     */
    private static void donjon(Structure s) {
        int mx = CX;
        int mz = 7;
        s.fill(DON_X1 - 1, 0, DON_Z1 - 1, DON_X2 + 1, 0, DON_Z2, Kit.ROUGH);
        s.fill(DON_X1 - 1, 1, DON_Z1 - 1, DON_X2 + 1, 1, DON_Z1 - 1, Kit.stairs(Kit.ROUGH_STAIRS, "south"));
        s.walls(DON_X1, 1, DON_Z1, DON_X2, DON_TOP, DON_Z2, Kit.WALL);
        s.walls(DON_X1, 1, DON_Z1, DON_X2, 2, DON_Z2, Kit.ROUGH);
        for (int y : new int[]{8, 16, 24}) {
            s.walls(DON_X1, y, DON_Z1, DON_X2, y, DON_Z2, Kit.TRIM);
        }
        for (int[] corner : new int[][]{{DON_X1, DON_Z1, 1, 1}, {DON_X2, DON_Z1, -1, 1}, {DON_X1, DON_Z2, 1, -1}, {DON_X2, DON_Z2, -1, -1}}) {
            for (int y = 1; y < DON_TOP; y++) {
                s.set(corner[0], y, corner[1], Kit.ROUGH);
                s.set(corner[0] + (y % 2 == 0 ? corner[2] : 0), y, corner[1] + (y % 2 == 0 ? 0 : corner[3]), Kit.ROUGH);
            }
        }
        // Inside: the floors, the core and the spiral stair through holes in the floors.
        s.fill(DON_X1 + 1, 1, DON_Z1 + 1, DON_X2 - 1, DON_TOP - 1, DON_Z2 - 1, AIR);
        s.fill(DON_X1 + 1, 0, DON_Z1 + 1, DON_X2 - 1, 0, DON_Z2 - 1, Kit.TRIM);
        for (int y : new int[]{8, 16, 24}) {
            s.fill(DON_X1 + 1, y, DON_Z1 + 1, DON_X2 - 1, y, DON_Z2 - 1, PLANKS);
        }
        s.fill(DON_X1, DON_TOP, DON_Z1, DON_X2, DON_TOP, DON_Z2, Kit.WALL);
        s.fill(mx, 1, mz, mx, DON_TOP, mz, Kit.WALL);
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        for (int y : new int[]{8, 16, 24, DON_TOP}) {
            for (int[] c : ring) {
                s.set(mx + c[0], y, mz + c[1], AIR);
            }
        }
        for (int i = 0; i < DON_TOP; i++) {
            int[] c = ring[i % 8];
            int[] n = ring[(i + 1) % 8];
            int dx = n[0] - c[0];
            int dz = n[1] - c[1];
            String facing = dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
            s.set(mx + c[0], 1 + i, mz + c[1], Kit.stairs(Kit.WALL_STAIRS, facing));
        }
        // Doors: from the kitchen into the cellar, and the wall walk through the guard room.
        s.set(DON_X1, 1, 10, AIR).set(DON_X1, 2, 10, AIR);
        s.door(DON_X1, WALK + 1, 5, DOOR, "east", "left").door(DON_X2, WALK + 1, 5, DOOR, "west", "left");
        // Windows in pairs on the upper floors, slits below.
        for (int y : new int[]{17, 25}) {
            for (int d : new int[]{-1, 1}) {
                s.fill(mx + d, y + 1, DON_Z1, mx + d, y + 3, DON_Z1, Kit.GLASS);
                s.fill(mx + d, y + 1, DON_Z2, mx + d, y + 3, DON_Z2, Kit.GLASS);
                s.fill(DON_X1, y + 1, mz + d, DON_X1, y + 3, mz + d, Kit.GLASS);
                s.fill(DON_X2, y + 1, mz + d, DON_X2, y + 3, mz + d, Kit.GLASS);
            }
            s.set(mx, y + 4, DON_Z1, Kit.CARVED).set(mx, y + 4, DON_Z2, Kit.CARVED);
            s.set(DON_X1, y + 4, mz, Kit.CARVED).set(DON_X2, y + 4, mz, Kit.CARVED);
        }
        s.fill(mx, 11, DON_Z1, mx, 12, DON_Z1, Kit.GLASS);
        s.fill(mx - 3, 10, DON_Z1, mx - 3, 12, DON_Z1, Kit.GLASS).fill(mx + 3, 10, DON_Z1, mx + 3, 12, DON_Z1, Kit.GLASS);
        s.set(mx - 3, 13, DON_Z1, Kit.CARVED).set(mx + 3, 13, DON_Z1, Kit.CARVED);
        s.fill(mx, 4, DON_Z1, mx, 5, DON_Z1, Kit.GLASS);
        for (int x : new int[]{20, 26}) {
            s.set(x, 22, DON_Z2 + 1, Kit.wallBanner("red", "south")).set(x, 22, DON_Z1 - 1, Kit.wallBanner("red", "north"));
        }
        // The cellar.
        s.set(19, 1, 3, Kit.barrel()).set(19, 2, 3, Kit.barrel()).set(20, 1, 3, Kit.barrel()).set(19, 1, 4, Kit.barrel());
        s.set(27, 1, 3, Kit.barrel()).set(27, 2, 3, Kit.barrel()).set(26, 1, 3, Kit.barrel()).set(27, 1, 4, Kit.chest("west"));
        s.set(27, 1, 11, Kit.barrel()).set(26, 1, 11, Kit.barrel()).set(27, 2, 11, Kit.barrel());
        // The guard room on the wall walk.
        table(s, 26, WALK + 1, 9);
        s.set(25, WALK + 1, 9, Kit.stairs(SPRUCE_STAIRS, "west")).set(27, WALK + 1, 9, Kit.stairs(SPRUCE_STAIRS, "east"));
        s.set(19, WALK + 1, 11, Kit.barrel()).set(27, WALK + 1, 11, Kit.chest("west")).set(19, WALK + 1, 10, "minecraft:grindstone[face=floor,facing=east]");
        // The armoury.
        s.set(19, 17, 3, "minecraft:smithing_table").set(20, 17, 3, "minecraft:grindstone[face=floor,facing=south]");
        s.set(27, 17, 3, Kit.barrel()).set(27, 17, 4, Kit.barrel()).set(27, 18, 3, Kit.barrel()).set(27, 17, 11, Kit.chest("west"));
        s.set(19, 17, 11, "minecraft:fletching_table").set(20, 19, DON_Z2 - 1, Kit.wallBanner("red", "north"));
        // The study.
        s.fill(19, 25, 3, 21, 27, 3, BOOKSHELF).fill(25, 25, 3, 27, 27, 3, BOOKSHELF);
        s.set(26, 25, 10, "minecraft:cartography_table").set(25, 25, 10, "minecraft:lectern[facing=south,has_book=false,powered=false]");
        s.set(19, 25, 10, TABLE).set(20, 25, 10, Kit.stairs(OAK_STAIRS, "east"));
        // Lanterns under every floor.
        for (int y : new int[]{7, 15, 23, 31}) {
            s.set(20, y, 4, Kit.hangingLantern()).set(26, y, 10, Kit.hangingLantern()).set(20, y, 10, Kit.hangingLantern());
            s.set(26, y, 4, Kit.hangingLantern());
        }
        donjonTop(s, mx, mz);
    }

    /**
     * The top of the donjon: the corbelled battlements with lanterns, the bartizans on the corners, the open lookout of
     * arches, the spire and the flag.
     */
    private static void donjonTop(Structure s, int mx, int mz) {
        int x1 = DON_X1 - 1;
        int x2 = DON_X2 + 1;
        int z1 = DON_Z1 - 1;
        int z2 = DON_Z2 + 1;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (x != x1 && x != x2 && z != z1 && z != z2) {
                    continue;
                }
                String toWall = x == x1 ? "east" : x == x2 ? "west" : z == z1 ? "south" : "north";
                s.set(x, DON_TOP - 1, z, Kit.upside(Kit.WALL_STAIRS, toWall));
                s.set(x, DON_TOP, z, Kit.WALL).set(x, DON_TOP + 1, z, Kit.WALL);
                if ((x + z) % 2 == 0) {
                    s.set(x, DON_TOP + 2, z, Kit.WALL);
                }
            }
        }
        for (int[] c : new int[][]{{DON_X1, DON_Z1}, {DON_X2, DON_Z1}, {DON_X1, DON_Z2}, {DON_X2, DON_Z2}}) {
            bartizan(s, c[0], c[1], DON_TOP, DON_TOP + 2, 3, 2, 3);
        }
        s.set(mx, DON_TOP + 2, z1, Kit.lantern()).set(mx, DON_TOP + 2, z2, Kit.lantern());
        s.set(x1, DON_TOP + 2, mz, Kit.lantern()).set(x2, DON_TOP + 2, mz, Kit.lantern());
        // The lookout: piers at the corners and in the middle of the sides, arches between, a vault of stone over it.
        int y = DON_TOP + 1;
        for (int x = mx - 3; x <= mx + 3; x++) {
            for (int z = mz - 3; z <= mz + 3; z++) {
                boolean edge = Math.abs(x - mx) == 3 || Math.abs(z - mz) == 3;
                if (!edge) {
                    continue;
                }
                boolean pier = (x - mx) % 3 == 0 && (z - mz) % 3 == 0;
                if (pier) {
                    s.set(x, y, z, Kit.TRIM).set(x, y + 1, z, Kit.CARVED).set(x, y + 2, z, Kit.TRIM);
                } else {
                    boolean alongX = Math.abs(z - mz) == 3;
                    int t = alongX ? x - mx : z - mz;
                    int toPier = Math.floorMod(t, 3) == 1 ? -1 : 1;
                    String facing = alongX ? (toPier < 0 ? "west" : "east") : (toPier < 0 ? "north" : "south");
                    s.set(x, y + 2, z, Kit.upside(Kit.WALL_STAIRS, facing));
                }
            }
        }
        s.fill(mx - 3, y + 3, mz - 3, mx + 3, y + 3, mz + 3, Kit.WALL);
        s.fill(mx - 3, y + 3, mz - 3, mx + 3, y + 3, mz + 3, Kit.TRIM);
        s.fill(mx - 2, y + 3, mz - 2, mx + 2, y + 3, mz + 2, Kit.WALL);
        s.set(mx, y + 2, mz - 2, Kit.hangingLantern()).set(mx, y + 2, mz + 2, Kit.hangingLantern());
        // The spire with the flagpole and the flag.
        int top = cone(s, mx, y + 4, mz, 9, 1, 1, 2, 2, 3);
        s.fill(mx, top, mz, mx, HEIGHT - 1, mz, Kit.fence("oak"));
        s.fill(mx + 1, HEIGHT - 2, mz, mx + 3, HEIGHT - 1, mz, Kit.CLOTH);
        s.set(mx + 3, HEIGHT - 2, mz, AIR);
    }

    // ---------------------------------------------------------------- towers and gate

    /**
     * The towers of the curtain. The two back corners are the tallest: round shafts on sloping feet under great flared
     * cones carried on corbels. The two front corners end in a fighting platform behind corbelled battlements, with a
     * turret and its pointed cap in the middle and a ladder up from the guard room. Two smaller towers stand in the middle
     * of the sides under cones of their own. In every tower the wall walk passes through a lit guard room with arrow slits.
     */
    private static void towers(Structure s) {
        for (int[] c : new int[][]{{5, 5, -1, -1}, {41, 5, 1, -1}}) {
            batter(s, c[0], c[1], 9);
            roundTower(s, c[0], c[1], 9, 19, WALK, 14);
            corbels(s, c[0], 19, c[1], 11, 9, Kit.WALL_STAIRS);
            cone(s, c[0], 20, c[1], 11, 1, 1, 2, 2, 3, 4);
            slits(s, c[0], c[1], 4, c[2], c[3]);
        }
        for (int[] c : new int[][]{{5, 40, -1, 1}, {41, 40, 1, 1}}) {
            batter(s, c[0], c[1], 9);
            roundTower(s, c[0], c[1], 9, 16, WALK, 14);
            corbels(s, c[0], 16, c[1], 11, 9, Kit.WALL_STAIRS);
            band(s, c[0], 17, c[1], 11, 9, Kit.WALL);
            int r = 5;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Kit.inDisc(dx, dz, 11) && !Kit.inDisc(dx, dz, 9) && Math.floorMod(dx + dz, 2) == 0) {
                        s.set(c[0] + dx, 18, c[1] + dz, Kit.WALL);
                    }
                }
            }
            for (int y = 17; y <= 20; y++) {
                Kit.disc(s, c[0], y, c[1], 5, y == 20 ? Kit.TRIM : Kit.WALL);
            }
            cone(s, c[0], 21, c[1], 7, 1, 2, 2, 3);
            s.set(c[0] - 3, 17, c[1], Kit.lantern()).set(c[0] + 3, 17, c[1], Kit.lantern());
            s.set(c[0], 17, c[1] - 3, Kit.lantern());
            for (int y = WALK + 1; y <= 16; y++) {
                s.set(c[0] + 2, y, c[1] + 3, Kit.ladder("north"));
            }
            slits(s, c[0], c[1], 4, c[2], c[3]);
        }
        for (int[] c : new int[][]{{4, 23, -1}, {42, 23, 1}}) {
            batter(s, c[0], c[1], 7);
            roundTower(s, c[0], c[1], 7, 14, WALK, 12);
            corbels(s, c[0], 14, c[1], 9, 7, Kit.WALL_STAIRS);
            cone(s, c[0], 15, c[1], 9, 1, 1, 2, 2, 3);
            s.set(c[0] + 3 * c[2], WALK + 2, c[1], AIR).set(c[0] + 3 * c[2], WALK + 3, c[1], AIR);
        }
        for (int x : new int[]{18, 28}) {
            roundTower(s, x, 42, 7, 16, WALK, 12);
            band(s, x, 17, 42, 9, 7, Kit.WALL);
            band(s, x, 16, 42, 9, 7, Kit.WALL);
            for (int dz = -4; dz <= 4; dz++) {
                for (int dx = -4; dx <= 4; dx++) {
                    if (Kit.inDisc(dx, dz, 9) && !Kit.inDisc(dx, dz, 7) && Math.floorMod(dx + dz, 2) == 0) {
                        s.set(x + dx, 18, 42 + dz, Kit.WALL);
                    }
                }
            }
            corbels(s, x, 15, 42, 9, 7, Kit.WALL_STAIRS);
            s.fill(x, 17, 42, x, 21, 42, Kit.fence("oak")).set(x, 22, 42, Kit.banner("red", 0));
            s.set(x - 2, 17, 42, Kit.lantern()).set(x + 2, 17, 42, Kit.lantern());
            s.set(x, WALK + 2, 45, AIR).set(x, WALK + 3, 45, AIR);
            s.set(x, 6, 46, Kit.wallBanner("red", "south"));
            s.set(x, 3, 46, Kit.wallTorch("south"));
        }
        // Banners of the kingdom on the faces of the towers that look out of the castle.
        s.set(5, 13, 0, Kit.wallBanner("red", "north")).set(0, 13, 5, Kit.wallBanner("red", "west"));
        s.set(41, 13, 0, Kit.wallBanner("red", "north")).set(46, 13, 5, Kit.wallBanner("red", "east"));
        s.set(5, 13, 45, Kit.wallBanner("red", "south")).set(0, 13, 40, Kit.wallBanner("red", "west"));
        s.set(41, 13, 45, Kit.wallBanner("red", "south")).set(46, 13, 40, Kit.wallBanner("red", "east"));
        s.set(0, 13, 23, Kit.wallBanner("red", "west")).set(46, 13, 23, Kit.wallBanner("red", "east"));
        // The doors of the wall walk into the towers.
        s.door(5, WALK + 1, 9, DOOR, "south", "left").door(9, WALK + 1, 5, DOOR, "east", "left");
        s.door(41, WALK + 1, 9, DOOR, "south", "right").door(37, WALK + 1, 5, DOOR, "west", "right");
        s.door(5, WALK + 1, 36, DOOR, "north", "right").door(9, WALK + 1, 40, DOOR, "east", "right");
        s.door(41, WALK + 1, 36, DOOR, "north", "left").door(37, WALK + 1, 40, DOOR, "west", "left");
        s.door(5, WALK + 1, 20, DOOR, "south", "left").door(5, WALK + 1, 26, DOOR, "north", "right");
        s.door(41, WALK + 1, 20, DOOR, "south", "right").door(41, WALK + 1, 26, DOOR, "north", "left");
        s.door(15, WALK + 1, 41, DOOR, "east", "left").door(31, WALK + 1, 41, DOOR, "west", "right");
    }

    /**
     * Arrow slits in the guard room of a corner tower, on the two sides that look out of the castle and on the corner
     * between them: {@code ox} and {@code oz} are the steps outwards.
     */
    private static void slits(Structure s, int cx, int cz, int r, int ox, int oz) {
        for (int y = WALK + 2; y <= WALK + 3; y++) {
            s.set(cx + r * ox, y, cz, AIR).set(cx, y, cz + r * oz, AIR);
            s.set(cx + 3 * ox, y, cz + 3 * oz, AIR);
        }
    }

    /**
     * The gatehouse between its two towers: a deep passage under a pointed vault with arches of carved keystones at both
     * ends, a portcullis of iron bars hanging raised behind the outer arch, lanterns; over it the guard room through which
     * the wall walk passes, a balcony on corbels with a balustrade, standing banners and a lantern, drapes of the kingdom's
     * cloth on either side of its door and the arms of the kingdom over it, and a battlemented top on machicolations.
     */
    private static void gatehouse(Structure s) {
        int x1 = CX - 2;
        int x2 = CX + 2;
        int front = 44;
        s.fill(x1, 1, 39, x2, 15, front, Kit.WALL);
        s.fill(x1, 1, front, x2, 2, front, Kit.ROUGH).fill(x1, 1, 39, x2, 1, 39, Kit.ROUGH);
        s.fill(x1, WALK, front, x2, WALK, front, Kit.TRIM);
        // The passage.
        s.fill(CX - 1, 1, 39, CX + 1, 4, front, AIR);
        for (int z = 39; z <= front; z++) {
            s.set(CX - 1, 5, z, Kit.upside(Kit.WALL_STAIRS, "west")).set(CX + 1, 5, z, Kit.upside(Kit.WALL_STAIRS, "east")).set(CX, 5, z, AIR);
        }
        s.set(CX, 6, 39, Kit.CARVED).set(CX, 6, front, Kit.CARVED);
        s.fill(CX - 1, 4, front - 1, CX + 1, 4, front - 1, Kit.BARS).set(CX, 5, front - 1, Kit.BARS);
        s.set(CX, 5, 41, Kit.hangingLantern());
        s.set(CX - 1, 3, 40, Kit.wallTorch("east")).set(CX + 1, 3, 40, Kit.wallTorch("west"));
        s.set(x1 - 1, 3, 38, Kit.wallTorch("north")).set(x2 + 1, 3, 38, Kit.wallTorch("north"));
        // The guard room and its doors to the towers and the balcony.
        s.fill(CX - 1, WALK, 40, CX + 1, WALK, front - 1, PLANKS);
        s.fill(CX - 1, WALK + 1, 40, CX + 1, 12, front - 1, AIR);
        s.set(CX, 12, 41, Kit.hangingLantern());
        s.set(CX + 1, WALK + 1, 40, Kit.barrel()).set(CX - 1, WALK + 1, 40, Kit.chest("south"));
        s.door(x1, WALK + 1, 42, DOOR, "east", "left").door(x2, WALK + 1, 42, DOOR, "west", "left");
        s.door(CX, WALK + 1, front, DOOR, "south", "left");
        s.fill(CX, WALK + 2, 39, CX, WALK + 3, 39, Kit.GLASS);
        // The balcony.
        s.fill(x1, WALK - 1, front + 1, x2, WALK - 1, front + 1, Kit.upside(Kit.WALL_STAIRS, "north"));
        s.fill(x1, WALK, front + 1, x2, WALK, front + 1, Kit.TRIM);
        s.fill(x1, WALK, front + 2, x2, WALK, front + 2, Kit.topSlab(Kit.TRIM_SLAB));
        s.fill(x1, WALK + 1, front + 2, x2, WALK + 1, front + 2, Kit.WALL_FENCE);
        s.set(x1, WALK + 1, front + 1, Kit.WALL_FENCE).set(x2, WALK + 1, front + 1, Kit.WALL_FENCE);
        s.set(x1, WALK + 2, front + 2, Kit.banner("red", 0)).set(x2, WALK + 2, front + 2, Kit.banner("red", 0));
        s.set(CX, WALK + 2, front + 2, Kit.lantern());
        // The drapes and the arms over the balcony.
        s.fill(x1, WALK + 1, front, x1, 14, front, Kit.CLOTH).fill(x2, WALK + 1, front, x2, 14, front, Kit.CLOTH);
        s.set(CX, 12, front, Kit.CARVED).set(CX, 13, front, GOLD).set(CX, 11, front, Kit.TRIM);
        s.set(CX - 1, 13, front, Kit.upside(Kit.TRIM_STAIRS, "east")).set(CX + 1, 13, front, Kit.upside(Kit.TRIM_STAIRS, "west"));
        s.set(CX - 1, 12, front, Kit.stairs(Kit.TRIM_STAIRS, "east")).set(CX + 1, 12, front, Kit.stairs(Kit.TRIM_STAIRS, "west"));
        // Machicolations over the front, battlements round the top.
        s.fill(x1, 14, front + 1, x2, 14, front + 1, Kit.upside(Kit.WALL_STAIRS, "north"));
        s.fill(x1, 15, front + 1, x2, 16, front + 1, Kit.WALL);
        s.fill(x1, 16, 39, x2, 16, 39, Kit.WALL).fill(x1, 16, 39, x1, 16, front, Kit.WALL).fill(x2, 16, 39, x2, 16, front, Kit.WALL);
        for (int x = x1; x <= x2; x++) {
            if (x % 2 == 1) {
                s.set(x, 17, front + 1, Kit.WALL).set(x, 17, 39, Kit.WALL);
            }
        }
        s.set(CX, 16, 42, Kit.lantern());
    }

    // ---------------------------------------------------------------- the courtyard

    /**
     * The courtyard: before the hall a paved court with a round fountain on the way from the gate to the great door, two
     * gardens of lawn on either side of the hall with a tree, flower beds along the walls, hedges, benches and paths of trim
     * to the doors of the lodgings and the chapel, stairs of stone up to the wall walk, and lamp posts everywhere.
     */
    private static void courtyard(Structure s) {
        // Lawns and beds.
        for (int[] lawn : new int[][]{{8, 21, 15, 34}, {31, 21, 38, 34}, {14, 34, 19, 37}, {27, 34, 32, 37}}) {
            for (int x = lawn[0]; x <= lawn[2]; x++) {
                for (int z = lawn[1]; z <= lawn[3]; z++) {
                    if (s.get(x, 0, z).equals(Kit.ROUGH) && !s.solid(x, 1, z)) {
                        s.set(x, 0, z, GRASS);
                    }
                }
            }
        }
        // Paths: across the court along the front wall, round the fountain, to the doors of the lodgings and the chapel.
        s.fill(8, 0, 38, 38, 0, 38, Kit.TRIM);
        s.fill(13, 0, 21, 13, 0, 37, Kit.TRIM);
        s.fill(33, 0, 21, 33, 0, 37, Kit.TRIM);
        band(s, CX, 0, 35, 9, 5, Kit.TRIM);
        s.fill(14, 0, 33, 18, 0, 33, Kit.TRIM).fill(28, 0, 33, 32, 0, 33, Kit.TRIM);
        fountain(s, CX, 35);
        // The gardens: a tree, hedges, flower beds along the aisles of the hall, benches and lamps.
        String[] flowers = {"poppy", "cornflower", "allium", "oxeye_daisy", "azure_bluet", "red_tulip", "lily_of_the_valley", "white_tulip"};
        for (int side = -1; side <= 1; side += 2) {
            int path = side < 0 ? 13 : 33;
            tree(s, CX + side * 13, 29, side < 0 ? Kit.leaves("oak") : Kit.leaves("flowering_azalea"));
            for (int z = 28; z <= 33; z++) {
                s.set(CX + side * 15, 1, z, Kit.leaves("oak"));
            }
            int i = side < 0 ? 0 : 3;
            for (int z = 21; z <= 29; z++) {
                int x = CX + side * 8;
                if (s.get(x, 0, z).equals(GRASS) && !s.solid(x, 1, z)) {
                    s.set(x, 1, z, "minecraft:" + flowers[i++ % flowers.length]);
                }
            }
            for (int z = 21; z <= 24; z++) {
                int x = CX + side * 12;
                if (s.get(x, 0, z).equals(GRASS)) {
                    s.set(x, 1, z, "minecraft:" + flowers[i++ % flowers.length]);
                }
            }
            s.set(path + side, 1, 33, Kit.stairs(SPRUCE_STAIRS, side < 0 ? "west" : "east"));
            s.set(path + side, 1, 32, Kit.stairs(SPRUCE_STAIRS, side < 0 ? "west" : "east"));
            lampPost(s, path - side, 23);
            lampPost(s, path - side, 30);
            lampPost(s, path + side, 37);
            // The flowers of the beds in the court.
            for (int x = CX + side * 4; x != CX + side * 10; x += side) {
                for (int z = 35; z <= 36; z++) {
                    if (s.get(x, 0, z).equals(GRASS) && !s.solid(x, 1, z)) {
                        s.set(x, 1, z, "minecraft:" + flowers[Math.floorMod(x + z, flowers.length)]);
                    }
                }
            }
            lampPost(s, CX + side * 5, 37);
            lampPost(s, CX + side * 5, 34);
            // The stairs up to the wall walk along the inner face of the side wall.
            int x = side < 0 ? 7 : 39;
            for (int k = 0; k <= 7; k++) {
                int z = 34 - k;
                if (k > 0) {
                    s.fill(x, 1, z, x, k, z, Kit.WALL);
                }
                s.set(x, 1 + k, z, Kit.stairs(Kit.WALL_STAIRS, "north"));
            }
            s.set(x, 0, 35, Kit.TRIM);
            // A lantern in the alley beside the lodgings.
            s.set(side < 0 ? 8 : 38, 1, 17, Kit.lantern());
        }
        // No lawn under anything that stands on it: there the ground is plain earth.
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                if (s.get(x, 0, z).equals(GRASS) && (States.isFull(s.get(x, 1, z)) || s.get(x, 1, z).contains("_leaves"))) {
                    s.set(x, 0, z, "minecraft:dirt");
                }
            }
        }
    }

    /**
     * A round fountain: a basin of trim with water round a carved pillar that carries a small bowl and a lantern.
     */
    private static void fountain(Structure s, int cx, int cz) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (!Kit.inDisc(dx, dz, 5)) {
                    continue;
                }
                s.set(cx + dx, 0, cz + dz, Kit.TRIM);
                if (Kit.onRim(dx, dz, 5)) {
                    s.set(cx + dx, 1, cz + dz, Kit.TRIM);
                } else if (dx != 0 || dz != 0) {
                    s.set(cx + dx, 1, cz + dz, WATER);
                }
            }
        }
        s.set(cx, 1, cz, Kit.CARVED).set(cx, 2, cz, Kit.CARVED).set(cx, 3, cz, Kit.TRIM);
        s.set(cx - 1, 3, cz, Kit.upside(Kit.TRIM_STAIRS, "east")).set(cx + 1, 3, cz, Kit.upside(Kit.TRIM_STAIRS, "west"));
        s.set(cx, 3, cz - 1, Kit.upside(Kit.TRIM_STAIRS, "south")).set(cx, 3, cz + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(cx, 4, cz, Kit.lantern());
    }

    /** A tree: a trunk of oak on the lawn, a round crown of leaves that never fall, a shroomlight in its heart. */
    private static void tree(Structure s, int x, int z, String leaves) {
        s.set(x, 0, z, GRASS);
        s.fill(x, 1, z, x, 4, z, Kit.log(OAK_LOG, "y"));
        for (int y = 4; y <= 7; y++) {
            int r = y == 4 || y == 7 ? 1 : 2;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (r == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2 || !s.inside(x + dx, y, z + dz) || s.solid(x + dx, y, z + dz)) {
                        continue;
                    }
                    s.set(x + dx, y, z + dz, leaves);
                }
            }
        }
        s.set(x, 5, z, "minecraft:shroomlight");
    }
}
