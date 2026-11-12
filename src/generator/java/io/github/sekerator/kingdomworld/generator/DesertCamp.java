package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The field camp of an army of the style "desert" (see {@link Desert}): the four buildings a camp is made of, drawn in the
 * manner of the hot dry lands so that a camp of such a kingdom can take them in place of the plain ones. The soldiers sleep
 * in a great pavilion of white canvas on a timber platform, its eaves, hips and skirt in bands of the cloth, a valance of
 * banners round its eaves, a gilt ball on its pole and a pointed porch before its door; they train in a yard of sand behind
 * a low wall of mud brick, entered through a gate under a pointed arch; their stores lie in a
 * block of mud brick under a flat roof with stepped merlons and a wind tower, its gate framed in red stone, the ends of its
 * beams standing out of the walls; and the camp is watched from a slender tower of mud brick on a battered foot, its
 * platform on corbels behind pointed merlons and shaded by a little tent of cloth. Every building has the footprint, the
 * entrance, the role and the blocks that count of the plain building it stands for.
 */
final class DesertCamp {
    private static final String WALL = Desert.WALL;
    private static final String BASE = Desert.BASE;
    private static final String TRIM = Desert.TRIM;
    private static final String CARVED = Desert.CARVED;
    private static final String CLOTH = Desert.CLOTH;
    /** The white of striped tents: white wool, a fixed accent. */
    private static final String CANVAS = "minecraft:white_wool";
    /** Beaten earth of floors: packed mud, a fixed accent. */
    private static final String EARTH = "minecraft:packed_mud";
    /** The sand of the drill ground, laid where the ground was. */
    private static final String SAND = "minecraft:sand";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String RACK = "minecraft:grindstone[face=floor,facing=south]";
    private static final String TARGET = "minecraft:target[power=0]";
    private static final String BARRACKS = "kingdomworld:barracks[facing=south]";
    private static final String STOREHOUSE = "kingdomworld:storehouse";

    private DesertCamp() {
    }

    /**
     * The camp buildings of the style: the barracks pavilion, the drill yard, the store and the watch tower.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("desert_camp_barracks", "defense", "barracks", "kingdomworld:barracks", 6, 0, 10, barracks(),
                "barracks", 1, "").params("\"bunks\": 12").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("desert_camp_yard", "defense", "yard", "minecraft:target", 6, 1, 10, yard(),
                "none", 1, "").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("desert_camp_store", "farming", "warehouse", "kingdomworld:storehouse", 6, 1, 10, store(),
                "warehouse", 1, "").params("\"containers\": 12").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("desert_camp_tower", "defense", "tower", "minecraft:oak_fence", 3, 0, 6, tower(),
                "tower", 1, "").at("door", 3, 1, 6));
        return list;
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

    /** The walls of a block from (x1, z1) to (x2, z2), from layer {@code y1} to {@code y2}. */
    private static void walls(Structure s, int x1, int z1, int x2, int z2, int y1, int y2, String state) {
        fill(s, x1, y1, z1, x2, y2, z1, state);
        fill(s, x1, y1, z2, x2, y2, z2, state);
        fill(s, x1, y1, z1, x1, y2, z2, state);
        fill(s, x2, y1, z1, x2, y2, z2, state);
    }

    /** Leaves out every position of the ground layer that the building does not pave, so that the ground there stays. */
    private static void keepGround(Structure s) {
        for (int z = 0; z < s.sizeZ; z++) {
            for (int x = 0; x < s.sizeX; x++) {
                if (!s.solid(x, 0, z)) {
                    s.skip(x, 0, z);
                }
            }
        }
    }

    /** A lantern on a post of rough stone standing on layer {@code y}. */
    private static void lampPost(Structure s, int x, int y, int z) {
        put(s, x, y, z, Desert.BASE_POST);
        put(s, x, y + 1, z, Kit.lantern());
    }

    /** A pier of a low wall standing on layer {@code y}: a plinth of red stone, two blocks of wall and a cap of red stone. */
    private static void pier(Structure s, int x, int y, int z) {
        put(s, x, y, z, BASE);
        put(s, x, y + 1, z, WALL);
        put(s, x, y + 2, z, WALL);
        put(s, x, y + 3, z, Kit.slab(Desert.BASE_SLAB));
    }

    /** A spear standing in a rack: a timber post with an iron point. */
    private static void spear(Structure s, int x, int y, int z) {
        put(s, x, y, z, Desert.FENCE);
        put(s, x, y + 1, z, Desert.ROD);
    }

    /**
     * A training dummy standing on layer {@code y}: a post, a body of straw with arms of timber along x, a head with a face
     * cut in it looking south and a cap of the cloth.
     */
    private static void dummy(Structure s, int x, int y, int z) {
        put(s, x, y, z, Desert.FENCE);
        put(s, x, y + 1, z, HAY);
        put(s, x - 1, y + 1, z, Desert.FENCE);
        put(s, x + 1, y + 1, z, Desert.FENCE);
        put(s, x, y + 2, z, "minecraft:carved_pumpkin[facing=south]");
        put(s, x, y + 3, z, Desert.CLOTH_CARPET);
    }

    // ---------------------------------------------------------------- tents

    /**
     * The walls of a tent of white canvas on the lines {@code x1}, {@code x2}, {@code z1} and {@code z2}, from layer
     * {@code y1} to {@code y2}, a pole of timber at each corner.
     */
    private static void tentWalls(Structure s, int x1, int z1, int x2, int z2, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            fill(s, x1, y, z1, x2, y, z1, CANVAS);
            fill(s, x1, y, z2, x2, y, z2, CANVAS);
            fill(s, x1, y, z1, x1, y, z2, CANVAS);
            fill(s, x2, y, z1, x2, y, z2, CANVAS);
            for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
                put(s, c[0], y, c[1], Kit.log(Desert.POST, "y"));
            }
        }
    }

    /**
     * The roof of a tent over the walls on the lines {@code x1}, {@code x2}, {@code z1} and {@code z2}: rings of white canvas
     * stepping in from the walls, ring {@code d} (counted from the walls) rising to layer {@code rise[d]} (the last value for
     * the rings further in) and closed down to the ring before it, so that a roof may rise faster near its top. The edge of
     * the eaves and the top are of the cloth; with {@code ribs} also the hips and the middle seam of every face, so that the
     * roof shows eight gores of white between bands of colour running up to the pole.
     */
    private static void tentRoof(Structure s, int x1, int z1, int x2, int z2, int[] rise, boolean ribs) {
        int mx = (x1 + x2) / 2;
        int mz = (z1 + z2) / 2;
        int top = Math.min(Math.min((x2 - x1) / 2, (z2 - z1) / 2), rise.length - 1);
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                int dx = Math.min(x - x1, x2 - x);
                int dz = Math.min(z - z1, z2 - z);
                int d = Math.min(Math.min(dx, dz), rise.length - 1);
                int y = rise[d];
                int from = d == 0 ? y : Math.min(y, rise[d - 1] + 1);
                boolean rib = d == 0 || d == top || ribs && dx == dz;
                fill(s, x, from, z, x, y, z, rib ? CLOTH : CANVAS);
            }
        }
    }

    /**
     * A valance of banners of the cloth round the eaves of a tent at layer {@code y}: a banner hanging from every cell of the
     * eaves outside the walls on the lines {@code x1}, {@code x2}, {@code z1} and {@code z2} (corners left out).
     */
    private static void valance(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1 + 1; x < x2; x++) {
            put(s, x, y, z1 - 1, Kit.wallBanner("red", "north"));
            put(s, x, y, z2 + 1, Kit.wallBanner("red", "south"));
        }
        for (int z = z1 + 1; z < z2; z++) {
            put(s, x1 - 1, y, z, Kit.wallBanner("red", "west"));
            put(s, x2 + 1, y, z, Kit.wallBanner("red", "east"));
        }
    }

    // ---------------------------------------------------------------- the barracks

    /**
     * The barracks of the camp: a great pavilion of white canvas on a platform of timber with a footing of red stone, its low
     * walls between corner poles with a skirt of the cloth and screened windows, its roof flaring out at the eaves and rising
     * steeply to a ridge pierced by the centre pole with a gilt ball; the edge of the eaves, the hips and the ridge in bands of
     * the cloth, a valance of banners of the cloth round the eaves. Before its pointed door a porch of the cloth rises to a
     * point on two poles, its ridge running back into the roof, lamps under it. Inside, on rugs,
     * twelve bunks along the side walls, the barracks block at the back between the racks of swords and spears under the
     * banner of the army, a low table round the centre pole with cushions and candles, the chests of the company and lamps
     * hanging from the roof; outside, standards on poles at the front corners.
     */
    static Structure barracks() {
        Structure s = new Structure(13, 14, 11);
        int x1 = 1;
        int z1 = 1;
        int x2 = 11;
        int z2 = 9;
        // The platform: a footing of red stone, a floor of timber.
        fill(s, x1, 0, z1, x2, 0, z2, BASE);
        fill(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, Desert.FLOOR);
        put(s, 4, 0, 10, BASE);
        put(s, 8, 0, 10, BASE);
        fill(s, 5, 0, 10, 7, 0, 10, Kit.stairs(Desert.BASE_STAIRS, "north"));
        // The pavilion: low walls with a skirt of the cloth and screened windows, a roof flaring out at the eaves and rising
        // steeply to the pole.
        tentWalls(s, x1, z1, x2, z2, 1, 3);
        fill(s, x1 + 1, 1, z1, x2 - 1, 1, z1, CLOTH);
        fill(s, x1 + 1, 1, z2, x2 - 1, 1, z2, CLOTH);
        fill(s, x1, 1, z1 + 1, x1, 1, z2 - 1, CLOTH);
        fill(s, x2, 1, z1 + 1, x2, 1, z2 - 1, CLOTH);
        for (int z : new int[]{3, 7}) {
            put(s, x1, 2, z, Kit.shutter(Desert.LATTICE, "east"));
            put(s, x2, 2, z, Kit.shutter(Desert.LATTICE, "west"));
        }
        for (int x : new int[]{3, 9}) {
            put(s, x, 2, z1, Kit.shutter(Desert.LATTICE, "south"));
        }
        tentRoof(s, x1, z1, x2, z2, new int[]{4, 5, 6, 8, 10}, true);
        valance(s, x1, z1, x2, z2, 4);
        // The pointed door, its flaps of the cloth tied back.
        fill(s, 5, 1, z2, 7, 3, z2, Structure.AIR);
        put(s, 6, 4, z2, Structure.AIR);
        fill(s, 4, 1, z2, 4, 3, z2, CLOTH);
        fill(s, 8, 1, z2, 8, 3, z2, CLOTH);
        // The porch before the door: a pointed gable of the cloth on two poles, its ridge running back into the roof.
        fill(s, 4, 1, 10, 4, 3, 10, Desert.FENCE);
        fill(s, 8, 1, 10, 8, 3, 10, Desert.FENCE);
        put(s, 6, 4, 10, Structure.AIR);
        for (int z = 8; z <= 10; z++) {
            for (int x = 4; x <= 8; x++) {
                int y = 6 - Math.abs(x - 6);
                if (z == 10 || z == 9 && y > 4 || z == 8 && x == 6) {
                    put(s, x, y, z, CLOTH);
                }
            }
        }
        put(s, 6, 5, z2, CLOTH);
        put(s, 5, 4, 10, Kit.hangingLantern());
        put(s, 7, 4, 10, Kit.hangingLantern());
        // Rugs over the floor, the centre pole with its gilt ball.
        Desert.rug(s, 4, 2, 8, 8, 1);
        fill(s, 6, 1, 5, 6, 11, 5, Kit.log(Desert.POST, "y"));
        Desert.finial(s, 6, 12, 5, 1);
        // Twelve bunks along the side walls, the chests of the company between them.
        for (int z : new int[]{2, 3, 4, 6, 7, 8}) {
            Furnish.bed(s, 3, 1, z, "west", "red");
            Furnish.bed(s, 9, 1, z, "east", "red");
        }
        Furnish.chest(s, 2, 1, 5, "east");
        Furnish.chest(s, 10, 1, 5, "west");
        put(s, 3, 1, 5, Desert.CLOTH_CARPET);
        put(s, 9, 1, 5, Desert.CLOTH_CARPET);
        // The barracks block at the back between the racks, the banner of the army over it.
        put(s, 6, 1, 2, BARRACKS);
        put(s, 5, 1, 2, RACK);
        put(s, 7, 1, 2, RACK);
        spear(s, 4, 1, 2);
        spear(s, 8, 1, 2);
        put(s, 6, 3, 2, Kit.wallBanner("red", "south"));
        // The low table round the centre pole, cushions round it.
        for (int z = 4; z <= 6; z++) {
            for (int x = 5; x <= 7; x++) {
                if (x != 6 || z != 5) {
                    put(s, x, 1, z, Kit.topSlab(Desert.FLOOR_SLAB));
                }
            }
        }
        put(s, 5, 2, 4, Furnish.candles(3));
        put(s, 7, 2, 6, Furnish.candles(2));
        Desert.trayTable(s, 7, 2, 4);
        Desert.jar(s, 5, 2, 6);
        for (int x = 5; x <= 7; x++) {
            put(s, x, 1, 3, Desert.CLOTH_CARPET);
            put(s, x, 1, 7, Desert.CLOTH_CARPET);
        }
        // Lamps hanging from the roof.
        Desert.lamp(s, 4, 3, 3);
        Desert.lamp(s, 8, 3, 3);
        Desert.lamp(s, 4, 3, 7);
        Desert.lamp(s, 8, 3, 7);
        Desert.lamp(s, 2, 3, 5);
        Desert.lamp(s, 10, 3, 5);
        // Standards on poles at the front corners.
        for (int x : new int[]{0, 12}) {
            fill(s, x, 0, 10, x, 4, 10, Desert.FENCE);
            put(s, x, 5, 10, Kit.banner("red", 0));
        }
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the drill yard

    /**
     * The drill yard of the camp, level with the ground: a floor of sand inside a low wall of mud brick under a coping of red
     * stone with piers, higher at the back where it is crowned with stepped merlons and three butts of straw with targets
     * stand against it; a sparring ring of beaten earth edged with paving with two dummies on it; a corner of weapons with
     * racks, spears and the barrels of arrows; along the east wall a bench in the shade of a striped awning with water;
     * entered through a slender gate of mud brick under a pointed arch with a lamp in it, a crest of merlons on top and
     * banners on its flanks, lanterns on the corners of the wall.
     */
    static Structure yard() {
        Structure s = new Structure(13, 10, 11);
        // The floor: sand, a footing of red stone under the walls, a sparring ring of beaten earth edged with paving and a
        // path from the gate.
        fill(s, 0, 0, 0, 12, 0, 10, BASE);
        fill(s, 1, 0, 1, 11, 0, 9, SAND);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (Desert.inPlan(dx, dz, 3, Desert.OCTAGON)) {
                    put(s, 6 + dx, 0, 5 + dz, Desert.inPlan(dx, dz, 2, Desert.OCTAGON) ? EARTH : Desert.PAVING);
                }
            }
        }
        fill(s, 5, 0, 9, 7, 0, 10, Desert.PAVING);
        fill(s, 6, 0, 8, 6, 0, 9, Desert.PAVING);
        // The back wall, high enough to stop the arrows, crowned with stepped merlons.
        fill(s, 0, 1, 0, 12, 1, 0, BASE);
        fill(s, 0, 2, 0, 12, 3, 0, WALL);
        fill(s, 0, 4, 0, 12, 4, 0, TRIM);
        Desert.crown(s, true, 0, 12, 0, 5, Desert.Merlons.STEPPED);
        // The low side and front walls under a coping of red stone, piers with pointed caps between them.
        for (int x : new int[]{0, 12}) {
            fill(s, x, 1, 1, x, 1, 9, BASE);
            fill(s, x, 2, 1, x, 2, 9, WALL);
            Desert.crown(s, false, 0, 10, x, 3, Desert.Merlons.NONE);
            for (int z : new int[]{4, 7}) {
                pier(s, x, 1, z);
            }
        }
        fill(s, 0, 1, 10, 3, 1, 10, BASE);
        fill(s, 9, 1, 10, 12, 1, 10, BASE);
        fill(s, 0, 2, 10, 3, 2, 10, WALL);
        fill(s, 9, 2, 10, 12, 2, 10, WALL);
        Desert.crown(s, true, 0, 4, 10, 3, Desert.Merlons.NONE);
        Desert.crown(s, true, 8, 12, 10, 3, Desert.Merlons.NONE);
        // Corner pillars with lanterns.
        for (int[] c : new int[][]{{0, 0}, {12, 0}}) {
            fill(s, c[0], 1, c[1], c[0], 5, c[1], WALL);
            put(s, c[0], 6, c[1], Kit.lantern());
        }
        for (int[] c : new int[][]{{0, 10}, {12, 10}}) {
            fill(s, c[0], 1, c[1], c[0], 3, c[1], WALL);
            put(s, c[0], 4, c[1], Kit.lantern());
        }
        // The gate: a pointed arch in a slender block of wall, a cornice and merlons over it, a lamp in its head.
        fill(s, 4, 1, 10, 8, 1, 10, BASE);
        fill(s, 4, 2, 10, 8, 6, 10, WALL);
        fill(s, 4, 7, 10, 8, 7, 10, TRIM);
        Desert.arch(s, 6, 1, 10, "south", 3, 3, 1, Desert.Arch.POINTED, null, false);
        Desert.crown(s, true, 3, 9, 10, 8, Desert.Merlons.POINTED);
        put(s, 6, 5, 10, Kit.hangingLantern());
        put(s, 3, 5, 10, Kit.wallBanner("red", "west"));
        put(s, 9, 5, 10, Kit.wallBanner("red", "east"));
        // Three butts of straw with targets against the back wall.
        for (int x : new int[]{3, 6, 9}) {
            put(s, x, 1, 1, HAY);
            put(s, x, 2, 1, TARGET);
        }
        // Two dummies on the sparring ring.
        dummy(s, 4, 1, 5);
        dummy(s, 8, 1, 5);
        // The corner of weapons at the front west.
        put(s, 1, 1, 8, RACK);
        put(s, 1, 1, 6, RACK);
        put(s, 1, 1, 7, Kit.barrel());
        put(s, 2, 1, 8, Kit.barrel());
        spear(s, 1, 1, 9);
        spear(s, 2, 1, 9);
        Desert.jar(s, 2, 1, 6);
        // The shaded bench along the east wall, water beside it.
        Desert.awning(s, 10, 4, 11, 8, 3, 1, "west");
        Desert.divan(s, 11, 5, 11, 6, 1, "west", false);
        put(s, 11, 1, 8, "minecraft:water_cauldron[level=3]");
        Desert.jar(s, 11, 1, 3);
        Desert.lamp(s, 11, 2, 6);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the store

    /**
     * The store of the camp: a block of mud brick on a plinth of red stone under a flat roof with stepped merlons, the ends of
     * its beams standing out of the back and the sides in a row, a wind tower over the back to keep the grain dry and cool,
     * its gate under a pointed arch in a frame of red stone that rises over the parapet with merlons of its own. Before it a
     * paved apron, an awning over hay and sacks waiting to be stored, a trough with fodder and a jar, a hitching post and a
     * lamp. Inside, under the beams: the two storehouse blocks at the back among chests and barrels, more chests and barrels
     * along the walls, sacks, crates and hay, lamps hanging from the beams.
     */
    static Structure store() {
        Structure s = new Structure(13, 15, 11);
        int x1 = 1;
        int z1 = 1;
        int x2 = 11;
        int z2 = 8;
        // Footing, floor and apron.
        fill(s, x1, 0, z1, x2, 0, z2, BASE);
        fill(s, x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, EARTH);
        fill(s, 5, 0, z1 + 1, 7, 0, z2, Desert.PAVING);
        fill(s, x1, 0, 9, x2, 0, 10, Desert.PAVING);
        // The walls and the roof.
        walls(s, x1, z1, x2, z2, 1, 1, BASE);
        walls(s, x1, z1, x2, z2, 2, 5, WALL);
        Desert.flatRoof(s, x1, z1, x2, z2, 6, Desert.Merlons.STEPPED, false);
        // The gate in its frame, the frame rising over the parapet with merlons of its own.
        int top = Desert.arch(s, 6, 1, z2, "south", 3, 3, 1, Desert.Arch.POINTED, null, true);
        Desert.crown(s, true, 2, 10, z2, top, Desert.Merlons.STEPPED);
        put(s, 2, top, z2, BASE);
        put(s, 10, top, z2, BASE);
        put(s, 2, top + 1, z2, Kit.slab(Desert.BASE_SLAB));
        put(s, 10, top + 1, z2, Kit.slab(Desert.BASE_SLAB));
        put(s, 6, 5, z2, Kit.hangingLantern());
        // Beams under the roof, the ends of the beams of the walls standing out in a row round the back and the sides.
        for (int x : new int[]{3, 6, 9}) {
            fill(s, x, 5, z1 + 1, x, 5, z2 - 1, Kit.log(Desert.BEAM, "z"));
        }
        for (int z : new int[]{2, 4, 6}) {
            put(s, x1 - 1, 4, z, Kit.log(Desert.BEAM, "x"));
            put(s, x2 + 1, 4, z, Kit.log(Desert.BEAM, "x"));
        }
        for (int x = 2; x <= 10; x += 2) {
            put(s, x, 4, z1 - 1, Kit.log(Desert.BEAM, "z"));
        }
        // A wind tower over the back.
        Desert.windTower(s, 9, 6, 3, 3, 5);
        // The back wall: storehouse blocks among chests, barrels and sacks.
        put(s, 2, 1, 2, Kit.barrel());
        put(s, 2, 2, 2, Kit.barrel());
        put(s, 3, 1, 2, Kit.chest("south"));
        put(s, 4, 1, 2, STOREHOUSE);
        put(s, 5, 1, 2, Kit.barrel());
        put(s, 5, 2, 2, Kit.barrel());
        Furnish.sack(s, 6, 1, 2, false);
        Furnish.sack(s, 6, 2, 2, true);
        put(s, 7, 1, 2, Kit.barrel());
        put(s, 7, 2, 2, Kit.barrel());
        put(s, 8, 1, 2, STOREHOUSE);
        put(s, 9, 1, 2, Kit.chest("south"));
        put(s, 10, 1, 2, Kit.barrel());
        put(s, 10, 2, 2, Kit.barrel());
        // The side walls: chests and barrels, hay and sacks to the front.
        put(s, 2, 1, 3, Kit.chest("east"));
        put(s, 2, 1, 4, Kit.barrel());
        put(s, 10, 1, 3, Kit.chest("west"));
        put(s, 10, 1, 4, Kit.barrel());
        put(s, 2, 1, 6, HAY);
        put(s, 2, 1, 7, HAY);
        put(s, 2, 2, 7, HAY);
        put(s, 3, 1, 7, HAY);
        Furnish.sack(s, 10, 1, 7, false);
        Furnish.sack(s, 10, 2, 7, false);
        Furnish.sack(s, 10, 1, 6, true);
        Furnish.sack(s, 9, 1, 7, false);
        // A pile of crates and sacks in the middle.
        Furnish.crate(s, 6, 1, 4, "spruce");
        Furnish.crate(s, 7, 1, 4, "spruce");
        Furnish.crate(s, 6, 2, 4, "spruce");
        Furnish.sack(s, 5, 1, 4, true);
        // Lamps hanging from the beams.
        put(s, 3, 4, 4, Kit.hangingLantern());
        put(s, 9, 4, 4, Kit.hangingLantern());
        put(s, 3, 4, 6, Kit.hangingLantern());
        put(s, 9, 4, 6, Kit.hangingLantern());
        put(s, 6, 4, 6, Kit.hangingLantern());
        // The apron: an awning over hay and sacks, a trough and a hitching post with a lamp.
        Desert.awning(s, x1, 9, 4, 10, 3, 1, "south");
        put(s, 2, 1, 9, HAY);
        put(s, 2, 2, 9, HAY);
        put(s, 3, 1, 9, HAY);
        Furnish.sack(s, 1, 1, 9, false);
        Furnish.sack(s, 3, 1, 10, true);
        put(s, 9, 1, 9, "minecraft:water_cauldron[level=3]");
        put(s, 10, 1, 9, HAY);
        Desert.jar(s, 10, 1, 10);
        lampPost(s, 11, 1, 10);
        put(s, 8, 1, 10, Desert.FENCE);
        keepGround(s);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the watch tower

    /**
     * The watch tower of the camp: a slender tower of mud brick, five wide, on a battered foot of seven, the ends of its
     * beams standing out of its faces in two rows, slits on three sides; at its top a platform jutting out on a ring of
     * corbels behind a parapet with pointed merlons, shaded by a little tent of white canvas, its eaves and top of the cloth,
     * on four poles with a valance of banners, a lamp under it and a gilt ball on it. One enters through a narrow door in a
     * frame of red stone and climbs a ladder up the inside, lit by lamps, through a hatch onto the platform.
     */
    static Structure tower() {
        Structure s = new Structure(7, 22, 7);
        int deck = 13;
        // The battered foot: solid round a chamber and the passage of the door.
        fill(s, 0, 0, 0, 6, 0, 6, BASE);
        fill(s, 0, 1, 0, 6, 2, 6, WALL);
        fill(s, 2, 1, 2, 4, 2, 4, Structure.AIR);
        fill(s, 2, 0, 2, 4, 0, 4, Desert.PAVING);
        for (int i = 0; i <= 6; i++) {
            put(s, i, 3, 0, Kit.stairs(Desert.BASE_STAIRS, "south"));
            put(s, i, 3, 6, Kit.stairs(Desert.BASE_STAIRS, "north"));
            put(s, 0, 3, i, Kit.stairs(Desert.BASE_STAIRS, "east"));
            put(s, 6, 3, i, Kit.stairs(Desert.BASE_STAIRS, "west"));
        }
        put(s, 0, 3, 0, Kit.stairs(Desert.BASE_STAIRS, "south", "outer_right"));
        put(s, 6, 3, 0, Kit.stairs(Desert.BASE_STAIRS, "south", "outer_left"));
        put(s, 0, 3, 6, Kit.stairs(Desert.BASE_STAIRS, "north", "outer_left"));
        put(s, 6, 3, 6, Kit.stairs(Desert.BASE_STAIRS, "north", "outer_right"));
        // The door: a passage through the front of the foot, a frame of red stone and a keystone.
        fill(s, 3, 1, 5, 3, 2, 6, Structure.AIR);
        s.door(3, 1, 5, Desert.DOOR, "north", "left");
        fill(s, 2, 1, 6, 2, 2, 6, BASE);
        fill(s, 4, 1, 6, 4, 2, 6, BASE);
        put(s, 3, 3, 6, CARVED);
        put(s, 3, 0, 6, Kit.stairs(Desert.BASE_STAIRS, "north"));
        // The shaft: bands of trim at its foot and its top, slits on three sides, the ends of beams.
        for (int y = 3; y < deck - 1; y++) {
            Desert.shell(s, 3, y, y, 3, 2, Desert.SQUARE, y == 3 || y == deck - 2 ? TRIM : WALL);
        }
        for (int y : new int[]{8, 9}) {
            put(s, 1, y, 3, Structure.AIR);
            put(s, 5, y, 3, Structure.AIR);
            put(s, 3, y, 5, Structure.AIR);
        }
        for (int y : new int[]{6, 10}) {
            for (int a : new int[]{2, 4}) {
                put(s, 0, y, a, Kit.log(Desert.BEAM, "x"));
                put(s, 6, y, a, Kit.log(Desert.BEAM, "x"));
                put(s, a, y, 0, Kit.log(Desert.BEAM, "z"));
                put(s, a, y, 6, Kit.log(Desert.BEAM, "z"));
            }
        }
        // The corbels, the platform and the parapet with pointed merlons.
        Desert.shell(s, 3, deck - 1, deck - 1, 3, 2, Desert.SQUARE, WALL);
        Desert.corbels(s, 3, deck - 1, 3, 3, Desert.SQUARE);
        Desert.plate(s, 3, deck, 3, 3, Desert.SQUARE, WALL);
        Desert.plate(s, 3, deck, 3, 2, Desert.SQUARE, Desert.ROOF.block());
        Desert.shell(s, 3, deck + 1, deck + 1, 3, 3, Desert.SQUARE, WALL);
        Desert.crown(s, true, 0, 6, 0, deck + 2, Desert.Merlons.POINTED);
        Desert.crown(s, true, 0, 6, 6, deck + 2, Desert.Merlons.POINTED);
        Desert.crown(s, false, 0, 6, 0, deck + 2, Desert.Merlons.POINTED);
        Desert.crown(s, false, 0, 6, 6, deck + 2, Desert.Merlons.POINTED);
        for (int[] c : new int[][]{{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
            put(s, c[0], deck + 2, c[1], WALL);
            put(s, c[0], deck + 3, c[1], Kit.slab(Desert.BASE_SLAB));
        }
        // The ladder up the back of the inside through a hatch in the platform, lamps on corbels.
        for (int y = 1; y <= deck; y++) {
            put(s, 3, y, 2, Kit.ladder("south"));
        }
        put(s, 4, 1, 4, Kit.lantern());
        put(s, 4, 6, 4, TRIM);
        put(s, 4, 5, 4, Kit.hangingLantern());
        put(s, 2, 11, 4, TRIM);
        put(s, 2, 10, 4, Kit.hangingLantern());
        // The little tent over the platform on four poles, with its valance and gilt ball.
        int roof = deck + 4;
        for (int[] c : new int[][]{{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
            fill(s, c[0], deck + 1, c[1], c[0], roof - 1, c[1], Desert.FENCE);
        }
        tentRoof(s, 1, 1, 5, 5, new int[]{roof, roof + 1, roof + 2}, false);
        valance(s, 1, 1, 5, 5, roof);
        Desert.finial(s, 3, roof + 3, 3, 1);
        Desert.lamp(s, 3, roof - 1, 3);
        s.connect();
        return s;
    }
}
