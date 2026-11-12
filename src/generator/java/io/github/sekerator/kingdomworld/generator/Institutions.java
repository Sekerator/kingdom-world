package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The institutions of a capital, each to be known from the street at a glance: the treasury that is a temple of money with
 * columns, a pediment and a green dome, the courthouse with its clock and bell tower beside the fortified guardhouse of the
 * watch, and the long calm infirmary with its cupola, its sign of the green cross and its garden of herbs.
 *
 * <p>All three stand flush with the street: layer 0 replaces the ground (paving, a yard, a garden), layer 1 is the street.
 * They are drawn with the blocks that stand for the parts of a look (see {@link Kit}), so that each takes the stone, the
 * roof, the plaster and the wood of the kingdom it is built in.
 */
final class Institutions {
    private static final String WORK_POST = "kingdomworld:work_post";
    private static final String STRIPPED_SPRUCE = "minecraft:stripped_spruce_log";
    private static final String STRIPPED_OAK = "minecraft:stripped_oak_log";
    private static final String DARK_LOG = "minecraft:dark_oak_log";
    private static final String SPRUCE_PLANKS = "minecraft:spruce_planks";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String SPRUCE_DOOR = "minecraft:spruce_door";
    private static final String BOOKSHELF = "minecraft:bookshelf";
    private static final String PLATE = "minecraft:oak_pressure_plate[powered=false]";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String GOLD = "minecraft:gold_block";
    /** Copper gone green, waxed so that it stays so: the dome of the treasury. */
    private static final Kit.Roof COPPER = new Kit.Roof("minecraft:waxed_oxidized_cut_copper",
            "minecraft:waxed_oxidized_cut_copper_stairs", "minecraft:waxed_oxidized_cut_copper_slab");

    private Institutions() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("classic_treasury", "trade", "bank", "kingdomworld:mint", 8, 1, 16, treasuryHouse(),
                "none", 1, "")
                .at("door", 8, 3, 12)
                .at("bank", 8, 3, 5)
                .at("guard", 11, 3, 9));
        list.add(new BuildingGenerator.Building("classic_court", "defense", "court", "minecraft:bell", 9, 1, 18, courthouse(),
                "barracks", 1, "")
                .params("\"bunks\": 4")
                .at("door", 6, 1, 18)
                .at("guard", 6, 2, 15)
                .at("guard", 7, 2, 7)
                .at("cells", 13, 2, 9));
        list.add(new BuildingGenerator.Building("classic_infirmary", "decoration", "infirmary", "kingdomworld:infirmary", 8, 1, 12, infirmary(),
                "none", 1, "")
                .at("door", 4, 1, 12));
        return list;
    }

    // ---------------------------------------------------------------- shared shapes

    private static String gate(String wood, String facing) {
        return "minecraft:" + wood + "_fence_gate[facing=" + facing + ",in_wall=false,open=false,powered=false]";
    }

    private static String lectern(String facing) {
        return "minecraft:lectern[facing=" + facing + ",has_book=false,powered=false]";
    }

    private static String grindstone(String facing) {
        return "minecraft:grindstone[face=floor,facing=" + facing + "]";
    }

    /** An iron trapdoor standing open against the block behind it: a shield hung on a wall. */
    private static String shield(String facing) {
        return Kit.shutter("minecraft:iron_trapdoor", facing);
    }

    /**
     * A lantern hanging on a chain from a roof or a ceiling: the chain from {@code top} down, the lantern under it.
     */
    private static void chandelier(Structure s, int x, int z, int lanternY, int top) {
        if (top > lanternY) {
            s.fill(x, lanternY + 1, z, x, top, z, Kit.chain());
        }
        s.set(x, lanternY, z, Kit.hangingLantern());
    }

    /**
     * A gable roof whose ridge runs along z, its gables to the front and the back: rows of stairs rising from the west eave
     * {@code x1} and the east eave {@code x2}. The first {@code steep} rows above the eaves climb two layers each, with a
     * full block of the roof under the stair; the rows above climb one. An odd width is closed with a ridge of slabs.
     *
     * @return for every column from x1 to x2 the lowest layer the roof takes in it
     */
    private static int[] roofZ(Structure s, int x1, int x2, int z1, int z2, int y, int steep, Kit.Roof roof) {
        int[] under = new int[x2 - x1 + 1];
        int height = y;
        for (int row = 0; x1 + row <= x2 - row; row++) {
            boolean steepRow = row > 0 && row <= steep;
            if (row > 0) {
                height += steepRow ? 2 : 1;
            }
            int west = x1 + row;
            int east = x2 - row;
            if (steepRow) {
                s.fill(west, height - 1, z1, west, height - 1, z2, roof.block());
                s.fill(east, height - 1, z1, east, height - 1, z2, roof.block());
            }
            if (west < east) {
                s.fill(west, height, z1, west, height, z2, Kit.stairs(roof.stairs(), "east"));
                s.fill(east, height, z1, east, height, z2, Kit.stairs(roof.stairs(), "west"));
            } else {
                s.fill(west, height, z1, west, height, z2, Kit.slab(roof.slab()));
            }
            under[west - x1] = steepRow ? height - 1 : height;
            under[east - x1] = under[west - x1];
        }
        return under;
    }

    /**
     * A gable roof whose ridge runs along x, its eaves to the front and the back: rows of stairs rising from the back eave
     * {@code z1} and the front eave {@code z2}, steep rows as in {@link #roofZ}.
     *
     * @return for every row from z1 to z2 the lowest layer the roof takes in it
     */
    private static int[] roofX(Structure s, int x1, int x2, int z1, int z2, int y, int steep, Kit.Roof roof) {
        int[] under = new int[z2 - z1 + 1];
        int height = y;
        for (int row = 0; z1 + row <= z2 - row; row++) {
            boolean steepRow = row > 0 && row <= steep;
            if (row > 0) {
                height += steepRow ? 2 : 1;
            }
            int back = z1 + row;
            int front = z2 - row;
            if (steepRow) {
                s.fill(x1, height - 1, back, x2, height - 1, back, roof.block());
                s.fill(x1, height - 1, front, x2, height - 1, front, roof.block());
            }
            if (back < front) {
                s.fill(x1, height, back, x2, height, back, Kit.stairs(roof.stairs(), "south"));
                s.fill(x1, height, front, x2, height, front, Kit.stairs(roof.stairs(), "north"));
            } else {
                s.fill(x1, height, back, x2, height, back, Kit.slab(roof.slab()));
            }
            under[back - z1] = steepRow ? height - 1 : height;
            under[front - z1] = under[back - z1];
        }
        return under;
    }

    /**
     * Closes the gable of a roof along z at the wall {@code z}: every column between the eaves from {@code y} up to the
     * underside of the roof, where nothing stands yet.
     */
    private static void gableAtZ(Structure s, int x1, int[] under, int z, int y, String state) {
        for (int i = 1; i < under.length - 1; i++) {
            for (int h = y; h < under[i]; h++) {
                if (!s.solid(x1 + i, h, z)) {
                    s.set(x1 + i, h, z, state);
                }
            }
        }
    }

    /**
     * Closes the gable of a roof along x at the wall {@code x}.
     */
    private static void gableAtX(Structure s, int z1, int[] under, int x, int y, String state) {
        for (int i = 1; i < under.length - 1; i++) {
            for (int h = y; h < under[i]; h++) {
                if (!s.solid(x, h, z1 + i)) {
                    s.set(x, h, z1 + i, state);
                }
            }
        }
    }

    /**
     * A tented roof over a square tower that climbs {@code rise} layers for every block inwards: each ring is a course of
     * stairs on {@code rise - 1} courses of the full block, filled inside so that no hollow is left under it, closed with a
     * slab on its point.
     *
     * @return the layer above the point
     */
    private static int needle(Structure s, int x1, int z1, int x2, int z2, int y, int rise, Kit.Roof roof, String core) {
        int layer = y;
        int ring = 0;
        while (x2 - ring - (x1 + ring) >= 2 && z2 - ring - (z1 + ring) >= 2) {
            int west = x1 + ring;
            int east = x2 - ring;
            int back = z1 + ring;
            int front = z2 - ring;
            if (ring > 0) {
                for (int k = 0; k < rise - 1; k++) {
                    s.walls(west, layer, back, east, layer, front, roof.block());
                    s.fill(west + 1, layer, back + 1, east - 1, layer, front - 1, core);
                    layer++;
                }
            }
            s.fill(west, layer, back, east, layer, back, Kit.stairs(roof.stairs(), "south"));
            s.fill(west, layer, front, east, layer, front, Kit.stairs(roof.stairs(), "north"));
            s.fill(west, layer, back + 1, west, layer, front - 1, Kit.stairs(roof.stairs(), "east"));
            s.fill(east, layer, back + 1, east, layer, front - 1, Kit.stairs(roof.stairs(), "west"));
            s.set(west, layer, back, Kit.stairs(roof.stairs(), "south", "outer_left"));
            s.set(east, layer, back, Kit.stairs(roof.stairs(), "south", "outer_right"));
            s.set(west, layer, front, Kit.stairs(roof.stairs(), "north", "outer_right"));
            s.set(east, layer, front, Kit.stairs(roof.stairs(), "north", "outer_left"));
            s.fill(west + 1, layer, back + 1, east - 1, layer, front - 1, core);
            layer++;
            ring++;
        }
        if (x1 + ring <= x2 - ring && z1 + ring <= z2 - ring) {
            for (int k = 0; k < rise - 1; k++) {
                s.fill(x1 + ring, layer, z1 + ring, x2 - ring, layer, z2 - ring, roof.block());
                layer++;
            }
            s.fill(x1 + ring, layer, z1 + ring, x2 - ring, layer, z2 - ring, Kit.slab(roof.slab()));
            layer++;
        }
        return layer;
    }

    /**
     * A dome over a round plan around (cx, cz): one layer for every diameter given, from {@code y} up. Where a layer is
     * wider than the one over it, its edge is stairs sloping inwards, turned at the corners; the rest is full blocks.
     *
     * @return the layer over the dome
     */
    private static int dome(Structure s, int cx, int y, int cz, int[] diameters, Kit.Roof roof) {
        for (int i = 0; i < diameters.length; i++) {
            int d = diameters[i];
            int next = i + 1 < diameters.length ? diameters[i + 1] : 0;
            int r = (d - 1) / 2;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!Kit.inDisc(dx, dz, d)) {
                        continue;
                    }
                    boolean covered = dx == 0 && dz == 0 || next > 0 && Kit.inDisc(dx, dz, next);
                    s.set(cx + dx, y + i, cz + dz, covered ? roof.block() : domeStair(roof, d, dx, dz));
                }
            }
        }
        return y + diameters.length;
    }

    /** The stair on the edge of a layer of a dome, sloping down outwards. */
    private static String domeStair(Kit.Roof roof, int d, int dx, int dz) {
        boolean outX = dx != 0 && !Kit.inDisc(dx + Integer.signum(dx), dz, d);
        boolean outZ = dz != 0 && !Kit.inDisc(dx, dz + Integer.signum(dz), d);
        if (outX && outZ) {
            if (dz < 0) {
                return Kit.stairs(roof.stairs(), "south", dx < 0 ? "outer_left" : "outer_right");
            }
            return Kit.stairs(roof.stairs(), "north", dx < 0 ? "outer_right" : "outer_left");
        }
        boolean alongX = outX || !outZ && Math.abs(dx) >= Math.abs(dz);
        if (alongX) {
            return Kit.stairs(roof.stairs(), dx > 0 ? "west" : "east");
        }
        return Kit.stairs(roof.stairs(), dz > 0 ? "north" : "south");
    }

    /**
     * Quoins on a corner of a wall: the corner column of rough stone, and on every second layer one more block of it on
     * both faces, so that the corner looks bonded of long and short stones.
     *
     * @param dx the way along x from the corner into its wall (+1 or -1)
     * @param dz the way along z from the corner into its wall (+1 or -1)
     */
    private static void quoins(Structure s, int x, int z, int dx, int dz, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            s.set(x, y, z, Kit.ROUGH);
            if ((y - y1) % 2 == 0) {
                s.set(x + dx, y, z, Kit.ROUGH).set(x, y, z + dz, Kit.ROUGH);
            }
        }
    }

    // ---------------------------------------------------------------- the treasury

    /** The cella of the treasury: its walls, the band under the flat roof, the front wall behind the portico. */
    private static final int CELLA_X1 = 1;
    private static final int CELLA_X2 = 15;
    private static final int CELLA_Z1 = 1;
    private static final int CELLA_Z2 = 11;
    private static final int TREASURY_FLOOR = 2;
    private static final int TREASURY_ROOF = 10;
    /** The front row of the portico and the middle of the building. */
    private static final int PORTICO_Z = 14;
    private static final int MIDDLE = 8;

    /**
     * The treasury of the kingdom, its bank and its mint, built as a temple of money: a podium of rough stone with wide steps
     * of trim, a portico of six white columns under a low pediment with a gold coin in it, a cella of stone with pilasters
     * and tall windows barred with iron, a flat roof behind a balustrade, and in the middle of the roof a drum with windows
     * under a dome of green copper with a gold finial. A stack against the back wall smokes over the mint.
     *
     * <p>Inside, the counting hall with its chequered floor and red runner leads to the counter, where the bank stands in
     * a grille of iron bars under a gold emblem. To the west of the counter the vault shows its chests, barrels and gold
     * behind bars; behind it the clerk keeps his desk and lectern; to the east lies the minting room with the mint, the
     * minter's post and chest, a blast furnace, an anvil and a cauldron.
     */
    static Structure treasuryHouse() {
        Structure s = new Structure(17, 23, 17);
        s.fill(0, 0, 0, 16, 0, 16, Kit.ROUGH);
        s.fill(1, 0, 15, 15, 0, 16, Kit.TRIM);
        treasuryPodium(s);
        treasuryCella(s);
        treasuryPortico(s);
        treasuryRoof(s);
        treasuryHall(s);
        treasuryBackRooms(s);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The podium: rough stone with a coping of trim, two wide steps of trim across the front between cheeks that carry
     * lanterns.
     */
    private static void treasuryPodium(Structure s) {
        s.fill(0, 1, 0, 16, 1, PORTICO_Z, Kit.ROUGH);
        s.fill(0, TREASURY_FLOOR, 0, 16, TREASURY_FLOOR, PORTICO_Z, Kit.TRIM);
        s.fill(1, 1, PORTICO_Z + 1, 15, 1, PORTICO_Z + 1, Kit.TRIM);
        s.fill(1, TREASURY_FLOOR, PORTICO_Z + 1, 15, TREASURY_FLOOR, PORTICO_Z + 1, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        s.fill(1, 1, PORTICO_Z + 2, 15, 1, PORTICO_Z + 2, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        for (int x : new int[]{0, 16}) {
            s.fill(x, 1, PORTICO_Z + 1, x, 2, PORTICO_Z + 2, Kit.ROUGH);
            s.set(x, 3, PORTICO_Z + 2, Kit.ROUGH_FENCE).set(x, 4, PORTICO_Z + 2, Kit.lantern());
        }
    }

    /**
     * The cella: walls of stone with pilasters of trim, tall windows barred with iron on carved keystones and sills of trim,
     * small high windows to the back rooms, a band and a cornice of trim under the roof, two doors with lights over them
     * on either side of a pier with a gold block, banners of the kingdom on the wall of the portico.
     */
    private static void treasuryCella(Structure s) {
        int top = TREASURY_ROOF - 1;
        s.walls(CELLA_X1, TREASURY_FLOOR + 1, CELLA_Z1, CELLA_X2, top, CELLA_Z2, Kit.WALL);
        s.walls(CELLA_X1, TREASURY_ROOF, CELLA_Z1, CELLA_X2, TREASURY_ROOF, CELLA_Z2, Kit.TRIM);
        // Pilasters standing out of the walls on the ledge of the podium.
        for (int z : new int[]{1, 5, 8, 11}) {
            s.fill(CELLA_X1 - 1, TREASURY_FLOOR + 1, z, CELLA_X1 - 1, top, z, Kit.TRIM);
            s.fill(CELLA_X2 + 1, TREASURY_FLOOR + 1, z, CELLA_X2 + 1, top, z, Kit.TRIM);
        }
        for (int x : new int[]{1, 5, 11, 15}) {
            s.fill(x, TREASURY_FLOOR + 1, CELLA_Z1 - 1, x, top, CELLA_Z1 - 1, Kit.TRIM);
        }
        // The cornice over the pilasters.
        for (int z = CELLA_Z1; z <= PORTICO_Z; z++) {
            s.set(CELLA_X1 - 1, TREASURY_ROOF, z, Kit.upside(Kit.TRIM_STAIRS, "east"));
            s.set(CELLA_X2 + 1, TREASURY_ROOF, z, Kit.upside(Kit.TRIM_STAIRS, "west"));
        }
        for (int x = CELLA_X1; x <= CELLA_X2; x++) {
            s.set(x, TREASURY_ROOF, CELLA_Z1 - 1, Kit.upside(Kit.TRIM_STAIRS, "south"));
        }
        s.set(CELLA_X1 - 1, TREASURY_ROOF, CELLA_Z1 - 1, Kit.topSlab(Kit.TRIM_SLAB));
        s.set(CELLA_X2 + 1, TREASURY_ROOF, CELLA_Z1 - 1, Kit.topSlab(Kit.TRIM_SLAB));
        // Tall windows of the hall, high small windows of the back rooms.
        for (int z : new int[]{7, 9}) {
            for (int x : new int[]{CELLA_X1, CELLA_X2}) {
                s.fill(x, 4, z, x, 7, z, Kit.BARS);
                s.set(x, 8, z, Kit.CARVED);
                s.set(x == CELLA_X1 ? x - 1 : x + 1, 3, z, Kit.topSlab(Kit.TRIM_SLAB));
            }
        }
        for (int x : new int[]{CELLA_X1, CELLA_X2}) {
            s.fill(x, 6, 3, x, 7, 3, Kit.BARS);
            s.set(x == CELLA_X1 ? x - 1 : x + 1, 5, 3, Kit.topSlab(Kit.TRIM_SLAB));
        }
        for (int x : new int[]{3, MIDDLE, 13}) {
            s.fill(x, 6, CELLA_Z1, x, 7, CELLA_Z1, Kit.BARS);
            s.set(x, 5, CELLA_Z1 - 1, Kit.topSlab(Kit.TRIM_SLAB));
        }
        // The doors on either side of a pier, lights over them, a lintel with gold in its middle.
        s.door(MIDDLE - 1, TREASURY_FLOOR + 1, CELLA_Z2, SPRUCE_DOOR, "north", "left");
        s.door(MIDDLE + 1, TREASURY_FLOOR + 1, CELLA_Z2, SPRUCE_DOOR, "north", "right");
        s.set(MIDDLE - 1, TREASURY_FLOOR + 3, CELLA_Z2, Kit.GLASS).set(MIDDLE + 1, TREASURY_FLOOR + 3, CELLA_Z2, Kit.GLASS);
        s.fill(MIDDLE, TREASURY_FLOOR + 1, CELLA_Z2, MIDDLE, TREASURY_FLOOR + 3, CELLA_Z2, Kit.TRIM);
        s.fill(MIDDLE - 2, TREASURY_FLOOR + 1, CELLA_Z2, MIDDLE - 2, TREASURY_FLOOR + 4, CELLA_Z2, Kit.TRIM);
        s.fill(MIDDLE + 2, TREASURY_FLOOR + 1, CELLA_Z2, MIDDLE + 2, TREASURY_FLOOR + 4, CELLA_Z2, Kit.TRIM);
        s.fill(MIDDLE - 1, TREASURY_FLOOR + 4, CELLA_Z2, MIDDLE + 1, TREASURY_FLOOR + 4, CELLA_Z2, Kit.TRIM);
        s.set(MIDDLE, TREASURY_FLOOR + 4, CELLA_Z2, GOLD);
        s.set(MIDDLE - 2, TREASURY_FLOOR + 5, CELLA_Z2, Kit.stairs(Kit.TRIM_STAIRS, "east"));
        s.set(MIDDLE + 2, TREASURY_FLOOR + 5, CELLA_Z2, Kit.stairs(Kit.TRIM_STAIRS, "west"));
        s.fill(MIDDLE - 1, TREASURY_FLOOR + 5, CELLA_Z2, MIDDLE + 1, TREASURY_FLOOR + 5, CELLA_Z2, Kit.TRIM);
        s.set(MIDDLE, TREASURY_FLOOR + 6, CELLA_Z2, Kit.slab(Kit.TRIM_SLAB));
        s.set(3, 7, CELLA_Z2 + 1, Kit.wallBanner("red", "south")).set(13, 7, CELLA_Z2 + 1, Kit.wallBanner("red", "south"));
        s.set(4, 7, CELLA_Z2 + 1, Kit.wallBanner("yellow", "south")).set(12, 7, CELLA_Z2 + 1, Kit.wallBanner("yellow", "south"));
    }

    /**
     * The portico: six columns of white quartz on bases of trim with carved capitals, an architrave and a frieze with gold
     * in its middle, a cornice, and over it the low pediment of stone and trim with a coin of gold in a carved frame and
     * a gold block on its point. Lanterns hang between the columns.
     */
    private static void treasuryPortico(Structure s) {
        for (int x : new int[]{2, 4, 6, 10, 12, 14}) {
            s.set(x, TREASURY_FLOOR + 1, PORTICO_Z, Kit.TRIM);
            s.fill(x, TREASURY_FLOOR + 2, PORTICO_Z, x, TREASURY_FLOOR + 5, PORTICO_Z, "minecraft:quartz_pillar[axis=y]");
            s.set(x, TREASURY_FLOOR + 6, PORTICO_Z, "minecraft:chiseled_quartz_block");
        }
        int beam = TREASURY_ROOF - 1;
        s.fill(CELLA_X1, beam, CELLA_Z2 + 1, CELLA_X2, beam, PORTICO_Z, Kit.TRIM);
        s.fill(CELLA_X1, TREASURY_ROOF, CELLA_Z2 + 1, CELLA_X2, TREASURY_ROOF, PORTICO_Z, Kit.WALL);
        for (int x : new int[]{2, 4, 6, 10, 12, 14}) {
            s.set(x, TREASURY_ROOF, PORTICO_Z, Kit.CARVED);
        }
        s.set(MIDDLE, TREASURY_ROOF, PORTICO_Z, GOLD);
        s.fill(0, TREASURY_ROOF, PORTICO_Z + 1, 16, TREASURY_ROOF, PORTICO_Z + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(0, TREASURY_ROOF, PORTICO_Z + 1, Kit.topSlab(Kit.TRIM_SLAB)).set(16, TREASURY_ROOF, PORTICO_Z + 1, Kit.topSlab(Kit.TRIM_SLAB));
        // The pediment: a low gable of half a block a step, its edge of trim slabs, its slopes of roof slabs, the body of
        // stone, the coin in the tympanum.
        int base = TREASURY_ROOF + 1;
        for (int x = 0; x <= 16; x++) {
            int r = Math.min(x, 16 - x);
            int layer = base + r / 2;
            boolean top = r % 2 == 1;
            s.set(x, layer, PORTICO_Z + 1, top ? Kit.topSlab(Kit.TRIM_SLAB) : Kit.slab(Kit.TRIM_SLAB));
            if (x >= CELLA_X1 && x <= CELLA_X2) {
                for (int z = CELLA_Z2 + 1; z <= PORTICO_Z; z++) {
                    if (layer > base) {
                        s.fill(x, base, z, x, layer - 1, z, Kit.WALL);
                    }
                    s.set(x, layer, z, top ? Kit.topSlab(Kit.TRIM_SLAB) : Kit.slab(Kit.TRIM_SLAB));
                }
                if (top) {
                    s.set(x, layer, PORTICO_Z, Kit.WALL);
                }
            }
        }
        // The arms of the kingdom in the tympanum: a shield of its cloth with a gold coin, between carved scrolls.
        s.set(MIDDLE - 2, base + 1, PORTICO_Z, Kit.CARVED).set(MIDDLE + 2, base + 1, PORTICO_Z, Kit.CARVED);
        s.set(MIDDLE - 1, base + 1, PORTICO_Z, Kit.CLOTH).set(MIDDLE + 1, base + 1, PORTICO_Z, Kit.CLOTH);
        s.set(MIDDLE, base + 1, PORTICO_Z, GOLD).set(MIDDLE, base + 2, PORTICO_Z, Kit.CLOTH);
        s.set(MIDDLE, base, PORTICO_Z, Kit.CLOTH);
        // Banners of the kingdom hung from the architrave between the outer columns.
        s.set(3, beam, PORTICO_Z + 1, Kit.wallBanner("red", "south")).set(13, beam, PORTICO_Z + 1, Kit.wallBanner("red", "south"));
        s.set(MIDDLE, base + 5, PORTICO_Z + 1, GOLD);
        for (int x : new int[]{3, 13}) {
            s.set(x, beam - 1, 13, Kit.hangingLantern());
        }
        s.set(MIDDLE, beam - 1, 12, Kit.hangingLantern());
    }

    /**
     * The flat roof behind a balustrade with lanterns on its corner piers, the drum with four windows and a lantern inside,
     * the dome of green copper with a gold finial, the stack of the mint.
     */
    private static void treasuryRoof(Structure s) {
        int deck = TREASURY_ROOF;
        s.fill(CELLA_X1 + 1, deck, CELLA_Z1 + 1, CELLA_X2 - 1, deck, CELLA_Z2 - 1, "minecraft:smooth_stone");
        int y = deck + 1;
        for (int x = CELLA_X1; x <= CELLA_X2; x++) {
            for (int z = CELLA_Z1; z <= CELLA_Z2; z++) {
                boolean edge = x == CELLA_X1 || x == CELLA_X2 || z == CELLA_Z1;
                if (!edge) {
                    continue;
                }
                boolean corner = (x == CELLA_X1 || x == CELLA_X2) && (z == CELLA_Z1 || z == CELLA_Z2);
                boolean pier = corner || z == CELLA_Z1 && (x == 5 || x == MIDDLE || x == 11) || z == 5 || z == 8;
                if (pier) {
                    s.set(x, y, z, Kit.WALL).set(x, y + 1, z, corner ? Kit.lantern() : Kit.slab(Kit.TRIM_SLAB));
                } else {
                    s.set(x, y, z, Kit.WALL_FENCE).set(x, y + 1, z, Kit.slab(Kit.TRIM_SLAB));
                }
            }
        }
        // The drum, its cornice and the dome.
        int cz = 6;
        Kit.tube(s, MIDDLE, y, y + 3, cz, 7, Kit.WALL);
        Kit.tube(s, MIDDLE, y, y, cz, 7, Kit.TRIM);
        for (int[] w : new int[][]{{MIDDLE, cz - 3}, {MIDDLE, cz + 3}, {MIDDLE - 3, cz}, {MIDDLE + 3, cz}}) {
            s.fill(w[0], y + 1, w[1], w[0], y + 2, w[1], Kit.GLASS);
            s.set(w[0], y + 3, w[1], Kit.CARVED);
        }
        s.set(MIDDLE, y, cz, Kit.lantern());
        Kit.disc(s, MIDDLE, y + 4, cz, 9, Kit.TRIM);
        int top = dome(s, MIDDLE, y + 5, cz, new int[]{7, 7, 5, 3}, COPPER);
        s.set(MIDDLE, top, cz, GOLD);
        s.set(MIDDLE, top + 1, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        // The stack of the mint against the back wall.
        s.fill(12, TREASURY_FLOOR + 1, CELLA_Z1 - 1, 12, deck + 4, CELLA_Z1 - 1, Kit.CHIMNEY);
        s.set(12, deck + 5, CELLA_Z1 - 1, Kit.CHIMNEY_POT);
    }

    /**
     * The counting hall: a chequered floor, a red runner from the doors to the counter, benches under the windows, the
     * counter with the place of the bank in its middle inside a grille of iron bars under a gold emblem, the bars of the
     * vault to the west, the wall of the minting room with its door to the east, chandeliers under a ceiling with beams.
     */
    private static void treasuryHall(Structure s) {
        int f = TREASURY_FLOOR;
        for (int x = CELLA_X1 + 1; x <= CELLA_X2 - 1; x++) {
            for (int z = CELLA_Z1 + 1; z <= CELLA_Z2 - 1; z++) {
                s.set(x, f, z, (x + z) % 2 == 0 ? "minecraft:calcite" : "minecraft:polished_deepslate");
            }
        }
        s.fill(MIDDLE - 1, f + 1, 6, MIDDLE + 1, f + 1, CELLA_Z2 - 1, Kit.CLOTH_CARPET);
        for (int z : new int[]{7, 9}) {
            s.set(CELLA_X1 + 1, f + 1, z, Kit.stairs(SPRUCE_STAIRS, "west"));
            s.set(CELLA_X2 - 1, f + 1, z, Kit.stairs(SPRUCE_STAIRS, "east"));
        }
        s.set(CELLA_X1 + 1, f + 1, 8, Kit.lantern()).set(CELLA_X2 - 1, f + 1, 8, Kit.lantern());
        // The partition: the bars of the vault, the counter and its grille, the wall of the minting room.
        int z = 5;
        s.fill(CELLA_X1 + 1, f + 1, z, 5, TREASURY_ROOF - 1, z, Kit.BARS);
        s.fill(6, f + 1, z, 6, TREASURY_ROOF - 1, z, Kit.TRIM);
        s.fill(10, f + 1, z, 10, TREASURY_ROOF - 1, z, Kit.TRIM);
        s.fill(7, f + 1, z, 9, f + 1, z, Kit.TRIM);
        s.skip(MIDDLE, f + 1, z);
        s.fill(7, f + 2, z, 9, f + 3, z, Kit.BARS);
        s.set(MIDDLE, f + 2, z, Structure.AIR);
        s.fill(7, f + 4, z, 9, TREASURY_ROOF - 1, z, Kit.WALL);
        s.set(MIDDLE, f + 4, z, Kit.TRIM).set(7, f + 4, z, Kit.TRIM).set(9, f + 4, z, Kit.TRIM);
        s.set(MIDDLE, f + 5, z, GOLD).set(7, f + 5, z, Kit.CARVED).set(9, f + 5, z, Kit.CARVED);
        s.set(MIDDLE, f + 6, z + 1, Kit.wallBanner("red", "south"));
        s.fill(11, f + 1, z, CELLA_X2 - 1, TREASURY_ROOF - 1, z, Kit.WALL);
        s.door(12, f + 1, z, SPRUCE_DOOR, "south", "left");
        s.set(14, f + 2, z, Kit.BARS);
        s.set(11, f + 3, z + 1, Kit.wallTorch("south")).set(13, f + 3, z + 1, Kit.wallTorch("south"));
        // Beams under the ceiling and chandeliers.
        for (int bz : new int[]{6, 9}) {
            s.fill(CELLA_X1 + 1, TREASURY_ROOF - 1, bz, CELLA_X2 - 1, TREASURY_ROOF - 1, bz, Kit.log(STRIPPED_SPRUCE, "x"));
        }
        chandelier(s, 4, 8, TREASURY_ROOF - 2, TREASURY_ROOF - 1);
        chandelier(s, 12, 8, TREASURY_ROOF - 2, TREASURY_ROOF - 1);
        chandelier(s, MIDDLE, 8, TREASURY_ROOF - 3, TREASURY_ROOF - 1);
    }

    /**
     * Behind the counter: the vault with chests, barrels and gold behind its bars and a gate to the clerk; the clerk's
     * desk with its lectern; the minting room with the mint, the minter's post with a chest beside it, a blast furnace,
     * a barrel of ore, an anvil and a cauldron.
     */
    private static void treasuryBackRooms(Structure s) {
        int y = TREASURY_FLOOR + 1;
        // The vault.
        s.fill(6, y, 2, 6, TREASURY_ROOF - 1, 4, Kit.BARS);
        s.set(6, y, 3, gate("spruce", "east")).set(6, y + 1, 3, Structure.AIR);
        s.set(2, y, 2, Kit.chest("south")).set(3, y, 2, Kit.barrel()).set(4, y, 2, Kit.chest("south")).set(5, y, 2, Kit.chest("south"));
        s.set(2, y, 3, GOLD).set(2, y + 1, 3, GOLD).set(2, y, 4, "minecraft:raw_gold_block").set(3, y + 1, 2, Kit.barrel());
        s.set(5, y, 4, Kit.barrel()).set(4, y, 4, "minecraft:raw_iron_block");
        s.set(4, TREASURY_ROOF - 1, 3, Kit.hangingLantern());
        // The clerk.
        s.set(7, y, 2, BOOKSHELF).set(7, y + 1, 2, BOOKSHELF).set(MIDDLE, y, 2, lectern("south")).set(9, y, 2, Kit.barrel());
        s.set(9, y, 3, Kit.stairs(SPRUCE_STAIRS, "east")).set(9, y + 1, 2, Kit.lantern());
        s.set(MIDDLE, TREASURY_ROOF - 1, 3, Kit.hangingLantern());
        s.fill(10, y, 2, 10, TREASURY_ROOF - 1, 4, Kit.WALL);
        s.door(10, y, 3, SPRUCE_DOOR, "east", "left");
        // The minting room.
        s.set(11, y, 2, "minecraft:blast_furnace[facing=south,lit=false]").set(12, y, 2, Kit.barrel());
        s.set(13, y, 2, WORK_POST).set(14, y, 2, Kit.chest("south"));
        s.set(14, y, 3, "kingdomworld:mint").set(14, y, 4, "minecraft:cauldron");
        s.set(11, y, 4, "minecraft:anvil[facing=east]");
        s.set(12, TREASURY_ROOF - 1, 3, Kit.hangingLantern());
    }

    // ---------------------------------------------------------------- the courthouse

    /** The hall of justice: its walls and the top of them under the eaves. */
    private static final int COURT_X1 = 1;
    private static final int COURT_X2 = 11;
    private static final int COURT_Z1 = 1;
    private static final int COURT_Z2 = 13;
    private static final int COURT_EAVES = 12;
    /** The tower before the hall: its body, the clock, the belfry and the foot of the spire. */
    private static final int CLOCK_X1 = 4;
    private static final int CLOCK_X2 = 8;
    private static final int CLOCK_Z1 = 13;
    private static final int CLOCK_Z2 = 17;
    private static final int CLOCK_FACE = 18;
    private static final int BELFRY = 21;
    /** The guardhouse of the watch on the east side of the hall: its walls, the floor of its upper storey and its roof. */
    private static final int GUARD_X2 = 18;
    private static final int GUARD_Z1 = 4;
    private static final int GUARD_Z2 = 16;
    private static final int GUARD_UPPER = 6;
    private static final int GUARD_ROOF = 10;

    /**
     * The courthouse with the guardhouse of the watch. A tall hall of justice of stone under a steep dark roof, its sides
     * strong with buttresses between pointed windows, a rose window in its back gable. Before it rises the tower of the
     * court: the door in a pointed portal under the banners of the kingdom, a clock over it, an open belfry with
     * the bell, a cornice on corbels with four pinnacles and a slender spire of slate. On its east side the guardhouse
     * stands lower and rougher, a little fortress of rubble with a parapet of battlements, barred slits to its cells and
     * a shield over its own door.
     *
     * <p>Inside, the courtroom leads from the tower along a red carpet past the benches of the public and the bar to the
     * dais, where the judge sits at his high bench before the cloth of the kingdom; the dock behind its fences lies by the
     * door to the guardhouse. In the guardhouse a corridor runs along three cells behind iron bars, a ladder leads up to
     * the guard room with the barracks, four bunks of the watch, its racks and its table.
     */
    static Structure courthouse() {
        Structure s = new Structure(19, 35, 19);
        s.fill(0, 0, 0, 18, 0, 18, Kit.ROUGH);
        s.fill(5, 0, 18, 7, 0, 18, Kit.TRIM);
        courtHall(s);
        guardhouse(s);
        courtTower(s);
        courtInside(s);
        guardhouseInside(s);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The hall: walls of stone on a plinth of rough stone, buttresses of rough stone between tall pointed windows, the
     * steep roof of shingles with its gables of stone and a rose of glass in the back one.
     */
    private static void courtHall(Structure s) {
        s.fill(COURT_X1 + 1, 1, COURT_Z1 + 1, COURT_X2 - 1, 1, COURT_Z2 - 1, SPRUCE_PLANKS);
        s.walls(COURT_X1, 1, COURT_Z1, COURT_X2, COURT_EAVES, COURT_Z2, Kit.WALL);
        s.walls(COURT_X1, 1, COURT_Z1, COURT_X2, 1, COURT_Z2, Kit.ROUGH);
        quoins(s, COURT_X1, COURT_Z1, 1, 1, 1, COURT_EAVES);
        quoins(s, COURT_X1, COURT_Z2, 1, -1, 1, COURT_EAVES);
        quoins(s, COURT_X2, COURT_Z1, -1, 1, 1, COURT_EAVES);
        s.walls(COURT_X1, COURT_EAVES, COURT_Z1, COURT_X2, COURT_EAVES, COURT_Z2, Kit.TRIM);
        int[] under = roofZ(s, COURT_X1 - 1, COURT_X2 + 1, COURT_Z1 - 1, COURT_Z2 + 1, COURT_EAVES, 2, Kit.SHINGLE);
        gableAtZ(s, COURT_X1 - 1, under, COURT_Z1, COURT_EAVES + 1, Kit.WALL);
        gableAtZ(s, COURT_X1 - 1, under, COURT_Z2, COURT_EAVES + 1, Kit.WALL);
        // Pointed windows along the west side, buttresses between them.
        for (int zc : new int[]{3, 7, 11}) {
            lancetX(s, COURT_X1, -1, zc, 4, 8);
        }
        for (int z : new int[]{1, 5, 9, 13}) {
            s.fill(COURT_X1 - 1, 1, z, COURT_X1 - 1, 8, z, Kit.ROUGH);
            s.set(COURT_X1 - 1, 9, z, Kit.stairs(Kit.ROUGH_STAIRS, "east"));
        }
        s.set(COURT_X1 - 1, 5, 3, Kit.wallTorch("west")).set(COURT_X1 - 1, 5, 11, Kit.wallTorch("west"));
        // Windows of the front beside the tower and the clerestory over the guardhouse.
        for (int y = 4; y <= 8; y++) {
            s.set(2, y, COURT_Z2, Kit.GLASS).set(10, y, COURT_Z2, Kit.GLASS);
        }
        s.set(2, 3, COURT_Z2 + 1, Kit.topSlab(Kit.TRIM_SLAB)).set(10, 3, COURT_Z2 + 1, Kit.topSlab(Kit.TRIM_SLAB));
        s.set(2, 9, COURT_Z2, Kit.CARVED).set(10, 9, COURT_Z2, Kit.CARVED);
        // The back: two windows low, the rose of the gable over the cloth of the judge.
        for (int y = 4; y <= 7; y++) {
            s.set(3, y, COURT_Z1, Kit.GLASS).set(9, y, COURT_Z1, Kit.GLASS);
        }
        int rose = 16;
        s.fill(5, rose, COURT_Z1, 7, rose, COURT_Z1, Kit.stainedPane("yellow"));
        s.set(6, rose - 1, COURT_Z1, Kit.stainedPane("red")).set(6, rose + 1, COURT_Z1, Kit.stainedPane("red"));
        s.set(6, rose, COURT_Z1, Kit.stainedPane("orange"));
        s.set(5, rose - 1, COURT_Z1, Kit.TRIM).set(7, rose - 1, COURT_Z1, Kit.TRIM);
        s.set(5, rose + 1, COURT_Z1, Kit.TRIM).set(7, rose + 1, COURT_Z1, Kit.TRIM);
        s.fill(4, rose - 2, COURT_Z1 - 1, 8, rose - 2, COURT_Z1 - 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
    }

    /**
     * A tall pointed window in a wall that runs along z, three panes wide: the middle pane rises one higher between two
     * haunches of stone, a carved keystone over it, a sill of trim outside.
     *
     * @param out the way out of the building along x (-1 for a west wall, +1 for an east wall)
     */
    private static void lancetX(Structure s, int x, int out, int zc, int y1, int y2) {
        s.fill(x, y1, zc - 1, x, y2, zc + 1, Kit.GLASS);
        s.set(x, y2 + 1, zc - 1, Kit.upside(Kit.WALL_STAIRS, "north")).set(x, y2 + 1, zc + 1, Kit.upside(Kit.WALL_STAIRS, "south"));
        s.set(x, y2 + 1, zc, Kit.GLASS).set(x, y2 + 2, zc, Kit.CARVED);
        s.fill(x + out, y1 - 1, zc - 1, x + out, y1 - 1, zc + 1, Kit.topSlab(Kit.TRIM_SLAB));
    }

    /**
     * The tower of the court: a body of stone on rough stone with corners of trim, the door in a pointed portal with steps,
     * banners and lanterns, a clock on the front under a hood of trim and banners on the sides, the open belfry with the
     * bell, a cornice on corbels with a pinnacle on each corner, the spire of slate. A ladder climbs inside to the bell.
     */
    private static void courtTower(Structure s) {
        int top = BELFRY + 4;
        s.fill(CLOCK_X1, 1, CLOCK_Z1, CLOCK_X2, top, CLOCK_Z2, Kit.WALL);
        s.walls(CLOCK_X1, 1, CLOCK_Z1, CLOCK_X2, 2, CLOCK_Z2, Kit.ROUGH);
        s.fill(CLOCK_X1, 3, CLOCK_Z2, CLOCK_X1, BELFRY - 1, CLOCK_Z2, Kit.TRIM);
        s.fill(CLOCK_X2, 3, CLOCK_Z2, CLOCK_X2, BELFRY - 1, CLOCK_Z2, Kit.TRIM);
        s.fill(CLOCK_X1 + 1, 1, CLOCK_Z1 + 1, CLOCK_X2 - 1, 1, CLOCK_Z2 - 1, Kit.TRIM);
        s.fill(CLOCK_X1 + 1, 2, CLOCK_Z1 + 1, CLOCK_X2 - 1, BELFRY + 3, CLOCK_Z2 - 1, Structure.AIR);
        // String courses.
        for (int y : new int[]{7, CLOCK_FACE - 1, BELFRY}) {
            s.walls(CLOCK_X1, y, CLOCK_Z1, CLOCK_X2, y, CLOCK_Z2, Kit.TRIM);
        }
        // The portal: the door between jambs of trim under a pointed head, a light over it, steps before it.
        s.door(6, 2, CLOCK_Z2, SPRUCE_DOOR, "north", "left");
        s.set(6, 4, CLOCK_Z2, Kit.GLASS);
        s.fill(5, 2, CLOCK_Z2, 5, 4, CLOCK_Z2, Kit.TRIM).fill(7, 2, CLOCK_Z2, 7, 4, CLOCK_Z2, Kit.TRIM);
        s.set(5, 5, CLOCK_Z2, Kit.upside(Kit.TRIM_STAIRS, "west")).set(7, 5, CLOCK_Z2, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.set(6, 5, CLOCK_Z2, Kit.CARVED);
        s.fill(5, 6, CLOCK_Z2 + 1, 7, 6, CLOCK_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.fill(5, 1, CLOCK_Z2 + 1, 7, 1, CLOCK_Z2 + 1, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        s.set(4, 5, CLOCK_Z2 + 1, Kit.wallBanner("red", "south")).set(8, 5, CLOCK_Z2 + 1, Kit.wallBanner("red", "south"));
        s.set(4, 1, CLOCK_Z2 + 1, Kit.ROUGH).set(4, 2, CLOCK_Z2 + 1, Kit.lantern());
        s.set(8, 1, CLOCK_Z2 + 1, Kit.ROUGH).set(8, 2, CLOCK_Z2 + 1, Kit.lantern());
        // Lights up the tower: slits and torches.
        for (int y : new int[]{9, 13}) {
            s.fill(6, y, CLOCK_Z2, 6, y + 1, CLOCK_Z2, Kit.GLASS);
            s.fill(CLOCK_X1, y, 15, CLOCK_X1, y + 1, 15, Kit.GLASS).fill(CLOCK_X2, y, 15, CLOCK_X2, y + 1, 15, Kit.GLASS);
        }
        // The clock on the front: a white dial with a gold hub and black hands at three o'clock under a hood of trim; on the
        // sides the banners of the kingdom.
        int c = CLOCK_FACE + 1;
        s.fill(5, c - 1, CLOCK_Z2, 7, c + 1, CLOCK_Z2, "minecraft:white_concrete");
        s.set(6, c, CLOCK_Z2, GOLD).set(6, c + 1, CLOCK_Z2, "minecraft:black_concrete").set(7, c, CLOCK_Z2, "minecraft:black_concrete");
        s.fill(5, c + 2, CLOCK_Z2 + 1, 7, c + 2, CLOCK_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(CLOCK_X1 - 1, c + 1, 15, Kit.wallBanner("red", "west")).set(CLOCK_X2 + 1, c + 1, 15, Kit.wallBanner("red", "east"));
        // The belfry: an open arch on every side, the bell hanging from its ceiling, a lantern.
        s.fill(CLOCK_X1 + 1, BELFRY, CLOCK_Z1 + 1, CLOCK_X2 - 1, BELFRY, CLOCK_Z2 - 1, SPRUCE_PLANKS);
        for (int d = -1; d <= 1; d++) {
            for (int y = BELFRY + 1; y <= BELFRY + 2; y++) {
                s.set(6 + d, y, CLOCK_Z1, Structure.AIR).set(6 + d, y, CLOCK_Z2, Structure.AIR);
                s.set(CLOCK_X1, y, 15 + d, Structure.AIR).set(CLOCK_X2, y, 15 + d, Structure.AIR);
            }
        }
        int arch = BELFRY + 3;
        for (int z : new int[]{CLOCK_Z1, CLOCK_Z2}) {
            s.set(5, arch, z, Kit.upside(Kit.WALL_STAIRS, "west")).set(7, arch, z, Kit.upside(Kit.WALL_STAIRS, "east"));
            s.set(6, arch, z, Kit.CARVED);
        }
        for (int x : new int[]{CLOCK_X1, CLOCK_X2}) {
            s.set(x, arch, 14, Kit.upside(Kit.WALL_STAIRS, "north")).set(x, arch, 16, Kit.upside(Kit.WALL_STAIRS, "south"));
            s.set(x, arch, 15, Kit.CARVED);
        }
        s.fill(CLOCK_X1 + 1, arch, CLOCK_Z1 + 1, CLOCK_X2 - 1, arch, CLOCK_Z2 - 1, SPRUCE_PLANKS);
        s.set(6, arch - 1, 15, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        s.set(7, BELFRY + 1, 16, Kit.lantern());
        // The cornice on corbels, the pinnacles on its corners and the spire.
        int cornice = top;
        s.walls(CLOCK_X1, cornice, CLOCK_Z1, CLOCK_X2, cornice, CLOCK_Z2, Kit.TRIM);
        s.fill(CLOCK_X1, cornice, CLOCK_Z1 - 1, CLOCK_X2, cornice, CLOCK_Z1 - 1, Kit.upside(Kit.TRIM_STAIRS, "south"));
        s.fill(CLOCK_X1, cornice, CLOCK_Z2 + 1, CLOCK_X2, cornice, CLOCK_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.fill(CLOCK_X1 - 1, cornice, CLOCK_Z1, CLOCK_X1 - 1, cornice, CLOCK_Z2, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.fill(CLOCK_X2 + 1, cornice, CLOCK_Z1, CLOCK_X2 + 1, cornice, CLOCK_Z2, Kit.upside(Kit.TRIM_STAIRS, "west"));
        for (int[] p : new int[][]{{CLOCK_X1 - 1, CLOCK_Z1 - 1}, {CLOCK_X2 + 1, CLOCK_Z1 - 1}, {CLOCK_X1 - 1, CLOCK_Z2 + 1},
                {CLOCK_X2 + 1, CLOCK_Z2 + 1}}) {
            s.set(p[0], cornice, p[1], Kit.TRIM);
            s.fill(p[0], cornice + 1, p[1], p[0], cornice + 2, p[1], Kit.WALL_FENCE);
            s.set(p[0], cornice + 3, p[1], Kit.slab(Kit.SLATE.slab()));
        }
        int tip = needle(s, CLOCK_X1, CLOCK_Z1, CLOCK_X2, CLOCK_Z2, cornice + 1, 3, Kit.SLATE, SPRUCE_PLANKS);
        s.set(6, tip, 15, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        // The ladder up the back wall of the tower to the belfry, torches in the shaft.
        for (int y = 2; y <= BELFRY; y++) {
            s.set(5, y, CLOCK_Z1 + 1, Kit.ladder("south"));
        }
        s.set(7, 4, 16, Kit.wallTorch("north")).set(7, 11, 14, Kit.wallTorch("west")).set(5, 17, 16, Kit.wallTorch("north"));
        s.door(6, 2, CLOCK_Z1, SPRUCE_DOOR, "north", "left");
    }

    /**
     * The courtroom: the dais at the back with the high bench of the judge and the seats of his clerks before the cloth of
     * the kingdom, the dock behind its fences by the door to the guardhouse, a table for the pleaders, the bar with its
     * gate, benches for the public along the red carpet, tie beams with chandeliers.
     */
    private static void courtInside(Structure s) {
        int f = 2;
        // The dais and the high bench.
        s.fill(COURT_X1 + 1, 1, COURT_Z1 + 1, COURT_X2 - 1, 1, 4, Kit.TRIM);
        s.fill(COURT_X1 + 1, f, COURT_Z1 + 1, COURT_X2 - 1, f, 3, SPRUCE_PLANKS);
        s.fill(4, f, 4, 8, f, 4, SPRUCE_PLANKS);
        s.fill(4, f + 1, 4, 8, f + 1, 4, "minecraft:stripped_dark_oak_log[axis=x]");
        s.fill(2, f, 4, 3, f, 4, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        s.fill(9, f, 4, 10, f, 4, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        s.set(6, f + 1, 3, Kit.stairs(SPRUCE_STAIRS, "north"));
        s.fill(6, f + 1, 2, 6, f + 2, 2, "minecraft:stripped_dark_oak_log[axis=y]");
        s.set(4, f + 1, 3, Kit.stairs(SPRUCE_STAIRS, "north")).set(8, f + 1, 3, Kit.stairs(SPRUCE_STAIRS, "north"));
        s.set(5, f + 2, 4, Kit.lantern()).set(7, f + 2, 4, "minecraft:flower_pot");
        // The cloth of the kingdom behind the judge, a gold coin on it, banners on either side.
        s.fill(5, 5, COURT_Z1 + 1, 7, 9, COURT_Z1 + 1, Kit.CLOTH);
        s.set(6, 8, COURT_Z1 + 1, GOLD);
        s.fill(5, 10, COURT_Z1 + 1, 7, 10, COURT_Z1 + 1, Kit.log(DARK_LOG, "x"));
        s.set(3, 8, COURT_Z1 + 1, Kit.wallBanner("red", "south")).set(9, 8, COURT_Z1 + 1, Kit.wallBanner("red", "south"));
        // The dock by the door to the guardhouse.
        s.fill(8, f, 5, 8, f, 8, Kit.fence("spruce"));
        s.fill(9, f, 8, 10, f, 8, Kit.fence("spruce"));
        s.fill(9, f, 5, 10, f, 5, Kit.fence("spruce"));
        s.set(8, f, 7, gate("spruce", "east"));
        s.door(COURT_X2, f, 7, SPRUCE_DOOR, "west", "left");
        // The table of the pleaders on the west.
        s.fill(2, f, 6, 3, f, 6, Kit.topSlab(SPRUCE_SLAB));
        s.set(2, f, 7, Kit.stairs(SPRUCE_STAIRS, "south")).set(3, f, 7, Kit.stairs(SPRUCE_STAIRS, "south"));
        s.set(3, f + 1, 6, Kit.lantern()).set(2, f + 1, 6, "minecraft:flower_pot");
        s.set(4, f, 5, lectern("south"));
        // The bar with its gate, the benches of the public, the carpet.
        s.fill(2, f, 8, 5, f, 8, Kit.fence("spruce")).fill(7, f, 8, 7, f, 8, Kit.fence("spruce"));
        s.set(6, f, 8, gate("spruce", "north"));
        for (int z = 9; z <= 11; z++) {
            s.fill(2, f, z, 5, f, z, Kit.stairs(SPRUCE_STAIRS, "south"));
            s.fill(7, f, z, 10, f, z, Kit.stairs(SPRUCE_STAIRS, "south"));
        }
        s.fill(6, f, 5, 6, f, 12, Kit.CLOTH_CARPET);
        s.set(6, f, 8, gate("spruce", "north"));
        // Tie beams with chandeliers, torches on the walls.
        for (int z : new int[]{4, 8, 12}) {
            s.fill(COURT_X1 + 1, COURT_EAVES, z, COURT_X2 - 1, COURT_EAVES, z, Kit.log(DARK_LOG, "x"));
        }
        chandelier(s, 4, 8, 9, COURT_EAVES - 1);
        chandelier(s, 8, 8, 9, COURT_EAVES - 1);
        chandelier(s, 6, 4, 10, COURT_EAVES - 1);
        chandelier(s, 6, 12, 9, COURT_EAVES - 1);
        s.set(2, 5, 5, Kit.wallTorch("east")).set(2, 5, 9, Kit.wallTorch("east"));
        s.set(10, 5, 10, Kit.wallTorch("west")).set(10, 5, 3, Kit.wallTorch("west"));
    }

    /**
     * The guardhouse: walls of rubble with stone corners, a band of trim, a parapet of battlements with lanterns on its
     * corners over a cornice, barred slits to the cells, windows to the guard room, the door of the watch under a shield
     * between banners.
     */
    private static void guardhouse(Structure s) {
        int x1 = COURT_X2;
        s.fill(x1 + 1, 1, GUARD_Z1 + 1, GUARD_X2 - 1, 1, GUARD_Z2 - 1, SPRUCE_PLANKS);
        s.walls(x1, 1, GUARD_Z1, GUARD_X2, GUARD_ROOF - 1, GUARD_Z2, Kit.ROUGH);
        s.fill(x1, 1, GUARD_Z1, x1, COURT_EAVES, GUARD_Z2 - 3, Kit.WALL);
        for (int[] c : new int[][]{{GUARD_X2, GUARD_Z1}, {GUARD_X2, GUARD_Z2}, {x1, GUARD_Z2}}) {
            s.fill(c[0], 1, c[1], c[0], GUARD_ROOF - 1, c[1], Kit.WALL);
        }
        s.walls(x1, GUARD_UPPER, GUARD_Z1, GUARD_X2, GUARD_UPPER, GUARD_Z2, Kit.TRIM);
        s.fill(x1 + 1, GUARD_UPPER, GUARD_Z1 + 1, GUARD_X2 - 1, GUARD_UPPER, GUARD_Z2 - 1, SPRUCE_PLANKS);
        // The roof, its cornice and the battlements.
        s.walls(x1, GUARD_ROOF, GUARD_Z1, GUARD_X2, GUARD_ROOF, GUARD_Z2, Kit.TRIM);
        s.fill(x1 + 1, GUARD_ROOF, GUARD_Z1 + 1, GUARD_X2 - 1, GUARD_ROOF, GUARD_Z2 - 1, "minecraft:smooth_stone");
        s.fill(x1 + 1, GUARD_ROOF, GUARD_Z2 + 1, GUARD_X2, GUARD_ROOF, GUARD_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.fill(x1 + 2, GUARD_ROOF, GUARD_Z1 - 1, GUARD_X2, GUARD_ROOF, GUARD_Z1 - 1, Kit.upside(Kit.TRIM_STAIRS, "south"));
        Kit.battlements(s, x1 + 2, GUARD_Z1, GUARD_X2, GUARD_Z1, GUARD_ROOF + 1, Kit.WALL);
        Kit.battlements(s, x1, GUARD_Z2, GUARD_X2, GUARD_Z2, GUARD_ROOF + 1, Kit.WALL);
        Kit.battlements(s, GUARD_X2, GUARD_Z1, GUARD_X2, GUARD_Z2, GUARD_ROOF + 1, Kit.WALL);
        for (int[] c : new int[][]{{GUARD_X2, GUARD_Z1}, {GUARD_X2, GUARD_Z2}, {x1, GUARD_Z2}}) {
            s.set(c[0], GUARD_ROOF + 2, c[1], Kit.WALL).set(c[0], GUARD_ROOF + 3, c[1], Kit.lantern());
        }
        // Barred slits of the cells on the east, windows of the guard room.
        for (int z : new int[]{6, 9, 12}) {
            s.set(GUARD_X2, 4, z, Kit.BARS);
        }
        for (int z : new int[]{7, 10, 13}) {
            s.fill(GUARD_X2, 8, z, GUARD_X2, 8, z, Kit.GLASS);
        }
        for (int x : new int[]{13, 17}) {
            s.fill(x, 7, GUARD_Z2, x, 8, GUARD_Z2, Kit.GLASS);
        }
        s.fill(14, 8, GUARD_Z1, 15, 8, GUARD_Z1, Kit.GLASS);
        // The door of the watch: a shield over it, banners and lanterns beside it, a step.
        s.door(15, 2, GUARD_Z2, SPRUCE_DOOR, "north", "left");
        s.set(15, 4, GUARD_Z2, Kit.TRIM);
        s.set(14, 4, GUARD_Z2, Kit.upside(Kit.TRIM_STAIRS, "west")).set(16, 4, GUARD_Z2, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.set(15, 5, GUARD_Z2 + 1, shield("south"));
        s.set(14, 3, GUARD_Z2 + 1, Kit.wallTorch("south")).set(16, 3, GUARD_Z2 + 1, Kit.wallTorch("south"));
        s.set(13, 5, GUARD_Z2 + 1, Kit.wallBanner("red", "south")).set(17, 5, GUARD_Z2 + 1, Kit.wallBanner("red", "south"));
        s.set(15, 1, GUARD_Z2 + 1, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        s.set(GUARD_X2, 5, GUARD_Z1 - 1, Kit.wallTorch("north"));
        // The clerestory of the hall over the roof of the guardhouse.
        for (int z : new int[]{3, 7, 10}) {
            s.set(x1, GUARD_ROOF + 1, z, Kit.GLASS);
        }
    }

    /**
     * Inside the guardhouse: on the ground floor a corridor along three cells behind iron bars with iron doors, each with a
     * cot, a bucket and a torch, the desk of the watch with racks and the ladder; upstairs the guard room with the barracks,
     * four bunks with chests between them, racks of weapons and a table.
     */
    private static void guardhouseInside(Structure s) {
        int f = 2;
        // The cells along the east wall.
        s.fill(14, f, GUARD_Z1 + 1, 14, GUARD_UPPER - 1, 12, Kit.BARS);
        for (int z : new int[]{7, 10, 13}) {
            s.fill(15, f, z, GUARD_X2 - 1, GUARD_UPPER - 1, z, Kit.ROUGH);
        }
        for (int zc : new int[]{6, 9, 12}) {
            s.door(14, f, zc, "minecraft:iron_door", "west", "left");
            s.set(17, f, zc - 1, Kit.slab(SPRUCE_SLAB)).set(17, f, zc, Kit.slab(SPRUCE_SLAB));
            s.set(17, f + 1, zc - 1, "minecraft:white_carpet").set(17, f + 1, zc, "minecraft:light_gray_carpet");
            s.set(15, f, zc - 1, "minecraft:cauldron");
            s.set(16, f + 2, zc - 1, Kit.wallTorch("south"));
        }
        s.fill(GUARD_X2 - 1, f + 2, 6, GUARD_X2 - 1, GUARD_UPPER - 1, 6, Kit.chain());
        // The desk of the watch, racks, the ladder.
        s.set(13, f, 14, Kit.topSlab(SPRUCE_SLAB)).set(13, f + 1, 14, Kit.lantern());
        s.set(14, f, 14, Kit.stairs(SPRUCE_STAIRS, "east"));
        s.set(17, f, 14, grindstone("west")).set(17, f, 15, Kit.barrel()).set(16, f, 15, Kit.barrel());
        for (int y = f; y <= GUARD_UPPER; y++) {
            s.set(12, y, 15, Kit.ladder("east"));
        }
        s.set(13, 4, GUARD_Z1 + 1, Kit.wallTorch("south")).set(12, 4, 9, Kit.wallTorch("east"));
        s.set(16, 4, 15, Kit.wallTorch("north"));
        // The guard room upstairs.
        int u = GUARD_UPPER + 1;
        for (int z : new int[]{6, 8, 11, 13}) {
            s.bed(16, u, z, "minecraft:brown_bed", "east", 1, 0);
        }
        s.set(17, u, 7, Kit.chest("west")).set(17, u, 12, Kit.chest("west"));
        s.set(17, u, 9, Kit.barrel()).set(17, u, 10, Kit.barrel());
        s.set(17, u, 15, "kingdomworld:barracks[facing=west]");
        s.set(12, u, GUARD_Z1 + 1, grindstone("south")).set(13, u, GUARD_Z1 + 1, grindstone("south"));
        s.set(14, u, GUARD_Z1 + 1, Kit.fence("spruce")).set(15, u, GUARD_Z1 + 1, Kit.fence("spruce"));
        s.set(16, u, GUARD_Z1 + 1, Kit.barrel());
        s.set(12, u + 1, 9, shield("east")).set(12, u + 1, 11, shield("east"));
        s.set(14, u, 10, Kit.fence("spruce")).set(14, u + 1, 10, PLATE);
        s.set(13, u, 10, Kit.stairs(SPRUCE_STAIRS, "west")).set(15, u, 10, Kit.stairs(SPRUCE_STAIRS, "east"));
        s.set(14, GUARD_ROOF - 1, 8, Kit.hangingLantern()).set(14, GUARD_ROOF - 1, 13, Kit.hangingLantern());
        s.set(12, u + 1, 6, Kit.wallTorch("east"));
    }

    // ---------------------------------------------------------------- the infirmary

    /** The ward: its walls, the top plate under the eaves. */
    private static final int WARD_X1 = 1;
    private static final int WARD_X2 = 15;
    private static final int WARD_Z1 = 1;
    private static final int WARD_Z2 = 7;
    private static final int WARD_EAVES = 7;
    /** The dispensary before the west end of the ward, its gable to the street. */
    private static final int DISPENSARY_X2 = 7;
    private static final int DISPENSARY_Z2 = 11;

    /**
     * The infirmary: a long calm ward of white plaster in a light frame of stripped oak on a footing of rough stone, a row
     * of tall windows along both sides, under a roof of blue-green shingles with a glazed cupola on its ridge where a
     * lantern burns.
     * Before its west end the dispensary turns its gable to the street with the sign of the green cross in it, the white
     * and the lime banner on either side of its door and a lantern over it; before the east end lies the garden of herbs
     * behind a hedge, with beds of flowers and berry bushes, a path to the garden door of the ward, a bench and a lantern.
     *
     * <p>Inside, three infirmary posts stand down the middle of the ward with free floor round each and a lamp hanging over
     * it, eight cots of white and blue along the walls between white curtains; the dispensary holds shelves of books, a
     * desk with its lectern, brewing stands, a cauldron and barrels.
     */
    static Structure infirmary() {
        Structure s = new Structure(17, 18, 13);
        s.fill(0, 0, 0, 16, 0, 12, "minecraft:grass_block[snowy=false]");
        s.fill(WARD_X1, 0, WARD_Z1, WARD_X2, 0, WARD_Z2, Kit.ROUGH);
        s.fill(WARD_X1, 0, WARD_Z2, DISPENSARY_X2, 0, DISPENSARY_Z2, Kit.ROUGH);
        s.fill(3, 0, DISPENSARY_Z2 + 1, 5, 0, 12, Kit.TRIM);
        infirmaryWard(s);
        infirmaryDispensary(s);
        infirmaryRoofs(s);
        infirmaryInside(s);
        infirmaryGarden(s);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * A wall of light timber frame: a footing of rough stone, plaster, a top plate of stripped oak and posts of it at the
     * given places along the wall.
     *
     * @param alongX whether the wall runs along x (at z = {@code at}) or along z (at x = {@code at})
     */
    private static void frameWall(Structure s, boolean alongX, int at, int from, int to, int top, int[] posts) {
        for (int i = from; i <= to; i++) {
            int x = alongX ? i : at;
            int z = alongX ? at : i;
            s.set(x, 1, z, Kit.ROUGH);
            s.fill(x, 2, z, x, top - 1, z, Kit.PLASTER);
            s.set(x, top, z, Kit.log(STRIPPED_OAK, alongX ? "x" : "z"));
        }
        for (int p : posts) {
            int x = alongX ? p : at;
            int z = alongX ? at : p;
            s.fill(x, 2, z, x, top, z, Kit.log(STRIPPED_OAK, "y"));
        }
    }

    /**
     * The ward: walls of timber frame and plaster with tall windows between the posts on both sides, the door to the garden
     * with a step, the gables of plaster with a king post and a window.
     */
    private static void infirmaryWard(Structure s) {
        s.fill(WARD_X1 + 1, 1, WARD_Z1 + 1, WARD_X2 - 1, 1, WARD_Z2 - 1, SPRUCE_PLANKS);
        frameWall(s, true, WARD_Z1, WARD_X1, WARD_X2, WARD_EAVES, new int[]{1, 4, 8, 12, 15});
        frameWall(s, true, WARD_Z2, WARD_X1, WARD_X2, WARD_EAVES, new int[]{1, 7, 9, 11, 13, 15});
        frameWall(s, false, WARD_X1, WARD_Z1, WARD_Z2, WARD_EAVES, new int[]{1, 4, 7});
        frameWall(s, false, WARD_X2, WARD_Z1, WARD_Z2, WARD_EAVES, new int[]{1, 4, 7});
        for (int x : new int[]{2, 3, 6, 10, 13, 14}) {
            s.fill(x, 3, WARD_Z1, x, 6, WARD_Z1, Kit.GLASS);
            s.set(x, 2, WARD_Z1 - 1, Kit.topSlab(Kit.TRIM_SLAB));
        }
        for (int x : new int[]{10, 14}) {
            s.fill(x, 3, WARD_Z2, x, 6, WARD_Z2, Kit.GLASS);
            s.set(x, 2, WARD_Z2 + 1, Kit.topSlab(Kit.TRIM_SLAB));
        }
        for (int z : new int[]{2, 3, 5, 6}) {
            s.fill(WARD_X2, 3, z, WARD_X2, 5, z, Kit.GLASS);
            s.fill(WARD_X1, 3, z, WARD_X1, 5, z, Kit.GLASS);
        }
        // The door to the garden, a step down to the path, a lantern beside it.
        s.door(12, 2, WARD_Z2, SPRUCE_DOOR, "north", "left");
        s.set(12, 1, WARD_Z2 + 1, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        s.set(12, 4, WARD_Z2, Kit.TRIM);
        s.set(11, 4, WARD_Z2 + 1, Kit.wallTorch("south")).set(13, 4, WARD_Z2 + 1, Kit.wallTorch("south"));
    }

    /**
     * The dispensary before the west end of the ward: walls of the same frame, windows on its sides and front, its door
     * with a step, the white and the lime banner of the infirmary on either side of it and a lantern hanging over it.
     */
    private static void infirmaryDispensary(Structure s) {
        s.fill(WARD_X1 + 1, 1, WARD_Z2 + 1, DISPENSARY_X2 - 1, 1, DISPENSARY_Z2 - 1, SPRUCE_PLANKS);
        frameWall(s, true, DISPENSARY_Z2, WARD_X1, DISPENSARY_X2, WARD_EAVES, new int[]{1, 3, 5, 7});
        frameWall(s, false, WARD_X1, WARD_Z2, DISPENSARY_Z2, WARD_EAVES, new int[]{7, 11});
        frameWall(s, false, DISPENSARY_X2, WARD_Z2, DISPENSARY_Z2, WARD_EAVES, new int[]{7, 11});
        s.fill(WARD_X1, 3, 9, WARD_X1, 6, 9, Kit.GLASS).fill(DISPENSARY_X2, 3, 9, DISPENSARY_X2, 6, 9, Kit.GLASS);
        s.fill(2, 3, DISPENSARY_Z2, 2, 6, DISPENSARY_Z2, Kit.GLASS).fill(6, 3, DISPENSARY_Z2, 6, 6, DISPENSARY_Z2, Kit.GLASS);
        s.set(2, 2, DISPENSARY_Z2 + 1, Kit.topSlab(Kit.TRIM_SLAB)).set(6, 2, DISPENSARY_Z2 + 1, Kit.topSlab(Kit.TRIM_SLAB));
        s.set(0, 2, 9, Kit.topSlab(Kit.TRIM_SLAB)).set(8, 2, 9, Kit.topSlab(Kit.TRIM_SLAB));
        s.door(4, 2, DISPENSARY_Z2, SPRUCE_DOOR, "north", "left");
        s.set(4, 4, DISPENSARY_Z2, Kit.TRIM);
        s.set(4, 1, DISPENSARY_Z2 + 1, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        s.set(3, 4, DISPENSARY_Z2 + 1, Kit.wallBanner("white", "south")).set(5, 4, DISPENSARY_Z2 + 1, Kit.wallBanner("lime", "south"));
        s.set(4, 5, DISPENSARY_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(4, 4, DISPENSARY_Z2 + 1, Kit.hangingLantern());
        // The opening from the dispensary into the ward.
        s.fill(4, 2, WARD_Z2, 4, 3, WARD_Z2, Structure.AIR);
        s.set(4, 4, WARD_Z2, Kit.TRIM);
    }

    /**
     * The roofs: the steep roof of the ward with its gables of plaster, the roof of the dispensary running into it with the
     * sign of the green cross in its gable, the glazed cupola on the ridge of the ward with a lantern in it.
     */
    private static void infirmaryRoofs(Structure s) {
        int[] ward = roofX(s, WARD_X1 - 1, WARD_X2 + 1, WARD_Z1 - 1, WARD_Z2 + 1, WARD_EAVES, 1, Kit.TEAL);
        gableAtX(s, WARD_Z1 - 1, ward, WARD_X1, WARD_EAVES + 1, Kit.PLASTER);
        gableAtX(s, WARD_Z1 - 1, ward, WARD_X2, WARD_EAVES + 1, Kit.PLASTER);
        for (int x : new int[]{WARD_X1, WARD_X2}) {
            s.fill(x, WARD_EAVES + 1, 4, x, ward[4] - 1, 4, Kit.log(STRIPPED_OAK, "y"));
            s.set(x, WARD_EAVES + 2, 3, Kit.GLASS).set(x, WARD_EAVES + 2, 5, Kit.GLASS);
        }
        int[] wing = roofZ(s, WARD_X1 - 1, DISPENSARY_X2 + 1, WARD_Z2, DISPENSARY_Z2 + 1, WARD_EAVES, 1, Kit.TEAL);
        gableAtZ(s, WARD_X1 - 1, wing, DISPENSARY_Z2, WARD_EAVES + 1, Kit.PLASTER);
        // The ridge of the dispensary runs on into the slope of the ward, as far as it stands above it.
        for (int z = WARD_Z2 - 1; z > (WARD_Z1 + WARD_Z2) / 2; z--) {
            int wardTop = s.top(WARD_X1 + 2, z);
            for (int i = 0; i < wing.length; i++) {
                int x = WARD_X1 - 1 + i;
                int top = s.top(x, WARD_Z2);
                if (top <= wardTop) {
                    continue;
                }
                s.set(x, top, z, s.get(x, top, WARD_Z2));
                for (int y = wardTop + 1; y < top; y++) {
                    if (!s.solid(x, y, z)) {
                        s.set(x, y, z, SPRUCE_PLANKS);
                    }
                }
            }
        }
        // The ceiling of the dispensary, the space under its roof filled up to the slopes.
        s.fill(WARD_X1 + 1, WARD_EAVES, WARD_Z2 + 1, DISPENSARY_X2 - 1, WARD_EAVES, DISPENSARY_Z2 - 1, SPRUCE_PLANKS);
        for (int x = WARD_X1; x <= DISPENSARY_X2; x++) {
            for (int z = WARD_Z2; z <= DISPENSARY_Z2; z++) {
                for (int y = WARD_EAVES + 1; y < wing[x - WARD_X1 + 1]; y++) {
                    if (!s.solid(x, y, z)) {
                        s.set(x, y, z, SPRUCE_PLANKS);
                    }
                }
            }
        }

        // The sign of the green cross on white in the gable of the dispensary.
        int sign = DISPENSARY_Z2 + 1;
        s.fill(3, 8, sign, 5, 10, sign, "minecraft:white_wool");
        s.fill(3, 9, sign, 5, 9, sign, "minecraft:lime_wool");
        s.fill(4, 8, sign, 4, 10, sign, "minecraft:lime_wool");
        s.fill(3, 7, sign, 5, 7, sign, Kit.upside(Kit.TRIM_STAIRS, "north"));
        // The cupola on the ridge: a base of trim, posts of oak, glass between, a lantern, a little tented roof of slate.
        int base = ward[4];
        s.fill(7, base, 3, 9, base, 5, Kit.TRIM);
        for (int[] p : new int[][]{{7, 3}, {9, 3}, {7, 5}, {9, 5}}) {
            s.fill(p[0], base + 1, p[1], p[0], base + 2, p[1], Kit.log(STRIPPED_OAK, "y"));
        }
        s.fill(8, base + 1, 3, 8, base + 2, 3, Kit.GLASS).fill(8, base + 1, 5, 8, base + 2, 5, Kit.GLASS);
        s.fill(7, base + 1, 4, 7, base + 2, 4, Kit.GLASS).fill(9, base + 1, 4, 9, base + 2, 4, Kit.GLASS);
        s.set(8, base + 2, 4, Kit.hangingLantern());
        s.hipRoof(7, 9, 3, 5, base + 3, Kit.SLATE.stairs(), Kit.SLATE.slab());
        s.set(8, base + 3, 4, Kit.SLATE.block());
        s.set(8, base + 5, 4, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    /**
     * Inside the ward: three infirmary posts down the middle with a lamp over each, eight cots of a slab with a white
     * pillow and a blue blanket along the walls, white curtains standing between them, tie beams; inside the dispensary
     * the shelves, the desk, the brewing stands, the cauldron and the barrels.
     */
    private static void infirmaryInside(Structure s) {
        int f = 2;
        for (int x : new int[]{5, 9, 13}) {
            s.set(x, f, 4, "kingdomworld:infirmary");
            s.fill(x, WARD_EAVES, WARD_Z1 + 1, x, WARD_EAVES, WARD_Z2 - 1, Kit.log(STRIPPED_OAK, "z"));
            s.set(x, WARD_EAVES - 1, 4, Kit.hangingLantern());
        }
        for (int x : new int[]{2, 6, 9, 13}) {
            for (int z : new int[]{2, 6}) {
                s.set(x, f, z, Kit.slab(SPRUCE_SLAB)).set(x + 1, f, z, Kit.slab(SPRUCE_SLAB));
                boolean westHead = x < MIDDLE;
                s.set(westHead ? x : x + 1, f + 1, z, "minecraft:white_carpet");
                s.set(westHead ? x + 1 : x, f + 1, z, "minecraft:light_blue_carpet");
            }
        }
        // Curtains standing between the cots, a chest and a cauldron of clean water by the walls.
        for (int x : new int[]{4, 8, 11}) {
            s.set(x, f, 2, Kit.banner("white", 0));
        }
        s.set(8, f, 6, Kit.banner("white", 8)).set(12, f, 2, Kit.chest("south"));
        s.set(5, f, 2, "minecraft:cauldron");
        s.set(2, 4, 4, Kit.wallTorch("east")).set(14, 4, 4, Kit.wallTorch("west"));
        // The dispensary.
        s.fill(2, f, 8, 2, f + 1, 8, BOOKSHELF).fill(2, f, 10, 2, f + 1, 10, BOOKSHELF);
        s.set(2, f, 9, lectern("east")).set(3, f, 9, Kit.stairs(SPRUCE_STAIRS, "east"));
        s.set(6, f, 8, "minecraft:cauldron");
        s.set(6, f, 9, Kit.topSlab(SPRUCE_SLAB)).set(6, f + 1, 9, "minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]");
        s.set(6, f, 10, Kit.barrel()).set(6, f + 1, 10, "minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]");
        s.set(5, f, 8, Kit.barrel()).set(2, f + 2, 8, Kit.lantern());
        s.set(4, WARD_EAVES - 1, 9, Kit.hangingLantern());
        s.fill(3, f, 10, 3, f, 10, HAY);
    }

    /**
     * The garden of herbs: grass in a hedge of leaves with a gap for the path, a path of trodden earth from the gap to the
     * garden door of the ward, beds of herbs and flowers and berry bushes, a bench against the ward, a composter and a
     * lantern on a post.
     */
    private static void infirmaryGarden(Structure s) {
        int x1 = DISPENSARY_X2 + 2;
        s.fill(x1, 0, 12, 16, 0, 12, "minecraft:dirt").fill(16, 0, WARD_Z2 + 1, 16, 0, 11, "minecraft:dirt");
        s.fill(x1, 1, 12, 16, 1, 12, Kit.leaves("oak"));
        s.fill(16, 1, WARD_Z2 + 1, 16, 1, 11, Kit.leaves("oak"));
        s.set(12, 1, 12, Structure.AIR);
        s.fill(12, 0, WARD_Z2 + 2, 12, 0, 12, "minecraft:dirt_path");
        s.fill(x1, 0, 10, 15, 0, 10, "minecraft:dirt_path");
        String[] herbs = {"minecraft:allium", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:azure_bluet",
                "minecraft:lily_of_the_valley", "minecraft:poppy", "minecraft:dandelion", "minecraft:fern"};
        int i = 0;
        for (int z = 9; z <= 11; z++) {
            for (int x = x1; x <= 15; x++) {
                if (x == 12 || z == 10) {
                    continue;
                }
                s.set(x, 1, z, herbs[i++ % herbs.length]);
            }
        }
        s.set(9, 1, 11, "minecraft:sweet_berry_bush[age=3]").set(15, 1, 11, "minecraft:sweet_berry_bush[age=3]");
        s.set(14, 1, 9, "minecraft:sweet_berry_bush[age=2]");
        s.set(9, 1, WARD_Z2 + 1, Kit.stairs(SPRUCE_STAIRS, "north")).set(10, 1, WARD_Z2 + 1, Kit.stairs(SPRUCE_STAIRS, "north"));
        s.set(15, 1, WARD_Z2 + 1, "minecraft:composter[level=3]");
        s.set(11, 1, WARD_Z2 + 1, Kit.fence("oak")).set(11, 2, WARD_Z2 + 1, Kit.lantern());
        s.set(0, 1, 12, Kit.fence("oak")).set(0, 2, 12, Kit.lantern());
        s.set(7, 1, 12, Kit.fence("oak")).set(7, 2, 12, Kit.lantern());
        s.set(4, 4, WARD_Z1 - 1, Kit.wallTorch("north")).set(12, 4, WARD_Z1 - 1, Kit.wallTorch("north"));
    }
}
