package io.github.sekerator.kingdomworld.common.network;

import io.github.sekerator.kingdomworld.common.network.packet.FlagPackets;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The king saves a flag, and the server tells the flags of all kingdoms in packets of at most 512 of them.
 */
class FlagPacketsTest {
    private static final KingdomFlag FLAG = new KingdomFlag(11, List.of(new KingdomFlag.Layer("minecraft:cross", 0),
            new KingdomFlag.Layer("somemod:flags/eagle", 4)));

    @Test
    void saveGoesThereAndBack() {
        UUID kingdom = UUID.randomUUID();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new FlagPackets.Save(kingdom, FLAG).encode(buf);
        FlagPackets.Save read = FlagPackets.Save.decode(buf);
        assertEquals(0, buf.readableBytes());
        assertEquals(new FlagPackets.Save(kingdom, FLAG), read);
    }

    @Test
    void saveRefusesWhatNoFlagHolds() {
        assertThrows(RuntimeException.class, () -> FlagPackets.Save.decode(raw(11, 7, "minecraft:cross", 0)), "seven layers");
        assertThrows(RuntimeException.class, () -> FlagPackets.Save.decode(raw(16, 0, null, 0)), "a field of no dye");
        assertThrows(RuntimeException.class, () -> FlagPackets.Save.decode(raw(11, 1, "minecraft:cross", 16)), "a layer of no dye");
        assertThrows(RuntimeException.class, () -> FlagPackets.Save.decode(raw(11, 1, "m:" + "a".repeat(KingdomFlag.MAX_PATTERN_LENGTH), 0)),
                "an id too long");
        assertThrows(RuntimeException.class, () -> FlagPackets.Save.decode(raw(11, 1, "minecraft:base", 0)), "the field as a layer");
        assertThrows(RuntimeException.class, () -> FlagPackets.Save.decode(raw(11, 1, "Not An Id", 0)));
    }

    /**
     * A packet to save a flag of {@code count} layers of the same pattern and dye, written by hand.
     */
    private static FriendlyByteBuf raw(int base, int count, String pattern, int dye) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(UUID.randomUUID());
        buf.writeByte(base);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            buf.writeUtf(pattern);
            buf.writeByte(dye);
        }
        return buf;
    }

    @Test
    void theDirectoryGoesThereAndBack() {
        List<FlagPackets.Entry> entries = List.of(new FlagPackets.Entry(UUID.randomUUID(), FLAG, true),
                new FlagPackets.Entry(UUID.randomUUID(), KingdomFlag.plain(14), false));
        List<UUID> removed = List.of(UUID.randomUUID());
        FlagPackets.Directory directory = new FlagPackets.Directory(false, entries, removed);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        directory.encode(buf);
        FlagPackets.Directory read = FlagPackets.Directory.decode(buf);
        assertEquals(0, buf.readableBytes());
        assertEquals(directory, read);
        assertFalse(read.full());
    }

    @Test
    void aLongDirectoryGoesInSeveralPacketsOnlyTheFirstFull() {
        List<FlagPackets.Entry> entries = new ArrayList<>();
        for (int i = 0; i < FlagPackets.MAX_ENTRIES * 2 + 3; i++) {
            entries.add(new FlagPackets.Entry(UUID.randomUUID(), KingdomFlag.plain(i % 16), i % 2 == 0));
        }
        List<FlagPackets.Directory> packets = FlagPackets.Directory.split(true, entries, List.of());
        assertEquals(3, packets.size());
        assertTrue(packets.get(0).full());
        assertFalse(packets.get(1).full());
        assertFalse(packets.get(2).full());
        assertEquals(FlagPackets.MAX_ENTRIES, packets.get(0).entries().size());
        assertEquals(3, packets.get(2).entries().size());
        List<FlagPackets.Entry> joined = new ArrayList<>();
        packets.forEach(packet -> joined.addAll(packet.entries()));
        assertEquals(entries, joined);

        List<FlagPackets.Directory> empty = FlagPackets.Directory.split(true, List.of(), List.of());
        assertEquals(1, empty.size(), "an empty directory still clears what the client knew");
        assertTrue(empty.get(0).full());

        assertThrows(IllegalArgumentException.class, () -> new FlagPackets.Directory(false, entries, List.of()));
        FriendlyByteBuf tooMany = new FriendlyByteBuf(Unpooled.buffer());
        tooMany.writeBoolean(false);
        tooMany.writeVarInt(FlagPackets.MAX_ENTRIES + 1);
        assertThrows(IllegalArgumentException.class, () -> FlagPackets.Directory.decode(tooMany));
    }
}
