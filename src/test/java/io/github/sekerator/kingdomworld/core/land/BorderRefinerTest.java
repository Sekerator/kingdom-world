package io.github.sekerator.kingdomworld.core.land;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorderRefinerTest {
    private static final long SEED = 0xB0DE7L;

    /**
     * Probes for cells as a world would give them: a river one chunk wide in the second column of chunks of a river cell, the
     * sea in a sea cell with a strip of beach, the class of the cell elsewhere.
     */
    static ChunkProbes probes(SurveyRaster raster, int[] cells) {
        byte[] classes = new byte[cells.length * SurveyRaster.CHUNKS_PER_CELL];
        for (int i = 0; i < cells.length; i++) {
            TerrainClass terrain = raster.terrain(cells[i]);
            for (int local = 0; local < SurveyRaster.CHUNKS_PER_CELL; local++) {
                int lx = local & 3;
                TerrainClass chunk = switch (terrain) {
                    case RIVER -> lx == 1 ? TerrainClass.RIVER : TerrainClass.LAND;
                    case OCEAN -> lx == 0 ? TerrainClass.BEACH : TerrainClass.OCEAN;
                    default -> terrain;
                };
                classes[i * SurveyRaster.CHUNKS_PER_CELL + local] = chunk.id();
            }
        }
        return new ChunkProbes(cells, classes);
    }

    @Test
    void theBorderFollowsTheProbedRiverChunkByChunk() {
        ProvincePlan plan = ProvincePlannerTest.riverState();
        SurveyRaster raster = plan.raster();
        int[] cells = BorderRefiner.probeCells(plan);
        assertTrue(cells.length > 0);
        for (int cell : cells) {
            assertEquals(TerrainClass.RIVER, raster.terrain(cell), "only the river at the border is probed");
        }
        RefinedLand land = BorderRefiner.refine(plan, probes(raster, cells), SEED);
        int heart = plan.heart(0);
        int other = plan.provincesOf(0).get(1);
        int riverChunk = raster.chunkX(raster.index(15, 0)) + 1;
        for (int cz = 0; cz < raster.height(); cz++) {
            for (int lz = 0; lz < SurveyRaster.CELL_CHUNKS; lz++) {
                int chunkZ = raster.chunkZ(raster.index(0, cz)) + lz;
                for (int cx = 0; cx < raster.width() * SurveyRaster.CELL_CHUNKS; cx++) {
                    int chunkX = (raster.originX() >> 4) + cx;
                    int province = land.provinceAt(chunkX, chunkZ);
                    if (chunkX < riverChunk) {
                        assertEquals(heart, province, "west of the river at chunk " + chunkX + ", " + chunkZ);
                    } else if (chunkX > riverChunk) {
                        assertEquals(other, province, "east of the river at chunk " + chunkX + ", " + chunkZ);
                    } else {
                        assertTrue(province == heart || province == other);
                    }
                }
            }
        }
    }

    @Test
    void drawnBordersKeepTheGapTheSpawnZoneAndTheLandOfPlayers() {
        SurveyRaster world = SyntheticLand.world(SEED, StateRules.RADIUS, 0.08);
        SurveyRaster.Builder builder = new SurveyRaster.Builder(world.originX(), world.originZ(), world.width(), world.height());
        for (int cell = 0; cell < world.cells(); cell++) {
            builder.set(world.cellX(cell), world.cellZ(cell), world.terrain(cell), world.groundHeight(cell));
            if (Math.abs(world.centerZ(cell) - 3000) < 200 && world.centerX(cell) > 0) {
                builder.mark(world.cellX(cell), world.cellZ(cell), SurveyRaster.PLAYER);
            }
        }
        SurveyRaster raster = builder.build();
        StatePlan states = StatePlanner.plan(raster, StateRules.defaults(0, 0), SEED);
        ProvincePlan plan = ProvincePlanner.plan(states, ProvinceRules.DEFAULT, SEED);
        int[] cells = BorderRefiner.probeCells(plan);
        for (int i = 1; i < cells.length; i++) {
            assertTrue(cells[i] > cells[i - 1], "the cells to probe come in order");
        }
        RefinedLand land = BorderRefiner.refine(plan, probes(raster, cells), SEED);

        int chunksX = raster.width() * SurveyRaster.CELL_CHUNKS;
        int chunksZ = raster.height() * SurveyRaster.CELL_CHUNKS;
        int baseX = raster.originX() >> 4;
        int baseZ = raster.originZ() >> 4;
        int[] state = new int[chunksX * chunksZ];
        int[] counts = new int[plan.provinces().size() + 1];
        int moved = 0;
        for (int z = 0; z < chunksZ; z++) {
            for (int x = 0; x < chunksX; x++) {
                int province = land.provinceAt(baseX + x, baseZ + z);
                counts[province]++;
                state[z * chunksX + x] = plan.stateOf(province) + 1;
                int cell = raster.cellOfChunk(baseX + x, baseZ + z);
                if (province != plan.provinceAt(cell)) {
                    moved++;
                }
                if (province != 0 && plan.stateOf(province) >= 0) {
                    assertFalse(raster.player(cell), "no state on the land of a player");
                    double near = Math.hypot(Math.max(0, Math.max(-((baseX + x) * 16 + 16), (baseX + x) * 16)),
                            Math.max(0, Math.max(-((baseZ + z) * 16 + 16), (baseZ + z) * 16)));
                    assertTrue(near >= StateRules.SPAWN_ZONE, "no state in the spawn zone");
                }
            }
        }
        assertTrue(moved > 0, "the drawn borders leave the edges of the cells");
        long drawn = 0;
        long planned = 0;
        for (PlannedProvince province : plan.provinces()) {
            int chunks = counts[province.id()];
            drawn += chunks;
            planned += province.chunks();
            // A long border on a river moves up to half of every river cell to the other side.
            assertTrue(Math.abs(chunks - province.chunks()) <= 0.25 * province.chunks(),
                    "province " + province.id() + " has " + chunks + " chunks against " + province.chunks() + " " + province + " "
                            + terrains(plan, province.id()));
        }
        assertTrue(Math.abs(drawn - planned) <= 0.05 * planned, drawn + " chunks drawn against " + planned);
        // The chunks of states at their edges, and the least gap between two states.
        List<int[]> edge = new ArrayList<>();
        for (int z = 0; z < chunksZ; z++) {
            for (int x = 0; x < chunksX; x++) {
                int s = state[z * chunksX + x];
                if (s == 0) {
                    continue;
                }
                for (int d = 0; d < 4; d++) {
                    int nx = x + LandMath.DX4[d];
                    int nz = z + LandMath.DZ4[d];
                    if (nx >= 0 && nz >= 0 && nx < chunksX && nz < chunksZ && state[nz * chunksX + nx] != s) {
                        edge.add(new int[]{x, z, s});
                        break;
                    }
                }
            }
        }
        double least = Double.MAX_VALUE;
        for (int i = 0; i < edge.size(); i++) {
            int[] a = edge.get(i);
            for (int j = i + 1; j < edge.size(); j++) {
                int[] b = edge.get(j);
                if (a[2] == b[2]) {
                    continue;
                }
                double ex = Math.max(0, Math.abs(a[0] - b[0]) - 1);
                double ez = Math.max(0, Math.abs(a[1] - b[1]) - 1);
                least = Math.min(least, Math.hypot(ex, ez) * 16);
            }
        }
        assertTrue(least >= StateRules.GAP_BLOCKS, "two states " + least + " blocks apart");
    }

    static String terrains(ProvincePlan plan, int id) {
        StringBuilder text = new StringBuilder();
        for (int cell = 0; cell < plan.raster().cells(); cell++) {
            if (plan.provinceAt(cell) == id) {
                text.append(plan.raster().terrain(cell)).append(plan.raster().player(cell) ? "*" : "").append(' ');
            }
        }
        return text.toString();
    }

    @Test
    void tilesAreTheSameEveryTimeAndCoverTheProvinces() {
        StatePlan states = StatePlanner.plan(SyntheticLand.world(5, 4000, 0.1), StateRules.defaults(0, 0).withRing(1500, 4000), 5);
        ProvincePlan plan = ProvincePlanner.plan(states, ProvinceRules.DEFAULT, 5);
        ChunkProbes probes = probes(plan.raster(), BorderRefiner.probeCells(plan));
        RefinedLand first = BorderRefiner.refine(plan, probes, 5);
        RefinedLand second = BorderRefiner.refine(plan, probes, 5);
        long[] keys = first.tileKeys();
        assertTrue(keys.length > 0);
        assertTrue(java.util.Arrays.equals(keys, second.tileKeys()));
        long total = 0;
        for (long key : keys) {
            char[] tile = first.tile(RefinedLand.tileX(key), RefinedLand.tileZ(key));
            assertTrue(java.util.Arrays.equals(tile, second.tile(RefinedLand.tileX(key), RefinedLand.tileZ(key))));
            for (int i = 0; i < tile.length; i++) {
                int chunkX = RefinedLand.tileX(key) * RefinedLand.TILE + (i & 31);
                int chunkZ = RefinedLand.tileZ(key) * RefinedLand.TILE + (i >> 5);
                assertEquals(first.provinceAt(chunkX, chunkZ), tile[RefinedLand.indexInTile(chunkX, chunkZ)]);
                if (tile[i] != 0) {
                    total++;
                }
            }
        }
        long cells = 0;
        for (PlannedProvince province : plan.provinces()) {
            cells += province.chunks();
        }
        assertTrue(Math.abs(total - cells) <= 0.05 * cells, total + " chunks in tiles against " + cells);
    }
}
