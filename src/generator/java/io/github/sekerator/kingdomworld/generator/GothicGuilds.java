package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The two guilds of a capital in the style "gothic", each a program drawn for the rank of its kingdom, from B for a weak
 * one to SS for a strong one.
 *
 * <p>The adventurers' guild is a hall of the order: dark walls pierced by tall pointed windows between buttresses, a very
 * steep roof between stone gables with copings and pinnacles, a doorway under a wimperg and a rose in the gable, and a
 * slim tower ending in a needle with lucarnes and an iron cross. The rank grows it: B a hall with its loft and a square
 * tower at its back corner; A a larger hall with dormers, an eight-sided tower and a yard behind an iron railing; S a long
 * hall under a ribbed vault with its side to the street and a crowned belfry in the middle of its front over a forecourt;
 * SS the great hall of the order, a nave with aisles under flying buttresses between two towers of the front, the greater
 * one crowned and needled over the capital. Inside every rank: the great hall with long tables and benches, a
 * chimneypiece, the contract board, trophies, and the counter of the registrar with the guild desk, his post and the chest
 * of the guild; upstairs the map room with its lectern and, from A, the chamber of the master with a bed of state.
 *
 * <p>The mages' guild lives in its tower, a dark tower of the arcane: slim and square, windows of purple glass on every
 * storey, piers standing free around it that fly their arches to its walls, a crown of a pierced parapet with great
 * pinnacles that end in glowing rods and gargoyles at its corners, and a needle spire with lucarnes over the laboratory.
 * B is one tower of three storeys; A a broader one; S adds a storey and a library wing with its gable to the street and a
 * paved court; SS is a cluster of spired towers, the great tower with its observatory behind a library nave flanked by two
 * slim towers. On the ground floor the magic focus with the post of the mage and its chest beside it; a library, a brewing
 * room, a research room or laboratory, and real beds for the mages: one in B, two in A, three in S, four in SS.
 *
 * <p>Drawn with the shapes of {@link Gothic}; coordinates as everywhere: x from west to east, z from the back to the front
 * (the entrance looks south), y up; layer 0 stands on the ground and is the street.
 */
final class GothicGuilds {
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
    private static final String AMETHYST = "minecraft:amethyst_block";
    private static final String CLUSTER = "minecraft:amethyst_cluster[facing=up,waterlogged=false]";
    private static final String END_ROD = "minecraft:end_rod[facing=up]";
    private static final String LECTERN = "minecraft:lectern[facing=south,has_book=false,powered=false]";
    private static final String PURPLE_CARPET = "minecraft:purple_carpet";
    /** The guild's own colour on its banners, the same in every kingdom. */
    private static final String GUILD = "black";

    /**
     * A drawn building with its named places: the door, and the guard by the desk (none for a mage tower).
     */
    private record Drawn(Structure structure, int[] door, int[] guard) {
    }

    private GothicGuilds() {
    }

    /**
     * The adventurers' guilds gothic_guild_b, _a, _s and _ss (trade, type guild, with their door and the guard by the desk)
     * and the mage towers gothic_mage_tower_b, _a, _s and _ss (production, role mage_tower, with their door), all standing
     * on the ground with their anchor in the middle of their front edge.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Rank rank : Rank.values()) {
            String key = rank.name().toLowerCase(Locale.ROOT);
            Drawn guild = guild(rank);
            Structure g = guild.structure();
            list.add(new BuildingGenerator.Building("gothic_guild_" + key, "trade", "guild", "kingdomworld:guild_desk",
                    (g.sizeX - 1) / 2, 0, g.sizeZ - 1, g, "none", 1, "")
                    .at("door", guild.door()[0], guild.door()[1], guild.door()[2])
                    .at("guard", guild.guard()[0], guild.guard()[1], guild.guard()[2]));
        }
        for (Rank rank : Rank.values()) {
            String key = rank.name().toLowerCase(Locale.ROOT);
            Drawn tower = mageTower(rank);
            Structure t = tower.structure();
            list.add(new BuildingGenerator.Building("gothic_mage_tower_" + key, "production", "mage_tower", "kingdomworld:magic_focus",
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
     * Rank B: a hall of the order with its steep gable to the street, a grand doorway under a wimperg and a rose window in
     * the gable, buttresses along its sides and dormers on its roof; at its back corner a slim tower rises high over the
     * ridge, a slender needle with its lucarnes on a cornice over its walls, and an iron railing with lamps closes the yard
     * beside the hall. Inside, the great hall under a ceiling of beams: the counter of the registrar at the back with his
     * post, the chest and the shelves of the guild, the long table with benches before the chimneypiece, the contract board
     * and the trophies; over it the loft under the roof is the map room, lit by the rose; the tower holds the archive, a
     * room of arms and the bell of the guild.
     */
    private static Drawn guildB() {
        Structure s = new Structure(15, 36, 15);
        int x1 = 1;
        int x2 = 9;
        int z1 = 1;
        int z2 = 12;
        int eaves = 8;
        int loft = 5;
        int cx = 5;
        walls(s, x1, z1, x2, z2, eaves - 1);
        Gothic.paving(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, 0);
        s.fill(x1 + 1, loft, z1 + 1, x2 - 1, loft, z2 - 1, Gothic.PLANKS);
        for (int z : new int[]{3, 6, 9}) {
            s.fill(x1 + 1, loft, z, x2 - 1, loft, z, Kit.log(Gothic.BEAM, "x"));
        }
        int ridge = Gothic.steepRoof(s, x1, z1, x2, z2, eaves, false, 2, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.cresting(s, cx, z1 + 1, cx, z2 - 1, ridge);
        Gothic.dormer(s, x1, z1, x2, z2, eaves, false, 2, 8, false, 2);
        Gothic.dormer(s, x1, z1, x2, z2, eaves, false, 2, 9, true, 2);

        // the tower at the back corner, its west wall the east wall of the hall
        int tx = 11;
        int tz = 3;
        int top = 22;
        walls(s, tx - 2, tz - 2, tx + 2, tz + 2, top - 1);
        clear(s, tx - 1, 1, tz - 1, tx + 1, top - 1, tz + 1);
        for (int y : new int[]{0, loft, 11, 17}) {
            s.fill(tx - 1, y, tz - 1, tx + 1, y, tz + 1, Gothic.PLANKS);
        }
        s.fill(tx - 1, top, tz - 1, tx + 1, top, tz + 1, Gothic.WALL);

        // the front: a grand doorway, the rose in the gable
        Gothic.portal(s, cx, 1, z2, "south", 1, true);
        banners(s, cx - 3, cx + 3, 6, z2 + 1, "south");
        s.set(cx, 0, z2 + 1, Kit.stairs(Gothic.TRACERY_STAIRS, "north"));
        Gothic.rose(s, cx, 12, z2, "south", 5);
        // the sides and the back
        Gothic.lancet(s, x1, 2, 3, "west", 1, 2, Gothic.AZURE, Gothic.CRIMSON);
        Gothic.lancet(s, x1, 2, 5, "west", 1, 2, Gothic.AZURE, Gothic.CRIMSON);
        put(s, x1 - 1, 4, 4, Gothic.TRACERY);
        Gothic.lancet(s, x1, 2, 11, "west", 1, 2, Gothic.AZURE, Gothic.CRIMSON);
        Gothic.lancet(s, x2, 2, 6, "east", 1, 2, Gothic.AZURE, Gothic.CRIMSON);
        Gothic.lancet(s, x2, 2, 10, "east", 1, 2, Gothic.AZURE, Gothic.CRIMSON);
        Gothic.lancet(s, cx, 9, z1, "north", 3, 2, Gothic.VIOLET, Gothic.CRIMSON);
        // the tower
        Gothic.lancet(s, tx, 2, tz + 2, "south", 1, 2, Gothic.AZURE, Gothic.CRIMSON);
        Gothic.lancet(s, tx, 12, tz + 2, "south", 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
        Gothic.lancet(s, tx, 18, tz + 2, "south", 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
        for (String side : new String[]{"east", "north"}) {
            int[] d = Furnish.step(side);
            int x = tx + 2 * d[0];
            int z = tz + 2 * d[1];
            Gothic.lancet(s, x, 7, z, side, 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x, 13, z, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x, 18, z, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
        }
        Gothic.lancet(s, tx - 2, 18, tz, "west", 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
        for (int z : new int[]{z1, 7}) {
            Gothic.buttress(s, x1, 0, z, "west", 7, 1, false);
        }
        Gothic.buttress(s, x2, 0, 8, "east", 7, 1, false);
        Gothic.buttress(s, x1, 0, z1, "north", 7, 1, false);
        Gothic.buttress(s, x1, 0, z2, "south", 7, 1, false);
        Gothic.buttress(s, x2, 0, z2, "south", 7, 1, false);
        Gothic.buttress(s, x2, 0, z2, "east", 7, 1, false);
        // the top of the tower: a cornice, gargoyles, pinnacles on the corners, the needle
        towerTop(s, tx, tz, 2, top);
        spire(s, tx, top, tz, 2, 13, true);
        // the yard beside the hall behind its railing
        Gothic.ironFence(s, x2 + 2, 14, 14, 14, 0, 2, true);
        Gothic.ironFence(s, 14, 7, 14, 13, 0, 2, true);

        // the great hall
        counter(s, cx, 1, 5, 3, 8, 1);
        Furnish.books(s, 2, 2, 6, 2, 1, 3);
        ladder(s, 8, 2, 1, loft + 1, "west");
        Gothic.fireplace(s, x2, 1, 8, "west", loft - 1);
        Furnish.longTable(s, cx, 7, cx, 9, 1, Gothic.WOOD);
        Furnish.bench(s, cx - 1, 7, cx - 1, 9, 1, "east", Gothic.WOOD);
        Furnish.bench(s, cx + 1, 7, cx + 1, 9, 1, "west", Gothic.WOOD);
        s.set(cx, 2, 7, Furnish.candles(3)).set(cx, 2, 9, Furnish.candles(2));
        contractBoard(s, x1 + 1, 1, 9, "east", 3);
        trophies(s, 7, 1, z2 - 1, "north", 2);
        Gothic.candelabrum(s, x1 + 1, 1, z2 - 1);
        lamp(s, cx, loft - 1, 3);
        lamp(s, cx, loft - 1, 8);
        lamp(s, 3, loft - 1, 11);
        lamp(s, 7, loft - 1, 11);
        s.door(x2, 1, 4, Gothic.DOOR, "east", "left");
        put(s, cx - 1, 3, z2 - 1, Kit.wallBanner(GUILD, "north"));

        // the loft: the map room
        int f = loft + 1;
        guildMap(s, 3, f, z1 + 1, 5);
        mapTable(s, cx, f, 4);
        s.set(3, f, 4, LECTERN);
        Gothic.bookcase(s, x1 + 1, f, 8, "east", 2);
        Furnish.longTable(s, 7, 7, 7, 9, f, Gothic.WOOD);
        Furnish.bench(s, 6, 7, 6, 9, f, "east", Gothic.WOOD);
        s.set(7, f + 1, 8, Furnish.candles(3));
        s.door(x2, f, 4, Gothic.DOOR, "east", "left");
        lamp(s, cx, 10, 4);
        lamp(s, cx, 10, 9);
        put(s, 4, f + 1, z2 - 1, Kit.wallBanner(GUILD, "north"));
        put(s, 6, f + 1, z2 - 1, Kit.wallBanner(GUILD, "north"));

        // the tower: the stair hall, the archive, the room of arms, the bell
        ladder(s, tx + 1, tz - 1, 1, 18, "west");
        Furnish.books(s, tx - 1, tz - 1, tx, tz - 1, f, f + 1);
        Furnish.crate(s, tx + 1, f, tz + 1, Gothic.WOOD);
        Furnish.weaponRack(s, tx - 1, 12, tz - 1, "south", Gothic.WOOD);
        s.set(tx, 12, tz - 1, Gothic.WOOD_FENCE).set(tx, 13, tz - 1, "minecraft:skeleton_skull[rotation=0]");
        s.set(tx, top - 1, tz, "minecraft:bell[attachment=ceiling,facing=north,powered=false]");
        lamp(s, tx, loft - 1, tz + 1);
        lamp(s, tx - 1, 10, tz + 1);
        lamp(s, tx - 1, 16, tz + 1);
        s.set(tx - 1, 18, tz + 1, Kit.lantern());
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{cx, 1, z2 - 1}, new int[]{cx + 1, 1, 6});
    }

    /**
     * Rank A: a larger hall of the order, its gable to the street with a doorway of two leaves under a wimperg and a great
     * rose over it, tall lancets between the buttresses of its sides, dormers on its roof and an iron cresting on its ridge;
     * at its back corner an eight-sided tower with lancets on its faces climbs far over the roof to a cornice with pinnacles
     * and a slender needle with lucarnes; beside the hall a paved yard of the guild behind an iron railing with lamps.
     * Inside, the great hall: the counter of the registrar before the shelves of the guild, a long table with benches, the
     * chimneypiece, the contract board and trophies; in the loft under the roof the map room and the chamber of the master
     * of the guild with his bed of state; the tower holds the archive, a room of arms and the bell.
     */
    private static Drawn guildA() {
        Structure s = new Structure(19, 42, 19);
        int x1 = 1;
        int x2 = 11;
        int z1 = 3;
        int z2 = 16;
        int eaves = 10;
        int loft = 6;
        int cx = 6;
        walls(s, x1, z1, x2, z2, eaves - 1);
        Gothic.paving(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, 0);
        s.fill(x1 + 1, loft, z1 + 1, x2 - 1, loft, z2 - 1, Gothic.PLANKS);
        for (int z = z1 + 2; z < z2; z += 3) {
            s.fill(x1 + 1, loft, z, x2 - 1, loft, z, Kit.log(Gothic.BEAM, "x"));
        }
        int ridge = Gothic.steepRoof(s, x1, z1, x2, z2, eaves, false, 2, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.cresting(s, cx, z1 + 1, cx, z2 - 1, ridge);
        for (int z : new int[]{8, 13}) {
            Gothic.dormer(s, x1, z1, x2, z2, eaves, false, 2, z, false, 2);
            Gothic.dormer(s, x1, z1, x2, z2, eaves, false, 2, z, true, 2);
        }

        // the eight-sided tower at the back corner
        int tx = 14;
        int tz = 4;
        int tr = 3;
        int top = 27;
        Gothic.octagonWalls(s, tx, 0, top - 1, tz, tr, Gothic.WALL);
        Gothic.octagonWalls(s, tx, 0, 0, tz, tr, Gothic.ROUGH);
        for (int y = 1; y < top; y++) {
            octagonFloor(s, tx, y, tz, tr - 1, AIR);
        }
        for (int y : new int[]{0, loft, 13, 20}) {
            octagonFloor(s, tx, y, tz, tr - 1, Gothic.PLANKS);
        }
        octagonFloor(s, tx, top, tz, tr - 1, Gothic.WALL);

        // the front: a doorway of two leaves, the rose in the gable
        Gothic.portal(s, cx, 1, z2, "south", 3, true);
        banners(s, cx - 4, cx + 4, 6, z2 + 1, "south");
        for (int x = cx - 1; x <= cx + 1; x++) {
            s.set(x, 0, z2 + 1, Kit.stairs(Gothic.TRACERY_STAIRS, "north"));
        }
        Gothic.rose(s, cx, 15, z2, "south", 7);
        for (int x : new int[]{2, 10}) {
            Gothic.blindLancet(s, x, 11, z2, "south", 1, 1);
        }
        // the sides and the back
        for (int z : new int[]{5, 9, 13}) {
            Gothic.lancet(s, x1, 2, z, "west", 1, 3, Gothic.AZURE, Gothic.CRIMSON);
            if (z > 5) {
                Gothic.lancet(s, x2, 2, z, "east", 1, 3, Gothic.AZURE, Gothic.CRIMSON);
            }
        }
        Gothic.lancet(s, cx, 11, z1, "north", 3, 3, Gothic.VIOLET, Gothic.CRIMSON);
        for (int z : new int[]{z1, 7, 11, 15}) {
            Gothic.buttress(s, x1, 0, z, "west", 9, 2, false);
        }
        for (int z : new int[]{11, 15}) {
            Gothic.buttress(s, x2, 0, z, "east", 9, 2, false);
        }
        Gothic.buttress(s, x1, 0, z1, "north", 9, 2, false);
        Gothic.buttress(s, cx, 0, z1, "north", 9, 1, false);
        for (int x : new int[]{x1, x2}) {
            Gothic.buttress(s, x, 0, z2, "south", 9, 1, false);
        }
        // the tower: lancets on its free faces, a cornice, pinnacles on its diagonals, the needle
        for (String side : new String[]{"south", "east", "north"}) {
            int[] d = Furnish.step(side);
            int x = tx + tr * d[0];
            int z = tz + tr * d[1];
            if (!side.equals("south")) {
                Gothic.lancet(s, x, 2, z, side, 1, 2, Gothic.AZURE, Gothic.CRIMSON);
            }
            Gothic.lancet(s, x, 14, z, side, 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x, 21, z, side, 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
        }
        Gothic.lancet(s, tx, 8, tz + tr, "south", 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
        Gothic.lancet(s, tx + tr, 8, tz, "east", 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
        Gothic.lancet(s, tx - tr, 21, tz, "west", 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
        octagonCornice(s, tx, top - 1, tz, tr);
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                Gothic.pinnacle(s, tx + dx, top, tz + dz, 4);
            }
        }
        spire(s, tx, top, tz, 2, 14, true);
        // the yard of the guild beside the hall
        for (int x = x2 + 1; x <= 17; x++) {
            for (int z = 9; z <= 17; z++) {
                if (!s.solid(x, 0, z)) {
                    s.set(x, 0, z, Kit.slab(Gothic.TRACERY_SLAB));
                }
            }
        }
        Gothic.ironFence(s, x2 + 2, 18, 18, 18, 0, 2, true);
        Gothic.ironFence(s, 18, 9, 18, 17, 0, 2, true);
        lampPost(s, 15, 0, 13);

        // the great hall
        counter(s, cx, 1, 7, 4, 8, 1);
        Furnish.books(s, 2, 4, 9, 4, 1, 3);
        ladder(s, 10, 4, 1, loft + 1, "west");
        s.door(x2, 1, 5, Gothic.DOOR, "east", "left");
        Gothic.fireplace(s, x1, 1, 11, "east", loft - 1);
        Furnish.longTable(s, cx, 10, cx, 13, 1, Gothic.WOOD);
        Furnish.bench(s, cx - 1, 10, cx - 1, 13, 1, "east", Gothic.WOOD);
        Furnish.bench(s, cx + 1, 10, cx + 1, 13, 1, "west", Gothic.WOOD);
        s.set(cx, 2, 10, Furnish.candles(3)).set(cx, 2, 12, Furnish.candles(2)).set(cx, 2, 13, Furnish.candles(3));
        contractBoard(s, x2 - 1, 1, 11, "west", 3);
        trophies(s, 4, 1, z2 - 1, "north", 3);
        trophies(s, 10, 1, z2 - 1, "north", 2);
        Gothic.candelabrum(s, 2, 1, 8);
        Gothic.candelabrum(s, 10, 1, 8);
        for (int z : new int[]{5, 9, 12, 14}) {
            lamp(s, cx, loft - 1, z);
        }
        lamp(s, 3, loft - 1, 13);
        lamp(s, 9, loft - 1, 14);

        // the loft: the map room at the back, the chamber of the master at the front
        int f = loft + 1;
        guildMap(s, 3, f, z1 + 1, 7);
        mapTable(s, cx, f, 6);
        s.set(4, f, 6, LECTERN);
        Gothic.bookcase(s, x1 + 1, f, 9, "east", 2);
        Furnish.longTable(s, 8, 8, 8, 10, f, Gothic.WOOD);
        Furnish.bench(s, 7, 8, 7, 10, f, "east", Gothic.WOOD);
        s.set(8, f + 1, 9, Furnish.candles(3));
        s.door(x2, f, 5, Gothic.DOOR, "east", "left");
        Gothic.stateBed(s, 3, f, 13, "south");
        Furnish.rug(s, 5, 12, 8, 14, f, Gothic.CLOTH_CARPET);
        Furnish.desk(s, 9, f, 14, "north", Gothic.WOOD);
        Gothic.candelabrum(s, 9, f, 12);
        lamp(s, cx, 12, 6);
        lamp(s, cx, 12, 10);
        lamp(s, cx, 12, 14);

        // the tower: the stair hall, the archive, the room of arms, the bell
        ladder(s, tx + 2, tz, 1, 21, "west");
        Furnish.books(s, tx - 1, tz - 2, tx + 1, tz - 2, f, f + 1);
        Furnish.crate(s, tx, f, tz + 2, Gothic.WOOD);
        Furnish.weaponRack(s, tx - 1, 14, tz - 2, "south", Gothic.WOOD);
        Furnish.weaponRack(s, tx + 1, 14, tz - 2, "south", Gothic.WOOD);
        s.set(tx, 14, tz + 2, Gothic.WOOD_FENCE).set(tx, 15, tz + 2, "minecraft:skeleton_skull[rotation=8]");
        s.set(tx, top - 1, tz, "minecraft:bell[attachment=ceiling,facing=north,powered=false]");
        lamp(s, tx, loft - 1, tz);
        lamp(s, tx, 12, tz);
        lamp(s, tx, 19, tz);
        s.set(tx - 1, 21, tz + 1, Kit.lantern());
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{cx, 1, z2 - 1}, new int[]{cx + 2, 1, 8});
    }

    /**
     * Rank S: a long hall of the order with its side to the street, stone gables with roses at both ends, tall lancets
     * between pinnacled buttresses, dormers and an iron cresting on its roof; from the middle of its front rises the belfry
     * of the guild, a square tower buttressed at its corners with a doorway of two leaves under a wimperg, a rose over it and
     * a crown of a pierced parapet, great pinnacles and gargoyles round a needle spire with lucarnes; before it a paved
     * forecourt behind an iron railing with lamps. Inside, the great hall under a ribbed vault with coronas of candles: the
     * counter of the registrar at the back before the shelves of the guild, two long tables with benches, two chimneypieces
     * and trophies; the tall hall under the belfry with the contract board; under the roof the map room with the great map
     * of the guild on a table and the chamber of the master with his bed of state; in the tower the archive, the armoury and
     * the bell.
     */
    private static Drawn guildS() {
        Structure s = new Structure(23, 47, 25);
        int x1 = 1;
        int x2 = 21;
        int z1 = 3;
        int z2 = 13;
        int eaves = 12;
        int cz = 8;
        walls(s, x1, z1, x2, z2, eaves - 1);
        Gothic.paving(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, 0);
        Gothic.vault(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, 7, true, 4, 11);
        s.fill(x1 + 1, 12, z1 + 1, x2 - 1, 12, z2 - 1, Gothic.PLANKS);
        int ridge = Gothic.steepRoof(s, x1, z1, x2, z2, eaves, true, 2, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.cresting(s, x1 + 1, cz, x2 - 1, cz, ridge);
        for (int x : new int[]{4, 18}) {
            Gothic.dormer(s, x1, z1, x2, z2, eaves, true, 2, x, true, 2);
        }
        for (int x : new int[]{6, 11, 16}) {
            Gothic.dormer(s, x1, z1, x2, z2, eaves, true, 2, x, false, 2);
        }

        // the belfry in the middle of the front
        int tx = 11;
        int tz = 17;
        int top = 29;
        walls(s, tx - 4, tz - 4, tx + 4, tz + 4, top - 1);
        clear(s, tx - 3, 1, tz - 3, tx + 3, top - 1, tz + 3);
        Gothic.paving(s, tx - 3, tz - 3, tx + 3, tz + 3, 0);
        for (int y : new int[]{12, 19, 24}) {
            s.fill(tx - 3, y, tz - 3, tx + 3, y, tz + 3, Gothic.PLANKS);
        }
        s.fill(tx - 3, top, tz - 3, tx + 3, top, tz + 3, Gothic.WALL);
        Gothic.pointedArch(s, tx, 1, z2, "south", 3, 5, 1, Gothic.TRIM_STAIRS, Gothic.TRIM_SLAB);

        // the front of the belfry: the doorway, the rose, the windows of the belfry
        Gothic.portal(s, tx, 1, tz + 4, "south", 3, true);
        banners(s, tx - 3, tx + 3, 10, tz + 5, "south");
        for (int x = tx - 1; x <= tx + 1; x++) {
            s.set(x, 0, tz + 5, Kit.stairs(Gothic.TRACERY_STAIRS, "north"));
        }
        Gothic.rose(s, tx, 15, tz + 4, "south", 5);
        Gothic.lancet(s, tx, 20, tz + 4, "south", 3, 2, Gothic.VIOLET, Gothic.CRIMSON);
        Gothic.lancet(s, tx, 25, tz + 4, "south", 3, 1, Gothic.VIOLET, Gothic.CRIMSON);
        for (int side = -1; side <= 1; side += 2) {
            int x = tx + 4 * side;
            String facing = side < 0 ? "west" : "east";
            Gothic.lancet(s, x, 7, tz, facing, 1, 3, Gothic.AZURE, Gothic.CRIMSON);
            Gothic.lancet(s, x, 14, tz, facing, 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x, 20, tz, facing, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x, 25, tz, facing, 1, 1, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.buttress(s, x, 0, tz + 4, "south", 18, 2, true);
            Gothic.buttress(s, x, 0, tz + 4, facing, 18, 2, true);
        }
        Gothic.crown(s, tx - 4, tz - 4, tx + 4, tz + 4, top, true);
        spire(s, tx, top, tz, 3, 18, true);
        walkLamps(s, tx, tz, 3, top + 1);

        // the hall: the front beside the belfry, the back, the gables
        for (int x : new int[]{4, 18}) {
            Gothic.lancet(s, x, 2, z2, "south", 3, 5, Gothic.AZURE, Gothic.CRIMSON);
        }
        for (int x : new int[]{4, 8, 14, 18}) {
            Gothic.lancet(s, x, 2, z1, "north", 1, 5, Gothic.AZURE, Gothic.CRIMSON);
        }
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? x1 : x2;
            String facing = side == 0 ? "west" : "east";
            for (int z : new int[]{5, 11}) {
                Gothic.lancet(s, x, 2, z, facing, 1, 5, Gothic.AZURE, Gothic.CRIMSON);
            }
            Gothic.rose(s, x, 17, cz, facing, 5);
            Gothic.lancet(s, x, 13, cz, facing, 1, 1, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.buttress(s, x, 0, z1, facing, 10, 2, false);
            Gothic.buttress(s, x, 0, z2, facing, 10, 2, false);
            Gothic.buttress(s, x, 0, z2, "south", 10, 2, false);
        }
        for (int x : new int[]{x1, 6, 11, 16, x2}) {
            Gothic.buttress(s, x, 0, z1, "north", 8, 2, true);
        }

        // the forecourt
        for (int x = 0; x < s.sizeX; x++) {
            for (int z = z2 + 1; z < s.sizeZ; z++) {
                if (!s.solid(x, 0, z)) {
                    s.set(x, 0, z, Kit.slab(Gothic.TRACERY_SLAB));
                }
            }
        }
        Gothic.ironFence(s, 0, 24, 8, 24, 0, 2, true);
        Gothic.ironFence(s, 14, 24, 22, 24, 0, 2, true);
        lampPost(s, 3, 0, 18);
        lampPost(s, 19, 0, 18);

        // the great hall
        counter(s, tx, 1, 7, 9, 13, 1);
        Furnish.books(s, 9, 4, 13, 4, 1, 3);
        Gothic.fireplace(s, x1, 1, cz, "east", 6);
        Gothic.fireplace(s, x2, 1, cz, "west", 6);
        for (int xa : new int[]{3, 15}) {
            Furnish.longTable(s, xa, 10, xa + 4, 10, 1, Gothic.WOOD);
            Furnish.bench(s, xa, 9, xa + 4, 9, 1, "south", Gothic.WOOD);
            Furnish.bench(s, xa, 11, xa + 4, 11, 1, "north", Gothic.WOOD);
            s.set(xa, 2, 10, Furnish.candles(3)).set(xa + 2, 2, 10, Furnish.candles(2)).set(xa + 4, 2, 10, Furnish.candles(3));
        }
        trophies(s, 5, 1, z1 + 1, "south", 3);
        trophies(s, 15, 1, z1 + 1, "south", 3);
        for (int x : new int[]{5, 11, 17}) {
            Gothic.corona(s, x, cz, 10, 4);
        }
        Gothic.candelabrum(s, 2, 1, 5);
        Gothic.candelabrum(s, 20, 1, 5);
        Gothic.candelabrum(s, 2, 1, 11);
        Gothic.candelabrum(s, 20, 1, 11);
        for (int x : new int[]{8, 14}) {
            Gothic.candelabrum(s, x, 1, 12);
        }

        // the hall under the belfry: the contract board, benches, banners
        contractBoard(s, tx - 3, 1, tz, "east", 3);
        Gothic.pew(s, tx + 3, tz - 1, tx + 3, tz + 1, 1, "west");
        for (int z : new int[]{tz - 2, tz + 2}) {
            put(s, tx + 3, 5, z, Kit.wallBanner(GUILD, "west"));
        }
        Gothic.corona(s, tx, tz, 11, 5);
        Gothic.candelabrum(s, tx - 3, 1, tz + 3);
        Gothic.candelabrum(s, tx + 3, 1, tz + 3);

        // under the roof: the map room and the chamber of the master
        int f = 13;
        guildMapTable(s, 5, f, 7, 5, 2);
        for (int x = 5; x <= 9; x++) {
            s.set(x, f, 6, Kit.stairs(Gothic.WOOD_STAIRS, "north"));
        }
        s.set(3, f, 8, "minecraft:lectern[facing=east,has_book=false,powered=false]");
        s.set(7, f, 10, CARTOGRAPHY);
        Furnish.books(s, 3, 5, 9, 5, f, f + 1);
        Gothic.stateBed(s, 17, f, cz, "east");
        Furnish.rug(s, 14, 7, 16, 9, f, Gothic.CLOTH_CARPET);
        Furnish.desk(s, 15, f, 11, "north", Gothic.WOOD);
        Furnish.books(s, 13, 5, 18, 5, f, f + 1);
        Gothic.candelabrum(s, 13, f, 10);
        s.door(tx, f, z2, Gothic.DOOR, "north", "left");
        for (int x : new int[]{4, 8, 11, 14, 18}) {
            lamp(s, x, f + 3, cz);
        }

        // the tower: the archive, the armoury, the bell
        ladder(s, tx + 3, tz - 3, 1, 25, "west");
        Furnish.books(s, tx - 3, tz - 3, tx - 3, tz + 3, f, f + 1);
        Furnish.crate(s, tx + 2, f, tz + 3, Gothic.WOOD);
        Furnish.crate(s, tx + 3, f, tz + 3, Gothic.WOOD);
        lamp(s, tx, 18, tz);
        for (int x = tx - 2; x <= tx + 2; x += 2) {
            Furnish.weaponRack(s, x, 20, tz - 3, "south", Gothic.WOOD);
        }
        s.set(tx - 3, 20, tz, Gothic.WOOD_FENCE).set(tx - 3, 21, tz, "minecraft:skeleton_skull[rotation=12]");
        lamp(s, tx, 23, tz);
        s.set(tx, top - 1, tz, "minecraft:bell[attachment=ceiling,facing=north,powered=false]");
        lamp(s, tx - 2, top - 1, tz - 2);
        lamp(s, tx + 2, top - 1, tz + 2);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{tx, 1, tz + 3}, new int[]{tx + 2, 1, 8});
    }

    /**
     * The great map of the guild on a table: lands, seas and hills of terracotta {@code width} along x and {@code depth}
     * along z from (x, y, z), a table one block high whose top is the map.
     */
    private static void guildMapTable(Structure s, int x, int y, int z, int width, int depth) {
        Structure map = new Structure(width, 2, 1);
        guildMap(map, 0, 0, 0, width);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < depth; j++) {
                s.set(x + i, y, z + j, map.get(i, (j + i) % 2, 0));
            }
        }
    }

    /**
     * Rank SS, one of the main buildings of a capital after the castle: the great hall of the order, a high nave under a
     * steep roof with an iron cresting, aisles under lean-to roofs behind tracery parapets, flying buttresses over them to
     * piers with pinnacles and gargoyles; its front between two towers, a doorway of two leaves under a wimperg and a great
     * rose in the gable; the west tower, the greater, rises to a crown of a pierced parapet, great pinnacles and gargoyles
     * round a needle spire with lucarnes, the east one to a lower crown with a slender needle; before them a paved forecourt
     * behind an iron railing with lamps. Inside, the nave under a ribbed vault with coronas of candles: the counter of the
     * registrar at the back before the shelves of the guild, two long tables with benches; the aisles open to it through
     * pointed arcades, with a chimneypiece and benches, the contract board and trophies; the west tower holds the armoury,
     * the chamber of the master with his bed of state and the archive, the east one the hall of the companions, the map
     * room and the bell.
     */
    private static Drawn guildSS() {
        Structure s = new Structure(31, 48, 33);
        int nx1 = 10;
        int nx2 = 20;
        int nz1 = 4;
        int nz2 = 28;
        int eaves = 17;
        int cx = 15;
        int az1 = 6;
        int az2 = 20;
        // the aisles: outer walls, back walls stepping up under their lean-to roofs
        for (int side = 0; side < 2; side++) {
            int ox = side == 0 ? 6 : 24;
            for (int y = 0; y <= 7; y++) {
                s.fill(ox, y, az1, ox, y, az2, y == 0 ? Gothic.ROUGH : Gothic.WALL);
            }
            for (int k = 0; k <= 4; k++) {
                int x = side == 0 ? ox + k : ox - k;
                s.fill(x, 0, az1, x, 7 + k, az1, Gothic.WALL);
                s.set(x, 0, az1, Gothic.ROUGH);
            }
            Gothic.paving(s, side == 0 ? 7 : 21, az1 + 1, side == 0 ? 9 : 23, az2 - 1, 0);
        }
        // the nave
        walls(s, nx1, nz1, nx2, nz2, eaves - 1);
        Gothic.paving(s, nx1 + 1, nz1 + 1, nx2 - 1, nz2 - 1, 0);
        for (int z : new int[]{10, 14, 18}) {
            Gothic.pointedArch(s, nx1, 1, z, "west", 3, 5, 1, Gothic.TRIM_STAIRS, Gothic.TRIM_SLAB);
            Gothic.pointedArch(s, nx2, 1, z, "east", 3, 5, 1, Gothic.TRIM_STAIRS, Gothic.TRIM_SLAB);
        }
        Gothic.vault(s, nx1 + 1, nz1 + 1, nx2 - 1, nz2 - 1, 14, false, 4, 8);
        int ridge = Gothic.steepRoof(s, nx1, nz1, nx2, nz2, eaves, false, 2, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.cresting(s, cx, nz1 + 1, cx, nz2 - 1, ridge);
        Gothic.shedRoof(s, 7, az1 + 1, 9, az2 - 1, 8, "east", 1);
        Gothic.shedRoof(s, 21, az1 + 1, 23, az2 - 1, 8, "west", 1);
        Gothic.parapet(s, 6, az1, 6, az2, 8);
        Gothic.parapet(s, 24, az1, 24, az2, 8);

        // the towers of the front
        int wx = 6;
        int ex = 24;
        int tz = 24;
        int wTop = 31;
        int eTop = 30;
        for (int t = 0; t < 2; t++) {
            int tx = t == 0 ? wx : ex;
            int top = t == 0 ? wTop : eTop;
            walls(s, tx - 4, tz - 4, tx + 4, tz + 4, top - 1);
            clear(s, tx - 3, 1, tz - 3, tx + 3, top - 1, tz + 3);
            Gothic.paving(s, tx - 3, tz - 3, tx + 3, tz + 3, 0);
            for (int y : new int[]{8, 16, 23}) {
                s.fill(tx - 3, y, tz - 3, tx + 3, y, tz + 3, Gothic.PLANKS);
            }
            s.fill(tx - 3, top, tz - 3, tx + 3, top, tz + 3, Gothic.WALL);
            Gothic.pointedArch(s, t == 0 ? 8 : 22, 1, az2, "north", 3, 4, 1, Gothic.TRIM_STAIRS, Gothic.TRIM_SLAB);
        }

        // the front of the nave: the doorway and the great rose
        Gothic.portal(s, cx, 1, nz2, "south", 3, true);
        banners(s, cx - 4, cx + 4, 9, nz2 + 1, "south");
        for (int x = cx - 1; x <= cx + 1; x++) {
            s.set(x, 0, nz2 + 1, Kit.stairs(Gothic.TRACERY_STAIRS, "north"));
        }
        Gothic.rose(s, cx, 18, nz2, "south", 9);
        // the windows of the towers
        for (int t = 0; t < 2; t++) {
            int tx = t == 0 ? wx : ex;
            String out = t == 0 ? "west" : "east";
            String in = t == 0 ? "east" : "west";
            int side = t == 0 ? -1 : 1;
            Gothic.lancet(s, tx, 2, tz + 4, "south", 1, 4, Gothic.AZURE, Gothic.CRIMSON);
            Gothic.lancet(s, tx + 4 * side, 2, tz, out, 1, 4, Gothic.AZURE, Gothic.CRIMSON);
            Gothic.lancet(s, tx, 10, tz + 4, "south", 3, 3, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, tx + 4 * side, 10, tz, out, 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, tx, 18, tz + 4, "south", 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, tx + 4 * side, 18, tz, out, 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, tx, 18, tz - 4, "north", 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
            for (String f : new String[]{"south", out, "north", in}) {
                int[] d = Furnish.step(f);
                Gothic.lancet(s, tx + 4 * d[0], 25, tz + 4 * d[1], f, 3, 2, Gothic.VIOLET, Gothic.CRIMSON);
            }
            Gothic.buttress(s, tx - 4, 0, tz + 4, "south", 20, 2, true);
            Gothic.buttress(s, tx + 4, 0, tz + 4, "south", 20, 2, true);
            Gothic.buttress(s, tx + 4 * side, 0, tz + 4, out, 20, 2, true);
            Gothic.buttress(s, tx + 4 * side, 0, tz - 4, out, 20, 2, true);
        }
        // the aisles, the clerestory and the flying buttresses
        for (int side = 0; side < 2; side++) {
            int ox = side == 0 ? 6 : 24;
            int nx = side == 0 ? nx1 : nx2;
            String out = side == 0 ? "west" : "east";
            for (int z : new int[]{10, 18}) {
                Gothic.lancet(s, ox, 2, z, out, 1, 4, Gothic.AZURE, Gothic.CRIMSON);
            }
            for (int z : new int[]{10, 14, 18}) {
                Gothic.lancet(s, nx, 11, z, out, 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
            }
            for (int z : new int[]{8, 12, 16}) {
                Gothic.flyingButtress(s, nx, 15, z, out, 5, 0, true);
            }
            Gothic.buttress(s, ox, 0, az1, "north", 7, 1, false);
            Gothic.lancet(s, side == 0 ? 8 : 22, 2, az1, "north", 1, 3, Gothic.AZURE, Gothic.CRIMSON);
        }
        // the back of the nave: a great window of three lights, a rose over it, buttresses
        Gothic.lancet(s, cx, 4, nz1, "north", 5, 5, Gothic.VIOLET, Gothic.AZURE);
        Gothic.rose(s, cx, 17, nz1, "north", 5);
        Gothic.buttress(s, nx1, 0, nz1, "north", 15, 2, true);
        Gothic.buttress(s, nx2, 0, nz1, "north", 15, 2, true);
        // the tops of the towers
        Gothic.crown(s, wx - 4, tz - 4, wx + 4, tz + 4, wTop, true);
        spire(s, wx, wTop, tz, 3, 17, true);
        walkLamps(s, wx, tz, 3, wTop + 1);
        Gothic.crown(s, ex - 4, tz - 4, ex + 4, tz + 4, eTop, true);
        spire(s, ex, eTop, tz, 2, 13, true);
        walkLamps(s, ex, tz, 3, eTop + 1);

        // the forecourt
        for (int x = 0; x < s.sizeX; x++) {
            for (int z = nz2 + 1; z < s.sizeZ; z++) {
                if (!s.solid(x, 0, z)) {
                    s.set(x, 0, z, Kit.slab(Gothic.TRACERY_SLAB));
                }
            }
        }
        Gothic.ironFence(s, 0, 32, 12, 32, 0, 2, true);
        Gothic.ironFence(s, 18, 32, 30, 32, 0, 2, true);
        lampPost(s, 12, 0, 30);
        lampPost(s, 18, 0, 30);

        // the nave: the counter at the back, the long tables, coronas, candelabra
        counter(s, cx, 1, 9, 13, 17, 1);
        Furnish.books(s, 11, 5, 13, 5, 1, 3);
        Furnish.books(s, 17, 5, 19, 5, 1, 3);
        Gothic.candelabrum(s, 14, 1, 5);
        Gothic.candelabrum(s, 16, 1, 5);
        for (int x : new int[]{13, 17}) {
            Furnish.longTable(s, x, 12, x, 24, 1, Gothic.WOOD);
            Furnish.bench(s, x - 1, 12, x - 1, 24, 1, "east", Gothic.WOOD);
            Furnish.bench(s, x + 1, 12, x + 1, 24, 1, "west", Gothic.WOOD);
            for (int z = 12; z <= 24; z += 3) {
                s.set(x, 2, z, Furnish.candles(z % 2 == 0 ? 3 : 2));
            }
        }
        for (int z : new int[]{8, 14, 20, 25}) {
            Gothic.corona(s, cx, z, 17, 6);
        }
        for (int z : new int[]{8, 12, 16}) {
            put(s, nx1 + 1, 9, z, Kit.wallBanner("red", "east"));
            put(s, nx2 - 1, 9, z, Kit.wallBanner("red", "west"));
        }
        put(s, cx - 3, 9, nz2 - 1, Kit.wallBanner(GUILD, "north"));
        put(s, cx + 3, 9, nz2 - 1, Kit.wallBanner(GUILD, "north"));
        // the aisles: a chimneypiece and benches in the west, the contract board in the east, trophies at their backs
        Gothic.fireplace(s, 6, 1, 14, "east", 6);
        Furnish.bench(s, 9, 13, 9, 15, 1, "west", Gothic.WOOD);
        contractBoard(s, 23, 1, 14, "west", 3);
        trophies(s, 7, 1, az1 + 1, "south", 3);
        trophies(s, 21, 1, az1 + 1, "south", 3);
        for (int x : new int[]{8, 22}) {
            for (int z : new int[]{9, 17}) {
                lamp(s, x, 6, z);
            }
        }
        // the nave attic over the vault is closed: lamps keep it from darkness
        for (int z = 7; z <= 25; z += 6) {
            s.set(cx, 19, z, Kit.lantern());
        }

        // the west tower: the armoury, the chamber of the master, the archive, the room under the crown
        ladder(s, wx - 3, tz - 3, 1, 24, "east");
        for (int z = tz - 1; z <= tz + 3; z += 2) {
            Furnish.weaponRack(s, wx - 3, 1, z, "east", Gothic.WOOD);
            s.set(wx - 3, 3, z, "minecraft:skeleton_wall_skull[facing=east]");
        }
        s.set(wx - 1, 1, tz + 3, Gothic.WOOD_FENCE).set(wx - 1, 2, tz + 3, "minecraft:skeleton_skull[rotation=8]");
        s.set(wx + 1, 1, tz + 3, Gothic.WOOD_FENCE).set(wx + 1, 2, tz + 3, "minecraft:skeleton_skull[rotation=8]");
        Furnish.crate(s, wx + 3, 1, tz, Gothic.WOOD);
        Furnish.sack(s, wx + 3, 1, tz + 1, false);
        lamp(s, wx, 7, tz);
        Gothic.stateBed(s, wx + 1, 9, tz - 1, "north");
        Gothic.fireplace(s, wx - 4, 9, tz + 2, "east", 15);
        Furnish.rug(s, wx - 1, tz + 1, wx + 2, tz + 3, 9, Gothic.CLOTH_CARPET);
        Furnish.desk(s, wx + 3, 9, tz + 2, "west", Gothic.WOOD);
        Gothic.bookcase(s, wx + 3, 9, tz - 1, "west", 3);
        lamp(s, wx, 15, tz + 1);
        lamp(s, wx - 2, 15, tz - 2);
        Furnish.books(s, wx + 3, tz - 2, wx + 3, tz + 3, 17, 18);
        Furnish.desk(s, wx, 17, tz, "south", Gothic.WOOD);
        lamp(s, wx, 22, tz - 1);
        s.set(wx, wTop - 1, tz, "minecraft:bell[attachment=ceiling,facing=north,powered=false]");
        lamp(s, wx - 2, wTop - 1, tz + 2);
        lamp(s, wx + 2, wTop - 1, tz - 2);
        // the east tower: the hall of the companions, the map room, the room under the crown
        ladder(s, ex + 3, tz - 3, 1, 24, "west");
        Furnish.longTable(s, ex, tz - 1, ex, tz + 2, 1, Gothic.WOOD);
        Furnish.bench(s, ex - 1, tz - 1, ex - 1, tz + 2, 1, "east", Gothic.WOOD);
        Furnish.bench(s, ex + 1, tz - 1, ex + 1, tz + 2, 1, "west", Gothic.WOOD);
        s.set(ex, 2, tz, Furnish.candles(3));
        Gothic.fireplace(s, ex + 4, 1, tz + 2, "west", 7);
        lamp(s, ex, 7, tz);
        int f = 9;
        guildMapTable(s, ex - 2, f, tz - 1, 5, 2);
        for (int x = ex - 2; x <= ex + 2; x++) {
            s.set(x, f, tz + 1, Kit.stairs(Gothic.WOOD_STAIRS, "south"));
        }
        s.set(ex - 3, f, tz + 3, "minecraft:lectern[facing=north,has_book=false,powered=false]");
        s.set(ex + 2, f, tz + 3, CARTOGRAPHY);
        Gothic.bookcase(s, ex - 2, f, tz - 3, "south", 3);
        guildMap(s, ex, f + 1, tz - 3, 3);
        lamp(s, ex, 15, tz);
        lamp(s, ex - 2, 15, tz + 2);
        Furnish.crate(s, ex - 3, 17, tz + 3, Gothic.WOOD);
        Furnish.sack(s, ex - 2, 17, tz + 3, true);
        lamp(s, ex, 22, tz);
        lamp(s, ex, eTop - 1, tz);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{cx, 1, nz2 - 1}, new int[]{cx + 2, 1, 10});
    }

    // ---------------------------------------------------------------- furniture of the guild

    /**
     * The counter of the registrar: the guild desk looking south at (x, y, z) in a counter of timber slabs from column
     * {@code xa} to {@code xb} with candles and a lantern on it, the post of the registrar two blocks behind the desk and the
     * one chest of the guild beside the post on the side {@code chestSide} (+1 east, -1 west).
     */
    private static void counter(Structure s, int x, int y, int z, int xa, int xb, int chestSide) {
        for (int xx = xa; xx <= xb; xx++) {
            s.set(xx, y, z, xx == x ? DESK : Kit.topSlab(Gothic.WOOD_SLAB));
        }
        s.set(xa, y + 1, z, Furnish.candles(2));
        s.set(xb, y + 1, z, Kit.lantern());
        s.set(x, y, z - 2, WORK_POST);
        s.set(x + chestSide, y, z - 2, Kit.chest("south"));
    }

    /**
     * The contract board against a wall, looking {@code facing}: a panel of timber {@code width} wide and four high in the
     * cells before the wall around (x, z), under a pointed top of timber stairs; a ledge before it with papers (white
     * carpet) and candles, notices of white, yellow and grey cloth hanging on it.
     */
    private static void contractBoard(Structure s, int x, int y, int z, String facing, int width) {
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        int half = (width - 1) / 2;
        for (int u = -half; u <= half; u++) {
            int px = x + a[0] * u;
            int pz = z + a[1] * u;
            s.fill(px, y, pz, px, y + 3, pz, Gothic.PLANKS);
            int fx = px + f[0];
            int fz = pz + f[1];
            s.set(fx, y, fz, Kit.topSlab(Gothic.WOOD_SLAB));
            s.set(fx, y + 1, fz, u == 0 ? Furnish.candles(3) : "minecraft:white_carpet");
            String color = Math.floorMod(u, 3) == 1 ? "yellow" : Math.floorMod(u, 3) == 2 ? "light_gray" : "white";
            s.set(fx, y + 3, fz, Kit.wallBanner(color, facing));
        }
        int ex = x + a[0] * (half + 1);
        int ez = z + a[1] * (half + 1);
        int wx = x - a[0] * (half + 1);
        int wz = z - a[1] * (half + 1);
        String plus = Furnish.left(facing);
        put(s, wx + a[0], y + 4, wz + a[1], Kit.stairs(Gothic.WOOD_STAIRS, plus));
        put(s, ex - a[0], y + 4, ez - a[1], Kit.stairs(Gothic.WOOD_STAIRS, Furnish.opposite(plus)));
        for (int u = -half + 1; u <= half - 1; u++) {
            put(s, x + a[0] * u, y + 4, z + a[1] * u, Gothic.PLANKS);
        }
        put(s, x, y + 5, z, Furnish.candle(""));
    }

    /**
     * Trophies along a wall that looks {@code facing}, {@code count} cells from (x, z) toward the left of {@code facing}:
     * racks of weapons (grindstones) under the skulls of beasts hung on the wall, and between them pikes carrying skulls;
     * the banner of the guild over them.
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
                s.set(px, y, pz, Gothic.WOOD_FENCE).set(px, y + 1, pz, "minecraft:skeleton_skull[rotation=" + rotation(facing) + "]");
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
     * A map of the lands of the guild painted on a wall that runs along x: lands, seas and hills of terracotta
     * {@code width} wide and two high, from (x, y, z) toward the east.
     */
    private static void guildMap(Structure s, int x, int y, int z, int width) {
        String[] map = {"lgyBcglb", "gcglbylg"};
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < width; i++) {
                String color = switch (map[row].charAt(i % 8)) {
                    case 'l' -> "lime";
                    case 'g' -> "green";
                    case 'y' -> "yellow";
                    case 'b' -> "light_blue";
                    case 'c' -> "cyan";
                    default -> "brown";
                };
                s.set(x + i, y + 1 - row, z, "minecraft:" + color + "_terracotta");
            }
        }
    }

    /**
     * The map table: the cartography table with a stool of timber before it.
     */
    private static void mapTable(Structure s, int x, int y, int z) {
        s.set(x, y, z, CARTOGRAPHY);
        s.set(x, y, z + 1, Kit.stairs(Gothic.WOOD_STAIRS, "south"));
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
     * Rank B: one slim square tower of three storeys under a slender octagonal needle that grows out of its square with
     * a pinnacle on each corner and lucarnes of purple glass, a grand doorway under a wimperg, purple lancets on every
     * storey, and three piers standing free around it that fly their arches to its walls. On the ground floor the magic
     * focus with the post of the mage and its chest, a brewing corner and shelves; over it the room of the mage with the
     * library; at the top, under the spire, the laboratory.
     */
    private static Drawn mageB() {
        Structure s = new Structure(11, 33, 11);
        int c = 5;
        int r = 3;
        int top = 16;
        walls(s, c - r, c - r, c + r, c + r, top - 1);
        Gothic.paving(s, c - r + 1, c - r + 1, c + r - 1, c + r - 1, 0);
        s.fill(c - r + 1, 5, c - r + 1, c + r - 1, 5, c + r - 1, Gothic.PLANKS);
        s.fill(c - r + 1, 10, c - r + 1, c + r - 1, 10, c + r - 1, Gothic.PLANKS);
        s.fill(c - r + 1, top, c - r + 1, c + r - 1, top, c + r - 1, Gothic.WALL);
        Gothic.portal(s, c, 1, c + r, "south", 1, true);
        banners(s, c - 2, c + 2, 9, c + r + 1, "south");
        s.set(c, 0, c + r + 1, Kit.stairs(Gothic.TRACERY_STAIRS, "north"));
        for (String side : new String[]{"west", "east", "north"}) {
            int[] d = Furnish.step(side);
            Gothic.lancet(s, c + d[0] * r, 2, c + d[1] * r, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            if (side.equals("north")) {
                Gothic.blindLancet(s, c + d[0] * r, 7, c + d[1] * r, side, 1, 2);
            } else {
                Gothic.lancet(s, c + d[0] * r, 7, c + d[1] * r, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            }
        }
        for (String side : new String[]{"west", "east", "north", "south"}) {
            int[] d = Furnish.step(side);
            int[] a = Furnish.step(Furnish.left(side));
            for (int u = -1; u <= 1; u += 2) {
                Gothic.lancet(s, c + d[0] * r + a[0] * u, 12, c + d[1] * r + a[1] * u, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            }
            put(s, c + d[0] * (r + 1), 14, c + d[1] * (r + 1), Gothic.TRACERY);
        }
        for (String side : new String[]{"west", "east", "north"}) {
            int[] d = Furnish.step(side);
            Gothic.flyingButtress(s, c + d[0] * r, 12, c + d[1] * r, side, 2, 0, false);
        }
        Gothic.crown(s, c - r, c - r, c + r, c + r, top, true);
        glowingTips(s, c - r, c - r, c + r, c + r, top);
        spire(s, c, top + 1, c, 2, 15, false);

        // the ground floor: the focus, the post of the mage and its chest, the brewing corner, shelves
        Furnish.rug(s, c - 1, c - 1, c + 1, c + 1, 1, PURPLE_CARPET);
        s.set(c, 1, c, FOCUS);
        s.set(c - 2, 1, c - 2, WORK_POST).set(c - 1, 1, c - 2, Kit.chest("south"));
        Furnish.books(s, c - 2, c - 1, c - 2, c - 1, 1, 2);
        Furnish.books(s, c - 2, c + 1, c - 2, c + 1, 1, 2);
        s.set(c + 2, 1, c + 2, BREWING).set(c - 2, 1, c + 2, CAULDRON);
        ladder(s, c + 2, c - 2, 1, 11, "west");
        lamp(s, c, 4, c);
        s.set(c + 2, 1, c, Kit.topSlab(Gothic.WOOD_SLAB)).set(c + 2, 2, c, Furnish.candles(3));
        // the room of the mage with the library
        Gothic.bed(s, c - 2, 6, c + 1, "north", -1);
        Gothic.bookcase(s, c, 6, c - 2, "south", 2);
        s.set(c + 2, 6, c + 1, "minecraft:lectern[facing=west,has_book=false,powered=false]");
        Furnish.rug(s, c - 1, c, c + 1, c + 2, 6, PURPLE_CARPET);
        lamp(s, c, 9, c + 1);
        // the laboratory under the spire
        laboratory(s, c, 11, c, 2);
        lamp(s, c, 15, c);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, c + r - 1}, null);
    }

    /**
     * Rank A: a taller and broader square tower of three storeys, its crown of a pierced parapet with great pinnacles and
     * gargoyles round a needle spire of eight sides with lucarnes of purple glass; three windows of purple glass side by side
     * light the laboratory at the top, pairs of them on the sides where three piers stand free around the tower and fly
     * their arches to it. On the ground floor the magic focus, the post of the mage with its chest and the brewing corner;
     * over it the room of the two mages with the library; at the top, under the spire, the laboratory.
     */
    private static Drawn mageA() {
        Structure s = new Structure(13, 35, 13);
        int c = 6;
        int r = 4;
        int top = 17;
        walls(s, c - r, c - r, c + r, c + r, top - 1);
        Gothic.paving(s, c - r + 1, c - r + 1, c + r - 1, c + r - 1, 0);
        s.fill(c - r + 1, 5, c - r + 1, c + r - 1, 5, c + r - 1, Gothic.PLANKS);
        s.fill(c - r + 1, 10, c - r + 1, c + r - 1, 10, c + r - 1, Gothic.PLANKS);
        s.fill(c - r + 1, top, c - r + 1, c + r - 1, top, c + r - 1, Gothic.WALL);
        Gothic.portal(s, c, 1, c + r, "south", 1, true);
        banners(s, c - 2, c + 2, 9, c + r + 1, "south");
        s.set(c, 0, c + r + 1, Kit.stairs(Gothic.TRACERY_STAIRS, "north"));
        for (String side : new String[]{"west", "east", "north"}) {
            int[] d = Furnish.step(side);
            int[] a = Furnish.step(Furnish.left(side));
            int x = c + d[0] * r;
            int z = c + d[1] * r;
            Gothic.lancet(s, x, 2, z, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            if (side.equals("north")) {
                Gothic.blindLancet(s, x, 7, z, side, 1, 2);
            } else {
                Gothic.lancet(s, x, 7, z, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            }
            for (int u = -2; u <= 2; u += 4) {
                Gothic.lancet(s, x + a[0] * u, 12, z + a[1] * u, side, 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
            }
        }
        for (int u = -2; u <= 2; u += 2) {
            Gothic.lancet(s, c + u, 12, c + r, "south", 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
        }
        put(s, c - 1, 15, c + r + 1, Gothic.TRACERY);
        put(s, c + 1, 15, c + r + 1, Gothic.TRACERY);
        for (String side : new String[]{"west", "east", "north"}) {
            int[] d = Furnish.step(side);
            Gothic.flyingButtress(s, c + d[0] * r, 13, c + d[1] * r, side, 2, 0, false);
        }
        Gothic.crown(s, c - r, c - r, c + r, c + r, top, true);
        glowingTips(s, c - r, c - r, c + r, c + r, top);
        spire(s, c, top, c, 3, 17, true);

        // the ground floor: the focus, the post of the mage and its chest, the brewing corner, shelves
        Furnish.rug(s, c - 1, c - 1, c + 1, c + 1, 1, PURPLE_CARPET);
        s.set(c, 1, c, FOCUS);
        s.set(c - 2, 1, c - 2, WORK_POST).set(c - 1, 1, c - 2, Kit.chest("south"));
        Furnish.books(s, c - 3, c - 3, c - 3, c - 2, 1, 2);
        Furnish.books(s, c - 3, c + 2, c - 3, c + 3, 1, 2);
        s.set(c + 3, 1, c + 3, BREWING).set(c + 3, 1, c + 2, CAULDRON).set(c + 2, 1, c + 3, BREWING);
        s.set(c - 2, 1, c + 3, Kit.topSlab(Gothic.WOOD_SLAB)).set(c - 2, 2, c + 3, Furnish.candles(3));
        s.set(c + 3, 1, c, Kit.topSlab(Gothic.WOOD_SLAB)).set(c + 3, 2, c, "minecraft:flower_pot");
        ladder(s, c + 3, c - 3, 1, 11, "west");
        Gothic.candelabrum(s, c + 2, 1, c - 2);
        lamp(s, c, 4, c);
        lamp(s, c - 2, 4, c + 2);
        // the room of the two mages with the library
        Gothic.bed(s, c - 3, 6, c + 3, "north", -1);
        Gothic.bed(s, c - 1, 6, c + 3, "north", -1);
        Gothic.bookcase(s, c, 6, c - 3, "south", 2);
        Furnish.books(s, c - 3, c - 3, c - 3, c - 1, 6, 7);
        s.set(c + 2, 6, c + 1, "minecraft:lectern[facing=west,has_book=false,powered=false]");
        Furnish.desk(s, c + 2, 6, c + 3, "north", Gothic.WOOD);
        Furnish.rug(s, c - 1, c - 1, c + 1, c + 1, 6, PURPLE_CARPET);
        lamp(s, c, 9, c);
        lamp(s, c + 2, 9, c - 2);
        // the laboratory under the spire
        laboratory(s, c, 11, c, 3);
        lamp(s, c, 15, c);
        lamp(s, c - 2, 15, c + 2);
        lamp(s, c + 2, 15, c + 2);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, c + r - 1}, null);
    }

    /**
     * The laboratory in the top storey of a mage tower around (cx, cz), one stands at layer {@code y}, the inside of the
     * storey {@code h} blocks to every side of its middle, the ladder from below in its north-east corner: the enchanting
     * table in its middle between two glowing end rods, shelves of books in the north-west corner and in the middle of the
     * side walls, candles, a globe of amethyst on a black stand by a table of star charts, a brewing stand and a cauldron.
     */
    private static void laboratory(Structure s, int cx, int y, int cz, int h) {
        s.set(cx, y, cz, ENCHANTING);
        Furnish.books(s, cx - h, cz - h, cx - h, cz - h, y, y + 1);
        Furnish.books(s, cx - h, cz, cx - h, cz, y, y + 1);
        Furnish.books(s, cx + h, cz, cx + h, cz, y, y + 1);
        s.set(cx - h + 1, y, cz - h, Kit.topSlab(Gothic.WOOD_SLAB)).set(cx - h + 1, y + 1, cz - h, Furnish.candles(3));
        s.set(cx - h, y, cz + h, Gothic.STAND).set(cx - h, y + 1, cz + h, AMETHYST).set(cx - h, y + 2, cz + h, CLUSTER);
        s.set(cx - h + 1, y, cz + h, CARTOGRAPHY);
        s.set(cx + h, y, cz + h, BREWING).set(cx + h, y, cz + h - 1, CAULDRON);
        s.set(cx, y, cz - 2, END_ROD).set(cx, y, cz + 2, END_ROD);
    }

    /**
     * Rank S: the tower of A grown by a storey, two piers flying their arches to it from the east and the north, and on its
     * west a library wing under a steep roof with its stone gable to the street, a rose and a doorway in it, lancets between
     * buttresses on its side; before the tower a paved court behind an iron railing with lamps. On the ground floor of the
     * tower the magic focus with the post of the mage and its chest; over it the room of the three mages, the brewing room,
     * and at the top, under the spire, the laboratory; the wing is the library, open to its roof.
     */
    private static Drawn mageS() {
        Structure s = new Structure(19, 41, 17);
        int c = 12;
        int cz = 6;
        int r = 4;
        int top = 22;
        // the library wing on the west
        int ax1 = 2;
        int ax2 = 8;
        int az1 = 4;
        int az2 = 14;
        walls(s, ax1, az1, ax2, az2, 7);
        Gothic.paving(s, ax1 + 1, az1 + 1, ax2 - 1, az2 - 1, 0);
        int ridge = Gothic.steepRoof(s, ax1, az1, ax2, az2, 8, false, 2, Gothic.PARAPET, Gothic.PARAPET);
        Gothic.cresting(s, 5, az1 + 1, 5, az2 - 1, ridge);
        // the tower
        walls(s, c - r, cz - r, c + r, cz + r, top - 1);
        clear(s, c - r + 1, 1, cz - r + 1, c + r - 1, top - 1, cz + r - 1);
        Gothic.paving(s, c - r + 1, cz - r + 1, c + r - 1, cz + r - 1, 0);
        for (int y : new int[]{5, 10, 15}) {
            s.fill(c - r + 1, y, cz - r + 1, c + r - 1, y, cz + r - 1, Gothic.PLANKS);
        }
        s.fill(c - r + 1, top, cz - r + 1, c + r - 1, top, cz + r - 1, Gothic.WALL);
        s.door(ax2, 1, 7, Gothic.DOOR, "east", "left");
        Gothic.portal(s, c, 1, cz + r, "south", 1, true);
        banners(s, c - 2, c + 2, 9, cz + r + 1, "south");
        s.set(c, 0, cz + r + 1, Kit.stairs(Gothic.TRACERY_STAIRS, "north"));
        // the windows of the tower
        for (String side : new String[]{"east", "north"}) {
            int[] d = Furnish.step(side);
            int[] a = Furnish.step(Furnish.left(side));
            int x = c + d[0] * r;
            int z = cz + d[1] * r;
            Gothic.lancet(s, x, 2, z, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x, 7, z, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x, 12, z, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            for (int u = -2; u <= 2; u += 4) {
                Gothic.lancet(s, x + a[0] * u, 17, z + a[1] * u, side, 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
            }
            Gothic.flyingButtress(s, x, 18, z, side, 2, 0, false);
        }
        for (int u = -2; u <= 2; u += 4) {
            Gothic.lancet(s, c - r, 17, cz + u, "west", 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
        }
        for (int u = -2; u <= 2; u += 2) {
            Gothic.lancet(s, c + u, 17, cz + r, "south", 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
        }
        Gothic.lancet(s, c, 12, cz + r, "south", 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
        for (int u = -1; u <= 1; u += 2) {
            Gothic.blindLancet(s, c + 2 * u, 12, cz + r, "south", 1, 2);
            put(s, c + u, 14, cz + r + 1, Gothic.TRACERY);
            put(s, c + u, 20, cz + r + 1, Gothic.TRACERY);
        }
        Gothic.crown(s, c - r, cz - r, c + r, cz + r, top, true);
        glowingTips(s, c - r, cz - r, c + r, cz + r, top);
        spire(s, c, top, cz, 3, 19, true);
        // the wing: the gable to the street, the side, the back
        Gothic.portal(s, 5, 1, az2, "south", 1, false);
        s.set(5, 0, az2 + 1, Kit.stairs(Gothic.TRACERY_STAIRS, "north"));
        Gothic.rose(s, 5, 10, az2, "south", 5);
        for (int z : new int[]{7, 11}) {
            Gothic.lancet(s, ax1, 2, z, "west", 1, 4, Gothic.VIOLET, Gothic.CRIMSON);
        }
        for (int z : new int[]{az1, 9, az2}) {
            Gothic.buttress(s, ax1, 0, z, "west", 7, 1, false);
        }
        Gothic.lancet(s, 5, 8, az1, "north", 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
        Gothic.lancet(s, ax2, 3, 12, "east", 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
        // the court before the tower
        for (int x = ax2 + 1; x < s.sizeX; x++) {
            for (int z = cz + r + 1; z < s.sizeZ; z++) {
                if (!s.solid(x, 0, z)) {
                    s.set(x, 0, z, Kit.slab(Gothic.TRACERY_SLAB));
                }
            }
        }
        Gothic.ironFence(s, 13, 16, 18, 16, 0, 2, true);
        Gothic.ironFence(s, 18, 11, 18, 15, 0, 2, true);
        lampPost(s, 10, 0, 15);

        // the ground floor: the focus, the post of the mage and its chest, shelves
        Furnish.rug(s, c - 1, cz - 1, c + 1, cz + 1, 1, PURPLE_CARPET);
        s.set(c, 1, cz, FOCUS);
        s.set(c - 2, 1, cz - 2, WORK_POST).set(c - 1, 1, cz - 2, Kit.chest("south"));
        Furnish.books(s, c - 3, cz - 3, c - 2, cz - 3, 1, 3);
        Furnish.books(s, c + 1, cz - 3, c + 2, cz - 3, 1, 3);
        s.set(c + 3, 1, cz + 3, BREWING).set(c + 3, 1, cz + 2, CAULDRON);
        s.set(c - 3, 1, cz + 3, Kit.topSlab(Gothic.WOOD_SLAB)).set(c - 3, 2, cz + 3, Furnish.candles(3));
        ladder(s, c + 3, cz - 3, 1, 16, "west");
        Gothic.candelabrum(s, c + 2, 1, cz - 2);
        lamp(s, c, 4, cz);
        lamp(s, c - 2, 4, cz + 2);
        lamp(s, c + 2, 4, cz - 2);
        // the room of the three mages
        for (int x = c - 3; x <= c + 1; x += 2) {
            Gothic.bed(s, x, 6, cz - 2, "north", -1);
        }
        Furnish.rug(s, c - 2, cz, c + 1, cz + 2, 6, PURPLE_CARPET);
        Furnish.desk(s, c + 3, 6, cz + 2, "west", Gothic.WOOD);
        Gothic.bookcase(s, c - 3, 6, cz + 1, "east", 2);
        Furnish.crate(s, c + 3, 6, cz + 3, Gothic.WOOD);
        lamp(s, c, 9, cz + 1);
        lamp(s, c + 2, 9, cz - 1);
        // the brewing room
        brewingRoom(s, c, 11, cz, 3);
        lamp(s, c, 14, cz);
        lamp(s, c - 2, 14, cz + 2);
        // the laboratory under the spire
        laboratory(s, c, 16, cz, 3);
        lamp(s, c, 21, cz);
        lamp(s, c - 2, 21, cz + 2);
        lamp(s, c + 2, 21, cz + 2);

        // the library in the wing
        Gothic.bookcase(s, ax1 + 1, 1, 9, "east", 4);
        Furnish.books(s, ax1 + 1, az1 + 1, ax1 + 1, az1 + 1, 1, 4);
        Furnish.books(s, ax2 - 1, az1 + 1, ax2 - 1, 6, 1, 4);
        Furnish.books(s, ax2 - 1, 9, ax2 - 1, 10, 1, 4);
        s.set(5, 1, az1 + 1, LECTERN);
        Furnish.longTable(s, 5, 8, 5, 11, 1, Gothic.WOOD);
        Furnish.bench(s, 4, 8, 4, 11, 1, "east", Gothic.WOOD);
        s.set(5, 2, 8, Furnish.candles(3)).set(5, 2, 11, Furnish.candles(2));
        Gothic.candelabrum(s, ax2 - 1, 1, az2 - 1);
        for (int z : new int[]{6, 10}) {
            lamp(s, 5, 6, z);
        }
        lamp(s, 6, 4, az2 - 2);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, cz + r - 1}, null);
    }

    /**
     * The brewing room in a storey of a mage tower around (cx, cz), one stands at layer {@code y}, the inside {@code h}
     * blocks to every side of its middle, the ladder in its north-east corner: brewing stands and cauldrons along its west
     * wall, shelves of slabs with candles and pots, sacks and a crate of herbs, plants in tubs, a table in the middle.
     */
    private static void brewingRoom(Structure s, int cx, int y, int cz, int h) {
        s.set(cx - h, y, cz - h, BREWING).set(cx - h, y, cz - h + 1, CAULDRON).set(cx - h, y, cz + 1, BREWING);
        s.set(cx - h, y, cz + h - 1, CAULDRON).set(cx - h, y, cz + h, BREWING);
        s.set(cx - h, y, cz, Kit.topSlab(Gothic.WOOD_SLAB)).set(cx - h, y + 1, cz, Furnish.candles(3));
        s.set(cx - 1, y, cz - h, Kit.topSlab(Gothic.WOOD_SLAB)).set(cx - 1, y + 1, cz - h, "minecraft:flower_pot");
        s.set(cx + 1, y, cz - h, Kit.topSlab(Gothic.WOOD_SLAB)).set(cx + 1, y + 1, cz - h, Furnish.candles(2));
        Furnish.sack(s, cx + h, y, cz + h, false);
        Furnish.sack(s, cx + h - 1, y, cz + h, true);
        Furnish.crate(s, cx + h, y, cz + h - 1, "oak");
        Furnish.plant(s, cx + h, y, cz - h + 1, "azalea");
        s.set(cx, y, cz, Kit.topSlab(Gothic.WOOD_SLAB)).set(cx, y + 1, cz, Furnish.candles(3));
    }

    /**
     * Rank SS, one of the main buildings of a capital after the castle: a cluster of spired towers. The great tower of the
     * mages stands at the back, six piers around it flying their arches to its walls, its crown of a pierced parapet, great
     * pinnacles and gargoyles round a needle spire with lucarnes, the observatory under it lit by windows of three lights on
     * every side; before it the library, a nave under a ribbed vault and a steep roof whose stone gable looks to the street
     * with a doorway of two leaves under a wimperg and a great rose; two slim towers flank the nave, crowned with pinnacles
     * and needles; before them a paved forecourt behind an iron railing with lamps. On the ground floor of the great tower
     * the magic focus with the post of the mage and its chest, over it the research room, the brewing room, the study and the
     * observatory; the slim towers hold the stores of herbs, the rooms of the four mages, a study and two lookouts.
     */
    private static Drawn mageSS() {
        Structure s = new Structure(25, 47, 25);
        int c = 12;
        int cz = 6;
        int r = 4;
        int top = 27;
        // the library nave
        int nx1 = 7;
        int nx2 = 17;
        int nz1 = 10;
        int nz2 = 21;
        walls(s, nx1, nz1, nx2, nz2, 9);
        Gothic.paving(s, nx1 + 1, nz1 + 1, nx2 - 1, nz2 - 1, 0);
        Gothic.vault(s, nx1 + 1, nz1 + 1, nx2 - 1, nz2 - 1, 7, false, 3, 12);
        int ridge = Gothic.steepRoof(s, nx1, nz1, nx2, nz2, 10, false, 2, Gothic.OPEN, Gothic.PARAPET);
        Gothic.cresting(s, c, nz1 + 1, c, nz2 - 1, ridge);
        // the great tower
        walls(s, c - r, cz - r, c + r, cz + r, top - 1);
        clear(s, c - r + 1, 1, cz - r + 1, c + r - 1, top - 1, cz + r - 1);
        Gothic.paving(s, c - r + 1, cz - r + 1, c + r - 1, cz + r - 1, 0);
        for (int y : new int[]{5, 10, 15, 20}) {
            s.fill(c - r + 1, y, cz - r + 1, c + r - 1, y, cz + r - 1, Gothic.PLANKS);
        }
        s.fill(c - r + 1, top, cz - r + 1, c + r - 1, top, cz + r - 1, Gothic.WALL);
        Gothic.pointedArch(s, c, 1, nz1, "south", 3, 4, 1, Gothic.TRIM_STAIRS, Gothic.TRIM_SLAB);
        // the slim towers beside the nave
        int sz = 16;
        int[] sxs = {4, 20};
        int sTop = 22;
        for (int sx : sxs) {
            walls(s, sx - 3, sz - 3, sx + 3, sz + 3, sTop - 1);
            clear(s, sx - 2, 1, sz - 2, sx + 2, sTop - 1, sz + 2);
            Gothic.paving(s, sx - 2, sz - 2, sx + 2, sz + 2, 0);
            for (int y : new int[]{5, 10, 15}) {
                s.fill(sx - 2, y, sz - 2, sx + 2, y, sz + 2, Gothic.PLANKS);
            }
            s.fill(sx - 2, sTop, sz - 2, sx + 2, sTop, sz + 2, Gothic.WALL);
        }
        s.door(nx1, 1, sz, Gothic.DOOR, "west", "left");
        s.door(nx2, 1, sz, Gothic.DOOR, "east", "left");

        // the front of the nave
        Gothic.portal(s, c, 1, nz2, "south", 3, true);
        banners(s, c - 3, c + 3, 10, nz2 + 1, "south");
        for (int x = c - 1; x <= c + 1; x++) {
            s.set(x, 0, nz2 + 1, Kit.stairs(Gothic.TRACERY_STAIRS, "north"));
        }
        Gothic.rose(s, c, 15, nz2, "south", 7);
        for (int side = -1; side <= 1; side += 2) {
            int x = side < 0 ? nx1 : nx2;
            String facing = side < 0 ? "west" : "east";
            Gothic.lancet(s, x, 2, nz1 + 1, facing, 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.buttress(s, x, 0, nz2, "south", 9, 1, false);
        }
        // the windows of the slim towers, their buttresses
        for (int t = 0; t < 2; t++) {
            int sx = sxs[t];
            String out = t == 0 ? "west" : "east";
            int side = t == 0 ? -1 : 1;
            for (int y : new int[]{2, 7, 12, 17}) {
                Gothic.lancet(s, sx, y, sz + 3, "south", 1, y == 17 ? 3 : 2, Gothic.VIOLET, Gothic.CRIMSON);
                Gothic.lancet(s, sx + 3 * side, y, sz, out, 1, y == 17 ? 3 : 2, Gothic.VIOLET, Gothic.CRIMSON);
            }
            Gothic.blindLancet(s, sx, 12, sz - 3, "north", 1, 2);
            Gothic.lancet(s, sx, 17, sz - 3, "north", 1, 3, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.buttress(s, sx + 3 * side, 0, sz + 3, "south", 14, 1, true);
            Gothic.buttress(s, sx + 3 * side, 0, sz + 3, out, 14, 1, true);
            Gothic.buttress(s, sx + 3 * side, 0, sz - 3, out, 14, 1, true);
            Gothic.crown(s, sx - 3, sz - 3, sx + 3, sz + 3, sTop, true);
            glowingTips(s, sx - 3, sz - 3, sx + 3, sz + 3, sTop);
            spire(s, sx, sTop + 1, sz, 2, 13, false);
        }
        // the windows of the great tower, the flying buttresses, the crown and the spire
        for (String side : new String[]{"west", "east", "north"}) {
            int[] d = Furnish.step(side);
            int[] a = Furnish.step(Furnish.left(side));
            int x = c + d[0] * r;
            int z = cz + d[1] * r;
            Gothic.lancet(s, x, 2, z, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x, 7, z, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            Gothic.lancet(s, x, 12, z, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            if (side.equals("east")) {
                Gothic.lancet(s, x, 17, z, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            } else {
                Gothic.blindLancet(s, x, 17, z, side, 1, 2);
            }
            for (int u = -2; u <= 2; u += 2) {
                Gothic.lancet(s, x + a[0] * u, 23, z + a[1] * u, side, 1, 2, Gothic.VIOLET, Gothic.CRIMSON);
            }
            for (int u = -1; u <= 1; u += 2) {
                put(s, x + a[0] * u + d[0], 25, z + a[1] * u + d[1], Gothic.TRACERY);
            }
            for (int u = -2; u <= 2; u += 4) {
                Gothic.flyingButtress(s, x + a[0] * u, 18, z + a[1] * u, side, 2, 0, false);
            }
        }
        Gothic.lancet(s, c, 23, cz + r, "south", 3, 2, Gothic.VIOLET, Gothic.CRIMSON);
        Gothic.crown(s, c - r, cz - r, c + r, cz + r, top, true);
        glowingTips(s, c - r, cz - r, c + r, cz + r, top);
        spire(s, c, top, cz, 3, 20, true);

        // the forecourt
        for (int x = 0; x < s.sizeX; x++) {
            for (int z = nz2 + 1; z < s.sizeZ; z++) {
                if (!s.solid(x, 0, z)) {
                    s.set(x, 0, z, Kit.slab(Gothic.TRACERY_SLAB));
                }
            }
        }
        Gothic.ironFence(s, 0, 24, 9, 24, 0, 2, true);
        Gothic.ironFence(s, 15, 24, 24, 24, 0, 2, true);
        lampPost(s, 4, 0, 23);
        lampPost(s, 20, 0, 23);

        // the great tower: the focus, the research room, the brewing room, the study, the observatory
        Furnish.rug(s, c - 1, cz - 1, c + 1, cz + 1, 1, PURPLE_CARPET);
        s.set(c, 1, cz, FOCUS);
        s.set(c - 2, 1, cz - 2, WORK_POST).set(c - 1, 1, cz - 2, Kit.chest("south"));
        Furnish.books(s, c - 3, cz - 3, c - 2, cz - 3, 1, 3);
        Furnish.books(s, c + 1, cz - 3, c + 2, cz - 3, 1, 3);
        s.set(c - 3, 1, cz + 3, AMETHYST).set(c - 3, 2, cz + 3, CLUSTER);
        s.set(c + 3, 1, cz + 3, AMETHYST).set(c + 3, 2, cz + 3, CLUSTER);
        Gothic.candelabrum(s, c - 3, 1, cz + 2);
        Gothic.candelabrum(s, c + 3, 1, cz + 2);
        ladder(s, c + 3, cz - 3, 1, 21, "west");
        lamp(s, c, 4, cz);
        lamp(s, c - 2, 4, cz + 2);
        lamp(s, c + 2, 4, cz + 2);
        // the research room
        s.set(c, 6, cz, ENCHANTING);
        for (int d = -2; d <= 2; d++) {
            if (d != 0) {
                Furnish.books(s, c - 2, cz + d, c - 2, cz + d, 6, 7);
                Furnish.books(s, c + 2, cz + d, c + 2, cz + d, 6, 7);
            }
        }
        s.set(c, 6, cz - 3, CARTOGRAPHY);
        s.set(c - 3, 6, cz + 3, Kit.topSlab(Gothic.WOOD_SLAB)).set(c - 3, 7, cz + 3, Furnish.candles(3));
        s.set(c + 3, 6, cz + 3, Gothic.STAND).set(c + 3, 7, cz + 3, AMETHYST).set(c + 3, 8, cz + 3, CLUSTER);
        lamp(s, c, 9, cz);
        lamp(s, c - 2, 9, cz - 3);
        lamp(s, c + 2, 9, cz + 3);
        // the brewing room
        brewingRoom(s, c, 11, cz, 3);
        lamp(s, c, 14, cz + 1);
        lamp(s, c - 2, 14, cz + 2);
        // the study
        Gothic.bookcase(s, c - 3, 16, cz, "east", 3);
        Gothic.bookcase(s, c, 16, cz - 3, "south", 2);
        Furnish.desk(s, c + 2, 16, cz + 2, "west", Gothic.WOOD);
        s.set(c - 1, 16, cz + 2, Gothic.STAND).set(c - 1, 17, cz + 2, AMETHYST);
        Furnish.rug(s, c - 1, cz - 1, c + 1, cz + 1, 16, PURPLE_CARPET);
        lamp(s, c, 19, cz);
        lamp(s, c + 2, 19, cz - 2);
        // the observatory: a great telescope on its stand, star charts, an armillary of amethyst
        s.set(c, 21, cz - 1, Gothic.STAND).set(c, 22, cz - 1, Gothic.STAND);
        s.set(c, 23, cz - 1, "minecraft:lightning_rod[facing=north,powered=false,waterlogged=false]");
        s.set(c, 23, cz, "minecraft:lightning_rod[facing=north,powered=false,waterlogged=false]");
        s.set(c - 3, 21, cz + 3, CARTOGRAPHY);
        s.set(c - 2, 21, cz + 3, Kit.topSlab(Gothic.WOOD_SLAB)).set(c - 2, 22, cz + 3, Furnish.candles(3));
        s.set(c + 2, 21, cz + 2, Gothic.STAND).set(c + 2, 22, cz + 2, AMETHYST).set(c + 2, 23, cz + 2, CLUSTER);
        s.set(c - 3, 21, cz - 2, END_ROD).set(c + 3, 21, cz + 1, END_ROD);
        Furnish.chair(s, c, 21, cz + 1, "north", Gothic.WOOD);
        lamp(s, c - 2, 26, cz);
        lamp(s, c + 2, 26, cz);
        lamp(s, c, 26, cz + 2);

        // the library in the nave
        for (int side = -1; side <= 1; side += 2) {
            int x = side < 0 ? nx1 + 1 : nx2 - 1;
            String facing = side < 0 ? "east" : "west";
            Gothic.bookcase(s, x, 1, 13, facing, 3);
            Gothic.bookcase(s, x, 1, 19, facing, 3);
            Gothic.candelabrum(s, x, 1, sz - 1);
        }
        s.set(c, 1, nz1 + 2, LECTERN);
        Furnish.longTable(s, c, 14, c, 18, 1, Gothic.WOOD);
        Furnish.bench(s, c - 1, 14, c - 1, 18, 1, "east", Gothic.WOOD);
        Furnish.bench(s, c + 1, 14, c + 1, 18, 1, "west", Gothic.WOOD);
        s.set(c, 2, 14, Furnish.candles(3)).set(c, 2, 16, Furnish.candles(2)).set(c, 2, 18, Furnish.candles(3));
        for (int z : new int[]{12, 16, 20}) {
            Gothic.corona(s, c, z, 10, 3);
        }
        for (int z = 12; z <= 20; z += 4) {
            s.set(c, 12, z, Kit.lantern());
        }

        // the slim towers: stores of herbs, the rooms of the mages, a study, the lookouts
        for (int t = 0; t < 2; t++) {
            int sx = sxs[t];
            int side = t == 0 ? -1 : 1;
            int lx = sx + 2 * side;
            ladder(s, lx, sz - 2, 1, 16, t == 0 ? "east" : "west");
            // the store
            Furnish.sack(s, sx - 2 * side, 1, sz + 2, false);
            Furnish.sack(s, sx - side, 1, sz + 2, true);
            Furnish.crate(s, sx, 1, sz + 2, Gothic.WOOD);
            s.set(sx + 2 * side, 1, sz + 2, CAULDRON);
            Furnish.plant(s, sx + 2 * side, 1, sz - 1, "azalea");
            lamp(s, sx, 4, sz);
            // the room of two mages
            Gothic.bed(s, sx - 1, 6, sz + 2, "north", 1);
            Gothic.bed(s, sx + 1, 6, sz + 2, "north", -1);
            Furnish.rug(s, sx - 1, sz - 1, sx + 1, sz, 6, PURPLE_CARPET);
            lamp(s, sx, 9, sz - 1);
            // the study
            Gothic.bookcase(s, sx, 11, sz - 2, "south", 2);
            Furnish.desk(s, sx - 2 * side, 11, sz + 1, t == 0 ? "west" : "east", Gothic.WOOD);
            lamp(s, sx, 14, sz);
            // the lookout
            s.set(sx, 16, sz, Gothic.STAND).set(sx, 17, sz, "minecraft:lightning_rod[facing=south,powered=false,waterlogged=false]");
            Furnish.chair(s, sx - side, 16, sz + 1, "south", Gothic.WOOD);
            lamp(s, sx, 21, sz);
        }
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, nz2 - 1}, null);
    }

    // ================================================================ shared parts

    /**
     * The walls of a rectangle from layer 0 to {@code top}: rough stone on layer 0 (the plinth), the wall above.
     */
    private static void walls(Structure s, int x1, int z1, int x2, int z2, int top) {
        for (int y = 0; y <= top; y++) {
            s.walls(x1, y, z1, x2, y, z2, y == 0 ? Gothic.ROUGH : Gothic.WALL);
        }
    }

    /**
     * The crowning of a square tower of half-size {@code r} around (cx, cz) whose walls stand up to {@code top - 1} and on
     * which a needle spire of half-size {@code r} begins at {@code top}: a cornice of hanging trim stairs along its sides
     * one block out, a gargoyle under each corner and a pinnacle on each corner beside the foot of the spire.
     */
    private static void towerTop(Structure s, int cx, int cz, int r, int top) {
        Gothic.cornice(s, cx - r, cz - r - 1, cx + r, cz - r - 1, top - 1, "north");
        Gothic.cornice(s, cx - r, cz + r + 1, cx + r, cz + r + 1, top - 1, "south");
        Gothic.cornice(s, cx - r - 1, cz - r, cx - r - 1, cz + r, top - 1, "west");
        Gothic.cornice(s, cx + r + 1, cz - r, cx + r + 1, cz + r, top - 1, "east");
        for (int dx = -r; dx <= r; dx += 2 * r) {
            for (int dz = -r; dz <= r; dz += 2 * r) {
                Gothic.pinnacle(s, cx + dx, top, cz + dz, 3);
                // the gargoyle looks to the front or the back, or to the side where there is no room for its head
                int hz = cz + dz + 2 * Integer.signum(dz);
                int hx = cx + dx + 2 * Integer.signum(dx);
                if (s.inside(cx + dx, top - 2, hz)) {
                    Gothic.gargoyle(s, cx + dx, top - 2, cz + dz + Integer.signum(dz), dz < 0 ? "north" : "south");
                } else if (s.inside(hx, top - 2, cz + dz)) {
                    Gothic.gargoyle(s, cx + dx + Integer.signum(dx), top - 2, cz + dz, dx < 0 ? "west" : "east");
                }
            }
        }
    }

    /**
     * A slender needle spire of eight sides standing on layer {@code y} around (cx, cz), {@code height} layers with its
     * cross, all of the spire: a foot of an octagon of half-size {@code r} (2 or 3) two layers high with its rim sloping in,
     * for half-size 3 a ring of half-size 2 three layers high over it, a shaft of a cross of half-size 1 with its arms
     * sloping in at the top, a needle of single blocks and an iron cross. With {@code lucarnes} four lucarnes of purple glass
     * under little gables of the spire look out of it: for a foot of half-size 2 on the cornice around the foot, for one of
     * half-size 3 on the foot itself, before the ring over it. Thinner than the needle spire of the style, so that the
     * towers of the guilds end in needles even when the height of a building leaves little room.
     *
     * @return the layer above the cross
     */
    private static int spire(Structure s, int cx, int y, int cz, int r, int height, boolean lucarnes) {
        int layer = y;
        for (int ring = r; ring >= 2; ring--) {
            int rise = ring == r ? 2 : 3;
            for (int j = 0; j < rise; j++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    for (int dx = -ring; dx <= ring; dx++) {
                        if (Gothic.inOctagon(dx, dz, ring)) {
                            boolean rim = !Gothic.inOctagon(dx, dz, ring - 1);
                            put(s, cx + dx, layer, cz + dz, j == rise - 1 && rim ? slope(dx, dz, ring) : Gothic.SPIRE.block());
                        }
                    }
                }
                layer++;
            }
        }
        int rest = height - (layer - y) - 3;
        int shaft = Math.max(2, rest / 2);
        for (int j = 0; j < shaft; j++) {
            for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                boolean arm = d[0] != 0 || d[1] != 0;
                put(s, cx + d[0], layer, cz + d[1], j == shaft - 1 && arm ? slope(d[0], d[1], 1) : Gothic.SPIRE.block());
            }
            layer++;
        }
        for (int i = 0; i < Math.max(2, rest - shaft); i++) {
            put(s, cx, layer++, cz, Gothic.SPIRE.block());
        }
        put(s, cx, layer, cz, Gothic.IRON);
        put(s, cx, layer + 1, cz, Gothic.IRON);
        put(s, cx - 1, layer + 1, cz, Gothic.IRON);
        put(s, cx + 1, layer + 1, cz, Gothic.IRON);
        put(s, cx, layer + 2, cz, Gothic.IRON);
        int ly = r == 2 ? y : y + 2;
        for (String facing : lucarnes ? new String[]{"north", "south", "west", "east"} : new String[0]) {
            int[] d = Furnish.step(facing);
            int[] a = Furnish.step(Furnish.left(facing));
            String plus = Furnish.left(facing);
            int lx = cx + 3 * d[0];
            int lz = cz + 3 * d[1];
            put(s, lx, ly, lz, Gothic.VIOLET);
            put(s, lx + a[0], ly, lz + a[1], Gothic.SPIRE.block());
            put(s, lx - a[0], ly, lz - a[1], Gothic.SPIRE.block());
            put(s, lx - a[0], ly + 1, lz - a[1], Kit.stairs(Gothic.SPIRE.stairs(), plus));
            put(s, lx + a[0], ly + 1, lz + a[1], Kit.stairs(Gothic.SPIRE.stairs(), Furnish.opposite(plus)));
            put(s, lx, ly + 1, lz, Gothic.SPIRE.block());
            put(s, lx, ly + 2, lz, Kit.slab(Gothic.SPIRE.slab()));
        }
        return layer + 3;
    }

    /**
     * A stair of the spire on the rim of an octagon of half-size {@code r}, its high side to the middle, turned at the
     * corners of the octagon.
     */
    private static String slope(int dx, int dz, int r) {
        boolean outX = dx != 0 && !Gothic.inOctagon(dx + Integer.signum(dx), dz, r);
        boolean outZ = dz != 0 && !Gothic.inOctagon(dx, dz + Integer.signum(dz), r);
        String block = Gothic.SPIRE.stairs();
        if (outX && outZ) {
            return Kit.stairs(block, dz > 0 ? "north" : "south", dz > 0 == dx > 0 ? "outer_left" : "outer_right");
        }
        if (outX || !outZ && Math.abs(dx) >= Math.abs(dz)) {
            return Kit.stairs(block, dx > 0 ? "west" : "east");
        }
        return Kit.stairs(block, dz > 0 ? "north" : "south");
    }

    /**
     * One layer of an octagon of half-size {@code r} around (cx, cz), filled with {@code state}: the floor or the clearing
     * of an eight-sided storey.
     */
    private static void octagonFloor(Structure s, int cx, int y, int cz, int r, String state) {
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Gothic.inOctagon(dx, dz, r)) {
                    put(s, cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /**
     * A cornice round an eight-sided tower of half-size {@code r} around (cx, cz): hanging trim stairs one block out of its
     * walls on layer {@code y}, all round.
     */
    private static void octagonCornice(Structure s, int cx, int y, int cz, int r) {
        for (int dz = -r - 1; dz <= r + 1; dz++) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                if (Gothic.inOctagon(dx, dz, r + 1) && !Gothic.inOctagon(dx, dz, r) && s.inside(cx + dx, y, cz + dz)
                        && !s.solid(cx + dx, y, cz + dz)) {
                    String in = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
                    s.set(cx + dx, y, cz + dz, Kit.upside(Gothic.TRIM_STAIRS, in));
                }
            }
        }
    }

    /**
     * A lamp post of a yard standing on layer {@code y}: a block of tracery, a black post two high and a soul lantern on it.
     */
    private static void lampPost(Structure s, int x, int y, int z) {
        put(s, x, y, z, Gothic.TRACERY);
        put(s, x, y + 1, z, Gothic.STAND);
        put(s, x, y + 2, z, Gothic.STAND);
        put(s, x, y + 3, z, Gothic.SOUL_LANTERN);
    }

    /**
     * Two banners of the cloth of the kingdom hanging on a wall that looks {@code facing}, in the cells (x1, y, z) and
     * (x2, y, z) before it, beside a doorway; a cell that is taken is left as it is.
     */
    private static void banners(Structure s, int x1, int x2, int y, int z, String facing) {
        for (int x : new int[]{x1, x2}) {
            if (s.inside(x, y, z) && !s.solid(x, y, z)) {
                s.set(x, y, z, Kit.wallBanner("red", facing));
            }
        }
    }

    /**
     * Soul lanterns standing in the corners of the walk inside a crown around (cx, cz), {@code r} blocks out from its
     * middle, on layer {@code y}, so that no dark corner is left beside the foot of the spire.
     */
    private static void walkLamps(Structure s, int cx, int cz, int r, int y) {
        for (int dx = -r; dx <= r; dx += 2 * r) {
            for (int dz = -r; dz <= r; dz += 2 * r) {
                if (s.inside(cx + dx, y, cz + dz) && !s.solid(cx + dx, y, cz + dz)) {
                    s.set(cx + dx, y, cz + dz, Gothic.SOUL_LANTERN);
                }
            }
        }
    }

    /**
     * The glowing tips of a crown of mages: an end rod on the point of each great pinnacle at the corners of a crown laid
     * on layer {@code y} over the walls from (x1, z1) to (x2, z2).
     */
    private static void glowingTips(Structure s, int x1, int z1, int x2, int z2, int y) {
        for (int x : new int[]{x1, x2}) {
            for (int z : new int[]{z1, z2}) {
                put(s, x, y + 7, z, END_ROD);
            }
        }
    }

    /**
     * Clears a box of air: the inside of a tower through the roofs drawn before it.
     */
    private static void clear(Structure s, int x1, int y1, int z1, int x2, int y2, int z2) {
        s.fill(x1, y1, z1, x2, y2, z2, AIR);
    }

    /**
     * A ladder looking {@code facing} (it hangs on the block behind it) in the column (x, z) from {@code y1} to
     * {@code y2}, through the floors it meets.
     */
    private static void ladder(Structure s, int x, int z, int y1, int y2, String facing) {
        s.fill(x, y1, z, x, y2, z, Kit.ladder(facing));
    }

    /**
     * A lantern hanging at (x, y, z) on a chain up to the first solid block over it: a beam, a floor or a vault.
     */
    private static void lamp(Structure s, int x, int y, int z) {
        s.set(x, y, z, Kit.hangingLantern());
        for (int yy = y + 1; yy < s.sizeY && !s.solid(x, yy, z); yy++) {
            s.set(x, yy, z, Kit.chain());
        }
    }

    /**
     * Sets a block where the position lies inside the structure; a shape that reaches over its edge is cut there.
     */
    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }
}
