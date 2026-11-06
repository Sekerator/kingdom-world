package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Public facts about a kingdom shown on the map: its color, its capital and the card a player sees after
 * clicking its land.
 *
 * @param population    number of citizens
 * @param hasCapital    whether the capital lies in the dimension the map shows
 * @param capitalChunkX chunk x of the throne, meaningful only if {@code hasCapital}
 * @param capitalChunkZ chunk z of the throne, meaningful only if {@code hasCapital}
 */
public record KingdomMapInfo(UUID id, String name, int color, String rulerName, int members, int chunks, int population,
                             boolean hasCapital, int capitalChunkX, int capitalChunkZ) {
    public static final int MAX_PLAYER_NAME = 64;

    public KingdomMapInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(rulerName, "rulerName");
        color &= Kingdom.MAX_COLOR;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, KingdomNames.MAX_LENGTH);
        buf.writeInt(color);
        buf.writeUtf(rulerName, MAX_PLAYER_NAME);
        buf.writeVarInt(members);
        buf.writeVarInt(chunks);
        buf.writeVarInt(population);
        buf.writeBoolean(hasCapital);
        if (hasCapital) {
            buf.writeInt(capitalChunkX);
            buf.writeInt(capitalChunkZ);
        }
    }

    public static KingdomMapInfo decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(KingdomNames.MAX_LENGTH);
        int color = buf.readInt();
        String ruler = buf.readUtf(MAX_PLAYER_NAME);
        int members = buf.readVarInt();
        int chunks = buf.readVarInt();
        int population = buf.readVarInt();
        boolean hasCapital = buf.readBoolean();
        int x = hasCapital ? buf.readInt() : 0;
        int z = hasCapital ? buf.readInt() : 0;
        return new KingdomMapInfo(id, name, color, ruler, members, chunks, population, hasCapital, x, z);
    }

    public static void writeList(FriendlyByteBuf buf, List<KingdomMapInfo> list) {
        buf.writeVarInt(list.size());
        for (KingdomMapInfo info : list) {
            info.encode(buf);
        }
    }

    public static List<KingdomMapInfo> readList(FriendlyByteBuf buf, int maxSize) {
        int size = buf.readVarInt();
        if (size < 0 || size > maxSize) {
            throw new DecoderException("Too many kingdoms: " + size + " (max " + maxSize + ")");
        }
        List<KingdomMapInfo> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(decode(buf));
        }
        return list;
    }
}
