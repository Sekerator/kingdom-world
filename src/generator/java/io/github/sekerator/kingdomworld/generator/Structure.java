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
