package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The field camp of an army of the style "southern" (see {@link Southern}): the four buildings a camp raises, drawn in
 * the manner of the south. Each takes the ground, the entrance on the south, the role and the counted blocks of the plain
 * building of a camp it stands in for (the barracks hall, the drill yard, the barn and the timber watch tower), so that a
 * camp of a southern kingdom can put them in their places.
 *
 * <p>The camp is built like the forts of the old legions and the missions of the south: a long whitewashed barrack under
 * a low gable of clay tiles carried over a gallery of timber posts, with a bell gable over its door; a palaestra of sand
 * behind a low white wall, with a loggia of round arches under a lean-to of tiles for the weapons; an open store
 * (barchessa) of three arches with a granary loft and a small dovecote tower on its hip roof; and a square watch tower
 * of dressed stone crowned by machicolations and swallowtail merlons under a pavilion of tiles that flies the standard
 * of the kingdom. Cloth (beds, banners) takes the colour of the kingdom's look; everything is lit.
 */
final class SouthernCamp {
    private static final String RENDER = Southern.RENDER;
    private static final String STONE = Southern.STONE;
    private static final String STONE_STAIRS = Southern.STONE_STAIRS;
    private static final String PAVING = Southern.PAVING;
    private static final String TRIM = Southern.TRIM;
    private static final String TRIM_STAIRS = Southern.TRIM_STAIRS;
    private static final String TRIM_SLAB = Southern.TRIM_SLAB;
    private static final String CARVED = Southern.CARVED;
    private static final Kit.Roof TILES = Southern.TILES;
    private static final String BEAM = Southern.BEAM;
    private static final String PLANKS = Southern.PLANKS;
    private static final String FENCE = Southern.FENCE;
    private static final String DOOR = Southern.DOOR;
    private static final String WOOD = Southern.WOOD;
    private static final String COTTO = Southern.COTTO;
    private static final String IRON = Southern.IRON;
    private static final String POT = Southern.POT;
    private static final String AIR = Structure.AIR;
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String SAND = "minecraft:sand";
    private static final String CLOTH = "red";

    private SouthernCamp() {
    }

    /**
     * The four buildings of the camp: the barracks, the drill yard, the store and the watch tower.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("southern_camp_barracks", "defense", "barracks", "kingdomworld:barracks", 6, 0, 10,
                barracks(), "barracks", 1, "").params("\"bunks\": 12").at("door", 6, 1, 9));
        list.add(new BuildingGenerator.Building("southern_camp_yard", "defense", "yard", "minecraft:target", 6, 1, 10, yard(),
                "none", 1, "").at("door", 6, 1, 9));
        list.add(new BuildingGenerator.Building("southern_camp_store", "farming", "warehouse", "kingdomworld:storehouse", 6, 1, 10,
                store(), "warehouse", 1, "").params("\"containers\": 12").at("door", 6, 1, 9));
        list.add(new BuildingGenerator.Building("southern_camp_tower", "defense", "tower", "minecraft:oak_fence", 3, 0, 6, tower(),
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
     * A standard flying from a roof: a pole at (x, y, z) on the block under it and the banner of the kingdom on the pole,
     * looking south.
     *
     * @return the layer above the banner
     */
    private static int standard(Structure s, int x, int y, int z) {
        put(s, x, y, z, FENCE);
        put(s, x, y + 1, z, Kit.banner(CLOTH, 0));
        return y + 2;
    }

    /**
     * A training dummy on the ground at layer {@code y - 1}: a post, a body of straw with arms of poles, a head looking south.
     */
    private static void dummy(Structure s, int x, int y, int z) {
        put(s, x, y, z, FENCE);
        put(s, x, y + 1, z, HAY);
        put(s, x, y + 2, z, "minecraft:carved_pumpkin[facing=south]");
        put(s, x - 1, y + 1, z, FENCE);
        put(s, x + 1, y + 1, z, FENCE);
    }

    // ---------------------------------------------------------------- the barracks

    /**
     * The barracks of the camp: a long hall of whitewashed render on a footing of dressed stone with quoins, under a low
     * gable roof of clay tiles whose ridge runs along the front and whose eaves stand out on a cornice. The front slope
     * reaches out over a gallery (corredor) along the whole front: posts of timber with brackets (zapatas) under a beam,
     * a ceiling of planks with lanterns hanging from it, steps up to the door in its middle, a water jar and a trough at
     * its ends. The door has a frame of trim under a carved keystone and the banners of the kingdom beside it; windows
     * with shutters. Over the door, on the front wall, rises a bell gable (espadana) with stepped shoulders, an arched
     * opening holding the bell that calls the company, a band of trim and a little pediment with a copper finial. Inside
     * under a ceiling of dark beams the twelve bunks stand in two rows, their heads against the end walls under shelves
     * and high windows; between the rows the barracks block under the banner of the kingdom, flanked by the racks of
     * swords and two chests of the company, and the mess table with its benches; lanterns hang over the aisles. A
     * woodpile lies under the back eave.
     */
    static Structure barracks() {
        Structure s = new Structure(13, 16, 11);
        // The footing and the cotto floor of the hall, the floor of the gallery before it with steps up to the door.
        fill(s, 0, 0, 0, 12, 0, 0, PAVING);
        fill(s, 0, 0, 1, 12, 0, 8, STONE);
        fill(s, 1, 0, 2, 11, 0, 7, COTTO);
        fill(s, 0, 0, 9, 12, 0, 10, PAVING);
        fill(s, 0, 0, 10, 12, 0, 10, TRIM);
        fill(s, 5, 0, 9, 7, 0, 9, COTTO);
        fill(s, 5, 0, 10, 7, 0, 10, Kit.stairs(Southern.PAVING_STAIRS, "north"));
        // The walls with their quoins, the string course at the eaves and the ceiling of beams.
        Southern.walls(s, 0, 1, 12, 8, 1, 5);
        Southern.band(s, 0, 1, 12, 8, 5);
        Southern.beamCeiling(s, 1, 2, 11, 7, 5, false);
        // The door in a frame of trim under a carved keystone, the banners of the kingdom beside it.
        fill(s, 5, 1, 8, 5, 3, 8, TRIM);
        fill(s, 7, 1, 8, 7, 3, 8, TRIM);
        put(s, 6, 3, 8, CARVED);
        s.door(6, 1, 8, DOOR, "north", "left");
        put(s, 4, 3, 9, Kit.wallBanner(CLOTH, "south"));
        put(s, 8, 3, 9, Kit.wallBanner(CLOTH, "south"));
        // Windows with shutters on the front and the back, high windows over the bunks in the end walls.
        Southern.window(s, 2, 2, 8, "south", 1, 2, true, false);
        Southern.window(s, 10, 2, 8, "south", 1, 2, true, false);
        Southern.window(s, 3, 2, 1, "north", 1, 2, true, false);
        Southern.window(s, 9, 2, 1, "north", 1, 2, true, false);
        for (int z : new int[]{3, 6}) {
            Southern.window(s, 0, 3, z, "west", 1, 2, false, false);
            Southern.window(s, 12, 3, z, "east", 1, 2, false, false);
        }
        // The gallery (corredor) along the front: posts of timber with brackets (zapatas) under a beam, the eave of the
        // roof over them, lanterns hanging under the beam.
        for (int x : new int[]{0, 4, 8, 12}) {
            fill(s, x, 1, 10, x, 4, 10, Kit.log("minecraft:spruce_log", "y"));
            put(s, x - 1, 4, 10, Kit.upside(Southern.PLANK_STAIRS, "east"));
            put(s, x + 1, 4, 10, Kit.upside(Southern.PLANK_STAIRS, "west"));
        }
        fill(s, 0, 5, 10, 12, 5, 10, Kit.log(BEAM, "x"));
        fill(s, 0, 5, 9, 12, 5, 9, PLANKS);
        for (int x : new int[]{2, 6, 10}) {
            put(s, x, 4, 9, Kit.hangingLantern());
        }
        // The bell gable on the front wall over the door: a wall of render between quoins with scrolled shoulders, a
        // round arch on a sill of trim holding the bell under a carved keystone, a band of trim, a pediment and a finial.
        fill(s, 3, 7, 8, 9, 11, 8, RENDER);
        fill(s, 3, 7, 8, 3, 11, 8, STONE);
        fill(s, 9, 7, 8, 9, 11, 8, STONE);
        for (int side = -1; side <= 1; side += 2) {
            put(s, 6 + 4 * side, 7, 8, RENDER);
            put(s, 6 + 4 * side, 8, 8, Kit.stairs(TRIM_STAIRS, side < 0 ? "east" : "west"));
        }
        fill(s, 5, 7, 8, 7, 7, 8, TRIM);
        fill(s, 5, 8, 8, 7, 10, 8, AIR);
        put(s, 5, 10, 8, Kit.upside(TRIM_STAIRS, "west"));
        put(s, 7, 10, 8, Kit.upside(TRIM_STAIRS, "east"));
        put(s, 6, 11, 8, CARVED);
        put(s, 6, 10, 8, Southern.BELL);
        fill(s, 3, 12, 8, 9, 12, 8, TRIM);
        put(s, 4, 13, 8, Kit.stairs(TRIM_STAIRS, "east"));
        fill(s, 5, 13, 8, 7, 13, 8, RENDER);
        put(s, 8, 13, 8, Kit.stairs(TRIM_STAIRS, "west"));
        put(s, 5, 14, 8, Kit.stairs(TRIM_STAIRS, "east"));
        put(s, 6, 14, 8, CARVED);
        put(s, 7, 14, 8, Kit.stairs(TRIM_STAIRS, "west"));
        Southern.finial(s, 6, 15, 8);
        // The low gable roof of tiles over the hall and the gallery, the gables in the end walls.
        Southern.lowRoof(s, 6, TILES, TRIM_STAIRS, RENDER, new int[]{-1, 0, 13, 10, Southern.RIDGE_X});
        // The gallery: a water jar and a trough at its ends.
        Southern.amphora(s, 1, 1, 9);
        put(s, 11, 1, 9, "minecraft:water_cauldron[level=3]");
        // The woodpile under the back eave.
        fill(s, 5, 1, 0, 7, 1, 0, Kit.log("minecraft:oak_log", "x"));
        put(s, 6, 2, 0, Kit.log("minecraft:oak_log", "x"));
        // The twelve bunks in two rows, their heads against the end walls, shelves over them.
        for (int z = 2; z <= 7; z++) {
            Furnish.bed(s, 2, 1, z, "west", CLOTH);
            Furnish.bed(s, 10, 1, z, "east", CLOTH);
            put(s, 1, 2, z, Kit.trapdoor("minecraft:" + WOOD + "_trapdoor", "east", true));
            put(s, 11, 2, z, Kit.trapdoor("minecraft:" + WOOD + "_trapdoor", "west", true));
        }
        put(s, 1, 3, 2, Furnish.candles(1));
        put(s, 11, 3, 7, Furnish.candles(1));
        put(s, 1, 3, 5, POT);
        put(s, 11, 3, 4, POT);
        // Between the rows: the barracks block under the banner, the racks of swords, two chests of the company.
        put(s, 6, 1, 2, "kingdomworld:barracks[facing=south]");
        put(s, 6, 3, 2, Kit.wallBanner(CLOTH, "south"));
        Furnish.weaponRack(s, 4, 1, 2, "south", WOOD);
        Furnish.weaponRack(s, 8, 1, 2, "south", WOOD);
        Furnish.chest(s, 5, 1, 2, "south");
        Furnish.chest(s, 7, 1, 2, "south");
        // The mess table with its benches and the light.
        Furnish.longTable(s, 5, 5, 7, 5, 1, WOOD);
        Furnish.bench(s, 5, 4, 7, 4, 1, "south", WOOD);
        Furnish.bench(s, 5, 6, 7, 6, 1, "north", WOOD);
        put(s, 5, 2, 5, Furnish.candles(2));
        put(s, 6, 2, 5, "minecraft:" + WOOD + "_pressure_plate[powered=false]");
        put(s, 7, 2, 5, POT);
        for (int[] l : new int[][]{{3, 3}, {3, 6}, {9, 3}, {9, 6}, {6, 5}}) {
            put(s, l[0], 4, l[1], Kit.hangingLantern());
        }
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- the drill yard

    /**
     * The drill yard of the camp, a palaestra level with the ground: a field of sand inside a low wall of render with a
     * coping of trim, entered through a gap between two piers with lanterns, a path of cotto from the gate. Along its
     * west side a loggia of three round arches on stone columns under a lean-to of tiles, its ceiling of dark beams, holds
     * the weapon corner: the racks of swords, a barrel of arrows, a chest of gear, a stone bench, a trough and a water jar,
     * the banners of the kingdom and two barred windows in its back wall and lanterns in its bays; before its end, under
     * the overhang of its roof, straw for the dummies. At the back of the field a white wall is the butt of three archery
     * targets on straw with the banners of the kingdom between them; in the field two training dummies and a post for the
     * sword. A dark cypress in the back corner, a lamp post in the front one and a lantern on a pier of the east wall.
     */
    static Structure yard() {
        Structure s = new Structure(13, 11, 11);
        // The ground: paving under the walls and the front, sand in the field, a path of cotto from the gate.
        fill(s, 4, 0, 0, 12, 0, 10, PAVING);
        fill(s, 0, 0, 9, 3, 0, 10, PAVING);
        fill(s, 4, 0, 1, 11, 0, 9, SAND);
        fill(s, 5, 0, 8, 7, 0, 10, COTTO);
        // The cypress in the back corner, before the walls so that the walls keep their coping.
        Southern.cypress(s, 11, 1, 1, 8);
        // The loggia on the west side: its walls, its arches, its ceiling, the lean-to roof over it.
        Southern.walls(s, 0, 0, 3, 8, 1, 4);
        Southern.loggia(s, 0, 0, 3, 8, 1, 3, 3, "east", false);
        Southern.band(s, 0, 0, 3, 8, 4);
        fill(s, 0, 5, 0, 0, 6, 8, RENDER);
        Southern.window(s, 0, 2, 2, "west", 1, 2, false, false, IRON);
        Southern.window(s, 0, 2, 6, "west", 1, 2, false, false, IRON);
        // The butt of the targets at the back.
        fill(s, 4, 1, 0, 12, 3, 0, RENDER);
        fill(s, 4, 1, 0, 12, 1, 0, STONE);
        fill(s, 12, 1, 0, 12, 3, 0, STONE);
        fill(s, 4, 4, 0, 12, 4, 0, Kit.slab(TRIM_SLAB));
        Southern.lowRoof(s, 5, TILES, TRIM_STAIRS, RENDER, new int[]{-1, -1, 4, 9, Southern.LEAN_WEST});
        // The low wall on the east side and the front, the piers of the gate and of the east wall with their lanterns.
        fill(s, 12, 1, 1, 12, 1, 10, RENDER);
        fill(s, 12, 2, 1, 12, 2, 10, Kit.slab(TRIM_SLAB));
        fill(s, 0, 1, 9, 0, 1, 10, RENDER);
        fill(s, 0, 2, 9, 0, 2, 10, Kit.slab(TRIM_SLAB));
        fill(s, 1, 1, 10, 4, 1, 10, RENDER);
        fill(s, 1, 2, 10, 4, 2, 10, Kit.slab(TRIM_SLAB));
        fill(s, 8, 1, 10, 11, 1, 10, RENDER);
        fill(s, 8, 2, 10, 11, 2, 10, Kit.slab(TRIM_SLAB));
        for (int x : new int[]{4, 8}) {
            fill(s, x, 1, 10, x, 2, 10, STONE);
            put(s, x, 3, 10, TRIM);
            put(s, x, 4, 10, Kit.lantern());
        }
        fill(s, 12, 1, 10, 12, 2, 10, STONE);
        fill(s, 12, 1, 5, 12, 2, 5, STONE);
        put(s, 12, 3, 5, Kit.lantern());
        // The weapon corner in the loggia.
        put(s, 1, 1, 1, "minecraft:grindstone[face=floor,facing=east]");
        put(s, 1, 1, 2, "minecraft:grindstone[face=floor,facing=east]");
        put(s, 1, 1, 3, Kit.barrel());
        Furnish.chest(s, 1, 1, 4, "east");
        Southern.stoneBench(s, 1, 5, 1, 6, 1, "east");
        put(s, 1, 1, 7, "minecraft:water_cauldron[level=3]");
        Southern.amphora(s, 2, 1, 7);
        put(s, 1, 3, 3, Kit.wallBanner(CLOTH, "east"));
        put(s, 1, 3, 5, Kit.wallBanner(CLOTH, "east"));
        // The nook before the loggia: straw for the dummies.
        put(s, 1, 1, 9, HAY);
        put(s, 2, 1, 9, HAY);
        put(s, 1, 2, 9, HAY);
        // The targets on straw before the butt.
        for (int x : new int[]{5, 7, 9}) {
            put(s, x, 1, 1, HAY);
            put(s, x, 2, 1, "minecraft:target[power=0]");
        }
        put(s, 6, 3, 1, Kit.wallBanner(CLOTH, "south"));
        put(s, 8, 3, 1, Kit.wallBanner(CLOTH, "south"));
        // Two dummies and a post for the sword in the field, a lamp post by the targets.
        dummy(s, 6, 1, 6);
        dummy(s, 10, 1, 6);
        fill(s, 8, 1, 4, 8, 2, 4, Kit.log("minecraft:stripped_oak_log", "y"));
        Southern.lampPost(s, 11, 1, 9);
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- the store

    /**
     * The store of the camp, an open barn of the south (barchessa) level with the ground: walls of whitewashed render with
     * quoins, opening to the front in three round arches on stone piers, a wide one in the middle for the carts and a
     * narrower one on each side; over them the granary loft behind small barred windows, the banner of the kingdom between
     * them; a low hip roof of tiles with deep eaves and on its middle a small square dovecote tower of render with openings
     * on its four faces, bands of trim and a pyramid of tiles with a copper finial, carried inside on a ring of beams over
     * posts. Below are the two storehouse blocks of the army among fourteen barrels and chests along the walls, straw, sacks
     * and amphorae; a ladder climbs to the loft with its straw, sacks of grain and crates; lanterns hang on every floor, in
     * the middle arch and on lamp posts at the corners of the apron, straw lies under the west eave and crates under the
     * east one.
     */
    static Structure store() {
        Structure s = new Structure(13, 21, 11);
        // The ground: paving, a footing under the walls, a border of trim at the front, cotto before the middle arch.
        fill(s, 0, 0, 0, 12, 0, 10, PAVING);
        fill(s, 1, 0, 1, 11, 0, 8, STONE);
        fill(s, 2, 0, 2, 10, 0, 7, PAVING);
        fill(s, 0, 0, 10, 12, 0, 10, TRIM);
        fill(s, 5, 0, 8, 7, 0, 10, COTTO);
        // The walls up to the eaves, the string course at the floor of the loft, the loft floor of beams.
        Southern.walls(s, 1, 1, 11, 8, 1, 8);
        Southern.band(s, 1, 1, 11, 8, 5);
        Southern.beamCeiling(s, 2, 2, 10, 7, 5, false);
        // The three arches of the front.
        Southern.arcade(s, 1, 8, 4, 8, 1, 4, 2, TRIM);
        Southern.arcade(s, 4, 8, 8, 8, 1, 4, 3, TRIM);
        Southern.arcade(s, 8, 8, 11, 8, 1, 4, 2, TRIM);
        put(s, 6, 4, 8, Kit.hangingLantern());
        // Barred windows: the loft over the arches, the back and the ends; the banner of the kingdom over the middle arch.
        Southern.window(s, 3, 6, 8, "south", 1, 2, true, false, IRON);
        Southern.window(s, 9, 6, 8, "south", 1, 2, true, false, IRON);
        put(s, 6, 7, 9, Kit.wallBanner(CLOTH, "south"));
        Southern.window(s, 3, 2, 1, "north", 1, 2, false, false, IRON);
        Southern.window(s, 9, 2, 1, "north", 1, 2, false, false, IRON);
        Southern.window(s, 6, 6, 1, "north", 1, 2, false, false, IRON);
        Southern.window(s, 1, 6, 4, "west", 1, 2, false, false, IRON);
        Southern.window(s, 11, 6, 4, "east", 1, 2, false, false, IRON);
        Southern.window(s, 1, 2, 5, "west", 1, 2, false, false, IRON);
        Southern.window(s, 11, 2, 5, "east", 1, 2, false, false, IRON);
        // The ring of beams that carries the dovecote, on posts standing on the loft floor.
        for (int[] c : new int[][]{{4, 2}, {8, 2}, {4, 6}, {8, 6}}) {
            fill(s, c[0], 6, c[1], c[0], 7, c[1], Kit.log(BEAM, "y"));
        }
        s.beam(4, 2, 8, 6, 8, BEAM);
        // The dovecote: walls of render from the eaves, openings, a band and a ceiling at its top.
        Southern.walls(s, 4, 2, 8, 6, 9, 14);
        Southern.band(s, 4, 2, 8, 6, 11);
        Southern.band(s, 4, 2, 8, 6, 15);
        fill(s, 5, 15, 3, 7, 15, 5, PLANKS);
        for (int y = 12; y <= 13; y++) {
            put(s, 6, y, 2, AIR);
            put(s, 6, y, 6, AIR);
            put(s, 4, y, 4, AIR);
            put(s, 8, y, 4, AIR);
        }
        // The hip roof, the inside of the dovecote cleared, its cornice and its pyramid.
        Southern.lowRoof(s, 9, TILES, TRIM_STAIRS, null, new int[]{0, 0, 12, 9, Southern.HIP});
        fill(s, 5, 9, 3, 7, 14, 5, AIR);
        put(s, 6, 14, 4, Kit.hangingLantern());
        Southern.cornice(s, 3, 1, 9, 7, 15, TRIM_STAIRS);
        int apex = Southern.pyramid(s, 3, 1, 9, 7, 16, TILES);
        Southern.finial(s, 6, apex, 4);
        // The ladder to the loft against the back wall.
        for (int y = 1; y <= 5; y++) {
            put(s, 6, y, 2, Kit.ladder("south"));
        }
        // The stores of the army: two storehouse blocks among barrels and chests along the back and the side walls.
        put(s, 4, 1, 2, "kingdomworld:storehouse");
        put(s, 8, 1, 2, "kingdomworld:storehouse");
        for (int[] b : new int[][]{{2, 1, 2}, {3, 1, 2}, {2, 2, 2}, {9, 1, 2}, {10, 1, 2}, {10, 2, 2}, {2, 1, 4}, {2, 2, 4},
                {10, 1, 4}, {10, 2, 4}}) {
            put(s, b[0], b[1], b[2], Kit.barrel());
        }
        Furnish.chest(s, 5, 1, 2, "south");
        Furnish.chest(s, 7, 1, 2, "south");
        Furnish.chest(s, 2, 1, 3, "east");
        Furnish.chest(s, 10, 1, 3, "west");
        // Straw, sacks, crates and amphorae of oil and wine.
        put(s, 2, 1, 6, HAY);
        put(s, 2, 1, 7, HAY);
        put(s, 2, 2, 7, HAY);
        put(s, 10, 1, 6, HAY);
        put(s, 10, 1, 7, HAY);
        Furnish.sack(s, 3, 1, 7, false);
        Furnish.sack(s, 9, 1, 7, true);
        Furnish.sack(s, 10, 2, 7, false);
        Furnish.crate(s, 8, 1, 4, WOOD);
        Furnish.crate(s, 8, 1, 5, WOOD);
        Furnish.sack(s, 8, 2, 4, true);
        Southern.amphora(s, 4, 1, 4);
        Southern.amphora(s, 4, 1, 5);
        Southern.amphora(s, 3, 1, 5);
        for (int[] l : new int[][]{{4, 4, 4}, {8, 4, 6}, {6, 4, 4}}) {
            put(s, l[0], l[1], l[2], Kit.hangingLantern());
        }
        // The loft: straw, sacks of grain, crates, lanterns on the ring of beams.
        fill(s, 2, 6, 2, 3, 6, 3, HAY);
        put(s, 2, 7, 2, HAY);
        fill(s, 9, 6, 2, 10, 6, 3, HAY);
        put(s, 10, 7, 2, HAY);
        for (int[] g : new int[][]{{2, 6, 6}, {2, 6, 7}, {3, 6, 7}, {2, 7, 7}, {10, 6, 6}, {10, 6, 7}, {9, 6, 7}, {10, 7, 7}}) {
            Furnish.sack(s, g[0], g[1], g[2], (g[0] + g[2]) % 2 == 0);
        }
        Furnish.crate(s, 5, 6, 7, WOOD);
        Furnish.crate(s, 7, 6, 7, WOOD);
        put(s, 4, 7, 4, Kit.hangingLantern());
        put(s, 8, 7, 4, Kit.hangingLantern());
        put(s, 6, 7, 6, Kit.hangingLantern());
        // The apron: lamp posts at its corners, straw under the west eave and crates under the east one.
        Southern.lampPost(s, 0, 1, 10);
        Southern.lampPost(s, 12, 1, 10);
        put(s, 0, 1, 6, HAY);
        put(s, 0, 1, 7, HAY);
        put(s, 0, 2, 7, HAY);
        Furnish.crate(s, 12, 1, 6, WOOD);
        Furnish.crate(s, 12, 1, 7, WOOD);
        Furnish.sack(s, 12, 2, 7, false);
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- the watch tower

    /**
     * The watch tower of the camp: a square shaft of dressed stone five wide on a paved plinth with a step, a door under a
     * carved keystone between lanterns on iron brackets and the banner of the kingdom over it; inside a guard room with
     * arrow slits, a rack of swords, a barrel and a bench, a room above with a chest, a table and a stool and narrow
     * windows on four sides, a ladder through both floors to the top. There a walk behind machicolations on corbels of
     * trim and swallowtail merlons, and high over the walk, clear of the merlons, a pavilion on four posts and a ring of
     * beams under a pyramid of tiles, a lantern hanging from it on a chain and the standard of the kingdom flying from its
     * point.
     */
    static Structure tower() {
        Structure s = new Structure(7, 26, 7);
        // The plinth with a border of trim and the step to the door.
        fill(s, 0, 0, 0, 6, 0, 6, TRIM);
        int walk = Southern.towerShaft(s, 1, 1, 0, 5, 13, STONE, false, "south");
        put(s, 3, 0, 6, Kit.stairs(TRIM_STAIRS, "north"));
        Southern.wallLantern(s, 2, 3, 6);
        Southern.wallLantern(s, 4, 3, 6);
        put(s, 3, 6, 6, Kit.wallBanner(CLOTH, "south"));
        // The crown: machicolations and swallowtail merlons around the walk.
        Southern.machicolations(s, 1, 1, 5, 5, walk, STONE, STONE_STAIRS, true);
        // The pavilion over the walk: four posts and a pyramid of tiles with a lantern under it.
        for (int[] p : new int[][]{{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
            fill(s, p[0], walk, p[1], p[0], walk + 4, p[1], FENCE);
        }
        s.beam(1, 1, 5, 5, walk + 5, BEAM);
        int apex = Southern.pyramid(s, 0, 0, 6, 6, walk + 6, TILES);
        standard(s, 3, apex, 3);
        Furnish.chandelier(s, 3, 3, apex - 2, 4);
        // Arrow slits in the guard room.
        Southern.window(s, 1, 3, 3, "west", 1, 2, false, false);
        Southern.window(s, 5, 3, 3, "east", 1, 2, false, false);
        Southern.window(s, 3, 3, 1, "north", 1, 2, false, false);
        // The guard room: a rack of swords, a barrel, a bench.
        Furnish.weaponRack(s, 4, 1, 2, "west", WOOD);
        put(s, 4, 1, 3, Kit.barrel());
        Furnish.chair(s, 4, 1, 4, "west", WOOD);
        // The room above: a chest, a table and a stool.
        Furnish.chest(s, 4, 8, 2, "west");
        Furnish.table(s, 4, 8, 4, WOOD);
        Furnish.chair(s, 4, 8, 3, "south", WOOD);
        s.connect();
        s.trimSky(3);
        return s;
    }
}
