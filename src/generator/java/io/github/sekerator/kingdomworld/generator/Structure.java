package io.github.sekerator.kingdomworld.generator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A building drawn block by block: a box of the given size in which every position holds a block state, written as the
 * game writes them ({@code minecraft:oak_stairs[facing=north,half=bottom]}). Positions nobody set are air, so the builders
 * clear the whole box; a position left out with {@link #skip} is no part of the building at all, so that what stands there
 * (a throne in its hall) stays. x runs along the front, z from the back (0) to the front, y up from the foundation (0).
 */
final class Structure {
    /** Data version of Minecraft 1.20.1, so that the game reads the file without converting it. */
    static final int DATA_VERSION = 3465;
    static final String AIR = "minecraft:air";
    /** Marks a position that is left out of the file. */
    private static final String LEFT_OUT = "";

    final int sizeX;
    final int sizeY;
    final int sizeZ;
    private final String[] states;

    Structure(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.states = new String[sizeX * sizeY * sizeZ];
    }

    private int index(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            throw new IllegalArgumentException("Outside the structure: " + x + " " + y + " " + z);
        }
        return (y * sizeZ + z) * sizeX + x;
    }

    Structure set(int x, int y, int z, String state) {
        states[index(x, y, z)] = state;
        return this;
    }

    String get(int x, int y, int z) {
        String state = states[index(x, y, z)];
        return state == null ? AIR : state;
    }

    /**
     * Leaves a position out of the building: nothing is built there and nothing cleared.
     */
    Structure skip(int x, int y, int z) {
        states[index(x, y, z)] = LEFT_OUT;
        return this;
    }

    /**
     * Whether a position holds a block of the building (not air and not left out).
     */
    boolean solid(int x, int y, int z) {
        String state = states[index(x, y, z)];
        return state != null && !state.equals(LEFT_OUT) && !state.equals(AIR);
    }

    Structure fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    set(x, y, z, state);
                }
            }
        }
        return this;
    }

    /**
     * The four walls of a box, without its inside.
     */
    Structure walls(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        fill(x1, y1, z1, x2, y2, z1, state);
        fill(x1, y1, z2, x2, y2, z2, state);
        fill(x1, y1, z1, x1, y2, z2, state);
        fill(x2, y1, z1, x2, y2, z2, state);
        return this;
    }

    /**
     * Upright logs at the four corners of a box.
     */
    Structure corners(int x1, int y1, int z1, int x2, int y2, int z2, String log) {
        fill(x1, y1, z1, x1, y2, z1, log + "[axis=y]");
        fill(x2, y1, z1, x2, y2, z1, log + "[axis=y]");
        fill(x1, y1, z2, x1, y2, z2, log + "[axis=y]");
        fill(x2, y1, z2, x2, y2, z2, log + "[axis=y]");
        return this;
    }

    /**
     * A ring of lying logs at one height: along x on the front and back, along z on the sides.
     */
    Structure beam(int x1, int z1, int x2, int z2, int y, String log) {
        fill(x1, y, z1, x2, y, z1, log + "[axis=x]");
        fill(x1, y, z2, x2, y, z2, log + "[axis=x]");
        fill(x1, y, z1 + 1, x1, y, z2 - 1, log + "[axis=z]");
        fill(x2, y, z1 + 1, x2, y, z2 - 1, log + "[axis=z]");
        return this;
    }

    /**
     * A gable roof whose ridge runs along x: rows of stairs rising from the back eave ({@code z1}) and the front eave
     * ({@code z2}) one block per row, a ridge of slabs, and the gable ends filled with {@code gable} under the roof.
     *
     * @param y      height of the eaves
     * @param x1     first column of the roof, with the overhang
     * @param x2     last column
     * @param wallX1 the gable wall on the low x side, or -1 for none
     * @param wallX2 the gable wall on the high x side, or -1 for none
     */
    Structure gableRoof(int x1, int x2, int z1, int z2, int y, String stairs, String slab, String gable, int wallX1, int wallX2) {
        int row = 0;
        while (z1 + row < z2 - row) {
            int back = z1 + row;
            int front = z2 - row;
            int height = y + row;
            fill(x1, height, back, x2, height, back, stairs + "[facing=south,half=bottom,shape=straight]");
            fill(x1, height, front, x2, height, front, stairs + "[facing=north,half=bottom,shape=straight]");
            // Under the first row the walls carry the roof; above it the gables close the ends.
            if (row > 0) {
                for (int wall : new int[]{wallX1, wallX2}) {
                    if (wall >= 0 && front - back > 1) {
                        fill(wall, height, back + 1, wall, height, front - 1, gable);
                    }
                }
            }
            row++;
        }
        if (z1 + row == z2 - row) {
            int height = y + row;
            fill(x1, height, z1 + row, x2, height, z1 + row, slab + "[type=bottom]");
            for (int wall : new int[]{wallX1, wallX2}) {
                if (wall >= 0) {
                    set(wall, height - 1, z1 + row, gable);
                }
            }
        }
        return this;
    }

    /**
     * A gable roof whose ridge runs along z, so that the gable looks at the street: rows of stairs rising from the west eave
     * ({@code x1}) and the east eave ({@code x2}) one block per row, a ridge of slabs, and the gable ends filled with
     * {@code gable} under the roof.
     *
     * @param y      height of the eaves
     * @param z1     first row of the roof, with the overhang
     * @param z2     last row
     * @param wallZ1 the gable wall at the back, or -1 for none
     * @param wallZ2 the gable wall at the front, or -1 for none
     */
    Structure gableRoofZ(int x1, int x2, int z1, int z2, int y, String stairs, String slab, String gable, int wallZ1, int wallZ2) {
        int row = 0;
        while (x1 + row < x2 - row) {
            int west = x1 + row;
            int east = x2 - row;
            int height = y + row;
            fill(west, height, z1, west, height, z2, stairs + "[facing=east,half=bottom,shape=straight]");
            fill(east, height, z1, east, height, z2, stairs + "[facing=west,half=bottom,shape=straight]");
            if (row > 0) {
                for (int wall : new int[]{wallZ1, wallZ2}) {
                    if (wall >= 0 && east - west > 1) {
                        fill(west + 1, height, wall, east - 1, height, wall, gable);
                    }
                }
            }
            row++;
        }
        if (x1 + row == x2 - row) {
            int height = y + row;
            fill(x1 + row, height, z1, x1 + row, height, z2, slab + "[type=bottom]");
            for (int wall : new int[]{wallZ1, wallZ2}) {
                if (wall >= 0) {
                    set(x1 + row, height - 1, wall, gable);
                }
            }
        }
        return this;
    }

    /**
     * A hip roof: rings of stairs rising one block per ring from the eaves on all four sides, with turned stairs at the
     * corners, closed with slabs where the rings meet.
     *
     * @param y height of the eaves
     */
    Structure hipRoof(int x1, int x2, int z1, int z2, int y, String stairs, String slab) {
        int ring = 0;
        while (x2 - ring - (x1 + ring) >= 2 && z2 - ring - (z1 + ring) >= 2) {
            int west = x1 + ring;
            int east = x2 - ring;
            int back = z1 + ring;
            int front = z2 - ring;
            int height = y + ring;
            fill(west, height, back, east, height, back, stairs + "[facing=south,half=bottom,shape=straight]");
            fill(west, height, front, east, height, front, stairs + "[facing=north,half=bottom,shape=straight]");
            fill(west, height, back + 1, west, height, front - 1, stairs + "[facing=east,half=bottom,shape=straight]");
            fill(east, height, back + 1, east, height, front - 1, stairs + "[facing=west,half=bottom,shape=straight]");
            set(west, height, back, stairs + "[facing=south,half=bottom,shape=outer_left]");
            set(east, height, back, stairs + "[facing=south,half=bottom,shape=outer_right]");
            set(west, height, front, stairs + "[facing=north,half=bottom,shape=outer_right]");
            set(east, height, front, stairs + "[facing=north,half=bottom,shape=outer_left]");
            ring++;
        }
        if (x1 + ring <= x2 - ring && z1 + ring <= z2 - ring) {
            fill(x1 + ring, y + ring, z1 + ring, x2 - ring, y + ring, z2 - ring, slab + "[type=bottom]");
        }
        return this;
    }

    /**
     * A door of two halves facing the given way, its lower half at {@code y}.
     */
    Structure door(int x, int y, int z, String door, String facing, String hinge) {
        set(x, y, z, door + "[facing=" + facing + ",half=lower,hinge=" + hinge + ",open=false,powered=false]");
        set(x, y + 1, z, door + "[facing=" + facing + ",half=upper,hinge=" + hinge + ",open=false,powered=false]");
        return this;
    }

    /**
     * A bed whose foot is at the position and whose head lies the way it faces.
     */
    Structure bed(int x, int y, int z, String bed, String facing, int dx, int dz) {
        set(x, y, z, bed + "[facing=" + facing + ",part=foot,occupied=false]");
        set(x + dx, y, z + dz, bed + "[facing=" + facing + ",part=head,occupied=false]");
        return this;
    }

    // ---------------------------------------------------------------- whole structures

    /**
     * Whether a position is left out of the building.
     */
    boolean leftOut(int x, int y, int z) {
        return LEFT_OUT.equals(states[index(x, y, z)]);
    }

    /**
     * Whether a position lies inside the box.
     */
    boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    /**
     * The highest layer of a column that holds a block, or -1 for an empty column.
     */
    int top(int x, int z) {
        for (int y = sizeY - 1; y >= 0; y--) {
            if (solid(x, y, z)) {
                return y;
            }
        }
        return -1;
    }

    /**
     * Leaves the sky out of the building: in every column the air more than {@code headroom} layers above its highest block
     * is no part of the file, so that a large building does not carry tens of thousands of positions that only say "air".
     * The air below stays and clears the place for those who walk there.
     */
    Structure trimSky(int headroom) {
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                for (int y = top(x, z) + headroom + 1; y < sizeY; y++) {
                    if (!leftOut(x, y, z)) {
                        skip(x, y, z);
                    }
                }
            }
        }
        return this;
    }

    /**
     * Copies another structure into this one with its corner at the given position; positions it leaves out stay as they are.
     */
    Structure paste(Structure other, int dx, int dy, int dz) {
        for (int y = 0; y < other.sizeY; y++) {
            for (int z = 0; z < other.sizeZ; z++) {
                for (int x = 0; x < other.sizeX; x++) {
                    String state = other.states[other.index(x, y, z)];
                    if (!LEFT_OUT.equals(state) && inside(x + dx, y + dy, z + dz)) {
                        set(x + dx, y + dy, z + dz, state == null ? AIR : state);
                    }
                }
            }
        }
        return this;
    }

    /**
     * This structure turned clockwise by quarter turns as a placement turns a building: once, and the front that looked south
     * looks west.
     */
    Structure turned(int quarters) {
        Structure result = this;
        for (int turn = 0; turn < Math.floorMod(quarters, 4); turn++) {
            Structure from = result;
            Structure next = new Structure(from.sizeZ, from.sizeY, from.sizeX);
            for (int y = 0; y < from.sizeY; y++) {
                for (int z = 0; z < from.sizeZ; z++) {
                    for (int x = 0; x < from.sizeX; x++) {
                        String state = from.states[from.index(x, y, z)];
                        next.states[next.index(from.sizeZ - 1 - z, y, x)] = state == null || state.isEmpty() ? state : States.turned(state);
                    }
                }
            }
            result = next;
        }
        return result;
    }

    /**
     * This structure mirrored along its front, as a placement mirrors a building: west and east change places.
     */
    Structure mirrored() {
        Structure next = new Structure(sizeX, sizeY, sizeZ);
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    String state = states[index(x, y, z)];
                    next.states[next.index(sizeX - 1 - x, y, z)] = state == null || state.isEmpty() ? state : States.mirrored(state);
                }
            }
        }
        return next;
    }

    /**
     * Joins fences, panes, bars and walls to their neighbours the way the game does when they are placed by hand, so that a
     * building is drawn with bare posts and gets its rails, window bars and parapets whole. Call it once, when the
     * building is drawn.
     */
    Structure connect() {
        // Walls from the top down: whether a wall keeps its post depends on the wall above it.
        for (int y = sizeY - 1; y >= 0; y--) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    String state = states[index(x, y, z)];
                    if (state == null || state.isEmpty()) {
                        continue;
                    }
                    String id = States.id(state);
                    if (States.isFence(id)) {
                        set(x, y, z, States.with(state, "north", String.valueOf(fenceJoins(id, x, y, z - 1)),
                                "south", String.valueOf(fenceJoins(id, x, y, z + 1)), "west", String.valueOf(fenceJoins(id, x - 1, y, z)),
                                "east", String.valueOf(fenceJoins(id, x + 1, y, z)), "waterlogged", "false"));
                    } else if (States.isPane(id)) {
                        set(x, y, z, States.with(state, "north", String.valueOf(paneJoins(x, y, z - 1)),
                                "south", String.valueOf(paneJoins(x, y, z + 1)), "west", String.valueOf(paneJoins(x - 1, y, z)),
                                "east", String.valueOf(paneJoins(x + 1, y, z)), "waterlogged", "false"));
                    } else if (States.isWall(id)) {
                        set(x, y, z, wall(state, x, y, z));
                    }
                }
            }
        }
        return this;
    }

    private String at(int x, int y, int z) {
        if (!inside(x, y, z)) {
            return AIR;
        }
        String state = states[index(x, y, z)];
        return state == null || state.isEmpty() ? AIR : state;
    }

    private boolean fenceJoins(String fence, int x, int y, int z) {
        String id = States.id(at(x, y, z));
        if (States.isFence(id)) {
            // Wooden fences join each other whatever their wood; a fence of nether bricks joins only its own kind.
            return id.equals(fence) || !id.contains("brick") && !fence.contains("brick");
        }
        return id.endsWith("_fence_gate") || States.isFull(at(x, y, z));
    }

    private boolean paneJoins(int x, int y, int z) {
        String state = at(x, y, z);
        String id = States.id(state);
        return States.isPane(id) || States.isWall(id) || States.isFull(state);
    }

    private boolean wallJoins(int x, int y, int z) {
        String state = at(x, y, z);
        String id = States.id(state);
        return States.isWall(id) || States.isPane(id) || id.endsWith("_fence_gate") || States.isFull(state);
    }

    private String wall(String state, int x, int y, int z) {
        boolean north = wallJoins(x, y, z - 1);
        boolean south = wallJoins(x, y, z + 1);
        boolean west = wallJoins(x - 1, y, z);
        boolean east = wallJoins(x + 1, y, z);
        String above = at(x, y + 1, z);
        String aboveId = States.id(above);
        boolean covered = States.isFull(above);
        boolean line = north && south && !west && !east || west && east && !north && !south;
        // The post stays unless the wall runs straight on; a wall with a post or anything that stands on a post keeps it.
        boolean post = !line || States.isWall(aboveId) && above.contains("up=true")
                || !above.equals(AIR) && !covered && !States.isWall(aboveId) && !States.isPane(aboveId);
        return States.with(state, "north", arm(north, covered, above, "north"), "south", arm(south, covered, above, "south"),
                "west", arm(west, covered, above, "west"), "east", arm(east, covered, above, "east"),
                "up", String.valueOf(post), "waterlogged", "false");
    }

    private static String arm(boolean joins, boolean covered, String above, String side) {
        if (!joins) {
            return "none";
        }
        boolean raised = covered || above.contains(side + "=low") || above.contains(side + "=tall") || above.contains(side + "=true");
        return raised ? "tall" : "low";
    }

    /**
     * The structure as the game writes it: size, palette and every position with its state.
     */
    Nbt.Compound toNbt() {
        Map<String, Integer> palette = new LinkedHashMap<>();
        Nbt.ListTag blocks = new Nbt.ListTag();
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    String state = get(x, y, z);
                    if (state.equals(LEFT_OUT)) {
                        continue;
                    }
                    Integer id = palette.get(state);
                    if (id == null) {
                        id = palette.size();
                        palette.put(state, id);
                    }
                    blocks.add(new Nbt.Compound().put("pos", Nbt.ints(x, y, z)).put("state", id));
                }
            }
        }
        Nbt.ListTag paletteTag = new Nbt.ListTag();
        for (String state : palette.keySet()) {
            paletteTag.add(stateTag(state));
        }
        return new Nbt.Compound()
                .put("DataVersion", DATA_VERSION)
                .put("size", Nbt.ints(sizeX, sizeY, sizeZ))
                .put("palette", paletteTag)
                .put("blocks", blocks)
                .put("entities", new Nbt.ListTag());
    }

    private static Nbt.Compound stateTag(String state) {
        int open = state.indexOf('[');
        Nbt.Compound tag = new Nbt.Compound().put("Name", open < 0 ? state : state.substring(0, open));
        if (open >= 0) {
            Map<String, String> properties = new TreeMap<>();
            for (String pair : state.substring(open + 1, state.length() - 1).split(",")) {
                String[] parts = pair.split("=");
                properties.put(parts[0], parts[1]);
            }
            Nbt.Compound props = new Nbt.Compound();
            properties.forEach(props::put);
            tag.put("Properties", props);
        }
        return tag;
    }

    byte[] bytes() throws IOException {
        return Nbt.gzip(toNbt());
    }

    /**
     * Distinct block states used, without air.
     */
    List<String> used() {
        List<String> used = new ArrayList<>();
        for (String state : states) {
            if (state != null && !state.equals(LEFT_OUT) && !used.contains(state)) {
                used.add(state);
            }
        }
        return used;
    }
}
