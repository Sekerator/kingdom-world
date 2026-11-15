package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The heights of a levelled road along its way, found by dynamic programming over the whole way: every block may lie from 24
 * below to 24 above the height the search gave it (over water above the water), the road climbs or falls a block at most once in
 * {@code k} blocks, and a block costs the exact work of its cross-section, the middle, the way beside it and the verges, with
 * every change of height a quarter of a plain block dearer. Crossings of roads and gates are pinned to their height, the ends
 * kept within a block of their ground. Where some block has no height it may take, the window is doubled between the pins round
 * it, up to the whole height of the world, so that there is always a profile.
 *
 * <p>Afterwards a tunnel shorter than four blocks becomes a cutting, and a cutting of up to two blocks between two tunnels a tunnel
 * where it may.
 */
public final class RoadProfile {
    /** Blocks above and below the height of the search a block may take at first. */
    public static final int WINDOW = 24;
    /** The penalty of an end farther than a block from its ground. */
    static final double SOFT = 1e6;

    /**
     * What a profile is made from.
     *
     * @param cells the way, x and z of its blocks side by side
     * @param heights the height the search gave each block, or the ground for a way it did not search
     * @param pins  heights blocks must take, by index
     * @param lows  the lowest height each block may take at first, or {@code null} for {@value #WINDOW} below its height
     * @param highs the highest, or {@code null} for {@value #WINDOW} above
     */
    public record Input(List<int[]> cells, int[] heights, Map<Integer, Integer> pins, int[] lows, int[] highs) {
        public Input(List<int[]> cells, int[] heights, Map<Integer, Integer> pins) {
            this(cells, heights, pins, null, null);
        }
    }

    /**
     * The profile: the height and kind of every block.
     */
    public record Result(int[] heights, RouteCell.Kind[] kinds, double cost, int windows) {
    }

    private RoadProfile() {
    }

    /**
     * The kind of a block whose middle is a column at a height, or {@code null} where the road may not be there: the same rules as
     * the search ({@link CellCosts}).
     *
     * @param waterNear the highest water within the width of the road and a block, or {@link LandColumn#NONE}
     */
    public static RouteCell.Kind kind(LandColumn column, int y, int waterNear, EarthRules earth, int maxFill, int minY, int maxY) {
        if (column == null || column.closed() || y <= minY || y + earth.tunnelHeight() + 2 >= maxY) {
            return null;
        }
        if (column.has(LandColumn.ROAD) || column.has(LandColumn.WALL) || column.has(LandColumn.GATE)) {
            return y == column.roadY() ? RouteCell.Kind.GROUND : null;
        }
        int ground = column.ground();
        int tunnel = earth.tunnelHeight();
        boolean flooded = waterNear != LandColumn.NONE && y < waterNear;
        if (column.water()) {
            return y >= column.waterTop() + 1 ? RouteCell.Kind.BRIDGE : null;
        }
        if (column.has(LandColumn.LAVA)) {
            boolean bore = y + tunnel + 1 + EarthRules.LAVA_COVER + 4 <= ground && column.naturalTo() <= y - 1 && !flooded
                    && !(column.caveLiquid() && column.caveWithin(y - 1, y + tunnel + 1) > 0);
            return bore ? RouteCell.Kind.TUNNEL : null;
        }
        int d = ground - y;
        if (d == 0) {
            return RouteCell.Kind.GROUND;
        }
        if (d < 0) {
            return -d - 1 <= maxFill ? RouteCell.Kind.FILL : RouteCell.Kind.BRIDGE;
        }
        if (flooded) {
            return null;
        }
        boolean cut = column.naturalTo() <= y;
        boolean bore = ground - (y + tunnel) >= EarthRules.MIN_COVER && column.naturalTo() <= y - 1
                && !(column.caveLiquid() && column.caveWithin(y - 1, y + tunnel + 1) > 0);
        if (cut && bore) {
            // The cheaper of the two by the middle column, as the search takes it.
            return cutWork(column, y, earth) <= tunnelWork(column, y, earth) ? RouteCell.Kind.CUT : RouteCell.Kind.TUNNEL;
        }
        return cut ? RouteCell.Kind.CUT : bore ? RouteCell.Kind.TUNNEL : null;
    }

    private static double cutWork(LandColumn column, int y, EarthRules earth) {
        int[] dug = RoadSurvey.dig(column, y + 1, column.ground());
        return dug[0] * earth.digSoilSeconds() + dug[1] * earth.digRockSeconds();
    }

    private static double tunnelWork(LandColumn column, int y, EarthRules earth) {
        int[] dug = RoadSurvey.dig(column, y + 1, y + earth.tunnelHeight());
        return (dug[0] * earth.digSoilSeconds() + dug[1] * earth.digRockSeconds()) * EarthRules.TUNNEL_FACTOR;
    }

    /**
     * The cross-section of every block: its columns as the layout gives them.
     */
    static final class Sections {
        final List<List<RoadLayout.Block>> blocks = new ArrayList<>();
        final List<List<LandColumn>> columns = new ArrayList<>();
        /** The column of the middle of every block, whichever block its column belongs to in the layout. */
        final List<LandColumn> middles = new ArrayList<>();
        final int[] waterNear;

        Sections(List<int[]> cells, RoadClass roadClass, RoadSurvey.Land land) {
            List<RouteCell> way = new ArrayList<>(cells.size());
            for (int[] cell : cells) {
                way.add(new RouteCell(cell[0], 0, cell[1], RouteCell.Kind.GROUND, 0, null));
            }
            for (int[] cell : cells) {
                blocks.add(new ArrayList<>());
                columns.add(new ArrayList<>());
                middles.add(land.at(cell[0], cell[1]));
            }
            for (RoadLayout.Block block : RoadLayout.blocks(way, roadClass, RoadLayout.LEVELLED, null)) {
                blocks.get(block.cell()).add(block);
                columns.get(block.cell()).add(land.at(block.x(), block.z()));
            }
            waterNear = new int[cells.size()];
            for (int i = 0; i < cells.size(); i++) {
                int water = middles.get(i) != null && middles.get(i).water() ? middles.get(i).waterTop() : LandColumn.NONE;
                for (LandColumn column : columns.get(i)) {
                    if (column != null && column.water()) {
                        water = Math.max(water, column.waterTop());
                    }
                }
                waterNear[i] = water;
            }
        }
    }

    /**
     * The exact work of the cross-section of a block at a height, as the search counts it: seconds of builders and miners and items
     * at their worth; infinite where its middle may not be there.
     */
    static double cost(Sections sections, int i, int y, RoadClass roadClass, RoadRules rules, EarthRules earth, int minY, int maxY,
                       RouteCell.Kind[] kindOut) {
        List<RoadLayout.Block> blocks = sections.blocks.get(i);
        List<LandColumn> columns = sections.columns.get(i);
        // The middle of a block may belong to a block before it in the layout, where the way turns back: its own column counts.
        LandColumn middle = sections.middles.get(i);
        RouteCell.Kind kind = kind(middle, y, sections.waterNear[i], earth, rules.maxFill(), minY, maxY);
        if (kind == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (kindOut != null) {
            kindOut[0] = kind;
        }
        double sum = 0;
        boolean lit = false;
        for (int b = 0; b < blocks.size(); b++) {
            RoadLayout.Block block = blocks.get(b);
            LandColumn column = columns.get(b);
            if (column == null) {
                continue;
            }
            if (column.closed() && !column.has(LandColumn.ROAD)) {
                // Something built beside the way: the laying waits there; dear, but not out of the question.
                sum += 1e5;
                continue;
            }
            boolean pier = kind == RouteCell.Kind.BRIDGE && block.role() == RoadLayout.Role.MIDDLE && i % EarthRules.PIER_EVERY == 0;
            boolean light = kind == RouteCell.Kind.TUNNEL && block.role() == RoadLayout.Role.VERGE && !lit
                    && i % EarthRules.TORCH_EVERY == EarthRules.TORCH_AT;
            lit |= light;
            RoadLayout.ColumnSurvey survey = RoadSurvey.column(column, y, kind, block.role(), pier, light, earth.tunnelHeight());
            RoadLayout.Block at = new RoadLayout.Block(i, block.x(), y, block.z(), kind, 0, block.role(), survey);
            sum += ColumnWork.of(at, roadClass, rules, earth).effort();
        }
        return sum;
    }

    /**
     * Finds the profile.
     */
    public static Result solve(Input input, RoadClass roadClass, RoadSurvey.Land land, RoadRules rules, EarthRules earth, int minY,
                               int maxY) {
        List<int[]> cells = input.cells();
        int n = cells.size();
        Sections sections = new Sections(cells, roadClass, land);
        int[] lo = new int[n];
        int[] hi = new int[n];
        int[] radius = new int[n];
        Arrays.fill(radius, WINDOW);
        for (int windows = 1; ; windows++) {
            for (int i = 0; i < n; i++) {
                Integer pin = input.pins().get(i);
                if (pin != null) {
                    lo[i] = pin;
                    hi[i] = pin;
                } else {
                    int extra = radius[i] - WINDOW;
                    int low = input.lows() != null ? input.lows()[i] : input.heights()[i] - WINDOW;
                    int high = input.highs() != null ? input.highs()[i] : input.heights()[i] + WINDOW;
                    lo[i] = Math.max(minY + 1, low - extra);
                    hi[i] = Math.min(maxY - earth.tunnelHeight() - 3, high + extra);
                }
            }
            Result[] out = new Result[1];
            int failed = solveWindows(cells, input, sections, lo, hi, roadClass, rules, earth, minY, maxY, out);
            if (failed < 0) {
                Result result = out[0];
                return new Result(result.heights(), fix(result.kinds(), result.heights(), sections, earth, rules, minY, maxY), result.cost(), windows);
            }
            // Widen the window between the pins round the block that had no height, up to the whole world.
            int from = failed;
            while (from > 0 && !input.pins().containsKey(from)) {
                from--;
            }
            int to = failed;
            while (to < n - 1 && !input.pins().containsKey(to)) {
                to++;
            }
            boolean grew = false;
            for (int i = from; i <= to; i++) {
                if (radius[i] < maxY - minY) {
                    radius[i] = Math.min(maxY - minY, radius[i] * 2);
                    grew = true;
                }
            }
            if (!grew) {
                // Nothing more to widen: the way cannot be levelled, every block keeps the height of the search.
                RouteCell.Kind[] kinds = new RouteCell.Kind[n];
                Arrays.fill(kinds, RouteCell.Kind.GROUND);
                return new Result(input.heights().clone(), kinds, Double.POSITIVE_INFINITY, windows);
            }
        }
    }

    /**
     * Runs the profile within the windows.
     *
     * @return the first block without a height, or -1 when there is a profile (then given in {@code out} if asked)
     */
    private static int solveWindows(List<int[]> cells, Input input, Sections sections, int[] lo, int[] hi, RoadClass roadClass, RoadRules rules,
                                    EarthRules earth, int minY, int maxY, Result[] out) {
        int n = cells.size();
        int k = earth.grade(roadClass);
        double change = EarthRules.GRADE_CHANGE * EarthRules.base(roadClass, rules);
        double[] previous = null;
        byte[][] back = new byte[n][];
        for (int i = 0; i < n; i++) {
            int span = hi[i] - lo[i] + 1;
            double[] current = new double[span * k];
            Arrays.fill(current, Double.POSITIVE_INFINITY);
            back[i] = new byte[span * k];
            int groundAt = groundOf(sections, i);
            boolean soft = i == 0 || i == n - 1;
            boolean any = false;
            for (int yi = 0; yi < span; yi++) {
                int y = lo[i] + yi;
                double c = cost(sections, i, y, roadClass, rules, earth, minY, maxY, null);
                if (!Double.isFinite(c)) {
                    continue;
                }
                if (soft && groundAt != LandColumn.NONE && Math.abs(y - groundAt) > 1 && !input.pins().containsKey(i)) {
                    c += SOFT;
                }
                if (i == 0) {
                    current[yi * k + (k - 1)] = c;
                    any = true;
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    int py = y - dy;
                    if (py < lo[i - 1] || py > hi[i - 1]) {
                        continue;
                    }
                    int pyi = py - lo[i - 1];
                    for (int pg = 0; pg < k; pg++) {
                        double p = previous[pyi * k + pg];
                        if (!Double.isFinite(p)) {
                            continue;
                        }
                        int g;
                        double total;
                        if (dy != 0) {
                            if (pg < k - 1) {
                                continue;
                            }
                            g = 0;
                            total = p + c + change;
                        } else {
                            g = Math.min(k - 1, pg + 1);
                            total = p + c;
                        }
                        int at = yi * k + g;
                        if (total < current[at]) {
                            current[at] = total;
                            back[i][at] = (byte) ((dy + 1) << 4 | pg);
                            any = true;
                        }
                    }
                }
            }
            if (!any) {
                return i;
            }
            previous = current;
        }
        if (out != null) {
            int best = -1;
            double bestCost = Double.POSITIVE_INFINITY;
            for (int s = 0; s < previous.length; s++) {
                if (previous[s] < bestCost) {
                    bestCost = previous[s];
                    best = s;
                }
            }
            int[] heights = new int[n];
            RouteCell.Kind[] kinds = new RouteCell.Kind[n];
            int s = best;
            for (int i = n - 1; i >= 0; i--) {
                int yi = s / k;
                int y = lo[i] + yi;
                heights[i] = y;
                RouteCell.Kind[] kind = new RouteCell.Kind[1];
                cost(sections, i, y, roadClass, rules, earth, minY, maxY, kind);
                kinds[i] = kind[0] == null ? RouteCell.Kind.GROUND : kind[0];
                if (i > 0) {
                    int dy = (back[i][s] >> 4) - 1;
                    int pg = back[i][s] & 0xF;
                    s = (y - dy - lo[i - 1]) * k + pg;
                }
            }
            out[0] = new Result(heights, kinds, bestCost, 0);
        }
        return -1;
    }

    private static int groundOf(Sections sections, int i) {
        LandColumn middle = sections.middles.get(i);
        return middle == null ? LandColumn.NONE : middle.top();
    }

    /**
     * A tunnel shorter than four blocks becomes a cutting where it may; a cutting of up to two blocks between two tunnels a tunnel.
     */
    static RouteCell.Kind[] fix(RouteCell.Kind[] kinds, int[] heights, Sections sections, EarthRules earth, RoadRules rules, int minY, int maxY) {
        RouteCell.Kind[] result = kinds.clone();
        int n = result.length;
        int i = 0;
        while (i < n) {
            if (result[i] != RouteCell.Kind.TUNNEL) {
                i++;
                continue;
            }
            int j = i;
            while (j < n && result[j] == RouteCell.Kind.TUNNEL) {
                j++;
            }
            if (j - i < 4 && allMay(sections, i, j, heights, RouteCell.Kind.CUT, earth)) {
                for (int t = i; t < j; t++) {
                    result[t] = RouteCell.Kind.CUT;
                }
            }
            i = j;
        }
        i = 0;
        while (i < n) {
            if (result[i] != RouteCell.Kind.CUT) {
                i++;
                continue;
            }
            int j = i;
            while (j < n && result[j] == RouteCell.Kind.CUT) {
                j++;
            }
            boolean between = i > 0 && j < n && result[i - 1] == RouteCell.Kind.TUNNEL && result[j] == RouteCell.Kind.TUNNEL;
            if (between && j - i <= 2 && allMay(sections, i, j, heights, RouteCell.Kind.TUNNEL, earth)) {
                for (int t = i; t < j; t++) {
                    result[t] = RouteCell.Kind.TUNNEL;
                }
            }
            i = j;
        }
        return result;
    }

    /**
     * Whether every block from {@code from} up to {@code to} may be of a kind at its height.
     */
    private static boolean allMay(Sections sections, int from, int to, int[] heights, RouteCell.Kind kind, EarthRules earth) {
        for (int i = from; i < to; i++) {
            LandColumn middle = sections.middles.get(i);
            if (middle == null || middle.water()) {
                return false;
            }
            int y = heights[i];
            if (kind == RouteCell.Kind.CUT && !(middle.naturalTo() <= y && middle.ground() > y)) {
                return false;
            }
            if (kind == RouteCell.Kind.TUNNEL && !(middle.ground() - (y + earth.tunnelHeight()) >= EarthRules.MIN_COVER
                    && middle.naturalTo() <= y - 1)) {
                return false;
            }
        }
        return true;
    }
}
