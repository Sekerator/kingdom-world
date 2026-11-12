package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle of a medium kingdom of the world, the palace in the middle of its capital. A curtain wall with a walk behind
 * battlements runs all round between six round towers: four big ones at the corners under cones of slate, the two at the
 * back the tallest, and two smaller ones with battlements in the middle of the side walls. In the south wall a gatehouse with
 * twin crenellated towers guards a deep vaulted passage with a portcullis, a balcony over the gate and the banners of the
 * kingdom on both sides. Inside lies a courtyard with a fountain on the way from the gate to the keep, gardens with trees and flower beds on
 * both sides and lamp posts along the paths.
 *
 * <p>The keep holds the throne hall: a nave with two aisles under galleries, a gable front with a great portal, a tall
 * window and a rose window, corbelled turrets at its front corners and a steep roof of tiles. The throne stands in the
 * world before the castle is built, in the middle of the ground at the north end of the hall; the castle leaves it and the
 * ground under it out. Behind the throne rises the donjon, the highest point of the capital: the treasury on its ground
 * floor, the council room, the royal chamber and the library above, a spiral stair round a newel through all of them, an
 * open lookout under a tall spire and the flag of the kingdom on its point. A timbered kitchen wing leans on its west side,
 * a chapel with a bell cote on its east side.
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
    private static final String BOOKSHELF = "minecraft:bookshelf";
    private static final String GOLD = "minecraft:gold_block";
    private static final String YELLOW = "minecraft:yellow_wool";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String ROD = "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]";
    private static final String HATCH = Kit.trapdoor("minecraft:oak_trapdoor", "north", true);

    // ---------------------------------------------------------------- the plan

    /** The box: 41 by 41, 44 high; the throne in its middle. */
    private static final int SIZE = 41;
    private static final int HEIGHT = 44;
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
    /** The donjon behind the throne, its floors (the last one that of the lookout) and the roof over the lookout. */
    private static final int DON_X1 = 15;
    private static final int DON_X2 = 25;
    private static final int DON_Z1 = 8;
    private static final int DON_Z2 = 18;
    private static final int[] FLOORS = {9, 14, 19, 23};
    private static final int SPIRE = 27;
    /** The newel of the spiral stair in the donjon and the eight cells around it in the order they climb. */
    private static final int NEWEL_X = 23;
    private static final int NEWEL_Z = 15;
    private static final int[][] SPIRAL = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
    /** The gate passage, from the courtyard to the street. */
    private static final int GATE_Z1 = 35;
    private static final int GATE_Z2 = 39;
    /** The fountain in the courtyard, on the way from the gate to the keep. */
    private static final int FOUNTAIN_Z = 31;

    private MediumCastle() {
    }

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
     * The castle of a medium kingdom: the curtain wall with its six towers and the gatehouse, the keep with the throne hall,
     * the donjon behind it, the kitchen and the chapel wings and the courtyard with its fountain and gardens.
     */
    static Structure castle() {
        Structure s = new Structure(SIZE, HEIGHT, SIZE);
        ground(s);
        curtainWalls(s);
        int[] tall = {1, 2, 2, 2, 2, 2};
        int[] flared = {1, 1, 2, 2, 2, 2};
        roundTower(s, 4, 4, 9, 16, tall, 4, 1, "south", true);
        roundTower(s, 36, 4, 9, 16, tall, 36, 1, "south", true);
        roundTower(s, 4, 36, 9, 14, flared, -1, -1, null, false);
        roundTower(s, 36, 36, 9, 14, flared, -1, -1, null, false);
        roundTower(s, 3, 20, 7, 12, null, 1, 20, "east", true);
        roundTower(s, 37, 20, 7, 12, null, 39, 20, "west", true);
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

    private static String opposite(String side) {
        return switch (side) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    private static int outsideSides(int dx, int dz, int diameter) {
        int n = 0;
        n += Kit.inDisc(dx + 1, dz, diameter) ? 0 : 1;
        n += Kit.inDisc(dx - 1, dz, diameter) ? 0 : 1;
        n += Kit.inDisc(dx, dz + 1, diameter) ? 0 : 1;
        n += Kit.inDisc(dx, dz - 1, diameter) ? 0 : 1;
        return n;
    }

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
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
     * height of the walk that the walk runs through, for a tall tower a second room over it, bands of trim, windows on its
     * four sides and a ring of corbels at its top. Over the corbels either a cone of slate open to the room under it, with a
     * banner of the kingdom or a rod on its point, or a parapet with merlons round a platform reached by a ladder, with lanterns
     * in the crenels and a banner on a pole.
     *
     * @param top   the last layer of its wall
     * @param rises the rises of the rings of its cone, or null for a platform with battlements
     * @param lx    the ladder up from the guard room (x, z and the way it faces), standing against the side away from the
     *              courtyard, or -1 for none
     */
    private static void roundTower(Structure s, int cx, int cz, int d, int top, int[] rises, int lx, int lz, String lf, boolean flag) {
        int r = (d - 1) / 2;
        boolean upper = top >= 15;
        boolean battlements = rises == null;
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
                        state = y <= 2 ? ROUGH : y == WALK - 1 || y == 12 ? TRIM : WALL;
                    } else if (y < WALK - 1) {
                        state = ROUGH;
                    } else if (y == WALK - 1 || upper && y == 12) {
                        state = PLANKS;
                    } else if (battlements && y == top) {
                        state = WALL;
                    } else {
                        state = AIR;
                    }
                    put(s, cx + dx, y, cz + dz, state);
                }
            }
        }
        batterRing(s, cx, cz, d);
        // Windows on the four sides, but not behind the ladder.
        int sx = -100;
        int sz = -100;
        if (lx >= 0) {
            sx = lx + (lf.equals("east") ? -1 : lf.equals("west") ? 1 : 0);
            sz = lz + (lf.equals("south") ? -1 : lf.equals("north") ? 1 : 0);
            for (int y = WALK; y <= (battlements ? top : 12); y++) {
                s.set(lx, y, lz, Kit.ladder(lf));
            }
        }
        for (int[] side : new int[][]{{0, -r}, {0, r}, {-r, 0}, {r, 0}}) {
            int x = cx + side[0];
            int z = cz + side[1];
            if (!s.inside(x, 0, z) || x == sx && z == sz) {
                continue;
            }
            s.set(x, 10, z, Kit.GLASS).set(x, 11, z, Kit.GLASS);
            if (upper) {
                s.set(x, 13, z, Kit.GLASS).set(x, 14, z, Kit.GLASS);
            }
        }
        s.set(cx, WALK, cz, Kit.lantern());
        if (upper) {
            s.set(cx, 13, cz, Kit.lantern());
        }
        corbelRing(s, cx, top, cz, d, TRIM_STAIRS);
        if (battlements) {
            int rr = r + 1;
            for (int dz = -rr; dz <= rr; dz++) {
                for (int dx = -rr; dx <= rr; dx++) {
                    if (Kit.inDisc(dx, dz, d + 2) && !Kit.inDisc(dx, dz, d) && s.inside(cx + dx, top + 1, cz + dz)) {
                        s.set(cx + dx, top + 1, cz + dz, WALL);
                        boolean merlon = Math.floorMod(dx + dz, 2) == 0;
                        s.set(cx + dx, top + 2, cz + dz, merlon ? WALL : dx == 0 || dz == 0 ? Kit.lantern() : AIR);
                    }
                }
            }
            s.set(cx, top + 1, cz, Kit.fence("spruce")).set(cx, top + 2, cz, Kit.fence("spruce")).set(cx, top + 3, cz, Kit.banner("red", 0));
            return;
        }
        int tip = cone(s, cx, top + 1, cz, d, rises, Kit.SLATE);
        if (flag) {
            s.set(cx, tip, cz, Kit.fence("spruce")).set(cx, tip + 1, cz, Kit.fence("spruce")).set(cx, tip + 2, cz, Kit.banner("red", 0));
        } else {
            s.set(cx, tip, cz, ROD);
        }
    }

    /**
     * The gatehouse in the middle of the south wall: twin round towers with battlements, between them a block over a vaulted
     * passage with arches at both mouths and a portcullis hung behind the outer arch, a guard room over the passage that the
     * wall walk runs through, a balcony in an arched niche over the gate on a row of corbels, battlements over it, the
     * banners of the kingdom on both sides of the gate and lanterns in the passage.
     */
    private static void gatehouse(Structure s) {
        s.fill(16, 1, GATE_Z1, 24, 12, GATE_Z2, WALL);
        s.fill(16, 1, GATE_Z1, 24, 2, GATE_Z1, ROUGH).fill(16, 1, GATE_Z2, 24, 2, GATE_Z2, ROUGH);
        s.fill(16, WALK - 1, GATE_Z1, 24, WALK - 1, GATE_Z1, TRIM).fill(16, WALK - 1, GATE_Z2, 24, WALK - 1, GATE_Z2, TRIM);
        roundTower(s, 15, 37, 7, 17, null, 15, 39, "north", true);
        roundTower(s, 25, 37, 7, 17, null, 25, 39, "north", true);
        // The passage, paved, under a vault of stairs, with arches at both mouths.
        s.fill(19, 0, GATE_Z1 - 1, 21, 0, SIZE - 1, TRIM);
        s.fill(17, 0, SIZE - 1, 23, 0, SIZE - 1, TRIM);
        s.fill(19, 1, GATE_Z1, 21, 5, GATE_Z2, AIR);
        for (int z = GATE_Z1; z <= GATE_Z2; z++) {
            s.set(19, 5, z, Kit.upside(WALL_STAIRS, "west")).set(21, 5, z, Kit.upside(WALL_STAIRS, "east"));
        }
        for (int z : new int[]{GATE_Z1, GATE_Z2}) {
            s.set(19, 5, z, Kit.upside(TRIM_STAIRS, "west")).set(21, 5, z, Kit.upside(TRIM_STAIRS, "east"));
            s.set(18, 6, z, TRIM).set(22, 6, z, TRIM).set(20, 6, z, CARVED);
            s.set(19, 6, z, Kit.upside(TRIM_STAIRS, "west")).set(21, 6, z, Kit.upside(TRIM_STAIRS, "east"));
            s.fill(18, 3, z, 18, 5, z, TRIM).fill(22, 3, z, 22, 5, z, TRIM);
        }
        // The portcullis, half raised behind the outer arch.
        s.fill(19, 4, GATE_Z2 - 1, 21, 4, GATE_Z2 - 1, Kit.BARS).set(20, 5, GATE_Z2 - 1, Kit.BARS);
        s.set(20, 5, 37, Kit.hangingLantern()).set(20, 5, GATE_Z1 + 1, Kit.hangingLantern());
        s.set(19, 3, 37, Kit.wallTorch("east")).set(21, 3, 37, Kit.wallTorch("west"));
        // The guard room over the passage, with doors to the towers and to the balcony.
        s.fill(19, WALK - 1, 36, 21, WALK - 1, 37, PLANKS);
        s.fill(19, WALK, 36, 21, 11, 37, AIR);
        s.door(18, WALK, 37, DOOR, "east", "left").door(22, WALK, 37, DOOR, "west", "left");
        s.door(20, WALK, GATE_Z2 - 1, DOOR, "south", "left");
        s.set(20, 9, GATE_Z1, Kit.GLASS).set(20, 10, GATE_Z1, Kit.GLASS);
        s.set(19, 11, 36, Kit.hangingLantern()).set(21, WALK, 36, Kit.barrel()).set(21, WALK + 1, 36, Kit.barrel());
        // The balcony in its niche, its floor on corbels, its rail of stone.
        s.fill(19, WALK, GATE_Z2, 21, 10, GATE_Z2, AIR);
        s.set(19, 11, GATE_Z2, Kit.upside(TRIM_STAIRS, "west")).set(21, 11, GATE_Z2, Kit.upside(TRIM_STAIRS, "east"));
        s.set(20, 11, GATE_Z2, AIR).set(20, 12, GATE_Z2, CARVED);
        for (int x = 18; x <= 22; x++) {
            s.set(x, 6, SIZE - 1, Kit.upside(TRIM_STAIRS, "north"));
            s.set(x, WALK - 1, SIZE - 1, TRIM);
            s.set(x, WALK, SIZE - 1, WALL_FENCE);
            // Battlements over the niche on a second row of corbels.
            s.set(x, 12, SIZE - 1, Kit.upside(TRIM_STAIRS, "north"));
            s.set(x, 13, SIZE - 1, WALL);
            if (x % 2 == 0) {
                s.set(x, 14, SIZE - 1, WALL);
            }
        }
        s.set(19, 10, GATE_Z2, Kit.wallBanner("red", "south")).set(21, 10, GATE_Z2, Kit.wallBanner("red", "south"));
        for (int x = 18; x <= 22; x++) {
            s.set(x, 13, GATE_Z1, WALL);
            if (x % 2 == 0) {
                s.set(x, 14, GATE_Z1, WALL);
            }
        }
        s.set(20, 13, 37, Kit.lantern());
        // The banners of the kingdom on both sides of the gate and long ones on the outer sides of the towers.
        s.set(17, 5, SIZE - 1, Kit.wallBanner("red", "south")).set(23, 5, SIZE - 1, Kit.wallBanner("red", "south"));
        for (int y : new int[]{11, 13}) {
            s.set(13, y, SIZE - 1, Kit.wallBanner("red", "south")).set(27, y, SIZE - 1, Kit.wallBanner("red", "south"));
        }
        s.set(17, 3, SIZE - 1, Kit.wallTorch("south")).set(23, 3, SIZE - 1, Kit.wallTorch("south"));
    }

    /**
     * The doors from the wall walk into the towers, so that the walk runs all round through their guard rooms.
     */
    private static void walkDoors(Structure s) {
        s.door(8, WALK, 3, DOOR, "west", "left").door(3, WALK, 8, DOOR, "north", "left");
        s.door(32, WALK, 3, DOOR, "east", "left").door(37, WALK, 8, DOOR, "north", "left");
        s.door(3, WALK, 32, DOOR, "south", "left").door(8, WALK, 37, DOOR, "west", "left");
        s.door(37, WALK, 32, DOOR, "south", "left").door(32, WALK, 37, DOOR, "east", "left");
        s.door(3, WALK, 17, DOOR, "north", "left").door(3, WALK, 23, DOOR, "south", "left");
        s.door(37, WALK, 17, DOOR, "north", "left").door(37, WALK, 23, DOOR, "south", "left");
        s.door(12, WALK, 37, DOOR, "west", "left").door(28, WALK, 37, DOOR, "east", "left");
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
     * bays of its long sides, windows over the aisles and tall arched windows of stained glass in the galleries, the steep
     * roof of tiles with its gable to the street, and on the gable front the great portal under a double arch, a tall
     * window over it, a rose window in the gable, banners and two turrets corbelled out at its corners.
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
        // Buttresses of rough stone between the bays, stepped back twice.
        for (int z : new int[]{21, 24}) {
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
        s.set(CX, KEEP_TOP + 10, KEEP_Z2, Kit.fence("spruce")).set(CX, KEEP_TOP + 11, KEEP_Z2, Kit.banner("red", 0));
        bartizan(s, KEEP_X1 - 1, KEEP_Z2 + 1);
        bartizan(s, KEEP_X2 + 1, KEEP_Z2 + 1);
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
     * A turret corbelled out from a front corner of the keep: an inverted cone of rough stone on the corner, a hollow round
     * shaft lit inside with slits on its outer sides and a band of trim, a cornice and a cone of slate with a rod.
     */
    private static void bartizan(Structure s, int cx, int cz) {
        int toCornerX = cx < CX ? 1 : -1;
        s.set(cx, 7, cz, ROUGH);
        s.set(cx + toCornerX, 7, cz, Kit.upside(ROUGH_STAIRS, "north"));
        s.set(cx, 7, cz - 1, Kit.upside(ROUGH_STAIRS, toCornerX > 0 ? "east" : "west"));
        Kit.disc(s, cx, 8, cz, 3, ROUGH);
        corbelRing(s, cx, 8, cz, 3, ROUGH_STAIRS);
        for (int y = 9; y <= 18; y++) {
            Kit.disc(s, cx, y, cz, 5, y == 14 ? TRIM : WALL);
            Kit.disc(s, cx, y, cz, 3, AIR);
        }
        s.set(cx, 9, cz, Kit.lantern());
        for (int y : new int[]{11, 12, 16}) {
            s.set(cx - 2 * toCornerX, y, cz, Kit.GLASS).set(cx, y, cz + 2, Kit.GLASS);
        }
        corbelRing(s, cx, 18, cz, 5, TRIM_STAIRS);
        int tip = cone(s, cx, 19, cz, 5, new int[]{1, 2, 2, 3}, Kit.SLATE);
        s.set(cx, tip, cz, ROD);
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
     * The donjon behind the throne, the highest tower of the capital: square, with quoins and a footing of rough stone, bands
     * of trim at its floors, windows with carved heads on every floor, an oriel on corbels before the royal chamber, long
     * banners on its sides, a ledge on corbels under an open lookout with arches on every side, a roof with a parapet and
     * merlons on a second row of corbels, pinnacles at its corners and in the middle a slender spire of slate, the highest
     * point of the capital, with the flag of the kingdom on its point.
     */
    private static void donjon(Structure s) {
        int top = FLOORS[3];
        int mx = (DON_X1 + DON_X2) / 2;
        int mz = (DON_Z1 + DON_Z2) / 2;
        s.fill(DON_X1, 0, DON_Z1, DON_X2, 0, DON_Z2, ROUGH);
        s.fill(DON_X1 + 1, 1, DON_Z1 + 1, DON_X2 - 1, SPIRE, DON_Z2 - 1, AIR);
        s.walls(DON_X1, 1, DON_Z1, DON_X2, top - 1, DON_Z2, WALL);
        s.walls(DON_X1, 1, DON_Z1, DON_X2, 2, DON_Z2, ROUGH);
        for (int x = DON_X1; x <= DON_X2; x++) {
            s.set(x, 1, DON_Z1 - 1, Kit.stairs(ROUGH_STAIRS, "south"));
        }
        for (int x : new int[]{DON_X1, DON_X2}) {
            for (int z : new int[]{DON_Z1, DON_Z2}) {
                s.fill(x, 1, z, x, top - 1, z, ROUGH);
            }
        }
        for (int i = 0; i < 3; i++) {
            s.walls(DON_X1, FLOORS[i], DON_Z1, DON_X2, FLOORS[i], DON_Z2, TRIM);
            s.fill(DON_X1 + 1, FLOORS[i], DON_Z1 + 1, DON_X2 - 1, FLOORS[i], DON_Z2 - 1, PLANKS);
        }
        s.fill(DON_X1 + 1, 0, DON_Z1 + 1, DON_X2 - 1, 0, DON_Z2 - 1, TRIM);
        // Windows: barred ones by the treasury, then on every floor where the roofs of the wings and the keep leave room.
        s.fill(18, 3, DON_Z1, 18, 4, DON_Z1, Kit.BARS).fill(23, 3, DON_Z1, 23, 4, DON_Z1, Kit.BARS);
        for (int x : new int[]{17, mx, 23}) {
            s.fill(x, 11, DON_Z1, x, 12, DON_Z1, Kit.GLASS);
            s.fill(x, 15, DON_Z1, x, 17, DON_Z1, Kit.GLASS);
        }
        for (int z : new int[]{11, 15}) {
            s.fill(DON_X1, 15, z, DON_X1, 17, z, Kit.GLASS);
        }
        s.fill(DON_X2, 15, 11, DON_X2, 17, 11, Kit.GLASS);
        for (int x : new int[]{17, 23}) {
            s.fill(x, 20, DON_Z1, x, 21, DON_Z1, Kit.GLASS);
        }
        s.fill(DON_X1, 20, mz, DON_X1, 21, mz, Kit.GLASS).fill(DON_X2, 20, mz, DON_X2, 21, mz, Kit.GLASS);
        // Carved heads over the windows of the upper floors.
        for (int x : new int[]{17, mx, 23}) {
            s.set(x, 13, DON_Z1, CARVED).set(x, 18, DON_Z1, CARVED);
        }
        for (int x : new int[]{17, 23}) {
            s.set(x, 22, DON_Z1, CARVED);
        }
        s.set(DON_X1, 18, 11, CARVED).set(DON_X1, 18, 15, CARVED).set(DON_X2, 18, 11, CARVED);
        s.set(DON_X1, 22, mz, CARVED).set(DON_X2, 22, mz, CARVED);
        // An oriel on corbels before the royal chamber, open to the room behind it.
        int oz = DON_Z1 - 1;
        s.set(mx, FLOORS[1] - 2, oz, Kit.upside(TRIM_STAIRS, "south"));
        s.fill(mx - 1, FLOORS[1] - 1, oz, mx + 1, FLOORS[1] - 1, oz, Kit.upside(TRIM_STAIRS, "south"));
        s.fill(mx - 1, FLOORS[1], oz, mx + 1, FLOORS[1], oz, TRIM);
        s.fill(mx - 1, FLOORS[1] + 1, oz, mx - 1, FLOORS[1] + 3, oz, WALL).fill(mx + 1, FLOORS[1] + 1, oz, mx + 1, FLOORS[1] + 3, oz, WALL);
        s.fill(mx, FLOORS[1] + 1, oz, mx, FLOORS[1] + 3, oz, Kit.stainedPane("light_blue"));
        s.fill(mx, FLOORS[1] + 1, DON_Z1, mx, FLOORS[1] + 3, DON_Z1, AIR);
        s.fill(mx - 1, FLOORS[1] + 4, oz, mx + 1, FLOORS[1] + 4, oz, Kit.stairs(TRIM_STAIRS, "north"));
        // Long banners of the kingdom on the sides, over the roofs of the wings.
        for (int y : new int[]{14, 16}) {
            s.set(DON_X1 - 1, y, mz, Kit.wallBanner("red", "west")).set(DON_X2 + 1, y, mz, Kit.wallBanner("red", "east"));
        }
        // The ledge of the lookout on its corbels, the floor, the piers with rails and arches between them.
        corbelledRing(s, top, TRIM);
        s.fill(DON_X1 + 1, top, DON_Z1 + 1, DON_X2 - 1, top, DON_Z2 - 1, PLANKS);
        s.walls(DON_X1, top, DON_Z1, DON_X2, top, DON_Z2, TRIM);
        for (int i = 0; i <= 10; i++) {
            lookoutSide(s, DON_X1 + i, DON_Z1, i);
            lookoutSide(s, DON_X1 + i, DON_Z2, i);
            lookoutSide(s, DON_X1, DON_Z1 + i, i);
            lookoutSide(s, DON_X2, DON_Z1 + i, i);
        }
        // The roof of the lookout with a light set in it, a parapet with merlons on corbels round its edge, pinnacles.
        s.fill(DON_X1, SPIRE, DON_Z1, DON_X2, SPIRE, DON_Z2, WALL);
        s.walls(DON_X1, SPIRE, DON_Z1, DON_X2, SPIRE, DON_Z2, TRIM);
        s.set(mx, SPIRE, mz, GLOW);
        corbelledRing(s, SPIRE + 1, WALL);
        for (int x = DON_X1 - 1; x <= DON_X2 + 1; x++) {
            for (int z = DON_Z1 - 1; z <= DON_Z2 + 1; z++) {
                boolean edge = x < DON_X1 || x > DON_X2 || z < DON_Z1 || z > DON_Z2;
                if (edge && Math.floorMod(x + z, 2) == 0) {
                    s.set(x, SPIRE + 2, z, WALL);
                }
            }
        }
        for (int x : new int[]{DON_X1 - 1, DON_X2 + 1}) {
            for (int z : new int[]{DON_Z1 - 1, DON_Z2 + 1}) {
                s.set(x, SPIRE + 2, z, WALL).set(x, SPIRE + 3, z, CARVED).set(x, SPIRE + 4, z, Kit.slab(Kit.SLATE.slab()));
            }
        }
        // The spire: a slender cone of slate rising from the roof, lanterns round its foot, the flag of the kingdom on its point.
        int tip = cone(s, mx, SPIRE + 1, mz, 9, new int[]{1, 2, 2, 2, 2, 3}, Kit.SLATE);
        for (int[] spot : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            s.set(mx + spot[0], SPIRE + 1, mz + spot[1], Kit.lantern());
        }
        s.set(mx, tip, mz, Kit.fence("spruce")).set(mx, tip + 1, mz, Kit.fence("spruce"));
        s.fill(mx + 1, tip + 1, mz, mx + 3, tip + 1, mz, CLOTH).fill(mx + 1, tip, mz, mx + 2, tip, mz, CLOTH);
    }

    /**
     * A ring one block outside the walls of the donjon at a layer, carried on stairs hanging upside down under it.
     */
    private static void corbelledRing(Structure s, int y, String state) {
        for (int x = DON_X1 - 1; x <= DON_X2 + 1; x++) {
            for (int z = DON_Z1 - 1; z <= DON_Z2 + 1; z++) {
                if (x < DON_X1 || x > DON_X2 || z < DON_Z1 || z > DON_Z2) {
                    String in = x < DON_X1 ? "east" : x > DON_X2 ? "west" : z < DON_Z1 ? "south" : "north";
                    s.set(x, y - 1, z, Kit.upside(TRIM_STAIRS, in));
                    s.set(x, y, z, state);
                }
            }
        }
    }

    /**
     * One cell of a side of the lookout: piers at the corners and in the middle of every side, a rail of stone between
     * them, and over every opening an arch of two stairs.
     */
    private static void lookoutSide(Structure s, int x, int z, int i) {
        int y = FLOORS[3] + 1;
        if (i == 0 || i == 5 || i == 10) {
            s.fill(x, y, z, x, y + 2, z, i == 5 ? WALL : ROUGH);
            return;
        }
        boolean alongX = (z == DON_Z1 || z == DON_Z2) && x != DON_X1 && x != DON_X2;
        s.set(x, y, z, WALL_FENCE).set(x, y + 1, z, AIR);
        boolean nearLow = i == 1 || i == 6;
        boolean nearHigh = i == 4 || i == 9;
        String lowSide = alongX ? "west" : "north";
        String highSide = alongX ? "east" : "south";
        s.set(x, y + 2, z, nearLow ? Kit.upside(TRIM_STAIRS, lowSide) : nearHigh ? Kit.upside(TRIM_STAIRS, highSide) : AIR);
    }

    /**
     * Inside the donjon: the spiral stair round its newel through every floor, the treasury with its vault behind iron bars,
     * an armoury, the council room, the royal chamber, the library and the lookout with its bell, all lit.
     */
    private static void donjonRooms(Structure s) {
        spiralStair(s);
        // The ground floor: the vault behind its grille, the antechamber with the counting desk, the armoury.
        s.fill(16, 1, 14, 20, FLOORS[0] - 1, 14, Kit.BARS);
        s.door(18, 1, 14, DOOR, "south", "left");
        s.fill(21, 1, 9, 21, FLOORS[0] - 1, 14, WALL);
        for (int x = 16; x <= 20; x++) {
            s.set(x, 1, 9, x % 2 == 0 ? Kit.chest("south") : Kit.barrel());
        }
        s.set(16, 2, 9, Kit.barrel()).set(20, 2, 9, Kit.barrel());
        s.set(16, 1, 10, Kit.barrel()).set(16, 1, 11, Kit.chest("east")).set(16, 1, 12, Kit.barrel()).set(16, 2, 10, Kit.barrel());
        s.set(20, 1, 10, GOLD).set(20, 1, 11, GOLD).set(20, 2, 10, "minecraft:raw_gold_block").set(20, 1, 12, "minecraft:emerald_block");
        s.set(20, 2, 11, Kit.lantern()).set(18, FLOORS[0] - 1, 11, Kit.hangingLantern());
        s.set(21, 1, 17, "minecraft:lectern[facing=north,has_book=false,powered=false]");
        s.set(16, 1, 16, Kit.barrel()).set(19, FLOORS[0] - 1, 16, Kit.hangingLantern());
        s.set(16, 4, 15, Kit.wallBanner("red", "east"));
        for (int z = 9; z <= 13; z++) {
            s.set(24, 1, z, Kit.barrel());
        }
        s.set(24, 2, 10, Kit.barrel()).set(22, 1, 9, Kit.chest("south")).set(23, 1, 9, "minecraft:grindstone[face=floor,facing=south]");
        s.set(23, FLOORS[0] - 1, 11, Kit.hangingLantern());
        // The council room: a long table with chairs, shelves of books, a map table and the banner of the kingdom.
        int y = FLOORS[0] + 1;
        s.fill(17, y, 12, 20, y, 12, Kit.topSlab(SPRUCE_SLAB));
        for (int x = 17; x <= 20; x++) {
            s.set(x, y, 11, Kit.stairs(SPRUCE_STAIRS, "north")).set(x, y, 13, Kit.stairs(SPRUCE_STAIRS, "south"));
        }
        s.set(16, y, 12, Kit.stairs(SPRUCE_STAIRS, "west")).set(21, y, 12, Kit.stairs(SPRUCE_STAIRS, "east"));
        s.set(18, y + 1, 12, Kit.lantern()).set(20, y + 1, 12, "minecraft:flower_pot");
        s.fill(16, y, 9, 16, y + 2, 10, BOOKSHELF).fill(16, y, 15, 16, y + 2, 17, BOOKSHELF);
        s.set(24, y, 9, "minecraft:cartography_table").set(24, y, 10, Kit.barrel());
        s.set(16, y + 2, 13, Kit.wallBanner("red", "east"));
        s.set(18, FLOORS[1] - 1, 10, Kit.hangingLantern()).set(19, FLOORS[1] - 1, 15, Kit.hangingLantern());
        s.set(23, FLOORS[1] - 1, 11, Kit.hangingLantern());
        // The royal chamber: a carpet of the kingdom, a hearth with two chairs, a wardrobe, a writing desk.
        y = FLOORS[1] + 1;
        s.fill(17, y, 10, 21, y, 16, Kit.CLOTH_CARPET);
        s.set(16, y, 12, ROUGH).set(16, y, 14, ROUGH).set(16, y, 13, "minecraft:campfire[facing=east,lit=true,signal_fire=false,waterlogged=false]");
        s.fill(16, y + 1, 12, 16, y + 1, 14, Kit.upside(ROUGH_STAIRS, "west")).fill(16, y + 2, 12, 16, FLOORS[2] - 1, 14, ROUGH);
        s.set(18, y, 12, Kit.stairs(SPRUCE_STAIRS, "east")).set(18, y, 14, Kit.stairs(SPRUCE_STAIRS, "east"));
        s.set(17, y, 9, Kit.chest("south")).set(18, y, 9, Kit.barrel()).set(19, y, 9, Kit.chest("south")).set(21, y, 9, Kit.barrel());
        s.set(21, y, 16, Kit.topSlab(SPRUCE_SLAB)).set(20, y, 16, Kit.stairs(SPRUCE_STAIRS, "west"));
        s.set(21, y + 1, 16, Kit.lantern());
        s.set(24, y + 2, 10, Kit.wallBanner("red", "west")).set(16, y + 2, 10, Kit.wallBanner("red", "east"));
        s.set(19, FLOORS[2] - 1, 12, Kit.hangingLantern()).set(23, FLOORS[2] - 1, 11, Kit.hangingLantern());
        // The library: shelves along the walls, a lectern, a reading table.
        y = FLOORS[2] + 1;
        for (int x = 16; x <= 24; x++) {
            if (x != 17 && x != 23) {
                s.fill(x, y, DON_Z1 + 1, x, y + 1, DON_Z1 + 1, BOOKSHELF);
            }
        }
        for (int z = 10; z <= 17; z++) {
            if (z != 13) {
                s.fill(16, y, z, 16, y + 1, z, BOOKSHELF);
            }
        }
        s.set(19, y, 13, "minecraft:lectern[facing=south,has_book=false,powered=false]");
        s.set(19, y, 16, Kit.topSlab(SPRUCE_SLAB)).set(18, y, 16, Kit.stairs(SPRUCE_STAIRS, "west"));
        s.set(19, y + 1, 16, Kit.lantern());
        s.set(20, FLOORS[3] - 1, 11, Kit.hangingLantern()).set(19, FLOORS[3] - 1, 15, Kit.hangingLantern());
        // The lookout: the great bell and lanterns hanging from its roof, a lantern on the newel.
        y = FLOORS[3] + 1;
        s.set(CX, SPIRE - 1, 13, "minecraft:bell[attachment=ceiling,facing=north,powered=false]");
        s.set(17, SPIRE - 1, 13, Kit.hangingLantern()).set(23, SPIRE - 1, 13, Kit.hangingLantern());
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
        int x1 = 8;
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
        for (int[] post : new int[][]{{x1, z1}, {x1, z2}, {x1, 11}, {x1, 15}, {11, z1}, {11, z2}, {x2, z1}, {x2, z2}}) {
            s.fill(post[0], 3, post[1], post[0], eaves, post[1], Kit.log(OAK_LOG, "y"));
        }
        roofX(s, x1, x2, z1 - 1, z2 + 1, eaves, Kit.SHINGLE, PLASTER, x1, -1);
        s.fill(x1, eaves + 1, 13, x1, eaves + 4, 13, Kit.log(OAK_LOG, "y"));
        // Windows, the door and a lantern over it.
        s.fill(x1, 4, 10, x1, 5, 10, Kit.GLASS).fill(x1, 4, 16, x1, 5, 16, Kit.GLASS);
        s.fill(13, 4, z2, 13, 5, z2, Kit.GLASS).fill(9, 4, z2, 9, 5, z2, Kit.GLASS);
        s.fill(13, 4, z1, 13, 5, z1, Kit.GLASS).fill(9, 4, z1, 9, 5, z1, Kit.GLASS);
        s.door(11, 1, z2, "minecraft:oak_door", "south", "left");
        s.set(10, 3, z2 + 1, Kit.wallTorch("south"));
        // The chimney stack outside the west gable.
        s.fill(x1 - 1, 1, 13, x1 - 1, 13, 13, Kit.CHIMNEY).set(x1 - 1, 14, 13, Kit.CHIMNEY_POT);
        // Inside: the hearth under its hood, ovens and stores along the north wall, a table, a beam with lanterns.
        s.set(x1 + 1, 1, 12, ROUGH).set(x1 + 1, 1, 14, ROUGH).set(x1 + 1, 1, 13, "minecraft:campfire[facing=east,lit=true,signal_fire=false,waterlogged=false]");
        s.fill(x1 + 1, 2, 12, x1 + 1, 2, 14, Kit.upside(ROUGH_STAIRS, "west")).fill(x1 + 1, 3, 12, x1 + 1, 6, 14, ROUGH);
        s.set(10, 1, z1 + 1, "minecraft:smoker[facing=south,lit=false]").set(11, 1, z1 + 1, "minecraft:furnace[facing=south,lit=false]");
        s.set(12, 1, z1 + 1, Kit.barrel()).set(13, 1, z1 + 1, Kit.barrel()).set(14, 1, z1 + 1, "minecraft:crafting_table");
        s.set(13, 2, z1 + 1, Kit.barrel()).set(14, 1, 12, "minecraft:cauldron").set(14, 1, 15, HAY).set(14, 2, 15, HAY);
        s.fill(11, 1, 13, 12, 1, 13, Kit.topSlab(SPRUCE_SLAB));
        s.set(11, 1, 14, Kit.stairs(SPRUCE_STAIRS, "south")).set(12, 1, 14, Kit.stairs(SPRUCE_STAIRS, "south"));
        s.fill(x1 + 2, eaves - 1, 13, x2, eaves - 1, 13, Kit.log(LOG, "x"));
        s.set(11, eaves - 2, 13, Kit.hangingLantern()).set(13, eaves - 2, 13, Kit.hangingLantern());
    }

    /**
     * The chapel leaning on the east side of the donjon: stone walls with buttresses, tall lancets of stained glass, a roof
     * of blue-green shingles with its gable to the courtyard, a door under an arch and a small rose window in the gable, a
     * belfry with a bell and a little spire on its ridge; inside an altar with candles, pews on both sides of a carpet and
     * chandeliers.
     */
    private static void chapel(Structure s) {
        int x1 = 26;
        int x2 = 32;
        int z1 = 9;
        int z2 = 17;
        int eaves = 9;
        int mid = 29;
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
        for (int x : new int[]{28, 30}) {
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
        // The belfry on the ridge: four piers, the bell, a little spire.
        int bz = z2 - 1;
        for (int x : new int[]{mid - 1, mid + 1}) {
            for (int z : new int[]{bz - 1, bz + 1}) {
                s.fill(x, eaves + 4, z, x, eaves + 6, z, WALL);
            }
        }
        s.fill(mid - 1, eaves + 7, bz - 1, mid + 1, eaves + 7, bz + 1, TRIM);
        s.set(mid, eaves + 6, bz, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        int tip = Kit.spire(s, mid - 1, bz - 1, mid + 1, bz + 1, eaves + 8, Kit.SLATE);
        s.set(mid, tip, bz, ROD);
        // Inside: the altar with candles and a banner over it, pews, a carpet, chandeliers.
        s.fill(mid - 1, 1, z1 + 1, mid + 1, 1, z1 + 1, TRIM).set(mid, 1, z1 + 1, CARVED);
        s.set(mid - 1, 2, z1 + 1, "minecraft:candle[candles=3,lit=true,waterlogged=false]").set(mid + 1, 2, z1 + 1, "minecraft:candle[candles=3,lit=true,waterlogged=false]");
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
     * wall walk on both sides, a well in the yard of the kitchen with a woodpile and hay, lamp posts along the ways.
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
        for (int[] p : new int[][]{{15, 29}, {25, 29}, {15, 33}, {25, 33}, {8, 19}, {32, 19}, {20, 5}}) {
            lampPost(s, p[0], p[1]);
        }
        // The yard of the kitchen: a well with a roof, a woodpile, hay and barrels.
        int wx = 7;
        int wz = 25;
        s.walls(wx - 1, 1, wz - 1, wx + 1, 1, wz + 1, ROUGH);
        s.set(wx, 0, wz, ROUGH).set(wx, 1, wz, WATER);
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            s.fill(wx + c[0], 2, wz + c[1], wx + c[0], 3, wz + c[1], Kit.fence("oak"));
        }
        s.fill(wx - 1, 4, wz - 1, wx + 1, 4, wz + 1, Kit.slab(Kit.SHINGLE.slab()));
        s.set(wx, 3, wz, Kit.hangingLantern());
        s.fill(5, 1, 10, 5, 2, 13, Kit.log(LOG, "z")).fill(6, 1, 10, 6, 1, 13, Kit.log(LOG, "z"));
        s.set(9, 1, 18, Kit.barrel()).set(10, 1, 18, Kit.barrel()).set(9, 2, 18, Kit.barrel()).set(5, 1, 15, HAY).set(6, 1, 15, HAY).set(5, 2, 15, HAY);
        // Stores behind the donjon.
        s.set(17, 1, 4, Kit.barrel()).set(18, 1, 4, Kit.barrel()).set(23, 1, 4, HAY).set(24, 1, 4, HAY).set(23, 2, 4, HAY);
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
