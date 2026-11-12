package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The town of the style "eastern" (see {@link Eastern}): its houses, from the board shack of the poor to the walled
 * residence of a noble family with its gate and pond, its smithy, its inn and a piece of its stone wall with the gate tower.
 * All of them stand on footings or platforms of stone, their frames of dark timber hold walls of white plaster, boards and
 * sliding screens of paper, and their roofs curve up at the eaves; the richer the house, the deeper its eaves, the more of
 * them it has and the more red lacquer it shows. Every room is furnished for those who live or work there and lit.
 */
final class EasternTown {
    private static final String POST_Y = Kit.log(Eastern.POST, "y");
    private static final String PLASTER = Eastern.PLASTER;
    private static final String PLANKS = Eastern.PLANKS;
    private static final String FLOOR = Eastern.FLOOR;
    private static final String PAPER = Eastern.PAPER;
    private static final String FENCE = Eastern.FENCE;
    private static final String ROUGH = Eastern.ROUGH;
    private static final String STONE = Eastern.STONE;
    private static final String TRIM = Eastern.TRIM;
    private static final Kit.Roof ROOF = Eastern.ROOF;
    /** A roof of boards (timber): the cheap roof of the poor, the wood of the place. */
    private static final Kit.Roof BOARDS = new Kit.Roof(Eastern.PLANKS, Eastern.STAIRS, Eastern.SLAB);
    /** Thatch of straw: bales of hay, a fixed accent, so that the farmhouse reads yellow among the dark tiles. */
    private static final String THATCH = "minecraft:hay_block[axis=y]";
    /** The beaten earth of an entry hall and a kitchen floor: packed mud, a fixed accent. */
    private static final String EARTH = "minecraft:packed_mud";

    private EasternTown() {
    }

    // ---------------------------------------------------------------- the plans of the houses

    /**
     * The houses the house program draws: their names, their ground and their beds.
     */
    enum Plan {
        SHACK("shack", 5, 7, 1, "minecraft:spruce_planks"),
        FARMER("farmer", 11, 9, 2, "minecraft:hay_block"),
        TOWN("town", 9, 13, 2, "minecraft:white_stained_glass_pane"),
        MERCHANT("merchant", 11, 13, 3, "minecraft:red_nether_brick_wall"),
        GARDEN("garden", 11, 15, 2, "minecraft:andesite_wall"),
        NOBLE("noble", 13, 15, 4, "minecraft:red_concrete");

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
     * The town buildings of the style: the six houses of the house program, the smithy, the inn and the piece of the town
     * wall with its gate.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Plan plan : Plan.values()) {
            Structure s = house(plan);
            list.add(new BuildingGenerator.Building("eastern_house_" + plan.key, "housing", "house", plan.icon, (s.sizeX - 1) / 2, 0,
                    s.sizeZ - 1, s, "house", 1, ""));
        }
        Structure smithy = smithy();
        list.add(new BuildingGenerator.Building("eastern_smithy", "production", "workshop", "minecraft:anvil", (smithy.sizeX - 1) / 2, 0,
                smithy.sizeZ - 1, smithy, "workshop", 1, "").at("door", 6, 1, 9));
        Structure tavern = tavern();
        list.add(new BuildingGenerator.Building("eastern_tavern", "trade", "tavern", "minecraft:barrel", (tavern.sizeX - 1) / 2, 0,
                tavern.sizeZ - 1, tavern, "tavern", 1, "").at("door", 6, 1, 10));
        Structure gate = wallGate();
        list.add(new BuildingGenerator.Building("eastern_wall_gate", "defense", "gatehouse", "minecraft:red_concrete", (gate.sizeX - 1) / 2, 0,
                gate.sizeZ - 1, gate, "tower", 1, "")
                .at("door", 11, 0, 10)
                .at("castle_gate", 10, 1, 5).at("castle_gate", 12, 1, 5)
                .at("castle_wall", 3, 6, 5).at("castle_wall", 19, 6, 5));
        return list;
    }

    /**
     * The house program: draws the house of a plan.
     */
    static Structure house(Plan plan) {
        return switch (plan) {
            case SHACK -> shack();
            case FARMER -> farmer();
            case TOWN -> townHouse();
            case MERCHANT -> merchant();
            case GARDEN -> gardenHouse();
            case NOBLE -> noble();
        };
    }

    // ---------------------------------------------------------------- drawing inside the box

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

    private static boolean solid(Structure s, int x, int y, int z) {
        return s.inside(x, y, z) && s.solid(x, y, z);
    }

    // ---------------------------------------------------------------- shared shapes

    /** The lowest layer a piece of a curved roof of surface height {@code h} and rise {@code rise} takes. */
    private static int pieceBottom(int y, int h, int rise) {
        if (rise <= 1) {
            return h % 2 == 1 ? y + (h - 1) / 2 : y + h / 2 - 1;
        }
        return y + (h + 1) / 2 - 1 - (rise / 2 - 1);
    }

    /**
     * One piece of a curved roof whose surface stands {@code h} half blocks above the eaves at layer {@code y}: a slab where
     * it rises half a block, a stair where it rises one, full blocks under a stair where it rises more.
     */
    private static void piece(Structure s, int x, int y, int z, int h, int rise, String stair, Kit.Roof roof) {
        if (rise <= 1) {
            put(s, x, h % 2 == 1 ? y + (h - 1) / 2 : y + h / 2 - 1, z, h % 2 == 1 ? Kit.slab(roof.slab()) : Kit.topSlab(roof.slab()));
            return;
        }
        int top = y + (h + 1) / 2 - 1;
        for (int k = 1; k < rise / 2; k++) {
            put(s, x, top - k, z, roof.block());
        }
        put(s, x, top, z, stair);
    }

    /**
     * A curved gable roof over the rectangle of its eaves {@code x1..x2, z1..z2} whose ridge runs along x (or along z when
     * {@code ridgeX} is false) whatever the proportions of the rectangle (an odd width across the ridge): rows rising from
     * both eaves as {@link Eastern#heights} says, a ridge of full blocks with horns at its ends, gables one block inside the
     * ends filled with {@code gable} (white plaster gets a king post of timber) from the layer {@code gableFrom} up, and the
     * four corners swept up by {@code lift}. The roof of town houses, whose long slope looks at the street however deep the
     * house is, and of gates whose gable looks at the street however wide they are.
     *
     * @return the layer of the ridge
     */
    private static int rowRoof(Structure s, int x1, int z1, int x2, int z2, int y, int pitch, int lift, Kit.Roof roof,
                               String gable, int gableFrom, boolean ridgeX) {
        int a1 = ridgeX ? x1 : z1;
        int a2 = ridgeX ? x2 : z2;
        int c1 = ridgeX ? z1 : x1;
        int c2 = ridgeX ? z2 : x2;
        int rings = (c2 - c1) / 2;
        int[] h = Eastern.heights(rings, pitch);
        String up1 = ridgeX ? "south" : "east";
        String up2 = ridgeX ? "north" : "west";
        for (int k = 0; k < rings; k++) {
            int rise = h[k] - (k == 0 ? 0 : h[k - 1]);
            for (int a = a1; a <= a2; a++) {
                piece(s, ridgeX ? a : c1 + k, y, ridgeX ? c1 + k : a, h[k], rise, Kit.stairs(roof.stairs(), up1), roof);
                piece(s, ridgeX ? a : c2 - k, y, ridgeX ? c2 - k : a, h[k], rise, Kit.stairs(roof.stairs(), up2), roof);
            }
        }
        int top = y + (h[rings - 1] + 1) / 2;
        int mc = c1 + rings;
        if (gable != null) {
            for (int a : new int[]{a1 + 1, a2 - 1}) {
                for (int c = c1 + 1; c <= c2 - 1; c++) {
                    int k = Math.min(c - c1, c2 - c);
                    int under = k >= rings ? top - 2 : pieceBottom(y, h[k], h[k] - (k == 0 ? 0 : h[k - 1])) - 1;
                    for (int yy = gableFrom; yy <= under; yy++) {
                        int px = ridgeX ? a : c;
                        int pz = ridgeX ? c : a;
                        if (!solid(s, px, yy, pz)) {
                            put(s, px, yy, pz, gable.equals(PLASTER) && c == mc ? POST_Y : gable);
                        }
                    }
                }
            }
        }
        for (int a = a1; a <= a2; a++) {
            put(s, ridgeX ? a : mc, top - 1, ridgeX ? mc : a, roof.block());
            put(s, ridgeX ? a : mc, top, ridgeX ? mc : a, roof.block());
        }
        put(s, ridgeX ? a1 : mc, top + 1, ridgeX ? mc : a1, Kit.stairs(roof.stairs(), ridgeX ? "west" : "north"));
        put(s, ridgeX ? a2 : mc, top + 1, ridgeX ? mc : a2, Kit.stairs(roof.stairs(), ridgeX ? "east" : "south"));
        if (rings >= 5 && a2 - a1 >= 6) {
            put(s, ridgeX ? a1 + 1 : mc, top + 1, ridgeX ? mc : a1 + 1, Kit.slab(roof.slab()));
            put(s, ridgeX ? a2 - 1 : mc, top + 1, ridgeX ? mc : a2 - 1, Kit.slab(roof.slab()));
        }
        if (lift > 0) {
            Eastern.sweep(s, x1, z1, x2, z2, y, lift, roof, !ridgeX, ridgeX);
        }
        return top;
    }

    /**
     * A pent roof (hisashi) along the face of a wall that runs along x and looks the way of {@code facing} (north or
     * south), from {@code x1} to {@code x2} at layer {@code y}: a row of stairs against the wall and a row of slabs before
     * it, its two ends turned up, and brackets hanging under it at both ends.
     *
     * @param z the row of the wall
     */
    private static void pent(Structure s, int x1, int x2, int z, int y, String facing) {
        int d = facing.equals("south") ? 1 : -1;
        String back = Furnish.opposite(facing);
        for (int x = x1; x <= x2; x++) {
            put(s, x, y, z + d, Kit.stairs(ROOF.stairs(), back));
            put(s, x, y, z + 2 * d, Kit.slab(ROOF.slab()));
        }
        put(s, x1, y, z + 2 * d, Kit.stairs(ROOF.stairs(), "west"));
        put(s, x2, y, z + 2 * d, Kit.stairs(ROOF.stairs(), "east"));
        put(s, x1, y - 1, z + d, Kit.upside(Eastern.STAIRS, back));
        put(s, x2, y - 1, z + d, Kit.upside(Eastern.STAIRS, back));
    }

    /**
     * A stair chest (kaidan-dansu): steps of timber rising from (x, y, z) one layer for every block the way of
     * (dx, dz) up to the floor of the storey above at layer {@code floor}, each carried on drawers of stripped wood that fill
     * the space under it. It opens the floor above over the steps that need head room.
     */
    private static void stairChest(Structure s, int x, int y, int z, int dx, int dz, int floor) {
        String up = dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
        int n = floor - y + 1;
        for (int i = 0; i < n; i++) {
            int px = x + dx * i;
            int pz = z + dz * i;
            put(s, px, y + i, pz, Kit.stairs(Eastern.FLOOR_STAIRS, up));
            for (int yy = y; yy < y + i; yy++) {
                Furnish.crate(s, px, yy, pz, "spruce");
            }
            if (i < n - 1 && y + i + 2 >= floor) {
                put(s, px, floor, pz, Structure.AIR);
            }
        }
    }

    /** Bolts of cloth on the shelves of a shop: wool of a few fixed colours, stacked. */
    private static void bolts(Structure s, int x, int y, int z, int high, int seed) {
        String[] colours = {"light_blue", "white", "yellow", "cyan", "pink", "lime"};
        for (int i = 0; i < high; i++) {
            put(s, x, y + i, z, "minecraft:" + colours[Math.floorMod(seed + i * 2, colours.length)] + "_wool");
        }
    }

    /**
     * A lower roof (a ledge of tiles) around the walls of a storey {@code x1..x2, z1..z2} whose head beam lies at layer
     * {@code y}, the walls of the storey above going on up from them: a ring of stairs one block out at {@code y + 1}
     * leaning on the wall, a ring of slabs two blocks out at {@code y}, its four corners swept up.
     */
    private static void ledge(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1 - 2; x <= x2 + 2; x++) {
            put(s, x, y, z1 - 2, Kit.slab(ROOF.slab()));
            put(s, x, y, z2 + 2, Kit.slab(ROOF.slab()));
        }
        for (int z = z1 - 2; z <= z2 + 2; z++) {
            put(s, x1 - 2, y, z, Kit.slab(ROOF.slab()));
            put(s, x2 + 2, y, z, Kit.slab(ROOF.slab()));
        }
        for (int x = x1; x <= x2; x++) {
            put(s, x, y + 1, z1 - 1, Kit.stairs(ROOF.stairs(), "south"));
            put(s, x, y + 1, z2 + 1, Kit.stairs(ROOF.stairs(), "north"));
            put(s, x, y, z1 - 1, Kit.topSlab(ROOF.slab()));
            put(s, x, y, z2 + 1, Kit.topSlab(ROOF.slab()));
        }
        for (int z = z1; z <= z2; z++) {
            put(s, x1 - 1, y + 1, z, Kit.stairs(ROOF.stairs(), "east"));
            put(s, x2 + 1, y + 1, z, Kit.stairs(ROOF.stairs(), "west"));
            put(s, x1 - 1, y, z, Kit.topSlab(ROOF.slab()));
            put(s, x2 + 1, y, z, Kit.topSlab(ROOF.slab()));
        }
        put(s, x1 - 1, y + 1, z1 - 1, Kit.stairs(ROOF.stairs(), "south", "outer_left"));
        put(s, x2 + 1, y + 1, z1 - 1, Kit.stairs(ROOF.stairs(), "south", "outer_right"));
        put(s, x1 - 1, y + 1, z2 + 1, Kit.stairs(ROOF.stairs(), "north", "outer_right"));
        put(s, x2 + 1, y + 1, z2 + 1, Kit.stairs(ROOF.stairs(), "north", "outer_left"));
        for (int[] c : new int[][]{{x1 - 1, z1 - 1}, {x2 + 1, z1 - 1}, {x1 - 1, z2 + 1}, {x2 + 1, z2 + 1}}) {
            put(s, c[0], y, c[1], Kit.topSlab(ROOF.slab()));
        }
        Eastern.sweep(s, x1 - 2, z1 - 2, x2 + 2, z2 + 2, y, 1, ROOF, true, true);
    }

    /**
     * A thatched roof of straw over the rectangle of its eaves, its ridge along x: rings of bales one block in and one layer
     * up from the eaves at layer {@code y}, as steep as thatch must be; the first {@code hip} rings slope on all four sides,
     * above them small gables of boards with a smoke vent of lattice stand {@code hip} blocks inside the ends. The ridge is
     * bound under a saddle of the roof's tiles with logs laid across it every second block, and its ends turn up in horns.
     *
     * @return the layer of the ridge
     */
    private static int thatch(Structure s, int x1, int z1, int x2, int z2, int y, int hip) {
        int rings = (z2 - z1) / 2;
        int from = x1 + hip - 1;
        int to = x2 - hip + 1;
        // the eave ring is one layer thick, every ring above it two: thatch is steep
        for (int k = 0; k < rings; k++) {
            int lo = k == 0 ? y : y + 2 * k - 1;
            int hi = k == 0 ? y : y + 2 * k;
            for (int yy = lo; yy <= hi; yy++) {
                if (k < hip) {
                    for (int x = x1 + k; x <= x2 - k; x++) {
                        put(s, x, yy, z1 + k, THATCH);
                        put(s, x, yy, z2 - k, THATCH);
                    }
                    for (int z = z1 + k; z <= z2 - k; z++) {
                        put(s, x1 + k, yy, z, THATCH);
                        put(s, x2 - k, yy, z, THATCH);
                    }
                } else {
                    for (int x = from; x <= to; x++) {
                        put(s, x, yy, z1 + k, THATCH);
                        put(s, x, yy, z2 - k, THATCH);
                    }
                }
            }
        }
        int zc = (z1 + z2) / 2;
        int top = y + 2 * rings - 1;
        // the gables over the hips, with a vent of lattice under the ridge
        for (int x : new int[]{x1 + hip, x2 - hip}) {
            for (int yy = y + 1; yy < top; yy++) {
                int k = (yy - y + 1) / 2;
                for (int z = z1 + k + 1; z <= z2 - k - 1; z++) {
                    if (!solid(s, x, yy, z)) {
                        put(s, x, yy, z, yy >= top - 2 && z == zc ? FENCE : PLANKS);
                    }
                }
            }
        }
        // the saddle of tiles over the ridge, the logs across it and the horns at its ends
        for (int x = from; x <= to; x++) {
            put(s, x, top, zc, ROOF.block());
            put(s, x, top, zc - 1, Kit.stairs(ROOF.stairs(), "south"));
            put(s, x, top, zc + 1, Kit.stairs(ROOF.stairs(), "north"));
            boolean log = x > from && x < to && (x - from) % 2 == 0;
            if (log) {
                fill(s, x, top + 1, zc - 1, x, top + 1, zc + 1, Kit.log(Eastern.POST, "z"));
            } else if (x > from && x < to) {
                put(s, x, top + 1, zc, Kit.slab(ROOF.slab()));
            }
        }
        put(s, from, top + 1, zc, Kit.stairs(ROOF.stairs(), "west"));
        put(s, to, top + 1, zc, Kit.stairs(ROOF.stairs(), "east"));
        return top;
    }

    // ---------------------------------------------------------------- the shack

    /**
     * A shack of the poor: a little box of boards between dark posts on a sill of logs, under a low roof of boards whose
     * ends reach out over the door and the back wall. In the gable to the street the door, a paper window and a slatted one;
     * firewood and a rain tub under the overhang. Inside, under a ceiling of boards: a bed on the floor, a little sunken
     * hearth with a lantern over it, the chest, a sack of rice and a shelf with a candle.
     */
    static Structure shack() {
        Structure s = new Structure(5, 9, 7);
        fill(s, 0, 0, 1, 4, 0, 5, FLOOR);
        Eastern.frame(s, 0, 1, 4, 5, 0, 4, 2, POST_Y, PLANKS);
        // the frame stands on stones; above the boards the panels are of daubed mud
        for (int x = 0; x <= 4; x++) {
            for (int z = 1; z <= 5; z++) {
                if (x == 0 || x == 4 || z == 1 || z == 5) {
                    put(s, x, 0, z, ROUGH);
                    for (int y = 2; y <= 3; y++) {
                        if (!s.get(x, y, z).equals(POST_Y)) {
                            put(s, x, y, z, EARTH);
                        }
                    }
                }
            }
        }
        fill(s, 1, 4, 2, 3, 4, 4, PLANKS);
        int ridge = Eastern.roof(s, 0, 0, 4, 6, 5, Eastern.GABLE, 0, 0, BOARDS, PLANKS);
        // no horns on a roof of boards, but stones laid on it against the wind
        put(s, 2, ridge + 1, 0, Structure.AIR);
        put(s, 2, ridge + 1, 6, Structure.AIR);
        for (int[] p : new int[][]{{1, 1}, {1, 4}, {3, 2}, {3, 5}, {0, 3}, {4, 0}}) {
            put(s, p[0], p[0] == 0 || p[0] == 4 ? 6 : 6, p[1], p[0] == 0 || p[0] == 4 ? Kit.slab(Eastern.ROUGH_SLAB) : Kit.ROUGH_FENCE);
        }
        // the front: the door, a paper window and a slatted one
        s.door(2, 1, 5, Eastern.DOOR, "north", "left");
        put(s, 1, 2, 5, PAPER);
        put(s, 3, 2, 5, FENCE);
        put(s, 0, 2, 4, PAPER);
        put(s, 4, 2, 2, PAPER);
        put(s, 2, 0, 6, Kit.slab(Eastern.ROUGH_SLAB));
        Eastern.noren(s, 2, 5, 2, 5, 3, "south");
        // firewood and a rain tub under the overhang
        fill(s, 0, 0, 6, 0, 1, 6, Kit.log("minecraft:oak_log", "x"));
        put(s, 1, 0, 6, Kit.log("minecraft:oak_log", "x"));
        put(s, 4, 0, 6, "minecraft:water_cauldron[level=3]");
        put(s, 3, 4, 6, Kit.hangingLantern());
        // inside
        Eastern.futon(s, 1, 1, 3, "north");
        put(s, 3, 0, 3, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        put(s, 3, 3, 3, Kit.chain());
        put(s, 3, 2, 3, Kit.hangingLantern());
        Furnish.chest(s, 3, 1, 2, "west");
        Furnish.sack(s, 3, 1, 4, true);
        put(s, 1, 2, 4, Kit.topSlab(Eastern.FLOOR_SLAB));
        put(s, 1, 3, 4, Furnish.candles(1));
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the town house

    /**
     * A town house with a shop (machiya): two storeys of white plaster between dark posts, its long roof slope to the
     * street. The shop front below has a slatted lattice to each side of the door under a curtain of cloth, a bench before
     * the lattice and a pent roof over it all on two brackets, with a lantern and a hanging sign; the low upper storey
     * looks out through a long slatted window, and fire walls of plaster stand out from both ends of its front. Inside, the
     * shop of a cloth merchant at the front with shelves of bolts and a counter, behind it the living room on mats with a
     * low table, the household shrine, the kitchen and the chest, and a stair of drawers up to the bedroom under the roof
     * with two beds, an alcove, a chest of drawers and a folding screen.
     */
    static Structure townHouse() {
        Structure s = new Structure(9, 18, 13);
        fill(s, 1, 0, 1, 7, 0, 9, ROUGH);
        fill(s, 2, 0, 2, 6, 0, 8, FLOOR);
        Eastern.frame(s, 1, 1, 7, 9, 0, 5, 0, POST_Y, PLASTER);
        Eastern.frame(s, 1, 1, 7, 9, 5, 9, 0, POST_Y, PLASTER);
        fill(s, 2, 5, 2, 6, 5, 8, FLOOR);
        fill(s, 2, 9, 2, 6, 9, 8, PLANKS);
        // the wainscot of the ground storey
        for (int z = 2; z <= 8; z++) {
            for (int x : new int[]{1, 7}) {
                if (!s.get(x, 1, z).equals(POST_Y)) {
                    put(s, x, 1, z, PLANKS);
                }
            }
        }
        // the shop front: lattices, the door under its curtain, the pent roof, the bench, the sign and the lantern
        fill(s, 2, 1, 9, 6, 4, 9, PLASTER);
        Eastern.lattice(s, 2, 9, 3, 9, 1, 3);
        Eastern.lattice(s, 5, 9, 6, 9, 1, 3);
        s.door(4, 1, 9, Eastern.DOOR, "north", "left");
        Eastern.noren(s, 4, 9, 4, 9, 3, "south");
        pent(s, 0, 8, 9, 5, "south");
        fill(s, 1, 1, 10, 3, 1, 10, Kit.topSlab(Eastern.FLOOR_SLAB));
        put(s, 6, 1, 10, Kit.topSlab(Eastern.FLOOR_SLAB));
        put(s, 4, 4, 10, Kit.hangingLantern());
        put(s, 7, 3, 10, Kit.wallBanner("red", "south"));
        put(s, 4, 0, 10, Kit.slab(Eastern.ROUGH_SLAB));
        // the upper storey: a long slatted window, fire walls at the ends of its front
        Eastern.lattice(s, 2, 9, 6, 9, 7, 7);
        for (int x : new int[]{0, 8}) {
            fill(s, x, 6, 9, x, 8, 10, PLASTER);
            fill(s, x, 5, 9, x, 5, 10, ROUGH);
            put(s, x, 9, 9, Kit.slab(ROOF.slab()));
            put(s, x, 9, 10, Kit.stairs(ROOF.stairs(), "north"));
        }
        // windows of the sides and the back
        Eastern.window(s, 1, 2, 5, "west", 1, 2);
        Eastern.window(s, 7, 2, 3, "east", 1, 2);
        Eastern.window(s, 1, 7, 7, "west", 1, 1);
        Eastern.window(s, 7, 7, 4, "east", 1, 1);
        Eastern.lattice(s, 3, 1, 5, 1, 7, 7);
        rowRoof(s, 0, 0, 8, 10, 10, 0, 1, ROOF, PLASTER, 10, true);
        // the shop: bolts of cloth on the shelves, the counter with the scales of the trade
        for (int z = 7; z <= 8; z++) {
            bolts(s, 2, 1, z, 3, z);
        }
        fill(s, 5, 1, 7, 6, 1, 7, Kit.topSlab(Eastern.FLOOR_SLAB));
        put(s, 6, 2, 7, Furnish.candles(2));
        put(s, 5, 2, 7, "minecraft:white_carpet");
        bolts(s, 6, 1, 8, 1, 4);
        put(s, 4, 4, 7, Kit.hangingLantern());
        // the living room on mats: the stair of drawers, the low table, the shrine, the kitchen, the chest
        Eastern.tatami(s, 3, 2, 6, 5, 0, false);
        stairChest(s, 2, 1, 6, 0, -1, 5);
        Furnish.kitchen(s, 4, 1, 2, "south", Eastern.WOOD);
        Eastern.lowTable(s, 4, 4, 4, 4, 1);
        Eastern.shrine(s, 6, 1, 4, "west");
        Furnish.chest(s, 6, 1, 5, "west");
        put(s, 4, 4, 4, Kit.hangingLantern());
        // the bedroom under the roof
        fill(s, 3, 6, 3, 3, 6, 4, FENCE);
        put(s, 2, 6, 5, FENCE);
        Eastern.futon(s, 4, 6, 8, "north");
        Eastern.futon(s, 6, 6, 8, "north");
        Eastern.alcove(s, 6, 6, 2, "south");
        Eastern.cabinet(s, 5, 6, 2, 2);
        Eastern.foldingScreen(s, 6, 5, 5, 5, 6);
        Eastern.paperLamp(s, 2, 6, 8);
        put(s, 4, 8, 4, Kit.hangingLantern());
        put(s, 5, 8, 7, Kit.hangingLantern());
        // light under the roof
        put(s, 3, 10, 5, Kit.lantern());
        put(s, 5, 10, 5, Kit.lantern());
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the merchant's house

    /**
     * A merchant's house of two storeys on a plinth of stone, its gable to the street: a white hip-and-gable roof with
     * corners sweeping up crowns it, a ledge of tiles runs around its sides and back between the storeys, and before the
     * front an arcade of red pillars carries the balcony of the upper storey, with a red balustrade, slender red posts under
     * the eaves, lanterns of cloth and plants in tubs. Behind the arcade the shop of a dealer in tea and porcelain: slatted
     * windows, the door under its curtain, shelves of jars and sacks of tea, a counter, the household shrine, and a stair of
     * drawers up to the family's rooms, which open onto the balcony through sliding screens: three beds, an alcove, a chest
     * of drawers, the chest of the household and paper lamps.
     */
    static Structure merchant() {
        Structure s = new Structure(11, 20, 13);
        Eastern.platform(s, 1, 1, 9, 12, 0, 0, 0, FLOOR);
        Eastern.frame(s, 2, 2, 8, 9, 0, 5, 0, POST_Y, PLASTER);
        Eastern.frame(s, 2, 2, 8, 9, 5, 10, 0, POST_Y, PLASTER);
        fill(s, 3, 5, 3, 7, 5, 8, FLOOR);
        fill(s, 3, 10, 3, 7, 10, 8, PLANKS);
        for (int z = 3; z <= 8; z++) {
            for (int x : new int[]{2, 8}) {
                if (!s.get(x, 1, z).equals(POST_Y)) {
                    put(s, x, 1, z, PLANKS);
                }
            }
        }
        ledge(s, 2, 2, 8, 9, 5);
        // the arcade and the balcony over it
        fill(s, 2, 5, 10, 8, 6, 11, Structure.AIR);
        for (int x : new int[]{2, 5, 8}) {
            Eastern.pillar(s, x, 1, 4, 11, false);
            fill(s, x, 6, 11, x, 9, 11, Eastern.RED_POST);
        }
        fill(s, 2, 5, 10, 8, 5, 10, PLANKS);
        fill(s, 2, 5, 11, 8, 5, 11, Kit.log(Eastern.POST, "x"));
        Eastern.railing(s, 3, 11, 4, 11, 6, true);
        Eastern.railing(s, 6, 11, 7, 11, 6, true);
        Eastern.railing(s, 2, 10, 2, 10, 6, true);
        Eastern.railing(s, 8, 10, 8, 10, 6, true);
        Furnish.plant(s, 3, 6, 10, "flowering_azalea");
        Furnish.plant(s, 7, 6, 10, "azalea");
        // the shop front: slatted windows, the door under its curtain; screens of the rooms above
        Eastern.lattice(s, 3, 9, 3, 9, 1, 3);
        Eastern.lattice(s, 7, 9, 7, 9, 1, 3);
        s.door(5, 1, 9, Eastern.DOOR, "north", "left");
        Eastern.noren(s, 4, 9, 6, 9, 3, "south");
        for (int x : new int[]{3, 5, 7}) {
            Eastern.screen(s, x, 9, x, 9, 6, 9, false);
        }
        // windows of the sides and the back
        Eastern.window(s, 2, 2, 6, "west", 1, 2);
        Eastern.window(s, 8, 2, 4, "east", 1, 2);
        Eastern.window(s, 2, 7, 6, "west", 1, 2);
        Eastern.window(s, 8, 7, 6, "east", 1, 2);
        Eastern.window(s, 5, 7, 2, "north", 1, 2);
        Eastern.lattice(s, 4, 2, 6, 2, 3, 3);
        // the roof; a frieze of plaster over the front wall and a ceiling over the balcony
        fill(s, 3, 11, 9, 7, 11, 9, PLASTER);
        Eastern.hipGableRoof(s, 0, 0, 10, 12, 10, 1);
        fill(s, 3, 10, 10, 7, 10, 10, PLANKS);
        // lanterns of cloth under the balcony and under the eaves
        Eastern.clothLantern(s, 3, 10, 4, 0);
        Eastern.clothLantern(s, 7, 10, 4, 0);
        Eastern.clothLantern(s, 5, 10, 9, 0);
        // the shop: shelves of jars and sacks of tea, the counter, the shrine, the stair of drawers
        for (int x = 4; x <= 7; x++) {
            if (x % 2 == 0) {
                Furnish.crate(s, x, 1, 3, "spruce");
            } else {
                put(s, x, 1, 3, "minecraft:green_wool");
            }
            put(s, x, 2, 3, Kit.topSlab(Eastern.FLOOR_SLAB));
            put(s, x, 3, 3, x % 2 == 0 ? "minecraft:flower_pot" : Furnish.candles(1));
        }
        fill(s, 6, 1, 5, 6, 1, 7, Kit.topSlab(Eastern.FLOOR_SLAB));
        put(s, 6, 2, 6, "minecraft:flower_pot");
        put(s, 6, 2, 5, Furnish.candles(2));
        Furnish.sack(s, 7, 1, 8, false);
        put(s, 7, 1, 7, "minecraft:lime_wool");
        Eastern.shrine(s, 7, 1, 4, "west");
        stairChest(s, 3, 1, 8, 0, -1, 5);
        put(s, 5, 4, 5, Kit.hangingLantern());
        put(s, 5, 4, 8, Kit.hangingLantern());
        // the rooms above
        fill(s, 4, 6, 5, 4, 6, 6, FENCE);
        put(s, 3, 6, 7, FENCE);
        Eastern.tatami(s, 4, 3, 7, 8, 5, false);
        put(s, 4, 5, 5, FLOOR);
        put(s, 4, 5, 6, FLOOR);
        Eastern.futon(s, 5, 6, 8, "north");
        Eastern.futon(s, 7, 6, 8, "north");
        Eastern.futon(s, 7, 6, 5, "north");
        Eastern.alcove(s, 5, 6, 3, "south");
        Eastern.cabinet(s, 6, 6, 3, 2);
        Furnish.chest(s, 4, 6, 3, "south");
        Eastern.paperLamp(s, 3, 6, 8);
        Eastern.paperLamp(s, 5, 6, 6);
        put(s, 6, 9, 6, Kit.hangingLantern());
        put(s, 4, 11, 5, Kit.lantern());
        put(s, 6, 11, 5, Kit.lantern());
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the house with a garden

    /**
     * A house with a small garden: a single storey of white plaster between dark posts on a platform of stone under a
     * hip-and-gable roof whose corners sweep up, a round paper window in its side, its front of sliding screens opening onto
     * a veranda of boards. Before it a little walled garden: white walls under copings of tiles, a gate of dark posts under a
     * little roof of its own with its leaves standing open, stepping stones over raked gravel to the shoe stone at the
     * veranda, a stone lantern, a pine pruned into pads, flowering shrubs, a stand of bamboo and a stone basin of water with moss. Inside, on mats under
     * a ceiling of boards: two beds, the kitchen corner, a low table with cushions, an alcove with its scroll, the chest of
     * the household, a chest of drawers and paper lamps.
     */
    static Structure gardenHouse() {
        Structure s = new Structure(11, 15, 15);
        // the garden walls first; the house stands over the back of their ring
        fill(s, 2, 0, 10, 8, 0, 12, Eastern.GRAVEL);
        Eastern.gardenWall(s, 1, 9, 9, 13, 0, 2, true, 4);
        fill(s, 2, 1, 8, 8, 4, 10, Structure.AIR);
        // the house
        Eastern.platform(s, 1, 1, 9, 9, 0, 1, 0, FLOOR);
        Eastern.frame(s, 2, 2, 8, 8, 1, 5, 2, POST_Y, PLASTER);
        fill(s, 3, 5, 3, 7, 5, 7, PLANKS);
        Eastern.tatami(s, 3, 3, 7, 7, 1, true);
        fill(s, 2, 1, 9, 8, 1, 9, FLOOR);
        for (int x = 3; x <= 7; x++) {
            if (x != 5) {
                Eastern.screen(s, x, 8, x, 8, 2, 4, false);
            }
        }
        s.door(5, 2, 8, Eastern.DOOR, "north", "left");
        Eastern.roundWindow(s, 8, 2, 5, false, 3);
        Eastern.window(s, 2, 3, 5, "west", 1, 1);
        Eastern.lattice(s, 4, 2, 6, 2, 3, 3);
        Eastern.hipGableRoof(s, 0, 0, 10, 10, 5, 1);
        // the gate: dark posts, a beam, a little roof, the leaves standing open
        fill(s, 3, 0, 13, 7, 4, 13, Structure.AIR);
        fill(s, 3, 2, 12, 7, 3, 14, Structure.AIR);
        put(s, 3, 0, 13, STONE);
        put(s, 7, 0, 13, STONE);
        fill(s, 4, 0, 13, 6, 0, 13, TRIM);
        fill(s, 3, 1, 13, 3, 4, 13, POST_Y);
        fill(s, 7, 1, 13, 7, 4, 13, POST_Y);
        fill(s, 2, 5, 13, 8, 5, 13, Kit.log(Eastern.POST, "x"));
        for (int x = 2; x <= 8; x++) {
            put(s, x, 6, 12, Kit.stairs(ROOF.stairs(), "south"));
            put(s, x, 6, 14, Kit.stairs(ROOF.stairs(), "north"));
            put(s, x, 6, 13, ROOF.block());
            put(s, x, 7, 13, Kit.slab(ROOF.slab()));
        }
        put(s, 2, 7, 13, Kit.stairs(ROOF.stairs(), "west"));
        put(s, 8, 7, 13, Kit.stairs(ROOF.stairs(), "east"));
        for (int x : new int[]{2, 8}) {
            put(s, x, 6, 12, Kit.stairs(ROOF.stairs(), "south", x == 2 ? "outer_left" : "outer_right"));
            put(s, x, 6, 14, Kit.stairs(ROOF.stairs(), "north", x == 2 ? "outer_right" : "outer_left"));
        }
        put(s, 3, 4, 12, Kit.upside(Eastern.STAIRS, "south"));
        put(s, 7, 4, 12, Kit.upside(Eastern.STAIRS, "south"));
        put(s, 3, 4, 14, Kit.upside(Eastern.STAIRS, "north"));
        put(s, 7, 4, 14, Kit.upside(Eastern.STAIRS, "north"));
        put(s, 4, 1, 13, "minecraft:spruce_fence_gate[facing=north,in_wall=false,open=true,powered=false]");
        put(s, 6, 1, 13, "minecraft:spruce_fence_gate[facing=north,in_wall=false,open=true,powered=false]");
        put(s, 5, 4, 14, Kit.hangingLantern());
        // the garden: stepping stones, the shoe stone, a lantern, a pine, bamboo, a basin with moss
        for (int z = 10; z <= 12; z++) {
            put(s, 5, 0, z, Eastern.GRANITE);
        }
        put(s, 5, 1, 10, Kit.slab(Eastern.GRANITE_SLAB));
        Eastern.stoneLantern(s, 3, 1, 11, false);
        Eastern.pine(s, 7, 1, 11, 4, "west");
        put(s, 7, 0, 11, Eastern.MOSS);
        put(s, 8, 1, 10, Kit.leaves("flowering_azalea"));
        put(s, 8, 1, 12, Kit.leaves("flowering_azalea"));
        put(s, 4, 1, 12, Kit.leaves("flowering_azalea"));
        put(s, 2, 0, 12, Eastern.GRASS);
        put(s, 8, 0, 12, Eastern.MOSS);
        put(s, 2, 0, 10, Eastern.MOSS);
        put(s, 2, 1, 10, "minecraft:water_cauldron[level=3]");
        put(s, 3, 1, 10, Eastern.ROUGH);
        put(s, 3, 2, 10, Kit.slab(Eastern.ROUGH_SLAB));
        for (int yy = 1; yy <= 4; yy++) {
            put(s, 2, yy, 12, "minecraft:bamboo[age=1,leaves=" + (yy >= 3 ? "large" : "small") + ",stage=0]");
        }
        // inside: beds, the kitchen corner, the low table, the alcove, the chest, a chest of drawers, lamps and light
        Eastern.futon(s, 3, 2, 4, "north");
        Eastern.futon(s, 4, 2, 4, "north");
        Furnish.kitchen(s, 5, 2, 3, "south", Eastern.WOOD);
        Eastern.lowTable(s, 5, 2, 5, 6, 2);
        Eastern.alcove(s, 7, 2, 7, "west");
        Furnish.chest(s, 7, 2, 5, "west");
        Eastern.cabinet(s, 3, 2, 7, 1);
        Eastern.paperLamp(s, 3, 2, 6);
        put(s, 5, 4, 5, Kit.hangingLantern());
        put(s, 4, 6, 5, Kit.lantern());
        put(s, 6, 6, 5, Kit.lantern());
        put(s, 5, 4, 9, Kit.hangingLantern());
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the noble residence

    /**
     * The residence of a noble family, walled: the hall of two storeys on a platform of stone with steps, its veranda of red
     * pillars with a red balustrade under a ledge of tiles, its upper storey with a round paper window under a great
     * hip-and-gable roof with white gables and corners sweeping far up. Before it a court behind white walls under copings
     * of tiles: a pond with rocks and moss, a blossoming cherry, stone lanterns, a path of stone, and the gate on four red
     * pillars under a curved roof whose gable looks at the street, its leaves standing open, a plaque over them and lanterns
     * of cloth. Below, the hall of guests on mats with the alcove and its scroll, a low table with cushions, the household
     * shrine, a chest of drawers, paper lamps and a stair of drawers; above, the rooms of the family with four beds, the
     * chest of the household and a folding screen.
     */
    static Structure noble() {
        Structure s = new Structure(13, 18, 15);
        // the court and its walls, the pond
        fill(s, 2, 0, 8, 10, 0, 11, Eastern.GRAVEL);
        Eastern.gardenWall(s, 1, 8, 11, 12, 0, 2, true, 4);
        fill(s, 2, 0, 7, 10, 4, 9, Structure.AIR);
        fill(s, 2, 0, 8, 10, 0, 9, Eastern.GRAVEL);
        Eastern.pond(s, 2, 9, 4, 10, 0);
        // the hall: platform, steps, two storeys, the veranda of red pillars, the ledge and the great roof
        Eastern.platform(s, 1, 0, 11, 7, 0, 1, 0, FLOOR);
        Eastern.steps(s, 6, 8, "south", 3, 1, 0, false);
        Eastern.frame(s, 2, 1, 10, 6, 1, 6, 0, POST_Y, PLASTER);
        Eastern.frame(s, 2, 1, 10, 6, 6, 10, 0, POST_Y, PLASTER);
        fill(s, 3, 6, 2, 9, 6, 5, FLOOR);
        fill(s, 3, 10, 2, 9, 10, 5, PLANKS);
        fill(s, 2, 1, 7, 10, 1, 7, FLOOR);
        ledge(s, 2, 1, 10, 6, 6);
        for (int x : new int[]{2, 4, 8, 10}) {
            Eastern.pillar(s, x, 2, 5, 7, false);
        }
        Eastern.railing(s, 3, 7, 3, 7, 2, true);
        Eastern.railing(s, 9, 7, 9, 7, 2, true);
        for (int x = 3; x <= 9; x++) {
            if (x != 6) {
                Eastern.screen(s, x, 6, x, 6, 2, 5, false);
            }
        }
        s.door(6, 2, 6, Eastern.DOOR, "north", "left");
        put(s, 6, 4, 6, Eastern.PAPER);
        put(s, 6, 5, 6, Eastern.PAPER);
        Eastern.roundWindow(s, 6, 7, 6, true, 3);
        Eastern.screen(s, 3, 6, 4, 6, 7, 9, false);
        Eastern.screen(s, 8, 6, 9, 6, 7, 9, false);
        Eastern.window(s, 2, 3, 3, "west", 1, 2);
        Eastern.window(s, 10, 3, 4, "east", 1, 2);
        Eastern.window(s, 2, 8, 3, "west", 1, 1);
        Eastern.window(s, 10, 8, 4, "east", 1, 1);
        Eastern.roof(s, 0, 0, 12, 8, 10, 2, 1, 2, ROOF, PLASTER);
        for (int x = 4; x <= 8; x += 4) {
            Eastern.clothLantern(s, x + 1, 7, 5, 0);
        }
        // the gate on four red pillars under its curved roof
        fill(s, 4, 0, 11, 8, 5, 13, Structure.AIR);
        fill(s, 5, 0, 11, 7, 0, 13, TRIM);
        for (int x : new int[]{4, 8}) {
            for (int z : new int[]{11, 13}) {
                Eastern.pillar(s, x, 0, 4, z, false);
            }
            put(s, x, 0, 12, TRIM);
            fill(s, x, 1, 12, x, 4, 12, PLASTER);
        }
        s.beam(4, 11, 8, 13, 5, Eastern.POST);
        fill(s, 5, 5, 12, 7, 5, 12, PLANKS);
        fill(s, 4, 6, 11, 8, 6, 13, PLANKS);
        for (int z : new int[]{11, 13}) {
            put(s, 3, 5, z, Kit.upside(Eastern.STAIRS, "east"));
            put(s, 9, 5, z, Kit.upside(Eastern.STAIRS, "west"));
        }
        rowRoof(s, 2, 10, 10, 14, 6, 1, 2, ROOF, PLASTER, 7, false);
        s.door(5, 1, 12, Eastern.DOOR, "south", "left");
        s.door(7, 1, 12, Eastern.DOOR, "south", "right");
        for (int yy = 1; yy <= 2; yy++) {
            put(s, 5, yy, 12, s.get(5, yy, 12).replace("open=false", "open=true"));
            put(s, 7, yy, 12, s.get(7, yy, 12).replace("open=false", "open=true"));
        }
        put(s, 6, 4, 14, Kit.wallBanner("red", "south"));
        put(s, 6, 4, 10, Kit.wallBanner("red", "north"));
        Eastern.clothLantern(s, 6, 13, 5, 1);
        Eastern.clothLantern(s, 6, 11, 5, 1);
        // the court: the path, the cherry, the lanterns
        fill(s, 5, 0, 10, 7, 0, 10, Eastern.GRANITE);
        put(s, 6, 0, 11, Eastern.GRANITE);
        fill(s, 9, 0, 9, 10, 0, 11, Eastern.GRASS);
        Eastern.cherry(s, 10, 1, 10, 5);
        put(s, 10, 0, 10, Eastern.MOSS);
        Eastern.stoneLantern(s, 8, 1, 10, false);
        Eastern.stoneLantern(s, 4, 1, 11, false);
        // the hall of guests below
        Eastern.tatami(s, 3, 2, 9, 5, 1, true);
        stairChest(s, 3, 2, 2, 1, 0, 6);
        Eastern.alcove(s, 9, 2, 3, "west");
        Eastern.lowTable(s, 5, 4, 6, 4, 2);
        Eastern.shrine(s, 3, 2, 4, "east");
        Eastern.cabinet(s, 9, 2, 5, 2);
        Eastern.paperLamp(s, 3, 2, 5);
        Eastern.foldingScreen(s, 8, 2, 8, 3, 2);
        put(s, 6, 5, 3, Kit.hangingLantern());
        put(s, 5, 5, 5, Kit.hangingLantern());
        // the rooms of the family above
        fill(s, 5, 7, 3, 6, 7, 3, FENCE);
        put(s, 4, 7, 2, FENCE);
        Eastern.tatami(s, 3, 3, 9, 5, 6, false);
        Eastern.futon(s, 3, 7, 5, "north");
        Eastern.futon(s, 4, 7, 5, "north");
        Eastern.futon(s, 8, 7, 5, "north");
        Eastern.futon(s, 9, 7, 5, "north");
        Furnish.chest(s, 9, 7, 2, "south");
        Eastern.cabinet(s, 9, 7, 3, 1);
        Eastern.paperLamp(s, 6, 7, 5);
        put(s, 6, 9, 4, Kit.hangingLantern());
        put(s, 4, 11, 3, Kit.lantern());
        put(s, 8, 11, 3, Kit.lantern());
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the farmhouse

    /**
     * A farmhouse (minka) under a great thatched roof of straw, hipped below and gabled at the top with smoke vents, its
     * ridge bound in tiles with logs laid across. The low walls are of white plaster over a wainscot of boards between dark
     * posts. To the west the raised rooms of the family open to the street through paper screens onto a veranda of boards;
     * to the east the earthen hall behind a double door under a curtain holds the kitchen with its cauldron and fire, bales
     * of hay, sacks of rice and baskets. In the raised room a sunken hearth burns under a hanging lantern, two beds lie on
     * the floor, an alcove with a scroll stands at the back wall, and the chest of the household by the step. Hay is stacked
     * and firewood piled under the eaves.
     */
    static Structure farmer() {
        Structure s = new Structure(11, 15, 9);
        fill(s, 1, 0, 1, 9, 0, 7, ROUGH);
        Eastern.frame(s, 1, 1, 9, 7, 0, 5, 4, POST_Y, PLASTER);
        // the wainscot of boards along the foot of the walls
        for (int x = 2; x <= 8; x++) {
            for (int z : new int[]{1, 7}) {
                if (!s.get(x, 1, z).equals(POST_Y)) {
                    put(s, x, 1, z, PLANKS);
                }
            }
        }
        for (int z = 2; z <= 6; z++) {
            for (int x : new int[]{1, 9}) {
                if (!s.get(x, 1, z).equals(POST_Y)) {
                    put(s, x, 1, z, PLANKS);
                }
            }
        }
        // floors: the raised rooms to the west, the earthen hall to the east; the ceiling
        fill(s, 2, 1, 2, 5, 1, 6, FLOOR);
        fill(s, 6, 0, 2, 8, 0, 6, EARTH);
        fill(s, 2, 5, 2, 8, 5, 6, PLANKS);
        thatch(s, 0, 0, 10, 8, 5, 2);
        // the front of the rooms: paper screens over a veranda of boards
        Eastern.screen(s, 2, 7, 4, 7, 2, 4, false);
        fill(s, 1, 1, 8, 5, 1, 8, Kit.topSlab(Eastern.FLOOR_SLAB));
        put(s, 1, 0, 8, FENCE);
        put(s, 5, 0, 8, FENCE);
        // the doors of the earthen hall under their curtain, a slatted window beside them
        s.door(7, 1, 7, Eastern.DOOR, "north", "left");
        s.door(8, 1, 7, Eastern.DOOR, "north", "right");
        Eastern.noren(s, 7, 7, 8, 7, 3, "south");
        put(s, 6, 2, 7, FENCE);
        put(s, 6, 3, 7, FENCE);
        // windows: slats in the back, paper in the west, slats in the east
        Eastern.lattice(s, 3, 1, 4, 1, 3, 3);
        Eastern.lattice(s, 7, 1, 7, 1, 3, 3);
        Eastern.window(s, 1, 3, 4, "west", 1, 1);
        Eastern.lattice(s, 9, 2, 9, 9, 3, 3);
        Eastern.lattice(s, 9, 6, 9, 9, 3, 3);
        // the raised room: the sunken hearth, beds, the alcove, the chest, cushions and light
        put(s, 3, 1, 4, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        put(s, 3, 4, 4, Kit.chain());
        put(s, 3, 3, 4, Kit.hangingLantern());
        put(s, 2, 2, 4, Kit.CLOTH_CARPET);
        put(s, 4, 2, 4, Kit.CLOTH_CARPET);
        put(s, 3, 2, 5, Kit.CLOTH_CARPET);
        Eastern.futon(s, 2, 2, 3, "north");
        Eastern.futon(s, 5, 2, 3, "north");
        Eastern.alcove(s, 4, 2, 2, "south");
        Furnish.chest(s, 5, 2, 6, "west");
        Eastern.paperLamp(s, 2, 2, 6);
        // the earthen hall: the kitchen, hay, rice and baskets
        Furnish.kitchen(s, 6, 1, 2, "south", Eastern.WOOD);
        put(s, 8, 1, 4, THATCH);
        put(s, 8, 1, 5, THATCH);
        put(s, 8, 2, 5, THATCH);
        Furnish.sack(s, 8, 1, 3, true);
        Furnish.sack(s, 6, 1, 6, false);
        put(s, 6, 1, 5, "minecraft:cauldron");
        put(s, 7, 4, 4, Kit.hangingLantern());
        put(s, 4, 4, 5, Kit.hangingLantern());
        // light under the thatch
        put(s, 3, 6, 4, Kit.lantern());
        put(s, 7, 6, 4, Kit.lantern());
        // hay and firewood under the eaves
        put(s, 0, 0, 5, THATCH);
        put(s, 0, 0, 6, THATCH);
        put(s, 0, 1, 6, THATCH);
        fill(s, 10, 0, 2, 10, 1, 5, Kit.log("minecraft:oak_log", "z"));
        put(s, 7, 0, 8, Kit.slab(Eastern.ROUGH_SLAB));
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the smithy

    /**
     * A smithy: a workshop hall on a low platform of stone, its walls of boards blackened below and plaster above between
     * dark posts, its wide front standing open under a curtain, under a gable roof with white gables, its corners swept up,
     * and a louvre on the ridge to let out the smoke. The forge of rough stone stands at the back with its fire under a hood
     * that leads into a stack of rough stone rising through the roof. Around it the anvil, the quenching tub, the blast
     * furnace, the smith's post with his chest, the smithing table, a grindstone and heaps of charcoal and iron; before the
     * front, under the eaves, a pile of firewood, sacks of charcoal and a lantern on its post.
     */
    static Structure smithy() {
        Structure s = new Structure(13, 16, 13);
        Eastern.platform(s, 1, 1, 11, 10, 0, 0, 0, null);
        Eastern.frame(s, 2, 2, 10, 8, 0, 6, 2, POST_Y, PLASTER);
        for (int y = 1; y <= 2; y++) {
            for (int x = 3; x <= 9; x++) {
                for (int z : new int[]{2, 8}) {
                    if (!s.get(x, y, z).equals(POST_Y)) {
                        put(s, x, y, z, PLANKS);
                    }
                }
            }
            for (int z = 3; z <= 7; z++) {
                for (int x : new int[]{2, 10}) {
                    if (!s.get(x, y, z).equals(POST_Y)) {
                        put(s, x, y, z, PLANKS);
                    }
                }
            }
        }
        // the open front under its lintel and curtain
        fill(s, 4, 1, 8, 8, 3, 8, Structure.AIR);
        fill(s, 3, 4, 8, 9, 4, 8, Kit.log(Eastern.POST, "x"));
        Eastern.noren(s, 5, 8, 7, 8, 4, "south");
        Eastern.lattice(s, 2, 5, 2, 6, 3, 4);
        Eastern.lattice(s, 10, 4, 10, 6, 3, 4);
        Eastern.lattice(s, 7, 2, 9, 2, 4, 4);
        fill(s, 3, 6, 3, 9, 6, 7, PLANKS);
        rowRoof(s, 1, 0, 11, 10, 6, 1, 2, ROOF, PLASTER, 7, true);
        // the louvre on the ridge
        fill(s, 4, 12, 5, 8, 12, 5, FENCE);
        for (int x = 3; x <= 9; x++) {
            put(s, x, 13, 4, Kit.stairs(ROOF.stairs(), "south"));
            put(s, x, 13, 6, Kit.stairs(ROOF.stairs(), "north"));
            put(s, x, 13, 5, ROOF.block());
        }
        put(s, 3, 14, 5, Kit.stairs(ROOF.stairs(), "west"));
        put(s, 9, 14, 5, Kit.stairs(ROOF.stairs(), "east"));
        fill(s, 4, 14, 5, 8, 14, 5, Kit.slab(ROOF.slab()));
        // the forge, its hood and the stack of rough stone through the roof
        fill(s, 3, 1, 3, 4, 1, 4, ROUGH);
        put(s, 3, 2, 3, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        put(s, 4, 2, 3, Kit.slab(Eastern.ROUGH_SLAB));
        fill(s, 3, 2, 4, 4, 2, 4, Kit.slab(Eastern.ROUGH_SLAB));
        fill(s, 3, 4, 4, 4, 4, 4, Kit.upside(Eastern.ROUGH_STAIRS, "north"));
        fill(s, 3, 4, 3, 4, 6, 3, ROUGH);
        fill(s, 3, 0, 2, 4, 13, 2, ROUGH);
        fill(s, 3, 7, 3, 4, 8, 3, ROUGH);
        put(s, 3, 14, 2, Kit.ROUGH_FENCE);
        put(s, 4, 14, 2, Kit.ROUGH_FENCE);
        fill(s, 3, 13, 1, 4, 13, 1, Kit.stairs(Eastern.ROUGH_STAIRS, "south"));
        // the tools of the trade, the smith's post and his chest
        put(s, 5, 1, 3, "minecraft:blast_furnace[facing=south,lit=false]");
        put(s, 5, 1, 4, "minecraft:water_cauldron[level=3]");
        put(s, 7, 1, 3, "kingdomworld:work_post");
        Furnish.chest(s, 8, 1, 3, "south");
        put(s, 9, 1, 3, "minecraft:smithing_table");
        put(s, 6, 1, 5, "minecraft:anvil[facing=west]");
        put(s, 9, 1, 6, "minecraft:grindstone[face=floor,facing=south]");
        put(s, 3, 1, 6, "minecraft:coal_block");
        put(s, 3, 1, 7, "minecraft:coal_block");
        put(s, 3, 2, 7, "minecraft:coal_block");
        put(s, 9, 1, 7, "minecraft:raw_iron_block");
        put(s, 9, 1, 4, Kit.log("minecraft:stripped_oak_log", "y"));
        put(s, 9, 2, 4, Kit.slab(Eastern.ROUGH_SLAB));
        put(s, 6, 5, 4, Kit.hangingLantern());
        put(s, 8, 5, 6, Kit.hangingLantern());
        put(s, 5, 7, 5, Kit.lantern());
        put(s, 8, 7, 5, Kit.lantern());
        // firewood, charcoal and the lantern post before the front
        fill(s, 2, 1, 9, 2, 2, 10, Kit.log("minecraft:oak_log", "z"));
        put(s, 3, 1, 10, Kit.log("minecraft:oak_log", "z"));
        Furnish.sack(s, 10, 1, 9, false);
        Furnish.sack(s, 10, 1, 10, false);
        Furnish.sack(s, 10, 2, 10, false);
        put(s, 6, 5, 9, Kit.hangingLantern());
        Eastern.lanternPost(s, 11, 0, 12, 3, "west");
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the inn

    /**
     * An inn of two storeys with its long side to the street on a platform of stone: walls of white plaster between dark
     * posts, a gable roof with white gables and corners swept up, a pent roof at the back. Before the front red pillars
     * carry the balcony of the upper storey with its red balustrade; red lanterns of cloth hang under the balcony and under
     * the eaves, a ball of cedar twigs, the old sign of a house that serves rice wine, hangs from a corner, and lanterns on
     * posts stand at the street. Below the taproom: the counter with the innkeeper's post, his chest and three casks, a
     * smoker and the cauldron of the kitchen, shelves of jars, low tables on mats with cushions, a bench, and a stair of
     * drawers up to the guest rooms. Above, the rooms of the guests, parted by sliding walls of paper, with four beds,
     * chests of drawers and paper lamps.
     */
    static Structure tavern() {
        Structure s = new Structure(13, 21, 15);
        Eastern.platform(s, 0, 2, 12, 12, 0, 0, 0, null);
        fill(s, 2, 0, 4, 10, 0, 8, FLOOR);
        Eastern.frame(s, 1, 3, 11, 9, 0, 5, 2, POST_Y, PLASTER);
        Eastern.frame(s, 1, 3, 11, 9, 5, 10, 2, POST_Y, PLASTER);
        fill(s, 2, 5, 4, 10, 5, 8, FLOOR);
        fill(s, 2, 10, 4, 10, 10, 8, PLANKS);
        for (int x = 2; x <= 10; x++) {
            for (int z : new int[]{3, 9}) {
                if (!s.get(x, 1, z).equals(POST_Y)) {
                    put(s, x, 1, z, PLANKS);
                }
            }
        }
        // the front: screens and the door below, screens above
        for (int x : new int[]{4, 8}) {
            Eastern.screen(s, x, 9, x, 9, 1, 4, false);
        }
        Eastern.lattice(s, 2, 9, 2, 9, 2, 3);
        Eastern.lattice(s, 10, 9, 10, 9, 2, 3);
        s.door(6, 1, 9, Eastern.DOOR, "north", "left");
        Eastern.noren(s, 5, 9, 7, 9, 3, "south");
        for (int x = 2; x <= 10; x += 2) {
            Eastern.screen(s, x, 9, x, 9, 6, 9, false);
        }
        // the arcade and the balcony
        fill(s, 1, 5, 10, 11, 5, 10, PLANKS);
        fill(s, 1, 5, 11, 11, 5, 11, Kit.log(Eastern.POST, "x"));
        for (int x : new int[]{1, 4, 8, 11}) {
            Eastern.pillar(s, x, 1, 4, 11, false);
            fill(s, x, 6, 11, x, 9, 11, Eastern.RED_POST);
        }
        Eastern.railing(s, 2, 11, 3, 11, 6, true);
        Eastern.railing(s, 5, 11, 7, 11, 6, true);
        Eastern.railing(s, 9, 11, 10, 11, 6, true);
        fill(s, 1, 10, 11, 11, 10, 11, Kit.log(Eastern.POST, "x"));
        fill(s, 1, 10, 10, 11, 11, 10, PLANKS);
        fill(s, 2, 11, 9, 10, 12, 9, PLASTER);
        put(s, 1, 11, 9, POST_Y);
        put(s, 11, 11, 9, POST_Y);
        // windows of the sides and the back
        Eastern.window(s, 1, 2, 6, "west", 1, 2);
        Eastern.window(s, 11, 2, 6, "east", 1, 2);
        Eastern.window(s, 1, 7, 6, "west", 1, 2);
        Eastern.window(s, 11, 7, 6, "east", 1, 2);
        Eastern.lattice(s, 4, 3, 4, 3, 7, 8);
        Eastern.lattice(s, 8, 3, 8, 3, 7, 8);
        pent(s, 0, 12, 3, 5, "north");
        rowRoof(s, 0, 1, 12, 13, 10, 1, 2, ROOF, PLASTER, 11, true);
        // lanterns of cloth, the ball of cedar, lanterns on posts at the street
        for (int x : new int[]{2, 6, 10}) {
            Eastern.clothLantern(s, x, 10, 4, 0);
        }
        for (int x : new int[]{3, 6, 9}) {
            Eastern.clothLantern(s, x, 12, 9, 1);
        }
        put(s, 0, 9, 12, Kit.chain());
        put(s, 0, 8, 12, Kit.leaves("spruce"));
        put(s, 0, 7, 12, Kit.leaves("spruce"));
        Eastern.lanternPost(s, 0, 0, 13, 3, "east");
        Eastern.lanternPost(s, 12, 0, 13, 3, "west");
        // the taproom: the counter, the post, the chest, three casks, the kitchen, shelves
        put(s, 7, 1, 4, "minecraft:smoker[facing=south,lit=false]");
        put(s, 8, 1, 4, "kingdomworld:work_post");
        Furnish.chest(s, 9, 1, 4, "south");
        put(s, 10, 1, 4, Kit.barrel());
        put(s, 10, 1, 5, Kit.barrel());
        put(s, 10, 2, 4, Kit.barrel());
        put(s, 6, 1, 4, "minecraft:water_cauldron[level=3]");
        fill(s, 8, 1, 6, 10, 1, 6, Kit.topSlab(Eastern.FLOOR_SLAB));
        put(s, 9, 2, 6, Furnish.candles(2));
        put(s, 10, 2, 6, "minecraft:flower_pot");
        fill(s, 6, 3, 4, 9, 3, 4, Kit.topSlab(Eastern.FLOOR_SLAB));
        put(s, 6, 4, 4, "minecraft:flower_pot");
        put(s, 8, 4, 4, "minecraft:flower_pot");
        put(s, 9, 4, 4, Furnish.candles(3));
        // low tables on mats, a bench by the door, the stair of drawers
        Eastern.tatami(s, 2, 5, 5, 8, 0, true);
        Eastern.lowTable(s, 3, 6, 4, 6, 1);
        Eastern.lowTable(s, 3, 8, 3, 8, 1);
        Furnish.bench(s, 8, 8, 10, 8, 1, "north", Eastern.WOOD);
        stairChest(s, 2, 1, 4, 1, 0, 5);
        put(s, 4, 4, 6, Kit.hangingLantern());
        put(s, 8, 4, 6, Kit.hangingLantern());
        put(s, 6, 4, 8, Kit.hangingLantern());
        // the guest rooms above
        Eastern.tatami(s, 2, 5, 10, 8, 5, false);
        Eastern.screen(s, 6, 6, 6, 8, 6, 9, true);
        put(s, 6, 6, 6, Eastern.PAPER_SOLID);
        Eastern.futon(s, 2, 6, 8, "north");
        Eastern.futon(s, 4, 6, 8, "north");
        Eastern.futon(s, 8, 6, 8, "north");
        Eastern.futon(s, 10, 6, 8, "north");
        Eastern.cabinet(s, 3, 6, 6, 1);
        Eastern.cabinet(s, 9, 6, 6, 1);
        Eastern.paperLamp(s, 5, 6, 8);
        Eastern.paperLamp(s, 7, 6, 8);
        Eastern.alcove(s, 10, 6, 4, "south");
        put(s, 3, 9, 7, Kit.hangingLantern());
        put(s, 9, 9, 7, Kit.hangingLantern());
        put(s, 7, 9, 4, Kit.hangingLantern());
        put(s, 4, 11, 6, Kit.lantern());
        put(s, 8, 11, 6, Kit.lantern());
        s.connect();
        return s.trimSky(3);
    }

    // ---------------------------------------------------------------- the town wall and its gate

    /**
     * A piece of the town wall with its gate: ramparts of stone with battered feet and white parapets under copings of
     * tiles, loopholes in them and lanterns over the walks, with flights of steps up from the town; between them the gate
     * tower on a battered base of stone pierced by the passage, closed toward the field by a double gate standing open, a
     * plaque over its mouth and lanterns in it. On the base stands the guard room of white plaster between red posts with
     * barred windows toward the field and paper ones toward the town, under a skirt roof whose corners sweep up; on that
     * roof a red balustrade rings the watch room above, crowned by a hip-and-gable roof with white gables and horns. Inside a
     * war drum, racks of weapons, a bench and lanterns; a ladder up to the watch room with its alarm bell.
     */
    static Structure wallGate() {
        Structure s = new Structure(23, 24, 11);
        Eastern.rampart(s, 0, 3, 7, 3, 0, 6, "north");
        Eastern.rampart(s, 15, 3, 22, 3, 0, 6, "north");
        // the flights of steps up to the walks from the town
        for (int i = 0; i <= 5; i++) {
            put(s, i, i, 6, Kit.stairs(Eastern.STONE_STAIRS, "east"));
            put(s, 22 - i, i, 6, Kit.stairs(Eastern.STONE_STAIRS, "west"));
            for (int yy = 0; yy < i; yy++) {
                put(s, i, yy, 6, STONE);
                put(s, 22 - i, yy, 6, STONE);
            }
        }
        for (int x : new int[]{2, 5, 17, 20}) {
            put(s, x, 7, 4, Kit.hangingLantern());
        }
        // the battered base and its passage
        Eastern.platform(s, 7, 1, 15, 9, 0, 5, 3, null);
        fill(s, 10, 1, 1, 12, 4, 9, Structure.AIR);
        fill(s, 10, 0, 1, 12, 0, 9, TRIM);
        for (int z = 2; z <= 8; z++) {
            put(s, 10, 4, z, Kit.upside(Eastern.TRIM_STAIRS, "west"));
            put(s, 12, 4, z, Kit.upside(Eastern.TRIM_STAIRS, "east"));
        }
        s.door(10, 1, 2, Eastern.DOOR, "south", "left");
        s.door(12, 1, 2, Eastern.DOOR, "south", "right");
        for (int yy = 1; yy <= 2; yy++) {
            put(s, 10, yy, 2, s.get(10, yy, 2).replace("open=false", "open=true"));
            put(s, 12, yy, 2, s.get(12, yy, 2).replace("open=false", "open=true"));
        }
        put(s, 11, 4, 4, Kit.hangingLantern());
        put(s, 11, 4, 7, Kit.hangingLantern());
        put(s, 11, 5, 9, Kit.wallBanner("red", "south"));
        put(s, 11, 2, 0, Kit.wallBanner("red", "north"));
        // the guard room on the base, its skirt roof, the balustrade and the watch room
        Eastern.frame(s, 8, 2, 14, 8, 5, 10, 0, Eastern.RED, PLASTER);
        fill(s, 9, 5, 3, 13, 5, 7, FLOOR);
        fill(s, 9, 10, 3, 13, 10, 7, PLANKS);
        for (int x : new int[]{9, 11, 13}) {
            fill(s, x, 7, 2, x, 8, 2, Kit.BARS);
            Eastern.window(s, x, 7, 8, "south", 1, 2);
        }
        s.door(8, 6, 5, Eastern.DOOR, "east", "left");
        s.door(14, 6, 5, Eastern.DOOR, "west", "left");
        int sill = Eastern.skirt(s, 8, 2, 14, 8, 10, 2, 1, 2, ROOF);
        Eastern.frame(s, 9, 3, 13, 7, sill, sill + 4, 0, Eastern.RED, PLASTER);
        fill(s, 10, sill, 4, 12, sill, 6, FLOOR);
        Eastern.balcony(s, 8, 2, 14, 8, sill + 1);
        for (int z : new int[]{3, 7}) {
            fill(s, 11, sill + 2, z, 11, sill + 3, z, PAPER);
        }
        Eastern.roundWindow(s, 9, sill + 1, 5, false, 3);
        Eastern.roundWindow(s, 13, sill + 1, 5, false, 3);
        fill(s, 10, sill + 4, 4, 12, sill + 4, 6, PLANKS);
        Eastern.roof(s, 7, 1, 15, 9, sill + 4, 2, 1, 2, ROOF, PLASTER);
        // lanterns of cloth under the skirt over both mouths of the passage
        for (int x : new int[]{9, 13}) {
            Eastern.clothLantern(s, x, 1, 9, 1);
            Eastern.clothLantern(s, x, 9, 9, 1);
        }
        // the guard room: the drum, racks, a bench, the ladder, light
        put(s, 12, 6, 3, "minecraft:note_block[instrument=basedrum,note=0,powered=false]");
        put(s, 12, 7, 3, Kit.CLOTH_CARPET);
        Furnish.weaponRack(s, 9, 6, 3, "south", Eastern.WOOD);
        Furnish.weaponRack(s, 13, 6, 7, "north", Eastern.WOOD);
        Furnish.bench(s, 12, 7, 12, 7, 6, "north", Eastern.WOOD);
        fill(s, 10, 6, 3, 10, 9, 3, POST_Y);
        for (int yy = 6; yy <= sill; yy++) {
            put(s, 10, yy, 4, Kit.ladder("south"));
        }
        put(s, 11, 9, 5, Kit.hangingLantern());
        put(s, 12, sill + 3, 5, "minecraft:bell[attachment=ceiling,facing=north,powered=false]");
        put(s, 10, sill + 3, 6, Kit.hangingLantern());
        put(s, 11, sill + 5, 5, Kit.lantern());
        s.connect();
        return s.trimSky(3);
    }
}
