package io.github.sekerator.kingdomworld.core.territory;

import io.github.sekerator.kingdomworld.core.territory.ChunkOutlines.Area;
import io.github.sekerator.kingdomworld.core.territory.ChunkOutlines.Corner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkOutlinesTest {

    private static long chunk(int x, int z) {
        return ChunkKey.of(x, z);
    }

    private static Set<Long> rectangle(int minX, int minZ, int maxX, int maxZ) {
        Set<Long> chunks = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(chunk(x, z));
            }
        }
        return chunks;
    }

    private static List<Corner> corners(int... xz) {
        List<Corner> list = new ArrayList<>();
        for (int i = 0; i < xz.length; i += 2) {
            list.add(new Corner(xz[i], xz[i + 1]));
        }
        return list;
    }

    /**
     * Every chunk centre lies on the land of exactly the areas that contain it, and nothing else does: the outlines
     * describe the same land as the chunks.
     */
    private static void assertCovers(Set<Long> chunks, List<Area> areas) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long key : chunks) {
            minX = Math.min(minX, ChunkKey.x(key));
            minZ = Math.min(minZ, ChunkKey.z(key));
            maxX = Math.max(maxX, ChunkKey.x(key));
            maxZ = Math.max(maxZ, ChunkKey.z(key));
        }
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                int inside = 0;
                for (Area area : areas) {
                    if (ChunkOutlines.contains(area, x + 0.5, z + 0.5)) {
                        inside++;
                    }
                }
                assertEquals(chunks.contains(chunk(x, z)) ? 1 : 0, inside, "chunk " + x + ", " + z);
            }
        }
        assertEquals(chunks.size(), ChunkOutlines.chunkCount(areas));
        for (Area area : areas) {
            assertTrue(area.outer().doubleArea() > 0, "outer ring must run clockwise");
            area.holes().forEach(hole -> assertTrue(hole.doubleArea() < 0, "holes run the other way"));
            assertTrue(chunks.contains(area.anchor()), "label anchor must lie on the land");
        }
    }

    @Test
    void rectangleIsOneRingOfFourCorners() {
        Set<Long> chunks = rectangle(-1, -1, 2, 0);
        List<Area> areas = ChunkOutlines.of(chunks);
        assertEquals(1, areas.size());
        Area area = areas.get(0);
        assertEquals(corners(-1, -1, 3, -1, 3, 1, -1, 1), area.outer().corners());
        assertTrue(area.holes().isEmpty());
        assertEquals(8, area.chunks());
        assertEquals(8, area.outer().doubleArea() / 2);
        assertEquals(-16, area.outer().corners().get(0).blockX());
        assertCovers(chunks, areas);
    }

    @Test
    void singleChunkIsASquare() {
        List<Area> areas = ChunkOutlines.of(Set.of(chunk(5, -7)));
        assertEquals(corners(5, -7, 6, -7, 6, -6, 5, -6), areas.get(0).outer().corners());
        assertEquals(5, areas.get(0).anchorX());
        assertEquals(-7, areas.get(0).anchorZ());
    }

    @Test
    void letterLHasSixCorners() {
        Set<Long> chunks = rectangle(0, 0, 0, 2);
        chunks.addAll(rectangle(1, 2, 2, 2));
        List<Area> areas = ChunkOutlines.of(chunks);
        assertEquals(1, areas.size());
        assertEquals(corners(0, 0, 1, 0, 1, 2, 3, 2, 3, 3, 0, 3), areas.get(0).outer().corners());
        assertCovers(chunks, areas);
    }

    @Test
    void holeInsideIsCutOut() {
        Set<Long> chunks = rectangle(0, 0, 4, 4);
        chunks.removeAll(rectangle(1, 1, 2, 2));
        List<Area> areas = ChunkOutlines.of(chunks);
        assertEquals(1, areas.size());
        Area area = areas.get(0);
        assertEquals(1, area.holes().size());
        assertEquals(-4, area.holes().get(0).doubleArea() / 2);
        assertFalse(ChunkOutlines.contains(area, 1.5, 1.5));
        assertTrue(ChunkOutlines.contains(area, 3.5, 3.5));
        assertCovers(chunks, areas);
    }

    @Test
    void twoEnclavesAreSeparateAreasLargestFirst() {
        Set<Long> chunks = rectangle(0, 0, 1, 1);
        chunks.addAll(rectangle(10, 10, 12, 12));
        List<Area> areas = ChunkOutlines.of(chunks);
        assertEquals(2, areas.size());
        assertEquals(9, areas.get(0).chunks());
        assertEquals(4, areas.get(1).chunks());
        assertCovers(chunks, areas);
    }

    @Test
    void chunksTouchingAtACornerAreSeparateAreas() {
        Set<Long> chunks = Set.of(chunk(0, 0), chunk(1, 1));
        List<Area> areas = ChunkOutlines.of(chunks);
        assertEquals(2, areas.size());
        assertCovers(chunks, areas);
    }

    @Test
    void ringClosedOnlyAtACornerStaysWithoutCrossing() {
        // A ring of land whose inner gap opens to the outside only through a corner.
        Set<Long> chunks = new HashSet<>(List.of(chunk(0, 0), chunk(-1, 0), chunk(-1, 1), chunk(-1, 2), chunk(0, 2),
                chunk(1, 2), chunk(1, 1)));
        List<Area> areas = ChunkOutlines.of(chunks);
        assertEquals(1, areas.size());
        assertCovers(chunks, areas);
    }

    @Test
    void neighbouringKingdomsShareTheirBorder() {
        UUID west = UUID.randomUUID();
        UUID east = UUID.randomUUID();
        Map<Long, UUID> claims = new HashMap<>();
        rectangle(0, 0, 2, 2).forEach(chunk -> claims.put(chunk, west));
        rectangle(3, 0, 5, 2).forEach(chunk -> claims.put(chunk, east));
        Map<UUID, Set<Long>> byOwner = ChunkOutlines.byOwner(claims);
        Area westArea = ChunkOutlines.of(byOwner.get(west)).get(0);
        Area eastArea = ChunkOutlines.of(byOwner.get(east)).get(0);
        assertTrue(westArea.outer().corners().contains(new Corner(3, 0)));
        assertTrue(eastArea.outer().corners().contains(new Corner(3, 0)));
        assertTrue(westArea.outer().corners().contains(new Corner(3, 3)));
        assertTrue(eastArea.outer().corners().contains(new Corner(3, 3)));
        assertFalse(ChunkOutlines.contains(westArea, 3.5, 1.5));
        assertTrue(ChunkOutlines.contains(eastArea, 3.5, 1.5));
    }

    @Test
    void enclosedKingdomIsAHoleOfItsNeighbour() {
        Set<Long> outer = rectangle(0, 0, 6, 6);
        Set<Long> inner = rectangle(2, 2, 3, 3);
        outer.removeAll(inner);
        Area area = ChunkOutlines.of(outer).get(0);
        assertEquals(1, area.holes().size());
        assertEquals(4, -area.holes().get(0).doubleArea() / 2);
        assertCovers(outer, List.of(area));
        assertCovers(inner, ChunkOutlines.of(inner));
    }

    @Test
    void labelStandsOnThePreferredChunkWhenItIsLand() {
        Set<Long> chunks = rectangle(0, 0, 6, 0);
        assertEquals(chunk(6, 0), ChunkOutlines.of(chunks, chunk(6, 0)).get(0).anchor());
        assertEquals(chunk(3, 0), ChunkOutlines.of(chunks, chunk(40, 40)).get(0).anchor());
    }

    @Test
    void randomLandIsDescribedExactly() {
        Random random = new Random(20261107L);
        for (int round = 0; round < 200; round++) {
            Set<Long> chunks = new HashSet<>();
            int size = 3 + random.nextInt(9);
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    if (random.nextDouble() < 0.55) {
                        chunks.add(chunk(x, z));
                    }
                }
            }
            if (chunks.isEmpty()) {
                continue;
            }
            assertCovers(chunks, ChunkOutlines.of(chunks));
        }
    }

    @Test
    void outlinesDoNotDependOnTheOrderOfChunks() {
        List<Long> chunks = new ArrayList<>(rectangle(0, 0, 4, 3));
        chunks.remove(chunk(2, 1));
        List<Area> forward = ChunkOutlines.of(chunks);
        java.util.Collections.reverse(chunks);
        assertEquals(forward, ChunkOutlines.of(chunks));
    }
}
