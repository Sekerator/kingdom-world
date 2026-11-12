package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The two guilds of a capital in the style "terem", each a program drawn for the rank of its kingdom, from B for a weak one
 * to SS for a strong one.
 *
 * <p>The adventurers' guild is a house of the druzhina, a guest court of the bold: a log hall on a stone base with a high
 * carved porch, its great hall with long tables, the stove, the contract board, the counter of the registrar and trophies,
 * and the rooms of the guild upstairs with maps and books. The rank grows it: B a hall with its loft and porch; A adds a
 * gallery and a watch tower under a tent; S a long hall with two towers and a forecourt behind a palisade with a gate; SS a
 * grand guild court, a great hall under a barrel roof with towers and galleries round a court, the banners of the guild on
 * its towers, one of the main buildings of a capital after the castle.
 *
 * <p>The mages' guild lives in its tower: a tall wooden tower on a stone base, its log frames stepping in as it rises like
 * a tented bell tower, square below and eight-sided above, an open gallery near the top for watching the stars, a tent with
 * little windows and an onion dome on it, windows of purple and magenta glass in carved frames, amethyst and end rods at
 * the crown. On its ground floor the magic focus with the post of the mage and its chest; books, a lectern, brewing stands
 * and cauldrons, a research room with an enchanting table, and real beds for the mages: one in B, two in A, three in S and
 * four in SS. A grows the tower and gives it a porch; S adds a side tower and a library hall; SS is a tower court: the
 * main tower with two lower towers joined by galleries, a library hall, a brewing room and an observatory.
 *
 * <p>Drawn with the shapes of {@link Terem}; coordinates as everywhere: x from west to east, z from the back to the front
 * (the entrance looks south), y up; layer 0 stands on the ground and is the street.
 */
final class TeremGuilds {
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
    private static final String HAY = "minecraft:hay_block[axis=y]";
    /** The guild's own colour on its banners, the same in every kingdom. */
    private static final String GUILD = "black";
    private static final String PURPLE = Kit.stainedPane("purple");
    private static final String MAGENTA = Kit.stainedPane("magenta");

    /**
     * A drawn building with its named places: the door, and the guards by the desk.
     */
    private record Drawn(Structure structure, int[] door, List<int[]> guards) {
    }

    private TeremGuilds() {
    }

    /**
     * The adventurers' guilds terem_guild_b, _a, _s and _ss (trade, type guild, with their door and the guard by the desk)
     * and the mage towers terem_mage_tower_b, _a, _s and _ss (production, role mage_tower, with their door), all standing on
     * the ground with their anchor in the middle of their front edge.
     */
    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Rank rank : Rank.values()) {
            String key = rank.name().toLowerCase(Locale.ROOT);
            Drawn guild = guild(rank);
            Structure g = guild.structure();
            BuildingGenerator.Building b = new BuildingGenerator.Building("terem_guild_" + key, "trade", "guild", "kingdomworld:guild_desk",
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
            list.add(new BuildingGenerator.Building("terem_mage_tower_" + key, "production", "mage_tower", "kingdomworld:magic_focus",
                    (t.sizeX - 1) / 2, 0, t.sizeZ - 1, t, "mage_tower", 1, "")
                    .at("door", tower.door()[0], tower.door()[1], tower.door()[2]));
        }
        return list;
    }

    // ================================================================ the adventurers' guild

    /** The floor of the halls on their stone base, and the layer one stands on in them. */
    private static final int FLOOR = 2;
    private static final int STAND = 3;

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
     * Rank B: a log hall on a stone base, its high gable to the street under a steep carved roof, a high porch with a tent
     * before the door, the black banners of the guild beside it. Inside the great hall: the stove in the back corner, the
     * counter of the registrar with the desk, his post and his chest, trophies on the back wall, the contract board, a long
     * table with benches and the red corner; a ladder climbs to the loft of the guild with its map, books and map table.
     */
    private static Drawn guildB() {
        Structure s = new Structure(13, 18, 15);
        int x1 = 1;
        int x2 = 11;
        int z1 = 1;
        int z2 = 10;
        int top = 8;
        base(s, x1, z1, x2, z2);
        Terem.logWalls(s, x1, FLOOR, z1, x2, top, z2);
        s.fill(x1 + 1, top, z1 + 1, x2 - 1, top, z2 - 1, Terem.PLANKS);
        // the windows: one to the street beside the porch, three to the sides, the window of the loft in the gable
        Terem.window(s, 8, 4, z2, "south", 1, 2, true);
        Terem.window(s, x1, 4, 6, "west", 1, 2, true);
        Terem.window(s, x2, 4, 3, "east", 1, 2, true);
        Terem.window(s, x2, 4, 7, "east", 1, 2, true);
        Terem.window(s, 6, 10, z2, "south", 1, 2, true);
        int ridge = Terem.gableRoof(s, x1, z1, x2, z2, top, false, 2);
        towelOff(s, 6, 10, 11, z2 + 1);
        konek(s, 6, ridge, z2 + 2, "south");
        Terem.porch(s, 3, STAND, z2, "south", 0, 1, false);
        flag(s, 3, STAND + 9, z2 + 2, 0);
        s.set(2, 5, z2 + 1, Kit.wallBanner(GUILD, "south")).set(4, 5, z2 + 1, Kit.wallBanner(GUILD, "south"));

        // the great hall
        Terem.stove(s, 3, STAND, 4, "south", 14);
        chimney(s, 3, 15, 4, 2);
        registrar(s, 9, STAND, 5, "east");
        trophies(s, 6, STAND, z1 + 1, "south", 2);
        contractBoard(s, 6, STAND, z2 - 1, "north", 3, false);
        Terem.feastTable(s, 4, 6, 7, 6, STAND, true);
        Terem.redCorner(s, x2 - 1, STAND, z2 - 1, "south", "east");
        Terem.lavka(s, 8, z2 - 1, 9, z2 - 1, STAND);
        Terem.candleChandelier(s, 6, 6, top - 1, 1);
        Furnish.chandelier(s, 9, 3, top - 1, 1);
        ladder(s, x1 + 1, STAND, 7, "east", top);

        // the loft of the guild under the roof
        int loft = top + 1;
        Furnish.books(s, 4, z1 + 1, 8, z1 + 1, loft, loft + 1);
        guildMap(s, 4, loft + 2, z1 + 1);
        mapTable(s, 6, loft, 5);
        Terem.lavka(s, x1 + 1, 3, x1 + 1, 5, loft);
        Terem.lavka(s, x2 - 1, 3, x2 - 1, 8, loft);
        Furnish.chandelier(s, 6, 4, 14, 2);
        Furnish.chandelier(s, 6, 8, 14, 2);
        s.set(6, loft + 1, z2 - 1, Kit.wallBanner(GUILD, "north"));
        s.connect();
        return new Drawn(s, new int[]{3, STAND, z2 + 1}, List.of(new int[]{8, STAND, 6}));
    }

    /**
     * Rank A: the hall of B grown deeper, with a covered gallery on posts and a plinth of stone along its east wall leading
     * to a watch tower at its back corner: a log tower whose lookout juts out over brackets under a tent with the banner of
     * the guild on its point. Before the gallery a fenced and paved yard where the adventurers train, with a dummy of hay
     * and a target. Inside the hall a long table runs from the counter to the door, and a stair climbs along the west wall
     * to the loft of the guild.
     */
    private static Drawn guildA() {
        Structure s = new Structure(19, 30, 19);
        int x1 = 1;
        int x2 = 11;
        int z1 = 1;
        int z2 = 13;
        int top = 8;
        base(s, x1, z1, x2, z2);
        Terem.logWalls(s, x1, FLOOR, z1, x2, top, z2);
        s.fill(x1 + 1, top, z1 + 1, x2 - 1, top, z2 - 1, Terem.PLANKS);
        Terem.window(s, 8, 4, z2, "south", 1, 2, true);
        Terem.window(s, x1, 4, 5, "west", 1, 2, true);
        Terem.window(s, 6, 10, z2, "south", 1, 2, true);
        // under the roof of the gallery the windows of the east wall need no frames
        s.fill(x2, 4, 8, x2, 5, 9, Terem.MICA);
        int ridge = Terem.gableRoof(s, x1, z1, x2, z2, top, false, 2);
        towelOff(s, 6, 10, 11, z2 + 1);
        konek(s, 6, ridge, z2 + 2, "south");
        Terem.porch(s, 3, STAND, z2, "south", 0, 1, false);
        flag(s, 3, STAND + 9, z2 + 2, 0);
        s.set(2, 5, z2 + 1, Kit.wallBanner(GUILD, "south")).set(4, 5, z2 + 1, Kit.wallBanner(GUILD, "south"));

        // the gallery on a plinth along the east wall of the hall, its door from the hall, the tower at its end; its roof
        // leans on the wall, which stays whole under it, and a railing closes its open end
        int tx = 14;
        int tz = 4;
        Terem.gallery(s, x2 + 1, 7, x2 + 3, 12, STAND, 0, false);
        s.fill(x2, top - 2, 7, x2, top - 1, 12, Kit.log(Terem.LOG, "z"));
        plinth(s, x2 + 1, 7, x2 + 3, 12, FLOOR);
        s.set(x2 + 1, STAND, 10, AIR).set(x2 + 2, STAND, 12, Terem.CARVE_FENCE);
        s.door(x2, STAND, 10, Terem.DOOR, "west", "left");
        watchTower(s, tx, tz, 2, FLOOR, 13, 1, false);
        s.door(x2 + 2, STAND, tz + 2, Terem.DOOR, "north", "left");
        trainingYard(s, 13, 14, 18, 18);

        // the great hall
        Terem.stove(s, 3, STAND, 4, "south", 14);
        chimney(s, 3, 15, 4, 2);
        registrar(s, 9, STAND, 5, "east");
        trophies(s, 6, STAND, z1 + 1, "south", 2);
        contractBoard(s, 5, STAND, z2 - 1, "north", 3, false);
        Terem.feastTable(s, 7, 7, 7, 10, STAND, true);
        Terem.redCorner(s, x2 - 1, STAND, z2 - 1, "south", "east");
        Terem.lavka(s, x2 - 1, 11, x2 - 1, 11, STAND);
        Terem.candleChandelier(s, 7, 7, top - 1, 1);
        Terem.candleChandelier(s, 7, 10, top - 1, 1);
        Furnish.chandelier(s, 9, 3, top - 1, 1);
        flight(s, x1 + 1, STAND, z2 - 1, "north", 6);
        lamps(s, 0, top - 1, 0, new int[]{x1 + 2, 10}, new int[]{8, z2 - 1});
        s.set(x1 + 2, top + 1, 8, Terem.FENCE).set(x1 + 2, top + 1, 9, Terem.FENCE).set(x1 + 1, top + 1, 10, Terem.FENCE);

        // the loft of the guild
        int loft = top + 1;
        Furnish.books(s, 4, z1 + 1, 8, z1 + 1, loft, loft + 1);
        guildMap(s, 4, loft + 2, z1 + 1);
        mapTable(s, 6, loft, 5);
        mapTable(s, 6, loft, 9);
        Terem.lavka(s, x2 - 1, 3, x2 - 1, 11, loft);
        Furnish.chandelier(s, 6, 4, 14, 2);
        Furnish.chandelier(s, 6, 8, 14, 2);
        Furnish.chandelier(s, 6, 11, 14, 2);
        s.set(5, loft + 1, z2 - 1, Kit.wallBanner(GUILD, "north")).set(7, loft + 1, z2 - 1, Kit.wallBanner(GUILD, "north"));
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{3, STAND, z2 + 1}, List.of(new int[]{x2 - 1, STAND, 6}));
    }

    /**
     * A watch tower of the guild (vezha) around (cx, cz): a square log frame of half-size {@code h} on a base of rough
     * stone, an upper room halfway up, and at layer {@code lookout} the lookout jutting out one block on every side over
     * brackets, three courses high with mica windows on its four sides; over it a tent with a watch lantern on its top
     * (smotrilnya) or a gilt needle, and on the point the banner of the guild. A ladder climbs inside along the west wall
     * from the floor to the lookout; a rack of weapons and a skull on a pike stand in the upper room.
     *
     * @param floor the layer of its floor (the tower stands on its base up to there)
     * @param eave  how far the eaves of the tent reach beyond the walls of the lookout: 0 or 1
     * @param watch whether the tent carries a watch lantern instead of the needle
     */
    private static void watchTower(Structure s, int cx, int cz, int h, int floor, int lookout, int eave, boolean watch) {
        int logs = Math.max(floor, 1);
        s.fill(cx - h, 0, cz - h, cx + h, logs - 1, cz + h, Terem.FOOTING);
        Terem.logWalls(s, cx - h, logs, cz - h, cx + h, lookout - 1, cz + h);
        s.fill(cx - h + 1, floor, cz - h + 1, cx + h - 1, floor, cz + h - 1, Terem.FLOOR);
        int room = (floor + lookout) / 2;
        s.fill(cx - h + 1, room, cz - h + 1, cx + h - 1, room, cz + h - 1, Terem.PLANKS);
        // the brackets, the floor and the walls of the lookout
        for (int dz = -h - 1; dz <= h + 1; dz++) {
            for (int dx = -h - 1; dx <= h + 1; dx++) {
                if (Math.abs(dx) == h + 1 || Math.abs(dz) == h + 1) {
                    put(s, cx + dx, lookout, cz + dz, slope(Terem.STAIRS, dx, dz, h + 1, Terem.SQUARE, true));
                } else {
                    put(s, cx + dx, lookout, cz + dz, Terem.FLOOR);
                }
            }
        }
        Terem.logWalls(s, cx - h - 1, lookout + 1, cz - h - 1, cx + h + 1, lookout + 3, cz + h + 1, Terem.LOG, false);
        for (String side : new String[]{"south", "west", "east", "north"}) {
            int[] d = Furnish.step(side);
            int[] a = Furnish.step(Furnish.left(side));
            for (int u = -1; u <= 1; u++) {
                put(s, cx + d[0] * (h + 1) + a[0] * u, lookout + 2, cz + d[1] * (h + 1) + a[1] * u, Terem.MICA);
            }
        }
        int eaves = lookout + 4;
        if (watch) {
            int flat = Terem.tent(s, Terem.EVERYWHERE, cx, cz, h + 1 + eave, eaves, Terem.SQUARE, Terem.SQUARE, Terem.TENT_RISES, 0, 1);
            int tip = Terem.watch(s, cx, flat, cz);
            flag(s, cx, tip - 1, cz, 0);
        } else {
            int tip = Terem.tent(s, Terem.EVERYWHERE, cx, cz, h + 1 + eave, eaves, Terem.SQUARE, Terem.SQUARE, Terem.TENT_RISES, 0, -1);
            flag(s, cx, tip - 1, cz, 0);
        }
        ladder(s, cx - h + 1, floor + 1, cz, "east", lookout);
        put(s, cx + 1, room - 1, cz, Kit.hangingLantern());
        put(s, cx + 1, lookout + 3, cz, Kit.hangingLantern());
        put(s, cx + h - 1, room + 1, cz - h + 1, "minecraft:grindstone[face=floor,facing=south]");
        put(s, cx + h - 1, room + 1, cz + h - 1, Terem.FENCE);
        put(s, cx + h - 1, room + 2, cz + h - 1, "minecraft:skeleton_skull[rotation=0]");
    }

    /**
     * The yard where the adventurers train, fenced from (x1, z1) to (x2, z2) and paved with rough stone, a walk of boards
     * from its gate: a dummy of hay on a post with arms and a pumpkin head, a target on a bale of hay, a rack of weapons, a
     * lamp on a post and a gate to the street on its south side.
     */
    private static void trainingYard(Structure s, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (x == x1 || x == x2 || z == z1 || z == z2) {
                    put(s, x, 0, z, Terem.FENCE);
                }
            }
        }
        int gx = (x1 + x2) / 2;
        put(s, gx, 0, z2, "minecraft:spruce_fence_gate[facing=south,in_wall=false,open=false,powered=false]");
        pave(s, x1 + 1, z1 + 1, x2 - 1, z2 - 1, gx, 0, z1 + 1, z2 - 1);
        int mx = x1 + 2;
        int mz = z1 + 2;
        s.set(mx, 0, mz, Terem.FENCE).set(mx, 1, mz, HAY).set(mx, 2, mz, "minecraft:carved_pumpkin[facing=south]");
        s.set(mx - 1, 1, mz, Terem.FENCE).set(mx + 1, 1, mz, Terem.FENCE);
        s.set(x2 - 1, 0, z1 + 1, HAY).set(x2 - 1, 1, z1 + 1, "minecraft:target[power=0]");
        s.set(x2 - 1, 0, z2 - 1, "minecraft:grindstone[face=floor,facing=west]");
        s.set(x1 + 1, 0, z2 - 1, Terem.FENCE).set(x1 + 1, 1, z2 - 1, Kit.lantern());
    }

    /**
     * Rank S: a long hall of two storeys with its long side to a forecourt, a steep roof with a carved cross gable over
     * the middle (the svetlitsa, the upper chamber of the guild) and a high porch under a barrel roof before the door. The
     * forecourt lies behind a palisade of sharpened logs with a gate under a tent; two watch towers stand in it before the
     * corners of the hall, each with its lookout jutting out under a tent and the banner of the guild on the point; a dummy,
     * a target, banners on poles and lamps on posts stand in the forecourt, paved with stone and a walk of boards from the
     * gate. Inside the great
     * hall two long tables, the stove, the counter of the registrar, the trophies and the contract board; a stair to the
     * upper floor with the council table, the books of the guild and the map room in the svetlitsa.
     */
    private static Drawn guildS() {
        Structure s = new Structure(23, 29, 25);
        int x1 = 3;
        int x2 = 19;
        int z1 = 1;
        int z2 = 11;
        int upper = 8;
        int top = 12;
        base(s, x1, z1, x2, z2);
        Terem.logWalls(s, x1, FLOOR, z1, x2, top, z2);
        s.fill(x1 + 1, upper, z1 + 1, x2 - 1, upper, z2 - 1, Terem.PLANKS);
        for (int x : new int[]{6, 16}) {
            Terem.window(s, x, 4, z2, "south", 1, 2, true);
        }
        for (int x : new int[]{5, 17}) {
            Terem.window(s, x, 9, z2, "south", 1, 2, true);
        }
        for (int x : new int[]{8, 14}) {
            Terem.window(s, x, 4, z1, "north", 1, 2, true);
            Terem.window(s, x, 9, z1, "north", 1, 2, true);
        }
        Terem.window(s, x1, 9, 3, "west", 1, 2, true);
        Terem.window(s, x2, 9, 6, "east", 1, 2, true);
        Terem.window(s, x1, 14, 6, "west", 1, 2, false);
        Terem.window(s, x2, 14, 6, "east", 1, 2, false);
        int ridge = Terem.gableRoof(s, x1, z1, x2, z2, top, true, 2);
        towelOff(s, x1 - 1, 14, 15, 6);
        towelOff(s, x2 + 1, 14, 15, 6);
        konek(s, x1 - 2, ridge, 6, "west");
        konek(s, x2 + 2, ridge, 6, "east");
        crossGable(s, 8, 6, 14, z2, upper, 14);
        s.set(11, ridge, 6, Kit.log(Terem.POST, "x"));
        s.fill(11, ridge, 4, 11, ridge, 5, AIR);
        Terem.porch(s, 11, STAND, z2, "south", 0, -1, true);

        // the towers before the corners of the hall, the palisade and the gate of the forecourt
        watchTower(s, 4, 16, 2, 0, 12, 0, false);
        watchTower(s, 18, 16, 2, 0, 12, 0, false);
        s.door(6, 1, 16, Terem.DOOR, "west", "left");
        s.door(16, 1, 16, Terem.DOOR, "east", "left");
        tyn(s, 2, 21, 8, 21, 4);
        tyn(s, 14, 21, 20, 21, 4);
        tyn(s, 2, 20, 2, 20, 4);
        tyn(s, 20, 20, 20, 20, 4);
        tentGate(s, 11, 21, 2);
        // the forecourt: a dummy and a target, the banners of the guild on poles beside the gate
        s.set(14, 0, 17, Terem.FENCE).set(14, 1, 17, HAY).set(14, 2, 17, "minecraft:carved_pumpkin[facing=west]");
        s.set(14, 1, 16, Terem.FENCE).set(14, 1, 18, Terem.FENCE);
        s.set(8, 0, 16, HAY).set(8, 1, 16, "minecraft:target[power=0]").set(8, 0, 17, "minecraft:grindstone[face=floor,facing=east]");
        flagpole(s, 8, 0, 20, 5);
        flagpole(s, 14, 0, 20, 5);
        pave(s, x1, z2 + 1, x2, 20, 11, 1, 15, 20);
        for (int x : new int[]{x1 + 1, x2 - 1}) {
            s.set(x, 1, z2 + 2, Terem.FENCE).set(x, 2, z2 + 2, Kit.lantern());
        }

        // the great hall
        Terem.stove(s, 5, STAND, 4, "south", 18);
        chimney(s, 5, 19, 4, 2);
        registrar(s, 16, STAND, 5, "east");
        trophies(s, 9, STAND, z1 + 1, "south", 5);
        contractBoard(s, x1 + 1, STAND, 7, "east", 3, false);
        Terem.redCorner(s, x1 + 1, STAND, z2 - 1, "south", "west");
        Terem.feastTable(s, 6, 7, 9, 7, STAND, true);
        Terem.feastTable(s, 12, 7, 15, 7, STAND, true);
        Terem.candleChandelier(s, 8, 7, upper - 1, 1);
        Terem.candleChandelier(s, 14, 7, upper - 1, 1);
        Furnish.chandelier(s, 11, 9, upper - 1, 1);
        Furnish.chandelier(s, 16, 3, upper - 1, 1);
        flight(s, x2 - 1, STAND, z2 - 1, "north", 6);

        // the upper floor: the council table, books, the map room in the svetlitsa
        int up = upper + 1;
        s.set(x2 - 2, up, 6, Terem.FENCE).set(x2 - 2, up, 7, Terem.FENCE).set(x2 - 1, up, 8, Terem.FENCE);
        Furnish.books(s, 9, z1 + 1, 13, z1 + 1, up, up + 1);
        Terem.feastTable(s, 8, 4, 14, 4, up, true);
        Terem.lavka(s, x1 + 1, 5, x1 + 1, 9, up);
        guildMapZ(s, x1 + 1, up + 1, 5);
        s.door(11, up, 6, Terem.DOOR, "south", "left");
        mapTable(s, 11, up, 8);
        Furnish.books(s, 9, 9, 9, 10, up, up + 1);
        Furnish.books(s, 13, 9, 13, 10, up, up + 1);
        lamp(s, 11, 13, 4);
        lamp(s, 6, 13, 6);
        lamp(s, 16, 13, 6);
        lamp(s, 11, 14, 9);
        lamp(s, 6, 6, 9);
        lamps(s, 11, top - 1, 6, new int[]{-6, -3}, new int[]{-6, 3}, new int[]{6, 3}, new int[]{6, -3});
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{11, STAND, z2 + 1}, List.of(new int[]{17, STAND, 6}));
    }

    /**
     * A cross gable (the svetlitsa, the bright upper chamber) rising out of the front slope of a roof whose ridge runs along
     * x: a log frame from (x1, z1) to (x2, z2) whose front wall stands on the front wall of the hall, from the upper floor
     * at layer {@code floor} up to its eaves at {@code top}, under a steep carved gable roof of its own with its gable to the
     * street, a window with a carved frame in it and the towel and horse head of its ridge. The roof of the hall is cleared
     * inside it.
     */
    private static void crossGable(Structure s, int x1, int z1, int x2, int z2, int floor, int top) {
        s.fill(x1 + 1, floor + 1, z1 + 1, x2 - 1, top + 8, z2, AIR);
        s.fill(x1, top - 1, z2 + 1, x2, top + 8, z2 + 1, AIR);
        Terem.logWalls(s, x1, floor + 1, z1, x2, top, z2);
        int mid = (x1 + x2) / 2;
        Terem.window(s, mid, top - 2, z2, "south", 1, 2, true);
        int ridge = Terem.gableRoof(s, x1, z1, x2, z2, top, false, 2);
        towelOff(s, mid, top - 2, top - 1, z2 + 1);
        konek(s, mid, ridge, z2 + 2, "south");
    }

    /**
     * A tyn: a palisade of upright logs along x or z from layer 0, {@code height} high, sharpened in pairs on its top.
     */
    private static void tyn(Structure s, int x1, int z1, int x2, int z2, int height) {
        boolean alongX = z1 == z2;
        int a1 = alongX ? x1 : z1;
        int a2 = alongX ? x2 : z2;
        for (int a = a1; a <= a2; a++) {
            int x = alongX ? a : x1;
            int z = alongX ? z1 : a;
            s.fill(x, 0, z, x, height - 1, z, Kit.log(Terem.LOG, "y"));
            String facing = (a - a1) % 2 == 0 ? (alongX ? "east" : "south") : (alongX ? "west" : "north");
            s.set(x, height, z, Kit.stairs(Terem.STAIRS, facing));
        }
    }

    /**
     * A gate of the forecourt under a tent: four hewn posts at the corners of a square of half-size {@code r} around the
     * gate at (cx, cz) in a palisade along x, a ring of beams on them, the tent with the banner of the guild on its point,
     * the leaves of the gate standing open, a lantern hanging in the gateway and the banner of the guild on the front beam,
     * outside over the way in, so that nothing hangs in the gateway before those who pass through it.
     */
    private static void tentGate(Structure s, int cx, int cz, int r) {
        int beam = 5;
        for (int dx = -r; dx <= r; dx += 2 * r) {
            for (int dz = -r; dz <= r; dz += 2 * r) {
                s.fill(cx + dx, 0, cz + dz, cx + dx, beam - 1, cz + dz, Kit.log(Terem.POST, "y"));
            }
        }
        s.fill(cx - r, beam, cz - r, cx + r, beam, cz - r, Kit.log(Terem.POST, "x"));
        s.fill(cx - r, beam, cz + r, cx + r, beam, cz + r, Kit.log(Terem.POST, "x"));
        s.fill(cx - r, beam, cz - r + 1, cx - r, beam, cz + r - 1, Kit.log(Terem.POST, "z"));
        s.fill(cx + r, beam, cz - r + 1, cx + r, beam, cz + r - 1, Kit.log(Terem.POST, "z"));
        // the gateway in the palisade: posts at its sides, a carved lintel, the open leaves
        s.fill(cx - r, 0, cz, cx - r, beam - 1, cz, Kit.log(Terem.POST, "y"));
        s.fill(cx + r, 0, cz, cx + r, beam - 1, cz, Kit.log(Terem.POST, "y"));
        s.fill(cx - r + 1, beam - 1, cz, cx + r - 1, beam - 1, cz, Terem.CARVE);
        s.set(cx - r + 1, beam - 2, cz, Kit.upside(Terem.CARVE_STAIRS, "east")).set(cx + r - 1, beam - 2, cz, Kit.upside(Terem.CARVE_STAIRS, "west"));
        s.set(cx - r + 1, 0, cz, "minecraft:spruce_fence_gate[facing=west,in_wall=false,open=true,powered=false]");
        s.set(cx + r - 1, 0, cz, "minecraft:spruce_fence_gate[facing=east,in_wall=false,open=true,powered=false]");
        put(s, cx, beam, cz + r + 1, Kit.wallBanner(GUILD, "south"));
        s.set(cx, beam - 2, cz, Kit.hangingLantern());
        put(s, cx, beam - 1, cz - r, Kit.hangingLantern());
        s.set(cx, beam, cz, Kit.log(Terem.POST, "x"));
        int tip = Terem.tent(s, Terem.EVERYWHERE, cx, cz, r + 1, beam + 1, Terem.SQUARE, Terem.SQUARE, Terem.TENT_RISES, 0, -1);
        flag(s, cx, tip - 1, cz, 0);
        pave(s, cx - r + 1, cz - r, cx + r - 1, cz + r, cx, r, cz - r, cz + r);
    }

    /**
     * A pole of the timber {@code height} high from layer {@code y} with the banner of the guild on its top.
     */
    private static void flagpole(Structure s, int x, int y, int z, int height) {
        s.fill(x, y, z, x, y + height - 1, z, Terem.FENCE);
        s.set(x, y + height, z, Kit.banner(GUILD, 0));
    }

    /**
     * Rank SS: the guild court, one of the main buildings of a capital after the castle. At the back of a court the great
     * hall of two storeys under a barrel roof with carved kokoshniki at its ends and three onion domes on its ridge, a high
     * porch under a tent before its door; covered galleries on carved posts over plinths of stone along both sides of the
     * court lead from the hall to two watch towers at the front corners, each with its lookout and a watch lantern carrying
     * the banner of the guild; between them a palisade and the gate under a tall tent. The court is paved with stone and a
     * walk of boards from the gate to the porch; in it a well, a dummy, a target and the banners of the guild. Inside, the
     * great hall with two long tables, the stove, the counter of the registrar, the trophies and the contract board, and over
     * it the vaulted upper hall of the guild with its council table, books and maps, lit by horoses of lanterns.
     */
    private static Drawn guildSS() {
        Structure s = new Structure(31, 40, 33);
        int x1 = 5;
        int x2 = 25;
        int z1 = 2;
        int z2 = 12;
        int upper = 8;
        int top = 14;
        int c = 15;
        base(s, x1, z1, x2, z2);
        Terem.logWalls(s, x1, FLOOR, z1, x2, top, z2);
        s.fill(x1 + 1, upper, z1 + 1, x2 - 1, upper, z2 - 1, Terem.PLANKS);
        for (int x : new int[]{9, 21}) {
            Terem.window(s, x, 4, z2, "south", 1, 2, true);
        }
        for (int x : new int[]{9, 20}) {
            Terem.window(s, x, 4, z1, "north", 1, 2, true);
        }
        for (int x : new int[]{9, 12, 18, 21}) {
            Terem.window(s, x, 10, z2, "south", 1, 2, true);
            Terem.window(s, x, 10, z1, "north", 1, 2, true);
        }
        for (int z : new int[]{4, 10}) {
            Terem.window(s, x1, 10, z, "west", 1, 2, true);
        }
        for (int z : new int[]{4, 8}) {
            Terem.window(s, x2, 10, z, "east", 1, 2, true);
        }
        int ridge = Terem.barrelRoof(s, x1 - 1, x2 + 1, 7, top, 13, true, Terem.ROOF, null, true, true);
        Terem.window(s, x1, 17, 7, "west", 1, 2, false);
        Terem.window(s, x2, 17, 7, "east", 1, 2, false);
        Terem.onionDome(s, 9, ridge, 7, 5);
        Terem.onionDome(s, 21, ridge, 7, 5);
        Terem.onionDome(s, c, ridge, 7, 7);
        Terem.porch(s, c, STAND, z2, "south", 0, -1, false);
        flag(s, c, STAND + 9, z2 + 2, 0);

        // the galleries round the court and the towers at its front corners; under the galleries and the landings that
        // join them to the hall and to the towers a plinth of stone, so that no hollow is left under their boards
        Terem.gallery(s, 3, 14, 7, 24, STAND, 0, false);
        Terem.gallery(s, 23, 14, 27, 24, STAND, 0, false);
        for (int x : new int[]{6, 24}) {
            s.door(x, STAND, z2, Terem.DOOR, "north", "left");
        }
        watchTower(s, 5, 28, 2, FLOOR, 15, 1, true);
        watchTower(s, 25, 28, 2, FLOOR, 15, 1, true);
        for (int x : new int[]{5, 25}) {
            s.door(x, STAND, 26, Terem.DOOR, "south", "left");
        }
        for (int gx : new int[]{3, 23}) {
            plinth(s, gx, z2 + 1, gx + 4, 25, FLOOR);
            for (int z : new int[]{z2 + 1, 25}) {
                for (int x = gx; x <= gx + 4; x++) {
                    if (!s.solid(x, FLOOR, z)) {
                        s.set(x, FLOOR, z, Terem.FLOOR);
                    }
                }
            }
            for (int x : new int[]{gx, gx + 4}) {
                if (!s.solid(x, STAND, z2 + 1)) {
                    s.set(x, STAND, z2 + 1, Terem.CARVE_FENCE);
                }
            }
        }
        // the palisade and the gate
        tyn(s, 8, 28, 11, 28, 5);
        tyn(s, 19, 28, 22, 28, 5);
        tentGate(s, c, 28, 3);
        // the court: a well, a dummy and a target, racks of weapons, the banners of the guild; paved with stone and a walk
        // of boards from the gate to the porch
        well(s, c, 20);
        s.set(10, 0, 18, Terem.FENCE).set(10, 1, 18, HAY).set(10, 2, 18, "minecraft:carved_pumpkin[facing=south]");
        s.set(9, 1, 18, Terem.FENCE).set(11, 1, 18, Terem.FENCE);
        s.set(20, 0, 17, HAY).set(20, 1, 17, "minecraft:target[power=0]").set(20, 0, 18, HAY);
        s.set(9, 0, 23, "minecraft:grindstone[face=floor,facing=east]").set(21, 0, 23, "minecraft:grindstone[face=floor,facing=west]");
        flagpole(s, 11, 0, 25, 6);
        flagpole(s, 19, 0, 25, 6);
        for (int x : new int[]{10, 20}) {
            s.set(x, 0, 21, Terem.FENCE).set(x, 1, 21, Kit.lantern());
        }
        pave(s, 8, z2 + 1, 22, 27, c, 1, z2 + 4, 27);

        // the great hall
        Terem.stove(s, 7, STAND, 5, "south", 20);
        chimney(s, 7, 21, 5, 2);
        registrar(s, 22, STAND, 6, "east");
        trophies(s, 11, STAND, z1 + 1, "south", 9);
        contractBoard(s, x1 + 1, STAND, 8, "east", 3, false);
        Terem.feastTable(s, 9, 8, 13, 8, STAND, true);
        Terem.feastTable(s, 17, 8, 21, 8, STAND, true);
        Terem.candleChandelier(s, 11, 8, upper - 1, 1);
        Terem.candleChandelier(s, 19, 8, upper - 1, 1);
        Furnish.chandelier(s, c, 10, upper - 1, 1);
        Furnish.chandelier(s, 22, 4, upper - 1, 1);
        Furnish.chandelier(s, 7, 9, upper - 1, 1);
        Furnish.chandelier(s, 13, 5, upper - 1, 1);
        flight(s, x2 - 1, STAND, z2 - 2, "north", 6);

        // the upper hall under the vault
        int up = upper + 1;
        s.set(x2 - 2, up, 6, Terem.FENCE).set(x2 - 2, up, 7, Terem.FENCE).set(x2 - 1, up, 8, Terem.FENCE);
        for (int x = x1 + 2; x <= x2 - 2; x++) {
            if (x != 9 && x != 12 && x != 18 && x != 21 && x != 7) {
                Furnish.books(s, x, z1 + 1, x, z1 + 1, up, up + 1);
            }
        }
        Terem.feastTable(s, 10, 7, 20, 7, up, true);
        guildMapZ(s, x1 + 1, up + 1, 5);
        mapTable(s, 9, up, 10);
        mapTable(s, 21, up, 10);
        Terem.lavka(s, 13, z2 - 1, 17, z2 - 1, up);
        Terem.redCorner(s, x2 - 1, up, z2 - 1, "south", "east");
        s.set(c - 1, up + 3, z2 - 1, Kit.wallBanner(GUILD, "north")).set(c + 1, up + 3, z2 - 1, Kit.wallBanner(GUILD, "north"));
        horos(s, c, 7, 22, 13);
        horos(s, 10, 7, 22, 13);
        horos(s, 20, 7, 22, 13);
        lamp(s, 8, 12, 5);
        lamp(s, 22, 12, 5);
        for (int x : new int[]{8, 13, 17, 22}) {
            lamp(s, x, 12, 10);
        }
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, STAND, z2 + 2}, List.of(new int[]{23, STAND, 7}));
    }

    /**
     * A well of the court: a frame of logs around a cauldron of water, two posts with a beam and a little roof of shingles
     * over it, a lantern hanging from the beam.
     */
    private static void well(Structure s, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    s.set(x + dx, 0, z + dz, Kit.log(Terem.LOG, dz == 0 ? "z" : "x"));
                }
            }
        }
        s.set(x, 0, z, CAULDRON);
        s.fill(x - 1, 1, z, x - 1, 2, z, Kit.log(Terem.POST, "y")).fill(x + 1, 1, z, x + 1, 2, z, Kit.log(Terem.POST, "y"));
        s.fill(x - 1, 3, z, x + 1, 3, z, Kit.log(Terem.POST, "x"));
        s.fill(x - 2, 3, z - 1, x + 2, 3, z - 1, Kit.stairs(Terem.ROOF.stairs(), "south"));
        s.fill(x - 2, 3, z + 1, x + 2, 3, z + 1, Kit.stairs(Terem.ROOF.stairs(), "north"));
        s.fill(x - 2, 4, z, x + 2, 4, z, Kit.slab(Terem.ROOF.slab()));
        s.set(x - 2, 3, z, Kit.upside(Terem.CARVE_STAIRS, "east")).set(x + 2, 3, z, Kit.upside(Terem.CARVE_STAIRS, "west"));
        s.set(x, 2, z, Kit.hangingLantern());
    }

    // ---------------------------------------------------------------- parts of the guild

    /**
     * The stone base of a log frame: two layers of rough stone under its walls, the floor of the hall on it.
     */
    private static void base(Structure s, int x1, int z1, int x2, int z2) {
        s.fill(x1, 0, z1, x2, FLOOR - 1, z2, Terem.FOOTING);
        s.fill(x1 + 1, FLOOR, z1 + 1, x2 - 1, FLOOR, z2 - 1, Terem.FLOOR);
    }

    /**
     * Takes away the lower end of the carved towel of a gable where it would hang before the window of the gable: the posts
     * of white wood at (x, z) from layer {@code y1} to {@code y2}.
     */
    private static void towelOff(Structure s, int x, int y1, int y2, int z) {
        for (int y = y1; y <= y2; y++) {
            if (s.get(x, y, z).startsWith(Terem.CARVE_FENCE)) {
                s.set(x, y, z, AIR);
            }
        }
    }

    /**
     * The carved horse head (konek) on the end of a ridge log: a block of white wood with a head of stairs looking out.
     *
     * @param y the layer of the ridge log
     */
    private static void konek(Structure s, int x, int y, int z, String facing) {
        put(s, x, y + 1, z, Kit.stairs(Terem.CARVE_STAIRS, facing));
    }

    /**
     * The banner of the guild on the point of a tent: the rod of its finial at (x, y, z) becomes a pole of the timber
     * carrying the banner, which looks the way {@code rotation} says.
     */
    private static void flag(Structure s, int x, int y, int z, int rotation) {
        put(s, x, y, z, Terem.FENCE);
        put(s, x, y + 1, z, Kit.banner(GUILD, rotation));
    }

    /**
     * A chimney of brick on the stack of a stove where it leaves the roof: {@code height} layers and a pot.
     */
    private static void chimney(Structure s, int x, int y, int z, int height) {
        s.fill(x, y, z, x, y + height - 1, z, Kit.CHIMNEY);
        s.set(x, y + height, z, Kit.CHIMNEY_POT);
    }

    /**
     * The counter of the registrar with the guild desk in its middle looking south, his post two blocks behind it and the
     * one chest of the guild beside the post on the side {@code side}, shelves of books against the wall behind.
     *
     * @param x the column of the desk
     * @param z the row of the counter
     */
    private static void registrar(Structure s, int x, int y, int z, String side) {
        int d = side.equals("east") ? 1 : -1;
        s.set(x - 1, y, z, Kit.topSlab(Terem.FLOOR_SLAB)).set(x - 1, y + 1, z, Furnish.candles(2));
        s.set(x, y, z, DESK);
        s.set(x + 1, y, z, Kit.topSlab(Terem.FLOOR_SLAB)).set(x + 1, y + 1, z, Kit.lantern());
        s.set(x, y, z - 2, WORK_POST);
        s.set(x + d, y, z - 2, Kit.chest(d > 0 ? "west" : "east"));
        Furnish.books(s, x - 1, z - 3, x + 1, z - 3, y, y + 1);
    }

    /**
     * Trophies along a wall that looks {@code facing}, {@code count} cells from (x, z) toward the left of {@code facing}:
     * racks of weapons (grindstones) with the skulls of beasts hung over them, and between them pikes carrying skulls; the
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
                s.set(px, y, pz, Terem.FENCE).set(px, y + 1, pz, "minecraft:skeleton_skull[rotation=" + rotation(facing) + "]");
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
     * The contract board on a wall, looking {@code facing}: a board of white birch planks {@code width} wide and four high,
     * a ledge before it with papers (white carpet) and candles, notices of white and yellow cloth hanging on the board.
     *
     * @param x     the column of its middle against the wall
     * @param frame whether hewn posts frame its ends
     */
    private static void contractBoard(Structure s, int x, int y, int z, String facing, int width, boolean frame) {
        int[] f = Furnish.step(facing);
        int[] a = Furnish.step(Furnish.left(facing));
        int half = (width - 1) / 2;
        for (int u = -half; u <= half; u++) {
            int px = x + a[0] * u;
            int pz = z + a[1] * u;
            s.fill(px, y, pz, px, y + 3, pz, Terem.CARVE);
            int fx = px + f[0];
            int fz = pz + f[1];
            s.set(fx, y, fz, Kit.topSlab(Terem.FLOOR_SLAB));
            s.set(fx, y + 1, fz, u == 0 ? Furnish.candles(3) : "minecraft:white_carpet");
            String color = Math.floorMod(u, 3) == 1 ? "yellow" : "white";
            s.set(fx, y + 3, fz, Kit.wallBanner(color, facing));
        }
        // the frame of hewn posts at its ends
        for (int side = -1; frame && side <= 1; side += 2) {
            int px = x + a[0] * side * (half + 1);
            int pz = z + a[1] * side * (half + 1);
            s.fill(px, y, pz, px, y + 3, pz, Kit.log(Terem.POST, "y"));
        }
    }

    /**
     * A ladder against the wall behind it (the wall lies opposite to {@code facing}), from layer {@code y} up through the
     * ceiling at {@code top}.
     */
    private static void ladder(Structure s, int x, int y, int z, String facing, int top) {
        s.fill(x, y, z, x, top, z, Kit.ladder(facing));
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

    /**
     * The same map painted on a wall that runs along z, from (x, y, z) five blocks toward the front.
     */
    private static void guildMapZ(Structure s, int x, int y, int z) {
        Structure map = new Structure(5, 2, 1);
        guildMap(map, 0, 0, 0);
        for (int i = 0; i < 5; i++) {
            for (int row = 0; row < 2; row++) {
                s.set(x, y + row, z + i, map.get(i, row, 0));
            }
        }
    }

    /**
     * A lantern hanging at (x, y, z) on a chain up to the first solid block over it: a beam, a ceiling or the underside of
     * a roof.
     */
    private static void lamp(Structure s, int x, int y, int z) {
        s.set(x, y, z, Kit.hangingLantern());
        for (int yy = y + 1; yy < s.sizeY && !s.solid(x, yy, z); yy++) {
            s.set(x, yy, z, Kit.chain());
        }
    }

    /**
     * Lanterns that light the corners of a room: one at each offset from (cx, cz) at layer {@code y}, hanging on a chain from
     * the ceiling or the roof at most eight layers over it; a cell that is taken, or has nothing over it to hang from, is
     * left as it is.
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
                    if (!s.get(x, yy, z).startsWith("minecraft:chain") && !s.get(x, yy, z).startsWith("minecraft:lantern")) {
                        lamp(s, x, y, z);
                    }
                    break;
                }
            }
        }
    }

    /**
     * A great horos of lanterns for a high hall: a chain from the vault at {@code top} down to a hub of carved posts at
     * layer {@code hub} with four arms, a lantern standing on each arm.
     */
    private static void horos(Structure s, int x, int z, int top, int hub) {
        for (int yy = hub + 1; yy <= top; yy++) {
            put(s, x, yy, z, Kit.chain());
        }
        put(s, x, hub, z, Terem.CARVE_FENCE);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            put(s, x + d[0], hub, z + d[1], Terem.CARVE_FENCE);
            put(s, x + d[0], hub + 1, z + d[1], Kit.lantern());
        }
    }

    /**
     * Paves a court on layer 0 from (x1, z1) to (x2, z2) wherever nothing stands yet: rough stone, and a walk of boards
     * {@code half} blocks to each side of the column {@code walkX} from row {@code walkZ1} to row {@code walkZ2}, so that
     * no bare ground shows within the walls of the guild.
     */
    private static void pave(Structure s, int x1, int z1, int x2, int z2, int walkX, int half, int walkZ1, int walkZ2) {
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                if (s.inside(x, 0, z) && !s.solid(x, 0, z)) {
                    boolean walk = Math.abs(x - walkX) <= half && z >= walkZ1 && z <= walkZ2;
                    s.set(x, 0, z, walk ? Terem.FLOOR : Terem.FOOTING);
                }
            }
        }
    }

    /**
     * A plinth of rough stone under a raised gallery from (x1, z1) to (x2, z2), from the ground up to the layer under its
     * boards at {@code floor}, so that no dark hollow is left under them.
     */
    private static void plinth(Structure s, int x1, int z1, int x2, int z2, int floor) {
        s.fill(x1, 0, z1, x2, floor - 1, z2, Terem.FOOTING);
    }

    /**
     * The map table: the cartography table with stools of the timber at its sides and before it.
     */
    private static void mapTable(Structure s, int x, int y, int z) {
        s.set(x, y, z, CARTOGRAPHY);
        s.set(x - 1, y, z, Kit.stairs(Terem.FLOOR_STAIRS, "west")).set(x + 1, y, z, Kit.stairs(Terem.FLOOR_STAIRS, "east"));
        s.set(x, y, z + 1, Kit.stairs(Terem.FLOOR_STAIRS, "south"));
    }

    // ================================================================ the mages' tower

    private static final String LECTERN = "minecraft:lectern[facing=south,has_book=false,powered=false]";
    /** The four places two blocks diagonally out from the middle of a storey, where lanterns light its corners. */
    private static final int[][] CORNERS = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};

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
     * Rank B: one tower. A square storey of stone on a sloped plinth holds the focus; on it an eight-sided log storey with
     * the mage's room; over it the gallery for watching the stars jutting out round the research room, and the eight-sided
     * tent with an onion dome, amethyst on its eaves.
     */
    private static Drawn mageB() {
        Structure s = new Structure(11, 31, 11);
        int c = 5;
        int gallery = towerShell(s, c, c, 4, 1, 5, "", "east", "north");
        s.door(c, 1, 9, Terem.DOOR, "north", "left");
        focusFloor(s, c, c, 2);
        // the mage's room: the bed, the lectern, shelves
        Furnish.bed(s, c + 3, 6, c + 2, "north", "purple");
        Furnish.rug(s, c + 1, c + 1, c + 2, c + 2, 6, "minecraft:purple_carpet");
        Furnish.books(s, c - 2, c - 3, c - 1, c - 3, 6, 7);
        Furnish.books(s, c + 1, c - 3, c + 2, c - 3, 6, 7);
        s.set(c, 6, c - 2, LECTERN);
        ladder(s, c + 3, 6, c - 2, "west", gallery);
        hatch(s, c + 3, gallery, c - 2, "west");
        Furnish.chandelier(s, c, c, gallery - 1, 1);
        researchRoom(s, c, gallery + 1, c);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, 8}, List.of());
    }

    /**
     * Rank A: a taller tower of two log storeys, the room of its two mages over the focus and the library over them,
     * entered through a porch annex under a barrel roof with its carved kokoshnik to the street.
     */
    private static Drawn mageA() {
        Structure s = new Structure(13, 36, 15);
        int c = 6;
        int gallery = towerShell(s, c, c, 4, 2, 5, "south", "east", "north", "west");
        Terem.porch(s, c, 1, c + 4, "south", 0, -1, true);
        focusFloor(s, c, c, 2);
        // the room of the mages
        Furnish.bed(s, c + 3, 6, c + 2, "north", "purple");
        Furnish.bed(s, c + 1, 6, c + 3, "north", "purple");
        Furnish.rug(s, c, c, c + 2, c + 1, 6, "minecraft:purple_carpet");
        Furnish.books(s, c - 2, c - 3, c - 1, c - 3, 6, 7);
        Furnish.books(s, c + 1, c - 3, c + 2, c - 3, 6, 7);
        Furnish.crate(s, c - 2, 6, c + 2, "spruce");
        s.set(c - 2, 7, c + 2, Furnish.candles(2));
        Furnish.chandelier(s, c, c, 9, 1);
        // the library
        library(s, c, 11, c);
        ladder(s, c + 3, 6, c - 2, "west", gallery);
        hatch(s, c + 3, gallery, c - 2, "west");
        researchRoom(s, c, gallery + 1, c);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, c + 5}, List.of());
    }

    /**
     * Rank S: a tower of three log storeys (the room of two mages, the brewing room, the study), a library hall of logs
     * beside it under a barrel roof with a little onion dome, and a slender eight-sided side tower with the room of the third
     * mage under its own tent and dome, the three joined door to door by little closed passages; a porch with a tent before
     * the door of the tower.
     */
    private static Drawn mageS() {
        Structure s = new Structure(17, 41, 17);
        int c = 5;
        int gallery = towerShell(s, c, c, 4, 3, 5, "south", "west", "north");
        Terem.porch(s, c, 1, c + 4, "south", 0, -1, false);
        focusFloor(s, c, c, 1);
        // the room of two mages
        Furnish.bed(s, c + 3, 6, c + 2, "north", "purple");
        Furnish.bed(s, c + 1, 6, c + 3, "north", "purple");
        Furnish.rug(s, c, c, c + 2, c + 1, 6, "minecraft:purple_carpet");
        Furnish.books(s, c - 2, c - 3, c - 1, c - 3, 6, 7);
        Furnish.books(s, c + 1, c - 3, c + 2, c - 3, 6, 7);
        Furnish.chandelier(s, c, c, 9, 1);
        // the brewing room and the study
        brewingRoom(s, c, 11, c);
        study(s, c, 16, c);
        ladder(s, c + 3, 6, c - 2, "west", gallery);
        hatch(s, c + 3, gallery, c - 2, "west");
        researchRoom(s, c, gallery + 1, c);

        // the library hall beside the tower, its door from the tower and its door to the side tower
        wingHall(s, 11, 1, 15, 9, true);
        libraryInside(s, 11, 1, 15, 9, true, c);
        s.door(c + 4, 1, c, Terem.DOOR, "west", "left");
        s.set(c + 5, 0, c, Terem.FLOOR);
        s.door(11, 1, c, Terem.DOOR, "east", "left");
        s.set(13, 0, 10, Terem.FLOOR);
        s.door(13, 1, 9, Terem.DOOR, "north", "left");
        // the side tower with the room of the third mage
        sideTower(s, 13, 13, 2, 13, 5, "east", "east", "south");
        s.door(13, 1, 11, Terem.DOOR, "south", "left");
        joint(s, c + 5, 2, c + 5, 8, 5, c + 5, c);
        joint(s, 12, 10, 14, 10, 5, 13, 10);
        Furnish.bed(s, 14, 7, 14, "north", "purple");
        s.set(14, 1, 12, BREWING).set(14, 1, 14, CAULDRON);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, c + 5}, List.of());
    }

    /**
     * Rank SS: the tower court of the mages. The main tower of four log storeys rises in the middle (the room of two mages,
     * the study, the research room with the enchanting table, the hall of the mages, and the observatory under the tent) to
     * the gilt ball and glowing spike of its dome; low wings under barrel roofs with little domes stand against it on both
     * sides, the library hall to the west and the brewing room to the east, each joined to it by a little closed passage;
     * before them a court paved with stone and a walk of boards, with a porch under a tent at the door of the tower, and at
     * the front corners two slender lower towers with the rooms of two more mages, joined by a covered gallery on carved
     * posts through whose middle one enters the court.
     */
    private static Drawn mageSS() {
        Structure s = new Structure(25, 46, 25);
        int c = 12;
        int cz = 7;
        int gallery = towerShell(s, c, cz, 4, 4, 5, "south west east", "north");
        Terem.porch(s, c, 1, cz + 4, "south", 0, -1, false);
        focusFloor(s, c, cz, 2);
        // the room of two mages, the study, the research room, the hall of the mages, the observatory
        Furnish.bed(s, c + 3, 6, cz + 2, "north", "purple");
        Furnish.bed(s, c + 1, 6, cz + 3, "north", "purple");
        Furnish.rug(s, c, cz, c + 2, cz + 1, 6, "minecraft:purple_carpet");
        Furnish.books(s, c - 2, cz - 3, c - 1, cz - 3, 6, 7);
        Furnish.books(s, c + 1, cz - 3, c + 2, cz - 3, 6, 7);
        Furnish.chandelier(s, c, cz, 9, 1);
        study(s, c, 11, cz);
        researchRoom(s, c, 16, cz);
        Furnish.books(s, c - 3, cz - 2, c - 3, cz - 1, 16, 17);
        Furnish.books(s, c - 3, cz + 1, c - 3, cz + 2, 16, 17);
        Terem.feastTable(s, c - 1, cz, c + 1, cz, 21, false);
        for (int[] p : new int[][]{{-2, 0}, {2, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            if (Math.abs(p[0]) == 2) {
                Furnish.chair(s, c + p[0], 21, cz + p[1], p[0] < 0 ? "east" : "west", "oak");
            } else {
                Furnish.chair(s, c + p[0], 21, cz + p[1], p[1] < 0 ? "south" : "north", "oak");
            }
        }
        Terem.candleChandelier(s, c, cz, 24, 1);
        lamps(s, c, 24, cz, CORNERS);
        ladder(s, c + 3, 6, cz - 2, "west", gallery);
        hatch(s, c + 3, gallery, cz - 2, "west");
        observatory(s, c, gallery + 1, cz);

        // the wings: the library hall to the west, the brewing room to the east
        wingHall(s, 2, 2, 6, 12, false);
        libraryInside(s, 2, 2, 6, 12, false, cz - 2);
        s.door(c - 4, 1, cz - 2, Terem.DOOR, "east", "left");
        s.set(c - 5, 0, cz - 2, Terem.FLOOR);
        s.door(6, 1, cz - 2, Terem.DOOR, "west", "left");
        wingHall(s, 18, 2, 22, 12, true);
        brewingInside(s, 18, 2, 22, 12, true, cz);
        s.door(c + 4, 1, cz, Terem.DOOR, "west", "left");
        s.set(c + 5, 0, cz, Terem.FLOOR);
        s.door(18, 1, cz, Terem.DOOR, "east", "left");
        for (int x : new int[]{4, 20}) {
            s.door(x, 1, 12, Terem.DOOR, "north", "left");
        }
        joint(s, c - 5, 3, c - 5, 11, 5, c - 5, cz - 2);
        joint(s, c + 5, 3, c + 5, 11, 5, c + 5, cz);

        // the lower towers at the front corners and the gallery between them
        sideTower(s, 4, 20, 2, 15, 5, "south", "west", "south");
        sideTower(s, 20, 20, 2, 15, 5, "south", "east", "south");
        Furnish.bed(s, 5, 7, 21, "north", "purple");
        Furnish.bed(s, 19, 7, 21, "north", "purple");
        Terem.gallery(s, 7, 19, 17, 21, 1, 0, true);
        s.set(c, 1, 19, AIR).set(c, 1, 21, AIR);
        s.door(6, 1, 20, Terem.DOOR, "west", "left");
        s.door(18, 1, 20, Terem.DOOR, "east", "left");
        // the court: amethyst grown from the ground, lamps on posts
        for (int[] p : new int[][]{{8, 15}, {16, 15}, {9, 17}, {15, 17}}) {
            s.set(p[0], 0, p[1], AMETHYST).set(p[0], 1, p[1], CLUSTER);
        }
        for (int x : new int[]{8, 16}) {
            s.set(x, 0, 13, Terem.FENCE).set(x, 1, 13, Kit.lantern());
        }
        pave(s, 2, 13, 22, 18, c, 1, 15, 18);
        s.connect();
        s.trimSky(3);
        return new Drawn(s, new int[]{c, 1, cz + 5}, List.of());
    }

    /**
     * The observatory in the top room of a mage tower (an eight-sided storey of half-size 3) where one stands at layer
     * {@code y}: a great telescope on its stand in the middle looking up through the tent, smaller ones at the windows, a
     * table of star charts, an armillary of amethyst on a post, candles.
     */
    private static void observatory(Structure s, int cx, int y, int cz) {
        s.set(cx, y, cz, Terem.FENCE).set(cx, y + 1, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        telescope(s, cx - 2, y, cz, "west");
        telescope(s, cx + 2, y, cz, "east");
        s.set(cx, y, cz - 2, CARTOGRAPHY);
        s.set(cx - 1, y, cz + 2, Terem.FENCE).set(cx - 1, y + 1, cz + 2, AMETHYST);
        s.set(cx + 1, y, cz + 2, Kit.topSlab(Terem.FLOOR_SLAB)).set(cx + 1, y + 1, cz + 2, Furnish.candles(3));
        Furnish.chandelier(s, cx + 1, cz - 1, y + 3, 1);
    }

    /**
     * The ground floor of a mage tower of half-size 4 around (cx, cz), one stands at layer 1: the magic focus in its middle
     * on a rug, the post of the mage two blocks from it with the one chest beside it, brewing stands and a cauldron by the
     * east wall, shelves of books against the north wall leaving its window free, a stair climbing along the west wall to the
     * storey over it (the hole in that floor fenced), a chandelier of candles and lanterns in the four corners.
     */
    private static void focusFloor(Structure s, int cx, int cz, int brewing) {
        Furnish.rug(s, cx - 1, cz - 1, cx + 1, cz + 1, 1, "minecraft:purple_carpet");
        s.set(cx, 1, cz, FOCUS);
        s.set(cx + 2, 1, cz - 2, WORK_POST).set(cx + 3, 1, cz - 2, Kit.chest("west"));
        s.set(cx + 3, 1, cz + 2, BREWING).set(cx + 3, 1, cz + 3, CAULDRON);
        if (brewing > 1) {
            s.set(cx + 2, 1, cz + 3, BREWING);
        }
        Furnish.books(s, cx - 2, cz - 3, cx - 1, cz - 3, 1, 3);
        Furnish.books(s, cx + 1, cz - 3, cx + 2, cz - 3, 1, 3);
        flight(s, cx - 3, 1, cz + 3, "north", 5);
        s.set(cx - 2, 6, cz, Terem.CARVE_FENCE).set(cx - 2, 6, cz + 1, Terem.CARVE_FENCE).set(cx - 3, 6, cz + 2, Terem.CARVE_FENCE);
        Terem.candleChandelier(s, cx, cz, 4, 1);
        lamps(s, cx, 4, cz, CORNERS);
    }

    /**
     * The library on an eight-sided storey of half-size 4 where one stands at layer {@code y}: shelves of books round the
     * walls between the windows, the lectern before the shelves of the north, a reading table with candles and
     * benches, a chandelier.
     */
    private static void library(Structure s, int cx, int y, int cz) {
        for (int[] p : new int[][]{{-2, -3}, {-1, -3}, {1, -3}, {2, -3}, {-3, -2}, {-3, -1}, {-3, 1}, {-3, 2}, {-2, 3},
                {-1, 3}, {1, 3}, {2, 3}, {3, 1}, {3, 2}}) {
            Furnish.books(s, cx + p[0], cz + p[1], cx + p[0], cz + p[1], y, y + 1);
        }
        s.set(cx, y, cz - 2, LECTERN);
        Terem.feastTable(s, cx - 1, cz + 1, cx + 1, cz + 1, y, true);
        Terem.candleChandelier(s, cx, cz, y + 3, 1);
        lamps(s, cx, y + 3, cz, CORNERS);
    }

    /**
     * The brewing room on an eight-sided storey of half-size 4 where one stands at layer {@code y}: three brewing stands,
     * shelves of slabs with candles and a pot between them, two cauldrons, a crate and sacks of herbs, a table with candles,
     * a chandelier.
     */
    private static void brewingRoom(Structure s, int cx, int y, int cz) {
        s.set(cx - 2, y, cz - 3, BREWING).set(cx + 2, y, cz - 3, BREWING).set(cx - 3, y, cz - 2, BREWING);
        s.set(cx - 1, y, cz - 3, Kit.topSlab(Terem.FLOOR_SLAB)).set(cx - 1, y + 1, cz - 3, Furnish.candles(2));
        s.set(cx + 1, y, cz - 3, Kit.topSlab(Terem.FLOOR_SLAB)).set(cx + 1, y + 1, cz - 3, "minecraft:flower_pot");
        s.set(cx - 3, y, cz - 1, CAULDRON).set(cx - 3, y, cz + 1, CAULDRON);
        Furnish.crate(s, cx - 2, y, cz + 3, "oak");
        Furnish.sack(s, cx - 1, y, cz + 3, false);
        Furnish.sack(s, cx + 1, y, cz + 3, true);
        s.set(cx, y, cz + 1, Kit.topSlab(Terem.FLOOR_SLAB)).set(cx, y + 1, cz + 1, Furnish.candles(3));
        Terem.candleChandelier(s, cx, cz, y + 3, 1);
        lamps(s, cx, y + 3, cz, CORNERS);
    }

    /**
     * The study on an eight-sided storey of half-size 4: a desk of the mage with a chair, shelves of books, a table of star
     * charts, a globe of amethyst on a post, a chandelier.
     */
    private static void study(Structure s, int cx, int y, int cz) {
        Furnish.books(s, cx - 2, cz - 3, cx - 1, cz - 3, y, y + 1);
        Furnish.books(s, cx + 1, cz - 3, cx + 2, cz - 3, y, y + 1);
        Furnish.books(s, cx - 3, cz - 2, cx - 3, cz - 1, y, y + 1);
        Furnish.desk(s, cx - 2, y, cz + 2, "north", "oak");
        s.set(cx + 1, y, cz + 2, CARTOGRAPHY);
        s.set(cx - 2, y, cz, Terem.FENCE).set(cx - 2, y + 1, cz, AMETHYST);
        Terem.candleChandelier(s, cx, cz, y + 3, 1);
        lamps(s, cx, y + 3, cz, CORNERS);
    }

    /**
     * A low wing of a mage tower: a log frame from (x1, z1) to (x2, z2) on a footing of rough stone, one stands at layer 1,
     * under a barrel roof along z whose carved kokoshniki look north and south with a little onion dome on its ridge;
     * windows of purple glass in carved frames along its outer long wall, one in its north wall and one of magenta glass in
     * its southern kokoshnik; lanterns hanging from the vault.
     *
     * @param windowsEast whether its outer long wall (with the windows) is the east one
     */
    private static void wingHall(Structure s, int x1, int z1, int x2, int z2, boolean windowsEast) {
        int top = 6;
        s.walls(x1, 0, z1, x2, 0, z2, Terem.FOOTING);
        s.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, Terem.FLOOR);
        Terem.logWalls(s, x1, 1, z1, x2, top, z2);
        int cx = (x1 + x2) / 2;
        for (int z = z1 + 2; z <= z2 - 2; z += 4) {
            Terem.window(s, windowsEast ? x2 : x1, 2, z, windowsEast ? "east" : "west", 1, 2, true, PURPLE);
        }
        Terem.window(s, cx, 2, z1, "north", 1, 2, false, PURPLE);
        int ridge = Terem.barrelRoof(s, z1 - 1, z2 + 1, cx, top, x2 - x1 + 3, false, Terem.ROOF, null, true, true);
        Terem.window(s, cx, top + 2, z2, "south", 1, 2, false, MAGENTA);
        Terem.onionDome(s, cx, ridge, (z1 + z2) / 2, 3);
        lamp(s, cx, 4, z1 + 3);
        lamp(s, cx, 4, z2 - 3);
    }

    /**
     * The library in a wing three blocks wide inside: shelves of books along both long walls, low under the windows and open
     * at the door from the tower in row {@code doorZ}, and the lectern at the north end looking down the aisle.
     */
    private static void libraryInside(Structure s, int x1, int z1, int x2, int z2, boolean windowsEast, int doorZ) {
        int windowSide = windowsEast ? x2 - 1 : x1 + 1;
        int doorSide = windowsEast ? x1 + 1 : x2 - 1;
        for (int z = z1 + 1; z <= z2 - 1; z++) {
            if (z != doorZ) {
                Furnish.books(s, doorSide, z, doorSide, z, 1, 3);
            }
            Furnish.books(s, windowSide, z, windowSide, z, 1, (z - z1) % 4 == 2 ? 1 : 3);
        }
        int cx = (x1 + x2) / 2;
        s.set(cx, 1, z1 + 1, LECTERN);
        s.set(windowSide, 2, z1 + 2, Furnish.candles(3)).set(windowSide, 2, z1 + 6, Furnish.candles(3));
    }

    /**
     * The brewing room in a wing three blocks wide inside: brewing stands, cauldrons and a table with candles along the
     * windows, crates and sacks of herbs and plants in tubs along the inner wall, open at the door from the tower in row
     * {@code doorZ}.
     */
    private static void brewingInside(Structure s, int x1, int z1, int x2, int z2, boolean windowsEast, int doorZ) {
        int windowSide = windowsEast ? x2 - 1 : x1 + 1;
        int doorSide = windowsEast ? x1 + 1 : x2 - 1;
        for (int z = z1 + 1; z <= z2 - 1; z++) {
            int k = z - z1;
            String w = k % 4 == 2 ? BREWING : k % 4 == 0 ? CAULDRON : Kit.topSlab(Terem.FLOOR_SLAB);
            s.set(windowSide, 1, z, w);
            if (k % 4 == 1 || k % 4 == 3) {
                s.set(windowSide, 2, z, k % 4 == 1 ? Furnish.candles(2) : "minecraft:flower_pot");
            }
            if (z == doorZ || z == z2 - 1) {
                continue;
            }
            switch (k % 3) {
                case 0 -> Furnish.crate(s, doorSide, 1, z, "oak");
                case 1 -> Furnish.sack(s, doorSide, 1, z, k % 2 == 0);
                default -> Furnish.plant(s, doorSide, 1, z, "azalea");
            }
        }
    }

    /**
     * A slender side tower of the mages: an eight-sided log tower of half-size {@code r} on a plinth of rough stone around
     * (cx, cz), one stands at layer 1 and in the room over it at layer 7, its walls up to {@code top}; a little roof of
     * shingles rings it at the floor of the room, whose windows of magenta glass have carved frames; a ladder against the
     * wall opposite to {@code ladder}; an eight-sided tent with an onion dome over it.
     *
     * @param ladder  the way the ladder looks (it hangs on the wall on the other side)
     * @param windows the sides whose walls get windows
     */
    private static void sideTower(Structure s, int cx, int cz, int r, int top, int onion, String ladder, String... windows) {
        Terem.plate(s, cx, 0, cz, r + 1, Terem.OCTAGON, Terem.FOOTING);
        Terem.octWalls(s, cx, 1, top, cz, r);
        floorIn(s, cx, 0, cz, r, Terem.OCTAGON, Terem.FLOOR);
        floorIn(s, cx, 6, cz, r, Terem.OCTAGON, Terem.FLOOR);
        skirt(s, cx, 6, cz, r + 1, Terem.OCTAGON, r, Terem.OCTAGON);
        for (String side : windows) {
            int[] d = Furnish.step(side);
            Terem.window(s, cx + d[0] * r, 8, cz + d[1] * r, side, 1, 2, true, MAGENTA);
            s.fill(cx + d[0] * r, 2, cz + d[1] * r, cx + d[0] * r, 3, cz + d[1] * r, PURPLE);
        }
        int[] back = Furnish.step(Furnish.opposite(ladder));
        ladder(s, cx + back[0] * (r - 1), 1, cz + back[1] * (r - 1), ladder, 6);
        Terem.tent(s, Terem.EVERYWHERE, cx, cz, r + 1, top + 1, Terem.OCTAGON, Terem.OCTAGON, Terem.TENT_RISES, onion, -1);
        put(s, cx, 5, cz, Kit.hangingLantern());
        put(s, cx, top, cz, Kit.hangingLantern());
    }

    // ---------------------------------------------------------------- parts of the towers

    /**
     * The body of a mage tower around (cx, cz), from the street up to the point of its dome: a square storey of stone on a
     * sloped plinth (layers 0 to 4, its floor at 5), little roofs on the corners where the eight-sided log storeys stand on
     * it, {@code logs} log storeys of five layers with windows of magenta glass in carved frames on their four sides, the
     * gallery for watching the stars jutting out over brackets round the research room, an eight-sided storey stepped in
     * with windows of purple glass and its door to the gallery on the south, and the tent with its crown and onion dome.
     *
     * @param r          the half-size of the stone storey and of the log storeys (the research room is one less)
     * @param logs       how many log storeys stand on the stone one
     * @param onion      the width of the onion dome on the point of the tent
     * @param blocked    the sides on which the first log storey gets no window, because a porch or a wing stands there
     * @param stoneSides the sides of the stone storey that get a window
     * @return the layer of the floor of the gallery and of the research room
     */
    private static int towerShell(Structure s, int cx, int cz, int r, int logs, int onion, String blocked, String... stoneSides) {
        stoneStorey(s, cx, cz, r, 0, 4);
        skirt(s, cx, 5, cz, r, Terem.SQUARE, r, Terem.OCTAGON);
        for (String side : stoneSides) {
            int[] d = Furnish.step(side);
            Terem.window(s, cx + d[0] * r, 2, cz + d[1] * r, side, 1, 2, false, PURPLE);
        }
        int y = 5;
        for (int i = 0; i < logs; i++) {
            octStorey(s, cx, cz, r, y, y + 4);
            y += 5;
        }
        walkway(s, cx, y, cz, r, r + 1);
        octStorey(s, cx, cz, r - 1, y, y + 4);
        for (String side : new String[]{"south", "west", "east", "north"}) {
            int[] d = Furnish.step(side);
            for (int i = blocked.contains(side) ? 1 : 0; i < logs; i++) {
                Terem.window(s, cx + d[0] * r, 6 + 5 * i, cz + d[1] * r, side, 1, 2, true, MAGENTA);
            }
            if (!side.equals("south")) {
                s.fill(cx + d[0] * (r - 1), y + 2, cz + d[1] * (r - 1), cx + d[0] * (r - 1), y + 3, cz + d[1] * (r - 1), PURPLE);
            }
        }
        s.door(cx, y + 1, cz + r - 1, Terem.DOOR, "north", "left");
        crownTent(s, cx, y + 5, cz, r, onion);
        // end rods standing on the corners of the railing of the gallery, lanterns under the eaves, a telescope to the north
        for (int[] p : octCorners(r + 1)) {
            put(s, cx + p[0], y + 2, cz + p[1], "minecraft:end_rod[facing=up]");
        }
        for (int[] p : new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
            put(s, cx + p[0], y + 4, cz + p[1], Kit.hangingLantern());
        }
        telescope(s, cx, y + 1, cz - r, "north");
        return y;
    }

    /**
     * The research room in an eight-sided storey of half-size 3 whose floor one stands on at layer {@code y}: the enchanting
     * table in its middle, shelves of books on six sides around it leaving the windows free, a table of star charts and a
     * chandelier of candles.
     */
    private static void researchRoom(Structure s, int cx, int y, int cz) {
        s.set(cx, y, cz, ENCHANTING);
        for (int[] p : new int[][]{{-1, -2}, {1, -2}, {-2, -1}, {2, -1}, {-2, 1}, {2, 1}}) {
            Furnish.books(s, cx + p[0], cz + p[1], cx + p[0], cz + p[1], y, y + 1);
        }
        s.set(cx - 1, y, cz + 2, CARTOGRAPHY).set(cx + 1, y, cz + 2, Kit.topSlab(Terem.FLOOR_SLAB));
        s.set(cx + 1, y + 1, cz + 2, Furnish.candles(3));
        Terem.candleChandelier(s, cx, cz, y + 3, 1);
        lamps(s, cx, y + 3, cz, new int[]{2, 0}, new int[]{-2, 0}, new int[]{0, 2}, new int[]{0, -2});
    }

    /**
     * A square storey of stone around (cx, cz) of the half-size {@code r} from layer {@code y} (its plinth) up to
     * {@code top}: a sloped plinth of rough stone, walls of the stone of the place with quoins of rough stone at the corners,
     * a floor of planks inside.
     */
    private static void stoneStorey(Structure s, int cx, int cz, int r, int y, int top) {
        for (int dz = -r - 1; dz <= r + 1; dz++) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                int ax = Math.abs(dx);
                int az = Math.abs(dz);
                if (ax == r + 1 || az == r + 1) {
                    put(s, cx + dx, y, cz + dz, slope(Terem.FOOTING_STAIRS, dx, dz, r + 1, Terem.SQUARE, false));
                } else if (ax == r || az == r) {
                    put(s, cx + dx, y, cz + dz, Terem.FOOTING);
                    for (int yy = y + 1; yy <= top; yy++) {
                        put(s, cx + dx, yy, cz + dz, ax == r && az == r ? Kit.ROUGH : Kit.WALL);
                    }
                } else {
                    put(s, cx + dx, y, cz + dz, Terem.FLOOR);
                }
            }
        }
    }

    /**
     * A floor of the given block over the inside of a plan (its rim left to the walls).
     */
    private static void floorIn(Structure s, int cx, int y, int cz, int r, int plan, String state) {
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Terem.inPlan(dx, dz, r, plan) && !onRim(dx, dz, r, plan)) {
                    put(s, cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /**
     * An eight-sided log storey (vosmerik) of half-size {@code r} from layer {@code y1} to {@code y2}, its floor of planks
     * at {@code y1} inside the walls.
     */
    private static void octStorey(Structure s, int cx, int cz, int r, int y1, int y2) {
        Terem.octWalls(s, cx, y1, y2, cz, r);
        floorIn(s, cx, y1, cz, r, Terem.OCTAGON, Terem.FLOOR);
    }

    /**
     * Where a frame steps in, the ledge of the frame below it gets a little roof (politsa) of shingles at layer {@code y}:
     * every cell of the outer plan outside the inner one, a stair on the edge and, where the ledge is two blocks wide, a
     * second stair a layer higher on a block of the roof.
     */
    private static void skirt(Structure s, int cx, int y, int cz, int outer, int outerPlan, int inner, int innerPlan) {
        for (int dz = -outer; dz <= outer; dz++) {
            for (int dx = -outer; dx <= outer; dx++) {
                if (!Terem.inPlan(dx, dz, outer, outerPlan) || Terem.inPlan(dx, dz, inner, innerPlan)) {
                    continue;
                }
                if (onRim(dx, dz, outer, outerPlan)) {
                    put(s, cx + dx, y, cz + dz, slope(Terem.ROOF.stairs(), dx, dz, outer, outerPlan, false));
                } else {
                    put(s, cx + dx, y, cz + dz, Terem.ROOF.block());
                    put(s, cx + dx, y + 1, cz + dz, slope(Terem.ROOF.stairs(), dx, dz, outer - 1, outerPlan, false));
                }
            }
        }
    }

    /**
     * A gallery round a tower at layer {@code y}: a floor of planks over an eight-sided plan of half-size {@code floorR},
     * jutting out over brackets beyond the frame of half-size {@code wallR} under it, and a carved railing on its edge.
     */
    private static void walkway(Structure s, int cx, int y, int cz, int wallR, int floorR) {
        Terem.plate(s, cx, y, cz, floorR, Terem.OCTAGON, Terem.FLOOR);
        for (int dz = -floorR; dz <= floorR; dz++) {
            for (int dx = -floorR; dx <= floorR; dx++) {
                if (!Terem.inPlan(dx, dz, floorR, Terem.OCTAGON)) {
                    continue;
                }
                if (onRim(dx, dz, floorR, Terem.OCTAGON)) {
                    put(s, cx + dx, y + 1, cz + dz, Terem.CARVE_FENCE);
                }
                if (!Terem.inPlan(dx, dz, wallR, Terem.OCTAGON)) {
                    put(s, cx + dx, y - 1, cz + dz, slope(Terem.STAIRS, dx, dz, floorR, Terem.OCTAGON, true));
                }
            }
        }
    }

    /**
     * The crown of a mage tower: a steep eight-sided tent with its eaves of half-size {@code r} at layer {@code y}, each
     * ring rising three layers, its little windows glazed purple, an onion dome of the given width on its point, and
     * clusters of amethyst standing on the eight corners of its eaves.
     *
     * @return the layer above the top
     */
    private static int crownTent(Structure s, int cx, int y, int cz, int r, int onion) {
        int[] rises = Terem.TENT_RISES;
        int tip = Terem.tent(s, Terem.EVERYWHERE, cx, cz, r, y, Terem.OCTAGON, Terem.OCTAGON, rises, onion, -1);
        slukhi(s, cx, y + rises[0] + rises[1], cz, r - 1);
        for (int[] p : octCorners(r)) {
            put(s, cx + p[0], y + 1, cz + p[1], CLUSTER);
        }
        // the rod of the finial becomes a glowing spike
        if (s.inside(cx, tip - 1, cz) && s.get(cx, tip - 1, cz).startsWith("minecraft:lightning_rod")) {
            s.set(cx, tip - 1, cz, "minecraft:end_rod[facing=up]");
        }
        return tip;
    }

    /**
     * The eight corners of an eight-sided plan of half-size {@code r}: where its straight sides meet its cut ones.
     */
    private static int[][] octCorners(int r) {
        int d = 0;
        while (Terem.inPlan(r, d + 1, r, Terem.OCTAGON)) {
            d++;
        }
        return new int[][]{{r, d}, {r, -d}, {-r, d}, {-r, -d}, {d, r}, {-d, r}, {d, -r}, {-d, -r}};
    }

    /**
     * The little windows (slukhi) of a tent at layer {@code y}, {@code d} blocks out from its middle on its four faces,
     * glazed with purple glass.
     */
    private static void slukhi(Structure s, int cx, int y, int cz, int d) {
        for (int[] p : new int[][]{{d, 0}, {-d, 0}, {0, d}, {0, -d}}) {
            if (s.inside(cx + p[0], y, cz + p[1]) && s.get(cx + p[0], y, cz + p[1]).startsWith(Terem.MICA)) {
                s.set(cx + p[0], y, cz + p[1], PURPLE);
            }
        }
    }

    /**
     * The hatch where a ladder comes up through the floor of the gallery: a trapdoor of the timber lying shut in the floor,
     * so that the room under the gallery has no hole to the sky; turned the way the ladder is, it opens into a rung of it
     * for those who climb.
     */
    private static void hatch(Structure s, int x, int y, int z, String facing) {
        s.set(x, y, z, Kit.trapdoor("minecraft:oak_trapdoor", facing, true));
    }

    /**
     * Closes the narrow gap between a tower and a wing that stand side by side with their doors facing each other: lying
     * logs fill the gap from (x1, z1) to (x2, z2) from layer 1 to layer {@code top}, leaving a passage two layers high
     * at (px, pz) under a lintel, so that the doors are joined by a little closed passage instead of a slot open to the sky.
     */
    private static void joint(Structure s, int x1, int z1, int x2, int z2, int top, int px, int pz) {
        String axis = x1 == x2 ? "z" : "x";
        for (int y = 1; y <= top; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    boolean passage = x == px && z == pz && y <= 2;
                    if (!passage && !s.solid(x, y, z)) {
                        s.set(x, y, z, Kit.log(Terem.LOG, axis));
                    }
                }
            }
        }
    }

    /**
     * A telescope on its stand looking out toward {@code facing}: a post with the tube of a lightning rod lying on it.
     */
    private static void telescope(Structure s, int x, int y, int z, String facing) {
        s.set(x, y, z, Terem.FENCE);
        s.set(x, y + 1, z, "minecraft:lightning_rod[facing=" + facing + ",powered=false,waterlogged=false]");
    }

    /**
     * A flight of stairs climbing one layer for every block toward {@code dir} from (x, y, z), {@code n} steps, the space
     * above each step cleared for the head (the floor above is cut away where it must be) and a sawn stringer of stairs
     * hanging upside down under each step.
     */
    private static void flight(Structure s, int x, int y, int z, String dir, int n) {
        int[] d = Furnish.step(dir);
        for (int k = 0; k < n; k++) {
            int px = x + d[0] * k;
            int pz = z + d[1] * k;
            int py = y + k;
            s.set(px, py, pz, Kit.stairs(Terem.STAIRS, dir));
            s.set(px, py + 1, pz, AIR).set(px, py + 2, pz, AIR);
            if (k > 0) {
                s.set(px, py - 1, pz, Kit.upside(Terem.STAIRS, Furnish.opposite(dir)));
            }
        }
    }

    // ---------------------------------------------------------------- small shapes

    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.inside(x, y, z)) {
            s.set(x, y, z, state);
        }
    }

    private static boolean onRim(int dx, int dz, int r, int plan) {
        return Terem.inPlan(dx, dz, r, plan) && (!Terem.inPlan(dx + 1, dz, r, plan) || !Terem.inPlan(dx - 1, dz, r, plan)
                || !Terem.inPlan(dx, dz + 1, r, plan) || !Terem.inPlan(dx, dz - 1, r, plan));
    }

    /**
     * Stairs on the rim of a plan with their high side to its middle; where the rim turns a corner, a corner stair.
     */
    private static String slope(String block, int dx, int dz, int r, int plan, boolean upside) {
        boolean outX = dx != 0 && !Terem.inPlan(dx + Integer.signum(dx), dz, r, plan);
        boolean outZ = dz != 0 && !Terem.inPlan(dx, dz + Integer.signum(dz), r, plan);
        String half = upside ? "top" : "bottom";
        String facing;
        String shape = "straight";
        if (outX && outZ) {
            facing = dz > 0 ? "north" : "south";
            shape = dz > 0 == dx > 0 ? "outer_left" : "outer_right";
        } else if (outX || !outZ && Math.abs(dx) >= Math.abs(dz)) {
            facing = dx > 0 ? "west" : "east";
        } else {
            facing = dz > 0 ? "north" : "south";
        }
        return block + "[facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=false]";
    }
}
