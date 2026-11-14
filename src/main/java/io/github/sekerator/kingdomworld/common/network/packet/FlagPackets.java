package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.flag.FlagActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The packets of the flags of kingdoms: the king saves the flag drawn in the editor, and the server tells every client the flag
 * every kingdom flies, all of them when a player joins and what changed afterwards.
 */
public final class FlagPackets {
    /** The most kingdoms one packet of the directory tells of; a longer list goes in several packets. */
    public static final int MAX_ENTRIES = 512;

    private FlagPackets() {
    }

    static void writeFlag(FriendlyByteBuf buf, KingdomFlag flag) {
        buf.writeByte(flag.base());
        buf.writeByte(flag.layers().size());
        for (KingdomFlag.Layer layer : flag.layers()) {
            buf.writeUtf(layer.pattern(), KingdomFlag.MAX_PATTERN_LENGTH);
            buf.writeByte(layer.dye());
        }
    }

    /**
     * Reads a flag, refusing more layers than a flag holds, a dye that is none and an id of a pattern too long or of a wrong
     * form.
     */
    static KingdomFlag readFlag(FriendlyByteBuf buf) {
        int base = buf.readUnsignedByte();
        int count = buf.readUnsignedByte();
        if (count > KingdomFlag.MAX_LAYERS) {
            throw new IllegalArgumentException("A flag of " + count + " layers");
        }
        List<KingdomFlag.Layer> layers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String pattern = buf.readUtf(KingdomFlag.MAX_PATTERN_LENGTH);
            int dye = buf.readUnsignedByte();
            if (!KingdomFlag.validDye(dye)) {
                throw new IllegalArgumentException("Not a dye: " + dye);
            }
            layers.add(new KingdomFlag.Layer(pattern, dye));
        }
        if (!KingdomFlag.validDye(base)) {
            throw new IllegalArgumentException("Not a dye: " + base);
        }
        return new KingdomFlag(base, layers);
    }

    /**
     * Client to server: the king saves the flag of the kingdom.
     */
    public record Save(UUID kingdom, KingdomFlag flag) {
        public static final RequestPolicy POLICY = new RequestPolicy("flag_save", 0, 6, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdom);
            writeFlag(buf, flag);
        }

        public static Save decode(FriendlyByteBuf buf) {
            return new Save(buf.readUUID(), readFlag(buf));
        }

        public static void handle(Save message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireSize(sender, POLICY, "layers", message.flag().layers(), KingdomFlag.MAX_LAYERS)) {
                return;
            }
            KingdomActions.Feedback feedback = FlagActions.change(sender.server, sender.getUUID(), message.kingdom(), message.flag());
            KingdomWorldNetwork.sendToPlayer(sender, new ActionResultPacket(feedback.success(), feedback.message()));
        }
    }

    /**
     * The flag a kingdom flies.
     *
     * @param custom whether it is a flag of its own rather than the flag of its colour
     */
    public record Entry(UUID kingdom, KingdomFlag flag, boolean custom) {
    }

    /**
     * Server to client: the flags of kingdoms. A full directory comes in as many packets as it needs, the first marked
     * {@code full}, which replaces everything the client knew; the others add to it.
     *
     * @param removed kingdoms that are no more
     */
    public record Directory(boolean full, List<Entry> entries, List<UUID> removed) {
        public Directory {
            entries = List.copyOf(entries);
            removed = List.copyOf(removed);
            if (entries.size() > MAX_ENTRIES || removed.size() > MAX_ENTRIES) {
                throw new IllegalArgumentException("Too many flags for one packet: " + entries.size() + " and " + removed.size());
            }
        }

        /**
         * The packets of a whole directory, or of the changes to one, of at most {@link #MAX_ENTRIES} entries each.
         */
        public static List<Directory> split(boolean full, List<Entry> entries, List<UUID> removed) {
            List<Directory> packets = new ArrayList<>();
            int count = Math.max(1, Math.max((entries.size() + MAX_ENTRIES - 1) / MAX_ENTRIES, (removed.size() + MAX_ENTRIES - 1) / MAX_ENTRIES));
            for (int i = 0; i < count; i++) {
                packets.add(new Directory(full && i == 0, slice(entries, i), slice(removed, i)));
            }
            return packets;
        }

        private static <T> List<T> slice(List<T> list, int part) {
            int from = Math.min(list.size(), part * MAX_ENTRIES);
            return list.subList(from, Math.min(list.size(), from + MAX_ENTRIES));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(full);
            buf.writeVarInt(entries.size());
            for (Entry entry : entries) {
                buf.writeUUID(entry.kingdom());
                buf.writeBoolean(entry.custom());
                writeFlag(buf, entry.flag());
            }
            buf.writeVarInt(removed.size());
            for (UUID id : removed) {
                buf.writeUUID(id);
            }
        }

        public static Directory decode(FriendlyByteBuf buf) {
            boolean full = buf.readBoolean();
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_ENTRIES) {
                throw new IllegalArgumentException("Too many flags: " + size);
            }
            List<Entry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                UUID kingdom = buf.readUUID();
                boolean custom = buf.readBoolean();
                entries.add(new Entry(kingdom, readFlag(buf), custom));
            }
            int gone = buf.readVarInt();
            if (gone < 0 || gone > MAX_ENTRIES) {
                throw new IllegalArgumentException("Too many kingdoms gone: " + gone);
            }
            List<UUID> removed = new ArrayList<>(gone);
            for (int i = 0; i < gone; i++) {
                removed.add(buf.readUUID());
            }
            return new Directory(full, entries, removed);
        }

        public static void handle(Directory message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleFlags(message));
        }
    }
}
