package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Server to a player: provinces of a dimension that changed hands, one entry a province however large it is; also the holders
 * of every province anew when what a player may see of the kingdoms changed.
 *
 * @param provinces the numbers of the provinces
 * @param owners    for every province the palette index plus one, 0 for a province of nobody (or of a kingdom the player may not
 *                  see)
 */
public record ProvinceDeltaPacket(String dimension, int[] provinces, List<KingdomMapInfo> palette, int[] owners) {
    /** Most provinces a packet names. */
    public static final int MAX_PROVINCES = ProvinceMap.MAX_ID + 1;

    public ProvinceDeltaPacket {
        Objects.requireNonNull(dimension, "dimension");
        palette = List.copyOf(palette);
        if (provinces.length != owners.length || provinces.length > MAX_PROVINCES || palette.size() > ProvinceSnapshotPacket.MAX_KINGDOMS) {
            throw new IllegalArgumentException("Invalid province delta of " + provinces.length + " province(s)");
        }
        for (int owner : owners) {
            if (owner < 0 || owner > palette.size()) {
                throw new IllegalArgumentException("Invalid holder index " + owner);
            }
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, MapAreaPacket.MAX_DIMENSION_ID);
        KingdomMapInfo.writeList(buf, palette);
        buf.writeVarInt(provinces.length);
        for (int i = 0; i < provinces.length; i++) {
            buf.writeVarInt(provinces[i]);
            buf.writeVarInt(owners[i]);
        }
    }

    public static ProvinceDeltaPacket decode(FriendlyByteBuf buf) {
        String dimension = buf.readUtf(MapAreaPacket.MAX_DIMENSION_ID);
        List<KingdomMapInfo> palette = KingdomMapInfo.readList(buf, ProvinceSnapshotPacket.MAX_KINGDOMS);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_PROVINCES) {
            throw new DecoderException("Invalid province delta size " + size);
        }
        int[] provinces = new int[size];
        int[] owners = new int[size];
        for (int i = 0; i < size; i++) {
            provinces[i] = buf.readVarInt();
            owners[i] = buf.readVarInt();
            if (provinces[i] < 1 || provinces[i] > ProvinceMap.MAX_ID || owners[i] < 0 || owners[i] > palette.size()) {
                throw new DecoderException("Invalid province " + provinces[i] + " or holder " + owners[i]);
            }
        }
        return new ProvinceDeltaPacket(dimension, provinces, palette, owners);
    }

    public static void handle(ProvinceDeltaPacket message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleProvinceDelta(message));
    }
}
