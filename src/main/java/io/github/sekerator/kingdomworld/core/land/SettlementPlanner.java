package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.npc.NpcLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Places the settlements of every state. How many of each rank a state has the seed of the world picks within the ranges of
 * its size. The capital stands at the throne. A large city stands in each core province, the nearest to the capital first, in
 * the middle of the province; small towns go to the core provinces without a city (two at most in each), then one to a
 * province with a city, then into the heart, then a second one to a province with a city. Villages and hamlets stand two to five around the capital, the cities and the
 * towns, 300 to 700 blocks from them. The frontier and the wild provinces stay empty.
 *
 * <p>A settlement stands on open land or a desert (a village or a hamlet on hills too) never generated nor land of a player,
 * with its own province on every side of its cell, which keeps it 64 blocks from the border of the province however the border is drawn, and the distances of its rank
 * from every other settlement. Every place comes with up to three cells around it that keep the same rules, for when the world
 * turns the place down.
 */
public final class SettlementPlanner {
    /** Blocks of the buckets the places are kept in for the checks of distance. */
    private static final int BUCKET = 1024;

    private SettlementPlanner() {
    }

    public static SettlementPlan plan(ProvincePlan provinces, SettlementRules rules, long seed) {
        return new Run(provinces, rules, seed).plan();
    }

    private static final class Run {
        private final ProvincePlan plan;
        private final StatePlan states;
        private final SurveyRaster raster;
        private final SettlementRules rules;
        private final long seed;
        private final boolean[] probed;
        private final int[][] provinceCells;
        private final List<SettlementSlot> slots = new ArrayList<>();
        private final List<List<SettlementSlot.Spot>> alternates = new ArrayList<>();
        private final Map<Long, List<Integer>> buckets = new HashMap<>();

        Run(ProvincePlan plan, SettlementRules rules, long seed) {
            this.plan = plan;
            this.states = plan.states();
            this.raster = plan.raster();
            this.rules = rules;
            this.seed = LandMath.mix(seed, 0x534554544C45L);
            this.probed = new boolean[raster.cells()];
            for (int cell : BorderRefiner.probeCells(plan)) {
                probed[cell] = true;
            }
            int total = plan.provinces().size();
            int[] sizes = new int[total + 1];
            for (int cell = 0; cell < raster.cells(); cell++) {
                sizes[plan.provinceAt(cell)]++;
            }
            provinceCells = new int[total + 1][];
            for (int id = 0; id <= total; id++) {
                provinceCells[id] = new int[sizes[id]];
            }
            int[] filled = new int[total + 1];
            for (int cell = 0; cell < raster.cells(); cell++) {
                int id = plan.provinceAt(cell);
                provinceCells[id][filled[id]++] = cell;
            }
        }

        SettlementPlan plan() {
            int count = states.states().size();
            int[][] wanted = new int[count][Tier.values().length];
            for (int s = 0; s < count; s++) {
                if (plan.provincesOf(s).isEmpty()) {
                    continue;
                }
                PlannedState state = states.state(s);
                NpcLevel level = state.level();
                SplittableRandom random = new SplittableRandom(LandMath.mix(seed, s));
                int cities = pick(random, rules.cities(level));
                int towns = pick(random, rules.towns(level));
                int children = pick(random, rules.villages(level));
                int villages = (int) Math.round(children * rules.villageShare());
                wanted[s][Tier.CAPITAL.ordinal()] = 1;
                wanted[s][Tier.CITY.ordinal()] = cities;
                wanted[s][Tier.TOWN.ordinal()] = towns;
                wanted[s][Tier.VILLAGE.ordinal()] = villages;
                wanted[s][Tier.HAMLET.ordinal()] = children - villages;
                placeState(s, cities, towns, villages, children - villages);
            }
            List<SettlementSlot> done = new ArrayList<>();
            for (int i = 0; i < slots.size(); i++) {
                SettlementSlot slot = slots.get(i);
                done.add(new SettlementSlot(slot.index(), slot.state(), slot.tier(), slot.x(), slot.z(), slot.cell(), slot.province(),
                        slot.parent(), alternatesOf(slot)));
            }
            return new SettlementPlan(plan, rules, done, wanted);
        }

        private static int pick(SplittableRandom random, int[] range) {
            return random.nextInt(range[0], range[1] + 1);
        }

        private void placeState(int s, int cities, int towns, int villages, int hamlets) {
            int throne = plan.throneCell(s);
            int capital = add(s, Tier.CAPITAL, throne, -1);
            List<Integer> core = new ArrayList<>();
            for (int id : plan.provincesOf(s)) {
                if (plan.province(id).kind() == ProvinceKind.CORE) {
                    core.add(id);
                }
            }
            core.sort(Comparator.comparingDouble((Integer id) -> distance(plan.province(id).centerCell(), throne)).thenComparingInt(id -> id));

            // Large cities: one in a core province, the nearest to the capital first.
            List<Integer> cityIndices = new ArrayList<>();
            boolean[] hasCity = new boolean[plan.provinces().size() + 1];
            for (int id : core) {
                if (cityIndices.size() >= cities) {
                    break;
                }
                int cell = middle(s, id, Tier.CITY);
                if (cell >= 0) {
                    cityIndices.add(add(s, Tier.CITY, cell, capital));
                    hasCity[id] = true;
                }
            }

            // Small towns: the core provinces without a city, one each and then a second; then one in a province with a
            // city; then the heart.
            List<Integer> townIndices = new ArrayList<>();
            int[] townsIn = new int[plan.provinces().size() + 1];
            List<int[]> passes = new ArrayList<>();
            for (int round = 1; round <= 2; round++) {
                for (int id : core) {
                    if (!hasCity[id]) {
                        passes.add(new int[]{id, round});
                    }
                }
            }
            for (int id : core) {
                if (hasCity[id]) {
                    passes.add(new int[]{id, 1});
                }
            }
            passes.add(new int[]{plan.heart(s), 1});
            passes.add(new int[]{plan.heart(s), 2});
            for (int id : core) {
                if (hasCity[id]) {
                    passes.add(new int[]{id, 2});
                }
            }
            for (int[] pass : passes) {
                if (townIndices.size() >= towns) {
                    break;
                }
                int id = pass[0];
                if (townsIn[id] >= pass[1]) {
                    continue;
                }
                int cell = spread(s, id, Tier.TOWN);
                if (cell >= 0) {
                    townIndices.add(add(s, Tier.TOWN, cell, nearestAbove(s, cell, Tier.TOWN)));
                    townsIn[id]++;
                }
            }

            // Villages and hamlets around the capital, the cities and the towns.
            List<Integer> parents = new ArrayList<>();
            parents.add(capital);
            parents.addAll(cityIndices);
            parents.addAll(townIndices);
            int children = villages + hamlets;
            // Two to each in turn, the capital first, then one more to each in turn up to five.
            int[] share = new int[parents.size()];
            int left = children;
            for (int p = 0; p < parents.size() && left > 0; p++) {
                share[p] = Math.min(rules.childrenMin(), left);
                left -= share[p];
            }
            for (boolean more = true; left > 0 && more; ) {
                more = false;
                for (int p = 0; p < parents.size() && left > 0; p++) {
                    if (share[p] < rules.childrenMax()) {
                        share[p]++;
                        left--;
                        more = true;
                    }
                }
            }
            int[] placed = new int[parents.size()];
            int made = 0;
            for (int p = 0; p < parents.size(); p++) {
                for (int k = 0; k < share[p]; k++) {
                    Tier tier = made < villages ? Tier.VILLAGE : Tier.HAMLET;
                    if (placeChild(s, tier, parents, p, placed)) {
                        made++;
                    }
                }
            }
        }

        /**
         * Places a village or a hamlet around a settlement, or around the next one with room if that has none.
         */
        private boolean placeChild(int s, Tier tier, List<Integer> parents, int first, int[] placed) {
            for (int step = 0; step < parents.size(); step++) {
                int p = (first + step) % parents.size();
                if (placed[p] >= rules.childrenMax()) {
                    continue;
                }
                int cell = around(s, parents.get(p), tier);
                if (cell >= 0) {
                    add(s, tier, cell, parents.get(p));
                    placed[p]++;
                    return true;
                }
            }
            return false;
        }

        /**
         * The cell of a province nearest its middle where a settlement of a rank may stand, or -1.
         */
        private int middle(int s, int province, Tier tier) {
            int center = plan.province(province).centerCell();
            int best = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int cell : provinceCells[province]) {
                if (!valid(s, cell, tier) || !apart(cell, tier, -1)) {
                    continue;
                }
                double distance = distance(cell, center);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = cell;
                }
            }
            return best;
        }

        /**
         * The cell of a province farthest from the settlements around it (up to twice the distance of the rank), the nearest
         * to its middle among equals, where a settlement of a rank may stand, or -1.
         */
        private int spread(int s, int province, Tier tier) {
            int center = plan.province(province).centerCell();
            double cap = 2.0 * rules.distance(tier);
            int best = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int cell : provinceCells[province]) {
                if (!valid(s, cell, tier) || !apart(cell, tier, -1)) {
                    continue;
                }
                double score = Math.min(cap, nearest(cell)) - 0.01 * distance(cell, center);
                if (score > bestScore) {
                    bestScore = score;
                    best = cell;
                }
            }
            return best;
        }

        /**
         * A cell 300 to 700 blocks from a settlement where a village or a hamlet may stand, away from the other settlements and
         * a little stirred by the seed, or -1.
         */
        private int around(int s, int parentIndex, Tier tier) {
            SettlementSlot parent = slots.get(parentIndex);
            int reach = rules.childMax() / SurveyRaster.CELL + 1;
            int px = raster.cellX(parent.cell());
            int pz = raster.cellZ(parent.cell());
            double middle = (rules.childMin() + rules.childMax()) / 2.0;
            int best = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    if (!raster.contains(px + dx, pz + dz)) {
                        continue;
                    }
                    int cell = raster.index(px + dx, pz + dz);
                    double from = Math.hypot(raster.centerX(cell) - parent.x(), raster.centerZ(cell) - parent.z());
                    if (from < rules.childMin() || from > rules.childMax() || !valid(s, cell, tier) || !apart(cell, tier, -1)) {
                        continue;
                    }
                    double jitter = LandMath.unit(LandMath.mix(seed, slots.size(), cell)) * 80;
                    double score = Math.min(400, nearest(cell)) + jitter - 0.15 * Math.abs(from - middle);
                    if (score > bestScore) {
                        bestScore = score;
                        best = cell;
                    }
                }
            }
            return best;
        }

        /**
         * Whether a settlement of a state may stand on a cell: open land or a desert (a village or a hamlet on hills too), never
         * generated nor land of a player, in the heart or a core province of the state, with the same province on every side
         * and no probed water around.
         */
        private boolean valid(int s, int cell, Tier tier) {
            TerrainClass terrain = raster.terrain(cell);
            boolean small = tier == Tier.VILLAGE || tier == Tier.HAMLET;
            if (!(terrain.settles() || (small && terrain == TerrainClass.HILLS)) || raster.generated(cell) || raster.player(cell)
                    || states.stateAt(cell) != s) {
                return false;
            }
            int province = plan.provinceAt(cell);
            ProvinceKind kind = plan.province(province).kind();
            if (kind != ProvinceKind.HEART && kind != ProvinceKind.CORE) {
                return false;
            }
            int cx = raster.cellX(cell);
            int cz = raster.cellZ(cell);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (!raster.contains(cx + dx, cz + dz)) {
                        return false;
                    }
                    int next = raster.index(cx + dx, cz + dz);
                    if (plan.provinceAt(next) != province || probed[next]) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Whether a settlement of a rank on a cell keeps its distance from every settlement placed but one.
         */
        private boolean apart(int cell, Tier tier, int except) {
            int x = raster.centerX(cell);
            int z = raster.centerZ(cell);
            for (int index : near(x, z)) {
                if (index == except) {
                    continue;
                }
                SettlementSlot other = slots.get(index);
                if (Math.hypot(other.x() - x, other.z() - z) < rules.apart(tier, other.tier())) {
                    return false;
                }
            }
            return true;
        }

        /**
         * The distance from a cell to the nearest settlement placed, as far as the buckets around it reach.
         */
        private double nearest(int cell) {
            int x = raster.centerX(cell);
            int z = raster.centerZ(cell);
            double least = BUCKET;
            for (int index : near(x, z)) {
                SettlementSlot other = slots.get(index);
                least = Math.min(least, Math.hypot(other.x() - x, other.z() - z));
            }
            return least;
        }

        private List<Integer> near(int x, int z) {
            List<Integer> list = new ArrayList<>();
            int bx = Math.floorDiv(x, BUCKET);
            int bz = Math.floorDiv(z, BUCKET);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    List<Integer> bucket = buckets.get(key(bx + dx, bz + dz));
                    if (bucket != null) {
                        list.addAll(bucket);
                    }
                }
            }
            return list;
        }

        private static long key(int bx, int bz) {
            return ((long) bx << 32) | (bz & 0xFFFFFFFFL);
        }

        /**
         * The settlement of a state of a higher rank nearest a cell.
         */
        private int nearestAbove(int s, int cell, Tier tier) {
            int best = -1;
            double bestDistance = Double.MAX_VALUE;
            for (SettlementSlot slot : slots) {
                if (slot.state() == s && slot.tier().above(tier)) {
                    double distance = distance(slot.cell(), cell);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = slot.index();
                    }
                }
            }
            return best;
        }

        private int add(int s, Tier tier, int cell, int parent) {
            int index = slots.size();
            int x = raster.centerX(cell);
            int z = raster.centerZ(cell);
            slots.add(new SettlementSlot(index, s, tier, x, z, cell, plan.provinceAt(cell), parent, List.of()));
            buckets.computeIfAbsent(key(Math.floorDiv(x, BUCKET), Math.floorDiv(z, BUCKET)), k -> new ArrayList<>()).add(index);
            return index;
        }

        /**
         * Cells around a settlement that keep its rules and the distances from all other settlements, the nearest first.
         */
        private List<SettlementSlot.Spot> alternatesOf(SettlementSlot slot) {
            List<SettlementSlot.Spot> list = new ArrayList<>();
            if (slot.tier() == Tier.CAPITAL) {
                return list;
            }
            SettlementSlot parent = slot.parent() >= 0 ? slots.get(slot.parent()) : null;
            int cx = raster.cellX(slot.cell());
            int cz = raster.cellZ(slot.cell());
            List<int[]> found = new ArrayList<>();
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    if ((dx == 0 && dz == 0) || !raster.contains(cx + dx, cz + dz)) {
                        continue;
                    }
                    int cell = raster.index(cx + dx, cz + dz);
                    if (!valid(slot.state(), cell, slot.tier()) || !apart(cell, slot.tier(), slot.index())) {
                        continue;
                    }
                    boolean child = slot.tier() == Tier.VILLAGE || slot.tier() == Tier.HAMLET;
                    if (!child && plan.provinceAt(cell) != slot.province()) {
                        continue;
                    }
                    if (child && parent != null) {
                        double from = Math.hypot(raster.centerX(cell) - parent.x(), raster.centerZ(cell) - parent.z());
                        if (from < rules.childMin() || from > rules.childMax()) {
                            continue;
                        }
                    }
                    found.add(new int[]{dx * dx + dz * dz, cell});
                }
            }
            found.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
            for (int i = 0; i < found.size() && list.size() < rules.alternates(); i++) {
                int cell = found.get(i)[1];
                list.add(new SettlementSlot.Spot(raster.centerX(cell), raster.centerZ(cell), cell, plan.provinceAt(cell)));
            }
            return list;
        }

        private double distance(int a, int b) {
            return Math.hypot(raster.centerX(a) - raster.centerX(b), raster.centerZ(a) - raster.centerZ(b));
        }
    }
}
