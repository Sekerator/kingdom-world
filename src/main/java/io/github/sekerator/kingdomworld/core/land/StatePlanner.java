package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.npc.NpcLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Lays the states of a world over its survey. The seeds come first, the largest states first: on open land between the spawn
 * zone and the radius ({@code [S + r, R - r/2]} from the spawn, {@code r} the radius of a disc of the land of the state), apart
 * from each other by their radii and the gap, with most of the disc around them land. Then the land of all states grows from
 * the seeds at once, cell by cell in the order of the cost of the way from the seed (open land 1, hills 3, mountains 8, a river
 * or a lake 12, the sea not at all), and a state stops when it has its target. No state takes a cell near the land of another
 * (the gap and the reach of the drawn borders on both sides), a cell of the spawn zone or a cell of the land of a player; a state
 * hemmed in by the sea is kept when it got {@value #CRAMPED} of its target, otherwise its seed moves to another place and the land
 * grows again.
 *
 * <p>When the targets of all states together take more than the share of the ring the rules allow, or leave less neutral land
 * than they ask, all targets shrink by the same factor ({@link StatePlan#scale()}).
 */
public final class StatePlanner {
    /** The share of land the disc around a seed needs on the first pass over the candidates. */
    public static final double SEED_LAND = 0.6;
    /** The share of its target a hemmed-in state keeps its seed with. */
    public static final double CRAMPED = 0.6;
    /** How many times the seed of a state moves at most. */
    public static final int MAX_MOVES = 6;
    /** Cells for the throne a state offers at most. */
    public static final int THRONE_CANDIDATES = 24;
    /** Candidate seeds drawn on each pass. */
    static final int ATTEMPTS = 1500;
    /**
     * The passes over the candidate seeds: the share of the radii and the gap two seeds keep, and the share of land around a
     * seed. Later passes ask less.
     */
    private static final double[][] PASSES = {{1.0, SEED_LAND}, {0.85, 0.5}, {0.7, 0.4}};

    private StatePlanner() {
    }

    /**
     * Cells of land for an amount of chunks.
     */
    public static int cellsOf(int chunks) {
        return Math.max(1, Math.round(chunks / (float) SurveyRaster.CHUNKS_PER_CELL));
    }

    /**
     * The radius of a disc of so many chunks, in blocks.
     */
    public static double radiusOf(int chunks) {
        return 16 * Math.sqrt(chunks / Math.PI);
    }

    /**
     * The plan of the states of a world.
     */
    public static StatePlan plan(SurveyRaster raster, StateRules rules, long seed) {
        return new Run(raster, rules, seed, null).plan();
    }

    /**
     * The plan with the seeds of the first states given, the largest first, where they are not -1; those seeds never move.
     */
    static StatePlan plan(SurveyRaster raster, StateRules rules, long seed, int[] fixedSeeds) {
        return new Run(raster, rules, seed, fixedSeeds).plan();
    }

    /**
     * Where cells of two states may not lie: the cells around a cell closer, edge to edge, than the gap and the reach of the
     * drawn borders on both sides.
     */
    static int[][] gapOffsets(int gapBlocks) {
        double limit = (gapBlocks + 2.0 * BorderRefiner.REACH) / SurveyRaster.CELL;
        int reach = (int) Math.ceil(limit) + 1;
        List<int[]> offsets = new ArrayList<>();
        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                double ex = Math.max(0, Math.abs(dx) - 1);
                double ez = Math.max(0, Math.abs(dz) - 1);
                if (ex * ex + ez * ez < limit * limit) {
                    offsets.add(new int[]{dx, dz});
                }
            }
        }
        return offsets.toArray(new int[0][]);
    }

    private static final class Run {
        private final SurveyRaster raster;
        private final StateRules rules;
        private final long seed;
        private final int cells;
        private final boolean[] spawnZone;
        private final boolean[] free;
        private final int[] rowLand;
        private final int[][] gap;
        private final int[] fixed;

        Run(SurveyRaster raster, StateRules rules, long seed, int[] fixed) {
            this.raster = raster;
            this.rules = rules;
            this.seed = seed;
            this.fixed = fixed == null ? new int[0] : fixed.clone();
            this.cells = raster.cells();
            this.spawnZone = new boolean[cells];
            this.free = new boolean[cells];
            int width = raster.width();
            this.rowLand = new int[raster.height() * (width + 1)];
            for (int cell = 0; cell < cells; cell++) {
                spawnZone[cell] = raster.distanceTo(cell, rules.spawnX(), rules.spawnZ()) < rules.spawnZone();
                free[cell] = raster.terrain(cell).passable() && !raster.player(cell) && !spawnZone[cell];
            }
            for (int z = 0; z < raster.height(); z++) {
                int row = z * (width + 1);
                for (int x = 0; x < width; x++) {
                    int cell = raster.index(x, z);
                    rowLand[row + x + 1] = rowLand[row + x] + (free[cell] && raster.terrain(cell).dry() ? 1 : 0);
                }
            }
            this.gap = gapOffsets(rules.gapBlocks());
        }

        StatePlan plan() {
            int ringCells = 0;
            int landCells = 0;
            for (int cell = 0; cell < cells; cell++) {
                TerrainClass terrain = raster.terrain(cell);
                if (terrain == TerrainClass.UNKNOWN || spawnZone[cell]) {
                    continue;
                }
                double fromSpawn = Math.hypot(raster.centerX(cell) - rules.spawnX(), raster.centerZ(cell) - rules.spawnZ());
                if (fromSpawn <= rules.radius()) {
                    ringCells++;
                }
                if (terrain.dry()) {
                    landCells++;
                }
            }

            // The states of the quota, the largest first, each with its size from the seed of the world.
            List<NpcLevel> levels = new ArrayList<>();
            List<Integer> wanted = new ArrayList<>();
            List<Long> seeds = new ArrayList<>();
            long total = 0;
            NpcLevel[] all = NpcLevel.values();
            for (int l = all.length - 1; l >= 0; l--) {
                for (int k = 0; k < rules.quota(all[l]); k++) {
                    long stateSeed = LandMath.mix(seed, 0x5354415445L, levels.size());
                    int chunks = new SplittableRandom(stateSeed).nextInt(rules.minChunks(all[l]), rules.maxChunks(all[l]) + 1);
                    levels.add(all[l]);
                    wanted.add(chunks);
                    seeds.add(stateSeed);
                    total += cellsOf(chunks);
                }
            }
            double scale = 1;
            if (total > 0) {
                scale = Math.min(scale, rules.ringShare() * ringCells / total);
                scale = Math.min(scale, (1 - rules.neutralShare()) * landCells / total);
                scale = Math.max(0, scale);
            }
            int count = levels.size();
            int[] targetChunks = new int[count];
            int[] targets = new int[count];
            double[] radii = new double[count];
            Candidates[] streams = new Candidates[count];
            for (int s = 0; s < count; s++) {
                targetChunks[s] = (int) Math.round(wanted.get(s) * scale);
                targets[s] = cellsOf(targetChunks[s]);
                radii[s] = radiusOf(targetChunks[s]);
                streams[s] = new Candidates(LandMath.mix(seeds.get(s), 0x5345454453L), radii[s]);
            }

            // Seeds, the largest states first; a state without a place is left out.
            int[] seedCells = new int[count];
            java.util.Arrays.fill(seedCells, -1);
            for (int s = 0; s < count; s++) {
                seedCells[s] = s < fixed.length && fixed[s] >= 0 ? fixed[s] : streams[s].next(s, seedCells, radii);
            }
            List<Integer> placed = new ArrayList<>();
            for (int s = 0; s < count; s++) {
                if (seedCells[s] >= 0) {
                    placed.add(s);
                }
            }
            int missing = count - placed.size();
            int n = placed.size();
            int[] cellsOfSeed = new int[n];
            int[] goals = new int[n];
            double[] reach = new double[n];
            for (int i = 0; i < n; i++) {
                cellsOfSeed[i] = seedCells[placed.get(i)];
                goals[i] = targets[placed.get(i)];
                reach[i] = radii[placed.get(i)];
            }

            // The land grows; states hemmed in move their seeds while that helps.
            int[] moves = new int[n];
            Growth best = null;
            int[] bestSeeds = cellsOfSeed.clone();
            int[] bestMoves = moves.clone();
            double bestScore = -1;
            for (int round = 0; round <= MAX_MOVES; round++) {
                Growth growth = grow(cellsOfSeed, goals);
                double score = 0;
                for (int i = 0; i < n; i++) {
                    score += Math.min(1.0, growth.counts[i] / (double) goals[i]);
                }
                if (score > bestScore + 1e-9) {
                    best = growth;
                    bestScore = score;
                    bestSeeds = cellsOfSeed.clone();
                    bestMoves = moves.clone();
                }
                boolean moved = false;
                for (int i = 0; i < n; i++) {
                    boolean pinned = placed.get(i) < fixed.length && fixed[placed.get(i)] >= 0;
                    if (!pinned && growth.counts[i] < CRAMPED * goals[i] && moves[i] < MAX_MOVES) {
                        int next = streams[placed.get(i)].next(i, cellsOfSeed, reach);
                        if (next >= 0) {
                            cellsOfSeed[i] = next;
                            moves[i]++;
                            moved = true;
                        }
                    }
                }
                if (!moved) {
                    break;
                }
            }

            List<PlannedState> states = new ArrayList<>();
            int[] owner = best == null ? new int[cells] : best.owner;
            for (int i = 0; i < n; i++) {
                int s = placed.get(i);
                int seedCell = bestSeeds[i];
                int got = best == null ? 0 : best.counts[i];
                states.add(new PlannedState(i, levels.get(s), seeds.get(s), targetChunks[s], seedCell, raster.centerX(seedCell),
                        raster.centerZ(seedCell), got, got < goals[i], bestMoves[i], thrones(owner, i, seedCell, reach[i])));
            }
            return new StatePlan(raster, rules, seed, owner, spawnZone, states, scale, missing, ringCells, landCells);
        }

        /**
         * The land of all states grown at once from their seeds. First the states grow side by side until they meet or have
         * their targets, so that where two meet the border lies where the way was dearest (a river costs a state twelve steps
         * to cross, and the other comes up to its bank meanwhile). Then the gap is cut out of both sides of every such border
         * alike, and the states that lost land grow on where no other state is near.
         */
        private Growth grow(int[] seedCells, int[] goals) {
            int n = seedCells.length;
            int[] owner = new int[cells];
            int[] counts = new int[n];
            LongHeap heap = new LongHeap(4096);
            for (int s = 0; s < n; s++) {
                heap.add(LandMath.pack(0, s, seedCells[s]));
            }
            spread(heap, owner, null, counts, goals);
            cut(owner);
            int[] near = new int[cells];
            keep(owner, near, seedCells);
            java.util.Arrays.fill(counts, 0);
            int width = raster.width();
            for (int cell = 0; cell < cells; cell++) {
                if (owner[cell] != 0) {
                    counts[owner[cell] - 1]++;
                }
            }
            heap.clear();
            for (int cell = 0; cell < cells; cell++) {
                int s = owner[cell] - 1;
                if (s < 0 || counts[s] >= goals[s]) {
                    continue;
                }
                int cx = cell % width;
                int cz = cell / width;
                for (int d = 0; d < 4; d++) {
                    int x = cx + LandMath.DX4[d];
                    int z = cz + LandMath.DZ4[d];
                    if (raster.contains(x, z)) {
                        int next = z * width + x;
                        if (owner[next] == 0 && open(near, next, s)) {
                            heap.add(LandMath.pack(raster.terrain(next).cost(), s, next));
                        }
                    }
                }
            }
            spread(heap, owner, near, counts, goals);
            return new Growth(owner, counts);
        }

        /**
         * Claims cells in the order of the cost of the way until every state has its target or no way is left; with
         * {@code near}, no state comes within the gap of another.
         */
        private void spread(LongHeap heap, int[] owner, int[] near, int[] counts, int[] goals) {
            int width = raster.width();
            while (!heap.isEmpty()) {
                long entry = heap.poll();
                int cell = LandMath.cell(entry);
                int s = LandMath.source(entry);
                if (owner[cell] != 0 || counts[s] >= goals[s] || !open(near, cell, s)) {
                    continue;
                }
                owner[cell] = s + 1;
                counts[s]++;
                if (near != null) {
                    stamp(near, cell, s);
                }
                if (counts[s] >= goals[s]) {
                    continue;
                }
                int cost = LandMath.cost(entry);
                int cx = cell % width;
                int cz = cell / width;
                for (int d = 0; d < 4; d++) {
                    int x = cx + LandMath.DX4[d];
                    int z = cz + LandMath.DZ4[d];
                    if (!raster.contains(x, z)) {
                        continue;
                    }
                    int next = z * width + x;
                    if (owner[next] == 0 && open(near, next, s)) {
                        heap.add(LandMath.pack(cost + raster.terrain(next).cost(), s, next));
                    }
                }
            }
        }

        /**
         * Cuts the gap out of both sides of every border alike: a cell closer to the land of another state than half the gap is
         * given up.
         */
        private void cut(int[] owner) {
            int width = raster.width();
            double half = gapCells() / 2;
            boolean[] lose = new boolean[cells];
            for (int cell = 0; cell < cells; cell++) {
                int s = owner[cell];
                if (s == 0) {
                    continue;
                }
                int cx = cell % width;
                int cz = cell / width;
                for (int[] offset : gap) {
                    double ex = Math.max(0, Math.abs(offset[0]) - 1);
                    double ez = Math.max(0, Math.abs(offset[1]) - 1);
                    if (ex * ex + ez * ez >= half * half) {
                        continue;
                    }
                    int x = cx + offset[0];
                    int z = cz + offset[1];
                    if (raster.contains(x, z)) {
                        int other = owner[z * width + x];
                        if (other != 0 && other != s) {
                            lose[cell] = true;
                            break;
                        }
                    }
                }
            }
            for (int cell = 0; cell < cells; cell++) {
                if (lose[cell]) {
                    owner[cell] = 0;
                }
            }
        }

        /**
         * Keeps of every state the land joined to its seed (or its largest piece, if the seed was cut away) and nothing within
         * the gap of a state before it, and marks the land near every state that is left.
         */
        private void keep(int[] owner, int[] near, int[] seedCells) {
            int width = raster.width();
            int n = seedCells.length;
            int[] piece = new int[cells];
            int[] queue = new int[cells];
            int pieces = 0;
            for (int s = 0; s < n; s++) {
                // Land within the gap of the states kept before is given up.
                for (int cell = 0; cell < cells; cell++) {
                    if (owner[cell] == s + 1 && !open(near, cell, s)) {
                        owner[cell] = 0;
                    }
                }
                int best = -1;
                int bestSize = 0;
                boolean seedKept = owner[seedCells[s]] == s + 1;
                for (int start = 0; start < cells; start++) {
                    if (owner[start] != s + 1 || piece[start] != 0) {
                        continue;
                    }
                    int label = ++pieces;
                    int head = 0;
                    int tail = 0;
                    queue[tail++] = start;
                    piece[start] = label;
                    boolean hasSeed = false;
                    while (head < tail) {
                        int cell = queue[head++];
                        hasSeed |= cell == seedCells[s];
                        int cx = cell % width;
                        int cz = cell / width;
                        for (int d = 0; d < 4; d++) {
                            int x = cx + LandMath.DX4[d];
                            int z = cz + LandMath.DZ4[d];
                            if (raster.contains(x, z)) {
                                int next = z * width + x;
                                if (owner[next] == s + 1 && piece[next] == 0) {
                                    piece[next] = label;
                                    queue[tail++] = next;
                                }
                            }
                        }
                    }
                    if (seedKept ? hasSeed : tail > bestSize) {
                        best = label;
                        bestSize = seedKept ? Integer.MAX_VALUE : tail;
                    }
                }
                for (int cell = 0; cell < cells; cell++) {
                    if (owner[cell] == s + 1) {
                        if (piece[cell] != best) {
                            owner[cell] = 0;
                        } else {
                            stamp(near, cell, s);
                        }
                    }
                }
            }
        }

        private void stamp(int[] near, int cell, int s) {
            int width = raster.width();
            int cx = cell % width;
            int cz = cell / width;
            for (int[] offset : gap) {
                int x = cx + offset[0];
                int z = cz + offset[1];
                if (raster.contains(x, z)) {
                    int other = z * width + x;
                    if (near[other] == 0) {
                        near[other] = s + 1;
                    } else if (near[other] != s + 1) {
                        near[other] = -1;
                    }
                }
            }
        }

        /**
         * The gap between the cells of two states, edge to edge, in cells: the gap of the rules and the reach of the drawn
         * borders on both sides.
         */
        private double gapCells() {
            return (rules.gapBlocks() + 2.0 * BorderRefiner.REACH) / SurveyRaster.CELL;
        }

        private boolean open(int[] near, int cell, int state) {
            return free[cell] && (near == null || near[cell] == 0 || near[cell] == state + 1);
        }

        /**
         * Cells for the throne of a state around its seed, the nearest first: open land never generated, with land of the
         * state all around.
         */
        private List<Integer> thrones(int[] owner, int state, int seedCell, double radius) {
            int width = raster.width();
            int sx = seedCell % width;
            int sz = seedCell / width;
            int reach = (int) Math.max(8, Math.ceil(radius / 2 / SurveyRaster.CELL));
            List<long[]> found = new ArrayList<>();
            for (int strict = 1; strict >= 0 && found.isEmpty(); strict--) {
                for (int dz = -reach; dz <= reach; dz++) {
                    for (int dx = -reach; dx <= reach; dx++) {
                        int x = sx + dx;
                        int z = sz + dz;
                        if (!raster.contains(x, z) || dx * dx + dz * dz > reach * reach) {
                            continue;
                        }
                        int cell = z * width + x;
                        if (owner[cell] != state + 1 || !raster.terrain(cell).settles() || raster.generated(cell) || raster.player(cell)) {
                            continue;
                        }
                        if (strict == 1 && !surrounded(owner, state, x, z)) {
                            continue;
                        }
                        found.add(new long[]{(long) (dx * dx + dz * dz), cell});
                    }
                }
            }
            found.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < found.size() && list.size() < THRONE_CANDIDATES; i++) {
                list.add((int) found.get(i)[1]);
            }
            return list;
        }

        private boolean surrounded(int[] owner, int state, int x, int z) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (!raster.contains(x + dx, z + dz)) {
                        return false;
                    }
                    int cell = raster.index(x + dx, z + dz);
                    if (owner[cell] != state + 1 || !raster.terrain(cell).dry() || raster.terrain(cell) == TerrainClass.RIVER) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * The share of claimable land in a disc around a cell; cells beyond the survey count as no land.
         */
        private double landShare(int cell, double radius) {
            int width = raster.width();
            int cx = cell % width;
            int cz = cell / width;
            double rho = radius / SurveyRaster.CELL;
            int rows = (int) Math.floor(rho);
            long total = 0;
            long land = 0;
            for (int dz = -rows; dz <= rows; dz++) {
                int half = (int) Math.floor(Math.sqrt(rho * rho - (double) dz * dz));
                total += 2L * half + 1;
                int z = cz + dz;
                if (z < 0 || z >= raster.height()) {
                    continue;
                }
                int x0 = Math.max(0, cx - half);
                int x1 = Math.min(width - 1, cx + half);
                if (x1 >= x0) {
                    int row = z * (width + 1);
                    land += rowLand[row + x1 + 1] - rowLand[row + x0];
                }
            }
            return total == 0 ? 0 : (double) land / total;
        }

        /**
         * The candidate seeds of one state, drawn from its seed: uniform over its part of the ring, pass after pass.
         */
        private final class Candidates {
            private final SplittableRandom random;
            private final double radius;
            private int pass;
            private int attempt;

            Candidates(long seed, double radius) {
                this.random = new SplittableRandom(seed);
                this.radius = radius;
            }

            /**
             * The next candidate that keeps away from the other seeds, or -1 when the candidates ran out.
             */
            int next(int self, int[] seedCells, double[] radii) {
                double inner = rules.spawnZone() + radius;
                double outer = Math.max(inner, rules.radius() - radius / 2);
                while (pass < PASSES.length) {
                    if (attempt >= ATTEMPTS) {
                        pass++;
                        attempt = 0;
                        continue;
                    }
                    attempt++;
                    double angle = random.nextDouble() * Math.PI * 2;
                    double u = random.nextDouble();
                    double distance = Math.sqrt(inner * inner + u * (outer * outer - inner * inner));
                    int x = rules.spawnX() + (int) Math.round(Math.cos(angle) * distance);
                    int z = rules.spawnZ() + (int) Math.round(Math.sin(angle) * distance);
                    int cell = raster.cellAt(x, z);
                    if (cell < 0 || !free[cell] || !raster.terrain(cell).settles() || raster.generated(cell)) {
                        continue;
                    }
                    if (!apart(self, cell, seedCells, radii, PASSES[pass][0])) {
                        continue;
                    }
                    if (landShare(cell, radius) < PASSES[pass][1]) {
                        continue;
                    }
                    return cell;
                }
                return -1;
            }

            private boolean apart(int self, int cell, int[] seedCells, double[] radii, double factor) {
                for (int other = 0; other < seedCells.length; other++) {
                    if (other == self || seedCells[other] < 0) {
                        continue;
                    }
                    double distance = Math.hypot(raster.centerX(cell) - raster.centerX(seedCells[other]),
                            raster.centerZ(cell) - raster.centerZ(seedCells[other]));
                    if (distance < (radius + radii[other] + rules.gapBlocks()) * factor) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

    private record Growth(int[] owner, int[] counts) {
    }
}
