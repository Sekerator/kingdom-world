package io.github.sekerator.kingdomworld.core.construction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Where a building gets the flags of its kingdom its template does not have: beside the door, on the roof, or at the front
 * corners of its site.
 *
 * <p>A house gets one flag; any other building one, two or three by the area of its footprint (up to {@link #SMALL} cells,
 * up to {@link #MEDIUM}, more), less the flags its template already has. A building of defense gets one more on its roof
 * unless a flag of its template stands in the top quarter of its height. Every flag stands in a cell the template leaves as
 * air, inside the site, never next to another flag, a door or a window. The rules, in order:
 *
 * <ol>
 *   <li>At the entrance: a flag on the wall beside the door on the front nearest to its middle, at the height of the top of
 *       the door, facing out; with two or more flags wanted, one on each side. A double door is passed over.</li>
 *   <li>On the roof: standing flags on the highest tops of the building that cover a room, no deeper than {@link #ROOF_DEPTH}
 *       layers below the highest, near its middle first, the next ones at least {@link #ROOF_SPACING} blocks from each other,
 *       so that towers get theirs.</li>
 *   <li>On the ground: standing flags at the two front corners of the site, one cell in from its edges, never on a path or
 *       before the door.</li>
 * </ol>
 *
 * <p>The positions are those of the template, before it is turned and mirrored into the world.
 */
public final class FlagSpots {
    /** The largest footprint of one flag, in cells. */
    public static final int SMALL = 120;
    /** The largest footprint of two flags, in cells. */
    public static final int MEDIUM = 300;
    /** How many layers below the highest top a flag on a roof may stand. */
    public static final int ROOF_DEPTH = 4;
    /** How far apart flags on roofs stand at least, in blocks. */
    public static final int ROOF_SPACING = 6;

    /**
     * What a cell of a template holds, as far as flags care.
     */
    public enum Cell {
        AIR,
        /** A full block a flag hangs on or stands on; glass is no such block. */
        SOLID,
        /** The lower half of a door, or a gate of a fence. */
        DOOR,
        /** Glass, a pane, bars, a trapdoor. */
        WINDOW,
        /** A flag of the kingdom. */
        FLAG,
        /** A path or gravel. */
        PATH,
        OTHER,
        /** A cell the template sets no block in. */
        NONE
    }

    /**
     * The cells of a template.
     */
    public interface Grid {
        int sizeX();

        int sizeY();

        int sizeZ();

        Cell at(int x, int y, int z);
    }

    public enum Kind {
        /** A flag on a wall, facing out. */
        WALL,
        /** A flag on a pole, standing on the block under it. */
        STANDING
    }

    /**
     * A place of a flag.
     *
     * @param facing   the way a flag on a wall faces, or the way a standing flag looks
     * @param rotation the rotation of a standing flag in sixteenths of a turn, 0 facing south as the game counts it
     */
    public record Spot(int x, int y, int z, Kind kind, Facing facing, int rotation) {
        public Spot {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(facing, "facing");
        }
    }

    private FlagSpots() {
    }

    /**
     * How many flags a building flies in all.
     */
    public static int wanted(int sizeX, int sizeZ, boolean housing) {
        if (housing) {
            return 1;
        }
        int area = sizeX * sizeZ;
        return area <= SMALL ? 1 : area <= MEDIUM ? 2 : 3;
    }

    /**
     * The rotation of a standing flag that looks a way.
     */
    public static int rotation(Facing facing) {
        return switch (facing) {
            case SOUTH -> 0;
            case WEST -> 4;
            case NORTH -> 8;
            case EAST -> 12;
        };
    }

    /**
     * The places of the flags a building gets besides those of its template.
     *
     * @param entrance the side of the template its entrance faces
     * @param housing  whether the building is a house
     * @param defense  whether it is a building of defense
     */
    public static List<Spot> find(Grid grid, Facing entrance, boolean housing, boolean defense) {
        Places places = new Places(grid);
        int own = 0;
        boolean high = false;
        int quarter = grid.sizeY() - Math.max(1, (grid.sizeY() + 3) / 4);
        for (int x = 0; x < grid.sizeX(); x++) {
            for (int y = 0; y < grid.sizeY(); y++) {
                for (int z = 0; z < grid.sizeZ(); z++) {
                    if (grid.at(x, y, z) == Cell.FLAG) {
                        own++;
                        high |= y >= quarter;
                        places.taken.add(new int[]{x, y, z});
                    }
                }
            }
        }
        int need = Math.max(0, wanted(grid.sizeX(), grid.sizeZ(), housing) - own);
        List<Spot> spots = new ArrayList<>();
        Door door = door(grid, entrance);
        if (need > 0 && door != null) {
            entrance(grid, places, door, entrance, need, spots);
        }
        if (spots.size() < need) {
            roof(grid, places, entrance, need - spots.size(), spots);
        }
        if (spots.size() < need) {
            ground(grid, places, entrance, door, need - spots.size(), spots);
        }
        if (defense && !high && spots.stream().noneMatch(spot -> spot.y() >= quarter)) {
            roof(grid, places, entrance, 1, spots);
        }
        return List.copyOf(spots);
    }

    // ---------------------------------------------------------------- the door

    /**
     * The lower half of the door of the front, nearest to the middle of the front.
     */
    private record Door(int x, int y, int z) {
    }

    private static Door door(Grid grid, Facing entrance) {
        Facing side = entrance.clockwise();
        double middleX = (grid.sizeX() - 1) / 2.0;
        double middleZ = (grid.sizeZ() - 1) / 2.0;
        Door best = null;
        int bestFront = Integer.MIN_VALUE;
        double bestOff = Double.MAX_VALUE;
        for (int y = 0; y < grid.sizeY(); y++) {
            for (int z = 0; z < grid.sizeZ(); z++) {
                for (int x = 0; x < grid.sizeX(); x++) {
                    if (grid.at(x, y, z) != Cell.DOOR || cell(grid, x + entrance.dx(), y, z + entrance.dz()) != Cell.AIR) {
                        continue;
                    }
                    int front = x * entrance.dx() + z * entrance.dz();
                    double off = Math.abs((x - middleX) * side.dx() + (z - middleZ) * side.dz());
                    if (front > bestFront || front == bestFront && off < bestOff) {
                        best = new Door(x, y, z);
                        bestFront = front;
                        bestOff = off;
                    }
                }
            }
        }
        return best;
    }

    private static void entrance(Grid grid, Places places, Door door, Facing entrance, int need, List<Spot> spots) {
        Facing side = entrance.clockwise();
        List<Spot> found = new ArrayList<>();
        for (int sign : new int[]{-1, 1}) {
            for (int k = 1; k <= 2; k++) {
                int wx = door.x() + sign * k * side.dx();
                int wz = door.z() + sign * k * side.dz();
                int fx = wx + entrance.dx();
                int fz = wz + entrance.dz();
                int y = door.y() + 2;
                if (cell(grid, wx, y - 1, wz) == Cell.SOLID && cell(grid, wx, y, wz) == Cell.SOLID
                        && places.free(fx, y, fz) && places.free(fx, y - 1, fz)) {
                    found.add(new Spot(fx, y, fz, Kind.WALL, entrance, 0));
                    break;
                }
            }
        }
        for (Spot spot : found) {
            if (spots.size() >= need) {
                break;
            }
            if (places.free(spot.x(), spot.y(), spot.z()) && places.free(spot.x(), spot.y() - 1, spot.z())) {
                places.take(spot.x(), spot.y(), spot.z());
                places.take(spot.x(), spot.y() - 1, spot.z());
                spots.add(spot);
            }
        }
    }

    // ---------------------------------------------------------------- the roof

    private static void roof(Grid grid, Places places, Facing entrance, int need, List<Spot> spots) {
        List<int[]> tops = new ArrayList<>();
        int highest = Integer.MIN_VALUE;
        for (int x = 0; x < grid.sizeX(); x++) {
            for (int z = 0; z < grid.sizeZ(); z++) {
                for (int y = grid.sizeY() - 1; y >= 0; y--) {
                    Cell cell = grid.at(x, y, z);
                    if (cell == Cell.SOLID) {
                        highest = Math.max(highest, y);
                        if (y + 1 < grid.sizeY() && grid.at(x, y + 1, z) == Cell.AIR
                                && (y + 2 >= grid.sizeY() || grid.at(x, y + 2, z) == Cell.AIR) && roofs(grid, x, y, z)) {
                            tops.add(new int[]{x, y, z});
                        }
                        break;
                    }
                    if (cell != Cell.AIR && cell != Cell.NONE) {
                        // Something else stands highest here: no top to stand on.
                        break;
                    }
                }
            }
        }
        double middleX = (grid.sizeX() - 1) / 2.0;
        double middleZ = (grid.sizeZ() - 1) / 2.0;
        int deepest = highest - ROOF_DEPTH;
        tops.removeIf(top -> top[1] < deepest);
        tops.sort(Comparator.<int[]>comparingInt(top -> -top[1])
                .thenComparingDouble(top -> (top[0] - middleX) * (top[0] - middleX) + (top[2] - middleZ) * (top[2] - middleZ))
                .thenComparingInt(top -> top[0]).thenComparingInt(top -> top[2]));
        int added = 0;
        for (int[] top : tops) {
            if (added >= need) {
                return;
            }
            int x = top[0];
            int y = top[1] + 1;
            int z = top[2];
            if (!places.free(x, y, z) || y + 1 < grid.sizeY() && !places.free(x, y + 1, z)) {
                continue;
            }
            boolean apart = true;
            for (int[] other : places.roofs) {
                int dx = other[0] - x;
                int dz = other[1] - z;
                if (dx * dx + dz * dz < ROOF_SPACING * ROOF_SPACING) {
                    apart = false;
                    break;
                }
            }
            if (!apart) {
                continue;
            }
            places.roofs.add(new int[]{x, z});
            places.take(x, y, z);
            if (y + 1 < grid.sizeY()) {
                places.take(x, y + 1, z);
            }
            spots.add(new Spot(x, y, z, Kind.STANDING, entrance, rotation(entrance)));
            added++;
        }
    }

    /**
     * Whether a top is a roof: it covers a room, some air of the template under it. The ground of a yard or a garden and the
     * top of a solid wall are no roofs.
     */
    private static boolean roofs(Grid grid, int x, int top, int z) {
        for (int y = top - 1; y >= 0; y--) {
            if (grid.at(x, y, z) == Cell.AIR) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- the ground

    private static void ground(Grid grid, Places places, Facing entrance, Door door, int need, List<Spot> spots) {
        Facing side = entrance.clockwise();
        boolean alongX = side.dx() != 0;
        int sideSize = alongX ? grid.sizeX() : grid.sizeZ();
        int depthSize = alongX ? grid.sizeZ() : grid.sizeX();
        if (sideSize < 3 || depthSize < 3) {
            return;
        }
        // The row one cell in from the front edge.
        int frontPositive = (alongX ? entrance.dz() : entrance.dx()) > 0 ? depthSize - 2 : 1;
        int axis = door != null ? (alongX ? door.x() : door.z()) : -1;
        int middleLow = (sideSize - 1) / 2;
        int middleHigh = sideSize / 2;
        int added = 0;
        for (int along : new int[]{1, sideSize - 2}) {
            if (added >= need) {
                return;
            }
            boolean onAxis = axis >= 0 ? Math.abs(along - axis) <= 1 : along >= middleLow - 1 && along <= middleHigh + 1;
            if (onAxis) {
                continue;
            }
            int x = alongX ? along : frontPositive;
            int z = alongX ? frontPositive : along;
            for (int y = 0; y < grid.sizeY(); y++) {
                if (grid.at(x, y, z) != Cell.AIR) {
                    continue;
                }
                Cell under = y == 0 ? Cell.NONE : grid.at(x, y - 1, z);
                if (under != Cell.SOLID && under != Cell.NONE) {
                    break;
                }
                if (places.free(x, y, z) && (y + 1 >= grid.sizeY() || places.free(x, y + 1, z))) {
                    places.take(x, y, z);
                    if (y + 1 < grid.sizeY()) {
                        places.take(x, y + 1, z);
                    }
                    spots.add(new Spot(x, y, z, Kind.STANDING, entrance, rotation(entrance)));
                    added++;
                }
                break;
            }
        }
    }

    // ---------------------------------------------------------------- cells

    private static Cell cell(Grid grid, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= grid.sizeX() || y >= grid.sizeY() || z >= grid.sizeZ()) {
            return Cell.NONE;
        }
        return grid.at(x, y, z);
    }

    /**
     * The cells flags and their cloth take, and the rules of a free place.
     */
    private static final class Places {
        private final Grid grid;
        private final List<int[]> taken = new ArrayList<>();
        /** The columns of the flags put on roofs, which stand apart. */
        private final List<int[]> roofs = new ArrayList<>();

        Places(Grid grid) {
            this.grid = grid;
        }

        void take(int x, int y, int z) {
            taken.add(new int[]{x, y, z});
        }

        /**
         * Whether a flag or its cloth may take a cell: air of the template inside the site, not next to another flag and not
         * before a door or a window.
         */
        boolean free(int x, int y, int z) {
            if (cell(grid, x, y, z) != Cell.AIR) {
                return false;
            }
            for (int[] other : taken) {
                if (Math.abs(other[0] - x) <= 1 && Math.abs(other[1] - y) <= 1 && Math.abs(other[2] - z) <= 1) {
                    return false;
                }
            }
            for (Facing facing : Facing.values()) {
                int nx = x + facing.dx();
                int nz = z + facing.dz();
                Cell next = cell(grid, nx, y, nz);
                if (next == Cell.DOOR || next == Cell.WINDOW || cell(grid, nx, y - 1, nz) == Cell.DOOR) {
                    return false;
                }
            }
            return true;
        }
    }
}
