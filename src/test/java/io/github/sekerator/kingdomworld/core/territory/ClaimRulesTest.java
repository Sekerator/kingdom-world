package io.github.sekerator.kingdomworld.core.territory;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimRulesTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    private TerritoryIndex index;
    private UUID alpha;
    private UUID beta;

    @BeforeEach
    void setUp() {
        index = new TerritoryIndex();
        alpha = UUID.randomUUID();
        beta = UUID.randomUUID();
    }

    private static long chunk(int x, int z) {
        return ChunkKey.of(x, z);
    }

    /** Alpha with its capital in chunk (0, 0) and the 3x3 square around it. */
    private void foundAlpha() {
        index.establish(alpha, new Capital(OVERWORLD, 8, 64, 8),
                ClaimRules.initialClaim(index, OVERWORLD, chunk(0, 0), 1, 1));
    }

    private Optional<KingdomError> claim(UUID kingdom, int x, int z, int max, int buffer) {
        return ClaimRules.checkClaim(index, kingdom, OVERWORLD, chunk(x, z), max, buffer);
    }

    @Test
    void chunkKeyMatchesGameLayoutAndRoundTrips() {
        long key = ChunkKey.of(-3, 7);
        assertEquals(-3, ChunkKey.x(key));
        assertEquals(7, ChunkKey.z(key));
        // The game packs (x, z) as x in the low and z in the high 32 bits.
        assertEquals((-3L & 0xFFFFFFFFL) | (7L << 32), key);
        assertEquals(2, ChunkKey.distance(chunk(0, 0), chunk(-2, 1)));
        assertEquals(0, new Capital(OVERWORLD, 15, 0, -1).chunkX());
        assertEquals(-1, new Capital(OVERWORLD, 15, 0, -1).chunkZ());
    }

    @Test
    void initialClaimIsTheSquareAroundTheCapital() {
        foundAlpha();
        Territory territory = index.territory(alpha).orElseThrow();
        assertEquals(9, territory.size());
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                assertEquals(alpha, index.ownerOf(OVERWORLD, chunk(x, z)));
            }
        }
        assertNull(index.ownerOf(OVERWORLD, chunk(2, 0)));
        assertNull(index.ownerOf(NETHER, chunk(0, 0)));
    }

    @Test
    void initialClaimSkipsForeignLandAndKeepsConnection() {
        index.establish(beta, new Capital(OVERWORLD, 64, 64, 0), List.of(chunk(4, 0)));
        // Radius 3 around (0, 0) with buffer 1: chunks (3, -1)..(3, 1) touch beta at (4, 0) and are skipped.
        Set<Long> claim = ClaimRules.initialClaim(index, OVERWORLD, chunk(0, 0), 3, 1);
        assertTrue(claim.contains(chunk(0, 0)));
        assertFalse(claim.contains(chunk(3, 0)));
        assertFalse(claim.contains(chunk(3, 1)));
        assertTrue(claim.contains(chunk(3, 2)));
        assertEquals(49 - 3, claim.size());
        for (long key : claim) {
            assertTrue(ChunkKey.distance(key, chunk(4, 0)) > 1, "too close: " + ChunkKey.format(key));
        }
    }

    @Test
    void claimNeedsAdjacentFreeChunk() {
        foundAlpha();
        assertEquals(Optional.empty(), claim(alpha, 2, 0, 25, 1));
        assertEquals(Optional.of(KingdomError.NOT_ADJACENT), claim(alpha, 3, 0, 25, 1));
        // Diagonal contact is not enough.
        assertEquals(Optional.of(KingdomError.NOT_ADJACENT), claim(alpha, 2, 2, 25, 1));
        assertEquals(Optional.of(KingdomError.ALREADY_OWNED), claim(alpha, 1, 1, 25, 1));
        assertEquals(Optional.of(KingdomError.NO_CAPITAL), claim(beta, 5, 5, 25, 1));
        assertEquals(Optional.of(KingdomError.WRONG_DIMENSION),
                ClaimRules.checkClaim(index, alpha, NETHER, chunk(2, 0), 25, 1));
    }

    @Test
    void claimRespectsTheLimit() {
        foundAlpha();
        assertEquals(Optional.of(KingdomError.LIMIT_REACHED), claim(alpha, 2, 0, 9, 0));
        assertEquals(Optional.empty(), claim(alpha, 2, 0, 10, 0));
        index.assign(alpha, chunk(2, 0));
        assertEquals(Optional.of(KingdomError.LIMIT_REACHED), claim(alpha, 3, 0, 10, 0));
    }

    @Test
    void claimKeepsDistanceToOtherKingdoms() {
        foundAlpha();
        index.establish(beta, new Capital(OVERWORLD, 64, 64, 0), List.of(chunk(4, 0)));
        // (2, 0) is two chunks from beta's (4, 0): fine with buffer 1, refused with buffer 2.
        assertEquals(Optional.empty(), claim(alpha, 2, 0, 25, 1));
        assertEquals(Optional.of(KingdomError.TOO_CLOSE_TO_OTHER_KINGDOM), claim(alpha, 2, 0, 25, 2));
        index.assign(alpha, chunk(2, 0));
        // (3, 0) touches beta: refused with buffer 1, allowed when borders may touch.
        assertEquals(Optional.of(KingdomError.TOO_CLOSE_TO_OTHER_KINGDOM), claim(alpha, 3, 0, 25, 1));
        assertEquals(Optional.empty(), claim(alpha, 3, 0, 25, 0));
        index.assign(alpha, chunk(3, 0));
        assertEquals(Optional.of(KingdomError.OWNED_BY_OTHER), claim(alpha, 4, 0, 25, 0));
    }

    @Test
    void unclaimKeepsTerritoryConnected() {
        foundAlpha();
        index.assign(alpha, chunk(2, 0));
        index.assign(alpha, chunk(3, 0));
        // (2, 0) is the only link between the square and (3, 0).
        assertEquals(Optional.of(KingdomError.WOULD_DISCONNECT), ClaimRules.checkUnclaim(index, alpha, OVERWORLD, chunk(2, 0)));
        assertEquals(Optional.empty(), ClaimRules.checkUnclaim(index, alpha, OVERWORLD, chunk(3, 0)));
        // Corners of the square can go, the capital chunk cannot.
        assertEquals(Optional.empty(), ClaimRules.checkUnclaim(index, alpha, OVERWORLD, chunk(1, 1)));
        assertEquals(Optional.of(KingdomError.CAPITAL_CHUNK), ClaimRules.checkUnclaim(index, alpha, OVERWORLD, chunk(0, 0)));
        assertEquals(Optional.of(KingdomError.NOT_OWNED), ClaimRules.checkUnclaim(index, alpha, OVERWORLD, chunk(9, 9)));
        assertEquals(Optional.of(KingdomError.WRONG_DIMENSION), ClaimRules.checkUnclaim(index, alpha, NETHER, chunk(1, 1)));
    }

    @Test
    void cornerMustKeepOneSideToTheCapital() {
        foundAlpha();
        // Removing the middle of an edge keeps both corners of that edge reachable through the other edges.
        assertEquals(Optional.empty(), ClaimRules.checkUnclaim(index, alpha, OVERWORLD, chunk(1, 0)));
        index.release(alpha, chunk(1, 0));
        // Corner (1, 1) now hangs on (0, 1) alone.
        assertEquals(Optional.of(KingdomError.WOULD_DISCONNECT), ClaimRules.checkUnclaim(index, alpha, OVERWORLD, chunk(0, 1)));
        assertEquals(Optional.empty(), ClaimRules.checkUnclaim(index, alpha, OVERWORLD, chunk(1, 1)));
    }

    @Test
    void indexNotifiesListenersAndRejectsInconsistentChanges() {
        List<String> events = new ArrayList<>();
        index.addListener((dimension, chunk, oldOwner, newOwner) ->
                events.add(ChunkKey.format(chunk) + ":" + (oldOwner == null ? "-" : "a") + ">" + (newOwner == null ? "-" : "a")));
        index.establish(alpha, new Capital(OVERWORLD, 0, 64, 0), List.of());
        index.assign(alpha, chunk(1, 0));
        index.release(alpha, chunk(1, 0));
        assertEquals(List.of("[0, 0]:->a", "[1, 0]:->a", "[1, 0]:a>-"), events);

        assertThrows(IllegalArgumentException.class, () -> index.assign(alpha, chunk(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> index.release(alpha, chunk(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> index.establish(beta, new Capital(OVERWORLD, 0, 0, 0), List.of()));

        events.clear();
        assertEquals(Set.of(chunk(0, 0)), Set.copyOf(index.remove(alpha)));
        assertEquals(List.of("[0, 0]:a>-"), events);
        assertNull(index.ownerOf(OVERWORLD, chunk(0, 0)));
        assertTrue(index.claimsIn(OVERWORLD).isEmpty());
    }

    @Test
    void foreignLandSearchWorksForSmallAndLargeRadius() {
        index.establish(beta, new Capital(OVERWORLD, 160, 0, 0), List.of());
        assertTrue(index.hasForeignLandWithin(OVERWORLD, chunk(0, 0), 10, alpha));
        assertFalse(index.hasForeignLandWithin(OVERWORLD, chunk(0, 0), 9, alpha));
        assertFalse(index.hasForeignLandWithin(OVERWORLD, chunk(10, 0), 3, beta));
        assertFalse(index.hasForeignLandWithin(NETHER, chunk(10, 0), 3, null));
        assertTrue(index.hasForeignLandWithin(OVERWORLD, chunk(10, 0), 0, null));
    }

    @Test
    void chunkLimitGrowsWithSubjects() {
        ChunkLimitPolicy policy = ChunkLimitPolicy.perSubject(25, 4);
        assertEquals(25, policy.maxChunks(new KingdomStats(alpha, 1, 0, 9)));
        assertEquals(33, policy.maxChunks(new KingdomStats(alpha, 3, 2, 9)));
        assertThrows(IllegalArgumentException.class, () -> ChunkLimitPolicy.perSubject(-1, 4));
    }
}
