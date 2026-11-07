package io.github.sekerator.kingdomworld.core.territory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryShapesTest {
    private TerritoryShapes shapes;
    private UUID alpha;
    private UUID beta;

    private static long chunk(int x, int z) {
        return ChunkKey.of(x, z);
    }

    @BeforeEach
    void setUp() {
        shapes = new TerritoryShapes();
        alpha = UUID.randomUUID();
        beta = UUID.randomUUID();
        Map<Long, UUID> claims = new HashMap<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                claims.put(chunk(x, z), alpha);
                claims.put(chunk(x + 10, z), beta);
            }
        }
        shapes.reset(claims);
        shapes.setAnchor(alpha, chunk(0, 0));
        Map<UUID, List<ChunkOutlines.Area>> first = shapes.rebuild();
        assertEquals(Set.of(alpha, beta), first.keySet());
    }

    @Test
    void claimingAChunkRedrawsOnlyThatKingdom() {
        shapes.change(chunk(2, 0), null, alpha);
        assertTrue(shapes.hasChanges());
        Map<UUID, List<ChunkOutlines.Area>> changed = shapes.rebuild();
        assertEquals(Set.of(alpha), changed.keySet());
        List<ChunkOutlines.Area> areas = changed.get(alpha);
        assertEquals(1, areas.size());
        assertEquals(10, areas.get(0).chunks());
        assertEquals(8, areas.get(0).outer().corners().size());
        assertEquals(chunk(0, 0), areas.get(0).anchor());
        assertFalse(shapes.hasChanges());
        assertTrue(shapes.rebuild().isEmpty());
    }

    @Test
    void losingTheMiddleChunkLeavesAHole() {
        shapes.change(chunk(0, 0), alpha, null);
        List<ChunkOutlines.Area> areas = shapes.rebuild().get(alpha);
        assertEquals(1, areas.size());
        assertEquals(1, areas.get(0).holes().size());
        assertEquals(8, areas.get(0).chunks());
        assertEquals(List.of(), shapes.rebuild().getOrDefault(beta, List.of()));
    }

    @Test
    void losingABridgeSplitsTheLandIntoEnclaves() {
        shapes.change(chunk(0, -1), alpha, null);
        shapes.change(chunk(0, 0), alpha, null);
        shapes.change(chunk(0, 1), alpha, null);
        List<ChunkOutlines.Area> areas = shapes.rebuild().get(alpha);
        assertEquals(2, areas.size());
        assertEquals(3, areas.get(0).chunks());
        assertEquals(3, areas.get(1).chunks());
    }

    @Test
    void chunkPassingBetweenKingdomsRedrawsBoth() {
        shapes.change(chunk(1, 0), alpha, beta);
        Map<UUID, List<ChunkOutlines.Area>> changed = shapes.rebuild();
        assertEquals(Set.of(alpha, beta), changed.keySet());
        assertEquals(8, ChunkOutlines.chunkCount(changed.get(alpha)));
        // The chunk lies far from beta's land, so beta now has an enclave.
        assertEquals(2, changed.get(beta).size());
        assertTrue(shapes.chunksOf(beta).contains(chunk(1, 0)));
        assertFalse(shapes.chunksOf(alpha).contains(chunk(1, 0)));
    }

    @Test
    void kingdomWithoutLandIsReportedEmpty() {
        for (long chunk : Set.copyOf(shapes.chunksOf(beta))) {
            shapes.change(chunk, beta, null);
        }
        Map<UUID, List<ChunkOutlines.Area>> changed = shapes.rebuild();
        assertEquals(List.of(), changed.get(beta));
        assertFalse(shapes.shapes().containsKey(beta));
    }

    @Test
    void sameOwnerOrUnknownChunkChangesNothing() {
        shapes.change(chunk(0, 0), alpha, alpha);
        shapes.change(chunk(50, 50), beta, null);
        assertFalse(shapes.hasChanges());
    }

    @Test
    void movingTheLabelRedrawsTheKingdom() {
        shapes.setAnchor(beta, chunk(11, 1));
        Map<UUID, List<ChunkOutlines.Area>> changed = shapes.rebuild();
        assertEquals(Set.of(beta), changed.keySet());
        assertEquals(chunk(11, 1), changed.get(beta).get(0).anchor());
    }

    @Test
    void resetMarksKingdomsThatLostAllLand() {
        shapes.reset(Map.of(chunk(0, 0), alpha));
        Map<UUID, List<ChunkOutlines.Area>> changed = shapes.rebuild();
        assertEquals(List.of(), changed.get(beta));
        assertEquals(1, changed.get(alpha).get(0).chunks());
    }
}
