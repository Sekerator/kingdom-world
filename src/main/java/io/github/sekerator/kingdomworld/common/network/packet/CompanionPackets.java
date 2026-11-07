package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientCompanionHandlers;
import io.github.sekerator.kingdomworld.common.companion.CompanionActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.CompanionViews;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Companions and parties on the network: the market and the hire at a desk of the guild, the window of a companion, the
 * window of a party and the list in the corner of the screen. Requests from clients are checked by the server (the desk
 * and its distance, ownership, the lead of the party, money, limits); answers carry a fresh view.
 */
public final class CompanionPackets {
    private CompanionPackets() {
    }

    // ---------------------------------------------------------------- the market

    /**
     * Client to server: show me the market of this desk.
     */
    public record MarketRequest(BlockPos pos) {
        public static final RequestPolicy POLICY = new RequestPolicy("companion_market_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
        }

        public static MarketRequest decode(FriendlyByteBuf buf) {
            return new MarketRequest(buf.readBlockPos());
        }

        public static void handle(MarketRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                CompanionActions.sendMarket(sender, message.pos());
            }
        }
    }

    /**
     * Server to client: the market of a desk.
     */
    public record MarketOpen(CompanionViews.MarketView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static MarketOpen decode(FriendlyByteBuf buf) {
            return new MarketOpen(CompanionViews.MarketView.decode(buf));
        }

        public static void handle(MarketOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCompanionHandlers.handleMarket(message.view()));
        }
    }

    /**
     * Client to server: something done on the market of a desk.
     *
     * @param offer the offer bought or hired
     * @param coins whether to pay with the coins carried rather than from the account
     */
    public record MarketAction(BlockPos pos, Action action, long offer, boolean coins) {
        public static final RequestPolicy POLICY = new RequestPolicy("companion_market", 0, 20, 10_000L);

        public enum Action {
            REFRESH,
            TAKE,
            CLOSE_MARKET,
            OPEN_MARKET
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeEnum(action);
            buf.writeVarLong(offer);
            buf.writeBoolean(coins);
        }

        public static MarketAction decode(FriendlyByteBuf buf) {
            return new MarketAction(buf.readBlockPos(), KingdomView.readEnum(buf, Action.class), buf.readVarLong(), buf.readBoolean());
        }

        public static void handle(MarketAction message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "offer", message.offer(), 0, Long.MAX_VALUE)) {
                return;
            }
            CompanionActions.handleMarket(sender, message);
        }
    }

    // ---------------------------------------------------------------- a companion

    /**
     * Server to client: the state of the companion whose window is open.
     */
    public record CompanionUpdate(CompanionViews.CompanionView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static CompanionUpdate decode(FriendlyByteBuf buf) {
            return new CompanionUpdate(CompanionViews.CompanionView.decode(buf));
        }

        public static void handle(CompanionUpdate message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCompanionHandlers.handleCompanion(message.view()));
        }
    }

    /**
     * Client to server: an order or a decision about one of the sender's companions.
     */
    public record CompanionAction(UUID companion, Action action) {
        public static final RequestPolicy POLICY = new RequestPolicy("companion_action", 0, 20, 10_000L);

        public enum Action {
            FOLLOW,
            STAND,
            GUARD,
            CONTRACT,
            /** Set a slave free into the owner's service for a wage. */
            FREE,
            /** Set a slave free as a citizen of the owner's kingdom. */
            FREE_TO_KINGDOM,
            SELL,
            /** Let a free companion go. */
            DISMISS
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(companion);
            buf.writeEnum(action);
        }

        public static CompanionAction decode(FriendlyByteBuf buf) {
            return new CompanionAction(buf.readUUID(), KingdomView.readEnum(buf, Action.class));
        }

        public static void handle(CompanionAction message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                CompanionActions.handleCompanion(sender, message);
            }
        }
    }

    // ---------------------------------------------------------------- a party

    /**
     * Client to server: show me my party.
     */
    public record PartyRequest() {
        public static final RequestPolicy POLICY = new RequestPolicy("party_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
        }

        public static PartyRequest decode(FriendlyByteBuf buf) {
            return new PartyRequest();
        }

        public static void handle(PartyRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                CompanionActions.sendParty(sender, true);
            }
        }
    }

    /**
     * Server to client: the window of the party, opened or refreshed.
     */
    public record PartyOpen(CompanionViews.PartyView view, boolean open) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
            buf.writeBoolean(open);
        }

        public static PartyOpen decode(FriendlyByteBuf buf) {
            return new PartyOpen(CompanionViews.PartyView.decode(buf), buf.readBoolean());
        }

        public static void handle(PartyOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCompanionHandlers.handleParty(message.view(), message.open()));
        }
    }

    /**
     * Client to server: something done in the window of the party.
     *
     * @param target the member acted on
     * @param name   the player invited
     * @param value  the party of an invitation, a share, or 0 and 1 for a switch
     */
    public record PartyAction(Action action, UUID target, String name, long value) {
        public static final RequestPolicy POLICY = new RequestPolicy("party_action", 0, 20, 10_000L);

        public enum Action {
            INVITE,
            ACCEPT,
            DECLINE,
            LEAVE,
            KICK,
            LEADER,
            SHARE,
            EVEN,
            FIRE
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(action);
            buf.writeUUID(target);
            buf.writeUtf(name, CompanionViews.MAX_NAME);
            buf.writeVarLong(value);
        }

        public static PartyAction decode(FriendlyByteBuf buf) {
            return new PartyAction(KingdomView.readEnum(buf, Action.class), buf.readUUID(), buf.readUtf(CompanionViews.MAX_NAME), buf.readVarLong());
        }

        public static void handle(PartyAction message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireLength(sender, POLICY, "name", message.name(), CompanionViews.MAX_NAME)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "value", message.value(), 0, Long.MAX_VALUE)) {
                return;
            }
            CompanionActions.handleParty(sender, message);
        }
    }

    /**
     * Server to client: the list in the corner of the screen.
     */
    public record PartyHud(List<CompanionViews.HudLine> lines) {
        public PartyHud {
            lines = List.copyOf(lines);
        }

        public void encode(FriendlyByteBuf buf) {
            CompanionViews.writeHud(buf, lines);
        }

        public static PartyHud decode(FriendlyByteBuf buf) {
            return new PartyHud(CompanionViews.readHud(buf));
        }

        public static void handle(PartyHud message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCompanionHandlers.handleHud(message.lines()));
        }
    }
}
