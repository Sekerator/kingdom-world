package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * The ring of a city wall on the lay of the land: a square of a radius around the city, each block on the ground there.
 * Water, danger and what must not be broken are left out of the ring; the wall has a gap there. Gates are cut where a road
 * crosses the ring.
 */
public final class WallContour {

    private WallContour() {
    }

    /**
     * The ring around a place, walked round once.
     *
     * @return the blocks of the ring, or {@code null} if some of the land cannot be looked at yet
     */
    public static List<RouteCell> ring(Terrain terrain, int centerX, int centerZ, int radius) {
        List<int[]> walk = new ArrayList<>();
        for (int x = centerX - radius; x < centerX + radius; x++) {
            walk.add(new int[]{x, centerZ - radius});
        }
        for (int z = centerZ - radius; z < centerZ + radius; z++) {
            walk.add(new int[]{centerX + radius, z});
        }
        for (int x = centerX + radius; x > centerX - radius; x--) {
            walk.add(new int[]{x, centerZ + radius});
        }
        for (int z = centerZ + radius; z > centerZ - radius; z--) {
            walk.add(new int[]{centerX - radius, z});
        }
        List<RouteCell> cells = new ArrayList<>();
        for (int[] at : walk) {
            Terrain.Column column = terrain.column(at[0], at[1]);
            if (column == null) {
                return null;
            }
            if (column.surface() == Terrain.Surface.LAND) {
                cells.add(new RouteCell(at[0], column.y(), at[1], RouteCell.Kind.GROUND, 0, column.owner()));
            }
        }
        return cells;
    }

    /**
     * The blocks of a ring where roads cross it: a gate for every crossing, in the middle of the blocks of the ring the road
     * passes next to.
     */
    public static List<Integer> gates(List<RouteCell> ring, Collection<List<RouteCell>> roads) {
        TreeSet<Integer> touched = new TreeSet<>();
        for (List<RouteCell> road : roads) {
            for (RouteCell cell : road) {
                for (int i = 0; i < ring.size(); i++) {
                    RouteCell wall = ring.get(i);
                    if (Math.abs(wall.x() - cell.x()) <= 1 && Math.abs(wall.z() - cell.z()) <= 1) {
                        touched.add(i);
                    }
                }
            }
        }
        List<Integer> gates = new ArrayList<>();
        Integer runStart = null;
        Integer previous = null;
        for (int index : touched) {
            if (previous == null || index != previous + 1) {
                if (runStart != null) {
                    gates.add((runStart + previous) / 2);
                }
                runStart = index;
            }
            previous = index;
        }
        if (runStart != null) {
            gates.add((runStart + previous) / 2);
        }
        return gates;
    }
}
