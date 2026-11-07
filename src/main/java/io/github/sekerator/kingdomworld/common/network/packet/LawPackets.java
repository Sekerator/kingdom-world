package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientLawHandlers;
import io.github.sekerator.kingdomworld.common.law.LawActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.LawViews;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The law on the network: the "Law" tab of the throne screen, the player's window of the law, the windows to give up to a
 * guard, to pay at a gate and to buy back confiscated goods, and the line above the hotbar. Requests from clients are
 * checked by the server (rights, distance to a guard or a place of ransom, money); answers carry a fresh view.
 */
public final class LawPackets {
    private static final UUID NOBODY = new UUID(0, 0);

    private LawPackets() {
    }

    // ---------------------------------------------------------------- the law tab

    /**
     * Client to server: show me the law tab of this kingdom.
     */
    public record TabRequest(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("law_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static TabRequest decode(FriendlyByteBuf buf) {
            return new TabRequest(buf.readUUID());
        }

        public static void handle(TabRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                LawActions.sendTab(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: the law tab.
     */
    public record TabResponse(LawViews.TabView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static TabResponse decode(FriendlyByteBuf buf) {
            return new TabResponse(LawViews.TabView.decode(buf));
        }

        public static void handle(TabResponse message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLawHandlers.handleTab(message.view()));
        }
    }

    /**
     * Client to server: a decision of the court.
     *
     * @param player the wanted player, for a pardon or a fine
     * @param value  the fine, the multiplier in permille, the entry fee, the reward for heads (-1 the config's), or 1 to ban
     *               slaves and 0 to allow them
     */
    public record TabChange(UUID kingdomId, Action action, UUID player, long value) {
        public static final RequestPolicy POLICY = new RequestPolicy("law_change", 0, 20, 10_000L);

        public enum Action {
            PARDON,
            SET_FINE,
            MULTIPLIER,
            ENTRY_FEE,
            BOUNTY,
            SLAVERY
        }

        public TabChange {
            player = player == null ? NOBODY : player;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeUUID(player);
            buf.writeLong(value);
        }

        public static TabChange decode(FriendlyByteBuf buf) {
            return new TabChange(buf.readUUID(), KingdomView.readEnum(buf, Action.class), buf.readUUID(), buf.readLong());
        }

        public static void handle(TabChange message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "value", message.value(), -1, Money.MAX)) {
                return;
            }
            LawActions.handleTab(sender, message);
        }
    }

    // ---------------------------------------------------------------- the player's window

    /**
     * Client to server: open my window of the law, or something done in it.
     *
     * @param kingdomId the kingdom whose debt is paid
     * @param value     the amount paid, the confiscation to buy back, or 1 to switch paying at gates on
     */
    public record PlayerAction(Action action, UUID kingdomId, long value) {
        public static final RequestPolicy POLICY = new RequestPolicy("law_player", 0, 20, 10_000L);

        public enum Action {
            OPEN,
            REFRESH,
            PAY_DEBT,
            AUTO_PAY,
            RANSOM_VIEW
        }

        public PlayerAction {
            kingdomId = kingdomId == null ? NOBODY : kingdomId;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(action);
            buf.writeUUID(kingdomId);
            buf.writeLong(value);
        }

        public static PlayerAction decode(FriendlyByteBuf buf) {
            return new PlayerAction(KingdomView.readEnum(buf, Action.class), buf.readUUID(), buf.readLong());
        }

        public static void handle(PlayerAction message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "value", message.value(), 0, Money.MAX)) {
                return;
            }
            LawActions.handlePlayer(sender, message);
        }
    }

    /**
     * Server to client: the player's window of the law, opened or refreshed.
     */
    public record PlayerOpen(LawViews.PlayerView view, boolean open) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
            buf.writeBoolean(open);
        }

        public static PlayerOpen decode(FriendlyByteBuf buf) {
            return new PlayerOpen(LawViews.PlayerView.decode(buf), buf.readBoolean());
        }

        public static void handle(PlayerOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLawHandlers.handlePlayer(message.view(), message.open()));
        }
    }

    // ---------------------------------------------------------------- dialogs

    /**
     * Server to client: open the window to give up to a guard or to pay at a gate.
     */
    public record DialogOpen(LawViews.DialogView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static DialogOpen decode(FriendlyByteBuf buf) {
            return new DialogOpen(LawViews.DialogView.decode(buf));
        }

        public static void handle(DialogOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLawHandlers.handleDialog(message.view()));
        }
    }

    /**
     * Client to server: give up and pay (or accept the sentence), or pay the entry fee.
     *
     * @param account whether to pay from the account rather than with coins
     */
    public record DialogAction(LawViews.Dialog dialog, UUID kingdomId, boolean account) {
        public static final RequestPolicy POLICY = new RequestPolicy("law_dialog", 0, 10, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(dialog);
            buf.writeUUID(kingdomId);
            buf.writeBoolean(account);
        }

        public static DialogAction decode(FriendlyByteBuf buf) {
            return new DialogAction(KingdomView.readEnum(buf, LawViews.Dialog.class), buf.readUUID(), buf.readBoolean());
        }

        public static void handle(DialogAction message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                LawActions.handleDialog(sender, message);
            }
        }
    }

    /**
     * Server to client: open the window to buy back confiscated goods, or refresh it.
     */
    public record RansomOpen(LawViews.RansomView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static RansomOpen decode(FriendlyByteBuf buf) {
            return new RansomOpen(LawViews.RansomView.decode(buf));
        }

        public static void handle(RansomOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLawHandlers.handleRansom(message.view()));
        }
    }

    /**
     * Client to server: buy back confiscated goods.
     */
    public record RansomAction(long id, boolean account) {
        public static final RequestPolicy POLICY = new RequestPolicy("law_ransom", 0, 10, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeBoolean(account);
        }

        public static RansomAction decode(FriendlyByteBuf buf) {
            return new RansomAction(buf.readVarLong(), buf.readBoolean());
        }

        public static void handle(RansomAction message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "id", message.id(), 1, Long.MAX_VALUE)) {
                return;
            }
            LawActions.ransom(sender, message.id(), message.account());
        }
    }

    // ---------------------------------------------------------------- the line above the hotbar

    /**
     * Server to client: the line about the player's record in the kingdom whose land they stand on.
     */
    public record WantedLinePacket(LawViews.WantedLine line) {
        public void encode(FriendlyByteBuf buf) {
            line.encode(buf);
        }

        public static WantedLinePacket decode(FriendlyByteBuf buf) {
            return new WantedLinePacket(LawViews.WantedLine.decode(buf));
        }

        public static void handle(WantedLinePacket message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLawHandlers.handleLine(message.line()));
        }
    }
}
