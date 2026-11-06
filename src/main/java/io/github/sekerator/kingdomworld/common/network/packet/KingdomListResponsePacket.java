package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomSummary;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Server to client: one page of the public kingdom list.
 *
 * @param total    number of kingdoms on the server
 * @param page     page index this packet answers
 * @param kingdoms the kingdoms on this page, at most {@link KingdomListRequestPacket#MAX_PAGE_SIZE}
 */
public record KingdomListResponsePacket(int total, int page, List<KingdomSummary> kingdoms) {
    public static final int MAX_ENTRIES = KingdomListRequestPacket.MAX_PAGE_SIZE;

    public KingdomListResponsePacket {
        Objects.requireNonNull(kingdoms, "kingdoms");
        kingdoms = List.copyOf(kingdoms);
        if (total < 0 || page < 0 || kingdoms.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid kingdom list page: total=" + total + ", page=" + page
                    + ", entries=" + kingdoms.size());
        }
    }

    /**
     * Builds one page of the given kingdoms.
     */
    public static KingdomListResponsePacket page(List<Kingdom> all, int page, int pageSize) {
        long from = (long) page * pageSize;
        List<KingdomSummary> entries = new ArrayList<>();
        for (long i = from; i < all.size() && i < from + pageSize; i++) {
            entries.add(KingdomSummary.of(all.get((int) i)));
        }
        return new KingdomListResponsePacket(all.size(), page, entries);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(total);
        buf.writeVarInt(page);
        buf.writeVarInt(kingdoms.size());
        for (KingdomSummary kingdom : kingdoms) {
            buf.writeUUID(kingdom.id());
            buf.writeUtf(kingdom.name(), KingdomNames.MAX_LENGTH);
            buf.writeInt(kingdom.color());
            buf.writeBoolean(kingdom.hasRuler());
        }
    }

    public static KingdomListResponsePacket decode(FriendlyByteBuf buf) {
        int total = buf.readVarInt();
        int page = buf.readVarInt();
        int count = buf.readVarInt();
        if (total < 0 || page < 0 || count < 0 || count > MAX_ENTRIES) {
            throw new DecoderException("Invalid kingdom list header: total=" + total + ", page=" + page + ", count=" + count);
        }
        List<KingdomSummary> kingdoms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            kingdoms.add(new KingdomSummary(buf.readUUID(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt(), buf.readBoolean()));
        }
        return new KingdomListResponsePacket(total, page, kingdoms);
    }

    public static void handle(KingdomListResponsePacket message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleKingdomList(message));
    }
}
