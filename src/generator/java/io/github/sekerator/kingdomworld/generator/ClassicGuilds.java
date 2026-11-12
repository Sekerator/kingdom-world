package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The houses of the two guilds of a capital in the classic style, the adventurers' guild and the mages' guild, each drawn
 * by one program for the four ranks a guild may have: B in the capital of a weak kingdom up to SS in that of a strong one.
 * The higher the rank, the bigger, the higher and the richer the house.
 *
 * <p>The adventurers' guild grows from a modest guild house with its hall under a loft (B) to a hall with a gallery and a
 * tower (A), a broad guildhall between two round towers behind a walled forecourt (S) and a guild palace round a courtyard,
 * its great hall under a high roof (SS). Every rank has the same things inside: the hall with long tables and benches, the
 * hearth with its trophies, the contract board hung with notices, the counter with the guild desk and the post of the
 * registrar, and upstairs the map room and the rooms of the masters, who sleep in beds of state that are no homes.
 *
 * <p>The mages' guild lives in its tower: a slender tower (B), a taller one with a balcony (A), a tower with an annex (S)
 * and a main tower with turrets joined to it by bridges, a library wing and an observatory (SS). On the ground floor stands
 * the magic focus with the post of the mage beside it; above lie the library, the brewing room, the research room and the
 * rooms of the mages, who live there in beds of their own.
 *
 * <p>All of them stand on a ground of their own: layer 0 replaces the ground (paving, grass, a yard) and the street lies at
 * layer 1. They are drawn with the blocks that stand for the parts of a look (see {@link Kit}).
 */
final class ClassicGuilds {
    /** The rank of a guild, from B for a weak kingdom to SS for a strong one. */
    enum Rank { B, A, S, SS }

    /** A drawn building with the places named in it. */
    private record Drawing(Structure structure, int[] door, int[] guard) {
    }

    private static final String AIR = Structure.AIR;
    private static final String WORK_POST = "kingdomworld:work_post";
    private static final String DESK = "kingdomworld:guild_desk[facing=south]";
    private static final String FOCUS = "kingdomworld:magic_focus";
    private static final String ACCUMULATOR = "kingdomworld:crystal_accumulator";
    private static final String OAK_LOG = "minecraft:oak_log";
    private static final String SPRUCE_LOG = "minecraft:spruce_log";
    private static final String STRIPPED_SPRUCE = "minecraft:stripped_spruce_log";
    private static final String DARK_LOG = "minecraft:dark_oak_log";
    /** The dark boards of the contract board. */
    private static final String DARK_BOARD = "minecraft:stripped_dark_oak_log";
    private static final String PLANKS = "minecraft:spruce_planks";
    private static final String OAK_PLANKS = "minecraft:oak_planks";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String DOOR = "minecraft:spruce_door";
    private static final String BOOKSHELF = "minecraft:bookshelf";
    private static final String SKULL = "minecraft:skeleton_skull[rotation=0]";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String COARSE = "minecraft:coarse_dirt";
    private static final String GOLD = "minecraft:gold_block";
    private static final String CARTOGRAPHY = "minecraft:cartography_table";
    private static final String AMETHYST = "minecraft:amethyst_block";
    private static final String CLUSTER = "minecraft:amethyst_cluster[facing=up,waterlogged=false]";
    private static final String CALCITE = "minecraft:calcite";
    private static final String DEEPSLATE = "minecraft:polished_deepslate";
    private static final String TILES = "minecraft:deepslate_tiles";
    private static final String ENCHANTING = "minecraft:enchanting_table";
    private static final String BREWING = "minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]";
    private static final String CAULDRON = "minecraft:water_cauldron[level=3]";
    private static final String EMPTY_CAULDRON = "minecraft:cauldron";
    private static final String SEA_LANTERN = "minecraft:sea_lantern";
    private static final String SOUL_LANTERN = "minecraft:soul_lantern[hanging=false,waterlogged=false]";
    private static final String PURPLE_CARPET = "minecraft:purple_carpet";
    /** The tube of a telescope lying toward the window it looks out of. */
    private static final String TELESCOPE = "minecraft:lightning_rod[facing=north,powered=false,waterlogged=false]";
    /** A gilded finial on the point of a spire. */
    private static final String ROD = "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]";
    private static final String BELL = "minecraft:bell[attachment=ceiling,facing=north,powered=false]";
    private static final String TARGET = "minecraft:target[power=0]";
    private static final String PURPLE = "purple";
    private static final String MAGENTA = "magenta";

    private ClassicGuilds() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        for (Rank rank : Rank.values()) {
            Drawing d = guild(rank);
            Structure s = d.structure();
            list.add(new BuildingGenerator.Building("classic_guild_" + rank.name().toLowerCase(Locale.ROOT), "trade", "guild",
                    "kingdomworld:guild_desk", (s.sizeX - 1) / 2, 1, s.sizeZ - 1, s, "none", 1, "")
                    .at("door", d.door()[0], d.door()[1], d.door()[2])
                    .at("guard", d.guard()[0], d.guard()[1], d.guard()[2]));
        }
        for (Rank rank : Rank.values()) {
            Drawing d = mageTower(rank);
            Structure s = d.structure();
            list.add(new BuildingGenerator.Building("classic_mage_tower_" + rank.name().toLowerCase(Locale.ROOT), "production",
                    "mage_tower", "kingdomworld:magic_focus", (s.sizeX - 1) / 2, 1, s.sizeZ - 1, s, "mage_tower", 1, "")
                    .at("door", d.door()[0], d.door()[1], d.door()[2]));
        }
        return list;
    }

    // ---------------------------------------------------------------- states

    private static String gate(String facing) {
        return "minecraft:spruce_fence_gate[facing=" + facing + ",in_wall=false,open=false,powered=false]";
    }

    private static String lectern(String facing) {
        return "minecraft:lectern[facing=" + facing + ",has_book=false,powered=false]";
    }

    private static String campfire(String facing) {
        return "minecraft:campfire[facing=" + facing + ",lit=true,signal_fire=false,waterlogged=false]";
    }

    private static String grindstone(String facing) {
        return "minecraft:grindstone[face=floor,facing=" + facing + "]";
    }

    /** An iron trapdoor standing open against the block behind it: a shield hung on a wall. */
    private static String shield(String facing) {
        return Kit.shutter("minecraft:iron_trapdoor", facing);
    }

    /** The head of a slain monster mounted on the wall behind it. */
    private static String trophy(String kind, String facing) {
        return "minecraft:" + kind + "[facing=" + facing + "]";
    }

    private static String endRod(String facing) {
        return "minecraft:end_rod[facing=" + facing + "]";
    }

    private static String pane(String color) {
        return Kit.stainedPane(color);
    }

    private static String wool(String color) {
        return "minecraft:" + color + "_wool";
    }

    private static String carpet(String color) {
        return "minecraft:" + color + "_carpet";
    }

    private static String opposite(String facing) {
        return Furnish.opposite(facing);
    }

    // ---------------------------------------------------------------- straight shapes

    /**
     * A lantern hanging on a chain from a roof or a ceiling: the chain from {@code top} down, the lantern under it.
     */
    private static void chandelier(Structure s, int x, int z, int lanternY, int top) {
        if (top > lanternY) {
            s.fill(x, lanternY + 1, z, x, top, z, Kit.chain());
        }
        s.set(x, lanternY, z, Kit.hangingLantern());
    }

    /**
     * A gable roof whose ridge runs along z, its gables to the front and the back: rows of stairs rising from the west eave
     * {@code x1} and the east eave {@code x2}. The first {@code steep} rows above the eaves climb two layers each, with a
     * full block of the roof under the stair; the rows above climb one. An odd width is closed with a ridge of slabs.
     *
     * @return for every column from x1 to x2 the lowest layer the roof takes in it
     */
    private static int[] roofZ(Structure s, int x1, int x2, int z1, int z2, int y, int steep, Kit.Roof roof) {
        int[] under = new int[x2 - x1 + 1];
        int height = y;
        for (int row = 0; x1 + row <= x2 - row; row++) {
            boolean steepRow = row > 0 && row <= steep;
            if (row > 0) {
                height += steepRow ? 2 : 1;
            }
            int west = x1 + row;
            int east = x2 - row;
            if (steepRow) {
                s.fill(west, height - 1, z1, west, height - 1, z2, roof.block());
                s.fill(east, height - 1, z1, east, height - 1, z2, roof.block());
            }
            if (west < east) {
                s.fill(west, height, z1, west, height, z2, Kit.stairs(roof.stairs(), "east"));
                s.fill(east, height, z1, east, height, z2, Kit.stairs(roof.stairs(), "west"));
            } else {
                s.fill(west, height, z1, west, height, z2, Kit.slab(roof.slab()));
            }
            under[west - x1] = steepRow ? height - 1 : height;
            under[east - x1] = under[west - x1];
        }
        return under;
    }

    /**
     * A gable roof whose ridge runs along x, its eaves to the front and the back: rows of stairs rising from the back eave
     * {@code z1} and the front eave {@code z2}, steep rows as in {@link #roofZ}.
     *
     * @return for every row from z1 to z2 the lowest layer the roof takes in it
     */
    private static int[] roofX(Structure s, int x1, int x2, int z1, int z2, int y, int steep, Kit.Roof roof) {
        int[] under = new int[z2 - z1 + 1];
        int height = y;
        for (int row = 0; z1 + row <= z2 - row; row++) {
            boolean steepRow = row > 0 && row <= steep;
            if (row > 0) {
                height += steepRow ? 2 : 1;
            }
            int back = z1 + row;
            int front = z2 - row;
            if (steepRow) {
                s.fill(x1, height - 1, back, x2, height - 1, back, roof.block());
                s.fill(x1, height - 1, front, x2, height - 1, front, roof.block());
            }
            if (back < front) {
                s.fill(x1, height, back, x2, height, back, Kit.stairs(roof.stairs(), "south"));
                s.fill(x1, height, front, x2, height, front, Kit.stairs(roof.stairs(), "north"));
            } else {
                s.fill(x1, height, back, x2, height, back, Kit.slab(roof.slab()));
            }
            under[back - z1] = steepRow ? height - 1 : height;
            under[front - z1] = under[back - z1];
        }
        return under;
    }

    /**
     * A cross gable on the front slope of a roof whose ridge runs along x: a gable roof whose ridge runs along z from the
     * back row {@code z1} to the front eave {@code z2}, laid only where it rises over the main roof, so that it runs into the
     * main slope; under it the main roof is taken away, so that the room below opens into the gable. The main roof must be
     * drawn first; its slope is read in the column {@code refX} beside the gable.
     *
     * @return for every column from x1 to x2 the lowest layer the cross roof takes in it
     */
    private static int[] crossGable(Structure s, int x1, int x2, int z1, int z2, int y, int steep, Kit.Roof roof, int refX) {
        Structure c = new Structure(s.sizeX, s.sizeY, s.sizeZ);
        int[] under = roofZ(c, x1, x2, z1, z2, y, steep, roof);
        for (int z = z1; z <= z2; z++) {
            int main = s.top(refX, z);
            int low = main;
            while (low > y && States.id(s.get(refX, low - 1, z)).startsWith(States.id(roof.block()).replace("_planks", ""))) {
                low--;
            }
            for (int x = x1; x <= x2; x++) {
                int top = c.top(x, z);
                if (top < main) {
                    continue;
                }
                s.fill(x, low, z, x, main, z, AIR);
                for (int h = y; h <= top; h++) {
                    if (c.solid(x, h, z)) {
                        s.set(x, h, z, c.get(x, h, z));
                    }
                }
            }
        }
        return under;
    }

    /**
     * Closes the gable of a roof along z at the wall {@code z}: every column between the eaves from {@code y} up to the
     * underside of the roof, where nothing stands yet.
     */
    private static void gableAtZ(Structure s, int x1, int[] under, int z, int y, String state) {
        for (int i = 1; i < under.length - 1; i++) {
            for (int h = y; h < under[i]; h++) {
                if (!s.solid(x1 + i, h, z)) {
                    s.set(x1 + i, h, z, state);
                }
            }
        }
    }

    /**
     * Closes the gable of a roof along x at the wall {@code x}.
     */
    private static void gableAtX(Structure s, int z1, int[] under, int x, int y, String state) {
        for (int i = 1; i < under.length - 1; i++) {
            for (int h = y; h < under[i]; h++) {
                if (!s.solid(x, h, z1 + i)) {
                    s.set(x, h, z1 + i, state);
                }
            }
        }
    }

    /**
     * A tented roof over a square tower that climbs {@code rise} layers for every block inwards: each ring is a course of
     * stairs on {@code rise - 1} courses of the full block, filled inside so that no hollow is left under it, closed with a
     * slab on its point.
     *
     * @return the layer above the point
     */
    private static int needle(Structure s, int x1, int z1, int x2, int z2, int y, int rise, Kit.Roof roof, String core) {
        int layer = y;
        int ring = 0;
        while (x2 - ring - (x1 + ring) >= 2 && z2 - ring - (z1 + ring) >= 2) {
            int west = x1 + ring;
            int east = x2 - ring;
            int back = z1 + ring;
            int front = z2 - ring;
            if (ring > 0) {
                for (int k = 0; k < rise - 1; k++) {
                    s.walls(west, layer, back, east, layer, front, roof.block());
                    s.fill(west + 1, layer, back + 1, east - 1, layer, front - 1, core);
                    layer++;
                }
            }
            s.fill(west, layer, back, east, layer, back, Kit.stairs(roof.stairs(), "south"));
            s.fill(west, layer, front, east, layer, front, Kit.stairs(roof.stairs(), "north"));
            s.fill(west, layer, back + 1, west, layer, front - 1, Kit.stairs(roof.stairs(), "east"));
            s.fill(east, layer, back + 1, east, layer, front - 1, Kit.stairs(roof.stairs(), "west"));
            s.set(west, layer, back, Kit.stairs(roof.stairs(), "south", "outer_left"));
            s.set(east, layer, back, Kit.stairs(roof.stairs(), "south", "outer_right"));
            s.set(west, layer, front, Kit.stairs(roof.stairs(), "north", "outer_right"));
            s.set(east, layer, front, Kit.stairs(roof.stairs(), "north", "outer_left"));
            s.fill(west + 1, layer, back + 1, east - 1, layer, front - 1, core);
            layer++;
            ring++;
        }
        if (x1 + ring <= x2 - ring && z1 + ring <= z2 - ring) {
            for (int k = 0; k < rise - 1; k++) {
                s.fill(x1 + ring, layer, z1 + ring, x2 - ring, layer, z2 - ring, roof.block());
                layer++;
            }
            s.fill(x1 + ring, layer, z1 + ring, x2 - ring, layer, z2 - ring, Kit.slab(roof.slab()));
            layer++;
        }
        return layer;
    }

    /**
     * A dormer in a slope of a roof whose ridge runs along z, over a side wall at {@code xWall}: a window between two posts
     * of oak with cheeks of plaster, under a little gable roof that runs into the slope. {@code in} is the way from the wall
     * into the building (+1 for the west wall, -1 for the east wall), {@code y} the layer of the foot of the window; the
     * slope must climb one layer per block from the layer {@code y} over the wall.
     */
    private static void dormer(Structure s, int xWall, int in, int zc, int y, Kit.Roof roof) {
        s.fill(xWall + in, y, zc, xWall + in, y + 1, zc, AIR);
        s.fill(xWall + 2 * in, y, zc, xWall + 2 * in, y + 2, zc, AIR);
        for (int dz : new int[]{-1, 1}) {
            s.fill(xWall, y, zc + dz, xWall, y + 1, zc + dz, Kit.log(OAK_LOG, "y"));
            s.fill(xWall + in, y, zc + dz, xWall + in, y + 1, zc + dz, Kit.PLASTER);
        }
        s.fill(xWall, y, zc, xWall, y + 1, zc, Kit.GLASS);
        s.set(xWall, y + 2, zc, Kit.PLASTER);
        for (int k = -1; k <= 3; k++) {
            int x = xWall + k * in;
            if (k <= 1) {
                s.set(x, y + 1, zc - 2, Kit.stairs(roof.stairs(), "south")).set(x, y + 1, zc + 2, Kit.stairs(roof.stairs(), "north"));
            }
            if (k <= 2) {
                s.set(x, y + 2, zc - 1, Kit.stairs(roof.stairs(), "south")).set(x, y + 2, zc + 1, Kit.stairs(roof.stairs(), "north"));
            }
            s.set(x, y + 3, zc, Kit.slab(roof.slab()));
        }
    }

    /**
     * Quoins on a corner of a wall: the corner column of rough stone, and on every second layer one more block of it on
     * both faces, so that the corner looks bonded of long and short stones.
     *
     * @param dx the way along x from the corner into its wall (+1 or -1)
     * @param dz the way along z from the corner into its wall (+1 or -1)
     */
    private static void quoins(Structure s, int x, int z, int dx, int dz, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            s.set(x, y, z, Kit.ROUGH);
            if ((y - y1) % 2 == 0) {
                s.set(x + dx, y, z, Kit.ROUGH).set(x, y, z + dz, Kit.ROUGH);
            }
        }
    }

    /**
     * A storey of stone: the walls of a box of stone on a course of rough stone, quoins on its four corners.
     */
    private static void stoneStorey(Structure s, int x1, int z1, int x2, int z2, int y1, int y2) {
        s.walls(x1, y1, z1, x2, y2, z2, Kit.WALL);
        s.walls(x1, y1, z1, x2, y1, z2, Kit.ROUGH);
        quoins(s, x1, z1, 1, 1, y1, y2);
        quoins(s, x2, z1, -1, 1, y1, y2);
        quoins(s, x1, z2, 1, -1, y1, y2);
        quoins(s, x2, z2, -1, -1, y1, y2);
    }

    /**
     * A storey of timber frame: walls of plaster from {@code y1} to {@code y2 - 1} between upright posts of oak at the given
     * columns of the front and back walls and the given rows of the side walls and at the corners, and a plate of lying
     * logs all round at {@code y2}.
     */
    private static void timberStorey(Structure s, int x1, int z1, int x2, int z2, int y1, int y2, int[] postsX, int[] postsZ) {
        s.walls(x1, y1, z1, x2, y2 - 1, z2, Kit.PLASTER);
        for (int x : postsX) {
            s.fill(x, y1, z1, x, y2 - 1, z1, Kit.log(OAK_LOG, "y")).fill(x, y1, z2, x, y2 - 1, z2, Kit.log(OAK_LOG, "y"));
        }
        for (int z : postsZ) {
            s.fill(x1, y1, z, x1, y2 - 1, z, Kit.log(OAK_LOG, "y")).fill(x2, y1, z, x2, y2 - 1, z, Kit.log(OAK_LOG, "y"));
        }
        s.corners(x1, y1, z1, x2, y2 - 1, z2, OAK_LOG);
        s.beam(x1, z1, x2, z2, y2, OAK_LOG);
    }

    /**
     * A window in a wall of stone: panes from {@code y1} to {@code y2} in the cells from {@code (x1, z1)} to {@code (x2, z2)}
     * along the wall, a lintel of trim over them with a carved keystone in its middle, and a sill of trim slabs outside.
     *
     * @param out the way out of the wall
     */
    private static void stoneWindow(Structure s, int x1, int z1, int x2, int z2, int y1, int y2, String out, String glass) {
        int[] o = Furnish.step(out);
        s.fill(x1, y1, z1, x2, y2, z2, glass);
        s.fill(x1, y2 + 1, z1, x2, y2 + 1, z2, Kit.TRIM);
        s.fill(x1 + o[0], y1 - 1, z1 + o[1], x2 + o[0], y1 - 1, z2 + o[1], Kit.topSlab(Kit.TRIM_SLAB));
        if (x1 == x2 && z1 == z2 || Math.abs(x2 - x1) == 2 || Math.abs(z2 - z1) == 2) {
            s.set((x1 + x2) / 2, y2 + 1, (z1 + z2) / 2, Kit.CARVED);
        }
    }

    /**
     * Shutters standing open on both sides of a window that runs from {@code (x1, z1)} to {@code (x2, z2)} along a wall,
     * against the wall outside it.
     */
    private static void shutters(Structure s, int x1, int z1, int x2, int z2, int y1, int y2, String out) {
        int[] o = Furnish.step(out);
        int ax = x1 == x2 ? 0 : 1;
        int az = x1 == x2 ? 1 : 0;
        for (int y = y1; y <= y2; y++) {
            s.set(x1 - ax + o[0], y, z1 - az + o[1], Kit.shutter(Kit.SHUTTER, out));
            s.set(x2 + ax + o[0], y, z2 + az + o[1], Kit.shutter(Kit.SHUTTER, out));
        }
    }

    /**
     * An arched door in a wall of stone that runs along x: the door of spruce, jambs of rough stone, a fanlight of glass in
     * the arch over it between two haunches and a carved keystone over all.
     */
    private static void archedDoor(Structure s, int x, int y, int z) {
        s.door(x, y, z, DOOR, "north", "left");
        s.fill(x - 1, y, z, x - 1, y + 1, z, Kit.ROUGH).fill(x + 1, y, z, x + 1, y + 1, z, Kit.ROUGH);
        s.set(x, y + 2, z, Kit.GLASS);
        s.set(x - 1, y + 2, z, Kit.upside(Kit.WALL_STAIRS, "west")).set(x + 1, y + 2, z, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(x, y + 3, z, Kit.CARVED);
    }

    /**
     * A round window of stained glass five across, a ring of red and blue round a heart of yellow: in a wall along x at
     * {@code z} when {@code alongX}, otherwise in a wall along z at {@code x}.
     */
    private static void rose(Structure s, int x, int cy, int z, boolean alongX) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int d = -2; d <= 2; d++) {
                if (Kit.inDisc(d, dy, 5)) {
                    String color = d == 0 && dy == 0 ? "yellow" : Math.abs(d) + Math.abs(dy) == 1 ? "orange" : (d + dy) % 2 == 0 ? "red" : "blue";
                    s.set(alongX ? x + d : x, cy + dy, alongX ? z : z + d, pane(color));
                }
            }
        }
    }

    /**
     * A pointed window three wide in a wall along x or along z: panes from {@code y1} to {@code y2}, the middle column one
     * higher between two haunches, a carved keystone over the point.
     *
     * @param glass the panes of the side columns, of the middle column and of the point
     */
    private static void lancet(Structure s, int x, int z, int y1, int y2, boolean alongX, String[] glass) {
        for (int i = -1; i <= 1; i++) {
            s.fill(alongX ? x + i : x, y1, alongX ? z : z + i, alongX ? x + i : x, y2, alongX ? z : z + i, i == 0 ? glass[1] : glass[0]);
        }
        s.set(x, y2 + 1, z, glass[2]);
        if (alongX) {
            s.set(x - 1, y2 + 1, z, Kit.upside(Kit.WALL_STAIRS, "west")).set(x + 1, y2 + 1, z, Kit.upside(Kit.WALL_STAIRS, "east"));
        } else {
            s.set(x, y2 + 1, z - 1, Kit.upside(Kit.WALL_STAIRS, "north")).set(x, y2 + 1, z + 1, Kit.upside(Kit.WALL_STAIRS, "south"));
        }
        s.set(x, y2 + 2, z, Kit.CARVED);
    }

    /**
     * A lamp post: a post of stone wall on a block of trim with a lantern on top.
     */
    private static void lampPost(Structure s, int x, int y, int z, int height) {
        s.set(x, y, z, Kit.TRIM);
        s.fill(x, y + 1, z, x, y + height - 1, z, Kit.WALL_FENCE);
        s.set(x, y + height, z, Kit.lantern());
    }

    /**
     * A flag pole with a banner of the kingdom or of the guild on its top: a post of fence on a block of rough stone.
     */
    private static void flagPole(Structure s, int x, int y, int z, int height, String color, int rotation) {
        s.set(x, y, z, Kit.ROUGH);
        s.fill(x, y + 1, z, x, y + height - 1, z, Kit.fence("spruce"));
        s.set(x, y + height, z, Kit.banner(color, rotation));
    }

    // ---------------------------------------------------------------- round shapes

    /**
     * The way to the middle of a round plan from a cell of it, along the axis on which the cell lies farther out.
     */
    private static String inward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? "west" : "east";
        }
        return dz > 0 ? "north" : "south";
    }

    private static boolean interior(int dx, int dz, int diameter) {
        return Kit.inDisc(dx, dz, diameter) && !Kit.onRim(dx, dz, diameter);
    }

    /**
     * The cells of a ring between two round plans, one layer high: in the outer plan and not in the inner one.
     */
    private static void band(Structure s, int cx, int y, int cz, int outer, int inner, String state) {
        int r = (outer - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Kit.inDisc(dx, dz, outer) && (inner < 1 || !Kit.inDisc(dx, dz, inner)) && s.inside(cx + dx, y, cz + dz)) {
                    s.set(cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /**
     * The floor inside a round wall: every cell of the plan but its rim.
     */
    private static void roundFloor(Structure s, int cx, int y, int cz, int diameter, String state) {
        int r = (diameter - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (interior(dx, dz, diameter)) {
                    s.set(cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /**
     * A ring of upside-down stairs under a ledge, their hanging part against the wall: the brackets of a balcony or a cornice.
     */
    private static void corbels(Structure s, int cx, int y, int cz, int outer, int inner, String stairs) {
        int r = (outer - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Kit.inDisc(dx, dz, outer) && !Kit.inDisc(dx, dz, inner) && s.inside(cx + dx, y, cz + dz)) {
                    s.set(cx + dx, y, cz + dz, Kit.upside(stairs, inward(dx, dz)));
                }
            }
        }
    }

    /**
     * Stairs on the rim of a round plan with their high side to the middle: the sloping top of a plinth, the edge of a roof.
     * Where the rim turns a corner the stairs turn with it.
     */
    private static void rimStairs(Structure s, int cx, int y, int cz, int diameter, String stairs) {
        int r = (diameter - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Kit.onRim(dx, dz, diameter) && s.inside(cx + dx, y, cz + dz)) {
                    s.set(cx + dx, y, cz + dz, slopeStairs(stairs, dx, dz));
                }
            }
        }
    }

    private static String slopeStairs(String stairs, int dx, int dz) {
        if (Math.abs(dx) == Math.abs(dz) && dx != 0) {
            if (dz < 0) {
                return Kit.stairs(stairs, "south", dx < 0 ? "outer_left" : "outer_right");
            }
            return Kit.stairs(stairs, "north", dx < 0 ? "outer_right" : "outer_left");
        }
        return Kit.stairs(stairs, inward(dx, dz));
    }

    /**
     * A round pointed roof: rings narrowing by one block on every side, each ring rising the given number of layers, the
     * top layer of every ring edged with stairs turned to the middle, and a shell two blocks thick under them so that the
     * roof is closed. The rings that follow the last number of {@code rises} rise by it as well.
     *
     * @param width the width of the eaves
     * @return the layer above the point
     */
    private static int roundRoof(Structure s, int cx, int y, int cz, int width, int[] rises, Kit.Roof roof) {
        int layer = y;
        int ring = 0;
        for (int w = width; w >= 1; w -= 2, ring++) {
            int rise = rises[Math.min(ring, rises.length - 1)];
            int r = (w - 1) / 2;
            for (int step = 0; step < rise; step++) {
                boolean top = step == rise - 1;
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (!Kit.inDisc(dx, dz, w) || w >= 5 && Kit.inDisc(dx, dz, w - 4) || !s.inside(cx + dx, layer, cz + dz)) {
                            continue;
                        }
                        boolean edge = w >= 3 && Kit.onRim(dx, dz, w);
                        s.set(cx + dx, layer, cz + dz, top && edge ? slopeStairs(roof.stairs(), dx, dz) : roof.block());
                    }
                }
                layer++;
            }
        }
        return layer;
    }

    /**
     * The cells along the inside of a round wall, in order clockwise from the north as seen from above, each beside the one
     * before it: the way a stair winds up a tower.
     */
    private static List<int[]> innerRing(int diameter) {
        int r = (diameter - 1) / 2;
        List<int[]> cells = new ArrayList<>();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (!interior(dx, dz, diameter)) {
                    continue;
                }
                boolean byWall = false;
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        byWall |= !interior(dx + ox, dz + oz, diameter);
                    }
                }
                if (byWall) {
                    cells.add(new int[]{dx, dz});
                }
            }
        }
        cells.sort((a, b) -> Double.compare(angle(a), angle(b)));
        for (int i = 0; i < cells.size(); i++) {
            int[] a = cells.get(i);
            int[] b = cells.get((i + 1) % cells.size());
            if (Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) != 1) {
                throw new IllegalStateException("The inner ring of " + diameter + " is not a walk at " + a[0] + "," + a[1]);
            }
        }
        return cells;
    }

    private static double angle(int[] cell) {
        double a = Math.atan2(cell[0], -cell[1]);
        return a < 0 ? a + 2 * Math.PI : a;
    }

    private static int indexOf(List<int[]> ring, int dx, int dz) {
        for (int i = 0; i < ring.size(); i++) {
            if (ring.get(i)[0] == dx && ring.get(i)[1] == dz) {
                return i;
            }
        }
        throw new IllegalArgumentException("Not on the ring: " + dx + "," + dz);
    }

    private static String toward(int[] from, int[] to) {
        if (to[0] > from[0]) {
            return "east";
        }
        if (to[0] < from[0]) {
            return "west";
        }
        return to[1] > from[1] ? "south" : "north";
    }

    /**
     * One flight of a winding stair, from the floor at layer {@code from} to the floor at layer {@code to}: one step to a
     * cell of the inner ring from {@code start} on, the last step lying in the upper floor, an upside-down stair under every
     * step but the first, and the upper floor opened over the steps where a head would knock against it.
     *
     * @return the cells of the opening in the upper floor
     */
    private static List<int[]> flight(Structure s, int cx, int cz, List<int[]> ring, int start, int from, int to, String stairs) {
        int n = to - from;
        List<int[]> holes = new ArrayList<>();
        for (int k = 1; k <= n; k++) {
            int[] cell = ring.get((start + k - 1) % ring.size());
            int[] next = ring.get((start + k) % ring.size());
            String facing = toward(cell, next);
            int x = cx + cell[0];
            int z = cz + cell[1];
            s.set(x, from + k, z, Kit.stairs(stairs, facing));
            if (k >= 2) {
                s.set(x, from + k - 1, z, Kit.upside(stairs, opposite(facing)));
            }
            s.fill(x, from + k + 1, z, x, from + k + 2, z, AIR);
            if (k >= n - 2 && k < n) {
                holes.add(new int[]{x, to, z});
            }
        }
        return holes;
    }

    /**
     * A rail of fences round the openings of a stair in the floors, on the floor cells beside them inside a round wall of the
     * given width, but not where a stair comes up beside a cell, so that the way off the stair stays open. It is laid when
     * all the flights stand.
     */
    private static void rails(Structure s, int cx, int cz, int diameter, List<int[]> holes) {
        for (int[] hole : holes) {
            int y = hole[1];
            for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = hole[0] + side[0];
                int z = hole[2] + side[1];
                if (interior(x - cx, z - cz, diameter) && s.get(x, y + 1, z).equals(AIR) && States.isFull(s.get(x, y, z))
                        && !besideStairs(s, x, y, z)) {
                    s.set(x, y + 1, z, Kit.fence("spruce"));
                }
            }
        }
    }

    /**
     * Whether stairs stand beside a cell, level with its floor or one above it.
     */
    private static boolean besideStairs(Structure s, int x, int y, int z) {
        for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int h = y; h <= y + 1; h++) {
                if (s.inside(x + side[0], h, z + side[1]) && States.id(s.get(x + side[0], h, z + side[1])).endsWith("_stairs")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A window in a flat side of a round wall: panes in the rim cell {@code (x, z)} from layer {@code y1} to {@code y2} and a
     * keystone of carved stone over them; a wide window has panes on either side as well and a pointed head of upside-down
     * stairs. Brackets of trim under it outside form the sill, when {@code sill} is set.
     *
     * @param outX the way out of the wall at the window along x (-1, 0 or 1)
     * @param outZ the way out of the wall along z
     */
    private static void roundWindow(Structure s, int x, int z, int outX, int outZ, int y1, int y2, boolean wide, boolean sill, String pane) {
        int ax = outZ != 0 ? 1 : 0;
        int az = outX != 0 ? 1 : 0;
        int reach = wide ? 1 : 0;
        for (int o = -reach; o <= reach; o++) {
            s.fill(x + o * ax, y1, z + o * az, x + o * ax, y2, z + o * az, pane);
            if (sill) {
                String toWall = toward(new int[]{outX, outZ}, new int[]{0, 0});
                s.set(x + o * ax + outX, y1 - 1, z + o * az + outZ, Kit.upside(Kit.TRIM_STAIRS, toWall));
            }
        }
        if (wide) {
            s.set(x, y2 + 1, z, pane);
            s.set(x - ax, y2 + 1, z - az, Kit.upside(Kit.TRIM_STAIRS, ax == 1 ? "west" : "north"));
            s.set(x + ax, y2 + 1, z + az, Kit.upside(Kit.TRIM_STAIRS, ax == 1 ? "east" : "south"));
            s.set(x, y2 + 2, z, Kit.CARVED);
        } else {
            s.set(x, y2 + 1, z, Kit.CARVED);
        }
    }

    /**
     * The hollow of a round spire over a room: the layer under the first block of the spire above its middle.
     */
    private static int hollowTop(Structure s, int cx, int y, int cz) {
        int h = y;
        while (s.inside(cx, h + 1, cz) && !s.solid(cx, h + 1, cz)) {
            h++;
        }
        return h;
    }

    // ---------------------------------------------------------------- the furniture of a guild

    /**
     * A hearth in a back wall that runs along x at {@code zw}, opening south into a hall whose people stand at layer
     * {@code f}: fires in a niche of rough stone two layers high, cheeks and a hearthstone before it, a mantel of trim with
     * the skulls of beasts and a lantern on it and, when {@code tall}, a shield and two banners over it. The breast of the
     * chimney behind the wall is drawn by the building.
     *
     * @param half how far the cheeks stand from the middle
     */
    private static void hearth(Structure s, int cx, int f, int zw, int half, boolean tall) {
        s.fill(cx - half, f, zw, cx + half, f + 3, zw, Kit.ROUGH);
        s.fill(cx - half + 1, f, zw, cx + half - 1, f, zw, campfire("south"));
        s.fill(cx - half + 1, f + 1, zw, cx + half - 1, f + 1, zw, AIR);
        s.fill(cx - half, f - 1, zw + 1, cx + half, f - 1, zw + 1, Kit.TRIM);
        s.fill(cx - half, f, zw + 1, cx - half, f + 1, zw + 1, Kit.ROUGH);
        s.fill(cx + half, f, zw + 1, cx + half, f + 1, zw + 1, Kit.ROUGH);
        s.fill(cx - half, f + 2, zw + 1, cx + half, f + 2, zw + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(cx - 1, f + 3, zw + 1, SKULL).set(cx + 1, f + 3, zw + 1, SKULL).set(cx, f + 3, zw + 1, Kit.lantern());
        if (tall) {
            s.set(cx, f + 4, zw + 1, shield("south"));
            s.set(cx - half, f + 4, zw + 1, Kit.wallBanner("red", "south")).set(cx + half, f + 4, zw + 1, Kit.wallBanner("red", "south"));
        }
    }

    /**
     * The contract board of the guild on a wall: a frame of stripped logs from {@code (x1, z1)} to {@code (x2, z2)} along the
     * wall, from the layer {@code f} up to {@code top}, filled with boards of dark wood, the notices pinned on it as white and
     * grey banners, and before it a ledge with papers and candles on it.
     *
     * @param out the way from the board into the room
     */
    private static void contractBoard(Structure s, int x1, int z1, int x2, int z2, int f, int top, String out) {
        boolean alongX = z1 == z2;
        String axis = alongX ? "x" : "z";
        int ax = alongX ? 1 : 0;
        int az = alongX ? 0 : 1;
        int[] o = Furnish.step(out);
        s.fill(x1, f, z1, x1, top, z1, Kit.log(STRIPPED_SPRUCE, "y"));
        s.fill(x2, f, z2, x2, top, z2, Kit.log(STRIPPED_SPRUCE, "y"));
        s.fill(x1 + ax, f, z1 + az, x2 - ax, f, z2 - az, Kit.log(STRIPPED_SPRUCE, axis));
        s.fill(x1 + ax, top, z1 + az, x2 - ax, top, z2 - az, Kit.log(STRIPPED_SPRUCE, axis));
        s.fill(x1 + ax, f + 1, z1 + az, x2 - ax, top - 1, z2 - az, Kit.log(DARK_BOARD, axis));
        String[] notices = {"white", "light_gray", "white", "yellow", "white", "light_gray"};
        int i = 0;
        for (int x = x1 + ax, z = z1 + az; x <= x2 - ax && z <= z2 - az; x += ax, z += az, i++) {
            int px = x + o[0];
            int pz = z + o[1];
            s.set(px, f, pz, Kit.topSlab(SPRUCE_SLAB));
            s.set(px, f + 1, pz, i % 2 == 1 ? Furnish.candles(1 + i % 3) : "minecraft:white_carpet");
            s.set(px, top - 1, pz, Kit.wallBanner(notices[i % notices.length], out));
            if (top - f >= 5 && i % 2 == 0) {
                s.set(px, top - 3, pz, Kit.wallBanner(notices[(i + 1) % notices.length], out));
            }
        }
        // Candles on the posts of the frame.
        if (s.inside(x1, top + 1, z1) && !s.solid(x1, top + 1, z1)) {
            s.set(x1, top + 1, z1, Furnish.candles(2));
        }
        if (s.inside(x2, top + 1, z2) && !s.solid(x2, top + 1, z2)) {
            s.set(x2, top + 1, z2, Furnish.candles(3));
        }
    }

    /**
     * A long table of upper slabs from one end to the other along x or z, a bench on either side, candles, a lantern and a
     * cake on it.
     */
    private static void feastTable(Structure s, int x1, int z1, int x2, int z2, int f) {
        boolean alongX = z1 == z2;
        Furnish.longTable(s, x1, z1, x2, z2, f, "spruce");
        if (alongX) {
            Furnish.bench(s, x1, z1 - 1, x2, z1 - 1, f, "south", "spruce");
            Furnish.bench(s, x1, z1 + 1, x2, z1 + 1, f, "north", "spruce");
        } else {
            Furnish.bench(s, x1 - 1, z1, x1 - 1, z2, f, "east", "spruce");
            Furnish.bench(s, x1 + 1, z1, x1 + 1, z2, f, "west", "spruce");
        }
        int n = alongX ? x2 - x1 : z2 - z1;
        for (int i = 0; i <= n; i++) {
            int x = alongX ? x1 + i : x1;
            int z = alongX ? z1 : z1 + i;
            if (i == n / 2) {
                s.set(x, f + 1, z, Kit.lantern());
            } else if (i == n / 2 + 2) {
                s.set(x, f + 1, z, "minecraft:cake[bites=0]");
            } else if (i % 2 == 0) {
                s.set(x, f + 1, z, Furnish.candles(i % 4 == 0 ? 3 : 2));
            }
        }
    }

    /**
     * A straight flight of stairs from a floor whose people stand at layer {@code f} up to the floor at layer
     * {@code f + steps - 1}: one step a cell from {@code (x, z)} along {@code (dx, dz)}, each resting on {@code under}, the
     * last one level with the upper floor, and the upper floor opened over the steps where a head would knock against it.
     */
    private static void straightStair(Structure s, int x, int z, int dx, int dz, int f, int steps, String stairs, String under) {
        String facing = dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
        for (int k = 0; k < steps; k++) {
            int cx = x + k * dx;
            int cz = z + k * dz;
            if (k > 0) {
                s.fill(cx, f, cz, cx, f + k - 1, cz, under);
            }
            s.set(cx, f + k, cz, Kit.stairs(stairs, facing));
            s.fill(cx, f + k + 1, cz, cx, f + k + 2, cz, AIR);
        }
    }

    /**
     * A table of maps: upper slabs from one end to the other with the lands, the seas and the forests of the realm laid on
     * them in carpets of green, blue and brown.
     */
    private static void mapTable(Structure s, int x1, int z1, int x2, int z2, int f) {
        s.fill(x1, f, z1, x2, f, z2, Kit.topSlab(SPRUCE_SLAB));
        String[] lands = {"lime", "light_blue", "green", "brown", "lime", "blue", "green"};
        int i = 0;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                s.set(x, f + 1, z, carpet(lands[i++ % lands.length]));
            }
        }
    }

    /**
     * A map of the lands painted on a wall along x at {@code z}: rows of terracotta from {@code x1} on, the top row at
     * {@code top}, in a frame of stripped logs.
     */
    private static void paintedMap(Structure s, int x1, int top, int z, String[] rows) {
        for (int row = 0; row < rows.length; row++) {
            for (int i = 0; i < rows[row].length(); i++) {
                String color = switch (rows[row].charAt(i)) {
                    case 'l' -> "lime";
                    case 'g' -> "green";
                    case 'y' -> "yellow";
                    case 'b' -> "light_blue";
                    case 'c' -> "cyan";
                    case 'w' -> "white";
                    default -> "brown";
                };
                s.set(x1 + i, top - row, z, "minecraft:" + color + "_terracotta");
            }
        }
        int x2 = x1 + rows[0].length() - 1;
        int bottom = top - rows.length + 1;
        s.fill(x1 - 1, bottom - 1, z, x1 - 1, top + 1, z, Kit.log(STRIPPED_SPRUCE, "y"));
        s.fill(x2 + 1, bottom - 1, z, x2 + 1, top + 1, z, Kit.log(STRIPPED_SPRUCE, "y"));
        s.fill(x1, top + 1, z, x2, top + 1, z, Kit.log(STRIPPED_SPRUCE, "x"));
        s.fill(x1, bottom - 1, z, x2, bottom - 1, z, Kit.log(STRIPPED_SPRUCE, "x"));
    }

    /**
     * The banner of the adventurers' guild, black with a golden sword, three blocks wide and nine high, woven of wool on the
     * front of a wall along x at {@code z}, its top row at {@code top}.
     */
    private static void swordBanner(Structure s, int x1, int top, int z) {
        String[] flag = {"BBB", "BYB", "YYY", "BWB", "BWB", "BWB", "BWB", "BBB", "B.B"};
        for (int row = 0; row < flag.length; row++) {
            for (int i = 0; i < 3; i++) {
                char c = flag[row].charAt(i);
                if (c != '.') {
                    s.set(x1 + i, top - row, z, wool(c == 'B' ? "black" : c == 'Y' ? "yellow" : "light_gray"));
                }
            }
        }
    }

    /**
     * A training dummy: a post with a body of hay and arms of fence and a carved pumpkin for a head looking the given way.
     */
    private static void dummy(Structure s, int x, int y, int z, String facing) {
        s.set(x, y, z, Kit.fence("spruce")).set(x, y + 1, z, HAY).set(x, y + 2, z, "minecraft:carved_pumpkin[facing=" + facing + "]");
        boolean alongX = facing.equals("north") || facing.equals("south");
        s.set(x + (alongX ? 1 : 0), y + 1, z + (alongX ? 0 : 1), Kit.fence("spruce"));
        s.set(x - (alongX ? 1 : 0), y + 1, z - (alongX ? 0 : 1), Kit.fence("spruce"));
    }

    // ---------------------------------------------------------------- the adventurers' guild

    /**
     * The house of the adventurers' guild of the given rank.
     */
    static Drawing guild(Rank rank) {
        return switch (rank) {
            case B -> guildB();
            case A -> guildA();
            case S -> guildS();
            case SS -> guildSS();
        };
    }

    /**
     * The guild house of rank B, 13 by 15: a hall of stone with quoins of rough stone under a loft of timber frame that juts
     * out over the street on brackets of trim, its gable of plaster and oak to the front under a roof of tiles with a dormer
     * in either slope, a ridge turret with a bell under a slate spire and a chimney on the back gable. The arched door has a
     * fanlight and a carved keystone; benches stand under the jetty and lamps at the corners.
     *
     * <p>Inside, the hall: the hearth on the back wall with skulls on its mantel, the counter of the registrar with the guild
     * desk and the post behind it, the contract board on the west wall, one long table with benches, weapon racks, shields and
     * the heads of beasts on the east wall and a stair with bookshelves under it up to the loft. In the loft lie the map room
     * at the back and the room of the guild master, with his bed of state, over the street.
     */
    private static Drawing guildB() {
        Structure s = new Structure(13, 26, 15);
        // The ground: paving, a path of trim to the street, a woodpile and hay in the back yard.
        s.fill(0, 0, 0, 12, 0, 14, Kit.ROUGH);
        s.fill(5, 0, 13, 7, 0, 14, Kit.TRIM);
        // The stone storey and the jetty.
        s.fill(2, 1, 3, 10, 1, 11, PLANKS);
        stoneStorey(s, 1, 2, 11, 12, 1, 5);
        s.walls(1, 6, 2, 11, 6, 13, Kit.TRIM);
        s.fill(2, 6, 3, 10, 6, 12, PLANKS);
        s.fill(1, 6, 13, 11, 6, 13, Kit.log(OAK_LOG, "x"));
        for (int x = 1; x <= 11; x++) {
            s.set(x, 5, 13, Kit.upside(Kit.TRIM_STAIRS, "north"));
        }
        // The timber storey: a plate under the eaves at layer 9.
        timberStorey(s, 1, 2, 11, 13, 7, 9, new int[]{4, 6, 8}, new int[]{5, 8, 10});
        // Windows of the stone storey with sills and lintels; the door.
        stoneWindow(s, 3, 12, 4, 12, 3, 4, "south", Kit.GLASS);
        stoneWindow(s, 8, 12, 9, 12, 3, 4, "south", Kit.GLASS);
        stoneWindow(s, 1, 4, 1, 4, 3, 4, "west", Kit.GLASS);
        stoneWindow(s, 1, 6, 1, 6, 3, 4, "west", Kit.GLASS);
        stoneWindow(s, 11, 6, 11, 6, 3, 4, "east", Kit.GLASS);
        stoneWindow(s, 3, 2, 3, 2, 3, 4, "north", Kit.GLASS);
        stoneWindow(s, 9, 2, 9, 2, 3, 4, "north", Kit.GLASS);
        archedDoor(s, 6, 2, 12);
        s.fill(5, 1, 13, 7, 1, 13, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        // Windows of the timber storey with shutters.
        for (int x : new int[]{2, 3, 5, 7, 9, 10}) {
            s.fill(x, 7, 13, x, 8, 13, Kit.GLASS);
        }
        shutters(s, 2, 13, 3, 13, 7, 8, "south");
        shutters(s, 9, 13, 10, 13, 7, 8, "south");
        for (int z : new int[]{3, 4, 9}) {
            s.fill(1, 7, z, 1, 8, z, Kit.GLASS).fill(11, 7, z, 11, 8, z, Kit.GLASS);
        }
        shutters(s, 1, 3, 1, 4, 7, 8, "west");
        shutters(s, 11, 3, 11, 4, 7, 8, "east");
        s.fill(2, 7, 2, 3, 8, 2, Kit.GLASS).fill(9, 7, 2, 10, 8, 2, Kit.GLASS);
        // The roof of tiles and its gables.
        int[] under = roofZ(s, 0, 12, 1, 14, 9, 0, Kit.TILE);
        gableAtZ(s, 0, under, 13, 10, Kit.PLASTER);
        gableAtZ(s, 0, under, 2, 10, Kit.PLASTER);
        for (int z : new int[]{2, 13}) {
            s.fill(6, 10, z, 6, under[6] - 1, z, Kit.log(OAK_LOG, "y"));
            s.fill(3, 10, z, 3, 11, z, Kit.log(OAK_LOG, "y")).fill(9, 10, z, 9, 11, z, Kit.log(OAK_LOG, "y"));
            s.fill(4, 12, z, 5, 12, z, Kit.log(OAK_LOG, "x")).fill(7, 12, z, 8, 12, z, Kit.log(OAK_LOG, "x"));
        }
        s.fill(4, 10, 13, 5, 11, 13, Kit.GLASS).fill(7, 10, 13, 8, 11, 13, Kit.GLASS);
        s.set(5, 13, 13, Kit.GLASS).set(7, 13, 13, Kit.GLASS);
        s.set(4, 11, 2, Kit.GLASS).set(8, 11, 2, Kit.GLASS);
        s.set(6, 13, 14, Kit.wallBanner("black", "south"));
        dormer(s, 1, 1, 5, 10, Kit.TILE);
        dormer(s, 11, -1, 10, 10, Kit.TILE);
        // The chimney: the breast of rough stone on the back gable, the stack of brick over the ridge.
        s.fill(4, 1, 1, 8, 8, 1, Kit.ROUGH);
        s.fill(5, 1, 0, 7, 5, 0, Kit.ROUGH);
        s.fill(5, 6, 0, 7, 6, 0, Kit.stairs(Kit.ROUGH_STAIRS, "south"));
        for (int x = 4; x <= 8; x++) {
            s.fill(x, 9, 1, x, under[x] - 1, 1, Kit.ROUGH);
        }
        s.fill(5, 13, 1, 7, 15, 1, Kit.ROUGH);
        s.fill(5, 16, 1, 7, 18, 1, Kit.CHIMNEY);
        s.set(6, 19, 1, Kit.CHIMNEY_POT).set(5, 19, 1, Kit.slab(Kit.CHIMNEY_SLAB)).set(7, 19, 1, Kit.slab(Kit.CHIMNEY_SLAB));
        // The ridge turret: a base of trim, posts and rails round a bell, a spire of slate with a gilded finial.
        s.fill(5, 15, 9, 7, 15, 11, Kit.TRIM);
        for (int[] c : new int[][]{{5, 9}, {7, 9}, {5, 11}, {7, 11}}) {
            s.fill(c[0], 16, c[1], c[0], 17, c[1], Kit.log(OAK_LOG, "y"));
        }
        s.set(6, 16, 9, Kit.fence("oak")).set(6, 16, 11, Kit.fence("oak")).set(5, 16, 10, Kit.fence("oak")).set(7, 16, 10, Kit.fence("oak"));
        int top = needle(s, 4, 8, 8, 12, 18, 2, Kit.SLATE, PLANKS);
        s.set(6, top, 10, ROD);
        s.set(6, 17, 10, BELL);
        // Benches under the jetty, lamps at the corners, the woodpile and hay in the yard.
        Furnish.bench(s, 2, 13, 3, 13, 1, "south", "spruce");
        Furnish.bench(s, 9, 13, 10, 13, 1, "south", "spruce");
        s.set(4, 4, 13, Kit.hangingLantern()).set(8, 4, 13, Kit.hangingLantern());
        lampPost(s, 0, 1, 14, 3);
        lampPost(s, 12, 1, 14, 3);
        s.fill(0, 1, 3, 0, 2, 7, Kit.log(SPRUCE_LOG, "z"));
        s.set(0, 3, 4, Kit.log(SPRUCE_LOG, "z")).set(0, 3, 5, Kit.log(SPRUCE_LOG, "z")).set(0, 3, 6, Kit.log(SPRUCE_LOG, "z"));
        s.set(10, 1, 0, HAY).set(11, 1, 0, HAY).set(11, 2, 0, HAY).set(12, 1, 1, HAY);
        s.set(1, 1, 0, Kit.ROUGH).set(1, 2, 0, Kit.lantern());
        for (int z = 3; z <= 11; z++) {
            s.set(12, 0, z, GRASS);
            if (z % 2 == 1) {
                s.set(12, 1, z, z % 4 == 1 ? "minecraft:poppy" : "minecraft:cornflower");
            }
        }
        guildBHall(s);
        guildBLoft(s, under);
        s.connect();
        s.trimSky(3);
        return new Drawing(s, new int[]{6, 2, 11}, new int[]{4, 2, 6});
    }

    /**
     * The hall of the guild house of rank B, its people standing at layer 2 under the loft at layer 6.
     */
    private static void guildBHall(Structure s) {
        int f = 2;
        hearth(s, 6, f, 2, 2, false);
        s.set(3, 4, 3, Kit.wallBanner("red", "south")).set(9, 4, 3, Kit.wallBanner("red", "south"));
        // The registrar: the counter with the desk and a gate, the post and its chest behind it, a shelf closing the nook.
        s.set(2, f, 5, gate("south")).set(3, f, 5, DESK);
        s.set(2, f, 3, WORK_POST).set(3, f, 3, Kit.chest("south"));
        s.fill(4, f, 4, 4, f + 1, 4, BOOKSHELF).set(4, f + 2, 4, Furnish.candles(2));
        s.set(4, f, 5, PLANKS).set(4, f + 1, 5, Kit.lantern());
        chandelier(s, 3, 4, 5, 5);
        // The contract board on the west wall.
        contractBoard(s, 2, 7, 2, 11, f, 5, "east");
        // The long table, the red carpet from the door to the hearth.
        feastTable(s, 6, 6, 6, 9, f);
        s.fill(6, f, 10, 6, f, 11, Kit.CLOTH_CARPET).fill(6, f, 4, 6, f, 5, Kit.CLOTH_CARPET);
        // The stair up the east wall to the loft, bookshelves under it.
        straightStair(s, 10, 11, 0, -1, f, 5, SPRUCE_STAIRS, BOOKSHELF);
        // Trophies on the east wall: racks of weapons, a skull on a pike, the heads of beasts, shields, the guild's banner.
        s.set(10, f, 3, grindstone("west")).set(10, f, 5, grindstone("west")).set(9, f, 3, grindstone("north"));
        s.set(10, f, 4, Kit.fence("spruce")).set(10, f + 1, 4, SKULL);
        s.set(10, f + 2, 3, shield("west")).set(10, f + 2, 5, shield("west"));
        s.set(10, f + 3, 4, trophy("zombie_wall_head", "west"));
        // Light.
        chandelier(s, 6, 7, 5, 5);
        chandelier(s, 8, 10, 5, 5);
        chandelier(s, 4, 9, 5, 5);
    }

    /**
     * The loft of the guild house of rank B, its people standing at layer 7: a partition of timber across it, the map room
     * behind and the room of the master over the street.
     */
    private static void guildBLoft(Structure s, int[] under) {
        int f = 7;
        // The partition with a doorway at the head of the stair and a door into the master's room.
        for (int x = 2; x <= 9; x++) {
            s.fill(x, f, 7, x, under[x] - 1, 7, Kit.PLASTER);
        }
        s.fill(4, f, 7, 4, under[4] - 1, 7, Kit.log(OAK_LOG, "y")).fill(8, f, 7, 8, under[8] - 1, 7, Kit.log(OAK_LOG, "y"));
        s.door(6, f, 7, DOOR, "north", "left");
        s.fill(10, f, 7, 10, f + 1, 7, AIR);
        s.set(10, f + 2, 7, Kit.log(OAK_LOG, "x"));
        // Rails round the opening of the stair.
        s.set(9, f, 8, Kit.fence("spruce")).set(9, f, 9, Kit.fence("spruce")).set(10, f, 10, Kit.fence("spruce"));
        // The map room: shelves of books, the cartography table, a table of maps with chairs, the lectern.
        Furnish.books(s, 2, 3, 3, 3, f, f + 1);
        Furnish.books(s, 9, 3, 10, 3, f, f + 1);
        s.set(5, f, 3, CARTOGRAPHY).set(5, f + 1, 3, Kit.lantern());
        mapTable(s, 6, 4, 7, 4, f);
        Furnish.chair(s, 6, f, 5, "north", "spruce");
        Furnish.chair(s, 7, f, 5, "north", "spruce");
        s.set(2, f, 6, lectern("east"));
        s.set(2, f, 5, BOOKSHELF);
        chandelier(s, 6, 5, 10, under[6] - 1);
        // The master's room: the bed of state, a desk at the window, a rug, a wardrobe and the banner of the guild.
        Furnish.canopyBed(s, 4, f, 10, "west", "spruce");
        Furnish.desk(s, 7, f, 12, "north", "spruce");
        Furnish.rug(s, 5, 9, 6, 11, f, Kit.CLOTH_CARPET);
        Furnish.crate(s, 9, f, 12, "spruce");
        Furnish.crate(s, 10, f, 12, "spruce");
        s.set(9, f + 1, 12, Furnish.candles(1));
        s.set(2, f + 1, 12, Kit.wallTorch("east"));
        chandelier(s, 6, 10, 10, under[6] - 1);
    }

    // ---------------------------------------------------------------- rank A

    /** The hall of rank A: its walls, the gallery floor and the top of its walls under the eaves. */
    private static final int A_X1 = 1;
    private static final int A_X2 = 13;
    private static final int A_Z1 = 2;
    private static final int A_Z2 = 15;
    private static final int A_GALLERY = 7;
    private static final int A_EAVES = 11;
    /** The tower at the front east corner of the hall: its body, and the lookout over the corbels. */
    private static final int A_TX1 = 13;
    private static final int A_TX2 = 17;
    private static final int A_TZ1 = 12;
    private static final int A_TZ2 = 16;
    private static final int A_LOOKOUT = 18;

    /**
     * The guild hall of rank A, 19 by 19: a great hall of stone below and timber frame above, its tall gable to the street
     * under a roof of tiles, entered through a gabled porch on rough piers. At its front east corner a slender square tower
     * carries the black banner of the sword of the guild, widens on corbels into a lookout with open arches and ends in a
     * spire with the banner of the kingdom on its point. A chimney climbs the back gable, and behind the tower a fenced yard
     * holds a training dummy and an archery butt.
     *
     * <p>Inside, the great hall rises to the roof: two long tables with benches, the hearth at the back under the gallery
     * with skulls on its mantel and a shield over it, the counter of the registrar with the guild desk and his post behind
     * it, the trophies of the guild, the contract board of dark wood hung with notices on the west wall and a stair along the
     * east wall up to the gallery. On the gallery lie the map room with its shelves, its map table and the map of the lands
     * painted on the gable wall, and the loge of the guild master with his bed of state.
     */
    private static Drawing guildA() {
        Structure s = new Structure(19, 34, 19);
        s.fill(0, 0, 0, 18, 0, 18, Kit.ROUGH);
        s.fill(6, 0, 16, 8, 0, 18, Kit.TRIM);
        guildAWalls(s);
        int[] under = guildARoof(s);
        guildAPorch(s);
        guildATower(s);
        guildAHall(s);
        guildAGallery(s, under);
        guildAYard(s);
        s.connect();
        s.trimSky(3);
        return new Drawing(s, new int[]{7, 2, 14}, new int[]{10, 2, 8});
    }

    /**
     * The walls of the hall of rank A: a ground storey of stone on a plinth of rough stone with quoins, a string course at
     * the gallery floor, an upper storey of timber frame with plaster, windows with sills of trim and shutters, buttresses
     * along the west side.
     */
    private static void guildAWalls(Structure s) {
        s.fill(A_X1 + 1, 1, A_Z1 + 1, A_X2 - 1, 1, A_Z2 - 1, PLANKS);
        s.walls(A_X1, 1, A_Z1, A_X2, A_GALLERY - 1, A_Z2, Kit.WALL);
        s.walls(A_X1, 1, A_Z1, A_X2, 1, A_Z2, Kit.ROUGH);
        quoins(s, A_X1, A_Z1, 1, 1, 1, A_GALLERY - 1);
        quoins(s, A_X2, A_Z1, -1, 1, 1, A_GALLERY - 1);
        quoins(s, A_X1, A_Z2, 1, -1, 1, A_GALLERY - 1);
        // The string course of trim at the floor of the gallery, a cornice of it all round.
        s.walls(A_X1, A_GALLERY, A_Z1, A_X2, A_GALLERY, A_Z2, Kit.TRIM);
        for (int x : new int[]{1, 2, 3, 11, 12}) {
            s.set(x, A_GALLERY, A_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        }
        for (int z = A_Z1; z <= A_Z2; z++) {
            s.set(A_X1 - 1, A_GALLERY, z, Kit.upside(Kit.TRIM_STAIRS, "east"));
        }
        s.set(A_X1 - 1, A_GALLERY, A_Z2 + 1, Kit.topSlab(Kit.TRIM_SLAB));
        for (int z = A_Z1; z < A_TZ1; z++) {
            s.set(A_X2 + 1, A_GALLERY, z, Kit.upside(Kit.TRIM_STAIRS, "west"));
        }
        for (int x : new int[]{1, 2, 3, 4, 10, 11, 12, 13}) {
            s.set(x, A_GALLERY, A_Z1 - 1, Kit.upside(Kit.TRIM_STAIRS, "south"));
        }
        s.set(A_X1 - 1, A_GALLERY, A_Z1 - 1, Kit.topSlab(Kit.TRIM_SLAB)).set(A_X2 + 1, A_GALLERY, A_Z1 - 1, Kit.topSlab(Kit.TRIM_SLAB));
        s.set(2, 4, A_Z1 - 1, Kit.wallTorch("north")).set(11, 4, A_Z1 - 1, Kit.wallTorch("north"));
        // The upper storey: plaster between oak posts, a plate of logs under the eaves.
        s.walls(A_X1, A_GALLERY + 1, A_Z1, A_X2, A_EAVES, A_Z2, Kit.PLASTER);
        for (int x : new int[]{1, 4, 7, 10, 13}) {
            s.fill(x, A_GALLERY + 1, A_Z1, x, A_EAVES - 1, A_Z1, Kit.log(OAK_LOG, "y"));
            s.fill(x, A_GALLERY + 1, A_Z2, x, A_EAVES - 1, A_Z2, Kit.log(OAK_LOG, "y"));
        }
        for (int z : new int[]{5, 9, 12}) {
            s.fill(A_X1, A_GALLERY + 1, z, A_X1, A_EAVES - 1, z, Kit.log(OAK_LOG, "y"));
            s.fill(A_X2, A_GALLERY + 1, z, A_X2, A_EAVES - 1, z, Kit.log(OAK_LOG, "y"));
        }
        s.beam(A_X1, A_Z1, A_X2, A_Z2, A_EAVES, OAK_LOG);
        // Windows of the ground storey: tall, with sills of trim and lintels with a carved keystone.
        int[][] low = {{2, A_Z2, 0, 1}, {3, A_Z2, 0, 1}, {11, A_Z2, 0, 1}, {12, A_Z2, 0, 1},
                {A_X1, 6, -1, 0}, {A_X1, 13, -1, 0}, {A_X2, 5, 1, 0}, {A_X2, 9, 1, 0}, {3, A_Z1, 0, -1}, {11, A_Z1, 0, -1}};
        for (int[] w : low) {
            s.fill(w[0], 3, w[1], w[0], 5, w[1], Kit.GLASS);
            s.set(w[0] + w[2], 2, w[1] + w[3], Kit.topSlab(Kit.TRIM_SLAB));
            s.set(w[0], 6, w[1], Kit.TRIM);
        }
        s.set(A_X1, 6, 6, Kit.CARVED).set(A_X1, 6, 13, Kit.CARVED).set(A_X2, 6, 5, Kit.CARVED).set(A_X2, 6, 9, Kit.CARVED);
        // Windows of the upper storey with their shutters.
        int up = A_GALLERY + 2;
        for (int x : new int[]{2, 3, 5, 6, 8, 9, 11, 12}) {
            s.fill(x, up, A_Z2, x, up + 1, A_Z2, Kit.GLASS);
        }
        for (int z : new int[]{7, 10, 11, 13, 14}) {
            s.fill(A_X1, up, z, A_X1, up + 1, z, Kit.GLASS);
        }
        for (int z : new int[]{7, 10, 11}) {
            s.fill(A_X2, up, z, A_X2, up + 1, z, Kit.GLASS);
        }
        for (int y = up; y <= up + 1; y++) {
            s.set(1, y, A_Z2 + 1, Kit.shutter(Kit.SHUTTER, "south")).set(4, y, A_Z2 + 1, Kit.shutter(Kit.SHUTTER, "south"));
            s.set(10, y, A_Z2 + 1, Kit.shutter(Kit.SHUTTER, "south"));
            s.set(A_X1 - 1, y, 6, Kit.shutter(Kit.SHUTTER, "west")).set(A_X1 - 1, y, 8, Kit.shutter(Kit.SHUTTER, "west"));
            s.set(A_X1 - 1, y, 9, Kit.shutter(Kit.SHUTTER, "west")).set(A_X1 - 1, y, 12, Kit.shutter(Kit.SHUTTER, "west"));
            s.set(A_X1 - 1, y, 15, Kit.shutter(Kit.SHUTTER, "west"));
        }
        // Buttresses of rough stone along the west side.
        for (int z : new int[]{4, 9, 11}) {
            s.fill(A_X1 - 1, 1, z, A_X1 - 1, 4, z, Kit.ROUGH);
            s.set(A_X1 - 1, 5, z, Kit.stairs(Kit.ROUGH_STAIRS, "east"));
        }
        for (int z : new int[]{7, 10, 14}) {
            s.set(A_X1 - 1, 4, z, Kit.wallTorch("west"));
        }
    }

    /**
     * The roof of tiles over the hall of rank A with its gables, the front one of plaster in a frame of oak with windows,
     * the back one of stone with the chimney breast of rough stone and the stack of brick climbing it; dormers in both
     * slopes.
     *
     * @return the lowest layer of the roof in every column from x 0 to 14
     */
    private static int[] guildARoof(Structure s) {
        int[] under = roofZ(s, A_X1 - 1, A_X2 + 1, A_Z1 - 1, A_Z2 + 1, A_EAVES, 0, Kit.TILE);
        gableAtZ(s, A_X1 - 1, under, A_Z2, A_EAVES + 1, Kit.PLASTER);
        gableAtZ(s, A_X1 - 1, under, A_Z1, A_EAVES + 1, Kit.WALL);
        // The front gable: a king post, two posts and a collar of oak, windows between them.
        s.fill(7, A_EAVES + 1, A_Z2, 7, under[7] - 1, A_Z2, Kit.log(OAK_LOG, "y"));
        s.fill(4, A_EAVES + 1, A_Z2, 4, 14, A_Z2, Kit.log(OAK_LOG, "y"));
        s.fill(10, A_EAVES + 1, A_Z2, 10, 14, A_Z2, Kit.log(OAK_LOG, "y"));
        s.fill(5, 15, A_Z2, 6, 15, A_Z2, Kit.log(OAK_LOG, "x")).fill(8, 15, A_Z2, 9, 15, A_Z2, Kit.log(OAK_LOG, "x"));
        s.fill(5, 12, A_Z2, 6, 13, A_Z2, Kit.GLASS).fill(8, 12, A_Z2, 9, 13, A_Z2, Kit.GLASS);
        s.set(6, 16, A_Z2, Kit.GLASS).set(8, 16, A_Z2, Kit.GLASS);
        // Dormers in both slopes over the hall.
        dormer(s, A_X1, 1, 6, A_EAVES + 1, Kit.TILE);
        dormer(s, A_X1, 1, 11, A_EAVES + 1, Kit.TILE);
        dormer(s, A_X2, -1, 7, A_EAVES + 1, Kit.TILE);
        // The back gable: small windows high up, the chimney breast and the stack.
        s.set(4, 13, A_Z1, Kit.GLASS).set(10, 13, A_Z1, Kit.GLASS);
        s.fill(5, 1, A_Z1 - 2, 9, 8, A_Z1 - 1, Kit.ROUGH);
        s.set(5, 9, A_Z1 - 1, Kit.stairs(Kit.ROUGH_STAIRS, "east")).set(9, 9, A_Z1 - 1, Kit.stairs(Kit.ROUGH_STAIRS, "west"));
        s.set(5, 9, A_Z1 - 2, Kit.stairs(Kit.ROUGH_STAIRS, "east")).set(9, 9, A_Z1 - 2, Kit.stairs(Kit.ROUGH_STAIRS, "west"));
        s.fill(6, 9, A_Z1 - 1, 8, under[6] - 1, A_Z1 - 1, Kit.ROUGH);
        s.fill(6, 9, A_Z1 - 2, 8, 15, A_Z1 - 2, Kit.ROUGH);
        s.fill(6, 16, A_Z1 - 2, 8, 20, A_Z1 - 2, Kit.CHIMNEY);
        s.set(7, 21, A_Z1 - 2, Kit.CHIMNEY_POT);
        s.set(6, 21, A_Z1 - 2, Kit.slab(Kit.CHIMNEY_SLAB)).set(8, 21, A_Z1 - 2, Kit.slab(Kit.CHIMNEY_SLAB));
        return under;
    }

    /**
     * The porch of rank A before the door: two piers of rough stone and an arch with a carved keystone, a little gable of
     * plaster with the black banner of the guild, a roof of tiles, a floor and wide steps of trim and lanterns on posts
     * beside them. The door has lights of glass beside it and over it in a frame of trim.
     */
    private static void guildAPorch(Structure s) {
        int front = A_Z2 + 2;
        s.fill(6, 1, A_Z2 + 1, 8, 1, front, Kit.TRIM);
        s.fill(5, 1, A_Z2 + 1, 5, 6, front, Kit.WALL).fill(9, 1, A_Z2 + 1, 9, 6, front, Kit.WALL);
        s.fill(5, 1, front, 5, 6, front, Kit.ROUGH).fill(9, 1, front, 9, 6, front, Kit.ROUGH);
        s.fill(5, 2, A_Z2 + 1, 5, 3, A_Z2 + 1, AIR).fill(9, 2, A_Z2 + 1, 9, 3, A_Z2 + 1, AIR);
        s.set(5, 4, A_Z2 + 1, Kit.CARVED).set(9, 4, A_Z2 + 1, Kit.CARVED);
        s.fill(6, 5, front, 8, 6, front, Kit.WALL);
        s.set(6, 5, front, Kit.upside(Kit.WALL_STAIRS, "west")).set(8, 5, front, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(7, 5, front, Kit.CARVED);
        s.fill(5, 6, front, 9, 6, front, Kit.TRIM);
        int[] under = roofZ(s, 4, 10, A_Z2 + 1, front + 1, 6, 0, Kit.TILE);
        gableAtZ(s, 4, under, front, 7, Kit.PLASTER);
        s.set(7, 7, front + 1, Kit.wallBanner("black", "south"));
        s.set(7, 8, A_Z2 + 1, Kit.hangingLantern());
        // The steps, their cheeks and the lantern posts.
        s.fill(5, 1, front + 1, 9, 1, front + 1, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        for (int x : new int[]{4, 10}) {
            s.set(x, 1, front + 1, Kit.ROUGH).set(x, 2, front + 1, Kit.ROUGH_FENCE).set(x, 3, front + 1, Kit.lantern());
        }
        // The door with its lights and frame.
        s.door(7, 2, A_Z2, DOOR, "north", "left");
        s.fill(6, 2, A_Z2, 6, 3, A_Z2, Kit.GLASS).fill(8, 2, A_Z2, 8, 3, A_Z2, Kit.GLASS);
        s.fill(6, 4, A_Z2, 8, 4, A_Z2, Kit.GLASS);
        s.fill(5, 5, A_Z2, 9, 5, A_Z2, Kit.TRIM).set(7, 5, A_Z2, Kit.CARVED);
        s.set(4, 5, A_Z2 + 1, Kit.wallBanner("red", "south"));
        s.set(10, 5, A_Z2 + 1, Kit.wallBanner("red", "south"));
    }

    /**
     * The tower of rank A: a slender body of stone on a base of rough stone with quoins and slits, the black banner of the
     * sword hanging from a rod on its front, a ring of corbels carrying the lookout with an open arch on every side, a spire
     * of slate and the banner of the kingdom on its point. A ladder climbs inside from the hall to the lookout.
     */
    private static void guildATower(Structure s) {
        int corbel = A_LOOKOUT - 1;
        s.fill(A_TX1, 1, A_TZ1, A_TX2, corbel, A_TZ2, Kit.WALL);
        s.walls(A_TX1, 1, A_TZ1, A_TX2, 2, A_TZ2, Kit.ROUGH);
        quoins(s, A_TX2, A_TZ1, -1, 1, 1, corbel);
        quoins(s, A_TX2, A_TZ2, -1, -1, 1, corbel);
        quoins(s, A_TX1, A_TZ2, 1, -1, 1, corbel);
        s.fill(A_TX1 + 1, 1, A_TZ1 + 1, A_TX2 - 1, 1, A_TZ2 - 1, PLANKS);
        s.fill(A_TX1 + 1, 2, A_TZ1 + 1, A_TX2 - 1, A_LOOKOUT, A_TZ2 - 1, AIR);
        int mid = (A_TX1 + A_TX2) / 2;
        int midZ = (A_TZ1 + A_TZ2) / 2;
        // Slits on the faces.
        s.fill(mid, 4, A_TZ2, mid, 5, A_TZ2, Kit.GLASS);
        s.fill(A_TX2, 5, midZ, A_TX2, 6, midZ, Kit.GLASS).fill(A_TX2, 10, midZ, A_TX2, 11, midZ, Kit.GLASS);
        s.fill(A_TX2, 14, midZ, A_TX2, 15, midZ, Kit.GLASS);
        s.fill(mid, 8, A_TZ1, mid, 9, A_TZ1, Kit.GLASS).fill(mid, 13, A_TZ1, mid, 14, A_TZ1, Kit.GLASS);
        // The banner of the guild hung from a rod of dark oak.
        s.fill(A_TX1, A_LOOKOUT - 2, A_TZ2 + 1, A_TX2, A_LOOKOUT - 2, A_TZ2 + 1, Kit.log(DARK_LOG, "x"));
        swordBanner(s, A_TX1 + 1, A_LOOKOUT - 3, A_TZ2 + 1);
        // The corbels and the lookout.
        int x1 = A_TX1 - 1;
        int x2 = A_TX2 + 1;
        int z1 = A_TZ1 - 1;
        int z2 = A_TZ2 + 1;
        s.fill(A_TX1, corbel, z1, A_TX2, corbel, z1, Kit.upside(Kit.TRIM_STAIRS, "south"));
        s.fill(A_TX1, corbel, z2, A_TX2, corbel, z2, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.fill(x1, corbel, A_TZ1, x1, corbel, A_TZ2, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.fill(x2, corbel, A_TZ1, x2, corbel, A_TZ2, Kit.upside(Kit.TRIM_STAIRS, "west"));
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            s.set(c[0], corbel, c[1], Kit.topSlab(Kit.TRIM_SLAB));
        }
        s.fill(x1, A_LOOKOUT, z1, x2, A_LOOKOUT, z2, Kit.TRIM);
        s.fill(x1 + 1, A_LOOKOUT, z1 + 1, x2 - 1, A_LOOKOUT, z2 - 1, PLANKS);
        s.walls(x1, A_LOOKOUT + 1, z1, x2, A_LOOKOUT + 3, z2, Kit.WALL);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            s.fill(c[0], A_LOOKOUT + 1, c[1], c[0], A_LOOKOUT + 3, c[1], Kit.ROUGH);
        }
        for (int side = 0; side < 4; side++) {
            for (int d = -1; d <= 1; d++) {
                int[] p = side == 0 ? new int[]{mid + d, z2} : side == 1 ? new int[]{mid + d, z1} : side == 2 ? new int[]{x1, midZ + d} : new int[]{x2, midZ + d};
                s.set(p[0], A_LOOKOUT + 1, p[1], Kit.fence("oak"));
                s.set(p[0], A_LOOKOUT + 2, p[1], AIR);
            }
        }
        s.set(mid - 1, A_LOOKOUT + 3, z2, Kit.upside(Kit.WALL_STAIRS, "west")).set(mid + 1, A_LOOKOUT + 3, z2, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(mid - 1, A_LOOKOUT + 3, z1, Kit.upside(Kit.WALL_STAIRS, "west")).set(mid + 1, A_LOOKOUT + 3, z1, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(x1, A_LOOKOUT + 3, midZ - 1, Kit.upside(Kit.WALL_STAIRS, "north")).set(x1, A_LOOKOUT + 3, midZ + 1, Kit.upside(Kit.WALL_STAIRS, "south"));
        s.set(x2, A_LOOKOUT + 3, midZ - 1, Kit.upside(Kit.WALL_STAIRS, "north")).set(x2, A_LOOKOUT + 3, midZ + 1, Kit.upside(Kit.WALL_STAIRS, "south"));
        s.set(mid, A_LOOKOUT + 3, z2, Kit.CARVED).set(mid, A_LOOKOUT + 3, z1, Kit.CARVED);
        s.set(x1, A_LOOKOUT + 3, midZ, Kit.CARVED).set(x2, A_LOOKOUT + 3, midZ, Kit.CARVED);
        s.set(mid, A_LOOKOUT + 3, midZ, Kit.hangingLantern());
        int top = needle(s, x1, z1, x2, z2, A_LOOKOUT + 4, 3, Kit.SLATE, PLANKS);
        s.set(mid, top, midZ, Kit.fence("oak"));
        s.set(mid, top + 1, midZ, Kit.banner("red", 0));
        // The ladder from the hall to the lookout, the door from the hall, a rack of weapons and lights in the shaft.
        for (int y = 2; y <= A_LOOKOUT; y++) {
            s.set(mid, y, A_TZ1 + 1, Kit.ladder("south"));
        }
        s.door(A_TX1, 2, midZ, DOOR, "east", "left");
        s.set(A_TX2 - 1, 2, midZ, grindstone("west")).set(A_TX2 - 1, 2, A_TZ2 - 1, Kit.lantern());
        s.set(A_TX2 - 1, 7, A_TZ2 - 1, Kit.wallTorch("north")).set(A_TX2 - 1, 12, midZ - 1, Kit.wallTorch("west"));
        s.set(A_TX2 - 1, 16, A_TZ2 - 1, Kit.wallTorch("north"));
    }

    /**
     * The great hall of rank A: the hearth with its trophies, the counter of the registrar, the contract board, two long
     * tables with benches, the stair to the gallery with bookshelves under it, chandeliers hanging from the roof.
     */
    private static void guildAHall(Structure s) {
        int f = 2;
        hearth(s, 7, f, A_Z1, 2, true);
        // The trophy corner in the west: racks of weapons, a skull on a pike, the heads of beasts and shields.
        s.set(2, f, 3, grindstone("east")).set(2, f, 5, grindstone("east")).set(3, f, 3, grindstone("south"));
        s.set(2, f, 4, Kit.fence("spruce")).set(2, f + 1, 4, SKULL);
        s.set(2, f + 3, 3, shield("east")).set(2, f + 3, 5, shield("east"));
        s.set(2, f + 3, 4, trophy("creeper_wall_head", "east"));
        s.set(3, f + 4, 3, trophy("skeleton_wall_skull", "south"));
        chandelier(s, 3, 5, 6, 6);
        // The registrar: the counter with the guild desk and a gate, the post with its chest behind, shelves of ledgers.
        s.set(10, f, 7, gate("south"));
        s.set(11, f, 7, DESK);
        s.set(12, f, 7, PLANKS).set(12, f + 1, 7, Kit.lantern());
        s.set(11, f, 5, WORK_POST).set(12, f, 5, Kit.chest("west"));
        s.fill(10, f, 3, 12, f + 2, 3, BOOKSHELF);
        s.set(12, f, 4, PLANKS).set(12, f + 1, 4, Furnish.candles(3));
        s.set(10, f, 4, Kit.stairs(SPRUCE_STAIRS, "west"));
        chandelier(s, 11, 5, 6, 6);
        // The posts under the edge of the gallery.
        s.fill(4, f, 7, 4, A_GALLERY - 1, 7, Kit.log(SPRUCE_LOG, "y")).fill(9, f, 7, 9, A_GALLERY - 1, 7, Kit.log(SPRUCE_LOG, "y"));
        s.set(5, A_GALLERY - 1, 7, Kit.upside(SPRUCE_STAIRS, "west")).set(3, A_GALLERY - 1, 7, Kit.upside(SPRUCE_STAIRS, "east"));
        s.set(8, A_GALLERY - 1, 7, Kit.upside(SPRUCE_STAIRS, "east")).set(10, A_GALLERY - 1, 7, Kit.upside(SPRUCE_STAIRS, "west"));
        // The contract board on the west wall.
        contractBoard(s, 2, 8, 2, 13, f, 6, "east");
        // The long tables and their benches, the red carpet from the door to the hearth.
        feastTable(s, 5, 9, 5, 13, f);
        feastTable(s, 9, 9, 9, 13, f);
        s.fill(7, f, 4, 7, f, 14, Kit.CLOTH_CARPET);
        // The stair along the east wall up to the gallery, bookshelves under it.
        straightStair(s, 12, 13, 0, -1, f, 6, SPRUCE_STAIRS, BOOKSHELF);
        // Lights: chandeliers over the tables from the roof, torches by the door.
        chandelier(s, 5, 11, 6, 15);
        chandelier(s, 9, 11, 6, 15);
        chandelier(s, 7, 12, 8, 17);
        s.set(10, 4, 14, Kit.wallTorch("north"));
        s.set(4, 4, 14, Kit.wallTorch("north"));
    }

    /**
     * The gallery of rank A over the back of the hall: a railing along its edge; the map room with bookshelves along the
     * back wall, the map of the lands painted over them in a frame, the cartography table, a table of maps and the lectern;
     * and behind a partition of timber the loge of the guild master with his bed of state and his desk.
     */
    private static void guildAGallery(Structure s, int[] under) {
        int y = A_GALLERY + 1;
        s.fill(A_X1 + 1, A_GALLERY, A_Z1 + 1, A_X2 - 1, A_GALLERY, 7, PLANKS);
        s.fill(A_X1 + 1, A_GALLERY, 7, A_X2 - 1, A_GALLERY, 7, Kit.log(SPRUCE_LOG, "x"));
        s.fill(A_X1 + 1, y, 7, A_X2 - 2, y, 7, Kit.fence("spruce"));
        // The map room: shelves along the back wall, the map over them, the tables.
        Furnish.books(s, 7, A_Z1 + 1, 12, A_Z1 + 1, y, y + 1);
        paintedMap(s, 8, y + 4, A_Z1 + 1, new String[]{"lcg", "gBl", "bgy"});
        s.set(9, y, 5, CARTOGRAPHY).set(9, y + 1, 5, Kit.lantern());
        Furnish.chair(s, 8, y, 5, "east", "spruce");
        mapTable(s, 10, 5, 11, 5, y);
        s.set(12, y, 4, lectern("west"));
        s.set(7, y + 2, 6, Kit.wallTorch("east"));
        s.set(12, y + 2, 6, Kit.wallTorch("west"));
        // The partition of the master's loge, its door, the bed of state, a desk, a rug and a wardrobe.
        for (int z = A_Z1 + 1; z <= 6; z++) {
            s.fill(6, y, z, 6, under[6] - 1, z, Kit.PLASTER);
        }
        s.fill(6, y, A_Z1 + 1, 6, under[6] - 1, A_Z1 + 1, Kit.log(OAK_LOG, "y"));
        s.door(6, y, 6, DOOR, "west", "left");
        s.set(6, y + 2, 6, Kit.log(OAK_LOG, "z"));
        Furnish.canopyBed(s, 3, y, 5, "north", "spruce");
        Furnish.desk(s, 5, y, 3, "south", "spruce");
        Furnish.rug(s, 5, 5, 5, 6, y, Kit.CLOTH_CARPET);
        s.set(2, y + 1, 6, Kit.wallBanner("black", "east"));
        s.set(2, y + 2, 5, Kit.wallTorch("east"));
        Furnish.crate(s, 2, y, 6, "spruce");
    }

    /**
     * The yard of rank A behind the tower: coarse ground inside a fence with a gate, a training dummy, an archery butt, a
     * rack of weapons, hay and a lantern on a post.
     */
    private static void guildAYard(Structure s) {
        s.fill(14, 0, 1, 18, 0, 11, COARSE);
        s.fill(18, 1, 1, 18, 1, 11, Kit.fence("spruce"));
        s.fill(13, 1, 0, 17, 1, 0, Kit.fence("spruce")).set(18, 1, 0, Kit.fence("spruce"));
        s.set(18, 1, 6, gate("east"));
        dummy(s, 16, 1, 3, "west");
        s.set(17, 1, 9, HAY).set(17, 2, 9, TARGET).set(17, 1, 10, HAY);
        s.set(15, 1, 1, grindstone("south")).set(16, 1, 1, HAY);
        s.set(15, 1, 10, Kit.fence("spruce")).set(15, 2, 10, Kit.fence("spruce")).set(15, 3, 10, Kit.lantern());
        s.set(17, 1, 1, Kit.fence("spruce")).set(17, 2, 1, Kit.lantern());
    }

    // ---------------------------------------------------------------- rank S

    /** The hall of rank S: its walls, the upper floor and the plate under the eaves. */
    private static final int S_X1 = 3;
    private static final int S_X2 = 19;
    private static final int S_Z1 = 1;
    private static final int S_Z2 = 11;
    private static final int S_UPPER = 7;
    private static final int S_EAVES = 12;
    /** The round towers at the front corners: the middles of the west and the east one, their floors and their top. */
    private static final int S_TW = 4;
    private static final int S_TE = 18;
    private static final int S_TZ = 14;
    private static final int S_TOWER_ROOM = 13;
    private static final int S_TOWER_TOP = 19;

    /**
     * The guildhall of rank S, 23 by 25: a broad hall of stone with an upper storey of timber frame under a steep roof of
     * tiles, a cross gable over the portal in the middle of its front, and on the front corners two round towers of stone
     * with slate spires, each with the black banner of the sword of the guild on its face. Between the towers lies a
     * forecourt behind a low wall with a gateway of an arch, banners on poles, a training dummy and an archery butt. The
     * portal is a round arch with lights beside and over the door under a porch on columns that carries the balcony of the
     * map room.
     *
     * <p>Inside, the great hall fills the ground floor: the hearth in the middle of the back wall with its trophies, two long
     * tables, the contract board on the west wall, the counter of the registrar with the guild desk and his post in the east
     * corner and racks of weapons on the east wall. The west tower holds a winding stair up to the upper floor and on to a
     * lookout under its spire; the east one the armoury and over it the strong room of the guild. Upstairs the map room lies
     * in the middle behind the balcony between the rooms of two guild masters.
     */
    private static Drawing guildS() {
        Structure s = new Structure(23, 36, 25);
        s.fill(0, 0, 0, 22, 0, 24, Kit.ROUGH);
        guildSWalls(s);
        int[] under = guildSRoof(s);
        roundGuildTower(s, S_TW, S_TZ, S_UPPER, S_TOWER_ROOM, S_TOWER_TOP, true);
        roundGuildTower(s, S_TE, S_TZ, S_UPPER, S_TOWER_ROOM, S_TOWER_TOP, false);
        guildSPortal(s);
        guildSHall(s);
        guildSUpper(s, under);
        guildSForecourt(s);
        s.connect();
        s.trimSky(3);
        return new Drawing(s, new int[]{11, 2, 10}, new int[]{16, 2, 6});
    }

    /**
     * The walls of the hall of rank S: the stone storey with quoins on a course of rough stone and its windows, a string
     * course and a cornice of trim at the upper floor, the storey of timber frame with its windows and shutters.
     */
    private static void guildSWalls(Structure s) {
        s.fill(S_X1 + 1, 1, S_Z1 + 1, S_X2 - 1, 1, S_Z2 - 1, PLANKS);
        stoneStorey(s, S_X1, S_Z1, S_X2, S_Z2, 1, S_UPPER - 1);
        s.walls(S_X1, S_UPPER, S_Z1, S_X2, S_UPPER, S_Z2, Kit.TRIM);
        s.fill(S_X1 + 1, S_UPPER, S_Z1 + 1, S_X2 - 1, S_UPPER, S_Z2 - 1, PLANKS);
        for (int x = S_X1; x <= S_X2; x++) {
            s.set(x, S_UPPER, S_Z1 - 1, Kit.upside(Kit.TRIM_STAIRS, "south"));
        }
        for (int z = S_Z1; z <= S_Z2 - 1; z++) {
            s.set(S_X1 - 1, S_UPPER, z, Kit.upside(Kit.TRIM_STAIRS, "east")).set(S_X2 + 1, S_UPPER, z, Kit.upside(Kit.TRIM_STAIRS, "west"));
        }
        s.set(S_X1 - 1, S_UPPER, S_Z1 - 1, Kit.topSlab(Kit.TRIM_SLAB)).set(S_X2 + 1, S_UPPER, S_Z1 - 1, Kit.topSlab(Kit.TRIM_SLAB));
        timberStorey(s, S_X1, S_Z1, S_X2, S_Z2, S_UPPER + 1, S_EAVES, new int[]{5, 8, 11, 14, 17}, new int[]{4, 8});
        // Windows of the stone storey.
        stoneWindow(s, 7, S_Z2, 8, S_Z2, 3, 5, "south", Kit.GLASS);
        stoneWindow(s, 14, S_Z2, 15, S_Z2, 3, 5, "south", Kit.GLASS);
        stoneWindow(s, 6, S_Z1, 7, S_Z1, 3, 5, "north", Kit.GLASS);
        stoneWindow(s, 15, S_Z1, 16, S_Z1, 3, 5, "north", Kit.GLASS);
        stoneWindow(s, S_X1, 9, S_X1, 10, 3, 5, "west", Kit.GLASS);
        stoneWindow(s, S_X2, 4, S_X2, 5, 3, 5, "east", Kit.GLASS);
        // Windows of the timber storey with shutters.
        int y = S_UPPER + 2;
        for (int[] w : new int[][]{{6, 7}, {9, 10}, {12, 13}, {15, 16}}) {
            s.fill(w[0], y, S_Z1, w[1], y + 1, S_Z1, Kit.GLASS);
            shutters(s, w[0], S_Z1, w[1], S_Z1, y, y + 1, "north");
        }
        s.fill(7, y, S_Z2, 7, y + 1, S_Z2, Kit.GLASS).fill(15, y, S_Z2, 15, y + 1, S_Z2, Kit.GLASS);
        for (int x : new int[]{S_X1, S_X2}) {
            s.fill(x, y, 5, x, y + 1, 7, Kit.GLASS);
            shutters(s, x, 5, x, 7, y, y + 1, x == S_X1 ? "west" : "east");
        }
    }

    /**
     * The roof of rank S: a steep roof of tiles over the hall with gables of plaster and oak at both ends, a cross gable over
     * the portal with a band of windows, and the chimney of the hearth climbing the back eave.
     *
     * @return the lowest layer of the roof in every row from z 0 to 12
     */
    private static int[] guildSRoof(Structure s) {
        int[] under = roofX(s, S_X1 - 1, S_X2 + 1, S_Z1 - 1, S_Z2 + 1, S_EAVES, 2, Kit.TILE);
        for (int x : new int[]{S_X1, S_X2}) {
            gableAtX(s, S_Z1 - 1, under, x, S_EAVES + 1, Kit.PLASTER);
            s.fill(x, S_EAVES + 1, 6, x, under[6] - 1, 6, Kit.log(OAK_LOG, "y"));
            s.fill(x, S_EAVES + 1, 3, x, under[3] - 1, 3, Kit.log(OAK_LOG, "y")).fill(x, S_EAVES + 1, 9, x, under[9] - 1, 9, Kit.log(OAK_LOG, "y"));
            s.fill(x, 16, 4, x, 16, 5, Kit.log(OAK_LOG, "z")).fill(x, 16, 7, x, 16, 8, Kit.log(OAK_LOG, "z"));
            s.fill(x, 14, 4, x, 15, 5, Kit.GLASS).fill(x, 14, 7, x, 15, 8, Kit.GLASS);
            s.set(x, 18, 6, Kit.GLASS).set(x, 17, 6, Kit.log(OAK_LOG, "y"));
        }
        // The cross gable over the portal: the main roof opened in front of it, its own roof running into the main one.
        int[] cross = crossGable(s, 7, 15, 6, S_Z2 + 1, S_EAVES, 1, Kit.TILE, 6);
        gableAtZ(s, 7, cross, S_Z2, S_EAVES + 1, Kit.PLASTER);
        s.fill(9, S_EAVES + 1, S_Z2, 9, S_EAVES + 2, S_Z2, Kit.log(OAK_LOG, "y")).fill(13, S_EAVES + 1, S_Z2, 13, S_EAVES + 2, S_Z2, Kit.log(OAK_LOG, "y"));
        s.fill(10, S_EAVES + 1, S_Z2, 12, S_EAVES + 2, S_Z2, Kit.GLASS);
        s.fill(10, S_EAVES + 3, S_Z2, 12, S_EAVES + 3, S_Z2, Kit.log(OAK_LOG, "x"));
        s.set(11, S_EAVES + 4, S_Z2, Kit.GLASS);
        // The chimney: the breast of rough stone on the back wall, the stack of brick rising free over the eave.
        s.fill(9, 1, 0, 13, 9, 0, Kit.ROUGH);
        s.set(9, 10, 0, Kit.stairs(Kit.ROUGH_STAIRS, "east")).set(13, 10, 0, Kit.stairs(Kit.ROUGH_STAIRS, "west"));
        s.fill(10, 10, 0, 12, 14, 0, Kit.ROUGH);
        s.fill(10, 15, 0, 12, 21, 0, Kit.CHIMNEY);
        s.set(11, 22, 0, Kit.CHIMNEY_POT).set(10, 22, 0, Kit.slab(Kit.CHIMNEY_SLAB)).set(12, 22, 0, Kit.slab(Kit.CHIMNEY_SLAB));
        return under;
    }

    /**
     * A round tower of the guild at a front corner of a hall, seven blocks across: a sloping foot of rough stone, the shaft of
     * stone with bands of trim at its floors, slits and windows, a ring of corbels under the eaves and a slate spire with a
     * gilded finial, and the black banner of the sword of the guild hanging on its face from a rod of dark oak. Doors lead
     * into it from the hall and from the upper floor. The west tower holds a winding stair from the hall up to the upper floor
     * and on to its top room, a lookout under the spire; the east one holds the armoury of the guild and over it the strong
     * room, from which a ladder climbs to its top room.
     */
    private static void roundGuildTower(Structure s, int cx, int cz, int upper, int room, int top, boolean stair) {
        int d = 7;
        for (int y = 1; y <= top + 10; y++) {
            roundFloor(s, cx, y, cz, d, AIR);
        }
        band(s, cx, 0, cz, 9, 0, Kit.ROUGH);
        rimStairs(s, cx, 1, cz, 9, Kit.ROUGH_STAIRS);
        Kit.tube(s, cx, 1, 2, cz, d, Kit.ROUGH);
        Kit.tube(s, cx, 3, top, cz, d, Kit.WALL);
        for (int y : new int[]{upper, room, top}) {
            Kit.tube(s, cx, y, y, cz, d, Kit.TRIM);
        }
        roundFloor(s, cx, 1, cz, d, PLANKS);
        roundFloor(s, cx, upper, cz, d, PLANKS);
        roundFloor(s, cx, room, cz, d, PLANKS);
        // Slits low down, windows in the upper floor and the top room, the doors from the hall.
        int out = cx < 11 ? -1 : 1;
        for (int[] w : new int[][]{{out, 0}, {-out, 0}}) {
            roundWindow(s, cx + 3 * w[0], cz + 3 * w[1], w[0], w[1], 3, 4, false, false, Kit.GLASS);
            roundWindow(s, cx + 3 * w[0], cz + 3 * w[1], w[0], w[1], upper + 2, upper + 3, false, true, Kit.GLASS);
        }
        for (int[] w : new int[][]{{out, 0}, {0, 1}, {-out, 0}}) {
            roundWindow(s, cx + 3 * w[0], cz + 3 * w[1], w[0], w[1], room + 2, room + 4, true, true, Kit.GLASS);
        }
        s.door(cx, 2, cz - 3, DOOR, "south", "left");
        s.door(cx, upper + 1, cz - 3, DOOR, "south", "left");
        // The eaves and the spire.
        corbels(s, cx, top, cz, 9, d, Kit.TRIM_STAIRS);
        int point = roundRoof(s, cx, top + 1, cz, 9, new int[]{1, 2, 2, 3, 3}, Kit.SLATE);
        s.set(cx, point, cz, ROD);
        chandelier(s, cx, cz, top, hollowTop(s, cx, top, cz));
        // The banner of the sword on the face of the tower.
        s.fill(cx - 1, 14, cz + 4, cx + 1, 14, cz + 4, Kit.log(DARK_LOG, "x"));
        swordBanner(s, cx - 1, 13, cz + 4);
        if (stair) {
            List<int[]> ring = innerRing(d);
            int start = indexOf(ring, 1, -2);
            List<int[]> holes = new ArrayList<>();
            holes.addAll(flight(s, cx, cz, ring, start, 1, upper, Kit.WALL_STAIRS));
            holes.addAll(flight(s, cx, cz, ring, start + upper - 1, upper, room, Kit.WALL_STAIRS));
            rails(s, cx, cz, d, holes);
            // A rack of spears at the foot of the stair, a bench and a lantern in the lookout.
            s.set(cx - 2, 2, cz, grindstone("east"));
            s.set(cx - 1, 2, cz - 2, Kit.lantern());
            s.set(cx + 1, room + 1, cz - 1, Kit.stairs(SPRUCE_STAIRS, "north"));
            s.set(cx + 2, room + 1, cz - 1, Kit.stairs(SPRUCE_STAIRS, "north"));
            s.set(cx - 1, upper + 1, cz - 1, Kit.lantern());
        } else {
            // The armoury: racks of weapons, shields, a dummy in armour of iron bars and the heads of beasts.
            s.set(cx - 2, 2, cz, grindstone("east")).set(cx + 2, 2, cz, grindstone("west"));
            s.set(cx - 1, 2, cz + 2, grindstone("north")).set(cx + 1, 2, cz + 2, grindstone("north"));
            s.set(cx, 2, cz + 1, Kit.fence("spruce")).set(cx, 3, cz + 1, "minecraft:iron_block").set(cx, 4, cz + 1, SKULL);
            s.set(cx - 2, 4, cz + 1, shield("east")).set(cx + 2, 4, cz + 1, shield("west"));
            chandelier(s, cx, cz, 6, 6);
            // The strong room: gold, crates and sacks of the spoils, a lantern; a ladder up to the top room.
            s.set(cx - 2, upper + 1, cz, GOLD).set(cx - 2, upper + 1, cz + 1, GOLD);
            Furnish.crate(s, cx - 1, upper + 1, cz + 2, "spruce");
            Furnish.crate(s, cx - 1, upper + 2, cz + 2, "spruce");
            Furnish.sack(s, cx, upper + 1, cz + 2, false);
            Furnish.sack(s, cx + 1, upper + 1, cz + 2, true);
            s.set(cx - 2, upper + 2, cz, Kit.lantern());
            for (int y = upper + 1; y <= room; y++) {
                s.set(cx - 2, y, cz - 1, Kit.ladder("east"));
            }
            s.set(cx - 1, room + 1, cz + 1, Kit.stairs(SPRUCE_STAIRS, "south"));
            chandelier(s, cx, cz, room - 1, room - 1);
        }
    }

    /**
     * The portal of rank S in the middle of the front: a round arch of stone with a carved keystone over the door, lights
     * beside and over it, and before it a porch of two columns under an arch that carries the balcony of the map room with
     * its balustrade of stone and lanterns; steps of trim up to the porch, the banners of the guild on the columns.
     */
    private static void guildSPortal(Structure s) {
        int z = S_Z2;
        s.fill(9, 2, z, 9, 5, z, Kit.ROUGH).fill(13, 2, z, 13, 5, z, Kit.ROUGH);
        s.door(11, 2, z, DOOR, "north", "left");
        s.fill(10, 2, z, 10, 3, z, Kit.GLASS).fill(12, 2, z, 12, 3, z, Kit.GLASS);
        s.fill(10, 4, z, 12, 4, z, Kit.GLASS);
        s.set(10, 5, z, Kit.upside(Kit.WALL_STAIRS, "west")).set(12, 5, z, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(11, 5, z, Kit.GLASS).set(11, 6, z, Kit.CARVED);
        // The porch and the balcony on it.
        s.fill(9, 1, z + 1, 13, 1, z + 2, Kit.TRIM);
        s.fill(10, 1, z + 3, 12, 1, z + 3, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        s.set(9, 1, z + 3, Kit.ROUGH).set(13, 1, z + 3, Kit.ROUGH);
        for (int x : new int[]{9, 13}) {
            s.fill(x, 2, z + 2, x, 5, z + 2, Kit.TRIM).set(x, 6, z + 2, Kit.CARVED);
            s.set(x, 6, z + 1, Kit.WALL);
            s.set(x, 4, z + 3, Kit.wallBanner("black", "south"));
        }
        s.set(10, 6, z + 2, Kit.upside(Kit.WALL_STAIRS, "west")).set(12, 6, z + 2, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(11, 6, z + 2, Kit.CARVED);
        s.set(10, 6, z + 1, Kit.upside(Kit.WALL_STAIRS, "west")).set(12, 6, z + 1, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(11, 6, z + 1, Kit.WALL);
        s.fill(9, S_UPPER, z + 1, 13, S_UPPER, z + 2, Kit.TRIM);
        s.fill(9, S_UPPER + 1, z + 2, 13, S_UPPER + 1, z + 2, Kit.WALL_FENCE);
        s.set(9, S_UPPER + 1, z + 1, Kit.WALL_FENCE).set(13, S_UPPER + 1, z + 1, Kit.WALL_FENCE);
        s.set(9, S_UPPER + 2, z + 2, Kit.lantern()).set(13, S_UPPER + 2, z + 2, Kit.lantern());
        s.set(11, 5, z + 1, Kit.hangingLantern());
        // The balcony door and its lights.
        s.door(11, S_UPPER + 1, z, DOOR, "north", "left");
        s.fill(10, S_UPPER + 1, z, 10, S_UPPER + 2, z, Kit.GLASS).fill(12, S_UPPER + 1, z, 12, S_UPPER + 2, z, Kit.GLASS);
    }

    /**
     * The great hall of rank S, its people standing at layer 2 under the upper floor: the hearth in the middle of the back
     * wall, the counter of the registrar, the contract board, two long tables, racks of weapons, chandeliers.
     */
    private static void guildSHall(Structure s) {
        int f = 2;
        hearth(s, 11, f, S_Z1, 2, true);
        // The registrar in the north east corner: shelves of ledgers, the post and its chest, the counter with the desk.
        Furnish.books(s, 17, 2, 18, 2, f, f + 2);
        s.set(16, f, 2, Kit.stairs(SPRUCE_STAIRS, "south")).set(16, f + 2, 2, Kit.wallBanner("black", "south"));
        s.set(17, f, 3, WORK_POST).set(18, f, 3, Kit.chest("west"));
        s.set(16, f, 5, gate("south")).set(17, f, 5, DESK).set(18, f, 5, PLANKS).set(18, f + 1, 5, Kit.lantern());
        chandelier(s, 17, 4, 5, 6);
        // The contract board on the west wall.
        contractBoard(s, 4, 2, 4, 8, f, 6, "east");
        // The long tables and the red carpet from the door to the hearth.
        feastTable(s, 8, 4, 8, 9, f);
        feastTable(s, 14, 4, 14, 9, f);
        s.fill(11, f, 3, 11, f, 10, Kit.CLOTH_CARPET);
        // Trophies on the east wall and by the door: racks of weapons, a skull on a pike, shields, banners.
        s.set(18, f, 7, grindstone("west")).set(18, f, 9, grindstone("west"));
        s.set(18, f, 8, Kit.fence("spruce")).set(18, f + 1, 8, SKULL);
        s.set(18, f + 2, 7, shield("west")).set(18, f + 2, 9, shield("west"));
        s.set(18, f + 3, 8, Kit.wallBanner("red", "west"));
        s.set(5, f, 10, grindstone("east"));
        s.set(10, f + 3, 10, Kit.wallBanner("red", "north")).set(12, f + 3, 10, Kit.wallBanner("red", "north"));
        // Light.
        Furnish.greatChandelier(s, 11, 6, 6, 1);
        chandelier(s, 8, 4, 5, 6);
        chandelier(s, 14, 4, 5, 6);
        chandelier(s, 8, 9, 5, 6);
        chandelier(s, 14, 9, 5, 6);
        chandelier(s, 5, 9, 5, 6);
    }

    /**
     * The upper floor of rank S, its people standing at layer 8 under the open roof: partitions of timber, the room of a
     * guild master at either end and the map room in the middle with its shelves, the map of the lands, its tables and the
     * lectern.
     */
    private static void guildSUpper(Structure s, int[] under) {
        int f = S_UPPER + 1;
        for (int x : new int[]{8, 14}) {
            for (int z = S_Z1 + 1; z <= S_Z2 - 1; z++) {
                s.fill(x, f, z, x, under[z] - 1, z, Kit.PLASTER);
            }
            s.fill(x, f, 4, x, under[4] - 1, 4, Kit.log(OAK_LOG, "y")).fill(x, f, 8, x, under[8] - 1, 8, Kit.log(OAK_LOG, "y"));
            s.door(x, f, 6, DOOR, x == 8 ? "east" : "west", "left");
            s.set(x, f + 2, 6, Kit.log(OAK_LOG, "z"));
        }
        // The rooms of the two masters.
        for (int side : new int[]{-1, 1}) {
            int bx = side < 0 ? 5 : 17;
            Furnish.canopyBed(s, bx, f, 4, "north", "spruce");
            Furnish.rug(s, bx, 6, bx + side * -1, 8, f, Kit.CLOTH_CARPET);
            int dx = side < 0 ? 7 : 15;
            Furnish.desk(s, dx, f, 10, "north", "spruce");
            Furnish.crate(s, side < 0 ? 4 : 18, f, 6, "spruce");
            s.set(side < 0 ? 4 : 18, f + 1, 6, Furnish.candles(2));
            s.set(side < 0 ? 4 : 18, f + 1, 8, Kit.wallBanner("black", side < 0 ? "east" : "west"));
            chandelier(s, bx, 6, f + 4, under[6] - 1);
        }
        // The map room.
        Furnish.books(s, 9, 2, 13, 2, f, f + 1);
        paintedMap(s, 10, f + 4, 2, new String[]{"lcg", "gyl", "bgB"});
        s.set(11, f, 5, CARTOGRAPHY).set(11, f + 1, 5, Kit.lantern());
        mapTable(s, 10, 7, 12, 7, f);
        Furnish.chair(s, 10, f, 8, "north", "spruce");
        Furnish.chair(s, 12, f, 8, "north", "spruce");
        s.set(13, f, 4, lectern("west"));
        s.set(9, f, 4, BOOKSHELF).set(9, f + 1, 4, Furnish.candles(3));
        chandelier(s, 11, 6, f + 4, under[6] - 1);
        s.set(9, f + 2, 10, Kit.wallTorch("east")).set(13, f + 2, 10, Kit.wallTorch("west"));
    }

    /**
     * The forecourt of rank S between the towers: a low wall of stone with a coping of trim and piers, a gateway of an arch
     * with a carved keystone and lanterns, a path of trim to the porch, flag poles with the banners of the guild, a training
     * dummy and an archery butt on one side, benches on the other, beds of flowers along the wall and lamps.
     */
    private static void guildSForecourt(Structure s) {
        int front = 24;
        // The walls.
        s.fill(1, 1, front, 21, 1, front, Kit.ROUGH).fill(1, 2, front, 21, 2, front, Kit.WALL);
        s.fill(1, 3, front, 21, 3, front, Kit.slab(Kit.TRIM_SLAB));
        for (int x : new int[]{1, 21}) {
            s.fill(x, 1, 16, x, 1, front, Kit.ROUGH).fill(x, 2, 16, x, 2, front, Kit.WALL);
            s.fill(x, 3, 16, x, 3, front, Kit.slab(Kit.TRIM_SLAB));
        }
        for (int[] p : new int[][]{{1, 20}, {21, 20}, {1, front}, {5, front}, {17, front}, {21, front}}) {
            s.fill(p[0], 1, p[1], p[0], 3, p[1], Kit.ROUGH).set(p[0], 4, p[1], Kit.slab(Kit.TRIM_SLAB));
        }
        // The gateway.
        s.fill(10, 1, front, 12, 3, front, AIR);
        for (int x : new int[]{9, 13}) {
            s.fill(x, 1, front, x, 4, front, Kit.ROUGH).set(x, 5, front, Kit.TRIM).set(x, 6, front, Kit.lantern());
        }
        s.set(10, 4, front, Kit.upside(Kit.WALL_STAIRS, "west")).set(12, 4, front, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.fill(10, 5, front, 12, 5, front, Kit.WALL).set(11, 5, front, Kit.CARVED);
        s.fill(10, 6, front, 12, 6, front, Kit.slab(Kit.TRIM_SLAB));
        s.set(11, 4, front - 1, Kit.wallBanner("black", "north"));
        // The path, the beds of flowers along the front wall.
        s.fill(10, 0, 15, 12, 0, front, Kit.TRIM);
        for (int x = 2; x <= 20; x++) {
            if (x < 9 || x > 13) {
                s.set(x, 0, front - 1, GRASS);
                s.set(x, 1, front - 1, x % 3 == 0 ? "minecraft:poppy" : x % 3 == 1 ? "minecraft:cornflower" : "minecraft:oxeye_daisy");
            }
        }
        // Flag poles with the banners of the guild, lamps by the porch.
        flagPole(s, 8, 1, 19, 6, "black", 0);
        flagPole(s, 14, 1, 19, 6, "black", 0);
        lampPost(s, 8, 1, 15, 3);
        lampPost(s, 14, 1, 15, 3);
        // The training corner: a dummy, an archery butt and a rack of weapons on coarse ground.
        s.fill(2, 0, 18, 6, 0, 22, COARSE);
        dummy(s, 4, 1, 20, "east");
        s.set(2, 1, 21, HAY).set(2, 2, 21, TARGET).set(2, 1, 22, HAY);
        s.set(6, 1, 22, grindstone("north"));
        // Benches and a lamp on the other side.
        Furnish.bench(s, 17, 19, 19, 19, 1, "south", "spruce");
        Furnish.bench(s, 17, 22, 19, 22, 1, "north", "spruce");
        lampPost(s, 20, 1, 21, 3);
        Furnish.plant(s, 16, 1, 21, "oak");
    }
    // ---------------------------------------------------------------- rank SS

    /** The great hall of rank SS at the back: its walls, the course of trim on top of them and the eaves of its roof. */
    private static final int SS_X1 = 3;
    private static final int SS_X2 = 27;
    private static final int SS_Z1 = 2;
    private static final int SS_Z2 = 12;
    private static final int SS_WALL_TOP = 14;
    private static final int SS_EAVES = 15;
    /** The middle of the front, the axis from the gate to the hearth. */
    private static final int SS_MID = 15;
    /** The wings on either side of the courtyard: their outer and court walls, their front, upper floor and eaves. */
    private static final int SS_WING_FRONT = 23;
    private static final int SS_UPPER = 7;
    private static final int SS_WING_EAVES = 11;
    /** The round towers at the front of the wings, their floors, and the front wall with the gate between them. */
    private static final int SS_TW = 6;
    private static final int SS_TE = 24;
    private static final int SS_TZ = 26;
    private static final int SS_TOWER_ROOM = 13;
    private static final int SS_TOWER_TOP = 18;
    private static final int SS_FRONT = 28;

    /**
     * The guild palace of rank SS, 31 by 33, one of the great buildings of a strong capital: three ranges of stone round a
     * courtyard. At the back stands the great hall, its walls of stone fourteen high with lancets of stained glass between
     * buttresses, its high and steep roof of tiles crowned by a ridge turret with a bell under a slate spire, rose windows
     * in its gables and a great gable of stone over its porch. Two wings run forward on either side, their ground floors
     * open to the court in arcades, their upper storeys of timber frame. At their front ends rise two round towers with
     * slate spires and the black banners of the sword of the guild, and between them the front wall closes the court with a
     * gate under a gablet. In the court a fountain plays among flag poles, lamps and benches.
     *
     * <p>Inside, the great hall rises to its open roof of tie beams and king posts: the great hearth with its hood and its
     * trophies in the middle of the back wall, shields, skulls and racks of weapons beside it, the counter of the registrar
     * with the guild desk and his post, two long tables, the high table of the masters on a dais at the east end and the
     * contract board on the west end wall, great chandeliers hanging from the beams. The arcade of the west wing is the
     * trophy gallery and training hall, that of the east wing the archery range; winding stairs in the towers lead up to the
     * rooms of two guild masters in the west wing and to the map room in the east wing.
     */
    private static Drawing guildSS() {
        Structure s = new Structure(31, 40, 33);
        guildSSGround(s);
        guildSSWing(s, 3, 9, -1);
        guildSSWing(s, 21, 27, 1);
        guildSSHallWalls(s);
        guildSSHallRoof(s);
        roundGuildTower(s, SS_TW, SS_TZ, SS_UPPER, SS_TOWER_ROOM, SS_TOWER_TOP, true);
        roundGuildTower(s, SS_TE, SS_TZ, SS_UPPER, SS_TOWER_ROOM, SS_TOWER_TOP, true);
        for (int x : new int[]{SS_TW, SS_TE}) {
            s.fill(x - 2, 1, SS_WING_FRONT - 1, x + 2, 1, SS_WING_FRONT - 1, Kit.TRIM);
            s.set(x - 3, 1, SS_WING_FRONT, Kit.ROUGH).set(x + 3, 1, SS_WING_FRONT, Kit.ROUGH);
        }
        guildSSPorch(s);
        guildSSFront(s);
        guildSSCourt(s);
        guildSSHall(s);
        guildSSWings(s);
        footings(s);
        s.connect();
        s.trimSky(3);
        return new Drawing(s, new int[]{SS_MID, 2, SS_Z2 - 1}, new int[]{21, 2, 6});
    }

    /**
     * The ground of rank SS: paving of rough stone, strips of grass with flowers and bushes along the outer sides of the
     * wings and the back of the hall.
     */
    private static void guildSSGround(Structure s) {
        s.fill(0, 0, 0, 30, 0, 32, Kit.ROUGH);
        for (int x : new int[]{0, 1, 29, 30}) {
            s.fill(x, 0, 2, x, 0, 23, GRASS);
        }
        s.fill(0, 0, 0, 30, 0, 0, GRASS);
        for (int z = 3; z <= 22; z += 3) {
            for (int x : new int[]{0, 30}) {
                s.set(x, 1, z, Kit.log(OAK_LOG, "y")).set(x, 2, z, Kit.leaves("oak"));
            }
            for (int x : new int[]{1, 29}) {
                s.set(x, 1, z + 1, z % 2 == 0 ? "minecraft:poppy" : "minecraft:cornflower");
            }
        }
    }

    /**
     * A wing of rank SS from the hall to its tower, between {@code x1} and {@code x2}: a ground storey of stone open to the
     * court in an arcade of three arches on piers, a string course of trim, an upper storey of timber frame with windows
     * and shutters and a roof of tiles whose gable hides behind the tower.
     *
     * @param side -1 for the west wing, whose court wall is at x2; 1 for the east one, whose court wall is at x1
     */
    private static void guildSSWing(Structure s, int x1, int x2, int side) {
        int court = side < 0 ? x2 : x1;
        int outer = side < 0 ? x1 : x2;
        int z1 = SS_Z2;
        int z2 = SS_WING_FRONT;
        s.fill(x1 + 1, 1, z1 + 1, x2 - 1, 1, z2 - 1, Kit.TRIM);
        stoneStorey(s, x1, z1, x2, z2, 1, SS_UPPER - 1);
        s.walls(x1, SS_UPPER, z1, x2, SS_UPPER, z2, Kit.TRIM);
        s.fill(x1 + 1, SS_UPPER, z1 + 1, x2 - 1, SS_UPPER, z2 - 1, PLANKS);
        timberStorey(s, x1, z1, x2, z2, SS_UPPER + 1, SS_WING_EAVES, new int[]{x1 + 3}, new int[]{15, 18, 21});
        // The arcade: three arches on piers to the court, a step before each.
        for (int[] a : new int[][]{{14, 15}, {17, 18}, {20, 21}}) {
            s.fill(court, 2, a[0], court, 4, a[1], AIR);
            s.fill(court, 1, a[0], court, 1, a[1], Kit.TRIM);
            s.set(court, 5, a[0], Kit.upside(Kit.WALL_STAIRS, "north")).set(court, 5, a[1], Kit.upside(Kit.WALL_STAIRS, "south"));
            s.fill(court - side, 1, a[0], court - side, 1, a[1], Kit.stairs(Kit.TRIM_STAIRS, side < 0 ? "west" : "east"));
        }
        for (int z : new int[]{16, 19}) {
            s.set(court, 1, z, Kit.ROUGH).set(court, 5, z, Kit.TRIM);
        }
        // Windows of the outer stone wall and of the timber storey.
        for (int z : new int[]{15, 18, 21}) {
            stoneWindow(s, outer, z - 1, outer, z, 3, 4, side < 0 ? "west" : "east", Kit.GLASS);
        }
        int y = SS_UPPER + 2;
        for (int[] w : new int[][]{{13, 14}, {16, 17}, {19, 20}}) {
            s.fill(outer, y, w[0], outer, y + 1, w[1], Kit.GLASS);
            s.fill(court, y, w[0], court, y + 1, w[1], Kit.GLASS);
            shutters(s, outer, w[0], outer, w[1], y, y + 1, side < 0 ? "west" : "east");
            shutters(s, court, w[0], court, w[1], y, y + 1, side < 0 ? "east" : "west");
        }
        s.set(court, y, 22, Kit.GLASS).set(court, y + 1, 22, Kit.GLASS);
        // The roof.
        int[] under = roofZ(s, x1 - 1, x2 + 1, z1 + 1, z2 + 1, SS_WING_EAVES, 0, Kit.TILE);
        gableAtZ(s, x1 - 1, under, z2, SS_WING_EAVES + 1, Kit.PLASTER);
    }

    /**
     * The walls of the great hall of rank SS: stone on a course of rough stone with quoins, a string course and a course of
     * trim at the top, lancets of stained glass between buttresses on the back wall and on the court, lancets in the end
     * walls, the breast of the great chimney on the back wall and its stack of brick.
     */
    private static void guildSSHallWalls(Structure s) {
        s.fill(SS_X1 + 1, 1, SS_Z1 + 1, SS_X2 - 1, 1, SS_Z2 - 1, PLANKS);
        stoneStorey(s, SS_X1, SS_Z1, SS_X2, SS_Z2, 1, SS_WALL_TOP);
        s.walls(SS_X1, 8, SS_Z1, SS_X2, 8, SS_Z2, Kit.TRIM);
        s.walls(SS_X1, SS_WALL_TOP, SS_Z1, SS_X2, SS_WALL_TOP, SS_Z2, Kit.TRIM);
        String[] glass = {pane("light_blue"), pane("yellow"), pane("red")};
        for (int x : new int[]{6, 10, 20, 24}) {
            lancet(s, x, SS_Z1, 6, 11, true, glass);
        }
        for (int x : new int[]{4, 8, 22, 26}) {
            s.fill(x, 1, SS_Z1 - 1, x, 11, SS_Z1 - 1, Kit.ROUGH);
            s.set(x, 12, SS_Z1 - 1, Kit.stairs(Kit.ROUGH_STAIRS, "south"));
            s.fill(x, 1, SS_Z1 - 2, x, 5, SS_Z1 - 2, Kit.ROUGH);
            s.set(x, 6, SS_Z1 - 2, Kit.stairs(Kit.ROUGH_STAIRS, "south"));
            s.set(x, 8, SS_Z1 - 2, Kit.wallTorch("north"));
        }
        for (int x : new int[]{10, 20}) {
            s.fill(x, 7, SS_Z2, x, 12, SS_Z2, pane("light_blue"));
            s.set(x, 13, SS_Z2, Kit.CARVED);
        }
        for (int x : new int[]{SS_X1, SS_X2}) {
            lancet(s, x, 7, 9, 12, false, glass);
        }
        // The breast of the chimney and its stack.
        s.fill(12, 1, SS_Z1 - 1, 18, SS_WALL_TOP, SS_Z1 - 1, Kit.ROUGH);
        s.fill(13, 1, SS_Z1 - 2, 17, 10, SS_Z1 - 2, Kit.ROUGH);
        s.fill(13, 11, SS_Z1 - 2, 17, 11, SS_Z1 - 2, Kit.stairs(Kit.ROUGH_STAIRS, "south"));
        s.fill(14, SS_WALL_TOP + 1, SS_Z1 - 1, 16, 17, SS_Z1 - 1, Kit.ROUGH);
        s.fill(14, 18, SS_Z1 - 1, 16, 29, SS_Z1 - 1, Kit.CHIMNEY);
        s.set(15, 30, SS_Z1 - 1, Kit.CHIMNEY_POT).set(14, 30, SS_Z1 - 1, Kit.slab(Kit.CHIMNEY_SLAB)).set(16, 30, SS_Z1 - 1, Kit.slab(Kit.CHIMNEY_SLAB));
        // The doors into the wings.
        s.door(6, 2, SS_Z2, DOOR, "north", "left");
        s.door(24, 2, SS_Z2, DOOR, "north", "left");
    }

    /**
     * The high roof of the great hall of rank SS: steep tiles with gables of stone and rose windows at both ends, the great
     * gable of stone over the porch with a rose window, and the ridge turret with its bell and slate spire.
     */
    private static void guildSSHallRoof(Structure s) {
        int[] under = roofX(s, SS_X1 - 1, SS_X2 + 1, SS_Z1 - 1, SS_Z2 + 1, SS_EAVES, 6, Kit.TILE);
        for (int x : new int[]{SS_X1, SS_X2}) {
            gableAtX(s, SS_Z1 - 1, under, x, SS_EAVES, Kit.WALL);
            rose(s, x, 20, 7, false);
            s.set(x, 23, 7, Kit.CARVED);
        }
        int[] cross = crossGable(s, 9, 21, 8, SS_Z2 + 1, SS_EAVES, 2, Kit.TILE, 8);
        gableAtZ(s, 9, cross, SS_Z2, SS_EAVES, Kit.WALL);
        rose(s, SS_MID, 19, SS_Z2, true);
        s.set(SS_MID, 22, SS_Z2, Kit.CARVED);
        // The ridge turret.
        int z = 7;
        s.fill(SS_MID - 1, 26, z - 1, SS_MID + 1, 27, z + 1, Kit.TRIM);
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            s.fill(SS_MID + c[0], 28, z + c[1], SS_MID + c[0], 29, z + c[1], Kit.log(OAK_LOG, "y"));
        }
        for (int[] c : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            s.set(SS_MID + c[0], 28, z + c[1], Kit.fence("oak"));
        }
        int top = needle(s, SS_MID - 2, z - 2, SS_MID + 2, z + 2, 30, 3, Kit.SLATE, PLANKS);
        s.set(SS_MID, top, z, ROD);
        s.set(SS_MID, 29, z, BELL);
    }

    /**
     * The porch of the great hall of rank SS: piers of stone with arches in their sides, a stepped round arch in front with a
     * carved keystone, a gable of plaster and oak under a roof of tiles with the black banner of the guild, steps of trim;
     * behind it the door between lights under an arched fanlight.
     */
    private static void guildSSPorch(Structure s) {
        int z = SS_Z2;
        s.fill(13, 2, z, 13, 5, z, Kit.ROUGH).fill(17, 2, z, 17, 5, z, Kit.ROUGH);
        s.door(SS_MID, 2, z, DOOR, "north", "left");
        s.fill(14, 2, z, 14, 3, z, Kit.GLASS).fill(16, 2, z, 16, 3, z, Kit.GLASS);
        s.fill(14, 4, z, 16, 4, z, Kit.GLASS);
        s.set(14, 5, z, Kit.upside(Kit.WALL_STAIRS, "west")).set(16, 5, z, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(SS_MID, 5, z, Kit.GLASS).set(SS_MID, 6, z, Kit.CARVED);
        int front = z + 3;
        s.fill(13, 1, z + 1, 17, 1, front, Kit.TRIM);
        for (int x : new int[]{12, 18}) {
            s.fill(x, 1, z + 1, x, 8, front, Kit.WALL);
            s.fill(x, 1, front, x, 8, front, Kit.ROUGH);
            s.fill(x, 2, z + 1, x, 4, z + 2, AIR);
            s.set(x, 5, z + 1, Kit.upside(Kit.WALL_STAIRS, "north")).set(x, 5, z + 2, Kit.upside(Kit.WALL_STAIRS, "south"));
            s.set(x, 1, front + 1, Kit.ROUGH).set(x, 2, front + 1, Kit.lantern());
        }
        s.fill(13, 6, front, 17, 8, front, Kit.WALL);
        s.set(13, 6, front, Kit.upside(Kit.WALL_STAIRS, "west")).set(17, 6, front, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.fill(14, 6, front, 16, 6, front, AIR);
        s.set(14, 7, front, Kit.upside(Kit.WALL_STAIRS, "west")).set(16, 7, front, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(SS_MID, 7, front, AIR).set(SS_MID, 8, front, Kit.CARVED);
        s.fill(12, 9, z + 1, 18, 9, front, Kit.TRIM);
        int[] under = roofZ(s, 11, 19, z + 1, front + 1, 9, 0, Kit.TILE);
        gableAtZ(s, 11, under, front, 10, Kit.PLASTER);
        s.fill(SS_MID, 10, front, SS_MID, under[4] - 1, front, Kit.log(OAK_LOG, "y"));
        s.set(SS_MID, 11, front + 1, Kit.wallBanner("black", "south"));
        s.fill(13, 1, front + 1, 17, 1, front + 1, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        chandelier(s, SS_MID, z + 2, 7, 8);
    }

    /**
     * The front of rank SS between the towers: a wall of stone with a coping of trim and piers, the gate in its middle
     * under a round arch with a carved keystone and a stepped gablet carrying the banner of the guild; before it an apron
     * with a path of trim, flag poles with the banners of the guild and lamps.
     */
    private static void guildSSFront(Structure s) {
        int z = SS_FRONT;
        s.fill(9, 1, z, 21, 1, z, Kit.ROUGH).fill(9, 2, z, 21, 3, z, Kit.WALL);
        s.fill(9, 4, z, 21, 4, z, Kit.slab(Kit.TRIM_SLAB));
        for (int x : new int[]{9, 11, 19, 21}) {
            s.fill(x, 1, z, x, 4, z, Kit.ROUGH).set(x, 5, z, Kit.slab(Kit.TRIM_SLAB));
        }
        for (int x : new int[]{9, 21}) {
            s.fill(x, 1, SS_WING_FRONT + 1, x, 6, SS_WING_FRONT + 1, Kit.WALL);
        }
        // The gate.
        s.fill(14, 1, z, 16, 4, z, AIR);
        for (int x : new int[]{13, 17}) {
            s.fill(x, 1, z, x, 7, z, Kit.ROUGH).set(x, 8, z, Kit.lantern());
        }
        s.set(14, 5, z, Kit.upside(Kit.WALL_STAIRS, "west")).set(16, 5, z, Kit.upside(Kit.WALL_STAIRS, "east")).set(SS_MID, 5, z, AIR);
        s.set(14, 6, z, Kit.WALL).set(16, 6, z, Kit.WALL).set(SS_MID, 6, z, Kit.CARVED);
        s.fill(14, 7, z, 16, 7, z, Kit.TRIM);
        s.set(14, 8, z, Kit.stairs(Kit.WALL_STAIRS, "east")).set(16, 8, z, Kit.stairs(Kit.WALL_STAIRS, "west")).set(SS_MID, 8, z, Kit.WALL);
        s.set(SS_MID, 9, z, Kit.TRIM).set(SS_MID, 10, z, ROD);
        s.set(SS_MID, 8, z + 1, Kit.wallBanner("black", "south"));
        s.set(SS_MID, 4, z - 1, Kit.hangingLantern());
        s.set(SS_MID, 5, z - 1, Kit.TRIM);
        // The apron.
        s.fill(14, 0, z, 16, 0, 32, Kit.TRIM);
        flagPole(s, 11, 1, 31, 7, "black", 0);
        flagPole(s, 19, 1, 31, 7, "black", 0);
        lampPost(s, 12, 1, 30, 3);
        lampPost(s, 18, 1, 30, 3);
        for (int x = 0; x <= 30; x++) {
            if (x < 10 || x > 20) {
                s.set(x, 0, 32, GRASS);
                if (x % 2 == 0) {
                    s.set(x, 1, 32, x % 4 == 0 ? "minecraft:poppy" : "minecraft:oxeye_daisy");
                }
            }
        }
    }

    /**
     * The courtyard of rank SS: a fountain in the middle with a pillar and a lantern in a round basin, paths of trim from the
     * gate round the fountain to the porch, flag poles with the banners of the guild, lamps and stone benches facing the
     * fountain.
     */
    private static void guildSSCourt(Structure s) {
        int cz = 20;
        band(s, SS_MID, 0, cz, 9, 0, Kit.TRIM);
        s.fill(14, 0, SS_Z2 + 5, 16, 0, SS_FRONT, Kit.TRIM);
        band(s, SS_MID, 0, cz, 5, 0, Kit.ROUGH);
        Kit.tube(s, SS_MID, 1, 1, cz, 5, Kit.TRIM);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                s.set(SS_MID + dx, 1, cz + dz, "minecraft:water[level=0]");
            }
        }
        s.fill(SS_MID, 1, cz, SS_MID, 2, cz, Kit.WALL).set(SS_MID, 3, cz, Kit.CARVED).set(SS_MID, 4, cz, Kit.lantern());
        flagPole(s, 11, 1, 14, 7, "black", 0);
        flagPole(s, 19, 1, 14, 7, "black", 0);
        flagPole(s, 11, 1, 26, 6, "red", 0);
        flagPole(s, 19, 1, 26, 6, "red", 0);
        lampPost(s, 11, 1, 20, 3);
        lampPost(s, 19, 1, 20, 3);
        s.fill(12, 1, 17, 12, 1, 18, Kit.stairs(Kit.TRIM_STAIRS, "west")).fill(18, 1, 17, 18, 1, 18, Kit.stairs(Kit.TRIM_STAIRS, "east"));
        s.fill(12, 1, 22, 12, 1, 23, Kit.stairs(Kit.TRIM_STAIRS, "west")).fill(18, 1, 22, 18, 1, 23, Kit.stairs(Kit.TRIM_STAIRS, "east"));
    }

    /**
     * The great hall of rank SS, its people standing at layer 2 under the open roof: the great hearth with its hood, the
     * trophies, the counter of the registrar, the contract board, two long tables, the high table on its dais, tie beams
     * and king posts carrying the great chandeliers.
     */
    private static void guildSSHall(Structure s) {
        int f = 2;
        hearth(s, SS_MID, f, SS_Z1, 3, true);
        s.fill(13, 7, SS_Z1 + 1, 17, 7, SS_Z1 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.fill(14, 8, SS_Z1 + 1, 16, SS_WALL_TOP - 1, SS_Z1 + 1, Kit.ROUGH);
        s.set(SS_MID, 10, SS_Z1 + 2, Kit.wallBanner("black", "south"));
        s.set(SS_MID, 12, SS_Z1 + 2, shield("south"));
        // Trophies on the back wall west of the hearth: racks of weapons, skulls on pikes and brackets, shields.
        for (int x = 5; x <= 10; x++) {
            if (x % 2 == 1) {
                s.set(x, f, SS_Z1 + 1, grindstone("south"));
                s.set(x, f + 3, SS_Z1 + 1, shield("south"));
            } else {
                s.set(x, f, SS_Z1 + 1, Kit.fence("spruce")).set(x, f + 1, SS_Z1 + 1, SKULL);
                s.set(x, f + 3, SS_Z1 + 1, Kit.upside(Kit.TRIM_STAIRS, "north")).set(x, f + 4, SS_Z1 + 1, SKULL);
            }
        }
        // The registrar: shelves of ledgers, the post and its chest behind the counter with the guild desk and a gate.
        Furnish.books(s, 19, SS_Z1 + 1, 19, SS_Z1 + 1, f, f + 2);
        Furnish.books(s, 22, SS_Z1 + 1, 23, SS_Z1 + 1, f, f + 2);
        s.set(21, f, SS_Z1 + 1, WORK_POST).set(20, f, SS_Z1 + 1, Kit.chest("east"));
        s.set(19, f, 5, PLANKS).set(19, f + 1, 5, Kit.lantern());
        s.set(20, f, 5, gate("south")).set(21, f, 5, DESK);
        s.set(22, f, 5, PLANKS).set(22, f + 1, 5, Furnish.candles(3));
        s.set(23, f, 5, PLANKS).set(23, f + 1, 5, Furnish.candles(2));
        // The contract board on the west end wall.
        contractBoard(s, 4, 4, 4, 10, f, 7, "east");
        // The long tables and the red carpet from the porch to the hearth.
        feastTable(s, 7, 8, 12, 8, f);
        feastTable(s, 18, 8, 23, 8, f);
        s.fill(SS_MID, f, 4, SS_MID, f, SS_Z2 - 1, Kit.CLOTH_CARPET);
        // The high table of the masters on its dais at the east end, banners behind it.
        s.fill(25, f, 4, 26, f, 10, Kit.slab(Kit.TRIM_SLAB));
        s.fill(24, f, 4, 24, f, 10, Kit.stairs(Kit.TRIM_STAIRS, "east"));
        Furnish.longTable(s, 25, 6, 25, 9, f + 1, "spruce");
        for (int z = 6; z <= 9; z++) {
            Furnish.chair(s, 26, f + 1, z, "west", "spruce");
        }
        s.set(25, f + 2, 6, Furnish.candles(3)).set(25, f + 2, 9, Furnish.candles(3)).set(25, f + 2, 7, Kit.lantern());
        s.set(26, f + 4, 6, Kit.wallBanner("black", "west")).set(26, f + 4, 9, Kit.wallBanner("black", "west"));
        s.set(26, f + 5, 7, shield("west")).set(26, f + 5, 8, shield("west"));
        // The roof trusses and the chandeliers.
        for (int x : new int[]{8, 22}) {
            s.fill(x, SS_WALL_TOP, SS_Z1 + 1, x, SS_WALL_TOP, SS_Z2 - 1, Kit.log(SPRUCE_LOG, "z"));
            s.fill(x, SS_WALL_TOP + 1, 7, x, 25, 7, Kit.log(SPRUCE_LOG, "y"));
            Furnish.greatChandelier(s, x, 7, SS_WALL_TOP - 1, 3);
        }
        Furnish.greatChandelier(s, SS_MID, 7, 25, 14);
        chandelier(s, 6, 4, 9, 19);
        chandelier(s, 6, 10, 9, 19);
        chandelier(s, 25, 5, 9, 21);
        chandelier(s, 25, 10, 9, 19);
        for (int x : new int[]{20, 24}) {
            s.set(x, 5, SS_Z1 + 1, Kit.wallTorch("south"));
        }
    }

    /**
     * The insides of the wings of rank SS: in the arcade of the west wing the trophy gallery with racks of weapons, shields,
     * skulls and training dummies, in the arcade of the east wing the archery range; upstairs in the west wing the rooms of
     * two guild masters with their beds of state and desks, in the east wing the map room with its shelves, the map of the
     * lands, its tables and the lectern.
     */
    private static void guildSSWings(Structure s) {
        int f = 2;
        // The trophy gallery in the west.
        for (int z = 13; z <= 21; z++) {
            switch (z % 3) {
                case 1 -> s.set(4, f, z, grindstone("east"));
                case 2 -> s.set(4, f, z, Kit.fence("spruce")).set(4, f + 1, z, SKULL);
                default -> s.set(4, f + 3, z, shield("east"));
            }
        }
        dummy(s, 6, f, 15, "east");
        dummy(s, 6, f, 19, "east");
        s.set(8, f, 22, grindstone("north"));
        for (int z : new int[]{14, 20}) {
            chandelier(s, 6, z, 5, 6);
        }
        // The archery range in the east.
        for (int x : new int[]{22, 26}) {
            s.set(x, f, 13, HAY).set(x, f + 1, 13, TARGET);
        }
        s.fill(22, f, 19, 26, f, 19, Kit.fence("spruce"));
        s.set(24, f, 19, gate("south"));
        s.set(26, f, 21, grindstone("west")).set(26, f, 22, grindstone("west"));
        s.set(26, f + 2, 16, shield("west"));
        Furnish.bench(s, 22, 22, 23, 22, f, "north", "spruce");
        for (int z : new int[]{15, 21}) {
            chandelier(s, 24, z, 5, 6);
        }
        // The rooms of the masters over the trophy gallery.
        int u = SS_UPPER + 1;
        for (int x = 4; x <= 8; x++) {
            s.fill(x, u, 18, x, wingUnder(x - 2) - 1, 18, Kit.PLASTER);
        }
        s.fill(6, u, 18, 6, wingUnder(4) - 1, 18, Kit.log(OAK_LOG, "y"));
        s.door(7, u, 18, DOOR, "north", "left");
        s.set(7, u + 2, 18, Kit.log(OAK_LOG, "x"));
        Furnish.canopyBed(s, 5, u, 15, "north", "spruce");
        Furnish.desk(s, 8, u, 14, "west", "spruce");
        Furnish.rug(s, 6, 16, 7, 17, u, Kit.CLOTH_CARPET);
        Furnish.crate(s, 4, u, 17, "spruce");
        s.set(4, u + 1, 17, Furnish.candles(2));
        Furnish.canopyBed(s, 5, u, 21, "north", "spruce");
        Furnish.desk(s, 8, u, 22, "north", "spruce");
        Furnish.crate(s, 8, u, 19, "spruce");
        Furnish.rug(s, 7, 20, 7, 21, u, Kit.CLOTH_CARPET);
        chandelier(s, 6, 16, u + 3, wingUnder(4) - 1);
        chandelier(s, 7, 20, u + 3, wingUnder(5) - 1);
        // The map room over the archery range.
        Furnish.books(s, 22, 13, 26, 13, u, u + 1);
        paintedMap(s, 23, u + 3, 13, new String[]{"lcg", "bgy"});
        Furnish.books(s, 26, 14, 26, 15, u, u + 1);
        s.set(24, u, 16, CARTOGRAPHY).set(24, u + 1, 16, Kit.lantern());
        mapTable(s, 23, 18, 25, 18, u);
        for (int x = 23; x <= 25; x += 2) {
            Furnish.chair(s, x, u, 19, "north", "spruce");
        }
        s.set(22, u, 15, lectern("east"));
        Furnish.chair(s, 23, u, 16, "east", "spruce");
        chandelier(s, 24, 15, u + 3, wingUnder(4) - 1);
        chandelier(s, 24, 20, u + 3, wingUnder(4) - 1);
    }

    /**
     * The lowest layer of the roof of a wing of rank SS over its column {@code i} counted from its west eave.
     */
    private static int wingUnder(int i) {
        int row = Math.min(i, 8 - i);
        return SS_WING_EAVES + row;
    }
    // ---------------------------------------------------------------- the mages' guild

    /**
     * The tower of the mages' guild of the given rank.
     */
    static Drawing mageTower(Rank rank) {
        return switch (rank) {
            case B -> mageB();
            case A -> mageA();
            case S -> mageS();
            case SS -> mageSS();
        };
    }

    /**
     * A piece of furniture put on a floor only where its cell is free and the floor under it bears it, so that it never
     * takes the place of a step, a rail or another piece.
     */
    private static void put(Structure s, int x, int y, int z, String state) {
        if (s.get(x, y, z).equals(AIR) && States.isFull(s.get(x, y - 1, z))) {
            s.set(x, y, z, state);
        }
    }

    /**
     * The round shaft of a mage tower {@code d} across from the ground to {@code top}: its inside cleared, two courses of
     * rough stone, the wall of stone with a band of trim at every floor above the first, and the floors of the given blocks.
     */
    private static void mageShaft(Structure s, int cx, int cz, int d, int top, int[] floors, String[] floorBlocks) {
        for (int y = floors[0]; y <= top; y++) {
            roundFloor(s, cx, y, cz, d, AIR);
        }
        Kit.tube(s, cx, 2, 3, cz, d, Kit.ROUGH);
        Kit.tube(s, cx, 4, top, cz, d, Kit.WALL);
        for (int i = 1; i < floors.length; i++) {
            Kit.tube(s, cx, floors[i], floors[i], cz, d, Kit.TRIM);
        }
        for (int i = 0; i < floors.length; i++) {
            roundFloor(s, cx, floors[i], cz, d, floorBlocks[i]);
        }
    }

    /**
     * The winding stair of a round tower from each floor to the next, one flight after another along the inside of its
     * wall from the cell {@code (startDx, startDz)} on, and the rails round its openings in the floors.
     */
    private static void windingStair(Structure s, int cx, int cz, int d, int[] floors, int startDx, int startDz) {
        List<int[]> ring = innerRing(d);
        int start = indexOf(ring, startDx, startDz);
        List<int[]> holes = new ArrayList<>();
        for (int i = 0; i + 1 < floors.length; i++) {
            holes.addAll(flight(s, cx, cz, ring, start, floors[i], floors[i + 1], Kit.WALL_STAIRS));
            start += floors[i + 1] - floors[i];
        }
        rails(s, cx, cz, d, holes);
    }

    /**
     * The hall of the focus on the ground floor of a mage tower: a ring of calcite in the dark floor round the focus on its
     * block of amethyst, the post of the mage within reach of it with its chest beside it, and a lantern over the focus
     * hanging from the floor above.
     *
     * @param post where the post stands from the middle, the chest standing one further along x
     */
    private static void focusHall(Structure s, int cx, int cz, int floor, int ceiling, int[] post, String chestFacing) {
        for (int[] c : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
            s.set(cx + c[0], floor, cz + c[1], CALCITE);
        }
        s.set(cx, floor, cz, AMETHYST);
        s.set(cx, floor + 1, cz, FOCUS);
        s.set(cx + post[0], floor + 1, cz + post[1], WORK_POST);
        s.set(cx + post[0] + Integer.signum(post[0]), floor + 1, cz + post[1], Kit.chest(chestFacing));
        chandelier(s, cx, cz, ceiling - 1, ceiling - 1);
    }

    /**
     * The top of a mage tower that ends in a balcony: brackets on the diagonals and two rings of corbels of trim carrying a
     * balcony all round its top room with a parapet set with end rods; the top room glazed in purple and magenta, with
     * doors to the balcony west and east, under a slate spire whose eaves send out rays of end rods and whose point bears
     * amethyst and an end rod; a lantern hangs in the spire.
     *
     * @return the layer of the point of the spire
     */
    private static int balconyTop(Structure s, int cx, int cz, int d, int floor, int eaves, int[] rises) {
        int w1 = d + 2;
        int w2 = d + 4;
        int r = (w1 - 1) / 2;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Kit.inDisc(dx, dz, w1) && !Kit.inDisc(dx, dz, d) && dx != 0 && dz != 0) {
                    s.set(cx + dx, floor - 2, cz + dz, Kit.upside(Kit.TRIM_STAIRS, inward(dx, dz)));
                }
            }
        }
        band(s, cx, floor - 1, cz, w1, d, Kit.TRIM);
        corbels(s, cx, floor - 1, cz, w2, w1, Kit.TRIM_STAIRS);
        band(s, cx, floor, cz, w2, d, Kit.WALL);
        Kit.tube(s, cx, floor, floor, cz, w2, Kit.TRIM);
        Kit.tube(s, cx, floor, floor, cz, d, Kit.TRIM);
        int y = floor + 1;
        Kit.tube(s, cx, y, y, cz, w2, Kit.WALL_FENCE);
        int rr = (w2 - 1) / 2;
        for (int dz = -rr; dz <= rr; dz++) {
            for (int dx = -rr; dx <= rr; dx++) {
                if (Kit.onRim(dx, dz, w2) && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz) + 1 || Math.abs(dz) == Math.abs(dx) + 1)) {
                    s.set(cx + dx, y + 1, cz + dz, endRod("up"));
                }
            }
        }
        // The top room: glazed on four sides, doors to the balcony west and east.
        int h = (d - 1) / 2;
        Kit.tube(s, cx, y, eaves - 1, cz, d, Kit.WALL);
        Kit.tube(s, cx, eaves - 1, eaves - 1, cz, d, Kit.TRIM);
        for (int side : new int[]{-h, h}) {
            s.door(cx + side, y, cz, DOOR, side < 0 ? "east" : "west", "left");
            s.fill(cx, y, cz + side, cx, eaves - 2, cz + side, pane(PURPLE));
            for (int along : new int[]{-1, 1}) {
                s.fill(cx + side, y, cz + along, cx + side, eaves - 2, cz + along, pane(MAGENTA));
                s.fill(cx + along, y, cz + side, cx + along, eaves - 2, cz + side, pane(MAGENTA));
            }
        }
        // The spire with rays of end rods at its eaves, a lantern hanging in it, the crown on its point.
        int top = roundRoof(s, cx, eaves, cz, w1, rises, Kit.SLATE);
        s.set(cx, top, cz, AMETHYST).set(cx, top + 1, cz, endRod("up"));
        int ray = r + 1;
        s.set(cx + ray, eaves, cz, endRod("east")).set(cx - ray, eaves, cz, endRod("west"));
        s.set(cx, eaves, cz + ray, endRod("south")).set(cx, eaves, cz - ray, endRod("north"));
        chandelier(s, cx, cz, eaves - 1, hollowTop(s, cx, eaves - 1, cz));
        return top + 1;
    }

    /**
     * A small gabled window in a round slate roof, looking out on one side: a pane of purple glass between two cheeks of
     * slate under a little gable roof, the roof behind it opened so that the lantern in the spire shines through.
     *
     * @param r    how far from the middle the window stands
     * @param outX the way out along x (-1, 0 or 1)
     * @param outZ the way out along z
     */
    private static void lucarne(Structure s, int cx, int cz, int y, int r, int outX, int outZ) {
        int ax = outZ != 0 ? 1 : 0;
        int az = outX != 0 ? 1 : 0;
        int x = cx + r * outX;
        int z = cz + r * outZ;
        s.set(x, y, z, pane(PURPLE));
        s.set(x - ax, y, z - az, Kit.SLATE.block()).set(x + ax, y, z + az, Kit.SLATE.block());
        s.set(x - ax, y + 1, z - az, Kit.stairs(Kit.SLATE.stairs(), ax == 1 ? "east" : "south"));
        s.set(x + ax, y + 1, z + az, Kit.stairs(Kit.SLATE.stairs(), ax == 1 ? "west" : "north"));
        s.set(x, y + 1, z, Kit.SLATE.block()).set(x, y + 2, z, Kit.slab(Kit.SLATE.slab()));
        s.set(x - outX, y, z - outZ, AIR).set(x - 2 * outX, y, z - 2 * outZ, AIR);
    }

    /**
     * Rough stone in place of the grass under every block that stands on the ground, so that no grass lies smothered.
     */
    private static void footings(Structure s) {
        for (int z = 0; z < s.sizeZ; z++) {
            for (int x = 0; x < s.sizeX; x++) {
                if (s.get(x, 0, z).equals(GRASS) && States.isFull(s.get(x, 1, z))) {
                    s.set(x, 0, z, Kit.ROUGH);
                }
            }
        }
    }

    /**
     * A garden of grass round a mage tower with purple and blue flowers in its corners, outside the paving of the given
     * width.
     */
    private static void mageGarden(Structure s, int cx, int cz, int paving) {
        s.fill(0, 0, 0, s.sizeX - 1, 0, s.sizeZ - 1, GRASS);
        band(s, cx, 0, cz, paving, 0, Kit.ROUGH);
        int r = (paving - 1) / 2;
        for (int z = 0; z < s.sizeZ; z++) {
            for (int x = 0; x < s.sizeX; x++) {
                if (s.get(x, 0, z).equals(GRASS) && !Kit.inDisc(x - cx, z - cz, paving + 2) && (x * 7 + z * 3) % 4 != 0) {
                    s.set(x, 1, z, (x + z) % 3 == 0 ? "minecraft:azure_bluet" : "minecraft:allium");
                }
            }
        }
    }

    // ---------------------------------------------------------------- mage tower B

    /**
     * The mage tower of rank B, 11 by 11 and 27 high: a slender round tower of stone seven blocks across on a stepped base
     * of rough stone, buttresses on its diagonals with lanterns on their toes, strips of purple windows on sills of trim with
     * amethyst between the storeys, banners of the guild beside the arched door, and a steep slate spire with a ring of
     * corbels under its eaves, rays of end rods and a crown of amethyst.
     *
     * <p>A stair winds up along the inside of the wall past three storeys: the hall of the focus with the post of the mage,
     * its chest and an enchanting table between shelves; the library with the brewing stand; and the bedroom of the mage
     * with his bed and his chest under the open spire.
     */
    private static Drawing mageB() {
        int cx = 5;
        int cz = 5;
        int d = 7;
        int[] floors = {1, 6, 11};
        int eaves = 16;
        Structure s = new Structure(11, 28, 11);
        mageGarden(s, cx, cz, 9);
        s.fill(4, 0, 9, 6, 0, 10, Kit.TRIM);
        band(s, cx, 1, cz, 9, 0, Kit.ROUGH);
        rimStairs(s, cx, 2, cz, 9, Kit.ROUGH_STAIRS);
        s.fill(4, 1, 9, 6, 1, 9, Kit.ROUGH);
        s.fill(4, 1, 10, 6, 1, 10, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        mageShaft(s, cx, cz, d, eaves - 1, floors, new String[]{DEEPSLATE, PLANKS, OAK_PLANKS});
        Kit.tube(s, cx, eaves - 1, eaves - 1, cz, d, Kit.TRIM);
        // Buttresses on the diagonals, lanterns on their toes.
        for (int[] g : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            for (int[] c : new int[][]{{3, 2}, {2, 3}}) {
                s.fill(cx + g[0] * c[0], 2, cz + g[1] * c[1], cx + g[0] * c[0], 8, cz + g[1] * c[1], Kit.ROUGH);
                s.set(cx + g[0] * c[0], 9, cz + g[1] * c[1], slopeStairs(Kit.ROUGH_STAIRS, g[0] * c[0], g[1] * c[1]));
            }
            s.set(cx + 3 * g[0], 2, cz + 3 * g[1], Kit.ROUGH).set(cx + 3 * g[0], 3, cz + 3 * g[1], Kit.lantern());
        }
        // The door with its arch, banners over it.
        s.door(cx, 2, cz + 3, DOOR, "north", "left");
        s.set(cx - 1, 4, cz + 3, Kit.upside(Kit.WALL_STAIRS, "west")).set(cx + 1, 4, cz + 3, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(cx, 4, cz + 3, pane(PURPLE)).set(cx, 5, cz + 3, Kit.CARVED);
        s.set(cx - 1, 11, cz + 4, Kit.wallBanner(PURPLE, "south")).set(cx + 1, 11, cz + 4, Kit.wallBanner(PURPLE, "south"));
        // Strips of windows on four sides, amethyst between the storeys.
        for (int[] side : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            int x = cx + 3 * side[0];
            int z = cz + 3 * side[1];
            if (side[1] <= 0) {
                roundWindow(s, x, z, side[0], side[1], 3, 4, false, false, pane(PURPLE));
                s.set(x, 6, z, AMETHYST);
            }
            roundWindow(s, x, z, side[0], side[1], 8, 9, false, true, pane(PURPLE));
            roundWindow(s, x, z, side[0], side[1], 13, 14, false, true, pane(PURPLE));
            s.set(x, 11, z, AMETHYST);
        }
        windingStair(s, cx, cz, d, floors, -1, 2);
        // The hall of the focus: the post and its chest, an enchanting table between shelves, amethyst, a carpet.
        focusHall(s, cx, cz, 1, 6, new int[]{1, -1}, "west");
        s.set(cx, 2, cz - 2, ENCHANTING);
        s.set(cx - 1, 2, cz - 2, BOOKSHELF).set(cx + 1, 2, cz - 2, BOOKSHELF);
        s.set(cx - 1, 3, cz - 2, SOUL_LANTERN).set(cx + 1, 3, cz - 2, Furnish.candles(3));
        s.set(cx + 2, 2, cz + 1, AMETHYST).set(cx + 2, 3, cz + 1, CLUSTER);
        s.fill(cx, 2, cz + 1, cx, 2, cz + 2, PURPLE_CARPET);
        // The library with the brewing stand and its cauldron.
        int y = 7;
        for (int[] c : new int[][]{{2, -1}, {2, 1}, {1, 2}, {-1, 2}}) {
            s.fill(cx + c[0], y, cz + c[1], cx + c[0], y + 2, cz + c[1], BOOKSHELF);
        }
        put(s, cx + 1, y, cz, lectern("east"));
        put(s, cx, y, cz + 1, BREWING);
        put(s, cx, y, cz + 2, CAULDRON);
        put(s, cx - 1, y, cz, Kit.topSlab(SPRUCE_SLAB));
        put(s, cx - 1, y + 1, cz, Furnish.candles(2));
        chandelier(s, cx, cz, 10, 10);
        // The bedroom: the bed of the mage, his chest, a desk at the window and a shelf with a lantern.
        y = 12;
        s.bed(cx - 1, y, cz + 1, "minecraft:purple_bed", "west", -1, 0);
        put(s, cx - 2, y, cz, Kit.chest("east"));
        put(s, cx + 2, y, cz + 1, Kit.topSlab(SPRUCE_SLAB));
        put(s, cx + 2, y + 1, cz + 1, Furnish.candles(2));
        put(s, cx + 1, y, cz + 1, Kit.stairs(SPRUCE_STAIRS, "west"));
        put(s, cx + 1, y, cz + 2, BOOKSHELF);
        put(s, cx + 1, y + 1, cz + 2, SOUL_LANTERN);
        put(s, cx, y, cz, PURPLE_CARPET);
        put(s, cx, y, cz + 1, PURPLE_CARPET);
        // The spire.
        corbels(s, cx, eaves - 1, cz, 9, d, Kit.TRIM_STAIRS);
        int top = roundRoof(s, cx, eaves, cz, 9, new int[]{1, 1, 2, 2, 3}, Kit.SLATE);
        s.set(cx, top, cz, AMETHYST).set(cx, top + 1, cz, endRod("up"));
        s.set(cx + 5, eaves, cz, endRod("east")).set(cx - 5, eaves, cz, endRod("west"));
        s.set(cx, eaves, cz + 5, endRod("south")).set(cx, eaves, cz - 5, endRod("north"));
        lucarne(s, cx, cz, eaves + 2, 2, 0, 1);
        chandelier(s, cx, cz, eaves - 1, hollowTop(s, cx, eaves - 1, cz));
        s.connect();
        s.trimSky(3);
        return new Drawing(s, new int[]{cx, 2, cz + 2}, null);
    }

    // ---------------------------------------------------------------- mage tower A

    /**
     * The mage tower of rank A, 13 by 13 and 33 high: a round tower of stone nine blocks across on a stepped base, an arched
     * door under a little gabled canopy, strips of purple windows with amethyst between the storeys, and at the top a
     * balcony on corbels round a glazed room under a slate spire with lucarnes, rays of end rods and a crown of amethyst.
     *
     * <p>Inside, a stair winds past the hall of the focus with the post of the mage and its chest, the library, the brewing
     * room, the bedroom of the two mages and up to the research room under the spire, with its enchanting table, its
     * crystals of amethyst and its telescope.
     */
    private static Drawing mageA() {
        int cx = 6;
        int cz = 6;
        int d = 9;
        int[] floors = {1, 6, 10, 14, 18};
        int eaves = 21;
        Structure s = new Structure(13, 35, 13);
        mageGarden(s, cx, cz, 13);
        s.fill(5, 0, 11, 7, 0, 12, Kit.TRIM);
        rimStairs(s, cx, 1, cz, 13, Kit.ROUGH_STAIRS);
        band(s, cx, 1, cz, 11, 0, Kit.ROUGH);
        rimStairs(s, cx, 2, cz, 11, Kit.ROUGH_STAIRS);
        mageShaft(s, cx, cz, d, floors[4] - 1, floors, new String[]{DEEPSLATE, PLANKS, TILES, OAK_PLANKS, TILES});
        // The door under its canopy.
        s.door(cx, 2, cz + 4, DOOR, "north", "left");
        s.set(cx - 1, 4, cz + 4, Kit.upside(Kit.WALL_STAIRS, "west")).set(cx + 1, 4, cz + 4, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(cx, 4, cz + 4, pane(PURPLE)).set(cx, 5, cz + 4, Kit.CARVED);
        s.fill(cx - 1, 5, cz + 5, cx + 1, 5, cz + 5, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(cx - 1, 6, cz + 5, Kit.stairs(Kit.TRIM_STAIRS, "east")).set(cx + 1, 6, cz + 5, Kit.stairs(Kit.TRIM_STAIRS, "west"));
        s.set(cx, 6, cz + 5, Kit.TRIM).set(cx, 7, cz + 5, Kit.slab(Kit.TRIM_SLAB));
        s.set(cx - 2, 4, cz + 5, Kit.wallTorch("south")).set(cx + 2, 4, cz + 5, Kit.wallTorch("south"));
        s.set(cx - 2, 10, cz + 5, Kit.wallBanner(PURPLE, "south")).set(cx + 2, 10, cz + 5, Kit.wallBanner(PURPLE, "south"));
        // Strips of windows on four sides, amethyst between the storeys.
        for (int[] side : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            int x = cx + 4 * side[0];
            int z = cz + 4 * side[1];
            for (int i = 0; i < 4; i++) {
                int y1 = floors[i] + 2;
                if (i == 0 && side[1] > 0) {
                    continue;
                }
                roundWindow(s, x, z, side[0], side[1], y1, y1 + 1, i == 1 || i == 3, i > 0, pane(PURPLE));
            }
            for (int i = 1; i < 4; i++) {
                if (i > 1 || side[1] <= 0) {
                    s.set(x, floors[i], z, AMETHYST);
                }
            }
        }
        windingStair(s, cx, cz, d, floors, -1, 3);
        // The hall of the focus: the post and its chest, shelves with end rods, amethyst, a carpet from the door.
        focusHall(s, cx, cz, 1, 6, new int[]{1, -1}, "west");
        for (int dx = -1; dx <= 1; dx++) {
            put(s, cx + dx, 2, cz - 3, BOOKSHELF);
            put(s, cx + dx, 3, cz - 3, dx == 0 ? SOUL_LANTERN : endRod("up"));
        }
        put(s, cx + 3, 2, cz + 1, AMETHYST);
        put(s, cx + 3, 3, cz + 1, CLUSTER);
        put(s, cx + 2, 2, cz + 2, BOOKSHELF);
        put(s, cx + 2, 3, cz + 2, Furnish.candles(3));
        s.fill(cx, 2, cz + 1, cx, 2, cz + 3, PURPLE_CARPET);
        // The library: shelves along the wall, the lectern and a reading table.
        int y = 7;
        for (int[] c : new int[][]{{-2, -3}, {-1, -3}, {0, -3}, {1, -3}, {2, -3}, {2, -2}, {3, -2}, {3, 1}, {3, 2}, {2, 3}, {1, 3}}) {
            if (s.get(cx + c[0], y, cz + c[1]).equals(AIR)) {
                s.fill(cx + c[0], y, cz + c[1], cx + c[0], y + 2, cz + c[1], BOOKSHELF);
            }
        }
        put(s, cx, y, cz + 1, lectern("north"));
        put(s, cx + 1, y, cz - 1, Kit.topSlab(SPRUCE_SLAB));
        put(s, cx + 1, y + 1, cz - 1, Furnish.candles(3));
        put(s, cx + 1, y, cz, Kit.stairs(SPRUCE_STAIRS, "south"));
        put(s, cx - 1, y, cz - 1, Kit.stairs(SPRUCE_STAIRS, "east"));
        chandelier(s, cx, cz, 9, 9);
        // The brewing room: a cauldron ringed by brewing stands, more cauldrons, herbs and sacks.
        y = 11;
        put(s, cx, y, cz, CAULDRON);
        for (int[] c : new int[][]{{1, 0}, {0, 1}, {0, -1}}) {
            put(s, cx + c[0], y, cz + c[1], BREWING);
        }
        put(s, cx + 3, y, cz + 1, EMPTY_CAULDRON);
        put(s, cx + 3, y, cz + 2, CAULDRON);
        put(s, cx + 2, y, cz + 3, HAY);
        put(s, cx + 1, y, cz + 3, "minecraft:white_wool");
        put(s, cx + 2, y, cz + 2, Kit.topSlab(SPRUCE_SLAB));
        put(s, cx + 2, y + 1, cz + 2, Furnish.candles(2));
        put(s, cx + 2, y, cz - 2, BOOKSHELF);
        put(s, cx + 2, y + 1, cz - 2, SOUL_LANTERN);
        chandelier(s, cx - 1, cz + 1, 13, 13);
        // The bedroom of the two mages: beds with their heads to the west wall, a chest, a desk, a carpet.
        y = 15;
        s.bed(cx - 2, y, cz, "minecraft:purple_bed", "west", -1, 0);
        s.bed(cx - 2, y, cz + 1, "minecraft:purple_bed", "west", -1, 0);
        put(s, cx - 2, y, cz - 1, Kit.chest("east"));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                put(s, cx + dx, y, cz + dz, PURPLE_CARPET);
            }
        }
        put(s, cx + 2, y, cz + 2, Kit.topSlab(SPRUCE_SLAB));
        put(s, cx + 2, y + 1, cz + 2, Furnish.candles(1));
        put(s, cx + 1, y, cz + 2, Kit.stairs(SPRUCE_STAIRS, "west"));
        put(s, cx + 3, y, cz + 1, BOOKSHELF);
        put(s, cx + 3, y + 1, cz + 1, SOUL_LANTERN);
        chandelier(s, cx, cz, 17, 17);
        // The research room under the spire: the enchanting table between shelves, crystals, a telescope and star maps.
        balconyTop(s, cx, cz, d, floors[4], eaves, new int[]{1, 1, 2, 2, 2, 2});
        y = 19;
        put(s, cx, y, cz - 1, ENCHANTING);
        put(s, cx - 1, y, cz - 2, BOOKSHELF);
        put(s, cx + 1, y, cz - 3, BOOKSHELF);
        put(s, cx - 2, y, cz + 2, AMETHYST);
        put(s, cx - 2, y + 1, cz + 2, CLUSTER);
        put(s, cx + 2, y, cz + 2, AMETHYST);
        put(s, cx + 2, y + 1, cz + 2, CLUSTER);
        put(s, cx, y, cz + 3, Kit.fence("spruce"));
        s.set(cx, y + 1, cz + 3, TELESCOPE.replace("north", "south"));
        put(s, cx - 1, y, cz + 1, CARTOGRAPHY);
        for (int[] side : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            lucarne(s, cx, cz, eaves + 2, 3, side[0], side[1]);
        }
        s.connect();
        s.trimSky(3);
        return new Drawing(s, new int[]{cx, 2, cz + 3}, null);
    }

    // ---------------------------------------------------------------- mage tower S

    /** The middle of the tower of rank S and the walls of its annex. */
    private static final int MS_X = 8;
    private static final int MS_Z = 6;
    private static final int MS_AX1 = 3;
    private static final int MS_AX2 = 13;
    private static final int MS_AZ2 = 15;

    /**
     * The mage tower of rank S, 17 by 17 and 36 high: a round tower of stone nine blocks across with five storeys, a
     * balcony round its top room and a slate spire with lucarnes, and before it an annex of two storeys, stone below and
     * timber frame above, under a roof of blue-green tiles whose gable with a rose of purple glass looks at the street over
     * the porch.
     *
     * <p>The annex holds the brewing room on the ground floor and the bedroom of the three mages above. The tower holds the
     * hall of the focus with the post of the mage and its chest, the library, the research room with the crystal
     * accumulator and the enchanting table, the study with its maps of the stars, and the observatory under the spire.
     */
    private static Drawing mageS() {
        int cx = MS_X;
        int cz = MS_Z;
        int d = 9;
        int[] floors = {1, 6, 11, 16, 20};
        int eaves = 24;
        Structure s = new Structure(17, 38, 17);
        mageGarden(s, cx, cz, 15);
        s.fill(MS_AX1 - 1, 0, 9, MS_AX2 + 1, 0, 16, Kit.ROUGH);
        s.fill(7, 0, 16, 9, 0, 16, Kit.TRIM);
        rimStairs(s, cx, 1, cz, 13, Kit.ROUGH_STAIRS);
        band(s, cx, 1, cz, 11, 0, Kit.ROUGH);
        rimStairs(s, cx, 2, cz, 11, Kit.ROUGH_STAIRS);
        mageAnnex(s);
        mageShaft(s, cx, cz, d, floors[4] - 1, floors, new String[]{DEEPSLATE, PLANKS, CALCITE, OAK_PLANKS, TILES});
        // The openings into the annex below and above, strips of windows, amethyst between the storeys.
        s.fill(cx, 2, cz + 4, cx, 3, cz + 4, AIR);
        s.set(cx, 4, cz + 4, Kit.CARVED);
        s.door(cx, 7, cz + 4, DOOR, "north", "left");
        for (int[] side : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            int x = cx + 4 * side[0];
            int z = cz + 4 * side[1];
            for (int i = 0; i < 4; i++) {
                int y1 = floors[i] + 2;
                if (side[1] > 0 && i < 3) {
                    continue;
                }
                roundWindow(s, x, z, side[0], side[1], y1, y1 + 1, i % 2 == 0, i > 0, pane(i == 2 ? MAGENTA : PURPLE));
            }
            for (int i = 1; i < 4; i++) {
                if (side[1] <= 0 || i > 2) {
                    s.set(x, floors[i], z, AMETHYST);
                }
            }
        }
        s.set(cx - 2, 17, cz + 5, Kit.wallBanner(PURPLE, "south")).set(cx + 2, 17, cz + 5, Kit.wallBanner(PURPLE, "south"));
        windingStair(s, cx, cz, d, floors, -1, 3);
        // The hall of the focus.
        focusHall(s, cx, cz, 1, 6, new int[]{1, -1}, "west");
        for (int dx = -1; dx <= 1; dx++) {
            put(s, cx + dx, 2, cz - 3, BOOKSHELF);
            put(s, cx + dx, 3, cz - 3, dx == 0 ? SOUL_LANTERN : endRod("up"));
        }
        put(s, cx + 3, 2, cz, AMETHYST);
        put(s, cx + 3, 3, cz, CLUSTER);
        put(s, cx + 3, 2, cz + 1, BOOKSHELF);
        put(s, cx + 2, 2, cz + 2, Kit.stairs(SPRUCE_STAIRS, "west"));
        s.fill(cx, 2, cz + 1, cx, 2, cz + 3, PURPLE_CARPET);
        // The library.
        int y = 7;
        for (int[] c : new int[][]{{-2, -3}, {-1, -3}, {0, -3}, {1, -3}, {2, -3}, {2, -2}, {3, -2}, {3, -1}, {3, 1}, {3, 2}, {2, 2}}) {
            if (s.get(cx + c[0], y, cz + c[1]).equals(AIR)) {
                s.fill(cx + c[0], y, cz + c[1], cx + c[0], y + 2, cz + c[1], BOOKSHELF);
            }
        }
        put(s, cx + 1, y, cz, lectern("east"));
        put(s, cx - 1, y, cz - 1, Kit.topSlab(SPRUCE_SLAB));
        put(s, cx - 1, y + 1, cz - 1, Furnish.candles(3));
        put(s, cx - 1, y, cz, Kit.stairs(SPRUCE_STAIRS, "south"));
        chandelier(s, cx, cz, 10, 10);
        // The research room: the crystal accumulator on amethyst, crystals round it, the enchanting table, end rods.
        y = 12;
        for (int[] c : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            s.set(cx + c[0], 11, cz + c[1], AMETHYST);
        }
        s.set(cx, y, cz, ACCUMULATOR);
        for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {3, 0}}) {
            put(s, cx + c[0], y, cz + c[1], AMETHYST);
            put(s, cx + c[0], y + 1, cz + c[1], CLUSTER);
        }
        put(s, cx, y, cz + 2, ENCHANTING);
        for (int dx : new int[]{-1, 1}) {
            put(s, cx + dx, y, cz + 3, BOOKSHELF);
            put(s, cx + dx, y + 1, cz + 3, BOOKSHELF);
            put(s, cx + dx, y + 2, cz + 3, endRod("up"));
        }
        chandelier(s, cx, cz + 1, 15, 15);
        // The study: maps of the stars, a desk, shelves.
        y = 17;
        put(s, cx, y, cz, CARTOGRAPHY);
        put(s, cx + 1, y, cz, Kit.stairs(SPRUCE_STAIRS, "east"));
        mapTable(s, cx - 1, cz + 2, cx + 1, cz + 2, y);
        for (int[] c : new int[][]{{3, 1}, {3, 2}, {2, 3}, {1, 3}, {-1, 3}}) {
            if (s.get(cx + c[0], y, cz + c[1]).equals(AIR)) {
                s.fill(cx + c[0], y, cz + c[1], cx + c[0], y + 1, cz + c[1], BOOKSHELF);
            }
        }
        put(s, cx + 2, y, cz - 2, AMETHYST);
        put(s, cx + 2, y + 1, cz - 2, CLUSTER);
        chandelier(s, cx, cz - 1, 19, 19);
        // The observatory under the spire.
        balconyTop(s, cx, cz, d, floors[4], eaves, new int[]{1, 1, 2, 2, 2, 2});
        y = 21;
        put(s, cx, y, cz - 3, Kit.fence("spruce"));
        s.set(cx, y + 1, cz - 3, TELESCOPE);
        put(s, cx + 2, y, cz + 2, AMETHYST);
        put(s, cx + 2, y + 1, cz + 2, CLUSTER);
        put(s, cx - 2, y, cz + 2, Kit.stairs(SPRUCE_STAIRS, "north"));
        put(s, cx + 1, y, cz - 2, SEA_LANTERN);
        for (int[] side : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            lucarne(s, cx, cz, eaves + 2, 3, side[0], side[1]);
        }
        mageAnnexRooms(s);
        s.connect();
        s.trimSky(3);
        return new Drawing(s, new int[]{cx, 2, MS_AZ2 - 1}, null);
    }

    /**
     * The annex of the mage tower of rank S: a ground storey of stone with quoins and windows of purple glass, a string
     * course, an upper storey of timber frame with plaster, a roof of blue-green tiles whose gable to the street holds a
     * rose of purple and magenta glass, an arched door between lights under a porch on two columns with lanterns.
     */
    private static void mageAnnex(Structure s) {
        int x1 = MS_AX1;
        int x2 = MS_AX2;
        int z1 = 10;
        int z2 = MS_AZ2;
        s.fill(x1 + 1, 1, z1 + 1, x2 - 1, 9, z2 - 1, AIR);
        s.fill(x1 + 1, 1, z1 + 1, x2 - 1, 1, z2 - 1, TILES);
        stoneStorey(s, x1, z1, x2, z2, 1, 5);
        s.walls(x1, 6, z1, x2, 6, z2, Kit.TRIM);
        s.fill(x1 + 1, 6, z1 + 1, x2 - 1, 6, z2 - 1, PLANKS);
        timberStorey(s, x1, z1, x2, z2, 7, 10, new int[]{5, 8, 11}, new int[]{12, 14});
        int[] under = roofZ(s, x1 - 1, x2 + 1, z1, z2 + 1, 10, 0, Kit.TEAL);
        gableAtZ(s, x1 - 1, under, z2, 11, Kit.PLASTER);
        s.fill(8, 11, z2, 8, under[6] - 1, z2, Kit.log(OAK_LOG, "y"));
        // The rose of the gable.
        s.set(7, 12, z2, pane(MAGENTA)).set(9, 12, z2, pane(MAGENTA)).set(8, 12, z2, pane(PURPLE));
        s.set(8, 13, z2, pane(PURPLE)).set(8, 11, z2, pane(PURPLE));
        s.set(7, 13, z2, Kit.log(OAK_LOG, "x")).set(9, 13, z2, Kit.log(OAK_LOG, "x"));
        // Windows below and above, the door between lights, the porch.
        for (int x : new int[]{5, 12}) {
            stoneWindow(s, x - 1, z2, x, z2, 3, 4, "south", pane(PURPLE));
        }
        for (int x : new int[]{x1, x2}) {
            stoneWindow(s, x, 12, x, 13, 3, 4, x == x1 ? "west" : "east", pane(PURPLE));
            s.fill(x, 8, 13, x, 9, 13, Kit.GLASS);
            s.fill(x, 8, 11, x, 9, 11, Kit.GLASS);
        }
        for (int x : new int[]{4, 6, 10, 12}) {
            s.fill(x, 8, z2, x, 9, z2, Kit.GLASS);
        }
        s.fill(7, 2, z2, 7, 5, z2, Kit.ROUGH).fill(9, 2, z2, 9, 5, z2, Kit.ROUGH);
        s.door(8, 2, z2, DOOR, "north", "left");
        s.set(8, 4, z2, pane(PURPLE)).set(8, 5, z2, Kit.CARVED);
        s.set(7, 4, z2, Kit.upside(Kit.WALL_STAIRS, "west")).set(9, 4, z2, Kit.upside(Kit.WALL_STAIRS, "east"));
        s.set(8, 1, z2 + 1, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        for (int x : new int[]{6, 10}) {
            s.set(x, 4, z2 + 1, Kit.wallBanner(PURPLE, "south"));
        }
        s.fill(7, 5, z2 + 1, 9, 5, z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(7, 6, z2 + 1, Kit.stairs(Kit.TRIM_STAIRS, "east")).set(9, 6, z2 + 1, Kit.stairs(Kit.TRIM_STAIRS, "west")).set(8, 6, z2 + 1, Kit.TRIM);
        s.set(7, 1, z2 + 1, Kit.lantern()).set(9, 1, z2 + 1, Kit.lantern());
    }

    /**
     * The rooms of the annex of rank S: the brewing room below with brewing stands, cauldrons, herbs and sacks and the way
     * into the hall of the focus; the bedroom of the three mages above with their beds, their chest, a desk and a rug.
     */
    private static void mageAnnexRooms(Structure s) {
        int y = 2;
        put(s, 4, y, 11, BREWING);
        put(s, 5, y, 11, CAULDRON);
        put(s, 4, y, 12, Kit.topSlab(SPRUCE_SLAB));
        put(s, 4, y + 1, 12, BREWING);
        put(s, 4, y, 13, EMPTY_CAULDRON);
        put(s, 12, y, 11, HAY);
        put(s, 12, y + 1, 11, HAY);
        put(s, 11, y, 11, "minecraft:white_wool");
        put(s, 12, y, 12, "minecraft:brown_wool");
        put(s, 12, y, 13, Kit.topSlab(SPRUCE_SLAB));
        put(s, 12, y + 1, 13, Furnish.candles(3));
        put(s, 11, y, 14, BREWING);
        Furnish.rug(s, 7, 11, 9, 14, y, PURPLE_CARPET);
        chandelier(s, 8, 12, 5, 5);
        chandelier(s, 5, 13, 5, 5);
        chandelier(s, 11, 13, 5, 5);
        // The bedroom.
        y = 7;
        Furnish.bed(s, 5, y, 11, "west", PURPLE);
        Furnish.bed(s, 5, y, 13, "west", PURPLE);
        Furnish.bed(s, 11, y, 12, "east", PURPLE);
        put(s, 12, y, 14, Kit.chest("west"));
        put(s, 4, y, 14, Kit.topSlab(SPRUCE_SLAB));
        put(s, 4, y + 1, 14, Furnish.candles(2));
        put(s, 5, y, 14, Kit.stairs(SPRUCE_STAIRS, "east"));
        put(s, 12, y, 11, BOOKSHELF);
        put(s, 12, y + 1, 11, SOUL_LANTERN);
        Furnish.rug(s, 7, 12, 9, 14, y, PURPLE_CARPET);
        chandelier(s, 8, 13, 10, 13);
    }

    // ---------------------------------------------------------------- mage tower SS

    /** The middle of the main tower of rank SS. */
    private static final int G_X = 12;
    private static final int G_Z = 10;
    /** The floors of the main tower: the hall of the focus, the library, the alchemists, the crystals, the bedroom, the observatory. */
    private static final int G_HALL = 1;
    private static final int G_LIBRARY = 6;
    private static final int G_ALCHEMY = 10;
    private static final int G_CRYSTAL = 14;
    private static final int G_BEDROOM = 18;
    private static final int G_PLATFORM = 22;
    /** The ring beam on the columns of the observatory and the eaves of the great spire over it. */
    private static final int G_BEAM = 28;
    private static final int G_EAVES = 29;
    /** The middles of the west and the east turret and the floors of their top rooms, level with their bridges. */
    private static final int WEST_X = 5;
    private static final int EAST_X = 19;
    private static final int TURRET_Z = 16;
    private static final int WEST_FLOOR = G_BEDROOM;
    private static final int EAST_FLOOR = G_CRYSTAL;

    private static void m(Structure s, int dx, int y, int dz, String state) {
        s.set(G_X + dx, y, G_Z + dz, state);
    }

    private static void mPut(Structure s, int dx, int y, int dz, String state) {
        put(s, G_X + dx, y, G_Z + dz, state);
    }

    /**
     * The mage tower of rank SS, 25 by 25 and 46 high, the seat of the mages of a strong kingdom: a main round tower of
     * stone eleven blocks across and six storeys high, held by flying buttresses on piers with pinnacles, flanked at the
     * front by two slender turrets of different heights that bridges join to it high up, and joined on its west side by the
     * library wing, a hall of two storeys with tall lancets of purple glass under a roof of blue-green tiles. The entrance
     * is a gabled hall with a rose window and a porch on columns.
     *
     * <p>Inside, a stair winds up the main tower past the hall of the focus with the post of the mage and its chest, the
     * library, the floor of the alchemists, the crystal room with the crystal accumulator and the enchanting table, and the
     * bedroom of three mages, up to the observatory: an open gallery on columns under the great slate spire, with its
     * telescope, its orrery and its maps of the stars. The west turret holds the bed of the fourth mage, the east one a room
     * of scrying; the library wing holds the reading room with the lectern and a gallery of books above it.
     */
    private static Drawing mageSS() {
        Structure s = new Structure(25, 48, 25);
        grandGround(s);
        band(s, G_X, 1, G_Z, 13, 0, Kit.ROUGH);
        rimStairs(s, G_X, 2, G_Z, 13, Kit.ROUGH_STAIRS);
        libraryWing(s);
        grandShaft(s);
        for (int z : new int[]{G_Z - 2, G_Z + 2}) {
            flyer(s, 22, z, -1, 0, 3);
        }
        for (int x : new int[]{G_X - 2, G_X + 2}) {
            flyer(s, x, 0, 0, 1, 3);
        }
        turret(s, WEST_X, WEST_FLOOR, -1);
        turret(s, EAST_X, EAST_FLOOR, 1);
        grandEntrance(s);
        grandFloors(s);
        grandTurretRooms(s);
        grandPlatform(s);
        libraryRooms(s);
        footings(s);
        s.connect();
        s.trimSky(3);
        return new Drawing(s, new int[]{G_X, 2, G_Z + 8}, null);
    }

    /**
     * The ground of rank SS: grass with flowers in its corners, rough stone paving round the towers, a path of stone to the
     * porch and two lamp posts beside it.
     */
    private static void grandGround(Structure s) {
        s.fill(0, 0, 0, 24, 0, 24, GRASS);
        band(s, G_X, 0, G_Z, 17, 0, Kit.ROUGH);
        band(s, WEST_X, 0, TURRET_Z, 9, 0, Kit.ROUGH);
        band(s, EAST_X, 0, TURRET_Z, 9, 0, Kit.ROUGH);
        s.fill(8, 0, 15, 16, 0, 24, Kit.ROUGH);
        s.fill(11, 0, 18, 13, 0, 24, Kit.WALL);
        for (int z = 0; z <= 24; z++) {
            for (int x = 0; x <= 24; x++) {
                boolean corner = (x <= 2 || x >= 22) && (z <= 1 || z >= 22) || x <= 1 && z >= 19 || x >= 23 && z >= 19;
                if (corner && s.get(x, 0, z).equals(GRASS) && (x + z) % 2 == 0) {
                    s.set(x, 1, z, (x + z) % 4 == 0 ? "minecraft:azure_bluet" : "minecraft:allium");
                }
            }
        }
        for (int x : new int[]{8, 16}) {
            s.set(x, 1, 23, Kit.WALL).set(x, 2, 23, Kit.WALL_FENCE).set(x, 3, 23, Kit.lantern());
        }
    }

    /**
     * The main tower of rank SS: a plinth with a sloping top, the round wall on a base of rough stone with bands of trim at
     * every floor, upright strips of trim on its faces and between them windows of purple glass, wide and narrow by turns,
     * magenta in the crystal room, none low on the west where the library wing leans on it; banners of the kingdom on the
     * front over the entrance.
     */
    private static void grandShaft(Structure s) {
        for (int y = G_HALL; y < G_PLATFORM; y++) {
            roundFloor(s, G_X, y, G_Z, 11, AIR);
        }
        Kit.tube(s, G_X, 2, 4, G_Z, 11, Kit.ROUGH);
        Kit.tube(s, G_X, 5, G_PLATFORM - 1, G_Z, 11, Kit.WALL);
        for (int y : new int[]{G_LIBRARY, G_ALCHEMY, G_CRYSTAL, G_BEDROOM}) {
            Kit.tube(s, G_X, y, y, G_Z, 11, Kit.TRIM);
        }
        String glass = pane(PURPLE);
        for (int[] side : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            int x = G_X + 5 * side[0];
            int z = G_Z + 5 * side[1];
            int ax = side[1] != 0 ? 1 : 0;
            int az = side[0] != 0 ? 1 : 0;
            boolean front = side[1] > 0;
            boolean west = side[0] < 0;
            int from = front ? 12 : west ? 17 : 5;
            s.fill(x - 2 * ax, from, z - 2 * az, x - 2 * ax, G_PLATFORM - 1, z - 2 * az, Kit.TRIM);
            s.fill(x + 2 * ax, from, z + 2 * az, x + 2 * ax, G_PLATFORM - 1, z + 2 * az, Kit.TRIM);
            if (!front && !west) {
                roundWindow(s, x, z, side[0], side[1], 3, 4, true, false, glass);
                roundWindow(s, x, z, side[0], side[1], 7, 8, false, true, glass);
            }
            if (!west) {
                roundWindow(s, x, z, side[0], side[1], 11, 12, true, true, glass);
                roundWindow(s, x, z, side[0], side[1], 15, 16, false, true, pane(MAGENTA));
            }
            roundWindow(s, x, z, side[0], side[1], 19, 20, true, true, glass);
        }
        m(s, -2, 14, 6, Kit.wallBanner("red", "south"));
        m(s, 2, 14, 6, Kit.wallBanner("red", "south"));
    }

    /**
     * One flying buttress: its pier at {@code (x, z)}, low with a lantern on it, and the next cell toward the tower, tall,
     * with a band of trim and a pinnacle; from the top of the tall one a heavy arch two blocks thick and from its band a
     * light one, each {@code span} cells long and rising one layer a cell to the wall of the tower.
     */
    private static void flyer(Structure s, int x, int z, int stepX, int stepZ, int span) {
        String toTower = toward(new int[]{0, 0}, new int[]{stepX, stepZ});
        String away = opposite(toTower);
        int px = x + stepX;
        int pz = z + stepZ;
        s.set(x, 0, z, Kit.ROUGH).set(px, 0, pz, Kit.ROUGH);
        s.fill(x, 1, z, x, 6, z, Kit.ROUGH);
        s.set(x, 7, z, Kit.lantern());
        s.fill(px, 1, pz, px, 4, pz, Kit.ROUGH);
        s.fill(px, 5, pz, px, 15, pz, Kit.WALL);
        s.set(px, 7, pz, Kit.TRIM).set(px, 15, pz, Kit.TRIM);
        s.set(px, 16, pz, Kit.WALL_FENCE).set(px, 17, pz, Kit.WALL_FENCE).set(px, 18, pz, endRod("up"));
        for (int i = 1; i <= span; i++) {
            int fx = px + i * stepX;
            int fz = pz + i * stepZ;
            s.set(fx, 15 + i, fz, Kit.stairs(Kit.WALL_STAIRS, toTower));
            s.set(fx, 14 + i, fz, Kit.WALL);
            s.set(fx, 13 + i, fz, Kit.upside(Kit.WALL_STAIRS, away));
            s.set(fx, 7 + i, fz, Kit.stairs(Kit.WALL_STAIRS, toTower));
            s.set(fx, 6 + i, fz, Kit.upside(Kit.WALL_STAIRS, away));
        }
    }

    /**
     * A slender turret of rank SS five blocks across: a plinth of rough stone, a solid shaft with bands of trim, a top room
     * level with the bridge to the main tower with windows of purple glass on its three outer sides and a door toward the
     * bridge, a banner of the kingdom on its front and a slate cone with an end rod on its point.
     *
     * @param side -1 for the west turret, 1 for the east one
     */
    private static void turret(Structure s, int cx, int floorY, int side) {
        int cz = TURRET_Z;
        band(s, cx, 1, cz, 7, 0, Kit.ROUGH);
        rimStairs(s, cx, 2, cz, 7, Kit.ROUGH_STAIRS);
        for (int y = 2; y <= floorY; y++) {
            band(s, cx, y, cz, 5, 0, y <= 4 ? Kit.ROUGH : Kit.WALL);
        }
        for (int y = 9; y < floorY; y += 5) {
            Kit.tube(s, cx, y, y, cz, 5, Kit.TRIM);
        }
        Kit.tube(s, cx, floorY, floorY, cz, 5, Kit.TRIM);
        roundFloor(s, cx, floorY, cz, 5, PLANKS);
        Kit.tube(s, cx, floorY + 1, floorY + 3, cz, 5, Kit.WALL);
        Kit.tube(s, cx, floorY + 4, floorY + 4, cz, 5, Kit.TRIM);
        for (int[] w : new int[][]{{2 * side, 0}, {0, -2}, {0, 2}}) {
            s.fill(cx + w[0], floorY + 2, cz + w[1], cx + w[0], floorY + 3, cz + w[1], pane(PURPLE));
        }
        s.fill(cx - 2 * side, floorY + 1, cz - 1, cx - 2 * side, floorY + 2, cz - 1, AIR);
        s.set(cx, floorY - 5, cz + 3, Kit.wallBanner("red", "south"));
        int top = roundRoof(s, cx, floorY + 5, cz, 7, new int[]{1, 3, 3, 3}, Kit.SLATE);
        s.set(cx, top, cz, endRod("up"));
        chandelier(s, cx, cz, floorY + 4, hollowTop(s, cx, floorY + 4, cz));
    }

    /**
     * The entrance hall of rank SS at the foot of the main tower: walls of stone on rough stone under a gable roof of
     * blue-green tiles whose gable looks at the street with a rose of purple glass, a door between side lights under a
     * fanlight, a porch on two columns with an arch and lanterns, steps up to it; inside, benches, banners of the kingdom
     * and a red carpet into the tower.
     */
    private static void grandEntrance(Structure s) {
        int x1 = G_X - 3;
        int x2 = G_X + 3;
        int z1 = G_Z + 5;
        int z2 = G_Z + 9;
        s.walls(x1, 1, z1, x2, 7, z2, Kit.WALL);
        s.walls(x1, 1, z1, x2, 1, z2, Kit.ROUGH);
        s.fill(x1, 1, z2, x1, 7, z2, Kit.ROUGH).fill(x2, 1, z2, x2, 7, z2, Kit.ROUGH);
        s.fill(x1 + 1, 1, z1 + 1, x2 - 1, 1, z2 - 1, Kit.TRIM);
        s.fill(x1 + 1, 2, z1 + 1, x2 - 1, 7, z2 - 1, AIR);
        s.gableRoofZ(x1 - 1, x2 + 1, z1, z2 + 1, 7, Kit.TEAL.stairs(), Kit.TEAL.slab(), Kit.WALL, -1, z2);
        s.fill(x1, 7, z2, x2, 7, z2, Kit.TRIM);
        // The rose window of the gable.
        s.fill(G_X - 1, 8, z2, G_X + 1, 8, z2, pane(MAGENTA)).set(G_X, 8, z2, pane(PURPLE)).set(G_X, 9, z2, pane(PURPLE));
        s.set(G_X, 10, z2, Kit.CARVED);
        // The door between side lights under a fanlight.
        s.door(G_X, 2, z2, DOOR, "north", "left");
        s.fill(G_X - 1, 2, z2, G_X - 1, 3, z2, pane(PURPLE)).fill(G_X + 1, 2, z2, G_X + 1, 3, z2, pane(PURPLE));
        s.set(G_X - 1, 4, z2, Kit.upside(Kit.TRIM_STAIRS, "west")).set(G_X + 1, 4, z2, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.set(G_X, 4, z2, pane(PURPLE)).set(G_X, 5, z2, Kit.CARVED);
        s.fill(x1, 3, z1 + 2, x1, 4, z1 + 2, pane(PURPLE)).fill(x2, 3, z1 + 2, x2, 4, z1 + 2, pane(PURPLE));
        // The porch: two columns, an arch between them, lanterns on the capitals, steps.
        for (int x : new int[]{G_X - 2, G_X + 2}) {
            s.set(x, 1, z2 + 1, Kit.ROUGH).fill(x, 2, z2 + 1, x, 4, z2 + 1, Kit.TRIM).set(x, 5, z2 + 1, Kit.CARVED).set(x, 6, z2 + 1, Kit.lantern());
        }
        s.fill(G_X - 1, 1, z2 + 1, G_X + 1, 1, z2 + 1, Kit.TRIM);
        s.set(G_X - 1, 4, z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "west")).set(G_X + 1, 4, z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.fill(G_X - 1, 5, z2 + 1, G_X + 1, 5, z2 + 1, Kit.TRIM).set(G_X, 5, z2 + 1, Kit.CARVED);
        s.fill(G_X - 1, 6, z2 + 1, G_X + 1, 6, z2 + 1, Kit.slab(Kit.TRIM_SLAB));
        s.fill(G_X - 1, 1, z2 + 2, G_X + 1, 1, z2 + 2, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        // Inside: benches, banners, a carpet, a lantern under the ridge, the opening into the tower.
        for (int z = z1 + 1; z <= z1 + 2; z++) {
            s.set(x1 + 1, 2, z, Kit.stairs(SPRUCE_STAIRS, "west")).set(x2 - 1, 2, z, Kit.stairs(SPRUCE_STAIRS, "east"));
        }
        s.set(x1 + 1, 4, z2 - 1, Kit.wallBanner("red", "north")).set(x2 - 1, 4, z2 - 1, Kit.wallBanner("red", "north"));
        chandelier(s, G_X, z1 + 2, 6, 10);
        for (int x = G_X - 2; x <= G_X + 2; x++) {
            s.fill(x, 1, z1, x, 4, z1, Kit.ROUGH).fill(x, 5, z1, x, 11, z1, Kit.WALL);
        }
        m(s, 0, G_LIBRARY, 5, Kit.TRIM);
        s.fill(G_X, 2, z1, G_X, 3, z1, AIR);
        m(s, 0, 1, 5, Kit.TRIM);
        s.fill(G_X, 2, z1 + 1, G_X, 2, z2 - 1, Kit.CLOTH_CARPET);
    }

    /**
     * The storeys of the main tower of rank SS and its winding stair: the hall of the focus, the library, the floor of the
     * alchemists, the crystal room and the bedroom of three mages.
     */
    private static void grandFloors(Structure s) {
        roundFloor(s, G_X, G_HALL, G_Z, 11, DEEPSLATE);
        roundFloor(s, G_X, G_LIBRARY, G_Z, 11, PLANKS);
        roundFloor(s, G_X, G_ALCHEMY, G_Z, 11, TILES);
        roundFloor(s, G_X, G_CRYSTAL, G_Z, 11, CALCITE);
        roundFloor(s, G_X, G_BEDROOM, G_Z, 11, OAK_PLANKS);
        roundFloor(s, G_X, G_PLATFORM, G_Z, 13, TILES);
        windingStair(s, G_X, G_Z, 11, new int[]{G_HALL, G_LIBRARY, G_ALCHEMY, G_CRYSTAL, G_BEDROOM, G_PLATFORM}, -4, 0);
        // The doors to the library wing below and above.
        s.door(G_X - 5, 2, G_Z + 1, DOOR, "east", "left");
        s.door(G_X - 5, G_LIBRARY + 1, G_Z, DOOR, "east", "left");
        grandHall(s);
        grandLibrary(s);
        grandAlchemy(s);
        grandCrystal(s);
        grandBedroom(s);
    }

    /**
     * The hall of the focus of rank SS: a floor of dark stone with a ring of calcite, the focus on its amethyst in the
     * middle, the post of the mage with its chest, shelves with end rods, crystals of amethyst, a carpet from the entrance
     * and lanterns hanging from the floor above.
     */
    private static void grandHall(Structure s) {
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                int r = dx * dx + dz * dz;
                if (r >= 8 && r <= 10 && interior(dx, dz, 11) && States.isFull(s.get(G_X + dx, G_HALL, G_Z + dz))) {
                    m(s, dx, G_HALL, dz, CALCITE);
                }
            }
        }
        focusHall(s, G_X, G_Z, G_HALL, G_LIBRARY, new int[]{2, -2}, "west");
        int y = G_HALL + 1;
        for (int dx : new int[]{-2, 2}) {
            mPut(s, dx, y, -4, BOOKSHELF);
            mPut(s, dx, y + 1, -4, BOOKSHELF);
            mPut(s, dx, y + 2, -4, endRod("up"));
        }
        mPut(s, 0, y, -4, AMETHYST);
        mPut(s, 0, y + 1, -4, CLUSTER);
        mPut(s, 3, y, -3, BOOKSHELF);
        mPut(s, 3, y + 1, -3, SOUL_LANTERN);
        mPut(s, 3, y, 2, AMETHYST);
        mPut(s, 3, y + 1, 2, CLUSTER);
        mPut(s, -3, y, 3, BOOKSHELF);
        mPut(s, -3, y + 1, 3, Furnish.candles(3));
        for (int dz = 1; dz <= 4; dz++) {
            mPut(s, 0, y, dz, Kit.CLOTH_CARPET);
        }
        for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}}) {
            chandelier(s, G_X + c[0], G_Z + c[1], G_LIBRARY - 1, G_LIBRARY - 1);
        }
    }

    /**
     * The library of the main tower of rank SS: bookshelves three high all round the wall but at the windows and the door to
     * the wing, a long reading table with chairs and lanterns, a chandelier.
     */
    private static void grandLibrary(Structure s) {
        int y = G_LIBRARY + 1;
        for (int[] c : innerRing(11)) {
            boolean window = c[0] == 0 && Math.abs(c[1]) == 4 || c[1] == 0 && Math.abs(c[0]) == 4;
            boolean free = true;
            for (int h = y; h <= y + 2; h++) {
                free &= s.get(G_X + c[0], h, G_Z + c[1]).equals(AIR);
            }
            if (!window && free && States.isFull(s.get(G_X + c[0], G_LIBRARY, G_Z + c[1]))) {
                s.fill(G_X + c[0], y, G_Z + c[1], G_X + c[0], y + 2, G_Z + c[1], BOOKSHELF);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            mPut(s, dx, y, 0, Kit.topSlab(SPRUCE_SLAB));
            mPut(s, dx, y, 1, Kit.stairs(SPRUCE_STAIRS, "south"));
            mPut(s, dx, y, -1, Kit.stairs(SPRUCE_STAIRS, "north"));
        }
        mPut(s, -1, y + 1, 0, Kit.lantern());
        mPut(s, 1, y + 1, 0, Kit.lantern());
        mPut(s, 0, y + 1, 0, Furnish.candles(3));
        chandelier(s, G_X, G_Z + 3, G_ALCHEMY - 1, G_ALCHEMY - 1);
        chandelier(s, G_X - 2, G_Z - 2, G_ALCHEMY - 1, G_ALCHEMY - 1);
    }

    /**
     * The floor of the alchemists of rank SS: a water cauldron in the middle ringed by four brewing stands, cauldrons and
     * another brewing stand along the wall, herbs in sacks and bales, a shelf with a lantern.
     */
    private static void grandAlchemy(Structure s) {
        int y = G_ALCHEMY + 1;
        mPut(s, 0, y, 0, CAULDRON);
        for (int[] c : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            mPut(s, c[0], y, c[1], BREWING);
        }
        mPut(s, -4, y, 1, CAULDRON);
        mPut(s, -4, y, 2, EMPTY_CAULDRON);
        mPut(s, -3, y, 3, CAULDRON);
        mPut(s, -4, y, -1, HAY);
        mPut(s, -4, y + 1, -1, HAY);
        mPut(s, -2, y, 4, "minecraft:white_wool");
        mPut(s, -1, y, 4, "minecraft:brown_wool");
        mPut(s, 1, y, 4, Kit.topSlab(SPRUCE_SLAB));
        mPut(s, 1, y + 1, 4, Furnish.candles(3));
        mPut(s, 2, y, 4, "minecraft:dried_kelp_block");
        mPut(s, 3, y, 3, BOOKSHELF);
        mPut(s, 3, y + 1, 3, SOUL_LANTERN);
        mPut(s, 4, y, 2, BREWING);
        mPut(s, 4, y, 1, Kit.topSlab(SPRUCE_SLAB));
        chandelier(s, G_X, G_Z, G_CRYSTAL - 1, G_CRYSTAL - 1);
        chandelier(s, G_X - 2, G_Z + 2, G_CRYSTAL - 1, G_CRYSTAL - 1);
        chandelier(s, G_X + 2, G_Z + 2, G_CRYSTAL - 1, G_CRYSTAL - 1);
    }

    /**
     * The crystal room of rank SS, the research room of the mages: the crystal accumulator in the middle on a floor of
     * calcite inlaid with amethyst, blocks of amethyst with clusters on them round it and along the wall, end rods, the
     * enchanting table between shelves, the door to the bridge of the east turret.
     */
    private static void grandCrystal(Structure s) {
        int y = G_CRYSTAL + 1;
        for (int[] c : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
            m(s, c[0], G_CRYSTAL, c[1], AMETHYST);
        }
        m(s, 0, y, 0, ACCUMULATOR);
        for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}, {-4, 1}, {-4, -1}, {-1, -4}, {1, -4}}) {
            mPut(s, c[0], y, c[1], AMETHYST);
            mPut(s, c[0], y + 1, c[1], CLUSTER);
        }
        mPut(s, -4, y, 2, endRod("up"));
        mPut(s, -4, y, -2, endRod("up"));
        mPut(s, -1, y, 3, BOOKSHELF);
        mPut(s, 0, y, 3, ENCHANTING);
        mPut(s, 1, y, 3, BOOKSHELF);
        mPut(s, -1, y + 1, 3, Furnish.candles(2));
        mPut(s, 1, y + 1, 3, Furnish.candles(2));
        chandelier(s, G_X, G_Z, G_BEDROOM - 1, G_BEDROOM - 1);
        m(s, 2, G_CRYSTAL, 5, Kit.TRIM);
        s.fill(G_X + 2, y, G_Z + 5, G_X + 2, y + 1, G_Z + 5, AIR);
    }

    /**
     * The bedroom of the three mages of rank SS: beds with their heads to the north wall, their chest between them, a desk,
     * a shelf with a lantern, a carpet, the door to the bridge of the west turret.
     */
    private static void grandBedroom(Structure s) {
        int y = G_BEDROOM + 1;
        for (int dx : new int[]{-2, 0, 2}) {
            s.bed(G_X + dx, y, G_Z - 3, "minecraft:purple_bed", "north", 0, -1);
        }
        mPut(s, 1, y, -4, Kit.chest("south"));
        mPut(s, -3, y, -2, BOOKSHELF);
        mPut(s, -3, y + 1, -2, SOUL_LANTERN);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                mPut(s, dx, y, dz, PURPLE_CARPET);
            }
        }
        mPut(s, -4, y, 2, Kit.topSlab(SPRUCE_SLAB));
        mPut(s, -4, y + 1, 2, Furnish.candles(2));
        mPut(s, -3, y, 2, Kit.stairs(SPRUCE_STAIRS, "east"));
        chandelier(s, G_X, G_Z, G_PLATFORM - 1, G_PLATFORM - 1);
        chandelier(s, G_X - 2, G_Z + 2, G_PLATFORM - 1, G_PLATFORM - 1);
        m(s, -2, G_BEDROOM, 5, Kit.TRIM);
        s.fill(G_X - 2, y, G_Z + 5, G_X - 2, y + 1, G_Z + 5, AIR);
    }

    /**
     * The bridges and the top rooms of the turrets of rank SS: the west one with the bed of the fourth mage, the east one a
     * room of scrying with a crystal and a table of star maps. Each bridge has a parapet, an arch under it and a lantern.
     */
    private static void grandTurretRooms(Structure s) {
        bridge(s, WEST_X + 3, WEST_X + 4, WEST_FLOOR, -1);
        bridge(s, EAST_X - 4, EAST_X - 3, EAST_FLOOR, 1);
        int y = WEST_FLOOR + 1;
        s.bed(WEST_X - 1, y, TURRET_Z, "minecraft:purple_bed", "north", 0, -1);
        put(s, WEST_X + 1, y, TURRET_Z + 1, BOOKSHELF);
        put(s, WEST_X + 1, y + 1, TURRET_Z + 1, Furnish.candles(1));
        y = EAST_FLOOR + 1;
        put(s, EAST_X, y, TURRET_Z + 1, AMETHYST);
        put(s, EAST_X, y + 1, TURRET_Z + 1, CLUSTER);
        put(s, EAST_X + 1, y, TURRET_Z, CARTOGRAPHY);
        put(s, EAST_X - 1, y, TURRET_Z + 1, Kit.stairs(SPRUCE_STAIRS, "west"));
    }

    /**
     * A bridge of two cells along z = 15 from a turret to the main tower of rank SS: a deck, a parapet on the open sides, an
     * arch of upside-down stairs under it and a lantern on the parapet.
     */
    private static void bridge(Structure s, int x1, int x2, int y, int side) {
        int z = TURRET_Z - 1;
        s.fill(x1, y, z, x2, y, z, Kit.WALL);
        s.set(x1, y - 1, z, Kit.upside(Kit.WALL_STAIRS, "west")).set(x2, y - 1, z, Kit.upside(Kit.WALL_STAIRS, "east"));
        for (int x = x1; x <= x2; x++) {
            if (!s.solid(x, y + 1, z + 1)) {
                s.set(x, y + 1, z + 1, Kit.WALL_FENCE);
            }
            if (!s.solid(x, y + 1, z - 1)) {
                s.set(x, y + 1, z - 1, Kit.WALL_FENCE);
            }
        }
        int post = side < 0 ? x1 : x2;
        s.set(post, y + 2, z + 1, Kit.lantern());
    }

    /**
     * The observatory of rank SS at the top of the main tower: corbels carrying a round gallery wider than the tower with a
     * parapet crowned by a ring of end rods, columns of trim and carved stone carrying the ring beam of the spire, an orrery
     * of a sea lantern on a pedestal, a telescope and a table of star maps, and the great spire over all with four glazed
     * lucarnes, a lantern hanging inside it and its crown of amethyst and an end rod.
     */
    private static void grandPlatform(Structure s) {
        corbels(s, G_X, G_PLATFORM - 1, G_Z, 13, 11, Kit.TRIM_STAIRS);
        band(s, G_X, G_PLATFORM, G_Z, 13, 11, Kit.WALL);
        Kit.tube(s, G_X, G_PLATFORM, G_PLATFORM, G_Z, 11, Kit.TRIM);
        int y = G_PLATFORM + 1;
        Kit.tube(s, G_X, y, y, G_Z, 13, Kit.WALL_FENCE);
        for (int dz = -6; dz <= 6; dz++) {
            for (int dx = -6; dx <= 6; dx++) {
                if (Kit.onRim(dx, dz, 13) && Math.floorMod(dx + dz, 2) == 0) {
                    m(s, dx, y + 1, dz, endRod("up"));
                }
            }
        }
        for (int[] c : new int[][]{{0, 5}, {0, -5}, {5, 0}, {-5, 0}, {4, 3}, {-4, 3}, {4, -3}, {-4, -3}}) {
            m(s, c[0], y, c[1], Kit.TRIM);
            for (int h = y + 1; h < G_BEAM - 1; h++) {
                m(s, c[0], h, c[1], Kit.CARVED);
            }
            m(s, c[0], G_BEAM - 1, c[1], Kit.TRIM);
        }
        Kit.tube(s, G_X, G_BEAM, G_BEAM, G_Z, 11, Kit.TRIM);
        mPut(s, 0, y, 0, Kit.CARVED);
        m(s, 0, y + 1, 0, SEA_LANTERN);
        mPut(s, 0, y, -3, Kit.fence("spruce"));
        m(s, 0, y + 1, -3, TELESCOPE);
        mPut(s, -2, y, -2, CARTOGRAPHY);
        mPut(s, 2, y, -2, Kit.stairs(SPRUCE_STAIRS, "south"));
        mPut(s, 3, y, 1, AMETHYST);
        mPut(s, 3, y + 1, 1, CLUSTER);
        int top = roundRoof(s, G_X, G_EAVES, G_Z, 11, new int[]{1, 2, 3, 3, 3, 3}, Kit.SLATE);
        for (int[] side : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            lucarne(s, G_X, G_Z, G_EAVES + 1, 5, side[0], side[1]);
        }
        m(s, 0, top, 0, AMETHYST);
        m(s, 0, top + 1, 0, endRod("up"));
        chandelier(s, G_X, G_Z, G_BEAM, hollowTop(s, G_X, G_BEAM, G_Z));
    }

    /** The library wing of rank SS on the west side of the main tower: its outer walls and its two floors. */
    private static final int LW_X1 = 1;
    private static final int LW_X2 = 7;
    private static final int LW_Z1 = 2;
    private static final int LW_Z2 = 12;
    private static final int LW_UPPER = 6;
    private static final int LW_EAVES = 11;

    /**
     * The library wing of rank SS: a hall of stone of two storeys against the west side of the main tower, quoins of rough
     * stone, a band of trim at its upper floor, tall lancets of purple and magenta glass between buttresses, a roof of
     * blue-green tiles with gables of stone and a round window in the front gable.
     */
    private static void libraryWing(Structure s) {
        s.fill(LW_X1 + 1, 1, LW_Z1 + 1, LW_X2 - 1, LW_EAVES, LW_Z2 - 1, AIR);
        s.fill(LW_X1 + 1, 1, LW_Z1 + 1, LW_X2 - 1, 1, LW_Z2 - 1, PLANKS);
        stoneStorey(s, LW_X1, LW_Z1, LW_X2, LW_Z2, 1, LW_EAVES);
        s.walls(LW_X1, LW_UPPER, LW_Z1, LW_X2, LW_UPPER, LW_Z2, Kit.TRIM);
        s.walls(LW_X1, LW_EAVES, LW_Z1, LW_X2, LW_EAVES, LW_Z2, Kit.TRIM);
        s.fill(LW_X1 + 1, LW_UPPER, LW_Z1 + 1, LW_X2 - 1, LW_UPPER, LW_Z2 - 1, PLANKS);
        String[] glass = {pane(PURPLE), pane(MAGENTA), pane(PURPLE)};
        for (int z : new int[]{5, 9}) {
            lancet(s, LW_X1, z, 3, 8, false, glass);
        }
        lancet(s, 4, LW_Z1, 3, 8, true, glass);
        lancet(s, 4, LW_Z2, 3, 4, true, glass);
        for (int z : new int[]{LW_Z1, 7, LW_Z2}) {
            s.fill(LW_X1 - 1, 1, z, LW_X1 - 1, 7, z, Kit.ROUGH);
            s.set(LW_X1 - 1, 8, z, Kit.stairs(Kit.ROUGH_STAIRS, "east"));
        }
        int[] under = roofZ(s, LW_X1 - 1, LW_X2 + 1, LW_Z1 - 1, LW_Z2 + 1, LW_EAVES, 1, Kit.TEAL);
        gableAtZ(s, LW_X1 - 1, under, LW_Z2, LW_EAVES + 1, Kit.WALL);
        gableAtZ(s, LW_X1 - 1, under, LW_Z1, LW_EAVES + 1, Kit.WALL);
        s.set(4, 12, LW_Z2, pane(PURPLE)).set(3, 12, LW_Z2, pane(MAGENTA)).set(5, 12, LW_Z2, pane(MAGENTA)).set(4, 13, LW_Z2, pane(PURPLE));
        s.set(4, 14, LW_Z2, Kit.CARVED);
        s.set(4, 12, LW_Z1, pane(PURPLE)).set(4, 13, LW_Z1, pane(PURPLE)).set(4, 14, LW_Z1, Kit.CARVED);
    }

    /**
     * The rooms of the library wing of rank SS: the reading room below with shelves along its walls, a long reading table
     * with chairs and candles and the lectern, and the gallery of books above with a table of star maps.
     */
    private static void libraryRooms(Structure s) {
        int y = 2;
        for (int[] c : new int[][]{{2, 3}, {6, 3}, {2, 7}, {6, 4}, {6, 5}, {6, 6}, {2, 11}}) {
            if (s.get(c[0], y, c[1]).equals(AIR)) {
                s.fill(c[0], y, c[1], c[0], y + 2, c[1], BOOKSHELF);
            }
        }
        Furnish.longTable(s, 4, 5, 4, 9, y, "spruce");
        for (int z = 5; z <= 9; z++) {
            Furnish.chair(s, 3, y, z, "east", "spruce");
            if (z != 7) {
                Furnish.chair(s, 5, y, z, "west", "spruce");
            }
            s.set(4, y + 1, z, z % 2 == 1 ? Furnish.candles(2) : Kit.lantern());
        }
        put(s, 4, y, 10, lectern("north"));
        chandelier(s, 4, 6, 4, 5);
        chandelier(s, 4, 9, 4, 5);
        // The gallery of books.
        y = LW_UPPER + 1;
        for (int[] c : new int[][]{{2, 3}, {6, 3}, {2, 7}, {6, 4}, {6, 5}, {6, 6}, {6, 7}, {2, 11}, {6, 11}}) {
            if (s.get(c[0], y, c[1]).equals(AIR)) {
                s.fill(c[0], y, c[1], c[0], y + 2, c[1], BOOKSHELF);
            }
        }
        mapTable(s, 3, 6, 5, 6, y);
        put(s, 4, y, 8, CARTOGRAPHY);
        put(s, 4, y + 1, 8, Kit.lantern());
        Furnish.chair(s, 3, y, 8, "east", "spruce");
        put(s, 5, y, 9, AMETHYST);
        put(s, 5, y + 1, 9, CLUSTER);
        Furnish.rug(s, 3, 9, 4, 10, y, PURPLE_CARPET);
        chandelier(s, 4, 5, 10, 14);
        chandelier(s, 4, 10, 10, 14);
    }
}
