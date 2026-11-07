package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientMagicHandlers;
import io.github.sekerator.kingdomworld.common.magic.MagicActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.MagicViews;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Magic on the network: the "Magic" tab of the throne screen, the screen of a magic focus and the screen of a gate. Requests
 * from clients are checked by the server (distance to the block, rights, research, energy, money); answers carry a fresh
 * view.
 */
public final class MagicPackets {
    private MagicPackets() {
    }

    // ---------------------------------------------------------------- the magic tab

    /**
     * Client to server: show me the magic tab of this kingdom.
     */
    public record TabRequest(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("magic_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static TabRequest decode(FriendlyByteBuf buf) {
            return new TabRequest(buf.readUUID());
        }

        public static void handle(TabRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                MagicActions.sendTab(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: the magic tab.
     */
    public record TabResponse(MagicViews.TabView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static TabResponse decode(FriendlyByteBuf buf) {
            return new TabResponse(MagicViews.TabView.decode(buf));
        }

        public static void handle(TabResponse message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMagicHandlers.handleTab(message.view()));
        }
    }

    /**
     * Client to server: a decision about the tower or the spells of the kingdom.
     *
     * @param value the research or the spell, by index
     * @param on    whether the spell is switched on
     */
    public record TabChange(UUID kingdomId, Action action, int value, boolean on) {
        public static final RequestPolicy POLICY = new RequestPolicy("magic_change", 0, 20, 10_000L);

        public enum Action {
            RESEARCH,
            SPELL,
            /** Puts the chunk the player stands in under the ward, or takes it out. */
            WARD_HERE
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeVarInt(value);
            buf.writeBoolean(on);
        }

        public static TabChange decode(FriendlyByteBuf buf) {
            return new TabChange(buf.readUUID(), KingdomView.readEnum(buf, Action.class), buf.readVarInt(), buf.readBoolean());
        }

        public static void handle(TabChange message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "value", message.value(), 0, 63)) {
                return;
            }
            MagicActions.handleTab(sender, message);
        }
    }

    // ---------------------------------------------------------------- the focus

    /**
     * Server to client: open the screen of a focus, or refresh it.
     */
    public record FocusOpen(MagicViews.FocusView view, boolean open) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
            buf.writeBoolean(open);
        }

        public static FocusOpen decode(FriendlyByteBuf buf) {
            return new FocusOpen(MagicViews.FocusView.decode(buf), buf.readBoolean());
        }

        public static void handle(FocusOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMagicHandlers.handleFocus(message.view(), message.open()));
        }
    }

    /**
     * Client to server: something done at a focus.
     *
     * @param spell the spell learned or put into a slot, by index; -1 clears a slot
     * @param slot  the slot of the staff in the main hand
     */
    public record FocusAction(BlockPos pos, Action action, int spell, int slot) {
        public static final RequestPolicy POLICY = new RequestPolicy("magic_focus", 0, 20, 10_000L);

        public enum Action {
            REFRESH,
            LEARN,
            BIND
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeEnum(action);
            buf.writeVarInt(spell + 1);
            buf.writeVarInt(slot);
        }

        public static FocusAction decode(FriendlyByteBuf buf) {
            return new FocusAction(buf.readBlockPos(), KingdomView.readEnum(buf, Action.class), buf.readVarInt() - 1, buf.readVarInt());
        }

        public static void handle(FocusAction message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "spell", message.spell(), -1, 63)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "slot", message.slot(), 0, 8)) {
                return;
            }
            MagicActions.handleFocus(sender, message);
        }
    }

    // ---------------------------------------------------------------- gates

    /**
     * Server to client: open the screen of a gate.
     */
    public record GateOpen(MagicViews.GateView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static GateOpen decode(FriendlyByteBuf buf) {
            return new GateOpen(MagicViews.GateView.decode(buf));
        }

        public static void handle(GateOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMagicHandlers.handleGate(message.view()));
        }
    }

    /**
     * Client to server: travel from the gate at {@code pos} to another gate of its kingdom.
     */
    public record GateTravel(BlockPos pos, long target) {
        public static final RequestPolicy POLICY = new RequestPolicy("magic_gate", 0, 10, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarLong(target);
        }

        public static GateTravel decode(FriendlyByteBuf buf) {
            return new GateTravel(buf.readBlockPos(), buf.readVarLong());
        }

        public static void handle(GateTravel message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "target", message.target(), 1, Long.MAX_VALUE)) {
                return;
            }
            MagicActions.travel(sender, message.pos(), message.target());
        }
    }
}
