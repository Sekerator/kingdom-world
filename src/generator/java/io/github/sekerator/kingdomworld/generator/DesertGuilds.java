package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The two guilds of a capital in the style "desert", each a program drawn for the rank of its kingdom, from B for a weak
 * one to SS for a strong one.
 *
 * <p>The adventurers' guild is a caravanserai of the bold: a block of thick walls under flat roofs crowned with merlons,
 * entered through a high portal (pishtaq) whose arched niche (iwan) holds the gate; inside a great hall under a dome on a
 * drum, with long tables and benches, a brazier, the contract board, the counter of the registrar with the guild desk, his
 * post and his chest, trophies of the hunt, and the rooms of the guild upstairs with the map of the lands and its book.
 * The rank grows it: B a hall with its loft behind a portal; A adds a court with arcaded galleries and two wind towers; S a
 * court with two storeys of arcades on one side and a watch tower under a little dome on the other; SS a great
 * caravanserai, a court with a star fountain and palms, a great hall under a high ribbed dome with an iwan to the court,
 * side halls with wind towers, galleries on two storeys, round towers with merlons on its front corners and a portal
 * flanked by slender turrets under little domes, one of the main buildings of a capital after the castle.
 *
 * <p>The mages' guild lives in a tower of astronomers: a slim eight-sided shaft on a square plinth with bands of glazed
 * tiles and keyhole windows, balconies on corbels and, on its top, an open pavilion of arches under a pointed dome in which
 * stands the armillary sphere (rings of chains, an axis of end rods, a gilt sun). On its ground floor the magic focus with
 * the post of the mage and its chest; a library, a brewing room, a research room, and real beds for the mages: one in B,
 * two in A, three in S and four in SS. A grows the tower and gives it a balcony half way up; S adds a wing with two small
 * domes for the library and the brewing room; SS is a madrasa: a court with a fountain, the domed hall of the focus, the
 * rooms of the mages round the court, a portal flanked by two minarets, and the observatory in a pavilion on its roof.
 *
 * <p>Drawn with the shapes of {@link Desert}; coordinates as everywhere: x from west to east, z from the back to the front
 * (the entrance looks south), y up; layer 0 stands on the ground and holds the floors.
 */
final class DesertGuilds {
    /** The rank of a guild: B for a weak kingdom, A, S, SS for the strongest. */
    enum Rank {
        B, A, S, SS
    }

    private static final String AIR = Structure.AIR;
    private static final String WALL = Desert.WALL;
    private static final String BASE = Desert.BASE;
    private static final String TRIM = Desert.TRIM;
    private static final String CARVED = Desert.CARVED;
    private static final String PAVING = Desert.PAVING;
    private static final String FLOOR = Desert.FLOOR;
    private static final String FLOOR_SLAB = Desert.FLOOR_SLAB;
    private static final String FLOOR_STAIRS = Desert.FLOOR_STAIRS;
    private static final String FENCE = Desert.FENCE;
    private static final String GLASS = Desert.GLASS;
    private static final String DECK = Desert.ROOF.block();
    private static final String GRASS = "minecraft:rooted_dirt";
    private static final String DESK = "kingdomworld:guild_desk[facing=south]";
    private static final String WORK_POST = "kingdomworld:work_post";
    private static final String FOCUS = "kingdomworld:magic_focus";
    private static final String CARTOGRAPHY = "minecraft:cartography_table";
    private static final String ENCHANTING = "minecraft:enchanting_table";
    private static final String BREWING = "minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]";
    private static final String CAULDRON = "minecraft:water_cauldron[level=3]";
    private static final String AMETHYST = "minecraft:amethyst_block";
    private static final String CLUSTER = "minecraft:amethyst_cluster[facing=up,waterlogged=false]";
    private static final String ROD_UP = "minecraft:end_rod[facing=up]";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String TARGET = "minecraft:target[power=0]";
    /** The guild's own colour on its banners, the same in every kingdom. */
    private static final String GUILD = "black";

    /**
     * A drawn building with its named places: the door, and the guards by the desk.
     */
    private record Drawn(Structure structure, int[] door, List<int[]> guards) {
    }

    private DesertGuilds() {
    }

    /**
     * The adventurers' guilds desert_guild_b, _a, _s and _ss (trade, type guild, with their door and the guard by the desk)
     * and the mage towers desert_mage_tower_b, _a, _s and _ss (production, role mage_tower, with their door), all standing
     * on the ground with their anchor in the middle of their front edge.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Rank rank : Rank.values()) {
            String key = rank.name().toLowerCase(Locale.ROOT);
            Drawn guild = guild(rank);
            Structure g = guild.structure();
            BuildingGenerator.Building b = new BuildingGenerator.Building("desert_guild_" + key, "trade", "guild", "kingdomworld:guild_desk",
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
            list.add(new BuildingGenerator.Building("desert_mage_tower_" + key, "production", "mage_tower", "kingdomworld:magic_focus",
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
     * Rank B: a khan of the guild. A block of thick walls under a terrace roof with pointed merlons and a pointed dome on a
     * glazed drum over the great hall; before it a portal as wide as the front rising over the roofs, a pointed iwan in its
     * frame, a band of tiles over it, braziers and the black banners of the guild in the iwan, the gate at its back under a
     * panel of tiles. Inside the hall: the contract board, a long table with benches, trophies by the door, a brazier, a
     * great lamp hanging from the dome; under the loft at the back the counter of the registrar with the desk, his post, his
     * chest and the shelves; a stair along the east wall to the loft, which holds the map of the lands, the map table and
     * the lectern of the guild.
     */
    private static Drawn guildB() {
        Structure s = new Structure(13, 23, 15);
        s.fill(1, 0, 1, 11, 0, 11, BASE);
        s.fill(2, 0, 2, 10, 0, 10, PAVING);
        s.fill(0, 0, 12, 12, 0, 13, PAVING);
        Desert.zellige(s, 4, 6, 8, 10, 0);
        block(s, 1, 1, 11, 11, 8);
        Desert.flatRoof(s, 1, 1, 11, 11, 9, Desert.Merlons.POINTED, true);
        domeOn(s, 6, 9, 6, 7, 4, Desert.DomeKind.POINTED);
        portal(s, 0, 12, 12, 13, 14, 5, 5, Desert.Arch.POINTED, 2);
        Desert.doorway(s, 6, 1, 11, "south", 3, 3, 1, Desert.Arch.POINTED, false);
        s.fill(4, 7, 11, 8, 8, 11, Desert.glazed(Desert.TILE, "south"));
        Desert.lamp(s, 6, 7, 12);
        Desert.brazier(s, 4, 1, 13);
        Desert.brazier(s, 8, 1, 13);
        s.set(4, 5, 12, Kit.wallBanner(GUILD, "east")).set(8, 5, 12, Kit.wallBanner(GUILD, "west"));
        // windows: lattices high in the side walls, a lattice oriel by the loft, keyholes low in the hall; tiles on the back
        Desert.lattice(s, 1, 6, 8, "west", 1, 2, false);
        Desert.lattice(s, 11, 6, 8, "east", 1, 2, false);
        Desert.lattice(s, 11, 6, 3, "east", 1, 2, true);
        Desert.lattice(s, 1, 6, 3, "west", 1, 2, false);
        Desert.arch(s, 1, 2, 6, "west", 1, 2, 1, Desert.Arch.HORSESHOE, GLASS, false);
        Desert.arch(s, 11, 2, 5, "east", 1, 2, 1, Desert.Arch.HORSESHOE, GLASS, false);
        panel(s, 4, 5, 1, 8, 7, "north");
        Desert.brazier(s, 2, 10, 10);
        Desert.brazier(s, 10, 10, 2);

        // the loft over the back of the hall and the stair to it
        s.fill(2, 5, 2, 10, 5, 5, FLOOR);
        for (int x = 2; x <= 9; x++) {
            s.set(x, 6, 5, FENCE);
        }
        flight(s, 10, 1, 10, "north", 5);
        // under the loft: the counter of the registrar
        Furnish.books(s, 2, 2, 10, 2, 1, 3);
        registrar(s, 6, 1, 5, 1);
        Furnish.crate(s, 9, 1, 3, "oak");
        Furnish.crate(s, 9, 2, 3, "oak");
        Furnish.sack(s, 9, 1, 4, true);
        Desert.jar(s, 2, 1, 3);
        Desert.jar(s, 3, 1, 3);
        Desert.lamp(s, 4, 4, 4);
        Desert.lamp(s, 8, 4, 4);
        // the hall
        contractBoard(s, 2, 1, 8, "east", 3);
        Furnish.longTable(s, 5, 8, 8, 8, 1, "oak");
        Furnish.bench(s, 5, 7, 8, 7, 1, "south", "oak");
        Furnish.bench(s, 5, 9, 8, 9, 1, "north", "oak");
        s.set(6, 2, 8, Furnish.candles(3));
        trophies(s, 4, 1, 10, "north", 3);
        s.set(8, 1, 10, "minecraft:bone_block[axis=y]").set(8, 2, 10, "minecraft:skeleton_skull[rotation=8]");
        Desert.brazier(s, 9, 1, 7);
        s.set(10, 7, 9, Kit.wallBanner(GUILD, "west"));
        hangChandelier(s, 6, 6, 8);
        Desert.lamp(s, 3, 8, 6);
        Desert.lamp(s, 9, 8, 9);
        // the loft: the map, the map table, the lectern
        int loft = 6;
        Furnish.books(s, 2, 2, 3, 2, loft, loft + 2);
        Furnish.books(s, 9, 2, 10, 2, loft, loft + 2);
        guildMap(s, 4, loft + 1, 2, 5);
        s.fill(4, loft, 2, 8, loft, 2, Kit.topSlab(FLOOR_SLAB));
        mapTable(s, 6, loft, 3);
        s.set(3, loft, 3, lectern("south"));
        Desert.lamp(s, 3, 8, 4);
        Desert.lamp(s, 9, 8, 3);
        s.connect();
        return new Drawn(s, new int[]{6, 1, 12}, List.of(new int[]{5, 1, 6}));
    }

    /**
     * Rank A: a small caravanserai. The domed hall of B grown wide, two wind towers on its back corners and a ribbed dome
     * over its middle; before it a court with a basin and two palms between galleries under terraces, each opening to the
     * court through two pointed arches on a slender column; the court closed by a raised portal with a horseshoe gate in its
     * frame. In the galleries a divan for the guests on the west and a training ground with racks, targets and a dummy on the
     * east. In the hall two long tables, a brazier between them, the contract board, trophies by the door; under the loft
     * the counter of the registrar; on the loft the map room and the chamber of the master of the guild with his bed of
     * state, behind a screen of shelves.
     */
    private static Drawn guildA() {
        Structure s = new Structure(19, 25, 21);
        s.fill(1, 0, 1, 17, 0, 19, BASE);
        s.fill(2, 0, 2, 16, 0, 18, PAVING);
        s.fill(4, 0, 20, 14, 0, 20, BASE);
        Desert.zellige(s, 6, 6, 12, 10, 0);
        // the galleries on both sides of the court, under terraces, and the front wall between them
        for (int side = -1; side <= 1; side += 2) {
            int xo = side < 0 ? 1 : 17;
            int xa = side < 0 ? 5 : 13;
            s.fill(xo, 1, 11, xo, 5, 19, WALL);
            s.fill(xo, 1, 11, xo, 1, 19, BASE);
            Desert.flatRoof(s, Math.min(xo, xa), 11, Math.max(xo, xa), 19, 6, Desert.Merlons.STEPPED, false);
            Desert.arcade(s, xa, 11, xa, 19, 1, 3, 5, side < 0 ? "east" : "west", 3, Desert.Arch.POINTED);
        }
        s.fill(5, 1, 19, 13, 5, 19, WALL);
        basin(s, 9, 0, 15, 2);
        // the hall
        block(s, 1, 1, 17, 11, 9);
        Desert.flatRoof(s, 1, 1, 17, 11, 10, Desert.Merlons.POINTED, true);
        domeOn(s, 9, 10, 6, 7, 4, Desert.DomeKind.RIBBED);
        windTower(s, 3, 10, 3, 3, 6);
        windTower(s, 15, 10, 3, 3, 6);
        Desert.doorway(s, 9, 1, 11, "south", 3, 3, 1, Desert.Arch.POINTED, true);
        // the portal: a horseshoe arch in its frame, the gate one step behind it
        portal(s, 4, 14, 19, 20, 13, 3, 2, Desert.Arch.HORSESHOE, 1);
        Desert.doorway(s, 9, 1, 19, "south", 3, 2, 1, Desert.Arch.HORSESHOE, false);
        s.set(9, 0, 20, Kit.slab(Desert.PAVING_SLAB));
        Desert.lamp(s, 9, 5, 20);
        s.set(3, 0, 20, BASE).set(15, 0, 20, BASE);
        Desert.brazier(s, 3, 1, 20);
        Desert.brazier(s, 15, 1, 20);
        // windows and tiles
        Desert.lattice(s, 1, 6, 3, "west", 1, 2, false);
        Desert.lattice(s, 1, 7, 8, "west", 1, 2, false);
        Desert.lattice(s, 17, 6, 3, "east", 1, 2, true);
        Desert.lattice(s, 17, 7, 8, "east", 1, 2, false);
        Desert.arch(s, 1, 2, 4, "west", 1, 2, 1, Desert.Arch.HORSESHOE, GLASS, false);
        Desert.arch(s, 17, 2, 4, "east", 1, 2, 1, Desert.Arch.HORSESHOE, GLASS, false);
        panel(s, 4, 5, 1, 7, 7, "north");
        panel(s, 11, 5, 1, 14, 7, "north");
        for (int z : new int[]{13, 17}) {
            Desert.lattice(s, 1, 3, z, "west", 1, 2, false);
            Desert.lattice(s, 17, 3, z, "east", 1, 2, false);
        }
        Desert.brazier(s, 2, 11, 10);
        Desert.brazier(s, 16, 11, 10);
        Desert.brazier(s, 3, 7, 15);
        Desert.brazier(s, 15, 7, 15);

        // the loft and its stair along the west wall
        s.fill(2, 5, 2, 16, 5, 5, FLOOR);
        for (int x = 3; x <= 16; x++) {
            s.set(x, 6, 5, FENCE);
        }
        flight(s, 2, 1, 10, "north", 5);
        // under the loft: the counter and the stores of the guild
        Furnish.books(s, 2, 2, 16, 2, 1, 3);
        registrar(s, 9, 1, 5, 1);
        Furnish.crate(s, 14, 1, 3, "oak");
        Furnish.crate(s, 15, 1, 3, "oak");
        Furnish.crate(s, 15, 2, 3, "oak");
        Furnish.sack(s, 16, 1, 3, true);
        Furnish.sack(s, 16, 1, 4, false);
        Desert.jar(s, 3, 1, 3);
        Desert.jar(s, 4, 1, 3);
        Desert.lamp(s, 5, 4, 4);
        Desert.lamp(s, 13, 4, 4);
        // the hall
        contractBoard(s, 16, 1, 8, "west", 3);
        for (int x0 : new int[]{4, 11}) {
            int x1 = x0 == 4 ? 7 : 13;
            Furnish.longTable(s, x0, 8, x1, 8, 1, "oak");
            Furnish.bench(s, x0, 7, x1, 7, 1, "south", "oak");
            Furnish.bench(s, x0, 9, x1, 9, 1, "north", "oak");
            s.set(x0 + 1, 2, 8, Furnish.candles(3));
        }
        Desert.brazier(s, 9, 1, 8);
        trophies(s, 7, 1, 10, "north", 4);
        trophies(s, 14, 1, 10, "north", 3);
        s.set(15, 1, 10, "minecraft:bone_block[axis=y]").set(15, 2, 10, "minecraft:skeleton_skull[rotation=8]");
        hangChandelier(s, 9, 6, 8);
        for (int[] p : new int[][]{{5, 8}, {13, 8}, {3, 10}, {15, 7}}) {
            Desert.lamp(s, p[0], 7, p[1]);
        }
        // the loft: the map room on the west, the chamber of the master on the east behind a screen of shelves
        int loft = 6;
        Furnish.books(s, 2, 2, 3, 2, loft, loft + 2);
        guildMap(s, 4, loft + 1, 2, 5);
        s.fill(4, loft, 2, 8, loft, 2, Kit.topSlab(FLOOR_SLAB));
        mapTable(s, 6, loft, 3);
        s.set(3, loft, 3, lectern("south"));
        Furnish.books(s, 10, 2, 10, 3, loft, loft + 1);
        Furnish.canopyBed(s, 14, loft, 4, "north", "oak");
        Desert.rug(s, 11, 2, 12, 4, loft);
        Desert.trayTable(s, 12, loft, 3);
        Desert.jar(s, 16, loft, 4);
        Desert.lamp(s, 6, 9, 3);
        Desert.lamp(s, 12, 9, 2);
        s.set(16, loft + 2, 3, Kit.wallBanner(GUILD, "west"));
        // the galleries: a divan for the guests, a training ground
        Desert.divan(s, 2, 13, 2, 17, 1, "east", false);
        Desert.trayTable(s, 4, 1, 15);
        Desert.jar(s, 4, 1, 12);
        Desert.jar(s, 4, 1, 18);
        s.set(16, 1, 13, "minecraft:grindstone[face=floor,facing=west]");
        s.set(16, 1, 14, FENCE).set(16, 2, 14, "minecraft:skeleton_skull[rotation=4]");
        s.set(16, 1, 16, TARGET).set(16, 2, 16, TARGET);
        s.set(15, 1, 17, HAY).set(15, 2, 17, "minecraft:carved_pumpkin[facing=west]");
        Furnish.crate(s, 16, 1, 18, "spruce");
        for (int x : new int[]{3, 15}) {
            Desert.lamp(s, x, 5, 13);
            Desert.lamp(s, x, 5, 17);
        }
        lampPost(s, 6, 1, 18);
        lampPost(s, 12, 1, 18);
        palms(s, 6, 12, 12, 18, 7, 7, new int[]{6, 14}, new int[]{12, 14});
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{9, 1, 18}, List.of(new int[]{8, 1, 6}));
    }

    /**
     * Rank S: a caravanserai with a watch tower. A tall hall under a ribbed dome on a glazed drum, its gate in a framed arch
     * to the court; on the west of the court a range of two storeys, both opening to the court through arcades of pointed
     * arches, the upper behind a balustrade, a wind tower on its terrace; on the east a block of masonry with a wall
     * fountain and, at the corner of the front, the watch tower of the guild with its floors and a ladder, its top crowned
     * with merlons and a little domed lookout with a signal fire; in the front a portal with a deep pointed iwan, tiles over
     * the gate. In the hall two long tables with benches, a brazier, the contract board, trophies, divans by the counter of
     * the registrar; in the range a lounge with a wall fountain under the map room; in the tower the armoury, the chamber of
     * the master with his bed of state, the trophy room and the watch room.
     */
    private static Drawn guildS() {
        Structure s = new Structure(23, 35, 25);
        s.fill(1, 0, 1, 21, 0, 22, BASE);
        s.fill(2, 0, 2, 20, 0, 21, PAVING);
        s.fill(16, 0, 16, 22, 0, 22, BASE);
        s.fill(6, 0, 23, 16, 0, 23, BASE);
        Desert.zellige(s, 8, 4, 14, 10, 0);
        basin(s, 11, 0, 18, 2);
        // the west range: two storeys, arcades on both, a wind tower on its terrace
        s.fill(1, 1, 13, 1, 10, 22, WALL);
        s.fill(1, 1, 22, 6, 10, 22, WALL);
        s.fill(1, 1, 13, 1, 1, 22, BASE);
        s.fill(1, 1, 22, 6, 1, 22, BASE);
        s.fill(2, 6, 14, 5, 6, 21, FLOOR);
        Desert.flatRoof(s, 1, 13, 6, 22, 11, Desert.Merlons.STEPPED, false);
        Desert.arcade(s, 6, 13, 6, 22, 1, 3, 5, "east", 3, Desert.Arch.POINTED);
        s.fill(6, 6, 13, 6, 6, 22, TRIM);
        Desert.arcade(s, 6, 13, 6, 22, 7, 2, 10, "east", 3, Desert.Arch.POINTED);
        for (int z = 14; z <= 21; z++) {
            if (!s.solid(6, 7, z)) {
                s.set(6, 7, z, Desert.BASE_POST);
            }
        }
        windTower(s, 3, 11, 20, 3, 5);
        // the east block with its wall fountain
        s.fill(16, 1, 13, 21, 10, 15, WALL);
        s.fill(16, 1, 13, 21, 1, 15, BASE);
        Desert.flatRoof(s, 16, 13, 21, 16, 11, Desert.Merlons.STEPPED, false);
        Desert.wallFountain(s, 15, 1, 14, "west");
        Desert.lattice(s, 16, 5, 14, "west", 1, 2, false);
        // the hall
        block(s, 1, 1, 21, 13, 11);
        Desert.flatRoof(s, 1, 1, 21, 13, 12, Desert.Merlons.POINTED, true);
        domeOn(s, 11, 12, 7, 9, 5, Desert.DomeKind.RIBBED);
        Desert.doorway(s, 11, 1, 13, "south", 3, 3, 1, Desert.Arch.POINTED, true);
        // the portal with its deep iwan, tiles over the gate at its back
        portal(s, 6, 16, 22, 23, 17, 5, 6, Desert.Arch.POINTED, 1);
        Desert.doorway(s, 11, 1, 22, "south", 3, 3, 1, Desert.Arch.POINTED, false);
        for (int y = 7; y <= 10; y++) {
            for (int x = 9; x <= 13; x++) {
                if (s.solid(x, y, 22)) {
                    s.set(x, y, 22, Desert.glazed((x + y) % 2 == 0 ? Desert.TILE : Desert.TILE_CYAN, "south"));
                }
            }
        }
        Desert.brazier(s, 9, 1, 23);
        Desert.brazier(s, 13, 1, 23);
        s.set(9, 5, 23, Kit.wallBanner(GUILD, "east")).set(13, 5, 23, Kit.wallBanner(GUILD, "west"));
        Desert.lamp(s, 11, 7, 23);
        // the watch tower on the front corner
        watchTower(s, 16, 16, 22, 22);
        // windows and tiles of the hall
        for (int z : new int[]{4, 10}) {
            Desert.lattice(s, 1, 3, z, "west", 1, 2, false);
            Desert.lattice(s, 21, 3, z, "east", 1, 2, false);
            Desert.lattice(s, 1, 8, z, "west", 1, 2, true);
            Desert.lattice(s, 21, 8, z, "east", 1, 2, true);
        }
        Desert.arch(s, 1, 2, 7, "west", 1, 3, 1, Desert.Arch.HORSESHOE, GLASS, false);
        Desert.arch(s, 21, 2, 7, "east", 1, 3, 1, Desert.Arch.HORSESHOE, GLASS, false);
        panel(s, 3, 5, 1, 8, 9, "north");
        panel(s, 14, 5, 1, 19, 9, "north");
        Desert.lattice(s, 3, 3, 22, "south", 1, 2, false);
        Desert.lattice(s, 3, 8, 22, "south", 1, 2, false);
        Desert.lattice(s, 1, 8, 18, "west", 1, 2, false);
        Desert.brazier(s, 2, 13, 12);
        Desert.brazier(s, 20, 13, 2);
        Desert.brazier(s, 2, 13, 2);
        Desert.brazier(s, 20, 13, 12);
        Desert.brazier(s, 4, 12, 15);
        Desert.brazier(s, 19, 12, 14);

        // the hall: the counter, divans by it, the board, two tables, the brazier, trophies
        Furnish.books(s, 2, 2, 20, 2, 1, 3);
        registrar(s, 11, 1, 4, 1);
        Desert.divan(s, 4, 3, 8, 3, 1, "south", false);
        Desert.divan(s, 14, 3, 18, 3, 1, "south", false);
        contractBoard(s, 2, 1, 8, "east", 5);
        for (int x : new int[]{6, 16}) {
            Furnish.longTable(s, x, 6, x, 10, 1, "oak");
            Furnish.bench(s, x - 1, 6, x - 1, 10, 1, "east", "oak");
            Furnish.bench(s, x + 1, 6, x + 1, 10, 1, "west", "oak");
            s.set(x, 2, 8, Furnish.candles(3));
        }
        Desert.brazier(s, 11, 1, 8);
        trophies(s, 20, 1, 6, "west", 5);
        s.set(20, 1, 11, "minecraft:bone_block[axis=y]").set(20, 2, 11, "minecraft:skeleton_skull[rotation=4]");
        s.set(20, 1, 12, "minecraft:bone_block[axis=y]");
        hangChandelier(s, 11, 7, 8);
        for (int[] p : new int[][]{{4, 5}, {18, 5}, {6, 11}, {16, 11}, {3, 8}, {19, 8}}) {
            Desert.lamp(s, p[0], 7, p[1]);
        }
        s.set(2, 7, 4, Kit.wallBanner(GUILD, "east")).set(20, 7, 4, Kit.wallBanner(GUILD, "west"));
        // the west range: the lounge below, the map room above
        flight(s, 2, 1, 21, "north", 6);
        for (int[] p : new int[][]{{3, 17}, {3, 18}, {2, 19}}) {
            s.set(p[0], 7, p[1], FENCE);
        }
        Desert.divan(s, 3, 14, 5, 14, 1, "south", false);
        Desert.rug(s, 3, 16, 5, 20, 1);
        Desert.trayTable(s, 4, 1, 17);
        Desert.trayTable(s, 4, 1, 19);
        Desert.wallFountain(s, 5, 1, 21, "north");
        Desert.lamp(s, 4, 5, 15);
        Desert.lamp(s, 4, 5, 19);
        int up = 7;
        guildMap(s, 2, up + 1, 14, 4);
        s.fill(3, up, 14, 5, up, 14, Kit.topSlab(FLOOR_SLAB));
        s.set(5, up, 16, lectern("west"));
        Furnish.books(s, 5, 17, 5, 18, up, up + 1);
        mapTable(s, 4, up, 19);
        Desert.jar(s, 2, up, 21);
        Desert.jar(s, 5, up, 21);
        Desert.lamp(s, 4, 10, 16);
        Desert.lamp(s, 3, 10, 20);
        palms(s, 7, 14, 15, 21, 12, 7, new int[]{8, 16}, new int[]{14, 16});
        lampPost(s, 8, 1, 21);
        lampPost(s, 14, 1, 21);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{11, 1, 21}, List.of(new int[]{10, 1, 5}));
    }

    /**
     * The watch tower of the guild S on the front corner from (x1, z1) to (x2, z2), seven wide: a lower stage of three
     * storeys with bands of trim at the floors, an upper stage stepped in by a block over a sloped band of trim, and at its
     * top a parapet on a ring of corbels flush with the lower stage, crowned with pointed merlons; lattice windows, a pointed
     * doorway from the court on its west face, a ladder up through all the floors; on the ground the armoury, then the
     * chamber of the master of the guild with his bed of state, the trophy room and, in the upper stage, the watch room; on
     * its terrace a lookout on four piers under a little pointed dome with a signal fire in it.
     */
    private static void watchTower(Structure s, int x1, int z1, int x2, int z2) {
        int step = 16;
        int deck = 24;
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        // the lower stage and its floors
        s.walls(x1, 1, z1, x2, step - 1, z2, WALL);
        s.walls(x1, 1, z1, x2, 1, z2, BASE);
        s.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, PAVING);
        s.fill(x1 + 1, 2, z1 + 1, x2 - 1, step - 1, z2 - 1, AIR);
        for (int k : new int[]{6, 11}) {
            s.walls(x1, k, z1, x2, k, z2, TRIM);
            s.fill(x1 + 1, k, z1 + 1, x2 - 1, k, z2 - 1, FLOOR);
        }
        // the step, the upper stage, the corbels and the parapet with its merlons
        ring(s, x1, z1, x2, z2, step, false);
        s.walls(x1 + 1, step, z1 + 1, x2 - 1, deck - 1, z2 - 1, WALL);
        s.walls(x1 + 1, step, z1 + 1, x2 - 1, step, z2 - 1, TRIM);
        s.fill(x1 + 2, step, z1 + 2, x2 - 2, step, z2 - 2, FLOOR);
        s.fill(x1 + 2, step + 1, z1 + 2, x2 - 2, deck - 1, z2 - 2, AIR);
        ring(s, x1, z1, x2, z2, deck - 1, true);
        Desert.flatRoof(s, x1, z1, x2, z2, deck, Desert.Merlons.POINTED, false);
        s.fill(x1 + 1, deck, z1 + 1, x2 - 1, deck, z2 - 1, Desert.ROOF.block());
        // the ladder against a pilaster in the lower stage and the wall of the upper one
        s.fill(x2 - 1, 1, cz, x2 - 1, step - 1, cz, WALL);
        for (int y = 1; y <= deck; y++) {
            s.set(x2 - 2, y, cz, Kit.ladder("west"));
        }
        Desert.doorway(s, x1, 1, cz, "west", 1, 2, 1, Desert.Arch.POINTED, true);
        for (int k : new int[]{1, 6, 11}) {
            Desert.lattice(s, cx, k + 2, z2, "south", 1, 2, false);
            Desert.lattice(s, x2, k + 2, z2 - 1, "east", 1, 2, false);
            Desert.lamp(s, cx, k + 3, cz + 2);
        }
        Desert.lattice(s, cx, step + 3, z2 - 1, "south", 1, 2, false);
        Desert.lattice(s, cx, step + 3, z1 + 1, "north", 1, 2, false);
        Desert.lattice(s, x1 + 1, step + 3, cz, "west", 1, 2, false);
        Desert.lamp(s, cx, deck - 2, cz);
        // the lookout: four piers, a ring of trim, a little dome, the signal fire
        for (int[] p : new int[][]{{x1 + 1, z1 + 1}, {x2 - 1, z1 + 1}, {x1 + 1, z2 - 1}, {x2 - 1, z2 - 1}}) {
            s.set(p[0], deck + 1, p[1], BASE).set(p[0], deck + 2, p[1], WALL).set(p[0], deck + 3, p[1], CARVED);
        }
        for (int z = z1 + 1; z <= z2 - 1; z++) {
            for (int x = x1 + 1; x <= x2 - 1; x++) {
                if (x == x1 + 1 || x == x2 - 1 || z == z1 + 1 || z == z2 - 1) {
                    s.set(x, deck + 4, z, TRIM);
                }
            }
        }
        Desert.dome(s, cx, deck + 5, cz, 5, Desert.DomeKind.POINTED);
        Desert.brazier(s, cx, deck + 1, cz);
        // the armoury
        s.set(x1 + 1, 1, z1 + 1, "minecraft:grindstone[face=floor,facing=south]");
        s.set(x1 + 2, 1, z1 + 1, "minecraft:grindstone[face=floor,facing=south]");
        Furnish.crate(s, x1 + 1, 1, z2 - 1, "spruce");
        Furnish.crate(s, x1 + 2, 1, z2 - 1, "spruce");
        Furnish.sack(s, x1 + 3, 1, z2 - 1, false);
        s.set(x1 + 4, 1, z1 + 1, FENCE).set(x1 + 4, 2, z1 + 1, "minecraft:skeleton_skull[rotation=0]");
        s.set(cx, 3, z1 + 1, Kit.wallBanner(GUILD, "south"));
        // the chamber of the master
        Furnish.canopyBed(s, x1 + 2, 7, z1 + 3, "north", "oak");
        Desert.rug(s, x1 + 1, z2 - 2, x1 + 3, z2 - 1, 7);
        Desert.trayTable(s, x1 + 2, 7, z2 - 1);
        Desert.jar(s, x2 - 2, 7, z1 + 1);
        // the trophy room
        trophies(s, x1 + 1, 12, z1 + 1, "south", 3);
        s.set(x1 + 1, 12, z2 - 1, "minecraft:bone_block[axis=y]").set(x1 + 1, 13, z2 - 1, "minecraft:wither_skeleton_skull[rotation=12]");
        s.set(x1 + 2, 12, z2 - 1, TARGET);
        // the watch room
        s.set(cx - 1, step + 1, cz - 1, Desert.BASE_POST)
                .set(cx - 1, step + 2, cz - 1, "minecraft:lightning_rod[facing=north,powered=false,waterlogged=false]");
        s.set(cx, step + 1, cz + 1, Kit.stairs(FLOOR_STAIRS, "south"));
    }

    /**
     * A ring of stairs of trim on the rim of a square from (x1, z1) to (x2, z2) at layer {@code y}, their high side inward:
     * standing ({@code hanging} false), the sloped band where a tower steps in; hanging upside down, the corbels under a
     * parapet that juts out over the stage below.
     */
    private static void ring(Structure s, int x1, int z1, int x2, int z2, int y, boolean hanging) {
        String half = hanging ? "top" : "bottom";
        for (int x = x1; x <= x2; x++) {
            s.set(x, y, z1, Desert.TRIM_STAIRS + "[facing=south,half=" + half + ",shape=straight,waterlogged=false]");
            s.set(x, y, z2, Desert.TRIM_STAIRS + "[facing=north,half=" + half + ",shape=straight,waterlogged=false]");
        }
        for (int z = z1 + 1; z < z2; z++) {
            s.set(x1, y, z, Desert.TRIM_STAIRS + "[facing=east,half=" + half + ",shape=straight,waterlogged=false]");
            s.set(x2, y, z, Desert.TRIM_STAIRS + "[facing=west,half=" + half + ",shape=straight,waterlogged=false]");
        }
        s.set(x1, y, z1, Desert.TRIM_STAIRS + "[facing=south,half=" + half + ",shape=outer_left,waterlogged=false]");
        s.set(x2, y, z1, Desert.TRIM_STAIRS + "[facing=south,half=" + half + ",shape=outer_right,waterlogged=false]");
        s.set(x1, y, z2, Desert.TRIM_STAIRS + "[facing=north,half=" + half + ",shape=outer_right,waterlogged=false]");
        s.set(x2, y, z2, Desert.TRIM_STAIRS + "[facing=north,half=" + half + ",shape=outer_left,waterlogged=false]");
    }

    /**
     * Rank SS: a great caravanserai, one of the main buildings of a capital. A great hall under a high ribbed dome on a
     * glazed drum, its iwan to the court a pishtaq of its own; side halls on its two flanks, each of two storeys with a wind
     * tower on its terrace; ranges of two storeys round the court, both storeys opening to it through arcades of pointed arches
     * on slender columns; a court with a star fountain, palms and lamps; round towers with merlons on the front corners; the
     * portal: a high pishtaq with a deep pointed iwan, a field of tiles, flanked by two slender turrets with balconies.
     * Inside the hall a gallery along the back joins the upper rooms of the side halls, the counter of the registrar under it,
     * two long tables, a brazier, the contract board and trophies; the west side hall holds the armoury under the map room,
     * the east one the kitchen under the chamber of the master of the guild; the west range a lounge under rooms with beds
     * of state for the guests, the east range a training ground under the hall of trophies.
     */
    private static Drawn guildSS() {
        Structure s = new Structure(31, 35, 33);
        s.fill(2, 0, 2, 28, 0, 28, BASE);
        s.fill(3, 0, 3, 27, 0, 27, PAVING);
        s.fill(9, 0, 28, 21, 0, 30, BASE);
        Desert.zellige(s, 11, 6, 19, 12, 0);
        // the court: paving with tiles along its sides, a star fountain, lamps
        for (int x = 9; x <= 21; x++) {
            s.set(x, 0, 19, Desert.glazed(Desert.TILE_CYAN, "east"));
            s.set(x, 0, 27, Desert.glazed(Desert.TILE_CYAN, "east"));
        }
        Desert.starFountain(s, 15, 0, 23, 2);
        for (int[] p : new int[][]{{12, 20}, {18, 20}, {12, 26}, {18, 26}}) {
            lampPost(s, p[0], 1, p[1]);
        }
        // the ranges round the court and the side halls, all of two storeys
        for (int side = -1; side <= 1; side += 2) {
            int xo = side < 0 ? 2 : 28;
            int xa = side < 0 ? 8 : 22;
            int xl = Math.min(xo, xa);
            int xh = Math.max(xo, xa);
            s.fill(xo, 1, 2, xo, 10, 28, WALL);
            s.fill(xo, 1, 2, xo, 1, 28, BASE);
            s.fill(xl, 1, 2, xh, 10, 2, WALL);
            s.fill(xl, 1, 2, xh, 1, 2, BASE);
            s.fill(xl, 1, 28, xh, 10, 28, WALL);
            s.fill(xl, 1, 28, xh, 1, 28, BASE);
            s.fill(xl, 1, 16, xh, 10, 16, WALL);
            s.fill(xl, 1, 16, xh, 1, 16, BASE);
            s.fill(xl + 1, 6, 3, xh - 1, 6, 15, FLOOR);
            s.fill(xl + 1, 6, 17, xh - 1, 6, 27, FLOOR);
            s.fill(xa, 6, 16, xa, 6, 28, TRIM);
            Desert.flatRoof(s, xl, 2, xh, 16, 11, Desert.Merlons.STEPPED, false);
            Desert.flatRoof(s, xl, 16, xh, 28, 11, Desert.Merlons.STEPPED, false);
            Desert.arcade(s, xa, 16, xa, 28, 1, 3, 5, side < 0 ? "east" : "west", 3, Desert.Arch.POINTED);
            Desert.arcade(s, xa, 16, xa, 28, 7, 2, 10, side < 0 ? "east" : "west", 3, Desert.Arch.POINTED);
            for (int z = 17; z <= 27; z++) {
                if (!s.solid(xa, 7, z)) {
                    s.set(xa, 7, z, Desert.BASE_POST);
                }
            }
            // a wind tower on the side hall, the round tower with its little dome on the front corner
            windTower(s, (xl + xh) / 2, 11, 9, 5, 7);
            roundTower(s, side < 0 ? 3 : 27, 28, 3, 14);
        }
        // the great hall with its own pishtaq on the court
        block(s, 8, 2, 22, 16, 13);
        Desert.flatRoof(s, 8, 2, 22, 16, 14, Desert.Merlons.POINTED, true);
        domeOn(s, 15, 14, 9, 11, 4, Desert.DomeKind.RIBBED);
        portal(s, 10, 20, 16, 18, 19, 5, 6, Desert.Arch.POINTED, 2);
        Desert.doorway(s, 15, 1, 16, "south", 3, 3, 1, Desert.Arch.POINTED, false);
        s.fill(13, 7, 16, 17, 9, 16, Desert.glazed(Desert.TILE, "south"));
        Desert.lamp(s, 15, 7, 17);
        // the portal of the caravanserai between two slender turrets
        portal(s, 9, 21, 28, 30, 21, 7, 8, Desert.Arch.POINTED, 2);
        Desert.doorway(s, 15, 1, 28, "south", 3, 4, 1, Desert.Arch.POINTED, false);
        for (int y = 8; y <= 14; y++) {
            for (int x = 12; x <= 18; x++) {
                if (s.solid(x, y, 28)) {
                    s.set(x, y, 28, Desert.glazed((x + y) % 2 == 0 ? Desert.TILE : Desert.TILE_CYAN, "south"));
                }
            }
        }
        s.fill(8, 0, 30, 10, 0, 32, BASE);
        s.fill(20, 0, 30, 22, 0, 32, BASE);
        for (int x : new int[]{9, 21}) {
            int balcony = Desert.minaret(s, x, 1, 31, 3, 30, null);
            for (int[] d : new int[][]{{2, 1}, {-2, 1}, {2, -1}, {-2, -1}}) {
                s.set(x + d[0], balcony + 2, 31 + d[1], Kit.lantern());
            }
        }
        Desert.brazier(s, 13, 1, 30);
        Desert.brazier(s, 17, 1, 30);
        s.set(12, 6, 30, Kit.wallBanner(GUILD, "east")).set(18, 6, 30, Kit.wallBanner(GUILD, "west"));
        Desert.lamp(s, 15, 9, 29);
        Desert.lamp(s, 15, 9, 30);
        // doors between the halls and the ranges, below and above
        for (int x : new int[]{5, 25}) {
            s.door(x, 1, 16, Desert.DOOR, "south", "left");
            s.door(x, 7, 16, Desert.DOOR, "south", "left");
        }
        s.door(8, 1, 14, Desert.DOOR, "east", "left");
        s.door(22, 1, 14, Desert.DOOR, "west", "left");
        s.door(8, 7, 4, Desert.DOOR, "east", "left");
        s.door(22, 7, 4, Desert.DOOR, "west", "left");
        // windows: lattices in the outer walls, keyholes, tiles
        for (int z : new int[]{6, 12, 20, 24}) {
            Desert.lattice(s, 2, 3, z, "west", 1, 2, false);
            Desert.lattice(s, 28, 3, z, "east", 1, 2, false);
            Desert.lattice(s, 2, 8, z, "west", 1, 2, z < 16);
            Desert.lattice(s, 28, 8, z, "east", 1, 2, z < 16);
        }
        for (int x : new int[]{11, 19}) {
            Desert.lattice(s, x, 9, 2, "north", 1, 2, false);
        }
        panel(s, 13, 3, 2, 17, 8, "north");
        Desert.arch(s, 5, 3, 2, "north", 1, 2, 1, Desert.Arch.HORSESHOE, GLASS, false);
        Desert.arch(s, 25, 3, 2, "north", 1, 2, 1, Desert.Arch.HORSESHOE, GLASS, false);
        for (int[] p : new int[][]{{9, 15, 3}, {21, 15, 3}, {9, 15, 15}, {21, 15, 15}, {4, 12, 20}, {26, 12, 20}, {4, 12, 8},
                {26, 12, 12}}) {
            Desert.brazier(s, p[0], p[1], p[2]);
        }

        // the great hall: the gallery along the back, the counter under it, the board, tables, the brazier, trophies
        s.fill(9, 6, 3, 21, 6, 5, FLOOR);
        for (int x = 9; x <= 21; x++) {
            s.set(x, 7, 5, FENCE);
        }
        Furnish.books(s, 9, 3, 21, 3, 1, 3);
        Desert.divan(s, 10, 4, 13, 4, 1, "south", false);
        Desert.divan(s, 17, 4, 20, 4, 1, "south", false);
        registrar(s, 15, 1, 6, 1);
        Desert.lamp(s, 11, 5, 5);
        Desert.lamp(s, 19, 5, 5);
        contractBoard(s, 9, 1, 10, "east", 5);
        for (int x : new int[]{13, 17}) {
            Furnish.longTable(s, x, 9, x, 13, 1, "oak");
            Furnish.bench(s, x - 1, 9, x - 1, 13, 1, "east", "oak");
            Furnish.bench(s, x + 1, 9, x + 1, 13, 1, "west", "oak");
            s.set(x, 2, 10, Furnish.candles(3));
            s.set(x, 2, 12, Furnish.candles(2));
        }
        Desert.brazier(s, 15, 1, 11);
        trophies(s, 21, 1, 8, "west", 5);
        s.set(21, 1, 13, "minecraft:bone_block[axis=y]").set(21, 2, 13, "minecraft:skeleton_skull[rotation=4]");
        hangChandelier(s, 15, 9, 9);
        for (int[] p : new int[][]{{11, 8}, {19, 8}, {11, 14}, {19, 14}, {15, 14}}) {
            Desert.lamp(s, p[0], 8, p[1]);
        }
        s.set(9, 9, 7, Kit.wallBanner(GUILD, "east")).set(21, 9, 7, Kit.wallBanner(GUILD, "west"));
        Desert.lamp(s, 12, 10, 4);
        Desert.lamp(s, 18, 10, 4);
        // the west side hall: the armoury below, the map room above
        flight(s, 3, 1, 15, "north", 6);
        for (int[] p : new int[][]{{4, 11}, {4, 12}, {3, 13}}) {
            s.set(p[0], 7, p[1], FENCE);
        }
        for (int z = 4; z <= 8; z += 2) {
            s.set(7, 1, z, "minecraft:grindstone[face=floor,facing=west]");
            s.set(7, 3, z, "minecraft:skeleton_wall_skull[facing=west]");
        }
        s.set(7, 1, 5, FENCE).set(7, 2, 5, "minecraft:skeleton_skull[rotation=4]");
        s.set(7, 1, 7, TARGET);
        Furnish.crate(s, 3, 1, 3, "spruce");
        Furnish.crate(s, 4, 1, 3, "spruce");
        Furnish.crate(s, 3, 2, 3, "spruce");
        Furnish.sack(s, 5, 1, 3, false);
        Furnish.sack(s, 6, 1, 3, true);
        s.set(5, 1, 14, HAY).set(5, 2, 14, "minecraft:carved_pumpkin[facing=east]");
        Desert.lamp(s, 5, 5, 6);
        Desert.lamp(s, 5, 5, 12);
        int up = 7;
        guildMap(s, 3, up + 1, 3, 5);
        s.fill(3, up, 3, 7, up, 3, Kit.topSlab(FLOOR_SLAB));
        mapTable(s, 5, up, 5);
        s.set(6, up, 8, lectern("west"));
        Furnish.books(s, 7, 10, 7, 14, up, up + 2);
        Desert.rug(s, 4, 6, 6, 8, up);
        Desert.lamp(s, 5, 10, 6);
        Desert.lamp(s, 5, 10, 12);
        // the east side hall: the kitchen below, the chamber of the master above
        flight(s, 27, 1, 15, "north", 6);
        for (int[] p : new int[][]{{26, 11}, {26, 12}, {27, 13}}) {
            s.set(p[0], 7, p[1], FENCE);
        }
        Furnish.kitchen(s, 23, 1, 3, "south", "oak");
        Desert.jar(s, 26, 1, 3);
        Desert.jar(s, 27, 1, 3);
        Furnish.longTable(s, 24, 6, 24, 10, 1, "oak");
        Furnish.bench(s, 23, 6, 23, 10, 1, "east", "oak");
        Furnish.bench(s, 25, 6, 25, 10, 1, "west", "oak");
        Furnish.sack(s, 25, 1, 14, true);
        Furnish.crate(s, 26, 1, 14, "oak");
        Desert.lamp(s, 25, 5, 5);
        Desert.lamp(s, 25, 5, 11);
        Furnish.canopyBed(s, 25, up, 5, "north", "oak");
        Desert.rug(s, 23, 7, 25, 9, up);
        Desert.divan(s, 23, 12, 23, 14, up, "east", false);
        Desert.trayTable(s, 24, up, 8);
        Desert.jar(s, 23, up, 3);
        Desert.lamp(s, 24, 10, 6);
        Desert.lamp(s, 25, 10, 12);
        // the west range: a lounge below, rooms of state for guests above
        Desert.divan(s, 3, 18, 3, 23, 1, "east", true);
        Desert.rug(s, 6, 18, 7, 23, 1);
        Desert.trayTable(s, 6, 1, 19);
        Desert.trayTable(s, 6, 1, 22);
        Desert.wallFountain(s, 6, 1, 17, "south");
        for (int z : new int[]{18, 22}) {
            Desert.lamp(s, 5, 5, z);
            Desert.lamp(s, 5, 10, z);
        }
        Furnish.canopyBed(s, 5, up, 20, "east", "oak");
        Furnish.canopyBed(s, 5, up, 24, "east", "oak");
        Desert.rug(s, 3, 18, 4, 23, up);
        Desert.jar(s, 3, up, 24);
        // the east range: a training ground below, the hall of trophies above
        for (int z = 18; z <= 22; z += 2) {
            s.set(27, 1, z, "minecraft:grindstone[face=floor,facing=west]");
        }
        s.set(27, 1, 19, TARGET).set(27, 2, 19, TARGET);
        s.set(27, 1, 23, TARGET).set(27, 2, 23, TARGET);
        s.set(24, 1, 21, HAY).set(24, 2, 21, "minecraft:carved_pumpkin[facing=west]");
        Furnish.crate(s, 27, 1, 21, "spruce");
        for (int z : new int[]{18, 22}) {
            Desert.lamp(s, 25, 5, z);
            Desert.lamp(s, 25, 10, z);
        }
        trophies(s, 27, up, 18, "west", 5);
        s.set(27, up, 23, "minecraft:bone_block[axis=y]").set(27, up + 1, 23, "minecraft:wither_skeleton_skull[rotation=4]");
        Furnish.bench(s, 23, 19, 23, 22, up, "east", "oak");
        palms(s, 9, 20, 21, 26, 12, 7, new int[]{10, 21}, new int[]{20, 21}, new int[]{10, 25}, new int[]{20, 25});
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{15, 1, 27}, List.of(new int[]{14, 1, 7}));
    }

    /**
     * A round tower of rough stone and wall on a front corner around (cx, cz), {@code r} in half-size, solid: a battered
     * foot, bands of trim, a ring of corbels under its parapet at layer {@code top} with merlons on it and a brazier on its
     * terrace.
     */
    private static void roundTower(Structure s, int cx, int cz, int r, int top) {
        for (int y = 0; y < top; y++) {
            Desert.plate(s, cx, y, cz, r, Desert.ROUND, y <= 2 ? BASE : y == 6 || y == top - 1 ? TRIM : WALL);
        }
        Desert.corbels(s, cx, top - 1, cz, r + 1, Desert.ROUND);
        Desert.plate(s, cx, top, cz, r + 1, Desert.ROUND, WALL);
        Desert.plate(s, cx, top, cz, r, Desert.ROUND, DECK);
        for (int dz = -r - 1; dz <= r + 1; dz++) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                if (rim(dx, dz, r + 1, Desert.ROUND)) {
                    Desert.put(s, cx + dx, top + 1, cz + dz, WALL);
                    if (Math.floorMod(dx + dz, 2) == 0) {
                        Desert.put(s, cx + dx, top + 2, cz + dz, Kit.slab(Desert.BASE_SLAB));
                    }
                }
            }
        }
        Desert.brazier(s, cx, top + 1, cz);
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

    /**
     * Rank B: one tower of astronomers. An eight-sided shaft on a square plinth, entered through a little portal with a
     * pointed doorway in its frame; on the ground floor the focus with the post and its chest, the brewing stands and the
     * cauldron, shelves; over it the mage's room with his bed, the shelves and the lectern; over that the research room;
     * on its top the observatory: a terrace flush with the shaft with a balustrade round an open pavilion of four piers
     * under a pointed dome, the armillary sphere standing in it.
     */
    private static Drawn mageB() {
        Structure s = new Structure(11, 31, 11);
        int c = 5;
        int platform = towerBody(s, c, c, 4, 3, false);
        towerPorch(s, c, c + 5, 3, 3);
        groundFloor(s, c, c, true);
        library(s, c, 6, c, -1, true, 1);
        researchRoom(s, c, 11, c, 1);
        observatory(s, c, platform, c, 7);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, c + 3}, List.of());
    }

    /**
     * Rank A: a taller tower of four storeys with a balcony half way up. The focus, the post, its chest and the brewing
     * stands on the ground floor; the room of the two mages with their beds; the library with the lectern; the research
     * room opening on the balcony; the observatory with the armillary sphere in its domed pavilion on the top.
     */
    private static Drawn mageA() {
        Structure s = new Structure(13, 36, 13);
        int c = 6;
        int platform = towerBody(s, c, c, 4, 4, true);
        towerPorch(s, c, c + 5, 3, 3);
        balcony(s, c, c, 4, 15);
        groundFloor(s, c, c, true);
        bedroom(s, c, 6, c, -1, 2);
        library(s, c, 11, c, 1, true, 0);
        researchRoom(s, c, 16, c, -1);
        observatory(s, c, platform, c, 7);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, c + 3}, List.of());
    }

    /**
     * Rank S: a tower of five storeys with a balcony, and before it a wing of one storey with two small pointed domes,
     * entered through a portal with a framed doorway: on the west of its vestibule the library with the lectern, on the east
     * the brewing room. In the tower the focus, the post and its chest on the ground floor; the rooms of the three mages (two
     * beds, then one with a desk); the room of the stars opening on the balcony; the research room; the observatory on top.
     */
    private static Drawn mageS() {
        Structure s = new Structure(17, 40, 17);
        int c = 8;
        int cz = 5;
        // the wing first: the tower stands over its back wall
        s.fill(0, 0, 9, 16, 0, 15, BASE);
        s.fill(0, 1, 9, 16, 6, 15, WALL);
        s.fill(0, 1, 9, 16, 1, 15, BASE);
        Desert.flatRoof(s, 0, 9, 16, 15, 7, Desert.Merlons.STEPPED, false);
        int platform = towerBody(s, c, cz, 4, 5, true);
        balcony(s, c, cz, 4, 15);
        int[][] rooms = {{1, 5}, {7, 9}, {11, 15}};
        for (int[] r : rooms) {
            s.fill(r[0], 0, 10, r[1], 0, 14, PAVING);
            s.fill(r[0], 1, 10, r[1], 6, 14, AIR);
        }
        for (int x : new int[]{0, 1, 2, 3, 4, 5, 11, 12, 13, 14, 15, 16}) {
            s.fill(x, 1, 9, x, 6, 9, WALL);
            s.set(x, 1, 9, BASE);
        }
        Desert.zellige(s, 7, 10, 9, 14, 0);
        for (int x : new int[]{3, 13}) {
            s.set(x, 7, 12, AIR).set(x - 1, 7, 12, AIR).set(x + 1, 7, 12, AIR).set(x, 7, 11, AIR).set(x, 7, 13, AIR);
            Desert.dome(s, x, 8, 12, 5, Desert.DomeKind.POINTED);
        }
        // the portal of the wing and the doors inside
        s.fill(5, 7, 15, 11, 11, 15, WALL);
        s.fill(5, 12, 15, 11, 12, 15, TRIM);
        int above = Desert.doorway(s, c, 1, 15, "south", 1, 3, 1, Desert.Arch.POINTED, true);
        for (int y = above; y < 12; y++) {
            for (int x = 6; x <= 10; x++) {
                s.set(x, y, 15, Desert.glazed(Desert.TILE_CYAN, "south"));
            }
        }
        Desert.crown(s, true, 5, 11, 15, 13, Desert.Merlons.POINTED);
        for (int x : new int[]{5, 11}) {
            s.set(x, 13, 15, WALL).set(x, 14, 15, CARVED).set(x, 15, 15, Kit.slab(Desert.BASE_SLAB));
        }
        Desert.doorway(s, c, 1, 9, "south", 1, 2, 1, Desert.Arch.POINTED, false);
        Desert.doorway(s, 6, 1, 12, "west", 1, 2, 1, Desert.Arch.POINTED, false);
        Desert.doorway(s, 10, 1, 12, "east", 1, 2, 1, Desert.Arch.POINTED, false);
        for (int x : new int[]{3, 13}) {
            Desert.lattice(s, x, 3, 15, "south", 1, 2, false);
        }
        Desert.lattice(s, 0, 3, 12, "west", 1, 2, false);
        Desert.lattice(s, 16, 3, 12, "east", 1, 2, false);
        Desert.lamp(s, c, 5, 12);
        Desert.brazier(s, 1, 8, 10);
        Desert.brazier(s, 15, 8, 10);
        Desert.brazier(s, 7, 8, 13);
        // the library and the brewing room of the wing
        Furnish.books(s, 1, 10, 5, 10, 1, 3);
        Furnish.books(s, 1, 11, 1, 14, 1, 3);
        s.set(3, 1, 11, lectern("south"));
        Desert.divan(s, 2, 14, 4, 14, 1, "north", false);
        Desert.rug(s, 2, 12, 4, 13, 1);
        Desert.trayTable(s, 3, 1, 12);
        Desert.lamp(s, 3, 4, 12);
        s.set(11, 1, 10, BREWING).set(13, 1, 10, BREWING).set(15, 1, 11, BREWING);
        s.set(12, 1, 10, Kit.topSlab(FLOOR_SLAB)).set(12, 2, 10, Furnish.candles(3));
        s.set(14, 1, 10, CAULDRON).set(15, 1, 12, CAULDRON);
        Furnish.crate(s, 15, 1, 14, "oak");
        Furnish.sack(s, 14, 1, 14, false);
        Furnish.sack(s, 15, 1, 13, true);
        Furnish.plant(s, 11, 1, 14, "azalea");
        s.set(13, 1, 12, Kit.topSlab(FLOOR_SLAB)).set(13, 2, 12, "minecraft:flower_pot");
        Desert.lamp(s, 13, 4, 12);
        // the tower
        groundFloor(s, c, cz, false);
        bedroom(s, c, 6, cz, -1, 2);
        bedroom(s, c, 11, cz, 1, 1);
        starRoom(s, c, 16, cz, -1);
        researchRoom(s, c, 21, cz, 1);
        observatory(s, c, platform, cz, 7);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, 14}, List.of());
    }

    /**
     * Rank SS: a madrasa of the mages. The domed hall of the focus at the back under a ribbed dome on a glazed drum, its
     * gate to the court in a framed horseshoe arch under a screen of tiles; on its flanks rooms of two storeys (the brewing
     * room under the research room on the west, the library under the study on the east); round the court with its basin
     * and palms ranges of two storeys, arcades below and lattice windows above, the rooms of the four mages with their beds
     * upstairs; the portal with a deep pointed iwan between two tall minarets with balconies; on the terraces of the ranges
     * two pavilions under pointed domes, the armillary sphere in the western, a telescope and a sundial in the eastern.
     */
    private static Drawn mageSS() {
        Structure s = new Structure(25, 47, 25);
        s.fill(1, 0, 1, 23, 0, 20, BASE);
        s.fill(2, 0, 2, 22, 0, 19, PAVING);
        s.fill(7, 0, 20, 17, 0, 22, BASE);
        basin(s, 12, 0, 16, 2);
        // the rooms on the flanks of the hall and the ranges round the court
        for (int side = -1; side <= 1; side += 2) {
            int xo = side < 0 ? 1 : 23;
            int xa = side < 0 ? 7 : 17;
            int xr = side < 0 ? 6 : 18;
            int xl = Math.min(xo, xa);
            int xh = Math.max(xo, xa);
            s.fill(xo, 1, 1, xo, 10, 20, WALL);
            s.fill(xo, 1, 1, xo, 1, 20, BASE);
            s.fill(Math.min(xo, xr), 1, 1, Math.max(xo, xr), 10, 1, WALL);
            s.fill(Math.min(xo, xr), 1, 1, Math.max(xo, xr), 1, 1, BASE);
            s.fill(xl, 1, 12, xh, 10, 12, WALL);
            s.fill(xl, 1, 12, xh, 1, 12, BASE);
            s.fill(xl, 1, 20, xh, 10, 20, WALL);
            s.fill(xl + 1, 6, 2, xh - 1, 6, 11, FLOOR);
            s.fill(xl + 1, 6, 13, xh - 1, 6, 19, FLOOR);
            s.fill(xa, 1, 12, xa, 10, 20, WALL);
            Desert.flatRoof(s, Math.min(xo, xr), 1, Math.max(xo, xr), 12, 11, Desert.Merlons.STEPPED, false);
            Desert.flatRoof(s, xl, 12, xh, 20, 11, Desert.Merlons.STEPPED, false);
            Desert.arcade(s, xa, 12, xa, 20, 1, 3, 5, side < 0 ? "east" : "west", 3, Desert.Arch.POINTED);
            s.fill(xa, 6, 12, xa, 6, 20, TRIM);
            for (int z : new int[]{14, 18}) {
                Desert.lattice(s, xa, 7, z, side < 0 ? "east" : "west", 1, 2, true);
            }
            Desert.lattice(s, xo, 3, 6, side < 0 ? "west" : "east", 1, 2, false);
            Desert.lattice(s, xo, 8, 6, side < 0 ? "west" : "east", 1, 2, false);
            Desert.lattice(s, xo, 8, 16, side < 0 ? "west" : "east", 1, 2, true);
            Desert.arch(s, xo, 2, 16, side < 0 ? "west" : "east", 1, 2, 1, Desert.Arch.HORSESHOE, GLASS, false);
            Desert.minaret(s, side < 0 ? 4 : 20, 0, 22, 5, 44, "north");
        }
        // the hall of the focus
        block(s, 6, 1, 18, 12, 12);
        s.fill(7, 1, 11, 17, 12, 11, WALL);
        Desert.flatRoof(s, 6, 1, 18, 12, 13, Desert.Merlons.POINTED, true);
        domeOn(s, 12, 13, 6, 9, 6, Desert.DomeKind.RIBBED);
        int above = Desert.doorway(s, 12, 1, 12, "south", 3, 2, 2, Desert.Arch.HORSESHOE, true);
        s.fill(7, 13, 12, 17, 17, 12, WALL);
        for (int y = above; y <= 16; y++) {
            for (int x = 8; x <= 16; x++) {
                s.set(x, y, 12, Desert.glazed(y == above || y == 16 ? Desert.TILE_BLUE : (x + y) % 2 == 0 ? Desert.TILE : Desert.TILE_CYAN,
                        "south"));
            }
        }
        s.fill(7, 17, 12, 17, 17, 12, TRIM);
        Desert.crown(s, true, 7, 17, 12, 18, Desert.Merlons.POINTED);
        for (int x : new int[]{7, 17}) {
            s.set(x, 18, 12, WALL).set(x, 19, 12, CARVED).set(x, 20, 12, Kit.slab(Desert.BASE_SLAB));
        }
        // the portal between the minarets
        portal(s, 7, 17, 20, 22, 17, 5, 6, Desert.Arch.POINTED, 2);
        Desert.doorway(s, 12, 1, 20, "south", 3, 3, 1, Desert.Arch.POINTED, false);
        s.fill(10, 7, 20, 14, 10, 20, Desert.glazed(Desert.TILE, "south"));
        Desert.lamp(s, 12, 7, 21);
        Desert.brazier(s, 10, 1, 22);
        Desert.brazier(s, 14, 1, 22);
        // doors between the rooms
        for (int x : new int[]{4, 20}) {
            s.door(x, 1, 12, Desert.DOOR, "south", "left");
            s.door(x, 7, 12, Desert.DOOR, "south", "left");
        }
        s.door(6, 1, 6, Desert.DOOR, "east", "left");
        s.door(18, 1, 6, Desert.DOOR, "west", "left");
        for (int x : new int[]{9, 15}) {
            Desert.lattice(s, x, 8, 1, "north", 1, 2, false);
        }
        panel(s, 10, 3, 1, 14, 7, "north");
        // the pavilions on the terraces of the ranges, the ladders up to them
        observatory(s, 4, 11, 16, 7);
        pavilion(s, 20, 11, 16, 7);
        Desert.dome(s, 20, 17, 16, 7, Desert.DomeKind.POINTED);
        sundial(s, 20, 12, 16);
        for (int y = 7; y <= 11; y++) {
            s.set(6, y, 13, Kit.ladder("west"));
            s.set(18, y, 13, Kit.ladder("east"));
        }
        for (int[] p : new int[][]{{4, 12, 3}, {20, 12, 3}, {7, 14, 3}, {17, 14, 3}, {7, 14, 10}, {17, 14, 10}}) {
            Desert.brazier(s, p[0], p[1], p[2]);
        }

        // the hall of the focus: the focus under the dome, the post and its chest, desks of the pupils, shelves
        Desert.zellige(s, 9, 3, 15, 9, 0);
        s.set(12, 1, 6, FOCUS);
        s.set(12, 1, 4, WORK_POST).set(13, 1, 4, Kit.chest("south"));
        Furnish.books(s, 7, 2, 11, 2, 1, 4);
        Furnish.books(s, 13, 2, 17, 2, 1, 4);
        s.set(12, 1, 2, CARVED).set(12, 2, 2, CLUSTER);
        for (int z : new int[]{4, 7}) {
            Furnish.desk(s, 8, 1, z, "east", "oak");
            Furnish.desk(s, 16, 1, z, "west", "oak");
        }
        Desert.wallFountain(s, 7, 1, 9, "east");
        Desert.wallFountain(s, 17, 1, 9, "west");
        Desert.rug(s, 10, 9, 14, 10, 1);
        hangChandelier(s, 12, 6, 8);
        for (int[] p : new int[][]{{8, 3}, {16, 3}, {8, 9}, {16, 9}}) {
            Desert.lamp(s, p[0], 7, p[1]);
        }
        // the west flank: the brewing room below, the research room above
        s.set(2, 1, 2, BREWING).set(3, 1, 2, CAULDRON).set(4, 1, 2, BREWING);
        s.set(5, 1, 2, Kit.topSlab(FLOOR_SLAB)).set(5, 2, 2, Furnish.candles(3));
        s.set(2, 1, 4, CAULDRON);
        Furnish.crate(s, 2, 1, 8, "oak");
        Furnish.sack(s, 2, 1, 9, false);
        Furnish.sack(s, 2, 1, 10, true);
        Furnish.plant(s, 5, 1, 10, "azalea");
        Furnish.plant(s, 5, 1, 8, "flowering_azalea");
        s.set(3, 1, 6, Kit.topSlab(FLOOR_SLAB)).set(3, 2, 6, "minecraft:flower_pot");
        Desert.lamp(s, 3, 5, 4);
        Desert.lamp(s, 4, 5, 9);
        Desert.rug(s, 3, 4, 5, 6, 7);
        s.set(4, 7, 5, ENCHANTING);
        Furnish.books(s, 2, 2, 5, 2, 7, 8);
        Furnish.books(s, 2, 3, 2, 6, 7, 8);
        s.set(5, 7, 9, CARTOGRAPHY);
        s.set(2, 7, 9, Desert.BASE_POST).set(2, 8, 9, AMETHYST);
        s.set(2, 9, 3, CLUSTER);
        Desert.lamp(s, 4, 10, 4);
        Desert.lamp(s, 4, 10, 9);
        // the east flank: the library below, the study above
        Furnish.books(s, 19, 2, 22, 2, 1, 4);
        Furnish.books(s, 22, 3, 22, 10, 1, 4);
        Furnish.books(s, 19, 10, 19, 11, 1, 2);
        s.set(20, 1, 4, lectern("south"));
        Desert.divan(s, 19, 7, 19, 9, 1, "east", false);
        Desert.trayTable(s, 20, 1, 8);
        Desert.lamp(s, 20, 5, 4);
        Desert.lamp(s, 21, 5, 9);
        Furnish.books(s, 19, 2, 22, 2, 7, 8);
        Furnish.desk(s, 21, 7, 5, "west", "oak");
        Furnish.desk(s, 21, 7, 8, "west", "oak");
        s.set(19, 7, 4, CARTOGRAPHY);
        Desert.rug(s, 19, 9, 20, 11, 7);
        Desert.lamp(s, 20, 10, 4);
        Desert.lamp(s, 20, 10, 9);
        // the ranges: lecture galleries below, the rooms of the mages above
        for (int side = -1; side <= 1; side += 2) {
            int xs = side < 0 ? 2 : 22;
            int xb = side < 0 ? 6 : 18;
            int xd = side < 0 ? 3 : 21;
            Desert.divan(s, side < 0 ? 3 : 19, 13, side < 0 ? 5 : 21, 13, 1, "south", false);
            Desert.rug(s, side < 0 ? 4 : 18, 15, side < 0 ? 6 : 20, 18, 1);
            Desert.trayTable(s, side < 0 ? 5 : 19, 1, 16);
            Desert.lamp(s, side < 0 ? 4 : 20, 5, 14);
            Desert.lamp(s, side < 0 ? 4 : 20, 5, 18);
            String color = side < 0 ? "light_blue" : "cyan";
            Furnish.bed(s, xb + side, 7, 15, side < 0 ? "east" : "west", color);
            Furnish.bed(s, xb + side, 7, 18, side < 0 ? "east" : "west", color);
            Desert.rug(s, side < 0 ? 4 : 20, 14, side < 0 ? 4 : 20, 19, 7);
            flight(s, xs, 1, 19, "north", 6);
            for (int[] p : new int[][]{{xd, 15}, {xd, 16}, {xs, 17}}) {
                s.set(p[0], 7, p[1], FENCE);
            }
            Desert.jar(s, xb, 7, 17);
            s.set(xb, 7, 16, Kit.topSlab(FLOOR_SLAB)).set(xb, 8, 16, Furnish.candles(2));
            Desert.lamp(s, side < 0 ? 4 : 20, 10, 15);
            Desert.lamp(s, side < 0 ? 4 : 20, 10, 18);
        }
        palms(s, 8, 13, 16, 19, 12, 7, new int[]{9, 14}, new int[]{15, 14});
        lampPost(s, 9, 1, 18);
        lampPost(s, 15, 1, 18);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{12, 1, 19}, List.of());
    }

    // ---------------------------------------------------------------- parts of the towers

    /**
     * The body of a tower of astronomers around (cx, cz): an eight-sided shaft of half-size {@code r} on a square plinth one
     * block wider (a solid course and a skirt of slabs), {@code levels} storeys of five layers (the ground floor of tiles,
     * the others of planks), a band of glazed tiles at each floor, keyhole windows on the north, slits of glass on the
     * diagonals; stairs climbing from storey to storey along the west and the east walls in turn, a railing round the hole
     * each leaves in the floor above, a ladder from the top storey through the floor of the observatory, which juts out one
     * block on a ring of corbels ({@code overhang}) or stays flush with the shaft, with a balustrade of rough stone posts.
     *
     * @return the layer of the floor of the observatory (one stands on the layer above)
     */
    private static int towerBody(Structure s, int cx, int cz, int r, int levels, boolean overhang) {
        Desert.plate(s, cx, 0, cz, r + 1, Desert.SQUARE, BASE);
        floorIn(s, cx, 0, cz, r, PAVING);
        for (int dz = -r - 1; dz <= r + 1; dz++) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                if (!Desert.inPlan(dx, dz, r, Desert.OCTAGON)) {
                    s.set(cx + dx, 1, cz + dz, BASE);
                    s.set(cx + dx, 2, cz + dz, Kit.slab(Desert.BASE_SLAB));
                }
            }
        }
        int top = 5 * levels;
        for (int y = 1; y < top; y++) {
            String state = y == 1 ? BASE : y % 5 == 0 ? Desert.glazed(Desert.TILE_CYAN, "north") : WALL;
            Desert.shell(s, cx, y, y, cz, r, Desert.OCTAGON, state);
            floorIn(s, cx, y, cz, r, AIR);
        }
        for (int k = 1; k < levels; k++) {
            floorIn(s, cx, 5 * k, cz, r, FLOOR);
        }
        for (int k = 0; k + 1 < levels; k++) {
            towerStair(s, cx, cz, r, 5 * k + 1, k % 2 == 0 ? -1 : 1);
        }
        int last = levels >= 2 && (levels - 2) % 2 == 0 ? -1 : 1;
        int lx = cx - last * (r - 1);
        for (int y = 5 * (levels - 1) + 1; y <= top; y++) {
            s.set(lx, y, cz, Kit.ladder(last < 0 ? "west" : "east"));
        }
        // windows
        int d = r - 1;
        for (int k = 0; k < levels; k++) {
            int y = 5 * k + 2;
            Desert.arch(s, cx, y, cz - r, "north", 1, 2, 1, Desert.Arch.HORSESHOE, GLASS, false);
            for (int[] g : new int[][]{{d, d}, {-d, d}, {d, -d}, {-d, -d}}) {
                if (rim(g[0], g[1], r, Desert.OCTAGON)) {
                    s.set(cx + g[0], y + 1, cz + g[1], GLASS);
                }
            }
        }
        // the observatory floor, on its corbels or flush with the shaft, and the balustrade
        int o = overhang ? r + 1 : r;
        if (overhang) {
            Desert.corbels(s, cx, top - 1, cz, o, Desert.OCTAGON);
        }
        Desert.plate(s, cx, top, cz, o, Desert.OCTAGON, TRIM);
        floorIn(s, cx, top, cz, r, PAVING);
        s.set(lx, top, cz, Kit.ladder(last < 0 ? "west" : "east"));
        for (int dz = -o; dz <= o; dz++) {
            for (int dx = -o; dx <= o; dx++) {
                if (rim(dx, dz, o, Desert.OCTAGON)) {
                    s.set(cx + dx, top + 1, cz + dz, Desert.BASE_POST);
                }
            }
        }
        return top;
    }

    /**
     * A stair inside a tower of half-size {@code r} from the storey where one stands at layer {@code y} to the floor four
     * layers over it: five steps along the west ({@code side} -1) or east (1) wall climbing north from two rows south of the
     * middle, the last of them in the plane of the floor above, and a railing round the hole they leave in it.
     */
    private static void towerStair(Structure s, int cx, int cz, int r, int y, int side) {
        flight(s, cx + side * (r - 1), y, cz + 2, "north", 5);
        int up = y + 5;
        s.set(cx + side * (r - 2), up, cz, FENCE);
        s.set(cx + side * (r - 2), up, cz - 1, FENCE);
        s.set(cx + side * (r - 1), up, cz + 1, FENCE);
    }

    /**
     * The entrance of a tower: a little portal standing on the row {@code z} before the south face of the tower, from
     * {@code cx - half} to {@code cx + half}, with a pointed doorway {@code jambs} high in its frame cut through it and the
     * face behind, the door in the face, a band of tiles and pointed merlons over the frame, pinnacles on its corners; its
     * threshold a slab of paving.
     */
    private static void towerPorch(Structure s, int cx, int z, int half, int jambs) {
        int top = jambs + 6;
        for (int x = cx - half; x <= cx + half; x++) {
            for (int y = 1; y <= top; y++) {
                s.set(x, y, z, y == 1 ? BASE : y == top ? TRIM : WALL);
                if (!s.solid(x, y, z - 1)) {
                    s.set(x, y, z - 1, y == 1 ? BASE : WALL);
                }
            }
            s.set(x, 0, z, BASE);
        }
        int above = Desert.arch(s, cx, 1, z, "south", 1, jambs, 2, Desert.Arch.POINTED, null, true);
        for (int y = above; y < top; y++) {
            for (int x = cx - half + 1; x < cx + half; x++) {
                s.set(x, y, z, Desert.glazed(Desert.TILE_CYAN, "south"));
            }
        }
        Desert.crown(s, true, cx - half, cx + half, z, top + 1, Desert.Merlons.POINTED);
        for (int x : new int[]{cx - half, cx + half}) {
            s.set(x, top + 1, z, WALL).set(x, top + 2, z, CARVED).set(x, top + 3, z, Kit.slab(Desert.BASE_SLAB));
        }
        s.door(cx, 1, z - 1, Desert.DOOR, "north", "left");
        s.set(cx, 0, z, Kit.slab(Desert.PAVING_SLAB));
        Desert.lamp(s, cx, jambs + 1, z);
    }

    /**
     * A balcony round a tower of half-size {@code r} at the floor of layer {@code floor}: a ring of corbels, a floor of trim
     * one block out of the shaft, a balustrade of rough stone posts with lanterns on its corners, and a pointed doorway onto it
     * from the storey on the south face.
     */
    private static void balcony(Structure s, int cx, int cz, int r, int floor) {
        Desert.corbels(s, cx, floor - 1, cz, r + 1, Desert.OCTAGON);
        for (int dz = -r - 1; dz <= r + 1; dz++) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                if (Desert.inPlan(dx, dz, r + 1, Desert.OCTAGON) && !Desert.inPlan(dx, dz, r, Desert.OCTAGON)) {
                    s.set(cx + dx, floor, cz + dz, TRIM);
                }
                if (rim(dx, dz, r + 1, Desert.OCTAGON)) {
                    s.set(cx + dx, floor + 1, cz + dz, Desert.BASE_POST);
                }
            }
        }
        for (int[] k : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            s.set(cx + k[0] * (r + 1), floor + 2, cz + k[1] * (r - 1), Kit.lantern());
        }
        Desert.doorway(s, cx, floor + 1, cz + r, "south", 1, 2, 1, Desert.Arch.POINTED, false);
    }

    /**
     * An open pavilion on a terrace or a tower whose floor is at layer {@code floor}: four piers on the diagonals of a round
     * plan {@code d} wide, the haunches of arches between them and a ring of trim on them; the dome is the caller's.
     *
     * @return the layer of the ring
     */
    private static int pavilion(Structure s, int cx, int floor, int cz, int d) {
        int r = (d - 1) / 2;
        int y = floor + 1;
        int ring = y + 3;
        for (int[] k : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            int px = cx + k[0] * (r - 1);
            int pz = cz + k[1] * (r - 1);
            s.set(px, y, pz, BASE);
            s.set(px, y + 1, pz, WALL).set(px, y + 2, pz, CARVED);
        }
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (!rim(dx, dz, r, Desert.ROUND)) {
                    continue;
                }
                s.set(cx + dx, ring, cz + dz, TRIM);
                // the haunches of the arches between the piers, leaning toward the piers
                if (Math.max(Math.abs(dx), Math.abs(dz)) == r && Math.min(Math.abs(dx), Math.abs(dz)) == 1) {
                    String out = Math.abs(dx) == r ? (dz > 0 ? "south" : "north") : (dx > 0 ? "east" : "west");
                    s.set(cx + dx, ring - 1, cz + dz, Kit.upside(Desert.TRIM_STAIRS, out));
                }
            }
        }
        return ring;
    }

    /**
     * The observatory on a tower or a terrace whose floor is at layer {@code floor}: an open {@link #pavilion} under a
     * pointed dome, the armillary sphere standing in its middle.
     */
    private static void observatory(Structure s, int cx, int floor, int cz, int d) {
        int ring = pavilion(s, cx, floor, cz, d);
        Desert.dome(s, cx, ring + 1, cz, d, Desert.DomeKind.POINTED);
        armillary(s, cx, floor + 1, cz);
    }

    /**
     * An armillary sphere standing on layer {@code y} at (cx, cz), five wide and seven high: a pedestal of carved stone; two
     * meridians of chains crossing over it (along x and along z) and the equator round them, an axis of end rods through
     * the middle with a gilt sun on it.
     */
    private static void armillary(Structure s, int cx, int y, int cz) {
        s.set(cx, y, cz, CARVED);
        int c = y + 3;
        String cx1 = "minecraft:chain[axis=x,waterlogged=false]";
        String cz1 = "minecraft:chain[axis=z,waterlogged=false]";
        String cy1 = Kit.chain();
        for (int k = -1; k <= 1; k++) {
            // the two meridians: their upright sides and their tops and feet
            s.set(cx + 2, c + k, cz, cy1).set(cx - 2, c + k, cz, cy1);
            s.set(cx, c + k, cz + 2, cy1).set(cx, c + k, cz - 2, cy1);
            s.set(cx + k, c + 2, cz, cx1).set(cx + k, c - 2, cz, cx1);
            s.set(cx, c + 2, cz + k, cz1).set(cx, c - 2, cz + k, cz1);
        }
        // the equator
        for (int k : new int[]{-1, 1}) {
            s.set(cx + 2, c, cz + k, cz1).set(cx - 2, c, cz + k, cz1);
            s.set(cx + k, c, cz + 2, cx1).set(cx + k, c, cz - 2, cx1);
        }
        // the axis and the sun
        s.set(cx, c - 2, cz, ROD_UP).set(cx, c - 1, cz, ROD_UP);
        s.set(cx, c, cz, Desert.GILT);
        s.set(cx, c + 1, cz, ROD_UP).set(cx, c + 2, cz, ROD_UP).set(cx, c + 3, cz, ROD_UP);
    }

    /**
     * A sundial and a telescope in a pavilion whose floor one stands on at layer {@code y}: a dial of smooth slabs round a
     * gnomon of end rods on a carved block, a telescope on its post looking north, a bench.
     */
    private static void sundial(Structure s, int cx, int y, int cz) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            s.set(cx + d[0], y, cz + d[1], Kit.slab(Desert.PAVING_SLAB));
        }
        s.set(cx, y, cz, CARVED).set(cx, y + 1, cz, ROD_UP).set(cx, y + 2, cz, ROD_UP);
        s.set(cx, y, cz - 2, Desert.BASE_POST).set(cx, y + 1, cz - 2, "minecraft:lightning_rod[facing=north,powered=false,waterlogged=false]");
        s.set(cx + 2, y, cz, Kit.stairs(FLOOR_STAIRS, "east")).set(cx - 2, y, cz, Kit.stairs(FLOOR_STAIRS, "west"));
        s.set(cx, y, cz + 2, Kit.topSlab(FLOOR_SLAB)).set(cx, y + 1, cz + 2, Furnish.candles(3));
    }

    // ---------------------------------------------------------------- rooms of the towers

    /**
     * Sets a cell of a storey of a tower around (cx, cz): {@code u} across from its middle toward the side {@code a} (1 the
     * east, -1 the west) from which the stair came up to this storey, {@code dz} along z.
     */
    private static void at(Structure s, int cx, int y, int cz, int a, int u, int dz, String state) {
        s.set(cx + a * u, y, cz + dz, state);
    }

    /** The way east or west of a storey drawn toward the side {@code a}: east stays east for a = 1 and turns west for -1. */
    private static String way(int a, String facing) {
        if (a > 0) {
            return facing;
        }
        return facing.equals("east") ? "west" : facing.equals("west") ? "east" : facing;
    }

    /**
     * The ground floor of a tower of half-size 4: the focus in its middle on tiles, the post of the mage two blocks from it
     * with the one chest beside it, shelves of books to the north, with {@code brewing} the brewing stands and the cauldron
     * on the east, else a desk; candles on a shelf, lanterns. The stair rises along the west wall and the door is south.
     */
    private static void groundFloor(Structure s, int cx, int cz, boolean brewing) {
        Desert.zellige(s, cx - 1, cz - 1, cx + 1, cz + 1, 0);
        s.set(cx, 1, cz, FOCUS);
        s.set(cx + 2, 1, cz - 2, WORK_POST).set(cx + 2, 1, cz - 3, Kit.chest("south"));
        if (brewing) {
            s.set(cx + 2, 1, cz + 2, BREWING).set(cx + 3, 1, cz + 1, CAULDRON).set(cx + 3, 1, cz - 1, BREWING);
        } else {
            Furnish.desk(s, cx + 3, 1, cz + 1, "west", "oak");
            Furnish.books(s, cx + 3, cz - 1, cx + 3, cz - 1, 1, 2);
            s.set(cx + 2, 1, cz + 2, Desert.BASE_POST).set(cx + 2, 2, cz + 2, AMETHYST);
        }
        s.set(cx + 1, 1, cz + 3, Kit.topSlab(FLOOR_SLAB)).set(cx + 1, 2, cz + 3, Furnish.candles(2));
        Furnish.books(s, cx - 1, cz - 3, cx + 1, cz - 3, 1, 3);
        Desert.lamp(s, cx - 1, 4, cz - 2);
        Desert.lamp(s, cx + 1, 4, cz + 2);
        Desert.lamp(s, cx + 1, 4, cz - 1);
    }

    /**
     * The room of the mages on a storey of a tower of half-size 4 where one stands at layer {@code y}, the stair having come
     * up on the side {@code a}: one or two beds with their heads to the north wall under the window, a shelf with candles,
     * a rug with a brass tray, a jar, lanterns.
     */
    private static void bedroom(Structure s, int cx, int y, int cz, int a, int beds) {
        if (beds >= 2) {
            Furnish.bed(s, cx - a, y, cz - 2, "north", "light_blue");
            Furnish.bed(s, cx + a, y, cz - 2, "north", "cyan");
            s.set(cx, y, cz - 3, Kit.topSlab(FLOOR_SLAB)).set(cx, y + 1, cz - 3, Furnish.candles(2));
        } else {
            Furnish.bed(s, cx, y, cz - 2, "north", "light_blue");
            at(s, cx, y, cz, a, -1, -3, Kit.topSlab(FLOOR_SLAB));
            at(s, cx, y + 1, cz, a, -1, -3, Furnish.candles(2));
            Desert.jar(s, cx + a, y, cz - 3);
            Furnish.desk(s, cx - 2 * a, y, cz, way(a, "east"), "oak");
        }
        Desert.rug(s, cx - 1, cz, cx + 1, cz + 2, y);
        Desert.trayTable(s, cx, y, cz + 1);
        at(s, cx, y, cz, a, -2, 1, "minecraft:decorated_pot[facing=south,waterlogged=false]");
        Desert.lamp(s, cx, y + 3, cz - 1);
        Desert.lamp(s, cx - a, y + 3, cz + 2);
    }

    /**
     * The library on a storey of a tower of half-size 4 where one stands at layer {@code y}, the stair having come up on
     * the side {@code a}: shelves of books round the walls, leaving the window, the stairs and the door free; with
     * {@code lectern} the lectern of the tower; a reading rug with a brass tray; {@code beds} beds of the mages (0 or 1).
     */
    private static void library(Structure s, int cx, int y, int cz, int a, boolean lectern, int beds) {
        for (int[] p : new int[][]{{-1, -3}, {1, -3}, {-2, -3}, {-2, -2}, {-2, -1}, {2, -3}}) {
            for (int k = 0; k < 3; k++) {
                at(s, cx, y + k, cz, a, p[0], p[1], "minecraft:bookshelf");
            }
        }
        if (beds > 0) {
            Furnish.bed(s, cx - a, y, cz + 2, "south", "light_blue");
            at(s, cx, y, cz, a, 1, 3, Kit.topSlab(FLOOR_SLAB));
            at(s, cx, y + 1, cz, a, 1, 3, Furnish.candles(2));
        } else {
            for (int[] p : new int[][]{{-1, 3}, {1, 3}}) {
                for (int k = 0; k < 2; k++) {
                    at(s, cx, y + k, cz, a, p[0], p[1], "minecraft:bookshelf");
                }
            }
            Desert.rug(s, cx - 1, cz, cx + 1, cz + 2, y);
        }
        if (lectern) {
            s.set(cx, y, cz - 2, lectern("south"));
        }
        Desert.trayTable(s, cx, y, cz + 1);
        at(s, cx, y + 3, cz, a, -2, -2, Furnish.candles(3));
        Desert.lamp(s, cx, y + 3, cz);
        Desert.lamp(s, cx + a, y + 3, cz + 2);
    }

    /**
     * The research room on a storey of a tower of half-size 4 where one stands at layer {@code y}, the stair having come up
     * on the side {@code a}: the enchanting table in its middle on a rug, shelves of books, a table of star charts, a globe
     * of amethyst on a post, candles.
     */
    private static void researchRoom(Structure s, int cx, int y, int cz, int a) {
        Desert.rug(s, cx - 1, cz - 1, cx + 1, cz + 1, y);
        s.set(cx, y, cz, ENCHANTING);
        for (int[] p : new int[][]{{-1, -3}, {1, -3}, {-2, -2}, {-2, -1}, {-1, 3}, {1, 3}, {2, 2}}) {
            at(s, cx, y, cz, a, p[0], p[1], "minecraft:bookshelf");
            at(s, cx, y + 1, cz, a, p[0], p[1], "minecraft:bookshelf");
        }
        at(s, cx, y, cz, a, -2, 1, CARTOGRAPHY);
        at(s, cx, y, cz, a, 2, 1, Desert.BASE_POST);
        at(s, cx, y + 1, cz, a, 2, 1, AMETHYST);
        at(s, cx, y + 2, cz, a, -2, -2, CLUSTER);
        at(s, cx, y + 2, cz, a, 2, 2, Furnish.candles(3));
        Desert.lamp(s, cx, y + 3, cz - 2);
        Desert.lamp(s, cx, y + 3, cz + 2);
    }

    /**
     * The room of the stars on a storey of a tower of half-size 4 where one stands at layer {@code y}, the stair having come
     * up on the side {@code a}: a telescope at the north window, a table of star charts, a desk, shelves, a brass astrolabe
     * on a stand.
     */
    private static void starRoom(Structure s, int cx, int y, int cz, int a) {
        s.set(cx, y, cz - 3, Desert.BASE_POST).set(cx, y + 1, cz - 3, "minecraft:lightning_rod[facing=north,powered=false,waterlogged=false]");
        s.set(cx, y, cz, CARTOGRAPHY);
        at(s, cx, y, cz, a, -1, 0, Kit.stairs(FLOOR_STAIRS, way(a, "west")));
        Furnish.desk(s, cx - 2 * a, y, cz + 1, way(a, "east"), "oak");
        for (int[] p : new int[][]{{-2, -2}, {-2, -1}, {-1, -3}, {1, -3}}) {
            at(s, cx, y, cz, a, p[0], p[1], "minecraft:bookshelf");
            at(s, cx, y + 1, cz, a, p[0], p[1], "minecraft:bookshelf");
        }
        at(s, cx, y, cz, a, 2, 2, Desert.BASE_POST);
        at(s, cx, y + 1, cz, a, 2, 2, Kit.slab(Desert.BRASS_SLAB));
        Desert.rug(s, cx - 1, cz + 1, cx + 1, cz + 2, y);
        Desert.lamp(s, cx, y + 3, cz - 1);
        Desert.lamp(s, cx, y + 3, cz + 2);
    }

    // ================================================================ shared parts

    /**
     * The walls of a block from (x1, z1) to (x2, z2) up to layer {@code top}, its lowest course of rough stone.
     */
    private static void block(Structure s, int x1, int z1, int x2, int z2, int top) {
        s.walls(x1, 1, z1, x2, top, z2, WALL);
        s.walls(x1, 1, z1, x2, 1, z2, BASE);
    }

    /**
     * A drum with a dome on it over a flat roof whose deck is at layer {@code deck}, the deck opened under it so that the
     * hall below rises into the dome.
     *
     * @return the layer above the finial
     */
    private static int domeOn(Structure s, int cx, int deck, int cz, int d, int drum, Desert.DomeKind kind) {
        int spring = Desert.drum(s, cx, deck + 1, cz, d, drum, GLASS);
        int r = (d - 1) / 2 - 1;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Desert.inPlan(dx, dz, r, Desert.ROUND)) {
                    s.set(cx + dx, deck, cz + dz, AIR);
                }
            }
        }
        return Desert.dome(s, cx, spring, cz, d, kind);
    }

    /**
     * A portal (pishtaq) from x1 to x2 standing on the rows z1 to z2 (its face on z2, looking south), rising to layer
     * {@code top}: a plinth of rough stone, an arch {@code width} wide with jambs {@code jambs} high in a frame cut
     * {@code depth} blocks into it (the iwan), a field of glazed tiles over the frame between bands of blue, a band of trim,
     * pointed merlons on its face and a coping behind them, pinnacles with lanterns on its corners. The gate behind the arch
     * is the caller's.
     *
     * @return the layer above the frame of the arch
     */
    private static int portal(Structure s, int x1, int x2, int z1, int z2, int top, int width, int jambs, Desert.Arch kind, int depth) {
        int cx = (x1 + x2) / 2;
        s.fill(x1, 1, z1, x2, top - 1, z2, WALL);
        s.fill(x1, 1, z1, x2, 1, z2, BASE);
        s.fill(x1, top, z1, x2, top, z2, TRIM);
        int above = Desert.arch(s, cx, 1, z2, "south", width, jambs, depth, kind, null, true);
        for (int y = above; y < top; y++) {
            for (int x = x1 + 1; x < x2; x++) {
                boolean border = y == above || y == top - 1;
                s.set(x, y, z2, Desert.glazed(border ? Desert.TILE_BLUE : (x + y) % 2 == 0 ? Desert.TILE : Desert.TILE_CYAN, "south"));
            }
        }
        Desert.crown(s, true, x1, x2, z2, top + 1, Desert.Merlons.POINTED);
        for (int z = z1; z < z2; z++) {
            Desert.crown(s, true, x1, x2, z, top + 1, Desert.Merlons.NONE);
        }
        for (int x : new int[]{x1, x2}) {
            for (int z = z1; z <= z2; z++) {
                s.set(x, top + 1, z, WALL);
            }
            s.set(x, top + 2, z2, CARVED).set(x, top + 3, z2, Kit.lantern());
        }
        return above;
    }

    /**
     * A wind tower ({@link Desert#windTower}) whose cap is covered all over with slabs of rough stone between its pinnacles,
     * so that nothing can stand on it in the dark.
     */
    private static void windTower(Structure s, int cx, int y, int cz, int width, int height) {
        int above = Desert.windTower(s, cx, y, cz, width, height);
        int h = (width - 1) / 2;
        for (int dz = -h; dz <= h; dz++) {
            for (int dx = -h; dx <= h; dx++) {
                if (!s.solid(cx + dx, above - 2, cz + dz)) {
                    s.set(cx + dx, above - 2, cz + dz, Kit.slab(Desert.BASE_SLAB));
                }
            }
        }
    }

    /**
     * A panel of glazed tiles in a frame of rough stone, flush with a wall whose face looks {@code facing}: along x from
     * {@code a1} to {@code a2} on the row {@code c} (along z on the column {@code c} for a face looking east or west), from
     * layer {@code y1} to {@code y2}.
     */
    private static void panel(Structure s, int a1, int y1, int c, int a2, int y2, String facing) {
        boolean alongX = facing.equals("north") || facing.equals("south");
        for (int a = a1; a <= a2; a++) {
            for (int y = y1; y <= y2; y++) {
                boolean frame = a == a1 || a == a2 || y == y1 || y == y2;
                String state = frame ? BASE : Desert.glazed((a + y) % 2 == 0 ? Desert.TILE : Desert.TILE_CYAN, facing);
                s.set(alongX ? a : c, y, alongX ? c : a, state);
            }
        }
    }

    /**
     * An octagonal basin of half-size {@code r} (five wide for 2) around (cx, cz) on the paving at layer {@code y}: a floor of
     * glazed tiles, a rim of slabs of trim, water inside, and in its middle a carved pedestal with a bowl of water on it.
     */
    private static void basin(Structure s, int cx, int y, int cz, int r) {
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (!Desert.inPlan(dx, dz, r, Desert.OCTAGON)) {
                    continue;
                }
                boolean edge = rim(dx, dz, r, Desert.OCTAGON);
                s.set(cx + dx, y, cz + dz, Desert.glazed(edge ? Desert.TILE_BLUE : Desert.TILE, "north"));
                s.set(cx + dx, y + 1, cz + dz, edge ? Kit.slab(Desert.TRIM_SLAB) : Desert.WATER);
            }
        }
        s.set(cx, y + 1, cz, CARVED).set(cx, y + 2, cz, Desert.WATER);
        s.set(cx + 1, y + 2, cz, Kit.upside(Desert.TRIM_STAIRS, "west")).set(cx - 1, y + 2, cz, Kit.upside(Desert.TRIM_STAIRS, "east"));
        s.set(cx, y + 2, cz + 1, Kit.upside(Desert.TRIM_STAIRS, "north")).set(cx, y + 2, cz - 1, Kit.upside(Desert.TRIM_STAIRS, "south"));
    }

    /**
     * Date palms in a court from (x1, z1) to (x2, z2), {@code height} high, standing on grass at the given spots: each is drawn
     * apart and only those of its blocks are kept that fall on air over the court, or on air over a roof whose deck is at
     * layer {@code high - 1} or above, so that no frond grows into a room or a wall.
     */
    private static void palms(Structure s, int x1, int z1, int x2, int z2, int high, int height, int[]... spots) {
        for (int[] p : spots) {
            Structure t = new Structure(s.sizeX, s.sizeY, s.sizeZ);
            Desert.palm(t, p[0], 1, p[1], height, null);
            for (int y = 1; y < s.sizeY; y++) {
                for (int z = 0; z < s.sizeZ; z++) {
                    for (int x = 0; x < s.sizeX; x++) {
                        if (!t.solid(x, y, z) || s.solid(x, y, z)) {
                            continue;
                        }
                        boolean court = x >= x1 && x <= x2 && z >= z1 && z <= z2;
                        int below = y - 1;
                        while (below > 0 && !s.solid(x, below, z)) {
                            below--;
                        }
                        if (court || below >= high - 1) {
                            s.set(x, y, z, t.get(x, y, z));
                        }
                    }
                }
            }
            s.set(p[0], 0, p[1], GRASS);
        }
    }

    /** A lamp on a post of rough stone standing on layer {@code y}. */
    private static void lampPost(Structure s, int x, int y, int z) {
        s.set(x, y, z, Desert.BASE_POST).set(x, y + 1, z, Kit.lantern());
    }

    /**
     * The counter of the registrar: the guild desk looking south at (x, y, z) between two counter tops with candles and a
     * lantern, his post two blocks behind the desk and the one chest of the guild beside the post on the side {@code side}
     * (1 east, -1 west).
     */
    private static void registrar(Structure s, int x, int y, int z, int side) {
        s.set(x - 1, y, z, Kit.topSlab(FLOOR_SLAB)).set(x - 1, y + 1, z, Furnish.candles(2));
        s.set(x, y, z, DESK);
        s.set(x + 1, y, z, Kit.topSlab(FLOOR_SLAB)).set(x + 1, y + 1, z, Kit.lantern());
        s.set(x, y, z - 1, AIR);
        s.set(x, y, z - 2, WORK_POST);
        s.set(x + side, y, z - 2, Kit.chest("south"));
    }

    /**
     * The contract board on a wall, looking {@code facing}: a board of timber {@code width} wide and four high under a crest
     * of trim with a carved middle, standing before the wall, a ledge before it with papers (white carpet) and candles,
     * notices of white and yellow cloth hanging on it.
     *
     * @param x the column of its middle, in the row before the wall
     */
    private static void contractBoard(Structure s, int x, int y, int z, String facing, int width) {
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        int half = (width - 1) / 2;
        for (int u = -half; u <= half; u++) {
            int px = x + a[0] * u;
            int pz = z + a[1] * u;
            s.fill(px, y, pz, px, y + 3, pz, Desert.PLANKS);
            s.set(px, y + 4, pz, u == 0 ? CARVED : Kit.slab(Desert.TRIM_SLAB));
            int fx = px + f[0];
            int fz = pz + f[1];
            s.set(fx, y, fz, Kit.topSlab(FLOOR_SLAB));
            s.set(fx, y + 1, fz, u == 0 ? Furnish.candles(3) : "minecraft:white_carpet");
            s.set(fx, y + 3, fz, Kit.wallBanner(Math.floorMod(u, 2) == 1 ? "yellow" : "white", facing));
        }
    }

    /**
     * Trophies along a wall that looks {@code facing}, {@code count} cells from (x, z) toward the left of {@code facing}:
     * racks of weapons (grindstones) with the skulls of beasts hung over them and between them pikes carrying skulls, the
     * banner of the guild over the middle.
     */
    private static void trophies(Structure s, int x, int y, int z, String facing, int count) {
        int[] a = Furnish.step(Furnish.left(facing));
        for (int u = 0; u < count; u++) {
            int px = x + a[0] * u;
            int pz = z + a[1] * u;
            if (u % 2 == 0) {
                s.set(px, y, pz, "minecraft:grindstone[face=floor,facing=" + facing + "]");
                s.set(px, y + 2, pz, (u % 4 == 0 ? "minecraft:skeleton_wall_skull" : "minecraft:wither_skeleton_wall_skull")
                        + "[facing=" + facing + "]");
            } else {
                s.set(px, y, pz, FENCE).set(px, y + 1, pz, "minecraft:skeleton_skull[rotation=" + rotation(facing) + "]");
            }
        }
        int mid = (count - 1) / 2;
        s.set(x + a[0] * mid, y + 3, z + a[1] * mid, Kit.wallBanner(GUILD, facing));
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
     * A map of the lands of the guild painted on a wall that runs along x: sand, rock, oases and the sea in terracotta
     * {@code width} wide (up to five) and two high, from (x, y, z) toward the east, its upper row at {@code y + 1}.
     */
    private static void guildMap(Structure s, int x, int y, int z, int width) {
        String[] map = {"ysgyc", "boyrb"};
        for (int row = 0; row < map.length; row++) {
            for (int i = 0; i < Math.min(5, width); i++) {
                String color = switch (map[row].charAt(i)) {
                    case 'y' -> "yellow";
                    case 's' -> "white";
                    case 'g' -> "lime";
                    case 'b' -> "light_blue";
                    case 'c' -> "cyan";
                    case 'r' -> "red";
                    default -> "orange";
                };
                s.set(x + i, y + 1 - row, z, "minecraft:" + color + "_terracotta");
            }
        }
    }

    /**
     * The map table: the cartography table with stools of the timber at its sides and before it.
     */
    private static void mapTable(Structure s, int x, int y, int z) {
        s.set(x, y, z, CARTOGRAPHY);
        s.set(x - 1, y, z, Kit.stairs(FLOOR_STAIRS, "west")).set(x + 1, y, z, Kit.stairs(FLOOR_STAIRS, "east"));
        s.set(x, y, z + 1, Kit.stairs(FLOOR_STAIRS, "south"));
    }

    /** A lectern without a book looking the given way. */
    private static String lectern(String facing) {
        return "minecraft:lectern[facing=" + facing + ",has_book=false,powered=false]";
    }

    /**
     * A great lamp hanging over (x, z) from the first solid block over layer {@code low} (the inside of a dome): a chain down
     * to a ring of lanterns at layer {@code low}.
     */
    private static void hangChandelier(Structure s, int x, int z, int low) {
        int top = low;
        while (top + 1 < s.sizeY && !s.solid(x, top + 1, z)) {
            top++;
        }
        Furnish.greatChandelier(s, x, z, top, top - low);
    }

    /**
     * A flight of {@code n} stairs of wall stone climbing one layer for every block toward {@code dir} from (x, y, z): solid
     * under every step, the space above each step cleared for the head (the floor above is cut away where it must be).
     */
    private static void flight(Structure s, int x, int y, int z, String dir, int n) {
        int[] d = Furnish.step(dir);
        for (int k = 0; k < n; k++) {
            int px = x + d[0] * k;
            int pz = z + d[1] * k;
            int py = y + k;
            for (int yy = y; yy < py; yy++) {
                s.set(px, yy, pz, WALL);
            }
            s.set(px, py, pz, Kit.stairs(Desert.WALL_STAIRS, dir));
            for (int yy = py + 1; yy <= py + 2 && yy < s.sizeY; yy++) {
                s.set(px, yy, pz, AIR);
            }
        }
    }

    /**
     * A floor of a storey inside an eight-sided shaft of half-size {@code r}: the cells inside its wall.
     */
    private static void floorIn(Structure s, int cx, int y, int cz, int r, String state) {
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Desert.inPlan(dx, dz, r, Desert.OCTAGON) && !rim(dx, dz, r, Desert.OCTAGON)) {
                    s.set(cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /** Whether a column lies on the rim of a plan of {@link Desert}: inside it with a neighbour outside. */
    private static boolean rim(int dx, int dz, int r, int plan) {
        return Desert.inPlan(dx, dz, r, plan) && (!Desert.inPlan(dx + 1, dz, r, plan) || !Desert.inPlan(dx - 1, dz, r, plan)
                || !Desert.inPlan(dx, dz + 1, r, plan) || !Desert.inPlan(dx, dz - 1, r, plan));
    }
}
