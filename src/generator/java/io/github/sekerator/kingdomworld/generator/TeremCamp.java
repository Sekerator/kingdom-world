package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The field camp of an army of the style "terem" (see {@link Terem}): the four buildings a host raises in the field, each
 * with the ground, the entrance, the role and the counted blocks of the plain building of a camp it stands for (the
 * barracks hall, the drill yard, the barn and the wooden watch tower), so that a camp of a terem kingdom is built of them.
 * The soldiers sleep in a great tent of linen striped white and red on a sill of logs, its steep slopes girt with red
 * bands, with the banner of the host on its pole; they train in a yard behind a palisade of sharpened logs, with butts of
 * straw, straw dummies in pointed helmets, the weapons under an awning of striped canvas, the alarm bell under a little
 * tent and a fire with its pot; the stores of the host lie in a log granary on stumps whose carved gable reaches out over a
 * porch on posts, beside a rick of hay and a cart; and the watch looks out from a log cabin on four tall legs under a tent
 * of shingles with a gilt ball and the banner on its point.
 *
 * <p>The canvas is a fixed accent (white wool for the walls, smooth quartz for the slopes of the tents); the red of the
 * stripes, bands, banners and beds is the cloth of the look, so that a camp shows the colour of its kingdom.
 */
final class TeremCamp {
    private static final String LOG = Terem.LOG;
    private static final String POST = Terem.POST;
    private static final String PLANKS = Terem.PLANKS;
    private static final String FLOOR = Terem.FLOOR;
    private static final String FENCE = Terem.FENCE;
    private static final String CARVE = Terem.CARVE;
    private static final String CARVE_STAIRS = Terem.CARVE_STAIRS;
    private static final String CARVE_SLAB = Terem.CARVE_SLAB;
    private static final String CARVE_FENCE = Terem.CARVE_FENCE;
    private static final String FOOTING = Terem.FOOTING;

    /** The canvas of the tents: white linen on the walls (a fixed accent). */
    private static final String CANVAS = "minecraft:white_wool";
    /** The slopes of the tents (a fixed accent). */
    private static final String CANVAS_STAIRS = "minecraft:smooth_quartz_stairs";
    private static final String CANVAS_BLOCK = "minecraft:smooth_quartz";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String GRINDSTONE = "minecraft:grindstone[face=floor,facing=";

    /** Kinds of the rings of a tent of canvas: a slope of stairs, a band of red cloth, solid canvas. */
    private static final int EAVE = 0;
    private static final int BAND = 1;
    private static final int SOLID = 2;

    private TeremCamp() {
    }

    /**
     * The four buildings of the camp: the soldiers' tent, the training yard, the store and the watch tower.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("terem_camp_barracks", "defense", "barracks", "kingdomworld:barracks", 6, 0, 10,
                barracks(), "barracks", 1, "").params("\"bunks\": 12").at("door", 6, 0, 10));
        list.add(new BuildingGenerator.Building("terem_camp_yard", "defense", "yard", "minecraft:target", 6, 1, 10,
                yard(), "none", 1, "").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("terem_camp_store", "farming", "warehouse", "kingdomworld:storehouse", 6, 1, 10,
                store(), "warehouse", 1, "").params("\"containers\": 12").at("door", 5, 3, 8));
        list.add(new BuildingGenerator.Building("terem_camp_tower", "defense", "tower", "minecraft:oak_fence", 3, 0, 6,
                tower(), "tower", 1, "").at("door", 3, 0, 5));
        return list;
    }

    // ---------------------------------------------------------------- shared shapes

    /**
     * A stair on the rim of the rectangle {@code ax..bx, az..bz}, its high side to the middle; a corner stair at a corner.
     */
    private static String rimStair(String block, int x, int z, int ax, int az, int bx, int bz, boolean upside) {
        String half = upside ? "top" : "bottom";
        boolean w = x == ax;
        boolean e = x == bx;
        boolean n = z == az;
        boolean so = z == bz;
        String facing;
        String shape = "straight";
        if (n && (w || e)) {
            facing = "south";
            shape = w ? "outer_left" : "outer_right";
        } else if (so && (w || e)) {
            facing = "north";
            shape = w ? "outer_right" : "outer_left";
        } else if (n) {
            facing = "south";
        } else if (so) {
            facing = "north";
        } else {
            facing = w ? "east" : "west";
        }
        return block + "[facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=false]";
    }

    /**
     * A tent of canvas over the rectangle {@code x1..x2, z1..z2}, built ring by ring from layer {@code y} up as a shell, so
     * that it is open inside up to its ridge: each step of the profile is one layer, the ring drawn {@code inset} blocks
     * inside the rectangle as a slope of stairs, a band of red cloth or solid canvas. A ring that has narrowed to a line is
     * the ridge, solid.
     *
     * @param profile pairs {inset, kind} from the bottom up
     * @return the layer above the last
     */
    private static int canvasTent(Structure s, int x1, int z1, int x2, int z2, int y, int[][] profile) {
        int layer = y;
        for (int[] step : profile) {
            int ax = x1 + step[0];
            int bx = x2 - step[0];
            int az = z1 + step[0];
            int bz = z2 - step[0];
            boolean line = ax == bx || az == bz;
            for (int z = az; z <= bz; z++) {
                for (int x = ax; x <= bx; x++) {
                    if (!line && x != ax && x != bx && z != az && z != bz) {
                        continue;
                    }
                    String state = line ? CANVAS_BLOCK : switch (step[1]) {
                        case EAVE -> rimStair(CANVAS_STAIRS, x, z, ax, az, bx, bz, false);
                        case BAND -> Kit.CLOTH;
                        default -> CANVAS_BLOCK;
                    };
                    s.set(x, layer, z, state);
                }
            }
            layer++;
        }
        return layer;
    }

    /**
     * A standard of the host: a pole of fence posts from layer {@code y} up {@code height} layers and a banner of the cloth
     * on its top, looking south.
     */
    private static void standard(Structure s, int x, int y, int z, int height) {
        s.fill(x, y, z, x, y + height - 1, z, FENCE);
        s.set(x, y + height, z, Kit.banner("red", 0));
    }

    /**
     * A rack of spears: a fence post with a spearhead of a lightning rod on it.
     */
    private static void spear(Structure s, int x, int y, int z) {
        s.set(x, y, z, FENCE).set(x, y + 1, z, Terem.ROD);
    }

    // ---------------------------------------------------------------- the soldiers' tent

    /**
     * The soldiers' tent (barracks): a great tent on a footing of rough stone and a sill of logs whose ends stick out at the
     * corners, its walls of linen in upright stripes of white and red, its steep slopes rising two for one from wide eaves
     * to a short ridge, girt with a red band above the eaves and another under the ridge; on the ridge two copper points and
     * the pole with the banner of the host. The door is under a red awning on two carved posts, between two lanterns under
     * the eaves. Inside, on a floor of boards and open up to the ridge: twelve bunks in two rows along the side walls, the
     * barracks block of the host by the back wall between two racks with a chest and a barrel and the banner over it, a
     * rack and a shield between the bunks of each row, a long table with benches under a chandelier of candles on a long
     * chain, a red runner to the door and four lanterns on chains.
     */
    static Structure barracks() {
        Structure s = new Structure(13, 20, 11);
        // The footing, the floor and the sill of logs.
        s.fill(1, 0, 1, 11, 0, 9, FOOTING).fill(2, 0, 2, 10, 0, 8, FLOOR);
        Terem.logWalls(s, 1, 1, 1, 11, 1, 9);
        // The walls of canvas in stripes of white and red, the red band under the eaves, the steep tent.
        for (int y = 2; y <= 6; y++) {
            for (int x = 1; x <= 11; x++) {
                for (int z = 1; z <= 9; z++) {
                    if (x == 1 || x == 11 || z == 1 || z == 9) {
                        boolean red = y == 6 || (x == 1 || x == 11 ? z : x) % 2 == 0;
                        s.set(x, y, z, red ? Kit.CLOTH : CANVAS);
                    }
                }
            }
        }
        int top = canvasTent(s, 0, 0, 12, 10, 6, new int[][]{{0, EAVE}, {1, BAND}, {1, EAVE}, {2, SOLID}, {2, EAVE},
                {3, SOLID}, {3, EAVE}, {4, BAND}, {4, EAVE}, {5, SOLID}});
        s.set(5, top, 5, Terem.ROD).set(7, top, 5, Terem.ROD);
        standard(s, 6, top, 5, 3);
        // The door under a red awning on two carved posts, lanterns under the eaves.
        s.door(6, 1, 9, Terem.DOOR, "north", "left");
        s.set(6, 0, 10, Kit.slab(Terem.FLOOR_SLAB));
        s.fill(5, 0, 10, 5, 3, 10, CARVE_FENCE).fill(7, 0, 10, 7, 3, 10, CARVE_FENCE);
        s.fill(5, 4, 10, 7, 4, 10, Kit.CLOTH);
        s.set(6, 5, 10, Kit.slab(CARVE_SLAB));
        s.set(3, 5, 10, Kit.hangingLantern()).set(9, 5, 10, Kit.hangingLantern());
        // Twelve bunks in two rows along the side walls, racks and shields between them.
        for (int z : new int[]{2, 3, 4, 6, 7, 8}) {
            Furnish.bed(s, 3, 1, z, "west", "red");
            Furnish.bed(s, 9, 1, z, "east", "red");
        }
        s.set(2, 1, 5, GRINDSTONE + "east]").set(10, 1, 5, GRINDSTONE + "west]");
        s.set(2, 3, 5, Kit.wallBanner("red", "east")).set(10, 3, 5, Kit.wallBanner("red", "west"));
        // The back wall: the barracks block between two racks, a chest and a barrel, the banner over them.
        s.set(6, 1, 2, "kingdomworld:barracks[facing=south]");
        s.set(5, 1, 2, GRINDSTONE + "south]").set(7, 1, 2, GRINDSTONE + "south]");
        Furnish.chest(s, 4, 1, 2, "south");
        s.set(8, 1, 2, Kit.barrel());
        s.set(6, 3, 2, Kit.wallBanner("red", "south"));
        s.set(8, 2, 2, Furnish.candles(2));
        // The long table with its benches, the runner to the door, the light.
        Terem.feastTable(s, 6, 4, 6, 6, 1, true);
        s.fill(6, 1, 7, 6, 1, 8, Kit.CLOTH_CARPET);
        Terem.candleChandelier(s, 6, 5, top - 2, top - 8);
        for (int[] p : new int[][]{{4, 3}, {8, 3}, {4, 7}, {8, 7}}) {
            s.fill(p[0], 6, p[1], p[0], 10, p[1], Kit.chain()).set(p[0], 5, p[1], Kit.hangingLantern());
        }
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the training yard

    /**
     * The training yard, level with the ground: trodden earth with grass along the fences, a road of logs in from the gate
     * and a line of logs to shoot from, closed at the back by a palisade of sharpened logs that stops the arrows and on the
     * other sides by a rail fence on posts with lanterns. Before the palisade the butts of straw with three targets and
     * lanterns; on the sides two straw dummies with red shields and pointed helmets; in the front west corner the weapons
     * under an awning of striped canvas on four poles (racks of swords, spears, a barrel of arrows); in the back east
     * corner the alarm bell on two posts under a little tent; in the front east corner a fire with a pot hanging over it
     * from a beam, benches of logs and the standard of the host. The gate is two tall posts under a lintel with carved
     * brackets, a lantern under it and a banner on it.
     */
    static Structure yard() {
        Structure s = new Structure(13, 11, 11);
        // The ground: trodden earth, the road of logs from the gate, the line to shoot from.
        s.fill(0, 0, 0, 12, 0, 10, "minecraft:coarse_dirt");
        s.fill(1, 0, 2, 11, 0, 9, "minecraft:dirt_path");
        s.fill(5, 0, 8, 7, 0, 10, Kit.log(LOG, "x"));
        s.fill(3, 0, 6, 9, 0, 6, Kit.log(POST, "x"));
        // The palisade at the back.
        for (int x = 0; x <= 12; x++) {
            s.fill(x, 1, 0, x, 3, 0, Kit.log(LOG, "y"));
            s.set(x, 4, 0, Kit.stairs(Terem.STAIRS, x % 2 == 0 ? "east" : "west"));
        }
        // The rail fence on posts along the sides and the front.
        for (int z = 1; z <= 10; z++) {
            for (int x : new int[]{0, 12}) {
                boolean post = z == 3 || z == 6 || z == 10;
                if (post) {
                    s.fill(x, 1, z, x, 2, z, Kit.log(POST, "y"));
                    s.set(x, 3, z, z == 10 ? Kit.slab(CARVE_SLAB) : Kit.lantern());
                } else {
                    s.set(x, 1, z, FENCE);
                }
            }
        }
        for (int x : new int[]{1, 2, 3, 9, 10, 11}) {
            s.set(x, 1, 10, FENCE);
        }
        // The gate: two tall posts, the lintel on carved brackets under a cap of shingles, its banner and its lantern.
        for (int x : new int[]{4, 8}) {
            s.fill(x, 1, 10, x, 4, 10, Kit.log(LOG, "y"));
        }
        s.fill(3, 5, 10, 9, 5, 10, Kit.log(POST, "x"));
        s.set(5, 4, 10, Kit.upside(CARVE_STAIRS, "west")).set(7, 4, 10, Kit.upside(CARVE_STAIRS, "east"));
        s.fill(3, 6, 10, 9, 6, 10, Kit.slab(Terem.ROOF.slab()));
        s.set(4, 6, 10, Kit.slab(CARVE_SLAB)).set(8, 6, 10, Kit.slab(CARVE_SLAB));
        s.set(6, 6, 10, Kit.banner("red", 0));
        s.set(6, 4, 10, Kit.hangingLantern());
        // The butts of straw with their targets before the palisade, spare straw in the corner.
        s.fill(3, 1, 1, 9, 2, 1, HAY);
        for (int x : new int[]{4, 6, 8}) {
            s.set(x, 2, 1, "minecraft:target[power=0]");
        }
        s.set(3, 3, 1, Kit.lantern()).set(9, 3, 1, Kit.lantern());
        s.fill(1, 1, 1, 2, 1, 1, HAY).set(1, 2, 1, HAY).set(1, 1, 2, HAY);
        s.set(11, 1, 1, Kit.barrel());
        Furnish.crate(s, 10, 1, 1, "spruce");
        // Two straw dummies with red shields and pointed helmets.
        for (int x : new int[]{2, 10}) {
            s.set(x, 1, 4, FENCE).set(x, 2, 4, HAY).set(x, 3, 4, "minecraft:white_wool").set(x, 4, 4, Terem.ROD);
            s.set(x - 1, 2, 4, FENCE).set(x + 1, 2, 4, FENCE);
            s.set(x, 2, 5, Kit.wallBanner("red", "south"));
        }
        // The weapons under an awning of striped canvas on four poles in the front west corner.
        for (int[] p : new int[][]{{1, 7}, {3, 7}, {1, 9}, {3, 9}}) {
            s.fill(p[0], 1, p[1], p[0], 3, p[1], FENCE);
        }
        for (int z = 6; z <= 10; z++) {
            String cloth = z % 2 == 0 ? Kit.CLOTH : CANVAS;
            s.set(0, 4, z, Kit.stairs(CANVAS_STAIRS, "east")).set(4, 4, z, Kit.stairs(CANVAS_STAIRS, "west"));
            s.fill(1, 4, z, 3, 4, z, cloth).set(2, 5, z, cloth);
            s.set(1, 5, z, Kit.stairs(CANVAS_STAIRS, "east")).set(3, 5, z, Kit.stairs(CANVAS_STAIRS, "west"));
        }
        s.set(2, 1, 9, GRINDSTONE + "south]").set(1, 1, 8, GRINDSTONE + "east]");
        s.set(2, 1, 7, Kit.barrel());
        spear(s, 2, 1, 8);
        spear(s, 3, 1, 8);
        s.set(2, 3, 8, Kit.hangingLantern());
        // The alarm bell on two posts under a little tent in the back east corner.
        s.fill(9, 1, 2, 9, 3, 2, Kit.log(POST, "y")).fill(11, 1, 2, 11, 3, 2, Kit.log(POST, "y"));
        s.fill(9, 4, 2, 11, 4, 2, Kit.log(POST, "x"));
        int bell = Terem.tent(s, Terem.EVERYWHERE, 10, 2, 1, 5, Terem.SQUARE, Terem.SQUARE, Terem.TENT_RISES, 0, 0);
        s.set(10, bell, 2, Terem.ROD);
        s.set(10, 3, 2, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        s.set(9, 4, 3, Kit.hangingLantern());
        // The fire of the yard in the front east corner: a pot hanging over it from a beam on two posts, benches of logs
        // round it, the standard of the host beside it.
        s.fill(9, 1, 8, 9, 3, 8, FENCE).fill(11, 1, 8, 11, 3, 8, FENCE);
        s.fill(9, 4, 8, 11, 4, 8, Kit.log(POST, "x"));
        s.set(10, 1, 8, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        s.set(10, 3, 8, Kit.chain()).set(10, 2, 8, "minecraft:cauldron");
        s.fill(9, 1, 6, 11, 1, 6, Kit.log(POST, "x"));
        s.set(8, 1, 8, Kit.log(POST, "z"));
        standard(s, 11, 1, 9, 5);
        // Grass along the fences where nobody treads; under anything that stands, the path is plain earth.
        for (int z = 2; z <= 9; z++) {
            for (int x : new int[]{1, 11}) {
                if (!s.solid(x, 1, z)) {
                    s.set(x, 0, z, "minecraft:grass_block[snowy=false]");
                }
            }
        }
        for (int[] p : new int[][]{{1, 3}, {1, 6}, {11, 5}, {11, 4}, {1, 5}}) {
            s.set(p[0], 1, p[1], "minecraft:grass");
        }
        for (int z = 0; z < s.sizeZ; z++) {
            for (int x = 0; x < s.sizeX; x++) {
                if (s.get(x, 0, z).startsWith("minecraft:dirt_path") && s.solid(x, 1, z)) {
                    s.set(x, 0, z, "minecraft:coarse_dirt");
                }
            }
        }
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the store

    /**
     * The store of the host: a log granary of six courses standing on stumps, so that the air goes under its floor, its
     * carved gable to the street carried forward on a beam over two posts with carved brackets, so that the roof shelters a
     * porch of boards before the door, two steps up from the ground; in the gable the door of the hay loft, a little window
     * with shutters in each side. Inside, along the walls, twelve barrels and chests of the host and its two storehouse
     * blocks among sacks, a crate and hay, three lanterns; hay in the loft. On the porch sacks, a crate and a barrel under a
     * lantern, the door in a carved frame and the banner of the host hanging from the beam. Beside the granary a rick of
     * hay on its poles and a cart with sacks under a cloth.
     */
    static Structure store() {
        Structure s = new Structure(13, 16, 11);
        // The stumps the granary stands on, so that the air goes under its floor.
        for (int x = 1; x <= 9; x += 2) {
            for (int z : new int[]{1, 4, 7}) {
                s.fill(x, 0, z, x, 1, z, Kit.log(LOG, "y"));
            }
        }
        s.fill(3, 0, 8, 3, 1, 8, Kit.log(LOG, "y")).fill(7, 0, 8, 7, 1, 8, Kit.log(LOG, "y"));
        // The log granary on them: six courses, a floor and a ceiling of boards.
        Terem.logWalls(s, 1, 2, 1, 9, 7, 7);
        s.fill(2, 2, 2, 8, 2, 6, FLOOR).fill(2, 7, 2, 8, 7, 6, PLANKS);
        // The porch of boards before the door, under the beam that carries the gable on two posts with carved brackets;
        // two steps up to it.
        s.fill(1, 2, 8, 9, 2, 8, FLOOR);
        s.fill(1, 0, 9, 1, 6, 9, Kit.log(POST, "y")).fill(9, 0, 9, 9, 6, 9, Kit.log(POST, "y"));
        s.fill(1, 7, 9, 9, 7, 9, Kit.log(LOG, "x")).fill(2, 7, 8, 8, 7, 8, PLANKS);
        s.set(2, 6, 9, Kit.upside(CARVE_STAIRS, "west")).set(8, 6, 9, Kit.upside(CARVE_STAIRS, "east"));
        s.set(5, 1, 10, Kit.stairs(Terem.FLOOR_STAIRS, "north")).set(5, 2, 9, Kit.stairs(Terem.FLOOR_STAIRS, "north"));
        s.set(5, 1, 9, Kit.upside(Terem.FLOOR_STAIRS, "south"));
        // Little windows in the sides, then the roof.
        Terem.window(s, 1, 4, 4, "west", 1, 1, true);
        Terem.window(s, 9, 4, 4, "east", 1, 1, true);
        Terem.gableRoof(s, 1, 1, 9, 9, 7, false, 1);
        // The door of the hay loft in the front gable, hay behind it.
        s.fill(5, 9, 9, 5, 10, 9, Structure.AIR);
        s.set(5, 8, 10, Kit.upside(CARVE_STAIRS, "north"));
        s.fill(2, 8, 2, 3, 8, 7, HAY).fill(7, 8, 2, 8, 8, 7, HAY).fill(4, 8, 2, 6, 8, 4, HAY).fill(3, 9, 3, 7, 9, 4, HAY);
        // The door with its carved frame, the lantern and the banner of the host on the porch.
        s.door(5, 3, 7, Terem.DOOR, "north", "left");
        s.fill(4, 3, 8, 4, 4, 8, CARVE_FENCE).fill(6, 3, 8, 6, 4, 8, CARVE_FENCE);
        s.set(4, 5, 8, Kit.stairs(CARVE_STAIRS, "east")).set(6, 5, 8, Kit.stairs(CARVE_STAIRS, "west")).set(5, 5, 8, CARVE);
        s.set(5, 6, 8, Kit.hangingLantern());
        s.set(5, 7, 10, Kit.wallBanner("red", "south"));
        // On the porch: sacks, a crate and a barrel.
        Furnish.sack(s, 2, 3, 8, false);
        Furnish.sack(s, 3, 3, 8, true);
        Furnish.sack(s, 2, 4, 8, false);
        Furnish.crate(s, 7, 3, 8, "spruce");
        s.set(8, 3, 8, Kit.barrel());
        // Inside: barrels two high and chests along the walls, the storehouse blocks, sacks and crates.
        for (int x : new int[]{2, 3, 7, 8}) {
            s.set(x, 3, 2, Kit.barrel()).set(x, 4, 2, Kit.barrel());
        }
        Furnish.chest(s, 4, 3, 2, "south");
        Furnish.chest(s, 6, 3, 2, "south");
        s.set(5, 3, 2, "kingdomworld:storehouse");
        Furnish.chest(s, 2, 3, 3, "east");
        Furnish.chest(s, 8, 3, 3, "west");
        s.set(2, 3, 4, "kingdomworld:storehouse");
        Furnish.sack(s, 8, 3, 4, false);
        Furnish.sack(s, 8, 4, 4, true);
        Furnish.sack(s, 8, 3, 5, false);
        Furnish.crate(s, 2, 3, 5, "spruce");
        s.set(2, 4, 5, HAY);
        Furnish.sack(s, 3, 3, 6, true);
        s.set(5, 6, 4, Kit.hangingLantern()).set(3, 6, 3, Kit.hangingLantern()).set(7, 6, 3, Kit.hangingLantern());
        s.set(5, 12, 5, Kit.hangingLantern());
        // The rick of hay on its poles.
        s.fill(11, 1, 1, 12, 1, 6, HAY).fill(11, 2, 2, 12, 2, 5, HAY).fill(11, 3, 3, 12, 3, 4, HAY);
        s.fill(11, 4, 3, 11, 5, 3, FENCE).fill(12, 4, 4, 12, 5, 4, FENCE);
        // The cart with sacks under canvas.
        s.fill(11, 1, 8, 11, 1, 9, Kit.log("minecraft:stripped_dark_oak_log", "x"));
        s.set(10, 1, 8, Kit.shutter("minecraft:dark_oak_trapdoor", "west")).set(12, 1, 8, Kit.shutter("minecraft:dark_oak_trapdoor", "east"));
        s.set(10, 1, 9, Kit.shutter("minecraft:dark_oak_trapdoor", "west")).set(12, 1, 9, Kit.shutter("minecraft:dark_oak_trapdoor", "east"));
        s.fill(11, 2, 7, 11, 2, 10, Kit.slab(Terem.SLAB));
        Furnish.sack(s, 11, 3, 8, false);
        s.set(11, 4, 8, "minecraft:white_carpet");
        Furnish.sack(s, 11, 3, 9, true);
        s.set(11, 1, 10, FENCE);
        s.connect();
        // The ground the store does not pave stays as it is.
        for (int z = 0; z < s.sizeZ; z++) {
            for (int x = 0; x < s.sizeX; x++) {
                if (!s.solid(x, 0, z)) {
                    s.skip(x, 0, z);
                }
            }
        }
        return s;
    }

    // ---------------------------------------------------------------- the watch tower

    /**
     * The watch tower: four legs of logs on stones, tied by two rings of hewn logs with knee braces, a mast in the middle
     * with the ladder up its front through the floor of the watch cabin. The cabin is a little log frame on the legs, its
     * log ends crossing at the corners, with an open window under a carved sill and shutters to every side; over it a tall
     * tent of shingles reaching out over its walls, with a gilt ball and the banner of the host on its point. In the cabin
     * hang the alarm bell and two lanterns, and lanterns hang under its floor over the foot of the ladder.
     */
    static Structure tower() {
        Structure s = new Structure(7, 23, 7);
        int f = 9;
        int[][] legs = {{1, 1}, {5, 1}, {1, 5}, {5, 5}};
        for (int[] leg : legs) {
            s.set(leg[0], 0, leg[1], FOOTING);
            s.fill(leg[0], 1, leg[1], leg[0], f - 1, leg[1], Kit.log(LOG, "y"));
        }
        // The rings of hewn logs and their knee braces.
        for (int y : new int[]{4, f - 1}) {
            s.fill(2, y, 1, 4, y, 1, Kit.log(POST, "x")).fill(2, y, 5, 4, y, 5, Kit.log(POST, "x"));
            s.fill(1, y, 2, 1, y, 4, Kit.log(POST, "z")).fill(5, y, 2, 5, y, 4, Kit.log(POST, "z"));
            for (int z : new int[]{1, 5}) {
                s.set(2, y - 1, z, Kit.upside(Terem.STAIRS, "west")).set(4, y - 1, z, Kit.upside(Terem.STAIRS, "east"));
            }
            for (int x : new int[]{1, 5}) {
                s.set(x, y - 1, 2, Kit.upside(Terem.STAIRS, "north")).set(x, y - 1, 4, Kit.upside(Terem.STAIRS, "south"));
            }
        }
        // The mast and the ladder.
        s.set(3, 0, 3, FOOTING).fill(3, 1, 3, 3, f - 1, 3, Kit.log(POST, "y"));
        // The watch cabin on the legs, its floor, a window to every side under a carved sill between open shutters.
        Terem.logWalls(s, 1, f, 1, 5, f + 3, 5);
        s.fill(2, f, 2, 4, f, 4, FLOOR);
        for (int y = 0; y <= f; y++) {
            s.set(3, y, 4, Kit.ladder("south"));
        }
        for (String side : new String[]{"north", "south", "east", "west"}) {
            int[] d = Furnish.step(side);
            int[] a = Furnish.step(Furnish.left(side));
            s.fill(3 + 2 * d[0], f + 2, 3 + 2 * d[1], 3 + 2 * d[0], f + 3, 3 + 2 * d[1], Structure.AIR);
            s.set(3 + 3 * d[0], f + 1, 3 + 3 * d[1], Kit.upside(CARVE_STAIRS, Furnish.opposite(side)));
            for (int y = f + 2; y <= f + 3; y++) {
                s.set(3 + 3 * d[0] + a[0], y, 3 + 3 * d[1] + a[1], Kit.shutter(Kit.SHUTTER, side));
                s.set(3 + 3 * d[0] - a[0], y, 3 + 3 * d[1] - a[1], Kit.shutter(Kit.SHUTTER, side));
            }
        }
        // The tall tent over the cabin with the banner of the host on its point.
        int top = Terem.tent(s, Terem.EVERYWHERE, 3, 3, 3, f + 4, Terem.SQUARE, Terem.SQUARE, new int[]{1, 2, 2, 3}, 0, -1);
        s.set(3, top - 1, 3, Kit.banner("red", 0));
        // The bell and the light.
        s.set(3, f + 3, 3, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        s.set(2, f + 3, 2, Kit.hangingLantern()).set(4, f + 3, 2, Kit.hangingLantern());
        s.set(2, f - 1, 2, Kit.hangingLantern()).set(4, f - 1, 4, Kit.hangingLantern());
        s.connect();
        return s;
    }
}
