package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientArmyHandlers;
import io.github.sekerator.kingdomworld.common.army.ArmyActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.ArmyViews;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.core.BlockPos;
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
 * The army on the network: the "Army" tab of the throne screen and the barracks. Requests from clients are checked by the
 * server (rights, the treasury, limits, the sender's own position for orders given in place); answers carry a fresh view.
 */
public final class ArmyPackets {
    /** Citizens one request may name, for forming a squad. */
    public static final int MAX_CITIZENS = 32;

    private ArmyPackets() {
    }

    // ---------------------------------------------------------------- the army tab

    /**
     * Client to server: show me the army tab of this kingdom.
     */
    public record TabRequest(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("army_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static TabRequest decode(FriendlyByteBuf buf) {
            return new TabRequest(buf.readUUID());
        }

        public static void handle(TabRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                ArmyActions.sendTab(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: the army tab.
     */
    public record TabResponse(ArmyViews.ArmyTabView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static TabResponse decode(FriendlyByteBuf buf) {
            return new TabResponse(ArmyViews.ArmyTabView.decode(buf));
        }

        public static void handle(TabResponse message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientArmyHandlers.handleTab(message.view()));
        }
    }

    /**
     * Client to server: a decision about the kingdom's army.
     *
     * @param first    the squad, the band, or 0
     * @param second   the deal escorted
     * @param citizens the citizen recruited or dismissed, or the members of a new squad
     */
    public record TabChange(UUID kingdomId, Action action, long first, long second, List<UUID> citizens) {
        public static final RequestPolicy POLICY = new RequestPolicy("army_change", 0, 20, 10_000L);

        public enum Action {
            RECRUIT_WARRIOR,
            RECRUIT_ARCHER,
            DISMISS,
            FORM_SQUAD,
            JOIN_SQUAD,
            COMMANDER,
            DISBAND,
            /** No order: the squad trains at the barracks. */
            ORDER_NONE,
            /** Hold the place where the sender stands. */
            ORDER_HOLD_HERE,
            /** Guard the chunks around the sender. */
            ORDER_GUARD_HERE,
            /** Follow the sender. */
            ORDER_FOLLOW_ME,
            ESCORT,
            HIRE,
            RELEASE
        }

        public TabChange {
            citizens = citizens == null ? List.of() : List.copyOf(citizens);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeVarLong(first);
            buf.writeVarLong(second);
            buf.writeVarInt(citizens.size());
            for (UUID citizen : citizens) {
                buf.writeUUID(citizen);
            }
        }

        public static TabChange decode(FriendlyByteBuf buf) {
            UUID kingdom = buf.readUUID();
            Action action = KingdomView.readEnum(buf, Action.class);
            long first = buf.readVarLong();
            long second = buf.readVarLong();
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_CITIZENS) {
                throw new IllegalArgumentException("Too many citizens in an army request: " + size);
            }
            List<UUID> citizens = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                citizens.add(buf.readUUID());
            }
            return new TabChange(kingdom, action, first, second, citizens);
        }

        public static void handle(TabChange message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "first", message.first(), 0, Long.MAX_VALUE)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "second", message.second(), 0, Long.MAX_VALUE)
                    || !ServerRequestGuard.requireSize(sender, POLICY, "citizens", message.citizens(), MAX_CITIZENS)) {
                return;
            }
            ArmyActions.handleTab(sender, message);
        }
    }

    // ---------------------------------------------------------------- the barracks

    /**
     * Server to client: open the barracks screen, or refresh it.
     */
    public record BarracksOpen(ArmyViews.BarracksView view, boolean open) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
            buf.writeBoolean(open);
        }

        public static BarracksOpen decode(FriendlyByteBuf buf) {
            return new BarracksOpen(ArmyViews.BarracksView.decode(buf), buf.readBoolean());
        }

        public static void handle(BarracksOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientArmyHandlers.handleBarracks(message.view(), message.open()));
        }
    }

    /**
     * Client to server: something done at a barracks.
     */
    public record BarracksAction(BlockPos pos, Action action) {
        public static final RequestPolicy POLICY = new RequestPolicy("army_barracks", 0, 20, 10_000L);

        public enum Action {
            REFRESH,
            RECRUIT_WARRIOR,
            RECRUIT_ARCHER
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeEnum(action);
        }

        public static BarracksAction decode(FriendlyByteBuf buf) {
            return new BarracksAction(buf.readBlockPos(), KingdomView.readEnum(buf, Action.class));
        }

        public static void handle(BarracksAction message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                ArmyActions.handleBarracks(sender, message);
            }
        }
    }
}
