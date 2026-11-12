package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * Three buildings of the towns of the classic style, the stone and half-timbered style of a kingdom of central Europe: the
 * smithy with its forge, its great chimney and its open front under a lean-to, the inn of three storeys whose upper floors
 * jut over the street, and a piece of the town wall with its gate between two round towers under slate cones.
 *
 * <p>They are drawn with the blocks that stand for the parts of a look (the stone of the walls, rough stone for footings
 * and quoins, trim for bands and sills, carved stone for keystones, roofs, spires, plaster, cloth and timber), so that the
 * same drawing takes the stone, the roof and the colours of every kingdom that builds it. What counts in the game is kept
 * to what each building is for: one work post with one chest in the smithy and in the inn, beds only in the rooms of the
 * inn, three barrels behind its counter and no container at the gate.
 */
final class ClassicTown {
    private static final String AIR = Structure.AIR;
    private static final String SPRUCE_LOG = "minecraft:spruce_log";
    private static final String OAK_LOG = "minecraft:oak_log";
    private static final String SPRUCE_PLANKS = "minecraft:spruce_planks";
    private static final String OAK_PLANKS = "minecraft:oak_planks";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String OAK_STAIRS = "minecraft:oak_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String DOOR = "minecraft:spruce_door";
    private static final String HATCH = "minecraft:oak_trapdoor";
    private static final String SHELF = "minecraft:spruce_trapdoor";
    private static final String WORK_POST = "kingdomworld:work_post";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String COAL = "minecraft:coal_block";
    private static final String CAMPFIRE = "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]";
    private static final String CAULDRON = "minecraft:water_cauldron[level=3]";
    private static final String POT = "minecraft:flower_pot";

    private ClassicTown() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        Structure smithy = smithy();
        list.add(new BuildingGenerator.Building("classic_smithy", "production", "workshop", "minecraft:anvil",
                (smithy.sizeX - 1) / 2, 0, smithy.sizeZ - 1, smithy, "workshop", 1, "")
                .at("door", 5, 1, 10));
        Structure inn = tavern();
        list.add(new BuildingGenerator.Building("classic_tavern", "trade", "tavern", "minecraft:barrel",
                (inn.sizeX - 1) / 2, 0, inn.sizeZ - 1, inn, "tavern", 1, "")
                .at("door", 6, 0, 12));
        Structure gate = wallGate();
        list.add(new BuildingGenerator.Building("classic_wall_gate", "defense", "gatehouse", "minecraft:iron_bars",
                (gate.sizeX - 1) / 2, 1, gate.sizeZ - 1, gate, "tower", 1, "")
                .at("door", 11, 1, 10)
                .at("castle_gate", PASS_X1, 1, 5).at("castle_gate", PASS_X2, 1, 5)
                .at("castle_wall", 1, GATE_WALK, 6).at("castle_wall", 21, GATE_WALK, 6));
        return list;
    }

    // ---------------------------------------------------------------- shared shapes

    private static String post() {
        return Kit.log(SPRUCE_LOG, "y");
    }

    /**
     * A lantern hanging on a chain from a beam or a roof: the chain from {@code top} down, the lantern under it.
     */
    private static void chandelier(Structure s, int x, int z, int lanternY, int top) {
        if (top > lanternY) {
            s.fill(x, lanternY + 1, z, x, top, z, Kit.chain());
        }
        s.set(x, lanternY, z, Kit.hangingLantern());
    }

    /**
     * Quoins on a corner of a wall: the corner column of rough stone, and on every second layer one more block of it on
     * both faces, so that the corner looks bonded of long and short stones.
     *
     * @param dx the way along x from the corner into its wall (+1 or -1)
     * @param dz the way along z from the corner into its wall (+1 or -1)
     */
    private static void quoins(Structure s, int x, int z, int dx, int dz, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            s.set(x, y, z, Kit.ROUGH);
            if ((y - y1) % 2 == 0) {
                s.set(x + dx, y, z, Kit.ROUGH).set(x, y, z + dz, Kit.ROUGH);
            }
        }
    }

    /**
     * Upright posts of spruce in a wall that runs along x at {@code z}, from layer {@code y1} to {@code y2}.
     */
    private static void postsX(Structure s, int z, int y1, int y2, int... xs) {
        for (int x : xs) {
            s.fill(x, y1, z, x, y2, z, post());
        }
    }

    /**
     * Upright posts of spruce in a wall that runs along z at {@code x}.
     */
    private static void postsZ(Structure s, int x, int y1, int y2, int... zs) {
        for (int z : zs) {
            s.fill(x, y1, z, x, y2, z, post());
        }
    }

    /**
     * A window in a wall along x at {@code z} from {@code x1} to {@code x2} and from {@code y1} to {@code y2}, with a shutter
     * standing open against the wall on each side of it, outside ({@code out} is "south" or "north").
     */
    private static void shutteredX(Structure s, int x1, int x2, int y1, int y2, int z, String out) {
        s.fill(x1, y1, z, x2, y2, z, Kit.GLASS);
        int dz = out.equals("south") ? 1 : -1;
        for (int y = y1; y <= y2; y++) {
            s.set(x1 - 1, y, z + dz, Kit.shutter(Kit.SHUTTER, out)).set(x2 + 1, y, z + dz, Kit.shutter(Kit.SHUTTER, out));
        }
    }

    /**
     * A window in a wall along z at {@code x} with its shutters ({@code out} is "east" or "west").
     */
    private static void shutteredZ(Structure s, int z1, int z2, int y1, int y2, int x, String out) {
        s.fill(x, y1, z1, x, y2, z2, Kit.GLASS);
        int dx = out.equals("east") ? 1 : -1;
        for (int y = y1; y <= y2; y++) {
            s.set(x + dx, y, z1 - 1, Kit.shutter(Kit.SHUTTER, out)).set(x + dx, y, z2 + 1, Kit.shutter(Kit.SHUTTER, out));
        }
    }

    /**
     * A window of a stone storey: panes from {@code y1} to {@code y2}, a lintel of trim over them in the wall and a sill of
     * trim standing out of the wall under them; {@code (ox, oz)} is the way out of the wall.
     */
    private static void stoneWindow(Structure s, int x, int z, int y1, int y2, int ox, int oz) {
        s.fill(x, y1, z, x, y2, z, Kit.GLASS);
        s.set(x, y2 + 1, z, Kit.TRIM);
        if (s.inside(x + ox, y1 - 1, z + oz)) {
            s.set(x + ox, y1 - 1, z + oz, Kit.topSlab(Kit.TRIM_SLAB));
        }
    }

    /**
     * A gable roof whose ridge runs along z, its gables to the front and the back: rows of stairs rising from the west eave
     * {@code x1} and the east eave {@code x2}. The first {@code steep} rows above the eaves climb two layers each, with a
     * full block of the roof under the stair; the rows above climb one. An odd width is closed with a ridge of slabs.
     *
     * @return for every column from x1 to x2 the lowest layer the roof takes in it
     */
    private static int[] roofZ(Structure s, int x1, int x2, int z1, int z2, int y, int steep, Kit.Roof roof) {
        int[] under = new int[x2 - x1 + 1];
        int height = y;
        for (int row = 0; x1 + row <= x2 - row; row++) {
            boolean steepRow = row > 0 && row <= steep;
            if (row > 0) {
                height += steepRow ? 2 : 1;
            }
            int west = x1 + row;
            int east = x2 - row;
            if (steepRow) {
                s.fill(west, height - 1, z1, west, height - 1, z2, roof.block());
                s.fill(east, height - 1, z1, east, height - 1, z2, roof.block());
            }
            if (west < east) {
                s.fill(west, height, z1, west, height, z2, Kit.stairs(roof.stairs(), "east"));
                s.fill(east, height, z1, east, height, z2, Kit.stairs(roof.stairs(), "west"));
            } else {
                s.fill(west, height, z1, west, height, z2, Kit.slab(roof.slab()));
            }
            under[west - x1] = steepRow ? height - 1 : height;
            under[east - x1] = under[west - x1];
        }
        return under;
    }

    /**
     * A gable roof whose ridge runs along x, its eaves to the front and the back, steep rows as in {@link #roofZ}.
     *
     * @return for every row from z1 to z2 the lowest layer the roof takes in it
     */
    private static int[] roofX(Structure s, int x1, int x2, int z1, int z2, int y, int steep, Kit.Roof roof) {
        int[] under = new int[z2 - z1 + 1];
        int height = y;
        for (int row = 0; z1 + row <= z2 - row; row++) {
            boolean steepRow = row > 0 && row <= steep;
            if (row > 0) {
                height += steepRow ? 2 : 1;
            }
            int back = z1 + row;
            int front = z2 - row;
            if (steepRow) {
                s.fill(x1, height - 1, back, x2, height - 1, back, roof.block());
                s.fill(x1, height - 1, front, x2, height - 1, front, roof.block());
            }
            if (back < front) {
                s.fill(x1, height, back, x2, height, back, Kit.stairs(roof.stairs(), "south"));
                s.fill(x1, height, front, x2, height, front, Kit.stairs(roof.stairs(), "north"));
            } else {
                s.fill(x1, height, back, x2, height, back, Kit.slab(roof.slab()));
            }
            under[back - z1] = steepRow ? height - 1 : height;
            under[front - z1] = under[back - z1];
        }
        return under;
    }

    /**
     * Closes the gable of a roof along z at the wall {@code z}: every column between the eaves from {@code y} up to the
     * underside of the roof, where nothing stands yet.
     */
    private static void gableAtZ(Structure s, int x1, int[] under, int z, int y, String state) {
        for (int i = 1; i < under.length - 1; i++) {
            for (int h = y; h < under[i]; h++) {
                if (!s.solid(x1 + i, h, z)) {
                    s.set(x1 + i, h, z, state);
                }
            }
        }
    }

    /**
     * Closes the gable of a roof along x at the wall {@code x}.
     */
    private static void gableAtX(Structure s, int z1, int[] under, int x, int y, String state) {
        for (int i = 1; i < under.length - 1; i++) {
            for (int h = y; h < under[i]; h++) {
                if (!s.solid(x, h, z1 + i)) {
                    s.set(x, h, z1 + i, state);
                }
            }
        }
    }

    // ---------------------------------------------------------------- round shapes

    /**
     * The facing that turns a stair on the ring around a round plan towards its middle.
     */
    private static String inward(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "west" : "east";
        }
        return dz > 0 ? "north" : "south";
    }

    /**
     * A stair on the edge of a round plan, turned towards its middle; where the edge turns a corner the stair is an outer
     * corner, so that the ring reads as round.
     */
    private static String roundStair(int dx, int dz, int diameter, String stairs, boolean upsideDown) {
        int sx = Integer.signum(dx);
        int sz = Integer.signum(dz);
        boolean outX = sx != 0 && !Kit.inDisc(dx + sx, dz, diameter);
        boolean outZ = sz != 0 && !Kit.inDisc(dx, dz + sz, diameter);
        String facing;
        String shape = "straight";
        if (outX && outZ) {
            facing = sz < 0 ? "south" : "north";
            shape = (sz < 0) == (sx < 0) ? "outer_left" : "outer_right";
        } else if (outX) {
            facing = sx > 0 ? "west" : "east";
        } else if (outZ) {
            facing = sz > 0 ? "north" : "south";
        } else {
            facing = inward(dx, dz);
        }
        return stairs + "[facing=" + facing + ",half=" + (upsideDown ? "top" : "bottom") + ",shape=" + shape + ",waterlogged=false]";
    }

    /**
     * A ring of stairs just outside a round plan, where nothing stands yet and outside the columns from {@code skipX1} to
     * {@code skipX2}: brackets hanging upside down under an eave, or the sloping foot of a tower.
     */
    private static void stairRing(Structure s, int cx, int y, int cz, int diameter, String stairs, boolean upsideDown,
                                  int skipX1, int skipX2) {
        int radius = (diameter + 1) / 2;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = cx + dx;
                if (x >= skipX1 && x <= skipX2) {
                    continue;
                }
                if (Kit.inDisc(dx, dz, diameter + 2) && !Kit.inDisc(dx, dz, diameter) && s.inside(x, y, cz + dz) && !s.solid(x, y, cz + dz)) {
                    String facing = inward(dx, dz);
                    s.set(x, y, cz + dz, upsideDown ? Kit.upside(stairs, facing) : Kit.stairs(stairs, facing));
                }
            }
        }
    }

    /**
     * A cone over a round tower: an eave of stairs one block out over the wall, then rings that shrink by one block on every
     * side, each as many layers tall as the next number of {@code rises}, the upper edge of every ring of stairs so that the
     * cone slopes instead of stepping. A shell two blocks thick, hollow inside.
     *
     * @param y the layer of the eave
     * @return the layer over the point
     */
    private static int coneRoof(Structure s, int cx, int y, int cz, int diameter, int[] rises, Kit.Roof roof) {
        int radius = (diameter + 1) / 2;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (!s.inside(cx + dx, y, cz + dz)) {
                    continue;
                }
                if (Kit.inDisc(dx, dz, diameter + 2) && !Kit.inDisc(dx, dz, diameter)) {
                    s.set(cx + dx, y, cz + dz, roundStair(dx, dz, diameter + 2, roof.stairs(), false));
                } else if (Kit.inDisc(dx, dz, diameter) && !Kit.inDisc(dx, dz, diameter - 4)) {
                    s.set(cx + dx, y, cz + dz, roof.block());
                }
            }
        }
        int layer = y + 1;
        int d = diameter;
        for (int ring = 0; d >= 1; ring++, d -= 2) {
            int rise = ring < rises.length ? rises[ring] : 2;
            for (int step = 0; step < rise; step++) {
                boolean top = step == rise - 1 && d > 1;
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (!Kit.inDisc(dx, dz, d) || d > 4 && Kit.inDisc(dx, dz, d - 4)) {
                            continue;
                        }
                        boolean edge = !Kit.inDisc(dx, dz, d - 2);
                        s.set(cx + dx, layer, cz + dz, top && edge ? roundStair(dx, dz, d, roof.stairs(), false) : roof.block());
                    }
                }
                layer++;
            }
        }
        return layer;
    }

    // ---------------------------------------------------------------- the smithy

    /** The floor of the smith's room over the forge, a band of trim round the walls at its height. */
    private static final int SMITHY_LOFT = 6;
    /** The plate on the walls under the eaves. */
    private static final int SMITHY_EAVES = 10;

    /**
     * The smithy of a town: a forge hall of stone on a plinth of rough stone with quoins, and over it the smith's room in a
     * timber frame filled with plaster under a steep roof of dark shingles, its gable turned to the street. The front of the
     * hall stands open under a wide arch with a carved keystone, and before it a lean-to of shingles on four posts shelters
     * the street side of the work: the grindstone, a woodpile and a chopping block. The great chimney of rough stone rises
     * against the east wall, through the eaves and over the ridge. A stair of timber climbs the west wall to the smith's door;
     * a red banner hangs in the gable as the sign of the house.
     *
     * <p>Inside, the forge stands against the chimney breast: a hearth of rough stone with the fire on it under a hood, the
     * blast furnace beside it and the quenching tub at its end; the anvil before it, the smithing table with tools hung over
     * it on the west wall, coal, ore and bars stacked along the back wall, and the smith's work post there with its chest.
     * Upstairs the smith's room has a hearth in the chimney breast, a table with two chairs, a bench under the window, a
     * shelf of books and chests of goods of stripped wood; no bed, the smith sleeps at home.
     */
    static Structure smithy() {
        Structure s = new Structure(11, 21, 13);
        smithyWalls(s);
        smithyRoof(s);
        smithyChimney(s);
        smithyLeanTo(s);
        smithyStair(s);
        smithyForge(s);
        smithyLoft(s);
        s.connect();
        return s;
    }

    /**
     * The walls: the hall of stone with its plinth, quoins and the open arch, the band of trim at the floor of the loft, the
     * timber frame of the upper storey with its windows and shutters, the plate under the eaves.
     */
    private static void smithyWalls(Structure s) {
        s.fill(1, 0, 1, 9, 0, 8, Kit.ROUGH);
        s.walls(1, 1, 1, 9, 5, 8, Kit.WALL);
        s.walls(1, 1, 1, 9, 1, 8, Kit.ROUGH);
        quoins(s, 1, 1, 1, 1, 1, 5);
        quoins(s, 9, 1, -1, 1, 1, 5);
        quoins(s, 1, 8, 1, -1, 1, 5);
        quoins(s, 9, 8, -1, -1, 1, 5);
        // The open front: an arch five wide, haunches of stone, voussoirs of trim and a carved keystone.
        s.fill(3, 1, 8, 7, 3, 8, AIR);
        s.set(3, 3, 8, Kit.upside(Kit.WALL_STAIRS, "west")).set(7, 3, 8, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.fill(3, 4, 8, 7, 4, 8, Kit.TRIM).set(5, 4, 8, Kit.CARVED);
        // Windows of the hall under lintels of trim.
        stoneWindow(s, 6, 1, 2, 3, 0, -1);
        stoneWindow(s, 1, 5, 2, 3, -1, 0);
        stoneWindow(s, 9, 2, 2, 3, 1, 0);
        stoneWindow(s, 9, 7, 2, 3, 1, 0);
        // The band at the floor of the loft and the floor itself.
        s.walls(1, SMITHY_LOFT, 1, 9, SMITHY_LOFT, 8, Kit.TRIM);
        s.fill(2, SMITHY_LOFT, 2, 8, SMITHY_LOFT, 7, SPRUCE_PLANKS);
        // The upper storey: plaster between posts of spruce, the plate on top.
        s.walls(1, 7, 1, 9, 9, 8, Kit.PLASTER);
        postsX(s, 1, 7, 9, 1, 4, 6, 9);
        postsX(s, 8, 7, 9, 1, 4, 6, 9);
        postsZ(s, 1, 7, 9, 2, 4, 7);
        postsZ(s, 9, 7, 9, 2, 7);
        s.beam(1, 1, 9, 8, SMITHY_EAVES, SPRUCE_LOG);
        // Braces in the middle panels of the front and the back.
        for (int z : new int[]{1, 8}) {
            s.set(5, 7, z, Kit.log(SPRUCE_LOG, "y"));
        }
        shutteredX(s, 2, 3, 8, 9, 8, "south");
        shutteredX(s, 7, 8, 8, 9, 8, "south");
        shutteredX(s, 2, 3, 8, 9, 1, "north");
        shutteredX(s, 7, 8, 8, 9, 1, "north");
        s.fill(1, 8, 5, 1, 9, 6, Kit.GLASS);
    }

    /**
     * The steep roof of dark shingles with its gable to the street, the gables of plaster in a frame of spruce with a king
     * post, a collar and two windows each, and the red banner hung in the front gable as the sign of the smithy.
     */
    private static void smithyRoof(Structure s) {
        int[] under = roofZ(s, 0, 10, 0, 9, SMITHY_EAVES, 2, Kit.SHINGLE);
        for (int z : new int[]{1, 8}) {
            gableAtZ(s, 0, under, z, SMITHY_EAVES + 1, Kit.PLASTER);
            s.fill(3, SMITHY_EAVES + 1, z, 3, under[3] - 1, z, post());
            s.fill(7, SMITHY_EAVES + 1, z, 7, under[7] - 1, z, post());
            s.fill(4, 14, z, 6, 14, z, Kit.log(SPRUCE_LOG, "x"));
            s.fill(5, SMITHY_EAVES + 1, z, 5, under[5] - 1, z, post());
            s.fill(4, 12, z, 4, 13, z, Kit.GLASS).fill(6, 12, z, 6, 13, z, Kit.GLASS);
        }
        s.set(5, 12, 9, Kit.wallBanner("red", "south"));
        s.set(5, under[5] + 1, 9, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        // Tie beams across the loft.
        s.fill(2, SMITHY_EAVES, 3, 8, SMITHY_EAVES, 3, Kit.log(SPRUCE_LOG, "x"));
        s.fill(2, SMITHY_EAVES, 6, 8, SMITHY_EAVES, 6, Kit.log(SPRUCE_LOG, "x"));
    }

    /**
     * The great chimney against the east wall: a breast of rough stone with weathered shoulders, and over it a stack of
     * stone two blocks square through the eaves to over the ridge, banded with trim and topped with two pots.
     */
    private static void smithyChimney(Structure s) {
        s.fill(10, 0, 3, 10, 7, 6, Kit.ROUGH);
        s.set(10, 8, 3, Kit.stairs(Kit.ROUGH_STAIRS, "south")).set(10, 8, 6, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.fill(10, 8, 4, 10, 17, 5, Kit.WALL);
        s.fill(9, SMITHY_EAVES, 4, 9, 17, 5, Kit.WALL);
        s.fill(10, 8, 4, 10, 8, 5, Kit.TRIM);
        s.fill(9, 18, 4, 10, 18, 5, Kit.TRIM);
        s.set(9, 19, 4, Kit.ROUGH_FENCE).set(10, 19, 5, Kit.ROUGH_FENCE);
    }

    /**
     * The lean-to before the open front: a floor of rough stone, four posts with knee braces carrying a beam, a roof of
     * shingles falling to the street, steps of trim in the middle and a lantern under its roof; under it the grindstone, a
     * woodpile and a chopping block, and beside it a cart wheel leaning on the chimney breast.
     */
    private static void smithyLeanTo(Structure s) {
        s.fill(1, 0, 9, 9, 0, 11, Kit.ROUGH);
        postsX(s, 11, 1, 2, 1, 3, 7, 9);
        s.fill(1, 3, 11, 9, 3, 11, Kit.log(SPRUCE_LOG, "x"));
        s.fill(1, 3, 9, 1, 3, 10, Kit.log(SPRUCE_LOG, "z")).fill(9, 3, 9, 9, 3, 10, Kit.log(SPRUCE_LOG, "z"));
        s.set(2, 2, 11, Kit.upside(SPRUCE_STAIRS, "west")).set(8, 2, 11, Kit.upside(SPRUCE_STAIRS, "east"));
        int[] heights = {6, 5, 4, 3};
        for (int i = 0; i < heights.length; i++) {
            s.fill(0, heights[i], 9 + i, 10, heights[i], 9 + i, Kit.stairs(Kit.SHINGLE.stairs(), "north"));
        }
        s.fill(4, 0, 12, 6, 0, 12, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        s.set(5, 4, 10, Kit.hangingLantern());
        // The grindstone at the east end, the woodpile and the chopping block at the west end.
        s.set(8, 1, 9, "minecraft:grindstone[face=floor,facing=south]");
        s.fill(1, 1, 9, 2, 2, 10, Kit.log(OAK_LOG, "z"));
        s.set(1, 3, 9, Kit.log(OAK_LOG, "z"));
        s.set(3, 1, 10, Kit.log("minecraft:stripped_oak_log", "y"));
        // A cart wheel brought for its iron tyre, leaning on the chimney breast.
        s.set(10, 1, 7, "minecraft:dark_oak_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]");
    }

    /**
     * The stair of timber along the west wall to the smith's door: steps of oak on a footing and on posts, a landing on a
     * post before the door with a lantern on its rail, a torch over the steps.
     */
    private static void smithyStair(Structure s) {
        for (int k = 0; k <= 6; k++) {
            s.set(0, k, 10 - k, Kit.stairs(OAK_STAIRS, "north"));
        }
        s.set(0, 0, 9, SPRUCE_PLANKS).fill(0, 0, 8, 0, 1, 8, SPRUCE_PLANKS);
        s.fill(0, 0, 6, 0, 3, 6, Kit.fence("spruce"));
        s.fill(0, 6, 2, 0, 6, 3, SPRUCE_PLANKS);
        s.fill(0, 0, 2, 0, 5, 2, post());
        s.set(0, 7, 2, Kit.fence("spruce")).set(0, 8, 2, Kit.lantern());
        s.door(1, 7, 3, DOOR, "east", "left");
        s.set(0, 8, 5, Kit.wallTorch("west"));
    }

    /**
     * The forge hall: the hearth of rough stone against the chimney breast with the fire on it under a hood, the blast
     * furnace and the quenching tub at its ends, the anvil before it; the work post of the smith with its chest, coal, ore
     * and iron bars along the back wall; the smithing table on the west wall with tongs, files and a whetstone hung over it;
     * lanterns hanging from the beams.
     */
    private static void smithyForge(Structure s) {
        // The chimney breast fills the east wall, the hearth stands before it.
        s.fill(9, 1, 3, 9, 9, 6, Kit.ROUGH);
        s.fill(8, 1, 3, 8, 1, 6, Kit.ROUGH);
        s.set(8, 2, 4, CAMPFIRE).set(8, 2, 5, CAMPFIRE);
        s.set(8, 2, 3, Kit.slab(Kit.ROUGH_SLAB)).set(8, 2, 6, Kit.slab(Kit.ROUGH_SLAB));
        s.fill(8, 4, 3, 8, 4, 6, Kit.upside(Kit.ROUGH_STAIRS, "east"));
        s.fill(8, 5, 3, 8, 5, 6, Kit.ROUGH);
        s.set(8, 1, 2, "minecraft:blast_furnace[facing=west,lit=true]");
        s.set(8, 1, 7, CAULDRON);
        s.set(6, 1, 5, "minecraft:anvil[facing=north]");
        // The smith's post with its chest, the stock along the back wall.
        s.set(3, 1, 2, WORK_POST).set(2, 1, 2, Kit.chest("south"));
        s.set(4, 1, 2, COAL).set(5, 1, 2, COAL).set(4, 2, 2, COAL);
        s.set(6, 1, 2, "minecraft:raw_iron_block").set(7, 1, 2, Kit.BARS).set(7, 2, 2, Kit.BARS);
        // The smithing table and the tools over it.
        s.set(2, 1, 4, "minecraft:smithing_table");
        s.set(2, 1, 3, Kit.topSlab(SPRUCE_SLAB));
        s.set(2, 3, 3, "minecraft:grindstone[face=wall,facing=east]");
        s.set(2, 3, 4, "minecraft:lightning_rod[facing=east,powered=false,waterlogged=false]");
        s.fill(2, 4, 4, 2, 5, 4, Kit.chain()).set(2, 3, 6, "minecraft:lightning_rod[facing=east,powered=false,waterlogged=false]");
        // Lanterns from the beams of the loft.
        s.set(4, 5, 4, Kit.hangingLantern()).set(5, 5, 7, Kit.hangingLantern());
    }

    /**
     * The smith's room in the loft: a hearth in the chimney breast, a table with two chairs on a rug, a bench under the west
     * window, a shelf of books, chests of goods of stripped wood and a sack; lanterns hang from the tie beams.
     */
    private static void smithyLoft(Structure s) {
        int y = SMITHY_LOFT + 1;
        Furnish.hearth(s, 9, y, 4, "west");
        Furnish.rug(s, 4, 4, 6, 6, y, Kit.CLOTH_CARPET);
        Furnish.table(s, 5, y, 5, "spruce");
        Furnish.chair(s, 4, y, 5, "east", "oak");
        Furnish.chair(s, 6, y, 5, "west", "oak");
        Furnish.bench(s, 2, 5, 2, 6, y, "east", "spruce");
        Furnish.books(s, 8, 7, 8, 7, y, y + 1);
        Furnish.crate(s, 8, y, 2, "spruce");
        Furnish.crate(s, 7, y, 2, "oak");
        Furnish.sack(s, 2, y, 7, false);
        s.set(8, y + 1, 2, Furnish.candles(2));
        s.set(5, 9, 3, Kit.hangingLantern());
        s.set(3, 9, 6, Kit.hangingLantern());
        chandelier(s, 5, 6, 13, 16);
    }

    // ---------------------------------------------------------------- the inn

    /** The floor of the first storey on the joists that jut one row over the street. */
    private static final int INN_JETTY1 = 5;
    /** The floor of the second storey, jutting one row further. */
    private static final int INN_JETTY2 = 9;
    /** The plate under the eaves and the floor of the attic. */
    private static final int INN_PLATE = 13;

    /**
     * The inn of a town: three storeys under a steep roof of red tiles whose eaves run along the street. The ground storey of
     * stone with quoins of rough stone, windows under lintels of trim and an arched door with a carved keystone; the first
     * storey of timber and plaster juts one row over the street on carved brackets, and the second juts once more, so that
     * the front steps forward floor by floor. A hanging sign, a pair of yellow banners on a beam with a lantern under it,
     * reaches over the street, benches stand under the jetty beside the door, and a great chimney of stone climbs the east
     * gable over the ridge.
     *
     * <p>Inside, the tap room: a counter with a keg and a lantern on it, the innkeeper's work post and chest behind it, two
     * barrels and the kitchen with its cauldron, fire and smoker along the back wall, the hearth with a spit over the fire in
     * the east wall, a long table with benches and two smaller tables; a stair in two flights climbs the west wall. Five
     * guest rooms with ten beds on the two upper storeys, two of them with a hearth, and an attic for stores.
     */
    static Structure tavern() {
        Structure s = new Structure(13, 26, 15);
        innGround(s);
        innUpperFloors(s);
        innRoof(s);
        innChimney(s);
        innSign(s);
        innStairs(s);
        innTapRoom(s);
        innRooms(s);
        innAttic(s);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The ground storey: footing, floor, stone walls on a plinth with quoins, the arched door with its step, windows with
     * lintels and sills, benches and a lantern under the jetty.
     */
    private static void innGround(Structure s) {
        s.fill(1, 0, 1, 11, 0, 11, Kit.ROUGH);
        s.fill(2, 0, 2, 10, 0, 10, SPRUCE_PLANKS);
        s.walls(1, 1, 1, 11, 4, 11, Kit.WALL);
        s.walls(1, 1, 1, 11, 1, 11, Kit.ROUGH);
        quoins(s, 1, 1, 1, 1, 1, 4);
        quoins(s, 11, 1, -1, 1, 1, 4);
        quoins(s, 1, 11, 1, -1, 1, 4);
        quoins(s, 11, 11, -1, -1, 1, 4);
        // The door between jambs of trim under an arch with a carved keystone, a step before it.
        s.door(6, 1, 11, DOOR, "north", "left");
        s.fill(5, 1, 11, 5, 2, 11, Kit.TRIM).fill(7, 1, 11, 7, 2, 11, Kit.TRIM);
        s.set(5, 3, 11, Kit.upside(Kit.TRIM_STAIRS, "west")).set(7, 3, 11, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.set(6, 3, 11, Kit.CARVED);
        s.set(6, 0, 12, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        // Windows.
        for (int x : new int[]{3, 4, 8, 9}) {
            stoneWindow(s, x, 11, 2, 3, 0, 1);
        }
        stoneWindow(s, 1, 9, 2, 3, -1, 0);
        stoneWindow(s, 11, 10, 2, 3, 1, 0);
        stoneWindow(s, 11, 2, 2, 3, 1, 0);
        stoneWindow(s, 7, 1, 2, 3, 0, -1);
        stoneWindow(s, 8, 1, 2, 3, 0, -1);
        // Benches under the jetty and a lantern hanging over the doorstep.
        Furnish.bench(s, 2, 12, 3, 12, 0, "south", "spruce");
        Furnish.bench(s, 9, 12, 10, 12, 0, "south", "spruce");
        s.set(6, INN_JETTY1 - 1, 12, Kit.hangingLantern());
    }

    /**
     * The two upper storeys of timber frame: the joists of each jutting one row further over the street on brackets, plaster
     * between posts of spruce, windows with shutters on the sides, braces in the corner panels, the plate and the attic floor.
     */
    private static void innUpperFloors(Structure s) {
        // The first storey on its jetty.
        s.beam(1, 1, 11, 12, INN_JETTY1, SPRUCE_LOG);
        s.fill(2, INN_JETTY1, 2, 10, INN_JETTY1, 11, SPRUCE_PLANKS);
        for (int x : new int[]{1, 3, 5, 7, 9, 11}) {
            s.set(x, INN_JETTY1 - 1, 12, Kit.upside(SPRUCE_STAIRS, "north"));
        }
        s.walls(1, 6, 1, 11, 8, 12, Kit.PLASTER);
        postsX(s, 12, 6, 8, 1, 3, 6, 9, 11);
        postsX(s, 1, 6, 8, 1, 3, 6, 9, 11);
        postsZ(s, 1, 6, 8, 4, 8);
        postsZ(s, 11, 6, 8, 4, 8);
        s.fill(4, 7, 12, 5, 8, 12, Kit.GLASS).fill(7, 7, 12, 8, 8, 12, Kit.GLASS);
        s.set(2, 6, 12, Kit.stairs(SPRUCE_STAIRS, "east")).set(10, 6, 12, Kit.stairs(SPRUCE_STAIRS, "west"));
        s.set(2, 6, 11, Kit.PLASTER).set(10, 6, 11, Kit.PLASTER);
        shutteredZ(s, 9, 10, 7, 8, 1, "west");
        shutteredZ(s, 9, 10, 7, 8, 11, "east");
        s.fill(1, 7, 5, 1, 8, 6, Kit.GLASS);
        s.fill(4, 7, 1, 5, 8, 1, Kit.GLASS).fill(7, 7, 1, 8, 8, 1, Kit.GLASS);
        // The second storey on its jetty.
        s.beam(1, 1, 11, 13, INN_JETTY2, SPRUCE_LOG);
        s.fill(2, INN_JETTY2, 2, 10, INN_JETTY2, 12, SPRUCE_PLANKS);
        for (int x : new int[]{1, 3, 6, 9, 11}) {
            s.set(x, INN_JETTY2 - 1, 13, Kit.upside(SPRUCE_STAIRS, "north"));
        }
        s.walls(1, 10, 1, 11, 12, 13, Kit.PLASTER);
        postsX(s, 13, 10, 12, 1, 3, 6, 9, 11);
        postsX(s, 1, 10, 12, 1, 3, 6, 9, 11);
        postsZ(s, 1, 10, 12, 4, 8);
        postsZ(s, 11, 10, 12, 4, 8);
        s.fill(4, 11, 13, 5, 12, 13, Kit.GLASS).fill(7, 11, 13, 8, 12, 13, Kit.GLASS);
        s.set(2, 11, 13, Kit.GLASS).set(10, 11, 13, Kit.GLASS);
        s.set(2, 10, 13, Kit.stairs(SPRUCE_STAIRS, "east")).set(10, 10, 13, Kit.stairs(SPRUCE_STAIRS, "west"));
        s.set(2, 10, 12, Kit.PLASTER).set(10, 10, 12, Kit.PLASTER);
        shutteredZ(s, 10, 11, 11, 12, 1, "west");
        shutteredZ(s, 10, 11, 11, 12, 11, "east");
        s.fill(1, 11, 2, 1, 12, 2, Kit.GLASS).fill(11, 11, 2, 11, 12, 2, Kit.GLASS);
        s.fill(6, 11, 1, 7, 12, 1, Kit.GLASS).fill(9, 11, 1, 9, 12, 1, Kit.GLASS);
        // The plate and the floor of the attic.
        s.beam(1, 1, 11, 13, INN_PLATE, SPRUCE_LOG);
        s.fill(2, INN_PLATE, 2, 10, INN_PLATE, 12, SPRUCE_PLANKS);
    }

    /**
     * The steep roof of red tiles with its eaves to the street, its gables of plaster in a frame of spruce with a king post,
     * two posts, a collar and windows into the attic.
     */
    private static void innRoof(Structure s) {
        int[] under = roofX(s, 0, 12, 0, 14, INN_PLATE, 2, Kit.TILE);
        for (int x : new int[]{1, 11}) {
            gableAtX(s, 0, under, x, INN_PLATE + 1, Kit.PLASTER);
            s.fill(x, INN_PLATE + 1, 7, x, under[7] - 1, 7, post());
            s.fill(x, INN_PLATE + 1, 4, x, under[4] - 1, 4, post());
            s.fill(x, INN_PLATE + 1, 10, x, under[10] - 1, 10, post());
            s.fill(x, 18, 5, x, 18, 9, Kit.log(SPRUCE_LOG, "z"));
            s.fill(x, 15, 5, x, 16, 6, Kit.GLASS).fill(x, 15, 8, x, 16, 9, Kit.GLASS);
        }
        innDormer(s, 3);
        innDormer(s, 9);
        s.set(0, under[7] + 1, 7, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    /**
     * A dormer in the front slope of the roof of the inn, standing on the plate of the second storey over column {@code x}:
     * a window between two posts with cheeks of plaster, a little gable of plaster over it and a roof of tiles running back
     * into the slope.
     */
    private static void innDormer(Structure s, int x) {
        int y = INN_PLATE + 1;
        int front = 13;
        s.fill(x - 1, y, front, x - 1, y + 1, front, post()).fill(x + 1, y, front, x + 1, y + 1, front, post());
        s.fill(x, y, front, x, y + 1, front, Kit.GLASS);
        s.set(x, y + 2, front, Kit.PLASTER);
        s.fill(x - 1, y, front - 1, x - 1, y + 1, front - 1, Kit.PLASTER).fill(x + 1, y, front - 1, x + 1, y + 1, front - 1, Kit.PLASTER);
        s.fill(x, y, front - 1, x, y + 2, front - 1, AIR);
        s.fill(x - 1, y + 2, front - 1, x - 1, y + 2, front + 1, Kit.stairs(Kit.TILE.stairs(), "east"));
        s.fill(x + 1, y + 2, front - 1, x + 1, y + 2, front + 1, Kit.stairs(Kit.TILE.stairs(), "west"));
        s.fill(x, y + 3, front - 2, x, y + 3, front + 1, Kit.slab(Kit.TILE.slab()));
    }

    /**
     * The great chimney on the east gable: a breast of rough stone with weathered shoulders, a stack of rough stone and of
     * stone over the plate, banded and capped with trim, two pots on top. Inside, the hearth of the tap room: jambs of rough
     * stone, the fire on a hearthstone of trim, a spit of chain over it and a mantel of trim with candles.
     */
    private static void innChimney(Structure s) {
        s.fill(12, 0, 4, 12, 3, 8, Kit.ROUGH);
        s.set(12, 4, 4, Kit.stairs(Kit.ROUGH_STAIRS, "south")).set(12, 4, 8, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.fill(12, 4, 5, 12, INN_PLATE - 1, 7, Kit.ROUGH);
        s.fill(12, INN_PLATE, 5, 12, 23, 7, Kit.WALL);
        s.fill(12, INN_PLATE, 5, 12, INN_PLATE, 7, Kit.TRIM);
        s.fill(12, 23, 5, 12, 23, 7, Kit.TRIM);
        s.set(12, 24, 5, Kit.ROUGH_FENCE).set(12, 24, 7, Kit.ROUGH_FENCE);
        // The hearth of the tap room.
        s.fill(11, 1, 4, 11, 4, 8, Kit.ROUGH);
        s.fill(10, 1, 4, 10, 3, 4, Kit.ROUGH).fill(10, 1, 8, 10, 3, 8, Kit.ROUGH);
        s.fill(10, 0, 5, 10, 0, 7, Kit.TRIM);
        s.set(10, 1, 6, CAMPFIRE);
        s.fill(10, 2, 5, 10, 2, 7, "minecraft:chain[axis=z,waterlogged=false]");
        s.fill(10, 3, 5, 10, 3, 7, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.set(10, 4, 5, Furnish.candles(2)).set(10, 4, 7, Furnish.candles(3));
        // The breast inside the timber walls of the two rooms above that have a hearth.
        s.fill(11, 6, 5, 11, 8, 7, Kit.ROUGH);
        s.fill(11, 10, 4, 11, 12, 6, Kit.ROUGH);
    }

    /**
     * The hanging sign of the inn: a beam of spruce on a knee brace reaching over the street from the first storey, a yellow
     * banner hanging on each side of its end and a lantern under it.
     */
    private static void innSign(Structure s) {
        s.fill(10, 7, 13, 10, 7, 14, Kit.log(SPRUCE_LOG, "z"));
        s.set(10, 6, 13, Kit.upside(SPRUCE_STAIRS, "north"));
        s.set(9, 7, 14, Kit.wallBanner("yellow", "west")).set(11, 7, 14, Kit.wallBanner("yellow", "east"));
        s.set(10, 6, 14, Kit.hangingLantern());
    }

    /**
     * The stair in two flights at the back of the west wall: the first rises north along the wall from the tap room to a
     * landing at the back, the second rises south beside it to the second storey; their undersides are boarded, rails guard
     * the openings in the floors and lanterns light them.
     */
    private static void innStairs(Structure s) {
        for (int k = 0; k < 5; k++) {
            s.set(2, 1 + k, 7 - k, Kit.stairs(OAK_STAIRS, "north"));
            if (k > 0) {
                s.fill(2, 1, 7 - k, 2, k, 7 - k, SPRUCE_PLANKS);
            }
        }
        s.fill(2, INN_JETTY1, 4, 2, INN_JETTY1, 5, AIR);
        for (int k = 0; k < 4; k++) {
            s.set(3, 6 + k, 3 + k, Kit.stairs(OAK_STAIRS, "south"));
            if (k > 0) {
                s.fill(3, 6, 3 + k, 3, 5 + k, 3 + k, SPRUCE_PLANKS);
            }
        }
        s.fill(3, INN_JETTY2, 4, 3, INN_JETTY2, 5, AIR);
        s.set(2, 6, 6, Kit.fence("spruce"));
        s.set(2, 10, 4, Kit.fence("spruce")).set(2, 10, 5, Kit.fence("spruce")).set(3, 10, 3, Kit.fence("spruce"));
        s.set(2, 8, 3, Kit.hangingLantern());
        s.set(2, 12, 3, Kit.hangingLantern());
    }

    /**
     * The tap room: the counter of stripped logs with a keg, a lantern, candles and a pot on it; behind it the innkeeper's
     * work post with its chest, the kitchen with its cauldron, fire and board, the smoker, two barrels and a sack along the
     * back wall under a shelf; a long table with two benches by the west window, a table by the hearth and one by the east
     * window with chairs; lanterns hang from the beams.
     */
    private static void innTapRoom(Structure s) {
        int y = 1;
        s.fill(4, y, 4, 9, y, 4, "minecraft:stripped_spruce_log[axis=x]");
        s.set(8, y + 1, 4, "minecraft:barrel[facing=south,open=false]");
        s.set(5, y + 1, 4, Kit.lantern()).set(6, y + 1, 4, Furnish.candles(3)).set(7, y + 1, 4, POT);
        s.set(9, y, 3, WORK_POST).set(10, y, 3, Kit.chest("west"));
        Furnish.kitchen(s, 3, y, 2, "south", "spruce");
        s.set(6, y, 2, "minecraft:smoker[facing=south,lit=true]");
        s.set(7, y, 2, "minecraft:barrel[facing=south,open=false]").set(8, y, 2, "minecraft:barrel[facing=south,open=false]");
        Furnish.sack(s, 9, y, 2, true);
        Furnish.crate(s, 10, y, 2, "spruce");
        Furnish.crate(s, 2, y, 2, "spruce");
        s.fill(6, y + 2, 2, 10, y + 2, 2, Kit.trapdoor(SHELF, "south", true));
        s.set(7, y + 3, 2, POT).set(9, y + 3, 2, POT).set(8, y + 3, 2, Furnish.candles(2));
        // The tables.
        Furnish.longTable(s, 3, 9, 5, 9, y, "spruce");
        Furnish.bench(s, 3, 8, 5, 8, y, "south", "spruce");
        Furnish.bench(s, 3, 10, 5, 10, y, "north", "spruce");
        s.set(4, y + 1, 9, Furnish.candles(3));
        Furnish.table(s, 8, y, 7, "spruce");
        Furnish.chair(s, 7, y, 7, "east", "oak");
        Furnish.chair(s, 8, y, 8, "north", "oak");
        Furnish.table(s, 9, y, 10, "spruce");
        Furnish.chair(s, 8, y, 10, "east", "oak");
        Furnish.chair(s, 10, y, 10, "west", "oak");
        // Lanterns from the beams.
        s.set(4, INN_JETTY1 - 1, 7, Kit.hangingLantern()).set(8, INN_JETTY1 - 1, 9, Kit.hangingLantern());
        s.set(6, INN_JETTY1 - 1, 3, Kit.hangingLantern()).set(6, INN_JETTY1 - 1, 6, Kit.hangingLantern());
    }

    /**
     * The guest rooms. On the first storey a corridor along the back wall leads to a room of two beds on the west, with a
     * table and a chest of goods, and to a room of two beds on the east with its hearth. On the second storey a corridor
     * from the stair leads to a room of two beds at the back with a hearth and to two rooms over the street, one with two
     * beds and one with a double bed; the ladder to the attic stands at its end.
     */
    private static void innRooms(Structure s) {
        int f = INN_JETTY1 + 1;
        s.fill(4, f, 3, 10, f + 2, 3, OAK_PLANKS);
        s.fill(6, f, 4, 6, f + 2, 11, OAK_PLANKS);
        s.door(5, f, 3, DOOR, "south", "left");
        s.door(8, f, 3, DOOR, "south", "left");
        s.set(7, f + 2, 2, Kit.hangingLantern());
        // The west room.
        Furnish.bed(s, 2, f, 10, "south", "red");
        Furnish.bed(s, 4, f, 10, "south", "red");
        Furnish.crate(s, 3, f, 11, "spruce");
        s.set(3, f + 1, 11, Kit.lantern());
        Furnish.table(s, 5, f, 6, "spruce");
        Furnish.chair(s, 4, f, 6, "east", "oak");
        Furnish.rug(s, 2, 8, 5, 9, f, Kit.CLOTH_CARPET);
        s.set(4, f + 2, 8, Kit.hangingLantern());
        // The east room with its hearth.
        Furnish.hearth(s, 11, f, 6, "west");
        Furnish.bed(s, 8, f, 10, "south", "white");
        Furnish.bed(s, 10, f, 10, "south", "white");
        Furnish.crate(s, 9, f, 11, "spruce");
        s.set(9, f + 1, 11, Furnish.candles(2));
        Furnish.chair(s, 9, f, 6, "east", "oak");
        Furnish.rug(s, 8, 7, 9, 8, f, "minecraft:brown_carpet");
        s.set(8, f + 2, 5, Kit.hangingLantern());
        // The second storey.
        int g = INN_JETTY2 + 1;
        s.fill(4, g, 2, 4, g + 2, 5, OAK_PLANKS);
        s.fill(4, g, 6, 10, g + 2, 6, OAK_PLANKS);
        s.fill(2, g, 8, 10, g + 2, 8, OAK_PLANKS);
        s.fill(6, g, 9, 6, g + 2, 12, OAK_PLANKS);
        s.door(7, g, 6, DOOR, "north", "left");
        s.door(3, g, 8, DOOR, "south", "left");
        s.door(9, g, 8, DOOR, "south", "left");
        s.set(6, g + 2, 7, Kit.hangingLantern());
        // The back room with its hearth.
        Furnish.hearth(s, 11, g, 5, "west");
        Furnish.bed(s, 6, g, 3, "north", "light_gray");
        Furnish.bed(s, 8, g, 3, "north", "light_gray");
        Furnish.crate(s, 7, g, 2, "spruce");
        s.set(7, g + 1, 2, Kit.lantern());
        Furnish.rug(s, 6, 4, 9, 5, g, Kit.CLOTH_CARPET);
        Furnish.chair(s, 5, g, 5, "east", "oak");
        s.set(7, g + 2, 4, Kit.hangingLantern());
        // The front rooms.
        Furnish.bed(s, 2, g, 11, "south", "red");
        Furnish.bed(s, 4, g, 11, "south", "red");
        Furnish.crate(s, 3, g, 12, "spruce");
        s.set(3, g + 1, 12, Furnish.candles(1));
        s.set(5, g, 9, Kit.stairs(OAK_STAIRS, "west"));
        s.set(3, g + 2, 10, Kit.hangingLantern());
        Furnish.bed(s, 8, g, 11, "south", "brown");
        Furnish.bed(s, 9, g, 11, "south", "brown");
        Furnish.crate(s, 10, g, 12, "spruce");
        s.set(10, g + 1, 12, Kit.lantern());
        Furnish.table(s, 7, g, 9, "spruce");
        Furnish.rug(s, 8, 9, 9, 10, g, "minecraft:brown_carpet");
        s.set(8, g + 2, 10, Kit.hangingLantern());
        // The ladder to the attic.
        s.fill(10, g, 7, 10, INN_PLATE, 7, Kit.ladder("west"));
    }

    /**
     * The attic under the roof: stores of the inn in crates and sacks, a bale of hay, lanterns hanging from the collar.
     */
    private static void innAttic(Structure s) {
        int a = INN_PLATE + 1;
        Furnish.crate(s, 3, a, 5, "spruce");
        Furnish.crate(s, 3, a, 6, "oak");
        Furnish.crate(s, 3, a + 1, 5, "spruce");
        Furnish.sack(s, 4, a, 9, true);
        Furnish.sack(s, 5, a, 9, false);
        Furnish.sack(s, 4, a + 1, 9, true);
        s.set(8, a, 5, HAY).set(8, a, 6, HAY).set(9, a, 5, HAY);
        chandelier(s, 3, 7, 18, 21);
        chandelier(s, 6, 7, 18, 21);
        chandelier(s, 9, 7, 18, 21);
    }

    // ---------------------------------------------------------------- the town gate

    /** The top of the town wall and the layer the feet of those who walk on it are in. */
    private static final int GATE_WALK_FLOOR = 6;
    private static final int GATE_WALK = 7;
    /** The town wall, three thick; its battlements stand on the outer row. */
    private static final int GATE_WALL_Z1 = 5;
    private static final int GATE_WALL_Z2 = 7;
    /** The two round towers: their middle row, diameter, the top of their walls and the floor of their lookout. */
    private static final int TOWER_Z = 6;
    private static final int TOWER_D = 7;
    private static final int TOWER_TOP = 14;
    private static final int TOWER_FLOOR = 10;
    /** The gate tower between them and the passage through it. */
    private static final int GATE_X1 = 8;
    private static final int GATE_X2 = 14;
    private static final int GATE_Z1 = 2;
    private static final int GATE_Z2 = 8;
    private static final int PASS_X1 = 10;
    private static final int PASS_X2 = 12;
    /** The floors of the gate tower: the chamber over the passage, the guard room, the bell storey; its eaves. */
    private static final int CHAMBER = 7;
    private static final int GUARD = 11;
    private static final int BELFRY = 15;
    private static final int GATE_EAVES = 20;

    /**
     * A piece of the town wall with its gate. The curtain of stone, three thick on a plinth of rough stone, carries a walk
     * behind battlements on its outer face over a cornice of trim brackets. In the middle stands the gate tower: stone on a
     * plinth with quoins, the passage three wide and four high through it under arches of trim with carved keystones, the
     * portcullis raised behind the outer arch and the leaves of the gate standing open behind the inner one; over the stone
     * a bell storey of timber and plaster jutting over both faces on brackets, under a steep roof of tiles with a finial.
     * Two round towers flank it, each on a battered foot of rough stone, with arrow slits, a banner over the field, a ring of
     * brackets under a slender cone of slate that leans against the gate tower and a flag on its point. On the side of the
     * town a stair of stone climbs along each tower to a platform at the height of the walk.
     *
     * <p>Inside, the chamber over the passage holds the windlass of the portcullis and a murder hole, with doors into both
     * towers; a ladder climbs to the guard room and to the bell; from the towers doors lead out onto the walk, and a ladder
     * in each tower climbs from its store room through its guard room to the lookout under the cone.
     */
    static Structure wallGate() {
        Structure s = new Structure(23, 30, 11);
        gateGround(s);
        gateCurtain(s);
        gateHouse(s);
        gateTower(s, 6, 1);
        gateTower(s, 16, -1);
        gateTop(s);
        gateStairs(s, 1);
        gateStairs(s, -1);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The ground: grass on both sides of the wall, the road of rough stone through the gate, flagstones in the passage.
     */
    private static void gateGround(Structure s) {
        s.fill(0, 0, 0, 22, 0, 10, GRASS);
        s.fill(PASS_X1 - 1, 0, 0, PASS_X2 + 1, 0, 10, Kit.ROUGH);
    }

    /**
     * The curtain: stone on a footing and a plinth of rough stone, battlements on its outer row over a cornice of trim
     * brackets, lanterns in the crenels at both ends.
     */
    private static void gateCurtain(Structure s) {
        s.fill(0, 0, GATE_WALL_Z1, 22, 0, GATE_WALL_Z2, Kit.ROUGH);
        s.fill(0, 1, GATE_WALL_Z1, 22, GATE_WALK_FLOOR, GATE_WALL_Z2, Kit.WALL);
        s.fill(0, 1, GATE_WALL_Z1, 22, 1, GATE_WALL_Z2, Kit.ROUGH);
        Kit.battlements(s, 0, GATE_WALL_Z2, 22, GATE_WALL_Z2, GATE_WALK, Kit.WALL);
        for (int x : new int[]{0, 1, 2, 3, 19, 20, 21, 22}) {
            s.set(x, GATE_WALK_FLOOR, GATE_WALL_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        }
        for (int x : new int[]{0, 2, 20, 22}) {
            s.set(x, GATE_WALK + 1, GATE_WALL_Z2, Kit.lantern());
        }
    }

    /**
     * The stone body of the gate tower up to the guard room: a block on a plinth with quoins, the passage under a vault
     * with arches of trim and carved keystones at both ends, lanterns in niches and torches outside, the leaves of the gate,
     * the portcullis and the chamber over the passage with its windlass and murder hole.
     */
    private static void gateHouse(Structure s) {
        s.fill(GATE_X1, 0, GATE_Z1, GATE_X2, 0, GATE_Z2, Kit.ROUGH);
        s.fill(PASS_X1, 0, GATE_Z1, PASS_X2, 0, GATE_Z2, Kit.WALL);
        s.fill(GATE_X1, 1, GATE_Z1, GATE_X2, BELFRY - 1, GATE_Z2, Kit.WALL);
        s.walls(GATE_X1, 1, GATE_Z1, GATE_X2, 1, GATE_Z2, Kit.ROUGH);
        // The passage under its vault: four high, the haunches of the arch on both sides of its top.
        s.fill(PASS_X1, 1, GATE_Z1, PASS_X2, 4, GATE_Z2, AIR);
        for (int z = GATE_Z1; z <= GATE_Z2; z++) {
            boolean arch = z == GATE_Z1 || z == GATE_Z2;
            s.set(PASS_X1, 5, z, Kit.upside(arch ? Kit.TRIM_STAIRS : Kit.WALL_STAIRS, "west"));
            s.set(PASS_X2, 5, z, Kit.upside(arch ? Kit.TRIM_STAIRS : Kit.WALL_STAIRS, "east"));
            s.set(11, 5, z, AIR);
        }
        for (int z : new int[]{GATE_Z1, GATE_Z2}) {
            s.set(PASS_X1 - 1, 6, z, Kit.TRIM).set(PASS_X1, 6, z, Kit.TRIM).set(PASS_X2, 6, z, Kit.TRIM).set(PASS_X2 + 1, 6, z, Kit.TRIM);
            s.set(11, 6, z, Kit.CARVED);
        }
        // Lanterns in niches of the passage, torches beside the arches.
        s.set(PASS_X1 - 1, 2, 4, Kit.lantern()).set(PASS_X2 + 1, 2, 4, Kit.lantern());
        s.set(PASS_X1 - 1, 3, GATE_Z2 + 1, Kit.wallTorch("south")).set(PASS_X2 + 1, 3, GATE_Z2 + 1, Kit.wallTorch("south"));
        s.set(PASS_X1 - 1, 3, GATE_Z1 - 1, Kit.wallTorch("north")).set(PASS_X2 + 1, 3, GATE_Z1 - 1, Kit.wallTorch("north"));
        // The leaves of the gate standing open against the walls of the passage, boards over them.
        openLeaf(s, PASS_X1, 3, "left");
        openLeaf(s, PASS_X2, 3, "right");
        s.fill(PASS_X1, 3, 3, PASS_X1, 4, 3, Kit.shutter(HATCH, "east"));
        s.fill(PASS_X2, 3, 3, PASS_X2, 4, 3, Kit.shutter(HATCH, "west"));
        // The chamber over the passage, the murder hole in its floor.
        s.fill(PASS_X1, CHAMBER, 3, PASS_X2, CHAMBER, 6, SPRUCE_PLANKS);
        s.fill(PASS_X1, CHAMBER + 1, 3, PASS_X2, GUARD - 1, 7, AIR);
        s.set(11, 6, 4, AIR).set(11, CHAMBER, 4, Kit.trapdoor(HATCH, "north", true));
        // The portcullis, raised: its bars hang from the chamber down to the top of the passage.
        s.fill(PASS_X1, 5, 7, PASS_X2, GUARD - 2, 7, Kit.BARS);
        // The windlass: a drum of spruce on two posts before the bars.
        s.set(PASS_X1, CHAMBER + 1, 6, Kit.fence("spruce")).set(PASS_X2, CHAMBER + 1, 6, Kit.fence("spruce"));
        s.fill(PASS_X1, CHAMBER + 2, 6, PASS_X2, CHAMBER + 2, 6, Kit.log(SPRUCE_LOG, "x"));
        s.set(11, GUARD - 1, 4, Kit.hangingLantern());
        // Slits of the chamber to the field, windows to the town.
        s.set(PASS_X1, CHAMBER + 2, GATE_Z2, Kit.BARS).set(PASS_X2, CHAMBER + 2, GATE_Z2, Kit.BARS);
        s.set(11, CHAMBER + 2, GATE_Z1, Kit.GLASS);
    }

    /**
     * A leaf of the gate standing open in the passage: a door turned on its hinge so that it lies against the wall beside it,
     * the west leaf on its left hinge and the east leaf on its right one.
     */
    private static void openLeaf(Structure s, int x, int z, String hinge) {
        s.set(x, 1, z, DOOR + "[facing=north,half=lower,hinge=" + hinge + ",open=true,powered=false]");
        s.set(x, 2, z, DOOR + "[facing=north,half=upper,hinge=" + hinge + ",open=true,powered=false]");
    }

    /**
     * A round tower beside the gate: a footing of rough stone and a battered foot of rough stairs, a wall of stone on a
     * plinth with bands of trim at its floors, a ring of trim brackets under the eave and a slender cone of slate with a flag
     * on its point. A door from the town into the store room on the ground, a door at the height of the walk out to the
     * wall, a ladder up through the guard room to the lookout; slits, windows, a banner of the kingdom over the field.
     * {@code side} is 1 for the west tower, -1 for the east one, so that both are drawn alike.
     */
    private static void gateTower(Structure s, int cx, int side) {
        int cz = TOWER_Z;
        Kit.disc(s, cx, 0, cz, TOWER_D, Kit.ROUGH);
        stairRing(s, cx, 1, cz, TOWER_D, Kit.ROUGH_STAIRS, false, GATE_X1, GATE_X2);
        Kit.tube(s, cx, 1, TOWER_TOP, cz, TOWER_D, Kit.WALL);
        Kit.tube(s, cx, 1, 2, cz, TOWER_D, Kit.ROUGH);
        Kit.tube(s, cx, GATE_WALK_FLOOR, GATE_WALK_FLOOR, cz, TOWER_D, Kit.TRIM);
        Kit.tube(s, cx, TOWER_FLOOR, TOWER_FLOOR, cz, TOWER_D, Kit.TRIM);
        for (int y = 1; y <= TOWER_TOP; y++) {
            Kit.disc(s, cx, y, cz, 5, AIR);
        }
        Kit.disc(s, cx, 0, cz, 5, SPRUCE_PLANKS);
        Kit.disc(s, cx, GATE_WALK_FLOOR, cz, 5, SPRUCE_PLANKS);
        Kit.disc(s, cx, TOWER_FLOOR, cz, 5, SPRUCE_PLANKS);
        stairRing(s, cx, TOWER_TOP, cz, TOWER_D, Kit.TRIM_STAIRS, true, GATE_X1, GATE_X2);
        int top = coneRoof(s, cx, TOWER_TOP + 1, cz, TOWER_D, new int[]{2, 3, 4, 3}, Kit.SLATE);
        s.set(cx, top, cz, Kit.fence("spruce")).set(cx, top + 1, cz, Kit.banner("red", 0));
        // Doors: from the town and out to the walk.
        String in = side > 0 ? "east" : "west";
        s.door(cx, 1, cz - 3, DOOR, "south", "left");
        s.door(cx - 3 * side, GATE_WALK, cz, DOOR, in, "left");
        // The ladder up the side of the tower to the lookout.
        for (int y = 1; y <= TOWER_FLOOR; y++) {
            s.set(cx - 2 * side, y, cz + 1, Kit.ladder(in));
        }
        // Slits, windows and the banner over the field.
        s.fill(cx, 2, cz + 3, cx, 3, cz + 3, Kit.BARS);
        s.fill(cx, GATE_WALK + 1, cz + 3, cx, GATE_WALK + 2, cz + 3, Kit.BARS);
        s.fill(cx - 3 * side, TOWER_FLOOR + 1, cz, cx - 3 * side, TOWER_FLOOR + 2, cz, Kit.GLASS);
        s.fill(cx, TOWER_FLOOR + 1, cz - 3, cx, TOWER_FLOOR + 2, cz - 3, Kit.GLASS);
        s.fill(cx - side, TOWER_FLOOR + 1, cz + 3, cx - side, TOWER_FLOOR + 2, cz + 3, Kit.GLASS);
        s.set(cx, TOWER_FLOOR, cz + 4, Kit.wallBanner("red", "south"));
        // The rooms: stores on the ground, the guard room at the walk, the lookout; all lit.
        Furnish.crate(s, cx - side, 1, cz + 2, "spruce");
        Furnish.crate(s, cx, 1, cz + 2, "oak");
        Furnish.sack(s, cx - side, 2, cz + 2, false);
        s.set(cx + side, 1, cz + 2, HAY);
        Furnish.weaponRack(s, cx - side, 1, cz - 2, "south", "spruce");
        s.set(cx, GATE_WALK_FLOOR - 1, cz, Kit.hangingLantern());
        Furnish.table(s, cx, GATE_WALK, cz + 2, "spruce");
        Furnish.chair(s, cx + side, GATE_WALK, cz + 2, side > 0 ? "west" : "east", "oak");
        s.set(cx, GATE_WALK + 2, cz, Kit.hangingLantern());
        s.set(cx + side, TOWER_FLOOR + 1, cz + 1, Kit.lantern());
        Furnish.crate(s, cx + side, TOWER_FLOOR + 1, cz - 1, "spruce");
    }

    /**
     * The upper gate tower, drawn over the towers so that their cones lean against it: the doors from the chamber into both
     * towers, the guard room with its table, slits and banners; the bell storey of timber frame and plaster jutting over
     * both faces on brackets, with windows on every side, the bell of the town hanging from a beam; the steep roof of tiles
     * with a finial, and the ladder that climbs from the chamber to the bell.
     */
    private static void gateTop(Structure s) {
        // The doors from the chamber into the towers, a step down into each.
        s.door(GATE_X1 + 1, CHAMBER + 1, 5, DOOR, "east", "left");
        s.door(GATE_X2 - 1, CHAMBER + 1, 5, DOOR, "west", "left");
        s.set(GATE_X1, CHAMBER, 5, Kit.stairs(Kit.WALL_STAIRS, "east"));
        s.set(GATE_X2, CHAMBER, 5, Kit.stairs(Kit.WALL_STAIRS, "west"));
        // The guard room inside the stone, quoins on the back corners.
        s.fill(PASS_X1, GUARD, 3, PASS_X2, GUARD, 7, SPRUCE_PLANKS);
        s.fill(PASS_X1, GUARD + 1, 3, PASS_X2, BELFRY - 1, 7, AIR);
        quoins(s, GATE_X1, GATE_Z1, 1, 1, 2, BELFRY - 1);
        quoins(s, GATE_X2, GATE_Z1, -1, 1, 2, BELFRY - 1);
        Furnish.table(s, 12, GUARD + 1, 5, "spruce");
        Furnish.chair(s, 12, GUARD + 1, 6, "north", "oak");
        Furnish.weaponRack(s, 12, GUARD + 1, 3, "south", "spruce");
        s.set(11, GUARD + 3, 6, Kit.hangingLantern());
        s.set(PASS_X1, GUARD + 2, GATE_Z2, Kit.BARS).set(PASS_X2, GUARD + 2, GATE_Z2, Kit.BARS);
        s.set(PASS_X1, GUARD + 2, GATE_Z1, Kit.GLASS).set(PASS_X2, GUARD + 2, GATE_Z1, Kit.GLASS);
        s.set(11, GUARD + 2, GATE_Z2 + 1, Kit.wallBanner("red", "south"));
        s.set(11, GUARD + 2, GATE_Z1 - 1, Kit.wallBanner("red", "north"));
        // The bell storey jutting over both faces: a floor on a ring of beams, brackets under it.
        s.beam(GATE_X1, GATE_Z1 - 1, GATE_X2, GATE_Z2 + 1, BELFRY, SPRUCE_LOG);
        s.fill(GATE_X1 + 1, BELFRY, GATE_Z1, GATE_X2 - 1, BELFRY, GATE_Z2, SPRUCE_PLANKS);
        for (int x = GATE_X1; x <= GATE_X2; x += 2) {
            s.set(x, BELFRY - 1, GATE_Z2 + 1, Kit.upside(SPRUCE_STAIRS, "north"));
            s.set(x, BELFRY - 1, GATE_Z1 - 1, Kit.upside(SPRUCE_STAIRS, "south"));
        }
        s.fill(GATE_X1 + 1, BELFRY + 1, GATE_Z1, GATE_X2 - 1, GATE_EAVES + 4, GATE_Z2, AIR);
        s.walls(GATE_X1, BELFRY + 1, GATE_Z1 - 1, GATE_X2, GATE_EAVES - 1, GATE_Z2 + 1, Kit.PLASTER);
        postsX(s, GATE_Z1 - 1, BELFRY + 1, GATE_EAVES - 1, GATE_X1, 11, GATE_X2);
        postsX(s, GATE_Z2 + 1, BELFRY + 1, GATE_EAVES - 1, GATE_X1, 11, GATE_X2);
        postsZ(s, GATE_X1, BELFRY + 1, GATE_EAVES - 1, 5);
        postsZ(s, GATE_X2, BELFRY + 1, GATE_EAVES - 1, 5);
        for (int z : new int[]{GATE_Z1 - 1, GATE_Z2 + 1}) {
            s.fill(9, BELFRY + 2, z, 10, BELFRY + 3, z, Kit.GLASS).fill(12, BELFRY + 2, z, 13, BELFRY + 3, z, Kit.GLASS);
            s.set(9, BELFRY + 1, z, Kit.stairs(SPRUCE_STAIRS, "east")).set(13, BELFRY + 1, z, Kit.stairs(SPRUCE_STAIRS, "west"));
        }
        for (int x : new int[]{GATE_X1, GATE_X2}) {
            s.fill(x, BELFRY + 2, 2, x, BELFRY + 3, 3, Kit.GLASS).fill(x, BELFRY + 2, 7, x, BELFRY + 3, 8, Kit.GLASS);
        }
        // The bell of the town under a beam, lanterns, the ladder from the chamber.
        s.fill(GATE_X1 + 1, GATE_EAVES - 1, 5, GATE_X2 - 1, GATE_EAVES - 1, 5, Kit.log(SPRUCE_LOG, "x"));
        s.set(11, GATE_EAVES - 2, 5, "minecraft:bell[attachment=ceiling,facing=north,powered=false]");
        s.set(9, BELFRY + 1, 7, Kit.lantern()).set(13, BELFRY + 1, 3, Kit.lantern());
        s.fill(PASS_X1, CHAMBER + 1, 3, PASS_X1, BELFRY, 3, Kit.ladder("south"));
        // The steep roof of tiles over the jutting storey, its gables of plaster to the field and to the town in a frame of
        // spruce with a window, finials on both ends of the ridge.
        s.beam(GATE_X1, GATE_Z1 - 1, GATE_X2, GATE_Z2 + 1, GATE_EAVES, SPRUCE_LOG);
        int[] under = roofZ(s, GATE_X1, GATE_X2, GATE_Z1 - 2, GATE_Z2 + 2, GATE_EAVES, 2, Kit.TILE);
        for (int z : new int[]{GATE_Z1 - 1, GATE_Z2 + 1}) {
            gableAtZ(s, GATE_X1, under, z, GATE_EAVES + 1, Kit.PLASTER);
            s.fill(11, GATE_EAVES + 1, z, 11, under[11 - GATE_X1] - 1, z, post());
            s.fill(11, GATE_EAVES + 2, z, 11, GATE_EAVES + 3, z, Kit.GLASS);
        }
        int ridge = under[11 - GATE_X1] + 1;
        s.set(11, ridge, GATE_Z1 - 2, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        s.set(11, ridge, GATE_Z2 + 2, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        chandelier(s, 11, 3, GATE_EAVES + 2, ridge - 2);
    }

    /**
     * A stair of stone on the side of the town, climbing along the tower from the road to a platform at the height of the
     * walk: steps on a solid ramp behind a parapet with a coping rail, a lantern at its foot and one on the platform.
     * {@code side} is 1 for the west stair, -1 for the east one.
     */
    private static void gateStairs(Structure s, int side) {
        int z = 1;
        String up = side > 0 ? "west" : "east";
        for (int k = 0; k < 6; k++) {
            int x = side > 0 ? 8 - k : 14 + k;
            s.set(x, 0, z, Kit.ROUGH).set(x, 0, z - 1, Kit.ROUGH);
            s.set(x, 1 + k, z, Kit.stairs(Kit.WALL_STAIRS, up));
            if (k > 0) {
                s.fill(x, 1, z, x, k, z, Kit.WALL);
            }
            s.fill(x, 1, z - 1, x, 1 + k, z - 1, Kit.WALL);
            s.set(x, 2 + k, z - 1, Kit.WALL_FENCE);
        }
        int x1 = side > 0 ? 0 : 20;
        int x2 = side > 0 ? 2 : 22;
        s.fill(x1, 0, 0, x2, 0, GATE_WALL_Z1 - 1, Kit.ROUGH);
        s.fill(x1, 1, 0, x2, GATE_WALK_FLOOR, GATE_WALL_Z1 - 1, Kit.WALL);
        s.fill(x1, 1, 0, x2, 1, GATE_WALL_Z1 - 1, Kit.ROUGH);
        s.fill(x1, GATE_WALK, 0, x2, GATE_WALK, 0, Kit.WALL_FENCE);
        s.set(side > 0 ? 0 : 22, GATE_WALK + 1, 0, Kit.lantern());
        s.set(side > 0 ? 8 : 14, 3, 0, Kit.lantern());
    }
}
