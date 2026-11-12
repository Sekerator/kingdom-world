package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The buildings that give a capital its face without serving any trade: a monument for the square, a chapel with its
 * steeple, a clock tower and a small park. None of them has a role; they are there to be looked at and walked through, so
 * that a capital is no row of the same houses. All face south and are drawn with the blocks that stand for the parts of a
 * look, so that each kingdom builds them of its own stone, wood, roof and cloth.
 */
final class Decorations {
    private static final String BRONZE = "minecraft:waxed_oxidized_copper";
    private static final String BRONZE_CUT = "minecraft:waxed_oxidized_cut_copper";
    private static final String BRONZE_STAIRS = "minecraft:waxed_oxidized_cut_copper_stairs";
    private static final String DARK = "minecraft:polished_blackstone";
    private static final String DARK_POST = "minecraft:polished_blackstone_wall";
    private static final String GOLD = "minecraft:gold_block";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String DIRT = "minecraft:dirt";
    private static final String SPRUCE_PLANKS = "minecraft:spruce_planks";
    private static final String OAK_FENCE = "minecraft:oak_fence";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String WATER = "minecraft:water[level=0]";

    private Decorations() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("classic_monument", "decoration", "monument", "minecraft:gold_block", 4, 1, 8, monument(),
                "none", 1, "").at("door", 4, 1, 8));
        list.add(new BuildingGenerator.Building("classic_chapel", "decoration", "chapel", "minecraft:bell", 6, 0, 20, chapel(),
                "none", 1, "").at("door", 6, 0, 20));
        list.add(new BuildingGenerator.Building("classic_clock_tower", "decoration", "clock_tower", "minecraft:clock", 4, 0, 8, clockTower(),
                "none", 1, "").at("door", 4, 0, 8));
        list.add(new BuildingGenerator.Building("classic_park", "decoration", "park", "minecraft:oak_sapling", 8, 1, 16, park(),
                "none", 1, "").at("door", 8, 1, 16));
        return list;
    }

    // ---------------------------------------------------------------- shared shapes

    /** Stairs of any half and shape. */
    private static String stairs(String block, String facing, String half, String shape) {
        return block + "[facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=false]";
    }

    /**
     * A square ring of stairs around a middle, every stair rising towards the middle and the corners turned: a step all
     * round a plinth, or (upside down) a cornice whose brackets lean on the wall inside it.
     */
    private static void stairRing(Structure s, int cx, int cz, int radius, int y, String block, boolean upside) {
        String half = upside ? "top" : "bottom";
        for (int i = -radius; i <= radius; i++) {
            s.set(cx + i, y, cz - radius, stairs(block, "south", half, "straight"));
            s.set(cx + i, y, cz + radius, stairs(block, "north", half, "straight"));
            s.set(cx - radius, y, cz + i, stairs(block, "east", half, "straight"));
            s.set(cx + radius, y, cz + i, stairs(block, "west", half, "straight"));
        }
        s.set(cx - radius, y, cz - radius, stairs(block, "south", half, "outer_left"));
        s.set(cx + radius, y, cz - radius, stairs(block, "south", half, "outer_right"));
        s.set(cx - radius, y, cz + radius, stairs(block, "north", half, "outer_right"));
        s.set(cx + radius, y, cz + radius, stairs(block, "north", half, "outer_left"));
    }

    /**
     * One ring of a round spire: on the rim of a round plan the stairs rise towards the middle, those on the slants turned as
     * outer corners.
     */
    private static void roundStairRing(Structure s, int cx, int y, int cz, int diameter, String block) {
        int radius = (diameter - 1) / 2;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (!Kit.onRim(dx, dz, diameter)) {
                    continue;
                }
                String state;
                if (Math.abs(dx) > Math.abs(dz)) {
                    state = stairs(block, dx > 0 ? "west" : "east", "bottom", "straight");
                } else if (Math.abs(dz) > Math.abs(dx)) {
                    state = stairs(block, dz > 0 ? "north" : "south", "bottom", "straight");
                } else if (dz > 0) {
                    state = stairs(block, "north", "bottom", dx > 0 ? "outer_left" : "outer_right");
                } else {
                    state = stairs(block, "south", "bottom", dx > 0 ? "outer_right" : "outer_left");
                }
                s.set(cx + dx, y, cz + dz, state);
            }
        }
    }

    /**
     * A tall round spire: rings of full blocks under rings of stairs, shrinking by one block on every side, {@code rise}
     * layers to a ring, the first ring only its stairs (the eaves), and a needle of {@code needle} blocks on the point.
     *
     * @return the layer above the needle
     */
    private static int roundSpire(Structure s, int cx, int y, int cz, int diameter, int rise, int needle, Kit.Roof roof) {
        int layer = y;
        for (int width = diameter; width >= 3; width -= 2) {
            if (width < diameter) {
                for (int k = 0; k < rise - 1; k++) {
                    Kit.disc(s, cx, layer++, cz, width, roof.block());
                }
                // Under the stairs of a ring the next ring's blocks begin, so that the spire is closed.
                Kit.disc(s, cx, layer, cz, width - 2, roof.block());
            }
            roundStairRing(s, cx, layer++, cz, width, roof.stairs());
        }
        for (int k = 0; k < needle; k++) {
            s.set(cx, layer++, cz, roof.block());
        }
        return layer;
    }

    /**
     * A pointed window or opening in a wall that runs along x or along z: three wide, open from {@code y1} to {@code y2},
     * the middle column one higher between two haunches, a carved keystone over the point.
     *
     * @param alongX whether the wall runs along x (a front or back wall)
     * @param glass  the panes of the side columns, of the middle column and of the point, or null for an open arch
     */
    private static void lancet(Structure s, int x, int z, int y1, int y2, boolean alongX, String haunchBlock, String[] glass) {
        for (int i = -1; i <= 1; i++) {
            int px = alongX ? x + i : x;
            int pz = alongX ? z : z + i;
            for (int y = y1; y <= y2; y++) {
                s.set(px, y, pz, glass == null ? Structure.AIR : i == 0 ? glass[1] : glass[0]);
            }
        }
        s.set(x, y2 + 1, z, glass == null ? Structure.AIR : glass[2]);
        if (alongX) {
            s.set(x - 1, y2 + 1, z, stairs(haunchBlock, "west", "top", "straight"));
            s.set(x + 1, y2 + 1, z, stairs(haunchBlock, "east", "top", "straight"));
        } else {
            s.set(x, y2 + 1, z - 1, stairs(haunchBlock, "north", "top", "straight"));
            s.set(x, y2 + 1, z + 1, stairs(haunchBlock, "south", "top", "straight"));
        }
        s.set(x, y2 + 2, z, Kit.CARVED);
    }

    /**
     * A steep gable roof whose ridge runs along z: the first {@code steepRows} rows above the eaves rise two blocks each (a
     * full block under a stair), the rows above them one block; the ridge is a slab. The gable walls are filled with
     * {@code gable} under the roof where nothing stands yet.
     */
    private static void steepGableZ(Structure s, int x1, int x2, int z1, int z2, int y, int steepRows, Kit.Roof roof, String gable,
                                    int wallZ1, int wallZ2) {
        int[] lowest = new int[x2 - x1 + 1];
        int height = y;
        for (int row = 0; x1 + row <= x2 - row; row++) {
            boolean steep = row > 0 && row <= steepRows;
            if (row > 0) {
                height += steep ? 2 : 1;
            }
            int west = x1 + row;
            int east = x2 - row;
            if (steep) {
                s.fill(west, height - 1, z1, west, height - 1, z2, roof.block()).fill(east, height - 1, z1, east, height - 1, z2, roof.block());
            }
            if (west < east) {
                s.fill(west, height, z1, west, height, z2, Kit.stairs(roof.stairs(), "east"));
                s.fill(east, height, z1, east, height, z2, Kit.stairs(roof.stairs(), "west"));
            } else {
                s.fill(west, height, z1, west, height, z2, Kit.slab(roof.slab()));
            }
            lowest[west - x1] = steep ? height - 1 : height;
            lowest[east - x1] = lowest[west - x1];
        }
        for (int wall : new int[]{wallZ1, wallZ2}) {
            if (wall < 0) {
                continue;
            }
            for (int x = x1 + 1; x < x2; x++) {
                for (int h = y; h < lowest[x - x1]; h++) {
                    if (!s.solid(x, h, wall)) {
                        s.set(x, h, wall, gable);
                    }
                }
            }
        }
    }

    /** A post of a fence with a lantern on top. */
    private static void lampPost(Structure s, int x, int y, int z, int height) {
        s.fill(x, y, z, x, y + height - 1, z, OAK_FENCE);
        s.set(x, y + height, z, Kit.lantern());
    }

    // ---------------------------------------------------------------- the monument

    /**
     * A monument for a square: a king in armour of green bronze on a high plinth. Two steps of trim run all round a slender
     * die of stone with rough quoins, carved plaques on its sides and the banners of the kingdom on its front and back; a
     * wide cornice with a lantern on each corner carries the king. He wears a skirt of armour and rounded shoulders, a
     * golden crown with four points, a cloak of the colour of the kingdom falling behind him to the plinth, a shield with
     * the kingdom's banner on his left arm, and he raises a glowing sword in his right. Lamp posts of dark iron stand at
     * the corners of the paving, beds of flowers and flowering azaleas between them.
     */
    static Structure monument() {
        Structure s = new Structure(9, 15, 9);
        int c = 4;
        // The paving: a path of trim in the middle of every side, beds of flowers on either side of it, rough stone under
        // the steps.
        for (int z = 0; z <= 8; z++) {
            for (int x = 0; x <= 8; x++) {
                int ring = Math.max(Math.abs(x - c), Math.abs(z - c));
                int along = Math.abs(x - c) == 4 ? Math.abs(z - c) : Math.abs(x - c);
                boolean corner = Math.abs(x - c) == 4 && Math.abs(z - c) == 4;
                s.set(x, 0, z, ring < 4 ? Kit.ROUGH : !corner && along >= 2 ? GRASS : Kit.TRIM);
            }
        }
        flowerBeds(s, c);
        // Two steps of trim all round the plinth.
        s.fill(1, 1, 1, 7, 1, 7, Kit.ROUGH);
        stairRing(s, c, c, 3, 1, Kit.TRIM_STAIRS, false);
        s.fill(2, 2, 2, 6, 2, 6, Kit.ROUGH);
        stairRing(s, c, c, 2, 2, Kit.TRIM_STAIRS, false);
        // The plinth: a slender die of wall with rough quoins, carved plaques on its sides, the banners of the kingdom on
        // its front and its back.
        s.fill(3, 3, 3, 5, 4, 5, Kit.WALL);
        for (int[] q : new int[][]{{3, 3}, {5, 3}, {3, 5}, {5, 5}}) {
            s.fill(q[0], 3, q[1], q[0], 4, q[1], Kit.ROUGH);
        }
        s.set(3, 4, c, Kit.CARVED).set(5, 4, c, Kit.CARVED);
        s.set(c, 4, 6, Kit.wallBanner("red", "south")).set(c, 4, 2, Kit.wallBanner("red", "north"));
        // The wide cornice that carries the king, lanterns on its corners.
        s.fill(3, 5, 3, 5, 5, 5, Kit.TRIM);
        stairRing(s, c, c, 2, 5, Kit.TRIM_STAIRS, true);
        for (int[] q : new int[][]{{2, 2}, {6, 2}, {2, 6}, {6, 6}}) {
            s.set(q[0], 6, q[1], Kit.lantern());
        }
        king(s, c, 6, c);
        // Lamp posts of dark iron at the corners.
        for (int[] q : new int[][]{{0, 0}, {8, 0}, {0, 8}, {8, 8}}) {
            s.fill(q[0], 1, q[1], q[0], 3, q[1], DARK_POST);
            s.set(q[0], 4, q[1], Kit.lantern());
        }
        s.connect();
        return s;
    }

    /**
     * The flowers of the beds at the edge of the paving: a flowering azalea by each lamp post, small flowers of a colour
     * of their own on every side by the paths.
     */
    private static void flowerBeds(Structure s, int c) {
        String[] small = {"minecraft:red_tulip", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:allium"};
        int side = 0;
        for (int[] dir : new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
            for (int sign : new int[]{-1, 1}) {
                for (int along = 2; along <= 3; along++) {
                    // dir names the side of the paving, sign and along the place on it
                    int x = dir[0] != 0 ? c + dir[0] * 4 : c + sign * along;
                    int z = dir[1] != 0 ? c + dir[1] * 4 : c + sign * along;
                    s.set(x, 1, z, along == 3 ? "minecraft:flowering_azalea" : small[side]);
                }
            }
            side++;
        }
    }

    /**
     * The king standing at {@code (cx, y, cz)} on his feet, facing south: legs, skirt, breast and shoulders, head and crown,
     * the cloak behind him, the shield on his left (west) arm and the sword raised in his right (east) hand.
     */
    private static void king(Structure s, int cx, int y, int cz) {
        // Boots and legs, apart.
        s.set(cx - 1, y, cz, BRONZE).set(cx + 1, y, cz, BRONZE);
        s.set(cx - 1, y + 1, cz, BRONZE).set(cx + 1, y + 1, cz, BRONZE);
        // The skirt of the armour, the breastplate and the shoulders.
        s.fill(cx - 1, y + 2, cz, cx + 1, y + 2, cz, BRONZE_CUT);
        s.fill(cx - 1, y + 3, cz, cx + 1, y + 4, cz, BRONZE);
        s.set(cx - 2, y + 4, cz, Kit.upside(BRONZE_STAIRS, "east"));
        s.set(cx + 2, y + 4, cz, BRONZE_CUT);
        // The head and the crown with four candles for its points.
        s.set(cx, y + 5, cz, BRONZE);
        s.set(cx, y + 6, cz, GOLD);
        s.set(cx, y + 7, cz, "minecraft:yellow_candle[candles=4,lit=false,waterlogged=false]");
        // The cloak falls from the shoulders behind him and lies on the plinth.
        s.fill(cx - 1, y, cz - 1, cx + 1, y + 4, cz - 1, Kit.CLOTH);
        s.fill(cx - 1, y, cz - 2, cx + 1, y, cz - 2, Kit.CLOTH_CARPET);
        // The left arm hangs and carries the shield, the banner of the kingdom.
        s.set(cx - 2, y + 3, cz, BRONZE).set(cx - 2, y + 2, cz, BRONZE);
        s.set(cx - 2, y + 3, cz + 1, Kit.wallBanner("red", "south"));
        // The right arm is raised and holds the sword: a gauntlet and a blade that glows.
        s.set(cx + 2, y + 5, cz, BRONZE).set(cx + 2, y + 6, cz, BRONZE_CUT);
        s.set(cx + 2, y + 7, cz, "minecraft:end_rod[facing=up]").set(cx + 2, y + 8, cz, "minecraft:end_rod[facing=up]");
    }

    // ---------------------------------------------------------------- the chapel

    /** The nave: its walls and the top of them, where the roof begins. */
    private static final int NAVE_X1 = 1;
    private static final int NAVE_X2 = 11;
    private static final int NAVE_Z1 = 1;
    private static final int NAVE_Z2 = 13;
    private static final int NAVE_TOP = 7;
    /** The steeple over the front: its walls, the floor of the belfry and the top of its walls. */
    private static final int TOWER_X1 = 3;
    private static final int TOWER_X2 = 9;
    private static final int TOWER_Z1 = 13;
    private static final int TOWER_Z2 = 19;
    private static final int BELFRY_FLOOR = 10;
    private static final int TOWER_TOP = 15;

    /**
     * The chapel of the town: a nave under a steep roof between buttresses of rough stone, three tall pointed windows of
     * stained glass on either side, a great one behind the altar and a stone cross on the back gable. Over the front stands a
     * square steeple with rough quoins. Its ground floor is an open porch under pointed arches with the door of the chapel at
     * its back; above it a rose window of coloured glass looks at the street, above that the bell hangs in a belfry open on
     * three sides, and from a cornice with four slender pinnacles rises a tall octagonal spire with a golden finial. Inside:
     * pews on either side of a carpeted aisle, an altar of quartz with candles on a dais, the banners of the kingdom on the
     * back wall and lanterns hanging from the ridge on long chains.
     */
    static Structure chapel() {
        Structure s = new Structure(13, 28, 21);
        chapelNave(s);
        steepGableZ(s, 0, 12, 0, NAVE_Z2, NAVE_TOP, 1, Kit.SHINGLE, Kit.WALL, NAVE_Z1, NAVE_Z2);
        // A cross of stone on the point of the back gable.
        s.fill(6, 15, NAVE_Z1, 6, 17, NAVE_Z1, Kit.WALL_FENCE);
        s.set(5, 16, NAVE_Z1, Kit.WALL_FENCE).set(7, 16, NAVE_Z1, Kit.WALL_FENCE);
        chapelWindows(s);
        chapelInside(s);
        steeple(s);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The walls of the nave on a rough footing and plinth, the floor, the buttresses.
     */
    private static void chapelNave(Structure s) {
        s.fill(NAVE_X1, 0, NAVE_Z1, NAVE_X2, 0, NAVE_Z2, Kit.ROUGH);
        s.walls(NAVE_X1, 1, NAVE_Z1, NAVE_X2, NAVE_TOP, NAVE_Z2, Kit.WALL);
        s.walls(NAVE_X1, 1, NAVE_Z1, NAVE_X2, 1, NAVE_Z2, Kit.ROUGH);
        // Floor: planks under the pews, trim in the aisle and before the altar.
        s.fill(NAVE_X1 + 1, 0, NAVE_Z1 + 1, NAVE_X2 - 1, 0, NAVE_Z2 - 1, SPRUCE_PLANKS);
        s.fill(5, 0, NAVE_Z1 + 1, 7, 0, NAVE_Z2 - 1, Kit.TRIM);
        s.fill(NAVE_X1 + 1, 0, NAVE_Z1 + 1, NAVE_X2 - 1, 0, 4, Kit.TRIM);
        // Buttresses between the windows and at the corners, capped with a slope.
        for (int z : new int[]{1, 5, 9, 13}) {
            buttress(s, 0, z, "east");
            buttress(s, 12, z, "west");
        }
        for (int x : new int[]{3, 9}) {
            buttress(s, x, 0, "south");
        }
    }

    /** A buttress of rough stone one block out of a wall, its cap sloping towards the wall it leans on. */
    private static void buttress(Structure s, int x, int z, String toWall) {
        s.fill(x, 0, z, x, 4, z, Kit.ROUGH);
        s.set(x, 5, z, Kit.stairs(Kit.ROUGH_STAIRS, toWall));
    }

    /**
     * The pointed windows: three on either side between the buttresses with sills of trim, the great window behind the
     * altar and a round window in the back gable.
     */
    private static void chapelWindows(Structure s) {
        String[] glass = {Kit.stainedPane("blue"), Kit.stainedPane("red"), Kit.stainedPane("yellow")};
        for (int z : new int[]{3, 7, 11}) {
            lancet(s, NAVE_X1, z, 2, 5, false, Kit.WALL_STAIRS, glass);
            lancet(s, NAVE_X2, z, 2, 5, false, Kit.WALL_STAIRS, glass);
            for (int i = -1; i <= 1; i++) {
                s.set(0, 1, z + i, Kit.topSlab(Kit.TRIM_SLAB)).set(12, 1, z + i, Kit.topSlab(Kit.TRIM_SLAB));
            }
        }
        String[] great = {Kit.stainedPane("light_blue"), Kit.stainedPane("orange"), Kit.stainedPane("yellow")};
        lancet(s, 6, NAVE_Z1, 2, 6, true, Kit.WALL_STAIRS, great);
        s.set(6, 10, NAVE_Z1, Kit.stainedPane("red")).set(6, 12, NAVE_Z1, Kit.stainedPane("red"));
        s.set(5, 11, NAVE_Z1, Kit.stainedPane("red")).set(7, 11, NAVE_Z1, Kit.stainedPane("red"));
        s.set(6, 11, NAVE_Z1, Kit.stainedPane("yellow"));
    }

    /**
     * Inside the nave: the dais and the altar of quartz with candles, banners on the back wall, the pews on either side of
     * the carpet, torches between the windows and two lanterns hanging from the ridge on long chains.
     */
    private static void chapelInside(Structure s) {
        s.fill(3, 1, 3, 9, 1, 4, Kit.slab(Kit.TRIM_SLAB));
        s.set(5, 1, 2, "minecraft:quartz_pillar[axis=y]").set(6, 1, 2, "minecraft:chiseled_quartz_block").set(7, 1, 2, "minecraft:quartz_pillar[axis=y]");
        s.fill(3, 1, 2, 4, 1, 2, Kit.slab(Kit.TRIM_SLAB)).fill(8, 1, 2, 9, 1, 2, Kit.slab(Kit.TRIM_SLAB));
        s.set(5, 2, 2, "minecraft:white_candle[candles=3,lit=true,waterlogged=false]");
        s.set(7, 2, 2, "minecraft:white_candle[candles=3,lit=true,waterlogged=false]");
        s.set(6, 2, 2, "minecraft:white_candle[candles=4,lit=true,waterlogged=false]");
        s.set(3, 4, 2, Kit.wallBanner("red", "south")).set(9, 4, 2, Kit.wallBanner("red", "south"));
        s.set(2, 1, 2, Kit.WALL_FENCE).set(2, 2, 2, "minecraft:white_candle[candles=1,lit=true,waterlogged=false]");
        s.set(10, 1, 2, Kit.WALL_FENCE).set(10, 2, 2, "minecraft:white_candle[candles=1,lit=true,waterlogged=false]");
        // The aisle and the pews.
        s.fill(6, 1, 5, 6, 1, NAVE_Z2 - 1, Kit.CLOTH_CARPET);
        for (int z : new int[]{6, 8, 10, 12}) {
            s.fill(2, 1, z, 4, 1, z, Kit.stairs(SPRUCE_STAIRS, "south"));
            s.fill(8, 1, z, 10, 1, z, Kit.stairs(SPRUCE_STAIRS, "south"));
        }
        for (int z : new int[]{5, 9}) {
            s.set(2, 4, z, Kit.wallTorch("east")).set(10, 4, z, Kit.wallTorch("west"));
        }
        for (int z : new int[]{5, 10}) {
            s.fill(6, 7, z, 6, 13, z, Kit.chain());
            s.set(6, 6, z, Kit.hangingLantern());
        }
    }

    /**
     * The steeple over the front of the nave: the open porch with the door of the chapel, the rose window, the belfry with
     * its bell, the cornice with pinnacles and the octagonal spire with its finial.
     */
    private static void steeple(Structure s) {
        int cx = 6;
        int cz = 16;
        s.fill(TOWER_X1, 0, TOWER_Z1, TOWER_X2, 0, TOWER_Z2, Kit.ROUGH);
        s.fill(TOWER_X1 + 1, 0, TOWER_Z1 + 1, TOWER_X2 - 1, 0, TOWER_Z2 - 1, Kit.TRIM);
        s.fill(TOWER_X1, 1, TOWER_Z1, TOWER_X2, TOWER_TOP, TOWER_Z2, Kit.WALL);
        s.fill(TOWER_X1 + 1, 1, TOWER_Z1 + 1, TOWER_X2 - 1, TOWER_TOP - 1, TOWER_Z2 - 1, Structure.AIR);
        s.walls(TOWER_X1, 1, TOWER_Z1, TOWER_X2, 1, TOWER_Z2, Kit.ROUGH);
        for (int[] q : new int[][]{{TOWER_X1, TOWER_Z1}, {TOWER_X2, TOWER_Z1}, {TOWER_X1, TOWER_Z2}, {TOWER_X2, TOWER_Z2}}) {
            s.fill(q[0], 1, q[1], q[0], TOWER_TOP, q[1], Kit.ROUGH);
        }
        // Buttresses at the front corners frame the porch; the step before it.
        buttress(s, TOWER_X1 - 1, TOWER_Z2, "east");
        buttress(s, TOWER_X2 + 1, TOWER_Z2, "west");
        s.fill(4, 0, 20, 8, 0, 20, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        // The porch: pointed arches on the front and the sides, benches, the door of the chapel at its back under a carved
        // head.
        lancet(s, cx, TOWER_Z2, 1, 2, true, Kit.WALL_STAIRS, null);
        lancet(s, TOWER_X1, cz, 1, 2, false, Kit.WALL_STAIRS, null);
        lancet(s, TOWER_X2, cz, 1, 2, false, Kit.WALL_STAIRS, null);
        s.door(cx, 1, TOWER_Z1, "minecraft:spruce_door", "north", "left");
        s.fill(cx - 1, 1, TOWER_Z1, cx - 1, 2, TOWER_Z1, Kit.TRIM).fill(cx + 1, 1, TOWER_Z1, cx + 1, 2, TOWER_Z1, Kit.TRIM);
        s.set(cx, 3, TOWER_Z1, Kit.CARVED);
        s.set(cx - 1, 3, TOWER_Z1, Kit.upside(Kit.TRIM_STAIRS, "west")).set(cx + 1, 3, TOWER_Z1, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.fill(TOWER_X1 + 1, 4, TOWER_Z1 + 1, TOWER_X2 - 1, 4, TOWER_Z2 - 1, SPRUCE_PLANKS);
        s.set(cx, 3, cz, Kit.hangingLantern());
        s.set(TOWER_X1 + 1, 1, TOWER_Z1 + 1, Kit.stairs(SPRUCE_STAIRS, "west")).set(TOWER_X2 - 1, 1, TOWER_Z1 + 1, Kit.stairs(SPRUCE_STAIRS, "east"));
        // The rose window over the porch: rings of coloured glass in a square of carved stone.
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int r = dx * dx + dy * dy;
                String pane = r == 0 ? "yellow" : r == 1 ? "red" : r == 2 ? "orange" : r == 4 ? "light_blue" : "blue";
                s.set(cx + dx, 7 + dy, TOWER_Z2, r > 6 ? Kit.CARVED : Kit.stainedPane(pane));
            }
        }
        s.set(cx, 5, cz, Kit.lantern());
        // A band of trim between the stages.
        s.walls(TOWER_X1, BELFRY_FLOOR, TOWER_Z1, TOWER_X2, BELFRY_FLOOR, TOWER_Z2, Kit.TRIM);
        s.fill(TOWER_X1 + 1, BELFRY_FLOOR, TOWER_Z1 + 1, TOWER_X2 - 1, BELFRY_FLOOR, TOWER_Z2 - 1, SPRUCE_PLANKS);
        // The belfry: open arches on the front and the sides, the bell on a beam, lanterns on the floor.
        lancet(s, cx, TOWER_Z2, BELFRY_FLOOR + 1, BELFRY_FLOOR + 3, true, Kit.WALL_STAIRS, null);
        lancet(s, TOWER_X1, cz, BELFRY_FLOOR + 1, BELFRY_FLOOR + 3, false, Kit.WALL_STAIRS, null);
        lancet(s, TOWER_X2, cz, BELFRY_FLOOR + 1, BELFRY_FLOOR + 3, false, Kit.WALL_STAIRS, null);
        s.fill(TOWER_X1 + 1, BELFRY_FLOOR + 4, cz, TOWER_X2 - 1, BELFRY_FLOOR + 4, cz, Kit.log("minecraft:spruce_log", "x"));
        s.set(cx, BELFRY_FLOOR + 3, cz, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        s.set(TOWER_X1 + 1, BELFRY_FLOOR + 1, TOWER_Z1 + 1, Kit.lantern()).set(TOWER_X2 - 1, BELFRY_FLOOR + 1, TOWER_Z1 + 1, Kit.lantern());
        // The top: a cornice of trim all round and a pinnacle on every corner.
        s.fill(TOWER_X1, TOWER_TOP, TOWER_Z1, TOWER_X2, TOWER_TOP, TOWER_Z2, Kit.WALL);
        stairRing(s, cx, cz, 4, TOWER_TOP, Kit.TRIM_STAIRS, true);
        for (int[] q : new int[][]{{TOWER_X1, TOWER_Z1}, {TOWER_X2, TOWER_Z1}, {TOWER_X1, TOWER_Z2}, {TOWER_X2, TOWER_Z2}}) {
            s.set(q[0], TOWER_TOP + 1, q[1], Kit.TRIM);
            s.set(q[0], TOWER_TOP + 2, q[1], Kit.WALL_FENCE);
            s.set(q[0], TOWER_TOP + 3, q[1], "minecraft:end_rod[facing=up]");
        }
        // The spire rising from the cornice, and its finial.
        int top = roundSpire(s, cx, TOWER_TOP, cz, 7, 4, 2, Kit.SLATE);
        s.set(cx, top, cz, GOLD);
        s.set(cx, top + 1, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    // ---------------------------------------------------------------- the clock tower

    /** The stages of the clock tower: the band on the shaft, the corbels, the stage of the clocks, the belfry, the cornice. */
    private static final int CLOCK_BAND = 4;
    private static final int CLOCK_CORBEL = 8;
    private static final int CLOCK_STAGE = 9;
    private static final int CLOCK_MIDDLE = 13;
    private static final int CLOCK_FLOOR = 17;
    private static final int CLOCK_CORNICE = 22;

    /**
     * A clock tower for a square. A slender shaft of wall with rough quoins rises from a sloped plinth of rough stone, a band
     * of trim and narrow windows on two levels; the door sits in a small portal with a gable of trim that stands out of the
     * front. On corbels the shaft widens into the stage of the clocks, which carries four great clock faces: a dark ring
     * standing out of the wall around a white face, hands of black, the hours as buttons and a glowing arbor in the middle.
     * Over the clocks the open belfry with its bell under pointed arches, a cornice with lanterns on posts at its corners and
     * a steep square spire with a golden ball and a lightning rod. Inside, a stair winds up along the walls from the door to
     * the belfry, lit by torches and by lanterns hanging in the middle of the shaft.
     */
    static Structure clockTower() {
        Structure s = new Structure(9, 32, 9);
        int c = 4;
        // The footing, the floor and the step; a sloped plinth of rough stone around the foot of the shaft.
        s.fill(0, 0, 0, 8, 0, 8, Kit.ROUGH);
        s.fill(2, 0, 2, 6, 0, 6, Kit.TRIM);
        s.set(c, 0, 8, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        stairRing(s, c, c, 4, 1, Kit.ROUGH_STAIRS, false);
        // The shaft from the ground to the corbels: a course of rough stone, rough quoins, a band of trim.
        s.walls(1, 1, 1, 7, CLOCK_CORBEL, 7, Kit.WALL);
        s.walls(1, 1, 1, 7, 1, 7, Kit.ROUGH);
        s.walls(1, CLOCK_BAND, 1, 7, CLOCK_BAND, 7, Kit.TRIM);
        squareQuoins(s, 1, 7, 1, CLOCK_CORBEL);
        // The door in a portal standing out of the front: two piers under a little gable, a torch on either side.
        s.door(c, 1, 7, "minecraft:spruce_door", "north", "left");
        s.fill(3, 1, 8, 3, 2, 8, Kit.WALL).fill(5, 1, 8, 5, 2, 8, Kit.WALL);
        s.fill(c, 1, 8, c, 2, 8, Structure.AIR);
        s.set(3, 3, 8, Kit.stairs(Kit.TRIM_STAIRS, "east")).set(5, 3, 8, Kit.stairs(Kit.TRIM_STAIRS, "west"));
        s.set(c, 3, 8, Kit.CARVED).set(c, 4, 8, Kit.slab(Kit.TRIM_SLAB));
        s.set(2, 2, 8, Kit.wallTorch("south")).set(6, 2, 8, Kit.wallTorch("south"));
        // Narrow windows on two levels, sills of trim under them, a carved head over the upper ones.
        for (int[] f : faces()) {
            int wx = c + f[0] * 3;
            int wz = c + f[1] * 3;
            if (f[1] != 1) {
                s.set(wx, 2, wz, Kit.GLASS).set(wx, 3, wz, Kit.GLASS);
            }
            s.set(wx, 5, wz, Kit.GLASS).set(wx, 6, wz, Kit.GLASS).set(wx, 7, wz, Kit.CARVED);
            s.set(c + f[0] * 4, CLOCK_BAND, c + f[1] * 4, Kit.topSlab(Kit.TRIM_SLAB));
        }
        // The corbels and the stage of the clocks, a band of trim above and below the faces.
        stairRing(s, c, c, 4, CLOCK_CORBEL, Kit.TRIM_STAIRS, true);
        s.fill(0, CLOCK_STAGE, 0, 8, CLOCK_FLOOR - 1, 8, Kit.WALL);
        s.fill(2, CLOCK_STAGE, 2, 6, CLOCK_FLOOR - 1, 6, Structure.AIR);
        s.walls(0, CLOCK_STAGE, 0, 8, CLOCK_STAGE, 8, Kit.TRIM);
        s.walls(0, CLOCK_FLOOR, 0, 8, CLOCK_FLOOR, 8, Kit.TRIM);
        squareQuoins(s, 0, 8, CLOCK_STAGE + 1, CLOCK_FLOOR - 1);
        for (int[] f : faces()) {
            clockFace(s, c, f[0], f[1]);
        }
        // The belfry: walls on a slope of trim, open arches on every side, the bell on a beam, lanterns on the floor.
        stairRing(s, c, c, 4, CLOCK_FLOOR + 1, Kit.TRIM_STAIRS, false);
        s.walls(1, CLOCK_FLOOR + 1, 1, 7, CLOCK_CORNICE, 7, Kit.WALL);
        squareQuoins(s, 1, 7, CLOCK_FLOOR + 1, CLOCK_CORNICE);
        s.fill(2, CLOCK_FLOOR, 2, 6, CLOCK_FLOOR, 6, SPRUCE_PLANKS);
        lancet(s, c, 7, CLOCK_FLOOR + 1, CLOCK_FLOOR + 3, true, Kit.WALL_STAIRS, null);
        lancet(s, c, 1, CLOCK_FLOOR + 1, CLOCK_FLOOR + 3, true, Kit.WALL_STAIRS, null);
        lancet(s, 1, c, CLOCK_FLOOR + 1, CLOCK_FLOOR + 3, false, Kit.WALL_STAIRS, null);
        lancet(s, 7, c, CLOCK_FLOOR + 1, CLOCK_FLOOR + 3, false, Kit.WALL_STAIRS, null);
        s.fill(2, CLOCK_FLOOR + 4, c, 6, CLOCK_FLOOR + 4, c, Kit.log("minecraft:spruce_log", "x"));
        s.set(c, CLOCK_FLOOR + 3, c, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        s.set(2, CLOCK_FLOOR + 1, 2, Kit.lantern()).set(6, CLOCK_FLOOR + 1, 2, Kit.lantern()).set(2, CLOCK_FLOOR + 1, 6, Kit.lantern());
        // The cornice with a lantern on every corner, the ceiling of the belfry and the spire.
        stairRing(s, c, c, 4, CLOCK_CORNICE, Kit.TRIM_STAIRS, true);
        s.fill(2, CLOCK_CORNICE, 2, 6, CLOCK_CORNICE, 6, SPRUCE_PLANKS);
        for (int[] q : new int[][]{{0, 0}, {8, 0}, {0, 8}, {8, 8}}) {
            s.set(q[0], CLOCK_CORNICE + 1, q[1], Kit.WALL_FENCE).set(q[0], CLOCK_CORNICE + 2, q[1], Kit.lantern());
        }
        int top = squareSpire(s, c, c, 3, CLOCK_CORNICE + 1, 3, Kit.SLATE);
        s.set(c, top, c, GOLD);
        s.set(c, top + 1, c, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        clockStair(s);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * A steep square spire: rings of stairs that shrink by one block on every side, the first only its stairs (the eaves)
     * and the others standing on {@code rise - 1} courses of full blocks, closed by a block in the middle of the last ring.
     *
     * @return the layer above the point
     */
    private static int squareSpire(Structure s, int cx, int cz, int radius, int y, int rise, Kit.Roof roof) {
        int layer = y;
        for (int r = radius; r >= 1; r--) {
            if (r < radius) {
                for (int k = 0; k < rise - 1; k++) {
                    s.fill(cx - r, layer, cz - r, cx + r, layer, cz + r, roof.block());
                    layer++;
                }
            }
            if (r > 1) {
                s.fill(cx - r + 1, layer, cz - r + 1, cx + r - 1, layer, cz + r - 1, roof.block());
            } else {
                s.set(cx, layer, cz, roof.block());
            }
            stairRing(s, cx, cz, r, layer, roof.stairs(), false);
            layer++;
        }
        return layer;
    }

    /** The four sides as steps outwards along x and z: south, east, north, west. */
    private static int[][] faces() {
        return new int[][]{{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    }

    /** The name of the side a step along x or z leads to. */
    private static String side(int dx, int dz) {
        return dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
    }

    /** Rough quoins on the corners of a square from one layer to another. */
    private static void squareQuoins(Structure s, int lo, int hi, int y1, int y2) {
        for (int[] q : new int[][]{{lo, lo}, {hi, lo}, {lo, hi}, {hi, hi}}) {
            s.fill(q[0], y1, q[1], q[0], y2, q[1], Kit.ROUGH);
        }
    }

    /**
     * A clock face on one side of the stage of the clocks, the side named by its step outwards {@code (ox, oz)}: a round
     * white face seven blocks across set in the inner wall, its dark ring standing in the outer wall, hands of black
     * showing three o'clock, buttons for twelve, three, six and nine and a glowing arbor in the middle.
     */
    private static void clockFace(Structure s, int c, int ox, int oz) {
        // Right along the face as seen from outside: for the south face east, for the east face north, and so on.
        int rx = oz;
        int rz = -ox;
        String outward = ox > 0 ? "east" : ox < 0 ? "west" : oz > 0 ? "south" : "north";
        for (int v = -3; v <= 3; v++) {
            for (int u = -3; u <= 3; u++) {
                if (!Kit.inDisc(u, v, 7)) {
                    continue;
                }
                int y = CLOCK_MIDDLE + v;
                int innerX = c + ox * 3 + rx * u;
                int innerZ = c + oz * 3 + rz * u;
                int outerX = c + ox * 4 + rx * u;
                int outerZ = c + oz * 4 + rz * u;
                if (Kit.onRim(u, v, 7)) {
                    s.set(innerX, y, innerZ, DARK).set(outerX, y, outerZ, DARK);
                    continue;
                }
                boolean hand = u == 0 && v >= 0 && v <= 2 || v == 0 && u >= 0 && u <= 1;
                s.set(innerX, y, innerZ, hand ? "minecraft:black_concrete" : "minecraft:white_concrete");
                s.set(outerX, y, outerZ, Structure.AIR);
                boolean hour = Math.abs(u) + Math.abs(v) == 2 && (u == 0 || v == 0);
                if (hour) {
                    s.set(outerX, y, outerZ, "minecraft:polished_blackstone_button[face=wall,facing=" + outward + ",powered=false]");
                }
            }
        }
        s.set(c + ox * 4, CLOCK_MIDDLE, c + oz * 4, "minecraft:end_rod[facing=" + outward + "]");
    }

    /**
     * The stair inside the clock tower: steps along the four walls around an open middle, from the floor by the door up to
     * the floor of the belfry, an upturned step under each, torches on the walls and lanterns hanging in the middle.
     */
    private static void clockStair(Structure s) {
        int[][] ring = {{5, 6}, {6, 6}, {6, 5}, {6, 4}, {6, 3}, {6, 2}, {5, 2}, {4, 2}, {3, 2}, {2, 2}, {2, 3}, {2, 4}, {2, 5},
                {2, 6}, {3, 6}, {4, 6}};
        int steps = CLOCK_FLOOR;
        for (int k = 0; k < steps; k++) {
            int[] here = ring[k % ring.length];
            int[] next = ring[(k + 1) % ring.length];
            String facing = next[0] > here[0] ? "east" : next[0] < here[0] ? "west" : next[1] > here[1] ? "south" : "north";
            String back = facing.equals("east") ? "west" : facing.equals("west") ? "east" : facing.equals("south") ? "north" : "south";
            int y = k + 1;
            s.set(here[0], y, here[1], Kit.stairs(Kit.WALL_STAIRS, facing));
            if (y > 1) {
                s.set(here[0], y - 1, here[1], Kit.upside(Kit.WALL_STAIRS, back));
            }
            // Holes in the floor of the belfry over the last steps.
            if (y + 2 >= CLOCK_FLOOR) {
                s.set(here[0], CLOCK_FLOOR, here[1], y == CLOCK_FLOOR ? Kit.stairs(Kit.WALL_STAIRS, facing) : Structure.AIR);
            }
            if (k % 4 == 2 && y + 2 < CLOCK_FLOOR) {
                String torch = here[1] == 6 ? "north" : here[0] == 6 ? "west" : here[1] == 2 ? "south" : "east";
                s.set(here[0], y + 2, here[1], Kit.wallTorch(torch));
            }
        }
        // Lanterns hanging in the open middle.
        s.fill(4, 8, 4, 4, CLOCK_FLOOR - 1, 4, Kit.chain());
        s.set(4, 13, 4, Kit.hangingLantern()).set(4, 7, 4, Kit.hangingLantern());
    }

    // ---------------------------------------------------------------- the park

    /**
     * A small park for the town, laid flush with the street: a low hedge of flowering azalea all round, a wide gate between
     * two stone piers with lanterns at the front and narrow gaps on the other sides. In the middle a pond in a kerb of rough
     * and mossy stone with a wooden footbridge across it carries the path from the gate to a gazebo at the back, which has
     * a railing, benches and a hip roof of its own with a lantern on its point. A broad oak and a cherry in bloom stand on
     * either side of the gazebo, a slender spruce and a birch in the front corners; paths of trim run round the pond, and
     * benches, lamp posts and beds of flowers line them.
     */
    static Structure park() {
        int n = 17;
        Structure s = new Structure(n, 13, n);
        boolean[][] water = new boolean[n][n];
        for (int z = 8; z <= 10; z++) {
            int half = z == 9 ? 4 : 3;
            for (int x = 8 - half; x <= 8 + half; x++) {
                water[x][z] = true;
            }
        }
        boolean[][] rim = around(water, n);
        boolean[][] pondAll = or(water, rim);
        boolean[][] ring = around(pondAll, n);
        // The ground: lawns, paths, the bed of the pond.
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                String ground = GRASS;
                if (water[x][z]) {
                    ground = "minecraft:clay";
                } else if (rim[x][z]) {
                    ground = Kit.ROUGH;
                } else if (ring[x][z] && x >= 2 && x <= 14) {
                    ground = Kit.TRIM;
                }
                s.set(x, 0, z, ground);
            }
        }
        // Paths from the gates: the front path three wide, one wide from the sides and through the gazebo to the back.
        s.fill(7, 0, 12, 9, 0, 16, Kit.TRIM);
        s.fill(0, 0, 9, 1, 0, 9, Kit.TRIM).fill(15, 0, 9, 16, 0, 9, Kit.TRIM);
        s.set(8, 0, 0, Kit.TRIM);
        // The pond: water inside a kerb of rough stone.
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                if (water[x][z]) {
                    s.set(x, 1, z, WATER);
                } else if (rim[x][z]) {
                    s.set(x, 1, z, (x + z) % 3 == 0 ? "minecraft:mossy_cobblestone" : Kit.ROUGH);
                }
            }
        }
        // The footbridge: a step at either end and a deck of slabs over the kerb and the water, rails of fence.
        s.set(8, 1, 12, Kit.stairs(SPRUCE_STAIRS, "north")).set(8, 1, 6, Kit.stairs(SPRUCE_STAIRS, "south"));
        s.fill(8, 2, 7, 8, 2, 11, Kit.slab(SPRUCE_SLAB));
        s.fill(7, 2, 7, 7, 2, 11, OAK_FENCE).fill(9, 2, 7, 9, 2, 11, OAK_FENCE);
        // The hedge with its gates.
        for (int i = 0; i < n; i++) {
            for (int[] p : new int[][]{{i, 0}, {i, n - 1}, {0, i}, {n - 1, i}}) {
                boolean gate = p[1] == n - 1 && p[0] >= 7 && p[0] <= 9 || p[1] == 0 && p[0] == 8 || (p[0] == 0 || p[0] == n - 1) && p[1] == 9;
                if (!gate) {
                    s.set(p[0], 0, p[1], DIRT).set(p[0], 1, p[1], Kit.leaves(i % 4 == 1 ? "flowering_azalea" : "azalea"));
                }
            }
        }
        for (int x : new int[]{6, 10}) {
            s.set(x, 1, n - 1, Kit.ROUGH).set(x, 2, n - 1, Kit.WALL).set(x, 3, n - 1, Kit.lantern());
        }
        gazebo(s);
        oak(s, 2, 3);
        cherry(s, 14, 3);
        spruce(s, 2, 14);
        birch(s, 14, 14);
        // Lamp posts at the corners of the path round the pond.
        lampPost(s, 3, 1, 7, 2);
        lampPost(s, 13, 1, 7, 2);
        lampPost(s, 3, 1, 11, 2);
        lampPost(s, 13, 1, 11, 2);
        // Benches looking at the pond.
        s.fill(1, 1, 7, 1, 1, 8, Kit.stairs(SPRUCE_STAIRS, "west")).fill(1, 1, 10, 1, 1, 11, Kit.stairs(SPRUCE_STAIRS, "west"));
        s.fill(15, 1, 7, 15, 1, 8, Kit.stairs(SPRUCE_STAIRS, "east")).fill(15, 1, 10, 15, 1, 11, Kit.stairs(SPRUCE_STAIRS, "east"));
        s.fill(6, 1, 13, 6, 1, 14, Kit.stairs(SPRUCE_STAIRS, "west")).fill(10, 1, 13, 10, 1, 14, Kit.stairs(SPRUCE_STAIRS, "east"));
        // Beds of flowers behind the benches by the front path, in stripes of colour.
        String[][] beds = {{"minecraft:red_tulip", "minecraft:oxeye_daisy"}, {"minecraft:allium", "minecraft:dandelion"}};
        for (int z = 13; z <= 15; z++) {
            for (int i = 0; i < 2; i++) {
                s.set(4 + i, 1, z, beds[0][(z + i) % 2]);
                s.set(11 + i, 1, z, beds[1][(z + i) % 2]);
            }
        }
        // Flowers on the lawns.
        String[] flowers = {"minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:allium",
                "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:pink_tulip"};
        for (int z = 1; z < n - 1; z++) {
            for (int x = 1; x < n - 1; x++) {
                if (s.get(x, 0, z).equals(GRASS) && s.get(x, 1, z).equals(Structure.AIR) && (x * 3 + z * 5) % 7 == 0) {
                    s.set(x, 1, z, flowers[(x + z * 2) % flowers.length]);
                }
            }
        }
        s.connect();
        return s;
    }

    private static boolean[][] around(boolean[][] inside, int n) {
        boolean[][] out = new boolean[n][n];
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                if (inside[x][z]) {
                    continue;
                }
                for (int dz = -1; dz <= 1 && !out[x][z]; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx >= 0 && nz >= 0 && nx < n && nz < n && inside[nx][nz]) {
                            out[x][z] = true;
                            break;
                        }
                    }
                }
            }
        }
        return out;
    }

    private static boolean[][] or(boolean[][] a, boolean[][] b) {
        boolean[][] out = new boolean[a.length][a.length];
        for (int z = 0; z < a.length; z++) {
            for (int x = 0; x < a.length; x++) {
                out[x][z] = a[x][z] || b[x][z];
            }
        }
        return out;
    }

    /**
     * The gazebo at the back of the park: a floor of planks, four posts with a railing between them open to the front and
     * the back, benches inside, a ring of beams and a hip roof with a lantern on its point and another hanging under it.
     */
    private static void gazebo(Structure s) {
        s.fill(6, 0, 1, 10, 0, 5, SPRUCE_PLANKS);
        for (int[] p : new int[][]{{6, 1}, {10, 1}, {6, 5}, {10, 5}}) {
            s.fill(p[0], 1, p[1], p[0], 3, p[1], Kit.log("minecraft:oak_log", "y"));
        }
        s.fill(6, 1, 2, 6, 1, 4, OAK_FENCE).fill(10, 1, 2, 10, 1, 4, OAK_FENCE);
        s.set(7, 1, 1, OAK_FENCE).set(9, 1, 1, OAK_FENCE).set(7, 1, 5, OAK_FENCE).set(9, 1, 5, OAK_FENCE);
        s.fill(7, 1, 2, 7, 1, 4, Kit.stairs(SPRUCE_STAIRS, "west")).fill(9, 1, 2, 9, 1, 4, Kit.stairs(SPRUCE_STAIRS, "east"));
        s.beam(6, 1, 10, 5, 4, "minecraft:oak_log");
        s.hipRoof(5, 11, 0, 6, 4, Kit.TEAL.stairs(), Kit.TEAL.slab());
        s.set(8, 7, 3, Kit.TEAL.block()).set(8, 8, 3, Kit.lantern());
        s.fill(8, 4, 3, 8, 6, 3, Kit.chain());
        s.set(8, 3, 3, Kit.hangingLantern());
    }

    /**
     * One layer of the crown of a tree: the leaves of a round plan of the given radius around the trunk, a few of its edge
     * left out so that the crown is ragged; logs and other blocks already there stay.
     */
    private static void crown(Structure s, int x, int y, int z, double radius, String leaves, int seed) {
        int r = (int) Math.ceil(radius);
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                boolean edge = d > radius - 1;
                if (d > radius || edge && Math.floorMod(dx * 7 + dz * 13 + y * 5 + seed, 5) == 0) {
                    continue;
                }
                if (s.inside(x + dx, y, z + dz) && !s.solid(x + dx, y, z + dz)) {
                    s.set(x + dx, y, z + dz, leaves);
                }
            }
        }
    }

    /** A spruce: a tall trunk in a cone of needles, wide and narrow layers in turn. */
    private static void spruce(Structure s, int x, int z) {
        s.set(x, 0, z, DIRT);
        s.fill(x, 1, z, x, 9, z, Kit.log("minecraft:spruce_log", "y"));
        double[] radius = {0, 0, 0, 2.6, 2.0, 2.3, 1.6, 1.9, 1.2, 1.4, 1.0, 0.5};
        for (int y = 3; y <= 11; y++) {
            crown(s, x, y, z, radius[y], Kit.leaves("spruce"), 1);
        }
    }

    /** A birch: a tall white trunk and a narrow crown, a shroomlight hidden in it. */
    private static void birch(Structure s, int x, int z) {
        s.set(x, 0, z, DIRT);
        s.fill(x, 1, z, x, 7, z, Kit.log("minecraft:birch_log", "y"));
        s.set(x, 7, z, "minecraft:shroomlight");
        double[] radius = {0, 0, 0, 0, 1.0, 1.6, 1.8, 1.5, 1.0, 0.5};
        for (int y = 4; y <= 9; y++) {
            crown(s, x, y, z, radius[y], Kit.leaves("birch"), 2);
        }
    }

    /** A broad oak: a short trunk with branches and a round, ragged crown, a shroomlight hidden in its heart. */
    private static void oak(Structure s, int x, int z) {
        s.set(x, 0, z, DIRT);
        s.fill(x, 1, z, x, 5, z, Kit.log("minecraft:oak_log", "y"));
        s.set(x + 1, 4, z, Kit.log("minecraft:oak_log", "x")).set(x - 1, 5, z, Kit.log("minecraft:oak_log", "x"));
        s.set(x, 5, z - 1, Kit.log("minecraft:oak_log", "z"));
        s.set(x, 6, z, "minecraft:shroomlight");
        double[] radius = {0, 0, 0, 0, 2.3, 3.0, 2.9, 2.2, 1.0};
        for (int y = 4; y <= 8; y++) {
            crown(s, x, y, z, radius[y], Kit.leaves("oak"), 3);
        }
    }

    /** A cherry in bloom: a short trunk forking into a wide, flat crown of pink with blossoms hanging under its edge. */
    private static void cherry(Structure s, int x, int z) {
        s.set(x, 0, z, DIRT);
        s.fill(x, 1, z, x, 4, z, Kit.log("minecraft:cherry_log", "y"));
        s.set(x + 1, 5, z, Kit.log("minecraft:cherry_log", "x")).set(x - 1, 5, z, Kit.log("minecraft:cherry_log", "x"));
        s.set(x, 5, z + 1, Kit.log("minecraft:cherry_log", "z"));
        s.set(x, 6, z, "minecraft:shroomlight");
        double[] radius = {0, 0, 0, 0, 0, 2.7, 3.0, 2.1, 1.0};
        for (int y = 5; y <= 8; y++) {
            crown(s, x, y, z, radius[y], Kit.leaves("cherry"), 4);
        }
        for (int[] d : new int[][]{{2, 1}, {-2, -1}, {1, -2}, {-1, 2}, {-2, 2}, {2, -2}}) {
            if (s.inside(x + d[0], 4, z + d[1]) && !s.solid(x + d[0], 4, z + d[1])) {
                s.set(x + d[0], 4, z + d[1], Kit.leaves("cherry"));
            }
        }
    }
}
