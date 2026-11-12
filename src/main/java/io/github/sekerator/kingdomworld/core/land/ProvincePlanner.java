package io.github.sekerator.kingdomworld.core.land;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits the land of every state, and the neutral land around them, into provinces. A state of {@code n} cells gets about
 * {@code n / cellsPerProvince} provinces: the first seed is the throne, every next one the cell farthest (by the cost of the way)
 * from the seeds before it, and every cell goes to the seed it is cheapest to reach, so that borders follow rivers and ridges.
 * A province smaller than {@link ProvinceRules#minCells()} joins its smallest neighbour, one larger than
 * {@link ProvinceRules#maxCells()} is split in two halves. The province of the throne is the heart; one to three provinces at
 * the edge of the state, farthest from the throne, are its frontier; the rest are its core. The neutral land beyond the spawn
 * zone is split the same way into wild provinces, every piece of land on its own, a piece too small for a province joining the
 * nearest wild province.
 */
public final class ProvincePlanner {
    private static final int INF = Integer.MAX_VALUE;

    private ProvincePlanner() {
    }

    /**
     * The provinces, the thrones standing at the first candidates of the states.
     */
    public static ProvincePlan plan(StatePlan states, ProvinceRules rules, long seed) {
        return plan(states, null, rules, seed);
    }

    /**
     * The provinces.
     *
     * @param thrones the cell of the throne of every state as the server chose it, or null (or -1 for a state) for the first
     *                candidate; a cell outside the state is taken as its first candidate
     */
    public static ProvincePlan plan(StatePlan states, int[] thrones, ProvinceRules rules, long seed) {
        return new Run(states, thrones, rules, seed).plan();
    }

    /**
     * How many provinces of a state are its frontier.
     */
    public static int frontierCount(int provinces) {
        if (provinces >= 3) {
            return Math.max(1, Math.min(3, (int) Math.round(provinces / 7.0)));
        }
        return provinces == 2 ? 1 : 0;
    }

    private static final class Run {
        private final StatePlan states;
        private final SurveyRaster raster;
        private final ProvinceRules rules;
        private final long seed;
        private final int cells;
        private final int width;
        private final int[] thrones;
        private final int[] label;
        private final int[] dist;
        private final int[] member;
        private final int[] sub;
        private final LongHeap heap = new LongHeap(4096);
        private int memberStamp;
        private int subStamp;
        private int nextLabel;

        Run(StatePlan states, int[] thrones, ProvinceRules rules, long seed) {
            this.states = states;
            this.raster = states.raster();
            this.rules = rules;
            this.seed = seed;
            this.cells = raster.cells();
            this.width = raster.width();
            this.label = new int[cells];
            this.dist = new int[cells];
            this.member = new int[cells];
            this.sub = new int[cells];
            Arrays.fill(label, -1);
            int count = states.states().size();
            this.thrones = new int[count];
            for (int s = 0; s < count; s++) {
                PlannedState state = states.state(s);
                int chosen = thrones != null && s < thrones.length ? thrones[s] : -1;
                if (chosen < 0 || chosen >= cells || states.stateAt(chosen) != s) {
                    chosen = state.throneCandidates().isEmpty() ? state.seedCell() : state.throneCandidates().get(0);
                }
                this.thrones[s] = chosen;
            }
        }

        ProvincePlan plan() {
            int count = states.states().size();
            List<List<Integer>> stateCells = new ArrayList<>();
            for (int s = 0; s < count; s++) {
                stateCells.add(new ArrayList<>());
            }
            for (int cell = 0; cell < cells; cell++) {
                int s = states.stateAt(cell);
                if (s >= 0) {
                    stateCells.get(s).add(cell);
                }
            }
            List<int[]> groups = new ArrayList<>();
            List<int[]> groupLabels = new ArrayList<>();
            for (int s = 0; s < count; s++) {
                int[] group = toArray(stateCells.get(s));
                groups.add(group);
                if (group.length == 0) {
                    groupLabels.add(new int[0]);
                    continue;
                }
                int root = states.stateAt(thrones[s]) == s ? thrones[s] : group[0];
                groupLabels.add(partition(group, root));
            }
            // The neutral land, piece by piece.
            boolean[] wild = new boolean[cells];
            for (int cell = 0; cell < cells; cell++) {
                wild[cell] = states.stateAt(cell) < 0 && !states.inSpawnZone(cell) && raster.terrain(cell).passable();
            }
            boolean[] seen = new boolean[cells];
            List<int[]> wildGroups = new ArrayList<>();
            int[] queue = new int[cells];
            for (int start = 0; start < cells; start++) {
                if (!wild[start] || seen[start]) {
                    continue;
                }
                int head = 0;
                int tail = 0;
                queue[tail++] = start;
                seen[start] = true;
                while (head < tail) {
                    int cell = queue[head++];
                    int cx = cell % width;
                    int cz = cell / width;
                    for (int d = 0; d < 4; d++) {
                        int x = cx + LandMath.DX4[d];
                        int z = cz + LandMath.DZ4[d];
                        if (raster.contains(x, z)) {
                            int next = z * width + x;
                            if (wild[next] && !seen[next]) {
                                seen[next] = true;
                                queue[tail++] = next;
                            }
                        }
                    }
                }
                int[] group = Arrays.copyOf(queue, tail);
                Arrays.sort(group);
                wildGroups.add(group);
            }
            List<int[]> wildLabels = new ArrayList<>();
            for (int[] group : wildGroups) {
                wildLabels.add(partition(group, nearestSpawn(group)));
            }
            joinSmallPieces(wildGroups);
            return assemble(groups, wildGroups);
        }

        private int nearestSpawn(int[] group) {
            int best = group[0];
            double bestDistance = Double.MAX_VALUE;
            for (int cell : group) {
                double distance = StrictMath.hypot(raster.centerX(cell) - states.rules().spawnX(), raster.centerZ(cell) - states.rules().spawnZ());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = cell;
                }
            }
            return best;
        }

        /**
         * Splits a group of cells into provinces by seeds at the farthest points, then evens them out.
         *
         * @return the labels of its provinces
         */
        private int[] partition(int[] group, int root) {
            int base = nextLabel;
            int wanted = Math.max(1, (int) Math.round(group.length / (double) rules.cellsPerProvince()));
            memberStamp++;
            for (int cell : group) {
                member[cell] = memberStamp;
                dist[cell] = INF;
            }
            spread(root, nextLabel++);
            for (int k = 1; k < wanted; k++) {
                int far = -1;
                int farDistance = 0;
                for (int cell : group) {
                    if (dist[cell] > farDistance) {
                        farDistance = dist[cell];
                        far = cell;
                    }
                }
                if (far < 0) {
                    break;
                }
                spread(far, nextLabel++);
            }
            balance(group, base);
            return labelsOf(group);
        }

        /**
         * The way from a new seed: every cell of the group it reaches cheaper than the seeds before it becomes its own.
         */
        private void spread(int from, int lab) {
            heap.clear();
            dist[from] = 0;
            label[from] = lab;
            heap.add(LandMath.pack(0, 0, from));
            while (!heap.isEmpty()) {
                long entry = heap.poll();
                int cell = LandMath.cell(entry);
                int cost = LandMath.cost(entry);
                if (cost > dist[cell]) {
                    continue;
                }
                int cx = cell % width;
                int cz = cell / width;
                for (int d = 0; d < 4; d++) {
                    int x = cx + LandMath.DX4[d];
                    int z = cz + LandMath.DZ4[d];
                    if (!raster.contains(x, z)) {
                        continue;
                    }
                    int next = z * width + x;
                    if (member[next] != memberStamp) {
                        continue;
                    }
                    int through = cost + raster.terrain(next).cost();
                    if (through < dist[next]) {
                        dist[next] = through;
                        label[next] = lab;
                        heap.add(LandMath.pack(through, 0, next));
                    }
                }
            }
        }

        /**
         * Evens the provinces of a group out, round by round: every province too small joins its smallest neighbour, the
         * smallest first; then every province too large is split in two.
         */
        private void balance(int[] group, int base) {
            if (group.length < rules.minCells()) {
                return;
            }
            boolean[] whole = new boolean[0];
            for (int round = 0; round < 64; round++) {
                int labels = nextLabel - base;
                if (whole.length < labels) {
                    whole = Arrays.copyOf(whole, labels);
                }
                int[][] members = members(group, base, labels);
                int[] sizes = new int[labels];
                for (int i = 0; i < labels; i++) {
                    sizes[i] = members[i].length;
                }
                Integer[] small = smallFirst(sizes);
                boolean merged = false;
                boolean[] grew = new boolean[labels];
                for (int i : small) {
                    // A province that took in another this round waits for the next one, when its cells are counted anew.
                    if (sizes[i] == 0 || sizes[i] >= rules.minCells() || grew[i]) {
                        continue;
                    }
                    int into = smallestNeighbour(members[i], base + i, sizes, base);
                    if (into < 0) {
                        continue;
                    }
                    for (int cell : members[i]) {
                        if (label[cell] == base + i) {
                            label[cell] = into;
                        }
                    }
                    sizes[into - base] += sizes[i];
                    sizes[i] = 0;
                    grew[into - base] = true;
                    merged = true;
                }
                if (merged) {
                    continue;
                }
                boolean splitAny = false;
                for (int i = 0; i < labels; i++) {
                    if (sizes[i] > rules.maxCells() && !whole[i]) {
                        if (split(members[i], base + i)) {
                            splitAny = true;
                        } else {
                            whole[i] = true;
                        }
                    }
                }
                if (!splitAny) {
                    return;
                }
            }
        }

        /**
         * The cells of every province of a group.
         */
        private int[][] members(int[] group, int base, int labels) {
            int[] sizes = new int[labels];
            for (int cell : group) {
                sizes[label[cell] - base]++;
            }
            int[][] members = new int[labels][];
            for (int i = 0; i < labels; i++) {
                members[i] = new int[sizes[i]];
            }
            int[] filled = new int[labels];
            for (int cell : group) {
                int i = label[cell] - base;
                members[i][filled[i]++] = cell;
            }
            return members;
        }

        private static Integer[] smallFirst(int[] sizes) {
            Integer[] order = new Integer[sizes.length];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            Arrays.sort(order, (a, b) -> sizes[a] != sizes[b] ? Integer.compare(sizes[a], sizes[b]) : Integer.compare(a, b));
            return order;
        }

        private int smallestNeighbour(int[] part, int lab, int[] sizes, int base) {
            int best = -1;
            for (int cell : part) {
                if (label[cell] != lab) {
                    continue;
                }
                int cx = cell % width;
                int cz = cell / width;
                for (int d = 0; d < 4; d++) {
                    int x = cx + LandMath.DX4[d];
                    int z = cz + LandMath.DZ4[d];
                    if (!raster.contains(x, z)) {
                        continue;
                    }
                    int next = z * width + x;
                    if (member[next] != memberStamp || label[next] == lab) {
                        continue;
                    }
                    int other = label[next];
                    if (best < 0 || sizes[other - base] < sizes[best - base] || (sizes[other - base] == sizes[best - base] && other < best)) {
                        best = other;
                    }
                }
            }
            return best;
        }

        /**
         * Splits a province in two halves grown from its two farthest cells.
         */
        private boolean split(int[] group, int lab) {
            List<Integer> list = new ArrayList<>();
            for (int cell : group) {
                if (label[cell] == lab) {
                    list.add(cell);
                }
            }
            int[] part = toArray(list);
            if (part.length < 2) {
                return false;
            }
            subStamp++;
            for (int cell : part) {
                sub[cell] = subStamp;
            }
            int a = farthest(part, part[0]);
            int b = farthest(part, a);
            if (a == b) {
                return false;
            }
            int other = nextLabel++;
            for (int cell : part) {
                label[cell] = -2;
            }
            int[] labs = {lab, other};
            int[] goals = {(part.length + 1) / 2, part.length / 2};
            int[] counts = new int[2];
            heap.clear();
            heap.add(LandMath.pack(0, 0, a));
            heap.add(LandMath.pack(0, 1, b));
            while (!heap.isEmpty()) {
                long entry = heap.poll();
                int cell = LandMath.cell(entry);
                int i = LandMath.source(entry);
                if (label[cell] != -2 || counts[i] >= goals[i]) {
                    continue;
                }
                label[cell] = labs[i];
                counts[i]++;
                if (counts[i] >= goals[i]) {
                    continue;
                }
                int cx = cell % width;
                int cz = cell / width;
                for (int d = 0; d < 4; d++) {
                    int x = cx + LandMath.DX4[d];
                    int z = cz + LandMath.DZ4[d];
                    if (raster.contains(x, z)) {
                        int next = z * width + x;
                        if (sub[next] == subStamp && label[next] == -2) {
                            heap.add(LandMath.pack(LandMath.cost(entry) + raster.terrain(next).cost(), i, next));
                        }
                    }
                }
            }
            // Cells one half could not reach past the other go to a neighbour.
            for (boolean changed = true; changed; ) {
                changed = false;
                for (int cell : part) {
                    if (label[cell] != -2) {
                        continue;
                    }
                    int cx = cell % width;
                    int cz = cell / width;
                    for (int d = 0; d < 4; d++) {
                        int x = cx + LandMath.DX4[d];
                        int z = cz + LandMath.DZ4[d];
                        if (raster.contains(x, z)) {
                            int next = z * width + x;
                            if (sub[next] == subStamp && label[next] >= 0) {
                                label[cell] = label[next];
                                changed = true;
                                break;
                            }
                        }
                    }
                }
            }
            int kept = 0;
            for (int cell : part) {
                if (label[cell] == -2) {
                    label[cell] = lab;
                }
                if (label[cell] == lab) {
                    kept++;
                }
            }
            if (Math.min(kept, part.length - kept) < rules.minCells()) {
                // One half hemmed the other in: the nearer half of the cells by the way from the first far cell, the rest the
                // other half.
                farthest(part, a);
                Integer[] order = new Integer[part.length];
                for (int i = 0; i < order.length; i++) {
                    order[i] = part[i];
                }
                Arrays.sort(order, (x, y) -> dist[x] != dist[y] ? Integer.compare(dist[x], dist[y]) : Integer.compare(x, y));
                for (int i = 0; i < order.length; i++) {
                    label[order[i]] = i < (order.length + 1) / 2 ? lab : other;
                }
                if (part.length / 2 < rules.minCells()) {
                    for (int cell : part) {
                        label[cell] = lab;
                    }
                    return false;
                }
            }
            return true;
        }

        /**
         * The cell of a part farthest from a cell by the cost of the way, the first one on a tie.
         */
        private int farthest(int[] part, int from) {
            for (int cell : part) {
                dist[cell] = INF;
            }
            heap.clear();
            dist[from] = 0;
            heap.add(LandMath.pack(0, 0, from));
            while (!heap.isEmpty()) {
                long entry = heap.poll();
                int cell = LandMath.cell(entry);
                int cost = LandMath.cost(entry);
                if (cost > dist[cell]) {
                    continue;
                }
                int cx = cell % width;
                int cz = cell / width;
                for (int d = 0; d < 4; d++) {
                    int x = cx + LandMath.DX4[d];
                    int z = cz + LandMath.DZ4[d];
                    if (raster.contains(x, z)) {
                        int next = z * width + x;
                        int through = cost + raster.terrain(next).cost();
                        if (sub[next] == subStamp && through < dist[next]) {
                            dist[next] = through;
                            heap.add(LandMath.pack(through, 0, next));
                        }
                    }
                }
            }
            int best = from;
            int bestDistance = 0;
            for (int cell : part) {
                if (dist[cell] != INF && dist[cell] > bestDistance) {
                    bestDistance = dist[cell];
                    best = cell;
                }
            }
            return best;
        }

        private int[] labelsOf(int[] group) {
            List<Integer> labels = new ArrayList<>();
            boolean[] seenLabel = new boolean[nextLabel];
            for (int cell : group) {
                if (label[cell] >= 0 && !seenLabel[label[cell]]) {
                    seenLabel[label[cell]] = true;
                    labels.add(label[cell]);
                }
            }
            return toArray(labels);
        }

        /**
         * A piece of neutral land too small for a province of its own joins the wild province whose middle is nearest.
         */
        private void joinSmallPieces(List<int[]> wildGroups) {
            long[] sumX = new long[nextLabel];
            long[] sumZ = new long[nextLabel];
            int[] count = new int[nextLabel];
            boolean[] large = new boolean[nextLabel];
            for (int[] group : wildGroups) {
                for (int cell : group) {
                    int lab = label[cell];
                    sumX[lab] += raster.cellX(cell);
                    sumZ[lab] += raster.cellZ(cell);
                    count[lab]++;
                    large[lab] = group.length >= rules.minCells();
                }
            }
            for (int[] group : wildGroups) {
                if (group.length >= rules.minCells()) {
                    continue;
                }
                double gx = 0;
                double gz = 0;
                for (int cell : group) {
                    gx += raster.cellX(cell);
                    gz += raster.cellZ(cell);
                }
                gx /= group.length;
                gz /= group.length;
                int best = -1;
                double bestDistance = Double.MAX_VALUE;
                for (int lab = 0; lab < nextLabel; lab++) {
                    if (!large[lab] || count[lab] == 0) {
                        continue;
                    }
                    double distance = StrictMath.hypot(sumX[lab] / (double) count[lab] - gx, sumZ[lab] / (double) count[lab] - gz);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = lab;
                    }
                }
                if (best >= 0) {
                    for (int cell : group) {
                        label[cell] = best;
                    }
                }
            }
        }

        private ProvincePlan assemble(List<int[]> stateGroups, List<int[]> wildGroups) {
            int[] firstCell = new int[nextLabel];
            Arrays.fill(firstCell, -1);
            for (int cell = 0; cell < cells; cell++) {
                if (label[cell] >= 0 && firstCell[label[cell]] < 0) {
                    firstCell[label[cell]] = cell;
                }
            }
            int[] idOf = new int[nextLabel];
            int[] stateOf = new int[nextLabel + 1];
            int next = 1;
            List<Integer> order = new ArrayList<>();
            for (int s = 0; s < stateGroups.size(); s++) {
                int[] group = stateGroups.get(s);
                if (group.length == 0) {
                    continue;
                }
                int heart = label[thrones[s]] >= 0 && states.stateAt(thrones[s]) == s ? label[thrones[s]] : label[group[0]];
                List<Integer> labels = new ArrayList<>();
                for (int lab : labelsOf(group)) {
                    if (lab != heart) {
                        labels.add(lab);
                    }
                }
                labels.sort((x, y) -> Integer.compare(firstCell[x], firstCell[y]));
                labels.add(0, heart);
                for (int lab : labels) {
                    idOf[lab] = next;
                    stateOf[next] = s;
                    order.add(lab);
                    next++;
                }
            }
            List<Integer> wildOrder = new ArrayList<>();
            for (int[] group : wildGroups) {
                for (int lab : labelsOf(group)) {
                    if (idOf[lab] == 0 && !wildOrder.contains(lab)) {
                        wildOrder.add(lab);
                    }
                }
            }
            wildOrder.sort((x, y) -> Integer.compare(firstCell[x], firstCell[y]));
            for (int lab : wildOrder) {
                idOf[lab] = next;
                stateOf[next] = -1;
                order.add(lab);
                next++;
            }
            int total = next - 1;
            int[] province = new int[cells];
            int[] size = new int[total + 1];
            long[] sumX = new long[total + 1];
            long[] sumZ = new long[total + 1];
            for (int cell = 0; cell < cells; cell++) {
                if (label[cell] >= 0) {
                    int id = idOf[label[cell]];
                    province[cell] = id;
                    size[id]++;
                    sumX[id] += raster.cellX(cell);
                    sumZ[id] += raster.cellZ(cell);
                }
            }
            int[] center = new int[total + 1];
            double[] centerDistance = new double[total + 1];
            Arrays.fill(center, -1);
            Arrays.fill(centerDistance, Double.MAX_VALUE);
            for (int cell = 0; cell < cells; cell++) {
                int id = province[cell];
                if (id == 0) {
                    continue;
                }
                double distance = StrictMath.hypot(raster.cellX(cell) - sumX[id] / (double) size[id], raster.cellZ(cell) - sumZ[id] / (double) size[id]);
                if (distance < centerDistance[id]) {
                    centerDistance[id] = distance;
                    center[id] = cell;
                }
            }

            // Neighbours and the kinds of their borders, every edge between two cells counted once.
            Map<Long, int[]> edges = new HashMap<>();
            boolean[] outer = new boolean[total + 1];
            for (int cell = 0; cell < cells; cell++) {
                int id = province[cell];
                int cx = cell % width;
                int cz = cell / width;
                for (int d = 0; d < 4; d++) {
                    int x = cx + LandMath.DX4[d];
                    int z = cz + LandMath.DZ4[d];
                    if (!raster.contains(x, z)) {
                        if (id != 0) {
                            outer[id] = true;
                        }
                        continue;
                    }
                    int other = province[z * width + x];
                    if (id != 0 && stateOf[id] >= 0 && (other == 0 || stateOf[other] != stateOf[id])) {
                        outer[id] = true;
                    }
                    if (d % 2 == 1 || id == 0 || other == 0 || other == id) {
                        continue;
                    }
                    long key = ((long) Math.min(id, other) << 32) | Math.max(id, other);
                    int[] kinds = edges.computeIfAbsent(key, k -> new int[BorderKind.values().length]);
                    kinds[BorderKind.between(raster.terrain(cell), raster.terrain(z * width + x)).ordinal()]++;
                }
            }
            List<List<Integer>> neighbours = new ArrayList<>();
            List<List<BorderKind>> kinds = new ArrayList<>();
            for (int id = 0; id <= total; id++) {
                neighbours.add(new ArrayList<>());
                kinds.add(new ArrayList<>());
            }
            List<Long> keys = new ArrayList<>(edges.keySet());
            keys.sort(Long::compare);
            for (long key : keys) {
                int a = (int) (key >>> 32);
                int b = (int) key;
                BorderKind kind = majority(edges.get(key));
                neighbours.get(a).add(b);
                kinds.get(a).add(kind);
                neighbours.get(b).add(a);
                kinds.get(b).add(kind);
            }
            for (int id = 1; id <= total; id++) {
                sortTogether(neighbours.get(id), kinds.get(id));
            }

            ProvinceKind[] kindOf = new ProvinceKind[total + 1];
            for (int id = 1; id <= total; id++) {
                kindOf[id] = stateOf[id] < 0 ? ProvinceKind.WILD : ProvinceKind.CORE;
            }
            for (int s = 0; s < stateGroups.size(); s++) {
                List<Integer> mine = new ArrayList<>();
                for (int id = 1; id <= total; id++) {
                    if (stateOf[id] == s) {
                        mine.add(id);
                    }
                }
                if (mine.isEmpty()) {
                    continue;
                }
                int heart = mine.get(0);
                kindOf[heart] = ProvinceKind.HEART;
                int frontier = frontierCount(mine.size());
                int throne = thrones[s];
                List<Integer> edge = new ArrayList<>();
                for (int id : mine) {
                    if (id != heart && outer[id]) {
                        edge.add(id);
                    }
                }
                edge.sort((x, y) -> {
                    boolean nx = neighbours.get(x).contains(heart);
                    boolean ny = neighbours.get(y).contains(heart);
                    if (nx != ny) {
                        return nx ? 1 : -1;
                    }
                    double dx = StrictMath.hypot(raster.cellX(center[x]) - raster.cellX(throne), raster.cellZ(center[x]) - raster.cellZ(throne));
                    double dy = StrictMath.hypot(raster.cellX(center[y]) - raster.cellX(throne), raster.cellZ(center[y]) - raster.cellZ(throne));
                    return dx != dy ? Double.compare(dy, dx) : Integer.compare(x, y);
                });
                for (int i = 0; i < frontier && i < edge.size(); i++) {
                    kindOf[edge.get(i)] = ProvinceKind.FRONTIER;
                }
            }

            List<PlannedProvince> provinces = new ArrayList<>();
            for (int id = 1; id <= total; id++) {
                provinces.add(new PlannedProvince(id, stateOf[id], kindOf[id], size[id], center[id], raster.centerX(center[id]),
                        raster.centerZ(center[id]), neighbours.get(id), kinds.get(id), LandMath.mix(seed, 0x50524F56L, id)));
            }
            return new ProvincePlan(states, rules, seed, province, provinces, thrones);
        }

        private static BorderKind majority(int[] counts) {
            int best = 0;
            for (int i = 1; i < counts.length; i++) {
                if (counts[i] > counts[best]) {
                    best = i;
                }
            }
            return BorderKind.values()[best];
        }

        private static void sortTogether(List<Integer> ids, List<BorderKind> kinds) {
            Integer[] order = new Integer[ids.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            Arrays.sort(order, (x, y) -> Integer.compare(ids.get(x), ids.get(y)));
            List<Integer> sortedIds = new ArrayList<>();
            List<BorderKind> sortedKinds = new ArrayList<>();
            for (int i : order) {
                sortedIds.add(ids.get(i));
                sortedKinds.add(kinds.get(i));
            }
            ids.clear();
            ids.addAll(sortedIds);
            kinds.clear();
            kinds.addAll(sortedKinds);
        }

        private static int[] toArray(List<Integer> list) {
            int[] array = new int[list.size()];
            for (int i = 0; i < array.length; i++) {
                array[i] = list.get(i);
            }
            return array;
        }
    }
}
