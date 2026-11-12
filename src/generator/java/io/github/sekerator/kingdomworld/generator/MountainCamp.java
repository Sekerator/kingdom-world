package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The field camp of an army of the style "mountain" (see {@link Mountain}): the four buildings an army raises where it
 * halts, drawn so that each takes the place of the plain one of a camp with the same footprint, entrance, role and blocks
 * that count. The people of the hold build even a camp of stone: a long barracks hall on a battered foot with a coped gable
 * to the front, a training ground behind a dry stone wall with a backstop for the archers and a standing stone of runes, a
 * low store under one long roof of stone slabs whose front slope runs on over a loading porch behind an arcade of heavy
 * piers, and a slim watch tower whose platform is sheltered by a low pyramid on stone posts. Dark bands, deep doors, amber
 * windows, the glow of braziers and the red of war banners mark them as one camp.
 */
final class MountainCamp {
    private static final String WALL = Mountain.WALL;
    private static final String ROUGH = Mountain.ROUGH;
    private static final String ROUGH_SLAB = Mountain.ROUGH_SLAB;
    private static final String TRIM = Mountain.TRIM;
    private static final String TRIM_STAIRS = Mountain.TRIM_STAIRS;
    private static final String TRIM_SLAB = Mountain.TRIM_SLAB;
    private static final String CARVED = Mountain.CARVED;
    private static final String FLOOR = Mountain.FLOOR;
    private static final String DARK = Mountain.DARK;
    private static final String DARK_WALL = Mountain.DARK_WALL;
    private static final String BEAM = Mountain.BEAM;
    private static final String PLANKS = Mountain.PLANKS;
    private static final String FENCE = Mountain.FENCE;
    private static final String WOOD = Mountain.WOOD;
    private static final String CARPET = Mountain.CARPET;
    private static final Kit.Roof ROOF = Mountain.ROOF;
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String COARSE = "minecraft:coarse_dirt";
    private static final String GRAVEL = "minecraft:gravel";
    private static final String FIRE = "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]";
    private static final String AIR = Structure.AIR;

    private MountainCamp() {
    }

    /**
     * The four buildings of the camp: the barracks, the training ground, the store and the watch tower.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("mountain_camp_barracks", "defense", "barracks", "kingdomworld:barracks", 6, 0, 10,
                barracks(), "barracks", 1, "").params("\"bunks\": 12").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("mountain_camp_yard", "defense", "yard", "minecraft:target", 6, 1, 10, yard(),
                "none", 1, "").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("mountain_camp_store", "farming", "warehouse", "kingdomworld:storehouse", 6, 1, 10,
                store(), "warehouse", 1, "").params("\"containers\": 12").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("mountain_camp_tower", "defense", "tower", "minecraft:oak_fence", 3, 0, 6, tower(),
                "tower", 1, "").at("door", 3, 1, 6));
        return list;
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

    /**
     * A small brazier: a post of dark stone on layer {@code y} with a fire burning on it.
     */
    private static void brazier(Structure s, int x, int y, int z) {
        put(s, x, y, z, DARK_WALL);
        put(s, x, y + 1, z, FIRE);
    }

    /**
     * A war banner of the kingdom on a pole: a post of timber {@code height} high from layer {@code y} and the banner on it
     * looking south.
     */
    private static void bannerPole(Structure s, int x, int y, int z, int height) {
        for (int i = 0; i < height; i++) {
            put(s, x, y + i, z, FENCE);
        }
        put(s, x, y + height, z, Kit.banner("red", 0));
    }

    /**
     * A lean-to roof of stone slabs whose rows run along z from {@code z1} to {@code z2}: the high row at column
     * {@code high} is a full block at layer {@code y}, every row further toward {@code low} half a block lower, the rows of
     * slabs closed beneath by an upper slab so that the roof is whole seen from below.
     */
    private static void leanTo(Structure s, int z1, int z2, int high, int low, int y) {
        int dir = low > high ? 1 : -1;
        int n = Math.abs(low - high);
        for (int i = 0; i <= n; i++) {
            int x = high + dir * i;
            int layer = y - i / 2;
            for (int z = z1; z <= z2; z++) {
                if (i % 2 == 0) {
                    put(s, x, layer, z, ROOF.block());
                } else {
                    put(s, x, layer, z, Kit.slab(ROOF.slab()));
                    if (s.inside(x, layer - 1, z) && !s.solid(x, layer - 1, z)) {
                        s.set(x, layer - 1, z, Kit.topSlab(ROOF.slab()));
                    }
                }
            }
        }
    }

    private static int count(Structure s, String... ids) {
        int n = 0;
        for (int y = 0; y < s.sizeY; y++) {
            for (int z = 0; z < s.sizeZ; z++) {
                for (int x = 0; x < s.sizeX; x++) {
                    String id = States.id(s.get(x, y, z));
                    for (String wanted : ids) {
                        if (id.equals(wanted)) {
                            n++;
                        }
                    }
                }
            }
        }
        return n;
    }

    private static int beds(Structure s) {
        int n = 0;
        for (int y = 0; y < s.sizeY; y++) {
            for (int z = 0; z < s.sizeZ; z++) {
                for (int x = 0; x < s.sizeX; x++) {
                    String state = s.get(x, y, z);
                    if (state.contains("_bed[") && state.contains("part=head")) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- the barracks

    /**
     * The barracks of the camp, 13 by 11: a long hall of stone on a battered foot of rough stone with two storeys, its coped
     * gable with a rune on the apex looking to the front over a deep arched door between two braziers and two long red
     * hangings, war banners on poles at its front corners; dark bands, small windows of amber glass, the heads of the floor
     * beams and a row of red shields on its sides, the stack of its hearth standing against the back gable. Inside on each
     * storey six bunks along the side walls, their heads to the wall; below the barracks block by the hearth, the racks of
     * the weapons, a long stone table with benches, the chest and the barrel of the company; a ladder up to the dormitory
     * under the roof, lit by a lantern on a chain.
     */
    static Structure barracks() {
        Structure s = new Structure(13, 14, 11);
        Mountain.Plan hall = Mountain.Plan.box(1, 1, 11, 9);
        Mountain.batter(s, hall, 0, 1, 2);
        Mountain.walls(s, hall, 0, 7, 1, WALL);
        Mountain.plate(s, hall.grow(-1), 0, FLOOR);
        Mountain.ring(s, hall, 2, DARK);
        Mountain.ring(s, hall, 7, DARK);
        // The floor of the dormitory and the heads of its beams sticking out of the side walls.
        fill(s, 2, 4, 2, 10, 4, 8, PLANKS);
        for (int z = 2; z <= 8; z += 2) {
            put(s, 0, 4, z, Kit.log(BEAM, "x"));
            put(s, 12, 4, z, Kit.log(BEAM, "x"));
        }
        Mountain.slabRoof(s, 0, 1, 12, 9, 8, Mountain.GABLE_Z, WALL);
        // The hearth at the back wall; its flue rises in a stack of rough stone standing against the back gable.
        Mountain.fireplace(s, 4, 1, 2, "south", 11);
        for (int y = 2; y <= 9; y++) {
            put(s, 4, y, 1, y == 2 || y == 7 ? DARK : WALL);
        }
        put(s, 4, 10, 1, TRIM);
        put(s, 4, 11, 1, Kit.slab(TRIM_SLAB));
        put(s, 4, 12, 1, AIR);
        Mountain.stack(s, 4, 0, 0, 12, 1);
        // Red shields of the company hang on the side walls between the windows of the dormitory.
        for (int z : new int[]{3, 5, 7}) {
            put(s, 0, 6, z, Kit.wallBanner("red", "west"));
            put(s, 12, 6, z, Kit.wallBanner("red", "east"));
        }
        // Windows: two on each side of each storey, two below and one high in the front gable, two in the back.
        for (int z : new int[]{4, 6}) {
            for (int y : new int[]{2, 6}) {
                Mountain.window(s, 1, y, z, "west", 1, 1);
                Mountain.window(s, 11, y, z, "east", 1, 1);
            }
        }
        Mountain.window(s, 2, 2, 9, "south", 1, 1);
        Mountain.window(s, 10, 2, 9, "south", 1, 1);
        Mountain.window(s, 6, 6, 9, "south", 2, 1);
        Mountain.window(s, 6, 2, 1, "north", 1, 1);
        Mountain.window(s, 8, 6, 1, "north", 1, 1);
        // The deep door with steps up to it, braziers and hangings beside it.
        Mountain.archDoor(s, 6, 1, 9, "south", 1, 1);
        for (int x = 5; x <= 7; x++) {
            put(s, x, 0, 10, Kit.stairs(TRIM_STAIRS, "north"));
        }
        brazier(s, 3, 2, 10);
        brazier(s, 9, 2, 10);
        Mountain.hanging(s, 3, 7, 10, "south", 3);
        Mountain.hanging(s, 9, 7, 10, "south", 3);
        bannerPole(s, 0, 2, 10, 3);
        bannerPole(s, 12, 2, 10, 3);
        // The ground floor: six bunks, the barracks block, racks, a table, the stores of the company.
        for (int z : new int[]{3, 5, 7}) {
            Furnish.bed(s, 3, 1, z, "west", "red");
            Furnish.bed(s, 9, 1, z, "east", "red");
        }
        put(s, 6, 1, 2, "kingdomworld:barracks[facing=south]");
        put(s, 2, 1, 2, Kit.barrel());
        Furnish.chest(s, 7, 1, 2, "south");
        for (int y = 1; y <= 5; y++) {
            put(s, 8, y, 2, Kit.ladder("south"));
        }
        Furnish.crate(s, 9, 1, 2, WOOD);
        Furnish.sack(s, 10, 1, 2, false);
        Furnish.weaponRack(s, 10, 1, 4, "west", WOOD);
        Furnish.weaponRack(s, 10, 1, 6, "west", WOOD);
        Furnish.weaponRack(s, 9, 1, 8, "north", WOOD);
        Furnish.weaponRack(s, 10, 1, 8, "north", WOOD);
        Furnish.crate(s, 2, 1, 4, WOOD);
        put(s, 2, 2, 4, Furnish.candles(2));
        Furnish.sack(s, 2, 1, 6, true);
        Furnish.table(s, 2, 1, 8, WOOD);
        Furnish.chair(s, 3, 1, 8, "west", WOOD);
        Mountain.stoneTable(s, 6, 4, 6, 6, 1, true);
        Furnish.rug(s, 5, 7, 7, 8, 1, CARPET);
        put(s, 4, 3, 4, Kit.hangingLantern());
        put(s, 8, 3, 6, Kit.hangingLantern());
        put(s, 4, 3, 7, Kit.hangingLantern());
        // The dormitory under the roof: six more bunks, gear between them, banners on the back wall.
        for (int z : new int[]{3, 5, 7}) {
            Furnish.bed(s, 3, 5, z, "west", "red");
            Furnish.bed(s, 9, 5, z, "east", "red");
        }
        Furnish.crate(s, 2, 5, 2, WOOD);
        put(s, 3, 6, 2, Kit.wallBanner("red", "south"));
        put(s, 5, 6, 2, Kit.wallBanner("red", "south"));
        Furnish.crate(s, 2, 5, 4, WOOD);
        put(s, 2, 6, 4, Furnish.candles(1));
        Furnish.sack(s, 2, 5, 6, false);
        Furnish.crate(s, 10, 5, 4, WOOD);
        Furnish.sack(s, 10, 5, 6, true);
        Furnish.crate(s, 9, 5, 2, WOOD);
        Furnish.sack(s, 10, 5, 2, false);
        Furnish.table(s, 2, 5, 8, WOOD);
        Furnish.chair(s, 3, 5, 8, "west", WOOD);
        Furnish.crate(s, 10, 5, 8, WOOD);
        put(s, 10, 6, 8, Furnish.candles(3));
        Furnish.rug(s, 6, 3, 6, 8, 5, CARPET);
        Furnish.chandelier(s, 6, 5, 9, 1);
        put(s, 3, 8, 5, Kit.hangingLantern());
        put(s, 9, 8, 5, Kit.hangingLantern());
        s.connect();
        if (beds(s) != 12) {
            throw new IllegalStateException("The camp barracks has " + beds(s) + " bunks instead of 12");
        }
        return s;
    }

    // ---------------------------------------------------------------- the training ground

    /**
     * The training ground of the camp, 13 by 11, level with the ground: a floor of trodden earth with a ring of stone flags
     * for sparring and lanes of gravel, inside a low dry stone wall with a timber rail on it between stone posts that carry
     * lanterns; at the back a high backstop wall with a coping, four archery butts of straw with targets before it and a
     * standing stone of runes in their middle; two straw dummies with red shields on the sides of the ring; in the front
     * west corner the weapons (racks, barrels, an anvil) under a shed of stone slabs, red shields on the wall behind them;
     * in the front east corner a bench, a water trough and lifting stones; the gap of the entrance between two gate posts
     * with war banners and braziers.
     */
    static Structure yard() {
        Structure s = new Structure(13, 7, 11);
        // The floor: trodden earth, a lane of gravel from the gate to the butts and along them, a ring of flags.
        fill(s, 0, 0, 0, 12, 0, 10, COARSE);
        fill(s, 5, 0, 8, 7, 0, 10, GRAVEL);
        fill(s, 1, 0, 2, 11, 0, 2, GRAVEL);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (Kit.onRim(dx, dz, 5)) {
                    put(s, 6 + dx, 0, 5 + dz, TRIM);
                }
            }
        }
        put(s, 6, 0, 5, Mountain.RUNE);
        // The backstop: a wall of stone three high with a coping, corner posts with lanterns.
        fill(s, 0, 0, 0, 12, 0, 0, ROUGH);
        fill(s, 0, 1, 0, 12, 1, 0, ROUGH);
        fill(s, 0, 2, 0, 12, 3, 0, WALL);
        fill(s, 0, 4, 0, 12, 4, 0, Kit.slab(TRIM_SLAB));
        Mountain.runeBand(s, 2, 0, 10, 0, 3);
        // The low dry stone wall round the sides and the front with a rail of timber on it.
        for (int z = 1; z <= 10; z++) {
            for (int x : new int[]{0, 12}) {
                put(s, x, 0, z, ROUGH);
                put(s, x, 1, z, ROUGH);
                put(s, x, 2, z, FENCE);
            }
        }
        for (int x = 1; x <= 11; x++) {
            if (x >= 5 && x <= 7) {
                continue;
            }
            put(s, x, 0, 10, ROUGH);
            put(s, x, 1, 10, ROUGH);
            put(s, x, 2, 10, FENCE);
        }
        // Posts of stone with lanterns: the back corners, the middle of the sides, the front corners.
        for (int[] p : new int[][]{{0, 0}, {12, 0}}) {
            put(s, p[0], 4, p[1], WALL);
            put(s, p[0], 5, p[1], Kit.lantern());
        }
        for (int[] p : new int[][]{{0, 5}, {12, 5}, {0, 10}, {12, 10}}) {
            put(s, p[0], 2, p[1], WALL);
            put(s, p[0], 3, p[1], DARK);
            put(s, p[0], 4, p[1], Kit.lantern());
        }
        // The gate posts with braziers and war banners.
        for (int x : new int[]{4, 8}) {
            put(s, x, 2, 10, WALL);
            put(s, x, 3, 10, DARK);
            bannerPole(s, x, 4, 10, 1);
        }
        brazier(s, 4, 1, 9);
        brazier(s, 8, 1, 9);
        // The butts and the stone of runes before the backstop.
        for (int x : new int[]{2, 4, 8, 10}) {
            put(s, x, 1, 1, HAY);
            put(s, x, 2, 1, "minecraft:target[power=0]");
        }
        Mountain.runeStone(s, 6, 1, 1, "south", 3);
        fill(s, 1, 1, 1, 1, 2, 1, HAY);
        put(s, 11, 1, 1, HAY);
        // Two dummies of straw with arms, a helm and a red shield.
        for (int x : new int[]{3, 9}) {
            put(s, x, 1, 5, FENCE);
            put(s, x, 2, 5, HAY);
            put(s, x, 3, 5, "minecraft:carved_pumpkin[facing=south]");
            put(s, x - 1, 2, 5, FENCE);
            put(s, x + 1, 2, 5, FENCE);
            put(s, x, 2, 6, Kit.wallBanner("red", "south"));
        }
        // The weapons in the front west corner under a shed of stone slabs leaning on a higher piece of the wall, red
        // shields hung on that wall.
        fill(s, 0, 2, 7, 0, 4, 9, WALL);
        put(s, 0, 2, 8, DARK);
        for (int z : new int[]{7, 9}) {
            fill(s, 3, 1, z, 3, 2, z, Kit.log(BEAM, "y"));
            put(s, 1, 3, z, Kit.wallBanner("red", "east"));
        }
        leanTo(s, 7, 9, 0, 3, 5);
        put(s, 2, 3, 8, Kit.hangingLantern());
        Furnish.weaponRack(s, 1, 1, 7, "east", WOOD);
        Furnish.weaponRack(s, 1, 1, 9, "east", WOOD);
        put(s, 1, 1, 8, Kit.barrel());
        put(s, 1, 2, 8, Kit.barrel());
        put(s, 2, 1, 9, "minecraft:anvil[facing=east]");
        // A bench and a water trough in the front east corner.
        Furnish.bench(s, 9, 9, 10, 9, 1, "north", WOOD);
        put(s, 11, 1, 9, "minecraft:water_cauldron[level=3]");
        put(s, 11, 1, 7, ROUGH);
        put(s, 11, 1, 8, Kit.slab(ROUGH_SLAB));
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the store

    /**
     * The store of the camp, 13 by 11, its floor level with the ground: a low long house of stone on a battered foot under
     * one roof of stone slabs whose ridge runs along the front, its gables at the sides coped and crowned with runes and
     * hung with red cloth; the front slope runs on over a loading porch behind an arcade of heavy piers on dark feet with
     * war banners on them, where hay, sacks, crates and casks wait, lanterns on dark posts before it. A wide door of three
     * leaves under an iron grille and a carved lintel. Inside the stores of the army: barrels and chests along the walls,
     * two storehouse blocks among them, hay and sacks, lanterns hanging from the roof.
     */
    static Structure store() {
        Structure s = new Structure(13, 11, 11);
        Mountain.Plan body = Mountain.Plan.box(1, 1, 11, 7);
        fill(s, 0, 0, 0, 12, 0, 8, ROUGH);
        Mountain.batter(s, body, 1, 1, 2);
        Mountain.walls(s, body, 1, 6, 1, WALL);
        Mountain.plate(s, body.grow(-1), 0, FLOOR);
        Mountain.ring(s, body, 3, DARK);
        // The porch: the batter gives way to a paving of flags.
        fill(s, 0, 1, 8, 12, 2, 10, AIR);
        fill(s, 1, 0, 8, 11, 0, 10, FLOOR);
        Mountain.slabRoof(s, 1, 0, 11, 10, 6, Mountain.GABLE_X, WALL);
        // An arcade of heavy piers on dark feet carries the front slope over the porch: two arches on the sides, a wider
        // one in the middle before the door under a carved keystone, a course of dressed stone over them.
        for (int x = 1; x <= 11; x++) {
            put(s, x, 5, 9, TRIM);
        }
        for (int x : new int[]{1, 4, 8, 11}) {
            put(s, x, 1, 9, DARK);
            fill(s, x, 2, 9, x, 4, 9, WALL);
        }
        for (int x : new int[]{2, 5, 9}) {
            put(s, x, 4, 9, Kit.upside(TRIM_STAIRS, "west"));
        }
        for (int x : new int[]{3, 7, 10}) {
            put(s, x, 4, 9, Kit.upside(TRIM_STAIRS, "east"));
        }
        put(s, 6, 5, 9, CARVED);
        for (int x : new int[]{1, 11}) {
            put(s, x, 1, 8, DARK);
            fill(s, x, 2, 8, x, 5, 8, WALL);
        }
        for (int x : new int[]{4, 8}) {
            put(s, x, 4, 10, Kit.wallBanner("red", "south"));
        }
        for (int x : new int[]{1, 11}) {
            put(s, x, 1, 10, DARK_WALL);
            put(s, x, 2, 10, Kit.lantern());
        }
        // The wide door under its lintel.
        Mountain.archDoor(s, 6, 1, 7, "south", 3, 0);
        // Windows in the gables and at the back.
        Mountain.window(s, 1, 3, 4, "west", 1, 1);
        Mountain.window(s, 11, 3, 4, "east", 1, 1);
        Mountain.hanging(s, 0, 5, 6, "west", 3);
        Mountain.hanging(s, 12, 5, 6, "east", 3);
        Mountain.window(s, 3, 3, 1, "north", 1, 1);
        Mountain.window(s, 9, 3, 1, "north", 1, 1);
        // Inside: barrels and chests along the walls, the two storehouse blocks, hay and sacks.
        for (int x : new int[]{2, 3, 9, 10}) {
            put(s, x, 1, 2, Kit.barrel());
            put(s, x, 2, 2, Kit.barrel());
        }
        put(s, 4, 1, 2, "kingdomworld:storehouse");
        put(s, 8, 1, 2, "kingdomworld:storehouse");
        for (int x = 5; x <= 7; x++) {
            Furnish.chest(s, x, 1, 2, "south");
        }
        Furnish.chest(s, 2, 1, 3, "east");
        Furnish.chest(s, 10, 1, 3, "west");
        put(s, 2, 1, 4, Kit.barrel());
        put(s, 2, 2, 4, Kit.barrel());
        put(s, 10, 1, 4, Kit.barrel());
        put(s, 10, 2, 4, Kit.barrel());
        put(s, 2, 1, 5, HAY);
        put(s, 2, 2, 5, HAY);
        Furnish.sack(s, 2, 1, 6, false);
        put(s, 10, 1, 5, HAY);
        Furnish.sack(s, 10, 1, 6, true);
        Furnish.sack(s, 10, 2, 6, false);
        Furnish.crate(s, 6, 1, 4, WOOD);
        Furnish.sack(s, 5, 1, 4, true);
        Furnish.sack(s, 7, 1, 4, false);
        Furnish.chandelier(s, 4, 4, 6, 1);
        Furnish.chandelier(s, 8, 4, 6, 1);
        // On the porch: hay at the west end, sacks, crates and casks at the east end, lanterns under the roof.
        fill(s, 2, 1, 8, 3, 1, 9, HAY);
        put(s, 2, 2, 8, HAY);
        put(s, 3, 2, 8, HAY);
        put(s, 2, 2, 9, HAY);
        Furnish.sack(s, 4, 1, 8, true);
        Mountain.casks(s, 9, 8, 10, 8, 1, "south");
        Furnish.crate(s, 8, 1, 8, WOOD);
        Furnish.sack(s, 9, 1, 10, false);
        Furnish.crate(s, 10, 1, 10, WOOD);
        Furnish.sack(s, 10, 2, 10, true);
        Furnish.chandelier(s, 6, 8, 6, 1);
        keepGround(s);
        s.connect();
        if (count(s, "minecraft:chest", "minecraft:barrel") < 12) {
            throw new IllegalStateException("The camp store holds fewer than 12 containers");
        }
        return s;
    }

    /**
     * Leaves out every position of the ground layer that the building does not pave, so that the ground there stays.
     */
    private static void keepGround(Structure s) {
        for (int z = 0; z < s.sizeZ; z++) {
            for (int x = 0; x < s.sizeX; x++) {
                if (!s.solid(x, 0, z)) {
                    s.skip(x, 0, z);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the watch tower

    /**
     * The watch tower of the camp, 7 by 7: a slim square shaft of stone on a battered foot of rough stone with dark bands and
     * dark corners, a deep arched door to the front and small windows of amber glass; a ladder up the back wall through a storey
     * of planks to the platform, which juts out on a ring of corbels and carries a heavy crenellated parapet with dark caps;
     * the merlons at its corners rise as stone posts that carry a low pyramid of the spires with a gilt point, so that the
     * watch looks out under a roof; a lantern hangs under it and a long red hanging marks the front.
     */
    static Structure tower() {
        Structure s = new Structure(7, 20, 7);
        Mountain.Plan shaft = Mountain.Plan.of(3, 3, 5, Mountain.SQUARE);
        int deck = 10;
        int storey = 5;
        Mountain.batter(s, shaft, 0, 1, 2);
        Mountain.walls(s, shaft, 0, deck - 1, 1, WALL);
        Mountain.plate(s, shaft.grow(-1), 0, FLOOR);
        Mountain.ring(s, shaft, 2, DARK);
        Mountain.ring(s, shaft, deck - 2, DARK);
        for (int[] c : new int[][]{{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
            fill(s, c[0], 3, c[1], c[0], deck - 3, c[1], DARK);
        }
        // A storey of planks; the ladder runs up the back wall to a hatch in the platform.
        Mountain.plate(s, shaft.grow(-1), storey, PLANKS);
        for (int y = 1; y <= deck; y++) {
            put(s, 3, y, 2, Kit.ladder("south"));
        }
        for (String side : new String[]{"west", "east"}) {
            int[] d = Furnish.step(side);
            Mountain.window(s, 3 + 2 * d[0], 3, 3, side, 1, 1);
            Mountain.window(s, 3 + 2 * d[0], storey + 2, 3, side, 1, 1);
        }
        // The platform on its corbels, the parapet, the posts and the pyramid over it.
        Mountain.corbels(s, shaft, deck - 1);
        Mountain.Plan top = shaft.grow(1);
        Mountain.plate(s, top, deck, FLOOR);
        put(s, 3, deck, 2, Kit.ladder("south"));
        Mountain.parapet(s, top, deck + 1);
        for (int[] c : new int[][]{{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
            fill(s, c[0], deck + 1, c[1], c[0], deck + 3, c[1], WALL);
            put(s, c[0], deck + 4, c[1], DARK);
        }
        Mountain.helm(s, top, deck + 5, Mountain.HELM);
        Furnish.chandelier(s, 3, 3, deck + 4, 1);
        // The door, a step before it, the long hanging on the front under the corbels.
        Mountain.archDoor(s, 3, 1, 5, "south", 1, 1);
        put(s, 3, 0, 6, Kit.stairs(TRIM_STAIRS, "north"));
        Mountain.hanging(s, 3, deck - 2, 6, "south", 4);
        // Inside: the gear of the watch below, a bench above, light on both storeys.
        put(s, 2, 1, 2, Kit.barrel());
        Furnish.weaponRack(s, 4, 1, 2, "south", WOOD);
        Furnish.crate(s, 2, 1, 4, WOOD);
        put(s, 4, storey - 1, 4, Kit.hangingLantern());
        Furnish.bench(s, 2, 2, 2, 4, storey + 1, "east", WOOD);
        Furnish.crate(s, 4, storey + 1, 2, WOOD);
        put(s, 4, storey + 2, 2, Furnish.candles(2));
        put(s, 4, deck - 1, 4, Kit.hangingLantern());
        s.connect();
        return s;
    }
}
