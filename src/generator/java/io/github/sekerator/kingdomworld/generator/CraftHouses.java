package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * Houses of the craftsmen's quarter of a capital: a narrow house whose upper floors jut over the street, a craftsman's house
 * with its workshop and yard, a house under a mansard roof and a house with a deep porch and a balcony. Each has a
 * silhouette of its own, and all are drawn with the blocks that stand for the parts of a look, so that two neighbours of
 * the same drawing still differ in stone, plaster, timber and roof.
 */
final class CraftHouses {
    private static final String SPRUCE_LOG = "minecraft:spruce_log";
    private static final String OAK_LOG = "minecraft:oak_log";
    private static final String SPRUCE_PLANKS = "minecraft:spruce_planks";
    private static final String OAK_PLANKS = "minecraft:oak_planks";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String OAK_STAIRS = "minecraft:oak_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String OAK_SLAB = "minecraft:oak_slab";
    private static final String DOOR = "minecraft:spruce_door";
    private static final String OAK_DOOR = "minecraft:oak_door";
    private static final String PRESSURE_PLATE = "minecraft:oak_pressure_plate[powered=false]";
    private static final String BOOKSHELF = "minecraft:bookshelf";
    private static final String CRAFTING_TABLE = "minecraft:crafting_table";
    private static final String HAY = "minecraft:hay_block[axis=y]";

    private CraftHouses() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(house("classic_house_jettied", "minecraft:white_terracotta", jettiedHouse()));
        list.add(house("classic_house_craftsman", "minecraft:crafting_table", workshopHouse()));
        list.add(house("classic_house_mansard", "minecraft:spruce_stairs", gambrelHouse()));
        list.add(house("classic_house_porch", "minecraft:oak_fence", porchHouse()));
        return list;
    }

    private static BuildingGenerator.Building house(String name, String icon, Structure structure) {
        return new BuildingGenerator.Building(name, "housing", "house", icon, (structure.sizeX - 1) / 2, 0, structure.sizeZ - 1,
                structure, "house", 1, "");
    }

    // ---------------------------------------------------------------- shared shapes

    private static String furnace(String block, String facing) {
        return "minecraft:" + block + "[facing=" + facing + ",lit=false]";
    }

    private static int rise(int[] rises, int row) {
        return row < rises.length ? rises[row] : 1;
    }

    /**
     * A gable roof whose ridge runs along z, so that its gables look at the street and at the back: rows of stairs rising
     * from the west eave ({@code x1}) and the east eave ({@code x2}). Row {@code r} lies {@code rises[r]} layers above the row
     * before it (one when the array is shorter); a row that rises two stands its stairs on a full block of the roof, so that
     * the slope is steep there. Where the rows meet, a ridge of slabs. Under the roof the gable walls are filled with
     * {@code gable} where nothing stands yet.
     *
     * @param y     height of the eaves
     * @param walls the rows of the gable walls
     */
    private static void gableZ(Structure s, int x1, int x2, int z1, int z2, int y, int[] rises, Kit.Roof roof, String gable,
                               int... walls) {
        int[] lowest = new int[x2 - x1 + 1];
        int height = y;
        for (int row = 0; x1 + row <= x2 - row; row++) {
            int up = row == 0 ? 0 : rise(rises, row);
            height += up;
            int west = x1 + row;
            int east = x2 - row;
            for (int extra = 1; extra < up; extra++) {
                s.fill(west, height - extra, z1, west, height - extra, z2, roof.block());
                s.fill(east, height - extra, z1, east, height - extra, z2, roof.block());
            }
            if (west < east) {
                s.fill(west, height, z1, west, height, z2, Kit.stairs(roof.stairs(), "east"));
                s.fill(east, height, z1, east, height, z2, Kit.stairs(roof.stairs(), "west"));
            } else {
                s.fill(west, height, z1, west, height, z2, Kit.slab(roof.slab()));
            }
            lowest[west - x1] = height - Math.max(0, up - 1);
            lowest[east - x1] = lowest[west - x1];
        }
        for (int wall : walls) {
            for (int x = x1 + 1; x < x2; x++) {
                for (int h = y; h < lowest[x - x1]; h++) {
                    if (!s.solid(x, h, wall)) {
                        s.set(x, h, wall, gable);
                    }
                }
            }
        }
    }

    /**
     * A gable roof whose ridge runs along x, so that its eaves look at the street and at the back: rows of stairs rising
     * from the back eave ({@code z1}) and the front eave ({@code z2}), each {@code rises[r]} layers above the row before it
     * as in {@link #gableZ}; the gable walls at the given columns are filled with {@code gable} under the roof.
     *
     * @param y     height of the eaves
     * @param walls the columns of the gable walls
     */
    private static void gableX(Structure s, int x1, int x2, int z1, int z2, int y, int[] rises, Kit.Roof roof, String gable,
                               int... walls) {
        int[] lowest = new int[z2 - z1 + 1];
        int height = y;
        for (int row = 0; z1 + row <= z2 - row; row++) {
            int up = row == 0 ? 0 : rise(rises, row);
            height += up;
            int back = z1 + row;
            int front = z2 - row;
            for (int extra = 1; extra < up; extra++) {
                s.fill(x1, height - extra, back, x2, height - extra, back, roof.block());
                s.fill(x1, height - extra, front, x2, height - extra, front, roof.block());
            }
            if (back < front) {
                s.fill(x1, height, back, x2, height, back, Kit.stairs(roof.stairs(), "south"));
                s.fill(x1, height, front, x2, height, front, Kit.stairs(roof.stairs(), "north"));
            } else {
                s.fill(x1, height, back, x2, height, back, Kit.slab(roof.slab()));
            }
            lowest[back - z1] = height - Math.max(0, up - 1);
            lowest[front - z1] = lowest[back - z1];
        }
        for (int wall : walls) {
            for (int z = z1 + 1; z < z2; z++) {
                for (int h = y; h < lowest[z - z1]; h++) {
                    if (!s.solid(wall, h, z)) {
                        s.set(wall, h, z, gable);
                    }
                }
            }
        }
    }

    /**
     * A hip roof: rings of stairs rising one block per ring from the eaves on all four sides, turned stairs at the corners,
     * closed with slabs where the rings meet.
     *
     * @param y height of the eaves
     */
    private static void hipRoof(Structure s, int x1, int x2, int z1, int z2, int y, Kit.Roof roof) {
        int ring = 0;
        while (x2 - ring - (x1 + ring) >= 2 && z2 - ring - (z1 + ring) >= 2) {
            int west = x1 + ring;
            int east = x2 - ring;
            int back = z1 + ring;
            int front = z2 - ring;
            int h = y + ring;
            s.fill(west, h, back, east, h, back, Kit.stairs(roof.stairs(), "south"));
            s.fill(west, h, front, east, h, front, Kit.stairs(roof.stairs(), "north"));
            s.fill(west, h, back + 1, west, h, front - 1, Kit.stairs(roof.stairs(), "east"));
            s.fill(east, h, back + 1, east, h, front - 1, Kit.stairs(roof.stairs(), "west"));
            s.set(west, h, back, Kit.stairs(roof.stairs(), "south", "outer_left"));
            s.set(east, h, back, Kit.stairs(roof.stairs(), "south", "outer_right"));
            s.set(west, h, front, Kit.stairs(roof.stairs(), "north", "outer_right"));
            s.set(east, h, front, Kit.stairs(roof.stairs(), "north", "outer_left"));
            ring++;
        }
        if (x1 + ring <= x2 - ring && z1 + ring <= z2 - ring) {
            s.fill(x1 + ring, y + ring, z1 + ring, x2 - ring, y + ring, z2 - ring, Kit.slab(roof.slab()));
        }
    }

    /**
     * A small gabled window in the front slope of a roof whose ridge runs along x: the pane at {@code (x, y, z)} in place of
     * the roof between two cheeks, a gable over it and a little roof of stairs running back into the slope, open behind.
     */
    private static void dormer(Structure s, int x, int y, int z, Kit.Roof roof, String wall) {
        s.set(x - 1, y, z, wall).set(x + 1, y, z, wall).set(x, y, z, Kit.GLASS).set(x, y + 1, z, wall);
        s.fill(x - 1, y + 1, z - 1, x - 1, y + 1, z, Kit.stairs(roof.stairs(), "east"));
        s.fill(x + 1, y + 1, z - 1, x + 1, y + 1, z, Kit.stairs(roof.stairs(), "west"));
        s.fill(x, y + 2, z - 1, x, y + 2, z, Kit.slab(roof.slab()));
        s.set(x, y + 1, z - 1, Structure.AIR).set(x, y, z - 1, Structure.AIR);
    }

    /**
     * A table of a fence post with a pressure plate on it.
     */
    private static void table(Structure s, int x, int y, int z) {
        s.set(x, y, z, Kit.fence("spruce")).set(x, y + 1, z, PRESSURE_PLATE);
    }

    /**
     * A lantern hanging on a chain: the lantern at {@code y}, the chain above it up to {@code top}.
     */
    private static void chandelier(Structure s, int x, int y, int z, int top) {
        s.set(x, y, z, Kit.hangingLantern());
        for (int h = y + 1; h <= top; h++) {
            s.set(x, h, z, Kit.chain());
        }
    }

    // ---------------------------------------------------------------- the craftsman's house

    /**
     * A craftsman's house laid out as an L around a yard. At the back stands the house of two floors, its long roof of
     * tiles to the street: a ground floor of rubble with dressed quoins, an upper floor of boards between posts, gables of
     * plaster, a dormer over the door and a chimney stack against the east gable. In front of its west half the workshop
     * reaches to the street, one floor under its own small gable with the banner of the house hung from the king post like
     * the sign of a shop; it is open at the front and to the yard: a workbench and a smithing table along the wall with tools
     * hung over them, a grindstone at the corner and a lantern from the ridge. The yard in the other corner, fenced low on the
     * street and the side, holds stacked boards, a log on a sawhorse and a chopping block, lit by a lantern on the corner
     * post; the house door opens onto it under a little pent roof. Inside: a kitchen with a hearth and a table below, a stair,
     * and two beds under the open roof above.
     */
    static Structure workshopHouse() {
        Structure s = new Structure(11, 14, 11);
        String post = Kit.log(SPRUCE_LOG, "y");
        Kit.Roof roof = Kit.TILE;
        // ---- the house: x 1..9, z 1..6
        s.fill(1, 0, 1, 9, 0, 6, Kit.ROUGH).fill(2, 0, 2, 8, 0, 5, SPRUCE_PLANKS);
        s.walls(1, 1, 1, 9, 3, 6, Kit.ROUGH);
        for (int[] c : new int[][]{{1, 1}, {9, 1}, {1, 6}, {9, 6}}) {
            s.fill(c[0], 1, c[1], c[0], 3, c[1], Kit.WALL);
        }
        s.beam(1, 1, 9, 6, 4, SPRUCE_LOG).fill(2, 4, 2, 8, 4, 5, SPRUCE_PLANKS);
        s.walls(1, 5, 1, 9, 7, 6, OAK_PLANKS);
        for (int x : new int[]{1, 5, 9}) {
            s.fill(x, 5, 1, x, 7, 1, post).fill(x, 5, 6, x, 7, 6, post);
        }
        s.beam(1, 1, 9, 6, 8, SPRUCE_LOG);
        gableX(s, 0, 9, 0, 7, 8, new int[]{0}, roof, Kit.PLASTER, 1, 9);
        dormer(s, 7, 9, 6, roof, Kit.PLASTER);
        // The chimney stack outside the east gable, the hearth in the wall inside it.
        s.fill(10, 0, 2, 10, 3, 4, Kit.CHIMNEY);
        s.set(10, 4, 2, Kit.stairs(Kit.CHIMNEY_STAIRS, "south")).set(10, 4, 4, Kit.stairs(Kit.CHIMNEY_STAIRS, "north"));
        s.fill(10, 4, 3, 10, 12, 3, Kit.CHIMNEY).set(10, 13, 3, Kit.CHIMNEY_POT);
        s.set(9, 1, 3, furnace("furnace", "west")).set(9, 1, 4, furnace("smoker", "west"));
        s.fill(8, 3, 3, 8, 3, 4, Kit.upside(Kit.TRIM_STAIRS, "east"));
        // Doors and windows; lintels of trim over the windows of the ground floor.
        s.door(7, 1, 6, DOOR, "north", "left");
        s.set(7, 0, 7, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        s.fill(6, 3, 7, 8, 3, 7, Kit.stairs(roof.stairs(), "north"));
        s.set(6, 2, 7, Kit.hangingLantern());
        s.door(3, 1, 6, OAK_DOOR, "north", "left");
        for (int[] w : new int[][]{{8, 6}, {7, 1}, {8, 1}, {2, 1}}) {
            s.set(w[0], 2, w[1], Kit.GLASS).set(w[0], 3, w[1], Kit.TRIM);
        }
        s.set(1, 2, 4, Kit.GLASS).set(1, 3, 4, Kit.TRIM);
        for (int[] w : new int[][]{{6, 6}, {8, 6}, {3, 1}, {7, 1}}) {
            s.set(w[0], 6, w[1], Kit.GLASS);
        }
        s.set(1, 6, 3, Kit.GLASS).set(1, 6, 4, Kit.GLASS).set(9, 6, 2, Kit.GLASS).set(9, 6, 5, Kit.GLASS);
        s.fill(1, 9, 3, 1, 9, 4, Kit.GLASS);
        // ---- the workshop: x 1..5, z 7..9, open at the front and toward the yard
        s.fill(1, 0, 7, 5, 0, 9, Kit.ROUGH).fill(2, 0, 7, 4, 0, 9, SPRUCE_PLANKS);
        s.fill(1, 1, 7, 1, 3, 8, OAK_PLANKS);
        s.fill(1, 1, 9, 1, 3, 9, post).fill(5, 1, 9, 5, 3, 9, post);
        s.fill(1, 4, 7, 1, 4, 9, Kit.log(SPRUCE_LOG, "z")).fill(5, 4, 7, 5, 4, 9, Kit.log(SPRUCE_LOG, "z"));
        s.fill(2, 4, 9, 4, 4, 9, Kit.log(SPRUCE_LOG, "x"));
        gableZ(s, 0, 6, 7, 10, 4, new int[]{0}, roof, Kit.PLASTER, 9);
        s.fill(3, 5, 9, 3, 6, 9, post);
        // The banner of the house hangs from the king post over the open front, like the sign of a shop.
        s.set(3, 5, 10, Kit.wallBanner("red", "south"));
        // The bench along the wall with the tools hung over it, the grindstone at the open corner.
        s.set(2, 1, 7, CRAFTING_TABLE).set(2, 1, 8, Kit.topSlab(SPRUCE_SLAB)).set(2, 1, 9, "minecraft:smithing_table");
        s.set(2, 2, 7, "minecraft:lever[face=wall,facing=east,powered=false]");
        s.set(2, 2, 8, "minecraft:tripwire_hook[attached=false,facing=east,powered=false]");
        s.set(2, 3, 8, "minecraft:lightning_rod[facing=east,powered=false,waterlogged=false]");
        s.set(2, 3, 7, "minecraft:tripwire_hook[attached=false,facing=east,powered=false]");
        s.set(4, 1, 9, "minecraft:grindstone[face=floor,facing=south]").set(4, 1, 7, Kit.barrel());
        chandelier(s, 3, 4, 8, 6);
        // ---- the yard: x 6..10, z 7..10
        // A low fence round the yard with a lantern on its corner post, the way to the door left open; a stack of boards
        // against the fence, a log on a sawhorse and a chopping block.
        s.fill(10, 0, 7, 10, 0, 10, Kit.fence("spruce")).fill(8, 0, 10, 9, 0, 10, Kit.fence("spruce"));
        s.set(10, 1, 10, Kit.lantern());
        s.fill(9, 0, 7, 9, 0, 8, OAK_PLANKS).fill(9, 1, 7, 9, 1, 8, Kit.slab(OAK_SLAB));
        s.set(8, 0, 7, Kit.slab(SPRUCE_SLAB));
        s.set(8, 0, 9, Kit.fence("spruce")).set(9, 0, 9, Kit.fence("spruce"));
        s.set(8, 1, 9, Kit.log(OAK_LOG, "x")).set(9, 1, 9, Kit.log(OAK_LOG, "x"));
        s.set(8, 0, 8, Kit.log("minecraft:stripped_oak_log", "y"));
        // ---- inside the house
        // A stair along the back wall, cupboards under it, a rail round its opening above.
        for (int i = 0; i < 4; i++) {
            s.set(6 - i, 1 + i, 2, Kit.stairs(OAK_STAIRS, "west"));
        }
        s.set(4, 4, 2, Structure.AIR).set(5, 4, 2, Structure.AIR);
        s.set(5, 1, 2, Kit.barrel()).set(4, 1, 2, Kit.barrel()).set(4, 2, 2, SPRUCE_PLANKS);
        s.set(3, 1, 2, BOOKSHELF).set(3, 2, 2, BOOKSHELF).set(3, 3, 2, SPRUCE_PLANKS);
        s.set(4, 5, 3, Kit.fence("spruce")).set(5, 5, 3, Kit.fence("spruce")).set(6, 5, 2, Kit.fence("spruce"));
        // The kitchen and the table.
        s.set(8, 1, 2, Kit.barrel()).set(8, 1, 5, "minecraft:cauldron");
        table(s, 5, 1, 4);
        s.set(4, 1, 4, Kit.stairs(OAK_STAIRS, "west")).set(6, 1, 4, Kit.stairs(OAK_STAIRS, "east"));
        s.set(5, 3, 3, Kit.hangingLantern()).set(2, 3, 5, Kit.wallTorch("east"));
        s.set(2, 1, 5, Kit.chest("east"));
        // The bedroom under the roof: two beds, a chest, a bookshelf and a lantern on a chain from the ridge.
        s.bed(7, 5, 3, "minecraft:green_bed", "north", 0, -1).bed(8, 5, 3, "minecraft:green_bed", "north", 0, -1);
        s.set(8, 5, 5, Kit.chest("west")).set(2, 5, 5, BOOKSHELF).set(3, 5, 5, Kit.barrel());
        s.fill(6, 5, 4, 7, 5, 4, Kit.CLOTH_CARPET);
        chandelier(s, 5, 7, 4, 10);
        s.connect();
        return s;
    }

    // ---------------------------------------------------------------- the house with a mansard

    /**
     * A stone house under a gambrel roof whose gable looks at the street. The roof starts right on the band of trim over the
     * ground floor: its lower slopes are steep, two rows of stairs standing on full blocks, its upper ones run at the common
     * pitch, so that the upper floor lives inside the roof; the gables are rendered in plaster with tall windows under lintels
     * of trim and an attic window under a carved keystone. The house stands on a high plinth of rough stone with quoins of
     * trim; a stoop of trim steps between two stone cheeks with lanterns leads up to a door framed by pilasters under a hood
     * with a small pediment. A wide dormer with two windows sits in each lower slope, and a chimney stack climbs the back
     * gable. Inside: a parlour and a kitchen below, a stair along the wall, and three beds under the high roof above.
     */
    static Structure gambrelHouse() {
        Structure s = new Structure(9, 15, 11);
        Kit.Roof roof = Kit.TEAL;
        // The plinth of rough stone, two layers high, and the floor on it.
        s.fill(1, 0, 1, 7, 1, 8, Kit.ROUGH).fill(2, 1, 2, 6, 1, 7, SPRUCE_PLANKS);
        // The ground floor of stone with quoins of trim, the band of trim that carries the eaves and the upper floor.
        s.walls(1, 2, 1, 7, 4, 8, Kit.WALL);
        for (int[] c : new int[][]{{1, 1}, {7, 1}, {1, 8}, {7, 8}}) {
            s.set(c[0], 2, c[1], Kit.TRIM).set(c[0], 4, c[1], Kit.TRIM);
        }
        s.walls(1, 5, 1, 7, 5, 8, Kit.TRIM).fill(2, 5, 2, 6, 5, 7, SPRUCE_PLANKS);
        // The gambrel roof from the band up: a steep lower slope of three and two layers a row, then the common pitch; its
        // gables rendered in plaster, the back flush for the chimney.
        gableZ(s, 0, 8, 1, 9, 5, new int[]{0, 3, 2, 1, 1}, roof, Kit.PLASTER, 1, 8);
        // A wide dormer with two windows in the middle of each steep lower slope.
        wideDormer(s, 1, 6, 3, 6, roof);
        wideDormer(s, 7, 6, 3, 6, roof);
        // The chimney stack on the back gable.
        s.fill(3, 0, 0, 5, 3, 0, Kit.CHIMNEY);
        s.set(3, 4, 0, Kit.stairs(Kit.CHIMNEY_STAIRS, "east")).set(5, 4, 0, Kit.stairs(Kit.CHIMNEY_STAIRS, "west"));
        s.fill(4, 4, 0, 4, 13, 0, Kit.CHIMNEY).set(4, 14, 0, Kit.CHIMNEY_POT);
        // The stoop: steps of trim between stone cheeks that carry lanterns.
        s.set(4, 0, 9, Kit.ROUGH).set(4, 1, 9, Kit.stairs(Kit.TRIM_STAIRS, "north")).set(4, 0, 10, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        for (int x : new int[]{3, 5}) {
            s.fill(x, 0, 9, x, 1, 9, Kit.WALL).set(x, 0, 10, Kit.slab(Kit.WALL_SLAB)).set(x, 2, 9, Kit.lantern());
        }
        // The door between pilasters of trim; tall windows between sills and lintels of trim.
        s.door(4, 2, 8, DOOR, "north", "left");
        s.fill(3, 2, 8, 3, 3, 8, Kit.TRIM).fill(5, 2, 8, 5, 3, 8, Kit.TRIM);
        // A hood over the door: a cornice of trim and a small pediment on it, the lanterns of the stoop under its ends.
        s.fill(3, 4, 9, 5, 4, 9, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(3, 5, 9, Kit.stairs(Kit.TRIM_STAIRS, "east")).set(5, 5, 9, Kit.stairs(Kit.TRIM_STAIRS, "west"));
        s.set(4, 5, 9, Kit.TRIM).set(4, 6, 9, Kit.slab(Kit.TRIM_SLAB));
        for (int[] w : new int[][]{{2, 8}, {6, 8}, {2, 1}, {6, 1}, {1, 3}, {1, 6}, {7, 3}, {7, 6}}) {
            tallWindow(s, w[0], 2, w[1]);
        }
        // The gables: two windows of the upper floor under lintels of trim, the attic window under a keystone.
        for (int z : new int[]{1, 8}) {
            for (int x : new int[]{3, 5}) {
                s.fill(x, 6, z, x, 7, z, Kit.GLASS).set(x, 8, z, Kit.TRIM);
            }
        }
        s.fill(4, 9, 8, 4, 10, 8, Kit.GLASS).set(4, 11, 8, Kit.CARVED);
        // The parlour and the kitchen: a hearth in the back wall under a hood, a table with a lamp, a bookshelf.
        s.set(4, 2, 1, furnace("furnace", "south")).set(3, 2, 2, furnace("smoker", "south")).set(2, 2, 2, Kit.barrel());
        s.set(5, 2, 2, Kit.barrel());
        s.fill(3, 4, 2, 5, 4, 2, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(3, 2, 5, Kit.fence("spruce")).set(3, 3, 5, Kit.lantern());
        s.set(2, 2, 5, Kit.stairs(OAK_STAIRS, "west")).set(4, 2, 5, Kit.stairs(OAK_STAIRS, "east"));
        s.set(2, 2, 7, BOOKSHELF).set(2, 3, 7, BOOKSHELF);
        s.set(4, 4, 4, Kit.hangingLantern());
        // A stair along the east wall, cupboards under it, a rail round its opening above.
        for (int i = 0; i < 4; i++) {
            s.set(6, 2 + i, 6 - i, Kit.stairs(OAK_STAIRS, "north"));
        }
        s.set(6, 5, 4, Structure.AIR).set(6, 5, 5, Structure.AIR);
        s.set(6, 2, 5, Kit.barrel()).set(6, 2, 4, Kit.barrel()).set(6, 3, 4, SPRUCE_PLANKS);
        s.set(6, 2, 3, Kit.chest("west")).fill(6, 3, 3, 6, 4, 3, SPRUCE_PLANKS);
        s.set(5, 6, 4, Kit.fence("spruce")).set(5, 6, 5, Kit.fence("spruce")).set(6, 6, 6, Kit.fence("spruce"));
        // Three beds under the roof, a chest and a lamp, a lantern on a chain from the ridge.
        s.bed(2, 6, 3, "minecraft:light_blue_bed", "north", 0, -1).bed(3, 6, 3, "minecraft:light_blue_bed", "north", 0, -1);
        s.bed(2, 6, 6, "minecraft:light_blue_bed", "south", 0, 1);
        s.set(4, 6, 2, Kit.barrel()).set(4, 7, 2, Kit.lantern()).set(4, 6, 7, Kit.chest("north"));
        s.fill(3, 6, 5, 4, 6, 5, Kit.CLOTH_CARPET);
        chandelier(s, 4, 8, 4, 11);
        s.connect();
        return s;
    }

    /**
     * A window two blocks high in a wall between a sill and a lintel of trim set in the wall.
     */
    private static void tallWindow(Structure s, int x, int y, int z) {
        s.fill(x, y, z, x, y + 1, z, Kit.GLASS).set(x, y + 2, z, Kit.TRIM).set(x, y - 1, z, Kit.TRIM);
    }

    /**
     * A small dormer in the slope of a roof whose ridge runs along z, standing over the wall at column {@code x}: a window of
     * {@code height} blocks in place of the roof, a gable of plaster over it, a little roof of stairs and a ridge slab.
     */
    private static void sideDormer(Structure s, int x, int y, int z, int height, Kit.Roof roof) {
        int top = y + height;
        s.fill(x, y, z, x, top - 1, z, Kit.GLASS).set(x, top, z, Kit.PLASTER);
        s.set(x, top, z - 1, Kit.stairs(roof.stairs(), "south")).set(x, top, z + 1, Kit.stairs(roof.stairs(), "north"));
        s.set(x, top + 1, z, Kit.slab(roof.slab()));
    }

    /**
     * A wide dormer in the steep slope of a roof whose ridge runs along z, standing over the wall at column {@code x} from row
     * {@code z1} to row {@code z2}: windows two blocks high between cheeks of plaster, and over them a gable of plaster under
     * a little roof of stairs that rises from both ends.
     */
    private static void wideDormer(Structure s, int x, int y, int z1, int z2, Kit.Roof roof) {
        s.fill(x, y, z1, x, y + 1, z2, Kit.PLASTER).fill(x, y, z1 + 1, x, y + 1, z2 - 1, Kit.GLASS);
        for (int row = 0; z1 + row <= z2 - row; row++) {
            int h = y + 2 + row;
            int a = z1 + row;
            int b = z2 - row;
            if (a == b) {
                s.set(x, h, a, Kit.slab(roof.slab()));
                break;
            }
            s.set(x, h, a, Kit.stairs(roof.stairs(), "south")).set(x, h, b, Kit.stairs(roof.stairs(), "north"));
            if (b - a > 1) {
                s.fill(x, h, a + 1, x, h, b - 1, Kit.PLASTER);
            }
        }
    }

    // ---------------------------------------------------------------- the house with a porch

    /**
     * A square house of two floors under a pyramid of a hip roof of dark shingles, rendered in plaster on a footing of
     * rough stone, with a band of trim at the floor of the upper storey and a cornice of trim under the eaves. A deep
     * porch runs the whole front on four posts with rails between them and a bench by the door; its roof is the floor
     * of a balcony with a railing and lanterns, reached by a door from the upper floor. Every window has shutters,
     * those of the sides flower boxes under them; a dormer looks out of the front slope and a chimney stack rises from
     * the top of the roof. Inside: a kitchen and a parlour below, a stair, and three beds under the open roof above.
     */
    static Structure porchHouse() {
        Structure s = new Structure(9, 16, 11);
        Kit.Roof roof = Kit.SHINGLE;
        String rail = Kit.fence("oak");
        // The footing and the floor, the walls of plaster, a band of trim between the floors and a cornice of trim on top.
        s.fill(1, 0, 1, 7, 0, 7, Kit.ROUGH).fill(2, 0, 2, 6, 0, 6, SPRUCE_PLANKS);
        s.walls(1, 1, 1, 7, 3, 7, Kit.PLASTER).walls(1, 5, 1, 7, 7, 7, Kit.PLASTER);
        s.walls(1, 4, 1, 7, 4, 7, Kit.TRIM).fill(2, 4, 2, 6, 4, 6, SPRUCE_PLANKS);
        s.walls(1, 8, 1, 7, 8, 7, Kit.TRIM);
        // The hip roof, its top a full block that carries the chimney stack, a dormer in its front slope.
        hipRoof(s, 0, 8, 0, 8, 8, roof);
        s.set(4, 12, 4, roof.block()).fill(4, 13, 4, 4, 14, 4, Kit.CHIMNEY).set(4, 15, 4, Kit.CHIMNEY_POT);
        dormer(s, 4, 9, 7, roof, Kit.PLASTER);
        // The porch: a deck edged with rough stone, a step of trim, four slender posts with rails between them, a valance
        // of slabs under the beam, a bench and a barrel by the door, a lantern under the balcony.
        s.fill(1, 0, 8, 7, 0, 9, Kit.ROUGH).fill(2, 0, 8, 6, 0, 8, OAK_PLANKS);
        s.set(4, 0, 10, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        for (int x : new int[]{1, 3, 5, 7}) {
            s.fill(x, 1, 9, x, 3, 9, rail);
        }
        s.set(2, 1, 9, rail).set(6, 1, 9, rail).set(1, 1, 8, rail).set(7, 1, 8, rail);
        for (int x : new int[]{2, 4, 6}) {
            s.set(x, 3, 9, Kit.topSlab(OAK_SLAB));
        }
        s.set(2, 1, 8, Kit.stairs(OAK_STAIRS, "north")).set(3, 1, 8, Kit.stairs(OAK_STAIRS, "north"));
        s.set(6, 1, 8, Kit.barrel()).set(6, 2, 8, "minecraft:flower_pot");
        s.set(4, 3, 8, Kit.hangingLantern());
        // The balcony on the porch: a floor of planks on beams, a railing with a lantern at each front corner.
        s.fill(1, 4, 9, 7, 4, 9, Kit.log(OAK_LOG, "x")).set(1, 4, 8, Kit.log(OAK_LOG, "z")).set(7, 4, 8, Kit.log(OAK_LOG, "z"));
        s.fill(2, 4, 8, 6, 4, 8, SPRUCE_PLANKS);
        s.fill(1, 5, 9, 7, 5, 9, rail).set(1, 5, 8, rail).set(7, 5, 8, rail);
        s.set(1, 6, 9, Kit.lantern()).set(7, 6, 9, Kit.lantern());
        // Doors with a pane over them, tall windows with shutters on both sides, flower boxes under those of the ground floor.
        s.door(4, 1, 7, DOOR, "north", "left").set(4, 3, 7, Kit.GLASS);
        s.door(4, 5, 7, DOOR, "north", "left").set(4, 7, 7, Kit.GLASS);
        for (int y : new int[]{2, 6}) {
            for (int x : new int[]{2, 6}) {
                shuttered(s, x, y, 7, "south");
            }
            shuttered(s, 1, y, 4, "west");
            shuttered(s, 7, y, 4, "east");
        }
        shuttered(s, 5, 2, 1, "north");
        shuttered(s, 2, 6, 1, "north");
        shuttered(s, 6, 6, 1, "north");
        flowerBox(s, 0, 1, 4, "west", "minecraft:poppy");
        flowerBox(s, 8, 1, 4, "east", "minecraft:dandelion");
        flowerBox(s, 5, 1, 0, "north", "minecraft:allium");
        // The stair along the back wall, a cupboard and a chest under it, a rail round its opening above.
        for (int i = 0; i < 4; i++) {
            s.set(5 - i, 1 + i, 2, Kit.stairs(OAK_STAIRS, "west"));
        }
        s.set(3, 4, 2, Structure.AIR).set(4, 4, 2, Structure.AIR);
        s.set(4, 1, 2, Kit.barrel()).set(3, 1, 2, Kit.barrel()).set(3, 2, 2, SPRUCE_PLANKS);
        s.set(2, 1, 2, Kit.chest("south")).fill(2, 2, 2, 2, 3, 2, SPRUCE_PLANKS);
        s.set(3, 5, 3, rail).set(4, 5, 3, rail).set(5, 5, 2, rail);
        // The kitchen against the east wall under a hood, the parlour table under a lantern, a bookshelf by the door.
        s.set(6, 1, 2, furnace("smoker", "west")).set(6, 1, 3, furnace("furnace", "west")).set(6, 1, 4, Kit.barrel());
        s.fill(6, 3, 2, 6, 3, 3, Kit.upside(Kit.TRIM_STAIRS, "east"));
        table(s, 4, 1, 4);
        s.set(3, 1, 4, Kit.stairs(OAK_STAIRS, "west")).set(5, 1, 4, Kit.stairs(OAK_STAIRS, "east"));
        s.set(4, 1, 5, Kit.stairs(OAK_STAIRS, "south"));
        s.set(2, 1, 6, BOOKSHELF).set(2, 2, 6, BOOKSHELF).set(6, 1, 6, Kit.chest("west"));
        s.set(4, 3, 4, Kit.hangingLantern());
        // Three beds under the open roof, a nightstand with a lamp, a chest, a lantern on a chain from the top.
        s.bed(6, 5, 3, "minecraft:yellow_bed", "north", 0, -1).bed(6, 5, 5, "minecraft:yellow_bed", "south", 0, 1);
        s.bed(2, 5, 5, "minecraft:yellow_bed", "south", 0, 1);
        s.set(6, 5, 4, Kit.barrel()).set(6, 6, 4, Kit.lantern()).set(3, 5, 6, Kit.chest("north"));
        s.fill(3, 5, 4, 5, 5, 5, Kit.CLOTH_CARPET);
        chandelier(s, 4, 8, 4, 11);
        s.connect();
        return s;
    }

    private static int dx(String side) {
        return side.equals("east") ? 1 : side.equals("west") ? -1 : 0;
    }

    private static int dz(String side) {
        return side.equals("south") ? 1 : side.equals("north") ? -1 : 0;
    }

    private static String opposite(String side) {
        return switch (side) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /**
     * A window two blocks high with a shutter standing open on each side of it, against the wall.
     *
     * @param out the side the wall looks to
     */
    private static void shuttered(Structure s, int x, int y, int z, String out) {
        int dx = dx(out);
        int dz = dz(out);
        // Along the wall: x for walls that look south or north, z for walls that look east or west.
        int ax = dz != 0 ? 1 : 0;
        int az = dx != 0 ? 1 : 0;
        for (int h = y; h <= y + 1; h++) {
            s.set(x, h, z, Kit.GLASS);
            s.set(x - ax + dx, h, z - az + dz, Kit.shutter(Kit.SHUTTER, out));
            s.set(x + ax + dx, h, z + az + dz, Kit.shutter(Kit.SHUTTER, out));
        }
    }

    /**
     * A flower box under a window, standing out of the wall: a block of moss with a flower on it, a corbel of trim under it
     * and, where the box leaves room, a shutter as its front.
     *
     * @param out the side the wall looks to
     */
    private static void flowerBox(Structure s, int x, int y, int z, String out, String flower) {
        s.set(x, y - 1, z, Kit.upside(Kit.TRIM_STAIRS, opposite(out)));
        s.set(x, y, z, "minecraft:moss_block").set(x, y + 1, z, flower);
        if (s.inside(x + dx(out), y, z + dz(out))) {
            s.set(x + dx(out), y, z + dz(out), Kit.shutter(Kit.SHUTTER, out));
        }
    }

    // ---------------------------------------------------------------- the jettied house

    /**
     * A narrow town house of three levels standing with its steep gable to the street. The ground floor is of stone with
     * quoins of rough stone, windows under lintels of trim, a door under a carved keystone and a flower box under each front
     * window. The upper floor is a timber frame with plaster between the posts, tall windows and braces; it juts one block
     * over the street on a row of brackets, and the gable over it juts once more, so that the front steps forward floor by
     * floor. A lantern hangs under the jetty over the doorstep, a tall narrow window and a small dormer in each slope light
     * the attic, a finial stands on the front end of the ridge and a chimney stack climbs the back wall. Inside: a kitchen
     * with a hearth below, a bedroom with two beds above and a store room in the attic, joined by ladders.
     */
    static Structure jettiedHouse() {
        Structure s = new Structure(7, 18, 11);
        String post = Kit.log(SPRUCE_LOG, "y");
        // The footing and the floor of the ground floor; a step of trim at the door.
        s.fill(1, 0, 1, 5, 0, 7, Kit.ROUGH).fill(2, 0, 2, 4, 0, 6, SPRUCE_PLANKS);
        s.set(3, 0, 8, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        // The ground floor: walls of stone with quoins of rough stone.
        s.walls(1, 1, 1, 5, 3, 7, Kit.WALL);
        for (int[] c : new int[][]{{1, 1}, {5, 1}, {1, 7}, {5, 7}}) {
            s.fill(c[0], 1, c[1], c[0], 3, c[1], Kit.ROUGH);
        }
        // The door under its keystone, the windows under lintels of trim, sills on the sides, flower boxes in front.
        s.door(3, 1, 7, DOOR, "north", "left");
        s.set(3, 3, 7, Kit.CARVED);
        for (int x : new int[]{2, 4}) {
            s.set(x, 2, 7, Kit.GLASS).set(x, 3, 7, Kit.TRIM);
        }
        for (int[] w : new int[][]{{1, 3}, {1, 5}, {5, 3}}) {
            s.set(w[0], 1, w[1], Kit.TRIM).set(w[0], 2, w[1], Kit.GLASS).set(w[0], 3, w[1], Kit.TRIM);
        }
        flowerBox(s, 2, 1, 8, "south", "minecraft:poppy");
        flowerBox(s, 4, 1, 8, "south", "minecraft:azure_bluet");
        // The first jetty: the floor of the upper storey reaches one row over the street on a row of brackets; the lantern
        // hangs between the middle ones over the doorstep.
        s.beam(1, 1, 5, 8, 4, SPRUCE_LOG).fill(2, 4, 2, 4, 4, 7, SPRUCE_PLANKS);
        for (int x : new int[]{1, 2, 4, 5}) {
            s.set(x, 3, 8, Kit.upside(SPRUCE_STAIRS, "north"));
        }
        s.set(3, 3, 8, Kit.hangingLantern());
        // The upper storey, four blocks high: posts at the corners and beside the windows, plaster between them.
        s.walls(1, 5, 1, 5, 8, 8, Kit.PLASTER);
        for (int x : new int[]{1, 5}) {
            for (int z : new int[]{1, 3, 6, 8}) {
                s.fill(x, 5, z, x, 8, z, post);
            }
            // A window of four panes in each side, knee braces under the plate in the narrow panels.
            s.fill(x, 6, 4, x, 7, 5, Kit.GLASS);
            s.set(x, 8, 2, Kit.upside(SPRUCE_STAIRS, "north")).set(x, 8, 7, Kit.upside(SPRUCE_STAIRS, "south"));
            s.set(x == 1 ? 2 : 4, 8, 2, Kit.PLASTER).set(x == 1 ? 2 : 4, 8, 7, Kit.PLASTER);
        }
        // The front: two tall windows, braces under them leaning on the corner posts, backed inside by plaster.
        for (int x : new int[]{2, 4}) {
            s.fill(x, 6, 8, x, 7, 8, Kit.GLASS).fill(x, 6, 1, x, 7, 1, Kit.GLASS);
            s.set(x, 5, 7, Kit.PLASTER);
        }
        s.fill(3, 5, 1, 3, 8, 1, post);
        s.set(2, 5, 8, Kit.stairs(SPRUCE_STAIRS, "west")).set(4, 5, 8, Kit.stairs(SPRUCE_STAIRS, "east"));
        // The second jetty: the attic floor reaches one row further on brackets under the posts, and the gable stands on it.
        s.beam(1, 1, 5, 9, 9, SPRUCE_LOG).fill(2, 9, 2, 4, 9, 8, SPRUCE_PLANKS);
        for (int x : new int[]{1, 3, 5}) {
            s.set(x, 8, 9, Kit.upside(SPRUCE_STAIRS, "north"));
        }
        // The steep roof, its gable to the street, overhanging the front gable by one row.
        gableZ(s, 0, 6, 1, 10, 9, new int[]{0, 2, 2, 2}, Kit.TILE, Kit.PLASTER, 1, 9);
        // The timbers of the gables: a king post over the tall attic window in front, braces beside it.
        s.fill(3, 12, 9, 3, 13, 9, post).fill(3, 10, 9, 3, 11, 9, Kit.GLASS);
        s.set(2, 10, 9, Kit.stairs(SPRUCE_STAIRS, "west")).set(4, 10, 9, Kit.stairs(SPRUCE_STAIRS, "east"));
        s.set(2, 10, 8, Kit.PLASTER).set(4, 10, 8, Kit.PLASTER);
        s.fill(3, 10, 1, 3, 13, 1, post);
        // A small dormer in each slope over the attic, and a finial on the front end of the ridge.
        sideDormer(s, 1, 10, 5, 1, Kit.TILE);
        sideDormer(s, 5, 10, 5, 1, Kit.TILE);
        s.set(3, 16, 10, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        // The chimney stack against the back wall, rising over the ridge.
        s.fill(2, 0, 0, 4, 2, 0, Kit.CHIMNEY);
        s.set(2, 3, 0, Kit.stairs(Kit.CHIMNEY_STAIRS, "east")).set(4, 3, 0, Kit.stairs(Kit.CHIMNEY_STAIRS, "west"));
        s.fill(3, 3, 0, 3, 16, 0, Kit.CHIMNEY).set(3, 17, 0, Kit.CHIMNEY_POT);
        // The kitchen: a hearth at the back under a hood of trim, a table with a lamp, a chair and a chest.
        s.set(2, 1, 2, furnace("smoker", "south")).set(3, 1, 2, furnace("furnace", "south")).set(4, 1, 2, Kit.barrel());
        s.fill(2, 3, 2, 4, 3, 2, Kit.upside(Kit.TRIM_STAIRS, "north"));
        s.set(3, 1, 4, Kit.fence("spruce")).set(3, 2, 4, Kit.lantern());
        s.set(2, 1, 4, Kit.stairs(OAK_STAIRS, "west"));
        s.set(2, 1, 6, Kit.chest("east"));
        // A ladder up through the floor at the east wall.
        for (int y = 1; y <= 4; y++) {
            s.set(4, y, 5, Kit.ladder("west"));
        }
        // The bedroom: two beds at the back, a nightstand with a lamp, a chest under the front windows, a bookshelf.
        s.bed(2, 5, 3, "minecraft:blue_bed", "north", 0, -1).bed(4, 5, 3, "minecraft:blue_bed", "north", 0, -1);
        s.set(3, 5, 2, Kit.barrel()).set(3, 6, 2, Kit.lantern());
        s.set(3, 5, 7, Kit.chest("north")).set(4, 5, 6, BOOKSHELF);
        s.fill(3, 5, 3, 3, 5, 6, Kit.CLOTH_CARPET);
        s.set(3, 8, 5, Kit.hangingLantern());
        // A ladder up into the attic at the west wall.
        for (int y = 5; y <= 9; y++) {
            s.set(2, y, 6, Kit.ladder("east"));
        }
        // The attic: stores under the roof and a lantern on a chain from the ridge.
        s.set(2, 10, 2, Kit.barrel()).set(3, 10, 2, HAY).set(4, 10, 2, Kit.barrel()).set(4, 10, 3, Kit.chest("west"));
        chandelier(s, 3, 11, 5, 13);
        s.connect();
        return s;
    }
}
