package io.github.sekerator.kingdomworld.generator;

/**
 * What the buildings of a capital are drawn with: the blocks that stand for the parts of a look, and the shapes they share.
 *
 * <p>A building is drawn once and looks different wherever it is laid, because the materials are given when it is laid. For
 * that every part of a look has blocks of its own, and a drawing never uses them for anything else:
 * <ul>
 * <li>the wall: the family of stone bricks, which becomes the stone of the place;</li>
 * <li>rough stone for footings, quoins and the cores of towers: the family of cobblestone;</li>
 * <li>trim for cornices, bands and sills: the family of polished andesite; carved stone for keystones;</li>
 * <li>the roof: one of the roofs below (stairs, slabs and the full block under them), never used in a wall;</li>
 * <li>plaster between the timbers of a frame: white terracotta;</li>
 * <li>cloth of the colour of the kingdom: red wool, carpets and banners;</li>
 * <li>shutters: spruce trapdoors; a chimney that a house may do without: the family of bricks;</li>
 * <li>timber: oak and spruce logs, planks, fences and doors, which become the wood of the place.</li>
 * </ul>
 */
final class Kit {
    static final String WALL = "minecraft:stone_bricks";
    static final String WALL_STAIRS = "minecraft:stone_brick_stairs";
    static final String WALL_SLAB = "minecraft:stone_brick_slab";
    static final String WALL_FENCE = "minecraft:stone_brick_wall";
    static final String ROUGH = "minecraft:cobblestone";
    static final String ROUGH_STAIRS = "minecraft:cobblestone_stairs";
    static final String ROUGH_SLAB = "minecraft:cobblestone_slab";
    static final String ROUGH_FENCE = "minecraft:cobblestone_wall";
    static final String TRIM = "minecraft:polished_andesite";
    static final String TRIM_STAIRS = "minecraft:polished_andesite_stairs";
    static final String TRIM_SLAB = "minecraft:polished_andesite_slab";
    static final String CARVED = "minecraft:chiseled_stone_bricks";
    static final String PLASTER = "minecraft:white_terracotta";
    static final String CLOTH = "minecraft:red_wool";
    static final String CLOTH_CARPET = "minecraft:red_carpet";
    static final String SHUTTER = "minecraft:spruce_trapdoor";
    static final String CHIMNEY = "minecraft:bricks";
    static final String CHIMNEY_STAIRS = "minecraft:brick_stairs";
    static final String CHIMNEY_SLAB = "minecraft:brick_slab";
    static final String CHIMNEY_POT = "minecraft:brick_wall";
    static final String GLASS = "minecraft:glass_pane";
    static final String BARS = "minecraft:iron_bars";

    /**
     * The blocks of a roof.
     */
    record Roof(String block, String stairs, String slab) {
    }

    /** Dark wooden shingles. */
    static final Roof SHINGLE = new Roof("minecraft:dark_oak_planks", "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab");
    /** Red tiles. */
    static final Roof TILE = new Roof("minecraft:mangrove_planks", "minecraft:mangrove_stairs", "minecraft:mangrove_slab");
    /** Blue-green shingles. */
    static final Roof TEAL = new Roof("minecraft:warped_planks", "minecraft:warped_stairs", "minecraft:warped_slab");
    /** Dark slate, the roof of castles and towers. */
    static final Roof SLATE = new Roof("minecraft:dark_prismarine", "minecraft:dark_prismarine_stairs", "minecraft:dark_prismarine_slab");

    private Kit() {
    }

    // ---------------------------------------------------------------- states

    static String stairs(String block, String facing) {
        return block + "[facing=" + facing + ",half=bottom,shape=straight,waterlogged=false]";
    }

    static String stairs(String block, String facing, String shape) {
        return block + "[facing=" + facing + ",half=bottom,shape=" + shape + ",waterlogged=false]";
    }

    /** Stairs hanging upside down: a bracket, a cornice, the haunch of an arch. */
    static String upside(String block, String facing) {
        return block + "[facing=" + facing + ",half=top,shape=straight,waterlogged=false]";
    }

    static String slab(String block) {
        return block + "[type=bottom,waterlogged=false]";
    }

    static String topSlab(String block) {
        return block + "[type=top,waterlogged=false]";
    }

    static String log(String log, String axis) {
        return log + "[axis=" + axis + "]";
    }

    /** A trapdoor lying in its opening, at the bottom or at the top of its cell. */
    static String trapdoor(String block, String facing, boolean top) {
        return block + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",open=false,powered=false,waterlogged=false]";
    }

    /** An open trapdoor standing flat against the block behind it, the block on the side opposite to where it faces. */
    static String shutter(String block, String facing) {
        return block + "[facing=" + facing + ",half=top,open=true,powered=false,waterlogged=false]";
    }

    static String wallTorch(String facing) {
        return "minecraft:wall_torch[facing=" + facing + "]";
    }

    static String ladder(String facing) {
        return "minecraft:ladder[facing=" + facing + ",waterlogged=false]";
    }

    static String chest(String facing) {
        return "minecraft:chest[facing=" + facing + ",type=single,waterlogged=false]";
    }

    static String barrel() {
        return "minecraft:barrel[facing=up,open=false]";
    }

    static String lantern() {
        return "minecraft:lantern[hanging=false,waterlogged=false]";
    }

    static String hangingLantern() {
        return "minecraft:lantern[hanging=true,waterlogged=false]";
    }

    static String chain() {
        return "minecraft:chain[axis=y,waterlogged=false]";
    }

    /** A banner of a colour hanging on the block behind it. */
    static String wallBanner(String color, String facing) {
        return "minecraft:" + color + "_wall_banner[facing=" + facing + "]";
    }

    /** A banner of a colour standing on a pole; rotation 0 looks south, 4 west, 8 north, 12 east. */
    static String banner(String color, int rotation) {
        return "minecraft:" + color + "_banner[rotation=" + rotation + "]";
    }

    /** Leaves that never decay. */
    static String leaves(String kind) {
        return "minecraft:" + kind + "_leaves[distance=7,persistent=true,waterlogged=false]";
    }

    /** A bare post of a fence; {@link Structure#connect()} joins it to its neighbours. */
    static String fence(String wood) {
        return "minecraft:" + wood + "_fence";
    }

    /** A pane of stained glass of a colour, bare until {@link Structure#connect()}. */
    static String stainedPane(String color) {
        return "minecraft:" + color + "_stained_glass_pane";
    }

    // ---------------------------------------------------------------- round shapes

    /**
     * Whether a column lies inside a round plan of an odd diameter around its middle.
     */
    static boolean inDisc(int dx, int dz, int diameter) {
        int radius = (diameter - 1) / 2;
        return dx * dx + dz * dz <= radius * radius + radius;
    }

    /**
     * Whether a column lies on the rim of a round plan: inside it with a neighbour outside.
     */
    static boolean onRim(int dx, int dz, int diameter) {
        if (!inDisc(dx, dz, diameter)) {
            return false;
        }
        return !inDisc(dx + 1, dz, diameter) || !inDisc(dx - 1, dz, diameter) || !inDisc(dx, dz + 1, diameter) || !inDisc(dx, dz - 1, diameter);
    }

    /**
     * A round slab of blocks: one layer of a round plan.
     */
    static void disc(Structure s, int cx, int y, int cz, int diameter, String state) {
        int radius = (diameter - 1) / 2;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (inDisc(dx, dz, diameter) && s.inside(cx + dx, y, cz + dz)) {
                    s.set(cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /**
     * The round wall of a tower from one layer to another, one block thick, hollow inside.
     */
    static void tube(Structure s, int cx, int y1, int y2, int cz, int diameter, String state) {
        int radius = (diameter - 1) / 2;
        for (int y = y1; y <= y2; y++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (onRim(dx, dz, diameter) && s.inside(cx + dx, y, cz + dz)) {
                        s.set(cx + dx, y, cz + dz, state);
                    }
                }
            }
        }
    }

    /**
     * A conical roof over a round tower: rings shrinking by one block each, every ring {@code rise} layers high, starting one
     * block wider than the tower so that the eaves overhang, hollow inside, with a finial of slabs on its point.
     *
     * @param y        the layer of the eaves
     * @param diameter the diameter of the tower under the roof
     * @return the layer above the point of the roof
     */
    static int cone(Structure s, int cx, int y, int cz, int diameter, Roof roof, int rise) {
        int layer = y;
        for (int width = diameter + 2; width >= 1; width -= 2) {
            for (int step = 0; step < rise; step++) {
                int radius = (width - 1) / 2;
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        // A shell two blocks thick, so that the ring above always rests on this one.
                        if (inDisc(dx, dz, width) && (width < 5 || !inDisc(dx, dz, width - 4)) && s.inside(cx + dx, layer, cz + dz)) {
                            s.set(cx + dx, layer, cz + dz, roof.block());
                        }
                    }
                }
                layer++;
            }
        }
        if (s.inside(cx, layer, cz)) {
            s.set(cx, layer, cz, slab(roof.slab()));
            layer++;
        }
        return layer;
    }

    /**
     * Battlements on a round tower: a parapet on the rim of the plan at {@code y} and merlons on every second block of it.
     */
    static void roundBattlements(Structure s, int cx, int y, int cz, int diameter, String block) {
        int radius = (diameter - 1) / 2;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (onRim(dx, dz, diameter) && s.inside(cx + dx, y + 1, cz + dz)) {
                    s.set(cx + dx, y, cz + dz, block);
                    if (Math.floorMod(dx + dz, 2) == 0) {
                        s.set(cx + dx, y + 1, cz + dz, block);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- straight shapes

    /**
     * Battlements along a straight line: a parapet at {@code y} and merlons on every second block of it.
     */
    static void battlements(Structure s, int x1, int z1, int x2, int z2, int y, String block) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                s.set(x, y, z, block);
                if (Math.floorMod(x + z, 2) == 0) {
                    s.set(x, y + 1, z, block);
                }
            }
        }
    }

    /**
     * A steep tented roof over a square tower: rings of stairs each standing on a ring of the full block of the roof, so
     * that it climbs two layers for every block inwards, closed with a slab on its point.
     *
     * @param y the layer of the eaves
     * @return the layer above the point of the roof
     */
    static int spire(Structure s, int x1, int z1, int x2, int z2, int y, Roof roof) {
        int layer = y;
        int ring = 0;
        while (x2 - ring - (x1 + ring) >= 2 && z2 - ring - (z1 + ring) >= 2) {
            int west = x1 + ring;
            int east = x2 - ring;
            int back = z1 + ring;
            int front = z2 - ring;
            if (ring > 0) {
                s.walls(west, layer, back, east, layer, front, roof.block());
                layer++;
            }
            s.fill(west, layer, back, east, layer, back, stairs(roof.stairs(), "south"));
            s.fill(west, layer, front, east, layer, front, stairs(roof.stairs(), "north"));
            s.fill(west, layer, back + 1, west, layer, front - 1, stairs(roof.stairs(), "east"));
            s.fill(east, layer, back + 1, east, layer, front - 1, stairs(roof.stairs(), "west"));
            s.set(west, layer, back, stairs(roof.stairs(), "south", "outer_left"));
            s.set(east, layer, back, stairs(roof.stairs(), "south", "outer_right"));
            s.set(west, layer, front, stairs(roof.stairs(), "north", "outer_right"));
            s.set(east, layer, front, stairs(roof.stairs(), "north", "outer_left"));
            layer++;
            ring++;
        }
        if (x1 + ring <= x2 - ring && z1 + ring <= z2 - ring) {
            s.fill(x1 + ring, layer - 1, z1 + ring, x2 - ring, layer - 1, z2 - ring, roof.block());
            s.fill(x1 + ring, layer, z1 + ring, x2 - ring, layer, z2 - ring, slab(roof.slab()));
            layer++;
        }
        return layer;
    }
}
