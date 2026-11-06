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
 * Server to all players: chunks of one dimension that changed owner since the last tick.
 *
 * @param chunks chunk keys (x in the low, z in the high 32 bits)
 * @param owners for every chunk the palette index plus one, 0 meaning the chunk became unclaimed
 */
public record TerritoryDeltaPacket(String dimension, List<KingdomMapInfo> palette, long[] chunks, int[] owners) {
    public static final int MAX_CHANGES = 65_536;

    public TerritoryDeltaPacket {
        Objects.requireNonNull(dimension, "dimension");
        palette = List.copyOf(palette);
        if (chunks.length != owners.length || chunks.length > MAX_CHANGES) {
            throw new IllegalArgumentException("Invalid territory delta with " + chunks.length + " chunks");
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, MapAreaPacket.MAX_DIMENSION_ID);
        KingdomMapInfo.writeList(buf, palette);
        buf.writeVarInt(chunks.length);
        for (int i = 0; i < chunks.length; i++) {
            buf.writeLong(chunks[i]);
            buf.writeVarInt(owners[i]);
        }
    }

    public static TerritoryDeltaPacket decode(FriendlyByteBuf buf) {
        String dimension = buf.readUtf(MapAreaPacket.MAX_DIMENSION_ID);
        List<KingdomMapInfo> palette = KingdomMapInfo.readList(buf, MAX_CHANGES);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_CHANGES) {
            throw new DecoderException("Invalid territory delta size " + size);
        }
        long[] chunks = new long[size];
        int[] owners = new int[size];
        for (int i = 0; i < size; i++) {
            chunks[i] = buf.readLong();
            owners[i] = buf.readVarInt();
            if (owners[i] < 0 || owners[i] > palette.size()) {
                throw new DecoderException("Invalid owner index " + owners[i]);
            }
        }
        return new TerritoryDeltaPacket(dimension, palette, chunks, owners);
    }

    public static void handle(TerritoryDeltaPacket message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleTerritoryDelta(message));
    }
}
