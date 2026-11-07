package io.github.sekerator.kingdomworld.core.territory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Outlines of land made of chunks, for maps that draw land as polygons.
 *
 * <p>Chunks that share a side merge into one area. An area may have holes (land enclosed by it that belongs to
 * nobody or to another kingdom); chunks that lie apart or only touch at a corner form separate areas (enclaves).
 * Corners are given in chunk units: corner (x, z) is the north-west corner of chunk (x, z), so multiplying by 16
 * gives block coordinates. Outer rings run clockwise on a map with x to the east and z to the south, holes run the
 * other way; corners where a ring goes straight on are left out. Where two chunks of an area touch only at a corner,
 * the ring turns around the chunk it came along, so rings never cross themselves.
 */
public final class ChunkOutlines {
    /** Directions east, south, west, north. */
    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DZ = {0, 1, 0, -1};

    private ChunkOutlines() {
    }

    /**
     * A corner of chunks, in chunk units.
     */
    public record Corner(int x, int z) {
        public int blockX() {
            return x * 16;
        }

        public int blockZ() {
            return z * 16;
        }
    }

    /**
     * A closed line of corners; the last corner connects back to the first.
     */
    public record Ring(List<Corner> corners) {
        public Ring {
            corners = List.copyOf(corners);
            if (corners.size() < 4) {
                throw new IllegalArgumentException("A ring of chunks has at least 4 corners: " + corners);
            }
        }

        /**
         * Twice the signed area in chunks squared: positive for outer rings, negative for holes.
         */
        public long doubleArea() {
            long sum = 0;
            for (int i = 0; i < corners.size(); i++) {
                Corner a = corners.get(i);
                Corner b = corners.get((i + 1) % corners.size());
                sum += (long) a.x() * b.z() - (long) b.x() * a.z();
            }
            return sum;
        }
    }

    /**
     * One connected piece of land.
     *
     * @param outer   the outline
     * @param holes   enclosed land that is not part of the area
     * @param chunks  number of chunks of the area
     * @param anchorX chunk x of a chunk of the area where a label fits: the preferred chunk if it lies in the area,
     *                otherwise the chunk nearest to the middle of the area
     * @param anchorZ chunk z of that chunk
     */
    public record Area(Ring outer, List<Ring> holes, int chunks, int anchorX, int anchorZ) {
        public Area {
            Objects.requireNonNull(outer, "outer");
            holes = List.copyOf(holes);
        }

        public long anchor() {
            return ChunkKey.of(anchorX, anchorZ);
        }
    }

    public static List<Area> of(Collection<Long> chunks) {
        return of(chunks, null);
    }

    /**
     * The areas of a set of chunks, the largest first.
     *
     * @param preferredAnchor chunk the label of its area should stand on (the capital), or {@code null}
     */
    public static List<Area> of(Collection<Long> chunks, Long preferredAnchor) {
        Set<Long> land = chunks instanceof Set<Long> set ? set : new HashSet<>(chunks);
        List<Area> areas = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        // Sorted, so that the result does not depend on the order of the input.
        for (long start : new TreeSet<>(land)) {
            if (!seen.add(start)) {
                continue;
            }
            List<Long> component = new ArrayList<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                long chunk = queue.poll();
                component.add(chunk);
                for (long side : ChunkKey.sides(chunk)) {
                    if (land.contains(side) && seen.add(side)) {
                        queue.add(side);
                    }
                }
            }
            areas.addAll(outline(component, land, preferredAnchor));
        }
        areas.sort(Comparator.comparingInt(Area::chunks).reversed()
                .thenComparingInt(Area::anchorZ).thenComparingInt(Area::anchorX));
        return List.copyOf(areas);
    }

    private static long corner(int x, int z) {
        return ChunkKey.of(x, z);
    }

    private static List<Area> outline(List<Long> component, Set<Long> land, Long preferredAnchor) {
        // Boundary edges with the land on their right, keyed by the corner they start at. A corner has two outgoing
        // edges only where two chunks of the land touch at that corner alone.
        Map<Long, List<Integer>> outgoing = new LinkedHashMap<>();
        for (long chunk : component) {
            int x = ChunkKey.x(chunk);
            int z = ChunkKey.z(chunk);
            if (!land.contains(ChunkKey.of(x, z - 1))) {
                add(outgoing, corner(x, z), 0);
            }
            if (!land.contains(ChunkKey.of(x + 1, z))) {
                add(outgoing, corner(x + 1, z), 1);
            }
            if (!land.contains(ChunkKey.of(x, z + 1))) {
                add(outgoing, corner(x + 1, z + 1), 2);
            }
            if (!land.contains(ChunkKey.of(x - 1, z))) {
                add(outgoing, corner(x, z + 1), 3);
            }
        }
        Set<Long> pinches = new HashSet<>();
        outgoing.forEach((key, dirs) -> {
            if (dirs.size() > 1) {
                pinches.add(key);
            }
        });

        List<Ring> outers = new ArrayList<>();
        List<Ring> holes = new ArrayList<>();
        // Rings start at ordinary corners, so that coming back to the start always closes the ring.
        for (boolean allowPinch : new boolean[]{false, true}) {
            for (Long start : new ArrayList<>(outgoing.keySet())) {
                while (outgoing.containsKey(start) && (allowPinch || !pinches.contains(start))) {
                    Ring ring = trace(outgoing, start);
                    (ring.doubleArea() > 0 ? outers : holes).add(ring);
                }
            }
        }

        long anchor = anchorOf(component, preferredAnchor);
        int count = component.size();
        List<Area> result = new ArrayList<>();
        if (outers.size() == 1) {
            result.add(new Area(outers.get(0), holes, count, ChunkKey.x(anchor), ChunkKey.z(anchor)));
            return result;
        }
        // Not expected for chunks connected by sides; kept safe: each hole goes to the outline around it.
        for (int i = 0; i < outers.size(); i++) {
            Ring outer = outers.get(i);
            List<Ring> inside = new ArrayList<>();
            for (Ring hole : holes) {
                Corner probe = hole.corners().get(0);
                if (contains(outer, probe.x() + 0.5, probe.z() + 0.5) || contains(outer, probe.x() - 0.5, probe.z() - 0.5)) {
                    inside.add(hole);
                }
            }
            result.add(new Area(outer, inside, i == 0 ? count : 0, ChunkKey.x(anchor), ChunkKey.z(anchor)));
        }
        return result;
    }

    private static void add(Map<Long, List<Integer>> outgoing, long corner, int direction) {
        outgoing.computeIfAbsent(corner, key -> new ArrayList<>(2)).add(direction);
    }

    private static Ring trace(Map<Long, List<Integer>> outgoing, long start) {
        List<Corner> corners = new ArrayList<>();
        long at = start;
        int direction = take(outgoing, at, -1);
        int first = direction;
        int x = ChunkKey.x(at);
        int z = ChunkKey.z(at);
        int previous = -1;
        while (true) {
            if (direction != previous) {
                corners.add(new Corner(x, z));
            }
            previous = direction;
            x += DX[direction];
            z += DZ[direction];
            at = corner(x, z);
            if (at == start) {
                break;
            }
            direction = take(outgoing, at, direction);
        }
        if (first == previous && !corners.isEmpty()) {
            // The ring entered the start corner going straight on: the start is not a turning point.
            corners.remove(0);
        }
        // Start at the north-west corner, so that the same land always gives the same ring.
        int west = 0;
        for (int i = 1; i < corners.size(); i++) {
            Corner c = corners.get(i);
            Corner best = corners.get(west);
            if (c.z() < best.z() || (c.z() == best.z() && c.x() < best.x())) {
                west = i;
            }
        }
        List<Corner> ordered = new ArrayList<>(corners.subList(west, corners.size()));
        ordered.addAll(corners.subList(0, west));
        return new Ring(ordered);
    }

    /**
     * Removes and returns the edge leaving a corner: the tightest right turn first, then straight on, then left.
     */
    private static int take(Map<Long, List<Integer>> outgoing, long corner, int incoming) {
        List<Integer> dirs = outgoing.get(corner);
        if (dirs == null || dirs.isEmpty()) {
            throw new IllegalStateException("Open outline at corner " + ChunkKey.format(corner));
        }
        int chosen = dirs.get(0);
        if (incoming >= 0) {
            for (int turn : new int[]{1, 0, 3}) {
                int candidate = (incoming + turn) % 4;
                if (dirs.contains(candidate)) {
                    chosen = candidate;
                    break;
                }
            }
        }
        dirs.remove((Integer) chosen);
        if (dirs.isEmpty()) {
            outgoing.remove(corner);
        }
        return chosen;
    }

    private static long anchorOf(List<Long> component, Long preferred) {
        if (preferred != null && component.contains(preferred)) {
            return preferred;
        }
        double sumX = 0;
        double sumZ = 0;
        for (long chunk : component) {
            sumX += ChunkKey.x(chunk);
            sumZ += ChunkKey.z(chunk);
        }
        double midX = sumX / component.size();
        double midZ = sumZ / component.size();
        long best = component.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (long chunk : component) {
            double dx = ChunkKey.x(chunk) - midX;
            double dz = ChunkKey.z(chunk) - midZ;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance || (distance == bestDistance && chunk < best)) {
                best = chunk;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Even-odd test of a point against a ring.
     */
    public static boolean contains(Ring ring, double x, double z) {
        boolean inside = false;
        List<Corner> corners = ring.corners();
        for (int i = 0, j = corners.size() - 1; i < corners.size(); j = i++) {
            Corner a = corners.get(i);
            Corner b = corners.get(j);
            if ((a.z() > z) != (b.z() > z) && x < (double) (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x()) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Whether a point lies on the land of an area: inside its outline and outside its holes.
     */
    public static boolean contains(Area area, double x, double z) {
        if (!contains(area.outer(), x, z)) {
            return false;
        }
        for (Ring hole : area.holes()) {
            if (contains(hole, x, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Total land of a map of areas, for checks: the number of chunks.
     */
    public static int chunkCount(Collection<Area> areas) {
        int total = 0;
        for (Area area : areas) {
            total += area.chunks();
        }
        return total;
    }

    /**
     * Groups claims ({@link ChunkKey} to owner) by owner.
     */
    public static <K> Map<K, Set<Long>> byOwner(Map<Long, K> claims) {
        Map<K, Set<Long>> result = new HashMap<>();
        claims.forEach((chunk, owner) -> result.computeIfAbsent(owner, key -> new HashSet<>()).add(chunk));
        return result;
    }
}
