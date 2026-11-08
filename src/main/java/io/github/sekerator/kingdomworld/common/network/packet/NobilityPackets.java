package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientNobilityHandlers;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.NobilityViews;
import io.github.sekerator.kingdomworld.common.nobility.NobilitySync;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The nobility on the network: the "Nobility" tab of the throne screen. Requests from clients are checked by the server
 * (rights, what is pending); answers carry a fresh view.
 */
public final class NobilityPackets {
    private static final UUID NOBODY = new UUID(0, 0);

    private NobilityPackets() {
    }

    /**
     * Client to server: show me the nobility of this kingdom.
     */
    public record TabRequest(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("nobility_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static TabRequest decode(FriendlyByteBuf buf) {
            return new TabRequest(buf.readUUID());
        }

        public static void handle(TabRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                NobilitySync.sendTab(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: the tab.
     */
    public record TabResponse(NobilityViews.TabView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static TabResponse decode(FriendlyByteBuf buf) {
            return new TabResponse(NobilityViews.TabView.decode(buf));
        }

        public static void handle(TabResponse message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNobilityHandlers.handleTab(message.view()));
        }
    }

    /**
     * Client to server: something done in the tab.
     *
     * @param first  the house, title, demand, plot or spy it is about
     * @param second a second number: the title of a grant, the house a title goes to, 1 to hunt foreign spies
     * @param target the player a title goes to, or the kingdom a spy goes to
     * @param word   the fief of a grant, the answer, the verdict or the task
     */
    public record TabChange(UUID kingdomId, Action action, long first, long second, UUID target, String word) {
        public static final RequestPolicy POLICY = new RequestPolicy("nobility_change", 0, 20, 10_000L);

        public enum Action {
            GRANT,
            REVOKE,
            ANSWER,
            VERDICT,
            HIRE,
            SEND,
            STATION,
            RANSOM,
            DISMISS
        }

        public TabChange {
            target = target == null ? NOBODY : target;
            word = word == null ? "" : word;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeVarLong(first);
            buf.writeVarLong(second);
            buf.writeUUID(target);
            buf.writeUtf(word, NobilityViews.MAX_WORD);
        }

        public static TabChange decode(FriendlyByteBuf buf) {
            return new TabChange(buf.readUUID(), KingdomView.readEnum(buf, Action.class), buf.readVarLong(), buf.readVarLong(), buf.readUUID(),
                    buf.readUtf(NobilityViews.MAX_WORD));
        }

        public UUID targetOrNull() {
            return NOBODY.equals(target) ? null : target;
        }

        public static void handle(TabChange message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "first", message.first(), -1, Long.MAX_VALUE)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "second", message.second(), -1, Long.MAX_VALUE)) {
                return;
            }
            NobilitySync.handleTab(sender, message);
        }
    }
}
