package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle of a small kingdom of the world: compact, but a real castle that reads as one mass rising in steps towards its
 * donjon. A curtain wall with a walk behind machicolated battlements joins four round corner towers of two kinds: at the
 * front two low and slim ones crowned with battlements, at the back two great ones under cones of slate. In the middle of
 * the front wall a strong square gatehouse with a deep arch, a portcullis and a crown of battlements guards the way in.
 * Behind a small courtyard with a well, a flower bed and two gardens stands the keep: a high hall whose gabled front with
 * its rose window looks at the gate, and behind it, on the north wall between the great towers, the square donjon, broader
 * and far higher than every tower, under a crown of battlements and a great spire with dormers and the flag of the kingdom.
 * Wall, towers, hall and donjon rise one behind the other like the steps of a pyramid. The throne of the kingdom stands in
 * the middle of the ground at the north end of the hall; the castle leaves it and the ground under it out.
 *
 * <p>Layer 0 replaces the ground, layer 1 is level with the street. Every part is drawn with the blocks that stand for the
 * parts of a look, so that the castle takes the stone, roofs and colours of its kingdom; the form alone, not the colour of
 * the spires, carries the design.
 */
final class SmallCastle {
    private static final String AIR = Structure.AIR;
    private static final String WOOD = "spruce";
    private static final String PLANKS = "minecraft:spruce_planks";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String DARK_LOG = "minecraft:dark_oak_log";
    private static final String OAK_LOG = "minecraft:oak_log";
    private static final String DOOR = "minecraft:spruce_door";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String GOLD = "minecraft:gold_block";
    private static final String YELLOW_WOOL = "minecraft:yellow_wool";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String FENCE = Kit.fence("oak");

    /** The ground is square, the box high enough for the spire of the donjon and its flag. */
    private static final int SIZE = 31;
    private static final int HEIGHT = 48;
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
    /** The donjon behind the hall, standing in the north wall between the great towers; its cornice and the middle row. */
    private static final int DON_X1 = 10;
    private static final int DON_X2 = 20;
    private static final int DON_Z1 = 2;
    private static final int DON_Z2 = 12;
    private static final int DON_CZ = 7;
    private static final int DON_TOP = 22;
    /** The round drum on the roof of the donjon: its diameter and the layer of its cornice, where the spire stands. */
    private static final int DRUM_D = 9;
    private static final int DRUM_TOP = 27;
    /** The layers of the spire of the donjon from its eaves to its point. */
    private static final int DON_SPIRE = 14;
    /** The floors of the donjon (the layer of their planks); the spiral stair arrives at every one of them. */
    private static final int[] DON_FLOORS = {5, 9, 13, 17};
    /** The cells of the spiral stair around its newel, in the order it climbs, one layer a cell from layer 1. */
    private static final int[][] SPIRAL = {{19, 5}, {18, 5}, {17, 5}, {17, 4}, {17, 3}, {18, 3}, {19, 3}, {19, 4}};
    private static final int NEWEL_X = 18;
    private static final int NEWEL_Z = 4;
    /** The great towers at the back corners: centres, diameter, top of the wall and the layers of their cones. */
    private static final int[][] BACK = {{5, 5}, {25, 5}};
    private static final int BACK_D = 9;
    private static final int BACK_TOP = 14;
    private static final int BACK_CONE = 10;
    /** The low towers at the front corners: centres, diameter and the layer of their platform. */
    private static final int[][] FRONT = {{4, 26}, {26, 26}};
    private static final int FRONT_D = 7;
    private static final int FRONT_TOP = 10;
    /** The gatehouse in the middle of the front wall, the row of its portcullis and the passage through it. */
    private static final int GATE_X1 = 11;
    private static final int GATE_X2 = 19;
    private static final int GATE_Z1 = 25;
    private static final int GATE_Z2 = 29;
    private static final int GATE_TOP = 12;
    private static final int GATE_Z = 27;
    private static final int PASS_X1 = 14;
    private static final int PASS_X2 = 16;

    private SmallCastle() {
    }

    /**
     * The template this class draws: the castle of a small kingdom with the places of its gate, its walk, its keep, its
     * hall and its treasury.
     */
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
                .at("castle_wall", 3, WALK + 1, 12).at("castle_wall", 3, WALK + 1, 20)
                .at("castle_wall", 27, WALK + 1, 12).at("castle_wall", 27, WALK + 1, 20)
                .at("castle_wall", 9, WALK + 1, 27).at("castle_wall", 21, WALK + 1, 27)
                .at("castle_wall", MID, WALK + 1, 26).at("castle_wall", MID, WALK + 1, 28));
        return list;
    }

    // ---------------------------------------------------------------- shapes

    /** A plan of an odd width around its middle: round or square. */
    private interface Plan {
        boolean in(int dx, int dz, int width);
    }

    private static final Plan ROUND = Kit::inDisc;

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

    /**
     * Machicolated battlements standing out one block around a square top: a course of corbels of trim at {@code y} with a
     * bracket under every merlon, the parapet on it, merlons on every second block and at the corners, capped with trim.
     */
    private static void crown(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                boolean westOrEast = x == x1 - 1 || x == x2 + 1;
                boolean northOrSouth = z == z1 - 1 || z == z2 + 1;
                if (!westOrEast && !northOrSouth || !s.inside(x, y + 3, z)) {
                    continue;
                }
                String toWall = x == x1 - 1 ? "east" : x == x2 + 1 ? "west" : z == z1 - 1 ? "south" : "north";
                boolean corner = westOrEast && northOrSouth;
                boolean merlon = corner || Math.floorMod(x + z, 2) == 0;
                s.set(x, y, z, corner ? Kit.TRIM : Kit.upside(Kit.TRIM_STAIRS, toWall));
                s.set(x, y + 1, z, Kit.WALL);
                if (merlon) {
                    s.set(x, y - 1, z, Kit.upside(Kit.WALL_STAIRS, toWall));
                    s.set(x, y + 2, z, Kit.WALL).set(x, y + 3, z, Kit.slab(Kit.TRIM_SLAB));
                }
            }
        }
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

    /** A pane of stained glass of a colour. */
    private static String pane(String color) {
        return Kit.stainedPane(color);
    }

    // ---------------------------------------------------------------- the castle

    /**
     * The castle of a small kingdom, built as a pyramid of masses. The curtain wall with its walk and machicolated
     * battlements is the lowest step; the two slim towers at the front corners rise a little above it under flat tops
     * behind battlements; the square gatehouse between them, broad and plain, stands higher still under its own crown of
     * battlements and a flag. Behind the courtyard the hall of the keep lifts its steep gable with the rose window, the two
     * great towers at the back corners stand twice as high as the front ones under cones of slate, and in the middle of the
     * back, joined to the hall, the donjon rises far above them all: a square tower broader than any other, with four floors
     * over the treasury, the highest an open lookout, a crown of machicolated battlements, a round drum out of the crown and
     * on it a great spire with four dormers and the flag of the kingdom on its point. The rooms: a throne hall with arcades;
     * a treasury, a council room, a library, the royal chamber and the lookout of the guard in the donjon; a guard room in
     * the gatehouse; a chapel, a kitchen, an armoury and a store in the corner towers.
     */
    static Structure castleSmall() {
        Structure s = new Structure(SIZE, HEIGHT, SIZE);
        ground(s);
        curtain(s);
        for (int[] tower : BACK) {
            greatTower(s, tower[0], tower[1]);
        }
        for (int[] tower : FRONT) {
            frontTower(s, tower[0], tower[1]);
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
        s.fill(PASS_X1 - 1, 0, GATE_Z1, PASS_X2 + 1, 0, SIZE - 1, Kit.ROUGH);
        s.fill(PASS_X1, 0, HALL_Z2 + 1, PASS_X2, 0, SIZE - 1, Kit.TRIM);
    }

    /**
     * The curtain wall: two blocks thick on a plinth and a talus of rough stone, blind arrow slits, and at the top a parapet
     * standing out over the outer face on a course of trim and brackets (machicolations), with merlons capped with trim and
     * lanterns in the crenels; the walk behind it is widened inside on a row of corbels. The north wall is hidden behind
     * the great towers and the donjon, which stand in it.
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
        // Blind arrow slits in the outer faces of the sides.
        for (int i : new int[]{11, MID, 19}) {
            s.fill(2, 3, i, 2, 4, i, AIR).fill(last, 3, i, last, 4, i, AIR);
        }
        // The machicolated battlements of the sides and of the front between the towers and the gatehouse.
        for (int i = 9; i <= 24; i++) {
            boolean merlon = i % 2 == 0;
            machicolation(s, 1, i, "east", merlon);
            machicolation(s, last + 1, i, "west", merlon);
        }
        for (int i : new int[]{8, 9, 10, 20, 21, 22}) {
            machicolation(s, i, last + 1, "north", i % 2 == 0);
        }
        for (int i : new int[]{13, 17, 21}) {
            s.set(1, WALK + 2, i, Kit.lantern()).set(last + 1, WALK + 2, i, Kit.lantern());
        }
        s.set(9, WALK + 2, last + 1, Kit.lantern()).set(21, WALK + 2, last + 1, Kit.lantern());
        // Banners of the kingdom hanging under the battlements of the sides.
        for (int i : new int[]{13, 17}) {
            s.set(1, WALK - 2, i, Kit.wallBanner("red", "west")).set(last + 1, WALK - 2, i, Kit.wallBanner("red", "east"));
        }
        // The walk widened on corbels along the inner faces of the sides.
        for (int i = 10; i <= 22; i++) {
            corbel(s, 4, i, "west");
            corbel(s, SIZE - 5, i, "east");
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
        s.set(x, WALK + 1, z, Kit.WALL);
        if (merlon) {
            s.set(x, WALK + 2, z, Kit.WALL).set(x, WALK + 3, z, Kit.slab(Kit.TRIM_SLAB));
        }
    }

    /** A slab of the walk standing out over the courtyard, on a bracket at every second block. */
    private static void corbel(Structure s, int x, int z, String wall) {
        s.set(x, WALK, z, Kit.topSlab(Kit.TRIM_SLAB));
        if ((x + z) % 2 == 0) {
            s.set(x, WALK - 1, z, Kit.upside(Kit.WALL_STAIRS, wall));
        }
    }

    /**
     * The batter of rough stone round the foot of a round tower, on its outer side only: the side to the courtyard, with
     * the door, stands straight.
     */
    private static void batter(Structure s, int cx, int cz, int d) {
        int sx = Integer.signum(cx - MID);
        int sz = Integer.signum(cz - MID);
        ring(s, ROUND, cx, 1, cz, d, Kit.ROUGH_STAIRS, Kit.ROUGH, false);
        int reach = (d + 1) / 2;
        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                if (dx * sx < 0 && dz * sz < 0 && !Kit.inDisc(dx, dz, d) && s.inside(cx + dx, 1, cz + dz)) {
                    s.set(cx + dx, 1, cz + dz, AIR);
                }
            }
        }
    }

    /**
     * A great round tower of nine at a back corner, standing out of the curtain and touching the donjon: a batter of rough
     * stone round its outer side, a room on the ground with a door to the courtyard, a room at the height of the walk with
     * a door to it, arrow slits, tall windows to the courtyard and banners of the kingdom under a corbelled cornice, and
     * over it a cone of slate, the highest roof of the castle after the spire of the donjon.
     */
    private static void greatTower(Structure s, int cx, int cz) {
        int d = BACK_D;
        int top = BACK_TOP;
        int sx = Integer.signum(cx - MID);
        int sz = Integer.signum(cz - MID);
        batter(s, cx, cz, d);
        Kit.tube(s, cx, 1, top, cz, d, Kit.WALL);
        Kit.tube(s, cx, 1, 2, cz, d, Kit.ROUGH);
        Kit.tube(s, cx, WALK, WALK, cz, d, Kit.TRIM);
        Kit.tube(s, cx, 10, 10, cz, d, Kit.TRIM);
        inside(s, cx, 1, top, cz, d, AIR);
        inside(s, cx, 0, 0, cz, d, PLANKS);
        inside(s, cx, WALK, WALK, cz, d, PLANKS);
        // Arrow slits on the outer faces, open windows of the room at the walk, tall windows to the courtyard.
        for (int[] face : new int[][]{{sx * 4, 0}, {0, sz * 4}, {sx * 3, sz * 3}}) {
            s.fill(cx + face[0], 3, cz + face[1], cx + face[0], 4, cz + face[1], Kit.GLASS);
            s.fill(cx + face[0], WALK + 2, cz + face[1], cx + face[0], WALK + 3, cz + face[1], AIR);
        }
        s.fill(cx - sx * 3, 11, cz - sz * 3, cx - sx * 3, 12, cz - sz * 3, Kit.GLASS);
        s.fill(cx, 11, cz - sz * 4, cx, 12, cz - sz * 4, Kit.GLASS);
        // Banners of the kingdom on the outer faces, under the cornice.
        s.set(cx + sx * 5, top - 2, cz, Kit.wallBanner("red", sx < 0 ? "west" : "east"));
        s.set(cx, top - 2, cz + sz * 5, Kit.wallBanner("red", sz < 0 ? "north" : "south"));
        // The cornice on corbels and the cone.
        Kit.tube(s, cx, top, top, cz, d, Kit.TRIM);
        ring(s, ROUND, cx, top, cz, d, Kit.WALL_STAIRS, Kit.WALL, true);
        spire(s, true, cx, top + 1, cz, (d - 1) / 2, BACK_CONE, Kit.SLATE);
        hang(s, cx, cz, WALK + 4);
        // The door to the courtyard and the door from the walk.
        s.door(cx - sx * 3, 1, cz - sz * 3, DOOR, "south", "left");
        s.set(cx - sx * 3, 3, cz - sz * 3, Kit.WALL);
        s.door(cx + sx, WALK + 1, cz - sz * 4, DOOR, "south", "left");
    }

    /**
     * A low round tower of seven at a front corner: a batter of rough stone round its outer side, a room on the ground
     * with a door to the courtyard, a room at the height of the walk with doors to the walk on both sides, arrow slits,
     * a banner of the kingdom on its front, and on top a flat platform behind battlements standing out on corbels.
     */
    private static void frontTower(Structure s, int cx, int cz) {
        int d = FRONT_D;
        int top = FRONT_TOP;
        int sx = Integer.signum(cx - MID);
        int sz = Integer.signum(cz - MID);
        batter(s, cx, cz, d);
        Kit.tube(s, cx, 1, top, cz, d, Kit.WALL);
        Kit.tube(s, cx, 1, 2, cz, d, Kit.ROUGH);
        Kit.tube(s, cx, WALK, WALK, cz, d, Kit.TRIM);
        inside(s, cx, 1, top - 1, cz, d, AIR);
        inside(s, cx, 0, 0, cz, d, PLANKS);
        inside(s, cx, WALK, WALK, cz, d, PLANKS);
        // Arrow slits on the outer faces, open windows of the room at the walk.
        for (int[] face : new int[][]{{sx * 3, 0}, {0, sz * 3}, {sx * 2, sz * 2}}) {
            s.fill(cx + face[0], 3, cz + face[1], cx + face[0], 4, cz + face[1], Kit.GLASS);
        }
        for (int[] face : new int[][]{{sx * 3, 0}, {sx * 2, sz * 2}}) {
            s.fill(cx + face[0], WALK + 2, cz + face[1], cx + face[0], WALK + 3, cz + face[1], AIR);
        }
        s.set(cx, WALK + 3, cz + sz * 4, Kit.wallBanner("red", sz < 0 ? "north" : "south"));
        // The platform behind battlements on corbels, with a lantern.
        Kit.disc(s, cx, top, cz, d, Kit.TRIM);
        ring(s, ROUND, cx, top, cz, d, Kit.WALL_STAIRS, Kit.WALL, true);
        Kit.roundBattlements(s, cx, top + 1, cz, d + 2, Kit.WALL);
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                if (s.get(cx + dx, top + 2, cz + dz).equals(Kit.WALL)) {
                    s.set(cx + dx, top + 3, cz + dz, Kit.slab(Kit.TRIM_SLAB));
                }
            }
        }
        s.set(cx, top + 1, cz, Kit.lantern());
        s.set(cx, top - 1, cz, Kit.hangingLantern());
        // The door to the courtyard and the doors from the walk.
        s.door(cx - sx * 2, 1, cz - sz * 2, DOOR, "south", "left");
        s.set(cx - sx * 2, 3, cz - sz * 2, Kit.WALL);
        s.door(cx, WALK + 1, cz - sz * 3, DOOR, "north", "left");
        s.door(cx - sx * 3, WALK + 1, cz + sz, DOOR, sx < 0 ? "east" : "west", "left");
    }

    /**
     * The gatehouse: a broad square block standing out of the front wall, with quoins of rough stone and a talus at its
     * foot. A deep outer arch five wide frames the gate, an arch three wide with a raised portcullis behind it; a passage
     * runs through to the courtyard. Over the passage lies the guard room, through which the walk passes, with arrow slits
     * and a window over the gate under the arms of the castle; on top a platform behind a crown of machicolated
     * battlements with lanterns and the flag of the kingdom, and the banners of the kingdom hang on both sides of the front.
     */
    private static void gatehouse(Structure s) {
        s.fill(GATE_X1, 1, GATE_Z1, GATE_X2, GATE_TOP, GATE_Z2, Kit.WALL);
        s.walls(GATE_X1, 1, GATE_Z1, GATE_X2, 2, GATE_Z2, Kit.ROUGH);
        for (int x : new int[]{GATE_X1, GATE_X2}) {
            for (int z : new int[]{GATE_Z1, GATE_Z2}) {
                s.fill(x, 1, z, x, GATE_TOP, z, Kit.ROUGH);
            }
        }
        s.walls(GATE_X1, WALK, GATE_Z1, GATE_X2, WALK, GATE_Z2, Kit.TRIM);
        for (int x = GATE_X1; x <= GATE_X2; x++) {
            if (x < PASS_X1 - 1 || x > PASS_X2 + 1) {
                s.set(x, 1, GATE_Z2 + 1, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
            }
        }
        // The deep outer arch with its keystone, and the passage with its arches behind it.
        s.fill(PASS_X1 - 1, 1, GATE_Z2, PASS_X2 + 1, 5, GATE_Z2, AIR);
        s.set(PASS_X1 - 1, 5, GATE_Z2, Kit.upside(Kit.WALL_STAIRS, "west")).set(PASS_X2 + 1, 5, GATE_Z2, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(PASS_X1 - 2, 5, GATE_Z2, Kit.TRIM).set(PASS_X2 + 2, 5, GATE_Z2, Kit.TRIM);
        s.set(MID, WALK, GATE_Z2, Kit.CARVED);
        s.fill(PASS_X1, 1, GATE_Z1, PASS_X2, 4, GATE_Z2 - 1, AIR);
        for (int z : new int[]{GATE_Z1, GATE_Z2 - 1}) {
            s.set(PASS_X1, 4, z, Kit.upside(Kit.WALL_STAIRS, "west")).set(PASS_X2, 4, z, Kit.upside(Kit.WALL_STAIRS, "east"));
            s.set(MID, 5, z, Kit.CARVED);
        }
        s.fill(PASS_X1, 4, GATE_Z, PASS_X2, 4, GATE_Z, Kit.BARS);
        s.fill(PASS_X1, 5, GATE_Z2, PASS_X2, 5, GATE_Z2, Kit.BARS);
        s.set(PASS_X1, 3, GATE_Z, Kit.wallTorch("east")).set(PASS_X2, 3, GATE_Z, Kit.wallTorch("west"));
        s.set(PASS_X1 - 2, 3, GATE_Z2 + 1, Kit.wallTorch("south")).set(PASS_X2 + 2, 3, GATE_Z2 + 1, Kit.wallTorch("south"));
        s.set(MID, 4, GATE_Z1 + 1, Kit.hangingLantern());
        // The guard room over the passage; the walk enters it from both sides.
        s.fill(GATE_X1 + 1, WALK, GATE_Z1 + 1, GATE_X2 - 1, WALK, GATE_Z2 - 1, PLANKS);
        s.fill(GATE_X1 + 1, WALK + 1, GATE_Z1 + 1, GATE_X2 - 1, GATE_TOP - 1, GATE_Z2 - 1, AIR);
        s.door(GATE_X1, WALK + 1, GATE_Z, DOOR, "east", "left").door(GATE_X2, WALK + 1, GATE_Z, DOOR, "west", "left");
        for (int x : new int[]{13, 17}) {
            s.fill(x, WALK + 2, GATE_Z1, x, WALK + 3, GATE_Z1, Kit.GLASS);
        }
        for (int x : new int[]{12, MID, 18}) {
            s.fill(x, WALK + 2, GATE_Z2, x, WALK + 3, GATE_Z2, Kit.GLASS);
        }
        s.set(MID, WALK + 1, GATE_Z2, Kit.TRIM);
        s.set(MID - 1, WALK + 4, GATE_Z2, Kit.TRIM).set(MID, WALK + 4, GATE_Z2, Kit.CARVED).set(MID + 1, WALK + 4, GATE_Z2, Kit.TRIM);
        s.set(MID, WALK + 5, GATE_Z2, Kit.slab(Kit.TRIM_SLAB));
        s.set(MID, GATE_TOP - 1, GATE_Z, Kit.hangingLantern());
        s.set(GATE_X1 + 1, WALK + 1, GATE_Z1 + 1, "minecraft:grindstone[face=floor,facing=north]");
        s.set(GATE_X2 - 1, WALK + 1, GATE_Z1 + 1, "minecraft:grindstone[face=floor,facing=north]");
        s.set(GATE_X1 + 1, WALK + 1, GATE_Z2 - 1, Kit.chest("east"));
        Furnish.crate(s, GATE_X2 - 1, WALK + 1, GATE_Z2 - 1, WOOD);
        Furnish.bench(s, 13, GATE_Z2 - 1, 14, GATE_Z2 - 1, WALK + 1, "north", WOOD);
        s.set(MID, WALK + 2, GATE_Z1 + 1, Kit.wallBanner("red", "south"));
        // The roof behind the crown, lanterns at its corners and the flag of the kingdom on a pole.
        s.fill(GATE_X1, GATE_TOP, GATE_Z1, GATE_X2, GATE_TOP, GATE_Z2, Kit.TRIM);
        crown(s, GATE_X1, GATE_Z1, GATE_X2, GATE_Z2, GATE_TOP);
        for (int x : new int[]{GATE_X1, GATE_X2}) {
            for (int z : new int[]{GATE_Z1, GATE_Z2}) {
                s.set(x, GATE_TOP + 1, z, Kit.lantern());
            }
        }
        s.fill(MID, GATE_TOP + 1, GATE_Z, MID, GATE_TOP + 5, GATE_Z, FENCE);
        s.fill(MID + 1, GATE_TOP + 4, GATE_Z, MID + 2, GATE_TOP + 5, GATE_Z, Kit.CLOTH);
        s.set(MID, GATE_TOP + 6, GATE_Z, Kit.lantern());
        // Banners of the kingdom on both sides of the front.
        s.set(GATE_X1 + 1, GATE_TOP - 1, GATE_Z2 + 1, Kit.wallBanner("red", "south"));
        s.set(GATE_X2 - 1, GATE_TOP - 1, GATE_Z2 + 1, Kit.wallBanner("red", "south"));
    }

    /**
     * The shell of the hall of the keep: walls on a plinth with quoins, buttresses on its sides and clasping buttresses at
     * its front corners, tall lancet windows of stained glass, a cornice of trim, a steep gable roof of tiles whose front
     * gable holds a rose window over the arched door in its porch. Its back wall is the front of the donjon.
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
            // A clasping buttress at the front corner, on both of its faces.
            s.fill(x, 1, HALL_Z2, x, 7, HALL_Z2, Kit.ROUGH);
            s.set(x, 8, HALL_Z2, Kit.stairs(Kit.ROUGH_STAIRS, toWall));
        }
        for (int x : new int[]{HALL_X1, HALL_X2}) {
            s.fill(x, 1, HALL_Z2 + 1, x, 7, HALL_Z2 + 1, Kit.ROUGH);
            s.set(x, 8, HALL_Z2 + 1, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
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
    }

    /**
     * The donjon, rising in three steps: a square tower eleven wide standing in the north wall behind the hall, on a plinth
     * of rough stone with quoins and courses of trim, windows at every floor and the banners of the kingdom on its faces,
     * an open lookout with arches under its cornice and a crown of machicolated battlements standing out on corbels with
     * lanterns at its corners; out of the crown a round drum with windows under a corbelled cornice; and on the drum a great
     * round spire of slate with a dormer on every side and the flag of the kingdom on its point, the highest point of the
     * castle by far.
     */
    private static void donjon(Structure s) {
        s.walls(DON_X1, 1, DON_Z1, DON_X2, DON_TOP, DON_Z2, Kit.WALL);
        s.fill(DON_X1 + 1, 1, DON_Z1 + 1, DON_X2 - 1, DON_TOP - 1, DON_Z2 - 1, AIR);
        s.walls(DON_X1, 1, DON_Z1, DON_X2, 2, DON_Z2, Kit.ROUGH);
        for (int x : new int[]{DON_X1, DON_X2}) {
            for (int z : new int[]{DON_Z1, DON_Z2}) {
                s.fill(x, 1, z, x, DON_TOP, z, Kit.ROUGH);
            }
        }
        for (int y : new int[]{DON_FLOORS[1], DON_FLOORS[3]}) {
            s.walls(DON_X1, y, DON_Z1, DON_X2, y, DON_Z2, Kit.TRIM);
        }
        // Windows of the rooms: two on every free face of every floor below the lookout, over a sill of trim.
        for (int i = 0; i < DON_FLOORS.length - 1; i++) {
            int floor = DON_FLOORS[i];
            for (int y = floor + 2; y <= floor + 3; y++) {
                for (int x : new int[]{13, 17}) {
                    s.set(x, y, DON_Z1, Kit.GLASS);
                }
                for (int z : new int[]{8, 10}) {
                    s.set(DON_X1, y, z, Kit.GLASS).set(DON_X2, y, z, Kit.GLASS);
                }
            }
            for (int x : new int[]{13, 17}) {
                s.set(x, floor + 1, DON_Z1, Kit.TRIM);
            }
        }
        // Barred slits of the treasury, slits of the stair hall.
        s.set(DON_X1, 3, 8, Kit.BARS).set(DON_X1, 3, 10, Kit.BARS).set(13, 3, DON_Z1, Kit.BARS);
        s.set(DON_X2, 3, 8, Kit.GLASS).set(DON_X2, 3, 10, Kit.GLASS);
        // The lookout: arches over a balustrade on every face; over the ridge of the hall the banner of the kingdom.
        int look = DON_FLOORS[DON_FLOORS.length - 1] + 1;
        for (int i : new int[]{-3, 0, 3}) {
            List<int[]> cells = new ArrayList<>(List.of(new int[]{MID + i, DON_Z1}, new int[]{DON_X1, DON_CZ + i},
                    new int[]{DON_X2, DON_CZ + i}));
            if (i != 0) {
                cells.add(new int[]{MID + i, DON_Z2});
            }
            for (int[] cell : cells) {
                s.set(cell[0], look, cell[1], Kit.WALL_FENCE);
                s.fill(cell[0], look + 1, cell[1], cell[0], DON_TOP - 1, cell[1], AIR);
            }
        }
        s.set(MID, DON_TOP - 1, DON_Z2 + 1, Kit.wallBanner("red", "south"));
        s.set(MID, DON_FLOORS[2] + 3, DON_Z1 - 1, Kit.wallBanner("red", "north"));
        // The cornice, the roof and the crown of battlements, with lanterns in its corners.
        s.fill(DON_X1, DON_TOP, DON_Z1, DON_X2, DON_TOP, DON_Z2, Kit.TRIM);
        crown(s, DON_X1, DON_Z1, DON_X2, DON_Z2, DON_TOP);
        for (int x : new int[]{DON_X1 + 1, DON_X2 - 1}) {
            for (int z : new int[]{DON_Z1 + 1, DON_Z2 - 1}) {
                s.set(x, DON_TOP + 1, z, Kit.lantern());
            }
        }
        // The drum: a round wall on the roof with a window to every side, a lantern inside and a cornice on corbels.
        Kit.tube(s, MID, DON_TOP + 1, DRUM_TOP, DON_CZ, DRUM_D, Kit.WALL);
        Kit.tube(s, MID, DON_TOP + 1, DON_TOP + 1, DON_CZ, DRUM_D, Kit.ROUGH);
        int r = (DRUM_D - 1) / 2;
        for (int[] face : new int[][]{{0, r}, {0, -r}, {r, 0}, {-r, 0}}) {
            s.fill(MID + face[0], DRUM_TOP - 2, DON_CZ + face[1], MID + face[0], DRUM_TOP - 1, DON_CZ + face[1], Kit.GLASS);
        }
        Kit.tube(s, MID, DRUM_TOP, DRUM_TOP, DON_CZ, DRUM_D, Kit.TRIM);
        ring(s, ROUND, MID, DRUM_TOP, DON_CZ, DRUM_D, Kit.WALL_STAIRS, Kit.WALL, true);
        // The spire on the drum, its dormers and the flag of the kingdom on its point.
        int point = spire(s, true, MID, DRUM_TOP + 1, DON_CZ, r, DON_SPIRE, Kit.SLATE);
        hang(s, MID, DON_CZ, DRUM_TOP - 1);
        for (int[] face : new int[][]{{0, 1, 1, 0}, {0, -1, 1, 0}, {1, 0, 0, 1}, {-1, 0, 0, 1}}) {
            dormer(s, face[0], face[1], face[2], face[3]);
        }
        s.fill(MID, point, DON_CZ, MID, point + 3, DON_CZ, FENCE);
        s.fill(MID + 1, point + 2, DON_CZ, MID + 3, point + 3, DON_CZ, Kit.CLOTH);
        s.set(MID, point + 4, DON_CZ, GOLD);
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
        int y = DRUM_TOP + 2;
        for (int u = -1; u <= 1; u++) {
            for (int v = 3; v <= 5; v++) {
                int x = MID + ax * u + ox * v;
                int z = DON_CZ + az * u + oz * v;
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
        s.set(HALL_X1 + 1, 5, 16, Kit.wallBanner("red", "east")).set(HALL_X2 - 1, 5, 16, Kit.wallBanner("red", "west"));
        s.set(HALL_X1 + 1, 4, HALL_Z2 - 1, Kit.wallTorch("east")).set(HALL_X2 - 1, 4, HALL_Z2 - 1, Kit.wallTorch("west"));
        s.set(HALL_X1 + 1, 4, HALL_Z1 + 1, Kit.wallTorch("east")).set(HALL_X2 - 1, 4, HALL_Z1 + 1, Kit.wallTorch("west"));
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
     * iron bars on the ground floor beside the hall of the stair, then the council room, the library, the royal chamber
     * with its bed of state and a corner for prayer, and on top the lookout of the guard with its hearth and racks of arms.
     */
    private static void donjonRooms(Structure s) {
        int x1 = DON_X1 + 1;
        int x2 = DON_X2 - 1;
        int z1 = DON_Z1 + 1;
        int z2 = DON_Z2 - 1;
        int last = DON_FLOORS[DON_FLOORS.length - 1];
        s.fill(x1, 0, z1, x2, 0, z2, PLANKS);
        for (int floor : DON_FLOORS) {
            s.fill(x1, floor, z1, x2, floor, z2, PLANKS);
            s.fill(NEWEL_X - 1, floor, NEWEL_Z - 1, NEWEL_X + 1, floor, NEWEL_Z + 1, AIR);
        }
        // The spiral stair.
        s.fill(NEWEL_X, 1, NEWEL_Z, NEWEL_X, last, NEWEL_Z, Kit.WALL);
        for (int k = 0; k < last; k++) {
            int[] here = SPIRAL[k % SPIRAL.length];
            int[] next = SPIRAL[(k + 1) % SPIRAL.length];
            String facing = next[0] > here[0] ? "east" : next[0] < here[0] ? "west" : next[1] > here[1] ? "south" : "north";
            s.set(here[0], 1 + k, here[1], Kit.stairs(Kit.WALL_STAIRS, facing));
        }
        // Railings around the well of the stair, open where it arrives.
        for (int floor : DON_FLOORS) {
            int[] arrive = SPIRAL[(floor - 1) % SPIRAL.length];
            int y = floor + 1;
            for (int z = NEWEL_Z - 1; z <= NEWEL_Z + 2; z++) {
                if (!(arrive[0] == NEWEL_X - 1 && arrive[1] == z)) {
                    s.set(NEWEL_X - 2, y, z, FENCE);
                }
            }
            for (int x = NEWEL_X - 1; x <= NEWEL_X + 1; x++) {
                if (!(arrive[1] == NEWEL_Z + 1 && arrive[0] == x)) {
                    s.set(x, y, NEWEL_Z + 2, FENCE);
                }
            }
        }
        s.set(NEWEL_X, last + 1, NEWEL_Z, Kit.lantern());
        treasury(s);
        // The hall of the stair: a bench for the guard and a lantern.
        Furnish.bench(s, 19, 9, 19, 10, 1, "west", WOOD);
        s.set(18, 4, 8, Kit.hangingLantern());
        s.set(19, 3, 11, Kit.wallTorch("north"));
        // The council room: a long table with chairs, bookshelves, banners and lanterns over the table.
        int y = DON_FLOORS[0] + 1;
        Furnish.longTable(s, 13, 7, 14, 10, y, WOOD);
        for (int z = 7; z <= 10; z++) {
            Furnish.chair(s, 12, y, z, "east", WOOD);
            Furnish.chair(s, 15, y, z, "west", WOOD);
        }
        Furnish.books(s, 11, 3, 12, 3, y, y + 1);
        Furnish.books(s, 11, 4, 11, 5, y, y + 1);
        s.set(12, y + 1, z2, Kit.wallBanner("red", "north")).set(MID, y + 1, z2, Kit.wallBanner("red", "north"));
        Furnish.crate(s, 19, y, 11, WOOD);
        Furnish.crate(s, 19, y, 10, WOOD);
        s.set(13, y + 2, 8, Kit.hangingLantern()).set(14, y + 2, 9, Kit.hangingLantern()).set(18, y + 2, 9, Kit.hangingLantern());
        // The library: shelves round the walls between the windows, a lectern, a reading table and a writing desk.
        y = DON_FLOORS[1] + 1;
        for (int z : new int[]{3, 4, 5, 6, 7, 9, 11}) {
            Furnish.books(s, 11, z, 11, z, y, y + 2);
        }
        Furnish.books(s, 12, 3, 12, 3, y, y + 2);
        Furnish.books(s, 14, 3, 15, 3, y, y + 2);
        Furnish.books(s, 12, 11, 15, 11, y, y + 2);
        s.set(13, y, 5, "minecraft:lectern[facing=south,has_book=false,powered=false]");
        Furnish.longTable(s, 13, 8, 14, 8, y, WOOD);
        s.set(13, y + 1, 8, Furnish.candles(2));
        Furnish.chair(s, 13, y, 9, "north", WOOD);
        Furnish.chair(s, 14, y, 9, "north", WOOD);
        Furnish.rug(s, 17, 7, 18, 10, y, Kit.CLOTH_CARPET);
        Furnish.desk(s, 19, y, 9, "west", WOOD);
        s.set(14, y + 2, 7, Kit.hangingLantern()).set(18, y + 2, 8, Kit.hangingLantern());
        // The royal chamber: a bed of state whose canopy is the ceiling, a rug, a wardrobe, a writing desk and a corner for
        // prayer with an altar, candles and windows of stained glass.
        y = DON_FLOORS[2] + 1;
        Furnish.canopyBed(s, 13, y, 5, "west", WOOD);
        for (int[] post : new int[][]{{11, 4}, {11, 6}, {13, 4}, {13, 6}}) {
            s.set(post[0], y + 3, post[1], PLANKS);
        }
        Furnish.rug(s, 12, 7, 15, 10, y, Kit.CLOTH_CARPET);
        Furnish.crate(s, 12, y, 11, WOOD);
        Furnish.crate(s, 13, y, 11, WOOD);
        Furnish.crate(s, 12, y + 1, 11, WOOD);
        Furnish.desk(s, 18, y, 8, "south", WOOD);
        s.set(19, y, 11, Kit.TRIM).set(19, y + 1, 11, Furnish.candles(3));
        s.set(18, y, 11, Kit.stairs(SPRUCE_STAIRS, "west"));
        s.set(DON_X2, y + 1, 10, pane("red")).set(DON_X2, y + 2, 10, pane("yellow"));
        s.set(DON_X2, y + 1, 8, pane("blue")).set(DON_X2, y + 2, 8, pane("yellow"));
        s.set(14, y + 2, 8, Kit.hangingLantern()).set(17, y + 2, 10, Kit.hangingLantern());
        // The lookout of the guard: a hearth, a table with benches, racks of arms, crates and a rug over the canopy below.
        y = DON_FLOORS[3] + 1;
        Furnish.rug(s, 11, 4, 13, 6, y, Kit.CLOTH_CARPET);
        Furnish.hearth(s, 11, y, 9, "east");
        Furnish.longTable(s, 14, 8, 15, 10, y, WOOD);
        Furnish.bench(s, 13, 8, 13, 10, y, "east", WOOD);
        Furnish.bench(s, 16, 8, 16, 10, y, "west", WOOD);
        Furnish.weaponRack(s, 19, y, 9, "west", WOOD);
        Furnish.weaponRack(s, 19, y, 11, "west", WOOD);
        Furnish.crate(s, 11, y, 11, WOOD);
        Furnish.crate(s, 12, y, 11, WOOD);
        Furnish.sack(s, 11, y + 1, 11, false);
        s.set(14, y + 2, 9, Kit.hangingLantern()).set(12, y + 2, 5, Kit.hangingLantern()).set(18, y + 2, 9, Kit.hangingLantern());
    }

    /**
     * The treasury on the ground floor of the donjon: a vault behind a grille of iron bars, entered by its own door from the
     * hall, holding the strong chests and the gold of the kingdom, crates of coin and a counting table with candles.
     */
    private static void treasury(Structure s) {
        s.fill(NEWEL_X - 2, 1, DON_Z1 + 1, NEWEL_X - 2, DON_FLOORS[0] - 1, DON_Z2 - 1, Kit.BARS);
        for (int z = 4; z <= 6; z++) {
            s.set(11, 1, z, Kit.chest("east"));
        }
        s.set(14, 1, 3, GOLD).set(15, 1, 3, "minecraft:raw_gold_block").set(14, 2, 3, GOLD).set(15, 1, 4, GOLD);
        Furnish.longTable(s, 13, 8, 14, 8, 1, WOOD);
        s.set(13, 2, 8, Furnish.candles(3));
        Furnish.chair(s, 13, 1, 9, "north", WOOD);
        Furnish.chair(s, 14, 1, 9, "north", WOOD);
        Furnish.crate(s, 11, 1, 9, WOOD);
        Furnish.crate(s, 11, 1, 10, WOOD);
        Furnish.crate(s, 11, 2, 10, WOOD);
        s.set(13, 4, 5, Kit.hangingLantern()).set(13, 4, 10, Kit.hangingLantern());
    }

    /**
     * The rooms on the ground floor of the corner towers: a chapel and a kitchen in the great towers, an armoury and a
     * store in the front ones; the rooms at the walk hold crates and are lit by lanterns.
     */
    private static void towerRooms(Structure s) {
        // The chapel in the north-west tower: an altar with candles, pews and windows of stained glass.
        s.fill(4, 1, 2, 6, 1, 2, Kit.TRIM);
        s.set(5, 2, 2, Furnish.candles(3)).set(4, 2, 2, Furnish.candle("")).set(6, 2, 2, Furnish.candle(""));
        s.set(5, 3, 2, Kit.wallBanner("red", "south"));
        for (int z : new int[]{5, 7}) {
            Furnish.bench(s, 3, z, 4, z, 1, "north", WOOD);
            Furnish.bench(s, 6, z, 7, z, 1, "north", WOOD);
        }
        s.fill(5, 3, 1, 5, 4, 1, pane("yellow")).fill(1, 3, 5, 1, 4, 5, pane("blue")).fill(2, 3, 2, 2, 4, 2, pane("red"));
        s.set(5, WALK - 1, 5, Kit.hangingLantern());
        // The kitchen in the north-east tower: a cauldron, a fire and a board, a furnace and a smoker, a table, a barrel.
        Furnish.kitchen(s, 24, 1, 2, "south", WOOD);
        s.set(28, 1, 4, "minecraft:furnace[facing=west,lit=false]").set(28, 1, 6, "minecraft:smoker[facing=west,lit=false]");
        s.set(27, 1, 3, Kit.barrel());
        s.set(24, 1, 5, Kit.topSlab(SPRUCE_SLAB)).set(24, 2, 5, "minecraft:cake[bites=0]");
        Furnish.chair(s, 23, 1, 5, "east", WOOD);
        Furnish.sack(s, 27, 1, 7, true);
        Furnish.sack(s, 26, 1, 8, false);
        s.set(25, WALK - 1, 5, Kit.hangingLantern());
        // The armoury in the south-west tower.
        s.set(2, 1, 26, "minecraft:smithing_table").set(3, 1, 28, "minecraft:anvil[facing=east]");
        s.set(2, 1, 25, Kit.chest("east"));
        Furnish.crate(s, 4, 1, 28, WOOD);
        s.set(5, 1, 28, "minecraft:grindstone[face=floor,facing=north]");
        s.set(4, WALK - 1, 26, Kit.hangingLantern());
        // The store in the south-east tower.
        s.set(28, 1, 26, Kit.barrel());
        Furnish.crate(s, 28, 1, 25, WOOD);
        Furnish.crate(s, 28, 2, 26, WOOD);
        s.set(28, 1, 27, HAY).set(27, 1, 28, HAY).set(26, 1, 28, HAY).set(27, 2, 28, HAY);
        Furnish.sack(s, 25, 1, 28, true);
        s.set(26, WALK - 1, 26, Kit.hangingLantern());
        // The rooms at the walk.
        for (int[] tower : BACK) {
            int sx = Integer.signum(tower[0] - MID);
            Furnish.crate(s, tower[0] + sx * 3, WALK + 1, tower[1], WOOD);
        }
        for (int[] tower : FRONT) {
            int sx = Integer.signum(tower[0] - MID);
            int sz = Integer.signum(tower[1] - MID);
            Furnish.crate(s, tower[0] + sx * 2, WALK + 1, tower[1] + sz, WOOD);
        }
    }

    /**
     * The courtyard: a well under a small roof, a flower bed, gardens with flowering trees on both sides of the keep, lamp
     * posts by the road and in the yards behind the keep, a woodpile by the kitchen and a bench by the chapel.
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
        // Lamp posts along the road and in the yards behind the keep.
        for (int[] post : new int[][]{{13, 24}, {17, 24}, {7, 11}, {23, 11}}) {
            s.fill(post[0], 1, post[1], post[0], 2, post[1], FENCE);
            s.set(post[0], 3, post[1], Kit.lantern());
        }
        // Behind the keep: a woodpile by the kitchen, a bench by the chapel.
        s.fill(24, 1, 11, 26, 1, 11, Kit.log(OAK_LOG, "x")).fill(25, 2, 11, 26, 2, 11, Kit.log(OAK_LOG, "x"));
        Furnish.bench(s, 4, 11, 5, 11, 1, "south", WOOD);
    }
}
