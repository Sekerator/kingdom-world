package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
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
 * Server to a player: the provinces of a dimension, a part at a time. A whole snapshot is sent when the player joins or changes
 * dimension, one part a tick: its first part starts the provinces known over ({@code reset}), its parts carry the tiles of the
 * provinces at most {@value #MAX_TILES} at a time, and the first also the table of the provinces and their holders. Later the
 * same packet without {@code reset} carries what changed: tiles, provinces described anew or forgotten.
 *
 * <p>A tile is written as its runs of equal numbers, each the number of the province and the length of the run as small
 * numbers; a tile without runs has no provinces any more.
 *
 * @param reset     whether the provinces known before are forgotten first (the first part of a whole snapshot)
 * @param tileKeys  the tiles carried, as {@link ProvinceMap#tileKey}
 * @param tiles     for every tile its numbers of provinces, chunk by chunk ({@link ProvinceMap#indexInTile}), or an empty array
 *                  for a tile that has no provinces any more
 * @param provinces provinces described anew
 * @param removed   provinces forgotten
 * @param palette   the kingdoms holding provinces of this packet
 * @param owners    for every province of {@code provinces} the palette index plus one, 0 for a province of nobody
 */
public record ProvinceSnapshotPacket(String dimension, boolean reset, long[] tileKeys, List<char[]> tiles, List<Entry> provinces,
                                     int[] removed, List<KingdomMapInfo> palette, int[] owners) {
    /** Most tiles a packet carries. */
    public static final int MAX_TILES = 32;
    /** Most provinces a packet describes or forgets. */
    public static final int MAX_PROVINCES = ProvinceMap.MAX_ID + 1;
    /** Most kingdoms a packet names. */
    public static final int MAX_KINGDOMS = 4_096;

    /**
     * What a player is told of a province: its number, its middle, what it is to its state and the seed of its names.
     */
    public record Entry(int id, int centerX, int centerZ, ProvinceKind kind, long nameSeed) {
        public Entry {
            Objects.requireNonNull(kind, "kind");
            if (id < 1 || id > ProvinceMap.MAX_ID) {
                throw new IllegalArgumentException("Province number out of range: " + id);
            }
        }
    }

    public ProvinceSnapshotPacket {
        Objects.requireNonNull(dimension, "dimension");
        tiles = List.copyOf(tiles);
        provinces = List.copyOf(provinces);
        palette = List.copyOf(palette);
        if (tileKeys.length != tiles.size() || tileKeys.length > MAX_TILES || provinces.size() != owners.length
                || provinces.size() + removed.length > MAX_PROVINCES || palette.size() > MAX_KINGDOMS) {
            throw new IllegalArgumentException("Invalid province snapshot of " + tileKeys.length + " tile(s) and " + provinces.size()
                    + " province(s)");
        }
        for (char[] tile : tiles) {
            if (tile.length != 0 && tile.length != ProvinceMap.TILE_CHUNKS) {
                throw new IllegalArgumentException("A tile has " + ProvinceMap.TILE_CHUNKS + " chunks");
            }
        }
        for (int owner : owners) {
            if (owner < 0 || owner > palette.size()) {
                throw new IllegalArgumentException("Invalid holder index " + owner);
            }
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, MapAreaPacket.MAX_DIMENSION_ID);
        buf.writeBoolean(reset);
        buf.writeVarInt(tileKeys.length);
        for (int i = 0; i < tileKeys.length; i++) {
            buf.writeLong(tileKeys[i]);
            writeTile(buf, tiles.get(i));
        }
        KingdomMapInfo.writeList(buf, palette);
        buf.writeVarInt(provinces.size());
        for (int i = 0; i < provinces.size(); i++) {
            Entry entry = provinces.get(i);
            buf.writeVarInt(entry.id());
            buf.writeInt(entry.centerX());
            buf.writeInt(entry.centerZ());
            buf.writeByte(entry.kind().ordinal());
            buf.writeLong(entry.nameSeed());
            buf.writeVarInt(owners[i]);
        }
        buf.writeVarInt(removed.length);
        for (int id : removed) {
            buf.writeVarInt(id);
        }
    }

    public static ProvinceSnapshotPacket decode(FriendlyByteBuf buf) {
        String dimension = buf.readUtf(MapAreaPacket.MAX_DIMENSION_ID);
        boolean reset = buf.readBoolean();
        int tileCount = buf.readVarInt();
        if (tileCount < 0 || tileCount > MAX_TILES) {
            throw new DecoderException("Invalid number of tiles " + tileCount);
        }
        long[] keys = new long[tileCount];
        char[][] tiles = new char[tileCount][];
        for (int i = 0; i < tileCount; i++) {
            keys[i] = buf.readLong();
            tiles[i] = readTile(buf);
        }
        List<KingdomMapInfo> palette = KingdomMapInfo.readList(buf, MAX_KINGDOMS);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_PROVINCES) {
            throw new DecoderException("Invalid number of provinces " + count);
        }
        Entry[] provinces = new Entry[count];
        int[] owners = new int[count];
        ProvinceKind[] kinds = ProvinceKind.values();
        for (int i = 0; i < count; i++) {
            int id = buf.readVarInt();
            int x = buf.readInt();
            int z = buf.readInt();
            int kind = buf.readUnsignedByte();
            long seed = buf.readLong();
            owners[i] = buf.readVarInt();
            if (id < 1 || id > ProvinceMap.MAX_ID || kind >= kinds.length || owners[i] < 0 || owners[i] > palette.size()) {
                throw new DecoderException("Invalid province " + id);
            }
            provinces[i] = new Entry(id, x, z, kinds[kind], seed);
        }
        int removedCount = buf.readVarInt();
        if (removedCount < 0 || removedCount > MAX_PROVINCES - count) {
            throw new DecoderException("Invalid number of provinces forgotten " + removedCount);
        }
        int[] removed = new int[removedCount];
        for (int i = 0; i < removedCount; i++) {
            removed[i] = buf.readVarInt();
            if (removed[i] < 1 || removed[i] > ProvinceMap.MAX_ID) {
                throw new DecoderException("Invalid province forgotten " + removed[i]);
            }
        }
        return new ProvinceSnapshotPacket(dimension, reset, keys, List.of(tiles), List.of(provinces), removed, palette, owners);
    }

    /**
     * A tile as its runs: their count, then the number and the length of each.
     */
    static void writeTile(FriendlyByteBuf buf, char[] tile) {
        if (tile.length == 0) {
            buf.writeVarInt(0);
            return;
        }
        int[] runs = ProvinceMap.encode(tile);
        buf.writeVarInt(runs.length);
        for (int run : runs) {
            buf.writeVarInt(run >>> 16);
            buf.writeVarInt(run & 0xFFFF);
        }
    }

    static char[] readTile(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count == 0) {
            return new char[0];
        }
        if (count < 0 || count > ProvinceMap.TILE_CHUNKS) {
            throw new DecoderException("Invalid number of runs " + count);
        }
        int[] runs = new int[count];
        for (int i = 0; i < count; i++) {
            int id = buf.readVarInt();
            int length = buf.readVarInt();
            if (id < 0 || id > ProvinceMap.MAX_ID || length < 1 || length > ProvinceMap.TILE_CHUNKS) {
                throw new DecoderException("Invalid run of a tile");
            }
            runs[i] = (id << 16) | length;
        }
        try {
            return ProvinceMap.decode(runs);
        } catch (IllegalArgumentException e) {
            throw new DecoderException(e.getMessage());
        }
    }

    public static void handle(ProvinceSnapshotPacket message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleProvinceSnapshot(message));
    }
}
