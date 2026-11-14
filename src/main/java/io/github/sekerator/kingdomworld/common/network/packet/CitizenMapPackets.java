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
 * The citizens of the player's kingdom on the atlas: asked for when the atlas opens with them shown and when they are switched on,
 * and answered only to one who manages the citizens of the kingdom.
 */
public final class CitizenMapPackets {
    /** Citizens one answer shows at most. */
    public static final int MAX_DOTS = 4096;

    private CitizenMapPackets() {
    }

    /**
     * Client to server: where the citizens of the player's kingdom are.
     */
    public record Request() {
        public static final RequestPolicy POLICY = new RequestPolicy("citizen_map", 0, 10, 10_000L);

        public void encode(FriendlyByteBuf buf) {
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request();
        }

        public static void handle(Request message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)) {
                return;
            }
            WorkforceActions.sendMap(sender);
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
     * Server to client: the citizens of the player's kingdom in the world of the player.
     *
     * @param allowed whether the player may see them: a member who manages the citizens
     */
    public record View(boolean allowed, UUID kingdomId, String dimension, List<Dot> dots) {
        public View {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(dimension, "dimension");
            dots = List.copyOf(dots.size() > MAX_DOTS ? dots.subList(0, MAX_DOTS) : dots);
        }

        public static final View NONE = new View(false, new UUID(0, 0), "", List.of());

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(allowed);
            buf.writeUUID(kingdomId);
            buf.writeUtf(dimension, 128);
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
            int size = KingdomView.readSize(buf, MAX_DOTS);
            List<Dot> dots = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                dots.add(new Dot(buf.readUUID(), buf.readInt(), buf.readBoolean(), CitizenView.readProfessionId(buf),
                        KingdomView.readEnum(buf, Workforce.Doing.class), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
            }
            return new View(allowed, kingdomId, dimension, dots);
        }

        public static void handle(View message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleCitizenMap(message));
        }
    }
}
