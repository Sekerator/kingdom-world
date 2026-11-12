package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The two guilds of a capital in the style "nordic", each a program drawn for the rank of its kingdom, from B for a weak one
 * to SS for a strong one.
 *
 * <p>The adventurers' guild is a mead hall of the bold: a longhouse of dark staves on a plinth of rough stone, its long walls
 * bowing out like the hull of a ship under a curved roof of shingles with dragon heads at the ends of its ridge, its gables
 * painted, a carved porch under its own little gable at the door, a row of painted shields along its walls like those
 * along the side of a ship and windows in white frames. Inside the great hall a long hearth, feast boards with benches, the
 * counter of the registrar with the guild desk, his post and his chest, the contract board, trophies of beasts and
 * weapons, and in the loft the map room of the guild. The rank grows it: B is the hall with its loft; A adds a watch tower
 * whose lookout under a gable with dragon heads stands over the roof; S a greater hall, a taller tower, an armoury under
 * turf and a forecourt for training behind a low wall; SS is a guild court, the great hall with the high seat of the
 * master across the back, two wings of two storeys with galleries round the court, and a gate tower whose roofs stack up
 * like those of a stave church (two skirts, a lookout under crossing gables with dragon heads, a needle), one of the main
 * buildings of a capital after the castle.
 *
 * <p>The mages' guild lives in a seer's tower: a slim tower of staves on a base of rough stone, its storeys under skirts of
 * shingles, a lantern room of pale blue glass lit with soul fire, and over it a crown of two steep gables crossing, painted,
 * with crossed boards and dragon heads, and a slender needle at the crossing, so that its roofs stack up like those of a
 * stave church; soul lanterns hang under the corners of its skirts and its crown, rune stones with soul lanterns stand
 * before its door. On its ground floor the magic focus with the post of the mage and its chest; the library, the
 * brewing room, the research room and the beds of the mages above: one in B, two in A, three in S and four in SS. A grows
 * the tower and gives it a carved porch; S adds a hall of staves under a steep roof with dragon heads for the library and
 * the brewing room; SS is a cluster: the high main tower with an observatory in its lantern and a gallery round its foot,
 * and two lower towers joined to it by covered walks.
 *
 * <p>Drawn with the shapes of {@link Nordic}; coordinates as everywhere: x from west to east, z from the back to the front
 * (the entrance looks south), y up; layer 0 stands on the ground and is the street.
 */
final class NordicGuilds {
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
    private static final String BOOKS = "minecraft:bookshelf";
    private static final String STAVE = Kit.log(Nordic.STAVE, "y");
    private static final String POST = Kit.log(Nordic.LOG, "y");
    /** The guild's own colour on its banners, the same in every kingdom. */
    private static final String GUILD = "black";
    /** A round shield painted in rings, hung on the walls of the hall like shields along the side of a ship. */
    private static final String SHIELD = "minecraft:target";
    /** The pale blue glass of the lantern rooms of the seers. */
    private static final String RUNE_GLASS = Kit.stainedPane("light_blue");
    private static final String SOUL_LANTERN = "minecraft:soul_lantern[hanging=false,waterlogged=false]";
    private static final String SOUL_HANGING = "minecraft:soul_lantern[hanging=true,waterlogged=false]";
    /** A fire burning in a hearth or a brazier. */
    private static final String FIRE = "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]";
    /** A drinking horn lying on a board along x, and one lying along z. */
    private static final String HORN_WE = "minecraft:lightning_rod[facing=east,powered=false,waterlogged=false]";
    private static final String HORN_NS = "minecraft:lightning_rod[facing=south,powered=false,waterlogged=false]";
    /** The head of a spear standing in its rack. */
    private static final String SPEAR = "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]";
    /** Rough stone as the blocks of a roof, for the sloping skirts of plinths. */
    private static final Kit.Roof ROUGH = new Kit.Roof(Nordic.FOOTING, Nordic.FOOTING_STAIRS, Nordic.FOOTING_SLAB);

    /**
     * A drawn building with its named places: the door, and the guards by the desk.
     */
    private record Drawn(Structure structure, int[] door, List<int[]> guards) {
    }

    private NordicGuilds() {
    }

    /**
     * The adventurers' guilds nordic_guild_b, _a, _s and _ss (trade, type guild, with their door and the guard by the desk)
     * and the mage towers nordic_mage_tower_b, _a, _s and _ss (production, role mage_tower, with their door), all standing
     * on the ground with their anchor in the middle of their front edge.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Rank rank : Rank.values()) {
            String key = rank.name().toLowerCase(Locale.ROOT);
            Drawn guild = guild(rank);
            Structure g = guild.structure();
            BuildingGenerator.Building b = new BuildingGenerator.Building("nordic_guild_" + key, "trade", "guild", "kingdomworld:guild_desk",
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
            list.add(new BuildingGenerator.Building("nordic_mage_tower_" + key, "production", "mage_tower", "kingdomworld:magic_focus",
                    (t.sizeX - 1) / 2, 0, t.sizeZ - 1, t, "mage_tower", 1, "")
                    .at("door", tower.door()[0], tower.door()[1], tower.door()[2]));
        }
        return list;
    }

    // ================================================================ the adventurers' guild

    /** The floor of the halls on their plinth of stone, and the layer one stands on in them. */
    private static final int FLOOR = 1;
    private static final int STAND = 2;

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
     * Rank B: a longhouse of staves with its painted gable to the street, its walls bowing out like a hull, a curved roof of
     * shingles with dragon heads at both ends of its ridge and a smoke louvre over the hearth, a carved porch with its own
     * gable before the door, a row of shields on each long wall, windows in white frames, the black banner of the guild on a
     * pole by the porch. Inside: the long hearth between two feast boards, the counter of the registrar across the back of
     * the hall with the desk, his post and the chest, shelves of books and skulls behind it, the contract board and the
     * trophies by the door; a ladder climbs to the loft over the counter, the map room of the guild with its lectern, map
     * table and the map of the lands painted on the gable.
     */
    private static Drawn guildB() {
        Structure s = new Structure(15, 20, 19);
        int c = 7;
        int a1 = 4;
        int a2 = 14;
        int end = 4;
        int mid = 5;
        int top = FLOOR + 5;
        int ridge = hall(s, a1, a2, c, end, mid, 5, false);
        Nordic.louvre(s, c, ridge, 11, false, Nordic.ROOF);
        Nordic.portal(s, c, STAND, a2, "south", true, Nordic.ROOF, Nordic.BOARD, null);
        porchSteps(s, c, a2 + 3);
        paintGable(s, c - 8, a1, c + 8, a1, top);
        paintGable(s, c - 8, a2, c + 8, a2, top);
        Nordic.window(s, c - 5, 3, 7, "west", 2, true);
        Nordic.window(s, c + 5, 3, 7, "east", 2, true);
        shields(s, c, a1, a2, end, mid, 4, 9, 11, true, true);
        Nordic.window(s, c, 9, a2, "south", 2, true);
        Nordic.window(s, c, 10, a1, "north", 2, true);
        flag(s, 1, a2 + 3, 8);

        // the back of the hall under the loft: the counter, the post and the chest, books and skulls
        loft(s, c, a1, a2, end, mid, a1 + 1, a1 + 4, top);
        s.fill(c, STAND, a1 + 1, c, top, a1 + 1, Kit.ladder("south"));
        Furnish.books(s, c - 3, a1 + 1, c - 2, a1 + 1, STAND, STAND + 1);
        Furnish.books(s, c + 2, a1 + 1, c + 3, a1 + 1, STAND, STAND + 1);
        counter(s, c, STAND, a1 + 4, 2);
        s.set(c - 1, STAND, a1 + 2, WORK_POST);
        s.set(c - 2, STAND, a1 + 2, Kit.chest("south"));
        s.set(c + 3, STAND, a1 + 2, grindstone("south"));
        skull(s, c - 3, STAND + 2, a1 + 1, "south", true);
        skull(s, c + 3, STAND + 2, a1 + 1, "south", false);
        s.set(c - 3, top - 1, a1 + 3, Kit.hangingLantern()).set(c + 3, top - 1, a1 + 3, Kit.hangingLantern());

        // the hall: the long hearth between the feast boards, the contract board and the trophies by the door
        Nordic.longHearth(s, c - 1, 10, c + 1, 12, STAND, ceiling(s, c, STAND, 11));
        Nordic.feastBoard(s, c - 3, 10, c - 3, 12, STAND);
        Nordic.feastBoard(s, c + 3, 10, c + 3, 12, STAND);
        contractBoard(s, c - 2, STAND, a2, "north", 2);
        s.set(c + 3, STAND, a2 - 1, grindstone("north"));
        skull(s, c + 2, STAND + 2, a2 - 1, "north", false);
        skull(s, c + 3, STAND + 2, a2 - 1, "north", true);
        s.set(c, STAND + 3, a2 - 1, "minecraft:dragon_wall_head[facing=north]");
        lamp(s, c - 3, top - 1, 9);
        lamp(s, c + 3, top - 1, 9);
        lamp(s, c - 3, top - 1, 13);
        lamp(s, c + 3, top - 1, 13);

        // the loft: the map room
        int up = top + 1;
        guildMap(s, c - 2, up + 1, a1);
        s.set(c - 2, up, a1 + 2, Kit.stairs(Nordic.FLOOR_STAIRS, "south")).set(c - 2, up, a1 + 1, lectern("south"));
        s.set(c + 2, up, a1 + 2, CARTOGRAPHY).set(c + 2, up, a1 + 3, Kit.stairs(Nordic.FLOOR_STAIRS, "south"));
        Furnish.books(s, c - 3, a1 + 2, c - 3, a1 + 2, up, up + 1);
        Furnish.books(s, c + 3, a1 + 2, c + 3, a1 + 2, up, up + 1);
        Nordic.furs(s, c - 1, a1 + 3, c + 1, a1 + 3, up);
        lamp(s, c, up + 3, a1 + 3);
        s.set(c + 2, up, a1 + 1, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c + 2, up + 1, a1 + 1, Furnish.candles(2));
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, STAND, a2 + 1}, List.of(new int[]{c + 2, STAND, a1 + 3}));
    }

    /**
     * Rank A: the hall of B grown longer and wider, and beside its front on the east the watch tower of the guild: a shaft
     * of staves on a base of rough stone, its lookout jutting out over brackets with an open rail all round under a steep
     * gable with crossed boards and dragon heads, the banner of the guild under its gable; a covered way from a side door of
     * the hall leads into it. The hall inside like B with a longer hearth and longer boards; in the tower the armoury and
     * the watch room.
     */
    private static Drawn guildA() {
        Structure s = new Structure(21, 28, 25);
        int c = 7;
        int a1 = 5;
        int a2 = 19;
        int end = 4;
        int mid = 6;
        int top = FLOOR + 5;
        int ridge = hall(s, a1, a2, c, end, mid, 5, false);
        Nordic.louvre(s, c, ridge, 13, false, Nordic.ROOF);
        Nordic.portal(s, c, STAND, a2, "south", true, Nordic.ROOF, Nordic.BOARD, null);
        porchSteps(s, c, a2 + 3);
        paintGable(s, c - 8, a1, c + 8, a1, top);
        paintGable(s, c - 8, a2, c + 8, a2, top);
        Nordic.window(s, c - 5, 3, 7, "west", 2, true);
        Nordic.window(s, c + 5, 3, 7, "east", 2, true);
        shields(s, c, a1, a2, end, mid, 4, 10, 15, true, true);
        Nordic.window(s, c, 9, a2, "south", 2, true);
        Nordic.window(s, c, 10, a1, "north", 2, true);
        flag(s, 1, a2 + 3, 9);

        // the watch tower and the covered way to it
        int tx = 16;
        int tz = 17;
        int lf = watchTower(s, tx, tz, 2, FLOOR, 12, false);
        s.set(13, 0, tz, Nordic.FOOTING).set(13, FLOOR, tz, Nordic.FLOOR);
        s.door(12, STAND, tz, Nordic.DOOR, "west", "left");
        s.door(14, STAND, tz, Nordic.DOOR, "east", "left");
        s.set(13, STAND + 3, tz, Kit.hangingLantern());
        s.set(tx + 1, STAND, tz - 1, grindstone("west")).set(tx + 1, STAND, tz + 1, grindstone("west"));
        Furnish.crate(s, tx - 1, STAND, tz + 1, "spruce");
        skull(s, tx + 1, STAND + 2, tz, "west", true);
        Furnish.bench(s, tx + 1, tz - 1, tx + 1, tz + 1, 8, "west", "spruce");
        s.set(tx + 1, lf - 1, tz + 1, Kit.hangingLantern());

        // the back of the hall under the loft
        loft(s, c, a1, a2, end, mid, a1 + 1, a1 + 4, top);
        s.fill(c, STAND, a1 + 1, c, top, a1 + 1, Kit.ladder("south"));
        Furnish.books(s, c - 4, a1 + 1, c - 3, a1 + 1, STAND, STAND + 1);
        Furnish.books(s, c + 3, a1 + 1, c + 4, a1 + 1, STAND, STAND + 1);
        counter(s, c, STAND, a1 + 4, 2);
        s.set(c - 1, STAND, a1 + 2, WORK_POST);
        s.set(c - 2, STAND, a1 + 2, Kit.chest("south"));
        s.set(c + 4, STAND, a1 + 3, grindstone("west"));
        skull(s, c - 4, STAND + 2, a1 + 1, "south", true);
        skull(s, c + 4, STAND + 2, a1 + 1, "south", false);
        s.set(c - 3, top - 1, a1 + 3, Kit.hangingLantern()).set(c + 3, top - 1, a1 + 3, Kit.hangingLantern());

        // the hall
        Nordic.longHearth(s, c - 1, 11, c + 1, 16, STAND, ceiling(s, c, STAND, 13));
        Nordic.feastBoard(s, c - 3, 11, c - 3, 15, STAND);
        Nordic.feastBoard(s, c + 3, 11, c + 3, 15, STAND);
        contractBoard(s, c - 2, STAND, a2, "north", 2);
        s.set(c + 3, STAND, a2 - 1, grindstone("north"));
        skull(s, c + 2, STAND + 2, a2 - 1, "north", false);
        skull(s, c + 3, STAND + 2, a2 - 1, "north", true);
        s.set(c, STAND + 3, a2 - 1, "minecraft:dragon_wall_head[facing=north]");
        Nordic.tapestry(s, c - 5, 11, c - 5, 15, 5, "east");
        for (int z : new int[]{10, 17}) {
            lamp(s, c - 3, top - 1, z);
            lamp(s, c + 3, top - 1, z);
        }

        // the loft: the map room
        int up = top + 1;
        guildMap(s, c - 2, up + 1, a1);
        s.set(c - 2, up, a1 + 2, Kit.stairs(Nordic.FLOOR_STAIRS, "south")).set(c - 2, up, a1 + 1, lectern("south"));
        s.set(c + 2, up, a1 + 1, CARTOGRAPHY).set(c + 2, up, a1 + 2, Kit.stairs(Nordic.FLOOR_STAIRS, "south"));
        Furnish.books(s, c - 3, a1 + 2, c - 3, a1 + 2, up, up + 1);
        Furnish.books(s, c + 3, a1 + 2, c + 3, a1 + 2, up, up + 1);
        Nordic.furs(s, c - 1, a1 + 3, c + 1, a1 + 3, up);
        lamp(s, c, up + 3, a1 + 3);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, STAND, a2 + 1}, List.of(new int[]{c + 2, STAND, a1 + 3}));
    }

    /**
     * Rank S: a greater longhouse with a carved porch carrying its own dragon, a taller watch tower beside it on the east
     * whose lookout stands high over the roofs, before the tower an armoury under a roof of turf, and before them all a
     * forecourt for training behind a low wall of rough stone with a gate between posts with lanterns: dummies, a target and
     * braziers. Inside the hall: the counter under a loft with the map room, the long hearth, two long feast boards,
     * tapestries over the shields, the contract board and trophies; in the tower the armoury, a chamber of honour with a bed
     * of state and the watch room.
     */
    private static Drawn guildS() {
        Structure s = new Structure(27, 33, 29);
        int c = 10;
        int a1 = 5;
        int a2 = 21;
        int end = 5;
        int mid = 7;
        int top = FLOOR + 6;
        int ridge = hall(s, a1, a2, c, end, mid, 6, false);
        Nordic.louvre(s, c, ridge, 15, false, Nordic.ROOF);
        Nordic.portal(s, c, STAND, a2, "south", true, Nordic.ROOF, Nordic.BOARD, Nordic.SPIRE);
        porchSteps(s, c, a2 + 3);
        paintGable(s, c - 9, a1, c + 9, a1, top);
        paintGable(s, c - 9, a2, c + 9, a2, top);
        Nordic.window(s, c - 6, 3, 8, "west", 2, true);
        Nordic.window(s, c - 6, 3, 19, "west", 2, true);
        Nordic.window(s, c + 6, 3, 19, "east", 2, true);
        shields(s, c, a1, a2, end, mid, 4, 10, 17, true, false);
        shields(s, c, a1, a2, end, mid, 4, 13, 17, false, true);
        Nordic.window(s, c, 10, a2, "south", 2, true);
        Nordic.window(s, c, 11, a1, "north", 2, true);

        // the watch tower, joined to the hall by a door through both walls
        int tx = 21;
        int tz = 9;
        int lf = watchTower(s, tx, tz, 3, FLOOR, 14, false);
        s.set(17, STAND, 10, AIR).set(17, STAND + 1, 10, AIR);
        s.door(18, STAND, 10, Nordic.DOOR, "east", "left");
        s.set(tx + 2, STAND, tz - 1, grindstone("west")).set(tx + 2, STAND, tz + 1, grindstone("west"));
        s.set(tx + 2, STAND, tz + 2, "minecraft:anvil[facing=north]");
        skull(s, tx + 2, STAND + 2, tz, "west", true);
        Furnish.crate(s, tx - 2, STAND, tz + 2, "spruce");
        // the chamber of honour and the watch room
        Furnish.canopyBed(s, tx - 1, 8, tz + 1, "north", "spruce");
        Nordic.furs(s, tx + 1, tz, tx + 2, tz + 2, 8);
        Furnish.bench(s, tx + 2, tz - 1, tx + 2, tz + 1, 13, "west", "spruce");
        s.set(tx - 2, 13, tz + 2, Kit.topSlab(Nordic.FLOOR_SLAB)).set(tx - 2, 14, tz + 2, Furnish.candles(2));
        s.set(tx + 2, lf - 1, tz + 2, Kit.hangingLantern());

        // the armoury under turf before the tower
        int ax1 = 19;
        int ax2 = 25;
        int az1 = 15;
        int az2 = 21;
        s.fill(ax1, 0, az1, ax2, 0, az2, Nordic.FOOTING);
        Nordic.footing(s, ax1, az1, ax2, az2, FLOOR);
        Nordic.staveWalls(s, ax1, STAND, az1, ax2, 5, az2);
        Nordic.turfRoof(s, ax1, az1, ax2, az2, 5, false, STAVE);
        s.door(22, STAND, az2, Nordic.DOOR, "north", "left");
        s.set(22, 0, az2 + 1, Nordic.FOOTING).set(22, 1, az2 + 1, Kit.stairs(Nordic.FOOTING_STAIRS, "north"));
        s.set(22, 0, az2 + 2, Kit.stairs(Nordic.FOOTING_STAIRS, "north"));
        Nordic.window(s, ax2, 3, 18, "east", 1, true);
        s.set(ax1 + 1, STAND + 1, az2, SHIELD).set(ax2 - 1, STAND + 1, az2, SHIELD);
        s.set(ax2 - 1, STAND, az1 + 1, grindstone("west")).set(ax2 - 1, STAND, az1 + 3, grindstone("west"));
        s.set(ax1 + 1, STAND, az1 + 1, "minecraft:anvil[facing=north]");
        Furnish.crate(s, ax1 + 1, STAND, az1 + 3, "spruce");
        s.set(ax1 + 1, STAND, az1 + 4, "minecraft:hay_block[axis=y]");
        s.set(ax2 - 1, STAND, az2 - 1, SHIELD);
        lamp(s, 22, 5, 18);

        // the forecourt: a low wall with a gate, dummies, a target and braziers
        for (int x = 0; x < s.sizeX; x++) {
            if (x >= 8 && x <= 14) {
                continue;
            }
            s.set(x, 0, 28, Nordic.FOOTING).set(x, 1, 28, Kit.ROUGH_FENCE);
        }
        for (int x : new int[]{8, 14}) {
            s.fill(x, 0, 28, x, 2, 28, Nordic.FOOTING);
            s.set(x, 3, 28, Kit.lantern());
        }
        dummy(s, 3, 24);
        dummy(s, 5, 26);
        s.set(2, 0, 26, Nordic.FENCE).set(2, 1, 26, SHIELD);
        brazier(s, 7, 24);
        brazier(s, 13, 24);
        flag(s, 17, 24, 10);

        // the back of the hall under the loft
        loft(s, c, a1, a2, end, mid, a1 + 1, a1 + 5, top);
        s.fill(c, STAND, a1 + 1, c, top, a1 + 1, Kit.ladder("south"));
        Furnish.books(s, c - 4, a1 + 1, c - 2, a1 + 1, STAND, STAND + 1);
        Furnish.books(s, c + 2, a1 + 1, c + 4, a1 + 1, STAND, STAND + 1);
        counter(s, c, STAND, a1 + 5, 2);
        s.set(c - 1, STAND, a1 + 3, WORK_POST);
        s.set(c - 2, STAND, a1 + 3, Kit.chest("south"));
        s.set(c + 5, STAND, a1 + 3, grindstone("west")).set(c - 5, STAND, a1 + 3, grindstone("east"));
        skull(s, c - 4, STAND + 2, a1 + 1, "south", true);
        skull(s, c + 4, STAND + 2, a1 + 1, "south", false);
        s.set(c - 4, top - 1, a1 + 4, Kit.hangingLantern()).set(c + 4, top - 1, a1 + 4, Kit.hangingLantern());

        // the hall
        Nordic.longHearth(s, c - 1, 12, c + 1, 18, STAND, ceiling(s, c, STAND, 15));
        Nordic.feastBoard(s, c - 4, 12, c - 4, 18, STAND);
        Nordic.feastBoard(s, c + 4, 12, c + 4, 18, STAND);
        contractBoard(s, c - 2, STAND, a2, "north", 2);
        s.set(c + 3, STAND, a2 - 1, grindstone("north"));
        skull(s, c + 2, STAND + 2, a2 - 1, "north", false);
        skull(s, c + 3, STAND + 2, a2 - 1, "north", true);
        s.set(c, STAND + 3, a2 - 1, "minecraft:dragon_wall_head[facing=north]");
        Nordic.tapestry(s, c - 6, 12, c - 6, 17, 6, "east");
        Nordic.tapestry(s, c + 6, 13, c + 6, 17, 6, "west");
        for (int z : new int[]{11, 15, 19}) {
            lamp(s, c - 4, top - 1, z);
            lamp(s, c + 4, top - 1, z);
        }

        // the loft: the map room
        int up = top + 1;
        guildMap(s, c - 2, up + 1, a1);
        s.set(c - 2, up, a1 + 2, Kit.stairs(Nordic.FLOOR_STAIRS, "south")).set(c - 2, up, a1 + 1, lectern("south"));
        s.set(c + 2, up, a1 + 1, CARTOGRAPHY).set(c + 2, up, a1 + 2, Kit.stairs(Nordic.FLOOR_STAIRS, "south"));
        Furnish.books(s, c - 4, a1 + 2, c - 4, a1 + 3, up, up + 1);
        Furnish.books(s, c + 4, a1 + 2, c + 4, a1 + 3, up, up + 1);
        Nordic.furs(s, c - 1, a1 + 3, c + 1, a1 + 4, up);
        s.set(c + 3, up, a1 + 4, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c + 3, up + 1, a1 + 4, Furnish.candles(3));
        lamp(s, c, up + 3, a1 + 3);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, STAND, a2 + 1}, List.of(new int[]{c + 2, STAND, a1 + 4}));
    }

    /**
     * Rank SS: a guild court. Across the back the great hall, a longhouse along the street with dragon heads at both ends of
     * its ridge and a carved porch with its own dragon in the middle of its long wall; along the sides two wings of two
     * storeys under steep roofs whose dragons look into the street; galleries on posts round the court before the wings and
     * behind the front wall; in the middle of the front the gate tower, its roofs stacked in tiers (two skirts, the lookout
     * under two painted gables crossing with dragon heads to the west and the east, a needle at the crossing), the gate
     * passage through its foot and the banners of the guild beside it. In the court dummies, targets, a rune stone and
     * braziers. The great hall is a mead hall under tie beams hung with lanterns: the sunken hearth behind iron bars with
     * the cauldron over it, the high seat of the master between carved posts, four long tables with benches beside the
     * hearth and one across before the honour wall of trophies and the casks of mead, shields and cloth on the walls, furs
     * on the floor, the counter of the registrar with the desk, his post and the chest, and the board of contracts on its
     * east gable (see {@link #meadHall}); the west wing holds the armoury and above it the map room of the guild; the east
     * wing the kitchen and above it two chambers of honour with beds of state; the gate tower a passage under a ceiling of
     * beams with the benches of the gate guard, the guard room over the gate and two watch rooms (see {@link #gateRooms}).
     */
    private static Drawn guildSS() {
        Structure s = new Structure(35, 44, 37);
        // the court, paved, with a walk of boards from the gate to the hall
        for (int z = 17; z <= 30; z++) {
            for (int x = 8; x <= 26; x++) {
                s.set(x, 0, z, Math.abs(x - 17) <= 1 ? Nordic.FLOOR : Nordic.FOOTING);
            }
        }
        // the great hall along the street
        int a1 = 6;
        int a2 = 28;
        int c = 9;
        int end = 5;
        int mid = 7;
        int top = FLOOR + 6;
        int ridge = hall(s, a1, a2, c, end, mid, 6, true);
        Nordic.louvre(s, 17, ridge, c, true, Nordic.ROOF);
        Nordic.portal(s, 17, STAND, 16, "south", true, Nordic.ROOF, Nordic.BOARD, Nordic.SPIRE);
        for (int x = 16; x <= 18; x++) {
            s.set(x, 1, 19, Kit.stairs(Nordic.FOOTING_STAIRS, "north"));
        }
        paintGable(s, a1, c - 9, a1, c + 9, top);
        paintGable(s, a2, c - 9, a2, c + 9, top);
        Nordic.window(s, 13, 3, 16, "south", 2, true);
        Nordic.window(s, 24, 3, 15, "south", 2, true);
        Nordic.window(s, 14, 3, 2, "north", 2, true);
        Nordic.window(s, 20, 3, 2, "north", 2, true);
        Nordic.window(s, a1, 10, c, "west", 2, true);
        Nordic.window(s, a2, 10, c, "east", 2, true);
        s.set(15, 4, 17, Kit.wallBanner(GUILD, "south")).set(19, 4, 17, Kit.wallBanner(GUILD, "south"));

        // the wings
        int wing = 10;
        for (int x1 : new int[]{1, 27}) {
            int x2 = x1 + 6;
            house(s, x1, 19, x2, 31, FLOOR, wing, false, Nordic.SPIRE);
            s.fill(x1 + 1, 6, 20, x2 - 1, 6, 30, Nordic.FLOOR);
            paintGable(s, x1, 31, x2, 31, wing);
            paintGable(s, x1, 19, x2, 19, wing);
            int outer = x1 == 1 ? x1 : x2;
            String out = x1 == 1 ? "west" : "east";
            for (int z : new int[]{22, 28}) {
                Nordic.window(s, outer, 3, z, out, 2, true);
                Nordic.window(s, outer, 8, z, out, 1, true);
            }
            Nordic.window(s, x1 + 3, 12, 31, "south", 2, true);
            Nordic.window(s, x1 + 3, 3, 31, "south", 2, true);
            for (int z = 24; z <= 26; z++) {
                s.set(outer, 4, z, SHIELD);
            }
        }
        // the galleries round the court: before the wings and behind the front wall
        gallerySide(s, 8, 19, 9, 30, "west");
        gallerySide(s, 25, 19, 26, 30, "east");
        for (int x1 : new int[]{8, 21}) {
            int x2 = x1 + 5;
            s.fill(x1, 0, 31, x2, 0, 33, Nordic.FOOTING);
            s.fill(x1, FLOOR, 31, x2, FLOOR, 32, Nordic.FLOOR);
            for (int x = x1; x <= x2; x++) {
                s.set(x, FLOOR, 33, Kit.log(Nordic.LOG, "x"));
                s.fill(x, STAND, 33, x, 8, 33, STAVE);
                s.set(x, 9, 33, Kit.log(Nordic.LOG, "x"));
                s.set(x, 10, 33, Kit.slab(Nordic.ROOF.slab()));
            }
            for (int x = x1; x <= x2; x += 5) {
                s.fill(x, STAND, 33, x, 8, 33, POST);
            }
            s.set(x1 + 2, 5, 33, SHIELD).set(x1 + 3, 5, 33, SHIELD);
            s.set(x1 + 2, 5, 34, Kit.wallBanner(GUILD, "south"));
            colonnade(s, x1, 31, x2, 31, 6, "x");
            leanTo(s, x1, 31, x2, 32, 6, "south", Nordic.ROOF);
        }
        // the gate tower with its stacked roofs: the skirt over the gate, the lookout, the crossing gables and the needle
        int gx = 17;
        int gz = 32;
        groundStorey(s, gx, gz, 3, 0, 9, 3);
        skirt(s, gx, gz, 3, 9, 1);
        upperStorey(s, gx, gz, 3, 10, 15);
        skirt(s, gx, gz, 3, 15, 1);
        upperStorey(s, gx, gz, 3, 16, 21);
        int lf = lookout(s, gx, gz, 3, 21);
        int crest = crown(s, gx, gz, 4, lf + 4, "we", null);
        spike(s, gx, gz, crest, 6);
        Nordic.window(s, gx, 18, gz + 3, "south", 2, true);
        Nordic.window(s, gx - 3, 18, gz, "west", 1, true);
        Nordic.window(s, gx + 3, 18, gz, "east", 1, true);
        for (int z : new int[]{gz - 3, gz + 3}) {
            s.fill(gx - 1, 1, z, gx + 1, 4, z, AIR);
            s.fill(gx - 1, 5, z, gx + 1, 5, z, Kit.log(Nordic.LOG, "x"));
        }
        s.fill(gx - 1, 0, gz + 4, gx + 1, 0, gz + 4, Kit.slab(Nordic.FOOTING_SLAB));
        s.fill(gx - 1, 0, gz - 4, gx + 1, 0, gz - 4, Nordic.FLOOR);
        s.set(gx - 2, 4, gz + 4, Kit.wallBanner(GUILD, "south")).set(gx + 2, 4, gz + 4, Kit.wallBanner(GUILD, "south"));
        s.set(gx - 2, 6, gz + 4, Kit.wallBanner(GUILD, "south")).set(gx + 2, 6, gz + 4, Kit.wallBanner(GUILD, "south"));
        Nordic.window(s, gx, 12, gz + 3, "south", 2, true);
        Nordic.window(s, gx - 3, 12, gz, "west", 2, true);
        Nordic.window(s, gx + 3, 12, gz, "east", 2, true);
        gateRooms(s, gx, gz, lf);

        // the court
        dummy(s, 11, 22);
        dummy(s, 11, 26);
        s.set(23, 0, 22, Nordic.FOOTING).set(23, 1, 22, Nordic.FENCE).set(23, 2, 22, SHIELD);
        s.set(23, 0, 26, Nordic.FOOTING).set(23, 1, 26, Nordic.FENCE).set(23, 2, 26, SHIELD);
        runeStone(s, 21, 24);
        brazier(s, 14, 20);
        brazier(s, 20, 20);
        brazier(s, 12, 28);
        brazier(s, 22, 28);

        // the great hall, the armoury and the map room in the west wing, the kitchen and the chambers in the east wing
        meadHall(s);
        s.door(7, STAND, 25, Nordic.DOOR, "west", "left");
        s.fill(6, STAND, 20, 6, 6, 20, Kit.ladder("south"));
        armoury(s);
        mapRoom(s);
        s.door(27, STAND, 25, Nordic.DOOR, "east", "left");
        s.fill(28, STAND, 20, 28, 6, 20, Kit.ladder("south"));
        kitchen(s);
        chambers(s);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{17, STAND, 17}, List.of(new int[]{23, STAND, 10}));
    }

    // ---------------------------------------------------------------- the rooms of the guild court SS

    /**
     * The great hall of the guild court SS, a longhouse along x from its gable on x = 6 to that on x = 28 around the line
     * z = 9, its floor on layer {@link #FLOOR}, its walls up to the plate on layer 7, its door in the middle of its south
     * wall. Tie beams cross it on the plates and lanterns hang from them on chains. In its middle the sunken hearth with the
     * cauldron hanging over it from the middle beam; across it from the door the high seat of the master between his carved
     * posts under the banner of the guild, shields beside it; to his left and right and on both sides of the door long
     * tables with benches along the walls and benches before them, horns and candles on the boards, furs on the floor, a
     * frieze of cloth over the windows, spears in their racks beside the door. At
     * the west gable the honour wall: the head of a dragon over a red cloth, skulls, shields and weapon racks, the casks of
     * mead with their taps in the corners and a long table across before it. At the east end the counter of the registrar
     * with the guild desk in its middle, his post, his chest, his shelves and his writing board behind it, and before it the
     * bench of those who wait and the board of contracts on the gable.
     */
    private static void meadHall(Structure s) {
        int y = STAND;
        int c = 9;
        for (int x = 9; x <= 25; x += 4) {
            int w = Nordic.hullWidth(x, 6, 28, 5, 7);
            s.fill(x, 7, c - w + 1, x, 7, c + w - 1, Kit.log(Nordic.LOG, "z"));
        }
        // the hearth and the cauldron over it
        sunkenHearth(s, 13, 21, c, y);
        s.fill(17, y + 2, c, 17, 6, c, Kit.chain());
        s.set(17, y + 1, c, CAULDRON);

        // the high seat between the carved posts, the banners of the guild on them, shields and furs before it
        Nordic.highSeat(s, 17, y, 3, "south");
        for (int x : new int[]{16, 18}) {
            s.set(x, y + 1, 3, POST).set(x, y + 2, 3, STAVE).set(x, y + 3, 3, POST).set(x, y + 4, 3, Kit.lantern());
        }
        s.set(17, y + 4, 3, Kit.wallBanner(GUILD, "south"));
        shield(s, 15, y + 2, 3, "south", true);
        shield(s, 19, y + 2, 3, "south", true);
        Nordic.furs(s, 15, 6, 19, 7, y);
        // a frieze of cloth along the long walls over the windows
        Nordic.tapestry(s, 12, 3, 15, 3, y + 4, "south");
        Nordic.tapestry(s, 19, 3, 22, 3, y + 4, "south");
        Nordic.tapestry(s, 12, 15, 15, 15, y + 4, "north");
        Nordic.tapestry(s, 19, 15, 22, 15, y + 4, "north");

        // the long tables on both sides of the hearth, benches along the walls and before the boards
        for (int x1 : new int[]{12, 19}) {
            int x2 = x1 + 3;
            Furnish.bench(s, x1, 3, x2, 3, y, "south", "spruce");
            hallTable(s, x1, 4, x2, 4, y, "s");
            hallTable(s, x1, 14, x2, 14, y, "n");
            Furnish.bench(s, x1, 15, x2, 15, y, "north", "spruce");
        }
        Nordic.furs(s, 16, 11, 18, 12, y);
        Nordic.furs(s, 11, 7, 12, 11, y);
        for (int x : new int[]{16, 18}) {
            s.set(x, y, 15, Nordic.FENCE).set(x, y + 1, 15, SPEAR);
        }
        shield(s, 12, y + 2, 3, "south", false);
        shield(s, 22, y + 2, 3, "south", false);
        shield(s, 12, y + 2, 15, "north", true);
        shield(s, 15, y + 2, 15, "north", false);
        shield(s, 19, y + 2, 15, "north", false);
        shield(s, 22, y + 2, 15, "north", true);
        for (int x = 8; x <= 11; x++) {
            if (x % 2 == 0) {
                s.set(x, y + 3, 4, Kit.wallBanner("red", "south")).set(x, y + 3, 14, Kit.wallBanner("red", "north"));
            } else {
                shield(s, x, y + 2, 4, "south", x == 9);
                shield(s, x, y + 2, 14, "north", x == 11);
            }
        }

        // the honour wall on the west gable, the casks of mead in its corners and the long table across before it
        s.set(7, y + 3, c, "minecraft:dragon_wall_head[facing=east]");
        s.set(7, y + 2, c, Kit.wallBanner("red", "east"));
        for (int z : new int[]{c - 2, c + 2}) {
            s.set(7, y, z, grindstone("east"));
            skull(s, 7, y + 2, z, "east", z < c);
        }
        shield(s, 7, y + 1, c - 1, "east", true);
        shield(s, 7, y + 1, c + 1, "east", false);
        for (int z : new int[]{5, 6, 12, 13}) {
            cask(s, 7, y, z, "east");
        }
        hallTable(s, 9, 7, 9, 11, y, "we");

        // the counter of the registrar, the desk in its middle, his post and his chest behind it
        s.fill(23, y, c, 27, y, c, Kit.topSlab(Nordic.SLAB));
        s.set(25, y, c, DESK);
        s.set(23, y + 1, c, Furnish.candles(3)).set(27, y + 1, c, Kit.lantern());
        s.set(25, y, 7, WORK_POST);
        s.set(24, y, 7, Kit.chest("south"));
        Furnish.books(s, 23, 4, 26, 4, y, y + 1);
        skull(s, 23, y + 2, 4, "south", true);
        s.set(24, y + 2, 4, Furnish.candles(2)).set(25, y + 2, 4, HORN_NS);
        skull(s, 26, y + 2, 4, "south", false);
        s.set(27, y, 6, Kit.topSlab(Nordic.SLAB)).set(27, y + 1, 6, Furnish.candles(1));
        s.set(27, y, 7, Kit.topSlab(Nordic.SLAB)).set(27, y + 1, 7, "minecraft:white_carpet");
        s.set(26, y, 7, Kit.stairs(Nordic.STAIRS, "west"));
        Furnish.crate(s, 23, y, 5, "spruce");
        s.set(23, y + 1, 5, Furnish.candles(2));
        Nordic.furs(s, 24, 5, 26, 6, y);
        s.set(27, y + 3, 5, Kit.wallBanner(GUILD, "west"));
        // those who wait sit on a bench under the window and read the board of contracts on the gable
        Furnish.bench(s, 23, 14, 26, 14, y, "north", "spruce");
        Nordic.furs(s, 24, 11, 26, 13, y);
        noticeBoard(s, 28, y, 10, 13);

        // the lanterns on the beams
        for (int x : new int[]{13, 21}) {
            lamp(s, x, 5, 4);
            lamp(s, x, 5, 14);
        }
        lamp(s, 9, 5, 7);
        lamp(s, 9, 5, 11);
        lamp(s, 17, 5, 6);
        lamp(s, 17, 5, 13);
        lamp(s, 25, 5, 6);
        lamp(s, 25, 5, 12);
    }

    /**
     * A sunken hearth along x from x1 to x2 around the line z, three wide, in a hall whose floor lies on layer
     * {@code y - 1}: a trough of rough stone let into the floor, fires burning in it on every other cell and glowing embers
     * deep between them, iron bars round its rim on the layer one stands on, so that no one steps into the fire.
     */
    private static void sunkenHearth(Structure s, int x1, int x2, int z, int y) {
        for (int x = x1; x <= x2; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dz != 0 || x == x1 || x == x2) {
                    s.set(x, y - 1, z + dz, Nordic.FOOTING);
                    s.set(x, y, z + dz, Kit.BARS);
                } else if ((x - x1) % 2 == 1) {
                    s.set(x, y - 2, z, Nordic.EMBERS);
                    s.set(x, y - 1, z, FIRE);
                    s.set(x, y, z, AIR);
                } else {
                    s.set(x, y - 2, z, "minecraft:magma_block");
                    s.set(x, y - 1, z, AIR);
                    s.set(x, y, z, AIR);
                }
            }
        }
    }

    /**
     * A long table of the hall from (x1, z1) to (x2, z2), along x or z, on layer y: boards of upper slabs with candles and
     * drinking horns on them, and benches of lower slabs along it on the sides named in {@code benches} ("n", "s", "w",
     * "e").
     */
    private static void hallTable(Structure s, int x1, int z1, int x2, int z2, int y, String benches) {
        boolean alongX = x2 - x1 >= z2 - z1;
        int n = alongX ? x2 - x1 : z2 - z1;
        for (int i = 0; i <= n; i++) {
            int x = alongX ? x1 + i : x1;
            int z = alongX ? z1 : z1 + i;
            s.set(x, y, z, Kit.topSlab(Nordic.SLAB));
            if (i % 4 == 1) {
                s.set(x, y + 1, z, Furnish.candles(2 + i % 3 % 2));
            } else if (i % 4 == 3) {
                s.set(x, y + 1, z, alongX ? HORN_NS : HORN_WE);
            }
            for (int[] b : new int[][]{{0, -1, 'n'}, {0, 1, 's'}, {-1, 0, 'w'}, {1, 0, 'e'}}) {
                if (benches.indexOf(b[2]) >= 0) {
                    s.set(x + b[0], y, z + b[1], Kit.slab(Nordic.SLAB));
                }
            }
        }
    }

    /**
     * A shield hung on the wall behind (x, y, z), looking {@code facing}: a round board of dark oak, or painted red.
     */
    private static void shield(Structure s, int x, int y, int z, String facing, boolean red) {
        s.set(x, y, z, Kit.shutter(red ? "minecraft:mangrove_trapdoor" : "minecraft:dark_oak_trapdoor", facing));
    }

    /**
     * Two casks of mead lying one on the other at (x, y, z) and above, their heads looking {@code facing}, a tap in the
     * head of the lower one.
     */
    private static void cask(Structure s, int x, int y, int z, String facing) {
        int[] f = Furnish.step(facing);
        String axis = f[0] != 0 ? "x" : "z";
        s.set(x, y, z, Kit.log(Nordic.LOG, axis)).set(x, y + 1, z, Kit.log(Nordic.LOG, axis));
        s.set(x + f[0], y, z + f[1], "minecraft:spruce_button[face=wall,facing=" + facing + ",powered=false]");
    }

    /**
     * The board of contracts in a gable on x from z1 to z2, looking west into the hall whose floor one stands on at layer
     * y: boards in the wall three layers high between two posts under a beam, the notices of white and yellow cloth hanging
     * on them, and a ledge before them with papers and candles.
     */
    private static void noticeBoard(Structure s, int x, int y, int z1, int z2) {
        String[] papers = {"white", "yellow", "white", "light_gray"};
        for (int z = z1; z <= z2; z++) {
            int i = z - z1;
            s.fill(x, y + 1, z, x, y + 3, z, Nordic.PLANKS);
            s.set(x - 1, y, z, Kit.topSlab(Nordic.SLAB));
            s.set(x - 1, y + 1, z, i % 2 == 0 ? "minecraft:white_carpet" : Furnish.candles(2));
            s.set(x - 1, y + 3, z, Kit.wallBanner(papers[i % papers.length], "west"));
        }
        s.fill(x, y + 1, z1 - 1, x, y + 3, z1 - 1, POST);
        s.fill(x, y + 1, z2 + 1, x, y + 3, z2 + 1, POST);
        s.fill(x, y + 4, z1 - 1, x, y + 4, z2 + 1, Kit.log(Nordic.LOG, "z"));
    }

    /**
     * The rooms of the gate tower of the guild court SS around (gx, gz), whose walls stand three blocks out, its floor on
     * the ground, the lookout on layer {@code lf}: over the passage a ceiling of boards on beams on layer 5, along the sides
     * of the passage the benches of the gate guard, shields, weapon racks and the banners of the guild; above the gate the
     * guard room with its table and benches, lit by a window over the gate on each side; the watch rooms above it with their
     * tables, benches and furs; a ladder along the back wall through all of them.
     */
    private static void gateRooms(Structure s, int gx, int gz, int lf) {
        // the ceiling over the passage
        s.fill(gx - 2, 5, gz - 2, gx + 2, 5, gz + 2, Nordic.FLOOR);
        for (int z : new int[]{gz - 1, gz + 1}) {
            s.fill(gx - 2, 5, z, gx + 2, 5, z, Kit.log(Nordic.LOG, "x"));
        }
        s.fill(gx - 2, 1, gz - 2, gx - 2, lf, gz - 2, Kit.ladder("south"));
        // the passage: the benches of the guard, shields and banners over them, weapon racks
        Furnish.bench(s, gx - 2, gz - 1, gx - 2, gz + 1, 1, "east", "spruce");
        Furnish.bench(s, gx + 2, gz - 1, gx + 2, gz + 1, 1, "west", "spruce");
        s.set(gx - 2, 1, gz + 2, grindstone("east")).set(gx + 2, 1, gz + 2, grindstone("west"));
        s.set(gx + 2, 1, gz - 2, grindstone("west"));
        for (int side = -1; side <= 1; side += 2) {
            String in = side < 0 ? "east" : "west";
            int x = gx + 2 * side;
            shield(s, x, 3, gz - 1, in, side < 0);
            shield(s, x, 3, gz + 1, in, side > 0);
            s.set(x, 4, gz, Kit.wallBanner(GUILD, in));
        }
        lamp(s, gx, 4, gz);
        lamp(s, gx, 4, gz - 2);
        lamp(s, gx, 4, gz + 2);

        // the guard room over the gate
        int g = 6;
        Nordic.window(s, gx, g + 1, gz + 3, "south", 2, true);
        Nordic.window(s, gx, g + 1, gz - 3, "north", 2, true);
        hallTable(s, gx - 1, gz, gx + 1, gz, g, "ns");
        s.set(gx + 2, g, gz - 2, grindstone("west")).set(gx + 2, g, gz + 2, grindstone("west"));
        Furnish.crate(s, gx - 2, g, gz + 2, "spruce");
        s.set(gx - 2, g + 1, gz + 2, Furnish.candles(2));
        Nordic.furs(s, gx - 1, gz + 2, gx + 1, gz + 2, g);
        shield(s, gx + 2, g + 2, gz, "west", true);
        shield(s, gx - 2, g + 2, gz, "east", false);
        lamp(s, gx, 9, gz);

        // the first watch room: a bench and a table by the east wall, furs, weapon racks
        int w1 = 11;
        Furnish.bench(s, gx + 2, gz - 1, gx + 2, gz + 1, w1, "west", "spruce");
        s.fill(gx + 1, w1, gz - 1, gx + 1, w1, gz + 1, Kit.topSlab(Nordic.SLAB));
        s.set(gx + 1, w1 + 1, gz - 1, Furnish.candles(2)).set(gx + 1, w1 + 1, gz + 1, HORN_WE);
        s.set(gx + 2, w1, gz + 2, grindstone("west")).set(gx - 2, w1, gz + 2, grindstone("east"));
        s.set(gx - 2, w1, gz + 1, Kit.topSlab(Nordic.FLOOR_SLAB)).set(gx - 2, w1 + 1, gz + 1, Furnish.candles(2));
        Nordic.furs(s, gx - 1, gz - 1, gx, gz + 1, w1);
        lamp(s, gx + 2, 14, gz + 2);
        lamp(s, gx - 1, 14, gz);

        // the second watch room under the lookout: a bench, a table with the horn of the watch, furs
        int w2 = 17;
        Furnish.bench(s, gx + 2, gz - 1, gx + 2, gz + 1, w2, "west", "spruce");
        s.set(gx + 1, w2, gz, Kit.topSlab(Nordic.SLAB)).set(gx + 1, w2 + 1, gz, HORN_WE);
        s.set(gx - 2, w2, gz + 2, grindstone("east"));
        Nordic.furs(s, gx - 1, gz - 1, gx, gz + 1, w2);
        lamp(s, gx - 1, 20, gz + 1);
    }

    /**
     * The armoury on the ground floor of the west wing of the guild court SS (inside x 2..6, z 20..30, standing on layer
     * {@link #STAND} under the floor of the map room on layer 6): weapon racks along the outer wall under the skulls of
     * beasts, racks of spears and shields along the inner wall, a table of blades in the middle, the anvil, crates and hay
     * at the far end, lanterns hanging from the beams.
     */
    private static void armoury(Structure s) {
        int y = STAND;
        for (int z = 21; z <= 29; z += 2) {
            s.set(2, y, z, grindstone("east"));
        }
        for (int z = 24; z <= 26; z++) {
            skull(s, 2, y + 2, z, "east", z % 2 == 0);
        }
        for (int z : new int[]{22, 28}) {
            s.set(2, y + 2, z + (z < 25 ? 1 : -1), Kit.wallBanner(GUILD, "east"));
        }
        // spears in racks and shields on the inner wall
        for (int z : new int[]{22, 23, 27, 28}) {
            s.set(6, y, z, Nordic.FENCE).set(6, y + 1, z, SPEAR);
        }
        shield(s, 6, y + 2, 22, "west", true);
        shield(s, 6, y + 2, 23, "west", false);
        shield(s, 6, y + 2, 27, "west", false);
        shield(s, 6, y + 2, 28, "west", true);
        // the table of blades
        s.fill(4, y, 24, 4, y, 26, Kit.topSlab(Nordic.SLAB));
        s.set(4, y + 1, 24, HORN_NS).set(4, y + 1, 26, Furnish.candles(3));
        s.set(4, y + 1, 25, "minecraft:lightning_rod[facing=south,powered=false,waterlogged=false]");
        s.set(4, y, 30, "minecraft:anvil[facing=east]");
        Furnish.crate(s, 2, y, 30, "spruce");
        Furnish.crate(s, 6, y, 30, "spruce");
        s.set(6, y, 29, "minecraft:hay_block[axis=y]");
        s.set(5, y, 30, "minecraft:hay_block[axis=y]");
        lamp(s, 4, 5, 22);
        lamp(s, 4, 5, 28);
    }

    /**
     * The map room of the guild over the armoury in the west wing of the guild court SS (inside x 2..6, z 20..30, standing
     * on layer 7): the map of the lands painted on the north gable with candles under it, the one lectern of the guild
     * with its stool, the cartography table by the window, a great table in the middle with a map of cloth spread on it and
     * chairs round it, shelves of books along both walls with candles on them, furs, crates of scrolls and the banner of
     * the guild on the south gable.
     */
    private static void mapRoom(Structure s) {
        int y = 7;
        guildMap(s, 2, y + 1, 19);
        s.set(3, y, 21, lectern("south")).set(3, y, 22, Kit.stairs(Nordic.FLOOR_STAIRS, "south"));
        s.set(2, y, 21, Kit.topSlab(Nordic.SLAB)).set(2, y + 1, 21, Furnish.candles(3));
        s.set(5, y, 21, Kit.topSlab(Nordic.SLAB)).set(5, y + 1, 21, Furnish.candles(2));
        s.set(2, y, 23, CARTOGRAPHY);
        // the great table with the map of cloth on it and chairs round it
        String[] land = {"lime", "green", "light_blue", "green", "brown", "lime", "light_blue", "white", "green"};
        for (int z = 24; z <= 26; z++) {
            for (int x = 3; x <= 5; x++) {
                s.set(x, y, z, Kit.topSlab(Nordic.SLAB));
                s.set(x, y + 1, z, "minecraft:" + land[(z - 24) * 3 + x - 3] + "_carpet");
            }
        }
        s.set(2, y, 25, Kit.stairs(Nordic.STAIRS, "west")).set(6, y, 25, Kit.stairs(Nordic.STAIRS, "east"));
        s.set(4, y, 23, Kit.stairs(Nordic.STAIRS, "north")).set(4, y, 27, Kit.stairs(Nordic.STAIRS, "south"));
        Furnish.books(s, 2, 27, 2, 30, y, y + 1);
        Furnish.books(s, 6, 27, 6, 30, y, y + 1);
        s.set(2, y + 2, 28, Furnish.candles(2)).set(6, y + 2, 29, Furnish.candles(3));
        Nordic.furs(s, 3, 28, 5, 30, y);
        Furnish.crate(s, 3, y, 30, "spruce");
        s.set(4, y + 2, 30, Kit.wallBanner(GUILD, "north"));
        lamp(s, 4, 10, 23);
        lamp(s, 4, 10, 28);
    }

    /**
     * The kitchen on the ground floor of the east wing of the guild court SS (inside x 28..32, z 20..30, standing on
     * layer {@link #STAND}): the cooking corner with the cauldron, the fire and the board under the shelf at the north
     * gable, the table of the servants with its benches, casks of mead, sacks and crates at the far end, hay, lanterns.
     */
    private static void kitchen(Structure s) {
        int y = STAND;
        Furnish.kitchen(s, 30, y, 20, "south", "spruce");
        Nordic.feastBoard(s, 30, 24, 30, 29, y);
        s.fill(32, y, 21, 32, y, 22, Kit.topSlab(Nordic.SLAB));
        s.set(32, y + 1, 21, "minecraft:cake[bites=0]").set(32, y + 1, 22, Furnish.candles(2));
        cask(s, 28, y, 22, "east");
        cask(s, 28, y, 23, "east");
        Furnish.crate(s, 32, y, 30, "spruce");
        Furnish.sack(s, 31, y, 30, true);
        Furnish.sack(s, 32, y, 29, false);
        Furnish.sack(s, 32, y, 28, true);
        s.set(32, y + 1, 30, "minecraft:hay_block[axis=y]");
        lamp(s, 30, 5, 23);
        lamp(s, 30, 5, 28);
    }

    /**
     * The chambers of honour of the masters over the kitchen in the east wing of the guild court SS (inside x 28..32,
     * z 20..30, standing on layer 7), parted by a wall of boards with a door: in each a bed of state with its canopy, which
     * is no home, a fur before it, a table with candles and a chair, a crate at the foot of the bed, a weapon rack and the
     * banner of the guild on the wall.
     */
    private static void chambers(Structure s) {
        int y = 7;
        // the wall between the chambers and its door
        s.fill(28, y, 25, 32, y + 2, 25, Nordic.PLANKS);
        s.fill(28, y + 3, 25, 32, y + 3, 25, Kit.log(Nordic.LOG, "x"));
        s.door(29, y, 25, Nordic.DOOR, "south", "left");
        // the north chamber
        Furnish.canopyBed(s, 31, y, 23, "north", "spruce");
        Nordic.furs(s, 28, 23, 29, 24, y);
        s.set(29, y, 21, Kit.topSlab(Nordic.SLAB)).set(29, y + 1, 21, Furnish.candles(2));
        s.set(29, y, 22, Kit.stairs(Nordic.STAIRS, "south"));
        Furnish.crate(s, 31, y, 24, "spruce");
        s.set(32, y, 24, grindstone("west"));
        s.set(30, y + 2, 20, Kit.wallBanner(GUILD, "south"));
        // the south chamber
        Furnish.canopyBed(s, 31, y, 29, "north", "spruce");
        Nordic.furs(s, 28, 28, 29, 30, y);
        s.set(28, y, 26, Kit.topSlab(Nordic.SLAB)).set(28, y + 1, 26, Furnish.candles(3));
        s.set(28, y, 27, Kit.stairs(Nordic.STAIRS, "south"));
        Furnish.crate(s, 31, y, 30, "spruce");
        s.set(32, y, 30, grindstone("west"));
        s.set(32, y + 2, 26, Kit.wallBanner(GUILD, "west"));
        lamp(s, 29, 10, 23);
        lamp(s, 29, 10, 28);
    }

    // ---------------------------------------------------------------- shapes of the guild

    /**
     * A longhouse hall from the gable on the line {@code a1} to that on {@code a2} (x when {@code alongX}, else z) around
     * the line {@code c} across, on a plinth of rough stone with a skirt of rough stairs round it: its floor on layer
     * {@link #FLOOR}, its walls {@code wall} high, half {@code end} wide at the gables and {@code mid} in the middle (see
     * {@link Nordic#longhouse}).
     *
     * @return the layer of its ridge in the middle
     */
    private static int hall(Structure s, int a1, int a2, int c, int end, int mid, int wall, boolean alongX) {
        Nordic.Area plan = (x, z) -> {
            int a = alongX ? x : z;
            int cc = alongX ? z : x;
            return a >= a1 && a <= a2 && Math.abs(cc - c) <= Nordic.hullWidth(a, a1, a2, end, mid);
        };
        int x1 = alongX ? a1 - 1 : c - mid - 1;
        int x2 = alongX ? a2 + 1 : c + mid + 1;
        int z1 = alongX ? c - mid - 1 : a1 - 1;
        int z2 = alongX ? c + mid + 1 : a2 + 1;
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                if (plan.has(x, z)) {
                    put(s, x, 0, z, Nordic.FOOTING);
                    continue;
                }
                String f = plan.has(x + 1, z) ? "east" : plan.has(x - 1, z) ? "west" : plan.has(x, z + 1) ? "south" : plan.has(x, z - 1) ? "north" : null;
                if (f != null) {
                    put(s, x, 0, z, Kit.stairs(Nordic.FOOTING_STAIRS, f));
                }
            }
        }
        return Nordic.longhouse(s, a1, a2, c, FLOOR, end, mid, wall, alongX, Nordic.HULL, Nordic.ROOF);
    }

    /**
     * Paints the gables within x1..x2, z1..z2: the staves above the plate at {@code top} become painted boards.
     */
    private static void paintGable(Structure s, int x1, int z1, int x2, int z2, int top) {
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                for (int y = top + 1; y < s.sizeY; y++) {
                    if (s.inside(x, y, z) && s.get(x, y, z).equals(STAVE)) {
                        s.set(x, y, z, Nordic.PAINT);
                    }
                }
            }
        }
    }

    /**
     * Shields on the long walls of a hall along z (see {@link #hall}) on layer {@code y} for the rows {@code z1..z2}, on the
     * west and the east wall as asked, wherever the wall there is a stave.
     */
    private static void shields(Structure s, int c, int a1, int a2, int end, int mid, int y, int z1, int z2, boolean west, boolean east) {
        for (int z = z1; z <= z2; z++) {
            int w = Nordic.hullWidth(z, a1, a2, end, mid);
            for (int x : new int[]{west ? c - w : -1, east ? c + w : -1}) {
                if (x >= 0 && s.get(x, y, z).equals(STAVE)) {
                    s.set(x, y, z, SHIELD);
                }
            }
        }
    }

    /**
     * The floor of a loft at the layer of the wall plate {@code top} inside a hall along z (see {@link #hall}) over the rows
     * {@code z1..z2}, with a rail of fences along its open edge on the row {@code z2}.
     */
    private static void loft(Structure s, int c, int a1, int a2, int end, int mid, int z1, int z2, int top) {
        for (int z = z1; z <= z2; z++) {
            int w = Nordic.hullWidth(z, a1, a2, end, mid);
            for (int x = c - w + 1; x <= c + w - 1; x++) {
                s.set(x, top, z, Nordic.FLOOR);
                if (z == z2) {
                    s.set(x, top + 1, z, Nordic.FENCE);
                }
            }
        }
    }

    /**
     * Two steps of rough stone up to a porch whose floor lies on layer {@link #FLOOR}: a stair on layer 1 on the row
     * {@code z} and one on layer 0 before it, three wide around the column {@code x}.
     */
    private static void porchSteps(Structure s, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            put(s, x + dx, 0, z, Nordic.FOOTING);
            put(s, x + dx, 1, z, Kit.stairs(Nordic.FOOTING_STAIRS, "north"));
            put(s, x + dx, 0, z + 1, Kit.stairs(Nordic.FOOTING_STAIRS, "north"));
        }
    }

    /**
     * The counter of the registrar across a hall on the row {@code z}, {@code half} blocks to each side of the column
     * {@code x}: boards of upper slabs with the guild desk in its middle looking south, candles at one end and a lantern at
     * the other.
     */
    private static void counter(Structure s, int x, int y, int z, int half) {
        s.fill(x - half, y, z, x + half, y, z, Kit.topSlab(Nordic.FLOOR_SLAB));
        s.set(x, y, z, DESK);
        s.set(x - half, y + 1, z, Furnish.candles(3));
        s.set(x + half, y + 1, z, Kit.lantern());
    }

    /**
     * The contract board in a wall that looks {@code facing} into the room, {@code width} cells from (x, z) to the left of
     * {@code facing}: boards in the wall two layers high, notices of white and yellow cloth hanging on them, and a ledge
     * before them with papers and candles.
     */
    private static void contractBoard(Structure s, int x, int y, int z, String facing, int width) {
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        for (int u = 0; u < width; u++) {
            int px = x + a[0] * u;
            int pz = z + a[1] * u;
            s.fill(px, y + 1, pz, px, y + 2, pz, Nordic.PLANKS);
            int fx = px + f[0];
            int fz = pz + f[1];
            s.set(fx, y, fz, Kit.topSlab(Nordic.FLOOR_SLAB));
            s.set(fx, y + 1, fz, u % 2 == 0 ? "minecraft:white_carpet" : Furnish.candles(2));
            s.set(fx, y + 2, fz, Kit.wallBanner(u % 2 == 0 ? "white" : "yellow", facing));
        }
    }

    /**
     * A watch tower of the guild around (cx, cz): a shaft of staves {@code 2r + 1} wide on a base of rough stone whose floor
     * lies on layer {@code floor}, up to its plate at {@code plate}, with floors of boards every five layers and a ladder
     * along its back wall through all of them; on top the lookout jutting out one block on every side over brackets: a
     * floor, posts at its corners, a rail between them and the open air above it up to a beam, and over it a steep gable
     * roof (its ridge along x or z) with gables of staves, crossed boards and dragon heads, the banner of the guild hanging
     * under each gable. A belt of lying logs marks every floor on the outside, small windows in white frames light the
     * storeys and shields hang between them.
     *
     * @return the layer of the floor of the lookout
     */
    private static int watchTower(Structure s, int cx, int cz, int r, int floor, int plate, boolean alongX) {
        groundStorey(s, cx, cz, r, floor, plate, floor + 3);
        for (int f = floor + 6; f <= plate - 2; f += 5) {
            s.fill(cx - r + 1, f, cz - r + 1, cx + r - 1, f, cz + r - 1, Nordic.FLOOR);
            s.beam(cx - r, cz - r, cx + r, cz + r, f, Nordic.LOG);
            s.corners(cx - r, f, cz - r, cx + r, f, cz + r, Nordic.LOG);
            s.set(cx + r - 1, f - 1, cz + r - 1, Kit.hangingLantern());
            Nordic.window(s, cx, f + 2, cz + r, "south", 2, true);
            Nordic.window(s, cx + r, f + 2, cz, "east", 1, true);
            Nordic.window(s, cx - r, f + 2, cz, "west", 1, true);
            s.set(cx + r, f - 2, cz + r - 1, SHIELD).set(cx - r, f - 2, cz + r - 1, SHIELD);
        }
        Nordic.window(s, cx, floor + 4, cz + r, "south", 1, true);
        int lf = lookout(s, cx, cz, r, plate);
        int big = r + 1;
        int ridge = Nordic.gableRoof(s, cx - big, cz - big, cx + big, cz + big, lf + 4, alongX, Nordic.STEEP, Nordic.ROOF, STAVE,
                Nordic.BOARD, Nordic.SPIRE);
        s.fill(cx, floor + 1, cz - r + 1, cx, lf, cz - r + 1, Kit.ladder("south"));
        if (alongX) {
            s.set(cx - big - 1, lf + 5, cz, Kit.wallBanner(GUILD, "west")).set(cx + big + 1, lf + 5, cz, Kit.wallBanner(GUILD, "east"));
        } else {
            s.set(cx, lf + 5, cz - big - 1, Kit.wallBanner(GUILD, "north")).set(cx, lf + 5, cz + big + 1, Kit.wallBanner(GUILD, "south"));
        }
        return lf;
    }

    /**
     * The lookout on top of a tower whose walls stand {@code r} blocks out from (cx, cz) with their plate at {@code plate}:
     * it juts out one block on every side over brackets: a floor, posts at its corners (and the middles of long sides), a
     * rail between them and the open air above it up to a beam on the fourth layer over its floor, a lantern hanging in it.
     *
     * @return the layer of its floor
     */
    private static int lookout(Structure s, int cx, int cz, int r, int plate) {
        s.set(cx + r - 1, plate, cz + r - 1, Kit.hangingLantern());
        int lf = plate + 1;
        int big = r + 1;
        for (int z = cz - big; z <= cz + big; z++) {
            for (int x = cx - big; x <= cx + big; x++) {
                boolean rim = Math.abs(x - cx) == big || Math.abs(z - cz) == big;
                if (!rim) {
                    continue;
                }
                String in = z == cz - big ? "south" : z == cz + big ? "north" : x == cx - big ? "east" : "west";
                s.set(x, plate, z, Kit.upside(Nordic.STAIRS, in));
            }
        }
        s.fill(cx - big, lf, cz - big, cx + big, lf, cz + big, Nordic.FLOOR);
        s.beam(cx - big, cz - big, cx + big, cz + big, lf, Nordic.LOG);
        for (int z = cz - big; z <= cz + big; z++) {
            for (int x = cx - big; x <= cx + big; x++) {
                boolean rim = Math.abs(x - cx) == big || Math.abs(z - cz) == big;
                if (!rim) {
                    continue;
                }
                boolean corner = Math.abs(x - cx) == big && Math.abs(z - cz) == big;
                boolean middle = big >= 4 && (x == cx || z == cz);
                if (corner || middle) {
                    s.fill(x, lf + 1, z, x, lf + 3, z, POST);
                } else {
                    s.set(x, lf + 1, z, Nordic.FENCE);
                }
            }
        }
        s.beam(cx - big, cz - big, cx + big, cz + big, lf + 4, Nordic.LOG);
        s.fill(cx - 1, lf + 4, cz, cx + 1, lf + 4, cz, Kit.log(Nordic.LOG, "x"));
        s.set(cx + 1, lf + 3, cz, Kit.hangingLantern());
        return lf;
    }

    /**
     * The crown of a tower over walls {@code r} blocks out from (cx, cz) whose plate (or beam) lies at {@code plate}: two
     * steep gable roofs of the spires crossing over the square, painted gables (with a slit of {@code slit} in the middle
     * of each, none when null), crossed boards at the four ends and dragon heads at the ends named in {@code dragons}
     * ("n", "s", "w", "e"), soul lanterns hanging under its four corners when the gables are lit.
     *
     * @return the layer of its ridge
     */
    private static int crown(Structure s, int cx, int cz, int r, int plate, String dragons, String slit) {
        int x1 = cx - r;
        int x2 = cx + r;
        int z1 = cz - r;
        int z2 = cz + r;
        Nordic.Field fx = Nordic.gableField(x1, z1, x2, z2, plate, true, Nordic.STEEP);
        Nordic.Field fz = Nordic.gableField(x1, z1, x2, z2, plate, false, Nordic.STEEP);
        Nordic.Field f = (x, z) -> Math.max(fx.at(x, z), fz.at(x, z));
        Nordic.roof(s, f, Nordic.EVERYWHERE, x1 - 1, z1 - 1, x2 + 1, z2 + 1, Nordic.SPIRE);
        Nordic.gableWall(s, f, true, x1, z1, z2, plate + 1, Nordic.PAINT);
        Nordic.gableWall(s, f, true, x2, z1, z2, plate + 1, Nordic.PAINT);
        Nordic.gableWall(s, f, false, z1, x1, x2, plate + 1, Nordic.PAINT);
        Nordic.gableWall(s, f, false, z2, x1, x2, plate + 1, Nordic.PAINT);
        if (slit != null) {
            for (int y = plate + 2; y <= plate + 3; y++) {
                s.set(x1, y, cz, slit).set(x2, y, cz, slit).set(cx, y, z1, slit).set(cx, y, z2, slit);
            }
            soulEaves(s, cx, cz, r + 1, plate - 1);
        }
        Nordic.crossedBoards(s, true, x1 - 1, z1 - 1, z2 + 1, Nordic.SPIRE, Nordic.BOARD, true);
        Nordic.crossedBoards(s, true, x2 + 1, z1 - 1, z2 + 1, Nordic.SPIRE, Nordic.BOARD, true);
        Nordic.crossedBoards(s, false, z1 - 1, x1 - 1, x2 + 1, Nordic.SPIRE, Nordic.BOARD, true);
        Nordic.crossedBoards(s, false, z2 + 1, x1 - 1, x2 + 1, Nordic.SPIRE, Nordic.BOARD, true);
        int ridge = fz.at(cx, z1);
        if (dragons.contains("n")) {
            Nordic.dragonHead(s, cx, ridge + 1, z1 - 1, "north", Nordic.SPIRE);
        }
        if (dragons.contains("s")) {
            Nordic.dragonHead(s, cx, ridge + 1, z2 + 1, "south", Nordic.SPIRE);
        }
        if (dragons.contains("w")) {
            Nordic.dragonHead(s, x1 - 1, ridge + 1, cz, "west", Nordic.SPIRE);
        }
        if (dragons.contains("e")) {
            Nordic.dragonHead(s, x2 + 1, ridge + 1, cz, "east", Nordic.SPIRE);
        }
        return ridge;
    }

    /**
     * A slender needle of the spires rising {@code height} layers from the crossing of a crown whose ridge lies at
     * {@code ridge}, a collar round its foot, with a vane on its point.
     *
     * @return the layer above the vane
     */
    private static int spike(Structure s, int cx, int cz, int ridge, int height) {
        return Nordic.needle(s, cx, ridge - 2, cz, 5, new int[]{2, height}, Nordic.SPIRE);
    }

    /**
     * A rectangular house of staves on the lines x1..x2, z1..z2: a plinth of rough stone under its floor on layer
     * {@code floor}, its walls up to the plate at {@code plate}, a steep gable roof of shingles with gables of staves,
     * crossed boards and dragon heads of {@code dragons} (none when null).
     *
     * @return the layer of its ridge
     */
    private static int house(Structure s, int x1, int z1, int x2, int z2, int floor, int plate, boolean alongX, Kit.Roof dragons) {
        for (int y = 0; y < floor; y++) {
            s.fill(x1, y, z1, x2, y, z2, Nordic.FOOTING);
        }
        Nordic.footing(s, x1, z1, x2, z2, floor);
        Nordic.staveWalls(s, x1, floor + 1, z1, x2, plate, z2);
        return Nordic.gableRoof(s, x1, z1, x2, z2, plate, alongX, Nordic.STEEP, Nordic.ROOF, STAVE, Nordic.BOARD, dragons);
    }

    /**
     * A gallery along the side of a court before a wall on the side {@code wall}: a walk of boards over x1..x2, z1..z2 on
     * layer {@link #FLOOR} on rough stone, posts along its open edge carrying a beam, and a lean-to roof of shingles over it
     * whose eaves lie one block beyond the posts on layer 6, with lanterns under it.
     */
    private static void gallerySide(Structure s, int x1, int z1, int x2, int z2, String wall) {
        s.fill(x1, 0, z1, x2, 0, z2, Nordic.FOOTING);
        s.fill(x1, FLOOR, z1, x2, FLOOR, z2, Nordic.FLOOR);
        boolean alongZ = wall.equals("west") || wall.equals("east");
        int edgeX = wall.equals("west") ? x2 : x1;
        int edgeZ = wall.equals("north") ? z2 : z1;
        if (alongZ) {
            colonnade(s, edgeX, z1, edgeX, z2, 6, "z");
        } else {
            colonnade(s, x1, edgeZ, x2, edgeZ, 6, "x");
        }
        leanTo(s, x1, z1, x2, z2, 6, wall, Nordic.ROOF);
    }

    /**
     * Posts of logs every three blocks along a line from (x1, z1) to (x2, z2) (and at both ends) from layer {@link #STAND}
     * up to a beam of lying logs along {@code axis} on layer {@code beam}, lanterns hanging from the beam between the posts.
     */
    private static void colonnade(Structure s, int x1, int z1, int x2, int z2, int beam, String axis) {
        int n = Math.max(x2 - x1, z2 - z1);
        for (int u = 0; u <= n; u++) {
            int x = x1 == x2 ? x1 : x1 + u;
            int z = x1 == x2 ? z1 + u : z1;
            s.set(x, beam, z, Kit.log(Nordic.LOG, axis));
            if (u % 3 == 0 || u == n) {
                s.fill(x, STAND, z, x, beam - 1, z, POST);
            } else if (u % 3 == 2) {
                s.set(x, beam - 1, z, Kit.hangingLantern());
            }
        }
    }

    /**
     * A lean-to roof of {@code roof} over x1..x2, z1..z2 leaning on a wall on the side {@code wall} (just beyond the area):
     * its eaves one block beyond the open side on layer {@code y}, rising one layer for every block toward the wall.
     */
    private static void leanTo(Structure s, int x1, int z1, int x2, int z2, int y, String wall, Kit.Roof roof) {
        Nordic.Field f = (x, z) -> {
            switch (wall) {
                case "east":
                    if (z < z1 || z > z2 || x < x1 - 1 || x > x2 + 1) {
                        return Nordic.NONE;
                    }
                    return x == x2 + 1 ? y + 1000 : y + x - (x1 - 1);
                case "west":
                    if (z < z1 || z > z2 || x < x1 - 1 || x > x2 + 1) {
                        return Nordic.NONE;
                    }
                    return x == x1 - 1 ? y + 1000 : y + (x2 + 1) - x;
                case "south":
                    if (x < x1 || x > x2 || z < z1 - 1 || z > z2 + 1) {
                        return Nordic.NONE;
                    }
                    return z == z2 + 1 ? y + 1000 : y + z - (z1 - 1);
                default:
                    if (x < x1 || x > x2 || z < z1 - 1 || z > z2 + 1) {
                        return Nordic.NONE;
                    }
                    return z == z1 - 1 ? y + 1000 : y + (z2 + 1) - z;
            }
        };
        Nordic.Area area = (x, z) -> f.at(x, z) != Nordic.NONE && f.at(x, z) < y + 1000;
        Nordic.roof(s, f, area, x1 - 1, z1 - 1, x2 + 1, z2 + 1, roof);
    }

    /**
     * A banner of the guild on a pole of fence posts at (x, z) from the ground up to layer {@code height}, standing on a
     * block of rough stone.
     */
    private static void flag(Structure s, int x, int z, int height) {
        s.set(x, 0, z, Nordic.FOOTING);
        s.fill(x, 1, z, x, height, z, Nordic.FENCE);
        s.set(x, height + 1, z, Kit.banner(GUILD, 0));
    }

    /**
     * A training dummy at (x, z): a post in the ground, a bale of hay for its body and a carved pumpkin for its head.
     */
    private static void dummy(Structure s, int x, int z) {
        s.set(x, 0, z, Nordic.FENCE).set(x, 1, z, "minecraft:hay_block[axis=y]").set(x, 2, z, "minecraft:carved_pumpkin[facing=south]");
    }

    /**
     * A brazier at (x, z): a fire burning on a block of rough stone.
     */
    private static void brazier(Structure s, int x, int z) {
        s.set(x, 0, z, Nordic.FOOTING);
        s.set(x, 1, z, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
    }

    /** A grindstone for the blades, a rack of weapons, whose working side looks {@code facing}. */
    private static String grindstone(String facing) {
        return "minecraft:grindstone[face=floor,facing=" + facing + "]";
    }

    /**
     * A skull of a beast hung on the wall behind (x, y, z), looking {@code facing}: a skeleton's or a wither skeleton's.
     */
    private static void skull(Structure s, int x, int y, int z, String facing, boolean dark) {
        s.set(x, y, z, (dark ? "minecraft:wither_skeleton_wall_skull" : "minecraft:skeleton_wall_skull") + "[facing=" + facing + "]");
    }

    /** A lectern whose reader stands on the side {@code facing}. */
    private static String lectern(String facing) {
        return "minecraft:lectern[facing=" + facing + ",has_book=false,powered=false]";
    }

    /**
     * A map of the lands of the guild painted on a wall that runs along x: lands, seas and hills of terracotta five wide and
     * two high, from (x, y, z) toward the east, its upper row at {@code y + 1}.
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
                s.set(x + i, y + 1 - row, z, "minecraft:" + color + "_terracotta");
            }
        }
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
     * Rank B: one seer's tower seven wide: a base of rough stone under the stave walls of its ground floor, a skirt of
     * shingles over it, the stave storey of the mage, the lantern room glazed with pale blue glass, and over it the crown of
     * two steep gables crossing, with crossed boards, dragon heads looking north and south and a needle at the crossing;
     * soul lanterns under the corners of the skirt and of the crown; two rune stones with soul lanterns flank the path to its
     * door. Inside: the focus with the post of the mage, its chest and the brewing corner on the ground floor; the bed of the
     * mage and the library above; the research room with the enchanting table in the lantern; a ladder climbs along the back
     * wall through every floor.
     */
    private static Drawn mageB() {
        Structure s = new Structure(11, 32, 15);
        int c = 5;
        int cz = 7;
        groundStorey(s, c, cz, 3, 0, 7, 2);
        skirt(s, c, cz, 3, 7, 2);
        upperStorey(s, c, cz, 3, 8, 12);
        lanternRoom(s, c, cz, 3, 13, 17);
        int ridge = crown(s, c, cz, 3, 17, "ns", RUNE_GLASS);
        spike(s, c, cz, ridge, 6);
        soulEaves(s, c, cz, 5, 6);
        int door = cz + 3;
        entrance(s, c, 1, door, "south");
        s.fill(c - 2, 1, cz - 2, c - 2, 13, cz - 2, Kit.ladder("south"));
        // ground floor: the focus, the post and its chest, the brewing corner
        s.set(c, 1, cz, FOCUS);
        s.set(c - 2, 1, cz, WORK_POST);
        s.set(c - 2, 1, cz + 1, Kit.chest("east"));
        s.set(c + 2, 1, cz - 2, BREWING).set(c + 2, 1, cz - 1, CAULDRON);
        s.set(c + 1, 1, cz - 2, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c + 1, 2, cz - 2, Furnish.candles(3));
        s.set(c + 2, 1, cz + 2, BOOKS).set(c + 2, 2, cz + 2, SOUL_LANTERN);
        soulLamp(s, c, 5, cz);
        lamp(s, c - 2, 6, cz + 2);
        lamp(s, c + 2, 6, cz + 1);
        Nordic.window(s, c - 3, 3, cz, "west", 2, true);
        Nordic.window(s, c + 3, 3, cz, "east", 2, true);
        // the library and the bed of the mage
        int y1 = 9;
        Furnish.books(s, c - 1, cz - 2, c + 1, cz - 2, y1, y1 + 1);
        Furnish.books(s, c - 2, cz, c - 2, cz + 2, y1, y1 + 1);
        Nordic.closedBed(s, c + 2, y1, cz + 1, "north", "blue", true);
        s.set(c, y1, cz, lectern("south"));
        Nordic.furs(s, c - 1, cz + 1, c, cz + 2, y1);
        lamp(s, c, 12, cz + 1);
        Nordic.window(s, c, y1 + 1, cz + 3, "south", 2, true);
        // the research room in the lantern
        int y2 = 14;
        s.set(c, y2, cz, ENCHANTING);
        Furnish.books(s, c + 2, cz - 2, c + 2, cz + 2, y2, y2);
        Furnish.books(s, c - 1, cz + 2, c + 1, cz + 2, y2, y2);
        s.set(c + 2, y2 + 1, cz - 2, SOUL_LANTERN).set(c + 2, y2 + 1, cz + 2, SOUL_LANTERN).set(c - 1, y2 + 1, cz + 2, SOUL_LANTERN);
        s.set(c - 2, y2, cz + 1, Kit.stairs(Nordic.FLOOR_STAIRS, "west"));
        soulLamp(s, c, y2 + 4, cz);
        // the rune stones and the path
        for (int z = door + 1; z < s.sizeZ; z++) {
            s.set(c, 0, z, Kit.slab(Nordic.FOOTING_SLAB));
        }
        runeStone(s, c - 3, door + 3);
        runeStone(s, c + 3, door + 3);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 0, door + 1}, List.of());
    }

    /**
     * Rank A: a taller and wider tower nine wide: the ground floor on a base of rough stone under a skirt, the stave storey
     * of the mages, the lantern room and the crown of crossing gables with dragon heads and a needle; a carved porch with a
     * gable and a dragon head before the door, rune stones before it. Inside: the focus with the post and its chest and the
     * brewing room on the ground floor; the library with two closed beds of the mages above; the research room with the
     * enchanting table and its shelves in the lantern.
     */
    private static Drawn mageA() {
        Structure s = new Structure(13, 36, 19);
        int c = 6;
        int cz = 8;
        groundStorey(s, c, cz, 4, 0, 8, 3);
        skirt(s, c, cz, 4, 8, 2);
        upperStorey(s, c, cz, 4, 9, 14);
        lanternRoom(s, c, cz, 4, 15, 19);
        int ridge = crown(s, c, cz, 4, 19, "ns", RUNE_GLASS);
        spike(s, c, cz, ridge, 6);
        soulEaves(s, c, cz, 6, 7);
        int door = cz + 4;
        Nordic.portal(s, c, 1, door, "south", true, Nordic.ROOF, Nordic.BOARD, Nordic.SPIRE);
        s.fill(c - 3, 1, cz - 3, c - 3, 15, cz - 3, Kit.ladder("south"));
        // ground floor: the focus, the post and its chest, the brewing room
        Nordic.furs(s, c - 1, cz - 1, c + 1, cz + 1, 1);
        s.set(c, 1, cz, FOCUS);
        s.set(c - 2, 1, cz, WORK_POST);
        s.set(c - 2, 1, cz + 1, Kit.chest("east"));
        Furnish.books(s, c - 1, cz - 3, c + 1, cz - 3, 1, 2);
        s.set(c + 3, 1, cz - 3, BREWING).set(c + 3, 1, cz - 2, CAULDRON);
        s.set(c + 3, 1, cz - 1, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c + 3, 2, cz - 1, BREWING);
        s.set(c + 2, 1, cz - 3, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c + 2, 2, cz - 3, Furnish.candles(3));
        s.set(c + 3, 1, cz + 3, BOOKS).set(c + 3, 2, cz + 3, SOUL_LANTERN);
        s.set(c - 3, 1, cz + 3, BOOKS).set(c - 3, 2, cz + 3, SOUL_LANTERN);
        soulLamp(s, c, 6, cz);
        lamp(s, c - 2, 7, cz + 3);
        lamp(s, c + 2, 7, cz + 3);
        lamp(s, c + 2, 7, cz - 2);
        Nordic.window(s, c - 4, 5, cz, "west", 2, true);
        Nordic.window(s, c + 4, 5, cz, "east", 2, true);
        // the library and the beds of the mages
        int y1 = 10;
        Furnish.books(s, c - 2, cz - 3, c + 2, cz - 3, y1, y1 + 2);
        Furnish.books(s, c - 3, cz, c - 3, cz + 1, y1, y1 + 1);
        Nordic.closedBed(s, c + 3, y1, cz + 2, "north", "blue", true);
        Nordic.closedBed(s, c, y1, cz + 3, "west", "cyan", true);
        s.set(c - 1, y1, cz, lectern("south"));
        s.set(c + 1, y1, cz - 1, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c + 1, y1 + 1, cz - 1, Furnish.candles(2));
        s.set(c + 1, y1, cz, Kit.stairs(Nordic.FLOOR_STAIRS, "south"));
        lamp(s, c, 14, cz + 1);
        lamp(s, c - 2, 14, cz + 2);
        Nordic.window(s, c, y1 + 1, cz + 4, "south", 2, true);
        Nordic.window(s, c + 4, y1 + 1, cz - 1, "east", 2, true);
        // the research room in the lantern
        int y2 = 16;
        s.set(c, y2, cz, ENCHANTING);
        Furnish.books(s, c + 2, cz - 2, c + 2, cz + 2, y2, y2);
        Furnish.books(s, c - 1, cz + 2, c + 1, cz + 2, y2, y2);
        s.set(c + 2, y2 + 1, cz - 2, SOUL_LANTERN).set(c + 2, y2 + 1, cz + 2, SOUL_LANTERN).set(c - 1, y2 + 1, cz + 2, SOUL_LANTERN);
        s.set(c - 2, y2, cz, Kit.stairs(Nordic.FLOOR_STAIRS, "west"));
        soulLamp(s, c, y2 + 4, cz);
        // the rune stones and the path
        for (int z = door + 3; z < s.sizeZ; z++) {
            s.set(c, 0, z, Kit.slab(Nordic.FOOTING_SLAB));
        }
        runeStone(s, c - 4, door + 4);
        runeStone(s, c + 4, door + 4);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, door + 1}, List.of());
    }

    /**
     * Rank S: the tower grown to three stave storeys between two skirts, the lantern and the crown of crossing gables with
     * dragon heads and a needle; beside it on the east a hall of staves under a steep roof with painted gables, crossed boards
     * and dragon heads, the library and the brewing room of the guild, its gable to the street with its own door. Inside the
     * tower: the focus with the post and its chest on the ground floor; the dormitory with three closed beds of the mages;
     * the research room with the enchanting table and its shelves; the observatory with a telescope and a chart of the stars
     * in the lantern.
     */
    private static Drawn mageS() {
        Structure s = new Structure(19, 42, 21);
        int c = 5;
        int cz = 8;
        groundStorey(s, c, cz, 4, 0, 8, 3);
        skirt(s, c, cz, 4, 8, 1);
        upperStorey(s, c, cz, 4, 9, 14);
        upperStorey(s, c, cz, 4, 15, 20);
        skirt(s, c, cz, 4, 20, 1);
        lanternRoom(s, c, cz, 4, 21, 25);
        int ridge = crown(s, c, cz, 4, 25, "ns", RUNE_GLASS);
        spike(s, c, cz, ridge, 6);
        soulEaves(s, c, cz, 5, 7);
        soulEaves(s, c, cz, 5, 19);
        int door = cz + 4;
        entrance(s, c, 1, door, "south");

        // the hall of the library and the brewing room
        int hx1 = 10;
        int hx2 = 16;
        int hz1 = 6;
        int hz2 = 16;
        int plate = 5;
        Nordic.footing(s, hx1, hz1, hx2, hz2, 0);
        Nordic.staveWalls(s, hx1, 1, hz1, hx2, plate, hz2);
        Nordic.Field f = Nordic.gableField(hx1, hz1, hx2, hz2, plate, false, Nordic.STEEP);
        Nordic.Area free = (x, z) -> x >= hx1 || z > cz + 5;
        for (int z = hz1 - 1; z <= cz + 4; z++) {
            s.set(hx1, 8, z, AIR);
        }
        Nordic.roof(s, f, free, hx1 - 1, hz1 - 1, hx2 + 1, hz2 + 1, Nordic.ROOF);
        Nordic.gableWall(s, f, false, hz1, hx1, hx2, plate + 1, Nordic.PAINT);
        Nordic.gableWall(s, f, false, hz2, hx1, hx2, plate + 1, Nordic.PAINT);
        Nordic.crossedBoards(s, false, hz1 - 1, hx1 - 1, hx2 + 1, Nordic.ROOF, Nordic.BOARD, true);
        Nordic.crossedBoards(s, false, hz2 + 1, hx1 - 1, hx2 + 1, Nordic.ROOF, Nordic.BOARD, true);
        int hc = (hx1 + hx2) / 2;
        int hallRidge = f.at(hc, hz1);
        Nordic.dragonHead(s, hc, hallRidge + 1, hz1 - 1, "north", Nordic.SPIRE);
        Nordic.dragonHead(s, hc, hallRidge + 1, hz2 + 1, "south", Nordic.SPIRE);
        entrance(s, hc, 1, hz2, "south");
        s.door(c + 4, 1, cz + 1, Nordic.DOOR, "east", "left");
        s.set(hx1, 1, cz + 1, AIR).set(hx1, 2, cz + 1, AIR);
        Nordic.window(s, hx2, 3, hz1 + 3, "east", 2, true);
        Nordic.window(s, hx2, 3, hz2 - 3, "east", 2, true);
        Nordic.window(s, hc, 7, hz2, "south", 2, true);
        // the library at the back of the hall
        Nordic.furs(s, hc - 1, hz1 + 2, hc + 1, hz1 + 3, 1);
        Furnish.books(s, hx1 + 1, hz1 + 1, hx2 - 1, hz1 + 1, 1, 3);
        Furnish.books(s, hx2 - 1, hz1 + 2, hx2 - 1, hz1 + 4, 1, 2);
        s.set(hc, 1, hz1 + 3, lectern("south"));
        s.fill(hc - 1, 1, hz1 + 5, hc + 1, 1, hz1 + 5, Kit.topSlab(Nordic.FLOOR_SLAB));
        s.set(hc - 1, 2, hz1 + 5, Furnish.candles(2)).set(hc + 1, 2, hz1 + 5, Furnish.candles(3));
        // the brewing room at the front
        s.set(hx2 - 1, 1, hz2 - 1, BREWING).set(hx2 - 1, 1, hz2 - 2, CAULDRON).set(hx2 - 1, 1, hz2 - 3, BREWING);
        s.set(hx1 + 1, 1, hz2 - 1, Kit.topSlab(Nordic.FLOOR_SLAB)).set(hx1 + 1, 2, hz2 - 1, BREWING);
        s.set(hx1 + 1, 1, hz2 - 2, CAULDRON);
        s.set(hx1 + 1, 1, hz2 - 3, Kit.topSlab(Nordic.FLOOR_SLAB)).set(hx1 + 1, 2, hz2 - 3, Furnish.candles(3));
        s.set(hx2 - 1, 1, hz2 - 4, BOOKS).set(hx2 - 1, 2, hz2 - 4, SOUL_LANTERN);
        lamp(s, hc, 5, hz1 + 3);
        lamp(s, hc, 5, hz2 - 2);
        soulLamp(s, hc, 6, hz1 + 6);

        // the tower: ground floor with the focus, the post and its chest
        s.fill(c - 3, 1, cz - 3, c - 3, 21, cz - 3, Kit.ladder("south"));
        s.set(c, 1, cz, FOCUS);
        s.set(c - 2, 1, cz, WORK_POST);
        s.set(c - 2, 1, cz + 1, Kit.chest("east"));
        runeRing(s, c, 1, cz);
        Furnish.books(s, c - 2, cz - 3, c + 1, cz - 3, 1, 2);
        s.set(c + 3, 1, cz - 3, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c + 3, 2, cz - 3, Furnish.candles(3));
        s.set(c + 3, 1, cz + 3, BOOKS).set(c + 3, 2, cz + 3, SOUL_LANTERN);
        s.set(c - 3, 1, cz + 3, BOOKS).set(c - 3, 2, cz + 3, SOUL_LANTERN);
        soulLamp(s, c, 6, cz);
        lamp(s, c - 2, 7, cz + 3);
        lamp(s, c + 2, 7, cz - 2);
        Nordic.window(s, c - 4, 5, cz, "west", 2, true);
        // the dormitory of the mages
        int y1 = 10;
        Nordic.closedBed(s, c + 3, y1, cz + 2, "north", "blue", true);
        Nordic.closedBed(s, c, y1, cz + 3, "west", "cyan", true);
        Nordic.closedBed(s, c - 3, y1, cz + 1, "north", "light_blue", true);
        Furnish.books(s, c - 2, cz - 3, c + 1, cz - 3, y1, y1 + 1);
        s.set(c + 3, y1, cz - 3, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c + 3, y1 + 1, cz - 3, Furnish.candles(2));
        Nordic.furs(s, c - 1, cz - 1, c + 1, cz, y1);
        lamp(s, c, 14, cz);
        Nordic.window(s, c, y1 + 1, cz + 4, "south", 2, true);
        Nordic.window(s, c + 4, y1 + 1, cz - 2, "east", 2, true);
        // the research room
        int y2 = 16;
        s.set(c, y2, cz, ENCHANTING);
        Furnish.books(s, c + 2, cz - 2, c + 2, cz + 2, y2, y2 + 1);
        Furnish.books(s, c - 1, cz + 2, c + 1, cz + 2, y2, y2);
        s.set(c - 1, y2 + 1, cz + 2, SOUL_LANTERN);
        s.set(c - 3, y2, cz + 1, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c - 3, y2 + 1, cz + 1, Furnish.candles(3));
        s.set(c - 3, y2, cz + 2, Kit.stairs(Nordic.FLOOR_STAIRS, "west"));
        s.set(c + 2, y2, cz - 3, BREWING).set(c + 3, y2, cz - 3, CAULDRON);
        lamp(s, c - 2, 20, cz + 2);
        soulLamp(s, c, 20, cz - 2);
        Nordic.window(s, c - 4, y2 + 1, cz, "west", 2, true);
        Nordic.window(s, c, y2 + 1, cz + 4, "south", 2, true);
        // the observatory in the lantern
        int y3 = 22;
        starChart(s, c, y3, cz, 1);
        telescope(s, c + 1, y3, cz + 1, "south");
        s.set(c - 1, y3, cz + 1, Kit.stairs(Nordic.FLOOR_STAIRS, "north"));
        s.set(c + 2, y3, cz - 2, BOOKS).set(c + 2, y3 + 1, cz - 2, SOUL_LANTERN);
        s.set(c - 2, y3, cz + 2, BOOKS).set(c - 2, y3 + 1, cz + 2, SOUL_LANTERN);
        soulLamp(s, c, y3 + 4, cz);

        // the paths and the rune stones
        for (int z = door + 1; z < s.sizeZ; z++) {
            s.set(c, 0, z, Kit.slab(Nordic.FOOTING_SLAB));
        }
        for (int z = hz2 + 1; z < s.sizeZ; z++) {
            s.set(hc, 0, z, Kit.slab(Nordic.FOOTING_SLAB));
        }
        runeStone(s, c - 3, door + 4);
        runeStone(s, c + 3, door + 5);
        runeStone(s, hx2 + 1, hz2 + 3);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 0, door + 1}, List.of());
    }

    /**
     * Rank SS: a cluster of seers' towers. The main tower eleven wide rises in four stave storeys under two skirts to its
     * lantern and a great crown of crossing gables with dragon heads at its four ends and a needle, a gallery with small
     * arches round its foot; before it at the two front corners two lower towers seven wide with their own lanterns and
     * crowns, joined to the gallery by covered walks. Inside the main tower: the focus with the post and its chest on the
     * ground floor; the library; the research room with the enchanting table among its shelves; the hall of the seers with
     * the high seat in a ring of rune stones; the observatory with telescopes and a chart of the stars. The west tower holds
     * the brewing room, the east tower the scriptorium; above each two closed beds of the mages.
     */
    private static Drawn mageSS() {
        Structure s = new Structure(25, 48, 27);
        int c = 12;
        int cz = 9;
        // the main tower
        groundStorey(s, c, cz, 5, 0, 8, 3);
        skirt(s, c, cz, 5, 8, 1);
        upperStorey(s, c, cz, 5, 9, 14);
        upperStorey(s, c, cz, 5, 15, 20);
        skirt(s, c, cz, 5, 20, 1);
        upperStorey(s, c, cz, 5, 21, 26);
        lanternRoom(s, c, cz, 5, 27, 31);
        int ridge = crown(s, c, cz, 5, 31, "nswe", RUNE_GLASS);
        spike(s, c, cz, ridge, 4);
        soulEaves(s, c, cz, 6, 19);
        Nordic.gallery(s, c - 5, cz - 5, c + 5, cz + 5, 0, 2, 4, Nordic.STEEP, Nordic.ROOF, "s");
        entrance(s, c, 1, cz + 5, "south");
        s.set(c, 0, cz + 6, Nordic.FLOOR);
        // the side towers and the covered walks to the gallery
        for (int tx : new int[]{4, 20}) {
            int tz = 22;
            int out = tx < c ? -1 : 1;
            groundStorey(s, tx, tz, 3, 0, 7, 2);
            skirt(s, tx, tz, 3, 7, 1);
            upperStorey(s, tx, tz, 3, 8, 12);
            lanternRoom(s, tx, tz, 3, 13, 17);
            int top = crown(s, tx, tz, 3, 17, "", RUNE_GLASS);
            spike(s, tx, tz, top, 5);
            int wx = tx - 2 * out;
            s.fill(wx - 1, 0, 16, wx + 1, 0, 18, Nordic.FLOOR);
            s.fill(wx, 1, 16, wx - out, 3, 16, AIR);
            s.fill(wx - 1, 1, 18, wx - 1, 3, 18, POST).fill(wx + 1, 1, 18, wx + 1, 3, 18, POST);
            s.set(wx - 1, 1, 17, Nordic.FENCE).set(wx + 1, 1, 17, Nordic.FENCE);
            s.fill(wx - 1, 4, 17, wx + 1, 4, 18, Kit.slab(Nordic.ROOF.slab()));
            s.set(wx, 3, 17, Kit.hangingLantern());
            s.door(wx, 1, tz - 3, Nordic.DOOR, "south", "left");
            s.fill(tx + 2 * out, 1, tz - 2, tx + 2 * out, 13, tz - 2, Kit.ladder("south"));
            // two beds of the mages above
            int y1 = 9;
            Nordic.closedBed(s, tx - 2 * out, y1, tz + 1, "north", tx < c ? "blue" : "cyan", true);
            Nordic.closedBed(s, tx, y1, tz + 2, tx < c ? "west" : "east", tx < c ? "light_blue" : "purple", true);
            lamp(s, tx, 12, tz);
            Nordic.window(s, tx, y1 + 1, tz + 3, "south", 2, true);
            Nordic.window(s, tx + 3 * out, y1 + 1, tz - 1, tx < c ? "west" : "east", 2, true);
            Nordic.window(s, tx + 3 * out, 4, tz, tx < c ? "west" : "east", 2, true);
            Nordic.window(s, tx, 4, tz + 3, "south", 2, true);
            // the lantern: a seat among soul lanterns
            s.set(tx, 14, tz, Kit.stairs(Nordic.FLOOR_STAIRS, "north"));
            s.set(tx - out, 14, tz + 2, BOOKS).set(tx - out, 15, tz + 2, SOUL_LANTERN);
            s.set(tx + out, 14, tz + 2, BOOKS).set(tx + out, 15, tz + 2, SOUL_LANTERN);
            soulLamp(s, tx, 18, tz);
            soulLamp(s, tx, 6, tz);
            lamp(s, tx - out, 6, tz + 2);
        }
        // the brewing room in the west tower, the scriptorium in the east tower
        s.set(2, 1, 22, CAULDRON).set(2, 1, 23, BREWING);
        s.set(2, 1, 24, Kit.topSlab(Nordic.FLOOR_SLAB)).set(2, 2, 24, Furnish.candles(3));
        s.set(4, 1, 24, Kit.topSlab(Nordic.FLOOR_SLAB)).set(4, 2, 24, BREWING);
        s.set(5, 1, 24, CAULDRON);
        s.set(6, 1, 24, BOOKS).set(6, 2, 24, SOUL_LANTERN);
        Furnish.books(s, 22, 21, 22, 24, 1, 2);
        Furnish.books(s, 19, 24, 21, 24, 1, 1);
        s.set(20, 1, 22, Kit.topSlab(Nordic.FLOOR_SLAB)).set(20, 2, 22, Furnish.candles(2));
        s.set(20, 1, 23, Kit.stairs(Nordic.FLOOR_STAIRS, "south"));
        s.set(19, 2, 24, SOUL_LANTERN);

        // the main tower: the focus, the post and its chest
        s.fill(c - 4, 1, cz - 4, c - 4, 27, cz - 4, Kit.ladder("south"));
        s.set(c, 1, cz, FOCUS);
        s.set(c - 2, 1, cz, WORK_POST);
        s.set(c - 2, 1, cz + 1, Kit.chest("east"));
        runeRing(s, c, 1, cz);
        Furnish.books(s, c - 2, cz - 4, c + 4, cz - 4, 1, 2);
        s.set(c + 4, 1, cz + 4, BOOKS).set(c + 4, 2, cz + 4, SOUL_LANTERN);
        s.set(c - 4, 1, cz + 4, BOOKS).set(c - 4, 2, cz + 4, SOUL_LANTERN);
        s.set(c + 4, 1, cz, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c + 4, 2, cz, Furnish.candles(3));
        soulLamp(s, c, 6, cz);
        lamp(s, c - 3, 7, cz + 3);
        lamp(s, c + 3, 7, cz + 3);
        lamp(s, c + 3, 7, cz - 3);
        // the library
        int y1 = 10;
        Furnish.books(s, c - 3, cz - 4, c + 4, cz - 4, y1, y1 + 2);
        Furnish.books(s, c + 4, cz - 3, c + 4, cz + 3, y1, y1 + 2);
        Furnish.books(s, c - 4, cz, c - 4, cz + 3, y1, y1 + 2);
        Furnish.books(s, c - 1, cz - 1, c + 1, cz - 1, y1, y1 + 1);
        Furnish.books(s, c - 1, cz + 2, c + 1, cz + 2, y1, y1 + 1);
        s.set(c, y1, cz + 4, lectern("north"));
        s.set(c + 2, y1, cz + 4, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c + 2, y1 + 1, cz + 4, Furnish.candles(3));
        s.set(c - 2, y1, cz + 4, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c - 2, y1 + 1, cz + 4, Furnish.candles(2));
        lamp(s, c - 2, 14, cz + 1);
        lamp(s, c + 2, 14, cz + 1);
        lamp(s, c, 14, cz - 3);
        Nordic.window(s, c, y1 + 1, cz + 5, "south", 2, true);
        Nordic.window(s, c - 5, y1 + 1, cz - 2, "west", 2, true);
        // the research room
        int y2 = 16;
        s.set(c, y2, cz, ENCHANTING);
        for (int d = -2; d <= 2; d++) {
            s.set(c + d, y2, cz - 2, BOOKS).set(c + 2, y2, cz + d, BOOKS);
        }
        s.set(c - 2, y2, cz - 1, BOOKS).set(c - 2, y2 + 1, cz - 2, SOUL_LANTERN).set(c + 2, y2 + 1, cz + 2, SOUL_LANTERN);
        s.set(c - 3, y2, cz + 3, BREWING).set(c - 4, y2, cz + 3, CAULDRON);
        s.set(c + 3, y2, cz + 3, Kit.topSlab(Nordic.FLOOR_SLAB)).set(c + 3, y2 + 1, cz + 3, Furnish.candles(3));
        s.set(c + 4, y2, cz + 3, Kit.stairs(Nordic.FLOOR_STAIRS, "east"));
        lamp(s, c - 3, 20, cz + 1);
        lamp(s, c + 3, 20, cz - 2);
        soulLamp(s, c, 20, cz + 3);
        Nordic.window(s, c, y2 + 1, cz + 5, "south", 2, true);
        Nordic.window(s, c + 5, y2 + 1, cz, "east", 2, true);
        // the hall of the seers
        int y3 = 22;
        Nordic.highSeat(s, c, y3, cz - 4, "south");
        Nordic.furs(s, c - 1, cz, c + 1, cz + 2, y3);
        for (int[] d : new int[][]{{-3, 0}, {3, 0}, {-3, 3}, {3, 3}, {0, 4}}) {
            runeStoneInside(s, c + d[0], y3, cz + d[1]);
        }
        Nordic.tapestry(s, c + 4, cz - 2, c + 4, cz + 2, y3 + 2, "west");
        lamp(s, c, 26, cz + 1);
        Nordic.window(s, c - 5, y3 + 1, cz, "west", 2, true);
        Nordic.window(s, c, y3 + 1, cz + 5, "south", 2, true);
        // the observatory in the lantern
        int y4 = 28;
        starChart(s, c, y4, cz, 2);
        telescope(s, c + 2, y4, cz + 2, "south");
        telescope(s, c - 2, y4, cz - 2, "north");
        s.set(c + 1, y4, cz + 2, Kit.stairs(Nordic.FLOOR_STAIRS, "east"));
        s.set(c + 3, y4, cz - 3, BOOKS).set(c + 3, y4 + 1, cz - 3, SOUL_LANTERN);
        s.set(c - 3, y4, cz + 3, BOOKS).set(c - 3, y4 + 1, cz + 3, SOUL_LANTERN);
        soulLamp(s, c, y4 + 5, cz);

        // the path, rune stones and soul lanterns on posts
        for (int z = cz + 7; z < s.sizeZ; z++) {
            for (int x = c - 1; x <= c + 1; x++) {
                s.set(x, 0, z, Kit.slab(Nordic.FOOTING_SLAB));
            }
        }
        runeStone(s, c - 3, 20);
        runeStone(s, c + 3, 20);
        runeStone(s, c - 3, 25);
        runeStone(s, c + 3, 25);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, cz + 6}, List.of());
    }

    // ---------------------------------------------------------------- shapes of the seers' towers

    /**
     * The ground storey of a tower: a footing of rough stone with a floor of boards on layer {@code floor} (with rough stone
     * under it down to the ground), a sloping skirt of rough stairs round its foot on layer 0, stave walls {@code r} blocks
     * out from (cx, cz) up to the plate at {@code plate}, of rough stone up to layer {@code stone}.
     */
    private static void groundStorey(Structure s, int cx, int cz, int r, int floor, int plate, int stone) {
        for (int y = 0; y < floor; y++) {
            s.fill(cx - r, y, cz - r, cx + r, y, cz + r, Nordic.FOOTING);
        }
        Nordic.footing(s, cx - r, cz - r, cx + r, cz + r, floor);
        Nordic.staveWalls(s, cx - r, floor + 1, cz - r, cx + r, plate, cz + r);
        for (int y = floor + 1; y <= stone; y++) {
            s.walls(cx - r, y, cz - r, cx + r, y, cz + r, Nordic.FOOTING);
        }
        Nordic.pentRoof(s, cx - r, cz - r, cx + r, cz + r, 0, 1, Nordic.PITCH, ROUGH);
    }

    /**
     * An upper storey of a tower: a floor of boards on the layer of its sill {@code floor}, stave walls {@code r} blocks out
     * from (cx, cz) up to the plate at {@code plate}.
     */
    private static void upperStorey(Structure s, int cx, int cz, int r, int floor, int plate) {
        s.fill(cx - r + 1, floor, cz - r + 1, cx + r - 1, floor, cz + r - 1, Nordic.FLOOR);
        Nordic.staveWalls(s, cx - r, floor, cz - r, cx + r, plate, cz + r);
    }

    /**
     * A skirt of the spires around the storey above, whose walls stand {@code r} blocks out from (cx, cz): its eaves
     * {@code depth} blocks beyond those walls on the layer {@code plate} (the plate of the storey below), rising steeply
     * towards them.
     */
    private static void skirt(Structure s, int cx, int cz, int r, int plate, int depth) {
        Nordic.pentRoof(s, cx - r, cz - r, cx + r, cz + r, plate, depth, Nordic.STEEP, Nordic.SPIRE);
    }

    /**
     * The lantern room on top of a seer's tower: an upper storey whose walls between the corner posts are pale blue glass two
     * layers high, from the second layer above its floor.
     */
    private static void lanternRoom(Structure s, int cx, int cz, int r, int floor, int plate) {
        upperStorey(s, cx, cz, r, floor, plate);
        for (int y = floor + 2; y <= floor + 3; y++) {
            for (int u = -r + 1; u <= r - 1; u++) {
                s.set(cx + u, y, cz - r, RUNE_GLASS).set(cx + u, y, cz + r, RUNE_GLASS);
                s.set(cx - r, y, cz + u, RUNE_GLASS).set(cx + r, y, cz + u, RUNE_GLASS);
            }
        }
    }

    /**
     * Soul lanterns hanging under the four corners of a skirt whose eaves lie {@code d} blocks out from (cx, cz) on the
     * layer {@code y + 1}.
     */
    private static void soulEaves(Structure s, int cx, int cz, int d, int y) {
        for (int[] k : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            put(s, cx + k[0] * d, y, cz + k[1] * d, SOUL_HANGING);
        }
    }

    /**
     * A door in a wall looking {@code facing}, at (x, y, z), framed by posts of logs and a lintel, with a step of rough slab
     * before it on the layer below.
     */
    private static void entrance(Structure s, int x, int y, int z, String facing) {
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        s.door(x, y, z, Nordic.DOOR, Furnish.opposite(facing), "left");
        for (int side = -1; side <= 1; side += 2) {
            s.fill(x + a[0] * side, y, z + a[1] * side, x + a[0] * side, y + 1, z + a[1] * side, POST);
        }
        s.fill(x - a[0], y + 2, z - a[1], x + a[0], y + 2, z + a[1], Kit.log(Nordic.LOG, a[0] != 0 ? "x" : "z"));
        put(s, x + f[0], y - 1, z + f[1], Kit.slab(Nordic.FOOTING_SLAB));
    }

    /**
     * A rune stone at (x, z): a block of rough stone in the ground, a standing stone of carved blocks two layers high and a
     * soul lantern burning on its top.
     */
    private static void runeStone(Structure s, int x, int z) {
        s.set(x, 0, z, Nordic.FOOTING);
        s.set(x, 1, z, Kit.CARVED).set(x, 2, z, Kit.CARVED);
        s.set(x, 3, z, SOUL_LANTERN);
    }

    /**
     * A small rune stone standing on a floor at (x, y, z): a carved block with a soul lantern on it.
     */
    private static void runeStoneInside(Structure s, int x, int y, int z) {
        s.set(x, y, z, Kit.CARVED).set(x, y + 1, z, SOUL_LANTERN);
    }

    /**
     * A ring of rune carpets round the focus at (x, y, z): light blue and cyan carpets on the eight cells around it.
     */
    private static void runeRing(Structure s, int x, int y, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dz != 0) {
                    s.set(x + dx, y, z + dz, (dx + dz) % 2 == 0 ? "minecraft:cyan_carpet" : "minecraft:light_blue_carpet");
                }
            }
        }
    }

    /**
     * A chart of the stars on the floor of an observatory around (x, z) on layer {@code y}: a square of black carpet
     * {@code 2r + 1} wide sprinkled with yellow and white stars.
     */
    private static void starChart(Structure s, int x, int y, int z, int r) {
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int hash = Math.floorMod(dx * 5 + dz * 3, 7);
                s.set(x + dx, y, z + dz, hash == 0 ? "minecraft:yellow_carpet" : hash == 3 ? "minecraft:white_carpet" : "minecraft:black_carpet");
            }
        }
    }

    /**
     * A telescope at (x, y, z) pointing out of the window on the side {@code facing}: a tube of a lightning rod lying on a
     * post.
     */
    private static void telescope(Structure s, int x, int y, int z, String facing) {
        s.set(x, y, z, Nordic.FENCE);
        s.set(x, y + 1, z, "minecraft:lightning_rod[facing=" + facing + ",powered=false,waterlogged=false]");
    }

    // ================================================================ small shapes

    /**
     * A lantern hanging at (x, y, z) on a chain up to the first solid block over it: a beam, a floor or the underside of a
     * roof.
     */
    private static void lamp(Structure s, int x, int y, int z) {
        s.set(x, y, z, Kit.hangingLantern());
        for (int yy = y + 1; yy < s.sizeY && !s.solid(x, yy, z); yy++) {
            s.set(x, yy, z, Kit.chain());
        }
    }

    /**
     * A soul lantern hanging at (x, y, z) on a chain up to the first solid block over it.
     */
    private static void soulLamp(Structure s, int x, int y, int z) {
        lamp(s, x, y, z);
        s.set(x, y, z, SOUL_HANGING);
    }

    /**
     * The lowest solid layer above layer {@code y} in the column (x, z): the ceiling to hang a cauldron or a lantern from.
     */
    private static int ceiling(Structure s, int x, int y, int z) {
        int yy = y + 1;
        while (yy < s.sizeY && !s.solid(x, yy, z)) {
            yy++;
        }
        return yy;
    }

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }
}
