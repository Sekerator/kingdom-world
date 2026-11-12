package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The town of the style "southern" (see {@link Southern}): its houses, from the fisherman's cottage under a little
 * gable of tiles to the villa with its loggias and its walled garden, its smithy, its tavern and a piece of its town
 * wall with the gate. All of them are whitewashed cubes on footings of dressed stone, under low roofs of clay tiles
 * with deep eaves or flat roof terraces behind parapets, with shutters standing open beside the windows, flower pots on
 * the sills, railings of wrought iron, arcades on columns, pergolas under vines, cypresses and citrus trees in pots;
 * inside floors of cotto, ceilings of dark beams, a hearth under a hood of plaster and light in every room.
 */
final class SouthernTown {
    private static final String RENDER = Southern.RENDER;
    private static final String STONE = Southern.STONE;
    private static final String STONE_STAIRS = Southern.STONE_STAIRS;
    private static final String STONE_SLAB = Southern.STONE_SLAB;
    private static final String PAVING = Southern.PAVING;
    private static final String TRIM = Southern.TRIM;
    private static final String TRIM_STAIRS = Southern.TRIM_STAIRS;
    private static final String TRIM_SLAB = Southern.TRIM_SLAB;
    private static final String CARVED = Southern.CARVED;
    private static final Kit.Roof TILES = Southern.TILES;
    private static final String BEAM = Southern.BEAM;
    private static final String PLANKS = Southern.PLANKS;
    private static final String PLANK_SLAB = Southern.PLANK_SLAB;
    private static final String PLANK_STAIRS = Southern.PLANK_STAIRS;
    private static final String FENCE = Southern.FENCE;
    private static final String DOOR = Southern.DOOR;
    private static final String WOOD = Southern.WOOD;
    private static final String COTTO = Southern.COTTO;
    private static final String IRON = Southern.IRON;
    private static final String GLASS = Southern.GLASS;
    private static final String POT = Southern.POT;
    private static final String AIR = Structure.AIR;

    private SouthernTown() {
    }

    // ---------------------------------------------------------------- the plans of the houses

    /**
     * The houses the house program draws, from the poorest to the noblest: their names, their ground, their storeys and
     * their beds.
     */
    enum Plan {
        FISHER("fisher", 5, 7, 1, 1, "minecraft:cod"),
        WHITE("white", 7, 9, 1, 2, "minecraft:white_concrete"),
        COURT("court", 11, 11, 1, 2, "minecraft:flowering_azalea_leaves"),
        NARROW("narrow", 7, 11, 3, 3, "minecraft:iron_bars"),
        MERCHANT("merchant", 11, 13, 2, 3, "minecraft:decorated_pot"),
        VILLA("villa", 13, 15, 2, 4, "minecraft:orange_terracotta");

        final String key;
        final int width;
        final int depth;
        final int storeys;
        final int beds;
        final String icon;

        Plan(String key, int width, int depth, int storeys, int beds, String icon) {
            this.key = key;
            this.width = width;
            this.depth = depth;
            this.storeys = storeys;
            this.beds = beds;
            this.icon = icon;
        }
    }

    /**
     * The town buildings of the style: the six houses of the house program, the smithy, the tavern and the piece of the
     * town wall with its gate.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Plan plan : Plan.values()) {
            Structure s = house(plan);
            list.add(new BuildingGenerator.Building("southern_house_" + plan.key, "housing", "house", plan.icon, (s.sizeX - 1) / 2, 0,
                    s.sizeZ - 1, s, "house", 1, ""));
        }
        Structure smithy = smithy();
        list.add(new BuildingGenerator.Building("southern_smithy", "production", "workshop", "minecraft:anvil", (smithy.sizeX - 1) / 2, 0,
                smithy.sizeZ - 1, smithy, "workshop", 1, "").at("door", 6, 1, 10));
        Structure tavern = tavern();
        list.add(new BuildingGenerator.Building("southern_tavern", "trade", "tavern", "minecraft:barrel", (tavern.sizeX - 1) / 2, 0,
                tavern.sizeZ - 1, tavern, "tavern", 1, "").at("door", 6, 1, 10));
        Structure gate = wallGate();
        list.add(new BuildingGenerator.Building("southern_wall_gate", "defense", "gatehouse", "minecraft:smooth_sandstone",
                (gate.sizeX - 1) / 2, 0, gate.sizeZ - 1, gate, "tower", 1, "")
                .at("door", 11, 1, 10)
                .at("castle_gate", GATE_X1, 1, 5).at("castle_gate", GATE_X2, 1, 5)
                .at("castle_wall", 1, WALK, 5).at("castle_wall", 21, WALK, 5));
        return list;
    }

    /**
     * The house program: draws the house of a plan.
     */
    static Structure house(Plan plan) {
        return switch (plan) {
            case FISHER -> fisher();
            case WHITE -> white();
            case COURT -> court();
            case NARROW -> narrow();
            case MERCHANT -> merchant();
            case VILLA -> villa();
        };
    }

    // ---------------------------------------------------------------- shared shapes

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static void add(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z) && !s.solid(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static void fill(Structure s, int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    put(s, x, y, z, state);
                }
            }
        }
    }

    /**
     * The footing of a house on layer 0 under the walls x1..x2, z1..z2: dressed stone on the outline, cotto inside.
     */
    private static void footing(Structure s, int x1, int z1, int x2, int z2) {
        fill(s, x1, 0, z1, x2, 0, z2, STONE);
        fill(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, COTTO);
    }

    /**
     * A chimney of render from layer {@code y1} to {@code y2} at (x, z), capped with a slab of tiles, the way the
     * chimneys of the south are hooded.
     */
    private static void chimney(Structure s, int x, int z, int y1, int y2) {
        fill(s, x, y1, z, x, y2, z, RENDER);
        put(s, x, y2 + 1, z, Kit.slab(TILES.slab()));
    }

    /**
     * A bed (a home) with its foot at (x, y, z) and its head towards {@code facing}, and a night stand with a candle
     * beside the head on the given side ({@code 1} to the left of facing, {@code -1} to the right, 0 none) where the
     * cell is free.
     */
    private static void bed(Structure s, int x, int y, int z, String facing, int stand) {
        Furnish.bed(s, x, y, z, facing, "red");
        if (stand != 0) {
            int[] f = Furnish.step(facing);
            int[] a = Furnish.step(Furnish.left(facing));
            int nx = x + f[0] + a[0] * stand;
            int nz = z + f[1] + a[1] * stand;
            if (s.inside(nx, y, nz) && !s.solid(nx, y, nz)) {
                put(s, nx, y, nz, Kit.topSlab("minecraft:" + WOOD + "_slab"));
                put(s, nx, y + 1, nz, Furnish.candles(1));
            }
        }
    }

    /** A number of a column that looks random but is the same every time, for loose patterns. */
    private static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /**
     * Vines lying on the rafters of a pergola over x1..x2, z1..z2 at layer {@code y}: a loose patchwork of green leaves
     * with bougainvillea in flower, about two cells in three covered, and a few hanging down at {@code y - 1} on the
     * edges.
     */
    private static void vines(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                int h = hash(x * 5 + 3, z * 7 + y);
                if (h % 3 != 0) {
                    add(s, x, y, z, h % 7 == 0 ? Southern.BLOSSOM : Southern.FOLIAGE);
                }
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                if (edge && h % 5 == 1) {
                    add(s, x, y - 1, z, h % 2 == 0 ? Southern.BLOSSOM : Southern.FOLIAGE);
                }
            }
        }
    }

    /**
     * A cypress like {@link Southern#cypress} (a trunk on the ground at layer {@code y - 1} and a narrow dark flame
     * {@code height} layers high), whose leaves only fill cells where nothing stands, so that it may grow against a
     * wall.
     */
    private static void cypress(Structure s, int x, int y, int z, int height) {
        put(s, x, y, z, Southern.TRUNK);
        for (int i = 1; i < height; i++) {
            add(s, x, y + i, z, Southern.CYPRESS);
        }
        for (int yy = y + (height >= 8 ? 2 : 1); yy <= y + 1 + height / 2; yy++) {
            add(s, x + 1, yy, z, Southern.CYPRESS);
            add(s, x - 1, yy, z, Southern.CYPRESS);
            add(s, x, yy, z + 1, Southern.CYPRESS);
            add(s, x, yy, z - 1, Southern.CYPRESS);
        }
    }

    /**
     * Bougainvillea climbing a wall: flowering leaves and green leaves in a loose patch over the cells given as {x, y,
     * z}, only where nothing stands.
     */
    private static void climber(Structure s, int[]... cells) {
        int i = 0;
        for (int[] c : cells) {
            add(s, c[0], c[1], c[2], i++ % 3 == 1 ? Southern.FOLIAGE : Southern.BLOSSOM);
        }
    }

    // ---------------------------------------------------------------- the fisherman's cottage

    /**
     * A fisherman's cottage: one whitewashed room on a footing of dressed stone under a low gable of tiles that looks
     * at the street and reaches out over a porch on two stone piers, its pediment closed with render over a beam of
     * trim. In the gable wall the door between two little windows with sills and pots; on the porch the oars, a crate
     * of fish, bundles of dried weed and a lantern. Inside: the bed, the hearth with its hood in the corner and its
     * chimney through the roof, the chest, a table with a stool, a sink and a lantern under the beams.
     */
    static Structure fisher() {
        Structure s = new Structure(5, 10, 7);
        footing(s, 0, 0, 4, 4);
        Southern.walls(s, 0, 0, 4, 4, 1, 4);
        Southern.band(s, 0, 0, 4, 4, 4);
        Southern.beamCeiling(s, 1, 1, 3, 3, 4, true);
        // The porch: a floor of paving, a round arch on two piers of dressed stone under the pediment.
        fill(s, 0, 0, 5, 4, 0, 6, PAVING);
        fill(s, 0, 0, 6, 4, 0, 6, TRIM);
        Southern.arcade(s, 0, 6, 4, 6, 1, 3, 3, TRIM);
        // The door between two little windows.
        s.door(2, 1, 4, DOOR, "north", "left");
        put(s, 2, 3, 4, TRIM);
        Southern.window(s, 1, 2, 4, "south", 1, 1, false, true);
        Southern.window(s, 3, 2, 4, "south", 1, 1, false, true);
        Southern.window(s, 0, 2, 2, "west", 1, 2, false, false);
        Southern.window(s, 4, 2, 2, "east", 1, 2, false, false);
        // The roof: a low gable along z from behind the back wall over the porch, the pediment over the beam.
        Southern.lowRoof(s, 5, TILES, Southern.TRIM_STAIRS, RENDER, new int[]{-1, -1, 5, 7, Southern.RIDGE_Z});
        fill(s, 1, 5, 4, 3, 5, 4, RENDER);
        put(s, 2, 5, 5, Kit.chain());
        put(s, 2, 4, 5, Kit.hangingLantern());
        // Inside: the hearth in the corner, the bed, the chest, the table, the sink, the light.
        Southern.hearth(s, 3, 1, 1, "south", 6);
        chimney(s, 3, 1, 5, 7);
        Furnish.bed(s, 1, 1, 2, "north", "red");
        Furnish.chest(s, 1, 1, 3, "east");
        Furnish.table(s, 3, 1, 3, WOOD);
        Furnish.chair(s, 3, 1, 2, "south", WOOD);
        put(s, 2, 3, 2, Kit.hangingLantern());
        // On the porch: oars, a crate of fish, bundles of dried weed, bougainvillea.
        fill(s, 0, 1, 5, 0, 2, 5, FENCE);
        Furnish.crate(s, 4, 1, 5, WOOD);
        put(s, 4, 2, 5, "minecraft:dried_kelp_block");
        climber(s, new int[]{4, 3, 5}, new int[]{4, 4, 5}, new int[]{3, 4, 5});
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the white house

    /**
     * A small white house: a whitewashed cube on a footing of dressed stone, its flat roof a terrace behind a parapet
     * with a coping of trim, reached by an outside stair that climbs along the east wall from the yard behind a solid
     * parapet of render. On the terrace a light pergola of two piers and poles under a vine with a bench below, the
     * chimney and pots. The door and a window with shutters and pots look onto a little paved yard with a planter and a
     * lantern on an iron arm; bougainvillea climbs the corner. Inside one room: two beds, the hearth with its hood on
     * the west wall, the chest, a table, a sink and a lantern.
     */
    static Structure white() {
        Structure s = new Structure(7, 13, 9);
        footing(s, 0, 1, 4, 6);
        Southern.walls(s, 0, 1, 4, 6, 1, 6);
        Southern.band(s, 0, 1, 4, 6, 6);
        Southern.beamCeiling(s, 1, 2, 3, 5, 5, false);
        fill(s, 1, 6, 2, 3, 6, 5, COTTO);
        Southern.parapet(s, 0, 1, 4, 6, 7);
        // The door and the window to the yard, windows on the other sides.
        s.door(3, 1, 6, DOOR, "north", "left");
        put(s, 3, 3, 6, TRIM);
        Southern.window(s, 1, 2, 6, "south", 1, 2, true, true);
        Southern.window(s, 0, 2, 4, "west", 1, 2, false, false);
        Southern.window(s, 2, 2, 1, "north", 1, 2, true, false);
        // The yard: paving with a border of trim, a planter, a lantern on an iron arm.
        fill(s, 0, 0, 7, 4, 0, 8, PAVING);
        fill(s, 0, 0, 8, 4, 0, 8, TRIM);
        Southern.planter(s, 0, 1, 8, true);
        Southern.wallLantern(s, 4, 3, 7);
        climber(s, new int[]{0, 4, 7}, new int[]{0, 5, 7}, new int[]{1, 5, 7}, new int[]{0, 3, 7}, new int[]{2, 5, 7});
        // The outside stair along the east wall: steps of dressed stone on a base of render, a solid parapet beside them.
        for (int i = 0; i <= 6; i++) {
            int z = 8 - i;
            fill(s, 5, 0, z, 5, i - 1, z, RENDER);
            put(s, 5, i, z, Kit.stairs(STONE_STAIRS, "north"));
            fill(s, 6, 0, z, 6, i + 1, z, RENDER);
            put(s, 6, i + 2, z, Kit.stairs(TRIM_STAIRS, "north"));
        }
        fill(s, 5, 0, 1, 6, 7, 1, RENDER);
        put(s, 5, 8, 1, Kit.slab(TRIM_SLAB));
        put(s, 6, 8, 1, Kit.slab(TRIM_SLAB));
        put(s, 4, 7, 2, AIR);
        put(s, 4, 8, 2, AIR);
        // The terrace: a light pergola of two piers and rafters of poles over the back half under a vine.
        for (int x : new int[]{1, 3}) {
            fill(s, x, 7, 3, x, 9, 3, STONE);
        }
        fill(s, 1, 10, 1, 3, 10, 3, FENCE);
        fill(s, 2, 10, 2, 2, 10, 2, AIR);
        climber(s, new int[]{1, 11, 3}, new int[]{2, 11, 1}, new int[]{3, 11, 2}, new int[]{1, 11, 1}, new int[]{3, 10, 2},
                new int[]{1, 9, 2}, new int[]{3, 11, 3});
        Furnish.bench(s, 1, 2, 2, 2, 7, "south", WOOD);
        put(s, 2, 9, 3, Kit.hangingLantern());
        Southern.hearth(s, 1, 1, 4, "east", 6);
        chimney(s, 1, 4, 7, 8);
        Southern.planter(s, 3, 7, 5, true);
        put(s, 0, 8, 6, Kit.lantern());
        // Inside: two beds against the back wall, the chest, the table, a sink, the light.
        Furnish.bed(s, 2, 1, 3, "north", "red");
        Furnish.bed(s, 3, 1, 3, "north", "red");
        Furnish.chest(s, 2, 1, 5, "north");
        Furnish.table(s, 2, 1, 4, WOOD);
        put(s, 1, 1, 2, "minecraft:water_cauldron[level=3]");
        put(s, 2, 4, 3, Kit.hangingLantern());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the house round a court

    /**
     * A house round a court: a long whitewashed house of one storey under a low hip roof of tiles at the back, and
     * before it a court closed by a garden wall with a door under a little roof of tiles. In the court a well with a
     * head of wrought iron and a lantern over it, and a pergola under vines with a table and benches; pots and
     * planters. The house has two rooms side by side, each with its own door to the court: the bedroom with two beds
     * with iron headboards, and the kitchen with the hearth under its hood, the counter of glazed tiles, the table and
     * the chest.
     */
    static Structure court() {
        Structure s = new Structure(11, 11, 11);
        footing(s, 1, 1, 9, 6);
        Southern.walls(s, 1, 1, 9, 6, 1, 5);
        Southern.band(s, 1, 1, 9, 6, 5);
        Southern.beamCeiling(s, 2, 2, 8, 5, 5, true);
        fill(s, 5, 1, 2, 5, 4, 5, RENDER);
        s.door(5, 1, 5, DOOR, "west", "left");
        // Doors and windows to the court, windows on the other sides.
        s.door(4, 1, 6, DOOR, "north", "left");
        s.door(7, 1, 6, DOOR, "north", "right");
        put(s, 4, 3, 6, TRIM);
        put(s, 7, 3, 6, TRIM);
        Southern.window(s, 2, 2, 6, "south", 1, 2, true, true);
        Southern.window(s, 8, 2, 6, "south", 1, 2, true, true);
        Southern.window(s, 1, 2, 3, "west", 1, 2, true, false);
        Southern.window(s, 9, 2, 3, "east", 1, 2, true, false);
        Southern.window(s, 3, 2, 1, "north", 1, 2, true, false);
        Southern.window(s, 7, 2, 1, "north", 1, 2, true, false);
        // The court: paving with paths of cotto, the garden wall with its door under a little roof of tiles.
        fill(s, 0, 0, 7, 10, 0, 10, PAVING);
        fill(s, 4, 0, 7, 7, 0, 9, COTTO);
        for (int z = 7; z <= 10; z++) {
            for (int x = 0; x <= 10; x++) {
                if (x == 0 || x == 10 || z == 10) {
                    put(s, x, 1, z, STONE);
                    put(s, x, 2, z, RENDER);
                    put(s, x, 3, z, Kit.slab(TRIM_SLAB));
                }
            }
        }
        fill(s, 4, 1, 10, 4, 3, 10, STONE);
        fill(s, 6, 1, 10, 6, 3, 10, STONE);
        s.door(5, 1, 10, DOOR, "north", "left");
        put(s, 5, 0, 10, TRIM);
        put(s, 5, 3, 10, TRIM);
        put(s, 4, 4, 10, Kit.stairs(TILES.stairs(), "east"));
        put(s, 6, 4, 10, Kit.stairs(TILES.stairs(), "west"));
        put(s, 5, 4, 10, Kit.slab(TILES.slab()));
        put(s, 0, 3, 10, Kit.lantern());
        put(s, 10, 3, 10, Kit.lantern());
        // The well: a rim of trim round the water, two posts with an arch of wrought iron, a lantern hanging over it.
        fill(s, 1, 0, 7, 3, 0, 9, TRIM);
        for (int z = 7; z <= 9; z++) {
            for (int x = 1; x <= 3; x++) {
                put(s, x, 1, z, x == 2 && z == 8 ? Southern.WATER : TRIM);
            }
        }
        fill(s, 1, 2, 8, 1, 3, 8, STONE);
        fill(s, 3, 2, 8, 3, 3, 8, STONE);
        fill(s, 1, 4, 8, 3, 4, 8, IRON);
        put(s, 2, 3, 8, Kit.chain());
        put(s, 2, 2, 8, Kit.hangingLantern());
        // The pergola before the kitchen: two piers in front, beams to the wall, vines, the table with its benches.
        fill(s, 6, 1, 9, 6, 3, 9, STONE);
        fill(s, 9, 1, 9, 9, 3, 9, STONE);
        fill(s, 6, 4, 9, 9, 4, 9, Kit.log(BEAM, "x"));
        for (int x : new int[]{6, 9}) {
            fill(s, x, 4, 7, x, 4, 8, Kit.log(BEAM, "z"));
        }
        fill(s, 7, 4, 7, 8, 4, 8, FENCE);
        Southern.wallLantern(s, 5, 3, 7);
        Furnish.longTable(s, 7, 8, 8, 8, 1, WOOD);
        Furnish.bench(s, 7, 9, 8, 9, 1, "north", WOOD);
        put(s, 7, 2, 8, Furnish.candles(2));
        put(s, 8, 2, 8, POT);
        put(s, 8, 3, 8, Kit.hangingLantern());
        Southern.planter(s, 9, 1, 7, true);
        Southern.planter(s, 4, 1, 9, false);
        // The roof, then the vines on the pergola where the eaves leave room.
        Southern.lowRoof(s, 6, TILES, TRIM_STAIRS, null, new int[]{0, 0, 10, 7, Southern.HIP});
        vines(s, 6, 7, 9, 9, 5);
        // The bedroom: two beds with iron headboards, a rug, an amphora, the light.
        Southern.ironBed(s, 2, 1, 4, "north", "red");
        Southern.ironBed(s, 4, 1, 4, "north", "red");
        Southern.amphora(s, 3, 1, 2);
        Furnish.rug(s, 3, 4, 3, 5, 1, Kit.CLOTH_CARPET);
        Furnish.chandelier(s, 3, 4, 4, 1);
        // The kitchen: the hearth, the counter of glazed tiles, the table, the chest, the light.
        Southern.hearth(s, 7, 1, 2, "south", 6);
        chimney(s, 7, 2, 6, 8);
        Southern.kitchen(s, 8, 1, 3, "west", 3);
        Furnish.chest(s, 6, 1, 3, "east");
        Furnish.table(s, 7, 1, 4, WOOD);
        Furnish.chair(s, 6, 1, 4, "east", WOOD);
        Furnish.chandelier(s, 7, 4, 4, 1);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the tall town house

    /**
     * A tall narrow town house of three storeys made to stand wall to wall in a street: side walls with quoins and only
     * small windows, a low gable of tiles to the street over a deep eave, bands of trim at every floor. Below, the door
     * between two windows with iron grilles; above, a balcony on corbels with an iron railing and lanterns on each
     * floor, shutters beside its door, bougainvillea climbing from a pot on the pavement. Inside, a stair of stone and
     * then of timber climbs along the back wall from floor to floor: the kitchen with the hearth, whose flue rises
     * through every floor to the chimney, the counter and the table below; a bedroom with two beds on the first floor;
     * a bedroom with a writing desk and books on the top.
     */
    static Structure narrow() {
        Structure s = new Structure(7, 21, 11);
        footing(s, 0, 1, 6, 8);
        Southern.walls(s, 0, 1, 6, 8, 1, 15);
        for (int y : new int[]{5, 10, 15}) {
            Southern.band(s, 0, 1, 6, 8, y);
            Southern.beamCeiling(s, 1, 2, 5, 7, y, true);
        }
        // The stairs along the back wall: stone below on a solid base, timber above.
        for (int i = 1; i <= 5; i++) {
            if (i > 1) {
                fill(s, i, 1, 2, i, i - 1, 2, RENDER);
            }
            put(s, i, i, 2, Kit.stairs(STONE_STAIRS, "east"));
            put(s, i, 5 + i, 2, Kit.stairs(PLANK_STAIRS, "east"));
        }
        fill(s, 1, 5, 2, 4, 5, 2, AIR);
        fill(s, 1, 10, 2, 4, 10, 2, AIR);
        // The street front: the door, windows with grilles, the balconies, a climber from a pot.
        fill(s, 0, 0, 9, 6, 0, 10, PAVING);
        fill(s, 0, 0, 10, 6, 0, 10, TRIM);
        s.door(3, 1, 8, DOOR, "north", "left");
        put(s, 3, 3, 8, CARVED);
        Southern.window(s, 1, 2, 8, "south", 1, 2, false, false, IRON);
        Southern.window(s, 5, 2, 8, "south", 1, 2, false, false, IRON);
        Southern.balcony(s, 3, 6, 8, "south", 3, 2);
        Southern.balcony(s, 3, 11, 8, "south", 3, 2);
        Southern.planter(s, 6, 1, 9, true);
        climber(s, new int[]{6, 2, 9}, new int[]{6, 3, 9}, new int[]{6, 4, 9}, new int[]{6, 5, 9}, new int[]{5, 4, 9});
        // The back: windows on the stairs and the upper rooms.
        Southern.window(s, 3, 7, 1, "north", 1, 2, true, false);
        Southern.window(s, 3, 12, 1, "north", 1, 2, true, false);
        Southern.window(s, 5, 12, 1, "north", 1, 2, false, false);
        for (int y : new int[]{7, 12}) {
            Southern.window(s, 0, y, 4, "west", 1, 2, false, false);
            Southern.window(s, 6, y, 5, "east", 1, 2, false, false);
        }
        // The roof: a low gable to the street, its eave over the top balcony; the chimney.
        Southern.lowRoof(s, 16, TILES, TRIM_STAIRS, RENDER, new int[]{-1, 0, 7, 9, Southern.RIDGE_Z});
        // The kitchen: the hearth on the west wall, its flue up through the house, the counter, the table, the chest.
        Southern.hearth(s, 1, 1, 5, "east", 15);
        chimney(s, 1, 5, 16, 19);
        Southern.kitchen(s, 5, 1, 4, "west", 3);
        Furnish.table(s, 3, 1, 5, WOOD);
        Furnish.chair(s, 3, 1, 4, "south", WOOD);
        Furnish.chair(s, 3, 1, 6, "north", WOOD);
        Furnish.chest(s, 1, 1, 7, "east");
        Furnish.chandelier(s, 3, 5, 4, 1);
        put(s, 5, 4, 3, Kit.hangingLantern());
        // The first floor: two beds by the balcony door, a rug, a crate for clothes, the light.
        bed(s, 1, 6, 6, "south", 1);
        bed(s, 5, 6, 6, "south", -1);
        Furnish.rug(s, 2, 4, 4, 5, 6, Kit.CLOTH_CARPET);
        Southern.amphora(s, 5, 6, 4);
        Furnish.chandelier(s, 3, 5, 9, 1);
        // The top floor: a bed, the writing desk with its chair, books, the light.
        bed(s, 5, 11, 6, "south", -1);
        Furnish.desk(s, 1, 11, 6, "east", WOOD);
        Furnish.books(s, 1, 7, 1, 7, 11, 12);
        Furnish.rug(s, 3, 5, 3, 6, 11, Kit.CLOTH_CARPET);
        Furnish.chandelier(s, 3, 5, 14, 1);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the merchant's house

    /**
     * A merchant's house: two whitewashed storeys under a low hip roof of tiles, the upper one carried over the
     * pavement on an arcade of two round arches that shelters the shop front: the door with two narrow windows, a wide
     * arched shop window whose sill is the counter to the street, a banner hanging on the middle column. Above, tall
     * windows with shutters and pots and a balcony with an iron railing. At the back corner rises the family tower: a
     * square shaft of render with quoins above the roof, an open belvedere of arches with an iron balustrade on its
     * top, a pyramid of tiles and a copper finial. Inside: the shop with its counter, shelves, cloth and amphorae, the
     * storeroom with sacks and crates, the strongroom with the chest at the foot of the tower; up a stone stair the
     * kitchen with its hearth, the hall with the long table and a bed; a bedroom on each floor of the tower, a ladder
     * through all of it up to the belvedere.
     */
    static Structure merchant() {
        Structure s = new Structure(11, 25, 13);
        footing(s, 1, 1, 9, 11);
        Southern.walls(s, 1, 1, 9, 11, 1, 10);
        Southern.band(s, 1, 1, 9, 11, 5);
        Southern.band(s, 1, 1, 9, 11, 10);
        Southern.beamCeiling(s, 2, 2, 8, 9, 5, true);
        Southern.beamCeiling(s, 2, 2, 8, 10, 10, true);
        // The portico under the upper storey, the shop front behind it.
        Southern.loggia(s, 1, 10, 9, 11, 1, 4, 3, "south", false);
        put(s, 1, 0, 10, STONE);
        put(s, 9, 0, 10, STONE);
        fill(s, 2, 1, 9, 8, 4, 9, RENDER);
        s.door(3, 1, 9, DOOR, "north", "left");
        put(s, 3, 3, 9, TRIM);
        Southern.window(s, 2, 2, 9, "south", 1, 2, false, false);
        Southern.window(s, 4, 2, 9, "south", 1, 2, false, false);
        Southern.window(s, 7, 2, 9, "south", 3, 2, false, false);
        put(s, 5, 3, 12, Kit.wallBanner("red", "south"));
        // The tower at the back east corner: its walls through the house and above the roof, its floors and the ladder.
        for (int y = 1; y <= 14; y++) {
            for (int z = 1; z <= 5; z++) {
                for (int x = 5; x <= 9; x++) {
                    if (x != 5 && x != 9 && z != 1 && z != 5) {
                        continue;
                    }
                    boolean corner = (x == 5 || x == 9) && (z == 1 || z == 5);
                    if (y == 5 || y == 10) {
                        if (x == 5 || z == 5) {
                            put(s, x, y, z, RENDER);
                        }
                        continue;
                    }
                    if (y <= 10 && x != 5 && z != 5) {
                        continue;
                    }
                    put(s, x, y, z, y == 14 ? TRIM : corner && y > 10 ? STONE : RENDER);
                }
            }
        }
        fill(s, 6, 14, 2, 8, 14, 4, PLANKS);
        s.door(7, 1, 5, DOOR, "north", "left");
        s.door(7, 6, 5, DOOR, "north", "left");
        // The upper storey: tall windows and the balcony to the street, windows on the sides and the back.
        Southern.window(s, 2, 6, 11, "south", 1, 3, true, true);
        Southern.window(s, 8, 6, 11, "south", 1, 3, true, true);
        Southern.balcony(s, 5, 6, 11, "south", 3, 1);
        Southern.window(s, 1, 7, 8, "west", 1, 2, true, false);
        Southern.window(s, 9, 7, 8, "east", 1, 2, true, false);
        Southern.window(s, 9, 2, 7, "east", 1, 2, true, false);
        Southern.window(s, 3, 2, 1, "north", 1, 2, true, false);
        Southern.window(s, 7, 2, 1, "north", 1, 2, true, false);
        Southern.window(s, 7, 7, 1, "north", 1, 2, true, false);
        put(s, 7, 12, 1, GLASS);
        put(s, 9, 12, 3, GLASS);
        put(s, 7, 12, 5, GLASS);
        // The roof, kept out of the tower, then the belvedere on the tower with its pyramid.
        Southern.lowRoof(s, 11, TILES, TRIM_STAIRS, null, new int[]{0, 0, 10, 12, Southern.HIP});
        fill(s, 6, 11, 2, 8, 13, 4, AIR);
        int eaves = Southern.belfry(s, 5, 1, 15, 5);
        put(s, 7, 18, 3, AIR);
        int top = Southern.pyramid(s, 4, 0, 10, 6, eaves, TILES);
        Southern.finial(s, 7, top, 3);
        for (int y = 1; y <= 14; y++) {
            put(s, 8, y, 4, Kit.ladder("west"));
        }
        // The stone stair up from the shop along the west wall, the opening over it with a railing.
        for (int i = 0; i < 5; i++) {
            int z = 8 - i;
            if (i > 0) {
                fill(s, 2, 1, z, 2, i, z, RENDER);
            }
            put(s, 2, 1 + i, z, Kit.stairs(STONE_STAIRS, "north"));
        }
        fill(s, 2, 5, 5, 2, 5, 8, AIR);
        fill(s, 3, 6, 6, 3, 6, 8, IRON);
        // The shop: the counter with goods, shelves on the tower wall, cloth and amphorae under the window, the light.
        Furnish.longTable(s, 5, 7, 7, 7, 1, WOOD);
        put(s, 5, 2, 7, Furnish.candles(2));
        put(s, 6, 2, 7, "minecraft:heavy_weighted_pressure_plate[power=0]");
        put(s, 7, 2, 7, POT);
        for (int x : new int[]{5, 6, 8}) {
            put(s, x, 3, 6, Kit.trapdoor("minecraft:" + WOOD + "_trapdoor", "south", true));
            put(s, x, 4, 6, x == 6 ? Furnish.candles(3) : POT);
        }
        put(s, 6, 1, 8, "minecraft:blue_wool");
        put(s, 7, 1, 8, "minecraft:yellow_wool");
        put(s, 8, 1, 8, "minecraft:white_wool");
        Southern.amphora(s, 8, 1, 6);
        Furnish.chandelier(s, 4, 7, 4, 1);
        Furnish.chandelier(s, 7, 7, 4, 1);
        // The storeroom behind the shop and the strongroom with the chest at the foot of the tower.
        Furnish.sack(s, 3, 1, 2, false);
        Furnish.sack(s, 4, 1, 2, true);
        Furnish.sack(s, 4, 2, 2, false);
        Furnish.crate(s, 2, 1, 2, WOOD);
        Furnish.crate(s, 2, 1, 3, WOOD);
        Southern.amphora(s, 4, 1, 4);
        put(s, 3, 4, 3, Kit.hangingLantern());
        Furnish.chest(s, 6, 1, 2, "south");
        Furnish.crate(s, 7, 1, 2, WOOD);
        Furnish.crate(s, 7, 2, 2, WOOD);
        Furnish.sack(s, 6, 1, 4, true);
        put(s, 7, 4, 3, Kit.hangingLantern());
        // The kitchen upstairs: the hearth on the back wall, its chimney, the counter of glazed tiles.
        Southern.hearth(s, 3, 6, 2, "south", 10);
        chimney(s, 3, 2, 11, 14);
        Southern.kitchen(s, 4, 6, 3, "west", 2);
        put(s, 3, 9, 4, Kit.hangingLantern());
        // The hall: the long table with benches, a bed by the tower wall, the light.
        Furnish.longTable(s, 5, 8, 7, 8, 6, WOOD);
        Furnish.bench(s, 5, 7, 7, 7, 6, "south", WOOD);
        Furnish.bench(s, 5, 9, 7, 9, 6, "north", WOOD);
        put(s, 5, 7, 8, Furnish.candles(3));
        put(s, 6, 7, 8, POT);
        put(s, 7, 7, 8, Furnish.candles(2));
        Furnish.chandelier(s, 6, 8, 9, 1);
        bed(s, 8, 6, 7, "north", 0);
        Furnish.rug(s, 3, 9, 4, 10, 6, Kit.CLOTH_CARPET);
        Furnish.chandelier(s, 3, 10, 9, 1);
        // The rooms of the tower: a bed on each floor, books on the upper one, light, a bench in the belvedere.
        bed(s, 6, 6, 3, "north", -1);
        put(s, 7, 9, 3, Kit.hangingLantern());
        bed(s, 6, 11, 3, "north", -1);
        Furnish.books(s, 8, 2, 8, 2, 11, 12);
        put(s, 7, 13, 3, Kit.hangingLantern());
        Furnish.bench(s, 6, 2, 6, 3, 15, "east", WOOD);
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- the villa

    /**
     * A villa: two whitewashed storeys under a wide low hip roof of tiles, its front a double loggia of round arches on
     * columns, below open to the garden, above behind an iron railing, with an open stair in the lower loggia climbing
     * to the upper one. Before it a walled garden: a wall of render with a coping of trim, a gate of three under a
     * round arch with amphorae on its piers, a pool with a fountain in the middle, two cypresses framing the loggias,
     * citrus trees in pots and lamp posts in the corners. Over the middle of the loggias rises a bell gable of render
     * with its bell in an arched opening. Inside: the kitchen with its hearth and its counter of glazed tiles, the
     * dining room with the long table, and above two bedrooms with two beds each with iron headboards, the chest and a
     * writing desk.
     */
    static Structure villa() {
        Structure s = new Structure(13, 20, 15);
        footing(s, 1, 1, 11, 8);
        Southern.walls(s, 1, 1, 11, 8, 1, 10);
        Southern.band(s, 1, 1, 11, 8, 5);
        Southern.band(s, 1, 1, 11, 8, 10);
        Southern.beamCeiling(s, 2, 2, 10, 4, 5, false);
        Southern.beamCeiling(s, 2, 2, 10, 4, 10, false);
        // The rooms behind the loggias: the wall between them, the wall between the rooms on both floors.
        fill(s, 2, 1, 5, 10, 4, 5, RENDER);
        fill(s, 2, 6, 5, 10, 9, 5, RENDER);
        fill(s, 2, 5, 5, 10, 5, 5, TRIM);
        fill(s, 6, 1, 2, 6, 4, 4, RENDER);
        fill(s, 6, 6, 2, 6, 9, 4, RENDER);
        // The two loggias, one over the other.
        Southern.loggia(s, 2, 6, 10, 8, 1, 4, 3, "south", false);
        Southern.loggia(s, 2, 6, 10, 8, 6, 4, 3, "south", true);
        // The open stair in the lower loggia: steps of stone carried by stairs hanging under them, the opening above.
        for (int i = 0; i < 5; i++) {
            put(s, 6 + i, 1 + i, 6, Kit.stairs(STONE_STAIRS, "east"));
            if (i > 0) {
                put(s, 6 + i, i, 6, Kit.upside(STONE_STAIRS, "west"));
            }
        }
        fill(s, 6, 5, 6, 9, 5, 6, AIR);
        // Doors from the loggias, windows on the sides and the back.
        s.door(3, 1, 5, DOOR, "north", "left");
        s.door(9, 1, 5, DOOR, "north", "left");
        s.door(3, 6, 5, DOOR, "north", "left");
        s.door(10, 6, 5, DOOR, "north", "left");
        s.door(6, 1, 3, DOOR, "west", "left");
        for (int y : new int[]{2, 7}) {
            Southern.window(s, 1, y, 3, "west", 1, 2, true, y == 7);
            Southern.window(s, 11, y, 3, "east", 1, 2, true, y == 7);
            Southern.window(s, 9, y, 1, "north", 1, 2, true, false);
        }
        Southern.window(s, 4, 7, 1, "north", 1, 2, true, false);
        // The roof and the chimney of the kitchen.
        Southern.lowRoof(s, 11, TILES, TRIM_STAIRS, null, new int[]{0, 0, 12, 9, Southern.HIP});
        Southern.hearth(s, 3, 1, 2, "south", 10);
        chimney(s, 3, 2, 11, 14);
        // The kitchen: the counter, a table with a chair, the light.
        Southern.kitchen(s, 5, 1, 2, "west", 3);
        Furnish.table(s, 2, 1, 4, WOOD);
        Furnish.chair(s, 2, 1, 3, "south", WOOD);
        Furnish.chandelier(s, 3, 3, 4, 1);
        // The dining room: the long table, chairs, books, the light.
        Furnish.longTable(s, 8, 3, 9, 3, 1, WOOD);
        Furnish.chair(s, 7, 1, 3, "east", WOOD);
        Furnish.chair(s, 10, 1, 3, "west", WOOD);
        Furnish.bench(s, 8, 2, 9, 2, 1, "south", WOOD);
        put(s, 8, 2, 3, Furnish.candles(3));
        put(s, 9, 2, 3, POT);
        Furnish.books(s, 10, 2, 10, 2, 1, 2);
        Furnish.chandelier(s, 8, 3, 4, 1);
        Furnish.chandelier(s, 9, 3, 4, 1);
        // The bedrooms: two beds each with iron headboards, the chest, a desk, the light.
        Southern.ironBed(s, 2, 6, 4, "north", "red");
        Southern.ironBed(s, 4, 6, 4, "north", "red");
        Furnish.chest(s, 5, 6, 2, "west");
        Furnish.chandelier(s, 4, 3, 9, 1);
        Southern.ironBed(s, 7, 6, 4, "north", "red");
        Southern.ironBed(s, 9, 6, 4, "north", "red");
        Furnish.desk(s, 10, 6, 2, "south", WOOD);
        Furnish.chandelier(s, 8, 3, 9, 1);
        // The loggias: benches and planters below, a table and chairs above.
        Southern.stoneBench(s, 2, 7, 3, 7, 1, "east");
        Southern.planter(s, 5, 1, 7, true);
        Furnish.table(s, 4, 6, 7, WOOD);
        Furnish.chair(s, 3, 6, 7, "east", WOOD);
        Furnish.chair(s, 5, 6, 7, "west", WOOD);
        Southern.planter(s, 2, 6, 6, true);
        // The garden: its wall, the gate, the pool with its fountain, the beds with cypresses and citrus, lamps.
        fill(s, 0, 0, 9, 12, 0, 14, "minecraft:grass_block[snowy=false]");
        fill(s, 1, 0, 9, 11, 0, 9, PAVING);
        fill(s, 1, 0, 13, 11, 0, 13, PAVING);
        fill(s, 5, 0, 9, 7, 0, 14, PAVING);
        fill(s, 3, 0, 9, 3, 0, 13, COTTO);
        fill(s, 9, 0, 9, 9, 0, 13, COTTO);
        for (int z = 9; z <= 14; z++) {
            for (int x = 0; x <= 12; x++) {
                if (x == 0 || x == 12 || z == 14) {
                    put(s, x, 0, z, STONE);
                    put(s, x, 1, z, STONE);
                    put(s, x, 2, z, RENDER);
                    put(s, x, 3, z, Kit.slab(TRIM_SLAB));
                }
            }
        }
        fill(s, 5, 1, 14, 7, 3, 14, AIR);
        fill(s, 4, 1, 14, 4, 4, 14, STONE);
        fill(s, 8, 1, 14, 8, 4, 14, STONE);
        put(s, 5, 3, 14, Kit.upside(TRIM_STAIRS, "west"));
        put(s, 7, 3, 14, Kit.upside(TRIM_STAIRS, "east"));
        fill(s, 5, 4, 14, 7, 4, 14, TRIM);
        put(s, 6, 4, 14, CARVED);
        fill(s, 5, 0, 14, 7, 0, 14, TRIM);
        Southern.amphora(s, 4, 5, 14);
        Southern.amphora(s, 8, 5, 14);
        pool(s, 4, 10, 8, 12);
        for (int x : new int[]{1, 11}) {
            put(s, x, 0, 9, "minecraft:rooted_dirt");
            cypress(s, x, 1, 9, 11);
            put(s, x == 1 ? 2 : 10, 0, 12, PAVING);
            Southern.citrus(s, x == 1 ? 2 : 10, 1, 12, true);
            Southern.lampPost(s, x, 1, 13);
        }
        put(s, 3, 3, 14, Kit.lantern());
        put(s, 9, 3, 14, Kit.lantern());
        // The bell gable over the middle of the loggias: a wall of render with an arched opening and its bell.
        fill(s, 5, 11, 8, 7, 14, 8, RENDER);
        put(s, 6, 11, 8, TRIM);
        put(s, 6, 12, 8, AIR);
        put(s, 6, 13, 8, Southern.BELL);
        put(s, 5, 15, 8, Kit.stairs(TRIM_STAIRS, "east"));
        put(s, 7, 15, 8, Kit.stairs(TRIM_STAIRS, "west"));
        put(s, 6, 15, 8, TRIM);
        Southern.finial(s, 6, 16, 8);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * A pool of a garden over x1..x2, z1..z2 (five by three): a rim of trim one layer high on the outline of layer 1
     * round the water, a floor of trim with sea lanterns under the water, and in its middle a pedestal of dressed stone
     * with a carved head carrying a bowl of stairs hanging round a cell of water. The water is closed on every side.
     */
    private static void pool(Structure s, int x1, int z1, int x2, int z2) {
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        fill(s, x1, 0, z1, x2, 0, z2, TRIM);
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                boolean rim = x == x1 || x == x2 || z == z1 || z == z2;
                put(s, x, 1, z, rim ? TRIM : Southern.WATER);
                if (!rim && x != cx) {
                    put(s, x, 0, z, Southern.SEA_LANTERN);
                }
            }
        }
        put(s, cx, 1, cz, STONE);
        put(s, cx, 2, cz, CARVED);
        put(s, cx, 3, cz, Southern.WATER);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                String toX = dx > 0 ? "west" : "east";
                String toZ = dz > 0 ? "north" : "south";
                String state = dx == 0 ? Kit.upside(TRIM_STAIRS, toZ) : dz == 0 ? Kit.upside(TRIM_STAIRS, toX)
                        : Southern.stairs(TRIM_STAIRS, toZ, "top", "outer_" + (Furnish.left(toZ).equals(toX) ? "left" : "right"));
                put(s, cx + dx, 3, cz + dz, state);
            }
        }
    }

    // ---------------------------------------------------------------- the smithy

    /**
     * A smithy: a whitewashed hall under a low gable of tiles whose front opens in two round arches on stone columns
     * onto a paved yard with lamp posts at its corners. In the hall the forge: a bed of dressed stone with its fire, a
     * hood over it and the flue through the gable into a tall square stack of dressed stone that stands outside on a
     * widened foot, with bands and a cap of trim; beside it the blast furnace, the quenching tub, the smithing table,
     * the smith's post with his chest, iron and coal in bins, tongs on chains, and the anvil in the middle. In the yard
     * the grindstone, a hitching rail, a trough, the woodpile and sacks of charcoal by the stack, a planter and
     * lanterns.
     */
    static Structure smithy() {
        Structure s = new Structure(13, 16, 11);
        fill(s, 1, 0, 1, 9, 0, 6, STONE);
        fill(s, 2, 0, 2, 8, 0, 5, PAVING);
        Southern.walls(s, 1, 1, 9, 6, 1, 5);
        Southern.band(s, 1, 1, 9, 6, 5);
        Southern.beamCeiling(s, 2, 2, 8, 5, 5, false);
        Southern.arcade(s, 1, 6, 9, 6, 1, 4, 3, TRIM);
        Southern.window(s, 3, 2, 1, "north", 1, 2, true, false);
        Southern.window(s, 1, 2, 3, "west", 1, 2, true, false);
        Southern.lowRoof(s, 6, TILES, TRIM_STAIRS, RENDER, new int[]{0, 0, 10, 7, Southern.RIDGE_X});
        // The yard before the arches: paving with a border of trim, lamp posts at its corners, lanterns in the arches.
        fill(s, 0, 0, 7, 10, 0, 10, PAVING);
        fill(s, 0, 0, 10, 10, 0, 10, TRIM);
        fill(s, 4, 0, 7, 6, 0, 9, COTTO);
        Southern.lampPost(s, 0, 1, 10);
        Southern.lampPost(s, 10, 1, 10);
        put(s, 3, 4, 6, Kit.hangingLantern());
        put(s, 7, 4, 6, Kit.hangingLantern());
        // The forge in the east end of the hall: its bed, its fire, its hood and the flue into the stack.
        fill(s, 7, 1, 2, 8, 1, 3, STONE);
        put(s, 7, 2, 2, STONE);
        put(s, 8, 2, 2, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        fill(s, 7, 2, 3, 8, 2, 3, Kit.slab(STONE_SLAB));
        fill(s, 7, 4, 3, 8, 4, 3, Kit.upside(STONE_STAIRS, "north"));
        fill(s, 7, 4, 2, 8, 5, 2, STONE);
        fill(s, 9, 3, 2, 9, 5, 2, STONE);
        forgeStack(s);
        // The tools: the blast furnace, the tub, the smithing table, the post with the chest, the anvil, the grindstone.
        put(s, 6, 1, 2, "minecraft:blast_furnace[facing=south,lit=false]");
        put(s, 8, 1, 4, "minecraft:water_cauldron[level=3]");
        put(s, 2, 1, 2, "minecraft:smithing_table");
        put(s, 4, 1, 2, "kingdomworld:work_post");
        Furnish.chest(s, 5, 1, 2, "south");
        put(s, 5, 1, 4, "minecraft:anvil[facing=west]");
        put(s, 3, 1, 8, "minecraft:grindstone[face=floor,facing=south]");
        put(s, 2, 1, 5, "minecraft:raw_iron_block");
        put(s, 2, 1, 4, "minecraft:coal_block");
        put(s, 2, 2, 4, Kit.slab(PLANK_SLAB));
        put(s, 3, 1, 2, Kit.log("minecraft:stripped_oak_log", "y"));
        put(s, 3, 2, 2, "minecraft:iron_bars");
        for (int x : new int[]{3, 6}) {
            put(s, x, 4, 4, Kit.chain());
            put(s, x, 3, 4, Kit.chain());
        }
        Furnish.chandelier(s, 4, 3, 4, 1);
        put(s, 7, 4, 5, Kit.hangingLantern());
        // The yard: a hitching rail, a trough, the woodpile and charcoal by the stack, a planter.
        fill(s, 1, 1, 8, 1, 1, 9, FENCE);
        put(s, 1, 2, 8, Kit.slab(PLANK_SLAB));
        put(s, 1, 2, 9, Kit.slab(PLANK_SLAB));
        put(s, 8, 1, 9, "minecraft:water_cauldron[level=3]");
        put(s, 9, 1, 9, "minecraft:water_cauldron[level=3]");
        Southern.planter(s, 9, 1, 7, true);
        fill(s, 11, 0, 5, 12, 1, 8, Kit.log("minecraft:oak_log", "z"));
        fill(s, 11, 2, 5, 11, 2, 8, Kit.log("minecraft:oak_log", "z"));
        fill(s, 12, 2, 5, 12, 2, 8, Kit.slab(PLANK_SLAB));
        fill(s, 11, 3, 5, 11, 3, 8, Kit.slab(PLANK_SLAB));
        Furnish.sack(s, 11, 0, 9, false);
        Furnish.sack(s, 12, 0, 9, false);
        Furnish.sack(s, 12, 1, 9, false);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The stack of the forge outside the east gable of the smithy: a shaft of dressed stone two by two from the ground
     * on a foot widened along the wall and sloped back above it, bands of trim at the eaves and at the top, and a cap
     * of trim stairs rising to a ridge. It is stone in every look and never brick: no part of it is a chimney that a
     * look may take away or turn red.
     */
    private static void forgeStack(Structure s) {
        fill(s, 10, 0, 2, 11, 12, 3, STONE);
        fill(s, 10, 0, 1, 11, 3, 4, STONE);
        fill(s, 10, 4, 1, 11, 4, 1, Kit.stairs(STONE_STAIRS, "south"));
        fill(s, 10, 4, 4, 11, 4, 4, Kit.stairs(STONE_STAIRS, "north"));
        fill(s, 10, 0, 1, 11, 0, 4, PAVING);
        fill(s, 10, 7, 2, 11, 7, 3, TRIM);
        fill(s, 10, 11, 2, 11, 11, 3, TRIM);
        put(s, 10, 12, 2, Kit.stairs(TRIM_STAIRS, "east"));
        put(s, 10, 12, 3, Kit.stairs(TRIM_STAIRS, "east"));
        put(s, 11, 12, 2, Kit.stairs(TRIM_STAIRS, "west"));
        put(s, 11, 12, 3, Kit.stairs(TRIM_STAIRS, "west"));
    }

    // ---------------------------------------------------------------- the tavern

    /**
     * A tavern (osteria): two whitewashed storeys under a low hip roof of tiles, before it a terrace under a pergola of
     * vines on stone piers with two tables and their benches, lanterns hanging from the rafters, planters and amphorae;
     * a sign hangs on its bracket from the side wall beside a lantern on an iron arm. In the taproom the counter of
     * blue glazed tiles, behind it the innkeeper's post with his chest, three barrels, shelves with jugs and candles
     * and the kitchen counter; a hearth under its hood on the side wall; two long tables with benches under lanterns; a
     * stone stair up to the hall over it and two rooms for guests with two beds each.
     */
    static Structure tavern() {
        Structure s = new Structure(13, 18, 15);
        footing(s, 1, 1, 11, 9);
        Southern.walls(s, 1, 1, 11, 9, 1, 10);
        Southern.band(s, 1, 1, 11, 9, 5);
        Southern.band(s, 1, 1, 11, 9, 10);
        Southern.beamCeiling(s, 2, 2, 10, 8, 5, true);
        Southern.beamCeiling(s, 2, 2, 10, 8, 10, true);
        // Doors and windows.
        s.door(6, 1, 9, DOOR, "north", "left");
        put(s, 6, 3, 9, CARVED);
        Southern.window(s, 3, 2, 9, "south", 1, 2, true, true);
        Southern.window(s, 9, 2, 9, "south", 1, 2, true, true);
        Southern.window(s, 5, 7, 9, "south", 1, 2, true, true);
        Southern.window(s, 9, 7, 9, "south", 1, 2, true, true);
        Southern.window(s, 2, 7, 9, "south", 1, 2, false, false);
        Southern.window(s, 11, 2, 7, "east", 1, 2, true, false);
        Southern.window(s, 11, 7, 7, "east", 1, 2, true, false);
        Southern.window(s, 1, 7, 7, "west", 1, 2, true, false);
        Southern.window(s, 6, 7, 1, "north", 1, 2, true, false);
        Southern.window(s, 9, 2, 1, "north", 1, 2, true, false);
        put(s, 12, 4, 8, "minecraft:spruce_wall_hanging_sign[facing=north,waterlogged=false]");
        Southern.wallLantern(s, 12, 3, 5);
        // The roof and the chimney of the hearth.
        Southern.lowRoof(s, 11, TILES, TRIM_STAIRS, null, new int[]{0, 0, 12, 10, Southern.HIP});
        Southern.hearth(s, 10, 1, 4, "west", 10);
        chimney(s, 10, 4, 11, 13);
        // The stone stair along the west wall on a solid base, the opening over it.
        for (int i = 0; i < 5; i++) {
            int z = 8 - i;
            if (i > 0) {
                fill(s, 2, 1, z, 2, i, z, RENDER);
            }
            put(s, 2, 1 + i, z, Kit.stairs(STONE_STAIRS, "north"));
        }
        fill(s, 2, 5, 5, 2, 5, 8, AIR);
        // The counter, the innkeeper's post with his chest, the barrels, the shelves, the kitchen counter, a smoker.
        fill(s, 3, 1, 4, 6, 1, 4, Southern.AZULEJO_BLUE + "[facing=south]");
        put(s, 3, 2, 4, Furnish.candles(2));
        put(s, 5, 2, 4, POT);
        put(s, 6, 2, 4, Furnish.candles(3));
        put(s, 5, 1, 2, "kingdomworld:work_post");
        Furnish.chest(s, 6, 1, 2, "south");
        put(s, 4, 1, 2, Kit.barrel());
        put(s, 4, 1, 3, Kit.barrel());
        put(s, 3, 1, 2, Kit.barrel());
        for (int x = 3; x <= 6; x++) {
            put(s, x, 3, 2, Kit.trapdoor("minecraft:" + WOOD + "_trapdoor", "south", true));
            put(s, x, 4, 2, x % 2 == 0 ? POT : Furnish.candles(1));
        }
        Southern.kitchen(s, 7, 1, 2, "south", 3);
        put(s, 10, 1, 2, "minecraft:smoker[facing=west,lit=false]");
        put(s, 5, 4, 3, Kit.hangingLantern());
        // The taproom: two long tables with benches, the light.
        for (int x : new int[]{3, 7}) {
            Furnish.longTable(s, x, 7, x + 2, 7, 1, WOOD);
            Furnish.bench(s, x, 6, x + 2, 6, 1, "south", WOOD);
            Furnish.bench(s, x, 8, x + 2, 8, 1, "north", WOOD);
            put(s, x, 2, 7, Furnish.candles(2));
            put(s, x + 2, 2, 7, POT);
            Furnish.chandelier(s, x + 1, 7, 4, 1);
        }
        Southern.amphora(s, 10, 1, 8);
        // Upstairs: the hall over the stair, the wall of the rooms with their doors, two rooms of two beds each.
        fill(s, 3, 6, 5, 10, 9, 5, RENDER);
        fill(s, 3, 6, 6, 3, 9, 8, RENDER);
        fill(s, 7, 6, 6, 7, 9, 8, RENDER);
        s.door(5, 6, 5, DOOR, "north", "left");
        s.door(9, 6, 5, DOOR, "north", "left");
        Furnish.bench(s, 5, 2, 8, 2, 6, "south", WOOD);
        Furnish.table(s, 4, 6, 3, WOOD);
        put(s, 6, 9, 3, Kit.hangingLantern());
        put(s, 2, 9, 4, Kit.hangingLantern());
        for (int x : new int[]{4, 8}) {
            Furnish.bed(s, x, 6, 7, "north", "red");
            Furnish.bed(s, x + 2, 6, 7, "north", "red");
            put(s, x + 1, 6, 8, Kit.topSlab("minecraft:" + WOOD + "_slab"));
            put(s, x + 1, 7, 8, Furnish.candles(1));
            put(s, x + 1, 9, 7, Kit.hangingLantern());
        }
        // The terrace: the pergola on piers before the front, its tables, lanterns, planters, an amphora.
        fill(s, 0, 0, 10, 12, 0, 14, PAVING);
        fill(s, 5, 0, 10, 7, 0, 14, COTTO);
        fill(s, 0, 0, 14, 12, 0, 14, TRIM);
        for (int x : new int[]{1, 4, 8, 11}) {
            fill(s, x, 1, 13, x, 3, 13, STONE);
            fill(s, x, 4, 10, x, 4, 13, Kit.log(BEAM, "z"));
        }
        fill(s, 1, 4, 13, 11, 4, 13, Kit.log(BEAM, "x"));
        for (int x : new int[]{2, 6, 10}) {
            fill(s, x, 4, 10, x, 4, 12, FENCE);
        }
        vines(s, 1, 10, 11, 13, 5);
        for (int x : new int[]{2, 9}) {
            Furnish.longTable(s, x, 11, x + 1, 11, 1, WOOD);
            Furnish.bench(s, x, 12, x + 1, 12, 1, "north", WOOD);
            Furnish.chair(s, x - 1 < 1 ? x : x - 1, 1, 11, "east", WOOD);
            put(s, x, 2, 11, Furnish.candles(2));
            put(s, x + 1, 2, 11, POT);
        }
        put(s, 2, 3, 11, Kit.hangingLantern());
        put(s, 6, 3, 11, Kit.hangingLantern());
        put(s, 10, 3, 11, Kit.hangingLantern());
        Southern.planter(s, 0, 1, 13, true);
        Southern.planter(s, 12, 1, 13, true);
        Southern.amphora(s, 0, 1, 10);
        Southern.amphora(s, 12, 1, 10);
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- the town wall and its gate

    /** Where the gate passage runs (x) and where one walks on the walls and in the rooms over the gate (feet). */
    private static final int GATE_X1 = 10;
    private static final int GATE_X2 = 12;
    private static final int WALK = 7;

    /**
     * A piece of the town wall with its gate: short curtains of light dressed stone with swallowtail merlons on the
     * field side and a walk on their top; between them two square towers standing out to the field and, set between the
     * towers, the gatehouse: a block of dressed stone pierced by a round arch of three with a ring of trim and a carved
     * keystone at both ends, a vaulted passage with the leaves of the gate standing open, and over the arch the gate
     * chamber with two barred windows and the banner of the town between them, under merlons. The towers rise high
     * above it: rooms at the foot and at the walk, an open loggia of arches with an iron balustrade on the top under a
     * pyramid of tiles with a copper finial, a ladder through them. Stairs of stone climb from the town along the backs
     * of the towers to the walks; lanterns light the passage, the stairs, the walks and every room.
     */
    static Structure wallGate() {
        Structure s = new Structure(23, 22, 11);
        // The road through the gate.
        fill(s, GATE_X1, 0, 0, GATE_X2, 0, 10, PAVING);
        // The curtains with their walk and merlons.
        for (int[] c : new int[][]{{0, 2}, {20, 22}}) {
            fill(s, c[0], 0, 4, c[1], WALK - 2, 6, STONE);
            fill(s, c[0], 0, 4, c[1], 0, 6, PAVING);
            fill(s, c[0], WALK - 1, 4, c[1], WALK - 1, 6, TRIM);
            Southern.merlons(s, c[0], 6, c[1], 6, WALK, STONE, STONE_STAIRS, true);
        }
        put(s, 0, WALK, 4, Kit.lantern());
        put(s, 22, WALK, 4, Kit.lantern());
        // The towers.
        gateTower(s, 3, "east");
        gateTower(s, 15, "west");
        // The stairs from the town along the backs of the towers, on solid bases, with landings to the walks.
        for (int i = 0; i < 6; i++) {
            fill(s, 8 - i, 0, 3, 8 - i, i, 3, STONE);
            put(s, 8 - i, i + 1, 3, Kit.stairs(STONE_STAIRS, "west"));
            fill(s, 14 + i, 0, 3, 14 + i, i, 3, STONE);
            put(s, 14 + i, i + 1, 3, Kit.stairs(STONE_STAIRS, "east"));
        }
        fill(s, 2, 0, 3, 2, WALK - 1, 3, STONE);
        fill(s, 20, 0, 3, 20, WALK - 1, 3, STONE);
        put(s, 2, WALK - 1, 3, TRIM);
        put(s, 20, WALK - 1, 3, TRIM);
        // The gatehouse, set back between the towers: the block, the vaulted passage, the arch with its ring and keystone.
        fill(s, 8, 0, 4, 14, 10, 7, STONE);
        fill(s, 8, 0, 4, 14, 0, 7, PAVING);
        fill(s, GATE_X1, 1, 4, GATE_X2, 5, 7, AIR);
        for (int z = 4; z <= 7; z++) {
            boolean end = z == 4 || z == 7;
            put(s, GATE_X1, 5, z, Kit.upside(end ? TRIM_STAIRS : STONE_STAIRS, "west"));
            put(s, GATE_X2, 5, z, Kit.upside(end ? TRIM_STAIRS : STONE_STAIRS, "east"));
        }
        for (int z : new int[]{4, 7}) {
            fill(s, GATE_X1 - 1, 1, z, GATE_X1 - 1, 5, z, TRIM);
            fill(s, GATE_X2 + 1, 1, z, GATE_X2 + 1, 5, z, TRIM);
            fill(s, GATE_X1 - 1, 6, z, GATE_X2 + 1, 6, z, TRIM);
            put(s, 11, 6, z, CARVED);
        }
        put(s, 11, 5, 6, Kit.hangingLantern());
        s.set(GATE_X1, 1, 6, DOOR + "[facing=east,half=lower,hinge=right,open=true,powered=false]");
        s.set(GATE_X1, 2, 6, DOOR + "[facing=east,half=upper,hinge=right,open=true,powered=false]");
        s.set(GATE_X2, 1, 6, DOOR + "[facing=west,half=lower,hinge=left,open=true,powered=false]");
        s.set(GATE_X2, 2, 6, DOOR + "[facing=west,half=upper,hinge=left,open=true,powered=false]");
        // The gate chamber over the passage: its floor, the doors from the towers, barred windows, the ladder to the top.
        fill(s, 9, WALK - 1, 5, 13, WALK - 1, 6, PLANKS);
        fill(s, 9, WALK, 5, 13, WALK + 2, 6, AIR);
        fill(s, 8, WALK, 6, 8, WALK + 1, 6, AIR);
        fill(s, 14, WALK, 6, 14, WALK + 1, 6, AIR);
        for (int x : new int[]{9, 13}) {
            fill(s, x, WALK, 7, x, WALK + 1, 7, IRON);
            put(s, x, WALK + 2, 7, TRIM);
            fill(s, x, WALK, 4, x, WALK + 1, 4, IRON);
        }
        put(s, 11, WALK + 1, 8, Kit.wallBanner("red", "south"));
        Southern.band(s, 8, 4, 14, 7, 10);
        for (int y = WALK; y <= 10; y++) {
            put(s, 11, y, 5, Kit.ladder("south"));
        }
        Furnish.bench(s, 12, 6, 13, 6, WALK, "north", WOOD);
        put(s, 10, WALK, 5, "minecraft:grindstone[face=floor,facing=south]");
        put(s, 11, WALK + 2, 6, Kit.hangingLantern());
        Southern.merlons(s, 8, 7, 14, 7, 11, STONE, STONE_STAIRS, true);
        Southern.merlons(s, 8, 4, 14, 4, 11, STONE, STONE_STAIRS, true);
        put(s, 8, 11, 5, Kit.lantern());
        put(s, 14, 11, 5, Kit.lantern());
        // Lanterns on the town side by the passage and over the stairs.
        Southern.wallLantern(s, 9, 3, 3);
        Southern.wallLantern(s, 13, 3, 3);
        Southern.wallLantern(s, 5, 7, 3);
        Southern.wallLantern(s, 17, 7, 3);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * A tower of the gate, five by five with its north-west corner at (x1, 4), standing out to the field: a shaft of
     * light dressed stone with bands of trim at the walk and at its top, a room at the foot and a room at the walk with
     * a door to the curtain on its outer side and one to the gate chamber on the side {@code inward}, loopholes of iron
     * bars, and on its top an open loggia of arches with an iron balustrade under a pyramid of tiles; a ladder from the
     * foot to the loggia, a bench in the guard room, lanterns on every floor.
     */
    private static void gateTower(Structure s, int x1, String inward) {
        int x2 = x1 + 4;
        int z1 = 4;
        int z2 = 8;
        int cx = x1 + 2;
        int top = WALK + 4;
        fill(s, x1, 0, z1, x2, 0, z2, STONE);
        fill(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, PAVING);
        s.walls(x1, 1, z1, x2, top, z2, STONE);
        Southern.band(s, x1, z1, x2, z2, WALK - 1);
        Southern.band(s, x1, z1, x2, z2, top);
        fill(s, x1 + 1, WALK - 1, z1 + 1, x2 - 1, WALK - 1, z2 - 1, PLANKS);
        fill(s, x1 + 1, top, z1 + 1, x2 - 1, top, z2 - 1, PLANKS);
        int in = inward.equals("east") ? x2 : x1;
        int out = inward.equals("east") ? x1 : x2;
        s.door(in, WALK, 6, DOOR, Furnish.opposite(inward), "left");
        s.door(out, WALK, 5, DOOR, inward, "left");
        for (int y : new int[]{2, WALK + 1}) {
            fill(s, cx, y, z2, cx, y + 1, z2, IRON);
            put(s, cx, y + 2, z2, TRIM);
            fill(s, out, y, 7, out, y + 1, 7, IRON);
        }
        int eaves = Southern.belfry(s, x1, z1, top + 1, 5);
        put(s, cx, top + 4, 6, Kit.hangingLantern());
        int apex = Southern.pyramid(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, eaves, TILES);
        Southern.finial(s, cx, apex, 6);
        int lx = inward.equals("east") ? x1 + 1 : x2 - 1;
        for (int y = 1; y <= top; y++) {
            put(s, lx, y, z1 + 1, Kit.ladder("south"));
        }
        put(s, cx, WALK - 2, 6, Kit.hangingLantern());
        put(s, cx, top - 1, 6, Kit.hangingLantern());
        int bx = inward.equals("east") ? x2 - 1 : x1 + 1;
        Furnish.bench(s, bx, 6, bx, 7, 1, inward.equals("east") ? "west" : "east", WOOD);
        Furnish.weaponRack(s, cx, 1, 5, "south", WOOD);
    }
}
