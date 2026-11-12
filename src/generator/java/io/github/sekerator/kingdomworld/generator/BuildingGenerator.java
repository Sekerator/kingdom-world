package io.github.sekerator.kingdomworld.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     *
     * @param roleParams the numbers of its role that differ from those of the role, as the body of a JSON object, or empty
     * @param points     named places inside it, each a list of positions (x, y, z in its own box, y the layer the feet are in):
     *                   where a guard stands, where a block is put later
     */
    record Building(String name, String category, String type, String icon, int anchorX, int anchorY, int anchorZ, Structure structure,
                    String role, int level, String upgradesTo, String roleParams, Map<String, List<int[]>> points) {
        Building(String name, String category, String type, String icon, int anchorX, int anchorY, int anchorZ, Structure structure,
                 String role, int level, String upgradesTo) {
            this(name, category, type, icon, anchorX, anchorY, anchorZ, structure, role, level, upgradesTo,
                    ROLE_PARAMS.getOrDefault(name, ""), Map.of());
        }

        Building(String name, String category, String type, String icon, int anchorX, int anchorY, int anchorZ, Structure structure) {
            this(name, category, type, icon, anchorX, anchorY, anchorZ, structure, type, 1, "");
        }

        /**
         * This building with other numbers of its role, for example {@code "bunks": 6}.
         */
        Building params(String params) {
            return new Building(name, category, type, icon, anchorX, anchorY, anchorZ, structure, role, level, upgradesTo, params, points);
        }

        /**
         * This building with one more position of a named place.
         */
        Building at(String point, int x, int y, int z) {
            Map<String, List<int[]>> more = new LinkedHashMap<>();
            points.forEach((key, list) -> more.put(key, new ArrayList<>(list)));
            more.computeIfAbsent(point, key -> new ArrayList<>()).add(new int[]{x, y, z});
            return new Building(name, category, type, icon, anchorX, anchorY, anchorZ, structure, role, level, upgradesTo, roleParams, more);
        }
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
        list.add(new Building("small_house", "housing", "house", "minecraft:oak_door", 4, 0, 8, smallHouse(), "house", 1,
                "kingdomworld:small_house_2"));
        list.add(new Building("small_house_2", "housing", "house", "minecraft:oak_door", 4, 0, 8, smallHouse2(), "house", 2, ""));
        list.add(new Building("large_house", "housing", "house", "minecraft:red_bed", 5, 0, 10, largeHouse()));
        list.add(new Building("storehouse", "farming", "warehouse", "kingdomworld:storehouse", 5, 0, 8, storehouse(), "warehouse", 1,
                "kingdomworld:storehouse_2"));
        list.add(new Building("storehouse_2", "farming", "warehouse", "kingdomworld:storehouse", 5, 0, 8, storehouse2(), "warehouse", 2, ""));
        list.add(new Building("workshop", "production", "workshop", "minecraft:crafting_table", 5, 0, 8, workshop()));
        list.add(new Building("market_square", "trade", "market", "kingdomworld:market_stall", 5, 0, 10, marketSquare()));
        list.add(new Building("watchtower", "defense", "tower", "minecraft:stone_bricks", 3, 0, 7, watchtower()));
        list.add(new Building("wall_gate", "defense", "wall", "minecraft:stone_brick_wall", 5, 0, 3, wallGate()));
        list.add(new Building("well", "decoration", "well", "minecraft:bucket", 2, 0, 4, well()));
        list.add(new Building("barracks", "defense", "barracks", "kingdomworld:barracks", 5, 0, 8, barracks()));
        list.add(new Building("mage_tower", "magic", "mage_tower", "kingdomworld:magic_focus", 4, 0, 8, mageTower()));
        list.add(new Building("tavern", "trade", "tavern", "minecraft:barrel", 5, 0, 10, tavern(), "tavern", 1, "kingdomworld:tavern_2"));
        list.add(new Building("tavern_2", "trade", "tavern", "minecraft:barrel", 5, 0, 10, tavern2(), "tavern", 2, ""));
        // The variants by which towns differ: more houses, the workshops of the trades, stores, the buildings of the garrison
        // and the palaces of the kingdoms of the world.
        list.addAll(Houses.buildings());
        list.addAll(Workshops.buildings());
        list.addAll(Stores.buildings());
        list.addAll(Military.buildings());
        list.addAll(Palaces.buildings());
        // The architectural styles of the kingdoms of the world. A building of a style is drawn once; its look (roof, plaster,
        // cloth, shutters, chimney...) is given when it is laid. Its furniture is tidied so that only beds of homes and the
        // stores of its households and workers count.
        list.addAll(Furnish.tidy(classic()));
        list.addAll(Furnish.tidy(terem()));
        list.addAll(Furnish.tidy(nordic()));
        list.addAll(Furnish.tidy(southern()));
        list.addAll(Furnish.tidy(desert()));
        list.addAll(Furnish.tidy(eastern()));
        list.addAll(Furnish.tidy(gothic()));
        list.addAll(Furnish.tidy(mountain()));
        return list;
    }

    /**
     * The style of stone with half-timbering: three castles and the outer ward of the great one, the houses of the guilds by
     * rank, the buildings of the institutions, yards and monuments, the houses of four quarters, a smithy, an inn and a town
     * gate.
     */
    static List<Building> classic() {
        List<Building> list = new ArrayList<>();
        list.addAll(SmallCastle.buildings());
        list.addAll(MediumCastle.buildings());
        list.addAll(GreatCastle.buildings());
        list.addAll(CastleWard.buildings());
        list.addAll(ClassicGuilds.buildings());
        list.addAll(Institutions.buildings());
        list.addAll(Yards.buildings());
        list.addAll(Decorations.buildings());
        list.addAll(PoorHouses.buildings());
        list.addAll(CraftHouses.buildings());
        list.addAll(RichHouses.buildings());
        list.addAll(ClassicTown.buildings());
        return list;
    }

    /**
     * The wooden style of the terem: a kremlin in three sizes, the houses of the guilds by rank, six houses, a smithy, a
     * tavern and a town gate, drawn with the shapes of {@link Terem}.
     */
    static List<Building> terem() {
        List<Building> list = new ArrayList<>();
        list.addAll(TeremCastle.buildings());
        list.addAll(TeremGuilds.buildings());
        list.addAll(TeremTown.buildings());
        return list;
    }

    /**
     * The timber style of the north: the hold of a jarl in three sizes, the houses of the guilds by rank, six houses from the
     * turf hut to the homestead of a chieftain, a smithy, a mead tavern and a town gate, dark staves under steep shingle roofs
     * with dragon heads, drawn with the shapes of {@link Nordic}.
     */
    static List<Building> nordic() {
        List<Building> list = new ArrayList<>();
        list.addAll(NordicCastle.buildings());
        list.addAll(NordicGuilds.buildings());
        list.addAll(NordicTown.buildings());
        return list;
    }

    /**
     * The style of the Mediterranean south: a palazzo fortress in three sizes, the houses of the guilds by rank, six houses
     * from the fisherman's cottage to the villa, a smithy, a tavern and a town gate, whitewashed cubes under low roofs of red
     * tiles with arcades, loggias and bell towers, drawn with the shapes of {@link Southern}.
     */
    static List<Building> southern() {
        List<Building> list = new ArrayList<>();
        list.addAll(SouthernCastle.buildings());
        list.addAll(SouthernGuilds.buildings());
        list.addAll(SouthernTown.buildings());
        return list;
    }

    /**
     * The style of sandstone and mud brick of the desert: a kasbah in three sizes, the houses of the guilds by rank, six
     * houses from the mud hut to the house of a noble, a smithy, a caravan tavern and a town gate, flat roofs with merlons,
     * domes, minarets and wind towers, drawn with the shapes of {@link Desert}.
     */
    static List<Building> desert() {
        List<Building> list = new ArrayList<>();
        list.addAll(DesertCastle.buildings());
        list.addAll(DesertGuilds.buildings());
        list.addAll(DesertTown.buildings());
        return list;
    }

    /**
     * The style of the far east: a castle with a tall keep in three sizes, the houses of the guilds by rank, six houses from
     * the shack to the residence of a noble, a smithy, an inn and a town gate, dark timber and white plaster on stone
     * platforms under great curving roofs, drawn with the shapes of {@link Eastern}.
     */
    static List<Building> eastern() {
        List<Building> list = new ArrayList<>();
        list.addAll(EasternCastle.buildings());
        list.addAll(EasternGuilds.buildings());
        list.addAll(EasternTown.buildings());
        return list;
    }

    /**
     * The dark gothic of slate and needles: a cathedral castle in three sizes, the houses of the guilds by rank, six houses
     * from the rubble hovel to the manor of a noble, a smithy, a tavern and a town gate, tall and narrow under very steep
     * roofs with pointed windows, buttresses and spires, drawn with the shapes of {@link Gothic}.
     */
    static List<Building> gothic() {
        List<Building> list = new ArrayList<>();
        list.addAll(GothicCastle.buildings());
        list.addAll(GothicGuilds.buildings());
        list.addAll(GothicTown.buildings());
        return list;
    }

    /**
     * The stone style of the mountains: a hold cut out of the rock in three sizes, the houses of the guilds by rank, six
     * houses from the miner's dugout to the hall of a noble family, a smithy, a tavern and a town gate, massive and low on
     * battered feet with carved faces of forefathers and glowing braziers, drawn with the shapes of {@link Mountain}.
     */
    static List<Building> mountain() {
        List<Building> list = new ArrayList<>();
        list.addAll(MountainCastle.buildings());
        list.addAll(MountainGuilds.buildings());
        list.addAll(MountainTown.buildings());
        return list;
    }

    /**
     * Numbers of the role of a building that differ from those of its role: a barracks hall has twelve bunks, the large stores
     * count more containers.
     */
    private static final Map<String, String> ROLE_PARAMS = Map.of(
            "barracks_hall", "\"bunks\": 12",
            "barn", "\"containers\": 12",
            "storage_yard", "\"containers\": 20");

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
                + "  \"role\": \"" + building.role() + "\",\n"
                + (building.roleParams().isEmpty() ? "" : "  \"role_params\": {" + building.roleParams() + "},\n")
                + "  \"level\": " + building.level() + ",\n"
                + (building.upgradesTo().isEmpty() ? "" : "  \"upgrades_to\": \"" + building.upgradesTo() + "\",\n")
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
     * The small house a level higher: a stone plinth under the timber walls, a second bed, a bookshelf, a lantern, flower
     * pots and shutters at the back windows. It stands on the same ground with the same door, so that an
     * upgrade changes only what differs.
     */
    static Structure smallHouse2() {
        Structure s = smallHouse();
        // A plinth of stone bricks around the house, the oak frame and the door stay.
        for (int x = 2; x <= 6; x++) {
            if (x != 4) {
                s.set(x, 1, 1, STONE_BRICKS).set(x, 1, 7, STONE_BRICKS);
            }
        }
        for (int z = 2; z <= 6; z++) {
            s.set(1, 1, z, STONE_BRICKS).set(7, 1, z, STONE_BRICKS);
        }
        s.bed(3, 1, 3, BED, "north", 0, -1);
        s.set(5, 1, 6, "minecraft:bookshelf");
        s.set(6, 2, 3, LANTERN);
        s.set(2, 1, 6, "minecraft:flower_pot").set(6, 1, 6, "minecraft:flower_pot");
        for (int[] window : new int[][]{{2, 0}, {6, 0}}) {
            s.set(window[0], 2, window[1], "minecraft:spruce_trapdoor[facing=north,half=top,open=true,powered=false,waterlogged=false]");
        }
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
     * The storehouse a level higher: walls of stone bricks below, chests along the side walls and more barrels, so that the
     * warehouse holds more containers.
     */
    static Structure storehouse2() {
        Structure s = storehouse();
        for (int x = 2; x <= 8; x++) {
            if (x != 5) {
                s.set(x, 1, 1, STONE_BRICKS).set(x, 2, 1, STONE_BRICKS);
            }
        }
        for (int z = 2; z <= 6; z++) {
            s.set(1, 1, z, STONE_BRICKS).set(1, 2, z, STONE_BRICKS).set(9, 1, z, STONE_BRICKS).set(9, 2, z, STONE_BRICKS);
        }
        s.set(2, 1, 4, "minecraft:chest[facing=east,type=single,waterlogged=false]");
        s.set(8, 1, 4, "minecraft:chest[facing=west,type=single,waterlogged=false]");
        s.set(4, 1, 2, BARREL).set(6, 1, 2, BARREL);
        s.set(2, 1, 5, BARREL).set(8, 1, 5, BARREL);
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
     * The barracks: a long hall of stone below and timber above, four bunks along the back wall, the barracks block in the
     * middle and barrels of gear by the door.
     */
    static Structure barracks() {
        Structure s = new Structure(11, 9, 9);
        s.fill(1, 0, 1, 9, 0, 7, COBBLE).fill(2, 0, 2, 8, 0, 6, SPRUCE_PLANKS);
        s.set(5, 0, 8, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 9, 1, 7, COBBLE).walls(1, 2, 1, 9, 3, 7, SPRUCE_PLANKS);
        s.corners(1, 1, 1, 9, 3, 7, SPRUCE_LOG);
        s.set(1, 2, 4, PANE_Z).set(9, 2, 4, PANE_Z).set(3, 2, 7, PANE_X).set(7, 2, 7, PANE_X);
        s.door(5, 1, 7, DOOR, "north", "left");
        s.beam(1, 1, 9, 7, 4, SPRUCE_LOG);
        s.gableRoof(0, 10, 0, 8, 4, DARK_STAIRS, DARK_SLAB, SPRUCE_PLANKS, 1, 9);
        for (int x : new int[]{2, 4, 6, 8}) {
            s.bed(x, 1, 3, BED, "north", 0, -1);
        }
        s.set(5, 1, 5, "kingdomworld:barracks");
        s.set(2, 1, 6, BARREL).set(8, 1, 6, BARREL).set(8, 1, 5, BARREL);
        s.set(3, 3, 2, wallTorch("south")).set(7, 3, 2, wallTorch("south"));
        s.set(4, 3, 8, wallTorch("south")).set(6, 3, 8, wallTorch("south"));
        return s;
    }

    /**
     * The tower of the mages: a round-cornered stone tower with windows of purple glass, the magic focus and the mage's work
     * post on the ground floor, a bed above and a platform with battlements on top.
     */
    static Structure mageTower() {
        Structure s = new Structure(9, 14, 9);
        s.fill(0, 0, 0, 8, 0, 7, COBBLE).fill(2, 0, 2, 6, 0, 6, "minecraft:polished_andesite");
        s.set(4, 0, 8, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 7, 10, 7, STONE_BRICKS);
        for (int y = 1; y <= 10; y++) {
            for (int[] corner : new int[][]{{1, 1}, {7, 1}, {1, 7}, {7, 7}}) {
                s.set(corner[0], y, corner[1], "minecraft:deepslate_bricks");
            }
        }
        String glassX = "minecraft:purple_stained_glass_pane[east=true,west=true,north=false,south=false,waterlogged=false]";
        String glassZ = "minecraft:purple_stained_glass_pane[north=true,south=true,east=false,west=false,waterlogged=false]";
        for (int y : new int[]{2, 7}) {
            s.set(1, y, 4, glassZ).set(7, y, 4, glassZ).set(4, y, 1, glassX);
        }
        s.set(4, 7, 7, glassX);
        s.door(4, 1, 7, DOOR, "north", "left");
        s.fill(2, 5, 2, 6, 5, 6, SPRUCE_PLANKS);
        for (int y = 1; y <= 10; y++) {
            s.set(6, y, 2, "minecraft:ladder[facing=west,waterlogged=false]");
        }
        s.fill(1, 11, 1, 7, 11, 7, STONE_BRICKS);
        s.set(6, 11, 2, "minecraft:ladder[facing=west,waterlogged=false]");
        for (int i = 1; i <= 7; i += 2) {
            s.set(i, 12, 1, STONE_BRICK_WALL).set(i, 12, 7, STONE_BRICK_WALL).set(1, 12, i, STONE_BRICK_WALL).set(7, 12, i, STONE_BRICK_WALL);
        }
        s.set(4, 12, 4, "minecraft:amethyst_block").set(4, 13, 4, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        s.set(4, 1, 4, "kingdomworld:magic_focus");
        s.set(3, 1, 3, "kingdomworld:work_post");
        s.set(2, 1, 6, "minecraft:bookshelf").set(2, 1, 5, "minecraft:bookshelf").set(6, 1, 6, "minecraft:enchanting_table");
        s.bed(3, 6, 4, BED, "north", 0, -1);
        s.set(5, 6, 5, "minecraft:bookshelf").set(5, 6, 3, LANTERN);
        s.set(4, 3, 2, wallTorch("south")).set(2, 3, 4, wallTorch("east"));
        s.set(3, 3, 8, wallTorch("south")).set(5, 3, 8, wallTorch("south"));
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

    /**
     * A tavern of two floors on the ground of a large house: below, a counter with the innkeeper's post behind it and barrels
     * at the back wall, tables with benches and lanterns; above, rooms with beds for guests, reached by a ladder.
     */
    static Structure tavern() {
        Structure s = new Structure(11, 15, 11);
        s.fill(1, 0, 1, 9, 0, 9, COBBLE).fill(2, 0, 2, 8, 0, 8, SPRUCE_PLANKS);
        s.set(5, 0, 10, stairs(COBBLE_STAIRS, "north"));
        s.walls(1, 1, 1, 9, 1, 9, COBBLE).walls(1, 2, 1, 9, 4, 9, OAK_PLANKS);
        s.beam(1, 1, 9, 9, 5, OAK_LOG);
        s.fill(2, 5, 2, 8, 5, 8, SPRUCE_PLANKS);
        s.walls(1, 6, 1, 9, 8, 9, OAK_PLANKS);
        s.corners(1, 1, 1, 9, 8, 9, OAK_LOG);
        s.fill(5, 2, 1, 5, 8, 1, OAK_LOG + "[axis=y]");
        for (int z : new int[]{3, 7}) {
            s.set(1, 3, z, PANE_Z).set(9, 3, z, PANE_Z).set(1, 7, z, PANE_Z).set(9, 7, z, PANE_Z);
        }
        for (int x : new int[]{3, 7}) {
            s.set(x, 3, 1, PANE_X).set(x, 3, 9, PANE_X).set(x, 7, 1, PANE_X).set(x, 7, 9, PANE_X);
        }
        s.door(5, 1, 9, OAK_DOOR, "north", "left");
        // The counter with the innkeeper's post behind it; the gap at its end lets the innkeeper out.
        for (int x = 2; x <= 4; x++) {
            s.set(x, 1, 4, "minecraft:stripped_spruce_log[axis=x]");
        }
        s.set(3, 1, 2, "kingdomworld:work_post");
        s.set(2, 1, 2, BARREL).set(4, 1, 2, BARREL).set(2, 2, 2, BARREL);
        s.set(5, 1, 2, "minecraft:smoker[facing=south,lit=false]");
        // Tables with benches.
        for (int[] table : new int[][]{{7, 3}, {7, 6}, {3, 7}}) {
            s.set(table[0], 1, table[1], FENCE).set(table[0], 2, table[1], "minecraft:oak_pressure_plate[powered=false]");
            s.set(table[0] - 1, 1, table[1], stairs(DARK_STAIRS, "east")).set(table[0] + 1, 1, table[1], stairs(DARK_STAIRS, "west"));
        }
        s.set(4, 4, 6, HANGING_LANTERN).set(7, 4, 4, HANGING_LANTERN).set(7, 4, 7, HANGING_LANTERN);
        s.set(4, 3, 10, wallTorch("south")).set(6, 3, 10, wallTorch("south"));
        // A ladder along the east wall up through the floor.
        s.set(8, 5, 2, Structure.AIR);
        for (int y = 1; y <= 6; y++) {
            s.set(8, y, 2, "minecraft:ladder[facing=west,waterlogged=false]");
        }
        // Rooms for guests above.
        s.bed(2, 6, 3, BED, "north", 0, -1).bed(4, 6, 3, BED, "north", 0, -1).bed(6, 6, 3, BED, "north", 0, -1);
        s.set(6, 6, 8, BARREL).set(8, 6, 8, BARREL);
        s.set(2, 8, 5, wallTorch("east")).set(8, 8, 5, wallTorch("west"));
        s.beam(1, 1, 9, 9, 9, OAK_LOG);
        s.gableRoof(0, 10, 0, 10, 9, ROOF_STAIRS, ROOF_SLAB, OAK_PLANKS, 1, 9);
        return s;
    }

    /**
     * The tavern a level higher: a plinth of stone bricks, a fourth room and more light, on the same ground with the same
     * door and counter, so that an upgrade changes only what differs.
     */
    static Structure tavern2() {
        Structure s = tavern();
        for (int x = 2; x <= 8; x++) {
            if (x != 5) {
                s.set(x, 1, 1, STONE_BRICKS).set(x, 1, 9, STONE_BRICKS);
            }
        }
        for (int z = 2; z <= 8; z++) {
            s.set(1, 1, z, STONE_BRICKS).set(9, 1, z, STONE_BRICKS);
        }
        s.bed(2, 6, 7, BED, "north", 0, -1);
        s.set(4, 4, 3, HANGING_LANTERN).set(5, 8, 6, HANGING_LANTERN);
        s.set(2, 2, 6, "minecraft:flower_pot").set(8, 2, 8, "minecraft:flower_pot");
        s.set(4, 2, 2, "minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]");
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
