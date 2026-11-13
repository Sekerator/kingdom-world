package io.github.sekerator.kingdomworld.common.network;

import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.packet.ProvinceDeltaPacket;
import io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvincePacketsTest {
    private static final KingdomMapInfo HOLDER = new KingdomMapInfo(UUID.fromString("00000000-0000-4000-8000-000000000001"), "Ozerovia",
            0x3366AA, "ruler", 3, 4000, 120, true, 10, -4);

    private static byte[] bytes(java.util.function.Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buf);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    private static FriendlyByteBuf read(byte[] bytes) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
    }

    private static int deflated(byte[] bytes) {
        Deflater deflater = new Deflater();
        deflater.setInput(bytes);
        deflater.finish();
        byte[] out = new byte[bytes.length + 64];
        int total = 0;
        while (!deflater.finished()) {
            total += deflater.deflate(out);
        }
        deflater.end();
        return total;
    }

    private static char[] tile(int... runs) {
        char[] tile = new char[ProvinceMap.TILE_CHUNKS];
        int at = 0;
        for (int i = 0; i < runs.length; i += 2) {
            Arrays.fill(tile, at, at + runs[i + 1], (char) runs[i]);
            at += runs[i + 1];
        }
        return tile;
    }

    @Test
    void aSnapshotGoesThereAndBack() {
        char[] mixed = tile(0, 100, 7, 400, 9, 524);
        ProvinceSnapshotPacket packet = new ProvinceSnapshotPacket("minecraft:overworld", true, new long[]{ProvinceMap.tileKey(-3, 5),
                ProvinceMap.tileKey(4, 4)}, List.of(mixed, new char[0]),
                List.of(new ProvinceSnapshotPacket.Entry(7, -100, 2500, ProvinceKind.HEART, 1234L),
                        new ProvinceSnapshotPacket.Entry(9, 300, 2600, ProvinceKind.WILD, -5L)),
                new int[]{11}, List.of(HOLDER), new int[]{1, 0});
        ProvinceSnapshotPacket back = ProvinceSnapshotPacket.decode(read(bytes(packet::encode)));
        assertEquals("minecraft:overworld", back.dimension());
        assertTrue(back.reset());
        assertArrayEquals(packet.tileKeys(), back.tileKeys());
        assertArrayEquals(mixed, back.tiles().get(0));
        assertEquals(0, back.tiles().get(1).length, "a tile without provinces stays empty");
        assertEquals(packet.provinces(), back.provinces());
        assertArrayEquals(new int[]{11}, back.removed());
        assertEquals(List.of(HOLDER.id()), back.palette().stream().map(KingdomMapInfo::id).toList());
        assertArrayEquals(new int[]{1, 0}, back.owners());
    }

    @Test
    void aDeltaGoesThereAndBack() {
        ProvinceDeltaPacket packet = new ProvinceDeltaPacket("minecraft:overworld", new int[]{7, 9}, List.of(HOLDER), new int[]{0, 1});
        ProvinceDeltaPacket back = ProvinceDeltaPacket.decode(read(bytes(packet::encode)));
        assertArrayEquals(new int[]{7, 9}, back.provinces());
        assertArrayEquals(new int[]{0, 1}, back.owners());
        assertEquals(HOLDER.id(), back.palette().get(0).id());
    }

    @Test
    void aTileIsWrittenAsItsRuns() {
        char[] tile = tile(3, 1, 0, 1022, 3, 1);
        byte[] written = bytes(new ProvinceSnapshotPacket("d", false, new long[]{0}, List.of(tile), List.of(), new int[0], List.of(),
                new int[0])::encode);
        ProvinceSnapshotPacket back = ProvinceSnapshotPacket.decode(read(written));
        assertArrayEquals(tile, back.tiles().get(0));
        // Three runs of two small numbers each, after the dimension and the key of the tile.
        assertTrue(written.length < 32, "A tile of three runs takes " + written.length + " bytes");
        assertArrayEquals(new int[]{(3 << 16) | 1, 1022, (3 << 16) | 1}, ProvinceMap.encode(tile));
    }

    @Test
    void aPartCarriesAtMostItsTiles() {
        long[] keys = new long[ProvinceSnapshotPacket.MAX_TILES + 1];
        List<char[]> tiles = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            keys[i] = ProvinceMap.tileKey(i, 0);
            tiles.add(tile(1, ProvinceMap.TILE_CHUNKS));
        }
        assertThrows(IllegalArgumentException.class, () -> new ProvinceSnapshotPacket("d", true, keys, tiles, List.of(), new int[0],
                List.of(), new int[0]));
        assertThrows(IllegalArgumentException.class, () -> new ProvinceSnapshotPacket("d", true, new long[0], List.of(),
                List.of(new ProvinceSnapshotPacket.Entry(1, 0, 0, ProvinceKind.CORE, 0)), new int[0], List.of(), new int[]{2}));
    }

    /**
     * A world of 600 tiles and 350 provinces drawn like the plan draws them (rough cells around jittered seeds): what a player
     * joining gets, written and as the network compresses it, part by part.
     */
    @Test
    void joiningAWorldOf600TilesAnd350ProvincesWeighsLittle() {
        int tilesX = 25;
        int tilesZ = 24;
        int chunksX = tilesX * ProvinceMap.TILE;
        int chunksZ = tilesZ * ProvinceMap.TILE;
        SplittableRandom random = new SplittableRandom(15);
        int seeds = 350;
        int[] sx = new int[seeds];
        int[] sz = new int[seeds];
        int side = (int) Math.sqrt((double) chunksX * chunksZ / seeds);
        int n = 0;
        for (int z = side / 2; z < chunksZ && n < seeds; z += side) {
            for (int x = side / 2; x < chunksX && n < seeds; x += side) {
                sx[n] = x + random.nextInt(-side / 3, side / 3 + 1);
                sz[n] = z + random.nextInt(-side / 3, side / 3 + 1);
                n++;
            }
        }
        List<ProvinceSnapshotPacket.Entry> entries = new ArrayList<>();
        int[] owners = new int[n];
        for (int i = 0; i < n; i++) {
            entries.add(new ProvinceSnapshotPacket.Entry(i + 1, sx[i] * 16, sz[i] * 16, i % 3 == 0 ? ProvinceKind.WILD : ProvinceKind.CORE, i));
            owners[i] = i % 3 == 0 ? 0 : 1;
        }
        long[] keys = new long[tilesX * tilesZ];
        List<char[]> tiles = new ArrayList<>();
        int k = 0;
        for (int tz = 0; tz < tilesZ; tz++) {
            for (int tx = 0; tx < tilesX; tx++) {
                char[] tile = new char[ProvinceMap.TILE_CHUNKS];
                for (int i = 0; i < tile.length; i++) {
                    int x = tx * 32 + (i & 31);
                    int z = tz * 32 + (i >> 5);
                    int best = 0;
                    long bestDistance = Long.MAX_VALUE;
                    for (int s = 0; s < n; s++) {
                        // A little noise along the borders, as the borders of the plan wander.
                        long dx = x - sx[s];
                        long dz = z - sz[s];
                        long distance = dx * dx + dz * dz + ((x * 31L + z * 17L + s) % 7);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = s + 1;
                        }
                    }
                    tile[i] = (char) best;
                }
                keys[k++] = ProvinceMap.tileKey(tx, tz);
                tiles.add(tile);
            }
        }
        long written = 0;
        long compressed = 0;
        int parts = 0;
        for (int at = 0; at < keys.length; at += ProvinceSnapshotPacket.MAX_TILES) {
            int end = Math.min(keys.length, at + ProvinceSnapshotPacket.MAX_TILES);
            boolean first = at == 0;
            ProvinceSnapshotPacket part = new ProvinceSnapshotPacket("minecraft:overworld", first, Arrays.copyOfRange(keys, at, end),
                    tiles.subList(at, end), first ? entries : List.of(), new int[0], first ? List.of(HOLDER) : List.of(),
                    first ? owners : new int[0]);
            byte[] bytes = bytes(part::encode);
            assertTrue(bytes.length <= 256 * 1024, "A part takes " + bytes.length + " bytes");
            written += bytes.length;
            compressed += deflated(bytes);
            parts++;
        }
        System.out.println("Joining a world of " + keys.length + " tiles and " + n + " provinces: " + parts + " parts, " + written
                + " bytes, " + compressed + " bytes compressed");
        assertTrue(compressed <= 150_000, "Joining takes " + compressed + " bytes compressed");
    }
}
