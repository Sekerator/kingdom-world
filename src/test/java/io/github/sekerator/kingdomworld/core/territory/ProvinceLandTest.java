package io.github.sekerator.kingdomworld.core.territory;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvinceLandTest {
    private static final String WORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    private static TerritoryIndex index(ProvinceMap map) {
        TerritoryIndex index = new TerritoryIndex();
        index.attachProvinces(map);
        return index;
    }

    private static ProvinceMap province(ProvinceMap map, int id, int x1, int z1, int x2, int z2) {
        map.define(Province.plain(id, (x1 + x2) * 8, (z1 + z2) * 8, ProvinceKind.CORE));
        map.fill(id, x1, z1, x2, z2);
        return map;
    }

    @Test
    void tilesGoToRunsAndBack() {
        SplittableRandom random = new SplittableRandom(7);
        for (int round = 0; round < 200; round++) {
            char[] tile = new char[ProvinceMap.TILE_CHUNKS];
            int at = 0;
            while (at < tile.length) {
                int length = 1 + random.nextInt(round % 2 == 0 ? 8 : 300);
                char id = (char) (random.nextInt(4) == 0 ? 0 : random.nextInt(1, ProvinceMap.MAX_ID + 1));
                for (int i = at; i < Math.min(tile.length, at + length); i++) {
                    tile[i] = id;
                }
                at += length;
            }
            int[] runs = ProvinceMap.encode(tile);
            assertArrayEquals(tile, ProvinceMap.decode(runs));
            assertTrue(runs.length <= tile.length);
        }
        assertEquals(1, ProvinceMap.encode(new char[ProvinceMap.TILE_CHUNKS]).length, "an empty tile is one run");
        assertThrows(IllegalArgumentException.class, () -> ProvinceMap.decode(new int[]{(5 << 16) | 1000}));
        assertThrows(IllegalArgumentException.class, () -> ProvinceMap.decode(new int[]{(5 << 16) | 1000, (5 << 16) | 100}));
        assertThrows(IllegalArgumentException.class, () -> ProvinceMap.decode(new int[]{5 << 16}));
    }

    @Test
    void aHundredThousandChangesAgreeWithAPlainMap() {
        SplittableRandom random = new SplittableRandom(42);
        ProvinceMap map = new ProvinceMap(WORLD);
        TerritoryIndex index = index(map);
        Map<Long, UUID> claims = new HashMap<>();
        Map<Long, Integer> chunkProvince = new HashMap<>();
        Map<Integer, UUID> holders = new HashMap<>();
        List<UUID> kingdoms = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            UUID id = new UUID(1, k);
            kingdoms.add(id);
            long capital = ChunkKey.of(-60 + 40 * k, 0);
            index.establish(id, new Capital(WORLD, ChunkKey.x(capital) * 16, 64, 0), List.of());
            claims.put(capital, id);
        }
        for (int id = 1; id <= 12; id++) {
            map.define(Province.plain(id, 0, 0, ProvinceKind.WILD));
        }
        for (int step = 0; step < 100_000; step++) {
            int op = random.nextInt(10);
            long chunk = ChunkKey.of(random.nextInt(-80, 81), random.nextInt(-80, 81));
            UUID kingdom = kingdoms.get(random.nextInt(kingdoms.size()));
            if (op < 3) {
                if (!claims.containsKey(chunk)) {
                    index.assign(kingdom, chunk);
                    claims.put(chunk, kingdom);
                }
            } else if (op < 5) {
                UUID owner = claims.get(chunk);
                if (owner != null && index.territory(owner).orElseThrow().capital().chunk() != chunk) {
                    index.release(owner, chunk);
                    claims.remove(chunk);
                }
            } else if (op < 6) {
                int id = random.nextInt(1, 13);
                UUID holder = random.nextInt(3) == 0 ? null : kingdom;
                index.transferProvince(id, holder, step);
                holders.put(id, holder);
            } else if (op < 7) {
                int id = random.nextInt(0, 13);
                int x = ChunkKey.x(chunk);
                int z = ChunkKey.z(chunk);
                int w = random.nextInt(6);
                int h = random.nextInt(6);
                map.fill(id, x, z, x + w, z + h);
                for (int dz = 0; dz <= h; dz++) {
                    for (int dx = 0; dx <= w; dx++) {
                        if (id == 0) {
                            chunkProvince.remove(ChunkKey.of(x + dx, z + dz));
                        } else {
                            chunkProvince.put(ChunkKey.of(x + dx, z + dz), id);
                        }
                    }
                }
            }
            // Every step a few chunks are compared with the plain map.
            for (int probe = 0; probe < 4; probe++) {
                long at = ChunkKey.of(random.nextInt(-90, 91), random.nextInt(-90, 91));
                Integer province = chunkProvince.get(at);
                UUID expected = claims.containsKey(at) ? claims.get(at) : province == null ? null : holders.get(province);
                assertEquals(expected, index.ownerOf(WORLD, at), "owner of " + ChunkKey.format(at) + " at step " + step);
                assertEquals(claims.get(at), index.claimOwner(WORLD, at));
                assertEquals(province == null ? 0 : province, index.provinceAt(WORLD, at));
                assertNull(index.ownerOf(NETHER, at), "provinces lie in one dimension");
            }
        }
        int[] counts = new int[13];
        for (int id : chunkProvince.values()) {
            counts[id]++;
        }
        for (int id = 1; id <= 12; id++) {
            assertEquals(counts[id], map.chunks(id), "chunks of province " + id);
        }
        // The tiles still say what the plain map says, chunk by chunk.
        for (Map.Entry<Long, Integer> entry : chunkProvince.entrySet()) {
            assertEquals((int) entry.getValue(), map.provinceAt(entry.getKey()));
        }
    }

    @Test
    void foreignLandNearbyIsFoundAsByLookingAtEveryChunk() {
        SplittableRandom random = new SplittableRandom(3);
        ProvinceMap map = new ProvinceMap(WORLD);
        TerritoryIndex index = index(map);
        UUID a = new UUID(2, 1);
        UUID b = new UUID(2, 2);
        index.establish(a, new Capital(WORLD, 0, 64, 0), List.of());
        index.establish(b, new Capital(WORLD, 640, 64, 0), List.of());
        province(map, 1, 10, 10, 20, 25);
        province(map, 2, -30, -5, -22, 5);
        index.transferProvince(1, b, 0);
        index.transferProvince(2, a, 0);
        for (int i = 0; i < 30; i++) {
            index.assign(random.nextBoolean() ? a : b, ChunkKey.of(random.nextInt(-40, 41), random.nextInt(-40, 41)));
        }
        for (int i = 0; i < 2000; i++) {
            long center = ChunkKey.of(random.nextInt(-50, 51), random.nextInt(-50, 51));
            int radius = random.nextInt(0, 21);
            UUID self = random.nextInt(3) == 0 ? null : random.nextBoolean() ? a : b;
            boolean expected = false;
            for (int dx = -radius; dx <= radius && !expected; dx++) {
                for (int dz = -radius; dz <= radius && !expected; dz++) {
                    UUID owner = index.ownerOf(WORLD, ChunkKey.offset(center, dx, dz));
                    expected = owner != null && !owner.equals(self);
                }
            }
            assertEquals(expected, index.hasForeignLandWithin(WORLD, center, radius, self));
        }
        assertFalse(index.hasForeignLandWithin(NETHER, 0, 20, null));
    }

    @Test
    void claimsKeepToTheRulesBesideProvinces() {
        ProvinceMap map = new ProvinceMap(WORLD);
        TerritoryIndex index = index(map);
        UUID mine = new UUID(3, 1);
        UUID theirs = new UUID(3, 2);
        index.establish(mine, new Capital(WORLD, 8, 64, 8), List.of(ChunkKey.of(1, 0)));
        index.establish(theirs, new Capital(WORLD, 16 * 100, 64, 8), List.of());
        province(map, 1, 10, -5, 20, 5);
        province(map, 2, 30, -5, 40, 5);
        index.transferProvince(1, mine, 0);
        index.transferProvince(2, theirs, 0);
        // Beside its own province a kingdom may claim though its other land is far.
        assertEquals(Optional.empty(), ClaimRules.checkClaim(index, mine, WORLD, ChunkKey.of(9, 0), 100, 1));
        // Inside its own province the land is its already; inside another's it belongs to that kingdom.
        assertEquals(Optional.of(KingdomError.ALREADY_OWNED), ClaimRules.checkClaim(index, mine, WORLD, ChunkKey.of(15, 0), 100, 1));
        assertEquals(Optional.of(KingdomError.OWNED_BY_OTHER), ClaimRules.checkClaim(index, mine, WORLD, ChunkKey.of(35, 0), 100, 1));
        // The buffer holds against a province as against a claim.
        index.assign(mine, ChunkKey.of(9, 0));
        index.assign(mine, ChunkKey.of(21, 0));
        index.assign(mine, ChunkKey.of(22, 0));
        assertEquals(Optional.empty(), ClaimRules.checkClaim(index, mine, WORLD, ChunkKey.of(23, 0), 100, 1));
        for (int x = 23; x <= 28; x++) {
            index.assign(mine, ChunkKey.of(x, 0));
        }
        assertEquals(Optional.of(KingdomError.TOO_CLOSE_TO_OTHER_KINGDOM), ClaimRules.checkClaim(index, mine, WORLD, ChunkKey.of(29, 0), 100, 1));
        assertEquals(Optional.of(KingdomError.NOT_ADJACENT), ClaimRules.checkClaim(index, mine, WORLD, ChunkKey.of(28, 2), 100, 1));
        // The limit counts claims only: eleven claims against a limit of twelve leave room for one, whatever the provinces hold.
        assertEquals(11, index.territory(mine).orElseThrow().size());
        assertEquals(Optional.empty(), ClaimRules.checkClaim(index, mine, WORLD, ChunkKey.of(10, 6), 12, 1));
        assertEquals(Optional.of(KingdomError.LIMIT_REACHED), ClaimRules.checkClaim(index, mine, WORLD, ChunkKey.of(10, 6), 11, 1));
        assertEquals(11 + 11 * 11, index.landSize(mine));
        // A claim inside its own province is counted once.
        index.assign(mine, ChunkKey.of(12, 0));
        assertEquals(12 + 11 * 11 - 1, index.landSize(mine));
    }

    @Test
    void claimsJoinedThroughAProvinceStayJoined() {
        ProvinceMap map = new ProvinceMap(WORLD);
        TerritoryIndex index = index(map);
        UUID mine = new UUID(4, 1);
        // The capital and a claim beside it; far away an exclave province with two claims at its edge.
        index.establish(mine, new Capital(WORLD, 8, 64, 8), List.of(ChunkKey.of(1, 0)));
        province(map, 1, 50, 50, 55, 55);
        index.assign(mine, ChunkKey.of(56, 52));
        index.assign(mine, ChunkKey.of(57, 52));
        assertFalse(index.staysConnectedWithout(mine, ChunkKey.of(1, 0)), "without the province the far claims hang loose");
        index.transferProvince(1, mine, 0);
        assertTrue(index.staysConnectedWithout(mine, ChunkKey.of(1, 0)), "the province holds the far claims");
        assertFalse(index.staysConnectedWithout(mine, ChunkKey.of(56, 52)), "the outer claim would be cut from the province");
        assertEquals(Optional.of(KingdomError.WOULD_DISCONNECT), ClaimRules.checkUnclaim(index, mine, WORLD, ChunkKey.of(56, 52)));
        // A claim inside its own province joins what touches it when given up.
        index.assign(mine, ChunkKey.of(58, 52));
        UUID other = new UUID(4, 2);
        index.establish(other, new Capital(WORLD, 16 * 200, 64, 8), List.of());
        index.transferProvince(1, other, 0);
        assertFalse(index.staysConnectedWithout(mine, ChunkKey.of(1, 0)));
    }

    @Test
    void chunksOfAProvinceAreToldAFewThousandATick() {
        ProvinceMap map = new ProvinceMap(WORLD);
        TerritoryIndex index = index(map);
        province(map, 1, 0, 0, 99, 99);
        UUID king = new UUID(5, 1);
        index.establish(king, new Capital(WORLD, 8, 64, 8), List.of(ChunkKey.of(1, 0), ChunkKey.of(2, 0)));
        List<Integer> provinceEvents = new ArrayList<>();
        int[] chunkEvents = {0};
        List<Long> told = new ArrayList<>();
        index.addProvinceListener((dimension, id, oldOwner, newOwner) -> provinceEvents.add(id));
        index.addProvinceChunkListener((dimension, chunk, oldOwner, newOwner) -> {
            chunkEvents[0]++;
            told.add(chunk);
            assertNull(oldOwner);
            assertEquals(king, newOwner);
        });
        index.addListener((dimension, chunk, oldOwner, newOwner) -> {
            throw new AssertionError("claims did not change");
        });
        assertTrue(index.transferProvince(1, king, 3));
        assertFalse(index.transferProvince(1, king, 3), "the same holder again changes nothing");
        assertEquals(List.of(1), provinceEvents);
        assertEquals(0, chunkEvents[0], "nothing is told before the drain");
        assertEquals(4096, index.drainProvinceChunks(4096));
        assertEquals(4096, index.drainProvinceChunks(4096));
        // 10 000 chunks, three of them claimed: the claims did not change hands.
        assertEquals(10_000 - 3 - 8192, index.drainProvinceChunks(4096));
        assertFalse(index.provinceChunksPending());
        assertEquals(0, index.drainProvinceChunks(4096));
        assertEquals(10_000 - 3, chunkEvents[0]);
        assertEquals(told.size(), new java.util.HashSet<>(told).size(), "every chunk once");
        assertEquals(3, map.acquiredDay(1));
    }

    @Test
    void aKingdomRemovedLeavesItsProvincesToNobody() {
        ProvinceMap map = new ProvinceMap(WORLD);
        TerritoryIndex index = index(map);
        province(map, 1, 0, 0, 9, 9);
        province(map, 2, 20, 0, 29, 9);
        UUID king = new UUID(6, 1);
        index.establish(king, new Capital(WORLD, 8, 64, 8), List.of());
        index.transferProvince(1, king, 0);
        index.transferProvince(2, king, 0);
        assertTrue(index.hasLand(WORLD));
        List<String> events = new ArrayList<>();
        index.addProvinceListener((dimension, id, oldOwner, newOwner) -> events.add(id + ":" + Objects.equals(oldOwner, king) + ":" + newOwner));
        index.remove(king);
        assertEquals(List.of("1:true:null", "2:true:null"), events);
        assertNull(index.ownerOf(WORLD, ChunkKey.of(5, 5)));
        assertFalse(index.hasLand(WORLD), "wild provinces are no land of a kingdom");
        assertEquals(List.of(), index.provincesOf(king));
    }

    @Test
    void theOwnerOfAChunkIsFoundFast() {
        ProvinceMap map = new ProvinceMap(WORLD);
        TerritoryIndex index = index(map);
        UUID king = new UUID(7, 1);
        List<Long> claimed = new ArrayList<>();
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 15; z++) {
                claimed.add(ChunkKey.of(x, z));
            }
        }
        index.establish(king, new Capital(WORLD, 8, 64, 8), claimed);
        // About 600 tiles of provinces, as a ring of states would make.
        int id = 1;
        for (int tx = 0; tx < 25; tx++) {
            for (int tz = 0; tz < 24; tz++) {
                map.define(Province.plain(id, 0, 0, ProvinceKind.CORE));
                map.fill(id, 96 + tx * 32, tz * 32, 96 + tx * 32 + 31, tz * 32 + 31);
                id++;
            }
        }
        for (int p = 1; p < id; p += 2) {
            index.transferProvince(p, king, 0);
        }
        SplittableRandom random = new SplittableRandom(1);
        long[] onClaims = new long[1 << 16];
        long[] onProvinces = new long[1 << 16];
        for (int i = 0; i < onClaims.length; i++) {
            onClaims[i] = claimed.get(random.nextInt(claimed.size()));
            // Players walk: the next chunk is mostly in the same tile.
            onProvinces[i] = ChunkKey.of(96 + (i / 64) % 800, (i / 4096) * 3 + (i % 64) / 8);
        }
        long sink = 0;
        double claimNanos = 0;
        double provinceNanos = 0;
        for (int round = 0; round < 6; round++) {
            long start = System.nanoTime();
            for (long chunk : onClaims) {
                sink += index.ownerOf(WORLD, chunk) == null ? 0 : 1;
            }
            claimNanos = (System.nanoTime() - start) / (double) onClaims.length;
            start = System.nanoTime();
            for (long chunk : onProvinces) {
                sink += index.ownerOf(WORLD, chunk) == null ? 0 : 1;
            }
            provinceNanos = (System.nanoTime() - start) / (double) onProvinces.length;
        }
        System.out.printf("ownerOf: %.1f ns on a claim, %.1f ns on a province; %d tiles take about %d KB (%d bytes a tile)%n", claimNanos,
                provinceNanos, map.tileCount(), map.memoryBytes() / 1024, map.memoryBytes() / Math.max(1, map.tileCount()));
        assertTrue(sink > 0);
        assertEquals(600, map.tileCount());
        assertTrue(map.memoryBytes() < 1_400_000, "600 tiles fit in 1.3 MB");
        assertTrue(provinceNanos < 2_000, "a lookup on a province is quick");
    }
}
