package io.github.sekerator.kingdomworld.common.land;

import io.github.sekerator.kingdomworld.core.land.SurveyClassifier;
import io.github.sekerator.kingdomworld.core.land.SurveyRaster;
import io.github.sekerator.kingdomworld.core.land.TerrainClass;
import net.minecraft.server.level.ServerLevel;

/**
 * The looks of the survey of the land at the noise of the generator, without generating a chunk. A cell is looked at in four
 * points 16 blocks from its middle along both axes, at the height of the ground at its middle, which is also the height of the
 * cell ({@link SurveyClassifier#cell}). The height is a guess from the depth of the noise ({@link SurveyProbes#guess}), a hundred
 * times cheaper than a column of the noise; the column itself is looked at only where the guess lies near the sea and the height
 * decides between water and land.
 *
 * <p>Every look here reads only the generator, which answers any thread as it does while it generates chunks on its own threads:
 * the plan asks them of worker threads, a block of cells at a time, and puts the answers into the survey on the server thread.
 */
public final class LandSurvey {
    /** Blocks from the middle of a cell to its points, along both axes. */
    static final int PROBE_OFFSET = 16;

    private LandSurvey() {
    }

    /**
     * The class and the height of the ground of a cell around a block. Any thread.
     *
     * @return {class id, height}
     */
    public static int[] cell(ServerLevel level, int x, int z, int seaLevel) {
        Integer guess = SurveyProbes.guess(level, x, z);
        boolean exact = guess == null;
        int floor = exact ? SurveyProbes.floor(level, x, z) : guess;
        SurveyClassifier.Probe[] probes = {
                SurveyProbes.probe(level, x - PROBE_OFFSET, floor, z - PROBE_OFFSET),
                SurveyProbes.probe(level, x + PROBE_OFFSET, floor, z - PROBE_OFFSET),
                SurveyProbes.probe(level, x - PROBE_OFFSET, floor, z + PROBE_OFFSET),
                SurveyProbes.probe(level, x + PROBE_OFFSET, floor, z + PROBE_OFFSET)};
        if (!exact && SurveyProbes.nearSea(floor, seaLevel) && SurveyClassifier.heightDecides(probes)) {
            floor = SurveyProbes.floor(level, x, z);
        }
        return new int[]{SurveyClassifier.cell(probes, floor, seaLevel).id(), floor};
    }

    /**
     * The classes and heights of a block of cells of a survey, from a cell on, row by row: those whose middle lies beyond the
     * circle of the survey stay unknown without a look. Any thread.
     *
     * @return {class id, height} for each cell, one after another
     */
    public static int[] cells(ServerLevel level, int from, int count, int originX, int originZ, int width, int spawnX, int spawnZ, long reach,
                              int seaLevel) {
        int[] out = new int[2 * count];
        for (int i = 0; i < count; i++) {
            int cell = from + i;
            int x = originX + (cell % width) * SurveyRaster.CELL + SurveyRaster.CELL / 2;
            int z = originZ + (cell / width) * SurveyRaster.CELL + SurveyRaster.CELL / 2;
            long dx = x - (long) spawnX;
            long dz = z - (long) spawnZ;
            if (dx * dx + dz * dz > reach * reach) {
                out[2 * i] = TerrainClass.UNKNOWN.id();
                continue;
            }
            int[] look = cell(level, x, z, seaLevel);
            out[2 * i] = look[0];
            out[2 * i + 1] = look[1];
        }
        return out;
    }

    /**
     * The class of a chunk by one look at its middle at the height of its ground ({@link SurveyClassifier#chunk}): for a border
     * drawn along the water.
     */
    public static TerrainClass probeChunk(ServerLevel level, int chunkX, int chunkZ, int seaLevel) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        int floor = SurveyProbes.floor(level, x, z);
        return SurveyClassifier.chunk(SurveyProbes.probe(level, x, floor, z), floor, seaLevel);
    }

    /**
     * The class of a chunk of a cell of a class, as cheaply as it can be told: the biome at the level of the sea says the sea, a
     * river or the shore at once; the height of the ground, a dear look, is asked only in a cell of a lake, where it alone tells
     * the water from the land, and there only where its guess lies near the sea. Elsewhere a chunk of another biome is land (or a
     * swamp). Any thread.
     */
    public static TerrainClass probeChunk(ServerLevel level, int chunkX, int chunkZ, int seaLevel, TerrainClass cell) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        SurveyClassifier.Probe probe = SurveyProbes.probe(level, x, seaLevel, z);
        if (probe == SurveyClassifier.Probe.OCEAN || probe == SurveyClassifier.Probe.RIVER || probe == SurveyClassifier.Probe.BEACH
                || cell != TerrainClass.LAKE) {
            return SurveyClassifier.chunk(probe, seaLevel + 1, seaLevel);
        }
        Integer guess = SurveyProbes.guess(level, x, z);
        boolean exact = guess == null || SurveyProbes.nearSea(guess, seaLevel);
        return SurveyClassifier.chunk(probe, exact ? SurveyProbes.floor(level, x, z) : guess, seaLevel);
    }

    /**
     * The classes of the chunks of a cell of a border along the water ({@link io.github.sekerator.kingdomworld.core.land.ChunkProbes}
     * order, row by row). In a cell of a lake only the height of the ground tells the water: it is looked at once every two by
     * two chunks, the first of the four standing for them all. Any thread.
     */
    public static byte[] probeCell(ServerLevel level, int firstChunkX, int firstChunkZ, int seaLevel, TerrainClass cell) {
        int side = SurveyRaster.CELL_CHUNKS;
        byte[] classes = new byte[SurveyRaster.CHUNKS_PER_CELL];
        boolean lake = cell == TerrainClass.LAKE;
        for (int local = 0; local < classes.length; local++) {
            int lead = (local / side & ~1) * side + (local % side & ~1);
            if (lake && lead != local) {
                classes[local] = classes[lead];
                continue;
            }
            classes[local] = probeChunk(level, firstChunkX + local % side, firstChunkZ + local / side, seaLevel, cell).id();
        }
        return classes;
    }
}
