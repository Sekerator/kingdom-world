package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The field camp of the style "gothic" (see {@link Gothic}): the four buildings an army of a gothic kingdom raises in the
 * field, each on the ground, with the entrance, the role and the counting blocks of the plain building of a camp it
 * stands for, so that a camp may be laid with either. The army builds light and quick but in its own manner: halls of
 * dark timber between gables of stone, roofs as steep as tents with iron crestings on their ridges, pointed doorways
 * and windows that glow purple, pinnacles on the gables, palisades of dark stakes tipped with iron, long banners of the
 * kingdom, soul lanterns on black posts and brackets. The barracks hall sleeps twelve under a small belfry spire, the
 * store is a barn with a stone porch over its cart road, the watch tower ends in an open arcade under a needle spire,
 * and the training yard has its butts under pointed hoods, its dummies in tabards and its racks of weapons.
 */
final class GothicCamp {
    private static final String WALL = Gothic.WALL;
    private static final String ROUGH = Gothic.ROUGH;
    private static final String TRIM = Gothic.TRIM;
    private static final String TRIM_STAIRS = Gothic.TRIM_STAIRS;
    private static final String TRIM_SLAB = Gothic.TRIM_SLAB;
    private static final String CARVED = Gothic.CARVED;
    private static final String PLASTER = Gothic.PLASTER;
    private static final Kit.Roof ROOF = Gothic.ROOF;
    private static final Kit.Roof SPIRE = Gothic.SPIRE;
    private static final String WOOD = Gothic.WOOD;
    private static final String PLANKS = Gothic.PLANKS;
    private static final String WOOD_FENCE = Gothic.WOOD_FENCE;
    private static final String BEAM = Gothic.BEAM;
    private static final String DOOR = Gothic.DOOR;
    private static final String BOARDS = "minecraft:stripped_spruce_log[axis=y]";
    private static final String GLASS = Gothic.GLASS;
    private static final String VIOLET = Gothic.VIOLET;
    private static final String IRON = Gothic.IRON;
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String TARGET = "minecraft:target[power=0]";
    private static final String BARRACKS = "kingdomworld:barracks[facing=west]";
    private static final String STOREHOUSE = "kingdomworld:storehouse";
    private static final String AIR = Structure.AIR;

    private GothicCamp() {
    }

    /**
     * The camp buildings of the style: the barracks hall, the training yard, the store and the watch tower.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("gothic_camp_barracks", "defense", "barracks", "kingdomworld:barracks", 6, 0, 10,
                barracks(), "barracks", 1, "").params("\"bunks\": 12").at("door", 6, 0, 10));
        list.add(new BuildingGenerator.Building("gothic_camp_yard", "defense", "yard", "minecraft:target", 6, 1, 10,
                yard(), "none", 1, "").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("gothic_camp_store", "farming", "warehouse", "kingdomworld:storehouse", 6, 1, 10,
                store(), "warehouse", 1, "").params("\"containers\": 12").at("door", 6, 1, 10));
        list.add(new BuildingGenerator.Building("gothic_camp_tower", "defense", "tower", "minecraft:oak_fence", 3, 0, 6,
                tower(), "tower", 1, "").at("door", 3, 0, 6));
        return list;
    }

    // ---------------------------------------------------------------- shared shapes

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    /**
     * A soul lantern on a bracket of black stone before a wall looking the way of {@code facing}: the bracket in the cell
     * (x, y, z) before the wall, the lantern hanging under it.
     */
    private static void bracketLamp(Structure s, int x, int y, int z, String facing) {
        put(s, x, y, z, Kit.upside(Gothic.GARGOYLE_STAIRS, Furnish.opposite(facing)));
        put(s, x, y - 1, z, Gothic.SOUL_LANTERN_HANGING);
    }

    /**
     * A lamp post of the camp standing on layer {@code y}: a black stone post two layers high with a soul lantern on it.
     */
    private static void lampPost(Structure s, int x, int y, int z) {
        put(s, x, y, z, Gothic.STAND);
        put(s, x, y + 1, z, Gothic.STAND);
        put(s, x, y + 2, z, Gothic.SOUL_LANTERN);
    }

    /**
     * A palisade of dark stakes along a straight line from (x1, z1) to (x2, z2), standing on layer 1: every second stake
     * {@code height + 1} logs high and tipped with an iron spike, the others {@code height} high with a timber point, so
     * that its top is jagged.
     */
    private static void palisade(Structure s, int x1, int z1, int x2, int z2, int height) {
        boolean alongX = z1 == z2;
        int a1 = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        int a2 = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        for (int a = a1; a <= a2; a++) {
            int x = alongX ? a : x1;
            int z = alongX ? z1 : a;
            boolean tall = (a - a1) % 2 == 0;
            int top = tall ? height + 1 : height;
            for (int y = 1; y <= top; y++) {
                put(s, x, y, z, Kit.log(BEAM, "y"));
            }
            put(s, x, top + 1, z, tall ? IRON : WOOD_FENCE);
        }
    }

    // ---------------------------------------------------------------- the barracks hall

    /**
     * The barracks hall of the camp: a long hall of dark timber that sleeps twelve, its gables of stone to the street and
     * the back, under a roof of shingles as steep as a tent. The long walls are a frame of posts with panels of plaster
     * and a band of windows under the eaves, steadied by stone buttresses; the front gable has a pointed doorway under a
     * red transom, a rose window with a blind lancet over it, the coping and the pinnacles of the style, two long banners
     * and soul lanterns on brackets; the back gable a tall lancet between two more banners. An iron cresting runs along the ridge and a small belfry with a bell carries
     * a needle spire with an iron cross. Inside, open to the rafters over tie beams: six bunks along each wall, a
     * chimneypiece with its fire at the back between two weapon racks, racks under banners between the bunks, a long
     * table with benches and candles under a corona, the barracks block and the chest of the company by the door.
     */
    static Structure barracks() {
        Structure s = new Structure(13, 26, 11);
        int x1 = 1;
        int x2 = 11;
        int z1 = 1;
        int z2 = 9;
        int eaves = 4;
        // The footing, the floor and the step.
        s.fill(x1, 0, z1, x2, 0, z2, ROUGH);
        s.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, PLANKS);
        s.set(6, 0, 10, Kit.stairs(TRIM_STAIRS, "north"));
        // The plinth, the timber long walls (posts, panels of plaster, a band of windows) and the stone gable walls.
        s.walls(x1, 1, z1, x2, 1, z2, ROUGH);
        for (int x : new int[]{x1, x2}) {
            for (int z = z1; z <= z2; z++) {
                boolean post = (z - z1) % 2 == 0;
                s.set(x, 2, z, post ? Kit.log(BEAM, "y") : PLASTER);
                s.set(x, 3, z, post ? Kit.log(BEAM, "y") : GLASS);
            }
        }
        s.fill(x1, 2, z1, x2, eaves - 1, z1, WALL);
        s.fill(x1, 2, z2, x2, eaves - 1, z2, WALL);
        // The roof as steep as a tent, its stone gables with their coping and pinnacles, the iron cresting.
        int ridge = Gothic.steepRoof(s, x1, z1, x2, z2, eaves, false, 2, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.cresting(s, 6, z1 + 1, 6, z2 - 1, ridge);
        // Buttresses against the posts of the long walls and at the corners.
        for (int z : new int[]{3, 5, 7}) {
            Gothic.buttress(s, x1, 0, z, "west", eaves, 1, false);
            Gothic.buttress(s, x2, 0, z, "east", eaves, 1, false);
        }
        for (int x : new int[]{x1, x2}) {
            Gothic.buttress(s, x, 0, z2, "south", eaves + 1, 1, false);
            Gothic.buttress(s, x, 0, z1, "north", eaves + 1, 1, false);
        }
        // The front: the pointed doorway, the rose window, the banners and the lamps.
        Gothic.portal(s, 6, 1, z2, "south", 1, false);
        Gothic.rose(s, 6, 8, z2, "south", 5);
        Gothic.blindLancet(s, 6, 12, z2, "south", 1, 1);
        s.set(3, 6, z2 + 1, Kit.wallBanner("red", "south")).set(9, 6, z2 + 1, Kit.wallBanner("red", "south"));
        bracketLamp(s, 4, 3, z2 + 1, "south");
        bracketLamp(s, 8, 3, z2 + 1, "south");
        // The back: a tall lancet over the chimneypiece between two banners.
        Gothic.lancet(s, 6, 7, z1, "north", 1, 3, GLASS, VIOLET);
        s.set(3, 6, z1 - 1, Kit.wallBanner("red", "north")).set(9, 6, z1 - 1, Kit.wallBanner("red", "north"));
        // The belfry on the ridge with its bell and needle spire.
        belfry(s, 6, ridge, 5);
        // Inside: tie beams with lanterns under them, the corona over the table.
        for (int z : new int[]{3, 7}) {
            s.fill(x1 + 1, eaves, z, x2 - 1, eaves, z, Kit.log(BEAM, "x"));
            s.set(4, eaves - 1, z, Kit.hangingLantern()).set(8, eaves - 1, z, Kit.hangingLantern());
        }
        Gothic.corona(s, 6, 5, ridge - 3, ridge - 3 - eaves);
        // Six bunks along each long wall.
        for (int z : new int[]{2, 3, 4, 6, 7, 8}) {
            Furnish.bed(s, 3, 1, z, "west", "red");
            Furnish.bed(s, 9, 1, z, "east", "red");
        }
        // The chimneypiece at the back between two weapon racks, the firewood beside the grate.
        Gothic.fireplace(s, 6, 1, z1, "south", 6);
        Furnish.weaponRack(s, 4, 1, 2, "south", WOOD);
        Furnish.weaponRack(s, 8, 1, 2, "south", WOOD);
        s.set(7, 1, 3, Kit.log(BEAM, "z"));
        // Between the bunks: a rack under a banner on each side.
        Furnish.weaponRack(s, 2, 1, 5, "east", WOOD);
        Furnish.weaponRack(s, 10, 1, 5, "west", WOOD);
        s.set(2, 3, 5, Kit.wallBanner("red", "east")).set(10, 3, 5, Kit.wallBanner("red", "west"));
        // The long table with its benches and candles.
        Furnish.longTable(s, 6, 5, 6, 6, 1, WOOD);
        Furnish.bench(s, 5, 5, 5, 6, 1, "east", WOOD);
        Furnish.bench(s, 7, 5, 7, 6, 1, "west", WOOD);
        s.set(6, 2, 5, Furnish.candles(2)).set(6, 2, 6, Furnish.candles(3));
        // By the door: the barracks block and the chest of the company.
        s.set(8, 1, 8, BARRACKS);
        s.set(4, 1, 8, Kit.chest("east"));
        s.connect();
        return s;
    }

    /**
     * A small belfry on the ridge of a roof at layer {@code ridge} around (cx, cz): a floor of timber over the ridge, four
     * posts with a bell hanging between them, a little tented roof of the spire and a needle with an iron cross over it.
     */
    private static void belfry(Structure s, int cx, int ridge, int cz) {
        s.fill(cx - 1, ridge - 1, cz - 1, cx + 1, ridge - 1, cz + 1, ROOF.block());
        s.fill(cx - 1, ridge, cz - 1, cx + 1, ridge, cz + 1, PLANKS);
        s.fill(cx - 1, ridge + 1, cz - 1, cx + 1, ridge + 2, cz + 1, AIR);
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                s.fill(cx + dx, ridge + 1, cz + dz, cx + dx, ridge + 2, cz + dz, WOOD_FENCE);
            }
        }
        s.set(cx, ridge + 2, cz, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        int top = Kit.spire(s, cx - 1, cz - 1, cx + 1, cz + 1, ridge + 3, SPIRE);
        int y = top - 1;
        s.set(cx, y, cz, SPIRE.block());
        s.set(cx, y + 1, cz, SPIRE.block());
        s.set(cx, y + 2, cz, IRON);
        s.set(cx, y + 3, cz, IRON).set(cx - 1, y + 3, cz, IRON).set(cx + 1, y + 3, cz, IRON);
        s.set(cx, y + 4, cz, IRON);
    }

    // ---------------------------------------------------------------- the training yard

    /**
     * The training yard of the camp, level with the ground: a floor of beaten earth with gravel before the butts and in
     * the lane from the gate and a square of checkered flags for the sword drill, closed at the back and the sides by a
     * palisade of dark stakes tipped with iron and at the front by an iron railing on stone posts with soul lanterns,
     * its gate between two stone posts that carry the banners of the kingdom. Three archery butts stand at the back in
     * timber frames under small pointed hoods, soul lanterns on the outer two; two dummies in tabards flank the square,
     * in its middle a pell with a lantern on it; the weapon corner has its racks, a barrel and a lantern under a lean-to,
     * the other front corner a bench and a water tub, and shields hang on the palisade.
     */
    static Structure yard() {
        Structure s = new Structure(13, 7, 11);
        // The ground: beaten earth, gravel before the butts and in the lane, the checkered square of the sword drill.
        s.fill(0, 0, 0, 12, 0, 10, "minecraft:coarse_dirt");
        s.fill(1, 0, 2, 11, 0, 2, "minecraft:gravel");
        s.fill(5, 0, 7, 7, 0, 10, "minecraft:gravel");
        Gothic.paving(s, 5, 4, 7, 6, 0);
        // The palisade at the back and the sides, stone posts with lanterns at the back corners.
        palisade(s, 1, 0, 11, 0, 2);
        palisade(s, 0, 1, 0, 9, 1);
        palisade(s, 12, 1, 12, 9, 1);
        for (int x : new int[]{0, 12}) {
            s.set(x, 1, 0, ROUGH).fill(x, 2, 0, x, 3, 0, WALL).set(x, 4, 0, CARVED).set(x, 5, 0, Gothic.SOUL_LANTERN);
            s.set(x, 3, 5, Gothic.SOUL_LANTERN);
        }
        // The front: an iron railing on stone posts and the gate between two posts with banners.
        Gothic.ironFence(s, 0, 10, 4, 10, 1, 2, true);
        Gothic.ironFence(s, 8, 10, 12, 10, 1, 2, true);
        for (int x : new int[]{4, 8}) {
            s.set(x, 1, 10, ROUGH).set(x, 2, 10, WALL).set(x, 3, 10, CARVED).set(x, 4, 10, Kit.banner("red", 0));
        }
        // Three archery butts under pointed hoods at the back, soul lanterns on the outer two.
        for (int x : new int[]{3, 6, 9}) {
            s.set(x, 1, 1, HAY).set(x, 2, 1, TARGET);
            for (int side = -1; side <= 1; side += 2) {
                s.fill(x + side, 1, 1, x + side, 2, 1, WOOD_FENCE);
                s.set(x + side, 3, 1, Kit.stairs(ROOF.stairs(), side < 0 ? "east" : "west"));
            }
            s.set(x, 3, 1, ROOF.block()).set(x, 4, 1, x == 6 ? Kit.slab(ROOF.slab()) : Gothic.SOUL_LANTERN);
        }
        // Two dummies in tabards and the pell with its lantern in the middle of the square.
        for (int x : new int[]{3, 9}) {
            s.set(x, 1, 5, WOOD_FENCE).set(x, 2, 5, HAY).set(x, 3, 5, "minecraft:carved_pumpkin[facing=south]");
            s.set(x - 1, 2, 5, WOOD_FENCE).set(x + 1, 2, 5, WOOD_FENCE);
            s.set(x, 2, 6, Kit.wallBanner("red", "south"));
        }
        s.fill(6, 1, 5, 6, 2, 5, Kit.log(BEAM, "y")).set(6, 3, 5, Gothic.SOUL_LANTERN);
        // The weapon corner under a lean-to: racks, the barrel of arms, spears.
        s.fill(2, 1, 7, 2, 2, 7, Kit.log(BEAM, "y")).fill(2, 1, 9, 2, 2, 9, Kit.log(BEAM, "y"));
        s.fill(2, 3, 7, 2, 3, 9, Kit.stairs(ROOF.stairs(), "west"));
        s.fill(1, 3, 7, 1, 3, 9, ROOF.block());
        s.fill(1, 4, 7, 1, 4, 9, Kit.stairs(ROOF.stairs(), "west"));
        Furnish.weaponRack(s, 1, 1, 7, "east", WOOD);
        Furnish.weaponRack(s, 1, 1, 8, "east", WOOD);
        s.set(1, 1, 9, Kit.barrel());
        s.set(1, 2, 9, WOOD_FENCE);
        s.set(1, 2, 8, Gothic.SOUL_LANTERN_HANGING);
        // The other corner: a bench, a water tub, hay.
        Furnish.bench(s, 10, 9, 11, 9, 1, "north", WOOD);
        s.set(11, 1, 7, "minecraft:water_cauldron[level=3]");
        s.set(11, 1, 1, HAY).set(11, 2, 1, HAY).set(1, 1, 1, HAY);
        // Shields hanging on the palisade.
        s.set(1, 2, 3, Kit.wallBanner("red", "east"));
        s.set(11, 2, 3, Kit.wallBanner("red", "west"));
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the store

    /**
     * The store of the army: a long barn of dark timber boards between posts on a plinth of rough stone, under a roof of
     * shingles as steep as a tent with an iron cresting, overhanging its two ends; the ends are of stone below, held by
     * buttresses at the corners and lit by a small lancet, and of boards in the gable, where a hay door opens to the loft
     * under a hoist beam with a soul lantern. From the middle of its front a stone porch with its own gable, coping and
     * pinnacles stands over the cart road, open through a great pointed arch under the banner of the kingdom, with soul
     * lanterns on brackets beside it. Inside, a threshing floor of stone runs from the arch to the back wall; along the
     * walls barrels in stacks and chests, the two storehouse blocks, hay and sacks; lofts at both ends under the roof
     * with hay and sacks, reached by ladders, lanterns under and over them and a corona over the floor. Outside under the
     * eaves more hay, sacks and crates.
     */
    static Structure store() {
        Structure s = new Structure(13, 17, 11);
        int x1 = 1;
        int x2 = 11;
        int z1 = 1;
        int z2 = 7;
        int eaves = 5;
        // The floor (layer 0 lies in the ground): boards, and the threshing floor and the cart road of rough stone.
        s.fill(x1, 0, z1, x2, 0, z2, ROUGH);
        s.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, PLANKS);
        s.fill(5, 0, 2, 7, 0, 10, ROUGH);
        s.fill(4, 0, 8, 8, 0, 9, ROUGH);
        // The long walls of boards between posts, the stone gable ends.
        s.walls(x1, 1, z1, x2, 1, z2, ROUGH);
        for (int z : new int[]{z1, z2}) {
            for (int x = x1; x <= x2; x++) {
                boolean post = x == 4 || x == 8 || x == 6 && z == z1;
                s.fill(x, 2, z, x, eaves - 1, z, post ? Kit.log(BEAM, "y") : BOARDS);
            }
        }
        for (int x : new int[]{x1, x2}) {
            s.fill(x, 2, z1, x, eaves - 1, z2, WALL);
        }
        for (int x : new int[]{2, 10}) {
            s.set(x, 3, z1, GLASS).set(x, 3, z2, GLASS);
        }
        // The porch: stone walls and its pointed cart arch; the opening into the barn behind it.
        for (int x : new int[]{4, 8}) {
            s.fill(x, 1, 8, x, 1, 9, ROUGH).fill(x, 2, 8, x, eaves - 1, 9, WALL);
        }
        s.set(5, 1, 9, ROUGH).set(6, 1, 9, ROUGH).set(7, 1, 9, ROUGH);
        s.fill(5, 2, 9, 7, eaves - 1, 9, WALL);
        s.fill(5, 1, z2, 7, eaves - 1, z2, AIR);
        // The roofs: the barn's along its length, the porch's from the front, joined in a valley.
        int ridge = Gothic.steepRoof(s, x1, z1, x2, z2, eaves, true, 2, Gothic.PLAIN, Gothic.PLAIN);
        Gothic.cresting(s, x1, 4, x2, 4, ridge);
        // The gables under the overhang of the roof are of boards round a hay door, a hoist beam and a lantern over it.
        for (int x : new int[]{x1, x2}) {
            int out = x == x1 ? x - 1 : x + 1;
            for (int y = eaves; y < ridge; y++) {
                for (int z = z1; z <= z2; z++) {
                    if (s.get(x, y, z).equals(WALL)) {
                        s.set(x, y, z, BOARDS);
                    }
                }
            }
            s.fill(x, eaves, 4, x, eaves, 4, Kit.log(BEAM, "y"));
            s.door(x, eaves + 1, 4, DOOR, x == x1 ? "west" : "east", "left");
            s.set(x, eaves + 3, 4, Kit.log(BEAM, "y"));
            s.set(x, eaves + 4, 4, Kit.log(BEAM, "x")).set(out, eaves + 4, 4, Kit.log(BEAM, "x"));
            s.set(out, eaves + 3, 4, Gothic.SOUL_LANTERN_HANGING);
            s.set(x, eaves + 5, 4, Kit.log(BEAM, "y"));
        }
        Gothic.steepRoof(s, 4, 8, 8, 9, eaves, false, 2, Gothic.OPEN, Gothic.PARAPET);
        porchValley(s, eaves);
        s.fill(5, eaves, 8, 7, eaves, 8, AIR);
        Gothic.pointedArch(s, 6, 1, 9, "south", 3, 3, 1, TRIM_STAIRS, TRIM_SLAB);
        s.set(6, 7, 10, Kit.wallBanner("red", "south"));
        bracketLamp(s, 3, 4, 8, "south");
        bracketLamp(s, 9, 4, 8, "south");
        // Buttresses at the corners of the stone ends and a lancet low in each end.
        for (int z : new int[]{z1, z2}) {
            Gothic.buttress(s, x1, 0, z, "west", eaves, 1, false);
            Gothic.buttress(s, x2, 0, z, "east", eaves, 1, false);
        }
        Gothic.lancet(s, x1, 2, 4, "west", 1, 1, GLASS, VIOLET);
        Gothic.lancet(s, x2, 2, 4, "east", 1, 1, GLASS, VIOLET);
        // The lofts at both ends with their ladders and rails.
        for (int side = -1; side <= 1; side += 2) {
            int inner = 6 + 2 * side;
            int outer = 6 + 4 * side;
            s.fill(Math.min(inner, outer), eaves, z1 + 1, Math.max(inner, outer), eaves, z2 - 1, PLANKS);
            for (int y = 1; y <= eaves; y++) {
                s.set(inner, y, z2 - 1, Kit.ladder("north"));
            }
            s.fill(inner, eaves + 1, z1 + 1, inner, eaves + 1, z2 - 2, WOOD_FENCE);
            s.fill(inner, 1, 4, inner, eaves - 1, 4, Kit.log(BEAM, "y"));
            s.set(6 + 3 * side, eaves - 1, 3, Kit.hangingLantern());
            s.set(6 + 3 * side, eaves - 1, 5, Kit.hangingLantern());
            s.fill(6 + 3 * side, 9, 4, 6 + 3 * side, 10, 4, Kit.chain()).set(6 + 3 * side, 8, 4, Kit.hangingLantern());
        }
        // Hay on the west loft, sacks and crates on the east one.
        s.fill(2, eaves + 1, 2, 3, eaves + 1, 3, HAY).fill(2, eaves + 1, 5, 3, eaves + 1, 6, HAY);
        s.set(2, eaves + 2, 3, HAY).set(2, eaves + 2, 5, HAY);
        s.fill(9, eaves + 1, 2, 10, eaves + 1, 3, "minecraft:white_wool").fill(10, eaves + 1, 5, 10, eaves + 1, 6, "minecraft:brown_wool");
        s.set(10, eaves + 2, 3, "minecraft:white_wool");
        Furnish.crate(s, 9, eaves + 1, 6, WOOD);
        // The storehouse blocks and the stores along the walls.
        s.set(4, 1, 2, STOREHOUSE).set(8, 1, 2, STOREHOUSE);
        for (int side = -1; side <= 1; side += 2) {
            int wall = 6 + 4 * side;
            String toRoom = side < 0 ? "east" : "west";
            s.set(wall, 1, 2, Kit.barrel()).set(wall, 2, 2, Kit.barrel());
            s.set(6 + 3 * side, 1, 2, Kit.chest("south"));
            s.set(wall, 1, 3, Kit.chest(toRoom));
            s.set(wall, 1, 4, Kit.barrel()).set(wall, 2, 4, Kit.barrel());
        }
        s.set(5, 1, 2, Kit.barrel()).set(7, 1, 2, Kit.barrel());
        s.set(6, 1, 2, HAY).set(6, 2, 2, HAY);
        s.set(2, 1, 5, HAY).set(2, 1, 6, HAY).set(2, 2, 6, HAY).set(3, 1, 6, HAY);
        Furnish.sack(s, 10, 1, 5, true);
        Furnish.sack(s, 10, 1, 6, false);
        Furnish.sack(s, 10, 2, 6, true);
        Furnish.crate(s, 9, 1, 6, WOOD);
        // The corona over the threshing floor.
        Gothic.corona(s, 6, 4, ridge - 3, ridge - 3 - (eaves - 1));
        // Outside under the eaves: hay, sacks, crates.
        s.set(2, 1, 8, HAY).set(3, 1, 8, HAY).set(2, 2, 8, HAY).set(2, 1, 9, HAY);
        Furnish.crate(s, 10, 1, 8, WOOD);
        Furnish.crate(s, 10, 2, 8, WOOD);
        Furnish.sack(s, 9, 1, 8, false);
        Furnish.sack(s, 10, 1, 9, true);
        keepGround(s);
        s.connect();
        return s;
    }

    /**
     * Carries the roof of the porch back into the slope of the barn's roof: where the porch roof stands above the barn's
     * on rows 5 to 7, its stairs, its full blocks and its ridge slab are drawn, so that the two roofs meet in a valley.
     */
    private static void porchValley(Structure s, int eaves) {
        for (int z = 5; z <= 7; z++) {
            int barn = eaves + 2 * (8 - z);
            for (int x = 3; x <= 9; x++) {
                int r = 3 - Math.abs(x - 6);
                int h = eaves + 2 * r;
                if (h < barn) {
                    continue;
                }
                if (r > 0) {
                    s.fill(x, h - 2, z, x, h - 1, z, ROOF.block());
                }
                s.set(x, h, z, x == 6 ? Kit.slab(ROOF.slab()) : Kit.stairs(ROOF.stairs(), x < 6 ? "east" : "west"));
            }
        }
    }

    /**
     * Leaves the empty positions of layer 0 out, so that the ground of the place stays around a building whose layer 0
     * lies in the ground.
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
     * The watch tower of the camp: a slender square tower of stone on a plinth of rough stone, its corners held by pairs
     * of stepped buttresses, its faces pierced by lancets that glow purple, a pointed doorway under a long banner. A ladder
     * climbs the inside, lit by torches, to the watch: an open arcade of pointed arches on four sides over an iron rail,
     * under a cornice, with lanterns and the alarm bell hanging from its vault; over it a needle spire with lucarnes, great
     * pinnacles on its four corners and an iron cross on its point.
     */
    static Structure tower() {
        Structure s = new Structure(7, 33, 7);
        int floor = 12;
        // The footing, the floor, the step.
        s.fill(1, 0, 1, 5, 0, 5, ROUGH);
        s.fill(2, 0, 2, 4, 0, 4, PLANKS);
        s.set(3, 0, 6, Kit.stairs(TRIM_STAIRS, "north"));
        // The shaft.
        s.walls(1, 1, 1, 5, 1, 5, ROUGH);
        s.walls(1, 2, 1, 5, floor - 1, 5, WALL);
        s.fill(2, floor, 2, 4, floor, 4, PLANKS);
        s.walls(1, floor, 1, 5, floor, 5, TRIM);
        // Buttresses in pairs at the corners.
        for (int x : new int[]{1, 5}) {
            Gothic.buttress(s, x, 0, 5, "south", 9, 1, false);
            Gothic.buttress(s, x, 0, 1, "north", 9, 1, false);
        }
        for (int z : new int[]{1, 5}) {
            Gothic.buttress(s, 1, 0, z, "west", 9, 1, false);
            Gothic.buttress(s, 5, 0, z, "east", 9, 1, false);
        }
        // The doorway and the banner over it; lancets on the other faces.
        Gothic.portal(s, 3, 1, 5, "south", 1, false);
        s.set(3, 7, 6, Kit.wallBanner("red", "south"));
        Gothic.lancet(s, 1, 6, 3, "west", 1, 2, GLASS, VIOLET);
        Gothic.lancet(s, 5, 6, 3, "east", 1, 2, GLASS, VIOLET);
        Gothic.lancet(s, 3, 6, 1, "north", 1, 2, GLASS, VIOLET);
        // The ladder and the torches inside.
        for (int y = 1; y <= floor; y++) {
            s.set(2, y, 2, Kit.ladder("south"));
        }
        s.set(2, 3, 4, Kit.wallTorch("east")).set(4, 3, 4, Kit.wallTorch("west"));
        s.set(4, 9, 4, Kit.wallTorch("west")).set(2, 10, 3, Kit.wallTorch("east"));
        // The cornice under the watch.
        Gothic.cornice(s, 2, 0, 4, 0, floor - 1, "north");
        Gothic.cornice(s, 2, 6, 4, 6, floor - 1, "south");
        Gothic.cornice(s, 0, 2, 0, 4, floor - 1, "west");
        Gothic.cornice(s, 6, 2, 6, 4, floor - 1, "east");
        // The watch: an arcade of pointed arches on four sides over an iron rail.
        int top = floor + 4;
        s.walls(1, floor + 1, 1, 5, top, 5, WALL);
        Gothic.pointedArch(s, 3, floor + 1, 5, "south", 3, 2, 1, TRIM_STAIRS, TRIM_SLAB);
        Gothic.pointedArch(s, 3, floor + 1, 1, "north", 3, 2, 1, TRIM_STAIRS, TRIM_SLAB);
        Gothic.pointedArch(s, 1, floor + 1, 3, "west", 3, 2, 1, TRIM_STAIRS, TRIM_SLAB);
        Gothic.pointedArch(s, 5, floor + 1, 3, "east", 3, 2, 1, TRIM_STAIRS, TRIM_SLAB);
        s.fill(2, floor + 1, 1, 4, floor + 1, 1, IRON).fill(2, floor + 1, 5, 4, floor + 1, 5, IRON);
        s.fill(1, floor + 1, 2, 1, floor + 1, 4, IRON).fill(5, floor + 1, 2, 5, floor + 1, 4, IRON);
        Gothic.cornice(s, 2, 0, 4, 0, top, "north");
        Gothic.cornice(s, 2, 6, 4, 6, top, "south");
        Gothic.cornice(s, 0, 2, 0, 4, top, "west");
        Gothic.cornice(s, 6, 2, 6, 4, top, "east");
        // The needle spire, its corner pinnacles, the bell and the lanterns under it.
        Gothic.needleSpire(s, 3, top + 1, 3, 2, 15);
        for (int x : new int[]{1, 5}) {
            for (int z : new int[]{1, 5}) {
                Gothic.greatPinnacle(s, x, top + 1, z, 5);
            }
        }
        s.set(3, top + 1, 3, SPIRE.block());
        s.set(3, top, 3, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        s.set(2, top, 2, Kit.hangingLantern()).set(4, top, 4, Kit.hangingLantern());
        s.trimSky(2);
        s.connect();
        return s;
    }
}
