package io.github.sekerator.kingdomworld.core.land;

import java.util.Arrays;

/**
 * The class of every chunk of some cells of the survey, probed in the world where a border runs through water: one probe of
 * the biome for each chunk. Immutable.
 *
 * <p>The chunks of a cell are given row by row: chunk {@code (lx, lz)} of the cell (each from 0 to 3) is
 * {@code lz * 4 + lx}.
 */
public final class ChunkProbes {
    /** No probes: every border is drawn by the noise. */
    public static final ChunkProbes NONE = new ChunkProbes(new int[0], new byte[0]);

    private final int[] cells;
    private final byte[] classes;

    /**
     * @param cells   the probed cells
     * @param classes the classes of their chunks ({@link TerrainClass#id()}), {@value SurveyRaster#CHUNKS_PER_CELL} a cell in
     *                the order of the cells
     */
    public ChunkProbes(int[] cells, byte[] classes) {
        if (cells == null || classes == null || classes.length != cells.length * SurveyRaster.CHUNKS_PER_CELL) {
            throw new IllegalArgumentException("Probes need a class for each chunk of each cell");
        }
        int[] order = cells.clone();
        Arrays.sort(order);
        for (int i = 1; i < order.length; i++) {
            if (order[i] == order[i - 1]) {
                throw new IllegalArgumentException("A cell probed twice: " + order[i]);
            }
        }
        this.cells = cells.clone();
        this.classes = classes.clone();
    }

    public int size() {
        return cells.length;
    }

    /**
     * The probed cell at a place in the list.
     */
    public int cell(int at) {
        return cells[at];
    }

    /**
     * The class of a chunk of the probed cell at a place in the list.
     */
    public TerrainClass chunk(int at, int local) {
        return TerrainClass.byId(classes[at * SurveyRaster.CHUNKS_PER_CELL + local]);
    }
}
