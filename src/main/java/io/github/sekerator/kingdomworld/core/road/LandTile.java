package io.github.sekerator.kingdomworld.core.road;

import java.util.Arrays;
import java.util.UUID;

/**
 * The land of one chunk as a levelled road sees it, column by column in arrays: about twenty bytes a column, five kilobytes a
 * chunk. Made from the blocks of a loaded chunk, from a chunk read from the disk, or guessed from the noise of the generator for
 * land never generated; a guessed tile knows only the height of its ground.
 */
public final class LandTile {
    /** Columns of a tile. */
    public static final int SIZE = 256;
    private static final int CAVE_LIQUID = 1 << 30;

    private final int chunkX;
    private final int chunkZ;
    private final UUID owner;
    private final boolean guessed;
    private final int[] ground = new int[SIZE];
    private final int[] waterTop = new int[SIZE];
    private final byte[] soil = new byte[SIZE];
    private final short[] logs = new short[SIZE];
    private final int[] flags = new int[SIZE];
    private final int[] naturalTo = new int[SIZE];
    private final int[] caveTop = new int[SIZE];
    private final int[] caveBottom = new int[SIZE];

    /**
     * A tile of the columns given, in the order {@code (x & 15) * 16 + (z & 15)}.
     *
     * @param owner   the kingdom whose land the chunk is, {@code null} for nobody's
     * @param guessed whether the land is only guessed: the plan shows it as such and lays nothing on it before it is read
     */
    public LandTile(int chunkX, int chunkZ, UUID owner, boolean guessed, LandColumn[] columns) {
        if (columns.length != SIZE) {
            throw new IllegalArgumentException("A tile has " + SIZE + " columns");
        }
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.owner = owner;
        this.guessed = guessed;
        for (int i = 0; i < SIZE; i++) {
            LandColumn column = columns[i];
            ground[i] = column.ground();
            waterTop[i] = column.waterTop();
            soil[i] = (byte) Math.max(0, Math.min(LandColumn.DEEP_SOIL, column.soil()));
            logs[i] = (short) Math.max(0, Math.min(Short.MAX_VALUE, column.logs()));
            flags[i] = (column.flags() & ~(LandColumn.ROAD | LandColumn.OWN_ROAD)) | (column.caveLiquid() ? CAVE_LIQUID : 0);
            naturalTo[i] = column.naturalTo();
            caveTop[i] = column.caveTop();
            caveBottom[i] = column.caveBottom();
        }
    }

    /**
     * A guessed tile: plain natural land at the heights given, water up to the level of the sea where it lies below it.
     *
     * @param heights the guessed height of the ground of each column
     * @param sea     the level of the sea, or {@link LandColumn#NONE}
     */
    public static LandTile guessed(int chunkX, int chunkZ, UUID owner, int[] heights, int sea) {
        LandColumn[] columns = new LandColumn[SIZE];
        for (int i = 0; i < SIZE; i++) {
            int height = heights[i];
            columns[i] = sea != LandColumn.NONE && height < sea ? LandColumn.water(height, sea) : LandColumn.land(height, 3);
        }
        return new LandTile(chunkX, chunkZ, owner, true, columns);
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public UUID owner() {
        return owner;
    }

    public boolean guessed() {
        return guessed;
    }

    private static int index(int x, int z) {
        return (x & 15) * 16 + (z & 15);
    }

    /**
     * The column at a block of the chunk.
     */
    public LandColumn column(int x, int z) {
        int i = index(x, z);
        int f = flags[i];
        return new LandColumn(ground[i], waterTop[i], soil[i], logs[i], f & ~CAVE_LIQUID, LandColumn.NONE, naturalTo[i], caveTop[i],
                caveBottom[i], (f & CAVE_LIQUID) != 0);
    }

    public int ground(int x, int z) {
        return ground[index(x, z)];
    }

    /**
     * The top of what a road meets at a column: the water or the ground.
     */
    public int top(int x, int z) {
        int i = index(x, z);
        return waterTop[i] != LandColumn.NONE ? waterTop[i] : ground[i];
    }

    public boolean water(int x, int z) {
        return waterTop[index(x, z)] != LandColumn.NONE;
    }

    public int flags(int x, int z) {
        return flags[index(x, z)] & ~CAVE_LIQUID;
    }

    /**
     * The lowest, highest and mean height of the tops of its columns, and the share of them under water.
     */
    public double[] summary() {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        double sum = 0;
        int water = 0;
        for (int i = 0; i < SIZE; i++) {
            int top = waterTop[i] != LandColumn.NONE ? waterTop[i] : ground[i];
            min = Math.min(min, top);
            max = Math.max(max, top);
            sum += top;
            water += waterTop[i] != LandColumn.NONE ? 1 : 0;
        }
        return new double[]{min, max, sum / SIZE, water / (double) SIZE};
    }

    /**
     * The share of its columns built over.
     */
    public double builtShare() {
        int built = 0;
        for (int f : flags) {
            built += (f & LandColumn.BUILT) != 0 ? 1 : 0;
        }
        return built / (double) SIZE;
    }

    @Override
    public String toString() {
        return "LandTile[" + chunkX + ", " + chunkZ + (guessed ? ", guessed" : "") + ", ground " + Arrays.stream(ground).min().orElse(0) + "-"
                + Arrays.stream(ground).max().orElse(0) + "]";
    }
}
