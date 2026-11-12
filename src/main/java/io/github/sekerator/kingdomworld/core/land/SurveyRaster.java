package io.github.sekerator.kingdomworld.core.land;

import java.util.Arrays;

/**
 * The survey of the land: a grid of cells of {@value #CELL} blocks (4 by 4 chunks), each with its class, the height of its
 * ground and two marks: chunks of it were generated already, and land of a player (with the four chunks around it) lies in it.
 * Immutable; the planners read it from any thread.
 *
 * <p>Cell {@code (0, 0)} begins at the block {@link #originX()}, {@link #originZ()}, a corner of a chunk. A cell is numbered
 * {@code cz * width + cx}.
 */
public final class SurveyRaster {
    /** Blocks across a cell. */
    public static final int CELL = 64;
    /** Chunks across a cell. */
    public static final int CELL_CHUNKS = CELL / 16;
    /** Chunks of a cell. */
    public static final int CHUNKS_PER_CELL = CELL_CHUNKS * CELL_CHUNKS;
    /** The mark of a cell where chunks were generated already. */
    public static final int GENERATED = 1;
    /** The mark of a cell where land of a player, or the four chunks around it, lies. */
    public static final int PLAYER = 2;

    private final int originX;
    private final int originZ;
    private final int width;
    private final int height;
    private final byte[] classes;
    private final short[] heights;
    private final byte[] marks;

    /**
     * @param classes the class of every cell ({@link TerrainClass#id()})
     * @param heights the height of the ground of every cell
     * @param marks   the marks of every cell ({@link #GENERATED}, {@link #PLAYER}), or null for none
     */
    public SurveyRaster(int originX, int originZ, int width, int height, byte[] classes, short[] heights, byte[] marks) {
        if ((originX & 15) != 0 || (originZ & 15) != 0) {
            throw new IllegalArgumentException("The survey begins at the corner of a chunk");
        }
        if (width < 1 || height < 1 || (long) width * height > 1 << 24) {
            throw new IllegalArgumentException("A survey of " + width + " by " + height + " cells");
        }
        int cells = width * height;
        if (classes == null || classes.length != cells || heights == null || heights.length != cells
                || (marks != null && marks.length != cells)) {
            throw new IllegalArgumentException("The survey needs a class and a height for each of its " + cells + " cells");
        }
        this.originX = originX;
        this.originZ = originZ;
        this.width = width;
        this.height = height;
        this.classes = classes.clone();
        this.heights = heights.clone();
        this.marks = marks == null ? new byte[cells] : marks.clone();
    }

    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int cells() {
        return width * height;
    }

    public int index(int cx, int cz) {
        return cz * width + cx;
    }

    public int cellX(int cell) {
        return cell % width;
    }

    public int cellZ(int cell) {
        return cell / width;
    }

    public boolean contains(int cx, int cz) {
        return cx >= 0 && cz >= 0 && cx < width && cz < height;
    }

    /**
     * The cell a block lies in, or -1 outside the survey.
     */
    public int cellAt(int blockX, int blockZ) {
        int cx = Math.floorDiv(blockX - originX, CELL);
        int cz = Math.floorDiv(blockZ - originZ, CELL);
        return contains(cx, cz) ? index(cx, cz) : -1;
    }

    /**
     * The cell a chunk lies in, or -1 outside the survey.
     */
    public int cellOfChunk(int chunkX, int chunkZ) {
        return cellAt(chunkX << 4, chunkZ << 4);
    }

    /** The block in the middle of a cell, along x. */
    public int centerX(int cell) {
        return originX + cellX(cell) * CELL + CELL / 2;
    }

    /** The block in the middle of a cell, along z. */
    public int centerZ(int cell) {
        return originZ + cellZ(cell) * CELL + CELL / 2;
    }

    /** The first chunk of a cell along x. */
    public int chunkX(int cell) {
        return (originX >> 4) + cellX(cell) * CELL_CHUNKS;
    }

    /** The first chunk of a cell along z. */
    public int chunkZ(int cell) {
        return (originZ >> 4) + cellZ(cell) * CELL_CHUNKS;
    }

    public TerrainClass terrain(int cell) {
        return TerrainClass.byId(classes[cell]);
    }

    public int groundHeight(int cell) {
        return heights[cell];
    }

    public boolean generated(int cell) {
        return (marks[cell] & GENERATED) != 0;
    }

    public boolean player(int cell) {
        return (marks[cell] & PLAYER) != 0;
    }

    /**
     * The least distance in blocks from a point to the blocks of a cell (0 inside it).
     */
    public double distanceTo(int cell, double x, double z) {
        double minX = originX + cellX(cell) * (double) CELL;
        double minZ = originZ + cellZ(cell) * (double) CELL;
        double dx = Math.max(0, Math.max(minX - x, x - (minX + CELL)));
        double dz = Math.max(0, Math.max(minZ - z, z - (minZ + CELL)));
        return Math.hypot(dx, dz);
    }

    /**
     * The greatest distance in blocks from a point to the blocks of a cell.
     */
    public double farthestFrom(int cell, double x, double z) {
        double minX = originX + cellX(cell) * (double) CELL;
        double minZ = originZ + cellZ(cell) * (double) CELL;
        double dx = Math.max(Math.abs(minX - x), Math.abs(minX + CELL - x));
        double dz = Math.max(Math.abs(minZ - z), Math.abs(minZ + CELL - z));
        return Math.hypot(dx, dz);
    }

    /**
     * A copy of the classes of the cells, as stored.
     */
    public byte[] classes() {
        return classes.clone();
    }

    /**
     * A copy of the heights of the cells, as stored.
     */
    public short[] heights() {
        return heights.clone();
    }

    /**
     * A copy of the marks of the cells, as stored.
     */
    public byte[] marks() {
        return marks.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SurveyRaster raster && raster.originX == originX && raster.originZ == originZ && raster.width == width
                && raster.height == height && Arrays.equals(raster.classes, classes) && Arrays.equals(raster.heights, heights)
                && Arrays.equals(raster.marks, marks);
    }

    @Override
    public int hashCode() {
        return ((originX * 31 + originZ) * 31 + width) * 31 + Arrays.hashCode(classes);
    }

    /**
     * A survey filled cell by cell; everything not set is {@link TerrainClass#UNKNOWN} at height 0 without marks.
     */
    public static final class Builder {
        private final int originX;
        private final int originZ;
        private final int width;
        private final int height;
        private final byte[] classes;
        private final short[] heights;
        private final byte[] marks;

        public Builder(int originX, int originZ, int width, int height) {
            if (width < 1 || height < 1 || (long) width * height > 1 << 24) {
                throw new IllegalArgumentException("A survey of " + width + " by " + height + " cells");
            }
            this.originX = originX;
            this.originZ = originZ;
            this.width = width;
            this.height = height;
            this.classes = new byte[width * height];
            this.heights = new short[width * height];
            this.marks = new byte[width * height];
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public Builder set(int cx, int cz, TerrainClass terrain, int groundHeight) {
            int cell = cz * width + cx;
            classes[cell] = terrain.id();
            heights[cell] = (short) groundHeight;
            return this;
        }

        public Builder terrain(int cx, int cz, TerrainClass terrain) {
            classes[cz * width + cx] = terrain.id();
            return this;
        }

        public TerrainClass terrain(int cx, int cz) {
            return TerrainClass.byId(classes[cz * width + cx]);
        }

        public Builder mark(int cx, int cz, int mark) {
            marks[cz * width + cx] |= (byte) mark;
            return this;
        }

        public SurveyRaster build() {
            return new SurveyRaster(originX, originZ, width, height, classes, heights, marks);
        }
    }
}
