package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientLifeHandlers;
import io.github.sekerator.kingdomworld.common.life.LifeActions;
import io.github.sekerator.kingdomworld.common.life.TavernActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.LifeViews;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The life of cities on the network: the window of a tavern, the life of a kingdom in the citizens tab with the festival the
 * king calls there, and festivals and fairs on the atlas. Requests from clients are checked by the server (distance to the
 * innkeeper, rights, money); answers carry a fresh view.
 */
public final class LifePackets {
    private LifePackets() {
    }

    /**
     * Server to client: the window of a tavern, opened or refreshed.
     */
    public record TavernOpen(LifeViews.TavernView view, boolean open) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
            buf.writeBoolean(open);
        }

        public static TavernOpen decode(FriendlyByteBuf buf) {
            return new TavernOpen(LifeViews.TavernView.decode(buf), buf.readBoolean());
        }

        public static void handle(TavernOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLifeHandlers.handleTavern(message.view(), message.open()));
        }
    }

    /**
     * Client to server: something done in the window of a tavern.
     *
     * @param item    the item to buy, by id
     * @param value   how many to buy, or the camp to mark
     * @param account whether to pay from the account rather than with coins
     */
    public record TavernAction(Action action, String item, long value, boolean account) {
        public static final RequestPolicy POLICY = new RequestPolicy("tavern", 0, 20, 10_000L);

        public enum Action {
            REFRESH,
            BUY,
            MARK,
            ROOM
        }

        public TavernAction {
            item = item == null ? "" : item;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(action);
            buf.writeUtf(item, 256);
            buf.writeVarLong(value);
            buf.writeBoolean(account);
        }

        public static TavernAction decode(FriendlyByteBuf buf) {
            return new TavernAction(KingdomView.readEnum(buf, Action.class), buf.readUtf(256), buf.readVarLong(), buf.readBoolean());
        }

        public static void handle(TavernAction message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "value", message.value(), 0, Long.MAX_VALUE)) {
                return;
            }
            TavernActions.handle(sender, message);
        }
    }

    /**
     * Client to server: show me the life of this kingdom.
     */
    public record TabRequest(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("life_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static TabRequest decode(FriendlyByteBuf buf) {
            return new TabRequest(buf.readUUID());
        }

        public static void handle(TabRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                LifeActions.sendTab(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: the life of a kingdom.
     */
    public record TabResponse(LifeViews.TabView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static TabResponse decode(FriendlyByteBuf buf) {
            return new TabResponse(LifeViews.TabView.decode(buf));
        }

        public static void handle(TabResponse message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLifeHandlers.handleTab(message.view()));
        }
    }

    /**
     * Client to server: the king calls a festival.
     */
    public record CallFestival(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("life_festival", 0, 10, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static CallFestival decode(FriendlyByteBuf buf) {
            return new CallFestival(buf.readUUID());
        }

        public static void handle(CallFestival message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                LifeActions.callFestival(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: where festivals and fairs are today, for the atlas.
     */
    public record MapSync(List<LifeViews.MapEvent> events) {
        public void encode(FriendlyByteBuf buf) {
            LifeViews.MapEvent.encodeAll(buf, events);
        }

        public static MapSync decode(FriendlyByteBuf buf) {
            return new MapSync(LifeViews.MapEvent.decodeAll(buf));
        }

        public static void handle(MapSync message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLifeHandlers.handleMap(message.events()));
        }
    }
}
