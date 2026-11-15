package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The blocks of a road: its middle widened to the width of its class, each block with what it needs.
 *
 * <p>Two layouts. In the first ({@value #LAID}), the roads before levelling, side blocks take the height of their middle block;
 * in the world a side block of a road on the ground that would float or sink more than a block is left out. In the second
 * ({@value #LEVELLED}) the road is levelled: every block of its width and of a verge on each side has a survey of the
 * earthworks there (what is dug, filled, propped and lit), and the road lies on the height of its middle across its whole
 * width.
 */
public final class RoadLayout {
    /** The layout of the roads laid on the land as it is. */
    public static final int LAID = 0;
    /** The layout of the levelled roads. */
    public static final int LEVELLED = 1;

    /**
     * What a block of road is made of.
     */
    public enum Material {
        /** Gravel or stone of the road itself. */
        SURFACE,
        /** Planks of a bridge. */
        BRIDGE,
        /** Earth or stone of an embankment. */
        FILL,
        /** Stone of the pier of a bridge. */
        PIER,
        /** A fence along a bridge. */
        RAIL,
        /** Stone that closes off a liquid, props the roof of a tunnel or covers a cave. */
        LINING,
        /** A torch in a tunnel. */
        TORCH;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Where a block lies across the road.
     */
    public enum Role {
        /** On the middle of the way. */
        MIDDLE,
        /** On the way beside its middle. */
        SIDE,
        /** On the verge beyond the way: earthworks, no surface. */
        VERGE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The earthworks of one column of a levelled road, as the plan found its land.
     *
     * @param soil   blocks of soil dug
     * @param rock   blocks of rock dug
     * @param logs   logs of trees felled
     * @param fill   blocks of embankment put in under the deck (on a verge up to the deck)
     * @param pier   blocks of a pier under a bridge
     * @param lining blocks of lining: a liquid closed off, the roof of a tunnel propped, a cave under the deck covered
     * @param light  whether a torch stands there
     * @param onRoad whether the column is part of another road already: nothing is done there
     */
    public record ColumnSurvey(int soil, int rock, int logs, int fill, int pier, int lining, boolean light, boolean onRoad) {
        public static final ColumnSurvey EMPTY = new ColumnSurvey(0, 0, 0, 0, 0, 0, false, false);
        /** The largest count a survey keeps. */
        public static final int MAX = 0xFFFF;
        /** The highest embankment a survey keeps. */
        public static final int MAX_FILL = 0xFF;

        public ColumnSurvey {
            soil = clamp(soil, MAX);
            rock = clamp(rock, MAX);
            logs = clamp(logs, MAX);
            fill = clamp(fill, MAX_FILL);
            pier = clamp(pier, MAX);
            lining = clamp(lining, MAX);
        }

        private static int clamp(int value, int max) {
            return Math.max(0, Math.min(max, value));
        }

        /**
         * Whether anything is dug there.
         */
        public boolean dug() {
            return soil > 0 || rock > 0;
        }
    }

    /**
     * One block of road.
     *
     * @param cell   the index of the middle block it belongs to
     * @param y      the height of the road there
     * @param depth  blocks of embankment under it, in the first layout
     * @param role   where it lies across the road
     * @param survey its earthworks in a levelled road; {@code null} in the first layout
     */
    public record Block(int cell, int x, int y, int z, RouteCell.Kind kind, int depth, Role role, ColumnSurvey survey) {

        /**
         * A block of the first layout.
         */
        public Block(int cell, int x, int y, int z, RouteCell.Kind kind, int depth, boolean side) {
            this(cell, x, y, z, kind, depth, side ? Role.SIDE : Role.MIDDLE, null);
        }

        /**
         * Whether it is beside the middle of the road.
         */
        public boolean side() {
            return role != Role.MIDDLE;
        }

        /**
         * Whether it belongs to a levelled road.
         */
        public boolean levelled() {
            return survey != null;
        }

        public Block withSurvey(ColumnSurvey value) {
            return new Block(cell, x, y, z, kind, depth, role, value);
        }

        /**
         * Items the block takes, in the order they are used: the embankment from the bottom up, then the top; in a levelled road
         * then the pier, the surface or the deck, the rail, the lining and the torch.
         */
        public List<Material> needs(RoadClass roadClass) {
            if (survey != null) {
                return levelledNeeds(roadClass);
            }
            List<Material> list = new ArrayList<>(depth + 1);
            if (kind == RouteCell.Kind.BRIDGE) {
                list.add(Material.BRIDGE);
                return list;
            }
            for (int i = 0; i < depth; i++) {
                list.add(Material.FILL);
            }
            if (kind == RouteCell.Kind.FILL) {
                // An embankment of a path is topped with earth that becomes the path.
                list.add(roadClass == RoadClass.PATH ? Material.FILL : Material.SURFACE);
            } else if (roadClass != RoadClass.PATH) {
                list.add(Material.SURFACE);
            }
            return list;
        }

        private List<Material> levelledNeeds(RoadClass roadClass) {
            List<Material> list = new ArrayList<>();
            if (survey.onRoad()) {
                return list;
            }
            for (int i = 0; i < survey.fill(); i++) {
                list.add(Material.FILL);
            }
            for (int i = 0; i < survey.pier(); i++) {
                list.add(Material.PIER);
            }
            Material top = top(roadClass);
            if (top != null) {
                list.add(top);
            }
            if (kind == RouteCell.Kind.BRIDGE && role == Role.VERGE && roadClass != RoadClass.PATH) {
                list.add(Material.RAIL);
            }
            for (int i = 0; i < survey.lining(); i++) {
                list.add(Material.LINING);
            }
            if (survey.light()) {
                list.add(Material.TORCH);
            }
            return list;
        }

        /**
         * What the top of a levelled block is made of: the deck of a bridge on its whole width, the surface on the way, earth
         * that becomes the path on the embankment of a path; nothing on a verge or a path on the ground.
         */
        public Material top(RoadClass roadClass) {
            if (kind == RouteCell.Kind.BRIDGE) {
                return Material.BRIDGE;
            }
            if (role == Role.VERGE) {
                return null;
            }
            if (roadClass != RoadClass.PATH) {
                return Material.SURFACE;
            }
            return kind == RouteCell.Kind.FILL ? Material.FILL : null;
        }
    }

    private RoadLayout() {
    }

    /**
     * The blocks of a road of the first layout along its middle, in the order of the middle; a block shared by two middle
     * blocks belongs to the first.
     */
    public static List<Block> blocks(List<RouteCell> cells, RoadClass roadClass) {
        List<Block> result = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        int half = roadClass.width() / 2;
        for (int i = 0; i < cells.size(); i++) {
            RouteCell cell = cells.get(i);
            if (taken.add(column(cell.x(), cell.z()))) {
                result.add(new Block(i, cell.x(), cell.y(), cell.z(), cell.kind(), cell.depth(), false));
            }
            if (half == 0) {
                continue;
            }
            for (int[] dir : across(cells, i)) {
                for (int s = -half; s <= half; s++) {
                    if (s == 0) {
                        continue;
                    }
                    int x = cell.x() + dir[0] * s;
                    int z = cell.z() + dir[1] * s;
                    if (taken.add(column(x, z))) {
                        result.add(new Block(i, x, cell.y(), z, cell.kind(), cell.depth(), true));
                    }
                }
            }
        }
        return result;
    }

    /**
     * The blocks of a road of a layout. A levelled road gets its middle, then the way beside it, then its verges, a block
     * shared by two middle blocks belonging to the first and taking the most of the roles they give it; each with its survey,
     * in the order of the blocks, or an empty one when the surveys given do not fit.
     *
     * @param surveys the surveys of the blocks of a levelled road, in order, or {@code null}
     */
    public static List<Block> blocks(List<RouteCell> cells, RoadClass roadClass, int layout, List<ColumnSurvey> surveys) {
        if (layout != LEVELLED) {
            return blocks(cells, roadClass);
        }
        List<Block> result = new ArrayList<>();
        Map<Long, Integer> taken = new HashMap<>();
        int half = roadClass.width() / 2;
        int reach = half + EarthRules.VERGE;
        for (int i = 0; i < cells.size(); i++) {
            RouteCell cell = cells.get(i);
            claim(result, taken, i, cell, cell.x(), cell.z(), Role.MIDDLE);
            int[][] across = across(cells, i);
            for (int distance = 1; distance <= reach; distance++) {
                Role role = distance <= half ? Role.SIDE : Role.VERGE;
                for (int[] dir : across) {
                    claim(result, taken, i, cell, cell.x() + dir[0] * distance, cell.z() + dir[1] * distance, role);
                    claim(result, taken, i, cell, cell.x() - dir[0] * distance, cell.z() - dir[1] * distance, role);
                }
            }
        }
        boolean fits = surveys != null && surveys.size() == result.size();
        for (int b = 0; b < result.size(); b++) {
            result.set(b, result.get(b).withSurvey(fits ? surveys.get(b) : ColumnSurvey.EMPTY));
        }
        return result;
    }

    /**
     * Takes a column for a block of a levelled road, or gives a block that has it a weightier role (a verge of one block that is
     * the way of the next), at the height and kind of the block giving it.
     */
    private static void claim(List<Block> result, Map<Long, Integer> taken, int index, RouteCell cell, int x, int z, Role role) {
        long key = column(x, z);
        Integer at = taken.get(key);
        if (at == null) {
            taken.put(key, result.size());
            result.add(new Block(index, x, cell.y(), z, cell.kind(), cell.depth(), role, ColumnSurvey.EMPTY));
            return;
        }
        Block known = result.get(at);
        if (role.ordinal() < known.role().ordinal()) {
            result.set(at, new Block(known.cell(), x, cell.y(), z, cell.kind(), cell.depth(), role, ColumnSurvey.EMPTY));
        }
    }

    /**
     * The directions across the way at a middle block; at a bend both, so that the corner is full.
     */
    private static int[][] across(List<RouteCell> cells, int i) {
        RouteCell before = cells.get(Math.max(0, i - 1));
        RouteCell after = cells.get(Math.min(cells.size() - 1, i + 1));
        int dx = Integer.signum(after.x() - before.x());
        int dz = Integer.signum(after.z() - before.z());
        if (dx == 0 && dz == 0) {
            dx = 1;
        }
        return dx != 0 && dz != 0 ? new int[][]{{1, 0}, {0, 1}} : new int[][]{{-dz, dx}};
    }

    static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    /**
     * Items of each material all the blocks take.
     */
    public static int[] materials(List<Block> blocks, RoadClass roadClass) {
        int[] counts = new int[Material.values().length];
        for (Block block : blocks) {
            for (Material material : block.needs(roadClass)) {
                counts[material.ordinal()]++;
            }
        }
        return counts;
    }

    /**
     * Seconds of a builder's work the blocks of the first layout take: every item placed, and at least a moment for a block of
     * path.
     */
    public static double seconds(List<Block> blocks, RoadClass roadClass, RoadRules rules) {
        double seconds = 0;
        for (Block block : blocks) {
            seconds += Math.max(1, block.needs(roadClass).size()) * rules.secondsPerBlock(roadClass);
        }
        return seconds;
    }
}
