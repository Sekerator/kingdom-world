package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Server to client: the land of a dimension for map integrations, either all of it or, when there is more than one
 * packet may carry, the square around the player. Later changes arrive as {@link TerritoryDeltaPacket}s.
 *
 * <p>Compact form: for every kingdom of the palette its chunks sorted by row, each written as the step from the
 * previous chunk, so neighbouring chunks of a row take two bytes.
 *
 * @param partial whether only the square of {@code radius} chunks around ({@code centerX}, {@code centerZ}) is
 *                included; otherwise the packet holds all land of the dimension the player may see
 * @param chunks  for every kingdom of the palette its chunks as {@link ChunkKey}s
 */
public record TerritorySnapshotPacket(String dimension, boolean partial, int centerX, int centerZ, int radius,
                                      List<KingdomMapInfo> palette, List<long[]> chunks) {
    /** Most chunks one snapshot carries. */
    public static final int MAX_CHUNKS = 65_536;
    /** Most kingdoms one snapshot names. */
    public static final int MAX_KINGDOMS = 4_096;

    public TerritorySnapshotPacket {
        Objects.requireNonNull(dimension, "dimension");
        palette = List.copyOf(palette);
        chunks = List.copyOf(chunks);
        if (palette.size() != chunks.size() || palette.size() > MAX_KINGDOMS || total(chunks) > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid territory snapshot of " + palette.size() + " kingdom(s)");
        }
    }

    public int chunkCount() {
        return total(chunks);
    }

    private static int total(List<long[]> chunks) {
        long total = 0;
        for (long[] list : chunks) {
            total += list.length;
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * Whether a chunk lies in the part of the dimension this snapshot describes.
     */
    public boolean covers(int chunkX, int chunkZ) {
        return !partial || (Math.abs(chunkX - centerX) <= radius && Math.abs(chunkZ - centerZ) <= radius);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, MapAreaPacket.MAX_DIMENSION_ID);
        buf.writeBoolean(partial);
        if (partial) {
            buf.writeInt(centerX);
            buf.writeInt(centerZ);
            buf.writeVarInt(radius);
        }
        KingdomMapInfo.writeList(buf, palette);
        for (long[] list : chunks) {
            long[] sorted = list.clone();
            Arrays.sort(sorted);
            buf.writeVarInt(sorted.length);
            int x = 0;
            int z = 0;
            for (long chunk : sorted) {
                writeSigned(buf, ChunkKey.z(chunk) - z);
                writeSigned(buf, ChunkKey.x(chunk) - x);
                x = ChunkKey.x(chunk);
                z = ChunkKey.z(chunk);
            }
        }
    }

    public static TerritorySnapshotPacket decode(FriendlyByteBuf buf) {
        String dimension = buf.readUtf(MapAreaPacket.MAX_DIMENSION_ID);
        boolean partial = buf.readBoolean();
        int centerX = partial ? buf.readInt() : 0;
        int centerZ = partial ? buf.readInt() : 0;
        int radius = partial ? buf.readVarInt() : 0;
        if (radius < 0 || radius > ClaimChunkPacket.MAX_CHUNK_COORDINATE) {
            throw new DecoderException("Invalid snapshot radius " + radius);
        }
        List<KingdomMapInfo> palette = KingdomMapInfo.readList(buf, MAX_KINGDOMS);
        long[][] chunks = new long[palette.size()][];
        int total = 0;
        for (int i = 0; i < palette.size(); i++) {
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_CHUNKS - total) {
                throw new DecoderException("Too many chunks in a territory snapshot: " + (total + size));
            }
            total += size;
            long[] list = new long[size];
            int x = 0;
            int z = 0;
            for (int j = 0; j < size; j++) {
                z += readSigned(buf);
                x += readSigned(buf);
                list[j] = ChunkKey.of(x, z);
            }
            chunks[i] = list;
        }
        return new TerritorySnapshotPacket(dimension, partial, centerX, centerZ, radius, palette, Arrays.asList(chunks));
    }

    private static void writeSigned(FriendlyByteBuf buf, int value) {
        buf.writeVarInt((value << 1) ^ (value >> 31));
    }

    private static int readSigned(FriendlyByteBuf buf) {
        int value = buf.readVarInt();
        return (value >>> 1) ^ -(value & 1);
    }

    public static void handle(TerritorySnapshotPacket message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleTerritorySnapshot(message));
    }
}
