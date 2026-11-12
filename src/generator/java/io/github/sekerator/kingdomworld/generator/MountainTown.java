package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The town of the style "mountain" (see {@link Mountain}): the houses of a hold of miners and smiths, from the dugout of
 * the poor half sunk in a bank of earth to the hall of a noble family behind its gated court, its smithy, its tavern and a
 * piece of its town wall with the gate. All of them are heavy and low, of stone on battered feet, with dark bands, small
 * deep windows of amber glass, deep doors and low roofs of stone slabs whose gables are coped and crowned with runes; timber
 * shows only where it is work (porches, brackets, balconies, sheds), and the glow of hearths and forges, gold and red cloth
 * set the accents. Every one is furnished for those who live or work there.
 */
final class MountainTown {
    private static final String WALL = Mountain.WALL;
    private static final String ROUGH = Mountain.ROUGH;
    private static final String ROUGH_STAIRS = Mountain.ROUGH_STAIRS;
    private static final String ROUGH_SLAB = Mountain.ROUGH_SLAB;
    private static final String TRIM = Mountain.TRIM;
    private static final String TRIM_STAIRS = Mountain.TRIM_STAIRS;
    private static final String TRIM_SLAB = Mountain.TRIM_SLAB;
    private static final String CARVED = Mountain.CARVED;
    private static final String FLOOR = Mountain.FLOOR;
    private static final String DARK = Mountain.DARK;
    private static final String DARK_STAIRS = Mountain.DARK_STAIRS;
    private static final String DARK_SLAB = Mountain.DARK_SLAB;
    private static final String EMBER = Mountain.EMBER;
    private static final String COPPER = Mountain.COPPER;
    private static final String COPPER_STAIRS = Mountain.COPPER_STAIRS;
    private static final String BEAM = Mountain.BEAM;
    private static final String LOG = Mountain.LOG;
    private static final String PLANKS = Mountain.PLANKS;
    private static final String STAIRS = Mountain.STAIRS;
    private static final String SLAB = Mountain.SLAB;
    private static final String FENCE = Mountain.FENCE;
    private static final String WOOD = Mountain.WOOD;
    private static final String CLOTH = Mountain.CLOTH;
    private static final String CARPET = Mountain.CARPET;
    private static final Kit.Roof ROOF = Mountain.ROOF;
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String MOSS = "minecraft:moss_block";
    private static final String AIR = Structure.AIR;

    private MountainTown() {
    }

    // ---------------------------------------------------------------- the plans of the houses

    /**
     * The houses the house program draws, from the poorest to the richest: the name, the box (width along x, height, depth
     * along z), the number of beds (homes) and the icon.
     */
    enum House {
        DUGOUT("dugout", 9, 9, 9, 1, "minecraft:moss_block"),
        MINER("miner", 9, 10, 11, 2, "minecraft:iron_pickaxe"),
        FORGE("forge", 13, 19, 13, 3, "minecraft:iron_ingot"),
        ROCK("rock", 11, 20, 13, 2, "minecraft:stone"),
        MERCHANT("merchant", 13, 19, 15, 3, "minecraft:gold_ingot"),
        NOBLE("noble", 13, 20, 15, 4, "minecraft:gilded_blackstone");

        final String key;
        final int width;
        final int height;
        final int depth;
        final int beds;
        final String icon;

        House(String key, int width, int height, int depth, int beds, String icon) {
            this.key = key;
            this.width = width;
            this.height = height;
            this.depth = depth;
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
        for (House house : House.values()) {
            Structure s = house(house);
            list.add(new BuildingGenerator.Building("mountain_house_" + house.key, "housing", "house", house.icon, (s.sizeX - 1) / 2, 0,
                    s.sizeZ - 1, s, "house", 1, ""));
        }
        Structure smithy = smithy();
        list.add(new BuildingGenerator.Building("mountain_smithy", "production", "workshop", "minecraft:anvil", (smithy.sizeX - 1) / 2, 0,
                smithy.sizeZ - 1, smithy, "workshop", 1, "").at("door", 6, 0, 11));
        Structure tavern = tavern();
        list.add(new BuildingGenerator.Building("mountain_tavern", "trade", "tavern", "minecraft:barrel", (tavern.sizeX - 1) / 2, 0,
                tavern.sizeZ - 1, tavern, "tavern", 1, "").at("door", 7, 1, 14));
        Structure gate = wallGate();
        list.add(new BuildingGenerator.Building("mountain_wall_gate", "defense", "gatehouse", "minecraft:polished_blackstone_bricks",
                (gate.sizeX - 1) / 2, 0, gate.sizeZ - 1, gate, "tower", 1, "")
                .at("door", 11, 1, 10)
                .at("castle_gate", 10, 1, 5).at("castle_gate", 12, 1, 5)
                .at("castle_wall", 9, GATE_WALK, 4).at("castle_wall", 13, GATE_WALK, 4));
        return list;
    }

    /**
     * The house program: draws the house of a plan. Every house is built the same way: a body of stone on a battered or
     * banked foot with its storeys and floors, its roof, its door and windows, the hearth and its stack, then what sets the
     * plan apart, then the furniture: beds by the number of the plan, one chest, a table, light in every room.
     */
    static Structure house(House house) {
        Structure s = new Structure(house.width, house.height, house.depth);
        switch (house) {
            case DUGOUT -> dugout(s);
            case MINER -> miner(s);
            case FORGE -> forgeHouse(s);
            case ROCK -> rockHouse(s);
            case MERCHANT -> merchant(s);
            case NOBLE -> noble(s);
        }
        int beds = 0;
        for (int y = 0; y < s.sizeY; y++) {
            for (int z = 0; z < s.sizeZ; z++) {
                for (int x = 0; x < s.sizeX; x++) {
                    String state = s.get(x, y, z);
                    if (state.contains("_bed[") && state.contains("part=head")) {
                        beds++;
                    }
                }
            }
        }
        if (beds != house.beds) {
            throw new IllegalStateException("The house " + house.key + " has " + beds + " beds instead of " + house.beds);
        }
        return s;
    }

    // ---------------------------------------------------------------- helpers

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
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

    private static void putIfFree(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z) && !s.solid(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static boolean is(Structure s, int x, int y, int z, String state) {
        return s.inside(x, y, z) && s.get(x, y, z).equals(state);
    }

    /**
     * A lean-to roof of stone slabs: its rows run along x from {@code a1} to {@code a2} (or along z when not
     * {@code alongX}); the high row {@code high} is a full block at layer {@code y}, every row further toward {@code low}
     * half a block lower, the rows of slabs closed beneath by an upper slab so that the roof is whole seen from below.
     */
    private static void leanTo(Structure s, int a1, int a2, int high, int low, int y, boolean alongX) {
        int dir = low > high ? 1 : -1;
        int n = Math.abs(low - high);
        for (int i = 0; i <= n; i++) {
            int r = high + dir * i;
            int layer = y - i / 2;
            for (int a = Math.min(a1, a2); a <= Math.max(a1, a2); a++) {
                int x = alongX ? a : r;
                int z = alongX ? r : a;
                if (i % 2 == 0) {
                    put(s, x, layer, z, ROOF.block());
                } else {
                    put(s, x, layer, z, Kit.slab(ROOF.slab()));
                    putIfFree(s, x, layer - 1, z, Kit.topSlab(ROOF.slab()));
                }
            }
        }
    }

    /**
     * A buttress on a wall whose batter reaches one block out: (x, z) is the cell of the outer face of the wall, the buttress
     * stands on the batter in the cell before it up to layer {@code y2} under a capital of trim, its foot widened by a step
     * of rough stone; the dark bands of the wall behind run on across it.
     */
    private static void buttress(Structure s, int x, int z, String facing, int y2) {
        int[] f = Furnish.step(facing);
        String in = Furnish.opposite(facing);
        int bx = x + f[0];
        int bz = z + f[1];
        for (int y = 0; y <= y2; y++) {
            boolean band = is(s, x, y, z, DARK);
            put(s, bx, y, bz, y < 2 ? ROUGH : band ? DARK : WALL);
        }
        put(s, bx + f[0], 0, bz + f[1], ROUGH);
        put(s, bx + f[0], 1, bz + f[1], Kit.stairs(ROUGH_STAIRS, in));
        put(s, bx + f[0], y2, bz + f[1], Kit.upside(TRIM_STAIRS, in));
        put(s, bx, y2 + 1, bz, Kit.slab(DARK_SLAB));
    }

    /**
     * A hearth of a smith in a house, opening the way of {@code facing}: the fire at (x, y, z) on a bed of dark stone between
     * two cheeks of glowing magma under dark stone, embers at its back in the wall, a hood of copper over it (a lip, then a
     * slope to the wall) and the flue through the two courses of the wall behind it up to layer {@code top}. No furnace: a
     * household cooks and works on the open fire.
     */
    private static void homeForge(Structure s, int x, int y, int z, String facing, int top) {
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        String in = Furnish.opposite(facing);
        put(s, x, y - 1, z, DARK);
        put(s, x, y, z, "minecraft:campfire[facing=" + facing + ",lit=true,signal_fire=false,waterlogged=false]");
        put(s, x, y + 1, z, AIR);
        put(s, x - f[0], y, z - f[1], EMBER);
        for (int u = -1; u <= 1; u += 2) {
            put(s, x + a[0] * u, y, z + a[1] * u, Mountain.MAGMA);
            put(s, x + a[0] * u, y + 1, z + a[1] * u, DARK);
        }
        for (int u = -1; u <= 1; u++) {
            put(s, x + a[0] * u, y + 2, z + a[1] * u, Kit.upside(COPPER_STAIRS, in));
            put(s, x + a[0] * u, y + 3, z + a[1] * u, u == 0 ? COPPER : Kit.stairs(COPPER_STAIRS, in));
        }
        for (int yy = y + 1; yy <= top; yy++) {
            put(s, x - f[0], yy, z - f[1], DARK);
            put(s, x - 2 * f[0], yy, z - 2 * f[1], DARK);
        }
    }

    /**
     * Turf that something covers turns to moss, so that it never turns to bare dirt in the game.
     */
    private static void turf(Structure s) {
        for (int y = 0; y < s.sizeY - 1; y++) {
            for (int z = 0; z < s.sizeZ; z++) {
                for (int x = 0; x < s.sizeX; x++) {
                    if (s.get(x, y, z).equals(GRASS) && s.solid(x, y + 1, z)) {
                        s.set(x, y, z, MOSS);
                    }
                }
            }
        }
    }

    /**
     * The chimney of a hearth or a forge: a stack of rough stone three by three around (x, z) from layer {@code y1}, a collar
     * of dark stone under its head at {@code y2 - 1} and on it a cap of dark stairs sloping up to a fire sunk in its middle,
     * so that it smokes.
     */
    private static void chimney(Structure s, int x, int z, int y1, int y2) {
        fill(s, x - 1, y1, z - 1, x + 1, y2 - 2, z + 1, ROUGH);
        fill(s, x - 1, y2 - 1, z - 1, x + 1, y2 - 1, z + 1, DARK);
        Mountain.Plan cap = Mountain.Plan.box(x - 1, z - 1, x + 1, z + 1);
        String fire = "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]";
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                String st = Mountain.slope(cap, x + dx, z + dz, DARK_STAIRS, false);
                put(s, x + dx, y2, z + dz, st != null ? st : fire);
            }
        }
    }

    /**
     * A natural stone of the rock at a cell: mostly stone, with andesite, tuff and rough stone among it, so that the rock is
     * mottled like a cliff.
     */
    private static String rockStone(int x, int y, int z) {
        int h = Math.floorMod(x * 31 + y * 17 + z * 13 + x * z * 7 + y * x * 3 + y * z * 5, 13);
        return h < 6 ? Mountain.ROCK : h < 9 ? "minecraft:andesite" : h < 11 ? "minecraft:tuff" : ROUGH;
    }

    /**
     * A wall of guests or of a store inside: planks between posts of hewn logs, from (x1, z1) to (x2, z2) and from layer
     * {@code y1} to {@code y2}.
     */
    private static void partition(Structure s, int x1, int z1, int x2, int z2, int y1, int y2) {
        fill(s, x1, y1, z1, x2, y2, z2, PLANKS);
        put(s, x1, y2, z1, Kit.log(BEAM, "y"));
        put(s, x2, y2, z2, Kit.log(BEAM, "y"));
        for (int y = y1; y <= y2; y++) {
            put(s, x1, y, z1, Kit.log(BEAM, "y"));
            put(s, x2, y, z2, Kit.log(BEAM, "y"));
        }
    }

    /**
     * A straight stair of planks going up the way of {@code up} from its first step at (x, y, z), {@code steps} steps,
     * clearing the head room over it.
     */
    private static void stair(Structure s, int x, int y, int z, String up, int steps) {
        int[] d = Furnish.step(up);
        for (int i = 0; i < steps; i++) {
            int sx = x + d[0] * i;
            int sz = z + d[1] * i;
            put(s, sx, y + i, sz, Kit.stairs(STAIRS, up));
            for (int yy = y + i + 1; yy <= y + i + 3; yy++) {
                put(s, sx, yy, sz, AIR);
            }
            for (int yy = y; yy < y + i; yy++) {
                put(s, sx, yy, sz, PLANKS);
            }
        }
    }

    // ---------------------------------------------------------------- the dugout

    /**
     * A dugout of the poor: a hut of stone with one room half sunk in a bank of rough stone under turf, so that from the
     * street one sees only its low roof of slabs and its coped gable; the door sits deep in an arched porch cut through the
     * bank, flanked by dry walls stepping down. A little stack smokes out of the roof, a window in the back gable looks over
     * the bank, logs lie on it. Inside, under a ceiling of slabs: a bed, a hearth behind an iron grille in the wall, the chest,
     * a table with a stool, a water tub, a sack of meal, a rug and a lantern.
     */
    private static void dugout(Structure s) {
        Mountain.Plan hut = Mountain.Plan.box(2, 1, 6, 7);
        // The bank: rough stone under turf around the hut, lower on its outer ring; dry walls at its front ends.
        for (int z = 0; z <= 8; z++) {
            for (int x = 0; x <= 8; x++) {
                if (hut.has(x, z)) {
                    continue;
                }
                boolean outer = x == 0 || x == 8;
                int top = outer ? 1 : 2;
                fill(s, x, 0, z, x, top - 1, z, ROUGH);
                put(s, x, top, z, GRASS);
            }
        }
        for (int x : new int[]{0, 1, 7, 8}) {
            boolean outer = x == 0 || x == 8;
            fill(s, x, 0, 8, x, outer ? 0 : 1, 8, ROUGH);
            put(s, x, outer ? 1 : 2, 8, Kit.slab(ROUGH_SLAB));
        }
        Mountain.walls(s, hut, 0, 3, 1, WALL);
        Mountain.plate(s, hut.grow(-1), 0, FLOOR);
        Mountain.slabRoof(s, 1, 1, 7, 7, 3, Mountain.GABLE_Z, WALL);
        Mountain.archDoor(s, 4, 1, 7, "south", 1, 1);
        put(s, 3, 4, 7, TRIM);
        put(s, 5, 4, 7, TRIM);
        Mountain.window(s, 4, 3, 1, "north", 1, 1);
        Mountain.stack(s, 6, 4, 2, 6, 1);
        // Logs and a lantern on the bank.
        fill(s, 8, 2, 2, 8, 2, 4, Kit.log(LOG, "z"));
        fill(s, 7, 3, 2, 7, 3, 3, Kit.log(LOG, "z"));
        put(s, 1, 3, 8, Kit.lantern());
        put(s, 7, 3, 8, Kit.lantern());
        // Inside.
        Furnish.rug(s, 4, 2, 4, 4, 1, CARPET);
        Furnish.bed(s, 3, 1, 3, "north", "red");
        Furnish.hearth(s, 6, 1, 4, "west");
        Furnish.chest(s, 5, 1, 2, "south");
        Furnish.table(s, 3, 1, 6, WOOD);
        Furnish.chair(s, 3, 1, 5, "south", WOOD);
        put(s, 5, 1, 6, "minecraft:water_cauldron[level=3]");
        Furnish.sack(s, 5, 1, 3, false);
        put(s, 4, 3, 4, Kit.hangingLantern());
        turf(s);
        s.connect();
    }

    // ---------------------------------------------------------------- the miner's house

    /**
     * The house of a miner: one room of stone on a battered foot under a low roof of slabs between two coped gables, whose
     * front slope runs on down over a porch of heavy timber: posts with knee braces under a beam, a railing and a deck of
     * planks, a lantern and a heap of ore. A hearth in the west gable with its stack on the coping; inside two beds, the
     * chest, a table with stools, a water tub, a crate and a rug.
     */
    private static void miner(Structure s) {
        Mountain.Plan house = Mountain.Plan.box(1, 1, 7, 7);
        Mountain.batter(s, house, 0, 1, 2);
        Mountain.walls(s, house, 0, 4, 1, WALL);
        Mountain.plate(s, house.grow(-1), 0, FLOOR);
        Mountain.ring(s, house, 2, DARK);
        Mountain.slabRoof(s, 1, 0, 7, 8, 4, Mountain.GABLE_X, WALL);
        // The porch under the slope of the roof running on: a paving of stone, posts of hewn logs with knee braces under a
        // beam, a bench along the wall.
        fill(s, 1, 0, 8, 7, 0, 10, FLOOR);
        fill(s, 1, 1, 8, 7, 3, 10, AIR);
        for (int x = 1; x <= 7; x++) {
            put(s, x, 4, 9, Kit.stairs(ROOF.stairs(), "north"));
            put(s, x, 4, 10, Kit.slab(ROOF.slab()));
            put(s, x, 3, 10, Kit.log(BEAM, "x"));
        }
        for (int x : new int[]{1, 7}) {
            fill(s, x, 1, 10, x, 2, 10, Kit.log(BEAM, "y"));
        }
        put(s, 2, 2, 10, Kit.upside(STAIRS, "west"));
        put(s, 6, 2, 10, Kit.upside(STAIRS, "east"));
        Furnish.bench(s, 2, 9, 3, 9, 1, "south", WOOD);
        put(s, 4, 3, 9, Kit.hangingLantern());
        Mountain.archDoor(s, 4, 1, 7, "south", 1, 0);
        Mountain.window(s, 2, 2, 7, "south", 2, 1);
        Mountain.window(s, 6, 2, 7, "south", 2, 1);
        Mountain.window(s, 7, 2, 4, "east", 2, 1);
        Mountain.window(s, 4, 2, 1, "north", 2, 1);
        Mountain.fireplace(s, 2, 1, 3, "east", 6);
        Mountain.stack(s, 1, 3, 6, 8, 1);
        // Ore from the pit heaped on the porch.
        put(s, 6, 1, 8, "minecraft:raw_iron_block");
        put(s, 6, 1, 9, "minecraft:coal_block");
        put(s, 5, 1, 9, "minecraft:raw_copper_block");
        // Inside.
        Furnish.rug(s, 3, 3, 4, 4, 1, CARPET);
        Furnish.bed(s, 5, 1, 3, "north", "red");
        Furnish.bed(s, 6, 1, 3, "north", "red");
        Furnish.chest(s, 4, 1, 2, "south");
        Furnish.table(s, 4, 1, 5, WOOD);
        Furnish.chair(s, 3, 1, 5, "east", WOOD);
        Furnish.chair(s, 5, 1, 5, "west", WOOD);
        put(s, 2, 1, 6, "minecraft:water_cauldron[level=3]");
        Furnish.crate(s, 6, 1, 6, WOOD);
        put(s, 6, 2, 6, Furnish.candles(2));
        put(s, 4, 4, 4, Kit.hangingLantern());
        s.connect();
    }


    // ---------------------------------------------------------------- the stone house with a forge

    /**
     * A house of two storeys of a smith's family, in two tiers: a ground storey of stone two blocks thick on a battered foot
     * with buttresses at its front corners, and over its ledge, weathered with trim, an upper storey set back one block
     * under a low hip roof of slabs. Against its east side stands the great stack of the forge, rough stone from the ground
     * to high over the roof with dark bands, a battered foot and a smoking collar; coal and logs are heaped beside it. A deep
     * door, a balcony of timber on brackets before the upper door. Below, the forge of the house with magma and a copper
     * hood, the anvil and the quenching tub, the chest, a table, the ladder up; above, three beds (one in a stone alcove), a
     * table and chairs.
     */
    private static void forgeHouse(Structure s) {
        Mountain.Plan ground = Mountain.Plan.box(1, 1, 9, 9);
        Mountain.Plan upper = ground.grow(-1);
        Mountain.Plan in = upper.grow(-1);
        Mountain.batter(s, ground, 0, 1, 2);
        Mountain.walls(s, ground, 0, 5, 2, WALL);
        Mountain.walls(s, upper, 6, 9, 1, WALL);
        Mountain.plate(s, in, 0, FLOOR);
        Mountain.plate(s, in, 5, PLANKS);
        Mountain.ring(s, ground, 2, DARK);
        Mountain.ring(s, ground, 5, DARK);
        for (int z = ground.z1(); z <= ground.z2(); z++) {
            for (int x = ground.x1(); x <= ground.x2(); x++) {
                if (ground.rim(x, z)) {
                    String ledge = Mountain.slope(ground, x, z, TRIM_STAIRS, false);
                    put(s, x, 6, z, ledge != null ? ledge : TRIM);
                }
            }
        }
        Mountain.slabRoof(s, 1, 1, 9, 9, 9, Mountain.HIP, null);
        // The stack of the forge against the east wall, on a battered foot, with dark bands.
        chimney(s, 11, 5, 0, 14);
        fill(s, 10, 5, 4, 12, 5, 6, DARK);
        for (int x = 10; x <= 12; x++) {
            put(s, x, 0, 3, Kit.stairs(ROUGH_STAIRS, "south"));
            put(s, x, 0, 7, Kit.stairs(ROUGH_STAIRS, "north"));
        }
        // The front: buttresses, the deep door, the balcony before the upper door.
        buttress(s, 1, 9, "south", 5);
        buttress(s, 9, 9, "south", 5);
        Mountain.archDoor(s, 5, 1, 8, "south", 1, 1);
        fill(s, 3, 6, 9, 7, 6, 9, AIR);
        Mountain.balcony(s, 3, 9, 7, 9, 5, "south", 2);
        Mountain.archDoor(s, 5, 6, 8, "south", 1, 0);
        // Windows.
        Mountain.window(s, 1, 2, 5, "west", 2, 2);
        Mountain.window(s, 5, 2, 1, "north", 2, 2);
        Mountain.window(s, 9, 2, 8, "east", 2, 2);
        Mountain.window(s, 2, 7, 4, "west", 2, 1);
        Mountain.window(s, 2, 7, 6, "west", 2, 1);
        Mountain.window(s, 4, 7, 2, "north", 2, 1);
        Mountain.window(s, 6, 7, 2, "north", 2, 1);
        Mountain.window(s, 8, 7, 3, "east", 2, 1);
        Mountain.window(s, 8, 7, 7, "east", 2, 1);
        // Coal and logs by the stack.
        fill(s, 11, 0, 0, 12, 0, 2, Kit.log(LOG, "z"));
        fill(s, 11, 1, 1, 12, 1, 2, Kit.log(LOG, "z"));
        put(s, 11, 0, 9, "minecraft:coal_block");
        put(s, 12, 0, 9, "minecraft:coal_block");
        put(s, 12, 1, 9, "minecraft:coal_block");
        put(s, 12, 0, 10, "minecraft:coal_block");
        Furnish.sack(s, 11, 0, 10, false);
        // Ground floor: the forge of the house, the anvil, the tub, the chest, a table, the ladder.
        homeForge(s, 7, 1, 5, "west", 4);
        put(s, 5, 1, 5, "minecraft:anvil[facing=north]");
        put(s, 6, 1, 3, "minecraft:water_cauldron[level=3]");
        Furnish.chest(s, 3, 1, 3, "east");
        Furnish.sack(s, 4, 1, 3, false);
        Furnish.table(s, 4, 1, 7, WOOD);
        Furnish.chair(s, 3, 1, 7, "east", WOOD);
        put(s, 4, 2, 7, Furnish.candles(2));
        for (int y = 1; y <= 6; y++) {
            put(s, 3, y, 5, Kit.ladder("east"));
        }
        put(s, 5, 4, 6, Kit.hangingLantern());
        put(s, 4, 4, 4, Kit.hangingLantern());
        // Upper floor: three beds, a table, a chair, a rug, light.
        Furnish.rug(s, 4, 5, 6, 6, 6, CARPET);
        Mountain.bedAlcove(s, 4, 6, 3, "south", "red");
        Furnish.bed(s, 7, 6, 4, "north", "red");
        Furnish.bed(s, 7, 6, 6, "south", "red");
        put(s, 7, 6, 5, Kit.topSlab(SLAB));
        put(s, 7, 7, 5, Furnish.candles(1));
        Furnish.table(s, 5, 6, 6, WOOD);
        Furnish.chair(s, 4, 6, 6, "east", WOOD);
        put(s, 5, 9, 5, Kit.hangingLantern());
        s.connect();
    }

    // ---------------------------------------------------------------- the house under the rock

    /**
     * The height of the crag behind the house under the rock at a column (-1 where there is none): a dome of rock that is
     * highest over the back rooms, its face steep toward the house and its back and sides falling away in steps; a spur
     * running down along the west side, a boulder or two at the east.
     */
    private static int rockTop(int x, int z) {
        int noise = Math.floorMod(x * 7 + z * 13 + x * z * 3, 3) - 1;
        if (z <= 5) {
            int back = z < 3 ? (3 - z) * 2 : 0;
            int side = Math.max(0, Math.abs(x - 5) - 2);
            return 15 - back - side + noise;
        }
        if (x == 0 && z <= 10) {
            return 9 - (z - 6) * 3 / 2 + noise;
        }
        if (x == 10 && z <= 7) {
            return 3 - (z - 6) + noise;
        }
        return -1;
    }

    /**
     * A house built against a rock: its back rooms are hewn into a crag of raw stone (mottled stone, andesite and tuff,
     * moss and turf on its ledges, a flowering bush) that rises high behind it and runs down along its west side; only its
     * front stands built, of stone on a battered foot under a lean-to of slabs that leans on the cliff, its side walls
     * stepping up under the slope. A deep portal, small windows, a frieze of runes cut in the rock face over the roof, a
     * stack smoking out of the top of the rock. Inside, the cellar with casks hewn at the back, the hearth whose flue runs up
     * through the rock, a table with benches, the chest, the tub, the stair; above, two beds in alcoves hewn in the rock, a
     * table and chairs.
     */
    private static void rockHouse(Structure s) {
        Mountain.Plan front = Mountain.Plan.box(1, 6, 9, 11);
        Mountain.batter(s, front, 0, 1, 2);
        // The rock.
        for (int z = 0; z <= 12; z++) {
            for (int x = 0; x <= 10; x++) {
                int top = rockTop(x, z);
                for (int y = 0; y <= top; y++) {
                    put(s, x, y, z, rockStone(x, y, z));
                }
                int green = Math.floorMod(x * 5 + z * 3 + x * z, 6);
                if (top >= 0 && green < 3) {
                    put(s, x, top, z, green == 2 ? MOSS : GRASS);
                }
            }
        }
        put(s, 1, rockTop(1, 1) + 1, 1, Kit.leaves("flowering_azalea"));
        put(s, 9, rockTop(9, 2) + 1, 2, Kit.leaves("azalea"));
        put(s, 0, rockTop(0, 6) + 1, 6, Kit.leaves("azalea"));
        // The built front: walls on the battered foot, the side walls stepping up under the lean-to.
        int[] wallTop = {12, 11, 11, 10, 10, 9};
        for (int z = 6; z <= 11; z++) {
            int top = wallTop[z - 6];
            fill(s, 1, 0, z, 2, top, z, WALL);
            fill(s, 8, 0, z, 9, top, z, WALL);
        }
        fill(s, 3, 0, 10, 7, 10, 10, WALL);
        fill(s, 3, 0, 11, 7, 9, 11, WALL);
        fill(s, 1, 2, 11, 9, 2, 11, DARK);
        fill(s, 9, 2, 6, 9, 2, 11, DARK);
        fill(s, 1, 2, 6, 1, 2, 11, DARK);
        leanTo(s, 0, 10, 6, 11, 13, true);
        for (int x = 0; x <= 10; x++) {
            put(s, x, 10, 12, Kit.upside(ROOF.stairs(), "north"));
        }
        // Hew the rooms: the ground room and the upper room behind and inside the front.
        fill(s, 3, 1, 2, 7, 10, 9, AIR);
        fill(s, 3, 0, 2, 7, 0, 9, FLOOR);
        fill(s, 3, 5, 2, 7, 5, 9, PLANKS);
        // The portal, windows, the frieze in the rock, the hearth and its stack on the rock.
        Mountain.archDoor(s, 5, 1, 10, "south", 1, 2);
        Mountain.window(s, 3, 7, 11, "south", 2, 2);
        Mountain.window(s, 7, 7, 11, "south", 2, 2);
        Mountain.window(s, 9, 2, 8, "east", 2, 2);
        Mountain.runeBand(s, 3, 5, 7, 5, 14);
        int stackFoot = rockTop(2, 4);
        Mountain.fireplace(s, 3, 1, 4, "east", stackFoot);
        chimney(s, 2, 4, stackFoot, stackFoot + 3);
        put(s, 0, rockTop(0, 9) + 1, 9, Kit.lantern());
        put(s, 10, rockTop(10, 6) + 1, 6, Kit.lantern());
        // The stair along the east wall, a railing at its well.
        stair(s, 7, 1, 9, "north", 5);
        fill(s, 6, 6, 7, 6, 6, 9, FENCE);
        // Ground room: casks hewn in the back, a table with benches, the chest, the tub, light.
        Mountain.casks(s, 4, 2, 7, 2, 1, "south");
        Mountain.stoneTable(s, 5, 5, 5, 7, 1, true);
        Furnish.chest(s, 3, 1, 8, "east");
        put(s, 3, 1, 9, "minecraft:water_cauldron[level=3]");
        put(s, 5, 4, 3, Kit.hangingLantern());
        put(s, 5, 4, 8, Kit.hangingLantern());
        // Upper room: two beds in alcoves hewn in the rock, a table and chairs, a rug, light.
        Mountain.bedAlcove(s, 4, 6, 2, "south", "red");
        Mountain.bedAlcove(s, 6, 6, 2, "south", "red");
        Furnish.rug(s, 4, 5, 5, 6, 6, CARPET);
        Furnish.table(s, 4, 6, 7, WOOD);
        Furnish.chair(s, 4, 6, 8, "north", WOOD);
        Furnish.chair(s, 5, 6, 7, "west", WOOD);
        put(s, 5, 10, 5, Kit.hangingLantern());
        put(s, 5, 8, 9, Kit.wallTorch("north"));
        turf(s);
        s.connect();
    }

    // ---------------------------------------------------------------- the merchant's house

    /**
     * The house of a merchant on a terrace: a platform of rough stone three high on a battered toe, paved with trim, its
     * parapet coped with slabs, a broad flight between lanterns up from the street; on it a house of two storeys under a
     * coped gable to the street with a rune on its apex and a frieze of runes and gilt stone at its foot. In the west bay
     * the shop opens behind a counter under a striped awning with a valance of banners; in the east bay the deep door, over
     * it a balcony of timber on brackets; a sign of two banners on a beam at the corner, a stack on the roof. In the shop
     * goods of ore and metal, crates and sacks, a desk with a candle, the chest, a shelf; the stair up; above, three beds (one
     * in an alcove), the hearth in the wall, a table and a chair.
     */
    private static void merchant(Structure s) {
        int walk = Mountain.terrace(s, 1, 1, 11, 11, 0, 3, "south", 3);
        Mountain.Plan house = Mountain.Plan.box(1, 1, 11, 8);
        Mountain.walls(s, house, 0, 12, 2, WALL);
        Mountain.Plan in = house.grow(-2);
        Mountain.plate(s, in, 7, PLANKS);
        Mountain.ring(s, house, walk, DARK);
        Mountain.ring(s, house, 7, DARK);
        Mountain.slabRoof(s, 0, 1, 12, 8, 12, Mountain.GABLE_Z, WALL);
        Mountain.runeBand(s, 1, 8, 11, 8, 12);
        // The shop: an opening with its counter, a lintel, a striped awning with a valance of banners.
        fill(s, 3, walk, 8, 5, walk + 1, 8, AIR);
        fill(s, 3, walk, 7, 5, walk, 7, Kit.topSlab(TRIM_SLAB));
        fill(s, 3, walk + 1, 7, 5, walk + 1, 7, AIR);
        put(s, 3, walk + 1, 7, "minecraft:raw_gold_block");
        put(s, 4, walk + 1, 7, "minecraft:flower_pot");
        put(s, 5, walk + 1, 7, Furnish.candles(3));
        for (int x = 2; x <= 6; x++) {
            put(s, x, walk + 2, 8, x == 4 ? CARVED : TRIM);
            put(s, x, walk + 2, 9, x % 2 == 0 ? CLOTH : "minecraft:white_wool");
            put(s, x, walk + 2, 10, Kit.wallBanner("red", "south"));
        }
        put(s, 2, walk + 1, 9, Kit.upside(TRIM_STAIRS, "north"));
        put(s, 6, walk + 1, 9, Kit.upside(TRIM_STAIRS, "north"));
        for (int x : new int[]{1, 11}) {
            for (int y = walk; y <= 11; y++) {
                put(s, x, y, 9, y == walk || y == 7 ? DARK : WALL);
            }
            put(s, x, 11, 10, Kit.upside(TRIM_STAIRS, "north"));
            put(s, x, 12, 9, Kit.slab(DARK_SLAB));
        }
        // The door, the balcony over it, the windows, the sign.
        Mountain.archDoor(s, 9, walk, 7, "south", 1, 1);
        Mountain.balcony(s, 8, 8, 10, 8, 7, "south", 2);
        Mountain.archDoor(s, 9, 8, 8, "south", 1, 0);
        fill(s, 9, 8, 7, 9, 9, 7, AIR);
        Mountain.window(s, 3, 9, 8, "south", 2, 2);
        Mountain.window(s, 6, 9, 8, "south", 2, 2);
        Mountain.window(s, 1, walk + 1, 5, "west", 2, 2);
        Mountain.window(s, 11, walk + 1, 5, "east", 2, 2);
        Mountain.window(s, 1, 9, 6, "west", 2, 2);
        Mountain.window(s, 11, 9, 5, "east", 2, 2);
        Mountain.window(s, 6, 9, 1, "north", 2, 2);
        put(s, 1, 10, 9, Kit.log(BEAM, "z"));
        put(s, 1, 10, 10, Kit.log(BEAM, "z"));
        put(s, 0, 10, 10, Kit.wallBanner("yellow", "west"));
        put(s, 2, 10, 10, Kit.wallBanner("yellow", "east"));
        // The shop: goods, the desk, the chest, a shelf, the stair.
        Furnish.chest(s, 9, walk, 4, "west");
        Furnish.desk(s, 6, walk, 5, "north", WOOD);
        put(s, 3, walk, 3, "minecraft:raw_iron_block");
        put(s, 3, walk + 1, 3, "minecraft:raw_gold_block");
        Furnish.sack(s, 3, walk, 4, false);
        Furnish.crate(s, 9, walk, 3, WOOD);
        put(s, 9, walk + 1, 3, "minecraft:raw_copper_block");
        Furnish.crate(s, 8, walk, 4, WOOD);
        fill(s, 3, walk + 2, 4, 3, walk + 2, 6, Kit.topSlab(TRIM_SLAB));
        put(s, 3, walk + 3, 4, "minecraft:flower_pot");
        put(s, 3, walk + 3, 5, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        put(s, 3, walk + 3, 6, Furnish.candles(2));
        stair(s, 4, walk, 3, "east", 5);
        put(s, 5, 6, 5, Kit.hangingLantern());
        put(s, 8, 6, 5, Kit.hangingLantern());
        // The upper floor: three beds, the hearth, a table, a chair, a rug, light.
        Furnish.rug(s, 4, 4, 8, 4, 8, CARPET);
        Mountain.bedAlcove(s, 4, 8, 6, "north", "red");
        Furnish.bed(s, 6, 8, 5, "south", "red");
        Furnish.bed(s, 9, 8, 4, "north", "red");
        Furnish.hearth(s, 2, 8, 4, "east");
        chimney(s, 2, 4, 13, 16);
        Furnish.table(s, 7, 8, 6, WOOD);
        Furnish.chair(s, 7, 8, 5, "south", WOOD);
        Furnish.chandelier(s, 6, 4, 13, 3);
        s.connect();
    }

    // ---------------------------------------------------------------- the noble hall

    /**
     * The hall of a noble family: a great block of two storeys on a battered foot between coped gables, a great stack on
     * the west gable; in the middle of its front a bay that thickens the wall and rises over the roof as a crown of stone on
     * corbels with a heavy parapet and a great brazier; on the bay the carved face of the forefather of the house with eyes
     * of embers and a crown of gold over the deep portal; buttresses at the corners, hangings of cloth beside the bay. Before
     * it a paved court behind a low wall with a gate of stone: two piers with braziers and an arch under a frieze of runes; a
     * stone of runes and a brazier in the court. Inside, the hall with its hearth, the long stone table with benches under a
     * great chandelier and the high seat under a hanging of cloth, the stair up; above four beds (two in alcoves), the chest,
     * a table, light.
     */
    private static void noble(Structure s) {
        Mountain.Plan hall = Mountain.Plan.box(1, 1, 11, 9);
        Mountain.batter(s, hall, 0, 1, 2);
        Mountain.batter(s, Mountain.Plan.box(3, 9, 9, 10), 0, 1, 2);
        Mountain.walls(s, hall, 0, 10, 2, WALL);
        Mountain.Plan in = hall.grow(-2);
        Mountain.plate(s, in, 0, FLOOR);
        Mountain.plate(s, in, 5, PLANKS);
        Mountain.ring(s, hall, 2, DARK);
        Mountain.ring(s, hall, 5, DARK);
        Mountain.ring(s, hall, 10, DARK);
        Mountain.slabRoof(s, 1, 0, 11, 10, 10, Mountain.GABLE_X, WALL);
        // The bay: the front wall thickened in the middle, rising over the roof as a crown on corbels.
        Mountain.Plan bay = Mountain.Plan.box(4, 7, 8, 10);
        fill(s, 3, 0, 9, 9, 12, 10, WALL);
        fill(s, 4, 11, 7, 8, 14, 10, WALL);
        Mountain.ring(s, bay, 13, DARK);
        Mountain.corbels(s, bay, 14, 1);
        Mountain.Plan crown = bay.grow(1);
        Mountain.plate(s, crown, 15, FLOOR);
        Mountain.parapet(s, crown, 16);
        Mountain.brazier(s, 6, 16, 8, true);
        // The portal under the face, buttresses, hangings, windows, the hearth and its stack.
        Mountain.archDoor(s, 6, 1, 10, "south", 1, 1);
        fill(s, 6, 1, 8, 6, 2, 9, AIR);
        Mountain.face(s, 6, 5, 10, "south", 0);
        buttress(s, 1, 9, "south", 9);
        buttress(s, 11, 9, "south", 9);
        Mountain.hanging(s, 2, 9, 10, "south", 5);
        Mountain.hanging(s, 10, 9, 10, "south", 5);
        Mountain.window(s, 1, 2, 7, "west", 2, 2);
        Mountain.window(s, 11, 2, 5, "east", 2, 2);
        Mountain.window(s, 1, 7, 3, "west", 2, 2);
        Mountain.window(s, 1, 7, 7, "west", 2, 2);
        Mountain.window(s, 11, 7, 4, "east", 2, 2);
        Mountain.window(s, 11, 7, 6, "east", 2, 2);
        Mountain.window(s, 4, 7, 1, "north", 2, 2);
        Mountain.window(s, 8, 7, 1, "north", 2, 2);
        Mountain.window(s, 6, 2, 1, "north", 2, 2);
        Mountain.fireplace(s, 3, 1, 5, "east", 13);
        chimney(s, 2, 5, 11, 16);
        // The court: paving, a low wall with a coping, the gate, a stone of runes, a brazier.
        fill(s, 1, 0, 11, 11, 0, 13, FLOOR);
        for (int z = 11; z <= 14; z++) {
            for (int x : new int[]{0, 12}) {
                fill(s, x, 0, z, x, 1, z, WALL);
                put(s, x, 2, z, Kit.slab(TRIM_SLAB));
            }
        }
        fill(s, 0, 0, 14, 12, 1, 14, WALL);
        fill(s, 0, 2, 14, 12, 2, 14, Kit.slab(TRIM_SLAB));
        gate(s, 6, 14);
        Mountain.runeStone(s, 2, 1, 12, "east", 3);
        Mountain.brazier(s, 10, 1, 12, false);
        // The hall: the hearth, the long table with benches, the high seat, the stair, light.
        Furnish.rug(s, 4, 4, 9, 6, 1, CARPET);
        Mountain.stoneTable(s, 5, 5, 8, 5, 1, true);
        Furnish.chair(s, 9, 1, 5, "west", WOOD);
        Mountain.hanging(s, 9, 4, 5, "west", 3);
        stair(s, 4, 1, 3, "east", 5);
        Furnish.greatChandelier(s, 6, 5, 4, 1);
        put(s, 4, 3, 7, Kit.wallTorch("north"));
        put(s, 8, 3, 7, Kit.wallTorch("north"));
        // Above: four beds, the chest, a table, light.
        Mountain.bedAlcove(s, 4, 6, 7, "north", "red");
        Mountain.bedAlcove(s, 8, 6, 7, "north", "red");
        Furnish.bed(s, 4, 6, 5, "north", "red");
        Furnish.bed(s, 9, 6, 4, "north", "red");
        put(s, 9, 6, 5, Kit.topSlab(SLAB));
        put(s, 9, 7, 5, Furnish.candles(2));
        Furnish.chest(s, 4, 6, 3, "south");
        Furnish.table(s, 6, 6, 5, WOOD);
        Furnish.chair(s, 6, 6, 6, "north", WOOD);
        Furnish.chandelier(s, 6, 5, 11, 2);
        put(s, 3, 8, 5, Kit.wallTorch("east"));
        s.connect();
    }

    /**
     * The gate of a court in its front wall at row {@code z} around column {@code x}: two piers of stone two deep with dark
     * caps and small braziers on them, between them an arch three wide with rounded haunches under a lintel with a carved
     * keystone, a frieze of runes and gilt stone over it and a lantern hanging in it.
     */
    private static void gate(Structure s, int x, int z) {
        fill(s, x - 1, 0, z, x + 1, 3, z, AIR);
        for (int side = -1; side <= 1; side += 2) {
            int px = x + side * 2;
            fill(s, px, 0, z - 1, px + side, 4, z, WALL);
            fill(s, px, 5, z - 1, px + side, 5, z, DARK);
            Mountain.brazier(s, px + side, 6, z, false);
            put(s, x + side, 3, z, Kit.upside(TRIM_STAIRS, side < 0 ? "west" : "east"));
        }
        put(s, x - 1, 4, z, TRIM);
        put(s, x, 4, z, CARVED);
        put(s, x + 1, 4, z, TRIM);
        Mountain.runeBand(s, x - 1, z, x + 1, z, 5);
        put(s, x, 6, z, Kit.slab(DARK_SLAB));
        put(s, x, 2, z, AIR);
        put(s, x, 3, z, Kit.hangingLantern());
    }

    // ---------------------------------------------------------------- the smithy

    /**
     * The smithy: a hall of stone on a battered foot under a low roof of slabs whose coped gable looks at the street with a
     * rune on its apex, buttresses at its front corners; a broad deep portal of three leaves under a frieze of runes, over it
     * a sign of two banners and a lantern on a beam out of the gable; out of the ridge rises the stack of the forge, rough
     * stone with a dark smoking collar. Inside the great forge against the back wall with its copper hood and blast
     * furnaces, the anvil before it, the quenching tub, the grindstone, the smithing table, the smith's post with his chest,
     * raw stock and casks, a tie beam with lanterns. Against the east side an open shed of timber on posts with knee braces
     * under a lean-to of slabs, over a yard with a heap of ore, logs and a chopping block; a stone of runes and a brazier at
     * the door.
     */
    static Structure smithy() {
        Structure s = new Structure(15, 18, 13);
        Mountain.Plan hall = Mountain.Plan.box(1, 1, 11, 9);
        Mountain.batter(s, hall, 0, 1, 2);
        Mountain.walls(s, hall, 0, 7, 2, WALL);
        Mountain.plate(s, hall.grow(-2), 0, FLOOR);
        Mountain.ring(s, hall, 2, DARK);
        Mountain.slabRoof(s, 0, 1, 12, 9, 7, Mountain.GABLE_Z, WALL);
        Mountain.forge(s, 6, 1, 5, "south", 9, true);
        chimney(s, 6, 3, 10, 15);
        // The portal, the frieze, buttresses, windows.
        Mountain.archDoor(s, 6, 1, 8, "south", 3, 2);
        Mountain.runeBand(s, 3, 9, 9, 9, 6);
        put(s, 6, 9, 10, Kit.log(BEAM, "z"));
        put(s, 6, 9, 11, Kit.log(BEAM, "z"));
        put(s, 5, 9, 11, Kit.wallBanner("red", "west"));
        put(s, 7, 9, 11, Kit.wallBanner("red", "east"));
        put(s, 6, 8, 11, Kit.hangingLantern());
        buttress(s, 1, 9, "south", 6);
        buttress(s, 11, 9, "south", 6);
        Mountain.window(s, 1, 3, 4, "west", 2, 2);
        Mountain.window(s, 1, 3, 7, "west", 2, 2);
        Mountain.window(s, 4, 3, 1, "north", 2, 2);
        Mountain.window(s, 8, 3, 1, "north", 2, 2);
        Mountain.window(s, 11, 3, 4, "east", 2, 2);
        Mountain.window(s, 11, 3, 7, "east", 2, 2);
        // The shed against the east side.
        fill(s, 12, 0, 1, 14, 0, 10, FLOOR);
        fill(s, 12, 1, 1, 14, 6, 10, AIR);
        for (int z : new int[]{1, 5, 10}) {
            fill(s, 14, 1, z, 14, 3, z, Kit.log(BEAM, "y"));
        }
        fill(s, 14, 4, 1, 14, 4, 10, Kit.log(BEAM, "z"));
        leanTo(s, 1, 10, 12, 14, 6, false);
        put(s, 14, 3, 2, Kit.upside(STAIRS, "north"));
        put(s, 14, 3, 4, Kit.upside(STAIRS, "south"));
        put(s, 14, 3, 6, Kit.upside(STAIRS, "north"));
        put(s, 14, 3, 9, Kit.upside(STAIRS, "south"));
        Mountain.stockPile(s, 13, 2, 14, 3, 1);
        fill(s, 14, 1, 7, 14, 1, 9, Kit.log(LOG, "z"));
        put(s, 14, 2, 8, Kit.log(LOG, "z"));
        put(s, 12, 1, 7, Kit.log("minecraft:stripped_spruce_log", "y"));
        put(s, 12, 5, 5, Kit.hangingLantern());
        // Before the door.
        Mountain.runeStone(s, 13, 0, 11, "south", 2);
        Mountain.brazier(s, 1, 0, 12, false);
        // Inside: the anvil, the tub, the grindstone, the smithing table, the post and its chest, stock, casks, light.
        put(s, 6, 1, 6, "minecraft:anvil[facing=west]");
        put(s, 3, 1, 5, "minecraft:water_cauldron[level=3]");
        put(s, 3, 1, 7, "minecraft:grindstone[face=floor,facing=east]");
        put(s, 9, 1, 3, "minecraft:smithing_table");
        put(s, 9, 1, 7, "kingdomworld:work_post");
        Furnish.chest(s, 9, 1, 6, "west");
        Mountain.stockPile(s, 3, 3, 3, 4, 1);
        Mountain.casks(s, 9, 4, 9, 5, 1, "west");
        for (int x = 3; x <= 9; x++) {
            put(s, x, 7, 6, Kit.log(BEAM, "x"));
        }
        put(s, 4, 6, 6, Kit.hangingLantern());
        put(s, 8, 6, 6, Kit.hangingLantern());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the tavern

    /**
     * The tavern: a long hall of two storeys on a battered foot under a low roof of slabs between coped gables, its eaves on
     * timber brackets, a great stack on the west gable, and at its east end a square tower of three storeys with dark
     * quoins, a door of its own, a crenellated top on corbels and a great brazier that shows the tavern from afar. A broad
     * deep portal of three leaves under a frieze of runes, a buttress at the corner, a sign of two banners on a beam; before
     * it a low terrace with a parapet, tables with benches and a flight down to the street. In the taproom the great hearth
     * between hangings, two stone tables with benches under great chandeliers, the bar with the innkeeper's post, his chest,
     * two barrels and casks over them; a stair up to two rooms for guests with two beds each, a fifth bed in the tower.
     */
    static Structure tavern() {
        Structure s = new Structure(21, 21, 17);
        Mountain.Plan hall = Mountain.Plan.box(1, 1, 13, 11);
        Mountain.batter(s, hall, 0, 1, 2);
        Mountain.walls(s, hall, 0, 11, 2, WALL);
        Mountain.Plan in = hall.grow(-2);
        Mountain.plate(s, in, 0, FLOOR);
        Mountain.plate(s, in, 6, PLANKS);
        Mountain.ring(s, hall, 2, DARK);
        Mountain.ring(s, hall, 6, DARK);
        Mountain.slabRoof(s, 1, 0, 12, 12, 11, Mountain.GABLE_X, WALL);
        Mountain.brackets(s, 2, 11, 10, 11, 10, "south", 1, 2);
        Mountain.brackets(s, 2, 1, 10, 1, 10, "north", 1, 2);
        // The tower at the east end, open to the hall on both floors.
        fill(s, 13, 0, 4, 17, 16, 8, AIR);
        Mountain.tower(s, 15, 0, 6, 7, 16, Mountain.SQUARE, Mountain.FLAT_TOP, "south", 6);
        Mountain.brazier(s, 15, 17, 6, true);
        fill(s, 11, 0, 3, 11, 0, 9, FLOOR);
        fill(s, 11, 1, 3, 11, 1, 9, AIR);
        put(s, 11, 7, 6, AIR);
        fill(s, 12, 1, 6, 12, 2, 6, AIR);
        fill(s, 12, 7, 4, 12, 8, 4, AIR);
        // The terrace before it.
        Mountain.terrace(s, 2, 13, 12, 15, 0, 1, "south", 3);
        // The portal, the buttress, the frieze, windows, the sign, the hearth and its stack.
        Mountain.archDoor(s, 7, 1, 10, "south", 3, 2);
        buttress(s, 1, 11, "south", 9);
        Mountain.runeBand(s, 2, 11, 12, 11, 6);
        Mountain.window(s, 4, 8, 11, "south", 2, 2);
        Mountain.window(s, 10, 8, 11, "south", 2, 2);
        Mountain.window(s, 1, 8, 9, "west", 2, 2);
        Mountain.window(s, 1, 3, 9, "west", 2, 2);
        Mountain.window(s, 5, 8, 1, "north", 2, 2);
        Mountain.window(s, 9, 8, 1, "north", 2, 2);
        put(s, 3, 8, 12, Kit.log(BEAM, "z"));
        put(s, 3, 8, 13, Kit.log(BEAM, "z"));
        put(s, 2, 8, 13, Kit.wallBanner("red", "west"));
        put(s, 4, 8, 13, Kit.wallBanner("red", "east"));
        Mountain.fireplace(s, 3, 1, 6, "east", 14);
        chimney(s, 2, 6, 14, 18);
        // On the terrace: tables with benches.
        Mountain.stoneTable(s, 4, 13, 4, 14, 1, true);
        Mountain.stoneTable(s, 10, 13, 10, 14, 1, true);
        // The taproom: the stair, the bar with the post, the chest, two barrels and casks over them.
        stair(s, 3, 1, 3, "east", 6);
        fill(s, 9, 1, 5, 11, 1, 5, Kit.topSlab(TRIM_SLAB));
        put(s, 9, 2, 5, Furnish.candles(2));
        put(s, 11, 2, 5, "minecraft:flower_pot");
        put(s, 10, 1, 3, "kingdomworld:work_post");
        Furnish.chest(s, 11, 1, 3, "south");
        put(s, 9, 1, 3, Kit.barrel());
        put(s, 11, 1, 4, Kit.barrel());
        Mountain.casks(s, 9, 3, 10, 3, 2, "south");
        put(s, 10, 5, 4, Kit.hangingLantern());
        // The tables, the chandeliers, hangings.
        Mountain.stoneTable(s, 4, 7, 7, 7, 1, true);
        Mountain.stoneTable(s, 10, 7, 10, 9, 1, true);
        Furnish.rug(s, 4, 9, 8, 9, 1, CARPET);
        Furnish.greatChandelier(s, 5, 7, 5, 1);
        Furnish.greatChandelier(s, 10, 8, 5, 1);
        Mountain.hanging(s, 3, 5, 8, "east", 3);
        Mountain.hanging(s, 3, 5, 4, "east", 3);
        // The upper storey: a passage along the back, two rooms for guests, a bed in the tower.
        partition(s, 3, 5, 11, 5, 7, 10);
        partition(s, 7, 6, 7, 9, 7, 10);
        s.door(5, 7, 5, Mountain.DOOR, "north", "left");
        s.door(9, 7, 5, Mountain.DOOR, "north", "left");
        Mountain.bedAlcove(s, 4, 7, 9, "north", "red");
        Furnish.bed(s, 6, 7, 8, "south", "red");
        Mountain.bedAlcove(s, 10, 7, 9, "north", "red");
        Furnish.bed(s, 8, 7, 8, "south", "red");
        Furnish.chandelier(s, 4, 7, 12, 2);
        Furnish.chandelier(s, 10, 7, 12, 2);
        Furnish.chandelier(s, 9, 4, 11, 2);
        Mountain.bedAlcove(s, 15, 7, 8, "north", "red");
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the town wall and its gate

    /** The layer one walks at on the top of the gate. */
    private static final int GATE_WALK = 16;

    /**
     * A piece of the town wall with its gate: a massive block of stone through which the gate tunnel runs three wide, and on
     * its field side the deep portal of receding arches with its three leaves under the great carved face of the forefather of
     * the hold, crowned with gold and with eyes of embers; over it the walk behind a heavy parapet with dark caps and a great
     * brazier, hangings of cloth beside the face. On both sides two low thick round towers on battered feet, with doors from
     * the town, ladders, a storey with lanterns and a crenellated top; from each top a shaft with a ladder inside the block
     * climbs to its walk. In the tunnel lanterns and the raised grille of a portcullis, an arch on the town side; braziers
     * before the gate.
     */
    static Structure wallGate() {
        Structure s = new Structure(23, 19, 11);
        Mountain.tower(s, 4, 0, 5, 7, 9, Mountain.ROUND, Mountain.FLAT_TOP, "north", 4);
        Mountain.tower(s, 18, 0, 5, 7, 9, Mountain.ROUND, Mountain.FLAT_TOP, "north", 4);
        // The gate block.
        Mountain.Plan block = Mountain.Plan.box(7, 1, 15, 8);
        Mountain.batter(s, Mountain.Plan.box(7, 1, 15, 1), 0, 1, 2);
        for (int y = 0; y < GATE_WALK - 1; y++) {
            Mountain.plate(s, block, y, WALL);
        }
        Mountain.ring(s, block, 6, DARK);
        Mountain.ring(s, block, GATE_WALK - 3, DARK);
        Mountain.corbels(s, block, GATE_WALK - 2, 1);
        Mountain.plate(s, block.grow(1), GATE_WALK - 1, FLOOR);
        Mountain.parapet(s, block.grow(1), GATE_WALK);
        Mountain.brazier(s, 11, GATE_WALK, 4, true);
        // The shafts from the tops of the towers up to the walk.
        for (int y = 10; y < GATE_WALK; y++) {
            put(s, 8, y, 5, Kit.ladder("west"));
            put(s, 14, y, 5, Kit.ladder("east"));
        }
        fill(s, 7, 10, 5, 7, 11, 5, AIR);
        fill(s, 15, 10, 5, 15, 11, 5, AIR);
        // The tunnel and its portal.
        fill(s, 10, 1, 0, 12, 4, 8, AIR);
        fill(s, 10, 0, 0, 12, 0, 8, FLOOR);
        put(s, 10, 4, 0, Kit.upside(TRIM_STAIRS, "east"));
        put(s, 12, 4, 0, Kit.upside(TRIM_STAIRS, "west"));
        put(s, 11, 5, 0, CARVED);
        fill(s, 10, 4, 3, 12, 4, 3, Mountain.IRON);
        put(s, 11, 4, 2, Kit.hangingLantern());
        put(s, 11, 4, 6, Kit.hangingLantern());
        put(s, 9, 3, 0, Kit.wallTorch("north"));
        put(s, 13, 3, 0, Kit.wallTorch("north"));
        Mountain.archDoor(s, 11, 1, 8, "south", 3, 2);
        Mountain.face(s, 11, 6, 8, "south", 0);
        Mountain.hanging(s, 7, 13, 9, "south", 6);
        Mountain.hanging(s, 15, 13, 9, "south", 6);
        Mountain.brazier(s, 6, 0, 10, false);
        Mountain.brazier(s, 16, 0, 10, false);
        s.connect().trimSky(3);
        return s;
    }
}
