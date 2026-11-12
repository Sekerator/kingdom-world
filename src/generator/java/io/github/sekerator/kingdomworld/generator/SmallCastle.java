package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle of a small kingdom of the world: compact, but a real castle. A curtain wall with a walk behind its battlements
 * runs between four round corner towers under conical roofs; in the front wall two slender round towers flank a deep arched
 * gate with a portcullis and a balcony over it. Behind a small courtyard with a well and a flower bed stands the keep: a
 * high hall whose gabled front with its rose window looks at the gate, and at its back the square donjon, the tallest part
 * of the castle, under a steep spire with a flag. The throne of the kingdom stands in the middle of the ground at the north
 * end of the hall; the castle leaves it and the ground under it out.
 *
 * <p>Layer 0 replaces the ground, layer 1 is level with the street. Every part is drawn with the blocks that stand for the
 * parts of a look, so that the castle takes the stone, roofs and colours of its kingdom.
 */
final class SmallCastle {
    private static final String AIR = Structure.AIR;
    private static final String PLANKS = "minecraft:spruce_planks";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String DARK_LOG = "minecraft:dark_oak_log";
    private static final String OAK_LOG = "minecraft:oak_log";
    private static final String DOOR = "minecraft:spruce_door";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String BOOKSHELF = "minecraft:bookshelf";
    private static final String GOLD = "minecraft:gold_block";
    private static final String YELLOW_WOOL = "minecraft:yellow_wool";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String FENCE = Kit.fence("oak");

    /** The ground is square, the box high enough for the spire and its flag. */
    private static final int SIZE = 31;
    private static final int HEIGHT = 40;
    /** The throne stands in the middle of the ground: x and z of its cell. */
    private static final int MID = 15;
    /** The top of the curtain wall: the walk lies on it, the feet of the guards one layer higher. */
    private static final int WALK = 6;
    /** The hall of the keep: its walls and their top. */
    private static final int HALL_X1 = 9;
    private static final int HALL_X2 = 21;
    private static final int HALL_Z1 = 12;
    private static final int HALL_Z2 = 20;
    private static final int HALL_TOP = 10;
    /** The donjon at the back of the keep, standing against the north wall. */
    private static final int DON_X1 = 11;
    private static final int DON_X2 = 19;
    private static final int DON_Z1 = 4;
    private static final int DON_Z2 = 12;
    private static final int DON_TOP = 21;
    /** The floors of the donjon (the layer of their planks); the spiral stair arrives at every one of them. */
    private static final int[] DON_FLOORS = {5, 9, 13, 17};
    /** The cells of the spiral stair around its newel, in the order it climbs, one layer a cell from layer 1. */
    private static final int[][] SPIRAL = {{18, 7}, {17, 7}, {16, 7}, {16, 6}, {16, 5}, {17, 5}, {18, 5}, {18, 6}};
    private static final int NEWEL_X = 17;
    private static final int NEWEL_Z = 6;
    /** The middle row of the gate towers and the gate passage between them. */
    private static final int GATE_Z = 27;
    private static final int PASS_X1 = 14;
    private static final int PASS_X2 = 16;

    private SmallCastle() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("classic_castle_small", "defense", "palace", "minecraft:chiseled_stone_bricks", MID, 1,
                SIZE - 1, castleSmall(), "none", 1, "")
                .at("door", MID, 1, SIZE - 1)
                .at("castle_gate", PASS_X1, 1, 26).at("castle_gate", PASS_X2, 1, 26)
                .at("palace_door", 14, 1, 19).at("palace_door", 16, 1, 19)
                .at("palace_hall", MID - 2, 1, MID + 3).at("palace_hall", MID + 2, 1, MID + 3)
                .at("palace_hall", 14, 1, 17).at("palace_hall", 16, 1, 17)
                .at("treasury", 12, 1, 13)
                .at("castle_wall", 3, WALK + 1, 10).at("castle_wall", 3, WALK + 1, 20)
                .at("castle_wall", 9, WALK + 1, 3).at("castle_wall", 21, WALK + 1, 3)
                .at("castle_wall", 27, WALK + 1, 10).at("castle_wall", 27, WALK + 1, 20)
                .at("castle_wall", MID, WALK + 1, 29).at("castle_wall", MID, WALK + 1, 26));
        return list;
    }

    // ---------------------------------------------------------------- shapes

    /** A plan of an odd width around its middle: round or square. */
    private interface Plan {
        boolean in(int dx, int dz, int width);
    }

    private static final Plan ROUND = Kit::inDisc;
    private static final Plan SQUARE = (dx, dz, width) -> Math.abs(dx) <= (width - 1) / 2 && Math.abs(dz) <= (width - 1) / 2;

    /** Whether a cell of a plan has a neighbour outside it. */
    private static boolean rim(Plan plan, int dx, int dz, int width) {
        return plan.in(dx, dz, width) && (!plan.in(dx + 1, dz, width) || !plan.in(dx - 1, dz, width)
                || !plan.in(dx, dz + 1, width) || !plan.in(dx, dz - 1, width));
    }

    /**
     * Stairs on the rim of a plan with their high side towards its middle: the slope of a roof, a batter at the foot of a
     * wall, or upside down a corbel under an overhang. A cell open on two sides gets a corner piece.
     */
    private static String inward(Plan plan, String stairs, int dx, int dz, int width, boolean upside) {
        boolean east = !plan.in(dx + 1, dz, width);
        boolean west = !plan.in(dx - 1, dz, width);
        boolean south = !plan.in(dx, dz + 1, width);
        boolean north = !plan.in(dx, dz - 1, width);
        String facing;
        String shape = "straight";
        if (east != west && south != north) {
            facing = south ? "north" : "south";
            shape = east == south ? "outer_left" : "outer_right";
        } else if (east != west) {
            facing = east ? "west" : "east";
        } else {
            facing = south ? "north" : "south";
        }
        return stairs + "[facing=" + facing + ",half=" + (upside ? "top" : "bottom") + ",shape=" + shape + ",waterlogged=false]";
    }

    /**
     * A ring around a plan of the given width, one block wide, at one layer: stairs turned inwards on its outer rim and
     * the block itself where the ring is wider. A batter of rough stone at the foot of a tower, or upside down the corbels
     * under the eaves of its roof.
     */
    private static void ring(Structure s, Plan plan, int cx, int y, int cz, int width, String stairs, String block, boolean upside) {
        int radius = (width + 1) / 2;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (plan.in(dx, dz, width + 2) && !plan.in(dx, dz, width) && s.inside(cx + dx, y, cz + dz)) {
                    s.set(cx + dx, y, cz + dz, rim(plan, dx, dz, width + 2)
                            ? inward(plan, stairs, dx, dz, width + 2, upside) : block);
                }
            }
        }
    }

    /** Whether a cell lies within a round or square plan of a radius that need not be whole. */
    private static boolean within(boolean round, int dx, int dz, double radius) {
        if (radius < 0) {
            return false;
        }
        return round ? dx * dx + dz * dz <= radius * radius + radius : Math.max(Math.abs(dx), Math.abs(dz)) <= radius + 0.01;
    }

    /** Stairs with their high side towards the middle of a plan, a corner piece on the diagonals. */
    private static String towardMiddle(String stairs, int dx, int dz) {
        String facing;
        String shape = "straight";
        if (Math.abs(dx) == Math.abs(dz) && dx != 0) {
            facing = dz > 0 ? "north" : "south";
            shape = (dx > 0) == (dz > 0) ? "outer_left" : "outer_right";
        } else if (Math.abs(dx) > Math.abs(dz)) {
            facing = dx > 0 ? "west" : "east";
        } else {
            facing = dz > 0 ? "north" : "south";
        }
        return Kit.stairs(stairs, facing, shape);
    }

    /**
     * A pointed roof over a round or square tower: its first layer is the eaves, one block wider than the tower all round;
     * above them the roof narrows evenly to its point. Where a layer reaches further than the one above it, its edge is a
     * stair turned inwards, so that the slope is smooth; it is a shell two blocks thick, hollow inside, with a slab on the
     * point.
     *
     * @param radius the half width of the tower under it
     * @param height the layers from the eaves to the point
     * @return the layer above the point
     */
    private static int spire(Structure s, boolean round, int cx, int y, int cz, int radius, int height, Kit.Roof roof) {
        for (int i = 0; i < height; i++) {
            double r = i == 0 ? radius + 1 : (double) radius * (height - i) / (height - 1.0);
            double above = i + 1 >= height ? -1 : (double) radius * (height - i - 1) / (height - 1.0);
            int reach = (int) Math.ceil(r) + 1;
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    if (within(round, dx, dz, r) && !within(round, dx, dz, r - 2) && s.inside(cx + dx, y + i, cz + dz)) {
                        boolean covered = within(round, dx, dz, above) || dx == 0 && dz == 0;
                        s.set(cx + dx, y + i, cz + dz, covered ? roof.block() : towardMiddle(roof.stairs(), dx, dz));
                    }
                }
            }
        }
        s.set(cx, y + height, cz, Kit.slab(roof.slab()));
        return y + height + 1;
    }

    /** A lantern hanging at {@code lanternY} on a chain that rises to the first block above it. */
    private static void hang(Structure s, int x, int z, int lanternY) {
        s.set(x, lanternY, z, Kit.hangingLantern());
        for (int y = lanternY + 1; y < s.sizeY && !s.solid(x, y, z); y++) {
            s.set(x, y, z, Kit.chain());
        }
    }

    /**
     * Fills the inside of a round tower (the plan without its rim) at the given layers.
     */
    private static void inside(Structure s, int cx, int y1, int y2, int cz, int diameter, String state) {
        int radius = (diameter - 1) / 2;
        for (int y = y1; y <= y2; y++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Kit.inDisc(dx, dz, diameter) && !Kit.onRim(dx, dz, diameter)) {
                        s.set(cx + dx, y, cz + dz, state);
                    }
                }
            }
        }
    }

    /** A lantern hanging on a chain from {@code top} down to {@code lanternY}. */
    private static void chandelier(Structure s, int x, int z, int lanternY, int top) {
        s.fill(x, lanternY + 1, z, x, top, z, Kit.chain());
        s.set(x, lanternY, z, Kit.hangingLantern());
    }

    private static String pane(String color) {
        return Kit.stainedPane(color);
    }

    // ---------------------------------------------------------------- the castle

    /**
     * The castle of a small kingdom. A curtain wall two blocks thick, with machicolated battlements, banners and a walk
     * widened inside on corbels, joins four round corner towers under slender cones of slate, the two at the back higher
     * than those at the front; the walk passes through the towers and over the gate, stone stairs lead up to it from the
     * courtyard. The gate in the south wall runs deep between two slender round towers crowned with battlements and flags,
     * under a raised portcullis and a balcony hung with the banners of the kingdom. The courtyard holds a roofed well, a
     * flower bed, gardens with trees and lamp posts along a paved road to the keep. The keep is a tall hall with buttresses,
     * lancet windows and a gabled front with a rose window over its arched porch between two battlemented turrets; inside,
     * two arcades of columns, a red carpet, banners and a chandelier lead to the throne on its dais. Behind the throne rises
     * the donjon: a treasury behind iron bars on its ground floor, a council room, a library and the royal chamber above it,
     * all joined by a spiral stair, and an open lookout under a round spire with dormers, four bartizans at its corners and a
     * great flag on its point. The corner towers hold a chapel, a kitchen, an armoury and a store.
     */
    static Structure castleSmall() {
        Structure s = new Structure(SIZE, HEIGHT, SIZE);
        ground(s);
        curtain(s);
        for (int[] tower : new int[][]{{4, 4, 15, 13}, {26, 4, 15, 13}, {4, 26, 12, 12}, {26, 26, 12, 12}}) {
            cornerTower(s, tower[0], tower[1], tower[2], tower[3]);
        }
        gatehouse(s);
        hall(s);
        donjon(s);
        throneHall(s);
        donjonRooms(s);
        towerRooms(s);
        courtyard(s);
        // Grass only where nothing stands on it.
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                if (s.get(x, 0, z).equals(GRASS) && s.solid(x, 1, z) && States.isFull(s.get(x, 1, z))) {
                    s.set(x, 0, z, Kit.ROUGH);
                }
            }
        }
        s.skip(MID, 0, MID).skip(MID, 1, MID);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The ground: grass outside the walls, rough paving inside them and a road of trim from the street through the gate to
     * the door of the keep.
     */
    private static void ground(Structure s) {
        s.fill(0, 0, 0, SIZE - 1, 0, SIZE - 1, GRASS);
        s.fill(2, 0, 2, SIZE - 3, 0, SIZE - 3, Kit.ROUGH);
        s.fill(PASS_X1, 0, HALL_Z2 + 1, PASS_X2, 0, SIZE - 1, Kit.TRIM);
        s.fill(PASS_X1 - 1, 0, SIZE - 2, PASS_X2 + 1, 0, SIZE - 1, Kit.ROUGH);
        s.fill(PASS_X1, 0, SIZE - 2, PASS_X2, 0, SIZE - 1, Kit.TRIM);
    }

    /**
     * The curtain wall: two blocks thick on a plinth and a talus of rough stone, blind arrow slits, and at the top a parapet
     * standing out over the outer face on a course of trim and brackets (machicolations), with merlons capped with trim and
     * lanterns in the crenels; the walk behind it is widened inside on a row of corbels.
     */
    private static void curtain(Structure s) {
        int last = SIZE - 3;
        s.fill(2, 1, 2, 3, WALK, last, Kit.WALL).fill(last - 1, 1, 2, last, WALK, last, Kit.WALL);
        s.fill(2, 1, 2, last, WALK, 3, Kit.WALL).fill(2, 1, last - 1, last, WALK, last, Kit.WALL);
        // Plinth and talus on the outer faces, a course of trim at the top.
        s.fill(2, 1, 2, 2, 2, last, Kit.ROUGH).fill(last, 1, 2, last, 2, last, Kit.ROUGH);
        s.fill(2, 1, 2, last, 2, 2, Kit.ROUGH).fill(2, 1, last, last, 2, last, Kit.ROUGH);
        s.fill(1, 1, 2, 1, 1, last, Kit.stairs(Kit.ROUGH_STAIRS, "east"));
        s.fill(last + 1, 1, 2, last + 1, 1, last, Kit.stairs(Kit.ROUGH_STAIRS, "west"));
        s.fill(2, 1, 1, last, 1, 1, Kit.stairs(Kit.ROUGH_STAIRS, "south"));
        s.fill(2, WALK, 2, 2, WALK, last, Kit.TRIM).fill(last, WALK, 2, last, WALK, last, Kit.TRIM);
        s.fill(2, WALK, 2, last, WALK, 2, Kit.TRIM).fill(2, WALK, last, last, WALK, last, Kit.TRIM);
        // Blind arrow slits in the outer faces.
        for (int i : new int[]{11, MID, 19}) {
            s.fill(2, 3, i, 2, 4, i, AIR).fill(last, 3, i, last, 4, i, AIR);
        }
        for (int i : new int[]{8, 22}) {
            s.fill(i, 3, 2, i, 4, 2, AIR);
        }
        // The machicolated battlements between the towers.
        for (int i = 6; i <= 24; i++) {
            boolean merlon = i % 2 == 0;
            machicolation(s, 1, i, "east", merlon);
            machicolation(s, last + 1, i, "west", merlon);
            machicolation(s, i, 1, "south", merlon);
        }
        for (int x : new int[]{8, 22}) {
            parapet(s, x, last, true);
        }
        for (int i : new int[]{9, 13, 17, 21}) {
            s.set(1, WALK + 2, i, Kit.lantern()).set(last + 1, WALK + 2, i, Kit.lantern()).set(i, WALK + 2, 1, Kit.lantern());
        }
        // Banners of the kingdom hanging under the battlements of the sides and the back.
        for (int i : new int[]{13, 17}) {
            s.set(1, WALK - 2, i, Kit.wallBanner("red", "west")).set(last + 1, WALK - 2, i, Kit.wallBanner("red", "east"));
            s.set(i, WALK - 2, 1, Kit.wallBanner("red", "north"));
        }
        // The walk widened on corbels along the inner face.
        for (int i = 8; i <= 22; i++) {
            corbel(s, 4, i, "west");
            corbel(s, SIZE - 5, i, "east");
            if (i < DON_X1 || i > DON_X2) {
                corbel(s, i, 4, "north");
            }
        }
        // Two flights of stone stairs up to the walk along the inner faces of the side walls.
        for (int x : new int[]{4, SIZE - 5}) {
            for (int k = 0; k < WALK; k++) {
                int z = 21 - k;
                s.fill(x, 1, z, x, k + 1, z, Kit.WALL);
                s.set(x, k + 1, z, Kit.stairs(Kit.WALL_STAIRS, "north"));
                if (k + 2 <= WALK) {
                    s.fill(x, k + 2, z, x, WALK, z, AIR);
                }
            }
            s.set(x, 1, 22, Kit.slab(Kit.TRIM_SLAB));
        }
    }

    /** A block of the parapet on the outer edge of the wall, with a merlon capped with a slab of trim, or a crenel. */
    private static void parapet(Structure s, int x, int z, boolean merlon) {
        s.set(x, WALK + 1, z, Kit.WALL);
        if (merlon) {
            s.set(x, WALK + 2, z, Kit.WALL).set(x, WALK + 3, z, Kit.slab(Kit.TRIM_SLAB));
        }
    }

    /**
     * A block of the parapet standing out one block beyond the outer face of the wall: on a course of trim and, under every
     * merlon, a bracket; a merlon capped with a slab of trim, or a crenel.
     *
     * @param wall the side of the wall it stands out from
     */
    private static void machicolation(Structure s, int x, int z, String wall, boolean merlon) {
        s.set(x, WALK, z, Kit.upside(Kit.TRIM_STAIRS, wall));
        if (merlon) {
            s.set(x, WALK - 1, z, Kit.upside(Kit.WALL_STAIRS, wall));
        }
        parapet(s, x, z, merlon);
    }

    /** A slab of the walk standing out over the courtyard, on a bracket at every second block. */
    private static void corbel(Structure s, int x, int z, String wall) {
        s.set(x, WALK, z, Kit.topSlab(Kit.TRIM_SLAB));
        if ((x + z) % 2 == 0) {
            s.set(x, WALK - 1, z, Kit.upside(Kit.WALL_STAIRS, wall));
        }
    }

    /**
     * A round corner tower of seven: a batter of rough stone round its outer side, a room on the ground with a door to the
     * courtyard, a room at the height of the walk with doors to the walk on both sides, arrow slits, small windows and
     * banners of the kingdom under a corbelled cornice and a slender cone of slate over it.
     *
     * @param top  the last layer of its wall
     * @param cone the layers of its cone from the eaves to the point
     */
    private static void cornerTower(Structure s, int cx, int cz, int top, int cone) {
        int d = 7;
        int sx = Integer.signum(cx - MID);
        int sz = Integer.signum(cz - MID);
        // The batter on the outer side only; the side to the courtyard, with the door, stands straight.
        ring(s, ROUND, cx, 1, cz, d, Kit.ROUGH_STAIRS, Kit.ROUGH, false);
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                if (dx * sx < 0 && dz * sz < 0 && !Kit.inDisc(dx, dz, d)) {
                    s.set(cx + dx, 1, cz + dz, AIR);
                }
            }
        }
        Kit.tube(s, cx, 1, top, cz, d, Kit.WALL);
        Kit.tube(s, cx, 1, 2, cz, d, Kit.ROUGH);
        Kit.tube(s, cx, WALK, WALK, cz, d, Kit.TRIM);
        inside(s, cx, 1, top, cz, d, AIR);
        inside(s, cx, 0, 0, cz, d, PLANKS);
        inside(s, cx, WALK, WALK, cz, d, PLANKS);
        // Arrow slits on the outer faces, small windows under the cornice all round.
        for (int[] face : new int[][]{{sx * 3, 0}, {0, sz * 3}, {sx * 2, sz * 2}}) {
            s.fill(cx + face[0], 3, cz + face[1], cx + face[0], 4, cz + face[1], Kit.GLASS);
            s.fill(cx + face[0], WALK + 2, cz + face[1], cx + face[0], WALK + 3, cz + face[1], AIR);
        }
        s.set(cx - sx * 3, top - 1, cz, Kit.GLASS).set(cx, top - 1, cz - sz * 3, Kit.GLASS);
        // Banners of the kingdom on the outer faces, under the cornice.
        s.set(cx + sx * 4, top - 1, cz, Kit.wallBanner("red", sx < 0 ? "west" : "east"));
        s.set(cx, top - 1, cz + sz * 4, Kit.wallBanner("red", sz < 0 ? "north" : "south"));
        // The cornice on corbels and the cone.
        Kit.tube(s, cx, top, top, cz, d, Kit.TRIM);
        ring(s, ROUND, cx, top, cz, d, Kit.WALL_STAIRS, Kit.WALL, true);
        spire(s, true, cx, top + 1, cz, 3, cone, Kit.SLATE);
        hang(s, cx, cz, WALK + 4);
        // The door to the courtyard.
        s.door(cx - sx * 2, 1, cz - sz * 2, DOOR, "south", "left");
        s.set(cx - sx * 2, 3, cz - sz * 2, Kit.WALL);
    }

    /**
     * The gate: a block over a passage three wide and four high under arches, with a raised portcullis, a guard room above
     * it through which the walk passes, a balcony on corbels in front of it and battlements on top, between two slender
     * round towers with battlements and flags; the banners of the kingdom hang on both sides of the balcony door.
     */
    private static void gatehouse(Structure s) {
        int front = GATE_Z + 1;
        s.fill(12, 1, 25, 18, 12, front, Kit.WALL);
        s.walls(12, 1, 25, 18, 1, front, Kit.ROUGH);
        gateTower(s, 11);
        gateTower(s, 19);
        // The passage and its arches.
        s.fill(PASS_X1, 1, 25, PASS_X2, 4, front, AIR);
        for (int z : new int[]{25, front}) {
            s.set(PASS_X1, 4, z, Kit.upside(Kit.WALL_STAIRS, "west")).set(PASS_X2, 4, z, Kit.upside(Kit.WALL_STAIRS, "east"));
            s.set(MID, 5, z, Kit.CARVED);
            s.set(PASS_X1 - 1, 5, z, Kit.TRIM).set(PASS_X2 + 1, 5, z, Kit.TRIM);
            s.set(PASS_X1, 5, z, Kit.TRIM).set(PASS_X2, 5, z, Kit.TRIM);
        }
        s.fill(PASS_X1, 4, GATE_Z, PASS_X2, 4, GATE_Z, Kit.BARS);
        s.set(PASS_X1, 3, GATE_Z, Kit.wallTorch("east")).set(PASS_X2, 3, GATE_Z, Kit.wallTorch("west"));
        // The guard room over the passage; the walk enters it from the towers.
        s.fill(PASS_X1, WALK, 26, PASS_X2, WALK, GATE_Z, PLANKS);
        s.fill(PASS_X1, WALK + 1, 26, PASS_X2, WALK + 4, GATE_Z, AIR);
        s.door(13, WALK + 1, GATE_Z, DOOR, "east", "left").door(17, WALK + 1, GATE_Z, DOOR, "west", "left");
        s.door(MID, WALK + 1, front, DOOR, "south", "left");
        s.set(MID, WALK + 1, 25, Kit.GLASS).set(MID, WALK + 2, 25, Kit.GLASS);
        s.set(MID, WALK + 4, 26, Kit.hangingLantern());
        s.set(PASS_X1, WALK + 1, 26, Kit.barrel()).set(PASS_X2, WALK + 1, 26, Kit.chest("south"));
        // The balcony in front of the guard room, on corbels over the gate.
        s.fill(13, WALK, front + 1, 17, WALK, front + 1, Kit.topSlab(Kit.TRIM_SLAB));
        s.fill(13, WALK - 1, front + 1, 17, WALK - 1, front + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.fill(13, WALK + 1, front + 2, 17, WALK + 1, front + 2, Kit.WALL_FENCE);
        s.set(13, WALK + 2, front + 2, Kit.lantern()).set(17, WALK + 2, front + 2, Kit.lantern());
        // Banners of the kingdom on both sides of the balcony door, the arms of the castle over it.
        s.set(PASS_X1, 11, front + 1, Kit.wallBanner("red", "south")).set(PASS_X2, 11, front + 1, Kit.wallBanner("red", "south"));
        s.set(MID, WALK + 3, front, Kit.TRIM).set(MID, WALK + 4, front, Kit.CARVED).set(MID, WALK + 5, front, Kit.TRIM);
        // Battlements on top.
        s.fill(12, 12, 25, 18, 12, front, Kit.TRIM);
        s.walls(12, 13, 25, 18, 13, front, Kit.WALL);
        for (int x = 12; x <= 18; x++) {
            for (int z = 25; z <= front; z++) {
                boolean edge = x == 12 || x == 18 || z == 25 || z == front;
                if (edge && (x + z) % 2 == 1) {
                    s.set(x, 14, z, Kit.WALL);
                }
            }
        }
    }

    /**
     * A slender round tower of five beside the gate: solid up to the walk, a room at the walk with doors to the wall and to
     * the guard room, arrow slits, a banner on its front, and on top a platform behind machicolated battlements with
     * lanterns and a flag of the kingdom on a pole.
     */
    private static void gateTower(Structure s, int cx) {
        int cz = GATE_Z;
        int d = 5;
        int top = 14;
        int side = Integer.signum(cx - MID);
        for (int y = 1; y < WALK; y++) {
            Kit.disc(s, cx, y, cz, d, Kit.WALL);
        }
        Kit.tube(s, cx, 1, top, cz, d, Kit.WALL);
        Kit.tube(s, cx, 1, 2, cz, d, Kit.ROUGH);
        Kit.tube(s, cx, WALK, WALK, cz, d, Kit.TRIM);
        Kit.tube(s, cx, 11, 11, cz, d, Kit.TRIM);
        inside(s, cx, WALK + 1, top - 1, cz, d, AIR);
        inside(s, cx, WALK, WALK, cz, d, PLANKS);
        for (int y : new int[]{WALK + 2, WALK + 3}) {
            s.set(cx, y, cz + 2, AIR).set(cx, y, cz - 2, AIR);
        }
        s.set(cx + side * 2, 12, cz, AIR).set(cx, 12, cz - 2, Kit.GLASS);
        s.set(cx, 13, cz + 3, Kit.wallBanner("red", "south"));
        s.set(cx, top - 1, cz, Kit.hangingLantern());
        // The battlements on corbels, lanterns on the parapet and the flag.
        Kit.disc(s, cx, top, cz, d, Kit.TRIM);
        ring(s, ROUND, cx, top, cz, d, Kit.WALL_STAIRS, Kit.WALL, true);
        Kit.roundBattlements(s, cx, top + 1, cz, d + 2, Kit.WALL);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (s.get(cx + dx, top + 2, cz + dz).equals(Kit.WALL)) {
                    s.set(cx + dx, top + 3, cz + dz, Kit.slab(Kit.TRIM_SLAB));
                }
            }
        }
        s.set(cx + side * 3, top + 2, cz, Kit.lantern()).set(cx, top + 2, cz + 3, Kit.lantern());
        s.fill(cx, top + 1, cz, cx, top + 5, cz, FENCE);
        s.fill(cx + side, top + 4, cz, cx + side * 2, top + 5, cz, Kit.CLOTH);
        s.set(cx, top + 6, cz, Kit.lantern());
        s.set(cx - side, top + 1, cz, Kit.lantern());
        // The doorway from the wall walk.
        s.door(cx + side * 2, WALK + 1, GATE_Z, DOOR, "east", "left");
        s.fill(cx + side * 3, WALK + 1, GATE_Z, cx + side * 3, WALK + 2, GATE_Z, AIR);
    }

    /**
     * The top of a small turret of three: a parapet round a lantern, merlons capped with trim at its corners.
     */
    private static void turretTop(Structure s, int x, int y, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    s.set(x, y, z, Kit.lantern());
                } else {
                    s.set(x + dx, y, z + dz, Kit.WALL);
                    if (dx != 0 && dz != 0) {
                        s.set(x + dx, y + 1, z + dz, Kit.WALL).set(x + dx, y + 2, z + dz, Kit.slab(Kit.TRIM_SLAB));
                    }
                }
            }
        }
    }

    /**
     * The shell of the hall of the keep: walls on a plinth with quoins and buttresses, tall lancet windows of stained glass,
     * a cornice of trim, a steep gable roof of tiles whose front gable holds a rose window over the arched door in its porch,
     * and two slender battlemented turrets standing out from the front corners.
     */
    private static void hall(Structure s) {
        s.walls(HALL_X1, 1, HALL_Z1, HALL_X2, HALL_TOP, HALL_Z2, Kit.WALL);
        s.walls(HALL_X1, 1, HALL_Z1, HALL_X2, 1, HALL_Z2, Kit.ROUGH);
        for (int x : new int[]{HALL_X1, HALL_X2}) {
            for (int z : new int[]{HALL_Z1, HALL_Z2}) {
                s.fill(x, 1, z, x, HALL_TOP, z, Kit.ROUGH);
            }
        }
        // Buttresses in the middle of the sides, windows on both sides of them.
        for (int x : new int[]{HALL_X1 - 1, HALL_X2 + 1}) {
            String toWall = x < MID ? "east" : "west";
            s.fill(x, 1, 16, x, 6, 16, Kit.ROUGH);
            s.set(x, 7, 16, Kit.stairs(Kit.ROUGH_STAIRS, toWall));
            for (int z : new int[]{14, 18}) {
                s.set(x, 2, z, Kit.upside(Kit.TRIM_STAIRS, toWall));
            }
        }
        for (int x : new int[]{HALL_X1, HALL_X2}) {
            for (int z : new int[]{14, 18}) {
                s.fill(x, 3, z, x, 7, z, pane("light_blue"));
                s.set(x, 4, z, pane("yellow")).set(x, 6, z, pane("red"));
                s.set(x, 8, z, Kit.CARVED);
            }
        }
        // The cornice and the roof.
        for (int z = HALL_Z1; z <= HALL_Z2; z++) {
            s.set(HALL_X1 - 1, HALL_TOP, z, Kit.upside(Kit.TRIM_STAIRS, "east"));
            s.set(HALL_X2 + 1, HALL_TOP, z, Kit.upside(Kit.TRIM_STAIRS, "west"));
        }
        s.fill(HALL_X1 + 1, HALL_TOP, HALL_Z2 + 1, HALL_X2 - 1, HALL_TOP, HALL_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.gableRoofZ(HALL_X1 - 1, HALL_X2 + 1, HALL_Z1 - 1, HALL_Z2 + 1, HALL_TOP + 1, Kit.TILE.stairs(), Kit.TILE.slab(), Kit.WALL,
                HALL_Z1, HALL_Z2);
        // The rose window in the front gable: a round of stained glass in a frame of trim, a course of trim under it.
        int rose = 11;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (Kit.inDisc(dx, dy, 5)) {
                    boolean edge = Kit.onRim(dx, dy, 5);
                    s.set(MID + dx, rose + dy, HALL_Z2, dx == 0 && dy == 0 ? pane("red") : edge ? pane("blue") : pane("yellow"));
                } else if (Kit.inDisc(dx, dy, 7)) {
                    s.set(MID + dx, rose + dy, HALL_Z2, Kit.TRIM);
                }
            }
        }
        s.fill(HALL_X1 + 1, 7, HALL_Z2, HALL_X2 - 1, 7, HALL_Z2, Kit.TRIM);
        s.set(MID, 16, HALL_Z2, Kit.GLASS);
        // The door: an arch three wide in a porch of trim with a small gable over it.
        s.fill(PASS_X1, 1, HALL_Z2, PASS_X2, 3, HALL_Z2, AIR);
        s.set(MID, 4, HALL_Z2, AIR);
        s.set(PASS_X1, 4, HALL_Z2, Kit.upside(Kit.WALL_STAIRS, "west")).set(PASS_X2, 4, HALL_Z2, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(MID, 5, HALL_Z2, Kit.CARVED);
        int porch = HALL_Z2 + 1;
        for (int x : new int[]{PASS_X1 - 1, PASS_X2 + 1}) {
            s.fill(x, 1, porch, x, 4, porch, Kit.TRIM);
            s.set(x, 1, porch, Kit.ROUGH);
        }
        s.set(PASS_X1 - 1, 5, porch, Kit.stairs(Kit.TRIM_STAIRS, "east")).set(PASS_X2 + 1, 5, porch, Kit.stairs(Kit.TRIM_STAIRS, "west"));
        s.fill(PASS_X1, 5, porch, PASS_X2, 5, porch, Kit.TRIM);
        s.set(PASS_X1, 6, porch, Kit.stairs(Kit.TRIM_STAIRS, "east")).set(PASS_X2, 6, porch, Kit.stairs(Kit.TRIM_STAIRS, "west"));
        s.set(MID, 6, porch, Kit.TRIM).set(MID, 7, porch, Kit.slab(Kit.TRIM_SLAB));
        s.set(PASS_X1 - 2, 3, porch, Kit.wallBanner("red", "south")).set(PASS_X2 + 2, 3, porch, Kit.wallBanner("red", "south"));
        s.set(PASS_X1 - 1, 5, porch + 1, AIR);
        // Slender turrets standing out from the front corners, crowned with battlements and lanterns.
        for (int x : new int[]{HALL_X1 - 1, HALL_X2 + 1}) {
            int z = HALL_Z2 + 1;
            for (int y = 1; y <= 13; y++) {
                Kit.disc(s, x, y, z, 3, y <= 2 ? Kit.ROUGH : y == 7 || y == HALL_TOP ? Kit.TRIM : Kit.WALL);
            }
            ring(s, SQUARE, x, 13, z, 1, Kit.TRIM_STAIRS, Kit.TRIM, true);
            turretTop(s, x, 14, z);
            s.set(x, 9, z + 2, Kit.wallBanner("red", "south"));
        }
    }

    /**
     * The donjon: a square tower against the north wall, on a plinth of rough stone with quoins and courses of trim, windows
     * at every floor, an open lookout with arches under a corbelled cornice, a round spire of slate with a dormer on every
     * side and a flag of the kingdom on its point, and four battlemented bartizans on corbels at its corners.
     */
    private static void donjon(Structure s) {
        s.walls(DON_X1, 1, DON_Z1, DON_X2, DON_TOP, DON_Z2, Kit.WALL);
        s.fill(DON_X1 + 1, 1, DON_Z1 + 1, DON_X2 - 1, DON_TOP, DON_Z2 - 1, AIR);
        s.walls(DON_X1, 1, DON_Z1, DON_X2, 2, DON_Z2, Kit.ROUGH);
        for (int x : new int[]{DON_X1, DON_X2}) {
            for (int z : new int[]{DON_Z1, DON_Z2}) {
                s.fill(x, 1, z, x, DON_TOP, z, Kit.ROUGH);
            }
        }
        for (int y : new int[]{9, DON_FLOORS[3]}) {
            s.walls(DON_X1, y, DON_Z1, DON_X2, y, DON_Z2, Kit.TRIM);
        }
        // Windows of the rooms: two on every free face of every floor.
        for (int floor : new int[]{DON_FLOORS[0], DON_FLOORS[1], DON_FLOORS[2]}) {
            for (int y = floor + 2; y <= floor + 3; y++) {
                s.set(13, y, DON_Z1, Kit.GLASS).set(17, y, DON_Z1, Kit.GLASS);
                for (int z : new int[]{7, 9}) {
                    s.set(DON_X1, y, z, Kit.GLASS).set(DON_X2, y, z, Kit.GLASS);
                }
            }
            s.set(13, floor + 1, DON_Z1 - 0, Kit.TRIM).set(17, floor + 1, DON_Z1, Kit.TRIM);
        }
        // Barred slits of the treasury.
        s.set(DON_X1, 2, 7, Kit.BARS).set(DON_X1, 2, 9, Kit.BARS);
        // The lookout: three arches on every face over a balustrade.
        int look = DON_FLOORS[3] + 1;
        for (int i : new int[]{13, MID, 17}) {
            for (int[] cell : new int[][]{{i, DON_Z1}, {i, DON_Z2}, {DON_X1, i - 7}, {DON_X2, i - 7}}) {
                s.set(cell[0], look, cell[1], Kit.WALL_FENCE);
                s.fill(cell[0], look + 1, cell[1], cell[0], look + 2, cell[1], AIR);
            }
        }
        // The cornice on corbels and the spire.
        s.walls(DON_X1, DON_TOP, DON_Z1, DON_X2, DON_TOP, DON_Z2, Kit.TRIM);
        ring(s, SQUARE, MID, DON_TOP, 8, 9, Kit.TRIM_STAIRS, Kit.TRIM, true);
        int point = spire(s, true, MID, DON_TOP + 1, 8, 4, 12, Kit.SLATE);
        hang(s, MID, 8, look + 2);
        // A dormer in the middle of every face of the spire.
        for (int[] face : new int[][]{{0, 1, 1, 0}, {0, -1, 1, 0}, {1, 0, 0, 1}, {-1, 0, 0, 1}}) {
            dormer(s, face[0], face[1], face[2], face[3]);
        }
        // Bartizans: small turrets standing out from the four corners on corbels, with battlements and lanterns.
        for (int x : new int[]{DON_X1 - 1, DON_X2 + 1}) {
            for (int z : new int[]{DON_Z1 - 1, DON_Z2 + 1}) {
                ring(s, SQUARE, x, DON_TOP - 4, z, 1, Kit.WALL_STAIRS, Kit.WALL, true);
                s.fill(x - 1, DON_TOP - 3, z - 1, x + 1, DON_TOP + 1, z + 1, Kit.WALL);
                s.fill(x - 1, DON_TOP - 1, z - 1, x + 1, DON_TOP - 1, z + 1, Kit.TRIM);
                s.set(x, DON_TOP, z + (z < 8 ? -1 : 1), Kit.BARS).set(x + (x < MID ? -1 : 1), DON_TOP, z, Kit.BARS);
                ring(s, SQUARE, x, DON_TOP + 1, z, 1, Kit.TRIM_STAIRS, Kit.TRIM, true);
                turretTop(s, x, DON_TOP + 2, z);
            }
        }
        // The flag: a pole on the point and a flag of the colour of the kingdom.
        s.fill(MID, point, 8, MID, point + 3, 8, FENCE);
        s.fill(MID + 1, point + 2, 8, MID + 3, point + 3, 8, Kit.CLOTH);
        s.set(MID, point + 4, 8, GOLD);
    }

    /**
     * A dormer on a face of the spire of the donjon: a small front of stone with a window two high, its cheeks and a little
     * gable roof of slate standing out of the slope.
     *
     * @param ox the way the face looks along x
     * @param oz the way the face looks along z
     * @param ax the way along the face along x
     * @param az the way along the face along z
     */
    private static void dormer(Structure s, int ox, int oz, int ax, int az) {
        int y = DON_TOP + 2;
        for (int u = -1; u <= 1; u++) {
            for (int v = 3; v <= 5; v++) {
                int x = MID + ax * u + ox * v;
                int z = 8 + az * u + oz * v;
                if (v <= 4) {
                    for (int dy = 0; dy <= 1; dy++) {
                        s.set(x, y + dy, z, u != 0 ? Kit.WALL : v == 4 ? Kit.GLASS : AIR);
                    }
                }
                String toRidge = u < 0 ? side(ax, az) : side(-ax, -az);
                s.set(x, y + 2, z, u == 0 ? Kit.SLATE.block() : Kit.stairs(Kit.SLATE.stairs(), toRidge));
                if (u == 0) {
                    s.set(x, y + 3, z, Kit.slab(Kit.SLATE.slab()));
                }
            }
        }
    }

    /** The name of a horizontal direction. */
    private static String side(int dx, int dz) {
        return dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
    }

    /**
     * The throne hall, open to its roof: a floor of trim, two arcades of columns with carved shafts and capitals of trim
     * carrying lanterns and banners, the throne on a dais of trim with a carved floor before a wall hung with the cloth and
     * banners of the kingdom and the gold of the crown under a canopy, a red carpet from the dais to the door, banners and
     * torches on the side walls and a great chandelier under the ridge. The doors on both sides of the dais lead to the
     * treasury and to the stair of the donjon.
     */
    private static void throneHall(Structure s) {
        s.fill(HALL_X1 + 1, 0, HALL_Z1 + 1, HALL_X2 - 1, 0, HALL_Z2 - 1, Kit.TRIM);
        s.fill(MID - 2, 0, HALL_Z1 + 1, MID + 2, 0, MID + 1, Kit.CARVED);
        // Two arcades along the nave: columns with carved shafts, bases and capitals of trim, pointed arches between them
        // and a beam of stone over them with lanterns on the capitals and banners hanging into the nave.
        for (int x : new int[]{12, 18}) {
            s.fill(x, 7, HALL_Z1 + 1, x, 7, HALL_Z2 - 1, Kit.WALL);
            for (int z : new int[]{14, 18}) {
                s.set(x, 1, z, Kit.TRIM);
                s.fill(x, 2, z, x, 6, z, Kit.CARVED);
                s.set(x, 7, z, Kit.TRIM);
                s.set(x - 1, 7, z, Kit.upside(Kit.TRIM_STAIRS, "east")).set(x + 1, 7, z, Kit.upside(Kit.TRIM_STAIRS, "west"));
                s.set(x, 8, z, Kit.lantern());
            }
            s.set(x, 6, 15, Kit.upside(Kit.WALL_STAIRS, "north")).set(x, 6, 17, Kit.upside(Kit.WALL_STAIRS, "south"));
            s.set(x, 6, HALL_Z1 + 1, Kit.upside(Kit.WALL_STAIRS, "north")).set(x, 6, HALL_Z2 - 1, Kit.upside(Kit.WALL_STAIRS, "south"));
            s.set(x + (x < MID ? 1 : -1), 7, 16, Kit.wallBanner("red", x < MID ? "east" : "west"));
        }
        // The wall behind the throne.
        s.fill(MID - 1, 2, HALL_Z1, MID - 1, 8, HALL_Z1, Kit.CLOTH).fill(MID + 1, 2, HALL_Z1, MID + 1, 8, HALL_Z1, Kit.CLOTH);
        s.fill(MID, 3, HALL_Z1, MID, 9, HALL_Z1, YELLOW_WOOL);
        s.set(MID, 10, HALL_Z1, GOLD);
        s.set(MID - 2, 6, HALL_Z1 + 1, Kit.wallBanner("red", "south")).set(MID + 2, 6, HALL_Z1 + 1, Kit.wallBanner("red", "south"));
        s.fill(MID - 1, 6, HALL_Z1 + 1, MID + 1, 6, HALL_Z1 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        // The dais: steps of trim around the throne.
        s.set(MID - 1, 1, MID, Kit.stairs(Kit.TRIM_STAIRS, "west")).set(MID + 1, 1, MID, Kit.stairs(Kit.TRIM_STAIRS, "east"));
        s.fill(MID - 1, 1, MID - 1, MID + 1, 1, MID - 1, Kit.slab(Kit.TRIM_SLAB));
        s.set(MID - 1, 1, MID + 1, Kit.stairs(Kit.TRIM_STAIRS, "north")).set(MID + 1, 1, MID + 1, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        s.fill(MID, 1, MID + 1, MID, 1, HALL_Z2 + 1, Kit.CLOTH_CARPET);
        // The doors to the treasury and to the donjon.
        s.door(12, 1, HALL_Z1, DOOR, "south", "left").door(18, 1, HALL_Z1, DOOR, "south", "right");
        s.set(12, 3, HALL_Z1, Kit.CARVED).set(18, 3, HALL_Z1, Kit.CARVED);
        // Banners on the side walls and torches by the windows.
        for (int z : new int[]{16}) {
            s.set(HALL_X1 + 1, 5, z, Kit.wallBanner("red", "east")).set(HALL_X2 - 1, 5, z, Kit.wallBanner("red", "west"));
        }
        s.set(HALL_X1 + 1, 4, 20 - 1, Kit.wallTorch("east")).set(HALL_X2 - 1, 4, 20 - 1, Kit.wallTorch("west"));
        s.set(HALL_X1 + 1, 4, 13, Kit.wallTorch("east")).set(HALL_X2 - 1, 4, 13, Kit.wallTorch("west"));
        // The great chandelier: a cross of fences on a chain under the ridge, a lantern under each arm.
        int cz = 17;
        s.fill(MID, 9, cz, MID, HALL_TOP + 6, cz, Kit.chain());
        s.set(MID, 8, cz, FENCE).set(MID - 1, 8, cz, FENCE).set(MID + 1, 8, cz, FENCE).set(MID, 8, cz - 1, FENCE).set(MID, 8, cz + 1, FENCE);
        for (int[] arm : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            s.set(MID + arm[0], 7, cz + arm[1], Kit.hangingLantern());
        }
    }

    /**
     * The rooms of the donjon, joined by a spiral stair of stone around a newel in its north-east corner: the treasury behind
     * iron bars on the ground floor, the council room, the library, the royal chamber with a corner for prayer, and the
     * lookout under the spire.
     */
    private static void donjonRooms(Structure s) {
        int x1 = DON_X1 + 1;
        int x2 = DON_X2 - 1;
        int z1 = DON_Z1 + 1;
        int z2 = DON_Z2 - 1;
        s.fill(x1, 0, z1, x2, 0, z2, PLANKS);
        for (int floor : DON_FLOORS) {
            s.fill(x1, floor, z1, x2, floor, z2, PLANKS);
            s.fill(16, floor, 5, 18, floor, 7, AIR);
        }
        // The spiral stair.
        s.fill(NEWEL_X, 1, NEWEL_Z, NEWEL_X, DON_FLOORS[3], NEWEL_Z, Kit.WALL);
        int last = DON_FLOORS[3] - 1;
        for (int k = 0; k <= last; k++) {
            int[] here = SPIRAL[k % SPIRAL.length];
            int[] next = SPIRAL[(k + 1) % SPIRAL.length];
            String facing = next[0] > here[0] ? "east" : next[0] < here[0] ? "west" : next[1] > here[1] ? "south" : "north";
            s.set(here[0], 1 + k, here[1], Kit.stairs(Kit.WALL_STAIRS, facing));
        }
        // Railings around the well of the stair, open where it arrives.
        for (int floor : DON_FLOORS) {
            boolean west = (floor - 1) % 8 == 4;
            int y = floor + 1;
            s.fill(15, y, west ? 6 : 5, 15, y, 8, FENCE);
            s.fill(16, y, 8, west ? 18 : 17, y, 8, FENCE);
        }
        s.set(NEWEL_X, DON_FLOORS[3] + 1, NEWEL_Z, Kit.lantern());
        treasury(s);
        // The council room: a long table with chairs, bookshelves, banners and a lantern.
        int y = DON_FLOORS[0] + 1;
        s.fill(13, y, 8, 14, y, 10, Kit.topSlab(SPRUCE_SLAB));
        for (int z = 8; z <= 10; z++) {
            s.set(12, y, z, Kit.stairs(SPRUCE_STAIRS, "west")).set(15, y, z, Kit.stairs(SPRUCE_STAIRS, "east"));
        }
        s.set(13, y, 11, Kit.stairs(SPRUCE_STAIRS, "south"));
        s.set(13, y + 1, 9, Kit.lantern());
        s.fill(12, y, 5, 13, y + 1, 5, BOOKSHELF).fill(12, y, 6, 12, y + 1, 6, BOOKSHELF);
        s.set(16, y + 1, 11, Kit.wallBanner("red", "north")).set(12, y + 1, 11, Kit.wallBanner("red", "north"));
        s.set(18, y, 11, Kit.chest("west"));
        chandelier(s, 16, 10, y + 2, y + 2);
        // The library: shelves round the walls, a reading desk and a lectern.
        y = DON_FLOORS[1] + 1;
        s.fill(12, y, 5, 12, y + 2, 11, BOOKSHELF);
        s.fill(13, y, 11, 17, y + 2, 11, BOOKSHELF);
        s.fill(13, y, 5, 14, y + 2, 5, BOOKSHELF);
        s.set(12, y + 1, 7, Kit.GLASS).set(12, y + 1, 9, Kit.GLASS);
        s.set(12, y + 2, 7, Kit.GLASS).set(12, y + 2, 9, Kit.GLASS);
        s.set(14, y, 8, "minecraft:lectern[facing=east,has_book=false,powered=false]");
        s.set(17, y, 10, Kit.topSlab(SPRUCE_SLAB)).set(16, y, 10, Kit.stairs(SPRUCE_STAIRS, "west"));
        s.set(17, y + 1, 10, Kit.lantern());
        chandelier(s, 14, 9, y + 2, y + 2);
        // The royal chamber: a carpet, chests, a writing desk and a corner for prayer with an altar and candles.
        y = DON_FLOORS[2] + 1;
        s.fill(13, y, 8, 17, y, 10, Kit.CLOTH_CARPET);
        s.set(12, y, 11, Kit.chest("east")).set(12, y, 10, Kit.chest("east"));
        s.set(18, y, 11, Kit.topSlab(SPRUCE_SLAB)).set(18, y, 10, Kit.stairs(SPRUCE_STAIRS, "west"));
        s.set(18, y + 1, 11, Kit.lantern());
        s.set(12, y, 5, Kit.TRIM).set(13, y, 5, Kit.TRIM);
        s.set(12, y + 1, 5, "minecraft:candle[candles=3,lit=true,waterlogged=false]");
        s.set(13, y + 1, 5, "minecraft:candle[candles=2,lit=true,waterlogged=false]");
        s.set(13, y, 6, Kit.stairs(SPRUCE_STAIRS, "south"));
        s.set(13, y + 2, DON_Z1, pane("yellow")).set(13, y + 1, DON_Z1, pane("red"));
        chandelier(s, 14, 9, y + 2, y + 2);
        // The lookout.
        y = DON_FLOORS[3] + 1;
        s.set(12, y, 11, Kit.barrel()).set(12, y + 1, 11, Kit.lantern());
    }

    /**
     * The treasury on the ground floor of the donjon: a vault behind a grille of iron bars, entered by its own door from the
     * hall, holding chests, barrels and the gold of the kingdom.
     */
    private static void treasury(Structure s) {
        s.fill(15, 1, DON_Z1 + 1, 15, DON_FLOORS[0] - 1, DON_Z2 - 1, Kit.BARS);
        s.fill(12, 1, 5, 12, 1, 9, Kit.chest("east"));
        s.set(12, 2, 5, GOLD).set(13, 1, 5, Kit.barrel()).set(14, 1, 5, Kit.barrel()).set(14, 2, 5, Kit.barrel());
        s.set(14, 1, 7, GOLD).set(14, 1, 8, "minecraft:raw_gold_block");
        s.set(14, 2, 7, "minecraft:gold_block");
        s.set(13, 4, 8, Kit.hangingLantern());
        s.set(12, 1, 10, Kit.barrel());
        s.set(17, 4, 10, Kit.hangingLantern());
    }

    /**
     * The rooms on the ground floor of the corner towers: a chapel, a kitchen, an armoury and a store; and the rooms at the
     * walk, with a barrel and a lantern.
     */
    private static void towerRooms(Structure s) {
        // The chapel in the north-west tower: an altar with candles, benches and windows of stained glass.
        s.set(3, 1, 3, Kit.TRIM).set(4, 1, 2, Kit.TRIM).set(2, 1, 4, Kit.TRIM);
        s.set(3, 2, 3, "minecraft:candle[candles=4,lit=true,waterlogged=false]");
        s.set(4, 2, 2, "minecraft:candle[candles=1,lit=true,waterlogged=false]");
        s.set(2, 2, 4, "minecraft:candle[candles=1,lit=true,waterlogged=false]");
        s.set(4, 1, 4, Kit.stairs(SPRUCE_STAIRS, "south")).set(5, 1, 4, Kit.stairs(SPRUCE_STAIRS, "south"));
        s.set(3, 1, 5, Kit.stairs(SPRUCE_STAIRS, "south"));
        s.fill(1, 3, 4, 1, 4, 4, pane("blue")).fill(4, 3, 1, 4, 4, 1, pane("yellow")).fill(2, 3, 2, 2, 4, 2, pane("red"));
        s.set(4, 5, 4, Kit.hangingLantern());
        // The kitchen in the north-east tower: a hearth, smoker, barrels and a table.
        s.set(27, 1, 2, Kit.ROUGH).set(28, 1, 4, "minecraft:smoker[facing=west,lit=false]");
        s.set(27, 2, 2, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        s.set(26, 1, 2, "minecraft:furnace[facing=south,lit=false]");
        s.set(25, 1, 2, Kit.barrel()).set(24, 1, 3, Kit.barrel()).set(28, 1, 5, Kit.barrel());
        s.set(26, 1, 4, Kit.topSlab(SPRUCE_SLAB)).set(26, 2, 4, "minecraft:cake[bites=0]");
        s.set(26, 5, 4, Kit.hangingLantern());
        s.set(28, 1, 3, "minecraft:water_cauldron[level=3]");
        // The armoury in the south-west tower.
        s.set(2, 1, 26, "minecraft:smithing_table").set(3, 1, 28, "minecraft:anvil[facing=east]");
        s.set(2, 1, 25, Kit.chest("east")).set(2, 1, 27, Kit.chest("east")).set(4, 1, 28, Kit.barrel());
        s.set(5, 1, 28, "minecraft:grindstone[face=floor,facing=north]");
        s.set(4, 5, 26, Kit.hangingLantern());
        // The store in the south-east tower.
        s.set(28, 1, 26, Kit.barrel()).set(28, 1, 25, Kit.barrel()).set(28, 2, 26, Kit.barrel()).set(28, 1, 27, HAY);
        s.set(27, 1, 28, HAY).set(26, 1, 28, HAY).set(27, 2, 28, HAY).set(25, 1, 28, Kit.chest("north"));
        s.set(26, 5, 26, Kit.hangingLantern());
        // The rooms at the walk.
        for (int[] tower : new int[][]{{4, 4}, {26, 4}, {4, 26}, {26, 26}}) {
            int sx = Integer.signum(tower[0] - MID);
            int sz = Integer.signum(tower[1] - MID);
            s.set(tower[0] - sx * 2, WALK + 1, tower[1] - sz, Kit.barrel());
        }
        // Doors from the walk into the towers.
        s.door(4, WALK + 1, 7, DOOR, "south", "left").door(7, WALK + 1, 4, DOOR, "east", "left");
        s.door(26, WALK + 1, 7, DOOR, "south", "left").door(23, WALK + 1, 4, DOOR, "west", "left");
        s.door(4, WALK + 1, 23, DOOR, "north", "left").door(7, WALK + 1, GATE_Z, DOOR, "east", "left");
        s.door(26, WALK + 1, 23, DOOR, "north", "left").door(23, WALK + 1, GATE_Z, DOOR, "west", "left");
    }

    /**
     * The courtyard: a well under a small roof, a flower bed, gardens with flowering trees on both sides of the keep, lamp
     * posts by the road and a woodpile, barrels and a bench behind the keep.
     */
    private static void courtyard(Structure s) {
        // The well: a ring of rough stone round the water, posts and a little roof with a lantern.
        int wx = 11;
        int wz = 23;
        s.fill(wx - 1, 1, wz - 1, wx + 1, 1, wz + 1, Kit.ROUGH);
        s.set(wx, 1, wz, "minecraft:water[level=0]");
        s.set(wx, 0, wz, Kit.ROUGH);
        for (int[] post : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            s.fill(wx + post[0], 2, wz + post[1], wx + post[0], 3, wz + post[1], Kit.WALL_FENCE);
        }
        s.hipRoof(wx - 1, wx + 1, wz - 1, wz + 1, 4, Kit.TEAL.stairs(), Kit.TEAL.slab());
        s.set(wx, 3, wz, Kit.hangingLantern());
        s.set(wx, 4, wz, Kit.TEAL.block());
        // The flower bed: flowers round a flowering bush in a bed of earth sunk into the paving.
        int fx = 19;
        s.fill(fx - 1, 0, wz - 1, fx + 1, 0, wz + 1, GRASS);
        s.set(fx, 1, wz, "minecraft:flowering_azalea");
        s.set(fx - 1, 1, wz - 1, "minecraft:poppy").set(fx + 1, 1, wz - 1, "minecraft:dandelion");
        s.set(fx - 1, 1, wz + 1, "minecraft:cornflower").set(fx + 1, 1, wz + 1, "minecraft:allium");
        s.set(fx, 1, wz - 1, "minecraft:azure_bluet").set(fx, 1, wz + 1, "minecraft:oxeye_daisy");
        s.set(fx - 1, 1, wz, "minecraft:red_tulip").set(fx + 1, 1, wz, "minecraft:white_tulip");
        s.set(MID, 4, HALL_Z2 + 1, Kit.hangingLantern());
        // Gardens with a flowering tree on both sides of the hall; a shroomlight hidden in its crown lights the garden.
        for (int x : new int[]{6, 24}) {
            s.fill(x - 1, 0, 13, x + 1, 0, 19, GRASS);
            s.fill(x, 1, 16, x, 4, 16, Kit.log(DARK_LOG, "y"));
            for (int y = 4; y <= 6; y++) {
                int r = y == 6 ? 0 : 1;
                for (int dz = -1 - r; dz <= 1 + r; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (Math.abs(dz) + Math.abs(dx) <= 2 + r && !(dx == 0 && dz == 0 && y == 4)) {
                            s.set(x + dx, y, 16 + dz, Kit.leaves((dx + dz + y) % 3 == 0 ? "flowering_azalea" : "oak"));
                        }
                    }
                }
            }
            s.set(x, 5, 16, "minecraft:shroomlight");
            for (int z : new int[]{13, 19}) {
                s.set(x - 1, 1, z, "minecraft:poppy").set(x + 1, 1, z, "minecraft:dandelion");
            }
            s.set(x, 1, 14, "minecraft:grass").set(x, 1, 18, "minecraft:fern");
        }
        // Lamp posts along the road and in the back yard.
        for (int[] post : new int[][]{{13, 24}, {17, 24}, {8, 8}, {22, 8}}) {
            s.fill(post[0], 1, post[1], post[0], 2, post[1], FENCE);
            s.set(post[0], 3, post[1], Kit.lantern());
        }
        // Behind the keep: a woodpile and barrels by the kitchen, a bench by the chapel.
        s.fill(21, 1, 10, 21, 2, 11, Kit.log(OAK_LOG, "x")).fill(22, 1, 10, 22, 1, 11, Kit.log(OAK_LOG, "x"));
        s.set(24, 1, 9, Kit.barrel()).set(25, 1, 9, Kit.barrel()).set(24, 2, 9, Kit.barrel());
        s.set(8, 1, 10, Kit.stairs(SPRUCE_STAIRS, "west")).set(8, 1, 11, Kit.stairs(SPRUCE_STAIRS, "west"));
    }
}
