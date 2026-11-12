package io.github.sekerator.kingdomworld.generator;

/**
 * The furniture of the buildings, the same pieces for every style; the wood of a piece is a parameter, and pieces of oak
 * and spruce follow the wood of the place like the timber of a building, the red cloth the colour of its look.
 *
 * <p>What a piece is made of matters to the game: a bed is a home, a work post a workplace, a chest or a barrel a store of
 * goods, a composter a field for farmers. So pieces made for looks use none of them: a bed of a castle is a canopy of wool
 * and slabs, a crate is a block of wood, a kitchen has a cauldron and a campfire. The pieces that do count say so.
 */
final class Furnish {
    private Furnish() {
    }

    private static String id(String wood, String kind) {
        return "minecraft:" + wood + "_" + kind;
    }

    // ---------------------------------------------------------------- what counts

    /**
     * A bed: a home for one. Only in houses, the rooms of mages and the bunks of barracks.
     *
     * @param facing where its head lies from its foot
     */
    static void bed(Structure s, int x, int y, int z, String facing, String color) {
        int dx = facing.equals("east") ? 1 : facing.equals("west") ? -1 : 0;
        int dz = facing.equals("south") ? 1 : facing.equals("north") ? -1 : 0;
        s.bed(x, y, z, "minecraft:" + color + "_bed", facing, dx, dz);
    }

    /**
     * The one chest of a household, for the goods of those who live there.
     */
    static void chest(Structure s, int x, int y, int z, String facing) {
        s.set(x, y, z, Kit.chest(facing));
    }

    // ---------------------------------------------------------------- tables and seats

    /**
     * A small table: a post with a plate on it.
     */
    static void table(Structure s, int x, int y, int z, String wood) {
        s.set(x, y, z, Kit.fence(wood)).set(x, y + 1, z, id(wood, "pressure_plate[powered=false]"));
    }

    /**
     * A long table of upper slabs from one end to the other along x or z, on posts at both ends.
     */
    static void longTable(Structure s, int x1, int z1, int x2, int z2, int y, String wood) {
        s.fill(x1, y, z1, x2, y, z2, Kit.topSlab(id(wood, "slab")));
    }

    /**
     * A chair: stairs whose seat looks the given way (the back on the other side).
     */
    static void chair(Structure s, int x, int y, int z, String facing, String wood) {
        s.set(x, y, z, Kit.stairs(id(wood, "stairs"), opposite(facing)));
    }

    /**
     * A bench along x or z: stairs side by side, the seat looking the given way.
     */
    static void bench(Structure s, int x1, int z1, int x2, int z2, int y, String facing, String wood) {
        s.fill(x1, y, z1, x2, y, z2, Kit.stairs(id(wood, "stairs"), opposite(facing)));
    }

    /**
     * A desk of a clerk: an upper slab with a lantern or candle on it and a chair before it.
     */
    static void desk(Structure s, int x, int y, int z, String facing, String wood) {
        s.set(x, y, z, Kit.topSlab(id(wood, "slab")));
        s.set(x, y + 1, z, candle("white"));
        int[] d = step(facing);
        if (s.inside(x + d[0], y, z + d[1])) {
            chair(s, x + d[0], y, z + d[1], opposite(facing), wood);
        }
    }

    // ---------------------------------------------------------------- shelves, stores and goods

    /**
     * A shelf of books from one layer to another.
     */
    static void books(Structure s, int x1, int z1, int x2, int z2, int y1, int y2) {
        s.fill(x1, y1, z1, x2, y2, z2, "minecraft:bookshelf");
    }

    /**
     * A crate of goods without an inventory: a block of stripped wood.
     */
    static void crate(Structure s, int x, int y, int z, String wood) {
        s.set(x, y, z, "minecraft:stripped_" + wood + "_wood[axis=y]");
    }

    /**
     * A sack of grain or flour: a block of wool of earthy colour.
     */
    static void sack(Structure s, int x, int y, int z, boolean white) {
        s.set(x, y, z, white ? "minecraft:white_wool" : "minecraft:brown_wool");
    }

    /**
     * A rack of weapons: a grindstone for the swords, a post for the spears.
     */
    static void weaponRack(Structure s, int x, int y, int z, String facing, String wood) {
        s.set(x, y, z, "minecraft:grindstone[face=floor,facing=" + facing + "]");
    }

    /**
     * A plant in a tub: leaves that never wither on a stump.
     */
    static void plant(Structure s, int x, int y, int z, String leaves) {
        s.set(x, y, z, "minecraft:stripped_oak_wood[axis=y]").set(x, y + 1, z, Kit.leaves(leaves));
    }

    // ---------------------------------------------------------------- fire, kitchen and light

    /**
     * A hearth in a wall: a niche of rough stone with a fire in it behind an iron grille, a mantel of trim over it.
     *
     * @param facing where the hearth opens to the room
     */
    static void hearth(Structure s, int x, int y, int z, String facing) {
        s.set(x, y, z, "minecraft:campfire[facing=" + facing + ",lit=true,signal_fire=false,waterlogged=false]");
        int[] d = step(facing);
        if (s.inside(x + d[0], y, z + d[1])) {
            s.set(x + d[0], y, z + d[1], "minecraft:iron_bars");
        }
        s.set(x, y + 1, z, Kit.ROUGH);
        if (s.inside(x + d[0], y + 1, z + d[1])) {
            s.set(x + d[0], y + 1, z + d[1], Kit.upside(Kit.TRIM_STAIRS, opposite(facing)));
        }
    }

    /**
     * A kitchen corner along x: a cauldron, a fire to cook on, a board for cutting and a shelf with pots above.
     */
    static void kitchen(Structure s, int x, int y, int z, String facing, String wood) {
        s.set(x, y, z, "minecraft:water_cauldron[level=3]");
        s.set(x + 1, y, z, "minecraft:campfire[facing=" + facing + ",lit=true,signal_fire=false,waterlogged=false]");
        s.set(x + 2, y, z, Kit.topSlab(id(wood, "slab")));
        s.set(x + 2, y + 1, z, id(wood, "pressure_plate[powered=false]"));
        if (s.inside(x, y + 2, z)) {
            s.fill(x, y + 2, z, x + 2, y + 2, z, Kit.trapdoor(id(wood, "trapdoor"), facing, true));
        }
    }

    /**
     * A lantern hanging from a chain of the given length under the ceiling at {@code top}.
     */
    static void chandelier(Structure s, int x, int z, int top, int chain) {
        s.fill(x, top - chain + 1, z, x, top, z, Kit.chain());
        s.set(x, top - chain, z, Kit.hangingLantern());
    }

    /**
     * A great chandelier: a ring of lanterns around a chain from the ceiling.
     */
    static void greatChandelier(Structure s, int x, int z, int top, int chain) {
        s.fill(x, top - chain + 1, z, x, top, z, Kit.chain());
        int y = top - chain;
        s.set(x, y, z, "minecraft:iron_bars");
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            s.set(x + d[0], y, z + d[1], Kit.hangingLantern());
        }
        s.set(x, y - 1, z, Kit.hangingLantern());
    }

    /**
     * A lit candle of a colour (the plain candle for {@code ""}).
     */
    static String candle(String color) {
        return "minecraft:" + (color.isEmpty() ? "" : color + "_") + "candle[candles=1,lit=true,waterlogged=false]";
    }

    /**
     * Candles on a table or a shelf: one to three of them.
     */
    static String candles(int count) {
        return "minecraft:candle[candles=" + count + ",lit=true,waterlogged=false]";
    }

    // ---------------------------------------------------------------- cloth

    /**
     * A rug of carpet over a floor.
     */
    static void rug(Structure s, int x1, int z1, int x2, int z2, int y, String carpet) {
        s.fill(x1, y, z1, x2, y, z2, carpet);
    }

    /**
     * A bed of state for castles and halls that is no home: a mattress of white wool under a cover of cloth, a head board
     * and four posts with a canopy of carpet on slabs.
     *
     * @param facing where its head lies from its foot
     */
    static void canopyBed(Structure s, int x, int y, int z, String facing, String wood) {
        int[] d = step(facing);
        int hx = x + d[0];
        int hz = z + d[1];
        s.set(x, y, z, "minecraft:white_wool");
        s.set(hx, y, hz, "minecraft:white_wool");
        s.set(x, y + 1, z, Kit.CLOTH_CARPET);
        s.set(hx, y + 1, hz, "minecraft:white_carpet");
        int[] side = step(left(facing));
        for (int[] post : new int[][]{{x + side[0], z + side[1]}, {x - side[0], z - side[1]}, {hx + side[0] + d[0], hz + side[1] + d[1]},
                {hx - side[0] + d[0], hz - side[1] + d[1]}}) {
            if (s.inside(post[0], y, post[1]) && !s.solid(post[0], y, post[1])) {
                s.fill(post[0], y, post[1], post[0], y + 2, post[1], Kit.fence(wood));
                s.set(post[0], y + 3, post[1], Kit.slab(id(wood, "slab")));
            }
        }
        s.set(hx + d[0], y, hz + d[1], id(wood, "planks"));
        s.set(hx + d[0], y + 1, hz + d[1], id(wood, "planks"));
        s.set(x, y + 3, z, Kit.CLOTH);
        s.set(hx, y + 3, hz, Kit.CLOTH);
    }

    // ---------------------------------------------------------------- what a finished building may hold

    private static boolean container(String state) {
        String id = States.id(state);
        return id.equals("minecraft:chest") || id.equals("minecraft:trapped_chest") || id.equals("minecraft:barrel");
    }

    private static boolean stove(String state) {
        String id = States.id(state);
        return id.equals("minecraft:furnace") || id.equals("minecraft:smoker") || id.equals("minecraft:blast_furnace");
    }

    /**
     * Whether a position touches a post of work on its own layer (sides and corners).
     */
    private static boolean byPost(Structure s, int x, int y, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dz != 0) && s.inside(x + dx, y, z + dz) && s.get(x + dx, y, z + dz).startsWith("kingdomworld:work_post")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A building whose furniture counts only where it should: the containers beside its posts of work and at most
     * {@code containers} others stay, the rest become crates without an inventory; at most {@code stoves} furnaces,
     * smokers and blast furnaces stay, the rest become stone counters; at most two lecterns, the rest bookshelves;
     * composters, the places of farmers, become hay. The positions are taken from the bottom up, so the ground floor keeps
     * what stays.
     */
    static Structure tidy(Structure s, int containers, int stoves) {
        int keptContainers = 0;
        int keptStoves = 0;
        int lecterns = 0;
        for (int y = 0; y < s.sizeY; y++) {
            for (int z = 0; z < s.sizeZ; z++) {
                for (int x = 0; x < s.sizeX; x++) {
                    String state = s.get(x, y, z);
                    String id = States.id(state);
                    if (container(state)) {
                        if (byPost(s, x, y, z)) {
                            continue;
                        }
                        if (keptContainers < containers) {
                            keptContainers++;
                        } else {
                            s.set(x, y, z, id.equals("minecraft:barrel") ? "minecraft:stripped_spruce_wood[axis=y]" : "minecraft:stripped_oak_wood[axis=y]");
                        }
                    } else if (stove(state)) {
                        if (keptStoves < stoves) {
                            keptStoves++;
                        } else {
                            s.set(x, y, z, Kit.TRIM);
                        }
                    } else if (id.equals("minecraft:lectern")) {
                        if (lecterns < 2) {
                            lecterns++;
                        } else {
                            s.set(x, y, z, "minecraft:bookshelf");
                        }
                    } else if (id.equals("minecraft:composter")) {
                        s.set(x, y, z, "minecraft:hay_block[axis=y]");
                    }
                }
            }
        }
        return s;
    }

    /**
     * The buildings of a style with their furniture tidied by what they are: a home keeps one container and one stove, a
     * workshop or a tavern its stoves, a castle the chests of its treasury.
     */
    static java.util.List<BuildingGenerator.Building> tidy(java.util.List<BuildingGenerator.Building> buildings) {
        for (BuildingGenerator.Building building : buildings) {
            String role = building.role();
            boolean work = role.equals("workshop") || role.equals("tavern");
            int containers = role.equals("house") ? 1 : building.type().equals("palace") ? 4 : 2;
            tidy(building.structure(), containers, work ? 8 : building.type().equals("palace") ? 2 : 1);
        }
        return buildings;
    }

    // ---------------------------------------------------------------- directions

    static int[] step(String facing) {
        return switch (facing) {
            case "north" -> new int[]{0, -1};
            case "south" -> new int[]{0, 1};
            case "west" -> new int[]{-1, 0};
            default -> new int[]{1, 0};
        };
    }

    static String opposite(String facing) {
        return switch (facing) {
            case "north" -> "south";
            case "south" -> "north";
            case "west" -> "east";
            default -> "west";
        };
    }

    static String left(String facing) {
        return switch (facing) {
            case "north" -> "west";
            case "west" -> "south";
            case "south" -> "east";
            default -> "north";
        };
    }
}
