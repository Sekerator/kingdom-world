package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.army.ArmyMap;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The army on the map of the atlas: what the kingdom of the player sees of its squads and of the squads of others, and the
 * orders given there. The server answers only for the kingdom of the sender and only with what that kingdom sees.
 */
public final class ArmyMapPackets {
    /** Most squads one command moves. */
    public static final int MAX_SQUADS = 16;
    /** Most points of one command: the route of a patrol, or the points on the way. */
    public static final int MAX_POINTS = 8;
    /** Most entries of one kind in a view. */
    public static final int MAX_ENTRIES = 256;
    /** Farthest block coordinate a command may name. */
    public static final int MAX_COORDINATE = 30_000_000;

    private ArmyMapPackets() {
    }

    /**
     * Client to server: show me the army of my kingdom on the map.
     */
    public record Request() {
        public static final RequestPolicy POLICY = new RequestPolicy("army_map_view", 0, 15, 10_000L);

        public void encode(FriendlyByteBuf buf) {
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request();
        }

        public static void handle(Request message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                ArmyMap.send(sender);
            }
        }
    }

    /**
     * A squad of the kingdom of the player.
     *
     * @param order     the id of the kind of its order
     * @param state     0 standing, 1 marching, 2 in battle
     * @param seconds   seconds until it is in place
     * @param formation the id of its formation
     * @param path      the points ahead of it, x and z in pairs
     */
    public record Own(long id, boolean band, String order, int x, int z, int size, int state, int seconds, String formation, int[] path) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeBoolean(band);
            buf.writeUtf(order, 16);
            buf.writeVarInt(x);
            buf.writeVarInt(z);
            buf.writeVarInt(size);
            buf.writeVarInt(state);
            buf.writeVarInt(seconds);
            buf.writeUtf(formation, 16);
            buf.writeVarIntArray(path);
        }

        static Own decode(FriendlyByteBuf buf) {
            return new Own(buf.readVarLong(), buf.readBoolean(), buf.readUtf(16), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf(16), buf.readVarIntArray(2 * 16));
        }
    }

    /**
     * A squad of another kingdom in sight, or the pale mark of one seen last.
     *
     * @param ally    whether the kingdom is an ally
     * @param seconds seconds since it was seen; 0 for a squad in sight
     */
    public record Other(long id, String kingdom, int color, int x, int z, int size, boolean ally, int seconds) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUtf(kingdom, 64);
            buf.writeInt(color);
            buf.writeVarInt(x);
            buf.writeVarInt(z);
            buf.writeVarInt(size);
            buf.writeBoolean(ally);
            buf.writeVarInt(seconds);
        }

        static Other decode(FriendlyByteBuf buf) {
            return new Other(buf.readVarLong(), buf.readUtf(64), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readVarInt());
        }
    }

    /**
     * Server to client: the army on the map.
     *
     * @param allowed  whether the player commands the army of a kingdom; nothing else is sent otherwise
     * @param battles  places of battles going on, x and z in pairs
     */
    public record View(boolean allowed, String dimension, int color, List<Own> own, List<Other> others, List<Other> marks, int[] battles) {
        public static final View NONE = new View(false, "", 0, List.of(), List.of(), List.of(), new int[0]);

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(allowed);
            buf.writeUtf(dimension, 128);
            buf.writeInt(color);
            buf.writeVarInt(own.size());
            own.forEach(entry -> entry.encode(buf));
            buf.writeVarInt(others.size());
            others.forEach(entry -> entry.encode(buf));
            buf.writeVarInt(marks.size());
            marks.forEach(entry -> entry.encode(buf));
            buf.writeVarIntArray(battles);
        }

        public static View decode(FriendlyByteBuf buf) {
            boolean allowed = buf.readBoolean();
            String dimension = buf.readUtf(128);
            int color = buf.readInt();
            List<Own> own = new ArrayList<>();
            for (int i = 0, n = size(buf); i < n; i++) {
                own.add(Own.decode(buf));
            }
            List<Other> others = new ArrayList<>();
            for (int i = 0, n = size(buf); i < n; i++) {
                others.add(Other.decode(buf));
            }
            List<Other> marks = new ArrayList<>();
            for (int i = 0, n = size(buf); i < n; i++) {
                marks.add(Other.decode(buf));
            }
            return new View(allowed, dimension, color, own, others, marks, buf.readVarIntArray(2 * MAX_ENTRIES));
        }

        private static int size(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_ENTRIES) {
                throw new IllegalArgumentException("Too many entries in a view of the army: " + size);
            }
            return size;
        }

        public static void handle(View message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.sekerator.kingdomworld.client.map.ClientArmyMap.update(message));
        }
    }

    /**
     * Client to server: an order given on the map, or a formation.
     *
     * @param kind      the id of the kind of order, or the id of a formation for {@link Action#FORMATION}
     * @param points    the place of the order or the route of a patrol, x and z in pairs
     * @param via       the points on the way, x and z in pairs
     * @param target    the squad attacked, or the caravan escorted
     * @param radius    the radius of a guarded or scouted area, in chunks
     */
    public record Command(Action action, List<Long> squads, String kind, int[] points, int[] via, long target, int radius) {
        public static final RequestPolicy POLICY = new RequestPolicy("army_map_order", 0, 20, 10_000L);

        public enum Action {
            ORDER,
            FORMATION
        }

        public Command {
            squads = List.copyOf(squads);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(action);
            buf.writeVarInt(squads.size());
            squads.forEach(buf::writeVarLong);
            buf.writeUtf(kind, 16);
            buf.writeVarIntArray(points);
            buf.writeVarIntArray(via);
            buf.writeVarLong(target);
            buf.writeVarInt(radius);
        }

        public static Command decode(FriendlyByteBuf buf) {
            Action action = KingdomView.readEnum(buf, Action.class);
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_SQUADS) {
                throw new IllegalArgumentException("Too many squads in an order of the map: " + count);
            }
            List<Long> squads = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                squads.add(buf.readVarLong());
            }
            return new Command(action, squads, buf.readUtf(16), buf.readVarIntArray(2 * MAX_POINTS), buf.readVarIntArray(2 * MAX_POINTS),
                    buf.readVarLong(), buf.readVarInt());
        }

        public static void handle(Command message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireSize(sender, POLICY, "squads", message.squads(), MAX_SQUADS)
                    || !ServerRequestGuard.requireValid(sender, POLICY, "points", message.points().length,
                    message.points().length % 2 == 0 && message.points().length <= 2 * MAX_POINTS && inRange(message.points()))
                    || !ServerRequestGuard.requireValid(sender, POLICY, "via", message.via().length,
                    message.via().length % 2 == 0 && message.via().length <= 2 * MAX_POINTS && inRange(message.via()))
                    || !ServerRequestGuard.requireRange(sender, POLICY, "target", message.target(), 0, Long.MAX_VALUE)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "radius", message.radius(), 0, 4)) {
                return;
            }
            ArmyMap.command(sender, message);
        }

        private static boolean inRange(int[] values) {
            for (int value : values) {
                if (Math.abs(value) > MAX_COORDINATE) {
                    return false;
                }
            }
            return true;
        }
    }
}
