package io.github.sekerator.kingdomworld.generator;

import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.BARREL;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.BED;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.COBBLE;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.COBBLE_STAIRS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.DOOR;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.HANGING_LANTERN;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.LANTERN;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.PANE_X;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.PANE_Z;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.ROOF_SLAB;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.ROOF_STAIRS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.SPRUCE_LOG;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.SPRUCE_PLANKS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.STONE_BRICKS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.STONE_BRICK_STAIRS;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.STONE_BRICK_WALL;
import static io.github.sekerator.kingdomworld.generator.BuildingGenerator.TORCH;

import java.util.ArrayList;
import java.util.List;

/**
 * The buildings of war: a timber watch tower for villages, a round stone tower for the corner of a city wall, a gatehouse
 * that stands inside the wall over its gate, a large hall of the garrison and a yard where soldiers train.
 */
final class Military {
    private static final String STRIPPED_SPRUCE = "minecraft:stripped_spruce_log";
    private static final String STONE_BRICK_SLAB_TOP = "minecraft:stone_brick_slab[type=top,waterlogged=false]";
    private static final String PRISMARINE_STAIRS = "minecraft:dark_prismarine_stairs";
    private static final String PRISMARINE_SLAB = "minecraft:dark_prismarine_slab";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String GRINDSTONE = "minecraft:grindstone[face=floor,facing=south]";
    private static final String TABLE_TOP = "minecraft:oak_pressure_plate[powered=false]";
    private static final String BARE_FENCE = fence(false, false, false, false);
    private static final String FENCE_X = fence(false, false, true, true);
    private static final String FENCE_Z = fence(true, true, false, false);

    private Military() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("wooden_watchtower", "defense", "tower", "minecraft:oak_fence", 3, 0, 6, woodenWatchtower(),
                "tower", 1, ""));
        list.add(new BuildingGenerator.Building("corner_tower", "defense", "tower", "minecraft:stone_brick_wall", 4, 0, 8, cornerTower(),
                "tower", 1, ""));
        list.add(new BuildingGenerator.Building("gatehouse", "defense", "gatehouse", "minecraft:iron_bars", 6, 1, 4, gatehouse(),
                "tower", 1, ""));
        list.add(new BuildingGenerator.Building("barracks_hall", "defense", "barracks", "kingdomworld:barracks", 6, 0, 10, barracksHall(),
                "barracks", 1, ""));
        list.add(new BuildingGenerator.Building("drill_yard", "defense", "yard", "minecraft:target", 6, 1, 10, drillYard(),
                "none", 1, ""));
        return list;
    }

    private static String stairs(String block, String facing) {
        return block + "[facing=" + facing + ",half=bottom,shape=straight,waterlogged=false]";
    }

    private static String upsideDown(String block, String facing) {
        return block + "[facing=" + facing + ",half=top,shape=straight,waterlogged=false]";
    }

    private static String wallTorch(String facing) {
        return "minecraft:wall_torch[facing=" + facing + "]";
    }

    private static String ladder(String facing) {
        return "minecraft:ladder[facing=" + facing + ",waterlogged=false]";
    }

    private static String fence(boolean north, boolean south, boolean east, boolean west) {
        return "minecraft:spruce_fence[east=" + east + ",north=" + north + ",south=" + south + ",waterlogged=false,west=" + west + "]";
    }

    /**
     * A table of a fence with a pressure plate on it.
     */
    private static void table(Structure s, int x, int y, int z) {
        s.set(x, y, z, BARE_FENCE).set(x, y + 1, z, TABLE_TOP);
    }

    /**
     * Only the lower rings of a hip roof: rings of stairs rising one block per ring from the eaves on all four sides, with
     * turned stairs at the corners, so that the top can be closed by something else (a flat deck, a turret).
     */
    private static void hipRings(Structure s, int x1, int x2, int z1, int z2, int y, int rings, String stairs) {
        for (int ring = 0; ring < rings; ring++) {
            int west = x1 + ring;
            int east = x2 - ring;
            int back = z1 + ring;
            int front = z2 - ring;
            int height = y + ring;
            s.fill(west, height, back, east, height, back, stairs + "[facing=south,half=bottom,shape=straight,waterlogged=false]");
            s.fill(west, height, front, east, height, front, stairs + "[facing=north,half=bottom,shape=straight,waterlogged=false]");
            s.fill(west, height, back + 1, west, height, front - 1, stairs + "[facing=east,half=bottom,shape=straight,waterlogged=false]");
            s.fill(east, height, back + 1, east, height, front - 1, stairs + "[facing=west,half=bottom,shape=straight,waterlogged=false]");
            s.set(west, height, back, stairs + "[facing=south,half=bottom,shape=outer_left,waterlogged=false]");
            s.set(east, height, back, stairs + "[facing=south,half=bottom,shape=outer_right,waterlogged=false]");
            s.set(west, height, front, stairs + "[facing=north,half=bottom,shape=outer_right,waterlogged=false]");
            s.set(east, height, front, stairs + "[facing=north,half=bottom,shape=outer_left,waterlogged=false]");
        }
    }

    // ---------------------------------------------------------------- the buildings

    /**
     * A timber watch tower for villages: four tall spruce legs with braces on a small stone base, a ladder up a mast in the
     * middle to an open platform with a rail, and a tented roof on corner posts with a lantern hanging under it. The foot
     * stands open.
     */
    static Structure woodenWatchtower() {
        Structure s = new Structure(7, 15, 7);
        s.fill(1, 0, 1, 5, 0, 5, COBBLE);
        s.set(3, 0, 6, stairs(COBBLE_STAIRS, "north"));
        int[][] legs = {{1, 1}, {5, 1}, {1, 5}, {5, 5}};
        for (int[] leg : legs) {
            s.set(leg[0], 1, leg[1], STONE_BRICKS);
            s.fill(leg[0], 2, leg[1], leg[0], 7, leg[1], SPRUCE_LOG + "[axis=y]");
        }
        // A ring of stripped logs halfway up holds the legs together, knee braces carry the platform.
        s.fill(2, 4, 1, 4, 4, 1, STRIPPED_SPRUCE + "[axis=x]").fill(2, 4, 5, 4, 4, 5, STRIPPED_SPRUCE + "[axis=x]");
        s.fill(1, 4, 2, 1, 4, 4, STRIPPED_SPRUCE + "[axis=z]").fill(5, 4, 2, 5, 4, 4, STRIPPED_SPRUCE + "[axis=z]");
        for (int z : new int[]{1, 5}) {
            s.set(2, 7, z, upsideDown(ROOF_STAIRS, "west")).set(4, 7, z, upsideDown(ROOF_STAIRS, "east"));
        }
        for (int x : new int[]{1, 5}) {
            s.set(x, 7, 2, upsideDown(ROOF_STAIRS, "north")).set(x, 7, 4, upsideDown(ROOF_STAIRS, "south"));
        }
        // The mast in the middle with the ladder on its front.
        s.set(3, 1, 3, STONE_BRICKS);
        s.fill(3, 2, 3, 3, 7, 3, STRIPPED_SPRUCE + "[axis=y]");
        s.set(2, 3, 3, wallTorch("west")).set(4, 3, 3, wallTorch("east"));
        // The platform reaches out over the legs.
        s.beam(0, 0, 6, 6, 8, SPRUCE_LOG);
        s.fill(1, 8, 1, 5, 8, 5, SPRUCE_PLANKS);
        for (int y = 1; y <= 8; y++) {
            s.set(3, y, 4, ladder("south"));
        }
        s.fill(1, 9, 0, 5, 9, 0, FENCE_X).fill(1, 9, 6, 5, 9, 6, FENCE_X);
        s.fill(0, 9, 1, 0, 9, 5, FENCE_Z).fill(6, 9, 1, 6, 9, 5, FENCE_Z);
        s.corners(0, 9, 0, 6, 10, 6, SPRUCE_LOG);
        s.hipRoof(0, 6, 0, 6, 11, ROOF_STAIRS, ROOF_SLAB);
        s.fill(3, 12, 3, 3, 13, 3, "minecraft:chain[axis=y,waterlogged=false]");
        s.set(3, 11, 3, HANGING_LANTERN);
        return s;
    }

    /**
     * Whether a column belongs to the octagon of the corner tower: a box of 9 by 8 with its corners cut.
     */
    private static boolean inOctagon(int x, int z) {
        if (x < 0 || x > 8 || z < 0 || z > 7) {
            return false;
        }
        return Math.min(x, 8 - x) + Math.min(z, 7 - z) >= 2;
    }

    private static boolean octagonWall(int x, int z) {
        if (!inOctagon(x, z)) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!inOctagon(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A strong stone tower for the corner of a city wall, octagonal in plan: three floors with arrow slits, a ladder up
     * the back wall to a fighting platform with battlements, and over the platform a tented roof of dark prismarine on
     * stone posts.
     */
    static Structure cornerTower() {
        Structure s = new Structure(9, 20, 9);
        for (int z = 0; z <= 7; z++) {
            for (int x = 0; x <= 8; x++) {
                if (!inOctagon(x, z)) {
                    continue;
                }
                s.set(x, 0, z, COBBLE);
                if (octagonWall(x, z)) {
                    // The cut corners in rough stone, the straight faces in bricks.
                    boolean diagonal = Math.min(x, 8 - x) > 0 && Math.min(z, 7 - z) > 0;
                    s.fill(x, 1, z, x, 12, z, diagonal ? COBBLE : STONE_BRICKS);
                    s.set(x, 1, z, COBBLE);
                } else {
                    s.set(x, 0, z, SPRUCE_PLANKS);
                    s.set(x, 4, z, SPRUCE_PLANKS).set(x, 8, z, SPRUCE_PLANKS);
                }
                s.set(x, 12, z, STONE_BRICKS);
            }
        }
        s.set(4, 0, 8, stairs(COBBLE_STAIRS, "north"));
        s.door(4, 1, 7, DOOR, "north", "left");
        s.set(3, 3, 8, wallTorch("south")).set(5, 3, 8, wallTorch("south"));
        // Arrow slits on every floor: on the sides and the back, and on the front above the door.
        for (int y : new int[]{2, 6, 10}) {
            s.fill(0, y, 4, 0, y + 1, 4, Structure.AIR).fill(8, y, 3, 8, y + 1, 3, Structure.AIR);
            s.fill(3, y, 0, 3, y + 1, 0, Structure.AIR).fill(5, y, 0, 5, y + 1, 0, Structure.AIR);
            if (y > 2) {
                s.fill(4, y, 7, 4, y + 1, 7, Structure.AIR);
            }
            s.set(1, y + 1, 3, wallTorch("east")).set(7, y + 1, 4, wallTorch("west"));
        }
        // A ladder up the back wall through every floor to the platform.
        for (int y = 1; y <= 12; y++) {
            s.set(4, y, 1, ladder("south"));
        }
        // The ground floor: stores of the guard.
        s.set(7, 1, 3, BARREL).set(7, 1, 4, BARREL).set(7, 2, 4, BARREL);
        s.set(1, 1, 4, "minecraft:chest[facing=east,type=single,waterlogged=false]");
        s.set(2, 1, 5, GRINDSTONE);
        // The middle floor: a table for the watch and their gear.
        s.set(2, 5, 3, "minecraft:chest[facing=east,type=single,waterlogged=false]").set(2, 5, 4, BARREL);
        table(s, 6, 5, 4);
        s.set(6, 5, 5, stairs(ROOF_STAIRS, "south"));
        // The top floor: arrows and powder kept dry.
        s.set(7, 9, 3, BARREL).set(7, 9, 4, BARREL).set(1, 9, 4, BARREL);
        s.set(3, 9, 6, "minecraft:fletching_table");
        // Battlements on the outer ring of the platform; eight posts of wall carry the roof.
        for (int[] post : new int[][]{{2, 0}, {6, 0}, {2, 7}, {6, 7}, {0, 2}, {0, 5}, {8, 2}, {8, 5}}) {
            s.fill(post[0], 13, post[1], post[0], 14, post[1], STONE_BRICK_WALL);
        }
        for (int[] merlon : new int[][]{{4, 0}, {4, 7}, {1, 1}, {7, 1}, {1, 6}, {7, 6}}) {
            s.set(merlon[0], 13, merlon[1], STONE_BRICKS);
        }
        s.hipRoof(0, 8, 0, 7, 15, PRISMARINE_STAIRS, PRISMARINE_SLAB);
        // The corners of the eaves follow the cut corners of the tower.
        s.set(0, 15, 0, Structure.AIR).set(8, 15, 0, Structure.AIR).set(0, 15, 7, Structure.AIR).set(8, 15, 7, Structure.AIR);
        s.set(1, 15, 1, PRISMARINE_STAIRS + "[facing=south,half=bottom,shape=outer_left,waterlogged=false]");
        s.set(7, 15, 1, PRISMARINE_STAIRS + "[facing=south,half=bottom,shape=outer_right,waterlogged=false]");
        s.set(1, 15, 6, PRISMARINE_STAIRS + "[facing=north,half=bottom,shape=outer_right,waterlogged=false]");
        s.set(7, 15, 6, PRISMARINE_STAIRS + "[facing=north,half=bottom,shape=outer_left,waterlogged=false]");
        s.set(4, 17, 3, HANGING_LANTERN);
        s.set(4, 19, 3, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        return s;
    }

    /**
     * A gatehouse that stands inside a city wall over its gate: a vaulted passage three wide runs through it to the gate,
     * low arcades through both flanks let the guards walk along the wall, a ladder in the west flank leads to the guard room
     * over the passage with slits to the front, and battlements crown the roof. Its front leans on the wall.
     */
    static Structure gatehouse() {
        Structure s = new Structure(13, 11, 5);
        s.fill(0, 0, 0, 12, 0, 4, STONE_BRICKS);
        s.fill(5, 0, 0, 7, 0, 4, COBBLE);
        s.fill(0, 1, 0, 12, 4, 4, STONE_BRICKS);
        // Rough stone piers at the corners and on both sides of the passage.
        for (int x : new int[]{0, 4, 8, 12}) {
            s.fill(x, 1, 0, x, 4, 0, COBBLE).fill(x, 1, 4, x, 4, 4, COBBLE);
        }
        s.fill(5, 1, 0, 7, 4, 4, Structure.AIR);
        // The arcades through the flanks, under a vault.
        for (int[] flank : new int[][]{{0, 4}, {8, 12}}) {
            s.fill(flank[0], 1, 1, flank[1], 2, 3, Structure.AIR);
            for (int x = flank[0]; x <= flank[1]; x++) {
                s.set(x, 3, 1, upsideDown(STONE_BRICK_STAIRS, "north"));
                s.set(x, 3, 2, STONE_BRICK_SLAB_TOP);
                s.set(x, 3, 3, upsideDown(STONE_BRICK_STAIRS, "south"));
            }
        }
        // Torches in niches of the piers light the passage.
        s.set(4, 3, 0, wallTorch("east")).set(4, 3, 4, wallTorch("east"));
        s.set(8, 3, 0, wallTorch("west")).set(8, 3, 4, wallTorch("west"));
        // The floor of the guard room with a timber sill around it, and the vault over the passage.
        s.fill(0, 5, 0, 12, 5, 4, STONE_BRICKS);
        s.beam(0, 0, 12, 4, 5, SPRUCE_LOG);
        s.fill(1, 5, 1, 11, 5, 3, SPRUCE_PLANKS);
        for (int z = 0; z <= 4; z++) {
            s.set(5, 5, z, upsideDown(STONE_BRICK_STAIRS, "west"));
            s.set(6, 5, z, STONE_BRICK_SLAB_TOP);
            s.set(7, 5, z, upsideDown(STONE_BRICK_STAIRS, "east"));
        }
        // The guard room.
        s.walls(0, 6, 0, 12, 8, 4, STONE_BRICKS);
        for (int x : new int[]{0, 4, 8, 12}) {
            s.fill(x, 6, 0, x, 8, 0, SPRUCE_LOG + "[axis=y]").fill(x, 6, 4, x, 8, 4, SPRUCE_LOG + "[axis=y]");
        }
        for (int x : new int[]{2, 6, 10}) {
            s.fill(x, 7, 4, x, 8, 4, Structure.AIR);
        }
        s.set(6, 7, 0, PANE_X).set(10, 7, 0, PANE_X);
        s.set(0, 7, 2, PANE_Z).set(12, 7, 2, PANE_Z);
        s.fill(0, 9, 0, 12, 9, 4, STONE_BRICKS);
        for (int y = 1; y <= 9; y++) {
            s.set(2, y, 1, ladder("south"));
        }
        s.set(4, 8, 2, HANGING_LANTERN).set(9, 8, 2, HANGING_LANTERN);
        s.set(10, 6, 1, BARREL).set(11, 6, 1, BARREL).set(11, 7, 1, BARREL);
        s.set(11, 6, 3, "minecraft:chest[facing=west,type=single,waterlogged=false]");
        table(s, 8, 6, 2);
        s.set(7, 6, 2, stairs(ROOF_STAIRS, "west"));
        s.set(1, 6, 3, BARREL);
        // Battlements on the roof.
        for (int x = 0; x <= 12; x += 2) {
            s.set(x, 10, 0, STONE_BRICKS).set(x, 10, 4, STONE_BRICKS);
        }
        s.set(0, 10, 2, STONE_BRICKS).set(12, 10, 2, STONE_BRICKS);
        s.set(6, 10, 2, TORCH).set(11, 10, 2, TORCH);
        return s;
    }

    /**
     * A large stone hall of the garrison: two floors with six bunks along the walls on each, the barracks block, weapon racks
     * and a table below, a ladder between the floors, under a hip roof with a small bell turret on the ridge.
     */
    static Structure barracksHall() {
        Structure s = new Structure(13, 14, 11);
        s.fill(1, 0, 1, 11, 0, 9, COBBLE).fill(2, 0, 2, 10, 0, 8, SPRUCE_PLANKS);
        s.set(6, 0, 10, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 11, 7, 9, STONE_BRICKS);
        s.walls(1, 1, 1, 11, 1, 9, COBBLE);
        for (int[] corner : new int[][]{{1, 1}, {11, 1}, {1, 9}, {11, 9}}) {
            s.fill(corner[0], 1, corner[1], corner[0], 7, corner[1], COBBLE);
        }
        // Timber bands at the upper floor and under the eaves.
        s.beam(1, 1, 11, 9, 4, SPRUCE_LOG);
        s.fill(2, 4, 2, 10, 4, 8, SPRUCE_PLANKS);
        s.beam(1, 1, 11, 9, 8, SPRUCE_LOG);
        for (int x : new int[]{3, 9}) {
            s.set(x, 2, 9, PANE_X).set(x, 2, 1, PANE_X);
        }
        for (int x : new int[]{3, 6, 9}) {
            s.set(x, 6, 9, PANE_X);
        }
        s.set(3, 6, 1, PANE_X).set(9, 6, 1, PANE_X);
        for (int z : new int[]{5, 7}) {
            s.set(1, 2, z, PANE_Z).set(11, 2, z, PANE_Z);
        }
        for (int z : new int[]{4, 6}) {
            s.set(1, 6, z, PANE_Z).set(11, 6, z, PANE_Z);
        }
        s.door(6, 1, 9, DOOR, "north", "left");
        s.set(5, 3, 10, wallTorch("south")).set(7, 3, 10, wallTorch("south"));
        // A ladder on the back wall through the floor.
        for (int y = 1; y <= 5; y++) {
            s.set(6, y, 2, ladder("south"));
        }
        // The ground floor: six bunks along the back wall, the barracks block, weapon racks and a table.
        for (int x : new int[]{2, 3, 4, 8, 9, 10}) {
            s.bed(x, 1, 3, BED, "north", 0, -1);
        }
        s.set(5, 1, 2, BARREL).set(7, 1, 2, BARREL);
        s.set(6, 1, 5, "kingdomworld:barracks");
        s.set(10, 1, 5, GRINDSTONE).set(10, 1, 6, GRINDSTONE);
        s.set(10, 1, 7, BARREL).set(10, 1, 8, BARREL).set(9, 1, 8, BARREL).set(10, 2, 8, BARREL);
        table(s, 3, 1, 7);
        s.set(2, 1, 7, stairs(ROOF_STAIRS, "west")).set(4, 1, 7, stairs(ROOF_STAIRS, "east"));
        s.set(2, 1, 8, BARREL);
        s.set(2, 3, 4, wallTorch("east")).set(10, 3, 4, wallTorch("west"));
        s.set(5, 3, 2, wallTorch("south")).set(7, 3, 2, wallTorch("south"));
        s.set(4, 3, 8, wallTorch("north")).set(8, 3, 8, wallTorch("north"));
        // The upper floor: three bunks along each side wall with chests between them.
        for (int z : new int[]{3, 5, 7}) {
            s.bed(3, 5, z, BED, "west", -1, 0);
            s.bed(9, 5, z, BED, "east", 1, 0);
        }
        s.set(2, 5, 4, "minecraft:chest[facing=east,type=single,waterlogged=false]");
        s.set(10, 5, 4, "minecraft:chest[facing=west,type=single,waterlogged=false]");
        s.set(2, 5, 6, BARREL).set(10, 5, 6, BARREL);
        s.set(5, 5, 2, BARREL).set(7, 5, 2, BARREL);
        table(s, 6, 5, 7);
        s.set(2, 7, 4, wallTorch("east")).set(2, 7, 6, wallTorch("east"));
        s.set(10, 7, 4, wallTorch("west")).set(10, 7, 6, wallTorch("west"));
        s.set(4, 7, 8, wallTorch("north")).set(8, 7, 8, wallTorch("north"));
        s.set(5, 7, 2, wallTorch("south")).set(7, 7, 2, wallTorch("south"));
        // The hip roof, flat on top where the bell turret stands.
        hipRings(s, 0, 12, 0, 10, 8, 3, ROOF_STAIRS);
        s.fill(3, 11, 3, 9, 11, 7, ROOF_SLAB + "[type=bottom,waterlogged=false]");
        s.fill(5, 11, 4, 7, 11, 6, SPRUCE_PLANKS);
        s.set(6, 10, 5, HANGING_LANTERN);
        for (int[] post : new int[][]{{5, 4}, {7, 4}, {5, 6}, {7, 6}}) {
            s.set(post[0], 12, post[1], BARE_FENCE);
        }
        s.set(6, 12, 5, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        hipRings(s, 5, 7, 4, 6, 13, 1, PRISMARINE_STAIRS);
        s.set(6, 13, 5, "minecraft:dark_prismarine");
        return s;
    }

    /**
     * A drill yard level with the street: a floor of coarse dirt and gravel inside a low rail, three archery butts at the
     * back, two training dummies, a corner with a grindstone and barrels, a bench, and lanterns on posts at the corners and
     * at the gap of the entrance.
     */
    static Structure drillYard() {
        Structure s = new Structure(13, 4, 11);
        s.fill(0, 0, 0, 12, 0, 10, "minecraft:coarse_dirt");
        s.fill(5, 0, 1, 7, 0, 10, "minecraft:gravel");
        s.fill(1, 0, 2, 11, 0, 2, "minecraft:gravel");
        s.walls(0, 0, 0, 12, 0, 10, STONE_BRICKS);
        s.fill(5, 0, 10, 7, 0, 10, "minecraft:gravel");
        // The rail with stone posts and lanterns at the corners and at the entrance.
        s.fill(1, 1, 0, 11, 1, 0, FENCE_X);
        s.fill(0, 1, 1, 0, 1, 9, FENCE_Z).fill(12, 1, 1, 12, 1, 9, FENCE_Z);
        s.fill(1, 1, 10, 3, 1, 10, FENCE_X).fill(9, 1, 10, 11, 1, 10, FENCE_X);
        for (int[] post : new int[][]{{0, 0}, {12, 0}, {0, 10}, {12, 10}, {4, 10}, {8, 10}}) {
            s.set(post[0], 1, post[1], STONE_BRICKS).set(post[0], 2, post[1], BARE_FENCE).set(post[0], 3, post[1], LANTERN);
        }
        // Archery butts at the back.
        for (int x : new int[]{3, 6, 9}) {
            s.set(x, 1, 1, HAY).set(x, 2, 1, "minecraft:target[power=0]");
        }
        // Training dummies with arms.
        for (int x : new int[]{3, 9}) {
            s.set(x, 1, 6, BARE_FENCE).set(x, 2, 6, HAY).set(x, 3, 6, "minecraft:carved_pumpkin[facing=south]");
            s.set(x - 1, 2, 6, fence(false, false, true, false)).set(x + 1, 2, 6, fence(false, false, false, true));
        }
        // The weapon rack in the front west corner, a bench and hay in the others.
        s.set(1, 1, 9, GRINDSTONE).set(1, 1, 8, BARREL).set(2, 1, 9, BARREL).set(1, 2, 8, BARREL).set(1, 1, 7, GRINDSTONE);
        s.set(10, 1, 9, stairs(ROOF_STAIRS, "south")).set(11, 1, 9, stairs(ROOF_STAIRS, "south"));
        s.set(11, 1, 1, HAY).set(11, 1, 2, HAY).set(11, 2, 1, HAY).set(1, 1, 1, BARREL);
        return s;
    }
}
