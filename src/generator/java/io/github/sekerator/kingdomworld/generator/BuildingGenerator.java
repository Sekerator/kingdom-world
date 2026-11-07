package io.github.sekerator.kingdomworld.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the built-in building templates of KingdomWorld and writes them as structure files (the format of the structure
 * block) with their descriptions next to them. Run with {@code gradlew generateBuildings}; the result is committed under
 * {@code src/main/resources/data/kingdomworld/kingdomworld_buildings}. Same code, same files: nothing here is random.
 *
 * <p>All buildings face south: their entrance is on the high z side, the anchor is the ground in front of it.
 */
public final class BuildingGenerator {
    static final String COBBLE = "minecraft:cobblestone";
    static final String COBBLE_STAIRS = "minecraft:cobblestone_stairs";
    static final String STONE_BRICKS = "minecraft:stone_bricks";
    static final String STONE_BRICK_STAIRS = "minecraft:stone_brick_stairs";
    static final String STONE_BRICK_WALL = "minecraft:stone_brick_wall";
    static final String OAK_LOG = "minecraft:oak_log";
    static final String SPRUCE_LOG = "minecraft:spruce_log";
    static final String OAK_PLANKS = "minecraft:oak_planks";
    static final String SPRUCE_PLANKS = "minecraft:spruce_planks";
    static final String ROOF_STAIRS = "minecraft:spruce_stairs";
    static final String ROOF_SLAB = "minecraft:spruce_slab";
    static final String DARK_STAIRS = "minecraft:dark_oak_stairs";
    static final String DARK_SLAB = "minecraft:dark_oak_slab";
    static final String PANE_X = "minecraft:glass_pane[east=true,west=true,north=false,south=false,waterlogged=false]";
    static final String PANE_Z = "minecraft:glass_pane[north=true,south=true,east=false,west=false,waterlogged=false]";
    static final String DOOR = "minecraft:spruce_door";
    static final String OAK_DOOR = "minecraft:oak_door";
    static final String BED = "minecraft:red_bed";
    static final String BARREL = "minecraft:barrel[facing=up,open=false]";
    static final String FENCE = "minecraft:oak_fence[north=false,south=false,east=false,west=false,waterlogged=false]";
    static final String LANTERN = "minecraft:lantern[hanging=false,waterlogged=false]";
    static final String HANGING_LANTERN = "minecraft:lantern[hanging=true,waterlogged=false]";
    static final String TORCH = "minecraft:torch";

    /**
     * A template with its description.
     */
    record Building(String name, String category, String type, String icon, int anchorX, int anchorY, int anchorZ, Structure structure) {
    }

    private BuildingGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "src/main/resources/data/kingdomworld/kingdomworld_buildings");
        Files.createDirectories(out);
        for (Building building : buildings()) {
            Files.write(out.resolve(building.name() + ".nbt"), building.structure().bytes());
            Files.writeString(out.resolve(building.name() + ".json"), json(building), StandardCharsets.UTF_8);
        }
        if (args.length > 1) {
            writeTestPack(Path.of(args[1]));
        }
        System.out.println("Wrote " + buildings().size() + " building templates to " + out.toAbsolutePath());
    }

    static List<Building> buildings() {
        List<Building> list = new ArrayList<>();
        list.add(new Building("small_house", "housing", "house", "minecraft:oak_door", 4, 0, 8, smallHouse()));
        list.add(new Building("large_house", "housing", "house", "minecraft:red_bed", 5, 0, 10, largeHouse()));
        list.add(new Building("storehouse", "farming", "warehouse", "kingdomworld:storehouse", 5, 0, 8, storehouse()));
        list.add(new Building("workshop", "production", "workshop", "minecraft:crafting_table", 5, 0, 8, workshop()));
        list.add(new Building("market_square", "trade", "market", "kingdomworld:market_stall", 5, 0, 10, marketSquare()));
        list.add(new Building("watchtower", "defense", "tower", "minecraft:stone_bricks", 3, 0, 7, watchtower()));
        list.add(new Building("wall_gate", "defense", "wall", "minecraft:stone_brick_wall", 5, 0, 3, wallGate()));
        list.add(new Building("well", "decoration", "well", "minecraft:bucket", 2, 0, 4, well()));
        return list;
    }

    /**
     * How many built-in buildings there are.
     */
    public static int buildingCount() {
        return buildings().size();
    }

    static String json(Building building) {
        return "{\n"
                + "  \"name\": \"kingdomworld.building." + building.name() + "\",\n"
                + "  \"category\": \"" + building.category() + "\",\n"
                + "  \"type\": \"" + building.type() + "\",\n"
                + "  \"icon\": \"" + building.icon() + "\",\n"
                + "  \"anchor\": [" + building.anchorX() + ", " + building.anchorY() + ", " + building.anchorZ() + "],\n"
                + "  \"entrance\": \"south\"\n"
                + "}\n";
    }

    private static String stairs(String block, String facing) {
        return block + "[facing=" + facing + ",half=bottom,shape=straight,waterlogged=false]";
    }

    private static String wallTorch(String facing) {
        return "minecraft:wall_torch[facing=" + facing + "]";
    }

    // ---------------------------------------------------------------- the buildings

    /**
     * A small timber house: a stone footing, oak frame and plank walls under a spruce gable roof, one bed.
     */
    static Structure smallHouse() {
        Structure s = new Structure(9, 9, 9);
        s.fill(1, 0, 1, 7, 0, 7, COBBLE).fill(2, 0, 2, 6, 0, 6, SPRUCE_PLANKS);
        s.set(4, 0, 8, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 7, 3, 7, OAK_PLANKS).corners(1, 1, 1, 7, 3, 7, OAK_LOG);
        s.fill(4, 1, 1, 4, 3, 1, OAK_LOG + "[axis=y]");
        s.set(1, 2, 3, PANE_Z).set(1, 2, 5, PANE_Z).set(7, 2, 3, PANE_Z).set(7, 2, 5, PANE_Z);
        s.set(2, 2, 1, PANE_X).set(6, 2, 1, PANE_X).set(2, 2, 7, PANE_X).set(6, 2, 7, PANE_X);
        s.door(4, 1, 7, OAK_DOOR, "north", "left");
        s.beam(1, 1, 7, 7, 4, OAK_LOG);
        s.gableRoof(0, 8, 0, 8, 4, ROOF_STAIRS, ROOF_SLAB, OAK_PLANKS, 1, 7);
        s.bed(2, 1, 3, BED, "north", 0, -1);
        s.set(6, 1, 2, BARREL).set(6, 1, 3, "minecraft:crafting_table");
        s.set(2, 3, 5, wallTorch("east")).set(6, 3, 5, wallTorch("west"));
        s.set(3, 3, 8, wallTorch("south")).set(5, 3, 8, wallTorch("south"));
        return s;
    }

    /**
     * A house of two floors: stone walls below, timber above, a ladder between, three beds.
     */
    static Structure largeHouse() {
        Structure s = new Structure(11, 15, 11);
        s.fill(1, 0, 1, 9, 0, 9, COBBLE).fill(2, 0, 2, 8, 0, 8, SPRUCE_PLANKS);
        s.set(5, 0, 10, stairs(COBBLE_STAIRS, "north"));
        // The ground floor of stone, the upper floor of planks, oak at the corners all the way up.
        s.walls(1, 1, 1, 9, 4, 9, STONE_BRICKS);
        s.beam(1, 1, 9, 9, 5, OAK_LOG);
        s.fill(2, 5, 2, 8, 5, 8, SPRUCE_PLANKS);
        s.walls(1, 6, 1, 9, 8, 9, OAK_PLANKS);
        s.corners(1, 1, 1, 9, 8, 9, OAK_LOG);
        s.fill(5, 6, 1, 5, 8, 1, OAK_LOG + "[axis=y]").fill(5, 6, 9, 5, 8, 9, OAK_LOG + "[axis=y]");
        for (int z : new int[]{3, 7}) {
            s.set(1, 2, z, PANE_Z).set(1, 3, z, PANE_Z).set(9, 2, z, PANE_Z).set(9, 3, z, PANE_Z);
            s.set(1, 7, z, PANE_Z).set(9, 7, z, PANE_Z);
        }
        for (int x : new int[]{3, 7}) {
            s.set(x, 2, 1, PANE_X).set(x, 3, 1, PANE_X).set(x, 2, 9, PANE_X).set(x, 3, 9, PANE_X);
            s.set(x, 7, 1, PANE_X).set(x, 7, 9, PANE_X);
        }
        s.door(5, 1, 9, DOOR, "north", "left");
        // A ladder along the back wall through a hole in the floor.
        s.set(8, 5, 2, Structure.AIR);
        for (int y = 1; y <= 6; y++) {
            s.set(8, y, 2, "minecraft:ladder[facing=south,waterlogged=false]");
        }
        s.beam(1, 1, 9, 9, 9, OAK_LOG);
        s.gableRoof(0, 10, 0, 10, 9, DARK_STAIRS, DARK_SLAB, OAK_PLANKS, 1, 9);
        s.bed(2, 1, 3, BED, "north", 0, -1);
        s.set(8, 1, 8, BARREL).set(2, 1, 8, "minecraft:crafting_table");
        s.bed(3, 6, 3, BED, "north", 0, -1).bed(5, 6, 3, BED, "north", 0, -1);
        s.set(2, 3, 5, wallTorch("east")).set(8, 3, 5, wallTorch("west"));
        s.set(2, 8, 5, wallTorch("east")).set(8, 8, 5, wallTorch("west"));
        s.set(4, 3, 10, wallTorch("south")).set(6, 3, 10, wallTorch("south"));
        return s;
    }

    /**
     * A storehouse of the kingdom: a long hall with the storehouse block at the back, barrels and hay.
     */
    static Structure storehouse() {
        Structure s = new Structure(11, 10, 9);
        s.fill(1, 0, 1, 9, 0, 7, COBBLE).fill(2, 0, 2, 8, 0, 6, SPRUCE_PLANKS);
        s.set(5, 0, 8, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 9, 2, 7, COBBLE).walls(1, 3, 1, 9, 4, 7, SPRUCE_PLANKS);
        s.corners(1, 1, 1, 9, 4, 7, SPRUCE_LOG);
        s.fill(5, 1, 1, 5, 4, 1, SPRUCE_LOG + "[axis=y]");
        s.set(1, 3, 4, PANE_Z).set(9, 3, 4, PANE_Z).set(3, 3, 7, PANE_X).set(7, 3, 7, PANE_X);
        s.set(3, 3, 1, PANE_X).set(7, 3, 1, PANE_X);
        s.door(5, 1, 7, DOOR, "north", "left");
        s.beam(1, 1, 9, 7, 5, SPRUCE_LOG);
        s.gableRoof(0, 10, 0, 8, 5, ROOF_STAIRS, ROOF_SLAB, SPRUCE_PLANKS, 1, 9);
        s.set(5, 1, 2, "kingdomworld:storehouse");
        s.set(3, 1, 2, BARREL).set(7, 1, 2, BARREL).set(2, 1, 2, BARREL).set(8, 1, 2, BARREL);
        s.set(2, 1, 6, "minecraft:hay_block[axis=y]").set(8, 1, 6, "minecraft:hay_block[axis=y]").set(2, 2, 6, "minecraft:hay_block[axis=x]");
        s.set(2, 3, 3, wallTorch("east")).set(8, 3, 3, wallTorch("west"));
        s.set(4, 3, 8, wallTorch("south")).set(6, 3, 8, wallTorch("south"));
        return s;
    }

    /**
     * A workshop open to the front: a crafting table, a furnace and a stonecutter at the back wall, a work post with its
     * chest in the middle.
     */
    static Structure workshop() {
        Structure s = new Structure(11, 9, 9);
        s.fill(1, 0, 1, 9, 0, 7, COBBLE).fill(2, 0, 2, 8, 0, 7, STONE_BRICKS);
        s.set(5, 0, 8, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 9, 1, 7, COBBLE).walls(1, 2, 1, 9, 3, 7, OAK_PLANKS);
        s.corners(1, 1, 1, 9, 3, 7, OAK_LOG);
        // The front stands open between two posts.
        s.fill(3, 1, 7, 7, 3, 7, Structure.AIR);
        s.fill(2, 1, 7, 2, 3, 7, OAK_LOG + "[axis=y]").fill(8, 1, 7, 8, 3, 7, OAK_LOG + "[axis=y]");
        s.set(1, 2, 4, PANE_Z).set(9, 2, 4, PANE_Z).set(3, 2, 1, PANE_X).set(7, 2, 1, PANE_X);
        s.beam(1, 1, 9, 7, 4, OAK_LOG);
        s.gableRoof(0, 10, 0, 8, 4, ROOF_STAIRS, ROOF_SLAB, OAK_PLANKS, 1, 9);
        s.set(2, 1, 2, "minecraft:crafting_table");
        s.set(3, 1, 2, "minecraft:furnace[facing=south,lit=false]");
        s.set(4, 1, 2, "minecraft:furnace[facing=south,lit=false]");
        s.set(8, 1, 2, "minecraft:stonecutter[facing=south]");
        s.set(5, 1, 4, "kingdomworld:work_post");
        s.set(6, 1, 4, "minecraft:chest[facing=south,type=single,waterlogged=false]");
        s.set(3, 3, 2, wallTorch("south")).set(7, 3, 2, wallTorch("south"));
        s.set(2, 2, 6, wallTorch("east")).set(8, 2, 6, wallTorch("west"));
        return s;
    }

    /**
     * A market square: a paved plaza, the market stall under a striped awning, the merchant's post and lamps at the
     * corners.
     */
    static Structure marketSquare() {
        Structure s = new Structure(11, 6, 11);
        s.fill(0, 0, 0, 10, 0, 10, COBBLE);
        s.walls(0, 0, 0, 10, 0, 10, STONE_BRICKS);
        s.fill(3, 0, 3, 7, 0, 7, "minecraft:smooth_stone");
        for (int[] corner : new int[][]{{3, 3}, {7, 3}, {3, 7}, {7, 7}}) {
            s.fill(corner[0], 1, corner[1], corner[0], 3, corner[1], FENCE);
        }
        for (int x = 3; x <= 7; x++) {
            s.fill(x, 4, 3, x, 4, 7, x % 2 == 1 ? "minecraft:red_wool" : "minecraft:white_wool");
        }
        s.set(5, 1, 5, "kingdomworld:market_stall");
        s.set(5, 1, 8, "kingdomworld:work_post");
        s.set(3, 1, 5, BARREL).set(7, 1, 5, BARREL).set(7, 1, 4, "minecraft:hay_block[axis=y]");
        s.set(5, 3, 5, HANGING_LANTERN);
        for (int[] corner : new int[][]{{0, 0}, {10, 0}, {0, 10}, {10, 10}}) {
            s.fill(corner[0], 1, corner[1], corner[0], 2, corner[1], FENCE);
            s.set(corner[0], 3, corner[1], LANTERN);
        }
        return s;
    }

    /**
     * A stone watchtower: a ladder inside up to a platform with battlements.
     */
    static Structure watchtower() {
        Structure s = new Structure(7, 13, 8);
        s.fill(0, 0, 0, 6, 0, 6, COBBLE);
        s.set(3, 0, 7, stairs(COBBLE_STAIRS, "north"));
        s.walls(0, 1, 0, 6, 10, 6, STONE_BRICKS);
        for (int y = 1; y <= 10; y++) {
            for (int[] corner : new int[][]{{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
                s.set(corner[0], y, corner[1], COBBLE);
            }
        }
        // Arrow slits on three sides; the back wall carries the ladder.
        for (int y : new int[]{4, 8}) {
            s.set(0, y, 3, Structure.AIR).set(6, y, 3, Structure.AIR);
        }
        s.set(3, 8, 6, Structure.AIR);
        s.door(3, 1, 6, DOOR, "north", "left");
        s.fill(0, 11, 0, 6, 11, 6, STONE_BRICKS);
        for (int y = 1; y <= 11; y++) {
            s.set(3, y, 1, "minecraft:ladder[facing=south,waterlogged=false]");
        }
        for (int i = 0; i <= 6; i += 2) {
            s.set(i, 12, 0, STONE_BRICK_WALL).set(i, 12, 6, STONE_BRICK_WALL).set(0, 12, i, STONE_BRICK_WALL).set(6, 12, i, STONE_BRICK_WALL);
        }
        s.set(1, 12, 5, TORCH).set(5, 12, 5, TORCH).set(5, 12, 1, TORCH);
        s.set(1, 3, 3, wallTorch("east")).set(5, 7, 3, wallTorch("west"));
        s.set(2, 3, 7, wallTorch("south")).set(4, 3, 7, wallTorch("south"));
        return s;
    }

    /**
     * A piece of town wall with a double gate under an arch and battlements on its outer side.
     */
    static Structure wallGate() {
        Structure s = new Structure(12, 7, 4);
        s.fill(0, 0, 1, 11, 0, 2, COBBLE);
        s.fill(5, 0, 0, 6, 0, 3, COBBLE);
        s.fill(0, 1, 1, 11, 5, 2, STONE_BRICKS);
        for (int y = 1; y <= 5; y++) {
            s.set(0, y, 1, COBBLE).set(0, y, 2, COBBLE).set(11, y, 1, COBBLE).set(11, y, 2, COBBLE);
        }
        s.fill(5, 1, 1, 6, 2, 2, Structure.AIR);
        s.set(5, 3, 1, STONE_BRICK_STAIRS + "[facing=west,half=top,shape=straight,waterlogged=false]");
        s.set(6, 3, 1, STONE_BRICK_STAIRS + "[facing=east,half=top,shape=straight,waterlogged=false]");
        s.set(5, 3, 2, STONE_BRICK_STAIRS + "[facing=west,half=top,shape=straight,waterlogged=false]");
        s.set(6, 3, 2, STONE_BRICK_STAIRS + "[facing=east,half=top,shape=straight,waterlogged=false]");
        s.door(5, 1, 2, DOOR, "north", "left").door(6, 1, 2, DOOR, "north", "right");
        for (int x = 0; x <= 11; x += 2) {
            s.set(x, 6, 2, STONE_BRICK_WALL);
        }
        s.set(4, 3, 3, wallTorch("south")).set(7, 3, 3, wallTorch("south"));
        return s;
    }

    /**
     * A well of stone with water inside and a small roof on posts; the water is not built (templates leave liquids out).
     */
    static Structure well() {
        Structure s = new Structure(5, 5, 5);
        s.walls(0, 0, 0, 4, 1, 4, COBBLE);
        s.fill(1, 0, 1, 3, 0, 3, "minecraft:water[level=0]");
        for (int[] corner : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) {
            s.fill(corner[0], 2, corner[1], corner[0], 3, corner[1], FENCE);
        }
        s.fill(0, 4, 0, 4, 4, 4, ROOF_SLAB + "[type=bottom,waterlogged=false]");
        s.set(2, 3, 2, HANGING_LANTERN);
        return s;
    }

    // ---------------------------------------------------------------- the data pack of the smoke check

    /**
     * A data pack with one template that needs Create: in a pack with Create it can be built, without it the server shows
     * why not. The smoke check puts it into the world.
     */
    static void writeTestPack(Path root) throws IOException {
        Path folder = root.resolve("data").resolve("smoketest").resolve("kingdomworld_buildings");
        Files.createDirectories(folder);
        Files.writeString(root.resolve("pack.mcmeta"),
                "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"KingdomWorld smoke check: a shed that needs Create\"\n  }\n}\n",
                StandardCharsets.UTF_8);
        Structure shed = new Structure(3, 3, 3);
        shed.fill(0, 0, 0, 2, 0, 2, OAK_PLANKS);
        shed.fill(0, 1, 0, 2, 1, 2, "create:andesite_casing");
        shed.set(1, 1, 1, Structure.AIR);
        shed.fill(0, 2, 0, 2, 2, 2, OAK_PLANKS);
        Files.write(folder.resolve("create_shed.nbt"), shed.bytes());
        Files.writeString(folder.resolve("create_shed.json"), "{\n"
                + "  \"name\": \"kingdomworld.building.create_shed\",\n"
                + "  \"fallback_name\": \"Create shed\",\n"
                + "  \"category\": \"production\",\n"
                + "  \"type\": \"shed\",\n"
                + "  \"icon\": \"minecraft:oak_planks\",\n"
                + "  \"anchor\": [1, 0, 2],\n"
                + "  \"entrance\": \"south\"\n"
                + "}\n", StandardCharsets.UTF_8);
    }
}
