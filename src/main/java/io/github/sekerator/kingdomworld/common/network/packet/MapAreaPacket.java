package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Server to client: owners of a rectangle of chunks.
 *
 * <p>Compact form: a palette of the kingdoms that appear, and for every chunk (row by row, x fastest) the
 * palette index plus one, 0 meaning unclaimed. Cells are sent run-length encoded as pairs of
 * (run length, value), which keeps mostly empty or mostly uniform areas small.
 */
public record MapAreaPacket(String dimension, int minChunkX, int minChunkZ, int width, int height,
                            List<KingdomMapInfo> palette, int[] cells) {
    public static final int MAX_DIMENSION_ID = 256;

    public MapAreaPacket {
        Objects.requireNonNull(dimension, "dimension");
        palette = List.copyOf(palette);
        if (width < 1 || height < 1 || width > MapAreaRequestPacket.MAX_SIDE || height > MapAreaRequestPacket.MAX_SIDE
                || cells.length != width * height) {
            throw new IllegalArgumentException("Invalid map area " + width + "x" + height + " with " + cells.length + " cells");
        }
    }

    /**
     * Owner palette index of a chunk inside the area, or -1 if unclaimed.
     */
    public int ownerIndex(int chunkX, int chunkZ) {
        return cells[(chunkZ - minChunkZ) * width + (chunkX - minChunkX)] - 1;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, MAX_DIMENSION_ID);
        buf.writeInt(minChunkX);
        buf.writeInt(minChunkZ);
        buf.writeVarInt(width);
        buf.writeVarInt(height);
        KingdomMapInfo.writeList(buf, palette);
        writeRuns(buf, cells);
    }

    public static MapAreaPacket decode(FriendlyByteBuf buf) {
        String dimension = buf.readUtf(MAX_DIMENSION_ID);
        int minX = buf.readInt();
        int minZ = buf.readInt();
        int width = buf.readVarInt();
        int height = buf.readVarInt();
        int max = MapAreaRequestPacket.MAX_SIDE;
        if (width < 1 || height < 1 || width > max || height > max) {
            throw new DecoderException("Invalid map area size " + width + "x" + height);
        }
        List<KingdomMapInfo> palette = KingdomMapInfo.readList(buf, width * height);
        int[] cells = readRuns(buf, width * height, palette.size());
        return new MapAreaPacket(dimension, minX, minZ, width, height, palette, cells);
    }

    static void writeRuns(FriendlyByteBuf buf, int[] cells) {
        int runs = 0;
        for (int i = 0; i < cells.length; i++) {
            if (i == 0 || cells[i] != cells[i - 1]) {
                runs++;
            }
        }
        buf.writeVarInt(runs);
        int start = 0;
        for (int i = 1; i <= cells.length; i++) {
            if (i == cells.length || cells[i] != cells[start]) {
                buf.writeVarInt(i - start);
                buf.writeVarInt(cells[start]);
                start = i;
            }
        }
    }

    static int[] readRuns(FriendlyByteBuf buf, int total, int paletteSize) {
        int runs = buf.readVarInt();
        if (runs < 0 || runs > total) {
            throw new DecoderException("Invalid run count " + runs);
        }
        int[] cells = new int[total];
        int position = 0;
        for (int r = 0; r < runs; r++) {
            int length = buf.readVarInt();
            int value = buf.readVarInt();
            if (length < 1 || length > total - position || value < 0 || value > paletteSize) {
                throw new DecoderException("Invalid run " + length + "x" + value + " at " + position);
            }
            java.util.Arrays.fill(cells, position, position + length, value);
            position += length;
        }
        if (position != total) {
            throw new DecoderException("Runs cover " + position + " of " + total + " cells");
        }
        return cells;
    }

    public static void handle(MapAreaPacket message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleMapArea(message));
    }
}
