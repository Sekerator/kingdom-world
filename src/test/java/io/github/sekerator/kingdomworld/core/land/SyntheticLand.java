package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.npc.NpcLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Surveys made up for the tests, and checks over plans.
 */
final class SyntheticLand {
    private SyntheticLand() {
    }

    /**
     * Open land, {@code width} by {@code height} cells, the block (0, 0) in the middle.
     */
    static SurveyRaster.Builder land(int width, int height) {
        SurveyRaster.Builder builder = new SurveyRaster.Builder(-width / 2 * SurveyRaster.CELL, -height / 2 * SurveyRaster.CELL, width, height);
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                builder.set(x, z, TerrainClass.LAND, 70);
            }
        }
        return builder;
    }

    /**
     * Fills a rectangle of cells with a class.
     */
    static SurveyRaster.Builder fill(SurveyRaster.Builder builder, int x0, int z0, int x1, int z1, TerrainClass terrain) {
        for (int z = Math.max(0, z0); z <= Math.min(builder.height() - 1, z1); z++) {
            for (int x = Math.max(0, x0); x <= Math.min(builder.width() - 1, x1); x++) {
                builder.terrain(x, z, terrain);
            }
        }
        return builder;
    }

    /**
     * A world like a surveyed one: a circle around the spawn at (0, 0) of the radius and 512 blocks, with seas, lakes, rivers,
     * hills, mountains, swamps and deserts by noise; beyond the circle nothing is known.
     *
     * @param sea how much of the land is under the sea: 0 for none, about 0.1 for a little
     */
    static SurveyRaster world(long seed, int radius, double sea) {
        int across = (int) Math.ceil((radius + 512) * 2.0 / SurveyRaster.CELL) + 2;
        across += across % 2;
        SurveyRaster.Builder builder = new SurveyRaster.Builder(-across / 2 * SurveyRaster.CELL, -across / 2 * SurveyRaster.CELL, across, across);
        double seaLevel = -1 + 2 * sea;
        for (int z = 0; z < across; z++) {
            for (int x = 0; x < across; x++) {
                double bx = (x - across / 2) * SurveyRaster.CELL + 32;
                double bz = (z - across / 2) * SurveyRaster.CELL + 32;
                if (Math.hypot(bx, bz) > radius + 512) {
                    continue;
                }
                double elevation = 0.6 * LandMath.noise(seed, bx, bz, 4096) + 0.3 * LandMath.noise(seed + 1, bx, bz, 1024)
                        + 0.1 * LandMath.noise(seed + 2, bx, bz, 256);
                TerrainClass terrain;
                if (sea > 0 && elevation < seaLevel) {
                    terrain = TerrainClass.OCEAN;
                } else if (sea > 0 && elevation < seaLevel + 0.04) {
                    terrain = TerrainClass.BEACH;
                } else if (Math.abs(LandMath.noise(seed + 4, bx, bz, 2048)) < 0.025) {
                    terrain = TerrainClass.RIVER;
                } else if (LandMath.noise(seed + 3, bx, bz, 512) > 0.8) {
                    terrain = TerrainClass.LAKE;
                } else if (elevation > 0.45) {
                    terrain = TerrainClass.STEEP;
                } else if (elevation > 0.3) {
                    terrain = TerrainClass.HILLS;
                } else if (LandMath.noise(seed + 5, bx, bz, 768) > 0.75) {
                    terrain = TerrainClass.SWAMP;
                } else if (LandMath.noise(seed + 6, bx, bz, 3000) > 0.6) {
                    terrain = TerrainClass.DESERT;
                } else {
                    terrain = TerrainClass.LAND;
                }
                builder.set(x, z, terrain, (int) (64 + elevation * 60));
            }
        }
        return builder.build();
    }

    /**
     * Rules over the whole of a small survey: the spawn in its middle with no zone, no limits on shares, the given sizes.
     */
    static StateRules openRules(int small, int medium, int large, int smallChunks, int mediumChunks, int largeChunks) {
        return new StateRules(0, 0, 0, 1_000_000, StateRules.GAP_BLOCKS, 0, 1, new int[]{small, medium, large},
                new int[]{smallChunks, mediumChunks, largeChunks}, new int[]{smallChunks, mediumChunks, largeChunks});
    }

    /**
     * The edge to edge distance between two cells, in blocks.
     */
    static double edgeDistance(SurveyRaster raster, int a, int b) {
        double ex = Math.max(0, Math.abs(raster.cellX(a) - raster.cellX(b)) - 1);
        double ez = Math.max(0, Math.abs(raster.cellZ(a) - raster.cellZ(b)) - 1);
        return Math.hypot(ex, ez) * SurveyRaster.CELL;
    }

    /**
     * Whether the cells of a state hang together.
     */
    static boolean connected(StatePlan plan, int state) {
        SurveyRaster raster = plan.raster();
        int start = -1;
        int total = 0;
        for (int cell = 0; cell < raster.cells(); cell++) {
            if (plan.stateAt(cell) == state) {
                total++;
                if (start < 0) {
                    start = cell;
                }
            }
        }
        if (start < 0) {
            return true;
        }
        boolean[] seen = new boolean[raster.cells()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start] = true;
        int reached = 0;
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            reached++;
            for (int d = 0; d < 4; d++) {
                int x = raster.cellX(cell) + LandMath.DX4[d];
                int z = raster.cellZ(cell) + LandMath.DZ4[d];
                if (raster.contains(x, z)) {
                    int next = raster.index(x, z);
                    if (!seen[next] && plan.stateAt(next) == state) {
                        seen[next] = true;
                        queue.add(next);
                    }
                }
            }
        }
        return reached == total;
    }

    /**
     * The cells of a state with a side on land outside it (not on the edge of the survey).
     */
    static List<Integer> borderCells(StatePlan plan, int state) {
        SurveyRaster raster = plan.raster();
        List<Integer> list = new ArrayList<>();
        for (int cell = 0; cell < raster.cells(); cell++) {
            if (plan.stateAt(cell) != state) {
                continue;
            }
            for (int d = 0; d < 4; d++) {
                int x = raster.cellX(cell) + LandMath.DX4[d];
                int z = raster.cellZ(cell) + LandMath.DZ4[d];
                if (raster.contains(x, z) && plan.stateAt(raster.index(x, z)) != state) {
                    list.add(cell);
                    break;
                }
            }
        }
        return list;
    }

    static int count(StatePlan plan, NpcLevel level) {
        int count = 0;
        for (PlannedState state : plan.states()) {
            if (state.level() == level) {
                count++;
            }
        }
        return count;
    }
}
