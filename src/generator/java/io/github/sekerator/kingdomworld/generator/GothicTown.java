package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The town of the style "gothic" (see {@link Gothic}): its houses, from the hovel of rubble and dark timber under a
 * roof as steep as a tent to the manor of a noble with its buttressed hall and walled court, its smithy, its tavern and
 * a piece of its wall with the gate. They stand tall and narrow on plinths of rough stone, dark walls pierced by
 * pointed windows in pale tracery, under very steep roofs whose stone gables end in pinnacles; turrets carry needle
 * spires, iron railings close the yards and soul lanterns light the doors. Inside every room has its floor, its
 * ceiling, its light and the furniture of those who live or work there.
 */
final class GothicTown {
    private static final String WALL = Gothic.WALL;
    private static final String WALL_STAIRS = Gothic.WALL_STAIRS;
    private static final String WALL_SLAB = Gothic.WALL_SLAB;
    private static final String ROUGH = Gothic.ROUGH;
    private static final String ROUGH_STAIRS = Gothic.ROUGH_STAIRS;
    private static final String ROUGH_SLAB = Gothic.ROUGH_SLAB;
    private static final String TRIM_STAIRS = Gothic.TRIM_STAIRS;
    private static final String TRIM_SLAB = Gothic.TRIM_SLAB;
    private static final String CARVED = Gothic.CARVED;
    private static final String PLASTER = Gothic.PLASTER;
    private static final String WOOD = Gothic.WOOD;
    private static final String PLANKS = Gothic.PLANKS;
    private static final String WOOD_STAIRS = Gothic.WOOD_STAIRS;
    private static final String WOOD_SLAB = Gothic.WOOD_SLAB;
    private static final String BEAM = Gothic.BEAM;
    private static final String DOOR = Gothic.DOOR;
    private static final String TRACERY = Gothic.TRACERY;
    private static final String TRACERY_STAIRS = Gothic.TRACERY_STAIRS;
    private static final String TRACERY_SLAB = Gothic.TRACERY_SLAB;
    private static final String GLASS = Gothic.GLASS;
    private static final String VIOLET = Gothic.VIOLET;
    private static final String CRIMSON = Gothic.CRIMSON;
    private static final String AZURE = Gothic.AZURE;
    private static final String IRON = Gothic.IRON;
    private static final String AIR = Structure.AIR;

    private GothicTown() {
    }

    // ---------------------------------------------------------------- the plans of the houses

    /**
     * The houses the house program draws: their names, their ground, their beds and their icons.
     */
    enum Plan {
        HOVEL("hovel", 5, 7, 1, "minecraft:cobbled_deepslate"),
        NARROW("narrow", 9, 13, 3, "minecraft:spruce_door"),
        ORIEL("oriel", 11, 11, 3, "minecraft:glass_pane"),
        MERCHANT("merchant", 11, 13, 3, "minecraft:purple_banner"),
        TURRET("turret", 13, 13, 3, "minecraft:deepslate_tiles"),
        MANOR("manor", 13, 15, 4, "minecraft:iron_bars");

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
     * The town buildings of the style: the six houses of the house program, the smithy, the tavern and the piece of the
     * town wall with its gate.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Plan plan : Plan.values()) {
            Structure s = house(plan);
            list.add(new BuildingGenerator.Building("gothic_house_" + plan.key, "housing", "house", plan.icon, (s.sizeX - 1) / 2,
                    0, s.sizeZ - 1, s, "house", 1, ""));
        }
        Structure smithy = smithy();
        list.add(new BuildingGenerator.Building("gothic_smithy", "production", "workshop", "minecraft:anvil",
                (smithy.sizeX - 1) / 2, 0, smithy.sizeZ - 1, smithy, "workshop", 1, "").at("door", 3, 1, 8));
        Structure tavern = tavern();
        list.add(new BuildingGenerator.Building("gothic_tavern", "trade", "tavern", "minecraft:barrel",
                (tavern.sizeX - 1) / 2, 0, tavern.sizeZ - 1, tavern, "tavern", 1, "").at("door", 3, 0, 14));
        Structure gate = wallGate();
        list.add(new BuildingGenerator.Building("gothic_wall_gate", "defense", "gatehouse", "minecraft:deepslate_bricks",
                (gate.sizeX - 1) / 2, 0, gate.sizeZ - 1, gate, "tower", 1, "")
                .at("door", 11, 0, 10)
                .at("castle_gate", 10, 1, 5).at("castle_gate", 12, 1, 5)
                .at("castle_wall", 3, 8, 5).at("castle_wall", 19, 8, 5));
        return list;
    }

    /**
     * The house program: draws the house of a plan.
     */
    static Structure house(Plan plan) {
        return switch (plan) {
            case HOVEL -> hovel();
            case NARROW -> narrow();
            case ORIEL -> oriel();
            case MERCHANT -> merchant();
            case TURRET -> turret();
            case MANOR -> manor();
        };
    }

    // ---------------------------------------------------------------- shared shapes

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    /**
     * The walls of a block from (x1, z1) to (x2, z2), standing on a course of rough stone at layer {@code y1} up to
     * {@code y2}, of the given stone.
     */
    private static void shell(Structure s, int x1, int z1, int x2, int z2, int y1, int y2, String stone) {
        s.walls(x1, y1, z1, x2, y1, z2, ROUGH);
        if (y2 > y1) {
            s.walls(x1, y1 + 1, z1, x2, y2, z2, stone);
        }
    }

    /**
     * A floor of boards inside the walls of a block at layer {@code y}.
     */
    private static void boards(Structure s, int x1, int z1, int x2, int z2, int y) {
        s.fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, PLANKS);
    }

    /**
     * A ladder against the block on the side opposite to {@code facing}, from layer {@code y1} to {@code y2}.
     */
    private static void ladder(Structure s, int x, int z, int y1, int y2, String facing) {
        for (int y = y1; y <= y2; y++) {
            s.set(x, y, z, Kit.ladder(facing));
        }
    }

    /**
     * A soul lantern on a bracket of iron before a wall looking the way of {@code facing}: the lantern hangs under a
     * bar one cell out of the wall.
     */
    private static void bracketLamp(Structure s, int x, int y, int z, String facing) {
        int[] d = Furnish.step(facing);
        put(s, x + d[0], y, z + d[1], Kit.upside(Gothic.GARGOYLE_STAIRS, Furnish.opposite(facing)));
        put(s, x + d[0], y - 1, z + d[1], Gothic.SOUL_LANTERN_HANGING);
    }

    // ---------------------------------------------------------------- slim spires

    /**
     * A slim needle spire for a small octagonal turret of half-size 2, standing on layer {@code y} around (cx, cz) and
     * reaching {@code height} layers in all: a foot as wide as the turret whose rim slopes in as a row of spire stairs, a
     * shaft three wide in a cross whose arms end in sloping stairs, with a pane of purple glass in each arm low down, then
     * a needle of single spire blocks and an iron cross on its point.
     *
     * @return the layer above the cross
     */
    private static int slimSpire(Structure s, int cx, int y, int cz, int height) {
        Kit.Roof spire = Gothic.SPIRE;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (!Gothic.inOctagon(dx, dz, 2)) {
                    continue;
                }
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                put(s, cx + dx, y, cz + dz, rim ? Kit.stairs(spire.stairs(), inward(dx, dz)) : spire.block());
            }
        }
        int shaft = Math.max(3, (height - 4) / 2);
        int layer = y + 1;
        for (int i = 0; i < shaft; i++) {
            boolean last = i == shaft - 1;
            put(s, cx, layer, cz, spire.block());
            for (String facing : new String[]{"north", "south", "west", "east"}) {
                int[] d = Furnish.step(facing);
                String arm = last ? Kit.stairs(spire.stairs(), Furnish.opposite(facing)) : i == 1 ? Gothic.VIOLET : spire.block();
                put(s, cx + d[0], layer, cz + d[1], arm);
            }
            layer++;
        }
        for (int i = 0; i < height - 4 - shaft; i++) {
            put(s, cx, layer++, cz, spire.block());
        }
        put(s, cx, layer, cz, IRON);
        put(s, cx, layer + 1, cz, IRON);
        put(s, cx - 1, layer + 1, cz, IRON);
        put(s, cx + 1, layer + 1, cz, IRON);
        put(s, cx, layer + 2, cz, IRON);
        return layer + 3;
    }

    /**
     * The way from a cell of a plan toward its middle, along the longer of its two offsets.
     */
    private static String inward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? "west" : "east";
        }
        return dz > 0 ? "north" : "south";
    }

    /**
     * The top of a small octagonal turret of half-size 2 whose walls stand up to layer {@code y}: a cornice of hanging
     * stairs one block out all round at {@code y}, a ledge of pale slabs over it, and a slim needle spire of
     * {@code height} layers from {@code y + 1} (see {@link #slimSpire}).
     *
     * @return the layer above the cross of the spire
     */
    private static int turretTop(Structure s, int cx, int y, int cz, int height) {
        ring(s, cx, y, cz, TRIM_STAIRS);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (Gothic.inOctagon(dx, dz, 3) && !Gothic.inOctagon(dx, dz, 2)) {
                    put(s, cx + dx, y + 1, cz + dz, Kit.slab(TRACERY_SLAB));
                }
            }
        }
        return slimSpire(s, cx, y + 1, cz, height);
    }

    /**
     * A band of hanging stairs of the given family one block out all round a small octagonal turret of half-size 2, at
     * layer {@code y}, wherever nothing stands.
     */
    private static void ring(Structure s, int cx, int y, int cz, String stairs) {
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (Gothic.inOctagon(dx, dz, 3) && !Gothic.inOctagon(dx, dz, 2) && s.inside(cx + dx, y, cz + dz)
                        && !s.solid(cx + dx, y, cz + dz)) {
                    s.set(cx + dx, y, cz + dz, Kit.upside(stairs, inward(dx, dz)));
                }
            }
        }
    }

    // ---------------------------------------------------------------- the hovel

    /**
     * A hovel of the poor: one room of rubble with a band of dark timber and plaster under the eaves, between corner
     * posts, under a roof of shingles so steep that it reads as a dark tent, overhanging the door in the gable to the
     * street. A little window with a sill and a shutter, a timber gable with a slit of glass, a stack of rubble through
     * the roof, logs under the overhang and a cold lantern by the door. Inside, open to the rafters: the bed, the
     * hearth with its fire behind a grate, the household chest, a table with a candle and a stool, a lantern on a
     * chain.
     */
    static Structure hovel() {
        Structure s = new Structure(5, 13, 7);
        s.fill(0, 0, 0, 4, 0, 5, ROUGH);
        s.fill(1, 0, 1, 3, 0, 4, PLANKS);
        s.walls(0, 1, 0, 4, 2, 5, ROUGH);
        s.walls(0, 3, 0, 4, 4, 5, PLASTER);
        s.corners(0, 1, 0, 4, 4, 5, BEAM);
        s.set(2, 3, 5, Kit.log(BEAM, "x")).set(2, 4, 5, Kit.log(BEAM, "y"));
        s.fill(2, 3, 0, 2, 4, 0, Kit.log(BEAM, "y"));
        for (int x : new int[]{0, 4}) {
            s.fill(x, 3, 3, x, 4, 3, Kit.log(BEAM, "y"));
        }
        // The roof: as steep as a tent, its eaves on the walls, overhanging both gables.
        Gothic.steepRoof(s, 1, 0, 3, 5, 5, false, 2, Gothic.PLAIN, Gothic.PLAIN);
        // The timber gables: plaster and a slit of glass under the ridge.
        for (int z : new int[]{0, 5}) {
            s.set(2, 5, z, PLASTER).set(2, 6, z, z == 5 ? GLASS : PLASTER);
        }
        // The front: the door, the little window with its sill and shutter, the step, the logs, the lantern.
        s.door(2, 1, 5, DOOR, "north", "left");
        s.set(1, 2, 5, GLASS).set(1, 1, 6, Kit.upside(TRIM_STAIRS, "north"));
        s.set(0, 2, 6, Kit.shutter(Kit.SHUTTER, "south"));
        s.set(2, 0, 6, Kit.slab(ROUGH_SLAB));
        s.set(3, 0, 6, Kit.log("minecraft:oak_log", "x")).set(4, 0, 6, Kit.log("minecraft:oak_log", "x"));
        s.set(4, 1, 6, Kit.log("minecraft:oak_log", "x"));
        s.set(3, 4, 6, Gothic.SOUL_LANTERN_HANGING);
        // Windows in the timber band of the sides and the back.
        s.set(0, 4, 2, GLASS).set(4, 4, 2, GLASS).set(1, 4, 0, GLASS);
        // The hearth in the back corner and its stack of rubble through the roof.
        Furnish.hearth(s, 3, 1, 1, "south");
        s.fill(3, 3, 1, 3, 10, 1, ROUGH);
        s.set(3, 11, 1, Kit.ROUGH_FENCE);
        // Inside: the bed, the chest, the table with a candle and a stool, the lantern.
        Furnish.bed(s, 1, 1, 3, "north", "brown");
        Furnish.chest(s, 1, 1, 1, "east");
        s.set(3, 1, 4, Kit.topSlab(WOOD_SLAB)).set(3, 2, 4, Furnish.candles(1));
        Furnish.chair(s, 3, 1, 3, "south", WOOD);
        Furnish.chandelier(s, 2, 3, 6, 2);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the narrow house

    /**
     * A narrow town house of three storeys on a deep plot, its gable to the street: a front of the style (a pointed
     * doorway between two lancets on a rough plinth, arcades of narrow lancets in plaster between piers, string
     * courses, a stone gable with its coping, kneelers and a pinnacle on the apex round a rose window), slim buttresses
     * with pinnacles on its front corners, a very steep roof with an iron cresting and dormers, a stack of rough stone
     * on the west wall. Inside a ladder climbs the back wall through every floor: the hall with a chimneypiece, a
     * kitchen corner, a table, a bench and the household chest, a chamber with two beds, a chamber with a bed, a desk
     * and a bookcase, and a loft of stores.
     */
    static Structure narrow() {
        Structure s = new Structure(9, 31, 13);
        int x1 = 1;
        int x2 = 7;
        int z1 = 1;
        int z2 = 11;
        int storey = 5;
        int eaves = 3 * storey;
        shell(s, x1, z1, x2, z2, 0, eaves - 1, WALL);
        for (int i = 0; i <= 3; i++) {
            boards(s, x1, z1, x2, z2, i * storey);
        }
        int ridge = Gothic.steepRoof(s, x1, z1, x2, z2, eaves, false, 3, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.cresting(s, 4, z1 + 1, 4, z2 - 1, ridge);
        Gothic.dormer(s, x1, z1, x2, z2, eaves, false, 3, 6, true, 2);
        Gothic.dormer(s, x1, z1, x2, z2, eaves, false, 3, 6, false, 2);
        Gothic.facade(s, 4, 0, z2, "south", 7, 3, storey, 3);
        s.set(4, 0, 12, Kit.stairs(TRIM_STAIRS, "north"));
        Gothic.buttress(s, x1, 0, z2, "south", eaves, 1, true);
        Gothic.buttress(s, x2, 0, z2, "south", eaves, 1, true);
        // Windows of the sides and the back.
        for (int i = 0; i < 3; i++) {
            int fl = i * storey;
            Gothic.lancet(s, x1, fl + 2, 8, "west", 1, 2, GLASS, VIOLET);
            Gothic.lancet(s, x2, fl + 2, 8, "east", 1, 2, GLASS, VIOLET);
            Gothic.lancet(s, 4, fl + 2, z1, "north", 1, 2, GLASS, VIOLET);
        }
        Gothic.lancet(s, 4, eaves + 2, z1, "north", 1, 3, GLASS, VIOLET);
        // The stack of rough stone against the west wall.
        s.fill(0, 1, 4, 0, ridge - 5, 4, ROUGH);
        s.set(0, ridge - 4, 4, Kit.ROUGH_FENCE);
        // The ladder through every floor.
        ladder(s, 2, 2, 1, eaves, "south");
        // The hall: a chimneypiece, a table with chairs, the chest, a cauldron, light.
        Gothic.fireplace(s, x2, 1, 5, "west", eaves - 1);
        Furnish.table(s, 3, 1, 8, WOOD);
        Furnish.chair(s, 3, 1, 7, "south", WOOD);
        Furnish.chair(s, 3, 1, 9, "north", WOOD);
        Furnish.chest(s, 6, 1, 2, "west");
        Furnish.kitchen(s, 3, 1, 2, "south", WOOD);
        Furnish.sack(s, 6, 1, 3, true);
        Furnish.crate(s, 6, 1, 10, WOOD);
        Furnish.bench(s, 2, 5, 2, 7, 1, "east", WOOD);
        Furnish.rug(s, 4, 7, 5, 9, 1, Gothic.CLOTH_CARPET);
        Furnish.chandelier(s, 4, 7, storey - 1, 1);
        Furnish.chandelier(s, 4, 3, storey - 1, 1);
        // The first chamber: two beds, a rug, a candelabrum.
        int fl = storey;
        Gothic.bed(s, 6, fl + 1, 9, "north", -1);
        Gothic.bed(s, 2, fl + 1, 9, "north", 1);
        Furnish.rug(s, 3, 6, 5, 9, fl + 1, Gothic.CLOTH_CARPET);
        Gothic.candelabrum(s, 6, fl + 1, 2);
        Furnish.crate(s, 2, fl + 1, 10, WOOD);
        Furnish.chandelier(s, 4, 7, fl + storey - 1, 1);
        Furnish.chandelier(s, 4, 3, fl + storey - 1, 1);
        // The second chamber: a bed, a desk, a bookcase.
        fl = 2 * storey;
        Gothic.bed(s, 6, fl + 1, 9, "north", -1);
        Furnish.desk(s, 6, fl + 1, 2, "west", WOOD);
        Gothic.bookcase(s, 3, fl + 1, 10, "north", 2);
        Furnish.rug(s, 3, 6, 4, 8, fl + 1, Gothic.CLOTH_CARPET);
        Furnish.chandelier(s, 4, 7, fl + storey - 1, 1);
        Furnish.chandelier(s, 4, 3, fl + storey - 1, 1);
        // The loft under the roof.
        fl = eaves;
        Furnish.crate(s, 5, fl + 1, 9, WOOD);
        Furnish.sack(s, 5, fl + 1, 10, true);
        Furnish.sack(s, 3, fl + 1, 10, false);
        Furnish.crate(s, 3, fl + 1, 9, WOOD);
        Furnish.chandelier(s, 4, 7, fl + 5, 2);
        Furnish.chandelier(s, 4, 3, fl + 5, 2);
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- the house with the oriel

    /**
     * A house with an oriel, its eaves to the street: two storeys under a steep roof with stone gables at both ends, a
     * dormer at the back and a stack of rough stone over the ridge. In the middle of the front a gable of stone rises
     * through the eaves with a two-light window and a pinnacle; under it the oriel juts on its corbel over the pointed
     * doorway, glazed on three sides between pale posts under a little spire cap, between lancets in plaster; below, a
     * lancet on each side of the door. An iron railing with lamps closes the strip before the house. Inside, the hall
     * with a chimneypiece, a table and the chest; a ladder up to the chamber with two beds, a bookcase and a seat in
     * the oriel; and up to the attic with the third bed.
     */
    static Structure oriel() {
        Structure s = new Structure(11, 24, 11);
        int x1 = 1;
        int x2 = 9;
        int z1 = 1;
        int z2 = 7;
        int storey = 5;
        int eaves = 2 * storey;
        shell(s, x1, z1, x2, z2, 0, eaves - 1, WALL);
        for (int i = 0; i <= 2; i++) {
            boards(s, x1, z1, x2, z2, i * storey);
        }
        // The front: a plinth, the ground floor of stone, the upper floor of plaster between piers, a string course.
        for (int y = storey + 1; y < eaves; y++) {
            s.fill(x1 + 1, y, z2, x2 - 1, y, z2, PLASTER);
        }
        Gothic.cornice(s, x1, z2 + 1, x2, z2 + 1, storey, "south");
        // The roof, dormers at the back, the gable over the oriel rising through the eaves.
        int ridge = Gothic.steepRoof(s, x1, z1, x2, z2, eaves, true, 2, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.dormer(s, x1, z1, x2, z2, eaves, true, 2, 3, false, 2);
        Gothic.dormer(s, x1, z1, x2, z2, eaves, true, 2, 7, false, 2);
        Gothic.steepRoof(s, 3, z2, 7, z2, eaves, false, 2, Gothic.PARAPET, Gothic.OPEN);
        s.set(5, eaves + 4, z2 - 1, Gothic.ROOF.block()).set(5, eaves + 5, z2 - 1, Gothic.ROOF.block());
        s.set(5, eaves + 6, z2 - 1, Kit.slab(Gothic.ROOF.slab()));
        s.set(4, eaves + 5, z2 - 1, Kit.stairs(Gothic.ROOF.stairs(), "east"));
        s.set(6, eaves + 5, z2 - 1, Kit.stairs(Gothic.ROOF.stairs(), "west"));
        Gothic.cornice(s, 3, z2 + 1, 7, z2 + 1, eaves, "south");
        Gothic.lancet(s, 5, eaves + 1, z2, "south", 3, 2, GLASS, VIOLET);
        // The ground floor: the doorway between two lancets sharing their colonnettes, the step.
        Gothic.portal(s, 5, 1, z2, "south", 1, false);
        Gothic.lancet(s, 3, 2, z2, "south", 1, 1, GLASS, CRIMSON);
        Gothic.lancet(s, 7, 2, z2, "south", 1, 1, GLASS, CRIMSON);
        s.set(4, 3, z2 + 1, TRACERY).set(6, 3, z2 + 1, TRACERY);
        s.set(5, 0, z2 + 1, Kit.stairs(TRIM_STAIRS, "north"));
        // The upper floor: two lancets and the oriel between them.
        Gothic.lancet(s, 2, storey + 1, z2, "south", 1, 2, GLASS, VIOLET);
        Gothic.lancet(s, 8, storey + 1, z2, "south", 1, 2, GLASS, VIOLET);
        Gothic.oriel(s, 5, storey, z2, "south", 3);
        // The railing before the house, open at the door.
        Gothic.ironFence(s, 0, 10, 4, 10, 0, 2, true);
        Gothic.ironFence(s, 6, 10, 10, 10, 0, 2, true);
        s.set(5, 0, 9, Kit.slab(Gothic.TRACERY_SLAB)).set(5, 0, 10, Kit.slab(Gothic.TRACERY_SLAB));
        // Windows of the sides and the back.
        for (int i = 0; i < 2; i++) {
            int fl = i * storey;
            Gothic.lancet(s, x1, fl + 2, 4, "west", 1, 2, GLASS, VIOLET);
            if (i == 0) {
                Gothic.lancet(s, x2, fl + 2, 3, "east", 1, 2, GLASS, VIOLET);
            }
            Gothic.lancet(s, 3, fl + 2, z1, "north", 1, 2, GLASS, VIOLET);
            Gothic.lancet(s, 7, fl + 2, z1, "north", 1, 2, GLASS, VIOLET);
        }
        Gothic.lancet(s, x1, eaves + 2, 4, "west", 1, 2, GLASS, VIOLET);
        Gothic.lancet(s, x2, eaves + 2, 4, "east", 1, 2, GLASS, VIOLET);
        // The ladder up the east wall through both floors.
        ladder(s, 8, 5, 1, eaves + 1, "west");
        // The hall: a chimneypiece on the back wall, its stack over the ridge, a table with chairs, the chest, a cauldron.
        Gothic.fireplace(s, 5, 1, z1, "south", eaves - 1);
        for (int y = eaves; y <= ridge + 1; y++) {
            s.set(5, y, 2, ROUGH);
        }
        s.set(5, ridge + 2, 2, Kit.ROUGH_FENCE);
        Furnish.longTable(s, 4, 5, 6, 5, 1, WOOD);
        Furnish.bench(s, 4, 4, 6, 4, 1, "south", WOOD);
        Furnish.chair(s, 3, 1, 5, "east", WOOD);
        s.set(5, 2, 5, Furnish.candles(2));
        Furnish.chest(s, 8, 1, 2, "west");
        s.set(2, 1, 2, "minecraft:water_cauldron[level=3]");
        Furnish.crate(s, 2, 1, 3, WOOD);
        Furnish.sack(s, 2, 1, 6, true);
        Furnish.chandelier(s, 5, 5, storey - 1, 1);
        Furnish.chandelier(s, 2, 4, storey - 1, 1);
        Furnish.chandelier(s, 8, 4, storey - 1, 1);
        // The chamber: two beds, a bookcase, a seat in the oriel, a rug.
        int fl = storey;
        Gothic.bed(s, 2, fl + 1, 3, "north", -1);
        Gothic.bed(s, 2, fl + 1, 5, "north", -1);
        Gothic.bookcase(s, 8, fl + 1, 3, "west", 2);
        Furnish.chair(s, 5, fl + 1, z2 + 1, "north", WOOD);
        Furnish.rug(s, 4, 4, 6, 6, fl + 1, Gothic.CLOTH_CARPET);
        Gothic.candelabrum(s, 6, fl + 1, 2);
        Furnish.chandelier(s, 5, 5, fl + storey - 1, 1);
        Furnish.chandelier(s, 2, 4, fl + storey - 1, 1);
        // The attic: the third bed, stores, lanterns.
        fl = eaves;
        Furnish.bed(s, 3, fl + 1, 4, "west", "red");
        Furnish.crate(s, 7, fl + 1, 3, WOOD);
        Furnish.sack(s, 7, fl + 1, 4, true);
        Furnish.chandelier(s, 4, 4, fl + 5, 2);
        Furnish.chandelier(s, 7, 4, fl + 5, 2);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the merchant's house

    /**
     * A merchant's house of three storeys, its gable to the street: the upper floors stand on an arcade of two pointed
     * arches of pale stone between piers, under which a paved loggia runs before the shop; the shop front behind it has
     * its pointed doorway and two shop windows. Above, the front of the style: arcades of lancets in plaster between
     * piers, string courses, and a stone gable with a rose window, niches and pinnacles; a steep roof with a cresting
     * and dormers. Buttresses with pinnacles stand along its sides, where the banners of the house hang. Inside the
     * shop a counter, bolts of cloth, sacks, crates and the merchant's chest; a ladder climbs through the hall with its
     * chimneypiece, long table and the merchant's bed, the chamber with two beds, and the loft of stores.
     */
    static Structure merchant() {
        Structure s = new Structure(11, 29, 13);
        int x1 = 1;
        int x2 = 9;
        int z1 = 1;
        int z2 = 11;
        int storey = 5;
        int eaves = 3 * storey;
        shell(s, x1, z1, x2, z2, 0, eaves - 1, WALL);
        for (int i = 1; i <= 3; i++) {
            boards(s, x1, z1, x2, z2, i * storey);
        }
        s.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, 7, PLANKS);
        int ridge = Gothic.steepRoof(s, x1, z1, x2, z2, eaves, false, 2, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.cresting(s, 5, z1 + 1, 5, z2 - 1, ridge);
        for (int z = 4; z <= 8; z += 4) {
            Gothic.dormer(s, x1, z1, x2, z2, eaves, false, 2, z, true, 2);
            Gothic.dormer(s, x1, z1, x2, z2, eaves, false, 2, z, false, 2);
        }
        Gothic.facade(s, 5, 0, z2, "south", 9, 3, storey, 2);
        // The arcade: the ground floor of the front rebuilt as two pointed arches between piers, a paved loggia behind.
        s.fill(0, 0, z2 + 1, 10, storey - 1, z2 + 1, AIR);
        s.fill(x1, 1, z2, x2, storey - 1, z2, WALL);
        s.fill(x1, 0, z2, x2, 0, z2, ROUGH);
        for (int x : new int[]{3, 7}) {
            Gothic.pointedArch(s, x, 1, z2, "south", 3, 3, 1, TRACERY_STAIRS, TRACERY_SLAB);
            s.fill(x - 1, 0, z2 + 1, x + 1, 0, z2 + 1, Kit.slab(TRACERY_SLAB));
        }
        for (int x : new int[]{x1, x2}) {
            s.set(x, 0, z2 + 1, ROUGH).fill(x, 1, z2 + 1, x, 3, z2 + 1, WALL).set(x, 4, z2 + 1, CARVED);
        }
        // Pale hoods over the arches, meeting on a carved corbel over the middle pier.
        for (int x : new int[]{3, 7}) {
            s.set(x - 1, 4, z2 + 1, Kit.stairs(TRACERY_STAIRS, "east")).set(x + 1, 4, z2 + 1, Kit.stairs(TRACERY_STAIRS, "west"));
            s.set(x, 5, z2 + 1, Kit.topSlab(TRACERY_SLAB));
        }
        s.set(5, 4, z2 + 1, CARVED);
        s.set(5, 3, z2 + 1, Kit.upside(TRIM_STAIRS, "north"));
        s.fill(x1 + 1, 1, 9, x2 - 1, storey - 1, 10, AIR);
        Gothic.paving(s, x1 + 1, 9, x2 - 1, 10, 0);
        for (int x : new int[]{2, 5, 8}) {
            s.fill(x, storey - 1, 9, x, storey - 1, 10, Kit.topSlab(TRIM_SLAB));
        }
        s.set(3, storey - 1, 10, Gothic.SOUL_LANTERN_HANGING).set(7, storey - 1, 10, Gothic.SOUL_LANTERN_HANGING);
        // The shop front at the back of the loggia: the doorway and two shop windows.
        s.fill(x1, 1, 8, x2, storey - 1, 8, WALL);
        Gothic.portal(s, 5, 1, 8, "south", 1, false);
        Gothic.lancet(s, 3, 2, 8, "south", 1, 1, GLASS, CRIMSON);
        Gothic.lancet(s, 7, 2, 8, "south", 1, 1, GLASS, CRIMSON);
        s.set(4, 3, 9, TRACERY).set(6, 3, 9, TRACERY);
        // Windows of the sides and the back, the banners of the house.
        for (int i = 0; i < 3; i++) {
            int fl = i * storey;
            Gothic.lancet(s, 5, fl + 2, z1, "north", 1, 2, GLASS, VIOLET);
            if (i > 0) {
                Gothic.lancet(s, x1, fl + 2, 6, "west", 1, 2, GLASS, VIOLET);
                Gothic.lancet(s, x2, fl + 2, 6, "east", 1, 2, GLASS, VIOLET);
            }
        }
        Gothic.lancet(s, 5, eaves + 2, z1, "north", 1, 3, GLASS, VIOLET);
        for (int z : new int[]{4, 8}) {
            Gothic.buttress(s, x1, 0, z, "west", 12, 1, true);
            Gothic.buttress(s, x2, 0, z, "east", 12, 1, true);
        }
        s.set(0, 3, 10, Kit.wallBanner("red", "west")).set(10, 3, 10, Kit.wallBanner("red", "east"));
        // The ladder up the back wall through every floor.
        ladder(s, 3, 2, 1, eaves + 1, "south");
        // The shop: the counter, the chest behind it, goods on the floor and by the walls.
        s.fill(3, 1, 5, 7, 1, 5, Kit.upside(WOOD_STAIRS, "south"));
        s.set(4, 2, 5, Furnish.candles(2)).set(6, 2, 5, "minecraft:purple_carpet");
        Furnish.chest(s, 5, 1, 2, "south");
        Furnish.crate(s, 6, 1, 2, WOOD);
        Furnish.crate(s, 2, 1, 2, WOOD);
        Furnish.sack(s, 7, 1, 2, true);
        Furnish.sack(s, 8, 1, 2, false);
        Furnish.sack(s, 8, 2, 2, false);
        s.set(8, 1, 3, "minecraft:purple_wool").set(8, 2, 3, "minecraft:black_wool").set(8, 1, 4, "minecraft:gray_wool");
        s.set(2, 1, 7, Kit.CLOTH).set(8, 1, 7, "minecraft:white_wool");
        Furnish.chandelier(s, 5, 4, storey - 1, 1);
        Furnish.chandelier(s, 3, 7, storey - 1, 1);
        Furnish.chandelier(s, 7, 7, storey - 1, 1);
        // The hall: a chimneypiece, the long table with benches, the merchant's bed, a desk.
        int fl = storey;
        Gothic.fireplace(s, x2, fl + 1, 5, "west", eaves - 1);
        for (int y = eaves; y <= ridge - 2; y++) {
            s.set(8, y, 5, ROUGH);
        }
        s.set(8, ridge - 1, 5, Kit.ROUGH_FENCE);
        Furnish.longTable(s, 4, 7, 6, 7, fl + 1, WOOD);
        Furnish.bench(s, 4, 6, 6, 6, fl + 1, "south", WOOD);
        Furnish.bench(s, 4, 8, 6, 8, fl + 1, "north", WOOD);
        s.set(5, fl + 2, 7, Furnish.candles(3));
        Gothic.bed(s, 2, fl + 1, 10, "north", -1);
        Furnish.desk(s, 8, fl + 1, 10, "north", WOOD);
        Furnish.rug(s, 4, 3, 6, 4, fl + 1, Gothic.CLOTH_CARPET);
        Furnish.chandelier(s, 5, 4, fl + storey - 1, 1);
        Furnish.chandelier(s, 5, 8, fl + storey - 1, 1);
        // The chamber: two beds, a bookcase, a candelabrum.
        fl = 2 * storey;
        Gothic.bed(s, 8, fl + 1, 10, "north", 1);
        Gothic.bed(s, 2, fl + 1, 10, "north", -1);
        Gothic.bookcase(s, 7, fl + 1, 2, "south", 2);
        Gothic.candelabrum(s, 8, fl + 1, 6);
        Furnish.rug(s, 4, 6, 6, 9, fl + 1, Gothic.CLOTH_CARPET);
        Furnish.chandelier(s, 5, 4, fl + storey - 1, 1);
        Furnish.chandelier(s, 5, 8, fl + storey - 1, 1);
        // The loft of stores.
        fl = eaves;
        for (int z = 4; z <= 8; z += 2) {
            Furnish.crate(s, 4, fl + 1, z, WOOD);
            Furnish.sack(s, 6, fl + 1, z, z == 6);
        }
        Furnish.chandelier(s, 5, 4, fl + 6, 3);
        Furnish.chandelier(s, 5, 8, fl + 6, 3);
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- the house with the turret

    /**
     * A house with a turret: a block of two storeys and an attic, its stone gable to the street round a rose window,
     * and at its front corner an octagonal turret jutting into the street, rising high over the ridge to a cornice of
     * corbels, a pale ledge and a slim needle spire with purple lights and an iron cross. Lancets in pale frames light
     * the turret floor by floor; an iron railing with lamps closes the strip before the door. Inside, the hall with a
     * chimneypiece whose stack climbs over the roof, a table and the chest; a ladder to the chamber with two beds and
     * up to the attic with the third; the turret, entered from every floor, holds the stair, a study and a lookout
     * under the spire.
     */
    static Structure turret() {
        Structure s = new Structure(13, 41, 13);
        int x1 = 1;
        int x2 = 7;
        int z1 = 1;
        int z2 = 9;
        int storey = 5;
        int eaves = 2 * storey;
        int tx = 9;
        int tz = 9;
        int top = 22;
        shell(s, x1, z1, x2, z2, 0, eaves - 1, WALL);
        for (int i = 0; i <= 2; i++) {
            boards(s, x1, z1, x2, z2, i * storey);
        }
        int ridge = Gothic.steepRoof(s, x1, z1, x2, z2, eaves, false, 2, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.dormer(s, x1, z1, x2, z2, eaves, false, 2, 5, false, 2);
        Gothic.facade(s, 4, 0, z2, "south", 7, 2, storey, 2);
        s.set(4, 0, z2 + 1, Kit.stairs(TRIM_STAIRS, "north"));
        // Windows of the sides and the back.
        for (int i = 0; i < 2; i++) {
            int fl = i * storey;
            Gothic.lancet(s, x1, fl + 2, 7, "west", 1, 2, GLASS, VIOLET);
            Gothic.lancet(s, x2, fl + 2, 4, "east", 1, 2, GLASS, VIOLET);
            Gothic.lancet(s, 2, fl + 2, z1, "north", 1, 2, GLASS, VIOLET);
            Gothic.lancet(s, 6, fl + 2, z1, "north", 1, 2, GLASS, VIOLET);
        }
        // The turret: octagonal walls, floors, slits of glass, a passage from every floor of the house.
        Gothic.octagonWalls(s, tx, 0, 0, tz, 2, ROUGH);
        Gothic.octagonWalls(s, tx, 1, top, tz, 2, WALL);
        for (int y = 0; y <= top; y++) {
            s.fill(tx - 1, y, tz - 1, tx + 1, y, tz + 1, y % storey == 0 && y <= 15 ? PLANKS : AIR);
        }
        for (int fl = 0; fl <= 15; fl += storey) {
            Gothic.lancet(s, tx, fl + 2, tz + 2, "south", 1, 2, GLASS, VIOLET);
            Gothic.lancet(s, tx + 2, fl + 2, tz, "east", 1, 2, GLASS, VIOLET);
            if (fl >= 10) {
                Gothic.lancet(s, tx, fl + 2, tz - 2, "north", 1, 2, GLASS, VIOLET);
            }
        }
        s.fill(x2, 1, 8, x2, 2, 8, AIR);
        s.fill(x2, storey + 1, 8, x2, storey + 2, 8, AIR);
        // A band of trim at the eaves, the cornice of corbels, the ledge and the needle spire.
        ring(s, tx, eaves, tz, TRIM_STAIRS);
        turretTop(s, tx, top, tz, 17);
        ladder(s, tx + 1, tz + 1, 1, 16, "north");
        // The railing before the house, open at the door.
        Gothic.ironFence(s, 0, 12, 3, 12, 0, 2, true);
        // The hall: a chimneypiece on the west wall with its stack over the roof, a table, the chest, a cauldron.
        Gothic.fireplace(s, x1, 1, 5, "east", eaves - 1);
        for (int y = eaves; y <= ridge; y++) {
            s.set(2, y, 5, ROUGH);
        }
        s.set(2, ridge + 1, 5, Kit.ROUGH_FENCE);
        ladder(s, 4, 2, 1, eaves + 1, "south");
        Furnish.table(s, 5, 1, 6, WOOD);
        Furnish.chair(s, 5, 1, 5, "south", WOOD);
        Furnish.chair(s, 5, 1, 7, "north", WOOD);
        Furnish.chest(s, 6, 1, 3, "west");
        Furnish.crate(s, 6, 1, 2, WOOD);
        s.set(2, 1, 2, "minecraft:water_cauldron[level=3]");
        Furnish.sack(s, 2, 1, 8, true);
        Furnish.chandelier(s, 4, 5, storey - 1, 1);
        Furnish.chandelier(s, tx, tz, storey - 1, 1);
        // The chamber: two beds, a rug, a candelabrum.
        int fl = storey;
        Gothic.bed(s, 6, fl + 1, 4, "north", 1);
        Gothic.bed(s, 3, fl + 1, 8, "north", 1);
        Furnish.rug(s, 4, 5, 5, 7, fl + 1, Gothic.CLOTH_CARPET);
        Gothic.candelabrum(s, 2, fl + 1, 2);
        Furnish.chandelier(s, 4, 5, fl + storey - 1, 1);
        // The study in the turret.
        Furnish.desk(s, tx + 1, fl + 1, tz - 1, "west", WOOD);
        Furnish.books(s, tx - 1, tz + 1, tx - 1, tz + 1, fl + 1, fl + 2);
        Furnish.chandelier(s, tx, tz, fl + storey - 1, 1);
        // The attic: the third bed, stores.
        fl = eaves;
        Furnish.bed(s, 4, fl + 1, 7, "north", "red");
        Furnish.crate(s, 3, fl + 1, 4, WOOD);
        Furnish.sack(s, 5, fl + 1, 4, true);
        Furnish.chandelier(s, 4, 5, fl + 6, 2);
        // The upper rooms of the turret: stores, the lookout.
        Furnish.crate(s, tx + 1, fl + 1, tz - 1, WOOD);
        Furnish.chandelier(s, tx, tz, fl + storey - 1, 1);
        Gothic.candelabrum(s, tx + 1, 16, tz - 1);
        int hook = top + 1;
        while (!s.solid(tx, hook, tz)) {
            s.set(tx, hook, tz, Kit.chain());
            hook++;
        }
        s.set(tx, top, tz, Kit.hangingLantern());
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- the manor

    /**
     * The manor of a noble: a hall of two storeys across the back of the plot under a steep roof with stone gables, an
     * iron cresting, dormers and a tall stack, and a wing of three storeys reaching to the street with the front of the
     * style (a pointed doorway, lancets in plaster, a very steep stone gable with its pinnacle) whose back gable rises
     * high over the roof of the hall. Beside the wing a court paved in pale and black stone is closed to the street by
     * a wall with a pointed gateway under a little gable and a pinnacle; in it a yew, a lamp on a black post, and the
     * grand doorway of the hall under its wimperg. Buttresses with pinnacles stand along the walls. Inside, the great
     * hall with its chimneypiece, the long table with benches, the kitchen corner and the chest; a stair in the wing to
     * the lord's chamber with two beds, a chamber with a bed, a desk and a bookcase, the chamber of the wing with the
     * fourth bed, a ladder to the study over it open to the roof, and a loft of stores.
     */
    static Structure manor() {
        Structure s = new Structure(13, 29, 15);
        int storey = 5;
        int eaves = 2 * storey;
        // The hall, the wing and the court.
        shell(s, 1, 1, 11, 7, 0, eaves - 1, WALL);
        int wingEaves = 3 * storey;
        shell(s, 1, 7, 5, 13, 0, wingEaves - 1, WALL);
        for (int i = 0; i <= 2; i++) {
            boards(s, 1, 1, 11, 7, i * storey);
        }
        boards(s, 1, 7, 5, 13, 0);
        boards(s, 1, 7, 5, 13, storey);
        s.fill(11, 0, 8, 11, 0, 13, ROUGH).fill(11, 1, 8, 11, 2, 13, WALL).fill(11, 3, 13, 11, 4, 13, WALL);
        Gothic.ironFence(s, 11, 8, 11, 12, 3, 2, false);
        s.fill(6, 0, 13, 10, 0, 13, ROUGH).fill(6, 1, 13, 10, 4, 13, WALL);
        s.fill(11, 4, 13, 11, 5, 13, WALL);
        s.set(6, 5, 13, Kit.slab(TRIM_SLAB)).set(10, 5, 13, Kit.slab(TRIM_SLAB));
        s.set(11, 6, 13, Gothic.SOUL_LANTERN);
        Gothic.paving(s, 6, 8, 10, 12, 0);
        // The roofs: the hall's with its cresting and dormers; the top storey of the wing rises over it to a roof with
        // stone gables at both ends.
        int ridge = Gothic.steepRoof(s, 1, 1, 11, 7, eaves, true, 2, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.cresting(s, 2, 4, 10, 4, ridge);
        s.walls(1, eaves, 7, 5, wingEaves - 1, 13, WALL);
        boards(s, 1, 7, 5, 13, eaves);
        Gothic.steepRoof(s, 1, 7, 5, 13, wingEaves, false, 3, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.dormer(s, 1, 1, 11, 7, eaves, true, 2, 3, false, 2);
        Gothic.dormer(s, 1, 1, 11, 7, eaves, true, 2, 9, false, 2);
        Gothic.dormer(s, 1, 1, 11, 7, eaves, true, 2, 9, true, 2);
        // The front of the wing to the street.
        Gothic.facade(s, 3, 0, 13, "south", 5, 3, storey, 3);
        s.set(3, 0, 14, Kit.stairs(TRIM_STAIRS, "north"));
        // The gateway of the court under a little gable with a pinnacle, a lamp hanging in it.
        Gothic.pointedArch(s, 8, 1, 13, "south", 3, 3, 1, TRACERY_STAIRS, TRACERY_SLAB);
        s.fill(7, 0, 13, 9, 0, 13, Kit.slab(TRACERY_SLAB));
        s.set(7, 5, 13, Kit.stairs(TRACERY_STAIRS, "east")).set(9, 5, 13, Kit.stairs(TRACERY_STAIRS, "west"));
        s.set(8, 5, 13, CARVED);
        Gothic.pinnacle(s, 8, 6, 13, 3);
        s.set(8, 4, 13, Kit.chain()).set(8, 3, 13, Gothic.SOUL_LANTERN_HANGING);
        // The grand doorway of the hall from the court.
        Gothic.portal(s, 8, 1, 7, "south", 1, true);
        // Buttresses with pinnacles along the walls.
        for (int z : new int[]{4, 10}) {
            Gothic.buttress(s, 1, 0, z, "west", 6, 1, true);
        }
        for (int x : new int[]{4, 8}) {
            Gothic.buttress(s, x, 0, 1, "north", 6, 1, true);
        }
        Gothic.buttress(s, 11, 0, 4, "east", 6, 1, true);
        Gothic.buttress(s, 11, 0, 10, "east", 3, 1, false);
        // Windows: the west side, the back, the east gable, the wing toward the court.
        for (int i = 0; i < 2; i++) {
            int fl = i * storey;
            for (int z : new int[]{2, 6, 8, 12}) {
                Gothic.lancet(s, 1, fl + 2, z, "west", 1, 2, GLASS, VIOLET);
            }
            Gothic.lancet(s, 2, fl + 2, 1, "north", 1, 2, GLASS, VIOLET);
            Gothic.lancet(s, 10, fl + 2, 1, "north", 1, 2, GLASS, VIOLET);
            Gothic.lancet(s, 11, fl + 2, 2, "east", 1, 2, GLASS, VIOLET);
            Gothic.lancet(s, 11, fl + 2, 6, "east", 1, 2, GLASS, VIOLET);
            Gothic.lancet(s, 5, fl + 2, 9, "east", 1, 2, GLASS, AZURE);
            Gothic.lancet(s, 5, fl + 2, 11, "east", 1, 2, GLASS, AZURE);
        }
        Gothic.lancet(s, 1, eaves + 2, 4, "west", 1, 3, GLASS, VIOLET);
        Gothic.lancet(s, 1, eaves + 2, 10, "west", 1, 2, GLASS, VIOLET);
        Gothic.lancet(s, 5, eaves + 2, 10, "east", 1, 2, GLASS, AZURE);
        Gothic.lancet(s, 11, eaves + 2, 4, "east", 1, 3, GLASS, VIOLET);
        // The court: a yew, a lamp on a black post.
        s.fill(10, 1, 11, 10, 3, 11, Kit.log("minecraft:dark_oak_log", "y"));
        for (int y = 3; y <= 6; y++) {
            int r = y == 6 ? 0 : 1;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (s.inside(10 + dx, y, 11 + dz) && !s.solid(10 + dx, y, 11 + dz) && (y > 3 || dx != 0 || dz != 0)) {
                        s.set(10 + dx, y, 11 + dz, Kit.leaves("spruce"));
                    }
                }
            }
        }
        s.fill(7, 1, 10, 7, 2, 10, Gothic.STAND).set(7, 3, 10, Gothic.SOUL_LANTERN);
        // The stair in the wing from the street door up to the chambers.
        for (int i = 0; i <= 4; i++) {
            s.set(3, 1 + i, 12 - i, Kit.stairs(WOOD_STAIRS, "north"));
            if (i > 0) {
                s.fill(3, 1, 12 - i, 3, i, 12 - i, PLANKS);
            }
        }
        s.set(3, storey, 9, AIR).set(3, storey, 10, AIR);
        s.fill(3, storey + 1, 7, 3, storey + 2, 7, AIR);
        s.fill(4, 1, 7, 4, 2, 7, AIR);
        Furnish.chandelier(s, 2, 10, storey - 1, 1);
        Furnish.chandelier(s, 4, 9, storey - 1, 1);
        // The great hall: the chimneypiece and its stack, the long table with benches, the kitchen, the chest, banners.
        Gothic.fireplace(s, 6, 1, 1, "south", eaves - 1);
        for (int y = eaves; y <= ridge + 1; y++) {
            s.set(6, y, 2, ROUGH);
        }
        s.set(6, ridge + 2, 2, Kit.ROUGH_FENCE);
        Furnish.longTable(s, 4, 4, 8, 4, 1, WOOD);
        Furnish.bench(s, 4, 3, 8, 3, 1, "south", WOOD);
        Furnish.bench(s, 4, 5, 8, 5, 1, "north", WOOD);
        s.set(5, 2, 4, Furnish.candles(3)).set(7, 2, 4, Furnish.candles(3));
        Furnish.kitchen(s, 8, 1, 2, "south", WOOD);
        Furnish.chest(s, 2, 1, 2, "east");
        Gothic.candelabrum(s, 2, 1, 6);
        Gothic.candelabrum(s, 10, 1, 6);
        s.set(3, 3, 2, Kit.wallBanner("red", "south"));
        Furnish.chandelier(s, 3, 4, storey - 1, 1);
        Furnish.chandelier(s, 6, 4, storey - 1, 1);
        Furnish.chandelier(s, 9, 4, storey - 1, 1);
        // The upper floor of the hall: the lord's chamber and a chamber beyond a partition with a door.
        int fl = storey;
        s.fill(6, fl + 1, 3, 6, eaves - 1, 6, WALL);
        s.door(6, fl + 1, 5, DOOR, "east", "left");
        Gothic.bed(s, 2, fl + 1, 3, "north", -1);
        Gothic.bed(s, 5, fl + 1, 3, "north", 1);
        Furnish.rug(s, 3, 4, 4, 5, fl + 1, Gothic.CLOTH_CARPET);
        Gothic.candelabrum(s, 2, fl + 1, 6);
        Furnish.chandelier(s, 3, 4, eaves - 1, 1);
        Gothic.bed(s, 10, fl + 1, 3, "north", 1);
        Furnish.desk(s, 8, fl + 1, 6, "north", WOOD);
        Gothic.bookcase(s, 7, fl + 1, 3, "east", 2);
        ladder(s, 10, 5, fl + 1, eaves + 1, "west");
        Furnish.chandelier(s, 8, 4, eaves - 1, 1);
        // The chamber of the wing, and over it the study open to the roof, a ladder between them.
        Gothic.bed(s, 4, fl + 1, 12, "north", 1);
        Gothic.candelabrum(s, 2, fl + 1, 12);
        Furnish.chandelier(s, 3, 10, eaves - 1, 1);
        ladder(s, 3, 12, fl + 1, eaves + 1, "north");
        Furnish.desk(s, 2, eaves + 1, 9, "south", WOOD);
        Gothic.bookcase(s, 3, eaves + 1, 8, "south", 2);
        Furnish.rug(s, 2, 11, 4, 11, eaves + 1, Gothic.CLOTH_CARPET);
        Gothic.candelabrum(s, 4, eaves + 1, 12);
        Furnish.chandelier(s, 3, 10, wingEaves + 5, 3);
        // The loft of stores over the hall.
        fl = eaves;
        for (int x = 3; x <= 9; x += 2) {
            Furnish.crate(s, x, fl + 1, 3, WOOD);
            Furnish.sack(s, x, fl + 1, 5, x % 4 == 1);
        }
        Furnish.chandelier(s, 4, 4, 15, 2);
        Furnish.chandelier(s, 8, 4, 15, 2);
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- the smithy

    /**
     * A smithy: a hall of stone under a very steep roof with stone gables and pinnacles, its long side to the street
     * opened by two great pointed arches under pale hoods onto a yard of rough stone where the anvil, the grindstone
     * and the quenching tub stand, gargoyles at its corners. Against the east gable rises the stack of the forge, a
     * square shaft of rough stone high over the ridge with a band and a cap of trim. Inside, open to the roof: the
     * forge of rough stone with its fire under a hood whose flue passes into the stack, the blast furnace, the smithing
     * table, the smith's post with his chest beside it, a bin of coal, bars of iron, sacks and crates, lanterns on
     * chains from the ridge.
     */
    static Structure smithy() {
        Structure s = new Structure(13, 24, 11);
        int x1 = 1;
        int x2 = 9;
        int z1 = 1;
        int z2 = 7;
        int eaves = 8;
        shell(s, x1, z1, x2, z2, 0, eaves - 1, WALL);
        s.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, ROUGH);
        int ridge = Gothic.steepRoof(s, x1, z1, x2, z2, eaves, true, 2, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.cresting(s, x1 + 1, 4, x2 - 1, 4, ridge);
        // The front: two great pointed arches under pale hoods, a pier between them, gargoyles at the corners.
        for (int x : new int[]{3, 7}) {
            Gothic.pointedArch(s, x, 1, z2, "south", 3, 4, 1, TRACERY_STAIRS, TRACERY_SLAB);
            s.set(x - 2, 5, z2 + 1, Kit.stairs(TRACERY_STAIRS, "east")).set(x + 2, 5, z2 + 1, Kit.stairs(TRACERY_STAIRS, "west"));
            s.set(x - 1, 6, z2 + 1, Kit.stairs(TRACERY_STAIRS, "east")).set(x + 1, 6, z2 + 1, Kit.stairs(TRACERY_STAIRS, "west"));
            s.set(x, 7, z2 + 1, Kit.slab(TRACERY_SLAB));
            s.fill(x - 1, 0, z2, x + 1, 0, z2, ROUGH);
        }
        s.set(5, 5, z2 + 1, CARVED);
        Gothic.buttress(s, 5, 0, z2, "south", 4, 1, false);
        Gothic.gargoyle(s, x1, eaves - 1, z2 + 1, "south");
        Gothic.gargoyle(s, x2, eaves - 1, z2 + 1, "south");
        // The yard of rough stone before the arches: anvil, grindstone, quenching tub, a lamp on a black post.
        s.fill(x1, 0, z2 + 1, x2, 0, z2 + 2, ROUGH);
        s.set(5, 0, z2 + 1, ROUGH);
        s.set(2, 1, 9, "minecraft:anvil[facing=east]");
        s.set(8, 1, 9, "minecraft:water_cauldron[level=3]");
        s.set(9, 1, 8, "minecraft:grindstone[face=floor,facing=south]");
        s.fill(0, 1, 8, 0, 2, 8, Gothic.STAND).set(0, 3, 8, Gothic.SOUL_LANTERN);
        s.fill(10, 1, 8, 10, 2, 8, Gothic.STAND).set(10, 3, 8, Gothic.SOUL_LANTERN);
        // The back and the west gable: high lancets.
        Gothic.lancet(s, 3, 3, z1, "north", 1, 2, GLASS, CRIMSON);
        Gothic.lancet(s, 7, 3, z1, "north", 1, 2, GLASS, CRIMSON);
        Gothic.lancet(s, x1, 2, 4, "west", 1, 3, GLASS, CRIMSON);
        Gothic.lancet(s, x1, eaves + 2, 4, "west", 1, 2, GLASS, VIOLET);
        // The stack of the forge against the east gable: rough stone, a band and a cap of trim.
        int stackTop = ridge + 4;
        s.fill(10, 0, 2, 11, stackTop, 3, ROUGH);
        s.fill(10, 0, 4, 11, 1, 4, ROUGH).fill(10, 2, 4, 11, 2, 4, Kit.stairs(ROUGH_STAIRS, "north"));
        s.fill(10, 0, 1, 11, 1, 1, ROUGH).fill(10, 2, 1, 11, 2, 1, Kit.stairs(ROUGH_STAIRS, "south"));
        s.fill(12, 0, 2, 12, 1, 3, ROUGH).fill(12, 2, 2, 12, 2, 3, Kit.stairs(ROUGH_STAIRS, "west"));
        s.fill(10, ridge - 1, 2, 11, ridge - 1, 3, TRACERY);
        s.fill(10, eaves, 2, 11, eaves, 3, TRACERY);
        s.fill(10, stackTop, 2, 11, stackTop, 3, TRACERY);
        s.set(10, stackTop + 1, 2, Kit.ROUGH_FENCE).set(11, stackTop + 1, 3, Kit.ROUGH_FENCE);
        s.set(11, stackTop + 1, 2, Kit.slab(TRACERY_SLAB)).set(10, stackTop + 1, 3, Kit.slab(TRACERY_SLAB));
        // Coal and iron by the stack.
        s.fill(10, 0, 6, 12, 0, 6, Kit.log("minecraft:oak_log", "x")).fill(11, 1, 6, 12, 1, 6, Kit.log("minecraft:oak_log", "x"));
        s.set(12, 0, 7, "minecraft:coal_block").set(12, 0, 5, "minecraft:coal_block");
        // The forge: a bed of rough stone with its fire, a hood, the flue through the gable into the stack.
        s.fill(7, 1, 2, 8, 1, 3, ROUGH);
        s.set(8, 2, 2, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        s.set(7, 2, 2, ROUGH).set(8, 2, 3, IRON).set(7, 2, 3, Kit.slab(ROUGH_SLAB));
        s.fill(7, 3, 2, 8, 3, 2, ROUGH).fill(7, 4, 3, 8, 4, 3, Kit.upside(ROUGH_STAIRS, "north"));
        s.fill(7, 4, 2, 8, 5, 2, ROUGH).fill(7, 5, 3, 8, 5, 3, Kit.stairs(ROUGH_STAIRS, "north"));
        s.fill(7, 6, 2, 8, 9, 2, ROUGH);
        // The smith's post with his chest, the blast furnace, the smithing table, the anvil, coal and iron.
        s.set(4, 1, 2, "kingdomworld:work_post");
        Furnish.chest(s, 5, 1, 2, "south");
        s.set(6, 1, 2, "minecraft:blast_furnace[facing=south,lit=false]");
        s.set(2, 1, 2, "minecraft:smithing_table");
        s.set(3, 1, 2, "minecraft:stonecutter[facing=south]");
        s.set(6, 1, 5, "minecraft:anvil[facing=west]");
        s.set(2, 1, 4, "minecraft:coal_block").set(2, 1, 5, "minecraft:iron_block");
        Furnish.sack(s, 2, 1, 6, false);
        s.set(8, 1, 6, "minecraft:cauldron");
        s.fill(2, 3, 3, 2, 3, 5, Kit.upside(WOOD_STAIRS, "east"));
        Furnish.chandelier(s, 3, 4, ridge - 3, 4);
        Furnish.chandelier(s, 6, 4, ridge - 3, 4);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the tavern

    /**
     * A tavern of two houses under twin gables to the street: two fronts of the style side by side, each with its
     * pointed doorway, arcade of lancets in plaster and very steep stone gable, between them a pier rising to a
     * pinnacle with the banner of the house and a lamp on an iron bracket; buttresses with pinnacles on the sides.
     * Inside, one taproom through both houses under an arcade of pillars: the counter with the innkeeper's post, his
     * chest and three barrels behind it, a kitchen with a smoker, a chimneypiece, long tables with benches and small
     * tables with chairs, chandeliers; a stair along the west wall up to the landing and three rooms for guests with
     * two beds each, and ladders to the lofts.
     */
    static Structure tavern() {
        Structure s = new Structure(13, 24, 15);
        int storey = 5;
        int eaves = 2 * storey;
        int z1 = 1;
        int z2 = 13;
        shell(s, 1, z1, 11, z2, 0, eaves - 1, WALL);
        for (int i = 0; i <= 2; i++) {
            boards(s, 1, z1, 11, z2, i * storey);
        }
        // The wall between the two houses: whole above, an arcade of pillars in the taproom.
        s.fill(6, storey + 1, z1 + 1, 6, eaves - 1, z2 - 1, WALL);
        s.fill(6, storey - 1, z1 + 1, 6, storey - 1, z2 - 1, Kit.log(BEAM, "z"));
        for (int z = z1 + 1; z < z2; z++) {
            if (z % 3 == 1) {
                s.fill(6, 1, z, 6, 3, z, WALL);
            } else {
                s.set(6, 3, z, Kit.upside(TRIM_STAIRS, z % 3 == 2 ? "north" : "south"));
            }
        }
        // The twin roofs, the gutter between them, the piers with pinnacles at both ends of it.
        Gothic.steepRoof(s, 1, z1, 5, z2, eaves, false, 3, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.steepRoof(s, 7, z1, 11, z2, eaves, false, 3, Gothic.PARAPET, Gothic.PARAPET);
        s.fill(6, eaves, z1, 6, eaves, z2, Kit.slab(WALL_SLAB));
        for (int z : new int[]{z1, z2}) {
            s.fill(6, eaves, z, 6, eaves + 2, z, WALL);
            Gothic.pinnacle(s, 6, eaves + 3, z, 4);
        }
        // The two fronts, the pier between them with the banner and a lamp, the steps.
        Gothic.facade(s, 3, 0, z2, "south", 5, 2, storey, 3);
        Gothic.facade(s, 9, 0, z2, "south", 5, 2, storey, 3);
        s.set(6, storey, z2 + 1, Kit.upside(TRIM_STAIRS, "north")).set(6, eaves, z2 + 1, Kit.upside(TRIM_STAIRS, "north"));
        s.set(6, 7, z2 + 1, Kit.wallBanner("red", "south"));
        bracketLamp(s, 6, 4, z2, "south");
        s.set(3, 0, z2 + 1, Kit.stairs(TRIM_STAIRS, "north")).set(9, 0, z2 + 1, Kit.stairs(TRIM_STAIRS, "north"));
        // Windows of the back and the sides, buttresses with pinnacles.
        for (int i = 0; i < 2; i++) {
            int fl = i * storey;
            Gothic.lancet(s, 3, fl + 2, z1, "north", 1, 2, GLASS, VIOLET);
            Gothic.lancet(s, 9, fl + 2, z1, "north", 1, 2, GLASS, VIOLET);
            for (int z : new int[]{4, 10}) {
                Gothic.lancet(s, 1, fl + 2, z, "west", 1, 2, GLASS, VIOLET);
                Gothic.lancet(s, 11, fl + 2, z, "east", 1, 2, GLASS, VIOLET);
            }
        }
        for (int x : new int[]{3, 9}) {
            Gothic.lancet(s, x, eaves + 2, z1, "north", 1, 2, GLASS, VIOLET);
        }
        Gothic.buttress(s, 1, 0, 7, "west", 7, 1, true);
        Gothic.buttress(s, 11, 0, 7, "east", 7, 1, true);
        // The counter: the innkeeper's post with his chest and three barrels behind it, a shelf with candles.
        s.set(9, 1, 3, "kingdomworld:work_post");
        Furnish.chest(s, 10, 1, 3, "west");
        s.set(8, 1, 2, Kit.barrel()).set(9, 1, 2, Kit.barrel()).set(10, 1, 2, Kit.barrel());
        s.fill(8, 1, 5, 10, 1, 5, Kit.upside(WOOD_STAIRS, "south"));
        s.set(8, 2, 5, Furnish.candles(2)).set(10, 2, 5, Furnish.candles(3));
        s.fill(8, 3, 2, 10, 3, 2, Kit.topSlab(WOOD_SLAB));
        s.set(8, 4, 2, Furnish.candles(3)).set(10, 4, 2, Furnish.candles(2));
        // The kitchen at the back of the west house.
        Furnish.kitchen(s, 2, 1, 2, "south", WOOD);
        s.set(5, 1, 2, "minecraft:smoker[facing=south,lit=false]");
        Furnish.sack(s, 2, 1, 4, true);
        Furnish.crate(s, 2, 1, 5, WOOD);
        // The chimneypiece on the east wall, its stack over the roof.
        Gothic.fireplace(s, 11, 1, 9, "west", eaves - 1);
        for (int y = eaves; y <= 18; y++) {
            s.set(10, y, 9, ROUGH);
        }
        s.set(10, 19, 9, Kit.ROUGH_FENCE);
        // Tables and benches.
        Furnish.longTable(s, 4, 7, 4, 10, 1, WOOD);
        Furnish.bench(s, 3, 7, 3, 10, 1, "east", WOOD);
        Furnish.bench(s, 5, 7, 5, 10, 1, "west", WOOD);
        s.set(4, 2, 8, Furnish.candles(3)).set(4, 2, 10, Furnish.candles(2));
        Furnish.table(s, 8, 1, 11, WOOD);
        Furnish.chair(s, 7, 1, 11, "east", WOOD);
        Furnish.chair(s, 8, 1, 10, "south", WOOD);
        Furnish.table(s, 8, 1, 7, WOOD);
        Furnish.chair(s, 7, 1, 7, "east", WOOD);
        Furnish.chair(s, 9, 1, 7, "west", WOOD);
        Gothic.candelabrum(s, 10, 1, 12);
        Furnish.chandelier(s, 3, 4, storey - 1, 1);
        Furnish.chandelier(s, 4, 9, storey - 1, 1);
        Furnish.chandelier(s, 8, 4, storey - 1, 1);
        Furnish.chandelier(s, 8, 9, storey - 1, 1);
        // The stair along the west wall up to the landing.
        for (int i = 0; i <= 4; i++) {
            s.set(2, 1 + i, 12 - i, Kit.stairs(WOOD_STAIRS, "north"));
            if (i > 0) {
                s.fill(2, 1, 12 - i, 2, i, 12 - i, PLANKS);
            }
        }
        s.set(2, storey, 9, AIR).set(2, storey, 10, AIR).set(2, storey, 11, AIR);
        // The rooms for guests: one in the west house behind the landing, two in the east house.
        int fl = storey;
        s.fill(2, fl + 1, 6, 5, eaves - 1, 6, PLANKS);
        s.door(4, fl + 1, 6, DOOR, "north", "left");
        s.door(6, fl + 1, 9, DOOR, "east", "left");
        s.fill(7, fl + 1, 7, 10, eaves - 1, 7, PLANKS);
        s.door(8, fl + 1, 7, DOOR, "north", "left");
        Gothic.bed(s, 2, fl + 1, 3, "north", -1);
        Gothic.bed(s, 5, fl + 1, 3, "north", 1);
        Furnish.chandelier(s, 3, 4, eaves - 1, 1);
        Gothic.bed(s, 7, fl + 1, 3, "north", -1);
        Gothic.bed(s, 10, fl + 1, 3, "north", 1);
        Furnish.chandelier(s, 8, 4, eaves - 1, 1);
        Gothic.bed(s, 7, fl + 1, 12, "north", -1);
        Gothic.bed(s, 9, fl + 1, 12, "north", 1);
        Furnish.chandelier(s, 8, 10, eaves - 1, 1);
        Gothic.candelabrum(s, 4, fl + 1, 11);
        Furnish.chandelier(s, 4, 9, eaves - 1, 1);
        // Ladders to the lofts of stores under the roofs, lanterns in them.
        ladder(s, 3, 12, fl + 1, eaves + 1, "north");
        ladder(s, 10, 11, fl + 1, eaves + 1, "west");
        for (int x : new int[]{3, 9}) {
            Furnish.crate(s, x, eaves + 1, 4, WOOD);
            Furnish.sack(s, x, eaves + 1, 6, true);
            Furnish.chandelier(s, x, 5, eaves + 5, 2);
            Furnish.chandelier(s, x, 10, eaves + 5, 2);
        }
        s.connect();
        s.trimSky(3);
        return s;
    }

    // ---------------------------------------------------------------- the town wall and its gate

    /**
     * A piece of the town wall with its gate: two curtains of dark ashlar with buttresses and pinnacles on the field
     * side, each with a walk behind a pierced tracery parapet and a stair of stone up to it from the town, meeting a
     * gatehouse. Its passage runs under pointed arches with the teeth of a raised portcullis in the outer one and a
     * ribbed vault, a pale hood over the arch on the field side; over it the gate room opens onto both walks, and a
     * ladder leads to the roof walk inside a crown of parapet, great pinnacles and gargoyles. Two slim octagonal towers
     * flank the arch on the field side, rising high over the crown to cornices of corbels and needle spires with purple
     * lucarnes and iron crosses; their guard rooms open from the passage and the gate room. Banners of the kingdom hang
     * on the town side.
     */
    static Structure wallGate() {
        Structure s = new Structure(23, 40, 11);
        int walk = 8;
        int top = 13;
        int towerTop = 19;
        // The curtains, their walks, parapets, buttresses and stairs, the same on both sides.
        for (int side = -1; side <= 1; side += 2) {
            int far = side < 0 ? 0 : 22;
            int near = side < 0 ? 7 : 15;
            int lo = Math.min(far, near);
            int hi = Math.max(far, near);
            s.fill(lo, 0, 4, hi, 0, 6, ROUGH).fill(lo, 1, 4, hi, walk - 1, 6, WALL);
            Gothic.parapet(s, side < 0 ? 0 : 17, 6, side < 0 ? 5 : 22, 6, walk);
            for (int d : new int[]{1, 3}) {
                Gothic.buttress(s, 11 + side * (11 - d), 0, 6, "south", 6, 2, true);
            }
            for (int i = 1; i <= walk - 1; i++) {
                int x = far - side * i;
                s.fill(x, 0, 3, x, i - 1, 3, WALL);
                s.set(x, i, 3, Kit.stairs(WALL_STAIRS, side < 0 ? "east" : "west"));
            }
            s.set(far, walk, 4, Gothic.SOUL_LANTERN);
        }
        // The gatehouse: its walls, the passage under its vault, the gate room, the roof walk.
        shell(s, 8, 2, 14, 9, 0, top, WALL);
        s.fill(9, 0, 3, 13, 0, 8, ROUGH);
        s.fill(9, 1, 3, 13, 6, 8, WALL);
        s.fill(10, 0, 2, 12, 0, 9, ROUGH);
        s.fill(10, 1, 2, 12, 5, 9, AIR);
        Gothic.vault(s, 10, 3, 12, 8, 4, false, 2, 3);
        Gothic.pointedArch(s, 11, 1, 9, "south", 3, 4, 1, TRACERY_STAIRS, TRACERY_SLAB);
        Gothic.pointedArch(s, 11, 1, 2, "north", 3, 4, 1, TRACERY_STAIRS, TRACERY_SLAB);
        s.fill(10, 4, 9, 12, 4, 9, IRON);
        s.fill(9, walk - 1, 3, 13, walk - 1, 8, PLANKS);
        s.fill(9, walk, 3, 13, top - 1, 8, AIR);
        s.fill(9, top, 3, 13, top, 8, WALL);
        Gothic.crown(s, 8, 2, 14, 9, top + 1, true);
        // The pale hood over the outer arch, the windows of the gate room.
        s.set(9, 5, 10, Kit.stairs(TRACERY_STAIRS, "east")).set(13, 5, 10, Kit.stairs(TRACERY_STAIRS, "west"));
        s.set(10, 6, 10, Kit.stairs(TRACERY_STAIRS, "east")).set(12, 6, 10, Kit.stairs(TRACERY_STAIRS, "west"));
        s.set(11, 7, 10, Kit.slab(TRACERY_SLAB));
        Gothic.lancet(s, 11, walk + 1, 9, "south", 1, 2, CRIMSON, VIOLET);
        Gothic.lancet(s, 11, walk + 1, 2, "north", 1, 2, GLASS, VIOLET);
        s.set(9, 9, 1, Kit.wallBanner("red", "north")).set(13, 9, 1, Kit.wallBanner("red", "north"));
        bracketLamp(s, 9, 4, 2, "north");
        bracketLamp(s, 13, 4, 2, "north");
        // Doors from the walks into the gate room.
        s.door(8, walk, 5, DOOR, "east", "left");
        s.door(14, walk, 5, DOOR, "west", "left");
        // The two towers flanking the arch on the field side.
        for (int tx : new int[]{7, 15}) {
            int tz = 8;
            Gothic.octagonWalls(s, tx, 0, 0, tz, 2, ROUGH);
            Gothic.octagonWalls(s, tx, 1, towerTop, tz, 2, WALL);
            for (int y = 0; y <= towerTop; y++) {
                s.fill(tx - 1, y, tz - 1, tx + 1, y, tz + 1, y == 0 || y == walk - 1 || y == top + 1 ? PLANKS : AIR);
            }
            ring(s, tx, top, tz, TRACERY_STAIRS);
            ring(s, tx, walk - 1, tz, TRACERY_STAIRS);
            turretTop(s, tx, towerTop, tz, 17);
            int out = tx < 11 ? -1 : 1;
            String outward = out < 0 ? "west" : "east";
            for (int y : new int[]{2, walk + 1, top + 3}) {
                Gothic.lancet(s, tx + 2 * out, y, tz, outward, 1, 2, GLASS, VIOLET);
                s.set(tx, y, tz + 2, GLASS).set(tx, y + 1, tz + 2, GLASS);
            }
            // Its doors from the passage and from the gate room, a ladder to its upper rooms, its lamps.
            int door = tx - out * 2;
            s.fill(door, 1, tz, door, 2, tz, AIR);
            s.fill(door, walk, tz - 1, door, walk + 1, tz - 1, AIR);
            ladder(s, tx + out, tz + 1, 1, top + 1, out < 0 ? "east" : "west");
            Furnish.chandelier(s, tx, tz, walk - 2, 2);
            Furnish.chandelier(s, tx, tz, top, 2);
            s.set(tx, towerTop, tz, Kit.hangingLantern());
            s.set(tx - out, 1, tz - 1, "minecraft:grindstone[face=floor,facing=" + (out < 0 ? "east" : "west") + "]");
        }
        // The gate room: a table with chairs, racks of arms, a candelabrum, lamps, a ladder to the roof walk.
        Furnish.table(s, 11, walk, 5, WOOD);
        Furnish.chair(s, 10, walk, 5, "east", WOOD);
        Furnish.chair(s, 12, walk, 5, "west", WOOD);
        Furnish.weaponRack(s, 9, walk, 3, "south", WOOD);
        Gothic.candelabrum(s, 13, walk, 6);
        ladder(s, 12, 3, walk, top, "south");
        Furnish.chandelier(s, 11, 6, top - 1, 1);
        Furnish.chandelier(s, 9, 4, top - 1, 1);
        s.set(10, walk + 2, 3, Kit.wallBanner("red", "south"));
        // Lamps on the roof walk and in the passage.
        s.set(9, top + 1, 3, Gothic.SOUL_LANTERN).set(13, top + 1, 8, Gothic.SOUL_LANTERN);
        s.set(11, 3, 5, Kit.hangingLantern());
        s.set(11, 4, 5, Kit.chain());
        s.connect();
        s.trimSky(3);
        return s;
    }
}
