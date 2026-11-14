package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.citizen.WorkforceActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.CitizenView;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Workforce;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The citizens of the player's kingdom on the atlas: asked for the part of the map in view while the atlas shows them, again every
 * few seconds and when the map moves away, and answered only to one who manages the citizens of the kingdom. However many citizens
 * stand there, all come: an answer goes in as many packets as it takes, {@link #MAX_DOTS} citizens in each.
 */
public final class CitizenMapPackets {
    /** Citizens one packet of an answer carries at most; the others come in the next packets of the same answer. */
    public static final int MAX_DOTS = 4096;

    private CitizenMapPackets() {
    }

    /**
     * Client to server: where the citizens of the player's kingdom are in a part of the map, in chunks, the bounds included.
     */
    public record Request(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        /** The atlas asks every few seconds and when the map moves, a second apart at the most. */
        public static final RequestPolicy POLICY = new RequestPolicy("citizen_map", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(minChunkX);
            buf.writeInt(minChunkZ);
            buf.writeInt(maxChunkX);
            buf.writeInt(maxChunkZ);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }

        public static void handle(Request message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            int limit = ClaimChunkPacket.MAX_CHUNK_COORDINATE;
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "minChunkX", message.minChunkX(), -limit, limit)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "minChunkZ", message.minChunkZ(), -limit, limit)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "maxChunkX", message.maxChunkX(), message.minChunkX(), limit)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "maxChunkZ", message.maxChunkZ(), message.minChunkZ(), limit)) {
                return;
            }
            WorkforceActions.sendMap(sender, message);
        }

        /**
         * Whether a block lies in the part of the map asked for.
         */
        public boolean holds(int blockX, int blockZ) {
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            return chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
        }
    }

    /**
     * A citizen on the atlas.
     *
     * @param live whether its body is there now; otherwise it is the last place known
     */
    public record Dot(UUID id, int seed, boolean female, String profession, Workforce.Doing doing, int x, int z, boolean live, boolean away) {
        public Dot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(doing, "doing");
        }
    }

    /**
     * Server to client: one packet of an answer, the citizens of the player's kingdom in the part of the map asked for, in the world of
     * the player.
     *
     * @param allowed whether the player may see them: a member who manages the citizens
     * @param page    the number of this packet in the answer, from 0
     * @param last    whether it is the last packet of the answer
     */
    public record View(boolean allowed, UUID kingdomId, String dimension, int page, boolean last, List<Dot> dots) {
        public View {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(dimension, "dimension");
            if (dots.size() > MAX_DOTS) {
                throw new IllegalArgumentException("A packet carries " + MAX_DOTS + " citizens at most, not " + dots.size());
            }
            if (page < 0) {
                throw new IllegalArgumentException("The page of an answer is not negative: " + page);
            }
            dots = List.copyOf(dots);
        }

        public static final View NONE = new View(false, new UUID(0, 0), "", 0, true, List.of());

        /**
         * The packets of an answer: the citizens {@link #MAX_DOTS} to a packet, and one empty packet when there are none.
         */
        public static List<View> pages(UUID kingdomId, String dimension, List<Dot> dots) {
            List<View> pages = new ArrayList<>();
            int count = Math.max(1, (dots.size() + MAX_DOTS - 1) / MAX_DOTS);
            for (int page = 0; page < count; page++) {
                List<Dot> part = dots.subList(page * MAX_DOTS, Math.min(dots.size(), (page + 1) * MAX_DOTS));
                pages.add(new View(true, kingdomId, dimension, page, page == count - 1, part));
            }
            return pages;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(allowed);
            buf.writeUUID(kingdomId);
            buf.writeUtf(dimension, 128);
            buf.writeVarInt(page);
            buf.writeBoolean(last);
            buf.writeVarInt(dots.size());
            for (Dot dot : dots) {
                buf.writeUUID(dot.id());
                buf.writeInt(dot.seed());
                buf.writeBoolean(dot.female());
                buf.writeUtf(dot.profession(), Profession.MAX_ID_LENGTH);
                buf.writeEnum(dot.doing());
                buf.writeVarInt(dot.x());
                buf.writeVarInt(dot.z());
                buf.writeBoolean(dot.live());
                buf.writeBoolean(dot.away());
            }
        }

        public static View decode(FriendlyByteBuf buf) {
            boolean allowed = buf.readBoolean();
            UUID kingdomId = buf.readUUID();
            String dimension = buf.readUtf(128);
            int page = buf.readVarInt();
            if (page < 0) {
                throw new io.netty.handler.codec.DecoderException("Bad page of the citizens of the atlas: " + page);
            }
            boolean last = buf.readBoolean();
            int size = KingdomView.readSize(buf, MAX_DOTS);
            List<Dot> dots = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                dots.add(new Dot(buf.readUUID(), buf.readInt(), buf.readBoolean(), CitizenView.readProfessionId(buf),
                        KingdomView.readEnum(buf, Workforce.Doing.class), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
            }
            return new View(allowed, kingdomId, dimension, page, last, dots);
        }

        public static void handle(View message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleCitizenMap(message));
        }
    }
}
