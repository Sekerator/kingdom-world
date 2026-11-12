package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The two guilds of a capital in the style "eastern", each a program drawn for the rank of its kingdom, from B for a weak
 * one to SS for a strong one.
 *
 * <p>The adventurers' guild is a hall of warriors in the manner of a training hall: a hall of dark posts and white plaster
 * on a platform of stone, its great curving roof with swept corners and horned ridge, red pillars before it. Inside the
 * great hall with its tables and benches, a brazier, the notice board of contracts under its little roof, the counter of the
 * registrar with the guild desk, his post and the chest of the guild, trophies of arms, and upstairs the map room of the
 * guild. The rank grows it: B a hall with its loft; A a hall with two roofs, a veranda of red pillars and a roofed gate porch;
 * S a greater hall behind a drum tower that stands over the gate passage; SS a walled compound entered through a gate under
 * a heavy roof, a pagoda on the axis of its court, a drum tower and a training yard beside it and the great hall at the
 * back, one of the main buildings of a capital after the castle.
 *
 * <p>The mages' guild lives in a pagoda of sages: storeys of white walls between red posts, each under its own curving
 * roof, a red balcony round every upper storey and a spire of rings on the top. On its ground floor the magic focus with the
 * post of the mage and its chest; books on every storey, a brewing room, a research room with the enchanting table and real
 * beds for the mages: one in B, two in A, three in S and four in SS. B has three storeys, A four, S five and a library wing,
 * SS seven storeys over a court with two open pavilions.
 *
 * <p>Drawn with the shapes of {@link Eastern}; coordinates as everywhere: x from west to east, z from the back to the front
 * (the entrance looks south), y up; layer 0 stands on the ground and is the street.
 */
final class EasternGuilds {
    /** The rank of a guild: B for a weak kingdom, A, S, SS for the strongest. */
    enum Rank {
        B, A, S, SS
    }

    private static final String AIR = Structure.AIR;
    private static final String DESK = "kingdomworld:guild_desk[facing=south]";
    private static final String WORK_POST = "kingdomworld:work_post";
    private static final String FOCUS = "kingdomworld:magic_focus";
    private static final String CARTOGRAPHY = "minecraft:cartography_table";
    private static final String ENCHANTING = "minecraft:enchanting_table";
    private static final String BREWING = "minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]";
    private static final String CAULDRON = "minecraft:water_cauldron[level=3]";
    private static final String LECTERN = "minecraft:lectern[facing=south,has_book=false,powered=false]";
    private static final String AMETHYST = "minecraft:amethyst_block";
    private static final String BOOKS = "minecraft:bookshelf";
    /** Violet glass in the windows of the top storey of a mage pagoda. */
    private static final String VIOLET = Kit.stainedPane("purple");
    /** An upright post of the timber. */
    private static final String UP = Kit.log(Eastern.POST, "y");
    /** The guild's own colour on its banners, the same in every kingdom. */
    private static final String GUILD = "black";
    /**
     * The pitch of the main roofs of the halls: gentle (see {@link Eastern#heights}), so that the white walls and the red
     * pillars keep their share of the front under the great roof.
     */
    private static final int HALL_PITCH = 0;

    /**
     * A drawn building with its named places: the door, and the guards by the desk.
     */
    private record Drawn(Structure structure, int[] door, List<int[]> guards) {
    }

    private EasternGuilds() {
    }

    /**
     * The adventurers' guilds eastern_guild_b, _a, _s and _ss (trade, type guild, with their door and the guard by the desk)
     * and the mage towers eastern_mage_tower_b, _a, _s and _ss (production, role mage_tower, with their door), all standing
     * on the ground with their anchor in the middle of their front edge.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Rank rank : Rank.values()) {
            String key = rank.name().toLowerCase(Locale.ROOT);
            Drawn guild = guild(rank);
            Structure g = guild.structure();
            BuildingGenerator.Building b = new BuildingGenerator.Building("eastern_guild_" + key, "trade", "guild", "kingdomworld:guild_desk",
                    (g.sizeX - 1) / 2, 0, g.sizeZ - 1, g, "none", 1, "")
                    .at("door", guild.door()[0], guild.door()[1], guild.door()[2]);
            for (int[] p : guild.guards()) {
                b = b.at("guard", p[0], p[1], p[2]);
            }
            list.add(b);
        }
        for (Rank rank : Rank.values()) {
            String key = rank.name().toLowerCase(Locale.ROOT);
            Drawn tower = mageTower(rank);
            Structure t = tower.structure();
            list.add(new BuildingGenerator.Building("eastern_mage_tower_" + key, "production", "mage_tower", "kingdomworld:magic_focus",
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
     * Rank B: a hall of dark posts and white plaster on a low platform of stone under a hip-and-gable roof whose ridge runs
     * along the street and whose white gables look to the sides, four red pillars carrying the front eave over a short
     * flight of steps, the black banners of the guild on their poles and stone lanterns beside the steps. Inside the great
     * hall: the counter of the registrar with the desk, his post and the chest of the guild before the trophies on the back
     * wall, a long table with benches, the notice board of contracts, a rack of arms, two braziers; a ladder climbs to the
     * loft under the roof, the map room of the guild with its map, books and lectern.
     */
    private static Drawn guildB() {
        Structure s = new Structure(13, 18, 15);
        int cx = 6;
        int x1 = 2;
        int x2 = 10;
        int z1 = 2;
        int z2 = 10;
        int floor = 1;
        int stand = 2;
        int head = 7;
        Eastern.platform(s, 1, 1, 11, 11, 0, floor, 0, null);
        Eastern.steps(s, cx, 12, "south", 3, floor, 0, false);
        Eastern.frame(s, x1, z1, x2, z2, floor, head, 4, UP, Eastern.PLASTER);
        fill(s, x1 + 1, floor, z1 + 1, x2 - 1, floor, z2 - 1, Eastern.FLOOR);
        fill(s, x1 + 1, head, z1 + 1, x2 - 1, head, z2 - 1, Eastern.PLANKS);
        frontWall(s, x1, x2, z2, cx, stand, head);
        // windows of paper in the middles of the panels of the sides and the back
        Eastern.window(s, x1, stand + 2, 4, "west", 1, 2);
        Eastern.window(s, x2, stand + 2, 4, "east", 1, 2);
        Eastern.window(s, 4, stand + 2, z1, "north", 1, 2);
        Eastern.window(s, 8, stand + 2, z1, "north", 1, 2);
        // the red pillars of the front eave, red railings between the outer ones, cloth lanterns
        for (int x : new int[]{2, 4, 8, 10}) {
            Eastern.pillar(s, x, stand, head - 1, z2 + 1, false);
        }
        Eastern.railing(s, 3, z2 + 1, 3, z2 + 1, stand, true);
        Eastern.railing(s, 9, z2 + 1, 9, z2 + 1, stand, true);
        Eastern.roof(s, 0, 0, 12, 12, head, 2, HALL_PITCH, 2, Eastern.ROOF, Eastern.PLASTER);
        Eastern.clothLantern(s, 3, z2 + 1, head - 1, 0);
        Eastern.clothLantern(s, 9, z2 + 1, head - 1, 0);
        // banners of the guild on poles and small stone lanterns beside the steps
        bannerPole(s, 0, 0, 13, 5);
        bannerPole(s, 12, 0, 13, 5);
        Eastern.stoneLantern(s, 2, 0, 13, false);
        Eastern.stoneLantern(s, 10, 0, 13, false);

        // the great hall
        registrar(s, cx, stand, 5, 4, 8, new int[]{5, 3}, new int[]{4, 3});
        trophies(s, 7, stand, z1 + 1, "south", 2);
        banner(s, cx, stand + 3, z1 + 1, "south");
        Furnish.longTable(s, cx, 7, cx, 8, stand, Eastern.WOOD);
        Furnish.bench(s, cx - 1, 7, cx - 1, 8, stand, "east", Eastern.WOOD);
        Furnish.bench(s, cx + 1, 7, cx + 1, 8, stand, "west", Eastern.WOOD);
        put(s, cx, stand + 1, 8, Furnish.candles(3));
        noticeBoard(s, x1 + 1, stand, 8, "east", 3);
        armsRack(s, x2 - 1, stand, 7, "west", 2);
        brazier(s, x1 + 1, stand, z2 - 1);
        brazier(s, x2 - 1, stand, z2 - 1);
        lamp(s, 4, head - 1, 4);
        lamp(s, 8, head - 1, 4);
        lamp(s, 4, head - 1, 8);
        lamp(s, 8, head - 1, 8);
        ladder(s, x2 - 1, stand, 6, "west", head);

        // the map room in the loft under the roof
        int loft = head + 1;
        Eastern.tatami(s, x1 + 1, 4, x2 - 2, 8, head, true);
        put(s, cx, loft, 4, LECTERN);
        Furnish.books(s, 4, 4, 5, 4, loft, loft + 1);
        Furnish.books(s, 7, 4, 8, 4, loft, loft + 1);
        guildMapZ(s, x1 + 1, loft + 1, 5, 3);
        mapTable(s, cx, loft, 6);
        Eastern.paperLamp(s, 3, loft, 8);
        Eastern.cabinet(s, 8, loft, 8, 1);
        lamp(s, 4, loft + 3, 6);
        lamp(s, 8, loft + 3, 6);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{cx, stand, z2 + 1}, List.of(new int[]{cx - 1, stand, 6}));
    }

    /**
     * Rank A: a hall with two roofs on a higher platform: a veranda of red pillars with brackets runs round it under the
     * lower roof, the upper hall rises over it under a hip-and-gable roof with sweeping corners, and a gate porch on four
     * red pillars stands before the door, its own gable roof raised over the steps and hung with the banner of the guild;
     * great stone lanterns beside the steps. Inside the great hall: the counter with the desk, the post and the chest, the
     * trophies and a shrine of the guild on the back wall, two long tables with benches, the notice board of contracts, a
     * rack of arms, braziers; a stair along the west wall climbs to the upper hall, the map room of the guild with its
     * lectern, map and books, and the chamber of the master with a bed of state behind a folding screen.
     */
    private static Drawn guildA() {
        Structure s = new Structure(17, 25, 19);
        int cx = 8;
        int x1 = 3;
        int x2 = 13;
        int z1 = 3;
        int z2 = 11;
        int floor = 2;
        int stand = 3;
        int low = 8;
        int high = 13;
        Eastern.platform(s, 1, 1, 15, 14, 0, floor, 0, null);
        Eastern.steps(s, cx, 15, "south", 3, floor, 0, false);
        greatHall(s, x1, z1, x2, z2, floor, low, high, HALL_PITCH, new int[]{2, 6, 10, 14}, new int[]{2, 7, 12},
                new int[]{2, 5, 8, 11, 14});
        gatePorch(s, cx, z2, stand, low, high, 15, 18);
        Eastern.stoneLantern(s, 2, 0, 16, true);
        Eastern.stoneLantern(s, 14, 0, 16, true);

        // the great hall
        hallStairs(s, x1 + 1, stand, z2 - 1, low);
        registrar(s, cx, stand, 6, 6, 10, new int[]{7, 4}, new int[]{6, 4});
        trophies(s, 9, stand, z1 + 1, "south", 3);
        banner(s, cx, stand + 2, z1 + 1, "south");
        Eastern.shrine(s, 5, stand, z1 + 1, "south");
        for (int x : new int[]{6, 10}) {
            Furnish.longTable(s, x, 8, x, 9, stand, Eastern.WOOD);
            Furnish.bench(s, x - 1, 8, x - 1, 9, stand, "east", Eastern.WOOD);
            Furnish.bench(s, x + 1, 8, x + 1, 9, stand, "west", Eastern.WOOD);
            put(s, x, stand + 1, 8, Furnish.candles(2));
        }
        noticeBoard(s, x2 - 1, stand, 5, "west", 3);
        armsRack(s, x2 - 1, stand, 8, "west", 2);
        brazier(s, x1 + 2, stand, z2 - 1);
        brazier(s, x2 - 1, stand, z2 - 1);
        for (int[] p : new int[][]{{6, 7}, {10, 7}, {cx, 5}, {cx, 10}}) {
            lamp(s, p[0], low - 1, p[1]);
        }

        // the upper hall: the map room and the chamber of the master
        int up = low + 1;
        mats(s, x1 + 2, z1 + 1, x2 - 1, z2 - 1, low);
        put(s, 6, up, z1 + 2, LECTERN);
        Furnish.books(s, 6, z1 + 1, 7, z1 + 1, up, up + 1);
        guildMapZ(s, x1 + 1, up + 1, 6, 3);
        mapTable(s, 7, up, 7);
        Eastern.paperLamp(s, 8, up, z2 - 1);
        Eastern.foldingScreen(s, 9, z1 + 1, 9, z1 + 3, up);
        Furnish.canopyBed(s, 11, up, 7, "north", Eastern.WOOD);
        Eastern.alcove(s, x2 - 1, up, z2 - 1, "west");
        Eastern.cabinet(s, 10, up, z2 - 1, 1);
        for (int[] p : new int[][]{{7, 6}, {11, 9}, {6, 9}}) {
            lamp(s, p[0], high - 1, p[1]);
        }
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{cx, stand, z2 + 1}, List.of(new int[]{cx - 1, stand, 7}));
    }

    /**
     * Rank S: a greater hall with two roofs behind a drum tower that stands over the gate passage: a battered base of stone
     * pierced by a vaulted passage whose stair climbs to the landing before the hall, and on it a pavilion of two storeys,
     * red pillars and a red balcony round it, under a pyramid roof with a spire of rings; the drum stands in its upper room.
     * The hall is as that of rank A but wider, its veranda of red pillars all round. Inside: the counter with the desk, the
     * post and the chest, trophies and a shrine on the back wall, two long tables with benches, two braziers, the notice
     * board and a rack of arms; upstairs the map room, a council table with cushions and the chamber of the master.
     */
    private static Drawn guildS() {
        Structure s = new Structure(23, 28, 25);
        int cx = 11;
        int x1 = 3;
        int x2 = 19;
        int z1 = 3;
        int z2 = 11;
        int floor = 2;
        int stand = 3;
        int low = 8;
        int high = 13;
        Eastern.platform(s, 1, 1, 21, 15, 0, floor, 0, null);
        greatHall(s, x1, z1, x2, z2, floor, low, high, HALL_PITCH, new int[]{2, 6, 9, 13, 16, 20}, new int[]{2, 7, 12},
                new int[]{2, 6, 9, 13, 16, 20});
        drumGate(s, cx, 16, 24);
        Eastern.stoneLantern(s, 5, stand, 14, false);
        Eastern.stoneLantern(s, 17, stand, 14, false);

        // the great hall
        hallStairs(s, x1 + 1, stand, z2 - 1, low);
        registrar(s, cx, stand, 6, 8, 14, new int[]{10, 4}, new int[]{9, 4});
        trophies(s, 12, stand, z1 + 1, "south", 5);
        banner(s, cx, stand + 2, z1 + 1, "south");
        Eastern.shrine(s, 6, stand, z1 + 1, "south");
        for (int x : new int[]{7, 15}) {
            Furnish.longTable(s, x, 8, x, 9, stand, Eastern.WOOD);
            Furnish.bench(s, x - 1, 8, x - 1, 9, stand, "east", Eastern.WOOD);
            Furnish.bench(s, x + 1, 8, x + 1, 9, stand, "west", Eastern.WOOD);
            put(s, x, stand + 1, 9, Furnish.candles(3));
        }
        brazier(s, cx - 1, stand, 9);
        brazier(s, cx + 1, stand, 9);
        noticeBoard(s, x2 - 1, stand, 5, "west", 3);
        armsRack(s, x2 - 1, stand, 8, "west", 3);
        for (int[] p : new int[][]{{7, 6}, {15, 6}, {7, 10}, {15, 10}, {cx, 8}}) {
            lamp(s, p[0], low - 1, p[1]);
        }

        // the upper hall: the map room, the council and the chamber of the master
        int up = low + 1;
        mats(s, x1 + 2, z1 + 1, x2 - 1, z2 - 1, low);
        put(s, 7, up, z1 + 2, LECTERN);
        Furnish.books(s, 6, z1 + 1, 8, z1 + 1, up, up + 1);
        guildMapZ(s, x1 + 1, up + 1, 6, 3);
        mapTable(s, 7, up, 7);
        Eastern.lowTable(s, 10, 6, 12, 7, up);
        Eastern.paperLamp(s, 11, up, z1 + 1);
        Eastern.foldingScreen(s, 13, z1 + 1, 13, z1 + 3, up);
        Furnish.canopyBed(s, 16, up, 7, "north", Eastern.WOOD);
        Eastern.alcove(s, x2 - 1, up, z2 - 1, "west");
        Eastern.cabinet(s, 14, up, z2 - 1, 2);
        Eastern.cabinet(s, x2 - 1, up, z1 + 1, 1);
        for (int[] p : new int[][]{{7, 6}, {cx, 9}, {16, 9}}) {
            lamp(s, p[0], high - 1, p[1]);
        }
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{cx, stand, z2 + 1}, List.of(new int[]{cx - 1, stand, 7}));
    }

    /**
     * Rank SS: the compound of the guild behind a white wall with a roof of tiles. At the front the gate under its heavy
     * roof; in the court before it a pagoda of five storeys stands on the axis, the shrine of the guild in its ground storey;
     * to the east a drum tower on a base of stone with a stair up its side, to the west the training yard with straw men,
     * targets, a rack of spears and a pine. At the back on its platform the great hall with two roofs and
     * a veranda of red pillars all round, great stone lanterns before its steps. Inside: the counter with the desk, the post
     * and the chest, trophies and a shrine, four long tables with benches under a great chandelier, braziers, the notice
     * board, racks of arms; upstairs the map room, the council table and the chamber of the master.
     */
    private static Drawn guildSS() {
        Structure s = new Structure(31, 38, 33);
        int cx = 15;
        int x1 = 5;
        int x2 = 25;
        int z1 = 3;
        int z2 = 11;
        int floor = 2;
        int stand = 3;
        int low = 8;
        int high = 14;
        // the court: gravel within the wall, paths of stone
        fill(s, 1, 0, 1, 29, 0, 28, Eastern.GRAVEL);
        Eastern.gardenWall(s, 0, 0, 30, 29, 0, 3, true, 4);
        fill(s, 13, 0, 25, 17, 0, 28, Eastern.STONE);
        fill(s, 9, 0, 14, 21, 0, 15, Eastern.STONE);
        fill(s, 9, 0, 16, 10, 0, 26, Eastern.STONE);
        fill(s, 20, 0, 16, 21, 0, 26, Eastern.STONE);
        fill(s, 9, 0, 26, 21, 0, 26, Eastern.STONE);

        // the great hall
        Eastern.platform(s, 3, 1, 27, 13, 0, floor, 0, null);
        Eastern.steps(s, cx, 14, "south", 7, floor, 1, true);
        int[] row = {4, 7, 10, 13, 17, 20, 23, 26};
        greatHall(s, x1, z1, x2, z2, floor, low, high, HALL_PITCH, row, new int[]{2, 7, 12}, row);
        Eastern.stoneLantern(s, 8, 1, 15, true);
        Eastern.stoneLantern(s, 22, 1, 15, true);

        // the pagoda on the axis, the drum tower, the training yard, the gate
        Eastern.platform(s, 11, 16, 19, 24, 0, 1, 0, null);
        Eastern.steps(s, cx, 25, "south", 3, 1, 1, false);
        int[] f = pagoda(s, cx, 20, 1, new int[]{3, 3, 2, 2, 2}, new int[]{6, 4, 4, 4, 4}, new int[]{2, 2, 2, 2, 2}, 6);
        guildShrine(s, cx, f[0] + 1, 20);
        drumTower(s, 25, 20);
        trainingYard(s);
        fill(s, 13, 0, 28, 17, 6, 30, AIR);
        gateHouse(s, cx, 1, 29, 2, 6);
        for (int x = 13; x <= 17; x++) {
            put(s, x, 0, 31, Kit.stairs(Eastern.STONE_STAIRS, "north"));
        }
        Eastern.stoneLantern(s, 12, 1, 27, false);
        Eastern.stoneLantern(s, 18, 1, 27, false);
        Eastern.stoneLantern(s, 2, 1, 4, false);
        Eastern.stoneLantern(s, 28, 1, 4, false);
        Eastern.stoneLantern(s, 28, 1, 25, false);
        Eastern.pine(s, 27, 1, 27, 5, "west");

        // the great hall inside
        hallStairs(s, x1 + 1, stand, z2 - 1, low);
        registrar(s, cx, stand, 6, 12, 18, new int[]{14, 4}, new int[]{13, 4});
        trophies(s, 16, stand, z1 + 1, "south", 7);
        banner(s, cx, stand + 2, z1 + 1, "south");
        Eastern.shrine(s, 9, stand, z1 + 1, "south");
        Eastern.shrine(s, 11, stand, z1 + 1, "south");
        for (int x : new int[]{9, 12, 18, 21}) {
            Furnish.longTable(s, x, 8, x, 9, stand, Eastern.WOOD);
            Furnish.bench(s, x - 1, 8, x - 1, 9, stand, "east", Eastern.WOOD);
            Furnish.bench(s, x + 1, 8, x + 1, 9, stand, "west", Eastern.WOOD);
            put(s, x, stand + 1, 9, Furnish.candles(3));
        }
        Furnish.greatChandelier(s, cx, 8, low - 1, 1);
        brazier(s, x1 + 2, stand, z2 - 1);
        brazier(s, x2 - 1, stand, z2 - 1);
        noticeBoard(s, x2 - 1, stand, 6, "west", 3);
        armsRack(s, x2 - 1, stand, 9, "west", 1);
        armsRack(s, x2 - 1, stand, 4, "west", 2);
        for (int[] p : new int[][]{{9, 6}, {21, 6}, {9, 10}, {21, 10}, {12, 5}, {18, 5}}) {
            lamp(s, p[0], low - 1, p[1]);
        }

        // the upper hall: the map room, the council and the chamber of the master
        int up = low + 1;
        mats(s, x1 + 2, z1 + 1, x2 - 1, z2 - 1, low);
        put(s, 10, up, z1 + 2, LECTERN);
        Furnish.books(s, 8, z1 + 1, 9, z1 + 1, up, up + 1);
        Furnish.books(s, 12, z1 + 1, 13, z1 + 1, up, up + 1);
        guildMapZ(s, x1 + 1, up + 1, 6, 3);
        mapTable(s, 10, up, 7);
        Eastern.lowTable(s, 14, 6, 16, 8, up);
        Eastern.foldingScreen(s, 19, z1 + 1, 19, z1 + 3, up);
        Furnish.canopyBed(s, 22, up, 7, "north", Eastern.WOOD);
        Eastern.alcove(s, x2 - 1, up, z2 - 1, "west");
        Eastern.cabinet(s, x2 - 1, up, z1 + 1, 1);
        Eastern.cabinet(s, 20, up, z2 - 1, 2);
        Eastern.paperLamp(s, 15, up, z1 + 1);
        for (int[] p : new int[][]{{10, 6}, {cx, 9}, {22, 9}, {18, 7}}) {
            lamp(s, p[0], high - 1, p[1]);
        }
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{cx, stand, z2 + 1}, List.of(new int[]{cx - 1, stand, 7}));
    }

    // ---------------------------------------------------------------- parts of the compounds

    /**
     * A great hall with two roofs on the rectangle of its walls {@code x1..x2, z1..z2} (the door in the middle of the
     * front), standing on the floor at layer {@code floor}: the lower hall of dark posts and white plaster up to its head
     * beam at {@code low}, sliding screens of paper and the door in its front, windows of paper high in the other walls; a
     * veranda of red pillars one block out from the walls at the given places (along the front, the back and the two sides),
     * a beam on their heads and brackets carrying the lower roof, whose eaves reach three blocks out; red balustrades
     * between the pillars of the back and the sides and lanterns of cloth under the front beam; the upper hall going on up
     * from the walls to its head beam at {@code high}, a window in every panel, under a hip-and-gable roof with sweeping
     * corners and gables of white plaster; ceilings over both halls and lanterns under the roof.
     */
    private static void greatHall(Structure s, int x1, int z1, int x2, int z2, int floor, int low, int high, int pitch,
                                  int[] frontX, int[] sideZ, int[] backX) {
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        int stand = floor + 1;
        Eastern.frame(s, x1, z1, x2, z2, floor, low, 4, UP, Eastern.PLASTER);
        fill(s, x1 + 1, floor, z1 + 1, x2 - 1, floor, z2 - 1, Eastern.FLOOR);
        fill(s, x1 + 1, low, z1 + 1, x2 - 1, low, z2 - 1, Eastern.FLOOR);
        frontWall(s, x1, x2, z2, cx, stand, low);
        for (int x = x1 + 1; x < x2; x++) {
            if (Math.floorMod(x - x1, 2) == 0) {
                fill(s, x, stand, z1, x, low - 1, z1, UP);
            } else {
                fill(s, x, low - 2, z1, x, low - 1, z1, Eastern.PAPER);
            }
        }
        for (int z = z1 + 2; z < z2; z += 4) {
            fill(s, x1, low - 2, z, x1, low - 1, z, Eastern.PAPER);
            fill(s, x2, low - 2, z, x2, low - 1, z, Eastern.PAPER);
        }
        // the veranda
        for (int x : frontX) {
            Eastern.pillar(s, x, stand, low - 1, z2 + 1, false);
        }
        for (int x : backX) {
            Eastern.pillar(s, x, stand, low - 1, z1 - 1, false);
        }
        for (int z : sideZ) {
            Eastern.pillar(s, x1 - 1, stand, low - 1, z, false);
            Eastern.pillar(s, x2 + 1, stand, low - 1, z, false);
        }
        for (int i = 0; i + 1 < backX.length; i++) {
            Eastern.railing(s, backX[i] + 1, z1 - 1, backX[i + 1] - 1, z1 - 1, stand, true);
        }
        for (int i = 0; i + 1 < sideZ.length; i++) {
            Eastern.railing(s, x1 - 1, sideZ[i] + 1, x1 - 1, sideZ[i + 1] - 1, stand, true);
            Eastern.railing(s, x2 + 1, sideZ[i] + 1, x2 + 1, sideZ[i + 1] - 1, stand, true);
        }
        s.beam(x1 - 1, z1 - 1, x2 + 1, z2 + 1, low, Eastern.POST);
        // the brackets on the heads of the pillars; the lower roof then covers their outer arms
        for (int x : frontX) {
            Eastern.bracket(s, x, low, z2 + 1, "south", 1);
        }
        for (int x : backX) {
            Eastern.bracket(s, x, low, z1 - 1, "north", 1);
        }
        for (int z : sideZ) {
            if (z > z1 - 1 && z < z2 + 1) {
                Eastern.bracket(s, x1 - 1, low, z, "west", 1);
                Eastern.bracket(s, x2 + 1, low, z, "east", 1);
            }
        }
        int sill = Eastern.skirt(s, x1, z1, x2, z2, low, 3, 0, 2, Eastern.ROOF);
        for (int i = 0; i + 1 < frontX.length; i++) {
            int mx = (frontX[i] + frontX[i + 1]) / 2;
            if (Math.abs(mx - cx) > 1) {
                Eastern.clothLantern(s, mx, z2 + 1, low - 1, 0);
            }
        }
        for (int i = 0; i + 1 < backX.length; i += 2) {
            Eastern.clothLantern(s, (backX[i] + backX[i + 1]) / 2, z1 - 1, low - 1, 0);
        }
        for (int[] c : new int[][]{{x1 - 2, z1 - 2}, {x2 + 2, z1 - 2}, {x1 - 2, z2 + 2}, {x2 + 2, z2 + 2}}) {
            Eastern.clothLantern(s, c[0], c[1], low - 1, 0);
        }
        // the upper hall
        Eastern.frame(s, x1, z1, x2, z2, sill, high, 4, UP, Eastern.PLASTER);
        for (int x = x1 + 2; x < x2; x += 4) {
            fill(s, x, sill + 1, z1, x, sill + 2, z1, Eastern.PAPER);
            fill(s, x, sill + 1, z2, x, sill + 2, z2, Eastern.PAPER);
        }
        for (int z = z1 + 2; z < z2; z += 4) {
            fill(s, x1, sill + 1, z, x1, sill + 2, z, Eastern.PAPER);
            fill(s, x2, sill + 1, z, x2, sill + 2, z, Eastern.PAPER);
        }
        fill(s, x1 + 1, high, z1 + 1, x2 - 1, high, z2 - 1, Eastern.PLANKS);
        Eastern.roof(s, x1 - 2, z1 - 2, x2 + 2, z2 + 2, high, 3, pitch, Eastern.lift(x1 - 2, z1 - 2, x2 + 2, z2 + 2), Eastern.ROOF,
                Eastern.PLASTER);
        for (int x = x1 + 3; x <= x2 - 3; x += Math.max(1, (x2 - x1 - 6) / 2)) {
            put(s, x, high + 1, cz, Kit.lantern());
        }
    }

    /**
     * A flight of stairs of the timber against the west wall of a hall, from the front at (x, stand, zFront) up toward the
     * back to the floor of the upper hall at layer {@code low}, through an opening in the ceiling with a railing beside it
     * upstairs.
     */
    private static void hallStairs(Structure s, int x, int stand, int zFront, int low) {
        int n = low - stand + 1;
        for (int i = 0; i < n; i++) {
            put(s, x, stand + i, zFront - i, Kit.stairs(Eastern.FLOOR_STAIRS, "north"));
        }
        for (int z = zFront - n + 2; z <= zFront; z++) {
            put(s, x, low, z, AIR);
            put(s, x + 1, low + 1, z, Eastern.FENCE);
        }
    }

    /**
     * A gate porch before the door of a hall with two roofs whose front wall stands at row {@code zWall}: the lower roof
     * over the entrance is cut away; four red pillars, two on the veranda and two on the cheeks of the steps at row
     * {@code zPillar}, carry a gable roof one layer above the lower eaves whose ridge runs from the upper wall out over the
     * steps to row {@code zEnd}; its gable to the street is of plaster, hung with the banner of the guild. The upper wall
     * behind it stays whole.
     */
    private static void gatePorch(Structure s, int cx, int zWall, int stand, int low, int high, int zPillar, int zEnd) {
        fill(s, cx - 3, low, zWall + 1, cx + 3, low + 2, zWall + 3, AIR);
        for (int x : new int[]{cx - 2, cx + 2}) {
            Eastern.pillar(s, x, stand, low, zWall + 1, false);
            Eastern.pillar(s, x, stand, low, zPillar, false);
        }
        String[][] wall = new String[7][high - low];
        for (int i = 0; i < 7; i++) {
            for (int y = low + 1; y <= high; y++) {
                wall[i][y - low - 1] = s.get(cx - 3 + i, y, zWall);
            }
        }
        Eastern.roof(s, cx - 3, zWall, cx + 3, zEnd, low + 1, Eastern.GABLE, 1, 1, Eastern.ROOF, null);
        for (int i = 0; i < 7; i++) {
            for (int y = low + 1; y <= high; y++) {
                s.set(cx - 3 + i, y, zWall, wall[i][y - low - 1]);
            }
        }
        fill(s, cx - 1, low + 1, zEnd - 1, cx + 1, low + 1, zEnd - 1, Eastern.PLASTER);
        put(s, cx, low + 1, zEnd, Kit.wallBanner(GUILD, "south"));
        Eastern.clothLantern(s, cx, zWall + 2, low + 1, 1);
    }

    /**
     * The drum tower over the gate passage of rank S, its base of stone from row {@code z1} to row {@code z2} around the
     * column {@code cx}: the base battered, pierced along z by a passage three wide under a vault, whose stair climbs at its
     * back to the landing of the hall; on the base a pavilion of two storeys with walls of white plaster and sliding screens,
     * red pillars on the edge of the base, a skirt roof with a red balcony on it, a pyramid roof with a spire of rings; a
     * ladder climbs in the passage up into the pavilion, another to the upper room where the drum stands.
     */
    private static void drumGate(Structure s, int cx, int z1, int z2) {
        int x1 = cx - 4;
        int x2 = cx + 4;
        int cz = (z1 + z2) / 2;
        int top = 6;
        Eastern.platform(s, x1, z1, x2, z2, 0, top, 4, null);
        for (int z = z1; z <= z2; z++) {
            fill(s, cx - 1, 0, z, cx + 1, 3, z, AIR);
            if (z > z1 && z < z2) {
                put(s, cx, 4, z, AIR);
                put(s, cx - 1, 4, z, Kit.upside(Eastern.STONE_STAIRS, "west"));
                put(s, cx + 1, 4, z, Kit.upside(Eastern.STONE_STAIRS, "east"));
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int x = cx - 1; x <= cx + 1; x++) {
                if (i > 0) {
                    fill(s, x, 0, z1 + 2 - i, x, i - 1, z1 + 2 - i, Eastern.STONE);
                }
                put(s, x, i, z1 + 2 - i, Kit.stairs(Eastern.STONE_STAIRS, "north"));
            }
        }
        put(s, cx, 4, cz - 1, Kit.hangingLantern());
        put(s, cx, 4, cz + 2, Kit.hangingLantern());
        // the banner of the guild over the mouth of the passage
        put(s, cx, 5, z2, Kit.wallBanner(GUILD, "south"));
        // the pavilion on the base
        int wx1 = cx - 2;
        int wx2 = cx + 2;
        int wz1 = cz - 2;
        int wz2 = cz + 2;
        int head = top + 5;
        Eastern.frame(s, wx1, wz1, wx2, wz2, top, head, 0, UP, Eastern.PLASTER);
        fill(s, wx1 + 1, top, wz1 + 1, wx2 - 1, top, wz2 - 1, Eastern.FLOOR);
        Eastern.screen(s, wx1 + 1, wz2, wx2 - 1, wz2, top + 1, head - 1, false);
        Eastern.screen(s, wx1, wz1 + 1, wx1, wz2 - 1, top + 1, head - 1, false);
        Eastern.screen(s, wx2, wz1 + 1, wx2, wz2 - 1, top + 1, head - 1, false);
        for (int x = cx - 3; x <= cx + 3; x += 3) {
            for (int z = cz - 3; z <= cz + 3; z += 3) {
                if (x != cx || z != cz) {
                    Eastern.pillar(s, x, top, head - 1, z, false);
                }
            }
        }
        fill(s, wx1 + 1, head, wz1 + 1, wx2 - 1, head, wz2 - 1, Eastern.PLANKS);
        Eastern.skirt(s, wx1, wz1, wx2, wz2, head, 2, 0, 1, Eastern.ROOF);
        Eastern.balcony(s, cx - 3, cz - 3, cx + 3, cz + 3, head + 1);
        int head2 = head + 4;
        Eastern.frame(s, wx1, wz1, wx2, wz2, head, head2, 0, UP, Eastern.PLASTER);
        for (int y = head + 2; y < head2; y++) {
            put(s, cx, y, wz1, Eastern.PAPER);
            put(s, cx, y, wz2, Eastern.PAPER);
            put(s, wx1, y, cz, Eastern.PAPER);
            put(s, wx2, y, cz, Eastern.PAPER);
        }
        fill(s, wx1 + 1, head2, wz1 + 1, wx2 - 1, head2, wz2 - 1, Eastern.PLANKS);
        put(s, cx, head2 + 1, cz, Kit.lantern());
        int roofTop = Eastern.roof(s, cx - 4, cz - 4, cx + 4, cz + 4, head2, Eastern.HIP, 1, 2, Eastern.ROOF, null);
        Eastern.finial(s, cx, roofTop, cz, 5);
        // ladders, the drum, light
        ladder(s, cx - 1, 0, cz, "east", top);
        ladder(s, cx + 1, top + 1, wz1 + 1, "south", head);
        drum(s, cx, head + 1, wz2 - 1);
        put(s, cx, head2 - 1, cz, Kit.hangingLantern());
        put(s, cx, head - 1, cz, Kit.hangingLantern());
        Furnish.bench(s, cx + 1, cz + 1, cx + 1, cz + 1, top + 1, "west", Eastern.WOOD);
    }

    /**
     * A drum on its stand at (x, y, z): the body of the drum on a frame of the timber with a cushion of cloth before it.
     */
    private static void drum(Structure s, int x, int y, int z) {
        put(s, x, y, z, "minecraft:note_block[instrument=basedrum,note=0,powered=false]");
        put(s, x, y - 0, z - 1, Kit.CLOTH_CARPET);
    }

    /**
     * The drum tower of rank SS around (cx, cz): a base of stone seven blocks square and four high with a stair of stone up
     * its north side, on it a timber storey with red pillars round it on the edge of the base and its door to the north,
     * a skirt roof with a red balcony on it, an upper storey with the drum and a hip-and-gable roof.
     */
    private static void drumTower(Structure s, int cx, int cz) {
        int top = 4;
        Eastern.platform(s, cx - 3, cz - 3, cx + 3, cz + 3, 0, top, 0, null);
        for (int i = 0; i < 4; i++) {
            int x = cx + 4 - i;
            fill(s, x, 0, cz - 4, x, i, cz - 4, Eastern.STONE);
            put(s, x, 1 + i, cz - 4, Kit.stairs(Eastern.STONE_STAIRS, "west"));
        }
        int head = top + 5;
        Eastern.frame(s, cx - 2, cz - 2, cx + 2, cz + 2, top, head, 0, UP, Eastern.PLASTER);
        fill(s, cx - 1, top, cz - 1, cx + 1, top, cz + 1, Eastern.FLOOR);
        Eastern.screen(s, cx - 1, cz + 2, cx + 1, cz + 2, top + 1, head - 1, false);
        Eastern.screen(s, cx - 2, cz - 1, cx - 2, cz + 1, top + 1, head - 1, false);
        Eastern.screen(s, cx + 2, cz - 1, cx + 2, cz + 1, top + 1, head - 1, false);
        s.door(cx + 1, top + 1, cz - 2, Eastern.DOOR, "south", "left");
        for (int x = cx - 3; x <= cx + 3; x += 3) {
            for (int z = cz - 3; z <= cz + 3; z += 3) {
                if (x != cx || z != cz) {
                    Eastern.pillar(s, x, top, head - 1, z, false);
                }
            }
        }
        fill(s, cx - 1, head, cz - 1, cx + 1, head, cz + 1, Eastern.PLANKS);
        Eastern.skirt(s, cx - 2, cz - 2, cx + 2, cz + 2, head, 2, 0, 1, Eastern.ROOF);
        Eastern.balcony(s, cx - 3, cz - 3, cx + 3, cz + 3, head + 1);
        int head2 = head + 4;
        Eastern.frame(s, cx - 2, cz - 2, cx + 2, cz + 2, head, head2, 0, UP, Eastern.PLASTER);
        for (int y = head + 2; y < head2; y++) {
            put(s, cx, y, cz - 2, Eastern.PAPER);
            put(s, cx, y, cz + 2, Eastern.PAPER);
            put(s, cx - 2, y, cz, Eastern.PAPER);
            put(s, cx + 2, y, cz, Eastern.PAPER);
        }
        fill(s, cx - 1, head2, cz - 1, cx + 1, head2, cz + 1, Eastern.PLANKS);
        put(s, cx, head2 + 1, cz, Kit.lantern());
        Eastern.roof(s, cx - 4, cz - 4, cx + 4, cz + 4, head2, 2, 1, 2, Eastern.ROOF, Eastern.PLASTER);
        ladder(s, cx - 1, top + 1, cz - 1, "south", head);
        drum(s, cx, head + 1, cz + 1);
        put(s, cx, head2 - 1, cz, Kit.hangingLantern());
        put(s, cx, head - 1, cz, Kit.hangingLantern());
        Furnish.bench(s, cx + 1, cz, cx + 1, cz + 1, top + 1, "west", Eastern.WOOD);
        Eastern.clothLantern(s, cx - 1, cz - 3, head - 1, 0);
        Eastern.clothLantern(s, cx + 1, cz - 3, head - 1, 0);
    }

    /**
     * The shrine of the guild in the ground storey of its pagoda, one stands at layer {@code y} around (cx, cz): an alcove
     * with a scroll facing the door, a shrine of candles on each side wall, the skulls of beasts slain by the guild on
     * stands, cushions and paper lamps.
     */
    private static void guildShrine(Structure s, int cx, int y, int cz) {
        Eastern.alcove(s, cx, y, cz - 2, "south");
        Eastern.shrine(s, cx - 2, y, cz, "east");
        Eastern.shrine(s, cx + 2, y, cz, "west");
        for (int x : new int[]{cx - 1, cx + 1}) {
            put(s, x, y, cz - 2, Eastern.FENCE);
            put(s, x, y + 1, cz - 2, "minecraft:skeleton_skull[rotation=0]");
        }
        put(s, cx, y, cz, Kit.CLOTH_CARPET);
        Eastern.paperLamp(s, cx + 2, y, cz + 2);
        Eastern.paperLamp(s, cx - 2, y, cz + 2);
    }

    /**
     * The training yard in the west of the compound of rank SS: straw men on posts, targets, a rack of spears, a bench along
     * the wall, lanterns on posts, a stone lantern and a pine.
     */
    private static void trainingYard(Structure s) {
        for (int x : new int[]{3, 6}) {
            put(s, x, 1, 17, Eastern.FENCE);
            put(s, x, 2, 17, "minecraft:hay_block[axis=y]");
            put(s, x, 3, 17, "minecraft:carved_pumpkin[facing=south]");
        }
        for (int z : new int[]{19, 21}) {
            put(s, 1, 1, z, Eastern.FENCE);
            put(s, 1, 2, z, "minecraft:target[power=0]");
        }
        for (int x = 2; x <= 7; x++) {
            if (x % 2 == 0) {
                spear(s, x, 1, 15);
            }
        }
        Eastern.lanternPost(s, 8, 1, 18, 3, "west");
        Eastern.lanternPost(s, 8, 1, 24, 3, "west");
        for (int z = 23; z <= 25; z++) {
            put(s, 1, 1, z, Kit.stairs(Eastern.FLOOR_STAIRS, "west"));
        }
        Eastern.stoneLantern(s, 7, 1, 27, false);
        Eastern.pine(s, 3, 1, 26, 5, "east");
    }

    /**
     * A gate under a heavy roof in a wall that runs along x at row {@code z}, its passage {@code 2 * hw + 1} wide around
     * the column {@code cx}, standing on layer {@code y}: four red pillars at the corners of the passage, plaster beside
     * them in the wall line, door leaves standing open, a lintel with the plaque of the guild, brackets on the heads of the
     * pillars and a hip-and-gable roof whose eaves reach three blocks before and behind the wall and two beyond the pillars,
     * steep and heavy; lanterns of cloth hang before and behind the passage.
     */
    private static void gateHouse(Structure s, int cx, int y, int z, int hw, int height) {
        int w = cx - hw - 1;
        int e = cx + hw + 1;
        int head = y + height;
        for (int x : new int[]{w, e}) {
            for (int dz = -1; dz <= 1; dz += 2) {
                Eastern.pillar(s, x, y, head - 1, z + dz, false);
            }
            for (int yy = y; yy < head; yy++) {
                put(s, x, yy, z, yy == y ? Eastern.TRIM : Eastern.PLASTER);
            }
        }
        fill(s, w + 1, y - 1, z - 1, e - 1, y - 1, z + 1, Eastern.TRIM);
        for (int x = w + 1; x <= e - 1; x++) {
            put(s, x, head - 2, z, Eastern.PLASTER);
            put(s, x, head - 1, z, Kit.log(Eastern.POST, "x"));
        }
        put(s, cx, head - 2, z + 1, Kit.wallBanner(GUILD, "south"));
        put(s, cx, head - 2, z - 1, Kit.wallBanner(GUILD, "north"));
        s.door(w + 1, y, z, Eastern.DOOR, "south", "left");
        s.door(e - 1, y, z, Eastern.DOOR, "south", "right");
        for (int yy = y; yy <= y + 1; yy++) {
            put(s, w + 1, yy, z, s.get(w + 1, yy, z).replace("open=false", "open=true"));
            put(s, e - 1, yy, z, s.get(e - 1, yy, z).replace("open=false", "open=true"));
        }
        s.beam(w, z - 1, e, z + 1, head, Eastern.POST);
        for (int x : new int[]{w, e}) {
            Eastern.bracket(s, x, head, z - 1, "north", 1);
            Eastern.bracket(s, x, head, z + 1, "south", 1);
            put(s, x, head, z, Eastern.PLANKS);
        }
        fill(s, w + 1, head, z, e - 1, head, z, Eastern.PLANKS);
        fill(s, w + 1, head + 1, z - 1, e - 1, head + 1, z + 1, Eastern.PLANKS);
        Eastern.clothLantern(s, cx, z + 2, head, 1);
        Eastern.clothLantern(s, cx, z - 2, head, 1);
        Eastern.roof(s, w - 2, z - 3, e + 2, z + 3, head + 1, 2, 2, 2, Eastern.ROOF, Eastern.PLASTER);
    }

    // ---------------------------------------------------------------- parts of the halls

    /**
     * The front wall of a hall along x at row {@code z} between the corner posts {@code x1} and {@code x2} (posts every two
     * blocks): sliding screens of paper between the posts, the door in the middle under a lintel with plaster over it and a
     * curtain of cloth hanging before the lintel.
     */
    private static void frontWall(Structure s, int x1, int x2, int z, int cx, int stand, int head) {
        for (int x = x1 + 1; x < x2; x++) {
            if (Math.floorMod(x - x1, 2) == 1 && x != cx) {
                Eastern.screen(s, x, z, x, z, stand, head - 1, false);
            } else if (x != cx) {
                fill(s, x, stand, z, x, head - 1, z, UP);
            }
        }
        s.door(cx, stand, z, Eastern.DOOR, "north", "left");
        put(s, cx, stand + 2, z, Kit.log(Eastern.POST, "x"));
        fill(s, cx, stand + 3, z, cx, head - 1, z, Eastern.PLASTER);
        put(s, cx, stand + 2, z + 1, Kit.wallBanner("red", "south"));
    }

    /**
     * The counter of the registrar along x at row {@code z} from {@code xa} to {@code xb}: boards of the timber with candles
     * and a lantern on them and the guild desk looking south in the middle at {@code x}; the post of the registrar and the
     * one chest of the guild beside it behind the counter at the given places.
     */
    private static void registrar(Structure s, int x, int y, int z, int xa, int xb, int[] post, int[] chest) {
        for (int xx = xa; xx <= xb; xx++) {
            put(s, xx, y, z, Kit.topSlab(Eastern.FLOOR_SLAB));
        }
        put(s, x, y, z, DESK);
        put(s, xa, y + 1, z, Furnish.candles(2));
        put(s, xb, y + 1, z, Kit.lantern());
        put(s, post[0], y, post[1], WORK_POST);
        put(s, chest[0], y, chest[1], Kit.chest("south"));
    }

    /**
     * Trophies on a wall that looks {@code facing}, {@code count} cells from (x, z) toward the left of {@code facing}: racks
     * of swords (grindstones) with the skulls of beasts hung over them and between them spears standing in a rack.
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
                spear(s, px, y, pz);
            }
        }
    }

    /** A spear standing in its rack: a shaft of fence with a tip of copper. */
    private static void spear(Structure s, int x, int y, int z) {
        put(s, x, y, z, Eastern.FENCE);
        put(s, x, y + 1, z, Eastern.ROD);
    }

    /**
     * A rack of arms along a wall that looks {@code facing}, {@code count} cells from (x, z) toward the left of
     * {@code facing}: spears and swords by turns, a skull over the middle.
     */
    private static void armsRack(Structure s, int x, int y, int z, String facing, int count) {
        int[] a = Furnish.step(Furnish.left(facing));
        for (int u = 0; u < count; u++) {
            int px = x + a[0] * u;
            int pz = z + a[1] * u;
            if (u % 2 == 0) {
                spear(s, px, y, pz);
            } else {
                put(s, px, y, pz, "minecraft:grindstone[face=floor,facing=" + facing + "]");
                put(s, px, y + 2, pz, "minecraft:skeleton_wall_skull[facing=" + facing + "]");
            }
        }
    }

    /** The banner of the guild hanging on the wall behind (x, y, z), looking {@code facing}. */
    private static void banner(Structure s, int x, int y, int z, String facing) {
        put(s, x, y, z, Kit.wallBanner(GUILD, facing));
    }

    /**
     * The notice board of contracts against a wall, looking {@code facing}: a board of the timber {@code width} wide and
     * two high on its posts, the notices of white and yellow paper (banners) pinned on it and a little roof of tiles over
     * it, as the boards of public notices stand by the roads.
     *
     * @param x the column of its first cell against the wall; it runs {@code width} cells toward the left of
     *          {@code facing}
     */
    private static void noticeBoard(Structure s, int x, int y, int z, String facing, int width) {
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        for (int u = 0; u < width; u++) {
            int px = x + a[0] * u;
            int pz = z + a[1] * u;
            boolean end = u == 0 || u == width - 1;
            put(s, px, y, pz, end ? Eastern.FENCE : Kit.topSlab(Eastern.SLAB));
            put(s, px, y + 1, pz, Eastern.PLANKS);
            put(s, px, y + 2, pz, Eastern.PLANKS);
            put(s, px, y + 3, pz, Kit.stairs(Eastern.ROOF.stairs(), Furnish.opposite(facing)));
            put(s, px + f[0], y + 2, pz + f[1], Kit.wallBanner(u % 2 == 0 ? "white" : "yellow", facing));
        }
    }

    /** A brazier: a bowl of fire on a stand of grey stone. */
    private static void brazier(Structure s, int x, int y, int z) {
        put(s, x, y, z, Eastern.GRANITE_WALL);
        put(s, x, y + 1, z, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
    }

    /**
     * A banner of the guild on a pole of timber standing on layer {@code y}: a post {@code height} high and the banner on
     * its top.
     */
    private static void bannerPole(Structure s, int x, int y, int z, int height) {
        fill(s, x, y, z, x, y + height - 1, z, Eastern.FENCE);
        put(s, x, y + height, z, Kit.banner(GUILD, 0));
    }

    /**
     * A ladder against the wall behind it (the wall lies opposite to {@code facing}), from layer {@code y} up through the
     * ceiling at {@code top}.
     */
    private static void ladder(Structure s, int x, int y, int z, String facing, int top) {
        fill(s, x, y, z, x, top, z, Kit.ladder(facing));
    }

    /**
     * A map of the lands of the guild hung on a wall that runs along x: lands, seas and hills of terracotta {@code width}
     * wide and two high, from (x, y, z) toward the east, its upper row at {@code y + 1}.
     */
    private static void guildMap(Structure s, int x, int y, int z, int width) {
        String[] map = {"lgyBc", "gcglb"};
        for (int row = 0; row < map.length; row++) {
            for (int i = 0; i < width; i++) {
                String color = switch (map[row].charAt(i % 5)) {
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
     * The same map hung on a wall that runs along z, from (x, y, z) {@code width} blocks toward the front.
     */
    private static void guildMapZ(Structure s, int x, int y, int z, int width) {
        Structure map = new Structure(width, 2, 1);
        guildMap(map, 0, 0, 0, width);
        for (int i = 0; i < width; i++) {
            for (int row = 0; row < 2; row++) {
                put(s, x, y + row, z + i, map.get(i, row, 0));
            }
        }
    }

    /**
     * The map table: the cartography table with stools of the timber at its sides and before it.
     */
    private static void mapTable(Structure s, int x, int y, int z) {
        put(s, x, y, z, CARTOGRAPHY);
        put(s, x - 1, y, z, Kit.stairs(Eastern.FLOOR_STAIRS, "west"));
        put(s, x + 1, y, z, Kit.stairs(Eastern.FLOOR_STAIRS, "east"));
        put(s, x, y, z + 1, Kit.stairs(Eastern.FLOOR_STAIRS, "south"));
    }

    // ================================================================ the mages' pagoda

    /**
     * The pagoda of the mages of a rank.
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
     * Rank B: a pagoda of three storeys on a low platform of stone, a step before its door. On its ground floor the focus
     * with the post of the mage and its chest, a brewing stand and a cauldron, shelves of books; on the second the room of
     * the mage with the bed, the library shelves and the lectern; on the third the research room with the enchanting table;
     * ladders in a corner from storey to storey.
     */
    private static Drawn mageB() {
        Structure s = new Structure(13, 28, 13);
        int c = 6;
        Eastern.platform(s, 1, 1, 11, 11, 0, 0, 0, null);
        Eastern.steps(s, c, 12, "south", 3, 0, 0, false);
        int[] f = pagoda(s, c, c, 0, new int[]{4, 3, 2}, new int[]{6, 4, 4}, new int[]{2, 2, 3}, 6);
        focusFloor(s, c, f[0] + 1, c, 4, 1);
        int y = f[1] + 1;
        Furnish.bed(s, c + 2, y, c + 2, "north", "white");
        Furnish.books(s, c + 1, c - 2, c + 2, c - 2, y, y + 1);
        put(s, c, y, c - 2, LECTERN);
        Furnish.books(s, c - 2, c + 1, c - 2, c + 2, y, y + 1);
        Eastern.tatami(s, c - 1, c - 1, c + 1, c + 1, f[1], false);
        Eastern.paperLamp(s, c + 2, y, c);
        research(s, c, f[2] + 1, c, 2);
        mageCrown(s, c, c, f[2], 2, f[2] + 4);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, 11}, List.of());
    }

    /**
     * Rank A: a pagoda of four storeys stepping in as it rises, on a platform of stone with a step before its door. On its
     * ground floor the focus with the post of the mage and its chest, the brewing corner and shelves of books; on the second
     * the room of the two mages with their beds, a shrine and shelves; on the third the library with its lectern and
     * reading table; on the fourth the research room with the enchanting table.
     */
    private static Drawn mageA() {
        Structure s = new Structure(15, 34, 15);
        int c = 7;
        Eastern.platform(s, 1, 1, 13, 13, 0, 0, 0, null);
        Eastern.steps(s, c, 14, "south", 3, 0, 0, false);
        int[] f = pagoda(s, c, c, 0, new int[]{5, 4, 3, 3}, new int[]{6, 4, 4, 4}, new int[]{2, 2, 2, 2}, 6);
        focusFloor(s, c, f[0] + 1, c, 5, 1);
        int y = f[1] + 1;
        mats(s, c - 3, c - 3, c + 3, c + 3, f[1]);
        Furnish.bed(s, c + 1, y, c + 3, "north", "white");
        Furnish.bed(s, c + 3, y, c + 3, "north", "white");
        Eastern.cabinet(s, c + 2, y, c + 3, 1);
        Eastern.alcove(s, c + 3, y, c - 2, "west");
        Eastern.paperLamp(s, c - 3, y, c + 3);
        shelves(s, c, y, c, 4);
        library(s, c, f[2] + 1, c, 3);
        research(s, c, f[3] + 1, c, 3);
        mageCrown(s, c, c, f[3], 3, f[3] + 4);
        for (int x : new int[]{1, 13}) {
            for (int z : new int[]{1, 13}) {
                Eastern.stoneLantern(s, x, 1, z, false);
            }
        }
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, 13}, List.of());
    }

    /**
     * Rank S: a pagoda of five storeys and a library wing beside it. The pagoda stands on a platform of stone with steps
     * before its door; its ground storey is high, the focus in it with the post of the mage and its chest and the brewing
     * corner; over it the room of two mages, the room of the third with his study, the research room and on the top the
     * observatory. A door in its east wall opens on a walk under the eaves to the library wing: a hall of white walls under a
     * hip-and-gable roof tucked under the lowest eaves of the pagoda, the shelves and the lectern of the library at its north
     * end, the brewing room at its south end. Before them a pond in a stone basin, stone lanterns and a pine.
     */
    private static Drawn mageS() {
        Structure s = new Structure(23, 42, 19);
        int cx = 8;
        int cz = 9;
        Eastern.platform(s, 3, 4, 21, 14, 0, 1, 0, null);
        Eastern.steps(s, cx, 15, "south", 3, 1, 0, false);
        libraryWing(s, 16, 5, 20, 13);
        int[] f = pagoda(s, cx, cz, 1, new int[]{4, 4, 3, 3, 2}, new int[]{7, 4, 4, 4, 4}, new int[]{3, 2, 3, 2, 3}, 6);
        s.door(cx + 4, 2, cz, Eastern.DOOR, "west", "left");
        put(s, cx + 4, 4, cz, Eastern.PLASTER);
        focusFloor(s, cx, f[0] + 1, cz, 4, -1);
        lamp(s, cx - 2, f[0] + 5, cz + 2);
        lamp(s, cx + 2, f[0] + 5, cz - 2);
        // the room of two mages
        int y = f[1] + 1;
        mats(s, cx - 3, cz - 3, cx + 3, cz + 3, f[1]);
        Furnish.bed(s, cx + 1, y, cz + 3, "north", "white");
        Furnish.bed(s, cx + 3, y, cz + 3, "north", "white");
        Eastern.cabinet(s, cx + 2, y, cz + 3, 1);
        Eastern.paperLamp(s, cx - 3, y, cz + 3);
        Eastern.alcove(s, cx + 3, y, cz - 2, "west");
        shelves(s, cx, y, cz, 4);
        // the room of the third mage with his study
        y = f[2] + 1;
        mats(s, cx - 2, cz - 2, cx + 2, cz + 2, f[2]);
        Furnish.bed(s, cx + 2, y, cz + 2, "north", "white");
        Furnish.desk(s, cx - 1, y, cz + 2, "north", Eastern.WOOD);
        Eastern.alcove(s, cx + 2, y, cz - 2, "south");
        shelves(s, cx, y, cz, 3);
        research(s, cx, f[3] + 1, cz, 3);
        observatory(s, cx, f[4] + 1, cz);
        mageCrown(s, cx, cz, f[4], 2, f[4] + 4);
        // the garden before them
        Eastern.pond(s, 13, 16, 20, 17, 1);
        Eastern.stoneLantern(s, 5, 0, 16, false);
        Eastern.stoneLantern(s, 11, 0, 16, false);
        Eastern.stoneLantern(s, 3, 0, 2, false);
        Eastern.stoneLantern(s, 4, 2, 4, false);
        Eastern.pine(s, 1, 0, 2, 5, "east");
        Eastern.pine(s, 21, 0, 2, 4, "west");
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{cx, 2, cz + 5}, List.of());
    }

    /**
     * The library wing of rank S on the rectangle of its walls {@code x1..x2, z1..z2}, its floor on layer 1: walls of white
     * plaster between dark posts, its door in the middle of the west wall, windows of paper, a hip-and-gable roof whose ridge
     * runs along z; inside the shelves of the library and the lectern at the north end, the brewing room with its stands,
     * cauldron, herbs and table at the south end, a folding screen between them.
     */
    private static void libraryWing(Structure s, int x1, int z1, int x2, int z2) {
        int cxw = (x1 + x2) / 2;
        int czw = (z1 + z2) / 2;
        int head = 6;
        int y = 2;
        Eastern.frame(s, x1, z1, x2, z2, 1, head, 0, UP, Eastern.PLASTER);
        fill(s, x1 + 1, 1, z1 + 1, x2 - 1, 1, z2 - 1, Eastern.FLOOR);
        fill(s, x1 + 1, head, z1 + 1, x2 - 1, head, z2 - 1, Eastern.PLANKS);
        s.door(x1, y, czw, Eastern.DOOR, "east", "left");
        put(s, x1, y + 2, czw, Eastern.PLASTER);
        Eastern.window(s, x2, y + 1, z1 + 2, "east", 1, 2);
        Eastern.window(s, x2, y + 1, z2 - 2, "east", 1, 2);
        Eastern.window(s, cxw, y + 1, z1, "north", 1, 2);
        Eastern.window(s, cxw, y + 1, z2, "south", 1, 2);
        Eastern.roof(s, x1 - 2, z1 - 2, x2 + 2, z2 + 2, head, 2, 1, 1, Eastern.ROOF, Eastern.PLASTER);
        put(s, cxw, head + 1, czw, Kit.lantern());
        // the library at the north end
        Furnish.books(s, x1 + 1, z1 + 1, x1 + 1, z1 + 1, y, y + 2);
        Furnish.books(s, x2 - 1, z1 + 1, x2 - 1, z1 + 1, y, y + 2);
        Furnish.books(s, cxw, z1 + 1, cxw, z1 + 1, y, y);
        Furnish.books(s, x2 - 1, z1 + 2, x2 - 1, z1 + 2, y, y);
        Furnish.books(s, x2 - 1, z1 + 3, x2 - 1, z1 + 3, y, y + 2);
        Furnish.books(s, x1 + 1, z1 + 2, x1 + 1, z1 + 2, y, y + 2);
        put(s, cxw, y, z1 + 3, LECTERN);
        put(s, cxw, y + 1, z1 + 1, Furnish.candles(3));
        // the brewing room at the south end
        put(s, x1 + 1, y, z2 - 1, BREWING);
        put(s, x2 - 1, y, z2 - 1, BREWING);
        put(s, cxw, y, z2 - 1, CAULDRON);
        put(s, x2 - 1, y, z2 - 2, Kit.topSlab(Eastern.FLOOR_SLAB));
        put(s, x2 - 1, y + 1, z2 - 2, Furnish.candles(2));
        Furnish.sack(s, x2 - 1, y, z2 - 3, false);
        Furnish.plant(s, x1 + 1, y, z2 - 3, "azalea");
        Eastern.foldingScreen(s, x2 - 1, czw, x2 - 1, czw, y);
        lamp(s, cxw, head - 1, z1 + 2);
        lamp(s, cxw, head - 1, z2 - 2);
    }

    /**
     * Rank SS: a pagoda of seven storeys, the highest building of the guilds, on a platform of stone; before it a court
     * paved with stone where a red arched bridge crosses a pond in a stone basin to the step of the pagoda, an open pavilion
     * on each side of the court, stone lanterns, pines and a blossoming cherry. On the ground floor the focus with the post
     * of the mage and its chest and the brewing room; over it the library with its lectern, the rooms of the four mages on
     * the next two storeys, the research room, the study of star charts and on the top the observatory.
     */
    private static Drawn mageSS() {
        Structure s = new Structure(25, 48, 25);
        int cx = 12;
        int cz = 8;
        // the court: paving, the pond with its bridge, the pavilions
        fill(s, 7, 0, 15, 17, 0, 24, Eastern.STONE);
        for (int x = 10; x <= 14; x++) {
            put(s, x, 0, 24, Kit.stairs(Eastern.STONE_STAIRS, "north"));
        }
        Eastern.pond(s, 8, 18, 16, 22, 1);
        Eastern.bridge(s, 17, 23, cx, 1, 5, false);
        Eastern.pavilion(s, 3, 0, 21, 1, 3);
        Eastern.pavilion(s, 21, 0, 21, 1, 3);
        Eastern.platform(s, 6, 2, 18, 14, 0, 1, 0, null);
        Eastern.steps(s, cx, 15, "south", 3, 1, 1, false);
        int[] f = pagoda(s, cx, cz, 1, new int[]{5, 4, 4, 3, 3, 2, 2}, new int[]{6, 4, 4, 4, 4, 4, 4},
                new int[]{3, 3, 2, 3, 2, 2, 2}, 6);
        focusFloor(s, cx, f[0] + 1, cz, 5, 1);
        put(s, cx + 4, f[0] + 1, cz - 2, BREWING);
        put(s, cx + 4, f[0] + 1, cz - 1, CAULDRON);
        lamp(s, cx - 3, f[0] + 5, cz + 3);
        lamp(s, cx + 3, f[0] + 5, cz - 3);
        library(s, cx, f[1] + 1, cz, 4);
        // the rooms of the mages
        int y = f[2] + 1;
        mats(s, cx - 3, cz - 3, cx + 3, cz + 3, f[2]);
        Furnish.bed(s, cx + 1, y, cz + 3, "north", "white");
        Furnish.bed(s, cx + 3, y, cz + 3, "north", "white");
        Eastern.cabinet(s, cx + 2, y, cz + 3, 1);
        Eastern.alcove(s, cx + 3, y, cz - 2, "west");
        Eastern.paperLamp(s, cx - 3, y, cz + 3);
        shelves(s, cx, y, cz, 4);
        y = f[3] + 1;
        mats(s, cx - 2, cz - 2, cx + 2, cz + 2, f[3]);
        Furnish.bed(s, cx + 2, y, cz + 2, "north", "white");
        Furnish.bed(s, cx - 1, y, cz + 2, "north", "white");
        Eastern.paperLamp(s, cx + 1, y, cz + 2);
        shelves(s, cx, y, cz, 3);
        research(s, cx, f[4] + 1, cz, 3);
        y = f[5] + 1;
        put(s, cx + 1, y, cz + 1, CARTOGRAPHY);
        Furnish.books(s, cx + 1, cz - 1, cx + 1, cz - 1, y, y + 1);
        put(s, cx - 1, y, cz + 1, Kit.topSlab(Eastern.FLOOR_SLAB));
        put(s, cx - 1, y + 1, cz + 1, Furnish.candles(3));
        observatory(s, cx, f[6] + 1, cz);
        mageCrown(s, cx, cz, f[6], 2, f[6] + 4);
        // lanterns and trees
        Eastern.stoneLantern(s, 8, 1, 16, false);
        Eastern.stoneLantern(s, 16, 1, 16, false);
        Eastern.stoneLantern(s, 6, 2, 2, false);
        Eastern.stoneLantern(s, 18, 2, 2, false);
        Eastern.stoneLantern(s, 4, 0, 1, false);
        Eastern.pine(s, 2, 0, 3, 6, "east");
        Eastern.cherry(s, 22, 0, 4, 5);
        Eastern.pine(s, 2, 0, 13, 4, "east");
        Eastern.pine(s, 22, 0, 13, 4, "west");
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{cx, 2, cz + 6}, List.of());
    }

    // ---------------------------------------------------------------- the pagoda and its rooms

    /**
     * A pagoda around the column (cx, cz) whose ground storey stands on the floor at layer {@code floor}: storeys of white
     * plaster between red posts of the half-sizes {@code r} from the ground up, each {@code h} layers from its floor to its
     * head beam and under its own curving skirt roof whose eaves reach {@code ov} blocks out (see {@link Eastern#storey});
     * the upper storeys ringed by red balconies on the roof below; the last under a pyramid roof with a spire of rings
     * {@code spire} layers high. The door of the ground storey looks south; ladders climb in the north-west corners from
     * storey to storey, each with a shelf of books behind it where the storey above steps in; the space under the top roof
     * is lit.
     *
     * @return the floor layers of the storeys (one stands a layer higher), and last the layer above the top of the spire
     */
    private static int[] pagoda(Structure s, int cx, int cz, int floor, int[] r, int[] h, int[] ov, int spire) {
        int n = r.length;
        int[] floors = new int[n + 1];
        int f = floor;
        for (int i = 0; i < n; i++) {
            floors[i] = f;
            int x1 = cx - r[i];
            int x2 = cx + r[i];
            int z1 = cz - r[i];
            int z2 = cz + r[i];
            if (i > 0) {
                Eastern.balcony(s, x1 - 1, z1 - 1, x2 + 1, z2 + 1, f + 1);
            }
            if (i + 1 < n) {
                f = Eastern.storey(s, x1, z1, x2, z2, f, h[i], ov[i], r[i] - r[i + 1], i == 0 ? "south" : null, Eastern.RED);
                continue;
            }
            int head = f + h[i];
            Eastern.frame(s, x1, z1, x2, z2, f, head, 0, Eastern.RED, Eastern.PLASTER);
            fill(s, x1 + 1, f, z1 + 1, x2 - 1, f, z2 - 1, Eastern.FLOOR);
            for (int yy = f + 2; yy < head; yy++) {
                put(s, cx, yy, z1, Eastern.PAPER);
                put(s, cx, yy, z2, Eastern.PAPER);
                put(s, x1, yy, cz, Eastern.PAPER);
                put(s, x2, yy, cz, Eastern.PAPER);
            }
            put(s, cx, head - 1, cz, Kit.hangingLantern());
            fill(s, x1 + 1, head, z1 + 1, x2 - 1, head, z2 - 1, Eastern.PLANKS);
            put(s, cx, head + 1, cz, Kit.lantern());
            int top = Eastern.roof(s, x1 - ov[i], z1 - ov[i], x2 + ov[i], z2 + ov[i], head, Eastern.HIP, 1, ov[i] >= 3 ? 2 : 1,
                    Eastern.ROOF, null);
            floors[n] = Eastern.finial(s, cx, top, cz, spire);
        }
        for (int i = 0; i + 1 < n; i++) {
            int rn = r[i + 1];
            int lx = cx - rn + 1;
            int lz = cz - rn + 1;
            int head = floors[i] + h[i];
            for (int yy = floors[i] + 1; yy <= floors[i + 1]; yy++) {
                put(s, lx, yy, lz, Kit.ladder("south"));
                if (rn < r[i] && yy < head) {
                    put(s, lx, yy, lz - 1, BOOKS);
                }
            }
        }
        return floors;
    }

    /**
     * The marks of the mages on the top storey of their pagoda (half-size {@code r}, its floor at {@code floor}): its windows
     * of violet glass instead of paper, and rods of light standing on the corners of its red balcony.
     */
    private static void mageCrown(Structure s, int cx, int cz, int floor, int r, int head) {
        for (int y = floor + 2; y < head; y++) {
            for (int[] p : new int[][]{{cx, cz - r}, {cx, cz + r}, {cx - r, cz}, {cx + r, cz}}) {
                if (s.get(p[0], y, p[1]).equals(Eastern.PAPER)) {
                    s.set(p[0], y, p[1], VIOLET);
                }
            }
        }
        for (int dx = -r - 1; dx <= r + 1; dx += 2 * r + 2) {
            for (int dz = -r - 1; dz <= r + 1; dz += 2 * r + 2) {
                put(s, cx + dx, floor + 2, cz + dz, "minecraft:end_rod[facing=up]");
            }
        }
    }

    /**
     * The ground floor of a mage tower whose storey has the half-size {@code r} around (cx, cz), one stands at layer
     * {@code y}: the magic focus in the middle on a rug, the post of the mage two blocks from it to the east ({@code side}
     * 1) or the west (-1) with the one chest beside it, a brewing stand and a cauldron in the north-east corner, shelves of
     * books along the north wall and the west wall leaving the windows free, a desk and a paper lamp.
     */
    private static void focusFloor(Structure s, int cx, int y, int cz, int r, int side) {
        int in = r - 1;
        Furnish.rug(s, cx - 1, cz - 1, cx + 1, cz + 1, y, "minecraft:purple_carpet");
        put(s, cx, y, cz, FOCUS);
        put(s, cx + 2 * side, y, cz, WORK_POST);
        put(s, cx + 3 * side, y, cz, Kit.chest(side > 0 ? "west" : "east"));
        put(s, cx + in, y, cz - in, CAULDRON);
        put(s, cx + in - 1, y, cz - in, BREWING);
        put(s, cx + in, y, cz - in + 1, Kit.topSlab(Eastern.FLOOR_SLAB));
        put(s, cx + in, y + 1, cz - in + 1, Furnish.candles(3));
        for (int x = cx - in; x <= cx + in - 2; x++) {
            if (x != cx && s.get(x, y, cz - in).equals(AIR)) {
                Furnish.books(s, x, cz - in, x, cz - in, y, y + 1);
            }
        }
        for (int z = cz - in + 2; z <= cz + in; z++) {
            if (z != cz) {
                Furnish.books(s, cx - in, z, cx - in, z, y, y + 1);
            }
        }
        Furnish.desk(s, cx - in + 1, y, cz + in, "north", Eastern.WOOD);
        Eastern.paperLamp(s, cx + in, y, cz + in);
    }

    /**
     * The research room on a storey of the half-size {@code r} around (cx, cz), one stands at layer {@code y}: the
     * enchanting table in the middle on mats, shelves of books in the corners, a table of star charts, a globe of amethyst
     * on a post and candles.
     */
    private static void research(Structure s, int cx, int y, int cz, int r) {
        int in = r - 1;
        mats(s, cx - 1, cz - 1, cx + 1, cz + 1, y - 1);
        put(s, cx, y, cz, ENCHANTING);
        Furnish.books(s, cx + in, cz - in, cx + in, cz - in, y, y + 1);
        Furnish.books(s, cx - in, cz + in, cx - in, cz + in, y, y + 1);
        Furnish.books(s, cx + in, cz + in, cx + in, cz + in, y, y);
        put(s, cx + in, y + 1, cz + in, Furnish.candles(3));
        put(s, cx - in, y, cz, CARTOGRAPHY);
        put(s, cx + in, y, cz, Eastern.FENCE);
        put(s, cx + in, y + 1, cz, AMETHYST);
    }

    /**
     * Shelves of books two high along the walls of a storey of the half-size {@code r} around (cx, cz), one stands at layer
     * {@code y}: they leave free the middles of the sides (windows and doors), whatever stands there already, the cell over
     * a ladder coming up through the floor and the cells beside a ladder going up.
     */
    private static void shelves(Structure s, int cx, int y, int cz, int r) {
        int in = r - 1;
        for (int u = -in; u <= in; u++) {
            if (u == 0) {
                continue;
            }
            shelf(s, cx + u, y, cz - in);
            shelf(s, cx + u, y, cz + in);
            shelf(s, cx - in, y, cz + u);
            shelf(s, cx + in, y, cz + u);
        }
    }

    private static void shelf(Structure s, int x, int y, int z) {
        if (!s.get(x, y, z).equals(AIR) || !s.get(x, y + 1, z).equals(AIR) || s.get(x, y - 1, z).startsWith("minecraft:ladder")) {
            return;
        }
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (s.inside(x + d[0], y, z + d[1]) && s.get(x + d[0], y, z + d[1]).startsWith("minecraft:ladder")) {
                return;
            }
            if (s.inside(x + d[0], y - 1, z + d[1]) && s.get(x + d[0], y - 1, z + d[1]).startsWith("minecraft:ladder")) {
                return;
            }
        }
        Furnish.books(s, x, z, x, z, y, y + 1);
    }

    /**
     * The library on a storey of the half-size {@code r} around (cx, cz), one stands at layer {@code y}: shelves of books
     * round the walls, the lectern before the north window, a low reading table with cushions in the middle on mats and
     * paper lamps.
     */
    private static void library(Structure s, int cx, int y, int cz, int r) {
        int in = r - 1;
        mats(s, cx - in, cz - in, cx + in, cz + in, y - 1);
        put(s, cx, y, cz - in, LECTERN);
        shelves(s, cx, y, cz, r);
        Eastern.lowTable(s, cx, cz + 1, cx, cz + 1, y);
        put(s, cx, y + 1, cz + 1, Furnish.candles(2));
        Eastern.paperLamp(s, cx + in, y, cz + in);
    }

    /**
     * The observatory in the top storey of a pagoda (half-size 2) around (cx, cz), one stands at layer {@code y}: a
     * telescope of copper on its stand at the east window, a table of star charts, a globe of amethyst and candles.
     */
    private static void observatory(Structure s, int cx, int y, int cz) {
        put(s, cx + 1, y, cz, Eastern.FENCE);
        put(s, cx + 1, y + 1, cz, Eastern.ROD);
        put(s, cx + 1, y, cz + 1, CARTOGRAPHY);
        put(s, cx - 1, y, cz + 1, Eastern.FENCE);
        put(s, cx - 1, y + 1, cz + 1, AMETHYST);
        put(s, cx + 1, y, cz - 1, Kit.topSlab(Eastern.FLOOR_SLAB));
        put(s, cx + 1, y + 1, cz - 1, Furnish.candles(3));
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

    /**
     * Woven mats laid over the floor boards of the rectangle at layer {@code y}, leaving anything else there (a ladder
     * through the floor) as it is.
     */
    private static void mats(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (s.inside(x, y, z) && s.get(x, y, z).equals(Eastern.FLOOR)) {
                    s.set(x, y, z, Eastern.TATAMI);
                }
            }
        }
    }

    /**
     * A lantern hanging at (x, y, z) on a chain up to the first solid block over it: a beam, a ceiling or the underside of
     * a roof.
     */
    private static void lamp(Structure s, int x, int y, int z) {
        put(s, x, y, z, Kit.hangingLantern());
        for (int yy = y + 1; yy < s.sizeY && !s.solid(x, yy, z); yy++) {
            s.set(x, yy, z, Kit.chain());
        }
    }
}
