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
        return ring(terrain, centerX, centerZ, radius, false);
    }

    /**
     * The ring around a place, walked round once.
     *
     * @param overWater whether blocks of water are in the ring too, at the height of the water: a wall at a height of its own fills
     *                  under itself
     * @return the blocks of the ring, or {@code null} if some of the land cannot be looked at yet
     */
    public static List<RouteCell> ring(Terrain terrain, int centerX, int centerZ, int radius, boolean overWater) {
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
            if (column.surface() == Terrain.Surface.LAND || overWater && column.surface() == Terrain.Surface.WATER) {
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

    /**
     * Where the gates of an older ring go on a new one: for each, the block of the new ring nearest to it, if one is within reach.
     * Two old gates that fall on one new block give one gate.
     *
     * @param reach how far, along x or z, the new block may lie from the old gate
     */
    public static List<Integer> keptGates(List<RouteCell> ring, List<RouteCell> oldGates, int reach) {
        TreeSet<Integer> kept = new TreeSet<>();
        for (RouteCell gate : oldGates) {
            int best = -1;
            long bestDistance = Long.MAX_VALUE;
            for (int i = 0; i < ring.size(); i++) {
                RouteCell cell = ring.get(i);
                long dx = cell.x() - gate.x();
                long dz = cell.z() - gate.z();
                if (Math.abs(dx) > reach || Math.abs(dz) > reach) {
                    continue;
                }
                if (dx * dx + dz * dz < bestDistance) {
                    best = i;
                    bestDistance = dx * dx + dz * dz;
                }
            }
            if (best >= 0) {
                kept.add(best);
            }
        }
        return new ArrayList<>(kept);
    }
}
