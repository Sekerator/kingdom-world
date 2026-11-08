package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientDiplomacyHandlers;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacySync;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Diplomacy on the network: the "Diplomacy" tab of the throne screen and the marks of the atlas. Requests from clients are
 * checked by the server (rights, the state of treaties and wars); answers carry a fresh view.
 */
public final class DiplomacyPackets {
    private static final UUID NOBODY = new UUID(0, 0);

    private DiplomacyPackets() {
    }

    /**
     * Client to server: show me the diplomacy tab of this kingdom.
     */
    public record TabRequest(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("diplomacy_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static TabRequest decode(FriendlyByteBuf buf) {
            return new TabRequest(buf.readUUID());
        }

        public static void handle(TabRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                DiplomacySync.sendTab(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: the diplomacy tab.
     */
    public record TabResponse(DiplomacyViews.TabView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static TabResponse decode(FriendlyByteBuf buf) {
            return new TabResponse(DiplomacyViews.TabView.decode(buf));
        }

        public static void handle(TabResponse message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientDiplomacyHandlers.handleTab(message.view()));
        }
    }

    /**
     * Client to server: something done in the diplomacy tab.
     *
     * @param target the other kingdom of an offer, a break or a declaration
     * @param value  the offer, the war, or 1 to break a treaty for a declaration
     * @param word   the goal of a declaration
     */
    public record TabChange(UUID kingdomId, Action action, UUID target, long value, String word) {
        public static final RequestPolicy POLICY = new RequestPolicy("diplomacy_change", 0, 20, 10_000L);

        public enum Action {
            PROPOSE_PACT,
            PROPOSE_ALLIANCE,
            OFFER_VASSALAGE,
            DEMAND_VASSALAGE,
            ASK_RELEASE,
            BREAK,
            DECLARE,
            ACCEPT,
            DECLINE,
            CANCEL,
            JOIN,
            REFUSE,
            PEACE_WHITE,
            PEACE_DEMAND,
            PEACE_CONCEDE
        }

        public TabChange {
            target = target == null ? NOBODY : target;
            word = word == null ? "" : word;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeUUID(target);
            buf.writeVarLong(value);
            buf.writeUtf(word, DiplomacyViews.MAX_WORD);
        }

        public static TabChange decode(FriendlyByteBuf buf) {
            return new TabChange(buf.readUUID(), KingdomView.readEnum(buf, Action.class), buf.readUUID(), buf.readVarLong(),
                    buf.readUtf(DiplomacyViews.MAX_WORD));
        }

        public static void handle(TabChange message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "value", message.value(), 0, Long.MAX_VALUE)) {
                return;
            }
            DiplomacySync.handleTab(sender, message);
        }
    }

    /**
     * Server to client: the marks of relations, wars and shields on the atlas.
     */
    public record MapInfo(DiplomacyViews.MapView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static MapInfo decode(FriendlyByteBuf buf) {
            return new MapInfo(DiplomacyViews.MapView.decode(buf));
        }

        public static void handle(MapInfo message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientDiplomacyHandlers.handleMap(message.view()));
        }
    }
}
