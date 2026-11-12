package io.github.sekerator.kingdomworld.core.land;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Draws the borders of the provinces chunk by chunk. Where a border runs through water (a cell of a river, a lake, a beach or
 * the sea at the border, {@link #probeCells(ProvincePlan)}), the server probes the biome of every chunk of the cell and the
 * chunks go to the province it is cheapest to reach them from, so that the border follows the river or the shore. Elsewhere a
 * chunk takes the province that weighs most among the four cells around its middle, blended between them and stirred by a smooth
 * noise of the seed of the world: the border runs between the cells in soft bends, not in steps of four chunks.
 *
 * <p>A chunk never goes further than {@value #REACH} blocks from the cells of its province; so the land of a state never
 * reaches into the spawn zone or onto the land of a player (whose cells keep their province chunk by chunk), and two states stay
 * the gap apart, their cells being the gap and twice the reach apart. The sea in a probed cell belongs to no province.
 */
public final class BorderRefiner {
    /** How far a drawn border takes the land of a province beyond its cells at most, in blocks. */
    public static final int REACH = 32;
    /** Blocks between the points of the noise of the borders. */
    static final int NOISE_STEP = 128;
    /** How much the noise weighs against the blend of the cells. */
    static final double NOISE_WEIGHT = 0.12;

    private BorderRefiner() {
    }

    /**
     * The cells whose chunks the server probes before the borders are drawn, in order: cells of a river, a lake, a beach or the
     * sea where two provinces, or a province and the sea, meet. Cells of the spawn zone and of the land of players are never
     * probed: they keep what they are.
     */
    public static int[] probeCells(ProvincePlan plan) {
        SurveyRaster raster = plan.raster();
        StatePlan states = plan.states();
        List<Integer> list = new ArrayList<>();
        for (int cell = 0; cell < raster.cells(); cell++) {
            if (needsProbe(plan, raster, states, cell)) {
                list.add(cell);
            }
        }
        int[] cells = new int[list.size()];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = list.get(i);
        }
        return cells;
    }

    private static boolean needsProbe(ProvincePlan plan, SurveyRaster raster, StatePlan states, int cell) {
        if (states.inSpawnZone(cell) || raster.player(cell) || !raster.terrain(cell).probedOnBorder()) {
            return false;
        }
        int own = plan.provinceAt(cell);
        int cx = raster.cellX(cell);
        int cz = raster.cellZ(cell);
        for (int d = 0; d < 4; d++) {
            int x = cx + LandMath.DX4[d];
            int z = cz + LandMath.DZ4[d];
            if (!raster.contains(x, z)) {
                continue;
            }
            int next = raster.index(x, z);
            int other = plan.provinceAt(next);
            if (other == own || states.inSpawnZone(next) || raster.player(next)) {
                continue;
            }
            if (other == 0 && raster.terrain(next) != TerrainClass.OCEAN) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * The provinces chunk by chunk. The water is drawn here once; the tiles are drawn when asked.
     *
     * @param probes the chunks the server probed; a cell that needs probes and has none is drawn by the noise
     */
    public static RefinedLand refine(ProvincePlan plan, ChunkProbes probes, long seed) {
        Work work = new Work(plan, probes, LandMath.mix(seed, 0x424F52444552L));
        return new RefinedLand(work, work.tiles());
    }

    /**
     * What the drawing needs: the plan, the probed chunks and where they went.
     */
    static final class Work {
        private final ProvincePlan plan;
        private final SurveyRaster raster;
        private final StatePlan states;
        private final long seed;
        private final int[] probeAt;
        private final int[] probedCells;
        private final byte[] probedClasses;
        private final char[] probed;

        Work(ProvincePlan plan, ChunkProbes probes, long seed) {
            this.plan = plan;
            this.raster = plan.raster();
            this.states = plan.states();
            this.seed = seed;
            this.probeAt = new int[raster.cells()];
            Arrays.fill(probeAt, -1);
            List<Integer> used = new ArrayList<>();
            for (int at = 0; at < probes.size(); at++) {
                int cell = probes.cell(at);
                if (cell >= 0 && cell < raster.cells() && needsProbe(plan, raster, states, cell)) {
                    used.add(at);
                }
            }
            probedCells = new int[used.size()];
            probedClasses = new byte[used.size() * SurveyRaster.CHUNKS_PER_CELL];
            for (int i = 0; i < used.size(); i++) {
                int at = used.get(i);
                probedCells[i] = probes.cell(at);
                probeAt[probedCells[i]] = i;
                for (int local = 0; local < SurveyRaster.CHUNKS_PER_CELL; local++) {
                    probedClasses[i * SurveyRaster.CHUNKS_PER_CELL + local] = probes.chunk(at, local).id();
                }
            }
            this.probed = drawWater();
        }

        /**
         * The chunks of the probed cells go to the province it is cheapest to reach them from, out of the cells around.
         */
        private char[] drawWater() {
            int count = probedCells.length * SurveyRaster.CHUNKS_PER_CELL;
            char[] result = new char[count];
            boolean[] done = new boolean[count];
            LongHeap heap = new LongHeap(Math.max(16, count));
            for (int i = 0; i < probedCells.length; i++) {
                int cell = probedCells[i];
                for (int local = 0; local < SurveyRaster.CHUNKS_PER_CELL; local++) {
                    int chunkX = raster.chunkX(cell) + (local & 3);
                    int chunkZ = raster.chunkZ(cell) + (local >> 2);
                    TerrainClass terrain = TerrainClass.byId(probedClasses[i * SurveyRaster.CHUNKS_PER_CELL + local]);
                    if (!terrain.passable()) {
                        continue;
                    }
                    for (int d = 0; d < 4; d++) {
                        int nx = chunkX + LandMath.DX4[d];
                        int nz = chunkZ + LandMath.DZ4[d];
                        int next = raster.cellOfChunk(nx, nz);
                        if (next < 0 || probeAt[next] >= 0 || states.inSpawnZone(next)) {
                            continue;
                        }
                        int province = plan.provinceAt(next);
                        if (province != 0) {
                            heap.add(pack(terrain.cost(), province, i * SurveyRaster.CHUNKS_PER_CELL + local));
                        }
                    }
                }
            }
            while (!heap.isEmpty()) {
                long entry = heap.poll();
                int chunk = chunkOf(entry);
                int province = provinceOf(entry);
                if (done[chunk]) {
                    continue;
                }
                int cell = probedCells[chunk / SurveyRaster.CHUNKS_PER_CELL];
                int local = chunk % SurveyRaster.CHUNKS_PER_CELL;
                int chunkX = raster.chunkX(cell) + (local & 3);
                int chunkZ = raster.chunkZ(cell) + (local >> 2);
                if (!allowed(cell, chunkX, chunkZ, province)) {
                    continue;
                }
                done[chunk] = true;
                result[chunk] = (char) province;
                int cost = costOf(entry);
                for (int d = 0; d < 4; d++) {
                    int nx = chunkX + LandMath.DX4[d];
                    int nz = chunkZ + LandMath.DZ4[d];
                    int next = raster.cellOfChunk(nx, nz);
                    if (next < 0 || probeAt[next] < 0) {
                        continue;
                    }
                    int nextChunk = probeAt[next] * SurveyRaster.CHUNKS_PER_CELL + ((nz - raster.chunkZ(next)) << 2) + (nx - raster.chunkX(next));
                    TerrainClass terrain = TerrainClass.byId(probedClasses[nextChunk]);
                    if (!done[nextChunk] && terrain.passable()) {
                        heap.add(pack(cost + terrain.cost(), province, nextChunk));
                    }
                }
            }
            // Dry chunks no province reached keep the province of their cell; the sea belongs to none.
            for (int chunk = 0; chunk < count; chunk++) {
                if (!done[chunk] && TerrainClass.byId(probedClasses[chunk]).passable()) {
                    result[chunk] = (char) plan.provinceAt(probedCells[chunk / SurveyRaster.CHUNKS_PER_CELL]);
                }
            }
            return result;
        }

        /**
         * Whether a chunk of a cell may go to a province: a wild one or one of the same state always, one of another state only
         * within the reach of a cell of that state.
         */
        private boolean allowed(int cell, int chunkX, int chunkZ, int province) {
            int state = plan.stateOf(province);
            if (state < 0 || states.stateAt(cell) == state) {
                return true;
            }
            int cx = raster.cellX(cell);
            int cz = raster.cellZ(cell);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (raster.contains(cx + dx, cz + dz)) {
                        int other = raster.index(cx + dx, cz + dz);
                        if (states.stateAt(other) == state && within(chunkX, chunkZ, other)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        /**
         * Whether every block of a chunk lies within the reach of a cell.
         */
        private boolean within(int chunkX, int chunkZ, int cell) {
            int minX = raster.originX() + raster.cellX(cell) * SurveyRaster.CELL;
            int minZ = raster.originZ() + raster.cellZ(cell) * SurveyRaster.CELL;
            int x0 = chunkX << 4;
            int z0 = chunkZ << 4;
            int fx = Math.max(0, Math.max(minX - x0, x0 + 16 - (minX + SurveyRaster.CELL)));
            int fz = Math.max(0, Math.max(minZ - z0, z0 + 16 - (minZ + SurveyRaster.CELL)));
            return (long) fx * fx + (long) fz * fz <= (long) REACH * REACH;
        }

        int provinceAt(int chunkX, int chunkZ) {
            int cell = raster.cellOfChunk(chunkX, chunkZ);
            if (cell < 0) {
                return 0;
            }
            int at = probeAt[cell];
            if (at >= 0) {
                return probed[at * SurveyRaster.CHUNKS_PER_CELL + ((chunkZ - raster.chunkZ(cell)) << 2) + (chunkX - raster.chunkX(cell))];
            }
            int own = plan.provinceAt(cell);
            if (own == 0 || raster.player(cell)) {
                return own;
            }
            // The blend of the four cells around the middle of the chunk.
            double px = (chunkX << 4) + 8;
            double pz = (chunkZ << 4) + 8;
            double u = (px - raster.originX()) / SurveyRaster.CELL - 0.5;
            double v = (pz - raster.originZ()) / SurveyRaster.CELL - 0.5;
            int i0 = (int) Math.floor(u);
            int j0 = (int) Math.floor(v);
            double fu = u - i0;
            double fv = v - j0;
            int[] ids = new int[4];
            double[] weights = new double[4];
            int found = 0;
            for (int k = 0; k < 4; k++) {
                int x = i0 + (k & 1);
                int z = j0 + (k >> 1);
                double weight = ((k & 1) == 0 ? 1 - fu : fu) * ((k >> 1) == 0 ? 1 - fv : fv);
                if (!raster.contains(x, z)) {
                    continue;
                }
                int other = raster.index(x, z);
                int province = plan.provinceAt(other);
                if (province == 0 || probeAt[other] >= 0 || raster.player(other) || states.inSpawnZone(other)) {
                    continue;
                }
                if (province != own && !within(chunkX, chunkZ, other)) {
                    continue;
                }
                int slot = 0;
                while (slot < found && ids[slot] != province) {
                    slot++;
                }
                if (slot == found) {
                    ids[found++] = province;
                }
                weights[slot] += weight;
            }
            int best = own;
            double bestWeight = Double.NEGATIVE_INFINITY;
            for (int slot = 0; slot < found; slot++) {
                double weight = weights[slot] + NOISE_WEIGHT * LandMath.noise(LandMath.mix(seed, ids[slot]), px, pz, NOISE_STEP);
                if (weight > bestWeight || (weight == bestWeight && (ids[slot] == own || (best != own && ids[slot] < best)))) {
                    bestWeight = weight;
                    best = ids[slot];
                }
            }
            return best;
        }

        /**
         * The tiles that hold a cell of a province or a probed cell, in order of their keys.
         */
        long[] tiles() {
            long[] keys = new long[raster.cells() * 4];
            int count = 0;
            for (int cell = 0; cell < raster.cells(); cell++) {
                if (plan.provinceAt(cell) == 0 && probeAt[cell] < 0) {
                    continue;
                }
                int x0 = Math.floorDiv(raster.chunkX(cell), RefinedLand.TILE);
                int x1 = Math.floorDiv(raster.chunkX(cell) + SurveyRaster.CELL_CHUNKS - 1, RefinedLand.TILE);
                int z0 = Math.floorDiv(raster.chunkZ(cell), RefinedLand.TILE);
                int z1 = Math.floorDiv(raster.chunkZ(cell) + SurveyRaster.CELL_CHUNKS - 1, RefinedLand.TILE);
                for (int tz = z0; tz <= z1; tz++) {
                    for (int tx = x0; tx <= x1; tx++) {
                        keys[count++] = RefinedLand.tileKey(tx, tz);
                    }
                }
            }
            long[] sorted = Arrays.copyOf(keys, count);
            Arrays.sort(sorted);
            int unique = 0;
            for (int i = 0; i < sorted.length; i++) {
                if (i == 0 || sorted[i] != sorted[i - 1]) {
                    sorted[unique++] = sorted[i];
                }
            }
            return Arrays.copyOf(sorted, unique);
        }

        private static long pack(int cost, int province, int chunk) {
            return ((long) Math.min(cost, (1 << 23) - 1) << 40) | ((long) province << 24) | chunk;
        }

        private static int costOf(long entry) {
            return (int) (entry >>> 40);
        }

        private static int provinceOf(long entry) {
            return (int) ((entry >>> 24) & 0xFFFF);
        }

        private static int chunkOf(long entry) {
            return (int) (entry & 0xFFFFFF);
        }
    }
}
