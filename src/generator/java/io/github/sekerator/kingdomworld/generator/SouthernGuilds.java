package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The two guilds of a capital in the style "southern", each a program drawn for the rank of its kingdom, from B for a weak
 * one to SS for a strong one; the higher the rank, the bigger, the higher and the richer the house.
 *
 * <p>The adventurers' guild is a guildhall like the town hall of a southern town: a whitewashed house on a footing of dressed
 * stone under a low roof of clay tiles, its ground floor opening to the square in an arcaded loggia, the hall of the guild
 * behind and above it, and a clock and a bell over it. B is a small hall with a bell gable over its front; A has a clock
 * tower rising from the middle of its front; S a lofty hall with a loggia of great arches beside a domed campanile and a
 * garden; SS a palazzo of three storeys with a loggia under its eaves, a court with arcaded galleries, a great hall across
 * the back and a tall tower crowned with machicolations and a belfry. Inside every rank: the great hall with tables and
 * benches, the hearth under its hood of plaster, the contract board, the counter of the registrar with the guild desk, his
 * post and the one chest of the guild, trophies, and upstairs the map room with its lectern and the rooms of the masters,
 * who sleep in beds of state that are no homes.
 *
 * <p>The mages' guild lives in its tower: a round white tower with lesenes of stone, arched windows of purple glass, a
 * frieze of glazed tiles under a terrace on corbels for watching the stars and a dome of blue glazed tiles over a drum.
 * B has three storeys, A four and a porch, S five and an annex with the library and the brewing room, SS six beside a domed
 * library hall and a garden court. On its ground floor the magic focus with the post of the mage and its chest; above the
 * library, the brewing room, the research room and real beds for the mages (one in B, two in A, three in S, four in SS).
 *
 * <p>Drawn with the shapes of {@link Southern}; coordinates as everywhere: x from west to east, z from the back to the front
 * (the entrance looks south), y up; layer 0 is the footing and the paving of the plot, one walks at layer 1.
 */
final class SouthernGuilds {
    /** The rank of a guild: B for a weak kingdom, A, S, SS for the strongest. */
    enum Rank {
        B, A, S, SS
    }

    /**
     * A drawn building with its named places: the door, and the guard by the desk (null for a mage tower).
     */
    private record Drawn(Structure structure, int[] door, int[] guard) {
    }

    private static final String AIR = Structure.AIR;
    private static final String RENDER = Southern.RENDER;
    private static final String STONE = Southern.STONE;
    private static final String STONE_STAIRS = Southern.STONE_STAIRS;
    private static final String PAVING = Southern.PAVING;
    private static final String PAVING_STAIRS = Southern.PAVING_STAIRS;
    private static final String TRIM = Southern.TRIM;
    private static final String TRIM_STAIRS = Southern.TRIM_STAIRS;
    private static final String TRIM_SLAB = Southern.TRIM_SLAB;
    private static final String CARVED = Southern.CARVED;
    private static final String COTTO = Southern.COTTO;
    private static final String AZULEJO = Southern.AZULEJO;
    private static final String AZULEJO_BLUE = Southern.AZULEJO_BLUE;
    private static final String IRON = Southern.IRON;
    private static final String BEAM = Southern.BEAM;
    private static final String PLANKS = Southern.PLANKS;
    private static final String PLANK_SLAB = Southern.PLANK_SLAB;
    private static final String PLANK_STAIRS = Southern.PLANK_STAIRS;
    private static final String FENCE = Southern.FENCE;
    private static final String DOOR = Southern.DOOR;
    private static final String WOOD = Southern.WOOD;
    private static final String POT = Southern.POT;

    private static final String DESK = "kingdomworld:guild_desk[facing=south]";
    private static final String WORK_POST = "kingdomworld:work_post";
    private static final String FOCUS = "kingdomworld:magic_focus";
    private static final String CARTOGRAPHY = "minecraft:cartography_table";
    private static final String ENCHANTING = "minecraft:enchanting_table";
    private static final String BREWING = "minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]";
    private static final String CAULDRON = "minecraft:water_cauldron[level=3]";
    private static final String AMETHYST = "minecraft:amethyst_block";
    private static final String CLUSTER = "minecraft:amethyst_cluster[facing=up,waterlogged=false]";
    private static final String GOLD = "minecraft:gold_block";
    private static final String LAPIS = "minecraft:lapis_block";
    /** The guild's own colour on its banners, the same in every kingdom. */
    private static final String GUILD = "black";
    /** The glass of the windows of the mages. */
    private static final String PURPLE = Kit.stainedPane("purple");
    /** The dome of a mage tower: tiles glazed blue and green, a fixed accent that every look keeps. */
    private static final Kit.Roof MAJOLICA = new Kit.Roof("minecraft:prismarine_bricks", "minecraft:prismarine_brick_stairs",
            "minecraft:prismarine_brick_slab");

    private SouthernGuilds() {
    }

    /**
     * The adventurers' guilds southern_guild_b, _a, _s and _ss (trade, type guild, with their door and the guard by the desk)
     * and the mage towers southern_mage_tower_b, _a, _s and _ss (production, role mage_tower, with their door), all standing
     * on the ground with their anchor in the middle of their front edge.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Rank rank : Rank.values()) {
            String key = rank.name().toLowerCase(Locale.ROOT);
            Drawn guild = guild(rank);
            Structure g = guild.structure();
            list.add(new BuildingGenerator.Building("southern_guild_" + key, "trade", "guild", "kingdomworld:guild_desk",
                    (g.sizeX - 1) / 2, 0, g.sizeZ - 1, g, "none", 1, "")
                    .at("door", guild.door()[0], guild.door()[1], guild.door()[2])
                    .at("guard", guild.guard()[0], guild.guard()[1], guild.guard()[2]));
        }
        for (Rank rank : Rank.values()) {
            String key = rank.name().toLowerCase(Locale.ROOT);
            Drawn tower = mageTower(rank);
            Structure t = tower.structure();
            list.add(new BuildingGenerator.Building("southern_mage_tower_" + key, "production", "mage_tower", "kingdomworld:magic_focus",
                    (t.sizeX - 1) / 2, 0, t.sizeZ - 1, t, "mage_tower", 1, "")
                    .at("door", tower.door()[0], tower.door()[1], tower.door()[2]));
        }
        return list;
    }

    // ================================================================ the adventurers' guild

    /**
     * The adventurers' guild of a rank.
     */
    static Drawn guild(Rank rank) {
        return switch (rank) {
            case B -> guildB();
            case A -> guildA();
            case S -> guildS();
            case SS -> guildSS();
        };
    }

    /**
     * Rank A: a guildhall of two storeys with a loggia of three arches on the square and a clock tower rising from the
     * middle of its front, in the plane of the front over the middle arch: stone pilasters run up from the columns, a
     * balcony on corbels before the door of the tower room, the clock dial of blue and gold over the eaves, an open belfry
     * with its bell and a pyramid of tiles with a finial. Inside: the great hall with two long tables, the hearth, the
     * counter and the contract board beside the door; upstairs the map room, the master's chamber with a bed of state and a
     * hall of trophies round the tower room, from which a ladder climbs to the belfry.
     */
    private static Drawn guildA() {
        Structure s = new Structure(17, 30, 19);
        plot(s);
        int x1 = 1;
        int x2 = 15;
        int z1 = 3;
        int z2 = 15;
        int ceil = 6;
        int top = 11;
        int up = ceil + 1;
        footing(s, x1, z1, x2, z2);
        Southern.walls(s, x1, z1, x2, z2, 1, top);
        Southern.band(s, x1, z1, x2, z2, ceil);
        s.fill(x1 + 1, 1, 12, x2 - 1, ceil - 1, 12, RENDER);
        Southern.beamCeiling(s, x1 + 1, z1 + 1, x2 - 1, 12, ceil, false);
        Southern.beamCeiling(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, top, false);
        Southern.loggia(s, 2, 13, 14, z2, 1, 5, 3, "south", false);
        entrance(s, 8, 1, 12, "south");
        contractBoard(s, 4, 1, 12, "south", 3, true);
        Southern.stoneBench(s, 11, 13, 13, 13, 1, "south");
        Southern.amphora(s, 2, 1, 13);
        Southern.planter(s, 14, 1, 13, true);

        // the roof first: the tower stands through it
        Southern.lowHipRoof(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, top + 1);
        s.fill(6, top, z2 + 1, 10, top + 1, z2 + 1, AIR);
        // the clock tower: pilasters from the columns, the walls of the tower room and of the shaft over the roof
        int ttop = 17;
        for (int y = up; y <= ttop; y++) {
            for (int z = 11; z <= 15; z++) {
                for (int x = 6; x <= 10; x++) {
                    boolean edge = x == 6 || x == 10 || z == 11 || z == 15;
                    boolean corner = (x == 6 || x == 10) && (z == 11 || z == 15);
                    if (edge) {
                        put(s, x, y, z, y == ttop ? TRIM : corner ? STONE : RENDER);
                    } else if (y > top) {
                        put(s, x, y, z, y == ttop ? PLANKS : AIR);
                    }
                }
            }
        }
        s.door(8, up, 11, DOOR, "south", "left");
        Southern.cornice(s, 5, 10, 11, 16, ttop, TRIM_STAIRS);
        dial(s, 8, 15, 15, "south");
        s.fill(6, 14, 13, 6, 15, 13, Kit.GLASS);
        s.fill(10, 14, 13, 10, 15, 13, Kit.GLASS);
        s.fill(7, up, 12, 7, ttop, 12, Kit.ladder("south"));
        int eaves = Southern.belfry(s, 6, 11, ttop + 1, 5);
        Southern.finial(s, 8, Southern.pyramid(s, 5, 10, 11, 16, eaves, Southern.TILES), 13);
        Furnish.chandelier(s, 9, 13, top - 1, 1);
        put(s, 9, top + 3, 14, Kit.hangingLantern());
        put(s, 9, top + 4, 14, Kit.chain());

        // the front: the balcony of the tower room, the windows with shutters
        Southern.balcony(s, 8, up, z2, "south", 3, 1);
        for (int y = up; y <= up + 1; y++) {
            put(s, 6, y, z2 + 1, AIR);
            put(s, 10, y, z2 + 1, AIR);
        }
        Southern.window(s, 4, up + 1, z2, "south", 1, 2, true, true);
        Southern.window(s, 12, up + 1, z2, "south", 1, 2, true, true);
        Southern.window(s, x1, up + 1, 5, "west", 1, 2, true, false);
        Southern.window(s, x1, up + 1, 11, "west", 1, 2, true, true);
        Southern.window(s, x2, up + 1, 5, "east", 1, 2, true, false);
        Southern.window(s, x2, up + 1, 11, "east", 1, 2, true, true);
        Southern.window(s, 6, up + 1, z1, "north", 1, 2, true, false);
        Southern.window(s, x1, 2, 5, "west", 1, 2, true, false, IRON);
        Southern.window(s, x1, 2, 11, "west", 1, 2, true, false, IRON);
        Southern.window(s, x2, 2, 5, "east", 1, 2, true, false, IRON);
        Southern.window(s, 8, 2, z1, "north", 1, 2, true, false, IRON);

        // the great hall
        registrar(s, 8, 1, 6, 1);
        Southern.hearth(s, 2, 1, 8, "east", top + 1);
        feastTable(s, 5, 8, 5, 10, 1);
        feastTable(s, 11, 8, 11, 10, 1);
        stair(s, x2 - 1, 1, 11, "north", up - 1);
        trophies(s, 3, 1, 4, "south", 3);
        trophies(s, 11, 1, 4, "south", 3);
        Furnish.greatChandelier(s, 5, 9, ceil - 1, 1);
        Furnish.greatChandelier(s, 11, 9, ceil - 1, 1);
        Furnish.chandelier(s, 8, 9, ceil - 1, 1);
        Furnish.chandelier(s, 8, 5, ceil - 1, 1);
        lamp(s, 3, 4, 11);
        lamp(s, 13, 4, 5);

        // upstairs: the master's chamber and the map room behind, the hall of trophies in front round the tower room
        put(s, 13, up, 7, IRON);
        s.fill(x1 + 1, up, 8, x2 - 1, top - 1, 8, RENDER);
        s.fill(8, up, 4, 8, top - 1, 7, RENDER);
        doorway(s, 5, up, 8);
        doorway(s, 11, up, 8);
        doorway(s, 8, up, 6);
        Furnish.canopyBed(s, 4, up, 6, "north", WOOD);
        Furnish.desk(s, 7, up, 5, "west", WOOD);
        Furnish.rug(s, 3, 7, 6, 7, up, Kit.CLOTH_CARPET);
        Furnish.chandelier(s, 6, 6, top - 1, 1);
        Furnish.books(s, 9, 4, 13, 4, up, up);
        guildMap(s, 9, up + 1, 4);
        put(s, 12, up, 5, lectern("south"));
        mapTable(s, 10, up, 6);
        Furnish.chandelier(s, 11, 6, top - 1, 1);
        trophies(s, 6, up, 9, "south", 5);
        Furnish.longTable(s, 3, 11, 3, 13, up, WOOD);
        Furnish.bench(s, 2, 11, 2, 13, up, "east", WOOD);
        Furnish.bench(s, 4, 11, 4, 13, up, "west", WOOD);
        put(s, 3, up + 1, 12, Furnish.candles(3));
        Furnish.chandelier(s, 3, 12, top - 1, 1);
        Furnish.bench(s, 14, 11, 14, 13, up, "west", WOOD);
        Furnish.table(s, 12, up, 12, WOOD);
        Southern.amphora(s, 14, up, 10);
        Furnish.chandelier(s, 12, 11, top - 1, 1);
        lamp(s, 8, up + 2, 9);
        lamp(s, 3, up + 2, 9);
        lamp(s, 13, up + 2, 9);

        atticLight(s, 4, top + 1, 6);
        atticLight(s, 12, top + 1, 6);
        chimney(s, 2, top + 2, 8);
        // the forecourt and the garden behind
        Southern.lampPost(s, 0, 1, 16);
        Southern.lampPost(s, 16, 1, 16);
        Southern.citrus(s, 2, 1, 17, true);
        Southern.citrus(s, 14, 1, 17, true);
        Southern.cypress(s, 2, 1, 1, 9);
        Southern.cypress(s, 14, 1, 1, 9);
        Southern.cypress(s, 8, 1, 1, 8);
        Southern.wallLantern(s, 5, 3, z1 - 1);
        Southern.wallLantern(s, 11, 3, z1 - 1);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{8, 1, 12}, new int[]{10, 1, 6});
    }

    /**
     * Rank S: a guildhall with a lofty piano nobile over a loggia of two great round arches, and beside it on the square
     * a campanile of seven with clock dials on three faces under its belfry and a dome on a drum. The great hall behind
     * the loggia holds two long tables, the hearth, the counter and the trophies; a stair climbs to the master's chamber,
     * the map room and the council hall with its balcony over the middle column. A door from the hall leads into the
     * garden behind the campanile with its fountain, its pergola and citrus trees.
     */
    private static Drawn guildS() {
        Structure s = new Structure(23, 41, 25);
        plot(s);
        int x1 = 1;
        int x2 = 15;
        int z1 = 4;
        int z2 = 20;
        int ceil = 7;
        int top = 13;
        int up = ceil + 1;
        footing(s, x1, z1, x2, z2);
        Southern.walls(s, x1, z1, x2, z2, 1, top);
        Southern.band(s, x1, z1, x2, z2, ceil);
        s.fill(x1 + 1, 1, 17, x2 - 1, ceil - 1, 17, RENDER);
        Southern.beamCeiling(s, x1 + 1, z1 + 1, x2 - 1, 17, ceil, false);
        Southern.beamCeiling(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, top, false);
        Southern.loggia(s, 2, 18, 14, z2, 1, 6, 5, "south", false);
        entrance(s, 5, 1, 17, "south");
        contractBoard(s, 11, 1, 17, "south", 3, true);
        Southern.stoneBench(s, 2, 18, 3, 18, 1, "south");
        Southern.planter(s, 7, 1, 18, true);
        Southern.amphora(s, 14, 1, 18);
        Southern.amphora(s, 14, 1, 19);

        // the campanile on the square, with the doors from the hall and from the council hall
        Southern.campanile(s, 15, 14, 0, 7, 25, Southern.DOMED, "south");
        s.fill(15, 10, 17, 15, 11, 17, RENDER);
        s.door(15, 1, 16, DOOR, "east", "left");
        s.door(15, up, 18, DOOR, "east", "left");
        dial(s, 18, 23, 20, "south");
        dial(s, 15, 23, 17, "west");
        dial(s, 21, 23, 17, "east");

        Southern.lowHipRoof(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, top + 1);
        // the front: arched windows on the piano nobile, the balcony over the middle column
        Southern.window(s, 4, up + 1, z2, "south", 3, 3, false, true);
        Southern.window(s, 12, up + 1, z2, "south", 3, 3, false, true);
        Southern.balcony(s, 8, up, z2, "south", 3, 2);
        Southern.window(s, x1, up + 1, 7, "west", 1, 2, true, false);
        Southern.window(s, x1, up + 1, 15, "west", 1, 2, true, true);
        Southern.window(s, x2, up + 1, 6, "east", 1, 2, true, false);
        Southern.window(s, x2, up + 1, 10, "east", 1, 2, true, false);
        Southern.window(s, 6, up + 1, z1, "north", 1, 2, true, false);
        Southern.window(s, x1, 2, 7, "west", 1, 3, true, false, IRON);
        Southern.window(s, x1, 2, 14, "west", 1, 3, true, false, IRON);
        Southern.window(s, 8, 2, z1, "north", 1, 2, false, false, IRON);
        Southern.window(s, x2, 2, 10, "east", 1, 3, true, false, IRON);
        s.door(x2, 1, 7, DOOR, "east", "left");
        put(s, x2, 3, 7, CARVED);

        // the great hall
        registrar(s, 8, 1, 7, 1);
        Southern.hearth(s, 2, 1, 11, "east", top + 1);
        feastTable(s, 5, 10, 5, 14, 1);
        feastTable(s, 11, 10, 11, 14, 1);
        stair(s, x2 - 1, 1, 15, "north", up - 1);
        trophies(s, 3, 1, 5, "south", 3);
        trophies(s, 11, 1, 5, "south", 3);
        Furnish.greatChandelier(s, 5, 12, ceil - 1, 2);
        Furnish.greatChandelier(s, 11, 12, ceil - 1, 2);
        Furnish.chandelier(s, 8, 12, ceil - 1, 2);
        Furnish.chandelier(s, 8, 6, ceil - 1, 2);
        lamp(s, 3, 4, 16);
        lamp(s, 13, 4, 7);

        // upstairs: the master's chamber and the map room behind, the council hall in front
        put(s, 13, up, 10, IRON);
        s.fill(x1 + 1, up, 11, x2 - 1, top - 1, 11, RENDER);
        s.fill(8, up, z1 + 1, 8, top - 1, 10, RENDER);
        doorway(s, 5, up, 11);
        doorway(s, 11, up, 11);
        doorway(s, 8, up, 8);
        Furnish.canopyBed(s, 4, up, 7, "north", WOOD);
        Furnish.desk(s, 7, up, 6, "west", WOOD);
        Furnish.rug(s, 3, 8, 6, 9, up, Kit.CLOTH_CARPET);
        Furnish.chandelier(s, 6, 9, top - 1, 1);
        lamp(s, 3, up + 2, 9);
        Furnish.books(s, 9, 5, 13, 5, up, up);
        guildMap(s, 9, up + 1, 5);
        put(s, 11, up, 6, lectern("south"));
        mapTable(s, 10, up, 8);
        Furnish.chandelier(s, 12, 7, top - 1, 1);
        trophies(s, 6, up, 12, "south", 5);
        feastTable(s, 5, 15, 11, 15, up);
        Furnish.greatChandelier(s, 8, 15, top - 1, 2);
        Southern.amphora(s, 2, up, 12);
        Southern.amphora(s, 14, up, 12);
        Southern.planter(s, 2, up, 19, true);
        Southern.planter(s, 14, up, 19, true);
        lamp(s, 3, up + 2, 17);
        lamp(s, 13, up + 2, 17);
        lamp(s, 3, up + 2, 13);
        lamp(s, 13, up + 2, 13);

        atticLight(s, 5, top + 1, 9);
        atticLight(s, 11, top + 1, 9);
        atticLight(s, 8, top + 1, 15);
        chimney(s, 2, top + 2, 11);

        // the garden behind the campanile
        s.fill(16, 0, 1, 22, 0, 13, PAVING);
        Kit.disc(s, 19, 0, 7, 9, COTTO);
        s.fill(16, 0, 7, 22, 0, 7, COTTO);
        Southern.fountain(s, 19, 1, 7, 5);
        Southern.pergola(s, 16, 0, 22, 2, 1, 3);
        Southern.stoneBench(s, 17, 1, 18, 1, 1, "south");
        Southern.stoneBench(s, 20, 1, 21, 1, 1, "south");
        Southern.citrus(s, 17, 1, 11, true);
        Southern.citrus(s, 21, 1, 11, true);
        Southern.lampPost(s, 16, 1, 4);
        Southern.lampPost(s, 22, 1, 4);
        Southern.lampPost(s, 22, 1, 10);
        // the forecourt
        Southern.lampPost(s, 0, 1, 21);
        Southern.lampPost(s, 22, 1, 21);
        Southern.citrus(s, 1, 1, 23, true);
        Southern.planter(s, 13, 1, 21, true);
        Southern.planter(s, 3, 1, 21, true);
        Southern.wallLantern(s, 3, 3, z1 - 1);
        Southern.wallLantern(s, 9, 3, z1 - 1);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{5, 1, 17}, new int[]{10, 1, 7});
    }

    /**
     * Rank SS: a guild palace, one of the great buildings of a capital. On the square a palazzo of three storeys: a loggia
     * of three great round arches on the ground floor, arched windows and a balcony on the piano nobile and an open
     * loggia of six arches under the eaves; at its west corner the tower of the guild, rising far over everything with its
     * clock dials, a crown of machicolations, an open belfry and a pyramid of tiles. Through a passage under the palazzo
     * one enters the court with its fountain between two arcaded galleries; across the back lies the great hall, two
     * storeys high, with two hearths, long tables and the trophies of the guild. Beside the court a walled garden with a
     * pergola, cypresses and a fountain in the wall. In the palazzo: the counter of the registrar, the stair hall, the
     * master's chamber, the map room with the council table and a gallery of trophies.
     */
    private static Drawn guildSS() {
        Structure s = new Structure(31, 47, 33);
        plot(s);
        // ---- the great hall across the back: x 1..29, z 1..9, two storeys high
        int hallTop = 12;
        footing(s, 1, 1, 29, 9);
        Southern.walls(s, 1, 1, 29, 9, 1, hallTop);
        Southern.band(s, 1, 1, 29, 9, 6);
        Southern.beamCeiling(s, 2, 2, 28, 8, hallTop, true);
        Southern.lowHipRoof(s, 0, 0, 30, 10, hallTop + 1);
        // ---- the galleries round the court, the garden wall
        Southern.walls(s, 9, 10, 9, 19, 1, 7);
        Southern.walls(s, 29, 10, 29, 19, 1, 7);
        s.fill(10, 0, 10, 28, 0, 19, PAVING);
        Southern.loggia(s, 10, 10, 12, 19, 1, 4, 2, "east", false);
        Southern.loggia(s, 26, 10, 28, 19, 1, 4, 2, "west", false);
        Southern.lowRoof(s, 6, Southern.TILES, TRIM_STAIRS, null, new int[]{9, 10, 13, 19, Southern.LEAN_WEST});
        Southern.lowRoof(s, 6, Southern.TILES, TRIM_STAIRS, null, new int[]{25, 10, 29, 19, Southern.LEAN_EAST});
        Southern.courtyard(s, 13, 10, 25, 19, 1, 5);
        // ---- the palazzo on the square: x 9..29, z 20..29
        int x1 = 9;
        int x2 = 29;
        int z1 = 20;
        int z2 = 29;
        int f1 = 7;
        int f2 = 13;
        int top = 18;
        footing(s, x1, z1, x2, z2);
        Southern.walls(s, x1, z1, x2, z2, 1, top);
        Southern.band(s, x1, z1, x2, z2, f1);
        Southern.band(s, x1, z1, x2, z2, f2);
        s.fill(x1 + 1, 1, 26, x2 - 1, f1 - 1, 26, RENDER);
        s.fill(17, 1, z1 + 1, 17, f1 - 1, 25, RENDER);
        s.fill(21, 1, z1 + 1, 21, f1 - 1, 25, RENDER);
        Southern.beamCeiling(s, x1 + 1, z1 + 1, x2 - 1, 26, f1, false);
        Southern.beamCeiling(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, f2, false);
        Southern.beamCeiling(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, top, false);
        Southern.loggia(s, 10, 27, 28, z2, 1, 6, 5, "south", false);
        // the passage from the loggia to the court
        archway(s, 19, 1, 26, 3, 5);
        archway(s, 19, 1, z1, 3, 5);
        s.fill(18, 0, z1, 20, 0, 26, COTTO);
        lamp(s, 19, 5, 23);
        // ---- the tower at the west corner of the front
        int shaft = 32;
        Southern.towerShaft(s, 1, 21, 0, 9, shaft, RENDER, true, "south");
        Southern.machicolations(s, 1, 21, 9, 29, shaft + 1, STONE, STONE_STAIRS, false);
        int stage = Southern.towerShaft(s, 3, 23, shaft, 5, 3, RENDER, true, "north");
        int eaves = Southern.belfry(s, 3, 23, stage, 5);
        Southern.finial(s, 5, Southern.pyramid(s, 2, 22, 8, 28, eaves, Southern.TILES), 25);
        for (int f : new int[]{7, 14, 21, 28, shaft}) {
            lamp(s, 2, f - 1, 28);
            lamp(s, 8, f - 1, 22);
        }
        bigDial(s, 5, 25, 29, "south");
        bigDial(s, 9, 25, 25, "east");
        bigDial(s, 1, 25, 25, "west");
        for (int[] c : new int[][]{{1, 21}, {9, 21}, {1, 29}, {9, 29}}) {
            put(s, c[0], shaft + 1, c[1], Kit.lantern());
        }
        s.door(9, 1, 24, DOOR, "east", "left");
        s.door(9, f1 + 1, 24, DOOR, "east", "left");
        // ---- the roof of the palazzo, after the tower so that it tucks against it
        Southern.lowHipRoof(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, top + 1);

        // ---- the front: the balcony and the arched windows of the piano nobile, the loggia under the eaves
        Southern.balcony(s, 19, f1 + 1, z2, "south", 5, 2);
        Southern.window(s, 13, f1 + 2, z2, "south", 3, 3, false, true);
        Southern.window(s, 25, f1 + 2, z2, "south", 3, 3, false, true);
        Southern.loggia(s, 10, 27, 28, z2, f2 + 1, 3, 2, "south", true);
        s.fill(x1 + 1, f2 + 1, 26, x2 - 1, top - 1, 26, RENDER);
        doorway(s, 19, f2 + 1, 26);
        Southern.window(s, x2, f2 + 2, 23, "east", 1, 2, true, false);
        for (int x : new int[]{13, 25}) {
            Southern.window(s, x, f1 + 2, z1, "north", 1, 2, true, false);
        }

        // ---- the ground floor of the palazzo: the counter on the west of the passage, the stair hall on the east
        registrar(s, 13, 1, 23, 1);
        contractBoard(s, 11, 1, 26, "south", 3, true);
        entrance(s, 15, 1, 26, "south");
        s.door(17, 1, 23, DOOR, "west", "left");
        s.door(21, 1, 23, DOOR, "east", "left");
        entrance(s, 25, 1, 26, "south");
        Furnish.bench(s, 10, 21, 10, 22, 1, "east", WOOD);
        Southern.planter(s, 16, 1, 21, false);
        Furnish.chandelier(s, 13, 24, f1 - 1, 2);
        lamp(s, 11, 4, 22);
        stair(s, 22, 1, 21, "east", f1);
        Furnish.bench(s, 28, 23, 28, 25, 1, "west", WOOD);
        Southern.amphora(s, 22, 1, 25);
        Southern.amphora(s, 23, 1, 25);
        Furnish.chandelier(s, 25, 24, f1 - 1, 2);
        Southern.stoneBench(s, 26, 27, 28, 27, 1, "south");
        Southern.stoneBench(s, 10, 27, 10, 28, 1, "east");
        // the tower: a guard room at its foot
        trophies(s, 2, 1, 27, "east", 4);
        feastTable(s, 6, 24, 6, 26, 1);

        // ---- the piano nobile: the master's chamber, the council hall with the map, the stair hall
        int s1 = f1 + 1;
        s.fill(15, s1, z1 + 1, 15, f2 - 1, z2 - 1, RENDER);
        s.fill(23, s1, z1 + 1, 23, f2 - 1, z2 - 1, RENDER);
        doorway(s, 15, s1, 24);
        doorway(s, 23, s1, 24);
        s.fill(25, s1, 22, 27, s1, 22, IRON);
        put(s, 25, s1, 21, IRON);
        Furnish.canopyBed(s, 12, s1, 24, "north", WOOD);
        Furnish.desk(s, 13, s1, 27, "south", WOOD);
        Furnish.rug(s, 11, 25, 13, 26, s1, Kit.CLOTH_CARPET);
        Furnish.chandelier(s, 12, 26, f2 - 1, 1);
        lamp(s, 10, s1 + 2, 21);
        Furnish.books(s, 16, 21, 22, 21, s1, s1);
        guildMap(s, 17, s1 + 1, 21);
        put(s, 19, s1, 22, lectern("south"));
        mapTable(s, 17, s1, 24);
        feastTable(s, 19, 24, 21, 24, s1);
        Furnish.greatChandelier(s, 19, 25, f2 - 1, 1);
        Furnish.chandelier(s, 17, 22, f2 - 1, 1);
        stair(s, 28, s1, 22, "south", f2 - f1);
        trophies(s, 24, s1, 27, "east", 3);
        Furnish.chandelier(s, 25, 25, f2 - 1, 1);
        s.fill(27, f2 + 1, 25, 27, f2 + 1, 26, IRON);
        put(s, 28, f2 + 1, 24, IRON);

        // ---- the top floor: the gallery of trophies behind the loggia under the eaves
        int s2 = f2 + 1;
        trophies(s, 11, s2, 21, "south", 7);
        trophies(s, 19, s2, 21, "south", 7);
        feastTable(s, 13, 24, 23, 24, s2);
        Furnish.chandelier(s, 14, 23, top - 1, 1);
        Furnish.chandelier(s, 22, 23, top - 1, 1);
        Furnish.chandelier(s, 18, 23, top - 1, 1);
        lamp(s, 11, s2 + 2, 25);
        for (int x : new int[]{11, 15, 23, 27}) {
            Southern.planter(s, x, s2, 28, x % 8 == 3);
        }

        // ---- the great hall: two hearths, long tables, trophies on the end walls, high windows
        entrance(s, 19, 1, 9, "south");
        s.door(11, 1, 9, DOOR, "north", "left");
        s.door(27, 1, 9, DOOR, "north", "left");
        Southern.hearth(s, 9, 1, 2, "south", hallTop + 1);
        Southern.hearth(s, 21, 1, 2, "south", hallTop + 1);
        feastTable(s, 4, 5, 12, 5, 1);
        feastTable(s, 17, 5, 26, 5, 1);
        trophies(s, 2, 1, 7, "east", 5);
        trophies(s, 28, 1, 3, "west", 5);
        for (int x = 13; x <= 17; x++) {
            put(s, x, 7, 2, Kit.wallBanner(x % 2 == 1 ? GUILD : "yellow", "south"));
        }
        put(s, 15, 4, 2, "minecraft:wither_skeleton_wall_skull[facing=south]");
        put(s, 14, 1, 2, "minecraft:grindstone[face=floor,facing=south]");
        put(s, 16, 1, 2, "minecraft:grindstone[face=floor,facing=south]");
        for (int x : new int[]{4, 26}) {
            Southern.window(s, x, 7, 1, "north", 3, 3, false, false);
        }
        for (int x : new int[]{15, 23}) {
            Southern.window(s, x, 7, 9, "south", 3, 3, false, false);
        }
        Southern.window(s, 15, 2, 9, "south", 1, 3, true, false, IRON);
        Southern.window(s, 23, 2, 9, "south", 1, 3, true, false, IRON);
        for (int x : new int[]{6, 15, 24}) {
            Furnish.greatChandelier(s, x, 5, hallTop - 1, 3);
        }
        lamp(s, 3, 5, 3);
        lamp(s, 27, 5, 7);
        chimney(s, 9, hallTop + 2, 2);
        chimney(s, 21, hallTop + 2, 2);
        atticLight(s, 15, hallTop + 1, 5);
        atticLight(s, 19, top + 1, 24);

        // ---- the garden beside the court
        s.fill(1, 0, 10, 8, 0, 20, PAVING);
        s.fill(5, 0, 10, 6, 0, 20, COTTO);
        s.fill(1, 1, 10, 1, 2, 20, RENDER);
        s.fill(1, 3, 10, 1, 3, 20, Kit.slab(TRIM_SLAB));
        Southern.pergola(s, 2, 10, 4, 20, 1, 3);
        Southern.wallFountain(s, 8, 1, 15, "west");
        s.door(9, 1, 12, DOOR, "west", "left");
        Southern.cypress(s, 7, 1, 11, 10);
        Southern.cypress(s, 7, 1, 19, 10);
        Southern.citrus(s, 6, 1, 15, true);
        Southern.lampPost(s, 5, 1, 12);
        Southern.lampPost(s, 5, 1, 18);
        // ---- the forecourt
        for (int x : new int[]{10, 16, 22, 28}) {
            Southern.lampPost(s, x, 1, 31);
        }
        Southern.citrus(s, 1, 1, 31, true);
        Southern.citrus(s, 30, 1, 31, true);
        for (int x : new int[]{5, 15, 25}) {
            Southern.wallLantern(s, x, 4, 0);
        }
        for (int z : new int[]{5, 15, 25}) {
            Southern.wallLantern(s, 30, 4, z);
        }
        Southern.wallLantern(s, 0, 3, 27);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{15, 1, 26}, new int[]{15, 1, 23});
    }

    /**
     * Rank B: a small guildhall of two storeys under a low hip roof of tiles. Its ground floor opens to the square in a
     * loggia of two round arches; behind it the great hall with the counter of the registrar, the hearth under its hood,
     * a long table with benches and the contract board set into the wall of the loggia, so that its notices are read from
     * the square as well. Over the arches the windows of the upper floor with their shutters and a clock dial of blue and
     * gold, and over the eaves a bell gable with its bell. Upstairs the map room with its lectern and the trophy room.
     */
    private static Drawn guildB() {
        Structure s = new Structure(13, 21, 15);
        plot(s);
        int x1 = 1;
        int x2 = 11;
        int z1 = 1;
        int z2 = 12;
        int ceil = 6;
        int top = 11;
        int up = ceil + 1;
        footing(s, x1, z1, x2, z2);
        Southern.walls(s, x1, z1, x2, z2, 1, top);
        Southern.band(s, x1, z1, x2, z2, ceil);
        // the wall between the hall and the loggia, the ceilings
        s.fill(x1 + 1, 1, 9, x2 - 1, ceil - 1, 9, RENDER);
        Southern.beamCeiling(s, x1 + 1, z1 + 1, x2 - 1, 9, ceil, false);
        Southern.beamCeiling(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, top, false);
        Southern.loggia(s, 2, 10, 10, z2, 1, 5, 3, "south", false);
        entrance(s, 4, 1, 9, "south");
        contractBoard(s, 8, 1, 9, "south", 3, true);
        Southern.stoneBench(s, 2, 10, 3, 10, 1, "south");
        Southern.amphora(s, 10, 1, 10);
        Southern.planter(s, 10, 1, 11, true);
        // the windows: grilles on the ground floor, shutters and pots above, the clock dial in the middle of the front
        Southern.window(s, x2, 2, 3, "east", 1, 2, true, false, IRON);
        Southern.window(s, 4, 2, z1, "north", 1, 2, true, false, IRON);
        Southern.window(s, 3, up + 1, z2, "south", 1, 2, true, true);
        Southern.window(s, 9, up + 1, z2, "south", 1, 2, true, true);
        dial(s, 6, up + 2, z2, "south");
        Southern.window(s, x1, up + 1, 4, "west", 1, 2, true, false);
        Southern.window(s, x1, up + 1, 9, "west", 1, 2, true, true);
        Southern.window(s, x2, up + 1, 4, "east", 1, 2, true, false);
        Southern.window(s, x2, up + 1, 10, "east", 1, 2, true, true);
        Southern.window(s, 3, up + 1, z1, "north", 1, 2, true, false);
        Southern.window(s, 9, up + 1, z1, "north", 1, 2, true, false);

        // the great hall
        registrar(s, 9, 1, 4, 1);
        Southern.hearth(s, 10, 1, 7, "west", top + 1);
        Furnish.longTable(s, 5, 6, 8, 6, 1, WOOD);
        Furnish.bench(s, 5, 5, 8, 5, 1, "south", WOOD);
        Furnish.bench(s, 5, 7, 8, 7, 1, "north", WOOD);
        put(s, 5, 2, 6, Furnish.candles(3));
        put(s, 7, 2, 6, POT);
        put(s, 8, 2, 6, Furnish.candles(2));
        Furnish.chandelier(s, 6, 6, ceil - 1, 2);
        Furnish.chandelier(s, 7, 3, ceil - 1, 1);
        stair(s, 2, 1, 8, "north", up - 1);
        Southern.amphora(s, 2, 1, 2);
        Southern.planter(s, 3, 1, 2, false);
        lamp(s, 3, 4, 7);

        // upstairs: the map room behind, the trophy room over the loggia
        s.fill(3, up, 4, 3, up, 5, IRON);
        put(s, 2, up, 6, IRON);
        s.fill(x1 + 1, up, 7, x2 - 1, top - 1, 7, RENDER);
        s.fill(6, up, 7, 6, up + 1, 7, AIR);
        put(s, 6, up + 2, 7, TRIM);
        Furnish.books(s, 4, 2, 8, 2, up, up);
        guildMap(s, 4, up + 1, 2);
        put(s, 6, up, 3, lectern("south"));
        mapTable(s, 8, up, 5);
        Furnish.chandelier(s, 6, 4, top - 1, 1);
        Furnish.chandelier(s, 9, 3, top - 1, 1);
        lamp(s, 3, up + 2, 3);
        trophies(s, 3, up, 8, "south", 3);
        trophies(s, 7, up, 8, "south", 3);
        Furnish.rug(s, 4, 9, 8, 11, up, Kit.CLOTH_CARPET);
        Furnish.table(s, 6, up, 10, WOOD);
        Furnish.chair(s, 5, up, 10, "east", WOOD);
        Furnish.chair(s, 7, up, 10, "west", WOOD);
        Furnish.chandelier(s, 6, 10, top - 1, 1);
        lamp(s, 3, up + 2, 10);
        lamp(s, 9, up + 2, 10);

        bellCote(s, 6, top, z2);
        Southern.lowHipRoof(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, top + 1);
        atticLight(s, 6, top + 1, 6);
        chimney(s, 10, top + 2, 7);
        // the forecourt: lamp posts and flowers
        Southern.lampPost(s, 0, 1, 13);
        Southern.lampPost(s, 12, 1, 13);
        Southern.planter(s, 1, 1, 13, true);
        Southern.planter(s, 11, 1, 13, true);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{4, 1, 9}, new int[]{7, 1, 4});
    }

    // ---------------------------------------------------------------- parts of the guild

    /**
     * A long table of the furniture wood from (x1, z1) to (x2, z2) along x or z with a bench on each side, candles and jugs
     * on it.
     */
    private static void feastTable(Structure s, int x1, int z1, int x2, int z2, int y) {
        boolean alongX = z1 == z2;
        Furnish.longTable(s, x1, z1, x2, z2, y, WOOD);
        if (alongX) {
            Furnish.bench(s, x1, z1 - 1, x2, z1 - 1, y, "south", WOOD);
            Furnish.bench(s, x1, z1 + 1, x2, z1 + 1, y, "north", WOOD);
        } else {
            Furnish.bench(s, x1 - 1, z1, x1 - 1, z2, y, "east", WOOD);
            Furnish.bench(s, x1 + 1, z1, x1 + 1, z2, y, "west", WOOD);
        }
        int n = alongX ? x2 - x1 : z2 - z1;
        for (int i = 0; i <= n; i++) {
            int x = alongX ? x1 + i : x1;
            int z = alongX ? z1 : z1 + i;
            if (i % 3 == 0) {
                put(s, x, y + 1, z, Furnish.candles(i % 2 == 0 ? 3 : 2));
            } else if (i % 3 == 2) {
                put(s, x, y + 1, z, POT);
            }
        }
    }

    /**
     * A doorway two high through a partition at (x, y, z) under a lintel of trim.
     */
    private static void doorway(Structure s, int x, int y, int z) {
        put(s, x, y, z, AIR);
        put(s, x, y + 1, z, AIR);
        put(s, x, y + 2, z, TRIM);
    }

    /**
     * An open round arch through a wall that runs along x: {@code width} (odd) wide around x and {@code height} high from
     * layer y, its upper corners rounded with upside-down stairs of trim and a carved keystone over its crown.
     */
    private static void archway(Structure s, int x, int y, int z, int width, int height) {
        int hw = (width - 1) / 2;
        s.fill(x - hw, y, z, x + hw, y + height - 1, z, AIR);
        put(s, x - hw, y + height - 1, z, Kit.upside(TRIM_STAIRS, "west"));
        put(s, x + hw, y + height - 1, z, Kit.upside(TRIM_STAIRS, "east"));
        for (int u = -hw + 1; u < hw; u++) {
            put(s, x + u, y + height, z, TRIM);
        }
        put(s, x, y + height, z, CARVED);
    }

    /**
     * A great clock dial five by five in a wall looking {@code facing}, centred on (x, y, z): a round frame of trim, a face
     * of blue lapis with two golden hands from a golden boss.
     */
    private static void bigDial(Structure s, int x, int y, int z, String facing) {
        int[] a = Furnish.step(Furnish.left(facing));
        for (int u = -2; u <= 2; u++) {
            for (int v = -2; v <= 2; v++) {
                if (Math.abs(u) == 2 && Math.abs(v) == 2) {
                    continue;
                }
                String state;
                if (Math.abs(u) == 2 || Math.abs(v) == 2) {
                    state = TRIM;
                } else if (u == 0 && v >= 0 || v == 0 && u == 1) {
                    state = GOLD;
                } else {
                    state = LAPIS;
                }
                put(s, x + a[0] * u, y + v, z + a[1] * u, state);
            }
        }
    }

    /**
     * The counter of the registrar: the guild desk at (x, y, z) looking south between two counters faced with blue glazed
     * tiles (candles on one, a lantern on the other), his post two blocks behind it, the one chest of the guild beside the
     * post on the side {@code side} (+1 east, -1 west) and a shelf of books on the other side.
     */
    private static void registrar(Structure s, int x, int y, int z, int side) {
        put(s, x, y, z, DESK);
        put(s, x - 1, y, z, AZULEJO_BLUE + "[facing=south]");
        put(s, x + 1, y, z, AZULEJO_BLUE + "[facing=south]");
        put(s, x - side, y + 1, z, Furnish.candles(2));
        put(s, x + side, y + 1, z, Kit.lantern());
        put(s, x, y, z - 2, WORK_POST);
        put(s, x + side, y, z - 2, Kit.chest("south"));
        Furnish.books(s, x - side, z - 2, x - side, z - 2, y, y + 1);
    }

    /**
     * The contract board set into an inner wall whose faces look {@code facing} and the other way: {@code width} cells
     * centred on (x, z), boards of the timber two high on a footing of trim and under a lintel of trim, with notices of
     * white and yellow cloth hanging on it on the side {@code facing} and, with {@code both}, on the other side too.
     */
    private static void contractBoard(Structure s, int x, int y, int z, String facing, int width, boolean both) {
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        int half = (width - 1) / 2;
        for (int u = -half; u <= half; u++) {
            int px = x + a[0] * u;
            int pz = z + a[1] * u;
            put(s, px, y, pz, TRIM);
            put(s, px, y + 1, pz, PLANKS);
            put(s, px, y + 2, pz, PLANKS);
            put(s, px, y + 3, pz, TRIM);
            String color = Math.floorMod(u, 2) == 1 ? "yellow" : "white";
            put(s, px + f[0], y + 2, pz + f[1], Kit.wallBanner(color, facing));
            if (both) {
                put(s, px - f[0], y + 2, pz - f[1], Kit.wallBanner(Math.floorMod(u, 2) == 1 ? "white" : "yellow",
                        Furnish.opposite(facing)));
            }
        }
    }

    /**
     * A clock dial in a wall looking {@code facing}, centred on (x, y, z): a cross of blue lapis around a gold boss with a
     * golden hand pointing up, the corners of trim, three by three.
     */
    private static void dial(Structure s, int x, int y, int z, String facing) {
        int[] a = Furnish.step(Furnish.left(facing));
        for (int u = -1; u <= 1; u++) {
            for (int v = -1; v <= 1; v++) {
                String state = u == 0 && v >= 0 ? GOLD : u == 0 || v == 0 ? LAPIS : TRIM;
                put(s, x + a[0] * u, y + v, z + a[1] * u, state);
            }
        }
    }

    /**
     * A bell gable over the middle of a front: a wall of render five wide rising from the top course at {@code top} in the
     * plane of the front at z, a round arch in it with the bell hanging under a carved keystone, piers behind its ends,
     * a stepped coping of trim and a finial.
     */
    private static void bellCote(Structure s, int cx, int top, int z) {
        int y = top + 1;
        put(s, cx - 3, y, z, Kit.stairs(TRIM_STAIRS, "east"));
        s.fill(cx - 2, y, z, cx + 2, y, z, RENDER);
        put(s, cx + 3, y, z, Kit.stairs(TRIM_STAIRS, "west"));
        s.fill(cx - 2, y + 1, z, cx - 2, y + 3, z, RENDER);
        s.fill(cx + 2, y + 1, z, cx + 2, y + 3, z, RENDER);
        put(s, cx - 1, y + 2, z, Kit.upside(TRIM_STAIRS, "west"));
        put(s, cx + 1, y + 2, z, Kit.upside(TRIM_STAIRS, "east"));
        s.fill(cx - 1, y + 3, z, cx + 1, y + 3, z, RENDER);
        put(s, cx, y + 3, z, CARVED);
        put(s, cx, y + 2, z, Southern.BELL);
        put(s, cx - 2, y + 4, z, Kit.stairs(TRIM_STAIRS, "east"));
        s.fill(cx - 1, y + 4, z, cx + 1, y + 4, z, TRIM);
        put(s, cx + 2, y + 4, z, Kit.stairs(TRIM_STAIRS, "west"));
        put(s, cx - 1, y + 5, z, Kit.stairs(TRIM_STAIRS, "east"));
        put(s, cx, y + 5, z, TRIM);
        put(s, cx + 1, y + 5, z, Kit.stairs(TRIM_STAIRS, "west"));
        Southern.finial(s, cx, y + 6, z);
    }

    /**
     * The door of a hall in a wall looking {@code facing}, its lower half at (x, y, z), with a fanlight of glass over it
     * and a carved keystone over the fanlight.
     */
    private static void entrance(Structure s, int x, int y, int z, String facing) {
        s.door(x, y, z, DOOR, Furnish.opposite(facing), "left");
        put(s, x, y + 2, z, Kit.GLASS);
        put(s, x, y + 3, z, CARVED);
    }

    /**
     * A lantern standing on a ceiling at (x, y, z) in the hollow under a roof, so that no dark room is left there.
     */
    private static void atticLight(Structure s, int x, int y, int z) {
        put(s, x, y, z, Kit.lantern());
    }

    /**
     * A chimney stack over the flue of a hearth: a block of tiles in the roof at {@code y}, the stack of brick over it with
     * its pot and cap.
     */
    private static void chimney(Structure s, int x, int y, int z) {
        put(s, x, y, z, Southern.TILES.block());
        put(s, x, y + 1, z, Kit.CHIMNEY);
        put(s, x, y + 2, z, Kit.CHIMNEY);
        put(s, x, y + 3, z, Kit.CHIMNEY_POT);
        put(s, x, y + 4, z, Kit.slab(Kit.CHIMNEY_SLAB));
    }

    /**
     * A flight of stairs of the timber from (x, y, z) climbing one layer for every block toward {@code dir}, {@code n} steps,
     * a solid base of render under every step, the floor above cut away over the steps for the head.
     */
    private static void stair(Structure s, int x, int y, int z, String dir, int n) {
        int[] d = Furnish.step(dir);
        for (int k = 0; k < n; k++) {
            int px = x + d[0] * k;
            int pz = z + d[1] * k;
            int py = y + k;
            if (k > 0) {
                s.fill(px, y, pz, px, py - 1, pz, RENDER);
            }
            put(s, px, py, pz, Kit.stairs(PLANK_STAIRS, dir));
            put(s, px, py + 1, pz, AIR);
            put(s, px, py + 2, pz, AIR);
        }
    }

    /**
     * Trophies along a wall that looks {@code facing}, {@code count} cells from (x, z) toward the left of {@code facing}:
     * racks of weapons (grindstones) with the skulls of beasts hung over them, between them pikes carrying skulls, and the
     * banner of the guild over the middle.
     */
    private static void trophies(Structure s, int x, int y, int z, String facing, int count) {
        int[] a = Furnish.step(Furnish.left(facing));
        for (int u = 0; u < count; u++) {
            int px = x + a[0] * u;
            int pz = z + a[1] * u;
            if (u % 2 == 0) {
                put(s, px, y, pz, "minecraft:grindstone[face=floor,facing=" + facing + "]");
                put(s, px, y + 2, pz, (u % 4 == 0 ? "minecraft:skeleton_wall_skull" : "minecraft:wither_skeleton_wall_skull")
                        + "[facing=" + facing + "]");
            } else {
                put(s, px, y, pz, FENCE);
                put(s, px, y + 1, pz, "minecraft:skeleton_skull[rotation=" + rotation(facing) + "]");
            }
        }
        int mid = (count - 1) / 2;
        put(s, x + a[0] * mid, y + 3, z + a[1] * mid, Kit.wallBanner(GUILD, facing));
    }

    /** The rotation of a standing skull or banner that looks the given way. */
    private static int rotation(String facing) {
        return switch (facing) {
            case "south" -> 0;
            case "west" -> 4;
            case "north" -> 8;
            default -> 12;
        };
    }

    /**
     * A map of the lands of the guild on a wall that runs along x: lands, seas and hills of terracotta five wide and two
     * high, from (x, y, z) toward the east, its upper row at {@code y + 1}.
     */
    private static void guildMap(Structure s, int x, int y, int z) {
        String[] map = {"lgyBc", "gcglb"};
        for (int row = 0; row < map.length; row++) {
            for (int i = 0; i < 5; i++) {
                String color = switch (map[row].charAt(i)) {
                    case 'l' -> "lime";
                    case 'g' -> "green";
                    case 'y' -> "yellow";
                    case 'b' -> "light_blue";
                    case 'c' -> "cyan";
                    default -> "brown";
                };
                put(s, x + i, y + 1 - row, z, "minecraft:" + color + "_terracotta");
            }
        }
    }

    /**
     * The map table: the cartography table with stools of the timber at its sides and before it.
     */
    private static void mapTable(Structure s, int x, int y, int z) {
        put(s, x, y, z, CARTOGRAPHY);
        put(s, x - 1, y, z, Kit.stairs(PLANK_STAIRS, "west"));
        put(s, x + 1, y, z, Kit.stairs(PLANK_STAIRS, "east"));
        put(s, x, y, z + 1, Kit.stairs(PLANK_STAIRS, "south"));
    }

    /** A lectern looking the given way, without a book: the one lectern of a building. */
    private static String lectern(String facing) {
        return "minecraft:lectern[facing=" + facing + ",has_book=false,powered=false]";
    }

    // ================================================================ the mages' tower

    /** The four sides of a round tower, where its windows look out. */
    private static final String[] SIDES = {"north", "south", "west", "east"};

    /**
     * The tower of the mages of a rank.
     */
    static Drawn mageTower(Rank rank) {
        return switch (rank) {
            case B -> mageB();
            case A -> mageA();
            case S -> mageS();
            case SS -> mageSS();
        };
    }

    /**
     * Rank B: one round white tower of three storeys on a footing of dressed stone, bands of trim at its floors, arched
     * windows of purple glass; at its top a terrace on a ring of corbels behind a white parapet, where the mage watches
     * the stars with his telescopes, and in its middle a little round observatory on a drum under a dome of blue glazed
     * tiles. The focus, the post and the brewing corner on the ground floor, the mage's bed and his books on the first,
     * the research room on the second.
     */
    private static Drawn mageB() {
        Structure s = new Structure(11, 27, 11);
        plot(s);
        int c = 5;
        int r = 4;
        int terrace = 15;
        shell(s, c, c, 9, terrace, "", 0, 5, 10);
        ladder(s, c - 3, c - 2, terrace);
        towerDoor(s, c, c, r, true);

        // the ground floor: the focus, the post and its chest, the brewing corner
        focus(s, c, 1, c);
        put(s, c + 2, 1, c - 2, WORK_POST);
        put(s, c + 3, 1, c - 2, Kit.chest("west"));
        put(s, c + 3, 1, c + 1, BREWING);
        put(s, c + 3, 1, c + 2, CAULDRON);
        Furnish.books(s, c - 2, c - 3, c - 2, c - 3, 1, 2);
        Furnish.books(s, c + 2, c - 3, c + 2, c - 3, 1, 2);
        Furnish.books(s, c - 3, c + 2, c - 3, c + 2, 1, 2);
        Southern.amphora(s, c - 2, 1, c + 3);
        Furnish.chandelier(s, c, c, 4, 1);
        lamp(s, c + 2, 3, c + 2);
        lamp(s, c - 2, 3, c + 2);

        // the first floor: the mage's bed and his library
        int f1 = 6;
        Southern.ironBed(s, c + 2, f1, c - 1, "north", "purple");
        Furnish.books(s, c - 2, c + 3, c - 2, c + 3, f1, f1 + 1);
        Furnish.books(s, c + 2, c + 3, c + 2, c + 3, f1, f1 + 1);
        Furnish.books(s, c - 3, c + 2, c - 3, c + 2, f1, f1 + 1);
        Furnish.books(s, c - 2, c - 3, c - 2, c - 3, f1, f1 + 1);
        put(s, c - 1, f1, c + 1, lectern("north"));
        Furnish.rug(s, c - 1, c - 1, c + 1, c, f1, "minecraft:purple_carpet");
        Furnish.chandelier(s, c, c, f1 + 3, 1);
        lamp(s, c - 2, f1 + 2, c);

        // the second floor: the research room
        researchRoom(s, c, 11, c);
        Furnish.chandelier(s, c, c + 1, 14, 1);

        // the terrace with the observatory under the dome
        observatory(s, c, terrace + 1, c, 7);
        telescope(s, c, terrace + 1, c - 4, "north");
        telescope(s, c + 4, terrace + 1, c, "east");
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, c + r}, null);
    }

    /**
     * Rank A: a taller round tower of four storeys, entered through a little porch, an arch on two columns under a lean-to
     * roof of tiles. The focus, the post and the brewing corner on the ground floor; the room of the two mages with their
     * beds; the library with its shelves round the walls, its lectern and reading table; the research room; and on the
     * terrace the observatory under a wider dome on a drum.
     */
    private static Drawn mageA() {
        Structure s = new Structure(13, 33, 15);
        plot(s);
        int c = 6;
        int r = 5;
        int terrace = 20;
        shell(s, c, c, 11, terrace, "", 0, 5, 10, 15);
        ladder(s, c - 4, c - 2, terrace);
        towerDoor(s, c, c, r, false);
        // the porch before the door
        int pz = c + r + 2;
        s.fill(c - 2, 0, c + r + 1, c + 2, 0, pz, STONE);
        s.fill(c - 1, 0, c + r + 1, c + 1, 0, pz, COTTO);
        Southern.arcade(s, c - 2, pz, c + 2, pz, 1, 4, 3, TRIM);
        s.fill(c - 2, 1, c + r + 1, c - 2, 4, c + r + 1, RENDER);
        s.fill(c + 2, 1, c + r + 1, c + 2, 4, c + r + 1, RENDER);
        s.fill(c - 2, 5, c + r + 1, c + 2, 5, c + r + 1, TRIM);
        s.fill(c - 1, 5, c + r + 1, c + 1, 5, c + r + 1, PLANKS);
        Southern.lowRoof(s, 6, Southern.TILES, TRIM_STAIRS, RENDER, new int[]{c - 3, c + r + 1, c + 3, pz + 1, Southern.LEAN_NORTH});
        lamp(s, c, 4, c + r + 1);

        // the ground floor
        focus(s, c, 1, c);
        put(s, c + 2, 1, c - 2, WORK_POST);
        put(s, c + 3, 1, c - 2, Kit.chest("west"));
        put(s, c + 3, 1, c + 1, BREWING);
        put(s, c + 3, 1, c + 2, BREWING);
        put(s, c + 2, 1, c + 3, CAULDRON);
        put(s, c + 1, 1, c + 3, Kit.topSlab("minecraft:" + WOOD + "_slab"));
        put(s, c + 1, 2, c + 3, Furnish.candles(2));
        Furnish.books(s, c - 2, c - 4, c - 2, c - 4, 1, 3);
        Furnish.books(s, c + 2, c - 4, c + 2, c - 4, 1, 3);
        Furnish.books(s, c - 4, c + 2, c - 4, c + 2, 1, 2);
        Southern.amphora(s, c - 3, 1, c + 3);
        Southern.planter(s, c - 2, 1, c + 4, true);
        Southern.planter(s, c + 2, 1, c + 4, true);
        Furnish.chandelier(s, c, c, 4, 1);
        lamp(s, c - 3, 3, c + 1);
        lamp(s, c + 3, 3, c - 1);

        // the first floor: the room of the two mages
        int f1 = 6;
        Southern.ironBed(s, c - 2, f1, c - 1, "north", "purple");
        Southern.ironBed(s, c + 2, f1, c - 1, "north", "purple");
        Furnish.rug(s, c - 1, c - 1, c + 1, c + 1, f1, "minecraft:purple_carpet");
        Furnish.desk(s, c, f1, c + 4, "north", WOOD);
        Furnish.crate(s, c - 3, f1, c + 3, WOOD);
        Furnish.crate(s, c + 3, f1, c + 3, WOOD);
        Furnish.books(s, c + 4, c + 2, c + 4, c + 2, f1, f1 + 1);
        Furnish.chandelier(s, c, c, f1 + 3, 1);
        lamp(s, c - 3, f1 + 2, c + 2);
        lamp(s, c + 3, f1 + 2, c + 2);

        // the second floor: the library
        int f2 = 11;
        shelvesRound(s, c, f2, c, 11, 2);
        put(s, c, f2, c - 3, lectern("south"));
        feastTable(s, c - 1, c + 1, c + 1, c + 1, f2);
        Furnish.chandelier(s, c, c, f2 + 3, 1);
        lamp(s, c - 3, f2 + 2, c - 3);
        lamp(s, c + 3, f2 + 2, c + 3);

        // the third floor: the research room
        researchRoom(s, c, 16, c);
        Furnish.chandelier(s, c, c + 1, 19, 1);
        lamp(s, c + 3, 18, c - 3);

        // the terrace with the observatory
        observatory(s, c, terrace + 1, c, 9);
        telescope(s, c, terrace + 1, c - 5, "north");
        telescope(s, c + 5, terrace + 1, c, "east");
        telescope(s, c - 5, terrace + 1, c, "west");
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, c + r}, null);
    }

    /**
     * Rank S: the tower of five storeys with an annex of two storeys against its west side under a low hip roof of tiles:
     * a porch under one great arch, the library behind it and the brewing room over it, joined to the tower on both
     * floors. In the tower the focus with the post, the room of two mages, the room of the third with his study, the
     * research room and the star chamber; on the terrace the observatory under its dome.
     */
    private static Drawn mageS() {
        Structure s = new Structure(19, 38, 17);
        plot(s);
        int cx = 12;
        int cz = 7;
        int r = 5;
        int terrace = 25;
        // ---- the annex: x 1..7, z 3..12
        int ax1 = 1;
        int ax2 = 7;
        int az1 = 3;
        int az2 = 12;
        footing(s, ax1, az1, ax2, az2);
        Southern.walls(s, ax1, az1, ax2, az2, 1, 10);
        Southern.band(s, ax1, az1, ax2, az2, 5);
        s.fill(ax1 + 1, 1, 9, ax2 - 1, 4, 9, RENDER);
        Southern.beamCeiling(s, ax1 + 1, az1 + 1, ax2 - 1, az2 - 1, 5, true);
        Southern.beamCeiling(s, ax1 + 1, az1 + 1, ax2 - 1, az2 - 1, 10, true);
        Southern.arcade(s, ax1, az2, ax2, az2, 1, 4, 5, TRIM);
        s.fill(ax1 + 1, 0, 10, ax2 - 1, 0, 11, COTTO);
        entrance(s, 4, 1, 9, "south");
        Southern.window(s, 4, 7, az2, "south", 3, 2, true, true);
        Southern.window(s, ax1, 2, 6, "west", 1, 2, true, false, IRON);
        Southern.window(s, ax1, 7, 5, "west", 1, 2, true, true);
        Southern.window(s, ax1, 7, 9, "west", 1, 2, true, true);
        Southern.window(s, 4, 7, az1, "north", 1, 2, true, false);
        Southern.lowHipRoof(s, ax1 - 1, az1 - 1, ax2 + 1, az2 + 1, 11);
        atticLight(s, 4, 11, 7);
        // ---- the tower over the east end of the annex
        shell(s, cx, cz, 11, terrace, "west", 15, 5, 10, 15, 20);
        ladder(s, cx - 4, cz - 2, terrace);
        towerDoor(s, cx, cz, r, true);
        s.door(ax2, 1, cz, DOOR, "west", "left");
        s.door(ax2, 6, cz, DOOR, "west", "left");

        // the library in the annex, its porch
        Furnish.books(s, 2, 4, 2, 5, 1, 3);
        Furnish.books(s, 2, 7, 2, 8, 1, 3);
        Furnish.books(s, 2, 6, 2, 6, 1, 1);
        Furnish.books(s, 3, 4, 6, 4, 1, 3);
        put(s, 4, 1, 6, lectern("south"));
        Furnish.table(s, 5, 1, 7, WOOD);
        Furnish.chair(s, 5, 1, 8, "north", WOOD);
        Furnish.chandelier(s, 4, 6, 4, 1);
        lamp(s, 5, 3, 8);
        Southern.stoneBench(s, 2, 10, 3, 10, 1, "south");
        Southern.stoneBench(s, 5, 10, 6, 10, 1, "south");
        lamp(s, 4, 4, 11);
        // the brewing room over it
        int f1 = 6;
        put(s, 2, f1, 5, BREWING);
        put(s, 2, f1, 7, BREWING);
        put(s, 2, f1, 9, BREWING);
        put(s, 2, f1, 6, CAULDRON);
        put(s, 2, f1, 8, CAULDRON);
        put(s, 3, f1, 4, Kit.topSlab("minecraft:" + WOOD + "_slab"));
        put(s, 3, f1 + 1, 4, Furnish.candles(3));
        put(s, 5, f1, 4, Kit.topSlab("minecraft:" + WOOD + "_slab"));
        put(s, 5, f1 + 1, 4, POT);
        Furnish.crate(s, 6, f1, 11, WOOD);
        Furnish.sack(s, 5, f1, 11, false);
        Furnish.sack(s, 2, f1, 11, true);
        Furnish.plant(s, 3, f1, 11, "flowering_azalea");
        Furnish.table(s, 4, f1, 8, WOOD);
        put(s, 4, f1 + 1, 8, Furnish.candles(2));
        Furnish.chandelier(s, 4, 7, 9, 1);
        lamp(s, 5, f1 + 2, 10);

        // the tower: the ground floor
        focus(s, cx, 1, cz);
        put(s, cx + 2, 1, cz - 2, WORK_POST);
        put(s, cx + 3, 1, cz - 2, Kit.chest("west"));
        Furnish.books(s, cx - 2, cz - 4, cx - 2, cz - 4, 1, 3);
        Furnish.books(s, cx + 2, cz - 4, cx + 2, cz - 4, 1, 3);
        Furnish.books(s, cx + 4, cz + 2, cx + 4, cz + 2, 1, 2);
        Southern.amphora(s, cx + 3, 1, cz + 3);
        Southern.planter(s, cx - 3, 1, cz + 3, true);
        Furnish.chandelier(s, cx, cz, 4, 1);
        lamp(s, cx - 3, 3, cz + 1);
        lamp(s, cx + 3, 3, cz + 1);
        // the room of two mages
        Southern.ironBed(s, cx - 1, f1, cz - 1, "north", "purple");
        Southern.ironBed(s, cx + 2, f1, cz - 1, "north", "purple");
        Furnish.rug(s, cx - 1, cz, cx + 2, cz + 1, f1, "minecraft:purple_carpet");
        Furnish.crate(s, cx + 3, f1, cz + 3, WOOD);
        Furnish.desk(s, cx, f1, cz + 4, "north", WOOD);
        Furnish.chandelier(s, cx, cz, f1 + 3, 1);
        lamp(s, cx - 3, f1 + 2, cz + 2);
        lamp(s, cx + 3, f1 + 2, cz + 2);
        // the room of the third mage with his study
        int f2 = 11;
        Southern.ironBed(s, cx + 2, f2, cz - 1, "north", "purple");
        Furnish.desk(s, cx - 2, f2, cz + 3, "north", WOOD);
        Furnish.books(s, cx - 4, cz + 2, cx - 4, cz + 2, f2, f2 + 1);
        Furnish.books(s, cx + 4, cz + 2, cx + 4, cz + 2, f2, f2 + 1);
        Furnish.books(s, cx - 2, cz - 4, cx - 2, cz - 4, f2, f2 + 1);
        put(s, cx + 2, f2, cz + 3, CARTOGRAPHY);
        Furnish.rug(s, cx - 1, cz - 1, cx + 1, cz + 1, f2, "minecraft:purple_carpet");
        Furnish.chandelier(s, cx, cz, f2 + 3, 1);
        lamp(s, cx - 3, f2 + 2, cz - 2);
        // the research room
        researchRoom(s, cx, 16, cz);
        Furnish.chandelier(s, cx, cz + 1, 19, 1);
        lamp(s, cx + 3, 18, cz - 3);
        // the star chamber
        starChamber(s, cx, 21, cz, r);
        // the observatory on the terrace
        observatory(s, cx, terrace + 1, cz, 9);
        telescope(s, cx, terrace + 1, cz - 5, "north");
        telescope(s, cx + 5, terrace + 1, cz, "east");

        // the forecourt
        Southern.citrus(s, 2, 1, 14, true);
        Southern.cypress(s, 17, 1, 14, 9);
        Southern.lampPost(s, 8, 1, 14);
        Southern.planter(s, 6, 1, 14, true);
        Southern.wallLantern(s, 4, 3, az1 - 1);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{cx, 1, cz + r}, null);
    }

    /**
     * Rank SS: the great mage tower of six storeys under the widest dome, beside it the domed library hall and before it
     * the garden court behind a low wall with a gate: a fountain on the axis of the tower door, cypresses, citrus trees in
     * pots, lamp posts and a pergola along its east side. In the tower the focus with the post, two rooms of two mages,
     * the brewing room, the research room and the star chamber; in the library hall shelves round the walls, reading
     * tables and the lectern under the dome; behind the hall a herb garden.
     */
    private static Drawn mageSS() {
        Structure s = new Structure(25, 45, 25);
        plot(s);
        int cx = 17;
        int cz = 7;
        int r = 6;
        int terrace = 30;
        shell(s, cx, cz, 13, terrace, "", 0, 5, 10, 15, 20, 25);
        ladder(s, cx - 5, cz - 2, terrace);
        towerDoor(s, cx, cz, r, true);

        // ---- the library hall: x 1..11, z 12..22, a dome on a drum over its middle
        int lx = 6;
        int lz = 17;
        footing(s, 1, 12, 11, 22);
        Southern.walls(s, 1, 12, 11, 22, 1, 8);
        Southern.band(s, 1, 12, 11, 22, 4);
        Southern.beamCeiling(s, 2, 13, 10, 21, 8, false);
        Southern.lowHipRoof(s, 0, 11, 12, 23, 9);
        for (int y = 9; y <= 13; y++) {
            Kit.disc(s, lx, y, lz, 7, AIR);
        }
        floorIn(s, lx, 8, lz, 7, AIR);
        dome(s, lx, 9, lz, 9, 4, MAJOLICA);
        entrance(s, lx, 1, 22, "south");
        s.door(11, 1, lz, DOOR, "east", "left");
        put(s, 11, 3, lz, CARVED);
        Southern.window(s, 3, 5, 22, "south", 3, 3, false, true, PURPLE);
        Southern.window(s, 9, 5, 22, "south", 3, 3, false, true, PURPLE);
        Southern.window(s, 1, 5, 15, "west", 3, 3, false, false, PURPLE);
        Southern.window(s, 1, 5, 19, "west", 3, 3, false, false, PURPLE);
        Southern.window(s, 6, 5, 12, "north", 3, 3, false, false, PURPLE);
        // shelves round the walls, the lectern under the dome, two reading tables
        for (int z = 13; z <= 21; z++) {
            Furnish.books(s, 2, z, 2, z, 1, 3);
            if (z != lz) {
                Furnish.books(s, 10, z, 10, z, 1, 3);
            }
        }
        Furnish.books(s, 3, 13, 9, 13, 1, 3);
        for (int x = 3; x <= 9; x++) {
            if (x != lx) {
                Furnish.books(s, x, 21, x, 21, 1, 3);
            }
        }
        Furnish.rug(s, lx - 1, lz - 1, lx + 1, lz + 1, 1, "minecraft:purple_carpet");
        feastTable(s, 4, 15, 4, 19, 1);
        feastTable(s, 8, 15, 8, 19, 1);
        put(s, lx, 1, lz, lectern("south"));
        Furnish.greatChandelier(s, lx, lz, 16, 8);
        for (int[] p : new int[][]{{3, 14}, {9, 14}, {3, 20}, {9, 20}}) {
            lamp(s, p[0], 6, p[1]);
        }
        // ---- the garden court before the tower: x 12..24, z 14..24
        s.fill(16, 0, 14, 18, 0, 23, COTTO);
        Kit.disc(s, cx, 0, 19, 9, COTTO);
        Southern.fountain(s, cx, 1, 19, 5);
        Southern.cypress(s, 14, 1, 15, 10);
        Southern.cypress(s, 20, 1, 15, 10);
        Southern.citrus(s, 14, 1, 22, true);
        Southern.citrus(s, 20, 1, 22, true);
        for (int[] p : new int[][]{{15, 16}, {19, 16}}) {
            Southern.lampPost(s, p[0], 1, p[1]);
        }
        Southern.pergola(s, 22, 14, 24, 23, 1, 3);
        Southern.stoneBench(s, 23, 15, 23, 16, 1, "west");
        Southern.stoneBench(s, 23, 21, 23, 22, 1, "west");
        Southern.stoneBench(s, 12, 18, 12, 20, 1, "east");
        // the wall of the court with its gate
        for (int x = 12; x <= 24; x++) {
            if (x >= cx - 1 && x <= cx + 1) {
                continue;
            }
            boolean pier = x == cx - 2 || x == cx + 2 || x == 24;
            put(s, x, 1, 24, pier ? STONE : RENDER);
            put(s, x, 2, 24, pier ? STONE : RENDER);
            put(s, x, 3, 24, pier ? STONE : Kit.slab(TRIM_SLAB));
            if (pier) {
                put(s, x, 4, 24, Kit.lantern());
            }
        }
        // ---- the herb garden behind the library hall
        s.fill(2, 0, 2, 8, 0, 9, COTTO);
        for (int x = 2; x <= 8; x += 2) {
            Southern.planter(s, x, 1, 3, x % 4 == 0);
            Southern.planter(s, x, 1, 8, x % 4 == 2);
        }
        Southern.cypress(s, 1, 1, 1, 9);
        Southern.cypress(s, 9, 1, 1, 9);
        Southern.citrus(s, 5, 1, 6, true);
        Southern.lampPost(s, 2, 1, 6);
        Southern.lampPost(s, 8, 1, 6);
        Southern.stoneBench(s, 4, 10, 6, 10, 1, "north");
        Southern.lampPost(s, 24, 1, 1);

        // ---- the tower: the ground floor
        focus(s, cx, 1, cz);
        put(s, cx + 2, 1, cz - 2, WORK_POST);
        put(s, cx + 3, 1, cz - 2, Kit.chest("west"));
        Furnish.books(s, cx - 2, cz - 5, cx - 2, cz - 5, 1, 3);
        Furnish.books(s, cx + 2, cz - 5, cx + 2, cz - 5, 1, 3);
        Furnish.bench(s, cx + 5, cz + 1, cx + 5, cz + 2, 1, "west", WOOD);
        Furnish.bench(s, cx - 5, cz + 1, cx - 5, cz + 2, 1, "east", WOOD);
        Southern.planter(s, cx - 3, 1, cz + 4, true);
        Southern.planter(s, cx + 3, 1, cz + 4, true);
        Southern.amphora(s, cx + 4, 1, cz - 3);
        Furnish.chandelier(s, cx, cz, 4, 1);
        lamp(s, cx - 3, 3, cz - 3);
        lamp(s, cx + 3, 3, cz + 3);
        lamp(s, cx - 3, 3, cz + 3);
        // two rooms of two mages
        for (int f = 6; f <= 11; f += 5) {
            Southern.ironBed(s, cx - 2, f, cz - 1, "north", "purple");
            Southern.ironBed(s, cx + 2, f, cz - 1, "north", "purple");
            Furnish.rug(s, cx - 1, cz - 1, cx + 1, cz + 1, f, "minecraft:purple_carpet");
            Furnish.desk(s, cx, f, cz + 5, "north", WOOD);
            Furnish.crate(s, cx - 4, f, cz + 3, WOOD);
            Furnish.crate(s, cx + 4, f, cz + 3, WOOD);
            Furnish.books(s, cx + 5, cz + 2, cx + 5, cz + 2, f, f + 1);
            Furnish.chandelier(s, cx, cz, f + 3, 1);
            lamp(s, cx - 3, f + 2, cz + 3);
            lamp(s, cx + 3, f + 2, cz + 3);
            lamp(s, cx + 3, f + 2, cz - 3);
        }
        // the brewing room
        int f3 = 16;
        for (int[] p : new int[][]{{-2, -4}, {2, -4}, {4, -2}, {-4, 3}}) {
            put(s, cx + p[0], f3, cz + p[1], BREWING);
        }
        put(s, cx - 1, f3, cz - 4, CAULDRON);
        put(s, cx + 1, f3, cz - 4, CAULDRON);
        put(s, cx + 4, f3, cz - 1, Kit.topSlab("minecraft:" + WOOD + "_slab"));
        put(s, cx + 4, f3 + 1, cz - 1, Furnish.candles(3));
        Furnish.table(s, cx, f3, cz + 1, WOOD);
        Furnish.chair(s, cx - 1, f3, cz + 1, "east", WOOD);
        Furnish.chair(s, cx + 1, f3, cz + 1, "west", WOOD);
        Furnish.crate(s, cx + 3, f3, cz + 4, WOOD);
        Furnish.sack(s, cx + 2, f3, cz + 4, false);
        Furnish.sack(s, cx - 2, f3, cz + 4, true);
        Furnish.plant(s, cx - 3, f3, cz + 4, "flowering_azalea");
        Furnish.plant(s, cx + 4, f3, cz + 3, "azalea");
        Furnish.chandelier(s, cx, cz, f3 + 3, 1);
        lamp(s, cx - 3, f3 + 2, cz - 3);
        lamp(s, cx + 3, f3 + 2, cz + 3);
        lamp(s, cx - 3, f3 + 2, cz + 2);
        // the research room and the star chamber
        researchRoom(s, cx, 21, cz);
        Furnish.chandelier(s, cx, cz + 1, 24, 1);
        lamp(s, cx + 3, 23, cz - 3);
        lamp(s, cx - 3, 23, cz + 3);
        starChamber(s, cx, 26, cz, r);
        // the observatory on the terrace
        observatory(s, cx, terrace + 1, cz, 11);
        telescope(s, cx, terrace + 1, cz - 6, "north");
        telescope(s, cx + 6, terrace + 1, cz, "east");
        telescope(s, cx - 6, terrace + 1, cz, "west");
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{cx, 1, cz + r}, null);
    }

    // ---------------------------------------------------------------- parts of the mage towers

    /**
     * The shell of a mage tower: the round tower of diameter d around (cx, cz) with its floors and the terrace at
     * {@code terrace}, and its arched windows of purple glass on the four axes of every storey (on the ground floor two
     * high and none to the south, where the door is), none on the sides in {@code blocked} below the layer
     * {@code blockedBelow}, where an annex stands against the tower (and then without the sloped plinth, which the annex
     * would cut).
     */
    private static void shell(Structure s, int cx, int cz, int d, int terrace, String blocked, int blockedBelow, int... floors) {
        int r = (d - 1) / 2;
        roundTower(s, cx, cz, d, terrace, blocked.isEmpty(), floors);
        terrace(s, cx, cz, d, terrace);
        for (String side : SIDES) {
            if (!side.equals("south") && !(blocked.contains(side) && 2 < blockedBelow)) {
                axisWindow(s, cx, cz, r, 2, side, 2);
            }
            for (int f : floors) {
                if (!(blocked.contains(side) && f + 1 < blockedBelow)) {
                    axisWindow(s, cx, cz, r, f + 1, side, 3);
                }
            }
        }
    }

    /**
     * The ladder of a tower at (x, z) against the wall on its west, from the ground floor up through every floor to the
     * terrace at {@code top}.
     */
    private static void ladder(Structure s, int x, int z, int top) {
        s.fill(x, 1, z, x, top, z, Kit.ladder("east"));
    }

    /**
     * The door of a round tower on its south face, a carved keystone over it; with {@code outside} also lanterns on iron
     * brackets on both sides of it and steps of stone before it.
     */
    private static void towerDoor(Structure s, int cx, int cz, int r, boolean outside) {
        s.door(cx, 1, cz + r, DOOR, "north", "left");
        put(s, cx, 3, cz + r, CARVED);
        if (outside) {
            Southern.wallLantern(s, cx - 1, 2, cz + r + 1);
            Southern.wallLantern(s, cx + 1, 2, cz + r + 1);
            s.fill(cx - 1, 0, cz + r + 1, cx + 1, 0, cz + r + 1, Kit.stairs(STONE_STAIRS, "north"));
        }
    }

    /**
     * A round tower around (cx, cz) of an odd diameter {@code d}: a footing of dressed stone with a floor of cotto at layer
     * 0, a wall of render from layer 1 (a plinth course of dressed stone) up to the layer {@code top}, empty inside, a
     * band of trim and a floor of beams and planks at every layer of {@code floors} and at the top.
     */
    private static void roundTower(Structure s, int cx, int cz, int d, int top, boolean talus, int... floors) {
        int r = (d - 1) / 2;
        Kit.disc(s, cx, 0, cz, d + 2, STONE);
        floorIn(s, cx, 0, cz, d, COTTO);
        Kit.tube(s, cx, 1, top, cz, d, RENDER);
        Kit.tube(s, cx, 1, 1, cz, d, STONE);
        for (int y = 1; y < top; y++) {
            floorIn(s, cx, y, cz, d, AIR);
        }
        // lesenes of dressed stone on the four diagonals, from the plinth to the terrace
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Kit.onRim(dx, dz, d) && Math.min(Math.abs(dx), Math.abs(dz)) * 5 >= r * 3) {
                    s.fill(cx + dx, 2, cz + dz, cx + dx, top - 1, cz + dz, STONE);
                }
                if (talus && Kit.inDisc(dx, dz, d + 2) && !Kit.inDisc(dx, dz, d) && !s.solid(cx + dx, 1, cz + dz)) {
                    put(s, cx + dx, 1, cz + dz, roundStair(STONE_STAIRS, "bottom", dx, dz, false));
                }
            }
        }
        for (int f : floors) {
            Kit.tube(s, cx, f, f, cz, d, TRIM);
            beamFloor(s, cx, f, cz, d);
        }
        Kit.tube(s, cx, top, top, cz, d, TRIM);
        beamFloor(s, cx, top, cz, d);
    }

    /**
     * Fills the inside of a round wall of diameter d at layer y (every cell of the plan that is not on its rim).
     */
    private static void floorIn(Structure s, int cx, int y, int cz, int d, String state) {
        int r = (d - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Kit.inDisc(dx, dz, d) && !Kit.onRim(dx, dz, d)) {
                    put(s, cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /**
     * A floor of beams every second line along z with planks between them inside a round wall of diameter d.
     */
    private static void beamFloor(Structure s, int cx, int y, int cz, int d) {
        int r = (d - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Kit.inDisc(dx, dz, d) && !Kit.onRim(dx, dz, d)) {
                    put(s, cx + dx, y, cz + dz, Math.floorMod(dx, 2) == 0 ? Kit.log(BEAM, "z") : PLANKS);
                }
            }
        }
    }

    /**
     * The terrace on the top of a round tower of diameter d whose last floor is at {@code top}: the floor of cotto
     * reaching one block beyond the wall, carried on a ring of corbels of trim (a continuous course of upside-down stairs
     * and a second course under every other cell) over a frieze of blue glazed tiles round the wall, a parapet of render
     * capped with slabs of trim on its rim, lanterns standing on the parapet on the diagonals.
     */
    private static void terrace(Structure s, int cx, int cz, int d, int top) {
        int big = d + 2;
        int r = (big - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (!Kit.inDisc(dx, dz, big)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                boolean outside = !Kit.inDisc(dx, dz, d);
                if (Kit.onRim(dx, dz, d) && s.get(x, top - 2, z).equals(RENDER)) {
                    put(s, x, top - 2, z, AZULEJO + "[facing=" + SIDES[Math.floorMod(dx + 2 * dz, 4)] + "]");
                }
                if (outside) {
                    put(s, x, top, z, TRIM);
                    put(s, x, top - 1, z, roundStair(TRIM_STAIRS, "top", dx, dz, false));
                    if (Math.floorMod(dx + dz, 2) == 0) {
                        put(s, x, top - 2, z, roundStair(TRIM_STAIRS, "top", dx, dz, false));
                    }
                } else if (!Kit.onRim(dx, dz, d)) {
                    put(s, x, top, z, COTTO);
                }
                if (Kit.onRim(dx, dz, big)) {
                    put(s, x, top + 1, z, RENDER);
                    put(s, x, top + 2, z, Kit.slab(TRIM_SLAB));
                }
            }
        }
        for (int[] p : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            for (int k = r; k > 0; k--) {
                int x = cx + p[0] * k;
                int z = cz + p[1] * k;
                if (s.inside(x, top + 1, z) && s.get(x, top + 1, z).equals(RENDER)) {
                    put(s, x, top + 2, z, Kit.lantern());
                    break;
                }
            }
        }
    }

    /**
     * An arched window three wide of purple glass, {@code height} high, on the flat face of a round wall of radius r that
     * looks {@code side}, its lowest panes at layer y.
     */
    private static void axisWindow(Structure s, int cx, int cz, int r, int y, String side, int height) {
        int[] d = Furnish.step(side);
        Southern.window(s, cx + d[0] * r, y, cz + d[1] * r, side, 3, height, false, false, PURPLE);
    }

    /**
     * Shelves of books {@code h} high round the inside of a round wall of diameter d at layer y, on every cell against
     * the wall but those before the windows on the four axes and those already taken.
     */
    private static void shelvesRound(Structure s, int cx, int y, int cz, int d, int h) {
        int r = (d - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (!Kit.inDisc(dx, dz, d) || Kit.onRim(dx, dz, d) || Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
                    continue;
                }
                boolean against = Kit.onRim(dx + 1, dz, d) || Kit.onRim(dx - 1, dz, d) || Kit.onRim(dx, dz + 1, d)
                        || Kit.onRim(dx, dz - 1, d);
                if (against && !s.solid(cx + dx, y, cz + dz)) {
                    Furnish.books(s, cx + dx, cz + dz, cx + dx, cz + dz, y, y + h - 1);
                }
            }
        }
    }

    /**
     * The magic focus at (x, y, z) on a round rug of blue carpet.
     */
    private static void focus(Structure s, int x, int y, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                put(s, x + dx, y, z + dz, "minecraft:blue_carpet");
            }
        }
        put(s, x, y, z, FOCUS);
    }

    /**
     * The research room on a round floor where one stands at layer y around (cx, cz): the enchanting table in the middle,
     * shelves of books round it two blocks away, a desk with a chair, a globe of amethyst on a post, a table of star charts.
     */
    private static void researchRoom(Structure s, int cx, int y, int cz) {
        put(s, cx, y, cz, ENCHANTING);
        for (int[] p : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}, {0, -3}, {3, 0}}) {
            Furnish.books(s, cx + p[0], cz + p[1], cx + p[0], cz + p[1], y, y + 1);
        }
        Furnish.desk(s, cx - 1, y, cz + 3, "north", WOOD);
        put(s, cx + 1, y, cz + 3, CARTOGRAPHY);
        put(s, cx - 3, y, cz, FENCE);
        put(s, cx - 3, y + 1, cz, AMETHYST);
        put(s, cx + 1, y, cz - 2, Kit.topSlab("minecraft:" + WOOD + "_slab"));
        put(s, cx + 1, y + 1, cz - 2, Furnish.candles(3));
    }

    /**
     * The star chamber in the top storey of a tower of radius r where one stands at layer y: a telescope at each of its
     * windows, a table of star charts, a globe of amethyst on a post, shelves of books between the windows, a chandelier.
     */
    private static void starChamber(Structure s, int cx, int y, int cz, int r) {
        int k = r - 1;
        telescope(s, cx, y, cz - k, "north");
        telescope(s, cx + k, y, cz, "east");
        telescope(s, cx - k + 1, y, cz, "west");
        telescope(s, cx, y, cz + k, "south");
        put(s, cx, y, cz, CARTOGRAPHY);
        put(s, cx + 1, y, cz + 1, FENCE);
        put(s, cx + 1, y + 1, cz + 1, AMETHYST);
        put(s, cx - 1, y, cz - 1, Kit.topSlab("minecraft:" + WOOD + "_slab"));
        put(s, cx - 1, y + 1, cz - 1, Furnish.candles(3));
        shelvesRound(s, cx, y, cz, 2 * r + 1, 2);
        Furnish.chandelier(s, cx, cz, y + 3, 1);
        lamp(s, cx + 2, y + 2, cz - 2);
        lamp(s, cx - 2, y + 2, cz + 2);
    }

    /**
     * The observatory in the middle of a terrace where one stands at layer y: a round drum of render four high and two
     * narrower than the dome of diameter {@code d}, with its windows and a door to the south, under the dome of blue
     * tiles; inside the great telescope looking up, a table of star charts and a globe of amethyst, a lantern hanging in
     * the dome.
     */
    private static void observatory(Structure s, int cx, int y, int cz, int d) {
        int drum = 4;
        dome(s, cx, y, cz, d, drum, MAJOLICA);
        int r = (d - 3) / 2;
        s.door(cx, y, cz + r, DOOR, "north", "left");
        put(s, cx, y, cz, FENCE);
        put(s, cx, y + 1, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        put(s, cx - 1, y, cz - 1, CARTOGRAPHY);
        put(s, cx + 1, y, cz - 1, FENCE);
        put(s, cx + 1, y + 1, cz - 1, AMETHYST);
        int inner = d >= 11 ? 5 : d >= 9 ? 3 : 2;
        Furnish.chandelier(s, cx, cz, y + drum + inner, inner + 1);
    }

    /**
     * A telescope on its stand looking out toward {@code facing}: a post with the tube of a lightning rod lying on it.
     */
    private static void telescope(Structure s, int x, int y, int z, String facing) {
        put(s, x, y, z, FENCE);
        put(s, x, y + 1, z, "minecraft:lightning_rod[facing=" + facing + ",powered=false,waterlogged=false]");
    }

    /**
     * A dome around (cx, cz) of an odd diameter (5 to 11) of the given roof, the shape of {@link Southern#dome} in other
     * tiles: with a {@code drum} a round drum of render two narrower from layer y with windows on its axes, a band of trim
     * and a cornice flaring out to the dome; then the stepped hemisphere, hollow and open below, and a finial on it.
     *
     * @return the layer above the finial
     */
    private static int dome(Structure s, int cx, int y, int cz, int diameter, int drum, Kit.Roof roof) {
        int r = (diameter - 1) / 2;
        int layer = y;
        if (drum > 0) {
            int dr = r - 1;
            Kit.tube(s, cx, y, y + drum - 1, cz, diameter - 2, RENDER);
            int w1 = drum >= 3 ? y + drum - 3 : y;
            for (int yy = w1; yy <= y + drum - 2 || yy == w1; yy++) {
                put(s, cx + dr, yy, cz, PURPLE);
                put(s, cx - dr, yy, cz, PURPLE);
                put(s, cx, yy, cz + dr, PURPLE);
                put(s, cx, yy, cz - dr, PURPLE);
            }
            Kit.tube(s, cx, y + drum, y + drum, cz, diameter - 2, TRIM);
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (inRound(dx, dz, r) && !Kit.inDisc(dx, dz, diameter - 2)) {
                        put(s, cx + dx, y + drum, cz + dz, roundStair(TRIM_STAIRS, "top", dx, dz, false));
                    }
                }
            }
            layer = y + drum + 1;
        }
        int[] radii = domeRadii(r);
        for (int i = 0; i < radii.length; i++) {
            int ri = radii[i];
            int next = i + 1 < radii.length ? radii[i + 1] : -1;
            for (int dz = -ri; dz <= ri; dz++) {
                for (int dx = -ri; dx <= ri; dx++) {
                    if (!inRound(dx, dz, ri)) {
                        continue;
                    }
                    String state;
                    if (!inRound(dx, dz, next)) {
                        state = dx == 0 && dz == 0 ? roof.block() : roundStair(roof.stairs(), "bottom", dx, dz, false);
                    } else if (inRound(dx + 1, dz, next) && inRound(dx - 1, dz, next) && inRound(dx, dz + 1, next)
                            && inRound(dx, dz - 1, next)) {
                        continue;
                    } else {
                        state = roof.block();
                    }
                    put(s, cx + dx, layer + i, cz + dz, state);
                }
            }
        }
        return Southern.finial(s, cx, layer + radii.length, cz);
    }

    /** Whether a column lies in a round plan of the given radius (a single block for 0, nothing below). */
    private static boolean inRound(int dx, int dz, int r) {
        return r >= 0 && dx * dx + dz * dz <= r * r + r / 2;
    }

    /** The radii of the layers of a dome of a radius from the bottom up, closed with a single block. */
    private static int[] domeRadii(int r) {
        if (r <= 2) {
            return r == 2 ? new int[]{2, 1, 0} : new int[]{1, 0};
        }
        List<Integer> list = new ArrayList<>();
        double edge = (r + 0.5) * (r + 0.5);
        for (int i = 0; edge - (i + 0.5) * (i + 0.5) >= 1; i++) {
            list.add((int) Math.floor(Math.sqrt(edge - (i + 0.5) * (i + 0.5))));
        }
        while (list.get(list.size() - 1) > 2) {
            list.add(list.get(list.size() - 1) - 1);
        }
        list.add(0);
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Stairs on a round rim around its middle (dx, dz from it) with their high part towards the middle; on the diagonals a
     * quarter (or three quarters with {@code inner}) towards it.
     */
    private static String roundStair(String block, String half, int dx, int dz, boolean inner) {
        String toX = dx > 0 ? "west" : "east";
        String toZ = dz > 0 ? "north" : "south";
        if (Math.abs(dx) == Math.abs(dz) && dx != 0) {
            return Southern.stairs(block, toZ, half, (inner ? "inner_" : "outer_") + (Furnish.left(toZ).equals(toX) ? "left" : "right"));
        }
        return Southern.stairs(block, Math.abs(dx) > Math.abs(dz) ? toX : toZ, half, "straight");
    }

    // ---------------------------------------------------------------- shared

    /**
     * The plot: paving of rough stone over the whole of layer 0 and a step of its stairs along the front edge.
     */
    private static void plot(Structure s) {
        s.fill(0, 0, 0, s.sizeX - 1, 0, s.sizeZ - 1, PAVING);
        s.fill(0, 0, s.sizeZ - 1, s.sizeX - 1, 0, s.sizeZ - 1, Kit.stairs(PAVING_STAIRS, "north"));
    }

    /**
     * The footing of a block: dressed stone under its walls and a floor of cotto inside, at layer 0.
     */
    private static void footing(Structure s, int x1, int z1, int x2, int z2) {
        s.fill(x1, 0, z1, x2, 0, z2, STONE);
        s.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, COTTO);
    }

    /**
     * A lantern hanging at (x, y, z) on a chain up to the first solid block over it.
     */
    private static void lamp(Structure s, int x, int y, int z) {
        put(s, x, y, z, Kit.hangingLantern());
        for (int yy = y + 1; yy < s.sizeY && !s.solid(x, yy, z); yy++) {
            s.set(x, yy, z, Kit.chain());
        }
    }

    /** Puts a block where the position lies in the box. */
    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }
}
