package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The two guilds of a capital in the style "mountain", each a program drawn for the rank of its kingdom, from B for a weak
 * one to SS for a strong one.
 *
 * <p>The adventurers' guild is a stone hall of the bold: a heavy hall on a battered foot under a low roof of stone slabs
 * whose coped gables carry runes, and before its middle a mass of stone that rises over the eaves, its deep arched door under
 * the carved face of a crowned forefather and its top crenellated. Inside, the great hall with stone tables and benches, the
 * hearth, the contract board, the counter of the registrar with the guild desk, his post and his chest, and trophies of the
 * hunt; upstairs the rooms of the guild with the map room. The rank grows it: B is the hall with its loft behind a portal
 * block; A turns the portal block into a low thick tower; S sets the hall on a terrace with a flight of stairs, its tower
 * higher, timber balconies on its front; SS is a great hall of pillars on two terraces with galleries inside and out, two
 * towers of different height and shape at its back, one of the main buildings of a capital after the castle.
 *
 * <p>The mages' guild lives in its tower: a thick round tower of stone on a battered foot with few small deep windows,
 * friezes of runes round its shaft and a heavy top on corbels whose merlons are points of amethyst, a crown of crystal round
 * a glowing spike. On its ground floor the magic focus with the post of the mage and its chest; a library, a brewing room, a
 * research room with the enchanting table, and real beds for the mages: one in B, two in A, three in S and four in SS. A
 * girds the tower with a crenellated walk where a narrower shaft rises from a wider drum; S stands on a wide low drum, a
 * round hall of pillars round the focus with a roof terrace, a portal block with the carved face before it; SS stands on a
 * terrace with a garden between two lower towers, girded like A, and carries a crystal observatory inside its crown.
 *
 * <p>Drawn with the shapes of {@link Mountain}; coordinates as everywhere: x from west to east, z from the back to the front
 * (the entrance looks south), y up; layer 0 stands on the ground.
 */
final class MountainGuilds {
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
    private static final String CLUSTER = "minecraft:amethyst_cluster[facing=up,waterlogged=false]";
    /** The glowing heart of the crystal crown: a pale violet light. */
    private static final String HEART = "minecraft:pearlescent_froglight[axis=y]";
    private static final String CRYSTAL = "minecraft:purple_stained_glass";
    private static final String ROD = "minecraft:end_rod[facing=up]";
    private static final String PURPLE = Kit.stainedPane("purple");
    private static final String MOSS = "minecraft:moss_block";
    /** The guild's own colour on its banners, the same in every kingdom. */
    private static final String GUILD = "black";

    private static final String WALL = Mountain.WALL;
    private static final String ROUGH = Mountain.ROUGH;
    private static final String TRIM = Mountain.TRIM;
    private static final String DARK = Mountain.DARK;
    private static final String FLOOR = Mountain.FLOOR;
    private static final String PLANKS = Mountain.PLANKS;
    private static final String WOOD = Mountain.WOOD;
    private static final String AMBER = Mountain.GLASS;

    /**
     * A drawn building with its named places: the door, and the guard by the desk (null for a mage tower).
     */
    private record Drawn(Structure structure, int[] door, int[] guard) {
    }

    private MountainGuilds() {
    }

    /**
     * The adventurers' guilds mountain_guild_b, _a, _s and _ss (trade, type guild, with their door and the guard by the
     * desk) and the mage towers mountain_mage_tower_b, _a, _s and _ss (production, role mage_tower, with their door), all
     * standing on the ground with their anchor in the middle of their front edge.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Rank rank : Rank.values()) {
            String key = rank.name().toLowerCase(Locale.ROOT);
            Drawn guild = guild(rank);
            Structure g = guild.structure();
            list.add(new BuildingGenerator.Building("mountain_guild_" + key, "trade", "guild", "kingdomworld:guild_desk",
                    (g.sizeX - 1) / 2, 0, g.sizeZ - 1, g, "none", 1, "")
                    .at("door", guild.door()[0], guild.door()[1], guild.door()[2])
                    .at("guard", guild.guard()[0], guild.guard()[1], guild.guard()[2]));
        }
        for (Rank rank : Rank.values()) {
            String key = rank.name().toLowerCase(Locale.ROOT);
            Drawn tower = mageTower(rank);
            Structure t = tower.structure();
            list.add(new BuildingGenerator.Building("mountain_mage_tower_" + key, "production", "mage_tower", "kingdomworld:magic_focus",
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
     * Rank B: a hall of stone on a battered foot under a low roof of slabs whose coped gables carry runes, with a loft under
     * it, a chimney breast of rough stone up its west gable. Before its middle a portal block rises over the eaves: a deep
     * arched door, the carved face of a forefather over it, corbels and a crenellated top with a brazier; red hangings and
     * braziers beside it. Inside the great hall: the counter of the registrar at the back with the desk, his post and his
     * chest, casks and shelves behind it, trophies on the east wall, the hearth on the west, a stone table with benches, the
     * contract board beside the door; a ladder climbs to the loft with the map room.
     */
    private static Drawn guildB() {
        Structure s = new Structure(13, 20, 15);
        Mountain.Plan hall = Mountain.Plan.box(1, 2, 11, 11);
        int top = 10;
        int loft = 6;
        int y = 1;
        Mountain.batter(s, hall, 0, 1, 2);
        Mountain.walls(s, hall, 0, top, 1, WALL);
        Mountain.Plan in = hall.grow(-1);
        Mountain.plate(s, in, 0, FLOOR);
        Mountain.plate(s, in, loft, PLANKS);
        Mountain.ring(s, hall, 2, DARK);
        Mountain.ring(s, hall, loft, DARK);
        Mountain.slabRoof(s, 1, 1, 11, 12, top, Mountain.GABLE_X, WALL);
        Mountain.brackets(s, 2, 2, 10, 2, top - 1, "north", 1, 2);
        // windows: on the sides below and in the loft, two at the back of the loft
        window(s, 1, 2, 4, "west", 2, 1, AMBER);
        window(s, 1, 2, 9, "west", 2, 1, AMBER);
        window(s, 11, 2, 8, "east", 2, 1, AMBER);
        window(s, 11, 2, 9, "east", 2, 1, AMBER);
        for (int z : new int[]{4, 9}) {
            window(s, 1, 8, z, "west", 1, 1, AMBER);
            window(s, 11, 8, z, "east", 1, 1, AMBER);
        }
        window(s, 4, 8, 2, "north", 1, 1, AMBER);
        window(s, 8, 8, 2, "north", 1, 1, AMBER);
        // the hearth with its chimney breast up the west gable
        Mountain.fireplace(s, 2, y, 7, "east", 13);
        breast(s, 0, 7, 2, 13, "west");
        Mountain.stack(s, 1, 7, 14, 16, 3);
        // the portal block
        portalBlock(s, 3, 9, 11, 12, 13, 0, false);
        Mountain.archDoor(s, 6, y, 11, "south", 1, 2);
        Mountain.face(s, 6, 6, 12, "south", 0);
        Mountain.hanging(s, 2, 9, 12, "south", 6);
        Mountain.hanging(s, 10, 9, 12, "south", 6);
        Mountain.brazier(s, 1, 0, 14, false);
        Mountain.brazier(s, 11, 0, 14, false);

        // the great hall
        registrar(s, 6, y, 5, 1);
        Mountain.casks(s, 2, 3, 4, 3, y, "south");
        Furnish.books(s, 8, 3, 10, 3, y, y + 1);
        s.set(9, y + 2, 3, Furnish.candles(2));
        trophies(s, 10, y, 4, "west", 3);
        Mountain.stoneTable(s, 4, 8, 7, 8, y, true);
        board(s, 8, y, 11, "north", 3);
        ladder(s, 10, y, 8, "west", loft);
        lamp(s, 4, loft - 1, 6);
        lamp(s, 8, loft - 1, 6);
        lamp(s, 6, loft - 1, 9);
        lamp(s, 3, loft - 1, 9);

        // the loft: the map room
        int l = loft + 1;
        s.set(6, l, 3, LECTERN);
        Furnish.books(s, 2, 3, 4, 3, l, l + 1);
        Furnish.books(s, 8, 3, 10, 3, l, l + 1);
        paintedMap(s, 4, l + 1, 11, "north");
        Furnish.rug(s, 5, 5, 7, 8, l, Mountain.CARPET);
        mapTable(s, 6, l, 6);
        Mountain.stoneTable(s, 3, 9, 5, 9, l, false);
        Furnish.weaponRack(s, 2, l, 5, "east", WOOD);
        Furnish.weaponRack(s, 2, l, 6, "east", WOOD);
        Furnish.crate(s, 10, l, 4, WOOD);
        Furnish.sack(s, 10, l, 5, false);
        s.set(2, l + 2, 7, Kit.wallBanner(GUILD, "east"));
        lamp(s, 4, top, 6);
        lamp(s, 8, top, 6);
        lamp(s, 6, top, 9);
        s.connect();
        return new Drawn(s, new int[]{6, y, 10}, new int[]{8, y, 6});
    }

    /**
     * Rank A: a broad hall under a low roof of slabs with coped gables, the stack of its hearth on the west gable, and before
     * its middle a low thick tower: the carved face of a forefather over its deep door, a frieze of dark stone, corbels and a
     * crenellated top with a great brazier. The tower holds the vestibule, a trophy room and a watch room, a ladder up
     * through them; arched doors join it to the great hall below and to the upper floor of the hall, where the map room and
     * the chamber of the guild master with a bed of state lie. In the great hall the counter with the desk, the hearth,
     * trophies, the contract board in the east wall and two stone tables with benches.
     */
    private static Drawn guildA() {
        Structure s = new Structure(19, 21, 19);
        Mountain.Plan hall = Mountain.Plan.box(1, 1, 17, 11);
        int top = 11;
        int up = 6;
        int y = 1;
        hallShell(s, hall, 0, top, 1, 2);
        Mountain.Plan in = hall.grow(-2);
        Mountain.plate(s, in, up, PLANKS);
        Mountain.ring(s, hall, up, DARK);
        Mountain.slabRoof(s, 1, 0, 17, 12, top, Mountain.GABLE_X, WALL);
        Mountain.brackets(s, 3, 1, 15, 1, top - 1, "north", 1, 2);
        Mountain.fireplace(s, 3, y, 6, "east", 15);
        Mountain.stack(s, 2, 6, 14, 17, 3);
        window(s, 6, 2, 1, "north", 2, 2, AMBER);
        window(s, 13, 2, 1, "north", 2, 2, AMBER);
        window(s, 10, 8, 1, "north", 2, 2, AMBER);
        window(s, 14, 8, 1, "north", 2, 2, AMBER);
        window(s, 1, 2, 9, "west", 2, 2, AMBER);
        window(s, 17, 2, 9, "east", 2, 2, AMBER);
        window(s, 1, 8, 4, "west", 2, 2, AMBER);
        window(s, 1, 8, 8, "west", 2, 2, AMBER);
        window(s, 17, 8, 4, "east", 2, 2, AMBER);
        window(s, 17, 8, 8, "east", 2, 2, AMBER);
        window(s, 3, 2, 11, "south", 2, 2, AMBER);
        window(s, 15, 2, 11, "south", 2, 2, AMBER);
        window(s, 3, 8, 11, "south", 2, 2, AMBER);
        window(s, 15, 8, 11, "south", 2, 2, AMBER);

        // the tower before the hall
        Mountain.Plan tower = Mountain.Plan.box(5, 10, 13, 16);
        keep(s, tower, 0, 2, 16, 1, Mountain.FLAT_TOP, new int[]{up, 11}, 11, 14, "west", hall, 1);
        window(s, 5, 8, 13, "west", 2, 2, AMBER);
        window(s, 5, 12, 13, "west", 2, 2, AMBER);
        window(s, 13, 8, 12, "east", 2, 2, AMBER);
        window(s, 13, 12, 12, "east", 2, 2, AMBER);
        Mountain.archDoor(s, 9, y, 15, "south", 1, 2);
        Mountain.face(s, 9, 6, 16, "south", 0);
        Mountain.archDoor(s, 9, y, 11, "north", 1, 1);
        passage(s, 9, up + 1, 11, 10, "north");
        Mountain.hanging(s, 2, 10, 12, "south", 6);
        Mountain.hanging(s, 16, 10, 12, "south", 6);
        Mountain.brazier(s, 2, 0, 15, true);
        Mountain.brazier(s, 16, 0, 15, true);

        // the great hall
        registrar(s, 9, y, 5, 1);
        Furnish.books(s, 7, 3, 8, 3, y, y + 1);
        Furnish.books(s, 11, 3, 12, 3, y, y + 1);
        trophies(s, 3, y, 3, "south", 3);
        Mountain.casks(s, 14, 3, 15, 3, y, "south");
        board(s, 16, y, 6, "west", 3);
        Mountain.stoneTable(s, 4, 8, 7, 8, y, true);
        Mountain.stoneTable(s, 11, 8, 14, 8, y, true);
        lamps(s, 0, up - 1, 0, new int[]{5, 5}, new int[]{13, 5}, new int[]{9, 7}, new int[]{4, 6}, new int[]{14, 6});

        // the upper floor: the map room and the chamber of the guild master
        int l = up + 1;
        s.set(4, l, 3, LECTERN);
        Furnish.books(s, 5, 3, 8, 3, l, l);
        paintedMap(s, 4, l + 1, 2, "south");
        Furnish.books(s, 3, 5, 3, 7, l, l + 1);
        mapTable(s, 6, l, 6);
        Furnish.rug(s, 5, 5, 7, 8, l, Mountain.CARPET);
        Furnish.canopyBed(s, 13, l, 4, "north", WOOD);
        Furnish.desk(s, 15, l, 7, "west", WOOD);
        Furnish.rug(s, 11, 6, 14, 8, l, Mountain.CARPET);
        Furnish.weaponRack(s, 11, l, 3, "south", WOOD);
        s.set(15, l + 2, 3, Kit.wallBanner(GUILD, "south"));
        lamps(s, 0, top - 1, 0, new int[]{5, 5}, new int[]{13, 6}, new int[]{9, 8}, new int[]{9, 4});

        // the tower: vestibule, trophy room, watch room
        s.set(7, y + 2, 13, Kit.wallBanner(GUILD, "east"));
        trophies(s, 7, l, 14, "east", 3);
        Furnish.weaponRack(s, 11, l, 12, "west", WOOD);
        Mountain.stoneTable(s, 8, 13, 9, 13, 12, true);
        Furnish.crate(s, 7, 12, 14, WOOD);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{9, y, 14}, new int[]{11, y, 6});
    }

    /**
     * Rank S: the guild on a terrace with a flight of stairs between sloping cheeks: a long hall under a low roof of slabs
     * with coped gables and the stack of its hearth, timber balconies on brackets on its front at the height of its upper
     * floor, and before its middle a tower wider than deep that carries the great carved face of a forefather over a
     * portal three doors wide, its top jutting out on two rings of corbels round a great brazier; red hangings, braziers and
     * rune stones on the terrace. Inside the great hall between two pillars: the counter, the hearth, trophies, casks, the
     * contract board and two stone tables; upstairs the map room, a council table and the chamber of the guild master;
     * in the tower the vestibule, a trophy room and an armoury.
     */
    private static Drawn guildS() {
        Structure s = new Structure(23, 29, 25);
        int y = 3;
        Mountain.Plan hall = Mountain.Plan.box(1, 1, 21, 13);
        Mountain.Plan terrace = Mountain.Plan.box(1, 14, 21, 20);
        int top = 15;
        int up = 9;
        Mountain.terrace(s, 1, 14, 21, 20, 0, y, "south", 5);
        Mountain.walls(s, hall, 0, top, 2, WALL);
        batter(s, hall, 0, 1, 2, terrace);
        Mountain.Plan in = hall.grow(-2);
        for (int yy = 0; yy < y - 1; yy++) {
            Mountain.plate(s, in, yy, ROUGH);
        }
        Mountain.plate(s, in, y - 1, FLOOR);
        Mountain.plate(s, in, up, PLANKS);
        Mountain.ring(s, hall, 2, DARK);
        Mountain.ring(s, hall, up, DARK);
        Mountain.slabRoof(s, 1, 0, 21, 14, top, Mountain.GABLE_X, WALL);
        Mountain.brackets(s, 3, 1, 19, 1, top - 1, "north", 1, 2);
        Mountain.brackets(s, 3, 13, 19, 13, top - 1, "south", 1, 2);
        Mountain.fireplace(s, 3, y, 7, "east", 19);
        Mountain.stack(s, 2, 7, 18, 21, 3);
        for (int x : new int[]{5, 17}) {
            window(s, x, 5, 1, "north", 2, 2, AMBER);
        }
        for (int x : new int[]{5, 11, 17}) {
            window(s, x, 11, 1, "north", 2, 2, AMBER);
        }
        for (int z : new int[]{4, 10}) {
            window(s, 1, 5, z, "west", 2, 2, AMBER);
            window(s, 21, 5, z, "east", 2, 2, AMBER);
            window(s, 1, 11, z, "west", 2, 2, AMBER);
            window(s, 21, 11, z, "east", 2, 2, AMBER);
        }
        window(s, 3, 5, 13, "south", 2, 2, AMBER);
        window(s, 19, 5, 13, "south", 2, 2, AMBER);

        // the tower before the hall, standing on the terrace
        Mountain.Plan tower = Mountain.Plan.box(6, 10, 16, 18);
        keep(s, tower, y - 1, y, 23, 0, Mountain.FLAT_TOP, new int[]{up, 16}, 14, 14, "west", hall, 2);
        window(s, 6, 11, 14, "west", 2, 2, AMBER);
        window(s, 6, 18, 14, "west", 2, 2, AMBER);
        window(s, 16, 11, 13, "east", 2, 2, AMBER);
        window(s, 16, 18, 13, "east", 2, 2, AMBER);
        Mountain.archDoor(s, 11, y, 17, "south", 3, 2);
        Mountain.face(s, 11, 8, 18, "south", 1);
        Mountain.archDoor(s, 11, y, 11, "north", 3, 1);
        passage(s, 11, up + 1, 11, 10, "north");
        Mountain.hanging(s, 6, 19, 19, "south", 8);
        Mountain.hanging(s, 16, 19, 19, "south", 8);
        // balconies on the front of the upper floor
        Mountain.balcony(s, 2, 13, 5, 13, up, "south", 2);
        Mountain.balcony(s, 17, 13, 20, 13, up, "south", 2);
        passage(s, 4, up + 1, 12, 13, "south");
        passage(s, 18, up + 1, 12, 13, "south");
        Mountain.runeStone(s, 3, y, 17, "south", 2);
        Mountain.runeStone(s, 19, y, 17, "south", 2);
        Mountain.brazier(s, 6, y, 20 - 1, false);
        Mountain.brazier(s, 16, y, 20 - 1, false);

        // the great hall
        registrar(s, 11, y, 5, 1);
        Furnish.books(s, 8, 3, 10, 3, y, y + 1);
        Furnish.books(s, 13, 3, 14, 3, y, y + 1);
        Mountain.column(s, 7, 5, y, up - 1);
        Mountain.column(s, 15, 5, y, up - 1);
        trophies(s, 3, y, 3, "south", 3);
        Mountain.casks(s, 17, 3, 19, 3, y, "south");
        board(s, 20, y, 7, "west", 3);
        Mountain.stoneTable(s, 5, 8, 9, 8, y, true);
        Mountain.stoneTable(s, 13, 8, 17, 8, y, true);
        Furnish.crate(s, 3, y, 11, WOOD);
        Furnish.sack(s, 4, y, 11, false);
        Furnish.weaponRack(s, 19, y, 11, "north", WOOD);
        lamps(s, 0, up - 1, 0, new int[]{4, 4}, new int[]{18, 4}, new int[]{11, 8}, new int[]{4, 10}, new int[]{18, 10},
                new int[]{9, 5}, new int[]{13, 5}, new int[]{7, 8}, new int[]{15, 8});

        // the upper floor
        int l = up + 1;
        Furnish.rug(s, 9, 8, 13, 9, l, Mountain.CARPET);
        Furnish.rug(s, 15, 6, 18, 9, l, Mountain.CARPET);
        s.set(4, l, 3, LECTERN);
        Furnish.books(s, 5, 3, 8, 3, l, l);
        paintedMap(s, 4, l + 1, 2, "south");
        mapTable(s, 6, l, 6);
        Mountain.stoneTable(s, 9, 6, 13, 6, l, true);
        Furnish.canopyBed(s, 17, l, 4, "north", WOOD);
        Furnish.desk(s, 19, l, 8, "west", WOOD);
        Furnish.weaponRack(s, 15, l, 3, "south", WOOD);
        Furnish.weaponRack(s, 19, l, 3, "south", WOOD);
        s.set(3, l + 2, 8, Kit.wallBanner(GUILD, "east"));
        s.set(19, l + 2, 11, Kit.wallBanner(GUILD, "north"));
        lamps(s, 0, top - 2, 0, new int[]{5, 6}, new int[]{11, 9}, new int[]{17, 7}, new int[]{11, 4}, new int[]{4, 10},
                new int[]{18, 10});

        // the tower: vestibule, trophy room, armoury
        s.set(8, y + 2, 14, Kit.wallBanner(GUILD, "east"));
        s.set(14, y + 2, 16, Kit.wallBanner(GUILD, "west"));
        trophies(s, 8, l, 16, "east", 5);
        Furnish.weaponRack(s, 13, l, 16, "north", WOOD);
        Mountain.stoneTable(s, 11, 13, 12, 13, l, true);
        int a = 17;
        for (int x = 8; x <= 12; x += 2) {
            Furnish.weaponRack(s, x, a, 16, "north", WOOD);
        }
        Mountain.stoneTable(s, 9, 13, 12, 13, a, false);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{11, y, 16}, new int[]{13, y, 6});
    }

    /**
     * Rank SS: a great hall on two terraces, a broad forecourt with a flight of seven steps and over it the upper terrace
     * before the hall with a flight of five. The hall stands on a battered foot under a low roof of slabs with coped gables
     * and the stack of its hearth; before its middle a portal block carries the great carved face over a portal of three
     * doors in three receding orders, hangings beside it and a great brazier on its crenellated top; timber balconies on
     * brackets on its front. At its back corners two towers of different height and shape: a square one on two rings of
     * corbels under a low pyramid with a gilt point, the highest of the guild, and a lower one with cut corners and a flat top
     * with a great brazier. Inside, the great hall of four pillars with the counter of the registrar, the hearth with the head
     * of a dragon over it, trophies, the contract board and long tables; timber galleries run round it inside and join the
     * towers and the balconies. The square tower holds the armoury, the map room and the chamber of the guild master; the
     * other the stores, the council room and a watch room.
     */
    private static Drawn guildSS() {
        Structure s = new Structure(31, 35, 33);
        int y = 4;
        // the forecourt and the upper terrace
        Mountain.terrace(s, 1, 25, 29, 29, 0, 2, "south", 7);
        Mountain.terrace(s, 3, 21, 27, 24, 0, y, "south", 0);
        for (int x = 13; x <= 17; x++) {
            put(s, x, y, 24, AIR);
            put(s, x, y + 1, 24, AIR);
        }
        Mountain.flight(s, 15, y - 1, 25, "south", 5, 2);

        // the great hall
        Mountain.Plan hall = Mountain.Plan.box(4, 6, 26, 20);
        int top = 18;
        int ceiling = 17;
        int gallery = 11;
        hallShell(s, hall, y - 1, top, 1, 2);
        Mountain.ring(s, hall, gallery, DARK);
        Mountain.slabRoof(s, 4, 5, 26, 21, top, Mountain.GABLE_X, WALL);
        Mountain.fireplace(s, 6, y, 16, "east", 22);
        Mountain.stack(s, 5, 16, 21, 24, 3);
        put(s, 6, y + 5, 16, "minecraft:dragon_wall_head[facing=east]");

        // the towers at the back corners
        Mountain.Plan west = Mountain.Plan.box(2, 2, 12, 12);
        keep(s, west, y - 1, 2, 24, 1, Mountain.HELM_TOP, new int[]{gallery, 18}, 7, 4, "south", hall, 2);
        Mountain.Plan east = Mountain.Plan.of(24, 6, 9, Mountain.CHAMFER);
        keep(s, east, y - 1, 2, 21, 1, Mountain.FLAT_TOP, new int[]{gallery, 16}, 24, 4, "south", hall, 1);
        for (int yy : new int[]{6, 14, 20}) {
            window(s, 2, yy, 7, "west", 2, 2, AMBER);
            window(s, 7, yy, 2, "north", 2, 2, AMBER);
        }
        for (int yy : new int[]{6, 13, 18}) {
            window(s, 28, yy, 6, "east", 2, 2, AMBER);
            window(s, 24, yy, 2, "north", 2, 2, AMBER);
        }

        // the portal block, the balconies and the windows of the front
        int px1 = 10;
        int px2 = 20;
        fill(s, px1, 0, 19, px2, 22, 22, WALL);
        Mountain.archDoor(s, 15, y, 19, "south", 3, 3);
        Mountain.face(s, 15, 10, 22, "south", 1);
        Mountain.Plan portal = Mountain.Plan.box(px1, 19, px2, 22);
        Mountain.runeBand(s, px1, 22, px2, 22, 22);
        Mountain.corbels(s, portal, 23, 1);
        Mountain.plate(s, portal.grow(1), 24, FLOOR);
        Mountain.parapet(s, portal.grow(1), 25);
        Mountain.brazier(s, 15, 25, 20, true);
        Mountain.hanging(s, px1, 20, 23, "south", 9);
        Mountain.hanging(s, px2, 20, 23, "south", 9);
        Mountain.balcony(s, 5, 20, 9, 20, gallery, "south", 2);
        Mountain.balcony(s, 21, 20, 25, 20, gallery, "south", 2);
        for (int x : new int[]{6, 24}) {
            window(s, x, 6, 20, "south", 2, 2, AMBER);
        }
        for (int x : new int[]{14, 18}) {
            window(s, x, 13, 6, "north", 2, 2, AMBER);
        }
        for (int z : new int[]{14, 18}) {
            window(s, 4, 6, z, "west", 2, 2, AMBER);
            window(s, 4, 13, z, "west", 2, 2, AMBER);
        }
        for (int z : new int[]{12, 19}) {
            window(s, 26, 6, z, "east", 2, 2, AMBER);
        }
        window(s, 26, 13, 13, "east", 2, 2, AMBER);
        window(s, 26, 13, 17, "east", 2, 2, AMBER);
        Mountain.brazier(s, 4, 2, 27, true);
        Mountain.brazier(s, 26, 2, 27, true);
        Mountain.runeStone(s, 9, 2, 26, "south", 3);
        Mountain.runeStone(s, 21, 2, 26, "south", 3);
        for (int z : new int[]{16, 20}) {
            Mountain.pier(s, 4, 0, top - 1, z, "west", 1);
        }
        for (int z : new int[]{15, 20}) {
            Mountain.pier(s, 26, 0, top - 1, z, "east", 1);
        }
        Mountain.brazier(s, 6, y, 23, false);
        Mountain.brazier(s, 24, y, 23, false);

        // the galleries inside, the doors to the towers and to the balconies
        int g = gallery + 1;
        Mountain.balcony(s, 5, 13, 5, 18, gallery, "east", 2);
        Mountain.balcony(s, 25, 11, 25, 18, gallery, "west", 2);
        Mountain.balcony(s, 13, 7, 19, 7, gallery, "south", 2);
        open(s, 7, g, 13);
        open(s, 7, g, 18);
        open(s, 23, g, 11);
        open(s, 23, g, 18);
        open(s, 13, g, 8);
        open(s, 19, g, 8);
        passage(s, 7, g, 11, 12, "south");
        passage(s, 11, g, 8, 12, "east");
        passage(s, 23, g, 9, 10, "south");
        passage(s, 21, g, 8, 20, "west");
        passage(s, 7, g, 19, 20, "south");
        passage(s, 23, g, 19, 20, "south");
        Mountain.archDoor(s, 9, y, 11, "south", 1, 1);
        Mountain.archDoor(s, 23, y, 9, "south", 1, 1);

        // the great hall
        registrar(s, 16, y, 11, 1);
        Furnish.books(s, 13, 8, 15, 8, y, y + 2);
        Furnish.books(s, 18, 8, 19, 8, y, y + 2);
        for (int[] c : new int[][]{{12, 13}, {18, 13}, {12, 16}, {18, 16}}) {
            Mountain.column(s, c[0], c[1], y, ceiling - 1);
        }
        trophies(s, 12, y, 18, "north", 5);
        trophies(s, 23, y, 18, "north", 5);
        board(s, 25, y, 15, "west", 5);
        Mountain.stoneTable(s, 8, 15, 10, 15, y, true);
        Mountain.stoneTable(s, 20, 15, 22, 15, y, true);
        Furnish.rug(s, 14, 12, 16, 18, y, Mountain.CARPET);
        // tie beams over the pillars carry the roof, the great chandeliers hang from them
        for (int z : new int[]{13, 16}) {
            for (int x = 6; x <= 24; x++) {
                put(s, x, ceiling, z, Kit.log(Mountain.BEAM, "x"));
            }
        }
        for (int x = 13; x <= 19; x++) {
            put(s, x, ceiling, 10, Kit.log(Mountain.BEAM, "x"));
        }
        Furnish.greatChandelier(s, 15, 13, ceiling - 1, 3);
        Furnish.greatChandelier(s, 15, 16, ceiling - 1, 3);
        Furnish.chandelier(s, 9, 16, ceiling - 1, 2);
        Furnish.chandelier(s, 21, 16, ceiling - 1, 2);
        Furnish.chandelier(s, 16, 10, ceiling - 1, 2);
        lamps(s, 0, gallery - 1, 0, new int[]{6, 14}, new int[]{6, 17}, new int[]{24, 12}, new int[]{24, 16},
                new int[]{14, 9}, new int[]{18, 9});
        lamps(s, 0, 20, 0, new int[]{9, 11}, new int[]{21, 11}, new int[]{9, 19}, new int[]{21, 19}, new int[]{15, 8},
                new int[]{15, 19});

        // the square tower: armoury, map room, chamber of the guild master
        trophies(s, 4, y, 10, "east", 5);
        Furnish.weaponRack(s, 10, y, 5, "west", WOOD);
        Furnish.weaponRack(s, 10, y, 7, "west", WOOD);
        Mountain.stockPile(s, 5, 4, 6, 5, y);
        Mountain.stoneTable(s, 6, 8, 8, 8, y, false);
        s.set(5, g, 4, LECTERN);
        Furnish.books(s, 4, 4, 4, 4, g, g + 1);
        Furnish.books(s, 8, 4, 10, 4, g, g + 1);
        paintedMap(s, 3, g + 1, 5, "east");
        mapTable(s, 7, g, 7);
        Furnish.rug(s, 5, 6, 9, 9, g, Mountain.CARPET);
        int m = 19;
        Furnish.canopyBed(s, 5, m, 6, "north", WOOD);
        Furnish.desk(s, 9, m, 9, "north", WOOD);
        Furnish.rug(s, 4, 7, 7, 10, m, Mountain.CARPET);
        Furnish.books(s, 9, 4, 10, 4, m, m + 1);
        Furnish.weaponRack(s, 10, m, 6, "west", WOOD);
        s.set(4, m + 2, 9, Kit.wallBanner(GUILD, "east"));
        lamps(s, 7, m + 3, 7, new int[]{-2, 2}, new int[]{2, -1});
        // the tower with cut corners: stores, council room, watch room
        Mountain.casks(s, 26, 4, 26, 7, y, "west");
        Furnish.crate(s, 22, y, 4, WOOD);
        Furnish.crate(s, 22, y, 5, WOOD);
        Furnish.sack(s, 23, y, 4, true);
        Furnish.sack(s, 22, y, 6, false);
        Mountain.stoneTable(s, 23, 6, 25, 6, g, true);
        s.set(26, g + 2, 6, Kit.wallBanner(GUILD, "west"));
        Furnish.weaponRack(s, 22, 17, 7, "east", WOOD);
        Furnish.bench(s, 26, 5, 26, 7, 17, "west", WOOD);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{15, y, 18}, new int[]{18, y, 11});
    }

    // ---------------------------------------------------------------- parts of the guilds

    /**
     * The shell of a hall: walls two thick from the ground up to layer {@code top} on a battered foot {@code out} blocks out,
     * rough stone filling its inside up to its floor of dressed stone at layer {@code floor}, a band of dark stone at layer
     * {@code band}.
     */
    private static void hallShell(Structure s, Mountain.Plan p, int floor, int top, int out, int band) {
        if (out > 0) {
            Mountain.batter(s, p, 0, out, 2);
        }
        Mountain.walls(s, p, 0, top, 2, WALL);
        Mountain.Plan in = p.grow(-2);
        for (int yy = 0; yy < floor; yy++) {
            Mountain.plate(s, in, yy, ROUGH);
        }
        Mountain.plate(s, in, floor, FLOOR);
        Mountain.ring(s, p, band, DARK);
    }

    /**
     * A battered foot like {@link Mountain#batter} that leaves out the columns of another plan (a hall a tower stands
     * against, a terrace a hall stands behind).
     */
    private static void batter(Structure s, Mountain.Plan wall, int y, int out, int rise, Mountain.Plan except) {
        for (int k = 0; k < out; k++) {
            Mountain.Plan p = wall.grow(out - k);
            for (int j = 0; j < rise; j++) {
                int layer = y + k * rise + j;
                boolean top = j == rise - 1;
                for (int z = p.z1(); z <= p.z2(); z++) {
                    for (int x = p.x1(); x <= p.x2(); x++) {
                        if (!p.has(x, z) || wall.has(x, z) || except != null && except.has(x, z)) {
                            continue;
                        }
                        String state = top && p.rim(x, z) ? Mountain.slope(p, x, z, Mountain.ROUGH_STAIRS, false) : null;
                        put(s, x, layer, z, state != null ? state : ROUGH);
                    }
                }
            }
        }
    }

    /**
     * A low thick tower of the guild on the plan {@code p}, standing against a hall (whose plan is {@code except}) or free:
     * whatever stood in its columns is cleared first; a battered foot {@code out} blocks out (none where the hall stands);
     * walls two thick from the ground to under its top at layer {@code f}; rough stone inside up to its ground floor of
     * dressed stone at layer {@code floor}; a band of dark stone at layer {@code band} and another under the corbels, dark
     * quoins on the corners of a square; floors of planks at the layers of {@code storeys} with a lantern under every
     * ceiling, a ladder at (lx, lz) looking {@code lf} from the ground floor up; corbels {@code deep} rings deep, the top
     * floor jutting out over them with a crenellated parapet, and in it a great brazier with the ladder coming up through a
     * hatch ({@link Mountain#FLAT_TOP}) or a low pyramid with a gilt point ({@link Mountain#HELM_TOP}).
     */
    private static void keep(Structure s, Mountain.Plan p, int floor, int band, int f, int out, int top, int[] storeys, int lx,
                             int lz, String lf, Mountain.Plan except, int deep) {
        for (int z = p.z1(); z <= p.z2(); z++) {
            for (int x = p.x1(); x <= p.x2(); x++) {
                if (p.has(x, z)) {
                    for (int yy = 0; yy < s.sizeY; yy++) {
                        s.set(x, yy, z, AIR);
                    }
                }
            }
        }
        if (out > 0) {
            batter(s, p, 0, out, 2, except);
        }
        Mountain.walls(s, p, 0, f - 1, 2, WALL);
        Mountain.Plan in = p.grow(-2);
        for (int yy = 0; yy < floor; yy++) {
            Mountain.plate(s, in, yy, ROUGH);
        }
        Mountain.plate(s, in, floor, FLOOR);
        Mountain.ring(s, p, band, DARK);
        Mountain.ring(s, p, f - deep - 1, DARK);
        if (p.cut() == 0 && !p.round()) {
            for (int yy = band + 1; yy < f - deep - 1; yy++) {
                for (int z = p.z1(); z <= p.z2(); z++) {
                    for (int x = p.x1(); x <= p.x2(); x++) {
                        boolean edgeX = x == p.x1() || x == p.x2();
                        boolean edgeZ = z == p.z1() || z == p.z2();
                        int ax = Math.min(x - p.x1(), p.x2() - x);
                        int az = Math.min(z - p.z1(), p.z2() - z);
                        boolean even = Math.floorMod(yy - band, 2) == 0;
                        if (edgeX && edgeZ || edgeZ && ax == 1 && even || edgeX && az == 1 && !even) {
                            put(s, x, yy, z, DARK);
                        }
                    }
                }
            }
        }
        List<Integer> floors = new ArrayList<>();
        floors.add(floor);
        for (int fl : storeys) {
            Mountain.plate(s, in, fl, PLANKS);
            floors.add(fl);
        }
        int ladderTop = top == Mountain.FLAT_TOP ? f : storeys[storeys.length - 1];
        for (int yy = floor + 1; yy <= ladderTop; yy++) {
            put(s, lx, yy, lz, Kit.ladder(lf));
        }
        for (int i = 0; i < floors.size(); i++) {
            int ceiling = i + 1 < floors.size() ? floors.get(i + 1) : f;
            put(s, in.cx(), ceiling - 1, in.cz(), Kit.hangingLantern());
        }
        Mountain.corbels(s, p, f - 1, deep);
        Mountain.Plan q = p.grow(deep);
        Mountain.plate(s, q, f, FLOOR);
        Mountain.parapet(s, q, f + 1);
        if (top == Mountain.FLAT_TOP) {
            put(s, lx, f, lz, Kit.ladder(lf));
            Mountain.brazier(s, in.cx(), f + 1, in.cz(), true);
        } else {
            Mountain.helm(s, q.grow(-1), f + 1, Mountain.HELM);
        }
    }

    /**
     * A portal block before the middle of a hall front: solid stone from (x1, z1) to (x2, z2) (z2 is its face) from layer
     * {@code base} up to layer {@code top}, a frieze of runes on its face at the top, over it corbels and a paved top jutting
     * out one block with a crenellated parapet and a brazier in its middle (a great one if {@code big}). The door, the face
     * and the hangings are the caller's.
     */
    private static void portalBlock(Structure s, int x1, int x2, int z1, int z2, int top, int base, boolean big) {
        Mountain.Plan p = Mountain.Plan.box(x1, z1, x2, z2);
        fill(s, x1, base, z1, x2, top, z2, WALL);
        Mountain.runeBand(s, x1, z2, x2, z2, top);
        Mountain.corbels(s, p, top + 1, 1);
        Mountain.Plan q = p.grow(1);
        Mountain.plate(s, q, top + 2, FLOOR);
        Mountain.parapet(s, q, top + 3);
        Mountain.brazier(s, (x1 + x2) / 2, top + 3, (z1 + z2) / 2, big);
    }

    /**
     * A chimney breast of rough stone three wide and one deep before a wall that looks {@code facing}: (x, z) is the middle
     * of it, from layer {@code y1} to {@code y2}, with a band of dark stone under its head.
     */
    private static void breast(Structure s, int x, int z, int y1, int y2, String facing) {
        int[] a = Furnish.step(Furnish.left(facing));
        for (int u = -1; u <= 1; u++) {
            for (int yy = y1; yy <= y2; yy++) {
                put(s, x + a[0] * u, yy, z + a[1] * u, yy == y2 - 1 ? DARK : ROUGH);
            }
        }
    }

    /**
     * A doorway through a wall two thick: a door at (x, y, z) with its leaf looking {@code facing}, and the cell beside it
     * toward that way along the row {@code z2} (or the column, for an east or west door) cleared two high.
     *
     * @param to the row (or column) of the cleared cell on the other side
     */
    private static void passage(Structure s, int x, int y, int z, int to, String facing) {
        boolean alongZ = facing.equals("north") || facing.equals("south");
        s.door(x, y, z, Mountain.DOOR, facing, "left");
        int cx = alongZ ? x : to;
        int cz = alongZ ? to : z;
        put(s, cx, y, cz, AIR);
        put(s, cx, y + 1, cz, AIR);
    }

    /**
     * Opens the railing of a gallery where a door gives onto it: the post at (x, y, z) and a lantern on it.
     */
    private static void open(Structure s, int x, int y, int z) {
        put(s, x, y, z, AIR);
        put(s, x, y + 1, z, AIR);
    }

    // ---------------------------------------------------------------- furniture of the guild

    /**
     * The counter of the registrar with the guild desk in its middle looking south, his post two blocks behind it and the
     * one chest of the guild beside the post ({@code side} 1 east, -1 west); a counter of dressed stone on both sides with
     * candles and a lantern.
     *
     * @param x the column of the desk
     * @param z the row of the counter
     */
    private static void registrar(Structure s, int x, int y, int z, int side) {
        s.set(x - 1, y, z, Kit.topSlab(Mountain.TRIM_SLAB)).set(x - 1, y + 1, z, Furnish.candles(2));
        s.set(x, y, z, DESK);
        s.set(x + 1, y, z, Kit.topSlab(Mountain.TRIM_SLAB)).set(x + 1, y + 1, z, Kit.lantern());
        s.set(x, y, z - 2, WORK_POST);
        s.set(x + side, y, z - 2, Kit.chest("south"));
    }

    /**
     * Trophies of the hunt along a wall: on cells before the wall that looks {@code facing}, {@code count} of them from
     * (x, z) to the left of {@code facing}: racks of weapons (grindstones) with skulls of beasts hung on the wall over
     * them, and between them posts of dark stone carrying skulls; the banner of the guild over the middle.
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
                put(s, px, y, pz, Mountain.DARK_WALL);
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
     * The contract board set in a wall: (x, z) is the middle of it in the inner face of a wall two deep, looking
     * {@code facing} into the room; a panel of dark stone {@code width} wide and three high under dressed stone and a carved
     * rune, notices of white and yellow cloth hanging on it, a ledge of stone before it with papers and candles.
     */
    private static void board(Structure s, int x, int y, int z, String facing, int width) {
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        int half = (width - 1) / 2;
        for (int u = -half; u <= half; u++) {
            int px = x + a[0] * u;
            int pz = z + a[1] * u;
            for (int yy = y; yy <= y + 2; yy++) {
                put(s, px, yy, pz, DARK);
            }
            put(s, px, y + 3, pz, u == 0 ? Mountain.RUNE : TRIM);
            int fx = px + f[0];
            int fz = pz + f[1];
            put(s, fx, y, fz, Kit.topSlab(Mountain.TRIM_SLAB));
            put(s, fx, y + 1, fz, u == 0 ? Furnish.candles(3) : "minecraft:white_carpet");
            String color = Math.floorMod(u, 3) == 1 ? "yellow" : "white";
            put(s, fx, y + 2, fz, Kit.wallBanner(color, facing));
        }
    }

    /**
     * A map of the lands of the guild painted in the inner face of a wall that looks {@code facing}: lands, seas and hills
     * of terracotta five wide and two high, its lower row at {@code y}, from (x, z) toward the east along a wall that looks
     * north or south, toward the south along one that looks east or west.
     */
    private static void paintedMap(Structure s, int x, int y, int z, String facing) {
        String[] map = {"lgyBc", "gcglb"};
        boolean alongX = facing.equals("north") || facing.equals("south");
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
                put(s, alongX ? x + i : x, y + 1 - row, alongX ? z : z + i, "minecraft:" + color + "_terracotta");
            }
        }
    }

    /**
     * The map table: the cartography table with stools of the timber at its sides and before it.
     */
    private static void mapTable(Structure s, int x, int y, int z) {
        put(s, x, y, z, CARTOGRAPHY);
        put(s, x - 1, y, z, Kit.stairs(Mountain.STAIRS, "west"));
        put(s, x + 1, y, z, Kit.stairs(Mountain.STAIRS, "east"));
        put(s, x, y, z + 1, Kit.stairs(Mountain.STAIRS, "south"));
    }

    // ================================================================ the mages' tower

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

    /** Kinds of rooms in the storeys of a mage tower. */
    private static final int BEDROOM = 1;
    private static final int LIBRARY = 2;
    private static final int BREWERY = 3;
    private static final int RESEARCH = 4;

    /**
     * Rank B: one round tower eleven wide on a battered foot, a frieze of runes round its shaft, a heavy top on corbels
     * with its crown of crystal. The focus with the post and chest of the mage and a brewing corner on the ground floor; the
     * bedroom of one mage, the library and the research room above, one over the other, a ladder up through them all.
     */
    private static Drawn mageB() {
        Structure s = new Structure(13, 28, 13);
        int c = 6;
        int f = 20;
        Mountain.Plan p = Mountain.Plan.of(c, c, 11, Mountain.ROUND);
        int band = Mountain.batter(s, p, 0, 1, 3);
        Mountain.walls(s, p, 0, f - 1, 2, WALL);
        Mountain.ring(s, p, band, DARK);
        Mountain.ring(s, p, f - 2, DARK);
        runeRing(s, p, 9);
        Mountain.corbels(s, p, f - 1, 1);
        Mountain.plate(s, p.grow(1), f, FLOOR);
        Mountain.Plan in = p.grow(-2);
        Mountain.plate(s, in, 0, FLOOR);
        for (int fl : new int[]{5, 10, 15}) {
            Mountain.plate(s, in, fl, PLANKS);
        }
        ladder(s, c, 1, c - 3, "south", f);
        crown(s, p.grow(1), f, true);
        focusRoom(s, c, 1, c, 3, 1);
        room(s, BEDROOM, c, 6, c, 3, "west");
        room(s, LIBRARY, c, 11, c, 3);
        room(s, RESEARCH, c, 16, c, 3);
        window(s, c + 5, 7, c, "east", 2, 2, AMBER);
        window(s, c, 7, c + 5, "south", 2, 2, AMBER);
        window(s, c, 12, c + 5, "south", 2, 2, PURPLE);
        window(s, c + 5, 12, c, "east", 2, 2, PURPLE);
        window(s, c - 5, 17, c, "west", 2, 2, PURPLE);
        window(s, c, 17, c + 5, "south", 2, 2, PURPLE);
        Mountain.archDoor(s, c, 1, c + 4, "south", 1, 2);
        Mountain.runeStone(s, c - 5, 0, c + 6, "south", 2);
        Mountain.brazier(s, c + 5, 0, c + 6, false);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, c + 3}, null);
    }

    /**
     * Rank A: a drum thirteen wide on a battered foot up to a girdle: corbels carrying a crenellated walk round the tower,
     * from which a narrower shaft eleven wide rises to the heavy top and its crown of crystal; friezes of runes round both.
     * The focus and a brewing corner on the ground floor; over it the bedroom of two mages; in the shaft the library with its
     * door onto the walk, the brewing room and the research room.
     */
    private static Drawn mageA() {
        Structure s = new Structure(15, 34, 15);
        int c = 7;
        int g = 10;
        int f = 26;
        Mountain.Plan low = Mountain.Plan.of(c, c, 13, Mountain.ROUND);
        Mountain.Plan high = Mountain.Plan.of(c, c, 11, Mountain.ROUND);
        int band = Mountain.batter(s, low, 0, 1, 3);
        Mountain.walls(s, low, 0, g, 2, WALL);
        Mountain.ring(s, low, band, DARK);
        runeRing(s, low, 7);
        girdle(s, low, g);
        walkLights(s, c, g + 2, c, 6);
        Mountain.walls(s, high, g + 1, f - 1, 2, WALL);
        Mountain.ring(s, high, f - 3, DARK);
        runeRing(s, high, 19);
        Mountain.corbels(s, high, f - 1, 2);
        Mountain.plate(s, high.grow(2), f, FLOOR);
        Mountain.plate(s, low.grow(-2), 0, FLOOR);
        Mountain.plate(s, low.grow(-2), 5, PLANKS);
        for (int fl : new int[]{16, 21}) {
            Mountain.plate(s, high.grow(-2), fl, PLANKS);
        }
        // the ladder runs up the north side of the shaft, against a pier of stone in the drum below
        fill(s, c, 1, c - 4, c, g, c - 4, WALL);
        ladder(s, c, 1, c - 3, "south", f);
        crown(s, high.grow(2), f, true);
        focusRoom(s, c, 1, c, 4, 2);
        room(s, BEDROOM, c, 6, c, 4, "west", "east");
        room(s, LIBRARY, c, g + 2, c, 3);
        room(s, BREWERY, c, 17, c, 3);
        room(s, RESEARCH, c, 22, c, 3);
        passage(s, c, g + 2, c + 4, c + 5, "south");
        window(s, c, 7, c + 6, "south", 2, 2, AMBER);
        window(s, c + 6, 2, c + 2, "east", 2, 2, AMBER);
        window(s, c - 5, 13, c, "west", 2, 2, PURPLE);
        window(s, c + 5, 13, c, "east", 2, 2, PURPLE);
        window(s, c + 5, 18, c, "east", 2, 2, PURPLE);
        window(s, c - 5, 18, c, "west", 2, 2, PURPLE);
        window(s, c, 23, c + 5, "south", 2, 2, PURPLE);
        window(s, c - 5, 23, c, "west", 2, 2, PURPLE);
        Mountain.archDoor(s, c, 1, c + 5, "south", 1, 2);
        Mountain.runeStone(s, 1, 0, 13, "south", 2);
        Mountain.runeStone(s, 13, 0, 13, "south", 2);
        Mountain.brazier(s, c - 4, g + 2, c + 4, false);
        Mountain.brazier(s, c + 4, g + 2, c + 4, false);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, c + 4}, null);
    }

    /**
     * Rank S: a wide low drum seventeen wide on a battered foot, the round hall of the focus within it among four pillars
     * with shelves of books round its wall; its roof a terrace behind a crenellated parapet, and before it a portal block
     * with the carved face of a forefather over the deep door. From the terrace the shaft rises thirteen wide to the heavy
     * top on two rings of corbels and its crown of crystal: the bedroom of two mages with a door onto the terrace, the
     * library, the brewing room with the bed of the third, the research room.
     */
    private static Drawn mageS() {
        Structure s = new Structure(19, 37, 21);
        int c = 9;
        int b = 7;
        int f = 29;
        Mountain.Plan base = Mountain.Plan.of(c, c, 17, Mountain.ROUND);
        Mountain.Plan shaft = Mountain.Plan.of(c, c, 13, Mountain.ROUND);
        int band = Mountain.batter(s, base, 0, 1, 2);
        Mountain.walls(s, base, 0, b, 2, WALL);
        Mountain.plate(s, base.grow(-2), 0, FLOOR);
        Mountain.ring(s, base, band, DARK);
        runeRing(s, base, 5);
        Mountain.corbels(s, base, b, 1);
        Mountain.plate(s, base.grow(1), b + 1, FLOOR);
        Mountain.parapet(s, base.grow(1), b + 2);
        Mountain.walls(s, shaft, b + 1, f - 1, 2, WALL);
        runeRing(s, shaft, 17);
        Mountain.ring(s, shaft, f - 3, DARK);
        Mountain.corbels(s, shaft, f - 1, 2);
        Mountain.plate(s, shaft.grow(2), f, FLOOR);
        for (int fl : new int[]{13, 18, 23}) {
            Mountain.plate(s, shaft.grow(-2), fl, PLANKS);
        }
        // the portal block with the face
        fill(s, 6, 0, 16, 12, 13, 18, WALL);
        Mountain.runeBand(s, 6, 18, 12, 18, 14);
        fill(s, 6, 14, 16, 12, 14, 17, WALL);
        Mountain.plate(s, Mountain.Plan.box(6, 16, 12, 18), 15, FLOOR);
        Mountain.parapet(s, Mountain.Plan.box(6, 16, 12, 18), 16);
        Mountain.archDoor(s, c, 1, 16, "south", 1, 2);
        Mountain.face(s, c, 6, 18, "south", 0);
        // pillars under the shaft, a pier behind the ladder
        for (int[] d : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            Mountain.column(s, c + d[0], c + d[1], 1, b);
        }
        fill(s, c, 1, c - 5, c, b, c - 5, WALL);
        ladder(s, c, 1, c - 4, "south", f);
        crown(s, shaft.grow(2), f, true);
        focusHall(s, c, 1, c, 6, b);
        room(s, BEDROOM, c, b + 2, c, 4, "west", "south");
        room(s, LIBRARY, c, 14, c, 4);
        room(s, BREWERY, c, 19, c, 4, "west");
        room(s, RESEARCH, c, 24, c, 4);
        passage(s, c + 5, b + 2, c, c + 6, "east");
        window(s, c - 8, 3, c, "west", 2, 2, AMBER);
        window(s, c + 8, 3, c, "east", 2, 2, AMBER);
        window(s, c + 6, 10, c - 3, "east", 2, 2, AMBER);
        window(s, c - 6, 15, c, "west", 2, 2, PURPLE);
        window(s, c + 6, 15, c, "east", 2, 2, PURPLE);
        window(s, c + 6, 20, c, "east", 2, 2, PURPLE);
        window(s, c, 20, c + 6, "south", 2, 2, PURPLE);
        window(s, c, 25, c + 6, "south", 2, 2, PURPLE);
        window(s, c - 6, 25, c, "west", 2, 2, PURPLE);
        window(s, c + 6, 25, c, "east", 2, 2, PURPLE);
        // the roof terrace
        Mountain.brazier(s, c + 7, b + 2, c - 3, false);
        Mountain.brazier(s, c - 7, b + 2, c - 3, false);
        Mountain.brazier(s, c + 7, b + 2, c + 3, false);
        Mountain.brazier(s, c - 7, b + 2, c + 3, false);
        Mountain.runeStone(s, 2, 0, 18, "south", 2);
        Mountain.runeStone(s, 16, 0, 18, "south", 2);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, 15}, null);
    }

    /**
     * Rank SS: the tower on a terrace three high with a broad flight of stairs and a garden on it: beds of moss with flowers
     * and azaleas, small trees, benches and lanterns. At its front corners two lower round towers under low pyramids, the
     * herb store and the crystal room of the guild. The main tower rises from a drum fifteen wide to a girdle with a
     * crenellated walk and on as a shaft thirteen wide to the heavy top on two rings of corbels, where the crown of crystal
     * rings the observatory: a dome of violet glass on ribs of copper with the telescope and the star table inside. A
     * portal block with the carved face of a forefather stands before its door. Inside: the focus, the bedroom of two mages,
     * the library with its door onto the walk, the bedroom of two more, the brewing room and the research room.
     */
    private static Drawn mageSS() {
        Structure s = new Structure(27, 48, 27);
        int cx = 13;
        int cz = 11;
        int w = Mountain.terrace(s, 1, 1, 25, 22, 0, 3, "south", 5);
        for (int x = 1; x <= 25; x++) {
            put(s, x, w, 1, WALL);
            put(s, x, w + 1, 1, Kit.slab(Mountain.TRIM_SLAB));
        }
        int g = 14;
        int f = 36;
        Mountain.Plan low = Mountain.Plan.of(cx, cz, 15, Mountain.ROUND);
        Mountain.Plan high = Mountain.Plan.of(cx, cz, 13, Mountain.ROUND);
        Mountain.walls(s, low, 0, g, 2, WALL);
        Mountain.Plan lowIn = low.grow(-2);
        for (int yy = 0; yy < w - 1; yy++) {
            Mountain.plate(s, lowIn, yy, ROUGH);
        }
        Mountain.plate(s, lowIn, w - 1, FLOOR);
        for (int yy = w; yy <= g; yy++) {
            Mountain.plate(s, lowIn, yy, AIR);
        }
        int band = Mountain.batter(s, low, w, 1, 2);
        Mountain.ring(s, low, band, DARK);
        runeRing(s, low, 10);
        Mountain.plate(s, lowIn, 8, PLANKS);
        // the portal block before the door, under the girdle
        fill(s, 10, 0, 17, 16, g - 1, 19, WALL);
        girdle(s, low, g);
        Mountain.walls(s, high, g + 1, f - 1, 2, WALL);
        Mountain.ring(s, high, f - 3, DARK);
        runeRing(s, high, 24);
        Mountain.corbels(s, high, f - 1, 2);
        Mountain.plate(s, high.grow(2), f, FLOOR);
        for (int fl : new int[]{20, 25, 30}) {
            Mountain.plate(s, high.grow(-2), fl, PLANKS);
        }
        walkLights(s, cx, g + 2, cz, 7);
        Mountain.archDoor(s, cx, w, 17, "south", 1, 2);
        Mountain.face(s, cx, 8, 19, "south", 0);
        fill(s, cx, w, cz - 5, cx, g, cz - 5, WALL);
        ladder(s, cx, w, cz - 4, "south", f);
        crown(s, high.grow(2), f, false);
        observatory(s, cx, f + 1, cz, 5);

        focusRoom(s, cx, w, cz, 5, 2);
        room(s, BEDROOM, cx, 9, cz, 5, "west", "east");
        room(s, LIBRARY, cx, g + 2, cz, 4);
        room(s, BEDROOM, cx, 21, cz, 4, "west", "east");
        room(s, BREWERY, cx, 26, cz, 4);
        room(s, RESEARCH, cx, 31, cz, 4);
        passage(s, cx, g + 2, cz + 5, cz + 6, "south");
        window(s, cx - 7, 5, cz, "west", 2, 2, AMBER);
        window(s, cx + 7, 5, cz, "east", 2, 2, AMBER);
        window(s, cx + 6, 10, cz + 3, "east", 2, 2, AMBER);
        window(s, cx - 6, 10, cz + 3, "west", 2, 2, AMBER);
        window(s, cx - 6, 17, cz, "west", 2, 2, PURPLE);
        window(s, cx + 6, 17, cz, "east", 2, 2, PURPLE);
        window(s, cx + 6, 22, cz + 2, "east", 2, 2, PURPLE);
        window(s, cx - 6, 22, cz + 2, "west", 2, 2, PURPLE);
        window(s, cx, 27, cz + 6, "south", 2, 2, PURPLE);
        window(s, cx + 6, 27, cz, "east", 2, 2, PURPLE);
        window(s, cx, 32, cz + 6, "south", 2, 2, PURPLE);
        window(s, cx - 6, 32, cz, "west", 2, 2, PURPLE);
        window(s, cx + 6, 32, cz, "east", 2, 2, PURPLE);

        // the side towers and the garden
        sideTower(s, 5, 18, w, "east");
        sideTower(s, 21, 18, w, "west");
        garden(s, w);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{cx, w, 16}, null);
    }

    // ---------------------------------------------------------------- parts of the towers

    /**
     * A girdle round a drum whose walls end at layer {@code g}: corbels jutting out one block at that layer, over them at
     * {@code g + 1} the floor of a walk round the shaft that rises on, and a crenellated parapet on its edge.
     */
    private static void girdle(Structure s, Mountain.Plan drum, int g) {
        Mountain.corbels(s, drum, g, 1);
        Mountain.plate(s, drum.grow(1), g + 1, FLOOR);
        Mountain.parapet(s, drum.grow(1), g + 2);
    }

    /**
     * Lanterns standing on the walk round a tower at layer {@code y}, eight of them at the radius {@code r} from (cx, cz),
     * where the cell is free.
     */
    private static void walkLights(Structure s, int cx, int y, int cz, int r) {
        int d = (int) Math.round(r / Math.sqrt(2));
        for (int[] o : new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}, {d, d}, {-d, d}, {d, -d}, {-d, -d}}) {
            int x = cx + o[0];
            int z = cz + o[1];
            if (s.inside(x, y, z) && !s.solid(x, y, z) && s.solid(x, y - 1, z)) {
                s.set(x, y, z, Kit.lantern());
            }
        }
    }

    /**
     * A frieze of runes round a round plan at layer {@code y}: carved runes, dark stone and gilt stone in turn on its rim.
     */
    private static void runeRing(Structure s, Mountain.Plan p, int y) {
        for (int z = p.z1(); z <= p.z2(); z++) {
            for (int x = p.x1(); x <= p.x2(); x++) {
                if (p.rim(x, z)) {
                    int k = Math.floorMod(x * 3 + z * 5, 4);
                    put(s, x, y, z, k == 0 ? Mountain.GILDED : k == 2 ? DARK : Mountain.RUNE);
                }
            }
        }
    }

    /**
     * The crown of crystal on the top of a tower whose floor lies at layer {@code f} on the plan {@code q}: on its rim a
     * low wall of stone, and on it, where a parapet would have its merlons, points of amethyst, every fourth one higher with
     * a gilt foot and a cluster on its tip, those between them tipped with rods of light, dark slabs in the gaps; with
     * {@code spike} a spike of crystal round a glowing heart in the middle, four lower crystals round it and clusters at its
     * foot.
     */
    private static void crown(Structure s, Mountain.Plan q, int f, boolean spike) {
        int cx = q.cx();
        int cz = q.cz();
        for (int z = q.z1(); z <= q.z2(); z++) {
            for (int x = q.x1(); x <= q.x2(); x++) {
                if (!q.rim(x, z)) {
                    continue;
                }
                put(s, x, f + 1, z, WALL);
                double a = Math.atan2(z - cz, x - cx) + Math.PI;
                int i = (int) Math.round(a / (2 * Math.PI) * q.rimCount());
                int k = Math.floorMod(i, 4);
                if (k == 0) {
                    put(s, x, f + 1, z, Mountain.GILDED);
                    put(s, x, f + 2, z, AMETHYST);
                    put(s, x, f + 3, z, AMETHYST);
                    put(s, x, f + 4, z, CLUSTER);
                } else if (k == 2) {
                    put(s, x, f + 2, z, AMETHYST);
                    put(s, x, f + 3, z, ROD);
                } else {
                    put(s, x, f + 2, z, Kit.slab(Mountain.DARK_SLAB));
                }
            }
        }
        if (!spike) {
            return;
        }
        put(s, cx, f + 1, cz, AMETHYST);
        put(s, cx, f + 2, cz, HEART);
        put(s, cx, f + 3, cz, AMETHYST);
        put(s, cx, f + 4, cz, CRYSTAL);
        put(s, cx, f + 5, cz, AMETHYST);
        put(s, cx, f + 6, cz, ROD);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            put(s, cx + d[0], f + 1, cz + d[1], AMETHYST);
            put(s, cx + d[0], f + 2, cz + d[1], AMETHYST);
            put(s, cx + d[0], f + 3, cz + d[1], CLUSTER);
        }
        for (int[] d : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            put(s, cx + d[0], f + 1, cz + d[1], CLUSTER);
        }
    }

    /**
     * The crystal observatory: a dome of glass of radius {@code r} round (cx, cz) standing on layer {@code y}, its shell of
     * violet and clear glass held by eight ribs of copper, a glowing heart and a spike of amethyst on its top; inside, a
     * telescope on a dark post, a star table and rods of light.
     */
    private static void observatory(Structure s, int cx, int y, int cz, int r) {
        int[] radii = {r, r, r, r - 1, r - 1, r - 2, r - 3, r - 4};
        for (int k = 0; k < radii.length; k++) {
            int rk = radii[k];
            int rn = k + 1 < radii.length ? radii[k + 1] : -1;
            if (rk <= 0) {
                put(s, cx, y + k, cz, HEART);
                continue;
            }
            for (int dz = -rk; dz <= rk; dz++) {
                for (int dx = -rk; dx <= rk; dx++) {
                    if (!Kit.inDisc(dx, dz, 2 * rk + 1)) {
                        continue;
                    }
                    boolean rim = Kit.onRim(dx, dz, 2 * rk + 1);
                    boolean lid = rn < 0 || !Kit.inDisc(dx, dz, 2 * rn + 1);
                    if (!rim && !lid || k == 0 && !rim) {
                        continue;
                    }
                    boolean rib = dx == 0 || dz == 0 || k == 0 && Math.abs(dx) == Math.abs(dz);
                    put(s, cx + dx, y + k, cz + dz, rib ? Mountain.COPPER : CRYSTAL);
                }
            }
        }
        int topLayer = y + radii.length;
        put(s, cx, topLayer, cz, AMETHYST);
        put(s, cx, topLayer + 1, cz, ROD);
        // the door of the dome toward the south, onto the walk inside the crown
        put(s, cx, y, cz + r, AIR);
        put(s, cx, y + 1, cz + r, AIR);
        // the telescope and the star table
        put(s, cx + 1, y, cz + 1, Mountain.DARK_WALL);
        put(s, cx + 1, y + 1, cz + 1, Mountain.COPPER);
        put(s, cx + 1, y + 2, cz + 1, "minecraft:end_rod[facing=north]");
        put(s, cx + 1, y + 2, cz, "minecraft:end_rod[facing=north]");
        put(s, cx - 2, y, cz + 1, CARTOGRAPHY);
        put(s, cx - 2, y, cz + 2, Kit.stairs(Mountain.STAIRS, "south"));
        put(s, cx + 3, y, cz - 1, Kit.topSlab(Mountain.TRIM_SLAB));
        put(s, cx + 3, y + 1, cz - 1, Furnish.candles(3));
        for (int[] d : new int[][]{{-3, -2}, {3, 2}, {-3, 2}}) {
            put(s, cx + d[0], y, cz + d[1], Mountain.DARK_WALL);
            put(s, cx + d[0], y + 1, cz + d[1], CLUSTER);
        }
        put(s, cx, y + radii.length - 2, cz, Kit.hangingLantern());
    }

    /**
     * A lower round tower of the mages nine wide at (cx, cz), standing on the ground at a corner of a terrace whose walk is
     * at layer {@code w}: walls two thick, a band of dark stone, a room on the level of the walk with its door on the side
     * {@code door} and one above it, a ladder; corbels, a crenellated top and a low pyramid with a gilt point.
     */
    private static void sideTower(Structure s, int cx, int cz, int w, String door) {
        Mountain.Plan p = Mountain.Plan.of(cx, cz, 9, Mountain.ROUND);
        int f = 16;
        Mountain.Plan in = p.grow(-2);
        for (int z = p.z1(); z <= p.z2(); z++) {
            for (int x = p.x1(); x <= p.x2(); x++) {
                if (p.has(x, z)) {
                    for (int yy = w; yy < s.sizeY; yy++) {
                        put(s, x, yy, z, AIR);
                    }
                }
            }
        }
        Mountain.walls(s, p, 0, f - 1, 2, WALL);
        for (int yy = 0; yy < w - 1; yy++) {
            Mountain.plate(s, in, yy, ROUGH);
        }
        Mountain.plate(s, in, w - 1, FLOOR);
        Mountain.plate(s, in, 9, PLANKS);
        Mountain.ring(s, p, w, DARK);
        Mountain.ring(s, p, f - 2, DARK);
        runeRing(s, p, 8);
        Mountain.corbels(s, p, f - 1, 1);
        Mountain.Plan q = p.grow(1);
        Mountain.plate(s, q, f, FLOOR);
        Mountain.parapet(s, q, f + 1);
        Mountain.helm(s, q.grow(-1), f + 1, Mountain.HELM);
        int[] d = Furnish.step(door);
        passage(s, cx + d[0] * 3, w, cz + d[1] * 3, d[0] != 0 ? cx + d[0] * 4 : cz + d[1] * 4, door);
        String back = Furnish.opposite(door);
        int[] b = Furnish.step(back);
        ladder(s, cx + b[0] * 2, w, cz + b[1] * 2, door, 9);
        // a store of herbs below, a room of crystals above
        Furnish.sack(s, cx, w, cz - 2, false);
        Furnish.sack(s, cx, w, cz + 2, true);
        Furnish.crate(s, cx - 1, w, cz + 1, WOOD);
        put(s, cx + 1, w, cz + 1, CAULDRON);
        put(s, cx, w + 3, cz, Kit.hangingLantern());
        put(s, cx, 10, cz - 2, AMETHYST);
        put(s, cx, 11, cz - 2, CLUSTER);
        put(s, cx, 10, cz + 2, Mountain.DARK_WALL);
        put(s, cx, 11, cz + 2, CLUSTER);
        put(s, cx - b[0], 10, cz, Kit.topSlab(Mountain.TRIM_SLAB));
        put(s, cx - b[0], 11, cz, Furnish.candles(3));
        put(s, cx, f - 2, cz, Kit.hangingLantern());
        int[] e = Furnish.step(door);
        window(s, cx + e[0] * 4, 11, cz + e[1] * 4, door, 2, 2, PURPLE);
        window(s, cx, 11, cz + 4, "south", 2, 2, PURPLE);
    }

    /**
     * The garden on the terrace of the mage tower, the walk at layer {@code w}: beds of moss along the parapets with
     * flowers and azaleas, small trees at the back corners, benches and lanterns on dark posts.
     */
    private static void garden(Structure s, int w) {
        String[] flowers = {"minecraft:allium", "minecraft:azure_bluet", "minecraft:cornflower", "minecraft:lily_of_the_valley",
                "minecraft:flowering_azalea", "minecraft:azalea"};
        int i = 0;
        // hedges along the west and east parapets between the side towers and the back, flowers before them
        for (int z = 4; z <= 12; z++) {
            for (int x : new int[]{2, 24}) {
                put(s, x, w - 1, z, MOSS);
                put(s, x, w, z, Kit.leaves(z % 3 == 0 ? "flowering_azalea" : "azalea"));
                if (z % 4 == 1) {
                    put(s, x, w + 1, z, Kit.leaves("flowering_azalea"));
                }
            }
        }
        // beds along the back parapet
        for (int x = 4; x <= 22; x++) {
            if (x >= 11 && x <= 15) {
                continue;
            }
            put(s, x, w - 1, 2, MOSS);
            put(s, x, w, 2, flowers[i++ % flowers.length]);
        }
        // small trees at the back corners and before the side towers
        for (int[] t : new int[][]{{3, 3}, {23, 3}}) {
            put(s, t[0], w - 1, t[1], MOSS);
            put(s, t[0], w, t[1], Kit.log(Mountain.LOG, "y"));
            put(s, t[0], w + 1, t[1], Kit.log(Mountain.LOG, "y"));
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    put(s, t[0] + dx, w + 2, t[1] + dz, Kit.leaves(dx == 0 && dz == 0 ? "azalea" : "flowering_azalea"));
                }
            }
            put(s, t[0], w + 3, t[1], Kit.leaves("azalea"));
        }
        // benches facing the tower, lanterns on dark posts
        Furnish.bench(s, 3, 8, 3, 10, w, "east", WOOD);
        Furnish.bench(s, 23, 8, 23, 10, w, "west", WOOD);
        for (int[] p : new int[][]{{3, 6}, {23, 6}, {3, 13}, {23, 13}, {8, 3}, {18, 3}}) {
            put(s, p[0], w, p[1], Mountain.DARK_WALL);
            put(s, p[0], w + 1, p[1], Kit.lantern());
        }
        // beds of flowers before the tower, either side of the walk to the door
        for (int x : new int[]{10, 16}) {
            for (int z = 20; z <= 21; z++) {
                put(s, x, w - 1, z, MOSS);
                put(s, x, w, z, flowers[i++ % flowers.length]);
            }
        }
    }

    // ---------------------------------------------------------------- rooms of the mages

    /**
     * The ground floor of a mage tower round (cx, cz), one stands at layer {@code y}, its inner radius {@code r}: the magic
     * focus in the middle on a violet rug, the post of the mage two blocks east and one north of it with the one chest
     * beside it, brewing stands and a cauldron to the west, shelves of books by the ladder, lanterns.
     */
    private static void focusRoom(Structure s, int cx, int y, int cz, int r, int brewing) {
        Furnish.rug(s, cx - 1, cz - 1, cx + 1, cz + 1, y, "minecraft:purple_carpet");
        s.set(cx, y, cz, FOCUS);
        s.set(cx + 2, y, cz - 1, WORK_POST).set(cx + 3, y, cz - 1, Kit.chest("west"));
        s.set(cx - 2, y, cz - 2, BREWING).set(cx - 3, y, cz - 1, CAULDRON);
        s.set(cx - 3, y, cz, Kit.topSlab(Mountain.TRIM_SLAB)).set(cx - 3, y + 1, cz, Furnish.candles(3));
        if (brewing > 1) {
            s.set(cx - 3, y, cz + 1, BREWING);
        }
        Furnish.books(s, cx - 1, cz - r, cx - 1, cz - r, y, y + 2);
        Furnish.books(s, cx + 1, cz - r, cx + 1, cz - r, y, y + 2);
        if (r >= 5) {
            for (int[] c : rimCells(cx, cz, r)) {
                int dx = c[0] - cx;
                int dz = c[1] - cz;
                if (dz > 1 && Math.abs(dx) >= 2 || dz < -2 && Math.abs(dx) >= 2) {
                    Furnish.books(s, c[0], c[1], c[0], c[1], y, y + 1);
                }
            }
        }
        s.set(cx + 2, y, cz - 2, "minecraft:flower_pot");
        lamps(s, cx, y + 3, cz, new int[]{2, 2}, new int[]{-2, 2}, new int[]{0, -1}, new int[]{-2, -2});
    }

    /**
     * The round hall of the focus in a wide drum: the focus in the middle of a circle of runes inlaid in the floor, the
     * post of the mage and its chest, a brewing corner, shelves of books round the wall between the windows, the pillars and
     * the door, two reading tables, crystals on dark posts and lanterns.
     */
    private static void focusHall(Structure s, int cx, int y, int cz, int r, int ceiling) {
        for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 2}, {-1, 2}, {1, -2}, {-1, -2}, {2, 1}, {2, -1}, {-2, 1},
                {-2, -1}}) {
            put(s, cx + d[0], y - 1, cz + d[1], (Math.abs(d[0]) + Math.abs(d[1])) == 2 ? Mountain.RUNE : Mountain.GILDED);
        }
        focusRoom(s, cx, y, cz, 4, 2);
        for (int[] c : rimCells(cx, cz, r)) {
            int dx = c[0] - cx;
            int dz = c[1] - cz;
            boolean free = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
            if (!free) {
                Furnish.books(s, c[0], c[1], c[0], c[1], y, y + 2);
            }
        }
        Mountain.stoneTable(s, cx - 3, cz + 4, cx - 2, cz + 4, y, false);
        Mountain.stoneTable(s, cx + 2, cz + 4, cx + 3, cz + 4, y, false);
        for (int[] d : new int[][]{{-5, -1}, {5, 1}}) {
            put(s, cx + d[0], y, cz + d[1], Mountain.DARK_WALL);
            put(s, cx + d[0], y + 1, cz + d[1], CLUSTER);
        }
        lamps(s, cx, ceiling - 1, cz, new int[]{0, 4}, new int[]{4, 0}, new int[]{-4, 0}, new int[]{0, 2}, new int[]{3, 3},
                new int[]{-3, 3}, new int[]{3, -3}, new int[]{-3, -3});
    }

    /**
     * A room of a storey of a mage tower round (cx, cz), one stands at layer {@code y}, its inner radius {@code r}: a
     * bedroom with a bed in an alcove of stone on each of the sides named, a library, a brewing room (with a bed on each side
     * named) or the research room.
     */
    private static void room(Structure s, int kind, int cx, int y, int cz, int r, String... beds) {
        for (String side : beds) {
            int[] d = Furnish.step(side);
            Mountain.bedAlcove(s, cx + d[0] * r, y, cz + d[1] * r, Furnish.opposite(side), "red");
        }
        switch (kind) {
            case BEDROOM -> {
                Furnish.rug(s, cx - 1, cz - 1, cx + 1, cz + 1, y, Mountain.CARPET);
                Furnish.crate(s, cx + 2, y, cz - 2, WOOD);
                put(s, cx + 2, y + 1, cz - 2, Furnish.candles(2));
                Furnish.desk(s, cx + 2, y, cz + 2, "north", WOOD);
                if (r >= 4) {
                    Furnish.books(s, cx - 2, cz - 2, cx - 2, cz - 2, y, y + 1);
                    Furnish.crate(s, cx - 2, y, cz + 2, WOOD);
                }
            }
            case LIBRARY -> {
                for (int[] c : rimCells(cx, cz, r)) {
                    boolean keep = c[0] == cx || c[1] == cz;
                    if (!keep) {
                        Furnish.books(s, c[0], c[1], c[0], c[1], y, y + 1);
                    }
                }
                put(s, cx - 1, y, cz - 1, LECTERN);
                Mountain.stoneTable(s, cx, cz + 1, cx + 1, cz + 1, y, false);
                if (r >= 4) {
                    put(s, cx, y, cz + 2, Kit.stairs(Mountain.STAIRS, "north"));
                }
            }
            case BREWERY -> {
                put(s, cx - 2, y, cz - 2, BREWING);
                put(s, cx + 2, y, cz - 2, BREWING);
                if (beds.length == 0) {
                    put(s, cx - r, y, cz, CAULDRON);
                }
                put(s, cx + r, y, cz, CAULDRON);
                Furnish.sack(s, cx - 2, y, cz + 2, false);
                Furnish.crate(s, cx + 2, y, cz + 2, WOOD);
                put(s, cx, y, cz, Kit.topSlab(Mountain.TRIM_SLAB));
                put(s, cx, y + 1, cz, Furnish.candles(3));
                put(s, cx + 1, y, cz - 2, Kit.topSlab(Mountain.TRIM_SLAB));
                put(s, cx + 1, y + 1, cz - 2, "minecraft:flower_pot");
            }
            default -> {
                put(s, cx, y, cz, ENCHANTING);
                for (int[] d : new int[][]{{-2, -1}, {-2, 0}, {-2, 1}, {2, -1}, {2, 0}, {2, 1}, {-1, -2}, {1, -2}}) {
                    Furnish.books(s, cx + d[0], cz + d[1], cx + d[0], cz + d[1], y, y);
                }
                put(s, cx - 1, y, cz + 2, Kit.topSlab(Mountain.TRIM_SLAB));
                put(s, cx - 1, y + 1, cz + 2, Furnish.candles(3));
                put(s, cx + 1, y, cz + 2, Mountain.DARK_WALL);
                put(s, cx + 1, y + 1, cz + 2, AMETHYST);
                if (r >= 4) {
                    put(s, cx - 3, y, cz + 2, CARTOGRAPHY);
                    put(s, cx + 3, y, cz + 2, Mountain.DARK_WALL);
                    put(s, cx + 3, y + 1, cz + 2, CLUSTER);
                }
            }
        }
        lamps(s, cx, y + 3, cz, new int[]{1, 1}, new int[]{-1, 1}, new int[]{1, -1}, new int[]{-1, -1});
    }

    /** The cells on the rim of the inside of a round storey of inner radius {@code r}, the ladder's cell left out. */
    private static List<int[]> rimCells(int cx, int cz, int r) {
        List<int[]> list = new ArrayList<>();
        Mountain.Plan in = Mountain.Plan.of(cx, cz, 2 * r + 1, Mountain.ROUND);
        for (int z = in.z1(); z <= in.z2(); z++) {
            for (int x = in.x1(); x <= in.x2(); x++) {
                if (in.rim(x, z) && !(x == cx && z == in.z1())) {
                    list.add(new int[]{x, z});
                }
            }
        }
        return list;
    }

    // ================================================================ small shapes

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
     * A small deep window like {@link Mountain#window} with a pane of the given glass: in a wall two thick the outer cell
     * stays open as a reveal and the pane sits in the inner one; a lintel of three stones with a carved middle over it and a
     * sill under it.
     */
    private static void window(Structure s, int x, int y, int z, String facing, int height, int thick, String glass) {
        Mountain.window(s, x, y, z, facing, height, thick);
        int[] f = Furnish.step(facing);
        for (int v = 0; v < height; v++) {
            put(s, thick >= 2 ? x - f[0] : x, y + v, thick >= 2 ? z - f[1] : z, glass);
        }
    }

    /**
     * A ladder against the wall behind it (the wall lies opposite to {@code facing}), from layer {@code y} up through the
     * floor at {@code top}.
     */
    private static void ladder(Structure s, int x, int y, int z, String facing, int top) {
        for (int yy = y; yy <= top; yy++) {
            put(s, x, yy, z, Kit.ladder(facing));
        }
    }

    /**
     * A lantern hanging at (x, y, z) on a chain up to the first solid block over it.
     */
    private static void lamp(Structure s, int x, int y, int z) {
        if (!s.inside(x, y, z) || s.solid(x, y, z)) {
            return;
        }
        s.set(x, y, z, Kit.hangingLantern());
        for (int yy = y + 1; yy < s.sizeY && !s.solid(x, yy, z); yy++) {
            s.set(x, yy, z, Kit.chain());
        }
    }

    /**
     * Lanterns at the given offsets from (cx, cz) at layer {@code y}, each hanging on a chain from the first solid block at
     * most eight layers over it; a taken cell or one with nothing over it is left as it is.
     */
    private static void lamps(Structure s, int cx, int y, int cz, int[]... offsets) {
        for (int[] d : offsets) {
            int x = cx + d[0];
            int z = cz + d[1];
            if (!s.inside(x, y, z) || s.solid(x, y, z)) {
                continue;
            }
            for (int yy = y + 1; yy <= y + 8 && yy < s.sizeY; yy++) {
                if (s.solid(x, yy, z)) {
                    lamp(s, x, y, z);
                    break;
                }
            }
        }
    }
}
