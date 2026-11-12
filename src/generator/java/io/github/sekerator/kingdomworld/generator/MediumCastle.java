package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle of a medium kingdom of the world, the palace in the middle of its capital, drawn as one mass that rises in
 * steps from the curtain wall over the towers and the keep to the donjon. A curtain wall with a walk behind battlements
 * runs all round between four round towers of two kinds: at the back corners two great towers, broad and tall under slender
 * cones of slate with the banners of the kingdom, at the front corners two slimmer and lower ones under short cones. In the
 * middle of the south wall stands a square gatehouse, broad and higher than the wall, with a deep vaulted passage and a
 * portcullis, a balcony over the gate and a parapet carried on corbels all round. Inside lies a courtyard with a fountain on
 * the way from the gate to the keep, gardens with trees and flower beds on both sides and lamp posts along the paths.
 *
 * <p>The keep holds the throne hall: a nave with two aisles under galleries, a gable front with a great portal, a tall
 * window and a rose window between angle buttresses, and a steep roof of tiles. The throne stands in the world before the
 * castle is built, in the middle of the ground at the north end of the hall; the castle leaves it and the ground under it
 * out. Behind the throne rises the donjon, broader than any tower and far higher than all of them: the treasury on its
 * ground floor, the council room, the royal chamber and the library above, a spiral stair round a newel through all of them,
 * an open lookout under a parapet on machicolations and over it a tall square spire with dormers on its four faces and the
 * flag of the kingdom on its point, the highest point of the capital. A timbered kitchen wing leans on its west side, a
 * chapel with a bell cote on its east side, so that the wings, the keep and the donjon step up together.
 */
final class MediumCastle {
    private static final String AIR = Structure.AIR;
    private static final String WALL = Kit.WALL;
    private static final String WALL_STAIRS = Kit.WALL_STAIRS;
    private static final String WALL_FENCE = Kit.WALL_FENCE;
    private static final String ROUGH = Kit.ROUGH;
    private static final String ROUGH_STAIRS = Kit.ROUGH_STAIRS;
    private static final String TRIM = Kit.TRIM;
    private static final String TRIM_STAIRS = Kit.TRIM_STAIRS;
    private static final String TRIM_SLAB = Kit.TRIM_SLAB;
    private static final String CARVED = Kit.CARVED;
    private static final String PLASTER = Kit.PLASTER;
    private static final String CLOTH = Kit.CLOTH;
    private static final String PLANKS = "minecraft:spruce_planks";
    private static final String LOG = "minecraft:spruce_log";
    private static final String OAK_LOG = "minecraft:oak_log";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String DOOR = "minecraft:spruce_door";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String DIRT = "minecraft:dirt";
    private static final String WATER = "minecraft:water[level=0]";
    private static final String GLOW = "minecraft:glowstone";
    private static final String GOLD = "minecraft:gold_block";
    private static final String YELLOW = "minecraft:yellow_wool";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String ROD = "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]";
    private static final String HATCH = Kit.trapdoor("minecraft:oak_trapdoor", "north", true);
    private static final String CAMPFIRE = "minecraft:campfire[facing=east,lit=true,signal_fire=false,waterlogged=false]";
    private static final String WOOD = "spruce";

    // ---------------------------------------------------------------- the plan

    /** The box: 41 by 41, 48 high; the throne in its middle. */
    private static final int SIZE = 41;
    private static final int HEIGHT = 48;
    private static final int CX = 20;
    private static final int CZ = 20;
    /** The layer the feet are in on the wall walk; the curtain wall is solid below it. */
    private static final int WALK = 8;
    /** The keep: its walls, the top of its walls (the eaves of its roof) and the floor of the galleries over its aisles. */
    private static final int KEEP_X1 = 12;
    private static final int KEEP_X2 = 28;
    private static final int KEEP_Z1 = 18;
    private static final int KEEP_Z2 = 27;
    private static final int KEEP_TOP = 14;
    private static final int GALLERY = 6;
    /** The donjon behind the throne, its floors (the last one that of the lookout) and its roof, on which the spire stands. */
    private static final int DON_X1 = 14;
    private static final int DON_X2 = 26;
    private static final int DON_Z1 = 6;
    private static final int DON_Z2 = 18;
    private static final int[] FLOORS = {9, 14, 19, 24};
    private static final int ROOF = 31;
    /** The newel of the spiral stair in the donjon and the eight cells around it in the order they climb. */
    private static final int NEWEL_X = 23;
    private static final int NEWEL_Z = 15;
    private static final int[][] SPIRAL = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
    /** The gatehouse: its walls and the layer of its roof. */
    private static final int GATE_X1 = 13;
    private static final int GATE_X2 = 27;
    private static final int GATE_Z1 = 35;
    private static final int GATE_Z2 = 39;
    private static final int GATE_TOP = 15;
    /** The fountain in the courtyard, on the way from the gate to the keep. */
    private static final int FOUNTAIN_Z = 31;

    private MediumCastle() {
    }

    /**
     * The template this class draws, with the places of the guards: four in the throne hall, the door of the keep, two in
     * the gate passage, one before the treasury, eight on the wall walk and the door of the castle.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("classic_castle_medium", "defense", "palace", "minecraft:stone_bricks", CX, 1, SIZE - 1, castle(),
                "none", 1, "")
                .at("palace_hall", CX - 2, 1, CZ + 3).at("palace_hall", CX + 2, 1, CZ + 3)
                .at("palace_hall", CX - 2, 1, CZ + 5).at("palace_hall", CX + 2, 1, CZ + 5)
                .at("palace_door", CX, 1, KEEP_Z2 - 1)
                .at("castle_gate", 19, 1, 37).at("castle_gate", 21, 1, 37)
                .at("treasury", 18, 1, 15)
                .at("castle_wall", 2, WALK, 12).at("castle_wall", 2, WALK, 28).at("castle_wall", 38, WALK, 12).at("castle_wall", 38, WALK, 28)
                .at("castle_wall", 13, WALK, 3).at("castle_wall", 27, WALK, 3).at("castle_wall", 10, WALK, 38).at("castle_wall", 30, WALK, 38)
                .at("door", CX, 1, SIZE - 1));
        return list;
    }

    /**
     * The castle of a medium kingdom: the curtain wall with its four towers and the gatehouse, the keep with the throne hall,
     * the donjon behind it, the kitchen and the chapel wings and the courtyard with its fountain and gardens.
     */
    static Structure castle() {
        Structure s = new Structure(SIZE, HEIGHT, SIZE);
        ground(s);
        curtainWalls(s);
        int[] slender = {1, 2, 2, 2, 2, 2};
        int[] stout = {1, 1, 2, 2, 2};
        roundTower(s, 4, 4, 9, 17, slender, new int[][]{{-1, 0}, {1, 0}, {0, 1}}, 4, 1, "south", true);
        roundTower(s, 36, 4, 9, 17, slender, new int[][]{{1, 0}, {-1, 0}, {0, 1}}, 36, 1, "south", true);
        roundTower(s, 4, 36, 7, 12, stout, new int[][]{{-1, 0}, {0, 1}}, -1, -1, null, false);
        roundTower(s, 36, 36, 7, 12, stout, new int[][]{{1, 0}, {0, 1}}, -1, -1, null, false);
        gatehouse(s);
        walkDoors(s);
        keep(s);
        donjon(s);
        throneHall(s);
        donjonRooms(s);
        kitchen(s);
        chapel(s);
        courtyard(s);
        gardens(s);
        s.skip(CX, 0, CZ).skip(CX, 1, CZ);
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- shared shapes

    /** The side of a cell lying at (dx, dz) from a centre on which that centre lies. */
    private static String toward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx < 0 ? "east" : "west";
        }
        return dz < 0 ? "south" : "north";
    }

    /** The side across from a side. */
    private static String opposite(String side) {
        return switch (side) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** Sets a block only where the position lies inside the box. */
    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    /**
     * A window of plain glass from one layer to another with a carved head over it.
     */
    private static void window(Structure s, int x, int z, int y1, int y2) {
        s.fill(x, y1, z, x, y2, z, Kit.GLASS).set(x, y2 + 1, z, CARVED);
    }

    /**
     * A cone over a round plan: rings shrinking by one block each, starting one block wider than the plan so that the eaves
     * overhang, every ring as many layers high as its entry in {@code rises} (the last entry serves the rings after it), a
     * shell two blocks thick, the top of every ring dressed with stairs where its edge runs straight.
     *
     * @return the layer above the point
     */
    private static int cone(Structure s, int cx, int y, int cz, int diameter, int[] rises, Kit.Roof roof) {
        int layer = y;
        int ring = 0;
        for (int width = diameter + 2; width >= 1; width -= 2, ring++) {
            int rise = rises[Math.min(ring, rises.length - 1)];
            int r = (width - 1) / 2;
            for (int step = 0; step < rise; step++) {
                boolean last = step == rise - 1;
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (!Kit.inDisc(dx, dz, width) || width >= 5 && Kit.inDisc(dx, dz, width - 4)) {
                            continue;
                        }
                        String state = roof.block();
                        if (last && width > 1 && !Kit.inDisc(dx, dz, width - 2)) {
                            state = coneStairs(roof, dx, dz, width);
                        }
                        put(s, cx + dx, layer, cz + dz, state);
                    }
                }
                layer++;
            }
        }
        return layer;
    }

    /**
     * The stair that dresses a cell on the edge of a ring of a cone: a straight one where one side of it is open, a turned
     * one where two are, so that the edge of the ring runs round smoothly.
     */
    private static String coneStairs(Kit.Roof roof, int dx, int dz, int width) {
        boolean west = !Kit.inDisc(dx - 1, dz, width);
        boolean east = !Kit.inDisc(dx + 1, dz, width);
        boolean north = !Kit.inDisc(dx, dz - 1, width);
        boolean south = !Kit.inDisc(dx, dz + 1, width);
        int open = (west ? 1 : 0) + (east ? 1 : 0) + (north ? 1 : 0) + (south ? 1 : 0);
        if (open == 2 && (west || east) && (north || south)) {
            String facing = north ? "south" : "north";
            boolean left = north == west;
            return Kit.stairs(roof.stairs(), facing, left ? "outer_left" : "outer_right");
        }
        if (open == 1) {
            return Kit.stairs(roof.stairs(), west ? "east" : east ? "west" : north ? "south" : "north");
        }
        return roof.block();
    }

    /**
     * A tall spire over a square plan: square rings shrinking by one block on every side, every ring as many layers high as
     * its entry in {@code rises} (the last entry serves the rings after it), a shell two blocks thick, the top of every ring
     * dressed with stairs and turned stairs at its corners.
     *
     * @return the layer above the point
     */
    private static int squareSpire(Structure s, int x1, int z1, int x2, int z2, int y, int[] rises, Kit.Roof roof) {
        int layer = y;
        for (int ring = 0; x1 + ring <= x2 - ring && z1 + ring <= z2 - ring; ring++) {
            int w = x1 + ring;
            int e = x2 - ring;
            int b = z1 + ring;
            int f = z2 - ring;
            int rise = rises[Math.min(ring, rises.length - 1)];
            for (int step = 0; step < rise; step++) {
                boolean last = step == rise - 1;
                for (int z = b; z <= f; z++) {
                    for (int x = w; x <= e; x++) {
                        if (x - w >= 2 && e - x >= 2 && z - b >= 2 && f - z >= 2) {
                            continue;
                        }
                        boolean edge = x == w || x == e || z == b || z == f;
                        String state = roof.block();
                        if (last && edge && e > w) {
                            state = spireStairs(roof, x == w, x == e, z == b, z == f);
                        }
                        put(s, x, layer, z, state);
                    }
                }
                layer++;
            }
        }
        return layer;
    }

    /**
     * The stair on the edge of a ring of a square spire: rising toward the middle, turned at the corners.
     */
    private static String spireStairs(Kit.Roof roof, boolean west, boolean east, boolean back, boolean front) {
        if (back && west) {
            return Kit.stairs(roof.stairs(), "south", "outer_left");
        }
        if (back && east) {
            return Kit.stairs(roof.stairs(), "south", "outer_right");
        }
        if (front && west) {
            return Kit.stairs(roof.stairs(), "north", "outer_right");
        }
        if (front && east) {
            return Kit.stairs(roof.stairs(), "north", "outer_left");
        }
        return Kit.stairs(roof.stairs(), back ? "south" : front ? "north" : west ? "east" : "west");
    }

    /**
     * A ring of corbels around a round plan: stairs hanging upside down from its rim, one block out, that carry the eaves of
     * a cone or a parapet.
     */
    private static void corbelRing(Structure s, int cx, int y, int cz, int diameter, String stairs) {
        int r = (diameter + 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Kit.inDisc(dx, dz, diameter + 2) && !Kit.inDisc(dx, dz, diameter) && s.inside(cx + dx, y, cz + dz)
                        && !s.solid(cx + dx, y, cz + dz)) {
                    s.set(cx + dx, y, cz + dz, Kit.upside(stairs, toward(dx, dz)));
                }
            }
        }
    }

    /**
     * A sloped foot around a round plan: stairs of rough stone leaning on its lowest course.
     */
    private static void batterRing(Structure s, int cx, int cz, int diameter) {
        int r = (diameter + 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Kit.inDisc(dx, dz, diameter + 2) && !Kit.inDisc(dx, dz, diameter) && s.inside(cx + dx, 1, cz + dz)
                        && !s.solid(cx + dx, 1, cz + dz)) {
                    s.set(cx + dx, 0, cz + dz, ROUGH);
                    s.set(cx + dx, 1, cz + dz, Kit.stairs(ROUGH_STAIRS, toward(dx, dz)));
                }
            }
        }
    }

    /**
     * A parapet carried one block out on a row of corbels round a rectangle: the corbels, two courses of wall, merlons on
     * every second block and at the corners merlons raised one course higher under a carved cap.
     *
     * @param y the layer of the corbels
     */
    private static void machicolations(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                if (x >= x1 && x <= x2 && z >= z1 && z <= z2) {
                    continue;
                }
                String in = x < x1 ? "east" : x > x2 ? "west" : z < z1 ? "south" : "north";
                s.set(x, y, z, Kit.upside(TRIM_STAIRS, in));
                s.set(x, y + 1, z, WALL).set(x, y + 2, z, WALL);
                boolean corner = (x < x1 || x > x2) && (z < z1 || z > z2);
                if (corner) {
                    s.set(x, y + 3, z, WALL).set(x, y + 4, z, CARVED);
                } else if (Math.floorMod(x + z, 2) == 0) {
                    s.set(x, y + 3, z, WALL);
                }
            }
        }
    }

    /** A lantern hanging on a chain: the chain from {@code top} down, the lantern under it. */
    private static void chandelier(Structure s, int x, int z, int lanternY, int top) {
        s.fill(x, lanternY + 1, z, x, top, z, Kit.chain());
        s.set(x, lanternY, z, Kit.hangingLantern());
    }

    /** A lamp post on the ground: two fence posts and a lantern on them. */
    private static void lampPost(Structure s, int x, int z) {
        s.set(x, 0, z, TRIM);
        s.set(x, 1, z, WALL_FENCE).set(x, 2, z, Kit.fence("spruce")).set(x, 3, z, Kit.lantern());
    }

    /** A small oak with a crown of leaves that never fall, standing on grass. */
    private static void tree(Structure s, int x, int z, int trunk) {
        s.set(x, 0, z, DIRT);
        for (int y = trunk - 1; y <= trunk + 2; y++) {
            int r = y >= trunk + 2 ? 1 : 2;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                    if (corner && (r == 1 || y != trunk) || !s.inside(x + dx, y, z + dz) || s.solid(x + dx, y, z + dz)) {
                        continue;
                    }
                    s.set(x + dx, y, z + dz, Kit.leaves("oak"));
                }
            }
        }
        s.fill(x, 1, z, x, trunk + 1, z, Kit.log(OAK_LOG, "y"));
    }

    // ---------------------------------------------------------------- the walls and the towers

    /**
     * The ground: a strip of lawn outside the walls, rough stone everywhere inside; paths and gardens are laid later.
     */
    private static void ground(Structure s) {
        s.fill(0, 0, 0, SIZE - 1, 0, SIZE - 1, GRASS);
        s.fill(1, 0, 1, SIZE - 2, 0, SIZE - 2, ROUGH);
    }

    /**
     * The curtain wall: two blocks thick with a course of rough stone at its foot, a sloped plinth and a band of trim over it,
     * pilasters of rough stone and arrow slits along its face, solid up to the walk, a parapet with merlons carried one block
     * out on a row of corbels, lanterns in the crenels and torches on its inner face.
     */
    private static void curtainWalls(Structure s) {
        for (int i = 4; i <= SIZE - 5; i++) {
            wallSlice(s, i, 1, i, 2, i, 3, i, 4, "south");
            wallSlice(s, i, 39, i, 38, i, 37, i, 36, "north");
            wallSlice(s, 1, i, 2, i, 3, i, 4, i, "east");
            wallSlice(s, 39, i, 38, i, 37, i, 36, i, "west");
        }
    }

    /**
     * One slice across the curtain wall: the parapet cell, the outer and the inner cell of the wall and the courtyard cell
     * before it; {@code inward} is the side from the parapet toward the courtyard.
     */
    private static void wallSlice(Structure s, int px, int pz, int ox, int oz, int ix, int iz, int cx, int cz, String inward) {
        s.set(px, 0, pz, ROUGH).set(ox, 0, oz, ROUGH).set(ix, 0, iz, ROUGH);
        s.fill(ox, 1, oz, ox, 2, oz, ROUGH).set(ox, 3, oz, TRIM).fill(ox, 4, oz, ox, WALK - 1, oz, WALL);
        s.set(ix, 1, iz, ROUGH).fill(ix, 2, iz, ix, WALK - 1, iz, WALL);
        int along = px == 1 || px == SIZE - 2 ? pz : px;
        s.set(px, 1, pz, Kit.stairs(ROUGH_STAIRS, inward));
        if (along % 8 == 0) {
            // A pilaster of rough stone every eight blocks, under the parapet.
            s.fill(px, 1, pz, px, 4, pz, ROUGH).set(px, 5, pz, Kit.stairs(ROUGH_STAIRS, inward));
        }
        s.set(px, WALK - 1, pz, Kit.upside(TRIM_STAIRS, inward));
        s.set(px, WALK, pz, WALL);
        if (Math.floorMod(px + pz, 2) == 0) {
            s.set(px, WALK + 1, pz, WALL);
        } else if (along % 6 == 0) {
            s.set(px, WALK + 1, pz, Kit.lantern());
        }
        if (along % 8 == 4) {
            s.set(cx, 4, cz, Kit.wallTorch(inward));
        }
        // Arrow slits: narrow recesses in the outer face.
        if (along % 4 == 2) {
            s.fill(ox, 4, oz, ox, 5, oz, AIR);
        }
    }

    /**
     * A round tower of the curtain wall: a solid footing of rough stone up to the walk with a sloped foot, a guard room at the
     * height of the walk that the walk runs through, for a tall tower a second room over it reached by a ladder, bands of
     * trim, windows on its outer sides and a ring of corbels at its top that carries the eaves of a cone of slate, open to the
     * room under it, with a banner of the kingdom or a rod on its point.
     *
     * @param top     the last layer of its wall
     * @param rises   the rises of the rings of its cone
     * @param windows the sides that have windows, as steps from the middle
     * @param lx      the ladder up from the guard room (x, z and the way it faces), standing against the side away from the
     *                courtyard, or -1 for none
     */
    private static void roundTower(Structure s, int cx, int cz, int d, int top, int[] rises, int[][] windows, int lx, int lz,
            String lf, boolean flag) {
        int r = (d - 1) / 2;
        boolean upper = top >= 15;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (!Kit.inDisc(dx, dz, d)) {
                    continue;
                }
                boolean rim = Kit.onRim(dx, dz, d);
                put(s, cx + dx, 0, cz + dz, ROUGH);
                for (int y = 1; y <= top; y++) {
                    String state;
                    if (rim) {
                        state = y <= 2 ? ROUGH : y == WALK - 1 || y == 12 || y == top ? TRIM : WALL;
                    } else if (y < WALK - 1) {
                        state = ROUGH;
                    } else if (y == WALK - 1 || upper && y == 12) {
                        state = PLANKS;
                    } else {
                        state = AIR;
                    }
                    put(s, cx + dx, y, cz + dz, state);
                }
            }
        }
        batterRing(s, cx, cz, d);
        if (lx >= 0) {
            for (int y = WALK; y <= 12; y++) {
                s.set(lx, y, lz, Kit.ladder(lf));
            }
        }
        for (int[] side : windows) {
            int x = cx + side[0] * r;
            int z = cz + side[1] * r;
            s.set(x, 10, z, Kit.GLASS).set(x, 11, z, Kit.GLASS);
            if (upper) {
                s.set(x, 14, z, Kit.GLASS).set(x, 15, z, Kit.GLASS);
            }
        }
        s.set(cx, WALK, cz, Kit.lantern());
        if (upper) {
            s.set(cx, 13, cz, Kit.lantern());
        }
        corbelRing(s, cx, top, cz, d, TRIM_STAIRS);
        int tip = cone(s, cx, top + 1, cz, d, rises, Kit.SLATE);
        if (flag) {
            s.set(cx, tip, cz, Kit.fence("spruce")).set(cx, tip + 1, cz, Kit.fence("spruce")).set(cx, tip + 2, cz, Kit.banner("red", 0));
        } else {
            s.set(cx, tip, cz, ROD);
        }
    }

    /**
     * The gatehouse in the middle of the south wall: one square mass, broader and higher than the wall, with quoins, a sloped
     * foot and bands of trim, a vaulted passage with arches at both mouths and a portcullis hung behind the outer arch, a
     * guard room over the passage that the wall walk runs through with the winch of the portcullis, a table and racks of
     * weapons, a balcony in an arched niche over the gate on a row of corbels, the banners of the kingdom on its front and
     * a parapet with merlons carried on corbels all round its roof.
     */
    private static void gatehouse(Structure s) {
        int x1 = GATE_X1;
        int x2 = GATE_X2;
        int z1 = GATE_Z1;
        int z2 = GATE_Z2;
        int top = GATE_TOP;
        s.fill(x1, 0, z1, x2, 0, z2, ROUGH);
        s.fill(x1, 1, z1, x2, top, z2, WALL);
        s.walls(x1, 1, z1, x2, 2, z2, ROUGH);
        s.walls(x1, WALK - 1, z1, x2, WALK - 1, z2, TRIM);
        s.walls(x1, top, z1, x2, top, z2, TRIM);
        for (int x : new int[]{x1, x2}) {
            for (int z : new int[]{z1, z2}) {
                s.fill(x, 1, z, x, top - 1, z, ROUGH);
            }
        }
        // A sloped foot along the front, not before the gate, and on the sides that look at the courtyard.
        for (int x = x1; x <= x2; x++) {
            if (x < 18 || x > 22) {
                s.set(x, 1, SIZE - 1, Kit.stairs(ROUGH_STAIRS, "north"));
            }
        }
        for (int z = z1; z <= 36; z++) {
            s.set(x1 - 1, 1, z, Kit.stairs(ROUGH_STAIRS, "east")).set(x2 + 1, 1, z, Kit.stairs(ROUGH_STAIRS, "west"));
        }
        // The passage, paved, under a vault of stairs, with arches at both mouths.
        s.fill(19, 0, z1 - 1, 21, 0, SIZE - 1, TRIM);
        s.fill(17, 0, SIZE - 1, 23, 0, SIZE - 1, TRIM);
        s.fill(19, 1, z1, 21, 5, z2, AIR);
        for (int z = z1; z <= z2; z++) {
            s.set(19, 5, z, Kit.upside(WALL_STAIRS, "west")).set(21, 5, z, Kit.upside(WALL_STAIRS, "east"));
        }
        for (int z : new int[]{z1, z2}) {
            s.set(19, 5, z, Kit.upside(TRIM_STAIRS, "west")).set(21, 5, z, Kit.upside(TRIM_STAIRS, "east"));
            s.set(18, 6, z, TRIM).set(22, 6, z, TRIM).set(20, 6, z, CARVED);
            s.set(19, 6, z, Kit.upside(TRIM_STAIRS, "west")).set(21, 6, z, Kit.upside(TRIM_STAIRS, "east"));
            s.fill(18, 3, z, 18, 5, z, TRIM).fill(22, 3, z, 22, 5, z, TRIM);
        }
        // The portcullis, half raised behind the outer arch, and the light of the passage.
        s.fill(19, 4, z2 - 1, 21, 4, z2 - 1, Kit.BARS).set(20, 5, z2 - 1, Kit.BARS);
        s.set(20, 5, z1 + 1, Kit.hangingLantern());
        s.set(19, 3, 37, Kit.wallTorch("east")).set(21, 3, 37, Kit.wallTorch("west"));
        // The guard room over the passage, its windows and doors to the wall walk on both sides.
        s.fill(x1 + 1, WALK - 1, z1 + 1, x2 - 1, WALK - 1, z2 - 1, PLANKS);
        s.fill(x1 + 1, WALK, z1 + 1, x2 - 1, top - 1, z2 - 1, AIR);
        for (int x : new int[]{16, CX, 24}) {
            window(s, x, z1, 9, 11);
        }
        for (int x : new int[]{15, 25}) {
            window(s, x, z2, 9, 11);
            s.fill(x, 3, z2, x, 4, z2, AIR);
        }
        window(s, x1, 37, 11, 12);
        window(s, x2, 37, 11, 12);
        // The winch of the portcullis, a table with a bench, racks of weapons, crates and lanterns.
        s.fill(19, WALK, 36, 19, WALK + 1, 36, Kit.fence(WOOD)).fill(21, WALK, 36, 21, WALK + 1, 36, Kit.fence(WOOD));
        s.fill(19, WALK + 2, 36, 21, WALK + 2, 36, Kit.log(LOG, "x"));
        Furnish.longTable(s, 15, 37, 17, 37, WALK, WOOD);
        Furnish.bench(s, 15, 36, 17, 36, WALK, "south", WOOD);
        s.set(16, WALK + 1, 37, Kit.lantern());
        Furnish.weaponRack(s, 24, WALK, 36, "south", WOOD);
        Furnish.weaponRack(s, 25, WALK, 36, "south", WOOD);
        Furnish.crate(s, 26, WALK, 36, WOOD);
        Furnish.crate(s, 26, WALK + 1, 36, WOOD);
        Furnish.sack(s, 14, WALK, 36, false);
        s.set(16, top - 1, 38, Kit.hangingLantern()).set(24, top - 1, 38, Kit.hangingLantern()).set(CX, top - 1, 37, Kit.hangingLantern());
        // The balcony in its niche, its floor on corbels, its rail of stone.
        s.fill(19, WALK, z2, 21, 10, z2, AIR);
        s.set(19, 11, z2, Kit.upside(TRIM_STAIRS, "west")).set(21, 11, z2, Kit.upside(TRIM_STAIRS, "east"));
        s.set(CX, 11, z2, AIR).set(CX, 12, z2, CARVED);
        for (int x = 18; x <= 22; x++) {
            s.set(x, 6, SIZE - 1, Kit.upside(TRIM_STAIRS, "north"));
            s.set(x, WALK - 1, SIZE - 1, TRIM);
            s.set(x, WALK, SIZE - 1, WALL_FENCE);
        }
        // The banners of the kingdom: short ones on both sides of the gate, long ones over them, torches.
        for (int x : new int[]{17, 23}) {
            s.set(x, 5, SIZE - 1, Kit.wallBanner("red", "south")).set(x, 3, SIZE - 1, Kit.wallTorch("south"));
            s.set(x, 12, SIZE - 1, Kit.wallBanner("red", "south")).set(x, 10, SIZE - 1, Kit.wallBanner("red", "south"));
        }
        // The parapet on its corbels and lanterns on the roof.
        machicolations(s, x1, z1, x2, z2, top - 1);
        s.set(16, top + 1, 37, Kit.lantern()).set(24, top + 1, 37, Kit.lantern());
    }

    /**
     * The doors from the wall walk into the towers and the gatehouse, so that the walk runs all round through their rooms.
     */
    private static void walkDoors(Structure s) {
        s.door(8, WALK, 3, DOOR, "west", "left").door(3, WALK, 8, DOOR, "north", "left");
        s.door(32, WALK, 3, DOOR, "east", "left").door(37, WALK, 8, DOOR, "north", "left");
        s.door(3, WALK, 33, DOOR, "south", "left").door(7, WALK, 37, DOOR, "west", "left");
        s.door(37, WALK, 33, DOOR, "south", "left").door(33, WALK, 37, DOOR, "east", "left");
        s.door(GATE_X1, WALK, 37, DOOR, "east", "left").door(GATE_X2, WALK, 37, DOOR, "west", "left");
    }

    // ---------------------------------------------------------------- the keep

    /**
     * Fills a box with a block where nothing of the building stands yet.
     */
    private static void fillFree(Structure s, int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    if (s.inside(x, y, z) && !s.solid(x, y, z)) {
                        s.set(x, y, z, state);
                    }
                }
            }
        }
    }

    /**
     * A gable roof whose ridge runs along z, so that its gable looks at the street: rows of stairs rising from both eaves one
     * block per row, a ridge of slabs, the gable walls filled under it; it covers only what is free, so that it can run
     * against a wall that stands already.
     */
    private static void roofZ(Structure s, int x1, int x2, int z1, int z2, int y, Kit.Roof roof, String gable, int wallZ1, int wallZ2) {
        int row = 0;
        while (x1 + row < x2 - row) {
            int h = y + row;
            fillFree(s, x1 + row, h, z1, x1 + row, h, z2, Kit.stairs(roof.stairs(), "east"));
            fillFree(s, x2 - row, h, z1, x2 - row, h, z2, Kit.stairs(roof.stairs(), "west"));
            if (row > 0 && x1 + row + 1 <= x2 - row - 1) {
                for (int wall : new int[]{wallZ1, wallZ2}) {
                    if (wall >= 0) {
                        fillFree(s, x1 + row + 1, h, wall, x2 - row - 1, h, wall, gable);
                    }
                }
            }
            row++;
        }
        if (x1 + row == x2 - row) {
            fillFree(s, x1 + row, y + row, z1, x1 + row, y + row, z2, Kit.slab(roof.slab()));
        }
    }

    /**
     * A gable roof whose ridge runs along x: rows of stairs rising from the back and the front eaves, a ridge of slabs, the
     * gable walls filled under it; like the other one it covers only what is free.
     */
    private static void roofX(Structure s, int x1, int x2, int z1, int z2, int y, Kit.Roof roof, String gable, int wallX1, int wallX2) {
        int row = 0;
        while (z1 + row < z2 - row) {
            int h = y + row;
            fillFree(s, x1, h, z1 + row, x2, h, z1 + row, Kit.stairs(roof.stairs(), "south"));
            fillFree(s, x1, h, z2 - row, x2, h, z2 - row, Kit.stairs(roof.stairs(), "north"));
            if (row > 0 && z1 + row + 1 <= z2 - row - 1) {
                for (int wall : new int[]{wallX1, wallX2}) {
                    if (wall >= 0) {
                        fillFree(s, wall, h, z1 + row + 1, wall, h, z2 - row - 1, gable);
                    }
                }
            }
            row++;
        }
        if (z1 + row == z2 - row) {
            fillFree(s, x1, y + row, z1 + row, x2, y + row, z1 + row, Kit.slab(roof.slab()));
        }
    }

    /**
     * The shell of the keep: walls of stone on a course of rough stone with quoins and bands of trim, buttresses between the
     * bays of its long sides and at its front corners, windows over the aisles and tall arched windows of stained glass in
     * the galleries, the steep roof of tiles with its gable to the street and a dormer on each side, and on the gable front
     * the great portal under a double arch, a tall window over it, a rose window in the gable and banners.
     */
    private static void keep(Structure s) {
        s.walls(KEEP_X1, 1, KEEP_Z1, KEEP_X2, KEEP_TOP, KEEP_Z2, WALL);
        s.walls(KEEP_X1, 1, KEEP_Z1, KEEP_X2, 1, KEEP_Z2, ROUGH);
        s.walls(KEEP_X1, GALLERY, KEEP_Z1, KEEP_X2, GALLERY, KEEP_Z2, TRIM);
        s.walls(KEEP_X1, KEEP_TOP, KEEP_Z1, KEEP_X2, KEEP_TOP, KEEP_Z2, TRIM);
        for (int x : new int[]{KEEP_X1, KEEP_X2}) {
            for (int z : new int[]{KEEP_Z1, KEEP_Z2}) {
                s.fill(x, 1, z, x, KEEP_TOP - 1, z, ROUGH);
            }
        }
        // A cornice under the eaves of the long sides and the roof itself.
        for (int z = KEEP_Z1; z <= KEEP_Z2; z++) {
            s.set(KEEP_X1 - 1, KEEP_TOP - 1, z, Kit.upside(TRIM_STAIRS, "east")).set(KEEP_X2 + 1, KEEP_TOP - 1, z, Kit.upside(TRIM_STAIRS, "west"));
        }
        roofZ(s, KEEP_X1 - 1, KEEP_X2 + 1, KEEP_Z1, KEEP_Z2 + 1, KEEP_TOP, Kit.TILE, WALL, KEEP_Z1, KEEP_Z2);
        // Buttresses of rough stone between the bays and at the front corners, stepped back twice.
        for (int z : new int[]{21, 24, KEEP_Z2}) {
            s.fill(KEEP_X1 - 1, 1, z, KEEP_X1 - 1, 9, z, ROUGH).set(KEEP_X1 - 1, 10, z, Kit.stairs(ROUGH_STAIRS, "east"));
            s.fill(KEEP_X1 - 2, 1, z, KEEP_X1 - 2, 4, z, ROUGH).set(KEEP_X1 - 2, 5, z, Kit.stairs(ROUGH_STAIRS, "east"));
            s.fill(KEEP_X2 + 1, 1, z, KEEP_X2 + 1, 9, z, ROUGH).set(KEEP_X2 + 1, 10, z, Kit.stairs(ROUGH_STAIRS, "west"));
            s.fill(KEEP_X2 + 2, 1, z, KEEP_X2 + 2, 4, z, ROUGH).set(KEEP_X2 + 2, 5, z, Kit.stairs(ROUGH_STAIRS, "west"));
        }
        // Windows of the long sides: low ones in the aisles, tall arched pairs of stained glass in the galleries.
        for (int x : new int[]{KEEP_X1, KEEP_X2}) {
            String out = x == KEEP_X1 ? "east" : "west";
            int sill = x == KEEP_X1 ? x - 1 : x + 1;
            for (int z : new int[]{19, 26}) {
                s.fill(x, 2, z, x, 4, z, Kit.GLASS);
            }
            for (int z : new int[]{19, 20, 22, 23, 25}) {
                s.fill(x, 8, z, x, 11, z, Kit.stainedPane("light_blue"));
                s.set(sill, 7, z, Kit.upside(TRIM_STAIRS, out));
                boolean first = z == 19 || z == 22;
                s.set(x, 12, z, z == 25 ? CARVED : Kit.upside(TRIM_STAIRS, first ? "north" : "south"));
            }
        }
        keepFront(s);
        keepDormer(s, KEEP_X1, 1);
        keepDormer(s, KEEP_X2, -1);
    }

    /**
     * A dormer standing on a long wall of the keep, in the middle of its side: a window between cheeks of stone, its own
     * little gable roof of tiles running into the roof of the keep, open to the hall.
     *
     * @param in the way into the keep along x
     */
    private static void keepDormer(Structure s, int wallX, int in) {
        int y = KEEP_TOP + 1;
        int mid = 22;
        for (int z : new int[]{mid - 1, mid + 1}) {
            s.fill(wallX, y, z, wallX, y + 1, z, WALL).set(wallX + in, y + 1, z, WALL);
        }
        s.fill(wallX, y, mid, wallX, y + 1, mid, Kit.stainedPane("light_blue")).set(wallX + in, y + 1, mid, AIR);
        for (int k = -1; k <= 2; k++) {
            int x = wallX + k * in;
            s.set(x, y + 2, mid - 1, Kit.stairs(Kit.TILE.stairs(), "south")).set(x, y + 2, mid + 1, Kit.stairs(Kit.TILE.stairs(), "north"));
            s.set(x, y + 2, mid, Kit.TILE.block()).set(x, y + 3, mid, Kit.slab(Kit.TILE.slab()));
        }
    }

    /**
     * The gable front of the keep: the portal, the tall window over it, the windows of the aisles and the galleries, the rose
     * window in the gable, the banners of the kingdom and torches by the door.
     */
    private static void keepFront(Structure s) {
        int z = KEEP_Z2;
        // The great portal: three wide, under a double arch with a carved keystone, jambs of trim and a hood over it.
        s.fill(19, 1, z, 21, 4, z, AIR).set(CX, 5, z, AIR);
        s.fill(18, 1, z, 18, 5, z, TRIM).fill(22, 1, z, 22, 5, z, TRIM);
        s.set(19, 5, z, Kit.upside(TRIM_STAIRS, "west")).set(21, 5, z, Kit.upside(TRIM_STAIRS, "east"));
        s.set(19, 6, z, Kit.upside(TRIM_STAIRS, "west")).set(21, 6, z, Kit.upside(TRIM_STAIRS, "east")).set(CX, 6, z, CARVED);
        s.fill(17, 7, z + 1, 23, 7, z + 1, Kit.upside(TRIM_STAIRS, "north"));
        s.set(17, 3, z + 1, Kit.wallTorch("south")).set(23, 3, z + 1, Kit.wallTorch("south"));
        // The tall window over the portal: three lights of stained glass under an arch, on a sill.
        s.fill(19, 8, z + 1, 21, 8, z + 1, Kit.slab(TRIM_SLAB));
        for (int y = 9; y <= 12; y++) {
            s.set(19, y, z, Kit.stainedPane("light_blue")).set(21, y, z, Kit.stainedPane("light_blue"));
            s.set(CX, y, z, Kit.stainedPane(y % 2 == 0 ? "blue" : "yellow"));
        }
        s.set(19, 13, z, Kit.upside(TRIM_STAIRS, "west")).set(21, 13, z, Kit.upside(TRIM_STAIRS, "east"));
        s.set(CX, 13, z, Kit.stainedPane("yellow")).set(CX, KEEP_TOP, z, CARVED);
        // Windows of the aisles and the galleries.
        for (int x : new int[]{14, 26}) {
            s.fill(x, 2, z, x, 4, z, Kit.GLASS);
            s.fill(x, 8, z, x, 11, z, Kit.stainedPane("light_blue"));
            s.set(x, 7, z + 1, Kit.slab(TRIM_SLAB));
        }
        // The rose window in the gable, framed with trim.
        int ry = 18;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                int d2 = dx * dx + dy * dy;
                String state = d2 == 0 ? Kit.stainedPane("yellow") : d2 <= 2 ? Kit.stainedPane("orange")
                        : d2 <= 5 ? Kit.stainedPane(Math.floorMod(dx + dy, 2) == 0 ? "blue" : "light_blue") : d2 <= 9 ? TRIM : null;
                if (state != null) {
                    s.set(CX + dx, ry + dy, z, state);
                }
            }
        }
        // The banners of the kingdom on both sides of the tall window.
        s.set(17, 11, z + 1, Kit.wallBanner("red", "south")).set(23, 11, z + 1, Kit.wallBanner("red", "south"));
    }

    /**
     * The throne hall: a nave paved with trim and aisles floored with planks, a red carpet from the portal to the dais, two
     * arcades of carved columns with capitals of trim carrying galleries over the aisles and arches over the galleries, stairs
     * up to the galleries at the front, the cloth of estate behind the throne under a canopy, banners on the columns, doors to
     * the donjon on both sides of the dais and chandeliers hanging from the ridge.
     */
    private static void throneHall(Structure s) {
        s.fill(KEEP_X1 + 1, 0, KEEP_Z1 + 1, KEEP_X2 - 1, 0, KEEP_Z2, TRIM);
        s.fill(KEEP_X1 + 1, 0, KEEP_Z1 + 1, 15, 0, KEEP_Z2 - 1, PLANKS).fill(25, 0, KEEP_Z1 + 1, KEEP_X2 - 1, 0, KEEP_Z2 - 1, PLANKS);
        // The galleries over the aisles, their rails and the arches under and over them.
        s.fill(KEEP_X1 + 1, GALLERY, KEEP_Z1 + 1, 16, GALLERY, KEEP_Z2 - 1, PLANKS);
        s.fill(24, GALLERY, KEEP_Z1 + 1, KEEP_X2 - 1, GALLERY, KEEP_Z2 - 1, PLANKS);
        for (int x : new int[]{16, 24}) {
            for (int z = KEEP_Z1 + 1; z <= KEEP_Z2 - 1; z++) {
                if (z == 21 || z == 24) {
                    continue;
                }
                String f = z == 19 || z == 22 || z == 25 ? "north" : "south";
                s.set(x, GALLERY - 1, z, Kit.upside(WALL_STAIRS, f)).set(x, 12, z, Kit.upside(WALL_STAIRS, f));
                s.set(x, GALLERY + 1, z, WALL_FENCE);
            }
            for (int z : new int[]{21, 24}) {
                s.set(x, 1, z, TRIM).fill(x, 2, z, x, 4, z, CARVED).set(x, 5, z, TRIM).set(x, 6, z, TRIM);
                s.fill(x, 7, z, x, 11, z, CARVED).set(x, 12, z, TRIM);
                String nave = x == 16 ? "east" : "west";
                int nx = x == 16 ? x + 1 : x - 1;
                s.set(nx, 5, z, Kit.upside(TRIM_STAIRS, opposite(nave)));
                s.set(nx, 4, z, Kit.wallBanner("red", nave));
            }
        }
        // Stairs up to the galleries along the outer walls of the aisles, from the front toward the throne.
        for (int x : new int[]{KEEP_X1 + 1, KEEP_X2 - 1}) {
            for (int k = 1; k <= GALLERY; k++) {
                int z = KEEP_Z2 - 1 - k;
                s.set(x, k, z, Kit.stairs(WALL_STAIRS, "north"));
                if (k > 1) {
                    s.set(x, k - 1, z, Kit.upside(WALL_STAIRS, "south"));
                }
            }
            s.fill(x, GALLERY, 21, x, GALLERY, 22, AIR);
        }
        // The dais, the carpet and the cloth of estate under its canopy.
        s.fill(17, 1, KEEP_Z1 + 1, 23, 1, KEEP_Z1 + 1, Kit.slab(TRIM_SLAB));
        for (int x : new int[]{18, 19, 21, 22}) {
            s.set(x, 1, CZ, Kit.slab(TRIM_SLAB));
        }
        s.set(19, 1, CZ + 1, Kit.slab(TRIM_SLAB)).set(21, 1, CZ + 1, Kit.slab(TRIM_SLAB)).set(CX, 1, CZ + 1, Kit.CLOTH_CARPET);
        s.fill(19, 1, CZ + 2, 21, 1, KEEP_Z2, Kit.CLOTH_CARPET);
        s.fill(19, 3, KEEP_Z1, 21, 10, KEEP_Z1, YELLOW).fill(CX, 3, KEEP_Z1, CX, 9, KEEP_Z1, CLOTH);
        s.fill(17, 2, KEEP_Z1, 17, 10, KEEP_Z1, CLOTH).fill(23, 2, KEEP_Z1, 23, 10, KEEP_Z1, CLOTH);
        s.fill(18, 11, KEEP_Z1 + 1, 22, 11, KEEP_Z1 + 1, Kit.upside(TRIM_STAIRS, "north"));
        s.set(18, 10, KEEP_Z1 + 1, Kit.wallBanner("red", "south")).set(22, 10, KEEP_Z1 + 1, Kit.wallBanner("red", "south"));
        // The doors into the donjon on both sides of the dais.
        s.door(16, 1, KEEP_Z1, DOOR, "south", "left").door(24, 1, KEEP_Z1, DOOR, "south", "right");
        // Light: chandeliers from the ridge, lanterns under and on the galleries.
        for (int z : new int[]{22, 24, 26}) {
            chandelier(s, CX, z, 10, 22);
        }
        for (int x : new int[]{14, 26}) {
            s.set(x, GALLERY - 1, 20, Kit.hangingLantern()).set(x, GALLERY - 1, 24, Kit.hangingLantern());
            s.set(x + (x < CX ? 1 : -1), GALLERY + 1, 19, Kit.lantern()).set(x + (x < CX ? 1 : -1), GALLERY + 1, 26, Kit.lantern());
        }
    }

    // ---------------------------------------------------------------- the donjon

    /**
     * The donjon behind the throne, broader than any tower and the highest of them all by far: square, with quoins and a
     * footing of rough stone, bands of trim at its floors, windows with carved heads on every floor where the roofs of the
     * wings and the keep leave room, an oriel on corbels before the royal chamber, long banners on its sides, an open lookout
     * with arches on every side, over it a parapet with merlons carried out on machicolations, and inside the parapet a tall
     * square spire of slate with a dormer on each face and the flag of the kingdom on its point, the highest point of the
     * capital.
     */
    private static void donjon(Structure s) {
        int top = FLOORS[3];
        int mx = (DON_X1 + DON_X2) / 2;
        int mz = (DON_Z1 + DON_Z2) / 2;
        s.fill(DON_X1, 0, DON_Z1, DON_X2, 0, DON_Z2, ROUGH);
        s.fill(DON_X1 + 1, 1, DON_Z1 + 1, DON_X2 - 1, ROOF, DON_Z2 - 1, AIR);
        s.walls(DON_X1, 1, DON_Z1, DON_X2, ROOF - 1, DON_Z2, WALL);
        s.walls(DON_X1, 1, DON_Z1, DON_X2, 2, DON_Z2, ROUGH);
        for (int x = DON_X1; x <= DON_X2; x++) {
            s.set(x, 1, DON_Z1 - 1, Kit.stairs(ROUGH_STAIRS, "south"));
        }
        for (int x : new int[]{DON_X1, DON_X2}) {
            for (int z : new int[]{DON_Z1, DON_Z2}) {
                s.fill(x, 1, z, x, ROOF - 1, z, ROUGH);
            }
        }
        for (int floor : FLOORS) {
            s.walls(DON_X1, floor, DON_Z1, DON_X2, floor, DON_Z2, TRIM);
            s.fill(DON_X1 + 1, floor, DON_Z1 + 1, DON_X2 - 1, floor, DON_Z2 - 1, PLANKS);
        }
        s.fill(DON_X1 + 1, 0, DON_Z1 + 1, DON_X2 - 1, 0, DON_Z2 - 1, TRIM);
        // Windows: barred ones by the treasury and the armoury, then on every floor where the roofs leave room.
        s.fill(17, 3, DON_Z1, 17, 4, DON_Z1, Kit.BARS).fill(23, 3, DON_Z1, 23, 4, DON_Z1, Kit.BARS);
        for (int x : new int[]{17, 23}) {
            window(s, x, DON_Z1, 11, 12);
            window(s, x, DON_Z1, 15, 17);
        }
        window(s, DON_X1, 9, 11, 12);
        window(s, DON_X2, 9, 11, 12);
        window(s, DON_X2, 15, 11, 12);
        for (int z : new int[]{9, 15}) {
            window(s, DON_X1, z, 15, 17);
        }
        for (int z : new int[]{8, 14}) {
            window(s, DON_X2, z, 15, 17);
        }
        for (int x : new int[]{17, mx, 23}) {
            window(s, x, DON_Z1, 20, 22);
        }
        for (int z : new int[]{10, 14}) {
            window(s, DON_X1, z, 20, 22);
            window(s, DON_X2, z, 20, 22);
        }
        for (int x : new int[]{16, 24}) {
            window(s, x, DON_Z2, 20, 22);
        }
        // An oriel on corbels before the royal chamber, open to the room behind it.
        int oz = DON_Z1 - 1;
        int y = FLOORS[1];
        s.set(mx, y - 2, oz, Kit.upside(TRIM_STAIRS, "south"));
        s.fill(mx - 1, y - 1, oz, mx + 1, y - 1, oz, Kit.upside(TRIM_STAIRS, "south"));
        s.fill(mx - 1, y, oz, mx + 1, y, oz, TRIM);
        s.fill(mx - 1, y + 1, oz, mx - 1, y + 3, oz, WALL).fill(mx + 1, y + 1, oz, mx + 1, y + 3, oz, WALL);
        s.fill(mx, y + 1, oz, mx, y + 3, oz, Kit.stainedPane("light_blue"));
        s.fill(mx, y + 1, DON_Z1, mx, y + 3, DON_Z1, AIR);
        s.fill(mx - 1, y + 4, oz, mx + 1, y + 4, oz, Kit.stairs(TRIM_STAIRS, "south"));
        // A string course on corbels at the floor of the library, on the back and the sides where no roof meets the walls.
        for (int x = DON_X1; x <= DON_X2; x++) {
            s.set(x, FLOORS[2], DON_Z1 - 1, Kit.upside(TRIM_STAIRS, "south"));
        }
        for (int z = DON_Z1; z <= DON_Z2; z++) {
            s.set(DON_X1 - 1, FLOORS[2], z, Kit.upside(TRIM_STAIRS, "east")).set(DON_X2 + 1, FLOORS[2], z, Kit.upside(TRIM_STAIRS, "west"));
        }
        // Long banners of the kingdom on the sides, over the roofs of the wings.
        for (int by : new int[]{14, 16}) {
            s.set(DON_X1 - 1, by, mz, Kit.wallBanner("red", "west")).set(DON_X2 + 1, by, mz, Kit.wallBanner("red", "east"));
        }
        // The lookout: piers at the corners and between three arches on every side, rails between them.
        for (int i = 0; i <= DON_X2 - DON_X1; i++) {
            lookoutSide(s, DON_X1 + i, DON_Z1, i, true);
            lookoutSide(s, DON_X1 + i, DON_Z2, i, true);
            lookoutSide(s, DON_X1, DON_Z1 + i, i, false);
            lookoutSide(s, DON_X2, DON_Z1 + i, i, false);
        }
        s.walls(DON_X1, ROOF - 1, DON_Z1, DON_X2, ROOF - 1, DON_Z2, TRIM);
        // The roof of the lookout with a light set in it and the parapet on its machicolations.
        s.fill(DON_X1, ROOF, DON_Z1, DON_X2, ROOF, DON_Z2, WALL);
        s.walls(DON_X1, ROOF, DON_Z1, DON_X2, ROOF, DON_Z2, TRIM);
        s.set(mx, ROOF, mz, GLOW);
        machicolations(s, DON_X1, DON_Z1, DON_X2, DON_Z2, ROOF - 1);
        // The spire: a tall square cone of slate inside the parapet, dormers on its faces, the flag of the kingdom on its point.
        int tip = squareSpire(s, DON_X1 + 1, DON_Z1 + 1, DON_X2 - 1, DON_Z2 - 1, ROOF + 1, new int[]{3, 3, 3, 2, 2, 1}, Kit.SLATE);
        spireDormer(s, mx, DON_Z2 - 1, 0, 1);
        spireDormer(s, mx, DON_Z1 + 1, 0, -1);
        spireDormer(s, DON_X1 + 1, mz, -1, 0);
        spireDormer(s, DON_X2 - 1, mz, 1, 0);
        int pole = Math.min(tip + 2, HEIGHT - 1);
        s.fill(mx, tip, mz, mx, pole, mz, Kit.fence("spruce"));
        s.fill(mx + 1, pole, mz, mx + 3, pole, mz, CLOTH).fill(mx + 1, pole - 1, mz, mx + 2, pole - 1, mz, CLOTH);
    }

    /**
     * A dormer on a face of the spire of the donjon, standing on its lowest ring so that it shows over the parapet: a window
     * of glass between cheeks of stone that looks into the lit space under the spire, and a small gable of slate over it.
     *
     * @param ox the way out of the face along x
     * @param oz the way out of the face along z
     */
    private static void spireDormer(Structure s, int x, int z, int ox, int oz) {
        int y = ROOF + 4;
        int ax = oz != 0 ? 1 : 0;
        int az = ox != 0 ? 1 : 0;
        String low = ax == 1 ? "east" : "south";
        String high = ax == 1 ? "west" : "north";
        s.fill(x - ax, y, z - az, x - ax, y + 1, z - az, WALL).fill(x + ax, y, z + az, x + ax, y + 1, z + az, WALL);
        s.fill(x, y, z, x, y + 1, z, Kit.GLASS);
        s.fill(x - ox, y, z - oz, x - ox, y + 1, z - oz, AIR);
        s.set(x - ax, y + 2, z - az, Kit.stairs(Kit.SLATE.stairs(), low)).set(x + ax, y + 2, z + az, Kit.stairs(Kit.SLATE.stairs(), high));
        s.set(x, y + 2, z, Kit.SLATE.block()).set(x, y + 3, z, Kit.slab(Kit.SLATE.slab()));
    }

    /**
     * One cell of a side of the lookout: piers at the corners and between three openings on every side, a rail of stone
     * between them, and over every opening an arch of two stairs.
     */
    private static void lookoutSide(Structure s, int x, int z, int i, boolean alongX) {
        int y = FLOORS[3] + 1;
        int k = i % 4;
        if (k == 0) {
            s.fill(x, y, z, x, ROOF - 2, z, i == 0 || i == DON_X2 - DON_X1 ? ROUGH : WALL);
            return;
        }
        s.set(x, y, z, WALL_FENCE).fill(x, y + 1, z, x, ROOF - 3, z, AIR);
        String lowSide = alongX ? "west" : "north";
        String highSide = alongX ? "east" : "south";
        s.set(x, ROOF - 2, z, k == 1 ? Kit.upside(TRIM_STAIRS, lowSide) : k == 3 ? Kit.upside(TRIM_STAIRS, highSide) : AIR);
    }

    /**
     * Inside the donjon: the spiral stair round its newel through every floor, the treasury with its vault behind iron bars
     * and an armoury, the council room, the royal chamber with its hearth and bed of state, the library and the lookout
     * with its bell, all lit.
     */
    private static void donjonRooms(Structure s) {
        spiralStair(s);
        // The ground floor: the vault behind its grille, the antechamber with the counting desk, the armoury.
        int low = FLOORS[0] - 1;
        s.fill(15, 1, 14, 20, low, 14, Kit.BARS);
        s.door(18, 1, 14, DOOR, "south", "left");
        s.fill(21, 1, DON_Z1 + 1, 21, low, 14, WALL);
        s.set(16, 1, 7, Kit.chest("south")).set(19, 1, 7, Kit.chest("south"));
        s.set(17, 1, 7, GOLD).set(18, 1, 7, GOLD).set(17, 2, 7, "minecraft:raw_gold_block");
        Furnish.crate(s, 15, 1, 7, WOOD);
        Furnish.crate(s, 20, 1, 7, WOOD);
        Furnish.crate(s, 20, 2, 7, WOOD);
        Furnish.sack(s, 15, 1, 9, false);
        Furnish.sack(s, 15, 1, 10, false);
        Furnish.sack(s, 15, 2, 9, true);
        s.set(15, 1, 12, "minecraft:emerald_block").set(20, 1, 10, GOLD).set(20, 1, 11, "minecraft:iron_block").set(20, 2, 10, "minecraft:raw_gold_block");
        s.set(18, low, 10, Kit.hangingLantern());
        Furnish.desk(s, 15, 1, 16, "east", WOOD);
        s.set(15, 4, 15, Kit.wallBanner("red", "east")).set(19, low, 16, Kit.hangingLantern());
        Furnish.weaponRack(s, 22, 1, 7, "south", WOOD);
        Furnish.weaponRack(s, 24, 1, 7, "south", WOOD);
        for (int z = 8; z <= 10; z++) {
            Furnish.crate(s, 25, 1, z, WOOD);
        }
        Furnish.crate(s, 25, 2, 9, WOOD);
        s.set(22, 3, 10, Kit.wallBanner("red", "east")).set(23, low, 10, Kit.hangingLantern());
        // The council room: a long table with chairs, shelves of books, a table with the map of the kingdom, a banner.
        int y = FLOORS[0] + 1;
        Furnish.longTable(s, 17, 11, 20, 11, y, WOOD);
        for (int x = 17; x <= 20; x++) {
            s.set(x, y, 10, Kit.stairs(SPRUCE_STAIRS, "north")).set(x, y, 12, Kit.stairs(SPRUCE_STAIRS, "south"));
        }
        s.set(16, y, 11, Kit.stairs(SPRUCE_STAIRS, "west")).set(21, y, 11, Kit.stairs(SPRUCE_STAIRS, "east"));
        s.set(18, y + 1, 11, Kit.lantern()).set(20, y + 1, 11, Furnish.candles(3));
        Furnish.books(s, 15, 11, 15, 15, y, y + 2);
        s.fill(23, y, 8, 24, y, 8, Kit.topSlab(SPRUCE_SLAB));
        s.set(23, y + 1, 8, "minecraft:white_carpet").set(24, y + 1, 8, Furnish.candle(""));
        s.set(CX, y + 2, DON_Z1 + 1, Kit.wallBanner("red", "south"));
        s.set(18, FLOORS[1] - 1, 9, Kit.hangingLantern()).set(18, FLOORS[1] - 1, 15, Kit.hangingLantern());
        s.set(24, FLOORS[1] - 1, 10, Kit.hangingLantern());
        // The royal chamber: a carpet of the kingdom, a hearth with two chairs, a bed of state, a wardrobe, a writing desk.
        y = FLOORS[1] + 1;
        Furnish.rug(s, 16, 8, 21, 13, y, Kit.CLOTH_CARPET);
        s.set(15, y, 11, ROUGH).set(15, y, 13, ROUGH).set(15, y, 12, CAMPFIRE);
        s.fill(15, y + 1, 11, 15, y + 1, 13, Kit.upside(ROUGH_STAIRS, "west")).fill(15, y + 2, 11, 15, FLOORS[2] - 1, 13, ROUGH);
        s.set(17, y, 11, Kit.stairs(SPRUCE_STAIRS, "east")).set(17, y, 13, Kit.stairs(SPRUCE_STAIRS, "east"));
        Furnish.canopyBed(s, 23, y, 10, "east", WOOD);
        for (int x = 15; x <= 16; x++) {
            Furnish.crate(s, x, y, DON_Z1 + 1, WOOD);
            Furnish.crate(s, x, y + 1, DON_Z1 + 1, WOOD);
        }
        Furnish.desk(s, 20, y, 16, "west", WOOD);
        s.set(DON_X2 - 1, y + 2, 12, Kit.wallBanner("red", "west"));
        s.set(19, FLOORS[2] - 1, 11, Kit.hangingLantern()).set(19, FLOORS[2] - 1, 15, Kit.hangingLantern());
        // The library: shelves along the walls, a lectern, a reading table.
        y = FLOORS[2] + 1;
        for (int x = DON_X1 + 1; x <= DON_X2 - 1; x++) {
            if (x != 17 && x != CX && x != 23) {
                Furnish.books(s, x, DON_Z1 + 1, x, DON_Z1 + 1, y, y + 1);
            }
        }
        for (int z = DON_Z1 + 2; z <= DON_Z2 - 1; z++) {
            if (z != 10 && z != 14) {
                Furnish.books(s, DON_X1 + 1, z, DON_X1 + 1, z, y, y + 1);
            }
        }
        s.set(19, y, 12, "minecraft:lectern[facing=south,has_book=false,powered=false]");
        Furnish.longTable(s, 18, 15, 19, 15, y, WOOD);
        s.set(18, y, 16, Kit.stairs(SPRUCE_STAIRS, "south")).set(19, y, 16, Kit.stairs(SPRUCE_STAIRS, "south"));
        s.set(19, y + 1, 15, Kit.lantern());
        s.set(20, FLOORS[3] - 1, 10, Kit.hangingLantern()).set(17, FLOORS[3] - 1, 14, Kit.hangingLantern());
        // The lookout: the great bell and lanterns hanging from its roof, a lantern on the newel.
        y = FLOORS[3] + 1;
        s.set(CX, ROOF - 1, 11, "minecraft:bell[attachment=ceiling,facing=north,powered=false]");
        s.set(17, ROOF - 1, 9, Kit.hangingLantern()).set(23, ROOF - 1, 9, Kit.hangingLantern()).set(17, ROOF - 1, 15, Kit.hangingLantern());
        s.set(NEWEL_X, y, NEWEL_Z, Kit.lantern());
    }

    /**
     * The spiral stair of the donjon: steps of stone turning round a newel lit by glowstone set in it, one block up per
     * step, from the ground floor to the lookout. Each floor is open where the stair passes and closed with hatches over the
     * cells where nobody's head comes.
     */
    private static void spiralStair(Structure s) {
        int last = FLOORS[3];
        s.fill(NEWEL_X, 1, NEWEL_Z, NEWEL_X, last, NEWEL_Z, WALL);
        for (int y : new int[]{5, 12, 17, 22}) {
            s.set(NEWEL_X, y, NEWEL_Z, GLOW);
        }
        for (int floor : FLOORS) {
            for (int j = 0; j < 8; j++) {
                boolean passage = false;
                for (int h = floor - 2; h <= floor; h++) {
                    passage |= Math.floorMod(h - 1 + 4, 8) == j;
                }
                s.set(NEWEL_X + SPIRAL[j][0], floor, NEWEL_Z + SPIRAL[j][1], passage ? AIR : HATCH);
            }
        }
        for (int i = 0; i < last; i++) {
            int[] c = SPIRAL[(i + 4) % 8];
            int[] n = SPIRAL[(i + 5) % 8];
            String dir = n[0] > c[0] ? "east" : n[0] < c[0] ? "west" : n[1] > c[1] ? "south" : "north";
            s.set(NEWEL_X + c[0], i + 1, NEWEL_Z + c[1], Kit.stairs(WALL_STAIRS, dir));
        }
    }

    // ---------------------------------------------------------------- the wings

    /**
     * The kitchen wing leaning on the west side of the donjon: walls of plaster in a frame of oak on a footing of rough
     * stone, a roof of dark shingles with its gable to the west and an outer chimney stack on that gable, and inside a hearth
     * under a hood, ovens, a table, stores and lanterns hanging from a beam.
     */
    private static void kitchen(Structure s) {
        int x1 = 7;
        int x2 = DON_X1 - 1;
        int z1 = 9;
        int z2 = 17;
        int eaves = 7;
        s.fill(x1, 0, z1, x2, 0, z2, ROUGH);
        s.fill(x1, 1, z1, x1, eaves, z2, PLASTER);
        s.fill(x1, 1, z1, x2, eaves, z1, PLASTER).fill(x1, 1, z2, x2, eaves, z2, PLASTER);
        s.fill(x1, 1, z1, x1, 2, z2, ROUGH);
        s.fill(x1, 1, z1, x2, 2, z1, ROUGH).fill(x1, 1, z2, x2, 2, z2, ROUGH);
        // The frame: posts at the corners and between the windows, a beam over the footing and one under the eaves.
        for (int z = z1; z <= z2; z++) {
            s.set(x1, 3, z, Kit.log(OAK_LOG, "z"));
        }
        for (int x = x1; x <= x2; x++) {
            s.set(x, 3, z1, Kit.log(OAK_LOG, "x")).set(x, 3, z2, Kit.log(OAK_LOG, "x"));
            s.set(x, eaves, z1, Kit.log(OAK_LOG, "x")).set(x, eaves, z2, Kit.log(OAK_LOG, "x"));
        }
        for (int[] post : new int[][]{{x1, z1}, {x1, z2}, {x1, 11}, {x1, 15}, {10, z1}, {10, z2}, {x2, z1}, {x2, z2}}) {
            s.fill(post[0], 3, post[1], post[0], eaves, post[1], Kit.log(OAK_LOG, "y"));
        }
        roofX(s, x1, x2, z1 - 1, z2 + 1, eaves, Kit.SHINGLE, PLASTER, x1, -1);
        s.fill(x1, eaves + 1, 13, x1, eaves + 4, 13, Kit.log(OAK_LOG, "y"));
        // Windows, the door and a torch by it.
        s.fill(x1, 4, 10, x1, 5, 10, Kit.GLASS).fill(x1, 4, 16, x1, 5, 16, Kit.GLASS);
        s.fill(12, 4, z2, 12, 5, z2, Kit.GLASS).fill(8, 4, z2, 8, 5, z2, Kit.GLASS);
        s.fill(12, 4, z1, 12, 5, z1, Kit.GLASS).fill(8, 4, z1, 8, 5, z1, Kit.GLASS);
        s.door(10, 1, z2, "minecraft:oak_door", "south", "left");
        s.set(9, 3, z2 + 1, Kit.wallTorch("south"));
        // The chimney stack outside the west gable.
        s.fill(x1 - 1, 1, 13, x1 - 1, 13, 13, Kit.CHIMNEY).set(x1 - 1, 14, 13, Kit.CHIMNEY_POT);
        // Inside: the hearth under its hood, ovens and stores along the north wall, a table, a beam with lanterns.
        s.set(x1 + 1, 1, 12, ROUGH).set(x1 + 1, 1, 14, ROUGH).set(x1 + 1, 1, 13, CAMPFIRE);
        s.fill(x1 + 1, 2, 12, x1 + 1, 2, 14, Kit.upside(ROUGH_STAIRS, "west")).fill(x1 + 1, 3, 12, x1 + 1, 6, 14, ROUGH);
        s.set(9, 1, z1 + 1, "minecraft:smoker[facing=south,lit=false]").set(10, 1, z1 + 1, "minecraft:furnace[facing=south,lit=false]");
        s.set(11, 1, z1 + 1, Kit.barrel()).set(13, 1, z1 + 1, "minecraft:crafting_table");
        Furnish.sack(s, 12, 1, z1 + 1, true);
        Furnish.crate(s, 12, 2, z1 + 1, WOOD);
        s.set(13, 1, 12, "minecraft:cauldron").set(13, 1, 15, HAY).set(13, 2, 15, HAY);
        Furnish.longTable(s, 10, 13, 11, 13, 1, WOOD);
        s.set(10, 1, 14, Kit.stairs(SPRUCE_STAIRS, "south")).set(11, 1, 14, Kit.stairs(SPRUCE_STAIRS, "south"));
        s.fill(x1 + 2, eaves - 1, 13, x2, eaves - 1, 13, Kit.log(LOG, "x"));
        s.set(10, eaves - 2, 13, Kit.hangingLantern()).set(12, eaves - 2, 13, Kit.hangingLantern());
    }

    /**
     * The chapel leaning on the east side of the donjon: stone walls with buttresses, tall lancets of stained glass, a roof
     * of blue-green shingles with its gable to the courtyard, a door under an arch and a small rose window in the gable, a
     * bell cote with a bell under a little pyramid of shingles on its ridge; inside an altar with candles, pews on both sides
     * of a carpet and chandeliers.
     */
    private static void chapel(Structure s) {
        int x1 = DON_X2 + 1;
        int x2 = 33;
        int z1 = 9;
        int z2 = 17;
        int eaves = 9;
        int mid = 30;
        s.fill(x1, 0, z1, x2, 0, z2, ROUGH).fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, TRIM);
        s.walls(x1, 1, z1, x2, eaves, z2, WALL);
        s.walls(x1, 1, z1, x2, 1, z2, ROUGH).walls(x1, eaves, z1, x2, eaves, z2, TRIM);
        roofZ(s, x1 - 1, x2 + 1, z1 - 1, z2 + 1, eaves, Kit.TEAL, WALL, z1, z2);
        // Buttresses on the east side and at the corners.
        for (int z : new int[]{z1, 13, z2}) {
            s.fill(x2 + 1, 1, z, x2 + 1, 5, z, ROUGH).set(x2 + 1, 6, z, Kit.stairs(ROUGH_STAIRS, "west"));
        }
        // Lancets of stained glass: two on the east side, two behind the altar.
        for (int z : new int[]{11, 15}) {
            s.fill(x2, 3, z, x2, 7, z, Kit.stainedPane(z == 11 ? "purple" : "blue")).set(x2, 8, z, CARVED);
        }
        for (int x : new int[]{mid - 1, mid + 1}) {
            s.fill(x, 3, z1, x, 7, z1, Kit.stainedPane("light_blue")).set(x, 8, z1, CARVED);
        }
        // The front: a door under an arch, a small rose window, a slit in the gable.
        s.door(mid, 1, z2, DOOR, "south", "left");
        s.fill(mid - 1, 1, z2, mid - 1, 3, z2, TRIM).fill(mid + 1, 1, z2, mid + 1, 3, z2, TRIM).set(mid, 3, z2, CARVED);
        s.set(mid - 1, 4, z2, Kit.upside(TRIM_STAIRS, "east")).set(mid + 1, 4, z2, Kit.upside(TRIM_STAIRS, "west"));
        s.set(mid, 6, z2, Kit.stainedPane("yellow")).set(mid - 1, 6, z2, Kit.stainedPane("red")).set(mid + 1, 6, z2, Kit.stainedPane("red"));
        s.set(mid, 5, z2, Kit.stainedPane("red")).set(mid, 7, z2, Kit.stainedPane("red"));
        s.fill(mid, 10, z2, mid, 11, z2, Kit.GLASS);
        s.set(mid + 2, 3, z2 + 1, Kit.wallTorch("south"));
        // The bell cote on the ridge: four piers, the bell, a little pyramid of shingles over them.
        int bz = z2 - 1;
        for (int x : new int[]{mid - 1, mid + 1}) {
            for (int z : new int[]{bz - 1, bz + 1}) {
                s.fill(x, eaves + 4, z, x, eaves + 6, z, WALL);
            }
        }
        s.fill(mid - 1, eaves + 7, bz - 1, mid + 1, eaves + 7, bz + 1, TRIM);
        s.set(mid, eaves + 6, bz, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        Kit.spire(s, mid - 1, bz - 1, mid + 1, bz + 1, eaves + 8, Kit.TEAL);
        // Inside: the altar with candles and a banner over it, pews, a carpet, chandeliers.
        s.fill(mid - 1, 1, z1 + 1, mid + 1, 1, z1 + 1, TRIM).set(mid, 1, z1 + 1, CARVED);
        s.set(mid - 1, 2, z1 + 1, Furnish.candles(3)).set(mid + 1, 2, z1 + 1, Furnish.candles(3));
        s.set(mid, 2, z1 + 1, GOLD).set(mid, 5, z1 + 1, "minecraft:white_wall_banner[facing=south]");
        for (int z = 12; z <= 15; z++) {
            for (int x : new int[]{x1 + 1, x1 + 2, x2 - 2, x2 - 1}) {
                s.set(x, 1, z, Kit.stairs(SPRUCE_STAIRS, "south"));
            }
        }
        s.fill(mid, 1, z1 + 2, mid, 1, z2 - 1, Kit.CLOTH_CARPET);
        chandelier(s, mid, 12, 6, eaves + 3);
        chandelier(s, mid, 15, 6, eaves + 3);
    }

    // ---------------------------------------------------------------- the courtyard

    /**
     * The courtyard: the paved forecourt between the gate and the keep with the fountain in its middle, stairs up to the
     * wall walk on both sides, a well in the yard of the kitchen with a woodpile and hay, stores behind the donjon and lamp
     * posts along the ways.
     */
    private static void courtyard(Structure s) {
        // The forecourt, paved with trim, and the way along the front of the keep.
        s.fill(14, 0, KEEP_Z2 + 1, 26, 0, GATE_Z1 - 1, TRIM);
        s.fill(5, 0, KEEP_Z2 + 1, 35, 0, KEEP_Z2 + 1, TRIM);
        s.fill(5, 0, 18, 5, 0, KEEP_Z2 + 1, TRIM).fill(35, 0, 18, 35, 0, KEEP_Z2 + 1, TRIM);
        // The fountain: a round basin with a rim of trim, a column with a bowl and a lantern on it.
        Kit.disc(s, CX, 0, FOUNTAIN_Z, 5, TRIM);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (Kit.inDisc(dx, dz, 5)) {
                    s.set(CX + dx, 1, FOUNTAIN_Z + dz, Kit.onRim(dx, dz, 5) ? TRIM : WATER);
                }
            }
        }
        s.fill(CX, 1, FOUNTAIN_Z, CX, 2, FOUNTAIN_Z, CARVED).set(CX, 3, FOUNTAIN_Z, TRIM).set(CX, 4, FOUNTAIN_Z, Kit.lantern());
        s.set(CX - 1, 3, FOUNTAIN_Z, Kit.upside(TRIM_STAIRS, "east")).set(CX + 1, 3, FOUNTAIN_Z, Kit.upside(TRIM_STAIRS, "west"));
        s.set(CX, 3, FOUNTAIN_Z - 1, Kit.upside(TRIM_STAIRS, "south")).set(CX, 3, FOUNTAIN_Z + 1, Kit.upside(TRIM_STAIRS, "north"));
        // Stairs up to the wall walk along the inner faces of the side walls.
        for (int x : new int[]{4, SIZE - 5}) {
            for (int k = 1; k < WALK; k++) {
                int z = 32 - k;
                if (k > 1) {
                    s.fill(x, 1, z, x, k - 1, z, WALL);
                }
                s.set(x, k, z, Kit.stairs(WALL_STAIRS, "north"));
            }
        }
        // Lamp posts at the corners of the forecourt and along the side ways.
        for (int[] p : new int[][]{{15, 29}, {25, 29}, {15, 33}, {25, 33}, {8, 19}, {32, 19}}) {
            lampPost(s, p[0], p[1]);
        }
        // The yard of the kitchen: a well with a roof, a woodpile, hay and crates.
        int wx = 7;
        int wz = 25;
        s.walls(wx - 1, 1, wz - 1, wx + 1, 1, wz + 1, ROUGH);
        s.set(wx, 0, wz, ROUGH).set(wx, 1, wz, WATER);
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            s.fill(wx + c[0], 2, wz + c[1], wx + c[0], 3, wz + c[1], Kit.fence("oak"));
        }
        s.fill(wx - 1, 4, wz - 1, wx + 1, 4, wz + 1, Kit.slab(Kit.SHINGLE.slab()));
        s.set(wx, 3, wz, Kit.hangingLantern());
        s.fill(5, 1, 10, 5, 2, 12, Kit.log(LOG, "z")).fill(6, 1, 10, 6, 1, 12, Kit.log(LOG, "z"));
        Furnish.crate(s, 8, 1, 18, WOOD);
        Furnish.crate(s, 9, 1, 18, WOOD);
        Furnish.crate(s, 8, 2, 18, WOOD);
        s.set(5, 1, 15, HAY).set(6, 1, 15, HAY).set(5, 2, 15, HAY);
        // Stores behind the donjon.
        Furnish.crate(s, 10, 1, 4, WOOD);
        Furnish.crate(s, 11, 1, 4, WOOD);
        Furnish.crate(s, 10, 2, 4, WOOD);
        s.set(29, 1, 4, HAY).set(30, 1, 4, HAY).set(30, 2, 4, HAY);
    }

    /**
     * The gardens: beds of grass on both sides of the forecourt and in the yard of the chapel, edged with low hedges, with
     * flowers and a tree in each.
     */
    private static void gardens(Structure s) {
        String[] flowers = {"minecraft:poppy", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:allium",
                "minecraft:dandelion", "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:white_tulip"};
        int[][] beds = {{6, 29, 13, 33}, {27, 29, 34, 33}, {29, 20, 34, 25}};
        int n = 0;
        for (int[] bed : beds) {
            for (int z = bed[1]; z <= bed[3]; z++) {
                for (int x = bed[0]; x <= bed[2]; x++) {
                    if (s.solid(x, 1, z) || s.solid(x, 0, z) && !s.get(x, 0, z).equals(ROUGH)) {
                        continue;
                    }
                    s.set(x, 0, z, GRASS);
                    boolean edge = x == bed[0] || x == bed[2] || z == bed[1] || z == bed[3];
                    if (edge) {
                        s.set(x, 0, z, DIRT).set(x, 1, z, Kit.leaves("oak"));
                    } else if (Math.floorMod(x * 3 + z * 5, 4) == 0) {
                        s.set(x, 1, z, flowers[n++ % flowers.length]);
                    }
                }
            }
        }
        // Openings in the hedges where the ways go in.
        for (int[] gap : new int[][]{{13, 31}, {27, 31}, {6, 31}, {34, 31}, {31, 25}}) {
            s.set(gap[0], 0, gap[1], GRASS).set(gap[0], 1, gap[1], AIR);
        }
        tree(s, 9, 31, 4);
        tree(s, 31, 31, 4);
        tree(s, 32, 22, 3);
    }
}
