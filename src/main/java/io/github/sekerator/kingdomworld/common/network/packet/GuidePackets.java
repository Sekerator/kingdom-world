package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.guide.GuideNumbers;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The packets of the guide: the server opens it ({@code /kingdom guide}), the guide asks for the numbers of the configuration
 * its pages show, and the server answers with the values it runs with.
 */
public final class GuidePackets {

    private GuidePackets() {
    }

    /**
     * Server to client: open the guide where it was left.
     */
    public record Open() {

        public void encode(FriendlyByteBuf buf) {
        }

        public static Open decode(FriendlyByteBuf buf) {
            return new Open();
        }

        public static void handle(Open message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientPacketHandlers::handleOpenGuide);
        }
    }

    /**
     * Client to server: the values of the configuration at {@code paths}, the numbers the pages of the guide show. The server
     * answers only for the paths its own guide shows.
     */
    public record NumbersRequest(List<String> paths) {
        public static final RequestPolicy POLICY = new RequestPolicy("guide_numbers", 0, 4, 10_000L);

        public NumbersRequest {
            paths = List.copyOf(paths);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(paths.size());
            for (String path : paths) {
                buf.writeUtf(path, GuideNumbers.MAX_PATH);
            }
        }

        public static NumbersRequest decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > GuideNumbers.MAX_PATHS) {
                throw new IllegalArgumentException("Too many paths of the guide: " + size);
            }
            List<String> paths = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                paths.add(buf.readUtf(GuideNumbers.MAX_PATH));
            }
            return new NumbersRequest(paths);
        }

        public static void handle(NumbersRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireSize(sender, POLICY, "paths", message.paths(), GuideNumbers.MAX_PATHS)) {
                return;
            }
            KingdomWorldNetwork.sendToPlayer(sender, new Numbers(GuideNumbers.current(message.paths(), GuideNumbers.shownByTheGuide())));
        }
    }

    /**
     * Server to client: the values of the configuration of the server by path, for the paths that hold a plain value.
     */
    public record Numbers(Map<String, String> values) {

        public Numbers {
            values = Map.copyOf(values);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(values.size());
            for (Map.Entry<String, String> entry : values.entrySet()) {
                buf.writeUtf(entry.getKey(), GuideNumbers.MAX_PATH);
                buf.writeUtf(entry.getValue(), GuideNumbers.MAX_VALUE);
            }
        }

        public static Numbers decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > GuideNumbers.MAX_PATHS) {
                throw new IllegalArgumentException("Too many numbers of the guide: " + size);
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                values.put(buf.readUtf(GuideNumbers.MAX_PATH), buf.readUtf(GuideNumbers.MAX_VALUE));
            }
            return new Numbers(values);
        }

        public static void handle(Numbers message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleGuideNumbers(message));
        }
    }
}
