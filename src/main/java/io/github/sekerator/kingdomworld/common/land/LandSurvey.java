package io.github.sekerator.kingdomworld.common.land;

import io.github.sekerator.kingdomworld.core.land.SurveyClassifier;
import io.github.sekerator.kingdomworld.core.land.SurveyRaster;
import io.github.sekerator.kingdomworld.core.land.TerrainClass;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Surveys the land of the overworld a few cells at a time, from the noise of the generator without generating a chunk. A cell
 * is looked at in four points 16 blocks from its middle along both axes, at the height of the ground at its middle, which is also
 * the height of the cell ({@link SurveyClassifier#cell}). Cells whose middle lies beyond the circle of the survey stay unknown
 * without a look.
 */
public final class LandSurvey {
    /** Blocks from the middle of a cell to its points, along both axes. */
    static final int PROBE_OFFSET = 16;

    private LandSurvey() {
    }

    /**
     * Surveys cells from {@link SurveySavedData#nextCell()} on until the time is up, and ends the survey after the last one.
     *
     * @param deadlineNanos the {@link System#nanoTime()} after which no cell is begun
     * @return whether the survey is complete
     */
    public static boolean step(MinecraftServer server, SurveySavedData data, long deadlineNanos) {
        if (data.complete()) {
            return true;
        }
        if (!data.started()) {
            return false;
        }
        ServerLevel level = server.overworld();
        int cells = data.cells();
        int width = data.width();
        long reach = (long) data.radius() + SurveySavedData.MARGIN;
        int cell = data.nextCell();
        // A cell is not begun when one costing half as much again as the last would not end before the deadline: one look at the
        // ground costs a lot.
        long last = 0;
        long now = System.nanoTime();
        while (cell < cells && now - deadlineNanos < 0 && now + last + last / 2 - deadlineNanos < 0) {
            long start = now;
            int x = data.originX() + (cell % width) * SurveyRaster.CELL + SurveyRaster.CELL / 2;
            int z = data.originZ() + (cell / width) * SurveyRaster.CELL + SurveyRaster.CELL / 2;
            long dx = x - (long) data.spawnX();
            long dz = z - (long) data.spawnZ();
            if (dx * dx + dz * dz > reach * reach) {
                data.put(cell, TerrainClass.UNKNOWN, 0);
            } else {
                int floor = SurveyProbes.floor(level, x, z);
                SurveyClassifier.Probe[] probes = {
                        SurveyProbes.probe(level, x - PROBE_OFFSET, floor, z - PROBE_OFFSET),
                        SurveyProbes.probe(level, x + PROBE_OFFSET, floor, z - PROBE_OFFSET),
                        SurveyProbes.probe(level, x - PROBE_OFFSET, floor, z + PROBE_OFFSET),
                        SurveyProbes.probe(level, x + PROBE_OFFSET, floor, z + PROBE_OFFSET)};
                data.put(cell, SurveyClassifier.cell(probes, floor, data.seaLevel()), floor);
            }
            cell++;
            data.advance(cell);
            now = System.nanoTime();
            if (dx * dx + dz * dz <= reach * reach) {
                last = now - start;
            }
        }
        if (cell >= cells) {
            data.finish();
            return true;
        }
        return false;
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
     * river or the shore at once; the height of the ground, the dear look, is asked only in a cell of a lake, where it alone tells
     * the water from the land. Elsewhere a chunk of another biome is land (or a swamp).
     */
    public static TerrainClass probeChunk(ServerLevel level, int chunkX, int chunkZ, int seaLevel, TerrainClass cell) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        SurveyClassifier.Probe probe = SurveyProbes.probe(level, x, seaLevel, z);
        if (probe == SurveyClassifier.Probe.OCEAN || probe == SurveyClassifier.Probe.RIVER || probe == SurveyClassifier.Probe.BEACH
                || cell != TerrainClass.LAKE) {
            groundLooked = false;
            return SurveyClassifier.chunk(probe, seaLevel + 1, seaLevel);
        }
        groundLooked = true;
        return SurveyClassifier.chunk(probe, SurveyProbes.floor(level, x, z), seaLevel);
    }

    /** Whether the last chunk probed by its cell needed the height of its ground. Server thread only. */
    private static boolean groundLooked;

    /**
     * Whether the last chunk probed by its cell ({@link #probeChunk(ServerLevel, int, int, int, TerrainClass)}) needed the dear
     * look at the height of its ground.
     */
    public static boolean lookedAtGround() {
        return groundLooked;
    }
}
