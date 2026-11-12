package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The town of the style "desert" (see {@link Desert}): its houses, from the mud hut of the poor under a canopy of palm
 * leaves to the house of a noble family with its domed hall and walled garden, its smithy, its tavern and a piece of its
 * wall with the great gate. All of them are blocks of thick sandstone or mud brick under flat roofs with parapets and
 * merlons, with few openings: arched doors with rings of red stone, small windows behind wooden lattices, glazed tiles
 * of turquoise and blue as accents; the richer houses open on a court with a fountain and palms and rise in wind towers,
 * domes and terraces. Every room is furnished for those who live or work there and lit.
 */
final class DesertTown {
    private static final String WALL = Desert.WALL;
    private static final String BASE = Desert.BASE;
    private static final String TRIM = Desert.TRIM;
    private static final String CARVED = Desert.CARVED;
    private static final String WHITEWASH = Desert.WHITEWASH;
    private static final String PAVING = Desert.PAVING;
    private static final String FLOOR = Desert.FLOOR;
    /** The beaten earth of the floors of the poor: packed mud, a fixed accent. */
    private static final String EARTH = "minecraft:packed_mud";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    /** The white gypsum of the window frames of tower houses: smooth quartz, a fixed accent. */
    private static final String GYPSUM = "minecraft:smooth_quartz";
    /** Dried palm leaves of canopies and sheds: slabs of bamboo mosaic, a fixed accent. */
    private static final String THATCH = "minecraft:bamboo_mosaic_slab[type=bottom,waterlogged=false]";

    private DesertTown() {
    }

    // ---------------------------------------------------------------- the plans of the houses

    /**
     * The houses the house program draws: their names, their ground and their beds.
     */
    enum Plan {
        HUT("hut", 5, 7, 1, "minecraft:mud_bricks"),
        COURT("court", 13, 13, 3, "minecraft:light_blue_glazed_terracotta"),
        WINDTOWER("windtower", 7, 9, 2, "minecraft:smooth_sandstone"),
        MERCHANT("merchant", 11, 11, 3, "minecraft:cyan_carpet"),
        TERRACE("terrace", 9, 11, 3, "minecraft:oak_trapdoor"),
        NOBLE("noble", 13, 15, 4, "minecraft:prismarine_bricks");

        final String key;
        final int width;
        final int depth;
        final int beds;
        final String icon;

        Plan(String key, int width, int depth, int beds, String icon) {
            this.key = key;
            this.width = width;
            this.depth = depth;
            this.beds = beds;
            this.icon = icon;
        }
    }

    /**
     * The town buildings of the style: the six houses of the house program, the smithy, the tavern and the piece of the town
     * wall with its gate.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Plan plan : Plan.values()) {
            Structure s = house(plan);
            list.add(new BuildingGenerator.Building("desert_house_" + plan.key, "housing", "house", plan.icon, (s.sizeX - 1) / 2, 0,
                    s.sizeZ - 1, s, "house", 1, ""));
        }
        Structure smithy = smithy();
        list.add(new BuildingGenerator.Building("desert_smithy", "production", "workshop", "minecraft:anvil", (smithy.sizeX - 1) / 2, 0,
                smithy.sizeZ - 1, smithy, "workshop", 1, "").at("door", 5, 1, 10));
        Structure tavern = tavern();
        list.add(new BuildingGenerator.Building("desert_tavern", "trade", "tavern", "minecraft:barrel", (tavern.sizeX - 1) / 2, 0,
                tavern.sizeZ - 1, tavern, "tavern", 1, "").at("door", 6, 1, 12));
        Structure gate = wallGate();
        list.add(new BuildingGenerator.Building("desert_wall_gate", "defense", "gatehouse", "minecraft:chiseled_sandstone", (gate.sizeX - 1) / 2, 0,
                gate.sizeZ - 1, gate, "tower", 1, "")
                .at("door", 11, 0, 10)
                .at("castle_gate", 10, 1, 5).at("castle_gate", 12, 1, 5)
                .at("castle_wall", 8, 13, 6).at("castle_wall", 14, 13, 6));
        return list;

    }

    /**
     * The house program: draws the house of a plan.
     */
    static Structure house(Plan plan) {
        return switch (plan) {
            case HUT -> hut();
            case COURT -> courtHouse();
            case WINDTOWER -> windTowerHouse();
            case MERCHANT -> merchantHouse();
            case TERRACE -> terraceHouse();
            case NOBLE -> nobleHouse();

        };
    }

    // ---------------------------------------------------------------- drawing inside the box

    private static void put(Structure s, int x, int y, int z, String state) {
        Desert.put(s, x, y, z, state);
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
     * The walls of a block from (x1, z1) to (x2, z2), from layer {@code y1} to {@code y2}, of the given material, with a
     * plinth course of rough stone at {@code y1} when {@code plinth}.
     */
    private static void walls(Structure s, int x1, int z1, int x2, int z2, int y1, int y2, String wall, boolean plinth) {
        for (int y = y1; y <= y2; y++) {
            String state = plinth && y == y1 ? BASE : wall;
            fill(s, x1, y, z1, x2, y, z1, state);
            fill(s, x1, y, z2, x2, y, z2, state);
            fill(s, x1, y, z1, x1, y, z2, state);
            fill(s, x2, y, z1, x2, y, z2, state);
        }
    }

    /**
     * A footing of rough stone at layer 0 under a block from (x1, z1) to (x2, z2) and its floor inside.
     */
    private static void footing(Structure s, int x1, int z1, int x2, int z2, String floor) {
        fill(s, x1, 0, z1, x2, 0, z2, BASE);
        fill(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, floor);
    }

    /** An inner door one wide under a pointed arch in a wall whose face looks {@code facing}. */
    private static void door(Structure s, int x, int y, int z, String facing) {
        Desert.doorway(s, x, y, z, facing, 1, 2, 1, Desert.Arch.POINTED, false);
    }

    /**
     * The ends of the beams of a ceiling (toron) sticking out of a wall face one block, every other block from {@code a1} to
     * {@code a2} along x (the face looking north or south at z) or along z (looking east or west at x).
     */
    private static void beamEnds(Structure s, int a1, int a2, int c, int y, String facing) {
        int[] o = Furnish.step(facing);
        boolean alongX = facing.equals("north") || facing.equals("south");
        for (int a = a1; a <= a2; a += 2) {
            int x = alongX ? a : c;
            int z = alongX ? c : a;
            put(s, x + o[0], y, z + o[1], Kit.log(Desert.BEAM, alongX ? "z" : "x"));
        }
    }

    /**
     * A small fountain of eight sides, five wide, around (cx, cz) on the paving at layer {@code y}: a floor of glazed tiles,
     * a rim of slabs of rough stone at {@code y + 1}, water inside and in its middle a carved pedestal with a little bowl of
     * trim and water on it.
     */
    private static void basin(Structure s, int cx, int y, int cz) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (!Desert.inPlan(dx, dz, 2, Desert.OCTAGON)) {
                    continue;
                }
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                boolean middle = Math.abs(dx) + Math.abs(dz) <= 1;
                put(s, cx + dx, y, cz + dz, Desert.glazed(rim ? Desert.TILE_CYAN : middle ? Desert.TILE_BLUE : Desert.TILE,
                        dx >= 0 == dz >= 0 ? "north" : "east"));
                put(s, cx + dx, y + 1, cz + dz, rim ? Kit.slab(Desert.BASE_SLAB) : Desert.WATER);
            }
        }
        put(s, cx, y + 1, cz, CARVED);
        put(s, cx, y + 2, cz, Desert.WATER);
        put(s, cx + 1, y + 2, cz, Kit.upside(Desert.TRIM_STAIRS, "west"));
        put(s, cx - 1, y + 2, cz, Kit.upside(Desert.TRIM_STAIRS, "east"));
        put(s, cx, y + 2, cz + 1, Kit.upside(Desert.TRIM_STAIRS, "north"));
        put(s, cx, y + 2, cz - 1, Kit.upside(Desert.TRIM_STAIRS, "south"));
    }

    /**
     * A small orange tree standing on layer {@code y}: a trunk two blocks high in a ring of trim slabs and a round head of
     * flowering leaves.
     */
    private static void orangeTree(Structure s, int x, int y, int z) {
        put(s, x, y - 1, z, "minecraft:coarse_dirt");
        put(s, x, y, z, Kit.log("minecraft:oak_log", "y"));
        put(s, x, y + 1, z, Kit.log("minecraft:oak_log", "y"));
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                put(s, x + dx, y + 2, z + dz, Kit.leaves((dx + dz) % 2 == 0 ? "flowering_azalea" : "azalea"));
                if (dx == 0 || dz == 0) {
                    put(s, x + dx, y + 3, z + dz, Kit.leaves("azalea"));
                }
            }
        }
    }

    /** A lantern on a post of rough stone standing on layer {@code y}. */
    private static void lampPost(Structure s, int x, int y, int z) {
        put(s, x, y, z, Desert.BASE_POST);
        put(s, x, y + 1, z, Kit.lantern());
    }

    // ---------------------------------------------------------------- the hut

    /**
     * A hut of the poor: one room of mud brick under a flat roof with a low parapet, its corners rising as little
     * pinnacles. Before its round-headed door the beams of the ceiling run out of the wall and with a lintel on two poles
     * carry a canopy of palm leaves over a platform of beaten earth, where the fire for cooking burns and the water jar
     * stands. Lattices in the side walls; inside a bed, the chest, a table with a candle, a rug and a lamp.
     */
    static Structure hut() {
        Structure s = new Structure(5, 9, 7);
        footing(s, 0, 0, 4, 4, EARTH);
        walls(s, 0, 0, 4, 4, 1, 3, WALL, false);
        Desert.flatRoof(s, 0, 0, 4, 4, 4, Desert.Merlons.NONE, false);
        Desert.doorway(s, 2, 1, 4, "south", 1, 2, 1, Desert.Arch.ROUND, false);
        Desert.lattice(s, 0, 2, 2, "west", 1, 1, false);
        Desert.lattice(s, 4, 2, 2, "east", 1, 1, false);
        // The canopy: the beams out of the wall, the lintel on two poles, the palm leaves.
        fill(s, 0, 0, 5, 4, 0, 5, EARTH);
        for (int x : new int[]{0, 2, 4}) {
            put(s, x, 3, 5, Kit.log(Desert.BEAM, "z"));
        }
        fill(s, 0, 3, 6, 4, 3, 6, Kit.log(Desert.BEAM, "x"));
        fill(s, 0, 0, 6, 0, 2, 6, Desert.FENCE);
        fill(s, 4, 0, 6, 4, 2, 6, Desert.FENCE);
        fill(s, 0, 4, 5, 4, 4, 6, THATCH);
        put(s, 0, 4, 6, Desert.FROND);
        put(s, 4, 4, 6, Desert.FROND);
        put(s, 2, 5, 5, Desert.FROND);
        put(s, 1, 3, 5, Kit.hangingLantern());
        put(s, 0, 1, 5, "minecraft:campfire[facing=east,lit=true,signal_fire=false,waterlogged=false]");
        Desert.jar(s, 4, 1, 5);
        // Inside.
        Furnish.bed(s, 1, 1, 2, "north", "red");
        Furnish.chest(s, 3, 1, 1, "south");
        put(s, 3, 1, 3, Kit.topSlab(Desert.FLOOR_SLAB));
        put(s, 3, 2, 3, Furnish.candles(2));
        put(s, 3, 1, 2, Desert.CLOTH);
        fill(s, 2, 1, 1, 2, 1, 2, Desert.CLOTH_CARPET);
        Desert.lamp(s, 2, 3, 2);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the court house

    /**
     * A house round a court: rooms on two sides of a court paved with sandstone, the others closed by high walls. In the
     * middle of the court a fountain of eight sides lined with glazed tiles, two date palms and two lamps on posts at its
     * corners. The majlis at the back opens on the court through an arcade of pointed arches; along the west side the
     * kitchen and two bedrooms. The house is entered from the street through a raised portal with a keyhole door in its
     * frame. One terrace roof with stepped merlons runs round the court.
     */
    static Structure courtHouse() {
        Structure s = new Structure(13, 15, 13);
        footing(s, 0, 0, 12, 12, PAVING);
        walls(s, 0, 0, 12, 12, 1, 5, WALL, true);
        fill(s, 4, 1, 0, 4, 5, 12, WALL);
        fill(s, 1, 1, 4, 3, 5, 4, WALL);
        fill(s, 1, 1, 8, 3, 5, 8, WALL);
        Desert.arcade(s, 4, 4, 12, 4, 1, 3, 5, "south", 3, Desert.Arch.POINTED);
        // The roof: one deck round the court, a low parapet with a tiled band toward the court.
        Desert.flatRoof(s, 0, 0, 12, 12, 6, Desert.Merlons.STEPPED, false);
        fill(s, 5, 6, 5, 11, 6, 11, Structure.AIR);
        for (int i = 4; i <= 12; i++) {
            put(s, 4, 6, i, Desert.glazed(Desert.TILE_CYAN, "north"));
            put(s, i, 6, 4, Desert.glazed(Desert.TILE_CYAN, "east"));
            if (i < 12) {
                put(s, 4, 7, i, Kit.slab(Desert.BASE_SLAB));
                put(s, i, 7, 4, Kit.slab(Desert.BASE_SLAB));
            }
        }
        // The court: the fountain, the palms, the lamps.
        basin(s, 8, 0, 8);
        put(s, 6, 0, 6, "minecraft:coarse_dirt");
        Desert.palm(s, 6, 1, 6, 8, null);
        orangeTree(s, 10, 1, 10);
        lampPost(s, 6, 1, 10);
        lampPost(s, 10, 1, 6);
        // The portal on the street: the front wall raised over the roof, the keyhole door in its frame.
        fill(s, 5, 6, 12, 11, 8, 12, WALL);
        Desert.doorway(s, 8, 1, 12, "south", 1, 2, 1, Desert.Arch.HORSESHOE, true);
        fill(s, 5, 9, 12, 11, 9, 12, TRIM);
        Desert.crown(s, true, 4, 12, 12, 10, Desert.Merlons.POINTED);
        put(s, 5, 10, 12, WALL);
        put(s, 11, 10, 12, WALL);
        put(s, 5, 11, 12, Kit.slab(Desert.BASE_SLAB));
        put(s, 11, 11, 12, Kit.slab(Desert.BASE_SLAB));
        // Doors and windows.
        door(s, 4, 1, 2, "east");
        door(s, 4, 1, 6, "east");
        door(s, 4, 1, 10, "east");
        Desert.lattice(s, 0, 2, 6, "west", 1, 2, false);
        Desert.lattice(s, 0, 2, 10, "west", 1, 2, false);
        Desert.lattice(s, 2, 2, 12, "south", 1, 2, false);
        Desert.lattice(s, 8, 3, 0, "north", 3, 1, false);
        put(s, 2, 4, 0, Desert.GLASS);
        // The majlis.
        Desert.divan(s, 6, 1, 10, 1, 1, "south", false);
        Desert.rug(s, 6, 2, 10, 3, 1);
        Desert.trayTable(s, 7, 1, 3);
        Desert.trayTable(s, 9, 1, 3);
        Desert.wallFountain(s, 11, 1, 2, "west");
        Desert.jar(s, 5, 1, 3);
        Desert.lamps(s, 8, 3, 2);
        // The kitchen.
        Furnish.kitchen(s, 1, 1, 1, "south", "oak");
        Desert.jar(s, 1, 1, 3);
        Furnish.sack(s, 1, 1, 2, true);
        Desert.lamp(s, 2, 4, 2);
        // The bedrooms.
        Furnish.bed(s, 1, 1, 6, "north", "red");
        Furnish.bed(s, 2, 1, 6, "north", "red");
        fill(s, 1, 1, 7, 2, 1, 7, Desert.CLOTH_CARPET);
        Desert.jar(s, 3, 1, 5);
        Desert.lamp(s, 2, 4, 6);
        Furnish.bed(s, 1, 1, 10, "north", "red");
        Furnish.chest(s, 1, 1, 11, "east");
        fill(s, 2, 1, 9, 3, 1, 9, Desert.CLOTH_CARPET);
        Desert.trayTable(s, 3, 1, 11);
        Desert.lamp(s, 2, 4, 10);
        s.connect();
        return s;
    }
    // ---------------------------------------------------------------- the house with the wind tower

    /**
     * A window of a tower house in a white frame: a tall pane of glass under a fanlight of warm coloured glass, framed with
     * whitewash on its sides, under its sill and over its head, flush with the wall whose face looks {@code facing}; (x, y,
     * z) the lower pane.
     */
    private static void framedWindow(Structure s, int x, int y, int z, String facing) {
        int[] a = Furnish.step(Furnish.left(facing));
        for (int u = -1; u <= 1; u++) {
            for (int r = -1; r <= 3; r++) {
                String state = u == 0 && r >= 0 && r <= 1 ? Desert.GLASS : u == 0 && r == 2 ? Desert.GLASS_WARM : GYPSUM;
                put(s, x + a[0] * u, y + r, z + a[1] * u, state);
            }
        }
    }

    /**
     * A straight flight of stairs of timber along x or z from (x, y, z), {@code steps} steps rising toward {@code rising},
     * each on a footing of wall down to layer {@code floor}.
     */
    private static void flight(Structure s, int x, int y, int z, int steps, String rising, int floor) {
        int[] d = Furnish.step(rising);
        for (int i = 0; i < steps; i++) {
            int sx = x + d[0] * i;
            int sz = z + d[1] * i;
            for (int yy = floor; yy < y + i; yy++) {
                put(s, sx, yy, sz, WALL);
            }
            put(s, sx, y + i, sz, Kit.stairs(Desert.FLOOR_STAIRS, rising));
        }
    }

    /**
     * A tall house of three storeys on a narrow plot, as the tower houses of mud brick are built: its walls banded with
     * whitewash at every floor, the windows of the upper storeys set in white frames under fanlights of coloured glass, a
     * lattice oriel on corbels over the door, and on the roof terrace a wind tower that draws the breeze down into the
     * bedroom. Inside the kitchen and the store on the ground floor, the sitting room above it and the bedroom at the top,
     * joined by stairs along the walls; a ladder leads up to the roof.
     */
    static Structure windTowerHouse() {
        Structure s = new Structure(7, 27, 9);
        footing(s, 0, 0, 6, 7, PAVING);
        walls(s, 0, 0, 6, 7, 1, 13, WALL, true);
        fill(s, 1, 5, 1, 5, 5, 6, FLOOR);
        fill(s, 1, 10, 1, 5, 10, 6, FLOOR);
        walls(s, 0, 0, 6, 7, 5, 5, WHITEWASH, false);
        walls(s, 0, 0, 6, 7, 10, 10, WHITEWASH, false);
        Desert.flatRoof(s, 0, 0, 6, 7, 14, Desert.Merlons.POINTED, false);
        walls(s, 0, 0, 6, 7, 14, 14, WHITEWASH, false);
        // The stairs: along the east wall to the sitting room, along the west wall to the bedroom, a ladder to the roof.
        flight(s, 5, 1, 6, 5, "north", 1);
        fill(s, 5, 5, 3, 5, 5, 4, Structure.AIR);
        fill(s, 4, 6, 3, 4, 6, 4, Desert.FENCE);
        put(s, 5, 6, 5, Desert.FENCE);
        flight(s, 1, 6, 2, 5, "south", 6);
        fill(s, 1, 10, 4, 1, 10, 5, Structure.AIR);
        fill(s, 2, 11, 4, 2, 11, 5, Desert.FENCE);
        put(s, 1, 11, 3, Desert.FENCE);
        fill(s, 5, 11, 1, 5, 14, 1, Kit.ladder("south"));
        // The wind tower over the bedroom, its shaft open into it; a brazier on the terrace.
        Desert.windTower(s, 3, 14, 2, 3, 8);
        put(s, 3, 14, 2, Structure.AIR);
        Desert.brazier(s, 1, 15, 6);
        // The front: the door, the oriel over it, the framed windows; windows in the sides and the back.
        Desert.doorway(s, 3, 1, 7, "south", 1, 2, 1, Desert.Arch.POINTED, false);
        put(s, 3, 0, 8, Kit.slab(Desert.PAVING_SLAB));
        Desert.lattice(s, 1, 2, 7, "south", 1, 1, false);
        Desert.lattice(s, 5, 2, 7, "south", 1, 1, false);
        Desert.lattice(s, 3, 7, 7, "south", 1, 2, true);
        framedWindow(s, 2, 11, 7, "south");
        framedWindow(s, 4, 11, 7, "south");
        put(s, 3, 11, 7, WHITEWASH);
        framedWindow(s, 0, 7, 3, "west");
        framedWindow(s, 6, 7, 3, "east");
        framedWindow(s, 0, 11, 4, "west");
        framedWindow(s, 6, 11, 4, "east");
        framedWindow(s, 2, 7, 0, "north");
        framedWindow(s, 4, 11, 0, "north");
        Desert.lattice(s, 0, 2, 4, "west", 1, 2, false);
        Desert.lattice(s, 6, 2, 3, "east", 1, 1, false);
        // The kitchen and the store.
        Furnish.kitchen(s, 1, 1, 1, "south", "oak");
        Furnish.chest(s, 4, 1, 1, "south");
        Furnish.table(s, 2, 1, 4, "oak");
        Furnish.chair(s, 3, 1, 4, "west", "oak");
        Desert.jar(s, 1, 1, 5);
        Desert.jar(s, 1, 1, 6);
        Furnish.sack(s, 1, 1, 4, true);
        Desert.lamp(s, 3, 4, 3);
        // The sitting room.
        fill(s, 2, 6, 6, 4, 6, 6, Kit.stairs(Desert.FLOOR_STAIRS, "south"));
        Desert.rug(s, 2, 3, 3, 5, 6);
        Desert.trayTable(s, 3, 6, 4);
        Desert.jar(s, 5, 6, 6);
        Desert.lamp(s, 3, 8, 4);
        Desert.lamp(s, 4, 9, 1);
        // The bedroom.
        Furnish.bed(s, 3, 11, 2, "north", "red");
        Furnish.bed(s, 4, 11, 2, "north", "red");
        fill(s, 3, 11, 4, 4, 11, 5, Desert.CLOTH_CARPET);
        Desert.jar(s, 5, 11, 6);
        Desert.lamp(s, 4, 13, 4);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the merchant's house

    /**
     * The house of a merchant of cloth and spices: two storeys over a shop that opens on the street through a pointed arch
     * behind a counter, under a striped awning on poles; bolts of cloth, sacks and jars inside. The house door beside the
     * shop leads to a hall with the stair, the kitchen lies behind the shop. Upstairs two rooms behind a broad lattice and a
     * lattice oriel over the door; on the roof terrace, crowned with stepped merlons, a little pavilion on columns under a
     * dome, where the family sits in the evening.
     */
    static Structure merchantHouse() {
        Structure s = new Structure(11, 19, 11);
        footing(s, 0, 0, 10, 7, PAVING);
        fill(s, 0, 0, 8, 6, 0, 9, PAVING);
        walls(s, 0, 0, 10, 7, 1, 9, WALL, true);
        fill(s, 1, 5, 1, 9, 5, 6, FLOOR);
        walls(s, 0, 0, 10, 7, 5, 5, TRIM, false);
        Desert.flatRoof(s, 0, 0, 10, 7, 10, Desert.Merlons.STEPPED, false);
        fill(s, 6, 1, 1, 6, 4, 6, WALL);
        fill(s, 1, 1, 3, 5, 4, 3, WALL);
        fill(s, 6, 6, 1, 6, 9, 6, WALL);
        // The shop: its arch, the counter, the goods, the awning.
        Desert.arch(s, 3, 1, 7, "south", 3, 2, 1, Desert.Arch.POINTED, null, false);
        fill(s, 2, 1, 6, 4, 1, 6, Kit.topSlab(Desert.FLOOR_SLAB));
        Desert.jar(s, 2, 2, 6);
        put(s, 4, 2, 6, Furnish.candles(3));
        put(s, 1, 1, 4, "minecraft:cyan_wool");
        put(s, 1, 2, 4, "minecraft:orange_wool");
        put(s, 1, 3, 4, "minecraft:blue_wool");
        put(s, 5, 1, 4, "minecraft:white_wool");
        put(s, 5, 2, 4, "minecraft:yellow_wool");
        Furnish.sack(s, 2, 1, 4, false);
        Furnish.sack(s, 1, 1, 5, false);
        Furnish.crate(s, 4, 1, 4, "oak");
        Desert.lamp(s, 3, 4, 5);
        Desert.lamp(s, 1, 4, 6);
        Desert.awning(s, 0, 8, 6, 9, 4, 1, "south");
        Desert.jar(s, 0, 1, 8);
        Desert.jar(s, 6, 1, 8);
        door(s, 6, 1, 5, "east");
        door(s, 3, 1, 3, "south");
        // The house door, the hall and the stair.
        Desert.doorway(s, 8, 1, 7, "south", 1, 2, 1, Desert.Arch.POINTED, false);
        flight(s, 9, 1, 6, 5, "north", 1);
        fill(s, 9, 5, 3, 9, 5, 4, Structure.AIR);
        fill(s, 8, 6, 3, 8, 6, 4, Desert.FENCE);
        put(s, 9, 6, 5, Desert.FENCE);
        Desert.jar(s, 7, 1, 1);
        Desert.lamp(s, 8, 4, 4);
        // The kitchen with the chest of the household.
        Furnish.kitchen(s, 1, 1, 1, "south", "oak");
        Furnish.chest(s, 5, 1, 1, "west");
        Desert.jar(s, 5, 1, 2);
        Desert.lamp(s, 3, 4, 2);
        // Upstairs: the windows, the two rooms, the ladder to the roof.
        Desert.lattice(s, 3, 7, 7, "south", 3, 2, false);
        Desert.lattice(s, 8, 7, 7, "south", 1, 2, true);
        Desert.lattice(s, 0, 7, 4, "west", 1, 2, false);
        Desert.lattice(s, 10, 7, 4, "east", 1, 2, false);
        put(s, 3, 8, 0, Desert.GLASS);
        door(s, 6, 6, 4, "east");
        Furnish.bed(s, 2, 6, 2, "north", "red");
        Furnish.bed(s, 4, 6, 2, "north", "red");
        Desert.rug(s, 1, 4, 5, 5, 6);
        Desert.lamp(s, 3, 9, 3);
        Furnish.bed(s, 7, 6, 2, "north", "red");
        fill(s, 7, 6, 6, 8, 6, 6, Kit.stairs(Desert.FLOOR_STAIRS, "south"));
        Desert.trayTable(s, 7, 6, 5);
        Desert.lamp(s, 8, 9, 2);
        fill(s, 9, 6, 1, 9, 10, 1, Kit.ladder("south"));
        // The pavilion on the roof.
        for (int[] c : new int[][]{{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
            fill(s, c[0], 11, c[1], c[0], 12, c[1], Desert.BASE_POST);
        }
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                boolean rim = x == 1 || x == 5 || z == 1 || z == 5;
                put(s, x, 13, z, rim ? TRIM : Desert.ROOF.block());
            }
        }
        Desert.dome(s, 3, 14, 3, 5, Desert.DomeKind.SMOOTH);
        fill(s, 2, 11, 2, 4, 11, 2, Kit.stairs(Desert.FLOOR_STAIRS, "north"));
        Desert.rug(s, 2, 3, 4, 4, 11);
        Desert.trayTable(s, 3, 11, 4);
        Desert.lamp(s, 3, 12, 3);
        Desert.brazier(s, 8, 11, 5);
        s.connect();
        return s;
    }
    /**
     * Turns every block of the wall stone of a box into whitewash: the walls of a plastered house.
     */
    private static void whitewash(Structure s, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int y = y1; y <= y2; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    if (s.inside(x, y, z) && s.get(x, y, z).equals(WALL)) {
                        s.set(x, y, z, WHITEWASH);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- the house with the roof terrace

    /**
     * A whitewashed house of two storeys with a roof terrace: the upper storey stands over the back half of the ground floor,
     * so that the roof of the front half is a terrace behind a parapet with pointed merlons, shaded by a striped awning on
     * posts, with divans, a rug and plants in tubs. Bands of turquoise tiles run round the house under both roofs; the
     * windows are screened with wooden lattices. Inside the sitting room by the street door, the kitchen at the back with the
     * stair, and above the bedroom that opens on the terrace.
     */
    static Structure terraceHouse() {
        Structure s = new Structure(9, 15, 11);
        footing(s, 0, 0, 8, 10, PAVING);
        walls(s, 0, 0, 8, 10, 1, 4, WALL, true);
        fill(s, 1, 1, 5, 7, 4, 5, WALL);
        Desert.flatRoof(s, 0, 0, 8, 10, 5, Desert.Merlons.STEPPED, false);
        walls(s, 0, 0, 8, 5, 5, 9, WALL, false);
        fill(s, 1, 5, 1, 7, 5, 4, FLOOR);
        Desert.flatRoof(s, 0, 0, 8, 5, 10, Desert.Merlons.NONE, false);
        whitewash(s, 0, 0, 0, 8, 14, 10);
        walls(s, 0, 0, 8, 10, 4, 4, Desert.glazed(Desert.TILE_CYAN, "north"), false);
        walls(s, 0, 0, 8, 5, 9, 9, Desert.glazed(Desert.TILE_CYAN, "east"), false);
        // Doors, windows and the stair.
        Desert.doorway(s, 4, 1, 10, "south", 1, 2, 1, Desert.Arch.POINTED, true);
        Desert.lattice(s, 2, 2, 10, "south", 1, 2, false);
        Desert.lattice(s, 6, 2, 10, "south", 1, 2, false);
        Desert.lattice(s, 0, 2, 7, "west", 1, 2, false);
        Desert.lattice(s, 8, 2, 7, "east", 1, 2, false);
        Desert.lattice(s, 4, 7, 0, "north", 3, 2, false);
        Desert.lattice(s, 0, 7, 3, "west", 1, 2, false);
        Desert.lattice(s, 8, 7, 3, "east", 1, 2, false);
        Desert.lattice(s, 2, 7, 5, "south", 1, 2, false);
        Desert.lattice(s, 6, 7, 5, "south", 1, 2, false);
        door(s, 4, 1, 5, "south");
        door(s, 4, 6, 5, "south");
        flight(s, 1, 1, 1, 5, "east", 1);
        fill(s, 3, 5, 1, 4, 5, 1, Structure.AIR);
        fill(s, 3, 6, 2, 4, 6, 2, Desert.FENCE);
        put(s, 2, 6, 1, Desert.FENCE);
        // The sitting room.
        fill(s, 1, 1, 6, 1, 1, 9, Kit.stairs(Desert.FLOOR_STAIRS, "west"));
        fill(s, 7, 1, 6, 7, 1, 9, Kit.stairs(Desert.FLOOR_STAIRS, "east"));
        Desert.rug(s, 2, 6, 6, 9, 1);
        Desert.trayTable(s, 4, 1, 7);
        Desert.lamp(s, 3, 4, 7);
        Desert.lamp(s, 5, 4, 8);
        // The kitchen, with the chest of the household.
        Furnish.kitchen(s, 5, 1, 4, "north", "oak");
        Furnish.chest(s, 7, 1, 2, "west");
        Furnish.sack(s, 7, 1, 1, true);
        Desert.jar(s, 6, 1, 1);
        Furnish.table(s, 2, 1, 3, "oak");
        Furnish.chair(s, 1, 1, 3, "east", "oak");
        Furnish.chair(s, 3, 1, 3, "west", "oak");
        Desert.lamp(s, 4, 4, 3);
        // The bedroom.
        Furnish.bed(s, 1, 6, 3, "north", "red");
        Furnish.bed(s, 2, 6, 3, "north", "red");
        Furnish.bed(s, 7, 6, 3, "north", "red");
        fill(s, 3, 6, 3, 5, 6, 4, Desert.CLOTH_CARPET);
        Desert.jar(s, 6, 6, 4);
        Desert.lamp(s, 4, 9, 3);
        // The terrace: the awning, divans, a rug, plants and lamps.
        Desert.awning(s, 1, 6, 7, 7, 8, 6, "south");
        fill(s, 1, 6, 6, 3, 6, 6, Kit.stairs(Desert.FLOOR_STAIRS, "north"));
        fill(s, 5, 6, 6, 7, 6, 6, Kit.stairs(Desert.FLOOR_STAIRS, "north"));
        Desert.rug(s, 2, 8, 6, 9, 6);
        Desert.trayTable(s, 4, 6, 8);
        Furnish.plant(s, 1, 6, 9, "azalea");
        Furnish.plant(s, 7, 6, 9, "flowering_azalea");
        put(s, 2, 7, 7, Kit.hangingLantern());
        put(s, 6, 7, 7, Kit.hangingLantern());
        Desert.brazier(s, 7, 11, 1);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the noble house

    /**
     * The house of a noble family: a block of three bays under a terrace roof with pointed merlons and, rising from it, a
     * domed chamber (qubba) with keyhole windows, a cornice on corbels and a ribbed dome of turquoise tiles with a gilt
     * finial. Before the house lies a garden walled with stepped merlons, entered through a raised portal with a horseshoe
     * gate: a fountain pool of eight sides in its middle, beds of flowers and two date palms. Inside, a hall of glazed
     * tiles with divans and a wall fountain behind a pointed portal, a bedroom with the chest, the kitchen with the stair up
     * to the terrace, and under the dome the chamber of the masters with a great lamp.
     */
    static Structure nobleHouse() {
        Structure s = new Structure(13, 22, 15);
        footing(s, 0, 0, 12, 8, PAVING);
        walls(s, 0, 0, 12, 8, 1, 5, WALL, true);
        fill(s, 4, 1, 1, 4, 5, 7, WALL);
        fill(s, 8, 1, 1, 8, 5, 7, WALL);
        Desert.flatRoof(s, 0, 0, 12, 8, 6, Desert.Merlons.POINTED, false);
        Desert.zellige(s, 5, 1, 7, 7, 0);
        // The domed chamber on the roof.
        walls(s, 3, 0, 9, 6, 7, 10, WALL, false);
        walls(s, 3, 0, 9, 6, 11, 11, Desert.glazed(Desert.TILE_CYAN, "north"), false);
        walls(s, 3, 0, 9, 6, 12, 12, TRIM, false);
        Desert.zellige(s, 4, 1, 8, 5, 6);
        Desert.corbels(s, 6, 12, 3, 4, Desert.SQUARE);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (!Kit.inDisc(dx, dz, 7)) {
                    boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                    put(s, 6 + dx, 13, 3 + dz, corner ? WALL : Kit.slab(Desert.BASE_SLAB));
                    if (corner) {
                        put(s, 6 + dx, 14, 3 + dz, Kit.slab(Desert.BASE_SLAB));
                    }
                }
            }
        }
        Desert.dome(s, 6, 13, 3, 7, Desert.DomeKind.POINTED);
        Desert.arch(s, 3, 8, 3, "west", 1, 1, 1, Desert.Arch.HORSESHOE, Desert.GLASS, false);
        Desert.arch(s, 6, 8, 6, "south", 1, 1, 1, Desert.Arch.HORSESHOE, Desert.GLASS, false);
        door(s, 9, 7, 3, "east");
        // The front of the house: the portal of the hall, lattices.
        Desert.doorway(s, 6, 1, 8, "south", 3, 3, 1, Desert.Arch.POINTED, true);
        Desert.lattice(s, 2, 2, 8, "south", 1, 2, false);
        Desert.lattice(s, 10, 2, 8, "south", 1, 2, false);
        Desert.lattice(s, 0, 2, 4, "west", 1, 2, false);
        Desert.lattice(s, 12, 2, 4, "east", 1, 2, false);
        put(s, 6, 3, 0, Desert.GLASS);
        // The hall.
        fill(s, 5, 1, 2, 5, 1, 3, Kit.stairs(Desert.FLOOR_STAIRS, "west"));
        fill(s, 5, 1, 5, 5, 1, 6, Kit.stairs(Desert.FLOOR_STAIRS, "west"));
        fill(s, 7, 1, 2, 7, 1, 3, Kit.stairs(Desert.FLOOR_STAIRS, "east"));
        fill(s, 7, 1, 5, 7, 1, 6, Kit.stairs(Desert.FLOOR_STAIRS, "east"));
        fill(s, 6, 1, 3, 6, 1, 6, Desert.CLOTH_CARPET);
        Desert.wallFountain(s, 6, 1, 1, "south");
        Desert.lamp(s, 6, 4, 3);
        Desert.lamp(s, 6, 4, 6);
        door(s, 4, 1, 4, "west");
        door(s, 8, 1, 4, "east");
        // The bedroom with the chest.
        Furnish.bed(s, 1, 1, 2, "north", "red");
        Furnish.bed(s, 2, 1, 2, "north", "red");
        Furnish.chest(s, 3, 1, 1, "south");
        Desert.rug(s, 1, 5, 3, 7, 1);
        Desert.lamp(s, 2, 4, 4);
        // The kitchen and the stair to the roof.
        Furnish.kitchen(s, 9, 1, 1, "south", "oak");
        flight(s, 11, 1, 7, 6, "north", 1);
        fill(s, 11, 6, 3, 11, 6, 4, Structure.AIR);
        put(s, 10, 7, 4, Desert.FENCE);
        put(s, 11, 7, 5, Desert.FENCE);
        Furnish.table(s, 9, 1, 5, "oak");
        Furnish.chair(s, 9, 1, 6, "north", "oak");
        Desert.jar(s, 9, 1, 7);
        Furnish.sack(s, 10, 1, 7, true);
        Desert.lamp(s, 10, 4, 4);
        // The chamber under the dome.
        Furnish.bed(s, 4, 7, 2, "north", "red");
        Furnish.bed(s, 8, 7, 2, "north", "red");
        fill(s, 5, 7, 1, 7, 7, 1, Kit.stairs(Desert.FLOOR_STAIRS, "north"));
        Desert.rug(s, 4, 3, 8, 5, 7);
        Desert.trayTable(s, 6, 7, 4);
        Furnish.greatChandelier(s, 6, 3, 16, 3);
        Desert.lamp(s, 4, 10, 4);
        Desert.lamp(s, 8, 10, 4);
        Desert.brazier(s, 1, 7, 7);
        Desert.brazier(s, 11, 7, 7);
        // The garden: its walls, the portal, the pool, the beds and the palms.
        fill(s, 0, 0, 9, 12, 0, 14, BASE);
        fill(s, 1, 0, 9, 11, 0, 13, PAVING);
        for (int y = 1; y <= 3; y++) {
            String state = y == 1 ? BASE : WALL;
            fill(s, 0, y, 9, 0, y, 14, state);
            fill(s, 12, y, 9, 12, y, 14, state);
            fill(s, 0, y, 14, 12, y, 14, state);
        }
        Desert.crown(s, false, 8, 14, 0, 4, Desert.Merlons.STEPPED);
        Desert.crown(s, false, 8, 14, 12, 4, Desert.Merlons.STEPPED);
        Desert.crown(s, true, 0, 12, 14, 4, Desert.Merlons.STEPPED);
        for (int x : new int[]{0, 12}) {
            put(s, x, 4, 14, WALL);
            put(s, x, 5, 14, Kit.slab(Desert.BASE_SLAB));
        }
        fill(s, 3, 1, 14, 9, 8, 14, WALL);
        fill(s, 3, 1, 14, 9, 1, 14, BASE);
        fill(s, 3, 9, 14, 9, 9, 14, TRIM);
        Desert.doorway(s, 6, 1, 14, "south", 3, 2, 1, Desert.Arch.HORSESHOE, false);
        Desert.crown(s, true, 3, 9, 14, 10, Desert.Merlons.POINTED);
        for (int x : new int[]{3, 9}) {
            put(s, x, 10, 14, WALL);
            put(s, x, 11, 14, Kit.lantern());
        }
        basin(s, 6, 0, 11);
        for (int side = 0; side < 2; side++) {
            int x1 = side == 0 ? 1 : 9;
            for (int z = 9; z <= 13; z++) {
                for (int x = x1; x <= x1 + 2; x++) {
                    put(s, x, 0, z, GRASS);
                    if ((x + 2 * z) % 3 != 0) {
                        put(s, x, 1, z, new String[]{"minecraft:red_tulip", "minecraft:orange_tulip", "minecraft:white_tulip",
                                "minecraft:poppy", "minecraft:fern"}[(x * 7 + z * 11) % 5]);
                    }
                }
            }
            int px = side == 0 ? 2 : 10;
            put(s, px, 0, 12, "minecraft:coarse_dirt");
            put(s, px, 1, 12, Structure.AIR);
            Desert.palm(s, px, 1, 12, 6, null);
            put(s, x1 + (side == 0 ? 0 : 2), 0, 9, PAVING);
            lampPost(s, x1 + (side == 0 ? 0 : 2), 1, 9);
        }
        s.connect();
        s.trimSky(3);
        return s;
    }
    // ---------------------------------------------------------------- the smithy

    /**
     * A smithy: a vaulted workshop of thick walls under a flat roof with stepped merlons and a plain dome of stone over its
     * west half, open on the street through a wide pointed arch. In the back corner the forge of rough stone with its fire
     * under a hood, its flue rising through the roof as a tall square chimney of rough stone with a band and a vented cap.
     * Inside the smith's post with his chest, the smithing table, the blast furnace, the anvil, the quenching tub, a
     * grindstone, iron and coal in store; before it a paved yard under a striped awning with a woodpile and sacks of
     * charcoal.
     */
    static Structure smithy() {
        Structure s = new Structure(11, 17, 13);
        footing(s, 0, 0, 10, 8, PAVING);
        fill(s, 0, 0, 9, 10, 0, 11, PAVING);
        walls(s, 0, 0, 10, 8, 1, 6, WALL, true);
        Desert.flatRoof(s, 0, 0, 10, 8, 7, Desert.Merlons.STEPPED, false);
        Desert.arch(s, 5, 1, 8, "south", 5, 2, 1, Desert.Arch.POINTED, null, false);
        Desert.lattice(s, 0, 3, 5, "west", 1, 2, false);
        Desert.lattice(s, 10, 3, 5, "east", 1, 2, false);
        put(s, 3, 4, 0, Desert.GLASS);
        // The dome over the west half, open into the workshop.
        int top = Desert.drum(s, 4, 8, 4, 5, 2, null);
        Desert.dome(s, 4, top, 4, 5, false, Desert.STONE, null, 0);
        Desert.plate(s, 4, 7, 4, 1, Desert.ROUND, Structure.AIR);
        // The forge, its hood and the chimney.
        fill(s, 8, 1, 1, 9, 1, 2, BASE);
        put(s, 8, 2, 1, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        put(s, 9, 2, 1, BASE);
        fill(s, 8, 2, 2, 9, 2, 2, Kit.slab(Desert.BASE_SLAB));
        fill(s, 8, 3, 1, 9, 6, 1, BASE);
        fill(s, 8, 3, 2, 9, 3, 2, Kit.upside(Desert.BASE_STAIRS, "north"));
        fill(s, 8, 4, 2, 9, 4, 2, Kit.stairs(Desert.BASE_STAIRS, "north"));
        fill(s, 8, 7, 1, 9, 13, 2, BASE);
        fill(s, 8, 10, 1, 9, 10, 2, TRIM);
        fill(s, 8, 14, 1, 9, 14, 2, TRIM);
        fill(s, 8, 15, 1, 9, 15, 2, Desert.BASE_POST);
        fill(s, 8, 16, 1, 9, 16, 2, Kit.slab(Desert.BASE_SLAB));
        // The smith's corner: his post and chest, the smithing table, the blast furnace.
        put(s, 2, 1, 1, "minecraft:smithing_table");
        put(s, 3, 1, 1, "kingdomworld:work_post");
        Furnish.chest(s, 4, 1, 1, "south");
        put(s, 6, 1, 1, "minecraft:blast_furnace[facing=south,lit=false]");
        put(s, 5, 1, 1, Kit.topSlab(Desert.SLAB));
        put(s, 5, 2, 1, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        // The anvil, the tub, the grindstone, the stock.
        put(s, 7, 1, 4, "minecraft:anvil[facing=west]");
        put(s, 9, 1, 5, "minecraft:water_cauldron[level=3]");
        put(s, 1, 1, 3, "minecraft:grindstone[face=floor,facing=east]");
        put(s, 1, 1, 6, "minecraft:raw_iron_block");
        put(s, 1, 1, 7, "minecraft:coal_block");
        put(s, 2, 1, 7, "minecraft:coal_block");
        put(s, 1, 2, 7, "minecraft:iron_block");
        Furnish.crate(s, 9, 1, 7, "spruce");
        Furnish.sack(s, 8, 1, 7, false);
        Desert.lamp(s, 6, 5, 3);
        Desert.lamp(s, 7, 5, 6);
        Desert.lamp(s, 2, 5, 6);
        // The yard: the awning, the woodpile, the sacks of charcoal.
        Desert.awning(s, 6, 9, 10, 10, 4, 1, "south");
        fill(s, 0, 1, 9, 2, 1, 9, Kit.log("minecraft:oak_log", "x"));
        fill(s, 0, 2, 9, 1, 2, 9, Kit.log("minecraft:oak_log", "x"));
        Furnish.sack(s, 0, 1, 10, false);
        Furnish.sack(s, 1, 1, 10, false);
        put(s, 9, 1, 9, "minecraft:grindstone[face=floor,facing=south]");
        put(s, 7, 3, 9, Kit.hangingLantern());
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the tavern

    /**
     * A tavern of two storeys entered through a raised portal with a pointed doorway, a striped awning on each side of it
     * shading a tea terrace with divans and brass tray tables, lattice oriels over the awnings, a wind tower on the roof.
     * In the taproom the counter with the innkeeper's post, his chest and the barrels behind it, two long tables with
     * benches, a corner of divans round a rug, the kitchen at the back and the stair up to the gallery; above two rooms for
     * guests with two beds each.
     */
    static Structure tavern() {
        Structure s = new Structure(13, 23, 15);
        footing(s, 0, 0, 12, 10, PAVING);
        fill(s, 0, 0, 11, 12, 0, 13, PAVING);
        walls(s, 0, 0, 12, 10, 1, 10, WALL, true);
        fill(s, 1, 6, 1, 11, 6, 9, FLOOR);
        walls(s, 0, 0, 12, 10, 6, 6, TRIM, false);
        Desert.flatRoof(s, 0, 0, 12, 10, 11, Desert.Merlons.POINTED, false);
        int top = Desert.windTower(s, 9, 11, 3, 5, 7);
        put(s, 9, 11, 3, Structure.AIR);
        // The portal: a block jutting out of the front and rising over the roof, pilasters of red stone, a panel of
        // glazed tiles over the door, the banners of the sign on its sides.
        fill(s, 3, 0, 11, 9, 0, 11, BASE);
        fill(s, 3, 1, 10, 9, 14, 11, WALL);
        fill(s, 3, 1, 11, 9, 1, 11, BASE);
        fill(s, 3, 2, 11, 3, 14, 11, BASE);
        fill(s, 9, 2, 11, 9, 14, 11, BASE);
        fill(s, 3, 15, 10, 9, 15, 11, TRIM);
        Desert.crown(s, true, 3, 9, 11, 16, Desert.Merlons.POINTED);
        Desert.crown(s, true, 3, 9, 10, 16, Desert.Merlons.NONE);
        for (int x : new int[]{3, 9}) {
            put(s, x, 16, 11, WALL);
            put(s, x, 17, 11, Kit.slab(Desert.BASE_SLAB));
            put(s, x, 16, 10, WALL);
        }
        Desert.doorway(s, 6, 1, 11, "south", 3, 3, 2, Desert.Arch.POINTED, false);
        for (int y = 8; y <= 12; y++) {
            for (int x = 4; x <= 8; x++) {
                boolean border = x == 4 || x == 8 || y == 8 || y == 12;
                put(s, x, y, 11, Desert.glazed(border ? Desert.TILE_BLUE : Desert.TILE, (x + y) % 2 == 0 ? "north" : "east"));
            }
        }
        put(s, 6, 10, 11, CARVED);
        put(s, 2, 10, 11, Kit.wallBanner("red", "west"));
        put(s, 10, 10, 11, Kit.wallBanner("red", "east"));
        // The tea terrace under the awnings.
        for (int side = 0; side < 2; side++) {
            int x1 = side == 0 ? 0 : 10;
            int x2 = x1 + 2;
            Desert.awning(s, x1, 11, x2, 13, 4, 1, "south");
            int wall = side == 0 ? 0 : 12;
            int in = side == 0 ? 1 : 11;
            fill(s, wall, 1, 11, wall, 1, 12, Kit.stairs(Desert.FLOOR_STAIRS, side == 0 ? "west" : "east"));
            fill(s, in, 1, 11, in, 1, 12, Desert.CLOTH_CARPET);
            Desert.trayTable(s, in, 1, 11);
            put(s, in, 3, 12, Kit.hangingLantern());
            Desert.lattice(s, side == 0 ? 1 : 11, 8, 10, "south", 1, 2, true);
        }

        // The counter, the innkeeper's post, his chest and the barrels.
        fill(s, 1, 1, 3, 3, 1, 3, Kit.topSlab(Desert.FLOOR_SLAB));
        put(s, 1, 2, 3, Furnish.candles(2));
        Desert.jar(s, 3, 2, 3);
        put(s, 2, 1, 1, "kingdomworld:work_post");
        Furnish.chest(s, 3, 1, 1, "south");
        put(s, 1, 1, 1, Kit.barrel());
        put(s, 1, 1, 2, Kit.barrel());
        put(s, 4, 1, 1, Kit.barrel());
        fill(s, 1, 3, 1, 4, 3, 1, Kit.topSlab(Desert.SLAB));
        Desert.jar(s, 2, 4, 1);
        put(s, 4, 4, 1, Furnish.candles(3));
        // The tables with their benches, the divans, the lamps.
        for (int x : new int[]{5, 8}) {
            Furnish.longTable(s, x, 4, x, 7, 1, "oak");
            Furnish.bench(s, x - 1, 4, x - 1, 7, 1, "east", "oak");
            Furnish.bench(s, x + 1, 4, x + 1, 7, 1, "west", "oak");
            Desert.lamps(s, x, 3, 5);
        }
        fill(s, 1, 1, 6, 1, 1, 9, Kit.stairs(Desert.FLOOR_STAIRS, "west"));
        fill(s, 2, 1, 6, 2, 1, 9, Desert.CLOTH_CARPET);
        Desert.trayTable(s, 2, 1, 7);
        Desert.lamp(s, 2, 4, 8);
        Desert.lamp(s, 6, 4, 9);
        // The kitchen and the stair.
        Furnish.kitchen(s, 8, 1, 1, "south", "oak");
        put(s, 11, 1, 1, "minecraft:smoker[facing=south,lit=false]");
        put(s, 6, 1, 1, Desert.CLOTH);
        Furnish.sack(s, 7, 1, 1, true);
        flight(s, 11, 1, 4, 6, "south", 1);
        fill(s, 11, 6, 7, 11, 6, 8, Structure.AIR);
        fill(s, 10, 7, 7, 10, 7, 8, Desert.FENCE);
        // The rooms for guests over the taproom, the gallery before them.
        fill(s, 1, 7, 6, 11, 10, 6, WALL);
        fill(s, 5, 7, 1, 5, 10, 5, WALL);
        door(s, 3, 7, 6, "south");
        door(s, 8, 7, 6, "south");
        for (int x1 : new int[]{1, 6}) {
            Furnish.bed(s, x1, 7, 2, "north", "red");
            Furnish.bed(s, x1 + 3, 7, 2, "north", "red");
            Desert.jar(s, x1 + 1, 7, 1);
            fill(s, x1 + 1, 7, 3, x1 + 2, 7, 4, Desert.CLOTH_CARPET);
            Desert.lamp(s, x1 + 1, 10, 3);
        }
        Desert.lattice(s, 0, 8, 3, "west", 1, 2, false);
        Desert.lattice(s, 12, 8, 3, "east", 1, 2, false);
        Desert.lattice(s, 3, 8, 0, "north", 1, 2, false);
        Desert.lattice(s, 8, 8, 0, "north", 1, 2, false);
        Desert.lattice(s, 6, 3, 0, "north", 1, 2, false);
        Desert.lattice(s, 0, 3, 7, "west", 1, 1, false);

        fill(s, 4, 7, 9, 7, 7, 9, Kit.stairs(Desert.FLOOR_STAIRS, "south"));
        Desert.trayTable(s, 5, 7, 8);
        Desert.lamp(s, 3, 10, 8);
        Desert.lamp(s, 8, 10, 8);
        Desert.brazier(s, 2, 12, 8);
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- the town wall and its gate

    /**
     * A stair winding round a newel in the three by three core of a gate tower whose middle is (cx, cz), from layer 1 up to
     * layer 12, and a door at its top on layer 13 leading out of the tower toward {@code out} onto the wall walk.
     */
    private static void spiral(Structure s, int cx, int cz, String out) {
        int sign = out.equals("east") ? 1 : -1;
        int[][] ring = {{-sign, -1}, {0, -1}, {sign, -1}, {sign, 0}, {sign, 1}, {0, 1}, {-sign, 1}, {-sign, 0}};
        fill(s, cx, 1, cz, cx, 12, cz, WALL);
        for (int i = 0; i < 12; i++) {
            int[] c = ring[i % 8];
            int[] n = ring[(i + 1) % 8];
            String facing = n[0] > c[0] ? "east" : n[0] < c[0] ? "west" : n[1] > c[1] ? "south" : "north";
            put(s, cx + c[0], 1 + i, cz + c[1], Kit.stairs(Desert.FLOOR_STAIRS, facing));
        }
        put(s, cx, 13, cz, Kit.lantern());
        door(s, cx + 2 * sign, 13, cz, out);
    }

    /**
     * A piece of the town wall with its gate: a block of masonry between two square towers, pierced by a great horseshoe
     * arch whose head is closed with a wooden grille, its ring of red and pale voussoirs under a band of glazed tiles; over
     * the gate a gallery jutting out on corbels with pointed merlons, the wall walk behind it. The towers, battered at the
     * foot and drawn in above, rise high over the walk with their corbelled parapets; in each a stair winds up round a newel
     * from the door at its foot to the walk. In the passage the double gate stands open; braziers flank the approach.
     */
    static Structure wallGate() {
        Structure s = new Structure(23, 28, 11);
        fill(s, 7, 0, 0, 15, 0, 9, PAVING);
        // The towers.
        for (int cx : new int[]{3, 19}) {
            Desert.tower(s, cx, 0, 5, 5, 24, Desert.Merlons.POINTED);
            fill(s, cx - 2, 0, 3, cx + 2, 0, 7, PAVING);
            door(s, cx, 1, 2, "north");
            spiral(s, cx, 5, cx < 11 ? "east" : "west");
            Desert.lamp(s, cx - 2, 5, 3);
            Desert.lamp(s, cx + 2, 5, 7);
            put(s, cx, 12, 8, Kit.wallBanner("red", "south"));
        }
        // The gate block, its passage and the walk.
        fill(s, 7, 1, 3, 15, 11, 8, WALL);
        fill(s, 6, 7, 3, 6, 11, 8, WALL);
        fill(s, 16, 7, 3, 16, 11, 8, WALL);
        fill(s, 7, 1, 8, 15, 1, 8, BASE);
        fill(s, 7, 1, 3, 15, 1, 3, BASE);
        fill(s, 6, 12, 3, 16, 12, 8, Desert.ROOF.block());
        Desert.arch(s, 11, 1, 8, "south", 5, 3, 6, Desert.Arch.HORSESHOE, null, false);
        for (int y = 7; y <= 10; y++) {
            for (int x = 8; x <= 14; x++) {
                if (!s.solid(x, y, 8)) {
                    put(s, x, y, 8, Kit.shutter(Desert.LATTICE, "north"));
                }
            }
        }
        for (int y = 7; y <= 10; y++) {
            for (int x : new int[]{7, 8, 14, 15}) {
                if (s.get(x, y, 8).equals(WALL)) {
                    put(s, x, y, 8, Desert.glazed((x + y) % 2 == 0 ? Desert.TILE : Desert.TILE_BLUE, "north"));
                }
            }
        }
        put(s, 9, 1, 7, Desert.DOOR + "[facing=north,half=lower,hinge=left,open=true,powered=false]");
        put(s, 9, 2, 7, Desert.DOOR + "[facing=north,half=upper,hinge=left,open=true,powered=false]");
        put(s, 13, 1, 7, Desert.DOOR + "[facing=north,half=lower,hinge=right,open=true,powered=false]");
        put(s, 13, 2, 7, Desert.DOOR + "[facing=north,half=upper,hinge=right,open=true,powered=false]");
        Desert.lamp(s, 11, 7, 5);
        // The gallery over the gate, the parapets of the walk.
        fill(s, 7, 11, 9, 15, 11, 9, Kit.upside(Desert.TRIM_STAIRS, "north"));
        fill(s, 7, 12, 9, 15, 12, 9, TRIM);
        fill(s, 7, 13, 9, 15, 13, 9, WALL);
        Desert.crown(s, true, 6, 16, 9, 14, Desert.Merlons.POINTED);
        put(s, 6, 13, 8, WALL);
        put(s, 16, 13, 8, WALL);
        put(s, 6, 14, 8, Kit.slab(Desert.BASE_SLAB));
        put(s, 16, 14, 8, Kit.slab(Desert.BASE_SLAB));
        Desert.crown(s, true, 5, 17, 3, 13, Desert.Merlons.NONE);
        for (int x : new int[]{8, 14}) {
            put(s, x, 12, 9, Desert.CARVED);
            put(s, x, 10, 9, Kit.hangingLantern());
        }
        Desert.brazier(s, 7, 1, 9);
        Desert.brazier(s, 15, 1, 9);
        Desert.brazier(s, 11, 13, 4);
        s.connect();
        s.trimSky(3);
        return s;
    }
}
