package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Houses of merchants and nobles for the capitals of the kingdoms of the world, each with a silhouette of its own so that a
 * street of the rich is no row of the same box: a merchant's house with a shop under a stepped gable, a town house with a
 * round turret on its corner, a manor behind a walled courtyard and a mansion with a tall tower.
 *
 * <p>They are drawn only with the blocks that stand for the parts of a look (see {@link Kit}), so that every kingdom and
 * every plot gives them its own stone, roof, plaster, cloth and wood. All face south and stand on a raised floor like the
 * other houses: layer 0 is the footing, the street lies under it.
 */
final class RichHouses {
    private static final String SPRUCE_LOG = "minecraft:spruce_log";
    private static final String OAK_LOG = "minecraft:oak_log";
    private static final String SPRUCE_PLANKS = "minecraft:spruce_planks";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String SPRUCE_DOOR = "minecraft:spruce_door";
    private static final String OAK_DOOR = "minecraft:oak_door";
    private static final String BOOKSHELF = "minecraft:bookshelf";
    private static final String CRAFTING = "minecraft:crafting_table";
    private static final String PLATE = "minecraft:oak_pressure_plate[powered=false]";
    private static final String CAMPFIRE = "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]";
    private static final String LIGHTNING_ROD = "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";

    private RichHouses() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(house("classic_house_shop", "minecraft:barrel", shopHouse()).at("door", 4, 0, 11));
        list.add(house("classic_house_turret", "minecraft:lantern", cornerHouse()).at("door", 3, 0, 9));
        list.add(house("classic_house_manor", "minecraft:oak_door", courtyardManor()).at("door", 6, 0, 8).at("gate", 6, 0, 14));
        list.add(house("classic_house_mansion", "minecraft:bell", towerMansion()).at("door", 3, 0, 11));
        return list;
    }

    private static BuildingGenerator.Building house(String name, String icon, Structure structure) {
        return new BuildingGenerator.Building(name, "housing", "house", icon, (structure.sizeX - 1) / 2, 0, structure.sizeZ - 1,
                structure, "house", 1, "");
    }

    // ---------------------------------------------------------------- states

    private static String furnace(String block, String facing) {
        return "minecraft:" + block + "[facing=" + facing + ",lit=false]";
    }

    private static String chair(String facing) {
        return Kit.stairs(SPRUCE_STAIRS, facing);
    }

    // ---------------------------------------------------------------- sides

    private static int stepX(String side) {
        return side.equals("east") ? 1 : side.equals("west") ? -1 : 0;
    }

    private static int stepZ(String side) {
        return side.equals("south") ? 1 : side.equals("north") ? -1 : 0;
    }

    private static String counterClockwise(String side) {
        return switch (side) {
            case "north" -> "west";
            case "west" -> "south";
            case "south" -> "east";
            default -> "north";
        };
    }

    private static String opposite(String side) {
        return switch (side) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    // ---------------------------------------------------------------- stairs

    /**
     * The state of the stairs at a position, or null when none stand there.
     */
    private static String stairsAt(Structure s, int x, int y, int z) {
        if (!s.inside(x, y, z)) {
            return null;
        }
        String state = s.get(x, y, z);
        return States.id(state).endsWith("_stairs") ? state : null;
    }

    private static boolean canTakeShape(Structure s, int x, int y, int z, String facing, String half, String side) {
        String other = stairsAt(s, x + stepX(side), y, z + stepZ(side));
        if (other == null) {
            return true;
        }
        Map<String, String> p = States.properties(other);
        return !facing.equals(p.get("facing")) || !half.equals(p.get("half"));
    }

    /**
     * Gives every flight of stairs the shape the game gives it beside its neighbours: outer corners on hips and turrets,
     * inner corners in valleys. The game works it out again whenever a neighbour changes, so the drawing must agree with it.
     */
    private static void shapeStairs(Structure s) {
        List<int[]> where = new ArrayList<>();
        List<String> shapes = new ArrayList<>();
        for (int y = 0; y < s.sizeY; y++) {
            for (int z = 0; z < s.sizeZ; z++) {
                for (int x = 0; x < s.sizeX; x++) {
                    String state = stairsAt(s, x, y, z);
                    if (state == null) {
                        continue;
                    }
                    Map<String, String> p = States.properties(state);
                    String facing = p.get("facing");
                    String half = p.get("half");
                    String shape = "straight";
                    String front = stairsAt(s, x + stepX(facing), y, z + stepZ(facing));
                    String back = stairsAt(s, x - stepX(facing), y, z - stepZ(facing));
                    Map<String, String> f = front == null ? null : States.properties(front);
                    Map<String, String> b = back == null ? null : States.properties(back);
                    if (f != null && half.equals(f.get("half")) && axisX(f.get("facing")) != axisX(facing)
                            && canTakeShape(s, x, y, z, facing, half, opposite(f.get("facing")))) {
                        shape = f.get("facing").equals(counterClockwise(facing)) ? "outer_left" : "outer_right";
                    } else if (b != null && half.equals(b.get("half")) && axisX(b.get("facing")) != axisX(facing)
                            && canTakeShape(s, x, y, z, facing, half, b.get("facing"))) {
                        shape = b.get("facing").equals(counterClockwise(facing)) ? "inner_left" : "inner_right";
                    }
                    where.add(new int[]{x, y, z});
                    shapes.add(States.with(state, "shape", shape));
                }
            }
        }
        for (int i = 0; i < where.size(); i++) {
            int[] p = where.get(i);
            s.set(p[0], p[1], p[2], shapes.get(i));
        }
    }

    private static boolean axisX(String side) {
        return side.equals("east") || side.equals("west");
    }

    /**
     * A winding stair of slabs around a newel post: every step half a block above the one before, so that eight steps make
     * a turn and a turn climbs four layers. The first step is a bottom slab at {@code y}.
     *
     * @param ring the cells around the post in the order they are climbed
     */
    private static void spiral(Structure s, int[][] ring, int y, int steps, String slab) {
        for (int i = 0; i < steps; i++) {
            int[] cell = ring[i % ring.length];
            s.set(cell[0], y + i / 2, cell[1], i % 2 == 0 ? Kit.slab(slab) : Kit.topSlab(slab));
        }
    }

    /**
     * A lantern hanging on a chain: the chain from {@code top} down, the lantern under it.
     */
    private static void chandelier(Structure s, int x, int z, int lanternY, int top) {
        if (top > lanternY) {
            s.fill(x, lanternY + 1, z, x, top, z, Kit.chain());
        }
        s.set(x, lanternY, z, Kit.hangingLantern());
    }

    /**
     * A small table: a fence post with a pressure plate on it.
     */
    private static void table(Structure s, int x, int y, int z) {
        s.set(x, y, z, Kit.fence("spruce")).set(x, y + 1, z, PLATE);
    }

    // ---------------------------------------------------------------- roofs

    /**
     * Roofs drawn from a field of heights: every roof shape (hip, gable, cone) says how high its surface stands over each
     * column, the highest one wins, and the field is laid as stairs climbing toward the ridge, slabs on the ridges and full
     * blocks under the stairs down to the surface of the lowest neighbour, so that hips, valleys and cones close without
     * holes. Shapes of one field meet in valleys; a roof that hangs over another (the eaves of a cone over a hip) is a
     * field of its own.
     */
    private static final class Roofs {
        private static final int NONE = Integer.MIN_VALUE;
        private final Structure s;
        private final int[][] height;
        private final int[][] eave;
        private final String[][] facing;
        private final Kit.Roof[][] roof;
        private final boolean[][] verge;

        Roofs(Structure s) {
            this.s = s;
            height = new int[s.sizeX][s.sizeZ];
            eave = new int[s.sizeX][s.sizeZ];
            facing = new String[s.sizeX][s.sizeZ];
            roof = new Kit.Roof[s.sizeX][s.sizeZ];
            verge = new boolean[s.sizeX][s.sizeZ];
            for (int[] row : height) {
                java.util.Arrays.fill(row, NONE);
            }
        }

        private void offer(int x, int z, int h, int eaveY, String dir, Kit.Roof r) {
            offer(x, z, h, eaveY, dir, r, false);
        }

        private void offer(int x, int z, int h, int eaveY, String dir, Kit.Roof r, boolean end) {
            if (x < 0 || z < 0 || x >= s.sizeX || z >= s.sizeZ || h <= height[x][z]) {
                return;
            }
            verge[x][z] = end;
            height[x][z] = h;
            eave[x][z] = eaveY;
            facing[x][z] = dir;
            roof[x][z] = r;
        }

        /** A hip roof rising {@code pitch} layers for every block inwards from its eaves at {@code y}. */
        Roofs hip(int x1, int z1, int x2, int z2, int y, int pitch, Kit.Roof r) {
            int rings = 0;
            while (x2 - rings - (x1 + rings) >= 2 && z2 - rings - (z1 + rings) >= 2) {
                rings++;
            }
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    int north = z - z1;
                    int south = z2 - z;
                    int west = x - x1;
                    int east = x2 - x;
                    int d = Math.min(Math.min(north, south), Math.min(west, east));
                    String dir = d >= rings ? null : d == north ? "south" : d == south ? "north" : d == west ? "east" : "west";
                    offer(x, z, y + pitch * Math.min(d, rings), y, dir, r);
                }
            }
            return this;
        }

        /**
         * A gable roof whose ridge runs along z, its gables to the street and the back. With {@code overhang} its end rows
         * hang out over the gables as verges: stairs only, with nothing under them, so that the gable wall behind shows
         * between their steps.
         */
        Roofs gableZ(int x1, int z1, int x2, int z2, int y, int pitch, Kit.Roof r, boolean overhang) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    int west = x - x1;
                    int east = x2 - x;
                    offer(x, z, y + pitch * Math.min(west, east), y, west < east ? "east" : east < west ? "west" : null, r,
                            overhang && (z == z1 || z == z2));
                }
            }
            return this;
        }

        /** A gable roof whose ridge runs along x, as {@link #gableZ} with x and z changing places. */
        Roofs gableX(int x1, int z1, int x2, int z2, int y, int pitch, Kit.Roof r, boolean overhang) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    int north = z - z1;
                    int south = z2 - z;
                    offer(x, z, y + pitch * Math.min(north, south), y, north < south ? "south" : south < north ? "north" : null, r,
                            overhang && (x == x1 || x == x2));
                }
            }
            return this;
        }

        /** A cone over a round plan of an odd width: rings climbing {@code pitch} layers each toward the point. */
        Roofs cone(int cx, int cz, int width, int y, int pitch, Kit.Roof r) {
            int radius = (width - 1) / 2;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (!Kit.inDisc(dx, dz, width)) {
                        continue;
                    }
                    int ring = 0;
                    while (width - 2 * (ring + 1) >= 1 && Kit.inDisc(dx, dz, width - 2 * (ring + 1))) {
                        ring++;
                    }
                    String dir = width - 2 * ring <= 1 ? null : Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "west" : "east")
                            : (dz > 0 ? "north" : "south");
                    offer(cx + dx, cz + dz, y + pitch * ring, y, dir, r);
                }
            }
            return this;
        }

        /** Lays the field. */
        Roofs build() {
            for (int x = 0; x < s.sizeX; x++) {
                for (int z = 0; z < s.sizeZ; z++) {
                    int h = height[x][z];
                    if (h == NONE) {
                        continue;
                    }
                    int low = h;
                    for (int[] n : new int[][]{{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}}) {
                        if (n[0] >= 0 && n[1] >= 0 && n[0] < s.sizeX && n[1] < s.sizeZ && height[n[0]][n[1]] != NONE) {
                            low = Math.min(low, height[n[0]][n[1]]);
                        }
                    }
                    int from = verge[x][z] ? h : Math.min(h, Math.max(eave[x][z], low));
                    for (int y = from; y < h; y++) {
                        s.set(x, y, z, roof[x][z].block());
                    }
                    s.set(x, h, z, facing[x][z] == null ? Kit.slab(roof[x][z].slab()) : Kit.stairs(roof[x][z].stairs(), facing[x][z]));
                }
            }
            return this;
        }

        /**
         * Closes a gable in the plane z = {@code wallZ} from {@code y} up to the stairs of the roof: the blocks of the roof
         * under the stairs in that plane give way to the gable, so that its face shows whole.
         */
        Roofs gableWallZ(int wallZ, int x1, int x2, int y, String state) {
            for (int x = x1; x <= x2; x++) {
                gableColumn(x, wallZ, y, state);
            }
            return this;
        }

        private void gableColumn(int x, int z, int y, String state) {
            if (height[x][z] == NONE) {
                return;
            }
            for (int h = y; h < height[x][z]; h++) {
                if (!s.solid(x, h, z) || s.get(x, h, z).equals(roof[x][z].block())) {
                    s.set(x, h, z, state);
                }
            }
        }

        /** Closes a gable in the plane x = {@code wallX}, as {@link #gableWallZ} does. */
        Roofs gableWallX(int wallX, int z1, int z2, int y, String state) {
            for (int z = z1; z <= z2; z++) {
                gableColumn(wallX, z, y, state);
            }
            return this;
        }
    }

    // ---------------------------------------------------------------- facades

    /**
     * A window in a wall that looks toward {@code out}: panes from {@code y1} to {@code y2}, a lintel over them and, when
     * {@code sill} is true, a sill of trim stairs jutting out under them.
     */
    private static void window(Structure s, int x, int z, int y1, int y2, String out, String lintel, boolean sill) {
        s.fill(x, y1, z, x, y2, z, Kit.GLASS);
        if (lintel != null) {
            s.set(x, y2 + 1, z, lintel);
        }
        if (sill) {
            s.set(x + stepX(out), y1 - 1, z + stepZ(out), Kit.upside(Kit.TRIM_STAIRS, opposite(out)));
        }
    }

    /**
     * Open shutters on either side of a window in a wall that looks toward {@code out}.
     */
    private static void shutters(Structure s, int x, int z, int y1, int y2, String out) {
        int ox = x + stepX(out);
        int oz = z + stepZ(out);
        for (int y = y1; y <= y2; y++) {
            if (axisX(out)) {
                s.set(ox, y, oz - 1, Kit.shutter(Kit.SHUTTER, out)).set(ox, y, oz + 1, Kit.shutter(Kit.SHUTTER, out));
            } else {
                s.set(ox - 1, y, oz, Kit.shutter(Kit.SHUTTER, out)).set(ox + 1, y, oz, Kit.shutter(Kit.SHUTTER, out));
            }
        }
    }

    /**
     * Quoins of rough stone at a corner: the corner itself from {@code y1} to {@code y2}, and on every second layer a long
     * stone reaching one block further into each of the walls named by {@code faces}, as (dx, dz) pairs.
     */
    private static void quoins(Structure s, int x, int z, int y1, int y2, int[]... faces) {
        for (int y = y1; y <= y2; y++) {
            s.set(x, y, z, Kit.ROUGH);
            if ((y - y1) % 2 == 0) {
                for (int[] face : faces) {
                    s.set(x + face[0], y, z + face[1], Kit.ROUGH);
                }
            }
        }
    }

    /**
     * A course of trim stairs hung upside down along the outside of the walls of a box at one height: a string course or a
     * cornice. Cells already taken are left as they are.
     */
    private static void course(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean inside = x >= x1 && x <= x2 && z >= z1 && z <= z2;
                if (inside || !s.inside(x, y, z) || s.solid(x, y, z)) {
                    continue;
                }
                String toward = z < z1 ? "south" : z > z2 ? "north" : x < x1 ? "east" : "west";
                s.set(x, y, z, Kit.upside(Kit.TRIM_STAIRS, toward));
            }
        }
    }

    // ---------------------------------------------------------------- the house with a turret

    /** The middle of the turret of the corner house and its diameter. */
    private static final int TURRET_X = 7;
    private static final int TURRET_Z = 7;
    private static final int TURRET = 5;
    /** The top of the turret wall, under the eaves of its cone. */
    private static final int TURRET_TOP = 21;

    /**
     * A town house of three storeys on the corner of two streets. A ground floor of stone on a rubble footing, two
     * plastered storeys over it, a band of trim jutting out over the stone and a cornice under the eaves, long and short
     * quoins of rubble at the corners and a steep hip roof of shingles. A round turret of stone stands on the front corner:
     * it swells out of both street fronts, holds a winding stair of stone slabs around a newel post, carries the banners of
     * the house and rises high over the eaves to a slate cone with a finial. A dormer of stone with a pediment rises from
     * the front through the eaves; under it an oriel of glass hangs on brackets over the door with a lantern beneath. On
     * the side a balcony opens from the bedrooms under the roof over a flower bed. A chimney of brick stands on the back
     * slope. Inside: a kitchen with its hearth and a dining table below, a parlour with books, a window seat in the oriel
     * and a bed above, two beds under the roof. Three beds.
     */
    static Structure cornerHouse() {
        Structure s = new Structure(11, 30, 11);
        // The footing and the floors.
        s.fill(1, 0, 1, 8, 0, 8, Kit.ROUGH);
        s.fill(2, 0, 2, 7, 0, 7, SPRUCE_PLANKS);
        // Ground floor of stone, plastered storeys above, bands of trim at the floors.
        s.walls(1, 1, 1, 8, 3, 8, Kit.WALL);
        s.walls(1, 4, 1, 8, 4, 8, Kit.TRIM);
        s.walls(1, 5, 1, 8, 7, 8, Kit.PLASTER);
        s.walls(1, 8, 1, 8, 8, 8, Kit.TRIM);
        s.walls(1, 9, 1, 8, 11, 8, Kit.PLASTER);
        s.fill(2, 4, 2, 7, 4, 7, SPRUCE_PLANKS).fill(2, 8, 2, 7, 8, 7, SPRUCE_PLANKS);
        quoins(s, 1, 1, 1, 11, new int[]{1, 0}, new int[]{0, 1});
        quoins(s, 8, 1, 1, 11, new int[]{-1, 0}, new int[]{0, 1});
        quoins(s, 1, 8, 1, 11, new int[]{0, -1});
        for (int y : new int[]{4, 8}) {
            s.set(1, y, 1, Kit.TRIM).set(8, y, 1, Kit.TRIM).set(1, y, 8, Kit.TRIM);
        }
        cornerFront(s);
        cornerSides(s);
        // The roof: a steep hip of shingles.
        new Roofs(s).hip(0, 0, 9, 9, 12, 2, Kit.SHINGLE).build();
        course(s, 1, 1, 8, 8, 4);
        course(s, 1, 1, 8, 8, 11);
        // A dormer of stone rises from the front through the eaves: a window between stone cheeks under a pediment.
        s.fill(2, 12, 9, 5, 12, 9, Structure.AIR);
        s.fill(2, 12, 8, 2, 13, 8, Kit.WALL).fill(4, 12, 8, 4, 13, 8, Kit.WALL);
        s.fill(3, 12, 8, 3, 13, 8, Kit.GLASS).fill(3, 12, 7, 3, 13, 7, Structure.AIR);
        s.set(2, 14, 8, Kit.stairs(Kit.TRIM_STAIRS, "east")).set(4, 14, 8, Kit.stairs(Kit.TRIM_STAIRS, "west"));
        s.set(3, 14, 8, Kit.CARVED).set(3, 15, 8, Kit.slab(Kit.TRIM_SLAB));
        // The chimney stands on the back slope, on a base of the roof itself.
        s.fill(2, 16, 2, 3, 16, 2, Kit.SHINGLE.block());
        s.fill(2, 17, 2, 3, 21, 2, Kit.CHIMNEY);
        s.set(2, 22, 2, Kit.CHIMNEY_POT).set(3, 22, 2, Kit.CHIMNEY_POT);
        cornerTurret(s);
        cornerRooms(s);
        shapeStairs(s);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The turret: its round wall from the footing up past the eaves with bands of trim at the floors, slit windows, a ring
     * of corbels under the eaves of its cone, the winding stair inside around a newel post with a lantern on top.
     */
    private static void cornerTurret(Structure s) {
        int cx = TURRET_X;
        int cz = TURRET_Z;
        s.set(9, 12, 9, Structure.AIR);
        for (int y = 0; y <= TURRET_TOP; y++) {
            Kit.disc(s, cx, y, cz, TURRET - 2, Structure.AIR);
            Kit.tube(s, cx, y, y, cz, TURRET, y <= 1 ? Kit.ROUGH : y == 4 || y == 8 ? Kit.TRIM : Kit.WALL);
        }
        Kit.disc(s, cx, 0, cz, TURRET - 2, Kit.ROUGH);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (Kit.inDisc(dx, dz, TURRET + 2) && !Kit.inDisc(dx, dz, TURRET)) {
                    String toward = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
                    s.set(cx + dx, TURRET_TOP, cz + dz, Kit.upside(Kit.TRIM_STAIRS, toward));
                }
            }
        }
        new Roofs(s).cone(cx, cz, TURRET + 2, TURRET_TOP + 1, 2, Kit.SLATE).build();
        s.set(cx, TURRET_TOP + 7, cz, Kit.SLATE.block()).set(cx, TURRET_TOP + 8, cz, LIGHTNING_ROD);
        // The winding stair: a turn to every storey, landings on the west side.
        int[][] ring = {{6, 6}, {7, 6}, {8, 6}, {8, 7}, {8, 8}, {7, 8}, {6, 8}, {6, 7}};
        s.fill(cx, 1, cz, cx, 11, cz, SPRUCE_LOG + "[axis=y]");
        spiral(s, ring, 1, 16, Kit.TRIM_SLAB);
        s.fill(5, 1, 6, 5, 2, 6, Structure.AIR);
        s.fill(5, 5, 7, 5, 6, 7, Structure.AIR).fill(5, 9, 7, 5, 10, 7, Structure.AIR);
        s.set(cx, 12, cz, Kit.lantern());
        chandelier(s, cx, cz, TURRET_TOP, TURRET_TOP + 4);
        for (int y : new int[]{2, 6, 10, 14, 18}) {
            s.fill(9, y, 7, 9, y + 1, 7, Kit.GLASS).fill(7, y, 9, 7, y + 1, 9, Kit.GLASS);
        }
        s.fill(5, 19, 7, 5, 20, 7, Kit.GLASS).fill(7, 19, 5, 7, 20, 5, Kit.GLASS);
        // Banners of the house hang on the two street faces of the turret.
        s.set(7, 13, 10, Kit.wallBanner("red", "south")).set(10, 13, 7, Kit.wallBanner("red", "east"));
    }

    /**
     * The front: the door under the oriel with a window on either side, the oriel of glass on brackets with a lantern
     * under it and a little roof over it, two windows over the oriel.
     */
    private static void cornerFront(Structure s) {
        s.set(3, 0, 9, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.set(2, 0, 9, Kit.slab(Kit.TRIM_SLAB)).set(4, 0, 9, Kit.slab(Kit.TRIM_SLAB));
        s.door(3, 1, 8, SPRUCE_DOOR, "north", "left");
        s.set(3, 3, 8, Kit.CARVED);
        window(s, 2, 8, 1, 2, "south", Kit.TRIM, false);
        window(s, 4, 8, 1, 2, "south", Kit.TRIM, false);
        // The oriel: open to the parlour behind it.
        s.fill(2, 5, 8, 4, 7, 8, Structure.AIR);
        s.fill(2, 4, 8, 4, 4, 9, SPRUCE_PLANKS);
        s.set(2, 3, 9, Kit.upside(SPRUCE_STAIRS, "north")).set(4, 3, 9, Kit.upside(SPRUCE_STAIRS, "north"));
        s.set(3, 3, 9, Kit.hangingLantern());
        s.fill(2, 5, 9, 4, 6, 9, "minecraft:glass");
        s.fill(2, 7, 9, 4, 7, 9, SPRUCE_PLANKS);
        s.fill(2, 8, 9, 4, 8, 9, Kit.stairs(Kit.SHINGLE.stairs(), "north"));
        for (int x : new int[]{2, 4}) {
            window(s, x, 8, 9, 10, "south", Kit.TRIM, false);
        }
    }

    /**
     * The side and back walls: framed windows on every storey, shutters on the first floor, the balcony on brackets before
     * the bedrooms and a flower bed under it.
     */
    private static void cornerSides(Structure s) {
        for (int[] floor : new int[][]{{1, 2}, {5, 6}, {9, 10}}) {
            int y = floor[0];
            for (int z : new int[]{3, 6}) {
                window(s, 1, z, y, floor[1], "west", Kit.TRIM, false);
            }
            for (int x : y == 1 ? new int[]{6} : new int[]{3, 6}) {
                window(s, x, 1, y, floor[1], "north", Kit.TRIM, false);
            }
        }
        for (int z : new int[]{3, 6}) {
            shutters(s, 1, z, 5, 6, "west");
        }
        for (int x : new int[]{3, 6}) {
            shutters(s, x, 1, 5, 6, "north");
        }
        // East side: windows below, the balcony door above.
        window(s, 8, 3, 1, 2, "east", Kit.TRIM, false);
        window(s, 8, 3, 5, 6, "east", Kit.TRIM, false);
        shutters(s, 8, 3, 5, 6, "east");
        s.door(8, 9, 3, SPRUCE_DOOR, "east", "left");
        s.set(8, 11, 3, Kit.TRIM);
        s.fill(9, 8, 2, 10, 8, 4, Kit.topSlab(Kit.TRIM_SLAB));
        s.set(9, 7, 2, Kit.upside(Kit.TRIM_STAIRS, "west")).set(9, 7, 4, Kit.upside(Kit.TRIM_STAIRS, "west"));
        s.set(9, 7, 3, Kit.upside(Kit.TRIM_STAIRS, "west"));
        s.fill(10, 9, 2, 10, 9, 4, Kit.fence("spruce")).set(9, 9, 2, Kit.fence("spruce")).set(9, 9, 4, Kit.fence("spruce"));
        s.set(10, 10, 2, Kit.lantern()).set(10, 10, 4, Kit.lantern());
        // A flower bed along the east side, edged with trim.
        s.fill(9, 0, 1, 10, 0, 1, Kit.TRIM).fill(9, 0, 5, 10, 0, 5, Kit.TRIM);
        s.fill(9, 0, 2, 10, 0, 4, GRASS);
        s.set(10, 1, 2, "minecraft:poppy").set(9, 1, 3, "minecraft:azure_bluet").set(10, 1, 4, "minecraft:cornflower");
        s.set(10, 1, 3, Kit.leaves("azalea")).set(9, 1, 2, "minecraft:allium").set(9, 1, 4, "minecraft:oxeye_daisy");
    }

    /**
     * The rooms: kitchen and dining table below, the parlour with its books, window seat and a bed, two beds under the roof.
     */
    private static void cornerRooms(Structure s) {
        // Ground floor: the hearth, stores, a table with chairs.
        s.set(2, 1, 2, furnace("smoker", "south")).set(3, 1, 2, CAMPFIRE).set(4, 1, 2, furnace("furnace", "south"));
        s.fill(2, 2, 2, 4, 3, 2, Kit.ROUGH).set(3, 2, 2, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(5, 1, 2, Kit.barrel()).set(7, 1, 2, Kit.barrel()).set(7, 2, 2, Kit.barrel()).set(7, 1, 3, Kit.chest("west"));
        s.set(7, 1, 4, CRAFTING);
        s.fill(3, 1, 4, 4, 1, 4, Kit.topSlab(SPRUCE_SLAB));
        s.set(2, 1, 4, chair("west")).set(5, 1, 4, chair("east"));
        s.set(3, 1, 5, chair("south")).set(4, 1, 5, chair("south"));
        s.set(2, 1, 7, Kit.barrel());
        s.set(3, 3, 3, Kit.hangingLantern()).set(6, 3, 3, Kit.hangingLantern()).set(3, 3, 6, Kit.hangingLantern());
        // First floor: the parlour with its window seat, books and a bed.
        s.fill(2, 5, 4, 2, 6, 5, BOOKSHELF);
        s.fill(3, 5, 3, 4, 5, 6, Kit.CLOTH_CARPET);
        table(s, 3, 5, 4);
        s.set(4, 5, 4, chair("east"));
        s.fill(2, 5, 8, 4, 5, 8, Kit.stairs(SPRUCE_STAIRS, "north"));
        s.bed(6, 5, 3, "minecraft:blue_bed", "north", 0, -1);
        s.set(7, 5, 2, Kit.chest("west")).set(7, 5, 4, BOOKSHELF).set(5, 5, 2, BOOKSHELF);
        s.set(4, 7, 4, Kit.hangingLantern()).set(6, 7, 4, Kit.hangingLantern());
        // Under the roof: two beds and their chests.
        s.bed(2, 9, 3, "minecraft:light_blue_bed", "north", 0, -1).bed(3, 9, 3, "minecraft:light_blue_bed", "north", 0, -1);
        s.set(4, 9, 2, Kit.chest("south")).set(2, 9, 4, Kit.chest("east")).set(7, 9, 2, BOOKSHELF).set(7, 9, 4, Kit.barrel());
        s.fill(3, 9, 5, 4, 9, 6, Kit.CLOTH_CARPET);
        chandelier(s, 4, 4, 13, 18);
        s.set(6, 15, 5, Kit.hangingLantern());
    }

    // ---------------------------------------------------------------- the merchant's house

    /** The tops of the steps of the front gable of the merchant's house, one for every column from the west. */
    private static final int[] STEPS = {14, 16, 18, 20, 22, 20, 18, 16, 14};

    /**
     * A tall narrow merchant's house whose stepped gable of stone looks at the street, as in the trading towns of the
     * north. The ground floor is the shop: two wide windows of glass full of bolts of cloth and barrels, a door between
     * them under a carved keystone, and before them a striped awning of the colour of the kingdom on two posts with a
     * paved apron under it. Over the awning hang flower boxes, a sign banner on a beam at the corner and, a storey higher,
     * a balcony on brackets across the front. The gable climbs in steps capped with trim, its piers of stone and its
     * panels of plaster; at the top a hoist beam juts out over the loft door with a barrel hanging on its chain. A chimney
     * of brick runs up the back gable. Inside: the counter, the stores under the stairs and the goods on display below,
     * the kitchen hearth and the dining table above, three beds under the roof and a loft for goods. Three beds.
     */
    static Structure shopHouse() {
        Structure s = new Structure(9, 24, 13);
        // The footing, the floors and the walls of stone with bands of trim at the floors.
        s.fill(0, 0, 1, 8, 0, 10, Kit.ROUGH);
        s.fill(1, 0, 2, 7, 0, 9, SPRUCE_PLANKS);
        s.walls(0, 1, 1, 8, 11, 10, Kit.WALL);
        s.walls(0, 5, 1, 8, 5, 10, Kit.TRIM).walls(0, 9, 1, 8, 9, 10, Kit.TRIM);
        s.fill(1, 5, 2, 7, 5, 9, SPRUCE_PLANKS).fill(1, 9, 2, 7, 9, 9, SPRUCE_PLANKS);
        for (int[] corner : new int[][]{{0, 1}, {8, 1}, {0, 10}, {8, 10}}) {
            s.fill(corner[0], 1, corner[1], corner[0], 4, corner[1], Kit.ROUGH);
        }
        // The roof: a steep gable of tiles from the back gable to the stepped front.
        Roofs roofs = new Roofs(s).gableZ(0, 1, 8, 9, 12, 2, Kit.TILE, false).build();
        roofs.gableWallZ(1, 0, 8, 12, Kit.WALL);
        s.fill(2, 13, 2, 6, 13, 9, SPRUCE_PLANKS);
        shopGable(s);
        shopFront(s);
        shopSides(s);
        shopRooms(s);
        shapeStairs(s);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The stepped gable: piers of stone and panels of plaster climbing in steps capped with trim slabs, two windows and the
     * loft door under the hoist beam, a carved top with a finial.
     */
    private static void shopGable(Structure s) {
        s.fill(0, 12, 10, 8, 12, 10, Kit.TRIM);
        for (int x = 0; x < STEPS.length; x++) {
            boolean pier = x % 2 == 0;
            s.fill(x, 13, 10, x, STEPS[x] - 1, 10, pier ? Kit.WALL : Kit.PLASTER);
            s.set(x, STEPS[x], 10, Kit.slab(Kit.TRIM_SLAB));
        }
        // The piers of the gable stand forward by a block over the band, so that the plaster lies in niches.
        for (int x : new int[]{2, 6}) {
            s.fill(x, 13, 11, x, STEPS[x] - 2, 11, Kit.WALL);
            s.set(x, STEPS[x] - 1, 11, Kit.slab(Kit.TRIM_SLAB));
        }
        s.fill(4, 13, 10, 4, 20, 10, Kit.WALL);
        s.set(4, 21, 10, Kit.CARVED).set(4, 22, 10, Kit.slab(Kit.TRIM_SLAB)).set(4, 23, 10, LIGHTNING_ROD);
        s.fill(4, 18, 10, 4, 19, 10, Kit.PLASTER);
        // The loft door and its windows, the hoist beam with a barrel on its chain.
        s.door(4, 14, 10, OAK_DOOR, "north", "left");
        s.set(4, 16, 10, Kit.CARVED);
        s.fill(3, 14, 10, 3, 15, 10, Kit.GLASS).fill(5, 14, 10, 5, 15, 10, Kit.GLASS);
        s.set(4, 17, 10, SPRUCE_LOG + "[axis=z]").set(4, 17, 11, SPRUCE_LOG + "[axis=z]").set(4, 17, 12, SPRUCE_LOG + "[axis=z]");
        s.set(4, 16, 12, Kit.chain()).set(4, 15, 12, Kit.chain()).set(4, 14, 12, Kit.barrel());
    }

    /**
     * The shop front and the storeys over it: the shop windows, the door, the awning on its posts, the apron with goods,
     * flower boxes, the sign banner on its beam, framed windows and the balcony.
     */
    private static void shopFront(Structure s) {
        // The shop: two wide windows, the door between them with a light over it.
        s.fill(1, 1, 10, 3, 3, 10, Kit.GLASS).fill(5, 1, 10, 7, 3, 10, Kit.GLASS);
        s.door(4, 1, 10, SPRUCE_DOOR, "north", "left");
        s.set(4, 3, 10, Kit.GLASS).set(4, 4, 10, Kit.CARVED);
        s.fill(1, 4, 10, 3, 4, 10, Kit.TRIM).fill(5, 4, 10, 7, 4, 10, Kit.TRIM);
        // The apron under the awning, the step, goods set out by the windows.
        s.fill(0, 0, 11, 8, 0, 11, Kit.slab(Kit.TRIM_SLAB));
        s.set(4, 0, 11, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.set(1, 0, 11, Kit.barrel()).set(7, 0, 11, Kit.barrel()).set(7, 1, 11, "minecraft:hay_block[axis=y]");
        // The striped awning: wool of the colour of the kingdom and white, falling from the wall to its posts.
        for (int x = 0; x <= 8; x++) {
            String wool = x % 2 == 0 ? Kit.CLOTH : "minecraft:white_wool";
            s.set(x, 4, 11, wool).set(x, 3, 12, wool);
        }
        s.fill(0, 0, 12, 0, 2, 12, Kit.fence("spruce")).fill(8, 0, 12, 8, 2, 12, Kit.fence("spruce"));
        s.set(0, 2, 11, Kit.wallTorch("south"));
        // The sign: a beam out of the corner with the banner of the house hanging from it.
        s.set(8, 6, 11, SPRUCE_LOG + "[axis=z]").set(8, 5, 11, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(8, 6, 12, Kit.wallBanner("red", "south"));
        // First floor: three framed windows with flower boxes over the awning.
        for (int[] w : new int[][]{{1, 2}, {4, 4}, {6, 7}}) {
            s.fill(w[0], 6, 10, w[1], 7, 10, Kit.GLASS);
            s.fill(w[0], 8, 10, w[1], 8, 10, Kit.TRIM);
        }
        s.set(4, 8, 10, Kit.CARVED);
        for (int x : new int[]{1, 2, 6, 7}) {
            s.set(x, 5, 11, "minecraft:podzol[snowy=false]").set(x, 5, 12, Kit.shutter(Kit.SHUTTER, "south"));
        }
        s.set(1, 6, 11, "minecraft:poppy").set(2, 6, 11, "minecraft:dandelion");
        s.set(6, 6, 11, "minecraft:dandelion").set(7, 6, 11, "minecraft:poppy");
        // Second floor: the balcony on brackets across the front, its door and two windows.
        s.door(4, 10, 10, SPRUCE_DOOR, "north", "left");
        s.fill(2, 10, 10, 2, 11, 10, Kit.GLASS).fill(6, 10, 10, 6, 11, 10, Kit.GLASS);
        s.fill(2, 9, 11, 6, 9, 12, Kit.topSlab(Kit.TRIM_SLAB));
        for (int x : new int[]{2, 4, 6}) {
            s.set(x, 8, 11, Kit.upside(Kit.TRIM_STAIRS, "north"));
        }
        s.fill(2, 10, 12, 6, 10, 12, Kit.fence("spruce")).set(2, 10, 11, Kit.fence("spruce")).set(6, 10, 11, Kit.fence("spruce"));
        s.set(2, 11, 12, Kit.lantern()).set(6, 11, 12, Kit.lantern());
    }

    /**
     * The side and back walls: windows on every storey, the chimney up the back gable.
     */
    private static void shopSides(Structure s) {
        for (int x : new int[]{0, 8}) {
            String out = x == 0 ? "west" : "east";
            // Ground floor: stone with windows.
            for (int z : new int[]{3, 6, 8}) {
                s.fill(x, 2, z, x, 3, z, Kit.GLASS);
                s.set(x, 4, z, Kit.TRIM);
            }
            // Upper storeys: piers of stone and panels of plaster, as on the gable, with paired windows in the panels.
            for (int z = 2; z <= 9; z++) {
                if (z != 4 && z != 7) {
                    s.fill(x, 6, z, x, 8, z, Kit.PLASTER).fill(x, 10, z, x, 11, z, Kit.PLASTER);
                }
            }
            for (int[] pair : x == 0 ? new int[][]{{8, 9}} : new int[][]{{2, 3}, {5, 6}, {8, 9}}) {
                s.fill(x, 6, pair[0], x, 7, pair[1], Kit.GLASS);
            }
            for (int[] pair : new int[][]{{2, 3}, {5, 6}, {8, 9}}) {
                s.fill(x, 10, pair[0], x, 10, pair[1], Kit.GLASS);
            }
        }
        window(s, 4, 1, 6, 7, "north", Kit.TRIM, false);
        window(s, 4, 1, 10, 11, "north", Kit.TRIM, false);
        s.set(4, 14, 1, Kit.GLASS).set(2, 2, 1, Kit.GLASS).set(2, 3, 1, Kit.GLASS);
        // The chimney of the kitchen hearth, up the back gable.
        s.fill(6, 0, 0, 6, 19, 0, Kit.CHIMNEY);
        s.set(6, 20, 0, Kit.CHIMNEY_POT);
    }

    /**
     * The rooms: the shop with its counter, displays and stores under the stairs; the kitchen and dining room; the
     * bedrooms; the loft.
     */
    private static void shopRooms(Structure s) {
        // The stairs up from the shop along the back wall, rising to the west; stores under them.
        for (int k = 0; k < 5; k++) {
            s.set(7 - k, 1 + k, 2, Kit.stairs(SPRUCE_STAIRS, "west"));
        }
        s.set(6, 1, 2, Kit.barrel()).fill(5, 1, 2, 5, 2, 2, Kit.barrel()).fill(4, 1, 2, 4, 3, 2, Kit.barrel());
        s.fill(3, 1, 2, 3, 4, 2, Kit.barrel()).set(4, 2, 2, Kit.chest("south"));
        s.fill(4, 5, 2, 5, 5, 2, Structure.AIR);
        // The counter, the merchant's chests behind it, goods in the windows.
        s.fill(1, 1, 6, 5, 1, 6, Kit.upside(SPRUCE_STAIRS, "north"));
        s.set(2, 2, 6, Kit.lantern());
        s.set(1, 1, 3, Kit.chest("east")).set(1, 1, 4, Kit.barrel()).set(1, 1, 5, Kit.chest("east")).set(1, 2, 4, Kit.barrel());
        s.set(7, 1, 4, Kit.barrel()).set(7, 1, 5, Kit.barrel()).set(7, 2, 4, Kit.barrel());
        s.set(1, 1, 9, "minecraft:white_wool").set(2, 1, 9, "minecraft:yellow_wool").set(1, 2, 9, "minecraft:light_blue_wool");
        s.set(3, 1, 9, Kit.barrel()).set(2, 2, 9, "minecraft:white_carpet");
        s.set(5, 1, 9, Kit.barrel()).set(6, 1, 9, "minecraft:lime_wool").set(7, 1, 9, "minecraft:orange_wool");
        s.set(7, 2, 9, "minecraft:pink_wool").set(6, 2, 9, "minecraft:cyan_carpet");
        s.set(3, 4, 8, Kit.hangingLantern()).set(6, 4, 7, Kit.hangingLantern()).set(6, 4, 4, Kit.hangingLantern());
        // First floor: the stairs on to the bedrooms along the west wall on a case of books; the hearth; the table.
        for (int k = 0; k < 4; k++) {
            s.set(1, 6 + k, 3 + k, Kit.stairs(SPRUCE_STAIRS, "south"));
            if (k > 0) {
                s.fill(1, 6, 3 + k, 1, 5 + k, 3 + k, BOOKSHELF);
            }
        }
        s.fill(1, 9, 4, 1, 9, 5, Structure.AIR);
        s.fill(3, 6, 3, 5, 6, 3, Kit.fence("spruce"));
        s.set(6, 6, 2, CAMPFIRE).set(7, 6, 2, furnace("smoker", "south"));
        s.fill(6, 7, 2, 7, 8, 2, Kit.ROUGH).set(6, 7, 2, Kit.upside(Kit.TRIM_STAIRS, "south"));
        s.set(7, 6, 3, furnace("furnace", "west")).set(7, 6, 4, Kit.barrel()).set(7, 6, 5, CRAFTING);
        s.fill(2, 6, 5, 6, 6, 8, Kit.CLOTH_CARPET);
        s.fill(3, 6, 6, 5, 6, 7, Kit.topSlab(SPRUCE_SLAB));
        for (int z : new int[]{6, 7}) {
            s.set(2, 6, z, chair("west")).set(6, 6, z, chair("east"));
        }
        s.set(4, 7, 6, Kit.lantern());
        s.set(7, 6, 7, BOOKSHELF).set(7, 7, 7, BOOKSHELF).set(7, 6, 6, Kit.chest("west"));
        s.set(4, 8, 4, Kit.hangingLantern()).set(4, 8, 8, Kit.hangingLantern());
        // Second floor: three beds along the back wall, chests, a sitting place by the balcony door.
        s.fill(2, 10, 4, 2, 10, 6, Kit.fence("spruce"));
        s.bed(3, 10, 3, "minecraft:white_bed", "north", 0, -1).bed(4, 10, 3, "minecraft:white_bed", "north", 0, -1);
        s.bed(6, 10, 3, "minecraft:yellow_bed", "north", 0, -1);
        for (int y = 10; y <= 13; y++) {
            s.set(5, y, 2, Kit.ladder("south"));
        }
        s.set(2, 10, 2, Kit.chest("south")).set(2, 10, 3, Kit.barrel()).set(7, 10, 2, Kit.chest("west")).set(7, 10, 3, BOOKSHELF);
        s.fill(3, 10, 6, 5, 10, 8, Kit.CLOTH_CARPET);
        table(s, 4, 10, 7);
        s.set(3, 10, 7, chair("west")).set(5, 10, 7, chair("east"));
        s.set(7, 10, 7, BOOKSHELF).set(7, 10, 9, Kit.barrel());
        s.set(3, 12, 5, Kit.hangingLantern()).set(5, 12, 8, Kit.hangingLantern());
        // The loft: goods hoisted in through the loft door.
        s.fill(3, 14, 3, 3, 14, 5, Kit.barrel()).set(3, 15, 3, Kit.barrel());
        s.fill(3, 14, 7, 3, 14, 8, "minecraft:hay_block[axis=x]").set(5, 14, 6, Kit.chest("west")).set(5, 14, 7, Kit.chest("west"));
        s.set(4, 17, 5, Kit.hangingLantern());
    }

    // ---------------------------------------------------------------- the manor with a courtyard

    /**
     * A lantern hanging at {@code y} on a chain that reaches up to the first block above it.
     */
    private static void pendant(Structure s, int x, int y, int z) {
        int top = y + 1;
        while (top < s.sizeY && !s.solid(x, top, z)) {
            top++;
        }
        if (top < s.sizeY && top - 1 >= y + 1) {
            s.fill(x, y + 1, z, x, top - 1, z, Kit.chain());
        }
        s.set(x, y, z, Kit.hangingLantern());
    }

    /**
     * A small tree for a courtyard: a trunk of oak, a crown of leaves that never fall and a light hidden in the crown.
     */
    private static void yardTree(Structure s, int x, int z) {
        s.fill(x, 1, z, x, 2, z, OAK_LOG + "[axis=y]");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) < 2) {
                    s.set(x + dx, 3, z + dz, Kit.leaves("oak"));
                }
            }
        }
        s.set(x, 3, z, "minecraft:shroomlight").set(x, 4, z, Kit.leaves("oak"));
    }

    /**
     * A manor of a noble family behind a walled courtyard. The house stands at the back: two tall storeys of stone on a
     * rubble footing, quoins of rubble, a band of trim between the storeys and a cornice under a steep hip roof of
     * blue-green shingles with a chimney of brick at either end of its ridge. Its middle bay steps forward under a steep
     * gable of its own whose ridge runs back into the hip, with a window in the gable; before its door a porch of two
     * carved columns carries a little pediment, and the banners of the family hang on the corners of the bay. The tall
     * windows have lintels with carved keystones and sills of trim. The courtyard is closed to the street by a wall with a
     * coping of trim and an arched gate between two piers, a lantern on each; inside, paths of trim around a small
     * fountain basin, flower beds, lamps and two small trees with a light in their crowns. Inside: a hall with the stairs,
     * the dining table and the kitchen below, two bedchambers and a sitting room in the bay above. Four beds.
     */
    static Structure courtyardManor() {
        Structure s = new Structure(13, 20, 15);
        // The footing and the floors.
        s.fill(1, 0, 1, 11, 0, 6, Kit.ROUGH).fill(4, 0, 6, 8, 0, 7, Kit.ROUGH);
        s.fill(2, 0, 2, 10, 0, 5, SPRUCE_PLANKS).fill(5, 0, 6, 7, 0, 6, SPRUCE_PLANKS);
        // Two storeys of stone, the middle bay one block forward, a band of trim between them.
        s.walls(1, 1, 1, 11, 9, 6, Kit.WALL).walls(4, 1, 6, 8, 9, 7, Kit.WALL);
        s.fill(5, 1, 6, 7, 9, 6, Structure.AIR);
        for (int[] q : new int[][]{{1, 1}, {11, 1}, {1, 6}, {11, 6}, {4, 7}, {8, 7}}) {
            quoins(s, q[0], q[1], 1, 9);
        }
        s.walls(1, 5, 1, 11, 5, 6, Kit.TRIM).walls(4, 5, 6, 8, 5, 7, Kit.TRIM);
        s.fill(2, 5, 2, 10, 5, 5, SPRUCE_PLANKS).fill(5, 5, 6, 7, 5, 6, SPRUCE_PLANKS);
        manorWindows(s);
        // The roofs: a steep hip and the gable of the bay running back into it.
        Roofs roofs = new Roofs(s).hip(0, 0, 12, 7, 10, 2, Kit.TEAL).gableZ(3, 3, 9, 8, 10, 2, Kit.TEAL, true).build();
        roofs.gableWallZ(7, 4, 8, 10, Kit.WALL);
        s.fill(6, 11, 7, 6, 12, 7, Kit.GLASS).set(6, 13, 7, Kit.CARVED).set(5, 11, 7, Kit.TRIM).set(7, 11, 7, Kit.TRIM);
        s.set(6, 10, 8, Kit.upside(Kit.TRIM_STAIRS, "north"));
        course(s, 1, 1, 11, 6, 5);
        course(s, 1, 1, 11, 6, 9);
        course(s, 4, 6, 8, 7, 9);
        // Two chimneys on the roof, one at either end of the ridge.
        for (int x : new int[]{2, 10}) {
            s.fill(x, 14, 3, x, 14, 4, Kit.TEAL.block());
            s.fill(x, 15, 3, x, 18, 4, Kit.CHIMNEY);
            s.set(x, 19, 3, Kit.CHIMNEY_POT).set(x, 19, 4, Kit.CHIMNEY_POT);
        }
        manorPorch(s);
        manorCourtyard(s);
        manorRooms(s);
        shapeStairs(s);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * Tall windows of both storeys: lintels of trim with carved keystones, sills of trim on the upper storey.
     */
    private static void manorWindows(Structure s) {
        for (int[] floor : new int[][]{{1, 3}, {6, 8}}) {
            int y1 = floor[0];
            int y2 = floor[1];
            boolean upper = y1 > 1;
            for (int x : new int[]{2, 10}) {
                window(s, x, 6, y1, y2, "south", Kit.CARVED, upper);
                if (upper || x == 2) {
                    window(s, x, 1, y1, y2, "north", Kit.CARVED, upper);
                }
            }
            s.fill(6, y1, 1, 6, y2, 1, Kit.GLASS);
            for (int x : upper ? new int[]{5, 6, 7} : new int[]{5, 7}) {
                window(s, x, 7, y1, y2, "south", x == 6 ? Kit.CARVED : Kit.TRIM, upper);
            }
            if (upper) {
                window(s, 6, 1, y1, y2, "north", Kit.CARVED, true);
            }
            for (int z : new int[]{3, 4}) {
                window(s, 1, z, y1, y2, "west", Kit.TRIM, upper);
                window(s, 11, z, y1, y2, "east", Kit.TRIM, upper);
            }
        }
    }

    /**
     * The porch before the door: two carved columns with capitals of trim carrying an entablature and a little pediment,
     * a lantern hanging under it, the step up to the door.
     */
    private static void manorPorch(Structure s) {
        s.door(6, 1, 7, SPRUCE_DOOR, "north", "left");
        s.set(6, 3, 7, Kit.GLASS).set(6, 4, 7, Kit.CARVED);
        for (int x : new int[]{5, 7}) {
            s.fill(x, 0, 8, x, 2, 8, Kit.CARVED);
            s.set(x, 3, 8, Kit.TRIM);
        }
        s.set(6, 0, 8, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.fill(5, 4, 8, 7, 4, 8, Kit.TRIM);
        s.set(5, 5, 8, Kit.stairs(Kit.TRIM_STAIRS, "east")).set(7, 5, 8, Kit.stairs(Kit.TRIM_STAIRS, "west"));
        s.set(6, 5, 8, Kit.CARVED).set(6, 6, 8, Kit.slab(Kit.TRIM_SLAB));
        s.set(4, 5, 8, Kit.slab(Kit.TRIM_SLAB)).set(8, 5, 8, Kit.slab(Kit.TRIM_SLAB));
        s.set(6, 3, 8, Kit.hangingLantern());
        // Banners of the family on the corners of the bay.
        s.set(4, 8, 8, Kit.wallBanner("red", "south")).set(8, 8, 8, Kit.wallBanner("red", "south"));
    }

    /**
     * The courtyard: its wall with a coping of trim, the arched gate between piers with lanterns, paths around the basin of
     * the fountain, the flower beds with their trees and lamps.
     */
    private static void manorCourtyard(Structure s) {
        // The wall: from the corners of the house to the street and along it.
        for (int[] run : new int[][]{{0, 6, 0, 14}, {12, 6, 12, 14}, {0, 14, 12, 14}}) {
            s.fill(run[0], 0, run[1], run[2], 0, run[3], Kit.ROUGH);
            s.fill(run[0], 1, run[1], run[2], 1, run[3], Kit.WALL);
            s.fill(run[0], 2, run[1], run[2], 2, run[3], Kit.slab(Kit.TRIM_SLAB));
        }
        for (int[] pier : new int[][]{{0, 14}, {12, 14}, {0, 10}, {12, 10}}) {
            s.fill(pier[0], 1, pier[1], pier[0], 2, pier[1], Kit.WALL);
            s.set(pier[0], 3, pier[1], Kit.slab(Kit.TRIM_SLAB));
        }
        // The gate: an arch between two piers, a carved keystone, a lantern on each pier.
        s.fill(5, 0, 14, 7, 2, 14, Structure.AIR);
        s.fill(5, 0, 14, 7, 0, 14, Kit.slab(Kit.TRIM_SLAB));
        s.set(5, 3, 14, Kit.upside(Kit.WALL_STAIRS, "west")).set(7, 3, 14, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(6, 3, 14, Kit.CARVED);
        s.fill(5, 4, 14, 7, 4, 14, Kit.slab(Kit.TRIM_SLAB)).set(6, 4, 14, Kit.TRIM).set(6, 5, 14, Kit.slab(Kit.TRIM_SLAB));
        for (int x : new int[]{4, 8}) {
            s.fill(x, 0, 14, x, 3, 14, Kit.WALL);
            s.set(x, 4, 14, Kit.CARVED).set(x, 5, 14, Kit.lantern());
            s.set(x, 3, 13, Kit.wallTorch("north"));
        }
        // The ground of the yard: grass in the beds, paths of trim around the basin and to the gate.
        s.fill(1, 0, 9, 11, 0, 13, GRASS).fill(1, 0, 8, 3, 0, 8, GRASS).fill(9, 0, 8, 11, 0, 8, GRASS);
        s.fill(4, 0, 9, 8, 0, 13, Kit.slab(Kit.TRIM_SLAB));
        s.set(4, 0, 8, Kit.slab(Kit.TRIM_SLAB)).set(8, 0, 8, Kit.slab(Kit.TRIM_SLAB));
        s.fill(1, 0, 7, 3, 0, 7, Kit.slab(Kit.TRIM_SLAB)).fill(9, 0, 7, 11, 0, 7, Kit.slab(Kit.TRIM_SLAB));
        // The fountain: a basin of trim on a rubble base with water in the middle.
        s.fill(5, 0, 10, 7, 0, 12, Kit.ROUGH);
        s.fill(5, 1, 10, 7, 1, 12, Kit.TRIM);
        s.set(6, 1, 11, "minecraft:water[level=0]");
        for (int[] c : new int[][]{{5, 10}, {7, 10}, {5, 12}, {7, 12}}) {
            s.set(c[0], 2, c[1], Kit.slab(Kit.TRIM_SLAB));
        }
        // The beds: two small trees, flowers, a lamp at the house.
        yardTree(s, 2, 11);
        yardTree(s, 10, 11);
        String[] flowers = {"minecraft:poppy", "minecraft:oxeye_daisy", "minecraft:allium", "minecraft:cornflower", "minecraft:azure_bluet"};
        int f = 0;
        for (int[] c : new int[][]{{1, 8}, {3, 8}, {2, 9}, {1, 13}, {3, 13}, {9, 8}, {11, 8}, {10, 9}, {9, 13}, {11, 13}, {3, 10}, {9, 10}}) {
            s.set(c[0], 1, c[1], flowers[f++ % flowers.length]);
        }
        for (int x : new int[]{2, 10}) {
            s.fill(x, 1, 8, x, 2, 8, Kit.fence("spruce"));
            s.set(x, 3, 8, Kit.lantern());
        }
    }

    /**
     * The rooms: below, one hall with the stairs, the dining table on one side and the kitchen on the other; above, two
     * bedchambers with two beds each and a sitting room over the hall.
     */
    private static void manorRooms(Structure s) {
        // The stairs in the hall, rising to the back; the hall passes under their upper flight.
        for (int k = 0; k < 5; k++) {
            s.set(5, 1 + k, 6 - k, Kit.stairs(SPRUCE_STAIRS, "north"));
        }
        s.set(5, 1, 5, SPRUCE_PLANKS).fill(5, 1, 3, 5, 3, 3, SPRUCE_PLANKS).fill(5, 1, 2, 5, 4, 2, SPRUCE_PLANKS);
        s.fill(5, 5, 3, 5, 5, 4, Structure.AIR);
        s.fill(6, 6, 3, 6, 6, 4, Kit.fence("spruce"));
        // The hall: a runner of the colour of the house from the door, a chest and a bench.
        s.fill(6, 1, 2, 6, 1, 6, Kit.CLOTH_CARPET);
        s.set(7, 1, 2, Kit.chest("south")).set(7, 1, 5, Kit.stairs(SPRUCE_STAIRS, "east"));
        pendant(s, 6, 4, 4);
        // The dining table on the west side, its chairs and a dresser of books.
        s.fill(3, 1, 3, 3, 1, 4, Kit.topSlab(SPRUCE_SLAB));
        for (int z : new int[]{3, 4}) {
            s.set(2, 1, z, chair("west")).set(4, 1, z, chair("east"));
        }
        s.set(3, 2, 3, Kit.lantern());
        s.fill(2, 1, 2, 3, 2, 2, BOOKSHELF).set(2, 1, 5, Kit.barrel());
        pendant(s, 3, 4, 4);
        // The kitchen on the east side: the hearth against the back wall, stores and a working table.
        s.set(9, 1, 2, furnace("furnace", "south")).set(10, 1, 2, CAMPFIRE);
        s.fill(9, 2, 2, 10, 4, 2, Kit.ROUGH).set(10, 2, 2, Kit.upside(Kit.TRIM_STAIRS, "south"));
        s.set(10, 1, 5, furnace("smoker", "north")).set(9, 1, 5, Kit.barrel()).set(9, 2, 5, Kit.barrel());
        s.set(8, 1, 2, CRAFTING).set(10, 1, 4, Kit.barrel());
        pendant(s, 9, 4, 4);
        // Upstairs: walls between the bedchambers and the sitting room, with doors.
        for (int x : new int[]{4, 8}) {
            s.fill(x, 6, 2, x, 9, 5, SPRUCE_PLANKS);
        }
        s.door(4, 6, 5, SPRUCE_DOOR, "west", "left");
        s.door(8, 6, 4, SPRUCE_DOOR, "east", "left");
        // The sitting room in the bay: a table with two chairs before the windows.
        table(s, 6, 6, 6);
        s.set(5, 6, 6, chair("west")).set(7, 6, 6, chair("east")).set(6, 6, 5, Kit.CLOTH_CARPET);
        // The bedchambers, two beds each, with chests and books.
        s.bed(2, 6, 3, "minecraft:blue_bed", "north", 0, -1).bed(3, 6, 3, "minecraft:blue_bed", "north", 0, -1);
        s.set(2, 6, 4, Kit.chest("east")).set(2, 6, 5, BOOKSHELF).set(3, 6, 4, Kit.CLOTH_CARPET);
        s.bed(9, 6, 3, "minecraft:purple_bed", "north", 0, -1).bed(10, 6, 3, "minecraft:purple_bed", "north", 0, -1);
        s.set(10, 6, 4, Kit.chest("west")).set(10, 6, 5, BOOKSHELF).set(9, 6, 5, Kit.CLOTH_CARPET);
        for (int[] p : new int[][]{{3, 4}, {9, 4}, {6, 4}}) {
            pendant(s, p[0], 9, p[1]);
        }
    }

    // ---------------------------------------------------------------- the mansion with a tower

    /** The top of the tower wall of the mansion, where the corbels of its crown stand. */
    private static final int TOWER_TOP = 16;

    /**
     * An L-shaped mansion of two storeys in the manner of a country villa, with a tall square tower of four storeys at the
     * corner where its wings meet. The wings are plastered over a rubble footing, with quoins of rubble, framed windows,
     * shutters and gables to the street and the side under low roofs of tiles. The front wing has the door under a balcony
     * on brackets; the back wing opens on the courtyard through a loggia of two wide arches whose roof is a terrace with a
     * balustrade before the upper rooms. The tower is of stone with bands of trim at its floors, windows on every storey
     * and the banners of the house over the roofs; at its top a ring of corbels carries a parapet with merlons, and out of it rises a slate spire with a
     * finial. A winding stair of stone slabs climbs inside the tower to a lookout room. Inside: the hall at the door, a
     * dining room and a kitchen opening on the loggia, two bedchambers with two beds each above. Four beds.
     */
    static Structure towerMansion() {
        Structure s = new Structure(13, 25, 13);
        // The footing: the wings, the tower and the floor of the loggia.
        s.fill(1, 0, 1, 11, 0, 6, Kit.ROUGH).fill(1, 0, 5, 5, 0, 10, Kit.ROUGH).fill(6, 0, 7, 12, 0, 8, Kit.ROUGH);
        s.fill(6, 0, 2, 10, 0, 5, SPRUCE_PLANKS).fill(2, 0, 6, 4, 0, 9, SPRUCE_PLANKS);
        // The wings: plaster on the footing, a band of trim at the upper floor.
        s.walls(5, 1, 1, 11, 7, 6, Kit.PLASTER).walls(1, 1, 5, 5, 7, 10, Kit.PLASTER);
        s.walls(5, 4, 1, 11, 4, 6, Kit.TRIM).walls(1, 4, 5, 5, 4, 10, Kit.TRIM);
        s.fill(6, 4, 2, 10, 4, 5, SPRUCE_PLANKS).fill(2, 4, 6, 4, 4, 9, SPRUCE_PLANKS);
        for (int[] q : new int[][]{{11, 1}, {11, 6}, {1, 10}, {5, 10}}) {
            quoins(s, q[0], q[1], 1, 7);
            s.set(q[0], 4, q[1], Kit.TRIM);
        }
        // The roofs of the wings: two low gables of tiles meeting in a valley, their ends against the tower.
        Roofs roofs = new Roofs(s).gableX(4, 0, 12, 7, 8, 1, Kit.TILE, true).gableZ(0, 4, 6, 11, 8, 1, Kit.TILE, true).build();
        roofs.gableWallX(11, 1, 6, 8, Kit.PLASTER).gableWallZ(10, 1, 5, 8, Kit.PLASTER);
        course(s, 5, 1, 11, 6, 7);
        course(s, 1, 6, 5, 10, 7);
        // A chimney on the ridge of the back wing.
        s.fill(9, 11, 3, 9, 11, 4, Kit.TILE.block());
        s.fill(9, 12, 3, 9, 13, 4, Kit.CHIMNEY);
        s.set(9, 14, 3, Kit.CHIMNEY_POT).set(9, 14, 4, Kit.CHIMNEY_POT);
        mansionTower(s);
        mansionLoggia(s);
        mansionFronts(s);
        mansionRooms(s);
        shapeStairs(s);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The tower: stone walls with quoins and bands of trim, a window on every storey, the winding stair around its newel
     * post, the crown of corbels and merlons, the spire and its finial.
     */
    private static void mansionTower(Structure s) {
        s.fill(1, 0, 1, 5, 0, 5, Kit.ROUGH);
        s.walls(1, 1, 1, 5, TOWER_TOP, 5, Kit.WALL);
        s.fill(2, 1, 2, 4, TOWER_TOP, 4, Structure.AIR);
        for (int y : new int[]{4, 8, 12}) {
            s.walls(1, y, 1, 5, y, 5, Kit.TRIM);
        }
        quoins(s, 1, 1, 1, TOWER_TOP);
        quoins(s, 5, 1, 9, TOWER_TOP);
        quoins(s, 1, 5, 9, TOWER_TOP);
        quoins(s, 5, 5, 12, TOWER_TOP);
        for (int y : new int[]{4, 8, 12}) {
            s.set(1, y, 1, Kit.TRIM).set(5, y, 1, Kit.TRIM).set(1, y, 5, Kit.TRIM).set(5, y, 5, Kit.TRIM);
        }
        // Windows: on the free faces on every storey, all round the lookout under the crown.
        for (int y : new int[]{2, 6, 10, 14}) {
            window(s, 1, 3, y, y + 1, "west", Kit.CARVED, false);
            window(s, 3, 1, y, y + 1, "north", Kit.CARVED, false);
        }
        window(s, 5, 3, 14, 15, "east", Kit.CARVED, false);
        window(s, 3, 5, 14, 15, "south", Kit.CARVED, false);
        s.set(2, 14, 6, Kit.wallBanner("red", "south")).set(4, 14, 6, Kit.wallBanner("red", "south"));
        // The crown: corbels, a parapet with merlons and lanterns in the corners, the spire inside it.
        course(s, 1, 1, 5, 5, TOWER_TOP);
        for (int i = 0; i <= 6; i++) {
            for (int[] c : new int[][]{{i, 0}, {i, 6}, {0, i}, {6, i}}) {
                s.set(c[0], TOWER_TOP + 1, c[1], Kit.WALL);
                if ((c[0] + c[1]) % 2 == 0) {
                    s.set(c[0], TOWER_TOP + 2, c[1], Kit.WALL);
                }
            }
        }
        for (int[] c : new int[][]{{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
            s.set(c[0], TOWER_TOP + 3, c[1], Kit.lantern());
        }
        s.walls(1, TOWER_TOP + 1, 1, 5, TOWER_TOP + 2, 5, Kit.WALL);
        new Roofs(s).hip(1, 1, 5, 5, TOWER_TOP + 3, 2, Kit.SLATE).build();
        s.set(3, TOWER_TOP + 7, 3, Kit.SLATE.block()).set(3, TOWER_TOP + 8, 3, LIGHTNING_ROD);
        // The winding stair: three turns from the hall to the lookout, landings on the south-east.
        int[][] ring = {{3, 4}, {2, 4}, {2, 3}, {2, 2}, {3, 2}, {4, 2}, {4, 3}, {4, 4}};
        s.fill(3, 1, 3, 3, 11, 3, SPRUCE_LOG + "[axis=y]");
        spiral(s, ring, 1, 24, Kit.TRIM_SLAB);
        s.set(3, 12, 3, Kit.TRIM).set(2, 12, 4, Kit.TRIM).set(3, 12, 4, Kit.TRIM);
        s.set(2, 13, 4, Kit.lantern());
        pendant(s, 3, 15, 3);
        // Doorways: from the hall of the front wing at the foot, into both wings at the first landing, and through the
        // foot of the tower under its stair into the back wing.
        s.fill(3, 1, 5, 3, 2, 5, Structure.AIR);
        s.fill(5, 5, 4, 5, 6, 4, Structure.AIR).fill(4, 5, 5, 4, 6, 5, Structure.AIR);
        s.fill(5, 1, 3, 5, 2, 3, Structure.AIR);
        s.set(2, 4, 3, Kit.wallTorch("east")).set(2, 8, 3, Kit.wallTorch("east"));
    }

    /**
     * The loggia before the back wing: two wide arches on piers with carved imposts, its roof a terrace with a balustrade
     * and lanterns; the paved courtyard before it with a lamp and a planter of flowers.
     */
    private static void mansionLoggia(Structure s) {
        s.fill(6, 1, 8, 12, 3, 8, Kit.WALL).fill(12, 1, 7, 12, 3, 7, Kit.WALL).set(12, 2, 7, Kit.GLASS);
        for (int[] arch : new int[][]{{7, 8}, {10, 11}}) {
            s.fill(arch[0], 1, 8, arch[1], 2, 8, Structure.AIR);
            s.set(arch[0], 3, 8, Kit.upside(Kit.WALL_STAIRS, "west")).set(arch[1], 3, 8, Kit.upside(Kit.WALL_STAIRS, "east"));
        }
        for (int x : new int[]{6, 9, 12}) {
            s.set(x, 0, 8, Kit.ROUGH).set(x, 3, 8, Kit.CARVED);
        }
        // The terrace over it.
        s.fill(6, 4, 7, 12, 4, 8, Kit.TRIM);
        s.fill(6, 5, 8, 12, 5, 8, Kit.WALL_FENCE).set(12, 5, 7, Kit.WALL_FENCE);
        s.set(6, 6, 8, Kit.lantern()).set(12, 6, 8, Kit.lantern()).set(9, 6, 8, Kit.lantern());
        course(s, 6, 7, 12, 8, 4);
        s.set(8, 3, 7, Kit.hangingLantern()).set(11, 3, 7, Kit.hangingLantern());
        // The courtyard: paving, a planter of flowers, a lamp, a bench.
        s.fill(6, 0, 9, 12, 0, 12, Kit.slab(Kit.TRIM_SLAB));
        s.fill(11, 0, 10, 12, 0, 12, GRASS);
        s.set(11, 1, 10, "minecraft:rose_bush[half=lower]").set(11, 2, 10, "minecraft:rose_bush[half=upper]");
        s.set(12, 1, 11, "minecraft:peony[half=lower]").set(12, 2, 11, "minecraft:peony[half=upper]");
        s.set(11, 1, 12, "minecraft:allium").set(12, 1, 12, "minecraft:oxeye_daisy").set(12, 1, 10, "minecraft:cornflower");
        s.set(11, 1, 11, Kit.leaves("flowering_azalea"));
        s.fill(7, 1, 11, 7, 2, 11, Kit.fence("spruce")).set(7, 3, 11, Kit.lantern());
        s.set(9, 1, 11, Kit.stairs(Kit.TRIM_STAIRS, "south")).set(10, 1, 11, Kit.stairs(Kit.TRIM_STAIRS, "south"));
    }

    /**
     * The fronts of the wings: the door of the front wing under its balcony, framed windows with shutters, the round
     * window in the gable, the doors of the back wing onto the loggia and the terrace.
     */
    private static void mansionFronts(Structure s) {
        // The front wing: the door with a carved keystone and the balcony over it.
        s.door(3, 1, 10, SPRUCE_DOOR, "north", "left");
        s.set(3, 3, 10, Kit.CARVED).set(3, 0, 11, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        window(s, 2, 10, 1, 2, "south", Kit.TRIM, false);
        window(s, 4, 10, 1, 2, "south", Kit.TRIM, false);
        s.door(3, 5, 10, SPRUCE_DOOR, "north", "left");
        window(s, 2, 10, 5, 6, "south", Kit.TRIM, false);
        window(s, 4, 10, 5, 6, "south", Kit.TRIM, false);
        s.fill(2, 4, 11, 4, 4, 12, Kit.TRIM);
        s.set(2, 3, 11, Kit.upside(Kit.TRIM_STAIRS, "north")).set(4, 3, 11, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.fill(2, 5, 12, 4, 5, 12, Kit.fence("spruce")).set(2, 5, 11, Kit.fence("spruce")).set(4, 5, 11, Kit.fence("spruce"));
        s.set(2, 6, 12, Kit.lantern()).set(4, 6, 12, Kit.lantern());
        s.set(3, 9, 10, Kit.GLASS).set(3, 10, 10, Kit.CARVED);
        s.set(3, 3, 11, Kit.hangingLantern());
        // The west side of the front wing, with shutters.
        for (int y : new int[]{1, 5}) {
            for (int z : new int[]{7, 9}) {
                window(s, 1, z, y, y + 1, "west", Kit.TRIM, false);
            }
            shutters(s, 1, 7, y, y + 1, "west");
        }
        // The east side of the front wing, on the courtyard.
        window(s, 5, 9, 1, 2, "east", Kit.TRIM, false);
        window(s, 5, 9, 5, 6, "east", Kit.TRIM, false);
        // The back wing: doors onto the loggia and the terrace, windows.
        s.door(8, 1, 6, SPRUCE_DOOR, "north", "left");
        s.door(8, 5, 6, SPRUCE_DOOR, "south", "left");
        window(s, 10, 6, 1, 2, "south", Kit.TRIM, false);
        window(s, 10, 6, 5, 6, "south", Kit.TRIM, false);
        window(s, 6, 6, 5, 6, "south", Kit.TRIM, false);
        for (int y : new int[]{1, 5}) {
            for (int x : y == 1 ? new int[]{7} : new int[]{7, 10}) {
                window(s, x, 1, y, y + 1, "north", Kit.TRIM, false);
                shutters(s, x, 1, y, y + 1, "north");
            }
            window(s, 11, 3, y, y + 1, "east", Kit.TRIM, false);
            window(s, 11, 4, y, y + 1, "east", Kit.TRIM, false);
        }
        s.set(11, 9, 3, Kit.GLASS).set(11, 9, 4, Kit.GLASS);
    }

    /**
     * The rooms: the hall of the front wing, the dining room and kitchen of the back wing, the bedchambers above.
     */
    private static void mansionRooms(Structure s) {
        // The hall: a runner to the tower stair, a bench and a chest.
        s.fill(3, 1, 6, 3, 1, 9, Kit.CLOTH_CARPET);
        s.set(2, 1, 6, Kit.chest("east")).set(4, 1, 7, Kit.stairs(SPRUCE_STAIRS, "east")).set(2, 1, 9, Kit.barrel());
        pendant(s, 3, 3, 7);
        // The dining room and kitchen of the back wing.
        s.fill(8, 1, 3, 8, 1, 4, Kit.topSlab(SPRUCE_SLAB));
        s.set(7, 1, 3, chair("west")).set(7, 1, 4, chair("west")).set(9, 1, 3, chair("east")).set(9, 1, 4, chair("east"));
        s.set(8, 2, 3, Kit.lantern());
        s.set(9, 1, 2, furnace("smoker", "south")).set(10, 1, 2, CAMPFIRE);
        s.fill(9, 2, 2, 10, 3, 2, Kit.ROUGH).set(10, 2, 2, Kit.upside(Kit.TRIM_STAIRS, "south"));
        s.set(10, 1, 3, furnace("furnace", "west")).set(10, 1, 4, Kit.barrel()).set(10, 1, 5, CRAFTING);
        s.set(6, 1, 5, Kit.barrel()).set(6, 1, 2, BOOKSHELF);
        pendant(s, 7, 3, 3);
        pendant(s, 9, 3, 4);
        // The bedchamber of the back wing: two beds, a chest, books, the door to the terrace.
        s.bed(9, 5, 3, "minecraft:red_bed", "north", 0, -1).bed(10, 5, 3, "minecraft:red_bed", "north", 0, -1);
        s.set(10, 5, 4, Kit.chest("west")).set(6, 5, 2, BOOKSHELF).set(6, 6, 2, BOOKSHELF).set(10, 5, 5, Kit.barrel());
        s.fill(7, 5, 4, 8, 5, 5, Kit.CLOTH_CARPET);
        pendant(s, 8, 7, 3);
        // The bedchamber of the front wing: two beds, a chest and a wardrobe, the door to the balcony.
        s.bed(2, 5, 7, "minecraft:green_bed", "north", 0, -1).bed(2, 5, 8, "minecraft:green_bed", "south", 0, 1);
        s.set(4, 5, 8, Kit.chest("west")).set(4, 5, 7, BOOKSHELF).fill(3, 5, 7, 3, 5, 8, Kit.CLOTH_CARPET);
        pendant(s, 3, 7, 8);
    }
}
